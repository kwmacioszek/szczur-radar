<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-21: zmiana IaC bez deployu nie blokuje wdrożeń

- **Plan**: context/changes/deploy-gate-no-deploy-changes/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-28
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 1 warning, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Success criteria

- Automated: 1.1 / 3.4 `tools/deploy-gate/test.sh` → "All checks passed." (77 PASS, 49 s); 1.2 cases (a)–(i) present, old expectation absent; 1.3 `bash -n` clean; 2.1 runs 40668249164 / 40669111765 / 40669730042 `success`; 2.2 diff empty; 3.1–3.3 pass. CI green on every branch commit.
- Manual: 1.4, 2.3–2.6 ticked with evidence (issue #115 comments, `deploy-plan.md` section S-21; re-verified on live data 2026-10-28); 3.5 comment on #73 posted with the operator's approval; 3.6 confirmed by the operator.

## Findings

### F1 — Empty CLI answer taken for "no deployment"

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/deploy-gate/verify-apply.sh:119, :167, :183
- **Detail**: `jq -r` on empty input prints nothing and exits 0, so a `railway deployment list --json` that exits 0 with empty stdout is not a failure. In `snapshot` `read` leaves `LATEST_ID` empty and the service is recorded as `null` (breaks the invariant at :179–180, "a failure is not taken for no deployment"); `check` then treats the whole list as after the apply and may judge an older apply's `iac-change-set/` deployment (reproduced with a stub: `{"postgres": null}`, then `p1 deployed (SUCCESS)`, exit 0). In `check` itself the same input gives `FOUND=""`: fail-closed, but it runs to the 15 m limit with the misleading reason "no list of deployments read after the window". Pre-existing in the old `latest()`; weighs more now that the boundary decides the verdict. The stub cannot express an empty or garbage answer, so the "printed no list of deployments" path is untested in both modes.
- **Fix**: `jq -er` (or `[[ -n $out ]] || return 1`) in `latest()` and `apply_deployment()`; stub answers `empty` and `garbage`; cases: `snapshot` retries and exits 1, `check` logs "printed no list of deployments" and never gives "no deploy".
- **Decision**: FIXED (jq -e in `latest()` and `apply_deployment()`; stub answers `empty`/`garbage`; cases `snapshot-empty`, `check-empty`; break-check: without `-e` `snapshot-empty` fails)

### F2 — Two ci.yml step comments still describe the old check

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: .github/workflows/ci.yml:463, :487-489
- **Detail**: :463 "the services the apply redeploys" (gate.sh now: "may redeploy"); :487–489 "so the check below waits for a new one" (it now looks for the apply's deployment above the boundary). The plan limited ci.yml to the comment of "Check after the apply" (:500-504), so these stayed stale.
- **Fix**: Reword both comments (comment-only; no step, `env:` or `if:` change).
- **Decision**: FIXED (both comments reworded; no step, `env:` or `if:` change)

### F3 — The window can end up to 1 s early

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/deploy-gate/verify-apply.sh:225, :232, :241
- **Detail**: `SECONDS` counts whole seconds, so `window_end = SECONDS + deploy_window` can be reached up to ~1 s early (measured: a 2 s window ended after 1.10 s). Harmless at the 2 m default; `asked_at` is correctly taken before the read.
- **Fix**: `window_end=$(( SECONDS + deploy_window + 1 ))`.
- **Decision**: FIXED (window_end + 1 s, capped at the limit so "no deploy" stays reachable when the window equals the limit; checked with a 3 s window and limit)

### F4 — Parity with sibling scripts: pause guard, cell escaping

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/deploy-gate/verify-apply.sh:295-297, :258-281
- **Detail**: No guard against a negative pause (pre-existing; `wait-safe.sh:120` has `(( pause > 0 )) || pause=0`) — a negative `sleep` exits 1 under `set -e` without `::error::` or summary (still fail-closed). `SKIPPED_REASON`, IDs and statuses go into the markdown table unescaped, while `gate.sh` escapes cells (`cell`); Railway's reasons seen so far contain no `|`, so cosmetic only (newlines are already collapsed).
- **Fix**: Copy the sibling's pause guard; escape `|` in summary cells.
- **Decision**: FIXED (pause guard as in wait-safe.sh; `|` escaped in the SKIPPED summary cell; case `skipped-pipe`, red without the escape)

### F5 — Test structure: inline call counts, no REMOVING case

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/deploy-gate/test.sh:632, :708, :728
- **Detail**: Three inline copies of `[[ $(grep -c …) == N ]] || { show_run; die; }` count `railway` calls, where the file otherwise uses helpers (`api_requests_were`); `REMOVING` has no case (only `REMOVED`).
- **Fix**: Helper `railway_calls_were <case> <service> <n>`; one `REMOVING` case.
- **Decision**: FIXED (helper `railway_calls_were` replaces the three inline counts; case `removing` with a 3 s limit, red when REMOVING is not treated as replaced)

### F6 — Phase 2 drift (documented) and one inaccurate commit message

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:1854-1862; commit e52ea3a
- **Detail**: All recorded in section S-21: the change-0 fallback was triggered by a `FAILED` Postgres (no volume), not by a CLI refusal; change 1 planned `source.checkSuites (null → true)`, so the gate's `(false → true)` path is covered only by its unit test; the 2.4 retry went beyond the plan's fallback (the PR-env toggle again, two empty commits, the branch moved through the GitHub API) and reproduced the parallel push live. The message of `e52ea3a` says "Pushed inside the check window", but it landed after it.
- **Fix**: Add a short "Deviations" note to `change.md` (history is not rewritten).
- **Decision**: FIXED ("Deviations from the plan" note in change.md)

## Triage (2026-10-28)

- Fixed: F1, F2, F3, F4, F5, F6 (6)
- Verdict after fixes: NEEDS ATTENTION → APPROVED
