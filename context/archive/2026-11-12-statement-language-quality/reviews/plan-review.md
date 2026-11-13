<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Jakość ocen per źródło — tezy w innym języku niż post (S-36)

- **Plan**: context/changes/statement-language-quality/plan.md
- **Mode**: Deep
- **Date**: 2026-11-12
- **Verdict**: REVISE → SOUND po poprawkach (triage 2026-11-12: 7 z 7 FIXED)
- **Findings**: 0 critical, 5 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

Grounding: 18/18 paths ✓, 6/6 symbols ✓, brief↔plan ✓, Progress↔Phase ✓. Potwierdzone w kodzie bez zastrzeżeń: strażnicy nowej kolumny (`ClosedSetsIT`, `PostColumnsIT` — wystarczy wpis w `SET_ONCE`), brak testu zależności pakietów, ścieżka JSON `claims` → `statement`, brak zmian w rejestrze kontraktu i `KNOWN_GAPS`, brak selektorów CSS zależnych od nazwy stanu.

## Findings

### F1 — Odczyt tez może wyłączyć obie miary, a jego koszt jest niezmierzony

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 (zapytanie o tezy), Phase 3 (linia `unavailable`), Performance Considerations
- **Detail**: Plan trzyma oba odczyty w jednym `AssessmentQuality.window`, więc wyjątek z nowego zapytania zabiera także miarę S-35, która dziś działa niezależnie. W nocy obie linie zastępuje jedno `unavailable` (`NightSummary.java:174-187`), a `/api/admin/assessment-quality` nie ma żadnego catch (`AssessmentQualityController.java:39`), więc cała sekcja dostaje 500. Jeden wiersz z `claims` niebędącym tablicą wywraca `jsonb_array_elements` i trzyma ten stan 7 dni. Koszt zapytania (odczyt `output` ok. 3,5 tys. prób przy każdym wejściu na `/admin`) plan odkłada do runbooka po merge'u, choć da się go zmierzyć teraz. Kryterium 3.3 „`unavailable` przy wyjątku odczytu tez” nic nie dodaje: istniejący test podmienia całe `window()` (`InternalPipelineRunIT.java:407`).
- **Fix A ⭐ Recommended**: Jedna domena awarii zostaje, ale zapytanie nie może paść na danych, a jego koszt mierzymy przed implementacją
  - Strength: Mała zmiana: warunek, że `claims` jest tablicą (inaczej zero wierszy), jeden `EXPLAIN ANALYZE` na produkcji do `evidence/`, test ze szpiegiem repozytorium zamiast `window()`.
  - Tradeoff: Inna awaria odczytu tez (np. time-out) nadal ukrywa miarę S-35 w nocy i w `/admin`.
  - Confidence: HIGH — 35 dni produkcyjnych `output` przeszło ten sam odczyt bez błędu.
  - Blind spot: Pomiar wymaga jeszcze jednego odczytu produkcji tylko do odczytu, czyli zgody właściciela.
- **Fix B**: Osobne domeny awarii — błąd odczytu tez kosztuje tylko linie i wiersz języka
  - Strength: Miara S-35 nigdy się nie pogarsza przez S-36.
  - Tradeoff: Druga linia `unavailable`, pole `language` z wartością null w API, warianty w plikach kontraktu albo wpis `KNOWN_GAPS`, więcej testów.
  - Confidence: MEDIUM — wzorzec jest, ale wariantu „null” żaden IT nie wytworzy bez sztucznej awarii.
  - Blind spot: Jak sekcja `/admin` ma pokazać brak jednej miary.
- **Decision**: FIXED — Fix B (osobne rekordy i odczyt miary języka, `language: null` w API, trzeci plik kontraktu), z dodatkiem: zapytanie pomija `claims` niebędące tablicą

### F2 — Zapytanie natywne nie zwróci rekordu z enumami

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 2, zmiana 2 (Contract)
- **Detail**: Kontrakt mówi `List<PostStatement> findModelStatements(...)` jako zapytanie natywne z rekordem `(Source, Language, String)`. Hibernate wstawia do konstruktora surowe wartości JDBC i nie stosuje konwerterów, więc trzy teksty nie pasują do enumów. Repozytorium ma na to wzorzec: `List<Object[]>` plus metoda domyślna z `Source.fromValue` (`PostRepository.java:78-91`). Enumy w zapytaniach natywnych binduje się jako `.value()`. `Language` nie ma w planie `fromValue`.
- **Fix**: Kontrakt: natywne `List<Object[]>` + metoda domyślna mapująca na `PostStatement`; `Language.fromValue`; status i `result_source` jako `.value()`.
- **Decision**: FIXED — kontrakt zapytania: `List<Object[]>` + metoda domyślna, `Language.fromValue`, enumy jako `.value()`

