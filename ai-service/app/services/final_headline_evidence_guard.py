import re

from app.schemas.ai_schema import AiCopyMode, SafeContext


# OpenAI가 usedFactIds로 선언할 수 있는 summaryFacts ID와
# Pydantic 모델의 실제 snake_case 필드명을 연결합니다.
FINAL_HEADLINE_FACT_ATTRIBUTES = {
    "summaryFacts.winnerSide": "winner_side",
    "summaryFacts.winnerName": "winner_name",
    "summaryFacts.loserName": "loser_name",
    "summaryFacts.winnerScore": "winner_score",
    "summaryFacts.loserScore": "loser_score",
    "summaryFacts.firstScoringSide": "first_scoring_side",
    "summaryFacts.firstScoringInning": "first_scoring_inning",
    "summaryFacts.tyingInning": "tying_inning",
    "summaryFacts.decisiveInning": "decisive_inning",
    "summaryFacts.decisiveRuns": "decisive_runs",
    "summaryFacts.leadChangeCount": "lead_change_count",
    "summaryFacts.comebackWin": "comeback_win",
    "summaryFacts.walkOff": "walk_off",
    "summaryFacts.shutout": "shutout",
    "summaryFacts.extraInnings": "extra_innings",
    "summaryFacts.finalInning": "final_inning",
    "summaryFacts.scoreGap": "score_gap",
    "summaryFacts.totalRuns": "total_runs",
}


FINAL_HEADLINE_BOOLEAN_CLAIMS = {
    "역전": "summaryFacts.comebackWin",
    "끝내기": "summaryFacts.walkOff",
    "영봉": "summaryFacts.shutout",
}

DECISIVE_SCORE_CLAIM_PATTERN = re.compile(
    r"결승타|결정(?:적(?:인)?)?\s*득점"
)


def _append_violation(
    violations: list[str],
    violation: str,
) -> None:
    """
    동일한 evidence 위반 코드가 중복되지 않도록 추가합니다.
    """

    if violation not in violations:
        violations.append(violation)


def _summary_fact_value(
    safe_context: SafeContext | None,
    fact_id: str,
) -> object | None:
    """
    summaryFacts evidence ID에 해당하는 실제 context 값을 반환합니다.
    """

    attribute_name = FINAL_HEADLINE_FACT_ATTRIBUTES.get(fact_id)

    if attribute_name is None or safe_context is None:
        return None

    summary_facts = getattr(
        safe_context,
        "summary_facts",
        None,
    )

    if summary_facts is None:
        return None

    return getattr(
        summary_facts,
        attribute_name,
        None,
    )


def _verified_plays_by_id(
    safe_context: SafeContext | None,
) -> dict[int, object]:
    """
    검증된 플레이를 playId 기준으로 조회할 수 있게 변환합니다.
    """

    plays_by_id: dict[int, object] = {}

    if safe_context is None:
        return plays_by_id

    for play in getattr(
        safe_context,
        "verified_plays",
        [],
    ) or []:
        play_id = getattr(play, "play_id", None)

        if (
            isinstance(play_id, int)
            and not isinstance(play_id, bool)
            and play_id > 0
        ):
            plays_by_id[play_id] = play

    return plays_by_id


def _normalized_player_name(
    value: object,
) -> str | None:
    """
    선수명을 비교 가능한 형태로 정규화합니다.
    """

    if not isinstance(value, str):
        return None

    normalized = " ".join(value.split()).strip()

    if not normalized:
        return None

    return normalized


def _player_names_for_play(
    play: object,
) -> set[str]:
    """
    하나의 verifiedPlay에서 타자·투수 이름을 수집합니다.
    """

    names: set[str] = set()

    for role_name in ("batter", "pitcher"):
        player = getattr(play, role_name, None)

        if player is None:
            continue

        player_name = _normalized_player_name(
            getattr(player, "name", None)
        )

        if player_name is not None:
            names.add(player_name)

    return names


