# PULSE 원본 데이터 아카이브

balldontlie MLB API 원본 응답을 수집 시각(`observed_at`)과 함께 S3에 그대로 적재하는 Lambda.
plays/plate_appearances에는 벽시계 타임스탬프가 없어서, 나중에 시간 감쇠 신호를 복원하려면
수집 시각을 지금부터 같이 저장해둬야 한다. 스키마 확정 전이므로 DB 없이 원본 JSON만 쌓고,
스코어링 로직이 정해지면 S3 → Postgres 백필한다.

폴링 설계는 노션 "외부 데이터 API 명세 및 활용 계획" 7장을 따르되, 스코어러(랭킹)가 아직
없는 수집 전용 환경에 맞게 조정했다.

## 현재 저장 방식과 확인 위치

지금은 추천 점수까지 저장하지 않고, balldontlie API 원본 응답을 수집 시각(`observed_at`)과
함께 S3에 먼저 쌓는다. 이 데이터는 "그 시점에 API가 어떻게 보였는지"를 보존하기 위한
raw archive다. 이후 스코어러가 추천 점수와 노출 결과를 같이 저장하게 되면, 과거 시즌
데이터로 돌리던 시뮬레이터는 S3에 쌓인 실시간 raw 데이터와 당시 추천 점수를 시간순으로
리플레이하는 방식으로 대체한다.

배포 스크립트가 AWS 계정 ID로 버킷명을 만든다:

```
s3://pulse-raw-<account-id>/
```

확인은 보통 세 군데에서 한다:

- S3 `raw/`: 실제 수집된 gzip JSON 객체. endpoint별로 prefix가 나뉜다.
- S3 `state/collector_state.json`: 라이브 수집기가 마지막 cursor, dedupe hash, 백필 여부를 저장하는 내부 상태.
- CloudWatch Logs `/aws/lambda/pulse-collector`: 매 분 실행 결과와 에러 로그.

예시:

```powershell
# 최근 수집 객체 확인
aws s3 ls s3://pulse-raw-<account-id>/raw/ --recursive | Select-Object -Last 20

# 특정 endpoint만 확인
aws s3 ls s3://pulse-raw-<account-id>/raw/games/ --recursive | Select-Object -Last 20
aws s3 ls s3://pulse-raw-<account-id>/raw/plays/ --recursive | Select-Object -Last 20

# collector 상태 확인
aws s3 cp s3://pulse-raw-<account-id>/state/collector_state.json -

# Lambda 로그 확인
aws logs tail /aws/lambda/pulse-collector --since 10m
```

## 동작 (EventBridge 1분 주기 + 라이브 중 ~10초 서브 폴링)

| 상태 | 수집 | 주기 |
|---|---|---|
| 상시 | `/games?dates[]=어제,오늘,내일(UTC)` 1콜 — 슬레이트 감시+라이브 커버 | 1분 (라이브 중 ~10초) |
| 상시 | `/odds?dates[]=오늘` — pregame 배치 갱신(~15-20분)과 라이브 라인 전부 포착 | 1분, 해시 dedupe |
| PREGAME | T-36h 이내 경기 `/lineups?game_ids[]` 1콜 — 선발 투수 등장·타순 공개 시점이 그대로 기록됨 (문서 8장 체크리스트 항목) | 1분, 해시 dedupe |
| LIVE | 경기별 `/plays` 커서(마지막 order) 증분 | ~10초 |
| LIVE | 경기별 `/plate_appearances` (단일 페이지, 커서 없음) | ~10초(매 서브폴 라운드, `PA_ROUND_STRIDE`로 조정), 해시 dedupe |
| SUSPENDED | 새 play 없이 10분+ → plays 5분으로 강등, `/games`로 재개 감지 | 5분 |
| FINAL_BACKFILL | 종료 감지 시 1회: plays 전체 재수집 + PA 최종본(`backfilled: true` 표시) + `/stats` | 1회 |
| 일 배치 | `/standings`, `/teams/season_stats`, `/player_injuries` | UTC 09시 이후 1회 |

계획과 다른 점 (의도적):
- lineups/odds를 계획(15분~1시간)보다 촘촘히 1분 간격으로 확인 — 해시 dedupe라 저장은
  변경 시에만 발생하고, 등장 시점 실측이 공짜로 얻어진다.
- plays는 커서 증분이라 주기가 길어도 데이터 유실은 없다. 주기는 `observed_at` 해상도만
  결정한다(라이브 중 ~10초, `SUBPOLL_INTERVAL`로 조정, 0이면 서브 폴링 끔).
- 경기별 `/plays`, `/plate_appearances` 호출은 `LIVE_GAME_WORKERS`(기본 8)로 병렬 실행한다.
  호출량은 같고 라운드 지연만 줄어 15경기 동시 진행 때도 10초 서브 폴링을 유지한다.
- PA는 커서가 없어 매번 전체 재조회라 주기를 당기는 만큼 실제 지연이 줄어든다. 매 서브폴
  라운드마다 폴링해 ~10초 주기를 낸다(`PA_ROUND_STRIDE`, 기본 1 = 매 라운드).
- (2026-07-04) 실측 피크가 한도의 ~20%로 여유가 있어 plays 20초→15초, PA 1분→30초로
  상향. 목표 상한은 25~30%(150~180 req/min) — 자체 토큰버킷 상한(300 req/min, 50%)은
  손대지 않고 백필·429 재시도용 여유로 남겨둔다(문서 2·7.4장 참고).
