package com.loyalty.saas.cascade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShadowContextTest {

    private ShadowContext shadow;
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        // tierTimeline: 1/15 升为 SILVER, 2/15 升为 GOLD
        List<ShadowContext.TierChangeRecord> timeline = new ArrayList<>();
        timeline.add(new ShadowContext.TierChangeRecord(
                "BASE", "SILVER", "UPGRADE", BASE.plusDays(15)));
        timeline.add(new ShadowContext.TierChangeRecord(
                "SILVER", "GOLD", "UPGRADE", BASE.plusDays(45)));

        shadow = new ShadowContext("PROG001", "8821", timeline, "BASE");
    }

    @Test
    void initialTierIsBase() {
        assertEquals("BASE", shadow.getCurrentTier());
    }

    @Test
    void advanceToTimeBeforeFirstUpgrade() {
        shadow.advanceToTime(BASE.plusDays(10)); // 1/10, before SILVER upgrade
        assertEquals("BASE", shadow.getCurrentTier());
    }

    @Test
    void advanceToTimeAtSilverUpgrade() {
        shadow.advanceToTime(BASE.plusDays(16)); // 1/16, after SILVER upgrade
        assertEquals("SILVER", shadow.getCurrentTier());
    }

    @Test
    void advanceToTimeAtGoldUpgrade() {
        shadow.advanceToTime(BASE.plusDays(50)); // 2/20, after GOLD upgrade
        assertEquals("GOLD", shadow.getCurrentTier());
    }

    @Test
    void advanceBackwardsResetsTier() {
        // go forward first
        shadow.advanceToTime(BASE.plusDays(50));
        assertEquals("GOLD", shadow.getCurrentTier());
        // go back — re-evaluates from timeline (correct for cascade replay)
        shadow.advanceToTime(BASE.plusDays(10));
        assertEquals("BASE", shadow.getCurrentTier(), "回到早期时间点应重新评估为早期等级");
    }

    @Test
    void applyAccumulatesShadowBalance() {
        shadow.advanceToTime(BASE.plusDays(50));
        shadow.apply("evt-1", "ACCRUAL", new BigDecimal("100.0000"), "SNAP-1");
        shadow.apply("evt-2", "ACCRUAL", new BigDecimal("50.0000"), "SNAP-1");

        assertEquals(0, new BigDecimal("150.0000").compareTo(shadow.getShadowBalance()));
        assertEquals(2, shadow.getShadowTransactions().size());
    }

    @Test
    void emptyTimelineUsesInitialTier() {
        ShadowContext emptyTimeline = new ShadowContext("PROG", "M1", List.of(), "BASE");
        emptyTimeline.advanceToTime(LocalDateTime.now());
        assertEquals("BASE", emptyTimeline.getCurrentTier());
    }
}