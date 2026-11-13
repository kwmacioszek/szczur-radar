# Testy mutacyjne rdzenia (S-45) — Plan Brief

> Full plan: `context/changes/core-mutation-testing/plan.md`

## What & Why

Zielony test mówi tylko tyle, że kod robi to, co test sprawdza. Nie mówi, czy test złapie błąd. Audyt po M-3 pokazał, że ochrona rdzenia bywa dziś przypadkowa (X1, X2).

S-45 mierzy ją systematycznie. PIT mutuje kod rdzenia: przebieg i warunkowe zapisy stanu, walidator kontraktu V2 oraz rozliczanie prób oceny. Każdy mutant, który przeżył, jest zabity testem albo przyjęty z zapisanym powodem. To drugie kryterium „done when” M-4 (`verification-scope.md:27`).

## Starting Point

PIT-a nie ma w buildzie. Logika rdzenia w Javie ma testy jednostkowe, przy czym walidator V2 ma wyłącznie takie. Warunkowe zapisy stanu to jednak stringi SQL w `RunRecorder` (17 zapytań) i `PostTransitions` (10) oraz predykaty prób w repozytoriach. PIT ich nie zmutuje, a chronią je tylko testy IT. CI (`ci.yml`) trwa około 4 min, a każdy job testowy blokuje deploy przez `railway-config` i Wait for CI.

## Desired End State

- `./mvnw -B -Pmutation test` mutuje klasy rdzenia, uruchamia tylko `*Test` (bez Dockera i sekretów) i kończy się błędem przy ocalałym mutancie spoza rejestru `AcceptedMutants` oraz przy wpisie, który przestał być potrzebny.
- Job `mutation` w `ci.yml` robi to przy każdym PR, w ≤ 10 min.
- Każdy strażnik SQL rdzenia został raz zepsuty, ma test IT, który to łapie, a wynik jest w `sql-breaks.md`.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Częstotliwość w CI | Job `mutation` przy każdym PR i pushu na `main`, blokuje (w `needs:` `railway-config`) | Regresja: nowy kod rdzenia bez zabijającego testu czerwieni PR, jak inne strażniki repo | Plan |
| Przyjęte mutanty | Rejestr `AcceptedMutants` (klasa, metoda z deskryptorem, mutator, opis; bez linii) z powodem + strażnik w obie strony | Precyzja per mutant i wzorzec `KNOWN_GAPS`, gdzie zbędny wpis jest czerwony; otwarty PIT nie ma znacznika per mutant | Plan |
| Powód przyjęcia | Prefiks kategorii `równoważny:` / `obronny:` / `tekst:`, pilnowany testem | Powód musi być konkretny i sprawdzalny, a nie „nie da się” | Plan |
| Logika w SQL | Ręczne zepsucie raz, tabela zepsucie → IT, luki zamknięte nowymi IT | Mierzy dokładnie „warunkowe zapisy stanu” z S-45 metodą X1/X2; PIT SQL-a nie widzi | Plan |
| Zakres klas | `agents.assessor.*`, `pipeline.assess/flow/state/run/step`, `PipelineRunService`, `RunWatchdog`, `posts.AssessmentQuality`; bez `NightSummary`, `MissingDays`, `api/` | Trzy obszary z roadmapy plus orkiestracja przebiegu, w ograniczonym czasie CI | Plan |
| Testy pod PIT | Tylko `*Test` (`targetTests`), bez `*IT` | Spring + Testcontainers pod PIT byłby bardzo wolny i kruchy | Research |
| Mutatory | `STRONGER` | Łapie usunięte warunki równości i switche po typach sealed, z których w dużej mierze składa się rdzeń | Plan |
| Przegląd jakości testów | Tylko tam, gdzie wskaże PIT (test zabija przez zachowanie, nie przez mock) | Outcome S-45 mówi tylko o mutantach; zegar i pełne ciała załatwiły S-26 i S-41 | Plan |
| Kod produkcyjny | Bez zmian; ocalały, który ujawnia błąd → stop i pytanie do właściciela | S-45 mierzy testy, a nie naprawia po cichu | Plan |

