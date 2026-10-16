package com.wj.omniagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.pojo.result.ResponseResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置
 * <p>
 * 采用 Session/Cookie 方案：
 * - 禁用 CSRF（前后端分离，接口由 axios 调用）
 * - 登录/登出由自定义 /api/auth/* 接口处理
 * - 未认证返回 401 JSON，无权限返回 403 JSON
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 密码加密器：与 UserServiceImpl 共用同一个 Bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器：供 AuthServiceImpl 手动调用登录认证
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * CORS 配置：允许前端开发服务器跨域访问，并携带 Cookie/Session
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Vue 开发服务器默认地址，生产环境可改为前端部署域名
        configuration.addAllowedOrigin("http://localhost:5173");
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        // 允许携带 Cookie（Session-Cookie）
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF，前后端分离场景不适用
                .csrf(AbstractHttpConfigurer::disable)

                // 启用 CORS，使用上方定义的 CorsConfigurationSource
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 配置 URL 授权
                .authorizeHttpRequests(auth -> auth
                        // 认证接口：登录放行，登出/当前用户需要认证
                        .requestMatchers("/api/auth/login").permitAll()

                        // Swagger / Knife4j 文档资源放行
                        .requestMatchers(
                                "/doc.html",
                                "/webjars/**",
                                "/swagger-resources/**",
                                "/v3/api-docs/**",
                                "/favicon.ico",
                                "/error"
                        ).permitAll()

                        // 其他所有接口需要认证
                        .anyRequest().authenticated()
                )

                // 禁用 Spring Security 默认表单登录，使用自定义 /api/auth/login
                .formLogin(AbstractHttpConfigurer::disable)

                // 禁用默认 logout，使用自定义 /api/auth/logout
                .logout(AbstractHttpConfigurer::disable)

                // Session 策略：需要时才创建（登录成功后创建 Session）
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                // 异常处理：返回统一 JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    /**
     * 未认证处理：返回 401 JSON
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeErrorResponse(
                response,
                HttpStatus.UNAUTHORIZED.value(),
                ErrorCodeEnum.UNAUTHORIZED
        );
    }

    /**
     * 无权限处理：返回 403 JSON
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeErrorResponse(
                response,
                HttpStatus.FORBIDDEN.value(),
                ErrorCodeEnum.FORBIDDEN
        );
    }

    private void writeErrorResponse(HttpServletResponse response, int status, ErrorCodeEnum errorCodeEnum) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ResponseResult<Void> result = ResponseResult.error(errorCodeEnum);
        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
