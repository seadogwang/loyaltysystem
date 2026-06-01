package com.loyalty.saas.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 触达通道熔断管理器 — 防止外部网关故障阻塞核心交易链路。
 *
 * <p>熔断触发条件：
 * <ul>
 *   <li>连续超时（HTTP 504/502）</li>
 *   <li>第三方频率限制（HTTP 429）</li>
 *   <li>连接超时 / 读取超时</li>
 * </ul>
 *
 * <p>熔断行为：
 * <ul>
 *   <li>触发后对该通道短路熔断 60 秒</li>
 *   <li>短路期间所有该通道的消息直接标记 RETRY，不尝试发送</li>
 *   <li>熔断恢复后自动放开</li>
 * </ul>
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap} + {@code volatile} 组合。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Component
public class CircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManager.class);

    /** 熔断时长（秒） */
    private static final long CIRCUIT_BREAK_SECONDS = 60;
    /** 触发熔断的连续失败次数 */
    private static final int FAILURE_THRESHOLD = 5;

    /** 通道 → 熔断状态 */
    private final ConcurrentHashMap<String, CircuitState> states = new ConcurrentHashMap<>();

    /**
     * 记录一次失败。
     *
     * @return true 如果刚触发熔断
     */
    public boolean recordFailure(String channel) {
        CircuitState state = states.computeIfAbsent(channel, k -> new CircuitState());
        int failures = state.failureCount.incrementAndGet();

        if (failures >= FAILURE_THRESHOLD && !state.isOpen()) {
            state.open(Instant.now().plusSeconds(CIRCUIT_BREAK_SECONDS));
            log.warn("[CircuitBreaker] 通道 [{}] 连续失败 {} 次，熔断 {} 秒",
                    channel, failures, CIRCUIT_BREAK_SECONDS);
            return true;
        }
        return false;
    }

    /**
     * 记录一次成功（重置失败计数）。
     */
    public void recordSuccess(String channel) {
        CircuitState state = states.get(channel);
        if (state != null) {
            state.failureCount.set(0);
            if (state.isOpen()) {
                state.close();
                log.info("[CircuitBreaker] 通道 [{}] 熔断恢复", channel);
            }
        }
    }

    /**
     * 检查通道当前是否处于熔断状态。
     *
     * @return true 如果已熔断（应跳过发送）
     */
    public boolean isCircuitOpen(String channel) {
        CircuitState state = states.get(channel);
        if (state == null) return false;
        return state.isOpen();
    }

    /**
     * 获取熔断剩余时间（秒），0 表示未熔断。
     */
    public long getRemainingSeconds(String channel) {
        CircuitState state = states.get(channel);
        if (state == null || !state.isOpen()) return 0;
        return Math.max(0, state.openUntil.getEpochSecond() - Instant.now().getEpochSecond());
    }

    /** 熔断状态 */
    static class CircuitState {
        final java.util.concurrent.atomic.AtomicInteger failureCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        volatile boolean open;
        volatile Instant openUntil;

        boolean isOpen() {
            if (!open) return false;
            if (Instant.now().isAfter(openUntil)) {
                open = false; // 自动恢复
                return false;
            }
            return true;
        }

        void open(Instant until) { this.open = true; this.openUntil = until; }
        void close() { this.open = false; this.openUntil = null; }
    }
}