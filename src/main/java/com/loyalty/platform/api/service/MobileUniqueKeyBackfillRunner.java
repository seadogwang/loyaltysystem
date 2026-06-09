package com.loyalty.platform.api.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * V1.6 数据回填：将历史会员的 mobile/phone 从 ext_attributes JSONB
 * 同步到 member_unique_key（MOBILE_PLAIN），修复手机号无法搜索的问题。
 *
 * <p>设计依据：§3.4.2 要求所有可用标识写入 member_unique_key。</p>
 *
 * <p>本 Runner 仅执行一次补数据操作，逻辑与 V1_6 SQL 迁移等价。
 * 数据补齐后可删除此文件。</p>
 */
@Component
public class MobileUniqueKeyBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MobileUniqueKeyBackfillRunner.class);

    @PersistenceContext
    private EntityManager em;

    @Override
    public void run(String... args) {
        log.info("[Backfill] 开始回填 MOBILE_PLAIN 唯一键...");

        // 1. 查找 ext_attributes 中有 mobile 或 phone 的所有会员
        Query query = em.createNativeQuery("""
            SELECT m.program_code, m.member_id,
                   regexp_replace(
                       COALESCE(m.ext_attributes->>'mobile', m.ext_attributes->>'phone'),
                       '[^0-9]', '', 'g'
                   ) AS raw_digits
            FROM member m
            WHERE m.ext_attributes ?| ARRAY['mobile', 'phone']
            """);

        @SuppressWarnings("unchecked")
        var rows = query.getResultList();
        int inserted = 0;
        int skipped = 0;

        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            String programCode = (String) cols[0];
            Long memberId = ((Number) cols[1]).longValue();
            String rawDigits = (String) cols[2];

            if (rawDigits == null || rawDigits.isEmpty()) {
                skipped++;
                continue;
            }

            // 规范化：去 +86 前缀
            String keyValue = rawDigits;
            if (keyValue.startsWith("86") && keyValue.length() > 11) {
                keyValue = keyValue.substring(2);
            }

            // 2. 检查是否已存在
            Long count = (Long) em.createQuery(
                "SELECT COUNT(k) FROM MemberUniqueKey k WHERE k.programCode=:pc AND k.keyCombination='MOBILE_PLAIN' AND k.keyValue=:kv")
                .setParameter("pc", programCode)
                .setParameter("kv", keyValue)
                .getSingleResult();

            if (count > 0) {
                skipped++;
                continue;
            }

            // 3. 插入
            em.createNativeQuery("""
                INSERT INTO member_unique_key (program_code, key_combination, key_value, member_id, is_strong, is_verified, created_at)
                VALUES (?, 'MOBILE_PLAIN', ?, ?, true, false, NOW())
                """)
                .setParameter(1, programCode)
                .setParameter(2, keyValue)
                .setParameter(3, memberId)
                .executeUpdate();

            inserted++;
        }

        log.info("[Backfill] 完成: 插入 {} 条, 跳过 {} 条 (重复或无有效手机号)", inserted, skipped);
    }
}