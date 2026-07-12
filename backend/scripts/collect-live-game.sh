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

if [[ -z "${BDL_API_KEY:-}" ]]; then
  echo ".env의 BDL_API_KEY를 설정하세요."
  exit 1
fi

docker compose \
  -f "$PROJECT_DIR/infra/local/docker-compose.yml" \
  --env-file "$ENV_FILE" \
  up -d --wait

export PULSE_POLLER_ENABLED=true

echo "라이브 경기 수집을 시작합니다. 수집을 마치려면 Ctrl+C를 누르세요."

cd "$BACKEND_DIR"
./gradlew bootRun
