package com.macro.mall.dao;

import com.macro.mall.domain.CacheMessageOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 缓存失效 outbox DAO。
 */
public interface CacheMessageOutboxDao {
    /**
     * 插入一条 outbox 消息。
     */
    int insert(CacheMessageOutbox outbox);

    /**
     * 根据主键查询 outbox 消息。
     */
    CacheMessageOutbox getItem(@Param("id") Long id);

    /**
     * 查询当前可投递的 outbox 消息。
     */
    List<CacheMessageOutbox> listDispatchable(@Param("statusList") List<Integer> statusList,
                                              @Param("processingExpireTime") Date processingExpireTime,
                                              @Param("now") Date now,
                                              @Param("limit") Integer limit);

    /**
     * 将消息抢占为处理中状态。
     */
    int markProcessing(@Param("id") Long id,
                       @Param("statusList") List<Integer> statusList,
                       @Param("updateTime") Date updateTime);

    /**
     * 将消息标记为已发送。
     */
    int markSent(@Param("id") Long id,
                 @Param("sentTime") Date sentTime,
                 @Param("updateTime") Date updateTime);

    /**
     * 将消息标记为发送失败并设置下次重试时间。
     */
    int markFailed(@Param("id") Long id,
                   @Param("retryCount") Integer retryCount,
                   @Param("availableTime") Date availableTime,
                   @Param("lastError") String lastError,
                   @Param("updateTime") Date updateTime);

    /**
     * 清理已经发送完成的历史消息。
     */
    int deleteSentBefore(@Param("beforeTime") Date beforeTime,
                         @Param("limit") Integer limit);
}
