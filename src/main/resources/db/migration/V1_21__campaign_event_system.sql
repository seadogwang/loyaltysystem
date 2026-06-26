-- ============================================================================
-- Campaign Event System + Feedback Loop — Metrics + Drift + Strategy
-- ============================================================================

-- ==================== 1. 反馈指标表 ====================

CREATE TABLE IF NOT EXISTS campaign_feedback_metrics (
    id VARCHAR(64) PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL,
    initiative_id VARCHAR(64),
    goal_id VARCHAR(64),
    -- 预测值
    predicted_roi DECIMAL(10,4),
    predicted_conversion DECIMAL(10,4),
    predicted_revenue DECIMAL(18,4),
    -- 实际值
    actual_roi DECIMAL(10,4),
    actual_conversion DECIMAL(10,4),
    actual_revenue DECIMAL(18,4),
    actual_cost DECIMAL(18,4),
    -- 偏差
    roi_deviation DECIMAL(10,4),
    conversion_deviation DECIMAL(10,4),
    -- 执行统计
    total_exposures BIGINT,
    total_engagements BIGINT,
    total_conversions BIGINT,
    unique_users BIGINT,
    -- 渠道明细
    channel_breakdown JSONB,
    -- 时间段
    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,
    -- 元数据
    calculated_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cfm_plan ON campaign_feedback_metrics(plan_id);
CREATE INDEX IF NOT EXISTS idx_cfm_calculated ON campaign_feedback_metrics(calculated_at DESC);

COMMENT ON TABLE campaign_feedback_metrics IS 'Campaign 反馈指标 — 预测vs实际对比，偏差分析';

-- ==================== 2. 模型漂移记录表 ====================

CREATE TABLE IF NOT EXISTS campaign_model_drift (
    id VARCHAR(64) PRIMARY KEY,
    model_name VARCHAR(64) NOT NULL,
    model_version VARCHAR(32),
    drift_detected BOOLEAN DEFAULT FALSE,
    drift_score DECIMAL(10,4),
    threshold DECIMAL(10,4),
    sample_size INT,
    mean_predicted DECIMAL(10,4),
    mean_actual DECIMAL(10,4),
    mae DECIMAL(10,4),
    rmse DECIMAL(10,4),
    affected_features TEXT[],
    detected_at TIMESTAMPTZ DEFAULT NOW(),
    retrained_at TIMESTAMPTZ,
    status VARCHAR(32) DEFAULT 'PENDING'
);
CREATE INDEX IF NOT EXISTS idx_cmd_model ON campaign_model_drift(model_name);
CREATE INDEX IF NOT EXISTS idx_cmd_detected ON campaign_model_drift(detected_at DESC);

COMMENT ON TABLE campaign_model_drift IS '模型漂移检测 — ML模型预测偏差监控与重训练触发';

-- ==================== 3. 策略调整记录表 ====================

CREATE TABLE IF NOT EXISTS campaign_strategy_adjustment (
    id VARCHAR(64) PRIMARY KEY,
    plan_id VARCHAR(64),
    workspace_id VARCHAR(64) NOT NULL,
    adjustment_type VARCHAR(32) NOT NULL,
    trigger_event VARCHAR(64),
    before_config JSONB,
    after_config JSONB,
    reason TEXT,
    expected_improvement DECIMAL(10,4),
    status VARCHAR(32) DEFAULT 'PENDING',
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    applied_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_csa_workspace ON campaign_strategy_adjustment(workspace_id);
CREATE INDEX IF NOT EXISTS idx_csa_plan ON campaign_strategy_adjustment(plan_id);

COMMENT ON TABLE campaign_strategy_adjustment IS '策略调整记录 — Feedback Loop 触发的预算/渠道/受众调整';
