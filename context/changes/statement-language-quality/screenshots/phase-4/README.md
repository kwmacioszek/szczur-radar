# Phase 4 — the "Jakość ocen (7 dni)" section of `/admin` (S-36)

`playwright-cli` (Chromium) on `npm run dev` with `szczur-radar-web/dev/mock-api.js`; each file is the section alone,
copies of the mock with `QUALITY` (or `MODE`) replaced. Widths 1280 px and 375 px, `set-color-scheme light|dark`.

| File | Mock | Theme | Width | What it shows |
| --- | --- | --- | --- | --- |
| `data-light-1280.png` | `QUALITY = 'data'` | light | 1280 | both measures: Wykop `w normie` / `w normie` (language 0,3% of 3476 theses, threshold 2%), Reddit `ponad progiem` / `ponad progiem` (31,0% of 2857, threshold 30%); the window names 100 attempts and 500 theses |
| `data-dark-1280.png` | `data` | dark | 1280 | the same |
| `data-light-375.png` | `data` | light | 375 | the same, wrapped |
| `data-dark-375.png` | `data` | dark | 375 | the same, wrapped |
| `few-light-1280.png` | `QUALITY = 'few'` | light | 1280 | Reddit's language below `minTheses`: `… 17,5% z 120 tez · próg 30%` with `za mało danych` (neutral); the share shows below the minimum as in the failed attempts' row (review F6, retaken 2026-11-13) |
| `few-dark-375.png` | `few` | dark | 375 | the same |
| `nolang-light-1280.png` | `QUALITY = 'nolang'` | light | 1280 | `language: null`: "tezy w innym języku niż post: nie udało się policzyć" without a badge, the failed attempts unchanged |
| `nolang-dark-1280.png` | `nolang` | dark | 1280 | the same |
| `nolang-light-375.png` | `nolang` | light | 375 | the same |
| `empty-light-1280.png` | `QUALITY = 'none'` | light | 1280 | no source in the window: "Brak ocen w ostatnich 7 dniach." |
| `empty-dark-375.png` | `none` | dark | 375 | the same |
| `error-light-1280.png` | `QUALITY = 'error'` | light | 1280 | 500: "Nie udało się wczytać jakości ocen." (`role="alert"`) |
| `error-dark-375.png` | `error` | dark | 375 | the same |
| `loading-light-1280.png` | `MODE = 'loading'` | light | 1280 | "Wczytywanie…" while the API hangs |

## The 7 states

| State | Screenshot or N/A |
| --- | --- |
| default | `data-*.png`, `few-*.png`, `nolang-*.png` |
| hover | N/A: the language row is text and a `StatusBadge`; nothing in the section reacts to the pointer |
| focus-visible | N/A: the section has no focusable element (the "Odśwież" button that reads it again sits in "Ostatnie przebiegi", unchanged) |
| disabled | N/A: the section has no control to disable |
| error | `error-*.png` (the route's 500); the language measure's own failure is `nolang-*.png` |
| empty | `empty-*.png` |
| loading | `loading-light-1280.png` |
