import re
from collections import Counter
from dataclasses import dataclass
from typing import Any

from app.prompts.play_translation_prompt import (
    find_matching_event_terms,
    find_matching_positions,
    load_baseball_terms,
)


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


# 선수명 후보를 찾을 때 일반 문맥 토큰으로 제외할 표현입니다.
IGNORED_NAME_TOKENS = frozenset(
    {
        "Top",
        "Bottom",
        "Pitcher",
        "Batter",
        "Runner",
        "Home",
        "Away",
        "Play",
        "Result",
    }
)


# 영문 선수명 후보를 추출합니다.
#
# \b 대신 ASCII lookaround를 사용하므로 "Soto가"처럼
# 영문 이름 바로 뒤에 한글 조사가 붙어도 Soto를 추출할 수 있습니다.
NAME_CANDIDATE_PATTERN = re.compile(
    r"(?<![A-Za-z0-9])"
    r"([A-Z][a-zA-Z'.-]{1,})"
    r"(?![A-Za-z0-9])"
)


# 정수와 소수 형태의 숫자를 비교 대상으로 사용합니다.
NUMBER_TOKEN_PATTERN = re.compile(r"\d+(?:\.\d+)?")


# 영문 원문에서 한국어 숫자 표기로 바뀔 수 있는 표현입니다.
#
# 예:
# - second → 2루
# - two outs → 2아웃
# - third inning → 3회
SOURCE_NUMBER_WORDS = {
    "zero": "0",
    "one": "1",
    "two": "2",
    "three": "3",
    "four": "4",
    "five": "5",
    "six": "6",
    "seven": "7",
    "eight": "8",
    "nine": "9",
    "ten": "10",
    "first": "1",
    "second": "2",
    "third": "3",
    "fourth": "4",
    "fifth": "5",
    "sixth": "6",
    "seventh": "7",
    "eighth": "8",
    "ninth": "9",
    "tenth": "10",
}


# 원문의 타구 방향이 번역문에도 보존됐는지 검사하는 규칙입니다.
#
# 이번 작업에서는 기존 동작과의 호환성을 위해 유지합니다.
# left-center, right-center, 선상 타구 등 세부 방향 검증은
# 후속 작업에서 YAML direction_patterns 기준으로 확장합니다.
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

    프롬프트와 동일한 YAML 용어집을 사용해 원문 이벤트와
    번역문 결과가 일치하는지 검증합니다.

    이 guard는 완전한 자연어 의미 분석기가 아니라,
    MVP에서 명확하게 확인 가능한 사실 보존 위반을
    결정론적 규칙으로 차단합니다.
    """

    violations: list[str] = []

    normalized_source = _normalize_text(source_text)
    normalized_translation = _normalize_text(
        translated_text or ""
    )

    # 프롬프트와 guard가 동일한 이벤트·금지 표현 규칙을
    # 사용하도록 YAML 용어집을 한 번 로드합니다.
    glossary = load_baseball_terms()

    if not normalized_source:
        violations.append("SOURCE_TEXT_EMPTY")

    if not normalized_translation:
        violations.append("TRANSLATED_TEXT_EMPTY")
        return _build_result(violations)

    violations.extend(
        _check_single_sentence(normalized_translation)
    )
    violations.extend(
        _check_player_names_preserved_and_not_added(
            source_text=source_text,
            translated_text=translated_text or "",
        )
    )
    violations.extend(
        _check_numbers_preserved_and_not_added(
            source_text=source_text,
            translated_text=translated_text or "",
            glossary=glossary,
        )
    )
    violations.extend(
        _check_glossary_event_preserved(
            source_text=source_text,
            translated_text=translated_text or "",
            glossary=glossary,
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
    violations.extend(
        _check_forbidden_commentary(
            translated_text=translated_text or "",
            glossary=glossary,
        )
    )

    return _build_result(violations)


def _build_result(
    violations: list[str],
) -> PlayTranslationGuardResult:
    """
    위반 목록으로 guard 결과 객체를 생성합니다.

    여러 검증 규칙이 동일한 violation을 반환할 수 있으므로,
    최초 발견 순서를 유지하면서 중복을 제거합니다.
    """

    unique_violations = list(dict.fromkeys(violations))

    return PlayTranslationGuardResult(
        spoiler_safe=not unique_violations,
        violations=unique_violations,
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


def _check_player_names_preserved_and_not_added(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    원문 선수명이 번역문에 모두 남아 있고,
    원문에 없는 영문 선수명이 추가되지 않았는지 검사합니다.

    이름이 다른 이름으로 바뀐 경우:
    - MISSING_PLAYER_NAME:{원문 이름}
    - ADDED_PLAYER_NAME:{추가 이름}

    두 violation을 함께 반환합니다.
    """

    source_names = _extract_name_candidates(source_text)
    translated_names = _extract_name_candidates(
        translated_text
    )

    source_name_set = set(source_names)
    translated_name_set = set(translated_names)

    violations: list[str] = []

    missing_names = [
        name
        for name in source_names
        if name not in translated_name_set
    ]
    violations.extend(
        f"MISSING_PLAYER_NAME:{name}"
        for name in missing_names
    )

    added_names = [
        name
        for name in translated_names
        if name not in source_name_set
    ]
    violations.extend(
        f"ADDED_PLAYER_NAME:{name}"
        for name in added_names
    )

    return violations


