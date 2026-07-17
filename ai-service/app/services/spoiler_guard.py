import re

from app.schemas.ai_schema import AiCopyMode, SafeContext


# 보호 모드에서 결과나 경기 방향을 드러낼 수 있어 차단하는 표현입니다.
PROTECTED_FORBIDDEN_WORDS = (
    "홈런",
    "역전",
    "끝내기",
    "영봉",
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
    "영봉",
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


# 공개 FINAL_HEADLINE에서 summaryFacts.totalRuns로 검증할 수 있는 총득점 표현입니다.
# 예: "총 8득점", "양 팀 합계 8득점"
REVEALED_TOTAL_RUNS_PATTERN = re.compile(
    r"(?:총|양\s*팀\s*합계)\s*(\d{1,3})\s*득점(?!권)"
)


# 보호 모드에서 점수 노출을 차단하기 위한 넓은 점수 패턴입니다.
# "7-8회" 같은 이닝 범위 표현은 점수로 처리하지 않습니다.
SCORE_PAIR_PATTERN = re.compile(
    r"(?<!\d)(\d{1,3})\s*(?:대|:|-)\s*(\d{1,3})(?!\d|\s*회)"
)


# 공개 FINAL_HEADLINE에서 허용하는 최종 점수 표기는 home-away 한 가지입니다.
# 예: 홈팀이 5-3으로 승리, 원정팀이 3-5로 승리
REVEALED_FINAL_SCORE_PATTERN = re.compile(
    r"(?<!\d)(\d{1,3})\s*-\s*(\d{1,3})(?!\d|\s*회)"
)


# 공개 모드에서도 5:3, 5대3 형식은 생성 포맷 통일을 위해 거부합니다.
REVEALED_UNSUPPORTED_SCORE_PAIR_PATTERN = re.compile(
    r"(?<!\d)\d{1,3}\s*(?:대|:)\s*\d{1,3}(?!\d|\s*회)"
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


# 승리 동사의 실제 주어에 가까운 팀만 winner claim으로 판별합니다.
# 넓은 범위 탐색을 하지 않아 "홈팀과 원정팀의 접전 끝 원정팀이 승리"에서
# 앞쪽의 "홈팀"을 승자로 오인하지 않습니다.
WIN_CLAIM_PATTERN = re.compile(
    r"(홈팀|원정팀)(?:이|가|은|는)?\s*"
    r"(?:\d{1,3}\s*-\s*\d{1,3}(?:으로|로)?\s*)?"
    r"(?:승리|이겼|이긴|이김)"
)


# 패배 동사의 실제 주어에 가까운 팀을 판별합니다.
# 홈팀 패배는 원정팀 승리, 원정팀 패배는 홈팀 승리로 변환합니다.
LOSS_CLAIM_PATTERN = re.compile(
    r"(홈팀|원정팀)(?:이|가|은|는)?\s*"
    r"(?:\d{1,3}\s*-\s*\d{1,3}(?:으로|로)?\s*)?"
    r"(?:패배|졌다|패한)"
)


# 승자 표현에서 "승자는 홈팀"처럼 명시된 결과도 허용합니다.
EXPLICIT_WINNER_PATTERN = re.compile(
    r"승자(?:는|가)?\s*(홈팀|원정팀)"
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
    문구에 포함된 금지 표현을 violations에bidden_words(
    text: str,
    forbidden_words: tuple[str, ...],
    violations: list[str],
) -> 추가합니다.
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


def _revealed_event_types(
    safe_context: SafeContext | None,
) -> set[str]:
    """
    FINAL_HEADLINE REVEALED safeContext에 실제 포함된 공개 이벤트 타입을 수집합니다.
    """

    if safe_context is None:
        return set()

    event_types: set[str] = set()

    for event in getattr(safe_context, "revealed_events", []) or []:
        event_type = getattr(event, "event_type", None)
        if event_type:
            event_types.add(event_type)

    for moment in getattr(safe_context, "revealed_moments", []) or []:
        for event_type in getattr(moment, "event_types", []) or []:
            if event_type:
                event_types.add(event_type)

    return event_types


def _revealed_supported_words(
    safe_context: SafeContext | None,
) -> set[str]:
    """
    REVEALED 모드에서 safeContext 근거로 검증 가능한 결과 표현을 반환합니다.

    PROTECTED 모드에서는 계속 금지하지만,
    REVEALED 모드에서는 revealedEvents/revealedMoments/verifiedPlays에 실제 근거가 있으면 허용합니다.
    """

    supported_words: set[str] = set()
    event_types = _revealed_event_types(safe_context)

    if "home_run" in event_types:
        supported_words.add("홈런")

    if "lead_change" in event_types:
        supported_words.update({"역전", "리드"})

    if "walk_off" in event_types:
        supported_words.add("끝내기")

    if "scoring_play" in event_types:
        supported_words.add("득점")

    if safe_context is None:
        return supported_words

    summary_facts = getattr(
        safe_context,
        "summary_facts",
        None,
    )

    if summary_facts is not None:
        if getattr(summary_facts, "comeback_win", None) is True:
            supported_words.add("역전")

        if getattr(summary_facts, "walk_off", None) is True:
            supported_words.add("끝내기")

        if getattr(summary_facts, "shutout", None) is True:
            supported_words.add("영봉")

    for play in getattr(safe_context, "verified_plays", []) or []:
        translated_text = getattr(play, "translated_text", None) or ""
        source_text = (getattr(play, "source_text", None) or "").lower()

        if (
            "홈런" in translated_text
            or "homered" in source_text
            or "home run" in source_text
        ):
            supported_words.add("홈런")

        if getattr(play, "scoring_play", None) is True:
            supported_words.add("득점")

        fact_tags = set(
            getattr(play, "fact_tags", []) or []
        )

        if "DECISIVE_SCORE" in fact_tags:
            supported_words.update(
                {
                    "결승타",
                    "득점",
                }
            )

    return supported_words


def _collect_revealed_unsupported_words(
    text: str,
    safe_context: SafeContext | None,
    violations: list[str],
) -> None:
    """
    REVEALED 모드에서 safeContext로 검증되지 않는 결과 표현만 차단합니다.
    """

    supported_words = _revealed_supported_words(safe_context)

    for word in REVEALED_UNSUPPORTED_WORDS:
        if word in text and word not in supported_words:
            _append_violation(
                violations,
                f"FORBIDDEN_WORD:{word}",
            )

    # 총득점 표현은 summaryFacts.totalRuns와 별도로 검증하므로,
    # 해당 구간만 제거한 뒤 남은 결과 표현을 일반 금지 패턴으로 검사합니다.
    unsupported_result_text = REVEALED_TOTAL_RUNS_PATTERN.sub("", text)

    for pattern, violation_word in UNSUPPORTED_RESULT_PATTERNS:
        if (
            pattern.search(unsupported_result_text)
            and violation_word not in supported_words
        ):
            _append_violation(
                violations,
                f"FORBIDDEN_WORD:{violation_word}",
            )


def _validate_revealed_total_runs(
    text: str,
    safe_context: SafeContext | None,
    violations: list[str],
) -> None:
    """
    공개 모드 문구의 총득점 숫자가 summaryFacts.totalRuns와 일치하는지 검사합니다.

    검증 대상은 "총 N득점"과 "양 팀 합계 N득점" 표현입니다.
    "N득점"처럼 총득점인지 불명확한 표현은 기존 일반 결과 표현 검증에서 차단합니다.
    """

    total_runs_matches = REVEALED_TOTAL_RUNS_PATTERN.findall(text)
    if not total_runs_matches:
        return

    summary_facts = (
        getattr(safe_context, "summary_facts", None)
        if safe_context is not None
        else None
    )
    expected_total_runs = (
        getattr(summary_facts, "total_runs", None)
        if summary_facts is not None
        else None
    )

    if expected_total_runs is None:
        _append_violation(
            violations,
            "TOTAL_RUNS_CONTEXT_MISSING",
        )
        return

    for total_runs_text in total_runs_matches:
        if int(total_runs_text) != expected_total_runs:
            _append_violation(
                violations,
                "TOTAL_RUNS_MISMATCH",
            )


def _validate_revealed_score(
    text: str,
    safe_context: SafeContext | None,
    violations: list[str],
) -> None:
    """
    공개 모드 문구의 점수가 safeContext.finalScore와 일치하는지 검사합니다.

    공개 FINAL_HEADLINE의 점수 표기는 반드시 home-away 순서의 하이픈 형식이어야 합니다.
    """

    score_matches = REVEALED_FINAL_SCORE_PATTERN.findall(text)
    unsupported_score_pair_found = (
        REVEALED_UNSUPPORTED_SCORE_PAIR_PATTERN.search(text) is not None
    )
    standalone_point_found = STANDALONE_POINT_PATTERN.search(text) is not None

    if (
        not score_matches
        and not unsupported_score_pair_found
        and not standalone_point_found
    ):
        return

    if unsupported_score_pair_found or standalone_point_found:
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


def _team_to_winner(team_name: str) -> str:
    """
    한국어 팀 지시어를 내부 winner 값으로 변환합니다.
    """

    if team_name == "홈팀":
        return "home"

    return "away"


def _opposite_winner(team_name: str) -> str:
    """
    패배 주어를 기준으로 반대 팀을 winner 값으로 변환합니다.
    """

    if team_name == "홈팀":
        return "away"

    return "home"


def _alias_values(
    *values: str | None,
) -> set[str]:
    """
    팀 정식명/약어/영문 마지막 단어를 승자 검증 alias로 사용합니다.
    """

    aliases: set[str] = set()

    for value in values:
        if not isinstance(value, str):
            continue

        alias = value.strip()
        if not alias:
            continue

        aliases.add(alias)

        parts = alias.split()
        if len(parts) > 1:
            aliases.add(parts[-1])

    return aliases


def _team_aliases(
    safe_context: SafeContext | None,
) -> dict[str, set[str]]:
    """
    safeContext의 팀 정보를 home/away winner claim 검증용 alias로 변환합니다.
    """

    aliases = {
        "home": {"홈팀"},
        "away": {"원정팀"},
    }

    if safe_context is None:
        return aliases

    teams = getattr(safe_context, "teams", None)
    if teams is not None:
        home = getattr(teams, "home", None)
        away = getattr(teams, "away", None)

        if home is not None:
            aliases["home"].update(
                _alias_values(
                    getattr(home, "name", None),
                    getattr(home, "abbr", None),
                )
            )

        if away is not None:
            aliases["away"].update(
                _alias_values(
                    getattr(away, "name", None),
                    getattr(away, "abbr", None),
                )
            )

    summary_facts = getattr(safe_context, "summary_facts", None)
    if summary_facts is not None:
        winner_side = getattr(summary_facts, "winner_side", None)
        loser_side = _opposite_winner(winner_side) if winner_side in {"home", "away"} else None

        if winner_side in aliases:
            aliases[winner_side].update(
                _alias_values(getattr(summary_facts, "winner_name", None))
            )

        if loser_side in aliases:
            aliases[loser_side].update(
                _alias_values(getattr(summary_facts, "loser_name", None))
            )

    return aliases


def _context_backed_winner_modifier_pattern(
    safe_context: SafeContext | None,
) -> str:
    """
    safeContext가 검증한 연장 여부와 종료 이닝을 기준으로
    승자 주어와 점수 사이에 허용할 제한된 수식어 정규식을 만듭니다.

    임의 문장을 허용하지 않고 현재 context와 일치하는 연장 표현만 허용합니다.
    """

    if safe_context is None:
        return ""

    if getattr(safe_context, "extra_innings", None) is not True:
        return ""

    modifiers = {
        "연장 끝에",
        "연장전 끝에",
    }

    innings_played = getattr(
        safe_context,
        "innings_played",
        None,
    )

    if (
        isinstance(innings_played, int)
        and not isinstance(innings_played, bool)
        and innings_played > 0
    ):
        modifiers.update(
            {
                f"{innings_played}회 연장 끝에",
                f"{innings_played}회 연장전 끝에",
            }
        )

    escaped_modifiers = "|".join(
        re.escape(modifier)
        for modifier in sorted(
            modifiers,
            key=len,
            reverse=True,
        )
    )

    return rf"(?:(?:{escaped_modifiers})\s*)?"


def _resolve_alias_winner_claims(
    text: str,
    safe_context: SafeContext | None,
) -> set[str]:
    """
    홈팀/원정팀 표현뿐 아니라 실제 팀명·약어 기반 승패 표현도 winner claim으로 인식합니다.

    safeContext가 검증한 연장 표현은 팀 주어와 점수 사이에 사용할 수 있습니다.
    """

    claimed_winners: set[str] = set()
    aliases = _team_aliases(safe_context)
    winner_modifier_pattern = (
        _context_backed_winner_modifier_pattern(
            safe_context
        )
    )

    win_verbs = r"승리|이겼|이긴|이김"
    loss_verbs = r"패배|졌다|패한"

    for side, side_aliases in aliases.items():
        for alias in sorted(
            side_aliases,
            key=len,
            reverse=True,
        ):
            escaped_alias = re.escape(alias)

            if re.search(
                rf"{escaped_alias}(?:이|가|은|는)?\s*"
                rf"{winner_modifier_pattern}"
                rf"(?:\d{{1,3}}\s*-\s*\d{{1,3}}(?:으로|로)?\s*)?"
                rf"(?:{win_verbs})",
                text,
                flags=re.IGNORECASE,
            ):
                claimed_winners.add(side)

            if re.search(
                rf"{escaped_alias}(?:이|가|은|는)?\s*"
                rf"{winner_modifier_pattern}"
                rf"(?:\d{{1,3}}\s*-\s*\d{{1,3}}(?:으로|로)?\s*)?"
                rf"(?:{loss_verbs})",
                text,
                flags=re.IGNORECASE,
            ):
                claimed_winners.add(
                    _opposite_winner(side)
                )

            if re.search(
                rf"승자(?:는|가)?\s*{escaped_alias}",
                text,
                flags=re.IGNORECASE,
            ):
                claimed_winners.add(side)

    return claimed_winners


def _resolve_claimed_winner(
    text: str,
    safe_context: SafeContext | None = None,
) -> str | None:
    """
    문구에서 명시적으로 주장하는 승자를 home, away, draw로 변환합니다.

    서로 모순된 결과가 실제로 둘 이상 감지되면 ambiguous를 반환합니다.
    """

    claimed_winners: set[str] = set()

    for match in WIN_CLAIM_PATTERN.finditer(text):
        claimed_winners.add(_team_to_winner(match.group(1)))

    for match in LOSS_CLAIM_PATTERN.finditer(text):
        claimed_winners.add(_opposite_winner(match.group(1)))

    for match in EXPLICIT_WINNER_PATTERN.finditer(text):
        claimed_winners.add(_team_to_winner(match.group(1)))

    claimed_winners.update(
        _resolve_alias_winner_claims(
            text=text,
            safe_context=safe_context,
        )
    )

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

    claimed_winner = _resolve_claimed_winner(text, safe_context)

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
        _collect_revealed_unsupported_words(
            text=normalized_text,
            safe_context=safe_context,
            violations=violations,
        )

        _validate_revealed_total_runs(
            text=normalized_text,
            safe_context=safe_context,
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
