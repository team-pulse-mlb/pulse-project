# 알림 설계

알림의 판정 조건, 대상, 파이프라인, 이벤트 스키마를 정의한다. 알림은 경기 단위로만 보낸다. 개별 홈런, 득점, 역전, 삼진 같은 이벤트 알림은 사용하지 않는다.

## 1. 알림 유형과 조건

| 유형 | 조건 | 대상 | 빈도 제한 | 전달 |
|---|---|---|---|---|
| 급상승 경기 알림 (`SURGE`) | `watch_score` 85 이상 진입 **그리고** 최근 5분 내 +15 이상 상승. 발화 후 70 미만으로 내려가야 재무장(히스테리시스) | 전역(관심 팀 무관), `notify_surge_enabled`로 개인별 차단 가능 | 전체 15분 1회 | 인앱 토스트 + 알림 센터 |
| 관심 팀 경기 시작 (`GAME_START`) | 관심 팀 경기의 진행 중 전환 감지 | 관심 팀(`favorite_team_ids`)이 홈·원정에 포함되고 `notify_game_start`를 켠 사용자 | 경기당 1회 | 인앱 토스트 + 알림 센터 |
| 경기 전환 안내 | 다른 경기의 `watch_score`가 현재 경기보다 20점 이상 높고 70 이상 | 상세 화면 조회 중인 사용자 | 같은 후보 경기 15분 1회 | 상세 화면 토스트 (알림 파이프라인이 아니라 상세 응답의 `switchSuggestion` 필드) |

- 급상승 판정은 scorer가, 경기 시작 판정은 poller가 하고, 사용자별 전달·저장은 api가 한다.
- 임계(85)·재무장(70)·급등 조건은 사용자별 설정이 아니라 `scoring.yml` 전역 상수다.
- 관심 선수는 알림 조건으로 사용하지 않는다. 관심 선수 정보는 정렬 가산, 태그, 상세 화면 표시로만 제공한다.

## 2. 파이프라인 — 판정과 전달의 분리

판정은 데이터를 가진 곳(scorer·poller)에서, 전달은 사용자를 아는 곳(api)에서 한다.

```mermaid
flowchart LR
    classDef app fill:#eef6ff,stroke:#4f83cc,color:#172033
    classDef mw fill:#fff7df,stroke:#d99a00,color:#172033
    classDef store fill:#edf7ed,stroke:#3f8f46,color:#172033
    classDef signal fill:#fff0f3,stroke:#d64568,color:#172033

    S["scorer<br/>SURGE 판정"] -->|"NotificationEvent"| Q["RabbitMQ<br/>notify.events"]
    P["poller<br/>GAME_START 판정"] -->|"NotificationEvent"| Q
    Q --> C["api<br/>notification 소비자"]
    C -->|"설정 켠 사용자 필터"| F["user_notifications<br/>insert (멱등)"]
    F --> SSE["SSE<br/>notification_created 푸시"]

    class S,P,C app
    class Q mw
    class F store
    class SSE signal
```

- 채널이 RabbitMQ인 이유: 알림은 one-shot이라 유실되면 복구 경로가 없다. 재조회 신호와 달리 "다음 사이클에 자연 복구"가 성립하지 않는다.
- 중복 전달을 전제로 `(event_id, user_id)` 유니크 제약으로 멱등 처리한다.
- 전역 15분 1회 레이트리밋은 발행 측(scorer)이 Redis 키로 관리한다.
- 경기 전환 안내는 알림 파이프라인을 타지 않는다. 상세 API 응답의 `switchSuggestion` 필드로 제공한다.

## 3. 이벤트 스키마 (RabbitMQ `notify.events`)

```jsonc
{ "eventId": "uuid", "type": "SURGE | GAME_START", "gameId": 5059041,
  "occurredAt": "2026-07-06T02:11:00Z", "tags": ["흐름 급변"] }
```

- 소비: api의 notification 모듈이 fan-out → `user_notifications` insert → SSE `notification_created` 푸시.
- fan-out 대상: `SURGE`는 `notify_enabled`·`notify_surge_enabled`가 켜진 전체 사용자. `GAME_START`는 그중 `notify_game_start`가 켜져 있고 `favorite_team_ids`에 홈 또는 원정 팀이 포함된 사용자만.
- 멱등: `(event_id, user_id)` 유니크 제약. 중복 전달을 전제로 한다.
- 알림 payload·문구에 점수 숫자를 싣지 않는다.
