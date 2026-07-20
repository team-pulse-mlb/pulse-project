# 기술 스택

PULSE는 3주 안에 통합 데모와 배포를 완성하되, 실제 라이브 경기가 도는 운영 상황을 가정한다. 요구사항에서 정당화되지 않는 기술은 넣지 않고, 같은 문제를 푸는 선택지 중 가장 가벼운 것을 고른다.

## 1. 스택 요약

| 분류 | 기술 | 선정 이유 |
|---|---|---|
| 백엔드 | Spring Boot 3.5.x, Java 21 | 팀 주력 스택. SSE 연결과 백그라운드 작업 처리에 적합 |
| 인증 | Spring Security + JWT (액세스+리프레시) | Stateless 인증. Access Token은 헤더로 전달하고, Refresh Token은 HttpOnly 쿠키로 전달한다. 리프레시 토큰 상태는 DB에 저장한다 |
| DB 접근 | Spring Data JPA + Flyway | 기본 프로파일은 `ddl-auto: none`, 로컬 개발은 `local` 프로파일에서만 `ddl-auto: update`, 배포 환경은 Flyway 마이그레이션만 사용. 베이스라인 V1은 DB 이전에 앞서 정리한다(세부 계획은 소유자가 별도 관리) |
| 메시지 큐 | RabbitMQ 3.x + Spring AMQP | 유실 불가한 계산 요청의 ack/재전달/DLQ 보장. 라이브 1회 계산 원칙상 계산 요청 유실 = 이력 영구 공백 |
| AI 서비스 | Python 3.12 + FastAPI + OpenAI API | 문구 생성·스포일러 검수 전용. 비동기+캐시로 응답 경로에서 분리 |
| API 문서 | springdoc-openapi + FastAPI OpenAPI | 실행 코드와 DTO에서 Swagger UI·OpenAPI JSON을 생성해 REST 계약 중복 방지 |
| 프론트엔드 | React + Vite | 경기 카드 반복 UI, SSE 커스텀 훅 |
| DB | PostgreSQL 16 (**AWS RDS**) | 라이브 1회 계산 결과는 재생성 불가 → 관리형 백업·시점 복구 필요 |
| 캐시/랭킹 | Redis 7 (EC2 Docker) | 랭킹·캐시는 PG에서 재계산 가능한 파생 데이터 → 관리형 불필요 |
| 실시간 | SSE (신호+재조회 방식) | 단방향 푸시만 필요. payload에 데이터를 싣지 않아 스포일러 필터링 지점을 REST 한 곳에 유지 |
| 관측성 | Micrometer + Prometheus + Grafana | 도메인 지표(API 성공률·429, 폴링 지연, 큐 적체·DLQ, SSE 연결 수) 대시보드 1장으로 범위 고정 |
| 배포 | AWS EC2 t3.medium 1대 + Docker Compose, RDS, S3+CloudFront(프론트) | 단일 호스트에 역할 분리 컨테이너. 분리 기준은 장애·부하 성격 |
| CI/CD | GitHub Actions | push → 빌드 → EC2 자동 배포 (기본 파이프라인만) |
| 외부 데이터 | balldontlie MLB API (GOAT 플랜, 600 req/min) | 필요한 데이터를 단일 API로 제공 |

## 2. 횡단 기준

문서 전체에서 반복 적용되는 기준 세 가지.

| 기준 | 적용 |
|---|---|
| **유실 불가 작업 = RabbitMQ, 유실 허용 신호 = Redis Pub/Sub** | 계산 요청·알림 이벤트는 브로커, 재조회 신호는 pub/sub |
| **재생성 불가 데이터 = PostgreSQL(RDS), 재계산 가능 데이터 = Redis** | 점수 이력·종료 경기 AI 문구·리프레시 토큰 상태는 PG 영속, 라이브 랭킹은 Redis |
| **판정은 데이터 옆, 전달은 사용자 옆** | 알림 판정은 scorer/poller, fan-out·SSE는 api |

## 3. 배포 토폴로지

EC2 t3.medium(4GB)+스왑 2GB 1대의 Docker Compose에 아래 컨테이너를 올리고, PostgreSQL만 RDS로 분리한다. 운영 Compose 정의는 [`infra/docker-compose.prod.yml`](../../infra/docker-compose.prod.yml)이며, `ai-service`도 여기에 포함된다.

| 컨테이너 | 내용 |
|---|---|
| `api` | REST·SSE·스포일러 보호 DTO·알림 전달 (Spring) |
| `poller` | 상태별 폴링·원본 저장·계산 요청 발행 (Spring) |
| `scorer` | 점수 계산·알림 판정·AI 트리거 (Spring) |
| `ai-service` | 문구 생성·검수 (FastAPI) |
| `redis` · `rabbitmq` · `prometheus` · `grafana` | 미들웨어·관측성 |

각 JVM은 `-Xmx` 명시, 컨테이너별 메모리 제한, Prometheus 보존 기간 7일 제한을 적용한다.

## 4. 도입하지 않기로 한 것

| 기술 | 기각 사유 |
|---|---|
| Kubernetes, MSA 세분화 | 단일 호스트 규모에 운영 비용만 추가 |
| Kafka | 초당 1건 미만 규모에 파티션 운영 부담 과잉 |
| Redis Streams (브로커 대용) | 재전달·DLQ·모니터링을 직접 구현해야 해 코드가 오히려 증가 |
| ElastiCache | 파생 데이터에 관리형 비용 불필요 |
| Web Push | 인앱 토스트·알림 센터로 충분, 배포 후 여유 시 |
| Loki·OpenTelemetry | 대시보드 중심 범위를 넘는 유지보수 부담. 경보는 OBSERVABILITY.md의 최소 기준만 둔다 |
| k6 부하 테스트 | 실사용자 트래픽이 없어 결과의 의미가 약함 |
