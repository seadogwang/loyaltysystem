package com.loyalty.platform.campaign.opportunity.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 机会DTO — 对外输出的机会对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Opportunity {
    private String id;
    private String memberId;
    private String segmentCode;
    private String opportunityType;
    private BigDecimal score;
    private BigDecimal churnProbability;
    private BigDecimal upliftScore;
    private BigDecimal conversionProbability;
    private BigDecimal rfmScore;
    private BigDecimal externalInfluence;
    private List<String> externalSignalIds;
    private String recommendedAction;
    private String recommendedChannel;
    private BigDecimal confidence;
    private String status;
    private String source;
    private Instant detectedAt;
    private Instant expiresAt;
}
