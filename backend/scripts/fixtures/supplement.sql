\set ON_ERROR_STOP on

BEGIN;

-- 실제 전체 타임라인 두 경기 외의 카드용 경기를 추가한다.
-- season·season_type는 확정 마이그레이션(V1)에는 있으나 JPA 엔티티/로컬 dev DB에는 없어 제외한다.
INSERT INTO games (
    game_id, postseason, start_time, status, lifecycle_state,
    period, home_team_id, away_team_id, home_runs, away_runs,
    home_inning_scores, away_inning_scores, venue, pregame_score, peak_base_score,
    final_headline_protected, final_headline_revealed, home_team_name,
    home_team_abbr, away_team_name, away_team_abbr, source, created_at, updated_at,
    observed_at, last_polled_at
)
VALUES
    (8800000001, false, now() + interval '3 hours',
     'STATUS_SCHEDULED', 'SCHEDULED', 9, 16, 22, 4, 2,
     '[0,0,1,0,2,0,0,1,0]'::jsonb, '[0,1,0,0,0,1,0,0,0]'::jsonb,
     'American Family Field', 82, 18,
     '초반부터 팽팽한 흐름이 이어진 경기', '밀워키가 피츠버그를 4-2로 제압한 경기',
     'Milwaukee Brewers', 'MIL', 'Pittsburgh Pirates', 'PIT',
     'FIXTURE', now(), now(), now(), now()),
    (8800000002, false, now() + interval '4 hours',
     'STATUS_SCHEDULED', 'SCHEDULED', 9, 16, 14, 4, 2,
     '[0,1,0,0,0,2,0,1,0]'::jsonb, '[0,0,1,0,0,0,1,0,0]'::jsonb,
     'American Family Field', 76, 20,
     '중반 승부처가 관전 포인트였던 경기', '밀워키가 로스앤젤레스에 4-2로 승리한 경기',
     'Milwaukee Brewers', 'MIL', 'Los Angeles Dodgers', 'LAD',
     'FIXTURE', now(), now(), now(), now()),
    (8800000003, false, now() + interval '5 hours',
     'STATUS_SCHEDULED', 'SCHEDULED', 9, 22, 16, 6, 3,
     '[1,0,0,2,0,0,1,0,2]'::jsonb, '[0,1,0,0,1,0,0,1,0]'::jsonb,
     'PNC Park', 68, 22,
     '후반까지 집중해서 볼 만했던 경기', '피츠버그가 밀워키를 6-3으로 꺾은 경기',
     'Pittsburgh Pirates', 'PIT', 'Milwaukee Brewers', 'MIL',
     'FIXTURE', now(), now(), now(), now()),
    (8800000005, false, now() - interval '2 hours',
     'STATUS_IN_PROGRESS', 'LIVE', 7, 14, 22, 4, 2,
     '[0,1,0,2,0,1]'::jsonb, '[0,0,1,0,1,0]'::jsonb,
     'Dodger Stadium', 64, 72,
     '득점권 승부가 이어지고 있는 경기', '로스앤젤레스가 피츠버그에 4-2로 앞선 경기',
     'Los Angeles Dodgers', 'LAD', 'Pittsburgh Pirates', 'PIT',
     'FIXTURE', now(), now(), now(), now()),
    (8800000007, false, now() - interval '5 hours',
     'STATUS_FINAL', 'FINAL', 9, 1, 16, 4, 2,
     '[0,1,0,0,0,2,0,1,0]'::jsonb, '[0,0,1,0,0,0,1,0,0]'::jsonb,
     'Chase Field', 58, 23,
     '결정적 순간이 후반에 나온 경기', '애리조나가 밀워키에 4-2로 승리한 경기',
     'Arizona Diamondbacks', 'ARI', 'Milwaukee Brewers', 'MIL',
     'FIXTURE', now(), now(), now(), now()),
    (8800000008, false, now() - interval '6 hours',
     'STATUS_FINAL', 'FINAL', 9, 22, 14, 6, 3,
     '[1,0,0,2,0,0,1,0,2]'::jsonb, '[0,1,0,0,1,0,0,1,0]'::jsonb,
     'PNC Park', 61, 25,
     '공수 집중력이 끝까지 빛난 경기', '피츠버그가 로스앤젤레스를 6-3으로 꺾은 경기',
     'Pittsburgh Pirates', 'PIT', 'Los Angeles Dodgers', 'LAD',
     'FIXTURE', now(), now(), now(), now());

