# AI 문구 설계

## 1. 목적과 산출물

보호 모드 AI 문구는 서버가 만든 안전한 context만 입력으로 사용하며, 금지 표현이나 점수·승패·우세 정보를 포함하면 저장하지 않는다. 공개 모드 플레이 번역은 원문 사실을 보존하는 전용 계약을 사용한다.

AI 산출물은 종료 경기 헤드라인(`FINAL_HEADLINE`), 보호 모드 이벤트 문구(`EVENT_COPY`), 공개 모드 최근 플레이 번역(`PLAY_TRANSLATION`)이다. 헤드라인은 종료 경기 전용이며, 이벤트 문구와 플레이 번역은 라이브 중 원천 데이터 영속 직후 비동기로 생성한다. 예정 경기에는 별도 AI 문구를 생성하지 않는다.

기본 문구는 두지 않는다. 저장된 헤드라인이 없으면 API 응답의 `headline`이 `null`이고, 프론트는 헤드라인 영역을 조건부 렌더링한다. 저장된 이벤트 문구가 없으면 이벤트 API의 `copy`는 `null`이며, 프론트는 이벤트 `label`을 폴백으로 표시한다. 저장된 플레이 번역이 없으면 최근 플레이 API가 원문 `text`를 임시 폴백으로 반환한다.

| 상황 | purpose | mode | 저장 컬럼 |
|---|---|---|---|
| 종료 경기 헤드라인 · 보호 모드 | `FINAL_HEADLINE` | `PROTECTED` | `games.final_headline_protected` |
| 종료 경기 헤드라인 · 공개 모드 | `FINAL_HEADLINE` | `REVEALED` | `games.final_headline_revealed` |
| `경기 흐름` 이벤트 문구 · 보호 모드 | `EVENT_COPY` | `PROTECTED` | `game_events.copy_protected` |
| `경기 흐름` 최근 플레이 번역 · 공개 모드 | `PLAY_TRANSLATION` | `REVEALED` | `plays.text_ko` |

## 2. 생성 트리거와 흐름

HTTP 요청 경로에서 LLM을 호출하지 않는다. 문구 생성은 사용자 요청 경로가 아니라 데이터 갱신 시점에 비동기로 수행한다. Spring Boot의 ai-service 호출 8초 데드라인은 사용자 화면 요청을 기다리게 하는 시간이 아니라, 백그라운드 문구 생성 요청을 포기하는 상한이다.

- scorer가 경기 종료 정리를 수행할 때 `FINAL_HEADLINE`을 `mode=PROTECTED`·`mode=REVEALED` 두 번 요청한다. Spring Boot 기준 데드라인(8초) 내 생성+검수 통과 시 저장한다. 실패·검수 불통과·`contextHash` 불일치 시 저장하지 않는다. 두 모드 요청은 서로 독립적이라, 한쪽이 실패해도 다른 쪽 저장에는 영향을 주지 않는다. 헤드라인 요청은 마지막 play와 공개 이벤트 영속이 모두 끝난 뒤에 보낸다. 상시 재생성 경로가 없으므로, 요청 중 컨텍스트가 바뀌어 `contextHash` 불일치로 헤드라인이 영구 미저장되는 상황을 트리거 순서로 방지한다.
- `EVENT_COPY`는 이벤트 영속 직후가 아니라 **추천 점수가 급변한 순간(타임라인 하이라이트)**에 `mode=PROTECTED`로 요청한다. 이벤트 태그마다 문구를 만들면 같은 이닝의 유사 이벤트가 저정보·중복 문구로 쌓이므로, 급변 구간의 anchor 이벤트 하나에만 문구를 붙인다. 공개 이벤트 문구는 생성하지 않는다. 저장 조건과 타임아웃은 `FINAL_HEADLINE`과 동일하다.
  - anchor는 급상승 윈도 안에서 아직 하이라이트로 표시되지 않았고 보호 라벨을 산출할 수 있는 `PROTECTED_SAFE` 이벤트 중, 유형 우선순위가 가장 높은(동순위면 가장 최근) 이벤트다. 우선순위는 정보량 순으로 `pressure_bases_loaded` > `pressure_scoring_position` > `full_count_two_out` > `pitcher_instability` > `long_at_bat` > `hard_contact`이며, 발생 빈도가 높은 `hard_contact`가 anchor를 과대 점유해 유사 문구가 연속 노출되는 것을 막는다. 직전(시간상 인접) 하이라이트와 같은 유형은 회피하되, 윈도에 그 유형뿐이면 그대로 허용해 하이라이트 밀도를 유지한다. anchor 이벤트에 `game_events.is_timeline_highlight=true`를 표시하고 그 행에 문구를 저장한다.
  - 급상승 윈도에 보호 이벤트가 하나도 없으면(득점·리드 교체 같은 결과성 사건만으로 급변한 경우) **하이라이트를 만들지 않는다.** 그 순간은 스포일러 없이 설명할 수 없으므로 보호 모드에서 노출하지 않는다(공개 모드는 결과 이벤트로 노출).
  - 경기 종료 정리 시점에 하이라이트가 0건이면 `min-score`(절대 점수) 조건만 완화한 백필로 급변 anchor를 `backfill-max-per-game` 건까지 소급 표시하고 같은 방식으로 문구를 요청한다. 완화 후에도 조건을 채우는 anchor가 없으면 타임라인은 빈 상태가 정상이다. 전체 문구 재처리 배치도 같은 백필을 사전 단계로 수행하며, 보호 이벤트 문구 재생성 대상은 `is_timeline_highlight=true`인 행으로 한정한다.
  - 하이라이트 임계는 알림용 `thresholds`와 분리한 `scoring.highlight`(min-score·rise-score·window·cooldown·backfill-max-per-game)로 관리한다. 개발 단계에서는 `scoring.highlight.enabled=true`(기본)로 하이라이트 트리거를 사용하며, `false`로 두면 기존 이벤트별 트리거로 되돌린다.
