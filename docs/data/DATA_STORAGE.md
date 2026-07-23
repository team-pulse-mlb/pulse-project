# 데이터 저장 정책

## 1. 저장 기준

```text
PostgreSQL(RDS) = 오래 남길 원본과 이력 — 잃으면 재생성 불가
Redis = 지금 화면에 필요한 최신 상태 — 잃어도 재계산 가능
```

### PostgreSQL (RDS)

| 테이블 | 성격 | 저장 내용 |
|---|---|---|
| `games` | 최신 스냅샷 | 경기 상태, 이닝, 점수, `pregame_score`와 계산 시점 입력(`pregame_inputs`), `peak_base_score`, 종료 헤드라인 |
| `plays` | append 로그 | 새 play 이벤트와 최초 관측 시각 |
| `game_events` | append 로그 | 라이브 중 추출한 흥미 순간 이벤트(보호/공개 등급 포함). 종료 경기 타임라인에 그대로 재사용 |
| `odds_snapshots` | 경기 전 스냅샷 | 벤더별 배당. `FIRST_SEEN`·`PREGAME_FINAL` 두 개만, 시작 전 관측만 허용 |
| `watch_scores` | append 로그 | `base_score`, `watch_score`, 신호별 기여, 추천 태그 |
| `users` · `refresh_tokens` | 계정 | 인증 정보, 리프레시 토큰 상태 |
| `user_settings` · `user_favorite_teams` · `user_favorite_players` | 사용자 설정 | 알림·전환 설정, 관심 팀·선수 |
| `notification_events` · `user_notifications` | 알림 | 전역 이벤트 원본 1행 + 사용자별 수신함(읽음 상태, 7일 보관) |

### Redis

| 데이터 | 내용 |
|---|---|
| 라이브 조회 | 진행 중 경기 랭킹과 현재 상태 캐시 |
| 단기 상태 | 알림 히스테리시스·쿨다운·전역 발화 한도·전환 안내 쿨다운 |
| pub/sub | 랭킹·경기·알림 재조회 신호 채널 |

구체적인 키 이름, 타입, 라이프사이클은 [REDIS_KEYS.md](REDIS_KEYS.md)를 따른다.

## 2. 원천별 저장 정책

| 원천 | 저장 위치 | 방식 |
|---|---|---|
| `/games` | `games` | 경기당 1행 upsert(최신 스냅샷). 관측 시각·증분 커서 포함 |
| `/plays` | `plays` | append 로그. 전 필드 + 폴러 최초 관측 시각(`observed_at`) + PA 유래 주자 상태(`runner_on_*`) |
| `/plate_appearances` | S3 원본 아카이브 + `game_events` 발췌 | 원본은 DB 비영속, 운영 poller가 수신 응답을 S3에 업로드해 보존. 라이브 추출 이벤트(긴 승부·강한 타구·투수 흔들림)만 `game_events`에 영속 |
| `/lineups` | `lineups` | 발췌 저장(내장 선수 객체 제외) |
| `/odds` | `odds_snapshots` | 경기 전 스냅샷 2종(`FIRST_SEEN`·`PREGAME_FINAL`)만 |
| `/standings` | `standings` | 일 배치 스냅샷(시점별 보존) |
| `/season_stats` | `player_season_stats` + `games.pregame_inputs` | 캐시는 최신 덮어쓰기. `pregame_score` 계산에 쓴 시점 값은 `pregame_inputs`에 불변 고정 |
| `/teams` · `/players` | `teams` · `players` | 마스터 upsert |

자체 계산 산출물(`watch_scores`·`game_events`·종료 경기 AI 헤드라인)은 외부에 존재하지 않는 데이터이므로 전부 DB에 영속한다.

## 3. 저장하지 않는 데이터

| 데이터 | 사유 |
|---|---|
| 경기 전 배당 라인 무브먼트(두 스냅샷 사이 갱신분) | 접전 기대 산출에는 두 스냅샷으로 충분 |
| 라이브 배당 궤적 | 스포일러 프리 원칙상 사용 금지 |
| `/odds/player_props` | 기능 범위 밖이라 저장하지 않는다. 종료 후 원본이 줄거나 사라질 수 있다 |
| `/stats` (경기별 선수 스탯) | 과거 경기 재조회 가능. 종료 후 재분석을 하지 않음 |
| 외부 API `scoring_summary` | 과거 경기 재조회 가능. 공개 상세의 득점 play 목록은 저장된 `plays`에서 파생한다. |
| `/teams/season_stats` | 점수 계산에 쓰지 않아 저장하지 않는다. 경기 당시 시점값은 보존하지 않는다 |
| `/player_injuries` | 실시간 표시·알림 억제 용도만. 이력 미보존 |
| splits/versus · pitch type stats 계열 | 미사용(문구 고도화 단계 소재) |
| PA 관측 시각 | 시간 감쇠 계산은 `plays.observed_at`을 사용. 원본에 시각 자체가 없어 재조회로도 복구 불가 |

