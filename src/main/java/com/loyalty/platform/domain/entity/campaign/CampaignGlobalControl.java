package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaign_global_control")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CampaignGlobalControl {

    @Id @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "program_code", nullable = false, unique = true, length = 32)
    private String programCode;

    @Column(name = "throttle_enabled")
    @Builder.Default
    private Boolean throttleEnabled = false;

    @Column(name = "throttle_factor", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal throttleFactor = BigDecimal.valueOf(1.0);

    @Column(name = "throttle_until")
    private Instant throttleUntil;

    @Column(name = "kill_switch_enabled")
    @Builder.Default
    private Boolean killSwitchEnabled = false;

    @Column(name = "kill_switch_activated_at")
    private Instant killSwitchActivatedAt;

    @Column(name = "kill_switch_activated_by", length = 64)
    private String killSwitchActivatedBy;

    @Column(name = "kill_switch_reason", columnDefinition = "TEXT")
    private String killSwitchReason;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
