# 운영 및 장애 대응

운영 환경은 EC2의 Docker Compose, RDS PostgreSQL, S3, Secrets Manager로 구성한다. EC2에서는 `pulse-api`, `pulse-poller`, `pulse-scorer`, `ai-service`, Redis, RabbitMQ, Prometheus, Grafana를 실행한다.

## 1. 운영 접근

운영 포트는 외부에 공개하지 않는다. EC2 셸과 관측 화면은 AWS Systems Manager로 접근한다.

### EC2 셸

```powershell
aws ssm start-session --target <EC2_INSTANCE_ID>
```

```bash
cd /home/ubuntu/pulse-runtime
docker compose -f docker-compose.prod.yml ps
```

### Grafana

```powershell
aws ssm start-session --target <EC2_INSTANCE_ID> `
  --document-name AWS-StartPortForwardingSession `
  --parameters "portNumber=3000,localPortNumber=13000"
```

브라우저에서 `http://127.0.0.1:13000`에 접속한다. 계정은 `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`를 사용하며 실제 값은 Secrets Manager에서만 관리한다.

### Prometheus

```powershell
aws ssm start-session --target <EC2_INSTANCE_ID> `
  --document-name AWS-StartPortForwardingSession `
  --parameters "portNumber=9090,localPortNumber=19090"
```

브라우저에서 `http://127.0.0.1:19090`에 접속한다. 일반 조회는 Grafana의 `Explore`에서 기본 데이터소스 `Prometheus`를 사용한다.

## 2. 기본 점검

배포 직후와 라이브 경기 시작 전에는 다음 순서로 확인한다.

```bash
cd /home/ubuntu/pulse-runtime
docker compose -f docker-compose.prod.yml ps
docker inspect -f '{{.Name}} {{.Config.Image}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' \
  pulse-api pulse-poller pulse-scorer pulse-ai-service pulse-redis pulse-rabbitmq pulse-prometheus pulse-grafana
docker compose -f docker-compose.prod.yml logs --tail=100 pulse-api pulse-poller pulse-scorer
```

- 배포 대상 커밋 SHA와 backend·ai-service 이미지 태그가 일치해야 한다.
- 상시 서비스는 모두 `healthy`여야 한다.
- `pulse-api`의 Flyway 적용이 끝난 뒤 poller·scorer가 기동돼야 한다.
- Grafana에서 `up{job="pulse-backend"}`의 api·poller·scorer가 모두 `1`이어야 한다.
- `/api/games?status=all&sort=startTime`, `/api/rankings/live?count=5`가 HTTP 200을 반환해야 한다.

전체 배포와 롤백 절차는 [`infra/prod/README.md`](../../infra/prod/README.md)를 따른다.

## 3. Prometheus 지표

Prometheus는 Compose 내부 네트워크에서 backend 역할별 관리 포트 `8081`의 `/actuator/prometheus`를 15초마다 수집한다. 모든 backend 시계열에는 `job="pulse-backend"`와 `role="api|poller|scorer"`가 붙는다.

| 지표 | 역할 | 애플리케이션 라벨 | 의미 |
|---|---|---|---|
| `pulse_bdl_requests_total` | poller | `endpoint`, `outcome` | balldontlie 엔드포인트별 호출 수. outcome은 `success`, `error`, `rate_limited`, `exception` |
| `pulse_bdl_rate_limit_total` | poller | `endpoint` | HTTP 429 응답 수 |
| `pulse_poller_ticks_total` | poller | `poller` | operational, pregame, player enrichment 폴링 틱 수 |
| `pulse_poller_backoff_activations_total` | poller | `target` | 외부 API 오류로 백오프를 시작한 횟수 |
| `pulse_poller_game_skips_total` | poller | `reason` | 경기 하나의 오류를 격리하고 다음 경기를 처리한 횟수 |
| `pulse_score_task_publish_failures_total` | poller, scorer | 없음 | `score.tasks` RabbitMQ 발행 실패 수 |
| `pulse_score_task_outbox_republish_runs_total` | poller, scorer | 없음 | 대기 중 ScoreTask outbox 재발행 실행 수 |
| `pulse_score_task_consumed_total` | scorer | `type` | pregame, live, terminal ScoreTask 소비 수 |
| `pulse_score_task_processing_seconds` | scorer | `type` | ScoreTask 처리 시간 |
| `pulse_scorer_surge_fired_total` | scorer | 없음 | SURGE 판정 발화 수 |
| `pulse_scorer_ranking_updated_total` | scorer | 없음 | 라이브 랭킹 갱신 수 |
| `pulse_notification_publish_failures_total` | poller, scorer | 없음 | `notify.events` 발행 실패 수 |
| `pulse_notification_outbox_republish_runs_total` | poller, scorer | 없음 | 대기 중 알림 outbox 재발행 실행 수 |

