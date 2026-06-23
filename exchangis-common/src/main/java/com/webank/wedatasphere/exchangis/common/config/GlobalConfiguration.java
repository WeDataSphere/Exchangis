package com.webank.wedatasphere.exchangis.common.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.linkis.common.conf.CommonVars;

import java.util.ArrayList;
import java.util.List;

/**
 * Global configuration for exchangis
 */
public class GlobalConfiguration {
    public static final CommonVars<String> ADMIN_USERS = CommonVars.apply(
            "wds.exchangis.common.auth.admin", "hadoop");

    public static final CommonVars<Boolean> PROXY_MODE = CommonVars.apply(
            "wds.exchangis.common.user.proxy.mode", true
    );
    public static final String AUTH_SEPARATOR = ",";

    /**
     * Route label of this Exchangis instance (environment identifier, e.g. DEV/PROD)
     * 用于与请求携带的 route label 做二次校验，防止 Gateway 在目标环境实例不存在时随机路由导致跨环境串号
     * Default empty: if empty, route validation is skipped (兼容单环境部署) / 为空时不做校验
     */
    public static final CommonVars<String> SERVER_ROUTE_LABEL = CommonVars.apply(
            "wds.exchangis.server.route", ""
    );


    private static final List<String> administrators = new ArrayList<>();

    static {
        String adminStr = ADMIN_USERS.getValue();
        if (StringUtils.isNotBlank(adminStr)) {
            for (String admin : adminStr.split(AUTH_SEPARATOR)) {
                if (StringUtils.isNotBlank(admin)) {
                    administrators.add(admin);
                }
            }
        }
    }

    /**
     * Get admin user
     * @return username
     */
    public static String getAdminUser(){
        return administrators.size() > 0? administrators.get(0) : null;
    }

    /**
     * Is admin user
     * @param username username
     * @return bool
     */
    public static boolean isAdminUser(String username){
        return administrators.contains(username) ;
    }

}
