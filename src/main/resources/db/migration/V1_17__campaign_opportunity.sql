-- ============================================================================
-- Campaign Tools 机会智能（Opportunity Intelligence）— 第2章详细设计
-- ============================================================================

-- 1. 机会表（campaign_opportunity）
-- 存储每次机会发现任务产出的会员级机会记录
CREATE TABLE IF NOT EXISTS campaign_opportunity (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES campaign_workspace(id) ON DELETE CASCADE,
    goal_id VARCHAR(64) REFERENCES campaign_goal(id),
    member_id VARCHAR(64) NOT NULL,
    segment_code VARCHAR(64),
    opportunity_type VARCHAR(32) NOT NULL,       -- CHURN_RISK / UPSELL / WINBACK / CROSS_SELL / ENGAGEMENT
    score DECIMAL(10,4) NOT NULL,                -- 综合机会评分（0~1）
    -- ML 输出字段
    churn_probability DECIMAL(10,4),
    uplift_score DECIMAL(10,4),
    conversion_probability DECIMAL(10,4),
    rfm_score DECIMAL(10,4),
    -- 外部影响
    external_influence DECIMAL(10,4) DEFAULT 1.0,
    external_signal_ids TEXT[],                  -- 影响该机会的外部信号ID列表
    -- 推荐
    recommended_action VARCHAR(255),
    recommended_channel VARCHAR(32),
    confidence DECIMAL(10,4),
    -- 状态
    status VARCHAR(32) DEFAULT 'ACTIVE',         -- ACTIVE / CONSUMED / EXPIRED / SUPPRESSED
    source VARCHAR(32) NOT NULL,                 -- INTERNAL / EXTERNAL / ML / HYBRID
    -- 时间
    detected_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_co_workspace ON campaign_opportunity(workspace_id);
CREATE INDEX IF NOT EXISTS idx_co_goal ON campaign_opportunity(goal_id);
CREATE INDEX IF NOT EXISTS idx_co_member ON campaign_opportunity(member_id);
CREATE INDEX IF NOT EXISTS idx_co_score ON campaign_opportunity(score DESC);
CREATE INDEX IF NOT EXISTS idx_co_status ON campaign_opportunity(status);
CREATE INDEX IF NOT EXISTS idx_co_type ON campaign_opportunity(opportunity_type);
CREATE INDEX IF NOT EXISTS idx_co_detected ON campaign_opportunity(detected_at DESC);

COMMENT ON TABLE campaign_opportunity IS '会员级机会记录 — 机会发现引擎输出';
COMMENT ON COLUMN campaign_opportunity.opportunity_type IS 'CHURN_RISK / UPSELL / WINBACK / CROSS_SELL / ENGAGEMENT';
COMMENT ON COLUMN campaign_opportunity.score IS '综合机会评分（0~1）';
COMMENT ON COLUMN campaign_opportunity.source IS 'INTERNAL / EXTERNAL / ML / HYBRID';

-- 2. 增强 external_signal 表（新增字段，已存在的表做 ALTER）
ALTER TABLE external_signal
    ADD COLUMN IF NOT EXISTS title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

COMMENT ON COLUMN external_signal.title IS '信号标题';
COMMENT ON COLUMN external_signal.description IS '信号详细描述';

-- 3. 会员宽表补充索引
CREATE INDEX IF NOT EXISTS idx_cmd_segment_score ON campaign_member_dim(segment_code, total_order_amount DESC);

-- 4. external_signal 补充索引
CREATE INDEX IF NOT EXISTS idx_es_program ON external_signal(program_code);
CREATE INDEX IF NOT EXISTS idx_es_expires ON external_signal(expires_at) WHERE expires_at > NOW();
