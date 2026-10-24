<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-13: dni bez danych w /admin

- **Plan**: context/changes/missed-night-detection/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-24
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 5 observations

Zakres: `git diff origin/main...HEAD` (24 pliki, commity `06854b5`…`723c2a6`) po `git fetch` 2026-10-24. Kryteria automatyczne uruchomione ponownie na `723c2a6`:
- `./mvnw -B verify`: 773 unit + 245 IT, BUILD SUCCESS;
- `npm test`: 26 plików, 415 testów;
- `npm run build`: OK.

Ostatni zielony CI na PR to `bf55e5c`. Wiersze Manual 2.3, 3.2 i 3.3 mają dowody w issue #72 (komentarz 6386051848).

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | WARNING |

## Findings

### F1 — PR #106 w konflikcie z main po S-11; CI nie rusza na `723c2a6`

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Success Criteria
- **Location**: N/A (PR #106; `REPOSITORY_GUIDELINES.md`, `context/deployment/deploy-plan.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`, `tools/render_roadmap_svg.py`)
- **Detail**:
  - Po zmergowaniu S-11 (#103, `ed34514`) `gh pr view 106` daje `mergeable: CONFLICTING`.
  - `git merge-tree` pokazuje konflikty treści w 4 plikach dokumentacji i skryptu SVG. Pliki kodu (`PipelineRunRepository.java`, `mock-api.js`, `pipeline.ts`) scalają się automatycznie.
  - GitHub nie uruchamia `ci.yml` dla PR w konflikcie: ostatni run to `bf55e5c`, dla `723c2a6` nie ma żadnego.
  - Bramka 3.1 była zielona na `dd554fa`, ale nie obejmuje kodu S-11 razem z tą zmianą.
- **Fix**: Zmergować `origin/main` do gałęzi i rozwiązać konflikty:
  - zachować sekcje S-11 i S-13 w `deploy-plan.md` oraz wpisy obu zmian w `REPOSITORY_GUIDELINES.md` i `pipeline/CLAUDE.md`;
  - w `render_roadmap_svg.py` przeliczyć statusy (S-11 i S-13) i wygenerować SVG od nowa.
  
  Potem lokalnie `./mvnw -B verify` i `npm test` (S-11 zmienił `PipelineRunRepository` i `runs.ts`), push i zielony CI.
  - Strength: przywraca bramkę CI na kodzie, który faktycznie trafi na `main`; konflikty są tylko w dokumentach.
  - Tradeoff: jeden commit merge'a i ponowny CI (~3 min) oraz redeploy API w PR env.
  - Confidence: HIGH — `merge-tree` nie pokazuje konfliktów w kodzie.
  - Blind spot: semantyczna kolizja S-11 z `findCoveringSince`/`findFirstCoveredFrom` (S-11 zmienił statusy przebiegów, np. przerwanie utkniętego przebiegu) — wyjdzie dopiero w testach po merge'u.
- **Decision**: FIXED — merge origin/main w `c3d9137` (konflikty w 4 plikach dokumentów/skryptu SVG rozwiązane z zachowaniem S-11 i S-13, SVG wygenerowany); po merge'u `./mvnw -B verify` 776 + 260 i `npm test` zielone; CI po pushu.

### F2 — Brak testu jednostkowego „dwa źródła z różnymi lukami”

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/run/MissingDaysTest.java:80-96
- **Detail**:
  - Plan wymieniał przypadek dwóch źródeł. `Source` ma dziś tylko `WYKOP`, więc zastąpiły go dwa inne przypadki: przebieg bez źródła nie pokrywa dnia i źródło nieskonfigurowane.
  - Kolejność źródeł w dniu wynika z `EnumSet` (`MissingDays.java:91`), czyli jest poprawna z konstrukcji.
  - Front testuje kilka źródeł na dzień (`missingDays.test.ts:21-34`).
- **Fix**: Dopisać przypadek dwóch źródeł razem z drugim źródłem (Reddit); dziś nic.
- **Decision**: FIXED (bez zmiany w kodzie dziś) — przypadek dwóch źródeł dopisać razem z drugim źródłem; w `follow-ups/review-fixes.md`.

### F3 — `MissingDaysIT` z `RUNNING` zależy od braku zatwierdzonego przebiegu `running` w bazie

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/MissingDaysIT.java:83-90,101
- **Detail**:
  - Przypadek sparametryzowany statusem `RUNNING` zapisuje przebieg `running` w transakcji testu.
  - `setUp` zeruje tylko `succeeded`/`partial`. Gdyby inny IT w tym samym kontekście zostawił zatwierdzony przebieg `running`, unikalny indeks `uq_pipeline_runs_one_running` wywróci ten przypadek.
  - Dziś przechodzi.
- **Fix**: W `setUp` przestawić też przebiegi `running`/`queued` (np. na `interrupted`), w tej samej wycofywanej transakcji.
- **Decision**: FIXED — `setUp` przestawia też zatwierdzone `queued`/`running` na `interrupted` w wycofywanej transakcji testu.

### F4 — `PipelineRunWithoutSourceIT` liczy okno z prawdziwego zegara

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunWithoutSourceIT.java:86-100
- **Detail**:
  - Test wylicza `today` z prawdziwego `Clock`, a kontroler robi to drugi raz.
  - Uruchomiony dokładnie na granicy północy w Warszawie porówna inne `from`/`to`. To teoretyczny flake.
- **Fix**: Sprawdzać tylko `days == []` (sedno przypadku) albo podać stały `Clock` jak w `MissingDaysIT`.
- **Decision**: FIXED — asercja tylko na `$.days` puste (`extractingPath(...).asArray().isEmpty()`).

### F5 — Kontroler składa przypadek użycia z dwóch osobnych odczytów

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java:139-155
- **Detail**:
  - `missingDays()` czyta pokrycia, potem pierwsze pokryte dni (jedno zapytanie na źródło), bez wspólnej transakcji.
  - Jeśli pierwszy w historii przebieg źródła skończy się między odczytami, jedna odpowiedź może pokazać jego dni jako brakujące. Następne odczytanie to poprawia.
  - Oba zapytania czytają całe `pipeline_runs` (jak `findLastUpdate`), co przy ~1 wierszu na dobę nic nie kosztuje.
  - Logika jest w czystym `MissingDays`, więc kontroler zostaje cienki.
- **Fix**: Zostawić. Przy drugim konsumencie (np. alarm z crona) przenieść składanie do usługi odczytu w `pipeline/`.
- **Decision**: FIXED (bez zmiany w kodzie dziś) — zostaje w kontrolerze; przenieść do usługi odczytu w `pipeline/` przy drugim konsumencie; w `follow-ups/review-fixes.md`.

### F6 — Brak testu odrzucenia nieaktualnej odpowiedzi `loadMissing`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/views/AdminView.vue:166-172,283
- **Detail**:
  - „Odśwież” może wysłać `loadMissing()` dwa razy: bezpośrednio i po wykryciu końca przebiegu w `loadRuns()`.
  - Strażnik `latestMissing` (kopia `latestRefresh`) odrzuca starszą odpowiedź, ale żaden test tego nie przypina. Lista przebiegów ma analogiczny test.
- **Fix**: Jeden test w `AdminView.test.ts` z dwoma odroczonymi odpowiedziami rozwiązanymi w odwrotnej kolejności.
- **Decision**: FIXED — test „keeps the newest answer when an older read of the days ends later” w `AdminView.test.ts`; bez strażnika `latestMissing` jest czerwony (sprawdzone i cofnięte).

## Triage (2026-10-24)

- Fixed: F1, F3, F4, F6 (4)
- Fixed bez zmiany dziś, odłożone do `follow-ups/review-fixes.md`: F2, F5 (2)
