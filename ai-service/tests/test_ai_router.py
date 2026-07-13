import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.services.openai_service import SpoilerFreeSummaryGenerationError


class AiRouterTestCase(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.final_context_hash = "game-5059082-final-protected-v1"
        self.event_context_hash = "event-91-protected-v1"

        self.final_headline_safe_context = {
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
        }

        self.event_copy_safe_context = {
            "eventType": "pressure_bases_loaded",
            "label": "만루 승부",
            "inning": 7,
        }

    def test_ai_test_endpoint_returns_ok(self):
        response = self.client.get("/ai/test")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json(),
            {
                "status": "ok",
                "message": "AI router is working",
            },
        )

    def test_final_headline_returns_generated_safe_title_with_context_hash(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.final_context_hash,
            "safeContext": self.final_headline_safe_context,
        }

        generated_summary = {
            "safe_title": "후반 긴장감이 크게 올라간 경기였습니다.",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.final_context_hash)
        self.assertEqual(data["safeTitle"], generated_summary["safe_title"])
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_event_copy_returns_generated_safe_title_with_context_hash(self):
        payload = {
            "gameId": 5059041,
            "eventId": 91,
            "mode": "PROTECTED",
            "contextHash": self.event_context_hash,
            "safeContext": self.event_copy_safe_context,
        }

        generated_summary = {
            "safe_title": "7회에 집중해서 볼 만한 승부처가 있었습니다.",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/event-copy", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.event_context_hash)
        self.assertEqual(data["safeTitle"], generated_summary["safe_title"])
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_final_headline_returns_failure_when_generated_text_is_unsafe(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.final_context_hash,
            "safeContext": self.final_headline_safe_context,
        }

        unsafe_summary = {
            "safe_title": "홈런으로 흐름이 바뀐 경기였습니다.",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=unsafe_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.final_context_hash)
        self.assertIn("FORBIDDEN_WORD:홈런", data["violations"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_event_copy_returns_failure_when_generated_text_is_unsafe(self):
        payload = {
            "gameId": 5059041,
            "eventId": 91,
            "mode": "PROTECTED",
            "contextHash": self.event_context_hash,
            "safeContext": self.event_copy_safe_context,
        }

        unsafe_summary = {
            "safe_title": "3-2 흐름으로 이어진 장면입니다.",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=unsafe_summary,
        ):
            response = self.client.post("/ai/event-copy", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.event_context_hash)
        self.assertIn("SCORE_PATTERN", data["violations"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_final_headline_returns_failure_when_generation_fails(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.final_context_hash,
            "safeContext": self.final_headline_safe_context,
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            side_effect=SpoilerFreeSummaryGenerationError(
                "OPENAI_API_KEY_MISSING"
            ),
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.final_context_hash)
        self.assertEqual(data["violations"], ["OPENAI_API_KEY_MISSING"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_event_copy_returns_failure_when_generation_fails(self):
        payload = {
            "gameId": 5059041,
            "eventId": 91,
            "mode": "PROTECTED",
            "contextHash": self.event_context_hash,
            "safeContext": self.event_copy_safe_context,
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            side_effect=SpoilerFreeSummaryGenerationError("OPENAI_TIMEOUT"),
        ):
            response = self.client.post("/ai/event-copy", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.event_context_hash)
        self.assertEqual(data["violations"], ["OPENAI_TIMEOUT"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_final_headline_returns_failure_when_safe_title_is_missing(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.final_context_hash,
            "safeContext": self.final_headline_safe_context,
        }

        generated_summary = {}

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.final_context_hash)
        self.assertEqual(
            data["violations"],
            ["OPENAI_RESPONSE_MISSING_FIELD:safe_title"],
        )
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_revealed_final_headline_allows_matching_score_and_winner(self):
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

        generated_summary = {
            "safe_title": "홈팀이 5-3으로 승리했습니다.",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], "game-5059082-final-revealed-v1")
        self.assertEqual(data["safeTitle"], generated_summary["safe_title"])
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_legacy_endpoints_are_not_available(self):
        legacy_payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.final_context_hash,
            "safeContext": self.final_headline_safe_context,
        }

        legacy_paths = [
            "/ai/spoiler-check",
            "/ai/spoiler-free-summary",
            "/ai/notification-text",
            "/ai/replay-summary",
        ]

        for path in legacy_paths:
            with self.subTest(path=path):
                response = self.client.post(path, json=legacy_payload)

                self.assertEqual(response.status_code, 404)


if __name__ == "__main__":
    unittest.main()