import json
import logging

from openai import APITimeoutError, OpenAI

from app.core.config import settings
from app.prompts.spoiler_free_prompt import build_spoiler_free_prompt
from app.schemas.ai_schema import EventCopyRequest, FinalHeadlineRequest


logger = logging.getLogger(__name__)

# 종료 경기 헤드라인과 이벤트 타임라인 문구가
# 같은 OpenAI 호출·파싱 로직을 재사용할 수 있도록 요청 타입을 묶는다.
AiCopyRequest = FinalHeadlineRequest | EventCopyRequest


class SpoilerFreeSummaryGenerationError(RuntimeError):
    """
    AI 문구 생성 실패를 router에서 실패 상태 응답으로 변환하기 위한 예외입니다.

    클래스 이름은 현재 ai_router.py와의 호환성을 위해 유지합니다.
    """


def generate_spoiler_free_summary(
    request: AiCopyRequest,
) -> dict[str, str]:
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
    except Exception as exc:
        logger.exception("OpenAI AI-copy generation failed.")
        raise SpoilerFreeSummaryGenerationError(
            "OPENAI_GENERATION_FAILED"
        ) from exc


def _generate_openai_copy(
    request: AiCopyRequest,
) -> dict[str, str]:
    """
    OpenAI Responses API를 호출해 AI 문구 후보를 생성합니다.

    FINAL_HEADLINE과 EVENT_COPY 모두 같은 호출 구조를 사용하고,
    endpoint별 세부 지시는 prompt builder가 담당합니다.
    """

    client = OpenAI(
        api_key=settings.openai_api_key,
        timeout=settings.openai_timeout_seconds,
        # OpenAI SDK의 기본 재시도로 인해 Spring Boot의 8초 제한을 넘지 않도록
        # ai-service 내부 자동 재시도는 사용하지 않습니다.
        max_retries=0,
    )

    response = client.responses.create(
        **_build_response_create_options(request)
    )

    return _parse_openai_copy(response.output_text)


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
                "type": "json_object",
            },
        },
    }

    if _supports_temperature(settings.openai_model):
        options["temperature"] = settings.openai_temperature

    return options


def _supports_temperature(model: str) -> bool:
    """
    현재 서비스에서 사용하는 모델의 temperature 지원 여부를 반환합니다.
    """

    return not model.startswith("gpt-5.6-luna")


def _parse_openai_copy(raw_text: str) -> dict[str, str]:
    """
    OpenAI 응답 문자열을 AI 문구 dict로 변환합니다.

    현재 FINAL_HEADLINE과 EVENT_COPY가 공통으로 사용하는 필드는
    safe_title 하나입니다.

    필수 문구가 없으면 ai-service가 fallback을 생성하지 않고
    실패 코드를 router로 전달합니다.
    """

    # OpenAI 응답 본문이 없으면 JSON 파싱 오류와 구분되는
    # 명확한 실패 상태로 처리합니다.
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

    # FINAL_HEADLINE과 EVENT_COPY 모두 router에서
    # safe_title을 최종 저장 후보 문구로 사용합니다.
    safe_title = _require_text_field(data, "safe_title")

    return {
        "safe_title": safe_title,
    }


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