## 4. Grafana 조회

Grafana `Explore`에서 조회 범위를 `Last 15 minutes`로 설정하고 PromQL을 실행한다.

### 수집 상태

```promql
up{job="pulse-backend"}
```

역할별 값이 `1`이면 정상 수집, `0`이면 컨테이너·네트워크·actuator 상태를 확인한다.

### 외부 API

```promql
sum by (endpoint, outcome) (
  rate(pulse_bdl_requests_total{role="poller"}[5m])
)
```

```promql
sum by (endpoint) (
  increase(pulse_bdl_rate_limit_total{role="poller"}[5m])
)
```

`rate_limited`, `exception`, 429가 증가하면 같은 시간대의 백오프를 함께 확인한다.

```promql
sum by (target) (
  increase(pulse_poller_backoff_activations_total{role="poller"}[5m])
)
```

### poller

```promql
sum by (poller) (
  rate(pulse_poller_ticks_total{role="poller"}[5m])
)
```

```promql
sum by (reason) (
  increase(pulse_poller_game_skips_total{role="poller"}[5m])
)
```

틱이 멈췄으면 poller 상태와 로그를 확인한다. 스킵 증가는 전체 중단이 아니라 경기 단위 오류 격리가 동작했다는 뜻이므로 로그에서 대상 경기와 원인을 찾는다.

### ScoreTask 발행과 복구

```promql
sum by (role) (
  increase(pulse_score_task_publish_failures_total[5m])
)
```

```promql
sum by (role) (
  increase(pulse_score_task_outbox_republish_runs_total[5m])
)
```

```promql
sum by (type) (
  increase(pulse_score_task_consumed_total{role="scorer"}[5m])
)
```

발행 실패는 outbox에 `PENDING`으로 남으므로 즉시 유실을 뜻하지 않는다. 재발행 실행 증가와 scorer 소비 회복을 순서대로 확인한다. 재발행 실행 값은 메시지 수가 아니라 poller·scorer 스케줄러의 실행 횟수다.

ScoreTask 발행 실패 경고 기준은 다음과 같다.

```promql
increase(pulse_score_task_publish_failures_total[5m]) > 0
```

poller·scorer는 실패 카운터를 시작 시 `0`으로 등록한다. 배포 직후에는 Prometheus가 0 기준 샘플을 2회 이상 수집한 뒤 경고를 검증한다.

### scorer 처리량과 지연

```promql
sum by (type) (
  rate(pulse_score_task_consumed_total{role="scorer"}[5m])
)
```

```promql
sum by (type) (
  rate(pulse_score_task_processing_seconds_sum{role="scorer"}[5m])
)
/
sum by (type) (
  rate(pulse_score_task_processing_seconds_count{role="scorer"}[5m])
)
```

소비량이 0으로 떨어지거나 평균 처리시간이 계속 증가하면 RabbitMQ 적체, scorer 상태, RDS 지연을 함께 확인한다. p95가 필요하면 histogram 설정을 먼저 추가하고 운영 메모리 영향을 검증한다.

### 알림 발행과 복구

```promql
sum by (role) (
  increase(pulse_notification_publish_failures_total[5m])
)
```

```promql
sum by (role) (
  increase(pulse_notification_outbox_republish_runs_total[5m])
)
```

발행 실패 후 재발행이 증가하지 않으면 poller·scorer 로그와 `notification_outbox` PENDING 상태를 확인한다. 재발행 뒤에도 알림이 저장되지 않으면 api의 `notify.events` 소비자와 `(event_id, user_id)` 멱등 저장을 확인한다.

## 5. 장애 대응 원칙

1. 발견 시각, 영향 기능, 배포 이미지 태그를 먼저 고정한다.
2. 컨테이너 상태, 지표, 로그 순서로 범위를 좁힌다.
3. 시크릿과 사용자 데이터는 로그·채팅·문서에 복사하지 않는다.
4. 원인을 확인하기 전에 전체 Compose를 재시작하지 않는다. 영향 서비스만 복구한다.
5. 운영 DB 쓰기, Flyway 이력 변경, 큐·볼륨 삭제는 백업과 명시적 승인을 먼저 확인한다.
6. 복구 후 같은 지표와 사용자 API로 정상화를 확인한다.

## 6. 상황별 초동 대응

