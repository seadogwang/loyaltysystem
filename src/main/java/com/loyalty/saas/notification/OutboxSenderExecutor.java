package com.loyalty.saas.notification;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.loyalty.saas.common.context.TenantContext;
import com.loyalty.saas.domain.entity.NotificationOutbox;
import com.loyalty.saas.domain.repository.NotificationOutboxRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可靠出件箱投递器 — 多线程异步消费 notification_outbox。
 *
 * <p>核心机制：
 * <ol>
 *   <li>定时扫描 PENDING/RETRY 状态的待发送记录</li>
 *   <li>对每条记录，先尝试获取 Redis 分布式锁（基于流水 ID）防止重复投递</li>
 *   <li>根据 channel 路由到对应的 NotificationProvider（SMS/WeChat）</li>
 *   <li>发送成功 → 标记 SENT；发送失败 → 标记 RETRY（指数退避）</li>
 *   <li>熔断器管理：连续失败超过阈值 → 通道短路 60 秒</li>
 * </ol>
 *
 * <p><b>线程安全</b>：
 * <ul>
 *   <li>分布式锁保证同一条记录不会被多个 Worker 并发发送</li>
 *   <li>熔断器用 ConcurrentHashMap 保证线程安全</li>
 * </ul>
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Service
public class OutboxSenderExecutor {

    private static final Logger log = LoggerFactory.getLogger(OutboxSenderExecutor.class);

    private final NotificationOutboxRepository outboxRepo;
    private final CircuitBreakerManager circuitBreaker;
    private final Map<String, NotificationProvider> providerMap;

    /** 出件箱消费线程池 */
    private ExecutorService outboxExecutor;

    /** 分布式锁前缀（骨架，生产环境用 Redis/Redisson） */
    private static final String LOCK_PREFIX = "outbox:lock:";

    /** 每次扫描批处理大小 */
    private static final int BATCH_SIZE = 50;
    /** 指数退避基础延迟（秒） */
    private static final int BASE_RETRY_SECONDS = 60;
    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;
    /** 锁超时（秒） */
    private static final int LOCK_TIMEOUT_SECONDS = 30;

    public OutboxSenderExecutor(NotificationOutboxRepository outboxRepo,
                                 CircuitBreakerManager circuitBreaker,
                                 List<NotificationProvider> providers) {
        this.outboxRepo = outboxRepo;
        this.circuitBreaker = circuitBreaker;
        this.providerMap = new ConcurrentHashMap<>();
        for (NotificationProvider p : providers) {
            providerMap.put(p.getChannel(), p);
        }
    }

