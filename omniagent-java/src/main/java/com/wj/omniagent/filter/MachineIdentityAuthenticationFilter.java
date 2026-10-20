package com.wj.omniagent.filter;

import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 机器身份（Agent）认证过滤器：在 Spring Security 授权过滤器之前运行。
 * - 无 X-API-Key：放行，交给 Session 登录流程处理（浏览器/前端）。
 * - 有 X-API-Key：校验通过后写入一个合成的已认证 Authentication，使后续
 *   .anyRequest().authenticated() 放行；角色/操作人由 ActingContextInterceptor
 *   写入 ActingContext，供 SecurityUtils 读取。
 */
public class MachineIdentityAuthenticationFilter extends OncePerRequestFilter {

    @Value("${acting.api-key:}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-Key");
        if (!StringUtils.hasText(apiKey)) {
            // 浏览器 / Session 登录请求：不在此处理，走正常认证
            chain.doFilter(request, response);
            return;
        }

        // 配置了机器密钥则必须匹配
        if (StringUtils.hasText(expectedApiKey) && !expectedApiKey.equals(apiKey)) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "API Key 校验失败");
        }

        // 建立合成 Authentication，令 Spring Security 放行（角色/用户由 ActingContext 提供）
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "machine-identity", null,
                List.of(new SimpleGrantedAuthority("ROLE_MACHINE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}

