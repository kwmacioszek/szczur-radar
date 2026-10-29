<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-20 — awaria albo cisza jednego źródła nie psuje przebiegu pozostałych

- **Plan**: `context/changes/source-isolation/plan.md`
- **Mode**: Deep
- **Date**: 2026-10-29
- **Verdict**: SOUND (po triage; początkowo REVISE)
- **Findings**: 0 critical, 3 warnings, 0 observations; wszystkie 3 FIXED
- **Scope**: analiza statyczna planu i kodu, jedna delegowana weryfikacja silnika oraz odbiorców zmian. Bez uruchamiania testów aplikacji i bez odczytu produkcji.

## Verdicts

| Dimension | Initial verdict | After fixes |
|-----------|-----------------|-------------|
| End-State Alignment | WARNING | PASS |
| Lean Execution | PASS | PASS |
| Architectural Fitness | PASS | PASS |
| Blind Spots | PASS | PASS |
| Plan Completeness | FAIL | PASS |

## Grounding

Grounding przed triage: 39/39 istniejących ścieżek ✓, 5/5 symboli ✓, brief↔plan ✓. Osiem nowych ścieżek było jawnie oznaczonych jako nowe. Wszystkie 19 kryteriów sukcesu odpowiadało wpisom Progress; brak checkboxów poza Progress. Poprawki zachowują tytuły, numerację i stan tych wpisów.

## Findings

### F1 — Testowemu źródłu brakuje mappera

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — poprawka jest oczywista i wąsko ograniczona
- **Dimension**: Plan Completeness
- **Location**: Faza 2 — fałszywe źródło i testy integracyjne
- **Detail**: Plan dodawał tylko `ScriptedSourceFetcher(REDDIT)`, ale oczekiwał ocenionych postów Reddita przy awarii Wykopu. `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/AssessmentInputs.java:32` wyszukuje `RawInputMapper` po źródle i rzuca wyjątek, gdy go nie ma. Jedyny produkcyjny mapper to `sources/wykop/WykopPosts.java:20`. Bez mappera testowe posty drugiego źródła dostawałyby błędy przetwarzania zamiast ocen.
- **Fix**: Dodać mapper drugiego źródła i zgodny RAW do konfiguracji testowej.
- **Decision**: FIXED — użytkownik wybrał „Fix in plan” i wymagał użycia produkcyjnej logiki mapowania oraz trwałego przekazania obowiązku do S-27.
- **Applied**: Faza 2 opisuje `ScriptedSourceConfiguration`: adapter `RawInputMapper(REDDIT)` deleguje do produkcyjnego `WykopPosts.map(raw)`; post powstaje przez `WykopPosts.post(...)` z istniejącego `fixtures/wykop/entry.json`, ze zmianą tylko klucza źródła. Test sprawdza ocenę oraz treść zapisanego wejścia oceny. Brief opisuje tę samą granicę testu.
- **Follow-up required in S-27**: Obowiązek zapisany bezpośrednio w `context/foundation/roadmap.md`, sekcja S-27: test integracyjny rzeczywistego Reddita musi używać jego produkcyjnego mappera i rzeczywistego, zanonimizowanego RAW, zamiast adaptera i RAW Wykopu. Konfiguracja testowa ma zostać dostosowana, aby nie tworzyć dwóch beanów pod tym samym kluczem źródła. Bramka obejmuje zapis RAW → mapowanie → ocenę przy awarii Wykopu. Dane renderera i `docs/visuals/roadmap.svg` zaktualizowane.

