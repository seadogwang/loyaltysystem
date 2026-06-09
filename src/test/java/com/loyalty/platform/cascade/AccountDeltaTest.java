package com.loyalty.platform.cascade;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class AccountDeltaTest {

    @Test
    void emptyDelta() {
        AccountDelta delta = new AccountDelta();
        assertTrue(delta.isEmpty());
        assertFalse(delta.hasPointChanges());
        assertFalse(delta.hasTierChange());
    }

    @Test
    void hasPointChanges() {
        AccountDelta delta = AccountDelta.builder()
                .pointsToDeduct(new BigDecimal("10.0000")).build();
        assertTrue(delta.hasPointChanges());
        assertFalse(delta.isEmpty());
    }

    @Test
    void hasTierChange() {
        AccountDelta delta = AccountDelta.builder()
                .oldTier("GOLD").newTier("SILVER").build();
        assertTrue(delta.hasTierChange());
        assertFalse(delta.hasPointChanges());
    }
}