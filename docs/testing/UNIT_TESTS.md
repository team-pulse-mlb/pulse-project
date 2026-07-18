# 단위 테스트

## 1. 범위

- 기준 경로: `backend/src/test/java`
- 기준 시점의 `*Test.java` 클래스: 112개
- 서비스·정책·계산·직렬화·설정 검증을 단위 테스트로 분류한다.
- Spring 컨텍스트, JPA 슬라이스, HTTP 테스트 경계를 사용하는 클래스도 전체 현황 확인을 위해 아래 목록에 포함하고, 통합 범위는 [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md)에서 별도로 구분한다.
- `TestScoringProperties.java`는 테스트 보조 클래스이므로 테스트 클래스 수와 목록에서 제외한다.

## 2. 사용 도구

| 도구 | 용도 | 근거 |
|---|---|---|
| JUnit 5 | 테스트 실행, 생명주기, 표시 이름 | `useJUnitPlatform()`, `org.junit.jupiter` 사용 |
| AssertJ | 값·컬렉션·예외 검증 | `assertThat`, `assertThatThrownBy`, `SoftAssertions` 사용 |
| Mockito | 협력 객체 대역, 호출·순서·인자 검증 | `MockitoExtension`, `mock`, `verify`, `ArgumentCaptor` 사용 |
| Spring Boot Test | 컨텍스트 러너, JPA 슬라이스, 테스트 빈 대체 | `spring-boot-starter-test` 사용 |
| MockMvc | 컨트롤러·보안 필터 HTTP 계약 검증 | 독립형 또는 웹 컨텍스트 기반 `MockMvc` 사용 |
| MockRestServiceServer | balldontlie HTTP 요청·응답 스텁 | `BalldontlieClientTest`에서 사용 |
| Testcontainers | 실제 PostgreSQL 동시성 검증 | `PlayerRegistrationWriterIntegrationTest`에서 사용 |
| H2 | JPA 슬라이스의 임베디드 DB | `testRuntimeOnly` 의존성과 테스트 설정 사용 |

## 3. 모듈별 테스트 클래스

### 3.1 ai

| 테스트 클래스 | 핵심 검증 시나리오 |
|---|---|
| `AiCopyResponseTest` | AI 응답의 근거 필드 역직렬화와 누락·null 목록 정규화·방어적 복사를 검증한다. |
| `AiFinalHeadlineContextMapperTest` | 공개 종료 헤드라인 컨텍스트를 보호 필드 없이 결과 기반 HTTP 요청으로 변환한다. |
| `AiFinalHeadlineCopyClientTest` | 종료 헤드라인 응답의 근거가 공통 결과 객체까지 전달되는지 검증한다. |
| `AiFinalHeadlineRequestTest` | 보호·공개 모드별 최종 점수·승자·v2 컨텍스트 직렬화 계약을 검증한다. |

### 3.2 api

