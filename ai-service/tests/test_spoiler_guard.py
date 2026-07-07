import unittest

from app.services.spoiler_guard import (
    DEFAULT_FALLBACK_TEXT,
    check_spoiler_text,
)


class SpoilerGuardTestCase(unittest.TestCase):
    def test_safe_text_passes(self):
        result = check_spoiler_text("지금 확인해볼 만한 흐름이 감지됐습니다.")

        self.assertTrue(result["spoiler_safe"])
        self.assertEqual(result["violations"], [])
        self.assertIsNone(result["fallback_text"])

    def test_forbidden_word_is_blocked(self):
        result = check_spoiler_text("홈런이 나온 경기입니다.")

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("FORBIDDEN_WORD:홈런", result["violations"])
        self.assertEqual(result["fallback_text"], DEFAULT_FALLBACK_TEXT)

    def test_score_pattern_is_blocked(self):
        result = check_spoiler_text("경기는 3-2 흐름입니다.")

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("SCORE_PATTERN", result["violations"])
        self.assertEqual(result["fallback_text"], DEFAULT_FALLBACK_TEXT)

    def test_multiple_violations_are_detected(self):
        result = check_spoiler_text("역전 홈런으로 5:4가 됐습니다.")

        self.assertFalse(result["spoiler_safe"])
        self.assertIn("FORBIDDEN_WORD:역전", result["violations"])
        self.assertIn("FORBIDDEN_WORD:홈런", result["violations"])
        self.assertIn("SCORE_PATTERN", result["violations"])
        self.assertEqual(result["fallback_text"], DEFAULT_FALLBACK_TEXT)


if __name__ == "__main__":
    unittest.main()