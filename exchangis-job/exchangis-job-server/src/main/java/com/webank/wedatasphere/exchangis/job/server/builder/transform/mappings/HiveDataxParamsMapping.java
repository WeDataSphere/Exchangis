package com.webank.wedatasphere.exchangis.job.server.builder.transform.mappings;

import com.webank.wedatasphere.exchangis.common.config.GlobalConfiguration;
import com.webank.wedatasphere.exchangis.common.util.PatternInjectUtils;
import com.webank.wedatasphere.exchangis.datasource.core.ExchangisDataSourceConfiguration;
import com.webank.wedatasphere.exchangis.datasource.core.exception.ExchangisDataSourceException;
import com.webank.wedatasphere.exchangis.datasource.core.service.MetadataInfoService;
import com.webank.wedatasphere.exchangis.job.domain.SubExchangisJob;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParam;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParamDefine;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParams;
import com.webank.wedatasphere.exchangis.job.exception.ExchangisJobException;
import com.webank.wedatasphere.exchangis.job.server.builder.JobParamConstraints;
import org.apache.commons.lang3.StringUtils;
import org.apache.linkis.common.conf.CommonVars;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Hive datax mapping
 */
public class HiveDataxParamsMapping extends AbstractExchangisJobParamsMapping{

    private static final Map<String, Type> FIELD_MAP = new HashMap<>();

    private static final BitSet CHAR_TO_ESCAPE = new BitSet(128);

    private static final String[] SOURCE_SUPPORT_FILETYPE = new String[]{"TEXT", "ORC","RC","SEQ","CSV"};

    private static final String[] SINK_SUPPORT_FILETYPE = new String[]{"ORC", "TEXT"};

    /**
     * Default hive warehouse uri pattern when the table location is absent / 表 location 缺失时默认的 hive warehouse 路径模板
     */
    private static final CommonVars<String> HIVE_WAREHOUSE_PATTERN = CommonVars.apply("wds.exchangis.job.builder.hive.warehouse", "/user/hive/warehouse/${user}");

    private enum Type {
        /**
         * types that supported by <em>DataX</em>
         */
        STRING, LONG, BOOLEAN, DOUBLE, DATE, BINARY, OBJECT
    }
    //hive type => dataX type
    static{
        FIELD_MAP.put("TINYINT", Type.LONG);
        FIELD_MAP.put("SMALLINT", Type.LONG);
        FIELD_MAP.put("INT", Type.LONG);
        FIELD_MAP.put("BIGINT", Type.LONG);
        FIELD_MAP.put("FLOAT", Type.DOUBLE);
        FIELD_MAP.put("DOUBLE", Type.DOUBLE);
        FIELD_MAP.put("DECIMAL", Type.DOUBLE);
        FIELD_MAP.put("STRING", Type.STRING);
        FIELD_MAP.put("CHAR", Type.STRING);
        FIELD_MAP.put("VARCHAR", Type.STRING);
        FIELD_MAP.put("STRUCT", Type.STRING);
        FIELD_MAP.put("MAP", Type.OBJECT);
        FIELD_MAP.put("ARRAY", Type.OBJECT);
        FIELD_MAP.put("UNION", Type.STRING);
        FIELD_MAP.put("BINARY", Type.BINARY);
        FIELD_MAP.put("BOOLEAN", Type.BOOLEAN);
        FIELD_MAP.put("DATE", Type.DATE);
        FIELD_MAP.put("TIMESTAMP", Type.DATE);
        for(char c = 0; c < ' '; ++c) {
            CHAR_TO_ESCAPE.set(c);
        }
        char[] clist = new char[]{'\u0001', '\u0002', '\u0003', '\u0004', '\u0005', '\u0006', '\u0007', '\b', '\t', '\n', '\u000b',
                '\f', '\r', '\u000e', '\u000f', '\u0010', '\u0011', '\u0012', '\u0013', '\u0014', '\u0015', '\u0016', '\u0017', '\u0018',
                '\u0019', '\u001a', '\u001b', '\u001c', '\u001d', '\u001e', '\u001f', '"', '#', '%', '\'', '*', '/', ':', '=', '?', '\\', '\u007f', '{', '[', ']', '^'};

        for (char c : clist) {
            CHAR_TO_ESCAPE.set(c);
        }
    }

