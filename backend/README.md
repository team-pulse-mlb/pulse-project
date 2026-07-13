# PULSE 백엔드

Java 21·Spring Boot 기반 백엔드다. MLB 경기 수집, 관전 점수 계산, 실시간 추천 순위, REST API·SSE를 담당한다.

## 패키지 구조

```text
com.pulse/
├── api/                 REST API·SSE
│   ├── home/            홈 경기 목록·실시간 랭킹
│   ├── sse/             Redis 신호의 SSE 중계
│   └── user/            회원·인증
├── poller/              MLB 경기·플레이 수집
├── scorer/              관전 점수·이벤트 계산
├── ranking/             Redis 실시간 추천 순위
├── replay/              이관·재점수화·백테스트
├── common/              외부 API·설정·메시지
└── domain/              엔티티·Repository
```

```text
MLB API → poller → PostgreSQL → RabbitMQ → scorer → Redis → REST API·SSE
```

## 기능별 주요 파일

| 기능 | 진입점 | 로직 구현 |
|---|---|---|
| 홈 경기 목록 | `api/home/HomeGameController.java` | `api/home/HomeQueryService.java` |
| 실시간 추천 순위 | `api/home/HomeRankingController.java` | `ranking/RankingService.java` |
| 경기 상세·펄스 그래프 | `api/GameController.java` | `api/GameQueryService.java`, `scorer/TensionCurveQueryService.java` |
| SSE | `api/sse/SseController.java` | `api/sse/RedisSignalRelay.java` |
| 점수 계산 | `scorer/ScoreTaskListener.java` | `scorer/LiveScoringService.java`, `scorer/ScoreCalculator.java` |
| 경기 수집 | `poller/` | `common/client/` |
| 알림 발행 | `scorer/SurgeDetector.java` | `common/message/NotificationOutboxDispatcher.java` |

## 시뮬레이션

시뮬레이션은 로컬 DB의 과거 경기를 복제해 `poller → RabbitMQ → scorer → Redis/SSE` 흐름을 재현한다.

시뮬레이션 스크립트가 별도의 백엔드를 8080 포트로 실행하므로, IntelliJ에서 실행 중인 `PulseApplication`을 먼저 중지한다. Docker 컨테이너는 중지하지 않는다.

IntelliJ에서 `pulse-project/`를 연 상태의 저장소 루트 터미널에서 실행한다.

```bash
bash backend/scripts/run-simulation.sh
```

스크립트가 다음 작업을 처리한다.

- 로컬 PostgreSQL·Redis·RabbitMQ 실행
- 플레이가 100개 이상이고 후반 득점이 많은 경기 선택
- 중복되지 않는 시뮬레이션 경기 ID 생성
- 20배속 `SURGE` 모드로 백엔드 실행

특정 경기 ID를 사용하려면 인자로 전달한다.

```bash
bash backend/scripts/run-simulation.sh 123456
```

경기 데이터가 없으면 다음 방법 중 하나로 로컬 DB에 저장한다.

```bash
# S3의 최근 경기 적재
bash backend/scripts/load-s3-game.sh

# 현재 라이브 경기 수집. 종료할 때 Ctrl+C
bash backend/scripts/collect-live-game.sh
```

S3 적재에는 `.env`의 `PULSE_REPLAY_S3_BUCKET`과 AWS 자격 증명, 라이브 수집에는 `BDL_API_KEY`가 필요하다. S3의 특정 경기는 `bash backend/scripts/load-s3-game.sh <경기_ID> <YYYY-MM-DD>`로 지정한다. 적재 후 시뮬레이션 스크립트를 다시 실행한다.

`simulation ready` 로그와 Redis 순위를 확인한다.

```bash
docker exec pulse-redis redis-cli ZRANGE score:rank:live 0 -1 WITHSCORES REV
```

## 테스트와 빌드

JDK 21에서 실행한다.

```bash
cd backend
./gradlew test
./gradlew clean build
```
