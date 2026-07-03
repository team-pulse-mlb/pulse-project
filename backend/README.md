# backend

Spring Boot 단일 프로젝트. 로컬 개발은 S3 라이브 raw archive를 읽어 PostgreSQL과 Redis에 재생한다.

## 프로필

| 프로필 | 역할 |
|---|---|
| `api` | REST API, Swagger, 스포일러 보호 응답 |
| `replay` | S3 raw archive 읽기 -> 로컬 DB 적재 -> watch_score/replay_segments 계산 -> Redis 랭킹 갱신 |

## 패키지 구조

```text
com.pulse
├── api/       # 컨트롤러와 보호/공개 응답 DTO
├── replay/    # S3 archive reader, replay loader, ScoreCalculator, replay segment 계산
├── ranking/   # Redis ZSET 기반 live ranking
├── domain/    # games, plays, watch_scores, replay_segments 엔티티와 리포지토리
└── common/    # scoring.yml 바인딩, 외부 API DTO/클라이언트
```

## 로컬 리플레이 파이프라인

```text
S3 raw/games + raw/plays
  -> S3ReplayDataLoader
  -> games / plays 저장
  -> ReplayScoringService
  -> watch_scores / replay_segments 저장
  -> Redis score:rank:live 갱신
  -> API 조회
```

S3 raw 객체는 `{"observed_at", "endpoint", "params", "response"}` 래퍼를 사용한다. `backfilled: true` 객체는 시간 감쇠 재현에 쓰지 않으므로 로더가 건너뛴다.

## 실행

`.env` 또는 실행 환경에 아래 값을 설정한다.

```bash
PULSE_REPLAY_S3_BUCKET=pulse-raw-<account-id>
PULSE_REPLAY_GAME_ID=<live-game-id>
PULSE_REPLAY_DATE=YYYY-MM-DD
AWS_REGION=ap-northeast-2
```

```bash
./gradlew bootRun --args='--spring.profiles.active=api,replay'
```

## 테스트

```bash
./gradlew test
```

`ScoreCalculatorTest`는 상수 조정 후에도 기본 점수의 상식적 순서가 유지되는지 검증한다.
