# PULSE 백엔드

Java 21·Spring Boot 기반 백엔드다. MLB 경기 수집, 관전 점수 계산, 실시간 추천 순위, REST API·SSE를 담당한다.

## 패키지 구조

```text
com.pulse/
├── api/                 REST API·SSE
│   ├── home/            홈 경기 목록·실시간 랭킹
│   ├── sse/             Redis 신호의 SSE 중계
│   └── user/            회원·인증
├── poller/              MLB 경기·플레이 수집
├── scorer/              관전 점수·이벤트 계산
├── ranking/             Redis 실시간 추천 순위
├── replay/              이관·재점수화·백테스트
├── common/              외부 API·설정·메시지
└── domain/              엔티티·Repository
```

```text
MLB API → poller → PostgreSQL → RabbitMQ → scorer → Redis → REST API·SSE
```

## 기능별 주요 파일

| 기능 | 진입점 | 로직 구현 |
|---|---|---|
| 홈 경기 목록 | `api/home/HomeGameController.java` | `api/home/HomeQueryService.java` |
| 실시간 추천 순위 | `api/home/HomeRankingController.java` | `ranking/RankingService.java` |
| 경기 상세·펄스 그래프 | `api/GameController.java` | `api/GameQueryService.java`, `scorer/TensionCurveQueryService.java` |
| SSE | `api/sse/SseController.java` | `api/sse/RedisSignalRelay.java` |
| 점수 계산 | `scorer/ScoreTaskListener.java` | `scorer/LiveScoringService.java`, `scorer/ScoreCalculator.java` |
| 경기 수집 | `poller/` | `common/client/` |
| 알림 발행 | `scorer/SurgeDetector.java` | `common/message/NotificationOutboxDispatcher.java` |

## 정적 개발 슬레이트

1. 저장소 루트에서 로컬 인프라를 실행한다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env up -d --wait
```

2. IntelliJ의 `PulseApplication` 실행 구성에 저장소 루트의 `.env`를 EnvFile로 적용한 뒤 실행한다.

3. 다른 터미널의 저장소 루트에서 시드를 적용한다. 팀·선수와 시뮬레이터 원본 경기(games+plays)만 적재하며, 원본은 과거 종료 상태라 슬레이트에 직접 노출되지 않는다. 예정·진행·종료 카드와 점수·문구·알림은 아래 시뮬레이터가 라이브로 만든다.

```bash
bash backend/scripts/seed-dev-slate.sh
```

픽스처를 갱신하려면 다음 중 하나를 실행한다.

```bash
bash backend/scripts/dump-fixture-game.sh
bash backend/scripts/dump-fixture-game.sh 5059222
```

## 시뮬레이션

시드 이후 카드·점수·문구·알림은 시뮬레이터가 만든다. 로컬 DB의 과거 경기를 원본으로 복제해 `poller → RabbitMQ → scorer → Redis/SSE → AI 문구 → SURGE → notify` 실시간 흐름을 그대로 재현한다.

시뮬레이션 스크립트가 별도의 백엔드를 8080 포트로 실행하므로, IntelliJ에서 실행 중인 `PulseApplication`을 먼저 중지한다. Docker 컨테이너는 중지하지 않는다.

IntelliJ에서 `pulse-project/`를 연 상태의 저장소 루트 터미널에서 실행한다.

```bash
bash backend/scripts/run-simulation.sh
```

스크립트가 다음 작업을 처리한다.

- 로컬 PostgreSQL·Redis·RabbitMQ 실행
- 플레이가 100개 이상이고 후반 득점이 많은 경기 선택
- 중복되지 않는 시뮬레이션 경기 ID 생성
- 20배속 `SURGE` 모드로 백엔드 실행

특정 경기 ID를 사용하려면 인자로 전달한다.

```bash
bash backend/scripts/run-simulation.sh 123456
```

### 다중 경기 연출 (발표용 데모)

예정·진행·종료 카드를 동시에 띄우려면 `run-simulation.sh`(단일 경기) 대신 `pulse.simulation.games` 목록으로 여러 원본을 한 번에 연출한다. 원본은 시드가 적재한 8800000004·8800000006(플레이 100개 이상)을 쓰고, 경기별 `target-game-id`와 `start-offset`으로 상태를 나눈다. `start-offset`은 양수면 그만큼 이미 진행된 상태(진행·종료), 음수면 미래 시작(예정)을 의미한다. 설정 예시는 `application.yml`의 `pulse.simulation.games` 주석을 따른다.

- SURGE 전역 발화 한도 때문에 여러 진행 경기가 동시에 득점하면 알림이 일부만 발화한다. 알림을 보이려는 경기는 `start-offset`을 시차로 벌린다.
- 예정 카드의 음수 `start-offset`이 너무 크면 시작 시각이 다음 날(ET)로 넘어가 오늘 슬레이트에서 빠진다. 같은 ET 날짜 안에 두도록 조정한다.
- 실제 AI 문구는 `ai-service` 컨테이너와 `OPENAI_API_KEY`가 있어야 생성된다. 없으면 문구만 비고 나머지 흐름은 동작한다.

경기 데이터가 없으면 다음 방법 중 하나로 로컬 DB에 저장한다.

```bash
# S3의 최근 경기 적재
bash backend/scripts/load-s3-game.sh

