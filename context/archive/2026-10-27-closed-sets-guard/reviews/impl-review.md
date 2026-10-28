<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-23: zamknięte zbiory i kolumny posta pilnowane w CI

- **Plan**: context/changes/closed-sets-guard/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2
- **Date**: 2026-10-28
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Evidence: commits e7c294f (p1), b522368 (p2), f38f030 (epilogue) on e5ab295. Every Changes Required item is MATCH. The registry's 22 entries equal the 22 value-list CHECKs of V1–V11, one to one. The accessors match the converters (`Provider`/`ServiceTier`/`ReasoningEffort::id`, the rest `::value`). The `PostColumnsIT` matrix agrees with `pipeline/CLAUDE.md` and the SQL of `PostTransitions` in every scenario. No `src/main/java/*.java`, no migration, and neither `MigrationsIT` nor `PostRepositoryIT` changed.

Automated criteria rerun on 2026-10-28:
- `ClosedSetsIT,PostColumnsIT`: 15/15 green.
- Doc greps: 5 files and 2 mentions.
- `gh pr checks 126`: 11/11 pass, including both Railway PR-env deployments.
- The full `verify` (1.1, 2.1) and the deliberate breaks (1.3, 1.4, 2.3, 2.4) are recorded in the goal run; nothing has changed in the code since.

Manual rows 1.6 and 2.7 are pending (not ticked). This review supports ticking both.

## Findings

### F1 — Completeness check reads only three CHECK shapes

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsIT.java:116-123, 248-258
- **Detail**:
  - `everyValueListCheckIsInTheRegistry` sees only three CHECK shapes:
    - a whole definition `x = ANY (ARRAY['…'::text, …])`;
    - a whole definition `x = '…'::text`;
    - `(x <@ ARRAY[…])` anywhere in a definition.
  - A new closed-set column passes silently if both hold:
    - it is mapped to a `String` (so the metamodel check also misses it);
    - its CHECK has another shape: `x IS NULL OR x IN (…)`, a compound CHECK, or a `VARCHAR` column (`(x)::text = ANY ((ARRAY[…'::character varying])::text[])`).
  - The opposite edge: `CONTAINED_IN` uses `find()` without an anchor, so any future compound CHECK containing `col <@ ARRAY[…]` would require an enum (false positive). It also reads only the first `<@`.
  - Today every closed set is `TEXT` with a plain `IN` (V3:2), and a registered column whose CHECK changes shape still goes red ("found []"). So nothing is red-should-be-green today.
- **Fix A ⭐ Recommended**: In `valueListChecks()`, flag every CHECK containing `ARRAY[` or `'…'::` literals of one column that is neither parsed nor on an explicit list of known compound CHECKs (e.g. `ck_posts_stage_attempts`, `ck_assessments_outcome`, …)
  - Strength: A new closed set in an unusual shape goes red instead of passing, and the list of compound CHECKs makes the conscious exceptions visible.
  - Tradeoff: One more list in the test to maintain (a new compound CHECK with a literal = one line).
  - Confidence: MED — the shape of "a CHECK with literals" must be precise enough not to catch regex CHECKs (`~ '^[0-9a-f]{64}$'`).
  - Blind spot: There may be compound CHECKs with literals not listed in the plan; they need to be listed against the database.
- **Fix B**: Write the rule into the Javadoc and the HINT: "a closed set is a `TEXT` column with a plain `CHECK (x IN (…))`"
  - Strength: Zero code, and it states the convention that already holds (V3:2).
  - Tradeoff: The rule stays a convention; the guard does not enforce it.
  - Confidence: HIGH — that is the state of all 22 columns today.
  - Blind spot: An author who does not read the Javadoc.
- **Decision**: FIXED (Fix A): `NOT_A_VALUE_LIST` + `everyCheckWithLiteralsIsAValueListOrKnown`; a VARCHAR `probe IS NULL OR probe IN (…)` goes red (break-check 2026-10-28)

### F2 — Changes outside the plan's Changes Required

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md:441-443; context/foundation/roadmap.md; tools/render_roadmap_svg.py:193; docs/visuals/roadmap.svg
- **Detail**:
  - A second sentence about `PostColumnsIT` in the Tests section of `pipeline/CLAUDE.md`. The plan allowed one sentence in the `PostTransitions` row; this one is accurate.
  - The S-23 status flip in the roadmap plus regeneration of the SVG. Required by the REPOSITORY_GUIDELINES rule "Whenever roadmap.md changes…"; only S-23 and the counts changed.
  - Both were reported as ADAPT in the run report and are harmless.
