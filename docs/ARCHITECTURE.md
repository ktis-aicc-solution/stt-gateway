# STT-Gateway 전체 설계 문서 (ARCHITECTURE.md)

> **대상 독자:** 초보 개발자도 이해할 수 있도록 핵심 개념부터 상세 구현까지 단계별로 설명합니다.

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [전체 시스템 구성도](#2-전체-시스템-구성도)
3. [핵심 개념 설명 (초보 개발자용)](#3-핵심-개념-설명-초보-개발자용)
4. [서버 디렉토리 구조](#4-서버-디렉토리-구조)
5. [스프링부트 프로젝트 설정](#5-스프링부트-프로젝트-설정)
6. [Dependencies 선택](#6-dependencies-선택)
7. [핵심 모듈 설계 - 전체 패키지 구조](#7-핵심-모듈-설계---전체-패키지-구조)
8. [애플리케이션 내 각 모듈별 구성도](#8-애플리케이션-내-각-모듈별-구성도)
9. [모듈별 상세 구현](#9-모듈별-상세-구현)
10. [성능 최적화 전략](#10-성능-최적화-전략)
11. [STT 확장성 설계](#11-stt-확장성-설계)

---

## 1. 프로젝트 개요

### 1.1 배경 및 목적

컨택센터에서 고객과 상담사가 전화 통화를 할 때, 그 음성을 자동으로 텍스트로 변환(STT: Speech-to-Text)하면 다음과 같은 비즈니스 가치를 얻을 수 있습니다.

- **실시간 상담 보조**: 통화 중 고객이 말하는 내용을 텍스트로 보여줘 상담사가 빠르게 대응
- **통화 후 분석**: 통화 내용 전체를 텍스트로 기록하여 품질 관리(QA), 키워드 분석에 활용
- **컴플라이언스**: 금융, 통신 등 규제 산업에서 통화 내용 보관 의무 충족

**STT-Gateway**는 기존 IPCC(IP Contact Center) 시스템과 Google STT(또는 다른 STT 솔루션) 사이의 **중간 다리 역할**을 하는 애플리케이션입니다.

### 1.2 프로젝트 목표

| 목표 | 설명 |
|------|------|
| 음성 데이터 수집 | IPCC 스위치에서 미러링된 네트워크 패킷 캡처 |
| SIP 세션 추적 | SIP 프로토콜을 파싱하여 콜 시작/종료 감지 |
| RTP 오디오 추출 | 실제 음성 데이터(RTP 패킷)를 PCM/WAV 파일로 변환 |
| STT 변환 | 배치(통화 후) + 스트리밍(실시간) 방식 지원 |
| 결과 저장 | 변환된 텍스트를 DB에 저장 |
| 실시간 화면 표시 | WebSocket으로 프론트엔드에 실시간 텍스트 전달 |
| STT 공급자 교체 | Google, Naver, 국산 STT 등 교체 가능한 구조 |

### 1.3 시스템 환경

| 구분 | IP 주소 | 역할 |
|------|---------|------|
| IPCC 서버 | 211.192.89.43 | PBX, IVR, CTI, 녹취, 통계 |
| 상담AP 서버 | 192.168.201.39 | 상담사 업무 화면 |
| STTGateway 서버 | 211.192.89.96 | 패킷 수신(eth3 미러링) + STT Gateway 실행 |

---

## 2. 전체 시스템 구성도

### 2.1 물리적 네트워크 구성도

```
┌─────────────────────────────────────────────────────────────────────┐
│                        외부 인터넷                                    │
│                   고객 전화: 070-4140-5642                           │
└─────────────────────────────┬───────────────────────────────────────┘
                              │ 공중전화망 (PSTN/VoIP)
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│              IPCC 서버  (211.192.89.43)                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐  │
│  │   PBX    │ │   IVR    │ │   CTI    │ │  녹취    │ │  통계   │  │
│  │ (교환기) │ │(자동안내)│ │(전화제어)│ │          │ │         │  │
│  └────┬─────┘ └──────────┘ └────┬─────┘ └──────────┘ └─────────┘  │
└───────┼─────────────────────────┼───────────────────────────────────┘
        │ SIP + RTP 패킷           │ CTI API
        │                         ▼
        │               ┌──────────────────┐
        │               │  상담AP 서버     │
        │               │ (192.168.201.39) │
        │               │  상담사 화면     │
        │               └──────────────────┘
        │
        ▼
┌───────────────────────┐
│   GS1900 스위치       │  ← 음성 패킷을 여기서 복사(미러링)
│   (네트워크 스위치)   │
└──────────┬────────────┘
           │ 포트 미러링 (eth3으로 복사 전송)
           ▼
┌─────────────────────────────────────────────────────────────────────┐
│              STTGateway 서버 (211.192.89.96)                        │
│                                                                     │
│  eth3 ──► [패킷캡처] ──► [SIP파싱] ──► [세션관리]                  │
│                    └──► [RTP디코딩] ──► [오디오파일]                │
│                                  │                                  │
│                                  ▼                                  │
│                          [STT 서비스]                               │
│                    ┌─────────┴──────────┐                          │
│                    ▼                    ▼                           │
│               [배치 STT]          [스트리밍 STT]                    │
│                    │                    │                           │
│                    └─────────┬──────────┘                          │
│                              ▼                                      │
│                   [WebSocket Push] ──► 프론트엔드                   │
│                   [REST API]       ──► 외부 시스템                  │
│                   [PostgreSQL DB]                                   │
└─────────────────────────────────────────────────────────────────────┘
           │
           ▼ HTTPS/gRPC
┌──────────────────────┐
│   Google STT API     │
│  (Cloud Speech API)  │
└──────────────────────┘
```

### 2.2 STTGateway 내부 데이터 흐름도

```
네트워크 패킷 (eth3 미러링 포트)
         │
         ▼
┌─────────────────┐
│  PacketCapture  │  pcap4j로 eth3 패킷 캡처
│     Module      │  또는 DUMP 파일 읽기
└────────┬────────┘
         │ raw packet
         ├──────────────────────────────────┐
         ▼                                  ▼
┌─────────────────┐                ┌─────────────────┐
│   SIP Parser    │                │   RTP Decoder   │
│                 │                │                 │
│ UDP/TCP 5060    │                │ UDP 10000~20000 │
│ - Call-ID 추출  │                │ - G.711 디코딩  │
│ - From(고객번호)│                │ - RX/TX 분리   │
│ - INVITE/BYE   │                │ - PCM 변환      │
└────────┬────────┘                └────────┬────────┘
         │ 콜 이벤트                          │ 오디오 데이터
         ▼                                  ▼
┌─────────────────────────────────────────────┐
│           CallSession Manager               │
│                                             │
│  Map<callId, CallSession>                   │
│  - 콜 시작/종료 관리                         │
│  - RTP 스트림과 콜 매핑                      │
│  - 오디오 버퍼 관리                          │
└──────┬──────────────────────┬───────────────┘
       │                      │
       ▼                      ▼
┌─────────────┐      ┌─────────────────┐
│ AudioFile   │      │   STT Service   │
│   Writer    │      │                 │
│             │      │ ┌─────────────┐ │
│ /APP_DATA/  │      │ │ 배치 방식   │ │  통화 종료 후
│  audio/     │      │ │ (Batch)     │ │  WAV파일 → STT
│  {id}_rx.wav│      │ └─────────────┘ │
│  {id}_tx.wav│      │ ┌─────────────┐ │
└─────────────┘      │ │스트리밍방식 │ │  실시간 PCM → STT
                     │ │(Streaming)  │ │
                     │ └─────────────┘ │
                     └───────┬─────────┘
                             │ 텍스트 결과
                             ▼
              ┌──────────────────────────┐
              │   결과 처리 & 저장        │
              │                          │
              │  PostgreSQL DB 저장       │
              │  WebSocket Push (실시간)  │
              │  REST API 응답            │
              └──────────────────────────┘
```

### 2.3 STT 스트리밍 5분 제한 해결 방안

```
Google STT Streaming 최대 5분 제한

통화 시작 (t=0)
    │
    ▼
[Stream #1 시작] ──────────────► [4분 45초] Stream #1 종료 예정 알림
                                       │
                                       ▼
                                [Stream #2 미리 준비]
                                       │
    [Stream #1 종료] ◄─────────────────┘
    [Stream #2 시작] ──────────────► [계속 진행]

    ※ maxDurationSeconds = 270 (4분 30초)로 설정하여 5분 전에 재연결
    ※ overlapSeconds = 2로 설정하여 음절이 잘리지 않도록 처리
    ※ SttService가 while 루프에서 세션이 활성화된 동안 자동 재연결
```

---

## 3. 핵심 개념 설명 (초보 개발자용)

### 3.1 SIP 프로토콜이란?

> **비유**: SIP는 전화를 **연결/끊는** 역할을 합니다. 마치 "전화 거는 행위"와 "전화 끊는 행위"만 담당합니다. 실제 목소리는 담당하지 않습니다.

```
고객이 전화를 걸면:

1. 고객 ──[SIP INVITE]──► PBX        "나 전화하고 싶어요"
2. PBX  ──[SIP 100 Trying]──► 고객   "알겠어요, 잠깐만요"
3. PBX  ──[SIP 180 Ringing]──► 고객  "상담사 전화기 울리고 있어요"
4. 상담사가 전화 받으면:
   PBX  ──[SIP 200 OK]──► 고객       "연결됐어요!"
5. 서로 통화 종료 시:
   ──[SIP BYE]──►                    "전화 끊을게요"

SIP 메시지에는 다음 정보가 포함됩니다:
- Call-ID: 이 통화의 고유 ID (예: "a84b4c76e66710@10.0.0.1")
- From:    발신자 (고객) 전화번호
- To:      수신자 (상담사) 번호
- SDP:     실제 음성을 어떤 방식으로 주고받을지 협의
```

### 3.2 RTP 프로토콜이란?

> **비유**: RTP는 실제 **목소리 데이터**를 전달합니다. 마치 전화기 안에서 목소리를 디지털 신호로 보내는 것과 같습니다.

```
SIP로 연결이 되면, 실제 음성은 RTP 패킷으로 전달됩니다:

고객 마이크 → 디지털 변환 → RTP 패킷 → 네트워크 → 상담사 스피커
상담사 마이크 → 디지털 변환 → RTP 패킷 → 네트워크 → 고객 스피커

RTP 패킷 구조:
┌─────────────────────────────────────────────┐
│ Version(2) │ PT(7) │ Sequence Number(16)   │
│ Timestamp(32)                               │
│ SSRC(32) ← 이 스트림의 고유 ID             │
│ 음성 데이터 (Payload) ...                  │
└─────────────────────────────────────────────┘

PT (Payload Type): 0 = G.711 μ-law, 8 = G.711 A-law
SSRC: 각 단방향 스트림의 고유 식별자 (RX/TX 구분에 사용)
```

### 3.3 패킷 미러링이란?

> **비유**: CCTV의 화면을 복사해서 다른 모니터로도 보내는 것과 같습니다. 원본 통화에는 영향 없이 패킷을 복사합니다.

```
일반적인 네트워크:
고객 ──패킷──► 스위치 ──패킷──► IPCC 서버

포트 미러링 설정 후:
고객 ──패킷──► 스위치 ──패킷──► IPCC 서버 (원본, 변화 없음)
                     └──패킷(복사)──► STTGateway 서버 eth3

STTGateway 서버는 "엿듣기" 모드로 패킷을 수신합니다.
tcpdump -ni eth3 -c 20  ← 이 명령어로 들어오는 패킷 확인 가능
```

### 3.4 G.711 코덱이란?

> **비유**: 음성을 디지털로 저장하는 방식입니다. MP3처럼 음악을 압축하듯, 전화 음성도 특정 방식으로 변환합니다.

```
G.711 특징:
- 샘플링: 8,000 Hz (1초에 8,000번 소리를 측정)
- 비트: 8 bit per sample
- 대역폭: 64 kbps (초당 8,000 × 8 bit = 64,000 bit)
- 방식: A-law (유럽/아시아) 또는 μ-law (미국)

변환 과정:
RTP Payload (G.711 A-law bytes)
    → G.711 디코딩 (a-law to linear PCM 변환)
    → PCM 16-bit linear (표준 오디오 형식)
    → WAV 파일에 헤더 추가하여 저장
```

### 3.5 배치 STT vs 스트리밍 STT

```
배치(Batch) STT:
─────────────────
통화 시작 ──────────── 통화 종료 ──► WAV 파일 ──► Google STT ──► 텍스트
                              (통화 종료 후 처리)
장점: 안정적, Google STT 비용 효율적
단점: 실시간이 아님 (통화 끝난 후에 텍스트 확인 가능)

스트리밍(Streaming) STT:
─────────────────────────
통화 시작 ──► 실시간 오디오 ──► Google STT ──► 실시간 텍스트
         (통화 중에 동시 처리)
장점: 실시간으로 텍스트 확인 가능
단점: Google STT 최대 5분 제한, 네트워크 비용 높음
```

### 3.6 WebSocket이란?

> **비유**: 일반 HTTP는 "문자 한 번 보내고 답장 받으면 끝"이지만, WebSocket은 "카카오톡처럼 연결을 유지하면서 계속 메시지를 주고받는" 방식입니다.

```
일반 HTTP:
브라우저 ──[요청]──► 서버
브라우저 ◄──[응답]── 서버
(연결 종료)

WebSocket:
브라우저 ──[연결 요청]──► 서버
브라우저 ◄══[연결 유지]══ 서버
브라우저 ◄──[텍스트 push]── 서버  ← 실시간 STT 결과 전달
브라우저 ◄──[텍스트 push]── 서버
브라우저 ◄──[텍스트 push]── 서버
```

---

## 4. 서버 디렉토리 구조

### 4.1 배포 서버 (211.192.89.96) 디렉토리

```
/
├── APP/                                    # 애플리케이션 홈
│   ├── stt-gateway.jar                    # 실행 가능한 JAR 파일
│   ├── application.yaml                   # 운영 설정 파일 (외부 설정)
│   ├── google-credentials.json            # Google Cloud 서비스 계정 키
│   ├── start.sh                           # 실행 스크립트
│   └── stop.sh                            # 중지 스크립트
│
├── APP_LOGS/                              # 로그 파일 디렉토리
│   ├── stt-gateway.log                   # 현재 로그
│   ├── stt-gateway.2024-01-01.log        # 일자별 롤링 로그
│   └── error.log                         # 에러 전용 로그
│
└── APP_DATA/                             # 데이터 저장 디렉토리
    ├── DUMP/                             # 패킷 덤프 파일 (3일 보관)
    │   ├── 20240101/                     # 날짜별 폴더
    │   │   ├── call_a84b4c76e66710.pcap  # 콜별 패킷 덤프
    │   │   └── ...
    │   └── 20240102/
    │
    └── audio/                           # 오디오 파일 (30일 보관)
        ├── 20240101/                    # 날짜별 폴더
        │   ├── a84b4c76e66710_rx.wav   # 고객 음성 (수신)
        │   ├── a84b4c76e66710_tx.wav   # 상담사 음성 (송신)
        │   └── ...
        └── 20240102/
```

### 4.2 파일 명명 규칙

```
오디오 파일:
{날짜}/{SIP_Call-ID_앞20자}_{채널}.wav

예시:
20240101/a84b4c76e66710abcdef_rx.wav   ← 고객 음성
20240101/a84b4c76e66710abcdef_tx.wav   ← 상담사 음성

패킷 덤프:
{날짜}/call_{SIP_Call-ID_앞20자}.pcap
```

### 4.3 디스크 용량 계산

```
1통화 기준 (평균 5분 통화):
- RTP 오디오 (G.711): 64kbps × 60초 × 5분 × 2채널 = 약 4.8MB
- WAV 헤더 추가: 약 5MB/콜
- 패킷 덤프 (SIP+RTP): 약 6MB/콜 (TCP/UDP 헤더 포함)
- 합계: 약 11MB/콜

하루 1,000콜 기준:
- 오디오: 5MB × 1,000 = 5GB/일
- 덤프: 6MB × 1,000 = 6GB/일
- 합계: 약 11GB/일

보관 기준:
- DUMP 파일: 3일 보관 → 약 33GB
- 오디오 파일: 30일 보관 → 약 150GB
- SSD 권장 (NVMe SSD 500GB 이상)
```

---

## 5. 스프링부트 프로젝트 설정

### 5.1 start.spring.io 설정

```
Project:      Gradle - Groovy
Language:     Java
Spring Boot:  3.5.13

Project Metadata:
  Group:        com.ktis
  Artifact:     stt-gateway
  Name:         stt-gateway
  Description:  STT Gateway for AICC Contact Center
  Package name: com.ktis.stt_gateway   ← 하이픈은 Java 패키지명 불가, 언더스코어 사용
  Packaging:    Jar
  Java:         17
```

> **주의**: `com.ktis.stt-gateway`는 Java 패키지명으로 사용 불가 (하이픈은 뺄셈 연산자로 인식됨). 반드시 `com.ktis.stt_gateway` (언더스코어) 사용.

### 5.2 application.yaml 핵심 설정

```yaml
spring:
  application:
    name: stt-gateway

  datasource:
    url: jdbc:postgresql://localhost:5432/sttgw
    username: sttgw_user
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8080

logging:
  file:
    name: /APP_LOGS/stt-gateway.log
  level:
    root: INFO
    com.ktis.stt_gateway: DEBUG

stt:
  gateway:
    capture:
      interface: eth3
      filter: "udp port 5060 or (udp portrange 10000-20000)"
      dump-path: /APP_DATA/DUMP
      dump-enabled: true
      dump-retention-days: 3
    audio:
      output-path: /APP_DATA/audio
      format: WAV
      sample-rate: 8000
      bits-per-sample: 16
      retention-days: 30
    sip:
      port: 5060
    rtp:
      port-min: 10000
      port-max: 20000
    streaming:
      max-duration-seconds: 270
      overlap-seconds: 2
```

---

## 6. Dependencies 선택

### 6.1 build.gradle 전체 의존성

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.13'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.ktis'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // ─── Spring Boot 기본 ──────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'

    // ─── 데이터베이스 ──────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'
    runtimeOnly 'com.h2database:h2'

    // ─── 보안 ──────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'

    // ─── WebSocket (실시간 STT 결과 push) ─────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-websocket'

    // ─── 패킷 캡처 (pcap4j) ──────────────────────────────────────────
    implementation 'org.pcap4j:pcap4j-core:1.8.2'
    implementation 'org.pcap4j:pcap4j-packetfactory-static:1.8.2'

    // ─── Google STT ───────────────────────────────────────────────────
    implementation 'com.google.cloud:google-cloud-speech:4.39.0'
    implementation 'com.google.auth:google-auth-library-oauth2-http:1.24.1'

    // ─── Circuit Breaker (STT 서비스 장애 대응) ────────────────────────
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'

    // ─── 유틸리티 ─────────────────────────────────────────────────────
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // ─── 개발 도구 ────────────────────────────────────────────────────
    developmentOnly 'org.springframework.boot:spring-boot-devtools'

    // ─── 테스트 ───────────────────────────────────────────────────────
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:postgresql:1.19.8'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

### 6.2 주요 Dependencies 선택 이유

| 라이브러리 | 이유 |
|-----------|------|
| `pcap4j-core` | Java에서 네트워크 패킷을 캡처하는 사실상 표준 라이브러리. libpcap 위에서 동작 |
| `google-cloud-speech` | Google STT 공식 Java 클라이언트. gRPC 기반 스트리밍 지원 |
| `spring-boot-starter-websocket` | STOMP 기반 WebSocket으로 실시간 텍스트 push |
| `resilience4j` | STT 서비스 장애 시 Circuit Breaker 패턴으로 시스템 보호 |
| `testcontainers` | 테스트 시 실제 PostgreSQL 컨테이너 기동 (H2와 방언 차이 없음) |
| `thymeleaf` | 서버 사이드 HTML 렌더링. 별도 프론트엔드 프레임워크 없이 관리 화면 구현 |

---

## 7. 핵심 모듈 설계 - 전체 패키지 구조

```
src/
└── main/
    ├── java/
    │   └── com/ktis/stt_gateway/
    │       │
    │       ├── SttGatewayApplication.java
    │       │
    │       ├── config/
    │       │   ├── AppProperties.java               # application.yaml 바인딩
    │       │   ├── AsyncConfig.java                 # 비동기 스레드풀 설정
    │       │   ├── WebSocketConfig.java
    │       │   ├── SecurityConfig.java
    │       │   └── GoogleSttConfig.java
    │       │
    │       ├── capture/                             # [모듈1] 패킷 캡처
    │       │   ├── PacketCaptureService.java
    │       │   ├── PacketCaptureManager.java
    │       │   ├── PcapFileReader.java
    │       │   └── PacketDispatcher.java
    │       │
    │       ├── sip/                                 # [모듈2] SIP 파싱
    │       │   ├── SipParser.java
    │       │   ├── SipMessage.java
    │       │   ├── SipMethod.java
    │       │   └── SipSessionTracker.java
    │       │
    │       ├── rtp/                                 # [모듈3] RTP 디코딩
    │       │   ├── RtpDecoder.java
    │       │   ├── RtpPacket.java
    │       │   ├── G711Codec.java
    │       │   └── RtpStreamBuffer.java
    │       │
    │       ├── session/                             # [모듈4] 통화 세션 관리
    │       │   ├── CallSession.java
    │       │   ├── CallSessionManager.java
    │       │   └── CallEventListener.java
    │       │
    │       ├── audio/                               # [모듈5] 오디오 파일 생성
    │       │   ├── AudioFileWriter.java
    │       │   ├── WavFileBuilder.java
    │       │   ├── AudioChannel.java                # RX / TX enum
    │       │   └── AudioFileManager.java            # 오래된 파일 정리 스케줄러
    │       │
    │       ├── stt/                                 # [모듈6] STT 연동
    │       │   ├── SttProvider.java                 # 인터페이스 (핵심)
    │       │   ├── SttRequest.java
    │       │   ├── SttResult.java
    │       │   ├── SttMode.java                     # BATCH / STREAMING enum
    │       │   ├── SttProviderFactory.java
    │       │   ├── SttService.java
    │       │   └── provider/
    │       │       ├── GoogleSttProvider.java
    │       │       ├── NaverSttProvider.java        # 향후 추가
    │       │       └── EtriSttProvider.java         # 향후 추가
    │       │
    │       ├── push/                                # [모듈7] WebSocket Push
    │       │   ├── WebSocketPushService.java
    │       │   └── TranscriptionMessage.java
    │       │
    │       ├── api/                                 # [모듈8] REST API
    │       │   ├── CallController.java
    │       │   ├── SttConfigController.java
    │       │   ├── AudioController.java
    │       │   └── CaptureController.java
    │       │
    │       ├── web/                                 # [모듈9] Thymeleaf 화면
    │       │   ├── DashboardController.java
    │       │   ├── SttConfigWebController.java
    │       │   └── MonitorController.java
    │       │
    │       ├── domain/                              # JPA Entity
    │       │   ├── Call.java
    │       │   ├── SttResultEntity.java
    │       │   ├── SttProviderConfig.java
    │       │   ├── AudioFile.java
    │       │   ├── CallStatus.java                  # enum
    │       │   └── SttMode.java                     # enum (stt 패키지와 공유)
    │       │
    │       ├── repository/
    │       │   ├── CallRepository.java
    │       │   ├── SttResultRepository.java
    │       │   ├── SttProviderConfigRepository.java
    │       │   └── AudioFileRepository.java
    │       │
    │       ├── dto/
    │       │   ├── CallDto.java
    │       │   ├── SttResultDto.java
    │       │   ├── SttProviderConfigDto.java
    │       │   └── PageResponseDto.java
    │       │
    │       └── exception/
    │           ├── SttGatewayException.java
    │           ├── CallNotFoundException.java
    │           ├── SttProcessingException.java
    │           ├── PacketCaptureException.java
    │           └── GlobalExceptionHandler.java
    │
    └── resources/
        ├── application.yaml
        ├── application-dev.yaml
        ├── application-prod.yaml
        ├── static/
        │   ├── css/dashboard.css
        │   └── js/
        │       ├── websocket-client.js
        │       └── stt-config.js
        └── templates/
            ├── layout/base.html
            ├── dashboard.html
            ├── stt-config.html
            └── monitor.html
```

---

## 8. 애플리케이션 내 각 모듈별 구성도

### 8.1 전체 모듈 의존 관계

```
┌──────────────────────────────────────────────────────────────────┐
│                    외부 입력                                      │
│  eth3 네트워크  │  .pcap 파일  │  REST API 호출  │  브라우저     │
└───────┬─────────┴──────┬───────┴────────┬────────┴────┬──────────┘
        │                │                │             │
        ▼                ▼                ▼             ▼
┌───────────────┐ ┌──────────────┐ ┌──────────┐ ┌──────────────┐
│  [모듈1]      │ │  [모듈1]     │ │ [모듈8]  │ │  [모듈9]     │
│ PacketCapture │ │ PcapFileRead │ │ REST API │ │  Web (HTML)  │
│  (실시간 캡처) │ │ (파일 읽기) │ └────┬─────┘ └──────┬───────┘
└───────┬───────┘ └──────┬───────┘      │               │
        │                │              │               │
        └────────┬────────┘              │               │
                 ▼                       │               │
        ┌────────────────┐              │               │
        │ PacketDispatch │              │               │
        │  SIP? → 분기   │              │               │
        │  RTP? → 분기   │              │               │
        └────┬──────┬────┘              │               │
             │      │                   │               │
             ▼      ▼                   │               │
    ┌──────────┐ ┌──────────┐           │               │
    │ [모듈2]  │ │ [모듈3]  │           │               │
    │  SIP     │ │  RTP     │           │               │
    │ Parser   │ │ Decoder  │           │               │
    └────┬─────┘ └────┬─────┘           │               │
         │             │                │               │
         ▼             ▼                │               │
    ┌─────────────────────────────┐     │               │
    │         [모듈4]             │◄────┘               │
    │    CallSession Manager      │                     │
    │  - 콜 생성/종료              │                     │
    │  - RTP↔콜 매핑              │                     │
    └───────┬─────────────────────┘                     │
            │                                           │
            ├──────────────────────────────┐            │
            ▼                              ▼            │
    ┌──────────────┐              ┌──────────────────┐  │
    │   [모듈5]    │              │     [모듈6]      │  │
    │ AudioFile    │              │   STT Service    │  │
    │   Writer     │              │                  │  │
    │ (비동기)     │              │ ┌──────────────┐ │  │
    │              │              │ │SttProvider   │ │  │
    │ /APP_DATA/   │              │ │ Interface    │ │  │
    │  audio/      │              │ └──────┬───────┘ │  │
    └──────────────┘              │        │         │  │
                                  │  ┌─────┴──────┐  │  │
                                  │  │ Google STT │  │  │
                                  │  │ Naver STT  │  │  │
                                  │  │ ETRI STT   │  │  │
                                  │  └────────────┘  │  │
                                  └────────┬──────────┘  │
                                           │             │
                                           ▼             │
                                  ┌──────────────────┐   │
                                  │    [모듈7]       │   │
                                  │  WebSocket Push  │◄──┘
                                  │ /topic/stt/{id}  │
                                  └──────────────────┘
                                           │
                                           ▼
                                  ┌──────────────────┐
                                  │   브라우저       │
                                  │  (실시간 텍스트) │
                                  └──────────────────┘
```

---

## 9. 모듈별 상세 구현

### 9.1 모듈1: 패킷 캡처 (PacketCapture)

#### 역할
eth3 네트워크 인터페이스에서 SIP(5060)와 RTP(10000-20000) UDP 패킷을 캡처하거나 .pcap 파일을 읽어서 분석합니다.

#### 핵심 클래스 코드 구조

```java
// PacketCaptureService.java
@Service
@Slf4j
public class PacketCaptureService {

    private final AppProperties props;
    private final PacketDispatcher dispatcher;
    private PcapHandle handle;
    private volatile boolean running = false;

    public void startCapture() throws Exception {
        String interfaceName = props.getCapture().getInterfaceName(); // "eth3"

        PcapNetworkInterface nif = Pcaps.getDevByName(interfaceName);
        handle = nif.openLive(
            65535,                               // 캡처할 최대 패킷 크기 (bytes)
            PromiscuousMode.PROMISCUOUS,         // 무차별 모드
            100                                  // timeout ms
        );

        // BPF 필터: SIP(5060)와 RTP(10000~20000) 포트만 캡처
        handle.setFilter(
            "udp port 5060 or (udp portrange 10000-20000)",
            BpfProgram.BpfCompileMode.OPTIMIZE
        );

        running = true;
        new Thread(this::captureLoop, "packet-capture-thread").start();
    }

    private void captureLoop() {
        try {
            handle.loop(-1, (RawPacketListener) rawData -> {
                if (!running) return;
                dispatcher.dispatch(rawData, handle.getTimestamp());
            });
        } catch (Exception e) {
            log.error("패킷 캡처 오류: {}", e.getMessage());
        }
    }

    // .pcap 파일 읽기 (배치 모드)
    public void readPcapFile(String filePath) throws Exception {
        PcapHandle offlineHandle = Pcaps.openOffline(filePath);
        offlineHandle.loop(-1, (RawPacketListener) rawData ->
            dispatcher.dispatch(rawData, offlineHandle.getTimestamp())
        );
    }
}
```

```java
// PacketDispatcher.java - SIP/RTP 패킷 분류
@Component
public class PacketDispatcher {

    private final SipParser sipParser;
    private final RtpDecoder rtpDecoder;

    public void dispatch(byte[] rawData, Timestamp timestamp) {
        try {
            EthernetPacket ethPacket = EthernetPacket.newPacket(rawData, 0, rawData.length);
            IpV4Packet ipPacket = ethPacket.get(IpV4Packet.class);
            UdpPacket udpPacket = ethPacket.get(UdpPacket.class);

            if (udpPacket == null) return;

            int srcPort = udpPacket.getHeader().getSrcPort().valueAsInt();
            int dstPort = udpPacket.getHeader().getDstPort().valueAsInt();
            byte[] payload = udpPacket.getPayload().getRawData();

            if (srcPort == 5060 || dstPort == 5060) {
                sipParser.parse(payload, ipPacket, timestamp);
            } else if (isRtpPort(srcPort) || isRtpPort(dstPort)) {
                rtpDecoder.decode(payload, ipPacket, udpPacket, timestamp);
            }
        } catch (Exception e) {
            // 파싱 오류는 무시하고 계속 진행 (패킷 유실 대응)
        }
    }

    private boolean isRtpPort(int port) {
        return port >= 10000 && port <= 20000;
    }
}
```

---

### 9.2 모듈2: SIP 파싱 (SIP Parser)

#### 역할
SIP 프로토콜 메시지를 파싱하여 콜 시작(INVITE), 콜 종료(BYE), 콜 ID, 고객 전화번호를 추출합니다.

```java
// SipParser.java
@Component
@Slf4j
public class SipParser {

    private final CallSessionManager sessionManager;

    public void parse(byte[] payload, IpV4Packet ipPacket, Timestamp timestamp) {
        String sipText = new String(payload, StandardCharsets.UTF_8);
        if (!sipText.contains("SIP/2.0")) return;

        SipMessage message = parseSipMessage(sipText);

        switch (message.getMethod()) {
            case INVITE -> sessionManager.onCallStart(message, timestamp);
            case BYE    -> sessionManager.onCallEnd(message, timestamp);
            case ACK    -> sessionManager.onCallConnected(message, timestamp);
            default     -> log.debug("SIP 메시지 무시: {}", message.getMethod());
        }
    }

    private SipMessage parseSipMessage(String sipText) {
        String[] lines = sipText.split("\r\n");
        SipMessage.Builder builder = SipMessage.builder();

        for (String line : lines) {
            if (line.startsWith("Call-ID:") || line.startsWith("i:")) {
                builder.callId(extractValue(line));
            } else if (line.startsWith("From:") || line.startsWith("f:")) {
                // From: <sip:07041405642@10.0.0.1>;tag=xyz
                builder.fromNumber(extractPhoneNumber(line));
            } else if (line.startsWith("To:") || line.startsWith("t:")) {
                builder.toNumber(extractPhoneNumber(line));
            }
        }

        if (lines[0].startsWith("INVITE")) builder.method(SipMethod.INVITE);
        else if (lines[0].startsWith("BYE")) builder.method(SipMethod.BYE);

        return builder.build();
    }

    private String extractPhoneNumber(String fromLine) {
        // From: <sip:07041405642@211.192.89.43> 에서 07041405642 추출
        Pattern pattern = Pattern.compile("sip:(\\d+)@");
        Matcher matcher = pattern.matcher(fromLine);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}
```

---

### 9.3 모듈3: RTP 디코딩 및 RX/TX 분리

#### RX/TX 분리 전략

```
방법: IP 주소 기반 분리
- 고객측 IP에서 오는 RTP → RX (수신, 고객 음성)
- 상담사측 IP에서 오는 RTP → TX (송신, 상담사 음성)
- SIP SDP의 c= 라인 IP 정보와 패킷의 src IP를 비교하여 판단
```

```java
// RtpDecoder.java
@Component
@Slf4j
public class RtpDecoder {

    private final CallSessionManager sessionManager;
    private final G711Codec codec = new G711Codec();

    public void decode(byte[] payload, IpV4Packet ipPacket,
                       UdpPacket udpPacket, Timestamp timestamp) {
        if (payload.length < 12) return; // RTP 헤더 최소 12바이트

        RtpPacket rtpPacket = parseRtpHeader(payload);
        int dstPort = udpPacket.getHeader().getDstPort().valueAsInt();
        String srcIp = ipPacket.getHeader().getSrcAddr().getHostAddress();

        CallSession session = sessionManager.findByRtpPort(dstPort, srcIp);
        if (session == null) return;

        // G.711 디코딩: byte[] → short[] (16-bit PCM)
        short[] pcmSamples = codec.decode(rtpPacket.getPayload(), rtpPacket.getPayloadType());

        // RX/TX 방향 판단
        AudioChannel channel = session.isCustomerIp(srcIp) ? AudioChannel.RX : AudioChannel.TX;
        session.appendAudio(channel, pcmSamples, timestamp);
    }

    private RtpPacket parseRtpHeader(byte[] data) {
        // RFC 3550 RTP 헤더 파싱
        int payloadType = data[1] & 0x7F;           // 코덱 타입 (8=G.711 A-law)
        int seqNumber = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        long ssrc = ((data[8] & 0xFFL) << 24) | ((data[9] & 0xFFL) << 16)
                  | ((data[10] & 0xFFL) << 8) | (data[11] & 0xFFL);
        byte[] rtpPayload = Arrays.copyOfRange(data, 12, data.length);
        return new RtpPacket(payloadType, seqNumber, ssrc, rtpPayload);
    }
}
```

```java
// G711Codec.java - G.711 A-law 디코딩
public class G711Codec {

    private static final short[] ALAW_TABLE = buildAlawTable();

    public short[] decode(byte[] alawData, int payloadType) {
        short[] pcm = new short[alawData.length];
        if (payloadType == 8) {        // G.711 A-law (유럽/아시아)
            for (int i = 0; i < alawData.length; i++) {
                pcm[i] = ALAW_TABLE[alawData[i] & 0xFF];
            }
        } else if (payloadType == 0) { // G.711 μ-law (미국)
            for (int i = 0; i < alawData.length; i++) {
                pcm[i] = decodeMulaw(alawData[i]);
            }
        }
        return pcm;
    }

    private static short[] buildAlawTable() {
        short[] table = new short[256];
        for (int i = 0; i < 256; i++) {
            int alaw = i ^ 0x55;
            int sign = alaw & 0x80;
            int exponent = (alaw & 0x70) >> 4;
            int mantissa = alaw & 0x0F;
            int linear = (mantissa << 4) | 0x08;
            if (exponent > 0) linear = (linear + 0x100) << (exponent - 1);
            table[i] = (short) (sign != 0 ? linear : -linear);
        }
        return table;
    }
}
```

---

### 9.4 모듈4: 통화 세션 관리 (CallSession Manager)

```java
// CallSessionManager.java
@Component
@Slf4j
public class CallSessionManager {

    private final ConcurrentHashMap<String, CallSession> activeSessions
        = new ConcurrentHashMap<>();

    private final CallRepository callRepository;
    private final AudioFileWriter audioFileWriter;
    private final SttService sttService;

    public void onCallStart(SipMessage sip, Timestamp timestamp) {
        String callId = sip.getCallId();
        if (activeSessions.containsKey(callId)) return;

        CallSession session = CallSession.builder()
            .callId(callId)
            .callerNumber(sip.getFromNumber())
            .calleeNumber(sip.getToNumber())
            .startTime(timestamp.toLocalDateTime())
            .status(CallStatus.RINGING)
            .build();

        activeSessions.put(callId, session);
        callRepository.save(session.toEntity());
        log.info("콜 시작: callId={}, from={}", callId, sip.getFromNumber());
    }

    @Async("callProcessingExecutor")
    public void onCallEnd(SipMessage sip, Timestamp timestamp) {
        CallSession session = activeSessions.remove(sip.getCallId());
        if (session == null) return;

        session.setEndTime(timestamp.toLocalDateTime());
        session.setStatus(CallStatus.COMPLETED);

        audioFileWriter.finalizeAndSave(session);   // 오디오 파일 저장
        sttService.transcribeBatch(session);        // 배치 STT 시작
        callRepository.updateCallEnd(session.getCallId(),
            session.getEndTime(), CallStatus.COMPLETED);

        log.info("콜 종료: callId={}, duration={}초",
            session.getCallId(), session.getDurationSeconds());
    }
}
```

```java
// CallSession.java - 통화 세션 상태 객체
@Data
@Builder
public class CallSession {
    private String callId;
    private String callerNumber;
    private String calleeNumber;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private CallStatus status;
    private String customerIp;
    private int customerRtpPort;
    private int agentRtpPort;

    // 오디오 버퍼 (통화 중 메모리에 보관)
    private final Queue<short[]> rxBuffer = new ConcurrentLinkedQueue<>();
    private final Queue<short[]> txBuffer = new ConcurrentLinkedQueue<>();

    public void appendAudio(AudioChannel channel, short[] samples, Timestamp ts) {
        if (channel == AudioChannel.RX) rxBuffer.add(samples);
        else txBuffer.add(samples);
    }

    public boolean isCustomerIp(String ip) {
        return customerIp != null && customerIp.equals(ip);
    }

    public boolean isActive() {
        return status == CallStatus.ACTIVE || status == CallStatus.RINGING;
    }

    public long getDurationSeconds() {
        if (startTime == null || endTime == null) return 0;
        return Duration.between(startTime, endTime).getSeconds();
    }
}
```

---

### 9.5 모듈5: 오디오 파일 생성 (AudioFile Writer)

#### 디스크 I/O 최적화 전략

```
문제: 하루 1,000콜 × 초당 8,000개 RTP 패킷 → 즉시 쓰기 시 디스크 I/O 과부하

해결:
1. 메모리 버퍼링: PCM 데이터를 먼저 메모리(큐)에 모음
2. 통화 종료 후 일괄 저장: 통화 중에는 메모리에 보관
3. 비동기 쓰기: @Async로 별도 스레드에서 처리
4. BufferedOutputStream: 64KB 버퍼로 I/O 횟수 감소
```

```java
// AudioFileWriter.java
@Service
@Slf4j
public class AudioFileWriter {

    private final AppProperties props;
    private final AudioFileRepository audioFileRepository;

    @Async("fileWriteExecutor")
    public CompletableFuture<Void> finalizeAndSave(CallSession session) {
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path audioDir = Path.of(props.getAudio().getOutputPath(), dateDir);

        try {
            Files.createDirectories(audioDir);

            Path rxFile = audioDir.resolve(session.getCallId() + "_rx.wav");
            saveWavFile(rxFile, drainBuffer(session.getRxBuffer()));

            Path txFile = audioDir.resolve(session.getCallId() + "_tx.wav");
            saveWavFile(txFile, drainBuffer(session.getTxBuffer()));

            audioFileRepository.save(AudioFile.builder()
                .callId(session.getCallId())
                .channel("RX")
                .filePath(rxFile.toString())
                .fileSize(Files.size(rxFile))
                .format("WAV")
                .build());

            log.info("오디오 파일 저장 완료: {}", rxFile);
        } catch (IOException e) {
            log.error("오디오 파일 저장 실패: {}", e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    private void saveWavFile(Path filePath, byte[] pcmData) throws IOException {
        byte[] wavHeader = WavFileBuilder.createHeader(pcmData.length, 8000, 1, 16);
        try (BufferedOutputStream bos = new BufferedOutputStream(
                Files.newOutputStream(filePath), 65536)) { // 64KB 버퍼
            bos.write(wavHeader);
            bos.write(pcmData);
        }
    }
}
```

```java
// WavFileBuilder.java - WAV 파일 헤더 생성
public class WavFileBuilder {

    public static byte[] createHeader(int pcmDataLength, int sampleRate,
                                       int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(pcmDataLength + 36);    // ChunkSize
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);                    // Subchunk1Size (PCM)
        header.putShort((short) 1);           // AudioFormat (PCM=1)
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) bitsPerSample);
        header.put("data".getBytes());
        header.putInt(pcmDataLength);
        return header.array();
    }
}
```

---

### 9.6 모듈6: Google STT 연동 (STT Service)

#### STT 공급자 인터페이스 (확장성 핵심)

```java
// SttProvider.java - 모든 STT 공급자가 구현해야 하는 인터페이스
public interface SttProvider {

    String getProviderName();

    // 배치 방식: 파일을 읽어서 변환 (통화 후 처리)
    SttResult transcribeBatch(SttRequest request);

    // 스트리밍 방식: 실시간 오디오 스트림을 텍스트로 변환
    void transcribeStreaming(SttRequest request, Consumer<SttResult> consumer);

    void stopStreaming(String sessionKey);

    boolean isAvailable();
}
```

```java
// GoogleSttProvider.java
@Component("googleSttProvider")
@Slf4j
public class GoogleSttProvider implements SttProvider {

    private final SpeechClient speechClient;

    @Override
    public String getProviderName() { return "google"; }

    @Override
    public SttResult transcribeBatch(SttRequest request) {
        try {
            byte[] audioData = Files.readAllBytes(Path.of(request.getFilePath()));

            RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(8000)
                .setLanguageCode("ko-KR")
                .setEnableAutomaticPunctuation(true)
                .setModel("phone_call")          // 전화 통화 최적화 모델
                .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioData))
                .build();

            RecognizeResponse response = speechClient.recognize(config, audio);

            StringBuilder transcript = new StringBuilder();
            for (SpeechRecognitionResult result : response.getResultsList()) {
                transcript.append(result.getAlternatives(0).getTranscript()).append(" ");
            }

            return SttResult.builder()
                .callId(request.getCallId())
                .channel(request.getChannel())
                .transcript(transcript.toString().trim())
                .provider("google")
                .mode(SttMode.BATCH)
                .build();

        } catch (Exception e) {
            log.error("Google STT 배치 변환 오류: {}", e.getMessage());
            return SttResult.error(request.getCallId(), e.getMessage());
        }
    }

    @Override
    public void transcribeStreaming(SttRequest request, Consumer<SttResult> consumer) {
        RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setSampleRateHertz(8000)
            .setLanguageCode("ko-KR")
            .setModel("phone_call")
            .build();

        StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
            .setConfig(recognitionConfig)
            .setInterimResults(true)   // 중간 결과도 전달 (빠른 실시간 표시)
            .build();

        ResponseObserver<StreamingRecognizeResponse> responseObserver =
            new ResponseObserver<>() {
                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    for (StreamingRecognitionResult result : response.getResultsList()) {
                        String text = result.getAlternatives(0).getTranscript();
                        boolean isFinal = result.getIsFinal();

                        consumer.accept(SttResult.builder()
                            .callId(request.getCallId())
                            .transcript(text)
                            .isFinal(isFinal)
                            .mode(SttMode.STREAMING)
                            .build());
                    }
                }
                @Override
                public void onComplete() { log.debug("STT 스트림 완료"); }
                @Override
                public void onError(Throwable t) {
                    log.error("STT 스트림 오류: {}", t.getMessage());
                }
            };

        // gRPC 스트리밍 세션 시작 (첫 요청: 설정 전송)
        ClientStream<StreamingRecognizeRequest> stream =
            speechClient.streamingRecognizeCallable().splitCall(responseObserver);

        stream.send(StreamingRecognizeRequest.newBuilder()
            .setStreamingConfig(streamingConfig)
            .build());
    }
}
```

#### 5분 제한 처리 - SttService

```java
// SttService.java - 5분 제한 자동 재연결
@Service
@Slf4j
public class SttService {

    private final SttProviderFactory providerFactory;
    private final SttResultRepository resultRepository;
    private final WebSocketPushService pushService;
    private final AppProperties props;

    @Async("sttExecutor")
    public void startStreaming(CallSession session) {
        int streamNumber = 0;

        while (session.isActive()) {            // 통화 진행 중인 동안 반복
            streamNumber++;
            LocalDateTime streamStart = LocalDateTime.now();
            int maxDurationSec = props.getStreaming().getMaxDurationSeconds(); // 270초

            log.info("STT 스트리밍 시작 #{}: callId={}", streamNumber, session.getCallId());

            SttProvider provider = providerFactory.getActiveProvider();
            provider.transcribeStreaming(
                SttRequest.builder().callId(session.getCallId()).channel("RX").build(),
                result -> {
                    pushService.push(session.getCallId(), result);
                    if (result.isFinal()) {
                        resultRepository.save(result.toEntity());
                    }
                }
            );

            // 4분 30초 대기 또는 통화 종료까지 대기
            waitUntilDurationOrCallEnd(session, streamStart, maxDurationSec);
            provider.stopStreaming(session.getCallId() + "_RX");
        }

        log.info("STT 스트리밍 종료: callId={}, {}개 스트림 사용",
            session.getCallId(), streamNumber);
    }

    @Async("sttExecutor")
    public void transcribeBatch(CallSession session) {
        SttProvider provider = providerFactory.getActiveProvider();

        for (String channel : List.of("RX", "TX")) {
            SttRequest request = SttRequest.builder()
                .callId(session.getCallId())
                .channel(channel)
                .filePath(resolveAudioFilePath(session, channel))
                .languageCode("ko-KR")
                .build();

            SttResult result = provider.transcribeBatch(request);
            resultRepository.save(result.toEntity());
            log.info("배치 STT 완료: callId={}, channel={}", session.getCallId(), channel);
        }
    }
}
```

---

### 9.7 모듈7: 실시간 WebSocket Push

```java
// WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

```java
// WebSocketPushService.java
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public void push(String callId, SttResult result) {
        TranscriptionMessage message = TranscriptionMessage.builder()
            .callId(callId)
            .text(result.getTranscript())
            .channel(result.getChannel())
            .isFinal(result.isFinal())
            .timestamp(LocalDateTime.now())
            .build();

        // /topic/stt/{callId} 채널로 메시지 전송
        messagingTemplate.convertAndSend("/topic/stt/" + callId, message);
    }
}
```

#### 브라우저 JavaScript WebSocket 클라이언트

```javascript
// websocket-client.js
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    // 특정 콜 ID의 STT 결과 구독
    stompClient.subscribe('/topic/stt/' + callId, function(message) {
        const data = JSON.parse(message.body);

        if (data.channel === 'RX') {
            appendTranscript('고객', data.text, data.isFinal);
        } else {
            appendTranscript('상담사', data.text, data.isFinal);
        }
    });
});

function appendTranscript(speaker, text, isFinal) {
    const container = document.getElementById('transcript-container');

    if (!isFinal) {
        // 중간 결과: 이전 중간 결과를 교체 (회색으로 표시)
        let interim = document.getElementById('interim-' + speaker);
        if (!interim) {
            interim = document.createElement('p');
            interim.id = 'interim-' + speaker;
            interim.className = 'interim text-muted';
            container.appendChild(interim);
        }
        interim.textContent = `[${speaker}] ${text}`;
    } else {
        // 최종 결과: 새 줄로 영구 추가
        const p = document.createElement('p');
        p.className = 'final ' + (speaker === '고객' ? 'rx' : 'tx');
        p.textContent = `[${speaker}] ${text}`;
        container.appendChild(p);
        document.getElementById('interim-' + speaker)?.remove();
        container.scrollTop = container.scrollHeight;
    }
}
```

---

### 9.8 모듈8: REST API

```
[콜 관리 API]
GET    /api/v1/calls                      현재 활성 콜 목록
GET    /api/v1/calls/{callId}             특정 콜 상세 정보
GET    /api/v1/calls/{callId}/transcript  콜의 STT 텍스트 전체
GET    /api/v1/calls/history              콜 이력 (페이징)

[오디오 API]
GET    /api/v1/audio/{callId}/rx          고객 음성 파일 다운로드
GET    /api/v1/audio/{callId}/tx          상담사 음성 파일 다운로드

[STT 설정 API]
GET    /api/v1/stt/providers              등록된 STT 공급자 목록
GET    /api/v1/stt/providers/active       현재 활성 STT 공급자
PUT    /api/v1/stt/providers/{name}/activate  STT 공급자 변경
POST   /api/v1/stt/providers              새 STT 공급자 설정 저장
PUT    /api/v1/stt/providers/{id}         STT 공급자 설정 수정

[패킷 캡처 제어 API]
POST   /api/v1/capture/start              캡처 시작
POST   /api/v1/capture/stop               캡처 중지
GET    /api/v1/capture/status             캡처 상태 조회
```

---

### 9.9 모듈9: 프론트엔드 (Thymeleaf) 화면 구성

**STT 설정 + 실시간 모니터링 화면 (`/stt-config`)**

```
┌──────────────────────────────────────────────────────────┐
│  STT 공급자 설정                                          │
├──────────────────────────────────────────────────────────┤
│  현재 활성 공급자: ● Google Speech-to-Text              │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Google STT                              [활성화] │   │
│  │  인증 키 파일: /APP/google-credentials.json       │   │
│  │  언어: 한국어 (ko-KR)          [설정 저장]        │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Naver CLOVA Speech                  [활성화]    │   │
│  │  Client ID: ****               [설정 저장]        │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │  ETRI STT (국가 AI)                  [활성화]    │   │
│  │  API Key: ****                 [설정 저장]        │   │
│  └──────────────────────────────────────────────────┘   │
├──────────────────────────────────────────────────────────┤
│  실시간 통화 모니터링 (현재 통화 중: 3건)                │
├──────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────┐ │
│  │ 070-4140-5642  →  내선 1001  │  통화중 00:03:25   │ │
│  ├────────────────────────────────────────────────────┤ │
│  │ [고객]    안녕하세요 제가 지난번에 신청했던        │ │
│  │ [상담사]  네, 고객님 확인해 드리겠습니다          │ │
│  │ [고객]    그게 아직도 처리가...                   │ │
│  │ [고객]    (처리가 안 됐나요)               ← 중간결과│ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

---

## 10. 성능 최적화 전략

### 10.1 스레드풀 설계

```java
// AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {

    // 패킷 캡처 전용 (단일 스레드)
    @Bean("captureExecutor")
    public Executor captureExecutor() {
        return Executors.newSingleThreadExecutor(
            r -> new Thread(r, "packet-capture-thread"));
    }

    // 오디오 파일 쓰기 전용 (최대 20개 동시 처리)
    @Bean("fileWriteExecutor")
    public Executor fileWriteExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("file-write-");
        executor.initialize();
        return executor;
    }

    // STT 처리 전용 (최대 30개 동시 처리)
    @Bean("sttExecutor")
    public Executor sttExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("stt-");
        executor.initialize();
        return executor;
    }

    // 콜 이벤트 처리 (최대 50개)
    @Bean("callProcessingExecutor")
    public Executor callProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setThreadNamePrefix("call-proc-");
        executor.initialize();
        return executor;
    }
}
```

### 10.2 메모리 관리

```
동시 콜 50개 가정 시 메모리 사용량 추정:

RTP 버퍼 (콜당 최대 5분):
- G.711 16KB/초 × 300초 × 2채널 = 약 9.6MB/콜
- 50콜: 약 480MB

STT gRPC 스트리밍 버퍼 (콜당):
- 약 10~50MB/콜

JVM 권장 설정:
- -Xms512m -Xmx4g (최소 512MB, 최대 4GB)
- -XX:+UseG1GC (G1GC: 대용량 힙에서 안정적 GC 성능)

메모리 절약:
- 오디오 버퍼를 30초마다 파일에 플러시 (통화 중간 플러시 옵션)
```

---

## 11. STT 확장성 설계

### 11.1 Strategy 패턴으로 STT 공급자 교체

```
SttProvider (인터페이스)
    │
    ├── GoogleSttProvider      ← 현재 구현체 (배치+스트리밍)
    ├── NaverClovaSttProvider  ← 향후 추가 (배치만)
    ├── EtriSttProvider        ← 향후 추가 (한국어 특화, 금융권)
    ├── KakaoSttProvider       ← 향후 추가
    └── WhisperSttProvider     ← 향후 추가 (OpenAI)

SttProviderFactory:
    DB stt_provider_configs 테이블에서 is_active=true 공급자 조회
    → 해당 이름의 Spring Bean 반환
    → 화면에서 "활성화" 클릭 시 즉시 공급자 전환
```

### 11.2 새 STT 공급자 추가 방법

```
새 STT 공급자 추가 시 해야 할 것:

1. SttProvider 인터페이스 구현 클래스 생성:
   src/.../stt/provider/NewSttProvider.java
   @Component("newSttProvider")  ← Bean 이름: {providerName}SttProvider

2. 메서드 구현:
   - getProviderName() → "new_provider" (DB의 provider_name과 일치)
   - transcribeBatch() 구현
   - transcribeStreaming() 구현 (미지원 시 UnsupportedOperationException)

3. DB 레코드 삽입:
   INSERT INTO stt_provider_configs (provider_name, display_name, ...)
   VALUES ('new_provider', '새 STT 서비스', ...);

4. 화면에서 설정 및 활성화

→ 기존 코드 수정 없이 새 공급자 추가 가능!
```

---

*상세 DB 스키마는 `SCHEMA.md`, 코딩 컨벤션은 `CONVENTIONS.md`, 배포 가이드는 `DEPLOYMENT.md`를 참고하세요.*
