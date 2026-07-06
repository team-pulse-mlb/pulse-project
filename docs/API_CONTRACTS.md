# 모듈 계약 명세

이 문서는 팀원 간 병렬 개발의 계약 지점을 정의하는 **단일 기준**이다. REST/SSE 명세, 화면별 노출 필드, 모듈 인터페이스, 메시지·캐시 스키마를 다룬다. 금지 필드 목록은 직렬화 가드 테스트(`GameDetailSerializationGuardTest`)와 항상 같은 기준을 유지한다.

## 1. REST 엔드포인트

| 메서드·경로 | 설명 | 로그인 |
|---|---|---|
| `GET /api/rankings/live` | 홈 추천 보드(예정/진행/종료 목록, 추천순). 로그인 시 관심 팀/선수 가산이 적용된 순서 | 선택 |
| `GET /api/games/{id}?mode=PROTECTED\|REVEALED` | 경기 상세. `mode` 기본값 `PROTECTED`. 진행 중이면 `switchSuggestion` 포함 | 선택 |
| `GET /api/games/{id}/replay?mode=` | 종료 경기 다시보기 구간 목록 | 선택 |
| `GET /api/sse` | SSE 구독(이벤트 3종, §2) | 선택 |
| `POST /api/auth/signup` · `/login` · `/refresh` · `/logout` | 이메일+비밀번호 인증, JWT 발급·회전·폐기 | — |
| `GET·PUT /api/me/preferences` | 관심 팀/선수, 알림 설정 | 필요 |
| `GET /api/me/notifications` · `POST /api/me/notifications/read` | 알림 센터 목록·읽음 처리 | 필요 |
| `GET /api/players?search=` | 관심 선수 등록용 이름 검색 | 필요 |

공통 에러 응답: `{ "code": "GAME_NOT_FOUND", "message": "..." }` 형식, HTTP 상태 코드와 함께.

## 2. SSE 이벤트

payload에는 점수·순위·결과 데이터를 싣지 않는다. 클라이언트는 신호 수신 즉시 해당 REST를 재조회한다(개인화·스포일러 필터링은 항상 서버 REST 응답에서 적용). `notification_created`는 인증된 연결에만 발행한다.

| 이벤트 | payload | 클라이언트 동작 |
|---|---|---|
| `ranking_changed` | `{ sequence, generatedAt }` | 홈 랭킹 재조회 |
| `game_updated` | `{ gameId, sequence, generatedAt }` | 보고 있는 경기면 상세 재조회(현재 mode 유지) |
| `notification_created` | `{ notificationId }` | 알림 목록 재조회, 토스트 표시 |

## 3. 화면별 노출 필드 (보호 모드 기준)

### 3.1 경계 규칙

- **이닝 숫자는 노출, 초/말(`inningType`)은 비노출.** 이닝 숫자는 남은 관전 분량 판단에 필요하고 결과 방향을 드러내지 않는다. 초/말은 공격팀을 드러내 주자 압박과 결합 시 방향이 노출된다.
- **최근 흐름은 태그 변화 히스토리로 제공한다.** play 단위 마스킹 타임라인은 제공하지 않는다.
- **다시보기 구간의 이닝 범위(예: 5회~7회)는 노출한다.** 초/말·play order·구간 점수는 숨긴다.
- **내부 추천 점수는 모드와 무관하게 항상 금지.** 공개 모드에서 공개되는 것은 "경기의 점수"이지 "추천 점수"가 아니다.

### 3.2 필드 표

| 화면 | 노출 | 숨김 |
|---|---|---|
| 홈 카드 (예정) | 매치업, 시작 시각, 구장, 선발 투수, 추천 태그 | (pregame_score는 정렬에만) |
| 홈 카드 (진행) | 매치업, 이닝 숫자, LIVE 배지, 추천 태그, AI 한 줄 문구 | 점수, 초/말, 아웃카운트 |
| 홈 카드 (종료) | 매치업, 다시보기 구간 수 배지, 추천 태그 | 최종 점수, 승패 |
| 상세 (진행, 보호) | 매치업·선발, 이닝 숫자, 아웃·볼카운트, 득점권/만루 여부, 추천 태그, 태그 히스토리, AI 헤드라인, 관심 선수 출전 여부, `switchSuggestion` | 점수, 초/말, play 원문, 타석 결과 |
| 상세 (진행, 공개) | 보호 항목 + 점수, 이닝 초/말, play 타임라인(원문), 타석 결과, 이닝별 점수 | 내부 추천 점수 |
| 상세 (종료, 보호) | 구간 목록(이닝 범위·구간 태그·AI 요약), 구간 수 | 최종 점수, 승패, scoring_summary |
| 상세 (종료, 공개) | + 최종 점수, 이닝별 점수, scoring_summary, 타임라인 | 내부 추천 점수 |

