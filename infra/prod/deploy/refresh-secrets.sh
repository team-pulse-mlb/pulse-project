#!/usr/bin/env bash
# 배포 사이에 Secret Manager 값이 바뀐 경우에만 애플리케이션 컨테이너를 재생성한다.
set -euo pipefail

exec 9>/run/lock/pulse-deploy.lock
flock -n 9 || exit 0

set -a
source /etc/pulse/secrets.conf
set +a

STATE_FILE="${PULSE_SYNC_STATE_FILE:-/home/ubuntu/pulse-runtime/.env.apply-pending}"

result=$(/usr/local/lib/pulse/sync-secrets.py)
[ "$result" = "changed" ] || exit 0

wait_healthy() {
  local container="$1"
  for _ in $(seq 1 48); do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$container" 2>/dev/null || true)
    [ "$status" = healthy ] && return 0
    sleep 5
  done
  return 1
}

cd /home/ubuntu/pulse-runtime
if grep -q '^PULSE_AI_IMAGE=.' .env; then
  docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate ai-service
  wait_healthy pulse-ai-service
else
  echo "PULSE_AI_IMAGE가 없어 ai-service 재생성을 건너뛴다."
fi
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate pulse-api
wait_healthy pulse-api
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate pulse-poller pulse-scorer
wait_healthy pulse-poller
wait_healthy pulse-scorer

# 모든 컨테이너가 healthy가 된 뒤에만 마커를 지워, 실패 시 다음 실행이 재시도하게 한다.
rm -f "$STATE_FILE"
