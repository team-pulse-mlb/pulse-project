import json
import logging
import re
import unicodedata

from openai import APITimeoutError, OpenAI

from app.core.config import settings
from app.prompts.play_translation_prompt import (
    build_play_translation_prompt,
)
from app.schemas.ai_schema import PlayTranslationRequest


logger = logging.getLogger(__name__)


# MLB play 원문에서 선수명 후보로 볼 수 있는
# 영문·악센트 문자·이니셜·복합 성씨를 추출한다.
_NAME_TOKEN = (
    r"(?:[A-Z]\.)+"
    r"|[A-ZÀ-ÖØ-Þ][A-Za-zÀ-ÖØ-öø-ÿ]*"
    r"(?:['’.-][A-Za-zÀ-ÖØ-öø-ÿ]+)*"
    r"|[a-z]+(?:['’][A-Z]|[A-Z])"
    r"[A-Za-zÀ-ÖØ-öø-ÿ'’.-]*"
)

_PLAYER_NAME_PATTERN = re.compile(
    rf"(?<![A-Za-zÀ-ÖØ-öø-ÿ])"
    rf"(?:{_NAME_TOKEN})"
    rf"(?:\s+(?:{_NAME_TOKEN}))*"
)

_NUMBER_PATTERN = re.compile(r"\d+")

# 문장 첫 글자가 대문자여서 이름처럼 보일 수 있는
# 일반 경기 서술어는 선수명 후보에서 제외한다.
_NON_PLAYER_NAME_CANDIDATES = {
    "Batter",
    "Challenge",
    "Defensive",
    "Game",
    "Offensive",
    "Pitching",
    "Replay",
    "Runner",
    "The",
}


def find_play_translation_violations(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    PLAY_TRANSLATION 결과가 원문의 선수명과 숫자를 보존했는지 검사합니다.

    실제 이름이나 원문은 violations에 포함하지 않아
    운영 로그에 원문 일부가 노출되지 않도록 합니다.
    """

    normalized_source = _normalize_preservation_text(
        source_text
    )
    normalized_translation = _normalize_preservation_text(
        translated_text
    )

    violations: list[str] = []

    player_names = _extract_player_name_candidates(
        normalized_source
    )

    if any(
        player_name not in normalized_translation
        for player_name in player_names
    ):
        violations.append(
            "PLAYER_NAME_NOT_PRESERVED"
        )

    source_numbers = set(
        _NUMBER_PATTERN.findall(normalized_source)
    )
    translated_numbers = set(
        _NUMBER_PATTERN.findall(normalized_translation)
    )

    if not source_numbers.issubset(
        translated_numbers
    ):
        violations.append(
            "NUMBER_NOT_PRESERVED"
        )

    return violations


def _extract_player_name_candidates(
    source_text: str,
) -> list[str]:
    """
    영어 MLB play 문장에서 대문자 이름 후보를 순서대로 추출합니다.
    """

    candidates: list[str] = []

    for match in _PLAYER_NAME_PATTERN.finditer(
        source_text
    ):
        candidate = match.group(0).strip()
        first_token = (
            candidate
            .split()[0]
            .rstrip(".")
        )

        if first_token in _NON_PLAYER_NAME_CANDIDATES:
            continue

        if candidate not in candidates:
            candidates.append(candidate)

    return candidates


def _normalize_preservation_text(
    value: str,
) -> str:
    """
    Unicode 조합과 따옴표 차이만 정규화하고,
    선수명의 철자·대소문자는 그대로 검증합니다.
    """

    normalized = unicodedata.normalize(
        "NFC",
        value or "",
    ).replace("’", "'")

    return re.sub(
        r"\s+",
        " ",
        normalized,
    ).strip()


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