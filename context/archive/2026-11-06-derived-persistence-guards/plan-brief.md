# Strażnicy zapisu i końca przebiegu liczą zbiory z kodu (S-39) — Plan Brief

> Full plan: `context/changes/derived-persistence-guards/plan.md`
> Research: `context/changes/post-m3-verification-audit/research.md` (§1 R-17, X1, X2; §5 R-23)

## What & Why

CI ma czerwienieć, gdy konwerter enuma zapisuje inną wartość, niż dopuszcza `CHECK`, gdy złożony `CHECK` ma literał spoza enuma i gdy nowa metoda kończąca przebieg nie ma powiązanego powiadomienia (R-23). Dziś X1 i X2 przeszły przez `ClosedSetsIT`, a złapały je tylko testy zachowania, więc ochrona jest przypadkowa. Zmiana dotyczy samych testów.

## Starting Point

`ClosedSetsIT` liczy wartości enuma własną lambdą rejestru, nie konwerterem; złożone `CHECK`i z literałem enuma są w `NOT_A_VALUE_LIST` i pomijane po nazwie. Stan końcowy przebiegu zapisuje tylko `RunRecorder`, każda ścieżka woła `runEnded`, ale żaden test nie pilnuje nowej. Wzór strażnika to `PostColumnsIT:178-192`.

## Desired End State

Zły konwerter, literówka w złożonym `CHECK` i niesklasyfikowana metoda `RunRecorder` zaczerwieniają testy, każdy z własnym stałym testem czerwonej ścieżki. Wyjątki są nazwane w testach z powodem, a wyjątek, który przestał obowiązywać, też czerwieni.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Wartość konwertera (X1) | Strażnik woła prawdziwy konwerter (skan `@Converter`, razem z `RoleConverter`) | Porównywana wartość jest tą, którą zapisuje Hibernate, bez drugiej lambdy. | Plan |
| Złożone `CHECK`i (X2) | Literał musi być wartością enuma swojej kolumny | Łapie literówkę i literał z innej kolumny; samo „dowolna wartość” tego nie robi. | Plan |
| Mechanizm R-23 | Refleksja po `RunRecorder` + nazwane istniejące testy `runEnded` | Wzór `PostColumnsIT`; reguła „wywołanie obok wywołania” nie pasuje do podziału `RunSequence.finish` / `execute`. | Plan |
| Wyjątki | Mapy nazwa → powód w testach, strażnik czerwieni przy wpisie przeterminowanym | Nic w kodzie produkcyjnym, każdy wyjątek widać w jednym miejscu. | Plan |
| Dowód czerwieni | Stałe testy jednostkowe czerwonej ścieżki + jeden przebieg ze zmianą psującą na fazę | Czerwień zostaje w testach i jest sprawdzona raz na realnym kodzie. | Plan |
| Zakres | Tylko testy i dwa zdania dokumentacji; bez adnotacji, ArchUnit i PIT | Cel slice'a nie wymaga zmian w kodzie produkcyjnym. | Research |

## Scope

**In scope:** `ClosedSetsIT` (wartości z konwerterów, literały złożonych `CHECK`ów), nowe klasy pomocnicze i testy strażników, strażnik `RunRecorder`, wzmianki w `pipeline/CLAUDE.md` i javadoc `ClosedSetsIT`.

**Out of scope:** kod produkcyjny i migracje, wykrywanie nowego wywołującego istniejącej metody kończącej, zapis wierszy per wartość, testy mutacyjne, ponowienie powiadomienia po commicie (S-12), statusy w `domain-distillation.md`, `CHECK`i z wzorcem lub zbiorem otwartym.

## Architecture / Approach

Każdy strażnik: „czytaj świat” + „oceń”. Ocena to czysta funkcja w klasie pomocniczej w `src/test/`, wołana przez strażnika (`ClosedSetsIT` z bazą, `RunEndingTest` bez Springa) i przez osobny `*Test` z danymi złymi celowo. Strażnik R-23 widzi nową metodę kończącą, a lukę nowego wywołującego pokrywa przebieg z usuniętym `runEnded`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. X1 — wartość z konwertera | `ClosedSetsIT` porównuje z `CHECK` wartość z prawdziwego konwertera (dla `pipeline_runs.sources` z `PipelineRun.range`) | Znalezienie konwertera po typie enuma (pakietowe konstruktory, `RoleConverter`) |
| 2. X2 — literały złożonych `CHECK`ów | Parser par kolumna–literał na 8 złożonych `CHECK`ach, `NOT_A_VALUE_LIST` z powodami (5 wzorców i zbiorów otwartych) | Postgres normalizuje tekst (`(col)::text = '…'::text`), parser musi znać dzisiejsze formy; zbyt ścisły strażnik blokuje uprawnione zmiany |
| 3. R-23 — koniec przebiegu | Klasyfikacja metod `RunRecorder` i nazwane testy `runEnded` | Ścieżka bez testu sprawdzającego `runEnded` wymaga dopisania go |

**Prerequisites:** brak (S-39 bez zależności; `it-test-isolation` już zmergowane do `main`).
**Estimated effort:** ok. 2–3 sesje, 3 fazy, 5 nowych plików testowych (`ClosedSetsGuard`, `ClosedSetsGuardTest`, `RunEndingGuard`, `RunEndingTest`, `RunEndingGuardTest`) i zmiany w `ClosedSetsIT`.

## Open Risks & Assumptions

- Nazwy testów dla R-23 trzeba wybrać po przeczytaniu, że każdy sprawdza `runEnded`; audyt podaje lokalizacje, nie potwierdza treści asercji.
- Strażnik R-23 nie widzi nowego wywołującego istniejącej metody (np. nowa ścieżka wołająca `finish`); ta luka zostaje pokryta tylko przebiegiem ze zmianą psującą kod.
- Strażnik R-23 zakłada, że `RunRecorder` jest jedynym zapisującym stan końcowy przebiegu; zapis spoza niego wychwyci tylko przegląd.
- Strażnik R-23 dowodzi, że nazwane testy istnieją, nie że sprawdzają `runEnded`; treść potwierdza jednorazowy przebieg ze zmianą psującą kod.
- Zbyt ścisły strażnik zablokuje uprawnione zmiany (ryzyko z roadmapy); stąd jawne wyjątki z powodem.

## Success Criteria (Summary)

- X1 (`RunKind::name`), X2 (`'interupted'`) i usunięte `runEnded` zaczerwieniają odpowiedniego strażnika i po cofnięciu zmiany `src/main` jest czysty.
- Każdy strażnik ma stałe testy czerwonej ścieżki, `./mvnw -B verify` zielone.
