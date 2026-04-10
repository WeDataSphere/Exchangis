-- ============================================================
-- 重置exchangis_project_info表自增主键脚本
-- Reset auto-increment primary key for exchangis_project_info table
-- ============================================================
-- 说明：此脚本将重置exchangis_project_info表的自增值到指定位置
-- Note: This script will reset the auto-increment value to a specified position
-- ============================================================

-- Step 1: 查看当前的自增值 / Step 1: Check current auto-increment value
SHOW TABLE STATUS LIKE 'exchangis_project_info';

-- Step 2: 查看当前最大ID值 / Step 2: Check current maximum ID value
SELECT MAX(id) as current_max_id FROM exchangis_project_info;

-- Step 3: 查看关联表的数据情况 / Step 3: Check related tables data
-- 检查exchangis_job_entity表中的project_id / Check project_id in exchangis_job_entity
SELECT 
    'exchangis_job_entity' as table_name,
    COUNT(*) as record_count,
    MIN(project_id) as min_project_id,
    MAX(project_id) as max_project_id,
    COUNT(DISTINCT project_id) as distinct_project_count
FROM exchangis_job_entity
WHERE project_id IS NOT NULL;

-- 检查exchangis_project_user表中的project_id / Check project_id in exchangis_project_user
SELECT 
    'exchangis_project_user' as table_name,
    COUNT(*) as record_count,
    MIN(project_id) as min_project_id,
    MAX(project_id) as max_project_id,
    COUNT(DISTINCT project_id) as distinct_project_count
FROM exchangis_project_user;

-- ============================================================
-- 执行重置操作（请根据实际情况修改下面的值）
-- Execute reset operation (modify the value below according to actual situation)
-- ============================================================

-- Step 4: 备份数据（可选但强烈推荐）/ Step 4: Backup data (optional but strongly recommended)
-- CREATE TABLE exchangis_project_info_backup AS SELECT * FROM exchangis_project_info;

-- Step 5: 重置自增值 / Step 5: Reset auto-increment value
-- 注意：将下面的数字替换为你想要的自增起始值
-- Note: Replace the number below with your desired auto-increment start value
-- 建议设置为：MAX(id) + 1 / Recommended: MAX(id) + 1
ALTER TABLE exchangis_project_info AUTO_INCREMENT = 1;

-- ============================================================
-- 验证结果 / Verify results
-- ============================================================

-- Step 6: 再次查看自增值确认修改成功 / Step 6: Check auto-increment value again
SHOW TABLE STATUS LIKE 'exchangis_project_info';

-- Step 7: 测试插入一条数据验证自增是否正常 / Step 7: Test insert to verify auto-increment
-- INSERT INTO exchangis_project_info (name, create_user) VALUES ('__test_auto_increment__', 'system');
-- SELECT id, name FROM exchangis_project_info WHERE name = '__test_auto_increment__';
-- -- 删除测试数据 / Delete test data
-- DELETE FROM exchangis_project_info WHERE name = '__test_auto_increment__';

