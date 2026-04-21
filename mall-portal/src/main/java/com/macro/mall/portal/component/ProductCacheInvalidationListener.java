package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.cache.product.ProductCacheInvalidationMessage;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 商品详情缓存失效消息监听器。
 */
@Component
public class ProductCacheInvalidationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCacheInvalidationListener.class);

    private final ObjectMapper objectMapper;
    private final ProductDetailLocalCache localCache;

    public ProductCacheInvalidationListener(ObjectMapper objectMapper,
                                            ProductDetailLocalCache localCache) {
        this.objectMapper = objectMapper;
        this.localCache = localCache;
    }

    /**
     * 监听 RabbitMQ 中的缓存失效消息并删除本地缓存。
     */
    @RabbitListener(queues = "#{productCacheInvalidationQueue.name}",
            containerFactory = "productCacheInvalidateRabbitListenerContainerFactory")
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            ProductCacheInvalidationMessage invalidationMessage =
                    objectMapper.readValue(payload, ProductCacheInvalidationMessage.class);
            if (invalidationMessage.getProductId() != null) {
                localCache.invalidate(invalidationMessage.getProductId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            LOGGER.warn("Consume product cache invalidation message failed, messageId:{}, error:{}",
                    message.getMessageProperties().getMessageId(), e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
