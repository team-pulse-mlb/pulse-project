# 통합 테스트

## 1. 범위

- 기준 경로: `backend/src/test/java`
- Spring 테스트 슬라이스, 실제 PostgreSQL 컨테이너, Spring 컨텍스트, HTTP 테스트 경계를 사용하는 테스트를 통합 성격으로 분류한다.
- 현재 `@SpringBootTest`와 `WebTestClient`를 사용하는 테스트는 없다.
- 실제 RabbitMQ·Redis 컨테이너와 실제 balldontlie API를 호출하는 테스트는 없다.

## 2. 데이터베이스 통합

| 테스트 클래스 | 구성 | 통합 범위 | 핵심 시나리오 |
|---|---|---|---|
| `PlayerRegistrationWriterIntegrationTest` | `@DataJpaTest`, `@Testcontainers`, PostgreSQL 16, `@ServiceConnection`, 테스트 DB 교체 비활성화 | Writer → Spring 트랜잭션 → JPA repository → 실제 PostgreSQL | 서로 다른 트랜잭션의 동일 선수 동시 upsert가 PK 충돌 없이 행 하나만 남기는지 검증한다. |
| `GameEventRepositoryTest` | `@DataJpaTest`, H2, Flyway 비활성화 | Spring Data JPA repository → 생성된 테스트 스키마 | 보호 문구 재시도·재처리·하이라이트 조회 쿼리 조건과 정렬을 검증한다. |
| `GameFinalizationRepositoryTest` | `@DataJpaTest`, H2 | repository 갱신 쿼리 → 생성된 테스트 스키마 | 종료 처리 원자 갱신이 한 번만 성공하는지 검증한다. |
| `ScoreTaskOutboxRepositoryTest` | `@DataJpaTest`, H2 | outbox entity·repository → 생성된 테스트 스키마 | payload 영속·복원과 보존 기간·배치 제한 삭제 쿼리를 검증한다. |
| `LiveGameCycleWriterTest` | `@DataJpaTest`, H2, 일부 협력 객체 `@MockitoBean` | writer → 트랜잭션 → 게임·play repository | outbox insert 실패가 play 저장과 경기 커서 갱신까지 롤백하는지 검증한다. |
| `PollerGameWriterTest` | `@DataJpaTest`, H2, 대상 빈 `@Import` | writer → JPA repository → 게임·팀·선수·play 테이블 | 경기·팀·play·stub 선수와 커서·주자 상태 저장을 검증한다. |
| `PregameGameWriterTest` | `@DataJpaTest`, H2, 대상 빈 `@Import` | writer → JPA repository → 라인업·배당·순위·시즌 스탯 테이블 | 사전 경기 데이터의 insert·update·중복 억제와 조회 조건을 검증한다. |

### 2.1 테스트 DB 구성

- 공통 테스트 설정 `backend/src/test/resources/application.properties`에서 `spring.jpa.hibernate.ddl-auto=update`를 적용한다.
- H2 기반 `@DataJpaTest`는 임베디드 DB와 Hibernate 생성 스키마를 사용한다.
- 관련 테스트는 Flyway를 비활성화해 운영 마이그레이션과 분리한다.
- PostgreSQL 통합 테스트는 `postgres:16-alpine` 컨테이너 한 개를 클래스 동안 사용한다.
- PostgreSQL 동시성 테스트는 테스트 메서드의 기본 트랜잭션을 끄고 작업 스레드마다 독립 트랜잭션을 시작한다.

## 3. HTTP 경계 통합

| 테스트 클래스 | 구성 | 통합 범위 | 핵심 시나리오 |
|---|---|---|---|
| `NotificationControllerTest` | `MockMvcBuilders.standaloneSetup`, Mockito 서비스, Validator | HTTP 요청·JSON·Bean Validation → Controller | 알림 조회·읽음 처리 API의 상태 코드, 요청 검증과 응답 JSON을 검증한다. |
| `SecurityConfigTest` | `WebApplicationContextRunner`, 실제 Security filter chain, `MockMvc` | Spring 웹 컨텍스트 → 보안 필터 → 테스트 컨트롤러 | 비로그인 보호 API가 401을 반환하고 웹·비웹 빈 구성이 분리되는지 검증한다. |
| `BalldontlieClientTest` | `MockRestServiceServer` | HTTP client 요청 생성 → 스텁 응답 역직렬화·헤더 처리 | 다중 날짜 쿼리와 호출 예산 응답 헤더를 검증한다. |