def _player_aliases_by_name(
    plays_by_id: dict[int, object],
) -> dict[str, str]:
    """
    safeContext 선수명에서 문구 검사용 alias를 생성합니다.

    - 전체 이름은 항상 alias로 사용합니다.
    - 영문 성은 문맥에서 한 선수에게만 해당할 때 사용합니다.
    - 같은 성을 가진 선수가 둘 이상이면 성 단독 alias는 사용하지 않습니다.
    """

    player_names: set[str] = set()

    for play in plays_by_id.values():
        player_names.update(_player_names_for_play(play))

    alias_owners: dict[str, set[str]] = {}
    alias_display: dict[str, str] = {}

    for player_name in player_names:
        aliases = {player_name}
        name_parts = player_name.split()

        if len(name_parts) > 1:
            last_name = name_parts[-1]

            if len(last_name) >= 3:
                aliases.add(last_name)

        for alias in aliases:
            alias_key = alias.casefold()
            alias_display[alias_key] = alias
            alias_owners.setdefault(alias_key, set()).add(
                player_name
            )

    return {
        alias_display[alias_key]: next(iter(owners))
        for alias_key, owners in alias_owners.items()
        if len(owners) == 1
    }


def _text_contains_player_alias(
    text: str,
    alias: str,
) -> bool:
    """
    문구에 선수명 alias가 독립된 이름으로 포함됐는지 검사합니다.
    """

    if alias.isascii():
        pattern = re.compile(
            rf"(?<![A-Za-z0-9])"
            rf"{re.escape(alias)}"
            rf"(?![A-Za-z0-9])",
            flags=re.IGNORECASE,
        )

        return pattern.search(text) is not None

    return alias in text


def _validate_player_mentions(
    text: str,
    plays_by_id: dict[int, object],
    used_play_ids: list[int],
    violations: list[str],
) -> None:
    """
    헤드라인에 등장한 선수가 usedPlayIds 플레이와 연결되는지 검사합니다.
    """

    used_player_names: set[str] = set()

    for play_id in used_play_ids:
        play = plays_by_id.get(play_id)

        if play is None:
            continue

        used_player_names.update(
            player_name.casefold()
            for player_name in _player_names_for_play(play)
        )

    aliases_by_name = _player_aliases_by_name(plays_by_id)

    for alias, player_name in aliases_by_name.items():
        if not _text_contains_player_alias(text, alias):
            continue

        if player_name.casefold() in used_player_names:
            continue

        _append_violation(
            violations,
            f"EVIDENCE_PLAYER_PLAY_MISSING:{player_name}",
        )


def _validate_text_claim_evidence(
    text: str,
    safe_context: SafeContext | None,
    used_fact_ids: list[str],
    used_play_ids: list[int],
    violations: list[str],
) -> None:
    """
    헤드라인의 결과 주장과 선언된 evidence를 직접 대조합니다.
    """

    normalized_text = text.strip()

    for claim_word, required_fact_id in (
        FINAL_HEADLINE_BOOLEAN_CLAIMS.items()
    ):
        if claim_word not in normalized_text:
            continue

        if required_fact_id not in used_fact_ids:
            _append_violation(
                violations,
                (
                    "EVIDENCE_REQUIRED_FACT_MISSING:"
                    f"{required_fact_id}"
                ),
            )
            continue

        if _summary_fact_value(
            safe_context,
            required_fact_id,
        ) is not True:
            _append_violation(
                violations,
                (
                    "EVIDENCE_REQUIRED_FACT_FALSE:"
                    f"{required_fact_id}"
                ),
            )

    plays_by_id = _verified_plays_by_id(safe_context)

    if DECISIVE_SCORE_CLAIM_PATTERN.search(normalized_text) is not None:
        decisive_play_found = False

        for play_id in used_play_ids:
            play = plays_by_id.get(play_id)

            if play is None:
                continue

            fact_tags = set(
                getattr(play, "fact_tags", []) or []
            )

            if "DECISIVE_SCORE" in fact_tags:
                decisive_play_found = True
                break

        if not decisive_play_found:
            _append_violation(
                violations,
                "EVIDENCE_REQUIRED_PLAY_TAG_MISSING:DECISIVE_SCORE",
            )

    _validate_player_mentions(
        text=normalized_text,
        plays_by_id=plays_by_id,
        used_play_ids=used_play_ids,
        violations=violations,
    )


