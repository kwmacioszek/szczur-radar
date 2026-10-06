<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Kontrakt oceny LLM V2 z walidatorem i pomiarem na zestawie wzorcowym

- **Plan**: context/changes/assessment-contract-harness/plan.md
- **Scope**: Full plan — phases without an earlier review (1, 2, 4) plus cross-phase checks and the production code of all phases; phases 3, 5, 6 have their own reports (`impl-review-phase-{3,5,6}.md`), whose findings are not repeated
- **Reviewed phases**: 1, 2, 4
- **Date**: 2026-10-06
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 6 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Success criteria: 1.1 (V1 only in the archive), 1.2 (no `contract-v1.md` references), 1.3 (no API keys in the change folder), 2.6 (no Python in `contract-v2.md`) re-run — pass; `./mvnw -B verify` (425 unit + 57 IT) and CI on PR #31 (run 37421759932) green; `SkeletonAgent` gone. 1.5 (`uv run pytest`) cannot be re-run since F-03 removed the Python backend — historical. Clean: D1–D15 each a strategy with failure and passing tests, §7.2 table enumerated, schema ↔ records ↔ §9 limits, strict parsing, §10 white space / §11 prefilter, prompt SHA pin (v2.5), `Assessor`/`AssessorAgent` per plan, §17 covers every contract change, layering (`llm` independent of the assessor, `sources` maps only to `AssessmentInputV1`), post text sent as escaped JSON and marked untrusted.

## Findings

### F1 — Prompt's news rule lacks the §7.1.7 exception

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/main/resources/assessor/system-prompt.md:25
- **Detail**: Contract §6.6 (since 2026-10-05) says article facts without comment → `not_expressed` **except a price move of the target (§7.1.7)**. Prompt line 25 says "fakty bez komentarza autora → `not_expressed`" without the exception, while line 31 gives the §7.1.7 rule — a news post describing a price move gets two conflicting instructions. Not documented. Any prompt change means v2.6 and re-recording the four configurations (CI gate).
- **Fix A ⭐ Recommended**: Document as a known gap (implementation note) and fix it with the next prompt change (S-02 or LLM hardening), re-recording then.
  - Strength: Keeps the measured v2.5 recordings and the chosen thresholds valid; the more specific line 31 already wins in practice (gold direction 73 %).
  - Tradeoff: The prompt stays inconsistent with the contract until the next change.
  - Confidence: MED — how the model resolves the conflict is not measured separately.
  - Blind spot: No count of news posts with price moves in the gold set.
- **Fix B**: Add "(poza ruchem ceny celu — niżej)" to line 25, bump to v2.6, re-record luna, deepseek-flash, gemini-flash-lite, luna-low (< $0.5).
  - Strength: Prompt and contract agree before merge.
  - Tradeoff: New measurement; Luna's margin (2 targets / 1 target) may not hold.
  - Confidence: MED — small wording change, but results move by a few targets between runs.
  - Blind spot: Run-to-run noise of the same prompt is not measured.
- **Decision**: ACCEPTED via Fix A — known gap in implementation-notes N19; fix with the next prompt change (S-02 / hardening) and re-record.