| 테스트 클래스 | 핵심 검증 시나리오 |
|---|---|
| `AiCopyContextServiceTest` | 이벤트·헤드라인 컨텍스트의 쿼터, 화이트리스트, 보호 필드 차단과 사실 계산을 검증한다. |
| `GameDetailSerializationGuardTest` | 예정·진행·종료와 보호·공개 조합별 상세 DTO 노출 필드 계약을 검증한다. |
| `GameEventQueryServiceTest` | 보호 모드 하이라이트 조회, 공개 모드 차단, 잘못된 모드 폴백과 경기 없음 응답을 검증한다. |
| `GameQueryServiceTest` | 상태·모드별 상세 조립, 라인업·구장·헤드라인 처리와 보호 모드 폴백을 검증한다. |
| `GameRecentPlayQueryServiceTest` | 보호 모드 play 차단과 공개 모드 최근 타석·저장 번역 조회를 검증한다. |
| `AnonymousHomeRankingCacheTest` | 익명 홈 랭킹 응답을 TTL 동안 재사용하는지 검증한다. |
| `AnonymousHomeRankingCacheWiringTest` | 익명 랭킹 캐시가 Spring 컨텍스트에서 정상 생성되는지 검증한다. |
| `HomeQueryServiceTest` | 홈 슬롯 채우기·최대 5장·개인화·오래된 랭킹 제거와 일괄 조회를 검증한다. |
| `HomeRankingControllerTest` | 익명 인증 객체를 비로그인 요청으로 처리하는지 검증한다. |
| `NotificationControllerTest` | 알림 목록·선택 읽음·전체 읽음 API와 잘못된 요청 응답을 검증한다. |
| `NotificationEventListenerTest` | 유효한 알림 이벤트를 fan-out 서비스로 전달하고 null 이벤트를 건너뛴다. |
| `NotificationFanOutServiceTest` | 관심 팀 경기 시작·급상승 알림 대상 선정, 저장과 중복 멱등성을 검증한다. |
| `NotificationServiceTest` | 최신 알림 조회와 선택·전체 읽음 처리, ID 정규화를 검증한다. |
| `RedisSignalRelayTest` | Redis 랭킹·경기 신호를 SSE로 중계하고 시퀀스·비정상 채널을 처리한다. |
| `SseControllerTest` | 일회용 토큰 발급과 익명·인증 구독, 잘못된 토큰 거절을 검증한다. |
| `SseEmitterRegistryTest` | 연결 등록·할당량·방송·사용자 전송·실패 연결 정리와 느린 연결 격리를 검증한다. |
| `SsePropertiesTest` | SSE 실행기와 연결 할당량 기본값을 검증한다. |
| `SseRoleGateTest` | 웹 여부와 활성화 설정에 따른 SSE 빈 등록 조건을 검증한다. |
| `SseTokenServiceTest` | Redis 기반 SSE 토큰의 60초 TTL, 일회성 소비와 예외 처리를 검증한다. |
| `PlayerRegistrationWriterIntegrationTest` | 실제 PostgreSQL에서 동일 선수 동시 upsert가 행 하나만 남기는지 검증한다. |
| `PlayerRegistrationWriterTest` | 외부 선수 정보 upsert 시 팀 매핑과 결측 필드 보존 규칙을 검증한다. |
| `PlayerSearchMemoryCacheTest` | 동일 검색어 동시 로드 단일화, 만료 제거와 최대 개수 축출을 검증한다. |
| `PlayerSearchServiceTest` | 외부 검색 우선·로컬 보완·장애 폴백·캐시·짧은 검색어 차단을 검증한다. |
| `UserPreferenceServiceTest` | 관심 팀·선수 갱신 순서, 검색 캐시·외부 조회와 한도·장애 처리를 검증한다. |

### 3.3 common

| 테스트 클래스 | 핵심 검증 시나리오 |
|---|---|
| `AiContextHashCalculatorTest` | AI 컨텍스트와 play 번역 해시의 결정성, 모드·입력 변경 민감도를 검증한다. |
| `AiCopyResultTest` | AI 문구 결과의 근거 목록 기본값·null 정규화·방어적 복사를 검증한다. |
| `BalldontlieClientTest` | 다중 날짜 HTTP 요청과 호출 예산 헤더 기록을 스텁 서버로 검증한다. |
| `BdlDtosTest` | 외부 경기 JSON의 최상위 팀 이름 역직렬화를 검증한다. |
| `BdlPropertiesTest` | 외부 API 연결·읽기 타임아웃 기본값과 사용자 설정 유지 여부를 검증한다. |
| `RabbitMqConfigTest` | quorum 큐·DLQ·delivery limit·prefetch·재큐잉 설정을 검증한다. |
| `SecurityConfigTest` | 웹·비웹 보안 빈 구성과 비로그인 선수 검색 API의 401 응답을 검증한다. |
| `NotificationEventSerializationTest` | 알림 이벤트의 완성 메시지와 최신 태그 직렬화 계약을 검증한다. |
| `NotificationMessageBeanConstructionTest` | 알림 발행자와 디스패처의 생성자 주입 구성을 검증한다. |
| `NotificationOutboxDispatcherTest` | RabbitMQ 발행 성공·실패 시 outbox 상태와 재발행 대상을 검증한다. |
| `NotificationOutboxSchedulerRoleGateTest` | poller·scorer 역할과 명시 설정에 따른 알림 outbox 스케줄러 등록을 검증한다. |
| `PublisherTest` | ScoreTask·알림 이벤트의 outbox 선저장, 동시 insert 재사용과 발행 실패 격리를 검증한다. |
| `ScoreTaskOutboxCleanupSchedulerTest` | 보존 기간이 지난 발행 완료 행을 제한된 배치로 삭제하는지 검증한다. |
| `ScoreTaskOutboxDispatcherTest` | broker ack·nack·timeout·재시작 상황의 ScoreTask 발행 상태와 복구를 검증한다. |
| `ScoreTaskOutboxSchedulerRoleGateTest` | poller·scorer 역할과 명시 설정에 따른 ScoreTask outbox 스케줄러 등록을 검증한다. |
| `ScoreTaskSerializationTest` | 상황·PA·경기 스냅샷을 포함한 ScoreTask의 신규·구버전 JSON 호환성을 검증한다. |
| `PulseMetricsTest` | 태그가 있는 카운터 증가와 작업 시간 기록을 검증한다. |
| `AfterCommitExecutorTest` | 트랜잭션 유무에 따른 커밋 후 실행과 즉시 실행을 검증한다. |
| `JdbcUserPreferenceReaderTest` | 정규화한 이메일로 관심 팀·선수를 조회하고 빈 이메일을 처리한다. |