def _extract_name_candidates(
    text: str,
) -> list[str]:
    """
    텍스트에서 영문 선수명 후보를 추출합니다.

    - 일반 문맥 토큰은 제외합니다.
    - RBI, MLB처럼 전부 대문자인 약어는 제외합니다.
    - 최초 등장 순서를 유지하면서 중복을 제거합니다.
    """

    candidates = NAME_CANDIDATE_PATTERN.findall(text)

    filtered_candidates = [
        candidate
        for candidate in candidates
        if candidate not in IGNORED_NAME_TOKENS
        and not candidate.isupper()
    ]

    return list(dict.fromkeys(filtered_candidates))


def _check_numbers_preserved_and_not_added(
    source_text: str,
    translated_text: str,
    glossary: dict[str, Any],
) -> list[str]:
    """
    원문의 명시적 숫자가 번역문에 보존됐는지 검사하고,
    원문이나 야구 용어 규칙으로 설명되지 않는 숫자 추가를 차단합니다.

    허용 예:
    - doubled → 2루타
    - tripled → 3루타
    - stole second → 2루 도루
    - grounded out to third → 3루수 땅볼 아웃

    차단 예:
    - 숫자가 없는 single 원문에 "12구째" 추가
    - pitch 12를 pitch 13으로 변경
    """

    source_number_counts = Counter(
        _extract_number_tokens(source_text)
    )
    translated_number_counts = Counter(
        _extract_number_tokens(translated_text)
    )
    semantically_allowed_numbers = (
        _extract_semantically_allowed_numbers(
            source_text=source_text,
            glossary=glossary,
        )
    )

    violations: list[str] = []

    for number, required_count in (
        source_number_counts.items()
    ):
        translated_count = translated_number_counts[number]

        if translated_count < required_count:
            violations.append(
                f"MISSING_NUMBER:{number}"
            )

    for number, translated_count in (
        translated_number_counts.items()
    ):
        source_count = source_number_counts[number]

        # double→2루타, second→2루처럼 원문 의미가
        # 숫자 표기를 허용하는 경우에는 추가 숫자로 보지 않습니다.
        if number in semantically_allowed_numbers:
            continue

        if translated_count > source_count:
            violations.append(
                f"ADDED_NUMBER:{number}"
            )

    return violations


def _extract_number_tokens(
    text: str,
) -> list[str]:
    """
    텍스트에서 정수·소수 형태의 숫자를 추출합니다.
    """

    return NUMBER_TOKEN_PATTERN.findall(text)


def _extract_semantically_allowed_numbers(
    source_text: str,
    glossary: dict[str, Any],
) -> set[str]:
    """
    원문 의미상 한국어 번역에 등장할 수 있는 숫자를 추출합니다.

    명시적 숫자 외에도 다음 변환을 허용합니다.
    - 영문 수사·서수 → 숫자
    - YAML 이벤트 canonicalKo/requiredKo의 숫자
    - YAML 수비 위치 canonicalKo의 숫자
    """

    allowed_numbers: set[str] = set()
    normalized_source = source_text.lower()

    for source_word, number in SOURCE_NUMBER_WORDS.items():
        source_word_pattern = re.compile(
            rf"(?<![a-z])"
            rf"{re.escape(source_word)}"
            rf"(?![a-z])",
            re.IGNORECASE,
        )

        if source_word_pattern.search(normalized_source):
            allowed_numbers.add(number)

    matched_event_terms = find_matching_event_terms(
        source_text=source_text,
        glossary=glossary,
    )

    for matched_term in matched_event_terms:
        _add_numbers_from_value(
            allowed_numbers=allowed_numbers,
            value=matched_term.get("canonicalKo"),
        )
        _add_numbers_from_value(
            allowed_numbers=allowed_numbers,
            value=matched_term.get("requiredKo", []),
        )

    matched_positions = find_matching_positions(
        source_text=source_text,
        glossary=glossary,
    )

    for matched_position in matched_positions:
        _add_numbers_from_value(
            allowed_numbers=allowed_numbers,
            value=matched_position.get("canonicalKo"),
        )

    return allowed_numbers


