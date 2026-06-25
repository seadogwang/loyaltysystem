-- 积分类型分类扩展：point_category 三分法
-- 执行方式：psql -U loyalty -d loyalty_dev -f V1_14__point_category.sql

ALTER TABLE point_type_definition
    ADD COLUMN IF NOT EXISTS point_category VARCHAR(20) DEFAULT 'ASSET';

COMMENT ON COLUMN point_type_definition.point_category IS '积分分类: ASSET(资产型)/CONTRIBUTION(贡献型)/RECORD(记录型)';

-- 更新已有数据：根据 is_tier_calc 推导分类
UPDATE point_type_definition SET point_category = 'CONTRIBUTION' WHERE type_code IN ('TIER', 'PURCHASE_COUNT', 'BEHAVIOR_POINTS');
UPDATE point_type_definition SET point_category = 'ASSET' WHERE type_code IN ('REWARD', 'CREDIT', 'PREPAY_CREDIT');