    /**
     * Whether to auto create table / 是否自动建表
     */
    private static final JobParamDefine<Boolean> AUTO_CREATE_TABLE = JobParams.define(
            "autoCreateTable", JobParamConstraints.AUTO_CREATE_TABLE,
            (Function<String, Boolean>) Boolean::valueOf, String.class);

    /**
     * Hive database
     */
    private static final JobParamDefine<String>  HIVE_DATABASE = JobParams.define("hiveDatabase", JobParamConstraints.DATABASE);

    /**
     * Hive table
     */
    private static final JobParamDefine<String> HIVE_TABLE = JobParams.define("hiveTable", JobParamConstraints.TABLE);

    /**
     * Hive uris
     */
    private static final JobParamDefine<String> HIVE_URIS = JobParams.define("hiveMetastoreUris", "uris");

    /**
     * Data file name (prefix)
     */
    private static final JobParamDefine<String> DATA_FILE_NAME = JobParams.define("fileName", () -> "exch_hive_");
    /**
     * Encoding
     */
    private static final JobParamDefine<String> ENCODING  = JobParams.define("encoding", paramSet -> {
        JobParam<String> encodingParam = paramSet.get(JobParamConstraints.ENCODING);
        if (Objects.nonNull(encodingParam)){
            return encodingParam.getValue();
        }
        return "utf-8";
    });

    /**
     * Null format
     */
    private static final JobParamDefine<String> NULL_FORMAT = JobParams.define("nullFormat", paramSet -> {
        JobParam<String> nullFormatParam = paramSet.get(JobParamConstraints.NULL_FORMAT);
        if (Objects.nonNull(nullFormatParam)){
            return nullFormatParam.getValue();
        }
        return "\\N";
    });


    /**
     * Table properties
     */
    private static final JobParamDefine<Map<String, String>> HIVE_TABLE_PROPS = JobParams.define("tableProps", paramSet -> {
        String database = HIVE_DATABASE.getValue(paramSet);
        String table = HIVE_TABLE.getValue(paramSet);
        JobParam<String> dataSourceId = paramSet.get(JobParamConstraints.DATA_SOURCE_ID);
        JobParam<String> dsCreator = paramSet.get(JobParamConstraints.DATA_SOURCE_CREATOR);
        String dsOwner = Objects.nonNull(dsCreator) ? dsCreator.getValue() : GlobalConfiguration.getAdminUser();
        try {
           return Objects.requireNonNull(getBean(MetadataInfoService.class)).getTableProps(
                   Optional.ofNullable(dsOwner).orElse(getJobBuilderContext().getOriginalJob().getCreateUser()),
                    Long.valueOf(dataSourceId.getValue()), database, table);
        } catch (ExchangisDataSourceException e) {
            // If autoCreateTable is enabled, swallow the query exception and return empty props / 开启自动建表时，吞掉查询异常并返回空信息
            if (Boolean.TRUE.equals(AUTO_CREATE_TABLE.getValue(paramSet))){
                debug("Fail to query table props for [{}.{}] (autoCreateTable=true, ignore it)", database, table, e);
                return new HashMap<>();
            }
            throw new ExchangisJobException.Runtime(e.getErrCode(), e.getMessage(), e.getCause());
        }
    });

    /**
     * Database properties (query with empty table to get the database-level props) / 数据库属性（table 传空串，获取库级属性）
     */
    private static final JobParamDefine<Map<String, String>> HIVE_DB_PROPS = JobParams.define("dbProps", paramSet -> {
        String database = HIVE_DATABASE.getValue(paramSet);
        JobParam<String> dataSourceId = paramSet.get(JobParamConstraints.DATA_SOURCE_ID);
        JobParam<String> dsCreator = paramSet.get(JobParamConstraints.DATA_SOURCE_CREATOR);
        String dsOwner = Objects.nonNull(dsCreator) ? dsCreator.getValue() : GlobalConfiguration.getAdminUser();
        try {
            return Objects.requireNonNull(getBean(MetadataInfoService.class)).getTableProps(
                    Optional.ofNullable(dsOwner).orElse(getJobBuilderContext().getOriginalJob().getCreateUser()),
                    Long.valueOf(dataSourceId.getValue()), database, "__DB_DEFAULT__");
        } catch (ExchangisDataSourceException e) {
            // If autoCreateTable is enabled, swallow the query exception and return empty props / 开启自动建表时，吞掉查询异常并返回空信息
            if (Boolean.TRUE.equals(AUTO_CREATE_TABLE.getValue(paramSet))){
                debug("Fail to query database props for [{}] (autoCreateTable=true, ignore it)", database, e);
                return new HashMap<>();
            }
            throw new ExchangisJobException.Runtime(e.getErrCode(), e.getMessage(), e.getCause());
        }
    });

