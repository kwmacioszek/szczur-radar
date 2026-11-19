<!-- PLAN-REVIEW-REPORT -->
# Plan Review: F-07 Projekt UI/UX całej aplikacji (ui-design-system)

- **Plan**: `context/changes/ui-design-system/plan.md`
- **Mode**: Deep
- **Date**: 2026-11-19
- **Verdict**: REVISE → SOUND po triage (wszystkie ustalenia poprawione w planie)
- **Findings**: 0 critical · 7 warnings · 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | WARNING |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

10/10 ścieżek ✓, 7/7 symboli ✓ (`WCAG_TAGS` `visual/axe.ts:7`, regex hex `styleContract.test.ts:82`, strażnik fontu `font.spec.ts:31-43`, `font-synthesis` `style.css:58`, wysokość pól `style.css:170-175`, `ITEM_TYPE_WORDS` `labels.ts:92`, `post.spec.ts:23`), brief↔plan ✓, Progress↔Phase ✓ (4/4 fazy, 24/24 kryteria), lokalny krój przez `file://` w Chromium ✓ (sprawdzone uruchomieniem `chrome-headless-shell` na stronie z `@font-face` w katalogu nadrzędnym).

Weryfikacja kodu (jeden podagent): rozwiązywanie modułów ESM spoza `szczur-radar-web/` (sprzeczne z planem), reguła axe `target-size` w axe-core 4.14.1 (potwierdzona), promień rażenia nowych plików w `context/`, `docs/`, `tools/` (brak wpływu na CI, testy, Docker i Railway), wzorzec z S-46 (brak skryptu do ponownego użycia), deterministyczność `tools/render_roadmap_svg.py` (deterministyczny, ale nie czyta `roadmap.md`).

## Findings

### F1 — Faza 1 §1 nie zgadza się ze stanem gałęzi

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §1 „Start zmiany w repo”; kryteria „Kod aplikacji bez zmian” (1.6, 2.5, 3.6, 4.5); kryterium 1.7
- **Detail**: Kontrakt mówił „Gałąź stoi na świeżo pobranym `origin/main`”, a `feature/ui-design-system` istnieje już na origin jako `origin/main` (00cdc83) + commit roadmapy v6.1 (0ed2537), którego nie ma w `main`; dosłowne wykonanie oznaczało odtworzenie gałęzi i utratę v6.1. `git diff --quiet origin/main -- …` porównywał z ruchomym czubkiem `main`, więc PR webu zmergowany w międzyczasie dawał fałszywą czerwień. Kryterium 1.7 nie łapało rozjazdu `roadmap.md` ↔ dane skryptu SVG (skrypt ma dane wpisane ręcznie i nie czyta `roadmap.md`).
- **Fix**: Kontrakt opisuje istniejącą gałąź i commit v6.1 w PR; po `git fetch` rebase zamiast odtwarzania; sprawdzenie względem `git merge-base origin/main HEAD`; w 1.7 dodatkowy `grep -q '"F-07": (.*"progress"' tools/render_roadmap_svg.py`.
- **Decision**: FIXED

### F2 — Zwykły import nie załaduje Playwrighta i axe z `szczur-radar-web/node_modules`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §2 „Narzędzie kontroli projektu”
- **Detail**: Import bez ścieżki w ESM szuka pakietów w górę od folderu skryptu i nie trafia do `szczur-radar-web/node_modules` (folder obok); główny `package.json` (`szczur-radar-iac`) ma tylko `railway`, a `NODE_PATH` nie działa dla ESM. Podagent potwierdził `MODULE_NOT_FOUND`. Bezpośrednie zależności modułu web to tylko `@playwright/test` 1.65.1 i `@axe-core/playwright` 4.14.0.
- **Fix**: `createRequire(new URL('../../szczur-radar-web/package.json', import.meta.url))`, potem `chromium` z `@playwright/test` i `AxeBuilder` z `@axe-core/playwright` (CJS, bez `.default`); wyłącznie bezpośrednie zależności.
- **Decision**: FIXED

### F3 — Narzędzie kontroli projektu leży w folderze, który `/10x-archive` przenosi

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Phase 1 §2, Phase 1 §3 („Zasady i standardy”: sposób sprawdzenia), Phase 4 §3
- **Detail**: `ui-design.md` i jego zasoby to żywy dokument, a wytyczne mają mówić, że zmiana projektu to jego edycja z akceptacją właściciela. Jedyne narzędzie kontroli (`design-check.mjs`) leżało w folderze zmiany, więc po `/10x-archive` trafiłoby do `context/archive/` z domyślnymi ścieżkami do `context/changes/`; „sposób sprawdzenia” i linki z dokumentu do `context/changes/` przestałyby działać, a `doc --stage 4` sprawdza je przed archiwizacją.
- **Fix A ⭐ Recommended**: skrypt z README w `context/foundation/ui-design/tools/`, obok tego, co sprawdza; zakaz linków z `ui-design.md` do `context/changes/`.
  - Strength: trwały dokument wskazuje trwałą ścieżkę; materiał roboczy zostaje w folderze zmiany.
  - Tradeoff: kod w `context/foundation/` to tam nowość.
  - Confidence: HIGH — zmiana ścieżek w planie i w poleceniach kryteriów.
  - Blind spot: reguła o linkach musi wejść do kontroli `doc`.
- **Fix B**: narzędzie repo `tools/design-check/` z README, jak inne narzędzia.
  - Strength: zgodne z konwencją `tools/*`.
  - Tradeoff: inne narzędzia mają `test.sh` w CI; bez niego wyjątek, z nim nowe zadanie CI.
  - Confidence: MED — nie wiadomo, czy właściciel chce narzędzia projektu w CI.
  - Blind spot: utrzymanie po M-5 nierozstrzygnięte.
