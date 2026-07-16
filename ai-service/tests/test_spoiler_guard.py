import unittest

from app.schemas.ai_schema import AiCopyMode, FinalScore, SafeContext
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
    