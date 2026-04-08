# STT-Gateway 코딩 컨벤션 (CONVENTIONS.md)

> 이 문서는 STT-Gateway 프로젝트에 참여하는 모든 개발자가 따라야 하는 코딩 규칙입니다.
> 일관된 코드 스타일은 코드 리뷰를 빠르게 하고, 유지보수를 쉽게 만들어 줍니다.

---

## 목차

1. [프로젝트 패키지 명명 규칙](#1-프로젝트-패키지-명명-규칙)
2. [Java 코딩 스타일](#2-java-코딩-스타일)
3. [Spring Boot 레이어별 규칙](#3-spring-boot-레이어별-규칙)
4. [API 설계 규칙](#4-api-설계-규칙)
5. [예외 처리 규칙](#5-예외-처리-규칙)
6. [로깅 규칙](#6-로깅-규칙)
7. [데이터베이스 규칙](#7-데이터베이스-규칙)
8. [테스트 작성 규칙](#8-테스트-작성-규칙)
9. [Git 커밋 규칙](#9-git-커밋-규칙)
10. [설정 파일 규칙](#10-설정-파일-규칙)
11. [STT 공급자 확장 규칙](#11-stt-공급자-확장-규칙)

---

## 1. 프로젝트 패키지 명명 규칙

### 1.1 기본 패키지

```
com.ktis.stt_gateway        ← 항상 이 패키지를 사용 (언더스코어)
```

> **절대 사용 금지**: `com.aicc.sttgw` 패키지는 레거시(구버전)입니다. 새 클래스는 절대 이 패키지에 만들지 않습니다.

### 1.2 서브 패키지 구조

| 패키지 | 용도 | 예시 클래스 |
|--------|------|-------------|
| `config` | Spring 설정 클래스 | `AsyncConfig`, `WebSocketConfig` |
| `capture` | 패킷 캡처 관련 | `PacketCaptureService` |
| `sip` | SIP 프로토콜 처리 | `SipParser`, `SipMessage` |
| `rtp` | RTP 오디오 처리 | `RtpDecoder`, `G711Codec` |
| `session` | 콜 세션 관리 | `CallSessionManager`, `CallSession` |
| `audio` | 오디오 파일 생성 | `AudioFileWriter`, `WavFileBuilder` |
| `stt` | STT 연동 | `SttProvider`, `SttService` |
| `stt.provider` | STT 공급자 구현 | `GoogleSttProvider` |
| `push` | WebSocket push | `WebSocketPushService` |
| `api` | REST 컨트롤러 | `CallController` |
| `web` | Thymeleaf 컨트롤러 | `DashboardController` |
| `domain` | JPA Entity | `Call`, `SttResultEntity` |
| `repository` | Spring Data Repository | `CallRepository` |
| `dto` | 데이터 전송 객체 | `CallDto`, `SttResultDto` |
| `exception` | 커스텀 예외 | `CallNotFoundException`, `GlobalExceptionHandler` |

---

## 2. Java 코딩 스타일

### 2.1 클래스 명명 규칙

```java
// ✅ 올바른 예시
public class CallSessionManager { }     // 파스칼케이스 (PascalCase)
public interface SttProvider { }        // 인터페이스도 파스칼케이스
public enum AudioChannel { RX, TX }    // 열거형도 파스칼케이스, 값은 대문자

// ❌ 잘못된 예시
public class callSessionManager { }    // 소문자 시작 금지
public class Call_Session_Manager { }  // 언더스코어 금지 (패키지명 제외)
```

### 2.2 클래스 접미사 규칙

| 접미사 | 용도 |
|--------|------|
| `Service` | 비즈니스 로직 처리 |
| `Manager` | 상태(State) 관리 (싱글톤, Map 보관) |
| `Controller` | Spring MVC 컨트롤러 (REST API 또는 View) |
| `Repository` | Spring Data JPA 인터페이스 |
| `Config` | Spring 설정 클래스 (`@Configuration`) |
| `Provider` | 전략 패턴 구현체 (STT 공급자 등) |
| `Factory` | 객체 생성 담당 |
| `Decoder` / `Parser` | 데이터 파싱/변환 전용 |
| `Writer` / `Reader` | 파일 I/O 전용 |
| `Dto` | Data Transfer Object (API 요청/응답) |
| `Listener` | 이벤트 리스너 인터페이스/구현체 |

### 2.3 메서드 명명 규칙

```java
// 동작을 나타내는 동사로 시작
public void startCapture() { }          // 시작
public void stopCapture() { }           // 중지
public CallSession findByCallId() { }   // 조회 (단건, null 가능)
public Optional<CallSession> findOptionalByCallId() { }  // 조회 (Optional)
public List<Call> findActiveCalls() { } // 목록 조회
public boolean isActive() { }           // boolean 반환 → is/has 접두사
public boolean hasActiveStream() { }
public void onCallStart() { }           // 이벤트 핸들러 → on 접두사
public void onCallEnd() { }

// ❌ 피해야 할 네이밍
public CallSession get() { }            // 너무 모호함
public void process() { }              // 무엇을 처리하는지 불명확
public void doSomething() { }          // do 접두사 금지
```

### 2.4 변수 명명 규칙

```java
// ✅ 올바른 예시
private final CallSessionManager sessionManager;   // camelCase
private String callId;
private LocalDateTime startTime;
private final ConcurrentHashMap<String, CallSession> activeSessions;

// 상수는 대문자 + 언더스코어
private static final int MAX_STREAM_DURATION_SECONDS = 270;
private static final String DEFAULT_LANGUAGE_CODE = "ko-KR";

// ❌ 잘못된 예시
private String s;                   // 단일 문자 변수 금지 (반복문 인덱스 제외)
private String call_id;             // 언더스코어 금지 (상수 제외)
private String CallId;              // 대문자 시작 금지 (클래스명과 혼동)
```

### 2.5 Lombok 사용 규칙

```java
// ✅ 권장 어노테이션 조합

// Entity 클래스
@Entity
@Table(name = "calls")
@Getter                    // getter만 생성 (setter 자제)
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 필수
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Call { }

// DTO 클래스
@Data                      // getter + setter + equals + hashCode + toString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallDto { }

// Service/Manager 클래스
@Service
@Slf4j                     // log 변수 자동 생성
@RequiredArgsConstructor   // final 필드 생성자 주입
public class CallSessionManager {
    private final CallRepository callRepository;  // final → 생성자 주입
}

// ❌ 금지 패턴
@Data  // Entity에 @Data 사용 금지 (equals/hashCode 문제, setter 노출)
```

### 2.6 Optional 사용 규칙

```java
// ✅ Optional 올바른 사용
public Optional<CallSession> findSession(String callId) {
    return Optional.ofNullable(activeSessions.get(callId));
}

// 사용 시
sessionManager.findSession(callId)
    .ifPresent(session -> {
        // 세션이 있을 때만 처리
    });

// 또는
CallSession session = sessionManager.findSession(callId)
    .orElseThrow(() -> new CallNotFoundException(callId));

// ❌ 금지 패턴
Optional<CallSession> opt = sessionManager.findSession(callId);
CallSession s = opt.get();    // 검사 없이 get() 절대 금지 (NPE 위험)
```

---

## 3. Spring Boot 레이어별 규칙

### 3.1 Controller 규칙

```java
// REST API 컨트롤러
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Slf4j
public class CallController {

    private final CallService callService;   // Service만 의존 (Repository 직접 접근 금지)

    @GetMapping("/{callId}")
    public ResponseEntity<CallDto> getCall(@PathVariable String callId) {
        // 비즈니스 로직은 Service에서 처리
        // Controller는 요청/응답 변환만 담당
        return ResponseEntity.ok(callService.findCall(callId));
    }

    @PostMapping
    public ResponseEntity<CallDto> createCall(@Valid @RequestBody CallDto request) {
        // @Valid: 입력 검증 필수
        CallDto created = callService.createCall(request);
        URI location = URI.create("/api/v1/calls/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }
}

// Thymeleaf 뷰 컨트롤러
@Controller                    // @RestController 아닌 @Controller 사용
@RequestMapping("/stt-config")
public class SttConfigWebController {

    @GetMapping
    public String showConfig(Model model) {
        model.addAttribute("providers", sttConfigService.getAllProviders());
        return "stt-config";   // templates/stt-config.html 반환
    }
}
```

### 3.2 Service 규칙

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)   // 기본은 읽기 전용 트랜잭션
public class CallService {

    private final CallRepository callRepository;

    // 조회 메서드: 기본 readOnly = true 적용됨
    public CallDto findCall(String callId) {
        return callRepository.findByCallId(callId)
            .map(CallDto::fromEntity)
            .orElseThrow(() -> new CallNotFoundException(callId));
    }

    // 데이터 변경 메서드: 명시적으로 @Transactional 추가
    @Transactional
    public void updateCallStatus(String callId, CallStatus status) {
        Call call = callRepository.findByCallId(callId)
            .orElseThrow(() -> new CallNotFoundException(callId));
        call.updateStatus(status);  // Entity의 비즈니스 메서드 호출
        // @Transactional 내에서 변경감지(dirty checking)로 자동 저장됨
    }
}
```

### 3.3 Repository 규칙

```java
// Spring Data JPA 인터페이스
public interface CallRepository extends JpaRepository<Call, Long> {

    // 메서드 이름으로 쿼리 자동 생성 (간단한 경우)
    Optional<Call> findByCallId(String callId);
    List<Call> findByStatusOrderByStartTimeDesc(CallStatus status);

    // 복잡한 쿼리는 @Query 사용
    @Query("SELECT c FROM Call c WHERE c.startTime >= :from AND c.startTime < :to " +
           "ORDER BY c.startTime DESC")
    Page<Call> findCallsByDateRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );

    // 통계 쿼리 (DTO Projection)
    @Query("SELECT new com.ktis.stt_gateway.dto.CallStatsDto(c.status, COUNT(c)) " +
           "FROM Call c GROUP BY c.status")
    List<CallStatsDto> countByStatus();
}
```

### 3.4 Entity 규칙

```java
@Entity
@Table(name = "calls",
    indexes = {
        @Index(name = "idx_calls_call_id", columnList = "call_id"),
        @Index(name = "idx_calls_start_time", columnList = "start_time")
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

    @Enumerated(EnumType.STRING)              // DB에 문자열로 저장
    @Column(nullable = false, length = 20)
    private CallStatus status;

    @CreationTimestamp                        // INSERT 시 자동 설정
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp                          // UPDATE 시 자동 설정
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Setter 대신 비즈니스 메서드 제공
    public void updateStatus(CallStatus newStatus) {
        this.status = newStatus;
    }

    public void markCompleted(LocalDateTime endTime) {
        this.status = CallStatus.COMPLETED;
        this.endTime = endTime;
    }
}
```

---

## 4. API 설계 규칙

### 4.1 URL 규칙

```
기본 패턴: /api/v1/{리소스}
           항상 복수형, 소문자, 하이픈(-) 사용

✅ 올바른 URL
GET    /api/v1/calls
GET    /api/v1/calls/{callId}
GET    /api/v1/calls/{callId}/stt-results
PUT    /api/v1/stt/providers/{name}/activate

❌ 잘못된 URL
GET    /api/v1/getCall              // 동사 사용 금지
GET    /api/v1/Call                 // 대문자 금지
GET    /api/v1/call_list            // 언더스코어 금지
```

### 4.2 응답 형식 규칙

```json
// 성공 응답 (단건)
{
  "callId": "a84b4c76e66710",
  "callerNumber": "07041405642",
  "status": "ACTIVE"
}

// 성공 응답 (목록)
{
  "content": [ {}, {} ],
  "totalElements": 100,
  "totalPages": 10,
  "pageNumber": 0,
  "pageSize": 10
}

// 에러 응답 (공통 형식)
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "콜을 찾을 수 없습니다: a84b4c76e66710",
  "path": "/api/v1/calls/a84b4c76e66710"
}
```

### 4.3 HTTP 상태 코드 규칙

| 상황 | 상태 코드 |
|------|-----------|
| 정상 조회 | 200 OK |
| 생성 성공 | 201 Created (Location 헤더 포함) |
| 수정 성공 (응답 본문 없음) | 204 No Content |
| 입력값 오류 | 400 Bad Request |
| 인증 필요 | 401 Unauthorized |
| 권한 없음 | 403 Forbidden |
| 리소스 없음 | 404 Not Found |
| 서버 오류 | 500 Internal Server Error |

---

## 5. 예외 처리 규칙

### 5.1 커스텀 예외 클래스

```java
// exception/ 패키지에 위치

// 기본 비즈니스 예외 (모든 커스텀 예외의 부모)
public class SttGatewayException extends RuntimeException {
    public SttGatewayException(String message) { super(message); }
    public SttGatewayException(String message, Throwable cause) { super(message, cause); }
}

// 리소스 없음 예외
public class CallNotFoundException extends SttGatewayException {
    public CallNotFoundException(String callId) {
        super("콜을 찾을 수 없습니다: " + callId);
    }
}

// STT 처리 예외
public class SttProcessingException extends SttGatewayException {
    public SttProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 패킷 캡처 예외
public class PacketCaptureException extends SttGatewayException {
    public PacketCaptureException(String message) { super(message); }
}
```

### 5.2 전역 예외 처리기

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 리소스 없음 → 404
    @ExceptionHandler(CallNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CallNotFoundException ex,
                                                         HttpServletRequest request) {
        log.warn("리소스 없음: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage(),
                request.getRequestURI()));
    }

    // 입력 검증 오류 → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message,
                request.getRequestURI()));
    }

    // STT 처리 오류 → 500
    @ExceptionHandler(SttProcessingException.class)
    public ResponseEntity<ErrorResponse> handleSttError(SttProcessingException ex,
                                                         HttpServletRequest request) {
        log.error("STT 처리 오류: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                "STT 처리 중 오류가 발생했습니다", request.getRequestURI()));
    }

    // 예상치 못한 오류 → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex,
                                                        HttpServletRequest request) {
        log.error("예상치 못한 오류: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다", request.getRequestURI()));
    }
}
```

### 5.3 예외 처리 원칙

```
1. 패킷 캡처/파싱 오류: catch하고 log 후 다음 패킷 계속 처리
   → 한 패킷 오류로 전체 캡처가 중단되면 안 됨

2. STT 변환 오류: 재시도 후 실패 시 DB에 오류 상태 저장, 계속 진행
   → 한 콜의 STT 실패가 다른 콜에 영향 주면 안 됨

3. DB 오류: 로그 남기고 예외 전파 (데이터 무결성 문제는 무시 금지)

4. 오디오 파일 저장 오류: 로그 남기고 오류 상태 기록
   → 파일 저장 실패가 통화 품질에 영향 주면 안 됨
```

---

## 6. 로깅 규칙

### 6.1 로그 레벨 사용 기준

| 레벨 | 사용 상황 | 예시 |
|------|-----------|------|
| `ERROR` | 시스템 오류, 즉시 확인 필요 | STT API 연결 실패, DB 저장 실패 |
| `WARN` | 예상된 오류, 재시도 가능 | 알 수 없는 RTP 스트림, 5분 제한 재연결 |
| `INFO` | 주요 비즈니스 이벤트 | 콜 시작/종료, STT 완료, 캡처 시작/중지 |
| `DEBUG` | 상세 동작 정보 (개발 시) | SIP 파싱 결과, RTP 패킷 수신, 버퍼 상태 |
| `TRACE` | 매우 상세한 정보 (특수한 경우만) | 개별 패킷 내용 |

### 6.2 로그 작성 규칙

```java
// ✅ 올바른 로그 작성

// INFO: 콜 시작/종료는 반드시 남김
log.info("콜 시작: callId={}, from={}, to={}", callId, fromNumber, toNumber);
log.info("콜 종료: callId={}, duration={}초, sttMode={}", callId, duration, sttMode);

// INFO: STT 완료
log.info("STT 배치 완료: callId={}, channel={}, textLength={}",
    callId, channel, transcript.length());

// WARN: 재시도 가능한 오류
log.warn("STT 스트리밍 5분 제한 도달, 재연결: callId={}, streamNo={}",
    callId, streamNo);
log.warn("알 수 없는 RTP 스트림 무시: srcPort={}", srcPort);

// ERROR: 심각한 오류 (예외 객체를 마지막 인자로 전달해야 스택트레이스 출력)
log.error("오디오 파일 저장 실패: callId={}, path={}", callId, filePath, e);

// DEBUG: 상세 처리 과정
log.debug("SIP INVITE 수신: callId={}", callId);
log.debug("RTP 패킷 수신: ssrc={}, seq={}, payloadSize={}", ssrc, seq, payloadSize);

// ❌ 금지 패턴
log.info("콜 시작: " + callId);       // 문자열 연결 금지 (성능)
System.out.println("콜 시작");        // System.out 절대 금지
log.error(e.getMessage());            // 예외 객체 누락 → 스택트레이스 없음
```

### 6.3 민감 정보 로깅 주의

```java
// ❌ 전화번호 전체 로깅 금지 (개인정보보호법)
log.info("고객 번호: {}", callerNumber);

// ✅ 마스킹 처리 후 로깅
log.info("고객 번호: {}", maskPhoneNumber(callerNumber));  // 0101****5678

private String maskPhoneNumber(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.length() < 8) return "****";
    return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(8);
}
```

---

## 7. 데이터베이스 규칙

### 7.1 테이블/컬럼 명명

```sql
-- 테이블: snake_case, 복수형
CREATE TABLE calls ( ... );
CREATE TABLE stt_results ( ... );
CREATE TABLE stt_provider_configs ( ... );
CREATE TABLE audio_files ( ... );

-- 컬럼: snake_case
call_id, caller_number, start_time, is_active, created_at, updated_at

-- PK: id (BIGSERIAL)
-- FK: {참조테이블_단수형}_id
-- 타임스탬프: created_at, updated_at (모든 테이블 필수)
```

### 7.2 인덱스 규칙

```sql
-- 인덱스 명명: idx_{테이블명}_{컬럼명}
CREATE INDEX idx_calls_start_time ON calls(start_time);
CREATE INDEX idx_stt_results_call_id ON stt_results(call_id);

-- 자주 조회되는 컬럼에 인덱스 필수:
-- calls.call_id (고유, 메인 검색키)
-- calls.start_time (날짜별 조회)
-- stt_results.call_id (콜-결과 조인)
```

### 7.3 마이그레이션 규칙

```
DDL 변경은 반드시 마이그레이션 파일로 관리:

파일 위치: src/main/resources/db/migration/
파일명: V{버전}__{설명}.sql
예시:
  V1__create_calls_table.sql
  V2__create_stt_results_table.sql
  V3__add_provider_column_to_calls.sql

규칙:
- 버전은 순차적으로 증가 (V1, V2, V3, ...)
- 설명은 영어, 언더스코어로 연결
- 기존 마이그레이션 파일 절대 수정 금지 (새 파일로 추가)
- 운영 배포 전 로컬에서 반드시 테스트
```

---

## 8. 테스트 작성 규칙

### 8.1 테스트 클래스 구조

```java
// 단위 테스트: Mockito 사용, 빠른 실행
@ExtendWith(MockitoExtension.class)
class SipParserTest {

    @InjectMocks
    private SipParser sipParser;

    @Mock
    private CallSessionManager sessionManager;

    @Test
    @DisplayName("SIP INVITE 메시지에서 Call-ID와 발신번호를 올바르게 파싱한다")
    void shouldParseSipInviteCorrectly() {
        // Given
        String sipInvite = "INVITE sip:1000@10.0.0.1 SIP/2.0\r\n" +
                          "Call-ID: a84b4c76e66710@10.0.0.1\r\n" +
                          "From: <sip:07041405642@10.0.0.1>;tag=xyz\r\n";

        // When
        sipParser.parse(sipInvite.getBytes(), null, null);

        // Then
        ArgumentCaptor<SipMessage> captor = ArgumentCaptor.forClass(SipMessage.class);
        verify(sessionManager).onCallStart(captor.capture(), any());
        assertThat(captor.getValue().getCallId()).isEqualTo("a84b4c76e66710@10.0.0.1");
        assertThat(captor.getValue().getFromNumber()).isEqualTo("07041405642");
    }
}

// 통합 테스트: @SpringBootTest, TestContainers 사용
@SpringBootTest
@Testcontainers
class CallServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("sttgw_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CallService callService;

    @Test
    @DisplayName("콜 저장 후 callId로 조회할 수 있다")
    void shouldFindCallByCallId() {
        // 실제 PostgreSQL에서 테스트
    }
}
```

### 8.2 테스트 네이밍

```java
// @DisplayName: 한국어로 작성, "~한다" 형식으로 끝냄
@DisplayName("SIP INVITE에서 발신번호를 파싱한다")
@DisplayName("5분 초과 시 STT 스트림을 재연결한다")
@DisplayName("오디오 파일이 없으면 404를 반환한다")

// 메서드명: should{결과}When{조건} 또는 should{행동}
void shouldParseSipInviteCorrectly() { }
void shouldThrow404WhenCallNotFound() { }
void shouldRestartStreamWhenDurationExceeds5Minutes() { }
```

### 8.3 Given-When-Then 패턴 필수

```java
@Test
void shouldCreateCallSession() {
    // Given (사전 조건 설정)
    SipMessage inviteMessage = SipMessage.builder()
        .callId("test-call-id")
        .fromNumber("07041405642")
        .method(SipMethod.INVITE)
        .build();

    // When (실행)
    sessionManager.onCallStart(inviteMessage, Timestamp.from(Instant.now()));

    // Then (검증)
    assertThat(sessionManager.findSession("test-call-id")).isPresent();
    verify(callRepository).save(any(Call.class));
}
```

---

## 9. Git 커밋 규칙

### 9.1 커밋 타입

| 타입 | 용도 |
|------|------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 코드 리팩토링 (기능 변경 없음) |
| `test` | 테스트 코드 추가/수정 |
| `docs` | 문서 수정 (`.md` 파일 등) |
| `chore` | 빌드 설정, 의존성 변경 |
| `perf` | 성능 개선 |

### 9.2 커밋 메시지 예시

```bash
# ✅ 올바른 예시
feat: Google STT 스트리밍 연동 구현
fix: 5분 초과 시 STT 스트림 재연결 오류 수정
feat: STT 공급자 교체 API 추가
test: SipParser 단위 테스트 추가
refactor: CallSessionManager에서 파일 쓰기 로직을 AudioFileWriter로 분리

# ❌ 잘못된 예시
Update code            # 너무 모호함
수정함                  # 무엇을 수정했는지 불명확
WIP                    # Work In Progress 커밋 금지
```

---

## 10. 설정 파일 규칙

### 10.1 YAML 작성 규칙

```yaml
# ✅ 올바른 YAML
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sttgw
    username: ${DB_USERNAME:sttgw_user}     # 환경변수 우선, 기본값 지정

stt:
  gateway:
    capture:
      interface: eth3
      dump-path: /APP_DATA/DUMP

# ❌ 잘못된 YAML
spring:
  datasource:
    password: mypassword123                  # 비밀번호 평문 저장 금지

stt.gateway.capture.interface: eth3         # 점(.) 표기법 금지 (YAML 계층 사용)
```

### 10.2 환경별 설정 분리

```
application.yaml          → 공통 설정 (민감 정보 없음)
application-dev.yaml      → 개발 환경 (H2, 로컬 경로, DEBUG 로그)
application-prod.yaml     → 운영 환경 (PostgreSQL, 운영 경로, INFO 로그)
/APP/application.yaml     → 운영 서버 실제 설정 (Git 제외, 비밀번호 포함)

민감 정보(비밀번호, API 키):
- 환경변수로 주입: ${DB_PASSWORD}
- 운영 서버 /APP/application.yaml 에서 관리
- 절대 Git에 커밋하지 않음
```

---

## 11. STT 공급자 확장 규칙

새 STT 공급자를 추가할 때 반드시 지켜야 할 규칙입니다.

### 11.1 인터페이스 구현 필수 체크리스트

```java
@Component("naverSttProvider")              // Bean 이름: {공급자명}SttProvider
@Slf4j
public class NaverClovaSttProvider implements SttProvider {

    // 1. getProviderName()은 DB의 provider_name 컬럼값과 반드시 일치
    @Override
    public String getProviderName() { return "naver"; }

    // 2. 배치 방식 반드시 구현
    @Override
    public SttResult transcribeBatch(SttRequest request) { /* ... */ return null; }

    // 3. 스트리밍 미지원 시 명시적 예외 발생
    @Override
    public void transcribeStreaming(SttRequest request, Consumer<SttResult> consumer) {
        throw new UnsupportedOperationException(
            getProviderName() + " 공급자는 스트리밍 방식을 지원하지 않습니다");
    }

    // 4. 공급자 가용성 체크 (API 키 유효성 등)
    @Override
    public boolean isAvailable() { return /* API 키 존재 여부 확인 */ true; }
}
```

### 11.2 DB 등록 필수

```sql
-- 새 공급자 추가 시 DB에 기본 레코드 반드시 삽입
INSERT INTO stt_provider_configs
    (provider_name, display_name, is_active, language_code,
     supports_streaming, supports_batch, config_json)
VALUES
    ('naver', 'Naver CLOVA Speech', false, 'ko-KR',
     false, true,
     '{"clientId": "", "clientSecret": ""}');
```

---

*이 컨벤션 문서는 프로젝트 진행 중 팀 합의를 통해 업데이트될 수 있습니다.*
