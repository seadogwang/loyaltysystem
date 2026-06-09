package com.loyalty.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Set;

/**
 * 租户配置属性 — 从 application.yml 的 loyalty.tenant 前缀读取。
 *
 * <p>作为 Hibernate 和 MyBatis 拦截器的共享配置源，
 * 统一管理多租户表列表，避免多处硬编码导致不一致。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "loyalty.tenant")
public class TenantProperties {

    /** 多租户表名列表（拦截器自动注入 program_code） */
    private Set<String> multiTenantTables = Collections.emptySet();

    public Set<String> getMultiTenantTables() {
        return multiTenantTables;
    }

    public void setMultiTenantTables(Set<String> multiTenantTables) {
        this.multiTenantTables = multiTenantTables;
    }
}