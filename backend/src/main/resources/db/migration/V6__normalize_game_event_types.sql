-- 초기 구현에서 대문자로 저장한 공개 전용 이벤트를 API 계약의 소문자 식별자로 통일한다.
-- 동일 키의 소문자 행이 이미 있으면 대문자 중복 행을 먼저 제거한다.

DELETE FROM game_events legacy
USING game_events normalized
WHERE legacy.game_id = normalized.game_id
  AND legacy.source_type = normalized.source_type
  AND legacy.source_ref = normalized.source_ref
  AND legacy.event_type IN ('SCORING_PLAY', 'LEAD_CHANGE')
  AND normalized.event_type = LOWER(legacy.event_type);

UPDATE game_events
SET event_type = LOWER(event_type)
WHERE event_type IN ('SCORING_PLAY', 'LEAD_CHANGE');
