FALLBACK_TEXT_BY_PURPOSE = {
    "LIVE_HEADLINE": "지금 볼 만한 흐름이 감지됐습니다.",
    "NOTIFICATION": "지금 볼 만한 흐름의 경기가 감지됐습니다.",
    "SWITCH_SUGGESTION": "다른 경기에서 긴장감이 높아졌습니다.",
    "REPLAY_SUMMARY": "다시 볼 만한 흐름이 이어진 구간입니다.",
}


def get_fallback_text(purpose: str) -> str:
    return FALLBACK_TEXT_BY_PURPOSE.get(
        purpose,
        FALLBACK_TEXT_BY_PURPOSE["LIVE_HEADLINE"],
    )