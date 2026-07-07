# 모듈 계약 명세

REST/SSE 명세, 모듈 인터페이스, 메시지·캐시 스키마를 정의한다. 화면별 노출 필드·금지 필드는 스포일러 노출 정책 문서가, AI 문구 계약은 AI 문구 설계 문서가, 알림 이벤트 계약은 알림 설계 문서가 정본이다.

## 1. REST 엔드포인트

| 메서드·경로 | 설명 | 로그인 |
|---|---|---|
| `GET /api/rankings/live` | 홈 상단 추천 영역(예정/진행/종료 슬롯, 추천순). 로그인 시 관심 팀/선수 가산이 적용된 순서 | 선택 |
| `GET /api/games?date=&status=&sort=` | 홈 하단 전체 경기 목록(슬레이트 단위). `date` 미지정 시 오늘 슬레이트, `status` in `all\|scheduled\|live\|finished`(기본 `all`), `sort` in `recommended\|startTime`(기본 `startTime`) | 선택 |
| `GET /api/games/{id}?mode=PROTECTED\|REVEALED` | 경기 상세. `mode` 기본값 `PROTECTED`. 진행 중이면 `switchSuggestion` 포함 | 선택 |
| `GET /api/games/{id}/replay?mode=` | 종료 경기 다시보기 구간 목록 | 선택 |
| `GET /api/games/{id}/events?mode=` | 흥미 순간 이벤트 타임라인(진행·종료 공통) | 선택 |
| `GET /api/sse` | SSE 구독(이벤트 3종) | 선택 |
| `POST /api/sse/token` | SSE 연결용 1회용 단기 토큰 발급(§2.1) | 필요 |
| `POST /api/members/signup` · `/login` · `/refresh` · `/logout` | 이메일+비밀번호 인증, JWT 발급·재발급·로그아웃 | — |
| `GET /api/members/me` | 현재 로그인 사용자 확인 | 필요 |
| `POST /api/members/email/**` | 이메일 인증 요청·확인 | — |
| `GET·PUT /api/me/preferences` | 관심 팀/선수, 알림 설정 | 필요 |
| `GET /api/me/notifications` · `POST /api/me/notifications/read` | 알림 센터 목록·읽음 처리 | 필요 |
| `GET /api/players?search=` | 관심 선수 등록용 이름 검색 | 필요 |

공통 에러 응답: `{ "code": "GAME_NOT_FOUND", "message": "..." }` 형식, HTTP 상태 코드와 함께.

`GET /api/games`의 `date`는 MLB 슬레이트 날짜(미국 동부시간 기준 `YYYY-MM-DD`)다. 서버가 해당 슬레이트를 `start_time`(UTC) 범위로 변환해 조회한다. `sort=recommended`는 상태 탭(`scheduled`/`live`/`finished`)에서만 유효하며, `status=all`이면 `startTime`으로 강제하고 진행 중 경기를 상단에 고정한다. 추천순 정렬 키는 예정 `pregame_score`, 진행 `watch_score`, 종료 `peak_base_score`이고, 응답에는 점수·순위 숫자를 포함하지 않는다. 이 엔드포인트는 홈(api.home) 소유이며 경기 상세(`GET /api/games/{id}`)와 경로 네임스페이스를 공유한다.

### 1.1 인증 토큰 정책

| 항목 | 기준 |
|---|---|
| 액세스 토큰 | JWT, 유효기간 15분. `Authorization: Bearer` 헤더로 전달 |
| 리프레시 토큰 | JWT, 유효기간 7일. 로그인 응답의 HttpOnly 쿠키 `refreshToken`으로 전달한다. 쿠키 경로는 `/api/members`다 |
| 서버 저장 | DB `refresh_tokens`에 토큰 해시와 상태를 저장한다. 토큰 원문은 저장하지 않는다 |
| 재발급 | `/api/members/refresh` 성공 시 기존 리프레시 토큰을 폐기(`revoked_at`)하고 새 액세스 토큰과 새 리프레시 토큰을 발급한다 |
| 재사용 감지 | 폐기된 리프레시 토큰이 다시 제시되면 해당 사용자의 리프레시 토큰 전체를 폐기한다 |
| 로그아웃 | `/api/members/logout`은 리프레시 토큰을 폐기하고, 응답에서 `refreshToken` 쿠키를 만료시킨다 |
| 정리 기준 | `refresh_tokens` 행은 `expires_at` 경과 후에만 배치 삭제한다. 폐기 행도 만료 전에는 재사용 감지 근거로 보존한다 |
| 배포 설정 | 로컬은 쿠키 `Secure=false`, 배포 HTTPS 환경은 `Secure=true`로 전환한다 |

