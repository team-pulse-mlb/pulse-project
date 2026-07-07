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

```text
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
.venv/Scripts/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## 환경변수

`.env`는 로컬에서만 사용하고 커밋하지 않는다.

```env
APP_ENV=local
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_MODEL=gpt-4o-mini
OPENAI_TEMPERATURE=0.2
OPENAI_MAX_OUTPUT_TOKENS=200
```

## ai-service 규칙

- ai-service는 추천 점수, 승패, 우세 팀, replay segment를 계산하지 않는다.
- Spring Boot가 넘긴 safe context만 사용해 문구를 생성한다.
- 생성된 문구는 반드시 `spoiler_guard.py` 검수를 통과해야 한다.
- 검수 실패 또는 OpenAI 호출 실패 시 fallback 문구를 반환한다.

## safe context 매핑 기준

Spring Boot 응답에서 ai-service 요청으로 매핑하는 기준은 아래와 같다.

| Spring field | ai-service field |
|---|---|
| `status` | `safeContext.gameStatus` |
| `periodLabel` | `safeContext.inningPhase` |
| `reasonTags` | `safeContext.safeTags` |
| `spoilerSafeSignals` | `safeContext.reasonCodes` |

`recentPlays`, `teams`, `startTime`, `purpose` 같은 원본 경기 필드는 문구 생성에 사용하지 않는다.

## 요청 예시

`test_payload_spring_safe_context.json`은 Spring safe context를 ai-service 요청 형태로 바꾼 테스트 payload다.

```json
{
  "gameId": 5059082,
  "mode": "PROTECTED",
  "surface": "HOME_CARD",
  "language": "ko",
  "maxLength": 80,
  "safeContext": {
    "gameStatus": "STATUS_FINAL",
    "inningPhase": "경기 종료",
    "safeTags": ["후반 긴장 구간"],
    "reasonCodes": ["late_or_extra"]
  }
}
```

## 응답 필드

### `/ai/spoiler-free-summary`

| Field | 설명 |
|---|---|
| `spoilerSafe` | 최종 문구가 스포일러 검수를 통과했는지 여부 |
| `safeTitle` | 스포일러 없는 제목 |
| `safeReason` | 스포일러 없는 추천 이유 |
| `notificationText` | 알림에 사용할 수 있는 짧은 문구 |
| `tags` | 화면에 노출 가능한 안전 태그 |
| `violations` | 검수에서 감지된 위반 항목 |
| `fallbackUsed` | fallback 문구 사용 여부 |

## 테스트

```bash
python -m compileall app
```

PowerShell 예시:

```powershell
$body = Get-Content -Raw -Encoding UTF8 test_payload_spring_safe_context.json

Invoke-RestMethod `
  -Uri "http://127.0.0.1:8000/ai/spoiler-free-summary" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body $body
```