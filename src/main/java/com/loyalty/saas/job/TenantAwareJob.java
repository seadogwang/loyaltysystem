package com.loyalty.saas.job;

import com.loyalty.saas.common.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.function.Consumer;

/**
 * 定时任务租户分片基类 —— 后台 Job 安全红线。
 *
 * <p>由于后台定时任务没有 HTTP 请求，{@link TenantContext} 的 ThreadLocal 默认为空！
 * 每一个 Job 必须在底层硬性实现租户分片与隔离，严禁发生租户穿透。
 *
 * <p>使用方式：
 * <pre>{@code
 * runWithTenantIsolation(programCodes -> {
 *     for (String pc : programCodes) {
 *         TenantContext.set(pc);
 *         try {
 *             // 处理该租户的业务逻辑
 *         } finally {
 *             TenantContext.clear();
 *         }
 *     }
 * });
 * }</pre>
 */
@Component
public abstract class TenantAwareJob {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @PersistenceContext
    protected EntityManager em;

    /**
     * 获取所有活跃租户的 program_code 列表。
     * 每次执行时动态查询，避免缓存过期导致的租户遗漏。
     */
    protected List<String> getActiveProgramCodes() {
        @SuppressWarnings("unchecked")
        List<String> codes = em.createNativeQuery(
                "SELECT code FROM program WHERE status = 'ACTIVE' ORDER BY code")
                .getResultList();
        return codes;
    }

    /**
     * 租户隔离执行器 —— 确保 each-tenant 级别的事务边界和 finally 清理。
     *
     * @param perTenantLogic 每个租户的业务逻辑（入参为 programCode）
     */
    protected void forEachTenant(Consumer<String> perTenantLogic) {
        List<String> programCodes = getActiveProgramCodes();
        log.info("[{}] 开始定时任务，共 {} 个租户", getJobName(), programCodes.size());

        int successCount = 0;
        int failCount = 0;

        for (String programCode : programCodes) {
            // 【安全红线】手动设置租户上下文
            TenantContext.set(programCode);
            try {
                // 设置 PostgreSQL RLS 上下文
                em.createNativeQuery(
                        "SET app.current_program_code = '" + programCode + "'")
                        .executeUpdate();

                perTenantLogic.accept(programCode);
                successCount++;

            } catch (Exception e) {
                failCount++;
                log.error("[{}] 租户 [{}] 处理异常", getJobName(), programCode, e);
            } finally {
                // 【安全红线】finally 块中严格清理 ThreadLocal，防止线程池复用污染
                TenantContext.clear();
            }
        }

        log.info("[{}] 定时任务完成: 成功={}, 失败={}", getJobName(), successCount, failCount);
    }

    /** 任务名称（日志用） */
    protected abstract String getJobName();
}