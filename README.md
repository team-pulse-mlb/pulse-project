# PULSE — 팀원 초기 설정 가이드

> 프로젝트 소개 README는 완성 후 교체한다. 이 문서는 레포를 처음 받은 팀원의 개발 환경 설정 가이드다.

스포일러 프리 야구 관전 타이밍 추천 서비스. 데이터 파이프라인(외부 API 폴링 → 추천 점수 계산 → DB/Redis 저장)이 이미 구현되어 있으므로, 아래 설정을 마치면 각자 담당 기능 개발을 시작할 수 있다.

---

## 0. 사전 설치

| 도구 | 버전 | 확인 명령 |
|---|---|---|
| Git | 최신 | `git --version` |
| JDK | **21** (Temurin 또는 Oracle) | `java -version` |
| Docker Desktop | 최신 | `docker --version` |
| Node.js | 20 이상 (frontend 담당) | `node --version` |
| Python | 3.12 (ai-service 담당) | `python --version` |
| IDE | IntelliJ IDEA (backend) / VS Code (frontend, ai-service) | — |

Docker Desktop은 설치 후 **실행 중 상태**여야 한다.

---

## 1. 레포 클론과 Git 설정

```bash
git clone https://github.com/team-pulse-mlb/pulse-project.git
cd pulse-project

# 커밋 작성자 정보 (GitHub 계정과 동일하게)
git config user.name "본인 GitHub 이름"
git config user.email "본인 GitHub 이메일"
```

---

## 2. 환경변수 설정

```bash
cp .env.example .env
```

`.env`를 열어 값을 채운다. **로컬 개발 기준으로는 두 개만 채우면 된다.**

| 변수 | 값 | 받는 곳 |
|---|---|---|
| `BDL_API_KEY` | balldontlie API 키 | 예은 (팀 공용 키) |
| `OPENAI_API_KEY` | OpenAI API 키 (ai-service 담당만) | 창현 |

나머지 DB/Redis/RabbitMQ 값은 비워 두면 로컬 기본값(`pulse`/`pulse`)이 사용된다.

`.env`는 절대 커밋하지 않는다 (`.gitignore`에 이미 등록됨).

---

## 3. 인프라 기동 (PostgreSQL + Redis + RabbitMQ)

```bash
docker compose -f infra/docker-compose.yml up -d
```

확인:

```bash
docker ps
# pulse-postgres, pulse-redis, pulse-rabbitmq 3개가 Up (healthy) 상태면 정상
```

RabbitMQ 관리 UI: http://localhost:15672 (계정 `pulse` / `pulse`) — 메시지 흐름을 눈으로 확인할 수 있다.

---

## 4. backend 실행 (전원 공통)

backend는 프로필 3개로 나뉜 하나의 Spring Boot 프로젝트다.

| 프로필 | 역할 |
|---|---|
| `api` | REST API + SSE (포트 8080) |
| `poller` | balldontlie 폴링 → DB 저장 → RabbitMQ 발행 |
| `scorer` | 메시지 소비 → watch_score 계산 → Redis 랭킹 |

### IntelliJ로 여는 경우

1. IntelliJ에서 `backend/` 폴더를 프로젝트로 열기 (Gradle 자동 인식)
2. File → Project Structure → SDK가 **21**인지 확인
3. Run Configuration의 Active profiles에 `api` 입력 후 실행
4. poller, scorer도 각각 Run Configuration을 만들어 실행 (환경변수 `BDL_API_KEY`는 poller 설정에 추가)

### 터미널로 실행하는 경우 (터미널 3개)

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=api'
./gradlew bootRun --args='--spring.profiles.active=poller'    # BDL_API_KEY 환경변수 필요
./gradlew bootRun --args='--spring.profiles.active=scorer'
```

### 동작 확인

```bash
# API 서버 상태
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

# 파이프라인 확인 (poller + scorer가 돌고 MLB 경기가 진행 중일 때)
curl http://localhost:8080/api/rankings/live
# → [{"gameId":5059041,"watchScore":73.0}, ...]
```

진행 중인 MLB 경기가 없는 시간대(한국 기준 낮~저녁)에는 랭킹이 비어 있는 것이 정상이다.

### 담당 기능에서 사용할 API

프론트엔드와 ai-service 담당자는 DB나 Redis에 직접 연결하지 않고 Spring Boot API를 통해 데이터를 받는다.

API 명세와 테스트는 Swagger UI에서 확인한다.

```text
http://localhost:8080/swagger-ui/index.html
```

브라우저 확인 순서:

1. `api` 프로필로 backend를 실행한다.
2. 브라우저에서 Swagger UI 주소를 연다.
3. `game-controller` 또는 `ai-context-controller` 섹션을 펼친다.
4. 사용할 API를 선택하고 `Try it out`을 누른다.
5. `gameId`, `mode`, `purpose` 값을 입력한 뒤 `Execute`를 누른다.

`gameId`는 DBeaver의 `games.id` 또는 `GET /api/rankings/live` 응답에서 확인한 값을 사용한다. 데이터가 아직 쌓이지 않았거나 해당 경기가 없으면 `404`가 나올 수 있다.

```bash
# 경기 상세 화면용
curl "http://localhost:8080/api/games/{gameId}?mode=PROTECTED"
curl "http://localhost:8080/api/games/{gameId}?mode=NORMAL"

