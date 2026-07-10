# 로컬 인프라

로컬 개발용 PostgreSQL 16·Redis 7 Docker Compose 구성이다. 운영 배포에는 사용하지 않는다.

## 요구사항

- Docker
- Docker Compose

현재 Compose에는 RabbitMQ가 없다. 메시지 큐 흐름 검증에는 팀의 별도 RabbitMQ 구성이 필요하다.

## 실행

### VS Code와 Docker Desktop

1. VS Code Explorer에서 `.env.example`을 복사해 `.env`를 만든다.
2. `.env`의 `POSTGRES_PASSWORD`를 설정한다.
3. Microsoft Container Tools 확장을 설치한다.
4. `infra/docker-compose.yml`을 우클릭하고 **Compose Up**을 선택한다.
5. Docker 보기 또는 Docker Desktop의 **Containers**에서 `pulse-postgres`, `pulse-redis` 상태를 확인한다.

- Spring Boot와 DB 클라이언트에도 같은 접속값을 사용한다.
- `.env`는 커밋하지 않는다.

### 터미널 대체 방법

VS Code의 Compose 메뉴를 사용할 수 없을 때 저장소 루트에서 실행한다.

```bash
cp .env.example .env
```

PowerShell:

```powershell
Copy-Item .env.example .env
```

```powershell
docker compose -f infra/docker-compose.yml --env-file .env up -d
```

## 확인

Docker Desktop의 **Containers**에서 두 컨테이너가 `Running (healthy)`인지 확인한다. 로그는 컨테이너 이름을 선택한 뒤 **Logs** 탭에서 확인한다.

명령으로 상태를 확인해야 할 때만 다음을 사용한다.

```powershell
docker compose -f infra/docker-compose.yml --env-file .env ps
docker exec pulse-postgres pg_isready
docker exec pulse-redis redis-cli ping
```

- PostgreSQL·Redis 상태: `healthy`
- PostgreSQL 응답: `accepting connections`
- Redis 응답: `PONG`

## 포트와 데이터

| 항목 | 기본값 | 변경 변수 |
|---|---|---|
| PostgreSQL 포트 | `5432` | `POSTGRES_PORT` |
| Redis 포트 | `6379` | `REDIS_PORT` |
| PostgreSQL 데이터 | `pulse-pgdata` 볼륨 | Compose 자동 생성 |

`infra/init/`은 빈 PostgreSQL 볼륨의 최초 기동 시 실행할 초기화 스크립트 위치다. 현재는 `.gitkeep`만 있고 초기화 SQL은 없다.

## 중지와 초기화

일반 중지는 VS Code Docker 보기 또는 Docker Desktop에서 두 컨테이너의 **Stop**을 선택한다. 데이터 볼륨은 유지된다.

Compose 리소스를 제거할 때만 다음 명령을 사용한다.

```powershell
docker compose -f infra/docker-compose.yml --env-file .env down
```

데이터까지 삭제한다.

```powershell
docker compose -f infra/docker-compose.yml --env-file .env down -v
```

> `down -v`는 로컬 PostgreSQL 데이터를 복구할 수 없게 삭제한다. 초기화할 때만 실행한다.

## 운영 인프라

- AWS 운영 구성: [AWS 리소스 구성과 환경 설정](../docs/team/INFRA.md)
- 운영 시크릿: AWS Secrets Manager나 승인된 배포 환경 변수로 관리한다.
- 운영 시크릿을 이 폴더나 저장소에 두지 않는다.
