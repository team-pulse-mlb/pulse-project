#!/usr/bin/env bash

# 커밋된 정적 픽스처로 개발용 예정·진행·종료 슬레이트를 멱등하게 구성한다.
#
# 전제 조건:
# - 저장소 루트에 `.env`가 있어야 한다.
# - `pulse-postgres`, `pulse-redis`가 실행 중이어야 한다.
# - 백엔드를 한 번 실행해 JPA 엔티티 기준 로컬 스키마를 먼저 생성한다.
#
# 작업 내용:
# - 팀·선수·경기·플레이·관전 점수 픽스처를 PostgreSQL에 재적재한다.
# - 예정·진행·종료 경기를 재배치하고 진행 경기의 미래 플레이를 제거한다.
# - 예상 선발, 이벤트 문구, 종료 헤드라인, 데모 사용자·알림 데이터를 재구성한다.
# - 선택 기능의 테이블이 존재할 때만 관심 선수·알림 데이터를 적재한다.
# - Redis `score:rank:live`와 `game:{id}:live`를 현재 진행 경기 기준으로 재구성한다.
# 사용법:
#   bash backend/scripts/seed-dev-slate.sh [scheduled_count] [live_count]
# 기본값은 예정 3경기, 진행 2경기이며 나머지 3경기는 종료로 배치한다.
# 인자는 각각 1 이상이어야 하고 합계는 7 이하여야 한다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
FIXTURE_DIR="$SCRIPT_DIR/fixtures"
ENV_FILE="$PROJECT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env가 없습니다. 저장소 루트에서 'cp .env.example .env'를 먼저 실행하세요."
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

SCHEDULED_COUNT="${1:-3}"
LIVE_COUNT="${2:-2}"

if [[ ! "$SCHEDULED_COUNT" =~ ^[0-9]+$ || ! "$LIVE_COUNT" =~ ^[0-9]+$ ]]; then
  echo "예정 경기 수와 진행 경기 수는 숫자여야 합니다."
  exit 1
fi

if (( SCHEDULED_COUNT < 1 || LIVE_COUNT < 1 || SCHEDULED_COUNT + LIVE_COUNT > 7 )); then
  echo "예정·진행 경기 수는 각각 1 이상이고 합계는 7 이하여야 합니다."
  exit 1
fi

for fixture_file in \
  teams.csv \
  players.csv \
  game_5059222.csv \
  plays_5059222.csv \
  watch_scores_5059222.csv \
  load-fixtures.sql \
  supplement.sql \
  notifications.sql; do
  if [[ ! -f "$FIXTURE_DIR/$fixture_file" ]]; then
    echo "픽스처 파일이 없습니다: $FIXTURE_DIR/$fixture_file"
    exit 1
  fi
done

# Git Bash(MSYS)는 컨테이너 내부 절대경로 인자를 호스트 경로로 변환하므로 이 호출만 변환을 끈다.
MSYS_NO_PATHCONV=1 docker exec -i pulse-postgres mkdir -p /tmp/pulse-fixtures
docker cp "$FIXTURE_DIR/." pulse-postgres:/tmp/pulse-fixtures >/dev/null

# psql -f 경로도 MSYS가 변환하므로 SQL은 stdin으로 넘긴다. CSV는 컨테이너 /tmp/pulse-fixtures에서 \copy로 읽는다.
for sql_file in load-fixtures.sql supplement.sql notifications.sql; do
  docker exec -i pulse-postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
    < "$FIXTURE_DIR/$sql_file"
done

docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -v scheduled_count="$SCHEDULED_COUNT" -v live_count="$LIVE_COUNT" <<'SQL'
WITH ranked AS (
    SELECT game_id, row_number() OVER (ORDER BY game_id) AS slate_order
    FROM games
    WHERE game_id BETWEEN 8800000001 AND 8800000008
      AND source = 'FIXTURE'
), scheduled AS (
    SELECT game_id, slate_order
    FROM ranked
    WHERE slate_order <= :scheduled_count
)
UPDATE games game
SET status = 'STATUS_SCHEDULED',
    lifecycle_state = 'SCHEDULED',
    start_time = GREATEST(
        ((now() AT TIME ZONE 'America/New_York')::date + time '18:00')
            AT TIME ZONE 'America/New_York',
        date_trunc('hour', now()) + interval '1 hour'
    ) + ((scheduled.slate_order - 1) * interval '30 minutes'),
    period = NULL,
    home_runs = NULL,
    away_runs = NULL,
    home_inning_scores = NULL,
    away_inning_scores = NULL,
    last_play_order = NULL,
    last_polled_at = now(),
    observed_at = now(),
    updated_at = now()
FROM scheduled
WHERE game.game_id = scheduled.game_id;

WITH ranked AS (
    SELECT game_id, row_number() OVER (ORDER BY game_id) AS slate_order
    FROM games
    WHERE game_id BETWEEN 8800000001 AND 8800000008
      AND source = 'FIXTURE'
), live AS (
    -- 전체 타임라인(실제 경기 복제) 경기는 max 이닝이 9라 그대로 두면 절단이 무효화되고
    -- 종료 상태가 노출된다. play가 많은 경기만 6회로 캡해 진짜 진행 중 상태로 만든다.
    SELECT ranked.game_id,
           ranked.slate_order,
           CASE
               WHEN (SELECT count(*) FROM plays play WHERE play.game_id = ranked.game_id) > 50
                   THEN 6
               ELSE COALESCE((
                   SELECT max(play.inning)
                   FROM plays play
                   WHERE play.game_id = ranked.game_id
               ), games.period, 7)
           END AS current_inning
    FROM ranked
    JOIN games ON games.game_id = ranked.game_id
    WHERE ranked.slate_order > :scheduled_count
      AND ranked.slate_order <= :scheduled_count + :live_count
)
UPDATE games game
SET status = 'STATUS_IN_PROGRESS',
    lifecycle_state = 'LIVE',
    start_time = now() - ((live.slate_order - :scheduled_count + 1) * interval '1 hour'),
    period = live.current_inning,
    last_polled_at = now(),
    observed_at = now(),
    updated_at = now()
FROM live
WHERE game.game_id = live.game_id;

-- 진행 경기에는 현재 이닝 이후의 미래 타임라인을 남기지 않는다.
DELETE FROM plays play
USING games game
WHERE play.game_id = game.game_id
  AND game.game_id BETWEEN 8800000001 AND 8800000008
  AND game.status = 'STATUS_IN_PROGRESS'
  AND play.inning > game.period;

-- watch_scores는 이 덤프 경기에선 inning·play_order가 모두 최종값으로 스탬프돼 있어(진행 시계열이 아님)
-- 이닝 기준으로 자르면 전부 삭제된다. 라이브 랭킹·latestTag 폴백에 쓸 최신 점수를 유지하기 위해 자르지 않는다.

WITH latest_plays AS (
    SELECT DISTINCT ON (play.game_id)
           play.game_id, play.home_score, play.away_score, play.play_order
    FROM plays play
    JOIN games game ON game.game_id = play.game_id
    WHERE game.game_id BETWEEN 8800000001 AND 8800000008
      AND game.status = 'STATUS_IN_PROGRESS'
      AND play.inning <= game.period
    ORDER BY play.game_id, play.play_order DESC
)
UPDATE games game
SET home_runs = latest_plays.home_score,
    away_runs = latest_plays.away_score,
    last_play_order = latest_plays.play_order,
    updated_at = now()
FROM latest_plays
WHERE game.game_id = latest_plays.game_id;

