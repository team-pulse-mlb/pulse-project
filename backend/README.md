# backend

Spring Boot 단일 프로젝트. Spring 프로필로 3개 프로세스를 분리 실행한다.

| 프로필 | 역할 |
|---|---|
| `api` | REST API + SSE + 스포일러 보호 응답 |
| `poller` | balldontlie 적응형 폴링 → DB 저장 → RabbitMQ 발행 |
| `scorer` | 메시지 소비 → watch_score 계산 → Redis 랭킹 갱신 |

## 패키지 구조

```
com.pulse
├── api/       # 컨트롤러 (RankingController: 개발 확인용 랭킹 조회)
├── poller/    # GameSyncService(/games tripwire) · PlaySyncService(커서 증분 수집) · PollerScheduler
├── scorer/    # ScoreCalculator(기본 신호 계산) · ScoringService · RankingService(Redis ZSET) · ScoreTaskListener
├── domain/    # Game(스냅샷) · Play(append 로그) · WatchScore(점수 이력) + 리포지토리
└── common/    # ScoringProperties(scoring.yml 바인딩) · RabbitConfig(큐+DLQ) · BalldontlieClient
```

## 구현된 파이프라인

```
poller: /games, /plays 폴링 → PostgreSQL 저장 → RabbitMQ에 ScoreTask 발행
scorer: ScoreTask 소비 → 기본 신호 계산 → watch_scores 로그 저장 → Redis 랭킹 갱신
api:    GET /api/rankings/live 로 랭킹 확인
```

주요 TODO는 코드의 `TODO(담당)` 주석으로 표시되어 있다: 적응형 폴링 티어, 429 backoff,
경기 중요도 보정(standings), 상세 신호(plate_appearances), 스포일러 보호 DTO.

## 테스트

`ScoreCalculatorTest`가 점수 불변 조건(9회 1점 차 > 5회 7점 차 등)을 검증한다.
`scoring.yml` 상수를 조정해도 이 테스트는 통과해야 한다.

```bash
./gradlew test
```

## 추천 점수 상수

모든 가중치는 `src/main/resources/scoring.yml`에 있다. 변경 규칙은 파일 상단 주석을 따른다.

## 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=api'
./gradlew bootRun --args='--spring.profiles.active=poller'
./gradlew bootRun --args='--spring.profiles.active=scorer'
```
