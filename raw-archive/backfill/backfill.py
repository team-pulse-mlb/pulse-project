"""PULSE 과거 시즌 원본 백필.

로컬에서 한 번 실행해 과거 balldontlie MLB 시즌 데이터를 S3에 gzip JSON으로 저장한다.
과거 데이터는 실제 관측 시각이 없으므로 모두 `backfilled=true`로 표시한다.
이 데이터는 시간 감쇠 재현이 아니라 결과 기반 백테스트에만 사용한다.

시즌별 저장 위치:
  raw/historical/season=YYYY/games.json.gz
  raw/historical/season=YYYY/standings.json.gz
  raw/historical/season=YYYY/team_season_stats.json.gz
  raw/historical/season=YYYY/games/game_id=<id>.json.gz

사용:
  python raw-archive/backfill/backfill.py --bucket pulse-raw-<account-id>

필요:
  BDL_API_KEY 환경변수, boto3, AWS 자격 증명
"""

import argparse
import gzip
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import deque
from datetime import datetime, timezone

import boto3

BASE_URL = "https://api.balldontlie.io/mlb/v1"
MAX_PAGES = 60  # 페이지 반복 안전장치


class Throttle:
    """API 호출량을 분당 제한한다."""

    def __init__(self, max_per_min):
        self.max = max_per_min
        self.calls = deque()

    def wait(self):
        now = time.monotonic()
        while self.calls and now - self.calls[0] > 60:
            self.calls.popleft()
        if len(self.calls) >= self.max:
            time.sleep(max(0.0, 60 - (now - self.calls[0])) + 0.05)
        self.calls.append(time.monotonic())


def api_get(throttle, path, params):
    throttle.wait()
    query = urllib.parse.urlencode(params, doseq=True)
    req = urllib.request.Request(
        f"{BASE_URL}{path}?{query}",
        headers={"Authorization": f"Bearer {os.environ['BDL_API_KEY']}"},
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < 3:
                time.sleep(10 * (attempt + 1))
                continue
            raise
        except urllib.error.URLError:
            if attempt < 3:
                time.sleep(5)
                continue
            raise


def paged_get(throttle, path, params):
    """커서 페이지를 끝까지 읽어 원본 응답 목록으로 반환한다."""
    pages, cursor = [], None
    for _ in range(MAX_PAGES):
        p = dict(params)
        if cursor is not None:
            p["cursor"] = cursor
        resp = api_get(throttle, path, p)
        pages.append({"endpoint": path, "params": p, "response": resp})
        next_cursor = resp.get("meta", {}).get("next_cursor")
        if next_cursor is None or next_cursor == cursor:
            break
        cursor = next_cursor
    return pages


def put_gz(s3, bucket, key, doc):
    s3.put_object(
        Bucket=bucket, Key=key,
        Body=gzip.compress(json.dumps(doc, ensure_ascii=False).encode("utf-8")),
        ContentType="application/json", ContentEncoding="gzip",
    )


def wrap(documents, season):
    return {
        "observed_at": datetime.now(timezone.utc).isoformat(),
        "backfilled": True,  # 수집 시각일 뿐 시간 감쇠 신호가 아니다
        "season": season,
        "documents": documents,
    }


def existing_game_ids(s3, bucket, season):
    prefix = f"raw/historical/season={season}/games/game_id="
    done = set()
    for page in s3.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=prefix):
        for obj in page.get("Contents", []):
            done.add(obj["Key"][len(prefix):].removesuffix(".json.gz"))
    return done


def fetch_season_games(throttle, season):
    """정규 시즌과 포스트시즌 경기 목록을 합쳐 가져온다."""
    pages = paged_get(throttle, "/games", {"seasons[]": [season], "per_page": 100})
    try:
        pages += paged_get(throttle, "/games",
                           {"seasons[]": [season], "postseason": "true", "per_page": 100})
    except urllib.error.HTTPError as e:
        print(f"  포스트시즌 조회가 지원되지 않습니다({e.code}). 기본 조회 결과만 사용합니다")
    games = {}
    for pg in pages:
        for g in pg["response"].get("data", []):
            games[g["id"]] = g
    return pages, sorted(games.values(), key=lambda g: (g.get("date") or "", g["id"]))


def is_final(g):
    return (g.get("status") or "").strip().upper() == "STATUS_FINAL"


def backfill_game(throttle, s3, bucket, season, gid):
    docs = paged_get(throttle, "/plays", {"game_id": gid, "per_page": 100})
    pa_params = {"game_id": gid, "per_page": 100}  # 단일 페이지, 커서 없음
    docs.append({"endpoint": "/plate_appearances", "params": pa_params,
                 "response": api_get(throttle, "/plate_appearances", pa_params)})
    docs += paged_get(throttle, "/stats", {"game_ids[]": [gid], "per_page": 100})
    put_gz(s3, bucket, f"raw/historical/season={season}/games/game_id={gid}.json.gz",
           wrap(docs, season))


def backfill_season(throttle, s3, bucket, season):
    print(f"{season} 시즌: 경기 목록 조회 중...")
    game_pages, games = fetch_season_games(throttle, season)
    if not games:
        print(f"{season} 시즌: 경기 없음. API 제공 범위 밖으로 보고 건너뜁니다")
        return
    put_gz(s3, bucket, f"raw/historical/season={season}/games.json.gz",
           wrap(game_pages, season))

    for endpoint, key in (("/standings", "standings"),
                          ("/teams/season_stats", "team_season_stats")):
        try:
            resp = api_get(throttle, endpoint, {"season": season})
            put_gz(s3, bucket, f"raw/historical/season={season}/{key}.json.gz",
                   wrap([{"endpoint": endpoint, "params": {"season": season},
                          "response": resp}], season))
        except urllib.error.HTTPError as e:
            print(f"  {endpoint} 조회 실패({season}): {e}")

    finals = [g for g in games if is_final(g)]
    done = existing_game_ids(s3, bucket, season)
    todo = [g for g in finals if str(g["id"]) not in done]
    print(f"{season} 시즌: 전체 {len(games)}경기, 종료 {len(finals)}경기, "
          f"이미 저장 {len(done)}경기, 추가 저장 {len(todo)}경기")

    for i, g in enumerate(todo, 1):
        gid = str(g["id"])
        try:
            backfill_game(throttle, s3, bucket, season, gid)
        except (urllib.error.URLError, urllib.error.HTTPError) as e:
            print(f"  경기 {gid} 저장 실패. 다시 실행하면 재시도됩니다: {e}")
        if i % 50 == 0 or i == len(todo):
            print(f"  {i}/{len(todo)}경기 저장 완료")


def main():
    ap = argparse.ArgumentParser(description="과거 MLB 시즌 원본을 S3에 gzip JSON으로 저장")
    ap.add_argument("--bucket", required=True)
    ap.add_argument("--seasons", type=int, nargs="+", default=[2023, 2024, 2025])
    ap.add_argument("--region", default="ap-northeast-2")
    ap.add_argument("--rpm", type=int, default=300, help="분당 최대 API 요청 수")
    args = ap.parse_args()

    if not os.environ.get("BDL_API_KEY"):
        sys.exit("BDL_API_KEY 환경변수가 필요합니다")

    s3 = boto3.client("s3", region_name=args.region)
    throttle = Throttle(args.rpm)
    for season in sorted(args.seasons):
        backfill_season(throttle, s3, args.bucket, season)
    print("완료")


if __name__ == "__main__":
    main()
