#!/usr/bin/env bash
# =============================================================================
# STT Gateway 배포 스크립트
# 사용법: ./deploy.sh [옵션]
#   -h, --host    배포 서버 주소 (기본: 211.192.89.96)
#   -u, --user    SSH 사용자 (기본: root)
#   -p, --port    SSH 포트 (기본: 22)
#   --skip-build  빌드 생략 (기존 JAR 사용)
# =============================================================================
set -euo pipefail

# --- 기본값 ---
REMOTE_HOST="211.192.89.96"
REMOTE_USER="ktdsuser"
SSH_PORT="7300"
SKIP_BUILD=false

REMOTE_APP_DIR="/APP"
REMOTE_JAR="$REMOTE_APP_DIR/stt-gateway.jar"
REMOTE_LOG="/APP_LOGS/stt-gateway.log"
SERVICE_NAME="stt-gateway"

LOCAL_JAR_PATTERN="build/libs/stt-gateway-*.jar"

# --- 색상 ---
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# --- 인수 파싱 ---
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--host)        REMOTE_HOST="$2"; shift 2 ;;
        -u|--user)        REMOTE_USER="$2"; shift 2 ;;
        -p|--port)        SSH_PORT="$2";    shift 2 ;;
        --skip-build)     SKIP_BUILD=true;  shift   ;;
        *) error "알 수 없는 옵션: $1" ;;
    esac
done

SSH_OPTS="-p $SSH_PORT -o StrictHostKeyChecking=no -o ConnectTimeout=10"
SSH_TARGET="$REMOTE_USER@$REMOTE_HOST"

echo "========================================"
info "STT Gateway 배포 시작"
info "대상 서버: $SSH_TARGET:$SSH_PORT"
echo "========================================"

# --- 1. 빌드 ---
if [ "$SKIP_BUILD" = false ]; then
    info "[1/4] Gradle 빌드 중..."
    ./gradlew clean build -x test --quiet
    info "빌드 완료"
else
    warn "[1/4] 빌드 생략 (--skip-build)"
fi

# JAR 파일 확인
LOCAL_JAR=$(ls $LOCAL_JAR_PATTERN 2>/dev/null | grep -v plain | head -1)
[ -z "$LOCAL_JAR" ] && error "JAR 파일을 찾을 수 없습니다: $LOCAL_JAR_PATTERN"
info "배포 JAR: $LOCAL_JAR"

# --- 2. 서버 연결 확인 ---
info "[2/4] 서버 연결 확인 중..."
ssh $SSH_OPTS "$SSH_TARGET" "echo OK" > /dev/null || error "서버 연결 실패"
info "서버 연결 성공"

# --- 3. JAR 전송 ---
info "[3/4] JAR 전송 중..."
scp $SSH_OPTS "$LOCAL_JAR" "$SSH_TARGET:$REMOTE_JAR.new"
ssh $SSH_OPTS "$SSH_TARGET" "
    set -e
    # 기존 JAR 백업
    if [ -f $REMOTE_JAR ]; then
        cp $REMOTE_JAR ${REMOTE_JAR}.bak
        echo 'BACKUP: 기존 JAR 백업 완료'
    fi
    mv $REMOTE_JAR.new $REMOTE_JAR
    echo 'DEPLOY: JAR 교체 완료'
"
info "JAR 전송 완료"

# --- 4. 서비스 재시작 ---
info "[4/4] 서비스 재시작 중..."
ssh $SSH_OPTS "$SSH_TARGET" "
    set -e
    sudo systemctl restart $SERVICE_NAME
    echo 'SERVICE: 재시작 명령 전송 완료'
"

# 기동 대기 (최대 30초)
info "서비스 기동 대기 중..."
for i in $(seq 1 30); do
    sleep 1
    STATUS=$(ssh $SSH_OPTS "$SSH_TARGET" "sudo systemctl is-active $SERVICE_NAME 2>/dev/null || echo inactive")
    if [ "$STATUS" = "active" ]; then
        info "서비스 기동 완료 (${i}초)"
        break
    fi
    if [ "$i" -eq 30 ]; then
        warn "30초 내 기동 확인 실패 — 로그를 확인하세요"
        ssh $SSH_OPTS "$SSH_TARGET" "journalctl -u $SERVICE_NAME -n 30 --no-pager" || true
        exit 1
    fi
done

echo "========================================"
info "배포 완료!"
info "로그 확인: ssh $SSH_TARGET tail -f $REMOTE_LOG"
echo "========================================"
