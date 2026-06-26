package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外部信号 — 竞品、舆情、政策等 AI 技能采集的外部信号。
 */
@Entity
@Table(name = "external_signal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalSignal {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    @Column(name = "signal_type", length = 64)
    private String signalType;

    /** INFO / WARNING / CRITICAL */
    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "source_skill", length = 64)
    private String sourceSkill;

    @Column(name = "target_entity", length = 255)
    private String targetEntity;

    /** 信号标题 */
    @Column(name = "title", length = 255)
    private String title;

    /** 信号详细描述 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    /** 影响系数，>1 增强机会 */
    @Column(name = "impact_factor", precision = 5, scale = 4)
    private BigDecimal impactFactor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_segments", columnDefinition = "TEXT[]")
    private String affectedSegments;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_consumed")
    @Builder.Default
    private Boolean isConsumed = false;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
