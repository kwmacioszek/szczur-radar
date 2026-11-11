-- Indexes on two foreign keys a run is read by (S-34 review F3): Postgres does not index a foreign key
-- itself, so fk_assessments_run and fk_posts_first_run had none, and a query by run scanned the whole
-- table, a cost that grows with the history, not with the run. assessments.run_id: the night's summary's
-- per-night aggregates (pipeline/NightSummary: AssessmentRepository.sumBySource and
-- countDistinctPostsBySource). posts.first_run_id: the posts a run stored first
-- (PostRepository.countFirstStoredBySource for the summary, findIdsFirstStoredBy for a range run's
-- selection). Plain CREATE INDEX in the migration's transaction (not CONCURRENTLY, which Flyway cannot run
-- in one): the tables are small, and the pre-deploy migrate runs before the new API starts.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

CREATE INDEX ix_assessments_run ON assessments (run_id);
CREATE INDEX ix_posts_first_run ON posts (first_run_id);
