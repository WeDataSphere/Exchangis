package com.webank.wedatasphere.exchangis.server.web;

import org.apache.commons.lang3.StringUtils;
import org.apache.linkis.server.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import com.webank.wedatasphere.exchangis.common.config.GlobalConfiguration;
import com.webank.wedatasphere.exchangis.common.util.json.Json;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Exchangis 全局拦截器 (Exchangis global interceptor)
 * <p>
 * 在 Linkis {@code SecurityFilter} 之后、Controller 之前执行，仅做 route label 二次校验：
 * 防止 Gateway 在目标环境实例不存在时随机路由导致跨环境串号
 * (route label re-check: prevent cross-env leak when gateway falls back to random routing)
 * <p>
 * route label 从请求头 {@value #ROUTE_HEADER} 读取（由新前端统一注入），
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
     * 环境未就绪时的错误提示 (error message when environment is not ready)
     */
    private static final String ENV_NOT_READY_MSG =
            "Environment is not loaded, please retry later (环境未成功加载，请稍后重试)";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // route label 二次校验 (route label re-check)
        if (!validateRouteLabel(request)) {
            String requestRoute = request.getHeader(ROUTE_HEADER);
            String serverRoute = GlobalConfiguration.SERVER_ROUTE_LABEL.getValue();
            LOG.warn("preHandle reject: route mismatch for {} (route 校验拒绝: 请求={}, 本实例={}, 路径={})",
                    request.getRequestURI(), requestRoute, serverRoute, request.getRequestURI());
            // HTTP 503：服务暂不可用；Message status=1(ERROR)，与现有接口错误格式一致，不触发前端登录跳转
            // HTTP 503 service unavailable; Message status=1(ERROR), same format as existing error responses
            writeErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, ENV_NOT_READY_MSG);
            return false;
        }
        return true;
    }

    /**
     * 将错误以 Message 格式（application/json;charset=UTF-8）写入 response body (write error as Message JSON)
     */
    private void writeErrorResponse(HttpServletResponse response, int httpStatus, String message) throws Exception {
        Message msg = Message.error(message);
        // Message.error 默认 status=1(ERROR)，与现有 controller 错误响应格式一致
        String body = Json.toJson(msg, null);
        response.setStatus(httpStatus);
        response.setHeader("Content-Type", "application/json;charset=UTF-8");
        response.getOutputStream().print(body);
        response.getOutputStream().flush();
    }

    /**
     * 校验请求 route label 是否与本实例匹配 (validate request route label against this instance)
     * <p>
     * 规则：
     * 1. 本实例未配置 route（空串）→ 放行（兼容单环境部署）
     * 2. 请求未携带 {@value #ROUTE_HEADER} → 放行（兼容旧前端，未升级前端的请求不阻断）
     * 3. 本实例配置了 route 且请求携带了 header → 两者必须相等，否则拒绝
     */
    private boolean validateRouteLabel(HttpServletRequest request) {
        String serverRoute = GlobalConfiguration.SERVER_ROUTE_LABEL.getValue();
        if (StringUtils.isBlank(serverRoute)) {
            // 本实例未配置 route，不做校验 (no route configured on this instance, skip)
            return true;
        }
        String requestRoute = request.getHeader(ROUTE_HEADER);
        if (StringUtils.isBlank(requestRoute)) {
            // 请求未带 route header，兼容旧前端放行 (no route header, pass for old-frontend compatibility)
            return true;
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
        // no-op
    }

    @Override
    public int order() {
        // 标签校验尽早执行 (route check should run early)
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