현재 PR #15의 SecurityConfig는 개발 중 다른 기능 연결을 막지 않기 위해 `/api/members/me`만 인증을 강제하고 나머지는 임시 `permitAll`로 둔다. 위 표의 로그인 필요 API는 통합 전 보호 범위로 다시 잠근다.

### 1.2 대표 응답 예시 (mock 개발 기준)

정렬은 서버가 적용해 내려주며, 응답에 점수·순위 숫자는 포함하지 않는다.

```jsonc
// GET /api/rankings/live
{
  "generatedAt": "2026-07-07T02:10:40Z",
  "live": [
    { "gameId": 5059041, "matchup": { "home": "CHC", "away": "SD" },
      "inning": 8, "tags": ["접전 흐름", "득점권 압박"],
      "headline": "지금 볼 만한 흐름이 감지됐습니다." }
  ],
  "scheduled": [
    { "gameId": 5059100, "matchup": { "home": "NYY", "away": "BOS" },
      "startTime": "2026-07-07T23:05:00Z", "venue": "Yankee Stadium",
      "probablePitchers": { "home": "...", "away": "..." },
      "tags": ["순위 경쟁"] }
  ],
  "finished": [
    { "gameId": 5058990, "matchup": { "home": "LAD", "away": "SF" },
      "replaySegmentCount": 2, "tags": ["후반 긴장 구간"] }
  ]
}

// GET /api/games?date=2026-07-06&status=all&sort=startTime
{
  "slateDate": "2026-07-06",
  "games": [
    { "gameId": 5059041, "gameState": "LIVE",
      "matchup": { "home": "CHC", "away": "SD" },
      "startTime": "2026-07-06T23:05:00Z", "inning": 8,
      "tags": ["접전 흐름", "득점권 압박"],
      "headline": "지금 볼 만한 흐름이 감지됐습니다." },
    { "gameId": 5059100, "gameState": "SCHEDULED",
      "matchup": { "home": "NYY", "away": "BOS" },
      "startTime": "2026-07-07T23:05:00Z",
      "probablePitchers": { "home": "...", "away": "..." },
      "tags": ["순위 경쟁"] },
    { "gameId": 5058990, "gameState": "FINAL",
      "matchup": { "home": "LAD", "away": "SF" },
      "startTime": "2026-07-06T20:10:00Z",
      "replaySegmentCount": 2, "tags": ["후반 긴장 구간"] }
  ]
}

// GET /api/games/{id}?mode=PROTECTED (진행 중)
{
  "gameId": 5059041, "gameState": "LIVE", "mode": "PROTECTED",
  "matchup": { "home": "CHC", "away": "SD" },
  "probablePitchers": { "home": "...", "away": "..." },
  "inning": 8,
  "situation": { "outs": 2, "balls": 3, "strikes": 2,
                 "scoringPosition": true, "basesLoaded": false },
  "tags": ["접전 흐름", "득점권 압박"],
  "tagHistory": [ { "tags": ["접전 흐름"], "observedAt": "2026-07-07T01:40:00Z" } ],
  "headline": "지금 볼 만한 흐름이 감지됐습니다.",
  "favoritePlayersPlaying": ["Shohei Ohtani"],
  "switchSuggestion": null
}
```

## 2. SSE 이벤트

payload에는 점수·순위·결과 데이터를 싣지 않는다. 클라이언트는 신호 수신 즉시 해당 REST를 재조회한다(개인화·스포일러 필터링은 항상 서버 REST 응답에서 적용). `notification_created`는 인증된 연결에만 발행한다.

| 이벤트 | payload | 클라이언트 동작 |
|---|---|---|
| `ranking_changed` | `{ sequence, generatedAt }` | 홈 랭킹 재조회 |
| `game_updated` | `{ gameId, sequence, generatedAt }` | 보고 있는 경기면 상세 재조회(현재 mode 유지) |
| `notification_created` | `{ notificationId }` | 알림 목록 재조회, 토스트 표시 |

