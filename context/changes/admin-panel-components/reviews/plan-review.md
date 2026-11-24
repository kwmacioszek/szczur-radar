<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Panel admina w trzech częściach (S-50)

- **Plan**: context/changes/admin-panel-components/plan.md
- **Mode**: Deep
- **Date**: 2026-11-24
- **Verdict**: REVISE → SOUND po poprawkach
- **Findings**: 0 critical, 3 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

16/16 paths ✓, 9/9 symbols ✓ (pageFromQuery, restoreFocus, postOrigin, fetchFailedPosts/FAILED_PAGE_SIZE, retryPosts → Run[], restorePosts, MODE_VALUES, scalanie `to.meta` w strażniku, trasa przebiegu w atrapie z `started`), brief↔plan ✓, Progress↔Fazy ✓ (6/6 faz, 40/40 kryteriów). Twierdzenia zweryfikowane w kodzie inline, bez sub-agenta.

## Findings

### F1 — Fokus na „Panel admina” po każdej zmianie części to nowe zachowanie, nie dzisiejsze

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details („Fokus po zmianie części”), Faza 3 §3
- **Detail**: Plan pisał „tak jak dziś po wczytaniu widoku”, ale `AdminView` nie stawia fokusu na nagłówku (`PageHeader` bez `ref`, AdminView.vue:541). Robią to tylko FeedView i RunDetailView. Układ panelu się nie remontuje, więc skok na wspólny `h1` nad paskiem przy każdej zmianie części zabierał klawiaturę z klikniętego odnośnika i nie ogłaszał nic nowego.
- **Fix A ⭐ Recommended**: `focusTitle()` tylko raz na montaż układu; przy zmianie części fokus zostaje na klikniętym odnośniku.
  - Strength: klawiatura zostaje w pasku jak w zakładkach; wejście do panelu jak Feed i RunDetail.
  - Tradeoff: czytnik nie ogłasza treści nowej części, tylko zmianę `aria-current`.
  - Confidence: HIGH — układ zostaje zamontowany (plan sam to testuje).
  - Blind spot: None significant.
- **Fix B**: fokus na `h2` pierwszej sekcji części po wczytaniu danych.
  - Strength: czytnik ogłasza treść nowej części.
  - Tradeoff: fokus skacze za pasek; trzeba ustalić regułę remisu dla trzech sekcji „Przebiegów”.
  - Confidence: MED — wymaga reguły remisu.
  - Blind spot: zderzenie z fokusem „Wpisz do formularza” na „Od”.
- **Decision**: FIXED (Fix A) — Critical Implementation Details, Faza 3 §2 (`focusTitle()` raz na montaż układu + testy), Faza 3 §3, Faza 6 §1 (E2E: fokus zostaje na odnośniku).

### F2 — Test „błąd jednej sekcji nie psuje pozostałych” traci sens po rozbiciu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 3 §5 (`states.spec.ts`)
- **Detail**: `QUALITY: error` (states.spec.ts:110-147) sprawdzało dane pozostałych sekcji i ponowienie tylko jakości. Na `/admin/quality` jakość stoi sama, więc niezależności trzech sekcji „Przebiegów” nic nie sprawdzało w E2E; kontrakt `admin-error` wymaga też „pozostałe części działają”. Tryb `MISSING: 'error'` istnieje (mock-api.js:155), ale plan go nie używał.
- **Fix**: przypadek `MISSING: 'error'` na `/admin` (alert tylko w „Dni bez danych”, reszta z danymi, ponowienie tylko `GET …/missing-days`) oraz w `QUALITY: error` klik „Przebiegi” i dane tej części.
- **Decision**: FIXED — Faza 3 §5.

### F3 — Rozbicie tras dotyka testów spoza listy plików fazy 3

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 §1 i §5
- **Detail**: `router/index.test.ts:175` wymaga `matched` długości 2 (dzieci `/admin` mają 3); test kolumn (:189) nie schodzi do dzieci układu; `AppShell.test.ts:22` mockuje `../views/AdminView.vue`; `SCREENS` w `e2e/shell.spec.ts:55-60` nie ma `/admin/quality` i `/admin/posts`.
- **Fix**: wymienić te cztery miejsca w fazie 3.
- **Decision**: FIXED — Faza 3 §1 (plik `AppShell.test.ts`, asercje routera) i §5 (`SCREENS`).

### F4 — Pusty stan panelu bez asercji „bez licznika” i „Pokaż przebiegi”

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 §5 / Faza 6 §1
- **Detail**: kontrakt `admin-empty` (ui-design.md:592) wymaga pustego stanu bez licznika w pasku i z „Pokaż przebiegi”; żaden test E2E planu tego nie sprawdzał.
- **Fix**: test `MODE: 'empty'` na `/admin/posts`: brak tabeli, odnośnik w pasku dokładnie „Nieudane posty”, „Pokaż przebiegi” → `/admin`.
- **Decision**: FIXED — Faza 6 §1.

### F5 — „Jak ustalił F-08 dla `.subnav a`” nie wskazuje rozwiązania

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2 §1
- **Detail**: F-08 zapisał tylko „bez literału” (archive/2026-11-19-ui-design-system/reviews/impl-review.md:178); w `style.css` nie ma tokenu grubości linii, a `margin-bottom: -1px` (prototype.css:900) skan odrzuci.
- **Fix**: `border-bottom: 1px solid var(--line)` na `nav`, bieżąca część przez `box-shadow: inset 0 -2px 0 var(--accent)`, bez ujemnego marginesu.
- **Decision**: FIXED — Faza 2 §1.

### F6 — Post z Reddita trzeba dopiero zasiać w teście kontraktu

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 §3
- **Detail**: `aFailedPostsPageMatchesTheSharedContract` (PostsApiIT.java:298) sieje tylko posty z Wykopu; asercja posta z Reddita wymaga nowego elementu w pliku kontraktu i próbce.
- **Fix**: jeden `seeds.redditPost("stocks", PostStage.FAILED, 1, …)` (PostSeeds.java:81) z próbą `seeds.failed(…)` w tym teście.
- **Decision**: FIXED — Faza 1 §3.
