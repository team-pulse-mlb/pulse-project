# AI 문구 설계

AI 문구의 생성 트리거, 컨텍스트·HTTP 계약, 검수, 저장, 폴백을 정의한다. AI는 추천 여부를 판단하지 않고, 서버가 전달한 스포일러 세이프 context로 문구만 만든다.

## 1. purpose와 기본 문구

AI 문구는 서버가 만든 안전한 context만 입력으로 사용한다. 생성 결과가 금지 표현이나 점수·승패·우세 정보를 포함하면 기본 문구로 대체한다.

헤드라인·요약 고도화는 용도별 엔드포인트와 `safeContext` 스키마를 늘리는 방식으로 확장한다. `ai-service`, `com.pulse.ai`, `AiCopyReader` 구조는 그대로 두며, 새 저장 필드가 필요한 종료 산출물만 Flyway 증분으로 추가한다.

기본 문구의 최종 정본은 Spring Boot의 `com.pulse.ai`/`AiCopyReader`에 둔다. ai-service는 AI 문구 후보 생성과 스포일러 검수만 담당하며, fallback 문구를 응답할 수 있어도 저장·최종 노출 여부를 결정하지 않는다.

| 상황 | purpose | 기본 문구 |
|---|---|---|
| 라이브 추천 | `LIVE_HEADLINE` | 지금 볼 만한 흐름이 감지됐습니다. |
| 급상승 알림 | `NOTIFICATION` | 지금 볼 만한 흐름의 경기가 감지됐습니다. |
| 관심 팀 경기 시작 알림 | `NOTIFICATION` | 관심 팀 경기가 시작됐습니다. |
| 전환 안내 | `SWITCH_SUGGESTION` | 다른 경기에서 긴장감이 높아졌습니다. |
| 다시보기 | `REPLAY_SUMMARY` | 다시 볼 만한 흐름이 이어진 구간입니다. |
| 종료 경기 헤드라인 | `FINAL_HEADLINE` | 다시 볼 만한 흐름이 있었던 경기입니다. |

## 2. 생성 트리거와 흐름

HTTP 요청 경로에서 LLM을 호출하지 않는다. 문구 생성은 사용자 요청 경로가 아니라 **데이터 갱신 시점에 비동기로** 수행한다.

- scorer가 유의미한 변화(태그 세트 변화, 추천 상태 진입, 구간 확정, 경기 종료 정리)를 감지한 시점에 비동기로 요청하고, 데드라인(8초) 내 생성+검수 통과 시 저장한다. 실패·검수 불통과·fallback 응답·`contextHash` 불일치 시 저장하지 않고 기존 문구를 유지하며 다음 변화 때 재시도한다.
- 종료 정리 시에는 `FINAL_HEADLINE`과 마감된 구간의 `REPLAY_SUMMARY` 생성을 트리거한다.
- Spring Boot의 목적별 기본 문구를 먼저 표시하고, AI 문구가 준비되면 같은 보호 규칙을 통과한 경우에만 교체한다.

## 3. 저장 위치 — 라이브는 Redis, 종료는 PostgreSQL

| 문구 종류 | 저장 | 이유 |
|---|---|---|
| 라이브 문구 (`LIVE_HEADLINE`, `NOTIFICATION`, `SWITCH_SUGGESTION`) | Redis `game:{id}:copy:{purpose}` (TTL) | 매 사이클 갱신되는 휘발성 데이터. 유실 시 Spring Boot 기본 문구로 응답하고 다음 변화 때 재생성 |
| 종료 경기 문구 (`REPLAY_SUMMARY`, `FINAL_HEADLINE`) | PostgreSQL (`replay_segments.ai_summary`, `games.final_headline`) + Redis 읽기 캐시 | 확정 산출물. 몇 년 뒤 상세 화면에도 나와야 하고 재생성에 LLM 비용이 들어 "Redis=재계산 가능한 것만" 원칙에 해당하지 않음 |

## 4. ai-service 요청 컨텍스트

Spring Boot는 원본 경기 데이터를 그대로 보내지 않고, 화면 노출이 가능한 값만 `safeContext`로 매핑해 ai-service에 전달한다. 점수·이닝 초/말·play 원문·타석 결과·우세 팀·승패 결과는 어떤 경우에도 포함하지 않는다. `safeTags`에는 내부 신호명이 아니라 보호 표기 문자열만 넣는다.

Spring Boot 필드 매핑 기준은 아래와 같다.

| Spring field | ai-service field |
|---|---|
| `status` | `safeContext.gameStatus` |
| `periodLabel` | `safeContext.inningPhase` |
| `reasonTags` | `safeContext.safeTags` |
| `spoilerSafeSignals` | `safeContext.reasonCodes` |

`recentPlays`, `teams`, `startTime`, 원본 `purpose` 같은 경기 원본 필드는 문구 생성 요청에 넣지 않는다.

`safeContext.gameStatus`는 Spring 상태값을 그대로 사용한다. ai-service는 최소한 `STATUS_SCHEDULED`, `STATUS_LIVE`, `STATUS_FINAL`을 처리해야 한다.

