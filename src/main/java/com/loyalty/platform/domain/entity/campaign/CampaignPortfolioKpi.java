package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Portfolio KPI。
 */
@Entity
@Table(name = "campaign_portfolio_kpi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignPortfolioKpi {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "portfolio_id", nullable = false, length = 64)
    private String portfolioId;

    @Column(name = "kpi_type", nullable = false, length = 32)
    private String kpiType;

    @Column(name = "target_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal targetValue;

    /** 预测值 */
    @Column(name = "predicted_value", precision = 18, scale = 4)
    private BigDecimal predictedValue;

    @Column(name = "weight", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
