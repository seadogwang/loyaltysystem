package com.loyalty.platform.rules.action;

import java.math.BigDecimal;

public class AwardPointsAction extends Action {
    private final String programCode;
    private final String memberId;
    private final String accountType;
    private final BigDecimal points;

    // Accumulative limit metadata (optional, for promo rule excess control)
    private final BigDecimal accumulativeLimit;
    private final String excessStrategy;
    private final BigDecimal downgradeMultiplier;
    private final boolean downgradeContinueCycle;

    public AwardPointsAction(String programCode, String memberId, String accountType,
                              BigDecimal points, String ruleId, String ruleSnapshotId) {
        super(ruleId, ruleSnapshotId);
        this.programCode = programCode; this.memberId = memberId;
        this.accountType = accountType; this.points = points;
        this.accumulativeLimit = null;
        this.excessStrategy = null;
        this.downgradeMultiplier = null;
        this.downgradeContinueCycle = false;
    }

    public AwardPointsAction(String programCode, String memberId, String accountType,
                              BigDecimal points, String ruleId, String ruleSnapshotId,
                              BigDecimal accumulativeLimit, String excessStrategy,
                              BigDecimal downgradeMultiplier, boolean downgradeContinueCycle) {
        super(ruleId, ruleSnapshotId);
        this.programCode = programCode; this.memberId = memberId;
        this.accountType = accountType; this.points = points;
        this.accumulativeLimit = accumulativeLimit;
        this.excessStrategy = excessStrategy;
        this.downgradeMultiplier = downgradeMultiplier;
        this.downgradeContinueCycle = downgradeContinueCycle;
    }

    public String getProgramCode() { return programCode; }
    public String getMemberId() { return memberId; }
    public String getAccountType() { return accountType; }
    public BigDecimal getPoints() { return points; }

    public BigDecimal getAccumulativeLimit() { return accumulativeLimit; }
    public String getExcessStrategy() { return excessStrategy; }
    public BigDecimal getDowngradeMultiplier() { return downgradeMultiplier; }
    public boolean isDowngradeContinueCycle() { return downgradeContinueCycle; }

    @Override public String actionType() { return "AWARD_POINTS"; }
    @Override public String toString() { return "AwardPoints{" + accountType + "=" + points + "}"; }
}