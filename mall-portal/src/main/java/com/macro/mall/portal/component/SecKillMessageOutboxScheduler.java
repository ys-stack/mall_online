package com.macro.mall.portal.component;

import com.macro.mall.portal.service.SecKillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀 outbox 补偿投递调度器。
 */
@Slf4j
@Component
public class SecKillMessageOutboxScheduler {
    @Autowired
    private SecKillService secKillService;

    @Value("${mall.seckill.outbox.scan-limit:100}")
    private Integer scanLimit;

    /**
     * 定时扫描未投递成功的秒杀 outbox 消息，防止应用宕机或 MQ 短暂故障导致消息丢失。
     */
    @Scheduled(fixedDelayString = "${mall.seckill.outbox.fixed-delay-millis:5000}")
    public void dispatchPendingMessages() {
        try {
            secKillService.dispatchPendingOutboxMessages(scanLimit);
        } catch (Exception e) {
            log.error("Dispatch seckill outbox messages failed", e);
        }
    }
}
