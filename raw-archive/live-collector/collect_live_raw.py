"""PULSE 라이브 원본 수집기.

balldontlie MLB API 응답을 수집 시각(`observed_at`)과 함께 S3에 저장한다.
plays/plate_appearances에는 실제 관측 시각이 없으므로, 나중에 시간 감쇠를
재현하려면 수집 시각을 같이 남겨야 한다.

주요 동작:
  - /games: 어제, 오늘, 내일 경기 목록을 확인한다.
  - /lineups, /odds: 경기 전 데이터 변화를 해시로 중복 제거해 저장한다.
  - /plays: 진행 중 경기의 새 play를 커서로 증분 수집한다.
  - /plate_appearances: 커서가 없어 진행 중 경기마다 전체 재조회 후 중복 제거한다.
  - 종료 경기: plays, PA 최종본, stats를 한 번 더 저장한다.
  - 일 배치: UTC 09시 이후 standings, team stats, injuries를 저장한다.

환경변수:
  BDL_API_KEY       balldontlie API 키
  BUCKET            S3 버킷 이름
  POLL_PA           "false"면 plate_appearances 수집 생략
  SUBPOLL_INTERVAL  라이브 서브 폴링 간격(초)
  PA_ROUND_STRIDE   PA 수집 라운드 간격
  LIVE_GAME_WORKERS 경기별 라이브 호출 병렬 워커 수
"""

import concurrent.futures
import gzip
import hashlib
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

BASE_URL = "https://api.balldontlie.io/mlb/v1"
MAX_PAGES = 20          # 커서 페이지 무한 루프 방지
DEFAULT_LIVE_GAME_WORKERS = 8
SUSPEND_AFTER_S = 600   # 10분 동안 새 play가 없으면 중단 상태로 본다
SUSPENDED_POLL_S = 300  # 중단 상태의 plays 재확인 간격
LINEUP_WINDOW_H = 36    # 경기 36시간 전부터 라인업을 확인한다
BATCH_HOUR_UTC = 9

_s3_client = None


def get_s3():
    """S3 클라이언트를 최초 사용 시 생성하고 이후 재사용한다."""
    global _s3_client
    if _s3_client is None:
        import boto3

        _s3_client = boto3.client("s3")
    return _s3_client


def api_get(path, params):
    query = urllib.parse.urlencode(params, doseq=True)
    url = f"{BASE_URL}{path}?{query}"
    req = urllib.request.Request(
        url, headers={"Authorization": f"Bearer {os.environ['BDL_API_KEY']}"}
    )
    for attempt in (1, 2):
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt == 1:
                time.sleep(2)
                continue
            raise


def save_raw(bucket, key, observed_at, endpoint, params, response, backfilled=False):
    doc = {"observed_at": observed_at, "endpoint": endpoint, "params": params, "response": response}
    if backfilled:
        doc["backfilled"] = True  # 시간 감쇠 재현에서 제외한다
    get_s3().put_object(
        Bucket=bucket, Key=key,
        Body=gzip.compress(json.dumps(doc, ensure_ascii=False).encode("utf-8")),
        ContentType="application/json", ContentEncoding="gzip",
    )


def load_state(bucket):
    s3_client = get_s3()
    try:
        obj = s3_client.get_object(Bucket=bucket, Key="state/collector_state.json")
        return json.loads(obj["Body"].read())
    except s3_client.exceptions.NoSuchKey:
        return {}


def save_state(bucket, state):
    get_s3().put_object(
        Bucket=bucket, Key="state/collector_state.json",
        Body=json.dumps(state).encode("utf-8"), ContentType="application/json",
    )


def body_hash(response):
    return hashlib.md5(json.dumps(response, sort_keys=True).encode("utf-8")).hexdigest()


def dedupe_save(bucket, state, saved, hash_key, s3_key, observed_at, endpoint, params, resp):
    h = body_hash(resp)
    if state["hashes"].get(hash_key) != h:
        save_raw(bucket, s3_key, observed_at, endpoint, params, resp)
        state["hashes"][hash_key] = h
        saved.append(hash_key)


def run_game_tasks(items, max_workers, task):
    if not items:
        return []
    workers = max(1, min(max_workers, len(items)))
    if workers == 1:
        return [task(item) for item in items]
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        return list(executor.map(task, items))


def classify_status(status):
    """알 수 없는 상태는 진행 중으로 보고 원본 수집을 놓치지 않는다."""
    s = (status or "").strip().upper()
    if s in ("STATUS_FINAL", "STATUS_POSTPONED", "STATUS_CANCELED"):
        return "done"
    if s == "STATUS_SCHEDULED":
        return "scheduled"
    if s == "STATUS_IN_PROGRESS":
        return "live"
    return "unknown"


