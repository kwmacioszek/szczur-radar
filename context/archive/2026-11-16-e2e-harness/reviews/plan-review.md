<!-- PLAN-REVIEW-REPORT -->
# Plan Review: F-05: testy w przeglądarce w CI na atrapie API

- **Plan**: context/changes/e2e-harness/plan.md
- **Mode**: Deep
- **Date**: 2026-11-16
- **Verdict**: REVISE (po poprawkach: SOUND)
- **Findings**: 0 critical, 4 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

Grounding: 11/11 paths ✓, 8/8 symbols ✓, brief↔plan ✓, Progress↔Fazy ✓ (4 fazy, 20 wierszy).

Sprawdzone twierdzenia planu:

- `@playwright/test` 1.65.1 (npm, 2026-11-13) i obraz `mcr.microsoft.com/playwright:v1.65.1-noble` są opublikowane.
- `playwright-cli` 0.1.22 woła funkcję `run-code` z jednym argumentem `page`; plik atrapy ma 53 kB.
- `rules.test.ts` nie zależy od linii stałych trybów (`:96,100` i skan `detail: '…'`).
- Z 20 asercji testów sprawdzonych względem kodu SPA i danych atrapy 18 się zgadza; rozjazdy są w F4.
- Filtr `orlen` + `bullish` daje N = 20 (tylko wariant 5), wszystkie na stronie 0.

## Findings

### F1 — Stały zegar wyłącza ponowne odczyty dzwonka

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details („Zegar w dwóch miejscach”)
- **Detail**: Plan twierdził, że przy `setFixedTime` „polling SPA chodzi jak w produkcji”. Dzwonek pomija odczyt, gdy `Date.now() - notificationsReadAt < FRESH_MS` (`szczur-radar-web/src/notificationsStore.ts:60`), więc przy zamrożonym `Date` czyta listę raz i nigdy więcej. Testy F-05 na tym nie padają (licznik 5 → 4 to lokalne odjęcie), ale reguła „zegar stały” trafia do wytycznych, a S-50 przebuduje dzwonek.
- **Fix**: Zostawić `setFixedTime` jako domyślny, poprawić zdanie w planie i dopisać do punktu E2E w wytycznych regułę: test logiki liczonej z różnicy `Date` przesuwa czas kolejnym `page.clock.setFixedTime`.
  - Strength: daty formularza, okna D-n i przyszłe zrzuty F-06 zostają deterministyczne.
  - Tradeoff: harness domyślnie różni się od produkcji w jednym miejscu.
  - Confidence: HIGH — to jedyna logika różnicy `Date` w SPA.
  - Blind spot: nie sprawdzono w przeglądarce, że drugie `setFixedTime` odblokowuje odczyt dzwonka.
- **Decision**: FIXED

### F2 — Ponowienie w CI ukrywa niestabilny test

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 — `playwright.config.ts`; Faza 4 — job `web-e2e`
- **Detail**: Brief zapisuje decyzję „flaky test się naprawia, nie obchodzi”, a konfiguracja ma `retries: 1` w CI i raport tylko przy `failure()`. Test, który przechodzi w ponowieniu, dawał zielony job bez śladu.
- **Fix**: `failOnFlakyTests: !!process.env.CI` (opcja jest w typach 1.65.1).
- **Decision**: FIXED

### F3 — Kontener jako root na rezerwowej puli runnerów

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Key Discoveries; Faza 4 — job `web-e2e`
- **Detail**: Plan uzasadniał kontener tym, że „działa na obu pulach”. Runner self-hosted działa jako `gha` (`tools/ci-runner/setup.sh:90`) i jest trwały, a obraz Playwrighta działa jako root, więc zostawia w katalogu roboczym pliki roota. Następny job może paść na sprzątaniu w `actions/checkout`. Pula jest dziś wyłączona.
- **Fix**: Ostatni krok joba z `if: always()` oddaje katalog roboczy właścicielowi (`chown -R`), a Key Discoveries mówi „niesprawdzone na puli”.
  - Confidence: MEDIUM — znany problem jobów kontenerowych na trwałych runnerach; nieodtworzony tutaj.
- **Decision**: FIXED

### F4 — Szczegóły kontraktu testów niezgodne z kodem

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 (strażnik), Faza 3 (auth, feed)
- **Detail**: (1) Po wylogowaniu `goto('/')` daje `/login?redirect=/`, nie `%2F` (`src/router/index.test.ts:36`). (2) Oba przyciski „Wyczyść filtry” są w `main` (`FeedFilter.vue:75`, `FeedView.vue:193`), więc zawężenie do `main` łamie tryb strict. (3) `NOW` strażnika musi zostać liczbą (`mockApi.test.ts:164-165`). (4) Wywołań `installMock(` jest 27, nie „około 50”; `it.each` z `{ QUALITY: quality }` (`:835-836`) i alias `Constants` (`:469`) wymagają dopasowania typów.
- **Fix**: Poprawić te cztery miejsca w Contract faz 1 i 3.
- **Decision**: FIXED. Dodatkowo do listy pułapek lokatorów weszły `getByLabel("Do")` na `/admin` i 2 checkboxy źródeł obok checkboxów nieudanych postów.

### F5 — Roadmapa zmieniona bez SVG; mapa repo się zestarzeje

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 (pierwszy commit)
- **Detail**: `roadmap.md` ma w drzewie roboczym F-05 `planning`, a `tools/render_roadmap_svg.py:230` nadal `ready`. Wytyczne każą przy każdej zmianie roadmapy poprawić dane skryptu i wygenerować `docs/visuals/roadmap.svg`. `context/map/repo-map.md:141,268` opisuje „8 stałych trybu” i podmianę tekstu.
- **Fix**: Dopisać do fazy 1 aktualizację skryptu i SVG w pierwszym commicie; mapę repo zostawić do następnego `/10x-repo-map`.
- **Decision**: FIXED

### F6 — Bramka 3.6 w środku planu a bieg bez nadzoru

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 — Implementation Note; kryterium 4.3
- **Detail**: Notatka kazała zatrzymać się na akceptację przed fazą 4, a `/10x-goal-implement` nie zatrzymuje się na wierszach Manual. Kryterium 4.3 ma wpisane „18 passed”.
- **Fix**: Przeredagować notatkę: stop pod `/10x-implement`; pod `/10x-goal-implement` wiersz 3.6 na liście końcowej, akceptacja przed merge'em, zmiana listy zmienia liczbę w 4.3.
- **Decision**: FIXED
