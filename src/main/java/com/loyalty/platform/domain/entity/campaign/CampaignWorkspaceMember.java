package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 工作区成员（权限模型）。
 */
@Entity
@Table(name = "campaign_workspace_member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignWorkspaceMember {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** OWNER / ADMIN / ANALYST / VIEWER */
    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
