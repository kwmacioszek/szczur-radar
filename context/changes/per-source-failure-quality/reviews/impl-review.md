<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Jakość ocen per źródło — nieudane próby według klas (S-35)

- **Plan**: context/changes/per-source-failure-quality/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-11
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 5 observations

Phase 4 is in scope explicitly: all of its Automated rows are `[x]`. Only its two Manual rows (4.5, 4.6) are pending, and those are the owner's to decide.

Automated criteria re-run on 16f1cd5:
- `./mvnw -B verify`: unit 1116 / IT 379, 0 failures, BUILD SUCCESS
- `npm test`: 29 files / 573 tests
- `npm run build`: green
- `tools/pipeline-trigger/test.sh`: All checks passed
- `gh pr checks 166`: all 12 checks pass
- PR-env checks 4.2–4.4: evidence in `evidence/pr-env/README.md` and the scratchpad (200 with the window keys, 401 without a session, the `quality 7d` lines in the cron log and in ntfy, test variables removed). The keys and the variables were checked again from the main context on 2026-11-11.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — A failure computing the quality window also loses NIGHT_SUMMARY

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/NightSummary.java:182-186, pipeline/api/InternalPipelineRunController.java:117-125
- **Detail**:
  - `quality.window` and `quality.crossed` run inside `NightSummary.of`, in the same `@Transactional(readOnly, REPEATABLE_READ)` as the rest of the summary.
  - If either throws, the route answers 500, `NIGHT_SUMMARY` is not raised, and the cron logs a single `WARN: nightly summary not sent`.
  - Under S-34 the summary must go out every night, and a missing summary is the only sign that the API → ntfy path broke. The quality lines are an addition, but they now share that path.
  - The current code has no known throwing path: `percent` gets a whole > 0, and `sources` skips sources without attempts. The risk is a future bug or a query error confined to the new query, such as a timeout on a large window. A whole-database failure would kill the summary anyway.
  - A `try/catch` inside `of` is not enough: Postgres aborts the transaction after a failed statement.
- **Fix A ⭐ Recommended**: Compute the quality in its own read-only call after `summary.of`, from the controller or from a separate method of `NightSummary`.
  - On a `RuntimeException`: log an ERROR with the exception class, send the summary with a line `quality 7d: unavailable (<class>)`, and send no `ASSESSMENT_QUALITY`.
  - Strength: Keeps S-34's "every night" promise independent of S-35. The quality transaction is separate, so an aborted statement does not touch the summary.
  - Tradeoff: The quality and the summary are no longer one REPEATABLE_READ snapshot. The difference is attempts written between the two reads, which does not matter at night. It needs an extra test (failing quality → summary sent).
  - Confidence: HIGH — the controller already does the raise; the change is local.
  - Blind spot: Whether `Night` should then still hold the window, or the controller appends the lines to the text.
- **Fix B**: Leave it, and add one sentence to the S-35 runbook: a missing summary can also come from the quality query (look for the ERROR in the API log).
  - Strength: No code change. The failure is unlikely and is visible as a missing summary.
  - Tradeoff: One subsystem failure takes out the other signal for the whole night.
  - Confidence: MED — it depends on how much the owner trusts the stability of the new query.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A). `NightSummary.of` reads the window in its own read-only `TransactionTemplate` before the `REPEATABLE_READ` snapshot. On a `RuntimeException` it logs an ERROR, and `Night.qualityUnavailable` gives the line `quality 7d (<from>…<to>): unavailable (<class>)`, nothing in crossed. Tests: `NightSummaryTest.anUnavailableQualityWindowIsOneLineAndNoAlarm` and `InternalPipelineRunIT.aQualityWindowThatCannotBeReadLeavesTheSummaryWithoutItsQualityLines` (`@MockitoSpyBean AssessmentQuality` throws `QueryTimeoutException`; break-check: a rethrow in the catch → the IT red). Docs: the class javadoc, `pipeline/CLAUDE.md`, deploy-plan S-35.

### F2 — The /admin section describes the denominator inaccurately

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/src/views/AdminView.vue:467
- **Detail**:
  - The section says "próby z odpowiedzią modelu, z ponowieniami".
  - The measure (plan, PRD FR-033, deploy-plan S-35) counts attempts with a model call (`result_source = model`), including `provider_error` and `timeout`, which have no answer from the model.
  - The PRD and the deploy-plan describe this correctly; only the SPA does not.
