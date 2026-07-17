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
from app.prompts.spoiler_free_prompt import build_spoiler_free_prompt
from app.schemas.ai_schema import EventCopyRequest, FinalHeadlineRequest


logger = logging.getLogger(__name__)

# 종료 경기 헤드라인과 이벤트 타임라인 문구가
# 같은 OpenAI 호출·파싱 로직을 재사용할 수 있도록 요청 타입을 묶는다.
AiCopyRequest = FinalHeadlineRequest | EventCopyRequest


# OpenAI 호출 자체는 성공했지만 응답 본문이 불안정한 경우입니다.
# 같은 요청을 짧게 한 번 더 보내면 정상 JSON이 돌아올 수 있으므로
# FINAL_HEADLINE/EVENT_COPY에 한해 서비스 레이어에서 재시도합니다.
RETRYABLE_GENERATION_ERROR_CODES = frozenset(
    {
        "OPENAI_EMPTY_RESPONSE",
        "OPENAI_INVALID_JSON",
        "OPENAI_INVALID_RESPONSE_TYPE",
        "OPENAI_RESPONSE_MISSING_FIELD:safe_title",
    }
)


class SpoilerFreeSummaryGenerationError(RuntimeError):
    """
    AI 문구 생성 실패를 router에서 실패 상태 응답으로 변환하기 위한 예외입니다.

    클래스 이름은 현재 ai_router.py와의 호환성을 위해 유지합니다.
    """


def generate_spoiler_free_summary(
    request: AiCopyRequest,
) -> dict[str, object]:
    """
    종료 경기 헤드라인 또는 이벤트 타임라인용 AI 문구를 생성합니다.

    ai-service는 fallback 기본 문구를 만들지 않습니다.
    OpenAI 호출 실패 또는 응답 형식 오류는 예외로 전달하고,
    router가 spoilerSafe=false 응답으로 변환합니다.
    """

    if not settings.openai_api_key:
        raise SpoilerFreeSummaryGenerationError("OPENAI_API_KEY_MISSING")

    try:
        return _generate_openai_copy(request)
    except SpoilerFreeSummaryGenerationError:
        # 이미 업무용 실패 코드로 변환된 예외는 그대로 router에 전달합니다.
        raise
    except APITimeoutError as exc:
        logger.exception("OpenAI AI-copy generation timed out.")
        raise SpoilerFreeSummaryGenerationError("OPENAI_TIMEOUT") from exc
    except (APIConnectionError, RateLimitError) as exc:
        logger.exception("OpenAI AI-copy generation failed.")
        raise SpoilerFreeSummaryGenerationError(
            "OPENAI_GENERATION_FAILED"
        ) from exc
    except Exception as exc:
        logger.exception("OpenAI AI-copy generation failed.")
        raise SpoilerFreeSummaryGenerationError(
            "OPENAI_GENERATION_FAILED"
        ) from exc


def _generate_openai_copy(
    request: AiCopyRequest,
) -> dict[str, object]:
    """
    OpenAI Responses API를 호출해 AI 문구 후보를 생성합니다.

    FINAL_HEADLINE과 EVENT_COPY 모두 같은 호출 구조를 사용하고,
    endpoint별 세부 지시는 prompt builder가 담당합니다.

    SDK 자동 재시도는 꺼두고, 서비스 레이어에서
    AI-copy에 필요한 오류만 짧게 재시도합니다.
    """

    client = OpenAI(
        api_key=settings.openai_api_key,
        timeout=_openai_ai_copy_timeout_seconds(),
        # OpenAI SDK의 기본 재시도로 인해 Spring Boot의 8초 제한을 넘지 않도록
        # SDK 자동 재시도 대신 아래 retry loop만 사용합니다.
        max_retries=0,
    )

    options = _build_response_create_options(request)

    return _generate_openai_copy_with_retry(
        client=client,
        request=request,
        options=options,
        sleep_func=_sleep_before_retry,
    )


def _generate_openai_copy_with_retry(
    client: OpenAI,
    request: AiCopyRequest,
    options: dict[str, object],
    sleep_func: Callable[[float], None],
) -> dict[str, object]:
    """
    OpenAI 호출과 응답 파싱을 최대 N회 시도합니다.

    재시도 대상:
    - APITimeoutError
    - APIConnectionError
    - RateLimitError
    - 빈 응답
    - JSON 파싱 실패
    - safe_title 누락 또는 공백
    """

    max_attempts = _openai_ai_copy_max_attempts()

    for attempt_number in range(1, max_attempts + 1):
        try:
            response = client.responses.create(**options)

            return _parse_openai_response(response)
        except SpoilerFreeSummaryGenerationError as exc:
            if not _should_retry_generation_error(
                error=exc,
                attempt_number=attempt_number,
                max_attempts=max_attempts,
            ):
                raise

            _log_ai_copy_retry(
                request=request,
                attempt_number=attempt_number,
                max_attempts=max_attempts,
                reason=str(exc),
            )
            sleep_func(_retry_delay_seconds(attempt_number))
        except _retryable_openai_exceptions() as exc:
            if attempt_number >= max_attempts:
                raise

            _log_ai_copy_retry(
                request=request,
                attempt_number=attempt_number,
                max_attempts=max_attempts,
                reason=exc.__class__.__name__,
            )
            sleep_func(_retry_delay_seconds(attempt_number))

    # 정상적으로는 for loop 안에서 return 또는 raise 된다.
    raise SpoilerFreeSummaryGenerationError(
        "OPENAI_GENERATION_FAILED"
    )


