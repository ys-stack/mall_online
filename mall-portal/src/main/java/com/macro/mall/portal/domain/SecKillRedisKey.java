package com.macro.mall.portal.domain;

/**
 * 秒杀 Redis Key 统一定义。
 */
public class SecKillRedisKey {
    private static final String PREFIX = "mall:seckill:";

    private SecKillRedisKey() {
    }

    /**
     * 获取秒杀库存 Key。
     */
    public static String stockKey(Long relationId) {
        return PREFIX + "stock:" + relationId;
    }

    /**
     * 获取用户秒杀资格 Key，用于一人一单和防重复提交。
     */
    public static String userKey(Long relationId, Long memberId) {
        return PREFIX + "user:" + relationId + ":" + memberId;
    }

    /**
     * 获取异步建单处理中 Key，用于消费者侧防重复并发消费。
     */
    public static String processingKey(String requestId) {
        return PREFIX + "processing:" + requestId;
    }

    /**
     * 获取订单创建成功 Key，用于消费者幂等和前端查询。
     */
    public static String orderKey(Long relationId, Long memberId) {
        return PREFIX + "order:" + relationId + ":" + memberId;
    }
}
