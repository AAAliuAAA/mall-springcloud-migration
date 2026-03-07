package com.macro.mall.portal.feign;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.PmsBrand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
                LOGGER.error("调用 mall-admin recommendList 失败，执行降级", cause);
                // 返回空列表，前端正常渲染，不影响页面其他内容
                return CommonResult.success(Collections.emptyList());
            }
        };
    }
}