- **Decision**: FIXED via Fix B — `tools/design-check/` (skrypt, README, `fixtures/`), bez zadania w CI jak `tools/ci-runner/` i `tools/render_roadmap_svg.py`; dodatkowo kontrola `doc` zabrania linków z `ui-design.md` do `context/changes/`, a faza 4 dopisuje narzędzie do „Project Structure” w `REPOSITORY_GUIDELINES.md`.

### F4 — Własna kontrola 24×24 px dubluje axe i jest ostrzejsza niż brama w CI

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Phase 1 §2, podpolecenie `pages`
- **Detail**: axe-core 4.14.1 (z `@axe-core/playwright` 4.14.0) ma regułę `target-size` z tagami `wcag22aa`/`wcag258`; ma `enabled: false`, ale `runOnly` po tagach ją uruchamia, więc `visual/axe.ts:7` sprawdza ją już dziś w CI. Reguła obejmuje wyjątek odstępu (`target-offset`) i linków w tekście (`widget-not-inline-matches`). Własna kontrola z planu nie miała wyjątku odstępu ani określonej reguły „linków w zdaniu”.
- **Fix**: Usunąć własną kontrolę; w „Zasadach i standardach” zapisać, że WCAG 2.5.8 sprawdza reguła axe `target-size`, tak samo jak w CI.
- **Decision**: FIXED

### F5 — Standard obiecuje reflow przy 320 px, a kontrola działa przy 375 px

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Phase 1 §3 („Zasady i standardy”) vs Phase 1 §2 `pages`
- **Detail**: Dokument deklaruje WCAG 1.4.10 (brak przewijania w poziomie przy 320 px CSS), a `pages` sprawdzał tylko 1280 i 375 px, więc deklarowana reguła nie miała dowodu.
- **Fix**: `pages` sprawdza też 320 px w obu motywach (axe i brak przewijania w poziomie, bez zrzutów).
- **Decision**: FIXED

### F6 — Dowód celowego złamania skryptu poza Progress, a parser może rozjechać się z testem aplikacji

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Testing Strategy vs Phase 1 Success Criteria
- **Detail**: „Skrypt jest sprawdzany na celowym złamaniu” było tylko w Testing Strategy, bez kryterium i wiersza Progress. Twierdzenie, że tokeny przechodzące skrypt przejdą test aplikacji, opierało się na kopii parsera `styleContract.test.ts`, bez dowodu, że kopia czyta plik tak samo.
- **Fix**: Kryteria 1.8 (`design-check.mjs selftest` na próbkach w `tools/design-check/fixtures/broken/`: rola o za niskim kontraście, rola nie-hex, `prefers-color-scheme`, element szerszy niż 375 px, przycisk bez nazwy) i 1.9 (`contrast` na dzisiejszym `szczur-radar-web/src/style.css` z 19 parami `PAIRS` w `fixtures/app-pairs.json`, tylko odczyt); ręczna akceptacja wariantu przesunięta na 1.10.
- **Decision**: FIXED

### F7 — Bramkę przeglądu heurystycznego zamyka implementujący

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 §2, kryterium 3.4
- **Detail**: Przegląd ma być niezależny, bo właściciel nie oceni standardów, ale ustalenia blokujące poprawiał i odnotowywał implementujący, a 3.4 sprawdza tylko wiersz `**Otwarte blokujące:** 0`.
- **Fix**: Ustalenie blokujące zamyka wyłącznie agent przeglądu w drugim przejściu po poprawionych prototypach; tylko on zmienia stany i wiersz „Otwarte blokujące”, każde przejście jest osobną sekcją z datą.
- **Decision**: FIXED

### F8 — Gęstość pikseli zrzutów nieokreślona, a krój właściciel wybiera z PNG

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 §4 (18 zrzutów wariantów), Performance Considerations
- **Detail**: Na bramce fazy 1 właściciel ocenia kroje i gęstość tylko na zrzutach; zrzut 1× na ekranie o wysokiej gęstości rozmywa tekst, a zrzut 2× jest około 4 razy większy wobec budżetu 15 MB. Plan nie rozstrzygał żadnej z opcji.
- **Fix**: `deviceScaleFactor: 2` dla 18 zrzutów wariantów (kadr okna), 1 dla zrzutów całych stron w fazie 3; opcja `--scale` w `shots`; zapisane w kontrakcie i budżecie.
- **Decision**: FIXED

### F9 — Par z progiem 3:1 nie da się dopisać do dzisiejszego `PAIRS` bez zmiany testu

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 §1 (`pairs.json`), Phase 4 §1 (zmiany w bramach)
- **Detail**: `styleContract.test.ts:26` ma jeden `MIN_CONTRAST = 4.5` dla wszystkich par, więc pary obramowań i fokusu z progiem 3:1 po dopisaniu byłyby czerwone; lista zmian w bramach z fazy 4 tego nie wymieniała.
- **Fix**: W fazie 2 §1 i fazie 4 §1: F-08 daje testowi próg osobno dla każdej pary (4,5 tekst, 3,0 elementy interfejsu i fokus).
- **Decision**: FIXED

## Triage

| Decyzja | Ustalenia |
|---------|-----------|
| Fixed | F1, F2, F3 (Fix B), F4, F5, F6, F7, F8, F9 |
| Skipped | — |
| Accepted | — |
| Dismissed | — |

Werdykt po poprawkach: REVISE → SOUND. Poprawki objęły też `plan-brief.md` (miejsce skryptu kontroli, F3).
