# 모듈 계약 명세

## 1. REST 엔드포인트

| 메서드·경로 | 설명 | 로그인 |
|---|---|---|
| `GET /api/rankings/live` | 홈 상단 추천 영역(예정/진행/종료 슬롯, 추천순). 로그인 시 관심 팀/선수 가산이 적용된 순서 | 선택 |
| `GET /api/games?date=&status=&sort=` | 홈 하단 전체 경기 목록. `status=scheduled`는 현재 이후 모든 예정 경기를 조회하고, 나머지 상태는 슬레이트 단위로 조회한다. `date` 미지정 시 오늘 슬레이트, `status` in `all\|scheduled\|live\|finished`(기본 `all`), `sort` in `recommended\|startTime`(기본 `startTime`) | 선택 |
| `GET /api/games/{id}?mode=PROTECTED\|REVEALED` | 경기 상세. `mode` 기본값 `PROTECTED`. 진행 중이면 `switchSuggestion` 포함 | 선택 |
| `GET /api/games/{id}/events?mode=` | 보호 모드 `경기 흐름`용 흥미 순간 이벤트. 공개 모드는 빈 목록 | 선택 |
| `GET /api/games/{id}/recent-plays?mode=` | 공개 모드 `경기 흐름`용 전체 타석 결과(최신순, 건수 제한 없음). 보호 모드는 빈 목록 | 선택 |
| `GET /api/teams` | 회원가입 Step 2 관심 팀 선택용 팀 목록 | 선택 |
| `GET /api/sse` | SSE 구독(이벤트 3종) | 선택 |
| `POST /api/sse/token` | SSE 연결용 1회용 단기 토큰 발급(§2.1) | 필요 |
| `POST /api/members/signup` · `/login` · `/refresh` · `/logout` | 이메일+비밀번호 인증, JWT 발급·재발급·로그아웃 | — |
| `GET /api/members/me` | 현재 로그인 사용자 확인 | 필요 |
| `POST /api/members/email/**` | 이메일 인증 요청·확인 | — |
| `GET·PUT /api/me/preferences` | 관심 팀/선수, 알림 설정 | 필요 |
| `GET /api/me/notifications` · `POST /api/me/notifications/read` | 알림 센터 목록·읽음 처리 | 필요 |
| `GET /api/players?search=` | 최초 로그인 온보딩과 설정의 관심 선수 등록용 이름 검색(§1.3). 외부 우선 read-only, DB 쓰기 없음 | 필요 |

공통 에러 응답: `{ "code": "GAME_NOT_FOUND", "message": "..." }` 형식, HTTP 상태 코드와 함께.

`GET /api/games`의 `date`는 MLB 슬레이트 날짜(미국 동부시간 기준 `YYYY-MM-DD`)다. `status=scheduled`이면 `date`와 무관하게 현재 시각 이후의 `STATUS_SCHEDULED` 경기를 모두 조회한다. 그 외 상태는 서버가 해당 슬레이트를 `start_time`(UTC) 범위로 변환해 조회한다. `sort=recommended`는 상태 탭(`scheduled`/`live`/`finished`)에서만 유효하며, `status=all`이면 `startTime`으로 강제하고 진행 중 경기를 상단에 고정한다. 추천순 정렬 키는 예정 `pregame_score`, 진행 `watch_score`, 종료 `peak_base_score`이고, 응답에는 점수·순위 숫자를 포함하지 않는다. 홈 카드 필드는 상태별로 다르며 진행 카드는 `latestTag` 단일 필드만 포함하고, 예정 카드는 태그 필드를 포함하지 않으며, 종료 카드는 `headline`과 `keyMoment`만 포함한다. 이 엔드포인트는 홈(api.home) 소유이며 경기 상세(`GET /api/games/{id}`)와 경로 네임스페이스를 공유한다.

### 1.1 인증 토큰 정책

