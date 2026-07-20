# PULSE 팀 개발 규칙

## 1. 브랜치 규칙

`main`에는 직접 커밋하지 않는다. 모든 작업은 새 브랜치에서 진행하고 PR로 병합한다.

브랜치 이름은 아래 형식을 사용한다.

| 종류 | 형식 | 예시 |
|---|---|---|
| 기능 추가 | `feat/{이름}-{작업}` | `feat/minseok-game-detail` |
| 버그 수정 | `fix/{이름}-{작업}` | `fix/yeeun-ranking-empty` |
| 문서 수정 | `docs/{이름}-{작업}` | `docs/changhyun-api-guide` |
| 리팩터링 | `refactor/{이름}-{작업}` | `refactor/yeeun-score-service` |
| 테스트 | `test/{이름}-{작업}` | `test/minseok-score-calculator` |
| 점수 상수 조정 | `tune/{이름}-{작업}` | `tune/yeeun-scoring-weight` |

작업 시작 전에는 항상 최신 `main`에서 브랜치를 만든다.

```bash
git checkout main
git pull
git checkout -b feat/{이름}-{작업}
```

## 2. 커밋 메시지 규칙

커밋 메시지는 아래 형식을 사용한다.

```text
type: 변경 내용을 한 문장으로 요약
```

사용하는 타입은 아래로 제한한다.

| 타입 | 사용 시점 | 예시 |
|---|---|---|
| `feat` | 사용자에게 보이는 기능 추가 | `feat: 실시간 랭킹 API 추가` |
| `fix` | 버그 수정 | `fix: 진행 중 경기만 랭킹에 포함되도록 수정` |
| `docs` | README, API 문서, 팀 규칙 수정 | `docs: 브랜치 규칙 문서 추가` |
| `refactor` | 동작 변화 없는 구조 개선 | `refactor: 점수 계산 로직 분리` |
| `test` | 테스트 추가 또는 수정 | `test: 점수 계산 경계값 테스트 추가` |
| `style` | 포맷팅, 네이밍 등 동작 없는 코드 정리 | `style: 메서드 이름 정리` |
| `build` | Gradle, Docker, 의존성 변경 | `build: PostgreSQL 드라이버 의존성 추가` |
| `ci` | GitHub Actions 등 CI 설정 변경 | `ci: backend 빌드 워크플로 추가` |
| `tune` | 추천 점수 상수 조정 | `tune: 역전 상황 가중치 조정` |

커밋에는 공동 작성자 꼬리말, 자동 생성 도구명, 개인 메모를 넣지 않는다.

## 3. PR 규칙

PR은 작게 만든다. 한 PR에는 한 가지 목적만 담는다.

PR 제목도 커밋 메시지와 같은 타입을 사용한다.

```text
feat: 실시간 랭킹 API 추가
docs: 팀 개발 규칙 추가
```

PR 본문에는 아래 내용을 적는다.

- 무엇을 바꿨는지
- 왜 바꿨는지
- 어떻게 확인했는지
- 기존 설계 문서나 전체 코드에 영향이 있으면 명시

병합은 GitHub의 **Squash and merge**를 사용한다.

## 4. 코드 컨벤션

### 공통

- 시크릿 값은 커밋하지 않는다. `.env`와 API 키는 로컬에만 둔다.
- 외부 API, S3, DB, Redis 설정값은 코드에 하드코딩하지 않는다.
- 이름은 줄임말보다 의미가 드러나는 단어를 사용한다.
- TODO는 담당자와 후속 작업이 명확할 때만 남긴다.

```java
// TODO(yeeun): 429 응답에 대한 backoff 정책 추가
```

### backend

- Java 21과 Spring Boot 기준으로 작성한다.
- backend 패키지는 기능별 경계를 유지하고, `domain`과 `common`만 공용으로 사용한다.
- 컨트롤러는 요청/응답 역할에 집중하고, 비즈니스 로직은 서비스로 옮긴다.
- 추천 점수 상수는 `backend/src/main/resources/scoring.yml`에만 둔다.
- `scoring.yml`을 바꾸면 `version`을 올리고 PR에 조정 근거를 적는다.
- 테스트는 최소한 계산 로직, 경계값, 실패하기 쉬운 조건에 추가한다.

### frontend

- 초기 프로젝트가 생성된 뒤에는 컴포넌트, API 호출, 상태 관리를 분리한다.
- 보호 모드 화면에는 점수, 승패, 팀 우세처럼 스포일러가 되는 값을 노출하지 않는다.
- API 응답 타입과 화면 표시용 데이터를 구분한다.

### ai-service

- 모델 호출 프롬프트와 응답 스키마는 문서화한다.
- API 키와 모델명은 환경변수로 관리한다.
- 추천 이유 문구는 스포일러 프리 정책을 먼저 통과해야 한다.

## 5. 작업 전 확인

PR을 올리기 전에 담당 영역에 맞는 검증을 실행한다.

```bash
# backend (JDK 21 기준)
cd backend && ./gradlew build

# frontend
cd frontend && npm run lint && npm run build

# ai-service
cd ai-service && python -m unittest discover -s tests
```
