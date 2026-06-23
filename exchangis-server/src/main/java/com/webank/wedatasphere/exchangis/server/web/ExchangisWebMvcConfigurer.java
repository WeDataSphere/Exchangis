package com.webank.wedatasphere.exchangis.server.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Exchangis Web MVC 配置 (Exchangis WebMvc configuration)
 * <p>
 * 自动收集容器中所有 {@link ExchangisHandlerInterceptor} Bean 并注册为 Spring MVC 拦截器，
 * 新增拦截器只需实现该接口并标注 {@code @Component}，无需修改本类。
 * <p>
 * 与 Linkis 的 {@code InterceptorConfigure} 并存，Spring 会自动合并多个
 * {@code WebMvcConfigurer} 的 {@code addInterceptors} 注册。
 * <p>
 * 所在包 {@code com.webank.wedatasphere.exchangis.server.web} 处于
 * {@code DataWorkCloudApplication} 的 scanBasePackages 范围内，会被 Spring 容器扫描到。
 */
@Configuration
public class ExchangisWebMvcConfigurer implements WebMvcConfigurer {

    @Autowired
    private List<ExchangisHandlerInterceptor> interceptors;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (interceptors == null || interceptors.isEmpty()) {
            return;
        }
        for (ExchangisHandlerInterceptor interceptor : interceptors) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns(interceptor.pathPatterns())
                    .excludePathPatterns(interceptor.excludePathPatterns())
                    .order(interceptor.order());
        }
    }
}
