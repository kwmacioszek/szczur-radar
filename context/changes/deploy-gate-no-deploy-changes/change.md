---
change_id: deploy-gate-no-deploy-changes
title: Zmiana IaC bez deployu nie blokuje wdrożeń (S-21)
status: impl_reviewed
created: 2026-10-27
updated: 2026-10-28
archived_at: null
---

## Notes

Dotyczy S-21 z roadmapy z milestone M-3

### Deviations from the plan (impl-review F6, 2026-10-28)

- Phase 2, change 0: the fallback (README lines + close/reopen PR) was triggered by Postgres in the PR env ending `FAILED` without a volume, not by the CLI refusing (`02cddd79`).
- Phase 2, change 1: `checkSuites: false` set with `railway environment edit` (stdin JSON) planned as `source.checkSuites (null → true)`; the gate's `(false → true)` path is covered only by `tools/deploy-gate/test.sh`.
- Phase 2, 2.4: the first attempt missed the window; the retry (Wait for CI off again in the PR env, empty commits `b0ab3fe` and `4637f86`, the branch moved through the GitHub API) reproduced the parallel push live (`ce7e4e80`).
- Commit `e52ea3a` says "Pushed inside the check window", but it landed after the window (10:03:44); history is not rewritten.