| 항목 | 기준 |
|---|---|
| 액세스 토큰 | JWT, 유효기간 15분. `Authorization: Bearer` 헤더로 전달 |
| 리프레시 토큰 | JWT, 유효기간 7일. 로그인 응답의 HttpOnly 쿠키 `refreshToken`으로 전달한다. 쿠키 경로는 `/api/members`다 |
| 서버 저장 | DB `refresh_tokens`에 토큰 해시와 상태(`ACTIVE`/`REVOKED`/`REUSED`)를 저장한다. 토큰 원문은 저장하지 않는다 |
| 재발급 | `/api/members/refresh` 성공 시 기존 리프레시 토큰을 폐기(`revoked_at`)하고 새 액세스 토큰과 새 리프레시 토큰을 발급한다 |
| 재사용 감지 | 폐기된 리프레시 토큰이 다시 제시되면 해당 사용자의 리프레시 토큰 전체를 폐기한다 |
| 로그아웃 | `/api/members/logout`은 리프레시 토큰을 폐기하고, 응답에서 `refreshToken` 쿠키를 만료시킨다 |
| 정리 기준 | `refresh_tokens` 행은 `expires_at` 경과 후에만 배치 삭제한다. 폐기 행도 만료 전에는 재사용 감지 근거로 보존한다 |
| 배포 설정 | 로컬은 쿠키 `Secure=false`, 배포 HTTPS 환경은 `Secure=true`로 전환한다 |

현재 PR #15의 SecurityConfig는 개발 중 다른 기능 연결을 막지 않기 위해 `/api/members/me`만 인증을 강제하고 나머지는 임시 `permitAll`로 둔다. 위 표의 로그인 필요 API는 통합 전 보호 범위로 다시 잠근다.

`POST /api/members/email/**`(§1)로 통일한 이메일 인증 관련 경로는 아래 정책을 따른다.

| 항목 | 기준 |
|---|---|
| 인증번호 유효기간 | 발급 후 5분(TTL). 재발급 시 이전 인증번호는 폐기한다 |
| 인증 완료 상태 유지 | 인증 성공 후 30분간 유지한다. 회원가입은 이 시간 안에 완료해야 한다 |
| 메일 발송 실패 | 인증번호를 저장하지 않는다(발급 자체를 실패로 처리) |

### 1.2 대표 응답 예시 (mock 개발 기준)

정렬은 서버가 적용해 내려주며, 응답에 점수·순위 숫자는 포함하지 않는다.

```jsonc
// GET /api/rankings/live
{
  "generatedAt": "2026-07-07T02:10:40Z",
  "live": [
    { "gameId": 5059041, "matchup": { "home": "CHC", "away": "SD" },
      "inning": 8, "latestTag": "득점권 압박" }
  ],
  "scheduled": [
    { "gameId": 5059100, "matchup": { "home": "NYY", "away": "BOS" },
      "startTime": "2026-07-07T23:05:00Z", "venue": "Yankee Stadium",
      "probablePitchers": { "home": "...", "away": "..." } }
  ],
  "finished": [
    { "gameId": 5058990, "matchup": { "home": "LAD", "away": "SF" },
      "headline": "7회 만루 승부가 등장한, 끝까지 긴장감이 이어진 경기입니다.",
      "keyMoment": "만루 승부" }
  ]
}

// GET /api/games?date=2026-07-06&status=all&sort=startTime
{
  "slateDate": "2026-07-06",
  "games": [
    { "gameId": 5059041, "gameState": "LIVE",
      "matchup": { "home": "CHC", "away": "SD" },
      "startTime": "2026-07-06T23:05:00Z", "inning": 8,
      "latestTag": "득점권 압박" },
    { "gameId": 5059100, "gameState": "SCHEDULED",
      "matchup": { "home": "NYY", "away": "BOS" },
      "startTime": "2026-07-07T23:05:00Z", "venue": "Yankee Stadium",
      "probablePitchers": { "home": "...", "away": "..." } },
    { "gameId": 5058990, "gameState": "FINAL",
      "matchup": { "home": "LAD", "away": "SF" },
      "startTime": "2026-07-06T20:10:00Z",
      "headline": null, "keyMoment": "만루 승부" }
  ]
}
```

`status`·`displayMode`는 `GameQueryService`가 이미 사용 중인 필드명이다(`gameState`/`mode`가 아님). `homeTeam`/`awayTeam`은 `{ id, name, abbr }` 객체이며, 보호 모드에서도 매치업 식별을 위해 노출한다(팀 우세·점수는 미포함). `inning`은 이닝 숫자만 노출하고 초/말(`inningType`)은 보호 모드 어떤 필드에도 포함하지 않는다.

