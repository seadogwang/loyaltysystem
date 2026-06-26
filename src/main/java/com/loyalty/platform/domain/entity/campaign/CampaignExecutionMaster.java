package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "campaign_execution_master")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CampaignExecutionMaster {

    @Id @Column(length = 64) private String id;
    @Column(name = "plan_id", nullable = false, length = 64) private String planId;
    @Column(name = "workspace_id", nullable = false, length = 64) private String workspaceId;
    @Column(name = "goal_id", length = 64) private String goalId;
    @Column(name = "execution_key", unique = true) private Long executionKey;
    @Column(name = "zeebe_process_id", length = 100) private String zeebeProcessId;
    @Column(name = "zeebe_version") private Integer zeebeVersion;
    @Column(name = "status", nullable = false, length = 32) private String status;
    @Column(name = "total_nodes") @Builder.Default private Integer totalNodes = 0;
    @Column(name = "completed_nodes") @Builder.Default private Integer completedNodes = 0;
    @Column(name = "failed_nodes") @Builder.Default private Integer failedNodes = 0;
    @Column(name = "total_users") @Builder.Default private Integer totalUsers = 0;
    @Column(name = "processed_users") @Builder.Default private Integer processedUsers = 0;
    @Column(name = "start_time") private Instant startTime;
    @Column(name = "end_time") private Instant endTime;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "triggered_by", length = 64) private String triggeredBy;
    @Column(name = "trigger_source", length = 32) private String triggerSource;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "created_at", updatable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(name = "updated_at") @Builder.Default private Instant updatedAt = Instant.now();
}
