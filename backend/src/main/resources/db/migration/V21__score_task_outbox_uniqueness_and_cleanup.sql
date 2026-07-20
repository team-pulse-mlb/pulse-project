WITH ranked AS (
    SELECT outbox_id,
           ROW_NUMBER() OVER (
               PARTITION BY game_id, observed_at
               ORDER BY created_at, outbox_id
           ) AS duplicate_rank
    FROM score_task_outbox
)
DELETE FROM score_task_outbox
WHERE outbox_id IN (
    SELECT outbox_id
    FROM ranked
    WHERE duplicate_rank > 1
);

ALTER TABLE score_task_outbox
    DROP CONSTRAINT IF EXISTS uq_score_task_outbox_cycle;

ALTER TABLE score_task_outbox
    ADD CONSTRAINT uq_score_task_outbox_cycle UNIQUE (game_id, observed_at);

CREATE INDEX IF NOT EXISTS idx_score_task_outbox_published_cleanup
    ON score_task_outbox (published_at, created_at)
    WHERE status = 'PUBLISHED';
