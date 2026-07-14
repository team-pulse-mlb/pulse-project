# 운영 인프라

AWS EC2·RDS에서 실행하는 운영 환경 가이드다.

## 구성

| 리소스 | 역할 |
|---|---|
| EC2 | backend·Redis·RabbitMQ·Prometheus·Grafana 실행 |
| RDS PostgreSQL | 경기 원본·계산 이력 저장 |
| S3 | 원본 아카이브와 배포 파일 저장 |
| Secrets Manager | RDS 자격 증명·운영 런타임 시크릿 관리 |

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

### 자동 배포

`main`의 `backend/**`, `ai-service/**`, 운영 Compose 또는 배포 스크립트가 바뀌면 `.github/workflows/application-deploy.yml`이 실행된다.

1. JDK 21로 backend 테스트·빌드
2. backend·ai-service 이미지를 ECR에 커밋 SHA 태그로 push
3. Compose와 배포 스크립트를 비공개 배포 S3 버킷에 업로드
4. GitHub OIDC 역할로 지정 EC2에 SSM 명령 전송
5. EC2가 Secret Manager를 동기화하고 ai-service → api → poller·scorer 순서로 재생성

GitHub에는 장기 AWS 액세스 키를 저장하지 않는다. `main` 브랜치만 `pulse-github-actions-deploy` 역할을 위임받으며, CI에는 운영 시크릿이 전달되지 않는다.

### 확인

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

# AI 헤드라인이 누락된 전체 종료 경기 백필
docker compose -f docker-compose.prod.yml run --rm pulse-headline-backfill

# 특정 종료 경기만 백필
docker compose -f docker-compose.prod.yml run --rm \
  -e JAVA_OPTS='-Dpulse.headline-backfill.game-ids=<GAME_ID_1>,<GAME_ID_2>' \
  pulse-headline-backfill
```

## 프론트엔드·API HTTPS

프론트엔드는 S3 + CloudFront(OAC)로 서빙한다. 리소스·배포 절차는 [`FRONTEND.md`](FRONTEND.md), API를 `api.pulsemlb.com`으로 HTTPS화하는 절차는 [`API_HTTPS.md`](API_HTTPS.md) 참고.

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

RDS 관리 시크릿은 `username`·`password`를 제공한다. `POSTGRES_HOST`·`POSTGRES_PORT`·`POSTGRES_DB`와 나머지 런타임 값은 `pulse/prod/runtime`에서 관리한다. EC2의 `pulse-secret-sync.timer`가 5분마다 두 시크릿을 확인하고 값이 바뀐 경우에만 애플리케이션 컨테이너를 재생성한다.

```bash
systemctl status pulse-secret-sync.timer
journalctl -u pulse-secret-sync.service --since today
```

`OPENAI_API_KEY`는 `pulse/prod/runtime` JSON에 추가한다. 값이 없어도 ai-service 헬스 서버는 기동하지만 실제 문구 생성 요청은 실패하므로 운영 AI 기능 활성화 전 반드시 등록한다. 시크릿 값은 GitHub Variables·Secrets나 CI 로그에 넣지 않는다.

### GitHub 저장소 Variables

| 변수 | 역할 |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | GitHub OIDC 배포 역할 |
| `BACKEND_ECR_REPOSITORY`, `AI_ECR_REPOSITORY` | 이미지 저장소 이름 |
| `DEPLOY_S3_BUCKET` | Compose·배포 스크립트 전달 버킷 |
| `EC2_INSTANCE_ID` | SSM 배포 대상 |
| `RDS_SECRET_ARN`, `RUNTIME_SECRET_ARN` | EC2가 동기화할 시크릿 식별자 |

## AWS 콘솔 확인

| 대상 | 위치 |
|---|---|
| EC2 | EC2 → Instances |
| RDS | RDS → Databases |
| 원본 | S3 → Buckets → 원본 버킷 |
| 로그 | CloudWatch → Log groups |
| 시크릿 | Secrets Manager → Secrets |

리소스 중지·삭제, 보안그룹 변경, RDS 변경 전에는 백업 상태와 서비스 영향을 확인한다.
