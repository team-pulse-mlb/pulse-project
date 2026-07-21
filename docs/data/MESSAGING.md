# 메시징 계약

## 1. RabbitMQ 큐

| 큐 | 용도 | 정책 |
|---|---|---|
| `score.tasks`와 `.dlq` | poller → game processor 계산 요청 | ack 실패 시 1회 재전달 후 DLQ, 소비자 prefetch 5 |
| `notify.events`와 `.dlq` | 알림 이벤트 | 동일, 소비 측 멱등 처리 전제 |

알림 이벤트의 payload, outbox, 멱등 키, fan-out은 [NOTIFICATIONS.md](../policy/NOTIFICATIONS.md)를 따른다.

## 2. `ScoreTask` 계약

- 공통 필드: `gameId`, `observedAt`, `lastPlayOrder`, `lifecycleState`
- 라이브 작업: `lifecycleState=LIVE`, `gameSnapshot`, nullable `situation`, 이벤트 추출 사실만 담은 `plateAppearances`
- 예정 작업: `lifecycleState=PREGAME`, `situation=null`. scorer는 DB의 최신 경기 전 입력으로 `pregame_score`를 멱등 재계산
- 종료 작업: `lifecycleState`가 `FINAL`·`DONE`·`SUSPENDED_POSTPONED`, `situation=null`
- `situation=null`은 계산 불가·현재 타석 없음이고 모든 주자 필드가 false인 값은 압박 없음이다.
- `scoringPosition = runnerOnSecond || runnerOnThird`, `basesLoaded`는 세 주자 점유다.
- 구버전 작업에서 `situation`·`gameSnapshot`이 없거나 `plateAppearances`가 null이어도 소비자는 안전하게 처리한다.

## 3. 발행·멱등 처리

poller는 원본 payload를 `score_task_outbox`의 `PENDING` 행으로 먼저 커밋한다. 발행 실패 시 지수 백오프로 재시도하며 재시작 후에도 미발행 행을 조회한다. 발행 직후 상태 반영 전 장애로 중복 전달될 수 있다.

scorer는 `watch_scores`의 UNIQUE(`game_id`, `computed_at`)로 라이브 재전달을 멱등 처리한다. `computed_at`은 `observedAt`이다. 종료 정리는 경기 상태 전이를 기준으로 한 번만 실행하며 중복·역순 작업에 안전해야 한다.

AI 내부 HTTP 요청·응답 스키마는 ai-service OpenAPI, 저장·검수 정책은 [AI_COPY.md](../policy/AI_COPY.md)를 따른다.

