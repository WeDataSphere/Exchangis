package com.webank.wedatasphere.exchangis.common.enums;

public enum ExchangisDataSourceType {

    ELASTICSEARCH("ELASTICSEARCH", "分布式全文索引"),

    HIVE("HIVE", "大数据存储"),

    MONGODB("MONGODB", "非关系型数据库"),

    MYSQL("MYSQL", "关系型数据库"),

    SFTP("SFTP", "sftp连接"),

    ORACLE("ORACLE", "关系型数据库"),

    STARROCKS("STARROCKS", "大数据存储"),

    TDSQL("TDSQL", "大数据存储"),

    DB2("DB2", "关系型数据库"),

    OSCAR("OSCAR", "关系型数据库"),

    /**
     * File source type (architecture ② / M5).
     * <p>⚠️ This enum entry is ONLY a type identifier for job content parsing
     * ({@code ExchangisJobDataSourcesContent.ExchangisJobDataSource.setType(String)} calls
     * {@code ExchangisDataSourceType.valueOf(type)}). File source does NOT register as an
     * {@code ExchangisDataSourceDefinition} subclass, is NOT loaded by
     * {@code ExchangisDataSourceDefLoader}, and does NOT appear in datasource management
     * (the type list comes from Linkis + the Definition loader, not from this enum).
     *
     * <p>本枚举项仅作 job content 解析的类型标识符（{@code setType} 内 {@code valueOf} 需要）。
     * 文件 source 不注册为 {@code ExchangisDataSourceDefinition} 子类、不进
     * {@code ExchangisDataSourceDefLoader} 加载范围、不出现于数据源管理（类型列表来自
     * Linkis + Definition loader，不遍历本枚举）——符合架构②/M5。
     */
    FILE("FILE", "文件");

    /**
     * Type name
     */
    public String name;

    /**
     * Classifier
     */
    public String classifier;
    ExchangisDataSourceType(String name, String classifier) {
        this.name = name;
        this.classifier = classifier;
    }
}
