package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 营销举措 — 属于 Goal 的策略分组。
 */
@Entity
@Table(name = "campaign_initiative")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignInitiative {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "goal_id", nullable = false, length = 64)
    private String goalId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** WINBACK / GROWTH / ENGAGEMENT / ACQUISITION */
    @Column(name = "initiative_type", length = 32)
    private String initiativeType;

    /** PLANNED / ACTIVE / PAUSED / COMPLETED / ARCHIVED */
    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "PLANNED";

    /** 数字越小优先级越高 */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 举措的规则配置（人群、条件等） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_config", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> ruleConfig = new LinkedHashMap<>();

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
