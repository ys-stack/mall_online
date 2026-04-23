package com.macro.mall.portal.domain;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 秒杀提交结果。
 */
@Getter
@Setter
public class SecKillResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("结果编码：0->已受理；1->售罄；2->重复提交；3->活动未预热；4->参数错误")
    private Integer code;

    @ApiModelProperty("结果说明")
    private String message;

    @ApiModelProperty("秒杀请求ID，后续可用于查询异步建单结果")
    private String requestId;

    @ApiModelProperty("秒杀商品活动关系ID")
    private Long flashPromotionProductRelationId;

    /**
     * 创建已受理结果。
     */
    public static SecKillResult accepted(String requestId, Long relationId) {
        SecKillResult result = new SecKillResult();
        result.setCode(0);
        result.setMessage("秒杀请求已受理，请稍后查看订单");
        result.setRequestId(requestId);
        result.setFlashPromotionProductRelationId(relationId);
        return result;
    }

    /**
     * 创建失败结果。
     */
    public static SecKillResult failed(Integer code, String message, Long relationId) {
        SecKillResult result = new SecKillResult();
        result.setCode(code);
        result.setMessage(message);
        result.setFlashPromotionProductRelationId(relationId);
        return result;
    }
}
