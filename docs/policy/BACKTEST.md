# 백테스트와 가중치 튜닝

## 1. 데이터와 재생 방법

- 데이터: DB 이전 완료 후 백테스트는 **운영 DB에 적재된 이력**으로 수행한다. 입력은 `plays`·`games`·`watch_scores`·`odds_snapshots`·`standings`이며, S3에서 이전·보존한 과거 데이터를 포함한다. S3 원본을 직접 재생하는 방식은 DB 이전 전까지의 잠정 방식이며, 이전 완료 후에는 사용하지 않는다.
- 데이터 구분: 이전 데이터는 `source` 컬럼(`S3_LIVE_ARCHIVE`·`S3_BACKFILL`)과 이전 경계 시각으로 운영 수집분(`OPERATIONAL`)과 구분한다. 재생 로직은 `source`에 따라 시간 감쇠 방식을 분기한다.
- 적용 버전: 신규 저장·재계산한 `watch_scores`는 `scoring_version`으로 적용한 `scoring.yml` 스냅샷을 구분한다. 기존 행의 `null`은 버전 미상으로 다룬다.
- 재생: 경기별 plays를 order 순으로 점수 함수에 주입한다.
  - `OPERATIONAL`·`S3_LIVE_ARCHIVE`(observed_at 실측 보유): 실제 시간 감쇠로 재생한다. `S3_LIVE_ARCHIVE`는 관측 주기가 운영(약 20초)과 다를 수 있어 감쇠 오차 상한이 커진다.
  - `S3_BACKFILL`(과거 시즌, 벽시계 시각 없음): 시간 감쇠 항을 order 윈도우로 근사한다(최근 득점: 이후 15 plays 선형 감쇠, 리드 변경: 이후 25 plays 선형 감쇠).
- 압박 신호: 타석 시작 시 주자 상태가 `plays.runner_on_*`로 영속되므로 DB 기반 재생에서 재계산·가중치 튜닝이 가능하다. `runner_on_*`가 `null`인 play(PA 미대응)는 해당 시점 압박을 0점 처리한다.
- 그 외 PA 기반 상세 신호(강한 타구·구속 저하·긴 타석): 랭킹 신호가 아니라 태그·이벤트 표시 전용이므로 가중치 튜닝 대상이 아니다. PA 원본은 운영 DB에 영속하지 않으므로 DB만으로는 재산출할 수 없고, `game_events`에 저장된 추출 결과를 사용하며 소급 재추출이 필요하면 S3 PA 아카이브를 재생한다.

## 2. 검증 지표

| 지표 | 방법 | 목적 |
|---|---|---|
| 정렬 품질 | 경기 단위 접전 지표(최종 점수 차, 리드 변경 횟수, 8회 이후 동점 여부, 연장 여부)와 경기 peak `watch_score`의 순위 상관 | 점수 상위 경기가 실제 접전이었는지 |
| 예측력 | 시점 t의 `base_score`와 "이후 12 plays 내 득점 또는 리드 변경 발생"의 판별력(AUC) | 점수가 긴장을 결과보다 먼저 잡는지 |
| 알림 빈도 | 알림 임계(`scoring.yml` `alert-score`) 기준 하루 알림 발생 수 분포 | 알림이 하루 2~5건 범위가 되도록 임계·상수 조정 |

## 3. 튜닝 절차

`scoring.yml` 상수를 그리드 서치로 조정한다. 지표 우선순위는 예측력 > 정렬 품질 > 알림 빈도로 한다. 상수 변경 시 `version`을 올리고 `tune:` PR에 영향 리포트를 첨부한다. 지표는 정답이 아니라 변경 영향을 확인하는 가드레일로 읽는다(예: 알림 수 급증, 정렬 품질 급락).

## 4. 가중치 영향 추적 파이프라인

`scoring.yml` 상수 변경의 영향을 운영 DB 이력 재계산으로 자동 산출한다. `backtest` 배치 프로파일과 `backtestImpact` Gradle 태스크로 구현되어 있다(`backend/src/main/java/com/pulse/replay/backtest/`).

