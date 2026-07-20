import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app.schemas.ai_schema import AiCopyMode, PlayTranslationRequest
from app.services import play_translation_service
from app.services.play_translation_service import (
    PlayTranslationGenerationError,
)


class PlayTranslationServiceTestCase(unittest.TestCase):
    def setUp(self):
        self.original_api_key = (
            play_translation_service.settings.openai_api_key
        )
        self.original_model = (
            play_translation_service.settings.openai_model
        )
        self.original_temperature = (
            play_translation_service.settings.openai_temperature
        )
        self.original_max_output_tokens = (
            play_translation_service.settings.openai_max_output_tokens
        )
        self.original_timeout_seconds = (
            play_translation_service.settings.openai_timeout_seconds
        )
        self.original_play_translation_timeout_seconds = (
            play_translation_service
            .settings
            .openai_play_translation_timeout_seconds
        )
        self.original_play_translation_max_attempts = (
            play_translation_service
            .settings
            .openai_play_translation_max_attempts
        )
        self.original_retry_base_delay_seconds = (
            play_translation_service
            .settings
            .openai_play_translation_retry_base_delay_seconds
        )
        self.original_retry_max_delay_seconds = (
            play_translation_service
            .settings
            .openai_play_translation_retry_max_delay_seconds
        )
        self.original_retry_jitter_seconds = (
            play_translation_service
            .settings
            .openai_play_translation_retry_jitter_seconds
        )

        self.request = PlayTranslationRequest(
            game_id=5059041,
            play_id=312,
            mode=AiCopyMode.REVEALED,
            context_hash="play-312-v1",
            source_text="Soto singled to center.",
            target_language="ko",
        )

    def tearDown(self):
        play_translation_service.settings.openai_api_key = (
            self.original_api_key
        )
        play_translation_service.settings.openai_model = (
            self.original_model
        )
        play_translation_service.settings.openai_temperature = (
            self.original_temperature
        )
        play_translation_service.settings.openai_max_output_tokens = (
            self.original_max_output_tokens
        )
        play_translation_service.settings.openai_timeout_seconds = (
            self.original_timeout_seconds
        )
        (
            play_translation_service
            .settings
            .openai_play_translation_timeout_seconds
        ) = self.original_play_translation_timeout_seconds
        (
            play_translation_service
            .settings
            .openai_play_translation_max_attempts
        ) = self.original_play_translation_max_attempts
        (
            play_translation_service
            .settings
            .openai_play_translation_retry_base_delay_seconds
        ) = self.original_retry_base_delay_seconds
        (
            play_translation_service
            .settings
            .openai_play_translation_retry_max_delay_seconds
        ) = self.original_retry_max_delay_seconds
        (
            play_translation_service
            .settings
            .openai_play_translation_retry_jitter_seconds
        ) = self.original_retry_jitter_seconds

    def test_generate_play_translation_rejects_missing_api_key(self):
        play_translation_service.settings.openai_api_key = ""

        with self.assertRaises(PlayTranslationGenerationError) as context:
            play_translation_service.generate_play_translation(
                self.request
            )

        self.assertEqual(
            str(context.exception),
            "OPENAI_API_KEY_MISSING",
        )

    def test_generate_play_translation_preserves_business_error_after_retry_exhausted(self):
        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )
        play_translation_service.settings.openai_play_translation_max_attempts = 1

        with patch(
            "app.services.play_translation_service._generate_openai_translation",
            side_effect=PlayTranslationGenerationError(
                "OPENAI_INVALID_JSON"
            ),
        ):
            with self.assertRaises(
                PlayTranslationGenerationError
            ) as context:
                play_translation_service.generate_play_translation(
                    self.request
                )

        self.assertEqual(
            str(context.exception),
            "OPENAI_INVALID_JSON",
        )

    def test_generate_play_translation_converts_timeout_error_after_retry_exhausted(self):
        class FakeTimeoutError(Exception):
            pass

        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )
        play_translation_service.settings.openai_play_translation_max_attempts = 1

        with patch.object(
            play_translation_service,
            "APITimeoutError",
            FakeTimeoutError,
        ):
            with patch(
                "app.services.play_translation_service._generate_openai_translation",
                side_effect=FakeTimeoutError("timeout"),
            ):
                with self.assertRaises(
                    PlayTranslationGenerationError
                ) as context:
                    play_translation_service.generate_play_translation(
                        self.request
                    )

        self.assertEqual(
            str(context.exception),
            "OPENAI_TIMEOUT",
        )

    def test_generate_play_translation_converts_unknown_error(self):
        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )

        with patch(
            "app.services.play_translation_service._generate_openai_translation",
            side_effect=RuntimeError("network error"),
        ):
            with self.assertRaises(
                PlayTranslationGenerationError
            ) as context:
                play_translation_service.generate_play_translation(
                    self.request
                )

        self.assertEqual(
            str(context.exception),
            "OPENAI_GENERATION_FAILED",
        )

    def test_generate_openai_translation_uses_json_schema_without_temperature_for_luna(self):
        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )
        play_translation_service.settings.openai_model = (
            "gpt-5.6-luna"
        )
        (
            play_translation_service
            .settings
            .openai_play_translation_timeout_seconds
        ) = 3.0

        with patch(
            "app.services.play_translation_service.OpenAI"
        ) as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.return_value = (
                SimpleNamespace(
                    output_text=(
                        '{"translated_text": '
                        '"Soto, 중견수 방면 안타"}'
                    )
                )
            )

            result = (
                play_translation_service
                ._generate_openai_translation(self.request)
            )

        self.assertEqual(
            result,
            {
                "translated_text": "Soto, 중견수 방면 안타",
            },
        )

        mock_openai.assert_called_once_with(
            api_key="fake-api-key",
            timeout=3.0,
            max_retries=0,
        )

        options = (
            mock_client
            .responses
            .create
            .call_args
            .kwargs
        )

        self.assertEqual(options["model"], "gpt-5.6-luna")
        self.assertIn(
            "Soto singled to center.",
            options["input"],
        )
        self.assertEqual(
            options["max_output_tokens"],
            (
                play_translation_service
                .settings
                .openai_max_output_tokens
            ),
        )
        self.assertEqual(
            options["text"],
            {
                "format": {
                    "type": "json_schema",
                    "name": "play_translation_response",
                    "strict": True,
                    "schema": {
                        "type": "object",
                        "properties": {
                            "translated_text": {
                                "type": "string",
                                "description": (
                                    "MLB Play Result를 한국 야구 중계·기록 "
                                    "용어로 번역한 한 문장"
                                ),
                            },
                        },
                        "required": [
                            "translated_text",
                        ],
                        "additionalProperties": False,
                    },
                },
            },
        )
        self.assertNotIn("temperature", options)

    def test_build_response_create_options_includes_temperature_for_supported_model(self):
        play_translation_service.settings.openai_model = (
            "gpt-4o-mini"
        )
        play_translation_service.settings.openai_temperature = 0.2
        play_translation_service.settings.openai_max_output_tokens = 128

        options = (
            play_translation_service
            ._build_response_create_options(self.request)
        )

        self.assertEqual(options["model"], "gpt-4o-mini")
        self.assertEqual(options["temperature"], 0.2)
        self.assertEqual(options["max_output_tokens"], 128)
        self.assertEqual(
            options["text"]["format"]["type"],
            "json_schema",
        )
        self.assertTrue(
            options["text"]["format"]["strict"],
        )
        self.assertEqual(
            options["text"]["format"]["schema"]["required"],
            ["translated_text"],
        )
        self.assertIn(
            "Soto singled to center.",
            options["input"],
        )

    def test_generate_openai_translation_retries_empty_response_once(self):
        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )
        play_translation_service.settings.openai_play_translation_max_attempts = 2
        play_translation_service.settings.openai_play_translation_retry_jitter_seconds = 0.0

        with patch(
            "app.services.play_translation_service.OpenAI"
        ) as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = [
                SimpleNamespace(output_text=""),
                SimpleNamespace(
                    output_text=(
                        '{"translated_text": '
                        '"Soto, 중견수 방면 안타"}'
                    )
                ),
            ]

            with patch(
                "app.services.play_translation_service._sleep_before_retry"
            ) as mock_sleep:
                result = (
                    play_translation_service
                    ._generate_openai_translation(self.request)
                )

        self.assertEqual(
            result,
            {
                "translated_text": "Soto, 중견수 방면 안타",
            },
        )
        self.assertEqual(
            mock_client.responses.create.call_count,
            2,
        )
        mock_sleep.assert_called_once()

    def test_generate_openai_translation_retries_invalid_json_once(self):
        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )
        play_translation_service.settings.openai_play_translation_max_attempts = 2
        play_translation_service.settings.openai_play_translation_retry_jitter_seconds = 0.0

        with patch(
            "app.services.play_translation_service.OpenAI"
        ) as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = [
                SimpleNamespace(output_text="not-json"),
                SimpleNamespace(
                    output_text=(
                        '{"translated_text": '
                        '"Soto, 중견수 방면 안타"}'
                    )
                ),
            ]

            with patch(
                "app.services.play_translation_service._sleep_before_retry"
            ) as mock_sleep:
                result = (
                    play_translation_service
                    ._generate_openai_translation(self.request)
                )

        self.assertEqual(
            result,
            {
                "translated_text": "Soto, 중견수 방면 안타",
            },
        )
        self.assertEqual(
            mock_client.responses.create.call_count,
            2,
        )
        mock_sleep.assert_called_once()

    def test_generate_openai_translation_retries_timeout_once(self):
        class FakeTimeoutError(Exception):
            pass

        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )
        play_translation_service.settings.openai_play_translation_max_attempts = 2
        play_translation_service.settings.openai_play_translation_retry_jitter_seconds = 0.0

        with patch.object(
            play_translation_service,
            "APITimeoutError",
            FakeTimeoutError,
        ):
            with patch(
                "app.services.play_translation_service.OpenAI"
            ) as mock_openai:
                mock_client = mock_openai.return_value
                mock_client.responses.create.side_effect = [
                    FakeTimeoutError("timeout"),
                    SimpleNamespace(
                        output_text=(
                            '{"translated_text": '
                            '"Soto, 중견수 방면 안타"}'
                        )
                    ),
                ]

                with patch(
                    "app.services.play_translation_service._sleep_before_retry"
                ) as mock_sleep:
                    result = (
                        play_translation_service
                        ._generate_openai_translation(self.request)
                    )

        self.assertEqual(
            result,
            {
                "translated_text": "Soto, 중견수 방면 안타",
            },
        )
        self.assertEqual(
            mock_client.responses.create.call_count,
            2,
        )
        mock_sleep.assert_called_once()

    def test_generate_openai_translation_does_not_retry_unknown_error(self):
        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )
        play_translation_service.settings.openai_play_translation_max_attempts = 2

        with patch(
            "app.services.play_translation_service.OpenAI"
        ) as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = RuntimeError(
                "network error"
            )

            with self.assertRaises(RuntimeError):
                (
                    play_translation_service
                    ._generate_openai_translation(self.request)
                )

        self.assertEqual(
            mock_client.responses.create.call_count,
            1,
        )

    def test_generate_openai_translation_raises_last_retryable_error_after_exhausted(self):
        play_translation_service.settings.openai_api_key = (
            "fake-api-key"
        )
        play_translation_service.settings.openai_play_translation_max_attempts = 2
        play_translation_service.settings.openai_play_translation_retry_jitter_seconds = 0.0

        with patch(
            "app.services.play_translation_service.OpenAI"
        ) as mock_openai:
            mock_client = mock_openai.return_value
            mock_client.responses.create.side_effect = [
                SimpleNamespace(output_text="not-json"),
                SimpleNamespace(output_text="not-json"),
            ]

            with patch(
                "app.services.play_translation_service._sleep_before_retry"
            ) as mock_sleep:
                with self.assertRaises(
                    PlayTranslationGenerationError
                ) as context:
                    (
                        play_translation_service
                        ._generate_openai_translation(self.request)
                    )

        self.assertEqual(
            str(context.exception),
            "OPENAI_INVALID_JSON",
        )
        self.assertEqual(
            mock_client.responses.create.call_count,
            2,
        )
        mock_sleep.assert_called_once()

    def test_parse_openai_translation_trims_translated_text(self):
        result = play_translation_service._parse_openai_translation(
            '{"translated_text": "  Soto, 중견수 방면 안타  "}'
        )

        self.assertEqual(
            result,
            {
                "translated_text": "Soto, 중견수 방면 안타",
            },
        )

    def test_parse_openai_translation_rejects_empty_response(self):
        with self.assertRaises(PlayTranslationGenerationError) as context:
            play_translation_service._parse_openai_translation("")

        self.assertEqual(
            str(context.exception),
            "OPENAI_EMPTY_RESPONSE",
        )

    def test_parse_openai_translation_rejects_invalid_json(self):
        with self.assertRaises(PlayTranslationGenerationError) as context:
            play_translation_service._parse_openai_translation(
                "not-json"
            )

        self.assertEqual(
            str(context.exception),
            "OPENAI_INVALID_JSON",
        )

    def test_parse_openai_translation_rejects_non_object_json(self):
        with self.assertRaises(PlayTranslationGenerationError) as context:
            play_translation_service._parse_openai_translation(
                '["translated_text"]'
            )

        self.assertEqual(
            str(context.exception),
            "OPENAI_INVALID_RESPONSE_TYPE",
        )

    def test_parse_openai_translation_rejects_missing_translated_text(self):
        with self.assertRaises(PlayTranslationGenerationError) as context:
            play_translation_service._parse_openai_translation(
                '{"other": "value"}'
            )

        self.assertEqual(
            str(context.exception),
            "OPENAI_RESPONSE_MISSING_FIELD:translated_text",
        )

    def test_parse_openai_translation_rejects_blank_translated_text(self):
        with self.assertRaises(PlayTranslationGenerationError) as context:
            play_translation_service._parse_openai_translation(
                '{"translated_text": "   "}'
            )

        self.assertEqual(
            str(context.exception),
            "OPENAI_RESPONSE_MISSING_FIELD:translated_text",
        )


if __name__ == "__main__":
    unittest.main()