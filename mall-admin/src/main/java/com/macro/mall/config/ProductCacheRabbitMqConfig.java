package com.macro.mall.config;

import com.macro.mall.common.cache.product.ProductCacheConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品详情缓存失效 RabbitMQ 配置。
 */
@Configuration
public class ProductCacheRabbitMqConfig {
    /**
     * 定义商品详情缓存失效广播交换机。
     */
    @Bean
    public FanoutExchange productDetailInvalidateExchange() {
        return ExchangeBuilder.fanoutExchange(ProductCacheConstants.PRODUCT_DETAIL_INVALIDATE_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 定义商品详情缓存失效死信交换机。
     */
    @Bean
    public FanoutExchange productDetailInvalidateDeadLetterExchange() {
        return ExchangeBuilder.fanoutExchange(ProductCacheConstants.PRODUCT_DETAIL_INVALIDATE_DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 定义商品详情缓存失效死信队列。
     */
    @Bean
    public Queue productDetailInvalidateDeadLetterQueue() {
        return new Queue(ProductCacheConstants.PRODUCT_DETAIL_INVALIDATE_DEAD_LETTER_QUEUE, true);
    }

    /**
     * 将死信队列绑定到死信交换机。
     */
    @Bean
    public Binding productDetailInvalidateDeadLetterBinding(FanoutExchange productDetailInvalidateDeadLetterExchange,
                                                            Queue productDetailInvalidateDeadLetterQueue) {
        return BindingBuilder.bind(productDetailInvalidateDeadLetterQueue).to(productDetailInvalidateDeadLetterExchange);
    }
}
