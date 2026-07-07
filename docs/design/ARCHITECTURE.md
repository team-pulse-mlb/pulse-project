# 아키텍처

## 1. 한 줄 요약

PULSE는 외부 MLB API를 계속 확인하다가, 경기 상태에 맞게 필요한 데이터만 수집하고, Spring Boot가 추천 점수를 계산한 뒤, Redis와 PostgreSQL에 저장된 결과를 API와 SSE로 클라이언트에 전달한다.

```text
외부 API -> 수집(poller) -> RabbitMQ -> 계산(scorer) -> Redis 랭킹/PostgreSQL 이력 -> API 응답/SSE 신호 -> 화면/알림
```

## 2. 전체 구조

```mermaid
flowchart TB
    U["사용자"]
    FE["React + Vite (Vercel)"]

    subgraph EC2["AWS EC2 t3.large / Docker Compose"]
        API["pulse-api / REST·SSE·스포일러 보호·알림 전달"]
        POLLER["pulse-poller / 상태별 폴링·GAME_START 판정"]
        MQ["RabbitMQ / score.tasks · notify.events"]
        SCORER["pulse-scorer / watch_score 계산·SURGE 판정"]
        REDIS[("Redis / 라이브 랭킹·문구 캐시·재조회 신호 pub/sub")]
        AI["ai-service / 문구 생성·스포일러 검수 (비동기)"]
        MON["Prometheus + Grafana / 도메인 지표"]
    end

    RDS[("AWS RDS PostgreSQL / 운영 원본·계산 이력")]
    BDL["balldontlie MLB API"]
    OPENAI["OpenAI API"]

    BDL -->|"games / lineups / odds / plays / PA"| POLLER
    POLLER -->|"원본·상태 저장"| RDS
    POLLER -->|"ScoreTask"| MQ
    MQ -->|"score.tasks"| SCORER
    SCORER -->|"점수 이력·구간 저장"| RDS
    SCORER -->|"랭킹·문구 캐시 갱신"| REDIS
    SCORER -.->|"재조회 신호 pub/sub"| API
    POLLER -->|"NotificationEvent"| MQ
    SCORER -->|"NotificationEvent"| MQ
    MQ -->|"notify.events"| API
    SCORER -->|"문구 생성 요청 (비동기)"| AI
    AI -->|"생성"| OPENAI
    AI -->|"검수 결과 반환"| SCORER
    API -->|"랭킹·캐시 조회"| REDIS
    API -->|"상세·이력 조회"| RDS
    U --> FE
    FE -->|"REST / SSE"| API
```

## 3. 컴포넌트 배치의 이유

| 컴포넌트 | 역할 | 왜 이렇게 나눴나 |
|---|---|---|
| `pulse-api` | REST 응답, SSE 푸시, 보호 모드 DTO 강제, 알림 fan-out·저장 | 사용자 요청·SSE 연결은 지연에 민감하고, 사용자 설정을 아는 유일한 곳이라 알림 "전달"도 여기서 한다 |
| `pulse-poller` | 경기 상태별 폴링, 원본 저장, ScoreTask 발행, LIVE 전이 감지(경기 시작 알림 판정) | 외부 API I/O·레이트리밋·백오프라는 고유 실패 모드를 가진다. 장애가 나도 api·scorer에 전파되지 않는다 |
| `pulse-scorer` | `watch_score`·태그·다시보기 구간 계산, 급상승 알림 판정, AI 문구 생성 트리거 | 점수 이력과 히스테리시스 상태를 가진 유일한 곳이라 "판정"이 여기 있다. 계산 버그·부하가 폴링 주기에 영향을 주지 않는다 |
| `RabbitMQ` | `score.tasks`(계산 요청), `notify.events`(알림 이벤트) | 유실되면 복구 불가능한 작업 전달용. ack·재전달·DLQ 제공 |
| `Redis` | 라이브 랭킹(ZSET), 현재 상태·문구 캐시, 재조회 신호 pub/sub, 쿨다운·레이트리밋 키 | 유실돼도 재계산·다음 사이클로 복구되는 것만 둔다 |
| `RDS PostgreSQL` | 운영 원본·계산 이력·사용자·알림 저장 | 라이브 1회 계산 결과는 재생성 불가라 관리형 백업이 필요하다 |
| `ai-service` | 추천 판단 없이, 서버가 넘긴 스포일러 세이프 context로 문구 생성·검수. 무상태(캐시·DB에 직접 쓰지 않음) | 응답 경로 밖(비동기+캐시)이라 장애가 사용자 응답에 영향 없다. 저장은 backend가 수행해 자격증명과 저장 분기 규칙(Redis/PG)을 backend에 유지한다 |

**배치 원칙 요약**: 판정은 데이터 옆에서(SURGE=scorer, GAME_START=poller), 전달은 사용자 옆에서(fan-out·SSE=api). 채널은 **유실 불가 작업 = RabbitMQ, 유실 허용 신호 = Redis Pub/Sub**.

## 4. 운영 흐름 요약

| 구간 | 한 줄 흐름 |
|---|---|
| 경기 전 | `poller`가 선발·배당·일 배치 데이터를 모음 -> PREGAME ScoreTask 발행 -> `scorer`가 `pregame_score` 계산 -> PostgreSQL 저장 |
| 경기 중 | `poller` 20초 수집 -> RabbitMQ -> `scorer` 계산 -> Redis 랭킹 갱신 -> 재조회 신호 -> SSE |
| 알림 | `scorer`/`poller` 판정 -> `notify.events` -> `api` fan-out·저장 -> SSE |
| AI 문구 | `scorer`가 유의미 변화 시 비동기 요청 -> 생성·검수 -> Redis 캐시 -> 다음 조회에 반영 |
| 경기 종료 | `poller`가 `FINAL` 감지 -> 종료 ScoreTask 발행 -> `scorer`가 열린 구간 마감·랭킹 제거·`signal:ranking` -> 라이브 중 저장된 `peak_base_score`와 구간으로 다시보기 제공 |

## 5. 설계 원칙

1. 외부 MLB API는 서버에서만 호출한다. 프론트는 직접 호출하지 않는다.
2. 추천 판단은 Spring Boot가 한다. AI 서버는 문구만 만든다.
3. 스포일러 보호는 프론트가 아니라 서버 응답 단계에서 강제한다. 필터링 지점은 REST DTO 한 곳이다(SSE는 데이터를 싣지 않는다).
4. PostgreSQL에는 오래 남길 데이터, Redis에는 실시간 조회용 데이터만 둔다. 관리형(RDS) 비용은 재생성 불가능한 데이터에만 쓴다.
5. 경기 종료 후 다시 크게 재분석하지 않는다. 라이브 중 계산한 이력과 구간을 사용한다.
6. 채널 선택 기준: 유실 불가 작업은 RabbitMQ, 유실 허용 신호는 Redis Pub/Sub.
7. 판정은 데이터를 가진 컴포넌트에서, 전달은 사용자를 아는 컴포넌트에서 한다.
