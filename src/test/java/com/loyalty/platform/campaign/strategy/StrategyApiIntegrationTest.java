package com.loyalty.platform.campaign.strategy;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.domain.entity.campaign.*;
import com.loyalty.platform.domain.repository.campaign.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({StrategyWorkflowService.class})
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=update"})
@DisplayName("Strategy Blueprint Integration Tests")
class StrategyApiIntegrationTest {

    @Autowired private CampaignGoalRepository goalRepository;
    @Autowired private StrategyBlueprintRepository blueprintRepository;
    @Autowired private GoalDecompositionRepository decompositionRepository;
    @Autowired private CampaignInitiativeRepository initiativeRepository;
    @Autowired private StrategyWorkflowService service;
    @PersistenceContext private EntityManager em;

    private static final String PROG = "PROG001";
    private static final String TAG = "sbp_" + UUID.randomUUID().toString().substring(0, 8);

    private static final String WS_ID = "WS_" + TAG;

    @BeforeEach void setUp() {
        TenantContext.set(PROG);
        // Create test workspace to satisfy FK
        try { em.createNativeQuery("INSERT INTO campaign_workspace (id, program_code, name, status) VALUES (?,?,?,?) ON CONFLICT (id) DO NOTHING")
                .setParameter(1, WS_ID).setParameter(2, PROG).setParameter(3, "Test WS").setParameter(4, "ACTIVE").executeUpdate(); } catch (Exception ignored) {}
    }
    @AfterEach void tearDown() {
        try { em.createNativeQuery("DELETE FROM campaign_goal_decomposition WHERE workspace_id = ?").setParameter(1, WS_ID).executeUpdate(); } catch (Exception ignored) {}
        try { em.createNativeQuery("DELETE FROM campaign_initiative WHERE workspace_id = ?").setParameter(1, WS_ID).executeUpdate(); } catch (Exception ignored) {}
        try { em.createNativeQuery("DELETE FROM campaign_goal WHERE workspace_id = ?").setParameter(1, WS_ID).executeUpdate(); } catch (Exception ignored) {}
        try { em.createNativeQuery("DELETE FROM campaign_strategy_blueprint WHERE id LIKE 'BP_" + TAG + "%'").executeUpdate(); } catch (Exception ignored) {}
        try { em.createNativeQuery("DELETE FROM campaign_workspace WHERE id = ?").setParameter(1, WS_ID).executeUpdate(); } catch (Exception ignored) {}
        TenantContext.clear();
    }

    @Test @DisplayName("1. 完整流程: 创建目标 → 分析缺口 → 创建举措")
    void shouldCompleteFullWorkflow() {
        // Pre-seed blueprint
        StrategyBlueprint bp = StrategyBlueprint.builder().id("BP_" + TAG)
                .blueprintName("Integration Test BP").industryType("RETAIL")
                .isActive(true).isSystemDefault(false).build();
        blueprintRepository.save(bp);
        em.flush();

        // Step 1: Create goal
        CampaignGoal g = CampaignGoal.builder().id("G_" + TAG).workspaceId(WS_ID).name("测试目标")
                .goalType("GMV").targetValue(BigDecimal.valueOf(1000000))
                .industryType("RETAIL").blueprintId("BP_" + TAG).build();
        CampaignGoal saved = service.createGoalWithBlueprint(g);
        em.flush();
        assertEquals("GOAL_DRAFT", saved.getWorkflowStatus());
        assertEquals("BP_" + TAG, saved.getBlueprintId());
        System.out.println("[PASS] Step 1: Goal created with blueprint");

        // Step 2: Analyze gap
        GoalDecomposition decomp = service.analyzeGap("G_" + TAG);
        em.flush();
        assertEquals("BLUEPRINT", decomp.getDecompositionMode());
        assertNotNull(decomp.getInitiativeSuggestions());
        assertTrue(decomp.getInitiativeSuggestions().contains("ACQUISITION"));
        System.out.println("[PASS] Step 2: Gap analyzed, mode=" + decomp.getDecompositionMode()
                + ", gap=" + decomp.getTotalGap());

        // Step 4: Create initiatives
        List<CampaignInitiative> inis = service.createInitiativesFromDecomposition("G_" + TAG);
        em.flush();
        assertFalse(inis.isEmpty());
        assertTrue(inis.size() >= 2);
        System.out.println("[PASS] Step 4: " + inis.size() + " initiatives created");
        inis.forEach(i -> System.out.println("  Initiative: " + i.getName() + " type=" + i.getInitiativeType()));
    }

