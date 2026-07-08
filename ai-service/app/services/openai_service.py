import json
import logging

from app.core.config import settings
from app.prompts.spoiler_free_prompt import build_spoiler_free_prompt
from app.schemas.ai_schema import (
    NotificationTextRequest,
    ReplaySummaryRequest,
    SpoilerFreeSummaryRequest,
)

logger = logging.getLogger(__name__)

AiTextRequest = (
    SpoilerFreeSummaryRequest
    | NotificationTextRequest
    | ReplaySummaryRequest
)


class SpoilerFreeSummaryGenerationError(RuntimeError):
    """
    AI 문구 생성 실패를 router에서 상태 응답으로 변환하기 위한 예외.
    """


def generate_spoiler_free_summary(request: AiTextRequest) -> dict:
    """
    스포일러 없는 AI 문구를 생성한다.

    ai-service는 fallback 기본 문구를 만들지 않는다.
    OpenAI 호출 실패 또는 응답 형식 오류는 예외로 전달하고,
    router가 실패 상태 응답으로 변환한다.
    """

    if not settings.openai_api_key:
        raise SpoilerFreeSummaryGenerationError("OPENAI_API_KEY_MISSING")

    try:
        return _generate_openai_spoiler_free_summary(request)
    except SpoilerFreeSummaryGenerationError:
        raise
    except Exception as exc:
        logger.exception("OpenAI summary generation failed.")
        raise SpoilerFreeSummaryGenerationError("OPENAI_GENERATION_FAILED") from exc


def _generate_openai_spoiler_free_summary(request: AiTextRequest) -> dict:
    """
    실제 OpenAI API를 호출해서 스포일러 없는 문구를 생성한다.
    """

    from openai import OpenAI

    client = OpenAI(api_key=settings.openai_api_key)

    response = client.responses.create(
        model=settings.openai_model,
        input=build_spoiler_free_prompt(request),
        temperature=settings.openai_temperature,
        max_output_tokens=settings.openai_max_output_tokens,
        text={"format": {"type": "json_object"}},
    )

    return _parse_openai_summary(response.output_text)


def _parse_openai_summary(raw_text: str) -> dict:
    """
    OpenAI 응답 문자열을 dict로 변환한다.

    필수 문구 필드가 없으면 fallback을 만들지 않고 실패로 처리한다.
    """

    try:
        data = json.loads(raw_text)
    except json.JSONDecodeError as exc:
        raise SpoilerFreeSummaryGenerationError("OPENAI_INVALID_JSON") from exc

    safe_title = _require_text_field(data, "safe_title")
    safe_reason = _require_text_field(data, "safe_reason")
    notification_text = _require_text_field(data, "notification_text")

    tags = data.get("tags")
    if not isinstance(tags, list):
        tags = []

    return {
        "safe_title": safe_title,
        "safe_reason": safe_reason,
        "notification_text": notification_text,
        "tags": tags,
    }


def _require_text_field(data: dict, field_name: str) -> str:
    value = data.get(field_name)

    if not isinstance(value, str) or not value.strip():
        raise SpoilerFreeSummaryGenerationError(
            f"OPENAI_RESPONSE_MISSING_FIELD:{field_name}"
        )

    return value.strip()