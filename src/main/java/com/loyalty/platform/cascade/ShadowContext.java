package com.loyalty.platform.cascade;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 影子上下文 —— 级联重算的核心内存模型。
 *
 * <p>在退款重算时，系统构建一个虚拟的影子账户，不依赖任何数据库锁。
 * 影子上下文维护三条时间线：
 * <ol>
 *   <li>等级变更时间线（从 tier_change_log 加载）</li>
 *   <li>影子积分余额（纯内存累加，不写 DB）</li>
 *   <li>影子交易明细列表（用于最终计算 Delta 差额）</li>
 * </ol>
 *
 * <p><b>使用协议</b>：每次回放事件前，必须先调用 {@link #advanceToTime(LocalDateTime)}
 * 将等级推到事件发生时刻应生效的状态，再用该状态参与规则推理。
 *
 * <p><b>线程安全</b>：这是纯内存对象，每次重算创建新实例，无共享状态。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Getter
public class ShadowContext {

    private final String programCode;
    private final String memberId;

    /** 等级变更时间线（按 changedAt 升序） */
    private final List<TierChangeRecord> tierTimeline;

    /** 当前回放位置对应的等级 */
    private String currentTier;

    /** 影子积分余额（纯内存累加） */
    private BigDecimal shadowBalance = BigDecimal.ZERO;

    /** 影子交易明细 */
    private final List<ShadowTransactionRecord> shadowTransactions = new ArrayList<>();

    /** 初始入会等级 */
    private final String initialTier;

    /** 时间轴游标位置 */
    private int timelineCursor = 0;

    public ShadowContext(String programCode, String memberId,
                         List<TierChangeRecord> tierTimeline, String initialTier) {
        this.programCode = programCode;
        this.memberId = memberId;
        this.tierTimeline = tierTimeline;
        this.initialTier = initialTier;
        this.currentTier = initialTier;
    }

    /**
     * 推进时间轴到指定时刻。
     *
     * <p>遍历等级变更时间线，将游标推到所有 {@code changedAt <= eventTime} 的变更记录之后，
     * 取最后一条变更记录的 newTier 作为 currentTier。如果没有更早的记录，则保持 initialTier。
     *
     * @param eventTime 事件发生的业务时间
     */
    public void advanceToTime(LocalDateTime eventTime) {
        if (eventTime == null) return;

        String newTier = tierTimeline.stream()
                .filter(t -> !t.changedAt().isAfter(eventTime))
                .max(Comparator.comparing(TierChangeRecord::changedAt))
                .map(TierChangeRecord::newTier)
                .orElse(initialTier);

        if (!newTier.equals(currentTier)) {
            this.currentTier = newTier;
        }
    }

    /**
     * 应用一笔影子交易（累加积分余额 + 记录明细）。
     *
     * @param eventId      事件 ID
     * @param eventType    事件类型
     * @param points       积分变动（正为发分，负为扣分）
     * @param ruleSnapshotId 使用的规则快照 ID
     */
    public void apply(String eventId, String eventType, BigDecimal points, String ruleSnapshotId) {
        this.shadowBalance = this.shadowBalance.add(points);
        this.shadowTransactions.add(new ShadowTransactionRecord(
                eventId, eventType, points, currentTier, ruleSnapshotId, LocalDateTime.now()));
    }

    /**
     * 等级变更记录（从 tier_change_log 加载）。
     */
    public record TierChangeRecord(String oldTier, String newTier, String changeReason,
                                    LocalDateTime changedAt) {}

    /**
     * 影子交易记录（内存，不落盘）。
     */
    public record ShadowTransactionRecord(String eventId, String eventType, BigDecimal points,
                                           String tierAtEvent, String ruleSnapshotId,
                                           LocalDateTime eventTime) {}
}