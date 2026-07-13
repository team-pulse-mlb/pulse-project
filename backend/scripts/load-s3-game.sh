#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$BACKEND_DIR/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

if [[ $# -ne 0 && $# -ne 2 ]]; then
  echo "사용법: bash backend/scripts/load-s3-game.sh [경기_ID YYYY-MM-DD]"
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env가 없습니다. 저장소 루트에서 'cp .env.example .env'를 먼저 실행하세요."
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

if [[ -z "${PULSE_REPLAY_S3_BUCKET:-}" ]]; then
  echo ".env의 PULSE_REPLAY_S3_BUCKET을 설정하세요."
  exit 1
fi

# 회사망 프록시(Somansa 등)가 HTTPS를 가로채는 환경에서 외부 HTTPS 호출이 PKIX 오류로
# 실패하면, PULSE_JAVA_TRUSTSTORE에 해당 CA를 포함한 truststore 경로를 지정해 JVM에 주입한다.
# 집망에서는 비워 두면 된다.
if [[ -n "${PULSE_JAVA_TRUSTSTORE:-}" ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=$PULSE_JAVA_TRUSTSTORE -Djavax.net.ssl.trustStorePassword=${PULSE_JAVA_TRUSTSTORE_PASSWORD:-changeit}"
fi

if [[ $# -eq 0 ]]; then
  if ! command -v aws >/dev/null 2>&1; then
    echo "최근 S3 경기를 자동 선택하려면 AWS CLI가 필요합니다."
    exit 1
  fi

  # 객체가 1,000개를 넘으면 CLI가 페이지마다 sort_by를 적용해 키가 여러 줄 반환되므로
  # 전체 목록을 받아 클라이언트에서 정렬해 최신 키 1개만 고른다.
  LATEST_KEY=$(aws s3api list-objects-v2 \
    --bucket "$PULSE_REPLAY_S3_BUCKET" \
    --prefix raw/plays/ \
    --query 'Contents[].[LastModified,Key]' \
    --output text | sort | tail -n 1 | cut -f2)

  if [[ "$LATEST_KEY" =~ game_id=([0-9]+).*plays_([0-9]{4}-[0-9]{2}-[0-9]{2})_ ]]; then
    GAME_ID="${BASH_REMATCH[1]}"
    ARCHIVE_DATE="${BASH_REMATCH[2]}"
  else
    echo "S3에서 사용할 경기 ID와 날짜를 찾지 못했습니다."
    exit 1
  fi
else
  GAME_ID="$1"
  ARCHIVE_DATE="$2"
fi

if [[ ! "$GAME_ID" =~ ^[0-9]+$ ]]; then
  echo "경기 ID는 숫자여야 합니다: $GAME_ID"
  exit 1
fi

if [[ ! "$ARCHIVE_DATE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "날짜는 YYYY-MM-DD 형식이어야 합니다: $ARCHIVE_DATE"
  exit 1
fi

# games 행은 해당 날짜의 /games 스냅샷에서만 생성되므로 스냅샷이 없으면 미리 중단한다.
if command -v aws >/dev/null 2>&1; then
  SNAPSHOT_KEY=$(aws s3api list-objects-v2 \
    --bucket "$PULSE_REPLAY_S3_BUCKET" \
    --prefix "raw/games/dt=$ARCHIVE_DATE/" \
    --max-items 1 \
    --query 'Contents[0].Key' \
    --output text | head -n 1)
  if [[ -z "$SNAPSHOT_KEY" || "$SNAPSHOT_KEY" == "None" ]]; then
    echo "raw/games/dt=$ARCHIVE_DATE/ 스냅샷이 없습니다. 이 날짜로는 games 행이 생성되지 않으니 다른 날짜를 지정하세요."
    exit 1
  fi
fi

docker compose \
  -f "$PROJECT_DIR/infra/local/docker-compose.yml" \
  --env-file "$ENV_FILE" \
  up -d --wait

export PULSE_REPLAY_GAME_ID="$GAME_ID"
export PULSE_REPLAY_DATE="$ARCHIVE_DATE"
# 하루 games 스냅샷은 400개를 넘고 키가 시간순이라, cap이 낮으면 오전 스냅샷만 재생돼
# 경기가 SCHEDULED로 남는다. .env의 낮은 기본값과 무관하게 종료 스냅샷까지 재생하도록 올린다.
export PULSE_REPLAY_MAX_OBJECTS_PER_PREFIX=2000
export PULSE_POLLER_ENABLED=false
export PULSE_SCORER_ENABLED=false
export PULSE_SSE_ENABLED=false

echo "S3 경기 적재: gameId=$GAME_ID / date=$ARCHIVE_DATE"

cd "$BACKEND_DIR"
./gradlew bootRun --args='--spring.profiles.active=replay --spring.main.web-application-type=none'
