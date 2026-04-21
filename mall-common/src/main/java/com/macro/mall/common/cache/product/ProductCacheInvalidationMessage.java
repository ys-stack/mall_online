package com.macro.mall.common.cache.product;

import java.io.Serializable;

/**
 * 商品详情缓存失效广播消息。
 */
public class ProductCacheInvalidationMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private Long productId;
    private String source;
    private String reason;
    private Long timestamp;

    /**
     * 获取消息唯一标识。
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * 设置消息唯一标识。
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * 获取商品 ID。
     */
    public Long getProductId() {
        return productId;
    }

    /**
     * 设置商品 ID。
     */
    public void setProductId(Long productId) {
        this.productId = productId;
    }

    /**
     * 获取消息来源。
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置消息来源。
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 获取失效原因。
     */
    public String getReason() {
        return reason;
    }

    /**
     * 设置失效原因。
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * 获取消息发送时间。
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * 设置消息发送时间。
     */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
