package com.loyalty.saas.member;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁适配器 — Ch3.4 完整实现。
 *
 * <p>替换 OneIdEnrollmentService 中的 ConcurrentHashMap 骨架，
 * 提供真正的 Redis 分布式锁，保护跨渠道并发入会。
 */
@Component
public class EnrollmentDistributedLock {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentDistributedLock.class);

    @Autowired(required = false)
    private RedissonClient redissonClient;

    /** 锁前缀 */
    private static final String LOCK_PREFIX = "loyalty:enroll:";
    /** 锁等待时间 */
    private static final long WAIT_SECONDS = 3;
    /** 锁持有时间 */
    private static final long LEASE_SECONDS = 10;

    /**
     * 获取入会分布式锁。
     *
     * @param programCode 租户代码
     * @param lockHash    锁哈希（基于手机号 MD5）
     * @return 锁对象，null 表示获取失败
     */
    public RLock acquire(String programCode, String lockHash) {
        if (redissonClient == null) {
            log.debug("[EnrollmentLock] Redisson 未配置，跳过分布式锁");
            return null;
        }
        String key = LOCK_PREFIX + programCode + ":" + lockHash;
        RLock lock = redissonClient.getLock(key);
        try {
            if (lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS)) {
                return lock;
            }
            log.warn("[EnrollmentLock] 锁获取超时: key={}", key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[EnrollmentLock] 锁获取中断: key={}", key);
        }
        return null;
    }

    /**
     * 释放锁。
     */
    public void release(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            try { lock.unlock(); } catch (Exception e) { log.warn("[EnrollmentLock] 释放锁异常", e); }
        }
    }
}