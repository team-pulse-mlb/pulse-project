"""실행: python -m unittest discover -s raw-archive/tests"""

import builtins
import importlib.util
import sys
import urllib.error
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).parents[1] / "live-collector" / "collect_live_raw.py"
sys.path.insert(0, str(MODULE_PATH.parent))
import collect_live_raw as collector


def make_http_error():
    return urllib.error.HTTPError(
        url="https://example.test", code=500, msg="테스트 오류", hdrs=None, fp=None
    )


def make_state():
    return {
        "hashes": {},
        "plays_cursor": {},
        "last_play_at": {},
        "suspended_poll_at": {},
        "backfilled": {},
        "daily_batch_done": {},
    }


def fixed_now_parts(hour=10):
    now = datetime(2026, 7, 20, hour, 0, tzinfo=timezone.utc)
    return now, now.isoformat(), "2026-07-20", f"{hour:02d}0000"


class S3BoundaryTest(unittest.TestCase):
    def test_module_import_does_not_require_boto3(self):
        original_import = builtins.__import__

        def import_without_boto3(name, *args, **kwargs):
            if name == "boto3":
                raise ModuleNotFoundError("boto3를 사용할 수 없음")
            return original_import(name, *args, **kwargs)

        spec = importlib.util.spec_from_file_location("collector_without_boto3", MODULE_PATH)
        module = importlib.util.module_from_spec(spec)
        with mock.patch("builtins.__import__", side_effect=import_without_boto3):
            spec.loader.exec_module(module)

        self.assertIsNone(module._s3_client)

    def test_save_raw_uses_injected_s3_access_point(self):
        fake_s3 = mock.Mock()

        with mock.patch.object(collector, "get_s3", return_value=fake_s3):
            collector.save_raw(
                "bucket", "raw/test.json.gz", "2026-07-20T10:00:00+00:00",
                "/test", {"id": 1}, {"data": []}
            )

        fake_s3.put_object.assert_called_once()


class PlayCursorTest(unittest.TestCase):
    def make_request(self, cursor="start"):
        return {
            "gid": "10",
            "skip": False,
            "cursor": cursor,
            "observed_at": "2026-07-20T10:00:00+00:00",
            "dt": "2026-07-20",
            "ts": "100000",
            "suspended_poll_at": None,
        }

    def test_collect_plays_advances_cursor_until_next_cursor_is_none(self):
        pages = [
            {"data": [{"id": 1}], "meta": {"next_cursor": "next"}},
            {"data": [{"id": 2}], "meta": {"next_cursor": None}},
        ]
        with mock.patch.object(collector, "api_get", side_effect=pages) as api_get, \
                mock.patch.object(collector, "save_raw") as save_raw:
            result = collector.collect_plays_for_game("bucket", self.make_request())

        self.assertEqual("next", result["cursor"])
        self.assertEqual(["plays:10", "plays:10"], result["saved"])
        self.assertEqual("next", api_get.call_args_list[1].args[1]["cursor"])
        self.assertEqual(2, save_raw.call_count)

    def test_collect_plays_stops_when_cursor_does_not_change(self):
        response = {"data": [], "meta": {"next_cursor": "same"}}
        with mock.patch.object(collector, "api_get", return_value=response) as api_get:
            result = collector.collect_plays_for_game("bucket", self.make_request("same"))

        self.assertEqual("same", result["cursor"])
        api_get.assert_called_once()

    def test_http_error_preserves_saved_pages_and_advanced_cursor(self):
        first_page = {"data": [{"id": 1}], "meta": {"next_cursor": "next"}}
        with mock.patch.object(
            collector, "api_get", side_effect=[first_page, make_http_error()]
        ), mock.patch.object(collector, "save_raw"):
            result = collector.collect_plays_for_game("bucket", self.make_request())

        self.assertEqual("next", result["cursor"])
        self.assertEqual(["plays:10"], result["saved"])
        self.assertTrue(result["got_new"])

    def test_apply_result_updates_last_play_and_keeps_existing_cursor_for_none(self):
        state = make_state()
        state["plays_cursor"]["10"] = "existing"
        state["last_play_at"]["10"] = "2026-07-20T09:00:00+00:00"
        state["suspended_poll_at"]["10"] = "2026-07-20T09:55:00+00:00"
        saved = []
        result = {
            "gid": "10",
            "saved": ["plays:10"],
            "got_new": True,
            "cursor": None,
            "observed_at": "2026-07-20T10:00:00+00:00",
            "suspended_poll_at": "2026-07-20T10:00:00+00:00",
        }

        collector.apply_play_poll_result(state, saved, result)

        self.assertEqual("existing", state["plays_cursor"]["10"])
        self.assertEqual(result["observed_at"], state["last_play_at"]["10"])
        self.assertNotIn("10", state["suspended_poll_at"])
        self.assertEqual(["plays:10"], saved)