### 2.1 연결·인증·재연결

- 로그인 연결: `POST /api/sse/token`으로 1회용 토큰(Redis, TTL 60초)을 발급받아 `GET /api/sse?token=`으로 연결한다. 액세스 토큰을 URL에 싣지 않기 위한 분리다(브라우저 히스토리·프록시 로그 노출 방지). 토큰은 연결 수립 시 소모된다.
- 비로그인 연결: 토큰 없이 연결하며 `ranking_changed`·`game_updated`만 수신한다.
- 하트비트: 서버가 25초 간격 SSE 코멘트를 보내 유휴 연결 단절을 방지한다.
- 연결 수명: 서버는 연결당 최대 60분 후 종료한다. 액세스 토큰 만료는 수립된 연결을 끊지 않는다.
- 재연결: `Last-Event-ID`는 사용하지 않는다. payload가 재조회 신호뿐이므로, 클라이언트 SSE 훅이 연결 오류 시 새 토큰을 발급받아 재연결하고 관련 REST를 1회 재조회하면 상태가 복구된다.

## 3. 경기 전환 추천 (`switchSuggestion`)

알림 파이프라인을 타지 않는다. `GET /api/games/{id}` 응답에 포함한다.

- 판정: 서버가 Redis 랭킹에서 "현재 경기보다 `watch_score` 20점 이상 높고 70 이상"인 경기를 찾는다. 점수는 서버 내부에서만 사용.
- 응답: `switchSuggestion: { gameId, matchup, tags } | null`
- 쿨다운: 같은 후보 15분 1회, 로그인 사용자는 Redis 키(사용자별), 비로그인은 클라이언트 보조.

## 4. 모듈 인터페이스 (팀 계약 지점)

| 인터페이스 | 제공 → 사용 | 시그니처·내용 |
|---|---|---|
| domain 읽기 | 예은 → 전원 | JPA 엔티티 읽기 전용. 스키마 변경은 예은만 |
| `ScoreQueryService` | 예은 → 민석 | `getLatestSignals(gameId)` → `{ tags, phase, situation, updatedAt }`. **점수 숫자는 계약에 없음** |
| `AiCopyReader` | 창현 → 전원 | `getCopy(gameId, purpose)` — 항상 non-null. 검수 통과 AI 문구가 없으면 Spring Boot의 목적별 기본 문구 반환 |
| `UserPreferenceReader` | 윤호 → 예은(홈 가산)·api(알림 fan-out, 전환 쿨다운) | 관심 팀/선수·알림 설정 조회 |
| `SseEventPublisher` | api 공통 | 이벤트 3종 발행 단일 창구 |
| AI 생성 트리거 | 창현 → 예은(scorer) | `com.pulse.ai`의 비동기 생성 요청 인터페이스. ai-service 호출, `contextHash` 검증, 검수 통과 문구 저장, 기본 문구 fallback 판단 담당 |
| `notify.events` | scorer·poller → 윤호 | 알림 이벤트 |

## 5. 메시징·캐시 명세

### 5.1 RabbitMQ

| 큐 | 용도 | 정책 |
|---|---|---|
| `score.tasks` (+`.dlq`) | poller → scorer 계산 요청 | ack 실패 시 1회 재전달 후 DLQ. 소비자 prefetch 5 |
| `notify.events` (+`.dlq`) | 알림 이벤트 | 동일. 소비 측 멱등 처리 전제 |

```jsonc
// ScoreTask (score.tasks) — 라이브 재계산 task: lifecycleState=LIVE, situation 포함
{ "gameId": 5059041, "observedAt": "2026-07-06T02:11:00Z",
  "lastPlayOrder": 217414761, "lifecycleState": "LIVE",
  "situation": {
    "outs": 2, "balls": 3, "strikes": 2,
    "runnerOnFirst": true, "runnerOnSecond": true, "runnerOnThird": true,
    "basesLoaded": true, "scoringPosition": true } }

// 종료 task: lifecycleState in {FINAL, DONE, SUSPENDED_POSTPONED}, situation=null
{ "gameId": 5059041, "observedAt": "2026-07-06T05:40:00Z",
  "lastPlayOrder": 217419999, "lifecycleState": "FINAL", "situation": null }

// pregame task: lifecycleState=PREGAME, situation=null
{ "gameId": 5059041, "observedAt": "2026-07-06T14:00:00Z",
  "lastPlayOrder": null, "lifecycleState": "PREGAME", "situation": null }
```

