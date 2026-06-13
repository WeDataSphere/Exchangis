package com.webank.wedatasphere.exchangis.datasource.core;


import com.webank.wedatasphere.exchangis.common.linkis.client.ClientConfiguration;
import org.apache.linkis.common.conf.CommonVars;

/**
 * Exchangis data source config
 */
public class ExchangisDataSourceConfiguration {
    /**
     * Server url
     */
    public static final CommonVars<String> SERVER_URL = CommonVars.apply("wds.exchangis.datasource.client.server-url",
            ClientConfiguration.LINKIS_SERVER_URL.getValue());

    /**
     * Token value
     */
    public static final CommonVars<String> AUTH_TOKEN_VALUE = CommonVars.apply("wds.exchangis.datasource.client.token.value",
            ClientConfiguration.LINKIS_TOKEN_VALUE.getValue());

    /**
     * Dws version
     */
    public static final CommonVars<String> DWS_VERSION = CommonVars.apply("wds.exchangis.datasource.client.dws.version",
            ClientConfiguration.LINKIS_DWS_VERSION.getValue());

    /**
     * Whether to enable kerberos authentication / 是否开启 kerberos 认证
     */
    public static final CommonVars<Boolean> KERBEROS_ENABLE = CommonVars.apply("wds.exchangis.datasource.kerberos.enable", false);

    /**
     * Kerberos realm / Kerberos 域
     */
    public static final CommonVars<String> KERBEROS_REALM = CommonVars.apply("wds.exchangis.datasource.kerberos.realm", "EXAMPLE.COM");

    /**
     * Kerberos keytab path, default "_local" means to use the local keytab on engine side / Kerberos keytab 路径，默认 _local 表示使用 engine 侧本地 keytab
     */
    public static final CommonVars<String> KERBEROS_KEYTAB_PATH = CommonVars.apply("wds.exchangis.datasource.kerberos.keytab.path", "_local");
}
