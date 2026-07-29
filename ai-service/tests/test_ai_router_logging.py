import unittest
from unittest.mock import patch

from app.routers import ai_router
from app.schemas.ai_schema import (
    AiCopyMode,
    EventCopyRequest,
    EventCopyResponse,
    FinalHeadlineRequest,
    FinalHeadlineResponse,
    PlayTranslationRequest,
    SafeContext,
)
from app.services.openai_service import SpoilerFreeSummaryGenerationError


class AiRouterLoggingTestCase(unittest.TestCase):
    def setUp(self):
        self.original_model = ai_router.settings.openai_model
        self.original_final_headline_model = (
            ai_router.settings.openai_final_headline_model
        )
        self.original_event_copy_model = (
            ai_router.settings.openai_event_copy_model
        )
        self.original_play_translation_model = (
            ai_router.settings.openai_play_translation_model
        )

        ai_router.settings.openai_model = "gpt-5-mini"
        ai_router.settings.openai_final_headline_model = "gpt-5-mini"
        ai_router.settings.openai_event_copy_model = "gpt-5.4-nano"
        ai_router.settings.openai_play_translation_model = "gpt-5-mini"

        self.final_headline_request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.PROTECTED,
            context_hash="final-headline-log-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
            ),
        )

        self.event_copy_request = EventCopyRequest(
            game_id=5059082,
            event_id=10,
            mode=AiCopyMode.PROTECTED,
            context_hash="event-copy-log-v1",
            safe_context=SafeContext(
                event_type="pressure_scoring_position",
                label="득점권 압박",
                inning=7,
            ),
        )

        self.play_translation_request = PlayTranslationRequest(
            game_id=5059041,
            play_id=312,
            mode=AiCopyMode.REVEALED,
            context_hash="play-translation-log-v1",
            source_text="Soto singled to center.",
            target_language="ko",
        )

    def tearDown(self):
        ai_router.settings.openai_model = self.original_model
        ai_router.settings.openai_final_headline_model = (
            self.original_final_headline_model
        )
        ai_router.settings.openai_event_copy_model = (
            self.original_event_copy_model
        )
        ai_router.settings.openai_play_translation_model = (
            self.original_play_translation_model
        )

    def test_generation_failure_logs_error_code_and_request_metadata(self):
        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            side_effect=SpoilerFreeSummaryGenerationError(
                "OPENAI_TIMEOUT"
            ),
        ), patch(
            "app.routers.ai_router.logger.warning"
        ) as mock_warning:
            response = ai_router._generate_checked_copy(
                request=self.final_headline_request,
                response_type=FinalHeadlineResponse,
            )

        self.assertFalse(response.spoiler_safe)
        self.assertEqual(response.violations, ["OPENAI_TIMEOUT"])

        mock_warning.assert_called_once_with(
            "%s purpose=%s gameId=%s eventId=%s mode=%s model=%s violations=%s",
            "AI_COPY_GENERATION_FAILED",
            "FINAL_HEADLINE",
            5059082,
            None,
            "PROTECTED",
            "gpt-5-mini",
            ["OPENAI_TIMEOUT"],
        )

    def test_missing_safe_title_logs_generation_failure(self):
        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value={},
        ), patch(
            "app.routers.ai_router.logger.warning"
        ) as mock_warning:
            response = ai_router._generate_checked_copy(
                request=self.final_headline_request,
                response_type=FinalHeadlineResponse,
            )

        expected_violations = [
            "OPENAI_RESPONSE_MISSING_FIELD:safe_title"
        ]

        self.assertFalse(response.spoiler_safe)
        self.assertEqual(response.violations, expected_violations)

        mock_warning.assert_called_once_with(
            "%s purpose=%s gameId=%s eventId=%s mode=%s model=%s violations=%s",
            "AI_COPY_GENERATION_FAILED",
            "FINAL_HEADLINE",
            5059082,
            None,
            "PROTECTED",
            "gpt-5-mini",
            expected_violations,
        )

    def test_spoiler_guard_rejection_logs_violations(self):
        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value={
                "safe_title": "홈런으로 분위기가 바뀌었습니다.",
            },
        ), patch(
            "app.routers.ai_router.logger.warning"
        ) as mock_warning:
            response = ai_router._generate_checked_copy(
                request=self.event_copy_request,
                response_type=EventCopyResponse,
            )

        self.assertFalse(response.spoiler_safe)
        self.assertIn(
            "FORBIDDEN_WORD:홈런",
            response.violations,
        )

        mock_warning.assert_called_once_with(
            "%s purpose=%s gameId=%s eventId=%s mode=%s model=%s violations=%s",
            "SPOILER_GUARD_REJECTED",
            "EVENT_COPY",
            5059082,
            10,
            "PROTECTED",
            "gpt-5.4-nano",
            response.violations,
        )

    def test_success_logs_metadata_without_generated_title(self):
        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value={
                "safe_title": "7회 득점권 승부가 이어졌습니다.",
            },
        ), patch(
            "app.routers.ai_router.logger.info"
        ) as mock_info:
            response = ai_router._generate_checked_copy(
                request=self.event_copy_request,
                response_type=EventCopyResponse,
            )

        self.assertTrue(response.spoiler_safe)
        self.assertEqual(
            response.safe_title,
            "7회 득점권 승부가 이어졌습니다.",
        )

        mock_info.assert_called_once_with(
            "AI_COPY_GENERATED purpose=%s gameId=%s eventId=%s mode=%s model=%s violations=%s",
            "EVENT_COPY",
            5059082,
            10,
            "PROTECTED",
            "gpt-5.4-nano",
            [],
        )


    def test_play_translation_failure_logs_dedicated_model(self):
        violations = ["OPENAI_TIMEOUT"]

        with patch(
            "app.routers.ai_router.logger.warning"
        ) as mock_warning:
            ai_router._log_play_translation_failure(
                event_name="PLAY_TRANSLATION_GENERATION_FAILED",
                request=self.play_translation_request,
                violations=violations,
            )

        mock_warning.assert_called_once_with(
            "%s purpose=%s gameId=%s playId=%s mode=%s model=%s violations=%s",
            "PLAY_TRANSLATION_GENERATION_FAILED",
            "PLAY_TRANSLATION",
            5059041,
            312,
            "REVEALED",
            "gpt-5-mini",
            violations,
        )

    def test_play_translation_success_logs_dedicated_model(self):
        with patch(
            "app.routers.ai_router.logger.info"
        ) as mock_info:
            ai_router._log_play_translation_success(
                self.play_translation_request
            )

        mock_info.assert_called_once_with(
            "AI_COPY_GENERATED purpose=%s gameId=%s playId=%s mode=%s model=%s violations=%s",
            "PLAY_TRANSLATION",
            5059041,
            312,
            "REVEALED",
            "gpt-5-mini",
            [],
        )


if __name__ == "__main__":
    unittest.main()