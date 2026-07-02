# PULSE — 팀원 초기 설정 가이드

> 프로젝트 소개 README는 완성 후 교체한다. 이 문서는 레포를 처음 받은 팀원의 로컬 개발 환경 설정 가이드다.

스포일러 프리 야구 관전 타이밍 추천 서비스. 아래 설정을 마치면 backend, frontend, ai-service 담당 기능 개발을 시작할 수 있다.

---

## 0. 사전 설치

| 도구 | 버전 |
|---|---|
| Git | 최신 |
| JDK | 21 |
| Docker Desktop | 최신 |
| Node.js | 20 이상 (frontend 담당) |
| Python | 3.12 (ai-service 담당) |
| IDE | IntelliJ IDEA (backend) / VS Code (frontend, ai-service) |

- Docker Desktop은 실행 중이어야 한다.
- 터미널 명령어는 **Git Bash 기준**으로 작성한다. Windows에서도 Git Bash 사용을 권장한다.

---

## 1. 레포 클론

```bash
git clone https://github.com/team-pulse-mlb/pulse-project.git
cd pulse-project
git config user.name "본인 GitHub 이름"
git config user.email "본인 GitHub 이메일"
```

---

## 2. 환경변수 설정

```bash
cp .env.example .env
```

`.env`에서 아래 값만 먼저 채운다.

| 변수 | 설명 |
|---|---|
| `BDL_API_KEY` | balldontlie API 키 |
| `POSTGRES_PASSWORD` | 로컬 PostgreSQL 비밀번호. 예: `pulse` |
| `RABBITMQ_PASSWORD` | 로컬 RabbitMQ 비밀번호. 예: `pulse` |
| `OPENAI_API_KEY` | ai-service 담당만 필요 |

`POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`는 팀원마다 다르게 정해도 된다. 단, Spring Boot, DBeaver, RabbitMQ 관리 UI에서도 같은 값을 사용해야 한다.

`.env`는 커밋하지 않는다.

---

## 3. 인프라 실행

```bash
docker compose -f infra/docker-compose.yml up -d
docker ps
```

Docker Compose가 PostgreSQL, Redis, RabbitMQ 컨테이너를 만든다.

| 항목 | 로컬 접속 |
|---|---|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| RabbitMQ 관리 UI | http://localhost:15672 |

PostgreSQL DB/유저 기본값은 `pulse`다. Redis는 로컬 개발에서 비밀번호 없이 사용한다.

주의: PostgreSQL 비밀번호는 Docker 볼륨이 처음 만들어질 때 적용된다. 이미 컨테이너를 띄운 뒤 `.env`의 `POSTGRES_PASSWORD`를 바꾸면 기존 DB 비밀번호가 자동으로 바뀌지 않을 수 있다.

---

## 4. backend 실행

IntelliJ에서 `backend/` 폴더를 열고 Run Configuration을 만든다.

간단 설정:

1. `PulseApplication`을 실행 대상으로 선택한다.
2. Run Configuration의 `Active profiles`에 원하는 프로필을 입력한다.
3. 개발용 데이터가 필요하면 `api,dev`를 입력하고 실행한다.

| 프로필 | 용도 |
|---|---|
| `api` | REST API, Swagger 확인 |
| `api,dev` | 실제 경기 시간이 아닐 때 샘플 데이터로 API 확인 |
| `poller` | balldontlie 데이터 수집 |
| `scorer` | 추천 점수 계산, Redis 랭킹 저장 |

처음 개발할 때는 `api,dev`로 실행하면 된다. 이 프로필은 샘플 경기, play, 추천 점수, Redis 랭킹을 로컬에 자동으로 넣고, 진행 중 샘플 경기 `900001`을 3초마다 갱신한다.

샘플 `gameId`:

| gameId | 상태 |
|---|---|
| `900001` | 진행 중 |
| `900002` | 예정 |
| `900003` | 종료 |

`900001`은 시간이 지나면 `plays`, `watch_scores`, Redis `score:rank:live` 값이 계속 바뀐다.

---

## 5. API 확인

Spring Boot를 `api` 또는 `api,dev` 프로필로 실행한 뒤 Swagger UI를 연다.

```text
http://localhost:8080/swagger-ui/index.html
```

Swagger에서 `game-controller`, `ai-context-controller`를 펼치고 `Try it out`으로 테스트한다.

개발용 진행 중 경기 확인:

```bash
curl "http://localhost:8080/api/games/900001?mode=PROTECTED"
curl "http://localhost:8080/api/ai/games/900001/spoiler-free-context"
curl "http://localhost:8080/api/rankings/live"
```

---

## 6. DB와 Redis 확인

### DBeaver

PostgreSQL 연결:

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `pulse` |
| Username | `pulse` |
| Password | `.env`의 `POSTGRES_PASSWORD` |

주요 테이블:

| 테이블 | 의미 |
|---|---|
| `games` | 경기 최신 상태 |
| `plays` | 경기별 play 로그 |
| `watch_scores` | 추천 점수 계산 이력 |

### RedisInsight

Redis 연결:

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `6379` |
| Password | 비움 |

실시간 랭킹 key:

```text
score:rank:live
```

---

## 7. frontend / ai-service

frontend:

```bash
cd frontend
```

이후 `frontend/README.md`를 따른다.

ai-service:

```bash
cd ai-service
python -m venv .venv
source .venv/Scripts/activate
```

이후 `ai-service/README.md`를 따른다.

---

## 8. 작업 흐름

`main`에는 직접 커밋하지 않는다. 작업은 새 브랜치에서 진행하고 PR로 병합한다.

```bash
git checkout main
git pull
git checkout -b feat/{이름}-{작업}
```

커밋 메시지 예시:

```text
feat: 경기 상세 API 추가
fix: 랭킹 빈 응답 처리
docs: 온보딩 문서 정리
```

PR은 작게 올리고, 병합 전 담당 영역 검증을 실행한다.

```bash
cd backend
./gradlew build
```

---

## 9. 자주 막히는 부분

| 증상 | 확인할 것 |
|---|---|
| DB 연결 실패 | Docker Desktop 실행 여부, `docker ps`, `.env` 비밀번호 |
| Swagger가 안 열림 | Spring Boot를 `api` 또는 `api,dev`로 실행했는지 |
| 랭킹이 비어 있음 | 실제 진행 중 경기가 없을 수 있음. 개발 중에는 `api,dev` 사용 |
| 개발용 데이터가 안 바뀜 | `api,dev` 프로필로 실행했는지 확인 |
| poller 401/403 | `BDL_API_KEY` 설정 |

| 도커 연결 오류 관련 해당 오류 뜨면 참고 |
| error while interpolating services.postgres.environment.POSTGRES_PASSWORD: required variable POSTGRES_PASSWORD is missing a value: POSTGRES_PASSWORD required |
| docker.exe compose --env-file .env -f infra/docker-compose.yml up -d 이렇게 실행할 것 |

설정 중 막히면 이 문서를 고쳐서 PR로 올려 주세요.
