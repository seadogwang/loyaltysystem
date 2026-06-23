-- V1_13__tier_enhancement.sql
-- 等级规则增强：按 rule_tier.md 设计文档实现

-- 1. 扩展 rule_definition 表，增加规则用途字段
ALTER TABLE rule_definition ADD COLUMN IF NOT EXISTS rule_purpose VARCHAR(30) DEFAULT 'EARN_POINTS';
COMMENT ON COLUMN rule_definition.rule_purpose IS 'EARN_POINTS|TIER_UPGRADE|TIER_DOWNGRADE|TIER_RETENTION|TIER_ACTIVITY';

-- 2. 扩展 tier_definition 表，增加等级配置字段
ALTER TABLE tier_definition ADD COLUMN IF NOT EXISTS tier_level INT DEFAULT 0;
ALTER TABLE tier_definition ADD COLUMN IF NOT EXISTS tier_value INT DEFAULT 0;
ALTER TABLE tier_definition ADD COLUMN IF NOT EXISTS tier_icon VARCHAR(255);
ALTER TABLE tier_definition ADD COLUMN IF NOT EXISTS tier_benefits JSONB;
ALTER TABLE tier_definition ADD COLUMN IF NOT EXISTS description TEXT;

COMMENT ON COLUMN tier_definition.tier_level IS '等级顺序(0=最低)';
COMMENT ON COLUMN tier_definition.tier_value IS '等级门槛值';
COMMENT ON COLUMN tier_definition.tier_benefits IS '等级权益配置JSON';

-- 3. 创建等级直升活动表
CREATE TABLE IF NOT EXISTS tier_activity (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    activity_code VARCHAR(64) NOT NULL,
    activity_name VARCHAR(200) NOT NULL,
    target_tier_code VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    trigger_config JSONB NOT NULL,
    valid_start_time TIMESTAMPTZ NOT NULL,
    valid_end_time TIMESTAMPTZ,
    once_per_member BOOLEAN DEFAULT true,
    member_scope VARCHAR(20) DEFAULT 'ALL',
    status VARCHAR(20) DEFAULT 'DRAFT',
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(program_code, activity_code)
);
CREATE INDEX IF NOT EXISTS idx_tier_act_program ON tier_activity(program_code);
CREATE INDEX IF NOT EXISTS idx_tier_act_status ON tier_activity(status);

-- 4. 创建会员直升活动记录表
CREATE TABLE IF NOT EXISTS member_tier_activity_log (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    activity_code VARCHAR(64) NOT NULL,
    original_tier VARCHAR(32),
    target_tier VARCHAR(32) NOT NULL,
    trigger_event_id VARCHAR(64),
    trigger_value JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(program_code, member_id, activity_code)
);
CREATE INDEX IF NOT EXISTS idx_mtal_member ON member_tier_activity_log(program_code, member_id);

-- 5. 权限
ALTER TABLE tier_activity OWNER TO loyalty_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tier_activity TO loyalty_app;
GRANT USAGE, SELECT ON SEQUENCE tier_activity_id_seq TO loyalty_app;

ALTER TABLE member_tier_activity_log OWNER TO loyalty_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON member_tier_activity_log TO loyalty_app;
GRANT USAGE, SELECT ON SEQUENCE member_tier_activity_log_id_seq TO loyalty_app;