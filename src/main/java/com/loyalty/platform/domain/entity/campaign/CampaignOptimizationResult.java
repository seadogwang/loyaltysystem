package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaign_optimization_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignOptimizationResult {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "portfolio_id", length = 64)
    private String portfolioId;

    @Column(name = "goal_id", length = 64)
    private String goalId;

    @Column(name = "optimization_type", nullable = false, length = 32)
    private String optimizationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraints", nullable = false, columnDefinition = "JSONB")
    private String constraints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "optimized_allocations", nullable = false, columnDefinition = "JSONB")
    private String optimizedAllocations;

    @Column(name = "expected_roi", precision = 10, scale = 4)
    private BigDecimal expectedRoi;

    @Column(name = "expected_revenue", precision = 18, scale = 4)
    private BigDecimal expectedRevenue;

    @Column(name = "iteration_count")
    private Integer iterationCount;

    @Column(name = "convergence_time_ms")
    private Long convergenceTimeMs;

    @Column(name = "baseline_roi", precision = 10, scale = 4)
    private BigDecimal baselineRoi;

    @Column(name = "improvement_pct", precision = 10, scale = 4)
    private BigDecimal improvementPct;

    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
