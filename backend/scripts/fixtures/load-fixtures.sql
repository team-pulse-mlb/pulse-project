\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE fixture_teams (LIKE teams INCLUDING DEFAULTS) ON COMMIT DROP;
CREATE TEMP TABLE fixture_players (LIKE players INCLUDING DEFAULTS) ON COMMIT DROP;
CREATE TEMP TABLE fixture_games (LIKE games INCLUDING DEFAULTS) ON COMMIT DROP;
CREATE TEMP TABLE fixture_plays (LIKE plays INCLUDING DEFAULTS) ON COMMIT DROP;

-- COPY는 CSV 헤더명이 아니라 아래 컬럼 위치로 매핑하므로 덤프 순서를 명시한다.
\copy fixture_teams (team_id, abbreviation, created_at, display_name, division, league, location, logo_team_id, name, short_display_name, slug, updated_at) FROM '/tmp/pulse-fixtures/teams.csv' WITH (FORMAT csv, HEADER true)
\copy fixture_players (player_id, created_at, first_name, full_name, last_name, position, team_id, updated_at) FROM '/tmp/pulse-fixtures/players.csv' WITH (FORMAT csv, HEADER true)
\copy fixture_games (game_id, away_inning_scores, away_runs, away_team_abbr, away_team_id, away_team_name, created_at, final_headline_protected, final_headline_revealed, home_inning_scores, home_runs, home_team_abbr, home_team_id, home_team_name, last_play_order, last_polled_at, lifecycle_state, observed_at, peak_base_score, period, postseason, pregame_inputs, pregame_score, source, start_time, status, updated_at, venue) FROM '/tmp/pulse-fixtures/game_5059222.csv' WITH (FORMAT csv, HEADER true)
\copy fixture_plays (id, away_score, backfilled, balls, batter_id, observed_at, game_id, home_score, inning, inning_type, outs, pitcher_id, play_order, runner_on_first, runner_on_second, runner_on_third, score_value, scoring_play, source, strikes, text, type) FROM '/tmp/pulse-fixtures/plays_5059222.csv' WITH (FORMAT csv, HEADER true)

-- 이전 실행에서 만든 픽스처 원본과 고정 시뮬레이션 대상만 정리한다. 운영 경기는 유지한다.
-- user_notifications는 로컬 dev DB(JPA ddl-auto)에는 아직 엔티티가 없어 테이블이 없을 수 있으므로
-- 존재할 때만 정리한다. 확정 마이그레이션(V1)에는 있는 테이블이다.
DO $$
BEGIN
    IF to_regclass('public.user_notifications') IS NOT NULL THEN
        DELETE FROM user_notifications
        WHERE event_id IN (
            SELECT event_id
            FROM notification_events
            WHERE game_id BETWEEN 8800000001 AND 8800000008
               OR game_id IN (14059224001, 14059226001, 14059226002)
        );
    END IF;
END $$;
DELETE FROM notification_outbox
WHERE game_id BETWEEN 8800000001 AND 8800000008
   OR game_id IN (14059224001, 14059226001, 14059226002);
DELETE FROM notification_events
WHERE game_id BETWEEN 8800000001 AND 8800000008
   OR game_id IN (14059224001, 14059226001, 14059226002);
DELETE FROM game_events
WHERE game_id BETWEEN 8800000001 AND 8800000008
   OR game_id IN (14059224001, 14059226001, 14059226002);
DELETE FROM lineups
WHERE game_id BETWEEN 8800000001 AND 8800000008
   OR game_id IN (14059224001, 14059226001, 14059226002);
DELETE FROM odds_snapshots
WHERE game_id BETWEEN 8800000001 AND 8800000008
   OR game_id IN (14059224001, 14059226001, 14059226002);
DELETE FROM plays
WHERE game_id BETWEEN 8800000001 AND 8800000008
   OR game_id IN (14059224001, 14059226001, 14059226002);
DELETE FROM watch_scores
WHERE game_id BETWEEN 8800000001 AND 8800000008
   OR game_id IN (14059224001, 14059226001, 14059226002);
DO $$
BEGIN
    IF to_regclass('public.replay_segments') IS NOT NULL THEN
        DELETE FROM replay_segments
        WHERE game_id BETWEEN 8800000001 AND 8800000008
           OR game_id IN (14059224001, 14059226001, 14059226002);
    END IF;
END $$;
DELETE FROM games
WHERE game_id BETWEEN 8800000001 AND 8800000008
   OR game_id IN (14059224001, 14059226001, 14059226002);