def validate_final_headline_evidence(
    mode: AiCopyMode,
    safe_context: SafeContext | None,
    used_fact_ids: list[str] | None,
    used_play_ids: list[int] | None,
    text: str | None = None,
) -> dict[str, bool | list[str]]:
    """
    OpenAI가 FINAL_HEADLINE 작성에 사용했다고 선언한 evidence가
    실제 safeContext에 존재하는지 검증합니다.

    검증 규칙:
    - PROTECTED 모드에서는 결과 evidence를 사용할 수 없습니다.
    - usedFactIds는 허용된 summaryFacts ID여야 합니다.
    - 해당 summaryFacts 값이 null이면 사용할 수 없습니다.
    - usedPlayIds는 verifiedPlays에 실제 존재해야 합니다.
    - 같은 ID를 두 번 선언할 수 없습니다.
    """

    violations: list[str] = []

    try:
        resolved_mode = (
            mode
            if isinstance(mode, AiCopyMode)
            else AiCopyMode(mode)
        )
    except ValueError:
        return {
            "evidence_safe": False,
            "violations": ["EVIDENCE_UNSUPPORTED_MODE"],
        }

    fact_ids = list(used_fact_ids or [])
    play_ids = list(used_play_ids or [])

    if resolved_mode == AiCopyMode.PROTECTED:
        if fact_ids or play_ids:
            _append_violation(
                violations,
                "EVIDENCE_NOT_ALLOWED_IN_PROTECTED_MODE",
            )

        return {
            "evidence_safe": len(violations) == 0,
            "violations": violations,
        }

    summary_facts = (
        getattr(safe_context, "summary_facts", None)
        if safe_context is not None
        else None
    )

    seen_fact_ids: set[str] = set()

    for fact_id in fact_ids:
        if fact_id in seen_fact_ids:
            _append_violation(
                violations,
                f"EVIDENCE_FACT_DUPLICATE:{fact_id}",
            )
            continue

        seen_fact_ids.add(fact_id)

        attribute_name = FINAL_HEADLINE_FACT_ATTRIBUTES.get(fact_id)

        if attribute_name is None:
            _append_violation(
                violations,
                f"EVIDENCE_FACT_UNKNOWN:{fact_id}",
            )
            continue

        fact_value = (
            getattr(summary_facts, attribute_name, None)
            if summary_facts is not None
            else None
        )

        if fact_value is None:
            _append_violation(
                violations,
                f"EVIDENCE_FACT_UNAVAILABLE:{fact_id}",
            )

    verified_play_ids: set[int] = set()

    if safe_context is not None:
        for play in getattr(safe_context, "verified_plays", []) or []:
            play_id = getattr(play, "play_id", None)

            if (
                isinstance(play_id, int)
                and not isinstance(play_id, bool)
                and play_id > 0
            ):
                verified_play_ids.add(play_id)

    seen_play_ids: set[int] = set()

    for play_id in play_ids:
        if play_id in seen_play_ids:
            _append_violation(
                violations,
                f"EVIDENCE_PLAY_DUPLICATE:{play_id}",
            )
            continue

        seen_play_ids.add(play_id)

        if play_id not in verified_play_ids:
            _append_violation(
                violations,
                f"EVIDENCE_PLAY_UNKNOWN:{play_id}",
            )

    if isinstance(text, str) and text.strip():
        _validate_text_claim_evidence(
            text=text,
            safe_context=safe_context,
            used_fact_ids=fact_ids,
            used_play_ids=play_ids,
            violations=violations,
        )

    return {
        "evidence_safe": len(violations) == 0,
        "violations": violations,
    }
