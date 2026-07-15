import logging

from fastapi import APIRouter

from app.core.config import settings
from app.schemas.ai_schema import (
    EventCopyRequest,
    EventCopyResponse,
    FinalHeadlineRequest,
    FinalHeadlineResponse,
)
from app.services.openai_service import (
    SpoilerFreeSummaryGenerationError,
    generate_spoiler_free_summary,
)
from app.services.spoiler_guard import check_spoiler_text


logger = logging.getLogger(__name__)

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


def _generation_failure_violations(
    error: SpoilerFreeSummaryGenerationError,
) -> list[str]:
    """
    OpenAI 생성 실패 예외를 Spring Boot가 저장 여부를 판단할 수 있는
    violations 코드로 변환한다.
    """

    error_code = str(error).strip()

    if not error_code:
        return ["OPENAI_GENERATION_FAILED"]

    return [error_code]


def _build_failed_response(
    response_type: type[FinalHeadlineResponse] | type[EventCopyResponse],
    context_hash: str,
    violations: list[str],
) -> FinalHeadlineResponse | EventCopyResponse:
    """
    생성 실패 또는 spoiler guard 실패 응답을 만든다.

    ai-service는 fallback 문구를 만들지 않으므로 safeTitle은 내려주지 않는다.
    """

    return response_type(
        spoiler_safe=False,
        context_hash=context_hash,
        violations=violations,
        fallback_used=False,
    )


def _extract_safe_title(generated_summary: dict) -> str | None:
    """
    OpenAI 생성 결과에서 실제 화면에 저장할 문구만 추출한다.

    현재 openai_service는 기존 safe_title 필드를 반환하므로,
    router에서는 이 값을 FINAL_HEADLINE / EVENT_COPY 공통 copy로 사용한다.
    """

    safe_title = generated_summary.get("safe_title")

    if not isinstance(safe_title, str):
        return None

    safe_title = safe_title.strip()

    if not safe_title:
        return None

    return safe_title


def _copy_purpose(
    request: FinalHeadlineRequest | EventCopyRequest,
) -> str:
    """
    요청 DTO 타입을 운영 로그의 AI 문구 목적값으로 변환한다.
    """

    if isinstance(request, FinalHeadlineRequest):
        return "FINAL_HEADLINE"

    return "EVENT_COPY"


def _copy_event_id(
    request: FinalHeadlineRequest | EventCopyRequest,
) -> int | None:
    """
    EVENT_COPY 요청에만 존재하는 eventId를 반환한다.
    """

    if isinstance(request, EventCopyRequest):
        return request.event_id

    return None


def _log_copy_failure(
    event_name: str,
    request: FinalHeadlineRequest | EventCopyRequest,
    violations: list[str],
) -> None:
    """
    생성 실패 또는 spoiler guard 반려를 운영 로그에 기록한다.

    safeTitle, safeContext, contextHash는 스포일러 또는 내부 데이터가
    포함될 수 있으므로 로그에 기록하지 않는다.
    """

    logger.warning(
        "%s purpose=%s gameId=%s eventId=%s mode=%s model=%s violations=%s",
        event_name,
        _copy_purpose(request),
        request.game_id,
        _copy_event_id(request),
        request.mode.value,
        settings.openai_model,
        violations,
    )


def _log_copy_success(
    request: FinalHeadlineRequest | EventCopyRequest,
) -> None:
    """
    AI 문구가 생성과 spoiler guard 검수를 모두 통과했음을 기록한다.
    """

    logger.info(
        "AI_COPY_GENERATED purpose=%s gameId=%s eventId=%s mode=%s model=%s violations=%s",
        _copy_purpose(request),
        request.game_id,
        _copy_event_id(request),
        request.mode.value,
        settings.openai_model,
        [],
    )


def _generate_checked_copy(
    request: FinalHeadlineRequest | EventCopyRequest,
    response_type: type[FinalHeadlineResponse] | type[EventCopyResponse],
) -> FinalHeadlineResponse | EventCopyResponse:
    """
    AI 문구 생성과 spoiler guard 검수를 공통 처리한다.

    처리 흐름:
    1. OpenAI 문구 후보 생성
    2. 생성 실패 시 spoilerSafe=false 상태 응답 반환
    3. 생성 문구 필드 누락 시 spoilerSafe=false 상태 응답 반환
    4. spoiler guard 검수
    5. 검수 실패 시 spoilerSafe=false 상태 응답 반환
    6. 검수 통과 시 safeTitle 반환
    """

    try:
        generated_summary = generate_spoiler_free_summary(request)
    except SpoilerFreeSummaryGenerationError as error:
        violations = _generation_failure_violations(error)

        _log_copy_failure(
            event_name="AI_COPY_GENERATION_FAILED",
            request=request,
            violations=violations,
        )

        return _build_failed_response(
            response_type=response_type,
            context_hash=request.context_hash,
            violations=violations,
        )

    safe_title = _extract_safe_title(generated_summary)

    if safe_title is None:
        violations = ["OPENAI_RESPONSE_MISSING_FIELD:safe_title"]

        _log_copy_failure(
            event_name="AI_COPY_GENERATION_FAILED",
            request=request,
            violations=violations,
        )

        return _build_failed_response(
            response_type=response_type,
            context_hash=request.context_hash,
            violations=violations,
        )

    # 요청 모드와 검증 기준이 되는 safeContext를 함께 전달해
    # PROTECTED / REVEALED별 정책과 실제 결과 일치 검사를 적용합니다.
    guard_result = check_spoiler_text(
        text=safe_title,
        mode=request.mode,
        safe_context=request.safe_context,
    )

    if not guard_result["spoiler_safe"]:
        violations = guard_result["violations"]

        _log_copy_failure(
            event_name="SPOILER_GUARD_REJECTED",
            request=request,
            violations=violations,
        )

        return _build_failed_response(
            response_type=response_type,
            context_hash=request.context_hash,
            violations=violations,
        )

    _log_copy_success(request)

    return response_type(
        spoiler_safe=True,
        context_hash=request.context_hash,
        safe_title=safe_title,
        violations=[],
        fallback_used=False,
    )


@router.post(
    "/final-headline",
    response_model=FinalHeadlineResponse,
    response_model_exclude_none=True,
)
def final_headline(request: FinalHeadlineRequest):
    """
    종료 경기 헤드라인을 생성한다.

    PROTECTED:
    - 점수, 승패, 우세 팀을 언급하면 안 된다.

    REVEALED:
    - safeContext에 포함된 finalScore, winner와 일치하는 범위에서만
      결과 언급이 가능하다.
    """

    return _generate_checked_copy(
        request=request,
        response_type=FinalHeadlineResponse,
    )


@router.post(
    "/event-copy",
    response_model=EventCopyResponse,
    response_model_exclude_none=True,
)
def event_copy(request: EventCopyRequest):
    """
    이벤트 타임라인 문구를 생성한다.

    구간 요약 API가 아니라,
    경기 이벤트 타임라인에 표시할 짧은 문구를 생성한다.
    """

    return _generate_checked_copy(
        request=request,
        response_type=EventCopyResponse,
    )