package com.loyalty.platform.campaign.execution.service;

import com.loyalty.platform.domain.entity.campaign.ExecutionDedup;
import com.loyalty.platform.domain.repository.campaign.ExecutionDedupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 幂等管理器 — 保证消息/积分/优惠券等不重复发送/发放。
 *
 * <p>使用 execution_dedup 表实现 INSERT IGNORE 语义：
 * dedup_key = planId:nodeId:userId:channel
 */
@Component
public class IdempotencyManager {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyManager.class);

    private final ExecutionDedupRepository dedupRepository;

    public IdempotencyManager(ExecutionDedupRepository dedupRepository) {
        this.dedupRepository = dedupRepository;
    }

    /**
     * 检查并标记幂等 Key。
     *
     * @return true: 首次执行（已标记）; false: 重复执行（已存在）
     */
    @Transactional
    public boolean checkAndMark(String planId, String nodeId, String userId, String channel) {
        String key = buildKey(planId, nodeId, userId, channel);

        if (dedupRepository.existsByDedupKey(key)) {
            log.debug("Duplicate execution blocked: key={}", key);
            return false;
        }

        ExecutionDedup dedup = ExecutionDedup.builder()
                .id(UUID.randomUUID().toString())
                .dedupKey(key)
                .planId(planId)
                .nodeId(nodeId)
                .userId(userId)
                .channel(channel)
                .createdAt(Instant.now())
                .ttl(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        dedupRepository.save(dedup);
        return true;
    }

    /**
     * 仅检查（不标记）。
     */
    @Transactional(readOnly = true)
    public boolean isDuplicate(String planId, String nodeId, String userId, String channel) {
        return dedupRepository.existsByDedupKey(buildKey(planId, nodeId, userId, channel));
    }

    /**
     * 清理过期的去重记录。
     */
    @Transactional
    public int cleanExpired() {
        int deleted = dedupRepository.deleteExpired(Instant.now());
        if (deleted > 0) log.info("Cleaned {} expired dedup records", deleted);
        return deleted;
    }

    private String buildKey(String planId, String nodeId, String userId, String channel) {
        return planId + ":" + nodeId + ":" + userId + ":" + (channel != null ? channel : "N/A");
    }
}
