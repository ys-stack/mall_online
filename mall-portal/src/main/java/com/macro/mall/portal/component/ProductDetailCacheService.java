package com.macro.mall.portal.component;

import com.macro.mall.common.cache.product.ProductCacheConstants;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.portal.domain.PmsPortalProductDetail;
import com.macro.mall.portal.domain.ProductDetailCacheValue;
import com.macro.mall.portal.properties.ProductDetailCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 商品详情两级缓存服务。
 */
@Component
public class ProductDetailCacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductDetailCacheService.class);
    private static final String RELEASE_LOCK_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final ProductDetailLocalCache localCache;
    private final RedisService redisService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductDetailCacheProperties properties;
    private final DefaultRedisScript<Long> releaseLockRedisScript;

    public ProductDetailCacheService(ProductDetailLocalCache localCache,
                                     RedisService redisService,
                                     StringRedisTemplate stringRedisTemplate,
                                     ProductDetailCacheProperties properties) {
        this.localCache = localCache;
        this.redisService = redisService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.releaseLockRedisScript = new DefaultRedisScript<>();
        this.releaseLockRedisScript.setScriptText(RELEASE_LOCK_LUA);
        this.releaseLockRedisScript.setResultType(Long.class);
    }

    /**
     * 按两级缓存流程获取商品详情。
     */
    public PmsPortalProductDetail getProductDetail(Long productId, Supplier<PmsPortalProductDetail> dbLoader) {
        ProductDetailCacheValue cacheValue = localCache.get(productId);
        if (cacheValue != null) {
            return unwrap(cacheValue);
        }
        cacheValue = getRedisCache(productId);
        if (cacheValue != null) {
            localCache.put(productId, cacheValue);
            return unwrap(cacheValue);
        }
        return rebuildCacheWithMutex(productId, dbLoader);
    }

    /**
     * 主动删除当前节点本地缓存和 Redis 缓存。
     */
    public void invalidate(Long productId) {
        localCache.invalidate(productId);
        redisService.del(ProductCacheConstants.productDetailKey(productId));
    }

    /**
     * 使用 Redis 分布式锁互斥重建缓存，防止击穿。
     */
    private PmsPortalProductDetail rebuildCacheWithMutex(Long productId, Supplier<PmsPortalProductDetail> dbLoader) {
        String lockKey = ProductCacheConstants.productDetailLockKey(productId);
        String lockValue = UUID.randomUUID().toString();
        if (!tryLock(lockKey, lockValue)) {
            return spinWaitForCache(productId, dbLoader);
        }
        try {
            ProductDetailCacheValue cacheValue = getRedisCache(productId);
            if (cacheValue != null) {
                localCache.put(productId, cacheValue);
                return unwrap(cacheValue);
            }
            PmsPortalProductDetail detail = dbLoader.get();
            ProductDetailCacheValue loadedValue = detail == null ? ProductDetailCacheValue.empty() : ProductDetailCacheValue.of(detail);
            putRedisCache(productId, loadedValue);
            localCache.put(productId, loadedValue);
            return detail;
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 抢锁失败后短暂自旋等待其他线程完成缓存回填。
     */
    private PmsPortalProductDetail spinWaitForCache(Long productId, Supplier<PmsPortalProductDetail> dbLoader) {
        for (int i = 0; i < properties.getLockRetryTimes(); i++) {
            sleep(properties.getLockRetryIntervalMillis());
            ProductDetailCacheValue cacheValue = localCache.get(productId);
            if (cacheValue != null) {
                return unwrap(cacheValue);
            }
            cacheValue = getRedisCache(productId);
            if (cacheValue != null) {
                localCache.put(productId, cacheValue);
                return unwrap(cacheValue);
            }
        }
        LOGGER.warn("Product detail cache rebuild wait timeout, fallback to DB, productId:{}", productId);
        sleep(properties.getFallbackSleepMillis());
        return dbLoader.get();
    }

    /**
     * 从 Redis 中读取商品详情缓存。
     */
    private ProductDetailCacheValue getRedisCache(Long productId) {
        Object cacheObject = redisService.get(ProductCacheConstants.productDetailKey(productId));
        if (cacheObject instanceof ProductDetailCacheValue) {
            return (ProductDetailCacheValue) cacheObject;
        }
        return null;
    }

    /**
     * 将商品详情写入 Redis，并附带随机抖动 TTL。
     */
    private void putRedisCache(Long productId, ProductDetailCacheValue cacheValue) {
        long ttlSeconds = cacheValue.isEmpty() ? properties.getRedisNullTtlSeconds() : properties.getRedisTtlSeconds();
        redisService.set(ProductCacheConstants.productDetailKey(productId), cacheValue,
                ttlSeconds + randomJitter(properties.getRedisTtlJitterSeconds()));
    }

    /**
     * 尝试获取商品详情缓存重建锁。
     */
    private boolean tryLock(String lockKey, String lockValue) {
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(properties.getLockTtlSeconds()));
        return Boolean.TRUE.equals(success);
    }

    /**
     * 使用 Lua 脚本安全释放分布式锁。
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            stringRedisTemplate.execute(releaseLockRedisScript, Collections.singletonList(lockKey), lockValue);
        } catch (Exception e) {
            LOGGER.warn("Release product detail cache lock failed, key:{}, message:{}", lockKey, e.getMessage());
        }
    }

    /**
     * 将缓存包装对象还原为业务对象。
     */
    private PmsPortalProductDetail unwrap(ProductDetailCacheValue cacheValue) {
        return cacheValue.isEmpty() ? null : cacheValue.getData();
    }

    /**
     * 生成 Redis TTL 的随机抖动值。
     */
    private long randomJitter(long maxJitterSeconds) {
        if (maxJitterSeconds <= 0) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(maxJitterSeconds + 1);
    }

    /**
     * 线程短暂休眠，用于锁竞争退避。
     */
    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
