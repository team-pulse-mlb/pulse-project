# PULSE — 팀원 초기 설정 가이드

이 문서는 레포를 처음 받은 팀원이 실제 라이브 경기 raw archive를 로컬에서 재생해 backend, frontend, ai-service 개발을 시작하는 절차를 정리한다.

---

## 0. 사전 설치

| 도구 | 버전 |
|---|---|
| Git | 최신 |
| JDK | 21 |
| Docker Desktop | 최신 |
| Node.js | 20 이상 |
| Python | 3.12 |
| AWS CLI | 최신 |
| IDE | IntelliJ IDEA / VS Code |

- Docker Desktop은 실행 중이어야 한다.
- S3 리플레이를 실행하는 사람은 AWS CLI 로그인 또는 환경변수 방식으로 `pulse-raw-<account-id>` 읽기 권한을 준비한다.
- 터미널 명령어는 Git Bash 기준이다.

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

`.env`에서 로컬 DB와 S3 리플레이 값을 채운다.

| 변수 | 설명 |
|---|---|
| `POSTGRES_PASSWORD` | 로컬 PostgreSQL 비밀번호. 예: `pulse` |
| `PULSE_REPLAY_S3_BUCKET` | S3 raw archive 버킷. 예: `pulse-raw-<account-id>` |
| `PULSE_REPLAY_GAME_ID` | 재생할 실제 라이브 경기 ID |
| `PULSE_REPLAY_DATE` | `raw/games/dt=YYYY-MM-DD/` 조회 날짜 |
| `AWS_REGION` | 기본값 `ap-northeast-2` |
| `OPENAI_API_KEY` | ai-service 담당만 필요 |

`.env`는 커밋하지 않는다.

S3 원본은 다음 형태를 기준으로 읽는다.

```text
raw/games/dt=YYYY-MM-DD/games_HHMMSSZ.json.gz
raw/plays/game_id=<id>/plays_YYYY-MM-DD_HHMMSSZ_c<cursor>.json.gz
```

각 객체는 `observed_at`, `endpoint`, `params`, `response`를 가진 gzip JSON이다. `backfilled: true` 객체는 로컬 리플레이의 시간 감쇠 계산에서 제외된다.

---

## 3. 인프라 실행

```bash
docker compose -f infra/docker-compose.yml --env-file .env up -d
docker ps
```

Docker Compose가 PostgreSQL과 Redis 컨테이너를 만든다.

| 항목 | 로컬 접속 |
|---|---|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |

PostgreSQL DB/유저 기본값은 `pulse`다. Redis는 로컬 개발에서 비밀번호 없이 사용한다.

---

## 4. S3 접근 확인

```bash
aws s3 ls s3://$PULSE_REPLAY_S3_BUCKET/raw/games/dt=$PULSE_REPLAY_DATE/ | tail
aws s3 ls s3://$PULSE_REPLAY_S3_BUCKET/raw/plays/game_id=$PULSE_REPLAY_GAME_ID/ | tail
```

PowerShell을 사용한다면 `$env:PULSE_REPLAY_S3_BUCKET` 형식으로 확인한다.

---

## 5. backend 실행

IntelliJ에서 `backend/` 폴더를 열고 `PulseApplication`을 실행한다.

Run Configuration의 `Active profiles`:

```text
api,replay
```

터미널 실행:

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=api,replay'
```

실행 시 backend는 S3 live archive를 시간순으로 읽고 로컬 PostgreSQL에 `games`, `plays`, `watch_scores`, `replay_segments`를 저장한다. 진행 중 경기의 최신 랭킹은 Redis `score:rank:live`에 저장된다.

---

## 6. API 확인

Spring Boot 실행 후 Swagger UI를 연다.

```text
http://localhost:8080/swagger-ui/index.html
```

```bash
curl "http://localhost:8080/api/games/$PULSE_REPLAY_GAME_ID?mode=PROTECTED"
curl "http://localhost:8080/api/games/$PULSE_REPLAY_GAME_ID?mode=REVEALED"
curl "http://localhost:8080/api/ai/games/$PULSE_REPLAY_GAME_ID/spoiler-free-context"
curl "http://localhost:8080/api/rankings/live"
```

---

## 7. DB와 Redis 확인

### DBeaver

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
| `plays` | S3 raw archive에서 재생한 play 로그 |
| `watch_scores` | 리플레이 중 계산한 추천 점수 이력 |
| `replay_segments` | 라이브 계산 중 열린 다시보기 추천 구간 |

### RedisInsight

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

## 8. frontend / ai-service

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

## 9. 작업 흐름

`main`에는 직접 커밋하지 않는다. 작업은 새 브랜치에서 진행하고 PR로 병합한다.

```bash
git checkout main
git pull origin main
git checkout -b feat/{이름}-{작업}
git add .
git commit -m "feat: add replay local loader"
git push origin feat/{이름}-{작업}
```

병합 전 담당 영역 검증을 실행한다.

```bash
cd backend
./gradlew test
```

---

## 10. 자주 막히는 부분

| 증상 | 확인할 것 |
|---|---|
| DB 연결 실패 | Docker Desktop 실행 여부, `docker ps`, `.env` 비밀번호 |
| Swagger가 안 열림 | Spring Boot를 `api,replay` 프로필로 실행했는지 |
| S3 접근 실패 | AWS 로그인, 버킷명, `AWS_REGION`, S3 읽기 권한 |
| 랭킹이 비어 있음 | `PULSE_REPLAY_GAME_ID`에 진행 중 경기 raw가 있는지 |
| 점수 이력이 적음 | `PULSE_REPLAY_DATE`, `PULSE_REPLAY_MAX_OBJECTS_PER_PREFIX` 값 |

설정 중 막히면 이 문서를 고쳐서 PR로 올린다.
