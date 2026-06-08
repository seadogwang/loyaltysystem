CREATE TABLE IF NOT EXISTS member_fifo_cursor (
    program_code VARCHAR(32) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    last_tx_id BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (program_code, member_id, account_type)
);