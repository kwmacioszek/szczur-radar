-- A running run's progress and why an interrupted run stopped (S-06).
-- last_activity_at moves with every change of a running run: its start, a step starting or ending, a
-- post's result counted. llm_retries are the HTTP attempts beyond the first of each assessment's call,
-- flex_fallbacks the calls whose flex tier fell back to the default one; both are counted per post, like
-- assessed_ok. deployment_id is the Railway deployment (RAILWAY_DEPLOYMENT_ID) of the instance that queued
-- the run: start-up recovery compares it with its own to tell a new deployment (deploy) from a restart.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE pipeline_runs
    ADD COLUMN last_activity_at   TIMESTAMPTZ NULL,
    ADD COLUMN llm_retries        INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN flex_fallbacks     INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN deployment_id      TEXT        NULL,
    ADD COLUMN interruption_cause TEXT        NULL,
    ADD CONSTRAINT ck_pipeline_runs_interruption_cause
        CHECK (interruption_cause IN ('deploy', 'restart', 'shutdown')),
    ADD CONSTRAINT ck_pipeline_runs_interruption_status
        CHECK (interruption_cause IS NULL OR status = 'interrupted');

-- An interruption closes the steps its run left open.
ALTER TABLE pipeline_run_steps DROP CONSTRAINT ck_pipeline_run_steps_status;
ALTER TABLE pipeline_run_steps ADD CONSTRAINT ck_pipeline_run_steps_status
    CHECK (status IN ('running', 'succeeded', 'failed', 'interrupted'));

-- The posts a run holds in assessment now (its progress "in flight").
CREATE INDEX ix_posts_assessing_run ON posts (assessing_run_id) WHERE stage = 'assessing';

-- Steps left running by runs that ended before this migration (e.g. a run interrupted on 2026-10-09)
-- end with their run. Runs still queued or running — e.g. in the old instance during the pre-deploy
-- migrate — keep their steps.
UPDATE pipeline_run_steps s
SET status = 'interrupted', finished_at = r.finished_at
FROM pipeline_runs r
WHERE r.id = s.run_id
  AND s.status = 'running'
  AND r.status NOT IN ('queued', 'running');
