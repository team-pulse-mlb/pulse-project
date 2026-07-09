# AI 문구 설계

## 1. purpose와 기본 문구

AI 문구는 서버가 만든 안전한 context만 입력으로 사용한다. 생성 결과가 금지 표현이나 점수·승패·우세 정보를 포함하면 기본 문구로 대체한다.

헤드라인·요약은 종료 경기 산출물에만 적용한다. 진행 중·예정 경기 카드와 진행 중 상세 화면은 추천 태그와 보호 모드 정보만 사용한다.

다시보기 구간 AI 요약(`REPLAY_SUMMARY`)은 스포일러 검수를 통과한 안전 문구이므로 보호 모드와 공개 모드 모두에서 같은 문구를 노출한다.

종료 경기 AI 헤드라인(`FINAL_HEADLINE`)은 보호 모드용과 공개 모드용을 **따로 생성·저장**한다. 보호 모드용은 기존과 동일하게 결과를 드러내지 않는 안전 문구다. 공개 모드용은 사용자가 이미 공개 전환을 선택한 상태에서만 노출되므로 최종 점수·승패 같은 실제 결과를 반영할 수 있다. 두 문구 모두 ai-service의 검수를 거치며, 검수 기준(§5)만 모드별로 다르다.

기본 문구는 Spring Boot의 `com.pulse.ai`/`AiCopyReader`에 둔다. ai-service는 AI 문구 후보 생성과 스포일러 검수만 담당하며, fallback 기본 문구를 만들거나 내려주지 않는다.

| 상황 | purpose | mode | 기본 문구 |
|---|---|---|---|
| 종료 경기 헤드라인 · 보호 모드 | `FINAL_HEADLINE` | `PROTECTED` | 다시 볼 만한 흐름이 있었던 경기입니다. |
| 종료 경기 헤드라인 · 공개 모드 | `FINAL_HEADLINE` | `REVEALED` | 최종 결과와 함께 다시 볼 만한 경기입니다. |
| 다시보기 구간 요약 | `REPLAY_SUMMARY` | — (모드 무관, 공통) | 다시 볼 만한 흐름이 이어진 구간입니다. |

## 2. 생성 트리거와 흐름

HTTP 요청 경로에서 LLM을 호출하지 않는다. 문구 생성은 사용자 요청 경로가 아니라 **데이터 갱신 시점에 비동기로** 수행한다. Spring Boot의 ai-service 호출 8초 데드라인은 사용자 화면 요청을 기다리게 하는 시간이 아니라, 백그라운드 문구 생성 요청을 포기하는 상한이다.

- scorer가 경기 종료 정리를 수행할 때 `FINAL_HEADLINE`을 `mode=PROTECTED`·`mode=REVEALED` 두 번 요청하고, 마감된 구간의 `REPLAY_SUMMARY`(모드 무관, 1회) 생성을 비동기로 요청한다. Spring Boot 기준 데드라인(8초) 내 생성+검수 통과 시 저장한다. 실패·검수 불통과·`contextHash` 불일치 시 저장하지 않고 다음 종료 문구 생성 기회에 재시도한다. 두 모드 요청은 서로 독립적이라, 한쪽이 실패해도 다른 쪽 저장에는 영향을 주지 않는다.
- 사용자 응답에서는 저장된 AI 문구가 있으면 그 문구를, 없으면 Spring Boot의 목적별 기본 문구를 즉시 반환한다. 이후 백그라운드 생성이 성공해 저장되면 다음 조회나 SSE 재조회 때 AI 문구로 교체된다.

## 3. 저장 위치

| 문구 종류 | 저장 | 이유 |
|---|---|---|
| 다시보기 구간 요약 (`REPLAY_SUMMARY`) | PostgreSQL (`replay_segments.ai_summary`) + Redis 읽기 캐시 | 확정 산출물. 몇 년 뒤 상세 화면에도 나와야 하고 재생성에 LLM 비용이 들어 "Redis=재계산 가능한 것만" 원칙에 해당하지 않음 |
| 종료 경기 헤드라인 (`FINAL_HEADLINE`) | PostgreSQL (`games.final_headline_protected`, `games.final_headline_revealed`) + Redis 읽기 캐시 | 위와 동일. 모드별로 별도 컬럼에 저장해 조회 시 모드 분기만으로 꺼내 쓴다 |

## 4. ai-service 요청 컨텍스트

