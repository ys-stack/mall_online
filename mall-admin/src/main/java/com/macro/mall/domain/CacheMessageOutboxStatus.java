package com.macro.mall.domain;

/**
 * 缓存失效 outbox 消息状态。
 */
public final class CacheMessageOutboxStatus {
    private CacheMessageOutboxStatus() {
    }

    public static final int PENDING = 0;
    public static final int PROCESSING = 1;
    public static final int SENT = 2;
    public static final int FAILED = 3;
}
