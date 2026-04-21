package com.macro.mall.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商品详情缓存失效配置。
 */
@Component
@ConfigurationProperties(prefix = "mall.cache.product.detail")
public class ProductCacheInvalidationProperties {
    private long delayedDoubleDeleteMillis = 500L;
    private int outboxDispatchBatchSize = 100;
    private long outboxDispatchIntervalMillis = 5000L;
    private int outboxProcessingTimeoutSeconds = 60;
    private int outboxRetryBaseSeconds = 5;
    private int outboxRetryMaxSeconds = 300;
    private int outboxRetentionDays = 7;
    private String outboxCleanupCron = "0 0 3 * * ?";
    private long mqConfirmTimeoutMillis = 5000L;

    /**
     * 获取延迟双删时间。
     */
    public long getDelayedDoubleDeleteMillis() {
        return delayedDoubleDeleteMillis;
    }

    /**
     * 设置延迟双删时间。
     */
    public void setDelayedDoubleDeleteMillis(long delayedDoubleDeleteMillis) {
        this.delayedDoubleDeleteMillis = delayedDoubleDeleteMillis;
    }

    /**
     * 获取单次扫描的 outbox 数量。
     */
    public int getOutboxDispatchBatchSize() {
        return outboxDispatchBatchSize;
    }

    /**
     * 设置单次扫描的 outbox 数量。
     */
    public void setOutboxDispatchBatchSize(int outboxDispatchBatchSize) {
        this.outboxDispatchBatchSize = outboxDispatchBatchSize;
    }

    /**
     * 获取 outbox 扫描间隔。
     */
    public long getOutboxDispatchIntervalMillis() {
        return outboxDispatchIntervalMillis;
    }

    /**
     * 设置 outbox 扫描间隔。
     */
    public void setOutboxDispatchIntervalMillis(long outboxDispatchIntervalMillis) {
        this.outboxDispatchIntervalMillis = outboxDispatchIntervalMillis;
    }

    /**
     * 获取处理中消息超时秒数。
     */
    public int getOutboxProcessingTimeoutSeconds() {
        return outboxProcessingTimeoutSeconds;
    }

    /**
     * 设置处理中消息超时秒数。
     */
    public void setOutboxProcessingTimeoutSeconds(int outboxProcessingTimeoutSeconds) {
        this.outboxProcessingTimeoutSeconds = outboxProcessingTimeoutSeconds;
    }

    /**
     * 获取失败重试基础秒数。
     */
    public int getOutboxRetryBaseSeconds() {
        return outboxRetryBaseSeconds;
    }

    /**
     * 设置失败重试基础秒数。
     */
    public void setOutboxRetryBaseSeconds(int outboxRetryBaseSeconds) {
        this.outboxRetryBaseSeconds = outboxRetryBaseSeconds;
    }

    /**
     * 获取失败重试最大秒数。
     */
    public int getOutboxRetryMaxSeconds() {
        return outboxRetryMaxSeconds;
    }

    /**
     * 设置失败重试最大秒数。
     */
    public void setOutboxRetryMaxSeconds(int outboxRetryMaxSeconds) {
        this.outboxRetryMaxSeconds = outboxRetryMaxSeconds;
    }

    /**
     * 获取 outbox 历史保留天数。
     */
    public int getOutboxRetentionDays() {
        return outboxRetentionDays;
    }

    /**
     * 设置 outbox 历史保留天数。
     */
    public void setOutboxRetentionDays(int outboxRetentionDays) {
        this.outboxRetentionDays = outboxRetentionDays;
    }

    /**
     * 获取历史 outbox 清理 cron。
     */
    public String getOutboxCleanupCron() {
        return outboxCleanupCron;
    }

    /**
     * 设置历史 outbox 清理 cron。
     */
    public void setOutboxCleanupCron(String outboxCleanupCron) {
        this.outboxCleanupCron = outboxCleanupCron;
    }

    /**
     * 获取发布确认等待超时时间。
     */
    public long getMqConfirmTimeoutMillis() {
        return mqConfirmTimeoutMillis;
    }

    /**
     * 设置发布确认等待超时时间。
     */
    public void setMqConfirmTimeoutMillis(long mqConfirmTimeoutMillis) {
        this.mqConfirmTimeoutMillis = mqConfirmTimeoutMillis;
    }
}
