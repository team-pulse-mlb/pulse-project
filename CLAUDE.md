# CLAUDE.md

PULSE 레포에서 작업할 때의 기준. 이미 있는 문서를 복붙하지 말고 링크로 위임한다. 여기에는 이 레포에서만 통하는 불변 원칙과, 코드·git 히스토리로는 안 드러나는 규칙만 둔다.

## 프로젝트

스포일러 프리 MLB 관전 타이밍 추천 서비스. balldontlie MLB API를 poller가 폴링 → RabbitMQ → scorer가 `watch_score` 계산 → Redis 랭킹 → api가 REST/SSE로 제공. 스포일러 보호는 **서버 응답 DTO 단계에서 강제**한다.

- 아키텍처·데이터 흐름: [docs/ARCHITECTURE_AND_DATA_FLOW.md](docs/ARCHITECTURE_AND_DATA_FLOW.md)
- 팀 개발 규칙(브랜치·커밋·PR·코드 컨벤션): [docs/CONVENTIONS.md](docs/CONVENTIONS.md)
- 로컬 셋업(S3 리플레이 실행): [docs/ONBOARDING.md](docs/ONBOARDING.md)
- 외부 API 실측 명세: [docs/EXTERNAL_DATA_API.md](docs/EXTERNAL_DATA_API.md) · 점수 계산: [docs/RECOMMENDATION_SCORE.md](docs/RECOMMENDATION_SCORE.md)
- 모듈별 상세: [backend/README.md](backend/README.md) · [ai-service/README.md](ai-service/README.md) · [frontend/README.md](frontend/README.md)

## 스택과 구조

| 모듈 | 스택 | 위치 |
|---|---|---|
| backend | Spring Boot 3.5 / Java 21 (프로필 `api`, `replay`) | [backend/](backend/) |
| ai-service | FastAPI / Python 3.12 | [ai-service/](ai-service/) |
| frontend | React + Vite (Vercel) | [frontend/](frontend/) |
| infra | PostgreSQL · Redis · RabbitMQ (Docker Compose) | [infra/](infra/) |

backend 패키지: `api`(컨트롤러·DTO) · `replay`(S3 재생·점수 계산) · `ranking`(Redis 랭킹) · `domain`(JPA) · `common`(설정·외부 클라이언트).

## 명령어

```bash
# 인프라
docker compose -f infra/docker-compose.yml --env-file .env up -d

# backend (Git Bash 기준, Windows는 gradlew.bat)
cd backend
./gradlew build                                              # 빌드+테스트 (PR 전 필수)
./gradlew test                                               # 테스트만
./gradlew bootRun --args='--spring.profiles.active=api,replay'   # 로컬 리플레이 실행

# ai-service
cd ai-service && uvicorn app.main:app --reload --port 8000
```

Swagger: `http://localhost:8080/swagger-ui/index.html`

## 설계 원칙 (깨지 않는다)

1. 외부 MLB API는 **서버에서만** 호출한다. frontend는 `pulse-api`만 부른다.
2. 추천 판단은 backend가 한다. ai-service는 문구 생성·스포일러 검수만 한다.
3. 스포일러 보호는 프론트가 아니라 **서버 응답 단계에서 강제**한다. 보호 모드 응답에 점수·승패·팀 우세를 노출하는 필드를 추가하지 않는다.
4. PostgreSQL = 오래 남길 원본·이력. Redis = 지금 화면에 필요한 최신 상태.
5. 추천 점수 상수는 [backend/src/main/resources/scoring.yml](backend/src/main/resources/scoring.yml)에만 둔다. 바꾸면 `version`을 올리고 조정 근거를 커밋 메시지에 적는다.
6. 시크릿(`.env`, API 키)은 커밋하지 않는다. 외부 API·S3·DB·Redis 설정값을 코드에 하드코딩하지 않는다.

## git 규칙 (이 레포 전용 — 기본 동작을 덮어쓴다)

- **`main`에 직접 커밋·push하지 않는다.** 새 브랜치에서 작업한다.
- 브랜치명: `feat|fix|docs|refactor|test|tune/{이름}-{작업}`. 커밋: `type: 한 문장 요약` (타입은 [CONVENTIONS.md §2](docs/CONVENTIONS.md) 참고).
- **커밋 메시지에 공동 작성자 꼬리말(`Co-Authored-By`)이나 자동 생성 도구명을 넣지 않는다.** 이 레포는 이를 금지하며 PR 체크리스트로도 검사한다.
- 레포 주인이 직접 병합하므로 **PR은 만들지 않는다.** 요청받은 작업은 브랜치 push까지만 하고 멈춘다. `main` push는 사용자가 직접 한다.
- push 전에 담당 영역 검증(`./gradlew build` 등)을 돌린다.

## 작업 태도

- 요청받은 것만 한다. 옆에서 발견한 문제는 고치지 말고 알린다.
- 사용자가 문제를 설명하거나 질문·검토를 요청하면, 산출물은 **판단**이다. 고치라는 말 전까지 진단만 보고하고 멈춘다.
- 필요 이상의 추상화·리팩터링·방어 코드를 넣지 않는다. 내부 코드와 프레임워크 보장은 신뢰하고, 검증은 시스템 경계(사용자 입력·외부 API)에서만 한다.
- 문서·주석·메시지는 이 레포 스타일대로 한국어로, 간결하게.
