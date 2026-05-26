package com.yittao.limiter.annotation;

import java.util.concurrent.TimeUnit;

/**
 * 分布式限流注解
 * 在方法上添加此注解，基于Redis实现接口级别的访问频率控制
 *
 * <pre>
 * 用法示例：
 * {@code
 * @RateLimit(key = "download", limit = 5, period = 1, unit = TimeUnit.MINUTES)
 * public void downloadFile(String fileId) { ... }
 * }
 * </pre>
 *
 * @author yittao
 */
public @interface RateLimit {

    /**
     * 限流Key前缀
     */
    String key();

    /**
     * 时间窗口内允许的最大请求数
     */
    int limit() default 10;

    /**
     * 时间窗口大小
     */
    int period() default 1;

    /**
     * 时间窗口单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Key生成策略
     */
    KeyType keyType() default KeyType.IP;

    /**
     * 触发限流时的降级方法名（可选）
     */
    String fallback() default "";
}
