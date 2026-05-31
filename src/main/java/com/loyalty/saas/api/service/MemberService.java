package com.loyalty.saas.api.service;

import com.loyalty.saas.common.context.TenantContext;
import com.loyalty.saas.common.exception.BusinessException;
import com.loyalty.saas.domain.entity.Member;
import com.loyalty.saas.domain.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);
    private final MemberRepository memberRepo;
    private final SchemaService schemaService;

    public MemberService(MemberRepository memberRepo, SchemaService schemaService) {
        this.memberRepo = memberRepo;
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
        log.info("[Member] Updated: program={}, memberId={}, schema={}", pc, memberId, sv);
        return member;
    }

    private String resolveTenant(String programCode) {
        return (programCode != null && !programCode.isBlank())
                ? programCode : TenantContext.getRequired();
    }
}