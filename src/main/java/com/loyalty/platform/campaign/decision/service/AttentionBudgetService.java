package com.loyalty.platform.campaign.decision.service;

import com.loyalty.platform.domain.entity.campaign.CampaignAttentionConsumption;
import com.loyalty.platform.domain.entity.campaign.UserAttentionBudget;
import com.loyalty.platform.domain.repository.campaign.CampaignAttentionConsumptionRepository;
import com.loyalty.platform.domain.repository.campaign.UserAttentionBudgetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * 注意力预算服务 — 按用户+日期+渠道的曝光频控 + 消费审计。
 *
 * <p>与 {@link UserAttentionBudget} 配合：
 * <ul>
 *   <li>UserAttentionBudget 维护配额状态（user_attention_budget 表）</li>
 *   <li>CampaignAttentionConsumption 记录消费流水（campaign_attention_consumption 表）</li>
 * </ul>
 */
@Service
@Transactional
public class AttentionBudgetService {

    private static final Logger log = LoggerFactory.getLogger(AttentionBudgetService.class);

    /** 默认每日最大曝光次数 */
    private static final int DEFAULT_MAX_EXPOSURE = 10;

    private final UserAttentionBudgetRepository budgetRepository;
    private final CampaignAttentionConsumptionRepository consumptionRepository;

    public AttentionBudgetService(UserAttentionBudgetRepository budgetRepository,
                                   CampaignAttentionConsumptionRepository consumptionRepository) {
        this.budgetRepository = budgetRepository;
        this.consumptionRepository = consumptionRepository;
    }

    // ========================================================================
    // 配额查询
    // ========================================================================

    /**
     * 检查用户在当前日期+渠道是否还有曝光配额。
     */
    @Transactional(readOnly = true)
    public boolean hasExposureQuota(String userId, String channel) {
        return hasExposureQuota(userId, channel, LocalDate.now());
    }

    /**
     * 检查用户在指定日期+渠道是否还有曝光配额。
     */
    @Transactional(readOnly = true)
    public boolean hasExposureQuota(String userId, String channel, LocalDate date) {
        UserAttentionBudget budget = budgetRepository.findByUserIdAndDateAndChannel(userId, date, channel);
        if (budget == null) {
            return true;
        }
        return budget.getUsedExposure() < budget.getMaxExposure();
    }

    /**
     * 获取用户的剩余曝光配额。
     */
    @Transactional(readOnly = true)
    public int getRemainingQuota(String userId, String channel) {
        return getRemainingQuota(userId, channel, LocalDate.now());
    }

    /**
     * 获取用户指定日期的剩余曝光配额。
     */
    @Transactional(readOnly = true)
    public int getRemainingQuota(String userId, String channel, LocalDate date) {
        UserAttentionBudget budget = budgetRepository.findByUserIdAndDateAndChannel(userId, date, channel);
        if (budget == null) {
            return getDefaultMaxExposure(channel);
        }
        return Math.max(0, budget.getMaxExposure() - budget.getUsedExposure());
    }

    // ========================================================================
    // 曝光消耗（含审计）
    // ========================================================================

    /**
     * 记录一次曝光消耗（含审计日志）。
     *
     * @param userId     用户 ID
     * @param campaignId 关联的 Campaign ID
     * @param channel    触达渠道
     */
    public void consume(String userId, String campaignId, String channel) {
        LocalDate today = LocalDate.now();

        // 更新配额
        UserAttentionBudget budget = budgetRepository.findByUserIdAndDateAndChannel(userId, today, channel);
        if (budget == null) {
            budget = UserAttentionBudget.builder()
                    .userId(userId)
                    .date(today)
                    .channel(channel)
                    .maxExposure(getDefaultMaxExposure(channel))
                    .usedExposure(1)
                    .build();
        } else {
            // 乐观检查：防止超发
            if (budget.getUsedExposure() >= budget.getMaxExposure()) {
                log.warn("Attention budget exhausted: user={}, channel={}, used={}/{}",
                        userId, channel, budget.getUsedExposure(), budget.getMaxExposure());
                return;
            }
            budget.setUsedExposure(budget.getUsedExposure() + 1);
        }
        budgetRepository.save(budget);

        // 记录消费审计
        CampaignAttentionConsumption consumption = CampaignAttentionConsumption.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .campaignId(campaignId)
                .channel(channel)
                .consumedAt(Instant.now())
                .build();
        consumptionRepository.save(consumption);

        log.debug("Attention consumed: user={}, channel={}, campaign={}", userId, channel, campaignId);
    }

