<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Podsumowanie nocy w ntfy zamiast alarmu o martwym poście (S-34)

- **Plan**: `context/changes/dead-posts-daily-digest/plan.md`
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-11
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 4 observations

Reviewed code: squash commit `2deaf39` (PR #162) on `main`. Automated criteria re-checked on `main`: 1.3, 1.4, 4.1, 4.2 locally PASS; CI run 42881908351 on `2deaf39` 9/9 green (backend verify, pipeline-trigger test, deploy-gate test, railway-config `No changes`). Manual 4.7 and 4.8 checked with evidence (owner's acceptance; `sealedVariableNames` lists `NTFY_URL`, `NTFY_TOKEN`). Production night 2026-11-10/11 verified in issue #144.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Ścieżki błędu odpytywania po 202 nie wysyłają podsumowania

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: tools/pipeline-trigger/trigger.sh:197, :325, :328
- **Detail**: Plan Phase 3 §1 and Critical Implementation Details: the summary call precedes every exit path after the 202, poll errors included. Code calls `nightly_summary` only before `not ended within` (:359) and before the result (:371); `cannot read the status … HTTP <non-200>` (:325), `unexpected status` (:328) and the API unreachable while waiting (:197) `fail` without it. The narrowing is documented consistently (pipeline-trigger README, deploy-plan S-34, PRD) but was never recorded as a plan decision, and `test.sh` does not pin it. On such a night the cron exits 1 (Telegram alerts), but the night's dead posts never reach ntfy — by the window rule no later summary shows cron runs' dead posts.
- **Fix A ⭐ Recommended**: Call `nightly_summary` before `fail` at :325 and :328 (leave :197 — the API is unreachable there), add a `test.sh` case for each.
  - Strength: Restores the plan's contract on the paths where the API is up; small, follows the existing call sites.
  - Tradeoff: A code change in the cron → a closeout PR that is no longer docs-only (the cron redeploys).
  - Confidence: HIGH — `request` always returns 0 and `nightly_summary` is already safe after a 202.
  - Blind spot: On a non-200 status read the summary itself may fail the same way; then it is one WARN line more.
- **Fix B**: Accept the narrowing: record it as a decision in the plan (addendum) and pin the current behavior in `test.sh`.
  - Strength: No code change; docs already describe it.
  - Tradeoff: Keeps the gap for dead posts of such a night (only the bell shows them).
  - Confidence: MEDIUM — depends on how rare these paths are (no occurrence on production so far).
  - Blind spot: Frequency of transient non-200 status reads not measured.
- **Decision**: FIXED (Fix A)

### F2 — Niechroniony zapis ciała żądania w nightly_summary może zmienić kod wyjścia crona

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/pipeline-trigger/trigger.sh:238
- **Detail**: `jq -cn … >"$work/summary-request"` runs under `set -e` without a guard. If jq or the redirect fails (full or read-only temp dir), the script aborts there: the exit code becomes jq's/bash's (2 is indistinguishable from `config_error`) and the last log line — the check-in's body — is no longer `OK:`/`FAILED:`. That contradicts the function's own contract ("one WARN line and nothing more").
- **Fix**: `jq … >"$work/summary-request" || { log "WARN: nightly summary not sent: cannot write the request"; return 0; }` plus a `test.sh` case.
- **Decision**: FIXED

### F3 — Brak indeksu na assessments.run_id i posts.first_run_id; plan twierdził, że jest

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/AssessmentRepository.java:41-60, PostRepository.java:69-71; plan.md "Performance Considerations"
- **Detail**: Plan: "existing indexes are enough (`assessments.run_id`, `ix_assessments_post_latest`)". Migrations have no index on `assessments.run_id` (only the FK `fk_assessments_run`, which Postgres does not index) nor on `posts.first_run_id` (verified: V3:59, :125, :178, V8:26 are the only indexes on these tables). `countDistinctPostsBySource`, `sumBySource` and `countFirstStoredBySource` seq-scan the tables once a night; cost grows with the whole history, not the night. The existing `findIdsFirstStoredBy` has the same gap. Cheap today (one call per night, the API awake anyway).
- **Fix A**: A migration `CREATE INDEX ix_assessments_run ON assessments (run_id)` (and `ix_posts_first_run ON posts (first_run_id)`) in the closeout PR.
  - Strength: Removes the growth with history for the summary and the existing `findIdsFirstStoredBy`.
  - Tradeoff: A migration in a docs PR; Flyway fix-forward, index build on production at deploy (small tables today).
  - Confidence: HIGH — standard FK index.
  - Blind spot: Write-path cost of two more indexes not measured (small).
- **Fix B ⭐ Recommended**: Record as a follow-up (`follow-ups/review-fixes.md` + a roadmap candidate), correct nothing in code now.
  - Strength: Keeps the closeout PR docs-only; the cost is negligible at today's size (one summary per night).
  - Tradeoff: The plan's wrong claim stays in the archived trail (this report corrects it).
  - Confidence: MEDIUM — table growth ~500 assessments/night; seq scan stays cheap for months.
  - Blind spot: No EXPLAIN on production data.
- **Decision**: FIXED (Fix A)

### F4 — Przykład podsumowania w dokumentach ma odwrotną kolejność źródeł niż kod

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: pipeline/NightSummary.java:39-45 (javadoc), context/deployment/deploy-plan.md (S-34 example, "pinned by NightSummaryTest"), tools/pipeline-trigger/README.md:65-66, plan.md "Desired End State"
- **Detail**: Code prints sources in `Source` order (WYKOP, REDDIT) — production's log of 2026-11-11 01:32 UTC shows `wykop:` then `reddit:`; the examples show `reddit:` first. `NightSummaryTest.thePlansExampleNight` passes only because it builds `Night` with reddit first: `Night` does not enforce the "in `Source` order" its javadoc claims. The README sample also has `new 197` > `fetched 166`, which cannot happen.
- **Fix**: Put `wykop:` first in the javadoc, deploy-plan S-34 and README examples (realistic numbers, e.g. production's night), and build the test's example in `Source` order.
- **Decision**: FIXED

### F5 — NightSummary bez jednej transakcji tylko do odczytu; of(pusta kolekcja) rzuca NoSuchElementException

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/NightSummary.java:108-156, :117
- **Detail**: ~7 queries each in their own transaction; while a run is still running (`not ended within` path) the counters may come from different moments. `of(emptyCollection)` throws at `found.getFirst()`; only the controller guards it, the method is public.
- **Fix**: `@Transactional(readOnly = true)` on `of` and an explicit precondition for an empty collection.
- **Decision**: FIXED

### F6 — README API pomija NIGHT_SUMMARY w priorytetach; brakujący przecinek w javadoc

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/README.md:22, :240; settings/InvalidSettingsException.java:15
- **Detail**: README says "3 for `TEST_SIGNAL`, 4 for errors" (written in Phase 1, not updated in Phase 2; `NIGHT_SUMMARY` is 3 too). Javadoc reads "`LLM_API_KEY` `NTFY_URL` or".
- **Fix**: Add `NIGHT_SUMMARY` (3) to both README places; add the comma.
- **Decision**: FIXED

### F7 — Trasa podsumowania przyjmuje dowolne przebiegi, nie tylko nocne

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunController.java:133-141
- **Detail**: 1–10 non-null UUIDs are validated, but not that they are the cron's (`scheduled()`) runs. A holder of `PIPELINE_TRIGGER_TOKEN` could make the API send a computed `NIGHT_SUMMARY` for old runs (each a new dedupe key), capped by the queue (2/min, 20 waiting). Only computed text, never caller text; the token is the cron's only.
- **Fix**: Optional: 400 unless every run is `scheduled()` — or accept as is.
- **Decision**: SKIPPED

### F8 — Plan nie przewidział pułapki variable.delete dla nowych zmiennych (incydent S-32)

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: plan.md "Critical Implementation Details", Migration Notes; plan-review F2
- **Detail**: Setting `NTFY_URL`/`NTFY_TOKEN` on production before #162 declared them stopped the deploy of S-32 (#160) on 2026-11-10 (`variable.delete` refused, API deploy `SKIPPED`); S-32 reached production with #162. Already handled: runbook S-34 step 2 warning (merge `71e2e97`), comment in #142, lesson "Nową zmienną z railway.ts ustawiaj na produkcji tuż przed merge'em…" (commit `370bd46`).
- **Fix**: None — recorded as a rule.
- **Decision**: ACCEPTED-AS-RULE: Nową zmienną z railway.ts ustawiaj na produkcji tuż przed merge'em — inny merge utknie na variable.delete
