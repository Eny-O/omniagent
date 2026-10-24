package com.wj.omniagent.common.security;

import com.wj.omniagent.modules.user.service.security.LoginUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：拦截 Authorization: Bearer，验签后构建认证信息写入 SecurityContext。
 * <p>
 * 与 Session 方案互斥——本项目已改为 STATELESS，身份一律来自签名 JWT，
 * 不再信任 X-API-Key / X-Acting-* 等机器身份声明头。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                authenticate(claims, request);
            } catch (JwtException | IllegalArgumentException e) {
                // 非法 / 过期 token：不设置认证，后续 .authenticated() 由 Security 统一返回 401 JSON
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String username = claims.getSubject();
        if (!StringUtils.hasText(username)) {
            return;
        }
        Number uidNum = claims.get("uid", Number.class);
        Long uid = uidNum != null ? uidNum.longValue() : null;
        String role = claims.get("role", String.class);
        Number deptNum = claims.get("deptId", Number.class);
        Long deptId = deptNum != null ? deptNum.longValue() : null;

        List<GrantedAuthority> authorities = role != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                : List.of();

        LoginUserPrincipal principal = new LoginUserPrincipal(
                uid, username, null, 1, role, deptId, authorities, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