def _build_response_create_options(
    request: AiCopyRequest,
) -> dict[str, object]:
    """
    OpenAI Responses API 호출 옵션을 모델 호환성에 맞게 구성합니다.

    gpt-5.6-luna는 temperature 파라미터를 지원하지 않으므로
    해당 모델 요청에서는 temperature 키 자체를 전달하지 않습니다.
    """

    options: dict[str, object] = {
        "model": settings.openai_model,
        "input": build_spoiler_free_prompt(request),
        "max_output_tokens": settings.openai_max_output_tokens,
        "text": {
            "format": {
                "type": "json_schema",
                "name": "ai_copy_response",
                "strict": True,
                "schema": _ai_copy_response_schema(request),
            },
        },
    }

    if _supports_reasoning(settings.openai_model):
        options["reasoning"] = {
            "effort": settings.openai_reasoning_effort,
        }

    if _supports_temperature(settings.openai_model):
        options["temperature"] = settings.openai_temperature

    return options


def _ai_copy_response_schema(
    request: AiCopyRequest,
) -> dict[str, object]:
    """
    요청 목적에 맞는 OpenAI Structured Output JSON Schema를 반환합니다.

    FINAL_HEADLINE:
    - safe_title
    - used_fact_ids
    - used_play_ids

    EVENT_COPY:
    - safe_title
    """

    properties: dict[str, object] = {
        "safe_title": {
            "type": "string",
            "description": (
                "스포일러 정책을 적용한 "
                "FINAL_HEADLINE 또는 EVENT_COPY 한 문장"
            ),
        },
    }
    required = ["safe_title"]

    if isinstance(request, FinalHeadlineRequest):
        properties["used_fact_ids"] = {
            "type": "array",
            "description": (
                "헤드라인 작성에 실제 사용한 summaryFacts 근거 ID 목록"
            ),
            "items": {
                "type": "string",
            },
        }
        properties["used_play_ids"] = {
            "type": "array",
            "description": (
                "헤드라인 작성에 실제 사용한 verifiedPlay playId 목록"
            ),
            "items": {
                "type": "integer",
            },
        }
        required.extend(
            [
                "used_fact_ids",
                "used_play_ids",
            ]
        )

    return {
        "type": "object",
        "properties": properties,
        "required": required,
        "additionalProperties": False,
    }


def _supports_reasoning(model: str) -> bool:
    """
    현재 사용 중인 GPT-5.6 계열 모델의 reasoning 옵션 지원 여부를 반환합니다.
    """

    return model.startswith("gpt-5.6")


def _supports_temperature(model: str) -> bool:
    """
    현재 서비스에서 사용하는 모델의 temperature 지원 여부를 반환합니다.
    """

    return not model.startswith("gpt-5.6-luna")


def _parse_openai_response(
    response: object,
) -> dict[str, object]:
    """
    Responses API 상태를 먼저 검사한 뒤 visible output을 파싱합니다.

    reasoning 중 max_output_tokens 한도에 도달하면 output_text가 비어 있을 수
    있으므로 단순 EMPTY_RESPONSE와 구분해서 업무 오류 코드로 변환합니다.
    """

    status = getattr(response, "status", None)
    incomplete_details = getattr(response, "incomplete_details", None)
    incomplete_reason = getattr(incomplete_details, "reason", None)

    if status == "incomplete":
        error_code_by_reason = {
            "max_output_tokens": "OPENAI_MAX_OUTPUT_TOKENS",
            "content_filter": "OPENAI_CONTENT_FILTER",
        }

        error_code = error_code_by_reason.get(
            incomplete_reason,
            "OPENAI_INCOMPLETE_RESPONSE",
        )

        logger.warning(
            "OpenAI response incomplete. model=%s reason=%s",
            settings.openai_model,
            incomplete_reason,
        )

        raise SpoilerFreeSummaryGenerationError(error_code)

    raw_text = getattr(response, "output_text", "")

    if not isinstance(raw_text, str) or not raw_text.strip():
        logger.warning(
            "OpenAI response has no visible output. model=%s status=%s",
            settings.openai_model,
            status,
        )

    return _parse_openai_copy(raw_text)


def _openai_ai_copy_timeout_seconds() -> float:
    """
    AI-copy 1회 OpenAI 호출 timeout을 반환합니다.

    별도 설정값이 없거나 잘못된 경우 기존 openai_timeout_seconds를 사용합니다.
    """

    configured_timeout = getattr(
        settings,
        "openai_ai_copy_timeout_seconds",
        None,
    )

    if (
        isinstance(configured_timeout, (int, float))
        and configured_timeout > 0
    ):
        return float(configured_timeout)

    return float(settings.openai_timeout_seconds)


