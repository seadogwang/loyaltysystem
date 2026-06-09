package com.loyalty.platform.flow.components;

import com.loyalty.platform.domain.entity.Member;
import com.loyalty.platform.flow.BaseLiteflowComponent;
import com.loyalty.platform.flow.EventContext;
import com.loyalty.platform.member.OneIdEnrollmentService;
import com.loyalty.platform.rules.drl.MemberFact;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Map;

/**
 * One-ID 匹配组件 — 根据 channel + unique_key 匹配或创建会员。
 *
 * <p>流程：
 * <ol>
 *   <li>从 standardizedPayload 中提取唯一标识（phone/openId/unionId）</li>
 *   <li>调用 OneIdEnrollmentService 匹配/创建会员</li>
 *   <li>构建 MemberFact 并设置到 EventContext</li>
 * </ol>
 */
@LiteflowComponent("oneIdCmp")
public class OneIdComponent extends BaseLiteflowComponent {

    @PersistenceContext
    private EntityManager em;

    @Override
    protected void doProcess(EventContext ctx) throws Exception {
        String pc = ctx.getProgramCode();
        Map<String, Object> payload = ctx.getStandardizedPayload();
        if (payload == null) {
            payload = Map.of();
        }

        // 提取 memberId（优先从标准化 payload 中获取）
        String memberIdStr = extractMemberId(payload);
        Long memberId = memberIdStr != null ? Long.parseLong(memberIdStr) : null;

        MemberFact memberFact;
        if (memberId != null) {
            try {
                Member member = em.createQuery(
                        "SELECT m FROM Member m WHERE m.programCode = :pc AND m.memberId = :mid",
                        Member.class)
                        .setParameter("pc", pc).setParameter("mid", memberId)
                        .getSingleResult();
                memberFact = new MemberFact(pc, member.getMemberId(), member.getTierCode(),
                        member.getStatus(), member.getExtAttributes());
                log.debug("[OneId] 会员已存在: memberId={}, tier={}", memberId, member.getTierCode());
            } catch (Exception e) {
                // 会员不存在时创建默认 MemberFact
                memberFact = new MemberFact(pc, memberId, "BASE", "ENROLLED", payload);
                log.info("[OneId] 会员不存在，使用默认值: memberId={}", memberId);
            }
        } else {
            // 无法提取 memberId，使用默认值
            memberFact = new MemberFact(pc, 0L, "BASE", "UNKNOWN", payload);
            log.warn("[OneId] 无法提取 memberId，使用默认");
        }

        ctx.setMemberFact(memberFact);
    }

    private String extractMemberId(Map<String, Object> payload) {
        // 多种 memberId key 变体
        for (String key : new String[]{"member_id", "memberId", "userId", "open_id", "buyer_nick"}) {
            Object val = payload.get(key);
            if (val != null) {
                String s = String.valueOf(val);
                // 只取数字
                s = s.replaceAll("[^0-9]", "");
                if (!s.isBlank() && s.length() <= 19) return s;
            }
        }
        return null;
    }
}