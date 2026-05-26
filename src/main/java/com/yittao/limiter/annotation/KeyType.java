package com.yittao.limiter.annotation;

/**
 * 限流Key生成策略
 *
 * @author yittao
 */
public enum KeyType {

    /**
     * 按客户端IP限流
     */
    IP,

    /**
     * 按用户ID限流
     */
    USER_ID,

    /**
     * 自定义Key
     */
    CUSTOM
}