    @PostConstruct
    void init() {
        outboxExecutor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadFactoryBuilder().setNameFormat("outbox-sender-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("[OutboxSender] 线程池已初始化: core=2, max=4, queue=200, channels={}", providerMap.keySet());
    }

    @PreDestroy
    void destroy() {
        if (outboxExecutor != null) {
            outboxExecutor.shutdown();
            try { outboxExecutor.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /**
     * 定时消费出件箱（每 5 秒）。
     */
    @Scheduled(fixedDelay = 5000)
    public void consumeOutbox() {
        // 获取所有活跃租户的待发送记录
        // 简化：按 program_code 逐个处理
        List<String> programs = getActivePrograms();
        for (String pc : programs) {
            TenantContext.set(pc);
            try {
                List<NotificationOutbox> pending = outboxRepo.findPending(pc);
                for (NotificationOutbox outbox : pending) {
                    outboxExecutor.submit(() -> processOne(pc, outbox));
                }
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * 处理单条出件箱记录。
     */
    void processOne(String programCode, NotificationOutbox outbox) {
        TenantContext.set(programCode);
        try {
            // 1. 检查熔断器
            if (circuitBreaker.isCircuitOpen(outbox.getChannel())) {
                long remaining = circuitBreaker.getRemainingSeconds(outbox.getChannel());
                log.debug("[Outbox] 通道 [{}] 熔断中 (剩余 {}s)，跳过: id={}", outbox.getChannel(), remaining, outbox.getId());
                return;
            }

            // 2. 获取分布式锁（骨架：Redis SETNX + TTL）
            if (!tryLock(outbox.getId())) {
                log.debug("[Outbox] 分布式锁未获取（已被其他 Worker 处理）: id={}", outbox.getId());
                return;
            }

            try {
                // 3. 更新状态为 SENDING
                updateStatus(outbox.getId(), "SENDING");

                // 4. 路由到对应 Provider 发送
                NotificationProvider provider = providerMap.get(outbox.getChannel());
                if (provider == null) {
                    log.warn("[Outbox] 未找到通道提供者: channel={}", outbox.getChannel());
                    updateStatus(outbox.getId(), "FAILED");
                    return;
                }

                NotificationProvider.SendResult result = provider.send(outbox);

                if (result.success()) {
                    // 发送成功
                    markSent(outbox.getId(), result.providerMessageId());
                    circuitBreaker.recordSuccess(outbox.getChannel());
                    log.info("[Outbox] 发送成功: id={}, channel={}, providerId={}",
                            outbox.getId(), outbox.getChannel(), result.providerMessageId());
                } else {
                    throw new RuntimeException(result.errorMessage());
                }
            } catch (Exception e) {
                // 发送失败 → 标记 RETRY
                handleFailure(outbox, e);
            } finally {
                releaseLock(outbox.getId());
            }
        } finally {
            TenantContext.clear();
        }
    }

    // ==================== 状态更新（事务） ====================

    @Transactional
    void updateStatus(Long id, String status) {
        outboxRepo.findById(id).ifPresent(o -> { o.setStatus(status); outboxRepo.save(o); });
    }

    @Transactional
    void markSent(Long id, String providerMsgId) {
        outboxRepo.findById(id).ifPresent(o -> {
            o.setStatus("SENT");
            o.setSentAt(LocalDateTime.now());
            o.setErrorMessage(providerMsgId);
            outboxRepo.save(o);
        });
    }

    @Transactional
    void handleFailure(NotificationOutbox outbox, Exception e) {
        int retry = (outbox.getRetryCount() != null ? outbox.getRetryCount() : 0) + 1;
        outbox.setRetryCount(retry);
        outbox.setErrorMessage(e.getMessage());

        if (retry >= MAX_RETRIES) {
            // 重试耗尽 → DEAD
            outbox.setStatus("DEAD");
            log.error("[Outbox] 重试耗尽 → 死信: id={}, channel={}, error={}",
                    outbox.getId(), outbox.getChannel(), e.getMessage());
        } else {
            // 指数退避
            long delay = BASE_RETRY_SECONDS * (1L << (retry - 1));
            outbox.setStatus("RETRY");
            outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
            log.warn("[Outbox] 发送失败 → 重试: id={}, channel={}, retry={}/{}, delay={}s",
                    outbox.getId(), outbox.getChannel(), retry, MAX_RETRIES, delay);
        }
        outboxRepo.save(outbox);

        // 记录失败 → 熔断器检查
        boolean triggered = circuitBreaker.recordFailure(outbox.getChannel());
        if (triggered) {
            log.error("[Outbox] 通道 [{}] 触发熔断！连续失败已达阈值", outbox.getChannel());
        }
    }

    // ==================== 分布式锁（骨架） ====================

    private final ConcurrentHashMap<Long, LockEntry> localLocks = new ConcurrentHashMap<>();

    record LockEntry(String holder, LocalDateTime expiresAt) {}

    /** 尝试获取分布式锁。骨架实现：本地 ConcurrentHashMap。
     *  生产环境替换为 Redisson: RLock lock = redisson.getLock(LOCK_PREFIX + id);
     */
    private boolean tryLock(Long id) {
        String holder = Thread.currentThread().getName();
        LockEntry existing = localLocks.putIfAbsent(id,
                new LockEntry(holder, LocalDateTime.now().plusSeconds(LOCK_TIMEOUT_SECONDS)));
        if (existing != null && existing.expiresAt.isAfter(LocalDateTime.now())) {
            return false; // 锁被持有且未过期
        }
        if (existing != null) {
            // 过期锁 → 抢占
            localLocks.put(id, new LockEntry(holder, LocalDateTime.now().plusSeconds(LOCK_TIMEOUT_SECONDS)));
        }
        return true;
    }

    private void releaseLock(Long id) {
        localLocks.remove(id);
    }

    @SuppressWarnings("unchecked")
    private List<String> getActivePrograms() {
        return List.of("PROG001", "DEMO_PROG");
    }
}