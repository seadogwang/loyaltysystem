package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 营销目标 — 每个 Workspace 同时仅有一个 ACTIVE Goal。
 */
@Entity
@Table(name = "campaign_goal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignGoal {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** REVENUE / RETENTION / ACQUISITION / ENGAGEMENT */
    @Column(name = "goal_type", nullable = false, length = 32)
    private String goalType;

    /** DRAFT / ACTIVE / PAUSED / COMPLETED / ARCHIVED */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "DRAFT";

    /** 关联 Loyalty 指标 */
    @Column(name = "target_metric", length = 64)
    private String targetMetric;

    @Column(name = "target_value", precision = 18, scale = 4)
    private BigDecimal targetValue;

    @Column(name = "current_value", precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 行业类型: RETAIL / SAAS / FINANCE / EDUCATION / AUTO / ECOMMERCE */
    @Column(name = "industry_type", length = 64)
    private String industryType;

    /** 关联策略蓝图ID */
    @Column(name = "blueprint_id", length = 64)
    private String blueprintId;

    /** 策略工作流状态 */
    @Column(name = "workflow_status", length = 32)
    @Builder.Default
    private String workflowStatus = "GOAL_DRAFT";

    /** 平均客单价 */
    @Column(name = "avg_order_value", precision = 18, scale = 4)
    private BigDecimal avgOrderValue;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
