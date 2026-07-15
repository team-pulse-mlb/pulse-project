# 운영 지표

Prometheus는 Compose 내부 네트워크에서 각 backend 역할의 관리 포트 `8081`에 있는 `/actuator/prometheus`를 15초마다 수집한다. 관리 포트는 호스트에 공개하지 않는다.

## 도메인 지표

| Prometheus 지표 | 역할 | 라벨 | 의미 |
|---|---|---|---|
| `pulse_bdl_requests_total` | poller | `endpoint`, `outcome` | balldontlie 엔드포인트별 호출 수. outcome은 `success`, `error`, `rate_limited`, `exception` |
| `pulse_bdl_rate_limit_total` | poller | `endpoint` | HTTP 429 응답 수 |
| `pulse_poller_ticks_total` | poller | `poller` | operational, pregame, player enrichment 폴링 틱 수 |
| `pulse_poller_backoff_activations_total` | poller | `target` | 외부 API 오류로 백오프를 시작한 횟수 |
| `pulse_poller_game_skips_total` | poller | `reason` | 한 경기 오류를 격리하고 다음 경기를 계속 처리한 횟수 |
| `pulse_score_task_publish_failures_total` | poller, scorer | 없음 | `score.tasks` RabbitMQ 발행 실패 수 |
| `pulse_score_task_outbox_republish_runs_total` | poller, scorer | 없음 | 대기 중 ScoreTask outbox 재발행 실행 수 |
| `pulse_score_task_consumed_total` | scorer | `type` | pregame, live, terminal ScoreTask 소비 수 |
| `pulse_score_task_processing_seconds` | scorer | `type` | ScoreTask 처리 시간 |
| `pulse_scorer_surge_fired_total` | scorer | 없음 | SURGE 판정 발화 수 |
| `pulse_scorer_ranking_updated_total` | scorer | 없음 | 라이브 랭킹 갱신 수 |
| `pulse_notification_publish_failures_total` | poller, scorer | 없음 | `notify.events` 발행 실패 수 |
| `pulse_notification_outbox_republish_runs_total` | poller, scorer | 없음 | 대기 중 알림 outbox 재발행 실행 수 |

## 대시보드 전달 기준

- 외부 API: 엔드포인트별 호출률, 429 비율, 백오프 증가량
- poller: 분당 틱 수, 경기 격리 스킵 증가량, ScoreTask 발행 실패·outbox 재발행 증가량
- scorer: 초당 소비량, p95 처리 시간, SURGE·랭킹 갱신 증가량
- 공통: 알림 발행 실패와 outbox 재발행 증가량

ScoreTask 발행 실패 경보는 `increase(pulse_score_task_publish_failures_total[5m]) > 0`을 경고 기준으로 사용한다. 발행 실패는 영속 outbox에 PENDING으로 남아 같은 `observedAt` payload가 재발행되므로 즉시 유실을 뜻하지 않는다. 경보 발생 시 같은 기간의 `pulse_score_task_outbox_republish_runs_total` 증가와 scorer 소비 회복 여부를 함께 확인한다.
