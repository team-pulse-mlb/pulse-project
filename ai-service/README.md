# ai-service

FastAPI 기반 AI 문구 서비스다. 현재는 상태 확인과 스포일러 검수 API 골격까지 구현되어 있으며, Spring Boot 호출과 실제 문구 생성은 구현 예정이다. 경기 추천 판단은 담당하지 않는다.

## 현재 엔드포인트

| API | 역할 |
|---|---|
| `GET /health` | 프로세스 상태 확인 |
| `GET /ai/test` | 라우터 연결 확인 |
| `POST /ai/spoiler-check` | 문구 스포일러 포함 여부 검수 |

현재 `spoiler-check`는 요청·응답 스키마와 고정 안전 응답만 구현되어 있다. 실제 모델 호출과 판정 로직은 연결되지 않았다.

## [구현 예정] backend 연동 API

| API | 예정 역할 |
|---|---|
| `POST /ai/spoiler-free-summary` | 경기 카드용 스포일러 없는 제목·이유 생성 |
| `POST /ai/notification-text` | 관심 경기 알림 문구 생성 |
| `POST /ai/replay-summary` | 종료 경기 다시보기 요약 생성 |

backend 호출 클라이언트와 장애 시 fallback도 현재 코드에서 확인되지 않는다. 요청·응답 계약과 호출 시점은 창현이 확정한 뒤 `docs/design/API_CONTRACTS.md`와 이 README를 함께 갱신한다.

## 폴더 구조

```
app/
├── main.py                    # FastAPI 앱 시작점, /health
├── core/config.py             # [구현 예정] 환경변수 · 설정
├── routers/ai_router.py       # API 경로 정의
├── schemas/ai_schema.py       # 요청/응답 형식 (Pydantic)
└── services/
    ├── openai_service.py      # [구현 예정] OpenAI 호출 · 문구 생성
    └── spoiler_guard.py       # [구현 예정] 생성 문구 스포일러 검수
```

## 실행

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

기동 후 다음 주소를 확인한다.

- 상태 확인: `http://localhost:8000/health`
- OpenAPI UI: `http://localhost:8000/docs`

`.env.example`을 참고해 로컬 `.env`에 `OPENAI_API_KEY`, `OPENAI_MODEL`을 둔다. 현재 설정 로더와 모델 호출 코드가 연결되지 않았으므로 값을 넣어도 실제 OpenAI 요청은 발생하지 않는다.

## backend 인터페이스 원칙

- ai-service는 추천 여부나 관전 점수를 결정하지 않는다.
- backend가 스포일러 없는 최소 context를 전달하고, ai-service는 문구 생성·검수 결과만 반환한다.
- 점수·승패·팀 우세·플레이 원문은 보호 모드 요청에 포함하지 않는다.
- 구현 전 실제 계약은 [API 계약](../docs/design/API_CONTRACTS.md)을 확인한다.

## 규칙

- Spring Boot로부터 스포일러 없는 context만 받는다. 점수·승패·팀 우세 정보는 입력에 포함되지 않는다.
- [구현 예정] 생성된 문구는 `spoiler_guard` 검수를 거친 뒤 반환한다.
- [구현 예정] 서비스 장애 시 Spring Boot가 안전한 기본 문구로 대체한다.
