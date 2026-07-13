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

# 회사망 프록시(Somansa 등)가 HTTPS를 가로채면 교체된 CA가 JDK truststore에 없어
# 외부 API(balldontlie.io) 호출이 PKIX 오류로 실패한다. PULSE_JAVA_TRUSTSTORE에 해당 CA를
# 포함한 truststore 경로를 지정하면 bootRun JVM에 주입한다. 집망에서는 비워 두면 된다.
if [[ -n "${PULSE_JAVA_TRUSTSTORE:-}" ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=$PULSE_JAVA_TRUSTSTORE -Djavax.net.ssl.trustStorePassword=${PULSE_JAVA_TRUSTSTORE_PASSWORD:-changeit}"
fi

docker compose \
  -f "$PROJECT_DIR/infra/local/docker-compose.yml" \
  --env-file "$ENV_FILE" \
  up -d --wait

export PULSE_POLLER_ENABLED=true

echo "라이브 경기 수집을 시작합니다. 수집을 마치려면 Ctrl+C를 누르세요."

cd "$BACKEND_DIR"
./gradlew bootRun
