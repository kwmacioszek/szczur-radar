# Review follow-ups: restore-dead-posts

From `reviews/impl-review.md` (full review, 2026-11-09).

## F4: retrying a post that died since the list loaded leaves the list stale

- **Where:** `szczur-radar-web/src/views/AdminView.vue`, `retry()`.
- **What happens:**
  1. The failed list still shows a post as `failed`.
  2. The post becomes `dead` before the admin acts.
  3. "Ponów zaznaczone" gets 400 `post_dead` and shows the error.
  4. The list is not read again, so the post keeps its `failed` label.
  5. "Przywróć martwe" does not count it until the page is refreshed ("Odśwież").
- **Why not in S-33:** this gap existed before S-33. The plan excludes changing retry beyond the subset and the count ("What We're NOT Doing").
- **Fix (one of):**
  - on a `post_dead` error, call `loadFailed()`, so the post shows as dead and can be restored at once;
  - or add a pointer to "Przywróć martwe" to the `post_dead` message.
- **Done when:** a Vitest test where retry answers 400 `post_dead` shows the post as "martwy" without "Odśwież" (or shows the message with the pointer).
