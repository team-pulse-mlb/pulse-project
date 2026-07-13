import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.services.openai_service import SpoilerFreeSummaryGenerationError


class AiRouterTestCase(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.context_hash = "game-5059082-status-final-v3"
        self.safe_context = {
            "gameStatus": "STATUS_FINAL",
            "inningPhase": "경기 종료",
            "safeTags": ["후반 긴장 구간"],
            "reasonCodes": ["late_or_extra"],
        }

    def test_spoiler_free_summary_returns_generated_text_with_context_hash(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "HOME_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "safeContext": self.safe_context,
        }

        generated_summary = {
            "safe_title": "후반 긴장 구간",
            "safe_reason": "경기 종료 후에도 여운이 남는 순간들을 경험해보세요.",
            "notification_text": "후반 긴장 구간을 놓치지 마세요!",
            "tags": ["후반 긴장 구간", "경기 종료"],
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/spoiler-free-summary", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["safeTitle"], generated_summary["safe_title"])
        self.assertEqual(data["safeReason"], generated_summary["safe_reason"])
        self.assertEqual(data["notificationText"], generated_summary["notification_text"])
        self.assertEqual(data["tags"], generated_summary["tags"])

        # Spring Boot가 저장 조건을 단순하게 판단할 수 있도록
        # 성공 응답에서도 공통 필드는 항상 내려준다.
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_notification_text_returns_generated_text_with_context_hash(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "HOME_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "channel": "WEB",
            "safeContext": self.safe_context,
        }

        generated_summary = {
            "safe_title": "후반 긴장 구간",
            "safe_reason": "경기 흐름을 지켜볼 만한 구간입니다.",
            "notification_text": "후반 긴장 구간을 놓치지 마세요!",
            "tags": ["후반 긴장 구간"],
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/notification-text", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["notificationText"], generated_summary["notification_text"])
        self.assertEqual(data["tags"], generated_summary["tags"])

        # 알림 응답도 Spring Boot 저장 판단에 필요한 공통 필드를 항상 포함한다.
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_replay_summary_returns_generated_text_with_context_hash(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "REPLAY_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "replaySegmentId": "segment-5059082-001",
            "segmentLabel": "스포일러 없이 다시 보기 좋은 구간",
            "segmentReasonTags": ["후반 긴장 구간"],
            "safeContext": self.safe_context,
        }

        generated_summary = {
            "safe_title": "후반 긴장 구간",
            "safe_reason": "경기 종료 후에도 다시 볼 만한 흐름이 이어진 구간입니다.",
            "notification_text": "다시 보기 좋은 구간입니다.",
            "tags": ["후반 긴장 구간"],
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            return_value=generated_summary,
        ):
            response = self.client.post("/ai/replay-summary", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertTrue(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["replayTitle"], payload["segmentLabel"])
        self.assertEqual(data["replaySummary"], generated_summary["safe_reason"])
        self.assertEqual(data["tags"], payload["segmentReasonTags"])

        # 다시보기 응답도 공통 필드를 항상 내려 Spring Boot 파싱을 단순화한다.
        self.assertEqual(data["violations"], [])
        self.assertFalse(data["fallbackUsed"])

    def test_spoiler_free_summary_returns_failure_state_when_generated_text_is_unsafe(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "HOME_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "safeContext": self.safe_context,
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

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertIn("FORBIDDEN_WORD:홈런", data["violations"])
        self.assertFalse(data["fallbackUsed"])

        # 검수 실패 시 생성 문구와 태그는 내려주지 않는다.
        self.assertNotIn("safeTitle", data)
        self.assertNotIn("safeReason", data)
        self.assertNotIn("notificationText", data)
        self.assertNotIn("tags", data)

    def test_notification_text_returns_failure_state_when_generated_text_is_unsafe(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "HOME_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "channel": "WEB",
            "safeContext": self.safe_context,
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

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertIn("FORBIDDEN_WORD:홈런", data["violations"])
        self.assertFalse(data["fallbackUsed"])

        # 알림 검수 실패 시 알림 문구와 태그는 내려주지 않는다.
        self.assertNotIn("notificationText", data)
        self.assertNotIn("tags", data)

    def test_replay_summary_returns_failure_state_when_generated_text_is_unsafe(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "REPLAY_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "replaySegmentId": "segment-5059082-001",
            "segmentLabel": "스포일러 없이 다시 보기 좋은 구간",
            "segmentReasonTags": ["후반 긴장 구간"],
            "safeContext": self.safe_context,
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

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertIn("FORBIDDEN_WORD:끝내기", data["violations"])
        self.assertFalse(data["fallbackUsed"])

        # 다시보기 검수 실패 시 다시보기 제목/요약/태그는 내려주지 않는다.
        self.assertNotIn("replayTitle", data)
        self.assertNotIn("replaySummary", data)
        self.assertNotIn("tags", data)

    def test_spoiler_free_summary_returns_failure_state_when_generation_fails(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "HOME_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "safeContext": self.safe_context,
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            side_effect=SpoilerFreeSummaryGenerationError("OPENAI_API_KEY_MISSING"),
        ):
            response = self.client.post("/ai/spoiler-free-summary", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["violations"], ["OPENAI_API_KEY_MISSING"])
        self.assertFalse(data["fallbackUsed"])

        # OpenAI 생성 실패 시에도 fallback 기본 문구는 ai-service가 만들지 않는다.
        self.assertNotIn("safeTitle", data)
        self.assertNotIn("safeReason", data)
        self.assertNotIn("notificationText", data)
        self.assertNotIn("tags", data)

    def test_notification_text_returns_failure_state_when_generation_fails(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "HOME_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "channel": "WEB",
            "safeContext": self.safe_context,
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            side_effect=SpoilerFreeSummaryGenerationError("OPENAI_GENERATION_FAILED"),
        ):
            response = self.client.post("/ai/notification-text", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["violations"], ["OPENAI_GENERATION_FAILED"])
        self.assertFalse(data["fallbackUsed"])

        # OpenAI 생성 실패 시 알림 문구는 비워둔다.
        self.assertNotIn("notificationText", data)
        self.assertNotIn("tags", data)

    def test_replay_summary_returns_failure_state_when_generation_fails(self):
        payload = {
            "gameId": 5059082,
            "mode": "PROTECTED",
            "surface": "REPLAY_CARD",
            "language": "ko",
            "maxLength": 80,
            "contextHash": self.context_hash,
            "replaySegmentId": "segment-5059082-001",
            "segmentLabel": "스포일러 없이 다시 보기 좋은 구간",
            "segmentReasonTags": ["후반 긴장 구간"],
            "safeContext": self.safe_context,
        }

        with patch(
            "app.routers.ai_router.generate_spoiler_free_summary",
            side_effect=SpoilerFreeSummaryGenerationError("OPENAI_TIMEOUT"),
        ):
            response = self.client.post("/ai/replay-summary", json=payload)

        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertFalse(data["spoilerSafe"])
        self.assertEqual(data["contextHash"], self.context_hash)
        self.assertEqual(data["violations"], ["OPENAI_TIMEOUT"])
        self.assertFalse(data["fallbackUsed"])

        # OpenAI 생성 실패 시 다시보기 제목/요약/태그는 내려주지 않는다.
        self.assertNotIn("replayTitle", data)
        self.assertNotIn("replaySummary", data)
        self.assertNotIn("tags", data)


if __name__ == "__main__":
    unittest.main()
