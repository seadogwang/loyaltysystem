package com.loyalty.platform.campaign.execution.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;

/**
 * Zeebe / Camunda 执行引擎配置。
 *
 * <p>开发模式：使用内置模拟引擎（ZeebeExecutionService），无需外部 Zeebe 集群。
 * 生产模式：通过 ZeebeClient 连接独立 Zeebe 集群。
 *
 * <p>生产环境配置（application.yml）：
 * <pre>
 * zeebe:
 *   client:
 *     broker:
 *       gateway-address: zeebe-gateway:26500
 *     security:
 *       plaintext: false
 *   worker:
 *     default:
 *       timeout: 30000
 *       max-jobs-active: 32
 * </pre>
 */
@Configuration
@EnableScheduling
public class ZeebeConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ZeebeConfig.class);

    @jakarta.annotation.PostConstruct
    @org.springframework.context.annotation.Profile("dev")
    public void initDevMode() {
        log.info("==============================================");
        log.info("Zeebe Execution Engine: DEV MODE (simulated)");
        log.info("Workers registered via WorkerRegistry");
        log.info("Persistence enabled via campaign_zeebe_* tables");
        log.info("==============================================");
    }

    @jakarta.annotation.PostConstruct
    @org.springframework.context.annotation.Profile("!dev")
    public void initProdMode() {
        log.info("==============================================");
        log.info("Zeebe Execution Engine: PRODUCTION MODE");
        log.info("Requires external Zeebe cluster + ZeebeClient bean");
        log.info("==============================================");
    }
}