- scorer가 새 `Play Result`를 확인하면 원문 `plays.text`에 대해 `PLAY_TRANSLATION`을 요청한다. 동일 원문 해시의 번역이 이미 있으면 재요청하지 않는다.
- 사용자 응답에서는 저장된 AI 문구가 있으면 그 문구를 반환한다. 헤드라인이 없으면 `headline=null`, 이벤트 문구가 없으면 `copy=null`을 반환한다. 플레이 번역이 없으면 최근 플레이 API가 원문과 `translated=false`를 반환한다.
- AI 문구 DB 저장에 성공하면 scorer는 해당 경기의 `game_updated` 신호를 재발행한다. 클라이언트는 기존 재조회 흐름으로 빈 헤드라인 영역이 문구로 채워지고, 이벤트 라벨 폴백이나 최근 플레이 원문이 AI 결과로 교체되는 경험을 제공한다.
- 실패 시 무한 재호출을 막기 위해 시도 횟수를 영속하고 상한을 둔다. `EVENT_COPY`는 재시도 스케줄러가 일정 지연 후 상한(기본 3회)까지 재요청하고, `PLAY_TRANSLATION`도 시도 상한(기본 3회)을 넘으면 원문 폴백을 유지한다. 공개 헤드라인은 중요 플레이 번역이 저장된 뒤 경기당 1회만 자동 재생성을 시도한다(`games.final_headline_revealed_regeneration_attempted_at`으로 멱등). 상한 초과 시 `null`(또는 원문 폴백) 상태가 유지된다.

## 3. 저장 위치

| 문구 종류 | 저장 |
|---|---|
| 종료 경기 헤드라인 · 보호 모드 | PostgreSQL `games.final_headline_protected` |
| 종료 경기 헤드라인 · 공개 모드 | PostgreSQL `games.final_headline_revealed` |
| 이벤트 문구 · 보호 모드 | PostgreSQL `game_events.copy_protected` |
| 이벤트 문구 context hash · 보호 모드 | PostgreSQL `game_events.copy_protected_context_hash` |
| 최근 플레이 한국어 번역 · 공개 모드 | PostgreSQL `plays.text_ko` |
| 최근 플레이 번역 context hash · 공개 모드 | PostgreSQL `plays.text_ko_context_hash` |

context hash는 산출물별 원천과 목적을 포함해 계산한다. 이벤트 문구와 플레이 번역은 서로 다른 테이블·컬럼에 저장하며, 공개 이벤트 문구용 컬럼은 더 이상 사용하지 않는다.

모든 컬럼은 nullable이다. 저장된 값이 없으면 API 응답의 `headline` 또는 `copy` 필드는 `null`이다. 플레이 번역이 없으면 최근 플레이 API가 원문을 반환한다. AI 문구는 Redis 캐시를 두지 않고 `games`·`game_events`·`plays`에서 직접 조회한다.

## 4. ai-service 요청 컨텍스트

