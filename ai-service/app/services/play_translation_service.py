import json
import logging
import random
import time
from typing import Callable

from openai import (
    APIConnectionError,
    APITimeoutError,
    OpenAI,
    RateLimitError,
)

from app.core.config import settings
from app.prompts.play_translation_prompt import (
    build_play_translation_prompt,
)
from app.schemas.ai_schema import PlayTranslationRequest


logger = logging.getLogger(__name__)


# OpenAI 호출 자체는 성공했지만 응답 본문이 불안정한 경우입니다.
# 이 경우 같은 요청을 한 번 더 보내면 정상 JSON이 돌아올 수 있으므로
# PLAY_TRANSLATION에 한해 짧게 재시도합니다.
RETRYABLE_GENERATION_ERROR_CODES = frozenset(
    {
        "OPENAI_EMPTY_RESPONSE",
        "OPENAI_INVALID_JSON",
        "OPENAI_INVALID_RESPONSE_TYPE",
        "OPENAI_RESPONSE_MISSING_FIELD:translated_text",
    }
)


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

    SDK 자동 재시도는 꺼두고, 서비스 레이어에서
    PLAY_TRANSLATION에 필요한 오류만 짧게 재시도합니다.
    """

    client = OpenAI(
        api_key=settings.openai_api_key,
        timeout=_openai_play_translation_timeout_seconds(),
        # Spring Boot의 8초 제한을 넘지 않도록
        # SDK 자동 재시도 대신 아래 retry loop만 사용합니다.
        max_retries=0,
    )

    options = _build_response_create_options(request)

    return _generate_openai_translation_with_retry(
        client=client,
        request=request,
        options=options,
        sleep_func=_sleep_before_retry,
    )


def _generate_openai_translation_with_retry(
    client: OpenAI,
    request: PlayTranslationRequest,
    options: dict[str, object],
    sleep_func: Callable[[float], None],
) -> dict[str, str]:
    """
    OpenAI 호출과 응답 파싱을 최대 N회 시도합니다.

    재시도 대상:
    - APITimeoutError
    - APIConnectionError
    - RateLimitError
    - 빈 응답
    - JSON 파싱 실패
    - translated_text 누락 또는 공백
    """

    max_attempts = _openai_play_translation_max_attempts()

    for attempt_number in range(1, max_attempts + 1):
        try:
            response = client.responses.create(**options)

            return _parse_openai_translation(
                response.output_text
            )
        except PlayTranslationGenerationError as exc:
            if not _should_retry_generation_error(
                error=exc,
                attempt_number=attempt_number,
                max_attempts=max_attempts,
            ):
                raise

            _log_play_translation_retry(
                request=request,
                attempt_number=attempt_number,
                max_attempts=max_attempts,
                reason=str(exc),
            )
            sleep_func(_retry_delay_seconds(attempt_number))
        except _retryable_openai_exceptions() as exc:
            if attempt_number >= max_attempts:
                raise

            _log_play_translation_retry(
                request=request,
                attempt_number=attempt_number,
                max_attempts=max_attempts,
                reason=exc.__class__.__name__,
            )
            sleep_func(_retry_delay_seconds(attempt_number))

    # 정상적으로는 for loop 안에서 return 또는 raise 된다.
    raise PlayTranslationGenerationError(
        "OPENAI_GENERATION_FAILED"
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
                "type": "json_schema",
                "name": "play_translation_response",
                "strict": True,
                "schema": {
                    "type": "object",
                    "properties": {
                        "translated_text": {
                            "type": "string",
                            "description": (
                                "MLB Play Result를 한국 야구 중계·기록 "
                                "용어로 번역한 한 문장"
                            ),
                        },
                    },
                    "required": [
                        "translated_text",
                    ],
                    "additionalProperties": False,
                },
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


def _openai_play_translation_timeout_seconds() -> float:
    """
    PLAY_TRANSLATION 1회 OpenAI 호출 timeout을 반환합니다.

    별도 설정값이 없거나 잘못된 경우 기존 openai_timeout_seconds를 사용합니다.
    """

    configured_timeout = getattr(
        settings,
        "openai_play_translation_timeout_seconds",
        None,
    )

    if (
        isinstance(configured_timeout, (int, float))
        and configured_timeout > 0
    ):
        return float(configured_timeout)

    return float(settings.openai_timeout_seconds)


def _openai_play_translation_max_attempts() -> int:
    """
    PLAY_TRANSLATION 최대 시도 횟수를 반환합니다.
    """

    configured_attempts = getattr(
        settings,
        "openai_play_translation_max_attempts",
        1,
    )

    if not isinstance(configured_attempts, int):
        return 1

    return max(1, configured_attempts)


def _should_retry_generation_error(
    error: PlayTranslationGenerationError,
    attempt_number: int,
    max_attempts: int,
) -> bool:
    """
    응답 파싱 계열 업무 오류를 재시도할지 판단합니다.
    """

    if attempt_number >= max_attempts:
        return False

    return str(error) in RETRYABLE_GENERATION_ERROR_CODES


def _retryable_openai_exceptions() -> tuple[type[BaseException], ...]:
    """
    네트워크·timeout·rate limit 계열 재시도 대상 예외입니다.
    """

    return (
        APITimeoutError,
        APIConnectionError,
        RateLimitError,
    )


def _retry_delay_seconds(
    attempt_number: int,
) -> float:
    """
    짧은 exponential backoff + jitter 시간을 계산합니다.
    """

    base_delay = max(
        0.0,
        float(
            getattr(
                settings,
                "openai_play_translation_retry_base_delay_seconds",
                0.0,
            )
        ),
    )
    max_delay = max(
        base_delay,
        float(
            getattr(
                settings,
                "openai_play_translation_retry_max_delay_seconds",
                base_delay,
            )
        ),
    )
    jitter = max(
        0.0,
        float(
            getattr(
                settings,
                "openai_play_translation_retry_jitter_seconds",
                0.0,
            )
        ),
    )

    exponential_delay = base_delay * (2 ** (attempt_number - 1))
    delay = min(max_delay, exponential_delay)

    if jitter:
        delay += random.uniform(0, jitter)

    return delay


def _sleep_before_retry(
    delay_seconds: float,
) -> None:
    """
    재시도 전 짧게 대기합니다.
    """

    if delay_seconds <= 0:
        return

    time.sleep(delay_seconds)


def _log_play_translation_retry(
    request: PlayTranslationRequest,
    attempt_number: int,
    max_attempts: int,
    reason: str,
) -> None:
    """
    PLAY_TRANSLATION 재시도 로그를 남깁니다.

    sourceText, translatedText, contextHash는 로그에 남기지 않습니다.
    """

    logger.warning(
        "OpenAI PLAY_TRANSLATION retrying. "
        "gameId=%s playId=%s mode=%s model=%s "
        "attempt=%s maxAttempts=%s reason=%s",
        request.game_id,
        request.play_id,
        request.mode.value,
        settings.openai_model,
        attempt_number,
        max_attempts,
        reason,
    )


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