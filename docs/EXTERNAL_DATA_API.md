# 외부 데이터 API 명세

> **실측 기준일: 2026-07-02~05 (2026 시즌 중).** GOAT 플랜 키로 전체 엔드포인트를 실제 호출해 확인한 결과를 "실측"으로 표기했다.

이 문서는 설계 문서가 아니라 balldontlie MLB API의 응답을 실제 호출로 확인한 데이터 레퍼런스다. 폴링 계획·계산 흐름 같은 설계 내용은 [ARCHITECTURE_AND_DATA_FLOW.md](ARCHITECTURE_AND_DATA_FLOW.md)를 따른다.

## 1. 문서 목적

이 문서는 balldontlie MLB API의 호출 제한, 엔드포인트별 실제 제공 데이터, 데이터 등장 시점(리드타임)을 정리한다.

참고 스펙:

```text
https://www.balldontlie.io/openapi/mlb.yml
```

## 2. 호출 제한

GOAT 플랜 기준 호출 한도는 `600 req/min`(평균 10 req/sec)이다.

- 인증: 모든 요청에 `Authorization: Bearer {API_KEY}` 헤더가 필요하다.
- 응답 헤더 `x-ratelimit-limit`(600), `x-ratelimit-remaining`, `x-ratelimit-reset`(epoch 초)으로 잔여 호출량을 실시간 확인할 수 있다.
- 한도 초과 시 `429 Too Many Requests` + 평문 body(`"Too many requests, please try again later."`)를 반환하며, **응답에 `Retry-After` 헤더(정수 초, 다음 리셋까지 남은 시간 = `x-ratelimit-reset`과 일치)가 포함된다**. 폴러 백오프는 이 값을 그대로 신뢰해 대기하면 된다. 리셋은 1분 단위 창(window)으로, 초과분만 429가 되고 창이 넘어가면 예산이 리필된다.
- `per_page` 최대값은 100이다. 초과하면 400 에러를 반환한다.


## 3. 요청 파라미터와 응답의 공통 특성

- 날짜 필터 형식: `dates[]=YYYY-MM-DD`. 복수 지정 가능.
  - UTC 날짜 기준이다. 미국 기준 하루 슬레이트가 UTC 이틀에 걸치므로(미국 저녁 경기는 UTC 다음날 00~03시 시작), 진행 경기를 빠짐없이 잡으려면 `dates[]=어제&dates[]=오늘`(UTC) 두 날짜를 항상 한 요청으로 함께 조회해야 한다. 복수 날짜를 한 요청에 담을 수 있다.
- 페이지네이션: cursor 방식(`meta.next_cursor`), `per_page` 최대 100.
- `/plays`의 `next_cursor`는 마지막 play의 `order` 값과 같다. 마지막으로 저장한 `order`를 cursor로 넘기면 **새 play만 증분 수집**할 수 있다.
- 경기 상태값(2026 시즌 분포): `STATUS_SCHEDULED`, `STATUS_IN_PROGRESS`, `STATUS_FINAL`(1,188건) 외에 `STATUS_POSTPONED`(9건), `STATUS_CANCELED`(3건)가 실제로 존재한다. 폴러 상태 머신이 반드시 처리해야 한다.
- `/plays`, `/plate_appearances`에는 **벽시계 타임스탬프가 없다**. "최근 득점 후 경과 시간" 같은 시간 기반 신호는 폴러가 최초 관측 시각(`observed_at`)을 저장해 계산해야 하며, 경과 시간의 정밀도 하한은 폴링 주기가 된다.
- `/lineups`, `/stats`, `/player_injuries` 등은 player 객체(팀 정보 포함)를 통째로 내장해 응답이 크다. 저장 시 필요한 필드만 추출한다.

## 4. 데이터 등장 시점 요약

| 데이터 | 등장·갱신 시점 |
|---|---|
| 경기 일정 | 최소 7일 뒤 날짜까지 제공. 미래 경기도 `date`에 실제 시작 시각(UTC ISO 8601)을 포함(T+3~T+6 확인). 시작 시각은 확정 전 변동 가능 |
| 선발 예상 투수 (`/lineups`) | 경기 36시간 전에도 제공 (내일 저녁 경기 6개 중 5개는 양팀, 1개는 한 팀만 확정) |
| 타순 (`batting_order`) | 종료 경기에는 팀당 타순 9명+선발 투수로 총 20행 제공. **공개 시점: 경기 시작 T-2.2h~T-4.4h, 중앙값 ≈ T-3.2h** |
| 경기 배당 (`/odds`) | 미국 기준 **당일 슬레이트만** 제공: 시작 13~15시간 전 경기에는 있고, 33시간 전 경기에는 없음. 벤더 6곳(fanduel, draftkings, betmgm, caesars, betrivers, fanatics) |
| 배당 갱신 주기 | 경기 전: 약 15~20분 간격 전 경기 일괄 배치 갱신(10:51Z → 11:07Z). 경기 중: 종료 시각까지 계속 갱신 = 라이브 배당(종료 경기의 `updated_at`이 경기 종료 무렵). **경기 전 접전도는 시작 직전 스냅샷을 자체 저장해야 함** (그 후에는 라이브 라인으로 덮임) |
| 선수 props (`/odds/player_props`) | 당일 공개. 경기당 700~800행(벤더 5~6곳, prop 19종). 종료 후에는 일부 유형만 잔존(59행) |
| 오프닝 배당 (`/odds/opening`) | **2026 시즌 정규 날짜는 빈 응답**(개막 시리즈 3/26~27과 2025 이전 시즌만 데이터 존재) → 사용 불가, `/odds` 첫 관측 스냅샷으로 대체 |
| plays | 경기당 약 550행(547). 종료 직후 전체 조회 가능. cursor 증분 수집 가능 |
| plate_appearances | 경기당 약 74타석·275투구. 단일 페이지 응답, 증분 커서 없음 → 전체 재조회 후 `pa_number`로 dedupe |
| 과거 데이터 보존 | odds 포함 4월 중순 과거 날짜도 전 경기 조회 확인. plays/PA/stats도 과거 경기 제공 → 백필·가중치 백테스트 가능 |