## 4. 라이브 시점에 저장하지 않으면 유실되는 데이터

**영구 유실** — 외부 API 재조회로 복구할 수 없다. 라이브 관측 시점의 저장이 유일한 확보 수단이다.

| 데이터 | 유실 원인 | 대응 |
|---|---|---|
| plays·PA 관측 시각 | 원본에 벽시계 타임스탬프 없음 | `plays.observed_at` 저장. PA 관측 시각은 별도 저장하지 않는다 |
| 경기 전 배당 | `/odds` 행이 라이브 라인으로 계속 덮어써짐. 과거 조회는 종료 무렵 라인만 반환 | `odds_snapshots` 2종 저장. 그 외 갱신분은 보존하지 않는다 |
| 선수 props | 종료 후 대부분 사라짐(794행 → 59행) | 저장하지 않는다 |
| 순위 시점값 | `/standings`는 현재 순위만 제공 | `standings` 일 스냅샷 저장 |
| 시즌 스탯 시점값 | 선수·팀 모두 현재 누적치만 제공, 경기 당시 값 복원 불가 | 계산 사용분만 `games.pregame_inputs`에 고정. 팀 시즌 스탯의 당시 값은 보존하지 않는다 |
| 부상자 명단 시점값 | 현재 목록만 제공 | 저장하지 않는다 |

**조건부 복구 가능** — 내용은 과거 경기 재조회로 복구되나, 벤더 보존 정책과 플랜 유지에 의존한다.

| 데이터 | 자체 보존 수단 |
|---|---|
| `/games`·`/plays` 원본 | DB 영속(운영 핵심) |
| `/plate_appearances` 원본 | S3 아카이브(운영 이전 후에도 유지) |
| `/stats`·외부 API `scoring_summary`·마스터·일정 | 자체 보존 없음(필요 시 외부 API 재조회에 의존). 공개 상세의 득점 play 목록은 저장된 `plays`에서 파생한다. |

## 5. S3 임시 수집과 운영 DB 이전

운영 데이터 경로는 운영 `poller` → RDS 적재 + ScoreTask 발행으로 일원화한다. S3 아카이브는 DB 이전 전까지의 개발·데이터 파악·백테스트용 **임시 수집**이며, 운영 이전이 완료되면 raw-archive 도구의 아카이빙을 중단한다. 단 `/plate_appearances` 원본은 예외로, 운영 이전 후에도 운영 `poller`가 수신한 응답을 S3에 계속 업로드해 보존한다.

```text
S3 = 개발·백테스트용 임시 원본 아카이브 (운영 이전 후 중단)
    + PA 원본 보존 계층 (운영 이전 후에도 poller가 계속 업로드)
```

PA만 예외인 이유: PA는 운영 DB에 영속하지 않는 유일한 원본이다. 다른 데이터는 DB가 자체 보존 수단이지만, PA는 이 아카이브가 없으면 `game_events` 추출 룰 변경 시 소급 재추출과 PA 신호 백테스트 확장이 외부 API의 과거 데이터 보존 정책에 의존하게 된다. 업로드 실패는 운영 경로(적재·ScoreTask)에 전파하지 않는다.

**이전 계획(임시 수집 → 운영 이전 → 중단 → 보존)**

S3 수집분은 Flyway 베이스라인 V1 적용 후 운영 스키마로 이전하고, `source`로 운영 수집분과 구분한다. DB 이전과 운영 `poller` 정상 동작 확인이 끝나면 raw-archive의 S3 아카이빙을 중단하고(PA 예외는 위와 같이 poller 업로드로 대체), 이전한 데이터는 폐기하지 않고 운영 DB에 영속한다. 세부 실행 절차와 일정 기준은 소유자(예은)가 별도로 관리한다.

- **이전 경계 시각:** `2026-07-08T12:53:55.001273Z`(KST `2026-07-08 21:53:55.001273`). 운영 DB의 이전 대상 테이블에서 `source=OPERATIONAL`로 기록된 최초 관측 시각을 기준으로 한다. 장애 복구 등으로 경계 이후 시각의 S3 이전분이 존재할 수 있으므로 개별 행의 최종 출처는 `source`로 판별한다.

| 구분 | 용도 |
|---|---|
| 라이브 아카이브 | 진행 중 경기를 원본 응답 그대로 저장한다. `observed_at`은 실제 관측 시각으로 사용한다. 이전 시 `source=S3_LIVE_ARCHIVE`로 표기한다. |
| 백필 데이터 | 과거 경기 분석용이다. `backfilled: true`로 표시하고 시간 기반 계산에는 사용하지 않는다. 이전 시 `source=S3_BACKFILL`로 표기한다. |
| 백테스트 | 이전 후 운영 DB 이력으로 `scoring.yml`을 튜닝한다. 가중치 변경 시 영향 리포트를 생성한다. |
