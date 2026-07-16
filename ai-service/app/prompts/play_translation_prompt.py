import json
import re
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml

from app.schemas.ai_schema import PlayTranslationRequest


BASEBALL_TERMS_PATH = (
    Path(__file__).resolve().parents[1]
    / "resources"
    / "baseball_terms_ko.yml"
)

FIELDING_OUT_MARKERS = (
    "grounded out",
    "flied out",
    "lined out",
    "popped out",
    "fouled out",
    "force out",
    "forced out",
    "double play",
    "triple play",
)

AMBIGUOUS_POSITION_PATTERNS = frozenset(
    {"first", "second", "third", "left", "center", "right"}
)


@lru_cache(maxsize=1)
def load_baseball_terms() -> dict[str, Any]:
    """
    PLAY_TRANSLATION 용어집을 한 번만 읽어 반환합니다.
    """

    if not BASEBALL_TERMS_PATH.is_file():
        raise FileNotFoundError(
            f"Baseball terms file not found: {BASEBALL_TERMS_PATH}"
        )

    loaded = yaml.safe_load(
        BASEBALL_TERMS_PATH.read_text(encoding="utf-8")
    )

    if not isinstance(loaded, dict):
        raise ValueError("Baseball terms YAML root must be a mapping")

    required_sections = {
        "metadata",
        "output_policy",
        "global_forbidden_expressions",
        "positions",
        "position_patterns",
        "directions",
        "direction_patterns",
        "modifiers",
        "event_terms",
    }
    missing_sections = sorted(required_sections - loaded.keys())

    if missing_sections:
        raise ValueError(
            "Baseball terms YAML is missing required sections: "
            + ", ".join(missing_sections)
        )

    return loaded


def normalize_source_text(source_text: str) -> str:
    """
    대소문자·따옴표·대시·연속 공백 차이를 줄여 매칭용 문자열을 만듭니다.
    """

    normalized = (
        source_text.strip()
        .lower()
        .replace("’", "'")
        .replace("–", "-")
        .replace("—", "-")
    )
    return re.sub(r"\s+", " ", normalized)


def _find_pattern_span(
    normalized_source: str,
    source_pattern: str,
) -> tuple[int, int] | None:
    """
    패턴을 단어 경계 기준으로 찾아 시작·끝 위치를 반환합니다.
    """

    normalized_pattern = normalize_source_text(source_pattern)
    escaped_pattern = re.escape(normalized_pattern).replace(
        r"\ ",
        r"\s+",
    )
    match = re.search(
        rf"(?<![a-z0-9]){escaped_pattern}(?![a-z0-9])",
        normalized_source,
    )
    return match.span() if match else None


def _spans_overlap(
    first: tuple[int, int],
    second: tuple[int, int],
) -> bool:
    """
    두 패턴의 문자열 범위가 겹치는지 확인합니다.
    """

    return first[0] < second[1] and second[0] < first[1]


def find_matching_event_terms(
    source_text: str,
    glossary: dict[str, Any] | None = None,
) -> list[dict[str, Any]]:
    """
    구체적인 표현을 우선해 겹치지 않는 이벤트 용어만 선택합니다.
    """

    terms = glossary or load_baseball_terms()
    normalized_source = normalize_source_text(source_text)
    candidates: list[dict[str, Any]] = []

    for term in terms["event_terms"]:
        best_match: tuple[str, tuple[int, int]] | None = None

        for source_pattern in term.get("source_patterns", []):
            span = _find_pattern_span(
                normalized_source,
                source_pattern,
            )
            if span is None:
                continue

            if (
                best_match is None
                or len(source_pattern) > len(best_match[0])
            ):
                best_match = (source_pattern, span)

        if best_match is None:
            continue

        matched_pattern, span = best_match

        candidates.append(
            {
                "id": term["id"],
                "category": term.get("category"),
                "priority": int(term.get("priority", 0)),
                "matchedPattern": matched_pattern,
                "canonicalKo": term["canonical_ko"],
                "outcomeCode": term.get("outcome_code"),
                "requiredKo": term.get("required_ko", []),
                "forbiddenKo": term.get("forbidden_ko", []),
                "span": span,
            }
        )

    candidates.sort(
        key=lambda item: (
            -item["priority"],
            -len(item["matchedPattern"]),
            item["span"][0],
        )
    )

    selected: list[dict[str, Any]] = []
    selected_spans: list[tuple[int, int]] = []

    for candidate in candidates:
        overlaps_existing = any(
            _spans_overlap(
                candidate["span"],
                selected_span,
            )
            for selected_span in selected_spans
        )
        if overlaps_existing:
            continue

        selected.append(candidate)
        selected_spans.append(candidate["span"])

    selected.sort(key=lambda item: item["span"][0])

    for item in selected:
        item.pop("priority")
        item.pop("span")

    return selected


