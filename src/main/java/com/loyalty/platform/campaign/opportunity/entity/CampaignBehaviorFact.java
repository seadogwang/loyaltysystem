package com.loyalty.platform.campaign.opportunity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 行为事件宽表 — 通过 CDC/定时任务从 Loyalty 同步。
 */
@Entity
@Table(name = "campaign_behavior_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignBehaviorFact {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "event_type", length = 64)
    private String eventType;                // PAGE_VIEW / LOGIN / CLICK / SHARE / etc.

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", columnDefinition = "jsonb")
    private String eventPayload;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
