# 운영 DB 이전 실행 계획

이 문서는 S3 임시 수집 데이터를 운영 PostgreSQL(RDS)로 이전하고, 운영 `poller` 기준 데이터 경로로 전환하기 위한 실행 계획을 정의한다. 배포 목표는 7/15이며 가능한 한 조기 진행한다. 관련 기준은 [DB_SCHEMA.md](DB_SCHEMA.md) §E-3의 S3 원본 레이아웃, §F의 `source` 컬럼 정의, [ARCHITECTURE_AND_DATA_FLOW.md](ARCHITECTURE_AND_DATA_FLOW.md) §10의 이전 배경 개요, [RECOMMENDATION_SCORE.md](RECOMMENDATION_SCORE.md) §8.4의 백테스트 파이프라인을 따른다.

## 사전 준비 체크리스트

소유자는 예은과 저장소 소유자다. 아래 항목이 준비되어야 0단계를 시작한다.

| 항목 | 무엇을 준비하는지 | 어디서 발급·설정하는지 | 전달해야 할 값 |
|---|---|---|---|
| RDS PostgreSQL 16 인스턴스 | 운영 스키마를 적용할 PostgreSQL 16 인스턴스. 규모는 `db.t3.micro`~`db.t3.small`이다. | AWS RDS 콘솔 | 엔드포인트, 포트, DB 이름 |
| DB 접속 시크릿 | 이전 스크립트와 Flyway가 사용할 접속 정보. 예: `DATABASE_URL`, 사용자명, 비밀번호. | AWS Secrets Manager 또는 배포 환경 변수 | `DATABASE_URL`, 사용자명, 비밀번호 |
| 보안 그룹 인바운드 규칙 | 5432 포트 접근 허용. 접근 허용 범위는 실행 환경으로 제한한다. | AWS EC2 보안 그룹 | 보안 그룹 ID, 허용 CIDR 또는 실행 주체 |
| S3 읽기 전용 IAM 자격 | `raw-archive` 버킷을 읽을 수 있는 최소 권한 자격. | AWS IAM | 액세스 키 또는 역할 ARN, 대상 버킷명 |
| `AWS_REGION` 값 | RDS, S3, IAM 호출에 사용할 리전. | AWS 계정 설정 및 배포 환경 변수 | `AWS_REGION` |
| balldontlie API 키 | 이전 스크립트가 검증 재조회를 수행할 경우 사용할 API 키. | balldontlie 계정 또는 시크릿 저장소 | API 키 시크릿명 또는 값 |
| Lambda EventBridge 트리거 비활성화 권한 | S3 아카이빙 중단 시 raw-archive Lambda 트리거를 비활성화할 권한. | AWS IAM, EventBridge, Lambda 콘솔 | 권한 보유 주체, 트리거 이름 |

## 운영 DB 이전 실행 단계

### 0단계. 사전 준비 검증

- 수행 내용
  - RDS PostgreSQL 16 접속 정보를 확인한다.
  - S3 `raw-archive` 버킷 읽기 권한을 확인한다.
  - Flyway 베이스라인 V1 스키마를 리뷰한다.
- 검증 기준
  - RDS 접속이 성공한다.
  - S3 `raw-archive` 읽기가 성공한다.
  - Flyway 베이스라인 V1 스키마 리뷰가 통과한다.

### 1단계. 운영 스키마 적용

- 수행 내용
  - Flyway 베이스라인 V1을 RDS에 적용한다.
  - 베이스라인에는 `source` 컬럼이 포함되어야 한다.
- 검증 기준
  - Flyway 명령이 성공한다.
  - §F-1에 명시된 7개 테이블(`games`·`plays`·`watch_scores`·`replay_segments`·`odds_snapshots`·`standings`·`lineups`)에 `source` 컬럼이 존재한다.

### 2단계. S3 원본 이전 스크립트 실행

- 수행 내용
  - S3 `raw-archive`의 `games`·`plays`·`odds`·`standings`·`lineups` 원본을 운영 스키마로 변환해 적재한다.
  - 라이브 아카이브와 백필 데이터는 `source` 값으로 구분한다.
- 검증 기준
  - S3 객체 수와 적재된 행 수 대조가 일치한다.
  - UNIQUE 제약 위반이 0건이다.
  - `observed_at NOT NULL` 위반이 0건이다.

### 3단계. scorer 파생 데이터 재생

- 수행 내용
  - 이전된 `plays`로 scorer를 1회 재생한다.
  - 과거 경기의 `watch_scores`와 `replay_segments`를 채운다.
  - 종료 정리 주체는 scorer로 일원화한다.
- 검증 기준
  - 이전 대상 각 경기마다 `watch_scores` 행이 존재한다.
  - `replay_segments` 구간이 정합성 있게 생성된다.

### 4단계. 운영 poller 가동과 cutover 기록

- 수행 내용
  - 운영 `poller`를 가동한다.
  - 이전 경계 시각(cutover)을 기록한다.
- 검증 기준
  - 가동 이후 신규 데이터가 `source=OPERATIONAL`로 적재된다.

### 5단계. 운영 정상 확인

- 수행 내용
  - 홈 랭킹 API와 상세 API를 확인한다.
  - SSE와 알림 동작을 확인한다.
  - Prometheus와 Grafana 관측 지표를 확인한다.
- 검증 기준
  - 홈 랭킹과 상세 API가 정상 응답한다.
  - SSE와 알림이 정상 동작한다.
  - 관측 지표가 정상 수집된다.

### 6단계. S3 아카이빙 중단