def parse_game_date(g):
    try:
        return datetime.fromisoformat(g["date"].replace("Z", "+00:00"))
    except (KeyError, ValueError, AttributeError):
        return None


def now_parts():
    now = datetime.now(timezone.utc)
    return now, now.isoformat(), now.strftime("%Y-%m-%d"), now.strftime("%H%M%S")


def poll_games(bucket, state, saved):
    """UTC 기준 어제/오늘/내일 경기 목록을 조회한다."""
    now, observed_at, dt, ts = now_parts()
    dates = [(now + timedelta(days=d)).strftime("%Y-%m-%d") for d in (-1, 0, 1)]
    params = {"dates[]": dates, "per_page": 100}
    resp = api_get("/games", params)
    dedupe_save(bucket, state, saved, "games",
                f"raw/games/dt={dt}/games_{ts}Z.json.gz", observed_at, "/games", params, resp)
    games = resp.get("data", [])
    live = [g for g in games if classify_status(g.get("status")) in ("live", "unknown")]
    return games, live


def build_play_poll_request(g, state, now, observed_at, dt, ts):
    gid = str(g["id"])
    suspended_poll_at = None
    last_new = state["last_play_at"].get(gid)
    if last_new and (now - datetime.fromisoformat(last_new)).total_seconds() > SUSPEND_AFTER_S:
        last_poll = state["suspended_poll_at"].get(gid)
        if last_poll and (now - datetime.fromisoformat(last_poll)).total_seconds() < SUSPENDED_POLL_S:
            return {"gid": gid, "skip": True, "observed_at": observed_at}
        suspended_poll_at = observed_at

    return {
        "gid": gid,
        "skip": False,
        "cursor": state["plays_cursor"].get(gid),
        "observed_at": observed_at,
        "dt": dt,
        "ts": ts,
        "suspended_poll_at": suspended_poll_at,
    }


def collect_plays_for_game(bucket, request):
    gid = request["gid"]
    result = {
        "gid": gid,
        "saved": [],
        "got_new": False,
        "cursor": None,
        "observed_at": request["observed_at"],
        "suspended_poll_at": request.get("suspended_poll_at"),
    }
    if request["skip"]:
        return result

    cursor = request["cursor"]
    for _ in range(MAX_PAGES):
        params = {"game_id": gid, "per_page": 100}
        if cursor is not None:
            params["cursor"] = cursor
        try:
            resp = api_get("/plays", params)
        except (urllib.error.URLError, urllib.error.HTTPError) as e:
            print(f"plays 조회 실패(game_id={gid}): {e}")
            break
        if resp.get("data"):
            key = f"raw/plays/game_id={gid}/plays_{request['dt']}_{request['ts']}Z_c{cursor or 0}.json.gz"
            save_raw(bucket, key, request["observed_at"], "/plays", params, resp)
            result["saved"].append(f"plays:{gid}")
            result["got_new"] = True
        next_cursor = resp.get("meta", {}).get("next_cursor")
        if next_cursor is None or next_cursor == cursor:
            break
        cursor = next_cursor
    result["cursor"] = cursor
    return result


def apply_play_poll_result(state, saved, result):
    gid = result["gid"]
    if result["suspended_poll_at"]:
        state["suspended_poll_at"][gid] = result["suspended_poll_at"]
    if result["cursor"] is not None:
        state["plays_cursor"][gid] = result["cursor"]
    if result["got_new"] or gid not in state["last_play_at"]:
        state["last_play_at"][gid] = result["observed_at"]
        state["suspended_poll_at"].pop(gid, None)
    saved.extend(result["saved"])


def poll_plays(bucket, state, saved, live_games, max_workers=DEFAULT_LIVE_GAME_WORKERS):
    """진행 중 경기의 새 play만 커서로 증분 수집한다."""
    now, observed_at, dt, ts = now_parts()
    requests = [build_play_poll_request(g, state, now, observed_at, dt, ts) for g in live_games]
    results = run_game_tasks(requests, max_workers, lambda request: collect_plays_for_game(bucket, request))
    for result in results:
        apply_play_poll_result(state, saved, result)


def poll_odds(bucket, state, saved):
    """오늘 경기 배당 변화를 해시로 중복 제거해 저장한다."""
    now, observed_at, dt, ts = now_parts()
    try:
        params = {"dates[]": [now.strftime("%Y-%m-%d")]}
        resp = api_get("/odds", params)
        dedupe_save(bucket, state, saved, "odds",
                    f"raw/odds/dt={dt}/odds_{ts}Z.json.gz", observed_at, "/odds", params, resp)
    except (urllib.error.URLError, urllib.error.HTTPError) as e:
        print(f"odds 조회 실패: {e}")


