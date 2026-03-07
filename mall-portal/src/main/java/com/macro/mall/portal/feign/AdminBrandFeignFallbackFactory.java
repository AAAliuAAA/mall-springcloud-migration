package com.macro.mall.portal.feign;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.PmsBrand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AdminBrandFeignFallbackFactory implements FallbackFactory<AdminBrandFeign> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBrandFeignFallbackFactory.class);

    @Override
    public AdminBrandFeign create(Throwable cause) {
        return new AdminBrandFeign() {
            @Override
            public CommonResult<List<PmsBrand>> recommendList(Integer pageNum, Integer pageSize) {
                if (cause instanceof DegradeException) {
                    // 熔断器打开触发，Sentinel 主动拦截
                    LOGGER.warn("熔断器已打开，直接降级，不调用 admin");
                } else {
                    // 正常调用失败，等超时后触发
                    LOGGER.error("调用 mall-admin 失败，执行降级", cause);
                }
                return CommonResult.success(Collections.emptyList());
            }
        };
    }
}
