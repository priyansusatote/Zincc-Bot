ALTER TABLE zincc.engine_connection
    ADD COLUMN primary_remaining double precision CHECK (primary_remaining BETWEEN 0 AND 100),
    ADD COLUMN primary_window_minutes integer CHECK (primary_window_minutes BETWEEN 1 AND 525600),
    ADD COLUMN allowance_observed_at timestamptz;
GRANT UPDATE(primary_remaining, primary_window_minutes, allowance_observed_at)
    ON zincc.engine_connection TO zincc_reader;

ALTER TABLE zincc.runner_job ADD COLUMN failure_reason text
    CHECK (failure_reason IN ('folder-changed','access-denied','local-pause'));
GRANT UPDATE(failure_reason) ON zincc.runner_job TO zincc_reader;
