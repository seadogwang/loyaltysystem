package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 举措 KPI。
 */
@Entity
@Table(name = "campaign_initiative_kpi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignInitiativeKpi {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "initiative_id", nullable = false, length = 64)
    private String initiativeId;

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