### 3.3 금지 필드 목록 (직렬화 가드 대상)

| 범위 | 금지 필드 |
|---|---|
| 전 모드 | `watch_score` · `base_score` · `pregame_score` · `peak_base_score` · `signal_contributions` 등 내부 점수 일체, odds 전체 |
| 보호 모드 | `runs`/`hits`/`errors`, 이닝별 점수, `inningType`(초/말), play `text`, `scoring_play`/`score_value`, 타석 `result`, `scoring_summary`, `home_score`/`away_score` |

## 4. 경기 전환 추천 (`switchSuggestion`)

알림 파이프라인을 타지 않는다. `GET /api/games/{id}` 응답에 포함한다.

- 판정: 서버가 Redis 랭킹에서 "현재 경기보다 `watch_score` 20점 이상 높고 70 이상"인 경기를 찾는다. 점수는 서버 내부에서만 사용.
- 응답: `switchSuggestion: { gameId, matchup, tags } | null`
- 쿨다운: 같은 후보 15분 1회, 로그인 사용자는 Redis 키(사용자별), 비로그인은 클라이언트 보조.

## 5. AI 문구 계약

### 5.1 생성 트리거와 흐름

HTTP 요청 경로에서 LLM을 호출하지 않는다. scorer가 유의미한 변화(태그 세트 변화, 추천 상태 진입, 구간 확정)를 감지한 시점에 비동기로 요청하고, 데드라인(8초) 내 생성+검수 통과 시 저장한다. 실패 시 기존 문구를 유지하고 다음 변화 때 재시도한다.

### 5.2 저장 위치 — 라이브는 Redis, 종료는 PostgreSQL

| 문구 종류 | 저장 | 이유 |
|---|---|---|
| 라이브 문구 (`LIVE_HEADLINE`, `NOTIFICATION`, `SWITCH_SUGGESTION`) | Redis `game:{id}:copy:{purpose}` (TTL) | 매 사이클 갱신되는 휘발성 데이터. 유실 시 룰 기반 폴백 후 다음 변화 때 재생성 |
| 종료 경기 문구 (`REPLAY_SUMMARY`, 종료 헤드라인) | PostgreSQL (`replay_segments.ai_summary`, `games.final_headline`) + Redis 읽기 캐시 | 확정 산출물. 몇 년 뒤 상세 화면에도 나와야 하고 재생성에 LLM 비용이 들어 "Redis=재계산 가능한 것만" 원칙에 해당하지 않음 |

### 5.3 컨텍스트 스키마 (scorer → ai-service)

점수·이닝 초/말·play 원문·타석 결과는 어떤 경우에도 포함하지 않는다. `tags`에는 내부 신호명이 아니라 [RECOMMENDATION_POLICY.md](RECOMMENDATION_POLICY.md) §2의 보호 표기 문자열만 넣는다.

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
```

### 5.4 소비 인터페이스

`AiCopyReader.getCopy(gameId, purpose)` — **항상 non-null.** 검수 통과한 AI 문구가 있으면 그것을, 없으면 룰 기반 기본 문구를 반환한다. 소비자(홈·상세·알림)는 폴백 여부를 알 필요가 없다. 생성 결과는 ai-service의 스포일러 검수 게이트(금지 표현·숫자 패턴·선수명과 경기 상황의 결합 표현)를 통과한 경우에만 저장된다.

## 6. 알림 계약

### 6.1 판정 위치와 조건

| 유형 | 판정 위치 | 조건 |
|---|---|---|
| `SURGE` (급상승) | scorer | `watch_score` 85 이상 진입 **그리고** 최근 5분 내 +15 이상 상승. 발화 후 70 미만으로 내려가야 재무장(히스테리시스). 전역 15분 1회 제한(Redis). 대상은 전역(관심 팀 무관) |
| `GAME_START` (관심 팀 경기 시작) | poller | LIVE 전이 감지 시, 경기당 1회. 수신은 관심 팀 사용자만(§6.2) |

### 6.2 이벤트 스키마 (RabbitMQ `notify.events`)

```jsonc
{ "eventId": "uuid", "type": "SURGE | GAME_START", "gameId": 5059041,
  "occurredAt": "2026-07-06T02:11:00Z", "tags": ["흐름 급변"] }
