# STT-Gateway 배포 가이드 (DEPLOYMENT.md)

> **배포 서버**: 211.192.89.96 (CentOS 7)
> **배포 경로**: `/APP/stt-gateway.jar`
> **Java**: Eclipse Temurin JDK 17
> **OS 서비스 계정**: `root`
> **DB**: PostgreSQL 15 / 데이터베이스 `sttgw` / 사용자 `sttuser`

---

## 목차

1. [서버 환경 준비](#1-서버-환경-준비)
2. [Java 네트워크 권한 설정](#2-java-네트워크-권한-설정)
3. [PostgreSQL 설치 및 설정](#3-postgresql-설치-및-설정)
4. [Flyway 마이그레이션 안내](#4-flyway-마이그레이션-안내)
5. [Google STT 인증 설정](#5-google-stt-인증-설정)
6. [애플리케이션 빌드](#6-애플리케이션-빌드)
7. [서버 디렉토리 구조 생성](#7-서버-디렉토리-구조-생성)
8. [운영 환경 설정 파일](#8-운영-환경-설정-파일)
9. [애플리케이션 배포 및 실행](#9-애플리케이션-배포-및-실행)
10. [systemd 서비스 등록](#10-systemd-서비스-등록)
11. [네트워크 설정 확인](#11-네트워크-설정-확인)
12. [모니터링 및 로그 확인](#12-모니터링-및-로그-확인)
13. [배포 체크리스트](#13-배포-체크리스트)
14. [트러블슈팅](#14-트러블슈팅)
    - 14.6 [permission denied for schema public (PostgreSQL 15)](#146-permission-denied-for-schema-public-postgresql-15)
    - 14.7 [systemd status=217/USER 오류](#147-systemd-status217user-오류)
    - 14.8 [BindException: 포트 충돌](#148-bindexception-주소가-이미-사용-중-포트-충돌)
    - 14.9 [YAML TAB 문자 파싱 오류](#149-yaml-파싱-오류-tab-문자)

---

## 1. 서버 환경 준비

### 1.1 Java 17 설치 (Eclipse Temurin)

CentOS 7 기본 저장소에는 Java 17이 없으므로 Eclipse Adoptium 공식 레포를 등록합니다.

```bash
# Adoptium 레포 등록
cat << 'EOF' | sudo tee /etc/yum.repos.d/adoptium.repo
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/centos/7/x86_64
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/security/keypair/default-gpg-key/public
EOF

# Temurin 17 설치
sudo yum install -y temurin-17-jdk

# 설치 확인
java -version
# 출력 예: openjdk version "17.x.x" ... Temurin
```

### 1.2 libpcap 설치 (패킷 캡처 필수)

pcap4j가 내부적으로 `libpcap` 네이티브 라이브러리를 사용합니다.

```bash
sudo yum install -y libpcap libpcap-devel

# 설치 확인
ldconfig -p | grep pcap
# 출력 예: libpcap.so.1 => /usr/lib64/libpcap.so.1
```

### 1.3 네트워크 인터페이스 확인

```bash
# eth3 (미러링 포트) 존재 및 UP 상태 확인
ip link show eth3

# 패킷 수신 테스트
sudo tcpdump -ni eth3 -c 20 "udp port 5060 or (udp portrange 10000-20000)"
```

---

## 2. Java 네트워크 권한 설정

root 계정 없이 패킷 캡처가 가능하도록 Java 실행파일에 Linux Capability를 부여합니다.

### 2.1 권한 부여

```bash
sudo setcap cap_net_raw,cap_net_admin=eip $(readlink -f $(which java))
```

### 2.2 권한 확인 (csh 환경)

`$(command)` 구문은 bash/sh 전용입니다. **csh/tcsh 콘솔에서는 backtick(`)을 사용**합니다.

```csh
# csh/tcsh에서 사용하는 명령어
getcap `which java`
```

**정상 설정 시 예상 출력:**

```
/usr/lib/jvm/temurin-17/bin/java = cap_net_admin,cap_net_raw+eip
```

> 출력 경로는 Java 설치 위치에 따라 다를 수 있습니다.  
> 뒤에 붙는 `+eip`는 각각 **e**ffective / **i**nheritable / **p**ermitted — 세 가지 권한이 모두 부여된 정상 상태입니다.  
> 아무것도 출력되지 않으면 권한이 설정되지 않은 것이므로 2.1 단계를 재실행하세요.

---

## 3. PostgreSQL 설치 및 설정

### 3.1 PostgreSQL 15 설치 (CentOS 7)

> **주의**: 기존에 PostgreSQL 9.2가 설치된 경우 반드시 15로 업그레이드가 필요합니다.  
> Flyway 10.x는 PostgreSQL 11 이상, Hibernate 6.x는 PostgreSQL 10 이상을 요구합니다.

```bash
# PGDG 공식 레포 등록
sudo yum install -y https://download.postgresql.org/pub/repos/yum/reporpms/EL-7-x86_64/pgdg-redhat-repo-latest.noarch.rpm

# CentOS 7 내장 PostgreSQL 모듈 비활성화 (충돌 방지)
sudo yum -y module disable postgresql 2>/dev/null || true

# PostgreSQL 15 설치
sudo yum install -y postgresql15-server postgresql15

# DB 초기화 및 서비스 등록
sudo /usr/pgsql-15/bin/postgresql-15-setup initdb
sudo systemctl enable --now postgresql-15

# 서비스 상태 확인
sudo systemctl status postgresql-15
```

### 3.2 데이터베이스 및 사용자 생성

```bash
sudo -u postgres psql
```

```sql
-- 데이터베이스 생성 (인코딩 UTF-8)
CREATE DATABASE sttgw
    ENCODING 'UTF8'
    LC_COLLATE 'ko_KR.UTF-8'
    LC_CTYPE 'ko_KR.UTF-8'
    TEMPLATE template0;

-- 전용 사용자 생성 (강력한 비밀번호 사용)
CREATE USER sttuser WITH PASSWORD '운영_비밀번호';

-- 권한 부여
GRANT ALL PRIVILEGES ON DATABASE sttgw TO sttuser;
\c sttgw
GRANT ALL ON SCHEMA public TO sttuser;
GRANT CREATE ON SCHEMA public TO sttuser;
ALTER SCHEMA public OWNER TO sttuser;
\q
```

> **PostgreSQL 15 주의**: PostgreSQL 15부터 `public` 스키마의 `CREATE` 권한이 기본 제거되었습니다.
> 반드시 `sttgw` DB에 **직접 접속한 상태**에서 `GRANT` 명령을 실행해야 합니다.
> `sudo -u postgres psql`로 접속하면 기본 DB인 `postgres`에 연결되므로,
> **`sudo -u postgres psql -d sttgw`** 로 접속 후 권한을 부여해야 합니다.

### 3.3 연결 확인

```bash
psql -U sttuser -d sttgw -h localhost
# 접속 성공 시: sttgw=>
```

---

## 4. Flyway 마이그레이션 안내

### 4.1 마이그레이션 파일 목록

프로젝트에는 다음 Flyway 마이그레이션 파일이 포함되어 있습니다.

```
src/main/resources/db/migration/
  V1__create_calls_table.sql            -- calls 테이블 및 인덱스 생성
  V2__create_stt_results_table.sql      -- stt_results 테이블 생성
  V3__create_stt_provider_configs_table.sql  -- stt_provider_configs 테이블 생성
  V4__create_audio_files_table.sql      -- audio_files 테이블 생성
  V5__insert_stt_provider_seed_data.sql -- STT 공급자 초기 데이터 삽입
```

### 4.2 수동 실행 여부

> **결론: 수동 실행 불필요. 애플리케이션 최초 기동 시 Flyway가 자동 실행합니다.**

`application.yaml`에 다음 설정이 포함되어 있어 애플리케이션 시작 시 자동으로 모든 마이그레이션을 순서대로 실행합니다.

```yaml
# application.yaml (공통)
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration

# application-prod.yaml
spring:
  flyway:
    enabled: true
```

**동작 순서:**

1. 애플리케이션 기동
2. Flyway가 DB에 `flyway_schema_history` 테이블을 생성 (없으면)
3. V1 → V2 → V3 → V4 → V5 순으로 SQL 자동 실행
4. 이후 기동 시에는 이미 적용된 버전을 건너뜀 (체크섬으로 검증)

**사전 조건** (Flyway가 처리하지 않는 부분 — 수동 필요):

- PostgreSQL 서버 기동
- `sttgw` 데이터베이스 생성 ([3.2 단계](#32-데이터베이스-및-사용자-생성) 참조)
- `sttuser` 계정 생성 및 권한 부여

---

## 5. Google STT 인증 설정

### 5.1 서비스 계정 키 파일 배치

> 발급 절차는 [`Google_STT_서비스계정_키발급_가이드.md`](./Google_STT_서비스계정_키발급_가이드.md)를 참고하세요.

```bash
# Google Cloud Console에서 다운로드한 JSON 키 파일을 서버에 업로드
scp -P 7300 google-credentials.json root@211.192.89.96:/APP/

# 파일 권한 설정 (소유자만 읽기 가능)
sudo chown root:root /APP/google-credentials.json
sudo chmod 400 /APP/google-credentials.json

# 배치 확인
ls -la /APP/google-credentials.json
```

### 5.2 Google Cloud IAM 권한 확인

```
서비스 계정에 부여해야 할 IAM 역할:
  - Cloud Speech Client (roles/speech.client)

활성화 필요한 API:
  - Cloud Speech-to-Text API
```

---

## 6. 애플리케이션 빌드

### 6.1 로컬 빌드 (Windows)

```bat
gradlew.bat clean build -x test
```

### 6.2 빌드 결과 확인

```bash
ls build/libs/
# stt-gateway-0.0.1-SNAPSHOT.jar
```

### 6.3 서버 전송

```bash
scp -P 7300 build/libs/stt-gateway-0.0.1-SNAPSHOT.jar \
    root@211.192.89.96:/APP/stt-gateway.jar

# 권한 설정
sudo chown root:root /APP/stt-gateway.jar
sudo chmod 755 /APP/stt-gateway.jar
```

또는 `deploy.sh` 스크립트를 사용합니다.

```bash
./deploy.sh
# 기본값: 211.192.89.96 / 포트 7300 / 사용자 root
```

---

## 7. 서버 디렉토리 구조 생성

```bash
# 필요한 디렉토리 일괄 생성
sudo mkdir -p /APP /APP_LOGS /APP_DATA/DUMP /APP_DATA/audio

# 소유권 및 권한 설정
sudo chown -R root:root /APP /APP_LOGS /APP_DATA
sudo chmod -R 755 /APP /APP_LOGS /APP_DATA

# 확인
ls -la /APP/ /APP_LOGS/ /APP_DATA/
```

---

## 8. 운영 환경 설정 파일

### 8.1 /APP/application.yaml 생성

> **중요**: 이 파일은 Git에 절대 커밋하지 않습니다. 비밀번호 등 민감 정보가 포함됩니다.

아래 명령으로 파일을 생성합니다. `vi`로 직접 편집 시 TAB 문자가 혼입될 수 있으므로 `cat` heredoc 방식을 권장합니다.

```bash
cat > /APP/application.yaml << 'EOF'
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sttgw
    username: sttuser
    password: "운영_비밀번호"
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

  security:
    user:
      name: admin
      password: "웹콘솔_비밀번호"

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true

server:
  port: 8080

logging:
  file:
    name: /APP_LOGS/stt-gateway.log
  level:
    root: INFO
    com.ktis.stt_gateway: INFO
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
      file-name-pattern: /APP_LOGS/stt-gateway.%d{yyyy-MM-dd}.%i.log

stt:
  gateway:
    google:
      credentials-file: /APP/google-credentials.json
EOF
```

> **YAML 주의사항**
> - YAML은 **스페이스 들여쓰기만 허용**합니다. TAB 문자 혼입 시 기동 실패합니다.
> - 파일 생성 후 TAB 문자 점검:
>   ```bash
>   cat -A /APP/application.yaml | grep '\^I'
>   # 출력이 없으면 정상 (^I가 TAB 문자)
>   ```
> - TAB이 발견되면 제거:
>   ```bash
>   sed -i 's/\t//g' /APP/application.yaml
>   ```

### 8.2 /APP/application.yaml 권한 설정

비밀번호가 포함된 파일이므로 **소유자만 읽고 쓸 수 있도록** 설정합니다.

```bash
# 소유자를 서비스 계정으로 변경
sudo chown root:root /APP/application.yaml

# 소유자만 읽기/쓰기 가능 (그룹·기타 접근 완전 차단)
sudo chmod 600 /APP/application.yaml

# 확인
ls -la /APP/application.yaml
# -rw------- 1 root root ... /APP/application.yaml
```

---

## 9. 애플리케이션 배포 및 실행

### 9.1 수동 실행 테스트 (서비스 등록 전 동작 확인용)

```bash
java \
  -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -jar /APP/stt-gateway.jar \
  --spring.profiles.active=prod \
  --spring.config.additional-location=file:/APP/

# 별도 터미널에서 로그 확인
tail -f /APP_LOGS/stt-gateway.log
```

> **주의**: 수동 실행 테스트 후 반드시 프로세스를 종료(Ctrl+C)하고 systemd 서비스를 시작하세요.
> 수동 실행 상태에서 서비스를 시작하면 8080 포트 충돌로 기동에 실패합니다.
> ```bash
> # 포트 충돌 시 점유 프로세스 종료
> ss -tlnp | grep 8080
> kill -9 <PID>
> ```

**정상 기동 확인 로그:**

```
Flyway Community Edition ... has successfully applied N migrations
Started SttGatewayApplication in X.XXX seconds
```

**헬스 체크:**

```bash
curl http://localhost:8080/actuator/health
# 정상: {"status":"UP"}
```

**웹 대시보드 접속:**

브라우저에서 `http://211.192.89.96:8080` 접속 시 로그인 페이지(`/login`)로 리다이렉트됩니다.
로그인 계정은 `/APP/application.yaml`의 `spring.security.user` 설정값을 사용합니다.

| 항목 | 기본값 |
|------|--------|
| ID | `admin` |
| PW | `application.yaml`에 설정한 값 |

---

## 10. systemd 서비스 등록

### 10.1 서비스 파일 작성

```bash
sudo vi /etc/systemd/system/stt-gateway.service
```

```ini
[Unit]
Description=STT Gateway Service
After=network.target postgresql-15.service
Requires=postgresql-15.service

[Service]
Type=simple
User=root
Group=root
WorkingDirectory=/APP

ExecStart=/usr/bin/java \
    -Xms512m -Xmx2g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/APP_LOGS/heapdump.hprof \
    -jar /APP/stt-gateway.jar \
    --spring.profiles.active=prod \
    --spring.config.additional-location=file:/APP/

SuccessExitStatus=143
Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal

# 패킷 캡처 권한 (root 없이 eth3 미러 포트 캡처)
AmbientCapabilities=CAP_NET_RAW CAP_NET_ADMIN
CapabilityBoundingSet=CAP_NET_RAW CAP_NET_ADMIN

[Install]
WantedBy=multi-user.target
```

### 10.2 서비스 활성화

```bash
# systemd 데몬 리로드
sudo systemctl daemon-reload

# 부팅 시 자동 시작 활성화
sudo systemctl enable stt-gateway

# 서비스 시작
sudo systemctl start stt-gateway

# 상태 확인
sudo systemctl status stt-gateway

# 실시간 로그 확인
sudo journalctl -u stt-gateway -f
```

---

## 11. 네트워크 설정 확인

### 11.1 eth3 미러링 패킷 확인

```bash
# SIP 패킷 수신 확인
sudo tcpdump -ni eth3 -c 20 "udp port 5060"

# RTP 패킷 수신 확인
sudo tcpdump -ni eth3 -c 20 "udp portrange 10000-20000"
```

패킷이 수신되지 않을 경우 확인 사항:
1. GS1900 스위치의 포트 미러링 설정
2. IPCC 서버(211.192.89.43)에 트래픽 발생 여부
3. `ip link show eth3` — eth3 인터페이스 UP 상태

### 11.2 방화벽 설정

```bash
# 8080 포트 허용
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload

# 허용 포트 확인
sudo firewall-cmd --list-ports
```

### 11.3 포트 리스닝 확인

```bash
sudo ss -tlnp | grep 8080

# Google STT API 연결 확인 (HTTPS 443 아웃바운드)
curl -v https://speech.googleapis.com
```

---

## 12. 모니터링 및 로그 확인

### 12.1 Spring Actuator 엔드포인트

```bash
# 애플리케이션 상태
curl http://localhost:8080/actuator/health

# JVM 메모리 사용량
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 스레드 수
curl http://localhost:8080/actuator/metrics/jvm.threads.live
```

### 12.2 로그 파일 확인

```bash
# 실시간 로그
tail -f /APP_LOGS/stt-gateway.log

# 에러만 필터링
grep "ERROR" /APP_LOGS/stt-gateway.log | tail -50

# 콜 이벤트 추적
grep "콜 시작\|콜 종료" /APP_LOGS/stt-gateway.log | tail -50

# 특정 Call-ID 추적
grep "a84b4c76e66710" /APP_LOGS/stt-gateway.log
```

### 12.3 데이터베이스 상태 확인

```bash
psql -U sttuser -d sttgw -h localhost
```

```sql
-- Flyway 마이그레이션 이력 확인
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- 현재 활성 콜 수
SELECT COUNT(*) FROM calls WHERE status IN ('RINGING', 'ACTIVE');

-- 현재 활성 STT 공급자
SELECT provider_name, display_name FROM stt_provider_configs WHERE is_active = TRUE;
```

### 12.4 디스크 사용량 모니터링

```bash
df -h /APP_DATA

# 날짜별 오디오 파일 크기
du -sh /APP_DATA/audio/*/
```

---

## 13. 배포 체크리스트

### 13.1 최초 배포

```
[ ] CentOS 7 확인 (cat /etc/centos-release)
[ ] Temurin JDK 17 설치 확인 (java -version)
[ ] libpcap 설치 확인 (ldconfig -p | grep pcap)
[ ] Java 네트워크 권한 설정 확인 (getcap `which java`)
     → 출력: /path/to/java = cap_net_admin,cap_net_raw+eip
[ ] PostgreSQL 15 설치 및 기동 확인 (systemctl status postgresql-15)
[ ] DB sttgw 및 사용자 sttuser 생성 확인
[ ] sttgw DB의 public 스키마 권한 부여 확인
     → sudo -u postgres psql -d sttgw 로 접속하여 GRANT 실행
[ ] 서버 디렉토리 생성 (/APP, /APP_LOGS, /APP_DATA/DUMP, /APP_DATA/audio)
[ ] Google 서비스 계정 키 파일 배치 (/APP/google-credentials.json, 권한 400)
[ ] /APP/application.yaml 생성 및 권한 확인 (ls -la → -rw-------)
     → TAB 문자 미포함 확인 (cat -A /APP/application.yaml | grep '\^I')
[ ] stt-gateway.jar 배포 (/APP/stt-gateway.jar)
[ ] eth3 인터페이스 패킷 수신 확인 (tcpdump)
[ ] 8080 포트 방화벽 허용 확인 (firewall-cmd --list-ports)
[ ] 수동 실행 테스트 — Flyway 마이그레이션 성공 로그 확인
[ ] 수동 실행 프로세스 종료 확인 (ss -tlnp | grep 8080)
[ ] /actuator/health 200 OK 확인
[ ] 웹 화면 접속 확인 (http://211.192.89.96:8080, admin 로그인)
[ ] systemd 서비스 등록 및 enable 처리 (User=root 확인)
```

### 13.2 업데이트 배포

```
[ ] 로컬 빌드 성공 확인 (gradlew.bat clean build -x test)
[ ] 서비스 중지 (sudo systemctl stop stt-gateway)
[ ] JAR 파일 전송 (scp 또는 ./deploy.sh --skip-build)
[ ] 서비스 시작 (sudo systemctl start stt-gateway)
[ ] Flyway 마이그레이션 성공 로그 확인 (새 V번호 파일 추가 시)
[ ] /actuator/health 상태 확인
[ ] 기능 동작 확인 (패킷 캡처, STT 변환)
```

---

## 14. 트러블슈팅

### 14.1 패킷 캡처가 안 될 때

```bash
# 원인 1: libpcap 미설치
sudo yum install -y libpcap libpcap-devel

# 원인 2: Java 권한 누락 (csh 환경)
getcap `which java`
# 출력이 없으면 권한 미설정 → 아래 명령 재실행 (bash로 전환 후)
sudo setcap cap_net_raw,cap_net_admin=eip $(readlink -f $(which java))

# 원인 3: eth3 인터페이스 없음
ip link show
# → /APP/application.yaml의 capture.interface-name 값 수정

# 원인 4: 스위치 미러링 미설정
sudo tcpdump -ni eth3   # 아무 패킷도 없으면 스위치 포트 미러링 확인
```

### 14.2 Flyway 마이그레이션 실패

```bash
# 로그에서 Flyway 오류 확인
grep -i "flyway\|migration" /APP_LOGS/stt-gateway.log | grep -i "error\|fail"

# 마이그레이션 이력 직접 확인
psql -U sttuser -d sttgw -h localhost -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
```

| 오류 메시지 | 원인 | 조치 |
|------------|------|------|
| `Unable to obtain connection` | DB 미기동 또는 접속 정보 오류 | postgresql-15 서비스 상태 확인, /APP/application.yaml 비밀번호 재확인 |
| `Validate failed: Migration checksum mismatch` | 기배포된 SQL 파일 수정됨 | 해당 버전 마이그레이션 파일 원복 (내용 변경 금지) |
| `Found non-empty schema(s)` | DB에 테이블이 있는데 baseline 미설정 | 신규 설치라면 DB를 비우고 재시작 |
| `ERROR: permission denied for schema public` | PostgreSQL 15 기본 권한 정책 변경 | 아래 14.6 참조 |

### 14.6 `permission denied for schema public` (PostgreSQL 15)

PostgreSQL 15부터 `public` 스키마의 CREATE 권한이 기본 제거되었습니다.
Flyway가 `flyway_schema_history` 테이블을 생성할 때 이 오류가 발생합니다.

**주의**: 반드시 `sttgw` DB에 **직접 접속**해서 권한을 부여해야 합니다.
`sudo -u postgres psql` 기본 접속은 `postgres` DB에 연결되므로 `sttgw`에 적용되지 않습니다.

```bash
# sttgw DB에 직접 접속
sudo -u postgres psql -d sttgw
```

```sql
GRANT ALL ON SCHEMA public TO sttuser;
GRANT CREATE ON SCHEMA public TO sttuser;
ALTER SCHEMA public OWNER TO sttuser;
\q
```

### 14.7 systemd status=217/USER 오류

```
stt-gateway.service: main process exited, code=exited, status=217/USER
Failed at step USER spawning /usr/bin/java: No such process
```

서비스 파일의 `User=` 에 지정된 OS 계정이 서버에 존재하지 않을 때 발생합니다.

```bash
# 서비스 파일 확인
cat /etc/systemd/system/stt-gateway.service | grep User

# 서버에 존재하는 계정으로 수정
vi /etc/systemd/system/stt-gateway.service
# User=root 으로 변경

systemctl daemon-reload
systemctl restart stt-gateway
```

### 14.8 `BindException: 주소가 이미 사용 중` (포트 충돌)

수동 실행 테스트 후 프로세스를 종료하지 않고 systemd 서비스를 기동하면 발생합니다.

```bash
# 8080 포트 점유 프로세스 확인
ss -tlnp | grep 8080

# 해당 PID 종료
kill -9 <PID>

# 서비스 재시작
systemctl restart stt-gateway
```

### 14.9 YAML 파싱 오류 (`TAB` 문자)

```
found character '\t(TAB)' that cannot start any token.
(Do not use \t(TAB) for indentation)
```

`vi`로 파일 편집 시 또는 복사/붙여넣기 과정에서 TAB 문자가 혼입되면 발생합니다.
YAML은 스페이스 들여쓰기만 허용합니다.

```bash
# TAB 문자 위치 확인 (^I 로 표시됨)
cat -A /APP/application.yaml | grep -n '\^I'

# TAB 문자 일괄 제거
sed -i 's/\t//g' /APP/application.yaml

# 제거 확인 후 서비스 재시작
systemctl restart stt-gateway
```

### 14.3 PostgreSQL 연결 실패

```bash
# 서비스 상태 확인
sudo systemctl status postgresql-15

# 연결 테스트
psql -U sttuser -d sttgw -h localhost

# 오류: "password authentication failed" → /APP/application.yaml의 password 확인
# 오류: "Connection refused" → postgresql-15 서비스 재시작
sudo systemctl restart postgresql-15
```

### 14.4 Google STT 연결 실패

```bash
# 인증 파일 존재 및 권한 확인
ls -la /APP/google-credentials.json
# -r-------- 1 ktdsuser ktdsuser

# JSON 유효성 확인
python3 -m json.tool /APP/google-credentials.json

# 서버에서 Google API 접근 확인 (443 아웃바운드)
curl -v https://speech.googleapis.com
```

### 14.5 메모리 부족 (OutOfMemoryError)

```bash
# 힙 덤프 자동 생성 위치 확인
ls -la /APP_LOGS/heapdump.hprof

# 조치: systemd 서비스 파일의 -Xmx 값 증가
# -Xmx2g → -Xmx4g 후 daemon-reload 및 서비스 재시작
```

---

*배포 관련 문제 발생 시 `/APP_LOGS/stt-gateway.log`의 ERROR 레벨 로그와 `journalctl -u stt-gateway` 출력을 먼저 확인하세요.*