-- 전체 타임라인 경기의 누락된 홈 카드·상세 필드를 보강한다.
UPDATE games
SET postseason = false,
    pregame_score = CASE game_id WHEN 8800000004 THEN 74 ELSE 71 END,
    peak_base_score = 27,
    final_headline_protected = CASE game_id
        WHEN 8800000004 THEN '후반까지 흐름을 놓치기 어려운 경기'
        ELSE '마지막까지 긴장감이 이어진 경기'
    END,
    final_headline_revealed = CASE game_id
        WHEN 8800000004 THEN '애리조나가 로스앤젤레스에 5-3으로 승리한 경기'
        ELSE '애리조나가 로스앤젤레스를 5-3으로 꺾은 경기'
    END,
    last_play_order = (
        SELECT max(play_order)
        FROM plays
        WHERE plays.game_id = games.game_id
    ),
    source = 'FIXTURE',
    updated_at = now()
WHERE game_id IN (8800000004, 8800000006);

-- 모든 역할 전환에서 정렬값과 헤드라인이 유지되도록 픽스처별 값을 확정한다.
UPDATE games
SET pregame_score = fixture_values.pregame_score,
    peak_base_score = fixture_values.peak_base_score,
    final_headline_protected = fixture_values.final_headline_protected,
    final_headline_revealed = fixture_values.final_headline_revealed,
    source = 'FIXTURE',
    updated_at = now()
FROM (
    VALUES
        (8800000001::BIGINT, 82::SMALLINT, 18::SMALLINT,
         '초반부터 팽팽한 흐름이 이어진 경기'::TEXT,
         '밀워키가 피츠버그를 4-2로 제압한 경기'::TEXT),
        (8800000002::BIGINT, 76::SMALLINT, 20::SMALLINT,
         '중반 승부처가 관전 포인트였던 경기'::TEXT,
         '밀워키가 로스앤젤레스에 4-2로 승리한 경기'::TEXT),
        (8800000003::BIGINT, 68::SMALLINT, 22::SMALLINT,
         '후반까지 집중해서 볼 만했던 경기'::TEXT,
         '피츠버그가 밀워키를 6-3으로 꺾은 경기'::TEXT),
        (8800000004::BIGINT, 74::SMALLINT, 27::SMALLINT,
         '후반까지 흐름을 놓치기 어려운 경기'::TEXT,
         '애리조나가 로스앤젤레스에 5-3으로 승리한 경기'::TEXT),
        (8800000005::BIGINT, 64::SMALLINT, 72::SMALLINT,
         '득점권 승부가 이어지고 있는 경기'::TEXT,
         '로스앤젤레스가 피츠버그에 4-2로 앞선 경기'::TEXT),
        (8800000006::BIGINT, 71::SMALLINT, 27::SMALLINT,
         '마지막까지 긴장감이 이어진 경기'::TEXT,
         '애리조나가 로스앤젤레스를 5-3으로 꺾은 경기'::TEXT),
        (8800000007::BIGINT, 58::SMALLINT, 23::SMALLINT,
         '결정적 순간이 후반에 나온 경기'::TEXT,
         '애리조나가 밀워키에 4-2로 승리한 경기'::TEXT),
        (8800000008::BIGINT, 61::SMALLINT, 25::SMALLINT,
         '공수 집중력이 끝까지 빛난 경기'::TEXT,
         '피츠버그가 로스앤젤레스를 6-3으로 꺾은 경기'::TEXT)
) AS fixture_values(game_id, pregame_score, peak_base_score, final_headline_protected, final_headline_revealed)
WHERE games.game_id = fixture_values.game_id;

