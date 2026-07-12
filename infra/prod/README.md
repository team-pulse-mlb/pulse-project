# 운영 인프라(AWS)

배포된 AWS 운영 환경의 리소스 구성, 운영 Docker Compose 구성, 배포 절차를 다룬다. 로컬 PostgreSQL·Redis 실행은 [`infra/local/README.md`](../local/README.md), S3 원본 수집기와 백필 도구는 [`raw-archive/README.md`](../../raw-archive/README.md)를 따른다.

| 구분 | 위치 | 이 문서 포함 여부 |
|---|---|---|
| 로컬 PostgreSQL·Redis | 개발 PC의 Docker Desktop | 제외 |
| 운영 애플리케이션·Redis·RabbitMQ·관측 스택 | AWS EC2 | 포함 |
| 운영 PostgreSQL | AWS RDS | 포함 |
| 원본 아카이브 | AWS S3·Lambda·EventBridge | 리소스 관계만 포함 |

## 1. AWS 리소스 구성

| 리소스 | 구성 | 비고 |
|---|---|---|
| EC2 | Ubuntu 24.04 LTS, t3.medium(4GB) + 스왑 2GB, gp3 30GiB, Elastic IP | 아래 운영 Compose 컨테이너 실행. 메모리 부족 확인 시 t3.large 상향 |
| RDS | PostgreSQL, Single-AZ, db.t3.micro, gp3 20GiB | 자동 확장 최대 100GiB, 삭제 보호 켬 |
| S3 | 원본 raw archive 버킷 | poller 원본 응답 저장, replay 입력, `deploy/` 프리픽스로 배포 산출물 전달 |
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

## 3. 운영 Docker Compose 구성

운영 Compose 정의는 [`infra/docker-compose.prod.yml`](../docker-compose.prod.yml)이다. EC2 런타임 디렉터리(`/home/ubuntu/pulse-runtime`)에 이 파일, [`infra/prod/`](./)의 설정 디렉터리(`prod/`), `.env`를 함께 두고 실행한다.

### 3.1 컨테이너 목록

| 서비스 | 이미지 | 역할 |
|---|---|---|
| `pulse-api` | backend 공용 이미지 | REST API·SSE·알림 fan-out, Flyway 마이그레이션 실행 |
| `pulse-poller` | backend 공용 이미지 | 외부 API 폴링, 원본 저장, `ScoreTask`·`GAME_START` 발행 |
| `pulse-scorer` | backend 공용 이미지 | `score.tasks` 소비, 점수 계산, 랭킹 갱신, `SURGE` 발행 |
| `redis` | `redis:7-alpine` | 랭킹 ZSET, 재조회 신호 Pub/Sub |
| `rabbitmq` | `rabbitmq:3.13-management` | `score.tasks`·`notify.events`, DLQ |
| `prometheus` | `prom/prometheus` | backend 관리 포트 scrape |
| `grafana` | `grafana/grafana` | 대시보드(데이터소스 프로비저닝까지 소유자 준비, 대시보드 제작은 운영·관측 담당) |
| `ai-service` | (미편입) | 담당자 Dockerfile·기동 방식 확정 시 편입 |

### 3.2 backend 역할 분리

세 backend 서비스는 같은 이미지를 역할 게이트 환경 변수만 바꿔 재사용한다.

| 서비스 | `PULSE_POLLER_ENABLED` | `PULSE_SCORER_ENABLED` | `PULSE_SSE_ENABLED` | Flyway | 힙 상한 |
|---|---|---|---|---|---|
| `pulse-api` | false | false | true | 실행(prod 프로파일) | 512m |
| `pulse-poller` | true | false | false | `SPRING_FLYWAY_ENABLED=false` | 256m |
| `pulse-scorer` | false | true | false | `SPRING_FLYWAY_ENABLED=false` | 384m |

