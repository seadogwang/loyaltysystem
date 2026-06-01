package com.loyalty.saas.security;

import java.util.Collections;
import java.util.Set;

/**
 * 当前请求的安全上下文 — 从 JWT Token 解析后存储。
 *
 * <p>由 {@link MultiTenantRbacInterceptor} 从 Authorization Header
 * 的 Bearer Token 中解析并注入到请求属性中。
 */
public class SecurityContext {

    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 用户角色 */
    private PlatformRole role;
    /** 用户所属租户 — 关键：用于 TenantContext 注入 */
    private String programCode;
    /** 细粒度权限集 */
    private Set<OperationPermission> permissions = Collections.emptySet();
    /** 令牌过期时间 */
    private long tokenExpiry;

    // ---- Getters/Setters ----
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public PlatformRole getRole() { return role; }
    public void setRole(PlatformRole role) { this.role = role; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public Set<OperationPermission> getPermissions() { return permissions; }
    public void setPermissions(Set<OperationPermission> permissions) { this.permissions = permissions; }

    public long getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(long tokenExpiry) { this.tokenExpiry = tokenExpiry; }

    /** 是否拥有指定的操作权限 */
    public boolean hasPermission(OperationPermission p) {
        return role == PlatformRole.SUPER_ADMIN || permissions.contains(p);
    }

    /** Token 是否已过期 */
    public boolean isTokenExpired() {
        return System.currentTimeMillis() > tokenExpiry;
    }

    @Override public String toString() {
        return "SecurityContext{userId=" + userId + ", role=" + role + ", program=" + programCode + "}";
    }
}