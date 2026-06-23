package com.loyalty.platform.api.service;

import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.domain.entity.Member;
import com.loyalty.platform.domain.entity.MemberAccount;
import com.loyalty.platform.domain.entity.MemberUniqueKey;
import com.loyalty.platform.domain.entity.PointTypeDefinition;
import com.loyalty.platform.domain.repository.MemberAccountRepository;
import com.loyalty.platform.domain.repository.MemberRepository;
import com.loyalty.platform.domain.repository.MemberUniqueKeyRepository;
import com.loyalty.platform.domain.repository.PointTypeDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);
    private final MemberRepository memberRepo;
    private final MemberUniqueKeyRepository uniqueKeyRepo;
    private final MemberAccountRepository accountRepo;
    private final PointTypeDefinitionRepository pointTypeRepo;
    private final ProgramSchemaService programSchemaService;

    public MemberService(MemberRepository memberRepo, MemberUniqueKeyRepository uniqueKeyRepo,
                         MemberAccountRepository accountRepo, PointTypeDefinitionRepository pointTypeRepo,
                         ProgramSchemaService programSchemaService) {
        this.memberRepo = memberRepo;
        this.uniqueKeyRepo = uniqueKeyRepo;
        this.accountRepo = accountRepo;
        this.pointTypeRepo = pointTypeRepo;
        this.programSchemaService = programSchemaService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Member createMember(String programCode, Long memberId, String tierCode,
                                Map<String, Object> extAttributes) {
        final String pc = resolveTenant(programCode);

        // §3.4.4 手机号唯一性校验：同一手机号不能重复注册
        String mobile = (String) extAttributes.get("mobile");
        if (mobile == null) mobile = (String) extAttributes.get("phone");
        String normalized = normalizePhoneNumber(mobile);
        if (normalized != null) {
            var existingKeys = uniqueKeyRepo.findByProgramCodeAndKeyCombinationAndKeyValue(
                    pc, "MOBILE_PLAIN", normalized);
            if (!existingKeys.isEmpty()) {
                Member existing = memberRepo.findByMemberId(pc, existingKeys.get(0).getMemberId())
                        .orElse(null);
                throw new BusinessException("ERR_MEMBER_EXISTS",
                        "手机号已注册: " + mobile + " (memberId="
                        + (existing != null ? existing.getMemberId() : "unknown") + ")");
            }
        }

        if (memberId == null) {
            memberId = System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
        }
        if (memberRepo.findByMemberId(pc, memberId).isPresent()) {
            throw new BusinessException("ERR_MEMBER_EXISTS", "Member exists: " + memberId);
        }

        String sv = programSchemaService.getCurrentVersion(pc, "MEMBER");
        programSchemaService.injectSchemaVersion(extAttributes, pc, "MEMBER");

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

        // 为所有活跃的积分类型创建 member_account
        ensureMemberAccounts(pc, memberId);

        log.info("[Member] Created: program={}, memberId={}, schema={}", pc, memberId, sv);
        return m;
    }

    @Transactional(rollbackFor = Exception.class)
    public Member updateMember(String programCode, Long memberId, Map<String, Object> extAttributes) {
        final String pc = resolveTenant(programCode);

        Member member = memberRepo.findByMemberId(pc, memberId)
                .orElseThrow(() -> new BusinessException("ERR_MEMBER_NOT_FOUND",
                        "Member not found: " + pc + "/" + memberId));

        programSchemaService.injectSchemaVersion(extAttributes, pc, "MEMBER");
        String sv = programSchemaService.getCurrentVersion(pc, "MEMBER");
        member.setExtAttributes(extAttributes);
        member.setSchemaVersion(sv);
        member.setUpdatedAt(LocalDateTime.now());
        memberRepo.save(member);
        // §3.4.2 同步唯一键：手机号变更时写入 member_unique_key
        bindMobileUniqueKey(pc, memberId, extAttributes);
        // 确保积分账户存在（兼容历史会员）
        ensureMemberAccounts(pc, memberId);
        log.info("[Member] Updated: program={}, memberId={}, schema={}", pc, memberId, sv);
        return member;
    }

    private String resolveTenant(String programCode) {
        return (programCode != null && !programCode.isBlank())
                ? programCode : TenantContext.getRequired();
    }

    // ==================== 积分账户初始化 ====================

    /**
     * 为会员创建所有活跃积分类型的 member_account 记录。
     * 如果账户已存在则跳过，确保幂等。
     */
    private void ensureMemberAccounts(String programCode, Long memberId) {
        List<PointTypeDefinition> pointTypes = pointTypeRepo
                .findByProgramCodeAndStatus(programCode, "ACTIVE");
        for (PointTypeDefinition pt : pointTypes) {
            String typeCode = pt.getTypeCode();
            if (accountRepo.findByMemberIdAndType(programCode, memberId, typeCode).isPresent()) {
                continue; // 已存在，跳过
            }
            MemberAccount acc = MemberAccount.builder()
                    .programCode(programCode)
                    .memberId(memberId)
                    .accountType(typeCode)
                    .creditLimit(BigDecimal.ZERO)
                    .creditUsed(BigDecimal.ZERO)
                    .overdraftLimit(BigDecimal.ZERO)
                    .pendingRepayAmount(BigDecimal.ZERO)
                    .totalAccrued(BigDecimal.ZERO)
                    .totalRedeemed(BigDecimal.ZERO)
                    .totalExpired(BigDecimal.ZERO)
                    .build();
            accountRepo.save(acc);
            log.info("[MemberService] 积分账户已创建: member={}, type={}, accountId={}",
                    memberId, typeCode, acc.getAccountId());
        }
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