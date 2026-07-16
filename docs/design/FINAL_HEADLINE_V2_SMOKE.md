# FINAL_HEADLINE v2 Smoke Test

## 목적

`POST /ai/final-headline`의 `mode=REVEALED` 요청에서 FINAL_HEADLINE v2 context가 ai-service까지 정상 전달되고, spoiler guard가 공개 근거를 기준으로 검수하는지 확인한다.

## PR 흐름

- PR #214: backend FINAL_HEADLINE v2 context 확장
- PR #216: ai-service FINAL_HEADLINE v2 schema/prompt 반영
- PR #217: FINAL_HEADLINE v2 공개 모드 guard 보정
- 현재 문서 브랜치: docs/changhyeon-final-headline-v2-smoke-v2

## 검증 대상

POST /ai/final-headline

## 로컬 서버 실행

```bash
cd /c/Users/강의실/Desktop/pulse-project/ai-service

./.venv/Scripts/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload