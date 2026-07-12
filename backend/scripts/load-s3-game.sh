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

if [[ $# -eq 0 ]]; then
  if ! command -v aws >/dev/null 2>&1; then
    echo "최근 S3 경기를 자동 선택하려면 AWS CLI가 필요합니다."
    exit 1
  fi

  LATEST_KEY=$(aws s3api list-objects-v2 \
    --bucket "$PULSE_REPLAY_S3_BUCKET" \
    --prefix raw/plays/ \
    --query 'sort_by(Contents,&LastModified)[-1].Key' \
    --output text)

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

docker compose \
  -f "$PROJECT_DIR/infra/local/docker-compose.yml" \
  --env-file "$ENV_FILE" \
  up -d --wait

export PULSE_REPLAY_GAME_ID="$GAME_ID"
export PULSE_REPLAY_DATE="$ARCHIVE_DATE"
export PULSE_POLLER_ENABLED=false
export PULSE_SCORER_ENABLED=false
export PULSE_SSE_ENABLED=false

echo "S3 경기 적재: gameId=$GAME_ID / date=$ARCHIVE_DATE"

cd "$BACKEND_DIR"
./gradlew bootRun --args='--spring.profiles.active=replay --spring.main.web-application-type=none'
