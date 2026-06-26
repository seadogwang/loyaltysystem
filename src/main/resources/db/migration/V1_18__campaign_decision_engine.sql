-- ============================================================================
-- Campaign Tools 决策引擎持久化 — Decision Result + Budget Allocation +
-- Attention Consumption Audit + Arbitration Log
-- ============================================================================

-- ==================== 1. 决策结果表 ====================

CREATE TABLE IF NOT EXISTS campaign_decision_result (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    portfolio_id VARCHAR(64),
    goal_id VARCHAR(64),
    decision_type VARCHAR(32) NOT NULL,          -- BUDGET_ALLOCATION / ARBITRATION / FULL_DECISION
    status VARCHAR(32) DEFAULT 'DRAFT',          -- DRAFT / APPLIED / REJECTED / SUPERSEDED / ROLLED_BACK
    -- 输入快照
    input_snapshot JSONB NOT NULL,               -- 决策输入完整快照
    -- 输出结果
    allocation_result JSONB NOT NULL,            -- 预算分配结果
    arbitration_result JSONB,                    -- 仲裁结果
    prioritization_result JSONB,                 -- 优先级排序结果
    -- 元数据
    total_budget DECIMAL(18,4),
    total_allocated DECIMAL(18,4),
    expected_total_roi DECIMAL(10,4),
    conflicts_resolved INT DEFAULT 0,
    rejected_candidates INT DEFAULT 0,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    applied_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_cdr_workspace ON campaign_decision_result(workspace_id);
CREATE INDEX IF NOT EXISTS idx_cdr_portfolio ON campaign_decision_result(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_cdr_status ON campaign_decision_result(status);
CREATE INDEX IF NOT EXISTS idx_cdr_created ON campaign_decision_result(created_at DESC);

COMMENT ON TABLE campaign_decision_result IS '决策引擎运行结果 — 包含输入快照和完整的分配/仲裁/排序输出';
COMMENT ON COLUMN campaign_decision_result.decision_type IS 'BUDGET_ALLOCATION / ARBITRATION / FULL_DECISION';
COMMENT ON COLUMN campaign_decision_result.status IS 'DRAFT / APPLIED / REJECTED / SUPERSEDED / ROLLED_BACK';

-- ==================== 2. 预算分配明细表 ====================

CREATE TABLE IF NOT EXISTS campaign_budget_allocation (
    id VARCHAR(64) PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL,
    initiative_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(64),                     -- 可选：具体 Campaign
    allocated_budget DECIMAL(18,4) NOT NULL,
    expected_roi DECIMAL(10,4),
    actual_roi DECIMAL(10,4),                    -- 执行后回填
    status VARCHAR(32) DEFAULT 'PENDING',        -- PENDING / EXECUTING / COMPLETED / FAILED
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cba_decision ON campaign_budget_allocation(decision_id);
CREATE INDEX IF NOT EXISTS idx_cba_initiative ON campaign_budget_allocation(initiative_id);
CREATE INDEX IF NOT EXISTS idx_cba_status ON campaign_budget_allocation(status);

COMMENT ON TABLE campaign_budget_allocation IS '预算分配明细 — 每个 Initiative 的预算分配和执行跟踪';
COMMENT ON COLUMN campaign_budget_allocation.status IS 'PENDING / EXECUTING / COMPLETED / FAILED';

-- ==================== 3. 注意力预算消费审计表 ====================

CREATE TABLE IF NOT EXISTS campaign_attention_consumption (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(64),
    channel VARCHAR(32) NOT NULL,
    consumed_at TIMESTAMPTZ DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent TEXT
);
CREATE INDEX IF NOT EXISTS idx_cac_user ON campaign_attention_consumption(user_id);
CREATE INDEX IF NOT EXISTS idx_cac_campaign ON campaign_attention_consumption(campaign_id);
CREATE INDEX IF NOT EXISTS idx_cac_consumed ON campaign_attention_consumption(consumed_at DESC);

COMMENT ON TABLE campaign_attention_consumption IS '注意力预算消费审计 — 记录每次曝光消耗的完整流水';

-- ==================== 4. 仲裁日志表 ====================

CREATE TABLE IF NOT EXISTS campaign_arbitration_log (
    id VARCHAR(64) PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL,
    conflict_type VARCHAR(32) NOT NULL,          -- USER / BUDGET / CHANNEL / TIME
    candidate_ids TEXT[] NOT NULL,               -- 冲突的候选ID列表
    resolution VARCHAR(64) NOT NULL,             -- 被选中的ID
    resolution_reason TEXT,
    priority_scores JSONB,                       -- 每个候选的优先级分数
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cal_decision ON campaign_arbitration_log(decision_id);
CREATE INDEX IF NOT EXISTS idx_cal_type ON campaign_arbitration_log(conflict_type);

COMMENT ON TABLE campaign_arbitration_log IS '冲突仲裁日志 — 记录每次冲突仲裁的详细过程和依据';
COMMENT ON COLUMN campaign_arbitration_log.conflict_type IS 'USER / BUDGET / CHANNEL / TIME';
