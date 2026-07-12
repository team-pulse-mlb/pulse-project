-- 보호·공개 문구의 입력 컨텍스트가 달라 모드별 해시를 독립적으로 저장한다.
ALTER TABLE game_events ADD COLUMN copy_protected_context_hash TEXT;
ALTER TABLE game_events ADD COLUMN copy_revealed_context_hash TEXT;
ALTER TABLE game_events DROP COLUMN copy_context_hash;