# 현재 라이브 경기 수집. 종료할 때 Ctrl+C
bash backend/scripts/collect-live-game.sh
```

S3 적재에는 `.env`의 `PULSE_REPLAY_S3_BUCKET`과 AWS 자격 증명, 라이브 수집에는 `BDL_API_KEY`가 필요하다. S3의 특정 경기는 `bash backend/scripts/load-s3-game.sh <경기_ID> <YYYY-MM-DD>`로 지정한다. 적재 후 시뮬레이션 스크립트를 다시 실행한다.

### 회사망 프록시(HTTPS 가로채기) 대응

회사망에서 프록시(Somansa 등)가 HTTPS를 가로채면, 교체된 CA가 JDK truststore에 없어 외부 API(`api.balldontlie.io`) 호출이 `PKIX path building failed` 오류로 실패한다. 집망에서는 해당하지 않는다.

프록시 CA를 포함한 Java truststore를 만들어 `.env`에 지정하면 수집·적재 스크립트가 실행 시 JVM에 주입한다. Git Bash에서 JDK 21 기준으로 만든다(경로·CA 이름은 환경에 맞춘다).

```bash
JH="/c/Program Files/Java/jdk-21.0.11"   # 설치된 JDK 21 경로
mkdir -p backend/.local
# 프록시 CA 인증서(예: somansa-root-ca.cer)를 backend/.local/에 저장한 뒤 실행
cp "$JH/lib/security/cacerts" backend/.local/pulse-truststore.jks
"$JH/bin/keytool" -importcert -noprompt -trustcacerts \
  -alias proxy-root-ca -file backend/.local/somansa-root-ca.cer \
  -keystore backend/.local/pulse-truststore.jks -storepass changeit
```

`.env`에 만든 truststore 경로를 지정한다(`backend/.local/`은 gitignore되어 커밋되지 않는다).

```dotenv
PULSE_JAVA_TRUSTSTORE=C:/Projects/pulse-project/backend/.local/pulse-truststore.jks
PULSE_JAVA_TRUSTSTORE_PASSWORD=changeit
```

프록시 CA 인증서는 Windows PowerShell에서 신뢰 저장소를 통해 추출할 수 있다.

```powershell
Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Subject -match "Somansa" } |
  ForEach-Object { [IO.File]::WriteAllBytes("backend\.local\somansa-root-ca.cer", $_.Export("Cert")) }
```

`simulation ready` 로그와 Redis 순위를 확인한다.

```bash
docker exec pulse-redis redis-cli ZRANGE score:rank:live 0 -1 WITHSCORES REV
```

## 테스트와 빌드

JDK 21에서 실행한다.

```bash
cd backend
./gradlew test
./gradlew clean build
```
