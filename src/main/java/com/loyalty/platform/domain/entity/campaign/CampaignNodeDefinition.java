package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "campaign_node_definition")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CampaignNodeDefinition {

    @Id @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "node_type", nullable = false, unique = true, length = 64)
    private String nodeType;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon", length = 32)
    private String icon;

    @Column(name = "color", length = 16)
    private String color;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_schema", nullable = false, columnDefinition = "JSONB")
    private String configSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", columnDefinition = "JSONB")
    private String inputSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", columnDefinition = "JSONB")
    private String outputSchema;

    @Column(name = "zeebe_worker_type", length = 64)
    private String zeebeWorkerType;

    @Column(name = "version") @Builder.Default
    private Integer version = 1;

    @Column(name = "status", length = 32) @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", updatable = false) @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at") @Builder.Default
    private Instant updatedAt = Instant.now();
}
