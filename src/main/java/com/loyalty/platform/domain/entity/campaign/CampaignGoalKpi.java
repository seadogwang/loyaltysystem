package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 目标 KPI。
 */
@Entity
@Table(name = "campaign_goal_kpi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignGoalKpi {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "goal_id", nullable = false, length = 64)
    private String goalId;

    /** REVENUE / CONVERSION / RETENTION / ROI */
    @Column(name = "kpi_type", nullable = false, length = 32)
    private String kpiType;

    @Column(name = "target_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal targetValue;

    @Column(name = "current_value", precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "weight", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
