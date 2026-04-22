CREATE TABLE audio_files (
    id          BIGSERIAL       PRIMARY KEY,
    call_id     VARCHAR(200)    NOT NULL,
    channel     VARCHAR(10)     NOT NULL,
    file_path   VARCHAR(500)    NOT NULL,
    file_name   VARCHAR(200)    NOT NULL,
    file_size   BIGINT,
    duration_ms BIGINT,
    format      VARCHAR(10)     NOT NULL DEFAULT 'WAV',
    sample_rate INTEGER         NOT NULL DEFAULT 8000,
    is_deleted  BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audio_files_call_id FOREIGN KEY (call_id) REFERENCES calls(call_id) ON DELETE CASCADE,
    CONSTRAINT uq_audio_files_call_channel UNIQUE (call_id, channel)
);

CREATE INDEX idx_audio_files_call_id ON audio_files (call_id);
CREATE INDEX idx_audio_files_cleanup ON audio_files (created_at, is_deleted);