Spring Boot는 원본 경기 데이터를 그대로 보내지 않고, 화면 노출이 가능한 값만 `safeContext`로 매핑해 ai-service에 전달한다. `safeTags`에는 내부 신호명이 아니라 보호 표기 문자열만 넣는다.

`REPLAY_SUMMARY`와 `FINAL_HEADLINE`의 `mode=PROTECTED` 요청은 점수·이닝 초/말·play 원문·타석 결과·우세 팀·승패 결과를 어떤 경우에도 포함하지 않는다. `FINAL_HEADLINE`의 `mode=REVEALED` 요청만 예외로, 이미 공개 모드에서 노출 중인 최종 점수·승패를 `safeContext.finalScore`·`safeContext.winner`에 담아 전달할 수 있다. 그 외 필드(초/말, play 원문, 타석 결과 등)는 REVEALED 요청에도 포함하지 않는다.

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
// /ai/final-headline (mode=PROTECTED)
{
  "gameId": 5058990,
  "mode": "PROTECTED",
  "surface": "FINAL_GAME_CARD",
  "language": "ko",
  "maxLength": 80,
  "safeContext": {
    "gameStatus": "STATUS_FINAL",
    "inningPhase": "경기 종료",
    "tensionLevel": "HIGH",
    "scoreBand": "RECOMMEND",
    "safeTags": ["후반 긴장 구간"],
    "reasonCodes": ["late_or_extra"]
  },
  "contextHash": "..."
}

// /ai/final-headline (mode=REVEALED) — finalScore·winner만 추가
{
  "gameId": 5058990,
  "mode": "REVEALED",
  "surface": "FINAL_GAME_DETAIL",
  "language": "ko",
  "maxLength": 80,
  "safeContext": {
    "gameStatus": "STATUS_FINAL",
    "inningPhase": "경기 종료",
    "tensionLevel": "HIGH",
    "scoreBand": "RECOMMEND",
    "safeTags": ["후반 긴장 구간"],
    "reasonCodes": ["late_or_extra"],
    "finalScore": { "home": 5, "away": 3 },
    "winner": "home"
  },
  "contextHash": "..."
}

// /ai/replay-summary
{
  "gameId": 5059041,
  "mode": "PROTECTED",
  "surface": "REPLAY_CARD",
  "language": "ko",
  "maxLength": 80,
  "replaySegmentId": "segment-5059041-001",
  "segmentLabel": "스포일러 없이 다시 보기 좋은 구간",
  "segmentReasonTags": ["후반 긴장 구간", "흐름 변화 가능성"],
  "safeContext": {
    "gameStatus": "STATUS_FINAL",
    "inningPhase": "경기 종료",
    "tensionLevel": "HIGH",
    "scoreBand": "RECOMMEND",
    "safeTags": ["후반 긴장 구간"],
    "reasonCodes": ["late_or_extra"]
  },
  "contextHash": "..."
}
```

## 5. scorer ↔ ai-service HTTP 계약

- 엔드포인트:
  - `POST /ai/final-headline`: 종료 경기 카드·상세용 헤드라인 생성
  - `POST /ai/replay-summary`: 다시보기 구간 제목과 요약 생성
- 요청: 각 엔드포인트는 §4의 `safeContext`와 `contextHash`를 받는다. `/ai/final-headline`은 `mode`(`PROTECTED`/`REVEALED`)에 따라 별도 요청으로 호출한다.
- 응답: 용도별 필드를 사용한다. 공통으로 검수 통과 여부(`spoilerSafe`), fallback 사용 여부(`fallbackUsed`), 위반 항목(`violations`), 요청과 같은 `contextHash`를 포함한다. `violations: []`, `fallbackUsed: false` 같은 기본값 필드도 생략하지 않는다. 실패·검수 불통과 응답에는 Spring Boot 기본 문구를 대신 넣지 않는다.
- 검수 기준(모드별 차이): `/ai/final-headline`의 `mode=PROTECTED` 응답은 결과·방향성 표현(SPOILER_POLICY.md §6)이 포함되면 `spoilerSafe=false`로 반려한다. `mode=REVEALED` 응답은 `safeContext.finalScore`·`winner`로 전달한 실제 결과와 일치하는 점수·승패 언급을 허용한다(단, 요청에 없는 사실을 지어내면 반려). `/ai/replay-summary`는 모드 구분 없이 항상 보호 기준을 적용한다.
- 타임아웃: Spring Boot → ai-service 호출은 8초, ai-service → OpenAI 호출은 6초로 제한한다. OpenAI 응답 이후 JSON 파싱·스포일러 검수·응답 반환 시간이 필요하므로 ai-service 내부 timeout을 Spring Boot 호출 timeout보다 짧게 둔다. 실패·검수 불통과·`contextHash` 불일치 시 저장하지 않고 기존 문구를 유지한다.
- ai-service는 무상태다. 캐시·DB에 직접 쓰지 않고 생성·검수 결과를 응답으로만 반환한다.
- 저장 책임: 종료 산출물(`replay_segments.ai_summary`, `games.final_headline_protected`, `games.final_headline_revealed`)은 scorer가 구간 확정·종료 정리 트랜잭션에서 저장한다. ai-service는 저장 여부를 판단하지 않는다. `FINAL_HEADLINE`의 두 모드 응답은 각각 대응하는 컬럼에 독립적으로 저장한다.
- 저장 조건: `spoilerSafe=true`, `fallbackUsed=false`, `contextHash` 일치인 응답만 저장한다.
- stale write 방지: 응답의 `contextHash`가 대상의 최신 컨텍스트 해시와 일치할 때만 저장한다. 늦게 도착한 응답이 더 새로운 문구를 덮어쓰지 않게 한다.

### 5.1 응답 필드

`passed`/`text`/`reason` 같은 공통 필드 대신 용도별 응답 필드를 사용한다. Spring Boot는 엔드포인트별 응답 필드를 명확히 매핑한다.

```jsonc
// /ai/final-headline (mode=PROTECTED)
{
  "spoilerSafe": true,
  "safeTitle": "다시 볼 만한 흐름이 있었던 경기",
  "tags": ["후반 긴장 구간"],
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "..."
}

