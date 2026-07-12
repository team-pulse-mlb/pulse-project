# PULSE 백엔드

Java 21·Spring Boot 단일 프로젝트다. 외부 MLB API 수집, 관전 점수 계산, PostgreSQL·Redis 저장, REST API 제공을 담당한다.

- 폴러 기본값: 비활성화
- 활성화 시 기본 주기: 20초
- 기본 서버 포트: `8080`

## 실행

요구사항:

- JDK 21
- Docker·Docker Compose

1. [로컬 인프라 가이드](../infra/local/README.md)에 따라 터미널에서 PostgreSQL과 Redis를 실행한다.

2. IntelliJ에서 `backend/`를 Gradle 프로젝트로 열고 Gradle JVM을 JDK 21로 지정한다.

3. `PulseApplication` 실행 구성의 **Environment variables**에 필요한 값을 설정한다.

   | 변수 | 기본값 | 용도 |
   |---|---|---|
   | `POSTGRES_HOST` | `localhost` | PostgreSQL 호스트 |
   | `POSTGRES_PORT` | `5432` | PostgreSQL 포트 |
   | `POSTGRES_DB` | `pulse` | DB 이름 |
   | `POSTGRES_USER` | `pulse` | DB 사용자 |
   | `POSTGRES_PASSWORD` | `pulse` | DB 비밀번호 |
   | `REDIS_HOST` | `localhost` | Redis 호스트 |
   | `REDIS_PORT` | `6379` | Redis 포트 |
   | `RABBITMQ_HOST` | `localhost` | RabbitMQ 호스트 |
   | `RABBITMQ_PORT` | `5672` | RabbitMQ 포트 |
   | `RABBITMQ_USER` | `pulse` | RabbitMQ 사용자 |
   | `RABBITMQ_PASSWORD` | `pulse` | RabbitMQ 비밀번호 |
   | `JWT_SECRET` | 없음 | JWT 서명 키, 필수 |
   | `BDL_API_KEY` | 빈 값 | 외부 MLB API 키 |
   | `PULSE_POLLER_ENABLED` | `false` | 라이브 폴러 활성화 |
   | `PULSE_SIMULATION_ENABLED` | `false` | 과거 경기 실시간 시뮬레이션 활성화 |

   루트 `.env`는 Docker Compose용이므로 Gradle이 자동으로 읽지 않는다. 전체 변수는 [`.env.example`](../.env.example)과 [`application.yml`](src/main/resources/application.yml)을 확인한다.

4. IntelliJ에서 `PulseApplication`을 실행한다. 기본 로컬 실행에서는 Active profiles를 비워 둔다. S3 원본을 재생할 때만 `replay` 프로필을 추가한다.

5. 브라우저에서 `http://localhost:8080/actuator/health`를 열어 응답을 확인한다. API 요청을 직접 시험할 때는 다음 명령을 사용할 수 있다.

```bash
curl "http://localhost:8080/api/games"
curl "http://localhost:8080/actuator/health"
```

## 프로필과 Flyway

| 구분 | Flyway | Hibernate |
|---|---|---|
| 기본 로컬 설정 | 비활성화 | `ddl-auto=update` |
| `prod` 프로필 | 활성화 | `ddl-auto=none` |

```bash
./gradlew bootRun --args="--spring.profiles.active=prod"
```

- `prod`는 운영 DB 연결 정보와 전체 환경 변수가 준비된 환경에서만 사용한다.
- 시작 시 V1~V7 중 미적용 마이그레이션을 순서대로 실행한다.

## 실시간 경기 시뮬레이션

시뮬레이션은 과거 경기 데이터를 별도 대상 경기 ID로 복제해 기존 poller → RabbitMQ → scorer → Redis/SSE 경로를 그대로 실행한다. `prod` 프로필에서는 설정과 기동 검사로 이중 차단된다.

필수 환경 변수:

