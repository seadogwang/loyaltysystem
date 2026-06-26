package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 内容素材 — 邮件、短信、推送模板。
 */
@Entity
@Table(name = "campaign_content_asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignContentAsset {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    @Column(name = "asset_name", nullable = false, length = 255)
    private String assetName;

    /** EMAIL_HTML / SMS_TEXT / PUSH_JSON */
    @Column(name = "asset_type", length = 32)
    private String assetType;

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "subject_line", length = 255)
    private String subjectLine;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    /** 变量占位符定义 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_schema", columnDefinition = "jsonb")
    private String variableSchema;

    /** DRAFT / PENDING_APPROVAL / APPROVED / REJECTED */
    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
