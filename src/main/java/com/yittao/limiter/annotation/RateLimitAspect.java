package com.yittao.limiter.aspect;

import com.yittao.limiter.annotation.KeyType;
import com.yittao.limiter.annotation.RateLimit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 拦截 @RateLimit 注解，基于Redis实现分布式限流
 *
 * @author yittao
 */
@Aspect
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private static final String KEY_PREFIX = "rate_limit:";

    private final RedissonClient redissonClient;

    public RateLimitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(com.yittao.limiter.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String redisKey = buildKey(rateLimit);

        RAtomicLong counter = redissonClient.getAtomicLong(redisKey);
        long count = counter.incrementAndGet();

        // 首次访问时设置过期时间
        if (count == 1) {
            counter.expire(rateLimit.period(), rateLimit.unit());
        }

        if (count > rateLimit.limit()) {
            log.warn("接口限流触发: key={}, count={}, limit={}", redisKey, count, rateLimit.limit());

            // 如果配置了降级方法，尝试调用
            if (!rateLimit.fallback().isEmpty()) {
                return invokeFallback(joinPoint, rateLimit.fallback());
            }

            throw new RateLimitException("请求过于频繁，请稍后再试");
        }

        return joinPoint.proceed();
    }

    /**
     * 构建Redis限流Key
     */
    private String buildKey(RateLimit rateLimit) {
        StringBuilder sb = new StringBuilder(KEY_PREFIX);
        sb.append(rateLimit.key()).append(":");

        switch (rateLimit.keyType()) {
            case IP:
                sb.append(getClientIp());
                break;
            case USER_ID:
                sb.append(getCurrentUserId());
                break;
            case CUSTOM:
                sb.append("custom");
                break;
            default:
                sb.append(getClientIp());
        }

        return sb.toString();
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取当前用户ID（需根据项目实际情况实现）
     */
    private String getCurrentUserId() {
        // 从SecurityContext或Token中获取，此处为示例
        return "default_user";
    }

    /**
     * 调用降级方法
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint, String fallbackMethod) throws Throwable {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Class<?> targetClass = joinPoint.getTarget().getClass();
            Method method = targetClass.getMethod(fallbackMethod, signature.getParameterTypes());
            return method.invoke(joinPoint.getTarget(), joinPoint.getArgs());
        } catch (NoSuchMethodException e) {
            log.error("降级方法不存在: {}", fallbackMethod, e);
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }
    }

    /**
     * 限流异常
     */
    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
