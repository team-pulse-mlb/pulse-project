# API 계약 안내

## REST 계약 기준

REST API의 경로, 요청 파라미터, 요청·응답 스키마, 인증 요구사항과 기본 동작은 실행 코드가 생성하는 OpenAPI를 단일 기준으로 사용한다.

| 서비스 | Swagger UI | OpenAPI JSON |
|---|---|---|
| Spring Boot backend | `/swagger-ui/index.html` | `/v3/api-docs` |
| FastAPI ai-service | `/docs` | `/openapi.json` |

REST 계약을 변경할 때는 컨트롤러의 OpenAPI 애너테이션과 요청·응답 DTO를 먼저 수정한다. 이 문서에 엔드포인트 표나 JSON 응답 예시를 중복 작성하지 않는다. 아직 구현되지 않은 REST API는 OpenAPI에 포함하지 않으며, 구현 전 합의가 필요하면 담당 설계 문서에 동작 원칙만 기록한다.

공통 오류 응답은 HTTP 상태 코드와 `{ "code": "...", "message": "..." }` 형식을 함께 사용한다.

## 주제별 계약 문서

| 주제 | 문서 |
|---|---|
| 인증 토큰·이메일 인증·관심 선수 | [AUTH_POLICY.md](AUTH_POLICY.md) |
| SSE 이벤트·연결·재연결 | [SSE.md](SSE.md) |
| RabbitMQ·`ScoreTask`·outbox | [MESSAGING.md](MESSAGING.md) |
| Redis 키·라이프사이클 | [REDIS_KEYS.md](REDIS_KEYS.md) |
| 팀 간 Java 인터페이스 | [MODULE_INTERFACES.md](MODULE_INTERFACES.md) |
| 경기 알림·전환 추천 | [NOTIFICATIONS.md](NOTIFICATIONS.md) |
| AI 컨텍스트·HTTP·저장·검수 | [AI_COPY.md](AI_COPY.md) |
| 데이터 저장·유실·동시성 원칙 | [DATA_STORAGE.md](DATA_STORAGE.md) |
