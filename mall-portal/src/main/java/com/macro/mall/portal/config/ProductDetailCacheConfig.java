package com.macro.mall.portal.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.macro.mall.portal.component.ProductDetailLocalCache;
import com.macro.mall.portal.properties.ProductDetailCacheProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品详情缓存配置。
 */
@Configuration
public class ProductDetailCacheConfig {

    /**
     * 创建商品详情本地 Caffeine 缓存。
     */
    @Bean
    public Cache<String, ProductDetailLocalCache.LocalCacheEntry> productDetailCaffeineCache(ProductDetailCacheProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.getLocalMaximumSize())
                .build();
    }
}