```jsonc
// GET /api/games/{id}?mode=protected (진행 중)
{
  "gameId": 5059041, "status": "STATUS_IN_PROGRESS", "displayMode": "PROTECTED",
  "homeTeam": { "id": 1, "name": "Chicago Cubs", "abbr": "CHC" },
  "awayTeam": { "id": 2, "name": "San Diego Padres", "abbr": "SD" },
  "startTime": "2026-07-07T01:05:00Z",
  "periodLabel": "후반",
  "inning": 8,
  "situation": { "outs": 2, "balls": 3, "strikes": 2,
                 "runnerOnFirst": false, "runnerOnSecond": true,
                 "runnerOnThird": false, "scoringPosition": true,
                 "basesLoaded": false },
  "favoritePlayersPlaying": ["Shohei Ohtani"],
  "switchSuggestion": {
    "gameId": 5059002,
    "matchup": { "home": "SEA", "away": "HOU" },
    "latestTag": "후반 긴장 구간"
  }
}
```

진행 중 상세의 `situation`은 양 모드에 포함한다. 이닝 교대 중이거나 현재 타석이 없으면 `situation=null`이다. 현재 타자/투수는 공개 모드에서만 `currentMatchup: { batter, pitcher }`로 포함한다. **진행 중 상세는 보호·공개 모두 `tensionCurve`를 포함하지 않는다**(펄스 그래프는 종료 경기 전용, USER_FLOW.md §4.5). `favoritePlayersPlaying`은 양 모드 응답에 포함하되, 보호 모드는 현재 타자/투수 자체를 노출하지 않으므로 이 필드를 강조 표시에 쓰지 않는다. 공개 모드는 `currentMatchup.batter`/`pitcher.name`과 최근 플레이 등장 선수명을 이 목록과 대조해 일치하면 강조 표시한다. 화면 영역 이름은 양 모드 모두 `경기 흐름`이며 보호 모드는 이벤트 API, 공개 모드는 최근 플레이 API를 조회한다.

```jsonc
// GET /api/games/{id}?mode=revealed (진행 중)
{
  "gameId": 5059041, "status": "STATUS_IN_PROGRESS", "displayMode": "REVEALED",
  "homeTeam": { "id": 1, "name": "Chicago Cubs", "abbr": "CHC" },
  "awayTeam": { "id": 2, "name": "San Diego Padres", "abbr": "SD" },
  "score": { "home": 3, "away": 4 },
  "inning": 8,
  "inningType": "Top",
  "situation": { "outs": 2, "balls": 3, "strikes": 2,
                 "runnerOnFirst": false, "runnerOnSecond": true,
                 "runnerOnThird": false, "scoringPosition": true,
                 "basesLoaded": false },
  "currentMatchup": {
    "batter": { "id": 1001, "name": "..." },
    "pitcher": { "id": 2001, "name": "..." }
  },
  "favoritePlayersPlaying": ["Shohei Ohtani"],
  "inningScores": {
    "away": [0, 1, 0, 2, 0, 0, 1, 0],
    "home": [0, 0, 1, 0, 2, 0, 0, 0]
  }
}
```

`summary.reasonTags`, `liveUpdateBlocks`, 종료 상세의 `eventMarkers`는 응답에 포함하지 않는다. 보호 이벤트는 `GET /api/games/{id}/events`, 공개 최근 플레이는 `GET /api/games/{id}/recent-plays`를 각각 단일 원천으로 사용한다.

종료 경기 상세 응답의 `headline`은 nullable이다. 보호 모드 `headline`은 `games.final_headline_protected`, 공개 모드 `headline`은 `games.final_headline_revealed`를 원천으로 하며 두 컬럼 모두 nullable이다(AI_COPY.md `FINAL_HEADLINE` 참고). 저장된 문구가 없으면 `headline=null`을 반환하고 프론트는 헤드라인 영역을 렌더링하지 않는다.

종료 경기에는 `tensionCurve`를 포함할 수 있다. 보호 모드는 이닝 단위 `{ inning, level }`, 공개 모드는 하프이닝 단위까지 허용하며 `level`은 1~5 양자화 값이다. `tensionCurve`에는 원 `base_score`, 축 눈금 숫자, 경기 간 비교용 절대 순위를 포함하지 않는다. 포인트에 `eventLabel`·`eventId`·마커·피크 표시 필드도 포함하지 않는다. 보호 모드의 사건 표시는 `GET /api/games/{id}/events`가 전담하며, 그래프와 `경기 흐름` 목록은 서로 연결하지 않는다.

