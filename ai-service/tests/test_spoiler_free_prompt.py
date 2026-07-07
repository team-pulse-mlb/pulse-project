import unittest

from app.prompts.spoiler_free_prompt import build_spoiler_free_prompt
from app.schemas.ai_schema import SafeContext, SpoilerFreeSummaryRequest


class SpoilerFreePromptTestCase(unittest.TestCase):
    def setUp(self):
        self.request = SpoilerFreeSummaryRequest(
            game_id=5059082,
            mode="PROTECTED",
            surface="HOME_CARD",
            language="ko",
            max_length=80,
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                tension_level="NORMAL",
                score_band="RECOMMEND",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
            ),
        )

    def test_prompt_contains_safe_context_values(self):
        prompt = build_spoiler_free_prompt(self.request)

        self.assertIn("STATUS_FINAL", prompt)
        self.assertIn("경기 종료", prompt)
        self.assertIn("후반 긴장 구간", prompt)
        self.assertIn("late_or_extra", prompt)

    def test_prompt_contains_spoiler_guard_rules(self):
        prompt = build_spoiler_free_prompt(self.request)

        self.assertIn("점수", prompt)
        self.assertIn("승패", prompt)
        self.assertIn("우세 팀", prompt)
        self.assertIn("홈런", prompt)
        self.assertIn("역전", prompt)
        self.assertIn("JSON만 반환", prompt)

    def test_prompt_uses_request_language_and_max_length(self):
        prompt = build_spoiler_free_prompt(self.request)

        self.assertIn('"language": "ko"', prompt)
        self.assertIn('"max_length": 80', prompt)


if __name__ == "__main__":
    unittest.main()