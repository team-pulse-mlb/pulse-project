import re

from app.schemas.ai_schema import AiCopyMode, SafeContext


# 보호 모드에서 결과나 경기 방향을 드러낼 수 있어 차단하는 표현입니다.
PROTECTED_FORBIDDEN_WORDS = (
    "홈런",
    "역전",
    "끝내기",
    "승리",
    "패배",
    "승자",
    "패자",
    "리드",
    "우세",
    "열세",
    "결승타",
    "동점타",
    "쐐기",
    "실점",
    "홈팀 리드",
    "원정팀 리드",
    "점수",
    "앞서",
    "따라붙",
    "결말",
    "결과",
    "스코어",
    "동점",
    "무승부",
    "이긴",
    "이겼",
    "이김",
    "졌다",
    "패한",
)


# REVEALED에서도 safeContext만으로 사실 여부를 검증하기 어려운 표현입니다.
# 최종 점수와 winner는 별도 검증 후 허용하지만,
# 경기 과정이나 특정 play 결과는 현재 계약에서 계속 차단합니다.
REVEALED_UNSUPPORTED_WORDS = (
    "홈런",
    "역전",
    "끝내기",
    "리드",
    "우세",
    "열세",
    "결승타",
    "동점타",
    "쐐기",
    "실점",
    "점수 차",
    "앞서",
    "따라붙",
)


# "득점"은 차단하지만 보호 표현인 "득점권"은 허용합니다.
UNSUPPORTED_RESULT_PATTERNS = (
    (
        re.compile(r"득점(?!권)"),
        "득점",
    ),
)


# 5-3, 5:3, 5대3 형식의 점수 쌍을 찾습니다.
# "7-8회" 같은 이닝 범위 표현은 점수로 처리하지 않습니다.
SCORE_PAIR_PATTERN = re.compile(
    r"(?<!\d)(\d{1,3})\s*(?:대|:|-)\s*(\d{1,3})(?!\d|\s*회)"
)


# "5점"처럼 한 팀의 점수만 나타내는 표현은
# home-away 최종 점수와 정확히 비교하기 어려워 별도로 차단합니다.
STANDALONE_POINT_PATTERN = re.compile(
    r"(?<!\d)\d{1,3}\s*점"
)


# 문구에 승패 또는 최종 결과 주장이 존재하는지 확인합니다.
RESULT_MARKER_PATTERN = re.compile(
    r"승리|패배|승자|패자|이긴|이겼|이김|졌다|패한|무승부|승패|동점"
)


# 문구가 홈팀 승리를 명시하는 형태입니다.
HOME_WIN_PATTERNS = (
    re.compile(
        r"홈팀(?:이|가)?[^.!?\n]{0,30}(?:승리|이겼|이긴|이김)"
    ),
    re.compile(
        r"원정팀(?:이|가)?[^.!?\n]{0,30}(?:패배|졌다|패한)"
    ),
    re.compile(r"승자(?:는|가)?\s*홈팀"),
)


# 문구가 원정팀 승리를 명시하는 형태입니다.
AWAY_WIN_PATTERNS = (
    re.compile(
        r"원정팀(?:이|가)?[^.!?\n]{0,30}(?:승리|이겼|이긴|이김)"
    ),
    re.compile(
        r"홈팀(?:이|가)?[^.!?\n]{0,30}(?:패배|졌다|패한)"
    ),
    re.compile(r"승자(?:는|가)?\s*원정팀"),
)


# 문구가 무승부를 명시하는 형태입니다.
DRAW_PATTERNS = (
    re.compile(r"무승부"),
    re.compile(r"동점(?:으로)?\s*(?:끝|종료|마무리)"),
)


def _append_violation(
    violations: list[str],
    violation: str,
) -> None:
    """
    동일한 위반 코드가 중복 추가되지 않도록 저장합니다.
    """

    if violation not in violations:
        violations.append(violation)


def _collect_forbidden_words(
    text: str,
    forbidden_words: tuple[str, ...],
    violations: list[str],
) -> None:
    """
    문구에 포함된 금지 표현을 violations에 추가합니다.
    """

    for word in forbidden_words:
        if word in text:
            _append_violation(
                violations,
                f"FORBIDDEN_WORD:{word}",
            )

    for pattern, violation_word in UNSUPPORTED_RESULT_PATTERNS:
        if pattern.search(text):
            _append_violation(
                violations,
                f"FORBIDDEN_WORD:{violation_word}",
            )


def _validate_revealed_score(
    text: str,
    safe_context: SafeContext | None,
    violations: list[str],
) -> None:
    """
    공개 모드 문구의 점수가 safeContext.finalScore와 일치하는지 검사합니다.

    최종 점수는 home-away 순서로 표현해야 합니다.
    """

    score_matches = SCORE_PAIR_PATTERN.findall(text)
    standalone_point_found = STANDALONE_POINT_PATTERN.search(text) is not None

    if not score_matches and not standalone_point_found:
        return

    if standalone_point_found:
        # "5점"처럼 한쪽 점수만 있는 표현은 실제 결과와 안전하게 비교할 수 없습니다.
        _append_violation(
            violations,
            "UNSUPPORTED_SCORE_FORMAT",
        )

    if safe_context is None or safe_context.final_score is None:
        _append_violation(
            violations,
            "SCORE_CONTEXT_MISSING",
        )
        return

    expected_score = (
        safe_context.final_score.home,
        safe_context.final_score.away,
    )

    for home_score_text, away_score_text in score_matches:
        actual_score = (
            int(home_score_text),
            int(away_score_text),
        )

        if actual_score != expected_score:
            _append_violation(
                violations,
                "SCORE_MISMATCH",
            )


