<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Kontrakt oceny LLM V2 z walidatorem i pomiarem na zestawie wzorcowym

- **Plan**: context/changes/assessment-contract-harness/plan.md
- **Scope**: Phase 6 of 6
- **Reviewed phases**: 6
- **Date**: 2026-10-06
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 5 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria: `./mvnw -B verify` (with `LLM_*` from `llm.env`) — 425 unit + 57 IT, BUILD SUCCESS, no `measurement` tests run; `RecordingsReplayTest` passes for `default` = `luna`; report generates. 6.5 (CI on PR) and 6.7 (one-time keys removed) are legitimately pending. Scope: extra candidate configs, `-Dgold.sample`, pilot 9 (Jev) are user-requested and documented (N13–N17, `frame.md`); nothing from "What We're NOT Doing" entered the code. No secrets in the 791 changed files; recordings contain only anonymised model output; nothing in `src/main` depends on measurement code.

## Findings

### F1 — "Current" recording ignores the configuration's model, tier and effort

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/Recording.java:72
- **Detail**: Plan step 2: current = current `PROMPT_VERSION`, `CONTRACT_VERSION` **and the configuration from `models.json`**. `Recording.current()` checks only the two versions; `Replay.of` adds the provider. Editing an entry in place (e.g. `luna` → other effort or tier) keeps the old recordings "current": `GoldRecorder` skips those posts on resume, and the report and the CI gate judge another configuration's answers under that id. (DeepSeek echoes `deepseek-flash` while `models.json` says `deepseek-v4-flash`, so the model name can only be compared where the provider echoes the requested id.)
- **Fix**: Add `Recording.currentFor(Model)` = versions + `serviceTier` + `reasoningEffort` (+ model where it is echoed verbatim) and use it in `Replay`, `GoldRecorder` and `RecordingsReplayTest`.
  - Strength: Restores the plan's definition; one predicate for all three users.
  - Tradeoff: Small change; an existing recording set that does not match fails loudly (wanted).
  - Confidence: HIGH — all 13 recorded sets were made with their current entries.
  - Blind spot: Flex-fallback recordings carry `serviceTier` flex with a standard channel — the tier check must use the requested tier, not the channel.
- **Decision**: DISMISSED — user: the measurement package only compares models; added `measurement/CLAUDE.md` (review standard: OBSERVATION at most, except the CI gate and metric definitions; a hand-edited `models.json` is operator misuse, not a gate defect).

### F2 — Runbook offers flex, which was never measured

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: context/deployment/deploy-plan.md (section "F-02: model domyślny", variable table and step 3)
- **Detail**: The table lets the operator choose `LLM_SERVICE_TIER=flex` (~$2.9/month), but `luna` was recorded on `default`; step 3 of the same section says a tier change is a new measurement. Flex is the same model at Batch prices, but it can return refusals/timeouts and falls back to standard — its compliance and latency on the gold set are unknown.
- **Fix A ⭐ Recommended**: Prescribe `default` in the runbook; flex only after a `luna-flex` configuration is recorded (30-post sample is enough to check refusals and compliance).
  - Strength: Production runs exactly the measured configuration; consistent with step 3.
  - Tradeoff: ~$3/month more until flex is measured.
  - Confidence: HIGH — removes the contradiction.
  - Blind spot: None significant.
- **Fix B**: Record `luna-flex` on the 30-post sample now and keep both options.
  - Strength: Keeps the cheaper option open before merge.
  - Tradeoff: Another measurement run (~$0.01) and a report update.
  - Confidence: MEDIUM — 30 posts show refusals and compliance, not quality differences.
  - Blind spot: Flex refusal rates vary by time of day.
- **Decision**: ACCEPTED — user takes the risk: default / batch / flex are assumed not to change the answers. Runbook step 3 aligned (a tier change is not a new measurement).

### F3 — Closed value sets as strings in the measurement records

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/ModelsConfig.java:70, Recording.java:43, GoldReport.java:202, Replay.java:103
- **Detail**: `provider`, `serviceTier`, `reasoningEffort`, `status`, `failureClass` (and the `prices` keys) are free strings although enums exist (`LlmProviderProperties.Provider/ServiceTier/ReasoningEffort`, `llm.Channel`, `AssessmentResult.Status`, `FailureClass`) — the accepted lesson "Zamknięte zbiory wartości trzymaj w enumach". It leads to `"flex".equals(...)`, `"failed".equals(...)`, `containsKey("peak")` and three different by-id lookups (`Recording.java:124`, `Replay.java:106`).
- **Fix**: Bind the record fields to the existing enums through their `@JsonValue` ids (one lookup helper); keep `prices` as a map but key it by a small `BillingKey` enum (channels + DeepSeek `peak`/`off_peak`).
  - Strength: Follows the lesson; the compiler sees every value; recordings stay byte-identical (same JSON ids).
  - Tradeoff: Touches 5 measurement classes and their tests.
  - Confidence: MED — JSON compatibility depends on every enum having a `@JsonValue`/`@JsonCreator` pair.
  - Blind spot: Not verified that every existing recording value maps to an enum constant (e.g. older `outputMode` values).
