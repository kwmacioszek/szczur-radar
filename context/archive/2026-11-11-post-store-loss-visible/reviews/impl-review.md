<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Post odrzucony przy zapisie nie znika po cichu (S-38)

- **Plan**: context/changes/post-store-loss-visible/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-11
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Plan Adherence: every planned change of phases 1–2 matches its contract (drift agent: 18 × MATCH, no MISSING). Two wording differences are within the intent: the parenthetical in `REPOSITORY_GUIDELINES.md` is merged into the existing one, and the deploy-plan heading follows the style of its other sections. Phase 3 has no repo changes; its evidence is in issue #148 (run `988b5672-0061-4537-8f88-8a5d14d7fdfe`).

Scope Discipline: the only extras are benign. `PipelineRunIT` has a class-level `@MockitoSpyBean OperatorAlertQueue` to read the `RUN_NEEDS_ATTENTION` detail, `PostStoreTest` has extra cases, and `AssessmentSelectionTest` takes the new `StoredBatch` constructor. The "What We're NOT Doing" list holds: no migration, no new counter or reason, no SPA, `rules.json` or contract change, no `context/domain/` edit, and the source's status is unchanged.

Success Criteria, re-run during this review:
- `./mvnw -B verify`: 1107 unit tests and 376 ITs, BUILD SUCCESS.
- `npm test`: 29 files, 563 tests.
- The grep checks 1.4 and 2.6 pass.
- Phase 3 automated rows are checked with evidence in #148.
- Manual 3.7 (the owner accepts the wording) is pending.

## Findings

### F1 — The rejection cause is read from a message that can carry the post's text

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/PostStore.java:163
- **Detail**:
  - How the name is found: `cause()` trusts `ConstraintViolationException.getConstraintName()`. Hibernate's PostgreSQL extractor finds that name with the template `constraint "…"` (for 23502 `column "…"`), applied to the message of the top `SQLException`.
  - Why that message is unsafe: for a JDBC batch the top exception is pgjdbc's `BatchUpdateException`. Its message is `Batch entry N <SQL with the parameter values> was aborted: ERROR: …`, and the values (title, body) come before the server's error.
  - What goes wrong: a rejected post whose text contains `constraint "x"` gets a "cause" cut out of its own text.
  - Where that text then lands: the WARN line (not cut), `pipeline_runs.error` and the ntfy detail of `RUN_NEEDS_ATTENTION`. This breaks the change's own rule ("never the exception's message: it holds the row's values").
  - Likelihood and test gap: unlikely (the post must also really be rejected). The unit tests build the exception with an explicit name, so they never take this path.
- **Fix A ⭐ Recommended**: Accept the name only when it is a Postgres identifier (e.g. `[a-z_][a-z0-9_$]{0,62}`), else fall back to `SQLState <code>`; add a unit test whose `BatchUpdateException` message holds `constraint "` inside the values.
  - Strength: Closes the leak in the one method that decides the cause, without touching configuration. Our constraint names (`ck_…`, `uq_…`, `fk_…`) all match.
  - Tradeoff: A post text that happens to look like an identifier still passes, though it is a single short word; a quoted or mixed-case name would fall back to the SQLState.
  - Confidence: HIGH — the migrations name every constraint in lower snake case.
  - Blind spot: Not checked whether the extractor ever returns a schema-qualified name.
- **Fix B**: Set the pgjdbc property `logServerErrorDetail=false` on the data source (`application.properties`, next to `socketTimeout`), so the batch message and the server's `Detail:` carry no values at all.
  - Strength: Removes the values at the source, which also fixes F2's leak.
  - Tradeoff: A configuration change for every database error of the API, not only the store step; less detail for debugging other errors.
  - Confidence: MED — pgjdbc gates the batch entry's SQL text on this flag, but not verified here against 42.7.13.
  - Blind spot: Whether Hibernate's extractor still finds the constraint name when the message is shorter (PostStoreIT would show it).
- **Decision**: FIXED via Fix B — `logServerErrorDetail=false` on the data source (`application.properties`); verified first on PostStoreIT: the batch message becomes `Batch entry N <unknown> was aborted: ERROR: … constraint "ck_posts_channel"`, 0 `Failing row` lines, the cause still `ck_posts_channel`; PostStoreIT (a) asserts no post text in the output (red without the flag)

