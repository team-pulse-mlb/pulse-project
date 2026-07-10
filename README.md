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
├── infra/         # 로컬 PostgreSQL·Redis
└── docs/          # 제품·설계·팀 문서
```

- [프론트엔드 가이드](frontend/README.md)
- [백엔드 가이드](backend/README.md)
- [로컬 인프라 가이드](infra/README.md)
- [원본 데이터 아카이브 가이드](raw-archive/README.md)
- [AWS 운영 인프라 가이드](docs/team/INFRA.md)

## 빠른 시작

1. VS Code에서 저장소를 열고 `.env.example`을 `.env`로 복사해 로컬 값을 입력한다.
2. VS Code의 Container Tools 확장으로 `infra/docker-compose.yml`의 PostgreSQL·Redis를 실행하고 Docker Desktop에서 상태를 확인한다.
3. IntelliJ에서 `backend/`를 Gradle 프로젝트로 열고 `PulseApplication`을 실행한다.
4. VS Code의 NPM Scripts 보기에서 `frontend`의 `dev` 스크립트를 실행한다.

- 프론트엔드: `http://localhost:5173`
- 백엔드: `http://localhost:8080`
- 백엔드 실행 전 `.env.example`을 참고해 `JWT_SECRET` 등 필요한 환경 변수를 IntelliJ 실행 구성에 설정한다.
- 터미널 실행과 세부 설정은 각 폴더의 README에서 보조 방법으로 제공한다.

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
