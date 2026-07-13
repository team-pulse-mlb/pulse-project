#!/usr/bin/env bash

# 개발용: 기존 시드 경기의 상태와 start_time을 "지금" 기준으로 재배치한다.
# 홈 화면에 예정·종료 경기가 항상 골고루 보이도록 맞추고, 진행(라이브) 경기는
# run-simulation.sh 로 별도로 띄운다.
#
# 홈이 경기를 고르는 기준:
#   - 상단 추천: 예정=now~now+36h, 종료=now-48h 이후, 진행=라이브 점수
#   - 일자별 목록: America/New_York 기준 "오늘" 슬레이트의 start_time
# 시드 경기는 고정 날짜라 시간이 지나면 "오늘" 창에서 밀려나므로 매번 재배치가 필요하다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env가 없습니다. 저장소 루트에서 'cp .env.example .env'를 먼저 실행하세요."
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

# 예정으로 돌릴 경기 수(나머지는 종료로 배치). 시뮬레이션 경기(9_000_000_000 이상)는 건드리지 않는다.
SCHEDULED_COUNT="${1:-3}"

docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -v scheduled_count="$SCHEDULED_COUNT" <<'SQL'
WITH ranked AS (
  SELECT game_id,
         row_number() OVER (ORDER BY game_id) AS rn
  FROM games
  WHERE game_id < 9000000000
)
-- 앞쪽 N개: 예정 경기를 "오늘"(뉴욕) 저녁 18시부터 30분 간격으로 배치.
-- 오늘 슬레이트 예정 탭에 노출되고, 낮 시간대 개발이면 상단 추천 예정(now~now+36h)에도 잡힌다.
UPDATE games g
SET status         = 'STATUS_SCHEDULED',
    lifecycle_state = 'SCHEDULED',
    start_time     = (((now() AT TIME ZONE 'America/New_York')::date + time '18:00')
                       AT TIME ZONE 'America/New_York')
                     + ((r.rn - 1) * interval '30 minute')
FROM ranked r
WHERE g.game_id = r.game_id
  AND r.rn <= :scheduled_count;

WITH ranked AS (
  SELECT game_id,
         row_number() OVER (ORDER BY game_id) AS rn
  FROM games
  WHERE game_id < 9000000000
)
-- 나머지: 종료 경기를 "오늘"(뉴욕) 낮 시간대로 20분 간격 배치해 종료 탭과 상단 추천 종료에 노출
UPDATE games g
SET status          = 'STATUS_FINAL',
    lifecycle_state = 'FINAL',
    start_time      = (((now() AT TIME ZONE 'America/New_York')::date + time '12:00')
                        AT TIME ZONE 'America/New_York')
                      + ((r.rn - :scheduled_count - 1) * interval '20 minute')
FROM ranked r
WHERE g.game_id = r.game_id
  AND r.rn > :scheduled_count;

-- 결과 요약
SELECT status,
       count(*) AS games,
       min(start_time) AS earliest,
       max(start_time) AS latest
FROM games
WHERE game_id < 9000000000
GROUP BY status
ORDER BY status;
SQL

echo
echo "완료. 진행(라이브) 경기는 다음으로 띄우세요:"
echo "  bash backend/scripts/run-simulation.sh"
