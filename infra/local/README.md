# 로컬 인프라

로컬 개발용 PostgreSQL 16·Redis 7 Docker Compose 구성이다. 운영 배포에는 사용하지 않는다. 운영 인프라는 [운영 인프라 가이드](../prod/README.md)를 따른다.

## 요구사항

- Docker
- Docker Compose

현재 Compose에는 RabbitMQ가 없다. 메시지 큐 흐름 검증에는 팀의 별도 RabbitMQ 구성이 필요하다.

## 실행

1. VS Code Explorer에서 `.env.example`을 복사해 `.env`를 만든다.
2. `.env`의 `POSTGRES_PASSWORD`를 설정한다.
3. 저장소 루트의 VS Code Git Bash 터미널에서 실행한다.

- Spring Boot와 DB 클라이언트에도 같은 접속값을 사용한다.
- `.env`는 커밋하지 않는다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env up -d
```

## 확인

Git Bash 터미널에서 두 컨테이너가 `healthy`인지 확인한다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env ps
docker exec pulse-postgres pg_isready
docker exec pulse-redis redis-cli ping
```

- PostgreSQL·Redis 상태: `healthy`
- PostgreSQL 응답: `accepting connections`
- Redis 응답: `PONG`
- 실행 오류나 상세 로그가 필요하면 Docker Desktop의 **Containers**에서 컨테이너를 선택하고 **Logs** 탭을 확인한다.

## 포트와 데이터

| 항목 | 기본값 | 변경 변수 |
|---|---|---|
| PostgreSQL 포트 | `5432` | `POSTGRES_PORT` |
| Redis 포트 | `6379` | `REDIS_PORT` |
| PostgreSQL 데이터 | `pulse-pgdata` 볼륨 | Compose 자동 생성 |

`infra/local/init/`은 빈 PostgreSQL 볼륨의 최초 기동 시 실행할 초기화 스크립트 위치다. 현재는 `.gitkeep`만 있고 초기화 SQL은 없다.

## 중지와 초기화

일반 중지는 저장소 루트의 Git Bash 터미널에서 실행한다. 데이터 볼륨은 유지된다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env down
```

데이터까지 삭제한다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env down -v
```

> `down -v`는 로컬 PostgreSQL 데이터를 복구할 수 없게 삭제한다. 초기화할 때만 실행한다.

## 운영 인프라

- AWS 운영 구성: [운영 인프라 가이드](../prod/README.md)
- 운영 시크릿: AWS Secrets Manager나 승인된 배포 환경 변수로 관리한다.
- 운영 시크릿을 이 폴더나 저장소에 두지 않는다.
