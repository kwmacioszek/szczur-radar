<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-46: przełącznik motywu jasnego i ciemnego w nowej palecie

- **Plan**: context/changes/light-dark-theme-switch/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-17
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria checked 2026-11-17:
- `npm test`: 891 passed.
- `npm run build`: OK.
- styleContract: 38 pair cases.
- `npm run e2e`: 22 passed.
- No `prefers-color-scheme` in `style.css` or `REPOSITORY_GUIDELINES.md`.
- 8 PNG in `palette/`.
- PR #195 CI: 13/13 pass at ea6a82e, including web-e2e with the visual tests.
- Manual 2.6 and 3.7 are pending.

## Findings

### F1 — Status S-46 w roadmap.md niezacommitowany

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/roadmap.md:117, :202
- **Detail**: `HEAD` has S-46 as `proposed`. The `in-progress` status lives only in the worktree, because the file was already edited (`planning`) before the run started. Meanwhile `tools/render_roadmap_svg.py:232` and `docs/visuals/roadmap.svg` were committed with `progress` in 16016a4. The roadmap and its SVG disagree in the PR, which breaks the rule in REPOSITORY_GUIDELINES.md.
- **Fix**: Commit `context/foundation/roadmap.md` (S-46 → `in-progress`) on the branch and push it.
- **Decision**: FIXED (09552dc)

### F2 — initTheme bez ochrony przed błędem matchMedia

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/theme.ts:69-73, src/main.ts:7
- **Detail**: `initTheme()` runs before `mount`. `window.matchMedia(...)` and `addEventListener` have no try/catch, unlike the script in index.html. If either throws (an old webview, or an object without `addEventListener`), the whole SPA does not start because of a cosmetic feature. The risk is low with Vite's target.
- **Fix**: Wrap the `matchMedia` part of `initTheme` in try/catch, as the inline script does, and add a unit case where `matchMedia` throws.
- **Decision**: FIXED (5960005)

### F3 — Stan modułu theme.ts bez resetu dla testów

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/theme.test.ts:59-66, src/components/ThemeToggle.test.ts:7-10
- **Detail**: `auth/session.ts` (`resetSession`) and `notificationsStore.ts` (`resetNotifications`) export a reset for tests. `theme.ts` does not. The `afterEach` works around it by calling `initTheme()` on a stub, which leaves a listener and the previous `theme.value`. ThemeToggle.test.ts starts every test with a stored choice from `setTheme('light')`.
- **Fix**: Export `resetTheme()` marked "(tests)" (detaches the listener, sets `light`, removes the attribute) and use it in both test files.
- **Decision**: FIXED (1a29d36)

### F4 — E2E „wybór wygrywa także po zmianie systemu” może przejść bez zdarzenia change

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-web/e2e/theme.spec.ts:48-50
- **Detail**: `expect.poll(...).toEqual(LIGHT)` right after `emulateMedia` passes on its first sample, before the browser has dispatched the `change` event. The live-page part of the test therefore guards nothing. The reload part and the unit test do guard it.
- **Fix**: Before the assertion, wait (`expect.poll`) until `matchMedia('(prefers-color-scheme: dark)').matches === true`, then check the theme.
- **Decision**: FIXED (34df7a9)

### F5 — Faza 2 zmieniła 9 wzorców zamiast 2

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/visual/__screenshots__/{light,dark}-375 (8d88557)
- **Detail**: Criterion 2.5 expected only `kitchen-sink-tokeny.png`. "Przyciski", "Dzwoneczek" and 4 `state-przyciski-*` also changed, by one-pixel anti-aliasing after the taller Tokeny section. This is described in the run report and the PR body, but not in the plan. Phase 3 re-rendered all 56 anyway.
- **Fix**: No action. It is documented in PR #195.
- **Decision**: SKIPPED

### F6 — Nieaktualny komentarz o parach kontrastu w style.css

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/style.css:3-4
- **Detail**: The comment says the pairs are "every text role on --bg, and --on-accent on --accent". It leaves out `--surface` and `--highlight` (19 pairs).
- **Fix**: Update the sentence to cover the text roles on `--bg` and `--surface`, plus `--text-h`/`--text` on `--highlight`.
- **Decision**: FIXED (5d5c938)

### F7 — Lokator nagłówka „Logowanie” bez exact: true

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/e2e/theme.spec.ts:80
- **Detail**: The repo's locator rule asks for `exact: true`.
- **Fix**: Add `exact: true` to `getByRole('heading', { name: 'Logowanie' })`.
- **Decision**: FIXED (5d5c938)

### F8 — Brak synchronizacji motywu między kartami

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/theme.ts
- **Detail**: There is no `storage` listener. A choice made in tab A does not reach tab B, and tab B keeps following the system despite the stored choice until it reloads. The plan does not cover multiple tabs.
- **Fix**: Leave it out of scope and note it for S-47 (shell).
  - Strength: Keeps to the plan; the effect is cosmetic and goes away after a reload.
  - Tradeoff: An inconsistency between open tabs until a reload.
  - Confidence: HIGH — the plan says nothing about tabs, and the rule after a reload is correct.
  - Blind spot: How often users keep several tabs open.
- **Decision**: FIXED (758810f, beyond plan scope at owner's request)

### F9 — Wiersz Manual 2.6 nieodhaczony mimo zapisanej decyzji

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/light-dark-theme-switch/plan.md:331
- **Detail**: `palette.md` records the owner's choice (A, no corrections), but row 2.6 is still `[ ]`. The autonomous agent does not tick Manual rows. Row 3.7 is correctly pending.
- **Fix**: The owner ticks 2.6 (A chosen 2026-11-17).
- **Decision**: FIXED (2.6 ticked by owner decision)