-- 전체 타임라인이 아닌 카드도 라이브 랭킹과 latestTag 폴백에 사용할 최신 점수를 가진다.
INSERT INTO watch_scores (
    game_id, computed_at, play_order, inning, inning_type, base_score,
    importance_multiplier, pregame_bonus, watch_score, signal_contributions,
    tags, backfilled, source
)
VALUES
    (8800000001, now() - interval '8 minutes', 88000000011, 9, 'End', 18, 1.00, 0.00, 18,
     '{"score_gap": 18.0}'::jsonb, ARRAY['접전 흐름'], true, 'FIXTURE'),
    (8800000002, now() - interval '7 minutes', 88000000021, 9, 'End', 20, 1.00, 0.00, 20,
     '{"pressure": 20.0}'::jsonb, ARRAY['긴 승부'], true, 'FIXTURE'),
    (8800000003, now() - interval '6 minutes', 88000000031, 9, 'End', 22, 1.00, 0.00, 22,
     '{"count_pressure": 22.0}'::jsonb, ARRAY['승부처 카운트'], true, 'FIXTURE'),
    (8800000005, now() - interval '5 minutes', 88000000051, 7, 'Bottom', 72, 1.08, 0.00, 78,
     '{"pressure": 48.0, "late_or_extra": 24.0}'::jsonb,
     ARRAY['후반 긴장 구간', '득점권 승부'], false, 'FIXTURE'),
    (8800000007, now() - interval '4 minutes', 88000000071, 9, 'End', 23, 1.00, 0.00, 23,
     '{"recent_score": 23.0}'::jsonb, ARRAY['후반 긴장 구간'], true, 'FIXTURE'),
    (8800000008, now() - interval '3 minutes', 88000000081, 9, 'End', 25, 1.00, 0.00, 25,
     '{"lead_change": 25.0}'::jsonb, ARRAY['접전 흐름'], true, 'FIXTURE');

-- CSV에 포함된 실제 투수만 사용해 각 경기 양 팀의 예상 선발을 구성한다.
INSERT INTO lineups (
    lineup_item_id, game_id, player_id, team_id, batting_order, position,
    is_probable_pitcher, observed_at, source
)
VALUES
    (88000000011, 8800000001, 3315628, 16, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000012, 8800000001, 543, 22, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000021, 8800000002, 5460, 14, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000022, 8800000002, 3315628, 16, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000031, 8800000003, 543, 22, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000032, 8800000003, 3315628, 16, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000041, 8800000004, 4667266, 1, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000042, 8800000004, 5460, 14, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000051, 8800000005, 5460, 14, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000052, 8800000005, 543, 22, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000061, 8800000006, 5460, 14, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000062, 8800000006, 4667266, 1, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000071, 8800000007, 4667266, 1, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000072, 8800000007, 3315628, 16, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000081, 8800000008, 543, 22, NULL, 'SP', true, now(), 'FIXTURE'),
    (88000000082, 8800000008, 5460, 14, NULL, 'SP', true, now(), 'FIXTURE')
ON CONFLICT (game_id, player_id) DO UPDATE SET
    team_id = EXCLUDED.team_id,
    position = EXCLUDED.position,
    is_probable_pitcher = EXCLUDED.is_probable_pitcher,
    observed_at = EXCLUDED.observed_at,
    source = EXCLUDED.source;

