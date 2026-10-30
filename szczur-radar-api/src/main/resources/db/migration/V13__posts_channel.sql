-- The channel a post came from (S-27), in the source's own spelling: Wykop's tag without '#' (gielda),
-- Reddit's subreddit name as its payload writes it (e.g. ValueInvesting). The same value as the
-- assessment input's channels[0], kept on the post itself so the feed and measurements need neither
-- assessments.input nor the raw payload (sources/SourcePost.channel, posts/Post.channel). An open set
-- (any subreddit), so no value list: only an empty channel is rejected.
-- Backfill: Wykop's only channel in the code is gielda (sources/wykop/WykopPosts.TAG); no environment
-- has a reddit post yet.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE posts ADD COLUMN channel TEXT;
UPDATE posts SET channel = 'gielda' WHERE source = 'wykop';
ALTER TABLE posts ALTER COLUMN channel SET NOT NULL;
ALTER TABLE posts ADD CONSTRAINT ck_posts_channel CHECK (channel <> '');