## 5. 엔드포인트 요약

| 엔드포인트 | 주요 제공 데이터 | 활용 |
|---|---|---|
| `/mlb/v1/games` | 경기 목록, 팀, 상태, 이닝(period), 점수, 이닝별 점수, scoring summary | 상태 머신 구동, 기본 신호(후반/점수차/초반 난타), 점수 계산 트리거 |
| `/mlb/v1/games/{id}` | 특정 경기 상세(목록과 동일 구조) | 정합성 보정용 단건 조회 |
| `/mlb/v1/plays` | play 이벤트, 점수, 카운트, 구종·구속, 타구 좌표 | 최근 득점·리드 변경·빅이닝·카운트 신호, 다시보기 구간 재생 |
| `/mlb/v1/plate_appearances` | 타석 결과, 주자 상황, pitch 단위 Statcast 전체 | 상세 신호(득점권·투수 흔들림·강한 타구), 태그·알림 판단 |
| `/mlb/v1/lineups` | 타순, 포지션, probable pitcher | pregame_score 선발 매치업, 관심 선수 선발 여부, 타순 확정 감지 |
| `/mlb/v1/stats` | 경기별 선수 타격·투구·수비 통합 스탯 | 종료 경기 분석(C층), 다시보기 구간 신뢰도 보강 |
| `/mlb/v1/season_stats` | 선수 시즌 누적 스탯 (WAR 포함) | 선발 매치업 강도(ERA/WAR), 스타 선수 가산 |
| `/mlb/v1/teams/season_stats` | 팀 시즌 누적 스탯 (QS, CG, SHO 포함) | 경기 성격 분류(난타전/투수전) |
| `/mlb/v1/standings` | 순위, 승패, 승률, games behind, 매직넘버, 최근 10경기 등 | 경기 중요도 보정(×0.9~×1.15), pregame_score |
| `/mlb/v1/players` (+`/active`) | 선수 정보, 포지션, 팀, 신체·드래프트 정보 | 선수 마스터 적재, 관심 선수 검색·등록 |
| `/mlb/v1/teams` | 팀 정보, 리그, 디비전 | 팀 마스터 적재 |
| `/mlb/v1/player_injuries` | 부상자 명단, 복귀 예정일, 코멘트 (문서 추가: 스펙에서 신규 발견) | 관심 선수 결장 파악, 알림 억제, 선발 변경 보조 |
| `/mlb/v1/players/splits` | 상황별 선수 성적(구장/타순/카운트/상대 등 9개 카테고리) | 고급 매치업 문구 소재 |
| `/mlb/v1/players/versus` | 타자 vs 상대팀 개별 투수 전적 | 관심 선수 매치업 표시 |
| pitch type stats 계열 (4종) | 구종별 투수/타자 성적 (경기/시즌 단위) | 구종 매치업(고급 단계) |
| `/mlb/v1/odds` | 경기 배당: 벤더 6곳의 spread/moneyline/total, updated_at | 시장 기대 접전도 → pregame_score 보조 (내부 전용, UI 노출 금지) |
| `/mlb/v1/odds/player_props` | 선수 props (19종, 벤더별 라인) | 관심 선수 주목도 보조(선택 기능) |
| `/mlb/v1/odds/opening` (+props) | historical opening odds (`opened_at`) | **2026 시즌 미제공 → 사용 안 함.** `/odds` 첫 관측 스냅샷으로 대체 |

## 6. 엔드포인트별 상세

### `/mlb/v1/games` — 경기 목록·상태

**동작 특성**

- `dates[]`는 UTC 날짜 기준. 진행 경기 커버는 `dates[]=어제&dates[]=오늘` 두 날짜를 한 요청으로 조회(3장 참고).
- 일정은 최소 7일 뒤까지 제공. `seasons[]`, `postseason`, `season_type` 필터 지원.
- `games/{id}` 단건 응답은 목록과 동일 구조(`scoring_summary` 포함) — 평상시에는 목록 조회로 충분.
- `clock`/`display_clock`은 야구에서는 항상 `0`/`"0:00"` — 사용하지 않는다.

