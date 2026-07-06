# 역할 분담 및 일정 계획

이 문서는 팀원이 병렬로 개발하기 위한 담당 영역, 계약 지점, 일정 기준을 정리한다.

## 1. 담당 영역별 기능 분해

backend 패키지 구조 확정안: `com.pulse.{api, poller, scorer, ranking, domain, common, replay}`. `poller`·`scorer`는 신규 패키지다. `api` 하위는 기능 패키지 `api.home` / `api.gamedetail` / `api.user` / `api.notification`으로 나눈다. 점수 계산 로직은 기존 `replay` 패키지에서 `scorer`로 이관하고(담당 예은), `replay`는 S3 재생 어댑터로 유지한다.

| 담당자 | 기능 영역 | backend 패키지 | frontend 폴더 |
|---|---|---|---|
| 예은(조장) | 데이터 파이프라인 · 점수 · 홈 · 공통 구조 | `poller` `scorer` `ranking` `replay` `api.home` `common` `domain` | `features/home` `shared` `app` |
| 창현 | AI 문구 생성 | ai-service 전체 + `AiCopyReader` 구현 | `features/ai-copy` |
| 민석 | 경기 상세 · 다시보기 | `api.gamedetail`(직렬화 가드 포함) | `features/game-detail` |
| 윤호 | 회원 · 알림 (+ 통합 후 운영·관측) | `api.user` `api.notification` | `features/auth` `features/notification` |

컨트롤러·서비스·DTO는 담당 기능 패키지 안에 둔다. 공유 지점은 `common/`과 `domain/`이며, 두 폴더 변경은 리뷰를 거친다. 기능 간 데이터 전달은 domain 읽기, Redis 이벤트, 공개 서비스 인터페이스로 제한한다.

선행 리팩토링 PR은 예은 단독 주관이며, `domain` 쓰기 소유도 예은에게 있다. Spring Security 도입 시 전원에게 공지한다.

## 2. 팀 간 계약 지점

아래 계약을 기준으로 구현체가 없어도 각자 목(mock)으로 개발을 시작할 수 있다. 상세 시그니처·스키마는 [API_CONTRACTS.md](API_CONTRACTS.md) §7이 단일 기준이다.

| 계약 | 제공 → 사용 | 형태 |
|---|---|---|
| 경기 데이터 엔티티 | 예은 → 민석·창현 | `domain` 읽기 전용 (스키마 변경은 예은만) |
| 최신 신호 조회 | 예은 → 민석 | `ScoreQueryService.getLatestSignals(gameId)` — 점수 숫자는 계약에 없음 |
| 알림 이벤트 | 예은(scorer·poller) → 윤호(알림) | RabbitMQ `notify.events`. 판정은 발행 측, 사용자 전달·저장은 api |
| 재조회 신호 | 예은(scorer) → api(SSE) | Redis pub/sub `signal:*` — SSE 이벤트 3종으로 중계 |
| 선호 조회 | 윤호 → 예은(홈 가산)·api(알림 fan-out·전환 쿨다운) | `UserPreferenceReader` 공개 인터페이스 |
| AI 문구 조회 | 창현 → 예은·민석·윤호 | `AiCopyReader.getCopy(gameId, purpose)` — 항상 non-null, 폴백 내장 |

## 3. 확정 기준

| # | 항목 | 기준 |
|---|---|---|
| ① | 인증 방식 | JWT 액세스 토큰 + 리프레시 토큰. 소셜 로그인은 추후 확장 |
| ② | poller→scorer 큐 | RabbitMQ `score.tasks` (선정 근거: [TECH_STACK.md](TECH_STACK.md)) |
| ③ | 실시간 채널 | 홈·상세·알림 모두 SSE, 신호+재조회 방식(payload에 데이터를 싣지 않음) |
| ④ | 알림 채널 범위 | 1차 인앱만, Web Push는 배포 후 여유 시 |
| ⑤ | 보호 모드 홈 노출 정책 | 매치업, 이닝 숫자(초/말 제외), 추천 이유 태그, AI 문구를 노출한다. `watch_score`는 정렬에만 쓰고 등급·순위·숫자는 노출하지 않는다 |
| ⑥ | domain 소유권 | 공용 유지 + 소유권 규칙(예은). 경기 데이터는 3~4개 기능이 함께 읽어 이관하면 오히려 남의 패키지 내부 참조로 바뀌고 스키마 변경 리뷰 신호가 사라진다 |
| ⑦ | 선행 리팩토링 PR | 예은 단독 |
| ⑧ | DB 스키마 관리 | 로컬 `ddl-auto: update`, 배포 환경(RDS)은 Flyway — 통합 시점(7/14 전후) 베이스라인 전환 |
| ⑨ | 공개 모드 세분화 | 경기 단위 전체 토글만. 계정 단위 기본 공개 설정은 두지 않고, 공개 상태는 클라이언트에만 저장 |
| ⑩ | AI 비용 정책 | 신호 유의 변화 시에만 재생성 + 캐시. 생성은 데이터 갱신 시점 비동기, 종료 경기 문구는 DB 영속 |
| ⑪ | 알림 처리 배치 | 판정=scorer(급상승)·poller(경기 시작), 전달·저장=api. 채널은 RabbitMQ `notify.events` |
| ⑫ | 배포 토폴로지 | EC2 1대 Docker Compose(컨테이너 8개) + PostgreSQL만 RDS 분리 |
| ⑬ | 알림 fan-out 범위 | SURGE 알림은 전역(관심 팀 무관), `notify_surge_enabled`로 개인 차단. GAME_START는 관심 팀 사용자에게만 fan-out |
| ⑭ | 기본 스포일러 모드 계정 설정 | MVP 공식 제외(항상 보호 모드 시작, 공개 상태는 클라이언트 저장) — [PROJECT_PROPOSAL.md](PROJECT_PROPOSAL.md) §6.6 대비 범위 축소 |
| ⑮ | 관심 선수 부상 표시 | 1차 제외, `player_injuries` 적재와 함께 후속 도입 |
| ⑯ | AI 경기 유형 분류 | MVP 제외, 추후 확장(확장 포인트: AI purpose enum 추가·태그 어휘 확장·games 컬럼 Flyway 증분 — 아키텍처 변경 불필요) |
| ⑰ | 백테스트 회귀 리포트 | 1차는 수동, 자동화는 통합 후 확정 로드맵([RECOMMENDATION_SCORE.md](RECOMMENDATION_SCORE.md) §8.3, 담당 예은) |
| ⑱ | backend 패키지 구조 | §1 확정안(컨테이너 경계 = 패키지 경계) |

