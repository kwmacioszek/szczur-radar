<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Bezpiecznik serii odmów dostawcy LLM (S-32)

- **Plan**: `context/changes/provider-refusal-breaker/plan.md`
- **Scope**: Phase 3 of 3
- **Reviewed phases**: 3
- **Date**: 2026-11-09
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 2 observations

## Scope and evidence

Phase 3 commits: `8fa4b3f` (PR-env result in `deploy-plan.md`, Progress 2.x/3.x), `489e6b8` (epilogue: Progress SHAs, `change.md` → `implemented`). Diff `58f5739..HEAD`: 3 files, 12 insertions, 10 deletions, documentation only — reviewed inline, without sub-agents (nothing to compare patterns against beyond the Results tables of earlier sections, which the row follows). No change touches a phase 1–2 file, so nothing earlier is affected.

Plan ↔ diff: `deploy-plan.md` S-32 Results row (plan Phase 3 #1) ✓; issue #142 comments (#2, GitHub, outside the diff) ✓; runbook "after the merge" (#2) present since phase 2 ✓; `plan.md` / `change.md` bookkeeping ✓. Nothing unplanned in the diff.

Automated criteria, re-run 2026-11-09:

| # | Command | Result |
|---|---|---|
| 3.1 | `gh pr checks 160` | 11 pass, 0 fail (head `489e6b8`; Railway: "No deployment needed - watched paths not modified") |
| 3.2 | SQL on `szczur-radar-pr-160`: newest run, its `error`, `count(*) … failure_class = 'provider_error'` | `partial`, `error` null, 0 |
| 3.2 | `railway logs -s szczur-radar-api -e szczur-radar-pr-160 --lines 3000 d2e72472-…` | 165 lines, 16 of run `db35738b`, 0 `refused 3 calls in a row`, 0 `ERROR` |
| 3.3 | `gh issue view 142 --comments \| grep -c 'szczur-radar-pr-'` | 2 |

Manual rows: 2.5 (owner judges the PRD clarification and the S-32 section) and 3.4 (merge decision) are pending and are genuine human decisions (lesson "Kryterium, które agent sprawdzi poleceniem…") — no agent-done work is hiding among them.

Safety: no secret in the diff, the issue comment or the transcript — the activation link was printed only in the owner's terminal; the URL pasted into the session had no token.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | WARNING |

## Findings

### F1 — The PR env's admin account: a precondition with no owner and no step

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Success Criteria
- **Location**: `context/changes/provider-refusal-breaker/plan.md:176` (Phase 3 #1 contract), `context/deployment/deploy-plan.md:2227` (S-32 PR-env verification, step 0)
- **Detail**: Criterion 3.2 (Automated) assumed "the owner logs in" to `/admin` of the PR env, and step 0 of the runbook lists "the PR env has an admin account (`create-user --role admin`)", but nothing says who creates it or when. A fresh PR env's database has no admin (one `user` without a password), and creating one prints an activation link, a secret the agent never handles (lesson "Stronę sprawdza agent przez playwright-cli…"); the agent's attempt to create a throwaway admin was refused by the permission check. So the unattended `/goal` run stopped at 3.2 with a GATE FAILURE after its 20-turn limit; the owner then created the account (with `RAILWAY_PROJECT_ID=<project>`, after the `-p` form the agent first gave failed with "Railway TUI not currently available") and started the run. `/10x-plan-review` did not flag it. It will recur in every plan whose PR-env check needs an admin session.
- **Fix**: Record a lesson: a plan whose PR-env criterion needs an admin session lists under Prerequisites "the owner creates and activates the PR env's admin account in their own terminal (`RAILWAY_PROJECT_ID=<project> railway ssh -s szczur-radar-api -e szczur-radar-pr-<N> -- java -jar /app/target/szczur-radar-api.jar create-user --email … --role admin`, section S-07), before an unattended run reaches that phase"; `/10x-plan-review` flags such a criterion without that prerequisite.
  - Strength: Fixes the class (every future PR-env check behind a login), not this plan, whose check already passed; keeps the rule that the link never passes through the agent.
  - Tradeoff: One more owner step before an unattended run; the run still cannot create the account itself.
  - Confidence: HIGH — the blocker and its resolution are in this session's transcript and in issue #142.
  - Blind spot: Whether a PR env could instead get a non-human route (the cron's token on `/internal/`) was not weighed — that is an infrastructure change outside this slice.
- **Decision**: FIXED (fixed differently, owner's choice: the agent creates a throwaway PR-env admin itself and activates it with a random password, as in S-06) + ACCEPTED-AS-RULE: „W PR env agent sam zakłada jednorazowe konto admina — link i hasło tylko w zmiennych powłoki” (`context/foundation/lessons.md`). No change to this plan: its check already passed.

### F2 — The run was started by the owner, not by the agent in a playwright-cli window

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `context/changes/provider-refusal-breaker/plan.md:176`, `context/deployment/deploy-plan.md:2228`
- **Detail**: The contract has the agent open `/admin` with `playwright-cli`, the owner log in there, and the agent start the RANGE run; the owner started run `db35738b` in their own browser (one of the two ways the agent offered). The evidence — SQL, the API log, the Results row, the #142 comment — was gathered by the agent; the row says "started by the owner as the PR env's admin".
- **Fix**: Accept — the deviation is recorded in the Results row and the issue comment; the run itself is the same.
- **Decision**: ACCEPTED — documented in the Results row and the #142 comment; no edit.

### F3 — The log command of 3.2 shows nothing after a documentation-only push

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `context/deployment/deploy-plan.md:2228` (S-32 PR-env verification, step 1)
- **Detail**: After the epilogue push (`489e6b8`, docs only) Railway skipped the API's deploy ("No deployment needed - watched paths not modified"), and `railway logs -s szczur-radar-api -e szczur-radar-pr-160 --lines 3000` now prints 0 lines; with the deployment's ID (`… d2e72472-3ffb-4d8c-93fb-37a7b8ea21eb`) it prints the 165 lines checked. Rerunning the step as written gives an empty log that looks clean. The production runbook (step 3, after a merge that deploys the API) is not affected.
- **Fix**: In step 1, name the deployment: `railway logs -s szczur-radar-api -e szczur-radar-pr-160 --lines 3000 <deployment-id>` (the ID in the Results row), with a half-sentence on why.
- **Decision**: FIXED — `deploy-plan.md` S-32 PR-env verification, step 1: the log command names the run's `deployment_id`, with the reason.

## Triage summary

- Fixed: F1 (fixed differently + rule), F3 (2)
- Rule: F1 — „W PR env agent sam zakłada jednorazowe konto admina — link i hasło tylko w zmiennych powłoki” (1)
- Accepted: F2 (1)
- Skipped: none