### F2 — Hibernate logs the failing rows (post text), and the volume made Railway drop S-38's own lines

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/resources/application.properties:10 (data source), logger `org.hibernate.orm.jdbc.error`
- **Detail**:
  - What Hibernate logs: on every failed batch and every failed single-post save, `SqlExceptionHelper` writes a WARN with the full SQL and `Failing row contains (…)`, which includes the title, body and author. This happened before S-38 too, but it breaks the `pipeline/CLAUDE.md` rule "no post text in logs".
  - What S-38 adds: its fallback now runs where it used to stay silent, and each rejected post adds one WARN of our own and one key read.
  - Observed in PR env (issue #148), runs with 24 and 12 rejections: about 1000 log lines in 8 s, and Railway dropped 9 of 24 and 7 of 12 of our WARN lines plus every INFO `store reddit` line. That is the very signal this slice adds.
  - The run with 22 rejections kept every line, so the loss depends on volume.
  - What stays reliable: the counters, `attentionReasons` and the run's error.
- **Fix A ⭐ Recommended**: Set `logServerErrorDetail=false` on the data source and `logging.level.org.hibernate.orm.jdbc.error=ERROR` (Hibernate logs these at WARN), with one line in `pipeline/CLAUDE.md` saying why.
  - Strength: Removes the post text from the logs and cuts the volume by the Hibernate lines, the bulk of the 1000. Our WARN lines already carry the cause.
  - Tradeoff: Other database errors of the API lose Hibernate's WARN detail; their exception still ends a step or a request with its own ERROR.
  - Confidence: MED — the logger name and level were seen in PR env; the pgjdbc flag's effect on the batch message is not verified here.
  - Blind spot: Whether any operator runbook relies on the `Failing row` lines.
- **Fix B**: Leave the configuration; record the risk in the deploy-plan S-38 section and handle it in a separate change.
  - Strength: Keeps this PR within its plan ("What We're NOT Doing" says nothing of logging, and the plan did not foresee it).
  - Tradeoff: Production keeps post text in its logs, and a burst of rejections can still hide the WARN and INFO lines.
  - Confidence: HIGH — nothing changes.
  - Blind spot: How often production meets a burst.
- **Decision**: FIXED as F1 (owner: "tak jak w F1") — the same `logServerErrorDetail=false`, without changing the Hibernate logger level: its WARN lines stay, one line each and without row values; documented in `pipeline/CLAUDE.md` and deploy-plan S-38

### F3 — Docs say NOT NULL falls back to the SQLState; Hibernate gives the column's name

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/deployment/deploy-plan.md (section S-38, step 1); RunFlow.java `RejectedPost` javadoc; szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/step/PostStoreTest.java (`theCauseIsTheConstraintOtherwiseTheSqlStateOtherwiseUnknown`)
- **Detail**:
  - With Hibernate's PostgreSQL extractor, 23503 arrives as a `ConstraintViolationException` named after the foreign key, and 23502 as one named after the **column** (template `column "`).
  - So the `SQLState` fallback appears in practice only for class-22 data errors.
  - The runbook says "`SQLState 23502` is a `NOT NULL`", and the unit test models 23502 as a bare `SQLException`.
- **Fix**: Say in the runbook and javadoc that a NOT NULL shows the column's name and the SQLState fallback is for data errors (class 22); let the test model 23502 as Hibernate does.
- **Decision**: FIXED — runbook step 1, `RejectedPost` / `PostStore` javadoc and `pipeline/CLAUDE.md` say a NOT NULL names its column and the SQLState fallback is a data error; PostStoreTest models 23502 as Hibernate does (`channel`) and the next-exception case on 22021

### F4 — The store part of the run's error can be cut by a long fetch summary

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunSequence.java:156
- **Detail**:
  - The error is `<sources> | store rejected … | <assessment>`. `RunRecorder.error` cuts it at 2000 characters and the alert detail at 300 (`RunNotifications.MAX_ERROR`).
  - A failed source's `<SimpleName>: <message>` is not cut first, so it can push `store rejected …` out of the alert and, rarely, out of the stored error.
  - The counts (`skipped N`) survive. The order is the one the plan chose.
- **Fix**: Accept as is (the plan fixed the order; the counts and WARN lines carry the rest), or cap the fetch summary's part before joining.
- **Decision**: FIXED — `RunSequence.assess` cuts the fetch summary to `FETCH_SUMMARY_WITH_STORE_MAX` (1000) when the store rejected posts, so the store part survives `RunRecorder.MAX_ERROR`; unchanged without rejections; two RunSequenceTest tests

### F5 — Nothing enforces posts = fetched − skipped, on which `ck_pipeline_run_sources_counts` relies

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/RunRecorder.java:177
- **Detail**:
  - `storeRejected` keeps `skipped <= fetched` only while a source makes one post per element, which both fetchers do today.
  - A future source that maps one element to several posts and has many rejected could break the `CHECK`. Then `storeRejected` throws and the run ends `failed`: loudly, not silently.
- **Fix**: Note the assumption in `sources/CLAUDE.md` (one post per fetched element), or assert it in `SourceIngest`.
- **Decision**: FIXED — `sources/CLAUDE.md` states one post per fetched element (`fetched = posts + skipped`), which `skipped <= fetched` relies on