**필드**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `id` | 경기 식별자 | `5059041` | 모든 하위 수집(plays/PA/lineups)의 키 |
| `season`, `season_type`, `postseason` | 시즌 연도/타입/포스트시즌 여부 | `2026`, `"regular"`, `false` | 시즌 필터, 경기 중요도 보정 |
| `date` | 경기 시작 시각 (UTC ISO 8601) | `"2026-06-30T00:05:00.000Z"` | 경기 생명주기 단계 전환 기준(T-36h/T-6h/시작) |
| `status` | 경기 상태 | `"STATUS_FINAL"`, `"STATUS_IN_PROGRESS"`, `"STATUS_SCHEDULED"`, `"STATUS_POSTPONED"`, `"STATUS_CANCELED"` | 폴러 상태 머신 구동 |
| `period` | 현재 이닝 | `9` | 후반/연장 가산 (+10/+20/+30) |
| `clock`, `display_clock` | 이닝 내 시간 | `0`, `"0:00"` (항상 0) | 미사용 |
| `home_team_name`, `away_team_name` | 팀 이름 문자열 | `"Chicago Cubs"` | 표시용 |
| `home_team`, `away_team` | 팀 객체 (id, abbreviation, league, division 등) | `{"id": 5, "abbreviation": "CHC", ...}` | 관심 팀 매칭, 표시용 |
| `home_team_data.runs`, `away_team_data.runs` | 팀별 득점 | `3`, `2` | 점수 차 가산 (내부 전용, 보호 모드 노출 금지) |
| `home_team_data.hits` / `.errors` | 팀별 안타/실책 | `10`, `0` | 경기 성격 참고(선택) |
| `home_team_data.inning_scores` | 이닝별 득점 배열 | `[0, 0, 0, 1, 1, 0, 0, 0, 1]` | 초반 난타 신호, 빅이닝 검증 |
| `venue`, `attendance` | 경기장, 관중 수 | `"Wrigley Field"`, `37607` | 표시용(스포일러 아님) |
| `conference_play` | 컨퍼런스 경기 여부 | `false` | 미사용 |
| `scoring_summary` | 득점 이벤트 배열 (play 텍스트, inning, period) | `[{"play": "Tatis Jr. grounded...", "inning": "top", ...}]` | 다시보기 구간 검증 보조 (공개 모드 전용 — 결과 텍스트 포함) |

### `/mlb/v1/plays` — play 이벤트 스트림

**동작 특성**

- 경기당 약 550행(547). `per_page` 100 기준 전체 6페이지.
- `next_cursor` = 마지막 play의 `order` → **저장한 마지막 order를 cursor로 넘기면 증분 수집 1콜로 끝**.
- **벽시계 타임스탬프 없음** → 시간 감쇠 신호는 폴러의 `observed_at` 기준으로 계산.
- 필터는 `game_id` 단수만 지원 — 경기당 1콜 필요.
- 진행 중 경기에서도 폴링 주기(~20초)마다 새 play가 연속 유입된다.

**필드**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `game_id` | 경기 식별자 | `5059041` | 경기 연결 키 |
| `order` | 경기 내 play 순서 (large integer) | `217414761` | 증분 커서, replay 구간 범위 저장 |
| `type` | play 이벤트 타입 | `"Start Inning"`, `"Strike Looking"`, `"Ball"`, `"Play Result"`, `"Fly Out"`, `"End Inning"` 등 | 이벤트 분류, 이닝 경계 감지 |
| `inning`, `inning_type` | 이닝 번호, 초/말/중간 | `1`, `"Top"`/`"Bottom"`/`"Mid"` | 빅이닝 신호, 후반 판정 보조 |
| `text` | 사람이 읽을 수 있는 play 설명 | `"Tatis Jr. struck out swinging."` | AI 문구 소재 (스포일러 검수 게이트 통과 필수) |
| `home_score`, `away_score` | play 후 점수 | `0`, `0` | 리드 변경 감지 (+15) |
| `scoring_play`, `score_value` | 득점 play 여부, 득점 수 | `true`, `1`~`3` (그 외 `null`) | 최근 득점 가산 (득점당 +10, 최대 +25) |
| `outs`, `balls`, `strikes` | 아웃/볼/스트라이크 카운트 | `2`, `1`, `3` | 카운트/아웃 가산 (풀카운트 +4, 2아웃 +4) |
| `batter_id`, `pitcher_id` | 타자/투수 ID | `492`, `713` | 관심 선수 매칭 |
| `pitch_type`, `pitch_velocity` | 구종, 구속 (mph, 정수) | `"Sweeper"`, `79` | 구속 저하 감지 보조 (정밀 값은 PA의 `release_speed`) |
| `hit_coordinate_x/y`, `trajectory` | 타구 좌표, 궤적 | `218`, `90`, `"F"`/`"P"`/`"G"`/`null` | 시각화 소재(후순위) |

