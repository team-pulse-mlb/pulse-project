-- 중요 플레이 번역 저장 후 REVEALED FINAL_HEADLINE을
-- 한 경기당 한 번만 자동 재생성하도록 시도 시각을 저장한다.
--
-- Redis 및 scorer 프로세스 재시작과 무관하게
-- 중복 OpenAI 호출을 방지하기 위한 영속 상태다.
ALTER TABLE games
    ADD COLUMN final_headline_revealed_regeneration_attempted_at
        TIMESTAMPTZ;
