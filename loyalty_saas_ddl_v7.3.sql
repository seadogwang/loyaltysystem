-- ============================================================
-- Loyalty SaaS v7.3 — 完整 DDL 脚本
-- 版本: 7.3
-- 日期: 2026-06-08 (含 fix_bug_1.md 交易/订单/行为事件扩展)
-- 来源: LoyaltyDesign20260606_v7.3.md
-- 说明: 基于设计文档 + 两份代码评审报告修复后的完整物理模型
-- ============================================================

-- ====================
-- 1. program — 租户/忠诚度计划表
-- ====================
CREATE TABLE program (
    id SERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL UNIQUE,
    config_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ====================
-- 2. member — 会员主表（双轨模型：核心字段 + JSONB动态扩展）
-- ====================
CREATE TABLE member (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100),
    gender VARCHAR(10),
    birthday DATE,
    tier_code VARCHAR(16),
    status VARCHAR(16) NOT NULL DEFAULT 'ENROLLED',
    schema_version VARCHAR(16),
    ext_attributes JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_member_program ON member(program_code);
CREATE INDEX idx_member_status ON member(program_code, status);
CREATE INDEX idx_member_tier ON member(program_code, tier_code);
CREATE INDEX idx_member_ext ON member USING GIN (ext_attributes);

-- ====================
-- 3. member_unique_key — 全渠道唯一键表（One-ID核心，分区表）
-- ====================
CREATE TABLE member_unique_key (
    id BIGSERIAL,
    program_code VARCHAR(32) NOT NULL,
    key_type VARCHAR(32) NOT NULL,
    key_value VARCHAR(256) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    is_strong BOOLEAN DEFAULT true,
    is_verified BOOLEAN DEFAULT false,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, key_type, key_value)
) PARTITION BY LIST (key_type);

CREATE TABLE member_unique_key_mobile
    PARTITION OF member_unique_key
    FOR VALUES IN ('MOBILE_PLAIN');
CREATE TABLE member_unique_key_wechat
    PARTITION OF member_unique_key
    FOR VALUES IN ('WECHAT_OPENID', 'WECHAT_UNIONID');
CREATE TABLE member_unique_key_tmall_ouid
    PARTITION OF member_unique_key
    FOR VALUES IN ('TMALL_OUID');
CREATE TABLE member_unique_key_tmall_omid
    PARTITION OF member_unique_key
    FOR VALUES IN ('TMALL_OMID');
CREATE TABLE member_unique_key_jd
    PARTITION OF member_unique_key
    FOR VALUES IN ('JD_PIN');
CREATE TABLE member_unique_key_douyin
    PARTITION OF member_unique_key
    FOR VALUES IN ('DOUYIN_OPENID');
CREATE TABLE member_unique_key_cold
    PARTITION OF member_unique_key
    FOR VALUES IN ('TMALL_MOBILE_MD5', 'JD_MOBILE_ENCRYPT', 'DOUYIN_MOBILE_MASK');
CREATE TABLE member_unique_key_other
    PARTITION OF member_unique_key DEFAULT;

CREATE INDEX idx_muk_member ON member_unique_key(program_code, target_member_id);

-- ====================
-- 4. point_type_definition — 积分类型定义表
-- ====================
CREATE TABLE point_type_definition (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    type_code VARCHAR(32) NOT NULL,
    type_name VARCHAR(100) NOT NULL,
    is_redeemable BOOLEAN DEFAULT false,
    is_tier_calc BOOLEAN DEFAULT false,
    is_transferable BOOLEAN DEFAULT false,
    allow_negative BOOLEAN DEFAULT false,
    expiry_mode VARCHAR(20) DEFAULT 'NATURAL_YEAR',
    expiry_value INT DEFAULT 12,
    credit_limit NUMERIC(18,4) DEFAULT 0,
    visible BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, type_code)
);

-- ====================
-- 5. member_account — 会员账户表（含冻结/合并状态，无实时余额）
-- ====================
CREATE TABLE member_account (
    account_id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    overdraft_limit NUMERIC(18,4) DEFAULT 0,
    credit_limit NUMERIC(18,4) DEFAULT 0,
    credit_used NUMERIC(18,4) DEFAULT 0,
    total_accrued NUMERIC(18,4) DEFAULT 0,
    total_redeemed NUMERIC(18,4) DEFAULT 0,
    total_expired NUMERIC(18,4) DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    frozen_status VARCHAR(16) DEFAULT 'ACTIVE',
    account_status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, member_id, account_type)
);

