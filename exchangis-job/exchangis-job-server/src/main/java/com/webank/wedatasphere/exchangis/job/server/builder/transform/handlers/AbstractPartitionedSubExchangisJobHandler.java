package com.webank.wedatasphere.exchangis.job.server.builder.transform.handlers;

import org.apache.commons.lang3.StringUtils;
import com.webank.wedatasphere.exchangis.common.config.GlobalConfiguration;
import com.webank.wedatasphere.exchangis.datasource.core.exception.ExchangisDataSourceException;
import com.webank.wedatasphere.exchangis.datasource.core.service.MetadataInfoService;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParam;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParamDefine;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParams;
import com.webank.wedatasphere.exchangis.job.exception.ExchangisJobException;
import com.webank.wedatasphere.exchangis.job.server.builder.JobParamConstraints;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Job handler of partition
 */
public abstract class AbstractPartitionedSubExchangisJobHandler extends AuthEnabledSubExchangisJobHandler{
    /**
     * Database
     */
    private static final JobParamDefine<String>  HIVE_DATABASE = JobParams.define("hiveDatabase", JobParamConstraints.DATABASE);
    private static final JobParamDefine<String>  DATABASE = JobParams.define("database", JobParamConstraints.DATABASE);

    /**
     * Table
     */
    private static final JobParamDefine<String> HIVE_TABLE = JobParams.define("hiveTable", JobParamConstraints.TABLE);
    private static final JobParamDefine<String> TABLE = JobParams.define("table", JobParamConstraints.TABLE);

    /**
     * Table partition
     */
    public static final JobParamDefine<Map<String, String>> TABLE_PARTITION = JobParams.define(JobParamConstraints.PARTITION);

    /**
     * Whether to auto create table / 是否自动建表
     */
    protected static final JobParamDefine<Boolean> AUTO_CREATE_TABLE = JobParams.define(
            JobParamConstraints.AUTO_CREATE_TABLE, paramSet -> {
               JobParam<String> autoCreate =  paramSet.get(JobParamConstraints.AUTO_CREATE_TABLE);
               if (null != autoCreate){
                    return Boolean.parseBoolean(autoCreate.getValue());
               }
               return false;
            });

    /**
     * Whether the table exists, actively checked via existsTable when autoCreateTable is on.
     * Returns null when autoCreateTable is off, or when existsTable is unsupported by the data source
     * (callers fall back to the passive degrade logic in that case) / 表是否存在；autoCreateTable 开启时通过
     * existsTable 主动判断；未开启或接口不支持时返回 null，由调用方回退原被动降级逻辑
     */
    protected static final JobParamDefine<Boolean> TABLE_EXISTS = JobParams.define(
            JobParamConstraints.AUTO_CREATE_TABLE + ".tableExists", paramSet -> {
        // 仅在自动建表开启时才有必要主动判断
        if (!Boolean.TRUE.equals(AUTO_CREATE_TABLE.getValue(paramSet))) {
            return null;
        }
        String database = DATABASE.getValue(paramSet);
        if (StringUtils.isBlank(database)) {
            database = HIVE_DATABASE.getValue(paramSet);
        }
        String table = TABLE.getValue(paramSet);
        if (StringUtils.isBlank(table)) {
            table = HIVE_TABLE.getValue(paramSet);
        }
        JobParam<String> dataSourceId = paramSet.get(JobParamConstraints.DATA_SOURCE_ID);
        JobParam<String> dsCreator = paramSet.get(JobParamConstraints.DATA_SOURCE_CREATOR);
        String dsOwner = Objects.nonNull(dsCreator) ? dsCreator.getValue() : GlobalConfiguration.getAdminUser();
        try {
            return Objects.requireNonNull(getBean(MetadataInfoService.class)).existsTable(
                    Optional.ofNullable(dsOwner).orElse(getJobBuilderContext().getOriginalJob().getCreateUser()),
                    Long.parseLong(dataSourceId.getValue()), database, table);
        } catch (ExchangisDataSourceException e) {
            // existsTable 接口不支持该数据源时回退为 null（未知），由调用方走原被动降级逻辑
            debug("Fail to check table existence for [{}.{}] (treat as unknown, fallback to passive degrade)", database, table, e);
            return null;
        }
    });

