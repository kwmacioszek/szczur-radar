<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-42: rejestr reguł SPA, druga runda

- **Plan**: context/changes/spa-rules-registry-round-two/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-12
- **Verdict**: APPROVED
- **Findings**: 0 critical, 3 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria re-run during the review:

- `npm test`: 778 tests passed. `npm run build` (`vue-tsc`) is green.
- `docker build ./szczur-radar-web` is green.
- `./mvnw -B verify`: 1129 unit tests and 388 IT passed. It ran locally with placeholder `LLM_*` values, because the shell had none set. The first run failed on the missing settings, not on the code.
- `gh pr checks 170`: every job is green.

The deliberate breaks (1.3, 1.4, 2.4, 3.4, 3.5, 4.6) and the browser check 4.7 were not repeated. Their evidence is the phase commits.

## Findings

### F1 — Bell throws on a new SPA with an old API

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/runs.ts:49, :85 (via src/components/NotificationMenu.vue:167)
- **Detail**: `runTone` reads `run.attentionReasons.length` and `runStatusLabel` reads `run.attentionReasons.includes(…)`. Railway deploys web and API separately, and the API has a pre-deploy `migrate` and a slower start. Until the new API is up, the new SPA can get a `NotifiedRun` without `attentionReasons`. When an admin opens the bell in that window, rendering the list throws a `TypeError`. It heals itself: the bell reads the list again after a page change, when the tab becomes visible, and every 60 s. The reverse case (old SPA, new API) is harmless. The plan's Migration Notes ("SPA czyta pole dopiero w tej samej wersji") miss this window. `RunView.attentionReasons` had the same exposure when it was added.
- **Fix A ⭐ Recommended**: Accept the risk knowingly. Add one sentence to the plan's Migration Notes and the PR body: on deploy, the bell may fail to render until the API is up.
  - Strength: The types and code stay consistent with the rule "the API always sends every key". The exposure is admin-only, lasts minutes and heals itself, and the repo already accepted it for `RunView`.
  - Tradeoff: An admin who opens the bell during the deploy sees an empty or broken list.
  - Confidence: MED — the bell list renders only in the expanded panel, but the deploy order was not measured.
  - Blind spot: How Vue handles the render error in `AppHeader` (whether the rest of the header stays).
- **Fix B**: Read defensively on the notification path: `(run.attentionReasons ?? [])` in `runTone` and `runStatusLabel`.
  - Strength: Two short edits remove the deploy window.
  - Tradeoff: `??` on a field the type says is not optional is a precedent against the contract rule, and the `vue-tsc`/eslint style may read it as dead code.
  - Confidence: HIGH — the change is local.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A: accepted knowingly; the deploy window is described in the plan's Migration Notes and in the PR #170 body)

### F2 — `fetchRuns` default size is a literal 20

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/api/pipeline.ts:133
- **Detail**: `export function fetchRuns(page = 0, size = 20)` keeps the literal two lines below `RUNS_PAGE_SIZE = 20`. In the same change, `fetchPosts` and `fetchFailedPosts` got their constants as defaults (`FEED_PAGE_SIZE`, `FAILED_PAGE_SIZE`). The new guideline text says the SPA's page sizes are named constants that `rules.test.ts` keeps within the limit, and this default escapes that test. Behaviour does not change, because `AdminView` always passes `size`.
- **Fix**: `size = RUNS_PAGE_SIZE`.
- **Decision**: FIXED

### F3 — Orphan JSDoc after `PAGE_SIZE` removal

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/views/AdminView.vue:181
- **Detail**: Deleting `const PAGE_SIZE = 20` left its comment `/** Rows per page of "Pokaż starsze". */`. It now sits directly above the `REFRESH_MS` JSDoc: two doc comments in a row, and the first one describes nothing.
- **Fix**: Move the sentence into the JSDoc of `RUNS_PAGE_SIZE` in `pipeline.ts`, which says only "The `size` the list of runs asks for", and delete line 181.
- **Decision**: FIXED

### F4 — `ContractTypes` Javadoc promises more than `reachableEnums` checks

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractTypes.java:19-23, 70-85
- **Detail**: The class Javadoc says a new shape of type "turns both red together". `reachableEnums` sees an enum only as a component or as `List<E>`, and it silently skips one held in `Optional<E>`, `Set<E>`, `Map<K,E>`, `E[]` or behind a sealed interface. Only `ContractVariantsTest` turns red, and only when some contract file has that component non-null. The suite as a whole still catches it today, but `SpaRulesTest` alone proves less than the comment says.
- **Fix**: In the `reachableEnums` callback, throw `IllegalStateException` for a component that is neither `List<X>`, a record nor `leaf(...)`, so the Javadoc becomes true.
- **Decision**: FIXED (`reachableEnums` throws for an unknown component shape; new test `anEnumInAShapeTheWalkDoesNotKnowIsAnError` with `Set<Colour>`)

### F5 — The every-form self-test skips `.not.toBe`

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/contract/rules.test.ts:348-362
- **Detail**: The regex for `.detail).(not.)toBe/toEqual` supports `.not.`, but the "rozpoznaje każdą formę" test has no example of it. A broken `not.` branch would pass silently. A pre-existing quirk remains: once a file contains `.detail)`, every `case '…'` in that file counts as an error code. That fails loudly, not vacuously, so it is noted only.
- **Fix**: Add the example `expect(e.detail).not.toBe('<kod>')` to the every-form test.
- **Decision**: FIXED

### F6 — Dead parameter and unstable path in `SpaRulesTest.describe`

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/contract/SpaRulesTest.java:194-196
- **Detail**: Every call passes `describe(…, boolean inRegistry)` the value `false`. The path `(at X.y)` in the failure message depends on the iteration order of `ContractRegistry.SHAPES`, a `Map.copyOf` whose order is not fixed between JVM runs, so the reported path can differ from run to run. This affects diagnostics only.
- **Fix**: Remove the `inRegistry` parameter, and walk the `SHAPES` roots in sorted order (for example, by route) so the message is deterministic.
- **Decision**: FIXED (`describe` → `unregistered(reached, registered)`; roots walked in `TreeMap` order of the routes)
