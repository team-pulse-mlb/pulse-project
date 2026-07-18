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

## 운영 접근

운영 포트는 외부에 공개하지 않는다. EC2 셸은 AWS Systems Manager로 접근한다.

```powershell
aws ssm start-session --target <EC2_INSTANCE_ID>
```

```bash
cd /home/ubuntu/pulse-runtime
docker compose -f docker-compose.prod.yml ps
```

Prometheus·Grafana 접근과 지표 조회 방법은 [`docs/design/ops/OBSERVABILITY.md`](../../docs/design/ops/OBSERVABILITY.md)를 따른다.

## 배포

### 자동 배포

`main`의 `backend/**`, `ai-service/**`, 운영 Compose 또는 배포 스크립트가 바뀌면 `.github/workflows/application-deploy.yml`이 실행된다.

1. JDK 21로 backend 테스트·빌드
2. backend·ai-service 이미지를 ECR에 커밋 SHA 태그로 push
3. Compose와 배포 스크립트를 비공개 배포 S3 버킷에 업로드
4. GitHub OIDC 역할로 지정 EC2에 SSM 명령 전송
5. EC2가 Secret Manager를 동기화하고 ai-service → api → poller·scorer 순서로 재생성

GitHub에는 장기 AWS 액세스 키를 저장하지 않는다. `main` 브랜치만 `pulse-github-actions-deploy` 역할을 위임받으며, CI에는 운영 시크릿이 전달되지 않는다.

추천 점수 백테스트는 별도 `pulse-github-actions-backtest` 역할을 사용한다. 이 역할은 PR별 임시 S3 경로와 `PulseBacktestImpact` SSM 문서만 사용할 수 있으며 Secrets Manager 권한은 없다. RDS 자격 증명은 EC2 역할만 읽는다.

### 확인

```bash
cd /home/ubuntu/pulse-runtime
docker compose -f docker-compose.prod.yml ps
docker inspect -f '{{.Name}} {{.Config.Image}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' \
  pulse-api pulse-poller pulse-scorer pulse-ai-service pulse-redis pulse-rabbitmq pulse-prometheus pulse-grafana
docker compose -f docker-compose.prod.yml logs --tail=100 pulse-api pulse-poller pulse-scorer
```

- 배포 대상 커밋 SHA와 backend·ai-service 이미지 태그가 일치하는지 확인
- 상시 서비스가 모두 `healthy`인지 확인
- `pulse-api` 로그에서 Flyway 성공 확인
- `up{job="pulse-backend"}`의 api·poller·scorer가 모두 `1`인지 확인
- `/api/games?status=all&sort=startTime`, `/api/rankings/live?count=5`가 HTTP 200을 반환하는지 확인

문제가 있으면 `.env`의 `PULSE_APP_IMAGE`를 직전 태그로 되돌리고 다시 실행한다.

### ECR 이미지 보존

`pulse-backend`·`pulse-ai-service` 저장소에는 [`ecr/lifecycle-policy.json`](ecr/lifecycle-policy.json) 수명 주기 정책을 적용한다.

- 커밋 SHA 태그 이미지는 최근 10개만 보존하고 나머지는 만료한다.
- 태그가 없는 이미지는 push 1일 후 만료한다.

따라서 `application-rollback` 워크플로로 되돌릴 수 있는 대상은 **최근 10회 배포**로 제한된다. 그보다 오래된 SHA를 지정하면 워크플로의 "Verify rollback artifacts exist" 단계가 이미지 부재로 실패하며, 이는 반쪽 롤백을 막는 의도된 동작이다.

정책 변경 후에는 두 저장소에 다시 적용한다.

```bash
aws ecr put-lifecycle-policy --repository-name pulse-backend \
  --region ap-northeast-2 \
  --lifecycle-policy-text file://infra/prod/ecr/lifecycle-policy.json
aws ecr put-lifecycle-policy --repository-name pulse-ai-service \
  --region ap-northeast-2 \
  --lifecycle-policy-text file://infra/prod/ecr/lifecycle-policy.json
```

## 장애 대응

1. 발견 시각, 영향 기능, 배포 이미지 태그를 먼저 기록한다.
2. 컨테이너 상태, 지표, 로그 순서로 범위를 좁힌다.
3. 원인을 확인하기 전에 전체 Compose를 재시작하지 않는다. 영향 서비스만 복구한다.
4. 운영 DB 쓰기, Flyway 이력 변경, 큐·볼륨 삭제는 백업과 명시적 승인을 먼저 확인한다.
5. 복구 후 같은 지표와 사용자 API로 정상화를 확인한다.

