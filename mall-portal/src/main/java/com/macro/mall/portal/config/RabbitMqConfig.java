package com.macro.mall.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.domain.QueueEnum;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消息队列相关配置
 * Created by macro on 2018/9/14.
 */
@Configuration
public class RabbitMqConfig {

    /**
     * 创建 RabbitMQ JSON 消息转换器，避免默认 Java 序列化带来的跨语言和排查成本。
     */
    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 订单消息实际消费队列所绑定的交换机
     */
    @Bean
    DirectExchange orderDirect() {
        return ExchangeBuilder
                .directExchange(QueueEnum.QUEUE_ORDER_CANCEL.getExchange())
                .durable(true)
                .build();
    }

    /**
     * 订单延迟队列所绑定的交换机
     */
    @Bean
    DirectExchange orderTtlDirect() {
        return ExchangeBuilder
                .directExchange(QueueEnum.QUEUE_TTL_ORDER_CANCEL.getExchange())
                .durable(true)
                .build();
    }

    /**
     * 订单实际消费队列
     */
    @Bean
    public Queue orderQueue() {
        return new Queue(QueueEnum.QUEUE_ORDER_CANCEL.getName());
    }

    /**
     * 订单延迟队列（死信队列）
     */
    @Bean
    public Queue orderTtlQueue() {
        return QueueBuilder
                .durable(QueueEnum.QUEUE_TTL_ORDER_CANCEL.getName())
                .withArgument("x-dead-letter-exchange", QueueEnum.QUEUE_ORDER_CANCEL.getExchange())//到期后转发的交换机
                .withArgument("x-dead-letter-routing-key", QueueEnum.QUEUE_ORDER_CANCEL.getRouteKey())//到期后转发的路由键
                .build();
    }

    /**
     * 将订单队列绑定到交换机
     */
    @Bean
    Binding orderBinding(DirectExchange orderDirect,Queue orderQueue){
        return BindingBuilder
                .bind(orderQueue)
                .to(orderDirect)
                .with(QueueEnum.QUEUE_ORDER_CANCEL.getRouteKey());
    }

    /**
     * 将订单延迟队列绑定到交换机
     */
    @Bean
    Binding orderTtlBinding(DirectExchange orderTtlDirect,Queue orderTtlQueue){
        return BindingBuilder
                .bind(orderTtlQueue)
                .to(orderTtlDirect)
                .with(QueueEnum.QUEUE_TTL_ORDER_CANCEL.getRouteKey());
    }

    /**
     * 定义秒杀建单交换机。
     */
    @Bean
    DirectExchange seckillOrderDirect() {
        return ExchangeBuilder
                .directExchange(QueueEnum.QUEUE_SECKILL_ORDER_CREATE.getExchange())
                .durable(true)
                .build();
    }

    /**
     * 定义秒杀建单死信交换机。
     */
    @Bean
    DirectExchange seckillOrderDeadLetterDirect() {
        return ExchangeBuilder
                .directExchange(QueueEnum.QUEUE_SECKILL_ORDER_DEAD_LETTER.getExchange())
                .durable(true)
                .build();
    }

    /**
     * 定义秒杀建单重试交换机。
     */
    @Bean
    DirectExchange seckillOrderRetryDirect() {
        return ExchangeBuilder
                .directExchange(QueueEnum.QUEUE_SECKILL_ORDER_RETRY.getExchange())
                .durable(true)
                .build();
    }

    /**
     * 定义秒杀建单队列，失败消息进入死信队列，避免静默丢失。
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder
                .durable(QueueEnum.QUEUE_SECKILL_ORDER_CREATE.getName())
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-dead-letter-exchange", QueueEnum.QUEUE_SECKILL_ORDER_DEAD_LETTER.getExchange())
                .withArgument("x-dead-letter-routing-key", QueueEnum.QUEUE_SECKILL_ORDER_DEAD_LETTER.getRouteKey())
                .build();
    }

    /**
     * 定义秒杀建单重试队列，消息延迟后重新投递回主队列。
     */
    @Bean
    public Queue seckillOrderRetryQueue(@Value("${mall.seckill.mq.retry-delay-millis:5000}") Integer retryDelayMillis) {
        return QueueBuilder
                .durable(QueueEnum.QUEUE_SECKILL_ORDER_RETRY.getName())
                .withArgument("x-message-ttl", retryDelayMillis)
                .withArgument("x-dead-letter-exchange", QueueEnum.QUEUE_SECKILL_ORDER_CREATE.getExchange())
                .withArgument("x-dead-letter-routing-key", QueueEnum.QUEUE_SECKILL_ORDER_CREATE.getRouteKey())
                .build();
    }

    /**
     * 定义秒杀建单死信队列，用于人工排查或后续补偿任务处理。
     */
    @Bean
    public Queue seckillOrderDeadLetterQueue() {
        return QueueBuilder
                .durable(QueueEnum.QUEUE_SECKILL_ORDER_DEAD_LETTER.getName())
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    /**
     * 绑定秒杀建单队列到秒杀交换机。
     */
    @Bean
    Binding seckillOrderBinding(DirectExchange seckillOrderDirect, Queue seckillOrderQueue) {
        return BindingBuilder
                .bind(seckillOrderQueue)
                .to(seckillOrderDirect)
                .with(QueueEnum.QUEUE_SECKILL_ORDER_CREATE.getRouteKey());
    }

    /**
     * 绑定秒杀死信队列到死信交换机。
     */
    @Bean
    Binding seckillOrderDeadLetterBinding(DirectExchange seckillOrderDeadLetterDirect, Queue seckillOrderDeadLetterQueue) {
        return BindingBuilder
                .bind(seckillOrderDeadLetterQueue)
                .to(seckillOrderDeadLetterDirect)
                .with(QueueEnum.QUEUE_SECKILL_ORDER_DEAD_LETTER.getRouteKey());
    }

    /**
     * 绑定秒杀重试队列到重试交换机。
     */
    @Bean
    Binding seckillOrderRetryBinding(DirectExchange seckillOrderRetryDirect, Queue seckillOrderRetryQueue) {
        return BindingBuilder
                .bind(seckillOrderRetryQueue)
                .to(seckillOrderRetryDirect)
                .with(QueueEnum.QUEUE_SECKILL_ORDER_RETRY.getRouteKey());
    }

    /**
     * 创建秒杀建单监听容器工厂，使用手动 ACK 保证成功落库后才确认消息。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory seckillOrderRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            MessageConverter rabbitJsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(rabbitJsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

}
