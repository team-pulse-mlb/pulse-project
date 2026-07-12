# PULSE 원본 데이터 아카이브

balldontlie MLB API 원본 응답을 S3에 gzip JSON으로 저장하는 도구 모음이다.

## 폴더 구조

```text
raw-archive/
├── live-collector/     Lambda 라이브 수집기
├── backfill/           과거 시즌 일회성 적재
├── deploy/             AWS 수집기 배포
└── analysis/           수집 데이터 분석
```

## 목차

| 목차 | 설명 |
|---|---|
| [수집 상태 확인](#수집-상태-확인) | S3 객체와 Lambda 로그 확인 |
| [과거 시즌 적재](#과거-시즌-적재) | 지정 시즌을 S3에 백필 |
| [수집기 배포](#수집기-배포) | Lambda·S3·EventBridge 갱신 |
| [저장 구조](#저장-구조) | S3 객체 경로 |

배포와 백필 명령은 실제 AWS 리소스와 운영 버킷을 변경한다.

## 수집 상태 확인

AWS 콘솔에서 리전을 `ap-northeast-2`로 선택한다.

| 확인 대상 | AWS 콘솔 위치 |
|---|---|
| 최근 원본 | S3 → Buckets → 원본 버킷 → `raw/` |
| 수집 상태 | S3 → 원본 버킷 → `state/collector_state.json` |
| 실행 로그 | CloudWatch → Log groups → `/aws/lambda/pulse-collector` |
| 실행 일정 | EventBridge → Rules → `pulse-collector-every-minute` |

Git Bash에서 확인할 수도 있다.

```bash
aws s3 ls s3://pulse-raw-<account-id>/raw/ --recursive | tail -n 20
aws logs tail /aws/lambda/pulse-collector --since 10m
```

## 과거 시즌 적재

Python, `boto3`, AWS 자격 증명, balldontlie API 키가 필요하다.

```bash
python -m pip install boto3
export BDL_API_KEY='<API_KEY>'
python raw-archive/backfill/backfill.py \
  --bucket pulse-raw-<account-id> \
  --seasons 2023 2024 2025
```

- 기본 호출 상한: 분당 300회
- 이미 적재된 경기는 재실행 시 건너뜀
- 과거 데이터는 실제 관측 시각이 없어 `backfilled=true`로 저장

## 수집기 배포

AWS CLI와 배포 권한이 필요하다. 저장소 루트의 Git Bash에서 실행한다.

```bash
powershell.exe -File ./raw-archive/deploy/deploy-collector.ps1 \
  -ApiKey '<balldontlie API key>'
```

배포 후 S3 객체와 CloudWatch 로그를 확인한다.

## 저장 구조

```text
raw/games/dt=YYYY-MM-DD/games_HHMMSSZ.json.gz
raw/plays/game_id=<id>/plays_YYYY-MM-DD_HHMMSSZ_c<cursor>.json.gz
raw/plate_appearances/game_id=<id>/pa_YYYY-MM-DD_HHMMSSZ.json.gz
raw/backfill/plays/game_id=<id>/plays_p<n>.json.gz
raw/historical/season=YYYY/games/game_id=<id>.json.gz
state/collector_state.json
```

원본 객체는 `observed_at`, `endpoint`, `params`, `response`를 포함한다. gzip 객체를 로컬에서 읽을 때는 압축을 해제해야 한다.
