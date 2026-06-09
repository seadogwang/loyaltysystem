package com.loyalty.platform.common.cache;

import com.loyalty.platform.common.context.TenantContext;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TenantKeyGenerator 单元测试 —— 验证租户隔离 Key 生成。
 */
@DisplayName("TenantKeyGenerator 单元测试")
class TenantKeyGeneratorTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("正常生成租户隔离 Key")
    void shouldGenerateTenantScopedKey() {
        TenantContext.set("PROG001");
        String key = TenantKeyGenerator.key("member", "12345");
        assertEquals("tenant:PROG001:member:12345", key);
    }

    @Test
    @DisplayName("不同租户生成不同 Key")
    void shouldGenerateDifferentKeysForDifferentTenants() {
        TenantContext.set("PROG001");
        String keyA = TenantKeyGenerator.key("member", "1");

        TenantContext.set("PROG002");
        String keyB = TenantKeyGenerator.key("member", "1");

        assertNotEquals(keyA, keyB);
        assertEquals("tenant:PROG001:member:1", keyA);
        assertEquals("tenant:PROG002:member:1", keyB);
    }

    @Test
    @DisplayName("生成模式匹配前缀")
    void shouldGenerateListPrefix() {
        TenantContext.set("PROG001");
        String prefix = TenantKeyGenerator.listPrefix("member");
        assertEquals("tenant:PROG001:member:*", prefix);
    }

    @Test
    @DisplayName("未设置租户上下文时抛出异常")
    void shouldThrowWhenTenantNotSet() {
        assertThrows(IllegalStateException.class,
                () -> TenantKeyGenerator.key("member", "123"));

        assertThrows(IllegalStateException.class,
                () -> TenantKeyGenerator.listPrefix("member"));
    }

    @Test
    @DisplayName("支持中文前缀")
    void shouldSupportChinesePrefix() {
        TenantContext.set("PROG001");
        String key = TenantKeyGenerator.key("积分", "8821");
        assertEquals("tenant:PROG001:积分:8821", key);
    }
}