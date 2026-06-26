-- ============================================================================
-- Campaign Tools 核心数据模型 — Planning Workspace + Execution + Compliance
-- ============================================================================
-- 执行: psql -U loyalty -d loyalty_dev -f V1_15__campaign_core.sql

-- ==================== 1. Planning Workspace ====================

-- 1.1 工作区主表
CREATE TABLE IF NOT EXISTS campaign_workspace (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,                  -- 关联 Loyalty Program
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',       -- ACTIVE / ARCHIVED / LOCKED
    active_goal_id VARCHAR(64),                         -- 当前激活的目标ID
    config JSONB DEFAULT '{}',                          -- 工作区级配置（时区、默认预算等）
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cw_program ON campaign_workspace(program_code);
CREATE INDEX IF NOT EXISTS idx_cw_status ON campaign_workspace(status);

COMMENT ON TABLE campaign_workspace IS '营销工作区 — Campaign Planning 顶层容器';
COMMENT ON COLUMN campaign_workspace.status IS 'ACTIVE / ARCHIVED / LOCKED';
COMMENT ON COLUMN campaign_workspace.config IS '工作区配置: timezone, defaultBudget 等';

-- 1.2 工作区成员（权限模型）
CREATE TABLE IF NOT EXISTS campaign_workspace_member (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES campaign_workspace(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,                          -- OWNER / ADMIN / ANALYST / VIEWER
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(workspace_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_cwm_workspace ON campaign_workspace_member(workspace_id);

-- 1.3 工作区快照（版本隔离核心）
CREATE TABLE IF NOT EXISTS campaign_workspace_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES campaign_workspace(id) ON DELETE CASCADE,
    snapshot_type VARCHAR(32) NOT NULL,                 -- GOAL / INITIATIVE / PORTFOLIO
    snapshot_data JSONB NOT NULL,
    version INT NOT NULL,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cws_workspace ON campaign_workspace_snapshot(workspace_id);
CREATE INDEX IF NOT EXISTS idx_cws_type_version ON campaign_workspace_snapshot(workspace_id, snapshot_type, version);

-- ==================== 2. Goal Management ====================

-- 2.1 目标主表
CREATE TABLE IF NOT EXISTS campaign_goal (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES campaign_workspace(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    goal_type VARCHAR(32) NOT NULL,                     -- REVENUE / RETENTION / ACQUISITION / ENGAGEMENT
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',        -- DRAFT / ACTIVE / PAUSED / COMPLETED / ARCHIVED
    target_metric VARCHAR(64),                          -- 关联 Loyalty 指标
    target_value DECIMAL(18,4),
    current_value DECIMAL(18,4) DEFAULT 0,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cg_workspace ON campaign_goal(workspace_id);
CREATE INDEX IF NOT EXISTS idx_cg_status ON campaign_goal(status);
CREATE INDEX IF NOT EXISTS idx_cg_active ON campaign_goal(workspace_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE campaign_goal IS '营销目标 — 每个 Workspace 同时仅有一个 ACTIVE Goal';

-- 2.2 目标 KPI 表
CREATE TABLE IF NOT EXISTS campaign_goal_kpi (
    id VARCHAR(64) PRIMARY KEY,
    goal_id VARCHAR(64) NOT NULL REFERENCES campaign_goal(id) ON DELETE CASCADE,
    kpi_type VARCHAR(32) NOT NULL,                      -- REVENUE / CONVERSION / RETENTION / ROI
    target_value DECIMAL(18,4) NOT NULL,
    current_value DECIMAL(18,4) DEFAULT 0,
    weight DECIMAL(5,2) DEFAULT 1.0,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(goal_id, kpi_type)
);
CREATE INDEX IF NOT EXISTS idx_cgk_goal ON campaign_goal_kpi(goal_id);

-- 2.3 目标版本表
CREATE TABLE IF NOT EXISTS campaign_goal_version (
    id VARCHAR(64) PRIMARY KEY,
    goal_id VARCHAR(64) NOT NULL REFERENCES campaign_goal(id) ON DELETE CASCADE,
    version INT NOT NULL,
    snapshot JSONB NOT NULL,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(goal_id, version)
);
CREATE INDEX IF NOT EXISTS idx_cgv_goal ON campaign_goal_version(goal_id);

-- ==================== 3. Initiative Management ====================

-- 3.1 Initiative 主表
CREATE TABLE IF NOT EXISTS campaign_initiative (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES campaign_workspace(id) ON DELETE CASCADE,
    goal_id VARCHAR(64) NOT NULL REFERENCES campaign_goal(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    initiative_type VARCHAR(32),                        -- WINBACK / GROWTH / ENGAGEMENT / ACQUISITION
    status VARCHAR(32) DEFAULT 'PLANNED',               -- PLANNED / ACTIVE / PAUSED / COMPLETED / ARCHIVED
    priority INT DEFAULT 100,                           -- 数字越小优先级越高
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    rule_config JSONB,                                  -- 举措的规则配置（人群、条件等）
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ci_workspace ON campaign_initiative(workspace_id);
CREATE INDEX IF NOT EXISTS idx_ci_goal ON campaign_initiative(goal_id);
CREATE INDEX IF NOT EXISTS idx_ci_status ON campaign_initiative(status);
CREATE INDEX IF NOT EXISTS idx_ci_priority ON campaign_initiative(priority);

COMMENT ON TABLE campaign_initiative IS '营销举措 — 属于 Goal 的策略分组';

-- 3.2 Initiative ↔ Campaign Plan 关系表
CREATE TABLE IF NOT EXISTS campaign_initiative_plan_relation (
    id VARCHAR(64) PRIMARY KEY,
    initiative_id VARCHAR(64) NOT NULL REFERENCES campaign_initiative(id) ON DELETE CASCADE,
    plan_id VARCHAR(64) NOT NULL,
    weight DECIMAL(10,4) DEFAULT 1.0,                   -- 该 Plan 在 Initiative 中的权重
    role VARCHAR(32) DEFAULT 'PRIMARY',                 -- PRIMARY / SUPPORTING / EXPERIMENTAL
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(initiative_id, plan_id)
);
CREATE INDEX IF NOT EXISTS idx_cipr_initiative ON campaign_initiative_plan_relation(initiative_id);
CREATE INDEX IF NOT EXISTS idx_cipr_plan ON campaign_initiative_plan_relation(plan_id);

-- 3.3 Initiative KPI 表
CREATE TABLE IF NOT EXISTS campaign_initiative_kpi (
    id VARCHAR(64) PRIMARY KEY,
    initiative_id VARCHAR(64) NOT NULL REFERENCES campaign_initiative(id) ON DELETE CASCADE,
    kpi_type VARCHAR(32) NOT NULL,
    target_value DECIMAL(18,4) NOT NULL,
    current_value DECIMAL(18,4) DEFAULT 0,
    weight DECIMAL(5,2) DEFAULT 1.0,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(initiative_id, kpi_type)
);
CREATE INDEX IF NOT EXISTS idx_cik_initiative ON campaign_initiative_kpi(initiative_id);

-- ==================== 4. Portfolio Management ====================

-- 4.1 Portfolio 主表
CREATE TABLE IF NOT EXISTS campaign_portfolio (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES campaign_workspace(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) DEFAULT 'DRAFT',                 -- DRAFT / OPTIMIZED / LOCKED / EXECUTING / COMPLETED
    optimization_mode VARCHAR(32) DEFAULT 'ROI_MAXIMIZATION', -- ROI_MAXIMIZATION / REVENUE_MAXIMIZATION / BALANCED
    total_budget DECIMAL(18,4),
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cp_workspace ON campaign_portfolio(workspace_id);
CREATE INDEX IF NOT EXISTS idx_cp_status ON campaign_portfolio(status);

COMMENT ON TABLE campaign_portfolio IS '营销组合 — 跨 Goal/Initiative 的全局资源优化层';

-- 4.2 Portfolio ↔ Initiative 关系表（预算分配）
CREATE TABLE IF NOT EXISTS campaign_portfolio_initiative_relation (
    id VARCHAR(64) PRIMARY KEY,
    portfolio_id VARCHAR(64) NOT NULL REFERENCES campaign_portfolio(id) ON DELETE CASCADE,
    initiative_id VARCHAR(64) NOT NULL REFERENCES campaign_initiative(id) ON DELETE CASCADE,
    allocated_budget DECIMAL(18,4),                     -- 分配预算
    expected_roi DECIMAL(10,4),                         -- 预期 ROI
    priority_weight DECIMAL(10,4) DEFAULT 1.0,          -- 优先级权重
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(portfolio_id, initiative_id)
);
CREATE INDEX IF NOT EXISTS idx_cppr_portfolio ON campaign_portfolio_initiative_relation(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_cppr_initiative ON campaign_portfolio_initiative_relation(initiative_id);

-- 4.3 Portfolio KPI 表
CREATE TABLE IF NOT EXISTS campaign_portfolio_kpi (
    id VARCHAR(64) PRIMARY KEY,
    portfolio_id VARCHAR(64) NOT NULL REFERENCES campaign_portfolio(id) ON DELETE CASCADE,
    kpi_type VARCHAR(32) NOT NULL,
    target_value DECIMAL(18,4) NOT NULL,
    predicted_value DECIMAL(18,4),                      -- 预测值
    weight DECIMAL(5,2) DEFAULT 1.0,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(portfolio_id, kpi_type)
);
CREATE INDEX IF NOT EXISTS idx_cpk_portfolio ON campaign_portfolio_kpi(portfolio_id);

-- ==================== 5. Campaign Plan ====================

CREATE TABLE IF NOT EXISTS campaign_plan (
    id VARCHAR(64) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL REFERENCES campaign_workspace(id) ON DELETE CASCADE,
    goal_id VARCHAR(64) REFERENCES campaign_goal(id),
    initiative_id VARCHAR(64) REFERENCES campaign_initiative(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) DEFAULT 'DRAFT',                 -- DRAFT / GENERATED / APPROVED / REJECTED / EXECUTING / COMPLETED
    total_budget DECIMAL(18,4),
    expected_roi DECIMAL(10,4),
    strategy_json JSONB,
    allocation_json JSONB,
    graph_json JSONB,                                   -- Canvas DAG
    forecast_json JSONB,
    -- Zeebe 相关
    zeebe_process_id VARCHAR(100),
    zeebe_version INT,
    zeebe_instance_key BIGINT,
    created_by VARCHAR(64),
    approved_by VARCHAR(64),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cplan_workspace ON campaign_plan(workspace_id);
CREATE INDEX IF NOT EXISTS idx_cplan_goal ON campaign_plan(goal_id);
CREATE INDEX IF NOT EXISTS idx_cplan_initiative ON campaign_plan(initiative_id);
CREATE INDEX IF NOT EXISTS idx_cplan_status ON campaign_plan(status);

COMMENT ON TABLE campaign_plan IS '活动计划 — 核心执行计划，包含 DAG 画布和 Zeebe 流程信息';

-- ==================== 6. External Signals ====================

CREATE TABLE IF NOT EXISTS external_signal (
    id VARCHAR(64) PRIMARY KEY,
    signal_type VARCHAR(64),
    severity VARCHAR(32),                               -- INFO / WARNING / CRITICAL
    source_skill VARCHAR(64),
    target_entity VARCHAR(255),
    raw_payload JSONB,
    impact_factor DECIMAL(5,4),                         -- 影响系数，>1 增强机会
    affected_segments TEXT[],
    recommended_action TEXT,
    expires_at TIMESTAMPTZ,
    is_consumed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_es_type ON external_signal(signal_type);
CREATE INDEX IF NOT EXISTS idx_es_severity ON external_signal(severity);
CREATE INDEX IF NOT EXISTS idx_es_consumed ON external_signal(is_consumed);

COMMENT ON TABLE external_signal IS '外部信号 — 竞品、舆情、政策等 AI 技能采集的外部信号';

-- ==================== 7. User Attention Budget (频控) ====================

CREATE TABLE IF NOT EXISTS user_attention_budget (
    user_id VARCHAR(64) NOT NULL,
    date DATE NOT NULL,
    channel VARCHAR(32) NOT NULL,
    max_exposure INT NOT NULL DEFAULT 10,
    used_exposure INT NOT NULL DEFAULT 0,
    PRIMARY KEY(user_id, date, channel)
);
COMMENT ON TABLE user_attention_budget IS '用户注意力预算 — 按用户+日期+渠道的曝光频控';

-- ==================== 8. Content & Compliance ====================

-- 8.1 内容素材
CREATE TABLE IF NOT EXISTS campaign_content_asset (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    asset_name VARCHAR(255) NOT NULL,
    asset_type VARCHAR(32),                             -- EMAIL_HTML / SMS_TEXT / PUSH_JSON
    channel VARCHAR(32),
    subject_line VARCHAR(255),
    body_text TEXT,
    variable_schema JSONB,                              -- 变量占位符定义
    status VARCHAR(32) DEFAULT 'DRAFT',                 -- DRAFT / PENDING_APPROVAL / APPROVED / REJECTED
    created_by VARCHAR(64),
    approved_by VARCHAR(64),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cca_program ON campaign_content_asset(program_code);
CREATE INDEX IF NOT EXISTS idx_cca_status ON campaign_content_asset(status);

-- 8.2 审批记录
CREATE TABLE IF NOT EXISTS campaign_approval_record (
    id VARCHAR(64) PRIMARY KEY,
    asset_id VARCHAR(64) REFERENCES campaign_content_asset(id),
    plan_id VARCHAR(64) REFERENCES campaign_plan(id),
    node_id VARCHAR(64),
    requester_id VARCHAR(64) NOT NULL,
    approver_id VARCHAR(64),
    action VARCHAR(32) NOT NULL,                        -- SUBMITTED / APPROVED / REJECTED / REVOKED
    comment TEXT,
    snapshot_before JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_car_asset ON campaign_approval_record(asset_id);
CREATE INDEX IF NOT EXISTS idx_car_plan ON campaign_approval_record(plan_id);

-- ==================== 9. Human Intervention ====================

CREATE TABLE IF NOT EXISTS campaign_intervention_command (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    plan_id VARCHAR(64) REFERENCES campaign_plan(id),
    target_node_id VARCHAR(64),
    command_type VARCHAR(32) NOT NULL,                  -- PAUSE / RESUME / CANCEL / SKIP_NODE / UPDATE_CONFIG
    reason TEXT,
    operator_id VARCHAR(64) NOT NULL,
    previous_state_snapshot JSONB,
    new_config_snapshot JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    executed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_cic_plan ON campaign_intervention_command(plan_id);
CREATE INDEX IF NOT EXISTS idx_cic_type ON campaign_intervention_command(command_type);

-- ==================== 10. Execution Dedup ====================

CREATE TABLE IF NOT EXISTS execution_dedup (
    id VARCHAR(64) PRIMARY KEY,
    dedup_key VARCHAR(255) NOT NULL UNIQUE,              -- campaign_id + node_id + user_id + channel
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ed_key ON execution_dedup(dedup_key);

-- ============================================================================
-- 数据初始化：预置示例数据（仅开发环境使用）
-- ============================================================================
-- 注意：以下数据仅在开发环境中通过 Flyway 的 repeatable migration 或 seed 脚本注入
