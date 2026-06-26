package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "campaign_compile_log")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CampaignCompileLog {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    @Column(name = "engine_type", nullable = false, length = 32)
    private String engineType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "node_count")
    private Integer nodeCount;

    @Column(name = "edge_count")
    private Integer edgeCount;

    @Column(name = "bpmn_size_bytes")
    private Integer bpmnSizeBytes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", columnDefinition = "JSONB")
    private String validationErrors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_warnings", columnDefinition = "JSONB")
    private String validationWarnings;

    @Column(name = "compile_duration_ms")
    private Long compileDurationMs;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
