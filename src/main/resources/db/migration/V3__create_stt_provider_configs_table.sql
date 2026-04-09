CREATE TABLE stt_provider_configs (
    id                   BIGSERIAL       PRIMARY KEY,
    provider_name        VARCHAR(50)     NOT NULL UNIQUE,
    display_name         VARCHAR(100)    NOT NULL,
    is_active            BOOLEAN         NOT NULL DEFAULT FALSE,
    language_code        VARCHAR(20)     NOT NULL DEFAULT 'ko-KR',
    config_json          TEXT,
    supports_streaming   BOOLEAN         NOT NULL DEFAULT FALSE,
    supports_batch       BOOLEAN         NOT NULL DEFAULT TRUE,
    description          TEXT,
    created_at           TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP       NOT NULL DEFAULT NOW()
);
