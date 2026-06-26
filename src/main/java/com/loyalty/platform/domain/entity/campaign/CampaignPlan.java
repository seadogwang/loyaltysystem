package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动计划 — 核心执行计划，包含 DAG 画布和 Zeebe 流程信息。
 */
@Entity
@Table(name = "campaign_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignPlan {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "goal_id", length = 64)
    private String goalId;

    @Column(name = "initiative_id", length = 64)
    private String initiativeId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** DRAFT / GENERATED / APPROVED / REJECTED / EXECUTING / COMPLETED */
    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "total_budget", precision = 18, scale = 4)
    private BigDecimal totalBudget;

    @Column(name = "expected_roi", precision = 10, scale = 4)
    private BigDecimal expectedRoi;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strategy_json", columnDefinition = "jsonb")
    private String strategyJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allocation_json", columnDefinition = "jsonb")
    private String allocationJson;

    /** Canvas DAG */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph_json", columnDefinition = "jsonb")
    private String graphJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forecast_json", columnDefinition = "jsonb")
    private String forecastJson;

    /** Zeebe 流程 ID */
    @Column(name = "zeebe_process_id", length = 100)
    private String zeebeProcessId;

    @Column(name = "zeebe_version")
    private Integer zeebeVersion;

    @Column(name = "zeebe_instance_key")
    private Long zeebeInstanceKey;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
