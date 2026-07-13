-- DB_SCHEMA.md B-3, B-4 확정에 따라 배열 방식 user_preferences를 폐기하고
-- user_settings, user_favorite_teams로 대체한다.
-- teams에는 로고 매핑 컬럼(logo_team_id)을 추가하고, poller가 채우지 않는
-- 마스터 상세 컬럼을 30팀 시드로 채운다.

-- 1. teams 로고 매핑 컬럼
ALTER TABLE teams ADD COLUMN IF NOT EXISTS logo_team_id BIGINT;

-- 2. 팀 마스터 시드 (team_id는 balldontlie id, logo_team_id는 MLB statsapi id)
--    abbreviation, display_name은 poller upsert와 동일한 값이라 충돌해도 안전하다.
INSERT INTO teams (
    team_id, logo_team_id, abbreviation, display_name, short_display_name,
    name, location, slug, league, division, created_at, updated_at
)
VALUES
    (1, 109, 'ARI', 'Arizona Diamondbacks', 'Diamondbacks', 'Diamondbacks', 'Arizona', 'arizona-diamondbacks', 'National', 'West', now(), now()),
    (2, 144, 'ATL', 'Atlanta Braves', 'Braves', 'Braves', 'Atlanta', 'atlanta-braves', 'National', 'East', now(), now()),
    (3, 110, 'BAL', 'Baltimore Orioles', 'Orioles', 'Orioles', 'Baltimore', 'baltimore-orioles', 'American', 'East', now(), now()),
    (4, 111, 'BOS', 'Boston Red Sox', 'Red Sox', 'Red Sox', 'Boston', 'boston-red-sox', 'American', 'East', now(), now()),
    (5, 112, 'CHC', 'Chicago Cubs', 'Cubs', 'Cubs', 'Chicago', 'chicago-cubs', 'National', 'Central', now(), now()),
    (6, 145, 'CHW', 'Chicago White Sox', 'White Sox', 'White Sox', 'Chicago', 'chicago-white-sox', 'American', 'Central', now(), now()),
    (7, 113, 'CIN', 'Cincinnati Reds', 'Reds', 'Reds', 'Cincinnati', 'cincinnati-reds', 'National', 'Central', now(), now()),
    (8, 114, 'CLE', 'Cleveland Guardians', 'Guardians', 'Guardians', 'Cleveland', 'cleveland-guardians', 'American', 'Central', now(), now()),
    (9, 115, 'COL', 'Colorado Rockies', 'Rockies', 'Rockies', 'Colorado', 'colorado-rockies', 'National', 'West', now(), now()),
    (10, 116, 'DET', 'Detroit Tigers', 'Tigers', 'Tigers', 'Detroit', 'detroit-tigers', 'American', 'Central', now(), now()),
    (11, 117, 'HOU', 'Houston Astros', 'Astros', 'Astros', 'Houston', 'houston-astros', 'American', 'West', now(), now()),
    (12, 118, 'KC', 'Kansas City Royals', 'Royals', 'Royals', 'Kansas City', 'kansas-city-royals', 'American', 'Central', now(), now()),
    (13, 108, 'LAA', 'Los Angeles Angels', 'Angels', 'Angels', 'Los Angeles', 'los-angeles-angels', 'American', 'West', now(), now()),
    (14, 119, 'LAD', 'Los Angeles Dodgers', 'Dodgers', 'Dodgers', 'Los Angeles', 'los-angeles-dodgers', 'National', 'West', now(), now()),
    (15, 146, 'MIA', 'Miami Marlins', 'Marlins', 'Marlins', 'Miami', 'miami-marlins', 'National', 'East', now(), now()),
    (16, 158, 'MIL', 'Milwaukee Brewers', 'Brewers', 'Brewers', 'Milwaukee', 'milwaukee-brewers', 'National', 'Central', now(), now()),
    (17, 142, 'MIN', 'Minnesota Twins', 'Twins', 'Twins', 'Minnesota', 'minnesota-twins', 'American', 'Central', now(), now()),
    (18, 121, 'NYM', 'New York Mets', 'Mets', 'Mets', 'New York', 'new-york-mets', 'National', 'East', now(), now()),
    (19, 147, 'NYY', 'New York Yankees', 'Yankees', 'Yankees', 'New York', 'new-york-yankees', 'American', 'East', now(), now()),
    (20, 133, 'OAK', 'Athletics', 'Athletics', 'Athletics', 'Oakland', 'oakland-athletics', 'American', 'West', now(), now()),
    (21, 143, 'PHI', 'Philadelphia Phillies', 'Phillies', 'Phillies', 'Philadelphia', 'philadelphia-phillies', 'National', 'East', now(), now()),
    (22, 134, 'PIT', 'Pittsburgh Pirates', 'Pirates', 'Pirates', 'Pittsburgh', 'pittsburgh-pirates', 'National', 'Central', now(), now()),
    (23, 135, 'SD', 'San Diego Padres', 'Padres', 'Padres', 'San Diego', 'san-diego-padres', 'National', 'West', now(), now()),
    (24, 137, 'SF', 'San Francisco Giants', 'Giants', 'Giants', 'San Francisco', 'san-francisco-giants', 'National', 'West', now(), now()),
    (25, 136, 'SEA', 'Seattle Mariners', 'Mariners', 'Mariners', 'Seattle', 'seattle-mariners', 'American', 'West', now(), now()),
    (26, 138, 'STL', 'St. Louis Cardinals', 'Cardinals', 'Cardinals', 'St. Louis', 'st-louis-cardinals', 'National', 'Central', now(), now()),
    (27, 139, 'TB', 'Tampa Bay Rays', 'Rays', 'Rays', 'Tampa Bay', 'tampa-bay-rays', 'American', 'East', now(), now()),
    (28, 140, 'TEX', 'Texas Rangers', 'Rangers', 'Rangers', 'Texas', 'texas-rangers', 'American', 'West', now(), now()),
    (29, 141, 'TOR', 'Toronto Blue Jays', 'Blue Jays', 'Blue Jays', 'Toronto', 'toronto-blue-jays', 'American', 'East', now(), now()),
    (30, 120, 'WSH', 'Washington Nationals', 'Nationals', 'Nationals', 'Washington', 'washington-nationals', 'National', 'East', now(), now())
ON CONFLICT (team_id) DO UPDATE SET
    logo_team_id = EXCLUDED.logo_team_id,
    abbreviation = EXCLUDED.abbreviation,
    display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    name = EXCLUDED.name,
    location = EXCLUDED.location,
    slug = EXCLUDED.slug,
    league = EXCLUDED.league,
    division = EXCLUDED.division,
    updated_at = now();

-- 3. 배열 방식 사용자 선호 테이블 폐기 (참조 코드 없음)
DROP TABLE IF EXISTS user_preferences;

-- 4. 사용자 설정 (DB_SCHEMA.md B-3)
CREATE TABLE user_settings (
    user_id BIGINT PRIMARY KEY,
    notify_game_start BOOLEAN NOT NULL DEFAULT true,
    notify_surge_enabled BOOLEAN NOT NULL DEFAULT true,
    recommend_switch_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- 5. 관심 팀 조인 (DB_SCHEMA.md B-4)
CREATE TABLE user_favorite_teams (
    user_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ,
    CONSTRAINT pk_user_favorite_teams PRIMARY KEY (user_id, team_id),
    CONSTRAINT fk_user_favorite_teams_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_favorite_teams_team FOREIGN KEY (team_id) REFERENCES teams (team_id)
);

CREATE INDEX idx_user_favorite_teams_team_id ON user_favorite_teams (team_id);
