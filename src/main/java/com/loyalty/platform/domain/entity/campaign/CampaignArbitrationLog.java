package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 仲裁日志 — 记录冲突仲裁的详细过程。
 *
 * <p>每次冲突仲裁生成一条日志，包含冲突类型、候选列表、
 * 最终选择及理由、优先级分数等完整仲裁依据。
 */
@Entity
@Table(name = "campaign_arbitration_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignArbitrationLog {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "decision_id", nullable = false, length = 64)
    private String decisionId;

    /** USER / BUDGET / CHANNEL / TIME */
    @Column(name = "conflict_type", nullable = false, length = 32)
    private String conflictType;

    /** 冲突的候选ID列表 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "candidate_ids", nullable = false, columnDefinition = "TEXT[]")
    private String[] candidateIds;

    /** 被选中的候选ID */
    @Column(name = "resolution", nullable = false, length = 64)
    private String resolution;

    @Column(name = "resolution_reason", columnDefinition = "TEXT")
    private String resolutionReason;

    /** 每个候选的优先级分数 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "priority_scores", columnDefinition = "JSONB")
    private String priorityScores;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