### 3.4 domain

| 테스트 클래스 | 핵심 검증 시나리오 |
|---|---|
| `GameEventLabelPolicyTest` | 보호·공개 이벤트 라벨과 미지 등급·유형 차단 규칙을 검증한다. |
| `GameEventRepositoryTest` | 보호 문구 재시도·재처리·하이라이트 조회 조건과 정렬을 검증한다. |
| `GameFinalizationRepositoryTest` | 종료 처리 권한을 games 행 원자 갱신으로 한 번만 획득하는지 검증한다. |
| `ScoreTaskOutboxRepositoryTest` | ScoreTask payload 영속·복원과 발행 완료 행의 보존 기간·배치 삭제를 검증한다. |

### 3.5 poller

| 테스트 클래스 | 핵심 검증 시나리오 |
|---|---|
| `GameLifecycleStateMachineTest` | 외부 상태와 시작 시각에 따른 LIVE·PREGAME·중단·종료 전이를 검증한다. |
| `GameUpsertResultTest` | LIVE·terminal 진입 판정을 실제 lifecycle 전이에만 한정하는지 검증한다. |
| `LiveGameCycleWriterTest` | outbox 저장 실패 시 play와 경기 커서가 함께 롤백되는지 검증한다. |
| `OperationalPollerTest` | 라이브·종료 작업 발행, 하트비트, 저소음 폴링, 복구 probe와 오류 격리를 검증한다. |
| `PaRawArchiveUploaderTest` | PA 원본 키 구조, 동일 응답 생략과 bucket 미설정 처리를 검증한다. |
| `PlayerEnrichmentPollerTest` | stub 선수 청크 보강, 빈 대상, 레이트리밋 백오프와 예외 전파를 검증한다. |
| `PlayerEnrichmentWriterTest` | 선수 상세 보강 시 결측값·팀 매핑·기존 값 보존 규칙을 검증한다. |
| `PlayerStubWriterTest` | 이미 존재하는 선수에 stub을 중복 삽입하지 않는지 검증한다. |
| `PollerBackoffTest` | 실패 시 retry-after까지 차단하고 성공 시 백오프를 해제한다. |
| `PollerExceptionClassifierTest` | 연결 시간 초과 등 백오프 대상 예외 분류를 검증한다. |
| `PollerGameWriterRunnerStateTest` | 주자 상태가 변경된 play만 저장하는지 검증한다. |
| `PollerGameWriterTest` | 경기·팀·play·선수 stub 영속, 중복 방지, 커서와 주자 상태 갱신을 검증한다. |
| `PollerRateLimiterTest` | 초당 호출 상한과 대기 중 인터럽트 처리를 검증한다. |
| `PollerRunnerStateMatcherTest` | 반복 타자의 PA 순서 매핑과 batter 결측 play 건너뛰기를 검증한다. |
| `PregameGameWriterTest` | 라인업·배당·순위·선수 시즌 스탯의 스냅샷 저장과 변경 판정을 검증한다. |
| `PregamePollerTest` | 사전 경기 상태별 수집 주기, 배당 대상과 PREGAME 작업 1회 발행을 검증한다. |
| `ScoreTaskFactoryTest` | 사전·라이브·종료 ScoreTask에 경기 스냅샷을 포함하는지 검증한다. |
| `SimulationBaseballDataSourceTest` | 배속·오프셋·커서·다중 경기별 시뮬레이션 데이터 공개를 검증한다. |
| `SimulationBeanConstructionTest` | 시뮬레이션 데이터 소스의 생성자 자동 주입 구성을 검증한다. |
| `SimulationPropertiesTest` | 배속·다중 경기 설정 정규화와 잘못된 ID·누락값 검증을 확인한다. |

