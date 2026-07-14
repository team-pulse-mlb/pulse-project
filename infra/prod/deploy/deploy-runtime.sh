#!/usr/bin/env bash
# ECR 이미지와 Secret Manager 값을 운영 Docker Compose에 반영한다.
set -euo pipefail

RUNTIME_DIR="${PULSE_RUNTIME_DIR:-/home/ubuntu/pulse-runtime}"
COMPOSE_FILE="$RUNTIME_DIR/docker-compose.prod.yml"
BACKEND_IMAGE="${1:-}"
AI_IMAGE="${2:-}"

exec 9>/run/lock/pulse-deploy.lock
flock -n 9 || {
  echo "다른 배포 또는 시크릿 동기화가 진행 중이다." >&2
  exit 1
}

set -a
# ARN과 리전만 들어 있으며 시크릿 값은 없다.
source /etc/pulse/secrets.conf
set +a

update_env_value() {
  local key="$1"
  local value="$2"
  [ -n "$value" ] || return 0
  PULSE_ENV_KEY="$key" PULSE_ENV_VALUE="$value" python3 - <<'PY'
import os
from pathlib import Path

path = Path("/home/ubuntu/pulse-runtime/.env")
key = os.environ["PULSE_ENV_KEY"]
value = os.environ["PULSE_ENV_VALUE"]
lines = path.read_text(encoding="utf-8").splitlines()
updated = []
found = False
for line in lines:
    if line.startswith(f"{key}="):
        updated.append(f"{key}={value}")
        found = True
    else:
        updated.append(line)
if not found:
    updated.append(f"{key}={value}")
path.write_text("\n".join(updated) + "\n", encoding="utf-8")
PY
}

wait_healthy() {
  local container="$1"
  local status=""
  for _ in $(seq 1 48); do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$container" 2>/dev/null || true)
    echo "$container=$status"
    [ "$status" = "healthy" ] && return 0
    [ "$status" = "unhealthy" ] && docker logs --tail=40 "$container" 2>&1 || true
    sleep 5
  done
  return 1
}

cd "$RUNTIME_DIR"
/usr/local/lib/pulse/sync-secrets.py
update_env_value PULSE_APP_IMAGE "$BACKEND_IMAGE"
update_env_value PULSE_AI_IMAGE "$AI_IMAGE"

registry=$(grep '^PULSE_APP_IMAGE=' .env | cut -d= -f2- | cut -d/ -f1)
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$registry" >/dev/null

docker compose -f "$COMPOSE_FILE" pull pulse-api pulse-poller pulse-scorer ai-service
docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate ai-service
wait_healthy pulse-ai-service
docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate pulse-api
wait_healthy pulse-api
docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate pulse-poller pulse-scorer
wait_healthy pulse-poller
wait_healthy pulse-scorer
docker compose -f "$COMPOSE_FILE" ps
