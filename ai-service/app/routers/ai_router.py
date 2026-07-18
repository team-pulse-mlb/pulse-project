import logging

from fastapi import APIRouter

from app.core.config import settings
from app.schemas.ai_schema import (
    EventCopyRequest,
    EventCopyResponse,
    FinalHeadlineRequest,
    FinalHeadlineResponse,
    PlayTranslationRequest,
    PlayTranslationResponse,
)
from app.services.openai_service import (
    SpoilerFreeSummaryGenerationError,
    generate_spoiler_free_summary,
)
from app.services.play_translation_guard import check_play_translation
from app.services.play_translation_service import (
    PlayTranslationGenerationError,
    generate_play_translation,
)
from app.services.final_headline_evidence_guard import (
    validate_final_headline_evidence,
)
from app.services.spoiler_guard import check_spoiler_text


logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/ai",
    tags=["AI"],
)


@router.get("/test", include_in_schema=False)
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


def _extract_used_fact_ids(
    generated_summary: dict,
) -> list[str]:
    """
    FINAL_HEADLINE 생성 결과에서 사용한 summaryFacts ID를 추출합니다.

    openai_service parser가 타입을 검증하지만,
    router 단위 테스트의 mock 응답과 예외 상황도 방어하기 위해
    문자열 목록이 아니면 빈 목록을 사용합니다.
    """

    value = generated_summary.get("used_fact_ids", [])

    if not isinstance(value, list):
        return []

    if any(
        not isinstance(item, str) or not item.strip()
        for item in value
    ):
        return []

    return [item.strip() for item in value]


