package com.loyalty.platform.campaign.decision.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 模拟预测结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationResult {
    private String candidateId;
    private double exposureRate;          // 曝光概率
    private double behaviorRate;          // 行为概率
    private double conversionRate;        // 转化概率
    private BigDecimal expectedRevenue;   // 预期收入
    private BigDecimal expectedROI;       // 预期 ROI
    private long estimatedReach;          // 预估触达人数
    private long estimatedConversions;    // 预估转化数

    // 三层模型明细
    private Map<String, Object> modelDetails;
}