-- ====================
-- 6. account_transaction — 积分流水表（分区表，FIFO核销核心）
-- ====================
CREATE TABLE account_transaction (
    id BIGSERIAL,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    remaining_amount NUMERIC(18,4),
    expires_at TIMESTAMPTZ,
    rule_id VARCHAR(64),
    rule_snapshot_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE UNIQUE INDEX uk_at_idempotent_operation ON account_transaction(program_code, operation_key, created_at);
CREATE INDEX idx_at_program_member_type ON account_transaction(program_code, member_id, account_type);

-- ====================
-- 7. redemption_allocation — 核销分摊明细表（FIFO溯源）
-- ====================
CREATE TABLE redemption_allocation (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    redemption_tx_id BIGINT NOT NULL,
    accrual_tx_id BIGINT NOT NULL,
    allocated_amount NUMERIC(18,4) NOT NULL
);

-- ====================
-- 8. member_fifo_cursor — FIFO游标表（分区表）
-- ====================
CREATE TABLE member_fifo_cursor (
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    last_tx_id BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (program_code, member_id, account_type)
) PARTITION BY LIST (program_code);

-- ====================
-- 9. member_merge_task — 合并任务表（Saga状态机）
-- ====================
CREATE TABLE member_merge_task (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    source_member_id VARCHAR(64) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, source_member_id)
);

-- ====================
-- 10. member_merge_audit — 合并审计日志表
-- ====================
CREATE TABLE member_merge_audit (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    source_member_id VARCHAR(64) NOT NULL,
    target_member_id VARCHAR(64) NOT NULL,
    operator VARCHAR(64),
    status VARCHAR(16),
    points_transferred NUMERIC(18,4),
    coupons_transferred INTEGER,
    tiers_transferred TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message TEXT
);

-- ====================
-- 11. tier_change_log — 等级变更历史表
-- ====================
CREATE TABLE tier_change_log (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    old_tier VARCHAR(16),
    new_tier VARCHAR(16) NOT NULL,
    change_reason VARCHAR(50) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_tier_log_member_time ON tier_change_log(member_id, changed_at);

-- ====================
-- 12. rule — 规则定义表
-- ====================
CREATE TABLE rule (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    rule_name VARCHAR(200),
    drl_content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    salience INT DEFAULT 0,
    activation_group VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, rule_id)
);

-- ====================
-- 13. rule_snapshot — 规则版本快照表
-- ====================
CREATE TABLE rule_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    drl_content TEXT NOT NULL,
    salience INT,
    activation_group VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================
-- 14. cascade_recalc_job — 级联重算任务表
-- ====================
CREATE TABLE cascade_recalc_job (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    reverse_event_id VARCHAR(64) NOT NULL,
    reverse_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    max_retry_count INT DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================
-- 15. compensation_log — 补偿日志表
-- ====================
CREATE TABLE compensation_log (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    job_id VARCHAR(64) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    points_to_deduct NUMERIC(18,4) DEFAULT 0,
    new_tier VARCHAR(16),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, job_id)
);

-- ====================
-- 16. negative_pending — 负资产追偿工单表
-- ====================
CREATE TABLE negative_pending (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    pending_amount NUMERIC(18,4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    settled_at TIMESTAMPTZ
);
CREATE INDEX idx_negative_pending_member ON negative_pending(program_code, member_id);

-- ====================
-- 17. coupon_instance — 优惠券实例表
-- ====================
CREATE TABLE coupon_instance (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    coupon_id VARCHAR(64) NOT NULL,
    coupon_name VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    expires_at TIMESTAMPTZ,
    merge_task_id BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_coupon_member ON coupon_instance(program_code, member_id);

-- ====================
-- 18. member_tag — 会员标签表
-- ====================
CREATE TABLE member_tag (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    tag_code VARCHAR(64) NOT NULL,
    tag_name VARCHAR(200),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, member_id, tag_code)
);

-- ====================
-- 19. channel_adapter_config — 渠道适配器配置表
-- ====================
CREATE TABLE channel_adapter_config (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    mapping_mode VARCHAR(20) NOT NULL DEFAULT 'VISUAL' CHECK (mapping_mode IN ('VISUAL', 'SCRIPT')),
    transform_script TEXT,
    spi_webhook_url VARCHAR(500),
    auth_config JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, channel)
);

