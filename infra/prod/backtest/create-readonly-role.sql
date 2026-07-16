-- 운영 RDS에서 pulse_admin으로 한 번 실행한다.
-- \password는 입력값을 화면·명령 이력에 남기지 않는다.

SELECT 'CREATE ROLE pulse_backtest_ro'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pulse_backtest_ro')
\gexec

ALTER ROLE pulse_backtest_ro LOGIN;
ALTER ROLE pulse_backtest_ro SET default_transaction_read_only = on;
ALTER ROLE pulse_backtest_ro SET statement_timeout = '30min';
ALTER ROLE pulse_backtest_ro SET idle_in_transaction_session_timeout = '1min';

GRANT CONNECT ON DATABASE pulse TO pulse_backtest_ro;
GRANT USAGE ON SCHEMA public TO pulse_backtest_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO pulse_backtest_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO pulse_backtest_ro;

\password pulse_backtest_ro
