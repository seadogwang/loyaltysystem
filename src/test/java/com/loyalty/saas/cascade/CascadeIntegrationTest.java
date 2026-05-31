package com.loyalty.saas.cascade;

import com.loyalty.saas.common.context.TenantContext;
import com.loyalty.saas.domain.entity.AccountTransaction;
import com.loyalty.saas.domain.entity.RedemptionAllocation;
import com.loyalty.saas.domain.repository.AccountTransactionRepository;
import com.loyalty.saas.domain.repository.RedemptionAllocationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RedemptionReversalService.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CascadeIntegrationTest {

    @Autowired private RedemptionReversalService reversalService;
    @Autowired private AccountTransactionRepository txRepo;
    @Autowired private RedemptionAllocationRepository allocRepo;
    @PersistenceContext private EntityManager em;

    private static final String PROG = "PROG001";
    private static final Long MEMBER = 8821L;

    private Long accrualTxId;
    private Long redemptionTxId;

    @BeforeEach
    void setUp() {
        TenantContext.set(PROG);
        em.createNativeQuery("SET app.current_program_code = '" + PROG + "'").executeUpdate();
    }

    @AfterEach
    void tearDown() {
        // cleanup
        if (redemptionTxId != null) em.createNativeQuery("DELETE FROM redemption_allocation WHERE redemption_transaction_id = ?").setParameter(1, redemptionTxId).executeUpdate();
        if (accrualTxId != null) em.createNativeQuery("DELETE FROM account_transaction WHERE id IN (?, ?)").setParameter(1, accrualTxId).setParameter(2, redemptionTxId != null ? redemptionTxId : 0L).executeUpdate();
        TenantContext.clear();
    }

    @Test @Order(1)
    @DisplayName("取消兑换 → 分摊额度加回原始批次 remaining_amount")
    void cancellationRestoresRemainingAmount() {
        // Step 1: 创建 ACCRUAL 批次 (100 积分, 30天后过期)
        AccountTransaction accrual = txRepo.save(AccountTransaction.builder()
                .accountId(1L) // required FK
                .programCode(PROG).memberId(MEMBER).accountType("REWARD_POINTS")
                .transactionType("ACCRUAL").amount(new BigDecimal("100.0000"))
                .remainingAmount(new BigDecimal("60.0000")) // 已消耗40
                .expiresAt(LocalDateTime.now().plusDays(30)).status("ACTIVE")
                .operationKey("test-accrual-" + System.currentTimeMillis())
                .createdAt(LocalDateTime.now()).build());
        em.flush();
        accrualTxId = accrual.getId();

        // Step 2: 创建 REDEMPTION 流水和分摊记录
        BigDecimal redeemAmount = new BigDecimal("30.0000");
        AccountTransaction redemption = txRepo.save(AccountTransaction.builder()
                .accountId(1L)
                .programCode(PROG).memberId(MEMBER).accountType("REWARD_POINTS")
                .transactionType("REDEMPTION").amount(redeemAmount.negate())
                .remainingAmount(BigDecimal.ZERO).status("ACTIVE")
                .operationKey("test-redemption-" + System.currentTimeMillis())
                .createdAt(LocalDateTime.now()).build());
        em.flush();
        redemptionTxId = redemption.getId();

        allocRepo.save(RedemptionAllocation.builder()
                .programCode(PROG).redemptionTransactionId(redemptionTxId)
                .accrualTransactionId(accrualTxId).allocatedAmount(redeemAmount)
                .allocationOrder(1).createdAt(LocalDateTime.now()).build());
        em.flush(); em.clear();

        // Step 3: 执行取消兑换
        reversalService.cancelRedemption(PROG, redemptionTxId, 0);

        em.flush(); em.clear();

        // Step 4: 验证 remaining_amount 恢复
        AccountTransaction restored = em.find(AccountTransaction.class, accrualTxId);
        assertNotNull(restored);
        BigDecimal expected = new BigDecimal("90.0000"); // 60 + 30
        assertTrue(restored.getRemainingAmount().compareTo(expected) >= 0,
                "remainingAmount should be restored to 90, actual=" + restored.getRemainingAmount());
    }

    @Test @Order(2)
    @DisplayName("取消兑换时原始批次已过期 → 拦截作废")
    void cancelledExpiredBatchIsIntercepted() {
        // 创建已过期的 ACCRUAL 批次
        AccountTransaction expiredAccrual = txRepo.save(AccountTransaction.builder()
                .accountId(1L)
                .programCode(PROG).memberId(MEMBER).accountType("REWARD_POINTS")
                .transactionType("ACCRUAL").amount(new BigDecimal("50.0000"))
                .remainingAmount(new BigDecimal("20.0000"))
                .expiresAt(LocalDateTime.now().minusDays(30)) // 已过期30天
                .status("ACTIVE")
                .operationKey("test-expired-" + System.currentTimeMillis())
                .createdAt(LocalDateTime.now().minusDays(60)).build());
        em.flush();
        accrualTxId = expiredAccrual.getId();

        AccountTransaction redemption = txRepo.save(AccountTransaction.builder()
                .accountId(1L)
                .programCode(PROG).memberId(MEMBER).accountType("REWARD_POINTS")
                .transactionType("REDEMPTION").amount(new BigDecimal("10.0000").negate())
                .remainingAmount(BigDecimal.ZERO).status("ACTIVE")
                .operationKey("test-redemption-expired-" + System.currentTimeMillis())
                .createdAt(LocalDateTime.now()).build());
        em.flush();
        redemptionTxId = redemption.getId();

        allocRepo.save(RedemptionAllocation.builder()
                .programCode(PROG).redemptionTransactionId(redemptionTxId)
                .accrualTransactionId(accrualTxId).allocatedAmount(new BigDecimal("10.0000"))
                .allocationOrder(1).createdAt(LocalDateTime.now()).build());
        em.flush(); em.clear();

        // 取消兑换（不启用宽限期）
        reversalService.cancelRedemption(PROG, redemptionTxId, 0);

        em.flush(); em.clear();

        // remaining_amount 不应被恢复（已过期拦截）
        AccountTransaction updated = em.find(AccountTransaction.class, accrualTxId);
        assertNotNull(updated);
        BigDecimal remaining = updated.getRemainingAmount();
        assertTrue(remaining.compareTo(new BigDecimal("20.0000")) <= 0,
                "已过期批次不应恢复积分，expected<=20, actual=" + remaining);
    }

    @Test @Order(3)
    @DisplayName("无分摊记录时静默返回")
    void noAllocationsSilentlyReturns() {
        assertDoesNotThrow(() -> reversalService.cancelRedemption(PROG, 999999L, 0));
    }
}