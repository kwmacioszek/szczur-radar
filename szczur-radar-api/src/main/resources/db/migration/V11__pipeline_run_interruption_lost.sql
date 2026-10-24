-- A run nobody executes any more (S-11): its instance was lost without closing it (a deploy overlap, a
-- run queued on the old instance after the new one's recovery, a failed start-up recovery). When no
-- queued or running run showed activity for PIPELINE_STALL_TIMEOUT and none runs on the instance asked
-- to start a run, that instance marks them all interrupted with the cause lost
-- (pipeline/run/InterruptionCause.LOST). Only the cause's value set grows; ck_pipeline_runs_interruption_status
-- (a cause only on an interrupted run) stays as V8 made it.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE pipeline_runs DROP CONSTRAINT ck_pipeline_runs_interruption_cause;
ALTER TABLE pipeline_runs ADD CONSTRAINT ck_pipeline_runs_interruption_cause
    CHECK (interruption_cause IN ('deploy', 'restart', 'shutdown', 'lost'));