### backend 컨테이너가 unhealthy인 경우

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 <SERVICE>
docker inspect -f '{{.Config.Image}} {{json .State.Health}}' <CONTAINER>
```

- 직전 배포와 동시에 발생했으면 이미지 태그, Flyway, 시크릿 동기화 로그를 확인한다.
- Redis·RabbitMQ 의존성 장애인지 애플리케이션 자체 부팅 실패인지 구분한다.
- 배포 이미지 결함이면 검증된 직전 이미지로 롤백한다.

### 외부 API 429 또는 백오프가 증가한 경우

- `pulse_bdl_rate_limit_total`, `pulse_bdl_requests_total`, `pulse_poller_backoff_activations_total`을 같은 시간 범위로 본다.
- 로그의 endpoint와 `Retry-After`를 확인한다.
- poller를 반복 재시작하거나 수동 호출을 추가하지 않는다.
- 백오프 후 success 호출과 poller 틱이 회복되는지 확인한다.

### 경기 단위 스킵이 증가한 경우

- `pulse_poller_game_skips_total` 증가 시각의 poller 로그에서 gameId와 예외를 찾는다.
- 다른 경기의 틱과 저장이 계속되는지 확인한다.
- 한 경기 문제로 전체 poller를 중단하지 않는다.

### RabbitMQ 또는 ScoreTask 발행이 실패한 경우

```bash
docker compose -f docker-compose.prod.yml ps rabbitmq pulse-poller pulse-scorer
docker compose -f docker-compose.prod.yml logs --tail=200 rabbitmq pulse-poller pulse-scorer
docker exec pulse-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
```

1. 발행 실패 증가를 확인한다.
2. `score_task_outbox`가 PENDING을 보존하는지 확인한다.
3. RabbitMQ만 복구하고 poller·scorer 연결 회복을 기다린다.
4. outbox 재발행과 scorer 소비 증가를 확인한다.
5. PENDING 0건, 큐 적체 해소, `(game_id, computed_at)` 중복 0건으로 종료한다.

ScoreTask 영속·재전달·멱등 계약은 [`API_CONTRACTS.md`](../design/API_CONTRACTS.md)의 RabbitMQ 명세를 따른다.

### scorer 소비가 멈추거나 느린 경우

- `pulse_score_task_consumed_total`, 평균 처리시간, `score.tasks` 적체를 함께 본다.
- RabbitMQ 소비자 수, RDS 연결, scorer GC·메모리 로그를 확인한다.
- 메시지가 처리 중이면 큐나 outbox를 삭제하지 않는다.
- 복구 후 소비율이 유입률을 앞서고 적체가 줄어드는지 확인한다.

### 알림 발행 또는 저장이 실패한 경우

- 알림 발행 실패와 outbox 재발행을 확인한다.
- `notify.events` 큐의 적체·소비자 수와 api 로그를 확인한다.
- 발행 측 원본 이벤트와 outbox를 삭제하지 않는다.
- 복구 후 outbox PENDING 0건과 사용자별 중복 0건을 확인한다.

알림 판정·발행·fan-out 계약은 [`NOTIFICATIONS.md`](../design/NOTIFICATIONS.md)를 따른다.

## 7. 복구 완료 기준

- 배포 커밋과 실행 이미지 태그가 일치한다.
- 상시 컨테이너가 모두 `healthy`다.
- `up{job="pulse-backend"}`의 모든 역할이 `1`이다.
- 오류 지표 증가가 멈추고 정상 처리 지표가 회복됐다.
- RabbitMQ 큐 적체와 outbox PENDING이 해소됐다.
- API 스모크 테스트가 HTTP 200을 반환한다.
- 데이터 유실과 멱등 키 중복이 없다.

장애 원인과 복구 과정이 재사용할 가치가 있을 때만 `local-docs/troubleshooting/`에 별도 기록한다.

## 8. 관련 문서

- [`infra/prod/README.md`](../../infra/prod/README.md): 배포, 롤백, 시크릿, 일회성 배치
- [`infra/docker-compose.prod.yml`](../../infra/docker-compose.prod.yml): 서비스, 포트, 헬스체크, 메모리 설정
- [`DATA_PIPELINE.md`](../design/DATA_PIPELINE.md): 상태별 폴링, 호출 예산, 429 대응
- [`API_CONTRACTS.md`](../design/API_CONTRACTS.md): RabbitMQ, outbox, 재전달·멱등 계약
- [`NOTIFICATIONS.md`](../design/NOTIFICATIONS.md): 알림 판정, outbox, fan-out 계약