- Flyway는 `pulse-api` 1곳에서만 실행해 동시 기동 시 마이그레이션 경합을 막는다. `pulse-poller`·`pulse-scorer`는 `pulse-api` 헬스 통과(=마이그레이션 완료) 후 기동한다.
- 힙 상한은 t3.medium(4GB) 기준 초기값이며 운영 측정 후 조정한다.

### 3.3 관리 포트와 포트 공개 정책

- 세 backend 역할 모두 actuator를 관리 포트 8081로 분리한다(`MANAGEMENT_SERVER_PORT=8081`). 헬스체크와 Prometheus scrape는 compose 내부 네트워크에서만 접근하고 호스트에 publish하지 않는다.
- 호스트에 공개하는 포트는 `pulse-api`의 8080뿐이다. RabbitMQ management(15672)·Prometheus(9090)·Grafana(3000)는 `127.0.0.1` 바인딩으로 두고 SSH/SSM 터널로만 접근한다. 외부 개방이 필요해지면 운영·관측 담당과 보안그룹 변경을 합의한다.

### 3.4 관측 스택

- Prometheus 설정: [`prod/prometheus/prometheus.yml`](./prometheus/prometheus.yml) — 세 backend의 `/actuator/prometheus`를 role 라벨로 scrape한다. micrometer 레지스트리 노출 전까지는 backend job 수집이 비어 있어도 무방하다.
- Grafana 데이터소스: [`prod/grafana/provisioning/datasources/prometheus.yml`](./grafana/provisioning/datasources/prometheus.yml)로 자동 프로비저닝한다. 대시보드 제작은 운영·관측 담당 영역이다.

## 4. 배포 절차 (SSM)

배포·운영 명령은 SSH 대신 SSM(`AWS-RunShellScript`)으로 실행한다.

1. 로컬에서 JDK 21로 `backend\gradlew.bat build` 실행 후 부팅 jar를 S3 `deploy/` 프리픽스에 업로드한다(EC2 인스턴스 프로파일은 이 프리픽스 읽기 권한만 가진다).
2. compose 파일이나 `prod/` 설정이 바뀐 경우 같은 방식으로 S3에 올려 EC2 런타임 디렉터리에 동기화한다.
3. SSM으로 EC2에서 실행: jar와 [`backend/Dockerfile`](../../backend/Dockerfile)을 내려받아 `docker build -t pulse-app:<날짜태그>` → `.env`의 `PULSE_APP_IMAGE`를 새 태그로 갱신 → `docker compose -f docker-compose.prod.yml up -d`.
4. 확인: `docker compose ps`에서 전 서비스 healthy, `pulse-api` 로그에서 Flyway 적용 결과 확인. 저장소 마이그레이션 파일과 운영 이력 체크섬이 어긋나면 기동이 실패하므로 로그를 먼저 본다.
5. 롤백: `.env`의 `PULSE_APP_IMAGE`를 직전 태그로 되돌리고 다시 `up -d`.

## 5. 일회성 배치 (replay·rescore)

배치 서비스는 compose `batch` 프로파일에만 속해 상시 기동되지 않으며, `run --rm`으로만 실행한다. 파라미터는 `JAVA_OPTS`의 `-D` 시스템 프로퍼티로 전달한다(이미지 ENTRYPOINT 구조상 컨테이너 인자로는 전달되지 않는다).

```bash
# replay: 특정 날짜·경기 원본을 S3에서 다시 적재
docker compose -f docker-compose.prod.yml run --rm \
  -e JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -Xss512k \
    -Dpulse.replay.date=<YYYY-MM-DD> -Dpulse.replay.game-id=<game_id>" \
  pulse-replay

# rescore: scorer 파생 데이터 재산정 (완료 시 자체 종료, exit code로 판정)
docker compose -f docker-compose.prod.yml run --rm \
  -e JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -Xss512k \
    -Dpulse.rescore.game-ids=<game_id,game_id,...>" \
  pulse-rescore
```

- `pulse.replay.date`는 필수이며 미지정 시 즉시 실패한다.
- replay 러너는 완료 후 컨텍스트를 닫지 않으므로 로그에서 완료 메시지를 확인한 뒤 수동으로 중지한다.
- 배치는 상시 컨테이너와 메모리를 공유하므로 트래픽이 낮은 시간에 실행한다.