헤드라인과 이벤트 문구는 원본 경기 데이터를 그대로 보내지 않고 화면 노출이 가능한 값만 `safeContext`로 매핑해 ai-service에 전달한다. `safeTags`와 `keyMoments.label`에는 내부 신호명이 아니라 보호 표기 문자열만 넣는다. 공개 모드 플레이 번역만 §4.3의 최소 원문 계약을 사용한다.

`mode=PROTECTED` 요청은 점수·이닝 초/말·play 원문·타석 결과·우세 팀·승패 결과를 어떤 경우에도 포함하지 않는다. `mode=REVEALED`의 `FINAL_HEADLINE`은 이미 공개 모드에서 노출 중인 결과 데이터(팀명, 최종 점수, 승패, 연장 여부, 득점 장면)를 전달할 수 있다. `PLAY_TRANSLATION`은 공개 모드 전용이므로 번역 대상 play 원문만 전달하고, 다른 경기 문맥이나 추천 점수는 전달하지 않는다.

"포함하지 않는다"는 값이 아니라 키 기준이다. `mode=PROTECTED` 요청 JSON에는 금지 필드의 키 자체가 없어야 하며, `"finalScore": null`처럼 값만 비운 형태도 계약 위반이다(SPOILER_POLICY.md §5). 요청 DTO를 모드별로 나누거나 `safeContext`에 `@JsonInclude(NON_NULL)`을 적용해 직렬화 단계에서 강제한다. 전역 Jackson 설정에 의존하지 않는다. ai-service는 보호 계약 검사를 필드 존재 여부로 수행할 수 있다.

### 4.0 컨텍스트 제공 계약 (`AiCopyContextReader`)

컨텍스트 조립과 `contextHash` 계산은 예은 영역(`com.pulse.common.ai`의 `AiCopyContextReader` 계약, 구현은 `com.pulse.api.AiCopyContextService`)이 담당한다. 창현 모듈(`com.pulse.ai`)은 이 빈을 주입받아 반환값을 ai-service 요청으로 변환·전송하고, 응답 검수·저장을 담당한다. 외부 REST GET 컨텍스트 API는 두지 않는다(기존 `GET /api/ai/games/{gameId}/spoiler-free-context`는 폐기).

- `finalHeadlineContext(gameId, mode)` — 경기가 없거나 종료 상태가 아니면 빈 값을 반환한다(헤드라인은 종료 경기 전용).
- `eventCopyContext(gameId, eventId, mode)` — `mode=PROTECTED`만 허용한다. 이벤트 없음, `gameId` 불일치, `spoiler_level!=PROTECTED_SAFE`, 라벨 산출 불가 중 하나라도 해당하면 빈 값을 반환한다.
- `playTranslationContext(gameId, playId)` — `type=Play Result`이고 비어 있지 않은 `plays.text`가 있을 때만 원문과 해시를 반환한다. 번역 입력에는 다른 play, 점수, 팀 우세, 추천 점수를 넣지 않는다.

`contextHash`는 SHA-256 소문자 hex다. 해시 입력은 `schemaVersion`, `purpose`, `mode`, `gameId`, 대상 식별자(`eventId` 또는 `playId`), context를 담은 봉투 JSON이며, 키 사전순 정렬·`null` 필드 제외·공백 없는 직렬화·UTF-8로 정규화한다. 창현 모듈은 해시를 재계산하지 않고 그대로 왕복시키며, 저장 직전에 최신 context를 재조회해 응답 해시와 일치할 때만 저장한다.

### 4.1 FINAL_HEADLINE 컨텍스트

헤드라인 컨텍스트는 모드별로 분리한다. 보호 모드는 긴장 흐름 서술용 보호 필드만, 공개 모드는 실제 경기 결과 필드만 사용한다. 공개 컨텍스트를 보호 컨텍스트의 확장판(보호 필드 + 점수)으로 만들지 않는다.

Spring Boot 필드 매핑 기준은 아래와 같다.

