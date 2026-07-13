-- 종료 헤드라인을 보호 모드와 공개 모드로 분리한다.
-- 로컬 DB는 V1으로 final_headline이 생성되고, 운영 DB는 구형 구조가 V1 baseline으로
-- 기록될 수 있으므로 정보 스키마를 확인해 멱등하게 처리한다.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'games'
          AND column_name = 'final_headline'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'games'
          AND column_name = 'final_headline_protected'
    ) THEN
        ALTER TABLE games RENAME COLUMN final_headline TO final_headline_protected;
    ELSIF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'games'
          AND column_name = 'final_headline'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'games'
          AND column_name = 'final_headline_protected'
    ) THEN
        UPDATE games
        SET final_headline_protected = final_headline
        WHERE final_headline_protected IS NULL
          AND final_headline IS NOT NULL;

        ALTER TABLE games DROP COLUMN final_headline;
    END IF;
END $$;

ALTER TABLE games ADD COLUMN IF NOT EXISTS final_headline_protected TEXT;
ALTER TABLE games ADD COLUMN IF NOT EXISTS final_headline_revealed TEXT;
