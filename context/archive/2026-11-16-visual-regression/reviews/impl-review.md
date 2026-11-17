<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: F-06: zrzuty porównawcze i test dostępności w CI

- **Plan**: context/changes/visual-regression/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-17
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | WARNING |

Kryteria automatyczne uruchomione ponownie 2026-11-17 na HEAD `7b748a0`:
- `npm run visual:docker`: 40 passed, 20 skipped (2.4m), bez `flaky`, porcelain czyste;
- `npm test`: 832 passed; `npx vitest list | grep -c visual/` = 0; `npm run build` zielone;
- strażnik środowiska na hoście: exit 1, komunikat z `visual:docker`;
- grep lokatorów pusty; `.dockerignore` ma 3 wpisy; 54 PNG (24 / 20 / 10);
- `Refs #177` w PR; grepy wytycznych i `verification-scope.md` zielone; artefakt `visual-baselines` w runie 43942015348.

Ręczny wiersz 3.6 odhaczony z dowodem (commit `7b748a0`, akceptacja właściciela).

## Triage (2026-11-17)

- Fixed: F1, F2, F5, F6, F7 (część 2), F8
- Resolved: F3 (bieg HEAD zielony)
- Dismissed: F4 (żaden wzorzec nie rysuje kroju mono), F7 część 1
- Po poprawkach: `npm run visual:docker` 40 passed / 20 skipped (1.5m), `npm test` 832 passed, grep lokatorów pusty. F5 i F8 sprawdzi dopiero bieg CI po pushu.

## Findings

### F1 — Próg różnicy 0 zamiast 0.2 nie zapisany w planie

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/playwright.visual.config.ts:50-52
- **Detail**: Plan (1.4, `plan.md:186`) i `plan-brief.md:42` mówią „domyślny threshold 0.2 i zero pikseli”. Kod ma `threshold: 0, maxDiffPixels: 0`. Powód jest dobry i zapisany w komentarzu, w commicie b99eae4 i w `REPOSITORY_GUIDELINES.md`: przy 0.2 złamanie hover przechodziło na zielono. Ale w folderze zmiany nie ma notatki o odchyleniu, a `visual-breaks.md` (wiersz 4) pokazuje tylko czerwony bieg przy 0, bez zielonego przy 0.2, który jest dowodem.
- **Fix**: Dopisać w `visual-breaks.md` (albo pod Fazą 2 w planie) notatkę o odchyleniu: próg 0, bo przy 0.2 złamanie 4 było zielone.
- **Decision**: FIXED — notatka o odchyleniu (próg 0, bo przy 0.2 złamanie 4 było zielone) w `visual-breaks.md`; plan bez zmian

### F2 — Ctrl-C nie przerywa lokalnego `visual:docker`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/scripts/visual-docker.sh:30-39
- **Detail**: `docker run` jest bez `-t`, więc SIGINT trafia do `--init` (tini), a ten przekazuje go tylko swojemu dziecku `bash -c`. Nieinteraktywny bash czekający na polecenie odkłada sygnał, więc npm i Playwright go nie dostają. Podagent odtworzył to w Dockerze: SIGINT po 5 s, a kontener i tak pracował pełne 21 s i zakończył się kodem 0. Przy biegu 2–5 min pod emulacją zostaje `docker kill`.
- **Fix**: Dodać `-e TINI_KILL_PROCESS_GROUP=1` (tini sygnalizuje całą grupę procesów; ten sam test kończył się kodem 130 po 5 s) i `trap` w wewnętrznym skrypcie, żeby `chown` wykonał się też po przerwaniu.
- **Decision**: FIXED — `-e TINI_KILL_PROCESS_GROUP=1` + `trap "give_back; exit 130" INT TERM` w `visual-docker.sh`; sprawdzone: SIGINT do klienta docker po 60 s → exit 130 po 2 s, 0 kontenerów, drzewo czyste

### F3 — Czerwony `mutation` na 4d0061a, bieg HEAD jeszcze trwa

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: CI run 43949206526 (commit 4d0061a); run 44038647589 (HEAD 7b748a0)
- **Detail**: Kryterium 3.5 („każdy job zielony”) sprawdzono na dbc7731. Na 4d0061a, który zmienia tylko `context/`, job `mutation` był czerwony. Wpis w `AcceptedMutants` (`IrrelevantHasNoContentRule::check`, `EmptyObjectReturnValsMutator`) nie przeżył, czyli mutant został zabity, przekroczył czas albo zniknął. Przez to `railway-config` był pominięty. Gałąź nie zmienia Javy, więc to niestabilność PIT (S-45), a nie wina tej zmiany. Bieg HEAD był w toku w czasie przeglądu (`web-e2e` zielony).
- **Fix**: Przed przekazaniem do merge potwierdzić zielony bieg 44038647589 (`gh pr checks 194`). Jeśli `mutation` znów jest czerwony, uruchomić go ponownie i założyć osobne issue o niestabilnym mutancie (poza F-06).
- **Decision**: RESOLVED — bieg 44038647589 (HEAD 7b748a0) zakończony `success`, w tym `mutation` i `railway-config`; niestabilny mutant PIT poza zakresem F-06

