-- ============================================================================
-- Intervention Enhancement — Approval + Global Control
-- ============================================================================

-- 1. 干预审批表
CREATE TABLE IF NOT EXISTS campaign_intervention_approval (
    id VARCHAR(64) PRIMARY KEY,
    command_id VARCHAR(64) NOT NULL,
    approver_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,                    -- APPROVED / REJECTED
    comment TEXT,
    approved_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cia_command ON campaign_intervention_approval(command_id);
CREATE INDEX IF NOT EXISTS idx_cia_approver ON campaign_intervention_approval(approver_id);

COMMENT ON TABLE campaign_intervention_approval IS '干预审批 — 高风险操作的二级审批记录';

-- 2. 全局控制状态表
CREATE TABLE IF NOT EXISTS campaign_global_control (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL UNIQUE,
    throttle_enabled BOOLEAN DEFAULT FALSE,
    throttle_factor DECIMAL(3,2) DEFAULT 1.0,
    throttle_until TIMESTAMPTZ,
    kill_switch_enabled BOOLEAN DEFAULT FALSE,
    kill_switch_activated_at TIMESTAMPTZ,
    kill_switch_activated_by VARCHAR(64),
    kill_switch_reason TEXT,
    updated_by VARCHAR(64),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cgc_program ON campaign_global_control(program_code);

COMMENT ON TABLE campaign_global_control IS '全局控制状态 — 租户级限流/Kill Switch';

-- 3. 扩展 intervention_command 表
ALTER TABLE campaign_intervention_command ADD COLUMN IF NOT EXISTS severity VARCHAR(32) DEFAULT 'WARNING';
ALTER TABLE campaign_intervention_command ADD COLUMN IF NOT EXISTS operator_name VARCHAR(255);
ALTER TABLE campaign_intervention_command ADD COLUMN IF NOT EXISTS operator_role VARCHAR(64);
ALTER TABLE campaign_intervention_command ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE campaign_intervention_command ADD COLUMN IF NOT EXISTS executed_at TIMESTAMPTZ;
ALTER TABLE campaign_intervention_command ADD COLUMN IF NOT EXISTS executed_by VARCHAR(64);
ALTER TABLE campaign_intervention_command ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();