// /ai/final-headline (mode=REVEALED)
{
  "spoilerSafe": true,
  "safeTitle": "LAD가 8회 대량 득점으로 SF를 5-3으로 제압한 경기",
  "tags": ["후반 긴장 구간"],
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "..."
}

// /ai/replay-summary
{
  "spoilerSafe": true,
  "replayTitle": "스포일러 없이 다시 보기 좋은 구간",
  "replaySummary": "다시 볼 만한 흐름이 이어진 구간입니다.",
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

### 5.2 fallback 책임

기본 문구의 최종 책임은 Spring Boot에 둔다. ai-service는 fallback 기본 문구를 만들거나 내려주지 않으며, 현재 계약에서는 `fallbackUsed`를 항상 `false`로 반환한다. Spring Boot는 `spoilerSafe=false` 응답을 Redis나 PostgreSQL에 저장하지 않는다.

Spring Boot는 아래 순서로 종료 문구를 결정한다. `FINAL_HEADLINE`은 이 판단을 `mode`별로 각각 수행한다(한쪽 모드의 fallback 여부가 다른 모드에 영향을 주지 않는다).

1. 검수 통과 AI 문구가 있고 `contextHash`가 최신이면 저장·노출한다.
2. ai-service 실패, timeout, `spoilerSafe=false`, `contextHash` 불일치이면 저장하지 않고 기존 문구를 유지한다.
3. 기존 문구가 없으면 Spring Boot의 목적·모드별 기본 문구를 반환한다.

종료 산출물(`FINAL_HEADLINE`, `REPLAY_SUMMARY`)은 PostgreSQL에 fallback 문구를 저장하지 않는다. 기본 문구는 조회 시점의 fallback으로만 사용하고, 다음 생성 기회에 다시 AI 문구 생성을 시도한다.

## 6. 소비 인터페이스

`AiCopyReader.getCopy(gameId, purpose, mode)` — **항상 non-null.** 종료 경기의 검수 통과 AI 문구가 있으면 그것을, 없으면 Spring Boot의 목적·모드별 기본 문구를 반환한다. 소비자(홈·상세·다시보기)는 폴백 여부를 알 필요가 없다. 생성 결과는 ai-service의 스포일러 검수 게이트를 통과하고 `fallbackUsed=false`이며 `contextHash`가 일치하는 경우에만 저장된다.

- `FINAL_HEADLINE`은 `mode`에 따라 다른 문구를 반환한다. 홈 카드(항상 보호 모드로 노출)와 경기 상세 보호 모드는 `mode=PROTECTED` 문구를, 경기 상세 공개 모드는 `mode=REVEALED` 문구를 조회한다.
- `REPLAY_SUMMARY`는 `mode` 값과 무관하게 항상 같은 문구를 반환한다(이미 스포일러 안전 문구로 검수된 값이므로 공개 모드 전용 문구를 별도로 분기하지 않는다).
