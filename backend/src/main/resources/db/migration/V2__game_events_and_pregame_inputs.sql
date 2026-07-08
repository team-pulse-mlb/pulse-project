-- 저장 정책 확정 반영 (DB_SCHEMA.md §A-5, §A-1 pregame_inputs).
-- game_events: scorer가 라이브 중 추출하는 흥미 순간 이벤트 append 로그.
-- games.pregame_inputs: pregame_score 계산 시점 입력 스냅샷.

CREATE TABLE game_events (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    event_type TEXT NOT NULL,
    spoiler_level TEXT,
    source_type TEXT NOT NULL,
    source_ref BIGINT NOT NULL,
    inning SMALLINT,
    inning_type TEXT,
    batter_id BIGINT,
    pitcher_id BIGINT,
    payload JSONB,
    ruleset_version TEXT,
    observed_at TIMESTAMPTZ,
    backfilled BOOLEAN NOT NULL DEFAULT false,
    source TEXT NOT NULL DEFAULT 'OPERATIONAL',
    CONSTRAINT uk_game_events_dedupe UNIQUE (game_id, event_type, source_type, source_ref),
    CONSTRAINT fk_game_events_game FOREIGN KEY (game_id) REFERENCES games (game_id),
    CONSTRAINT fk_game_events_batter FOREIGN KEY (batter_id) REFERENCES players (player_id),
    CONSTRAINT fk_game_events_pitcher FOREIGN KEY (pitcher_id) REFERENCES players (player_id)
);

CREATE INDEX idx_game_events_game_observed_at ON game_events (game_id, observed_at);

ALTER TABLE games ADD COLUMN pregame_inputs JSONB;
