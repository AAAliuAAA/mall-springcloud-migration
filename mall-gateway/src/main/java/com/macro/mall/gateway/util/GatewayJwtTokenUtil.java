package com.macro.mall.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Gateway专用的JWT解析工具类，只包含解析和验证逻辑，不依赖Spring Security
 */
@Component
public class GatewayJwtTokenUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayJwtTokenUtil.class);
    
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 从token中获取登录用户名
     */
    public String getUserNameFromToken(String token) {
        String username;
        try {
            Claims claims = getClaimsFromToken(token);
            username = claims.getSubject();
        } catch (Exception e) {
            username = null;
        }
        return username;
    }

    /**
     * 判断token是否已经失效
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiredDate = getExpiredDateFromToken(token);
            return expiredDate.before(new Date());
        } catch (Exception e) {
            // 如果解析失败（格式错误等），也视为过期/无效
            return true;
        }
    }

    /**
     * 从token中获取JWT中的负载
     */
    private Claims getClaimsFromToken(String token) {
        Claims claims = null;
        try {
            claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            LOGGER.info("JWT格式验证失败:{}", token);
            throw e;
        }
        return claims;
    }

    /**
     * 从token中获取过期时间
     */
    private Date getExpiredDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getExpiration();
    }
}
