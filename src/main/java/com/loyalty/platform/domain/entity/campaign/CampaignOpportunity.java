package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 会员级机会记录 — 机会发现引擎输出。
 *
 * <p>每次机会发现任务产出的会员级机会，包含 ML 评分、RFM 分、外部信号影响、
 * 推荐动作等完整信息。
 */
@Entity
@Table(name = "campaign_opportunity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignOpportunity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "goal_id", length = 64)
    private String goalId;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "segment_code", length = 64)
    private String segmentCode;

    /** CHURN_RISK / UPSELL / WINBACK / CROSS_SELL / ENGAGEMENT */
    @Column(name = "opportunity_type", nullable = false, length = 32)
    private String opportunityType;

    /** 综合机会评分（0~1） */
    @Column(name = "score", nullable = false, precision = 10, scale = 4)
    private BigDecimal score;

    @Column(name = "churn_probability", precision = 10, scale = 4)
    private BigDecimal churnProbability;

    @Column(name = "uplift_score", precision = 10, scale = 4)
    private BigDecimal upliftScore;

    @Column(name = "conversion_probability", precision = 10, scale = 4)
    private BigDecimal conversionProbability;

    @Column(name = "rfm_score", precision = 10, scale = 4)
    private BigDecimal rfmScore;

    /** 外部信号影响因子 */
    @Column(name = "external_influence", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal externalInfluence = BigDecimal.ONE;

    /** 影响该机会的外部信号ID列表 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "external_signal_ids", columnDefinition = "TEXT[]")
    private String[] externalSignalIds;

    @Column(name = "recommended_action", length = 255)
    private String recommendedAction;

    @Column(name = "recommended_channel", length = 32)
    private String recommendedChannel;

    /** 置信度 */
    @Column(name = "confidence", precision = 10, scale = 4)
    private BigDecimal confidence;

    /** ACTIVE / CONSUMED / EXPIRED / SUPPRESSED */
    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    /** INTERNAL / EXTERNAL / ML / HYBRID */
    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "detected_at")
    @Builder.Default
    private Instant detectedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
