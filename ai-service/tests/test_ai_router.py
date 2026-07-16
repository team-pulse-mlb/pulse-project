import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.services.openai_service import SpoilerFreeSummaryGenerationError


class AiRouterTestCase(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.context_hash = "game-5059082-final-headline-v1"
        self.protected_safe_context = {
            "gameStatus": "STATUS_FINAL",
            "inningPhase": "경기 종료",
            "safeTags": ["후반 긴장 구간"],
            "reasonCodes": ["late_or_extra"],
            "keyMoments": [],
        }
        self.revealed_safe_context = {
            "gameStatus": "STATUS_FINAL",
            "inningPhase": "경기 종료",
            "safeTags": ["접전 흐름"],
            "reasonCodes": ["late_or_extra"],
            "keyMoments": [],
            "finalScore": {
                "home": 5,
                "away": 3,
            },
            "winner": "home",
        }
        self.event_safe_context = {
            "eventType": "pressure_scoring_position",
            "label": "득점권 압박",
            "inning": 7,
        }

    def test_final_headline_returns_generated_text_with_context_hash(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.context_hash,
            "safeContext": self.protected_safe_context,
        }

        generated_summary = {
            "safe_title": "후반 긴장감이 이어진 경기",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["safeTitle"], generated_summary["safe_title"])
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_revealed_final_headline_valid_score_and_winner_passes(self):
        payload = {
            "gameId": 5059082,
            "mode": "REVEALED",
            "contextHash": self.context_hash,
            "safeContext": self.revealed_safe_context,
        }

        generated_summary = {
            "safe_title": "홈팀이 5-3으로 승리",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["safeTitle"], "홈팀이 5-3으로 승리")
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_revealed_final_headline_invalid_score_format_returns_failure_state(self):
        payload = {
            "gameId": 5059082,
            "mode": "REVEALED",
            "contextHash": self.context_hash,
            "safeContext": self.revealed_safe_context,
        }

        generated_summary = {
            "safe_title": "홈팀이 5점을 내며 승리",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertIn("UNSUPPORTED_SCORE_FORMAT", data["violations"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_revealed_final_headline_ambiguous_winner_returns_failure_state(self):
        payload = {
            "gameId": 5059082,
            "mode": "REVEALED",
            "contextHash": self.context_hash,
            "safeContext": self.revealed_safe_context,
        }

        generated_summary = {
            "safe_title": "홈팀 승리, 원정팀 승리",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertIn("WINNER_REFERENCE_AMBIGUOUS", data["violations"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_final_headline_returns_failure_state_when_generated_text_is_unsafe(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.context_hash,
            "safeContext": self.protected_safe_context,
        }

        unsafe_summary = {
            "safe_title": "홈런이 나온 경기",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=unsafe_summary,
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertIn("FORBIDDEN_WORD:홈런", data["violations"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_final_headline_returns_failure_state_when_generation_fails(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.context_hash,
            "safeContext": self.protected_safe_context,
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            side_effect=SpoilerFreeSummaryGenerationError("OPENAI_API_KEY_MISSING"),
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["violations"], ["OPENAI_API_KEY_MISSING"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_final_headline_returns_failure_state_when_safe_title_is_missing(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "contextHash": self.context_hash,
            "safeContext": self.protected_safe_context,
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value={},
        ):
            response = self.client.post("/ai/final-headline", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(
            data["violations"],
            ["OPENAI_RESPONSE_MISSING_FIELD:safe_title"],
        )
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)

    def test_event_copy_returns_generated_text_with_context_hash(self):
        payload = {
            "gameId": 5059082,
            "eventId": 10,
            "mode": "PROTECTED",
            "contextHash": "event-copy-v1",
            "safeContext": self.event_safe_context,
        }

        generated_summary = {
            "safe_title": "7회 득점권 승부가 이어졌습니다.",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/event-copy", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], "event-copy-v1")
        self.assertEqual(data["safeTitle"], generated_summary["safe_title"])
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_event_copy_accepts_protected_situation_context(self):
        payload = {
            "gameId": 5059082,
            "eventId": 10,
            "mode": "PROTECTED",
            "contextHash": "event-copy-v2",
            "safeContext": {
                "eventType": "full_count_two_out",
                "label": "승부처 카운트",
                "inning": 7,
                "contributingLabels": [
                    "만루 승부",
                    "승부처 카운트",
                ],
                "situation": {
                    "outs": 2,
                    "balls": 3,
                    "strikes": 2,
                    "runnerOnFirst": True,
                    "runnerOnSecond": True,
                    "runnerOnThird": True,
                },
            },
        }

        generated_summary = {
            "safe_title": "2사 만루에서 풀카운트 승부가 이어졌습니다.",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ) as generate_summary:
            response = self.client.post("/ai/event-copy", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], "event-copy-v2")
        self.assertEqual(data["safeTitle"], generated_summary["safe_title"])
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

        generated_request = generate_summary.call_args.args[0]
        self.assertEqual(generated_request.game_id, 5059082)
        self.assertEqual(generated_request.event_id, 10)
        self.assertEqual(generated_request.mode.value, "PROTECTED")
        self.assertEqual(
            generated_request.safe_context.contributing_labels,
            [
                "만루 승부",
                "승부처 카운트",
            ],
        )
        self.assertEqual(
            generated_request.safe_context.situation,
            {
                "outs": 2,
                "balls": 3,
                "strikes": 2,
                "runnerOnFirst": True,
                "runnerOnSecond": True,
                "runnerOnThird": True,
            },
        )

    def test_event_copy_returns_failure_state_when_generated_text_is_unsafe(self):
        payload = {
            "gameId": 5059082,
            "eventId": 10,
            "mode": "PROTECTED",
            "contextHash": "event-copy-v1",
            "safeContext": self.event_safe_context,
        }

        generated_summary = {
            "safe_title": "홈런으로 분위기가 바뀌었습니다.",
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/event-copy", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], "event-copy-v1")
        self.assertIn("FORBIDDEN_WORD:홈런", data["violations"])
        self.assertFalse(data["fallbackUsed"])
        self.assertNotIn("safeTitle", data)


if __name__ == "__main__":
    unittest.main()