from fastapi import APIRouter

from app.schemas.ai_schema import (
    NotificationTextRequest,
    NotificationTextResponse,
    ReplaySummaryRequest,
    ReplaySummaryResponse,
    SpoilerCheckRequest,
    SpoilerCheckResponse,
    SpoilerFreeSummaryRequest,
    SpoilerFreeSummaryResponse,
)

from app.services.openai_service import (
    SpoilerFreeSummaryGenerationError,
    generate_spoiler_free_summary,
)
from app.services.spoiler_guard import check_spoiler_text


router = APIRouter(
    prefix="/ai",
    tags=["AI"],
)


@router.get("/test")
def ai_test():
    return {
        "status": "ok",
        "message": "AI router is working",
    }


@router.post("/spoiler-check", response_model=SpoilerCheckResponse)
def spoiler_check(request: SpoilerCheckRequest):
    result = check_spoiler_text(request.text)

    return SpoilerCheckResponse(
        spoiler_safe=result["spoiler_safe"],
        violations=result["violations"],
        fallback_text=result["fallback_text"],
    )


def _generation_failure_violations(
    error: SpoilerFreeSummaryGenerationError,
) -> list[str]:
    """
    OpenAI 생성 실패 사유를 Spring Boot가 읽을 수 있는 violations 배열로 변환한다.

    여기서 fallback 문구를 만들지는 않는다.
    fallback 기본 문구의 최종 책임은 Spring Boot에 있다.
    """

    reason = str(error).strip()
    if not reason:
        reason = "OPENAI_GENERATION_FAILED"

    return [reason]


def _build_failed_summary_response(
    context_hash: str | None,
    violations: list[str],
) -> SpoilerFreeSummaryResponse:
    """
    /ai/spoiler-free-summary 실패 응답을 만든다.

    생성 문구 필드는 내려주지 않고,
    Spring Boot 저장 판단에 필요한 공통 필드만 채운다.
    """

    return SpoilerFreeSummaryResponse(
        spoiler_safe=False,
        context_hash=context_hash,
        violations=violations,
        fallback_used=False,
    )


def _build_failed_notification_response(
    context_hash: str | None,
    violations: list[str],
) -> NotificationTextResponse:
    """
    /ai/notification-text 실패 응답을 만든다.
    """

    return NotificationTextResponse(
        spoiler_safe=False,
        context_hash=context_hash,
        violations=violations,
        fallback_used=False,
    )


def _build_failed_replay_response(
    context_hash: str | None,
    violations: list[str],
) -> ReplaySummaryResponse:
    """
    /ai/replay-summary 실패 응답을 만든다.
    """

    return ReplaySummaryResponse(
        spoiler_safe=False,
        context_hash=context_hash,
        violations=violations,
        fallback_used=False,
    )


@router.post(
    "/spoiler-free-summary",
    response_model=SpoilerFreeSummaryResponse,
    response_model_exclude_none=True,
)
def spoiler_free_summary(request: SpoilerFreeSummaryRequest):
    try:
        generated_summary = generate_spoiler_free_summary(request)
    except SpoilerFreeSummaryGenerationError as error:
        return _build_failed_summary_response(
            context_hash=request.context_hash,
            violations=_generation_failure_violations(error),
        )

    combined_text = (
        f"{generated_summary['safe_title']} "
        f"{generated_summary['safe_reason']} "
        f"{generated_summary['notification_text']}"
    )

    guard_result = check_spoiler_text(combined_text)

    if not guard_result["spoiler_safe"]:
        return _build_failed_summary_response(
            context_hash=request.context_hash,
            violations=guard_result["violations"],
        )

    return SpoilerFreeSummaryResponse(
        spoiler_safe=True,
        context_hash=request.context_hash,
        safe_title=generated_summary["safe_title"],
        safe_reason=generated_summary["safe_reason"],
        notification_text=generated_summary["notification_text"],
        tags=generated_summary["tags"],
        violations=[],
        fallback_used=False,
    )


@router.post(
    "/notification-text",
    response_model=NotificationTextResponse,
    response_model_exclude_none=True,
)
def notification_text(request: NotificationTextRequest):
    """
    알림에 사용할 스포일러 없는 짧은 문구를 생성하는 API
    """

    try:
        generated_summary = generate_spoiler_free_summary(request)
    except SpoilerFreeSummaryGenerationError as error:
        return _build_failed_notification_response(
            context_hash=request.context_hash,
            violations=_generation_failure_violations(error),
        )

    notification_text_value = generated_summary["notification_text"]

    guard_result = check_spoiler_text(notification_text_value)

    if not guard_result["spoiler_safe"]:
        return _build_failed_notification_response(
            context_hash=request.context_hash,
            violations=guard_result["violations"],
        )

    return NotificationTextResponse(
        spoiler_safe=True,
        context_hash=request.context_hash,
        notification_text=notification_text_value,
        tags=generated_summary["tags"],
        violations=[],
        fallback_used=False,
    )


@router.post(
    "/replay-summary",
    response_model=ReplaySummaryResponse,
    response_model_exclude_none=True,
)
def replay_summary(request: ReplaySummaryRequest):
    """
    다시보기 구간에 사용할 스포일러 없는 제목/설명 문구를 생성하는 API
    """

    try:
        generated_summary = generate_spoiler_free_summary(request)
    except SpoilerFreeSummaryGenerationError as error:
        return _build_failed_replay_response(
            context_hash=request.context_hash,
            violations=_generation_failure_violations(error),
        )

    replay_title = request.segment_label
    replay_summary_value = generated_summary["safe_reason"]
    tags = request.segment_reason_tags or generated_summary["tags"]

    combined_text = f"{replay_title} {replay_summary_value}"
    guard_result = check_spoiler_text(combined_text)

    if not guard_result["spoiler_safe"]:
        return _build_failed_replay_response(
            context_hash=request.context_hash,
            violations=guard_result["violations"],
        )

    return ReplaySummaryResponse(
        spoiler_safe=True,
        context_hash=request.context_hash,
        replay_title=replay_title,
        replay_summary=replay_summary_value,
        tags=tags,
        violations=[],
        fallback_used=False,
    )
