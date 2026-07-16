-- 운영 DB에 남아 있는 이전 refresh_tokens 구조를
-- 현재 RefreshToken 엔티티 구조와 일치시킨다.
--
-- 이전 운영 구조:
--   PK 컬럼: id
--   status 컬럼 없음
--
-- 현재 애플리케이션 구조:
--   PK 컬럼: refresh_token_id
--   status: ACTIVE / REVOKED / REUSED

-- 1. 기존 PK 컬럼명이 id인 경우에만 refresh_token_id로 변경한다.
-- 이미 refresh_token_id 구조인 환경에서는 아무 작업도 하지 않는다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'refresh_tokens'
          AND column_name = 'id'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'refresh_tokens'
          AND column_name = 'refresh_token_id'
    ) THEN
ALTER TABLE public.refresh_tokens
    RENAME COLUMN id TO refresh_token_id;
END IF;
END
$$;

-- 2. Refresh Token 상태 컬럼을 추가한다.
-- 새로 생성되는 토큰의 기본 상태는 ACTIVE다.
ALTER TABLE public.refresh_tokens
    ADD COLUMN IF NOT EXISTS status TEXT;

-- 3. 기존 데이터가 있는 다른 환경까지 고려하여 상태를 채운다.
-- 폐기 시각이 없으면 ACTIVE, 있으면 REVOKED로 간주한다.
UPDATE public.refresh_tokens
SET status = CASE
                 WHEN revoked_at IS NULL THEN 'ACTIVE'
                 ELSE 'REVOKED'
    END
WHERE status IS NULL;

-- 4. 애플리케이션 엔티티 정의와 동일하게
-- 기본값 및 NOT NULL 조건을 적용한다.
ALTER TABLE public.refresh_tokens
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
ALTER COLUMN status SET NOT NULL;

-- 5. 애플리케이션에서 사용하는 상태값만 저장되도록 제한한다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_refresh_tokens_status'
          AND conrelid = 'public.refresh_tokens'::regclass
    ) THEN
ALTER TABLE public.refresh_tokens
    ADD CONSTRAINT ck_refresh_tokens_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'REUSED'));
END IF;
END
$$;