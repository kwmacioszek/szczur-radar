-- A post's technical failures in a row (S-09): an error of our code while assessing it (a mapper, an SDK
-- client, a write) stores no assessment and uses up no attempt, but it is counted here, with its message.
-- At the third (posts/Post.MAX_TECHNICAL_FAILURES) the post is on hold: it stays stored/failed,
-- but the RETRY run no longer takes it; an admin's run of chosen posts still does. Every stored assessment
-- (ok or failed) resets both. No index: the predicates filter by stage first.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE posts
    ADD COLUMN technical_failures   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_technical_error TEXT    NULL,
    ADD CONSTRAINT ck_posts_technical_failures CHECK (technical_failures >= 0);
