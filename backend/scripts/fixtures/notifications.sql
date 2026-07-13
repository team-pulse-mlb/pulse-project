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

-- user_notifications는 로컬 dev DB에 아직 테이블이 없을 수 있어 존재할 때만 정리한다(확정 마이그레이션 V1).
DO $$
BEGIN
    IF to_regclass('public.user_notifications') IS NOT NULL THEN
        DELETE FROM user_notifications
        WHERE event_id IN (
            '00000000-0000-4000-8000-000000000001'::uuid,
            '00000000-0000-4000-8000-000000000002'::uuid,
            '00000000-0000-4000-8000-000000000003'::uuid,
            '00000000-0000-4000-8000-000000000004'::uuid
        );
    END IF;
END $$;
DELETE FROM notification_outbox
WHERE event_id IN (
    '00000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000003'::uuid,
    '00000000-0000-4000-8000-000000000004'::uuid
);
DELETE FROM notification_events
WHERE event_id IN (
    '00000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000003'::uuid,
    '00000000-0000-4000-8000-000000000004'::uuid
);

INSERT INTO notification_events (event_id, type, game_id, tags, occurred_at)
VALUES
    ('00000000-0000-4000-8000-000000000001', 'GAME_START', 8800000004,
     ARRAY['경기 시작'], now() - interval '50 minutes'),
    ('00000000-0000-4000-8000-000000000002', 'SURGE', 8800000004,
     ARRAY['후반 긴장 구간', '접전 흐름'], now() - interval '30 minutes'),
    ('00000000-0000-4000-8000-000000000003', 'SURGE', 8800000005,
     ARRAY['득점권 승부'], now() - interval '10 minutes'),
    ('00000000-0000-4000-8000-000000000004', 'GAME_START', 8800000006,
     ARRAY['경기 시작'], now() - interval '1 day');

INSERT INTO notification_outbox (
    outbox_id, event_id, event_type, game_id, message, latest_tag, occurred_at,
    status, attempt_count, next_attempt_at, created_at, published_at, last_error
)
VALUES
    ('10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001',
     'GAME_START', 8800000004, '관심 팀 경기가 시작했습니다.', '경기 시작',
     now() - interval '50 minutes', 'PUBLISHED', 0, now() - interval '50 minutes',
     now() - interval '50 minutes', now() - interval '49 minutes', NULL),
    ('10000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000002',
     'SURGE', 8800000004, '지금 볼 만한 경기가 있습니다.', '후반 긴장 구간',
     now() - interval '30 minutes', 'PUBLISHED', 1, now() - interval '30 minutes',
     now() - interval '30 minutes', now() - interval '28 minutes', NULL),
    ('10000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000003',
     'SURGE', 8800000005, '득점권 승부가 이어지고 있습니다.', '득점권 승부',
     now() - interval '10 minutes', 'PENDING', 0, now() + interval '1 day',
     now() - interval '10 minutes', NULL, NULL),
    ('10000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000004',
     'GAME_START', 8800000006, '관심 팀 경기 알림 기록입니다.', '경기 시작',
     now() - interval '1 day', 'PENDING', 2, now() + interval '1 day',
     now() - interval '1 day', NULL, '개발용 재시도 상태');

-- user_notifications는 로컬 dev DB에 아직 테이블이 없을 수 있어 존재할 때만 채운다(확정 마이그레이션 V1).
DO $$
BEGIN
    IF to_regclass('public.user_notifications') IS NOT NULL THEN
        INSERT INTO user_notifications (event_id, user_id, message, read_at, created_at)
        VALUES
            ('00000000-0000-4000-8000-000000000001', 8800000001,
             '관심 팀 경기가 시작했습니다.', now() - interval '45 minutes', now() - interval '50 minutes'),
            ('00000000-0000-4000-8000-000000000002', 8800000001,
             '후반 긴장 구간이 시작됐습니다.', now() - interval '20 minutes', now() - interval '30 minutes'),
            ('00000000-0000-4000-8000-000000000003', 8800000001,
             '득점권 승부가 이어지고 있습니다.', NULL, now() - interval '10 minutes'),
            ('00000000-0000-4000-8000-000000000004', 8800000001,
             '어제 경기 시작 알림입니다.', NULL, now() - interval '1 day');
    END IF;
END $$;

COMMIT;
