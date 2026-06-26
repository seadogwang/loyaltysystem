package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "campaign_variable_binding")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CampaignVariableBinding {

    @Id @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    @Column(name = "asset_id", length = 64)
    private String assetId;

    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "segment_code", length = 64)
    private String segmentCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_bindings", nullable = false, columnDefinition = "JSONB")
    private String variableBindings;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
