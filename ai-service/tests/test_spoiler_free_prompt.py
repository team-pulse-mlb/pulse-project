import unittest

from app.prompts.spoiler_free_prompt import build_spoiler_free_prompt
from app.schemas.ai_schema import (
    AiCopyMode,
    EventCopyRequest,
    FinalHeadlineRequest,
    FinalScore,
    KeyMoment,
    SafeContext,
)


class SpoilerFreePromptTestCase(unittest.TestCase):
    def test_final_headline_prompt_contains_protected_safe_context_values(self):
        request = FinalHeadlineRequest(
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
                key_moments=[
                    KeyMoment(
                        inning=7,
                        label="만루 승부",
                    )
                ],
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "FINAL_HEADLINE"', prompt)
        self.assertIn('"mode": "PROTECTED"', prompt)
        self.assertIn('"language": "ko"', prompt)
        self.assertIn('"maxLength": 80', prompt)
        self.assertIn("STATUS_FINAL", prompt)
        self.assertIn("경기 종료", prompt)
        self.assertIn("HIGH", prompt)
        self.assertIn("RECOMMEND", prompt)
        self.assertIn("후반 긴장 구간", prompt)
        self.assertIn("late_or_extra", prompt)
        self.assertIn("만루 승부", prompt)

    def test_final_headline_protected_prompt_contains_spoiler_rules(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.PROTECTED,
            context_hash="game-5059082-final-protected-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                safe_tags=["후반 긴장 구간"],
                reason_codes=["late_or_extra"],
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn("점수", prompt)
        self.assertIn("승패", prompt)
        self.assertIn("승자", prompt)
        self.assertIn("패자", prompt)
        self.assertIn("홈런", prompt)
        self.assertIn("역전", prompt)
        self.assertIn("JSON 객체 하나만 반환", prompt)
        self.assertIn("safe_title", prompt)

    def test_final_headline_revealed_prompt_includes_result_only_when_context_has_it(self):
        request = FinalHeadlineRequest(
            game_id=5059082,
            mode=AiCopyMode.REVEALED,
            context_hash="game-5059082-final-revealed-v1",
            safe_context=SafeContext(
                game_status="STATUS_FINAL",
                inning_phase="경기 종료",
                final_score=FinalScore(
                    home=5,
                    away=3,
                ),
                winner="home",
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "FINAL_HEADLINE"', prompt)
        self.assertIn('"mode": "REVEALED"', prompt)
        self.assertIn('"finalScore"', prompt)
        self.assertIn('"home": 5', prompt)
        self.assertIn('"away": 3', prompt)
        self.assertIn('"winner": "home"', prompt)
        self.assertIn("safeContext에 실제로 포함된 점수", prompt)

    def test_event_copy_protected_prompt_contains_only_protected_event_context(self):
        request = EventCopyRequest(
            game_id=5059041,
            event_id=91,
            mode=AiCopyMode.PROTECTED,
            context_hash="event-91-protected-v1",
            safe_context=SafeContext(
                event_type="pressure_bases_loaded",
                label="만루 승부",
                inning=7,
                inning_type="Top",
                batter="Kim",
                pitcher="Steele",
                evidence={
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                },
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "EVENT_COPY"', prompt)
        self.assertIn('"mode": "PROTECTED"', prompt)
        self.assertIn('"eventType": "pressure_bases_loaded"', prompt)
        self.assertIn('"label": "만루 승부"', prompt)
        self.assertIn('"inning": 7', prompt)
        self.assertNotIn('"inningType": "Top"', prompt)
        self.assertNotIn('"batter": "Kim"', prompt)
        self.assertNotIn('"pitcher": "Steele"', prompt)
        self.assertNotIn('"evidence"', prompt)

    def test_event_copy_revealed_prompt_includes_revealed_event_context(self):
        request = EventCopyRequest(
            game_id=5059041,
            event_id=91,
            mode=AiCopyMode.REVEALED,
            context_hash="event-91-revealed-v1",
            safe_context=SafeContext(
                event_type="pressure_bases_loaded",
                label="만루 승부",
                inning=7,
                inning_type="Top",
                batter="Kim",
                pitcher="Steele",
                evidence={
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                },
            ),
        )

        prompt = build_spoiler_free_prompt(request)

        self.assertIn('"purpose": "EVENT_COPY"', prompt)
        self.assertIn('"mode": "REVEALED"', prompt)
        self.assertIn('"eventType": "pressure_bases_loaded"', prompt)
        self.assertIn('"label": "만루 승부"', prompt)
        self.assertIn('"inning": 7', prompt)
        self.assertIn('"inningType": "Top"', prompt)
        self.assertIn('"batter": "Kim"', prompt)
        self.assertIn('"pitcher": "Steele"', prompt)
        self.assertIn('"evidence"', prompt)
        self.assertIn('"outs": 2', prompt)
        self.assertIn('"balls": 3', prompt)
        self.assertIn('"strikes": 2', prompt)

    def test_unsupported_request_type_raises_type_error(self):
        with self.assertRaises(TypeError):
            build_spoiler_free_prompt(object())


if __name__ == "__main__":
    unittest.main()