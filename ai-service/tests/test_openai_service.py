import unittest
from unittest.mock import patch

from app.schemas.ai_schema import SafeContext, SpoilerFreeSummaryRequest
from app.services import openai_service


class OpenAiServiceTestCase(unittest.TestCase):
    def setUp(self):
        self.original_api_key = openai_service.settings.openai_api_key
        self.request = SpoilerFreeSummaryRequest(
            game_id=5059082,
            mode="PROTECTED",
            surface="HOME_CARD",
            language="ko",
            max_length=80,
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
            ),
        )

    def tearDown(self):
        openai_service.settings.openai_api_key = self.original_api_key

    def test_generate_summary_uses_mock_when_api_key_is_missing(self):
        openai_service.settings.openai_api_key = None

        result = openai_service.generate_spoiler_free_summary(self.request)

        self.assertTrue(result["safe_title"])
        self.assertTrue(result["safe_reason"])
        self.assertTrue(result["notification_text"])
        self.assertEqual(result["tags"], ["후반 긴장 구간"])

    def test_generate_summary_falls_back_when_openai_call_fails(self):
        openai_service.settings.openai_api_key = "fake-api-key"

        with patch(
            "app.services.openai_service._generate_openai_spoiler_free_summary",
            side_effect=RuntimeError("OpenAI error"),
        ):
            result = openai_service.generate_spoiler_free_summary(self.request)

        self.assertTrue(result["safe_title"])
        self.assertTrue(result["safe_reason"])
        self.assertTrue(result["notification_text"])
        self.assertEqual(result["tags"], ["후반 긴장 구간"])


if __name__ == "__main__":
    unittest.main()