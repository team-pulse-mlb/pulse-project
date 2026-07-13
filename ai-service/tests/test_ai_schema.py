import unittest

from app.schemas.ai_schema import SpoilerFreeSummaryRequest


class AiSchemaTestCase(unittest.TestCase):
    def test_spring_safe_context_uses_default_values(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "HOME_CARD",
            "language": "ko",
            "maxLength": 80,
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "safeTags": ["후반 긴장 구간"],
                "reasonCodes": ["late_or_extra"],
            },
        }

        request = SpoilerFreeSummaryRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059082)
        self.assertEqual(request.safe_context.game_status, "STATUS_FINAL")
        self.assertEqual(request.safe_context.inning_phase, "경기 종료")
        self.assertEqual(request.safe_context.tension_level, "NORMAL")
        self.assertEqual(request.safe_context.score_band, "RECOMMEND")
        self.assertEqual(request.safe_context.safe_tags, ["후반 긴장 구간"])
        self.assertEqual(request.safe_context.reason_codes, ["late_or_extra"])

    def test_extra_spring_fields_are_ignored(self):
        payload = {
            "gameId": 5059082,
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

        request = SpoilerFreeSummaryRequest.model_validate(payload)

        self.assertEqual(request.game_id, 5059082)
        self.assertEqual(request.safe_context.game_status, "STATUS_FINAL")
        self.assertFalse(hasattr(request, "recent_plays"))
        self.assertFalse(hasattr(request.safe_context, "unexpected_field"))


if __name__ == "__main__":
    unittest.main()