### F4 — Strażnik fontu nie sprawdza fontu mono

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/visual/font.spec.ts:41-42
- **Detail**: `fonts.conf` mapuje też `ui-monospace`/`monospace` → Liberation Mono, a `view-post` rysuje surowe dane krojem `var(--mono)` (`PostDetailView.vue:281`). Strażnik sprawdza tylko dwa węzły bezszeryfowe na `/login`. Jeśli przyszły obraz zgubi Liberation Mono, wzorce wygenerowane po podbiciu wersji cicho przejdą na inny krój mono.
- **Fix**: Trzecia próba na `/posts/<post 0>`: węzeł mono rysowany wyłącznie `Liberation Mono`.
- **Decision**: DISMISSED — założenie nieprawdziwe: jedyny tekst mono (`<pre>` w `PostDetailView.vue:185`) jest w zwiniętym `<details>` i wczytuje się leniwie; warunek gotowości `view-post` go nie rozwija, więc żaden wzorzec nie rysuje Liberation Mono. Strażnik mono ma sens dopiero, gdy wzorzec pokaże tekst mono

### F5 — Warunki kroków wizualnych w CI

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml (kroki „Run visual tests”, „Produce baselines for review”, „Keep the Playwright report”)
- **Detail**: Trzy drobne niedokładności kroków wizualnych:
  - (a) „Run visual tests” wymaga tylko `steps.install.outcome == 'success'`. Po czerwonej kontroli wersji obrazu krok i tak rusza, pada na braku przeglądarki i wgrywa bezużyteczny artefakt.
  - (b) `visual-baselines` powstaje przy każdym czerwonym kroku, także przy samym axe, strażniku fontu albo strażniku wzorców. Artefakt jest wtedy identyczny z repo, co może mylić przy procedurze „rozpakuj i przejrzyj”.
  - (c) Bieg aktualizujący czyści `test-results-visual/` (outputDir) przed wgraniem raportu, więc wgrany folder pochodzi z drugiego biegu. Dowody zostają w `playwright-report-visual/`.
- **Fix**: Dać kontroli wersji `id` i wymagać jej sukcesu w kroku wizualnym; bieg aktualizujący z `--output=test-results-visual-update`; w komentarzu zdanie, że artefakt równy repo oznacza błąd inny niż zrzut.
- **Decision**: FIXED — `id: version` dla kontroli wersji i `steps.version.outcome == 'success'` w warunku „Run visual tests”; bieg aktualizujący z `--output=test-results-visual-update`; zdanie w komentarzu o artefakcie równym repo. Weryfikacja tylko w CI po pushu

### F6 — Skrypt dockerowy: cytowanie ścieżki i brak cache npm

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/scripts/visual-docker.sh:12,31-32
- **Detail**: Dwie drobne wady skryptu:
  - `node -p "require('$module_dir/package.json')…"` wkleja ścieżkę do literału JS, więc apostrof w ścieżce go psuje. `-v "$module_dir:/work"` psuje się na dwukropku w ścieżce.
  - Wolumen `node_modules` niczego nie przyspiesza: `npm ci` zawsze go czyści, a cache `/root/.npm` znika z `--rm`. Każdy bieg pobiera paczki od nowa.
- **Fix**: Przekazać ścieżkę jako argument (`node -p 'require(process.argv[1])…' "$module_dir/package.json"`) i opcjonalnie montować nazwany wolumen na `/root/.npm`.
- **Decision**: FIXED — ścieżka do `package.json` przez `process.argv[1]` (sprawdzone na katalogu z apostrofem w nazwie); wolumen `szczur-radar-web-visual-npm-cache` na `/root/.npm` (ścieżka cache w obrazie potwierdzona: `npm config get cache`). Dwukropek w ścieżce modułu (`-v`) zostaje, to ograniczenie Dockera

### F7 — Przypadki brzegowe wyliczania sekcji i strażnika wzorców

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/visual/targets.ts:166-182, szczur-radar-web/visual/baselines.spec.ts
- **Detail**: Dwa przypadki brzegowe, dziś nieosiągalne:
  - Odejmowanie nagłówków `h2` z kart postów usuwa pierwszy pasujący tekst, więc karta o nagłówku równym nazwie sekcji usunęłaby sekcję. Ten krok wyliczania jest poprawną odchyłką od planu i opisują go wytyczne.
  - Strażnik porównuje tylko `*.png` w znanych katalogach projektów, więc plik leżący luzem w `__screenshots__/` albo plik inny niż PNG przechodzi.
- **Fix**: Odejmować nagłówki po tożsamości węzła, nie po tekście; strażnik liczy każdy wpis w `__screenshots__/`. Oba tanie, żadne nie jest pilne.
- **Decision**: FIXED (część 2) — strażnik zgłasza pliki luzem w `__screenshots__/` i każdy nieukryty plik w katalogu projektu; pliki ukryte (`.DS_Store`) pominięte. Złamanie: `loose.png` + `light-375/notes.txt` + `.DS_Store` → czerwony z dwoma problemami, `.DS_Store` pominięty. Część 1 (nagłówki kart) DISMISSED: `indexOf` po identycznym tekście daje tę samą listę nazw, więc te same slugi i regiony

### F8 — Wydłużone limity czasu działają też w CI

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/playwright.visual.config.ts:40-44
- **Detail**: `timeout: 90_000` i `expect.timeout: 20_000` są uzasadnione emulacją na arm64 („not in CI”), ale obowiązują też w CI. Tam opóźniają wykrycie zawieszenia; job i tak ma `timeout-minutes: 10`. Plan ich nie przewidywał.
- **Fix**: Uzależnić je od `CI` (domyślne w CI, wydłużone lokalnie) albo zostawić świadomie i dopisać to do komentarza.
- **Decision**: FIXED — `timeout: process.env.CI ? 30_000 : 90_000`, `expect.timeout: process.env.CI ? 5_000 : 20_000`; lokalnie bez zmian. Czy pełnostronicowe zrzuty mieszczą się w 5 s na runnerze, pokaże dopiero bieg CI