def _openai_ai_copy_max_attempts() -> int:
    """
    AI-copy 최대 시도 횟수를 반환합니다.
    """

    configured_attempts = getattr(
        settings,
        "openai_ai_copy_max_attempts",
        1,
    )

    if not isinstance(configured_attempts, int):
        return 1

    return max(1, configured_attempts)


def _should_retry_generation_error(
    error: SpoilerFreeSummaryGenerationError,
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
                "openai_ai_copy_retry_base_delay_seconds",
                0.0,
            )
        ),
    )
    max_delay = max(
        base_delay,
        float(
            getattr(
                settings,
                "openai_ai_copy_retry_max_delay_seconds",
                base_delay,
            )
        ),
    )
    jitter = max(
        0.0,
        float(
            getattr(
                settings,
                "openai_ai_copy_retry_jitter_seconds",
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



def _ai_copy_purpose(
    request: AiCopyRequest,
) -> str:
    """
    요청 schema 타입으로 AI-copy 목적을 구분합니다.

    FinalHeadlineRequest와 EventCopyRequest에는 purpose 필드가 없으므로
    retry 로그에서는 request 타입으로 목적을 계산합니다.
    """

    if isinstance(request, FinalHeadlineRequest):
        return "FINAL_HEADLINE"

    if isinstance(request, EventCopyRequest):
        return "EVENT_COPY"

    return request.__class__.__name__


def _log_ai_copy_retry(
    request: AiCopyRequest,
    attempt_number: int,
    max_attempts: int,
    reason: str,
) -> None:
    """
    AI-copy 재시도 로그를 남깁니다.

    prompt 본문, safe_title, contextHash는 로그에 남기지 않습니다.
    """

    logger.warning(
        "OpenAI AI-copy retrying. "
        "gameId=%s purpose=%s mode=%s model=%s "
        "attempt=%s maxAttempts=%s reason=%s",
        request.game_id,
        _ai_copy_purpose(request),
        request.mode.value,
        settings.openai_model,
        attempt_number,
        max_attempts,
        reason,
    )


def _parse_openai_copy(raw_text: str) -> dict[str, object]:
    """
    OpenAI 응답 문자열을 AI 문구 dict로 변환합니다.

    FINAL_HEADLINE 응답에 evidence ID가 포함되면 타입을 검증해 보존합니다.
    EVENT_COPY는 기존처럼 safe_title만 사용할 수 있습니다.
    """

    if not isinstance(raw_text, str) or not raw_text.strip():
        raise SpoilerFreeSummaryGenerationError("OPENAI_EMPTY_RESPONSE")

    try:
        data = json.loads(raw_text)
    except json.JSONDecodeError as exc:
        raise SpoilerFreeSummaryGenerationError(
            "OPENAI_INVALID_JSON"
        ) from exc

    if not isinstance(data, dict):
        raise SpoilerFreeSummaryGenerationError(
            "OPENAI_INVALID_RESPONSE_TYPE"
        )

    parsed: dict[str, object] = {
        "safe_title": _require_text_field(data, "safe_title"),
    }

    if "used_fact_ids" in data:
        parsed["used_fact_ids"] = _require_string_list_field(
            data,
            "used_fact_ids",
        )

    if "used_play_ids" in data:
        parsed["used_play_ids"] = _require_integer_list_field(
            data,
            "used_play_ids",
        )

    return parsed


def _require_string_list_field(
    data: dict,
    field_name: str,
) -> list[str]:
    """
    OpenAI JSON 응답에서 문자열 배열을 검증해 반환합니다.
    """

    value = data.get(field_name)

    if not isinstance(value, list) or any(
        not isinstance(item, str) or not item.strip()
        for item in value
    ):
        raise SpoilerFreeSummaryGenerationError(
            f"OPENAI_RESPONSE_INVALID_FIELD:{field_name}"
        )

    return [item.strip() for item in value]


def _require_integer_list_field(
    data: dict,
    field_name: str,
) -> list[int]:
    """
    OpenAI JSON 응답에서 양의 정수 배열을 검증해 반환합니다.

    bool은 Python에서 int의 하위 타입이므로 명시적으로 제외합니다.
    """

    value = data.get(field_name)

    if not isinstance(value, list) or any(
        not isinstance(item, int)
        or isinstance(item, bool)
        or item <= 0
        for item in value
    ):
        raise SpoilerFreeSummaryGenerationError(
            f"OPENAI_RESPONSE_INVALID_FIELD:{field_name}"
        )

    return list(value)


def _require_text_field(
    data: dict,
    field_name: str,
) -> str:
    """
    OpenAI JSON 응답에서 비어 있지 않은 문자열 필드를 추출합니다.
    """

    value = data.get(field_name)

    if not isinstance(value, str) or not value.strip():
        raise SpoilerFreeSummaryGenerationError(
            f"OPENAI_RESPONSE_MISSING_FIELD:{field_name}"
        )

    return value.strip()
