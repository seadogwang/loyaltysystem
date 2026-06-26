package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "campaign_node_execution_history")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CampaignNodeExecutionHistory {

    @Id @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "node_type", nullable = false, length = 64)
    private String nodeType;

    @Column(name = "execution_id", length = 64)
    private String executionId;

    @Column(name = "job_key")
    private Long jobKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "JSONB")
    private String inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_snapshot", columnDefinition = "JSONB")
    private String outputSnapshot;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count") @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "worker_host", length = 255)
    private String workerHost;

    @Column(name = "created_at", updatable = false) @Builder.Default
    private Instant createdAt = Instant.now();
}