def _resolve_claimed_winner(text: str) -> str | None:
    """
    문구에서 명시적으로 주장하는 승자를 home, away, draw로 변환합니다.

    둘 이상의 서로 다른 결과가 감지되면 ambiguous를 반환합니다.
    """

    claimed_winners: set[str] = set()

    if any(pattern.search(text) for pattern in HOME_WIN_PATTERNS):
        claimed_winners.add("home")

    if any(pattern.search(text) for pattern in AWAY_WIN_PATTERNS):
        claimed_winners.add("away")

    if any(pattern.search(text) for pattern in DRAW_PATTERNS):
        claimed_winners.add("draw")

    if len(claimed_winners) > 1:
        return "ambiguous"

    if not claimed_winners:
        return None

    return next(iter(claimed_winners))


def _validate_revealed_winner(
    text: str,
    safe_context: SafeContext | None,
    violations: list[str],
) -> None:
    """
    공개 모드 문구의 승패 표현이 safeContext.winner와 일치하는지 검사합니다.
    """

    if RESULT_MARKER_PATTERN.search(text) is None:
        return

    if safe_context is None or safe_context.winner is None:
        _append_violation(
            violations,
            "WINNER_CONTEXT_MISSING",
        )
        return

    expected_winner = safe_context.winner.strip().lower()

    if expected_winner not in {"home", "away", "draw"}:
        _append_violation(
            violations,
            "WINNER_CONTEXT_INVALID",
        )
        return

    claimed_winner = _resolve_claimed_winner(text)

    if claimed_winner == "ambiguous":
        _append_violation(
            violations,
            "WINNER_REFERENCE_AMBIGUOUS",
        )
        return

    if claimed_winner is None:
        # 팀 식별자 없이 "승리한 경기"처럼 결과만 주장하면
        # 현재 safeContext로는 어떤 팀을 의미하는지 검증할 수 없습니다.
        _append_violation(
            violations,
            "WINNER_REFERENCE_UNVERIFIABLE",
        )
        return

    if claimed_winner != expected_winner:
        _append_violation(
            violations,
            "WINNER_MISMATCH",
        )


def check_spoiler_text(
    text: str,
    mode: AiCopyMode = AiCopyMode.PROTECTED,
    safe_context: SafeContext | None = None,
) -> dict[str, bool | list[str]]:
    """
    생성 문구를 보호·공개 모드 정책에 맞게 검사합니다.

    PROTECTED:
    - 점수, 승패, 우세·열세, 결과 방향성 표현을 차단합니다.

    REVEALED:
    - safeContext에 실제로 전달된 finalScore와 winner에
      일치하는 점수·승패 표현만 허용합니다.
    - 경기 과정이나 원본 play 근거가 필요한 표현은 계속 차단합니다.

    반환값:
    - spoiler_safe: 검수 통과 여부
    - violations: 감지된 위반 코드 목록

    ai-service는 fallback 문구를 생성하거나 반환하지 않습니다.
    """

    if not isinstance(text, str) or not text.strip():
        return {
            "spoiler_safe": False,
            "violations": ["INVALID_TEXT"],
        }

    normalized_text = text.strip()
    violations: list[str] = []

    try:
        resolved_mode = (
            mode
            if isinstance(mode, AiCopyMode)
            else AiCopyMode(mode)
        )
    except ValueError:
        return {
            "spoiler_safe": False,
            "violations": ["UNSUPPORTED_MODE"],
        }

    if resolved_mode == AiCopyMode.PROTECTED:
        _collect_forbidden_words(
            text=normalized_text,
            forbidden_words=PROTECTED_FORBIDDEN_WORDS,
            violations=violations,
        )

        if SCORE_PAIR_PATTERN.search(normalized_text):
            _append_violation(
                violations,
                "SCORE_PATTERN",
            )

        if STANDALONE_POINT_PATTERN.search(normalized_text):
            _append_violation(
                violations,
                "SCORE_PATTERN",
            )

    elif resolved_mode == AiCopyMode.REVEALED:
        _collect_forbidden_words(
            text=normalized_text,
            forbidden_words=REVEALED_UNSUPPORTED_WORDS,
            violations=violations,
        )

        _validate_revealed_score(
            text=normalized_text,
            safe_context=safe_context,
            violations=violations,
        )
        _validate_revealed_winner(
            text=normalized_text,
            safe_context=safe_context,
            violations=violations,
        )

    return {
        "spoiler_safe": len(violations) == 0,
        "violations": violations,
    }