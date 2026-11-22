<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-49: logowanie i aktywacja na wspólnym fundamencie

- **Plan**: context/changes/login-on-shared-foundation/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-22
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

### Evidence

- **Plan adherence.** Every planned item of phases 1–4 is MATCH, and nothing is MISSING. There are three small justified drifts:
  - The location class in the kitchen sink is `.wide`, not `.field.wide`, because the root of `FormField` is `.form-field`.
  - The "Marka" section shows one `AppBrand` with a note instead of a wide and a narrow copy: the size is set by a media query, so one viewport cannot show both.
  - `ActivateView.submit` sets `submitting` before the local validation and resets it after. The button is busy for one tick and is never painted that way, and the order also guards against a double submit.
- **EXTRA changes, all harmless:**
  - `AuthLayout.vue:85-89` resets the margins of a `p` in the card, with a test.
  - `.note` in `LoginView.vue` has `--font-size-sm`.
  - Roadmap, SVG and guidelines changes from the triage of the phase 4 review.
- **Boundaries of "What We're NOT Doing".** Nothing changed in `src/api`, `src/auth/session.ts`, the router, `src/contract` or `ui-design.md`. No label or message text changed, and no new `STATES` were added.
- **Safety.**
  - The activation token is read only from the URL fragment and removed from the address in `onMounted`. It never reaches `history.state`, the DOM or a log.
  - `safeRedirect` is unchanged and applied on login.
  - There is no `v-html`.
  - `useId` gives unique ids; `aria-describedby` names only rendered ids.
- **Baselines.** There are exactly 18 baseline files, as the plan says, and `kitchen-sink-pola` / `state-pola-*` have no changed pixels:
  - 8 changed: `view-login`, `view-activate`.
  - 8 new: `view-login-error`, `view-activate-error`.
  - 2 new: `kitchen-sink-marka`.
- **Automated criteria, run in this session:**
  - Vitest: 36 files, 1090 tests, plus the component and view tests (54).
  - `npm run build`.
  - `npm run e2e`: 27 passed.
  - `design-check app`: OK, 73 pairs.
  - `npm run visual:docker`: 64 passed, with no baseline changed.
  - The greps for `login-form` and the dead styles are clean.
  - The guidelines grep is clean.
  - The PR body has `Refs #181` and the five baseline groups.
  - CI is green on 8a0d5e3.
- **Manual criterion.** 4.11 has the owner's acceptance in Progress, and the four PR-env screenshots are in `evidence/`.
- **Phase 4 report.** `reviews/impl-review-phase-4.md`: F1 and F2 are FIXED (67e48bc, 1332edc), and F3 is DEFERRED to `follow-ups/review-fixes.md`.

## Findings

### F1 — Po kliknięciu „Ustaw hasło”/„Zaloguj się” i błędzie formularza fokus spada na `<body>`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/ActivateView.vue:91-96, :113-120; szczur-radar-web/src/views/LoginView.vue (submit)
- **Detail**:
  - The plan says `unavailable` "does not move the focus", and the comment on `focusProblem` claims "the focus stays". That holds only for a submit with Enter.
  - When the user clicks the button, `AppButton` `busy` sets `disabled` on the focused button, and Chromium moves the focus to `<body>`.
  - After `role="alert"` appears, a keyboard user is back at the top of the document. The same happens on login after 401/429/a generic error.
  - The behaviour is pre-existing: on `main` the button was already disabled while busy. But the new comment presents it as correct.
  - The unit test (`ActivateView.test.ts:190-196`) blurs first and submits with `trigger('submit')`, so it does not catch this.
- **Fix**: After `submitting = false`, when `document.activeElement` is `body`, put the focus back on the submit button, in the error paths of `ActivateView` (`unavailable`) and of `LoginView`. Fix the comment, and add an E2E test that clicks the button in the `ACTIVATION: 'error'`/`LOGIN: 'invalid'` mode and checks the focus.
- **Decision**: FIXED (Fix now) — `src/focus.ts` `restoreFocus` w obu widokach, tryb atrapy `ACTIVATION: 'unavailable'`, dwa testy E2E czerwone bez poprawki

### F2 — Komentarze w E2E opisują S-49 jako przyszłą pracę

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/e2e/activation.spec.ts:1-2; szczur-radar-web/e2e/auth.spec.ts:1-2
- **Detail**: "on the screen S-49 rebuilds" and "the behaviour S-47 (the shell) and S-49 (the login) rebuild" were true before this change. They no longer are for S-49; S-47 is still future work.
- **Fix**: Change to "on the screen S-49 rebuilt on the shared components" and "… S-47 (the shell) rebuilds and S-49 (the login) rebuilt".
- **Decision**: FIXED (Fix now) — oba komentarze w czasie przeszłym dla S-49
