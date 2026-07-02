# ai-service

FastAPI 기반 AI 문구 서비스. Spring Boot에서 호출하며, 문구 생성과 스포일러 검수만 담당한다. 경기 추천 판단은 하지 않는다.

## 엔드포인트

| API | 역할 |
|---|---|
| `POST /ai/spoiler-free-summary` | 경기 카드용 스포일러 없는 제목/이유 생성 |
| `POST /ai/notification-text` | 관심 경기 알림 문구 생성 |
| `POST /ai/replay-summary` | 종료 경기 다시보기 구간 요약 생성 |
| `POST /ai/spoiler-check` | 문구 스포일러 포함 여부 검수 |

## 폴더 구조

```
app/
├── main.py                    # FastAPI 앱 시작점, /health
├── core/config.py             # 환경변수 · 설정
├── routers/ai_router.py       # API 경로 정의
├── schemas/ai_schema.py       # 요청/응답 형식 (Pydantic)
└── services/
    ├── openai_service.py      # OpenAI 호출 · 문구 생성
    └── spoiler_guard.py       # 생성 문구 스포일러 검수
```

## 실행

```bash
python -m venv .venv
.venv/Scripts/activate        # Windows / macOS·Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## 규칙

- Spring Boot로부터 스포일러 없는 context만 받는다. 점수·승패·팀 우세 정보는 입력에 포함되지 않는다.
- 생성된 문구는 반드시 `spoiler_guard` 검수를 거친 뒤 반환한다.
- 서비스 장애 시 Spring Boot가 태그 조합 기반 기본 문구로 대체한다.
