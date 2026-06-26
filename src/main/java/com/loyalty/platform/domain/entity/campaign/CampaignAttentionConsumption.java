package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 注意力预算消费审计 — 记录每次曝光消耗的完整流水。
 *
 * <p>与 {@link UserAttentionBudget} 配合使用：
 * UserAttentionBudget 维护配额状态，本表记录消费明细用于审计追溯。
 */
@Entity
@Table(name = "campaign_attention_consumption")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignAttentionConsumption {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "campaign_id", length = 64)
    private String campaignId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "consumed_at", updatable = false)
    @Builder.Default
    private Instant consumedAt = Instant.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
}