### `/mlb/v1/plate_appearances` — 타석 + 투구 Statcast

**동작 특성**

- 경기당 약 74타석·275투구. **단일 페이지 응답(페이지네이션 meta 없음), 증분 커서 없음** → 전체 재조회 후 `pa_number`로 dedupe.
- 타임스탬프 없음 → `observed_at` 저장 필요.
- `pitches[]` 안에 `description` 필드도 존재 — play 텍스트와 유사한 투구 설명.

**주요 활용**

- 긴 타석 감지 (`pa_number`, `pitch_number`)
- 득점권 승부 감지 (`runner_on_*`)
- hard contact 감지 (`exit_velocity >= 95`, `is_barrel`)
- 투수 pitch count 추적 (`pitcher_pitch_count`)
- 투구 위치 분석 (`plate_x`, `plate_z`, `is_in_zone`, `is_chase`)
- 구속 저하 감지 (`release_speed` 추이)
- 배트 스피드 트래킹 (`bat_speed`)

**타석 레벨 필드**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `batter_id`, `pitcher_id` | 타자/투수 ID | `492`, `713` | 관심 선수 타석 알림 |
| `inning`, `half_inning` | 이닝과 초/말 | `1`, `"top"` | 구간 매핑 |
| `pa_number` | 경기 내 타석 순서 | `1` | dedupe 키, 긴 타석 감지 |
| `outs` | 타석 시작 시 아웃 카운트 | `1` | 압박 상황 판단 |
| `batter_side`, `pitcher_hand` | 타자 방향 / 투수 손 | `"R"`/`"L"` | 매치업 표시 |
| `result` | 타석 결과 | `"Strikeout"`, `"Single"`, `"Home Run"` 등 | 종료 후 분석 (라이브 보호 모드에는 노출 금지) |
| `is_ball_in_play_out` | 인플레이 아웃 여부 | `false` | 보조 |
| `runner_on_first/second/third` | 주자 상황 | `false`, `false`, `false` | **득점권 압박·만루 신호 (핵심)** |

**투구(pitch) 레벨 필드 — `pitches[]` 배열**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `pitch_number` | 타석 내 투구 순서 | `1` | 긴 승부 감지 (8구 이상) |
| `balls`, `strikes` | 볼/스트라이크 카운트 | `0`, `0` | 카운트 압박 |
| `pitch_call`, `pitch_call_code`, `call_name` | 판정 코드/상세/표시명 | `"S"`, `"called_strike"`, `"Strike"` | 휘두름/판정 분석 |
| `pitch_type_code`, `pitch_type` | 구종 코드/표시명 | `"ST"` (Sweeper), `"FF"` (4-seam) | 구종 매치업 |
| `release_speed`, `plate_speed` | 릴리스/도달 구속 (mph) | `79.9`, `73.8` | **구속 저하 감지 (투수 흔들림)** |
| `spin_rate` | 회전수 (rpm) | `2240` | 구위 분석(고급) |
| `release_extension`, `plate_time` | 익스텐션(ft), 도달 시간(초) | `5.91`, `0.47` | 고급 분석 |
| `plate_x`, `plate_z`, `strike_zone`, `strike_zone_top/bottom` | 투구 위치, 존 구역/경계 | `0.1`, `3.31`, `2`, `3.35`/`1.69` | 커맨드 분석 |
| `horizontal/vertical_movement`, `*_break`, `induced_vertical_break` | 무브먼트(ft), 브레이크(inch) | `1.24`, `0.19`, `14`/`-41`/`2` | 구위 분석(고급) |
| `release_pos_*`, `velocity_*`, `acceleration_*` | 릴리스 좌표, 속도/가속 벡터 | `1.5, 50, 5.59` 등 | 미사용(저장만) |
| `bat_speed` | 배트 스피드 (mph) | `72.7` (홈런), `75.4` (헛스윙), `null` (무스윙) | 스윙 강도 트래킹 |
| `exit_velocity`, `launch_angle`, `hit_distance` | 타구 속도(mph)/발사각(도)/거리(ft) | `96.7`, `32`, `375` | **강한 타구 신호 (`>=95` → 장타 위험 태그)** |
| `is_barrel` | barrel 여부 | `false` (96.7mph/32도 — 기준 미달) | **강한 타구 신호** |
| `expected_batting_average`, `expected_woba`, `expected_slugging` | xBA / xwOBA / xSLG | `0.28`, `0.513`~`1.839`, `0.941` | 타구 질 평가, 다시보기 신뢰도 |
| `woba_value`, `woba_denom` | wOBA 분자/분모 (실결과) | `2`, `1` (홈런) | 종료 후 분석 |
| `is_in_zone`, `is_swing`, `is_whiff`, `is_contact`, `is_chase`, `is_command` | 존/스윙/헛스윙/컨택/체이스/커맨드 여부 | `true`/`false` | 투수 지배력·타자 대응 분석 |
| `game_pitch_count`, `pitcher_pitch_count` | 경기/투수 누적 투구 수 | `1`, `1` | **투수 흔들림 신호 (선발 100구 이상)** |

