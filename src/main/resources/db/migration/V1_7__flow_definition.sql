-- V1_6: 流程定义表 — LiteFlow 流程编排
-- 存储前端 React Flow 画布生成的 EL 表达式和完整画布状态

CREATE TABLE IF NOT EXISTS flow_definition (
    id              BIGSERIAL       PRIMARY KEY,
    program_code    VARCHAR(32)     NOT NULL,
    chain_name      VARCHAR(64)     NOT NULL,          -- ORDER_CHAIN, BEHAVIOR_CHAIN, REFUND_CHAIN
    chain_type      VARCHAR(32)     NOT NULL,          -- ORDER / BEHAVIOR / REFUND
    flow_graph      JSONB           NOT NULL,          -- { nodes: [], edges: [] } 完整画布状态
    el_expression   TEXT            NOT NULL,          -- 生成的 LiteFlow EL 表达式
    status          VARCHAR(20)     DEFAULT 'DRAFT',   -- DRAFT / PUBLISHED
    version         INT             DEFAULT 1,
    created_at      TIMESTAMPTZ     DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    UNIQUE(program_code, chain_name)
);

CREATE INDEX IF NOT EXISTS idx_fd_program ON flow_definition(program_code, chain_type);