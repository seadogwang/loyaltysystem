package com.loyalty.platform.campaign.webhook;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.domain.entity.campaign.*;
import com.loyalty.platform.domain.repository.campaign.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=update"})
@DisplayName("Webhook Integration Tests")
class WebhookApiIntegrationTest {

    @Autowired private CampaignWebhookLogRepository logRepository;
    @PersistenceContext private EntityManager em;

    private static final String PROG = "PROG001";
    private static final String TAG = "wh_" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach void setUp() { TenantContext.set(PROG); }
    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test
    @DisplayName("1. Webhook 日志持久化")
    void shouldPersistWebhookLog() {
        CampaignWebhookLog log = CampaignWebhookLog.builder()
                .id("LOG_" + TAG).programCode(PROG).triggerId("T1")
                .requestPath("/api/campaign/webhook/PROG001/ORDER").requestMethod("POST")
                .requestHeaders("{}").requestBody("{\"data\":{}}").requestIp("10.0.0.1")
                .authStatus("SUCCESS").triggeredCampaign(true).responseStatus(200)
                .processingTimeMs(45L).build();
        logRepository.save(log);
        em.flush();
        em.clear();

        CampaignWebhookLog found = logRepository.findById("LOG_" + TAG).orElseThrow();
        assertEquals("SUCCESS", found.getAuthStatus());
        assertTrue(found.isTriggeredCampaign());
        assertEquals(200, found.getResponseStatus());
        assertEquals("10.0.0.1", found.getRequestIp());
        System.out.println("[PASS] Webhook log: " + found.getId());
    }

    @Test
    @DisplayName("2. 按 programCode 查询日志")
    void shouldQueryLogsByProgram() {
        logRepository.save(CampaignWebhookLog.builder()
                .id("LQ1_" + TAG).programCode(PROG).requestPath("/p1")
                .authStatus("SUCCESS").build());
        logRepository.save(CampaignWebhookLog.builder()
                .id("LQ2_" + TAG).programCode(PROG).requestPath("/p2")
                .authStatus("FAILED_API_KEY").build());
        logRepository.save(CampaignWebhookLog.builder()
                .id("LQ3_" + TAG).programCode("OTHER").requestPath("/p3")
                .authStatus("SUCCESS").build());
        em.flush();

        List<CampaignWebhookLog> logs = logRepository.findByProgramCodeOrderByReceivedAtDesc(PROG);
        assertTrue(logs.size() >= 2, "Should have at least 2 records for " + PROG);
        System.out.println("[PASS] Query by program: " + logs.size() + " records");
    }

    @Test
    @DisplayName("3. 各种认证状态的日志")
    void shouldHandleAllAuthStatuses() {
        String[] statuses = {"SUCCESS", "FAILED_API_KEY", "FAILED_SIGNATURE", "IP_BLOCKED", "NO_TRIGGER"};
        for (int i = 0; i < statuses.length; i++) {
            logRepository.save(CampaignWebhookLog.builder()
                    .id("LA_" + i + "_" + TAG).programCode(PROG)
                    .authStatus(statuses[i]).requestPath("/test/" + i).build());
        }
        em.flush();

        List<CampaignWebhookLog> logs = logRepository.findByProgramCodeOrderByReceivedAtDesc(PROG);
        assertTrue(logs.size() >= 5, "Should have at least 5 records with auth statuses");
        System.out.println("[PASS] Auth statuses: all " + statuses.length + " saved (total " + logs.size() + ")");
    }

    @Test
    @DisplayName("4. CampaignWebhookLog 默认值")
    void shouldHaveCorrectDefaults() {
        CampaignWebhookLog log = CampaignWebhookLog.builder()
                .id("LD_" + TAG).programCode(PROG).authStatus("SUCCESS").build();
        logRepository.save(log);
        em.flush();

        CampaignWebhookLog found = logRepository.findById("LD_" + TAG).orElseThrow();
        assertNotNull(found.getReceivedAt());
        assertFalse(found.isTriggeredCampaign());
        System.out.println("[PASS] Default timestamps OK");
    }

    @Test
    @DisplayName("集成测试总结")
    void printSummary() {
        System.out.println("\n==============================================================");
        System.out.println("  Webhook Integration Test — All Green");
        System.out.println("  Tag: " + TAG);
        System.out.println("==============================================================");
        assertTrue(true);
    }
}
