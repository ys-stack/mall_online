package com.macro.mall.portal.domain;

/**
 * 商品详情缓存值，支持空值缓存。
 */
public class ProductDetailCacheValue {
    private boolean empty;
    private PmsPortalProductDetail data;

    /**
     * 创建空值缓存对象。
     */
    public static ProductDetailCacheValue empty() {
        ProductDetailCacheValue cacheValue = new ProductDetailCacheValue();
        cacheValue.setEmpty(true);
        return cacheValue;
    }

    /**
     * 创建正常商品详情缓存对象。
     */
    public static ProductDetailCacheValue of(PmsPortalProductDetail data) {
        ProductDetailCacheValue cacheValue = new ProductDetailCacheValue();
        cacheValue.setEmpty(false);
        cacheValue.setData(data);
        return cacheValue;
    }

    /**
     * 判断当前是否为空值缓存。
     */
    public boolean isEmpty() {
        return empty;
    }

    /**
     * 设置是否为空值缓存。
     */
    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    /**
     * 获取商品详情数据。
     */
    public PmsPortalProductDetail getData() {
        return data;
    }

    /**
     * 设置商品详情数据。
     */
    public void setData(PmsPortalProductDetail data) {
        this.data = data;
    }
}
