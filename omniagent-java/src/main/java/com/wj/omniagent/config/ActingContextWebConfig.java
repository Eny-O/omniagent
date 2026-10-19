package com.wj.omniagent.config;

import com.wj.omniagent.common.context.ActingContextInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册操作人上下文拦截器。
 * <p>
 * 仅对 /api/** 生效；认证相关接口（登录 / 注销）无需机器身份头，排除之。
 * 该配置独立于 SecurityConfig，不影响现有全放行调试策略。
 */
@Configuration
public class ActingContextWebConfig implements WebMvcConfigurer {

    @Autowired
    private ActingContextInterceptor actingContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(actingContextInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }
}
