<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Admin przywraca martwe posty (S-33) (restore-dead-posts)

- **Plan**: context/changes/restore-dead-posts/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-09
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 7 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Evidence

- **Diff `b57962b..HEAD`:** 27 files. Each one is in the plan, except `PipelineRunController.java`, where `MAX_POSTS` was made public (an adaptation recorded during implementation).
- **Plan drift (subagent):** every planned change is MATCH, and every test scenario named in the plan is present. "What We're NOT Doing" is respected:
  - no run is started;
  - no `assessments` row is deleted;
  - no migration;
  - no new error code and no change to `rules.json`;
  - no `pipeline_busy` refusal.
- **Safety and patterns (subagent):**
  - The SQL binds every value.
  - The route is covered by `hasRole(ADMIN)`, `ADMIN_ROUTES` and `OriginGuardFilter`.
  - The log line holds only UUIDs.
  - There is no race with `claim`/`record`: the row lock plus `WHERE stage = 'dead'` ensures this, with `ck_posts_assessing_owner` and `ck_posts_stage_attempts` in place.
  - The SPA has no double submit and no `v-html`.
  - The controller, the transition, the view, the mock and the tests follow their neighbours.
- **Gates re-run on 2026-11-09:**
  - `./mvnw -B verify` exit 0 (1063 unit, 345 IT, 0 failures), including `AdminRoleIT` and `AuthorizationCompletenessIT`.
  - Break-check 1.2: without the `restore` scenario, `PostColumnsIT.everyTransitionHasAScenario` fails ("without a scenario … [restore]"). The file was restored with `git checkout`.
  - `python3 tools/render_roadmap_svg.py` exit 0, and the SVG did not change (it is up to date).
  - `npm test` 547/547 and `npm run build` exit 0.
  - `gh pr checks 161`: 11/11 SUCCESS.
  - The `railway logs` grep for the restore line: 1 match.
  - 2.3 (playwright on the mock) was not re-run. Its evidence comes from the implementation: 7 states, both themes, 375 px.
- **Manual 2.4, 3.7, 3.8:** confirmed by the owner in the conversation on 2026-11-09 (`fb045c6`).

## Findings

### F1 — The "on hold" description says a stored post has no `lastAttempt`, which a restore makes false

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/FailedPostController.java:75-77; szczur-radar-web/src/views/AdminView.vue:466
- **Detail**: Both places say that a post which is `stored` and on hold "has no stored attempt, so no `lastAttempt`". A restored post keeps its earlier `assessments` rows. If it then reaches 3 technical failures, it is `stored` and on hold, and `lastAttempt` is its last failed attempt from before the restore. The SPA hides attempts for `stored`, so nothing wrong shows today; only the description is out of date.
- **Fix**: Reword both comments, e.g. "a restored post keeps its earlier attempts, so it may have a `lastAttempt`".
- **Decision**: FIXED — javadoc FailedPostController i komentarz w AdminView: przywrócony post zachowuje starszy lastAttempt

### F2 — The runbook's "N = M = the selection" is imprecise

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:2191
- **Detail**: This sentence was added while resolving F2 of the phase 3 review. M is the number of ids sent, which is only the checked dead posts, not the whole selection. N ≤ M, and N < M when a post was no longer dead (for example, another admin restored it first). That is not an error.
- **Fix**: Write that M is the checked dead posts and N ≤ M (N < M only when a post had stopped being dead), and that the ids are only the ones the admin chose.
- **Decision**: FIXED — deploy-plan.md:2191: M = wysłane (zaznaczone martwe), N ≤ M, N < M tylko gdy post przestał być martwy

### F3 — `PostTransitions.restore` with an empty collection builds an empty `IN ()`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/PostTransitions.java:190
- **Detail**: The method is public. An empty `postIds` expands to `IN ()`, which Postgres rejects as a syntax error. This was not checked at runtime. Today only `DeadPostController` calls it, and it refuses an empty list before the call.
- **Fix**: Add `if (postIds.isEmpty()) return 0;` (or state the precondition in the javadoc).
- **Decision**: FIXED — restore() zwraca 0 dla pustej kolekcji; przypadek w PostRepositoryIT

### F4 — A retry of a post that has died since the list loaded leaves the list stale

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: szczur-radar-web/src/views/AdminView.vue (`retry()`)
- **Detail**:
  - Suppose a post still shown as `failed` has become `dead` since the list loaded.
  - "Ponów zaznaczone" then gets 400 `post_dead` and shows the error message.
  - The list is not reloaded, so the post keeps its `failed` label, and "Przywróć martwe" cannot be used until the page is refreshed.
  - This is an existing gap that the new action makes visible. The plan excludes any change to retry other than the subset and the count ("What We're NOT Doing").
- **Fix**: A follow-up outside S-33: on `post_dead`, call `loadFailed()` or mention "Przywróć martwe" in the message.
- **Decision**: FIXED (follow-up) — zapisane w follow-ups/review-fixes.md; bez zmiany kodu w S-33

### F5 — The mock's restore route differs from the API

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/dev/mock-api.js:458-462
- **Detail**:
  - The header promises 400 for bad input, but the mock accepts a `null` id.
  - A restore sets `lastAttempt: null`, whereas the API keeps the history.
  - No screenshot state shows the difference today.
- **Fix**: Reject a list containing `null` (400 `invalid_request`), and keep `lastAttempt` on a restore.
- **Decision**: FIXED — atrapa odrzuca null w postIds (400 invalid_request) i zachowuje lastAttempt

### F6 — No test that `loadFailed` drops only ids no longer on the list

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-web/src/views/AdminView.vue:260; szczur-radar-web/src/views/AdminView.test.ts
- **Detail**: The plan changed `loadFailed` to drop from the selection only ids that are no longer on the list. Before, it also dropped dead posts. The restore test cannot tell this rule apart from `restore()` itself removing ids from the selection, so no test isolates it.
- **Fix**: Add a test: check a dead post and a failed one, reload the list without the failed one, and the dead post stays checked.
- **Decision**: FIXED — test „drops from the selection on a reload only the posts gone from the list”; czerwony przy starej regule

### F7 — `posts/api` depends on `pipeline` and on another controller's constant

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/DeadPostController.java:18-19
- **Detail**: This is the first `posts → pipeline` import, which creates a cycle with `pipeline → posts`. The plan accepted the cycle explicitly (`plan.md:29`). In addition, the 100-id limit is taken from `PipelineRunController.MAX_POSTS`, made public for this purpose, so one controller depends on another.
- **Fix**: Optionally move the limit to a shared constant in `config` (next to `PageLimits`), or accept it as is.
- **Decision**: FIXED — limit 100 w config/PostIdLimits.MAX_POSTS; PipelineRunController.MAX_POSTS znów package-private, DeadPostController bez importu z pipeline.api
