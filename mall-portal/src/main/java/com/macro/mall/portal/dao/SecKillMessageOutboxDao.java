package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.SecKillMessageOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 秒杀 outbox 消息 Dao。
 */
public interface SecKillMessageOutboxDao {
    /**
     * 新增秒杀 outbox 消息。
     */
    int insert(SecKillMessageOutbox record);

    /**
     * 标记消息已完成 Redis 资格预扣。
     */
    int markReserved(@Param("messageId") String messageId);

    /**
     * 标记消息正在投递 MQ。
     */
    int markProcessing(@Param("messageId") String messageId);

    /**
     * 标记消息已经成功投递 MQ。
     */
    int markSent(@Param("messageId") String messageId);

    /**
     * 标记消息投递失败并设置下一次重试时间。
     */
    int markFailed(@Param("messageId") String messageId,
                   @Param("lastError") String lastError,
                   @Param("nextRetryTime") Date nextRetryTime);

    /**
     * 标记消息取消，通常表示 Redis 资格预扣没有成功。
     */
    int markCanceled(@Param("messageId") String messageId,
                     @Param("lastError") String lastError);

    /**
     * 查询需要补偿投递的 outbox 消息。
     */
    List<SecKillMessageOutbox> listDispatchable(@Param("limit") Integer limit,
                                                @Param("processingTimeoutSeconds") Integer processingTimeoutSeconds);
}
