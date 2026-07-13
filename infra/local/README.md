# 로컬 인프라

로컬 개발용 PostgreSQL 16·Redis 7·RabbitMQ 3.13 구성이다.

## 구성

| 서비스 | 포트 | 데이터 |
|---|---|---|
| PostgreSQL | `5432` | `pulse-pgdata` Docker 볼륨 |
| Redis | `6379` | 컨테이너 종료 시 초기화 |
| RabbitMQ | `5672` | 로컬 메시지 큐 |
| RabbitMQ 관리 화면 | `15672` | 브라우저 접속 |

## 실행

Docker Desktop을 실행한다. VS Code 탐색기에서 `.env.example`을 복사해 `.env`를 만든 뒤 `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `JWT_SECRET`을 설정한다. `.env`는 커밋하지 않는다.

저장소 루트의 Git Bash에서 실행한다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env up -d
docker compose -f infra/local/docker-compose.yml --env-file .env ps
```

세 서비스가 `healthy`이면 준비가 끝난다.

## 상태 확인

```bash
docker exec pulse-postgres pg_isready
docker exec pulse-redis redis-cli ping
docker exec pulse-rabbitmq rabbitmq-diagnostics -q ping
```

정상 응답:

- PostgreSQL: `accepting connections`
- Redis: `PONG`
- RabbitMQ: `Ping succeeded`

로그는 Docker Desktop의 **Containers** 화면에서 확인한다.

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
