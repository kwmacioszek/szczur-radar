<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-10 llm-config-fail-fast

- **Plan**: context/changes/llm-config-fail-fast/plan.md
- **Mode**: Deep
- **Date**: 2026-10-18
- **Verdict**: SOUND
- **Findings**: 0 critical, 3 warnings, 3 observations (all fixed in the plan)

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING → PASS after fixes |
| Plan Completeness | WARNING → PASS after fixes |

## Grounding

- Paths: 8/8 ✓.
- Symbols: 4/4 ✓ — `DurationStyle.SIMPLE`, the `org.springframework.boot.diagnostics.FailureAnalyzer` key in `spring.factories`, `ConfigurationOnly`, `railway variable list --json`.
- brief↔plan ✓.
- Branch ↔ issue #69 (Development) ✓.

Checked in the Boot 4.0.8 sources:

- An analyzer with a no-arg constructor catches the listener's exception when the context is `null` (`SpringApplication.java:314-331,803-840`, `FailureAnalyzers.java:55-98`).
- `listeners.failed` does not switch logging off before the report (`LogbackLoggingSystem.java:337-347`).
- The block is logged at ERROR, and the stack trace only at DEBUG (`LoggingFailureAnalysisReporter.java:34-42`).
- `main` does not print the stack trace a second time (`SpringBootExceptionHandler.java:65-118`).

## Findings

### F1 — The parser rule does not state letter case or the bound format

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 §1
- **Detail**:
  - Letter case: `DurationStyle.SIMPLE` accepts upper-case units (`fromSuffix`, `DurationStyle.java:252-259`), but the plan's regex refuses them. The plan did not say which is intended.
  - Negatives: `-5m` parses, so only the range check can reject it.
  - Bounds: Boot has no "largest whole unit" printer (`print` prints ms, and the `Unit` enum is package-private, `:124-126,179`).
- **Fix**: The contract states that units are lower-case only, that the range check rejects negatives, and that `DurationSetting` has its own formatter (DAYS…NANOS, first unit that divides the value exactly). The tests get `300S`, `5M` and `-5m`.
- **Decision**: FIXED

### F2 — Negative tests log an ERROR block

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 §4
- **Detail**: The `environment()` helper in `LlmProviderConfigTest` (`:233`) silences only `org.springframework.boot.SpringApplication`. With the analyzer, about 15 negative cases would each log a full `APPLICATION FAILED TO START` block.
- **Fix**: The helper adds `--logging.level.org.springframework.boot.diagnostics=OFF`. The startup test does not silence that logger, because it checks that block.
- **Decision**: FIXED

### F3 — Phase 1 missed some references to `config/LlmProviderProperties`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §3–§4
- **Detail**: Three references were not listed: `pom.xml:87` (a comment), `README.md:67` and `llm/CLAUDE.md:30,85`. Criterion 1.2 searches only `src`.
- **Fix**:
  - The three references are now listed in §4.
  - New criterion 1.5 runs `grep --exclude-dir=target` over `szczur-radar-api` and `REPOSITORY_GUIDELINES.md`.
  - `context/map/**` stays as it is: those files are dated snapshots.
- **Decision**: FIXED

### F4 — Two conditions bring the stack trace back

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details → Timing & lifecycle
- **Detail**: Either of these turns the stack trace on at DEBUG:
  - `DEBUG=true` in the environment;
  - a high-precedence `@Order` on `LlmConfigurationCheck`, which would run it before `LoggingApplicationListener`.
- **Fix**: One sentence in "Timing & lifecycle": no `@Order` on the listener; `DEBUG=true` adding the stack trace is expected.
- **Decision**: FIXED

### F5 — The end state overstated where the block appears

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Desired End State
- **Detail**: `PIPELINE_STALL_TIMEOUT` is read only by the server. A bad `ACTIVATION_LINK_TTL` gives the existing message with code 2 before any context starts, not the block.
- **Fix**: The bullet now names each setting with the place where the operator sees its error (plan and brief).
- **Decision**: FIXED

### F6 — `24h` in the docs, `1d` in the message

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Desired End State, Phase 2 §4, Phase 4 §1
- **Detail**: The formatter prints `from 1ms to 1d`, while the table and the docs said `1ms`–`24h`.
- **Fix**: The table, the brief and Phase 4 write the range as `1ms`–`1d` (= `24h`). The `MAX = 24h` constant does not change.
- **Decision**: FIXED (user's choice: docs `1ms`–`1d`)
