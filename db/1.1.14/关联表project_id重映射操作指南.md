# 关联表project_id重映射操作指南
# Guide for Relinking project_id in Related Tables

## 背景 / Background

当重置`exchangis_project_info`表的主键ID后，关联表中的`project_id`字段需要同步更新，以保持数据一致性。

## 数据关系图 / Data Relationship Diagram

```
┌─────────────────────────┐
│ exchangis_project_info  │
│ ─────────────────────── │
│ id (PK) ←── 需要重置    │
│ name                    │
│ ...                     │
└────────┬────────────────┘
         │
         ├── exchangis_job_entity.project_id (需要更新)
         │
         └── exchangis_project_user.project_id (需要更新)
```

## 两种场景 / Two Scenarios

### 场景A：主表有数据，需要重新编号并同步关联表
**适用情况**：主表已有数据，想重新从1开始编号

**操作步骤**：
1. 创建新表并重新编号
2. 建立新旧ID映射关系
3. 更新所有关联表
4. 替换原表

**使用脚本**：`relink_project_id_mappings.sql`（方案1）

### 场景B：主表为空或可以清空
**适用情况**：开发环境、测试环境或数据可丢弃

**操作步骤**：
1. 清空所有表
2. 重置自增值

**使用脚本**：`relink_project_id_mappings.sql`（方案2）

---

## 详细操作步骤（场景A） / Detailed Steps (Scenario A)

### 准备阶段 / Preparation Phase

#### 1. 数据检查 / Data Check
```sql
-- 检查数据量 / Check data volume
SELECT
    (SELECT COUNT(*) FROM exchangis_project_info) as project_count,
    (SELECT COUNT(*) FROM exchangis_job_entity WHERE project_id IS NOT NULL) as job_count,
    (SELECT COUNT(*) FROM exchangis_project_user) as user_count;
```

#### 2. 创建备份 / Create Backup
```sql
-- 备份所有相关表 / Backup all related tables
CREATE TABLE exchangis_project_info_bak_YYYYMMDD AS SELECT * FROM exchangis_project_info;
CREATE TABLE exchangis_job_entity_bak_YYYYMMDD AS SELECT * FROM exchangis_job_entity;
CREATE TABLE exchangis_project_user_bak_YYYYMMDD AS SELECT * FROM exchangis_project_user;
```

### 执行阶段 / Execution Phase

#### Step 1: 创建ID映射表
```sql
-- 创建映射表结构
DROP TABLE IF EXISTS project_id_mapping;
CREATE TABLE project_id_mapping (
    old_id BIGINT(20) NOT NULL,
    new_id BIGINT(20) NOT NULL,
    name VARCHAR(64) NOT NULL,
    PRIMARY KEY (old_id),
    UNIQUE KEY (new_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```

#### Step 2: 创建新表并重新编号
```sql
-- 创建新表结构
DROP TABLE IF EXISTS exchangis_project_info_new;
CREATE TABLE exchangis_project_info_new LIKE exchangis_project_info;
ALTER TABLE exchangis_project_info_new MODIFY id BIGINT(20) NOT NULL;

-- 按原ID顺序重新编号，从1开始
SET @new_id = 0;
INSERT INTO exchangis_project_info_new (id, name, description, create_time, last_update_time, create_user, last_update_user, project_labels, domain, exec_users, view_users, edit_users, source)
SELECT
    @new_id := @new_id + 1 as new_id,
    name, description, create_time, last_update_time, create_user, last_update_user,
    project_labels, domain, exec_users, view_users, edit_users, source
FROM exchangis_project_info
ORDER BY id;
```

#### Step 3: 生成映射关系
```sql
-- 生成旧ID到新ID的映射
INSERT INTO project_id_mapping (old_id, new_id, name)
SELECT id, @new_id := @new_id + 1 as new_id, name
FROM exchangis_project_info
ORDER BY id;

-- 验证映射
SELECT * FROM project_id_mapping;
```

#### Step 4: 更新exchangis_job_entity
```sql
-- 备份
CREATE TABLE IF NOT EXISTS exchangis_job_entity_backup AS SELECT * FROM exchangis_job_entity;

-- 使用映射表更新project_id
UPDATE exchangis_job_entity j
INNER JOIN project_id_mapping m ON j.project_id = m.old_id
SET j.project_id = m.new_id;

-- 验证更新
SELECT COUNT(*) as updated_count FROM exchangis_job_entity WHERE project_id IS NOT NULL;
```

#### Step 5: 更新exchangis_project_user
```sql
-- 备份
CREATE TABLE IF NOT EXISTS exchangis_project_user_backup AS SELECT * FROM exchangis_project_user;

-- 创建临时表
DROP TABLE IF EXISTS exchangis_project_user_temp;
CREATE TABLE exchangis_project_user_temp AS
SELECT u.new_id as project_id, pu.priv_user, pu.priv, pu.last_update_time
FROM exchangis_project_user pu
INNER JOIN project_id_mapping u ON pu.project_id = u.old_id;

-- 删除旧数据
DELETE FROM exchangis_project_user
WHERE project_id IN (SELECT old_id FROM project_id_mapping);

-- 插入新数据
INSERT INTO exchangis_project_user (project_id, priv_user, priv, last_update_time)
SELECT project_id, priv_user, priv, last_update_time
FROM exchangis_project_user_temp;

-- 清理临时表
DROP TABLE exchangis_project_user_temp;

-- 验证更新
SELECT COUNT(*) as updated_count FROM exchangis_project_user;
```

