# fixtures/reddit — Arctic Shift answers

Real answers of Arctic Shift's `GET /api/posts/search` (`https://arctic-shift.photon-reddit.com`, the
source of `sources/reddit`), anonymized. Tests that need a case the recording does not have (more
pages, a missing field, an error) change these answers minimally in the test and say what changed;
error bodies are short synthetic JSON in the API's shape (`{"data":null,"error":"…"}`).

## Recording

Recorded on 2026-10-30 at about 09:07 UTC with `curl`, headers
`User-Agent: szczur-radar (+https://github.com/kwmacioszek/szczur-radar)` and
`Accept: application/json`, no key. Both answered HTTP 200 with `x-ratelimit-reset` (27–30 s) and
`x-ratelimit-reset-at` headers. The range is the day 2026-10-27 in Warsaw time (CET, UTC+1 after
the change of 2026-10-25) as `RedditRepository` asks for it: the run's range
`2026-10-26T23:00:00Z` (`1793055600`) … `2026-10-27T22:59:59Z` (`1793141999`), one second wider on
each side because Arctic Shift's `after` and `before` are exclusive (`after=1793055599`,
`before=1793142000`). No `fields` parameter: every post is the full Reddit object.

| File | Address |
| --- | --- |
| `search-posts-inwestowanie.json` | `https://arctic-shift.photon-reddit.com/api/posts/search?subreddit=inwestowanie&after=1793055599&before=1793142000&sort=asc&limit=25` |
| `search-posts-stocks.json` | `https://arctic-shift.photon-reddit.com/api/posts/search?subreddit=stocks&after=1793055599&before=1793142000&sort=asc&limit=25` |

## Contents

- `search-posts-inwestowanie.json`: the whole day of r/inwestowanie (14 posts, shorter than the
  limit), Polish, none removed. 9 self-posts (`is_self: true`) and 5 link posts, of which 2 have a
  non-empty `selftext` (`1xgmw66`, `1xgrbst`) and 3 none (`1xgto1s`, `1xgxe5i`, `1xh44sm`).
  `1xh44sm` is a crosspost from its author's profile (`crosspost_parent_list`) whose `_meta` says
  `was_deleted_later` with `removed_by_category: null` — still a live post for the repository.
  `1xh5lkk` is spam linking a Reddit profile in its `selftext`; the profile's nick (not an author of
  the file) was replaced by hand with `uzytkownik15` after the command below, as mentions are in the
  Wykop fixtures (`@uzytkownikN`). The rest of its text is as recorded.
- `search-posts-stocks.json`: the first 25 posts of the day of r/stocks (a full page of
  `limit=25`; the day has more), all self-posts: 19 removed (`removed_by_category`: 16 `moderator`,
  3 `reddit`; `selftext` `[removed]`) and 6 visible, one of them the daily discussion thread.

`created_utc` is an integer (seconds, UTC) in every post.

## Anonymization

Kept as recorded (public identifiers and content): `id`, `name`, `permalink`, `url`, `subreddit`,
`created_utc`, `title`, `selftext`, `removed_by_category`, `_meta` and every other field not listed
below. Changed:

- `author` → `uzytkownikN`, one number per nick within a file in order of first appearance (also in
  `crosspost_parent_list`); `[deleted]` stays;
- `author_fullname` → `t2_uzytkownikN` of the post's author;
- `profile_img` and `thumbnail` holding a URL (avatars, previews) → `<url>` (`self`, `default` stay);
- `media`, `secure_media`, `preview`, `media_metadata`, `gallery_data` and a non-empty `media_embed` /
  `secure_media_embed` → `<media>` (a `null` stays `null`);
- any other string holding a nick (the crosspost's profile subreddit `u_<nick>`, `u/<nick>`, its
  `/user/<nick>/…` permalink) → the nick's alias; `title` and `selftext` are post content and stay.

Checked after the run: no author's nick is left anywhere in either file, and `id`, `permalink`,
`url`, `subreddit`, `created_utc`, `title`, `selftext`, `removed_by_category`, `_meta` and `is_self`
equal the recording for every post.

Command (jq 1.8), run on each recorded answer:

```bash
jq -f anonymize.jq answer.json > search-posts-<subreddit>.json
```

`anonymize.jq`:

```jq
# Anonymizes an Arctic Shift answer {"data":[post, ...]} (szczur-radar, fixtures/reddit).
# Every nick (author, at any depth) gets a stable alias per file: uzytkownik1, 2, ... in order of
# first appearance; "[deleted]" stays. author_fullname -> t2_<alias>; avatar and thumbnail URLs ->
# "<url>"; media, secure_media, preview, media_metadata, gallery_data and non-empty *_media_embed ->
# "<media>"; any other string holding a nick (e.g. a crosspost from the author's profile, u_<nick>)
# gets the alias. title and selftext (post content) and everything else stay as recorded.
def nicks: [.. | objects | .author? | strings | select(. != "[deleted]")] | reduce .[] as $n ([]; if index([$n]) then . else . + [$n] end);
(nicks | to_entries | map({nick: .value, alias: ("uzytkownik" + (.key + 1 | tostring))})) as $aliases
| def alias($n): if $n == "[deleted]" then $n else ($aliases[] | select(.nick == $n) | .alias) end;
  def url: if type == "string" and test("^https?://") then "<url>" else . end;
  def post:
    (if (.author | type) == "string" then .author as $a | .author = alias($a)
        | if (has("author_fullname") and $a != "[deleted]") then .author_fullname = "t2_" + alias($a) else . end
     else . end)
    | (if has("profile_img") then .profile_img |= url else . end)
    | (if has("thumbnail") then .thumbnail |= url else . end)
    | reduce ("media", "secure_media", "preview", "media_metadata", "gallery_data") as $k (.;
        if (.[$k] // null) != null then .[$k] = "<media>" else . end)
    | reduce ("media_embed", "secure_media_embed") as $k (.;
        if ((.[$k] // {}) | length) > 0 then .[$k] = "<media>" else . end)
    | (if (.crosspost_parent_list | type) == "array" then .crosspost_parent_list |= map(post) else . end);
  def scrub:
    if type == "object" then with_entries(if .key == "title" or .key == "selftext" then . else .value |= scrub end)
    elif type == "array" then map(scrub)
    elif type == "string" then reduce $aliases[] as $p (.; split($p.nick) | join($p.alias))
    else . end;
  .data |= map(post | scrub)
```