```jsonc
// GET /api/games/{id}?mode=protected (종료)
{
  "gameId": 5058990, "status": "STATUS_FINAL", "displayMode": "PROTECTED",
  "headline": "7회 만루 승부가 등장한, 끝까지 긴장감이 이어진 경기입니다.",
  "tensionCurve": [
    { "inning": 5, "level": 3 },
    { "inning": 6, "level": 4 },
    { "inning": 7, "level": 5 }
  ]
}

// GET /api/games/{id}?mode=revealed (종료)
{
  "gameId": 5058990, "status": "STATUS_FINAL", "displayMode": "REVEALED",
  "headline": "LAD가 SF를 5-3으로 이긴 경기입니다.",
  "finalScore": { "home": 5, "away": 3 },
  "inningScores": {
    "away": [0, 0, 0, 1, 0, 0, 2, 0, 0],
    "home": [0, 1, 0, 0, 0, 1, 0, 3, null]
  },
  "scoringSummary": [
    { "inning": 4, "inningType": "Top", "text": "..." }
  ],
  "tensionCurve": [
    { "inning": 8, "inningType": "Bottom", "level": 5 }
  ]
}
```

`scoringSummary`는 외부 API의 `scoring_summary` 원문을 직접 전달하지 않고, `plays`에서 `scoring_play=true`인 행의 `text`로 파생한 득점 play 목록이다. 보호 모드에는 포함하지 않는다.

```jsonc
// GET /api/games/{id}/events?mode=PROTECTED
{
  "events": [
    { "eventId": 91, "eventType": "pressure_bases_loaded", "inning": 7,
      "label": "만루 승부",
      "copy": "7회 만루 상황에서 긴 승부가 이어졌습니다.",
      "observedAt": "2026-07-07T02:31:12Z" }
  ]
}

// GET /api/games/{id}/events?mode=REVEALED
{
  "events": []
}

// GET /api/games/{id}/recent-plays?mode=REVEALED
{
  "plays": [
    { "playId": 312, "inning": 8, "inningType": "Top",
      "text": "Marsh가 루킹 삼진을 당했습니다.",
      "translated": true,
      "score": { "home": 3, "away": 4 },
      "observedAt": "2026-07-07T02:35:12Z" }
  ]
}
```

이벤트 API의 `copy`는 nullable이며, 프론트 폴백은 `label`이다. 공개 모드는 이벤트 타임라인을 사용하지 않으므로 `mode=REVEALED`이면 빈 목록을 반환한다. 보호 모드(`mode=PROTECTED`)는 `game_events` 전체가 아니라 `is_timeline_highlight=true`인 하이라이트 이벤트만 반환한다(추천 점수 급변 순간 단위. 상세는 AI_COPY.md §2·§6).

최근 플레이 API는 `plays`의 `type=Play Result` 중 화면 필수값이 있는 전체 건을 `play_order DESC`로 반환한다. 진행 중·종료 경기 모두 건수 제한을 두지 않으며, 타석 결과 필터는 메모리 필터가 아니라 DB 조회 조건으로 적용한다. `text`는 프론트가 그대로 표시하는 완성 문구다. 저장된 한국어 번역이 있으면 `translated=true`로 반환하고, 아직 없으면 원문을 `translated=false`로 임시 반환한다. 보호 모드와 알 수 없는 `mode`는 빈 목록을 반환한다.

### POST `/ai/play-translation`

단일 MLB play result 원문을 한국어로 번역한다.

이 API는 공개 모드 최근 플레이 표시용이며, 현재는 `REVEALED` 모드와 `targetLanguage=ko`만 허용한다.

#### Request

```json
{
  "gameId": 5059041,
  "playId": 312,
  "mode": "REVEALED",
  "contextHash": "play-312-v1",
  "sourceText": "Soto singled to center.",
  "targetLanguage": "ko"
}
```

#### Success Response

번역 생성과 번역 검수 guard를 모두 통과한 경우 `translatedText`를 반환한다.

```json
{
  "translatedText": "Soto, 중견수 방면 안타",
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "play-312-v1"
}
```

#### Guard Failure Response

