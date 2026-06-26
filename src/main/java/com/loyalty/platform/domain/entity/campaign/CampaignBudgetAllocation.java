package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 预算分配明细 — 每个 Initiative 在决策中的预算分配。
 *
 * <p>跟踪从 PENDING → EXECUTING → COMPLETED/FAILED 的生命周期，
 * 执行完成后回填 actual_roi。
 */
@Entity
@Table(name = "campaign_budget_allocation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignBudgetAllocation {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "decision_id", nullable = false, length = 64)
    private String decisionId;

    @Column(name = "initiative_id", nullable = false, length = 64)
    private String initiativeId;

    @Column(name = "campaign_id", length = 64)
    private String campaignId;

    @Column(name = "allocated_budget", nullable = false, precision = 18, scale = 4)
    private BigDecimal allocatedBudget;

    @Column(name = "expected_roi", precision = 10, scale = 4)
    private BigDecimal expectedRoi;

    @Column(name = "actual_roi", precision = 10, scale = 4)
    private BigDecimal actualRoi;

    /** PENDING / EXECUTING / COMPLETED / FAILED */
    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
