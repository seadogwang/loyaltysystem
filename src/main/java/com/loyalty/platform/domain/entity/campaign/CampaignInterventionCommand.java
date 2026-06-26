package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 人工干预命令。
 */
@Entity
@Table(name = "campaign_intervention_command")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignInterventionCommand {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "target_node_id", length = 64)
    private String targetNodeId;

    /** PAUSE / RESUME / CANCEL / SKIP_NODE / UPDATE_CONFIG */
    @Column(name = "command_type", nullable = false, length = 32)
    private String commandType;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state_snapshot", columnDefinition = "jsonb")
    private String previousStateSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_config_snapshot", columnDefinition = "jsonb")
    private String newConfigSnapshot;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}
