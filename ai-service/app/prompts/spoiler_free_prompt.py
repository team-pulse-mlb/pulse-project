import json

from app.schemas.ai_schema import (
    AiCopyMode,
    EventCopyRequest,
    FinalHeadlineRequest,
)

PROMPT_LANGUAGE = "ko"
MAX_SAFE_TITLE_LENGTH = 80


# 종료 경기 헤드라인과 이벤트 타임라인 문구가 같은 prompt builder를 사용하므로
# 두 요청 모델을 공통 타입으로 묶는다.
AiCopyRequest = FinalHeadlineRequest | EventCopyRequest


def _resolve_prompt_purpose(request: AiCopyRequest) -> str:
    """
    요청 모델의 실제 타입으로 AI 문구 목적을 결정합니다.
    """

    if isinstance(request, FinalHeadlineRequest):
        return "FINAL_HEADLINE"

    if isinstance(request, EventCopyRequest):
        return "EVENT_COPY"

    # 지원하지 않는 요청을 임의의 기본 purpose로 처리하지 않고 즉시 실패시킵니다.
    raise TypeError(
        f"Unsupported AI copy request type: {type(request).__name__}"
    )


def _build_final_headline_context(
    request: FinalHeadlineRequest,
) -> dict:
    """
    종료 경기 헤드라인 생성에 필요한 필드만 추출합니다.

    PROTECTED에서는 점수와 승패를 제외하고,
    REVEALED에서만 finalScore와 winner를 포함합니다.
    """

    safe_context = request.safe_context

    prompt_context = {
        "gameStatus": safe_context.game_status,
        "inningPhase": safe_context.inning_phase,
        "tensionLevel": safe_context.tension_level,
        "scoreBand": safe_context.score_band,
        "safeTags": safe_context.safe_tags,
        "reasonCodes": safe_context.reason_codes,
        "keyMoments": [
            key_moment.model_dump(
                by_alias=True,
                exclude_none=True,
            )
            for key_moment in safe_context.key_moments
        ],
    }

    if request.mode == AiCopyMode.REVEALED:
        # 공개 모드에서도 Spring Boot가 실제로 전달한 결과만 프롬프트에 포함합니다.
        if safe_context.final_score is not None:
            prompt_context["finalScore"] = (
                safe_context.final_score.model_dump(
                    by_alias=True,
                    exclude_none=True,
                )
            )

        if safe_context.winner is not None:
            prompt_context["winner"] = safe_context.winner

    # 값이 없는 선택 필드는 프롬프트에서 제외해 모델이 null의 의미를 추측하지 않게 합니다.
    return {
        key: value
        for key, value in prompt_context.items()
        if value is not None
    }


def _build_event_copy_context(
    request: EventCopyRequest,
) -> dict:
    """
    이벤트 타임라인 문구 생성에 필요한 필드만 추출합니다.

    PROTECTED에서는 보호 라벨과 이닝 숫자만 사용하고,
    REVEALED에서만 초·말, 선수명, 제한된 evidence를 포함합니다.
    """

    safe_context = request.safe_context

    prompt_context = {
        "eventType": safe_context.event_type,
        "label": safe_context.label,
        "inning": safe_context.inning,
    }

    if request.mode == AiCopyMode.REVEALED:
        # 공개 모드 전용 정보도 safeContext에 실제로 들어온 값만 사용합니다.
        prompt_context.update(
            {
                "inningType": safe_context.inning_type,
                "batter": safe_context.batter,
                "pitcher": safe_context.pitcher,
                "evidence": safe_context.evidence,
            }
        )

    return {
        key: value
        for key, value in prompt_context.items()
        if value is not None
    }


def _build_safe_context(request: AiCopyRequest) -> dict:
    """
    endpoint와 mode에 맞는 safeContext 화이트리스트를 적용합니다.
    """

    if isinstance(request, FinalHeadlineRequest):
        return _build_final_headline_context(request)

    if isinstance(request, EventCopyRequest):
        return _build_event_copy_context(request)

    raise TypeError(
        f"Unsupported AI copy request type: {type(request).__name__}"
    )