def find_matching_modifiers(
    source_text: str,
    glossary: dict[str, Any] | None = None,
) -> list[dict[str, str]]:
    """
    원문에 직접 등장한 타점·끝내기·동점 수식어를 선택합니다.
    """

    terms = glossary or load_baseball_terms()
    normalized_source = normalize_source_text(source_text)
    matched: list[dict[str, Any]] = []

    for modifier in terms["modifiers"]:
        matching_patterns = [
            pattern
            for pattern in modifier.get("source_patterns", [])
            if _find_pattern_span(
                normalized_source,
                pattern,
            )
            is not None
        ]

        if not matching_patterns:
            continue

        best_pattern = max(matching_patterns, key=len)

        matched.append(
            {
                "id": modifier["id"],
                "matchedPattern": best_pattern,
                "canonicalKo": modifier["canonical_ko"],
                "priority": int(modifier.get("priority", 0)),
            }
        )

    matched.sort(
        key=lambda item: (
            -item["priority"],
            item["id"],
        )
    )

    for item in matched:
        item.pop("priority")

    return matched


def _is_fielding_out_phrase(
    normalized_source: str,
    position_pattern: str,
) -> bool:
    """
    to first·to center 등의 표현이 수비수가 처리한 아웃 문맥인지 확인합니다.
    """

    escaped_position = re.escape(position_pattern).replace(
        r"\ ",
        r"\s+",
    )
    marker_pattern = "|".join(
        re.escape(marker).replace(r"\ ", r"\s+")
        for marker in FIELDING_OUT_MARKERS
    )

    return (
        re.search(
            (
                rf"(?:{marker_pattern}).*?"
                rf"\bto\s+{escaped_position}\b"
            ),
            normalized_source,
        )
        is not None
    )


def find_matching_directions(
    source_text: str,
    glossary: dict[str, Any] | None = None,
) -> list[dict[str, str]]:
    """
    타구 방향 규칙을 찾되 수비수가 처리한 아웃 표현은
    방향으로 중복 해석하지 않습니다.
    """

    terms = glossary or load_baseball_terms()
    normalized_source = normalize_source_text(source_text)
    matched: list[dict[str, str]] = []

    for (
        direction_id,
        source_patterns,
    ) in terms["direction_patterns"].items():
        matching_patterns = [
            pattern
            for pattern in source_patterns
            if _find_pattern_span(
                normalized_source,
                pattern,
            )
            is not None
        ]

        if not matching_patterns:
            continue

        best_pattern = max(matching_patterns, key=len)
        bare_position = best_pattern.removeprefix("to ")

        if (
            direction_id in {"left", "center", "right"}
            and _is_fielding_out_phrase(
                normalized_source,
                bare_position,
            )
        ):
            continue

        matched.append(
            {
                "id": direction_id,
                "matchedPattern": best_pattern,
                "canonicalKo": terms["directions"][
                    direction_id
                ],
            }
        )

    return matched


