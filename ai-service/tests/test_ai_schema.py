import unittest

from app.schemas.ai_schema import (
    AiCopyMode,
    EventCopyRequest,
    FinalHeadlineRequest,
)


class AiSchemaTestCase(unittest.TestCase):
    def test_final_headline_request_accepts_spring_camel_case_payload(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": "game-5059082-final-protected-v1",
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "safeTags": ["후반 긴장 구간"],
                "reasonCodes": ["late_or_extra"],
                "keyMoments": [
                    {
                        "inning": 7,
                        "label": "만루 승부",
                    }
                ],
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059082)
        self.assertEqual(request.mode, AiCopyMode.PROTECTED)
        self.assertEqual(
            request.context_hash,
            "game-5059082-final-protected-v1",
        )
        self.assertEqual(request.safe_context.game_status, "STATUS_FINAL")
        self.assertEqual(request.safe_context.inning_phase, "경기 종료")
        self.assertEqual(request.safe_context.safe_tags, ["후반 긴장 구간"])
        self.assertEqual(request.safe_context.reason_codes, ["late_or_extra"])
        self.assertEqual(len(request.safe_context.key_moments), 1)
        self.assertEqual(request.safe_context.key_moments[0].inning, 7)
        self.assertEqual(request.safe_context.key_moments[0].label, "만루 승부")

    def test_final_headline_request_accepts_revealed_result_context(self):
        payload = {
            "gameId": 5059082,
            "mode": "REVEALED",
            "contextHash": "game-5059082-final-revealed-v1",
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "finalScore": {
                    "home": 5,
                    "away": 3,
                },
                "winner": "home",
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)

        self.assertEqual(request.mode, AiCopyMode.REVEALED)
        self.assertIsNotNone(request.safe_context.final_score)
        self.assertEqual(request.safe_context.final_score.home, 5)
        self.assertEqual(request.safe_context.final_score.away, 3)
        self.assertEqual(request.safe_context.winner, "home")

    def test_event_copy_request_accepts_spring_camel_case_payload(self):
        payload = {
            "gameId": 5059041,
            "eventId": 91,
            "mode": "REVEALED",
            "contextHash": "event-91-revealed-v1",
            "safeContext": {
                "eventType": "pressure_bases_loaded",
                "label": "만루 승부",
                "inning": 7,
                "inningType": "Top",
                "batter": "Kim",
                "pitcher": "Steele",
                "evidence": {
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                },
            },
        }

        request = EventCopyRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059041)
        self.assertEqual(request.event_id, 91)
        self.assertEqual(request.mode, AiCopyMode.REVEALED)
        self.assertEqual(request.context_hash, "event-91-revealed-v1")
        self.assertEqual(
            request.safe_context.event_type,
            "pressure_bases_loaded",
        )
        self.assertEqual(request.safe_context.label, "만루 승부")
        self.assertEqual(request.safe_context.inning, 7)
        self.assertEqual(request.safe_context.inning_type, "Top")
        self.assertEqual(request.safe_context.batter, "Kim")
        self.assertEqual(request.safe_context.pitcher, "Steele")
        self.assertEqual(
            request.safe_context.evidence,
            {
                "outs": 2,
                "balls": 3,
                "strikes": 2,
            },
        )

    def test_extra_spring_fields_are_ignored(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": "game-5059082-final-protected-v1",
            "purpose": "CARD_SUMMARY",
            "status": "STATUS_FINAL",
            "startTime": "2026-07-03T00:05:00Z",
            "teams": [
                {"id": 28, "name": "Texas Rangers", "abbr": "TEX"},
                {"id": 10, "name": "Detroit Tigers", "abbr": "DET"},
            ],
            "recentPlays": [
                {
                    "type": "Play Result",
                    "text": "Osuna grounded out to second.",
                }
            ],
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "safeTags": ["후반 긴장 구간"],
                "reasonCodes": ["late_or_extra"],
                "unexpectedField": "ignore me",
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059082)
        self.assertEqual(request.safe_context.game_status, "STATUS_FINAL")
        self.assertFalse(hasattr(request, "recent_plays"))
        self.assertFalse(hasattr(request.safe_context, "unexpected_field"))

    def test_missing_optional_safe_context_lists_default_to_empty_lists(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": "game-5059082-final-protected-v1",
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
            },
        }

        request = FinalHeadlineRequest.model_validate(payload)

        self.assertEqual(request.safe_context.safe_tags, [])
        self.assertEqual(request.safe_context.reason_codes, [])
        self.assertEqual(request.safe_context.key_moments, [])


if __name__ == "__main__":
    unittest.main()