- **Fix**: Change the text to "próby z wywołaniem modelu (bez prefiltra), z ponowieniami" and update AdminView.test.ts if it pins the text.
- **Decision**: FIXED. `AdminView.vue:467` now says "próby z wywołaniem modelu (bez prefiltra), z ponowieniami". No test pins it. The phase 3 screenshots in `evidence/` show the old text.

### F3 — The cron README sample uses numbers from a different night

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: tools/pipeline-trigger/README.md:67-68
- **Detail**:
  - The log sample is dated the night of 2026-10-13, window 2026-10-07…2026-10-13, and shows Wykop "2.6% of 3134" with no alarm.
  - Per `evidence/README.md`, that night was the only alarm night (19.0 % of 147 attempts).
  - The 3134 attempts belong to the window 2026-11-05…11. Reddit's "46 attempts" contradicts "assessed 99" in the line above, and Reddit was not on production until 2026-11-01.
  - Nothing pins the sample (the format is pinned by NightSummaryTest), but next to the deploy-plan example it misleads.
  - Also, the deploy-plan S-35 says the class shares "add up to the failed share". After rounding they only roughly do: its own example gives 19.1 against 19.0.
- **Fix**:
  - Move the README sample's quality lines to a consistent window, e.g. 2026-10-07…13 → Wykop "failed 2.0% of 147 attempts …" with no Reddit line; or move the whole sample to a November date.
  - In the deploy-plan, say "make up the failed share (up to rounding)".
- **Decision**: FIXED, differently from the offered option. The sample's own source lines say Wykop `assessed 150, failed 2` and Reddit `assessed 99` that night, so the evidence window (147 attempts) could not fit. The quality lines now follow from those lines: the window sees only that night, Wykop `failed 1.3% of 152 attempts (fabricated_evidence 1.3%); threshold 5%`, Reddit `99 attempts, fewer than 100, threshold not checked`. The README also has a sentence about this and about the `unavailable` line (F1). The deploy-plan says "up to rounding".

### F4 — PR env contradicted the plan: ASSESSMENT_QUALITY appeared, and Wykop was at 5.9 %

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/per-source-failure-quality/evidence/pr-env/README.md (step 3)
- **Detail**:
  - The plan expected no alarm in the PR env ("za mało prób").
  - On a fresh database the previous window is empty, so it counts as fewer than 100 attempts, and Wykop's 734 attempts at 5.9 % crossed its 5 % threshold. That is good end-to-end proof of the alarm.
  - The 5.9 % (`schema_violation` 4.1 %) comes from the CI's LLM settings, not production's. The deploy-plan's first-night runbook rightly expects no alarm on production, because production has history (~2.6 %).
  - This is input to the owner's row 4.5, not a code defect.
- **Fix**: Mention it in the 4.5 hand-off. On production, check the first nights' Wykop line; if it exceeds 5 % consistently, revisit the threshold (a PR in `Source`).
- **Decision**: FIXED. A comment on issue #145 (no topic name, no secrets) asks for a check of Wykop's first nights on production and a return to the threshold if it stays above 5 %.

### F5 — The comparison window cut at `now − 24 h` is an approximation, and a lost alarm never repeats

- **Severity**: 💬 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/posts/AssessmentQuality.java:51, 79-87
- **Detail**:
  - The plan chose `now − 24 h` deliberately ("Critical Implementation Details"). The real cut-off of the previous summary depends on how long last night's runs took.
  - When tonight's summary comes earlier than last night's, the tail of last night's attempts drops out of "before". A source that already alarmed can then alarm a second time. A missed alarm would need admin attempts in that small gap.
  - The rule is stateless and ntfy delivery is never retried. If the crossing night's message is lost, that episode never alarms again; only `; ABOVE THRESHOLD` in the nightly summary and the /admin section remain.
  - The night-summary timing logic (`PREVIOUS_NIGHT`) sits in `posts/` (the plan placed it there; there is no import violation).
- **Fix A ⭐ Recommended**: Add one sentence to the deploy-plan S-35 runbook: a lost `ASSESSMENT_QUALITY` message does not repeat, so `ABOVE THRESHOLD` in the summary is the lasting signal; and a duplicate alarm is possible when the summary times differ.
  - Strength: The cost is two sentences; the plan's decision stands.
  - Tradeoff: The approximation stays.
  - Confidence: HIGH — it describes the actual behaviour.
  - Blind spot: None significant.
