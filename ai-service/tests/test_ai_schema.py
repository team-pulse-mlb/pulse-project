import unittest

from app.schemas.ai_schema import (
    AiCopyMode,
    EventCopyRequest,
    EventCopyResponse,
    FinalHeadlineRequest,
    FinalHeadlineResponse,
)


class AiSchemaTestCase(unittest.TestCase):
    def test_final_headline_request_parses_camel_case_payload(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": "game-5059082-final-v3",
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "tensionLevel": "HIGH",
                "scoreBand": "RECOMMEND",
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
        self.assertEqual(request.context_hash, "game-5059082-final-v3")
        self.assertEqual(request.safe_context.game_status, "STATUS_FINAL")
        self.assertEqual(request.safe_context.inning_phase, "경기 종료")
        self.assertEqual(request.safe_context.tension_level, "HIGH")
        self.assertEqual(request.safe_context.score_band, "RECOMMEND")
        self.assertEqual(request.safe_context.safe_tags, ["후반 긴장 구간"])
        self.assertEqual(request.safe_context.reason_codes, ["late_or_extra"])
        self.assertEqual(len(request.safe_context.key_moments), 1)
        self.assertEqual(request.safe_context.key_moments[0].inning, 7)
        self.assertEqual(request.safe_context.key_moments[0].label, "만루 승부")

    def test_revealed_final_headline_request_parses_final_score(self):
        payload = {
            "gameId": 5059082,
            "mode": "REVEALED",
            "contextHash": "game-5059082-final-revealed-v3",
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

    def test_event_copy_request_parses_protected_payload(self):
        payload = {
            "gameId": 5059041,
            "eventId": 91,
            "mode": "PROTECTED",
            "contextHash": "event-91-protected-v1",
            "safeContext": {
                "eventType": "pressure_bases_loaded",
                "label": "만루 승부",
                "inning": 7,
            },
        }

        request = EventCopyRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059041)
        self.assertEqual(request.event_id, 91)
        self.assertEqual(request.mode, AiCopyMode.PROTECTED)
        self.assertEqual(request.context_hash, "event-91-protected-v1")
        self.assertEqual(request.safe_context.event_type, "pressure_bases_loaded")
        self.assertEqual(request.safe_context.label, "만루 승부")
        self.assertEqual(request.safe_context.inning, 7)
        self.assertIsNone(request.safe_context.inning_type)
        self.assertIsNone(request.safe_context.batter)
        self.assertIsNone(request.safe_context.pitcher)
        self.assertIsNone(request.safe_context.evidence)

    def test_event_copy_request_parses_revealed_payload(self):
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

        self.assertEqual(request.mode, AiCopyMode.REVEALED)
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
            "contextHash": "game-5059082-final-v3",
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

    def test_final_headline_response_serializes_to_camel_case(self):
        response = FinalHeadlineResponse(
            spoiler_safe=True,
            context_hash="game-5059082-final-v3",
            safe_title="후반 긴장감이 크게 올라간 경기였습니다.",
            violations=[],
            fallback_used=False,
        )

        data = response.model_dump(
            by_alias=True,
            exclude_none=True,
        )

        self.assertEqual(data["spoilerSafe"], True)
        self.assertEqual(data["contextHash"], "game-5059082-final-v3")
        self.assertEqual(data["safeTitle"], "후반 긴장감이 크게 올라간 경기였습니다.")
        self.assertEqual(data["violations"], [])
        self.assertEqual(data["fallbackUsed"], False)

    def test_event_copy_failure_response_omits_safe_title_when_none(self):
        response = EventCopyResponse(
            spoiler_safe=False,
            context_hash="event-91-protected-v1",
            safe_title=None,
            violations=["OPENAI_API_KEY_MISSING"],
            fallback_used=False,
        )

        data = response.model_dump(
            by_alias=True,
            exclude_none=True,
        )

        self.assertEqual(data["spoilerSafe"], False)
        self.assertEqual(data["contextHash"], "event-91-protected-v1")
        self.assertEqual(data["violations"], ["OPENAI_API_KEY_MISSING"])
        self.assertEqual(data["fallbackUsed"], False)
        self.assertNotIn("safeTitle", data)


if __name__ == "__main__":
    unittest.main()