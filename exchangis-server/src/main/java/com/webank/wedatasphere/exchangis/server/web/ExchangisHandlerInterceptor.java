package com.webank.wedatasphere.exchangis.server.web;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Exchangis 拦截器扩展接口 (Exchangis interceptor extension interface)
 * <p>
 * 实现该接口并标注 {@code @Component} 即可被 {@link ExchangisWebMvcConfigurer}
 * 自动收集并注册为 Spring MVC 拦截器，无需在 configurer 中手动 addInterceptor。
 * <p>
 * 自动注册条件：
 * 1. 实现本接口（继承 {@link HandlerInterceptor}）
 * 2. 声明为 Spring Bean（{@code @Component} / {@code @Configuration} 等，所在包在
 *    {@code DataWorkCloudApplication} 的 scanBasePackages 范围内，即
 *    {@code com.webank.wedatasphere.*} 下）
 * <p>
 * path pattern 与 order 由拦截器自身决定，互不干扰。
 */
public interface ExchangisHandlerInterceptor extends HandlerInterceptor {

    /**
     * 拦截路径，默认覆盖 Exchangis REST 接口 (intercept path, default to Exchangis REST endpoints)
     */
    default String[] pathPatterns() {
        return new String[]{"/api/rest_j/v1/**"};
    }

    /**
     * 排除路径 (excluded paths)
     */
    default String[] excludePathPatterns() {
        return new String[0];
    }

    /**
     * 执行顺序，默认最低优先级 (order, default lowest precedence)
     */
    default int order() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
