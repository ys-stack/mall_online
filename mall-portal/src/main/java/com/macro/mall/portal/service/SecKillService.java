package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.SecKillOrderMessage;
import com.macro.mall.portal.domain.SecKillResult;
import com.macro.mall.portal.domain.SecKillSubmitParam;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀业务 Service。
 */
public interface SecKillService {
    /**
     * 将活动库存预热到 Redis，真实项目中通常由活动发布、定时任务或运营后台触发。
     */
    Integer preheat(Long flashPromotionProductRelationId);

    /**
     * 提交秒杀请求，只做快速校验、Redis 原子扣减和 MQ 投递，不在请求线程直接写订单。
     */
    SecKillResult submit(SecKillSubmitParam param);

    /**
     * 消费 MQ 消息并创建秒杀订单，使用事务保证扣减 DB 秒杀库存和写订单的一致性。
     */
    @Transactional
    void createOrder(SecKillOrderMessage message);

    /**
     * 查询当前用户的秒杀建单结果，异步下单场景下供前端轮询使用。
     */
    String queryOrderSn(Long flashPromotionProductRelationId);

    /**
     * 补偿投递秒杀 outbox 消息，防止应用宕机导致 MQ 消息丢失。
     */
    void dispatchPendingOutboxMessages(Integer limit);

    /**
     * MQ 投递失败或消费者建单失败时补偿 Redis 资格和预扣库存。
     */
    void compensate(SecKillOrderMessage message);
}