| Spring field | ai-service field | mode |
|---|---|---|
| `status` | `safeContext.gameStatus` | 공통 |
| `periodLabel` | `safeContext.inningPhase` | 공통 |
| `reasonTags` | `safeContext.safeTags` | `PROTECTED` 전용 |
| `spoilerSafeSignals` | `safeContext.reasonCodes` | `PROTECTED` 전용 |
| `keyMoments` | `safeContext.keyMoments` | `PROTECTED` 전용 |
| `teams` | `safeContext.teams` | `REVEALED` 전용 |
| `finalScore` | `safeContext.finalScore` | `REVEALED` 전용 |
| `winner` | `safeContext.winner` | `REVEALED` 전용 |
| `inningsPlayed` | `safeContext.inningsPlayed` | `REVEALED` 전용 |
| `extraInnings` | `safeContext.extraInnings` | `REVEALED` 전용 |
| `postseason` | `safeContext.postseason` | `REVEALED` 전용 |
| `revealedMoments` | `safeContext.revealedMoments` | `REVEALED` 전용 |
| `venue` · `startTime` | `safeContext.venue`·`startTime` | `REVEALED` 전용 (v2) |
| `homeInningScores` · `awayInningScores` | `safeContext.homeInningScores`·`awayInningScores` | `REVEALED` 전용 (v2) |
| `summaryFacts` | `safeContext.summaryFacts` | `REVEALED` 전용 (v2) |
| `revealedEvents` | `safeContext.revealedEvents` | `REVEALED` 전용 (v2) |
| `verifiedPlays` | `safeContext.verifiedPlays` | `REVEALED` 전용 (v2) |

보호 모드 컨텍스트에는 play 원문·`inningType`·점수 같은 경기 원본 필드를 아예 담지 않는다(보호 정책 금지 필드). `safeContext.gameStatus`는 Spring 상태값을 그대로 사용한다. 종료 경기 문구만 생성하므로 `STATUS_FINAL`만 생성 대상이다.

공개 전용 키는 `mode=REVEALED` 요청에만 존재하고, `mode=PROTECTED` 요청 JSON에는 나타나지 않는다(§4 키 기준). 단 `mode=REVEALED`에서 무승부라 `winner`를 정할 수 없으면 키를 생략하지 않고 `"winner": null`로 보낸다(공개 모드에서 `null`은 무승부라는 사실 자체를 뜻하므로 부재와 구분해야 한다).

추천 점수 파생 값(`tensionLevel`·`scoreBand` 등 내부 점수 등급)은 모드와 무관하게 컨텍스트에 넣지 않는다. AI는 추천 여부를 판단하지 않는다. AI 문구가 그대로 화면에 노출되므로, `watch_score`·`peak_base_score`의 등급을 컨텍스트로 전달하면 문구를 통해 내부 추천 점수가 간접 노출된다(SPOILER_POLICY.md §5 전 모드 금지). `revealedMoments` 선별 기준에도 내부 점수·기여도를 사용하지 않는다.

#### 보호 컨텍스트 (`mode=PROTECTED`)

긴장 흐름은 `safeTags`·`reasonCodes`·`keyMoments`로만 전달한다. `keyMoments`는 `game_events`의 `spoiler_level=PROTECTED_SAFE` 행에서 산출하며, 공개 전용 이벤트는 넣지 않는다.

- 라벨 매핑이 없는 미지 `event_type`은 제외한다(기본 차단).
- `(inning, label)` 중복을 제거하고, 이닝당 최대 2개·동일 라벨 경기 전체 최대 2개·전체 최대 8개로 제한한다(라벨 노이즈로 인한 간접 스포일러·입력 편중 방지).
- 초과 시 최신 이벤트부터 역순으로 쿼터를 적용해 후반 긴장 구간을 우선 선별하고, 최종 목록은 `inning ASC NULLS LAST, observed_at ASC, id ASC`로 재정렬해 시간순을 유지한다.

#### 공개 컨텍스트 (`mode=REVEALED`)

공개 헤드라인의 목적은 결과를 이미 볼 사용자에게 경기를 한 줄로 요약하는 것이다. 보호 표기(`safeTags`·`reasonCodes`·보호용 `keyMoments`)는 결과가 빠진 간접 표현이라 공개 헤드라인에서는 표현력이 낮고 문장을 추상적으로 만들므로 공개 컨텍스트에 넣지 않는다. 대신 아래 결과 필드를 전달한다.