# LLM 문구 생성용 스포일러 프리 context
curl "http://localhost:8080/api/ai/games/{gameId}/spoiler-free-context"
curl "http://localhost:8080/api/ai/games/{gameId}/spoiler-free-context?purpose=NOTIFICATION"
```

`mode=PROTECTED`는 점수, 득점 play처럼 스포일러가 될 수 있는 값을 숨긴다. 보호 모드 화면이나 LLM 문구 생성에는 이 모드를 우선 사용한다.
`mode=NORMAL`은 경기 상세 화면에서 실제 점수와 play별 점수 정보를 보여줘야 할 때 사용한다.

빌드와 테스트만 돌려보려면:

```bash
cd backend
./gradlew build
```

---

## 5. 로컬 데이터 적재와 시각 확인

아직 공용 배포 서버가 없으므로, 각자 로컬에서 `poller`와 `scorer`를 실행해 데이터를 쌓아야 한다.

```text
balldontlie API
  -> poller
  -> PostgreSQL(games, plays)
  -> RabbitMQ(score task)
  -> scorer
  -> PostgreSQL(watch_scores) + Redis(score:rank:live)
```

### 데이터가 쌓이는 조건

- Docker Desktop이 실행 중이어야 한다.
- `postgres`, `redis`, `rabbitmq` 컨테이너가 떠 있어야 한다.
- `poller` 프로필이 실행 중이어야 `games`, `plays`가 쌓인다.
- `scorer` 프로필이 실행 중이어야 `watch_scores`와 Redis 랭킹이 쌓인다.
- 진행 중인 MLB 경기가 없으면 `watch_scores`나 Redis 랭킹이 비어 있을 수 있다.

### DBeaver에서 PostgreSQL 확인

DBeaver에서 새 PostgreSQL 연결을 만든다.

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `pulse` |
| Username | `pulse` |
| Password | `.env`의 `POSTGRES_PASSWORD` 값 |

주요 테이블:

| 테이블 | 의미 |
|---|---|
| `games` | 경기 최신 상태 스냅샷 |
| `plays` | 경기별 play 로그 |
| `watch_scores` | 추천 점수 계산 이력 |

자주 쓰는 확인 쿼리:

```sql
select *
from games
order by updated_at desc;
```

```sql
select *
from plays
order by fetched_at desc;
```

```sql
select game_id, base_score, watch_score, signals, reason_tags, created_at
from watch_scores
order by created_at desc;
```

특정 경기의 점수 흐름:

```sql
select game_id, base_score, watch_score, signals, reason_tags, created_at
from watch_scores
where game_id = 123
order by created_at;
```

`123`은 실제 `games.id` 값으로 바꿔서 조회한다.

### RedisInsight에서 실시간 랭킹 확인

RedisInsight에서 새 Redis 연결을 만든다.

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `6379` |
| Username | 비움 |
| Password | 비움 |
| Database Alias | `pulse-local` |

연결 후 key 검색창에서 아래 key를 찾는다.

```text
score:rank:live
```

이 값은 Redis Sorted Set이다.

```text
member = gameId
score = watchScore
```

예를 들어 `5059041 -> 73.0`이면 `gameId=5059041`인 경기가 현재 추천 점수 `73.0`으로 랭킹에 들어간 상태다.

### RabbitMQ 관리 UI 확인

브라우저에서 http://localhost:15672 로 접속한다.

| 항목 | 값 |
|---|---|
| Username | `pulse` |
| Password | `.env`의 `RABBITMQ_PASSWORD` 값 |

주요 큐:

| 큐 | 의미 |
|---|---|
| `score.calculate.q` | poller가 발행하고 scorer가 소비하는 점수 계산 작업 |
| `score.calculate.dlq` | 처리 실패한 점수 계산 작업 |

---

## 6. ai-service 실행 (창현)

```bash
cd ai-service
python -m venv .venv
.venv\Scripts\activate          # macOS/Linux: source .venv/bin/activate
# 이후 FastAPI 프로젝트 초기 생성 — ai-service/README.md 참고
```

## 7. frontend 실행 (담당자)

```bash
cd frontend
# Vite 프로젝트 초기 생성 — frontend/README.md 참고
```

---

## 8. 브랜치 규칙과 작업 흐름

1. `main`에서 직접 작업 금지 (브랜치 보호 설정됨)
2. 작업 시작 시:
   ```bash
   git checkout main && git pull
   git checkout -b feat/{이름}-{작업}     # 예: feat/minseok-game-detail
   ```
3. 커밋 메시지: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `style:`, `build:`, `ci:`, `tune:`(점수 상수 조정)
4. push 후 GitHub에서 PR 생성 → 템플릿 체크리스트 작성 → 리뷰 1명 승인 → **squash merge**
5. 추천 점수 상수는 `backend/src/main/resources/scoring.yml`에만 있다. 수정 시 파일 상단 규칙(version 증가, 커밋 메시지에 근거)을 따른다.

---

## 9. 자주 발생하는 문제

| 증상 | 해결 |
|---|---|
| `docker compose` 실행 시 포트 충돌 (5432 등) | 로컬에 이미 설치된 PostgreSQL/Redis 종료 후 재시도 |
| backend 기동 시 DB 연결 실패 | `docker ps`로 컨테이너 healthy 확인. `.env` 값과 docker-compose 기본값 불일치 여부 확인 |
| poller가 401/403 에러 | `BDL_API_KEY` 미설정 또는 오타. 환경변수가 실행 프로세스에 전달됐는지 확인 |
| `./gradlew` 실행 시 JAVA_HOME 오류 | JDK 21 설치 후 JAVA_HOME 설정, 또는 IntelliJ Gradle JVM을 21로 지정 |
| 랭킹 API가 빈 배열 | 정상일 수 있음 — 진행 중 MLB 경기가 있는 시간대(한국 기준 아침)에 확인 |

설정 중 막히면 이 문서를 고쳐서 PR로 올려 주세요. 다음 사람이 같은 곳에서 안 막히도록.
