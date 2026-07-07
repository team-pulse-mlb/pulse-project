import unittest
from unittest.mock import patch
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

    def test_spoiler_free_summary_uses_fallback_when_generated_text_is_unsafe(self):
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

        unsafe_summary = {
            "safe_title": "홈런이 나온 경기",
            "safe_reason": "역전 흐름이 있었습니다.",
            "notification_text": "3-2 상황입니다.",
            "tags": ["위험 태그"],
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=unsafe_summary,
        ):
            response = self.client.post("/ai/spoiler-free-summary", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["spoilerSafe"])
        self.assertTrue(data["fallbackUsed"])
        self.assertIn("FORBIDDEN_WORD:홈런", data["violations"])

    def test_notification_text_uses_fallback_when_generated_text_is_unsafe(self):
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

        unsafe_summary = {
            "safe_title": "관전 가치가 높아진 경기",
            "safe_reason": "볼 만한 흐름입니다.",
            "notification_text": "홈런 발생으로 흐름이 바뀌었습니다.",
            "tags": ["위험 태그"],
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=unsafe_summary,
        ):
            response = self.client.post("/ai/notification-text", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["spoilerSafe"])
        self.assertTrue(data["fallbackUsed"])
        self.assertIn("FORBIDDEN_WORD:홈런", data["violations"])

    def test_replay_summary_uses_fallback_when_generated_text_is_unsafe(self):
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

        unsafe_summary = {
            "safe_title": "관전 가치가 높아진 경기",
            "safe_reason": "끝내기 장면이 포함된 구간입니다.",
            "notification_text": "볼 만한 흐름입니다.",
            "tags": ["위험 태그"],
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=unsafe_summary,
        ):
            response = self.client.post("/ai/replay-summary", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["spoilerSafe"])
        self.assertTrue(data["fallbackUsed"])
        self.assertIn("FORBIDDEN_WORD:끝내기", data["violations"])


if __name__ == "__main__":
    unittest.main()