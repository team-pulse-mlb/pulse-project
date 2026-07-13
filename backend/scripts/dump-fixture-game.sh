#!/usr/bin/env bash

# 실제 완주 경기를 정적 픽스처 CSV로 다시 추출한다.
#
# 작업 내용:
# - 경기 ID를 생략하면 시뮬레이션 ID 범위 밖의 완주 경기 중 플레이가 가장 많은 경기를 선택한다.
# - 선택한 경기의 팀·선수·경기·플레이·관전 점수를 시드용 CSV로 추출한다.
# - 로더 계약을 유지하기 위해 경기 파일명은 `5059222`로 고정하고 내용만 교체한다.
# 사용법:
#   bash backend/scripts/dump-fixture-game.sh [game_id]

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

GAME_ID="${1:-}"

if [[ -n "$GAME_ID" && ! "$GAME_ID" =~ ^[0-9]+$ ]]; then
  echo "경기 ID는 숫자여야 합니다: $GAME_ID"
  exit 1
fi

if [[ -z "$GAME_ID" ]]; then
  GAME_ID=$(docker exec -i pulse-postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "
      SELECT g.game_id
      FROM games g
      JOIN plays p ON p.game_id = g.game_id
      WHERE g.game_id < 9000000000
        AND g.status LIKE 'STATUS_FINAL%'
      GROUP BY g.game_id
      ORDER BY count(*) DESC, g.game_id
      LIMIT 1;")
fi

if [[ -z "$GAME_ID" ]]; then
  echo "추출할 완주 경기가 없습니다."
  exit 1
fi

GAME_EXISTS=$(docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "
    SELECT count(*)
    FROM games
    WHERE game_id = $GAME_ID
      AND status LIKE 'STATUS_FINAL%';")

if [[ "$GAME_EXISTS" != "1" ]]; then
  echo "완주 경기 ID를 찾을 수 없습니다: $GAME_ID"
  exit 1
fi

mkdir -p "$FIXTURE_DIR"

docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c "\copy (SELECT team_id, abbreviation, created_at, display_name, division, league, location, logo_team_id, name, short_display_name, slug, updated_at FROM teams ORDER BY team_id) TO STDOUT WITH (FORMAT csv, HEADER true)" \
  > "$FIXTURE_DIR/teams.csv"

docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c "\copy (SELECT player_id, created_at, first_name, full_name, last_name, position, team_id, updated_at FROM players ORDER BY player_id) TO STDOUT WITH (FORMAT csv, HEADER true)" \
  > "$FIXTURE_DIR/players.csv"

docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c "\copy (SELECT game_id, away_inning_scores, away_runs, away_team_abbr, away_team_id, away_team_name, created_at, final_headline_protected, final_headline_revealed, home_inning_scores, home_runs, home_team_abbr, home_team_id, home_team_name, last_play_order, last_polled_at, lifecycle_state, observed_at, peak_base_score, period, postseason, pregame_inputs, pregame_score, source, start_time, status, updated_at, venue FROM games WHERE game_id = $GAME_ID) TO STDOUT WITH (FORMAT csv, HEADER true)" \
  > "$FIXTURE_DIR/game_5059222.csv"

docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c "\copy (SELECT id, away_score, backfilled, balls, batter_id, observed_at, game_id, home_score, inning, inning_type, outs, pitcher_id, play_order, runner_on_first, runner_on_second, runner_on_third, score_value, scoring_play, source, strikes, text, type FROM plays WHERE game_id = $GAME_ID ORDER BY play_order) TO STDOUT WITH (FORMAT csv, HEADER true)" \
  > "$FIXTURE_DIR/plays_5059222.csv"

docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c "\copy (SELECT id, backfilled, base_score, computed_at, game_id, importance_multiplier, inning, inning_type, play_order, pregame_bonus, signal_contributions, source, tags, watch_score FROM watch_scores WHERE game_id = $GAME_ID ORDER BY computed_at, id) TO STDOUT WITH (FORMAT csv, HEADER true)" \
  > "$FIXTURE_DIR/watch_scores_5059222.csv"

echo "픽스처 추출 완료: game_id=$GAME_ID"
echo "저장 위치: $FIXTURE_DIR"
echo "로더 계약에 따라 경기 CSV 파일명은 5059222로 고정됩니다."
