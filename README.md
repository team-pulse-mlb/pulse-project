<p align="center">
  <img src="frontend/public/pulse-logo.png" alt="PULSE 로고" width="220" />
</p>

<p align="center"><strong>스포일러 프리 MLB 관전 타이밍 추천 서비스</strong></p>

## 문제와 해결

- 중계·하이라이트를 찾다가 경기 결과를 먼저 알게 되는 문제를 줄인다.
- 점수와 승패를 숨긴 채 지금 볼 만한 경기를 점수화해 추천한다.
- 보호·공개 모드로 스포일러 노출 범위를 사용자가 선택한다.
- 종료 경기도 긴장 곡선과 주요 순간으로 다시 볼 구간을 고른다.

## 핵심 기능

| 기능 | 설명 |
|---|---|
| 홈 추천 | Bento 경기 카드와 실시간 추천 순위 |
| 경기 상세 | 보호·공개 모드 전환 |
| 종료 경기 | 점수 비노출 긴장 곡선과 주요 이벤트 |
| 관심 설정 | 관심 팀·선수, 경기 시작·급상승·전환 알림 |
| AI 문구 | 모드별 헤드라인과 이벤트 카피 |

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
