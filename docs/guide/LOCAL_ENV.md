# 로컬 인프라 가이드

로컬 개발용 전체 스택 구성이다. Docker Desktop을 사용한다.

## 빠른 실행

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
- 경기 시뮬레이션은 [LIVE_SIMULATOR.md](LIVE_SIMULATOR.md)를 따른다.

## 구성

| 서비스 | 포트 | 데이터 |
|---|---|---|
| PostgreSQL | `5432` | `pulse-pgdata` Docker 볼륨 |
| Redis | `6379` | 컨테이너 종료 시 초기화 |
| RabbitMQ | `5672` | 로컬 메시지 큐 |
| RabbitMQ 관리 화면 | `15672` | 브라우저 접속 |
| backend API | `8080` | Spring Boot API·SSE |
| ai-service | `8000` | AI 문구 생성 |
| frontend | `5173` | Vite 개발 서버 |

## 상태 확인

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env ps
```

모든 서비스가 `healthy`인지 확인한다. 로그는 Docker Desktop의 **Containers** 화면에서 확인한다.

## 종료와 초기화

데이터를 유지하고 종료한다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env down
```

PostgreSQL 데이터까지 초기화한다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env down -v
```

`down -v`로 삭제한 로컬 DB 데이터는 복구할 수 없다.
