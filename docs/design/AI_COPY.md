# AI 문구 설계

## 1. 목적과 산출물

AI 문구는 서버가 만든 안전한 context만 입력으로 사용한다. 생성 결과가 금지 표현이나 점수·승패·우세 정보를 포함하면 저장하지 않는다.

AI 산출물은 종료 경기 헤드라인(`FINAL_HEADLINE`)만 둔다. 보호 모드용 1개와 공개 모드용 1개를 별도로 생성·저장한다. 진행 중·예정 경기와 다시보기 구간에는 AI 문구를 생성하지 않는다.

기본 문구는 두지 않는다. 저장된 AI 문구가 없으면 `AiCopyReader.getCopy`는 `null`을 반환하고, 프론트는 헤드라인 영역을 조건부 렌더링한다.

| 상황 | purpose | mode | 저장 컬럼 |
|---|---|---|---|
| 종료 경기 헤드라인 · 보호 모드 | `FINAL_HEADLINE` | `PROTECTED` | `games.final_headline_protected` |
| 종료 경기 헤드라인 · 공개 모드 | `FINAL_HEADLINE` | `REVEALED` | `games.final_headline_revealed` |

## 2. 생성 트리거와 흐름

HTTP 요청 경로에서 LLM을 호출하지 않는다. 문구 생성은 사용자 요청 경로가 아니라 데이터 갱신 시점에 비동기로 수행한다. Spring Boot의 ai-service 호출 8초 데드라인은 사용자 화면 요청을 기다리게 하는 시간이 아니라, 백그라운드 문구 생성 요청을 포기하는 상한이다.

- scorer가 경기 종료 정리를 수행할 때 `FINAL_HEADLINE`을 `mode=PROTECTED`·`mode=REVEALED` 두 번 요청한다. Spring Boot 기준 데드라인(8초) 내 생성+검수 통과 시 저장한다. 실패·검수 불통과·`contextHash` 불일치 시 저장하지 않는다. 두 모드 요청은 서로 독립적이라, 한쪽이 실패해도 다른 쪽 저장에는 영향을 주지 않는다.
- 사용자 응답에서는 저장된 AI 문구가 있으면 그 문구를 반환하고, 없으면 `headline=null`을 반환한다. 프론트는 헤드라인 영역을 렌더링하지 않되 다른 영역의 높이와 레이아웃은 유지한다.
- AI 문구 DB 저장에 성공하면 scorer는 해당 경기의 `game_updated` 신호를 재발행한다. 클라이언트는 기존 상세 재조회 흐름으로 빈 공간이 문구로 자연스럽게 교체되는 경험을 제공한다.
- 종료 경기 AI 문구 재생성 정책은 v1에서 두지 않는다. 실패 시 빈 공간 상태가 유지되며, 재시도 배치 도입 여부는 운영 관측 후 결정한다.

## 3. 저장 위치

| 문구 종류 | 저장 |
|---|---|
| 종료 경기 헤드라인 · 보호 모드 | PostgreSQL `games.final_headline_protected` + Redis 읽기 캐시 |
| 종료 경기 헤드라인 · 공개 모드 | PostgreSQL `games.final_headline_revealed` + Redis 읽기 캐시 |

두 컬럼은 모두 nullable이다. 저장된 값이 없으면 API 응답의 헤드라인 필드는 `null`이다.

## 4. ai-service 요청 컨텍스트

Spring Boot는 원본 경기 데이터를 그대로 보내지 않고, 화면 노출이 가능한 값만 `safeContext`로 매핑해 ai-service에 전달한다. `safeTags`에는 내부 신호명이 아니라 보호 표기 문자열만 넣는다.

`mode=PROTECTED` 요청은 점수·이닝 초/말·play 원문·타석 결과·우세 팀·승패 결과를 어떤 경우에도 포함하지 않는다. `mode=REVEALED` 요청만 예외로, 이미 공개 모드에서 노출 중인 최종 점수·승패를 `safeContext.finalScore`·`safeContext.winner`에 담아 전달할 수 있다. 그 외 필드(초/말, play 원문, 타석 결과 등)는 REVEALED 요청에도 포함하지 않는다.

Spring Boot 필드 매핑 기준은 아래와 같다.

