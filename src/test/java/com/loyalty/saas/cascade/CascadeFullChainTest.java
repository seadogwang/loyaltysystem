package com.loyalty.saas.cascade;

import com.loyalty.saas.common.context.TenantContext;
import com.loyalty.saas.domain.entity.*;
import com.loyalty.saas.domain.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RedemptionReversalService.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CascadeFullChainTest {

    @Autowired private RedemptionReversalService reversalService;
    @Autowired private AccountTransactionRepository txRepo;
    @Autowired private RedemptionAllocationRepository allocRepo;
    @Autowired private TierChangeLogRepository tierLogRepo;
    @Autowired private CascadeRecalcJobRepository jobRepo;
    @Autowired private CascadeRecalcLogRepository recalcLogRepo;
    @PersistenceContext private EntityManager em;

    private static final String PROG = "PROG001";
    private static final Long MEMBER = 8821L;
    private final Set<Long> cleanupTxIds = new LinkedHashSet<>();
    private final Set<Long> cleanupJobIds = new LinkedHashSet<>();

    @BeforeEach
    void setUp() {
        TenantContext.set(PROG);
        em.createNativeQuery("SET app.current_program_code = '" + PROG + "'").executeUpdate();
    }

    @AfterEach
    void tearDown() {
        for (Long id : cleanupTxIds) {
            try { em.createNativeQuery("DELETE FROM redemption_allocation WHERE redemption_transaction_id = ?").setParameter(1, id).executeUpdate(); } catch (Exception ignored) {}
            try { em.createNativeQuery("DELETE FROM account_transaction WHERE id = ?").setParameter(1, id).executeUpdate(); } catch (Exception ignored) {}
        }
        for (Long id : cleanupJobIds) {
            try { em.createNativeQuery("DELETE FROM cascade_recalc_job WHERE id = ?").setParameter(1, id).executeUpdate(); } catch (Exception ignored) {}
        }
        cleanupTxIds.clear();
        cleanupJobIds.clear();
        TenantContext.clear();
    }

    // ==================== ShadowContext 回放测试（使用真实 DB 数据） ====================

    @Test @Order(1)
    @DisplayName("真实等级时间线构建：从 tier_change_log 加载历史变更")
    void realTierTimelineFromDb() {
        // 写入等级变更历史
        LocalDateTime base = LocalDateTime.of(2026, 5, 1, 0, 0);
        tierLogRepo.save(TierChangeLog.builder().programCode(PROG).memberId(MEMBER)
                .fromTier("BASE").toTier("SILVER").changeReason("ORDER_ACCRUAL").changedAt(base.plusDays(10)).build());
        tierLogRepo.save(TierChangeLog.builder().programCode(PROG).memberId(MEMBER)
                .fromTier("SILVER").toTier("GOLD").changeReason("ORDER_ACCRUAL").changedAt(base.plusDays(30)).build());
        em.flush();

        // 从 DB 加载时间线
        List<TierChangeLog> logs = tierLogRepo.findByMemberOrderByTime(PROG, MEMBER);
        assertEquals(2, logs.size());

        // 构建 ShadowContext
        List<ShadowContext.TierChangeRecord> timeline = logs.stream()
                .map(t -> new ShadowContext.TierChangeRecord(t.getFromTier(), t.getToTier(),
                        t.getChangeReason(), t.getChangedAt()))
                .toList();
        ShadowContext shadow = new ShadowContext(PROG, String.valueOf(MEMBER), timeline, "BASE");

        // 推进到 SILVER 期间
        shadow.advanceToTime(base.plusDays(15));
        assertEquals("SILVER", shadow.getCurrentTier());

        // 推进到 GOLD 期间
        shadow.advanceToTime(base.plusDays(35));
        assertEquals("GOLD", shadow.getCurrentTier());

        // 清理
        em.createNativeQuery("DELETE FROM tier_change_log WHERE program_code = ? AND member_id = ?")
                .setParameter(1, PROG).setParameter(2, MEMBER).executeUpdate();
    }

    // ==================== 级联重算任务生命周期测试 ====================

    @Test @Order(2)
    @DisplayName("cascade_recalc_job: PENDING → RUNNING → SUCCEEDED 完整流转")
    void jobLifecycle() {
        String jobId = "test-job-" + UUID.randomUUID().toString().substring(0, 8);

        // 创建 PENDING 任务
        CascadeRecalcJob job = jobRepo.save(CascadeRecalcJob.builder()
                .programCode(PROG).jobId(jobId).reverseEventId("evt-refund-001")
                .memberId(MEMBER).status("PENDING").affectedCount(0)
                .createdAt(LocalDateTime.now()).build());
        em.flush();
        cleanupJobIds.add(job.getId());

        // 验证 PENDING 状态
        var found = jobRepo.findByJobId(PROG, jobId);
        assertTrue(found.isPresent());
        assertEquals("PENDING", found.get().getStatus());

        // PENDING → RUNNING
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        jobRepo.save(job);
        em.flush(); em.clear();

        var running = jobRepo.findByJobId(PROG, jobId);
        assertTrue(running.isPresent());
        assertEquals("RUNNING", running.get().getStatus());
        assertNotNull(running.get().getStartedAt());

        // RUNNING → SUCCEEDED
        job.setStatus("SUCCEEDED");
        job.setFinishedAt(LocalDateTime.now());
        job.setAffectedCount(5);
        jobRepo.save(job);
        em.flush(); em.clear();

        var done = jobRepo.findByJobId(PROG, jobId);
        assertTrue(done.isPresent());
        assertEquals("SUCCEEDED", done.get().getStatus());
        assertEquals(5, done.get().getAffectedCount());
        assertNotNull(done.get().getFinishedAt());
    }

    @Test @Order(3)
    @DisplayName("卡死恢复: RUNNING 超过阈值 → 重置为 PENDING")
    void stuckJobRecovery() {
        String jobId = "stuck-job-" + UUID.randomUUID().toString().substring(0, 8);

        // 创建卡死任务（startedAt 在 10 分钟前）
        CascadeRecalcJob stuckJob = jobRepo.save(CascadeRecalcJob.builder()
                .programCode(PROG).jobId(jobId).reverseEventId("evt-stuck-001")
                .memberId(MEMBER).status("RUNNING")
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .createdAt(LocalDateTime.now().minusMinutes(15)).build());
        em.flush();
        cleanupJobIds.add(stuckJob.getId());

        // 查询卡死任务（阈值 5 分钟）
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(5);
        var stuckJobs = jobRepo.findStuckJobs(PROG, timeout);
        assertFalse(stuckJobs.isEmpty());
        assertTrue(stuckJobs.stream().anyMatch(j -> jobId.equals(j.getJobId())),
                "卡死任务应被检测到");

        // 模拟恢复
        for (CascadeRecalcJob j : stuckJobs) {
            j.setStatus("PENDING");
            j.setStartedAt(null);
            jobRepo.save(j);
        }
        em.flush(); em.clear();

        // 验证已恢复
        var recovered = jobRepo.findByJobId(PROG, jobId);
        assertTrue(recovered.isPresent());
        assertEquals("PENDING", recovered.get().getStatus());
        assertNull(recovered.get().getStartedAt());
    }

    // ==================== Delta 差额补偿幂等性测试 ====================

    @Test @Order(4)
    @DisplayName("补偿幂等: 同一 reverseEventId 只能补偿一次")
    void compensationIdempotency() {
        String reverseId = "comp-idem-" + UUID.randomUUID().toString().substring(0, 8);

        // 第一次补偿写入
        recalcLogRepo.save(CascadeRecalcLog.builder()
                .programCode(PROG).reverseEventId(reverseId).memberId(MEMBER)
                .affectedEventId("evt-001").originalPoints(new BigDecimal("100.00"))
                .recalculatedPoints(new BigDecimal("80.00"))
                .pointsDiff(new BigDecimal("-20.00")).recalcOrder(1)
                .createdAt(LocalDateTime.now()).build());
        em.flush();

        // 幂等检查
        assertTrue(recalcLogRepo.existsByReverseEventId(PROG, reverseId),
                "第一次补偿应被记录");
        assertFalse(recalcLogRepo.existsByReverseEventId(PROG, "non-existent"),
                "不存在的补偿应返回 false");

        // 清理
        em.createNativeQuery("DELETE FROM cascade_recalc_log WHERE reverse_event_id = ?")
                .setParameter(1, reverseId).executeUpdate();
    }

    @Test @Order(5)
    @DisplayName("AccountDelta 差额计算：无差额时跳过补偿")
    void deltaCalculationEmpty() {
        AccountDelta delta = new AccountDelta();
        assertTrue(delta.isEmpty());

        // 有差额
        delta.setPointsToDeduct(new BigDecimal("50.0000"));
        assertFalse(delta.isEmpty());
        assertTrue(delta.hasPointChanges());

        // 等级变更
        delta.setPointsToDeduct(BigDecimal.ZERO);
        delta.setOldTier("GOLD");
        delta.setNewTier("SILVER");
        assertFalse(delta.isEmpty());
        assertTrue(delta.hasTierChange());
    }

    // ==================== 退款还原 宽限期测试 ====================

    @Test @Order(6)
    @DisplayName("宽限期: 过期7天内的积分正常恢复")
    void gracePeriodRestoration() {
        // 创建 3 天前过期的 ACCRUAL 批次
        AccountTransaction accrual = txRepo.save(AccountTransaction.builder()
                .accountId(1L).programCode(PROG).memberId(MEMBER).accountType("REWARD_POINTS")
                .transactionType("ACCRUAL").amount(new BigDecimal("80.0000"))
                .remainingAmount(new BigDecimal("80.0000"))
                .expiresAt(LocalDateTime.now().minusDays(3)) // 3天前过期
                .status("ACTIVE")
                .operationKey("grace-test-" + System.currentTimeMillis())
                .createdAt(LocalDateTime.now().minusDays(60)).build());
        em.flush();
        cleanupTxIds.add(accrual.getId());

        BigDecimal redeemAmount = new BigDecimal("20.0000");
        AccountTransaction redemption = txRepo.save(AccountTransaction.builder()
                .accountId(1L).programCode(PROG).memberId(MEMBER).accountType("REWARD_POINTS")
                .transactionType("REDEMPTION").amount(redeemAmount.negate())
                .remainingAmount(BigDecimal.ZERO).status("ACTIVE")
                .operationKey("grace-redemption-" + System.currentTimeMillis())
                .createdAt(LocalDateTime.now()).build());
        em.flush();
        cleanupTxIds.add(redemption.getId());

        allocRepo.save(RedemptionAllocation.builder()
                .programCode(PROG).redemptionTransactionId(redemption.getId())
                .accrualTransactionId(accrual.getId()).allocatedAmount(redeemAmount)
                .allocationOrder(1).createdAt(LocalDateTime.now()).build());
        em.flush(); em.clear();

        // 取消兑换，启用 7 天宽限期
        reversalService.cancelRedemption(PROG, redemption.getId(), 7);
        em.flush(); em.clear();

        // 过期 3 天 < 宽限期 7 天 → 应恢复
        AccountTransaction restored = em.find(AccountTransaction.class, accrual.getId());
        assertNotNull(restored);
        BigDecimal expected = new BigDecimal("100.0000"); // 80 + 20
        assertTrue(restored.getRemainingAmount().compareTo(expected) >= 0,
                "宽限期内应恢复: expected=" + expected + ", actual=" + restored.getRemainingAmount());
    }
}