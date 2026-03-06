package com.macro.mall.gateway.filter;

import com.macro.mall.gateway.config.IgnoreUrlsConfig;
import com.macro.mall.gateway.util.GatewayJwtTokenUtil;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 将登录用户的JWT转化成用户信息的全局过滤器
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthGlobalFilter.class);

    @Autowired
    private IgnoreUrlsConfig ignoreUrlsConfig;

    @Autowired
    private GatewayJwtTokenUtil jwtTokenUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 检查是否在白名单中，按前缀匹配
        AntPathMatcher pathMatcher = new AntPathMatcher();
        for (String ignoreUrl : ignoreUrlsConfig.getUrls()) {
            if (pathMatcher.match(ignoreUrl, path)) {
                request = exchange.getRequest().mutate().header("X-User-Name", "").build();
                exchange = exchange.mutate().request(request).build();
                return chain.filter(exchange);
            }
        }

        // 2. 检查是否有Authorization请求头
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StrUtil.isEmpty(authHeader) || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "未登录或token解析失败");
        }

        // 3. 从请求头提取真正的 token 字符串
        String token = authHeader.substring(7);

        // 4. 解析并验证 token
        try {
            // 解析失败或者过期都会抛出异常或者返回true/null
            if (jwtTokenUtil.isTokenExpired(token)) {
                return unauthorized(exchange, "Token 已过期或无效");
            }
            
            String username = jwtTokenUtil.getUserNameFromToken(token);
            if (StrUtil.isEmpty(username)) {
                return unauthorized(exchange, "Token 解析失败：缺少用户信息");
            }

            // 5. 验证通过，将用户信息放入头部传递给下游业务微服务
            ServerHttpRequest newRequest = request.mutate()
                    .header("X-User-Name", username)
                    .build();

            return chain.filter(exchange.mutate().request(newRequest).build());
            
        } catch (Exception e) {
            LOGGER.error("Jwt 解析发生异常: ", e);
            return unauthorized(exchange, "Token 解析发生异常");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        // 构造和 CommonResult 一致的 JSON 格式
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return 0; // 优先级设为最高
    }
}
