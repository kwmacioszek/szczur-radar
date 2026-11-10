<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Admin przywraca martwe posty do oceny (restore-dead-posts)

- **Plan**: context/changes/restore-dead-posts/plan.md
- **Scope**: Phase 3 of 3
- **Reviewed phases**: 3
- **Date**: 2026-11-09
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Evidence

- Phase 3 commit `d298688`: `context/deployment/deploy-plan.md` (+25, section S-33) and `plan.md` (Progress). No production code; nothing outside the plan.
- Every claim in the S-33 section was checked against the code and agrees with it: the restore UPDATE (`stored`, 0 attempts, technical failure cleared), the validation (1–100 ids, otherwise 400 `invalid_request`), the answer `{"restored": N}`, the format of the INFO line, `post_dead` still returned by `POST /api/admin/pipeline-runs`, a RETRY run queued before an admin's range when leftovers exist (`PipelineRunService.java:185`), S-34 being the daily digest (`roadmap.md:114`), and R-04 / Q-18 (`domain-distillation.md:188,337`).
- The status line follows the lesson that it must stay true after the merge ("delivered by PR #161; production verification and closure in issue #143").
- Automated checks re-run on 2026-11-09:
  - `gh pr checks 161`: 11/11 SUCCESS.
  - `railway logs … | grep "restored 1 of 1 dead post(s)"`: 1 line.
  - The SQL for 3.2 and 3.3 was recorded in issue #143 (comment 6735490499).
- Manual rows 2.4, 3.7 and 3.8 were confirmed by the owner in the conversation on 2026-11-09 (commit `fb045c6`).

## Findings

### F1 — In the #143 comment, the RETRY run is called the "second run", and the failed range run is not mentioned

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: issue #143, comment 6735490499 (step 6)
- **Detail**:
  - The request for the second run queued two runs: `05d30e1c` (kind `retry`, which assessed the restored post) and `6aaadbbd` (kind `range`).
  - The comment calls `05d30e1c` "Drugi przebieg 2026-11-07, Reddit". In fact it is the RETRY run that the plan expected ("sprawdza, że RETRY ocenił przywrócony post").
  - `6aaadbbd` ended `failed`: Arctic Shift answered HTTP 422 "Timeout. Maybe slow down a bit" after 3 attempts. This is an external error unrelated to S-33. It finished at 08:23, before the push, so the deploy did not interrupt it.
  - The evidence is correct, but the run's description is not precise.
- **Fix**: Add a short follow-up comment to #143 that names `05d30e1c` as the RETRY run and records the `6aaadbbd` failure as an external Arctic Shift error.
- **Decision**: FIXED — doprecyzowanie w #143, komentarz 6737563687

### F2 — The post-merge runbook leaves out the plan's check for posts restored by mistake

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:2193 (After the merge)
- **Detail**: The plan's intent for the read-only post-merge check includes "brak martwych postów przywróconych przez pomyłkę" (no dead posts restored by mistake). The section's three steps cover the INFO line and the post's assessment, but they never say to compare the ids in the log line with the post the admin chose to restore.
- **Fix**: In step 1, add that the ids in the INFO line are only the ones the admin chose (`restored N of M` with N = M = the selection).
- **Decision**: FIXED — zdanie o id w linii INFO dopisane w kroku 1 (deploy-plan.md:2191)

### F3 — The issue comment has the page snapshot as text, not the screenshot

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: issue #143, comment 6735490499 (step 3)
- **Detail**: Criterion 3.5 says "zrzut w komentarzu issue" (a screenshot in the issue comment). `gh` cannot upload an image, so the comment carries the accessibility snapshot of the page instead: the `status` message and the disabled buttons. The run report recorded this as an ADAPT. The PNG files are in the session scratchpad: `pr161-before-restore.png` and `pr161-after-restore.png`.
- **Fix**: Accept the text snapshot as evidence, or the owner drags the PNGs into the comment in the GitHub web UI.
- **Decision**: ACCEPTED — właściciel przyjął snapshot dostępności jako dowód (odnotowane w #143, komentarz 6737563687)
