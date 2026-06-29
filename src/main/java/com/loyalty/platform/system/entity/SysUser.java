package com.loyalty.platform.system.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Column(name = "real_name", length = 128)
    private String realName;

    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "platform_role", nullable = false, length = 32)
    @Builder.Default
    private String platformRole = "OPERATOR";

    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "program_code", nullable = false, length = 64)
    private String programCode;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
