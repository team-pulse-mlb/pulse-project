import unittest

from app.schemas.ai_schema import (
    AiCopyMode,
    FinalScore,
    SafeContext,
    SummaryFacts,
    VerifiedPlay,
)
from app.services.spoiler_guard import check_spoiler_text


class SpoilerGuardTestCase(unittest.TestCase):
    def test_protected_safe_text_passes(self):
        result = check_spoiler_text(
            "지금 확인해볼 만한 흐름이 감지됐습니다.",
            mode=AiCopyMode.PROTECTED,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])
        self.assertNotIn("fallback_text", result)

    def test_protected_forbidden_word_is_blocked(self):
        result = check_spoiler_text(
            "홈런이 나온 경기입니다.",
            mode=AiCopyMode.PROTECTED,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("FORBIDDEN_WORD:홈런", result["violations"])
        self.assertNotIn("fallback_text", result)

    def test_protected_score_pattern_is_blocked(self):
        result = check_spoiler_text(
            "경기는 3-2 흐름입니다.",
            mode=AiCopyMode.PROTECTED,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("SCORE_PATTERN", result["violations"])
        self.assertNotIn("fallback_text", result)

    def test_protected_multiple_violations_are_detected(self):
        result = check_spoiler_text(
            "역전 홈런으로 5:4가 됐습니다.",
            mode=AiCopyMode.PROTECTED,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("FORBIDDEN_WORD:역전", result["violations"])
        self.assertIn("FORBIDDEN_WORD:홈런", result["violations"])
        self.assertIn("SCORE_PATTERN", result["violations"])
        self.assertNotIn("fallback_text", result)

    def test_protected_policy_forbidden_words_are_blocked(self):
        forbidden_words = [
            "홈런",
            "역전",
            "끝내기",
            "홈팀 리드",
            "원정팀 리드",
            "결말",
            "결과",
            "스코어",
            "동점",
        ]

        for word in forbidden_words:
            with self.subTest(word=word):
                result = check_spoiler_text(
                    f"{word}가 포함된 문구입니다.",
                    mode=AiCopyMode.PROTECTED,
                )

                self.assertFalse(result["spoiler_safe"])
                self.assertIn(
                    f"FORBIDDEN_WORD:{word}",
                    result["violations"],
                )
                self.assertNotIn("fallback_text", result)

    def test_protected_score_patterns_are_blocked(self):
        unsafe_texts = [
            "3대2 흐름입니다.",
            "3:2 흐름입니다.",
            "3-2 흐름입니다.",
            "3점 차입니다.",
        ]

        for text in unsafe_texts:
            with self.subTest(text=text):
                result = check_spoiler_text(
                    text,
                    mode=AiCopyMode.PROTECTED,
                )

                self.assertFalse(result["spoiler_safe"])
                self.assertIn("SCORE_PATTERN", result["violations"])
                self.assertNotIn("fallback_text", result)

    def test_protected_allows_scoring_position_word(self):
        result = check_spoiler_text(
            "7회 득점권 승부가 이어졌습니다.",
            mode=AiCopyMode.PROTECTED,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_home_win_with_home_away_score_passes(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
        )

        result = check_spoiler_text(
            "홈팀이 5-3으로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_away_win_with_home_away_score_passes(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=3, away=5),
            winner="away",
        )

        result = check_spoiler_text(
            "원정팀이 3-5로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_away_win_sentence_with_both_team_words_passes(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=3, away=5),
            winner="away",
        )

        result = check_spoiler_text(
            "홈팀과 원정팀의 접전 끝 원정팀이 3-5로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_summary_facts_support_result_claim_words(self):
        safe_context = SafeContext(
            summary_facts=SummaryFacts(
                comeback_win=True,
                walk_off=True,
                shutout=True,
            )
        )

        for text in [
            "후반 역전 장면",
            "9회 끝내기 장면",
            "영봉 경기",
        ]:
            with self.subTest(text=text):
                result = check_spoiler_text(
                    text,
                    mode=AiCopyMode.REVEALED,
                    safe_context=safe_context,
                )

                self.assertTrue(result["spoiler_safe"])
                self.assertEqual(result["violations"], [])

    def test_revealed_false_summary_fact_does_not_support_claim(self):
        safe_context = SafeContext(
            summary_facts=SummaryFacts(
                walk_off=False,
            )
        )

        result = check_spoiler_text(
            "9회 끝내기 장면",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn(
            "FORBIDDEN_WORD:끝내기",
            result["violations"],
        )

    def test_revealed_decisive_score_play_supports_walk_off_hit_word(self):
        safe_context = SafeContext(
            verified_plays=[
                VerifiedPlay(
                    play_id=312,
                    fact_tags=[
                        "DECISIVE_SCORE",
                        "HIT",
                    ],
                )
            ]
        )

        result = check_spoiler_text(
            "9회 결승타",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_verified_play_tags_support_additional_flow_words(self):
        safe_context = SafeContext(
            winner="home",
            verified_plays=[
                VerifiedPlay(
                    play_id=401,
                    scoring_play=True,
                    fact_tags=[
                        "TYING_SCORE",
                        "HIT",
                    ],
                ),
                VerifiedPlay(
                    play_id=402,
                    scoring_play=True,
                    fact_tags=[
                        "INSURANCE_SCORE",
                        "HIT",
                    ],
                ),
                VerifiedPlay(
                    play_id=403,
                    scoring_play=True,
                    fact_tags=[
                        "RUNS_SCORED",
                        "TRAILS_AFTER",
                        "CUTS_DEFICIT",
                    ],
                ),
                VerifiedPlay(
                    play_id=404,
                    scoring_play=True,
                    fact_tags=[
                        "TAKES_LEAD",
                        "LEADS_AFTER",
                    ],
                ),
            ],
        )

        safe_texts = [
            "7회 동점타",
            "8회 쐐기타",
            "한 차례 실점을 허용했습니다",
            "득점 후에도 열세였습니다",
            "한 점 차로 따라붙었습니다",
            "리드를 잡고 앞서기 시작했습니다",
            "우세를 이어갔습니다",
        ]

        for text in safe_texts:
            with self.subTest(text=text):
                result = check_spoiler_text(
                    text,
                    mode=AiCopyMode.REVEALED,
                    safe_context=safe_context,
                )

                self.assertTrue(result["spoiler_safe"])
                self.assertEqual(result["violations"], [])

    def test_revealed_additional_flow_words_without_tags_are_blocked(self):
        safe_context = SafeContext(
            winner="home",
            verified_plays=[
                VerifiedPlay(
                    play_id=499,
                    fact_tags=[],
                )
            ],
        )

        unsafe_words = [
            "동점타",
            "쐐기",
            "실점",
            "열세",
            "따라붙",
            "리드",
            "우세",
            "앞서",
        ]

        for word in unsafe_words:
            with self.subTest(word=word):
                result = check_spoiler_text(
                    f"{word} 장면",
                    mode=AiCopyMode.REVEALED,
                    safe_context=safe_context,
                )

                self.assertFalse(result["spoiler_safe"])
                self.assertIn(
                    f"FORBIDDEN_WORD:{word}",
                    result["violations"],
                )

    def test_revealed_single_team_point_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
        )

        result = check_spoiler_text(
            "홈팀이 5점을 내며 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("UNSUPPORTED_SCORE_FORMAT", result["violations"])

    def test_revealed_colon_score_format_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
        )

        result = check_spoiler_text(
            "홈팀이 5:3으로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("UNSUPPORTED_SCORE_FORMAT", result["violations"])

    def test_revealed_korean_score_format_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
        )

        result = check_spoiler_text(
            "홈팀이 5대3으로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("UNSUPPORTED_SCORE_FORMAT", result["violations"])

    def test_revealed_score_mismatch_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
        )

        result = check_spoiler_text(
            "홈팀이 4-3으로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("SCORE_MISMATCH", result["violations"])

    def test_revealed_score_without_context_is_blocked(self):
        result = check_spoiler_text(
            "홈팀이 5-3으로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=None,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("SCORE_CONTEXT_MISSING", result["violations"])

    def test_revealed_winner_mismatch_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="away",
        )

        result = check_spoiler_text(
            "홈팀이 5-3으로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("WINNER_MISMATCH", result["violations"])

    def test_revealed_ambiguous_winner_reference_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
        )

        result = check_spoiler_text(
            "홈팀 승리, 원정팀 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("WINNER_REFERENCE_AMBIGUOUS", result["violations"])

    def test_revealed_team_name_winner_claim_passes(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
            teams={
                "home": {
                    "name": "Los Angeles Dodgers",
                    "abbr": "LAD",
                },
                "away": {
                    "name": "San Francisco Giants",
                    "abbr": "SF",
                },
            },
            summary_facts={
                "winnerSide": "home",
                "winnerName": "Los Angeles Dodgers",
                "loserName": "San Francisco Giants",
                "winnerScore": 5,
                "loserScore": 3,
            },
        )

        result = check_spoiler_text(
            "Dodgers가 5-3으로 승리한 경기",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_team_name_winner_claim_with_context_backed_extra_inning_modifier_passes(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
            innings_played=10,
            extra_innings=True,
            teams={
                "home": {
                    "name": "Los Angeles Dodgers",
                    "abbr": "LAD",
                },
                "away": {
                    "name": "San Francisco Giants",
                    "abbr": "SF",
                },
            },
        )

        result = check_spoiler_text(
            "Los Angeles Dodgers가 10회 연장 끝에 5-3으로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_team_name_winner_claim_with_wrong_extra_inning_modifier_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
            innings_played=10,
            extra_innings=True,
            teams={
                "home": {
                    "name": "Los Angeles Dodgers",
                    "abbr": "LAD",
                },
                "away": {
                    "name": "San Francisco Giants",
                    "abbr": "SF",
                },
            },
        )

        result = check_spoiler_text(
            "Los Angeles Dodgers가 9회 연장 끝에 5-3으로 승리",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn(
            "WINNER_REFERENCE_UNVERIFIABLE",
            result["violations"],
        )

    def test_revealed_team_abbr_winner_claim_passes(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
            teams={
                "home": {
                    "name": "Los Angeles Dodgers",
                    "abbr": "LAD",
                },
                "away": {
                    "name": "San Francisco Giants",
                    "abbr": "SF",
                },
            },
        )

        result = check_spoiler_text(
            "LAD가 5-3으로 승리한 경기",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_home_run_with_revealed_context_passes(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
            revealed_events=[
                {
                    "eventType": "home_run",
                    "inning": 8,
                }
            ],
            verified_plays=[
                {
                    "sourceText": "Ohtani homered to right center.",
                    "translatedText": "Ohtani, 우중간 홈런",
                    "scoringPlay": True,
                    "factTags": ["SCORING_PLAY", "TRANSLATED"],
                }
            ],
        )

        result = check_spoiler_text(
            "홈팀이 5-3으로 승리, Ohtani 홈런이 나온 경기",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])

    def test_revealed_total_runs_matches_summary_facts_passes(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
            summary_facts={
                "totalRuns": 8,
            },
        )

        safe_texts = [
            "홈팀이 5-3으로 승리, 총 8득점이 나온 경기",
            "홈팀이 5-3으로 승리, 양 팀 합계 8득점이 나온 경기",
        ]

        for text in safe_texts:
            with self.subTest(text=text):
                result = check_spoiler_text(
                    text,
                    mode=AiCopyMode.REVEALED,
                    safe_context=safe_context,
                )

                self.assertTrue(result["spoiler_safe"])
                self.assertEqual(result["violations"], [])

    def test_revealed_total_runs_mismatch_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
            summary_facts={
                "totalRuns": 8,
            },
        )

        result = check_spoiler_text(
            "홈팀이 5-3으로 승리, 총 9득점이 나온 경기",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("TOTAL_RUNS_MISMATCH", result["violations"])

    def test_revealed_total_runs_without_summary_facts_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
        )

        result = check_spoiler_text(
            "홈팀이 5-3으로 승리, 양 팀 합계 8득점이 나온 경기",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn(
            "TOTAL_RUNS_CONTEXT_MISSING",
            result["violations"],
        )

    def test_revealed_unsupported_play_result_is_blocked(self):
        safe_context = SafeContext(
            final_score=FinalScore(home=5, away=3),
            winner="home",
        )

        result = check_spoiler_text(
            "홈런으로 분위기가 바뀐 경기였습니다.",
            mode=AiCopyMode.REVEALED,
            safe_context=safe_context,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("FORBIDDEN_WORD:홈런", result["violations"])

    def test_invalid_text_is_blocked(self):
        result = check_spoiler_text(
            "",
            mode=AiCopyMode.PROTECTED,
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertEqual(result["violations"], ["INVALID_TEXT"])

    def test_invalid_mode_is_blocked(self):
        result = check_spoiler_text(
            "안전한 문구입니다.",
            mode="UNKNOWN",
        )

        self.assertFalse(result["spoiler_safe"])
        self.assertEqual(result["violations"], ["UNSUPPORTED_MODE"])


if __name__ == "__main__":
    unittest.main()