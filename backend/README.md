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

## 시뮬레이터 실행

연출할 예정·진행·곧 종료 경기는 `application.yml`의 `pulse.simulation.games`에 고정돼 있다. 기본 20배속에서 진행 경기는 약 9분간 재생되고, 곧 종료 경기는 종료 전이 후 실제 ai-service 호출 결과를 홈 카드에 표시한다.

1. 저장소 루트에서 원본 시드를 적재한다.

```bash
bash backend/scripts/seed-dev-slate.sh
```

이 스크립트는 `pulse-api`를 로컬 프로파일로 재생성해 DB 스키마를 준비한 뒤 시드를 적재한다.

2. 같은 `pulse-api` 컨테이너를 시뮬레이션 모드로 재생성한다.

```bash
bash backend/scripts/run-simulation.sh
```

종료 후 일반 API 모드로 되돌린다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env up -d --force-recreate --wait pulse-api
```

## 테스트와 빌드

JDK 21에서 실행한다.

```bash
cd backend
./gradlew test
./gradlew clean build
```