def poll_lineups(bucket, state, saved, games):
    """36시간 안에 시작하는 경기의 라인업 변화를 저장한다."""
    now, observed_at, dt, ts = now_parts()
    ids = []
    for g in games:
        if classify_status(g.get("status")) != "scheduled":
            continue
        start = parse_game_date(g)
        if start and timedelta(0) <= start - now <= timedelta(hours=LINEUP_WINDOW_H):
            ids.append(str(g["id"]))
    if not ids:
        return
    try:
        params = {"game_ids[]": sorted(ids), "per_page": 100}
        resp = api_get("/lineups", params)
        dedupe_save(bucket, state, saved, "lineups",
                    f"raw/lineups/dt={dt}/lineups_{ts}Z.json.gz", observed_at, "/lineups", params, resp)
    except (urllib.error.URLError, urllib.error.HTTPError) as e:
        print(f"lineups 조회 실패: {e}")


def build_plate_appearance_request(g, state, observed_at, dt, ts):
    gid = str(g["id"])
    hash_key = f"pa:{gid}"
    return {
        "gid": gid,
        "hash_key": hash_key,
        "previous_hash": state["hashes"].get(hash_key),
        "observed_at": observed_at,
        "dt": dt,
        "ts": ts,
    }


def collect_plate_appearance_for_game(bucket, request):
    gid = request["gid"]
    try:
        params = {"game_id": gid, "per_page": 100}
        resp = api_get("/plate_appearances", params)
        h = body_hash(resp)
        if request["previous_hash"] != h:
            save_raw(bucket,
                     f"raw/plate_appearances/game_id={gid}/pa_{request['dt']}_{request['ts']}Z.json.gz",
                     request["observed_at"], "/plate_appearances", params, resp)
            return {"hash_key": request["hash_key"], "hash": h, "saved": True}
        return {"hash_key": request["hash_key"], "hash": h, "saved": False}
    except (urllib.error.URLError, urllib.error.HTTPError) as e:
        print(f"plate_appearances 조회 실패(game_id={gid}): {e}")
        return {"hash_key": request["hash_key"], "hash": None, "saved": False}


def poll_plate_appearances(bucket, state, saved, live_games, max_workers=DEFAULT_LIVE_GAME_WORKERS):
    """진행 중 경기의 plate_appearances를 전체 재조회 후 중복 제거한다."""
    _, observed_at, dt, ts = now_parts()
    requests = [build_plate_appearance_request(g, state, observed_at, dt, ts) for g in live_games]
    results = run_game_tasks(requests, max_workers,
                             lambda request: collect_plate_appearance_for_game(bucket, request))
    for result in results:
        if result["hash"] is not None:
            state["hashes"][result["hash_key"]] = result["hash"]
        if result["saved"]:
            saved.append(result["hash_key"])


def backfill_finished(bucket, state, saved, games):
    """종료된 경기는 최종 원본을 한 번 더 저장한다."""
    _, observed_at, dt, ts = now_parts()
    for g in games:
        gid = str(g["id"])
        if classify_status(g.get("status")) != "done" or gid in state["backfilled"]:
            continue
        try:
            cursor = None
            for page in range(MAX_PAGES):
                params = {"game_id": gid, "per_page": 100}
                if cursor is not None:
                    params["cursor"] = cursor
                resp = api_get("/plays", params)
                if resp.get("data"):
                    key = f"raw/backfill/plays/game_id={gid}/plays_p{page}.json.gz"
                    save_raw(bucket, key, observed_at, "/plays", params, resp, backfilled=True)
                next_cursor = resp.get("meta", {}).get("next_cursor")
                if next_cursor is None or next_cursor == cursor:
                    break
                cursor = next_cursor

            pa_params = {"game_id": gid, "per_page": 100}
            pa = api_get("/plate_appearances", pa_params)
            save_raw(bucket, f"raw/backfill/plate_appearances/game_id={gid}/pa_final.json.gz",
                     observed_at, "/plate_appearances", pa_params, pa, backfilled=True)

            st_params = {"game_ids[]": [gid], "per_page": 100}
            st = api_get("/stats", st_params)
            save_raw(bucket, f"raw/stats/game_id={gid}/stats_{dt}_{ts}Z.json.gz",
                     observed_at, "/stats", st_params, st)

            state["backfilled"][gid] = observed_at
            saved.append(f"backfill:{gid}")
        except (urllib.error.URLError, urllib.error.HTTPError) as e:
            print(f"종료 경기 최종 저장 실패(game_id={gid}). 다음 실행 때 재시도합니다: {e}")


