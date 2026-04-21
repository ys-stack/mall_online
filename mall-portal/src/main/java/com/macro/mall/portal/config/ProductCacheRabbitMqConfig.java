package com.macro.mall.portal.config;

import com.macro.mall.common.cache.product.ProductCacheConstants;
import com.macro.mall.portal.properties.ProductCacheMqConsumerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.InetAddress;

/**
 * 商品详情缓存失效 RabbitMQ 消费配置。
 */
@Configuration
public class ProductCacheRabbitMqConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCacheRabbitMqConfig.class);

    /**
     * 计算当前 portal 实例的唯一标识。
     */
    @Bean
    public String productCacheInstanceId(ProductCacheMqConsumerProperties properties,
                                         @Value("${spring.application.name:mall-portal}") String applicationName,
                                         @Value("${server.port:8085}") String serverPort) {
        if (StringUtils.hasText(properties.getInstanceId())) {
            return properties.getInstanceId();
        }
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            return applicationName + "-" + hostName.replace('.', '-') + "-" + serverPort;
        } catch (Exception e) {
            LOGGER.warn("Resolve product cache instance id failed, use fallback value:{}", e.getMessage());
            return applicationName + "-" + serverPort;
        }
    }

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
        return QueueBuilder.durable(ProductCacheConstants.PRODUCT_DETAIL_INVALIDATE_DEAD_LETTER_QUEUE).build();
    }

    /**
     * 定义当前 portal 实例专属的持久化缓存失效队列。
     */
    @Bean
    public Queue productCacheInvalidationQueue(String productCacheInstanceId,
                                               ProductCacheMqConsumerProperties properties) {
        return QueueBuilder.durable(ProductCacheConstants.productDetailInvalidateQueue(productCacheInstanceId))
                .withArgument("x-queue-type", properties.getQueueType())
                .withArgument("x-dead-letter-exchange", ProductCacheConstants.PRODUCT_DETAIL_INVALIDATE_DEAD_LETTER_EXCHANGE)
                .build();
    }

    /**
     * 将当前实例队列绑定到商品详情缓存失效广播交换机。
     */
    @Bean
    public Binding productCacheInvalidationBinding(FanoutExchange productDetailInvalidateExchange,
                                                   Queue productCacheInvalidationQueue) {
        return BindingBuilder.bind(productCacheInvalidationQueue).to(productDetailInvalidateExchange);
    }

    /**
     * 将死信队列绑定到死信交换机。
     */
    @Bean
    public Binding productCacheInvalidationDeadLetterBinding(FanoutExchange productDetailInvalidateDeadLetterExchange,
                                                             Queue productDetailInvalidateDeadLetterQueue) {
        return BindingBuilder.bind(productDetailInvalidateDeadLetterQueue).to(productDetailInvalidateDeadLetterExchange);
    }

    /**
     * 创建商品详情缓存失效消息监听容器工厂，并开启手动 ack。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory productCacheInvalidateRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ProductCacheMqConsumerProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(properties.getConsumerConcurrency());
        factory.setMaxConcurrentConsumers(properties.getMaxConsumerConcurrency());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
