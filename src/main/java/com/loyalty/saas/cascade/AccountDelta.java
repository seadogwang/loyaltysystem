package com.loyalty.saas.cascade;

import lombok.*;

import java.math.BigDecimal;

/**
 * 级联重算差额结果 (AccountDelta)。
 *
 * <p>对比"影子账户的最终推演结果"与"当前真实账户的状态"后得出的补偿指令。
 * 仅包含差异数据，不包含完整的账户快照。
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountDelta {

    /** 需要扣除的积分（正数表示多发了需要追回） */
    @Builder.Default
    private BigDecimal pointsToDeduct = BigDecimal.ZERO;

    /** 需要补发的积分（正数表示少发了需要补偿） */
    @Builder.Default
    private BigDecimal pointsToAdd = BigDecimal.ZERO;

    /** 修正后的等级（null 表示无需调整） */
    private String newTier;

    /** 修正前的等级 */
    private String oldTier;

    /** 影响的事件数量 */
    @Builder.Default
    private int affectedEventCount = 0;

    public boolean hasPointChanges() {
        return pointsToDeduct.compareTo(BigDecimal.ZERO) > 0
                || pointsToAdd.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasTierChange() {
        return newTier != null && !newTier.equals(oldTier);
    }

    public boolean isEmpty() {
        return !hasPointChanges() && !hasTierChange();
    }
}