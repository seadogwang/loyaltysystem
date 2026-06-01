package com.loyalty.saas.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity @Table(name = "point_type_definition")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointTypeDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "program_code", nullable = false, length = 100) private String programCode;
    @Column(name = "type_code", nullable = false, length = 50) private String typeCode;
    @Column(name = "type_name", length = 100) private String typeName;
    @Column(name = "is_redeemable") @Builder.Default private Boolean isRedeemable = true;
    @Column(name = "is_tier_calc") @Builder.Default private Boolean isTierCalc = false;
    @Column(name = "is_transferable") @Builder.Default private Boolean isTransferable = false;
    @Column(name = "allow_negative") @Builder.Default private Boolean allowNegative = false;
    @Column(name = "expiry_days") @Builder.Default private Integer expiryDays = 365;
    @Column(name = "config_json", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> configJson;
    @Column(name = "status", length = 20) @Builder.Default private String status = "ACTIVE";
    @Column(name = "created_at", updatable = false) @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
}