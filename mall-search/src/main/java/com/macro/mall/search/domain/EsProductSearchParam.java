package com.macro.mall.search.domain;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * ES 商品高级搜索参数。
 */
@Getter
@Setter
public class EsProductSearchParam {
    @ApiModelProperty("搜索关键词")
    private String keyword;

    @ApiModelProperty("品牌ID")
    private Long brandId;

    @ApiModelProperty("商品分类ID")
    private Long productCategoryId;

    @ApiModelProperty("最低价格")
    private BigDecimal minPrice;

    @ApiModelProperty("最高价格")
    private BigDecimal maxPrice;

    @ApiModelProperty("是否只看有库存商品")
    private Boolean onlyStock;

    @ApiModelProperty("促销类型")
    private Integer promotionType;

    @ApiModelProperty("排序字段：0->相关度；1->新品；2->销量；3->价格升序；4->价格降序")
    private Integer sort;
}
