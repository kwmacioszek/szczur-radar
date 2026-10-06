# measurement — model comparison harness (test-only)

This package exists to **compare LLM models** on the gold set and to pick the default (F-02): live
smoke, gold sampling, recording, metrics, report. It is test code, never ships in the jar, and
nothing in `src/main` may depend on it. How to run it: `README.md`.

## Standard of this code

It is a measuring tool, not product code. Keep it correct and readable, but do not hold it to
production standards:

- no hardening for misuse by the operator (odd `-D` values, hand-edited `models.json`, unknown
  channels) — a wrong run is visible in its output and simply repeated;
- free strings for recorded values (`provider`, `serviceTier`, `status`, price keys, …) are fine here:
  they mirror the JSON files, and the enums in `src/main` stay the source of truth;
- no refactoring for elegance, abstraction or deduplication unless a change of the measurement needs
  it;
- report layout and cost estimates are for the user's decision, not accounting.

Reviews (`/10x-impl-review`, code review) report issues in this package as **OBSERVATION** at most,
except the two things below.

## What must stay right

1. **The CI gate** — `RecordingsReplayTest` with `Replay`, `Recording`, `GoldMetrics`,
   `AnchorMatcher`, `GoldLabels` and `models.json` `default`: every current recording is judged as it
   was recorded, and the default configuration meets the thresholds in `GoldMetrics`. A wrong result
   here lets a validator or prompt regression into `main`.
2. **The metric definitions** in `GoldMetrics` (and the gold files in
   `src/test/resources/fixtures/assessor/gold/`), because the default model is chosen on them; a
   change is the user's decision and goes into the report and `implementation-notes.md`.

Keys only from the shell, never in the repo, recordings or logs (`README.md`).
