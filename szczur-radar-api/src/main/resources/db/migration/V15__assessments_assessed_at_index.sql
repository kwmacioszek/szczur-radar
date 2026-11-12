-- An index on assessments.assessed_at alone (S-35): the quality window of the assessments
-- (posts/AssessmentQuality: AssessmentRepository.countModelAttemptsBySourceAndClass) counts the attempts
-- of a range of assessed_at, every night for the night's summary and at every visit of /admin; without it
-- the query scans the whole, ever-growing table (ix_assessments_post_latest leads with post_id). Plain
-- CREATE INDEX in the migration's transaction (not CONCURRENTLY, which Flyway cannot run in one): the table
-- is small, and the pre-deploy migrate runs before the new API starts.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

CREATE INDEX ix_assessments_assessed_at ON assessments (assessed_at);