### `/mlb/v1/lineups` — 라인업·선발 투수

**동작 특성**

- `game_ids[]` 필터로 조회(복수 가능).
- **선발 예상 투수는 T-36h에도 제공** — `is_probable_pitcher=true` 행 2개(팀당 1개), `batting_order`는 `null`.
- 종료 경기에는 팀당 10행(타순 1~9 + SP), 총 20행.
- **타순 공개**: 경기 시작 T-2.2h~T-4.4h(중앙값 ≈ T-3.2h) 사이에 공개.
- `player` 객체가 통째로 포함(팀 정보까지) → 응답이 큼, 필요한 필드만 저장.

**필드**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `id` | 라인업 항목 식별자 | `3833789` | dedupe |
| `game_id` | 경기 식별자 | `5058921` | 경기 연결 |
| `player` | 선수 객체 (전체 정보 포함) | `{"id": 568, "full_name": "J.P. Crawford", ...}` | 관심 선수 선발 여부 판단 |
| `team` | 팀 객체 | `{"id": 25, "abbreviation": "SEA", ...}` | 팀 구분 |
| `batting_order` | 타순 (공개 전 `null`) | `1`~`9` | 타순 확정 감지, 관심 선수 타순 표시 |
| `position` | 이 경기 포지션 (기본 포지션과 다를 수 있음) | `"3B"`, `"CF"`, `"DH"`, `"SP"` | 표시용 |
| `is_probable_pitcher` | 선발 예상 투수 여부 | `true`/`false` | **pregame_score 선발 매치업 강도의 입력** |

### `/mlb/v1/stats` — 경기별 선수 스탯

**동작 특성**

- `game_ids[]` 또는 `seasons[]` 필터. 경기당 약 29행(출전 선수 전원).
- 타격·투구·수비를 한 객체로 제공. **포지션 선수는 투구 필드가 전부 `null`, 투수는 타격 필드가 전부 `null`**.
- 주의: `avg`, `obp`, `slg`, `era`는 **시즌 누적 기준이다**(당일 2타수 0안타 선수의 `avg`가 0.215, 4타수 1안타가 0.206 — 경기 성적과 무관). 경기 단일 성적은 카운트 필드로 직접 계산할 것.
- 진행 중 경기에도 실시간 갱신된다(동일 경기를 12분 간격으로 재조회해 `at_bats`·`pitch_count`·`k` 증가 확인). 라이브 박스스코어로 활용 가능.

**타격 필드**: `at_bats`, `runs`, `hits`, `rbi`, `hr`, `bb`, `k`, `avg`, `obp`, `slg`, `doubles`, `triples`, `plate_appearances`, `total_bases`, `left_on_base`, `stolen_bases`, `caught_stealing`, `gidp`, `intentional_walks`, `hit_by_pitch`, `sac_bunts`, `sac_flies`, `fly_outs`, `ground_outs`, `line_outs`, `pop_outs`, `air_outs`

**투구 필드**: `ip`, `p_hits`, `p_runs`, `er`, `p_bb`, `p_k`, `p_hr`, `pitch_count`, `strikes`, `era`, `batters_faced`, `pitching_outs`, `wins`, `losses`, `saves`, `holds`, `blown_saves`, `games_started`, `wild_pitches`, `balks`, `pitching_hbp`, `inherited_runners`, `inherited_runners_scored`

**수비 필드**: `putouts`, `assists`, `errors`, `fielding_chances`, `fielding_pct`

**활용**

- 종료 경기 분석(C층): 다시보기 구간 신뢰도 보강, 경기 요약 문구 소재(공개 모드).
- `pitch_count`·`ip`로 선발 투수 소화 이닝 기록 → 다음 경기 pregame 참고.

### `/mlb/v1/season_stats` — 선수 시즌 누적

**동작 특성**

- 파라미터: `season`(필수), `player_ids[]`, `team_id`, `postseason`, `season_type`, `sort_by`, `sort_order`.
- 타격/투구/수비 필드가 한 객체에 통합, 해당 없는 그룹은 `null`.

**주요 필드**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `batting_war` | 타격 WAR | `4.3` | 스타 선수 판정 → 선수 중요도 가산 |
| `pitching_war` | 투구 WAR | `2.56` | **선발 매치업 강도 (pregame_score)** |
| `pitching_era`, `pitching_whip`, `pitching_k_per_9` | ERA/WHIP/K9 | `3.12` 등 | **선발 매치업 강도 (pregame_score)** |
| `batting_avg`, `batting_ops`, `batting_hr` 등 | 타격 시즌 지표 | `0.301` 등 | 선수 카드 표시, 스타 판정 |
| `fielding_dwar`, `fielding_rf` | 수비 dWAR, Range Factor | `1.14`, `3.65` | 참고용 |
| `fielding_fip` | 이름과 달리 FIP 아님 | `65.7`~`97`(에이스), `3.67`(부진 투수) | **사용 안 함 확정**(FIP 스케일 아님, 성적과 무상관) |

