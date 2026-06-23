package com.loyalty.platform.spi;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.domain.entity.ChannelAdapterConfig;
import com.loyalty.platform.domain.repository.ChannelAdapterConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 统一 SPI 网关控制器。
 */
@Tag(name = "SPI Gateway", description = "外部渠道接入 — 统一 Webhook 入口（天猫/京东/抖音/微信小程序）")
@RestController
@RequestMapping("/api/open/spi/{channel}/{programCode}")
public class SpiGatewayController {

    private static final Logger log = LoggerFactory.getLogger(SpiGatewayController.class);

    private static final int MAX_BODY_SIZE = 1_048_576; // 1MB
    private ExecutorService spiThreadPool;

    private final SpiHandlerFactory handlerFactory;
    private final ChannelAdapterConfigRepository configRepo;
    private final SpiLogService spiLogService;

    public SpiGatewayController(SpiHandlerFactory handlerFactory,
                                 ChannelAdapterConfigRepository configRepo,
                                 SpiLogService spiLogService) {
        this.handlerFactory = handlerFactory;
        this.configRepo = configRepo;
        this.spiLogService = spiLogService;
    }

    @PostConstruct
    void init() {
        this.spiThreadPool = new ThreadPoolExecutor(
                4, 16, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadFactoryBuilder().setNameFormat("spi-worker-%d").setDaemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("[SpiGateway] SPI 线程池已初始化: core=4, max=16, queue=200");
    }

    @PreDestroy
    void destroy() {
        if (spiThreadPool != null) {
            spiThreadPool.shutdown();
            try { spiThreadPool.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    @Operation(summary = "统一 SPI Webhook", description = "接收外部渠道（天猫/京东/抖音/微信小程序）的订单、会员、退款等事件通知。异步处理 + 2000ms 超时隔离，失败也返回 HTTP 200")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "请求已接收（含业务失败）"),
        @ApiResponse(responseCode = "401", description = "签名验证失败"),
        @ApiResponse(responseCode = "404", description = "渠道配置不存在")
    })
    @PostMapping("/{action}")
    public ResponseEntity<Object> handleSpi(
            @Parameter(description = "渠道标识（TMALL/JD/DOUYIN/WECHAT_MINI）") @PathVariable String channel,
            @Parameter(description = "租户代码") @PathVariable String programCode,
            @Parameter(description = "操作类型（order.paid / member.enroll / refund.notify 等）") @PathVariable String action,
            HttpServletRequest request) {

        long startTime = System.currentTimeMillis();
        String requestId = request.getHeader("X-Request-Id");
        byte[] rawBody = readBody(request);

        TenantContext.set(programCode);

        try {
            ChannelSpiHandler handler = handlerFactory.getHandler(channel);

            var configOpt = configRepo.findActiveByProgramAndChannel(programCode, channel);
            if (configOpt.isEmpty()) {
                spiLogService.logFailed(channel, programCode, action, requestId,
                        "CONFIG_NOT_FOUND", new String(rawBody), "渠道配置不存在或未激活");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("code", "ERR_CONFIG_NOT_FOUND", "message", "Channel config not found"));
            }
            ChannelAdapterConfig config = configOpt.get();

            if (!handler.verifySignature(request, rawBody, config)) {
                spiLogService.logFailed(channel, programCode, action, requestId,
                        "SIGN_FAILED", new String(rawBody), "签名验证失败");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("code", "ERR_SIGN_FAILED", "message", "Signature Invalid"));
            }

            final ChannelSpiHandler finalHandler = handler;
            final ChannelAdapterConfig finalConfig = config;
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                TenantContext.set(programCode);
                try {
                    return finalHandler.handleAction(action, programCode, rawBody, finalConfig);
                } finally {
                    TenantContext.clear();
                }
            }, spiThreadPool);

            try {
                Object spiResponse = future.get(2000, TimeUnit.MILLISECONDS);
                int elapsed = (int) (System.currentTimeMillis() - startTime);
                spiLogService.logSuccess(channel, programCode, action, requestId,
                        null, new String(rawBody), String.valueOf(spiResponse), elapsed);
                return ResponseEntity.ok(spiResponse);

            } catch (TimeoutException te) {
                int elapsed = (int) (System.currentTimeMillis() - startTime);
                log.error("[SpiGateway] SPI 响应超时: channel={}, action={}, elapsed={}ms", channel, action, elapsed);
                spiLogService.logFailed(channel, programCode, action, requestId,
                        "TIMEOUT", new String(rawBody), "处理超时(>2000ms)");
                return ResponseEntity.ok(handler.buildErrorResponse(
                        new RuntimeException("SPI processing timeout")));

            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                log.error("[SpiGateway] SPI 业务异常: channel={}, action={}", channel, action, cause);
                spiLogService.logFailed(channel, programCode, action, requestId,
                        "BUSINESS_ERROR", new String(rawBody), cause.getMessage());
                return ResponseEntity.ok(handler.buildErrorResponse(
                        cause instanceof Exception ? (Exception) cause : new RuntimeException(cause)));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResponseEntity.ok(handler.buildErrorResponse(e));
            }

        } catch (Exception e) {
            spiLogService.logFailed(channel, programCode, action, requestId,
                    "INIT_ERROR", new String(rawBody), e.getMessage());
            return ResponseEntity.ok(Map.of("code", "ERR_INIT_FAILED", "message", e.getMessage()));
        } finally {
            TenantContext.clear();
        }
    }

    private byte[] readBody(HttpServletRequest request) {
        try (InputStream is = request.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            int total = 0;
            while ((len = is.read(buf)) != -1) {
                total += len;
                if (total > MAX_BODY_SIZE) {
                    log.warn("[SpiGateway] 请求体超过大小限制 {} bytes，已截断: uri={}",
                            MAX_BODY_SIZE, request.getRequestURI());
                    int allowed = len - (total - MAX_BODY_SIZE);
                    if (allowed > 0) {
                        bos.write(buf, 0, allowed);
                    }
                    break;
                }
                bos.write(buf, 0, len);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("[SpiGateway] 读取请求体失败", e);
            return new byte[0];
        }
    }
}
