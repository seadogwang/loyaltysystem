package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaign_simulation_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignSimulationResult {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "goal_id", length = 64)
    private String goalId;

    @Column(name = "initiative_id", length = 64)
    private String initiativeId;

    @Column(name = "simulation_type", nullable = false, length = 32)
    private String simulationType;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "JSONB")
    private String inputSnapshot;

    @Column(name = "baseline_conversion", precision = 10, scale = 4)
    private BigDecimal baselineConversion;

    @Column(name = "predicted_conversion", precision = 10, scale = 4)
    private BigDecimal predictedConversion;

    @Column(name = "predicted_revenue", precision = 18, scale = 4)
    private BigDecimal predictedRevenue;

    @Column(name = "predicted_roi", precision = 10, scale = 4)
    private BigDecimal predictedRoi;

    @Column(name = "uplift_pct", precision = 10, scale = 4)
    private BigDecimal upliftPct;

    @Column(name = "confidence", precision = 10, scale = 4)
    private BigDecimal confidence;

    @Column(name = "exposure_count")
    private Long exposureCount;

    @Column(name = "behavior_count")
    private Long behaviorCount;

    @Column(name = "conversion_count")
    private Long conversionCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segment_breakdown", columnDefinition = "JSONB")
    private String segmentBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_breakdown", columnDefinition = "JSONB")
    private String channelBreakdown;

    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "executed_by", length = 64)
    private String executedBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
