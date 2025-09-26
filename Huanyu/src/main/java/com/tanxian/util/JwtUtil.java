package com.tanxian.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 * 提供JWT Token的生成、验证和解析功能
 */
@Component
@Slf4j
public class JwtUtil {

    /**
     * JWT密钥
     */
    private static final String JWT_SECRET = "huanyu_jwt_secret_key_2025_very_long_and_secure_key_for_jwt_token_generation";
    
    /**
     * JWT过期时间（7天）
     */
    private static final long JWT_EXPIRATION = 7 * 24 * 60 * 60 * 1000L;
    
    /**
     * 生成密钥
     */
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());

    /**
     * 生成JWT Token
     * 
     * @param userId 用户ID
     * @param email 用户邮箱
     * @param nickname 用户昵称
     * @param avatarUrl 用户头像URL
     * @return JWT Token
     */
    public String generateToken(Long userId, String email, String nickname, String avatarUrl) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + JWT_EXPIRATION);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("userId", userId)
                .claim("email", email)
                .claim("nickname", nickname)
                .claim("avatarUrl", avatarUrl)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * 验证JWT Token
     * 
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从JWT Token中获取用户ID
     * 
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("userId", Long.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("从JWT Token获取用户ID失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从JWT Token中获取用户信息
     * 
     * @param token JWT Token
     * @return 用户信息Map
     */
    public Map<String, Object> getUserInfoFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return Map.of(
                "userId", claims.get("userId", Long.class),
                "email", claims.get("email", String.class),
                "nickname", claims.get("nickname", String.class),
                "avatarUrl", claims.get("avatarUrl", String.class)
            );
        } catch (JwtException | IllegalArgumentException e) {
            log.error("从JWT Token获取用户信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查Token是否过期
     * 
     * @param token JWT Token
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.error("检查JWT Token过期状态失败: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 获取Token剩余有效时间（毫秒）
     * 
     * @param token JWT Token
     * @return 剩余有效时间
     */
    public long getTokenRemainingTime(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("获取JWT Token剩余时间失败: {}", e.getMessage());
            return 0;
        }
    }
}