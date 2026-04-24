package com.alibaba.datax.plugin.reader.shenzhoureader;

import com.alibaba.datax.common.plugin.RecordSender;
import com.alibaba.datax.common.spi.Reader;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.rdbms.reader.CommonRdbmsReader;
import com.alibaba.datax.plugin.rdbms.reader.Constant;
import com.alibaba.datax.plugin.rdbms.util.DataBaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 神州数据库（神通/Oscar）DataX 读插件 / Shenzhou (Oscar) DataX Reader Plugin
 * <p>
 * 基于 CommonRdbmsReader 通用框架，通过 JDBC 连接神通数据库执行 SELECT 查询，
 * 将 ResultSet 中的数据转换为 DataX 内部格式并传输给下游 Writer。
 * Based on the CommonRdbmsReader framework, connects to Oscar via JDBC,
 * executes SELECT queries and converts ResultSet data to DataX internal format.
 * </p>
 * <p>
 * 神通数据库兼容 Oracle SQL 方言，JDBC 驱动: com.oscar.Driver，
 * JDBC URL 格式: jdbc:oscar://host:port/database
 * Oscar is Oracle-compatible, JDBC driver: com.oscar.Driver,
 * JDBC URL format: jdbc:oscar://host:port/database
 * </p>
 *
 * @see CommonRdbmsReader
 * @see DataBaseType#Oscar
 */
public class ShenzhouReader extends Reader {

    /** 数据库类型标识（神通/Oscar） / Database type identifier (Oscar) */
    private static final DataBaseType DATABASE_TYPE = DataBaseType.Oscar;

    /**
     * Reader Job 层：负责任务切分、前置校验等全局操作 / Job layer: task splitting, pre-check etc.
     * <p>
     * 所有数据库交互委托给 CommonRdbmsReader.Job 执行 / All DB interactions delegated to CommonRdbmsReader.Job
     * </p>
     */
    public static class Job extends Reader.Job {
        private static final Logger LOG = LoggerFactory
                .getLogger(Job.class);

        /** 原始作业配置 / Original job configuration */
        private Configuration originalConfig = null;
        /** 通用 RDBMS Reader Job，承载实际逻辑 / Common RDBMS Reader Job, carries actual logic */
        private CommonRdbmsReader.Job commonRdbmsReaderJob;

        /**
         * 初始化 Job 配置 / Initialize job configuration
         * <p>
         * 忽略用户配置的 fetchSize，由 JDBC 驱动自行控制游标读取大小
         * Ignore user-configured fetchSize, let the JDBC driver manage cursor fetch size
         * </p>
         */
        @Override
        public void init() {
            this.originalConfig = super.getPluginJobConf();

            // 神通数据库不需要配置 fetchSize，使用 JDBC 驱动默认行为
            // Oscar does not need fetchSize config, uses JDBC driver default behavior
            Integer userConfigedFetchSize = this.originalConfig.getInt(Constant.FETCH_SIZE);
            if (userConfigedFetchSize != null) {
                LOG.warn("fetchSize is not required for shenzhoureader and will be ignored. "
                        + "Remove fetchSize config to suppress this warning. "
                        + "(对 shenzhoureader 不需要配置 fetchSize，将会忽略这项配置，去除 fetchSize 配置可消除此警告)");
            }

            this.originalConfig.set(Constant.FETCH_SIZE, Integer.MIN_VALUE);

            // 委托给通用 RDBMS Reader 框架 / Delegate to common RDBMS Reader framework
            this.commonRdbmsReaderJob = new CommonRdbmsReader.Job(DATABASE_TYPE);
            this.commonRdbmsReaderJob.init(this.originalConfig);
        }

        @Override
        public void preCheck() {
            init();
            this.commonRdbmsReaderJob.preCheck(this.originalConfig, DATABASE_TYPE);
        }

        @Override
        public List<Configuration> split(int adviceNumber) {
            return this.commonRdbmsReaderJob.split(this.originalConfig, adviceNumber);
        }

        @Override
        public void post() {
            this.commonRdbmsReaderJob.post(this.originalConfig);
        }

        @Override
        public void destroy() {
            this.commonRdbmsReaderJob.destroy(this.originalConfig);
        }

    }

    /**
     * Reader Task 层：负责实际数据读取 / Task layer: actual data reading
     * <p>
     * 每个 Task 独立连接数据库执行分配的 SQL 切片查询，
     * 通过 RecordSender 将记录发送给 Writer
     * Each Task independently connects to the database to execute its assigned SQL slice query,
     * sending records to the Writer via RecordSender
     * </p>
     */
    public static class Task extends Reader.Task {

        /** 当前 Task 的切片配置 / Current task slice configuration */
        private Configuration readerSliceConfig;
        /** 通用 RDBMS Reader Task，执行实际的读取逻辑 / Common RDBMS Reader Task for actual reading */
        private CommonRdbmsReader.Task commonRdbmsReaderTask;

        @Override
        public void init() {
            this.readerSliceConfig = super.getPluginJobConf();
            this.commonRdbmsReaderTask = new CommonRdbmsReader.Task(DATABASE_TYPE, super.getTaskGroupId(), super.getTaskId());
            this.commonRdbmsReaderTask.init(this.readerSliceConfig);
        }

        /**
         * 通过 JDBC ResultSet 逐行读取数据并发送 / Read data row by row via JDBC ResultSet and send
         *
         * @param recordSender 记录发送器，将数据发送给 Writer / Record sender, sends data to Writer
         */
        @Override
        public void startRead(RecordSender recordSender) {
            int fetchSize = this.readerSliceConfig.getInt(Constant.FETCH_SIZE);

            this.commonRdbmsReaderTask.startRead(this.readerSliceConfig, recordSender,
                    super.getTaskPluginCollector(), fetchSize);
        }

        @Override
        public void post() {
            this.commonRdbmsReaderTask.post(this.readerSliceConfig);
        }

        @Override
        public void destroy() {
            this.commonRdbmsReaderTask.destroy(this.readerSliceConfig);
        }

    }

}