```jsonc
// /ai/spoiler-free-summary
{
  "gameId": 5059041,
  "mode": "PROTECTED",
  "surface": "HOME_CARD",
  "language": "ko",
  "maxLength": 80,
  "safeContext": {
    "gameStatus": "STATUS_LIVE",
    "inningPhase": "후반",
    "tensionLevel": "HIGH",
    "scoreBand": "RECOMMEND",
    "safeTags": ["접전 흐름", "득점권 압박"],
    "reasonCodes": ["close_flow", "scoring_position_pressure"]
  },
  "contextHash": "..."
}

// /ai/notification-text
{
  "gameId": 5059041,
  "mode": "PROTECTED",
  "surface": "NOTIFICATION",
  "language": "ko",
  "maxLength": 80,
  "channel": "WEB",
  "safeContext": {
    "gameStatus": "STATUS_LIVE",
    "inningPhase": "후반",
    "tensionLevel": "HIGH",
    "scoreBand": "RECOMMEND",
    "safeTags": ["접전 흐름"],
    "reasonCodes": ["close_flow"]
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
  - `POST /ai/spoiler-free-summary`: 홈 카드·경기 카드용 제목과 추천 이유 생성
  - `POST /ai/notification-text`: 알림용 짧은 문구 생성
  - `POST /ai/replay-summary`: 다시보기 구간 제목과 요약 생성
- 요청: 각 엔드포인트는 §4의 `safeContext`와 `contextHash`를 받는다.
- 응답: 용도별 필드를 사용한다. 공통으로 검수 통과 여부(`spoilerSafe`), fallback 사용 여부(`fallbackUsed`), 위반 항목(`violations`), 요청과 같은 `contextHash`를 포함한다.
- 타임아웃 8초. 실패·검수 불통과·fallback 응답·`contextHash` 불일치 시 저장하지 않고 기존 문구를 유지한다.
- ai-service는 무상태다. 캐시·DB에 직접 쓰지 않고 생성·검수 결과를 응답으로만 반환한다.
- 저장 책임: 라이브 문구(Redis `game:{id}:copy:*`)는 `com.pulse.ai`가 저장하고, 종료 산출물(`replay_segments.ai_summary`, `games.final_headline`)은 scorer가 구간 확정·종료 정리 트랜잭션에서 저장한다. ai-service는 저장 여부를 판단하지 않는다.
- 저장 조건: `spoilerSafe=true`, `fallbackUsed=false`, `contextHash` 일치인 응답만 저장한다.
- stale write 방지: 응답의 `contextHash`가 대상의 최신 컨텍스트 해시와 일치할 때만 저장한다. 늦게 도착한 응답이 더 새로운 문구를 덮어쓰지 않게 한다.

### 5.1 응답 필드

`passed`/`text`/`reason` 같은 공통 필드 대신 용도별 응답 필드를 사용한다. Spring Boot는 엔드포인트별 응답 필드를 명확히 매핑한다.

```jsonc
// /ai/spoiler-free-summary
{
  "spoilerSafe": true,
  "safeTitle": "관전 가치가 높아진 경기",
  "safeReason": "지금 확인해볼 만한 흐름이 감지됐습니다.",
  "notificationText": "관심 경기에서 볼 만한 흐름이 감지됐습니다.",
  "tags": ["접전 흐름"],
  "violations": [],
  "fallbackUsed": false,
  "contextHash": "..."
}

// /ai/notification-text
{
  "spoilerSafe": true,
  "notificationText": "관심 경기에서 볼 만한 흐름이 감지됐습니다.",
  "tags": ["접전 흐름"],
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

### 5.2 fallback 책임

기본 문구의 최종 책임은 Spring Boot에 둔다. ai-service가 `fallbackUsed=true`인 문구를 반환해도 Spring Boot는 해당 문구를 Redis나 PostgreSQL에 저장하지 않는다.

Spring Boot는 아래 순서로 문구를 결정한다.

1. 검수 통과 AI 문구가 있고 `contextHash`가 최신이면 저장·노출한다.
2. ai-service 실패, timeout, `spoilerSafe=false`, `fallbackUsed=true`, `contextHash` 불일치이면 저장하지 않고 기존 문구를 유지한다.
3. 기존 문구가 없으면 Spring Boot의 목적별 기본 문구를 반환한다.

종료 산출물(`FINAL_HEADLINE`, `REPLAY_SUMMARY`)은 PostgreSQL에 fallback 문구를 저장하지 않는다. 기본 문구는 조회 시점의 fallback으로만 사용하고, 다음 생성 기회에 다시 AI 문구 생성을 시도한다.

## 6. 소비 인터페이스

`AiCopyReader.getCopy(gameId, purpose)` — **항상 non-null.** 검수 통과한 AI 문구가 있으면 그것을, 없으면 Spring Boot의 목적별 기본 문구를 반환한다. 소비자(홈·상세·알림)는 폴백 여부를 알 필요가 없다. 생성 결과는 ai-service의 스포일러 검수 게이트(금지 표현·숫자 패턴·선수명과 경기 상황의 결합 표현)를 통과하고 `fallbackUsed=false`이며 `contextHash`가 일치하는 경우에만 저장된다.
