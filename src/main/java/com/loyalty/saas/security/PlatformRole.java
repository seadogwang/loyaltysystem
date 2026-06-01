package com.loyalty.saas.security;

/**
 * 平台角色枚举 — 多维度 RBAC 权限定义。
 *
 * <p>角色层级（权限递减）：
 * <ol>
 *   <li>{@code SUPER_ADMIN} — 系统超级管理员：跨租户管理、系统配置、全局审计</li>
 *   <li>{@code TENANT_ADMIN} — 品牌（租户）管理员：Program 配置、规则发布、会员管理</li>
 *   <li>{@code STORE_MANAGER} — 门店店长：会员查询、积分操作（受限额度）、报表查看</li>
 *   <li>{@code FINANCE_AUDITOR} — 财务审计员：只读模式，对账报表、审计日志、导出</li>
 *   <li>{@code OPERATOR} — 运营人员：会员管理、活动配置、渠道管理</li>
 * </ol>
 */
public enum PlatformRole {

    SUPER_ADMIN,
    TENANT_ADMIN,
    STORE_MANAGER,
    FINANCE_AUDITOR,
    OPERATOR;

    /**
     * 是否拥有跨租户访问权限。
     * 仅 SUPER_ADMIN 可跨租户操作。
     */
    public boolean isCrossTenant() {
        return this == SUPER_ADMIN;
    }

    /**
     * 是否拥有写入权限。
     * FINANCE_AUDITOR 为只读角色。
     */
    public boolean isReadOnly() {
        return this == FINANCE_AUDITOR;
    }

    /**
     * 从字符串解析角色。
     */
    public static PlatformRole fromString(String role) {
        if (role == null) return null;
        try {
            return valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}