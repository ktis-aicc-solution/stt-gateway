# STT-Gateway 데이터베이스 스키마 설계 (SCHEMA.md)

> **데이터베이스**: PostgreSQL
> **스키마**: `public`
> **문자 인코딩**: UTF-8

---

## 목차

1. [ER 다이어그램 (Entity-Relationship)](#1-er-다이어그램)
2. [테이블 상세 설계](#2-테이블-상세-설계)
3. [전체 DDL SQL](#3-전체-ddl-sql)
4. [인덱스 설계](#4-인덱스-설계)
5. [초기 데이터 (Seed Data)](#5-초기-데이터-seed-data)
6. [JPA Entity 매핑](#6-jpa-entity-매핑)
7. [자주 사용하는 쿼리](#7-자주-사용하는-쿼리)

---

## 1. ER 다이어그램

```
┌─────────────────────────┐       ┌──────────────────────────────┐
│         calls           │       │         stt_results          │
├─────────────────────────┤       ├──────────────────────────────┤
│ PK id          BIGSERIAL│       │ PK id              BIGSERIAL │
│    call_id     VARCHAR  │──┐    │ FK call_id         VARCHAR   │
│    caller_number VARCHAR│  │    │    channel         VARCHAR   │
│    callee_number VARCHAR│  │    │    transcript      TEXT      │
│    start_time  TIMESTAMP│  │    │    confidence      FLOAT     │
│    end_time    TIMESTAMP│  │    │    start_offset_ms BIGINT    │
│    duration_seconds INT │  │    │    end_offset_ms   BIGINT    │
│    status      VARCHAR  │  │    │    stt_mode        VARCHAR   │
│    stt_provider VARCHAR │  │    │    is_final        BOOLEAN   │
│    stt_mode    VARCHAR  │  │    │    error_message   VARCHAR   │
│    rx_file_path VARCHAR │  └───►│    created_at      TIMESTAMP │
│    tx_file_path VARCHAR │       └──────────────────────────────┘
│    created_at  TIMESTAMP│
│    updated_at  TIMESTAMP│       ┌──────────────────────────────┐
└─────────────────────────┘       │     stt_provider_configs     │
           │                      ├──────────────────────────────┤
           │ 1:N                  │ PK id           BIGSERIAL    │
           ▼                      │    provider_name VARCHAR     │
┌──────────────────────────┐      │    display_name  VARCHAR     │
│       audio_files        │      │    is_active     BOOLEAN     │
├──────────────────────────┤      │    language_code VARCHAR     │
│ PK id         BIGSERIAL  │      │    config_json   TEXT        │
│ FK call_id    VARCHAR    │      │    supports_streaming BOOL   │
│    channel    VARCHAR    │      │    supports_batch    BOOL    │
│    file_path  VARCHAR    │      │    description   TEXT        │
│    file_name  VARCHAR    │      │    created_at   TIMESTAMP    │
│    file_size  BIGINT     │      │    updated_at   TIMESTAMP    │
│    duration_ms BIGINT    │      └──────────────────────────────┘
│    format     VARCHAR    │
│    sample_rate INTEGER   │
│    is_deleted BOOLEAN    │
│    deleted_at TIMESTAMP  │
│    created_at TIMESTAMP  │
└──────────────────────────┘
```

**테이블 관계 설명:**
- `calls` ← `stt_results`: 1개의 콜에 여러 개의 STT 결과가 있음 (RX/TX, 구간별)
- `calls` ← `audio_files`: 1개의 콜에 최대 2개의 오디오 파일 (RX, TX)
- `stt_provider_configs`: 독립 테이블, STT 공급자 설정 관리 (활성 공급자 1개만 is_active=true)

---

## 2. 테이블 상세 설계

### 2.1 calls 테이블

> **역할**: 모든 통화 이력을 저장합니다. SIP Call-ID를 기준으로 통화를 식별합니다.

| 컬럼명 | 데이터 타입 | NULL | 기본값 | 설명 |
|--------|-----------|------|--------|------|
| `id` | BIGSERIAL | NOT NULL | 자동증가 | PK, 내부 식별자 |
| `call_id` | VARCHAR(200) | NOT NULL | - | SIP Call-ID (고유값) |
| `caller_number` | VARCHAR(50) | NULL | - | 발신자 번호 (SIP From, 고객 번호) |
| `callee_number` | VARCHAR(50) | NULL | - | 수신자 번호 (SIP To, 상담사 번호) |
| `start_time` | TIMESTAMP | NULL | - | 통화 시작 시각 (SIP INVITE 수신 시) |
| `end_time` | TIMESTAMP | NULL | - | 통화 종료 시각 (SIP BYE 수신 시) |
| `duration_seconds` | INTEGER | NULL | - | 통화 시간(초), 종료 시 계산 저장 |
| `status` | VARCHAR(20) | NOT NULL | `'RINGING'` | 상태: RINGING, ACTIVE, COMPLETED, ERROR |
| `stt_provider` | VARCHAR(50) | NULL | - | 사용된 STT 공급자 (예: `google`) |
| `stt_mode` | VARCHAR(20) | NULL | - | STT 방식: BATCH, STREAMING, BOTH |
| `rx_file_path` | VARCHAR(500) | NULL | - | 고객 음성 WAV 파일 경로 |
| `tx_file_path` | VARCHAR(500) | NULL | - | 상담사 음성 WAV 파일 경로 |
| `created_at` | TIMESTAMP | NOT NULL | `NOW()` | 레코드 생성 시각 |
| `updated_at` | TIMESTAMP | NOT NULL | `NOW()` | 레코드 최종 수정 시각 |

**status 값 설명:**

| 값 | 의미 |
|----|------|
| `RINGING` | SIP INVITE 수신, 통화 연결 중 |
| `ACTIVE` | SIP ACK 수신, 통화 진행 중 |
| `COMPLETED` | SIP BYE 수신, 정상 종료 |
| `ERROR` | 처리 중 오류 발생 |

---

### 2.2 stt_results 테이블

> **역할**: STT 변환 결과 텍스트를 저장합니다. 한 통화에 여러 결과가 쌓입니다 (구간별, RX/TX 각각).

| 컬럼명 | 데이터 타입 | NULL | 기본값 | 설명 |
|--------|-----------|------|--------|------|
| `id` | BIGSERIAL | NOT NULL | 자동증가 | PK |
| `call_id` | VARCHAR(200) | NOT NULL | - | FK → calls.call_id |
| `channel` | VARCHAR(10) | NOT NULL | - | 채널: RX(고객), TX(상담사) |
| `transcript` | TEXT | NULL | - | 변환된 텍스트 (한국어) |
| `confidence` | FLOAT | NULL | - | 인식 신뢰도 (0.0 ~ 1.0) |
| `start_offset_ms` | BIGINT | NULL | - | 통화 시작으로부터 텍스트 시작 시점 (ms) |
| `end_offset_ms` | BIGINT | NULL | - | 통화 시작으로부터 텍스트 종료 시점 (ms) |
| `stt_mode` | VARCHAR(20) | NOT NULL | - | 변환 방식: BATCH, STREAMING |
| `is_final` | BOOLEAN | NOT NULL | `false` | 최종 결과 여부 (스트리밍 중간결과 구분) |
| `error_message` | VARCHAR(500) | NULL | - | 변환 실패 시 오류 메시지 |
| `created_at` | TIMESTAMP | NOT NULL | `NOW()` | 레코드 생성 시각 |

**channel 값 설명:**

| 값 | 의미 |
|----|------|
| `RX` | Receive. 고객이 말한 음성 (상담사가 수신한 방향) |
| `TX` | Transmit. 상담사가 말한 음성 (고객이 수신한 방향) |

---

### 2.3 stt_provider_configs 테이블

> **역할**: 사용 가능한 STT 공급자 목록과 각 공급자의 설정(API 키 등)을 저장합니다.
> 화면에서 활성 공급자를 변경하면 이 테이블의 `is_active` 값이 바뀝니다.

| 컬럼명 | 데이터 타입 | NULL | 기본값 | 설명 |
|--------|-----------|------|--------|------|
| `id` | BIGSERIAL | NOT NULL | 자동증가 | PK |
| `provider_name` | VARCHAR(50) | NOT NULL | - | 공급자 코드 (예: `google`, `naver`, `etri`) |
| `display_name` | VARCHAR(100) | NOT NULL | - | 화면 표시명 (예: `Google Speech-to-Text`) |
| `is_active` | BOOLEAN | NOT NULL | `false` | 현재 사용 중인 공급자 여부 (1개만 true) |
| `language_code` | VARCHAR(20) | NOT NULL | `'ko-KR'` | 인식 언어 코드 |
| `config_json` | TEXT | NULL | - | 공급자별 설정 (JSON 형식, API 키 등) |
| `supports_streaming` | BOOLEAN | NOT NULL | `false` | 스트리밍 방식 지원 여부 |
| `supports_batch` | BOOLEAN | NOT NULL | `true` | 배치 방식 지원 여부 |
| `description` | TEXT | NULL | - | 공급자 설명 |
| `created_at` | TIMESTAMP | NOT NULL | `NOW()` | 레코드 생성 시각 |
| `updated_at` | TIMESTAMP | NOT NULL | `NOW()` | 레코드 최종 수정 시각 |

**config_json 형식 예시:**

```json
// Google STT
{
  "credentialsFilePath": "/APP/google-credentials.json",
  "projectId": "my-gcp-project"
}

// Naver CLOVA Speech
{
  "clientId": "your_client_id",
  "clientSecret": "your_client_secret",
  "apiUrl": "https://naveropenapi.apigw.ntruss.com/recog/v1/stt"
}

// ETRI STT (한국전자통신연구원)
{
  "apiKey": "your_etri_api_key",
  "apiUrl": "http://aiopen.etri.re.kr:8000/WiseASR/Recognition"
}
```

---

### 2.4 audio_files 테이블

> **역할**: 저장된 오디오 파일의 메타데이터를 관리합니다. 실제 파일은 파일시스템에 저장됩니다.

| 컬럼명 | 데이터 타입 | NULL | 기본값 | 설명 |
|--------|-----------|------|--------|------|
| `id` | BIGSERIAL | NOT NULL | 자동증가 | PK |
| `call_id` | VARCHAR(200) | NOT NULL | - | FK → calls.call_id |
| `channel` | VARCHAR(10) | NOT NULL | - | 채널: RX, TX |
| `file_path` | VARCHAR(500) | NOT NULL | - | 서버 절대 경로 |
| `file_name` | VARCHAR(200) | NOT NULL | - | 파일명 (예: `a84b4c76_rx.wav`) |
| `file_size` | BIGINT | NULL | - | 파일 크기 (bytes) |
| `duration_ms` | BIGINT | NULL | - | 오디오 길이 (milliseconds) |
| `format` | VARCHAR(10) | NOT NULL | `'WAV'` | 포맷: WAV, PCM |
| `sample_rate` | INTEGER | NOT NULL | `8000` | 샘플링 레이트 (Hz) |
| `is_deleted` | BOOLEAN | NOT NULL | `false` | 소프트 삭제 여부 |
| `deleted_at` | TIMESTAMP | NULL | - | 삭제 처리 시각 |
| `created_at` | TIMESTAMP | NOT NULL | `NOW()` | 레코드 생성 시각 |

---

## 3. 전체 DDL SQL

```sql
-- ═══════════════════════════════════════════════════════
-- STT-Gateway Database Schema
-- Database: PostgreSQL
-- Encoding: UTF-8
-- ═══════════════════════════════════════════════════════

-- ───────────────────────────────────────────────────────
-- 1. calls 테이블
-- ───────────────────────────────────────────────────────
CREATE TABLE calls (
    id               BIGSERIAL       PRIMARY KEY,
    call_id          VARCHAR(200)    NOT NULL UNIQUE,
    caller_number    VARCHAR(50),
    callee_number    VARCHAR(50),
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    duration_seconds INTEGER,
    status           VARCHAR(20)     NOT NULL DEFAULT 'RINGING'
                         CHECK (status IN ('RINGING', 'ACTIVE', 'COMPLETED', 'ERROR')),
    stt_provider     VARCHAR(50),
    stt_mode         VARCHAR(20)
                         CHECK (stt_mode IS NULL OR stt_mode IN ('BATCH', 'STREAMING', 'BOTH')),
    rx_file_path     VARCHAR(500),
    tx_file_path     VARCHAR(500),
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  calls                IS '통화 이력 테이블';
COMMENT ON COLUMN calls.call_id        IS 'SIP Call-ID (통화 고유 식별자)';
COMMENT ON COLUMN calls.caller_number  IS '발신자 번호 (SIP From, 고객 번호)';
COMMENT ON COLUMN calls.callee_number  IS '수신자 번호 (SIP To, 상담사 번호)';
COMMENT ON COLUMN calls.status         IS '통화 상태: RINGING/ACTIVE/COMPLETED/ERROR';
COMMENT ON COLUMN calls.stt_provider   IS '사용된 STT 공급자 이름';


-- ───────────────────────────────────────────────────────
-- 2. stt_results 테이블
-- ───────────────────────────────────────────────────────
CREATE TABLE stt_results (
    id               BIGSERIAL       PRIMARY KEY,
    call_id          VARCHAR(200)    NOT NULL,
    channel          VARCHAR(10)     NOT NULL
                         CHECK (channel IN ('RX', 'TX')),
    transcript       TEXT,
    confidence       FLOAT           CHECK (confidence IS NULL
                                        OR (confidence >= 0 AND confidence <= 1)),
    start_offset_ms  BIGINT,
    end_offset_ms    BIGINT,
    stt_mode         VARCHAR(20)     NOT NULL
                         CHECK (stt_mode IN ('BATCH', 'STREAMING')),
    is_final         BOOLEAN         NOT NULL DEFAULT FALSE,
    error_message    VARCHAR(500),
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_stt_results_call_id
        FOREIGN KEY (call_id) REFERENCES calls(call_id)
        ON DELETE CASCADE
);

COMMENT ON TABLE  stt_results              IS 'STT 변환 결과 저장 테이블';
COMMENT ON COLUMN stt_results.channel      IS '음성 채널: RX=고객, TX=상담사';
COMMENT ON COLUMN stt_results.confidence   IS 'STT 인식 신뢰도 (0.0~1.0)';
COMMENT ON COLUMN stt_results.start_offset_ms IS '통화 시작으로부터 텍스트 시작 시점(ms)';
COMMENT ON COLUMN stt_results.is_final     IS '최종 결과 여부 (false=스트리밍 중간 결과)';


-- ───────────────────────────────────────────────────────
-- 3. stt_provider_configs 테이블
-- ───────────────────────────────────────────────────────
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

COMMENT ON TABLE  stt_provider_configs                IS 'STT 공급자 설정 테이블';
COMMENT ON COLUMN stt_provider_configs.provider_name  IS '공급자 코드 (google, naver, etri 등)';
COMMENT ON COLUMN stt_provider_configs.is_active      IS '현재 활성 공급자 (1개만 true)';
COMMENT ON COLUMN stt_provider_configs.config_json    IS 'API 키 등 공급자별 설정 (JSON)';

-- is_active=true인 공급자는 항상 1개만 존재하도록 부분 유니크 인덱스로 보장
CREATE UNIQUE INDEX idx_stt_provider_configs_active_one
    ON stt_provider_configs (is_active)
    WHERE is_active = TRUE;


-- ───────────────────────────────────────────────────────
-- 4. audio_files 테이블
-- ───────────────────────────────────────────────────────
CREATE TABLE audio_files (
    id          BIGSERIAL       PRIMARY KEY,
    call_id     VARCHAR(200)    NOT NULL,
    channel     VARCHAR(10)     NOT NULL
                    CHECK (channel IN ('RX', 'TX')),
    file_path   VARCHAR(500)    NOT NULL,
    file_name   VARCHAR(200)    NOT NULL,
    file_size   BIGINT,
    duration_ms BIGINT,
    format      VARCHAR(10)     NOT NULL DEFAULT 'WAV'
                    CHECK (format IN ('WAV', 'PCM')),
    sample_rate INTEGER         NOT NULL DEFAULT 8000,
    is_deleted  BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_audio_files_call_id
        FOREIGN KEY (call_id) REFERENCES calls(call_id)
        ON DELETE CASCADE,

    CONSTRAINT uq_audio_files_call_channel
        UNIQUE (call_id, channel)    -- 한 콜에 RX 1개, TX 1개만
);

COMMENT ON TABLE  audio_files          IS '통화 오디오 파일 메타데이터';
COMMENT ON COLUMN audio_files.channel  IS '음성 채널: RX=고객, TX=상담사';
COMMENT ON COLUMN audio_files.file_path IS '서버 파일 절대 경로';


-- ───────────────────────────────────────────────────────
-- 5. updated_at 자동 갱신 트리거
-- ───────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_calls_updated_at
    BEFORE UPDATE ON calls
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_stt_provider_configs_updated_at
    BEFORE UPDATE ON stt_provider_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

---

## 4. 인덱스 설계

```sql
-- ───────────────────────────────────────────────────────
-- calls 테이블 인덱스
-- ───────────────────────────────────────────────────────

-- call_id는 UNIQUE로 이미 인덱스 생성됨

-- 날짜 범위 조회용 (대시보드, 통계)
CREATE INDEX idx_calls_start_time
    ON calls (start_time DESC);

-- 상태별 조회용 (활성 콜 목록) - 부분 인덱스로 크기 최소화
CREATE INDEX idx_calls_status_active
    ON calls (status, start_time)
    WHERE status IN ('RINGING', 'ACTIVE');

-- 발신번호로 콜 이력 조회용
CREATE INDEX idx_calls_caller_number
    ON calls (caller_number, start_time DESC);


-- ───────────────────────────────────────────────────────
-- stt_results 테이블 인덱스
-- ───────────────────────────────────────────────────────

-- call_id 기반 조회 (가장 빈번한 쿼리)
CREATE INDEX idx_stt_results_call_id
    ON stt_results (call_id, channel, created_at);

-- 최종 결과만 조회 최적화
CREATE INDEX idx_stt_results_final
    ON stt_results (call_id, is_final)
    WHERE is_final = TRUE;


-- ───────────────────────────────────────────────────────
-- audio_files 테이블 인덱스
-- ───────────────────────────────────────────────────────

CREATE INDEX idx_audio_files_call_id
    ON audio_files (call_id);

-- 보관 기간 초과 파일 조회 (정리 스케줄러용)
CREATE INDEX idx_audio_files_cleanup
    ON audio_files (created_at, is_deleted)
    WHERE is_deleted = FALSE;
```

### 4.1 인덱스 설계 이유

| 인덱스 | 이유 |
|--------|------|
| `calls.start_time` | 대시보드에서 날짜 기반 조회 빈번. DESC로 최신 콜 빠르게 접근 |
| `calls.status` (부분) | 활성 콜만 조회 빈번. 부분 인덱스로 COMPLETED 제외하여 크기 최소화 |
| `stt_results.call_id` | 콜 상세 화면에서 해당 콜의 STT 결과 조회 시 사용 |
| `stt_provider_configs` (부분 유니크) | is_active=TRUE인 레코드가 2개 이상이면 안 됨. DB 레벨 무결성 보장 |

---

## 5. 초기 데이터 (Seed Data)

```sql
-- ───────────────────────────────────────────────────────
-- STT 공급자 기본 설정 삽입
-- 애플리케이션 최초 실행 시 필요한 초기 데이터
-- ───────────────────────────────────────────────────────

INSERT INTO stt_provider_configs
    (provider_name, display_name, is_active, language_code, config_json,
     supports_streaming, supports_batch, description)
VALUES
    -- Google STT (기본 활성)
    ('google',
     'Google Speech-to-Text',
     TRUE,
     'ko-KR',
     '{"credentialsFilePath": "/APP/google-credentials.json", "projectId": ""}',
     TRUE,   -- 스트리밍 지원
     TRUE,
     'Google Cloud Speech-to-Text API. 배치/스트리밍 모두 지원. ko-KR 한국어 모델 최적화'),

    -- Naver CLOVA Speech (비활성)
    ('naver',
     'Naver CLOVA Speech',
     FALSE,
     'ko-KR',
     '{"clientId": "", "clientSecret": "", "apiUrl": "https://naveropenapi.apigw.ntruss.com/recog/v1/stt"}',
     FALSE,  -- 스트리밍 미지원 (배치만)
     TRUE,
     'Naver CLOVA Speech Recognition API. 한국어 특화. 배치 방식만 지원'),

    -- ETRI STT (비활성)
    ('etri',
     'ETRI 한국어 STT',
     FALSE,
     'ko-KR',
     '{"apiKey": "", "apiUrl": "http://aiopen.etri.re.kr:8000/WiseASR/Recognition"}',
     FALSE,
     TRUE,
     '한국전자통신연구원(ETRI) 한국어 음성인식 API. 금융/공공 분야 특화');
```

---

## 6. JPA Entity 매핑

### 6.1 Call Entity

```java
// domain/Call.java
@Entity
@Table(name = "calls",
    indexes = {
        @Index(name = "idx_calls_start_time", columnList = "start_time"),
        @Index(name = "idx_calls_caller_number", columnList = "caller_number, start_time")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, unique = true, length = 200)
    private String callId;

    @Column(name = "caller_number", length = 50)
    private String callerNumber;

    @Column(name = "callee_number", length = 50)
    private String calleeNumber;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CallStatus status = CallStatus.RINGING;

    @Column(name = "stt_provider", length = 50)
    private String sttProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "stt_mode", length = 20)
    private SttMode sttMode;

    @Column(name = "rx_file_path", length = 500)
    private String rxFilePath;

    @Column(name = "tx_file_path", length = 500)
    private String txFilePath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 비즈니스 메서드
    public void markActive() {
        this.status = CallStatus.ACTIVE;
    }

    public void markCompleted(LocalDateTime endTime) {
        this.status = CallStatus.COMPLETED;
        this.endTime = endTime;
        if (this.startTime != null) {
            this.durationSeconds = (int) Duration.between(this.startTime, endTime).getSeconds();
        }
    }

    public void markError() {
        this.status = CallStatus.ERROR;
    }

    public void setAudioPaths(String rxPath, String txPath) {
        this.rxFilePath = rxPath;
        this.txFilePath = txPath;
    }
}
```

### 6.2 SttResultEntity

```java
// domain/SttResultEntity.java
@Entity
@Table(name = "stt_results",
    indexes = {
        @Index(name = "idx_stt_results_call_id", columnList = "call_id, channel, created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SttResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, length = 200)
    private String callId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AudioChannel channel;       // RX, TX

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column
    private Float confidence;

    @Column(name = "start_offset_ms")
    private Long startOffsetMs;

    @Column(name = "end_offset_ms")
    private Long endOffsetMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "stt_mode", nullable = false, length = 20)
    private SttMode sttMode;

    @Column(name = "is_final", nullable = false)
    @Builder.Default
    private Boolean isFinal = false;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

### 6.3 SttProviderConfig Entity

```java
// domain/SttProviderConfig.java
@Entity
@Table(name = "stt_provider_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SttProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_name", nullable = false, unique = true, length = 50)
    private String providerName;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "language_code", nullable = false, length = 20)
    @Builder.Default
    private String languageCode = "ko-KR";

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "supports_streaming", nullable = false)
    @Builder.Default
    private Boolean supportsStreaming = false;

    @Column(name = "supports_batch", nullable = false)
    @Builder.Default
    private Boolean supportsBatch = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 비즈니스 메서드
    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
    public void updateConfig(String newConfigJson) { this.configJson = newConfigJson; }
}
```

### 6.4 열거형 (Enum) 정의

```java
// domain/CallStatus.java
public enum CallStatus {
    RINGING,    // 연결 중
    ACTIVE,     // 통화 중
    COMPLETED,  // 정상 종료
    ERROR       // 오류
}

// domain/SttMode.java
public enum SttMode {
    BATCH,      // 배치 (통화 후)
    STREAMING,  // 스트리밍 (실시간)
    BOTH        // 양쪽 모두 처리
}

// audio/AudioChannel.java
public enum AudioChannel {
    RX,         // 수신 (고객 음성)
    TX          // 송신 (상담사 음성)
}
```

---

## 7. 자주 사용하는 쿼리

```sql
-- 현재 활성 콜 목록 조회
SELECT call_id, caller_number, callee_number, start_time, status
FROM calls
WHERE status IN ('RINGING', 'ACTIVE')
ORDER BY start_time DESC;

-- 특정 콜의 STT 전체 텍스트 (시간 순)
SELECT channel, transcript, start_offset_ms, is_final
FROM stt_results
WHERE call_id = 'a84b4c76e66710@10.0.0.1'
  AND is_final = TRUE
ORDER BY start_offset_ms;

-- 현재 활성 STT 공급자 조회
SELECT provider_name, display_name, config_json, supports_streaming
FROM stt_provider_configs
WHERE is_active = TRUE
LIMIT 1;

-- 오늘 날짜 통화 통계
SELECT
    COUNT(*) AS total_calls,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
    COUNT(*) FILTER (WHERE status = 'ERROR') AS errors,
    ROUND(AVG(duration_seconds)) AS avg_duration_sec
FROM calls
WHERE start_time >= CURRENT_DATE
  AND start_time < CURRENT_DATE + INTERVAL '1 day';

-- 보관 기간 초과 오디오 파일 조회 (30일 이상)
SELECT id, call_id, file_path
FROM audio_files
WHERE is_deleted = FALSE
  AND created_at < NOW() - INTERVAL '30 days';

-- 테이블별 용량 확인
SELECT schemaname, tablename,
       pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```
