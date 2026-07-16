import re
from dataclasses import dataclass


@dataclass(frozen=True)
class PlayTranslationGuardResult:
    """
    PLAY_TRANSLATION 번역 검수 결과입니다.

    spoiler_safe:
    - 번역문이 원문 사실 보존 규칙을 통과했는지 여부입니다.

    violations:
    - Spring Boot가 저장 여부를 판단할 수 있는 위반 코드 목록입니다.
    """

    spoiler_safe: bool
    violations: list[str]


# 원문에 없는데 번역문에 추가되면 안 되는 결과 암시 표현입니다.
FORBIDDEN_ADDITIONS = {
    "홈런": "ADDED_RESULT:HOME_RUN",
    "역전": "ADDED_RESULT:COME_BACK",
    "끝내기": "ADDED_RESULT:WALK_OFF",
    "승리": "ADDED_RESULT:WIN",
    "패배": "ADDED_RESULT:LOSS",
    "득점": "ADDED_RESULT:SCORE",
    "실점": "ADDED_RESULT:RUN_ALLOWED",
    "리드": "ADDED_RESULT:LEAD",
}


# 원문 이벤트 표현과 번역문에 반드시 남아야 하는 한국어 핵심 표현입니다.
EVENT_PRESERVATION_RULES = [
    (
        re.compile(r"\bstruck out swinging\b", re.IGNORECASE),
        ["헛스윙", "삼진"],
        "MISSING_EVENT:STRIKEOUT_SWINGING",
    ),
    (
        re.compile(r"\bstruck out looking\b", re.IGNORECASE),
        ["루킹", "삼진"],
        "MISSING_EVENT:STRIKEOUT_LOOKING",
    ),
    (
        re.compile(r"\bstruck out\b", re.IGNORECASE),
        ["삼진"],
        "MISSING_EVENT:STRIKEOUT",
    ),
    (
        re.compile(r"\bwalked\b", re.IGNORECASE),
        ["볼넷"],
        "MISSING_EVENT:WALK",
    ),
    (
        re.compile(r"\bsingled\b", re.IGNORECASE),
        ["안타"],
        "MISSING_EVENT:SINGLE",
    ),
    (
        re.compile(r"\bdoubled\b", re.IGNORECASE),
        ["2루타"],
        "MISSING_EVENT:DOUBLE",
    ),
    (
        re.compile(r"\btripled\b", re.IGNORECASE),
        ["3루타"],
        "MISSING_EVENT:TRIPLE",
    ),
    (
        re.compile(r"\bhomered\b|\bhome run\b", re.IGNORECASE),
        ["홈런"],
        "MISSING_EVENT:HOME_RUN",
    ),
    (
        re.compile(r"\bgrounded out\b", re.IGNORECASE),
        ["땅볼", "아웃"],
        "MISSING_EVENT:GROUNDOUT",
    ),
    (
        re.compile(r"\bflied out\b", re.IGNORECASE),
        ["뜬공", "아웃"],
        "MISSING_EVENT:FLYOUT",
    ),
    (
        re.compile(r"\blined out\b", re.IGNORECASE),
        ["직선타", "아웃"],
        "MISSING_EVENT:LINEOUT",
    ),
    (
        re.compile(r"\bpopped out\b", re.IGNORECASE),
        ["뜬공", "아웃"],
        "MISSING_EVENT:POPOUT",
    ),
    (
        re.compile(r"\bsacrifice fly\b", re.IGNORECASE),
        ["희생플라이"],
        "MISSING_EVENT:SACRIFICE_FLY",
    ),
    (
        re.compile(r"\bhit by pitch\b", re.IGNORECASE),
        ["몸에 맞는 공"],
        "MISSING_EVENT:HIT_BY_PITCH",
    ),
    (
        re.compile(r"\bstole\b", re.IGNORECASE),
        ["도루"],
        "MISSING_EVENT:STOLEN_BASE",
    ),
]


DIRECTION_PRESERVATION_RULES = [
    (
        re.compile(r"\bto left\b", re.IGNORECASE),
        ["좌익", "왼쪽", "레프트"],
        "MISSING_DIRECTION:LEFT",
    ),
    (
        re.compile(r"\bto center\b", re.IGNORECASE),
        ["중견", "가운데", "센터"],
        "MISSING_DIRECTION:CENTER",
    ),
    (
        re.compile(r"\bto right\b", re.IGNORECASE),
        ["우익", "오른쪽", "라이트"],
        "MISSING_DIRECTION:RIGHT",
    ),
]


