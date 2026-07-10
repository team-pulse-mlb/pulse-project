# PULSE 백엔드

Java 21·Spring Boot 단일 프로젝트다. 외부 MLB API 수집, 관전 점수 계산, PostgreSQL·Redis 저장, REST API 제공을 담당한다.

- 폴러 기본값: 비활성화
- 활성화 시 기본 주기: 20초
- 기본 서버 포트: `8080`

## 실행

요구사항:

- JDK 21
- Docker·Docker Compose

1. [로컬 인프라 가이드](../infra/README.md)에 따라 VS Code 또는 Docker Desktop에서 PostgreSQL과 Redis를 실행한다.

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

   루트 `.env`는 Docker Compose용이므로 Gradle이 자동으로 읽지 않는다. 전체 변수는 [`.env.example`](../.env.example)과 [`application.yml`](src/main/resources/application.yml)을 확인한다.

4. IntelliJ에서 `PulseApplication`을 실행한다. 기본 로컬 실행에서는 Active profiles를 비워 둔다. S3 원본을 재생할 때만 `replay` 프로필을 추가한다.

터미널 대체 실행:

```powershell
cd backend
$env:JWT_SECRET = "로컬에서만-사용할-충분히-긴-임의값"
.\gradlew.bat bootRun
```

macOS·Linux는 `./gradlew bootRun`을 사용한다.

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

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=prod"
```

- `prod`는 운영 DB 연결 정보와 전체 환경 변수가 준비된 환경에서만 사용한다.
- 시작 시 V1~V7 중 미적용 마이그레이션을 순서대로 실행한다.

## 패키지 구조

```text
com.pulse/
├── api/
│   ├── home/             # 홈 목록·실시간 랭킹 API
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
- 현재 브랜치에는 프론트엔드 SSE 소비 훅과 설계 계약만 있고 백엔드 SSE 엔드포인트는 없다.

## DB 마이그레이션

| 버전 | 내용 |
|---|---|
| V1 | 운영 DB 기준선 |
| V2 | 경기 이벤트·경기 전 입력 스냅샷 |
| V3 | 홈·원정 팀 이름·약칭 |
| V4 | 최근 10경기 전적 타입 교정 |
| V5 | 보호·공개 이벤트 문구·팀 라벨 백필 |
| V6 | 공개 이벤트 타입 소문자 통일 |
| V7 | 사용자 설정·관심 팀·MLB 30팀 시드 |

- 위치: `src/main/resources/db/migration/`
- V7은 팀 마스터 시드만 포함하며 전체 데모 경기 시드는 없다.
- 스키마 기준: [DB 스키마](../docs/design/DB_SCHEMA.md)

## 테스트

```powershell
cd backend
.\gradlew.bat test
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