def daily_batch(bucket, state, saved):
    """UTC 09시 이후 하루 한 번 시즌/부상 데이터를 저장한다."""
    now, observed_at, dt, ts = now_parts()
    if now.hour < BATCH_HOUR_UTC:
        return
    batch_names = ("standings", "teams_season_stats", "player_injuries")
    daily_batch_done = state["daily_batch_done"]
    if state.pop("last_batch_date", None) == dt:
        daily_batch_done.update({name: dt for name in batch_names})
    state["daily_batch_done"] = {
        name: completed_date
        for name, completed_date in daily_batch_done.items()
        if completed_date == dt
    }
    daily_batch_done = state["daily_batch_done"]
    season = now.year
    for endpoint, params in (
        ("/standings", {"season": season}),
        ("/teams/season_stats", {"season": season}),
    ):
        name = endpoint.strip("/").replace("/", "_")
        if daily_batch_done.get(name) == dt:
            continue
        try:
            resp = api_get(endpoint, params)
            save_raw(bucket, f"raw/{name}/dt={dt}/{name}_{ts}Z.json.gz",
                     observed_at, endpoint, params, resp)
            saved.append(name)
            daily_batch_done[name] = dt
        except (urllib.error.URLError, urllib.error.HTTPError) as e:
            print(f"{endpoint} 조회 실패: {e}")
    if daily_batch_done.get("player_injuries") == dt:
        return
    try:
        cursor, pages = None, []
        for _ in range(MAX_PAGES):
            params = {"per_page": 100}
            if cursor is not None:
                params["cursor"] = cursor
            resp = api_get("/player_injuries", params)
            pages.append(resp)
            cursor = resp.get("meta", {}).get("next_cursor")
            if cursor is None:
                break
        save_raw(bucket, f"raw/player_injuries/dt={dt}/injuries_{ts}Z.json.gz",
                 observed_at, "/player_injuries", {"per_page": 100}, {"pages": pages})
        saved.append("player_injuries")
        daily_batch_done["player_injuries"] = dt
    except (urllib.error.URLError, urllib.error.HTTPError) as e:
        print(f"player_injuries 조회 실패: {e}")


def prune_state(state, games):
    """3일 조회 범위에서 벗어난 경기 상태를 정리한다."""
    current = {str(g["id"]) for g in games}
    for key in ("plays_cursor", "last_play_at", "suspended_poll_at", "backfilled"):
        state[key] = {k: v for k, v in state[key].items() if k in current}
    state["hashes"] = {
        k: v for k, v in state["hashes"].items()
        if ":" not in k or k.split(":", 1)[1] in current
    }


def handler(event, context):
    bucket = os.environ["BUCKET"]
    poll_pa = os.environ.get("POLL_PA", "true").lower() != "false"
    subpoll_interval = int(os.environ.get("SUBPOLL_INTERVAL", "10"))
    pa_round_stride = max(1, int(os.environ.get("PA_ROUND_STRIDE", "1")))
    live_game_workers = max(1, int(os.environ.get("LIVE_GAME_WORKERS", str(DEFAULT_LIVE_GAME_WORKERS))))

    state = load_state(bucket)
    for key in ("hashes", "plays_cursor", "last_play_at", "suspended_poll_at", "backfilled",
                "daily_batch_done"):
        state.setdefault(key, {})
    saved = []

    # 1분마다 기본 수집을 한 번 실행한다.
    games, live_games = poll_games(bucket, state, saved)
    poll_odds(bucket, state, saved)
    poll_lineups(bucket, state, saved, games)
    poll_plays(bucket, state, saved, live_games, live_game_workers)
    if poll_pa:
        poll_plate_appearances(bucket, state, saved, live_games, live_game_workers)
    backfill_finished(bucket, state, saved, games)
    daily_batch(bucket, state, saved)

    # 라이브 중에는 Lambda 한 번 안에서 더 촘촘하게 재조회한다.
    # EventBridge 최소 주기가 1분이라 내부 sleep으로 관측 해상도를 높인다.
    rounds = 0
    if subpoll_interval > 0:
        while live_games and context.get_remaining_time_in_millis() > (subpoll_interval + 15) * 1000:
            time.sleep(subpoll_interval)
            games, live_games = poll_games(bucket, state, saved)
            poll_plays(bucket, state, saved, live_games, live_game_workers)
            rounds += 1
            if poll_pa and rounds % pa_round_stride == 0:
                poll_plate_appearances(bucket, state, saved, live_games, live_game_workers)

    prune_state(state, games)
    save_state(bucket, state)
    result = {"live_games": len(live_games), "subpoll_rounds": rounds, "saved": saved}
    print(json.dumps(result))
    return result
