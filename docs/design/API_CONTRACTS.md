# 모듈 계약 명세

## 1. 문서 책임

REST API의 경로, 요청 파라미터, 요청·응답 스키마, 인증 요구사항과 기본 동작은 실행 코드가 생성하는 OpenAPI를 단일 기준으로 사용한다.

| 서비스 | Swagger UI | OpenAPI JSON |
|---|---|---|
| Spring Boot backend | `/swagger-ui/index.html` | `/v3/api-docs` |
| FastAPI ai-service | `/docs` | `/openapi.json` |

REST 계약을 변경할 때는 컨트롤러의 OpenAPI 애너테이션과 요청·응답 DTO를 먼저 수정한다. 이 문서에 엔드포인트 표나 JSON 응답 예시를 중복 작성하지 않는다. 아직 구현되지 않은 REST API는 OpenAPI에 포함하지 않으며, 구현 전 합의가 필요하면 담당 설계 문서에 동작 원칙만 기록한다.

이 문서는 OpenAPI로 충분히 표현하기 어려운 다음 계약을 담당한다.

- 인증 토큰의 수명·회전·저장 정책
- SSE 연결·재연결 정책과 이벤트 의미
- 팀 간 Java 인터페이스
- RabbitMQ 전달·멱등 계약
- Redis 키와 라이프사이클
- REST 구현에 영향을 주는 데이터 저장·동시성 원칙

공통 오류 응답은 HTTP 상태 코드와 `{ "code": "...", "message": "..." }` 형식을 함께 사용한다.

## 2. 인증·사용자 데이터 정책

### 2.1 인증 토큰

| 항목 | 기준 |
|---|---|
| 액세스 토큰 | JWT, 유효기간 15분. `Authorization: Bearer` 헤더로 전달 |
| 리프레시 토큰 | JWT, 유효기간 7일. HttpOnly `refreshToken` 쿠키로 전달하며 쿠키 경로는 `/api/members` |
| 서버 저장 | `refresh_tokens`에 토큰 해시와 `ACTIVE`·`REVOKED`·`REUSED` 상태 저장. 원문은 저장하지 않음 |
| 재발급 | 기존 리프레시 토큰을 폐기하고 새 액세스·리프레시 토큰 발급 |
| 재사용 감지 | 폐기된 토큰이 다시 제시되면 해당 사용자의 리프레시 토큰 전체 폐기 |
| 로그아웃 | 리프레시 토큰 폐기 후 쿠키 만료 |
| 정리 | `expires_at` 경과 후 배치 삭제. 폐기 행도 만료 전에는 재사용 감지 근거로 보존 |
| 배포 | 로컬 `Secure=false`, HTTPS 배포 환경 `Secure=true` |

이메일 인증번호는 발급 후 5분 동안 유효하며 재발급 시 이전 번호를 폐기한다. 인증 성공 상태는 30분 동안 유지한다. 메일 발송에 실패하면 인증번호를 저장하지 않고 발급 요청 전체를 실패 처리한다.

### 2.2 관심 선수 검색·등록

`players`는 추적 경기에 등장한 선수만 저장하는 테이블이 아니라 선수 신원 마스터이자 관심 선수 FK 대상이다. 검색과 저장 책임을 분리한다.

| 구분 | 정책 |
|---|---|
| 검색 | read-only. 검색어 정규화 → 짧은 TTL 메모리 캐시 → 외부 검색 → 동일 ID 로컬 정보 보조 병합. 외부 장애 시 로컬 결과로 폴백하고 불완전 상태를 응답 |
| 등록 | 설정 저장 시에만 외부 ID를 검증하고 `players`를 upsert. 선수 upsert와 `user_favorite_players` 변경을 한 트랜잭션으로 처리 |

필수 안전장치는 다음과 같다.

- 관심 선수 최대 5명 제한은 사용자 단위 잠금 안에서 멱등 확인 → 개수 확인 → upsert → 관계 저장 순으로 직렬화한다.
- 외부 선수의 팀이 없거나 로컬 `teams`에 없으면 `team_id=null`로 저장한다.
- 외부 응답의 null·빈 값은 기존 정상 값을 덮어쓰지 않는다.
- 트레이드 등 최신 정보는 재등록 또는 파이프라인 재관측 시점까지 반영하며 주기 갱신은 MVP 범위에서 제외한다.

