<p align="center">
  <img src="frontend/public/pulse-logo.png" alt="PULSE 로고" width="220" />
</p>

<p align="center"><strong>스포일러 프리 MLB 관전 타이밍 추천 서비스</strong></p>

<p align="center"><a href="https://pulsemlb.com">https://pulsemlb.com</a></p>

## 핵심 기능

<!-- 기능별 스크린샷·GIF 추가 예정 -->

| 문제 | 해결 |
|---|---|
| 동시에 진행되는 여러 경기 중 지금 볼 경기를 고르기 어렵다. | **실시간 경기 추천** — 진행 중인 경기의 관전 가치를 계산해 지금 볼 만한 순서로 보여준다. 로그인 사용자는 관심 팀·선수에 따라 개인화된 순서를 볼 수 있다. |
| 경기 흐름을 확인하는 과정에서 점수와 결과를 먼저 알게 된다. | **스포일러 보호와 공개** — 진행 중·종료 경기 모두 보호 모드로 시작한다. 보호 모드에서는 점수와 승패를 숨긴 경기 흐름을 보여주며, 종료 경기에는 AI 헤드라인·경기 긴장도 그래프·보호 안전 이벤트를 제공한다. 사용자가 공개로 전환하면 점수와 상세 결과를 볼 수 있다. |
| 경기를 계속 확인하지 않으면 볼 만한 순간이나 다른 경기의 상승 흐름을 놓치기 쉽다. | **관전 타이밍 알림** — 급상승 경기와 관심 팀 경기 시작을 인앱 토스트와 알림 센터로 알려준다. 경기 상세를 보고 있을 때 더 볼 만한 경기가 생기면 토스트로 이동을 제안한다. |

## 역할 분담

| 담당자 | 역할 |
|---|---|
| 예은 | 데이터 파이프라인, 점수·추천, 홈, 공통 구조 |
| 창현 | AI 문구 생성·검수 |
| 민석 | 경기 상세, 다시보기, 전환 알림 |
| 윤호 | 회원, 알림, 통합 후 관측 |

세부 경계와 일정은 [ROLES_AND_SCHEDULE.md](docs/team/ROLES_AND_SCHEDULE.md)를 따른다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, react-router v8 |
| Backend | Java 21, Spring Boot, Spring Data JPA, Flyway |
| Data | PostgreSQL 16, Redis 7 |
| AI | Python, FastAPI, Pydantic, OpenAI API |
| 실시간 | Server-Sent Events(SSE) |
| Infra | Docker Compose, AWS EC2·RDS·S3 |

## 저장소 구조

```text
pulse-project/
├── frontend/      # React 웹 애플리케이션
├── backend/       # 수집·점수화·랭킹·REST API
├── ai-service/    # AI 문구 생성·스포일러 검수
├── raw-archive/   # 원본 데이터 수집·백필·분석
├── infra/         # 인프라 구성(local: 로컬 개발용, prod: AWS 운영)
└── docs/          # 제품·설계·가이드·팀 문서
```

전체 문서 색인은 [docs/README.md](docs/README.md)를 따른다. 로컬 실행은 [로컬 인프라 가이드](docs/guide/LOCAL_ENV.md)를 따른다.

## 전체 아키텍처

```mermaid
flowchart TB
    classDef ext fill:#f4f4f5,stroke:#8b8b94,color:#172033
    classDef fe fill:#f3eefe,stroke:#7c5cd1,color:#172033
    classDef app fill:#eef6ff,stroke:#4f83cc,color:#172033
    classDef mw fill:#fff7df,stroke:#d99a00,color:#172033
    classDef store fill:#edf7ed,stroke:#3f8f46,color:#172033

    U["사용자"]
    FE["React + Vite<br/>(S3 + CloudFront)"]

    subgraph EC2["AWS EC2 t3.medium + 스왑 2GB / Docker Compose"]
        API["pulse-api<br/>REST·SSE·스포일러 보호·알림 전달"]
        POLLER["pulse-poller<br/>상태별 폴링·GAME_START 판정"]
        MQ["RabbitMQ<br/>score.tasks · notify.events"]
        SCORER["pulse-scorer<br/>watch_score 계산·SURGE 판정"]
        REDIS[("Redis<br/>라이브 랭킹·현재 상태 캐시·pub/sub")]
        AI["ai-service<br/>문구 생성·스포일러 검수"]
        MON["Prometheus + Grafana<br/>도메인 지표"]
    end

    RDS[("AWS RDS PostgreSQL<br/>운영 원본·계산 이력")]
    BDL["balldontlie MLB API"]
    OPENAI["OpenAI API"]

    BDL -->|"① games / lineups / odds / plays / PA"| POLLER
    POLLER -->|"② 원본·상태 저장"| RDS
    POLLER -->|"③ ScoreTask"| MQ
    MQ -->|"④ score.tasks"| SCORER
    SCORER -->|"⑤ 점수 이력·이벤트 저장"| RDS
    SCORER -->|"⑥ 랭킹 갱신"| REDIS
    SCORER -.->|"⑦ 재조회 신호 pub/sub"| API
    POLLER -->|"⑧ GAME_START NotificationEvent"| MQ
    SCORER -->|"⑨ SURGE NotificationEvent"| MQ
    MQ -->|"⑩ notify.events"| API
    SCORER -->|"⑪ 문구 생성 요청"| AI
    AI -->|"⑫ 생성 요청"| OPENAI
    AI -->|"⑬ 검수 결과 반환"| SCORER
    API -->|"⑭ 랭킹·캐시 조회"| REDIS
    API -->|"⑮ 상세·이력 조회"| RDS
    U --> FE
    FE -->|"⑯ REST / SSE"| API

    class BDL,OPENAI ext
    class U,FE fe
    class API,POLLER,SCORER,AI app
    class MQ,MON mw
    class REDIS,RDS store
```