번역문이 원문 `sourceText`의 핵심 사실을 보존하지 못한 경우 `translatedText`를 반환하지 않는다.

예를 들어 원문이 `single`인데 AI 번역문이 `홈런`으로 생성된 경우 guard 실패로 처리한다.

```json
{
  "violations": [
    "MISSING_EVENT:SINGLE",
    "ADDED_RESULT:HOME_RUN"
  ],
  "fallbackUsed": false,
  "contextHash": "play-312-v1"
}
```

`response_model_exclude_none=true` 정책으로 인해 실패 응답에서는 `translatedText` 필드가 제외된다.

#### Failure Policy

- ai-service는 fallback 번역을 생성하지 않는다.
- 생성 실패, 응답 필드 누락, guard 실패 모두 `fallbackUsed=false`를 반환한다.
- 실패 응답에서는 `contextHash`를 그대로 반환한다.
- 실패 응답에서는 저장 가능한 번역문이 없으므로 `translatedText`를 반환하지 않는다.

#### Main Violations

| Code | Meaning |
| --- | --- |
| `SOURCE_TEXT_EMPTY` | 원문이 비어 있음 |
| `TRANSLATED_TEXT_EMPTY` | 번역문이 비어 있음 |
| `MULTIPLE_SENTENCES` | 번역문이 여러 문장으로 생성됨 |
| `MISSING_PLAYER_NAME:{name}` | 원문 선수명이 번역문에서 누락됨 |
| `ADDED_PLAYER_NAME:{name}` | 원문에 없는 영문 선수명이 번역문에 추가됨 |
| `MISSING_NUMBER:{number}` | 원문 숫자가 번역문에서 누락됨 |
| `ADDED_NUMBER:{number}` | 원문 또는 용어집 규칙으로 설명되지 않는 숫자가 번역문에 추가됨 |
| `MISSING_EVENT:{outcomeCode}` | YAML 용어집에 매칭된 이벤트의 필수 한국어 표현이 번역문에 보존되지 않음 |
| `MISSING_EVENT:SINGLE` | 원문 single 이벤트가 안타로 보존되지 않음 |
| `MISSING_EVENT:DOUBLE` | 원문 double 이벤트가 2루타로 보존되지 않음 |
| `MISSING_EVENT:TRIPLE` | 원문 triple 이벤트가 3루타로 보존되지 않음 |
| `MISSING_EVENT:HOME_RUN` | 원문 home run 이벤트가 홈런으로 보존되지 않음 |
| `MISSING_EVENT:STRIKEOUT` | 원문 strikeout 이벤트가 삼진으로 보존되지 않음 |
| `MISSING_EVENT:STRIKEOUT_SWINGING` | 원문 swinging strikeout 세부 정보가 보존되지 않음 |
| `MISSING_EVENT:STRIKEOUT_LOOKING` | 원문 looking strikeout 세부 정보가 보존되지 않음 |
| `MISSING_DIRECTION:LEFT` | 좌측 방향 표현이 보존되지 않음 |
| `MISSING_DIRECTION:CENTER` | 중앙 방향 표현이 보존되지 않음 |
| `MISSING_DIRECTION:RIGHT` | 우측 방향 표현이 보존되지 않음 |
| `ADDED_RESULT:HOME_RUN` | 원문에 없는 홈런 결과가 추가됨 |
| `ADDED_RESULT:COME_BACK` | 원문에 없는 역전 표현이 추가됨 |
| `ADDED_RESULT:WALK_OFF` | 원문에 없는 끝내기 표현이 추가됨 |
| `ADDED_RESULT:WIN` | 원문에 없는 승리 표현이 추가됨 |
| `ADDED_RESULT:LOSS` | 원문에 없는 패배 표현이 추가됨 |
| `ADDED_RESULT:SCORE` | 원문에 없는 득점 표현이 추가됨 |
| `ADDED_RESULT:RUN_ALLOWED` | 원문에 없는 실점 표현이 추가됨 |
| `ADDED_RESULT:LEAD` | 원문에 없는 리드 표현이 추가됨 |
| `ADDED_COMMENTARY:{expression}` | YAML `global_forbidden_expressions`에 등록된 평가·감정·해설 표현이 번역문에 포함됨 |


### 1.3 관심 선수 검색·등록 계약

