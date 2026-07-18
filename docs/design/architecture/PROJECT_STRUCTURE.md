# 프로젝트 구조

## 1. 레포 최상위 구성

| 폴더 | 설명 |
|---|---|
| `backend/` | Spring Boot 기반 API·폴러·스코어러·랭킹·도메인 코드 |
| `frontend/` | React + Vite 기반 화면 코드 |
| `ai-service/` | FastAPI 기반 문구 생성·스포일러 검수 서비스 |
| `raw-archive/` | S3 임시 아카이브 도구. 데이터 이전 완료 후 폐기 예정 |
| `infra/` | 로컬 개발용 Compose(`infra/local`)와 운영 Compose(`infra/docker-compose.prod.yml`)·운영 절차 문서(`infra/prod`) |
| `docs/` | 프로젝트 문서 |

## 2. backend 패키지 구조

기능 간 데이터 전달은 `domain` 읽기, Redis 이벤트, RabbitMQ, 공개 서비스 인터페이스로 제한한다. `api.*` 기능 패키지 간 직접 참조 금지는 컨트롤러·서비스·DTO·구현체의 직접 참조 금지를 의미한다. 단, `common` 아래에 둔 계약 인터페이스(`common.user.UserPreferenceReader`, `common.ai.AiCopyContextReader` 등)의 import는 예외로 허용하며, 구현체는 제공 패키지 하위에 둔다. 그 외 기능 간 전달은 `domain` 읽기, Redis, RabbitMQ만 사용한다. 경기 데이터 JPA 엔티티·Repository는 `domain`에 두고, 회원 구현은 `api.user.domain`에 둔다.

| 패키지 | 역할 | 의존 방향 |
|---|---|---|
| `com.pulse.api` | REST·SSE·스포일러 보호 DTO·알림 전달 진입점 | `domain` 읽기, Redis 재조회 신호, 공개 서비스 인터페이스 사용 |
| `com.pulse.api.home` | 홈 추천 보드 API | `domain`, `ranking`, `UserPreferenceReader` 사용. 헤드라인은 `games` 컬럼 직접 조회 |
| `com.pulse.api.gamedetail` | 경기 상세·다시보기 API, 직렬화 가드 | `domain` 사용. 헤드라인·이벤트 문구·번역은 해당 테이블 직접 조회 |
| `com.pulse.api.user` | 회원·관심 팀·관심 선수·설정 API. 현재 회원 엔티티·Repository는 `api.user.domain`에 위치 | `common`, Redis 사용. 선호 조회는 `UserPreferenceReader`로 공개 |
| `com.pulse.api.notification` | 알림 fan-out·저장·SSE 알림 전달 | RabbitMQ `notify.events` 소비, `domain`, `UserPreferenceReader`, `SseEventPublisher` 사용 |
| `com.pulse.poller` | 상태별 폴링, 원본 저장, `ScoreTask`·`GAME_START` 이벤트 발행 | 외부 MLB API, `domain`, RabbitMQ `score.tasks`·`notify.events` 사용 |
| `com.pulse.scorer` | `watch_score` 계산, 추천 태그 계산, 흥미 순간 이벤트 추출, `SURGE` 판정, AI 트리거 | RabbitMQ `score.tasks` 소비, `domain`, Redis, `ai` 패키지의 트리거 인터페이스 경유, RabbitMQ `notify.events` 사용 |
| `com.pulse.ranking` | Redis 랭킹 반영 | Redis와 공개 서비스 인터페이스를 통해 사용 |
| `com.pulse.ai` | ai-service HTTP 클라이언트·요청 DTO·컨텍스트 매퍼. scorer가 사용할 헤드라인·보호 이벤트 문구·최근 플레이 번역 생성 트리거 | ai-service, `common.ai` 계약, `domain` 읽기 사용 |
| `com.pulse.replay` | S3 재생 어댑터, S3→운영 DB 이전 배치(`replay.migration`, `migration` 프로파일) | S3 임시 아카이브와 계산 재생·이전 경로에 한정 |
| `com.pulse.domain` | JPA 엔티티와 Repository | 전 기능에서 읽기 전용 사용 |
| `com.pulse.common` | 설정, 외부 클라이언트, 공통 DTO | 전 기능에서 공통 기반으로 사용 |

## 3. frontend 폴더 구조

폴더 내부에서는 컴포넌트, API 호출, 상태 관리를 분리한다.

대상 폴더는 `features/home`, `features/game-detail`, `features/ai-copy`, `features/auth`, `features/notification`, `shared`, `app`이다.

## 4. ai-service·raw-archive·infra 경계

| 영역 | 경계 |
|---|---|
| `ai-service/` | FastAPI 기반 문구 생성·스포일러 검수 서비스다. scorer가 응답 경로 밖에서 비동기 문구 생성을 요청하고, 검수 통과 문구는 Redis 또는 PostgreSQL 저장 경로로 전달된다. |
| `raw-archive/` | S3 임시 아카이브 도구다. 개발·데이터 파악·백테스트용 임시 수집에 한정하며, 운영 DB 이전 완료 후 폐기 예정이다. |
| `infra/` | `infra/local`은 로컬 개발용 Compose(PostgreSQL·Redis·RabbitMQ·앱 컨테이너), `infra/docker-compose.prod.yml`·`infra/prod`는 운영 Compose와 배포·운영 절차다. |
