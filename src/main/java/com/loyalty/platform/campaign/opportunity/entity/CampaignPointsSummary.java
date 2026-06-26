package com.loyalty.platform.campaign.opportunity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分汇总宽表 — 通过 CDC/定时任务从 Loyalty 同步。
 */
@Entity
@Table(name = "campaign_points_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignPointsSummary {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "point_type_code", length = 32)
    private String pointTypeCode;

    @Column(name = "point_type_name", length = 100)
    private String pointTypeName;

    @Column(name = "total_earned", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalEarned = BigDecimal.ZERO;

    @Column(name = "total_redeemed", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalRedeemed = BigDecimal.ZERO;

    @Column(name = "available_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "expiring_soon", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal expiringSoon = BigDecimal.ZERO;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