    /**
     * Partition keys
     */
    protected static final JobParamDefine<List<String>> PARTITION_KEYS = JobParams.define("partitionKeys", paramSet -> {
        JobParam<String> dataSourceId = paramSet.get(JobParamConstraints.DATA_SOURCE_ID);
        List<String> partitionKeys = new ArrayList<>();
        String database = DATABASE.getValue(paramSet);
        if (StringUtils.isBlank(database)) {
            database = HIVE_DATABASE.getValue(paramSet);
        }
        String table = TABLE.getValue(paramSet);
        if (StringUtils.isBlank(table)) {
            table = HIVE_TABLE.getValue(paramSet);
        }
        JobParam<String> dsCreator = paramSet.get(JobParamConstraints.DATA_SOURCE_CREATOR);
        String dsOwner = Objects.nonNull(dsCreator) ? dsCreator.getValue() : GlobalConfiguration.getAdminUser();
        Boolean tableExists = TABLE_EXISTS.getValue(paramSet);
        // 表确实不存在（主动判断）→ 走自动建表降级：从 TABLE_PARTITION 取分区键，不再查询元数据
        if (Boolean.FALSE.equals(tableExists)) {
            debug("Table [{}.{}] not exists (autoCreateTable=true, use keys from table partition)", database, table);
            Map<String, String> tablePartition = TABLE_PARTITION.getValue(paramSet);
            if (Objects.nonNull(tablePartition)) {
                // 过滤掉空值（null/空白）的分区键，避免脏 key 混入后续分区值匹配
                // filter out blank partition keys to avoid dirty keys polluting partition value matching
                partitionKeys = tablePartition.keySet().stream()
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toList());
            }
            return partitionKeys;
        }
        try {
            partitionKeys = Objects.requireNonNull(getBean(MetadataInfoService.class)).getPartitionKeys(
                    Optional.ofNullable(dsOwner).orElse(getJobBuilderContext().getOriginalJob().getCreateUser()),
                    Long.parseLong(dataSourceId.getValue()), database, table);
        } catch (ExchangisDataSourceException e) {
            // tableExists == null（不支持主动判断）才回退原被动降级；tableExists == true（表存在）则异常照常抛出
            if (null == tableExists && Boolean.TRUE.equals(AUTO_CREATE_TABLE.getValue(paramSet))) {
                debug("Fail to query partition keys for [{}.{}] (autoCreateTable=true, use keys from table partition)", database, table, e);
                Map<String, String> tablePartition = TABLE_PARTITION.getValue(paramSet);
                if (Objects.nonNull(tablePartition)) {
                    // 过滤掉空值（null/空白）的分区键，避免脏 key 混入后续分区值匹配
                    // filter out blank partition keys to avoid dirty keys polluting partition value matching
                    partitionKeys = tablePartition.keySet().stream()
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.toList());
                }
            } else {
                throw new ExchangisJobException.Runtime(e.getErrCode(), e.getMessage(), e.getCause());
            }
        }
        return partitionKeys;
    });

    /**
     * Partition values
     */
    protected static final JobParamDefine<String> PARTITION_VALUES = JobParams.define("partitionValues", paramSet -> {
        Map<String, String> partitions = Optional.ofNullable(TABLE_PARTITION.getValue(paramSet)).orElse(new HashMap<>());
        //Try to find actual partition from table properties
        List<String> partitionKeys = PARTITION_KEYS.getValue(paramSet);
        String[] partitionColumns = Objects.isNull(partitionKeys)? new String[0]: partitionKeys.toArray(new String[0]);
        if (partitionColumns.length > 0 && partitions.size() != partitionColumns.length){
            throw new ExchangisJobException.Runtime(-1, "Unmatched partition list: [" +
                    org.apache.commons.lang3.StringUtils.join(partitionColumns, ",") + "]", null);
        }
        if (partitionColumns.length > 0){
            return Arrays.stream(partitionColumns).map(partitions::get).collect(Collectors.joining(","));
        }
        return null;
    });
}