### F2 — Bramek faz nie da się przejść w podanej kolejności

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — poprawki są mechaniczne i jednoznaczne
- **Dimension**: Plan Completeness
- **Location**: Fazy 1, 2 i 4
- **Detail**: Rejestracja `REDDIT` w fazie 1 zmienia odpowiedź z 400 na 503, ale aktualizację `pipeline/api/PipelineRunControllerIT.java:243` plan odkładał do fazy 4. Nowy FK wymaga rozszerzenia `TRUNCATE` w obu testach: `pipeline/PipelineRunIT.java:220` i `pipeline/RunWatchdogIT.java:165`; plan wskazywał tylko pierwszy, dopiero w fazie 2. Pomijał też konsumentów zmienionych typów: `pipeline/step/PostStoreTest.java:73` konstruuje `FetchedBatch`, a `szczur-radar-web/src/views/AdminView.test.ts:30` tworzy wymagane przez typecheck obiekty `Run`.
- **Fix**: Zmianę oczekiwania HTTP i oba `TRUNCATE` wykonać w fazie 1; dodać `PostStoreTest` do fazy 2 i `AdminView.test.ts` do fazy 4.
- **Decision**: FIXED — użytkownik wybrał „F2 — fix in plan — popraw kolejność”.
- **Applied**: Faza 1 obejmuje oczekiwania 503 dla `reddit`, 400 dla nieznanego `twitter` oraz oba rozszerzenia `TRUNCATE`. Faza 2 obejmuje konstruktory `FetchedBatch` w `PostStoreTest`; faza 4 — pola `sourcesFailed` / `sourcesEmpty` w fixture `AdminView.test.ts`. Późniejsze fazy odwołują się do kontraktu HTTP i sprzątania bazy ustalonych w fazie 1. Brief uaktualniony; istniejące bramki i Progress zachowane.

### F3 — Backfill nie gwarantuje niezmienionych „Dni bez danych”

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — wymaga rozstrzygnięcia zachowania historii
- **Dimension**: End-State Alignment
- **Location**: Desired End State, backfill V12, faza 3 i runbook
- **Detail**: Plan obiecywał niezmienioną historię pokrycia, ale puste historyczne przebiegi `succeeded` mapował na `empty`, wykluczane przez nowe zapytania. Obecne `pipeline/run/PipelineRunRepository.java:88` uwzględnia takie przebiegi na podstawie statusu całego przebiegu. Po migracji brak innego udanego pobrania może ujawnić lukę lub przesunąć pierwszy pokryty dzień źródła. Nie zweryfikowano występowania takich danych na produkcji.
- **Fix**: Zachować mapowanie pustych historycznych przebiegów na `empty`, jawnie opisać korektę historii i dodać test pokrycia po backfillu.
  - Strength: Zachowuje zasadę, że pokrycie wymaga postów, bez dodatkowego wyjątku dla starych danych.
  - Tradeoff: Usuwa bezwarunkową obietnicę identycznej listy braków przed i po migracji.
  - Confidence: HIGH — skutek wynika bezpośrednio z mapowania backfillu i filtrów zapytań pokrycia.
  - Blind spot: Nie sprawdzano, czy produkcja zawiera takie historyczne przebiegi.
- **Decision**: FIXED — użytkownik zaakceptował poprawkę po przykładzie: 15 października z postami, 16 października bez postów i bez innego pokrycia → po migracji brakuje 16 października.
- **Applied**: Plan i brief opisują zamierzoną korektę historii. W fazie 3 `V12BackfillIT` po migracji V11→V12 odczytuje dane produkcyjnymi zapytaniami i składa `MissingDays`: pusty 14 października nie wyznacza początku okna, udany 15 października je wyznacza, pusty 16 października jest brakującym dniem dla `today = 2026-10-18`. Dodatkowe udane pobranie za 16 października usuwa lukę; historia z samymi pustymi przebiegami nie ma pierwszego pokrytego dnia i nie zgłasza braków. Kontrakty runbooka i weryfikacji PR env wyjaśniają dopuszczalne różnice zamiast wymagać identycznej listy.

## Triage Summary

- **Fixed**: F1, F2, F3 (3)
- **Skipped**: brak
- **Accepted**: brak
- **Dismissed**: brak
- **Pending**: brak
- **Verdict after fixes**: REVISE → SOUND
- **Implementation**: nie rozpoczęto; opisane testy pozostają do napisania i uruchomienia podczas implementacji.