```

- 소비: api의 notification 모듈이 fan-out → `user_notifications` insert → SSE `notification_created` 푸시.
- fan-out 대상: `SURGE`는 `notify_enabled`·`notify_surge_enabled`가 켜진 전체 사용자. `GAME_START`는 그중 `notify_game_start`가 켜져 있고 `favorite_team_ids`에 홈 또는 원정 팀이 포함된 사용자만.
- 멱등: `(event_id, user_id)` 유니크 제약. 중복 전달을 전제로 한다.
- 알림 payload·문구에 점수 숫자를 싣지 않는다.

## 7. 모듈 인터페이스 (팀 계약 지점)

| 인터페이스 | 제공 → 사용 | 시그니처·내용 |
|---|---|---|
| domain 읽기 | 예은 → 전원 | JPA 엔티티 읽기 전용. 스키마 변경은 예은만 |
| `ScoreQueryService` | 예은 → 민석 | `getLatestSignals(gameId)` → `{ tags, phase, situation, updatedAt }`. **점수 숫자는 계약에 없음** |
| `AiCopyReader` | 창현 → 전원 | §5.4. 항상 non-null |
| `UserPreferenceReader` | 윤호 → 예은(홈 가산)·api(알림 fan-out, 전환 쿨다운) | 관심 팀/선수·알림 설정 조회 |
| `SseEventPublisher` | api 공통 | 이벤트 3종 발행 단일 창구 |
| `notify.events` | scorer·poller → 윤호 | §6.2 |

## 8. 메시징·캐시 명세

### 8.1 RabbitMQ

| 큐 | 용도 | 정책 |
|---|---|---|
| `score.tasks` (+`.dlq`) | poller → scorer 계산 요청 | ack 실패 시 1회 재전달 후 DLQ. 소비자 prefetch 5 |
| `notify.events` (+`.dlq`) | 알림 이벤트 | 동일. 소비 측 멱등 처리 전제 |

```jsonc
// ScoreTask (score.tasks)
{ "gameId": 5059041, "observedAt": "2026-07-06T02:11:00Z",
  "lastPlayOrder": 217414761, "lifecycleState": "LIVE" }
```

재전달 멱등: `watch_scores`의 UNIQUE(`game_id`, `computed_at`) 충돌 시 scorer는 해당 사이클 저장을 건너뛴다(`computed_at` = `observedAt`).

### 8.2 Redis 키

| 키 | 타입 | 내용 |
|---|---|---|
| `score:rank:live` | ZSET | 진행 중 경기 랭킹 (member=game_id, score=watch_score) |
| `game:{id}:live` | HASH | 현재 점수·이닝·태그 캐시 (내부 전용) |
| `game:{id}:copy:{purpose}` | STRING | 검수 통과 AI 문구 캐시 |
| `notify:armed:{gameId}` | STRING | 급상승 히스테리시스 상태 |
| `notify:cooldown:global` | STRING | 전역 15분 레이트리밋 |
| `switch:cooldown:{userId}:{gameId}` | STRING | 전환 안내 쿨다운 |
| (pub/sub) `signal:ranking`, `signal:game:{id}` | 채널 | 재조회 신호. api가 SSE로 중계 |

라이프사이클 정리: 경기가 LIVE에서 이탈하면(`FINAL`·`DONE`·`SUSPENDED_POSTPONED`) scorer가 `score:rank:live`에서 해당 경기를 제거하고 `signal:ranking`을 발행한다. `game:{id}:live`·`game:{id}:copy:*`는 TTL로 소멸한다.