| 구분 | 내용 |
|---|---|
| 입력 | 기준 상수(`-Pbaseline`: version 정수 → classpath `scoring-baselines/scoring-v<N>.yml` 스냅샷, 그 외 → yml 파일 경로), 후보 상수(`-Pcandidate`: yml 파일 경로, 생략 시 현재 `scoring.yml`), 대상 기간(`-Pfrom`·`-Pto`, `start_time` UTC 날짜, 양끝 포함), 선택 필터 `-PgameIds`·`-Psources`(쉼표 구분) |
| 처리 | (1) 운영 DB에서 대상 경기의 `plays`·`games`·`standings`·`odds_snapshots`를 **읽기 전용**으로 로드 → (2) 기준·후보 두 상수 세트로 각각 scorer를 재계산한다(운영 테이블에 쓰지 않고 메모리에서 수행) → (3) 두 결과의 검증 지표와 아래 리포트 항목을 비교 |
| 출력 | `-PoutputDir`(기본 `docs/backtest`)에 `impact_v<기준>_vs_v<후보>_<from>_<to>.json`·`.md` 생성, `tune:` PR에 첨부 |
| 실행 | JDK 21 기준. 예: `backend\gradlew.bat backtestImpact -Pbaseline=6 -Pcandidate=scoring-candidate.yml -Pfrom=2026-06-01 -Pto=2026-06-30` |

**재계산 방식**

- watch_score = clamp(base_score × importance + pregame 보정). importance와 pregame의 contention은 경기 날짜 기준 `standings` 스냅샷으로 판정한다.
- pregame의 closeness는 `odds_snapshots`(PREGAME_FINAL 우선, 없으면 FIRST_SEEN)의 중앙값 implied probability로 재계산하고, starterMatchup은 라인업·시즌 스탯을 재로드하지 않으므로 `games.pregame_inputs`에 저장된 계산 결과를 재사용한다.
- `S3_BACKFILL`은 벽시계 시각이 없어 1번 절의 order 윈도 근사를 적용한다(최근 득점 15 plays 선형 감쇠, 리드 변경 25 plays 윈도. `pulse.backtest.*` 설정으로 조정 가능).
- 알림 빈도는 SurgeDetector 로직(임계·재무장·경기별 쿨다운·상승 트리거·전역 발화 한도)을 메모리로 재현한다. 전체 경기 사이클을 시각순으로 합쳐 `alert-global-window-minutes` 동안 `alert-global-limit`까지만 발화한다. 단 `S3_BACKFILL`은 벽시계 시각이 없으므로 시간 기반 쿨다운·상승 트리거·전역 발화 한도를 생략한다.

**리포트 항목**

| 항목 | 산출 |
|---|---|
| 순위 변화 | 경기 peak `watch_score` 랭킹의 기준↔후보 순위 상관(Spearman·Kendall)과 상위 N 진입·이탈 목록 |
| 알림 빈도 | 알림 임계(`alert-score`) 기준 하루 알림 수 분포(평균·최대)와 경기당 발화 수의 기준↔후보 변화 |
| 정렬 품질·예측력 | 순위 상관·AUC의 기준↔후보 델타 |
| 경보 플래그 | 알림 수 급증, 정렬 품질 급락 등 임계 초과 항목을 강조 표시 |

## 5. GitHub Actions 자동 리포트

`tune:` PR에서 `backend/src/main/resources/scoring.yml`만 변경하면 `backtest-impact` 워크플로가 자동 실행된다. GitHub-hosted 러너는 운영 RDS에 직접 접속하지 않는다. 변경 전·후 설정 파일을 임시 S3 경로에 올리고, SSM으로 VPC 내부 EC2의 고정 실행 스크립트를 호출한다. EC2는 현재 `main`에서 배포된 backend 이미지와 백테스트 전용 읽기 계정으로 최근 완료된 UTC 14일을 재계산한다.

PR head의 애플리케이션 코드는 운영 VPC에서 실행하지 않는다. 변경 전·후 `scoring.yml`만 배포된 백테스트 실행기에 입력하므로 PR 코드에 운영 DB 접근 권한이 전달되지 않는다. 외부 저장소에서 만든 PR과 draft PR은 실행하지 않는다.

자동 실행 전 다음 조건을 검사한다.

- PR 제목이 `tune:`으로 시작한다.
- 변경 파일은 `scoring.yml` 한 개뿐이다.
- 후보 `version`이 기준보다 증가했다.
- JDK 21로 백테스트 패키지 테스트를 통과했다.

결과 Markdown·JSON은 GitHub Actions 아티팩트로 30일 보관하고, 핵심 지표는 고정 PR 코멘트 한 건을 갱신해 게시한다. 생성된 결과 파일을 PR 브랜치에 커밋하지 않는다. 실행에 사용한 임시 S3 객체는 성공·실패와 관계없이 워크플로 마지막에 삭제한다.

경보 플래그는 초기 운영 검증 단계에서 경고로만 표시하며 PR 체크를 실패시키지 않는다. 운영 알림 빈도를 확인해 경보 기준을 확정한 뒤 required check 전환 여부를 결정한다. 실행 실패, 대상 경기 0건, 결과 파일 누락처럼 리포트 자체를 신뢰할 수 없는 경우에는 체크를 실패시킨다.
