<p align="center">
  <img src="frontend/public/pulse-logo.png" alt="PULSE 로고" width="220" />
</p>

<p align="center"><strong>스포일러 프리 MLB 관전 타이밍 추천 서비스</strong></p>

<p align="center"><a href="https://pulsemlb.com">https://pulsemlb.com</a></p>

## 핵심 기능

<!-- 기능별 스크린샷·GIF 추가 예정 -->

| 문제 | 해결 |
|---|---|
| 동시에 진행되는 여러 경기 중 지금 볼 경기를 고르기 어렵다. | **실시간 경기 추천** — 진행 중인 경기의 관전 가치를 계산해 지금 볼 만한 순서로 보여준다. 로그인 사용자는 관심 팀·선수에 따라 개인화된 순서를 볼 수 있다. |
| 경기 흐름을 확인하는 과정에서 점수와 결과를 먼저 알게 된다. | **스포일러 보호와 공개** — 진행 중·종료 경기 모두 보호 모드로 시작한다. 보호 모드에서는 점수와 승패를 숨긴 경기 흐름을 보여주며, 종료 경기에는 AI 헤드라인·경기 긴장도 그래프·보호 안전 이벤트를 제공한다. 사용자가 공개로 전환하면 점수와 상세 결과를 볼 수 있다. |
| 경기를 계속 확인하지 않으면 볼 만한 순간이나 다른 경기의 상승 흐름을 놓치기 쉽다. | **관전 타이밍 알림** — 급상승 경기와 관심 팀 경기 시작을 인앱 토스트와 알림 센터로 알려준다. 경기 상세를 보고 있을 때 더 볼 만한 경기가 생기면 토스트로 이동을 제안한다. |

## 역할 분담

| 담당자 | 역할 |
|---|---|
| 예은 | 데이터 파이프라인, 점수·추천, 홈, 공통 구조 |
| 창현 | AI 문구 생성·검수 |
| 민석 | 경기 상세, 다시보기, 전환 알림 |
| 윤호 | 회원, 알림, 통합 후 관측 |

세부 경계와 일정은 [역할 분담 및 일정 계획](docs/team/ROLES_AND_SCHEDULE.md)을 따른다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, react-router v8 |
| Backend | Java 21, Spring Boot, Spring Data JPA, Flyway |
| Data | PostgreSQL 16, Redis 7 |
| AI | Python, FastAPI, Pydantic, OpenAI API |
| 실시간 | Server-Sent Events(SSE) |
| Infra | Docker Compose, AWS EC2·RDS·S3 |

## 시스템 다이어그램

<!-- 전체 아키텍처 다이어그램 추가 예정 -->
<!-- 사용자 흐름도 추가 예정 -->

상세 설명은 [아키텍처](docs/architecture/ARCHITECTURE.md)와 [사용자 흐름](docs/product/USER_FLOW.md)을 따른다.

## 저장소 구조

```text
pulse-project/
├── frontend/      # React 웹 애플리케이션
├── backend/       # 수집·점수화·랭킹·REST API
├── ai-service/    # AI 문구 생성·스포일러 검수
├── raw-archive/   # 원본 데이터 수집·백필·분석
├── infra/         # 인프라 구성(local: 로컬 개발용, prod: AWS 운영)
└── docs/          # 제품·설계·가이드·팀 문서
```

## 문서 인덱스

| 구분 | 문서 |
|---|---|
| 제품 | [기능 명세](docs/product/FEATURE_SPEC.md), [사용자 흐름](docs/product/USER_FLOW.md), [프로젝트 제안](docs/product/PROJECT_PROPOSAL.md) |
| 설계 | [아키텍처](docs/architecture/ARCHITECTURE.md), [데이터 파이프라인](docs/data/DATA_PIPELINE.md), [API 계약 안내](docs/api/API_CONTRACTS.md), [SSE 계약](docs/api/SSE.md), [메시징 계약](docs/data/MESSAGING.md), [스포일러 정책](docs/policy/SPOILER_POLICY.md), [DB 스키마](docs/data/DB_SCHEMA.md) |
| 가이드 | [백엔드](docs/guide/BACKEND.md), [프론트엔드](docs/guide/FRONTEND.md), [ai-service](docs/guide/AI_SERVICE.md), [로컬 인프라](docs/guide/LOCAL_ENV.md), [원본 데이터 아카이브](docs/guide/RAW_ARCHIVE.md), [운영 인프라](infra/prod/README.md) |
| 협업 | [개발 규칙](docs/team/CONVENTIONS.md), [역할과 일정](docs/team/ROLES_AND_SCHEDULE.md) |
| 전체 | [문서 안내](docs/README.md) |

## 빠른 로컬 실행

Docker Desktop을 사용한다.

### 1. 환경 변수 준비

`.env.example`을 복사해 저장소 루트에 `.env`를 만들고, 파일 안 주석에 따라 값을 채운다.

### 2. 실행

Docker Desktop을 켠 뒤 저장소 루트에서 실행한다.
`8080` 포트를 사용하는 기존 백엔드는 먼저 종료한다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env up -d --build --wait
```

- 프론트엔드: `http://localhost:5173`
- 백엔드: `http://localhost:8080`
- ai-service: `http://localhost:8000`
- 상태 확인: `docker compose -f infra/local/docker-compose.yml --env-file .env ps`
- 경기 시뮬레이션은 [백엔드 가이드](docs/guide/BACKEND.md)를 따른다.
