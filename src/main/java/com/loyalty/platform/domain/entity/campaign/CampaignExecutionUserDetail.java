package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaign_execution_user_detail")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CampaignExecutionUserDetail {

    @Id @Column(length = 64) private String id;
    @Column(name = "execution_id", nullable = false, length = 64) private String executionId;
    @Column(name = "plan_id", nullable = false, length = 64) private String planId;
    @Column(name = "node_id", nullable = false, length = 64) private String nodeId;
    @Column(name = "user_id", nullable = false, length = 64) private String userId;
    @Column(name = "status", nullable = false, length = 32) private String status;
    @Column(name = "channel", length = 32) private String channel;
    @Column(name = "message_id", length = 64) private String messageId;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "points_granted", precision = 18, scale = 4) private BigDecimal pointsGranted;
    @Column(name = "coupon_issued", length = 64) private String couponIssued;
    @Column(name = "tier_upgraded", length = 32) private String tierUpgraded;
    @Column(name = "executed_at") private Instant executedAt;
    @Column(name = "created_at", updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
