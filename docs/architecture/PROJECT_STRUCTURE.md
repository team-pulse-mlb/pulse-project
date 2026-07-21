# 프로젝트 구조

## 1. 레포 최상위 구성

| 폴더 | 설명 |
|---|---|
| `backend/` | Spring Boot 기반 API·폴러·경기 처리기·랭킹·도메인 코드 |
| `frontend/` | React + Vite 기반 화면 코드 |
| `ai-service/` | FastAPI 기반 문구 생성·Structured Output·스포일러·근거·번역 검수 서비스 |
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
| `com.pulse.api.notification` | 알림 fan-out·저장·SSE 알림 전달 | RabbitMQ `notify.events` 소비, `domain`, `UserPreferenceReader` 사용. `signal:notification:{userId}` Redis 신호 발행 |
| `com.pulse.api.team` | 팀 마스터·로고 조회 API | `domain` 읽기 사용 |
| `com.pulse.poller` | 상태별 폴링, 원본 저장, `ScoreTask`·`GAME_START` 이벤트 발행 | 외부 MLB API, `domain`, RabbitMQ `score.tasks`·`notify.events` 사용 |
| `com.pulse.scoring` | `watch_score`·추천 태그를 계산하는 순수 계산 코어 | `common.config.ScoringProperties`, `domain` 입력만 사용. 메시지·Redis·외부 API를 모름 |
| `com.pulse.gameprocessing` | `score.tasks` 소비, 계산 오케스트레이션, 이벤트 추출, Redis 반영, `SURGE` 판정, AI 트리거 | `scoring`, `domain`, Redis, `ai` 패키지의 트리거 인터페이스, RabbitMQ `notify.events` 사용 |
| `com.pulse.ranking` | Redis 랭킹 반영 | Redis와 공개 서비스 인터페이스를 통해 사용 |
| `com.pulse.ai` | ai-service HTTP 클라이언트·요청 DTO·컨텍스트 매퍼·응답 계약. gameprocessing이 사용할 헤드라인·보호 이벤트 문구·최근 플레이 번역 생성 트리거 | ai-service, `common.ai` 계약, `domain` 읽기 사용 |
| `com.pulse.replay` | S3 재생 어댑터, S3→운영 DB 이전 배치(`replay.migration`, `migration` 프로파일) | S3 임시 아카이브와 계산 재생·이전 경로에 한정 |
| `com.pulse.domain` | JPA 엔티티와 Repository | 전 기능에서 읽기 전용 사용 |
| `com.pulse.common` | 설정, 외부 클라이언트, 공통 DTO | 전 기능에서 공통 기반으로 사용 |

`gameprocessing`은 처리 단계별로 다음 하위 패키지를 사용한다.

```text
com.pulse.gameprocessing
├── consumer      RabbitMQ 메시지 소비·경로 분기
├── application   라이브·경기 전·종료 처리 오케스트레이션과 조회
├── event         트랜잭션 이후 전달할 불변 이벤트
├── effect        Redis 반영·재조회 신호·SURGE 알림
├── highlight     흥미 이벤트 추출·타임라인 하이라이트
├── recovery      누락된 종료 작업 복구
└── aicopy        AI 문구·플레이 번역 요청과 재처리
```

```text
MLB API → poller → PostgreSQL → RabbitMQ → gameprocessing → Redis → REST API·SSE
gameprocessing → Spring safeContext → ai-service → OpenAI → Guard → PostgreSQL → Redis Pub/Sub game_updated → REST API·SSE 재조회
```

### 기능별 주요 파일

| 기능 | 진입점 | 로직 구현 |
|---|---|---|
| 홈 경기 목록 | `api/home/HomeGameController.java` | `api/home/HomeQueryService.java` |
| 실시간 추천 순위 | `api/home/HomeRankingController.java` | `ranking/RankingService.java` |
| 경기 상세·펄스 그래프 | `api/GameController.java` | `api/GameQueryService.java`, `gameprocessing/application/TensionCurveQueryService.java` |
| SSE | `api/sse/SseController.java` | `api/sse/RedisSignalRelay.java` |
| 점수 계산 | `gameprocessing/consumer/ScoreTaskListener.java` | `gameprocessing/application/LiveScoringService.java`, `scoring/ScoreCalculator.java` |
| 경기 수집 | `poller/` | `common/client/` |
| 알림 발행 | `gameprocessing/effect/SurgeDetector.java` | `common/message/NotificationOutboxDispatcher.java` |

## 3. frontend 폴더 구조

폴더 내부에서는 컴포넌트, API 호출, 상태 관리를 분리한다.

대상 폴더는 `features/home`, `features/game-detail`, `features/ai-copy`, `features/auth`, `features/notification`, `shared`, `app`이다.

```text
src/
├── app/                    라우터·레이아웃·프로바이더
├── assets/                 정적 리소스
├── features/
│   ├── auth/               회원·인증·관심 설정
│   ├── game-detail/        경기 상세·표시 모드
│   ├── home/               홈·추천 순위
│   └── notification/       알림 센터
├── shared/
│   ├── api/                HTTP 클라이언트
│   ├── components/         공통 UI
│   ├── hooks/              SSE 등 공통 훅
│   ├── lib/                쿼리 키·QueryClient
│   └── styles/             디자인 토큰·전역 스타일
└── main.tsx                애플리케이션 시작 파일
```

### 기능별 주요 파일

| 기능 | 화면 | 데이터·공통 코드 |
|---|---|---|
| 앱 시작·라우팅 | `src/main.tsx`, `src/app/router/root.tsx` | `src/app/providers.tsx` |
| 홈 | `src/features/home/pages/HomePage.tsx` | `hooks/useHomeQueries.ts`, `api/homeApi.ts` |
| 경기 상세 | `src/features/game-detail/pages/GameDetailPage.tsx` | `components/TensionCurve.tsx`, `components/EventTimeline.tsx` |
| SSE | `src/shared/hooks/useSse.ts` | `src/shared/lib/queryKeys.ts` |
| HTTP 요청 | 기능별 `api/*.ts` | `src/shared/api/httpClient.ts` |
| 공통 스타일 | 기능별 컴포넌트 | `src/shared/styles/global.css` |

## 4. ai-service·raw-archive·infra 경계

| 영역 | 경계 |
|---|---|
| `ai-service/` | FastAPI 기반 문구 생성·검수 서비스다. gameprocessing이 응답 경로 밖에서 비동기로 요청하며, ai-service는 Structured Output과 목적별 Guard를 통과한 결과·근거·`contextHash`만 backend로 반환한다. backend가 최신 `contextHash`를 재검증한 뒤 PostgreSQL에 저장하고 Redis Pub/Sub `game_updated` 재조회 신호를 발행한다. ai-service는 Redis·PostgreSQL에 직접 쓰지 않는다. |
| `raw-archive/` | S3 임시 아카이브 도구다. 개발·데이터 파악·백테스트용 임시 수집에 한정하며, 운영 DB 이전 완료 후 폐기 예정이다. 하위는 `live-collector/`(Lambda 라이브 수집기), `backfill/`(과거 시즌 일회성 적재), `deploy/`(AWS 수집기 배포), `analysis/`(수집 데이터 분석)로 나뉜다. |
| `infra/` | `infra/local`은 로컬 개발용 Compose(PostgreSQL·Redis·RabbitMQ·앱 컨테이너), `infra/docker-compose.prod.yml`·`infra/prod`는 운영 Compose와 배포·운영 절차다. |
