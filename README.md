<p align="center">
  <img src="frontend/public/pulse-logo.png" alt="PULSE 로고" width="220" />
</p>

<p align="center"><strong>스포일러 프리 MLB 관전 타이밍 추천 서비스</strong></p>

## 문제와 해결

| 문제 | 해결 |
|---|---|
| 동시에 진행되는 여러 경기 중 지금 볼 경기를 고르기 어렵다. | 진행 중 경기의 관전 가치를 계산해 추천순으로 보여준다. |
| 경기 흐름을 확인하는 과정에서 점수와 결과를 먼저 알게 된다. | 진행 중·종료 경기 상세를 보호 모드로 시작하고, 결과를 숨긴 경기 흐름을 제공한다. 사용자가 선택한 경기만 공개 모드로 전환한다. |
| 경기를 계속 확인하지 않으면 볼 만한 순간이나 다른 경기의 상승 흐름을 놓치기 쉽다. | 급상승 경기와 관심 팀 경기 시작을 알리고, 시청 중인 경기보다 관전 가치가 높은 경기가 생기면 이동을 제안한다. |

## 핵심 기능

| 기능 | 설명 |
|---|---|
| 실시간 경기 추천 | 진행 중인 경기의 관전 가치를 계산해 지금 볼 만한 순서로 보여준다. 로그인 사용자는 관심 팀·선수에 따라 개인화된 순서를 볼 수 있다. |
| 스포일러 보호와 공개 | 진행 중·종료 경기 모두 보호 모드로 시작한다. 보호 모드에서는 점수와 승패를 숨긴 경기 흐름을 보여주며, 종료 경기에는 AI 헤드라인·긴장 곡선·보호 안전 이벤트를 제공한다. 사용자가 공개로 전환하면 점수와 상세 결과를 볼 수 있다. |
| 관전 타이밍 알림 | 급상승 경기와 관심 팀 경기 시작을 인앱 토스트와 알림 센터로 알려준다. 경기 상세를 보고 있을 때 더 볼 만한 경기가 생기면 토스트로 이동을 제안한다. |

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, react-router v8 |
| Backend | Java 21, Spring Boot, Spring Data JPA, Flyway |
| Data | PostgreSQL 16, Redis 7 |
| AI | Python, FastAPI, Pydantic, OpenAI API |
| 실시간 | Server-Sent Events(SSE) |
| Infra | Docker Compose, AWS EC2·RDS·S3 |

## 저장소 구조

```text
pulse-project/
├── frontend/      # React 웹 애플리케이션
├── backend/       # 수집·점수화·랭킹·REST API
├── ai-service/    # AI 문구 생성·스포일러 검수
├── raw-archive/   # 원본 데이터 수집·백필·분석
├── infra/         # 인프라 구성(local: 로컬 개발용, prod: AWS 운영)
└── docs/          # 제품·설계·팀 문서
```

- [프론트엔드 가이드](frontend/README.md)
- [백엔드 가이드](backend/README.md)
- [로컬 인프라 가이드](infra/local/README.md)
- [운영 인프라 가이드](infra/prod/README.md)
- [원본 데이터 아카이브 가이드](raw-archive/README.md)

## 운영 배포

웹 브라우저에서 [https://pulsemlb.com](https://pulsemlb.com)에 접속한다.

## 빠른 로컬 실행

Docker Desktop을 사용한다.

### 1. 환경 변수 준비

VS Code 탐색기에서 `.env.example`을 복사해 `.env`를 만든다. `POSTGRES_PASSWORD`와 `RABBITMQ_PASSWORD`는 로컬에서 사용할 비밀번호로 설정한다.

`JWT_SECRET`에는 로그인 토큰이 아니라 JWT 서명에 사용할 서버 비밀키를 넣는다.
저장소 루트의 Git Bash에서 다음과 같이 생성할 수 있다.

```bash
openssl rand -base64 32 | tr '+/' '-_' | tr -d '='
```

출력된 값을 `.env`의 `JWT_SECRET=` 뒤에 붙인다. 이 값은 로컬 전용이며 저장소에 커밋하지 않는다.

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
- 시뮬레이션은 backend README를 따른다.

## 문서 인덱스

| 구분 | 문서 |
|---|---|
| 제품 | [기능 명세](docs/product/FEATURE_SPEC.md), [사용자 흐름](docs/product/USER_FLOW.md), [프로젝트 제안](docs/product/PROJECT_PROPOSAL.md) |
| 설계 | [아키텍처](docs/design/ARCHITECTURE.md), [데이터 파이프라인](docs/design/DATA_PIPELINE.md), [API 계약](docs/design/API_CONTRACTS.md), [스포일러 정책](docs/design/SPOILER_POLICY.md), [DB 스키마](docs/design/DB_SCHEMA.md) |
| 협업 | [개발 규칙](docs/team/CONVENTIONS.md), [역할과 일정](docs/team/ROLES_AND_SCHEDULE.md) |
| 전체 | [문서 안내](docs/README.md) |

## 팀

| 담당자 | 역할 |
|---|---|
| 예은 | 데이터 파이프라인, 점수·추천, 홈, 공통 구조 |
| 창현 | AI 문구 생성·검수 |
| 민석 | 경기 상세, 다시보기, 전환 알림 |
| 윤호 | 회원, 알림, 통합 후 관측 |

세부 경계와 일정은 [역할 분담 및 일정 계획](docs/team/ROLES_AND_SCHEDULE.md)을 따른다.
