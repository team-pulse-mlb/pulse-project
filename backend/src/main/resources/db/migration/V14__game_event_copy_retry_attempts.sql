ALTER TABLE game_events
    ADD COLUMN copy_protected_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE game_events
    ADD COLUMN copy_revealed_attempts INTEGER NOT NULL DEFAULT 0;
