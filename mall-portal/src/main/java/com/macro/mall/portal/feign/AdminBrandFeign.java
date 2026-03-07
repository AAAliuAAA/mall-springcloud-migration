package com.macro.mall.portal.feign;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.PmsBrand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@FeignClient(name = "mall-admin", fallbackFactory = AdminBrandFeignFallbackFactory.class)  // 对应 Nacos 里注册的服务名
public interface AdminBrandFeign {

    @GetMapping("/brand/recommendList")
    CommonResult<List<PmsBrand>> recommendList(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize);
}