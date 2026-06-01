package com.loyalty.saas.job;

import com.loyalty.saas.common.event.EventBridge;
import com.loyalty.saas.domain.entity.EventFact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 死信队列指数退避重试任务。
 *
 * <p>扫描 event_inbox 中状态为 FAILED 的事件，实现指数退避算法：
 * <pre>
 *   delay = baseDelay * 2^(retryCount - 1)
 *   baseDelay = 30 seconds
 *   maxDelay = 3600 seconds (1 hour)
 * </pre>
 *
 * <p>计算 {@code next_retry_time <= NOW()} 的记录，重新唤醒并
 * 推入本地 EventBridge（或 LocalEventBus 虚拟分区）重新处理。
 *
 * <p><b>租户隔离</b>：通过 {@link TenantAwareJob#forEachTenant} 保证。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Component
public class EventInboxRetryJob extends TenantAwareJob {

    /** 基础延迟（秒） */
    private static final long BASE_DELAY_SECONDS = 30;
    /** 最大延迟（秒） */
    private static final long MAX_DELAY_SECONDS = 3600;
    /** 最大重试次数 */
    private static final int MAX_RETRY_COUNT = 5;
    /** 每批处理上限 */
    private static final int BATCH_SIZE = 50;

    private final EventBridge eventBridge;

    public EventInboxRetryJob(@Autowired(required = false) EventBridge eventBridge) {
        this.eventBridge = eventBridge;
    }

    @Override protected String getJobName() { return "EventInboxRetryJob"; }

    /** 每 10 秒扫描一次 */
    @Scheduled(fixedDelay = 10000)
    public void execute() {
        forEachTenant(this::retryTenant);
    }

    @Transactional
    void retryTenant(String programCode) {
        // 查询可重试的事件：FAILED 状态 + next_retry_time <= NOW() + retryCount < MAX
        @SuppressWarnings("unchecked")
        List<Object[]> retryable = em.createNativeQuery(
                "SELECT id, idempotency_key, payload, source_channel, retry_count, error_message "
                        + "FROM event_inbox "
                        + "WHERE program_code = ? "
                        + "  AND status IN ('FAILED', 'TRANSFORM_FAILED') "
                        + "  AND retry_count < ? "
                        + "  AND (next_retry_at IS NULL OR next_retry_at <= NOW()) "
                        + "ORDER BY next_retry_at ASC NULLS FIRST "
                        + "LIMIT ? "
                        + "FOR UPDATE SKIP LOCKED",
                Object[].class)
                .setParameter(1, programCode)
                .setParameter(2, MAX_RETRY_COUNT)
                .setParameter(3, BATCH_SIZE)
                .getResultList();

        if (retryable.isEmpty()) return;

        log.info("[EventInboxRetry] 租户 [{}] 可重试事件: {} 条", programCode, retryable.size());

        int retried = 0;
        int exhausted = 0;

        for (Object[] row : retryable) {
            Long eventId = ((Number) row[0]).longValue();
            String idempotencyKey = (String) row[1];
            String payloadJson = (String) row[2];
            String channel = (String) row[3];
            int currentRetry = row[4] != null ? ((Number) row[4]).intValue() : 0;
            int newRetry = currentRetry + 1;

            if (newRetry > MAX_RETRY_COUNT) {
                // 重试耗尽 → 标记为 DEAD
                em.createNativeQuery(
                        "UPDATE event_inbox SET status = 'DEAD', retry_count = ? "
                                + "WHERE id = ? AND program_code = ?")
                        .setParameter(1, newRetry)
                        .setParameter(2, eventId)
                        .setParameter(3, programCode)
                        .executeUpdate();
                exhausted++;
                log.error("[EventInboxRetry] 重试耗尽→死信: id={}, retries={}", eventId, newRetry);
                continue;
            }

            // 计算下次重试时间（指数退避）
            long delaySec = Math.min(
                    BASE_DELAY_SECONDS * (1L << (newRetry - 1)),
                    MAX_DELAY_SECONDS);
            LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySec);

            // 更新重试状态
            em.createNativeQuery(
                    "UPDATE event_inbox SET status = 'RETRYING', retry_count = ?, "
                            + "next_retry_at = ? "
                            + "WHERE id = ? AND program_code = ?")
                    .setParameter(1, newRetry)
                    .setParameter(2, nextRetryAt)
                    .setParameter(3, eventId)
                    .setParameter(4, programCode)
                    .executeUpdate();

            // 推入 EventBridge 重新消费
            try {
                Map<String, Object> payload = parseJson(payloadJson);
                EventFact fact = new EventFact(programCode, "RETRY_" + (channel != null ? channel : "UNKNOWN"),
                        null, channel, Instant.now(), idempotencyKey, null, payload);

                if (eventBridge != null) {
                    // 通过 LocalEventBus 虚拟分区重新投递
                    eventBridge.publish("loyalty-events",
                            idempotencyKey != null ? idempotencyKey : "retry",
                            fact);
                }
                retried++;
            } catch (Exception e) {
                log.error("[EventInboxRetry] 重新投递失败: id={}", eventId, e);
            }
        }

        log.info("[EventInboxRetry] 租户 [{}] 处理完成: retried={}, exhausted={}",
                programCode, retried, exhausted);
    }

    /**
     * 计算指数退避延迟秒数。
     */
    public static long calculateBackoffDelay(int retryCount) {
        return Math.min(BASE_DELAY_SECONDS * (1L << (retryCount - 1)), MAX_DELAY_SECONDS);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("_raw", json != null ? json : "");
        }
    }
}