-- 보호 문구에는 승패와 점수를 포함하지 않는다.
INSERT INTO game_events (
    game_id, event_type, spoiler_level, source_type, source_ref, inning,
    inning_type, payload, ruleset_version, observed_at, backfilled, source,
    copy_protected, copy_revealed, copy_protected_context_hash,
    copy_revealed_context_hash
)
SELECT game_id,
       event_type,
       'PROTECTED_SAFE',
       'PLAY',
       game_id * 10 + 1,
       inning,
       inning_type,
       jsonb_build_object('fixture', true, 'description', copy_protected),
       'fixture-v1',
       now() - interval '10 minutes' + ((game_id - 8800000001) * interval '1 minute'),
       true,
       'FIXTURE',
       copy_protected,
       copy_revealed,
       'fixture-protected-' || game_id,
       'fixture-revealed-' || game_id
FROM (
    VALUES
        (8800000001::BIGINT, 'pressure_scoring_position'::TEXT, 5::SMALLINT, 'Bottom'::TEXT,
         '득점권에서 긴 승부가 이어졌습니다.'::TEXT, '5회 말 적시타로 경기 흐름이 바뀌었습니다.'::TEXT),
        (8800000002::BIGINT, 'long_at_bat'::TEXT, 6::SMALLINT, 'Top'::TEXT,
         '긴 타석 승부가 관전 포인트였습니다.'::TEXT, '6회 초 긴 승부 끝에 출루가 나왔습니다.'::TEXT),
        (8800000003::BIGINT, 'full_count_two_out'::TEXT, 7::SMALLINT, 'Bottom'::TEXT,
         '투아웃 풀카운트 승부가 이어졌습니다.'::TEXT, '7회 말 풀카운트 승부가 추가점으로 연결됐습니다.'::TEXT),
        (8800000004::BIGINT, 'pressure_bases_loaded'::TEXT, 8::SMALLINT, 'Top'::TEXT,
         '만루에서 중요한 승부가 진행 중입니다.'::TEXT, '8회 초 만루 기회가 경기의 핵심 장면이 됐습니다.'::TEXT),
        (8800000005::BIGINT, 'pitcher_instability'::TEXT, 7::SMALLINT, 'Bottom'::TEXT,
         '투수가 흔들리며 긴장감이 높아졌습니다.'::TEXT, '7회 말 연속 출루로 마운드가 흔들렸습니다.'::TEXT),
        (8800000006::BIGINT, 'hard_contact'::TEXT, 9::SMALLINT, 'Top'::TEXT,
         '후반 강한 타구가 승부처를 만들었습니다.'::TEXT, '9회 초 강한 타구가 쐐기 득점으로 이어졌습니다.'::TEXT),
        (8800000007::BIGINT, 'pressure_scoring_position'::TEXT, 8::SMALLINT, 'Bottom'::TEXT,
         '후반 득점권 승부가 관전 포인트였습니다.'::TEXT, '8회 말 적시타로 승부가 기울었습니다.'::TEXT),
        (8800000008::BIGINT, 'long_at_bat'::TEXT, 9::SMALLINT, 'Bottom'::TEXT,
         '마지막 이닝의 긴 승부가 인상적이었습니다.'::TEXT, '9회 말 긴 타석 승부로 경기가 마무리됐습니다.'::TEXT)
) AS fixture_events(game_id, event_type, inning, inning_type, copy_protected, copy_revealed)
ON CONFLICT (game_id, event_type, source_type, source_ref) DO UPDATE SET
    spoiler_level = EXCLUDED.spoiler_level,
    inning = EXCLUDED.inning,
    inning_type = EXCLUDED.inning_type,
    payload = EXCLUDED.payload,
    ruleset_version = EXCLUDED.ruleset_version,
    observed_at = EXCLUDED.observed_at,
    backfilled = EXCLUDED.backfilled,
    source = EXCLUDED.source,
    copy_protected = EXCLUDED.copy_protected,
    copy_revealed = EXCLUDED.copy_revealed,
    copy_protected_context_hash = EXCLUDED.copy_protected_context_hash,
    copy_revealed_context_hash = EXCLUDED.copy_revealed_context_hash;

COMMIT;
