-- standings.last_ten_games 타입 교정 (DB_SCHEMA.md §A-? standings).
-- balldontlie는 최근 10경기 전적을 "5-5"(승-패) 문자열로 제공하므로 SMALLINT로는 담을 수 없다.
-- 참고용 필드라 원문 문자열을 그대로 보존한다.

ALTER TABLE standings ALTER COLUMN last_ten_games TYPE TEXT USING last_ten_games::text;