    /**
     * 记录一次曝光消耗（无 Campaign 关联 — 兼容旧版）。
     */
    public void recordExposure(String userId, String channel) {
        consume(userId, null, channel);
    }

    /**
     * 记录指定日期的曝光消耗。
     */
    public void recordExposure(String userId, String channel, LocalDate date) {
        UserAttentionBudget budget = budgetRepository.findByUserIdAndDateAndChannel(userId, date, channel);
        if (budget == null) {
            budget = UserAttentionBudget.builder()
                    .userId(userId)
                    .date(date)
                    .channel(channel)
                    .maxExposure(DEFAULT_MAX_EXPOSURE)
                    .usedExposure(1)
                    .build();
        } else {
            budget.setUsedExposure(budget.getUsedExposure() + 1);
        }
        budgetRepository.save(budget);
    }

    /**
     * 批量记录曝光消耗。
     */
    public void recordExposures(String[] userIds, String channel) {
        for (String userId : userIds) {
            recordExposure(userId, channel);
        }
    }

    // ========================================================================
    // 批量检查
    // ========================================================================

    /**
     * 批量检查用户是否可发送（用于决策引擎）。
     *
     * @return Map<userId, canSend>
     */
    @Transactional(readOnly = true)
    public Map<String, Boolean> batchCanSend(List<String> userIds, String channel) {
        Map<String, Boolean> result = new HashMap<>();
        for (String userId : userIds) {
            result.put(userId, hasExposureQuota(userId, channel));
        }
        return result;
    }

    /**
     * 过滤出尚有曝光配额的用户。
     *
     * @return 有配额的用户 ID 列表
     */
    @Transactional(readOnly = true)
    public List<String> filterEligibleUsers(List<String> userIds, String channel) {
        LocalDate today = LocalDate.now();
        return userIds.stream()
                .filter(uid -> hasExposureQuota(uid, channel, today))
                .collect(java.util.stream.Collectors.toList());
    }

    // ========================================================================
    // 配额管理
    // ========================================================================

    /**
     * 设置用户的每日最大曝光次数。
     */
    public void setMaxExposure(String userId, String channel, int maxExposure) {
        LocalDate now = LocalDate.now();
        UserAttentionBudget budget = budgetRepository.findByUserIdAndDateAndChannel(userId, now, channel);
        if (budget == null) {
            budget = UserAttentionBudget.builder()
                    .userId(userId)
                    .date(now)
                    .channel(channel)
                    .maxExposure(maxExposure)
                    .usedExposure(0)
                    .build();
        } else {
            budget.setMaxExposure(maxExposure);
        }
        budgetRepository.save(budget);
        log.info("Attention budget set: user={}, channel={}, max={}", userId, channel, maxExposure);
    }

    /**
     * 释放决策相关的注意力预算（用于回滚）。
     *
     * <p>将 Initiative 下所有用户的当日配额 +1。
     */
    public void releaseForDecision(String initiativeId) {
        // 回滚时将已消耗的配额释放（简化实现：此处仅记录日志）
        // 生产环境需要查询 consumption 表，找到该 Initiative 下所有 consumption 记录，
        // 对应减少 user_attention_budget 的 used_exposure
        log.info("Attention budget release requested for initiative: {}", initiativeId);
    }

    // ========================================================================
    // 内部辅助
    // ========================================================================

    private int getDefaultMaxExposure(String channel) {
        if (channel == null) return DEFAULT_MAX_EXPOSURE;
        switch (channel.toUpperCase()) {
            case "EMAIL": return 3;
            case "SMS": return 2;
            case "PUSH": return 5;
            default: return DEFAULT_MAX_EXPOSURE;
        }
    }
}
