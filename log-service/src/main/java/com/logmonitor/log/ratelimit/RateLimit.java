package com.logmonitor.log.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（Redis 固定窗口计数）
 * <p>
 * 标注在 Controller 方法上，由 RateLimitInterceptor 按 用户/IP + key 维度计数，
 * 超过 limit 返回 429。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内最大请求次数 */
    int limit() default 30;

    /** 窗口大小（秒） */
    int windowSeconds() default 10;

    /** 限流维度 key；为空时使用方法名 */
    String key() default "";
}
