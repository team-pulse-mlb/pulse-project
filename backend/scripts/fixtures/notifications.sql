\set ON_ERROR_STOP on

BEGIN;

-- 공개 저장소에 둘 수 있는 개발용 더미 bcrypt 해시다. 실제 계정에 사용하지 않는다.
INSERT INTO users (
    user_id, email, password_hash, role, status, created_at, updated_at
)
VALUES (
    8800000001,
    'fixture@pulse.local',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'USER',
    'ACTIVE',
    now(),
    now()
)
ON CONFLICT (user_id) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    deleted_at = NULL,
    updated_at = now();

INSERT INTO user_settings (
    user_id, notify_game_start, notify_surge_enabled, recommend_switch_enabled,
    created_at, updated_at
)
VALUES (8800000001, true, true, true, now(), now())
ON CONFLICT (user_id) DO UPDATE SET
    notify_game_start = EXCLUDED.notify_game_start,
    notify_surge_enabled = EXCLUDED.notify_surge_enabled,
    recommend_switch_enabled = EXCLUDED.recommend_switch_enabled,
    updated_at = now();

INSERT INTO user_favorite_teams (user_id, team_id, created_at)
VALUES
    (8800000001, 1, now()),
    (8800000001, 14, now()),
    (8800000001, 16, now())
ON CONFLICT (user_id, team_id) DO NOTHING;

-- user_favorite_players는 로컬 dev DB(JPA ddl-auto)에는 아직 엔티티가 없어 테이블이 없을 수 있다.
-- 확정 마이그레이션(V10)에는 있는 테이블이라, 존재할 때만 채운다.
DO $$
BEGIN
    IF to_regclass('public.user_favorite_players') IS NOT NULL THEN
        INSERT INTO user_favorite_players (user_id, player_id, created_at)
        VALUES
            (8800000001, 4667266, now()),
            (8800000001, 5460, now())
        ON CONFLICT (user_id, player_id) DO NOTHING;
    END IF;
END $$;

-- 고정 알림 기록(notification_events·outbox·user_notifications)은 더 이상 주입하지 않는다.
-- 알림은 시뮬레이션 진행 중 notify.events → 알림 fan-out 경로가 라이브로 생성한다.
-- 이 데모 계정은 로그인해 라이브 알림을 받기 위한 것이며, 관심 팀은 위 즐겨찾기 기준으로
-- 매칭된다(연출할 경기의 팀과 겹치도록 필요 시 조정한다).

COMMIT;
