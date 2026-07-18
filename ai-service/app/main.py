from fastapi import FastAPI

from app.routers.ai_router import router as ai_router

app = FastAPI(
    title="PULSE AI Service",
    description=(
        "PULSE 백엔드가 전달한 제한된 경기 컨텍스트로 문구를 생성하고 "
        "스포일러·근거 보존 규칙을 검수하는 내부 API입니다. "
        "저장 조건과 contextHash 검증 정책은 docs/design/policy/AI_COPY.md를 따릅니다."
    ),
    version="0.1.0",
    openapi_tags=[
        {
            "name": "AI",
            "description": "종료 헤드라인, 보호 이벤트 문구와 공개 플레이 번역",
        },
        {
            "name": "운영",
            "description": "서비스 상태 확인",
        },
    ],
)

app.include_router(ai_router)

@app.get(
    "/health",
    tags=["운영"],
    summary="상태 확인",
    description="프로세스가 요청을 받을 수 있는지 확인합니다.",
)
def health_check():
    return {
        "status": "ok",
        "service": "ai-service",
        "version": "0.1.0",
    }