| 변수 | 설명 |
|---|---|
| `PULSE_SIMULATION_ENABLED=true` | 시뮬레이션 데이터 소스 활성화 |
| `PULSE_POLLER_ENABLED=true` | poller 활성화 |
| `PULSE_SIMULATION_SOURCE_GAME_ID` | 원본 경기 ID |
| `PULSE_SIMULATION_TARGET_GAME_ID` | 실행마다 새로 사용할 대상 경기 ID. 생략 시 원본 ID에 90억을 더함 |
| `PULSE_SIMULATION_SPEED` | `1`, `5`, `20` 중 하나 |
| `PULSE_SIMULATION_START_OFFSET` | 시작 위치. 예: `30m` |
| `PULSE_SIMULATION_PRESET` | `START` 또는 7회 이후 득점 직전으로 이동하는 `SURGE` |

S3 원본을 우선 사용하려면 `PULSE_SIMULATION_S3_BUCKET`, `PULSE_SIMULATION_ARCHIVE_DATE`를 함께 설정한다. 원본이 없거나 읽을 수 없으면 운영 DB의 `games`·`plays`로 대체한다.

PowerShell 실행 예:

```powershell
$env:PULSE_SIMULATION_ENABLED='true'
$env:PULSE_POLLER_ENABLED='true'
$env:PULSE_SCORER_ENABLED='true'
$env:PULSE_SIMULATION_SOURCE_GAME_ID='123456' # 선택한 원본 경기 ID로 교체
$env:PULSE_SIMULATION_TARGET_GAME_ID='9000123456' # DB에 없는 새 대상 경기 ID로 교체
$env:PULSE_SIMULATION_SPEED='20'
$env:PULSE_SIMULATION_PRESET='SURGE'
.\gradlew.bat bootRun
```

동일한 대상 경기 ID가 이미 DB에 있으면 기존 결과와 섞이지 않도록 기동을 중단한다. 다시 촬영할 때는 새 대상 ID를 지정한다. 세부 검증 순서는 `local-docs/SIMULATION_GUIDE.md`를 따른다.

## 패키지 구조

```text
com.pulse/
├── api/
│   ├── home/             # 홈 목록·실시간 랭킹 API
│   ├── sse/              # Redis 신호를 브라우저 SSE로 중계
│   └── user/             # 회원·인증·이메일 API
├── poller/               # 경기·플레이·선발·순위 수집
├── scorer/               # 관전 점수·이벤트·종료 처리
├── ranking/              # Redis 추천 순위
├── replay/
│   ├── migration/        # S3 원본 데이터 이관
│   └── rescore/          # 과거 데이터 재점수화
├── common/               # 외부 API·설정·메시지·트랜잭션
└── domain/               # 엔티티·저장소
```

현재 경기 상세 컨트롤러·조회 서비스는 `api` 바로 아래에 있다. 기능 확장 시 `api.gamedetail` 경계를 따른다.

## 기능별 개발 진입점

| 기능 | 먼저 볼 파일 | 이어서 볼 파일 |
|---|---|---|
| 홈 경기 목록 | `api/home/HomeGameController.java` | `api/home/HomeQueryService.java` |
| 실시간 추천 랭킹 | `api/home/HomeRankingController.java` | `ranking/RankingService.java`, `api/home/HomeQueryService.java` |
| 경기 상세·펄스 그래프 데이터 | `api/GameController.java` | `api/GameQueryService.java`, `domain/WatchScore.java` |
| SSE | `api/sse/SseController.java` | `api/sse/RedisSignalRelay.java`, `common/message/RedisSignalChannels.java` |
| 점수 계산 | `scorer/ScoreTaskListener.java` | `scorer/LiveScoringService.java`, `scorer/ScoreCalculator.java` |
| 알림 발행 | `scorer/SurgeDetector.java` | `common/message/NotificationOutboxDispatcher.java` |
| [구현 예정] 알림 소비·사용자 SSE | 없음 | 윤호 담당 `api.notification` 구현 후 연결 |

민석의 경기 상세 화면은 `GET /api/games/{gameId}` 응답의 종료 경기 긴장 곡선을 소비한다. 윤호의 클라이언트는 `GET /api/sse`의 `ranking_changed`, `game_updated` 신호를 소비할 수 있다. SSE 이벤트는 변경 데이터 전체가 아니라 재조회 신호다.

