-- Posts carry the shared model's item type (contract ItemType: post | link | comment) instead of the
-- source's own resource names (Wykop: entry | link), so a new source maps onto it and the persistence
-- layer stays unchanged (PRD § Non-Functional Requirements; review of phase 2, F3). The source's own
-- resource type stays in the raw payload. Wykop maps entry → post, link → link.
-- Also: a run that is not a range run carries no half of a range (F4).
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE posts ADD COLUMN item_type TEXT;
UPDATE posts SET item_type = CASE resource WHEN 'entry' THEN 'post' WHEN 'link' THEN 'link' END;
ALTER TABLE posts ALTER COLUMN item_type SET NOT NULL;

ALTER TABLE posts DROP CONSTRAINT uq_posts_source_item;
ALTER TABLE posts DROP CONSTRAINT ck_posts_resource;
ALTER TABLE posts DROP COLUMN resource;
ALTER TABLE posts ADD CONSTRAINT ck_posts_item_type CHECK (item_type IN ('post', 'link', 'comment'));
ALTER TABLE posts ADD CONSTRAINT uq_posts_source_item UNIQUE (source, item_type, source_item_id);

ALTER TABLE pipeline_runs DROP CONSTRAINT ck_pipeline_runs_range;
ALTER TABLE pipeline_runs ADD CONSTRAINT ck_pipeline_runs_range CHECK (
    (kind = 'range') = (range_from IS NOT NULL)
    AND (range_from IS NULL) = (range_to IS NULL)
    AND (range_from IS NULL OR range_from <= range_to));
