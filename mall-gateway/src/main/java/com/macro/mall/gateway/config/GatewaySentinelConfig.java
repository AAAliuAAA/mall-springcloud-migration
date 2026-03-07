package com.macro.mall.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashSet;

//gateway熔断规则，不影响服务间调用
@Configuration
public class GatewaySentinelConfig {

    @PostConstruct
    public void initRules() {
        // 针对路由 mall-portal 限流：每秒最多 200 个请求
        GatewayFlowRule portalRule = new GatewayFlowRule("mall-portal")
                .setCount(200)
                .setIntervalSec(1);

        // 针对路由 mall-admin 限流：每秒最多 100 个请求
        GatewayFlowRule adminRule = new GatewayFlowRule("mall-admin")
                .setCount(100)
                .setIntervalSec(1);

        GatewayRuleManager.loadRules(new HashSet<>(Arrays.asList(portalRule, adminRule)));
    }
}
