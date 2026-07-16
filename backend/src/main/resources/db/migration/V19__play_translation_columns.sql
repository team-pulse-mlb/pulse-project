-- 공개 모드 최근 플레이의 한국어 번역문과
-- 원문 변경 여부를 검증할 context hash를 저장한다.
-- 기존 플레이와 번역 실패 상태를 유지하기 위해 nullable로 추가한다.
ALTER TABLE plays
    ADD COLUMN text_ko TEXT,
    ADD COLUMN text_ko_context_hash TEXT;
