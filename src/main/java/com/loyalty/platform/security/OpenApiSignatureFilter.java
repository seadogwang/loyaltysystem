package com.loyalty.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.common.dto.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商户 Open API 签名验签过滤器 — HMAC-SHA256 + Nonce + Timestamp 防重放。
 *
 * <p>适用范围：{@code /api/open/**} 路径，面向第三方自建系统或小程序。
 *
 * <p>严格遵循设计文档第十章 10.2 节的通用 Header 约束：
 * <table>
 *   <tr><td>{@code X-Program-Code}</td><td>租户标识</td></tr>
 *   <tr><td>{@code X-Timestamp}</td><td>请求时间戳（Unix 毫秒）</td></tr>
 *   <tr><td>{@code X-Nonce}</td><td>随机字符串（防重放，15 分钟内有效）</td></tr>
 *   <tr><td>{@code X-Signature}</td><td>HMAC-SHA256 签名</td></tr>
 * </table>
 *
 * <p><b>签名算法</b>：
 * <pre>
 * stringToSign = HTTP_METHOD + "\n"
 *              + URI_PATH + "\n"
 *              + TIMESTAMP + "\n"
 *              + NONCE + "\n"
 *              + BODY
 * signature = Base64(HMAC-SHA256(AppSecret, stringToSign))
 * </pre>
 *
 * <p><b>重放攻击防护</b>：
 * <ul>
 *   <li>Nonce 在 15 分钟内唯一（内存 LRU 缓存）</li>
 *   <li>Timestamp 误差不超过 5 分钟</li>
 * </ul>
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class OpenApiSignatureFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSignatureFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 时间戳误差容忍（毫秒） */
    private static final long TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000;
    /** Nonce 有效期（毫秒） */
    private static final long NONCE_TTL_MS = 15 * 60 * 1000;

    /** Nonce 缓存：nonce → 首次使用时间戳 */
    private final ConcurrentHashMap<String, Long> nonceCache = new ConcurrentHashMap<>();

    /** Open API 路径前缀 */
    private static final String OPEN_API_PREFIX = "/api/open/";
    private static final String SPI_API_PREFIX = "/api/open/spi/";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // 仅拦截 /api/open/** 路径；SPI 路径使用渠道特定 HMAC，由此过滤器跳过
        if (!path.startsWith(OPEN_API_PREFIX) || path.startsWith(SPI_API_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String method = httpRequest.getMethod();
        String programCode = httpRequest.getHeader("X-Program-Code");
        String timestampStr = httpRequest.getHeader("X-Timestamp");
        String nonce = httpRequest.getHeader("X-Nonce");
        String signature = httpRequest.getHeader("X-Signature");

        // ---- Step 1: 必填 Header 校验 ----
        if (isBlank(programCode) || isBlank(timestampStr) || isBlank(nonce) || isBlank(signature)) {
            log.warn("[OpenAPI] 缺失必填 Header: program={}, ts={}, nonce={}, sign={}",
                    programCode, timestampStr, nonce, signature != null);
            writeError(httpResponse, HttpServletResponse.SC_BAD_REQUEST,
                    "ERR_MISSING_OPENAPI_HEADERS", "缺失必填 Header: X-Program-Code, X-Timestamp, X-Nonce, X-Signature");
            return;
        }

        // ---- Step 2: Timestamp 时效校验 ----
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            writeError(httpResponse, HttpServletResponse.SC_BAD_REQUEST,
                    "ERR_INVALID_TIMESTAMP", "X-Timestamp 格式无效");
            return;
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > TIMESTAMP_TOLERANCE_MS) {
            log.warn("[OpenAPI] 时间戳超时: program={}, diff={}ms", programCode, Math.abs(now - timestamp));
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "ERR_TIMESTAMP_EXPIRED", "请求时间戳已过期(>5分钟)");
            return;
        }

        // ---- Step 3: Nonce 防重放 ----
        // 清理过期 Nonce
        nonceCache.entrySet().removeIf(e -> now - e.getValue() > NONCE_TTL_MS);

        if (nonceCache.containsKey(nonce)) {
            log.warn("[OpenAPI] Nonce 重复使用: program={}, nonce={}", programCode, nonce);
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "ERR_NONCE_REUSED", "Nonce 已被使用（重放攻击拦截）");
            return;
        }
        nonceCache.put(nonce, now);

        // ---- Step 4: 读取请求体 ----
        byte[] rawBody = readBody(httpRequest);

        // ---- Step 5: 获取 AppSecret ----
        String appSecret = getAppSecret(programCode);
        if (appSecret == null) {
            log.warn("[OpenAPI] 租户 [{}] 的 AppSecret 未配置", programCode);
            writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "ERR_APP_SECRET_NOT_FOUND", "租户 AppSecret 未配置");
            return;
        }

        // ---- Step 6: HMAC-SHA256 验签 ----
        String stringToSign = method.toUpperCase() + "\n"
                + path + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + new String(rawBody, StandardCharsets.UTF_8);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String computedSign = Base64.getEncoder().encodeToString(computed);

            if (!MessageDigest.isEqual(
                    computedSign.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("[OpenAPI] 签名验证失败: program={}, path={}", programCode, path);
                writeError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                        "ERR_SIGNATURE_INVALID", "签名验证失败");
                return;
            }
        } catch (Exception e) {
            log.error("[OpenAPI] 验签异常: program={}", programCode, e);
            writeError(httpResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "ERR_SIGNATURE_PROCESSING", "验签处理异常");
            return;
        }

        // ---- Step 7: 注入租户上下文 + 放行 ----
        TenantContext.set(programCode);
        try {
            log.debug("[OpenAPI] 验签通过: program={}, path={}, nonce={}", programCode, path, nonce);
            chain.doFilter(request, response);
        } finally {
            // TenantContext清除由TenantContextFilter统一负责
        }
    }

    // ==================== 辅助方法 ====================

    private String getAppSecret(String programCode) {
        // 从 DB 或缓存获取（骨架）
        try {
            // 实际应查询 channel_adapter_config 或 tenant 表的 api_key
            // 这里简化返回固定密钥
            return "openapi-secret-for-" + programCode;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readBody(HttpServletRequest request) {
        try (InputStream is = request.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
            return bos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(
                ApiResponse.error(code, message)));
    }
}