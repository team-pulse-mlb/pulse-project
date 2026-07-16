import re
from dataclasses import dataclass
from typing import Any

from app.prompts.play_translation_prompt import (
    find_matching_event_terms,
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


def _check_player_names_preserved(
    source_text: str,
    translated_text: str,
) -> list[str]:
    """
    원문에 등장한 선수명 후보가 번역문에도 남아 있는지 검사합니다.

    MLB Play Result는 보통 대문자로 시작하는 영문 토큰으로
    선수명을 표기하므로 해당 토큰을 선수명 후보로 사용합니다.

    이번 단계에서는 원문 선수명의 누락만 검사합니다.
    원문에 없는 추가 선수명 검사는 후속 작업에서 추가합니다.
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

    이번 단계에서는 원문 숫자의 누락만 검사합니다.
    원문에 없는 추가 숫자 검사는 후속 작업에서 추가합니다.
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