## 6. 시크릿·환경 변수 관리

운영 시크릿은 GitHub Actions Secrets, AWS Secrets Manager, EC2 `.env` 파일로 분리해 관리한다. `.env`는 compose 파일과 같은 디렉터리에 두며, backend 서비스의 `env_file`과 compose 변수 치환이 같은 파일을 사용한다. 데이터베이스 마스터 자격 증명은 RDS 생성 시 AWS Secrets Manager가 관리하며, 애플리케이션은 필요한 최소 권한으로 조회한다.

환경 변수 목록은 이름만 문서화하고 실제 값은 저장소에 기록하지 않는다.

| 구분 | 변수명 |
|---|---|
| 배포 | `PULSE_APP_IMAGE` |
| 외부 API | `BDL_API_KEY`, `OPENAI_API_KEY` |
| S3 리플레이 | `PULSE_REPLAY_S3_BUCKET`, `PULSE_REPLAY_MAX_OBJECTS_PER_PREFIX` |
| 공통 | `AWS_REGION` |
| PostgreSQL | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| RabbitMQ | `RABBITMQ_USER`, `RABBITMQ_PASSWORD` |
| Grafana | `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` |
| 인증 | `JWT_SECRET` |
| ai-service | `AI_SERVICE_URL` |

`REDIS_HOST`·`RABBITMQ_HOST` 등 compose 내부 네트워크 호스트명은 `.env`가 아니라 compose 정의에서 서비스명으로 고정한다. GitHub Actions Secrets에는 배포와 연결 정보에 필요한 값만 등록한다. balldontlie API 키와 런타임 `.env` 값은 EC2에서 관리한다.

## 7. 네트워크

RDS는 퍼블릭 액세스를 허용하지 않는다. RDS 생성 시 AWS 콘솔의 EC2 연결 방식을 사용해 보안그룹을 자동 구성하며, EC2에서 RDS 5432 포트로 들어가는 연결만 허용한다.

로컬에서 운영 DB에 접속해야 하는 경우 RDS를 직접 공개하지 않고 EC2 SSH 터널을 경유한다. 운영 보안그룹에는 로컬 IP에서 RDS로 직접 접근하는 규칙을 추가하지 않는다.

api 컨테이너의 CORS는 프론트 배포 도메인과 로컬 개발 origin만 허용한다.

## 8. 로컬 개발 환경과 운영의 관계

로컬 개발은 `infra/local/docker-compose.yml`로 PostgreSQL·Redis 컨테이너를 실행하고 S3 raw archive 리플레이로 데이터를 채운다. 운영은 EC2에서 [`infra/docker-compose.prod.yml`](../docker-compose.prod.yml)로 애플리케이션·미들웨어·관측 컨테이너를 실행하고 PostgreSQL만 RDS로 분리한다.

## 9. IAM 원칙

EC2 인스턴스 프로파일은 `pulse-app-role`을 사용한다. 이 역할에는 S3 원본 버킷 읽기 권한과 RDS 시크릿 조회 권한만 최소 범위로 부여한다.

EC2에서 AWS API를 호출하기 위해 별도 액세스 키를 발급하지 않는다. 애플리케이션과 운영 스크립트는 인스턴스 프로파일 자격 증명을 사용한다.

## 10. AWS Management Console 확인 경로

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

## 11. 변경 경계

- 로컬 개발자는 `infra/local/docker-compose.yml`만 사용한다.
- 원본 수집 담당자는 S3·Lambda·EventBridge·CloudWatch의 수집 상태를 확인한다.
- 운영 배포, 보안그룹, 백업·복구는 운영·관측 담당자 영역이다.
- 운영 시크릿 관리와 변경은 소유자(예은) 영역이다.
- 실제 엔드포인트, 계정 ID, IP, 시크릿 값은 저장소 문서에 기록하지 않는다.