- **Fix B**: Cut the comparison window precisely at the creation time of the previous night's last cron run, which `NightSummary` already looks up with `findLastScheduledCreationBefore`, and move the crossing rule into `pipeline/`.
  - Strength: An exact cut-off; the summary's timing logic leaves `posts/`.
  - Tradeoff: A design change against the plan, plus new tests and an IT. The creation time of a run is not the time of its summary either.
  - Confidence: MED.
  - Blind spot: The case where the previous night had no cron run, e.g. a run that failed.
- **Decision**: FIXED (Fix A). The deploy-plan S-35 ("The alarm `ASSESSMENT_QUALITY`") now explains that a lost message does not repeat, so `ABOVE THRESHOLD` and `/admin` are the lasting signal, and that the `now − 24 h` cut can give a second alarm within one episode. The code is unchanged.

### F6 — No EXPLAIN showing that the range query uses ix_assessments_assessed_at

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/resources/db/migration/V15__assessments_assessed_at_index.sql, posts/AssessmentRepository.java:75
- **Detail**:
  - The index is added, but nothing shows the planner chooses it, with the join to `posts` as a nested loop on the primary key rather than a hash join over all of `posts`.
  - Also, the V15 comment says it runs "before the new API starts". A plain `CREATE INDEX` blocks the writes of the old API, which is still serving during pre-deploy, for a fraction of a second. That is acceptable, but the comment is inaccurate.
- **Fix**:
  - Run `EXPLAIN (ANALYZE, BUFFERS)` of the generated SQL in the PR env (which has ~840 attempts) and save it in `evidence/`.
  - Correct the V15 comment only if the PR is not yet merged. A V-file edited before its first deploy is fine; after a deploy it is fix-forward.
- **Decision**: FIXED (EXPLAIN only). Saved in `evidence/explain-pr166.md`:
  - The window covering nearly the whole PR-env table is a seq scan + hash join (correct for 839 of 952 rows).
  - A selective window is `Index Scan using ix_assessments_assessed_at` + a nested loop on `posts_pkey`.
  - The V15 comment is left alone: PR env #166 already applied V15, and a changed checksum would break its `migrate`.

### F7 — Test isolation in two ITs is inconsistent with the repo's pattern

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/AssessmentQualityControllerIT.java:68-69, pipeline/api/InternalPipelineRunIT.java:405-407
- **Detail**:
  - `AssessmentQualityControllerIT` deletes the Feb–Mar 2025 attempts "so no other test's attempt lies near the window". That is redundant: the class is `@Transactional`, and `DbIsolationExtension` (S-30) truncates after every class. The comment suggests a leak that cannot happen.
  - `InternalPipelineRunIT` deletes only the assessments in `finally`; the run, the post and the raw payload stay for the rest of the class. Nothing breaks today (checked), but it is a possible future flake, the kind S-30 removed.
- **Fix**: Remove the redundant delete with its comment in ControllerIT; in InternalPipelineRunIT, clean up the run, the post and the payload as well (or rely on the class's isolation pattern).
- **Decision**: FIXED.
  - `AssessmentQualityControllerIT`: the redundant `DELETE` and its comment are gone (the class's `@Transactional` and `DbIsolationExtension` are enough).
  - `InternalPipelineRunIT.aSourceCrossing…`: `finally` deletes the assessments, the post, the raw payload and the run.
  - Both ITs are green (27/27 and 5/5).

### F8 — Small inconsistencies: the SPA rounds differently from the API, and the plan says V14

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/views/AdminView.vue:165-167; context/changes/per-source-failure-quality/plan.md (Phase 1 Success Criteria, Progress 1.4)
- **Detail**:
  - `Intl.NumberFormat` rounds the floating-point ratio, while the API rounds exactly with BigDecimal HALF_UP. At `.x5` boundaries /admin and ntfy can differ by 0.1 percentage point. The state comes from the API, so this is display only.
  - The plan and Progress row 1.4 say "MigrationsIT z V14", but the migration is V15. Step titles are not renamed, so this is only a note for whoever reads it.
- **Fix**: Accept both. Optionally, format the share in the SPA from integers (`Math.round(failed*1000/attempts)/10`, with HALF_UP on integers).
- **Decision**: ACCEPTED. The 0.1 pp difference is display only (the state comes from the API); the "V14" in the step title stays (titles are not renamed), the code and docs say V15.
