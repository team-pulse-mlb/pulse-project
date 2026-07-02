from fastapi import APIRouter

from app.schemas.ai_schema import SpoilerCheckRequest,SpoilerCheckResponse
from app.services.spoiler_guard import check_spoiler_text


#AI 관련 API들을 /ai 경로 아래로 묶는 라우터
router = APIRouter(
  prefix="/ai",
  tags=["AI"]
)

@router.get("/test")
def ai_test():
  """
  AI Router가 정상 연결되었는지 확인하는 테스트 API

  사용 목적:
  - main.py에서 ai_router가 정상 include 되었는지 확인
  - Swagger 문서에 /ai/test가 표시되는지 확인
  """

  return{
    "status" : "ok",
    "message" : "AI router is working",
  }

@router.post("/spoiler-check", response_model=SpoilerCheckResponse)
def spoiler_check(request: SpoilerCheckRequest):
  """
  입력된 문구에 스포일러성 표현이 포함되어 있는지 검사하는 API

  처리 흐름:
  1. Swagger 또는 Spring Boot에서 검사할 문구를 text로 전달
  2. spoiler_guard.py의 check_spoiler_text 함수 호출
  3. 검사 결과를 SpoilerCheckResponse 형식으로 반환
  """
  #request.text에는 검사할 문구가 들어 있음
  result = check_spoiler_text(request.text)

  #spoiler_guard.py에서 반환한 dict를 응답 schema에 맞춰 반환
  return SpoilerCheckResponse(
    spoiler_safe=result["spoiler_safe"],
    violations=result["violations"],
    fallback_text=result["fallback_text"],
  )