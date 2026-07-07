import json
import logging

from app.core.config import settings
from app.schemas.ai_schema import (
    NotificationTextRequest,
    ReplaySummaryRequest,
    SpoilerFreeSummaryRequest,
)

logger = logging.getLogger(__name__)

# 세 endpoint가 같은 문구 생성 함수를 재사용할 수 있게 요청 타입을 묶어둔다.
AiTextRequest = (
    SpoilerFreeSummaryRequest
    | NotificationTextRequest
    | ReplaySummaryRequest
)


def generate_spoiler_free_summary(request: AiTextRequest) -> dict:
    """
    스포일러 없는 AI 문구를 생성한다.

    처리 흐름:
    - OPENAI_API_KEY가 없으면 mock 문구를 반환한다.
    - OPENAI_API_KEY가 있으면 OpenAI API 호출을 시도한다.
    - OpenAI 호출이 실패하면 서버 오류 대신 mock 문구로 fallback 한다.
    """

    if not settings.openai_api_key:
        return _generate_mock_spoiler_free_summary(request)

    try:
        return _generate_openai_spoiler_free_summary(request)
    except Exception:
        logger.exception("OpenAI summary generation failed. Using mock fallback.")
        return _generate_mock_spoiler_free_summary(request)


def _generate_openai_spoiler_free_summary(request: AiTextRequest) -> dict:
    """
    실제 OpenAI API를 호출해서 스포일러 없는 문구를 생성한다.

    이 함수는 OpenAI 호출만 담당하고,
    응답 검증과 fallback 처리는 _parse_openai_summary()에 맡긴다.
    """
    from openai import OpenAI

    client = OpenAI(api_key=settings.openai_api_key)

    response = client.responses.create(
        model=settings.openai_model,
        input=_build_spoiler_free_prompt(request),
        temperature=settings.openai_temperature,
        max_output_tokens=settings.openai_max_output_tokens,
        text={"format": {"type": "json_object"}},
    )

    return _parse_openai_summary(response.output_text, request)


def _build_spoiler_free_prompt(request: AiTextRequest) -> str:
    """
    OpenAI에게 전달할 프롬프트를 만든다.

    safe_context에 있는 정보만 사용하게 하고,
    점수/승패/선수명/홈런/역전/끝내기 같은 스포일러 표현을 금지한다.
    """
    safe_context = request.safe_context

    prompt_context = {
        "game_status": safe_context.game_status,
        "inning_phase": safe_context.inning_phase,
        "tension_level": safe_context.tension_level,
        "score_band": safe_context.score_band,
        "safe_tags": safe_context.safe_tags,
        "reason_codes": safe_context.reason_codes,
        "language": getattr(request, "language", "ko"),
        "max_length": getattr(request, "max_length", 80),
    }

    return f"""
너는 MLB 경기 추천 서비스의 스포일러 방지 문구 생성기입니다.

반드시 지켜야 할 규칙:
- 점수, 승패, 우세 팀, 특정 선수명, 홈런, 역전, 끝내기, 득점 상황을 말하지 마세요.
- 아래 safe_context에 있는 안전한 정보만 사용하세요.
- 한국어로 작성하세요.
- 반드시 JSON만 반환하세요.

반환 형식:
{{
  "safe_title": "스포일러 없는 제목",
  "safe_reason": "스포일러 없는 추천 이유",
  "notification_text": "스포일러 없는 알림 문구",
  "tags": ["태그1", "태그2"]
}}

safe_context:
{json.dumps(prompt_context, ensure_ascii=False)}
""".strip()


def _parse_openai_summary(raw_text: str, request: AiTextRequest) -> dict:
    """
    OpenAI 응답 문자열을 dict로 변환한다.

    JSON 파싱 실패 또는 필드 누락이 있으면
    기존 mock 문구를 사용해서 endpoint 응답이 깨지지 않게 한다.
    """
    fallback = _generate_mock_spoiler_free_summary(request)

    try:
        data = json.loads(raw_text)
    except json.JSONDecodeError:
        logger.warning("OpenAI response was not valid JSON. Using mock fallback.")
        return fallback

    tags = data.get("tags")
    if not isinstance(tags, list):
        tags = fallback["tags"]

    return {
        "safe_title": str(data.get("safe_title") or fallback["safe_title"]),
        "safe_reason": str(data.get("safe_reason") or fallback["safe_reason"]),
        "notification_text": str(
            data.get("notification_text") or fallback["notification_text"]
        ),
        "tags": tags,
    }


def _generate_mock_spoiler_free_summary(
    request: AiTextRequest,
) -> dict:
    """
    OpenAI API 연결 전 또는 OpenAI 호출 실패 시 사용할 mock 문구를 생성한다.

    역할:
    - safe_context만 사용해서 스포일러 없는 문구를 만든다.
    - 점수, 승패, 우세 팀 같은 정보는 사용하지 않는다.
    - Swagger 테스트와 Spring Boot 연동 테스트가 계속 가능하게 한다.
    """

    safe_context = request.safe_context

    tags = safe_context.safe_tags or ["추천 구간"]

    if safe_context.game_status == "UPCOMING":
        safe_title = "경기 전 확인해볼 만한 매치업"
    elif safe_context.game_status == "FINAL":
        safe_title = "다시 볼 만한 흐름이 있는 경기"
    elif safe_context.inning_phase in ["LATE", "EXTRA"]:
        safe_title = "후반부 긴장감이 올라간 경기"
    else:
        safe_title = "관전 가치가 높아진 경기"

    if safe_context.tension_level == "HIGH":
        safe_reason = "지금 확인해볼 만한 흐름이 감지됐습니다."
    elif safe_context.tension_level == "NORMAL":
        safe_reason = "경기 흐름을 지켜볼 만한 구간입니다."
    else:
        safe_reason = "부담 없이 확인해볼 수 있는 경기입니다."

    notification_text = "관심 경기에서 볼 만한 흐름이 감지됐어요."

    return {
        "safe_title": safe_title,
        "safe_reason": safe_reason,
        "notification_text": notification_text,
        "tags": tags,
    }