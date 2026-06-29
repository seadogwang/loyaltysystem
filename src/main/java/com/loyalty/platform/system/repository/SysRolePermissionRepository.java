package com.loyalty.platform.system.repository;

import com.loyalty.platform.system.entity.SysRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Long> {

    List<SysRolePermission> findAllByRoleId(Long roleId);

    @Transactional
    void deleteByRoleId(Long roleId);
}