`players` 테이블은 지금까지 poller 파이프라인(추적 경기의 라인업·plays)에서만 채웠다. 관심 선수 기능은 추적 경기에 없던 선수도 등록 대상이므로, `players`는 "추적 경기에 등장한 선수만 존재한다"는 불변식을 버리고 **선수 신원 마스터 겸 관심 선수 FK 대상**으로 확장한다. 소비처(홈·경기 상세·AI 문구)는 특정 `player_id`로만 조인하므로 여분 행이 있어도 영향이 없다.

검색과 저장의 책임을 분리한다.

| 구분 | 정책 |
|---|---|
| 검색 `GET /api/players?search=` | **read-only, DB 쓰기 없음.** balldontlie 이름 검색이 커버리지 원천이다(로컬 `players`는 관측된 부분집합이라 검색 완전성을 보장하지 못한다). 검색어 정규화 → 인메모리 TTL 캐시(짧은 만료, 영속 테이블 아님) → 미스 시 외부 호출 → 동일 ID 로컬 정보만 보조 병합. 외부 장애 시 로컬 결과로 폴백하고 응답에 불완전 상태(예: `complete=false`)를 표시해 "외부 장애"와 "실제 0건"을 구분한다. 쿼터 보호는 최소 검색어 길이·프론트 디바운스·동일 키워드 single-flight·0건 짧은 캐시로 한다. |
| 등록 `PUT /api/me/preferences`(`selectedPlayerIds`) | 저장 시점에만 `players` upsert. 선택된 ID가 검색 캐시에 있으면 재사용, 없으면 `getPlayers(ids)`로 검증·조회한다(클라이언트 임의 ID 삽입 차단). **선수 upsert와 `user_favorite_players` insert는 한 트랜잭션**으로 처리한다. |

필수 안전장치.

- **최대 5명 동시성**: 앱 레벨 `count < 5` 확인만으로는 동시 요청이 모두 통과할 수 있다. 사용자 단위 잠금으로 (이미 등록 시 멱등 성공 → 개수 재확인 → upsert → insert)를 직렬화한다.
- **`team_id`**: 자유계약·은퇴 선수는 팀이 없거나 로컬 `teams`에 없을 수 있다. 로컬 `teams`에 존재하는 팀만 FK 연결하고 나머지는 `null`로 저장한다. 팀 정보 때문에 등록 전체가 실패하면 안 된다.
- **null 덮어쓰기 금지**: 외부 응답의 null/빈 값이 기존 정상 값을 덮어쓰지 않는다.
- **최신성 한계**: 등록으로 이름까지 채워진 행은 기존 보강 배치(`full_name IS NULL` 대상) 갱신 범위 밖이다. 트레이드 등 변경은 **재등록 또는 파이프라인 재관측 시점까지만** 반영한다. 주기 갱신 배치는 MVP 범위에서 제외한다.

## 2. SSE 이벤트

payload에는 점수·순위·결과 데이터를 싣지 않는다. 클라이언트는 신호 수신 즉시 해당 REST를 재조회한다(개인화·스포일러 필터링은 항상 서버 REST 응답에서 적용). `notification_created`는 인증된 연결에만 발행한다.

| 이벤트 | payload | 클라이언트 동작 |
|---|---|---|
| `ranking_changed` | `{ sequence, generatedAt }` | 홈 랭킹 재조회 |
| `game_updated` | `{ gameId, sequence, generatedAt }` | 보고 있는 경기면 상세·이벤트 재조회(현재 mode 유지) |
| `notification_created` | `{ notificationId }` | 알림 목록 재조회, 토스트 표시 |

### 2.1 연결·인증·재연결

- 로그인 연결: `POST /api/sse/token`으로 1회용 토큰(Redis, TTL 60초)을 발급받아 `GET /api/sse?token=`으로 연결한다. 액세스 토큰을 URL에 싣지 않기 위한 분리다(브라우저 히스토리·프록시 로그 노출 방지). 토큰은 연결 수립 시 소모된다.
- 비로그인 연결: 토큰 없이 연결하며 `ranking_changed`·`game_updated`만 수신한다.
- 하트비트: 서버가 25초 간격 SSE 코멘트를 보내 유휴 연결 단절을 방지한다.
- 연결 수명: 서버는 연결당 최대 60분 후 종료한다. 액세스 토큰 만료는 수립된 연결을 끊지 않는다.
- 재연결: `Last-Event-ID`는 사용하지 않는다. payload가 재조회 신호뿐이므로, 클라이언트 SSE 훅이 연결 오류 시 새 토큰을 발급받아 재연결하고 관련 REST를 1회 재조회하면 상태가 복구된다.
- `sequence`: 이벤트 종류별로 api 인스턴스 내에서 단조 증가하는 진단용 카운터다. 서버 재시작 시 초기화되며, 클라이언트 동작의 근거로 쓰지 않는다.
- 연결 수 상한: 서버 동시 연결 수 상한(기본 1000)을 초과한 구독 요청은 503으로 거절한다.

