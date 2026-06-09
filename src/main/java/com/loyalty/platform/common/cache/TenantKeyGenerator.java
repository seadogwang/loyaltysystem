package com.loyalty.platform.common.cache;

import com.loyalty.platform.common.context.TenantContext;

/**
 * 租户感知的 Redis Key 生成器。
 * 所有 Redis Key 都强制包含租户前缀，防止跨租户数据泄露。
 */
public final class TenantKeyGenerator {

    private TenantKeyGenerator() {}

    /**
     * 生成租户隔离的 Redis Key。
     * 格式: tenant:{programCode}:{prefix}:{businessId}
     *
     * @param prefix     业务前缀（如 "member", "token", "rate_limit"）
     * @param businessId 业务 ID
     * @return 租户隔离的完整 Key
     * @throws IllegalStateException 如果租户上下文未设置
     */
    public static String key(String prefix, String businessId) {
        String pc = TenantContext.get();
        if (pc == null) {
            throw new IllegalStateException("TenantContext required for Redis key generation");
        }
        return "tenant:" + pc + ":" + prefix + ":" + businessId;
    }

    /**
     * 生成租户隔离的 Redis 模式匹配前缀（用于 SCAN/KEYS）。
     * 格式: tenant:{programCode}:{prefix}:*
     *
     * @param prefix 业务前缀
     * @return 模式匹配字符串
     */
    public static String listPrefix(String prefix) {
        String pc = TenantContext.get();
        if (pc == null) {
            throw new IllegalStateException("TenantContext required for Redis key generation");
        }
        return "tenant:" + pc + ":" + prefix + ":*";
    }
}