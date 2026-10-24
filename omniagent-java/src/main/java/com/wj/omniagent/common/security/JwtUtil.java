package com.wj.omniagent.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与验签（HS256 共享密钥）。
 * <p>
 * 浏览器登录与 Agent 调用统一使用本工具签发的 Bearer Token；
 * 密钥与 Agent 端共享（见 Agent 配置），两端均可验签。
 */
@Component
public class JwtUtil {

    /** 签名密钥：生产环境务必在 application.yml 覆盖为 ≥32 字节的强密钥。 */
    @Value("${jwt.secret:omniagent-dev-secret-please-change-in-prod-1234567890}")
    private String secret;

    /** 过期时长（分钟）。 */
    @Value("${jwt.expiration-minutes:120}")
    private long expirationMinutes;

    private SecretKey key() {
        // HS256 要求密钥 ≥ 256 bits；开发默认值已满足长度要求
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 Token，载荷包含用户ID / 用户名 / 角色 / 部门ID。
     */
    public String generateToken(Long userId, String username, String role, Long deptId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMinutes * 60_000L);
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .claim("deptId", deptId)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 验签并返回载荷；签名非法 / 过期会抛 JwtException。
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