**투구 시즌 필드 전체**: `pitching_gp`, `pitching_gs`, `pitching_qs`, `pitching_w`, `pitching_l`, `pitching_era`, `pitching_sv`, `pitching_hld`, `pitching_ip`, `pitching_h`, `pitching_er`, `pitching_hr`, `pitching_bb`, `pitching_whip`, `pitching_k`, `pitching_k_per_9`, `pitching_war`

### `/mlb/v1/teams/season_stats` — 팀 시즌 누적

**동작 특성**: `season` 필수, `team_id` 선택. 30팀 전체 1콜 조회 가능. `pitching_oba`(피안타율) 필드도 존재.

**주요 필드**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `gp` | 경기 수 | `84` | 비율 계산 분모 |
| `batting_avg`, `batting_obp`, `batting_slg`, `batting_ops` | 팀 타격 지표 | `0.235`, `0.301` 등 | **경기 성격 분류: 난타전 성향** |
| `batting_hr`, `batting_sb`, `batting_r` | 홈런, 도루, 득점 | `109`, `66` | 공격 스타일 분류 |
| `pitching_era`, `pitching_whip`, `pitching_oba` | 팀 평균자책·WHIP·피안타율 | `4.07`, `1.304` | **경기 성격 분류: 투수전 성향** |
| `pitching_qs`, `pitching_cg`, `pitching_sho`, `pitching_sv` | QS/완투/완봉/세이브 | `35`, `1`, `9`, `26` | 불펜·선발 안정성 참고 |

### `/mlb/v1/standings` — 순위

**동작 특성**

- `season` 파라미터로 조회, 30팀 전체 1콜.
- 기존 문서에 없던 필드 다수 발견: `last_ten_games`, `playoff_percent`, `division_percent`, `wildcard_percent`, `magic_number_division`, `magic_number_wildcard`, `home_wins/losses`, `road_wins/losses`, `differential`, `division_tied` 등 — **경기 중요도 보정 재료가 문서 가정보다 풍부**.

**필드**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `team`, `league_name`, `division_name` | 팀/리그/디비전 | `"American League"` | 그룹핑 |
| `wins`, `losses`, `win_percent`, `games_played` | 승패, 승률, 경기 수 | `44`, `40`, `0.524`, `84` | 기본 전력 판단 |
| `games_behind`, `division_games_behind` | 게임차 | `0` | **경기 중요도 보정 (순위 경쟁)** |
| `playoff_seed`, `playoff_percent`, `wildcard_percent` | PS 시드, 진출 확률 | `5`, `85.3` | **경기 중요도 보정 (PS 경쟁권 판정)** |
| `magic_number_division`, `magic_number_wildcard` | 매직넘버 | `—` | 시즌 막판 중요도 부스트 |
| `streak`, `last_ten_games` | 연승/연패(음수), 최근 10경기 | `2`, `-1` | 팀 폼 반영(선택) |
| `points_for/against`, `avg_points_*`, `differential` | 득실점, 경기당 평균, 득실차 | `331`, `339`, `3.94` | 경기 성격 분류 보조 |
| `home_wins/losses`, `road_wins/losses` | 홈/원정 승패 | `—` | 참고용 |
| `clincher` | 클린치 여부 | `null` (시즌 중 전부 null — 확정 시에만 값) | 확정 팀 중요도 하향 |

### `/mlb/v1/players`, `/players/active`, `/teams` — 마스터 데이터

**동작 특성**

- `/players`: `search`(이름 부분 일치), `first_name`, `last_name` 파라미터. `/players/active`는 현역만.
- 필드: `id`, `first_name`, `last_name`, `full_name`, `debut_year`, `jersey`, `college`, `position`, `active`, `birth_place`, `dob`, `age`, `height`, `weight`, `draft`, `bats_throws`, `team`(객체).
- `/teams`: 30팀, `division`/`league` 필터. 필드: `id`, `slug`, `abbreviation`, `display_name`, `short_display_name`, `name`, `location`, `league`, `division`.

**활용**: 선수/팀 마스터 적재(관심 선수 등록·검색의 기반), 관심 선수 검색은 `search` 파라미터로 온디맨드 호출.

### `/mlb/v1/player_injuries` — 부상자 명단

**동작 특성**

- 기존 문서에 없던 엔드포인트. OpenAPI 스펙에서 발견, 확인.
- cursor 페이지네이션, 전체 리그 부상자 목록 제공.
- 필드: `player`(전체 객체), `date`(부상 발생), `return_date`(복귀 예정), `type`(부위, 예: `"Oblique"`), `detail`(예: `"Strain"`), `side`(`"Left"`), `status`(`"10-Day-IL"`), `long_comment`, `short_comment`(영문 상세 코멘트).

**활용**

