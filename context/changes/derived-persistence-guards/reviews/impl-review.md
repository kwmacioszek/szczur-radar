<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Strażnicy zapisu i końca przebiegu liczą zbiory z kodu (S-39)

- **Plan**: context/changes/derived-persistence-guards/plan.md
- **Scope**: Full plan (3 of 3 phases)
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-06
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Success criteria

Full `./mvnw -B verify` re-run during the review: 1054 unit + 329 IT, BUILD SUCCESS. Break runs 1.2, 2.2, 3.2, 3.3 were not repeated (they edit src/main); their evidence is in the implementation run. No Manual rows in the plan.

## Findings

### F1 — `ck_pipeline_runs_sources` literal `'range'` is never read

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: ClosedSetsIT.java:237-238 (everyCheckWithLiteralsIsAValueListOrKnown), ClosedSetsGuard valueList / CONTAINED_IN
- **Detail**: `valueList()` ends with `CONTAINED_IN.find()`, which matches anywhere. The compound `ck_pipeline_runs_sources` (V12:29-31: `(kind = 'range') = (sources IS NOT NULL) AND (… sources <@ ARRAY[…])`) therefore counts as a value list and the literal check skips it; a typo in its `'range'` stays green, the X2 class of gap the slice closes.
- **Fix A ⭐ Recommended**: Treat only whole-definition `ANY_OF`/`ONE_OF` as a value list; for any other CHECK (including those with a `<@` fragment) also run `unknownLiterals` on the remaining literals.
  - Strength: Closes the gap with the existing parser; the same eight-fixture approach covers it.
  - Tradeoff: Needs a ninth fixture and a decision how `<@ ARRAY[…]` literals are attributed so they are not reported twice.
  - Confidence: HIGH — definition and CONTAINED_IN behaviour verified in V12 and the guard.
  - Blind spot: Other compound CHECKs with a `<@` fragment added later.
- **Fix B**: Keep the behaviour and say in the javadoc that a compound CHECK with a `<@` fragment is read only as a list.
  - Strength: No code change.
  - Tradeoff: The hole stays; the guard's own wording "a typo in a compound CHECK is red" stays partly untrue.
  - Confidence: HIGH — documentation only.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A)

### F2 — RunEndingGuard accepts `@Disabled` and measurement-only tests

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: RunEndingGuard.java (problemWith)
- **Detail**: A referenced test counts if it has `@Test`/`@ParameterizedTest`. `@Disabled` and `@Tag("measurement")` (excluded from surefire by pom.xml) are not checked, so a test that never runs in CI satisfies "an ending method has a test".
- **Fix**: Reject `@Disabled` on the method or class and a `measurement` tag; add cases to RunEndingGuardTest.
- **Decision**: FIXED (Fix now)

### F3 — `hasSize(21)` fails on every new converter

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: ClosedSetsGuardTest.java:76
- **Detail**: The test asserts exactly 21 converters. The intent is a floor against scanning zero; a legitimate new converter turns a unit test red with a bare "expected size 21" message.
- **Fix**: `hasSizeGreaterThan(20)` (or compare with the registry's enums) with a message saying what it guards.
- **Decision**: FIXED (Fix now)

### F4 — Parsing code duplicated between ClosedSetsGuard and ClosedSetsIT

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: ClosedSetsIT.java (LITERAL, LITERALS, VALUE, values(), difference(), ANY_LITERAL, valueList()) vs ClosedSetsGuard.java
- **Detail**: The literal patterns, `values()` and `difference()` exist in both classes; the `ANY_OF`/`ONE_OF`/`CONTAINED_IN` value-list parser lives only in the IT, so it is exercised only by the Docker-bound `theCheckParserReadsEveryFormOfAListOfValues`. The two grammars can drift apart, and F1 sits exactly on that seam.
- **Fix A ⭐ Recommended**: Move the shared patterns, `values()`, `difference()` and `valueList` into ClosedSetsGuard and unit-test `valueList` with the definition strings.
  - Strength: One grammar, unit-testable without Docker; makes F1 a one-place change; matches the lesson on abstraction while the code is small.
  - Tradeoff: A refactor commit in test code touching ClosedSetsIT again.
  - Confidence: HIGH — the duplication was checked in both files.
  - Blind spot: Test-method signatures in ClosedSetsIT that call the moved helpers.
- **Fix B**: Leave as is and accept the duplication.
  - Strength: No further change before the PR.
  - Tradeoff: Drift risk stays; F1 would need fixing in two grammars.
  - Confidence: MEDIUM — depends on how often the CHECK forms change.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A)

### F5 — Unreadable-form check counts only `::text` literals

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: ClosedSetsGuard.java (literalCount, VALUE) vs ANY_LITERAL in the IT
- **Detail**: A literal with another cast gives 0 literals and 0 pairs and passes silently; the promise "an unreadable form is red" holds only for `::text`. Harmless today.
- **Fix**: Make `literalCount` use `ANY_LITERAL`.
- **Decision**: FIXED (Fix now)

### F6 — Guard does not verify which converter an entity attribute uses

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: ClosedSetsIT / ClosedSetsGuard.problems
- **Detail**: The guard compares the scanned converter with the CHECK. A new attribute with `@Enumerated(STRING)` or an explicit different `@Convert` would write constant names while the guard stays green. None exist today (grep).
- **Fix**: Record as a known limit in the ClosedSetsIT javadoc; no mechanism now.
- **Decision**: FIXED (Fix now)

### F7 — Unstable message order and a bare NPE on a null-writing converter

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: ClosedSetsGuard.exceptionProblems, RunEndingGuard.missingCoverage (Map.of iteration), ClosedSetsGuard.writtenValues
- **Detail**: Problem messages follow `Map.of` order, which varies per JVM run (the F-class that made one phase-2 test flaky). No assertion depends on it today. A converter that maps a constant to null makes `TreeSet.add` throw a bare NPE without naming the enum.
- **Fix**: Iterate sorted keys; name the enum when the converter returns null.
- **Decision**: FIXED (Fix now)

### F8 — Two ending methods are witnessed by tests that mock the recorder

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: RunEndingTest.ENDS (`interrupt`, `finish`)
- **Detail**: `PipelineRunServiceTest#onCloseReportsARunOfItsOwnThatHadAlreadyEnded` stubs `recorder.interrupt` to an empty list, and `#aRunThatEndsWithItsSummaryLogsNoError` verifies `runEnded` against a mocked recorder; neither shows a real ending reaching `runEnded`. The IT partners cover this (`PipelineRunIT#aStoppingInstanceInterruptsItsRunAndPutsItsPostsBack`; for `finish` only the skipped-elements path). Within the plan's declared limit.
- **Fix**: Drop the weak service test from the `interrupt` entry; leave the rest.
- **Decision**: FIXED (Fix now)

### F9 — Inherited and non-public ending methods are not classified

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: RunEndingGuard.publicMethods (getDeclaredMethods)
- **Detail**: Public methods inherited from a RunRecorder superclass and protected/package-private ending methods are outside the guard. RunRecorder has no superclass today.
- **Fix**: State the limit in the guard's javadoc.
- **Decision**: FIXED (Fix now)

## After triage

All nine findings fixed in test code. Verified: targeted run (ClosedSetsGuardTest 20, RunEnding*Test 12, ClosedSetsIT 5), break run F1 (`'rang'` in `ck_pipeline_runs_sources` turns ClosedSetsIT red; migration removed from src and target/classes), full `./mvnw -B verify`: 1062 unit + 329 IT, BUILD SUCCESS.
