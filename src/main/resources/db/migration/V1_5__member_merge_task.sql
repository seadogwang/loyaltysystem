-- V1_5: 会员合并异步任务表
-- MergeTask 实体映射，支持 merge() 接口先创建任务、后台 Job 异步执行

CREATE TABLE IF NOT EXISTS member_merge_task (
    id              BIGSERIAL       PRIMARY KEY,
    program_code    VARCHAR(100)    NOT NULL,
    main_member_id  BIGINT          NOT NULL,
    duplicate_member_id BIGINT      NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'CREATED',  -- CREATED / PROCESSING / COMPLETED / FAILED
    error_message   VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mmt_program_status ON member_merge_task(program_code, status);
CREATE INDEX IF NOT EXISTS idx_mmt_main_member ON member_merge_task(program_code, main_member_id);

-- 多租户 RLS: 确保该表有 program_code 列，触发租户隔离
-- (如使用 PostgreSQL RLS，需对应 policy)