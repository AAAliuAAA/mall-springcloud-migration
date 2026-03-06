package com.macro.mall.portal.config;

import com.macro.mall.common.constant.AuthConstant;
import cn.hutool.core.util.StrUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局 Feign 调用拦截器配置
 * 主要作用：在 mall-portal 等消费端向服务端发起请求时，将原有的请求头诸如 X-User-Name 顺路携带给后端。
 */
@Configuration
public class FeignConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeignConfig.class);

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate requestTemplate) {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    // 提取网关塞在当前请求的 X-User-Name
                    String username = request.getHeader(AuthConstant.USER_TOKEN_HEADER);
                    // 塞进即将发出的 Feign 远程调用栈头中，保证权限闭环
                    if (StrUtil.isNotEmpty(username)) {
                        requestTemplate.header(AuthConstant.USER_TOKEN_HEADER, username);
                        LOGGER.debug("FeignInterceptor successfully forwarding header: {}={}", AuthConstant.USER_TOKEN_HEADER, username);
                    }
                }
            }
        };
    }
}
