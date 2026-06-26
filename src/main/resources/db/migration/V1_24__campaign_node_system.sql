-- ============================================================================
-- Node Config Schema System — Node Definition + Execution History
-- ============================================================================

CREATE TABLE IF NOT EXISTS campaign_node_definition (
    id VARCHAR(64) PRIMARY KEY,
    node_type VARCHAR(64) NOT NULL UNIQUE,
    category VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    icon VARCHAR(32),
    color VARCHAR(16),
    config_schema JSONB NOT NULL,
    input_schema JSONB,
    output_schema JSONB,
    zeebe_worker_type VARCHAR(64),
    version INT DEFAULT 1,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cnd_type ON campaign_node_definition(node_type);
CREATE INDEX IF NOT EXISTS idx_cnd_category ON campaign_node_definition(category);
CREATE INDEX IF NOT EXISTS idx_cnd_status ON campaign_node_definition(status);

COMMENT ON TABLE campaign_node_definition IS '节点类型定义 — 含 JSON Schema 的可插拔执行算子注册表';

CREATE TABLE IF NOT EXISTS campaign_node_execution_history (
    id VARCHAR(64) PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_type VARCHAR(64) NOT NULL,
    execution_id VARCHAR(64),
    job_key BIGINT,
    input_snapshot JSONB,
    output_snapshot JSONB,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    duration_ms BIGINT,
    worker_host VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cneh_plan ON campaign_node_execution_history(plan_id);
CREATE INDEX IF NOT EXISTS idx_cneh_node ON campaign_node_execution_history(node_id);
CREATE INDEX IF NOT EXISTS idx_cneh_status ON campaign_node_execution_history(status);
CREATE INDEX IF NOT EXISTS idx_cneh_start ON campaign_node_execution_history(start_time DESC);

COMMENT ON TABLE campaign_node_execution_history IS '节点执行历史 — 审计与调试用执行记录';

-- ==================== Seed Data: 12 Node Types ====================

INSERT INTO campaign_node_definition (id, node_type, category, name, description, icon, color, config_schema, zeebe_worker_type) VALUES
('ndef_start', 'START', 'END', '开始', '流程入口节点', '🟢', '#22c55e', '{"type":"object","properties":{}}', NULL),
('ndef_end', 'END', 'END', '结束', '流程结束节点', '🔴', '#ef4444', '{"type":"object","properties":{}}', NULL),
('ndef_audience', 'AUDIENCE_FILTER', 'INPUT', '人群筛选', '根据分群和条件筛选目标用户', '👥', '#3b82f6', '{"type":"object","required":["segmentCode"],"properties":{"segmentCode":{"type":"string","minLength":1},"limit":{"type":"integer","minimum":1,"maximum":100000,"default":10000},"filters":{"type":"array","items":{"type":"object","properties":{"field":{"type":"string"},"operator":{"enum":["eq","ne","gt","gte","lt","lte","contains","in"]},"value":{}}}}}', 'campaign-audience-filter'),
('ndef_condition', 'CONDITION', 'LOGIC', '条件分支', '基于条件判断分流', '🔀', '#eab308', '{"type":"object","required":["field","operator","value"],"properties":{"field":{"type":"string"},"operator":{"enum":["eq","ne","gt","gte","lt","lte","contains","in"]},"value":{}}}', NULL),
('ndef_split', 'SPLIT', 'LOGIC', '并行分支', '将流程拆分为多个并行分支', '📋', '#f97316', '{"type":"object","properties":{"branchCount":{"type":"integer","minimum":2,"maximum":10,"default":2}}}', NULL),
('ndef_merge', 'MERGE', 'LOGIC', '合并节点', '合并多个并行分支', '🔗', '#06b6d4', '{"type":"object","properties":{"waitForAll":{"type":"boolean","default":true}}}', NULL),
('ndef_ai_score', 'AI_SCORE', 'AI', 'AI 评分', '使用 ML 模型预测用户行为', '🤖', '#a855f7', '{"type":"object","required":["modelType"],"properties":{"modelType":{"enum":["churn","uplift","conversion","custom"]},"threshold":{"type":"number","minimum":0,"maximum":1,"default":0.7},"batchSize":{"type":"integer","minimum":1,"maximum":1000,"default":500}}}', 'campaign-ai-score'),
('ndef_send_email', 'SEND_EMAIL', 'ACTION', '发送邮件', '通过邮件渠道触达用户', '✉️', '#22c55e', '{"type":"object","required":["assetId"],"properties":{"assetId":{"type":"string","minLength":1},"requireApproval":{"type":"boolean","default":false},"retryCount":{"type":"integer","minimum":0,"maximum":5,"default":3},"rateLimit":{"type":"integer","minimum":1,"maximum":10000,"default":1000}}}', 'campaign-send-email'),
('ndef_offer_points', 'OFFER_POINTS', 'ACTION', '发放积分', '向用户发放忠诚度积分', '⭐', '#eab308', '{"type":"object","required":["pointType","amount"],"properties":{"pointType":{"enum":["TIER_POINTS","REWARD_POINTS","CAMPAIGN_BONUS"]},"amount":{"type":"number","minimum":0.01},"reason":{"type":"string","maxLength":255}}}', 'campaign-offer-points'),
('ndef_offer_coupon', 'OFFER_COUPON', 'ACTION', '发放优惠券', '向用户发放优惠券', '🎫', '#ca8a04', '{"type":"object","properties":{"couponTemplateId":{"type":"string"},"quantity":{"type":"integer","minimum":1,"default":1}}}', 'campaign-offer-coupon'),
('ndef_delay', 'DELAY', 'CONTROL', '延迟等待', '等待指定时长后继续', '⏰', '#6b7280', '{"type":"object","required":["duration","unit"],"properties":{"duration":{"type":"integer","minimum":1},"unit":{"enum":["milliseconds","seconds","minutes","hours","days"],"default":"minutes"}}}', 'campaign-delay'),
('ndef_approval', 'APPROVAL', 'CONTROL', '人工审批', '暂停流程等待人工审批', '✅', '#0891b2', '{"type":"object","properties":{"approverId":{"type":"string"},"approverGroup":{"type":"string"},"timeoutHours":{"type":"integer","minimum":1,"maximum":168,"default":24},"autoReject":{"type":"boolean","default":true}}}', 'campaign-approval')
ON CONFLICT (node_type) DO NOTHING;
