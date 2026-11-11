# S-35 Phase 3 — section "Jakość ocen (7 dni)" on /admin

Taken 2026-11-11 by the agent with `playwright-cli` (Chromium) on `vite` + `szczur-radar-web/dev/mock-api.js`
(copies with `QUALITY` = `data` / `few` / `none` / `error`, and `MODE = 'loading'`). Each PNG is a screenshot of the
section element only (`section[aria-labelledby="quality-title"]`), in both themes (`set-color-scheme light|dark`) at
1280 px and 375 px. File name: `quality-<variant>-<theme>-<width>.png`. At 375 px the page has no horizontal scroll
(`scrollWidth` 375 = `innerWidth`).

Retaken 2026-11-11 after review F2 (new wording "próby z wywołaniem modelu (bez prefiltra), z ponowieniami"), on the mock at commit d14f053; the `error` and `loading` shots came out byte-identical (that line is not shown in those states).

| State | Screenshots | Notes |
| --- | --- | --- |
| default | `quality-data-{light,dark}-{1280,375}.png`, `quality-few-{light,dark}-{1280,375}.png` | `data`: Wykop `w normie` (success), Reddit `ponad progiem` (warning); `few`: Reddit `za mało prób` (neutral). Window, share of failed attempts with the number of attempts, class shares with their labels, threshold. |
| hover | N/A | The section has no interactive element: text and `StatusBadge` only (no link, button or field). |
| focus-visible | N/A | Nothing in the section is focusable (checked: no `a`, `button`, `input` or `[tabindex]`). |
| disabled | N/A | No control in the section; read only. |
| error | `quality-error-{light,dark}-{1280,375}.png` | 500 → `StatusMessage` tone error (`role="alert"`): "Nie udało się wczytać jakości ocen." |
| empty | `quality-none-{light,dark}-{1280,375}.png` | `sources: []` → window line and "Brak ocen w ostatnich 7 dniach." |
| loading | `quality-loading-{light,dark}-{1280,375}.png` | Request hangs → "Wczytywanie…", the section has `aria-busy="true"`. |
