package com.loyalty.platform.campaign.opportunity.dto;

import lombok.*;

/**
 * 会员特征向量 — 供 ML 服务使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberFeature {
    private String memberId;
    private Integer recency;
    private Integer frequency;
    private Double monetary;
    private Double avgOrderValue;
    private Integer tierLevel;
    private Integer totalLoginDays;
    private Integer continuousLoginDays;
    private Integer daysSinceRegister;
}
