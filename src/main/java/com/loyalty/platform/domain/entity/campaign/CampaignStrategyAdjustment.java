package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaign_strategy_adjustment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignStrategyAdjustment {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "adjustment_type", nullable = false, length = 32)
    private String adjustmentType;

    @Column(name = "trigger_event", length = 64)
    private String triggerEvent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_config", columnDefinition = "JSONB")
    private String beforeConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_config", columnDefinition = "JSONB")
    private String afterConfig;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "expected_improvement", precision = 10, scale = 4)
    private BigDecimal expectedImprovement;

    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "applied_at")
    private Instant appliedAt;
}