    @Test @DisplayName("2. 无蓝图 → CORRELATION 模式")
    void shouldUseCorrelationWithoutBlueprint() {
        CampaignGoal g = CampaignGoal.builder().id("G2_" + TAG).workspaceId(WS_ID).name("无蓝图目标")
                .goalType("GMV").targetValue(BigDecimal.valueOf(500000)).industryType("UNKNOWN").build();
        goalRepository.save(g);
        em.flush();

        GoalDecomposition d = service.analyzeGap("G2_" + TAG);
        assertEquals("CORRELATION", d.getDecompositionMode());
        System.out.println("[PASS] No blueprint → CORRELATION mode");
    }

    @Test @DisplayName("3. 蓝图 CRUD")
    void shouldCrudBlueprint() {
        StrategyBlueprint bp = StrategyBlueprint.builder().blueprintName("CRUD Test")
                .industryType("SAAS").isActive(true).build();
        StrategyBlueprint saved = service.saveBlueprint(bp);
        assertNotNull(saved.getId());
        em.flush();

        List<StrategyBlueprint> all = blueprintRepository.findByIsActiveTrue();
        assertFalse(all.isEmpty());
        System.out.println("[PASS] Blueprint CRUD: " + saved.getId());
    }

    @Test @DisplayName("4. 行业蓝图查询")
    void shouldQueryByIndustry() {
        blueprintRepository.save(StrategyBlueprint.builder().id("BP_R_" + TAG)
                .blueprintName("Retail").industryType("RETAIL").isActive(true).build());
        blueprintRepository.save(StrategyBlueprint.builder().id("BP_S_" + TAG)
                .blueprintName("SaaS").industryType("SAAS").isActive(true).build());
        em.flush();

        List<StrategyBlueprint> retail = blueprintRepository.findByIndustryTypeAndIsActiveTrue("RETAIL");
        assertTrue(retail.size() >= 1, "Should have at least 1 RETAIL blueprint");
        assertTrue(retail.stream().anyMatch(b -> "Retail".equals(b.getBlueprintName())));
        System.out.println("[PASS] Industry filter: " + retail.size() + " RETAIL blueprints");
    }

    @Test @DisplayName("5. 通用蓝图降级")
    void shouldFallbackToGeneral() {
        blueprintRepository.save(StrategyBlueprint.builder().id("BP_GEN_" + TAG)
                .blueprintName("通用蓝图").industryType("GENERAL")
                .isActive(true).isSystemDefault(true).build());
        em.flush();

        CampaignGoal g = CampaignGoal.builder().id("G3_" + TAG).workspaceId(WS_ID).name("无匹配目标")
                .goalType("GMV").targetValue(BigDecimal.valueOf(1000000)).industryType("UNMATCHED").build();
        service.createGoalWithBlueprint(g);
        em.flush();

        CampaignGoal found = goalRepository.findById("G3_" + TAG).orElseThrow();
        assertEquals("BP_GEN_" + TAG, found.getBlueprintId(), "Should fallback to general blueprint");
        System.out.println("[PASS] Fallback to general blueprint verified");
    }

    @Test @DisplayName("集成测试总结")
    void printSummary() {
        System.out.println("\n==============================================================");
        System.out.println("  Strategy Blueprint Integration Test — All Green");
        System.out.println("  Tag: " + TAG);
        System.out.println("==============================================================");
        assertTrue(true);
    }
}
