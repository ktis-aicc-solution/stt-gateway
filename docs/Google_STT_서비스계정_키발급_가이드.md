# Google Cloud STT 서비스 계정 키 발급 가이드

**작성일:** 2026-04-13  
**대상:** STT-Gateway 운영 배포 담당자  
**전제 조건:** Google Cloud 계정 보유, 프로젝트 Owner 또는 Editor 권한

---

## 전체 흐름

```
① 프로젝트 확인/선택
      ↓
② Cloud Speech-to-Text API 활성화
      ↓
③ 서비스 계정 생성
      ↓
④ IAM 역할 부여
      ↓
⑤ JSON 키 파일 발급
      ↓
⑥ 운영 서버에 배치
      ↓
⑦ 애플리케이션 설정 및 동작 확인
```

---

## Step 1. Google Cloud Console 접속 및 프로젝트 확인

1. 브라우저에서 [https://console.cloud.google.com](https://console.cloud.google.com) 접속
2. 상단 프로젝트 선택 드롭다운에서 **STT-Gateway에 사용할 프로젝트를 선택**

> **주의:** 프로젝트가 없다면 `새 프로젝트 만들기`를 클릭하여 생성합니다.  
> 프로젝트 ID는 나중에 `application.yaml`에 입력하므로 메모해 두세요.

---

## Step 2. Cloud Speech-to-Text API 활성화

서비스 계정을 만들기 전에 STT API가 활성화되어 있어야 합니다.

1. 좌측 메뉴 → **`API 및 서비스`** → **`라이브러리`** 클릭
2. 검색창에 **`Cloud Speech-to-Text API`** 입력
3. 검색 결과에서 **`Cloud Speech-to-Text API`** 클릭
4. **`사용 설정`** 버튼 클릭 (이미 활성화된 경우 `사용 중` 상태로 표시됨)

> 활성화 완료 후 **API 및 서비스 → 사용 설정된 API 및 서비스** 목록에서  
> `Cloud Speech-to-Text API`가 보이면 정상입니다.

---

## Step 3. 서비스 계정 생성

1. 좌측 메뉴 → **`IAM 및 관리자`** → **`서비스 계정`** 클릭
2. 상단 **`+ 서비스 계정 만들기`** 클릭
3. 아래 정보를 입력합니다

| 항목 | 권장 입력값 | 비고 |
|------|-----------|------|
| **서비스 계정 이름** | `stt-gateway-service` | 용도를 알 수 있는 이름 |
| **서비스 계정 ID** | `stt-gateway-service` | 자동 생성됨, 변경 가능 |
| **설명 (선택)** | `STT-Gateway 애플리케이션 전용 서비스 계정` | |

4. **`만들고 계속하기`** 클릭

---

## Step 4. IAM 역할 부여

서비스 계정에 Cloud Speech-to-Text 호출 권한을 부여합니다.

1. `역할 선택` 드롭다운 클릭
2. 검색창에 **`Cloud Speech`** 입력
3. **`Cloud Speech 클라이언트`** (`roles/speech.client`) 선택

> **역할 선택 기준:**
>
> | 역할 | 권한 범위 | 권장 여부 |
> |------|---------|---------|
> | `Cloud Speech 클라이언트` (`roles/speech.client`) | STT API 호출만 가능 | **권장 (최소 권한 원칙)** |
> | `Cloud Speech 편집자` (`roles/speech.editor`) | 음성 모델 관리 포함 | 불필요한 권한 포함 |
> | `편집자` (`roles/editor`) | 프로젝트 전체 편집 | **절대 사용 금지** |

4. **`계속`** 클릭
5. `사용자에게 이 서비스 계정에 대한 액세스 권한 부여` 단계는 입력 없이 **`완료`** 클릭

---

## Step 5. JSON 키 파일 발급

1. 서비스 계정 목록에서 방금 만든 **`stt-gateway-service`** 계정의 이메일 클릭
2. 상단 탭에서 **`키`** 탭 클릭
3. **`키 추가`** → **`새 키 만들기`** 클릭
4. 키 유형: **`JSON`** 선택
5. **`만들기`** 클릭

> 브라우저에서 JSON 파일이 자동으로 다운로드됩니다.  
> 파일명 예시: `프로젝트명-a1b2c3d4e5f6.json`

### 발급된 JSON 파일 구조 (예시)

```json
{
  "type": "service_account",
  "project_id": "your-project-id",
  "private_key_id": "a1b2c3d4...",
  "private_key": "-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----\n",
  "client_email": "stt-gateway-service@your-project-id.iam.gserviceaccount.com",
  "client_id": "123456789012345678901",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  ...
}
```

> **보안 주의사항:**
> - 이 파일은 **비밀 키(private_key)를 포함**합니다. 외부 유출 시 즉시 키를 삭제하고 재발급해야 합니다.
> - Git, 이메일, 메신저 등에 **절대 업로드하지 마세요.**
> - 발급 후 안전한 저장소(사내 비밀 관리 시스템 등)에 보관하세요.

---

## Step 6. 운영 서버에 키 파일 배치

### 6-1. 키 파일 전송

로컬 PC에서 서버로 SCP로 전송합니다.

```bash
# 로컬 PC에서 실행
scp -P 7300 ~/Downloads/your-project-id-a1b2c3d4e5f6.json \
    ktdsuser@211.192.89.96:/APP/google-credentials.json
```

### 6-2. 파일 권한 설정

```bash
# 운영 서버에서 실행
chmod 400 /APP/google-credentials.json
chown ktdsuser:ktdsuser /APP/google-credentials.json

# 확인
ls -la /APP/google-credentials.json
# 출력 예: -r-------- 1 ktdsuser ktdsuser 2391 Apr 13 10:00 /APP/google-credentials.json
```

> `400` 권한: 소유자 읽기 전용. 다른 사용자는 읽기 불가.

---

## Step 7. 애플리케이션 설정

### 7-1. `/APP/application.yaml` 수정

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sttgw
    username: sttgw_user
    password: "운영_DB_비밀번호"

# Google Cloud 인증 키 경로 설정
google:
  application:
    credentials: /APP/google-credentials.json

# STT 공급자 활성화 확인 (DB에서 관리하지만, 초기 확인용)
# stt_provider_configs 테이블의 google 행: is_active = true
```

### 7-2. DB에서 Google STT 공급자 활성화 확인

Flyway 마이그레이션(`V5__insert_stt_provider_seed_data.sql`)에 의해 Google STT가 기본 활성(`is_active = true`)으로 등록됩니다.  
기동 후 아래 쿼리로 확인하세요.

```sql
SELECT provider_name, display_name, is_active
FROM stt_provider_configs;
```

기대 결과:

| provider_name | display_name | is_active |
|---------------|-------------|-----------|
| google | Google Speech-to-Text | **true** |
| naver | Naver CLOVA Speech | false |
| etri | ETRI 한국어 STT | false |

---

## Step 8. 동작 확인

### 8-1. 서비스 기동 후 인증 로그 확인

```bash
journalctl -u stt-gateway -f | grep -i "google\|credentials\|speech\|auth"
```

정상 인증 시 에러 없이 STT 서비스 초기화 메시지가 출력됩니다.

### 8-2. 인증 실패 시 주요 에러 메시지

| 에러 메시지 | 원인 | 조치 |
|-----------|------|------|
| `Could not find credentials` | JSON 파일 경로 오류 | `/APP/google-credentials.json` 경로 확인 |
| `Permission denied` | 파일 권한 문제 | `chmod 400`, `chown ktdsuser` 재확인 |
| `PERMISSION_DENIED: Cloud Speech-to-Text API has not been used` | API 미활성화 | Step 2 재수행 |
| `PERMISSION_DENIED: The caller does not have permission` | IAM 역할 누락 | Step 4 재수행, `roles/speech.client` 확인 |
| `invalid_grant` | 키 만료 또는 시스템 시간 불일치 | 서버 시간 동기화 (`ntpdate`) 또는 키 재발급 |

### 8-3. 서버 시간 동기화 (인증 오류 예방)

Google OAuth 인증은 서버 시간이 정확해야 합니다. CentOS 7에서 NTP 동기화 확인:

```bash
# 현재 시간 확인
timedatectl status

# NTP 동기화 활성화 (비활성 시)
sudo yum install -y ntp
sudo systemctl enable --now ntpd
ntpq -p
```

---

## Step 9. 키 관리 및 보안 수칙

### 키 교체 주기

| 항목 | 권장 |
|------|------|
| **정기 교체** | 6개월 ~ 1년 주기 |
| **즉시 교체** | 키 파일 유출 의심 시 |
| **즉시 삭제** | 담당자 퇴사, 프로젝트 종료 시 |

### 키 교체 절차

1. Google Cloud Console → IAM → 서비스 계정 → `stt-gateway-service` → 키 탭
2. 새 JSON 키 발급 (기존 키는 아직 삭제하지 않음)
3. 서버에 새 키 파일 배포 (`/APP/google-credentials.json` 교체)
4. 서비스 재시작 후 정상 동작 확인
5. Console에서 기존(구) 키 삭제

### 모니터링 권장

Google Cloud Console → **IAM → 서비스 계정 → `stt-gateway-service`** 페이지에서  
마지막 인증 활동을 주기적으로 확인하여 비정상 접근을 탐지하세요.

---

## 참고: gcloud CLI를 사용한 동일 작업

GUI 대신 명령줄로 수행하려는 경우 참고용입니다.

```bash
# gcloud CLI 설치 (CentOS 7)
curl https://sdk.cloud.google.com | bash
exec -l $SHELL
gcloud init

# 프로젝트 설정
gcloud config set project YOUR_PROJECT_ID

# Speech-to-Text API 활성화
gcloud services enable speech.googleapis.com

# 서비스 계정 생성
gcloud iam service-accounts create stt-gateway-service \
    --description="STT-Gateway 애플리케이션 전용 서비스 계정" \
    --display-name="stt-gateway-service"

# IAM 역할 부여
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:stt-gateway-service@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/speech.client"

# JSON 키 발급
gcloud iam service-accounts keys create /APP/google-credentials.json \
    --iam-account="stt-gateway-service@YOUR_PROJECT_ID.iam.gserviceaccount.com"

# 권한 설정
chmod 400 /APP/google-credentials.json
```

`YOUR_PROJECT_ID` 부분을 실제 프로젝트 ID로 교체하여 사용하세요.

---

*본 문서는 STT-Gateway 운영 배포 검토 보고서(`운영배포_검토보고서.md`)의 부속 문서입니다.*
