package com.macro.mall.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.cache.product.ProductCacheConstants;
import com.macro.mall.common.cache.product.ProductCacheInvalidationMessage;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.dao.CacheMessageOutboxDao;
import com.macro.mall.domain.CacheMessageOutbox;
import com.macro.mall.domain.CacheMessageOutboxStatus;
import com.macro.mall.properties.ProductCacheInvalidationProperties;
import com.macro.mall.service.ProductCacheInvalidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 商品详情缓存失效实现，采用 outbox + RabbitMQ 的可靠投递方案。
 */
@Service
public class ProductCacheInvalidationServiceImpl implements ProductCacheInvalidationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCacheInvalidationServiceImpl.class);
    private static final int CLEANUP_LIMIT = 500;

    private final ObjectMapper objectMapper;
    private final RedisService redisService;
    private final RabbitTemplate rabbitTemplate;
    private final CacheMessageOutboxDao cacheMessageOutboxDao;
    private final ProductCacheInvalidationProperties properties;

    public ProductCacheInvalidationServiceImpl(ObjectMapper objectMapper,
                                               RedisService redisService,
                                               RabbitTemplate rabbitTemplate,
                                               CacheMessageOutboxDao cacheMessageOutboxDao,
                                               ProductCacheInvalidationProperties properties) {
        this.objectMapper = objectMapper;
        this.redisService = redisService;
        this.rabbitTemplate = rabbitTemplate;
        this.cacheMessageOutboxDao = cacheMessageOutboxDao;
        this.properties = properties;
    }

    /**
     * 在事务内记录单个商品详情缓存失效消息。
     */
    @Override
    public Long recordInvalidation(Long productId, String reason) {
        if (productId == null) {
            return null;
        }
        Long immediateOutboxId = createOutboxMessage(productId, reason, 0L);
        long delayedDoubleDeleteMillis = properties.getDelayedDoubleDeleteMillis();
        if (delayedDoubleDeleteMillis > 0) {
            createOutboxMessage(productId, reason + "-delayed", delayedDoubleDeleteMillis);
        }
        return immediateOutboxId;
    }

    /**
     * 在事务内批量记录商品详情缓存失效消息。
     */
    @Override
    public List<Long> recordInvalidations(Collection<Long> productIds, String reason) {
        if (CollectionUtils.isEmpty(productIds)) {
            return new ArrayList<>();
        }
        Set<Long> distinctIds = new LinkedHashSet<>(productIds);
        List<Long> outboxIds = new ArrayList<>(distinctIds.size());
        for (Long productId : distinctIds) {
            Long outboxId = recordInvalidation(productId, reason);
            if (outboxId != null) {
                outboxIds.add(outboxId);
            }
        }
        return outboxIds;
    }

    /**
     * 按主键立即投递一条 outbox 消息。
     */
    @Override
    public void dispatchOutboxMessage(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        CacheMessageOutbox outbox = cacheMessageOutboxDao.getItem(outboxId);
        if (outbox == null) {
            return;
        }
        dispatchSingleMessage(outbox);
    }

    /**
     * 批量立即投递 outbox 消息。
     */
    @Override
    public void dispatchOutboxMessages(Collection<Long> outboxIds) {
        if (CollectionUtils.isEmpty(outboxIds)) {
            return;
        }
        for (Long outboxId : outboxIds) {
            dispatchOutboxMessage(outboxId);
        }
    }

    /**
     * 扫描并投递当前到期的 outbox 消息。
     */
    @Override
    public void dispatchPendingMessages() {
        Date now = new Date();
        Date processingExpireTime = new Date(now.getTime() - properties.getOutboxProcessingTimeoutSeconds() * 1000L);
        List<CacheMessageOutbox> outboxList = cacheMessageOutboxDao.listDispatchable(
                Arrays.asList(CacheMessageOutboxStatus.PENDING, CacheMessageOutboxStatus.FAILED),
                processingExpireTime,
                now,
                properties.getOutboxDispatchBatchSize());
        for (CacheMessageOutbox outbox : outboxList) {
            dispatchSingleMessage(outbox);
        }
    }

    /**
     * 清理历史已发送消息，避免 outbox 表无限增长。
     */
    @Override
    public void cleanupSentMessages() {
        Date beforeTime = Date.from(Instant.now().minus(properties.getOutboxRetentionDays(), ChronoUnit.DAYS));
        int cleaned = cacheMessageOutboxDao.deleteSentBefore(beforeTime, CLEANUP_LIMIT);
        if (cleaned > 0) {
            LOGGER.info("Clean expired product cache outbox messages, count:{}", cleaned);
        }
    }

    /**
     * 创建一条 outbox 消息记录。
     */
    private Long createOutboxMessage(Long productId, String reason, long delayMillis) {
        Date now = new Date();
        ProductCacheInvalidationMessage invalidationMessage = new ProductCacheInvalidationMessage();
        invalidationMessage.setMessageId(UUID.randomUUID().toString());
        invalidationMessage.setProductId(productId);
        invalidationMessage.setReason(reason);
        invalidationMessage.setSource("mall-admin");
        invalidationMessage.setTimestamp(now.getTime());

        CacheMessageOutbox outbox = new CacheMessageOutbox();
        outbox.setMessageId(invalidationMessage.getMessageId());
        outbox.setBizKey("product-detail:" + productId);
        outbox.setMessageType(ProductCacheConstants.PRODUCT_DETAIL_INVALIDATE_MESSAGE_TYPE);
        outbox.setExchangeName(ProductCacheConstants.PRODUCT_DETAIL_INVALIDATE_EXCHANGE);
        outbox.setRoutingKey("");
        outbox.setPayload(toJson(invalidationMessage));
        outbox.setStatus(CacheMessageOutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setAvailableTime(new Date(now.getTime() + Math.max(delayMillis, 0L)));
        outbox.setSentTime(null);
        outbox.setLastError(null);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        cacheMessageOutboxDao.insert(outbox);
        return outbox.getId();
    }

    /**
     * 投递单条 outbox 消息，并根据结果更新状态。
     */
    private void dispatchSingleMessage(CacheMessageOutbox outbox) {
        if (outbox == null || outbox.getId() == null) {
            return;
        }
        if (CacheMessageOutboxStatus.SENT == safeInt(outbox.getStatus())) {
            return;
        }
        Date now = new Date();
        int updated = cacheMessageOutboxDao.markProcessing(outbox.getId(),
                Arrays.asList(CacheMessageOutboxStatus.PENDING, CacheMessageOutboxStatus.FAILED, CacheMessageOutboxStatus.PROCESSING),
                now);
        if (updated == 0) {
            return;
        }
        try {
            ProductCacheInvalidationMessage invalidationMessage = objectMapper.readValue(outbox.getPayload(), ProductCacheInvalidationMessage.class);
            removeRedisCache(invalidationMessage.getProductId());
            publishMessage(outbox);
            cacheMessageOutboxDao.markSent(outbox.getId(), new Date(), new Date());
        } catch (Exception e) {
            int retryCount = safeInt(outbox.getRetryCount()) + 1;
            Date nextRetryTime = new Date(System.currentTimeMillis() + computeRetryDelayMillis(retryCount));
            String lastError = trimError(e.getMessage());
            cacheMessageOutboxDao.markFailed(outbox.getId(), retryCount, nextRetryTime, lastError, new Date());
            LOGGER.warn("Dispatch product cache invalidation outbox failed, outboxId:{}, retryCount:{}, error:{}",
                    outbox.getId(), retryCount, lastError);
        }
    }

    /**
     * 删除 Redis 中的商品详情缓存。
     */
    private void removeRedisCache(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        Boolean deleted = redisService.del(ProductCacheConstants.productDetailKey(productId));
        if (deleted == null) {
            LOGGER.debug("Redis product detail cache key not found, productId:{}", productId);
        }
    }

    /**
     * 发送 RabbitMQ 消息，并等待 broker confirm。
     */
    private void publishMessage(CacheMessageOutbox outbox) throws Exception {
        CorrelationData correlationData = new CorrelationData(outbox.getMessageId());
        rabbitTemplate.convertAndSend(outbox.getExchangeName(), outbox.getRoutingKey(), outbox.getPayload(), message -> {
            MessageProperties messageProperties = message.getMessageProperties();
            messageProperties.setMessageId(outbox.getMessageId());
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            messageProperties.setType(outbox.getMessageType());
            return message;
        }, correlationData);

        CorrelationData.Confirm confirm = correlationData.getFuture().get(properties.getMqConfirmTimeoutMillis(), TimeUnit.MILLISECONDS);
        if (confirm == null || !confirm.isAck()) {
            String reason = confirm == null ? "confirm timeout" : confirm.getReason();
            throw new AmqpException("product cache invalidation message broker nack: " + reason);
        }
        if (correlationData.getReturned() != null) {
            throw new AmqpException("product cache invalidation message returned by broker: "
                    + correlationData.getReturned().getReplyText());
        }
    }

    /**
     * 将对象序列化为 JSON。
     */
    private String toJson(ProductCacheInvalidationMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("Serialize product cache invalidation message failed", e);
        }
    }

    /**
     * 计算失败后的下次重试间隔，使用指数退避。
     */
    private long computeRetryDelayMillis(int retryCount) {
        long baseSeconds = Math.max(properties.getOutboxRetryBaseSeconds(), 1);
        long maxSeconds = Math.max(properties.getOutboxRetryMaxSeconds(), baseSeconds);
        long multiplier = 1L << Math.min(Math.max(retryCount - 1, 0), 6);
        long delaySeconds = Math.min(baseSeconds * multiplier, maxSeconds);
        return delaySeconds * 1000L;
    }

    /**
     * 截断错误信息，避免数据库字段过长。
     */
    private String trimError(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    /**
     * 安全读取整数值。
     */
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
