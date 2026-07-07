import unittest

from fastapi.testclient import TestClient

from app.main import app
from app.services import openai_service


class AiRouterTestCase(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        openai_service.settings.openai_api_key = None

    def test_spoiler_free_summary_returns_safe_response(self):
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

        response = self.client.post("/ai/spoiler-free-summary", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["spoilerSafe"])
        self.assertFalse(data["fallbackUsed"])
        self.assertEqual(data["violations"], [])
        self.assertTrue(data["safeTitle"])
        self.assertTrue(data["safeReason"])

    def test_notification_text_returns_safe_response(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "HOME_CARD",
            "language": "ko",
            "maxLength": 80,
            "channel": "WEB",
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "safeTags": ["후반 긴장 구간"],
                "reasonCodes": ["late_or_extra"],
            },
        }

        response = self.client.post("/ai/notification-text", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["spoilerSafe"])
        self.assertFalse(data["fallbackUsed"])
        self.assertEqual(data["violations"], [])
        self.assertTrue(data["notificationText"])

    def test_replay_summary_returns_safe_response(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "REPLAY_CARD",
            "language": "ko",
            "maxLength": 80,
            "replaySegmentId": "segment-5059082-001",
            "segmentLabel": "스포일러 없이 다시 보기 좋은 구간",
            "segmentReasonTags": ["후반 긴장 구간"],
            "safeContext": {
                "gameStatus": "STATUS_FINAL",
                "inningPhase": "경기 종료",
                "safeTags": ["후반 긴장 구간"],
                "reasonCodes": ["late_or_extra"],
            },
        }

        response = self.client.post("/ai/replay-summary", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["spoilerSafe"])
        self.assertFalse(data["fallbackUsed"])
        self.assertEqual(data["violations"], [])
        self.assertTrue(data["replayTitle"])
        self.assertTrue(data["replaySummary"])


if __name__ == "__main__":
    unittest.main()