    /**
     * Database location (taken from the database-level props, empty if absent) / 库级 location，从 HIVE_DB_PROPS 取，没有则为空
     */
    private static final JobParamDefine<String> DB_LOCATION = JobParams.define("dbLocation", paramSet ->
            HIVE_DB_PROPS.getValue(paramSet).getOrDefault("location", ""));


    /**
     * Field delimiter
     */
    private static final JobParamDefine<String> FIELD_DELIMITER = JobParams.define("fieldDelimiter", paramSet ->
            HIVE_TABLE_PROPS.getValue(paramSet).getOrDefault("field.delim", "\u0001"));

    /**
     * File type
     */
    private static final JobParamDefine<HiveV2FileType> FILE_TYPE = JobParams.define("fileType", paramSet -> {
        Map<String, String> tableProps = HIVE_TABLE_PROPS.getValue(paramSet);
        AtomicReference<HiveV2FileType> fileType = new AtomicReference<>();
        // set 前先判断值非 null，避免 serde/input/output 返回 null 覆盖前面已识别出的有效类型
        // check non-null before set, to avoid null from serde/input/output overwriting a valid type already found
        Optional.ofNullable(tableProps.get("serialization.lib")).ifPresent(serLib -> {
            HiveV2FileType type = HiveV2FileType.serde(serLib);
            if (Objects.nonNull(type)) {
                fileType.set(type);
            }
        });
        if (Objects.nonNull(fileType.get())){
            Optional.ofNullable(tableProps.get("file.inputformat")).ifPresent(inputFormat -> {
                HiveV2FileType type = HiveV2FileType.input(inputFormat);
                if (Objects.nonNull(type)) {
                    fileType.set(type);
                }
            });
        }
        if (Objects.nonNull(fileType.get())){
            Optional.ofNullable(tableProps.get("file.outputformat")).ifPresent(outputFormat -> {
                HiveV2FileType type = HiveV2FileType.output(outputFormat);
                if (Objects.nonNull(type)) {
                    fileType.set(type);
                }
            });
        }
        if (Objects.nonNull(fileType.get())){
            return fileType.get();
        }
        // 未识别出文件类型时的默认值：自动建表开启 且 tableProps 为空（表不存在）才默认 ORC，否则 TEXT
        // default when no file type recognized: ORC only if autoCreateTable enabled AND tableProps empty (table absent), otherwise TEXT
        boolean autoCreate = Boolean.TRUE.equals(AUTO_CREATE_TABLE.getValue(paramSet));
        boolean tableAbsent = tableProps == null || tableProps.isEmpty();
        return (autoCreate && tableAbsent) ? HiveV2FileType.ORC : HiveV2FileType.TEXT;
    });

    /**
     * Data location
     */
    private static final JobParamDefine<String[]> DATA_LOCATION = JobParams.define("location", paramSet -> {
        Map<String, String> tableProps = HIVE_TABLE_PROPS.getValue(paramSet);
        String location = tableProps.getOrDefault("location", "");
        String path = "";
        if (StringUtils.isNotBlank(location)){
            try {
                path = new URI(location).getPath();
            } catch (URISyntaxException e) {
                warn("Unrecognized location: [{}]", location,  e);
            }
        }
        String partitionValues = PARTITION_VALUES.getValue(paramSet);
        String suffixPath = "";
        if (StringUtils.isNotBlank(partitionValues)){
            String[] values = partitionValues.split(",");
            String[] keys = PARTITION_KEYS.getValue(paramSet).toArray(new String[0]);
            // Escape the path and value of partition
            StringBuilder pathBuilder = new StringBuilder().append("/");
            for(int i = 0; i < keys.length; i++){
                if (i > 0){
                    pathBuilder.append("/");
                }
                pathBuilder.append(escapeHivePathName(keys[i]));
                pathBuilder.append("=");
                // Not to escape all the value
                pathBuilder.append(PatternInjectUtils.REGEX.matcher(values[i]).find() ?
                        values[i] : escapeHivePathName(values[i]));
            }
            suffixPath = pathBuilder.toString();
        }
        suffixPath = suffixPath.replace(" ", "%20");
        return new String[]{location, location + suffixPath, path + suffixPath};
    });

