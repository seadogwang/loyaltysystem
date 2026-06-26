package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 审批记录。
 */
@Entity
@Table(name = "campaign_approval_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignApprovalRecord {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "asset_id", length = 64)
    private String assetId;

    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "requester_id", nullable = false, length = 64)
    private String requesterId;

    @Column(name = "approver_id", length = 64)
    private String approverId;

    /** SUBMITTED / APPROVED / REJECTED / REVOKED */
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_before", columnDefinition = "jsonb")
    private String snapshotBefore;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