### backend 컨테이너가 unhealthy인 경우

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 <SERVICE>
docker inspect -f '{{.Config.Image}} {{json .State.Health}}' <CONTAINER>
```

- 직전 배포와 동시에 발생했으면 이미지 태그, Flyway, 시크릿 동기화 로그를 확인한다.
- Redis·RabbitMQ 의존성 장애인지 애플리케이션 자체 부팅 실패인지 구분한다.
- 배포 이미지 결함이면 검증된 직전 이미지로 롤백한다.

### RabbitMQ 또는 ScoreTask 발행이 실패한 경우

```bash
docker compose -f docker-compose.prod.yml ps rabbitmq pulse-poller pulse-scorer
docker compose -f docker-compose.prod.yml logs --tail=200 rabbitmq pulse-poller pulse-scorer
docker exec pulse-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
```

- `score_task_outbox`의 PENDING 보존 여부를 확인한다.
- RabbitMQ 복구 후 outbox 재발행과 scorer 소비 증가를 확인한다.
- PENDING 0건, 큐 적체 해소, `(game_id, computed_at)` 중복 0건을 복구 완료 기준으로 사용한다.

### poller 또는 scorer 처리가 멈춘 경우

- poller 틱, 경기 스킵, scorer 소비량과 평균 처리시간을 같은 시간 범위로 확인한다.
- 외부 API 429이면 `Retry-After`와 백오프를 확인하고 poller를 반복 재시작하지 않는다.
- scorer가 느리면 RabbitMQ 소비자 수, 큐 적체, RDS 연결, GC·메모리 로그를 확인한다.
- 처리 중인 메시지, 큐, outbox를 임의로 삭제하지 않는다.

상세 PromQL과 경보 기준은 [`docs/design/ops/OBSERVABILITY.md`](../../docs/design/ops/OBSERVABILITY.md)를 따른다.

### 복구 완료 기준

- 배포 커밋과 실행 이미지 태그가 일치한다.
- 상시 컨테이너가 모두 `healthy`다.
- backend 역할별 Prometheus 수집 상태가 모두 `1`이다.
- 오류 지표 증가가 멈추고 정상 처리 지표가 회복됐다.
- RabbitMQ 큐 적체와 outbox PENDING이 해소됐다.
- API 스모크 테스트가 HTTP 200을 반환한다.
- 데이터 유실과 멱등 키 중복이 없다.

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

기존 값을 유지하면서 전체 종료 헤드라인과 보호 이벤트 문구를 재생성할 때는 GitHub Actions의
`ai-copy-reprocess` 워크플로를 열고 확인값 `REGENERATE_ALL_AI_COPY`를 입력해 실행한다.

## 백테스트 자동화 준비

### 1. 읽기 전용 DB 계정

운영 DB 백업 상태를 확인한 뒤 `pulse_admin`으로 다음 스크립트를 실행한다. 스크립트의 `\password` 프롬프트에 새 비밀번호를 입력하며 값을 명령 이력이나 저장소에 기록하지 않는다.

```bash
psql -h <RDS_ENDPOINT> -U pulse_admin -d pulse \
  -f infra/prod/backtest/create-readonly-role.sql
```

접속 후 `SHOW transaction_read_only`가 `on`인지 확인하고 `SELECT`는 성공하지만 임시 검증용 `CREATE TABLE`·`INSERT`가 거부되는지 확인한다. 확인용 쓰기 명령은 운영 테이블을 대상으로 실행하지 않는다.

Secrets Manager에 `pulse/backtest/readonly`를 만들고 아래 JSON 키를 저장한다.

```json
{
  "host": "<RDS_ENDPOINT>",
  "port": "5432",
  "dbname": "pulse",
  "username": "pulse_backtest_ro",
  "password": "<READONLY_PASSWORD>"
}
```

### 2. IAM과 SSM

1. `github-backtest-oidc-trust.json`으로 `pulse-github-actions-backtest` 역할을 만든다.
2. 역할에 `github-backtest-policy.json`을 연결한다.
3. EC2 역할의 정책을 `ec2-deploy-policy.json` 내용으로 갱신한다.
4. `backtest-impact-document.yml`을 `PulseBacktestImpact` 이름의 Command 문서로 생성한다.
5. `application-deploy`를 실행해 `/usr/local/bin/pulse-backtest-impact`를 EC2에 배포한다.

```bash
aws ssm create-document \
  --name PulseBacktestImpact \
  --document-type Command \
  --document-format YAML \
  --content file://infra/prod/ssm/backtest-impact-document.yml
```

문서가 이미 있으면 새 버전을 만들고 기본 버전으로 지정한다. 정책 파일의 계정·리전·버킷·인스턴스 값은 현재 운영 리소스와 대조한 뒤 적용한다.

### 3. GitHub Variables

| 변수 | 역할 |
|---|---|
| `BACKTEST_AWS_ROLE_ARN` | PR 전용 GitHub OIDC 역할 |
| `BACKTEST_DB_SECRET_ARN` | EC2가 읽는 백테스트 전용 DB 시크릿 |

백테스트 임시 입출력은 기존 `DEPLOY_S3_BUCKET`의 `backtest/pr/` prefix를 사용한다. `BACKTEST_DB_SECRET_ARN`은 배포 워크플로가 EC2의 `/etc/pulse/secrets.conf`에 식별자만 기록한다. 시크릿 값은 GitHub에 전달하지 않는다. 기존 `EC2_INSTANCE_ID`도 백테스트 워크플로가 함께 사용한다.

준비가 끝나면 `tune:` 제목의 내부 PR에서 `scoring.yml`만 변경해 실행한다. 결과는 PR 코멘트와 GitHub Actions 아티팩트에만 남고 저장소 파일은 추가로 변경하지 않는다. 자세한 실행 조건과 판독 기준은 [`docs/design/policy/BACKTEST.md`](../../docs/design/policy/BACKTEST.md)를 따른다.

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
| `BACKTEST_AWS_ROLE_ARN` | 백테스트 PR 전용 OIDC 역할 |
| `BACKTEST_DB_SECRET_ARN` | EC2 전용 읽기 DB 시크릿 식별자 |

## AWS 콘솔 확인

| 대상 | 위치 |
|---|---|
| EC2 | EC2 → Instances |
| RDS | RDS → Databases |
| 원본 | S3 → Buckets → 원본 버킷 |
| 로그 | CloudWatch → Log groups |
| 시크릿 | Secrets Manager → Secrets |

리소스 중지·삭제, 보안그룹 변경, RDS 변경 전에는 백업 상태와 서비스 영향을 확인한다.
