-- A range run names the sources it fetches (the admin's list, by default every configured source), so
-- the run and its history say what was read. Registering a new source adds it to this CHECK set too.
-- Range runs from before this migration fetched Wykop.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE pipeline_runs ADD COLUMN sources TEXT[] NULL;
UPDATE pipeline_runs SET sources = ARRAY['wykop'] WHERE kind = 'range';
ALTER TABLE pipeline_runs ADD CONSTRAINT ck_pipeline_runs_sources CHECK (
    (kind = 'range') = (sources IS NOT NULL)
    AND (sources IS NULL OR (cardinality(sources) > 0 AND sources <@ ARRAY['wykop']::TEXT[])));