def _build_purpose_instruction(purpose: str) -> str:
    """
    AI 문구 목적별 생성 지시문을 반환합니다.
    """

    purpose_instructions = {
        "FINAL_HEADLINE": (
            "종료된 경기의 카드와 상세 화면에 표시할 "
            "한 문장의 짧은 헤드라인을 생성하세요. "
            "safeContext의 경기 흐름과 keyMoments만 사용하세요."
        ),
        "EVENT_COPY": (
            "경기 이벤트 타임라인에 표시할 "
            "한 문장의 짧은 이벤트 문구를 생성하세요. "
            "safeContext의 이벤트 정보만 사용하세요."
        ),
    }

    try:
        return purpose_instructions[purpose]
    except KeyError as exc:
        raise ValueError(
            f"Unsupported AI copy purpose: {purpose}"
        ) from exc


def _build_mode_instruction(mode: AiCopyMode) -> str:
    """
    보호·공개 모드별 스포일러 제한 지시문을 반환합니다.
    """

    if mode == AiCopyMode.PROTECTED:
        return """
- 점수, 점수 차, 승패, 승자, 패자, 리드 팀을 언급하지 마세요.
- 홈런, 역전, 끝내기, 득점 결과처럼 경기 결과를 직접 드러내는 표현을 사용하지 마세요.
- 어느 팀이 유리하거나 불리했는지 추측하지 마세요.
- 이닝 숫자와 safeContext의 보호 라벨은 사용할 수 있습니다.
- 이닝 초/말, 선수명, 원본 play 내용은 생성하거나 추측하지 마세요.
""".strip()

    if mode == AiCopyMode.REVEALED:
        return """
- safeContext에 실제로 포함된 점수, 승패, 선수명, 이벤트 근거만 사용할 수 있습니다.
- safeContext에 없는 팀명, 선수명, 점수, 경기 결과를 추측하지 마세요.
- finalScore나 winner가 없으면 점수 또는 승패를 언급하지 마세요.
- evidence에 없는 타석 결과나 득점 결과를 만들어내지 마세요.
""".strip()

    raise ValueError(f"Unsupported AI copy mode: {mode}")


def build_spoiler_free_prompt(request: AiCopyRequest) -> str:
    """
    FINAL_HEADLINE 또는 EVENT_COPY 생성용 프롬프트를 만듭니다.

    함수 이름은 현재 openai_service.py와의 호환성을 위해 유지합니다.
    """

    purpose = _resolve_prompt_purpose(request)
    safe_context = _build_safe_context(request)

    prompt_payload = {
        "purpose": purpose,
        "mode": request.mode.value,
        "language": PROMPT_LANGUAGE,
        "maxLength": MAX_SAFE_TITLE_LENGTH,
        "safeContext": safe_context,
    }

    return f"""
너는 MLB 경기 관전 타이밍 서비스의 AI 문구 생성기입니다.

문구 목적:
{_build_purpose_instruction(purpose)}

현재 모드:
{request.mode.value}

모드별 규칙:
{_build_mode_instruction(request.mode)}

공통 규칙:
- 아래 JSON의 safeContext에 실제로 포함된 정보만 사용하세요.
- safeContext에 없는 사실은 추측하거나 만들어내지 마세요.
- 내부 추천 점수나 계산 수치를 문구에 포함하지 마세요.
- 문구는 {MAX_SAFE_TITLE_LENGTH}자 이내의 한 문장으로 작성하세요.
- 요청 언어는 {PROMPT_LANGUAGE}입니다.
- 설명, 마크다운, 코드 블록 없이 JSON 객체 하나만 반환하세요.
- 반환 JSON에는 safe_title 필드 하나만 포함하세요.
- safe_title은 빈 문자열이면 안 됩니다.

반환 형식:
{{
  "safe_title": "생성한 한 문장"
}}

요청 데이터:
{json.dumps(prompt_payload, ensure_ascii=False, indent=2)}
""".strip()