| 필드 | 원천 | 이유 |
|---|---|---|
| `teams` | `games.home/away_team_name`·`_abbr` | 팀을 정확한 이름·약어로 서술 |
| `finalScore` | `games.home_runs`·`away_runs` | 최종 점수 |
| `winner` | 최종 점수 비교 파생 | 승패. 무승부면 `null` 유지 |
| `inningsPlayed` | `games.period` | 연장·단축 경기 서술 근거 |
| `extraInnings` | `period > 9` 파생 | 연장 여부 명시 |
| `postseason` | `games.postseason` | 포스트시즌 여부. 라운드(와일드카드 등) 추론은 하지 않는다 |
| `revealedMoments` | 공개 전용 `game_events` + 대응 `plays` 결합 | 결정적 득점 장면 서술 근거 |
| `venue` · `startTime` | `games.venue`·`start_time` | 경기 기본 정보 서술(공개 모드에서 이미 노출되는 값) |
| `homeInningScores` · `awayInningScores` | `games.*_inning_scores` | 역전·빅이닝 등 점수 흐름 서술 근거 |
| `summaryFacts` | 최종 점수·이닝별 점수 파생 | 승자, 첫 득점·동점·결승 이닝, 리드 교체 수, 역전승·끝내기·완봉 같은 검증 가능한 요약 사실 |
| `revealedEvents` | 공개 전용 `game_events` | eventType별 허용된 근거(evidence)만 담은 공개 이벤트 목록 |
| `verifiedPlays` | `plays`의 검증된 Play Result | 원문·저장된 번역(`translatedText` 우선)·전후 점수. 프롬프트가 실제 play 문장을 인용할 근거 |

`revealedMoments`는 헤드라인 서사의 핵심 입력이며, 재조회 시 같은 해시가 나오도록 아래 규칙으로 결정적으로 선별한다.

- 대상은 `spoiler_level=REVEALED_ONLY`의 `scoring_play`·`lead_change`·`home_run`·`big_inning`만이다.
- 같은 play에서 나온 이벤트(예: 홈런 하나가 만드는 `scoring_play`+`home_run`+`lead_change`)는 동일 `(source_type, source_ref)` 기준 한 순간으로 병합하고 `eventTypes` 배열로 표현한다.
- 후보가 많으면 범주별 대표를 고른다: 가장 최근 리드 교체, 가장 큰 홈런 득점 장면, 득점 play가 가장 많았던 빅이닝, 경기의 마지막 득점 play. "가장 큰"은 `runsScored`, 빅이닝 규모는 `scoringPlays` 수치만 기준으로 쓴다.
- 중복 제거 후 최대 4개로 제한하고, 최종 배열은 `inning ASC, 초→말, source_ref ASC, id ASC`로 정렬한다. 동률 우선순위도 `source_ref`·`id`로 고정한다.

각 순간의 하위 필드는 아래와 같고, 값이 없는 키는 생략한다.

| 하위 필드 | 원천 |
|---|---|
| `inning` | `game_events.inning` |
| `inningHalf` | `game_events.inning_type` (`Top`/`Bottom`으로 정규화, `tensionCurve` 공개 응답과 동일 표기) |
| `battingTeam` | `inning_type` 파생(초=원정 약어, 말=홈 약어) |
| `eventTypes` | 병합된 `event_type` 목록 |
| `batter` | `game_events.batter_id` → `players.full_name` |
| `runsScored` | payload `scoreValue` |
| `scoreAfter` | 대응 `plays.home_score`·`away_score` |
| `scoringPlays` | `big_inning` payload의 `scoringPlays` |

조회 규칙: 공개 컨텍스트는 `WatchScore`와 보호 이벤트를 조회하지 않는다. `games` 단건 조회와 공개 이벤트·대응 play·선수명을 묶은 일괄 조회로 구성하고, 선수명 단건 반복 조회(N+1)를 만들지 않는다.

공개 컨텍스트에 넣지 않는 것과 이유:

- `standings`·`player_season_stats` — 추가 조회와 기준 시점 문제가 있고 경기 자체 요약에서 벗어난다.
- 원본 `payload` 전체 — 이벤트별 스키마가 불균일하고 입력이 커진다. `revealedEvents`는 eventType별로 허용된 evidence만 담는다.
- 내부 추천 점수·기여도 — §4.1 공통 금지(간접 노출 방지).

