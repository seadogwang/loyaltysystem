package com.loyalty.platform.api;

import com.loyalty.platform.api.service.MemberService;
import com.loyalty.platform.api.service.ProgramSchemaService;
import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.domain.entity.Member;
import com.loyalty.platform.domain.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProgramSchemaService.class, MemberService.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired private ProgramSchemaService schemaService;
    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepo;
    @PersistenceContext private EntityManager em;

    private static final String PROG = "PROG001";
    private static final Long TEST_MEMBER = 999999L;

    @BeforeEach
    void setUp() {
        TenantContext.set(PROG);
        em.createNativeQuery("SET app.current_program_code = '" + PROG + "'").executeUpdate();
    }

    @AfterEach
    void tearDown() {
        em.createNativeQuery("DELETE FROM member WHERE program_code = ? AND member_id = ?")
                .setParameter(1, PROG).setParameter(2, TEST_MEMBER).executeUpdate();
        TenantContext.clear();
    }

    @Test @Order(1)
    @DisplayName("Schema 版本获取")
    void getCurrentSchemaVersion() {
        String version = schemaService.getCurrentVersion(PROG, "MEMBER");
        assertNotNull(version);
        System.out.println("[TEST] Current schema version: " + version);

        Map<String, Object> schema = schemaService.getCurrentSchema(PROG, "MEMBER");
        assertNotNull(schema);
    }

    @Test @Order(2)
    @DisplayName("废弃字段引用检查")
    void checkFieldReferences() {
        var refs = schemaService.getFieldRuleReferences(PROG, "shoe_size");
        assertNotNull(refs);
        // 如果当前没有规则引用 shoe_size，则 safe_to_deprecate = true
        System.out.println("[TEST] Field 'shoe_size' references: " + refs.size());
    }

    @Test @Order(3)
    @DisplayName("创建会员 → schema_version 双写")
    void createMemberDoubleWrite() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("pet_name", "测试猫");
        ext.put("age", 3);

        Member m = memberService.createMember(PROG, TEST_MEMBER, "SILVER", ext);
        assertNotNull(m);
        assertEquals(TEST_MEMBER, m.getMemberId());
        assertEquals("SILVER", m.getTierCode());

        // 验证双写
        assertNotNull(m.getSchemaVersion(), "独立字段 schema_version 不能为空");
        assertNotNull(m.getExtAttributes().get("_schema_version"),
                "ext_attributes._schema_version 不能为空");
        assertEquals(m.getSchemaVersion(), m.getExtAttributes().get("_schema_version"),
                "双写值应一致");

        System.out.println("[TEST] Created: schemaVersion=" + m.getSchemaVersion()
                + ", ext._schema_version=" + m.getExtAttributes().get("_schema_version"));
    }

    @Test @Order(4)
    @DisplayName("更新会员 → schema_version 更新")
    void updateMemberDoubleWrite() {
        // 先创建（使用 mutable map）
        memberService.createMember(PROG, TEST_MEMBER, "BASE", new LinkedHashMap<>(Map.of("initial", true)));

        // 再更新
        Map<String, Object> newExt = new LinkedHashMap<>();
        newExt.put("pet_name", "已更新");
        newExt.put("level", 5);

        Member updated = memberService.updateMember(PROG, TEST_MEMBER, newExt);
        assertNotNull(updated.getSchemaVersion());
        assertEquals("已更新", updated.getExtAttributes().get("pet_name"));
        assertEquals(5, updated.getExtAttributes().get("level"));
        assertNotNull(updated.getExtAttributes().get("_schema_version"));

        System.out.println("[TEST] Updated: schemaVersion=" + updated.getSchemaVersion()
                + ", ext=" + updated.getExtAttributes());
    }

    @Test @Order(5)
    @DisplayName("查询会员 → 租户隔离校验")
    void queryMemberWithTenantIsolation() {
        memberService.createMember(PROG, TEST_MEMBER, "BASE", new LinkedHashMap<>(Map.of("test", true)));

        // 当前租户 PROG001 应能查到
        var found = memberRepo.findByMemberId(PROG, TEST_MEMBER);
        assertTrue(found.isPresent());
        assertTrue(found.get().getExtAttributes().get("_schema_version") != null);
    }

    @Test @Order(6)
    @DisplayName("SchemaService.injectSchemaVersion 注入元字段")
    void injectSchemaVersion() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("custom_field", "hello");
        schemaService.injectSchemaVersion(ext, PROG, "MEMBER");
        assertTrue(ext.containsKey("_schema_version"));
        System.out.println("[TEST] Injected version: " + ext.get("_schema_version"));
    }
}