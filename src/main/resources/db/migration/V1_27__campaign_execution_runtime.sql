-- ============================================================================
-- End-to-End Execution Runtime — Master + Step + User Detail
-- ============================================================================

-- 1. 执行主表
CREATE TABLE IF NOT EXISTS campaign_execution_master (
    id VARCHAR(64) PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    goal_id VARCHAR(64),
    execution_key BIGINT UNIQUE,
    zeebe_process_id VARCHAR(100),
    zeebe_version INT,
    status VARCHAR(32) NOT NULL,
    total_nodes INT DEFAULT 0,
    completed_nodes INT DEFAULT 0,
    failed_nodes INT DEFAULT 0,
    total_users INT DEFAULT 0,
    processed_users INT DEFAULT 0,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    duration_ms BIGINT,
    triggered_by VARCHAR(64),
    trigger_source VARCHAR(32),
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cem_plan ON campaign_execution_master(plan_id);
CREATE INDEX IF NOT EXISTS idx_cem_workspace ON campaign_execution_master(workspace_id);
CREATE INDEX IF NOT EXISTS idx_cem_status ON campaign_execution_master(status);
CREATE INDEX IF NOT EXISTS idx_cem_start ON campaign_execution_master(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_cem_key ON campaign_execution_master(execution_key);

COMMENT ON TABLE campaign_execution_master IS '执行主表 — 端到端执行链路的根记录';

-- 2. 执行步骤表
CREATE TABLE IF NOT EXISTS campaign_execution_step (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_type VARCHAR(64) NOT NULL,
    node_name VARCHAR(255),
    job_key BIGINT,
    worker_type VARCHAR(64),
    input_snapshot JSONB,
    output_snapshot JSONB,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    affected_users INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    duration_ms BIGINT,
    worker_host VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ces_execution ON campaign_execution_step(execution_id);
CREATE INDEX IF NOT EXISTS idx_ces_plan ON campaign_execution_step(plan_id);
CREATE INDEX IF NOT EXISTS idx_ces_node ON campaign_execution_step(node_id);
CREATE INDEX IF NOT EXISTS idx_ces_status ON campaign_execution_step(status);
CREATE INDEX IF NOT EXISTS idx_ces_start ON campaign_execution_step(start_time DESC);

COMMENT ON TABLE campaign_execution_step IS '执行步骤 — 每个 Canvas 节点的执行明细';

-- 3. 用户级执行明细表
CREATE TABLE IF NOT EXISTS campaign_execution_user_detail (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    channel VARCHAR(32),
    message_id VARCHAR(64),
    error_message TEXT,
    points_granted DECIMAL(18,4),
    coupon_issued VARCHAR(64),
    tier_upgraded VARCHAR(32),
    executed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ceud_execution ON campaign_execution_user_detail(execution_id);
CREATE INDEX IF NOT EXISTS idx_ceud_plan ON campaign_execution_user_detail(plan_id);
CREATE INDEX IF NOT EXISTS idx_ceud_user ON campaign_execution_user_detail(user_id);
CREATE INDEX IF NOT EXISTS idx_ceud_node ON campaign_execution_user_detail(node_id);
CREATE INDEX IF NOT EXISTS idx_ceud_status ON campaign_execution_user_detail(status);

COMMENT ON TABLE campaign_execution_user_detail IS '用户级执行明细 — 每用户每节点的执行结果';