def find_matching_positions(
    source_text: str,
    glossary: dict[str, Any] | None = None,
) -> list[dict[str, str]]:
    """
    명시적인 수비 위치를 찾고 first·second 같은 모호한 단어는
    아웃 문맥에서만 허용합니다.
    """

    terms = glossary or load_baseball_terms()
    normalized_source = normalize_source_text(source_text)
    matched: list[dict[str, str]] = []

    for (
        position_id,
        source_patterns,
    ) in terms["position_patterns"].items():
        valid_patterns: list[str] = []

        for pattern in source_patterns:
            if (
                _find_pattern_span(
                    normalized_source,
                    pattern,
                )
                is None
            ):
                continue

            if (
                pattern in AMBIGUOUS_POSITION_PATTERNS
                and not _is_fielding_out_phrase(
                    normalized_source,
                    pattern,
                )
            ):
                continue

            valid_patterns.append(pattern)

        if not valid_patterns:
            continue

        best_pattern = max(valid_patterns, key=len)

        matched.append(
            {
                "id": position_id,
                "matchedPattern": best_pattern,
                "canonicalKo": terms["positions"][
                    position_id
                ],
            }
        )

    return matched


def build_play_translation_prompt(
    request: PlayTranslationRequest,
) -> str:
    """
    원문과 직접 관련된 용어 규칙만 포함한
    PLAY_TRANSLATION 프롬프트를 만듭니다.
    """

    if not normalize_source_text(request.source_text):
        raise ValueError("sourceText must not be blank")

    glossary = load_baseball_terms()
    metadata = glossary["metadata"]

    prompt_payload = {
        "purpose": metadata["purpose"],
        "mode": request.mode.value,
        "targetLanguage": request.target_language,
        "glossaryVersion": metadata["version"],
        "sourceText": request.source_text,
        "matchedRules": {
            "eventTerms": find_matching_event_terms(
                request.source_text,
                glossary,
            ),
            "modifiers": find_matching_modifiers(
                request.source_text,
                glossary,
            ),
            "directions": find_matching_directions(
                request.source_text,
                glossary,
            ),
            "positions": find_matching_positions(
                request.source_text,
                glossary,
            ),
        },
        "outputPolicy": glossary["output_policy"],
        "forbiddenExpressions": glossary[
            "global_forbidden_expressions"
        ],
    }

    return f"""
너는 MLB의 단일 Play Result를 한국 야구 중계·기록 용어로 번역하는 번역기입니다.

번역 규칙:
- sourceText의 한 플레이만 번역하세요.
- 선수명은 철자와 표기를 바꾸지 말고 원문 그대로 유지하세요.
- 숫자, 거리, 베이스 번호, 아웃·출루·타구 결과와 사건 순서를 바꾸지 마세요.
- matchedRules에 있는 canonicalKo를 우선 사용하세요.
- matchedRules.eventTerms[].canonicalKo는 의미가 같은 다른 표현으로 바꾸지 말고 그대로 사용하세요.
- matchedRules.eventTerms[].requiredKo 항목은 모두 translated_text에 포함하세요.
- matchedRules.positions가 비어 있지 않으면 각 positions[].canonicalKo를 translated_text에 반드시 포함하세요.
- 수비 아웃 문맥에서 "to first", "to second", "to third", "to shortstop", "to left", "to center", "to right"는 방향이 아니라 수비 처리 위치입니다.
- 예: "lined out to second"는 "2루수 직선타 아웃"입니다. "라인드라이브 아웃"으로 바꾸지 마세요.
- matchedRules에 없는 야구 결과를 추측하거나 새로 만들지 마세요.
- 원문에 없는 점수, 이닝, 선수, 승패, 타점, 감정, 평가, 해설을 추가하지 마세요.
- 출력은 한국어 한 문장만 사용하세요.
- 가능하면 outputPolicy.preferred_format의 "선수명, 결과" 형식을 사용하세요.
- 설명, 마크다운, 코드 블록 없이 JSON 객체 하나만 반환하세요.
- 반환 JSON에는 translated_text 필드 하나만 포함하세요.
- translated_text는 빈 문자열이면 안 됩니다.

반환 형식:
{{
  "translated_text": "한국어 번역"
}}

요청 데이터:
{json.dumps(
    prompt_payload,
    ensure_ascii=False,
    indent=2,
)}
""".strip()