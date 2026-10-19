<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-10 llm-config-fail-fast

- **Plan**: context/changes/llm-config-fail-fast/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-18
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Success criteria: rows 1.1–4.2 are checked; CI on PR #82 is 9/9 green on `b2f26af` (HEAD code is identical; the epilogue only touches `context/changes`); the greps 1.2, 1.5, 2.3, 2.4 and 4.2 were re-run and pass. Manual rows 1.4, 2.5, 3.3, 3.4, 4.3 and 4.4 are pending, and none is falsely ticked.

## Findings

### F1 — The PIPELINE_STALL_TIMEOUT default hint breaks the new rule

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStallSettings.java:100
- **Detail**: The error ends with `, or unset for the default (15m 00s).`, which is formatted by `Durations.format`. An operator who copies `15m 00s` into the variable is refused, because a space and two parts break the rule. The hint is new in this change: the plan asks for `(<default>)` without naming a format. The assertions in `PipelineStallSettingsTest:83` and `InvalidSettingsFailureAnalyzerTest:36` pin the current format.
- **Fix**: Print the default in rule syntax (make `DurationSetting.format` public and use it, e.g. `(15m)`, `(39m)`), and update the two assertions.
- **Decision**: FIXED — default hint now `DurationSetting.format` (e.g. `(15m)`), `format` made public; assertions in PipelineStallSettingsTest and InvalidSettingsFailureAnalyzerTest updated

### F2 — Two changes go slightly beyond the plan (harmless)

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: szczur-radar-api/README.md (`create-admin` exit codes); ActivationSettingsTest:69, PipelineStallSettingsTest
- **Detail**: The README `create-admin` exit codes now read "1 odmowa lub błąd" instead of "1 odmowa"; this fixes a fact, since `FAILED` is also 1. Extra test cases go beyond the listed ones (`PT12H`, `12H`, `PT15M`, `25h`, `1ms`) and stay consistent with the contract.
- **Fix**: Keep both; no change needed.
- **Decision**: ACCEPTED — both extras kept as they are

### F3 — Refused values are echoed, so a key pasted into the wrong variable reaches the logs

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/LlmProviderProperties.java:134-135; settings/DurationSetting.java:68,74,78
- **Detail**: Messages carry `got '<value>'` for `LLM_API_PROVIDER`, `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT` and the three durations. If the operator pastes `LLM_API_KEY` into one of them, the key lands in the `APPLICATION FAILED TO START` block (stdout) and in the CLI line (stderr), i.e. in Railway's pre-deploy logs. This is not a regression: the old stack trace and `migrate failed:` carried it too. `LLM_API_KEY`'s own value is never printed (tested).
- **Fix A ⭐ Recommended**: Cap the echoed value (e.g. the first 16 characters + `…`) in `oneOf` and `DurationSetting`.
  - Strength: Takes the accidental-paste class out of every message, while short wrong values stay readable.
  - Tradeoff: Tests that assert `got '…'` for long values need adjusting; slightly less diagnostic for long values.
  - Confidence: MED — the paste scenario is plausible, but no incident is known.
  - Blind spot: Other settings outside `settings/` (Wykop, trigger token) do not echo values and were not checked for this.
- **Fix B**: Leave the behaviour and only soften the Javadoc of `InvalidSettingsException` ("never contains the value of `LLM_API_KEY`").
  - Strength: No code change; the message stays fully diagnostic.
  - Tradeoff: The risk stays.
  - Confidence: HIGH — documentation only.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — `SettingText.echoed` (16 characters, then `…`, never splits a code point) in `DurationSetting` and `oneOf`; tests in SettingTextTest, DurationSettingTest, LlmProviderConfigTest

### F4 — The CLI line stutters and repeats Boot's block

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/cli/CommandChecks.java:103
- **Detail**: The line reads `migrate: invalid configuration: Invalid LLM configuration: …` (or `…: invalid configuration: Invalid configuration: PIPELINE_STALL_TIMEOUT …`), and the whole message has just appeared in the Boot block. The plan asked for `<cmd>: invalid configuration: <message>` and the "one short line without repeating the whole block" (Critical Implementation Details), and these two pull against each other. Both outputs are single lines and stay readable.
- **Fix**: Leave as is, or print `<cmd>: invalid configuration (see above)`; that would need the README, the tests and the plan wording updated.
- **Decision**: ACCEPTED — matches the plan contract and the README; only redundant

### F5 — `USAGE` promises "before any database work" only by accident of today's beans

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/cli/ExitCode.java:19-24; cli/MigrateCommandTest.java:56-66
- **Detail**: `InvalidSettingsException` comes before any context (the listener) or from `RunWatchdog`, which is `@ConditionalOnWebApplication` and never exists in a command context. If a future bean in a command context threw it after Flyway, `migrate` would exit 2 although it had migrated. The test `anInvalidSettingThrownFromABeanIsAConfigurationError` simulates a case that cannot happen today (it only checks the wrapping).
- **Fix**: Add one sentence to the `contextFailed` Javadoc: setting checks in beans of a command context must run before any database work.
- **Decision**: FIXED — `CommandChecks.contextFailed` Javadoc now states that setting checks in a command context's beans must run before any database work

### F6 — The `InvalidSettingsException` Javadoc overstates its reach

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/settings/InvalidSettingsException.java:5-8
- **Detail**: The Javadoc says "one type for every such setting", but `PipelineTriggerToken:45`, `WykopConfiguration:46` and `ConcurrentAssessmentDispatcher:288` (`PIPELINE_LLM_CONCURRENCY`) still throw a plain `IllegalStateException`, so they still print a stack trace and exit 1. These are out of scope on purpose ("What We're NOT Doing"); only the wording is wrong.
- **Fix**: Reword it to "every setting read through `settings/`"; optionally open a follow-up for the remaining three settings.
- **Decision**: FIXED — Javadoc names the settings it covers (`LLM_*`, `PIPELINE_STALL_TIMEOUT`) and those still on a plain `IllegalStateException`; the secret sentence matches F3
