-- 보호 모드 이벤트 타임라인을 '급변 하이라이트' 단위로 노출하기 위한 표시 플래그.
-- 이벤트는 그대로 append하되, 추천 점수가 급변한 순간의 anchor 이벤트만 true로 표시한다.
-- 조회는 spoiler_level=PROTECTED_SAFE + is_timeline_highlight=true 로 필터한다.

ALTER TABLE game_events
    ADD COLUMN is_timeline_highlight BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_game_events_game_highlight
    ON game_events (game_id, is_timeline_highlight, observed_at);
