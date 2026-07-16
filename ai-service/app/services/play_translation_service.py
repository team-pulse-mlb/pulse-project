import json
import logging

from openai import APITimeoutError, OpenAI

from app.core.config import settings
from app.prompts.play_translation_prompt import (
    build_play_translation_prompt,
)
from app.schemas.ai_schema import PlayTranslationRequest


logger = logging.getLogger(__name__)


class PlayTranslationGenerationError(RuntimeError):
    """
    PLAY_TRANSLATION 생성 실패를 router가
    실패 응답으로 변환할 수 있게 하는 예외입니다.
    """


def generate_play_translation(
    request: PlayTranslationRequest,
) -> dict[str, str]:
    """
    단일 MLB Play Result의 한국어 번역 후보를 생성합니다.

    ai-service는 fallback 번역을 만들지 않습니다.
    OpenAI 호출 실패 또는 응답 형식 오류는 예외로 전달하고,
    후속 router가 실패 상태 응답으로 변환합니다.
    """

    if not settings.openai_api_key:
        raise PlayTranslationGenerationError(
            "OPENAI_API_KEY_MISSING"
        )

    try:
        return _generate_openai_translation(request)
    except PlayTranslationGenerationError:
        # 이미 업무용 실패 코드로 변환된 예외는 그대로 전달합니다.
        raise
    except APITimeoutError as exc:
        logger.exception(
            "OpenAI PLAY_TRANSLATION generation timed out."
        )
        raise PlayTranslationGenerationError(
            "OPENAI_TIMEOUT"
        ) from exc
    except Exception as exc:
        logger.exception(
            "OpenAI PLAY_TRANSLATION generation failed."
        )
        raise PlayTranslationGenerationError(
            "OPENAI_GENERATION_FAILED"
        ) from exc


def _generate_openai_translation(
    request: PlayTranslationRequest,
) -> dict[str, str]:
    """
    OpenAI Responses API를 호출해
    PLAY_TRANSLATION 후보를 생성합니다.
    """

    client = OpenAI(
        api_key=settings.openai_api_key,
        timeout=settings.openai_timeout_seconds,
        # Spring Boot의 8초 제한을 넘지 않도록
        # SDK 자동 재시도를 사용하지 않습니다.
        max_retries=0,
    )

    response = client.responses.create(
        **_build_response_create_options(request)
    )

    return _parse_openai_translation(
        response.output_text
    )


def _build_response_create_options(
    request: PlayTranslationRequest,
) -> dict[str, object]:
    """
    OpenAI Responses API 호출 옵션을
    현재 서비스 모델 호환성에 맞게 구성합니다.
    """

    options: dict[str, object] = {
        "model": settings.openai_model,
        "input": build_play_translation_prompt(request),
        "max_output_tokens": (
            settings.openai_max_output_tokens
        ),
        "text": {
            "format": {
                "type": "json_object",
            },
        },
    }

    if _supports_temperature(settings.openai_model):
        options["temperature"] = (
            settings.openai_temperature
        )

    return options


def _supports_temperature(model: str) -> bool:
    """
    현재 서비스에서 사용하는 모델의
    temperature 지원 여부를 반환합니다.
    """

    return not model.startswith("gpt-5.6-luna")


def _parse_openai_translation(
    raw_text: str,
) -> dict[str, str]:
    """
    OpenAI 응답 문자열에서
    비어 있지 않은 translated_text를 추출합니다.
    """

    if (
        not isinstance(raw_text, str)
        or not raw_text.strip()
    ):
        raise PlayTranslationGenerationError(
            "OPENAI_EMPTY_RESPONSE"
        )

    try:
        data = json.loads(raw_text)
    except json.JSONDecodeError as exc:
        raise PlayTranslationGenerationError(
            "OPENAI_INVALID_JSON"
        ) from exc

    if not isinstance(data, dict):
        raise PlayTranslationGenerationError(
            "OPENAI_INVALID_RESPONSE_TYPE"
        )

    translated_text = _require_text_field(
        data,
        "translated_text",
    )

    return {
        "translated_text": translated_text,
    }


def _require_text_field(
    data: dict,
    field_name: str,
) -> str:
    """
    OpenAI JSON 응답에서
    비어 있지 않은 문자열 필드를 추출합니다.
    """

    value = data.get(field_name)

    if (
        not isinstance(value, str)
        or not value.strip()
    ):
        raise PlayTranslationGenerationError(
            f"OPENAI_RESPONSE_MISSING_FIELD:{field_name}"
        )

    return value.strip()