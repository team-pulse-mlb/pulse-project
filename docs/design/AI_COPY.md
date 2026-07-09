# AI 문구 설계

## 1. 목적과 산출물

AI 문구는 서버가 만든 안전한 context만 입력으로 사용한다. 생성 결과가 금지 표현이나 점수·승패·우세 정보를 포함하면 저장하지 않는다.

AI 산출물은 종료 경기 헤드라인(`FINAL_HEADLINE`)과 이벤트 타임라인 문구(`EVENT_COPY`)다. 헤드라인은 종료 경기 전용이며, 이벤트 문구는 라이브 중 `game_events` 영속 직후 생성한다. 예정 경기와 다시보기 구간에는 별도 AI 문구를 생성하지 않는다.

기본 문구는 두지 않는다. 저장된 헤드라인이 없으면 `AiCopyReader.getCopy`는 `null`을 반환하고, 프론트는 헤드라인 영역을 조건부 렌더링한다. 저장된 이벤트 문구가 없으면 이벤트 API의 `copy`는 `null`이며, 프론트는 이벤트 `label`을 폴백으로 표시한다.

| 상황 | purpose | mode | 저장 컬럼 |
|---|---|---|---|
| 종료 경기 헤드라인 · 보호 모드 | `FINAL_HEADLINE` | `PROTECTED` | `games.final_headline_protected` |
| 종료 경기 헤드라인 · 공개 모드 | `FINAL_HEADLINE` | `REVEALED` | `games.final_headline_revealed` |
| 이벤트 타임라인 문구 · 보호 모드 | `EVENT_COPY` | `PROTECTED` | `game_events.copy_protected` |
| 이벤트 타임라인 문구 · 공개 모드 | `EVENT_COPY` | `REVEALED` | `game_events.copy_revealed` |

## 2. 생성 트리거와 흐름

HTTP 요청 경로에서 LLM을 호출하지 않는다. 문구 생성은 사용자 요청 경로가 아니라 데이터 갱신 시점에 비동기로 수행한다. Spring Boot의 ai-service 호출 8초 데드라인은 사용자 화면 요청을 기다리게 하는 시간이 아니라, 백그라운드 문구 생성 요청을 포기하는 상한이다.

- scorer가 경기 종료 정리를 수행할 때 `FINAL_HEADLINE`을 `mode=PROTECTED`·`mode=REVEALED` 두 번 요청한다. Spring Boot 기준 데드라인(8초) 내 생성+검수 통과 시 저장한다. 실패·검수 불통과·`contextHash` 불일치 시 저장하지 않는다. 두 모드 요청은 서로 독립적이라, 한쪽이 실패해도 다른 쪽 저장에는 영향을 주지 않는다.
- scorer가 `game_events`를 영속한 직후 `EVENT_COPY`를 요청한다. 보호 안전 이벤트는 `mode=PROTECTED`와 `mode=REVEALED`를 각각 요청하고, `REVEALED_ONLY` 이벤트는 `mode=REVEALED`만 요청한다. 저장 조건과 타임아웃은 `FINAL_HEADLINE`과 동일하다.
- 사용자 응답에서는 저장된 AI 문구가 있으면 그 문구를 반환한다. 헤드라인이 없으면 `headline=null`, 이벤트 문구가 없으면 `copy=null`을 반환한다.
- AI 문구 DB 저장에 성공하면 scorer는 해당 경기의 `game_updated` 신호를 재발행한다. 클라이언트는 기존 상세 재조회 흐름으로 빈 헤드라인 영역이 문구로 채워지거나 이벤트 라벨 폴백이 AI 문구로 교체되는 경험을 제공한다.
- AI 문구 재생성 정책은 v1에서 두지 않는다. 실패 시 `null` 상태가 유지되며, 재시도 배치 도입 여부는 운영 관측 후 결정한다.

## 3. 저장 위치

| 문구 종류 | 저장 |
|---|---|
| 종료 경기 헤드라인 · 보호 모드 | PostgreSQL `games.final_headline_protected` + Redis 읽기 캐시 |
| 종료 경기 헤드라인 · 공개 모드 | PostgreSQL `games.final_headline_revealed` + Redis 읽기 캐시 |
| 이벤트 문구 · 보호 모드 | PostgreSQL `game_events.copy_protected` |
| 이벤트 문구 · 공개 모드 | PostgreSQL `game_events.copy_revealed` |
| 이벤트 문구 context hash | PostgreSQL `game_events.copy_context_hash` |

