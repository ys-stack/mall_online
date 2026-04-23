package com.macro.mall.portal.component;

import com.macro.mall.portal.domain.SecKillOrderMessage;
import com.macro.mall.portal.domain.QueueEnum;
import com.macro.mall.portal.service.SecKillService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀建单消息消费者。
 */
@Slf4j
@Component
@RabbitListener(queues = "mall.seckill.order.create", containerFactory = "seckillOrderRabbitListenerContainerFactory")
public class SecKillOrderReceiver {
    @Autowired
    private SecKillService secKillService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${mall.seckill.mq.max-retry-times:3}")
    private Integer maxRetryTimes;

    /**
     * 消费秒杀建单消息，订单创建成功才 ACK；失败先进入延迟重试队列，超过次数后补偿并进入死信队列。
     */
    @RabbitHandler
    public void handle(SecKillOrderMessage message,
                       Channel channel,
                       @Header(name = "x-retry-count", required = false) Integer retryCount,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            secKillService.createOrder(message);
            channel.basicAck(deliveryTag, false);
            log.info("Seckill order created, requestId:{}", message.getRequestId());
        } catch (Exception e) {
            handleFailure(message, channel, deliveryTag, retryCount, e);
        }
    }

    /**
     * 处理建单失败场景，优先延迟重试，超过最大次数后再补偿 Redis 并进入死信。
     */
    private void handleFailure(SecKillOrderMessage message,
                               Channel channel,
                               long deliveryTag,
                               Integer retryCount,
                               Exception cause) throws IOException {
        int currentRetryCount = retryCount == null ? 0 : retryCount;
        if (message != null && currentRetryCount < maxRetryTimes) {
            try {
                sendRetryMessage(message, currentRetryCount + 1);
                channel.basicAck(deliveryTag, false);
                log.warn("Retry seckill order message, requestId:{}, retryCount:{}",
                        message.getRequestId(), currentRetryCount + 1, cause);
                return;
            } catch (Exception retryException) {
                channel.basicNack(deliveryTag, false, true);
                log.error("Send seckill retry message failed, requestId:{}",
                        message.getRequestId(), retryException);
                return;
            }
        }
        secKillService.compensate(message);
        channel.basicReject(deliveryTag, false);
        log.error("Create seckill order failed and moved to dead letter, requestId:{}",
                message == null ? null : message.getRequestId(), cause);
    }

    /**
     * 发送秒杀延迟重试消息，等待 retry queue TTL 到期后再回到主队列。
     */
    private void sendRetryMessage(SecKillOrderMessage message, Integer nextRetryCount) {
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(
                    QueueEnum.QUEUE_SECKILL_ORDER_RETRY.getExchange(),
                    QueueEnum.QUEUE_SECKILL_ORDER_RETRY.getRouteKey(),
                    message,
                    retryMessage -> {
                        retryMessage.getMessageProperties().setMessageId(message.getMessageId());
                        retryMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        retryMessage.getMessageProperties().setHeader("x-retry-count", nextRetryCount);
                        return retryMessage;
                    });
            operations.waitForConfirmsOrDie(5000);
            return true;
        });
    }
}
