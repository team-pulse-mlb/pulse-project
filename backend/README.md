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

커밋된 정적 픽스처만으로 홈·상세·알림 화면용 데이터를 구성할 수 있다. S3 아카이브나 실시간 리플레이는 필요하지 않다.

저장소 루트에서 로컬 인프라를 실행한다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env up -d --wait
```

백엔드를 한 번 실행해 스키마를 만든다. 로컬 기본 프로파일은 Flyway 대신 JPA `ddl-auto=update`로 엔티티 기준 스키마를 생성한다. 이미 스키마가 있는 DB라면 이 단계는 생략할 수 있다.

1. IntelliJ에서 저장소 루트인 `pulse-project/`를 연다.
2. **Settings → Plugins**에서 `EnvFile` 플러그인을 설치하고 IntelliJ를 재시작한다.
3. **Run → Edit Configurations → PulseApplication**에서 **Enable EnvFile**을 선택한다.
4. EnvFile 목록에 저장소 루트의 `.env`를 추가한다.
5. `PulseApplication`을 실행한다.

Git Bash에서 `./gradlew bootRun`을 직접 실행하면 IntelliJ의 EnvFile 설정이 적용되지 않는다.

`user_notifications`, `user_favorite_players`는 확정 마이그레이션(V1·V10)에는 있으나 아직 JPA 엔티티가 없어 로컬 DB에는 테이블이 없을 수 있다. 시드는 해당 테이블이 있을 때만 채우므로, 엔티티가 생기면 별도 수정 없이 자동으로 시드된다.

다른 터미널의 저장소 루트에서 시드 스크립트를 한 번 실행한다.

```bash
bash backend/scripts/seed-dev-slate.sh
```

스크립트가 다음 데이터를 멱등하게 다시 구성한다.

- 예정 3경기, 진행 2경기, 종료 3경기
- 실제 경기 기반 전체 플레이·관전 점수 타임라인 2개
- 예상 선발, 보호·공개 이벤트 문구, 정렬 점수와 종료 헤드라인
- 데모 사용자 설정·관심 팀·관심 선수와 읽음·안읽음 알림
- Redis `score:rank:live` 순위와 `game:{id}:live` 최신 상태

예정 경기 수와 진행 경기 수를 바꾸려면 순서대로 인자를 전달한다. 각 값은 1 이상이고 합계는 7 이하여야 한다. 기본값은 전체 타임라인 경기 중 하나를 진행, 하나를 종료로 유지한다.

```bash
bash backend/scripts/seed-dev-slate.sh 3 2
```

실제 완주 경기로 CSV를 갱신하려면 다음 명령을 사용한다. 경기 ID를 생략하면 시뮬레이션 범위 밖의 완주 경기 중 플레이가 가장 많은 경기를 선택한다. 로더 계약을 유지하기 위해 경기 관련 파일명은 `5059222`로 고정되며 내용만 선택한 경기로 교체된다.

```bash
bash backend/scripts/dump-fixture-game.sh
bash backend/scripts/dump-fixture-game.sh 5059222
```

## 시뮬레이션

시뮬레이션은 선택 사항이다. 정적 슬레이트 대신 `poller → RabbitMQ → scorer → Redis/SSE` 실시간 흐름을 재현할 때 로컬 DB의 과거 경기를 복제해 사용한다.

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