def _extract_used_play_ids(
    generated_summary: dict,
) -> list[int]:
    """
    FINAL_HEADLINE 생성 결과에서 사용한 verifiedPlay ID를 추출합니다.

    bool은 Python에서 int의 하위 타입이므로 명시적으로 제외합니다.
    """

    value = generated_summary.get("used_play_ids", [])

    if not isinstance(value, list):
        return []

    if any(
        not isinstance(item, int)
        or isinstance(item, bool)
        or item <= 0
        for item in value
    ):
        return []

    return list(value)


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

    used_fact_ids: list[str] = []
    used_play_ids: list[int] = []

    if isinstance(request, FinalHeadlineRequest):
        used_fact_ids = _extract_used_fact_ids(generated_summary)
        used_play_ids = _extract_used_play_ids(generated_summary)

        evidence_result = validate_final_headline_evidence(
            mode=request.mode,
            safe_context=request.safe_context,
            used_fact_ids=used_fact_ids,
            used_play_ids=used_play_ids,
            text=safe_title,
        )

        if not evidence_result["evidence_safe"]:
            violations = evidence_result["violations"]

            _log_copy_failure(
                event_name="FINAL_HEADLINE_EVIDENCE_REJECTED",
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

    response_values: dict[str, object] = {
        "spoiler_safe": True,
        "context_hash": request.context_hash,
        "safe_title": safe_title,
        "violations": [],
        "fallback_used": False,
    }

    # evidence 계약은 FINAL_HEADLINE에만 적용합니다.
    # EVENT_COPY 응답 구조에는 usedFactIds/usedPlayIds를 포함하지 않습니다.
    if isinstance(request, FinalHeadlineRequest):
        response_values["used_fact_ids"] = used_fact_ids
        response_values["used_play_ids"] = used_play_ids

    return response_type(**response_values)


def _translation_failure_violations(
    error: PlayTranslationGenerationError,
) -> list[str]:
    """
    PLAY_TRANSLATION 생성 실패 예외를
    Spring Boot가 저장 여부를 판단할 수 있는 violations 코드로 변환합니다.
    """

    error_code = str(error).strip()

    if not error_code:
        return ["OPENAI_GENERATION_FAILED"]

    return [error_code]


def _build_play_translation_failed_response(
    request: PlayTranslationRequest,
    violations: list[str],
) -> PlayTranslationResponse:
    """
    PLAY_TRANSLATION 생성 실패 응답을 만듭니다.

    ai-service는 fallback 번역을 만들지 않으므로
    translatedText는 내려주지 않습니다.
    """

    return PlayTranslationResponse(
        translated_text=None,
        violations=violations,
        fallback_used=False,
        context_hash=request.context_hash,
    )


def _extract_translated_text(
    generated_translation: dict,
) -> str | None:
    """
    OpenAI 생성 결과에서 저장 가능한 번역문만 추출합니다.
    """

    translated_text = generated_translation.get("translated_text")

    if not isinstance(translated_text, str):
        return None

    translated_text = translated_text.strip()

    if not translated_text:
        return None

    return translated_text


def _log_play_translation_failure(
    event_name: str,
    request: PlayTranslationRequest,
    violations: list[str],
) -> None:
    """
    PLAY_TRANSLATION 생성 실패를 운영 로그에 기록합니다.

    sourceText, translatedText, contextHash 원문은 로그에 남기지 않습니다.
    """

    logger.warning(
        "%s purpose=%s gameId=%s playId=%s mode=%s model=%s violations=%s",
        event_name,
        "PLAY_TRANSLATION",
        request.game_id,
        request.play_id,
        request.mode.value,
        settings.openai_model,
        violations,
    )


def _log_play_translation_success(
    request: PlayTranslationRequest,
) -> None:
    """
    PLAY_TRANSLATION 생성 성공을 운영 로그에 기록합니다.
    """

    logger.info(
        "AI_COPY_GENERATED purpose=%s gameId=%s playId=%s mode=%s model=%s violations=%s",
        "PLAY_TRANSLATION",
        request.game_id,
        request.play_id,
        request.mode.value,
        settings.openai_model,
        [],
    )


@router.post(
    "/final-headline",
    response_model=FinalHeadlineResponse,
    response_model_exclude_none=True,
    summary="종료 경기 헤드라인 생성",
    description=(
        "보호 또는 공개 모드의 제한된 컨텍스트로 종료 경기 헤드라인을 생성하고 "
        "스포일러 및 근거 일치 여부를 검수합니다."
    ),
    response_description="생성·검수 결과와 요청 contextHash",
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
    summary="보호 이벤트 문구 생성",
    description=(
        "보호 모드 타임라인 하이라이트의 안전한 라벨과 상황 근거로 "
        "스포일러 없는 한 문장 설명을 생성합니다."
    ),
    response_description="생성·검수 결과와 요청 contextHash",
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


@router.post(
    "/play-translation",
    response_model=PlayTranslationResponse,
    response_model_exclude_none=True,
    summary="공개 플레이 문구 번역",
    description=(
        "공개 모드의 단일 Play Result 원문을 선수명·숫자·야구 결과를 보존해 "
        "한국어로 번역하고 추가 해설 여부를 검수합니다."
    ),
    response_description="번역·검수 결과와 요청 contextHash",
)
def play_translation(request: PlayTranslationRequest):
    """
    공개 모드 최근 플레이에 표시할 단일 Play Result 원문을 한국어로 번역합니다.

    PLAY_TRANSLATION:
    - REVEALED 모드만 허용합니다.
    - targetLanguage=ko만 허용합니다.
    - ai-service는 fallback 번역을 만들지 않습니다.
    """

    try:
        generated_translation = generate_play_translation(request)
    except PlayTranslationGenerationError as error:
        violations = _translation_failure_violations(error)

        _log_play_translation_failure(
            event_name="PLAY_TRANSLATION_GENERATION_FAILED",
            request=request,
            violations=violations,
        )

        return _build_play_translation_failed_response(
            request=request,
            violations=violations,
        )

    translated_text = _extract_translated_text(generated_translation)

    if translated_text is None:
        violations = ["OPENAI_RESPONSE_MISSING_FIELD:translated_text"]

        _log_play_translation_failure(
            event_name="PLAY_TRANSLATION_GENERATION_FAILED",
            request=request,
            violations=violations,
        )

        return _build_play_translation_failed_response(
            request=request,
            violations=violations,
        )

    guard_result = check_play_translation(
        source_text=request.source_text,
        translated_text=translated_text,
    )

    if not guard_result.spoiler_safe:
        violations = guard_result.violations

        _log_play_translation_failure(
            event_name="PLAY_TRANSLATION_GUARD_REJECTED",
            request=request,
            violations=violations,
        )

        return _build_play_translation_failed_response(
            request=request,
            violations=violations,
        )

    _log_play_translation_success(request)

    return PlayTranslationResponse(
        translated_text=translated_text,
        violations=[],
        fallback_used=False,
        context_hash=request.context_hash,
    )
