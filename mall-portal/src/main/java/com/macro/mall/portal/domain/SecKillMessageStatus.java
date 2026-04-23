package com.macro.mall.portal.domain;

/**
 * 秒杀 outbox 消息状态。
 */
public class SecKillMessageStatus {
    public static final String INIT = "INIT";
    public static final String RESERVED = "RESERVED";
    public static final String PROCESSING = "PROCESSING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String CANCELED = "CANCELED";

    private SecKillMessageStatus() {
    }
}
