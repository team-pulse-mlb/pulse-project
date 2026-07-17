#!/usr/bin/env bash

# 발표용 데모 시뮬레이터의 정적 입력(games+plays)만 멱등하게 적재한다.
#
# 전제 조건:
# - 저장소 루트에 `.env`가 있어야 한다.
# - `pulse-postgres`가 실행 중이어야 한다.
# - 백엔드를 한 번 실행해 JPA 엔티티 기준 로컬 스키마를 먼저 생성한다.
#
# 작업 내용:
# - 팀·선수와 시뮬레이터 원본 경기(games+plays)를 PostgreSQL에 재적재한다.
# - 원본 경기는 과거 종료 상태로 적재해 오늘 슬레이트에 직접 노출되지 않는다.
# - 데모 로그인용 사용자·설정·즐겨찾기를 재구성한다.
#
# 예정·진행·종료 카드와 점수·문구·알림은 이 스크립트가 아니라 시뮬레이션 poller가
# 만든다. 시뮬레이터는 `pulse.simulation.games`로 원본→target 경기를 연출한다.
#   단일 경기:   bash backend/scripts/run-simulation.sh
#   다중 경기:   application.yml의 pulse.simulation.games 예시를 채운 뒤 bootRun
#
# 사용법:
#   bash backend/scripts/seed-dev-slate.sh

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

for fixture_file in \
  teams.csv \
  players.csv \
  game_5059222.csv \
  plays_5059222.csv \
  load-fixtures.sql \
  supplement.sql \
  notifications.sql; do
  if [[ ! -f "$FIXTURE_DIR/$fixture_file" ]]; then
    echo "픽스처 파일이 없습니다: $FIXTURE_DIR/$fixture_file"
    exit 1
  fi
done

# Git Bash(MSYS)는 컨테이너 내부 절대경로 인자를 호스트 경로로 변환하므로
# 컨테이너 내부 경로를 쓰는 호출만 변환을 끈다.
MSYS_NO_PATHCONV=1 docker exec -i pulse-postgres sh -lc "rm -rf /tmp/pulse-fixtures && mkdir -p /tmp/pulse-fixtures"

# docker cp는 Git Bash에서 절대 호스트 경로(/c/...)와 컨테이너 경로(/tmp/...)가 함께 있을 때
# 경로 변환이 꼬일 수 있으므로 저장소 루트 기준 상대 경로로 복사한다.
pushd "$PROJECT_DIR" >/dev/null
MSYS_NO_PATHCONV=1 docker cp "backend/scripts/fixtures/." "pulse-postgres:/tmp/pulse-fixtures/" >/dev/null
popd >/dev/null

# psql -f 경로도 MSYS가 변환하므로 SQL은 stdin으로 넘긴다. CSV는 컨테이너 /tmp/pulse-fixtures에서 \copy로 읽는다.
for sql_file in load-fixtures.sql supplement.sql notifications.sql; do
  docker exec -i pulse-postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
    < "$FIXTURE_DIR/$sql_file"
done

docker exec -i pulse-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "
    SELECT game_id, status, start_time,
           (SELECT count(*) FROM plays WHERE plays.game_id = games.game_id) AS plays
    FROM games
    WHERE game_id IN (8800000004, 8800000006)
    ORDER BY game_id;"

echo
echo "시뮬레이터 원본 games+plays 적재가 완료됐습니다."
echo "예정·진행·종료 카드는 시뮬레이션 poller로 연출하세요(run-simulation.sh 또는 pulse.simulation.games)."