### 3.6 ranking

| 테스트 클래스 | 핵심 검증 시나리오 |
|---|---|
| `PersonalizationCalculatorTest` | 관심 팀·선수 가산을 항목 수와 무관하게 각각 한 번만 적용하는지 검증한다. |
| `RankingServiceTest` | 현재 경기 대비 점수 차와 절대 최저 점수에 따른 전환 후보 선정을 검증한다. |

### 3.7 replay

| 테스트 클래스 | 핵심 검증 시나리오 |
|---|---|
| `S3RawArchiveClientTest` | S3 재생 날짜의 null·공백 입력을 거절하는지 검증한다. |
| `S3ReplayDataLoaderTest` | 백필·라이브 아카이브 play의 출처와 선수 FK 보장 저장을 검증한다. |
| `AlertSimulatorTest` | 알림 진입·해제·재무장·쿨다운과 전역 윈도 한도를 검증한다. |
| `BacktestImpactRunnerTest` | 대상 경기가 없는 백테스트 실행을 거절하는지 검증한다. |
| `GameReplayEngineTest` | 백필 득점·리드 변경의 play 윈도 감쇠 근사를 검증한다. |
| `ImpactReportGeneratorTest` | 기준 알림 유무에 따른 절대·비율 경보 한도 적용을 검증한다. |
| `MetricsCalculatorTest` | 동점 순위 상관, AUC와 예측 horizon 라벨 산출을 검증한다. |
| `ScoringConstantsLoaderTest` | 버전 스냅샷·임시 YAML 로드와 버전 불일치 실패를 검증한다. |
| `MigrationJsonMapperTest` | 이전 JSON의 결측 필드와 숫자·좌표를 방어적으로 매핑한다. |
| `OddsSnapshotSelectorTest` | 경기 시작 전 FIRST_SEEN·PREGAME_FINAL 관측 선택을 검증한다. |
| `PlateAppearancePlayMatcherTest` | 반복 타자·결측 batter·half 정규화에 따른 PA-play 매핑을 검증한다. |
| `HistoricalScoreReplayServiceTest` | 출처별 재생 시각·저장 단위·경기 필터와 live 점수 공식 일치를 검증한다. |

### 3.8 scorer