## 4. 단계별 일정

### 단계 1 — 핵심 백엔드 + UI 확정 (7/2 ~ 7/8)

| 팀원 | 작업 |
|---|---|
| 예은 | 폴러 워커 + 레이트리밋/재시도(backoff) → 오늘 경기·plays 저장 → RabbitMQ 메시지 발행 → 점수계산 워커(점수 로직 이식) → Redis Sorted Set 랭킹 |
| 창현 | FastAPI 골격 + LLM 연동 PoC → 스포일러 누출 검사 게이트 |
| 윤호 | Security 인증 → 회원/로그인 → 관심 팀·선수 설정 CRUD |
| 민석 | 보호/공개 DTO·직렬화 가드(최우선) → 경기 상세 API |

### 단계 2 — 프론트 연결 + 개인화 + AI 통합 (7/9 ~ 7/14)

| 팀원 | 작업 |
|---|---|
| 예은 | 홈 추천 보드 프론트(Vercel 배포) + SSE 연결로 랭킹 실시간 업데이트 + 경기 전환 추천 + 가중치 백테스트(수동 리포트) |
| 창현 | AI 요약 생성·검수 파이프라인 완성 + 문구 표시 연동 |
| 윤호 | 설정·알림함 프론트 + 개인화 보정 연결 |
| 민석 | 경기 상세 프론트 + 스포일러 공개 UX + 예정 경기 카드·상세 (팀/선수 상세 페이지는 통합 후 여유 시) |

**7/14 = 전체 흐름 통합:** 홈 → 상세 → 공개 전환 → 알림으로 이어지는 전체 시나리오

7/14 전에는 [FEATURE_SPEC.md](FEATURE_SPEC.md) §6 데모 완료 기준 8개 외 신규 범위를 추가하지 않는다.

### 단계 3 — 배포 & 테스트 (7/15 ~ 7/17)

- **프론트**: Vercel
- **백엔드/워커/AI**: AWS EC2 t3.large 1대 + Docker Compose
  - `api` / `poller` / `scorer` / `ai-service` 각각 컨테이너
  - Redis + RabbitMQ + Prometheus + Grafana 도커로 같은 EC2에 실행
  - **PostgreSQL은 RDS로 분리** (배포 환경은 Flyway 마이그레이션)
- **CI/CD**: GitHub Actions 배포 파이프라인 (push → 빌드 → EC2 자동 배포)
- **환경변수/시크릿**: `.env` 파일 또는 GitHub Actions Secrets 활용
- → **스테이징 배포 성공**

### 발표 준비 (7/18 ~ 7/21)

**replay 고정 시연 시나리오**(추천 점수 변화가 잘 보이는 구간), 정상/장애(fallback·오래된 데이터 표시) 비교 데모, **데모 녹화본** 확보.

### 발표일 (7/22)

## 5. 통합 후 운영·관리 로드맵

담당 윤호. 회원·알림(`api.user` `api.notification`) 완료가 선행 조건이며, 단계 3 배포 이후 우선순위 순으로 진행한다.

1. Grafana 도메인 대시보드 1장 구성·소유 (외부 API 성공률·429, 폴링 지연, 큐 적체·DLQ, SSE 연결 수)
2. GitHub Actions CI/CD 파이프라인 관리 (push → 빌드 → EC2 배포)
3. 시크릿 관리(`.env`, GitHub Actions Secrets) · 컨테이너 메모리 제한 점검
4. 보관 배치 운영: `user_notifications` 7일 삭제, `refresh_tokens` 만료 정리
5. RDS 백업·시점 복구 리허설
6. 장애 대응 런북: 429 백오프 확인 절차, DLQ 재처리 절차
7. (배포 후 여유 시) Web Push 검토
