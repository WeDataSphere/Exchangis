-- Add index for task_id field in exchangis_launched_task_entity table
-- 为exchangis_launched_task_entity表的task_id字段添加索引
ALTER TABLE exchangis_launched_task_entity ADD INDEX `task_id_idx`(`task_id`);
