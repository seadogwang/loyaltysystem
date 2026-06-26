-- ============================================================================
-- Campaign Tools 模拟与优化系统 — Simulation Result + Scenario + Optimization
-- ============================================================================

-- ==================== 1. 模拟结果表 ====================

CREATE TABLE IF NOT EXISTS campaign_simulation_result (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    goal_id VARCHAR(64),
    initiative_id VARCHAR(64),
    simulation_type VARCHAR(32) NOT NULL,          -- BASELINE / SCENARIO / OPTIMIZATION
    name VARCHAR(255),
    description TEXT,
    -- 输入快照
    input_snapshot JSONB NOT NULL,                 -- 模拟输入的完整快照
    -- 核心结果
    baseline_conversion DECIMAL(10,4),             -- 基线转化率
    predicted_conversion DECIMAL(10,4),            -- 预测转化率
    predicted_revenue DECIMAL(18,4),               -- 预测收入
    predicted_roi DECIMAL(10,4),                   -- 预测 ROI
    uplift_pct DECIMAL(10,4),                      -- 提升百分比
    confidence DECIMAL(10,4),                      -- 置信度
    -- 明细
    exposure_count BIGINT,                         -- 预估曝光人数
    behavior_count BIGINT,                         -- 预估互动人数
    conversion_count BIGINT,                       -- 预估转化人数
    -- 分层结果（JSON）
    segment_breakdown JSONB,                       -- 分群级别明细
    channel_breakdown JSONB,                       -- 渠道级别明细
    -- 元数据
    status VARCHAR(32) DEFAULT 'DRAFT',            -- DRAFT / COMPLETED / APPLIED
    executed_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_csr_workspace ON campaign_simulation_result(workspace_id);
CREATE INDEX IF NOT EXISTS idx_csr_goal ON campaign_simulation_result(goal_id);
CREATE INDEX IF NOT EXISTS idx_csr_type ON campaign_simulation_result(simulation_type);
CREATE INDEX IF NOT EXISTS idx_csr_created ON campaign_simulation_result(created_at DESC);

COMMENT ON TABLE campaign_simulation_result IS '模拟运行结果 — 包含基线、三层模型预测、分群渠道明细';
COMMENT ON COLUMN campaign_simulation_result.simulation_type IS 'BASELINE / SCENARIO / OPTIMIZATION';

-- ==================== 2. What-if 场景表 ====================

CREATE TABLE IF NOT EXISTS campaign_simulation_scenario (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    goal_id VARCHAR(64),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    -- 场景配置
    scenario_config JSONB NOT NULL,                -- 场景参数（预算、渠道、人群等）
    -- 对比基准
    baseline_simulation_id VARCHAR(64),            -- 对比的基准模拟
    -- 结果
    predicted_roi DECIMAL(10,4),
    predicted_revenue DECIMAL(18,4),
    improvement_over_baseline DECIMAL(10,4),
    -- 元数据
    status VARCHAR(32) DEFAULT 'DRAFT',
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_css_workspace ON campaign_simulation_scenario(workspace_id);
CREATE INDEX IF NOT EXISTS idx_css_goal ON campaign_simulation_scenario(goal_id);

COMMENT ON TABLE campaign_simulation_scenario IS 'What-if 场景定义 — 对比不同策略参数的模拟场景';

-- ==================== 3. 优化结果表 ====================

CREATE TABLE IF NOT EXISTS campaign_optimization_result (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    portfolio_id VARCHAR(64),
    goal_id VARCHAR(64),
    optimization_type VARCHAR(32) NOT NULL,        -- GREEDY / GENETIC / AI
    -- 输入
    constraints JSONB NOT NULL,                    -- 约束条件（总预算、渠道容量等）
    -- 输出
    optimized_allocations JSONB NOT NULL,          -- 最优预算分配
    expected_roi DECIMAL(10,4),
    expected_revenue DECIMAL(18,4),
    iteration_count INT,
    convergence_time_ms BIGINT,
    -- 对比
    baseline_roi DECIMAL(10,4),
    improvement_pct DECIMAL(10,4),
    -- 元数据
    status VARCHAR(32) DEFAULT 'DRAFT',            -- DRAFT / APPLIED / REJECTED
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cor_workspace ON campaign_optimization_result(workspace_id);
CREATE INDEX IF NOT EXISTS idx_cor_portfolio ON campaign_optimization_result(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_cor_type ON campaign_optimization_result(optimization_type);

COMMENT ON TABLE campaign_optimization_result IS '优化引擎输出 — 贪心/遗传/AI 算法的最优预算分配方案';
COMMENT ON COLUMN campaign_optimization_result.optimization_type IS 'GREEDY / GENETIC / AI';