## 데이터 흐름

```text
외부 MLB API
  → poller
  → PostgreSQL games·plays
  → RabbitMQ score.tasks
  → scorer의 watch_score·game_events
  → ranking의 Redis 추천 순위
  → REST API
  → frontend
```

- 상세 구조: [아키텍처](../docs/design/ARCHITECTURE.md)
- 수집·계산 순서: [데이터 파이프라인](../docs/design/DATA_PIPELINE.md)
- scorer는 Redis 채널에 변경 신호를 발행하고, api 역할의 `RedisSignalRelay`가 이를 SSE로 중계한다.

## 역할별 기동 게이트

운영 컨테이너는 같은 이미지를 사용하고 환경 변수로 역할을 나눈다.

| 역할 | 주요 설정 | 필요한 로컬 의존 서비스 |
|---|---|---|
| API | `PULSE_POLLER_ENABLED=false`, `PULSE_SCORER_ENABLED=false`, `PULSE_SSE_ENABLED=true` | PostgreSQL, Redis, RabbitMQ |
| poller | `PULSE_POLLER_ENABLED=true`, `PULSE_SCORER_ENABLED=false`, `PULSE_SSE_ENABLED=false` | PostgreSQL, Redis, RabbitMQ, 외부 API |
| scorer | `PULSE_POLLER_ENABLED=false`, `PULSE_SCORER_ENABLED=true`, `PULSE_SSE_ENABLED=false` | PostgreSQL, Redis, RabbitMQ |

기본 로컬 설정은 scorer와 SSE가 활성화되고 poller만 비활성화된다. 실제 운영 조합은 `infra/docker-compose.prod.yml`을 기준으로 확인한다.

## replay·migration·rescore

세 작업은 일반 웹 서버 기동과 구분되는 일회성 프로필이다.

```powershell
cd backend

# S3 원본 읽기 기능을 포함한 replay 프로필
$env:PULSE_REPLAY_S3_BUCKET='pulse-raw-<account-id>'
.\gradlew.bat bootRun --args='--spring.profiles.active=replay'

# S3 원본을 DB에 이관하고 종료
.\gradlew.bat bootRun --args='--spring.profiles.active=migration'

# 전체 경기 또는 지정 경기의 watch_scores를 다시 계산하고 종료
.\gradlew.bat bootRun --args='--spring.profiles.active=rescore --pulse.rescore.game-ids=123,456'
```

- `migration`은 `PULSE_REPLAY_S3_BUCKET`과 대상 DB 연결 정보를 반드시 확인한 뒤 실행한다.
- `rescore`에서 `pulse.rescore.game-ids`를 생략하면 DB의 대상 경기를 전체 조회한다.
- 운영 DB·S3를 대상으로 실행하는 작업은 사용자 승인 후 진행한다.

## DB 마이그레이션

- 위치: `src/main/resources/db/migration/`
- 운영에서는 `prod` 프로필로 Flyway를 활성화하고 Hibernate 스키마 변경을 끈다.
- 적용 순서와 현재 버전은 파일 목록을 직접 확인한다. 기존 migration 파일은 수정하지 않고 새 버전을 추가한다.
- 스키마 기준: [DB 스키마](../docs/design/DB_SCHEMA.md)

## 테스트

```bash
cd backend
./gradlew test
./gradlew clean build
```

- `GameEventExtractorTest`: play 기반 이벤트 추출
- `HomeQueryServiceTest`: 홈 조회·상태·정렬·노출 제한
- `ScoreCalculatorTest`: 신호별 점수·경계값

## 담당 경계

| 패키지 | 담당 |
|---|---|
| `poller`, `scorer`, `ranking`, `replay`, `api.home`, `common`, `domain` | 예은 |
| `api.gamedetail` | 민석 |
| `api.user`, `api.notification` | 윤호 |
| `com.pulse.ai` | 창현 |

`common`과 `domain`의 쓰기 소유자는 예은이다. API·스키마 변경 전 [API 계약](../docs/design/API_CONTRACTS.md)의 영향 범위를 확인한다.
