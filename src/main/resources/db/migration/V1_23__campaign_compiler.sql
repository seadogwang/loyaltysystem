-- ============================================================================
-- Canvas → BPMN Compiler — 编译日志 + Plan 扩展
-- ============================================================================

-- 1. 扩展 campaign_plan 表
ALTER TABLE campaign_plan ADD COLUMN IF NOT EXISTS compiled_bpmn_xml TEXT;
ALTER TABLE campaign_plan ADD COLUMN IF NOT EXISTS compiled_engine_type VARCHAR(32) DEFAULT 'ZEEBE';
ALTER TABLE campaign_plan ADD COLUMN IF NOT EXISTS compile_time TIMESTAMPTZ;
ALTER TABLE campaign_plan ADD COLUMN IF NOT EXISTS compile_version INT DEFAULT 1;

-- 2. 编译日志表
CREATE TABLE IF NOT EXISTS campaign_compile_log (
    id VARCHAR(64) PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL,
    engine_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,                    -- SUCCESS / FAILED / VALIDATION_ERROR
    node_count INT,
    edge_count INT,
    bpmn_size_bytes INT,
    validation_errors JSONB,
    validation_warnings JSONB,
    compile_duration_ms BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ccl_plan ON campaign_compile_log(plan_id);
CREATE INDEX IF NOT EXISTS idx_ccl_created ON campaign_compile_log(created_at DESC);

COMMENT ON TABLE campaign_compile_log IS 'Canvas → BPMN 编译日志 — 记录每次编译的结果与耗时';
