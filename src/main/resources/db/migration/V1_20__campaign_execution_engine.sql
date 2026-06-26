-- ============================================================================
-- Campaign Execution Engine — Zeebe Instance + Task + Dedup Enhancement
-- ============================================================================

-- ==================== 1. Campaign Plan 扩展 ====================
ALTER TABLE campaign_plan ADD COLUMN IF NOT EXISTS zeebe_deploy_time TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_cpl_zeebe_instance ON campaign_plan(zeebe_instance_key);

-- ==================== 2. Zeebe 执行实例表 ====================

CREATE TABLE IF NOT EXISTS campaign_zeebe_instance (
    id VARCHAR(64) PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL,
    process_instance_key BIGINT NOT NULL UNIQUE,
    bpmn_process_id VARCHAR(100),
    version INT,
    status VARCHAR(32) NOT NULL,                     -- CREATED / RUNNING / COMPLETED / FAILED / CANCELLED / PAUSED
    variables JSONB,                                 -- 流程变量快照
    error_message TEXT,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_czi_plan ON campaign_zeebe_instance(plan_id);
CREATE INDEX IF NOT EXISTS idx_czi_key ON campaign_zeebe_instance(process_instance_key);
CREATE INDEX IF NOT EXISTS idx_czi_status ON campaign_zeebe_instance(status);
CREATE INDEX IF NOT EXISTS idx_czi_start ON campaign_zeebe_instance(start_time DESC);

COMMENT ON TABLE campaign_zeebe_instance IS 'Zeebe 流程实例 — 存储每次执行实例的运行时信息';
COMMENT ON COLUMN campaign_zeebe_instance.status IS 'CREATED / RUNNING / COMPLETED / FAILED / CANCELLED / PAUSED';

-- ==================== 3. Zeebe 任务执行明细表 ====================

CREATE TABLE IF NOT EXISTS campaign_zeebe_task (
    id VARCHAR(64) PRIMARY KEY,
    instance_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    job_key BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,                  -- 对应 Worker 类型
    task_name VARCHAR(255),
    node_id VARCHAR(64),                             -- Canvas 节点 ID
    status VARCHAR(32) NOT NULL,                     -- CREATED / COMPLETED / FAILED / RETRY
    input_variables JSONB,
    output_variables JSONB,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    worker_id VARCHAR(64),
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_czt_instance ON campaign_zeebe_task(instance_id);
CREATE INDEX IF NOT EXISTS idx_czt_plan ON campaign_zeebe_task(plan_id);
CREATE INDEX IF NOT EXISTS idx_czt_job ON campaign_zeebe_task(job_key);
CREATE INDEX IF NOT EXISTS idx_czt_status ON campaign_zeebe_task(status);
CREATE INDEX IF NOT EXISTS idx_czt_type ON campaign_zeebe_task(task_type);

COMMENT ON TABLE campaign_zeebe_task IS 'Zeebe 任务执行明细 — 记录每个 Job 的完整执行信息';
COMMENT ON COLUMN campaign_zeebe_task.status IS 'CREATED / COMPLETED / FAILED / RETRY';

-- ==================== 4. 执行去重表增强 ====================
-- execution_dedup 已在 V1_15 创建（仅含 id/dedup_key/created_at）
-- 补充缺失字段
ALTER TABLE execution_dedup ADD COLUMN IF NOT EXISTS plan_id VARCHAR(64);
ALTER TABLE execution_dedup ADD COLUMN IF NOT EXISTS node_id VARCHAR(64);
ALTER TABLE execution_dedup ADD COLUMN IF NOT EXISTS user_id VARCHAR(64);
ALTER TABLE execution_dedup ADD COLUMN IF NOT EXISTS channel VARCHAR(32);
ALTER TABLE execution_dedup ADD COLUMN IF NOT EXISTS ttl TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '7 days');
CREATE INDEX IF NOT EXISTS idx_ced_plan ON execution_dedup(plan_id);
CREATE INDEX IF NOT EXISTS idx_ced_user ON execution_dedup(user_id);
CREATE INDEX IF NOT EXISTS idx_ced_ttl ON execution_dedup(ttl);
