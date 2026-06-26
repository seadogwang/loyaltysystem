package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Initiative ↔ Campaign Plan 关系。
 */
@Entity
@Table(name = "campaign_initiative_plan_relation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignInitiativePlanRelation {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "initiative_id", nullable = false, length = 64)
    private String initiativeId;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    /** 该 Plan 在 Initiative 中的权重 */
    @Column(name = "weight", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    /** PRIMARY / SUPPORTING / EXPERIMENTAL */
    @Column(name = "role", length = 32)
    @Builder.Default
    private String role = "PRIMARY";

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
