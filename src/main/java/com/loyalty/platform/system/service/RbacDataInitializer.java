package com.loyalty.platform.system.service;

import com.loyalty.platform.security.OperationPermission;
import com.loyalty.platform.system.entity.SysRole;
import com.loyalty.platform.system.entity.SysRolePermission;
import com.loyalty.platform.system.entity.SysUser;
import com.loyalty.platform.system.repository.SysRolePermissionRepository;
import com.loyalty.platform.system.repository.SysRoleRepository;
import com.loyalty.platform.system.repository.SysUserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * RBAC 数据初始化 — 首次启动时创建默认超级管理员用户和默认角色。
 * <p>
 * 仅在数据不存在时创建，不会覆盖已有数据。
 */
@Component
public class RbacDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RbacDataInitializer.class);

    private final SysUserRepository userRepo;
    private final SysRoleRepository roleRepo;
    private final SysRolePermissionRepository rolePermissionRepo;

    public RbacDataInitializer(SysUserRepository userRepo,
                               SysRoleRepository roleRepo,
                               SysRolePermissionRepository rolePermissionRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.rolePermissionRepo = rolePermissionRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedSuperAdmin();
            seedDefaultRoles();
        } catch (Exception e) {
            log.warn("[RbacInit] 数据初始化异常（可能表尚未创建，Flyway 未执行）: {}", e.getMessage());
        }
    }

    private void seedSuperAdmin() {
        if (userRepo.existsByProgramCodeAndUsername("*", "superadmin")) {
            log.debug("[RbacInit] SUPER_ADMIN 用户已存在，跳过");
            return;
        }

        SysUser superAdmin = SysUser.builder()
                .username("superadmin")
                .passwordHash(BCrypt.hashpw("admin123", BCrypt.gensalt(10)))
                .realName("系统超级管理员")
                .platformRole("SUPER_ADMIN")
                .status("ACTIVE")
                .programCode("*")
                .build();

        userRepo.save(superAdmin);
        log.info("[RbacInit] 默认 SUPER_ADMIN 用户已创建: superadmin / admin123");
    }

    private void seedDefaultRoles() {
        String programCode = "PROG001";

        // 租户管理员角色
        createRoleIfNotExists(programCode, "TENANT_ADMIN", "TENANT_ADMIN",
                "租户管理员 — 拥有该租户所有权限",
                OperationPermission.getPermissionsForRole(
                        com.loyalty.platform.security.PlatformRole.TENANT_ADMIN));

        // 门店店长角色
        createRoleIfNotExists(programCode, "STORE_MANAGER", "STORE_MANAGER",
                "门店店长 — 会员查询 + 有限积分操作",
                OperationPermission.getPermissionsForRole(
                        com.loyalty.platform.security.PlatformRole.STORE_MANAGER));

        // 运营人员角色
        createRoleIfNotExists(programCode, "OPERATOR", "OPERATOR",
                "运营人员 — 会员管理、活动配置、渠道管理",
                OperationPermission.getPermissionsForRole(
                        com.loyalty.platform.security.PlatformRole.OPERATOR));

        // 财务审计角色
        createRoleIfNotExists(programCode, "FINANCE_AUDITOR", "FINANCE_AUDITOR",
                "财务审计员 — 只读模式，审计报表与导出",
                OperationPermission.getPermissionsForRole(
                        com.loyalty.platform.security.PlatformRole.FINANCE_AUDITOR));
    }

    private void createRoleIfNotExists(String programCode, String roleCode,
                                        String roleName, String description,
                                        Set<OperationPermission> permissions) {
        if (roleRepo.existsByProgramCodeAndRoleCode(programCode, roleCode)) {
            log.debug("[RbacInit] 角色已存在: {}", roleCode);
            return;
        }

        SysRole role = SysRole.builder()
                .roleName(roleName)
                .roleCode(roleCode)
                .description(description)
                .programCode(programCode)
                .dataScope("TENANT")
                .isSystem(true)
                .build();

        role = roleRepo.save(role);

        for (OperationPermission p : permissions) {
            rolePermissionRepo.save(SysRolePermission.builder()
                    .roleId(role.getId())
                    .permissionCode(p.name())
                    .build());
        }

        log.info("[RbacInit] 默认角色已创建: {} ({} 个权限)", roleCode, permissions.size());
    }
}
