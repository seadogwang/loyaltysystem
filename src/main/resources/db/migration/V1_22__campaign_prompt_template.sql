-- ============================================================================
-- AI Prompt 模板管理表
-- ============================================================================

CREATE TABLE IF NOT EXISTS campaign_prompt_template (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(255),
    template_type VARCHAR(32),           -- PLAN_GENERATION / DAG_GENERATION / CONTENT_GENERATION
    system_prompt TEXT,
    user_prompt_template TEXT,
    output_schema JSONB,
    version INT DEFAULT 1,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(program_code, template_code, version)
);
CREATE INDEX IF NOT EXISTS idx_cpt_program ON campaign_prompt_template(program_code);
CREATE INDEX IF NOT EXISTS idx_cpt_type ON campaign_prompt_template(template_type);

COMMENT ON TABLE campaign_prompt_template IS 'AI Prompt 模板 — 版本化、多租户的 LLM Prompt 管理';
