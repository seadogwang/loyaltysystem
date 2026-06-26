package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaign_model_drift")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignModelDrift {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    @Column(name = "drift_detected")
    @Builder.Default
    private Boolean driftDetected = false;

    @Column(name = "drift_score", precision = 10, scale = 4)
    private BigDecimal driftScore;

    @Column(name = "threshold", precision = 10, scale = 4)
    private BigDecimal threshold;

    @Column(name = "sample_size")
    private Integer sampleSize;

    @Column(name = "mean_predicted", precision = 10, scale = 4)
    private BigDecimal meanPredicted;

    @Column(name = "mean_actual", precision = 10, scale = 4)
    private BigDecimal meanActual;

    @Column(name = "mae", precision = 10, scale = 4)
    private BigDecimal mae;

    @Column(name = "rmse", precision = 10, scale = 4)
    private BigDecimal rmse;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "affected_features", columnDefinition = "TEXT[]")
    private String[] affectedFeatures;

    @Column(name = "detected_at")
    @Builder.Default
    private Instant detectedAt = Instant.now();

    @Column(name = "retrained_at")
    private Instant retrainedAt;

    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "PENDING";
}
