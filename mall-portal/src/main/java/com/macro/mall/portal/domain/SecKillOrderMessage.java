package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 秒杀异步建单消息。
 */
@Getter
@Setter
public class SecKillOrderMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String requestId;
    private Long memberId;
    private String memberUsername;
    private Long memberReceiveAddressId;
    private Long flashPromotionProductRelationId;
    private Long productId;
    private Integer quantity;
    private Date createTime;
}