#### Step 6: 替换原表
```sql
-- 停止应用访问 / Stop application

-- 重命名表
RENAME TABLE
    exchangis_project_info TO exchangis_project_info_old,
    exchangis_project_info_new TO exchangis_project_info;

-- 重置自增值为最大ID+1
ALTER TABLE exchangis_project_info AUTO_INCREMENT = (SELECT MAX(id) + 1 FROM exchangis_project_info);
```

### 验证阶段 / Verification Phase

#### 验证数据完整性
```sql
-- 检查记录数
SELECT 'project_info' as table_name, COUNT(*) as count FROM exchangis_project_info
UNION ALL
SELECT 'job_entity', COUNT(*) FROM exchangis_job_entity WHERE project_id IS NOT NULL
UNION ALL
SELECT 'project_user', COUNT(*) FROM exchangis_project_user;

-- 检查关联关系
SELECT
    p.id as project_id,
    p.name as project_name,
    COUNT(DISTINCT j.id) as job_count,
    COUNT(DISTINCT pu.id) as user_count
FROM exchangis_project_info p
LEFT JOIN exchangis_job_entity j ON p.id = j.project_id
LEFT JOIN exchangis_project_user pu ON p.id = pu.project_id
GROUP BY p.id, p.name;
```

#### 测试插入新记录
```sql
-- 测试自增是否正常
INSERT INTO exchangis_project_info (name, create_user)
VALUES ('__test__', 'system');

SELECT id, name FROM exchangis_project_info WHERE name = '__test__';
DELETE FROM exchangis_project_info WHERE name = '__test__';
```

---

## 回滚方案 / Rollback Plan

### 如果需要回滚操作
```sql
-- 1. 停止应用 / Stop application

-- 2. 恢复project_info表
DROP TABLE exchangis_project_info;
RENAME TABLE exchangis_project_info_old TO exchangis_project_info;

-- 3. 恢复关联表（方法1：从备份恢复）
TRUNCATE TABLE exchangis_job_entity;
INSERT INTO exchangis_job_entity SELECT * FROM exchangis_job_entity_backup;

TRUNCATE TABLE exchangis_project_user;
INSERT INTO exchangis_project_user SELECT * FROM exchangis_project_user_backup;

-- 3. 恢复关联表（方法2：使用映射表反向更新）
UPDATE exchangis_job_entity j
INNER JOIN project_id_mapping m ON j.project_id = m.new_id
SET j.project_id = m.old_id;

DELETE FROM exchangis_project_user
WHERE project_id IN (SELECT new_id FROM project_id_mapping);

INSERT INTO exchangis_project_user (project_id, priv_user, priv, last_update_time)
SELECT m.old_id, pu.priv_user, pu.priv, pu.last_update_time
FROM exchangis_project_user pu
INNER JOIN project_id_mapping m ON pu.project_id = m.new_id;

-- 4. 重启应用 / Restart application
```

---

## 清理阶段 / Cleanup Phase

### 确认成功后清理临时表
```sql
-- 删除映射表
DROP TABLE IF EXISTS project_id_mapping;

-- 删除旧表
DROP TABLE IF EXISTS exchangis_project_info_old;

-- 删除备份表（可选，建议保留一段时间）/ Optional, recommend to keep for a while
-- DROP TABLE IF EXISTS exchangis_job_entity_backup;
-- DROP TABLE IF EXISTS exchangis_project_user_backup;
```

---

## 注意事项 / Important Notes

### ⚠️ 执行前检查清单
- [ ] 已在测试环境验证
- [ ] 已创建完整备份
- [ ] 已通知相关人员
- [ ] 选择业务低峰期
- [ ] 准备好回滚方案
- [ ] 停止应用写入访问

### 🔒 安全措施
1. **备份优先**：操作前必须备份所有相关表
2. **停止写入**：执行期间停止应用对相关表的写入操作
3. **分步验证**：每个步骤执行后都要验证结果
4. **保留映射**：`project_id_mapping`表在确认成功前不要删除

### 📊 性能考虑
- 数据量大时，UPDATE操作可能较慢
- 建议在维护窗口执行
- 可以分批更新关联表数据

### 🚨 常见问题
**Q: 更新过程中数据不一致怎么办？**
A: 立即停止操作，使用备份表恢复

**Q: 关联表更新失败怎么办？**
A: 使用映射表重新执行UPDATE语句

**Q: 自增值设置不对怎么办？**
A: 使用`ALTER TABLE xxx AUTO_INCREMENT = new_value`重新设置

---

## 快速参考 / Quick Reference

### 关键SQL语句
```sql
-- 重新编号
SET @new_id = 0;
INSERT INTO new_table (id, ...)
SELECT @new_id := @new_id + 1, ... FROM old_table ORDER BY id;

-- 更新关联表
UPDATE related_table r
INNER JOIN mapping_table m ON r.old_fk = m.old_id
SET r.old_fk = m.new_id;

-- 重置自增值
ALTER TABLE table_name AUTO_INCREMENT = (SELECT MAX(id) + 1 FROM table_name);
```

### 使用命令
```bash
# 执行完整脚本
mysql -u username -p database_name < relink_project_id_mappings.sql

# 在MySQL客户端中
source /path/to/relink_project_id_mappings.sql;
```
