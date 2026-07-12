# 운영 인프라(AWS)

배포된 AWS 운영 환경의 리소스 구성과 환경 설정을 다룬다. 로컬 PostgreSQL·Redis 실행은 [`infra/local/README.md`](../local/README.md), S3 원본 수집기와 백필 도구는 [`raw-archive/README.md`](../../raw-archive/README.md)를 따른다.

| 구분 | 위치 | 이 문서 포함 여부 |
|---|---|---|
| 로컬 PostgreSQL·Redis | 개발 PC의 Docker Desktop | 제외 |
| 운영 애플리케이션·Redis·RabbitMQ | AWS EC2 | 포함 |
| 운영 PostgreSQL | AWS RDS | 포함 |
| 원본 아카이브 | AWS S3·Lambda·EventBridge | 리소스 관계만 포함 |

## 1. AWS 리소스 구성

| 리소스 | 구성 | 비고 |
|---|---|---|
| EC2 | Ubuntu 24.04 LTS, t3.large, gp3 30GiB, Elastic IP | api·poller·scorer·ai-service·redis·rabbitmq·prometheus·grafana 컨테이너 실행 |
| RDS | PostgreSQL, Single-AZ, db.t3.micro, gp3 20GiB | 자동 확장 최대 100GiB, 삭제 보호 켬 |
| S3 | 원본 raw archive 버킷 | poller 원본 응답 저장 및 replay 입력 |
| 리전 | ap-northeast-2 | AWS 리소스 공통 리전 |

EC2와 RDS는 같은 기본 VPC에 배치한다. 실제 엔드포인트·리소스 ID·IP 값은 소유자 로컬 문서와 배포 환경 변수로만 관리한다.

## 2. RDS 사양

RDS는 PostgreSQL용 관리형 데이터베이스로 구성한다.

- 배포 형태: Single-AZ
- 인스턴스 클래스: db.t3.micro
- 스토리지: gp3 20GiB, 자동 확장 최대 100GiB
- 암호화: 기본 KMS 키 사용
- 백업: 자동 백업 7일
- 복제: 교차 리전 복제 없음
- 삭제 보호: 켬
- 모니터링: Database Insights Standard만 사용
- 마스터 자격 증명: AWS Secrets Manager 관리

초기 부하는 poller·scorer 중심이며 트래픽 규모가 작으므로 최소 클래스에서 시작한다. 스토리지는 증가만 가능하므로 초기값은 20GiB로 두고 자동 확장 상한을 100GiB로 제한한다.

## 3. 시크릿·환경 변수 관리

운영 시크릿은 GitHub Actions Secrets, AWS Secrets Manager, EC2 `.env` 파일로 분리해 관리한다. 데이터베이스 마스터 자격 증명은 RDS 생성 시 AWS Secrets Manager가 관리하며, 애플리케이션은 필요한 최소 권한으로 조회한다.

환경 변수 목록은 이름만 문서화하고 실제 값은 저장소에 기록하지 않는다.

| 구분 | 변수명 |
|---|---|
| 외부 API | `BDL_API_KEY`, `OPENAI_API_KEY` |
| S3 리플레이 | `PULSE_REPLAY_S3_BUCKET`, `PULSE_REPLAY_GAME_ID`, `PULSE_REPLAY_DATE`, `PULSE_REPLAY_MAX_OBJECTS_PER_PREFIX` |
| 공통 | `AWS_REGION` |
| PostgreSQL | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Redis | `REDIS_HOST`, `REDIS_PORT` |
| 인증 | `JWT_SECRET` |
| ai-service | `AI_SERVICE_URL` |

GitHub Actions Secrets에는 배포와 연결 정보에 필요한 값만 등록한다. balldontlie API 키와 런타임 `.env` 값은 EC2에서 관리한다.

## 4. 네트워크

RDS는 퍼블릭 액세스를 허용하지 않는다. RDS 생성 시 AWS 콘솔의 EC2 연결 방식을 사용해 보안그룹을 자동 구성하며, EC2에서 RDS 5432 포트로 들어가는 연결만 허용한다.

로컬에서 운영 DB에 접속해야 하는 경우 RDS를 직접 공개하지 않고 EC2 SSH 터널을 경유한다. 운영 보안그룹에는 로컬 IP에서 RDS로 직접 접근하는 규칙을 추가하지 않는다.

api 컨테이너의 CORS는 프론트 배포 도메인(Vercel)과 로컬 개발 origin만 허용한다.

## 5. 로컬 개발 환경과 운영의 관계

로컬 개발은 `infra/local/docker-compose.yml`로 PostgreSQL·Redis 컨테이너를 실행하고 S3 raw archive 리플레이로 데이터를 채운다. 운영은 EC2 Docker Compose로 애플리케이션 관련 컨테이너를 실행하고 PostgreSQL만 RDS로 분리한다.

`infra/local/`에는 로컬 개발용 Compose 설정을 둔다. 운영 배포용 Compose 설정은 현재 별도 배포 절차에서 관리하며, 배포 안정화 후 이 폴더(`infra/prod/`)에 코드화한다.

## 6. IAM 원칙

EC2 인스턴스 프로파일은 `pulse-app-role`을 사용한다. 이 역할에는 S3 원본 버킷 읽기 권한과 RDS 시크릿 조회 권한만 최소 범위로 부여한다.

EC2에서 AWS API를 호출하기 위해 별도 액세스 키를 발급하지 않는다. 애플리케이션과 운영 스크립트는 인스턴스 프로파일 자격 증명을 사용한다.

## 7. AWS Management Console 확인 경로

운영 상태 확인은 AWS Management Console을 우선 사용한다. 리전은 항상 `ap-northeast-2`인지 먼저 확인한다.

| 확인 대상 | 콘솔 경로 | 확인 항목 |
|---|---|---|
| EC2 | **EC2 → Instances** | 인스턴스 상태, 상태 검사, 연결된 보안그룹 |
| RDS | **RDS → Databases** | DB 상태, 엔드포인트, 백업, 삭제 보호 |
| S3 | **S3 → Buckets → `pulse-raw-<account-id>`** | `raw/`·`state/` 객체와 최종 수정 시각 |
| Lambda 수집기 | **Lambda → Functions → `pulse-collector`** | 마지막 수정, 환경 변수 이름, 모니터링 링크 |
| 수집 일정 | **EventBridge → Rules → `pulse-collector-every-minute`** | 규칙 상태와 대상 Lambda |
| 수집 로그 | **CloudWatch → Log groups → `/aws/lambda/pulse-collector`** | 최근 실행 결과와 오류 |
| 시크릿 | **Secrets Manager → Secrets** | 시크릿 존재 여부와 접근 권한만 확인 |

콘솔 화면에서 시크릿 값을 복사해 문서·이슈·채팅에 남기지 않는다. 리소스 중지, 보안그룹 변경, RDS 수정·삭제는 운영·관측 담당자와 합의한 뒤 수행한다.

## 8. 변경 경계

- 로컬 개발자는 `infra/local/docker-compose.yml`만 사용한다.
- 원본 수집 담당자는 S3·Lambda·EventBridge·CloudWatch의 수집 상태를 확인한다.
- 운영 배포, 보안그룹, 백업·복구는 운영·관측 담당자 영역이다.
- 운영 시크릿 관리와 변경은 소유자(예은) 영역이다.
- 실제 엔드포인트, 계정 ID, IP, 시크릿 값은 저장소 문서에 기록하지 않는다.
