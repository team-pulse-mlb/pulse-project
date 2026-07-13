# PULSE ai-service 컨테이너 이미지
# FastAPI(uvicorn) 기반 AI 문구 생성 서버를 8000 포트로 띄운다.
FROM python:3.12-slim

WORKDIR /app

# 의존성 레이어를 먼저 캐시해 코드 변경 시 재설치를 피한다.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
