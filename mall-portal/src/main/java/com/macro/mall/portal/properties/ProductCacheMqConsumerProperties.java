package com.macro.mall.portal.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商品详情缓存失效 MQ 消费配置。
 */
@Component
@ConfigurationProperties(prefix = "mall.cache.product.detail.mq")
public class ProductCacheMqConsumerProperties {
    private String instanceId;
    private String queueType = "quorum";
    private Integer consumerConcurrency = 1;
    private Integer maxConsumerConcurrency = 4;

    /**
     * 获取实例唯一标识。
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * 设置实例唯一标识。
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * 获取 RabbitMQ 队列类型。
     */
    public String getQueueType() {
        return queueType;
    }

    /**
     * 设置 RabbitMQ 队列类型。
     */
    public void setQueueType(String queueType) {
        this.queueType = queueType;
    }

    /**
     * 获取最小消费者并发数。
     */
    public Integer getConsumerConcurrency() {
        return consumerConcurrency;
    }

    /**
     * 设置最小消费者并发数。
     */
    public void setConsumerConcurrency(Integer consumerConcurrency) {
        this.consumerConcurrency = consumerConcurrency;
    }

    /**
     * 获取最大消费者并发数。
     */
    public Integer getMaxConsumerConcurrency() {
        return maxConsumerConcurrency;
    }

    /**
     * 设置最大消费者并发数。
     */
    public void setMaxConsumerConcurrency(Integer maxConsumerConcurrency) {
        this.maxConsumerConcurrency = maxConsumerConcurrency;
    }
}