    /**
     * Compress name
     */
    private static final JobParamDefine<String> COMPRESS_NAME = JobParams.define("compress", paramSet -> {
        HiveV2FileType fileType = FILE_TYPE.getValue(paramSet);
        if (HiveV2FileType.TEXT.equals(fileType)){
            return "GZIP";
        } else if (HiveV2FileType.ORC.equals(fileType)){
            return "SNAPPY";
        }
        return null;
    });

    /**
     * Data path
     */
    private static final JobParamDefine<String> DATA_PATH = JobParams.define("path", paramSet -> {
        String[] location = DATA_LOCATION.getValue(paramSet);
        // If the original location is blank, do not return the constructed path / 原始 location 为空时，不返回构造的路径
        if (StringUtils.isNotBlank(location[0]) && StringUtils.isNotBlank(location[2])){
            return location[2];
        }
        return null;
    });

    /**
     * Hadoop config
     */
    private static final JobParamDefine<Map<String, String>> HADOOP_CONF = JobParams.define("hadoopConfig", paramSet -> {
        String[] location = DATA_LOCATION.getValue(paramSet);
        String uri = location[0];
        // If the table location is absent, first try the database location, then fall back to the default warehouse pattern
        // 表 location 为空时，先尝试库级 location，仍为空再用提交用户通过默认 warehouse 模板生成
        if (StringUtils.isBlank(uri)){
            uri = DB_LOCATION.getValue(paramSet);
        }
        if (StringUtils.isBlank(uri)){
            Object userName = getJobBuilderContext().getEnv("USER_NAME");
            uri = PatternInjectUtils.inject(HIVE_WAREHOUSE_PATTERN.getValue(),
                    Collections.singletonMap("user", Objects.nonNull(userName) ? userName : ""));
        }
        try {
            // TODO get the other hdfs cluster with tab
            return Objects.requireNonNull(getBean(MetadataInfoService.class)).getLocalHdfsInfo(uri);
        } catch (ExchangisDataSourceException e) {
            // If autoCreateTable is enabled, swallow the exception and return empty hadoop config / 开启自动建表时降级返回空配置
            if (Boolean.TRUE.equals(AUTO_CREATE_TABLE.getValue(paramSet))){
                debug("Fail to query local hdfs info for uri [{}] (autoCreateTable=true, ignore it)", uri, e);
                return new HashMap<>();
            }
            throw new ExchangisJobException.Runtime(e.getErrCode(), e.getDesc(), e.getCause());
        }
    });

    /**
     * Whether the hadoop cluster enables kerberos authentication / Hadoop 集群是否开启 kerberos 认证
     */
    private static final String KERBEROS_AUTH_KEY = "hadoop.security.authentication";

    private static final JobParamDefine<Boolean> HAVE_KERBEROS = JobParams.define("haveKerberos", paramSet -> {
        // KERBEROS_ENABLE is on AND hadoop.security.authentication == kerberos
        // KERBEROS_ENABLE 开启 且 hadoopConfig 中 hadoop.security.authentication 为 kerberos
        if (!Boolean.TRUE.equals(ExchangisDataSourceConfiguration.KERBEROS_ENABLE.getValue())){
            return false;
        }
        Map<String, String> hadoopConf = HADOOP_CONF.getValue(paramSet);
        return Objects.nonNull(hadoopConf) && "kerberos".equalsIgnoreCase(hadoopConf.get(KERBEROS_AUTH_KEY));
    });

    /**
     * Kerberos principal (submitUser@REALM) / Kerberos 主体（提交用户@域）
     */
    private static final JobParamDefine<String> KERBEROS_PRINCIPAL = JobParams.define("kerberosPrincipal", paramSet -> {
        if (!Boolean.TRUE.equals(HAVE_KERBEROS.getValue(paramSet))){
            return null;
        }
        Object userName = getJobBuilderContext().getEnv("USER_NAME");
        String user = Objects.nonNull(userName) ? String.valueOf(userName) : "";
        return user + "@" + ExchangisDataSourceConfiguration.KERBEROS_REALM.getValue();
    });

    /**
     * Kerberos keytab path / Kerberos keytab 路径
     */
    private static final JobParamDefine<String> KERBEROS_KEYTAB_PATH = JobParams.define("kerberosKeytabPath", paramSet -> {
        if (!Boolean.TRUE.equals(HAVE_KERBEROS.getValue(paramSet))){
            return null;
        }
        // Prefer the configured value, otherwise default to "_local" / 优先取配置，否则默认 _local
        return ExchangisDataSourceConfiguration.KERBEROS_KEYTAB_PATH.getValue();
    });