- **Fix**: Accept as documented adaptations (nothing to change).
- **Decision**: ACCEPTED: documented adaptations (repo rule for the roadmap/SVG; the Tests sentence is accurate)

### F3 — PostColumnsIT can leave rows behind when `given()` fails

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/PostColumnsIT.java:234-246
- **Detail**:
  - `stored` is set only after `posts.save`. If `rawPayloads.store` or `posts.save` throws, the user, the run and the raw payload stay committed in the shared context.
  - Setting the run to `running` (:246) relies on no other committed `running` run existing (`uq_pipeline_runs_one_running`). `PostRepositoryIT:405-407` interrupts foreign running runs first. Today only `MigrationsIT.onlyOneRunIsRunning` creates one briefly, and it resets it.
- **Fix**: Set the cleanup ids step by step (user → run → raw → post), and before `running` interrupt foreign running runs as `PostRepositoryIT.running()` does.
- **Decision**: FIXED: cleanup ids kept step by step (`Rows` filled after each row); foreign `running` runs interrupted before the scenario's run is set running

### F4 — Matrix branches and the "only UPDATEs" claim

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/PostColumnsIT.java:57-59, 96-128, 173-189
- **Detail**:
  - Every held scenario starts with attempts 1, so the `stored` branch of the `CASE` in `RELEASE_SET` (attempts 0) is not run here. `PostRepositoryIT:241` covers it.
  - The coverage check works by method name, so a future overload (`release(…, X)`) counts as covered.
  - The Javadoc says `PostTransitions` holds "the only UPDATEs" of posts. That is true today (grep), but no test guards it: `Post` maps most `SET_ONCE` columns as updatable.
- **Fix**: Add a `release` scenario from `Given.held(0, RUNNING)` (→ stored), and soften the Javadoc to "are meant to be the only `UPDATE`s".
- **Decision**: FIXED: `release never failed` scenario (attempts 0 → stored); Javadoc "are meant to be the only UPDATEs"

### F5 — The registry repeats each converter's value method

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsIT.java:75-109, 160-182
- **Detail**:
  - The registry names `X::value` / `X::id` by hand instead of reading them from the converter (the converters are package-private).
  - `everyEnumAttributeOfAnEntityIsInTheRegistry` checks that `entity.attribute` exists, not that it maps to the registry's `table.column`.
  - Today all 22 pairs are correct (checked against the converters and migrations).
  - This was the plan's decision: an explicit registry, standard JPA API, no Hibernate internals.
- **Fix**: Accept as the plan's decision (the alternative is Hibernate's internal metamodel API, rejected at planning).
- **Decision**: ACCEPTED: the plan's decision (explicit registry, standard JPA API)

### F6 — `definition()` looks constraints up by name alone

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsIT.java:242-245
- **Detail**: A constraint name is unique per table, not per schema. A future duplicate `conname` would make `queryForObject` throw `IncorrectResultSizeDataAccessException` in the parser's self-test.
- **Fix**: Add `AND conrelid = ?::regclass` with the table name for each of the parser test's examples.
- **Decision**: FIXED: `definition(table, constraint)` with `conrelid = ?::regclass`

### F7 — The plan misread `ck_pipeline_runs_sources`; p1 commit type

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/closed-sets-guard/plan.md (Key Discoveries); commit e7c294f
- **Detail**:
  - Key Discoveries describes `ck_pipeline_runs_sources` as a whole definition `CHECK ((sources <@ ARRAY[…]))`. In reality it is compound (V5:8-10: `(kind='range') = (sources IS NOT NULL) AND …`). The implementation adapted correctly (`find()` + Javadoc :58-62); this was a defect of the plan, not of the code.
  - Commit p1 has type `test`, outside the goal-implement skill's list (feat/fix/chore/refactor/docs). It is a valid Conventional Commits type, and amending is forbidden.
- **Fix**: Nothing to change in the code; the squash merge message decides the final type.
- **Decision**: ACCEPTED: nothing to change in code; the squash merge message sets the final type

## Triage

- Fixed: F1 (Fix A), F3, F4, F6
- Accepted: F2, F5, F7
