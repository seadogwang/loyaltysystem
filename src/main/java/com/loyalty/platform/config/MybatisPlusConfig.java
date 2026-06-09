package com.loyalty.platform.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.loyalty.platform.common.interceptor.TenantMybatisPlusInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * MyBatis-Plus 配置。
 *
 * <p>注册 {@link TenantMybatisPlusInterceptor} 作为内部拦截器，
 * 实现 MyBatis SQL 层面的租户审计日志。
 *
 * <p>多租户表列表由 {@link TenantProperties} 统一管理，
 * 来源于 application.yml 的 {@code loyalty.tenant.multi-tenant-tables}，
 * 与 {@link HibernateInterceptorConfig} 共享同一个配置源。
 */
@Configuration
public class MybatisPlusConfig {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusConfig.class);

    private final TenantProperties tenantProperties;

    public MybatisPlusConfig(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        Set<String> tables = tenantProperties.getMultiTenantTables();
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        TenantMybatisPlusInterceptor tenantInterceptor =
                new TenantMybatisPlusInterceptor(tables);
        interceptor.addInnerInterceptor(tenantInterceptor);
        log.info("[MybatisPlusConfig] Tenant SQL interceptor registered, tables: {}", tables);
        return interceptor;
    }
}