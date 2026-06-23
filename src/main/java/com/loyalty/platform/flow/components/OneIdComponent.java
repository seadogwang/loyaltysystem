package com.loyalty.platform.flow.components;

import com.loyalty.platform.domain.entity.Member;
import com.loyalty.platform.domain.entity.MemberAccount;
import com.loyalty.platform.flow.BaseLiteflowComponent;
import com.loyalty.platform.flow.EventContext;
import com.loyalty.platform.rules.drl.MemberFact;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
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

                // 获取累计消费金额(TIER账户余额作为等级成长值)
                Double totalSpent = 0.0;
                Double tierPoints = 0.0;
                try {
                    var tierAcc = em.createQuery(
                        "SELECT a FROM MemberAccount a WHERE a.programCode=:pc AND a.memberId=:mid AND a.accountType='TIER'",
                        MemberAccount.class)
                        .setParameter("pc", pc).setParameter("mid", memberId)
                        .getResultList();
                    if (!tierAcc.isEmpty()) {
                        tierPoints = tierAcc.get(0).getTotalAccrued().doubleValue();
                    }
                    var rewardAcc = em.createQuery(
                        "SELECT a FROM MemberAccount a WHERE a.programCode=:pc AND a.memberId=:mid AND a.accountType='REWARD'",
                        MemberAccount.class)
                        .setParameter("pc", pc).setParameter("mid", memberId)
                        .getResultList();
                    if (!rewardAcc.isEmpty()) {
                        totalSpent = rewardAcc.get(0).getTotalAccrued().doubleValue();
                    }
                } catch (Exception ignored) {}

                memberFact = new MemberFact(pc, member.getMemberId(), member.getTierCode(),
                        member.getStatus(), member.getExtAttributes(), totalSpent, tierPoints);
                log.debug("[OneId] 会员已存在: memberId={}, tier={}, totalSpent={}, tierPoints={}",
                        memberId, member.getTierCode(), totalSpent, tierPoints);
            } catch (Exception e) {
                // 会员不存在时创建默认 MemberFact
                memberFact = new MemberFact(pc, memberId, "BASE", "ENROLLED", payload, 0.0, 0.0);
                log.info("[OneId] 会员不存在，使用默认值: memberId={}", memberId);
            }
        } else {
            // 无法提取 memberId，使用默认值
            memberFact = new MemberFact(pc, 0L, "BASE", "UNKNOWN", payload, 0.0, 0.0);
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