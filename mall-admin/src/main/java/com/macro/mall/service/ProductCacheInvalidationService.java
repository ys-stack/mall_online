package com.macro.mall.service;

import java.util.Collection;
import java.util.List;

/**
 * 商品详情缓存失效服务。
 */
public interface ProductCacheInvalidationService {
    /**
     * 在事务内记录单个商品的缓存失效消息。
     */
    Long recordInvalidation(Long productId, String reason);

    /**
     * 在事务内批量记录商品缓存失效消息。
     */
    List<Long> recordInvalidations(Collection<Long> productIds, String reason);

    /**
     * 按主键立即投递一条 outbox 消息。
     */
    void dispatchOutboxMessage(Long outboxId);

    /**
     * 批量立即投递 outbox 消息。
     */
    void dispatchOutboxMessages(Collection<Long> outboxIds);

    /**
     * 扫描并投递当前到期的 outbox 消息。
     */
    void dispatchPendingMessages();

    /**
     * 清理已经发送完成的历史 outbox 消息。
     */
    void cleanupSentMessages();
}
