package com.loyalty.platform.accounting;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分负分风险告警记录 — 当退款扣减导致负余额时记录告警。
 *
 * <p>告警等级：
 * <ul>
 *   <li>INFO — 单次负分，但未超过透支上限</li>
 *   <li>WARNING — 余额超过透支上限</li>
 *   <li>CRITICAL — 持续负分超过 30 天</li>
 * </ul>
 */
@Entity
@Table(name = "risk_alert")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_code", nullable = false, length = 100)
    private String programCode;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "account_type", nullable = false, length = 64)
    private String accountType;

    @Column(name = "current_balance", precision = 20, scale = 4)
    private BigDecimal currentBalance;

    @Column(name = "overdraft_limit", precision = 20, scale = 4)
    private BigDecimal overdraftLimit;

    /** 告警等级: INFO / WARNING / CRITICAL */
    @Column(name = "level", nullable = false, length = 20)
    private String level;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "acknowledged", nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