## 3. 경기 전환 추천 (`switchSuggestion`)

알림 파이프라인을 타지 않는다. `GET /api/games/{id}` 응답에 포함한다.

- 판정: 서버가 Redis 랭킹에서 "현재 경기보다 `watch_score` 20점 이상 높고 70 이상"인 경기를 찾는다. 점수는 서버 내부에서만 사용.
- 응답: `switchSuggestion: { gameId, matchup, latestTag } | null`
- 쿨다운: 같은 후보 15분 1회, 로그인 사용자는 Redis 키(사용자별), 비로그인은 클라이언트 보조.

## 4. 모듈 인터페이스 (팀 계약 지점)

| 인터페이스 | 제공 → 사용 | 시그니처·내용 |
|---|---|---|
| domain 읽기 | 예은 → 전원 | JPA 엔티티 읽기 전용. 스키마 변경은 예은만 |
| `ScoreQueryService` | 예은 → 민석 | `getLatestSignals(gameId)` → `{ latestTag, phase, situation, updatedAt }`. **점수 숫자는 계약에 없음** |
| `AiCopyReader` | 창현 → 전원 | `getCopy(gameId, purpose, mode)` — `FINAL_HEADLINE` 전용. 검수 통과 헤드라인이 있으면 문구를 반환하고, 저장된 문구가 없거나 미생성이면 `null`을 반환한다. |
| 보호 이벤트 문구 조회 | 예은 → 민석 | `game_events.copy_protected` 직접 조회. 보호 모드 이벤트 API가 단일 원천이다. |
| 최근 플레이 번역 조회 | 예은 → 민석 | `plays.text_ko` 우선, 없으면 `plays.text` 폴백. 공개 모드 최근 플레이 API가 단일 원천이다. |
| `UserPreferenceReader` | 윤호 → 예은(홈 가산)·api(알림 fan-out, 전환 쿨다운) | 이메일로 관심 팀 ID 집합·관심 선수 ID 집합·알림 설정 조회. 홈 가산은 관심 팀 1개 이상 일치 시 +10, 라인업의 관심 선수 1명 이상 일치 시 +5이며 각 조건은 개수와 무관하게 한 번만 반영한다. |
| `SseEventPublisher` | api 공통 | 이벤트 3종 발행 단일 창구 |
| AI 생성 트리거 | 창현 → 예은(scorer) | `FINAL_HEADLINE`, `EVENT_COPY` 비동기 생성 요청 인터페이스. ai-service 호출, `contextHash` 검증(모드별 해시 컬럼), 검수 통과 문구 저장 담당 |
| `AiCopyContextReader` | 예은 → 창현 | `finalHeadlineContext(gameId, mode)`·`eventCopyContext(gameId, eventId, mode)` — safeContext 필드와 `contextHash`(SHA-256, 예은 측 정규화 계산)를 반환. 빈 값은 "생성 대상 아님". 외부 REST GET 컨텍스트 API는 폐기. 상세는 `AI_COPY.md` §4.0 |
| `notify.events` | scorer·poller → 윤호 | 알림 이벤트. 서버가 고정 템플릿으로 완성한 `message` 전달 |

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
    "basesLoaded": true, "scoringPosition": true },
  "plateAppearances": [
    { "paNumber": 47, "inning": 7, "inningType": "top",
      "batterId": 492, "pitcherId": 713, "outs": 2,
      "runnerOnFirst": true, "runnerOnSecond": true, "runnerOnThird": true,
      "pitches": [
        { "pitchNumber": 8, "pitcherPitchCount": 101,
          "releaseSpeed": 92.1, "exitVelocity": 101.4, "barrel": true }
      ] }
  ] }

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
- `plateAppearances`: PA 원본 전체가 아니라 이벤트 추출에 필요한 사실만 전달한다. 결과·설명 원문은 포함하지 않는다. scorer는 이 값으로 긴 타석·투수 흔들림·강한 타구 이벤트를 판정한다.
- 하위호환: scorer는 `situation`과 `plateAppearances` 유무와 무관하게 동작해야 하며, poller·scorer 배포 순서나 브로커에 남은 구버전 task에 안전하다. `plateAppearances` 누락·null은 빈 목록으로 처리한다.
- `PREGAME` task: poller가 경기 전 입력이 갱신될 때(선발 확정·변경, `odds_snapshots` 기록, `standings` 일 배치 반영, `PREGAME_NEAR` 진입) 발행한다. scorer는 DB의 `lineups`·`odds_snapshots`·`standings`·`player_season_stats`만 읽어 `pregame_score`를 계산하고 `games.pregame_score`·`pregame_inputs`를 덮어쓴다. 최신 입력 기준 재계산이므로 중복·재전달에 멱등이며, `watch_scores`에는 행을 남기지 않는다. 선발 시즌 스탯의 온디맨드 외부 조회는 poller가 task 발행 전에 수행해 `player_season_stats`에 적재한다(외부 API 호출은 poller로 한정).