def _add_numbers_from_value(
    allowed_numbers: set[str],
    value: Any,
) -> None:
    """
    문자열 또는 문자열 목록에서 숫자를 찾아 허용 집합에 추가합니다.
    """

    if value is None:
        return

    if isinstance(value, (list, tuple, set)):
        for item in value:
            _add_numbers_from_value(
                allowed_numbers=allowed_numbers,
                value=item,
            )
        return

    allowed_numbers.update(
        _extract_number_tokens(str(value))
    )


def _check_glossary_event_preserved(
    source_text: str,
    translated_text: str,
    glossary: dict[str, Any],
) -> list[str]:
    """
    YAML 용어집에서 원문과 매칭된 이벤트의 필수 한국어 표현이
    번역문에 보존됐는지 검사합니다.

    프롬프트와 guard가 같은 event_terms를 사용하도록 하여
    이벤트 규칙이 서로 어긋나는 문제를 방지합니다.

    예:
    - wild pitch → requiredKo=["폭투"]
    - passed ball → requiredKo=["포일"]
    - grounded into double play → requiredKo=["병살"]
    """

    violations: list[str] = []

    matched_event_terms = find_matching_event_terms(
        source_text=source_text,
        glossary=glossary,
    )

    for matched_term in matched_event_terms:
        required_terms = [
            str(required_term).strip()
            for required_term in matched_term.get(
                "requiredKo",
                [],
            )
            if str(required_term).strip()
        ]

        # requiredKo가 모두 보존된 경우 정상입니다.
        if all(
            required_term in translated_text
            for required_term in required_terms
        ):
            continue

        outcome_code = str(
            matched_term.get("outcomeCode")
            or matched_term.get("id")
            or "UNKNOWN"
        ).upper()

        violations.append(
            f"MISSING_EVENT:{outcome_code}"
        )

    return violations


def _check_direction_preserved(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    원문 방향 표현이 번역문에 보존됐는지 검사합니다.
    """

    violations: list[str] = []

    for (
        pattern,
        allowed_terms,
        violation_code,
    ) in DIRECTION_PRESERVATION_RULES:
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

    예:
    - single 원문에 홈런 추가
    - 일반 플레이 원문에 끝내기·득점·승패 추가
    """

    violations: list[str] = []

    for forbidden_word, violation_code in (
        FORBIDDEN_ADDITIONS.items()
    ):
        if forbidden_word not in translated_text:
            continue

        if _source_allows_forbidden_word(
            source_text=source_text,
            forbidden_word=forbidden_word,
        ):
            continue

        violations.append(violation_code)

    return violations


def _check_forbidden_commentary(
    translated_text: str,
    glossary: dict[str, Any],
) -> list[str]:
    """
    YAML global_forbidden_expressions에 등록된 감정·평가·해설 표현이
    번역문에 추가됐는지 검사합니다.

    예:
    - 결정적인
    - 극적인
    - 승리를 이끈
    - 분위기를 바꾼
    """

    violations: list[str] = []

    forbidden_expressions = glossary.get(
        "global_forbidden_expressions",
        [],
    )

    for expression in forbidden_expressions:
        normalized_expression = str(expression).strip()

        if not normalized_expression:
            continue

        if normalized_expression not in translated_text:
            continue

        violations.append(
            f"ADDED_COMMENTARY:{normalized_expression}"
        )

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
        return (
            "walk-off" in source_text_lower
            or "walk off" in source_text_lower
        )

    if forbidden_word == "득점":
        return (
            "scored" in source_text_lower
            or "scores" in source_text_lower
        )

    return False