# STT-Gateway 배포 가이드 (DEPLOYMENT.md)

> **배포 서버**: 211.192.89.96 (Linux)
> **배포 경로**: `/APP/stt-gateway.jar`
> **Java 버전**: 17
> **빌드 도구**: Gradle Wrapper

---

## 목차

1. [서버 환경 준비](#1-서버-환경-준비)
2. [PostgreSQL 설치 및 설정](#2-postgresql-설치-및-설정)
3. [Google STT 인증 설정](#3-google-stt-인증-설정)
4. [애플리케이션 빌드](#4-애플리케이션-빌드)
5. [서버 디렉토리 구조 생성](#5-서버-디렉토리-구조-생성)
6. [운영 환경 설정 파일](#6-운영-환경-설정-파일)
7. [애플리케이션 배포 및 실행](#7-애플리케이션-배포-및-실행)
8. [systemd 서비스 등록 (자동 시작)](#8-systemd-서비스-등록-자동-시작)
9. [네트워크 설정 확인](#9-네트워크-설정-확인)
10. [모니터링 및 로그 확인](#10-모니터링-및-로그-확인)
11. [배포 체크리스트](#11-배포-체크리스트)
12. [트러블슈팅](#12-트러블슈팅)

---

## 1. 서버 환경 준비

### 1.1 Java 17 설치

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk

# CentOS/RHEL
sudo yum install -y java-17-openjdk-devel

# 설치 확인
java -version
# 출력 예: openjdk version "17.x.x"

# JAVA_HOME 설정
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### 1.2 libpcap 설치 (패킷 캡처 필수)

```bash
# pcap4j가 내부적으로 libpcap을 사용하므로 반드시 설치 필요

# Ubuntu/Debian
sudo apt-get install -y libpcap-dev

# CentOS/RHEL
sudo yum install -y libpcap-devel

# 설치 확인
ldconfig -p | grep pcap
# 출력 예: libpcap.so.1 => /usr/lib/x86_64-linux-gnu/libpcap.so.1
```

### 1.3 네트워크 인터페이스 확인

```bash
# eth3 (미러링 포트) 존재 확인
ip link show eth3

# 패킷 수신 테스트
sudo tcpdump -ni eth3 -c 20 "udp port 5060 or (udp portrange 10000-20000)"
```

### 1.4 애플리케이션 실행 계정 생성

```bash
# 보안을 위해 전용 계정으로 실행 권장
sudo useradd -r -s /bin/false sttgw

# root가 아닌 계정이 패킷 캡처를 할 수 있도록 권한 부여
sudo setcap cap_net_raw,cap_net_admin=eip $(which java)
```

---

## 2. PostgreSQL 설치 및 설정

### 2.1 PostgreSQL 15 설치

```bash
# Ubuntu
sudo apt-get install -y postgresql postgresql-contrib

# 서비스 시작
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

### 2.2 데이터베이스 및 사용자 생성

```bash
sudo -u postgres psql
```

```sql
-- 데이터베이스 생성
CREATE DATABASE sttgw
    ENCODING 'UTF8'
    LC_COLLATE 'ko_KR.UTF-8'
    LC_CTYPE 'ko_KR.UTF-8'
    TEMPLATE template0;

-- 전용 사용자 생성 (강력한 비밀번호 사용)
CREATE USER sttgw_user WITH PASSWORD 'your_strong_password_here';

-- 권한 부여
GRANT ALL PRIVILEGES ON DATABASE sttgw TO sttgw_user;
\c sttgw
GRANT ALL ON SCHEMA public TO sttgw_user;
\q
```

### 2.3 DDL 실행 (테이블 생성)

```bash
# SCHEMA.md의 DDL SQL을 파일로 저장 후 실행
psql -U sttgw_user -d sttgw -f /APP/schema.sql

# 초기 데이터 삽입 (STT 공급자 기본 설정)
psql -U sttgw_user -d sttgw -f /APP/seed.sql
```

> **참고**: Spring Boot의 `ddl-auto: validate`를 사용하므로 테이블을 미리 수동 생성해야 합니다.

---

## 3. Google STT 인증 설정

### 3.1 서비스 계정 키 파일 배치

```bash
# Google Cloud Console에서 다운로드한 JSON 키 파일을 서버에 업로드
scp google-credentials.json user@211.192.89.96:/APP/

# 파일 권한 설정 (소유자만 읽기 가능)
sudo chown sttgw:sttgw /APP/google-credentials.json
sudo chmod 400 /APP/google-credentials.json
```

### 3.2 Google Cloud IAM 권한

```
서비스 계정에 부여해야 할 IAM 역할:
- Cloud Speech Client (roles/speech.client)

활성화 필요한 API:
- Cloud Speech-to-Text API
```

---

## 4. 애플리케이션 빌드

### 4.1 로컬 빌드

```bash
# Windows
gradlew.bat clean build -x test

# Linux/Mac
./gradlew clean build -x test

# 빌드 결과 확인
ls -la build/libs/
# stt-gateway-0.0.1-SNAPSHOT.jar
```

### 4.2 서버 전송

```bash
scp build/libs/stt-gateway-0.0.1-SNAPSHOT.jar \
    user@211.192.89.96:/APP/stt-gateway.jar

sudo chown sttgw:sttgw /APP/stt-gateway.jar
sudo chmod 755 /APP/stt-gateway.jar
```

---

## 5. 서버 디렉토리 구조 생성

```bash
# 필요한 디렉토리 일괄 생성
sudo mkdir -p /APP
sudo mkdir -p /APP_LOGS
sudo mkdir -p /APP_DATA/DUMP
sudo mkdir -p /APP_DATA/audio

# 소유권 및 권한 설정
sudo chown -R sttgw:sttgw /APP /APP_LOGS /APP_DATA
sudo chmod -R 755 /APP /APP_LOGS /APP_DATA

# 확인
ls -la /APP/ /APP_LOGS/ /APP_DATA/
```

---

## 6. 운영 환경 설정 파일

### 6.1 /APP/application.yaml 생성

> **중요**: 이 파일은 Git에 절대 커밋하지 않습니다. 비밀번호, API 키 등 민감 정보가 포함됩니다.

```bash
sudo vi /APP/application.yaml
```

```yaml
# /APP/application.yaml (운영 서버 전용 설정)
spring:
  application:
    name: stt-gateway

  datasource:
    url: jdbc:postgresql://localhost:5432/sttgw
    username: sttgw_user
    password: "your_strong_password_here"    # 실제 비밀번호로 교체
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate                     # 운영: 테이블 자동 생성 안 함
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8080
  tomcat:
    threads:
      max: 200
      min-spare: 20

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
    google:
      credentials-file: /APP/google-credentials.json
```

### 6.2 파일 권한 설정

```bash
# 소유자만 읽을 수 있도록 설정 (비밀번호 포함)
sudo chown sttgw:sttgw /APP/application.yaml
sudo chmod 600 /APP/application.yaml
```

---

## 7. 애플리케이션 배포 및 실행

### 7.1 실행/중지 스크립트 작성

```bash
sudo vi /APP/start.sh
```

```bash
#!/bin/bash
# /APP/start.sh

APP_JAR="/APP/stt-gateway.jar"
CONFIG_FILE="/APP/application.yaml"
LOG_DIR="/APP_LOGS"
PID_FILE="/APP/stt-gateway.pid"

JVM_OPTS="-Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=$LOG_DIR/heapdump.hprof"

SPRING_OPTS="--spring.config.location=file:$CONFIG_FILE \
  --spring.profiles.active=prod"

echo "STT-Gateway 시작 중..."
nohup java $JVM_OPTS \
  -jar $APP_JAR \
  $SPRING_OPTS \
  >> $LOG_DIR/startup.log 2>&1 &

echo $! > $PID_FILE
echo "STT-Gateway 시작됨. PID: $(cat $PID_FILE)"
```

```bash
sudo vi /APP/stop.sh
```

```bash
#!/bin/bash
# /APP/stop.sh

PID_FILE="/APP/stt-gateway.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null 2>&1; then
        echo "STT-Gateway 중지 중... (PID: $PID)"
        kill -TERM $PID
        for i in $(seq 1 30); do
            if ! ps -p $PID > /dev/null 2>&1; then
                echo "STT-Gateway 정상 중지됨"
                rm -f $PID_FILE
                exit 0
            fi
            sleep 1
        done
        echo "강제 종료..."
        kill -KILL $PID
        rm -f $PID_FILE
    else
        echo "프로세스가 이미 종료됨"
        rm -f $PID_FILE
    fi
else
    echo "PID 파일 없음"
fi
```

```bash
sudo chmod +x /APP/start.sh /APP/stop.sh
```

### 7.2 수동 실행 테스트

```bash
sudo -u sttgw /APP/start.sh

# 로그 확인
tail -f /APP_LOGS/stt-gateway.log

# 상태 확인 (Spring Actuator)
curl http://localhost:8080/actuator/health
# 정상: {"status":"UP"}
```

---

## 8. systemd 서비스 등록 (자동 시작)

> 서버 재부팅 시 자동으로 STT-Gateway가 시작되도록 설정합니다.

```bash
sudo vi /etc/systemd/system/stt-gateway.service
```

```ini
[Unit]
Description=STT-Gateway Spring Boot Application
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=sttgw
Group=sttgw
WorkingDirectory=/APP

ExecStart=/usr/bin/java \
    -Xms512m -Xmx2g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/APP_LOGS/heapdump.hprof \
    -jar /APP/stt-gateway.jar \
    --spring.config.location=file:/APP/application.yaml \
    --spring.profiles.active=prod

ExecStop=/APP/stop.sh

# 비정상 종료 시 10초 후 재시작
Restart=on-failure
RestartSec=10

StandardOutput=append:/APP_LOGS/systemd-stdout.log
StandardError=append:/APP_LOGS/systemd-stderr.log

# 패킷 캡처를 위한 네트워크 권한
AmbientCapabilities=CAP_NET_RAW CAP_NET_ADMIN

[Install]
WantedBy=multi-user.target
```

```bash
# systemd 데몬 리로드
sudo systemctl daemon-reload

# 부팅 시 자동 시작 활성화
sudo systemctl enable stt-gateway

# 서비스 시작
sudo systemctl start stt-gateway

# 상태 확인
sudo systemctl status stt-gateway

# 로그 확인
sudo journalctl -u stt-gateway -f
```

---

## 9. 네트워크 설정 확인

### 9.1 eth3 미러링 패킷 확인

```bash
# SIP 패킷 수신 확인
sudo tcpdump -ni eth3 -c 20 "udp port 5060"

# RTP 패킷 수신 확인
sudo tcpdump -ni eth3 -c 20 "udp portrange 10000-20000"

# 패킷이 수신되지 않는 경우 확인사항:
# 1. GS1900 스위치의 포트 미러링 설정 확인
# 2. IPCC 서버(211.192.89.43)의 트래픽 발생 여부 확인
# 3. eth3 인터페이스 UP 상태 확인: ip link show eth3
```

### 9.2 방화벽 설정

```bash
# UFW (Ubuntu)
sudo ufw allow 8080/tcp

# 내부 네트워크만 허용 시
sudo ufw allow from 192.168.0.0/16 to any port 8080
sudo ufw allow from 211.192.89.0/24 to any port 8080

# firewalld (CentOS)
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

### 9.3 포트 확인

```bash
# 8080 포트 리스닝 확인
sudo ss -tlnp | grep 8080

# Google STT API 연결 확인 (HTTPS 443)
curl -v https://speech.googleapis.com
```

---

## 10. 모니터링 및 로그 확인

### 10.1 Spring Actuator 엔드포인트

```bash
# 애플리케이션 상태
curl http://localhost:8080/actuator/health

# 메모리 사용량
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 스레드 수
curl http://localhost:8080/actuator/metrics/jvm.threads.live
```

### 10.2 로그 파일 확인

```bash
# 실시간 로그 모니터링
tail -f /APP_LOGS/stt-gateway.log

# 에러만 필터링
grep "ERROR" /APP_LOGS/stt-gateway.log | tail -50

# 콜 시작/종료 이벤트만 보기
grep "콜 시작\|콜 종료" /APP_LOGS/stt-gateway.log | tail -50

# 특정 Call-ID 추적
grep "a84b4c76e66710" /APP_LOGS/stt-gateway.log
```

### 10.3 디스크 사용량 모니터링

```bash
# 전체 디스크 사용량
df -h /APP_DATA

# 날짜별 오디오 파일 크기
du -sh /APP_DATA/audio/*/

# 30일 이상 된 오디오 파일 확인
find /APP_DATA/audio -name "*.wav" -mtime +30 | wc -l
```

### 10.4 데이터베이스 모니터링

```sql
-- 현재 활성 콜 수
SELECT COUNT(*) FROM calls WHERE status IN ('RINGING', 'ACTIVE');

-- 오늘 처리된 콜 수
SELECT COUNT(*), status FROM calls
WHERE created_at >= CURRENT_DATE
GROUP BY status;

-- 현재 활성 STT 공급자
SELECT provider_name, display_name FROM stt_provider_configs
WHERE is_active = TRUE;
```

---

## 11. 배포 체크리스트

### 11.1 최초 배포 체크리스트

```
[ ] Java 17 설치 확인 (java -version)
[ ] libpcap 설치 확인 (ldconfig -p | grep pcap)
[ ] PostgreSQL 설치 및 sttgw 데이터베이스 생성
[ ] DDL SQL 실행 (테이블 생성 확인)
[ ] 초기 데이터 삽입 (STT 공급자 기본 설정)
[ ] 디렉토리 생성 (/APP, /APP_LOGS, /APP_DATA/DUMP, /APP_DATA/audio)
[ ] Google 서비스 계정 키 파일 배치 (/APP/google-credentials.json)
[ ] /APP/application.yaml 생성 및 내용 확인
[ ] stt-gateway.jar 배포 (/APP/stt-gateway.jar)
[ ] eth3 인터페이스 패킷 수신 확인 (tcpdump)
[ ] 애플리케이션 시작 및 /actuator/health 200 OK 확인
[ ] 웹 화면 접속 확인 (http://211.192.89.96:8080)
[ ] systemd 서비스 등록 및 enable 처리
```

### 11.2 업데이트 배포 체크리스트

```
[ ] DB 마이그레이션 필요 여부 확인
[ ] 빌드: ./gradlew clean build -x test
[ ] 서비스 중지: sudo systemctl stop stt-gateway
[ ] JAR 파일 교체: scp ... /APP/stt-gateway.jar
[ ] DB 마이그레이션 실행 (필요 시)
[ ] 서비스 시작: sudo systemctl start stt-gateway
[ ] 로그 확인: tail -f /APP_LOGS/stt-gateway.log
[ ] /actuator/health 상태 확인
[ ] 기능 동작 확인 (패킷 캡처, STT 변환)
```

---

## 12. 트러블슈팅

### 12.1 패킷 캡처가 안 될 때

```bash
# 원인 1: libpcap 미설치
sudo apt-get install -y libpcap-dev

# 원인 2: 권한 부족
sudo setcap cap_net_raw+ep $(readlink -f $(which java))

# 원인 3: eth3 인터페이스 없음
ip link show
# → application.yaml의 capture.interface 값 수정

# 원인 4: 스위치 미러링 미설정
sudo tcpdump -ni eth3  # 아무 패킷도 안 오면 스위치 설정 확인
```

### 12.2 PostgreSQL 연결 실패

```bash
# 서비스 상태 확인
sudo systemctl status postgresql

# 연결 테스트
psql -U sttgw_user -d sttgw -h localhost

# 오류: "password authentication failed"
# → /APP/application.yaml의 password 확인

# 오류: "Connection refused"
# → postgresql 서비스 실행 여부 확인
```

### 12.3 Google STT 연결 실패

```bash
# 인증 파일 확인
ls -la /APP/google-credentials.json

# JSON 유효성 확인
cat /APP/google-credentials.json | python3 -m json.tool

# 서버에서 Google API 접근 확인
curl -v https://speech.googleapis.com

# 방화벽에서 HTTPS(443) 아웃바운드 허용 확인
sudo ufw status
```

### 12.4 메모리 부족 (OutOfMemoryError)

```bash
# 힙 덤프 확인 (OOM 발생 시 자동 생성)
ls -la /APP_LOGS/heapdump.hprof

# 해결: start.sh 또는 systemd 설정의 -Xmx 값 증가
# -Xmx2g → -Xmx4g

# JVM 메모리 현황 (Actuator)
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

### 12.5 WAV 파일 저장 실패

```bash
# 디스크 용량 확인
df -h /APP_DATA

# 파일 권한 확인
ls -la /APP_DATA/audio/
sudo chown -R sttgw:sttgw /APP_DATA
sudo chmod -R 755 /APP_DATA
```

### 12.6 WebSocket 연결 안 됨 (실시간 텍스트 안 보일 때)

```bash
# 포트 리스닝 확인
sudo ss -tlnp | grep 8080

# 방화벽 확인
sudo ufw status

# Nginx 리버스 프록시 사용 시 WebSocket 업그레이드 설정 필요:
# proxy_set_header Upgrade $http_upgrade;
# proxy_set_header Connection "upgrade";
```

---

*배포 관련 문제 발생 시 `/APP_LOGS/stt-gateway.log`의 ERROR 레벨 로그를 먼저 확인하세요.*
