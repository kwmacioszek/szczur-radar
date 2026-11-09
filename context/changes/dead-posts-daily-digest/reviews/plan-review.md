<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Podsumowanie nocy w ntfy zamiast alarmu o martwym poście (S-34)

- **Plan**: `context/changes/dead-posts-daily-digest/plan.md`
- **Mode**: Deep
- **Date**: 2026-11-09
- **Verdict**: REVISE → SOUND after triage (all 7 findings fixed in the plan)
- **Findings**: 0 critical, 5 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING (F1) |
| Blind Spots | WARNING (F2, F3, F6) |
| Plan Completeness | WARNING (F4, F5, F7) |

## Grounding

23/23 paths ✓, 7/7 symbols ✓, brief↔plan ✓, Progress↔Phase ✓ (4 phases, 22 rows at review time — 24 after triage: 1.7 and 4.9 added; no checkboxes outside Progress).

Verified in code by a sub-agent (all CONFIRMED unless noted): `preserve()` semantics and the `variable.delete` gate refusal (`railway.ts:132-134,303-305`, `gate.jq:19,96`); every `trigger.sh` exit path after the 202 (`:272-275, :293-303, :328-334, :344-347`), `fail()` exits at once (`:17`), check-in body = last non-empty log line (`:87, :114-115`), `jq` in the cron image; `OperatorAlertQueue.raise` is fire-and-forget (`:164-202`); the "outside the night" window — admin runs cannot interleave with the cron's RETRY+RANGE (`PipelineRunService.java:216-225`, one `saveAll`), `pipeline_runs.created_at` is queue time (V3:15, `PipelineRun.java:66-68`), RETRY/POSTS runs can make a post dead (`PostTransitions.java:201-208`), `scheduled()` = requester SCHEDULER via the token filter. CONTRADICTED: `InternalPipelineRunController` is not web-only (F1).

## Findings

### F1 — Kontroler /internal/ nie jest web-only: bezpośrednia iniekcja OperatorAlertQueue wywróci `migrate`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 2 §3
- **Detail**: `InternalPipelineRunController` is a plain `@RestController` (`:38-40`); operator commands build the same context with `WebApplicationType.NONE` (`SzczurRadarApiApplication.java:36-39`), and `OperatorAlertQueue` is a bean only in the server (`AlertsConfiguration.java:20-22`). Direct injection would fail every command, including the Railway pre-deploy `migrate`. Precedent: `DeploySafetyController` takes `ObjectProvider` (`:43-45`).
- **Fix**: Add to Critical Implementation Details and Phase 2 §3: `ObjectProvider<OperatorAlertQueue>` + static `OperatorAlertQueue.raise(provider, …)`, as `RunNotifications.java:46,113`.
- **Decision**: FIXED

### F2 — Warunek „usuń HEALTHCHECK_PING_URL z API przed merge'em” da się wyeliminować, a CI na PR go nie wykryje

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details, Migration Notes, Phase 1 §4, Progress 4.8
- **Detail**: The claim is true (`railway.ts:303-305`, `gate.jq:19`), but `preserve()` never creates a value and is a no-op for an absent key, so keeping both declarations on the API is harmless. PR envs lack the sealed value, so the PR plan is empty and criterion 1.6 proves nothing; `variable.delete` appears only on `main` after the merge and stops the deploy if 4.8 is skipped.
- **Fix A ⭐ Recommended**: Keep `HEALTHCHECK_PING_URL: preserve()` next to `NTFY_URL: preserve()`; removing the variable becomes a post-merge runbook step; the declaration goes in a later change.
  - Strength: No ordering constraint before the merge; precedent `PIPELINE_STALL_TIMEOUT: preserve()` unset.
  - Tradeoff: A dead declaration until cleaned up.
  - Confidence: HIGH — semantics confirmed in `railway.ts` and `deploy-plan.md:840`.
  - Blind spot: The key must not also go into `variables.ignore`.
- **Fix B**: Keep the plan's approach, split 4.8 and add an Automated pre-merge check of production's key list (`railway variables -s szczur-radar-api -e production --json | jq -e …`).
  - Strength: Clean IaC right after the merge.
  - Tradeoff: A human step still blocks the deploy if skipped; an API restart between the removal and the deploy silences the channel.
  - Confidence: MEDIUM — order depends on a human.
  - Blind spot: Whether Railway restarts the API on a variable change without redeploy (`--skip-deploys` assumed).
