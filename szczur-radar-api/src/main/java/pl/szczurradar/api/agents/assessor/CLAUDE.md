# agents/assessor — LLM assessment contract V2 as code

Source of truth: `context/foundation/llm-assessment/contract-v2.md`. Code follows the contract, never the other way round.

- `contract/` — records and enums 1:1 with §2–§3 (`AssessmentInputV1`, `AssessmentOutputV2`, …); `ContractJson.MAPPER` is the strict mapper for contract JSON (not Boot's).
- `src/main/resources/assessor/assessment-output-v2.schema.json` — the one definition of the output shape and §9 limits. Record component order = schema property order; no object ends with a nested object (`ContractSchemaTest`).
- `validation/OutputParser` — raw text → JSON (`invalid_structured_output`) → schema (`schema_violation`) → records. Never strip fences or repair output; no lenient framework converter may run before it.
- `validation/` — domain rules D1–D15 as strategies (`rules/*`), run in order by `RuleBasedValidator` from `ContractV2Rules`; `StanceTable` is table §7.2 as data.
- `Prefilter` — §11 no-content entries: no model call, `noContentOutput()`.
- `prompt/AssessmentPrompt` — the system prompt (`src/main/resources/assessor/system-prompt.md`) and the user message (untrusted-material prefix + the input's contract JSON). Any change to either needs a new `PROMPT_VERSION`; `AssessmentPromptTest` pins the text's hash to the version.
- `Assessor` — one assessment: `prepare` (prefilter → `Decided`, else the `PreparedRequest`), one `LlmClient.complete`, `evaluate` (parser → validator) into an `AssessmentResult` with `Provenance` (§15). Never retries, never repairs; a failed call is a `failed` result (`provider_error` / `timeout`), not an exception. `AssessorAgent` (Embabel) only delegates to it.
- `text/Whitespace` (package `pl.szczurradar.api.text`, shared with `llm` and `sources`) — the §10 white space set. All white space handling (quotes, prefilter, input, metrics, blank model answers) goes through it; never `Character.isWhitespace`, `String.strip`/`trim`, `\s` or `\S`.

## Adding or changing a rule

1. Change the contract first (§16): failing real fixture, minimal change to `contract-v2.md`, user decision.
2. New `ValidationRule` class in `validation/rules/` with its own `RuleId` (and failure class from the §12 table).
3. Add it to `ContractV2Rules` in the right place — a rule set with a repeated `RuleId` is rejected.
4. Tests: a negative case asserting the rule, path and class, and a positive case (`ContractV2RulesTest`); update `examples.md` (and `ContractExamplesTest` keeps them valid).

Lengths are Unicode code points (`codePointCount`), never `String.length()`.
