package com.loyalty.platform.campaign.opportunity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单事实宽表 — 通过 CDC/定时任务从 Loyalty 同步。
 */
@Entity
@Table(name = "campaign_order_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignOrderFact {

    @Id
    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "program_code", nullable = false, length = 32)
    private String programCode;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "order_date")
    private LocalDateTime orderDate;

    @Column(name = "order_amount", precision = 18, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "order_status", length = 20)
    private String orderStatus;              // COMPLETED / REFUNDED / CANCELLED

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "product_category", length = 64)
    private String productCategory;

    @Column(name = "store_code", length = 32)
    private String storeCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ext_attributes", columnDefinition = "jsonb")
    private Map<String, Object> extAttributes;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
