package com.loyalty.platform.campaign.opportunity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会员汇总宽表 — 通过 CDC/定时任务从 Loyalty 同步。
 * 包含 RFM、等级、分群等分析所需字段。
 */
@Entity
@Table(name = "campaign_member_dim")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignMemberDim {

    @Id
    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    // ---- 基本信息 ----
    @Column(name = "status", length = 20)
    private String status;                   // ACTIVE / INACTIVE / BLACKLISTED

    @Column(name = "tier_code", length = 32)
    private String tierCode;

    @Column(name = "tier_name", length = 100)
    private String tierName;

    @Column(name = "segment_code", length = 32)
    private String segmentCode;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    // ---- RFM 字段 ----
    /** 最近购买日期（Recency） */
    @Column(name = "last_order_date")
    private LocalDate lastOrderDate;

    /** 最近购买距今天数 */
    @Column(name = "recency_days")
    private Integer recencyDays;

    /** 累计购买次数（Frequency） */
    @Column(name = "total_order_count")
    private Integer totalOrderCount;

    /** 累计购买金额（Monetary） */
    @Column(name = "total_order_amount", precision = 18, scale = 2)
    private BigDecimal totalOrderAmount;

    /** 平均客单价 */
    @Column(name = "avg_order_value", precision = 18, scale = 2)
    private BigDecimal avgOrderValue;

    // ---- 积分 ----
    @Column(name = "total_points_earned", precision = 18, scale = 2)
    private BigDecimal totalPointsEarned;

    @Column(name = "total_points_redeemed", precision = 18, scale = 2)
    private BigDecimal totalPointsRedeemed;

    @Column(name = "available_points", precision = 18, scale = 2)
    private BigDecimal availablePoints;

    // ---- ML 预测分（定时更新） ----
    @Column(name = "churn_probability", precision = 6, scale = 5)
    private BigDecimal churnProbability;

    @Column(name = "uplift_score", precision = 6, scale = 5)
    private BigDecimal upliftScore;

    @Column(name = "conversion_probability", precision = 6, scale = 5)
    private BigDecimal conversionProbability;

    // ---- RFM 评分 ----
    @Column(name = "rfm_recency_score")
    private Integer rfmRecencyScore;         // 1-5

    @Column(name = "rfm_frequency_score")
    private Integer rfmFrequencyScore;       // 1-5

    @Column(name = "rfm_monetary_score")
    private Integer rfmMonetaryScore;        // 1-5

    @Column(name = "rfm_total_score", precision = 8, scale = 2)
    private BigDecimal rfmTotalScore;

    // ---- 扩展属性 ----
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ext_attributes", columnDefinition = "jsonb")
    private Map<String, Object> extAttributes;

    // ---- 同步控制 ----
    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
