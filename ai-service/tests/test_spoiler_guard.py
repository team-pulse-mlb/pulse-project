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

    def test_policy_forbidden_words_are_blocked(self):
        forbidden_words = [
            "홈런",
            "역전",
            "끝내기",
            "실점 위기",
            "홈팀 리드",
            "원정팀 리드",
            "점수 차",
            "결말",
            "결과",
            "스코어",
            "동점",
        ]

        for word in forbidden_words:
            with self.subTest(word=word):
                result = check_spoiler_text(f"{word}가 포함된 문구입니다.")

                self.assertFalse(result["spoiler_safe"])
                self.assertIn(f"FORBIDDEN_WORD:{word}", result["violations"])
                self.assertEqual(result["fallback_text"], DEFAULT_FALLBACK_TEXT)

    def test_policy_score_patterns_are_blocked(self):
        unsafe_texts = [
            "3대2 흐름입니다.",
            "3:2 흐름입니다.",
            "3-2 흐름입니다.",
            "3점 차입니다.",
            "몇 점 차인지 궁금한 경기입니다.",
        ]

        for text in unsafe_texts:
            with self.subTest(text=text):
                result = check_spoiler_text(text)

                self.assertFalse(result["spoiler_safe"])
                self.assertIn("SCORE_PATTERN", result["violations"])
                self.assertEqual(result["fallback_text"], DEFAULT_FALLBACK_TEXT)


if __name__ == "__main__":
    unittest.main()

    