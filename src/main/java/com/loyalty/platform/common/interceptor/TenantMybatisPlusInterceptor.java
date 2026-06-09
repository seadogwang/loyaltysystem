package com.loyalty.platform.common.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.loyalty.platform.common.context.TenantContext;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Set;

/**
 * MyBatis-Plus 租户安全审计拦截器 —— 四层防御体系的第二层（辅助防线）。
 *
 * <p>注意：loyalty_dev 数据库已通过 PostgreSQL Row-Level Security (RLS) Policy
 * 实现终极租户隔离，本拦截器作为<b>辅助防御层</b>提供 SQL 级别的租户过滤审计日志。
 *
 * <p>在 dev 环境（无 RLS 的本地库）中，本拦截器自动在 SQL WHERE 子句中追加
 * {@code program_code = ?} 条件，实现应用层的租户隔离。
 *
 * <p>线程安全：无实例状态，通过 {@link TenantContext#get()} 获取租户信息。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class TenantMybatisPlusInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantMybatisPlusInterceptor.class);

    private final Set<String> multiTenantTables;

    public TenantMybatisPlusInterceptor(Set<String> multiTenantTables) {
        this.multiTenantTables = Set.copyOf(multiTenantTables);
        log.info("[TenantMybatisPlusInterceptor] 已注册多租户表: {}", multiTenantTables);
    }

    /**
     * 在 SQL 准备执行前，注入租户过滤条件。
     * PostgreSQL RLS Policy 是第一道防线；本拦截器在 dev 环境（无 RLS）中提供应用层隔离。
     */
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        String programCode = TenantContext.get();
        if (programCode == null) {
            return;
        }

        try {
            BoundSql boundSql = sh.getBoundSql();
            String sql = boundSql.getSql();

            // 审计日志：记录 SQL 执行时的租户上下文
            log.debug("[TenantMybatisPlusInterceptor] SQL 执行审计: programCode={}, sql={}",
                    programCode, sql.substring(0, Math.min(200, sql.length())));

            // 检查是否包含已知的多租户表
            boolean containsMultiTenantTable = multiTenantTables.stream()
                    .anyMatch(table -> sql.toLowerCase().contains(table.toLowerCase()));

            if (containsMultiTenantTable && !sql.toLowerCase().contains("program_code")) {
                String modifiedSql = injectTenantFilter(sql, programCode);
                MetaObject metaObject = SystemMetaObject.forObject(boundSql);
                metaObject.setValue("sql", modifiedSql);
                log.debug("[TenantMybatisPlusInterceptor] 已注入租户过滤: programCode={}", programCode);
            }
        } catch (Exception e) {
            log.warn("[TenantMybatisPlusInterceptor] SQL 租户注入异常（不影响业务，降级依赖 RLS）: {}", e.getMessage());
        }
    }

    /**
     * 在 SQL 语句中注入 program_code 过滤条件。
     *
     * <p>使用转义（而非参数化绑定）适用于 InnerInterceptor 无法直接操作
     * PreparedStatement 参数的限制。在 dev 环境足以满足安全需求，
     * 生产环境有 PostgreSQL RLS Policy 作为最终防线。
     */
    String injectTenantFilter(String sql, String programCode) {
        String escapedCode = programCode.replace("'", "''");
        String tenantCondition = "program_code = '" + escapedCode + "'";

        // 找到 ORDER BY / GROUP BY / HAVING / LIMIT / UNION 的位置作为插入锚点
        int insertIdx = findKeywordInsertPoint(sql);
        String upperSql = sql.toUpperCase();

        if (upperSql.contains("WHERE")) {
            return sql.substring(0, insertIdx) + " AND " + tenantCondition + " " + sql.substring(insertIdx);
        } else {
            return sql.substring(0, insertIdx) + " WHERE " + tenantCondition + " " + sql.substring(insertIdx);
        }
    }

    /** 找到 SQL 后缀关键词（ORDER BY 等）之前的位置作为条件注入锚点 */
    private int findKeywordInsertPoint(String sql) {
        String upper = sql.toUpperCase();
        int idx = sql.length();
        for (String kw : new String[]{"ORDER BY", "GROUP BY", "HAVING", "LIMIT", "OFFSET", "UNION", "FOR UPDATE"}) {
            int pos = upper.lastIndexOf(kw);
            if (pos > 0 && pos < idx) {
                idx = pos;
            }
        }
        return idx;
    }

    /**
     * 判断表名是否属于多租户表。
     */
    public boolean isMultiTenantTable(String tableName) {
        return tableName != null && multiTenantTables.contains(tableName);
    }

    public Set<String> getMultiTenantTables() {
        return multiTenantTables;
    }
}