-- ============================================================================
-- Exchangis 1.1.18 incremental DDL
-- ============================================================================

-- exchangis_job_file_resources
-- File source BML resource metadata (mirrors exchangis_job_transform_processor pattern)
-- 文件 source 上传至 BML 的文件元数据（仿 exchangis_job_transform_processor 模式）
--
-- Architecture ② / M5: file source does NOT go through the datasource management flow
-- (no exchangis_datasource row). This table only stores BML references for audit/reuse.
-- 架构②/M5：文件 source 不走数据源管理流程（不写 exchangis_datasource 表），
-- 本表仅存储 BML 文件引用，用于元数据/复用/审计。
CREATE TABLE IF NOT EXISTS `exchangis_job_file_resources` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `job_id` bigint(20) DEFAULT NULL COMMENT 'Associated job id, null if uploaded before job save (关联作业ID，上传时未保存作业则为空)',
  `file_name` varchar(255) NOT NULL COMMENT 'Original file name (原始文件名)',
  `file_size` bigint(20) NOT NULL DEFAULT 0 COMMENT 'File size in bytes (文件字节数)',
  `file_type` varchar(32) NOT NULL DEFAULT 'CSV' COMMENT 'File type: CSV/TEXT (文件类型)',
  `encoding` varchar(32) DEFAULT NULL COMMENT 'Detected encoding (检测编码)',
  `separator` varchar(8) DEFAULT NULL COMMENT 'Detected separator (检测分隔符)',
  `has_header` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Has header row (是否有表头)',
  `bml_resource_id` varchar(255) NOT NULL COMMENT 'BML resource id (BML资源ID)',
  `bml_version` varchar(64) NOT NULL COMMENT 'BML version (BML版本)',
  `owner` varchar(50) NOT NULL COMMENT 'BML owner/creator (BML所有者/创建者)',
  `parse_result` text COMMENT 'FileParseResult JSON snapshot (解析结果JSON快照)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_job_id` (`job_id`),
  INDEX `idx_bml_resource_id` (`bml_resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
