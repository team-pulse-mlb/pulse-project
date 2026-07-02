from fastapi import APIRouter

from app.schemas.ai_schema import SpoilerCheckRequest,SpoilerCheckResponse

router = APIRouter(
  prefix="/ai",
  tags=["AI"]
)

@router.get("/test")
def ai_test():
  return{
    "status" : "ok",
    "message" : "AI router is working",
  }

@router.post("/spoiler-check", response_model=SpoilerCheckResponse)
def spoiler_check(request: SpoilerCheckRequest):
  return SpoilerCheckResponse(
    spoiler_safe=True,
    violations=[],
    fallback_text=None,
  )