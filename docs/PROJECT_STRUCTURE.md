# 프로젝트 구조와 쓰기 소유자

## 1. 문서 목적

이 문서는 PULSE 레포 전체의 폴더·패키지 구조와 쓰기 소유자를 정리한다. 근거 문서는 `ARCHITECTURE_AND_DATA_FLOW.md`의 컴포넌트 배치, `TECH_STACK.md`의 배포 토폴로지, `API_CONTRACTS.md`의 모듈 인터페이스, `ROLES_AND_SCHEDULE.md`의 담당 영역별 기능 분해, `CONVENTIONS.md`의 코드 컨벤션이다.

## 2. 레포 최상위 구성

| 폴더 | 설명 | 쓰기 소유자 |
|---|---|---|
| `backend/` | Spring Boot 기반 API·폴러·스코어러·랭킹·도메인 코드 | 패키지별 담당자. 공용 패키지는 예은 |
| `frontend/` | React + Vite 기반 화면 코드 | 폴더별 담당자 |
| `ai-service/` | FastAPI 기반 문구 생성·스포일러 검수 서비스 | 창현 |
| `raw-archive/` | S3 임시 아카이브 도구. 데이터 이전 완료 후 폐기 예정 | 예은 |
| `infra/` | Docker Compose·CI/CD 설정 | (확인 필요) |
| `docs/` | 프로젝트 문서 | (확인 필요) |

## 3. backend 패키지 구조

기능 간 데이터 전달은 `domain` 읽기, Redis 이벤트, 공개 서비스 인터페이스로 제한한다. `api.*` 기능 패키지 간 직접 참조는 금지한다.

| 패키지 | 역할 | 쓰기 소유자 | 의존 방향 |
|---|---|---|---|
| `com.pulse.api` | REST·SSE·스포일러 보호 DTO·알림 전달 진입점 | 하위 기능별 담당자 | `domain` 읽기, Redis 재조회 신호, 공개 서비스 인터페이스 사용 |
| `com.pulse.api.home` | 홈 추천 보드 API | 예은 | `domain`, `ranking`, `AiCopyReader`, `UserPreferenceReader` 사용 |
| `com.pulse.api.gamedetail` | 경기 상세·다시보기 API, 직렬화 가드 | 민석 | `domain`, `ScoreQueryService`, `AiCopyReader` 사용 |
| `com.pulse.api.user` | 회원·관심 팀·관심 선수·설정 API | 윤호 | `domain`, `common` 사용. 선호 조회는 `UserPreferenceReader`로 공개 |
| `com.pulse.api.notification` | 알림 fan-out·저장·SSE 알림 전달 | 윤호 | RabbitMQ `notify.events` 소비, `domain`, `UserPreferenceReader`, `SseEventPublisher` 사용 |
| `com.pulse.poller` | 상태별 폴링, 원본 저장, `ScoreTask`·`GAME_START` 이벤트 발행 | 예은 | 외부 MLB API, `domain`, RabbitMQ `score.tasks`·`notify.events` 사용 |
| `com.pulse.scorer` | `watch_score` 계산, 추천 태그·다시보기 구간 계산, `SURGE` 판정, AI 트리거 | 예은 | RabbitMQ `score.tasks` 소비, `domain`, Redis, `ai-service`, RabbitMQ `notify.events` 사용 |
| `com.pulse.ranking` | Redis 랭킹 반영 | 예은 | Redis와 공개 서비스 인터페이스를 통해 사용 |
| `com.pulse.replay` | S3 재생 어댑터 | 예은 | S3 임시 아카이브와 계산 재생 경로에 한정 |
| `com.pulse.domain` | JPA 엔티티와 Repository | 공용. 쓰기 소유는 예은 | 전 기능에서 읽기 전용 사용. 스키마 변경은 예은만 |
| `com.pulse.common` | 설정, 외부 클라이언트, 공통 DTO | 공용. 쓰기 소유는 예은 | 전 기능에서 공통 기반으로 사용 |

