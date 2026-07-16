#!/usr/bin/env bash
# GitHub Actions가 전달한 변경 전·후 scoring.yml을 운영 이력으로 읽기 전용 비교한다.
set -euo pipefail

ARTIFACT_BUCKET="${1:-}"
ARTIFACT_PREFIX="${2:-}"
PULL_REQUEST="${3:-}"
COMMIT_SHA="${4:-}"
FROM_DATE="${5:-}"
TO_DATE="${6:-}"
RUNTIME_DIR="${PULSE_RUNTIME_DIR:-/home/ubuntu/pulse-runtime}"
COMPOSE_FILE="$RUNTIME_DIR/docker-compose.prod.yml"

require_pattern() {
  local name="$1"
  local value="$2"
  local pattern="$3"
  if [[ ! "$value" =~ $pattern ]]; then
    echo "$name 값 형식이 올바르지 않다." >&2
    exit 2
  fi
}

require_pattern "artifact bucket" "$ARTIFACT_BUCKET" '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$'
require_pattern "artifact prefix" "$ARTIFACT_PREFIX" '^backtest/pr/[0-9]+/[0-9a-f]{40}$'
require_pattern "pull request" "$PULL_REQUEST" '^[0-9]+$'
require_pattern "commit SHA" "$COMMIT_SHA" '^[0-9a-f]{40}$'
require_pattern "from" "$FROM_DATE" '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
require_pattern "to" "$TO_DATE" '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'

expected_prefix="backtest/pr/$PULL_REQUEST/$COMMIT_SHA"
if [ "$ARTIFACT_PREFIX" != "$expected_prefix" ]; then
  echo "PR 번호와 커밋 SHA가 artifact prefix와 일치하지 않는다." >&2
  exit 2
fi

if [ ! -f "$COMPOSE_FILE" ] || [ ! -x /usr/local/lib/pulse/sync-secrets.py ]; then
  echo "백테스트 실행에 필요한 운영 파일이 배포되지 않았다." >&2
  exit 3
fi

set -a
source /etc/pulse/secrets.conf
set +a
: "${AWS_REGION:?AWS_REGION required}"
: "${PULSE_BACKTEST_SECRET_ID:?PULSE_BACKTEST_SECRET_ID required}"

exec 9>/run/lock/pulse-backtest.lock
flock -w 30 9 || {
  echo "다른 백테스트가 실행 중이다." >&2
  exit 4
}

WORK_DIR=$(mktemp -d "$RUNTIME_DIR/backtest-pr-${PULL_REQUEST}.XXXXXX")
INPUT_DIR="$WORK_DIR/input"
OUTPUT_DIR="$WORK_DIR/output"
BACKTEST_ENV_FILE="$WORK_DIR/.env.backtest"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR"

cleanup() {
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

artifact_uri="s3://$ARTIFACT_BUCKET/$ARTIFACT_PREFIX"
aws s3 cp "$artifact_uri/input/base.yml" "$INPUT_DIR/base.yml" --region "$AWS_REGION" --only-show-errors
aws s3 cp "$artifact_uri/input/candidate.yml" "$INPUT_DIR/candidate.yml" --region "$AWS_REGION" --only-show-errors

PULSE_ENV_FILE="$BACKTEST_ENV_FILE" \
PULSE_RDS_SECRET_ID="$PULSE_BACKTEST_SECRET_ID" \
PULSE_RUNTIME_SECRET_ID="" \
  /usr/local/lib/pulse/sync-secrets.py >/dev/null

jwt_secret=$(python3 -c 'import secrets; print(secrets.token_hex(32))')
{
  printf "JWT_SECRET='%s'\n" "$jwt_secret"
  printf "PULSE_BACKTEST_BASELINE='/work/input/base.yml'\n"
  printf "PULSE_BACKTEST_CANDIDATE='/work/input/candidate.yml'\n"
  printf "PULSE_BACKTEST_FROM='%s'\n" "$FROM_DATE"
  printf "PULSE_BACKTEST_TO='%s'\n" "$TO_DATE"
  printf "PULSE_BACKTEST_OUTPUT_DIR='/work/output'\n"
} >> "$BACKTEST_ENV_FILE"
chmod 600 "$BACKTEST_ENV_FILE"

image=$(awk -F= '/^PULSE_APP_IMAGE=/{print substr($0, index($0, "=") + 1)}' "$RUNTIME_DIR/.env" | tr -d "'\"")
if [ -z "$image" ]; then
  echo "현재 배포된 backend 이미지 정보를 찾지 못했다." >&2
  exit 5
fi
container_uid=$(docker run --rm --entrypoint sh "$image" -c 'id -u')
chown "$container_uid" "$OUTPUT_DIR"

export PULSE_BACKTEST_ENV_FILE="$BACKTEST_ENV_FILE"

cd "$RUNTIME_DIR"
docker compose -f "$COMPOSE_FILE" --profile batch run --rm --no-deps \
  --volume "$INPUT_DIR:/work/input:ro" \
  --volume "$OUTPUT_DIR:/work/output" \
  pulse-backtest-impact

if ! compgen -G "$OUTPUT_DIR/impact_*.md" >/dev/null \
    || ! compgen -G "$OUTPUT_DIR/impact_*.json" >/dev/null; then
  echo "백테스트 결과 파일이 생성되지 않았다." >&2
  exit 6
fi

aws s3 sync "$OUTPUT_DIR/" "$artifact_uri/output/" --region "$AWS_REGION" --only-show-errors
echo "백테스트 완료: PR #$PULL_REQUEST, $FROM_DATE ~ $TO_DATE"
echo "결과 위치: $artifact_uri/output/"
