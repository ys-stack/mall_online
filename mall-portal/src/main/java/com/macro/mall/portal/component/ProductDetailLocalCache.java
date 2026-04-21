package com.macro.mall.portal.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.macro.mall.common.cache.product.ProductCacheConstants;
import com.macro.mall.portal.domain.ProductDetailCacheValue;
import com.macro.mall.portal.properties.ProductDetailCacheProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品详情本地缓存。
 */
@Component
public class ProductDetailLocalCache {
    private final Cache<String, LocalCacheEntry> localCache;
    private final ProductDetailCacheProperties properties;

    public ProductDetailLocalCache(Cache<String, LocalCacheEntry> localCache,
                                   ProductDetailCacheProperties properties) {
        this.localCache = localCache;
        this.properties = properties;
    }

    /**
     * 从本地缓存中获取商品详情。
     */
    public ProductDetailCacheValue get(Long productId) {
        String key = ProductCacheConstants.productDetailKey(productId);
        LocalCacheEntry entry = localCache.getIfPresent(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.getExpireAtMillis()) {
            localCache.invalidate(key);
            return null;
        }
        return entry.getCacheValue();
    }

    /**
     * 写入本地缓存，并为正常值和空值使用不同 TTL。
     */
    public void put(Long productId, ProductDetailCacheValue cacheValue) {
        long ttlSeconds = cacheValue.isEmpty() ? properties.getLocalNullTtlSeconds() : properties.getLocalTtlSeconds();
        long expireAtMillis = System.currentTimeMillis() + (ttlSeconds + randomJitter(properties.getLocalTtlJitterSeconds())) * 1000L;
        localCache.put(ProductCacheConstants.productDetailKey(productId), new LocalCacheEntry(cacheValue, expireAtMillis));
    }

    /**
     * 删除本地缓存中的商品详情。
     */
    public void invalidate(Long productId) {
        localCache.invalidate(ProductCacheConstants.productDetailKey(productId));
    }

    /**
     * 生成本地缓存 TTL 的随机抖动值。
     */
    private long randomJitter(long maxJitterSeconds) {
        if (maxJitterSeconds <= 0) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(maxJitterSeconds + 1);
    }

    public static class LocalCacheEntry {
        private final ProductDetailCacheValue cacheValue;
        private final long expireAtMillis;

        public LocalCacheEntry(ProductDetailCacheValue cacheValue, long expireAtMillis) {
            this.cacheValue = cacheValue;
            this.expireAtMillis = expireAtMillis;
        }

        /**
         * 获取缓存值。
         */
        public ProductDetailCacheValue getCacheValue() {
            return cacheValue;
        }

        /**
         * 获取缓存过期时间戳。
         */
        public long getExpireAtMillis() {
            return expireAtMillis;
        }
    }
}
