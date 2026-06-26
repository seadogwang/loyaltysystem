-- ============================================================================
-- Campaign Tools 数据仓库宽表 — 会员/订单/行为/积分同步表
-- ============================================================================
-- 执行: psql -U loyalty -d loyalty_dev -f V1_16__campaign_warehouse.sql

-- 1. 会员汇总宽表
CREATE TABLE IF NOT EXISTS campaign_member_dim (
    member_id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,

    -- 基本信息
    status VARCHAR(20) DEFAULT 'ACTIVE',            -- ACTIVE / INACTIVE / BLACKLISTED
    tier_code VARCHAR(32),
    tier_name VARCHAR(100),
    segment_code VARCHAR(32),
    registered_at TIMESTAMPTZ,

    -- RFM 字段
    last_order_date DATE,
    recency_days INT,                               -- 最近购买距今天数
    total_order_count INT DEFAULT 0,                -- 累计购买次数（Frequency）
    total_order_amount DECIMAL(18,2) DEFAULT 0,     -- 累计购买金额（Monetary）
    avg_order_value DECIMAL(18,2) DEFAULT 0,        -- 平均客单价

    -- 积分
    total_points_earned DECIMAL(18,2) DEFAULT 0,
    total_points_redeemed DECIMAL(18,2) DEFAULT 0,
    available_points DECIMAL(18,2) DEFAULT 0,

    -- ML 预测分
    churn_probability DECIMAL(6,5),
    uplift_score DECIMAL(6,5),
    conversion_probability DECIMAL(6,5),

    -- RFM 评分
    rfm_recency_score INT,
    rfm_frequency_score INT,
    rfm_monetary_score INT,
    rfm_total_score DECIMAL(8,2),

    -- 扩展属性
    ext_attributes JSONB DEFAULT '{}',

    -- 同步控制
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cmd_program ON campaign_member_dim(program_code);
CREATE INDEX IF NOT EXISTS idx_cmd_status ON campaign_member_dim(status);
CREATE INDEX IF NOT EXISTS idx_cmd_segment ON campaign_member_dim(segment_code);
CREATE INDEX IF NOT EXISTS idx_cmd_tier ON campaign_member_dim(tier_code);
CREATE INDEX IF NOT EXISTS idx_cmd_recency ON campaign_member_dim(recency_days);
CREATE INDEX IF NOT EXISTS idx_cmd_order_amount ON campaign_member_dim(total_order_amount DESC);

COMMENT ON TABLE campaign_member_dim IS '会员汇总宽表 — 含 RFM、等级、分群、ML 预测分';
COMMENT ON COLUMN campaign_member_dim.recency_days IS '最近购买距今天数';
COMMENT ON COLUMN campaign_member_dim.total_order_count IS '累计购买次数（Frequency）';
COMMENT ON COLUMN campaign_member_dim.total_order_amount IS '累计购买金额（Monetary）';
COMMENT ON COLUMN campaign_member_dim.rfm_recency_score IS 'RFM Recency 评分 (1-5)';
COMMENT ON COLUMN campaign_member_dim.rfm_frequency_score IS 'RFM Frequency 评分 (1-5)';
COMMENT ON COLUMN campaign_member_dim.rfm_monetary_score IS 'RFM Monetary 评分 (1-5)';

-- 2. 订单事实宽表
CREATE TABLE IF NOT EXISTS campaign_order_fact (
    order_id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    order_date TIMESTAMPTZ,
    order_amount DECIMAL(18,2),
    order_status VARCHAR(20),                       -- COMPLETED / REFUNDED / CANCELLED
    channel VARCHAR(32),
    product_category VARCHAR(64),
    store_code VARCHAR(32),
    ext_attributes JSONB DEFAULT '{}',
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cof_program ON campaign_order_fact(program_code);
CREATE INDEX IF NOT EXISTS idx_cof_member ON campaign_order_fact(member_id);
CREATE INDEX IF NOT EXISTS idx_cof_date ON campaign_order_fact(order_date);
CREATE INDEX IF NOT EXISTS idx_cof_status ON campaign_order_fact(order_status);

COMMENT ON TABLE campaign_order_fact IS '订单事实宽表';

-- 3. 行为事件宽表
CREATE TABLE IF NOT EXISTS campaign_behavior_fact (
    event_id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64),                         -- PAGE_VIEW / LOGIN / CLICK / SHARE
    event_time TIMESTAMPTZ,
    channel VARCHAR(32),
    session_id VARCHAR(64),
    event_payload JSONB,
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cbf_program ON campaign_behavior_fact(program_code);
CREATE INDEX IF NOT EXISTS idx_cbf_member ON campaign_behavior_fact(member_id);
CREATE INDEX IF NOT EXISTS idx_cbf_type ON campaign_behavior_fact(event_type);
CREATE INDEX IF NOT EXISTS idx_cbf_time ON campaign_behavior_fact(event_time);

COMMENT ON TABLE campaign_behavior_fact IS '行为事件宽表';

-- 4. 积分汇总宽表
CREATE TABLE IF NOT EXISTS campaign_points_summary (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    point_type_code VARCHAR(32),
    point_type_name VARCHAR(100),
    total_earned DECIMAL(18,2) DEFAULT 0,
    total_redeemed DECIMAL(18,2) DEFAULT 0,
    available_balance DECIMAL(18,2) DEFAULT 0,
    expiring_soon DECIMAL(18,2) DEFAULT 0,         -- 即将过期积分
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cps_program ON campaign_points_summary(program_code);
CREATE INDEX IF NOT EXISTS idx_cps_member ON campaign_points_summary(member_id);
CREATE INDEX IF NOT EXISTS idx_cps_type ON campaign_points_summary(point_type_code);

COMMENT ON TABLE campaign_points_summary IS '积分汇总宽表';

-- 5. 等级变更明细表
CREATE TABLE IF NOT EXISTS campaign_tier_change_detail (
    id VARCHAR(64) PRIMARY KEY,
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    from_tier_code VARCHAR(32),
    to_tier_code VARCHAR(32),
    change_type VARCHAR(32),                        -- UPGRADE / DOWNGRADE / MANUAL
    change_time TIMESTAMPTZ,
    reason VARCHAR(255),
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ctcd_program ON campaign_tier_change_detail(program_code);
CREATE INDEX IF NOT EXISTS idx_ctcd_member ON campaign_tier_change_detail(member_id);
CREATE INDEX IF NOT EXISTS idx_ctcd_time ON campaign_tier_change_detail(change_time);

COMMENT ON TABLE campaign_tier_change_detail IS '等级变更明细宽表';
