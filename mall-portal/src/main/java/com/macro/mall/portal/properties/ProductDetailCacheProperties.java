package com.macro.mall.portal.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商品详情两级缓存配置。
 */
@Component
@ConfigurationProperties(prefix = "mall.cache.product.detail")
public class ProductDetailCacheProperties {
    private long localMaximumSize = 10000L;
    private long localTtlSeconds = 60L;
    private long localNullTtlSeconds = 15L;
    private long localTtlJitterSeconds = 15L;
    private long redisTtlSeconds = 1800L;
    private long redisNullTtlSeconds = 60L;
    private long redisTtlJitterSeconds = 300L;
    private long lockTtlSeconds = 10L;
    private int lockRetryTimes = 20;
    private long lockRetryIntervalMillis = 50L;
    private long fallbackSleepMillis = 100L;

    /**
     * 获取本地缓存最大容量。
     */
    public long getLocalMaximumSize() {
        return localMaximumSize;
    }

    /**
     * 设置本地缓存最大容量。
     */
    public void setLocalMaximumSize(long localMaximumSize) {
        this.localMaximumSize = localMaximumSize;
    }

    /**
     * 获取本地缓存正常 TTL。
     */
    public long getLocalTtlSeconds() {
        return localTtlSeconds;
    }

    /**
     * 设置本地缓存正常 TTL。
     */
    public void setLocalTtlSeconds(long localTtlSeconds) {
        this.localTtlSeconds = localTtlSeconds;
    }

    /**
     * 获取本地空值缓存 TTL。
     */
    public long getLocalNullTtlSeconds() {
        return localNullTtlSeconds;
    }

    /**
     * 设置本地空值缓存 TTL。
     */
    public void setLocalNullTtlSeconds(long localNullTtlSeconds) {
        this.localNullTtlSeconds = localNullTtlSeconds;
    }

    /**
     * 获取本地缓存 TTL 抖动范围。
     */
    public long getLocalTtlJitterSeconds() {
        return localTtlJitterSeconds;
    }

    /**
     * 设置本地缓存 TTL 抖动范围。
     */
    public void setLocalTtlJitterSeconds(long localTtlJitterSeconds) {
        this.localTtlJitterSeconds = localTtlJitterSeconds;
    }

    /**
     * 获取 Redis 正常缓存 TTL。
     */
    public long getRedisTtlSeconds() {
        return redisTtlSeconds;
    }

    /**
     * 设置 Redis 正常缓存 TTL。
     */
    public void setRedisTtlSeconds(long redisTtlSeconds) {
        this.redisTtlSeconds = redisTtlSeconds;
    }

    /**
     * 获取 Redis 空值缓存 TTL。
     */
    public long getRedisNullTtlSeconds() {
        return redisNullTtlSeconds;
    }

    /**
     * 设置 Redis 空值缓存 TTL。
     */
    public void setRedisNullTtlSeconds(long redisNullTtlSeconds) {
        this.redisNullTtlSeconds = redisNullTtlSeconds;
    }

    /**
     * 获取 Redis 缓存 TTL 抖动范围。
     */
    public long getRedisTtlJitterSeconds() {
        return redisTtlJitterSeconds;
    }

    /**
     * 设置 Redis 缓存 TTL 抖动范围。
     */
    public void setRedisTtlJitterSeconds(long redisTtlJitterSeconds) {
        this.redisTtlJitterSeconds = redisTtlJitterSeconds;
    }

    /**
     * 获取互斥锁 TTL。
     */
    public long getLockTtlSeconds() {
        return lockTtlSeconds;
    }

    /**
     * 设置互斥锁 TTL。
     */
    public void setLockTtlSeconds(long lockTtlSeconds) {
        this.lockTtlSeconds = lockTtlSeconds;
    }

    /**
     * 获取锁竞争失败后的重试次数。
     */
    public int getLockRetryTimes() {
        return lockRetryTimes;
    }

    /**
     * 设置锁竞争失败后的重试次数。
     */
    public void setLockRetryTimes(int lockRetryTimes) {
        this.lockRetryTimes = lockRetryTimes;
    }

    /**
     * 获取每次重试之间的等待时间。
     */
    public long getLockRetryIntervalMillis() {
        return lockRetryIntervalMillis;
    }

    /**
     * 设置每次重试之间的等待时间。
     */
    public void setLockRetryIntervalMillis(long lockRetryIntervalMillis) {
        this.lockRetryIntervalMillis = lockRetryIntervalMillis;
    }

    /**
     * 获取兜底回源前的额外等待时间。
     */
    public long getFallbackSleepMillis() {
        return fallbackSleepMillis;
    }

    /**
     * 设置兜底回源前的额外等待时间。
     */
    public void setFallbackSleepMillis(long fallbackSleepMillis) {
        this.fallbackSleepMillis = fallbackSleepMillis;
    }
}
