\set ON_ERROR_STOP on

-- 발표용 데모 시뮬레이터 재구성(WORK_PLAN 8, Phase 2) 이후 이 파일은 의도적으로 비어 있다.
--
-- 이전에는 여기서 카드 화면을 흉내 내려고 합성 경기 6종과 고정 copy·final_headline·
-- watch_scores를 직접 주입했다(정적). 이제 정적 입력은 games+plays 픽스처(load-fixtures.sql)
-- 뿐이고, 예정·진행·종료 카드와 점수·문구·알림은 시뮬레이션 poller → scorer → SSE 경로가
-- 라이브로 생성한다. 다중 경기 연출은 pulse.simulation.games 설정으로 켠다(application.yml 예시 참고).
--
-- 합성 경기는 플레이가 없어 시뮬레이터 원본이 될 수 없으므로 남기지 않는다. 원본 경기는
-- load-fixtures.sql이 과거 시각으로 적재해 슬레이트에 직접 노출되지 않게 한다.

BEGIN;
COMMIT;
