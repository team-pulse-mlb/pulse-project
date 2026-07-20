# 관측성

Prometheus 지표 정의와 Grafana 조회·경보 기준을 관리한다. 배포, 컨테이너 점검, 장애 복구 절차는 [`OPERATIONS.md`](OPERATIONS.md)를 따른다.

**관측 범위 제한**: 현재 운영은 단일 EC2·단일 Redis·단일 RabbitMQ 구성이다. `ARCHITECTURE.md`가 설명하는 장애 격리는 프로세스 단위에 한정되며, 호스트·Redis·RabbitMQ 자체 장애는 전체 서비스에 영향을 준다. 여기 지표·경보도 프로세스 단위 이상 징후 탐지에 초점을 두며, 호스트 단일 장애점은 비용·일정 제약에 따른 의도적 범위다.

## 접근

운영 포트는 외부에 공개하지 않는다. AWS Systems Manager 포트 포워딩으로 접근한다.

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

브라우저에서 `http://127.0.0.1:19090`에 접속한다. 일반 조회는 Grafana `Explore`의 `Prometheus` 데이터소스를 사용한다.

## 수집 기준

Prometheus는 Compose 내부 네트워크에서 backend 역할별 관리 포트 `8081`의 `/actuator/prometheus`를 15초마다 수집한다. 관리 포트는 호스트에 공개하지 않는다. 모든 backend 시계열에는 `job="pulse-backend"`와 `role="api|poller|scorer"`가 붙는다.

```promql
up{job="pulse-backend"}
```

역할별 값이 `1`이면 정상 수집, `0`이면 컨테이너·네트워크·actuator 상태를 확인한다.

## 도메인 지표

| Prometheus 지표 | 역할 | 애플리케이션 라벨 | 의미 |
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

## Grafana 조회

Grafana `Explore`에서 조회 범위를 `Last 15 minutes`로 설정하고 PromQL을 실행한다.

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

```promql
sum by (target) (
  increase(pulse_poller_backoff_activations_total{role="poller"}[5m])
)
```

`rate_limited`, `exception`, 429가 증가하면 같은 시간대의 백오프와 poller 로그를 함께 확인한다.

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

틱이 멈췄으면 poller 상태와 로그를 확인한다. 스킵 증가는 경기 단위 오류 격리가 동작했다는 뜻이므로 로그에서 대상 경기와 원인을 찾는다.

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

소비량이 0으로 떨어지거나 평균 처리시간이 계속 증가하면 RabbitMQ 적체, scorer 상태, RDS 지연을 함께 확인한다. p95가 필요하면 histogram 설정과 운영 메모리 영향을 먼저 검증한다.

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

## 경보 기준

ScoreTask 발행 실패 경보는 다음 조건을 사용한다.

```promql
increase(pulse_score_task_publish_failures_total[5m]) > 0
```

poller·scorer는 실패 카운터를 시작 시 `0`으로 등록한다. 배포 직후에는 Prometheus가 0 기준 샘플을 2회 이상 수집한 뒤 경보를 검증한다. 경보 발생 시 같은 기간의 outbox 재발행 증가와 scorer 소비 회복을 함께 확인한다.

## 관련 문서

- [`OPERATIONS.md`](OPERATIONS.md): 배포, 컨테이너 점검, 장애 대응·복구
- [`DATA_PIPELINE.md`](../data/DATA_PIPELINE.md): 상태별 폴링, 호출 예산, 429 대응
- [`MESSAGING.md`](../data/MESSAGING.md): RabbitMQ, outbox, 재전달·멱등 계약
- [`NOTIFICATIONS.md`](../policy/NOTIFICATIONS.md): 알림 판정, outbox, fan-out 계약
