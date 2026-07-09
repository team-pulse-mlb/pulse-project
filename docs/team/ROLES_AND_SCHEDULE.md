# 역할 분담 및 일정 계획

## 1. 담당 영역

| 담당자 | 담당 영역 | backend | frontend |
|---|---|---|---|
| 예은(조장) | 데이터 파이프라인, 점수·추천 로직, 홈, 공통 구조 | `poller`, `scorer`, `ranking`, `replay`, `api.home`, `common`, `domain` | `features/home`, `shared`, `app` |
| 창현 | AI 문구 생성·검수 | `ai-service/`, `com.pulse.ai` | `features/ai-copy` |
| 민석 | 경기 상세, 상세 화면 다시보기, 상세 화면 내 전환 알림 | `api.gamedetail` | `features/game-detail` |
| 윤호 | 회원, 알림, 통합 후 운영·관측 | `api.user`, `api.notification` | `features/auth`, `features/notification` |

`common`과 `domain`은 공용 영역이지만 쓰기 소유자는 예은이다. 스키마·계약 변경은 모듈 인터페이스 영향 범위 안에서만 진행한다.

### 예은 — 데이터·점수·홈·공통

- 기반 배선: V1·V2 스키마·BalldontlieClient·프로파일·RabbitMQ 큐·DLQ 설정, 공유 메시지 DTO(`ScoreTask`·`NotificationEvent`)·발행·소비 배선
- poller 워커: 생명주기 상태머신, 상태별 폴링·백오프, `games`·`plays` 증분 수집, situation 추출, `ScoreTask`·`GAME_START`·종료 task 발행, 선발 시즌스탯 적재
- scorer 워커: 8신호 점수·다시보기 구간 로직(`replay`→`scorer` 이관), `score.tasks` 소비, `watch_score`·`peak_base_score`·`game_events` 영속, Redis 랭킹·캐시·`signal` 발행, SURGE 판정, `pregame_score`, 종료 정리
- api.home: `GET /api/rankings/live`(슬롯·개인화 가산·보호 DTO), `GET /api/games`(슬레이트·상태·정렬)
- SSE: `GET /api/sse`·1회용 토큰, `signal:*` 구독 → 이벤트 3종 중계
- 전환 후보 조회 지점(랭킹 기반) 제공 — 민석 상세가 소비
- 프론트: Vite+React 기반·`shared`·홈 보드·SSE 연결·Vercel 배포

### 창현 — AI 문구

- ai-service 생성·검수 파이프라인·스포일러 게이트
- `com.pulse.ai` AiCopyReader(폴백 내장), 라이브 Redis·종료 PG 저장, scorer 비동기 트리거

### 민석 — 경기 상세·다시보기·전환 알림

- 보호/공개 직렬화 가드, 상세 API·`/replay`·`/events`
- switchSuggestion 산출·응답 포함·쿨다운(랭킹은 예은 제공)
- 상세 화면·공개 전환 UX·타임라인·SSE `game_updated` 갱신·예정 경기 카드

### 윤호 — 회원·알림

- Security·JWT(회전·재사용 감지)·회원·이메일 인증·`preferences`·UserPreferenceReader 제공
- `notify.events` 소비·fan-out·알림 센터·7일 보관·프론트
- (통합 후) Grafana·CI/CD·백업·런북

## 2. 협업 지점

| 지점 | 역할 구분 |
|---|---|
| 경기 데이터·점수 신호 | 예은이 저장·계산하고, 민석·창현·윤호는 필요한 값을 읽어 사용한다. |
| 경기 전환 추천 | 예은이 추천 후보 산출·저장을 담당하고, 민석이 경기 상세 화면과 상세 화면 내 전환 알림을 담당한다. |
| 홈 추천 | 예은이 홈 랭킹, 추천순 정렬, 홈 화면 표시를 담당한다. |
| AI 문구 | 창현이 ai-service 문구 생성·검수와 `com.pulse.ai`의 기본 문구 fallback 판단을 담당하고, 각 화면 담당자가 표시 위치에 맞게 사용한다. |
| 회원·알림 | 윤호가 회원, 관심 팀·선수 설정, 알림 저장·전달을 담당한다. |
| 운영·관측 | 윤호가 통합 이후 Grafana, GitHub Actions 운영 파이프라인, 시크릿 관리, 백업·복구, 장애 런북을 담당한다. |

## 3. 단계별 일정

### 단계 1 — 핵심 백엔드 + UI 정리 (7/2 ~ 7/8)

| 팀원 | 작업 |
|---|---|
| 예은 | 폴러 워커, 오늘 경기·plays 저장, RabbitMQ 메시지 발행, 점수계산 워커, Redis 랭킹 |
| 창현 | FastAPI 골격, LLM 연동 PoC, 스포일러 누출 검사 게이트 |
| 윤호 | Security 인증, 회원·로그인, 관심 팀·선수 설정 CRUD |
| 민석 | 보호/공개 DTO·직렬화 가드, 경기 상세 API |

### 단계 2 — 프론트 연결 + 개인화 + AI 통합 (7/9 ~ 7/14)

| 팀원 | 작업 |
|---|---|
| 예은 | 홈 추천 보드 프론트, SSE 랭킹 업데이트, 전환 추천 후보 산출·저장, 가중치 백테스트 |
| 창현 | AI 요약 생성·검수 파이프라인, 문구 표시 연동 |
| 윤호 | 설정·알림함 프론트, 개인화 보정 연결 |
| 민석 | 경기 상세 프론트, 스포일러 공개 UX, 상세 화면 내 전환 알림, 예정 경기 카드·상세 |

**7/14 = 전체 흐름 통합:** 홈 → 상세 → 공개 전환 → 알림으로 이어지는 전체 시나리오

7/14 전에는 통합 데모 완료 기준 8개 외 신규 범위를 추가하지 않는다.

### 단계 3 — 배포 & 테스트 (7/15 ~ 7/17)

| 팀원 | 작업 |
|---|---|
| 예은 | 백엔드·워커 배포 지원, 점수·데이터 파이프라인 검증 |
| 창현 | AI 서비스 배포 지원, 문구 생성·검수 흐름 검증 |
| 민석 | 경기 상세·다시보기 화면 통합 검증 |
| 윤호 | 배포 파이프라인, 운영 시크릿, 관측·알림 운영 준비 |

### 발표 준비 (7/18 ~ 7/21)

| 팀원 | 작업 |
|---|---|
| 예은 | 추천 점수 변화 시연 구간, 정상·장애 비교 데모 준비 |
| 창현 | AI 문구 데모와 스포일러 검수 사례 준비 |
| 민석 | 경기 상세·다시보기·전환 알림 데모 준비 |
| 윤호 | 회원·알림·운영 흐름 데모 준비 |

### 발표일 (7/22)