## 4. frontend 폴더 구조

폴더 내부의 컴포넌트, API 호출, 상태 관리 배치 규칙은 코드 컨벤션의 frontend 절을 따른다.

| 폴더 | 쓰기 소유자 |
|---|---|
| `features/home` | 예은 |
| `features/game-detail` | 민석 |
| `features/ai-copy` | 창현 |
| `features/auth` | 윤호 |
| `features/notification` | 윤호 |
| `shared` | 예은 |
| `app` | 예은 |

## 5. ai-service·raw-archive·infra 경계

| 영역 | 경계 | 쓰기 소유자 |
|---|---|---|
| `ai-service/` | FastAPI 기반 문구 생성·스포일러 검수 서비스다. scorer가 응답 경로 밖에서 비동기 문구 생성을 요청하고, 검수 통과 문구는 Redis 또는 PostgreSQL 저장 경로로 전달된다. | 창현 |
| `raw-archive/` | S3 임시 아카이브 도구다. 개발·데이터 파악·백테스트용 임시 수집에 한정하며, 운영 DB 이전 완료 후 폐기 예정이다. | 예은 |
| `infra/` | Docker Compose와 CI/CD 설정 영역이다. 소유자는 근거 문서에 명확히 고정되어 있지 않다. | (확인 필요) |

## 6. 쓰기 소유 요약

| 영역 | 소유자 | 근거 문서 |
|---|---|---|
| `backend/com.pulse.poller` | 예은 | `ROLES_AND_SCHEDULE.md` §1, `ARCHITECTURE_AND_DATA_FLOW.md` §2 |
| `backend/com.pulse.scorer` | 예은 | `ROLES_AND_SCHEDULE.md` §1, `ARCHITECTURE_AND_DATA_FLOW.md` §2 |
| `backend/com.pulse.ranking` | 예은 | `ROLES_AND_SCHEDULE.md` §1 |
| `backend/com.pulse.replay` | 예은 | `ROLES_AND_SCHEDULE.md` §1 |
| `backend/com.pulse.api.home` | 예은 | `ROLES_AND_SCHEDULE.md` §1 |
| `backend/com.pulse.api.gamedetail` | 민석 | `ROLES_AND_SCHEDULE.md` §1 |
| `backend/com.pulse.api.user` | 윤호 | `ROLES_AND_SCHEDULE.md` §1 |
| `backend/com.pulse.api.notification` | 윤호 | `ROLES_AND_SCHEDULE.md` §1 |
| `backend/com.pulse.domain` | 공용. 쓰기 소유는 예은 | `ROLES_AND_SCHEDULE.md` §1, §3 |
| `backend/com.pulse.common` | 공용. 쓰기 소유는 예은 | `ROLES_AND_SCHEDULE.md` §1 |
| `frontend/features/home` | 예은 | `ROLES_AND_SCHEDULE.md` §1 |
| `frontend/features/game-detail` | 민석 | `ROLES_AND_SCHEDULE.md` §1 |
| `frontend/features/ai-copy` | 창현 | `ROLES_AND_SCHEDULE.md` §1 |
| `frontend/features/auth` | 윤호 | `ROLES_AND_SCHEDULE.md` §1 |
| `frontend/features/notification` | 윤호 | `ROLES_AND_SCHEDULE.md` §1 |
| `frontend/shared` | 예은 | `ROLES_AND_SCHEDULE.md` §1 |
| `frontend/app` | 예은 | `ROLES_AND_SCHEDULE.md` §1 |
| `ai-service/` | 창현 | `ROLES_AND_SCHEDULE.md` §1, `TECH_STACK.md` §3 |
| `raw-archive/` | 예은 | `ROLES_AND_SCHEDULE.md` §1, `ARCHITECTURE_AND_DATA_FLOW.md` §10 |
| `infra/` | (확인 필요) | `TECH_STACK.md` §3, `ROLES_AND_SCHEDULE.md` §5 |
| `docs/` | (확인 필요) | (확인 필요) |
