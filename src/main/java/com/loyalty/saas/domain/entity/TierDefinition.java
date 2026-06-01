package com.loyalty.saas.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity @Table(name = "tier_definition")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TierDefinition {
    @Id @Column(name = "tier_code", nullable = false, length = 50)
    private String tierCode;
    @Column(name = "program_code", nullable = false, length = 100) private String programCode;
    @Column(name = "tier_name", length = 100) private String tierName;
    @Column(name = "sequence", nullable = false) private Integer sequence;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "upgrade_criteria", columnDefinition = "jsonb") private Map<String, Object> upgradeCriteria;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "downgrade_criteria", columnDefinition = "jsonb") private Map<String, Object> downgradeCriteria;
    @Column(name = "created_at", updatable = false) @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") @Builder.Default private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
}