INSERT INTO teams (
    team_id, abbreviation, created_at, display_name, division, league,
    location, logo_team_id, name, short_display_name, slug, updated_at
)
SELECT team_id, abbreviation, created_at, display_name, division, league,
       location, logo_team_id, name, short_display_name, slug, updated_at
FROM fixture_teams
ON CONFLICT (team_id) DO NOTHING;

INSERT INTO players (
    player_id, created_at, first_name, full_name, last_name, position, team_id, updated_at
)
SELECT player_id, created_at, first_name, full_name, last_name, position, team_id, updated_at
FROM fixture_players
ON CONFLICT (player_id) DO NOTHING;

-- 실제 완주 경기 스냅샷을 시뮬레이터 원본으로 두 벌 복제한다.
-- 이 행들은 화면 카드가 아니라 시뮬레이터가 읽을 games+plays 원본이다. 슬레이트(오늘)에
-- 직접 노출되지 않도록 과거 시각의 종료 상태로 적재하고, final_headline 등 파생값은 비운다.
-- 카드·점수·문구는 시뮬레이션 poller가 만드는 target 경기에서 라이브로 생성된다.
INSERT INTO games (
    game_id, away_inning_scores, away_runs, away_team_abbr, away_team_id,
    away_team_name, created_at, final_headline_protected, final_headline_revealed,
    home_inning_scores, home_runs, home_team_abbr, home_team_id, home_team_name,
    last_play_order, last_polled_at, lifecycle_state, observed_at, peak_base_score,
    period, postseason, pregame_inputs, pregame_score, source, start_time, status,
    updated_at, venue
)
SELECT target.game_id,
       CASE WHEN target.reverse_matchup THEN source.home_inning_scores ELSE source.away_inning_scores END,
       CASE WHEN target.reverse_matchup THEN source.home_runs ELSE source.away_runs END,
       CASE WHEN target.reverse_matchup THEN source.home_team_abbr ELSE source.away_team_abbr END,
       CASE WHEN target.reverse_matchup THEN source.home_team_id ELSE source.away_team_id END,
       CASE WHEN target.reverse_matchup THEN source.home_team_name ELSE source.away_team_name END,
       COALESCE(source.created_at, now()),
       NULL,
       NULL,
       CASE WHEN target.reverse_matchup THEN source.away_inning_scores ELSE source.home_inning_scores END,
       CASE WHEN target.reverse_matchup THEN source.away_runs ELSE source.home_runs END,
       CASE WHEN target.reverse_matchup THEN source.away_team_abbr ELSE source.home_team_abbr END,
       CASE WHEN target.reverse_matchup THEN source.away_team_id ELSE source.home_team_id END,
       CASE WHEN target.reverse_matchup THEN source.away_team_name ELSE source.home_team_name END,
       source.last_play_order,
       source.last_polled_at,
       'FINAL',
       source.observed_at,
       source.peak_base_score,
       source.period,
       source.postseason,
       source.pregame_inputs,
       source.pregame_score,
       'FIXTURE',
       now() - interval '30 days',
       'STATUS_FINAL',
       COALESCE(source.updated_at, now()),
       source.venue
FROM fixture_games source
CROSS JOIN (
    VALUES
        (8800000004::BIGINT, true),
        (8800000006::BIGINT, false)
) AS target(game_id, reverse_matchup);

INSERT INTO plays (
    away_score, backfilled, balls, batter_id, observed_at, game_id, home_score,
    inning, inning_type, outs, pitcher_id, play_order, runner_on_first,
    runner_on_second, runner_on_third, score_value, scoring_play, source,
    strikes, text, type
)
SELECT CASE WHEN target.reverse_matchup THEN source.home_score ELSE source.away_score END,
       source.backfilled,
       source.balls,
       source.batter_id,
       source.observed_at,
       target.game_id,
       CASE WHEN target.reverse_matchup THEN source.away_score ELSE source.home_score END,
       source.inning,
       source.inning_type,
       source.outs,
       source.pitcher_id,
       source.play_order,
       source.runner_on_first,
       source.runner_on_second,
       source.runner_on_third,
       source.score_value,
       source.scoring_play,
       'FIXTURE',
       source.strikes,
       source.text,
       source.type
FROM fixture_plays source
CROSS JOIN (
    VALUES (8800000004::BIGINT, true), (8800000006::BIGINT, false)
) AS target(game_id, reverse_matchup);

COMMIT;
