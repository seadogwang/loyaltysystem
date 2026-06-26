package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaign_simulation_scenario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignSimulationScenario {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "goal_id", length = 64)
    private String goalId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_config", nullable = false, columnDefinition = "JSONB")
    private String scenarioConfig;

    @Column(name = "baseline_simulation_id", length = 64)
    private String baselineSimulationId;

    @Column(name = "predicted_roi", precision = 10, scale = 4)
    private BigDecimal predictedRoi;

    @Column(name = "predicted_revenue", precision = 18, scale = 4)
    private BigDecimal predictedRevenue;

    @Column(name = "improvement_over_baseline", precision = 10, scale = 4)
    private BigDecimal improvementOverBaseline;

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
