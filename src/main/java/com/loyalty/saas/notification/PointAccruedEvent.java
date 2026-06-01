package com.loyalty.saas.notification;

import com.loyalty.saas.common.event.BaseDomainEvent;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
class PointAccruedEvent extends BaseDomainEvent {
    private static final long serialVersionUID = 1L;
    private final Long memberId;
    private final BigDecimal points;
    private final String accountType;

    PointAccruedEvent(String programCode, Long memberId, BigDecimal points, String accountType) {
        super(programCode, "POINTS_ACCRUED");
        this.memberId = memberId;
        this.points = points;
        this.accountType = accountType;
    }
}