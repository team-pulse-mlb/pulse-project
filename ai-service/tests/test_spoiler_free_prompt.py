import unittest

from app.prompts.spoiler_free_prompt import build_spoiler_free_prompt
from app.schemas.ai_schema import (
    NotificationTextRequest,
    ReplaySummaryRequest,
    SafeContext,
    SpoilerFreeSummaryRequest,
)


class SpoilerFreePromptTestCase(unittest.TestCase):
    def setUp(self):
        self.safe_context = SafeContext(
            game_status="STATUS_FINAL",
            inning_phase="경기 종료",
            tension_level="NORMAL",
            score_band="RECOMMEND",
            safe_tags=["후반 긴장 구간"],
            reason_codes=["late_or_extra"],
        )

    def test_prompt_contains_safe_context_values(self):
        request = SpoilerFreeSummaryRequest(
            game_id=5059082,
            mode="PROTECTED",
            surface="HOME_CARD",
            language="ko",
            max_length=80,
            safe_context=self.safe_context,
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn("STATUS_FINAL", prompt)
        self.assertIn("경기 종료", prompt)
        self.assertIn("후반 긴장 구간", prompt)
        self.assertIn("late_or_extra", prompt)

    def test_prompt_contains_spoiler_guard_rules(self):
        request = SpoilerFreeSummaryRequest(
            game_id=5059082,
            mode="PROTECTED",
            surface="HOME_CARD",
            language="ko",
            max_length=80,
            safe_context=self.safe_context,
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn("점수", prompt)
        self.assertIn("승패", prompt)
        self.assertIn("우세 팀", prompt)
        self.assertIn("홈런", prompt)
        self.assertIn("역전", prompt)
        self.assertIn("JSON만 반환", prompt)

    def test_prompt_uses_request_language_and_max_length(self):
        request = SpoilerFreeSummaryRequest(
            game_id=5059082,
            mode="PROTECTED",
            surface="HOME_CARD",
            language="ko",
            max_length=80,
            safe_context=self.safe_context,
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"language": "ko"', prompt)
        self.assertIn('"max_length": 80', prompt)

    def test_summary_request_uses_live_headline_purpose(self):
        request = SpoilerFreeSummaryRequest(
            game_id=5059082,
            mode="PROTECTED",
            surface="HOME_CARD",
            language="ko",
            max_length=80,
            safe_context=self.safe_context,
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "LIVE_HEADLINE"', prompt)
        self.assertIn("경기 카드나 상세 화면", prompt)

    def test_notification_request_uses_notification_purpose(self):
        request = NotificationTextRequest(
            game_id=5059082,
            mode="PROTECTED",
            surface="HOME_CARD",
            language="ko",
            max_length=80,
            channel="WEB",
            safe_context=self.safe_context,
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "NOTIFICATION"', prompt)
        self.assertIn("알림 문구", prompt)

    def test_replay_request_uses_replay_summary_purpose(self):
        request = ReplaySummaryRequest(
            game_id=5059082,
            mode="PROTECTED",
            surface="REPLAY_CARD",
            language="ko",
            max_length=80,
            replay_segment_id="segment-5059082-001",
            segment_label="스포일러 없이 다시 보기 좋은 구간",
            segment_reason_tags=["후반 긴장 구간"],
            safe_context=self.safe_context,
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "REPLAY_SUMMARY"', prompt)
        self.assertIn("다시보기 구간", prompt)


if __name__ == "__main__":
    unittest.main()