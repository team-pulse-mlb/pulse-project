from fastapi import FastAPI

from app.routers.ai_router import router as ai_router

app = FastAPI(
  title="PULSE AI Service",
  description="PULSE 프로젝트 Python AI/LLM 서버",
  version="0.1.0",
)

app.include_router(ai_router)

@app.get("/health")
def health_check():
  return {
    "status" : "ok",
    "service": "ai-service",
    "version": "0.1.0",
  }