- (2026-07-03 확정 지시) 진행 중 경기 원본을 최대 해상도로 적재하기 위해 PA를 30초→매
  라운드로 재단축. S3는 개발용 원본 임시 저장소이며 운영 적재(PostgreSQL)와
  분리된다 — 운영 흐름은 노션 "데이터 수집·계산·저장 흐름" 문서 기준.

호출량: 평시 3콜/분, 피크(라이브 15경기, PA 매 라운드 포함) 실행당 ~30콜 × 라운드 수로
분당 ~150 req/min, 목표 상한 25~30%(150~180 req/min) 이내. 429는 2초 후 1회 재시도.

## S3 레이아웃 (`pulse-raw-<account-id>`)

```
raw/games/dt=YYYY-MM-DD/games_HHMMSSZ.json.gz
raw/odds/dt=YYYY-MM-DD/odds_HHMMSSZ.json.gz
raw/lineups/dt=YYYY-MM-DD/lineups_HHMMSSZ.json.gz
raw/plays/game_id=<id>/plays_YYYY-MM-DD_HHMMSSZ_c<cursor>.json.gz
raw/plate_appearances/game_id=<id>/pa_YYYY-MM-DD_HHMMSSZ.json.gz
raw/stats/game_id=<id>/stats_....json.gz               # 종료 후 1회
raw/backfill/plays/game_id=<id>/plays_p<n>.json.gz     # backfilled=true
raw/backfill/plate_appearances/game_id=<id>/pa_final.json.gz
raw/standings/dt=.../  raw/teams_season_stats/dt=.../  raw/player_injuries/dt=.../
raw/historical/season=YYYY/...                          # 과거 시즌 (아래 백필 참조)
state/collector_state.json   # 커서·dedupe 해시·백필 여부 (3일 윈도우 밖 경기는 자동 정리)
```

모든 raw 객체는 gzip 압축(`ContentEncoding: gzip`, JSON 대비 ~8-10배 절감). 읽을 때는
`gzip.decompress` 필요. gzip 적용(2026-07-02) 이전에 쌓였던 비압축 `.json`은 전부 `.json.gz`로
일괄 재압축했으므로 raw/ 아래는 gzip 단일 포맷이다. state 파일은 작고 계속 덮어써서
압축하지 않는다.

각 객체는 `{"observed_at", "endpoint", "params", "response"}` 래퍼 — `response`가 API 원본
그대로. 백필 산출물은 `"backfilled": true`가 추가돼 시간 감쇠 계산에서 제외할 수 있다
(문서 7.6 규칙).

## 과거 시즌 백필 (`backfill.py`)

일회성 로컬 스크립트. 과거 시즌 원본을 시즌·경기 단위 gzip 번들로 적재한다:

```
raw/historical/season=YYYY/games.json.gz               # /games 전 페이지 (정규+포스트시즌)
raw/historical/season=YYYY/standings.json.gz
raw/historical/season=YYYY/team_season_stats.json.gz
raw/historical/season=YYYY/games/game_id=<id>.json.gz  # 경기당 1번들: plays 전 페이지
                                                       #  + plate_appearances + stats
```

```powershell
$env:BDL_API_KEY = "<key>"
python raw-archive\backfill\backfill.py --bucket pulse-raw-<account-id> --seasons 2023 2024 2025
```

- **전부 `backfilled: true`** — plays/PA에 벽시계 시간이 없어 `observed_at`은 수집 시점일
  뿐이다. 시간 감쇠 재구성에는 절대 못 쓰고, 결과 기반 백테스트(흥미도 라벨링, 스코어링
  검증) 전용.
- 라이브 collector와 달리 경기당 1번들로 묶음 — PUT 수 ~6분의 1, 압축 효율↑. (라이브는
  `observed_at` 해상도 때문에 응답별 객체가 필요하지만 백필은 아니다.)
- 기본 300 req/min 스로틀(`--rpm`) — 600 한도를 라이브 collector(~110 피크)와 공유.
  재실행 시 이미 적재된 경기는 건너뛰므로 중단해도 안전.
- 시즌당 gzip 후 ~200MB, API ~1.5만 콜(300rpm으로 ~1시간). 게임이 0건인 시즌은 API 커버리지
  밖으로 보고 건너뛴다 — 몇 년치까지 제공되는지 `--seasons`로 탐색하면 된다.

## 배포

사전 조건: AWS CLI 설치 + `aws configure` 완료.

```powershell
.\raw-archive\deploy\deploy-collector.ps1 -ApiKey "<balldontlie API key>"
```

리전 기본값 ap-northeast-2. 스크립트는 멱등 — 코드 수정 후 다시 실행하면 업데이트된다.

## 비용

Lambda 1분 주기(월 4.3만 회, 라이브 중 ~50초 실행)는 프리 티어 안. S3 PUT은 dedupe 덕에
라이브 시간대 위주로 발생 → 월 $1 미만. 스토리지는 gzip이라 라이브 수집 월 수십 MB +
과거 시즌당 ~200MB — 25시즌을 담아도 5GB 안이다.

## 확인/중지

```powershell
# 수집 확인
aws s3 ls s3://pulse-raw-<account-id>/raw/ --recursive | Select-Object -Last 20
# 로그
aws logs tail /aws/lambda/pulse-collector --since 10m
# 일시 중지 / 재개
aws events disable-rule --name pulse-collector-every-minute
aws events enable-rule --name pulse-collector-every-minute
```
