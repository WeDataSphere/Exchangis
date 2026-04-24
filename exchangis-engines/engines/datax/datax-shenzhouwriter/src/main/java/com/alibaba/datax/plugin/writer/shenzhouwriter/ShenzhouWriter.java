package com.alibaba.datax.plugin.writer.shenzhouwriter;

import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.spi.Writer;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.rdbms.util.DataBaseType;
import com.alibaba.datax.plugin.rdbms.writer.CommonRdbmsWriter;
import com.alibaba.datax.plugin.rdbms.writer.Key;

import java.util.List;

/**
 * 神州数据库（神通/Oscar）DataX 写插件 / Shenzhou (Oscar) DataX Writer Plugin
 * <p>
 * 基于 CommonRdbmsWriter 通用框架，通过 JDBC 连接神通数据库，
 * 接收上游 Reader 传来的数据，批量执行 INSERT/REPLACE 语句写入目标表。
 * Based on the CommonRdbmsWriter framework, connects to Oscar via JDBC,
 * receives data from upstream Reader and batch-writes to target table via INSERT/REPLACE.
 * </p>
 * <p>
 * 支持的 writeMode:
 * <ul>
 *   <li>insert — 直接插入（默认）/ direct insert (default)</li>
 *   <li>replace — 先删后插（支持故障转移）/ delete then insert (supports failover)</li>
 *   <li>update — 按主键更新 / update by primary key</li>
 * </ul>
 * 神通数据库兼容 Oracle SQL 方言，JDBC 驱动: com.oscar.Driver，
 * JDBC URL 格式: jdbc:oscar://host:port/database
 * Oscar is Oracle-compatible, JDBC driver: com.oscar.Driver,
 * JDBC URL format: jdbc:oscar://host:port/database
 * </p>
 *
 * @see CommonRdbmsWriter
 * @see DataBaseType#Oscar
 */
public class ShenzhouWriter extends Writer {
    /** 数据库类型标识（神通/Oscar） / Database type identifier (Oscar) */
    private static final DataBaseType DATABASE_TYPE = DataBaseType.Oscar;

    /**
     * Writer Job 层：负责写任务切分、前/后置 SQL 执行等全局操作 / Job layer: task splitting, pre/post SQL execution etc.
     * <p>
     * 所有数据库交互委托给 CommonRdbmsWriter.Job 执行 / All DB interactions delegated to CommonRdbmsWriter.Job
     * </p>
     */
    public static class Job extends Writer.Job {
        /** 原始作业配置 / Original job configuration */
        private Configuration originalConfig = null;
        /** 通用 RDBMS Writer Job，承载实际逻辑 / Common RDBMS Writer Job, carries actual logic */
        private CommonRdbmsWriter.Job commonRdbmsWriterJob;

        @Override
        public void preCheck() {
            this.init();
            this.commonRdbmsWriterJob.writerPreCheck(this.originalConfig, DATABASE_TYPE);
        }

        /**
         * 初始化 Job 配置，委托给通用 RDBMS Writer 框架
         * / Initialize job configuration, delegate to common RDBMS Writer framework
         */
        @Override
        public void init() {
            this.originalConfig = super.getPluginJobConf();
            this.commonRdbmsWriterJob = new CommonRdbmsWriter.Job(DATABASE_TYPE);
            this.commonRdbmsWriterJob.init(this.originalConfig);
        }

        /**
         * 写入前准备，执行 preSql（如建表、清空数据等）
         * / Prepare before write, execute preSql (e.g. create table, truncate data)
         */
        @Override
        public void prepare() {
            this.commonRdbmsWriterJob.prepare(this.originalConfig);
        }

        @Override
        public List<Configuration> split(int mandatoryNumber) {
            return this.commonRdbmsWriterJob.split(this.originalConfig, mandatoryNumber);
        }

        /**
         * 写入后收尾，执行 postSql（如数据校验、重建索引等）
         * / Cleanup after write, execute postSql (e.g. data validation, rebuild indexes)
         */
        @Override
        public void post() {
            this.commonRdbmsWriterJob.post(this.originalConfig);
        }

        @Override
        public void destroy() {
            this.commonRdbmsWriterJob.destroy(this.originalConfig);
        }

    }

    /**
     * Writer Task 层：负责实际的数据写入操作 / Task layer: actual data writing
     * <p>
     * 每个 Task 独立连接数据库，从 RecordReceiver 接收 Reader 数据，
     * 批量执行 INSERT 语句，支持失败行逐行重试
     * Each Task independently connects to the database, receives data from RecordReceiver,
     * batch-executes INSERT statements, with row-by-row retry on failure
     * </p>
     */
    public static class Task extends Writer.Task {
        /** 当前 Task 的切片配置 / Current task slice configuration */
        private Configuration writerSliceConfig;
        /** 通用 RDBMS Writer Task，执行实际的写入逻辑 / Common RDBMS Writer Task for actual writing */
        private CommonRdbmsWriter.Task commonRdbmsWriterTask;

        @Override
        public void init() {
            this.writerSliceConfig = super.getPluginJobConf();
            this.commonRdbmsWriterTask = new CommonRdbmsWriter.Task(DATABASE_TYPE);
            this.commonRdbmsWriterTask.init(this.writerSliceConfig);
        }

        /**
         * Task 级别写入前准备（多表场景下各 Task 独立执行 preSql）
         * / Task-level pre-write preparation (each Task executes preSql independently in multi-table scenario)
         */
        @Override
        public void prepare() {
            this.commonRdbmsWriterTask.prepare(this.writerSliceConfig);
        }

        /**
         * 从 RecordReceiver 拉取数据行并批量写入目标表
         * / Pull data rows from RecordReceiver and batch-write to target table
         *
         * @param recordReceiver 记录接收器，接收 Reader 传来的数据 / Record receiver, receives data from Reader
         */
        @Override
        public void startWrite(RecordReceiver recordReceiver) {
            this.commonRdbmsWriterTask.startWrite(recordReceiver, this.writerSliceConfig,
                    super.getTaskPluginCollector());
        }

        @Override
        public void post() {
            this.commonRdbmsWriterTask.post(this.writerSliceConfig);
        }

        @Override
        public void destroy() {
            this.commonRdbmsWriterTask.destroy(this.writerSliceConfig);
        }

        /**
         * 是否支持故障转移重试 / Whether failover retry is supported
         * <p>
         * replace 模式是幂等的，支持故障转移 /
         * Replace mode is idempotent and supports failover
         * </p>
         *
         * @return writeMode 为 replace 时返回 true / true if writeMode is replace
         */
        @Override
        public boolean supportFailOver() {
            String writeMode = writerSliceConfig.getString(Key.WRITE_MODE);
            return "replace".equalsIgnoreCase(writeMode);
        }

    }

}
