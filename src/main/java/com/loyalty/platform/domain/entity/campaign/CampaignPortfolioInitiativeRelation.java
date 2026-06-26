package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Portfolio ↔ Initiative 关系（预算分配）。
 */
@Entity
@Table(name = "campaign_portfolio_initiative_relation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignPortfolioInitiativeRelation {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "portfolio_id", nullable = false, length = 64)
    private String portfolioId;

    @Column(name = "initiative_id", nullable = false, length = 64)
    private String initiativeId;

    /** 分配预算 */
    @Column(name = "allocated_budget", precision = 18, scale = 4)
    private BigDecimal allocatedBudget;

    /** 预期 ROI */
    @Column(name = "expected_roi", precision = 10, scale = 4)
    private BigDecimal expectedRoi;

    /** 优先级权重 */
    @Column(name = "priority_weight", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal priorityWeight = BigDecimal.ONE;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
