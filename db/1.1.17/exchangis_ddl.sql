-- Add index for job_execution_id field in exchangis_launched_task_entity table
-- 为exchangis_launched_task_entity表的job_execution_id字段添加索引（消除作业列表查询的全表扫描）
ALTER TABLE exchangis_launched_task_entity ADD INDEX `job_execution_id_idx`(`job_execution_id`);

-- Add index for create_time field in exchangis_launched_job_entity table
-- 为exchangis_launched_job_entity表的create_time字段添加索引（消除作业列表 ORDER BY create_time 的 filesort 及范围扫描）
ALTER TABLE exchangis_launched_job_entity ADD INDEX `idx_create_time`(`create_time`);
