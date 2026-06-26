package com.loyalty.platform.campaign.decision.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 决策执行请求 — 完整的决策输入参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRequest {

    /** 工作区 ID */
    private String workspaceId;

    /** 组合 ID */
    private String portfolioId;

    /** 目标 ID */
    private String goalId;

    /** 约束条件 */
    private DecisionConstraints constraints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionConstraints {

        /** 渠道容量限制 */
        private Map<String, Integer> channelCapacity;

        /** 每用户最大触达次数 */
        private Integer maxFrequencyPerUser;

        /** 最低 ROI 阈值 */
        private BigDecimal minROIThreshold;

        /** 黑名单分群 */
        private String[] blacklistSegments;
    }
}