- 관심 선수가 IL에 있으면: 출전 알림 대상에서 제외, "부상 중" 표시 → 불필요한 알림 방지.
- `return_date` 임박 시 "복귀 임박" 알림 소재(스포일러 아님).
- 코멘트 텍스트는 AI 문구 소재(번역·요약).

### `/mlb/v1/players/splits`, `/players/versus` — 상황별·상대 전적

**동작 특성**

- `/players/splits?player_id=&season=`: 응답이 배열이 아니라 **카테고리별 객체**: `byArena`(구장별), `byBattingOrder`(타순별), `byBreakdown`, `byCount`(카운트별), `byDayMonth`(월별), `byOpponent`(상대팀별), `byPosition`, `bySituation`(상황별), `split`(홈/원정 등). 각 항목에 타격+투구 지표 세트.
- `/players/versus?player_id=&opponent_team_id=`: 상대팀의 **개별 투수(타자)별** 통산 전적 행 목록(`opponent_player` 단위, `at_bats`, `hits`, `hr`, `avg` 등).

**활용**: 고급 단계 — 경기 상세·알림 문구의 매치업 근거("이 구장에서 강함", "이 투수 상대 통산 4할"). 랭킹 점수에는 사용하지 않음.

### pitch type stats 계열 (4종) — 구종별 성적

**동작 특성**

- `pitcher_pitch_type_game_stats`, `hitter_pitch_type_game_stats`(경기 단위, `season` 파라미터), `pitcher_pitch_type_season_stats`, `hitter_pitch_type_season_stats`(시즌 단위, `player_ids[]`).
- 필드: `pitch_type`/`pitch_name`, `pitch_count`, `pitch_usage_percent`, `zone_percent`, `chase_percent`, `command_percent`, `whiff_percent`, `contact_percent`, `called_strike_count`, `pa_count`, `hit_count`, `home_run_count`, `ba`, `slg`, `woba`, `xwoba`, `damage_count` 등 (구종당 1행).

**활용**: 고급 단계 — 구종별 매치업 분석("스위퍼 상대 whiff 40%"). 초기 범위에서는 제외, AI 문구 고도화 단계에서 도입.

### `/mlb/v1/odds` — 경기 배당

**동작 특성**

- 필터: `dates[]` 또는 `game_ids[]`. 기존 문서의 "game_id 필터는 400 에러"는 단수형 파라미터 이야기고, **복수형 `game_ids[]`는 정상 동작한다(정정)**.
- 벤더 6곳: fanduel, draftkings, betmgm, caesars, betrivers, fanatics. 경기당 벤더별 1행 = 6행.
- **당일 슬레이트만 제공**: 시작 13~15시간 전 경기에는 있고, 33시간 전 경기에는 없음. 미국 기준 그날 경기가 이른 아침(UTC 오전)에 일괄 등장.
- **갱신 주기**: 경기 전에는 약 15~20분 간격 일괄 배치 갱신(10:51Z → 11:07Z 전 경기 동시 갱신 확인). 경기 중에는 종료 시각까지 계속 갱신됨 — 즉 이 엔드포인트는 **라이브 배당**이며, 행이 계속 덮어써진다.
- 따라서 "경기 전 기대 접전도"가 필요하면 **시작 직전 값을 자체 스냅샷으로 저장**해야 한다. 과거 날짜 조회는 가능하지만 남아 있는 값은 마지막(대개 경기 종료 무렵) 라인이다.

**필드**

| 제공 데이터 | 설명 | 실제 예시 | 활용 |
|---|---|---|---|
| `id`, `game_id` | 식별자 | `265893635`, `5059041` | 경기 연결 |
| `vendor` | sportsbook 이름 | `"fanduel"`, `"draftkings"` 등 6곳 | 벤더 간 중앙값/평균으로 노이즈 제거 |
| `spread_home_value`, `spread_away_value` | 런라인 (문자열) | `"-1.5"`, `"1.5"` | 참고용 |
| `spread_home_odds`, `spread_away_odds` | 런라인 배당 (American, 정수) | `640`, `-1450` | 참고용 |
| `moneyline_home_odds`, `moneyline_away_odds` | 승리 배당 (American, 정수) | `-188`, `146` | **기대 접전도: 양 팀 암시 승률 차가 작을수록 접전 → pregame_score 가산 (내부 전용, UI 노출 금지)** |
| `total_value` | 총점 기준선 (문자열) | `"5.5"` | 기대 득점 규모 → 난타전/투수전 예상 보조 |
| `total_over_odds`, `total_under_odds` | 오버/언더 배당 | `-130`, `-102` | 참고용 |
| `updated_at` | 해당 벤더 행의 마지막 갱신 시각 (UTC) | `"2026-06-30T03:31:02.582Z"` | 신선도 판단, 스냅샷 시점 기록 |

### `/mlb/v1/odds/player_props` — 선수 props

**동작 특성**

