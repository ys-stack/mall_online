package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 秒杀 MQ 本地消息表记录。
 */
@Getter
@Setter
public class SecKillMessageOutbox implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String messageId;
    private String requestId;
    private Long memberId;
    private String memberUsername;
    private Long memberReceiveAddressId;
    private Long flashPromotionProductRelationId;
    private Long productId;
    private Integer quantity;
    private String status;
    private Integer retryCount;
    private Date nextRetryTime;
    private Date createTime;
    private Date updateTime;
    private String lastError;
}