```jsonc
// POST /ai/final-headline (mode=PROTECTED)
{
  "gameId": 5058990,
  "mode": "PROTECTED",
  "contextHash": "...",
  "safeContext": {
    "gameStatus": "STATUS_FINAL",
    "inningPhase": "경기 종료",
    "safeTags": ["후반 긴장 구간"],
    "reasonCodes": ["late_or_extra"],
    "keyMoments": [
      { "inning": 7, "label": "만루 승부" },
      { "inning": 8, "label": "득점권 승부" }
    ]
  }
}
// finalScore·winner 키는 나타나지 않는다.

// POST /ai/final-headline (mode=REVEALED)
{
  "gameId": 5058990,
  "mode": "REVEALED",
  "contextHash": "...",
  "safeContext": {
    "gameStatus": "STATUS_FINAL",
    "inningPhase": "경기 종료",
    "teams": {
      "home": { "name": "Los Angeles Dodgers", "abbr": "LAD" },
      "away": { "name": "San Francisco Giants", "abbr": "SF" }
    },
    "finalScore": { "home": 5, "away": 3 },
    "winner": "home",
    "inningsPlayed": 10,
    "extraInnings": true,
    "postseason": false,
    "revealedMoments": [
      {
        "inning": 8,
        "inningHalf": "Top",
        "battingTeam": "SF",
        "eventTypes": ["scoring_play", "home_run", "lead_change"],
        "batter": "Heliot Ramos",
        "runsScored": 2,
        "scoreAfter": { "home": 2, "away": 3 }
      },
      {
        "inning": 9,
        "inningHalf": "Bottom",
        "battingTeam": "LAD",
        "eventTypes": ["scoring_play"],
        "runsScored": 1,
        "scoreAfter": { "home": 3, "away": 3 }
      },
      {
        "inning": 10,
        "inningHalf": "Bottom",
        "battingTeam": "LAD",
        "eventTypes": ["scoring_play", "home_run", "lead_change"],
        "batter": "Shohei Ohtani",
        "runsScored": 2,
        "scoreAfter": { "home": 5, "away": 3 }
      }
    ]
  }
}
// safeTags·reasonCodes·keyMoments 키는 나타나지 않는다.
// venue·startTime·homeInningScores·awayInningScores·summaryFacts·revealedEvents·verifiedPlays(v2 확장 필드)는
// 값이 있을 때 함께 포함된다(위 예시에서는 생략).
```

### 4.2 EVENT_COPY 컨텍스트

이벤트 문구는 보호 모드에 노출 가능한 값만 전달하되, 같은 라벨의 이벤트도 문구가 구분되도록 스포일러-세이프한 상황 근거를 함께 넣는다.

| mode | safeContext |
|---|---|
| `PROTECTED` | `{ eventType, label, inning, contributingLabels?, situation? }` |

- `contributingLabels`: 같은 이닝의 서로 다른 보호 라벨 묶음(anchor 포함, 최대 4개). 여러 긴장 요소가 겹친 순간을 한 문구로 서술하기 위한 것이다.
- `situation`: 이벤트 유형별 **상황 근거 화이트리스트**. 카운트·아웃·주자·투구수처럼 '진행 상황'만 담는다.
  - `pressure_bases_loaded` → `outs`, `runnerOnFirst/Second/Third`
  - `pressure_scoring_position` → `outs`, `runnerOnSecond/Third`
  - `full_count_two_out` → `outs`, `balls`, `strikes`, `runnerOnFirst/Second/Third`
  - `long_at_bat` → `pitchNumber` · `pitcher_instability` → `pitcherPitchCount`
  - `hard_contact` → `outs`, `runnerOnFirst/Second/Third` (타구질 `exitVelocity`·`isBarrel`은 결과 암시라 계속 제외)
- **제외**: 원본 `payload` 전체, 선수명, 초/말, 점수·결과 수치, 결과 암시값(`exitVelocity`·`isBarrel`·`scoreValue`·`velocityDropMph`), 추천/긴장 점수 밴드(§4.1과 동일, 내부 점수 간접 노출 금지).
- `inning`은 컨텍스트에 남기지만 프론트 타임라인이 이미 "N회" 헤더로 표시하므로 **문구에는 이닝/회차를 넣지 않는다**(프롬프트 지시).
- `label`은 `PROTECTED_SAFE` 이벤트의 보호 표기만 반환하며, 라벨 존재 여부가 아니라 `spoiler_level`을 먼저 검사한다. 공개 모드 이벤트 문구 요청은 지원하지 않는다.
- 제공 주체는 `AiCopyContextReader`(예은)다. `contributingLabels`·`situation`을 ai-service 요청 JSON과 프롬프트로 연결하는 매퍼·프롬프트·`ai_schema` 반영은 창현 몫이다.

```jsonc
// POST /ai/event-copy (mode=PROTECTED)
{
  "gameId": 5059041,
  "eventId": 91,
  "mode": "PROTECTED",
  "contextHash": "...",
  "safeContext": {
    "eventType": "full_count_two_out",
    "label": "승부처 카운트",
    "inning": 7,
    "contributingLabels": ["만루 승부", "승부처 카운트"],
    "situation": {
      "outs": 2, "balls": 3, "strikes": 2,
      "runnerOnFirst": true, "runnerOnSecond": true, "runnerOnThird": true
    }
  }
}
```