모든 컬럼은 nullable이다. 저장된 값이 없으면 API 응답의 `headline` 또는 `copy` 필드는 `null`이다. 이벤트 문구는 Redis 캐시를 두지 않고 `game_events`에서 직접 조회한다.

## 4. ai-service 요청 컨텍스트

Spring Boot는 원본 경기 데이터를 그대로 보내지 않고, 화면 노출이 가능한 값만 `safeContext`로 매핑해 ai-service에 전달한다. `safeTags`와 `keyMoments.label`에는 내부 신호명이 아니라 보호 표기 문자열만 넣는다.

`mode=PROTECTED` 요청은 점수·이닝 초/말·play 원문·타석 결과·우세 팀·승패 결과를 어떤 경우에도 포함하지 않는다. `mode=REVEALED` 요청만 예외로, 이미 공개 모드에서 노출 중인 최종 점수·승패 또는 이벤트 근거를 `safeContext`에 담아 전달할 수 있다.

### 4.1 FINAL_HEADLINE 컨텍스트

Spring Boot 필드 매핑 기준은 아래와 같다.

| Spring field | ai-service field |
|---|---|
| `status` | `safeContext.gameStatus` |
| `periodLabel` | `safeContext.inningPhase` |
| `reasonTags` | `safeContext.safeTags` |
| `spoilerSafeSignals` | `safeContext.reasonCodes` |
| `keyMoments` | `safeContext.keyMoments` |

`recentPlays`, `teams`, `startTime`, 원본 `purpose` 같은 경기 원본 필드는 문구 생성 요청에 넣지 않는다. `safeContext.gameStatus`는 Spring 상태값을 그대로 사용한다. 종료 경기 문구만 생성하므로 `STATUS_FINAL`만 생성 대상이다.

```jsonc
// POST /ai/final-headline (mode=PROTECTED)
{
  "gameId": 5058990,
  "mode": "PROTECTED",
  "contextHash": "...",
  "safeContext": {
    "gameStatus": "STATUS_FINAL",
    "inningPhase": "경기 종료",
    "tensionLevel": "HIGH",
    "scoreBand": "RECOMMEND",
    "safeTags": ["후반 긴장 구간"],
    "reasonCodes": ["late_or_extra"],
    "keyMoments": [
      { "inning": 7, "label": "만루 승부" },
      { "inning": 8, "label": "득점권 승부" }
    ]
  }
}

// POST /ai/final-headline (mode=REVEALED)
{
  "gameId": 5058990,
  "mode": "REVEALED",
  "contextHash": "...",
  "safeContext": {
    "gameStatus": "STATUS_FINAL",
    "inningPhase": "경기 종료",
    "tensionLevel": "HIGH",
    "scoreBand": "RECOMMEND",
    "safeTags": ["후반 긴장 구간"],
    "reasonCodes": ["late_or_extra"],
    "keyMoments": [
      { "inning": 7, "label": "만루 승부" }
    ],
    "finalScore": { "home": 5, "away": 3 },
    "winner": "home"
  }
}
```

### 4.2 EVENT_COPY 컨텍스트

이벤트 문구는 이벤트 API에서 실제로 노출 가능한 필드만 전달한다.

| mode | safeContext |
|---|---|
| `PROTECTED` | `{ eventType, label, inning }` |
| `REVEALED` | 보호 필드 + `{ inningType, batter, pitcher, evidence }` |

`evidence`는 원본 `payload`를 문구 생성에 필요한 요약 형태로 제한한 값이다. 보호 모드에는 `payload`, 선수명, 초/말을 포함하지 않는다.

```jsonc
// POST /ai/event-copy (mode=PROTECTED)
{
  "gameId": 5059041,
  "eventId": 91,
  "mode": "PROTECTED",
  "contextHash": "...",
  "safeContext": {
    "eventType": "pressure_bases_loaded",
    "label": "만루 승부",
    "inning": 7
  }
}

// POST /ai/event-copy (mode=REVEALED)
{
  "gameId": 5059041,
  "eventId": 91,
  "mode": "REVEALED",
  "contextHash": "...",
  "safeContext": {
    "eventType": "pressure_bases_loaded",
    "label": "만루 승부",
    "inning": 7,
    "inningType": "Top",
    "batter": "Kim",
    "pitcher": "Steele",
    "evidence": { "outs": 2, "balls": 3, "strikes": 2 }
  }
}
```

## 5. scorer ↔ ai-service HTTP 계약