발행 보장: poller는 `ScoreTask` 원본 payload와 `PENDING` 상태를 `score_task_outbox` 한 행으로 먼저 커밋한다. 커밋 직후 `score.tasks` 발행을 시도하고, 실패하면 지수 백오프로 PENDING 행을 재발행한다. 애플리케이션 재시작 뒤에도 poller 또는 scorer 역할이 미발행 행을 다시 조회한다. 발행 성공 직후 상태 반영 전에 장애가 나면 중복 전달될 수 있다.

재전달 멱등: `watch_scores`의 UNIQUE(`game_id`, `computed_at`) 충돌 시 scorer는 해당 사이클 저장을 건너뛴다(`computed_at` = `observedAt`).

종료 task 멱등: `lifecycleState`가 `FINAL`·`DONE`·`SUSPENDED_POSTPONED`인 종료 task는 scorer가 종료 정리(`score:rank:live` 제거·`signal:ranking` 발행)를 경기 상태 전이 기준으로 1회만 수행한다. 이미 정리된 경기의 종료 task가 중복·역순·재전달로 도착해도 재실행하지 않는다. 종료 task 유실 대비로 poller는 상시 감시를 통해 상태 전이가 확정될 때까지 종료 task를 재발행할 수 있다.

### 5.2 Redis 키

| 키 | 타입 | 내용 |
|---|---|---|
| `score:rank:live` | ZSET | 진행 중 경기 랭킹 (member=game_id, score=watch_score) |
| `game:{id}:live` | HASH | 현재 점수·이닝·최신 태그 캐시 (내부 전용) |
| `game:{id}:copy:FINAL_HEADLINE:{mode}` | STRING | 종료 경기 AI 헤드라인 읽기 캐시. 원본은 `games.final_headline_protected`·`games.final_headline_revealed` |
| `notify:armed:{gameId}` | STRING | 급상승 히스테리시스 상태 |
| `notify:cooldown:global` | STRING | 전역 15분 레이트리밋 |
| `switch:cooldown:{userId}:{gameId}` | STRING | 전환 안내 쿨다운 |
| `sse:token:{token}` | STRING | SSE 연결용 1회용 토큰 (TTL 60초) |
| (pub/sub) `signal:ranking`, `signal:game:{id}` | 채널 | 재조회 신호. api가 SSE로 중계 |

보호 이벤트 문구(`EVENT_COPY`)와 최근 플레이 번역(`PLAY_TRANSLATION`)은 Redis 캐시를 두지 않고 각각 `game_events`, `plays`에서 직접 조회한다.

라이프사이클 정리: 경기가 LIVE에서 이탈하면(`FINAL`·`DONE`·`SUSPENDED_POSTPONED`) scorer가 poller의 종료 ScoreTask(`lifecycleState`)를 받아 `score:rank:live`에서 해당 경기를 제거하고 `signal:ranking`을 발행한다. `game:{id}:live`·`game:{id}:copy:FINAL_HEADLINE:*`는 TTL로 소멸한다.