### 4.3 PLAY_TRANSLATION 컨텍스트

공개 모드 최근 플레이에 표시할 단일 타석 결과만 번역한다. 요청에는 `gameId`, `playId`, `mode=REVEALED`, `contextHash`, `sourceText`, `targetLanguage=ko`를 포함한다. `sourceText` 외에 점수·팀·다른 play·추천 점수는 전달하지 않는다.

번역 결과는 원문의 선수명, 숫자, 야구 결과를 보존한 한 문장이어야 한다. 요약, 해설, 감정 표현, 원문에 없는 사실을 추가하지 않는다. 프론트는 저장된 번역을 재가공하지 않고 그대로 표시한다.

번역 근거는 `sourceText` 하나로 유지한다(v1 확정). 야구 용어(`to shortstop`, `looking` 등)는 추가 문맥 없이 번역 가능하고, 문맥을 넓히면 원문에 없는 사실 삽입과 해설성 표현을 유도할 수 있다. 대명사가 불명확해도 이름을 추정해 넣지 않고 한국어에서 자연스럽게 생략한다. 타자·투수명 참조 문맥(`referenceContext`) 추가는 운영 표본에서 대명사 오역이 반복된다는 근거가 생길 때 v2에서 검토한다. 컨텍스트를 확장하면 요청 계약과 stale 검증용 원문 해시 입력을 함께 바꿔야 한다.

```jsonc
// POST /ai/play-translation
{
  "gameId": 5059041,
  "playId": 312,
  "mode": "REVEALED",
  "contextHash": "...",
  "sourceText": "Marsh struck out looking.",
  "targetLanguage": "ko"
}
```

## 5. scorer ↔ ai-service HTTP 계약

내부 HTTP API의 경로, 요청·응답 필드, 필수값, enum, 예시는 FastAPI가 생성하는 OpenAPI를 단일 기준으로 사용한다.

| 문서 | 경로 |
|---|---|
| Swagger UI | ai-service `/docs` |
| OpenAPI JSON | ai-service `/openapi.json` |

스키마를 변경할 때는 `ai-service/app/schemas/ai_schema.py`의 Pydantic 모델과 `ai-service/app/routers/ai_router.py`의 `response_model`을 먼저 수정한다. 이 문서에는 요청·응답 JSON 예시를 중복 작성하지 않는다.

| API | 저장 위치 | 저장 책임 |
|---|---|---|
| 종료 헤드라인 생성 | `games.final_headline_*` | scorer |
| 보호 이벤트 문구 생성 | `game_events.copy_protected`, `copy_protected_context_hash` | scorer |
| 공개 플레이 번역 | `plays.text_ko`, `text_ko_context_hash` | scorer |

- 요청 직렬화: `mode=PROTECTED` 요청의 `safeContext`에는 금지 필드 키가 존재하지 않아야 한다(§4). 헤드라인의 모드별 직렬화 결과를 검증하는 테스트를 둔다. `PLAY_TRANSLATION`은 `mode=REVEALED`만 허용한다.
- 응답의 `violations`, `fallbackUsed`, `contextHash`는 성공·실패와 무관하게 반환한다.
- 검수 기준: `mode=PROTECTED` 응답은 결과·방향성 표현(SPOILER_POLICY.md §6)이 포함되면 `spoilerSafe=false`로 반려한다. 공개 헤드라인은 `safeContext`로 전달한 실제 결과 범위 안의 언급만 허용한다. 플레이 번역은 비어 있지 않아야 하며 원문의 이름·숫자·결과를 보존하고 설명을 추가하지 않아야 한다.
- 범위 밖 계약: 알림·토스트·`switchSuggestion` 문구는 LLM을 사용하지 않는다. 태그별 고정 템플릿으로 서버가 완성 문자열을 조립한다.
- 타임아웃: Spring Boot → ai-service 호출은 8초, ai-service → OpenAI 호출은 목적별 설정으로 더 짧게 제한한다(문구 생성·플레이 번역 3초, 공통 기본 6초). OpenAI 응답 이후 JSON 파싱·스포일러 검수·응답 반환 시간이 필요하므로 ai-service 내부 timeout을 Spring Boot 호출 timeout보다 짧게 둔다.
- ai-service는 무상태다. 캐시·DB에 직접 쓰지 않고 생성·검수 결과를 응답으로만 반환한다.
- ai-service는 대체 문구를 생성하거나 반환하지 않는다. 현재 계약에서 `fallbackUsed`는 항상 `false`다.
- 저장 조건: 헤드라인·이벤트 문구는 `spoilerSafe=true`, `fallbackUsed=false`, `contextHash` 일치인 응답만 저장한다. 플레이 번역은 `translatedText`가 비어 있지 않고 `fallbackUsed=false`, `contextHash` 일치일 때만 저장한다.
- stale write 방지: 응답의 `contextHash`가 대상의 최신 context 해시와 일치할 때만 저장한다. 이벤트 문구는 `eventId`·`gameId`·`mode=PROTECTED`, 플레이 번역은 `playId`·`gameId`·원문 해시가 모두 일치해야 한다.

