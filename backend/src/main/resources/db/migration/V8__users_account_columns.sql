-- DB_SCHEMA.md B-1 확정에 따라 users 계정 컬럼을 정렬한다.
-- 로컬 DB는 V1으로 생성되어 role이 이미 있고, 운영 DB는 V1이 baseline으로 기록된
-- 구형 구조라 role부터 없다. 두 환경 모두 통과하도록 IF NOT EXISTS를 사용한다.
-- PK(user_id)와 UNIQUE(email)은 두 환경 모두 이미 존재해 여기서 다루지 않는다.

ALTER TABLE users ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