WITH ranked AS (
    SELECT game_id, row_number() OVER (ORDER BY game_id) AS slate_order
    FROM games
    WHERE game_id BETWEEN 8800000001 AND 8800000008
      AND source = 'FIXTURE'
), finished AS (
    SELECT game_id, slate_order
    FROM ranked
    WHERE slate_order > :scheduled_count + :live_count
)
UPDATE games game
SET status = 'STATUS_FINAL',
    lifecycle_state = 'FINAL',
    start_time = (((now() AT TIME ZONE 'America/New_York')::date + time '12:00')
                    AT TIME ZONE 'America/New_York')
                 + ((finished.slate_order - :scheduled_count - :live_count - 1)
                    * interval '25 minutes'),
    period = COALESCE((
        SELECT max(play.inning)
        FROM plays play
        WHERE play.game_id = game.game_id
    ), game.period, 9),
    last_play_order = COALESCE((
        SELECT max(play.play_order)
        FROM plays play
        WHERE play.game_id = game.game_id
    ), game.last_play_order),
    last_polled_at = now(),
    observed_at = now(),
    updated_at = now()
FROM finished
WHERE game.game_id = finished.game_id;

SELECT status,
       count(*) AS games,
       min(start_time) AS earliest,
       max(start_time) AS latest
FROM games
WHERE game_id BETWEEN 8800000001 AND 8800000008
GROUP BY status
ORDER BY status;
SQL

FIXTURE_GAME_IDS=(
  8800000001 8800000002 8800000003 8800000004
  8800000005 8800000006 8800000007 8800000008
)

for game_id in "${FIXTURE_GAME_IDS[@]}"; do
  docker exec -i pulse-redis redis-cli ZREM score:rank:live "$game_id" >/dev/null
  docker exec -i pulse-redis redis-cli DEL "game:${game_id}:live" >/dev/null
done

LIVE_ROWS=$(docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F $'\t' -c "
    SELECT game.game_id,
           COALESCE(latest_score.watch_score, 0),
           COALESCE(latest_score.base_score, 0),
           COALESCE(game.home_runs, 0),
           COALESCE(game.away_runs, 0),
           COALESCE(latest_score.inning, game.period, 0),
           COALESCE(latest_score.inning_type, 'Middle'),
           COALESCE(latest_score.play_order, game.last_play_order, 0),
           COALESCE(
               latest_score.tags[array_length(latest_score.tags, 1)],
               '경기 흐름 변화'
           )
    FROM games game
    LEFT JOIN LATERAL (
        SELECT watch_score, base_score, inning, inning_type, play_order, tags
        FROM watch_scores
        WHERE game_id = game.game_id
        ORDER BY computed_at DESC, id DESC
        LIMIT 1
    ) latest_score ON true
    WHERE game.game_id BETWEEN 8800000001 AND 8800000008
      AND game.status = 'STATUS_IN_PROGRESS'
    ORDER BY game.game_id;")

# docker exec -i는 루프의 stdin을 소비해 첫 행만 처리되므로, 입력은 FD 3으로 읽고 redis-cli는 -i 없이 호출한다.
while IFS=$'\t' read -r game_id watch_score base_score home_runs away_runs inning inning_type last_play_order latest_tag <&3; do
  if [[ -z "$game_id" ]]; then
    continue
  fi

  docker exec pulse-redis redis-cli \
    ZADD score:rank:live "$watch_score" "$game_id" >/dev/null
  docker exec pulse-redis redis-cli \
    HSET "game:${game_id}:live" \
      watchScore "$watch_score" \
      baseScore "$base_score" \
      homeRuns "$home_runs" \
      awayRuns "$away_runs" \
      inning "$inning" \
      inningType "$inning_type" \
      lastPlayOrder "$last_play_order" \
      lifecycleState "LIVE" \
      latestTag "$latest_tag" \
      updatedAt "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" >/dev/null
done 3<<< "$LIVE_ROWS"

REDIS_LIVE_COUNT=$(docker exec -i pulse-redis redis-cli ZCARD score:rank:live)

echo
echo "정적 개발 슬레이트 구성이 완료됐습니다."
echo "Redis 라이브 멤버 수: $REDIS_LIVE_COUNT"
