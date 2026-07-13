#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$BACKEND_DIR/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env가 없습니다. 저장소 루트에서 'cp .env.example .env'를 먼저 실행하세요."
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

docker compose \
  -f "$PROJECT_DIR/infra/local/docker-compose.yml" \
  --env-file "$ENV_FILE" \
  up -d --wait

SOURCE_GAME_ID="${1:-}"

if [[ -z "$SOURCE_GAME_ID" ]]; then
  SOURCE_GAME_ID=$(docker exec pulse-postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "
      SELECT game_id
      FROM plays
      GROUP BY game_id
      HAVING count(*) >= 100
      ORDER BY count(*) FILTER (WHERE scoring_play AND inning >= 7) DESC,
               count(*) DESC
      LIMIT 1;")
fi

if [[ -z "$SOURCE_GAME_ID" ]]; then
  echo "시뮬레이션에 사용할 경기 데이터가 없습니다."
  echo "S3 적재: bash backend/scripts/load-s3-game.sh"
  echo "라이브 수집: bash backend/scripts/collect-live-game.sh"
  exit 1
fi

if [[ ! "$SOURCE_GAME_ID" =~ ^[0-9]+$ ]]; then
  echo "경기 ID는 숫자여야 합니다: $SOURCE_GAME_ID"
  exit 1
fi

TARGET_GAME_ID=$((9000000000 + $(date +%s) % 1000000000))

export PULSE_POLLER_ENABLED=true
export PULSE_SCORER_ENABLED=true
export PULSE_SIMULATION_ENABLED=true
export PULSE_SIMULATION_SOURCE_GAME_ID="$SOURCE_GAME_ID"
export PULSE_SIMULATION_TARGET_GAME_ID="$TARGET_GAME_ID"
export PULSE_SIMULATION_SPEED="${PULSE_SIMULATION_SPEED:-20}"
export PULSE_SIMULATION_PRESET="${PULSE_SIMULATION_PRESET:-SURGE}"

echo "원본 경기: $SOURCE_GAME_ID"
echo "시뮬레이션 경기: $TARGET_GAME_ID"
echo "속도: x$PULSE_SIMULATION_SPEED / 시작 위치: $PULSE_SIMULATION_PRESET"

cd "$BACKEND_DIR"
./gradlew bootRun
