<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-41 — kontrakt SPA ↔ API, druga runda — kształty

- **Plan**: `context/changes/api-contract-shapes-round-two/plan.md`
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-09
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Evidence

- **Drift check**:
  - every planned change is MATCH;
  - no production code changed (`git diff origin/main...HEAD --stat -- szczur-radar-api/src/main` is empty);
  - `dev/mock-api.js` and `KitchenSinkView.vue` are unchanged;
  - roadmap.md, roadmap.svg and the renderer only set S-41 to in-progress.
- **Success criteria re-run on 2026-11-09 (HEAD aae1143)**:
  - `./mvnw -B verify` is green: 1065 unit tests and 347 ITs. This run had `llm.env` loaded; a first run without it failed for environment reasons only, because the context would not start without `LLM_*`.
  - `npm test` is green: 29 files, 552 tests.
  - `npm run build` is green.
  - `docker build ./szczur-radar-web` is green.
- **Not re-run**: break-checks 1.5, 2.6, 3.5, 3.6, 3.7 and the 2.7 mock check. Their results are in the phase commits 0176413, 17ba4ba and cdee07c.
- **Manual 2.8**: the owner confirmed it on 2026-11-09 (aae1143).

## Findings

### F1 — Runs-page cleanup races with a previous test's late notification

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java:495-499 (with `awaitIdle` at :143-152)
- **Detail**:
  - `aRunsPageMatchesTheSharedContract` deletes six tables as separate auto-commit statements, in this order: `notifications`, …, `pipeline_runs`.
  - `awaitIdle` only waits until no run is `queued`/`running`.
  - `PipelineRunService.execute` (`:380-386`) commits the terminal status in `close()` first. Only after that does `notifications.runEnded(runId)` insert the admins' notifications on the `pipeline-run` thread.
  - Tests in this class start real runs that end `failed`/`partial` and therefore notify.
  - So a notification can land between `DELETE FROM notifications` and `DELETE FROM pipeline_runs`, and the second DELETE then fails on `fk_notifications_run`. The result is an intermittent flake that depends on test order.
  - `pipeline/CLAUDE.md` names this gap ("finished() alone returns too early"), and `PipelineRunIT.settle()` waits for `ownRuns()` for this reason.
  - The test was green in every run so far.
- **Fix A ⭐ Recommended**: make `awaitIdle` also wait for the queue thread to finish.
  - Approach: a small test-side accessor in package `pl.szczurradar.api.pipeline` exposes `ownRuns()`, which is package-private.
  - Strength: it closes the window for every test in the class at its source, the same way `PipelineRunIT.settle()` does.
  - Tradeoff: a new test helper class (about 10 lines).
  - Confidence: HIGH — `own.remove(runId)` runs after `runEnded` (`PipelineRunService.java:389`).
  - Blind spot: none significant.
- **Fix B**: replace the six DELETEs with one `TRUNCATE … ` in a single statement, as `DbIsolationExtension` does.
  - Strength: a one-line change; TRUNCATE waits for an uncommitted insert and then removes it.
  - Tradeoff: a notification inserted after the TRUNCATE still fails in the background thread on the FK, which raises a NOTIFICATION_FAILED alert in the log. Only the symptom in the test goes away.
  - Confidence: MED — not reproduced.
  - Blind spot: lock waits against a still-running clean-up.
- **Decision**: FIXED (Fix A) — `pipeline/OwnRuns` test helper; `PipelineRunControllerIT.awaitIdle` also waits for `ownRuns()` to be empty; PipelineRunControllerIT 17/17 green

### F2 — The `?:` scan finds only declarations at the start of a line

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/contract/responseTypes.test.ts:23-24
- **Detail**:
  - The regex `^\s*(?:readonly\s+)?(name)\?:` misses an optional field in an inline object type (`{ a: string; b?: number }`), `foo ?: T`, quoted keys and `Partial<…>`.
  - Nothing like that is in `src/api` today.
  - It does not misfire on ternaries, and the minimum-hits check is present.
- **Fix**: also match `[{;,]\s*name\?:` (or `\b\w+\s*\?:` outside ternaries) and add `Partial<` to the scan, or accept the gap with a comment in the test.
- **Decision**: FIXED — the scan also matches after `{`, `;`, `,`, `(` and `name ?:`, plus a case test and a `Partial<` check; responseTypes.test.ts 5/5 green

### F3 — A KNOWN_GAPS entry excuses every rule of its component; blank reasons are allowed

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractVariantsTest.java:120-131
- **Detail**:
  - A gap is keyed only by `Record.component`. On a `@Nullable List`, an entry for "no null" would also hide a later "never non-empty".
  - Blank reasons in `KNOWN_GAPS` and `NOT_READ_BY_SPA` are accepted, unlike the exceptions in `ClosedSetsIT`.
  - Neither matters today: the three gaps are plain nullable fields with reasons.
- **Fix**: assert every reason is non-blank. Optionally key a gap by the missing variant (`null`/`non-null`/`non-empty`).
- **Decision**: FIXED — `ContractVariantsTest.everyExceptionHasItsReason` rejects a blank reason in `KNOWN_GAPS` and `NOT_READ_BY_SPA`; keying by variant left out until a `@Nullable List` needs a gap; ContractVariantsTest 3/3 green

### F4 — The plan describes the PostSeeds retry run as having full times

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/src/contract/runs-page.json:16-18 (plan Phase 2 §2)
- **Detail**:
  - The plan says the fourth element is a "`succeeded` retry z pełnym czasem".
  - `PostSeeds` sets only the status, so `startedAt`/`finishedAt`/`lastActivityAt` are `null` in the file.
  - The file and the test agree, and the element adds another null-time variant. Only the plan's wording is off.
- **Fix**: none in code; recorded here.
- **Decision**: ACCEPTED — note only; the plan's phase blocks are not rewritten
