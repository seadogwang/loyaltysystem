package com.loyalty.platform.admin;

import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.security.SecurityContext;
import com.loyalty.platform.system.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证控制器 — 登录 / 登出 / Token 刷新。
 * <p>
 * 注意：/api/auth/** 路径已在 MultiTenantRbacInterceptor 白名单中，无需 Token 即可访问。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录 — 验证用户名密码，返回 JWT Token + 用户信息 + 权限列表。
     *
     * <pre>
     * Request:  { "username": "superadmin", "password": "admin123", "programCode": "PROG001" }
     * Response: { "code": "SUCCESS", "data": { "token": "eyJ...", "user": {...}, "permissions": [...] } }
     * </pre>
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String programCode = (String) body.getOrDefault("programCode", "PROG001");

        if (username == null || username.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("ERR_INVALID", "用户名不能为空"));
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("ERR_INVALID", "密码不能为空"));
        }

        try {
            Map<String, Object> result = authService.login(username, password, programCode);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (AuthService.AuthException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 获取当前用户信息（需 Token，用于页面刷新后恢复用户状态）。
     * 由拦截器解析 JWT 并注入 SecurityContext。
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(jakarta.servlet.http.HttpServletRequest request) {
        Object ctxObj = request.getAttribute("securityContext");
        if (ctxObj == null) {
            return ResponseEntity.ok(ApiResponse.error("ERR_UNAUTHORIZED", "未登录"));
        }
        SecurityContext ctx = (SecurityContext) ctxObj;
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", ctx.getUserId());
        user.put("username", ctx.getUsername());
        user.put("displayName", ctx.getUsername());
        user.put("platformRole", ctx.getRole() != null ? ctx.getRole().name() : null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", true);
        result.put("user", user);
        result.put("permissions", ctx.getPermissions() != null
                ? ctx.getPermissions().stream().map(Enum::name).toList()
                : java.util.List.of());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