- `NotificationControllerTest`는 전체 Spring Boot 컨텍스트를 띄우지 않는 독립형 MVC 테스트다.
- `BalldontlieClientTest`는 실제 외부 네트워크를 사용하지 않는다.
- 현재 `@WebMvcTest`, `@AutoConfigureMockMvc`, `WebTestClient` 기반 테스트는 없다.

## 4. Spring 컨텍스트 구성 통합

| 테스트 클래스 | 통합 범위 | 핵심 시나리오 |
|---|---|---|
| `AnonymousHomeRankingCacheWiringTest` | 소형 Annotation 기반 컨텍스트 → 캐시 빈 | 다중 생성자를 가진 캐시 빈이 실제 컨텍스트에서 생성되는지 검증한다. |
| `SseRoleGateTest` | 웹·비웹 컨텍스트 → 조건부 SSE 빈 | 웹 여부와 활성화 설정에 따른 빈 등록을 검증한다. |
| `SecurityConfigTest` | 웹·비웹 컨텍스트 → 보안 구성 빈 | 인증 관리자와 보안 필터 체인의 조건부 등록을 검증한다. |
| `NotificationMessageBeanConstructionTest` | 컨텍스트 러너 → 발행자·디스패처 빈 | Mockito RabbitTemplate을 주입한 생성자 구성의 유효성을 검증한다. |
| `NotificationOutboxSchedulerRoleGateTest` | 컨텍스트 러너 → 조건부 스케줄러 빈 | poller·game processor 역할과 설정에 따른 등록 여부를 검증한다. |
| `ScoreTaskOutboxSchedulerRoleGateTest` | 컨텍스트 러너 → 조건부 스케줄러 빈 | ScoreTask outbox 스케줄러의 역할 조건을 검증한다. |
| `SimulationBeanConstructionTest` | 컨텍스트 러너 → 시뮬레이션 빈 | 시뮬레이션 데이터 소스의 생성자 주입을 검증한다. |
| `EventCopyRetrySchedulerTest` | 컨텍스트 러너 → 재시도 스케줄러 빈 | 설정 조건과 대상 처리 구성의 등록 여부를 검증한다. |
| `GameProcessorRoleGateTest` | 컨텍스트 러너 → game processor 체인 빈 | 역할 활성화와 배치 프로파일에 따른 game processor 빈 구성을 검증한다. |

## 5. 외부 인프라별 검증 수준

| 인프라 | 현재 방식 | 실제 통합 여부 |
|---|---|---|
| PostgreSQL | Testcontainers `postgres:16-alpine` | `PlayerRegistrationWriterIntegrationTest`만 실제 통합 |
| H2 | `@DataJpaTest` 임베디드 DB | JPA 매핑·쿼리·트랜잭션 슬라이스 통합 |
| RabbitMQ | `RabbitTemplate` Mockito 대역, 큐 객체 설정 검증 | 실제 broker 미통합 |
| Redis | `StringRedisTemplate` Mockito 대역 | 실제 server·pub/sub 미통합 |
| balldontlie API | `MockRestServiceServer` | 실제 외부 API 미호출 |
| ai-service | client·결과 객체 Mockito 대역 | 실제 HTTP 서비스 미통합 |

## 6. 실행 전제

- JDK 21이 필요하다.
- 전체 테스트 실행 명령은 `backend\gradlew.bat test`다.
- Testcontainers 기반 테스트는 Docker daemon과 `postgres:16-alpine` 이미지 실행 권한이 필요하다.
- 로컬에 Docker가 없거나 daemon이 실행 중이지 않으면 `PlayerRegistrationWriterIntegrationTest`가 실패한다.
- H2·MockMvc·MockRestServiceServer·Mockito 기반 테스트는 Docker나 외부 네트워크가 필요하지 않다.

## 실행 결과

이 문서에서 통합 성격으로 분류한 테스트 클래스 18개 기준이다. Docker 실행 상태에서 Testcontainers 테스트를 포함해 실행했다.

| 총 테스트 수 | 성공 | 실패 | 스킵 | 실행일 |
|---|---|---|---|---|
| 52 | 52 | 0 | 0 | 2026-07-18 |
