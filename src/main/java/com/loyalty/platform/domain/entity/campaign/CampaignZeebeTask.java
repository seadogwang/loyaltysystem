package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "campaign_zeebe_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignZeebeTask {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "instance_id", nullable = false, length = 64)
    private String instanceId;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    @Column(name = "job_key", nullable = false)
    private Long jobKey;

    @Column(name = "task_type", nullable = false, length = 64)
    private String taskType;

    @Column(name = "task_name", length = 255)
    private String taskName;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_variables", columnDefinition = "JSONB")
    private Map<String, Object> inputVariables;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_variables", columnDefinition = "JSONB")
    private Map<String, Object> outputVariables;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(name = "worker_id", length = 64)
    private String workerId;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // ===== DLQ 死信队列扩展 =====
    @Column(name = "is_dlq")
    @Builder.Default
    private boolean isDlq = false;

    @Column(name = "dlq_reason", columnDefinition = "TEXT")
    private String dlqReason;

    @Column(name = "dlq_archived")
    @Builder.Default
    private boolean dlqArchived = false;

    @Column(name = "dlq_archived_at")
    private Instant dlqArchivedAt;

    @Column(name = "replayed_count")
    @Builder.Default
    private Integer replayedCount = 0;

    @Column(name = "original_job_key")
    private Long originalJobKey;

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