- **Decision**: FIXED via Fix B — new Automated row 4.9, 4.8 reworded (`--skip-deploys`), Critical Implementation Details and Migration Notes name the CI blind spot; Phase 4 §4 added.

### F3 — Limity ntfy.sh liczą się per IP, a Railway dzieli egress między najemców

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Current State Analysis (ntfy.sh), Open Risks (brief)
- **Detail**: ntfy.sh identifies a "visitor" by IP without a token; the API on Railway leaves through shared egress IPs, so the 60-request bucket and the 250/day limit are shared with other Railway tenants writing to ntfy.sh. Symptom: 429 → `FAILED` → one WARN line, i.e. silence in a channel whose only signal is its absence.
- **Fix** (as reported): Name the risk, add a 429 test case, defer a token to a follow-up change if 429 appears.
  - Strength: No new mechanism now.
  - Tradeoff: A production 429 means a separate change with a new secret.
  - Confidence: MEDIUM.
  - Blind spot: Whether Railway Hobby has dedicated egress IPs.
- **Decision**: FIXED differently — the owner chose an optional `NTFY_TOKEN` now (`Authorization: Bearer`, limits per account): `AlertSettings.TOKEN_NAME`, validation (printable ASCII, 1–256, requires `NTFY_URL`), header in `NtfyAlerts`, 429/401 → `FAILED` with WARN, `preserve()` in IaC, `send-test-alert`, runbook; new Automated row 1.7.

### F4 — `tools/healthchecks/test.sh`: usunięcie `api-events` psuje więcej niż „oczekiwanie”, w tym wyprowadzanie fixture `deploy-health`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §4
- **Detail**: `fixtures/get-checks.json` is a recording with `api-events` as `.checks[2]`; `post-api-events-created.json` is the only recorded "simple" check and the source from which step 8 (`test.sh:409-444`) derives the production `deploy-health` fixture by `gsub`. Request/DELETE counters in steps 1–4 and 8 count the third check. Step 9 reads only `PING_PATTERN` and `PING_MAX_LENGTH` from `AlertSettings.java` — the plan was right there.
- **Fix**: Name in Phase 1 §4: rename the simple-check fixture (e.g. `post-simple-created.json`) as the `deploy-health` source, edit `get-checks.json` + `fixtures/README.md`, lower the counters in steps 1–4 and 8, update `tools/healthchecks/README.md`.
- **Decision**: FIXED

### F5 — Promień rażenia poza planem: PRD/infrastructure.md i teksty w kodzie nadal mówią o healthchecks dla API

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §3, Phase 4 §1–2
- **Detail**: Not in the plan, false after the change: `cli/CommandType.java:21-22`, `settings/InvalidSettingsException.java:6,15`, `context/foundation/infrastructure.md:35,37,134`, `context/foundation/roadmap.md:67,453`.
- **Fix**: Add the files to Phase 1 §3 and Phase 4 §1; widen criterion 4.2 with a grep of `infrastructure.md` and of `HEALTHCHECK_PING_URL` in `src/main`.
- **Decision**: FIXED

### F6 — Noc, której podsumowanie przepadło, nie wraca w żadnym późniejszym podsumowaniu

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details (window), Open Risks
- **Detail**: The "outside the night" line covers only admin runs; dead posts of cron runs belong to their own night. If the summary call or the send fails (non-durable queue, no retries — deliberately in NOT Doing), those dead posts never reach ntfy; only the bell shows them.
- **Fix**: One sentence in the brief's Open Risks and in the S-34 runbook ("no morning summary = check /admin and the API log").
- **Decision**: FIXED

### F7 — Jedyny bezpieczny dla surogatów ucinacz jest w klasie, którą plan usuwa

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §2
- **Detail**: `HealthchecksAlerts.body()` (`:105-115`) cuts by chars with a high-surrogate check; the API has no byte-based UTF-8 truncation, which ntfy's 4096-byte limit needs. `RunNotifications.cut` and `RunRecorder.truncate` are not surrogate-safe.
- **Fix**: In Phase 1 §2: move `body()` into `NtfyAlerts`, extend it to a byte limit (encode UTF-8, back off to a code-point boundary); test with a 3- and a 4-byte character on the boundary.
- **Decision**: FIXED
