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
        self.original_max_output_tokens = (
            openai_service.settings.openai_max_output_tokens
        )
        self.original_timeout_seconds = (
            openai_service.settings.openai_timeout_seconds
        )
        self.original_ai_copy_timeout_seconds = (
            openai_service.settings.openai_ai_copy_timeout_seconds
        )
        self.original_ai_copy_max_attempts = (
            openai_service.settings.openai_ai_copy_max_attempts
        )
        self.original_retry_base_delay_seconds = (
            openai_service
            .settings
            .openai_ai_copy_retry_base_delay_seconds
        )
        self.original_retry_max_delay_seconds = (
            openai_service
            .settings
            .openai_ai_copy_retry_max_delay_seconds
        )
        self.original_retry_jitter_seconds = (
            openai_service
            .settings
            .openai_ai_copy_retry_jitter_seconds
        )

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
        openai_service.settings.openai_max_output_tokens = (
            self.original_max_output_tokens
        )
        openai_service.settings.openai_timeout_seconds = (
            self.original_timeout_seconds
        )
        openai_service.settings.openai_ai_copy_timeout_seconds = (
            self.original_ai_copy_timeout_seconds
        )
        openai_service.settings.openai_ai_copy_max_attempts = (
            self.original_ai_copy_max_attempts
        )
        openai_service.settings.openai_ai_copy_retry_base_delay_seconds = (
            self.original_retry_base_delay_seconds
        )
        openai_service.settings.openai_ai_copy_retry_max_delay_seconds = (
            self.original_retry_max_delay_seconds
        )
        openai_service.settings.openai_ai_copy_retry_jitter_seconds = (
            self.original_retry_jitter_seconds
        )

    def _disable_retry_sleep(self):
        openai_service.settings.openai_ai_copy_retry_base_delay_seconds = 0.0
        openai_service.settings.openai_ai_copy_retry_max_delay_seconds = 0.0
        openai_service.settings.openai_ai_copy_retry_jitter_seconds = 0.0

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
        self.assertNotIn("reasoning", request_options)
        self.assertEqual(
            result,
            {"safe_title": "후반 긴장감이 이어진 경기"},
        )

    def test_generate_openai_copy_uses_json_schema_without_temperature_for_luna(
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
            request_options["reasoning"],
            {
                "effort": openai_service.settings.openai_reasoning_effort,
            },
        )
        self.assertEqual(
            request_options["max_output_tokens"],
            openai_service.settings.openai_max_output_tokens,
        )
        self.assertEqual(
            request_options["text"]["format"]["type"],
            "json_schema",
        )
        self.assertTrue(
            request_options["text"]["format"]["strict"]
        )
        self.assertEqual(
            request_options["text"]["format"]["schema"]["required"],
            ["safe_title"],
        )
        self.assertFalse(
            request_options["text"]["format"]["schema"][
                "additionalProperties"
            ]
        )
        self.assertEqual(
            result,
            {"safe_title": "후반 긴장감이 이어진 경기"},
        )

    def test_generate_openai_copy_retries_empty_response_once(self):
        openai_service.settings.openai_api_key = "fake-api-key"
        openai_service.settings.openai_ai_copy_max_attempts = 2
        self._disable_retry_sleep()

        with patch("app.services.openai_service.OpenAI") as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = [
                SimpleNamespace(output_text=""),
                SimpleNamespace(
                    output_text='{"safe_title": "재시도 후 정상 문구"}'
                ),
            ]

            result = openai_service._generate_openai_copy(self.request)

        self.assertEqual(
            result,
            {"safe_title": "재시도 후 정상 문구"},
        )
        self.assertEqual(
            mock_client.responses.create.call_count,
            2,
        )

    def test_parse_openai_response_rejects_max_output_tokens(self):
        response = SimpleNamespace(
            status="incomplete",
            incomplete_details=SimpleNamespace(
                reason="max_output_tokens",
            ),
            output_text="",
        )

        with self.assertRaises(
            openai_service.SpoilerFreeSummaryGenerationError
        ) as context:
            openai_service._parse_openai_response(response)

        self.assertEqual(
            str(context.exception),
            "OPENAI_MAX_OUTPUT_TOKENS",
        )

    def test_parse_openai_response_rejects_content_filter(self):
        response = SimpleNamespace(
            status="incomplete",
            incomplete_details=SimpleNamespace(
                reason="content_filter",
            ),
            output_text="",
        )

        with self.assertRaises(
            openai_service.SpoilerFreeSummaryGenerationError
        ) as context:
            openai_service._parse_openai_response(response)

        self.assertEqual(
            str(context.exception),
            "OPENAI_CONTENT_FILTER",
        )

    def test_generate_openai_copy_retries_invalid_json_once(self):
        openai_service.settings.openai_api_key = "fake-api-key"
        openai_service.settings.openai_ai_copy_max_attempts = 2
        self._disable_retry_sleep()

        with patch("app.services.openai_service.OpenAI") as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = [
                SimpleNamespace(output_text="not-json"),
                SimpleNamespace(
                    output_text='{"safe_title": "JSON 재시도 성공"}'
                ),
            ]

            result = openai_service._generate_openai_copy(self.request)

        self.assertEqual(
            result,
            {"safe_title": "JSON 재시도 성공"},
        )
        self.assertEqual(
            mock_client.responses.create.call_count,
            2,
        )

    def test_generate_openai_copy_retries_timeout_once(self):
        openai_service.settings.openai_api_key = "fake-api-key"
        openai_service.settings.openai_ai_copy_max_attempts = 2
        self._disable_retry_sleep()

        class FakeTimeoutError(Exception):
            pass

        with patch(
            "app.services.openai_service.APITimeoutError",
            FakeTimeoutError,
        ), patch("app.services.openai_service.OpenAI") as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = [
                FakeTimeoutError("timeout"),
                SimpleNamespace(
                    output_text='{"safe_title": "타임아웃 재시도 성공"}'
                ),
            ]

            result = openai_service._generate_openai_copy(self.request)

        self.assertEqual(
            result,
            {"safe_title": "타임아웃 재시도 성공"},
        )
        self.assertEqual(
            mock_client.responses.create.call_count,
            2,
        )

    def test_generate_openai_copy_does_not_retry_unknown_error(self):
        openai_service.settings.openai_api_key = "fake-api-key"
        openai_service.settings.openai_ai_copy_max_attempts = 2
        self._disable_retry_sleep()

        with patch("app.services.openai_service.OpenAI") as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = RuntimeError(
                "unknown"
            )

            with self.assertRaises(RuntimeError):
                openai_service._generate_openai_copy(self.request)

        self.assertEqual(
            mock_client.responses.create.call_count,
            1,
        )

    def test_generate_openai_copy_raises_last_retryable_error_after_exhausted(
        self,
    ):
        openai_service.settings.openai_api_key = "fake-api-key"
        openai_service.settings.openai_ai_copy_max_attempts = 2
        self._disable_retry_sleep()

        with patch("app.services.openai_service.OpenAI") as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = [
                SimpleNamespace(output_text="not-json"),
                SimpleNamespace(output_text="still-not-json"),
            ]

            with self.assertRaises(
                openai_service.SpoilerFreeSummaryGenerationError
            ) as context:
                openai_service._generate_openai_copy(self.request)

        self.assertEqual(str(context.exception), "OPENAI_INVALID_JSON")
        self.assertEqual(
            mock_client.responses.create.call_count,
            2,
        )

    def test_generate_summary_converts_timeout_error_after_retry_exhausted(
        self,
    ):
        openai_service.settings.openai_api_key = "fake-api-key"

        class FakeTimeoutError(Exception):
            pass

        with patch(
            "app.services.openai_service.APITimeoutError",
            FakeTimeoutError,
        ), patch(
            "app.services.openai_service._generate_openai_copy",
            side_effect=FakeTimeoutError("timeout"),
        ), patch(
            "app.services.openai_service.logger.exception"
        ) as mock_logger_exception:
            with self.assertRaises(
                openai_service.SpoilerFreeSummaryGenerationError
            ) as context:
                openai_service.generate_spoiler_free_summary(self.request)

        self.assertEqual(str(context.exception), "OPENAI_TIMEOUT")
        self.assertTrue(mock_logger_exception.called)

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
