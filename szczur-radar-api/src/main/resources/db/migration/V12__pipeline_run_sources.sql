-- The result of each source of a range run (S-20), so one failing or silent source does not decide the
-- run of the others. A row's status:
--   succeeded — the source gave at least one post;
--   empty     — the source answered, but gave no post (no element in the range, or only skipped ones);
--   failed    — the source threw; error is '<SimpleName>: <message>'.
-- fetched / skipped are the source's own counts (added to the run's sums by the same write). A row is
-- written only by pipeline/state/RunRecorder.source, in one conditional statement with the run's
-- counters and activity, and only while the run is running: an interrupted run gets no row. The run
-- keeps sources_failed / sources_empty, so whether it needs attention stays a rule of its own row.
-- Registering a new source (sources/CLAUDE.md, step 1) adds its value to ck_pipeline_run_sources_source
-- too. Reddit is registered here (no source code yet: S-27).
-- History: each range run gets a row per source it named from its last fetch step — a step succeeded
-- with posts → succeeded, without (runs before S-08 ended an empty day succeeded) → empty; failed with
-- "gave no post to store" (S-08) → empty; any other failure → failed with the step's error; a step still
-- running or interrupted, or none → no row. Before this migration every run named one source, so the
-- run's sums are that source's counts.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE pipeline_runs
    ADD COLUMN sources_failed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN sources_empty  INTEGER NOT NULL DEFAULT 0;

-- Registering Reddit (sources/Source.REDDIT).
ALTER TABLE posts DROP CONSTRAINT ck_posts_source;
ALTER TABLE posts ADD CONSTRAINT ck_posts_source CHECK (source IN ('wykop', 'reddit'));
ALTER TABLE raw_payloads DROP CONSTRAINT ck_raw_payloads_source;
ALTER TABLE raw_payloads ADD CONSTRAINT ck_raw_payloads_source CHECK (source IN ('wykop', 'reddit'));
ALTER TABLE pipeline_runs DROP CONSTRAINT ck_pipeline_runs_sources;
ALTER TABLE pipeline_runs ADD CONSTRAINT ck_pipeline_runs_sources CHECK (
    (kind = 'range') = (sources IS NOT NULL)
    AND (sources IS NULL OR (cardinality(sources) > 0 AND sources <@ ARRAY['wykop', 'reddit']::TEXT[])));

CREATE TABLE pipeline_run_sources (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    run_id      UUID        NOT NULL,
    source      TEXT        NOT NULL,
    status      TEXT        NOT NULL,
    fetched     INTEGER     NOT NULL,
    skipped     INTEGER     NOT NULL,
    error       TEXT        NULL,
    finished_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pipeline_run_sources_pkey PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_run_sources_run FOREIGN KEY (run_id) REFERENCES pipeline_runs (id),
    CONSTRAINT ck_pipeline_run_sources_source CHECK (source IN ('wykop', 'reddit')),
    CONSTRAINT ck_pipeline_run_sources_status CHECK (status IN ('succeeded', 'empty', 'failed')),
    CONSTRAINT ck_pipeline_run_sources_counts CHECK (fetched >= 0 AND skipped >= 0 AND skipped <= fetched),
    CONSTRAINT ck_pipeline_run_sources_error CHECK ((status = 'failed') = (error IS NOT NULL)),
    CONSTRAINT uq_pipeline_run_sources_run_source UNIQUE (run_id, source)
);

WITH outcome AS (
    SELECT r.id AS run_id,
           s.source,
           CASE
               WHEN f.status = 'succeeded' AND r.fetched > r.skipped THEN 'succeeded'
               WHEN f.status = 'succeeded' THEN 'empty'
               WHEN f.error LIKE '%gave no post to store%' THEN 'empty'
               ELSE 'failed'
           END AS status,
           r.fetched,
           r.skipped,
           f.error,
           COALESCE(f.finished_at, r.finished_at, r.created_at) AS finished_at
    FROM pipeline_runs r
    CROSS JOIN LATERAL unnest(r.sources) AS s (source)
    CROSS JOIN LATERAL (
        SELECT st.status, st.error, st.finished_at
        FROM pipeline_run_steps st
        WHERE st.run_id = r.id AND st.step = 'fetch'
        ORDER BY st.started_at DESC, st.id DESC
        LIMIT 1) f
    WHERE r.kind = 'range'
      AND f.status IN ('succeeded', 'failed'))
INSERT INTO pipeline_run_sources (run_id, source, status, fetched, skipped, error, finished_at)
SELECT run_id, source, status, fetched, skipped,
       CASE WHEN status = 'failed' THEN COALESCE(error, 'unknown (before V12)') END,
       finished_at
FROM outcome;

UPDATE pipeline_runs r
SET sources_failed = c.failed,
    sources_empty  = c.empty
FROM (SELECT run_id,
             count(*) FILTER (WHERE status = 'failed') AS failed,
             count(*) FILTER (WHERE status = 'empty')  AS empty
      FROM pipeline_run_sources
      GROUP BY run_id) c
WHERE c.run_id = r.id;