### F2 — Prompt rule "posiadanie → bullish" is not in the contract

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: system-prompt.md:30; context/foundation/llm-assessment/contract-v2.md:164
- **Detail**: Prompt: "posiadanie, zakup lub dokupowanie, radość z zysku … → `bullish`; sprzedaż, strata … → `bearish`". The contract has no such rule, and §3 gives "trzymam, nic nie robię" as the `neutral` example (the prompt's `neutral` line dropped it). N13 records it as a prompt-only rule from the gold analysis — it bypasses §16 ("contract first") and the assessor `CLAUDE.md` (code follows the contract).
- **Fix A ⭐ Recommended**: Bring the contract to the prompt via §16: add the rule to §7.1 (holding/buying/profit → bullish, hedged when suggested; selling/loss → bearish; an explicit "nic nie robię" stays `neutral`), log it in §17. Document only — no prompt change, no re-recording.
  - Strength: Restores "contract first" for what was measured; the gold labels (guidelines v1.2) already read it this way.
  - Tradeoff: A contract change decided after the measurement — your decision.
  - Confidence: HIGH — wording only, matches what the prompt does.
  - Blind spot: Whether "trzymam" alone (without "nic nie robię") should be bullish is a judgement call for you.
- **Fix B**: Reword the prompt to the contract (drop "posiadanie") — v2.6 and re-recording, as in F1 Fix B.
  - Strength: Contract unchanged.
  - Tradeoff: Likely worse direction accuracy (the rule was added because models fled into `not_expressed`).
  - Confidence: LOW — effect unmeasured.
  - Blind spot: Same as F1 Fix B.
- **Decision**: FIXED via Fix A — contract §7.1.8 (author's position) + §3 `direction` + §17 entry; user: „trzymam X” alone = `bullish`, `hedged`; prompt unchanged.

### F3 — No output-token limit for any provider

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/{OpenRouterClient,DeepSeekClient,GeminiClient}.java, LlmClientFactory.java
- **Detail**: No `maxTokens` / `maxOutputTokens` anywhere in `src/main`, and `ProviderSchemas` drops the §9 length limits before sending the schema. A runaway structured answer (repetition loop) is billed up to the model's maximum output and can hit the timeout, which is retried (3 attempts). With 12k posts/month one loop costs little, a systematic one a lot.
- **Fix**: Set a per-provider output cap derived from the §9 maximums plus reasoning headroom (e.g. 8 000 tokens; Luna `medium` used ≤ ~2 500), and assert it in the client tests; a cut-off answer stays `invalid_structured_output`, not retried.
  - Strength: Bounds cost and time per call before S-02 runs unattended.
  - Tradeoff: Too low a cap cuts reasoning models; needs headroom.
  - Confidence: MED — max output per recorded call should be checked before choosing the number.
  - Blind spot: How each provider counts reasoning tokens against the cap.
- **Decision**: FIXED — `LlmClient.MAX_OUTPUT_TOKENS` = 16 000 in all three clients, asserted in the client tests (parameter names verified against the local server only).

### F4 — `ProviderMessages` record would print the API key

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/llm/ProviderMessages.java:8
- **Detail**: `record ProviderMessages(String apiKey)` keeps the generated `toString()` → `ProviderMessages[apiKey=<key>]`; every client holds one, so a log or debug dump of a client exposes the key (`LlmProviderProperties` overrides `toString` for this reason).
- **Fix**: Override `toString()` with `apiKey=<hidden>` and add a test that the key is absent.
- **Decision**: FIXED — `ProviderMessages.toString()` hides the key; `ProviderMessagesTest`.

### F5 — `Assessor.evaluate` fails open on an unknown failure class

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/Assessor.java:86; validation/ValidationResult.java:29
- **Detail**: `evaluate` returns `ok` when `failureClass()` is empty, not when `valid()`; `failureClass()` knows only three classes, so a future rule emitting another class would yield `ok` and drop its violations. Latent today (every `RuleId` maps into the three).
- **Fix**: Branch on `validation.valid()`; let `failureClass()` fall back to the first violation's class.
- **Decision**: FIXED — `Assessor.evaluate` decides on `valid()`; `failureClass()` falls back to the first violation's class; test in `RuleBasedValidatorTest`.

### F6 — No examples for §6.5a and §7.1.7

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: context/foundation/llm-assessment/examples.md
- **Detail**: §16 step 5 requires contract + examples + tests for each contract change. §6.5a (market slang → target "rynek") and §7.1.7 (price move → its direction, hedged) were added during phases 5–6, but `examples.md` got only a rationale edit (example 6), so `ContractExamplesTest` exercises neither rule.
- **Fix**: Add two short examples (a slang post → "rynek"; a "X dziś -5%" post → bearish hedged) with full outputs, covered by `ContractExamplesTest`; you approve the example texts.
  - Strength: Closes §16 for both rules; the validator is tested on the new stance shapes.
  - Tradeoff: Example text must be written and accepted (synthetic, or anonymized gold posts).
  - Confidence: HIGH — same pattern as examples 1–7.
  - Blind spot: None significant.
- **Decision**: FIXED — examples 8–10 (§6.5a, §7.1.7, §7.1.8) on real anonymized posts, approved by the user, read by `ContractExamplesTest`. Also: gold label 10-01-005 `krach` → `bearish` hedged (user, §7.1.7).

### F7 — Failed calls lose provider and model in provenance

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/Assessor.java (callFailed)
- **Detail**: On `LlmCallException` the provenance has provider, model and channel `null`; §15 / agent-implementation §6 list provider/model. S-02 would store `provider_error` / `timeout` rows without knowing which provider failed, although the configured one is known.
- **Fix**: Pass the configured provider and model to `Assessor` and fill them in failed results (channel stays null).
- **Decision**: DEFERRED to S-02 — `follow-ups/review-fixes.md`, comment on issue #14.

### F8 — Hardening before S-02 persists results

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: config/LlmProviderProperties.java:58-92, 165; llm/GeminiClient.java:73; sources/wykop/WykopApiClient.java:146; agents/assessor/Assessor.java:61; text/Whitespace.java
- **Detail**: (a) `Provider`/`ServiceTier`/`ReasoningEffort` lack `@JsonValue`, so a serialized `Provenance` gives `"GEMINI"` while other enums give lower-case ids; (b) `LLM_TIMEOUT` unbounded — Gemini's `toIntExact` overflows above ~24.8 days on the first call; (c) blank Gemini answer with `stop` passes as success (others → `provider_error`), and uses `isBlank` instead of `Whitespace`; (d) Wykop error bodies (incl. `auth`) quoted unmasked; (e) schema re-read and re-transformed per call; (f) invisible characters (U+200B, U+00AD, U+FEFF) are outside §10 — a model dropping them fails D6 (contract-level, check Wykop data first).
- **Fix**: Queue as a follow-up for S-02 (which persists results and runs unattended); none blocks F-02.
- **Decision**: DEFERRED to S-02 — `follow-ups/review-fixes.md`, comment on issue #14.

### F9 — Evidence of 4.6/4.7 is not where the criterion says

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: plan.md Progress 4.6, 4.7; PR #31 body
- **Detail**: 4.6 says the live-smoke results (denominator, Luna 429 rate, median time) are "w opisie PR"; they are only in implementation notes N5/N6 (Gemini 30/30 after the fix, DeepSeek 27/30, Luna 28/30, median 9.1 s; 0 retries). `LiveSmokeTest` reports retries without separating 429s.
- **Fix**: Add a "Kontrola na żywo (faza 4)" line with these numbers to the PR body and annotate 4.6/4.7 "(wyniki: N5/N6)".
- **Decision**: FIXED — live-smoke results in the PR #31 description; 4.6/4.7 annotated.

### F10 — Stale traces in docs and Progress

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: change.md (Notes); agent-implementation.md:102; plan.md Progress 1.1–1.8; contract-v2.md §17
- **Detail**: `change.md` still says "Plan v2", "GPT-6 Luna flex (pierwszy wybór)"; agent-implementation says labeling guidelines v1.0 (now v1.2); phase 1 rows have no SHA (landed via `7a406f2`) and 1.5 is a Python command; §17 lists 2026-10-05 above 2026-10-04.
- **Fix**: Update `change.md` notes (v3, Java, Luna standard/flex), agent-implementation → v1.2, annotate 1.x "(na main przez 7a406f2; 1.5 historyczne — backend w Pythonie usunięty w F-03)", reorder §17.
- **Decision**: FIXED — `change.md` notes (v3, Java, Luna), agent-implementation guidelines v1.2, Progress 1.1–1.8 → `7a406f2` (1.5 marked historical); §17 order left (newest first, consistent).
