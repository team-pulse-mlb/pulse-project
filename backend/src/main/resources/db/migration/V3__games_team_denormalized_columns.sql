-- games에 팀명·약자 비정규화 컬럼 추가 (DB_SCHEMA.md §A-1).
-- 폴러·리플레이가 balldontlie 응답에서 채우고 홈·상세 조회가 팀 라벨로 읽는다.
-- 기존 행을 위해 nullable로 추가하며, 이후 폴링 upsert가 값을 채운다.

ALTER TABLE games ADD COLUMN home_team_name TEXT;
ALTER TABLE games ADD COLUMN home_team_abbr TEXT;
ALTER TABLE games ADD COLUMN away_team_name TEXT;
ALTER TABLE games ADD COLUMN away_team_abbr TEXT;