## 3. SSE 이벤트

payload에는 점수·순위·결과 데이터를 싣지 않는다. 클라이언트는 신호를 받으면 현재 표시 모드를 유지한 채 관련 REST API를 재조회한다. 개인화와 스포일러 필터링은 REST 응답에서 적용한다.

| 이벤트 | payload | 클라이언트 동작 |
|---|---|---|
| `ranking_changed` | `{ sequence, generatedAt }` | 홈 랭킹 재조회 |
| `game_updated` | `{ gameId, sequence, generatedAt }` | 보고 있는 경기의 상세·흐름 재조회 |
| `notification_created` | `{ notificationId }` | 알림 목록 재조회 및 토스트 표시 |

### 3.1 연결·인증·재연결

- 로그인 연결은 액세스 토큰으로 1회용 SSE 토큰을 발급받은 뒤 연결한다. 토큰은 Redis에 60초 동안 저장하고 연결 수립 시 소모한다.
- 비로그인 연결은 토큰 없이 생성하며 `ranking_changed`와 `game_updated`만 수신한다.
- 인증 연결만 `notification_created`를 수신한다.
- 서버는 25초 간격 SSE 코멘트로 하트비트를 전송하고 연결당 최대 수명은 60분으로 제한한다.
- `Last-Event-ID`는 사용하지 않는다. 연결 오류 시 새 토큰으로 재연결하고 관련 REST API를 한 번 재조회해 상태를 복구한다.
- `sequence`는 이벤트 종류별 인스턴스 내부 진단용 카운터다. 서버 재시작 시 초기화되며 클라이언트 동작 근거로 사용하지 않는다.
- 동시 연결 상한은 기본 1000이며 초과 요청은 503으로 거절한다.

## 4. 경기 전환 추천

경기 전환 추천은 알림 파이프라인을 사용하지 않고 경기 상세 응답의 `switchSuggestion`으로 제공한다.

- Redis 라이브 랭킹에서 현재 경기보다 `watch_score`가 20점 이상 높고 70점 이상인 후보를 찾는다.
- 추천 점수는 서버 내부에서만 사용하고 응답에 노출하지 않는다.
- 같은 후보는 15분에 한 번만 제안한다. 로그인 사용자는 Redis 사용자별 키, 비로그인 사용자는 클라이언트 보조 상태를 사용한다.

## 5. 모듈 인터페이스

| 인터페이스 | 제공 → 사용 | 계약 |
|---|---|---|
| domain 읽기 | 예은 → 전원 | JPA 엔티티 읽기 전용. 스키마 변경은 예은 담당 |
| 종료 헤드라인 조회 | 예은·민석 공통 | `games.final_headline_protected`·`final_headline_revealed` 직접 조회. 없으면 `headline=null` |
| 보호 이벤트 문구 조회 | 예은 → 민석 | `game_events.copy_protected` 직접 조회. 보호 모드 이벤트 API가 단일 원천 |
| 최근 플레이 번역 조회 | 예은 → 민석 | `plays.text_ko` 우선, 없으면 `plays.text` 폴백. 공개 모드 최근 플레이 API가 단일 원천 |
| `UserPreferenceReader` | 윤호 → 예은·api | 이메일로 관심 팀 ID, 관심 선수 ID, 알림 설정 조회. 홈 가산은 팀 일치 +10, 선수 일치 +5를 조건별 한 번만 반영 |
| `SseEventPublisher` | api 공통 | SSE 이벤트 3종 발행 단일 창구 |
| AI 생성 트리거 | 창현 → 예은 | `FINAL_HEADLINE`·`EVENT_COPY`·`PLAY_TRANSLATION` 비동기 요청. ai-service 호출·검수·저장 담당 |
| `AiCopyContextReader` | 예은 → 창현 | 컨텍스트와 예은 측 정규화 SHA-256 `contextHash` 반환. 빈 값은 생성 대상 아님. 외부 REST 컨텍스트 API는 두지 않음 |
| `notify.events` | scorer·poller → 윤호 | 서버가 고정 템플릿으로 완성한 `message` 전달 |

