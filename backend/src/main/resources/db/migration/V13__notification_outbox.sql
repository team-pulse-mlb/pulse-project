CREATE TABLE notification_outbox (
    outbox_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(30) NOT NULL,
    game_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    latest_tag TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT fk_notification_outbox_event
        FOREIGN KEY (event_id) REFERENCES notification_events (event_id),
    CONSTRAINT ck_notification_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox (next_attempt_at, created_at)
    WHERE status = 'PENDING';
