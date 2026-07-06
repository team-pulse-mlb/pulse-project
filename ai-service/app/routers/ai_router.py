from fastapi import APIRouter

from app.schemas.ai_schema import (
    SpoilerCheckRequest,
    SpoilerCheckResponse,
    SpoilerFreeSummaryRequest,
    SpoilerFreeSummaryResponse,
)

from app.services.openai_service import generate_spoiler_free_summary
from app.services.spoiler_guard import check_spoiler_text


# AI 관련 API들을 /ai 경로 아래로 묶는 라우터
router = APIRouter(
    prefix="/ai",
    tags=["AI"],
)


@router.get("/test")
def ai_test():
    """
    AI Router가 정상 연결되었는지 확인하는 테스트 API

    사용 목적:
    - main.py에서 ai_router가 정상 include 되었는지 확인
    - Swagger 문서에 /ai/test가 표시되는지 확인
    """

    return {
        "status": "ok",
        "message": "AI router is working",
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

    result = check_spoiler_text(request.text)

    return SpoilerCheckResponse(
        spoiler_safe=result["spoiler_safe"],
        violations=result["violations"],
        fallback_text=result["fallback_text"],
    )


@router.post("/spoiler-free-summary", response_model=SpoilerFreeSummaryResponse)
def spoiler_free_summary(request: SpoilerFreeSummaryRequest):
    """
    경기 카드용 스포일러 없는 제목/이유 문구를 생성하는 API

    현재 단계:
    - OpenAI API는 아직 연결하지 않음
    - openai_service.py에서 mock 문구 생성
    - 생성된 mock 문구도 spoiler_guard.py 검수를 거친 뒤 반환

    처리 흐름:
    1. Spring Boot 또는 Swagger에서 safe_context 전달
    2. openai_service.py에서 safe_title, safe_reason, notification_text 생성
    3. 생성 문구를 하나로 합쳐 spoiler_guard.py로 검사
    4. 안전하면 mock 응답 반환
    5. 위험하면 fallback 문구로 대체 반환
    """

    generated_summary = generate_spoiler_free_summary(request)

    combined_text = (
        f"{generated_summary['safe_title']} "
        f"{generated_summary['safe_reason']} "
        f"{generated_summary['notification_text']}"
    )

    guard_result = check_spoiler_text(combined_text)

    if not guard_result["spoiler_safe"]:
        fallback_text = guard_result["fallback_text"]

        return SpoilerFreeSummaryResponse(
            spoiler_safe=True,
            safe_title="관전 가치가 높아진 경기",
            safe_reason=fallback_text,
            notification_text=fallback_text,
            tags=["추천 구간"],
            violations=guard_result["violations"],
            fallback_used=True,
        )

    return SpoilerFreeSummaryResponse(
        spoiler_safe=True,
        safe_title=generated_summary["safe_title"],
        safe_reason=generated_summary["safe_reason"],
        notification_text=generated_summary["notification_text"],
        tags=generated_summary["tags"],
        violations=[],
        fallback_used=False,
    )