- **Decision**: DISMISSED — measurement tool only (`measurement/CLAUDE.md`: OBSERVATION at most outside the CI gate and metric definitions); user, triage 2026-10-06.

### F4 — Unpriced channel costs $0 silently

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/GoldReport.java:281
- **Detail**: `actualCost` returns 0 when the recorded channel has no price in `models.json`; a new channel (e.g. `openai/flex` on a config without that key) would understate the monthly cost without a warning. Per-post averages also divide by all recordings, including calls without an answer (25 `provider_error` of llama-4-scout).
- **Fix**: Throw when a call with an answer has no price; divide per-post averages by calls with an answer and show failed calls separately.
- **Decision**: DISMISSED — measurement tool only (`measurement/CLAUDE.md`: OBSERVATION at most outside the CI gate and metric definitions); user, triage 2026-10-06.

### F5 — Criterion 6.6 still says "Luna flex"

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/assessment-contract-harness/plan.md (Progress 6.6)
- **Detail**: 6.6 is checked but its text says the recordings were made with Luna flex; they were made on the standard tier (N13, N16). A checked criterion that does not describe what happened misleads the archive.
- **Fix**: Append "(Luna nagrana w trybie standardowym — N13/N16)" to 6.6 without renaming the step.
- **Decision**: FIXED — criterion 6.6 annotated (Luna on the standard tier; tier treated as not affecting answers).

### F6 — Committed report is not what the generator produces now

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/assessment-contract-harness/reports/gold-2026-10-06.md:14
- **Detail**: The report was generated before the Sol run; the Sol line and the decision section were added by hand. Regenerating would add `gpt-6.1-sol` to the 30-post table and the incomplete list, and drop the decision section.
- **Fix**: Regenerate the report and re-append the decision section (or let `GoldReport` include a decision file next to the report).
- **Decision**: FIXED — report regenerated (Sol in the 30-post table and the incomplete list), decision section re-appended.

### F7 — Harness guard rails: vacuous replay, sample 0, duplicate ids

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RecordingsReplayTest.java:25, GoldRecorder.java:52, ModelsConfig.java:34
- **Detail**: `everyCurrentRecordingIsJudgedAsItWasRecorded` passes with zero current recordings (e.g. right after a `PROMPT_VERSION` bump, if `default` were null); `-Dgold.sample=0` throws `ArithmeticException`; `ModelsConfig` accepts duplicate ids and two entries matching the same `LLM_*` (`matching` takes the first).
- **Fix**: Assert replayed count > 0; require `gold.sample >= 1`; reject duplicate ids and ambiguous matches in the compact constructor.
- **Decision**: DISMISSED — measurement tool only (`measurement/CLAUDE.md`: OBSERVATION at most outside the CI gate and metric definitions); user, triage 2026-10-06.

### F8 — DeepSeek peak schedule is only a string

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/GoldReport.java:291
- **Detail**: Peak 01–04 and 06–10 UTC Mon–Fri is hard-coded and documented only in `priceSources`; DeepSeek's earlier scheme was a daily discount window (16:30–00:30 UTC, weekends included). The fallback's cost depends on it; not re-checked against the pricing page in this phase.
- **Fix**: Re-read the DeepSeek pricing page and record the check date in `priceSources` (move the schedule into `models.json` only if it changes).
- **Decision**: DISMISSED — measurement tool only (`measurement/CLAUDE.md`: OBSERVATION at most outside the CI gate and metric definitions); user, triage 2026-10-06.

### F9 — CI gate fitted to the chosen model

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/GoldMetrics.java (Metric thresholds)
- **Detail**: Direction and contradiction thresholds were relaxed in the same phase and from the same report that picked `luna` (user's decision, documented). `RecordingsReplayTest` is therefore a regression guard for Luna, not an independent quality check; the margin is 2 targets / 1 target.
- **Fix**: Say so in the PR description (with the original thresholds as the hardening goal).
- **Decision**: FIXED — queued for the PR description in `follow-ups/review-fixes.md`.

### F10 — Minor report and code tidiness

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: GoldReport.java:55, 169, 230, 299; context/foundation/roadmap.md (F-02 Unknowns)
- **Detail**: Missing `post_id` list cut to 20 + "(+N)" (plan asks for the list); `SECTION_6_5A_TARGETS` hard-codes gold facts outside `labels.json`; inline fully qualified `java.time.ZonedDateTime` / `java.util.LinkedHashMap`; roadmap and report say "13 zmierzonych" while 12 were recorded on the full set or the sample before Sol (13 now, of 14 configured; qwen3.5-9b never recorded).
- **Fix**: Print the full missing list; import the two types; fix the roadmap count; leave `SECTION_6_5A_TARGETS` (one-off analysis).
- **Decision**: FIXED (document part) — counts corrected to 12 measured before Sol / 13 with Sol in roadmap, hardening-sources and the report; code part DISMISSED like F3 (`measurement/CLAUDE.md`).