| 테스트 클래스 | 핵심 검증 시나리오 |
|---|---|
| `AiCopyReprocessPropertiesTest` | AI 문구 재처리 배치 크기와 기간 입력 조합의 기본값·검증을 확인한다. |
| `AiCopyReprocessRunnerTest` | 전체·기간·단계별 헤드라인·이벤트 문구·play 번역 강제 재생성을 검증한다. |
| `AiEventCopyGeneratorTest` | 보호 문구 저장·재생성·시도 횟수·검수 실패·컨텍스트 변경 처리를 검증한다. |
| `AiFinalHeadlineGeneratorTest` | 보호·공개 종료 헤드라인의 선택 저장과 신호 발행·빈 응답 생략을 검증한다. |
| `AiPlayTranslationGeneratorTest` | 공개 play 번역 저장·재생성·claim 멱등성과 신호 발행을 검증한다. |
| `EventCopyRetrySchedulerTest` | 누락 보호 문구 재시도 대상 처리, 개별 실패 격리와 빈 등록 조건을 검증한다. |
| `FinalHeadlineBackfillPropertiesTest` | 백필 경기 ID의 중복 제거·입력 순서·빈 목록 처리를 검증한다. |
| `GameEventExtractorTest` | 보호·공개 이벤트 유형, payload, 중복 억제와 경계 리드 변경 추출을 검증한다. |
| `GameFinalizationServiceTest` | DB 기반 종료 멱등성, 트랜잭션 커밋 후 정리·AI 요청과 롤백 격리를 검증한다. |
| `ImportanceCalculatorTest` | 포스트시즌 중요도 값을 입력 또는 경기 값에서 선택하는지 검증한다. |
| `LiveRankingRebuildRunnerTest` | 기동 시 라이브 최신 점수로 Redis 랭킹을 복원하고 실패를 격리한다. |
| `LiveScoringPlayTranslationTriggerTest` | 마지막 관측 play 순서까지만 번역을 요청하는지 검증한다. |
| `LiveScoringServiceDelayedTaskTest` | 지연 작업이 발행 시점 스냅샷과 관측 play 상한으로 계산하는지 검증한다. |
| `LiveScoringServiceStateTest` | 이미 종료된 경기는 재계산 대신 라이브 상태를 정리하는지 검증한다. |
| `LiveSignalPublisherTest` | Redis 랭킹·보호 캐시·재조회 신호와 태그 fallback을 검증한다. |
| `PregameScoringServiceTest` | 배당·선발·순위 기반 사전 점수와 배당 결측 폴백을 검증한다. |
| `ReasonTagsTest` | 보호 라벨과 풀카운트 조건으로 최신 추천 태그를 구성하는지 검증한다. |
| `ScoreCalculatorTest` | 접전·이닝·득점·리드 변경·압박 신호와 0~100 점수 상한을 검증한다. |
| `ScorerRoleGateTest` | scorer 활성화와 배치 프로파일에 따른 운영 scorer 빈 등록을 검증한다. |
| `ScoreTaskListenerTest` | PREGAME·TERMINAL·LIVE 작업을 해당 서비스로 라우팅하는지 검증한다. |
| `SurgeDetectorTest` | 급상승 상태 전이·재무장·전역 한도와 트랜잭션 커밋 원자성을 검증한다. |
| `SurgeNotificationPublisherTest` | 급상승 알림 payload 계약과 원 트랜잭션 커밋 후 독립 발행을 검증한다. |
| `TensionCurveQueryServiceTest` | 보호·공개 해상도, 연장 이닝, 결측 이력과 레벨 상한을 검증한다. |
| `TerminalTaskRecoveryRunnerTest` | 종료 기록이 없는 FINAL 경기만 terminal 작업으로 복구하는지 검증한다. |
| `TimelineHighlightBackfillTest` | 급변 하이라이트 백필·재구축의 윈도·상한·쿨다운·anchor 선정을 검증한다. |
| `TimelineHighlightTriggerTest` | 라이브 급변 하이라이트의 임계·쿨다운·anchor·보호 문구 요청을 검증한다. |
| `WatchScoreScoringVersionTest` | 라이브·운영 재계산 점수에 scoring version을 저장하는지 검증한다. |

## 4. 실행 방법

- JDK 21을 사용한다. Gradle 8.14.3과 JDK 26 조합은 사용하지 않는다.
- 저장소 루트에서 실행한다.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
backend\gradlew.bat test
```

- JDK 설치 경로는 환경에 따라 다르므로 `C:\Program Files\Java`에서 확인한다.
- Testcontainers 기반 테스트가 포함되므로 전체 테스트 실행 전 Docker 실행 상태를 확인한다.

## 5. 명명·작성 원칙

- 테스트 클래스는 대상 클래스명 뒤에 `Test`를 붙인다. 실제 인프라 통합을 강조할 때는 `IntegrationTest`를 사용한다.
- 테스트 메서드는 관찰된 두 형식을 유지한다.
  - `대상_should기대결과_when조건` 형태의 영문 메서드명
  - 시나리오를 직접 설명하는 한국어 메서드명
- 복잡한 기대 결과는 `@DisplayName`으로 한국어 시나리오를 명확히 적는다.
- given-when-then 순서로 준비·실행·검증을 분리하고, 하나의 테스트는 하나의 핵심 행동을 검증한다.
- 값과 컬렉션은 AssertJ를 우선 사용하고, 협력 객체 호출·순서·인자는 Mockito로 검증한다.
- 시간·ID·점수 입력은 고정값을 사용해 반복 실행 결과를 결정적으로 유지한다.
- 외부 시스템은 기본적으로 대역으로 격리하며, 실제 DB 동작이 필요한 경우에만 통합 테스트로 분리한다.
- 스포일러 계약은 값뿐 아니라 금지 필드가 응답에 존재하지 않는지도 검증한다.

## 실행 결과

전체 스위트(테스트 클래스 112개) 기준이며, 통합 성격 테스트를 포함한다.

| 총 테스트 수 | 성공 | 실패 | 스킵 | 실행일 |
|---|---|---|---|---|
| 462 | 462 | 0 | 0 | 2026-07-18 |
