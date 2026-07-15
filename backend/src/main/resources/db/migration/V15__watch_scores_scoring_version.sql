-- 기존 행의 적용 버전은 신뢰할 수 있게 역산할 수 없으므로 null로 유지한다.
-- 이 마이그레이션 이후 scorer가 새로 저장하는 행에는 적용한 scoring.yml version을 기록한다.
ALTER TABLE watch_scores
    ADD COLUMN scoring_version INTEGER;