- 필터: `game_id`, `player_id`, `prop_type`, `vendors`.
- 당일 공개, 경기당 700~800행(단일 응답, 벤더 5~6곳). **경기 종료 후에는 일부 유형만 잔존**(794행 → 59행).
- prop 19종: `hits`, `total_bases`, `home_runs`, `rbis`, `runs_scored`, `singles`, `doubles`, `triples`, `walks`, `stolen_bases`, `strikeouts`, `hits_runs_rbis`, `first_home_run`, `pitcher_strikeouts`, `pitcher_outs`, `pitcher_earned_runs`, `pitcher_hits_allowed`, `pitcher_walks`, `pitcher_record_a_win`.
- `market` 구조 2종: `over_under`(`over_odds`+`under_odds`) / `milestone`(단일 `odds`).

**활용**: 관심 선수 주목도 보조(예: 홈런 라인이 낮게 잡힌 날 = 시장이 기대하는 날) — 내부 전용, 선택 기능. 초기 범위에서는 미사용.

### `/mlb/v1/odds/opening` (+`/player_props/opening`) — 오프닝 라인

**결과: 2026 시즌에는 사실상 미제공**

- 2026 정규 시즌 날짜(4/1~7/1) 전부 빈 응답. 개막 시리즈(3/26~27)와 2025 이전 시즌만 데이터 존재.
- 필드는 `/odds`와 동일하되 `updated_at` 대신 `opened_at`.

**결론**: 현 시즌에는 사용하지 않는다. 오프닝 라인이 필요하면 `/odds`를 당일 오전 첫 등장 시점에 수집해 **첫 관측 스냅샷**으로 저장한다.

## 7. 데이터 저장 정책

엔드포인트별 데이터의 저장 위치와, 저장하지 않는 데이터의 유실 여부를 정리한다. 스키마 상세는 [DB_SCHEMA.md](DB_SCHEMA.md), 저장 기준은 [ARCHITECTURE_AND_DATA_FLOW.md](ARCHITECTURE_AND_DATA_FLOW.md) §6·§10을 따른다.

### 7.1 저장하는 데이터

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

자체 계산 산출물(`watch_scores`·`replay_segments`·`game_events`·검수 통과 AI 문구)은 외부에 존재하지 않는 데이터이므로 전부 DB에 영속한다([DB_SCHEMA.md](DB_SCHEMA.md) §A).

### 7.2 저장하지 않는 데이터

| 데이터 | 사유 |
|---|---|
| 경기 전 배당 라인 무브먼트(두 스냅샷 사이 갱신분) | 접전 기대 산출에는 두 스냅샷으로 충분 |
| 라이브 배당 궤적 | 스포일러 프리 원칙상 사용 금지 ([RECOMMENDATION_SCORE.md](RECOMMENDATION_SCORE.md) §9) |
| `/odds/player_props` | 기능 범위 밖. 종료 후 원본 소실을 수락 |
| `/stats` (경기별 선수 스탯) | 과거 경기 재조회 가능. 종료 후 재분석을 하지 않음 |
| `scoring_summary` | 과거 경기 재조회 가능 |
| `/teams/season_stats` | 점수 계산에 미사용. 시점값 소실 수락 |
| `/player_injuries` | 실시간 표시·알림 억제 용도만. 이력 미보존 |
| splits/versus · pitch type stats 계열 | 미사용(문구 고도화 단계 소재) |
| PA 관측 시각 | 시간 감쇠 계산은 `plays.observed_at`을 사용. 원본에 시각 자체가 없어 재조회로도 복구 불가 |

### 7.3 라이브 시점에 저장하지 않으면 유실되는 데이터

**영구 유실** — 외부 API 재조회로 복구할 수 없다. 라이브 관측 시점의 저장이 유일한 확보 수단이다.

| 데이터 | 유실 원인 | 대응 |
|---|---|---|
| plays·PA 관측 시각 | 원본에 벽시계 타임스탬프 없음 | `plays.observed_at` 저장. PA는 미저장 수락 |
| 경기 전 배당 | `/odds` 행이 라이브 라인으로 계속 덮어써짐. 과거 조회는 종료 무렵 라인만 반환 | `odds_snapshots` 2종 저장. 그 외 갱신분 수락 |
| 선수 props | 종료 후 대부분 소실(794행 → 59행) | 미저장 수락 |
| 순위 시점값 | `/standings`는 현재 순위만 제공 | `standings` 일 스냅샷 저장 |
| 시즌 스탯 시점값 | 선수·팀 모두 현재 누적치만 제공, 경기 당시 값 복원 불가 | 계산 사용분만 `games.pregame_inputs`에 고정. 팀 시즌 스탯은 수락 |
| 부상자 명단 시점값 | 현재 목록만 제공 | 미저장 수락 |

**조건부 복구 가능** — 내용은 과거 경기 재조회로 복구되나, 벤더 보존 정책과 플랜 유지에 의존한다.

| 데이터 | 자체 보존 수단 |
|---|---|
| `/games`·`/plays` 원본 | DB 영속(운영 핵심) |
| `/plate_appearances` 원본 | S3 아카이브(운영 이전 후에도 유지, §7.1) |
| `/stats`·`scoring_summary`·마스터·일정 | 자체 보존 없음(재조회 의존 수락) |
