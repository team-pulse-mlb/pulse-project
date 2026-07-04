#!/usr/bin/env python3
"""타순(batting_order) 최초 공개 시점을 S3 lineups 아카이브로 집계한다.

EXTERNAL_DATA_API.md 7장 "타순 공개 시점 정밀 실측"의 확인 스크립트.
추가 API 호출 없이 raw/lineups/dt=<date>/ 스냅샷(observed_at 포함)만으로,
경기별로 batting_order가 처음 non-null이 된 시각과 경기 시작 시각의 차(리드타임)를 집계한다.

사용:
  BDL_API_KEY, PULSE_REPLAY_S3_BUCKET, AWS_REGION 환경변수 필요(보통 .env).
  scripts/analyze-lineup-reveal.py 2026-07-02 2026-07-03 2026-07-04

주의: 그날 수집 창이 열리기 전 이미 공개돼 있던 경기는 좌측 절단(리드타임 하한만 관측)이므로
      해석 시 창 시작 시각과 겹치는 값은 하한으로 본다.
"""
import os, sys, json, gzip, subprocess, collections, statistics
from datetime import datetime, timezone

BUCKET = os.environ["PULSE_REPLAY_S3_BUCKET"]
REGION = os.environ.get("AWS_REGION", "ap-northeast-2")
API_KEY = os.environ["BDL_API_KEY"]
DATES = sys.argv[1:]
if not DATES:
    sys.exit("사용법: analyze-lineup-reveal.py <UTC-date> [<UTC-date> ...]")


def s3_ls(prefix):
    out = subprocess.check_output(["aws", "s3", "ls", f"s3://{BUCKET}/{prefix}", "--region", REGION], text=True)
    return [l.split()[-1] for l in out.strip().splitlines() if l.strip()]


def s3_get(key):
    raw = subprocess.check_output(["aws", "s3", "cp", f"s3://{BUCKET}/{key}", "-", "--region", REGION])
    try:
        return json.loads(gzip.decompress(raw))
    except OSError:
        return json.loads(raw)


def bdl_games(date):
    url = f"https://api.balldontlie.io/mlb/v1/games?dates[]={date}&per_page=100"
    raw = subprocess.check_output(["curl", "-sS", "-H", f"Authorization: Bearer {API_KEY}", url])
    return json.loads(raw)["data"]


def parse(ts):
    return datetime.fromisoformat(ts.replace("Z", "+00:00")).astimezone(timezone.utc)


rows = []
for date in DATES:
    games = bdl_games(date)
    starts = {g["id"]: parse(g["date"]) for g in games}
    statuses = {g["id"]: g["status"] for g in games}
    keys = sorted(s3_ls(f"raw/lineups/dt={date}/"))
    window_start = parse(s3_get(f"raw/lineups/dt={date}/{keys[0]}")["observed_at"]) if keys else None
    first_bo = {}
    for k in keys:
        env = s3_get(f"raw/lineups/dt={date}/{k}")
        obs = parse(env["observed_at"])
        for r in env["response"]["data"]:
            gid = r["game_id"]
            if r.get("batting_order") is not None and gid not in first_bo:
                first_bo[gid] = obs
    for gid, start in starts.items():
        obs = first_bo.get(gid)
        lead = (start - obs).total_seconds() / 3600 if obs else None
        # 창 시작 첫 스냅샷에서 이미 공개 = 좌측 절단(하한만 관측)
        censored = bool(obs and window_start and abs((obs - window_start).total_seconds()) < 60)
        rows.append(dict(date=date, gid=gid, start=start, status=statuses.get(gid),
                         first_bo=obs, lead_h=lead, censored=censored))

print("=== 게임별 타순 최초 공개 (경기 시작 대비) ===")
print(f"{'date':11} {'game_id':9} {'start(UTC)':14} {'status':18} {'타순공개':14} {'리드(h)':>8} 비고")
clean = []
for r in sorted(rows, key=lambda x: (x["date"], x["start"])):
    bo = f"{r['first_bo']:%m-%d %H:%MZ}" if r["first_bo"] else "미공개/공백"
    lead = f"{r['lead_h']:.2f}" if r["lead_h"] is not None else "-"
    note = "좌측절단(하한)" if r["censored"] else ("미공개" if r["first_bo"] is None else "")
    if r["lead_h"] is not None and not r["censored"]:
        clean.append(r["lead_h"])
    print(f"{r['date']:11} {r['gid']:<9} {r['start']:%m-%d %H:%MZ} {r['status']:18} {bo:14} {lead:>8} {note}")

print("\n=== 리드타임 분포 (수집 창 안에서 공개 포착된 경기만) ===")
if clean:
    clean.sort()
    print(f"n={len(clean)}  min=T-{max(clean):.2f}h  중앙값=T-{statistics.median(clean):.2f}h  "
          f"max=T-{min(clean):.2f}h  평균=T-{statistics.mean(clean):.2f}h")
    buckets = collections.Counter()
    for l in clean:
        b = "시작후" if l < 0 else f"{int(l)}~{int(l)+1}h"
        buckets[b] += 1
    for b in sorted(buckets, key=lambda x: (x != "시작후", x)):
        print(f"  T-{b}: {buckets[b]}")
else:
    print("수집 창 안에서 공개가 포착된 경기 없음(전부 좌측 절단/미공개).")
