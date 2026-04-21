package com.macro.mall.component;

import com.macro.mall.service.ProductCacheInvalidationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 商品详情缓存失效 outbox 调度器。
 */
@Component
public class ProductCacheInvalidationOutboxScheduler {
    private final ProductCacheInvalidationService productCacheInvalidationService;

    public ProductCacheInvalidationOutboxScheduler(ProductCacheInvalidationService productCacheInvalidationService) {
        this.productCacheInvalidationService = productCacheInvalidationService;
    }

    /**
     * 周期性扫描并补发未成功投递的缓存失效消息。
     */
    @Scheduled(fixedDelayString = "${mall.cache.product.detail.outbox-dispatch-interval-millis:5000}")
    public void dispatchPendingMessages() {
        productCacheInvalidationService.dispatchPendingMessages();
    }

    /**
     * 周期性清理历史已发送 outbox 消息。
     */
    @Scheduled(cron = "${mall.cache.product.detail.outbox-cleanup-cron:0 0 3 * * ?}")
    public void cleanupSentMessages() {
        productCacheInvalidationService.cleanupSentMessages();
    }
}
