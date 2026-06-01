package com.loyalty.saas.notification;

import com.loyalty.saas.common.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class TierChangeEvent extends BaseDomainEvent {
    private static final long serialVersionUID = 1L;
    private final Long memberId;
    private final String oldTier;
    private final String newTier;

    public TierChangeEvent(String programCode, Long memberId, String oldTier, String newTier) {
        super(programCode, "TIER_CHANGE");
        this.memberId = memberId;
        this.oldTier = oldTier;
        this.newTier = newTier;
    }
}