    /**
     * To "defaultFS"
     */
    private static final JobParamDefine<String> DEFAULT_FS = JobParams.define("defaultFS", paramSet ->
            HADOOP_CONF.getValue(paramSet).get("fs.defaultFS"));

    private static final JobParamDefine<String> IS_SINK_FILETYPE_SUPPORT = JobParams.define("sink.fileType.support", paramSet -> {
        if (!isSupport(FILE_TYPE.getValue(paramSet).name(), SINK_SUPPORT_FILETYPE)){
            throw new ExchangisJobException.Runtime(-1, "Unsupported sink file type [" + FILE_TYPE.getValue(paramSet).name() + "] of hive", null);
        }
        return null;
    });

    private static final JobParamDefine<String> IS_SOURCE_FILETYPE_SUPPORT = JobParams.define("sink.fileType.support", paramSet -> {
        if (!isSupport(FILE_TYPE.getValue(paramSet).name(), SOURCE_SUPPORT_FILETYPE)){
            throw new ExchangisJobException.Runtime(-1, "Unsupported source file type [" + FILE_TYPE.getValue(paramSet).name() + "] of hive", null);
        }
        return null;
    });
    // TODO kerberos params

    /**
     * Escape hive path name
     * @param path path name
     * @return path
     */
    protected static String escapeHivePathName(String path) {
        if (path != null && path.length() != 0) {
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < path.length(); ++i) {
                char c = path.charAt(i);
                if (c < CHAR_TO_ESCAPE.size() && CHAR_TO_ESCAPE.get(c)) {
                    sb.append('%');
                    sb.append(String.format("%1$02X", (int) c));
                } else {
                    sb.append(c);
                }
            }

            return sb.toString();
        } else {
            return "__HIVE_DEFAULT_PARTITION__";
        }
    }

    protected static boolean isSupport(String value, String[] array){
        boolean isSupport = false;
        for(String item: array){
            if(item.equalsIgnoreCase(value)){
                isSupport = true;
                break;
            }
        }
        return isSupport;
    }

    @Override
    public JobParamDefine<?>[] sourceMappings() {
        return new JobParamDefine[]{HIVE_DATABASE, HIVE_TABLE, ENCODING,
        NULL_FORMAT, PARTITION_VALUES, FIELD_DELIMITER, FILE_TYPE, DATA_PATH, HADOOP_CONF, DEFAULT_FS,
                HAVE_KERBEROS, KERBEROS_PRINCIPAL, KERBEROS_KEYTAB_PATH, IS_SOURCE_FILETYPE_SUPPORT};
    }

    @Override
    public JobParamDefine<?>[] sinkMappings() {
        return new JobParamDefine[]{HIVE_DATABASE, HIVE_TABLE, ENCODING, AUTO_CREATE_TABLE,
                NULL_FORMAT, PARTITION_KEYS, PARTITION_VALUES, FIELD_DELIMITER, FILE_TYPE, DATA_PATH, HADOOP_CONF, DEFAULT_FS,
                HAVE_KERBEROS, KERBEROS_PRINCIPAL, KERBEROS_KEYTAB_PATH,
                COMPRESS_NAME, IS_SINK_FILETYPE_SUPPORT, HIVE_URIS, DATA_FILE_NAME};
    }

    @Override
    protected Consumer<SubExchangisJob.ColumnDefine> srcColumnMappingFunc() {
        return columnDefine -> {
            String type = columnDefine.getType();
            Type t = FIELD_MAP.get(type.toUpperCase().replaceAll("[(<（][\\s\\S]+", ""));
            if (null != t){
                columnDefine.setType(t.toString());
                if (t == Type.OBJECT){
                    // Set the raw column type
                    columnDefine.setRawType(type);
                }
            } else {
                columnDefine.setType(Type.STRING.toString());
            }
        };
    }

    @Override
    protected Consumer<SubExchangisJob.ColumnDefine> sinkColumnMappingFunc() {
        return columnDefine -> columnDefine.setType(columnDefine.getType().replaceAll("[(<（][\\s\\S]+", ""));
    }

    @Override
    public String dataSourceType() {
        return "hive";
    }

    @Override
    public boolean acceptEngine(String engineType) {
        return "datax".equalsIgnoreCase(engineType);
    }

}
