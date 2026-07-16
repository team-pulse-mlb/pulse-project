import json
from typing import Any

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


def _dump_context_model(value: Any) -> dict | None:
    """
    Pydantic 모델을 Spring/프롬프트 계약의 camelCase dict로 변환합니다.
    """

    if value is None:
        return None

    return value.model_dump(
        by_alias=True,
        exclude_none=True,
    )


def _dump_context_model_list(values: list[Any]) -> list[dict]:
    """
    Pydantic 모델 리스트를 camelCase dict 리스트로 변환합니다.
    """

    return [
        value.model_dump(
            by_alias=True,
            exclude_none=True,
        )
        for value in values
    ]


def _build_final_headline_context(
    request: FinalHeadlineRequest,
) -> dict:
    """
    종료 경기 헤드라인 생성에 필요한 필드만 추출합니다.

    PROTECTED에서는 점수와 승패를 제외하고,
    REVEALED에서만 Spring Boot가 검증해 전달한 결과/이벤트/play 근거를 포함합니다.
    """

    safe_context = request.safe_context

    prompt_context = {
        "gameStatus": safe_context.game_status,
        "status": safe_context.status,
        "inningPhase": safe_context.inning_phase,
        "periodLabel": safe_context.period_label,
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
        # 공개 모드에서도 Spring Boot가 실제로 전달한 검증 근거만 프롬프트에 포함합니다.
        prompt_context.update(
            {
                "finalScore": _dump_context_model(safe_context.final_score),
                "winner": safe_context.winner,
                "teams": _dump_context_model(safe_context.teams),
                "inningsPlayed": safe_context.innings_played,
                "extraInnings": safe_context.extra_innings,
                "postseason": safe_context.postseason,
                "venue": safe_context.venue,
                "startTime": safe_context.start_time,
                "homeInningScores": safe_context.home_inning_scores,
                "awayInningScores": safe_context.away_inning_scores,
                "summaryFacts": _dump_context_model(safe_context.summary_facts),
                "revealedEvents": _dump_context_model_list(
                    safe_context.revealed_events
                ),
                "revealedMoments": _dump_context_model_list(
                    safe_context.revealed_moments
                ),
                "verifiedPlays": _dump_context_model_list(
                    safe_context.verified_plays
                ),
            }
        )

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

    PROTECTED에서는 보호 라벨, 이닝 숫자, 보호 가능한 상황 근거만 사용하고,
    REVEALED에서만 초·말, 선수명, 제한된 evidence를 포함합니다.
    """

    safe_context = request.safe_context

    prompt_context = {
        "eventType": safe_context.event_type,
        "label": safe_context.label,
        "inning": safe_context.inning,
    }

    if request.mode == AiCopyMode.PROTECTED:
        # PROTECTED EVENT_COPY에서는 점수·결과가 아닌
        # 보호 라벨 묶음과 상황 근거만 추가로 사용합니다.
        prompt_context.update(
            {
                "contributingLabels": (
                    safe_context.contributing_labels
                ),
                "situation": safe_context.situation,
            }
        )

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
            "safeContext의 경기 흐름, keyMoments, summaryFacts, "
            "revealedEvents, revealedMoments, verifiedPlays만 사용하세요."
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


def _build_mode_instruction(
    mode: AiCopyMode,
    purpose: str,
) -> str:
    """
    보호·공개 모드별 스포일러 제한 지시문을 반환합니다.

    EVENT_COPY는 경기 상세 타임라인의 "N회" 헤더 아래에 표시되므로,
    PROTECTED EVENT_COPY에만 이닝/회차 문구 금지와 문체 예시를 추가합니다.
    """

    if mode == AiCopyMode.PROTECTED:
        protected_instruction = """
- 점수, 점수 차, 승패, 승자, 패자, 리드 팀을 언급하지 마세요.
- 홈런, 역전, 끝내기, 득점 결과처럼 경기 결과를 직접 드러내는 표현을 사용하지 마세요.
- 어느 팀이 유리하거나 불리했는지 추측하지 마세요.
- 이닝 숫자와 safeContext의 보호 라벨은 사용할 수 있습니다.
- 이닝 초/말, 선수명, 원본 play 내용은 생성하거나 추측하지 마세요.
""".strip()

        if purpose == "EVENT_COPY":
            return f"""
{protected_instruction}

EVENT_COPY PROTECTED 추가 규칙:
- 화면에서 이미 "N회" 헤더를 표시하므로 safe_title에는 이닝 숫자, "회", "초", "말"을 쓰지 마세요.
- "~합니다"체로 작성하세요.
- 반드시 한 문장으로 작성하세요.
- "긴장감", "흐름", "승부처" 같은 상투 표현을 반복하지 마세요.
- contributingLabels와 situation에 있는 카운트·아웃·주자 정보만 조합하세요.
- situation에 없는 타석 결과, 득점 결과, 팀 유불리는 만들지 마세요.

EVENT_COPY PROTECTED 예시:
- label="승부처 카운트", contributingLabels=["만루 승부", "승부처 카운트"], situation={{"outs": 2, "balls": 3, "strikes": 2, "runnerOnFirst": true, "runnerOnSecond": true, "runnerOnThird": true}}
  → {{"safe_title": "2사 만루에서 풀카운트 승부가 이어졌습니다."}}
- label="득점권 압박", situation={{"outs": 1, "runnerOnSecond": true, "runnerOnThird": false}}
  → {{"safe_title": "1사 2루 상황에서 집중이 필요한 승부가 이어졌습니다."}}
- label="긴 승부", situation={{"pitchNumber": 8}}
  → {{"safe_title": "긴 타석 승부가 계속 이어졌습니다."}}
""".strip()

        return protected_instruction

    if mode == AiCopyMode.REVEALED:
        return """
- safeContext에 실제로 포함된 점수, 승패, 팀명, 선수명, 이벤트 근거, play 근거만 사용할 수 있습니다.
- safeContext에 없는 팀명, 선수명, 점수, 경기 결과, 타석 결과를 추측하지 마세요.
- FINAL_HEADLINE REVEALED에서는 teams, summaryFacts, revealedEvents, revealedMoments, verifiedPlays에 있는 사실을 우선 사용하세요.
- summaryFacts의 winnerName, loserName, winnerScore, loserScore는 최종 결과 문장의 기준입니다.
- verifiedPlays의 translatedText가 있으면 sourceText보다 translatedText를 우선 사용하세요.
- verifiedPlays와 revealedEvents에 없는 홈런, 역전, 끝내기, 득점 결과는 만들어내지 마세요.
- finalScore나 winner가 없으면 점수 또는 승패를 언급하지 마세요.
- FINAL_HEADLINE에서 점수를 언급할 때는 반드시 finalScore.home-finalScore.away 형식만 사용하세요.
- 점수 순서는 승자와 관계없이 항상 홈팀 점수-원정팀 점수 순서입니다.
- "5점", "5점 차", 한 팀 점수만 있는 표현, "5:3", "5대3" 형식은 사용하지 마세요.
- 승패를 언급할 때는 승자 한 팀만 문장의 주어로 사용하세요.
- 승자 주어는 반드시 "홈팀", "원정팀", teams.home.name, teams.home.abbr, teams.away.name, teams.away.abbr 중 하나를 그대로 사용하세요.
- 가장 안정적인 표현은 "홈팀이 ..." 또는 "원정팀이 ..."입니다.
- safeContext의 영문 팀명을 한국어로 번역·음역·축약하거나 별칭으로 바꾸지 마세요.
- 예를 들어 teams.home.name이 "Los Angeles Dodgers"여도 "다저스"를 새로 만들어 사용하지 마세요.
- 팀 주어 없이 "5-3 승리", "홈런으로 승리"처럼 작성하지 마세요.
- 홈팀 승리 예시는 "홈팀이 5-3으로 승리"입니다.
- 원정팀 승리 예시는 "원정팀이 3-5로 승리"입니다.
- 한 문장 안에서 홈팀과 원정팀을 모두 승자로 표현하지 마세요.
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
{_build_mode_instruction(request.mode, purpose)}

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