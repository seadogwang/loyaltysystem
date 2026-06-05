-- 积分类型定义扩展：有效期模式、可见性、透支/授信额度
-- 执行方式：psql -U loyalty -d loyalty_dev -f point_type_extension.sql

ALTER TABLE point_type_definition
    ADD COLUMN IF NOT EXISTS expiry_mode VARCHAR(30) DEFAULT 'FIXED_DAYS',
    ADD COLUMN IF NOT EXISTS expiry_value INTEGER DEFAULT 365,
    ADD COLUMN IF NOT EXISTS is_visible BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS overdraft_limit NUMERIC(20,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(20,4) DEFAULT 0;

COMMENT ON COLUMN point_type_definition.expiry_mode IS '过期模式: FIXED_DAYS/CALENDAR_MONTHS/CALENDAR_YEARS';
COMMENT ON COLUMN point_type_definition.expiry_value IS '过期值（天数/月数/年数），0=永不过期';
COMMENT ON COLUMN point_type_definition.is_visible IS '是否在前端展示给会员';
COMMENT ON COLUMN point_type_definition.overdraft_limit IS '被动透支上限（默认值）';
COMMENT ON COLUMN point_type_definition.credit_limit IS '主动授信额度（默认值）';