class PartialFailureRetryTest(unittest.TestCase):
    def test_backfill_retries_failed_game_and_continues_other_games(self):
        state = make_state()
        saved = []
        games = [
            {"id": 1, "status": "STATUS_FINAL"},
            {"id": 2, "status": "STATUS_FINAL"},
        ]
        failed_once = {"value": False}

        def fake_api_get(endpoint, params):
            if endpoint == "/plays":
                return {"data": [], "meta": {"next_cursor": None}}
            return {"data": [{"id": params.get("game_id", params.get("game_ids[]"))}]}

        def fake_save_raw(bucket, key, *args, **kwargs):
            if "game_id=1/pa_final" in key and not failed_once["value"]:
                failed_once["value"] = True
                raise make_http_error()

        with mock.patch.object(collector, "now_parts", return_value=fixed_now_parts()), \
                mock.patch.object(collector, "api_get", side_effect=fake_api_get), \
                mock.patch.object(collector, "save_raw", side_effect=fake_save_raw):
            collector.backfill_finished("bucket", state, saved, games)
            self.assertNotIn("1", state["backfilled"])
            self.assertIn("2", state["backfilled"])

            collector.backfill_finished("bucket", state, saved, games)

        self.assertIn("1", state["backfilled"])
        self.assertEqual(["backfill:2", "backfill:1"], saved)

    def test_plate_appearance_save_failure_leaves_hash_none_for_retry(self):
        request = {
            "gid": "10",
            "hash_key": "pa:10",
            "previous_hash": None,
            "observed_at": "2026-07-20T10:00:00+00:00",
            "dt": "2026-07-20",
            "ts": "100000",
        }
        response = {"data": [{"id": 1}]}
        with mock.patch.object(collector, "api_get", return_value=response), \
                mock.patch.object(
                    collector, "save_raw", side_effect=[make_http_error(), None]
                ) as save_raw:
            first = collector.collect_plate_appearance_for_game("bucket", request)
            second = collector.collect_plate_appearance_for_game("bucket", request)

        self.assertIsNone(first["hash"])
        self.assertFalse(first["saved"])
        self.assertEqual(collector.body_hash(response), second["hash"])
        self.assertTrue(second["saved"])
        self.assertEqual(2, save_raw.call_count)


class DailyBatchTest(unittest.TestCase):
    def test_before_utc_nine_does_nothing(self):
        state = make_state()
        state["last_batch_date"] = "2026-07-20"
        original = {key: value.copy() if isinstance(value, dict) else value
                    for key, value in state.items()}
        with mock.patch.object(collector, "now_parts", return_value=fixed_now_parts(8)), \
                mock.patch.object(collector, "api_get") as api_get, \
                mock.patch.object(collector, "save_raw") as save_raw:
            collector.daily_batch("bucket", state, [])

        self.assertEqual(original, state)
        api_get.assert_not_called()
        save_raw.assert_not_called()

    def test_only_successful_endpoints_are_completed_and_failure_retries_same_day(self):
        state = make_state()
        saved = []

        def first_api_get(endpoint, params):
            if endpoint == "/teams/season_stats":
                raise make_http_error()
            return {"data": [], "meta": {"next_cursor": None}}

        with mock.patch.object(collector, "now_parts", return_value=fixed_now_parts()), \
                mock.patch.object(collector, "api_get", side_effect=first_api_get), \
                mock.patch.object(collector, "save_raw"):
            collector.daily_batch("bucket", state, saved)

        self.assertEqual("2026-07-20", state["daily_batch_done"]["standings"])
        self.assertEqual("2026-07-20", state["daily_batch_done"]["player_injuries"])
        self.assertNotIn("teams_season_stats", state["daily_batch_done"])

        with mock.patch.object(collector, "now_parts", return_value=fixed_now_parts()), \
                mock.patch.object(
                    collector, "api_get",
                    return_value={"data": [], "meta": {"next_cursor": None}},
                ) as api_get, mock.patch.object(collector, "save_raw"):
            collector.daily_batch("bucket", state, saved)

        api_get.assert_called_once_with("/teams/season_stats", {"season": 2026})
        self.assertEqual("2026-07-20", state["daily_batch_done"]["teams_season_stats"])

    def test_legacy_date_migrates_to_endpoint_completion(self):
        state = make_state()
        state["last_batch_date"] = "2026-07-20"
        with mock.patch.object(collector, "now_parts", return_value=fixed_now_parts()), \
                mock.patch.object(collector, "api_get") as api_get, \
                mock.patch.object(collector, "save_raw"):
            collector.daily_batch("bucket", state, [])

        self.assertNotIn("last_batch_date", state)
        self.assertEqual(
            {
                "standings": "2026-07-20",
                "teams_season_stats": "2026-07-20",
                "player_injuries": "2026-07-20",
            },
            state["daily_batch_done"],
        )
        api_get.assert_not_called()

    def test_stale_completion_records_are_removed(self):
        state = make_state()
        state["daily_batch_done"] = {
            "standings": "2026-07-19",
            "obsolete": "2026-07-19",
        }
        response = {"data": [], "meta": {"next_cursor": None}}
        with mock.patch.object(collector, "now_parts", return_value=fixed_now_parts()), \
                mock.patch.object(collector, "api_get", return_value=response), \
                mock.patch.object(collector, "save_raw"):
            collector.daily_batch("bucket", state, [])

        self.assertNotIn("obsolete", state["daily_batch_done"])
        self.assertTrue(all(
            completed == "2026-07-20"
            for completed in state["daily_batch_done"].values()
        ))


class SuspendedPollingTest(unittest.TestCase):
    def test_stale_game_is_probed_then_skipped_during_suspended_interval(self):
        state = make_state()
        now, observed_at, dt, ts = fixed_now_parts()
        state["last_play_at"]["10"] = (
            now - timedelta(seconds=collector.SUSPEND_AFTER_S + 1)
        ).isoformat()

        probe = collector.build_play_poll_request(
            {"id": 10}, state, now, observed_at, dt, ts
        )
        self.assertFalse(probe["skip"])
        self.assertEqual(observed_at, probe["suspended_poll_at"])

        state["suspended_poll_at"]["10"] = (
            now - timedelta(seconds=collector.SUSPENDED_POLL_S - 1)
        ).isoformat()
        skipped = collector.build_play_poll_request(
            {"id": 10}, state, now, observed_at, dt, ts
        )

        self.assertTrue(skipped["skip"])


if __name__ == "__main__":
    unittest.main()
