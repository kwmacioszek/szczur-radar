# Review fixes — login-on-shared-foundation

Follow-ups from `reviews/impl-review-phase-4.md`. They are outside the scope of S-49: each one is its own change.

## F3 — Niestabilny job `mutation` (PIT): zaakceptowany mutant z TIMED_OUT

- **Source**: impl-review fazy 4, F3 (2026-11-22)
- **Seen**: CI run for 7c5ace8 (a commit that changes no Java code), job `mutation (PIT on the core)`. `SurvivingMutantsTest.everySurvivingMutantHasItsEntryAndEveryEntryItsMutant` reports the entry `AcceptedMutant.of("pl.szczurradar.api.pipeline.assess.ConcurrentAssessmentDispatcher", "assess", …)` as "without a surviving mutant (killed, timed out or gone)". Under CI load that mutant ended `TIMED_OUT`, not `SURVIVED`. The same job is green on 2123d07 and 8a0d5e3.
- **Risk**: a red `mutation` job unrelated to the change blocks `railway-config` and, through Wait for CI, the deploy, until someone re-runs it by hand.
- **Fix options** (to be decided in their own change):
  1. In `SurvivingMutantsTest`, treat `TIMED_OUT` of a mutant that has an entry in `AcceptedMutants` as still present, not as "gone". A killed mutant (`KILLED`) stays red.
  2. Raise PIT's `timeoutConstant` / `timeoutFactor` in the `mutation` profile of `szczur-radar-api/pom.xml`.
  3. Find out why this one mutant loops or slows down (`ConcurrentAssessmentDispatcher.assess`), and kill it with a behaviour test or give its entry a reason that covers the time-out.
- **Workaround until then**: `gh run rerun <id> --failed`.
