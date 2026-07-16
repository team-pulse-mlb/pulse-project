# FINAL_HEADLINE v2 Smoke Test

## 목적

`POST /ai/final-headline`의 `mode=REVEALED` 요청에서 FINAL_HEADLINE v2 context가 ai-service까지 정상 전달되고, spoiler guard가 공개 근거를 기준으로 검수하는지 확인한다.

## PR 흐름

- PR #214: backend FINAL_HEADLINE v2 context 확장
- PR #216: ai-service FINAL_HEADLINE v2 schema/prompt 반영
- PR #217: FINAL_HEADLINE v2 공개 모드 guard 보정
- PR #218: FINAL_HEADLINE v2 승자 주어 프롬프트 보강
- PR #219: OpenAI incomplete 응답 처리 보강
- PR #220: FINAL_HEADLINE v2 smoke 테스트 문서

## 검증 대상

```text
POST /ai/final-headline
```

## Smoke payload

```text
ai-service/test_payload_final_headline_v2_revealed.json
```

payload에는 다음 공개 근거가 포함된다.

- 최종 점수: 홈팀 5, 원정팀 3
- 승자: 홈팀
- 경기 종료 이닝: 10회
- 연장 경기 여부: true
- 홈팀: Los Angeles Dodgers
- 원정팀: San Francisco Giants
- 8회 Ohtani 우중간 홈런
- 공개된 verified play

## 로컬 서버 실행

```bash
cd /c/Users/강의실/Desktop/pulse-project/ai-service

./.venv/Scripts/python.exe -m uvicorn app.main:app \
  --host 0.0.0.0 \
  --port 8000 \
  --reload
```

서버 기동 확인:

```bash
curl -sS "http://localhost:8000/health"
```

기대 응답:

```json
{
  "status": "ok",
  "service": "ai-service",
  "version": "0.1.0"
}
```

## JSON 문법 확인

```bash
cd /c/Users/강의실/Desktop/pulse-project/ai-service

./.venv/Scripts/python.exe -m json.tool \
  --no-ensure-ascii \
  test_payload_final_headline_v2_revealed.json
```

## curl Smoke Test

```bash
cd /c/Users/강의실/Desktop/pulse-project/ai-service

curl -sS -X POST "http://localhost:8000/ai/final-headline" \
  -H "Content-Type: application/json" \
  --data-binary @test_payload_final_headline_v2_revealed.json
```

## 성공 기준

`safeTitle` 문장은 모델 실행마다 달라질 수 있다.

다음 필드를 기준으로 성공 여부를 판단한다.

```text
spoilerSafe=true
violations=[]
fallbackUsed=false
contextHash가 요청값과 일치
safeTitle이 비어 있지 않음
```

## 실제 Smoke Test 결과

최종 smoke 테스트에서 아래 응답을 확인했다.

```json
{
  "spoilerSafe": true,
  "contextHash": "game-5059082-final-revealed-v2-smoke",
  "safeTitle": "홈팀이 5-3으로 승리한 10회 연장전에서 Ohtani가 우중간 홈런",
  "violations": [],
  "fallbackUsed": false
}
```

검증 결과:

- OpenAI visible output 생성 성공
- 최종 점수 `5-3` 검증 통과
- `winner=home` 검증 통과
- `extraInnings=true` 근거 반영
- verified play의 Ohtani 홈런 근거 반영
- spoiler guard 통과
- fallback 미사용

## 관련 실패 코드

OpenAI Responses API 호출 실패 또는 불완전 응답은 다음 코드로 구분한다.

```text
OPENAI_API_KEY_MISSING
OPENAI_TIMEOUT
OPENAI_MAX_OUTPUT_TOKENS
OPENAI_CONTENT_FILTER
OPENAI_INCOMPLETE_RESPONSE
OPENAI_EMPTY_RESPONSE
OPENAI_INVALID_JSON
OPENAI_RESPONSE_MISSING_FIELD:safe_title
```

## 로컬 회귀 테스트

```bash
cd /c/Users/강의실/Desktop/pulse-project/ai-service

./.venv/Scripts/python.exe -m unittest tests.test_ai_schema
./.venv/Scripts/python.exe -m unittest tests.test_spoiler_free_prompt
./.venv/Scripts/python.exe -m unittest tests.test_spoiler_guard
./.venv/Scripts/python.exe -m unittest tests.test_openai_service
./.venv/Scripts/python.exe -m unittest discover -s tests
```

최종 확인 결과:

```text
tests.test_openai_service: Ran 14 tests / OK
전체 ai-service: Ran 144 tests / OK
```

## PROTECTED 모드 회귀 기준

PROTECTED 문구에는 다음 공개 정보가 노출되면 안 된다.

```text
finalScore
winner
teams
summaryFacts
revealedEvents
revealedMoments
verifiedPlays
homeInningScores
awayInningScores
sourceText
translatedText
batter
pitcher
```