- `situation`: 현재 타석의 압박·카운트 스칼라. poller가 `/plate_appearances`(주자)·`/plays`(카운트)에서 추출해 전달한다. PA는 운영 Postgres에 없으므로 scorer는 이 값으로 압박·카운트 신호를 계산한다.
- `situation`은 nullable이다. 종료 task·구버전 task·현재 타석 없음이면 `null`이며, scorer는 null-safe로 해당 신호를 0점 처리한다. `runnerOn*`이 모두 `false`인 상태("압박 없음")와 `situation=null`("계산 불가")을 구분한다.
- `scoringPosition` = `runnerOnSecond || runnerOnThird`, `basesLoaded` = 세 주자 모두 점유. scorer가 재유도할 수 있으나 계약에 명시해 소비 측 파싱을 단순화한다.
- 하위호환: scorer는 `situation` 유무와 무관하게 동작해야 하며, poller·scorer 배포 순서나 브로커에 남은 구버전 task에 안전하다.
- `PREGAME` task: poller가 경기 전 입력이 갱신될 때(선발 확정·변경, `odds_snapshots` 기록, `standings` 일 배치 반영, `PREGAME_NEAR` 진입) 발행한다. scorer는 DB의 `lineups`·`odds_snapshots`·`standings`·`player_season_stats`만 읽어 `pregame_score`를 계산하고 `games.pregame_score`·`pregame_inputs`를 덮어쓴다. 최신 입력 기준 재계산이므로 중복·재전달에 멱등이며, `watch_scores`에는 행을 남기지 않는다. 선발 시즌 스탯의 온디맨드 외부 조회는 poller가 task 발행 전에 수행해 `player_season_stats`에 적재한다(외부 API 호출은 poller로 한정).

재전달 멱등: `watch_scores`의 UNIQUE(`game_id`, `computed_at`) 충돌 시 scorer는 해당 사이클 저장을 건너뛴다(`computed_at` = `observedAt`).

종료 task 멱등: `lifecycleState`가 `FINAL`·`DONE`·`SUSPENDED_POSTPONED`인 종료 task는 scorer가 종료 정리(구간 마감·`score:rank:live` 제거·`signal:ranking` 발행)를 경기 상태 전이 기준으로 1회만 수행한다. 이미 정리된 경기의 종료 task가 중복·역순·재전달로 도착해도 재실행하지 않는다. 종료 task 유실 대비로 poller는 상시 감시를 통해 상태 전이가 확정될 때까지 종료 task를 재발행할 수 있다.

### 5.2 Redis 키

| 키 | 타입 | 내용 |
|---|---|---|
| `score:rank:live` | ZSET | 진행 중 경기 랭킹 (member=game_id, score=watch_score) |
| `game:{id}:live` | HASH | 현재 점수·이닝·태그 캐시 (내부 전용) |
| `game:{id}:copy:{purpose}` | STRING | 검수 통과 AI 문구 캐시 |
| `notify:armed:{gameId}` | STRING | 급상승 히스테리시스 상태 |
| `notify:cooldown:global` | STRING | 전역 15분 레이트리밋 |
| `switch:cooldown:{userId}:{gameId}` | STRING | 전환 안내 쿨다운 |
| `sse:token:{token}` | STRING | SSE 연결용 1회용 토큰 (TTL 60초) |
| (pub/sub) `signal:ranking`, `signal:game:{id}` | 채널 | 재조회 신호. api가 SSE로 중계 |

라이프사이클 정리: 경기가 LIVE에서 이탈하면(`FINAL`·`DONE`·`SUSPENDED_POSTPONED`) scorer가 poller의 종료 ScoreTask(`lifecycleState`)를 받아 `score:rank:live`에서 해당 경기를 제거하고 `signal:ranking`을 발행한다. `game:{id}:live`·`game:{id}:copy:*`는 TTL로 소멸한다.
