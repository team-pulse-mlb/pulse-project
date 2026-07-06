# PULSE 문서 색인

이 문서는 `docs/` 전체의 색인이다. PULSE 문서의 목적과 분류를 한곳에서 확인하기 위해 사용한다.

| 문서 | 분류 | 한 줄 설명 |
|---|---|---|
| [PROJECT_PROPOSAL.md](PROJECT_PROPOSAL.md) | 설계 | PULSE 서비스의 개요, 해결하려는 문제, 핵심 관전 경험을 정의한다. |
| [ARCHITECTURE_AND_DATA_FLOW.md](ARCHITECTURE_AND_DATA_FLOW.md) | 설계 | 경기 데이터 수집부터 점수 계산, 추천 노출까지의 처리 흐름과 컴포넌트 선택 이유를 설명한다. |
| [TECH_STACK.md](TECH_STACK.md) | 설계 | 3주 내 통합 데모와 배포를 목표로 선택한 기술 스택과 선정 이유를 정리한다. |
| [DB_SCHEMA.md](DB_SCHEMA.md) | 설계 | PULSE 운영 PostgreSQL 데이터베이스의 테이블 스키마와 타입 기준을 정의한다. |
| [API_CONTRACTS.md](API_CONTRACTS.md) | 설계 | REST, SSE, 화면별 노출 필드, 모듈 인터페이스, 메시지·캐시 스키마의 단일 계약 기준을 정의한다. |
| [RECOMMENDATION_SCORE.md](RECOMMENDATION_SCORE.md) | 설계 | 예정, 진행 중, 종료 경기를 정렬하는 내부 추천 점수 계산 방식을 정의한다. |
| [RECOMMENDATION_POLICY.md](RECOMMENDATION_POLICY.md) | 설계 | 계산된 신호를 화면, 알림, AI 문구에 스포일러 없이 노출하는 정책을 정의한다. |
| [FEATURE_SPEC.md](FEATURE_SPEC.md) | 설계 | 사용자가 보는 기능 범위와 화면 동작, 스포일러 보호 기본값을 정의한다. |
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | 설계 | 레포 전체의 폴더·패키지 구조와 쓰기 소유자를 정리한다. |
| [DB_MIGRATION.md](DB_MIGRATION.md) | 설계 | S3 임시 수집 데이터를 운영 PostgreSQL로 이전하고 운영 데이터 경로로 전환하는 실행 계획을 정의한다. |
| [EXTERNAL_DATA_API.md](EXTERNAL_DATA_API.md) | 데이터 레퍼런스 | balldontlie MLB API의 호출 제한, 실제 제공 데이터, 데이터 등장 시점을 실측 기준으로 정리한다. |
| [CONVENTIONS.md](CONVENTIONS.md) | 규칙·운영 | 팀원이 같은 방식으로 브랜치, 코드, PR을 관리하기 위한 개발 기준을 정리한다. |
| [ROLES_AND_SCHEDULE.md](ROLES_AND_SCHEDULE.md) | 규칙·운영 | 병렬 개발을 위한 담당 영역, 계약 지점, 일정 기준을 정리한다. |
| [ONBOARDING.md](ONBOARDING.md) | 규칙·운영 | 팀원이 로컬에서 raw archive 재생 기반 개발 환경을 시작하는 절차를 정리한다. |
