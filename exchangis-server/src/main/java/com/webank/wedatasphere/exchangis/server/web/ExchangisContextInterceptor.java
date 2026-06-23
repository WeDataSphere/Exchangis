package com.webank.wedatasphere.exchangis.server.web;

import org.apache.linkis.server.security.SecurityFilter;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import com.webank.wedatasphere.exchangis.common.config.GlobalConfiguration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Exchangis 全局拦截器 (Exchangis global interceptor)
 * <p>
 * 在 Linkis {@code SecurityFilter} 之后、Controller 之前执行，用于：
 * 1. 鉴权：校验登录态与接口访问权限 (auth: validate login & access privilege)
 * 2. 用户上下文注入：将登录用户写入 ThreadLocal 供下游业务使用 (inject login user into ThreadLocal)
 * 3. route label 二次校验：防止 Gateway 在目标环境实例不存在时随机路由导致跨环境串号
 *    (route label re-check: prevent cross-env leak when gateway falls back to random routing)
 * <p>
 * route label 从请求头 {@value #ROUTE_HEADER} 读取（由前端统一注入），
 * 与本实例 {@link GlobalConfiguration#SERVER_ROUTE_LABEL} 比对。
 * <p>
 * 实现 {@link ExchangisHandlerInterceptor} 并标注 {@code @Component}，由
 * {@link ExchangisWebMvcConfigurer} 自动收集注册，无需手动 addInterceptor。
 */
@Component
public class ExchangisContextInterceptor implements ExchangisHandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangisContextInterceptor.class);

    /**
     * 请求头中携带 route label 的 header 名 (header name carrying route label)
     */
    public static final String ROUTE_HEADER = "X-Exchangis-Route";

    /**
     * ThreadLocal 持有当前请求登录用户，供非 Controller 层（service/dao）获取 (hold login user for non-web layers)
     */
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    public static String currentUser() {
        return CURRENT_USER.get();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // SecurityFilter 已先执行，此处可直接取登录用户 (SecurityFilter runs first, login user is available)
        String loginUser = SecurityFilter.getLoginUsername(request);
        if (loginUser == null || loginUser.isEmpty()) {
            LOG.warn("preHandle reject: no login user for {} (未登录访问被拒绝: {})",
                    request.getRequestURI(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // route label 二次校验（先校验再写 ThreadLocal，避免校验失败导致用户上下文泄漏）
        // route label re-check (validate before setting ThreadLocal to avoid context leak on failure)
        if (!validateRouteLabel(request)) {
            String requestRoute = request.getHeader(ROUTE_HEADER);
            String serverRoute = GlobalConfiguration.SERVER_ROUTE_LABEL.getValue();
            LOG.warn("preHandle reject: route mismatch for {} (route 校验拒绝: 请求={}, 本实例={}, 路径={})",
                    request.getRequestURI(), requestRoute, serverRoute, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        CURRENT_USER.set(loginUser);
        LOG.debug("preHandle accept user: {} uri: {} (拦截放行 用户: {} 路径: {})",
                loginUser, request.getRequestURI(), loginUser, request.getRequestURI());
        return true;
    }

    /**
     * 校验请求 route label 是否与本实例匹配 (validate request route label against this instance)
     * <p>
     * 规则：
     * 1. 本实例未配置 route（空串）→ 跳过校验，放行（兼容单环境部署）
     * 2. 本实例配置了 route → 请求头必须携带相同 route，否则拒绝
     */
    private boolean validateRouteLabel(HttpServletRequest request) {
        String serverRoute = GlobalConfiguration.SERVER_ROUTE_LABEL.getValue();
        if (StringUtils.isBlank(serverRoute)) {
            // 本实例未配置 route，不做校验 (no route configured on this instance, skip)
            return true;
        }
        String requestRoute = request.getHeader(ROUTE_HEADER);
        if (StringUtils.isBlank(requestRoute)) {
            // 本实例有 route 但请求未带 → 拒绝 (instance has route but request missing it)
            return false;
        }
        return serverRoute.equals(requestRoute);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
        // no-op
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) throws Exception {
        // 必须清理，避免线程池复用导致的用户串号 (must clear to avoid user leak across pooled threads)
        CURRENT_USER.remove();
    }

    @Override
    public int order() {
        // 鉴权/上下文注入需最先执行 (auth & context injection should run first)
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
