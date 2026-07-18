# PULSE 문서 색인

`docs/`는 문서 종류별 5개 폴더로 구성한다. 각 문서는 한 주제만 다룬다.

| 폴더 | 종류 |
|---|---|
| `product/` | 무엇을 만드는가 — 기획과 기능 범위 |
| `design/` | 어떻게 만드는가 — 시스템 설계 |
| `reference/` | 외부 데이터 실측 레퍼런스 |
| `testing/` | 테스트 범위와 검증 기록 |
| `team/` | 팀 규칙과 운영 |

## product — 기획

| 문서 | 문서 범위 |
|---|---|
| [PROJECT_PROPOSAL.md](product/PROJECT_PROPOSAL.md) | 서비스 개요, 해결하려는 문제, 핵심 관전 경험 |
| [FEATURE_SPEC.md](product/FEATURE_SPEC.md) | 사용자 기능 범위, 데모 완료 기준 |
| [USER_FLOW.md](product/USER_FLOW.md) | 사용자 여정, 화면별 레이아웃·상태·인터랙션, 실 데이터 예시 |

## design — 시스템 설계

| 문서 | 문서 범위 |
|---|---|
| [ARCHITECTURE.md](design/ARCHITECTURE.md) | 전체 구조, 컴포넌트 배치 이유, 설계 원칙 |
| [DATA_PIPELINE.md](design/DATA_PIPELINE.md) | 폴링 상태 머신, 호출 예산·429 대응, 계산·응답 흐름 |
| [DATA_STORAGE.md](design/DATA_STORAGE.md) | 저장/비저장 정책, 유실 수락 기준, S3 아카이브·DB 이전 |
| [DB_SCHEMA.md](design/DB_SCHEMA.md) | PostgreSQL 테이블 스키마와 타입·키 기준 |
| [SCORING.md](design/SCORING.md) | watch/pregame/peak 점수 공식, 신호 정의, 결측 처리 |
| [BACKTEST.md](design/BACKTEST.md) | 가중치 백테스트 재생·지표·튜닝·영향 리포트 |
| [SPOILER_POLICY.md](design/SPOILER_POLICY.md) | 보호/공개 모드, 화면별 노출·금지 필드, 태그·이벤트 표기 |
| [NOTIFICATIONS.md](design/NOTIFICATIONS.md) | 알림·경기 전환 추천 조건, 파이프라인, 이벤트 스키마·fan-out |
| [AI_COPY.md](design/AI_COPY.md) | AI 문구 생성 트리거, 컨텍스트·HTTP 계약, 검수·저장 |
| [API_CONTRACTS.md](design/API_CONTRACTS.md) | REST 계약 기준과 주제별 계약 문서 색인 |
| [AUTH_POLICY.md](design/AUTH_POLICY.md) | 인증 토큰·이메일 인증, 관심 선수 검색·등록 정책 |
| [SSE.md](design/SSE.md) | SSE 이벤트, 연결·인증·재연결 정책 |
| [MESSAGING.md](design/MESSAGING.md) | RabbitMQ 큐, ScoreTask, outbox·멱등 계약 |
| [REDIS_KEYS.md](design/REDIS_KEYS.md) | Redis 키 이름·타입·라이프사이클 |
| [MODULE_INTERFACES.md](design/MODULE_INTERFACES.md) | 팀 간 Java 인터페이스와 제공·사용 경계 |
| [TECH_STACK.md](design/TECH_STACK.md) | 기술 스택 선정 이유, 배포 토폴로지, 기각 목록 |
| [PROJECT_STRUCTURE.md](design/PROJECT_STRUCTURE.md) | 레포 폴더·패키지 구조와 의존 방향 |
| [OBSERVABILITY.md](design/OBSERVABILITY.md) | Prometheus 지표 정의, Grafana 조회·경보 기준 |

## reference — 데이터 레퍼런스

| 문서 | 문서 범위 |
|---|---|
| [EXTERNAL_DATA_API.md](reference/EXTERNAL_DATA_API.md) | balldontlie MLB API의 호출 제한, 실제 제공 데이터, 등장 시점 |

## testing — 테스트

| 문서 | 문서 범위 |
|---|---|
| [UNIT_TESTS.md](testing/UNIT_TESTS.md) | 모듈별 단위 테스트 클래스, 핵심 시나리오, 실행 방법·작성 원칙 |
| [INTEGRATION_TESTS.md](testing/INTEGRATION_TESTS.md) | DB·HTTP·Spring 컨텍스트 통합 범위와 테스트 인프라 |
| [FUNCTIONAL_TESTS.md](testing/FUNCTIONAL_TESTS.md) | 사용자 주요 흐름의 기능 테스트 체크리스트 |

## team — 규칙·운영

| 문서 | 문서 범위 |
|---|---|
| [CONVENTIONS.md](team/CONVENTIONS.md) | 브랜치·커밋·PR 규칙, 코드 컨벤션 |
| [ROLES_AND_SCHEDULE.md](team/ROLES_AND_SCHEDULE.md) | 담당 영역, 계약 지점, 완료 기준, 일정 |
