package com.hospital.util;

import com.hospital.config.JwtProperties;
import com.hospital.security.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析。
 * 使用 JJWT 0.12.x 的新 API（verifyWith / parseSignedClaims）。
 */
@Component
public class JwtUtil {

    /** 自定义 claim：用户主键 */
    public static final String CLAIM_USER_ID = "uid";

    /** 自定义 claim：角色值 */
    public static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;

    private final SecretKey secretKey;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        // 把配置里的字符串密钥转成 HMAC 密钥，长度不足 32 字节会在这里直接抛异常
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token。
     * subject 放用户名，额外塞 uid/role，方便前端解析与日志排查，但鉴权仍以查库结果为准。
     */
    public String generateToken(LoginUser loginUser) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + properties.getExpireHours() * 3600_000L);
        return Jwts.builder()
                .subject(loginUser.getUsername())
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .claim(CLAIM_USER_ID, loginUser.getUserId())
                .claim(CLAIM_ROLE, loginUser.getRole())
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验签名、签发者、过期时间。
     * 校验失败会抛 JwtException，由过滤器统一捕获后按未登录处理。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** token 有效期（秒），登录响应里返回给前端 */
    public Long getExpireSeconds() {
        return properties.getExpireHours() * 3600L;
    }
}
