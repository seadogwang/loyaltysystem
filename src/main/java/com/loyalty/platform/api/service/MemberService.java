package com.loyalty.platform.api.service;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.domain.entity.Member;
import com.loyalty.platform.domain.entity.MemberUniqueKey;
import com.loyalty.platform.domain.repository.MemberRepository;
import com.loyalty.platform.domain.repository.MemberUniqueKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);
    private final MemberRepository memberRepo;
    private final MemberUniqueKeyRepository uniqueKeyRepo;
    private final SchemaService schemaService;

    public MemberService(MemberRepository memberRepo, MemberUniqueKeyRepository uniqueKeyRepo,
                         SchemaService schemaService) {
        this.memberRepo = memberRepo;
        this.uniqueKeyRepo = uniqueKeyRepo;
        this.schemaService = schemaService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Member createMember(String programCode, Long memberId, String tierCode,
                                Map<String, Object> extAttributes) {
        final String pc = resolveTenant(programCode);

        if (memberId == null) {
            memberId = System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
        }
        if (memberRepo.findByMemberId(pc, memberId).isPresent()) {
            throw new BusinessException("ERR_MEMBER_EXISTS", "Member exists: " + memberId);
        }

        String sv = schemaService.getCurrentVersion(pc, "MEMBER");
        schemaService.injectSchemaVersion(extAttributes, pc, "MEMBER");

        Member m = Member.builder()
                .programCode(pc).memberId(memberId)
                .tierCode(tierCode != null ? tierCode : "BASE")
                .status("ENROLLED")
                .extAttributes(extAttributes != null ? extAttributes : Map.of())
                .schemaVersion(sv)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        memberRepo.save(m);
        // §3.4.2 写入唯一键：将手机号同步到 member_unique_key
        bindMobileUniqueKey(pc, memberId, extAttributes);
        log.info("[Member] Created: program={}, memberId={}, schema={}", pc, memberId, sv);
        return m;
    }

    @Transactional(rollbackFor = Exception.class)
    public Member updateMember(String programCode, Long memberId, Map<String, Object> extAttributes) {
        final String pc = resolveTenant(programCode);

        Member member = memberRepo.findByMemberId(pc, memberId)
                .orElseThrow(() -> new BusinessException("ERR_MEMBER_NOT_FOUND",
                        "Member not found: " + pc + "/" + memberId));

        schemaService.injectSchemaVersion(extAttributes, pc, "MEMBER");
        String sv = schemaService.getCurrentVersion(pc, "MEMBER");
        member.setExtAttributes(extAttributes);
        member.setSchemaVersion(sv);
        member.setUpdatedAt(LocalDateTime.now());
        memberRepo.save(member);
        // §3.4.2 同步唯一键：手机号变更时写入 member_unique_key
        bindMobileUniqueKey(pc, memberId, extAttributes);
        log.info("[Member] Updated: program={}, memberId={}, schema={}", pc, memberId, sv);
        return member;
    }

    private String resolveTenant(String programCode) {
        return (programCode != null && !programCode.isBlank())
                ? programCode : TenantContext.getRequired();
    }

    // ==================== 唯一键同步 (§3.4.2) ====================

    /**
     * 从 extAttributes 提取手机号，规范化后写入 member_unique_key（MOBILE_PLAIN）。
     * §3.4.2 要求所有可用标识在入会/更新时写入辅助唯一键表。
     */
    private void bindMobileUniqueKey(String programCode, Long memberId, Map<String, Object> ext) {
        if (ext == null) return;
        String raw = (String) ext.get("mobile");
        if (raw == null) raw = (String) ext.get("phone");
        String normalized = normalizePhoneNumber(raw);
        if (normalized == null) return;

        try {
            uniqueKeyRepo.save(MemberUniqueKey.builder()
                .programCode(programCode)
                .keyCombination("MOBILE_PLAIN")
                .keyValue(normalized)
                .memberId(memberId)
                .isStrong(true)
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .build());
        } catch (DataIntegrityViolationException e) {
            log.warn("[MemberService] 唯一键冲突，跳过: program={}, memberId={}, mobile={}",
                programCode, memberId, normalized);
        }
    }

    /**
     * 规范化为纯数字，去除 +86、空格、横线等。与 OneIdEnrollmentService 逻辑一致。
     */
    static String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String normalized = phone.replaceAll("[^\\d]", "");
        if (normalized.isEmpty()) return null;
        if (normalized.startsWith("86") && normalized.length() > 11) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }
}