-- ====================
-- 20. channel_spi_log — SPI调用审计日志表
-- ====================
CREATE TABLE channel_spi_log (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    request_id VARCHAR(100),
    http_headers JSONB,
    request_payload JSONB,
    response_payload JSONB,
    status VARCHAR(20) NOT NULL,
    execution_time_ms INT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================
-- 21. event_inbox — 事件收件箱表（含独立列用于列表展示与过滤）
-- ====================
CREATE TABLE event_inbox (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(300) NOT NULL,
    payload JSONB NOT NULL,
    transform_logs JSONB,
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 3,
    last_error TEXT,
    reject_reason VARCHAR(50),
    next_retry_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    -- 独立列：用于列表展示与高频过滤
    member_id VARCHAR(64),
    event_type VARCHAR(32),
    event_time TIMESTAMPTZ,
    channel VARCHAR(32),
    order_id VARCHAR(64),
    total_amount NUMERIC(18,4),
    order_status VARCHAR(32),
    paid_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    behavior_code VARCHAR(64)
);
CREATE INDEX idx_event_inbox_order_id ON event_inbox(order_id);
CREATE INDEX idx_event_inbox_member_id ON event_inbox(member_id);
CREATE INDEX idx_event_inbox_event_time ON event_inbox(event_time);
CREATE INDEX idx_event_inbox_channel ON event_inbox(channel);
CREATE INDEX idx_event_inbox_order_status ON event_inbox(order_status);
CREATE INDEX idx_event_inbox_paid_at ON event_inbox(paid_at);
CREATE INDEX idx_event_inbox_member_time ON event_inbox(member_id, event_time DESC);
CREATE INDEX idx_event_inbox_type_time ON event_inbox(event_type, event_time);
CREATE INDEX idx_event_inbox_behavior_code ON event_inbox(behavior_code);

-- ====================
-- 22. program_schema — Schema版本管理表
-- ====================
CREATE TABLE program_schema (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    version VARCHAR(16) NOT NULL,
    field_schema JSONB NOT NULL,
    entity_relations JSONB,
    api_config JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (program_code, entity_type, version)
);

-- ====================
-- 23. behavior_code — 行为代码字典表
-- ====================
CREATE TABLE behavior_code (
    id BIGSERIAL PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    behavior_code VARCHAR(64) NOT NULL,
    behavior_name VARCHAR(128) NOT NULL,
    description TEXT,
    default_points INT,
    freq_limit_type VARCHAR(20) DEFAULT 'NONE',
    freq_limit_value INT,
    freq_limit_seconds INT,
    applicable_tiers JSONB,
    status VARCHAR(20) DEFAULT 'ENABLED',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    UNIQUE (program_code, behavior_code)
);

-- ====================
-- 24. idempotent_record — 幂等记录表（分区表，program_code而非tenant_id）
-- ====================
CREATE TABLE idempotent_record (
    program_code VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64),
    response_data JSONB,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    PRIMARY KEY (program_code, idempotency_key)
) PARTITION BY LIST (program_code);

-- ============================================================
-- 表清单汇总 (25张表)
-- ============================================================
-- | # | 表名                    | 用途简述                                          | 分区方式                |
-- |---|------------------------|-------------------------------------------------|----------------------|
-- | 1 | program                | 租户/忠诚度计划                                    | 无                   |
-- | 2 | member                 | 会员主表(双轨模型)                                  | 无                   |
-- | 3 | member_unique_key      | 全渠道唯一键(One-ID核心)                            | PARTITION BY LIST    |
-- | 4 | point_type_definition  | 积分类型定义                                       | 无                   |
-- | 5 | member_account         | 会员账户(含冻结/合并状态)                             | 无                   |
-- | 6 | account_transaction    | 积分流水(FIFO核销核心)                              | PARTITION BY RANGE   |
-- | 7 | redemption_allocation  | 核销分摊明细(FIFO溯源)                              | 无                   |
-- | 8 | member_fifo_cursor     | FIFO游标                                         | PARTITION BY LIST    |
-- | 9 | member_merge_task      | 合并任务(Saga状态机)                                | 无                   |
-- | 10| member_merge_audit     | 合并审计日志                                       | 无                   |
-- | 11| tier_change_log        | 等级变更历史                                       | 无                   |
-- | 12| rule                   | 规则定义                                          | 无                   |
-- | 13| rule_snapshot          | 规则版本快照                                       | 无                   |
-- | 14| cascade_recalc_job     | 级联重算任务                                       | 无                   |
-- | 15| compensation_log       | 补偿日志                                          | 无                   |
-- | 16| negative_pending       | 负资产追偿工单                                       | 无                   |
-- | 17| coupon_instance        | 优惠券实例                                         | 无                   |
-- | 18| member_tag             | 会员标签                                          | 无                   |
-- | 19| channel_adapter_config | 渠道适配器配置                                      | 无                   |
-- | 20| channel_spi_log        | SPI调用审计日志                                     | 无                   |
-- | 21| event_inbox            | 事件收件箱(含独立列+JSONB混合存储)                     | 无                   |
-- | 22| program_schema         | Schema版本管理                                    | 无                   |
-- | 23| behavior_code          | 行为代码字典                                       | 无                   |
-- | 24| idempotent_record      | 幂等记录                                          | PARTITION BY LIST    |