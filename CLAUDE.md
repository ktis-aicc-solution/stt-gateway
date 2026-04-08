# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 프로젝트 개요

**STT-Gateway** — 컨택센터(IPCC) 음성 통화를 Google STT(또는 다른 STT 솔루션)로 변환하는 Spring Boot 미들웨어.

- eth3 미러링 포트에서 SIP/RTP 패킷을 캡처 → 오디오 추출 → STT 변환 → WebSocket으로 실시간 텍스트 push
- 배포 서버: `211.192.89.96` | 배포 경로: `/APP/stt-gateway.jar`
- 상세 설계: `docs/ARCHITECTURE.md` | DB 스키마: `docs/SCHEMA.md`

---

## 빌드 및 실행 명령어

```bash
# 빌드 (테스트 제외)
./gradlew clean build -x test

# 빌드 (테스트 포함)
./gradlew build

# 로컬 실행 (개발 프로파일)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 특정 테스트 클래스 실행
./gradlew test --tests "com.ktis.stt_gateway.sip.SipParserTest"

# 특정 테스트 메서드 실행
./gradlew test --tests "com.ktis.stt_gateway.sip.SipParserTest.shouldParseSipInviteCorrectly"

# 클린
./gradlew clean
```

> Windows에서는 `gradlew.bat` 사용. 예: `gradlew.bat clean build -x test`

---

## 아키텍처 요약

### 핵심 데이터 흐름
```
eth3 미러링 패킷
 → [PacketCaptureService] pcap4j로 캡처
 → [PacketDispatcher] SIP(5060) / RTP(10000-20000) 분기
     ├─ SIP → [SipParser] Call-ID, From(고객번호) 추출 → [CallSessionManager] 세션 생성/종료
     └─ RTP → [RtpDecoder] G.711 디코딩, RX/TX 분리 → 세션 오디오 버퍼
 → [AudioFileWriter] /APP_DATA/audio/{날짜}/{callId}_{rx|tx}.wav 비동기 저장
 → [SttService] 배치(통화 후) or 스트리밍(실시간) STT 변환
 → [WebSocketPushService] /topic/stt/{callId} 채널로 브라우저 push
```

### 패키지 구조 (활성 패키지: `com.ktis.stt_gateway`)
> `com.aicc.sttgw`는 레거시 패키지 — 신규 코드 절대 추가 금지

| 패키지 | 역할 |
|--------|------|
| `capture` | pcap4j 패킷 캡처/파일 읽기 |
| `sip` | SIP 메시지 파싱 (INVITE/BYE) |
| `rtp` | RTP 디코딩, G.711 변환 |
| `session` | 콜 라이프사이클 관리 (`ConcurrentHashMap<callId, CallSession>`) |
| `audio` | WAV 파일 비동기 저장 |
| `stt` | STT 인터페이스 + 공급자 구현체 (`stt/provider/`) |
| `push` | STOMP WebSocket push |
| `api` | REST API 컨트롤러 (`/api/v1/`) |
| `web` | Thymeleaf 화면 컨트롤러 |
| `domain` | JPA Entity |
| `repository` | Spring Data JPA |
| `dto` | API 요청/응답 DTO |
| `config` | Spring 설정 클래스 |

### STT 공급자 확장 구조 (Strategy 패턴)
```
SttProvider (interface)
  ├── GoogleSttProvider   @Component("googleSttProvider")
  ├── NaverSttProvider    @Component("naverSttProvider")   ← 추가 예시
  └── EtriSttProvider     @Component("etriSttProvider")    ← 추가 예시

활성 공급자는 DB(stt_provider_configs.is_active=true)로 관리
→ SttProviderFactory가 활성 공급자 Bean을 동적으로 반환
```

### Google STT 5분 스트리밍 제한 처리
- `AppProperties.streaming.maxDurationSeconds = 270` (4분 30초)
- SttService가 while 루프에서 스트림을 자동 재시작
- 재연결 시 `overlapSeconds` 설정으로 음절 잘림 방지

---

## 주요 기술 스택

| 항목 | 내용 |
|------|------|
| Spring Boot | 3.5.13 / Java 17 |
| 패킷 캡처 | `pcap4j-core:1.8.2` (libpcap 필요) |
| Google STT | `google-cloud-speech:4.39.0` |
| WebSocket | Spring WebSocket + STOMP |
| DB | PostgreSQL (운영) / H2 (테스트) |
| Circuit Breaker | Resilience4j |
| 프론트엔드 | Thymeleaf + SockJS |
| 비동기 | `@Async` + 스레드풀 (capture / fileWrite / stt / callProcessing) |

---

## 서버 디렉토리 구조 (배포 서버)

```
/APP/                    애플리케이션 홈
/APP_LOGS/               로그 파일
/APP_DATA/DUMP/          패킷 덤프 (.pcap, 3일 보관)
/APP_DATA/audio/         오디오 파일 (WAV, 30일 보관)
```

오디오 파일명: `/APP_DATA/audio/{YYYYMMDD}/{callId}_rx.wav`, `{callId}_tx.wav`

---

## 데이터베이스 핵심 테이블

| 테이블 | 역할 |
|--------|------|
| `calls` | 통화 이력 (call_id = SIP Call-ID) |
| `stt_results` | STT 변환 텍스트 (channel: RX/TX) |
| `stt_provider_configs` | STT 공급자 설정 (is_active=true 1개만) |
| `audio_files` | 오디오 파일 메타데이터 |

---

## 코딩 규칙 핵심 요약

- **예외 처리**: 패킷 파싱 오류는 catch + log 후 계속 진행 (캡처 중단 금지)
- **Entity**: `@Data` 금지, setter 대신 비즈니스 메서드 제공
- **DI**: 항상 생성자 주입 (`@RequiredArgsConstructor` + `final`)
- **트랜잭션**: Service 기본 `@Transactional(readOnly=true)`, 변경 메서드에 `@Transactional` 추가
- **Controller**: Repository 직접 접근 금지, Service만 호출
- **로그**: `System.out` 금지, `@Slf4j` + 파라미터화 로그 (`log.info("콜: {}", callId)`)
- **민감정보**: 전화번호 마스킹 처리 후 로깅

상세 컨벤션: `docs/CONVENTIONS.md`

---

## application.yaml 프로파일

| 파일 | 환경 |
|------|------|
| `application.yaml` | 공통 (민감 정보 없음) |
| `application-dev.yaml` | 개발 (H2, DEBUG 로그) |
| `application-prod.yaml` | 운영 (PostgreSQL, INFO 로그) |
| `/APP/application.yaml` | 운영 서버 실제 설정 (Git 제외, 비밀번호 포함) |
