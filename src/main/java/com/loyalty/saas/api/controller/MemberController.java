package com.loyalty.saas.api.controller;

import com.loyalty.saas.api.service.MemberService;
import com.loyalty.saas.common.annotation.Idempotent;
import com.loyalty.saas.common.context.TenantContext;
import com.loyalty.saas.common.dto.ApiResponse;
import com.loyalty.saas.domain.entity.Member;
import com.loyalty.saas.domain.repository.MemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 动态会员 API — 供前端管理后台使用。
 *
 * <p>所有操作强制依赖 {@link TenantContext#getRequired()} 提取租户 ID，
 * 杜绝跨租户越权访问。
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;
    private final MemberRepository memberRepo;

    public MemberController(MemberService memberService, MemberRepository memberRepo) {
        this.memberService = memberService;
        this.memberRepo = memberRepo;
    }

    /** 查询会员 — 强制租户隔离 */
    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Member>> getMember(@PathVariable Long memberId) {
        // 【安全审计】所有查询强制通过 TenantContext 获取 programCode
        String programCode = TenantContext.getRequired();
        Member member = memberRepo.findByMemberId(programCode, memberId)
                .orElse(null);
        if (member == null) {
            return ResponseEntity.ok(ApiResponse.error("ERR_MEMBER_NOT_FOUND",
                    "会员不存在: " + memberId));
        }
        return ResponseEntity.ok(ApiResponse.success(member));
    }

    /** 创建会员 — Schema 版本双写 + 幂等保护 */
    @PostMapping
    @Idempotent
    public ResponseEntity<ApiResponse<Member>> createMember(@RequestBody Map<String, Object> body) {
        String programCode = TenantContext.getRequired();

        @SuppressWarnings("unchecked")
        Map<String, Object> extAttributes = (Map<String, Object>) body.getOrDefault("ext_attributes", Map.of());

        Member member = memberService.createMember(
                programCode,
                body.containsKey("member_id") ? Long.valueOf(body.get("member_id").toString()) : null,
                (String) body.getOrDefault("tier_code", "BASE"),
                extAttributes
        );
        return ResponseEntity.ok(ApiResponse.success("会员创建成功", member));
    }

    /** 更新会员动态属性 — Schema 版本双写 */
    @PutMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Member>> updateMember(@PathVariable Long memberId,
                                                             @RequestBody Map<String, Object> body) {
        String programCode = TenantContext.getRequired();

        @SuppressWarnings("unchecked")
        Map<String, Object> extAttributes = (Map<String, Object>) body.getOrDefault("ext_attributes", Map.of());

        Member member = memberService.updateMember(programCode, memberId, extAttributes);
        return ResponseEntity.ok(ApiResponse.success("会员更新成功", member));
    }
}