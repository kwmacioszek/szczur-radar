<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-41 — kontrakt SPA ↔ API, druga runda — kształty

- **Plan**: `context/changes/api-contract-shapes-round-two/plan.md`
- **Mode**: Deep
- **Date**: 2026-11-08
- **Verdict**: REVISE → SOUND after fixes
- **Findings**: 1 critical, 3 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | FAIL |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

8/8 paths ✓, symbols 7/9 ✓ (`routes()`/`Route` and the response records are not visible from the `contract` package), brief↔plan ✓

Confirmed by the code check:
- the `JsonMapper` bean (Boot 4.0.8 `JacksonAutoConfiguration`);
- retry run with `rangeFrom`/`rangeTo`/`requestedPostIds` null and `sources: []`;
- `import.meta.glob` with `?raw` (`rules.test.ts:250`);
- `PostsApiIT` seeds for every variant (some via `UPDATE`);
- `failed-post.json` touches only `shapes.test.ts`, `samples.ts`, `PostsApiIT:276` and `REPOSITORY_GUIDELINES.md:43`;
- no `Map`, `Optional` or generics in the response records.

## Findings

### F1 — Rejestr w pakiecie contract nie skompiluje się

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Phase 3 — §1 Rejestr, §3 ContractRoutesIT
- **Detail**:
  - From `pl.szczurradar.api.contract` only `SourceIngest.SourceState` is accessible. The other response records and their controllers are package-private.
  - `AuthorizationCompletenessIT` (`:49`), `routes()` (`:123`) and `Route` (`:142`) are package-private in `auth`.
  - The plan said "reuse routes()" and kept `Class.forName` only as a fallback.
- **Fix A ⭐ Recommended**: binary names via `Class.forName` in a static initializer, plus `routes()`/`Route` moved to a public test helper `web/MappedRoutes` (used by `AuthorizationCompletenessIT`, `AdminRoleIT`, `ContractRoutesIT`)
  - Strength: production code and the package convention unchanged; S-40 reuses the helper.
  - Tradeoff: IDE rename does not move the strings (the test fails loudly).
  - Confidence: HIGH — reading record components and annotations needs no `setAccessible`.
  - Blind spot: generics (`List<RunView>`) are built by hand in a helper.
- **Fix B**: make the response records and their controllers public
  - Strength: class literals checked by the compiler.
  - Tradeoff: ~8 production classes public only for tests.
  - Confidence: HIGH.
  - Blind spot: invites imports across feature packages.
- **Decision**: FIXED (Fix A) — Phase 3 §1 (widoczność), §3 (`MappedRoutes`, osobny commit refactor), brief Key Decisions

### F2 — runs-page.json z dokładnym total jest niestabilny

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 — §2; Critical Implementation Details
- **Detail**:
  - `PipelineRunControllerIT` is not `@Transactional`, and `@AfterEach` (`:143-153`) only waits for idle runs.
  - The `PostSeeds` constructor inserts a succeeded retry run (`PostSeeds.java:65-67`), and every `start()` leaves its runs behind.
  - The list test avoids this with `created_at` 2099 and asserts by id (`:288-337`).
- **Fix**: the page contract test deletes notifications and runs first (FK order), seeds its own, and keeps the `PostSeeds` retry run as a deliberate element.
- **Decision**: FIXED — Phase 2 §2 Contract, Critical Implementation Details

### F3 — Wpływ fazy 2 na SPA opisany nieściśle (atrapa, testy, kryterium 2.7)

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 — §3, criterion 2.7
- **Detail**:
  - There are two legacy `delete` tests (`runs.test.ts:257`, `RunDetailView.test.ts:445`).
  - KitchenSink builds no `Run`.
  - `mock-api.js` already sends both fields and has no retry runs, so 2.7 could not be run as written.
  - Adding `sourceResults: []` to the factories renders "Jeszcze żadne źródło…" in the range-run tests.
- **Fix**: correct the §3 file list and notes; 2.7 opens `/admin/runs/r-source-failed`.
- **Decision**: FIXED — Phase 2 §3, 2.7 (phase block and Progress), What We're NOT Doing, brief Out of scope

### F4 — Kolejność started-runs.json to [retry, range]

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — §1 table, §2
- **Detail**: `PipelineRunService.java:185` returns `List.of(retry, range)`; `PipelineRunControllerIT:164-177` asserts `containsExactly("retry","range")`.
- **Fix**: `[retry, range]` in the table and §2.
- **Decision**: FIXED

### F5 — LastAttempt.provider/model to wariant, nie kandydat do KNOWN_GAPS

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 — §2 KNOWN_GAPS
- **Detail**: `assessments.provider`/`model` are nullable (`V3:143-144`), and a MODEL `Provenance` does not require them.
- **Fix**: a null variant in `failed-posts-page.json` (an `UPDATE` in the seed), not a gap.
- **Decision**: FIXED — Phase 2 §1, Phase 3 §2, brief Open Risks
