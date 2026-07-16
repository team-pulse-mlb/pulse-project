-- PLAY_TRANSLATION 실패·검수 반려의 무한 재호출을 제한한다.
ALTER TABLE plays
    ADD COLUMN text_ko_attempts INTEGER NOT NULL DEFAULT 0;
