-- PULSE 운영 DB 베이스라인 (DB_SCHEMA.md 기준).
-- plays.runner_on_* 3컬럼은 이전 배치의 압박 주자 상태 영속용 추가분.

CREATE TABLE teams (
    team_id BIGINT PRIMARY KEY,
    abbreviation TEXT,
    display_name TEXT,
    short_display_name TEXT,
    name TEXT,
    location TEXT,
    slug TEXT,
    league TEXT,
    division TEXT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE TABLE players (
    player_id BIGINT PRIMARY KEY,
    full_name TEXT,
    first_name TEXT,
    last_name TEXT,
    position TEXT,
    team_id BIGINT,
    jersey TEXT,
    bats_throws TEXT,
    dob DATE,
    debut_year INT,
    active BOOLEAN,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_players_team FOREIGN KEY (team_id) REFERENCES teams (team_id)
);

CREATE INDEX idx_players_team_id ON players (team_id);
CREATE INDEX idx_players_full_name ON players (full_name);

CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE games (
    game_id BIGINT PRIMARY KEY,
    season INT,
    season_type TEXT,
    postseason BOOLEAN,
    start_time TIMESTAMPTZ,
    status TEXT,
    lifecycle_state TEXT,
    period SMALLINT,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    home_runs SMALLINT,
    away_runs SMALLINT,
    home_hits SMALLINT,
    away_hits SMALLINT,
    home_errors SMALLINT,
    away_errors SMALLINT,
    home_inning_scores JSONB,
    away_inning_scores JSONB,
    venue TEXT,
    attendance INT,
    pregame_score SMALLINT,
    peak_base_score SMALLINT,
    final_headline TEXT,
    last_play_order BIGINT,
    last_polled_at TIMESTAMPTZ,
    observed_at TIMESTAMPTZ,
    source TEXT NOT NULL DEFAULT 'OPERATIONAL',
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_games_home_team FOREIGN KEY (home_team_id) REFERENCES teams (team_id),
    CONSTRAINT fk_games_away_team FOREIGN KEY (away_team_id) REFERENCES teams (team_id)
);

CREATE INDEX idx_games_lifecycle_state ON games (lifecycle_state);
CREATE INDEX idx_games_start_time ON games (start_time);
CREATE INDEX idx_games_status ON games (status);

CREATE TABLE plays (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    play_order BIGINT NOT NULL,
    type TEXT,
    inning SMALLINT,
    inning_type TEXT,
    text TEXT,
    home_score SMALLINT,
    away_score SMALLINT,
    scoring_play BOOLEAN,
    score_value SMALLINT,
    outs SMALLINT,
    balls SMALLINT,
    strikes SMALLINT,
    batter_id BIGINT,
    pitcher_id BIGINT,
    pitch_type TEXT,
    pitch_velocity SMALLINT,
    hit_coordinate_x SMALLINT,
    hit_coordinate_y SMALLINT,
    trajectory TEXT,
    observed_at TIMESTAMPTZ,
    backfilled BOOLEAN NOT NULL DEFAULT false,
    source TEXT NOT NULL DEFAULT 'OPERATIONAL',
    runner_on_first BOOLEAN,
    runner_on_second BOOLEAN,
    runner_on_third BOOLEAN,
    CONSTRAINT uk_plays_game_order UNIQUE (game_id, play_order),
    CONSTRAINT fk_plays_game FOREIGN KEY (game_id) REFERENCES games (game_id),
    CONSTRAINT fk_plays_batter FOREIGN KEY (batter_id) REFERENCES players (player_id),
    CONSTRAINT fk_plays_pitcher FOREIGN KEY (pitcher_id) REFERENCES players (player_id)
);

CREATE INDEX idx_plays_game_order ON plays (game_id, play_order);

CREATE TABLE watch_scores (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    computed_at TIMESTAMPTZ,
    play_order BIGINT,
    inning SMALLINT,
    inning_type TEXT,
    base_score SMALLINT,
    importance_multiplier NUMERIC(4,2),
    pregame_bonus NUMERIC(4,2),
    watch_score SMALLINT,
    signal_contributions JSONB,
    tags TEXT[],
    backfilled BOOLEAN NOT NULL DEFAULT false,
    source TEXT NOT NULL DEFAULT 'OPERATIONAL',
    CONSTRAINT uk_watch_scores_game_computed_at UNIQUE (game_id, computed_at),
    CONSTRAINT fk_watch_scores_game FOREIGN KEY (game_id) REFERENCES games (game_id)
);

CREATE INDEX idx_watch_scores_game_computed_at ON watch_scores (game_id, computed_at);

CREATE TABLE replay_segments (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    start_play_order BIGINT,
    end_play_order BIGINT,
    start_inning SMALLINT,
    end_inning SMALLINT,
    start_inning_type TEXT,
    end_inning_type TEXT,
    peak_score SMALLINT,
    tags TEXT[],
    ai_summary TEXT,
    status TEXT,
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    source TEXT NOT NULL DEFAULT 'OPERATIONAL',
    CONSTRAINT fk_replay_segments_game FOREIGN KEY (game_id) REFERENCES games (game_id)
);

CREATE INDEX idx_replay_segments_game_peak_score ON replay_segments (game_id, peak_score DESC);

CREATE TABLE refresh_tokens (
    refresh_token_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    favorite_team_ids BIGINT[],
    favorite_player_ids BIGINT[],
    notify_enabled BOOLEAN DEFAULT true,
    notify_game_start BOOLEAN DEFAULT true,
    notify_surge_enabled BOOLEAN DEFAULT true,
    recommend_switch_enabled BOOLEAN DEFAULT true,
    show_finished_games BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE INDEX idx_user_preferences_favorite_team_ids ON user_preferences USING GIN (favorite_team_ids);
CREATE INDEX idx_user_preferences_favorite_player_ids ON user_preferences USING GIN (favorite_player_ids);

CREATE TABLE notification_events (
    event_id UUID PRIMARY KEY,
    type TEXT,
    game_id BIGINT NOT NULL,
    tags TEXT[],
    occurred_at TIMESTAMPTZ,
    CONSTRAINT fk_notification_events_game FOREIGN KEY (game_id) REFERENCES games (game_id)
);

CREATE INDEX idx_notification_events_game_occurred_at ON notification_events (game_id, occurred_at);

CREATE TABLE user_notifications (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    message TEXT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ,
    CONSTRAINT uk_user_notifications_event_user UNIQUE (event_id, user_id),
    CONSTRAINT fk_user_notifications_event FOREIGN KEY (event_id) REFERENCES notification_events (event_id),
    CONSTRAINT fk_user_notifications_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE INDEX idx_user_notifications_user_created_at ON user_notifications (user_id, created_at DESC);

CREATE TABLE lineups (
    lineup_item_id BIGINT PRIMARY KEY,
    game_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    batting_order SMALLINT,
    position TEXT,
    is_probable_pitcher BOOLEAN,
    observed_at TIMESTAMPTZ,
    source TEXT NOT NULL DEFAULT 'OPERATIONAL',
    CONSTRAINT uk_lineups_game_player UNIQUE (game_id, player_id),
    CONSTRAINT fk_lineups_game FOREIGN KEY (game_id) REFERENCES games (game_id),
    CONSTRAINT fk_lineups_player FOREIGN KEY (player_id) REFERENCES players (player_id),
    CONSTRAINT fk_lineups_team FOREIGN KEY (team_id) REFERENCES teams (team_id)
);

CREATE INDEX idx_lineups_game_id ON lineups (game_id);

CREATE TABLE odds_snapshots (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    vendor TEXT,
    snapshot_type TEXT,
    moneyline_home_odds INT,
    moneyline_away_odds INT,
    spread_home_value NUMERIC(3,1),
    spread_away_value NUMERIC(3,1),
    spread_home_odds INT,
    spread_away_odds INT,
    total_value NUMERIC(3,1),
    total_over_odds INT,
    total_under_odds INT,
    vendor_updated_at TIMESTAMPTZ,
    observed_at TIMESTAMPTZ,
    source TEXT NOT NULL DEFAULT 'OPERATIONAL',
    CONSTRAINT uk_odds_snapshots_game_vendor_type UNIQUE (game_id, vendor, snapshot_type),
    CONSTRAINT fk_odds_snapshots_game FOREIGN KEY (game_id) REFERENCES games (game_id)
);

CREATE TABLE standings (
    id BIGSERIAL PRIMARY KEY,
    season INT,
    snapshot_date DATE,
    team_id BIGINT NOT NULL,
    league_name TEXT,
    division_name TEXT,
    wins SMALLINT,
    losses SMALLINT,
    win_percent NUMERIC(4,3),
    games_behind NUMERIC(4,1),
    playoff_percent NUMERIC(5,2),
    wildcard_percent NUMERIC(5,2),
    streak SMALLINT,
    last_ten_games SMALLINT,
    observed_at TIMESTAMPTZ,
    source TEXT NOT NULL DEFAULT 'OPERATIONAL',
    CONSTRAINT uk_standings_season_snapshot_team UNIQUE (season, snapshot_date, team_id),
    CONSTRAINT fk_standings_team FOREIGN KEY (team_id) REFERENCES teams (team_id)
);

CREATE TABLE player_season_stats (
    season INT NOT NULL,
    player_id BIGINT NOT NULL,
    pitching_era NUMERIC(4,2),
    pitching_war NUMERIC(4,2),
    pitching_whip NUMERIC(4,2),
    pitching_k_per_9 NUMERIC(4,2),
    batting_war NUMERIC(4,2),
    batting_ops NUMERIC(4,3),
    batting_hr SMALLINT,
    updated_at TIMESTAMPTZ,
    CONSTRAINT pk_player_season_stats PRIMARY KEY (season, player_id),
    CONSTRAINT fk_player_season_stats_player FOREIGN KEY (player_id) REFERENCES players (player_id)
);
