package com.loyalty.saas.security;

import com.loyalty.saas.common.context.TenantContext;
import com.loyalty.saas.common.dto.ApiResponse;
import com.loyalty.saas.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 租户配额限制与计费哨兵 — SaaS 商业化限流与配额控制。
 *
 * <p><b>两个核心功能</b>：
 *
 * <p><b>1. API 动态限流（速率限制）</b>：
 * 基于 Redisson {@code RRateLimiter} 实现令牌桶算法，
 * 对不同 program_code 的 API 调用频率进行动态控制。
 * 大促期间可动态提升限流阈值（飞行模式）。
 *
 * <p><b>2. 会员总数高水位线阻断</b>：
 * 在会员入会（POST /api/members）的核心入口处，查询当前租户
 * 的会员总数。若已超过 SaaS 签约套餐的最大限制，直接拦截
 * 并返回业务错误码 {@code ERR_TENANT_QUOTA_EXCEEDED}。
 *
 * <p><b>配额配置来源</b>：从 {@code tenant_quota_usage} 表和
 * {@code program.config_json.plan_limits} 中读取。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Component
public class QuotaBillingSentinel implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(QuotaBillingSentinel.class);

    /** 默认 QPS 限制（按租户） */
    private static final double DEFAULT_TENANT_QPS = 100.0;
    /** 大促飞行模式 QPS */
    private static final double FLIGHT_MODE_QPS = 500.0;

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String programCode = TenantContext.get();

        if (programCode == null) return true; // 非租户请求放行

        // ==================== 1. 速率限制检查 ====================
        if (!checkRateLimit(programCode)) {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":\"ERR_RATE_LIMITED\",\"message\":\"请求频率过高，请稍后重试\"}");
            return false;
        }

        // ==================== 2. 会员总数配额检查 ====================
        if ("POST".equalsIgnoreCase(method) && (path.contains("/api/members")
                || path.contains("/api/enroll"))) {
            QuotaCheckResult quota = checkMemberQuota(programCode);
            if (quota.isExceeded()) {
                log.warn("[Quota] 租户 [{}] 会员配额超限: {}/{}", programCode, quota.current, quota.max);
                response.setStatus(HttpServletResponse.SC_OK); // HTTP 200 + 业务错误码
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(ApiResponse.error("ERR_TENANT_QUOTA_EXCEEDED",
                                String.format("会员总数已达套餐上限: %d/%d", quota.current, quota.max))));
                return false;
            }
        }

        return true;
    }

    // ==================== 速率限制 ====================

    /**
     * 基于内存计数器的简化限流（骨架）。
     * 生产环境应替换为 Redisson RRateLimiter：
     * <pre>{@code
     * RRateLimiter limiter = redissonClient.getRateLimiter("rate:" + programCode);
     * limiter.trySetRate(RateType.OVERALL, qps, 1, RateIntervalUnit.SECONDS);
     * return limiter.tryAcquire();
     * }</pre>
     */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>
            requestCounters = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long>
            windowStartTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long WINDOW_MS = 1000;

    private boolean checkRateLimit(String programCode) {
        long now = System.currentTimeMillis();
        long windowStart = windowStartTimes.getOrDefault(programCode, now);

        // 新窗口：重置计数器
        if (now - windowStart > WINDOW_MS) {
            windowStartTimes.put(programCode, now);
            requestCounters.put(programCode, new java.util.concurrent.atomic.AtomicInteger(0));
        }

        int count = requestCounters.computeIfAbsent(programCode, k ->
                new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
        double qps = getTenantQps(programCode);

        if (count > qps) {
            log.warn("[Quota] 租户 [{}] 速率超限: {}/{}", programCode, count, (int) qps);
            return false;
        }
        return true;
    }

    private double getTenantQps(String programCode) {
        // 从 program.config_json.plan_limits 读取
        try {
            Object result = em.createNativeQuery(
                    "SELECT config_json->'plan_limits'->>'api_qps' FROM program WHERE code = ?")
                    .setParameter(1, programCode)
                    .getSingleResult();
            if (result != null) return Double.parseDouble(result.toString());
        } catch (Exception e) {
            log.debug("[Quota] 无法读取自定义 QPS，使用默认值");
        }
        return DEFAULT_TENANT_QPS;
    }

    // ==================== 配额检查 ====================

    /**
     * 查询当前租户会员总数并对比套餐限额。
     */
    QuotaCheckResult checkMemberQuota(String programCode) {
        try {
            // 1. 统计当前会员数
            Number currentCount = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM member WHERE program_code = ? AND status != 'DEACTIVATED'")
                    .setParameter(1, programCode)
                    .getSingleResult();
            long current = currentCount.longValue();

            // 2. 读取套餐最大限额
            long maxMembers = getMaxMembers(programCode);

            return new QuotaCheckResult(current, maxMembers, current >= maxMembers);
        } catch (Exception e) {
            log.error("[Quota] 配额查询异常: program={}", programCode, e);
            return new QuotaCheckResult(0, Long.MAX_VALUE, false); // 异常放行
        }
    }

    private long getMaxMembers(String programCode) {
        try {
            // 从 tenant_quota_usage 读取套餐限额
            Object result = em.createNativeQuery(
                    "SELECT tqu.max_members FROM tenant_quota_usage tqu "
                            + "JOIN program p ON p.tenant_id = tqu.tenant_id "
                            + "WHERE p.code = ?")
                    .setParameter(1, programCode)
                    .getSingleResult();
            if (result != null) return ((Number) result).longValue();
        } catch (Exception e) {
            log.debug("[Quota] 无法读取租户配额配置");
        }
        return 10000; // 默认 10000 会员上限
    }

    /** 配额检查结果 */
    public record QuotaCheckResult(long current, long max, boolean isExceeded) {}
}