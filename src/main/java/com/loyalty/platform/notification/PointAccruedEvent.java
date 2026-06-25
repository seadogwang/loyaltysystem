package com.loyalty.platform.notification;

import com.loyalty.platform.common.event.BaseDomainEvent;
import lombok.Getter;
import java.math.BigDecimal;

/**
 * 积分发放事件 — 触发等级评估联动。
 *
 * <p>当积分类型为 TIER_POINTS 时，TierEvaluationService 监听此事件，
 * 自动评估会员是否需要升级。
 */
@Getter
public class PointAccruedEvent extends BaseDomainEvent {
    private static final long serialVersionUID = 1L;
    private final Long memberId;
    private final BigDecimal points;
    private final String accountType;
    private final String eventId;

    public PointAccruedEvent(String programCode, Long memberId, BigDecimal points, String accountType, String eventId) {
        super(programCode, "POINTS_ACCRUED");
        this.memberId = memberId;
        this.points = points;
        this.accountType = accountType;
        this.eventId = eventId;
    }
}