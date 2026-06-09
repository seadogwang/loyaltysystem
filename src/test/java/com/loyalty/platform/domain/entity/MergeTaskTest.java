package com.loyalty.platform.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MergeTask 实体单元测试 —— 验证构造器、Builder、状态流转。
 */
@DisplayName("MergeTask 实体测试")
class MergeTaskTest {

    @Test
    @DisplayName("Builder 创建默认状态为 CREATED")
    void shouldDefaultStatusToCreated() {
        MergeTask task = MergeTask.builder()
                .programCode("PROG001")
                .mainMemberId(100L)
                .duplicateMemberId(200L)
                .build();

        assertEquals("CREATED", task.getStatus());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
        assertNull(task.getErrorMessage());
    }

    @Test
    @DisplayName("状态流转：CREATED → PROCESSING → COMPLETED")
    void shouldTransitionToCompleted() {
        MergeTask task = MergeTask.builder()
                .programCode("PROG001")
                .mainMemberId(100L)
                .duplicateMemberId(200L)
                .build();

        assertEquals("CREATED", task.getStatus());

        task.setStatus("PROCESSING");
        assertEquals("PROCESSING", task.getStatus());

        task.setStatus("COMPLETED");
        assertEquals("COMPLETED", task.getStatus());
    }

    @Test
    @DisplayName("状态流转：CREATED → PROCESSING → FAILED")
    void shouldTransitionToFailed() {
        MergeTask task = MergeTask.builder()
                .programCode("PROG001")
                .mainMemberId(100L)
                .duplicateMemberId(200L)
                .build();

        task.setStatus("PROCESSING");
        task.setStatus("FAILED");
        task.setErrorMessage("会员不存在");

        assertEquals("FAILED", task.getStatus());
        assertEquals("会员不存在", task.getErrorMessage());
    }

    @Test
    @DisplayName("全参构造器可正确赋值")
    void shouldSetAllFieldsWithConstructor() {
        LocalDateTime now = LocalDateTime.now();
        MergeTask task = new MergeTask(
                1L, "PROG001", 100L, 200L,
                "COMPLETED", null, now, now);

        assertEquals(1L, task.getId());
        assertEquals("PROG001", task.getProgramCode());
        assertEquals(100L, task.getMainMemberId());
        assertEquals(200L, task.getDuplicateMemberId());
        assertEquals("COMPLETED", task.getStatus());
        assertEquals(now, task.getCreatedAt());
    }

    @Test
    @DisplayName("errorMessage 支持多字节字符")
    void shouldSupportUnicodeErrorMessage() {
        MergeTask task = MergeTask.builder()
                .programCode("PROG001")
                .mainMemberId(100L)
                .duplicateMemberId(200L)
                .status("FAILED")
                .errorMessage("合并失败：会员 200 已被合并至 300")
                .build();

        assertTrue(task.getErrorMessage().contains("已被合并"));
    }
}