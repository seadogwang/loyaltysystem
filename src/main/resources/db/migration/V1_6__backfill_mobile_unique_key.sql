-- ============================================================
-- V1_6: 回填 member_unique_key 中的 MOBILE_PLAIN 数据
-- 依据: 设计文档 §3.4.2 / §3.4.4
--
-- 问题: MemberService.createMember() 历史版本未将手机号写入
--       member_unique_key，导致会员无法通过手机号搜索。
-- 修复: 从 member.ext_attributes JSONB 提取 mobile/phone 字段，
--       规范化后写入 member_unique_key (MOBILE_PLAIN)。
-- ============================================================

-- 1. 规范化手机号并跳过已存在的键
WITH normalized AS (
    SELECT
        m.program_code,
        m.member_id,
        -- 先取 mobile，再 fallback phone，去除非数字
        regexp_replace(
            COALESCE(m.ext_attributes->>'mobile', m.ext_attributes->>'phone'),
            '[^0-9]', '', 'g'
        ) AS raw_digits
    FROM member m
    WHERE m.ext_attributes ?| ARRAY['mobile', 'phone']
)
, cleaned AS (
    SELECT
        program_code,
        member_id,
        CASE
            WHEN raw_digits LIKE '86%' AND length(raw_digits) > 11
                THEN substring(raw_digits FROM 3)
            ELSE raw_digits
        END AS key_value
    FROM normalized
    WHERE raw_digits IS NOT NULL AND raw_digits != ''
)
INSERT INTO member_unique_key (program_code, key_combination, key_value, member_id, is_strong, is_verified, created_at)
SELECT
    c.program_code,
    'MOBILE_PLAIN',
    c.key_value,
    c.member_id,
    true,
    false,
    NOW()
FROM cleaned c
WHERE NOT EXISTS (
    SELECT 1 FROM member_unique_key uk
    WHERE uk.program_code = c.program_code
      AND uk.key_combination = 'MOBILE_PLAIN'
      AND uk.key_value = c.key_value
);