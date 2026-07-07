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

from app.services.openai_service import generate_spoiler_free_summary
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


def _build_failed_summary_response(
    context_hash: str | None,
    violations: list[str],
) -> SpoilerFreeSummaryResponse:
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
    generated_summary = generate_spoiler_free_summary(request)

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

    generated_summary = generate_spoiler_free_summary(request)
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

    generated_summary = generate_spoiler_free_summary(request)

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