### F3 — Faza 2 nie będzie zielona: zmiana rekordów łamie testy NightSummary

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2, zmiana 3; Phase 3, zmiana 1
- **Detail**: Nowe komponenty w `SourceQuality` i `QualityCrossing` łamią kompilację `NightSummaryTest` (15 wywołań konstruktorów) i `NightSummaryIT` (3). Plan wymienia te pliki dopiero w fazie 3, a faza 2 wymaga zielonego `./mvnw -B verify`. Między fazami `crossingText` wypisałby przejście języka słowami o nieudanych próbach (`NightSummary.java:392-415`).
- **Fix**: Faza 2 nazywa mechaniczną poprawkę wywołań w obu testach, a `NightSummary` do fazy 3 bierze do alarmu tylko przejścia `FAILED_ATTEMPTS`.
- **Decision**: FIXED — przez F1 Fix B: rekordy S-35 zostają bez zmian, więc faza 2 nie łamie testów `NightSummary`

### F4 — Lista testów z INSERT-ami jest błędna, a MigrationsIT przypina wersje

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1, zmiana 4; Key Discoveries
- **Detail**: `V13BackfillIT` wstawia posty przy schemacie V12 (`target("12")`, `:38`), więc nie może dostać `language`; niezmieniony przejdzie, bo V16 wypełni jego posty. `MigrationsIT.java:38-39` ma `containsExactly("1", … "15")` i wymaga dopisania "16". Istniejące IT sieją udane próby z `output = '{}'`, czyli bez tez; testy miary muszą siać `claims`, a ich pomocnicy potrzebują parametru języka.
- **Fix**: Poprawić listę w fazie 1 (bez `V13BackfillIT`, z pinem wersji w `MigrationsIT`) i dopisać w fazie 2 sianie `claims` z parametrem języka.
- **Decision**: FIXED — lista INSERT-ów bez `V13BackfillIT`, pin wersji w `MigrationsIT`, pomocnik siejący tezy w fazie 2

### F5 — Kryterium 3.7 jest niespełnialne

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3, zmiana 4 i kryterium 3.7
- **Detail**: Kryterium wymaga, żeby zamknięcie wpisu Parked zmieniło `docs/visuals/roadmap.svg`. Skrypt nie niesie listy Parked: `groups` to ręczna lista 24 pozycji bez „Język posta w bazie” (`tools/render_roadmap_svg.py:229-247`), więc SVG zostanie bez zmian. SVG zmienia tylko status karty S-36 (`:213`). Skrypt nie zna statusu `planning`; wcześniejsze zmiany przestawiały kartę na `progress` albo `done` w PR-ze dostawy.
- **Fix**: Kryterium: skrypt kończy się kodem 0, a status karty S-36 w skrypcie odpowiada roadmapie. Przestawienie karty przenieść do fazy 1, gdzie `/10x-implement` zmienia status na in-progress.
- **Decision**: FIXED — karta S-36 na rysunku przeniesiona do fazy 1 (kryterium 1.6), kryterium 3.7 wymaga tylko kodu 0

### F6 — Pliki kontraktu edytuje się ręcznie, IT je tylko porównuje

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Key Discoveries; Phase 4, zmiana 1
- **Detail**: Plan dwa razy mówi, że IT „zapisuje” pliki kontraktu. `ContractFiles.assertMatches` tylko porównuje i przy różnicy drukuje treść do wklejenia (`ContractFiles.java:44-62`). Nazwa zagnieżdżonego rekordu widoku musi być unikalna wśród rekordów kontraktu (`ContractVariantsTest.java:288-291`), a plan jej nie podaje.
- **Fix**: Poprawić słowo na „porównuje” i nazwać rekord `LanguageView`.
- **Decision**: FIXED — „porównuje” zamiast „zapisuje”, rekord `LanguageView`

### F7 — Faza 4 nie mówi, dlaczego nie idzie przez /10x-ui

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4, zmiana 3
- **Detail**: `REPOSITORY_GUIDELINES.md`: „UI work on an existing view goes through `/10x-ui`”. Faza 4 dopisuje wiersz do istniejącej sekcji `AdminView`. Plan spełnia wymogi tej ścieżki (tokeny, 7 stanów, zrzuty), ale nie mówi, że to świadomy wybór.
- **Fix**: Jedno zdanie w fazie 4: wiersz danych w istniejącej sekcji, bez zmiany stylu i bez nowego komponentu, więc bez `/10x-ui`.
- **Decision**: FIXED — zdanie w fazie 4, zmiana 3
