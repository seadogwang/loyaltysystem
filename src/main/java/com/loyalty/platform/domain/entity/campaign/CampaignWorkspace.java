package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 营销工作区 — Campaign Planning 顶层容器。
 *
 * <p>Workspace = 一个独立的营销决策上下文（Decision Context Scope）。</p>
 */
@Entity
@Table(name = "campaign_workspace")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignWorkspace {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /** 关联 Loyalty Program */
    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** ACTIVE / ARCHIVED / LOCKED */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    /** 当前激活的目标ID */
    @Column(name = "active_goal_id", length = 64)
    private String activeGoalId;

    /** 工作区级配置（时区、默认预算等） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> config = new LinkedHashMap<>();

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