| Spring field | ai-service field |
|---|---|
| `status` | `safeContext.gameStatus` |
| `periodLabel` | `safeContext.inningPhase` |
| `reasonTags` | `safeContext.safeTags` |
| `spoilerSafeSignals` | `safeContext.reasonCodes` |

`recentPlays`, `teams`, `startTime`, 원본 `purpose` 같은 경기 원본 필드는 문구 생성 요청에 넣지 않는다.

`safeContext.gameStatus`는 Spring 상태값을 그대로 사용한다. 종료 경기 문구만 생성하므로 `STATUS_FINAL`만 생성 대상이다.

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
    "reasonCodes": ["late_or_extra"]
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
    "finalScore": { "home": 5, "away": 3 },
    "winner": "home"
  }
}
```

## 5. scorer ↔ ai-service HTTP 계약

- 엔드포인트: `POST /ai/final-headline`
- 요청: `gameId`, `mode`(`PROTECTED`/`REVEALED`), `contextHash`(필수), `safeContext`
- 응답: 검수 통과 여부(`spoilerSafe`), 생성 문구(`safeTitle`), fallback 사용 여부(`fallbackUsed`), 위반 항목(`violations`), 요청과 같은 `contextHash`를 포함한다. `violations: []`, `fallbackUsed: false` 같은 기본값 필드도 생략하지 않는다.
- 검수 기준: `mode=PROTECTED` 응답은 결과·방향성 표현(SPOILER_POLICY.md §6)이 포함되면 `spoilerSafe=false`로 반려한다. `mode=REVEALED` 응답은 `safeContext.finalScore`·`winner`로 전달한 실제 결과와 일치하는 점수·승패 언급만 허용한다. 요청에 없는 사실을 지어내면 반려한다.
- 범위 밖 계약: `notification-text`, `spoiler-check` 등 다른 엔드포인트는 현재 계약 범위에 포함하지 않는다.
- 타임아웃: Spring Boot → ai-service 호출은 8초, ai-service → OpenAI 호출은 6초로 제한한다. OpenAI 응답 이후 JSON 파싱·스포일러 검수·응답 반환 시간이 필요하므로 ai-service 내부 timeout을 Spring Boot 호출 timeout보다 짧게 둔다.
- ai-service는 무상태다. 캐시·DB에 직접 쓰지 않고 생성·검수 결과를 응답으로만 반환한다.
- ai-service는 대체 문구를 생성하거나 반환하지 않는다. 현재 계약에서 `fallbackUsed`는 항상 `false`다.
- 저장 책임: 종료 산출물(`games.final_headline_protected`, `games.final_headline_revealed`)은 scorer가 종료 정리 트랜잭션에서 저장한다. ai-service는 저장 여부를 판단하지 않는다.
- 저장 조건: `spoilerSafe=true`, `fallbackUsed=false`, `contextHash` 일치인 응답만 저장한다.
- stale write 방지: 응답의 `contextHash`가 대상의 최신 컨텍스트 해시와 일치할 때만 저장한다. 늦게 도착한 응답이 더 새로운 문구를 덮어쓰지 않게 한다.

### 5.1 응답 필드

`passed`/`text`/`reason` 같은 공통 필드 대신 응답 필드를 명확히 고정한다.

```jsonc
// mode=PROTECTED
{
  "spoilerSafe": true,
  "safeTitle": "다시 볼 만한 흐름이 있었던 경기",
  "tags": ["후반 긴장 구간"],
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "..."
}

// mode=REVEALED
{
  "spoilerSafe": true,
  "safeTitle": "LAD가 SF를 5-3으로 이긴 경기",
  "tags": ["후반 긴장 구간"],
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "..."
}
```

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

`AiCopyReader.getCopy(gameId, purpose, mode)`는 종료 경기의 검수 통과 AI 문구가 있으면 해당 문구를 반환하고, 저장된 문구가 없거나 미생성이면 `null`을 반환한다. 소비자(홈·상세)는 `null`을 조건부 렌더링 기준으로 사용한다.

홈 카드와 경기 상세 보호 모드는 `mode=PROTECTED` 문구를, 경기 상세 공개 모드는 `mode=REVEALED` 문구를 조회한다. `null`이면 헤드라인 영역을 렌더링하지 않는다.
