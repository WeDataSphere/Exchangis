# 重置exchangis_project_info自增主键操作指南
# Reset Auto-increment Primary Key for exchangis_project_info Guide

## 问题描述 / Problem Description
exchangis_project_info表的自增主键起始值过大，需要重置到较小的值。

## 关联表分析 / Related Tables Analysis

通过分析DDL文件，以下表引用了exchangis_project_info的id字段：

### 1. exchangis_job_entity
- **字段**: project_id (bigint(13))
- **索引**: KEY `idx_project_id` (`project_id`)
- **约束**: 无外键约束（只有索引）
- **影响**: 重置后需要确保project_id对应关系正确

### 2. exchangis_project_user
- **字段**: project_id (bigint(20))
- **索引**: UNIQUE KEY `exchangis_project_user_un` (`project_id`,`priv_user`,`priv`)
- **约束**: 无外键约束（只有唯一键）
- **影响**: 重置后需要确保project_id对应关系正确

### 好消息 / Good News
✅ 数据库中**没有定义外键约束**，这意味着可以直接重置自增值，不需要先删除/禁用外键约束。

## 操作步骤 / Operation Steps

### 第一步：数据备份（强烈推荐）/ Step 1: Backup Data (Strongly Recommended)
```sql
-- 备份exchangis_project_info表
CREATE TABLE exchangis_project_info_backup_YYYYMMDD AS SELECT * FROM exchangis_project_info;

-- 备份关联表
CREATE TABLE exchangis_job_entity_backup_YYYYMMDD AS SELECT * FROM exchangis_job_entity;
CREATE TABLE exchangis_project_user_backup_YYYYMMDD AS SELECT * FROM exchangis_project_user;
```

### 第二步：检查当前状态 / Step 2: Check Current Status
```sql
-- 查看当前自增值
SHOW TABLE STATUS LIKE 'exchangis_project_info';

-- 查看当前最大ID
SELECT MAX(id) as current_max_id FROM exchangis_project_info;

-- 检查关联表数据分布
SELECT
    'exchangis_job_entity' as table_name,
    COUNT(*) as record_count,
    MIN(project_id) as min_project_id,
    MAX(project_id) as max_project_id
FROM exchangis_job_entity
WHERE project_id IS NOT NULL
UNION ALL
SELECT
    'exchangis_project_user' as table_name,
    COUNT(*) as record_count,
    MIN(project_id) as min_project_id,
    MAX(project_id) as max_project_id
FROM exchangis_project_user;
```

### 第三步：重置自增值 / Step 3: Reset Auto-increment Value

**方案A：如果表为空或要删除所有数据 / Option A: If table is empty or will delete all data**
```sql
-- 清空表并重置自增值
TRUNCATE TABLE exchangis_project_info;
-- TRUNCATE会自动重置自增值为1
```

**方案B：如果表有数据且要保留 / Option B: If table has data and need to keep**
```sql
-- 方法1：直接设置自增值（推荐）
-- 新记录将从指定值开始递增
ALTER TABLE exchangis_project_info AUTO_INCREMENT = 1;
-- 或者设置为最大ID + 1
-- ALTER TABLE exchangis_project_info AUTO_INCREMENT = (SELECT MAX(id) + 1 FROM exchangis_project_info);

-- 方法2：通过删除并重新创建表（极端情况）
-- 注意：这会重新定义表结构，需要确认DDL完全一致
-- DROP TABLE exchangis_project_info;
-- CREATE TABLE exchangis_project_info (...);
```

### 第四步：关联表数据同步（如需要）/ Step 4: Sync Related Tables (If Needed)

如果需要将关联表的project_id也同步更新：
```sql
-- 示例：如果需要重新映射project_id
-- 警告：此操作需要谨慎，建议先在测试环境验证
-- UPDATE exchangis_job_entity SET project_id = new_project_id WHERE project_id = old_project_id;
-- UPDATE exchangis_project_user SET project_id = new_project_id WHERE project_id = old_project_id;
```

### 第五步：验证结果 / Step 5: Verify Results
```sql
-- 再次查看自增值
SHOW TABLE STATUS LIKE 'exchangis_project_info';

-- 插入测试数据验证
INSERT INTO exchangis_project_info (name, create_user)
VALUES ('__test_auto_increment__', 'system');

SELECT id, name FROM exchangis_project_info
WHERE name = '__test_auto_increment__';

-- 删除测试数据
DELETE FROM exchangis_project_info
WHERE name = '__test_auto_increment__';
```

## 注意事项 / Important Notes

### ⚠️ 风险提示 / Risk Warnings
1. **数据一致性**: 确保重置后的ID不会与关联表中的project_id冲突
2. **业务影响**: 确认重置操作不会影响正在运行的业务
3. **备份优先**: 执行任何修改前务必备份数据
4. **测试环境**: 建议先在测试环境验证操作流程

### 🔍 检查清单 / Checklist
- [ ] 已备份所有相关表
- [ ] 已确认当前最大ID值
- [ ] 已检查关联表数据分布
- [ ] 已在测试环境验证
- [ ] 已选择业务低峰期执行
- [ ] 已准备回滚方案

### 📝 常见问题 / FAQ

**Q1: 重置后对现有数据有影响吗？**
A: 对现有数据没有影响，只影响新插入记录的ID值。

**Q2: 需要重启应用吗？**
A: 不需要，数据库层面的修改对应用透明。

**Q3: 如何回滚？**
A: 使用备份表恢复：`RENAME TABLE exchangis_project_info TO exchangis_project_info_new, exchangis_project_info_backup TO exchangis_project_info;`

**Q4: 自增值可以设置为任意值吗？**
A: 可以，但建议设置为大于当前最大ID的值，避免主键冲突。

## 使用说明 / Usage Instructions

执行reset_project_auto_increment.sql脚本：
```bash
mysql -u username -p database_name < reset_project_auto_increment.sql
```

或在MySQL客户端中：
```sql
source /path/to/reset_project_auto_increment.sql;
```

## 联系支持 / Contact Support
如有问题，请联系DBA或技术支持团队。
