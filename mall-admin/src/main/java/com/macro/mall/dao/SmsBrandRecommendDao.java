package com.macro.mall.dao;

import com.macro.mall.model.PmsBrand;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SmsBrandRecommendDao {

    /**
     * 分页获取推荐品牌列表
     */
    List<PmsBrand> getRecommendBrandList(@Param("offset") Integer offset,
                                         @Param("limit") Integer limit);
    
}
