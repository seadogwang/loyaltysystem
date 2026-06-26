package com.loyalty.platform.campaign.event;

import com.loyalty.platform.campaign.opportunity.repository.CampaignMemberDimRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特征存储更新服务 — 从事件更新用户特征。
 *
 * <p>将 Campaign 执行产生的事件（曝光、互动、转化）转换为用户特征增量，
 * 更新 campaign_member_dim 表中的指标。
 */
@Service
public class FeatureStoreService {

    private static final Logger log = LoggerFactory.getLogger(FeatureStoreService.class);

    private final CampaignMemberDimRepository memberDimRepository;
    private final Map<String, UserFeatureCache> featureCache = new ConcurrentHashMap<>();

    public FeatureStoreService(CampaignMemberDimRepository memberDimRepository) {
        this.memberDimRepository = memberDimRepository;
    }

    public void incrementUserExposure(String userId, String channel) {
        log.debug("User exposed: userId={}, channel={}", userId, channel);
        updateCache(userId, "exposureCount", 1);
    }

    public void incrementUserEngagement(String userId, String engagementType) {
        log.debug("User engaged: userId={}, type={}", userId, engagementType);
        updateCache(userId, "engagementCount", 1);
    }

    public void updateUserEngagementScore(String userId, double delta) {
        updateCache(userId, "engagementScore", delta);
    }

    public void recordUserConversion(String userId, String conversionType, BigDecimal amount) {
        log.info("User converted: userId={}, type={}, amount={}", userId, conversionType, amount);
        updateCache(userId, "conversionCount", 1);
    }

    public void updateUserRFM(String userId, BigDecimal amount) {
        log.debug("Updating RFM for userId={}, amount={}", userId, amount);
    }

    public void updateNodeExecutionMetric(String planId, String nodeId, String nodeType, String status, long durationMs) {
        // Already handled by ZeebeTask persistence
    }

    private void updateCache(String userId, String field, double delta) {
        featureCache.computeIfAbsent(userId, k -> new UserFeatureCache());
        featureCache.get(userId).update(field, delta);
    }

    private static class UserFeatureCache {
        int exposureCount, engagementCount, conversionCount;
        double engagementScore = 0.3;
        void update(String field, double delta) {
            switch (field) {
                case "exposureCount": exposureCount += (int) delta; break;
                case "engagementCount": engagementCount += (int) delta; break;
                case "conversionCount": conversionCount += (int) delta; break;
                case "engagementScore": engagementScore = Math.min(Math.max(engagementScore + delta, 0), 1); break;
            }
        }
    }
}
