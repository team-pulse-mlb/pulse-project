CREATE TABLE score_task_outbox (
    outbox_id UUID PRIMARY KEY,
    game_id BIGINT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT uq_score_task_outbox_cycle UNIQUE (game_id, observed_at),
    CONSTRAINT fk_score_task_outbox_game
        FOREIGN KEY (game_id) REFERENCES games (game_id),
    CONSTRAINT ck_score_task_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_score_task_outbox_pending
    ON score_task_outbox (next_attempt_at, created_at)
    WHERE status = 'PENDING';