`AiCopyContextReader`의 메서드와 컨텍스트 상세는 [AI_COPY.md](AI_COPY.md)를 따른다.

## 6. 메시징·캐시 명세

### 6.1 RabbitMQ

| 큐 | 용도 | 정책 |
|---|---|---|
| `score.tasks`와 `.dlq` | poller → scorer 계산 요청 | ack 실패 시 1회 재전달 후 DLQ, 소비자 prefetch 5 |
| `notify.events`와 `.dlq` | 알림 이벤트 | 동일, 소비 측 멱등 처리 전제 |

`ScoreTask` 계약은 다음과 같다.

- 공통 필드: `gameId`, `observedAt`, `lastPlayOrder`, `lifecycleState`
- 라이브 작업: `lifecycleState=LIVE`, `gameSnapshot`, nullable `situation`, 이벤트 추출 사실만 담은 `plateAppearances`
- 예정 작업: `lifecycleState=PREGAME`, `situation=null`. scorer는 DB의 최신 경기 전 입력으로 `pregame_score`를 멱등 재계산
- 종료 작업: `lifecycleState`가 `FINAL`·`DONE`·`SUSPENDED_POSTPONED`, `situation=null`
- `situation=null`은 계산 불가·현재 타석 없음이고 모든 주자 필드가 false인 값은 압박 없음이다.
- `scoringPosition = runnerOnSecond || runnerOnThird`, `basesLoaded`는 세 주자 점유다.
- 구버전 작업에서 `situation`·`gameSnapshot`이 없거나 `plateAppearances`가 null이어도 소비자는 안전하게 처리한다.

poller는 원본 payload를 `score_task_outbox`의 `PENDING` 행으로 먼저 커밋한다. 발행 실패 시 지수 백오프로 재시도하며 재시작 후에도 미발행 행을 조회한다. 발행 직후 상태 반영 전 장애로 중복 전달될 수 있다.

scorer는 `watch_scores`의 UNIQUE(`game_id`, `computed_at`)로 라이브 재전달을 멱등 처리한다. `computed_at`은 `observedAt`이다. 종료 정리는 경기 상태 전이를 기준으로 한 번만 실행하며 중복·역순 작업에 안전해야 한다.

알림 이벤트의 세부 payload와 멱등 키는 [NOTIFICATIONS.md](NOTIFICATIONS.md)를 따른다. AI 내부 HTTP 요청·응답 스키마는 ai-service OpenAPI, 저장·검수 정책은 [AI_COPY.md](AI_COPY.md)를 따른다.

### 6.2 Redis 키

| 키 | 타입 | 내용 |
|---|---|---|
| `score:rank:live` | ZSET | 진행 중 경기 랭킹. member는 game ID, score는 watch score |
| `game:{id}:live` | HASH | 현재 점수·이닝·최신 태그 내부 캐시 |
| `notify:armed:{gameId}` | STRING | 급상승 히스테리시스 상태 |
| `notify:cooldown:{gameId}` | STRING | 급상승 경기별 15분 쿨다운 |
| `notify:surge:count:global` | STRING | 전역 15분 창 발화 수 |
| `switch:cooldown:{userId}:{gameId}` | STRING | 사용자별 경기 전환 안내 쿨다운 |
| `sse:token:{token}` | STRING | SSE 연결용 1회용 토큰, TTL 60초 |
| `signal:ranking` | pub/sub | 홈 랭킹 재조회 신호 |
| `signal:game:{id}` | pub/sub | 경기 상세 재조회 신호 |
| `signal:notification:{userId}` | pub/sub | 사용자 알림 재조회 신호 |

AI 문구는 Redis에 캐시하지 않고 `games`, `game_events`, `plays`에서 직접 조회한다. 경기가 라이브 상태에서 이탈하면 scorer가 `score:rank:live`에서 제거하고 랭킹 신호를 발행한다. `game:{id}:live`는 TTL로 소멸한다.