번호별 상세는 [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)를 따른다.

## 경기 상태별 수집 흐름

```mermaid
flowchart LR
    classDef main fill:#eef6ff,stroke:#4f83cc,color:#172033
    classDef live fill:#fff7df,stroke:#d99a00,color:#172033
    classDef done fill:#edf7ed,stroke:#3f8f46,color:#172033
    classDef hold fill:#fff0f3,stroke:#d64568,color:#172033

    START(("시작")) -->|"① 일정 발견"| SCHEDULED["SCHEDULED<br/>예정"]
    SCHEDULED -->|"② T-36h"| FAR["PREGAME_FAR<br/>선발 확인"]
    FAR -->|"③ T-6h"| NEAR["PREGAME_NEAR<br/>타순·배당 수집"]
    NEAR -->|"④ 경기 시작 감지"| LIVE["LIVE<br/>20초 수집"]
    LIVE -->|"⑤ 종료 감지"| FINAL["FINAL<br/>종료 정리"]
    SCHEDULED -->|"⑥ 연기·취소"| DONE["DONE<br/>종결"]
    LIVE -->|"⑦ 원본 STATUS_POSTPONED"| POSTPONED["SUSPENDED_POSTPONED<br/>보류"]
    POSTPONED -->|"재개 감지"| LIVE
    POSTPONED -->|"종료 확정"| FINAL
    POSTPONED -->|"취소 확정"| DONE
    DONE -.->|"재편성 재관측 시 재진입"| SCHEDULED

    class SCHEDULED,FAR,NEAR main
    class LIVE live
    class FINAL,DONE done
    class POSTPONED hold
```

상태별 수집 주기와 호출 예산은 [DATA_PIPELINE.md](docs/data/DATA_PIPELINE.md)를 따른다.

## 사용자 흐름

```mermaid
flowchart TD
    Enter["서비스 진입"] --> Home["홈 (/)\n상단 추천 + 하단 전체 목록"]
    Home -->|"비로그인 상태에서\n관심 팀 설정 시도"| Auth["로그인/회원가입"]
    Auth -->|"신규 가입"| Signup["회원가입\n계정 정보 → 관심 팀 → 알림 설정"]
    Signup -->|"가입 완료"| Login["로그인"]
    Auth -->|"기존 회원 로그인"| Login
    Login -->|"최초 로그인"| PlayerOnboard["관심 선수 선택 온보딩"]
    PlayerOnboard --> Home
    Login -->|"온보딩 완료 회원"| Home
    Home -->|"카드 클릭"| Detail["경기 상세\n(항상 보호 모드로 진입)"]
    Detail -->|"모드 토글에서 공개 선택\n(확인창 없이 즉시)"| DetailRevealed["경기 상세 · 공개 모드"]
    DetailRevealed -->|"모드 토글에서 보호 선택"| Detail
    Detail -->|"FINAL 전이 감지"| Replay["종료 경기 다시보기\n(경기 긴장도 그래프 · AI 헤드라인)"]
    Detail -->|"switchSuggestion 토스트"| OtherDetail["다른 경기 상세로 이동"]
    Home -->|"알림 아이콘"| NotiCenter["알림 센터"]
    Home -->|"마이페이지 아이콘"| MyPage["마이페이지\n알림 설정 + 관심 팀/선수"]
    MyPage -->|"관심 팀/선수 추가"| TeamPlayer["팀/선수 선택·관리"]
    Detail -.->|"급상승/관심 팀 시작\n인앱 토스트"| Toast["토스트 알림"]
    Toast --> NotiCenter
```

화면별 레이아웃과 상태는 [USER_FLOW.md](docs/product/USER_FLOW.md)를 따른다.

## DB 스키마

```mermaid
erDiagram
    teams ||--o{ players : "소속"
    teams ||--o{ games : "홈·원정"
    teams ||--o{ standings : "일별 순위"
    players ||--o{ lineups : "출전"
    players ||--o{ player_season_stats : "시즌 스탯"
    games ||--o{ plays : "play 로그"
    games ||--o{ watch_scores : "점수 이력"
    games ||--o{ game_events : "흥미 순간 이벤트"
    games ||--o{ lineups : "라인업"
    games ||--o{ odds_snapshots : "배당 스냅샷"
    games ||--o{ notification_events : "알림 발화"
    games ||--o{ score_task_outbox : "점수 작업 발행"
    users ||--o| user_settings : "설정"
    users ||--o{ user_favorite_teams : "관심 팀"
    teams ||--o{ user_favorite_teams : "관심 팀"
    users ||--o{ user_favorite_players : "관심 선수"
    players ||--o{ user_favorite_players : "관심 선수"
    users ||--o{ refresh_tokens : "토큰"
    users ||--o{ user_notifications : "수신함"
    notification_events ||--|| notification_outbox : "발행 상태"
    notification_events ||--o{ user_notifications : "fan-out"
```

테이블 스키마와 키 기준은 [DB_SCHEMA.md](docs/data/DB_SCHEMA.md)를 따른다.

## 알림 파이프라인

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

판정 조건과 이벤트 스키마는 [NOTIFICATIONS.md](docs/policy/NOTIFICATIONS.md)를 따른다.
