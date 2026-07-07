import json


def _resolve_prompt_purpose(request) -> str:
    surface = getattr(request, "surface", "HOME_CARD")

    if hasattr(request, "replay_segment_id"):
        return "REPLAY_SUMMARY"

    if hasattr(request, "channel"):
        return "NOTIFICATION"

    if surface == "REPLAY_CARD":
        return "REPLAY_SUMMARY"

    return "LIVE_HEADLINE"


def _build_purpose_instruction(purpose: str) -> str:
    purpose_instructions = {
        "LIVE_HEADLINE": "경기 카드나 상세 화면에서 사용할 짧은 스포일러 없는 제목과 추천 이유를 생성하세요.",
        "NOTIFICATION": "사용자에게 보낼 짧은 스포일러 없는 알림 문구를 생성하세요.",
        "SWITCH_SUGGESTION": "다른 경기를 확인해볼 만하다는 스포일러 없는 전환 안내 문구를 생성하세요.",
        "REPLAY_SUMMARY": "종료 경기 다시보기 구간에 사용할 스포일러 없는 제목과 요약 문구를 생성하세요.",
    }

    return purpose_instructions.get(
        purpose,
        purpose_instructions["LIVE_HEADLINE"],
    )


def build_spoiler_free_prompt(request) -> str:
    safe_context = request.safe_context
    purpose = _resolve_prompt_purpose(request)

    prompt_context = {
        "purpose": purpose,
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

문구 목적:
{_build_purpose_instruction(purpose)}

반드시 지켜야 할 규칙:
- 점수, 승패, 우세 팀, 특정 선수명, 홈런, 역전, 끝내기, 득점 상황을 말하지 마세요.
- 아래 safe_context에 있는 안전한 정보만 사용하세요.
- recentPlays, teams, startTime, purpose, status 같은 원본 경기 필드는 사용하지 마세요.
- safe_context에 없는 사실을 추측하지 마세요.
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
