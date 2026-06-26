package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaign_feedback_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignFeedbackMetrics {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    @Column(name = "initiative_id", length = 64)
    private String initiativeId;

    @Column(name = "goal_id", length = 64)
    private String goalId;

    @Column(name = "predicted_roi", precision = 10, scale = 4)
    private BigDecimal predictedRoi;

    @Column(name = "predicted_conversion", precision = 10, scale = 4)
    private BigDecimal predictedConversion;

    @Column(name = "predicted_revenue", precision = 18, scale = 4)
    private BigDecimal predictedRevenue;

    @Column(name = "actual_roi", precision = 10, scale = 4)
    private BigDecimal actualRoi;

    @Column(name = "actual_conversion", precision = 10, scale = 4)
    private BigDecimal actualConversion;

    @Column(name = "actual_revenue", precision = 18, scale = 4)
    private BigDecimal actualRevenue;

    @Column(name = "actual_cost", precision = 18, scale = 4)
    private BigDecimal actualCost;

    @Column(name = "roi_deviation", precision = 10, scale = 4)
    private BigDecimal roiDeviation;

    @Column(name = "conversion_deviation", precision = 10, scale = 4)
    private BigDecimal conversionDeviation;

    @Column(name = "total_exposures")
    private Long totalExposures;

    @Column(name = "total_engagements")
    private Long totalEngagements;

    @Column(name = "total_conversions")
    private Long totalConversions;

    @Column(name = "unique_users")
    private Long uniqueUsers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_breakdown", columnDefinition = "JSONB")
    private String channelBreakdown;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "calculated_at")
    @Builder.Default
    private Instant calculatedAt = Instant.now();

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
