-- DB_SCHEMA.md B-5 확정에 따라 관심 선수 조인 테이블을 추가한다.

CREATE TABLE user_favorite_players (
    user_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ,
    CONSTRAINT pk_user_favorite_players PRIMARY KEY (user_id, player_id),
    CONSTRAINT fk_user_favorite_players_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_favorite_players_player FOREIGN KEY (player_id) REFERENCES players (player_id)
);

CREATE INDEX idx_user_favorite_players_player_id ON user_favorite_players (player_id);
