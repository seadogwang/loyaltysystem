-- ============================================================================
-- Content & Compliance — 素材版本历史 + 变量绑定
-- ============================================================================

-- 1. 素材版本历史表
CREATE TABLE IF NOT EXISTS campaign_content_asset_history (
    id VARCHAR(64) PRIMARY KEY,
    asset_id VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    asset_name VARCHAR(255),
    subject_line VARCHAR(255),
    body_text TEXT,
    body_json JSONB,
    variable_schema JSONB,
    status VARCHAR(32),
    changed_by VARCHAR(64),
    change_comment TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ccah_asset ON campaign_content_asset_history(asset_id);
CREATE INDEX IF NOT EXISTS idx_ccah_version ON campaign_content_asset_history(asset_id, version);
CREATE INDEX IF NOT EXISTS idx_ccah_created ON campaign_content_asset_history(created_at DESC);

COMMENT ON TABLE campaign_content_asset_history IS '素材版本历史 — 每次修改的快照';

-- 2. 变量绑定配置表
CREATE TABLE IF NOT EXISTS campaign_variable_binding (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    asset_id VARCHAR(64),
    plan_id VARCHAR(64),
    segment_code VARCHAR(64),
    variable_bindings JSONB NOT NULL,
    priority INT DEFAULT 100,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cvb_program ON campaign_variable_binding(program_code);
CREATE INDEX IF NOT EXISTS idx_cvb_asset ON campaign_variable_binding(asset_id);
CREATE INDEX IF NOT EXISTS idx_cvb_plan ON campaign_variable_binding(plan_id);
CREATE INDEX IF NOT EXISTS idx_cvb_segment ON campaign_variable_binding(segment_code);

COMMENT ON TABLE campaign_variable_binding IS '变量绑定 — 按分群/Plan/素材的变量默认值映射';

-- 3. 扩展 content_asset 表
ALTER TABLE campaign_content_asset ADD COLUMN IF NOT EXISTS body_json JSONB;
ALTER TABLE campaign_content_asset ADD COLUMN IF NOT EXISTS rejected_by VARCHAR(64);
ALTER TABLE campaign_content_asset ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ;
ALTER TABLE campaign_content_asset ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
