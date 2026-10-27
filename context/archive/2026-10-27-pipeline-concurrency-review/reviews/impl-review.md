<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: pipeline-without-embabel — współbieżność

- **Plan**: `context/archive/2026-10-25-pipeline-without-embabel/plan.md` — wyłącznie kontekst historyczny
- **Scope**: Uzupełniający przegląd kodu pod kątem błędów współbieżności; nie przegląd faz ani zgodności z planem
- **Reviewed phases**: none
- **Date**: 2026-10-27
- **Commit**: `13402888d8be084762ca32ae2242199af09ffdce`
- **Diff baseline**: `0a17f7d`; poprawki poprzedniego przeglądu: `f2a2a09`
- **Verdict**: APPROVED — w tym wąskim zakresie nie znaleziono nowego potwierdzonego błędu; znane ograniczenie F3 pozostaje opisane poniżej
- **Findings**: 0 new critical, 0 new warnings, 0 new observations

## Zakres i metoda

Na życzenie użytkownika pominięto ponowną ocenę odchyleń, wzorców, dokumentacji, zależności i zakończenia faz. Poprzedni raport z dziewięcioma poprawkami, plan i lekcje wykorzystano do rozpoznania znanych problemów. Archiwum pozostaje niezmienione.

Dwa równoległe przebiegi analizy: cykl życia przebiegu oraz anulowanie ocen i persystencja postów. Główny przegląd dodatkowo sprawdził ich styki, diff i testy. Analizowano konkretne przeploty zapisów, wywołań `cancel`, wyjątków i sprzątania, a nie samą obecność zabezpieczeń.

Kod objęty analizą: `PipelineRunService`, `RunWatchdog`, `RunSequence`, `RunRecorder`, `ConcurrentAssessmentDispatcher`, `AssessmentPool`, `PostTransitions`; pomocniczo `PipelineStartupRecovery`, `RunNotifications`, `PostStore`, `SourceIngest`, selekcja postów i obsługa przerwania w transporcie LLM.

Ścieżki kodu poniżej są względne wobec `szczur-radar-api/src/main/java/pl/szczurradar/api/`.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | N/A — poza zakresem tego przeglądu |
| Scope Discipline | N/A — poza zakresem tego przeglądu |
| Safety & Quality | PASS dla nowych ustaleń; resztkowe okno wcześniejszego F3 poniżej |
| Architecture | N/A — poza zakresem tego przeglądu |
| Pattern Consistency | N/A — poza zakresem tego przeglądu |
| Success Criteria | PASS — 60 testów skupionych, bez ponawiania pełnych bramek planu |

## Findings

Brak nowych potwierdzonych ustaleń wymagających triage. Nie oznacza to dowodu braku wszystkich możliwych wyścigów.

## Sprawdzone przeploty

