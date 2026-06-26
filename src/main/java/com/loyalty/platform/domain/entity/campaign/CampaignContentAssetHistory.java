package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "campaign_content_asset_history")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CampaignContentAssetHistory {

    @Id @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "asset_id", nullable = false, length = 64)
    private String assetId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "asset_name", length = 255)
    private String assetName;

    @Column(name = "subject_line", length = 255)
    private String subjectLine;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_json", columnDefinition = "JSONB")
    private String bodyJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_schema", columnDefinition = "JSONB")
    private String variableSchema;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "changed_by", length = 64)
    private String changedBy;

    @Column(name = "change_comment", columnDefinition = "TEXT")
    private String changeComment;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
