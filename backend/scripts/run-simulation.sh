#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"
COMPOSE_FILE="$PROJECT_DIR/infra/local/docker-compose.yml"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env가 없습니다. 저장소 루트에서 'cp .env.example .env'를 먼저 실행하세요."
  exit 1
fi

export PULSE_POLLER_ENABLED=true
export PULSE_GAMEPROCESSOR_ENABLED=true
export PULSE_SIMULATION_ENABLED=true
export PULSE_SIMULATION_SPEED="${PULSE_SIMULATION_SPEED:-20}"
export PULSE_SIMULATION_PRESET="${PULSE_SIMULATION_PRESET:-SURGE}"

echo "속도: x$PULSE_SIMULATION_SPEED / 시작 위치: $PULSE_SIMULATION_PRESET"

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" \
  up -d --build --force-recreate --wait ai-service pulse-api frontend

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" logs -f ai-service pulse-api
