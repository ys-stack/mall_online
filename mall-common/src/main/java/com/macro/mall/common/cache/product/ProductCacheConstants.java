package com.macro.mall.common.cache.product;

/**
 * 商品详情缓存相关常量。
 */
public final class ProductCacheConstants {
    private ProductCacheConstants() {
    }

    public static final String PRODUCT_DETAIL_CACHE_KEY_PREFIX = "mall:portal:product:detail:";
    public static final String PRODUCT_DETAIL_LOCK_KEY_PREFIX = "mall:portal:product:detail:lock:";
    public static final String PRODUCT_DETAIL_INVALIDATE_EXCHANGE = "mall.cache.product.detail.invalidate.fanout";
    public static final String PRODUCT_DETAIL_INVALIDATE_QUEUE_PREFIX = "mall.cache.product.detail.invalidate.portal.";
    public static final String PRODUCT_DETAIL_INVALIDATE_DEAD_LETTER_EXCHANGE = "mall.cache.product.detail.invalidate.dlx";
    public static final String PRODUCT_DETAIL_INVALIDATE_DEAD_LETTER_QUEUE = "mall.cache.product.detail.invalidate.dlq";
    public static final String PRODUCT_DETAIL_INVALIDATE_MESSAGE_TYPE = "PRODUCT_DETAIL_INVALIDATION";

    /**
     * 构建商品详情缓存 Key。
     */
    public static String productDetailKey(Long productId) {
        return PRODUCT_DETAIL_CACHE_KEY_PREFIX + productId;
    }

    /**
     * 构建商品详情互斥锁 Key。
     */
    public static String productDetailLockKey(Long productId) {
        return PRODUCT_DETAIL_LOCK_KEY_PREFIX + productId;
    }

    /**
     * 构建商品详情失效队列名。
     */
    public static String productDetailInvalidateQueue(String instanceId) {
        return PRODUCT_DETAIL_INVALIDATE_QUEUE_PREFIX + instanceId;
    }
}
