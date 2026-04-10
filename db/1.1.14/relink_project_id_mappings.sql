-- ============================================================
-- 关联表project_id重新映射脚本
-- Relink project_id in related tables after resetting primary key
-- ============================================================
-- 场景：重置exchangis_project_info主键后，同步更新关联表中的project_id
-- Scenario: After resetting exchangis_project_info primary key, update project_id in related tables
-- ============================================================

-- ============================================================
-- 方案1: 如果需要重新编号现有数据
-- Option 1: If you need to renumber existing data
-- ============================================================

-- Step 1: 创建ID映射表 / Step 1: Create ID mapping table
DROP TABLE IF EXISTS project_id_mapping;
CREATE TABLE project_id_mapping (
    old_id BIGINT(20) NOT NULL,
    new_id BIGINT(20) NOT NULL,
    name VARCHAR(64) NOT NULL,
    PRIMARY KEY (old_id),
    UNIQUE KEY (new_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Step 2: 备份原有数据到新表（使用新的自增ID）/ Step 2: Backup data to new table (with new auto-increment IDs)
DROP TABLE IF EXISTS exchangis_project_info_new;
CREATE TABLE exchangis_project_info_new LIKE exchangis_project_info;
ALTER TABLE exchangis_project_info_new MODIFY id BIGINT(20) NOT NULL; -- 移除自增 / Remove auto-increment

-- 插入数据到新表，让ID从1开始重新编号 / Insert data to new table, renumbering ID starting from 1
SET @new_id = 0;
INSERT INTO exchangis_project_info_new (id, name, description, create_time, last_update_time, create_user, last_update_user, project_labels, domain, exec_users, view_users, edit_users, source)
SELECT
    @new_id := @new_id + 1 as new_id,
    name, description, create_time, last_update_time, create_user, last_update_user,
    project_labels, domain, exec_users, view_users, edit_users, source
FROM exchangis_project_info
ORDER BY id;

-- Step 3: 创建ID映射关系 / Step 3: Create ID mapping
INSERT INTO project_id_mapping (old_id, new_id, name)
SELECT id, @new_id := @new_id + 1 as new_id, name
FROM exchangis_project_info
ORDER BY id;

-- 验证映射 / Verify mapping
SELECT * FROM project_id_mapping LIMIT 10;
SELECT COUNT(*) as total_mappings FROM project_id_mapping;

-- Step 4: 备份关联表（可选但推荐）/ Step 4: Backup related tables (optional but recommended)
CREATE TABLE IF NOT EXISTS exchangis_job_entity_backup AS SELECT * FROM exchangis_job_entity;
CREATE TABLE IF NOT EXISTS exchangis_project_user_backup AS SELECT * FROM exchangis_project_user;

-- Step 5: 更新exchangis_job_entity表 / Step 5: Update exchangis_job_entity table
-- 方法A: 使用JOIN更新 / Method A: Update using JOIN
UPDATE exchangis_job_entity j
INNER JOIN project_id_mapping m ON j.project_id = m.old_id
SET j.project_id = m.new_id;

-- 验证更新 / Verify update
SELECT
    COUNT(*) as total_updated,
    MIN(project_id) as min_project_id,
    MAX(project_id) as max_project_id
FROM exchangis_job_entity
WHERE project_id IS NOT NULL;

-- Step 6: 更新exchangis_project_user表 / Step 6: Update exchangis_project_user table
-- 注意：由于有唯一约束(project_id, priv_user, priv)，需要先删除再插入
-- Note: Due to unique constraint, need to delete and insert

-- 创建临时表存储新数据 / Create temp table for new data
DROP TABLE IF EXISTS exchangis_project_user_temp;
CREATE TABLE exchangis_project_user_temp AS
SELECT u.new_id as project_id, pu.priv_user, pu.priv, pu.last_update_time
FROM exchangis_project_user pu
INNER JOIN project_id_mapping u ON pu.project_id = u.old_id;

-- 删除原数据 / Delete original data
DELETE FROM exchangis_project_user
WHERE project_id IN (SELECT old_id FROM project_id_mapping);

-- 插入新数据 / Insert new data
INSERT INTO exchangis_project_user (project_id, priv_user, priv, last_update_time)
SELECT project_id, priv_user, priv, last_update_time
FROM exchangis_project_user_temp;

DROP TABLE exchangis_project_user_temp;

-- 验证更新 / Verify update
SELECT
    COUNT(*) as total_updated,
    MIN(project_id) as min_project_id,
    MAX(project_id) as max_project_id
FROM exchangis_project_user;

-- Step 7: 替换原表 / Step 7: Replace original table
-- 重命名表 / Rename tables
RENAME TABLE
    exchangis_project_info TO exchangis_project_info_old,
    exchangis_project_info_new TO exchangis_project_info;

-- 重新设置自增值 / Reset auto-increment
ALTER TABLE exchangis_project_info AUTO_INCREMENT = (SELECT MAX(id) + 1 FROM exchangis_project_info);

-- Step 8: 验证结果 / Step 8: Verify results
-- 检查数据完整性 / Check data integrity
SELECT 'project_info' as table_name, COUNT(*) as count FROM exchangis_project_info
UNION ALL
SELECT 'job_entity', COUNT(*) FROM exchangis_job_entity WHERE project_id IS NOT NULL
UNION ALL
SELECT 'project_user', COUNT(*) FROM exchangis_project_user;

-- 检查关联关系 / Check relationships
SELECT
    p.id as project_id,
    p.name as project_name,
    COUNT(DISTINCT j.id) as job_count,
    COUNT(DISTINCT pu.id) as user_count
FROM exchangis_project_info p
LEFT JOIN exchangis_job_entity j ON p.id = j.project_id
LEFT JOIN exchangis_project_user pu ON p.id = pu.project_id
GROUP BY p.id, p.name
LIMIT 10;

-- ============================================================
-- 方案2: 如果主表为空，直接重置自增值（简单场景）
-- Option 2: If main table is empty, just reset auto-increment (simple scenario)
-- ============================================================

-- 如果主表为空或可以清空 / If main table is empty or can be truncated
-- TRUNCATE TABLE exchangis_project_info;
-- TRUNCATE TABLE exchangis_job_entity;  -- 如果也需要清空 / If also need to truncate
-- TRUNCATE TABLE exchangis_project_user; -- 如果也需要清空 / If also need to truncate

-- ============================================================
-- 回滚方案 / Rollback Plan
-- ============================================================

-- 如果需要回滚，使用备份表恢复 / If rollback needed, restore from backup tables
/*
-- 停止应用 / Stop application

-- 恢复project_info / Restore project_info
DROP TABLE exchangis_project_info;
RENAME TABLE exchangis_project_info_old TO exchangis_project_info;

-- 恢复关联表 / Restore related tables
-- 方法1: 从备份恢复 / Method 1: Restore from backup
TRUNCATE TABLE exchangis_job_entity;
INSERT INTO exchangis_job_entity SELECT * FROM exchangis_job_entity_backup;

TRUNCATE TABLE exchangis_project_user;
INSERT INTO exchangis_project_user SELECT * FROM exchangis_project_user_backup;

-- 方法2: 使用映射表反向更新 / Method 2: Reverse update using mapping table
UPDATE exchangis_job_entity j
INNER JOIN project_id_mapping m ON j.project_id = m.new_id
SET j.project_id = m.old_id;

-- 重新启动应用 / Restart application
*/

-- ============================================================
-- 清理临时表（确认成功后执行）/ Cleanup temp tables (execute after confirming success)
-- ============================================================
-- DROP TABLE IF EXISTS project_id_mapping;
-- DROP TABLE IF EXISTS exchangis_project_info_old;
-- DROP TABLE IF EXISTS exchangis_job_entity_backup;
-- DROP TABLE IF EXISTS exchangis_project_user_backup;
