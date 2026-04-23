package com.macro.mall.portal.domain;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 秒杀提交参数。
 */
@Getter
@Setter
public class SecKillSubmitParam implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("秒杀商品活动关系ID，对应 sms_flash_promotion_product_relation.id")
    private Long flashPromotionProductRelationId;

    @ApiModelProperty("收货地址ID，用于异步建单时补全收货信息")
    private Long memberReceiveAddressId;
}