def check_play_translation(
    source_text: str,
    translated_text: str | None,
) -> PlayTranslationGuardResult:
    """
    단일 MLB Play Result 번역문이 원문 사실을 보존했는지 검사합니다.

    이 guard는 완전한 의미론 검증기가 아니라,
    MVP에서 반드시 막아야 하는 명확한 위반을 규칙 기반으로 차단합니다.
    """

    violations: list[str] = []

    normalized_source = _normalize_text(source_text)
    normalized_translation = _normalize_text(translated_text or "")

    if not normalized_source:
        violations.append("SOURCE_TEXT_EMPTY")

    if not normalized_translation:
        violations.append("TRANSLATED_TEXT_EMPTY")
        return _build_result(violations)

    violations.extend(
        _check_single_sentence(normalized_translation)
    )
    violations.extend(
        _check_player_names_preserved(
            source_text=source_text,
            translated_text=translated_text or "",
        )
    )
    violations.extend(
        _check_numbers_preserved(
            source_text=source_text,
            translated_text=translated_text or "",
        )
    )
    violations.extend(
        _check_event_preserved(
            source_text=source_text,
            translated_text=translated_text or "",
        )
    )
    violations.extend(
        _check_direction_preserved(
            source_text=source_text,
            translated_text=translated_text or "",
        )
    )
    violations.extend(
        _check_forbidden_additions(
            source_text=source_text,
            translated_text=translated_text or "",
        )
    )

    return _build_result(violations)


def _build_result(
    violations: list[str],
) -> PlayTranslationGuardResult:
    """
    위반 목록으로 guard 결과 객체를 생성합니다.
    """

    return PlayTranslationGuardResult(
        spoiler_safe=not violations,
        violations=violations,
    )


def _normalize_text(text: str) -> str:
    """
    비교용 문자열을 정규화합니다.
    """

    return re.sub(
        r"\s+",
        " ",
        str(text).strip(),
    )


def _check_single_sentence(
    translated_text: str,
) -> list[str]:
    """
    번역문이 한 문장인지 검사합니다.
    """

    sentence_end_count = len(
        re.findall(r"[.!?。！？]", translated_text)
    )

    if sentence_end_count > 1:
        return ["MULTIPLE_SENTENCES"]

    return []


def _check_player_names_preserved(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    원문에 등장한 선수명 후보가 번역문에도 남아 있는지 검사합니다.

    MLB play result는 보통 문장 첫 토큰이 선수 성(last name)인 경우가 많으므로,
    대문자로 시작하는 영문 토큰을 선수명 후보로 본다.
    """

    source_names = _extract_name_candidates(source_text)

    missing_names = [
        name
        for name in source_names
        if name not in translated_text
    ]

    if missing_names:
        return [
            f"MISSING_PLAYER_NAME:{name}"
            for name in missing_names
        ]

    return []


def _extract_name_candidates(
    source_text: str,
) -> list[str]:
    """
    원문에서 선수명 후보를 추출합니다.
    """

    candidates = re.findall(
        r"\b[A-Z][a-zA-Z'.-]{1,}\b",
        source_text,
    )

    ignored_words = {
        "Top",
        "Bottom",
        "Pitcher",
        "Batter",
    }

    return [
        candidate
        for candidate in candidates
        if candidate not in ignored_words
    ]


def _check_numbers_preserved(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    원문에 등장한 숫자가 번역문에도 유지되는지 검사합니다.
    """

    source_numbers = re.findall(r"\d+", source_text)

    missing_numbers = [
        number
        for number in source_numbers
        if number not in translated_text
    ]

    if missing_numbers:
        return [
            f"MISSING_NUMBER:{number}"
            for number in missing_numbers
        ]

    return []


def _check_event_preserved(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    원문 야구 결과 표현이 번역문에 보존됐는지 검사합니다.
    """

    violations: list[str] = []

    for pattern, required_terms, violation_code in EVENT_PRESERVATION_RULES:
        if not pattern.search(source_text):
            continue

        if not all(
            required_term in translated_text
            for required_term in required_terms
        ):
            violations.append(violation_code)

    return violations


def _check_direction_preserved(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    원문 방향 표현이 번역문에 보존됐는지 검사합니다.
    """

    violations: list[str] = []

    for pattern, allowed_terms, violation_code in DIRECTION_PRESERVATION_RULES:
        if not pattern.search(source_text):
            continue

        if not any(
            allowed_term in translated_text
            for allowed_term in allowed_terms
        ):
            violations.append(violation_code)

    return violations


def _check_forbidden_additions(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    원문에 없는 위험 결과 표현이 번역문에 추가됐는지 검사합니다.
    """

    violations: list[str] = []

    for forbidden_word, violation_code in FORBIDDEN_ADDITIONS.items():
        if forbidden_word not in translated_text:
            continue

        if _source_allows_forbidden_word(
            source_text=source_text,
            forbidden_word=forbidden_word,
        ):
            continue

        violations.append(violation_code)

    return violations


def _source_allows_forbidden_word(
    source_text: str,
    forbidden_word: str,
) -> bool:
    """
    원문 이벤트가 해당 한국어 결과 표현을 허용하는지 판단합니다.
    """

    source_text_lower = source_text.lower()

    if forbidden_word == "홈런":
        return (
            "home run" in source_text_lower
            or "homered" in source_text_lower
        )

    if forbidden_word == "끝내기":
        return "walk-off" in source_text_lower

    if forbidden_word == "득점":
        return (
            "scored" in source_text_lower
            or "scores" in source_text_lower
        )

    return False