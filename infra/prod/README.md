# 운영 인프라

AWS EC2·RDS에서 실행하는 운영 환경 가이드다.

## 구성

| 리소스 | 역할 |
|---|---|
| EC2 | backend·Redis·RabbitMQ·Prometheus·Grafana 실행 |
| RDS PostgreSQL | 경기 원본·계산 이력 저장 |
| S3 | 원본 아카이브와 배포 파일 저장 |
| Secrets Manager | RDS 자격 증명 관리 |

EC2와 RDS는 같은 VPC에 있으며 RDS는 외부에 공개하지 않는다. 리전은 `ap-northeast-2`다.

## 컨테이너 구조

| 서비스 | 역할 | 힙 상한 |
|---|---|---|
| `pulse-api` | REST API·SSE·Flyway | 512m |
| `pulse-poller` | 외부 API 수집 | 256m |
| `pulse-scorer` | 점수 계산·랭킹 갱신 | 384m |
| `redis` | 실시간 추천 순위·변경 신호 | - |
| `rabbitmq` | 점수·알림 메시지 | - |
| `prometheus` | 지표 수집 | - |
| `grafana` | 지표 조회 | - |

backend 세 역할은 같은 이미지를 사용한다. Flyway는 `pulse-api`에서만 실행한다.

## 배포

### 1. 로컬 빌드

JDK 21에서 실행한다.

```bash
cd backend
./gradlew clean build
```

### 2. 배포 파일 전달

빌드 jar, Dockerfile, 운영 Compose와 설정 파일을 S3 `deploy/` 경로에 업로드한다. 실제 버킷과 파일 경로는 운영 환경 값으로 지정한다.

### 3. EC2 반영

SSM으로 EC2에 접속해 다음 순서로 진행한다.

1. S3 배포 파일을 `/home/ubuntu/pulse-runtime`에 내려받는다.
2. backend 이미지를 새 날짜 태그로 빌드한다.
3. `.env`의 `PULSE_APP_IMAGE`를 새 태그로 변경한다.
4. `docker compose -f docker-compose.prod.yml up -d`를 실행한다.

### 4. 확인

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=100 pulse-api
```

- 모든 서비스가 `healthy`인지 확인
- `pulse-api` 로그에서 Flyway 성공 확인
- API, poller, scorer의 actuator 지표 수집 확인

문제가 있으면 `.env`의 `PULSE_APP_IMAGE`를 직전 태그로 되돌리고 다시 실행한다.

## 일회성 배치

일회성 배치는 운영 DB와 S3 데이터를 변경한다. 대상 경기와 실행 시간을 먼저 확인한다.

```bash
# S3 원본 재적재
docker compose -f docker-compose.prod.yml run --rm \
  -e JAVA_OPTS='-Dpulse.replay.date=<YYYY-MM-DD> -Dpulse.replay.game-id=<GAME_ID>' \
  pulse-replay

# 기존 경기 재점수화
docker compose -f docker-compose.prod.yml run --rm \
  -e JAVA_OPTS='-Dpulse.rescore.game-ids=<GAME_ID>' \
  pulse-rescore
```

## 시크릿

실제 값은 저장소에 기록하지 않는다.

| 구분 | 환경 변수 |
|---|---|
| 이미지 | `PULSE_APP_IMAGE` |
| 외부 API | `BDL_API_KEY`, `OPENAI_API_KEY` |
| PostgreSQL | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| RabbitMQ | `RABBITMQ_USER`, `RABBITMQ_PASSWORD` |
| 인증 | `JWT_SECRET` |
| 관측 | `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` |

## AWS 콘솔 확인

| 대상 | 위치 |
|---|---|
| EC2 | EC2 → Instances |
| RDS | RDS → Databases |
| 원본 | S3 → Buckets → 원본 버킷 |
| 로그 | CloudWatch → Log groups |
| 시크릿 | Secrets Manager → Secrets |

리소스 중지·삭제, 보안그룹 변경, RDS 변경 전에는 백업 상태와 서비스 영향을 확인한다.
