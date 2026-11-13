-- The language of a post (S-36): the language of its source (sources/Source.language(), posts/Post.language),
-- not one detected from its text — an English entry on Wykop is 'pl'. The measure of theses in another
-- language than their post (posts/AssessmentQuality, S-36) reads it from the post, so detecting the language
-- later changes only how this column is written.
-- Backfill: every source has one language — wykop 'pl', reddit 'en'.
-- No default: the application sets it on every new post. An API older than this migration does not, so a post it
-- stores between the pre-deploy migrate and the new version's start is refused (counted as skipped, S-38); a
-- revert needs a new migration dropping NOT NULL.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE posts ADD COLUMN language TEXT;
UPDATE posts SET language = CASE source WHEN 'wykop' THEN 'pl' WHEN 'reddit' THEN 'en' END;
ALTER TABLE posts ALTER COLUMN language SET NOT NULL;
ALTER TABLE posts ADD CONSTRAINT ck_posts_language CHECK (language IN ('pl', 'en'));
