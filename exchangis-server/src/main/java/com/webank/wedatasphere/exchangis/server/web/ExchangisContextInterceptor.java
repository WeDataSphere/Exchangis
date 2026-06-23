package com.webank.wedatasphere.exchangis.server.web;

import org.apache.linkis.server.security.SecurityFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Exchangis 全局拦截器 (Exchangis global interceptor)
 * <p>
 * 在 Linkis {@code SecurityFilter} 之后、Controller 之前执行，用于：
 * 1. 鉴权：校验登录态与接口访问权限 (auth: validate login & access privilege)
 * 2. 用户上下文注入：将登录用户写入 ThreadLocal 供下游业务使用 (inject login user into ThreadLocal)
 * <p>
 * 实现 {@link ExchangisHandlerInterceptor} 并标注 {@code @Component}，由
 * {@link ExchangisWebMvcConfigurer} 自动收集注册，无需手动 addInterceptor。
 */
@Component
public class ExchangisContextInterceptor implements ExchangisHandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangisContextInterceptor.class);

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
        CURRENT_USER.set(loginUser);
        LOG.debug("preHandle accept user: {} uri: {} (拦截放行 用户: {} 路径: {})",
                loginUser, request.getRequestURI(), loginUser, request.getRequestURI());
        return true;
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