| 항목 | `POST /ai/final-headline` | `POST /ai/event-copy` |
|---|---|---|
| 요청 | `gameId`, `mode`(`PROTECTED`/`REVEALED`), `contextHash`, `safeContext` | `gameId`, `eventId`, `mode`(`PROTECTED`/`REVEALED`), `contextHash`, `safeContext` |
| 응답 | `spoilerSafe`, `safeTitle`, `violations`, `fallbackUsed`, `contextHash` | `spoilerSafe`, `safeTitle`, `violations`, `fallbackUsed`, `contextHash` |
| 저장 위치 | `games.final_headline_*` | `game_events.copy_*`, `copy_context_hash` |
| 저장 책임 | scorer | scorer |

- 응답 필드는 `violations: []`, `fallbackUsed: false` 같은 기본값도 생략하지 않는다.
- 검수 기준: `mode=PROTECTED` 응답은 결과·방향성 표현(SPOILER_POLICY.md §6)이 포함되면 `spoilerSafe=false`로 반려한다. `mode=REVEALED` 응답은 `safeContext`로 전달한 실제 결과와 근거 범위 안의 언급만 허용한다. 요청에 없는 사실을 지어내면 반려한다.
- 범위 밖 계약: 알림·토스트·`switchSuggestion` 문구는 LLM을 사용하지 않는다. 태그별 고정 템플릿으로 서버가 완성 문자열을 조립한다.
- 타임아웃: Spring Boot → ai-service 호출은 8초, ai-service → OpenAI 호출은 6초로 제한한다. OpenAI 응답 이후 JSON 파싱·스포일러 검수·응답 반환 시간이 필요하므로 ai-service 내부 timeout을 Spring Boot 호출 timeout보다 짧게 둔다.
- ai-service는 무상태다. 캐시·DB에 직접 쓰지 않고 생성·검수 결과를 응답으로만 반환한다.
- ai-service는 대체 문구를 생성하거나 반환하지 않는다. 현재 계약에서 `fallbackUsed`는 항상 `false`다.
- 저장 조건: `spoilerSafe=true`, `fallbackUsed=false`, `contextHash` 일치인 응답만 저장한다.
- stale write 방지: 응답의 `contextHash`가 대상의 최신 컨텍스트 해시와 일치할 때만 저장한다. 늦게 도착한 응답이 더 새로운 문구를 덮어쓰지 않게 한다.

### 5.1 응답 필드

`passed`/`text`/`reason` 같은 공통 필드 대신 응답 필드를 명확히 고정한다.

```jsonc
// FINAL_HEADLINE, mode=PROTECTED
{
  "spoilerSafe": true,
  "safeTitle": "7회 만루 승부가 등장한, 끝까지 긴장감이 이어진 경기입니다.",
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "..."
}

// EVENT_COPY, mode=PROTECTED
{
  "spoilerSafe": true,
  "safeTitle": "7회 만루 상황에서 긴 승부가 이어졌습니다.",
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "..."
}

// mode=REVEALED
{
  "spoilerSafe": true,
  "safeTitle": "LAD가 SF를 5-3으로 이긴 경기입니다.",
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "..."
}
```

보호 모드 문구는 이닝 숫자와 보호 표기만 사용한다. "중반 이후 득점권 승부가 반복되며 흐름이 크게 출렁인 경기입니다."처럼 결과·방향·팀 유불리를 드러내지 않는 표현만 허용한다.

검수 실패나 생성 실패 시에는 생성 문구 필드를 비우거나 생략하고, 실패 상태와 위반 항목을 반환한다. 생성 실패 사유도 `violations`에 코드 형태로 넣어 Spring Boot가 저장 금지 사유를 남길 수 있게 한다.

```jsonc
{
  "spoilerSafe": false,
  "violations": ["FORBIDDEN_WORD:홈런"],
  "fallbackUsed": false,
  "contextHash": "..."
}
```

## 6. 소비 인터페이스

`AiCopyReader.getCopy(gameId, purpose, mode)`는 `FINAL_HEADLINE` 전용이다. 종료 경기의 검수 통과 헤드라인이 있으면 해당 문구를 반환하고, 저장된 문구가 없거나 미생성이면 `null`을 반환한다. 소비자(홈·상세)는 `null`을 조건부 렌더링 기준으로 사용한다.

이벤트 문구는 `AiCopyReader`를 거치지 않는다. `GET /api/games/{id}/events`가 `game_events.copy_protected` 또는 `copy_revealed`를 직접 조회해 `copy`로 반환한다. `copy=null`이면 프론트는 같은 이벤트의 `label`을 폴백으로 표시한다.
