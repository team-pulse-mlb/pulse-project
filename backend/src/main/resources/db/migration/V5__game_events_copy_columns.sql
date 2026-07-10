-- game_events EVENT_COPY 저장 컬럼 추가 및 기존 games 팀 라벨 백필.
-- replay_segments 운영 데이터는 보존하며 이 마이그레이션에서 DROP하지 않는다.

ALTER TABLE game_events ADD COLUMN copy_protected TEXT;
ALTER TABLE game_events ADD COLUMN copy_revealed TEXT;
ALTER TABLE game_events ADD COLUMN copy_context_hash TEXT;

UPDATE games g
SET home_team_name = COALESCE(g.home_team_name, home_team.display_name),
    home_team_abbr = COALESCE(g.home_team_abbr, home_team.abbreviation),
    away_team_name = COALESCE(g.away_team_name, away_team.display_name),
    away_team_abbr = COALESCE(g.away_team_abbr, away_team.abbreviation)
FROM teams home_team,
     teams away_team
WHERE g.home_team_id = home_team.team_id
  AND g.away_team_id = away_team.team_id
  AND (
      g.home_team_name IS NULL
      OR g.home_team_abbr IS NULL
      OR g.away_team_name IS NULL
      OR g.away_team_abbr IS NULL
  );