- 수행 내용
  - raw-archive Lambda의 EventBridge 트리거를 비활성화한다.
  - 이 단계는 4단계와 5단계 통과가 하드 게이트다.
- 검증 기준
  - EventBridge 트리거가 비활성화된다.
  - 비활성화 이후 신규 S3 아카이빙 객체가 생성되지 않는다.

### 7단계. 후속 문서 갱신

- 수행 내용
  - `ONBOARDING.md`를 현재 S3 리플레이 절차에서 운영 DB 기준 단계별 절차로 갱신해야 한다.
  - `ONBOARDING.md` 수정은 이 문서 작성 작업 범위에 포함하지 않는다.
- 검증 기준
  - 후속 조치 항목으로 남긴다.

## 이전 스크립트 설계

### 입력 데이터

- 입력은 S3 `raw-archive`의 5종 원본이다.
  - `games`
  - `plays`
  - `odds`
  - `standings`
  - `lineups`
- §E-3은 S3 원본을 개발·백테스트용 원본 아카이브로 정의하며, 객체는 `observed_at`, `endpoint`, `params`, `response`를 가진 gzip JSON이다.
- §E-3에 명시된 객체 키 레이아웃은 다음과 같다.

```text
raw/games/dt=YYYY-MM-DD/games_HHMMSSZ.json.gz
raw/plays/game_id=<id>/plays_YYYY-MM-DD_HHMMSSZ_c<cursor>.json.gz
```

### 변환 규칙

- 각 원본의 `response`를 운영 스키마 컬럼에 맞게 매핑한다.
  - `games` 원본은 `games` 테이블의 최신 스냅샷 컬럼으로 매핑한다.
  - `plays` 원본은 `plays` 테이블의 경기별 append 로그 컬럼으로 매핑한다.
  - `odds` 원본은 `odds_snapshots` 테이블의 경기 전 배당 스냅샷 컬럼으로 매핑한다.
  - `standings` 원본은 `standings` 테이블의 일 배치 순위 컬럼으로 매핑한다.
  - `lineups` 원본은 `lineups` 테이블의 라인업·선발 컬럼으로 매핑한다.
- `source` 값은 §F-1 정의를 따른다.
  - 라이브 아카이브 이전분은 `S3_LIVE_ARCHIVE`로 표기한다.
  - 과거 시즌 백필 이전분은 `S3_BACKFILL`로 표기한다.
  - 운영 `poller` 수집분은 기본값 `OPERATIONAL`로 표기한다.
- `backfilled` 불리언은 `source = 'S3_BACKFILL'`과 동치로 취급한다.
- `observed_at`과 데이터 정밀도 차이는 §F-2의 운영 수집분과의 차이를 반영한다.

| 항목 | 차이 |
|---|---|
| `observed_at` 정밀도 | `S3_LIVE_ARCHIVE`의 관측 주기가 운영(약 20초)과 다를 수 있어 시간 감쇠(최근 득점·리드 변경) 오차 상한이 커진다. `S3_BACKFILL`은 벽시계 시각이 없어 order 윈도우로 근사한다. |
| 압박 신호 | PA 원본이 운영 DB에 영속하지 않으므로(§E-1) 이전 데이터의 압박 신호는 `watch_scores.signal_contributions`의 기여값만 보존되고 재계산할 수 없다. |
| `watch_scores` | 이전분은 이전 시점 `scoring.yml` 버전으로 재생한 값이다. 백테스트에서는 기준(baseline) 참고용으로만 쓰고, 가중치 재계산 시 새로 산출한다. |
| 선수 시즌 스탯 | `player_season_stats`는 최신값을 덮어써 과거 경기 시점 ERA를 복원할 수 없다. 이전 데이터의 `pregame_score` 선발 매치업 재계산은 제한된다. |
| 수집 주기·결측 | 라이브 아카이브·백필의 수집 주기가 운영 계획과 다를 수 있고, 일부 필드가 결측일 수 있다. 결측은 `null`로 두고 `source`로 구분한다. |

### 멱등성 원칙

- 이전 스크립트는 재실행 가능해야 한다.
- 대상 테이블의 UNIQUE 제약을 기준으로 중복 적재를 막는다.
- 이미 이전된 레코드는 스킵하거나 동일 키 충돌 시 기존 행을 유지한다.
- 재실행으로 `watch_scores`와 `replay_segments`가 중복 생성되지 않도록 경기·시각·구간 키 기준의 멱등 처리를 적용한다.
- 구체 SQL은 구현 단계에서 정의한다.

### 실행 방법

- 이전 스크립트는 별도 배치 스크립트 또는 Gradle 태스크 형태로 실행한다.
- 실제 구현은 다음 세션 범위다.
- 실행 환경은 RDS 쓰기 권한과 S3 읽기 권한을 가진다.

## 실패 대응 기준

- 각 단계의 검증 기준을 통과하지 못하면 다음 단계로 진행하지 않는다.
- 0~3단계 실패는 원인 수정 후 이전 스크립트의 멱등성을 전제로 재실행한다.
- 4단계 실패는 운영 `poller`를 정상화하고 cutover 기록을 검증한 뒤 5단계로 진행한다.
- 5단계 실패는 API, SSE, 알림, 관측 지표별 원인을 분리해 조치한다.
- 6단계는 4단계와 5단계 통과 전에는 어떤 경우에도 실행하지 않는다.
- 6단계를 잘못 실행한 경우 즉시 EventBridge 트리거를 복구하고 4단계와 5단계 검증을 다시 수행한다.
