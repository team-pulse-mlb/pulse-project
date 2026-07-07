# AI 문구 설계

AI 문구의 생성 트리거, 컨텍스트·HTTP 계약, 검수, 저장, 폴백을 정의한다. AI는 추천 여부를 판단하지 않고, 서버가 전달한 스포일러 세이프 context로 문구만 만든다.

## 1. purpose와 기본 문구

AI 문구는 서버가 만든 안전한 context만 입력으로 사용한다. 생성 결과가 금지 표현이나 점수·승패·우세 정보를 포함하면 기본 문구로 대체한다.

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

- scorer가 유의미한 변화(태그 세트 변화, 추천 상태 진입, 구간 확정, 경기 종료 정리)를 감지한 시점에 비동기로 요청하고, 데드라인(8초) 내 생성+검수 통과 시 저장한다. 실패 시 기존 문구를 유지하고 다음 변화 때 재시도한다.
- 종료 정리 시에는 `FINAL_HEADLINE`과 마감된 구간의 `REPLAY_SUMMARY` 생성을 트리거한다.
- 룰 기반 문구를 먼저 표시하고, AI 문구가 준비되면 같은 보호 규칙을 통과한 경우에만 교체한다.

## 3. 저장 위치 — 라이브는 Redis, 종료는 PostgreSQL

| 문구 종류 | 저장 | 이유 |
|---|---|---|
| 라이브 문구 (`LIVE_HEADLINE`, `NOTIFICATION`, `SWITCH_SUGGESTION`) | Redis `game:{id}:copy:{purpose}` (TTL) | 매 사이클 갱신되는 휘발성 데이터. 유실 시 룰 기반 폴백 후 다음 변화 때 재생성 |
| 종료 경기 문구 (`REPLAY_SUMMARY`, `FINAL_HEADLINE`) | PostgreSQL (`replay_segments.ai_summary`, `games.final_headline`) + Redis 읽기 캐시 | 확정 산출물. 몇 년 뒤 상세 화면에도 나와야 하고 재생성에 LLM 비용이 들어 "Redis=재계산 가능한 것만" 원칙에 해당하지 않음 |

## 4. 컨텍스트 스키마 (scorer → ai-service)

점수·이닝 초/말·play 원문·타석 결과는 어떤 경우에도 포함하지 않는다. `tags`에는 내부 신호명이 아니라 보호 표기 문자열만 넣는다.

```jsonc
// LiveCopyContext
{
  "gameId": 5059041,
  "purpose": "LIVE_HEADLINE | NOTIFICATION | SWITCH_SUGGESTION",
  "phase": "EARLY | MID | LATE | EXTRA",
  "matchup": { "home": "Cubs", "away": "Padres" },
  "tags": ["접전 흐름", "득점권 압박"],
  "situation": { "outs": 2, "balls": 3, "strikes": 2,
                 "runnersInScoringPosition": true, "basesLoaded": false },
  "favoritePlayerMention": "Shohei Ohtani",   // 선택. 이름만, 결과·타석 정보 결합 금지
  "contextHash": "..."
}

// ReplayCopyContext
{
  "gameId": 5059041,
  "segmentId": 17,
  "inningRange": { "start": 5, "end": 7 },    // 초/말 없음
  "segmentTags": ["흐름 급변", "후반 긴장 구간"],
  "contextHash": "..."
}

// FinalHeadlineContext — 경기 종료 정리 시 1회
{
  "gameId": 5059041,
  "purpose": "FINAL_HEADLINE",
  "segmentCount": 2,
  "topTags": ["후반 긴장 구간", "흐름 급변"],
  "contextHash": "..."
}
```

## 5. scorer ↔ ai-service HTTP 계약

- 엔드포인트: `POST /internal/v1/copy` (ai-service)
- 요청: `{ "purpose": "...", "context": <§4 스키마> }`
- 응답: `{ "passed": true|false, "text": "...", "reason": "...", "contextHash": "..." }` — `contextHash`는 요청 값을 그대로 반환한다.
- 타임아웃 8초. 실패·검수 불통과 시 저장하지 않고 기존 문구를 유지한다.
- ai-service는 무상태다. 캐시·DB에 직접 쓰지 않고 생성·검수 결과를 응답으로만 반환한다.
- 저장 책임: 라이브 문구(Redis `game:{id}:copy:*`)는 `com.pulse.ai`가 저장하고, 종료 산출물(`replay_segments.ai_summary`, `games.final_headline`)은 scorer가 구간 확정·종료 정리 트랜잭션에서 저장한다.
- stale write 방지: 응답의 `contextHash`가 대상의 최신 컨텍스트 해시와 일치할 때만 저장한다. 늦게 도착한 응답이 더 새로운 문구를 덮어쓰지 않게 한다.

## 6. 소비 인터페이스

`AiCopyReader.getCopy(gameId, purpose)` — **항상 non-null.** 검수 통과한 AI 문구가 있으면 그것을, 없으면 룰 기반 기본 문구를 반환한다. 소비자(홈·상세·알림)는 폴백 여부를 알 필요가 없다. 생성 결과는 ai-service의 스포일러 검수 게이트(금지 표현·숫자 패턴·선수명과 경기 상황의 결합 표현)를 통과한 경우에만 저장된다.
