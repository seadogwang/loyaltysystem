package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 决策结果 — 决策引擎每次运行的完整输出。
 *
 * <p>包含输入快照、分配结果、仲裁结果、优先级排序结果，
 * 支持 DRAFT → APPLIED → SUPERSEDED/ROLLED_BACK 生命周期。
 */
@Entity
@Table(name = "campaign_decision_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignDecisionResult {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "portfolio_id", length = 64)
    private String portfolioId;

    @Column(name = "goal_id", length = 64)
    private String goalId;

    /** BUDGET_ALLOCATION / ARBITRATION / FULL_DECISION */
    @Column(name = "decision_type", nullable = false, length = 32)
    private String decisionType;

    /** DRAFT / APPLIED / REJECTED / SUPERSEDED / ROLLED_BACK */
    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "DRAFT";

    /** 决策输入完整快照 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "JSONB")
    private String inputSnapshot;

    /** 预算分配结果 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allocation_result", nullable = false, columnDefinition = "JSONB")
    private String allocationResult;

    /** 仲裁结果 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arbitration_result", columnDefinition = "JSONB")
    private String arbitrationResult;

    /** 优先级排序结果 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prioritization_result", columnDefinition = "JSONB")
    private String prioritizationResult;

    @Column(name = "total_budget", precision = 18, scale = 4)
    private BigDecimal totalBudget;

    @Column(name = "total_allocated", precision = 18, scale = 4)
    private BigDecimal totalAllocated;

    @Column(name = "expected_total_roi", precision = 10, scale = 4)
    private BigDecimal expectedTotalRoi;

    @Column(name = "conflicts_resolved")
    @Builder.Default
    private Integer conflictsResolved = 0;

    @Column(name = "rejected_candidates")
    @Builder.Default
    private Integer rejectedCandidates = 0;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "applied_at")
    private Instant appliedAt;
}