## Scope

**In scope:**

- profil `mutation` w `pom.xml` (pitest-maven 1.30.2, junit5-plugin 1.2.3);
- pomiar bazowy;
- rejestr i strażnik z testami na fiksturach;
- zabicie albo przyjęcie każdego ocalałego w zakresie;
- ręczne zepsucia SQL z nowymi IT dla luk;
- job `mutation` w CI;
- wytyczne i README.

**Out of scope:**

- PIT na `*IT`;
- mutowanie SQL;
- klasy spoza rdzenia;
- osobny przegląd jakości testów;
- próg procentowy, historia przyrostowa i Arcmutate;
- osobny workflow;
- zmiany kodu produkcyjnego.

## Architecture / Approach

Profil `mutation` podpina PIT do fazy `process-test-classes`. Faza `test` w tym profilu uruchamia tylko test z tagiem `mutation`, czyli strażnika `SurvivingMutantsTest`. Strażnik czyta `target/pit-reports/mutations.xml` i porównuje ocalałe (`SURVIVED`, `NO_COVERAGE`) z `AcceptedMutants` jako multizbiory.

Ponieważ PIT przejąłby z Surefire `groups=mutation`, dostaje `parseSurefireConfig=false` i własne `excludedGroups`. Zwykłe `./mvnw test` wyklucza tag `mutation`, ale uruchamia `MutantRegistryTest`, który na fiksturach dowodzi logiki porównania.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. PIT w buildzie i pomiar bazowy | Profil `mutation`, spike JUnit 6, `mutation-baseline.md` | JUnit 6 pod Mavenem może dać 0 testów (issue #113); czas > 10 min → stop |
| 2. Rejestr i strażnik | `AcceptedMutants`, `SurvivingMutantsTest`, `MutantRegistryTest` na fiksturach | Kolejność PIT → strażnik w jednym poleceniu |
| 3. Ocalałe: walidator V2 i jakość ocen | Każdy ocalały w `agents.assessor.*` i `AssessmentQuality` zabity albo przyjęty | Wiele mutantów równoważnych w regułach D1–D15 |
| 4. Ocalałe: przebieg | Strażnik zielony na prawdziwym raporcie, tabela „po S-45” | Dyspozytor jest wielowątkowy, więc mutanty mogą być niestabilne |
| 5. Ręczne zepsucia SQL | `sql-breaks.md` (~40 strażników) i nowe IT dla luk | Czas: każde zepsucie to przebieg IT |
| 6. Job `mutation` w CI i dokumentacja | Blokujący job ≤ 10 min, reguła w wytycznych | Czas CI na 4 vCPU |

**Prerequisites:** S-30, S-32, S-33, S-38 (wszystkie `done`); issue #155; Docker lokalnie dla IT w Fazie 5.
**Estimated effort:** ~3–5 sesji. Fazy 3 i 4 zależą od liczby ocalałych, której nie znamy przed pomiarem bazowym.

## Open Risks & Assumptions

- Zakładamy, że PIT 1.30.2 z junit5-plugin 1.2.3 znajduje testy JUnit 6.0.3 pod Mavenem. Nikt tego nie potwierdził, więc Faza 1 zaczyna od spike'u i ma warunek stopu.
- Czas pomiaru jest nieznany do Fazy 1. Granica to 10 min; przekroczenie oznacza decyzję właściciela, a nie ciche zawężenie zakresu.
- Klucz bez numeru linii przeżyje edycje pliku, a nazwa metody lambdy jest w nim bez numeru (`lambda$name`), bo ten zmienia się po dodaniu lambdy.
- PR zmieniający rdzeń, otwarty równolegle, po merge'u S-45 musi spełnić strażnika.

## Success Criteria (Summary)

- `./mvnw -B -Pmutation test` jest zielony, a każdy wpis `AcceptedMutants` ma powód z kategorią. Właściciel akceptuje te powody.
- Job `mutation` blokuje PR z nowym ocalałym mutantem rdzenia i trwa ≤ 10 min.
- Każdy strażnik SQL rdzenia ma test IT, który łapie jego zepsucie (`sql-breaks.md`).