### 5.1 응답 처리 원칙

보호 모드 문구는 이닝 숫자와 보호 표기만 사용하며 결과·방향·팀 유불리를 드러내지 않는다. 검수 실패나 생성 실패 시 생성 문구 필드는 비우거나 생략하고 실패 상태와 위반 코드를 반환한다. Spring Boot는 위반 코드를 저장 금지 사유로 기록할 수 있다.

### 5.2 violations 코드 패턴

`violations`는 아래 패턴으로 고정한다. 상세 목록(이벤트·방향·위치별 개별 코드)은 ai-service의 YAML 용어집이 기준이다.

| 코드 패턴 | 의미 |
|---|---|
| `FORBIDDEN_WORD:{word}` | 보호 문구에 금지 표현(SPOILER_POLICY.md §6) 포함 |
| `SOURCE_TEXT_EMPTY` · `TRANSLATED_TEXT_EMPTY` | 원문 또는 번역문이 비어 있음 |
| `MULTIPLE_SENTENCES` | 번역문이 여러 문장으로 생성됨 |
| `MISSING_PLAYER_NAME:{name}` · `ADDED_PLAYER_NAME:{name}` | 원문 선수명 누락 또는 원문에 없는 선수명 추가 |
| `MISSING_NUMBER:{number}` · `ADDED_NUMBER:{number}` | 원문 숫자 누락 또는 근거 없는 숫자 추가 |
| `MISSING_EVENT:{outcomeCode}` | 용어집에 매칭된 이벤트(안타·홈런·삼진 등)의 필수 한국어 표현 누락 |
| `MISSING_DIRECTION:{directionId}` · `MISSING_POSITION:{positionId}` | 타구 방향·수비 위치 표현 누락 |
| `ADDED_RESULT:{code}` | 원문에 없는 결과 표현(홈런·역전·끝내기·승패·득실점·리드) 추가 |
| `ADDED_COMMENTARY:{expression}` | 평가·감정·해설 표현 추가 |
| `OPENAI_*` | 생성 자체 실패. `OPENAI_TIMEOUT`, `OPENAI_MAX_OUTPUT_TOKENS`, `OPENAI_CONTENT_FILTER`, `OPENAI_INCOMPLETE_RESPONSE`, `OPENAI_EMPTY_RESPONSE`, `OPENAI_INVALID_JSON`, `OPENAI_RESPONSE_MISSING_FIELD:{field}`, `OPENAI_API_KEY_MISSING` |

## 6. 소비 인터페이스

종료 헤드라인은 별도 리더 인터페이스 없이 API(홈·상세)가 `games.final_headline_protected`·`final_headline_revealed`를 직접 조회한다. 검수 통과 헤드라인이 없으면 응답 `headline=null`이고, 소비자(홈·상세)는 `null`을 조건부 렌더링 기준으로 사용한다.

이벤트 문구는 `GET /api/games/{id}/events?mode=PROTECTED`가 `game_events` 중 **`is_timeline_highlight=true`인 하이라이트 이벤트만** 조회해 `copy`(=`copy_protected`)로 반환한다. `copy=null`이면 프론트는 같은 이벤트의 `label`을 폴백으로 표시한다(하이라이트 표시는 유지되고 문구만 폴백). 공개 모드(`mode=REVEALED`)는 이벤트 타임라인을 사용하지 않으므로 빈 목록을 반환하고, 경기 흐름은 `GET /api/games/{id}/recent-plays`로 대체한다.

플레이 번역은 `GET /api/games/{id}/recent-plays?mode=REVEALED`가 `plays.text_ko`를 우선 조회해 응답 `text`로 반환한다. 번역이 없으면 `plays.text`와 `translated=false`를 반환하며, 프론트는 `text`를 그대로 표시한다.
