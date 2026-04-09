CREATE TABLE stt_results (
    id               BIGSERIAL       PRIMARY KEY,
    call_id          VARCHAR(200)    NOT NULL,
    channel          VARCHAR(10)     NOT NULL,
    transcript       TEXT,
    confidence       FLOAT,
    start_offset_ms  BIGINT,
    end_offset_ms    BIGINT,
    stt_mode         VARCHAR(20)     NOT NULL,
    is_final         BOOLEAN         NOT NULL DEFAULT FALSE,
    error_message    VARCHAR(500),
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_stt_results_call_id FOREIGN KEY (call_id) REFERENCES calls(call_id) ON DELETE CASCADE
);

CREATE INDEX idx_stt_results_call_id ON stt_results (call_id, channel, created_at);
CREATE INDEX idx_stt_results_final ON stt_results (call_id, is_final) WHERE is_final = TRUE;
