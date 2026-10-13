<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-05 daily-scheduled-run-freshness

- **Plan**: context/changes/daily-scheduled-run-freshness/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-10-13
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

The recorded adaptations match the plan's intent:
- the status route returns counters;
- a shared `PipelineRunExceptionHandler`;
- `pipeline-trigger` has its own 0.5 vCPU / 0.5 GB cap after #52;
- `h1` sits outside the `aria-busy` section;
- the docs name the Railway notification rule instead of e-mail;
- FR-022 is marked as delivered by S-02.

`tools/db-backup/test.sh` counts users dynamically. That change was required, because V6 inserts the system account.

Every "What We're NOT Doing" boundary holds:
- no `@Scheduled`;
- no schema-version check;
- no RETRY cap;
- no changes to `/api/posts`, `PostPage` or `api/posts.ts`;
- the backup still runs at 02:00.

Success criteria checked on 2026-10-13:
- `./mvnw -B verify` passed: 529 unit tests and 124 ITs.
- `npm test` passed 127 tests, and `npm run build` passed.
- `tools/pipeline-trigger/test.sh` and `tools/db-backup/test.sh` both ended "All checks passed".
- CI on `e3761a4` passed all 7 jobs.
- The manual items 1.8, 3.4–3.6, 4.3, 5.3 and 5.4 have evidence in `deploy-plan.md` (S-05) and in `ui/`.

## Findings

### F1 — The cron re-sends `POST /daily` after an ambiguous failure

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: tools/pipeline-trigger/trigger.sh:87-88, 142-156
- **Detail**: `request()` maps every curl failure to `000`, and the start loop retries `000` as "the API is waking".
  - Some of these failures happen after the POST was sent: a `--max-time 60` timeout, an empty reply, or a reset. The API may already have queued the run.
  - The retry then gets `409 pipeline_busy` for the cron's own run and waits as "busy" for up to 60 min.
  - If that run ends sooner, the cron starts a second daily run and reports only the second one. A `failed` first run would never reach the exit code, so no notification would fire.
  - If the first run lasts longer, the cron fails with a false "still busy".
  - The chance is low. While the API wakes, its port is closed ("Could not connect", seen in the PR env), so the usual `000` is safe to retry. The POST itself is a quick insert under an advisory lock.
- **Fix A ⭐ Recommended**: retry the POST only when no connection was made. `request()` also records `%{time_connect}`. A `000` with `time_connect` 0 means the API was unreachable, so retry it. A `000` after a connection is an unclear start, so fail with exit 1 and the message "the start may have gone through; check /admin". Add a `test.sh` scenario where the stub accepts the POST and hangs (needs a shorter `--max-time` for the test, e.g. an internal override).
  - Strength: no second run, and the result stays honest: an unclear start becomes a notification and the operator checks `/admin`. The change stays in one script, and the API contract does not change.
  - Tradeoff: in that rare case the night ends with an alarm instead of a run, even if the run actually went through.
  - Confidence: HIGH — curl sets `time_connect` to 0 when the TCP connection never opened. The PR-env wake log showed exactly that path.
  - Blind spot: a reset between connect and send would count as "unclear" even though nothing was sent. That is harmless: it only causes an alarm.
- **Fix B**: make `/daily` idempotent for the scheduler. While a queued or running run requested by `SCHEDULER` covers the same range, answer 202 with the existing runs, and let the cron poll them.
  - Strength: the cron always ends up waiting for the right runs, with no false alarm.
  - Tradeoff: new logic and tests in the API, plus a change to the route contract ("202 can return existing runs").
  - Confidence: MED — needs a query over active runs by requester and range, next to the advisory lock in `queue()`.
  - Blind spot: a RETRY run queued before the RANGE run makes "same range" harder to match.
- **Decision**: FIXED (Fix A) — `trigger.sh` retries a start only when no connection was made (`%{time_connect}` 0); no answer after a connection exits 1 ("the run may have started; check /admin"). New `TRIGGER_REQUEST_TIMEOUT` (default `60s`); `test.sh` case `no-answer` (1 POST, exit 1), red with the guard removed. README and deploy-plan updated.

### F2 — Whether a reference to the sealed token resolves is known only after the merge

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .railway/railway.ts (`pipeline-trigger.env.PIPELINE_TRIGGER_TOKEN`), context/deployment/deploy-plan.md (runbook S-05, step 2)
- **Detail**: Railway's docs do not say whether a reference to another service's sealed variable resolves. If it resolves to an empty value, the first 00:30 run exits 2 (config error). The runbook already has the check and the fallback, but it does not say when to run it.
- **Fix**: in step 2, say "on the merge day, before 00:30 UTC", so a failure shows before the first night.
- **Decision**: FIXED — runbook S-05 step 2 says "on the merge day, before 00:30 UTC".

### F3 — Start-up recovery interrupts runs whoever owns them (existing behaviour)

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStartupRecovery.java:57-84
- **Detail**: The change fixes the race where the waking request gets interrupted (recovery now runs before the port opens; `PipelineStartupRecoveryIT`). But when Railway runs a new container next to the old one during a deploy, the new one still marks the old one's running run `interrupted`. This dates from S-02. S-05 makes it more visible, because a run now happens every night. The only protection is the process rule "no merge 00:30–01:30 UTC" (REPOSITORY_GUIDELINES, deploy-plan).
- **Fix**: no code change in S-05. Optionally note it as a risk in roadmap S-06 (run history and failure signal).
- **Decision**: FIXED — no code change; the risk is noted under S-06 in `roadmap.md` (v2.16).

### F4 — An admin's backfill of an old range moves the label to "now"

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java:37-42
- **Detail**: `max(finished_at)` counts every RANGE run that ended `succeeded` or `partial`, including a manual re-run of an old week. The label then says "updated now" even when the last nights failed. This follows the interview decision ("RANGE, scheduled or manual") and the PRD note under FR-030. It is recorded here only so the meaning is clear.
- **Fix**: none. The decision is in the PRD. If FR-030 later should mean "data current up to day X", that is a separate change.
- **Decision**: FIXED (differently) — the label counts, among succeeded/partial RANGE runs, the one whose range reaches furthest, then the latest end (`findFinishedAtOfNewestRange`: `order by rangeTo desc, finishedAt desc limit 1`). An admin's run over older days no longer moves it; a run over the same newest days does. Two `FreshnessIT` cases (red with the old order); PRD FR-030 note, roadmap, deploy-plan, `pipeline/CLAUDE.md`, API README and Javadoc updated.

### F5 — `h1` moved out of the list's region (accessibility trade-off)

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: szczur-radar-web/src/views/FeedView.vue (template: `<main>` → `h1`, `.freshness`, `<section aria-labelledby="feed-title">`)
- **Detail**: The label has to sit right under `h1` and also outside `aria-busy`. To allow both, `h1` moved from the section into `main`. The region is still named "Posty" through `aria-labelledby`, and focus after a page change still lands on `h1`. All tests from `feed-view-ui-hardening` pass. Jumping to the landmark now lands on the list instead of the heading. Documented in `ui/README.md`.
- **Fix**: none. Keep it as is and come back to it in S-03 (`post-detail-view`) if the heading layout changes there.
- **Decision**: FIXED (differently) — `h1` and the label are back inside `<section aria-labelledby="feed-title">`; `aria-busy` moved to an inner `<div class="posts">` (list + pager). Tests use `.posts` for the busy state, plus a structure assertion (red with `h1` outside the section). Looks unchanged; `ui/README.md` updated.
