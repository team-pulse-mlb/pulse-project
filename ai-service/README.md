# ai-service

FastAPI 기반 AI 문구 생성 서비스다. backend에서 스포일러 없는 경기 정보를 받아 종료 경기 헤드라인과 이벤트 문구를 생성하고 검수한다.

## API

| API | 역할 |
|---|---|
| `GET /health` | 상태 확인 |
| `POST /ai/final-headline` | 종료 경기 헤드라인 생성 |
| `POST /ai/event-copy` | 경기 이벤트 문구 생성 |

## 원칙

- 추천 여부와 관전 점수는 결정하지 않는다.
- backend가 전달한 스포일러 없는 정보만 사용한다.
- 생성 실패나 검수 실패 시 대체 문구를 만들지 않고 실패 상태를 반환한다.
