package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 用户注意力预算 — 按用户+日期+渠道的曝光频控。
 */
@Entity
@Table(name = "user_attention_budget")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(UserAttentionBudgetId.class)
public class UserAttentionBudget {

    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Id
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Id
    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "max_exposure", nullable = false)
    @Builder.Default
    private Integer maxExposure = 10;

    @Column(name = "used_exposure", nullable = false)
    @Builder.Default
    private Integer usedExposure = 0;
}
