CREATE TABLE calls (
    id               BIGSERIAL       PRIMARY KEY,
    call_id          VARCHAR(200)    NOT NULL UNIQUE,
    caller_number    VARCHAR(50),
    callee_number    VARCHAR(50),
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    duration_seconds INTEGER,
    status           VARCHAR(20)     NOT NULL DEFAULT 'RINGING',
    stt_provider     VARCHAR(50),
    stt_mode         VARCHAR(20),
    rx_file_path     VARCHAR(500),
    tx_file_path     VARCHAR(500),
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_calls_start_time ON calls (start_time DESC);
CREATE INDEX idx_calls_caller_number ON calls (caller_number, start_time DESC);
CREATE INDEX idx_calls_status_active ON calls (status, start_time);
