-- Redis 초기화와 무관하게 종료 task 및 일회성 후처리의 멱등성을 보장한다.
ALTER TABLE games
    ADD COLUMN finalized_at TIMESTAMPTZ,
    ADD COLUMN terminal_done_at TIMESTAMPTZ,
    ADD COLUMN terminal_suspended_postponed_at TIMESTAMPTZ;
