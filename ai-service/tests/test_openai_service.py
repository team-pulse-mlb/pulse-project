import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app.schemas.ai_schema import (
    AiCopyMode,
    FinalHeadlineRequest,
    SafeContext,
)
from app.services import openai_service


class OpenAiServiceTestCase(unittest.TestCase):
    def setUp(self):
        self.original_api_key = openai_service.settings.openai_api_key
        self.original_model = openai_service.settings.openai_model

        self.request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.PROTECTED,
            context_hash="game-5059082-final-protected-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                tension_level="HIGH",
                score_band="RECOMMEND",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
            ),
        )

    def tearDown(self):
        openai_service.settings.openai_api_key = self.original_api_key
        openai_service.settings.openai_model = self.original_model

    def test_generate_summary_raises_error_when_api_key_is_missing(self):
        openai_service.settings.openai_api_key = None

        with self.assertRaises(
            openai_service.SpoilerFreeSummaryGenerationError
        ) as context:
            openai_service.generate_spoiler_free_summary(self.request)

        self.assertEqual(str(context.exception), "OPENAI_API_KEY_MISSING")

    def test_generate_summary_propagates_known_generation_error(self):
        openai_service.settings.openai_api_key = "fake-api-key"

        with patch(
            "app.services.openai_service._generate_openai_copy",
            side_effect=openai_service.SpoilerFreeSummaryGenerationError(
                "OPENAI_INVALID_JSON"
            ),
        ):
            with self.assertRaises(
                openai_service.SpoilerFreeSummaryGenerationError
            ) as context:
                openai_service.generate_spoiler_free_summary(self.request)

        self.assertEqual(str(context.exception), "OPENAI_INVALID_JSON")

    def test_generate_summary_raises_error_when_openai_call_fails(self):
        openai_service.settings.openai_api_key = "fake-api-key"

        with patch(
            "app.services.openai_service._generate_openai_copy",
            side_effect=RuntimeError("OpenAI error"),
        ), patch(
            "app.services.openai_service.logger.exception"
        ) as mock_logger_exception:
            with self.assertRaises(
                openai_service.SpoilerFreeSummaryGenerationError
            ) as context:
                openai_service.generate_spoiler_free_summary(self.request)

        self.assertEqual(str(context.exception), "OPENAI_GENERATION_FAILED")
        self.assertTrue(mock_logger_exception.called)

    def test_generate_openai_copy_includes_temperature_for_gpt_4o_mini(
        self,
    ):
        openai_service.settings.openai_api_key = "fake-api-key"
        openai_service.settings.openai_model = "gpt-4o-mini"

        with patch("app.services.openai_service.OpenAI") as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.return_value = SimpleNamespace(
                output_text='{"safe_title": "후반 긴장감이 이어진 경기"}'
            )

            result = openai_service._generate_openai_copy(self.request)

        request_options = mock_client.responses.create.call_args.kwargs

        self.assertEqual(request_options["model"], "gpt-4o-mini")
        self.assertEqual(
            request_options["temperature"],
            openai_service.settings.openai_temperature,
        )
        self.assertEqual(
            result,
            {"safe_title": "후반 긴장감이 이어진 경기"},
        )

    def test_generate_openai_copy_omits_temperature_for_gpt_5_6_luna(
        self,
    ):
        openai_service.settings.openai_api_key = "fake-api-key"
        openai_service.settings.openai_model = "gpt-5.6-luna"

        with patch("app.services.openai_service.OpenAI") as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.return_value = SimpleNamespace(
                output_text='{"safe_title": "후반 긴장감이 이어진 경기"}'
            )

            result = openai_service._generate_openai_copy(self.request)

        request_options = mock_client.responses.create.call_args.kwargs

        self.assertEqual(request_options["model"], "gpt-5.6-luna")
        self.assertNotIn("temperature", request_options)
        self.assertEqual(
            request_options["max_output_tokens"],
            openai_service.settings.openai_max_output_tokens,
        )
        self.assertEqual(
            request_options["text"],
            {"format": {"type": "json_object"}},
        )
        self.assertEqual(
            result,
            {"safe_title": "후반 긴장감이 이어진 경기"},
        )

    def test_parse_openai_copy_returns_safe_title(self):
        result = openai_service._parse_openai_copy(
            '{"safe_title": "후반 긴장감이 올라간 경기였습니다."}'
        )

        self.assertEqual(
            result,
            {
                "safe_title": "후반 긴장감이 올라간 경기였습니다.",
            },
        )

    def test_parse_openai_copy_strips_safe_title(self):
        result = openai_service._parse_openai_copy(
            '{"safe_title": "  후반 긴장감이 올라간 경기였습니다.  "}'
        )

        self.assertEqual(
            result["safe_title"],
            "후반 긴장감이 올라간 경기였습니다.",
        )

    def test_parse_openai_copy_rejects_empty_response(self):
        with self.assertRaises(
            openai_service.SpoilerFreeSummaryGenerationError
        ) as context:
            openai_service._parse_openai_copy("")

        self.assertEqual(str(context.exception), "OPENAI_EMPTY_RESPONSE")

    def test_parse_openai_copy_rejects_invalid_json(self):
        with self.assertRaises(
            openai_service.SpoilerFreeSummaryGenerationError
        ) as context:
            openai_service._parse_openai_copy("not-json")

        self.assertEqual(str(context.exception), "OPENAI_INVALID_JSON")

    def test_parse_openai_copy_rejects_non_object_json(self):
        with self.assertRaises(
            openai_service.SpoilerFreeSummaryGenerationError
        ) as context:
            openai_service._parse_openai_copy('["safe_title"]')

        self.assertEqual(
            str(context.exception),
            "OPENAI_INVALID_RESPONSE_TYPE",
        )

    def test_parse_openai_copy_rejects_missing_safe_title(self):
        with self.assertRaises(
            openai_service.SpoilerFreeSummaryGenerationError
        ) as context:
            openai_service._parse_openai_copy('{"safe_reason": "이유"}')

        self.assertEqual(
            str(context.exception),
            "OPENAI_RESPONSE_MISSING_FIELD:safe_title",
        )

    def test_parse_openai_copy_rejects_blank_safe_title(self):
        with self.assertRaises(
            openai_service.SpoilerFreeSummaryGenerationError
        ) as context:
            openai_service._parse_openai_copy('{"safe_title": "   "}')

        self.assertEqual(
            str(context.exception),
            "OPENAI_RESPONSE_MISSING_FIELD:safe_title",
        )


if __name__ == "__main__":
    unittest.main()
