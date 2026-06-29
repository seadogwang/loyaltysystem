package com.loyalty.platform.system.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_role")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "data_scope", length = 16)
    @Builder.Default
    private String dataScope = "TENANT";

    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = false;

    @Column(name = "program_code", nullable = false, length = 64)
    private String programCode;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
