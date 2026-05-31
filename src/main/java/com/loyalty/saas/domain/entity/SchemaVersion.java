package com.loyalty.saas.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "schema_version")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchemaVersion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_code", nullable = false, length = 100)
    private String programCode;

    /** Schema 类型: MEMBER, TRANSACTION, CUSTOM_ENTITY 等 */
    @Column(name = "schema_type", nullable = false, length = 50)
    private String schemaType;

    @Column(name = "schema_code", nullable = false, length = 100)
    private String schemaCode;

    @Column(name = "version", nullable = false)
    private Integer version;

    /** DRAFT / PUBLISHED / ARCHIVED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    /** JSON Schema 定义 (JSONB) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> schemaJson;

    /** 影响报告 (JSONB) — 废弃字段被哪些规则引用 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "impact_report", columnDefinition = "jsonb")
    private Map<String, Object> impactReport;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 获取版本标签: schema_code:v{version} */
    public String getVersionTag() {
        return schemaCode + ":v" + version;
    }
}