| Hipoteza | Wynik analizy |
|----------|---------------|
| Szybkie zakończenie zadania przed zarejestrowaniem go w serwisie | `submit` rejestruje `own` i `running` przed przekazaniem zadania executorowi (`pipeline/PipelineRunService.java:275`). |
| Spóźnione anulowanie poprzedniego przebiegu przerywa następny | `FutureTask` koordynuje zakończenie `run` z trwającym anulowaniem; executor czyści pozostałą flagę przed kolejnym zadaniem. Nie znaleziono przeplotu przenoszącego anulowanie na następny przebieg. |
| `stall`, `finish` albo `execute.finally` nadpisują zakończony przebieg | Zapisy statusu są warunkowe. `stopStalled` zatwierdza zakończenie i zwolnienie postów przed anulowaniem, a późniejsze `fail` nie zmienia terminalnego statusu (`pipeline/PipelineRunService.java:409`, `pipeline/state/RunRecorder.java`). |
| Nowy krok pojawia się po zatrzymaniu przebiegu | `openStep` aktualizuje wiersz przebiegu przed dodaniem kroku w jednej instrukcji. Zatrzymanie albo zamknie dodany krok, albo uniemożliwi jego utworzenie (`pipeline/state/RunRecorder.java:306`). |
| Inwersja blokad krok → przebieg kontra przebieg → krok | `endStep` zamyka krok i wywołuje `touch` w osobnych transakcjach; nie trzyma blokady kroku podczas oczekiwania na przebieg (`pipeline/state/RunRecorder.java:324`). |
| `cancel` pomija Future dopisany do listy po pobraniu jej snapshotu | Watchdog najpierw przerywa wątek przebiegu. Oczekiwanie na pominięte, nieukończone zadanie obsłuży przerwanie i anuluje kompletną listę. Zadania uruchamiane później widzą `cancelled`. Nie znaleziono trwałego zakleszczenia tej ścieżki. |
| Stary worker zapisuje wynik lub zwalnia post przejęty przez nowy przebieg | `record`, `release` i `releaseFailed` sprawdzają właściciela `assessing_run_id`. Późny wynik nie zmienia posta należącego do innego przebiegu (`pipeline/state/PostTransitions.java`). |
| Dwie ścieżki zakończenia tworzą podwójne powiadomienia | Wstawianie powiadomień jest idempotentne; powtórne `runEnded` nie tworzy kolejnego wpisu dla tego samego odbiorcy i przebiegu. |
| Wolny zapis poprawnej odpowiedzi powoduje zadziałanie `CLIENT_ERROR_STREAK` | Reset następuje dopiero po zapisaniu wyniku, co jest jawną semantyką obecnego kodu i kontraktu. Nie uznano opóźnionego resetu za jednoznaczny nowy błąd. |

## Znane ograniczenia — bez duplikowania ustaleń

- **F3 nie jest całkowicie wyeliminowane.** W `PipelineRunService.java:358` flaga jest czyszczona przed `running.remove`. Watchdog może wcześniej pobrać Future (`:424`), a `cancel(true)` wykonać dopiero po tym czyszczeniu, już podczas `close` (`:361`). Przy oczekiwaniu na połączenie przerwanie nadal może spowodować fałszywy `RUN_CLOSE_FAILED`. Poprawka ogranicza okno, ale nie synchronizuje anulowania ze sprzątaniem. To ten sam problem i skutek opisany w poprzednim F3; nie nadano mu nowego ID. Nie odtworzono go w tej sesji na wyczerpanej puli Hikari.
- **A4:** późny `claim` korzystający ze snapshotu sprzed zatrzymania pozostaje wcześniej zaakceptowanym ryzykiem, obsługiwanym przez sprzątanie sierot.
- **A8:** zgłoszenie nowego przebiegu równolegle z zamknięciem executora pozostaje znanym, jawnie odłożonym przypadkiem.
- **OkHttp:** przerwanie Future nie gwarantuje natychmiastowego końca wywołania w locie; pozostaje przyjęta granica timeoutu klienta. Ten przegląd jej nie zmienia.

## Weryfikacja wykonana w tej sesji

Polecenie w `szczur-radar-api/`:

```sh
./mvnw -B -o test '-Dtest=PipelineRunServiceTest,RunWatchdogTest,ConcurrentAssessmentDispatcherTest,RunSequenceTest'
```

Wynik: kod wyjścia 0; 60 testów, 0 failures, 0 errors, 0 skipped.

| Klasa | Testy |
|-------|-------|
| PipelineRunServiceTest | 16 |
| RunWatchdogTest | 8 |
| ConcurrentAssessmentDispatcherTest | 27 |
| RunSequenceTest | 9 |

Zgodnie z zawężeniem użytkownika nie powtarzano pełnych 837 testów jednostkowych i 270 integracyjnych, Testcontainers ani operacji w PR env. Nie dopisano nowych testów ani nie zmieniono kodu produkcyjnego. Analiza SQL nie była nowym eksperymentem współbieżności na Postgresie.
