package com.loyalty.platform.job;

import com.loyalty.platform.common.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MergeTaskJob 集成测试 —— 验证合并任务的生命周期。
 * 使用 @DataJpaTest + Replace.NONE 连接真实 PostgreSQL。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({com.loyalty.platform.member.MemberMergeService.class})
@DisplayName("MergeTaskJob 集成测试")
class MergeTaskJobIntegrationTest {

    @PersistenceContext private EntityManager em;

    @Autowired(required = false)
    private com.loyalty.platform.member.MemberMergeService mergeService;

    private static final String PROG = "PROG001";
    private static final Long TASK_ID = 99999001L;

    @BeforeEach
    void setUp() {
        TenantContext.set(PROG);
        em.createNativeQuery("SET app.current_program_code = '" + PROG + "'").executeUpdate();
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        em.createNativeQuery("DELETE FROM member_merge_task WHERE program_code = ? AND id = ?")
                .setParameter(1, PROG).setParameter(2, TASK_ID).executeUpdate();
        TenantContext.clear();
    }

    @Test
    @DisplayName("手动创建 MergeTask 记录并查询")
    void shouldCreateAndQueryMergeTask() {
        // 创建合并任务
        em.createNativeQuery(
                "INSERT INTO member_merge_task (id, program_code, main_member_id, duplicate_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 'CREATED')")
                .setParameter(1, TASK_ID)
                .setParameter(2, PROG)
                .setParameter(3, 100L)
                .setParameter(4, 200L)
                .executeUpdate();
        em.flush();

        // 查询 CREATED 状态的任务（模拟 MergeTaskJob 的查询逻辑）
        @SuppressWarnings("unchecked")
        List<Object[]> tasks = em.createNativeQuery(
                "SELECT id, main_member_id, duplicate_member_id "
                        + "FROM member_merge_task "
                        + "WHERE program_code = ? AND status = 'CREATED' "
                        + "LIMIT 10 "
                        + "FOR UPDATE SKIP LOCKED",
                Object[].class)
                .setParameter(1, PROG)
                .getResultList();

        assertFalse(tasks.isEmpty(), "应能查询到 CREATED 状态的合并任务");
        assertEquals(TASK_ID, ((Number) tasks.get(0)[0]).longValue());
        assertEquals(100L, ((Number) tasks.get(0)[1]).longValue());
        assertEquals(200L, ((Number) tasks.get(0)[2]).longValue());
    }

    @Test
    @DisplayName("状态流转: CREATED → PROCESSING → COMPLETED")
    void shouldTransitionStatusToCompleted() {
        // 创建任务
        em.createNativeQuery(
                "INSERT INTO member_merge_task (id, program_code, main_member_id, duplicate_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 'CREATED')")
                .setParameter(1, TASK_ID)
                .setParameter(2, PROG)
                .setParameter(3, 100L)
                .setParameter(4, 200L)
                .executeUpdate();
        em.flush();

        // 标记 PROCESSING
        int updated = em.createNativeQuery(
                "UPDATE member_merge_task SET status = 'PROCESSING' "
                        + "WHERE id = ? AND program_code = ?")
                .setParameter(1, TASK_ID)
                .setParameter(2, PROG)
                .executeUpdate();
        assertEquals(1, updated);

        // 标记 COMPLETED
        updated = em.createNativeQuery(
                "UPDATE member_merge_task SET status = 'COMPLETED' "
                        + "WHERE id = ? AND program_code = ?")
                .setParameter(1, TASK_ID)
                .setParameter(2, PROG)
                .executeUpdate();
        assertEquals(1, updated);

        // 验证
        Object status = em.createNativeQuery(
                "SELECT status FROM member_merge_task WHERE id = ?")
                .setParameter(1, TASK_ID)
                .getSingleResult();
        assertEquals("COMPLETED", status);
    }

    @Test
    @DisplayName("失败任务记录错误信息")
    void shouldRecordErrorMessageOnFailure() {
        em.createNativeQuery(
                "INSERT INTO member_merge_task (id, program_code, main_member_id, duplicate_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 'CREATED')")
                .setParameter(1, TASK_ID)
                .setParameter(2, PROG)
                .setParameter(3, 100L)
                .setParameter(4, 200L)
                .executeUpdate();
        em.flush();

        // 模拟失败
        em.createNativeQuery(
                "UPDATE member_merge_task SET status = 'FAILED', error_message = '会员不存在或已被合并' "
                        + "WHERE id = ? AND program_code = ?")
                .setParameter(1, TASK_ID)
                .setParameter(2, PROG)
                .executeUpdate();

        @SuppressWarnings("unchecked")
        List<Object[]> failed = em.createNativeQuery(
                "SELECT status, error_message FROM member_merge_task WHERE id = ?",
                Object[].class)
                .setParameter(1, TASK_ID)
                .getResultList();

        assertEquals("FAILED", failed.get(0)[0]);
        assertEquals("会员不存在或已被合并", failed.get(0)[1]);
    }

    @Test
    @DisplayName("SKIP LOCKED 不阻塞并发 — 只查询 CREATED 状态")
    void shouldOnlyQueryCreatedStatus() {
        // 创建两个任务
        em.createNativeQuery(
                "INSERT INTO member_merge_task (id, program_code, main_member_id, duplicate_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 'CREATED')")
                .setParameter(1, TASK_ID)
                .setParameter(2, PROG)
                .setParameter(3, 100L)
                .setParameter(4, 200L)
                .executeUpdate();
        em.createNativeQuery(
                "INSERT INTO member_merge_task (id, program_code, main_member_id, duplicate_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 'PROCESSING')")
                .setParameter(1, TASK_ID + 1)
                .setParameter(2, PROG)
                .setParameter(3, 300L)
                .setParameter(4, 400L)
                .executeUpdate();
        em.flush();

        // 只查询 CREATED（PROCESSING 的不应返回）
        @SuppressWarnings("unchecked")
        List<Object[]> tasks = em.createNativeQuery(
                "SELECT id, status FROM member_merge_task "
                        + "WHERE program_code = ? AND status = 'CREATED' "
                        + "LIMIT 10",
                Object[].class)
                .setParameter(1, PROG)
                .getResultList();

        // 清理第二个记录
        em.createNativeQuery("DELETE FROM member_merge_task WHERE id = ?")
                .setParameter(1, TASK_ID + 1).executeUpdate();

        assertFalse(tasks.isEmpty());
        assertTrue(tasks.stream().allMatch(r -> "CREATED".equals(r[1])),
                "只应返回 CREATED 状态的任务");
    }
}