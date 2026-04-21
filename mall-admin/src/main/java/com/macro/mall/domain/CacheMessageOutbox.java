package com.macro.mall.domain;

import java.util.Date;

/**
 * 缓存失效出站消息表对象。
 */
public class CacheMessageOutbox {
    private Long id;
    private String messageId;
    private String bizKey;
    private String messageType;
    private String exchangeName;
    private String routingKey;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private Date availableTime;
    private Date sentTime;
    private String lastError;
    private Date createTime;
    private Date updateTime;

    /**
     * 获取主键。
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键。
     */
    public void setId(Long id) {
        this.id = id;
    }

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
     * 获取业务键。
     */
    public String getBizKey() {
        return bizKey;
    }

    /**
     * 设置业务键。
     */
    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    /**
     * 获取消息类型。
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * 设置消息类型。
     */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    /**
     * 获取交换机名称。
     */
    public String getExchangeName() {
        return exchangeName;
    }

    /**
     * 设置交换机名称。
     */
    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    /**
     * 获取路由键。
     */
    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * 设置路由键。
     */
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    /**
     * 获取消息载荷。
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 设置消息载荷。
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * 获取投递状态。
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置投递状态。
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 获取重试次数。
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * 设置重试次数。
     */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 获取可投递时间。
     */
    public Date getAvailableTime() {
        return availableTime;
    }

    /**
     * 设置可投递时间。
     */
    public void setAvailableTime(Date availableTime) {
        this.availableTime = availableTime;
    }

    /**
     * 获取发送完成时间。
     */
    public Date getSentTime() {
        return sentTime;
    }

    /**
     * 设置发送完成时间。
     */
    public void setSentTime(Date sentTime) {
        this.sentTime = sentTime;
    }

    /**
     * 获取最近一次错误信息。
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * 设置最近一次错误信息。
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * 获取创建时间。
     */
    public Date getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间。
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间。
     */
    public Date getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间。
     */
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
