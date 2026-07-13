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
            context_hash="game-5059082-status-final-v3",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                tension_level="NORMAL",
                score_band="RECOMMEND",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
            ),
        )

    def tearDown(self):
        openai_service.settings.openai_api_key = self.original_api_key

    def test_generate_summary_raises_error_when_api_key_is_missing(self):
        openai_service.settings.openai_api_key = None

        with self.assertRaises(openai_service.SpoilerFreeSummaryGenerationError) as context:
            openai_service.generate_spoiler_free_summary(self.request)

        self.assertEqual(str(context.exception), "OPENAI_API_KEY_MISSING")

    def test_generate_summary_raises_error_when_openai_call_fails(self):
        openai_service.settings.openai_api_key = "fake-api-key"

        with patch(
            "app.services.openai_service._generate_openai_spoiler_free_summary",
            side_effect=RuntimeError("OpenAI error"),
        ), patch("app.services.openai_service.logger.exception") as mock_logger_exception:
            with self.assertRaises(openai_service.SpoilerFreeSummaryGenerationError) as context:
                openai_service.generate_spoiler_free_summary(self.request)

        self.assertEqual(str(context.exception), "OPENAI_GENERATION_FAILED")
        self.assertTrue(mock_logger_exception.called)


if __name__ == "__main__":
    unittest.main()