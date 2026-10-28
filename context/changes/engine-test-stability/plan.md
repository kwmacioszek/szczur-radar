# S-26: testy silnika bez zależności od zegara — plan implementacji

## Overview

Testy silnika (`api/pipeline/**`) mają przestać padać losowo na współdzielonym self-hosted runnerze: synchronizacja na zdarzeniach zamiast limitów zegara ściennego jako warunku poprawności, jeden limit `@Timeout` na test ze zrzutem wątków do logu joba, gdy coś naprawdę zawiśnie. Dwa znane okna fałszywych alarmów po S-17 (audyt E1) zostają naprawione i przypięte testami, a `pipeline/CLAUDE.md` zgadza się z kodem (audyt E6).

Skróty jak w audycie: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `test/` = `szczur-radar-api/src/test/java/pl/szczurradar/api/`.

## Current State Analysis

Podstawa: audyt po M-2 (`context/changes/post-m2-audit/research.md`, ustalenia E1, E2, E6; kod od commita audytu `d7c6113` bez zmian w `szczur-radar-api/`) oraz inwentarz oczekiwań zegarowych w testach silnika zrobiony na potrzeby tego planu.

- **Niestabilny test anulowania (E2).** `ConcurrentAssessmentDispatcherTest.cancellingARunInterruptsItsCallInFlightAndLeavesAnotherRunAlone` (`test/pipeline/assess/ConcurrentAssessmentDispatcherTest.java:469-510`) ma dwa tryby awarii:
  - *wyścig (znany):* drugi `holding.tryAcquire` (`:483`) zwalnia się, gdy zadanie `stopped` zaczęło wywołanie, ale wątek wywołujący `dispatch` może być jeszcze przed `tasks.futures().add(executor.submit(notStarted))` (`api/pipeline/assess/ConcurrentAssessmentDispatcher.java:205-213`). `cancel(RUN)` (`:260-268`) widzi wtedy jeden `Future` i loguje „1 post task(s)”, więc asercja `:505` („2 post task(s)”) pada. Produkt jest poprawny: spóźnione zadanie widzi `cancelled` i kończy się `NotTaken` (`:272-273`) — błędna jest tylko synchronizacja testu;
  - *Timeout (nieznany):* run 39133095273 (2026-10-18, `feature/safe-deploy-gate`) padł na `TimeoutException` z `Future.get(10, SECONDS)` (`:488`/`:491`); przyczyny nie da się ustalić — surefire nie ma limitu ani zrzutu (`szczur-radar-api/pom.xml:175-185`), nie ma `@Timeout` ani `junit-platform.properties`, log CI już nie istnieje.
- **Wzorzec w testach jednostkowych silnika:** synchronizacja jest deterministyczna (`Semaphore holding`, `CountDownLatch release`), ale każde oczekiwanie ma własny limit 10 s, który przy wolnym runnerze zgłasza `TimeoutException`/„expected true” zamiast zrzutu. Do tego:
  - `PipelineRunServiceTest`: wynik `await` użyty jako wartość zwracana atrapy (`:382`, po przekroczeniu `start()` cicho zwraca `false`), ignorowane wyniki `await` (`:388`, `:529` — przy przekroczeniu zmienia się kolejność zdarzeń i test pada w `containsExactly` daleko od przyczyny), pętle aktywnego czekania `Thread.onSpinWait()` z `isLessThan(deadline)` (`:394-398`, `:441-445`, `:477-481`);
  - `RunWatchdogTest.anErrorInOneCheckDoesNotEndTheLaterChecks` (`:123-141`): prawdziwy scheduler co 100 ms i `verify(service, timeout(5_000).atLeast(3))`;
  - `ConcurrentAssessmentDispatcherTest` `isBetween(2, THREADS)` (`:244`): dolna granica zależy od tego, czy `Thread.sleep(50)` (`:1012`) trzech wywołań nałoży się w czasie.
- **IT silnika** (`RunWatchdogIT`, `OrphanedRunIT` z `PIPELINE_STALL_TIMEOUT=5s`; `PipelineRunIT`):
  - asercje czasu trwania „krok skończył się < 10 s po zatrzymaniu” (`RunWatchdogIT.java:230-238`, `PipelineRunIT.java:391-414`, `:656-661`), mierzone razem z odczytami bazy i odpytywaniem logu;
  - ignorowane wyniki `answered.await(30, SECONDS)` w skryptowanym `LlmClient` (`RunWatchdogIT.java:202`, `PipelineRunIT.java:377`, `:639`): po 30 s wstrzymane wywołanie odpowiada `VALID` i test pada daleko od przyczyny; to samo `wykopGate.await(30 s)` w lokalnych serwerach Wykopu (`OrphanedRunIT.java:91`, `api/PipelineRunControllerIT.java:84`, `api/InternalPipelineRunIT.java:92`);
  - ciasne zapasy wobec progu 5 s: test bezpiecznika z pauzą 3 s na wywołanie (`RunWatchdogIT.java:336-343`, ~2 s zapasu, inaczej watchdog zatrzymuje przebieg zamiast bezpiecznika), w `OrphanedRunIT` przypadki z `now()` i „aktywność 2 s temu” (`:202-247`, 3 s zapasu między `INSERT` a żądaniem) oraz własny przebieg wstrzymany w `fetch`, który watchdog zatrzymałby po 5 s (`:249-264`). `OrphanedRunIT` nigdzie nie czeka na próg: martwe przebiegi wstawia z `now() - interval '1 hour'` (`:81`);
  - pomocnicze pętle `settle`/`finished`/`awaitLog`/`awaitClosed` (30–60 s) to tylko ochrona przed zawieszeniem i zgłaszają, na czym utknęły — zostają.
- **Okna fałszywych alarmów (E1)** — oba tylko wtedy, gdy spóźnione `cancel(true)` watchdoga (`stopStalled`, `api/pipeline/PipelineRunService.java:441-447`) trafia w odczyt bazy, a pula Hikari odmawia przerwanemu wątkowi połączenia:
  - *okno 1:* krok rzucił sam z siebie, `catch` w `execute` wyczyścił flagę (`:342`), przerwanie ląduje w `stillRunning` → `recorder.isRunning` rzuca → `stillRunning` zwraca `true` (`:392-400`) → fałszywe ERROR `Pipeline run <id> failed` i błąd przebiegu podany do `close` (bez zmiany statusu: `fail` jest warunkowy, watchdog już zakończył przebieg);
  - *okno 2:* przerwanie ląduje w `notifications.runEnded(runId)` w `finally` (`:377`) → `runs.findById` rzuca → ERROR i `NOTIFICATION_FAILED` w Telegramie (`api/notifications/RunNotifications.java:74-79`), choć `stopStalled` i tak zgłasza przebieg (`:447`).
  Wzorzec do naśladowania już istnieje: `close` przy błędzie czyści flagę i pyta `isRunning` (`:470-473`); test `aLateInterruptOfTheWatchdogCuttingTheCloseShortIsAnInfoWithoutAnAlert` (`test/pipeline/PipelineRunServiceTest.java:211-237`) pokazuje, jak odtworzyć skutek atrapą (`doAnswer`, które ustawia flagę i rzuca `DataAccessResourceFailureException("Interrupted during connection acquisition")`).
- **Notatki silnika (E6)** — `api/pipeline/CLAUDE.md` wobec kodu: `:41` trasa „failed posts” przypisana `PipelineRunController` (jest w `api/posts/api/FailedPostController.java`); `:94-98` „Two WARNs” niepełne (są też `PipelineRunService:262,267,440`, `PipelineStartupRecovery:96,110`, `ConcurrentAssessmentDispatcher:400,423,616`); `:127-128` pomija dolną granicę 100 ms interwału watchdoga (`RunWatchdog.java:51,76`); `:195-199`, `:258` nie znają okien E1; `:337-343` nie mówi o 500 i opóźnionym sprzątaniu po `RejectedExecutionException` (A8); `:164-166` „without another attempt” zakłada semantykę OkHttp, której nie sprawdza żaden test (B10).
- **Narzędzia:** JUnit Jupiter 6.0.3 (Spring Boot 4.0.8) ma `junit.jupiter.execution.timeout.threaddump.enabled`: przy przekroczeniu `@Timeout` wypisuje zrzut wszystkich wątków na stdout przed przerwaniem testu; surefire/failsafe domyślnie przepuszczają stdout do logu joba `backend` (`.github/workflows/ci.yml:20-61`). Awaitility nie jest używane i nie jest potrzebne.

## Desired End State

- Żaden test silnika nie zależy od szybkości maszyny jako warunku poprawności, poza scenariuszami z natury czasowymi (watchdog z progiem 5 s), które mają opisany zapas co najmniej ~2,5×.
- Testy jednostkowe silnika czekają bez własnego limitu; zawieszenie kończy klasowy `@Timeout`, a w logu joba jest zrzut wątków — następne wystąpienie Timeoutu z runu 39133095273 pokaże przyczynę.
- Wyścig z E2 nie może wystąpić: test woła `cancel` dopiero po zgłoszeniu obu zadań.
- Spóźnione przerwanie watchdoga nie daje już fałszywego ERROR ani `NOTIFICATION_FAILED`; prawdziwa awaria odczytu nadal daje ERROR i alert.
- `pipeline/CLAUDE.md` zgadza się z kodem i opisuje nowe zasady testów.

Weryfikacja: `./mvnw -B verify` zielone lokalnie i na PR, testy jednostkowe silnika 20× zielone pod obciążeniem CPU, celowe złamania z faz 1–3 dają czerwień w oczekiwanym miejscu.

### Key Discoveries:

- `ConcurrentAssessmentDispatcher.java:205-213` — rejestracja `Tasks` przed pierwszym `submit` i flaga `cancelled` sprawiają, że anulowanie w trakcie zgłaszania jest poprawne; test musi tylko poczekać na drugie `submit`.
- `ConcurrentAssessmentDispatcherTest.java:984` — dyspozytor przyjmuje własną pulę (`dispatcher(ExecutorService pool)`), więc test może podać pulę, która sygnalizuje zgłoszenie zadania.
- `PipelineRunService.java:466-473` — `close` już czyści flagę przed pytaniem `isRunning`; okno 1 to ta sama sytuacja o krok wcześniej.
- `RunNotifications.java:59-80` — `runEnded` nigdy nie rzuca; wołają go 4 inne miejsca (`onClose`, odzyskiwanie, `stopStalled`, `cleanUpOrphans`), więc ponowny odczyt musi zostawić flagę przerwania tak, jak przyszła.
- `@Timeout` na klasie obejmuje metody testowe, nie metody cyklu życia — start kontekstu Springa i Testcontainers nie podlega limitowi.

## What We're NOT Doing

- Sterowany czas w IT (zegar watchdoga, `now()` bazy w progu sieroty) — zmienia kod silnika i SQL; scenariusze watchdoga zostają na prawdziwym czasie z zapasem.
- Zmiany w `ci.yml` (`timeout-minutes`, upload raportów surefire) — zrzut trafia do logu joba.
- `forkedProcessTimeoutInSeconds` w surefire/failsafe.
- Losowa kolejność testów jednostkowych i ogólnorepozytoryjny domyślny limit czasu (`junit.jupiter.execution.timeout.default`) — limit dostają tylko klasy silnika.
- Testy na puli Hikari rozmiaru 1, szew dla przeplotu `:441`/`:443`, strażniki „enum ⊆ `CHECK`” i „kolumna encji vs SQL” (E4), A8 i B10 jako poprawki kodu — w `CLAUDE.md` poprawiamy tylko ich opis.
- Testy, które mogą fałszywie przejść (`assertUnchanged` 1 s, `anotherInstancesRunIsLeftAlone`), ale nie padają losowo — poza celem S-26.
- Wspólny pomocnik do czekania dla wszystkich IT — każda klasa zostaje przy swoich pętlach.

## Implementation Approach

Najpierw infrastruktura zrzutu i testy jednostkowe (tam był Timeout i tam jest wyścig), potem dwie małe poprawki E1 z testami, które dziś byłyby czerwone, potem IT, na końcu dokumentacja. Zasada dla testów silnika: oczekiwanie na zdarzenie bez własnego limitu pod klasowym `@Timeout`; wynik każdego ograniczonego oczekiwania, które zostaje, jest sprawdzany asercją z opisem; czas trwania nie jest asercją, jeśli tę samą własność da się wyrazić kolejnością zdarzeń.

## Critical Implementation Details

- **Flaga przerwania w ponownym odczycie (faza 2).** `FutureTask.cancel(true)` przerywa wątek raz, więc jeden ponowny odczyt z wyczyszczoną flagą dostaje połączenie. Po nim flaga ma być ustawiona z powrotem, jeśli była ustawiona przy błędzie, żeby decydowały istniejące reguły wołającego: w `execute` reguła z `finally` (flaga skasowana po przebiegu, chyba że instancja się zatrzymuje — `aLateInterrupt…` sprawdza, że przerwanie watchdoga nie przechodzi na następny przebieg). Przerwanie przywrócone przez `runEnded` w `finally` `execute` musi więc zostać skasowane po nim na tych samych zasadach co `flagged`.
- **Jeden limit na test.** `@Timeout` w trybie domyślnym (`SAME_THREAD`) przerywa wątek testu; oczekiwanie bez limitu (`acquire()`, `get()`, `await()`) rzuca wtedy `InterruptedException`, a JUnit raportuje „timed out after …” ze zrzutem wypisanym przed przerwaniem. `@AfterEach` (zwolnienie `release`, `shutdownNow()`) nadal się wykona.

## Phase 1: Zrzut wątków i testy jednostkowe bez zegara

### Overview

Przekroczenie czasu w teście silnika daje zrzut wątków w logu, testy jednostkowe dyspozytora, serwisu przebiegów i watchdoga czekają na zdarzenia, a wyścig E2 znika.

### Changes Required:

#### 1. Konfiguracja platformy JUnit

**File**: `szczur-radar-api/src/test/resources/junit-platform.properties` (nowy)

**Intent**: Włączyć zrzut wątków przy przekroczeniu `@Timeout` dla całego modułu (unit i IT czytają ten sam classpath testów), bez domyślnego limitu dla testów spoza silnika.

**Contract**: jeden klucz `junit.jupiter.execution.timeout.threaddump.enabled=true` z komentarzem po co (S-26, run 39133095273); żadnego `junit.jupiter.execution.timeout.default`.

#### 2. Test dyspozytora

**File**: `test/pipeline/assess/ConcurrentAssessmentDispatcherTest.java`

**Intent**: Usunąć wyścig E2, przenieść limit czasu z każdego oczekiwania na klasę i uniezależnić dolną granicę współbieżności od nałożenia się `Thread.sleep(50)`.

**Contract**:
- klasa ma `@Timeout` (rząd 60 s na test);
- `cancellingARunInterruptsItsCallInFlightAndLeavesAnotherRunAlone` woła `dispatcher.cancel(RUN)` dopiero, gdy pula dostała oba zadania zatrzymanego przebiegu (np. pula testowa sygnalizująca każde `submit`/`execute`, na którą test czeka) — asercja „2 post task(s)” zostaje bez zmian;
- `tryAcquire(n, SECONDS)`, `Future.get(n, SECONDS)` w ciałach testów → wersje bez limitu; `awaitTermination` i `Thread.join` mogą zostać z limitem nie krótszym niż klasowy `@Timeout`, a ich wynik jest sprawdzany;
- test z `isBetween(2, THREADS)` (`:244`) dostaje deterministyczną dolną granicę: wywołania czekają, aż w locie będą co najmniej dwa (bramka w skryptowanym `LlmClient`), zamiast liczyć na `sleep(50)`; górna granica (rozmiar puli) zostaje.

#### 3. Test serwisu przebiegów

**File**: `test/pipeline/PipelineRunServiceTest.java`

**Intent**: Każde oczekiwanie albo bez limitu pod klasowym `@Timeout`, albo z wynikiem sprawdzonym asercją z opisem; bez aktywnego czekania.

**Contract**:
- klasa ma `@Timeout`;
- `:382` — atrapa `recorder.start` nie zwraca wyniku `await` jako wartości: czeka bez limitu i zwraca jawne `true`;
- `:388`, `:529` — wyniki oczekiwań nie są już ignorowane (oczekiwanie bez limitu albo asercja);
- pętle `Thread.onSpinWait()` z `isLessThan(deadline)` (`:394-398`, `:441-445`, `:477-481`) → oczekiwanie na zatrzask/warunek sygnalizowany przez atrapę (albo odpytywanie bez własnego terminu pod `@Timeout`), bez porównywania dwóch liczb jako komunikatu błędu.

#### 4. Test watchdoga

**File**: `test/pipeline/RunWatchdogTest.java`

**Intent**: `anErrorInOneCheckDoesNotEndTheLaterChecks` ma czekać na trzy sprawdzenia, a nie liczyć je w oknie 5 s.

**Contract**: atrapa `stopStalled`/`own` odlicza zatrzask (3); test czeka na niego bez limitu pod klasowym `@Timeout`, potem `verify(…, atLeast(3))` bez `timeout(…)`. Pozostałe testy klasy bez zmian.

### Success Criteria:

#### Automated Verification:

- Trzy klasy zielone: `cd szczur-radar-api && ./mvnw -B test -Dtest='ConcurrentAssessmentDispatcherTest,PipelineRunServiceTest,RunWatchdogTest'`
- W trzech klasach nie zostało oczekiwanie z limitem krótszym niż klasowy `@Timeout` ani ignorowany wynik oczekiwania, ani `Thread.onSpinWait` (grep `tryAcquire\(|\.get\([0-9]|await\([0-9]|timeout\([0-9]|onSpinWait` → tylko udokumentowane wyjątki z kroku 2)
- Celowe złamanie wyścigu: tymczasowa pauza (np. 200 ms) w `ConcurrentAssessmentDispatcher.dispatch` między zgłoszeniami zadań — test anulowania zostaje zielony; pauza cofnięta
- Celowe zawieszenie: tymczasowo wstrzymane wywołanie, którego nic nie zwalnia — test pada na `@Timeout`, a wyjście zawiera zrzut wątków z wątkami puli; zmiana cofnięta
- 20 kolejnych przebiegów trzech klas pod obciążeniem CPU (np. równoległe `yes > /dev/null` na każdym rdzeniu) — 20/20 zielonych
- Wszystkie testy jednostkowe zielone: `./mvnw -B test`

**Implementation Note**: celowe złamania wykonuje agent i cofa je przed commitem; w commicie są tylko zmiany testów i `junit-platform.properties`.

---

## Phase 2: Okna fałszywych alarmów E1

### Overview

Odczyt bazy przerwany spóźnionym przerwaniem watchdoga jest powtarzany raz z wyczyszczoną flagą w obu oknach, więc przebieg zakończony przez watchdoga nie daje fałszywego ERROR ani `NOTIFICATION_FAILED`.

### Changes Required:

#### 1. Okno 1 — `stillRunning`

**File**: `api/pipeline/PipelineRunService.java`

**Intent**: Gdy `recorder.isRunning` rzuci, a wątek jest przerwany (przerwanie wylądowało w trakcie odczytu), odczytać jeszcze raz z wyczyszczoną flagą; dopiero błąd tego odczytu (albo błąd bez przerwania) zostaje dodany do `failure` jako `suppressed` i daje `true` jak dziś.

**Contract**: `stillRunning(UUID, RuntimeException)` — sygnatura bez zmian; flaga po powrocie taka, jaka była przy błędzie (przywrócona po ponownym odczycie); reguła flagi w `execute` i w `close` bez zmian. Javadoc `stillRunning` i `execute` mówi o tym oknie.

#### 2. Okno 2 — `runEnded`

**File**: `api/notifications/RunNotifications.java`

**Intent**: Gdy praca `runEnded` rzuci na przerwanym wątku, wykonać ją jeszcze raz z wyczyszczoną flagą, potem przywrócić flagę; dopiero błąd ponownej próby (albo błąd bez przerwania) to ERROR i `NOTIFICATION_FAILED` jak dziś. Powtórzenie jest bezpieczne: `notifyAdmins` jest idempotentne (`ON CONFLICT DO NOTHING`), a alert `RUN_NEEDS_ATTENTION` idzie tylko przy `created > 0`.

**Contract**: `runEnded(UUID)` — sygnatura i „nigdy nie rzuca” bez zmian; jedna ponowna próba, tylko gdy flaga była ustawiona przy błędzie; flaga po powrocie taka, jaka była przy błędzie.

**File**: `api/pipeline/PipelineRunService.java`

**Intent**: W `finally` `execute` przerwanie przywrócone przez `runEnded` podlega tej samej regule co `flagged`: skasowane, chyba że instancja się zatrzymuje.

**Contract**: po `execute` flaga przerwania jak dziś (`aLateInterruptOfTheWatchdogCuttingTheCloseShortIsAnInfoWithoutAnAlert` nadal zielony).

#### 3. Testy

**File**: `test/pipeline/PipelineRunServiceTest.java`

**Intent**: Przypiąć okno 1 i to, że prawdziwa awaria nadal alarmuje.

**Contract**: nowy test — krok rzuca, pierwsza odpowiedź `recorder.isRunning` ustawia flagę i rzuca `DataAccessResourceFailureException("Interrupted during connection acquisition")`, druga zwraca `false` → INFO `ended by the watchdog or an interruption`, brak linii ERROR przebiegu, `close` z domyślnym błędem (nie z błędem kroku), brak alertu, flaga skasowana po `execute`. Istniejący `aCloseFailureWhoseRunCannotBeReadIsStillRaised` (oba odczyty rzucają bez przerwania) zostaje zielony.

**File**: `test/notifications/RunNotificationsTest.java`

**Intent**: Przypiąć okno 2 na prawdziwym `RunNotifications`.

**Contract**: nowe testy — (a) pierwszy `runs.findById` ustawia flagę i rzuca, drugi zwraca przebieg wymagający uwagi → powiadomienie utworzone, brak ERROR, brak `NOTIFICATION_FAILED`, flaga ustawiona po powrocie; (b) oba odczyty rzucają na przerwanym wątku → ERROR i `NOTIFICATION_FAILED` jak dziś; (c) błąd bez przerwania → bez ponownej próby, ERROR i alert jak dziś.

### Success Criteria:

#### Automated Verification:

- Nowe i istniejące testy zielone: `cd szczur-radar-api && ./mvnw -B test -Dtest='PipelineRunServiceTest,RunNotificationsTest'`
- Celowe złamanie: bez ponownego odczytu w `stillRunning` nowy test okna 1 jest czerwony; bez ponownej próby w `runEnded` test (a) jest czerwony; zmiany cofnięte
- Wszystkie testy jednostkowe zielone: `./mvnw -B test`

---

## Phase 3: IT bez zegara jako warunku poprawności

### Overview

IT silnika wyrażają „krok skończył się przez przerwanie” kolejnością zdarzeń zamiast czasu trwania, nie ignorują wyników oczekiwań, a scenariusze z natury czasowe mają szerszy zapas wobec progu.

### Changes Required:

#### 1. Kolejność zamiast „< 10 s”

**File**: `test/pipeline/RunWatchdogIT.java`, `test/pipeline/PipelineRunIT.java`

**Intent**: Własność „zatrzymany krok kończy się przez przerwanie, nie przez odpowiedź wywołania” sprawdzać tym, że krok skończył się, zanim wstrzymane wywołanie dostało odpowiedź — wywołanie czeka bez limitu, aż przerwie je zatrzymanie albo zwolni `settle()`.

**Contract**: znikają `STOPPED_STEP_ENDS_WITHIN` i asercje czasu trwania (`RunWatchdogIT.java:89,230-238`, `PipelineRunIT.java:104,391-414,656-661`); skryptowane wywołania w tych scenariuszach czekają na `answered` bez limitu (`RunWatchdogIT.java:202`, `PipelineRunIT.java:377,639`), a test sprawdza, że w chwili końca kroku odpowiedź nie została wydana (np. flaga „odpowiedziano” fałszywa) i że wywołanie zobaczyło przerwanie. Pomocnicze pętle (`awaitLog`, `awaitClosed`, `settle`, `finished`) zostają z limitami — chronią przed zawieszeniem i mówią, na czym test utknął; `settle()` musi nadal zwalniać wstrzymane wywołania.

#### 2. Ignorowane wyniki oczekiwań w lokalnych serwerach Wykopu

**File**: `test/pipeline/OrphanedRunIT.java`, `test/pipeline/api/PipelineRunControllerIT.java`, `test/pipeline/api/InternalPipelineRunIT.java`

**Intent**: Bramka `wykopGate` nie może po cichu wypuścić wstrzymanego pobrania po 30 s.

**Contract**: `wykopGate.await` bez limitu (bramkę zwalnia test albo jego sprzątanie po teście) albo z wynikiem, który przy przekroczeniu kończy żądanie błędem rozpoznawalnym w teście — nigdy cichą odpowiedzią „pusty dzień”. Sprzątanie każdej klasy zwalnia bramkę także po czerwonym teście.

#### 3. Szerszy zapas wobec progu

**File**: `test/pipeline/OrphanedRunIT.java`

**Intent**: Żaden scenariusz klasy nie czeka na próg, więc próg może być daleko od czasu trwania testu.

**Contract**: `PIPELINE_STALL_TIMEOUT=5s` → `60s` (Javadoc klasy i `:57` zaktualizowane); przypadek „aktywność 2 s temu” zostaje (zapas 58 s), martwe przebiegi nadal `now() - interval '1 hour'`.

**File**: `test/pipeline/RunWatchdogIT.java`

**Intent**: Test bezpiecznika ma dowodzić, że każde wywołanie z awarią przesuwa aktywność (3 wywołania razem dłużej niż próg, każde krócej), z większym zapasem na jedno wywołanie.

**Contract**: pauza wywołania z awarią 3 s → 2 s (jedno wywołanie 2 s wobec 5 s progu; trzy razem 6 s > 5 s, dolna granica gwarantowana pauzami); opis scenariusza w Javadoc i w `pipeline/CLAUDE.md` (faza 4) mówi o zapasie. `aSlowRunWithProgressIsNotStopped` (1,5 s wobec 5 s) bez zmian.

#### 4. Limit na test IT silnika

**File**: `RunWatchdogIT`, `PipelineRunIT`, `OrphanedRunIT`, `LlmOutageIT`, `api/PipelineRunControllerIT`, `api/InternalPipelineRunIT`

**Intent**: Oczekiwania bez limitu z kroków 1–2 kończy klasowy `@Timeout` ze zrzutem wątków.

**Contract**: `@Timeout` na klasie (rząd 5 min na test — dłużej niż najdłuższa pętla pomocnicza 60 s); metody cyklu życia (kontekst Springa, Testcontainers) poza limitem.

### Success Criteria:

#### Automated Verification:

- IT silnika zielone na dwóch różnych ziarnach: `cd szczur-radar-api && ./mvnw -B verify -Dit.test='RunWatchdogIT,PipelineRunIT,OrphanedRunIT,LlmOutageIT,PipelineRunControllerIT,InternalPipelineRunIT' -Dit.seed=1` i to samo z `-Dit.seed=2`
- W IT silnika nie ma asercji czasu trwania `isLessThan(Duration…)` ani ignorowanego wyniku `await(…, …)` (grep `STOPPED_STEP_ENDS_WITHIN|answered.await\(|wykopGate.await\(` → tylko wywołania bez limitu albo z obsłużonym wynikiem)
- Celowe złamanie: tymczasowo bez `task.cancel(true)` w `stopStalled` scenariusz zawieszonego przebiegu w `RunWatchdogIT` jest czerwony (krok nie kończy się przed odpowiedzią), a nie zielony po 30 s; zmiana cofnięta
- Cały zestaw zielony lokalnie z Dockerem: `./mvnw -B verify`

---

## Phase 4: Notatki silnika (`pipeline/CLAUDE.md`)

### Overview

`api/pipeline/CLAUDE.md` zgadza się z kodem po S-17 i po zmianach faz 1–3.

### Changes Required:

#### 1. Nieścisłości z E6

**File**: `api/pipeline/CLAUDE.md`

**Intent**: Poprawić sześć miejsc wskazanych w audycie, bez przepisywania reszty.

**Contract**:
- `:41` — lista nieudanych postów należy do `posts/api/FailedPostController`, nie `PipelineRunController`;
- `:94-98` — zamiast „Two WARNs” pełny zbiór linii WARN silnika albo sformułowanie bez liczby, z odwołaniem do miejsc (`PipelineRunService`, `PipelineStartupRecovery`, `ConcurrentAssessmentDispatcher`);
- `:127-128` — interwał watchdoga: piąta część progu, najwyżej minuta, najmniej 100 ms (`RunWatchdog.java:51,76`);
- `:195-199`, `:258` — oba okna E1 i ich obsługa po fazie 2 (ponowny odczyt z wyczyszczoną flagą w `stillRunning` i `runEnded`; ERROR i alert tylko, gdy ponowny odczyt też pada);
- `:337-343` — `submit` po `shutdownNow()` w trakcie zatrzymania daje `RejectedExecutionException` po commicie → 500, a przebieg `queued` sprząta S-11 po `PIPELINE_STALL_TIMEOUT` od `created_at` albo odzyskiwanie (A8, stan dzisiejszy);
- `:164-166` — „without another attempt” jako założenie o OkHttp, którego żaden test nie sprawdza (B10).

#### 2. Zmiany faz 1–3

**File**: `api/pipeline/CLAUDE.md` (sekcja `## Tests`)

**Intent**: Opisać zasady testów silnika, żeby następny test ich nie łamał.

**Contract**: zasada „oczekiwanie na zdarzenie bez własnego limitu pod klasowym `@Timeout`; zrzut wątków z `junit-platform.properties`; czas trwania nie jest asercją, gdy własność da się wyrazić kolejnością”; `OrphanedRunIT` z `PIPELINE_STALL_TIMEOUT=60s`; test bezpiecznika w `RunWatchdogIT` z pauzą 2 s; nowe testy okien E1 w opisie `PipelineRunServiceTest` i `RunNotificationsTest`.

### Success Criteria:

#### Automated Verification:

- Grep potwierdza poprawki: `api/pipeline/CLAUDE.md` nie przypisuje „failed posts” `PipelineRunController`, wymienia `100 ms`, `FailedPostController`, `junit-platform.properties`, `@Timeout` i `PIPELINE_STALL_TIMEOUT=60s` przy `OrphanedRunIT`
- CI na PR zielone (wszystkie joby `ci.yml`, w tym `backend`): `gh pr checks <PR>`

#### Manual Verification:

- Użytkownik przegląda diff PR: zmiany kodu produkcyjnego ograniczone do dwóch ponownych odczytów (faza 2), reszta to testy, `junit-platform.properties` i `CLAUDE.md`

---

## Testing Strategy

### Unit Tests:

- Wyścig E2: anulowanie dopiero po zgłoszeniu obu zadań; celowa pauza w dyspozytorze nie czerwieni testu.
- Okno 1: przerwanie w `isRunning` → INFO bez alertu; awaria bez przerwania → ERROR i `RUN_CLOSE_FAILED` jak dziś.
- Okno 2: przerwanie w `runEnded` → powiadomienie bez ERROR; podwójna awaria albo awaria bez przerwania → ERROR i `NOTIFICATION_FAILED`.
- Zrzut: celowe zawieszenie kończy się na `@Timeout` ze zrzutem wątków w wyjściu.

### Integration Tests:

- `RunWatchdogIT`, `PipelineRunIT`: zatrzymany krok kończy się przed odpowiedzią wstrzymanego wywołania.
- `OrphanedRunIT`: wszystkie scenariusze na progu 60 s.
- Dwa ziarna kolejności (`-Dit.seed`).

### Manual Testing Steps:

1. Przejrzeć diff PR pod kątem zakresu zmian kodu produkcyjnego (faza 2).

## Performance Considerations

Testy jednostkowe nie zwalniają (synchronizacja na zdarzeniach). Test bezpiecznika w `RunWatchdogIT` skraca się o ~3 s (pauzy 2 s zamiast 3 s). Czekanie bez limitu wydłuża tylko czerwony test do klasowego `@Timeout` — w zamian jest zrzut wątków.

## Migration Notes

Nie dotyczy: bez migracji, bez zmian konfiguracji Railway ani CI. Zmiana zachowania na produkcji tylko w rzadkich oknach E1 (mniej fałszywych linii ERROR i alertów), więc PR env nie wymaga osobnej weryfikacji poza zwykłym deployem PR.

## References

- Audyt: `context/changes/post-m2-audit/research.md` (E1, E2, E6; tabela testowalności „Silnik”)
- Roadmapa: `context/foundation/roadmap.md` — S-26
- Przegląd współbieżności (F3): `context/archive/2026-10-27-pipeline-concurrency-review/reviews/impl-review.md:54`
- Wzorzec odtworzenia skutku przerwania: `test/pipeline/PipelineRunServiceTest.java:211-237`
- Notatki silnika: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Zrzut wątków i testy jednostkowe bez zegara

#### Automated

- [x] 1.1 Trzy klasy zielone: `./mvnw -B test -Dtest='ConcurrentAssessmentDispatcherTest,PipelineRunServiceTest,RunWatchdogTest'`
- [x] 1.2 Brak oczekiwań z limitem krótszym niż `@Timeout`, ignorowanych wyników i `onSpinWait` w trzech klasach (grep)
- [x] 1.3 Celowe złamanie wyścigu (pauza między zgłoszeniami) — test anulowania zielony; cofnięte
- [x] 1.4 Celowe zawieszenie — czerwień na `@Timeout` ze zrzutem wątków w wyjściu; cofnięte
- [x] 1.5 20 przebiegów trzech klas pod obciążeniem CPU — 20/20 zielonych
- [x] 1.6 Wszystkie testy jednostkowe zielone: `./mvnw -B test`

### Phase 2: Okna fałszywych alarmów E1

#### Automated

- [ ] 2.1 Nowe i istniejące testy zielone: `./mvnw -B test -Dtest='PipelineRunServiceTest,RunNotificationsTest'`
- [ ] 2.2 Celowe złamanie: bez ponownego odczytu nowe testy okien 1 i 2 czerwone; cofnięte
- [ ] 2.3 Wszystkie testy jednostkowe zielone: `./mvnw -B test`

### Phase 3: IT bez zegara jako warunku poprawności

#### Automated

- [ ] 3.1 IT silnika zielone na ziarnach 1 i 2 (`-Dit.test=… -Dit.seed=1`, `-Dit.seed=2`)
- [ ] 3.2 Brak asercji czasu trwania i ignorowanych wyników `await` w IT silnika (grep)
- [ ] 3.3 Celowe złamanie: bez `task.cancel(true)` w `stopStalled` scenariusz zawieszonego przebiegu czerwony; cofnięte
- [ ] 3.4 Cały zestaw zielony lokalnie: `./mvnw -B verify`

### Phase 4: Notatki silnika (`pipeline/CLAUDE.md`)

#### Automated

- [ ] 4.1 Grep potwierdza poprawki E6 i opis zasad testów w `pipeline/CLAUDE.md`
- [ ] 4.2 CI na PR zielone: `gh pr checks <PR>`

#### Manual

- [ ] 4.3 Użytkownik przegląda diff PR: kod produkcyjny tylko dwa ponowne odczyty (faza 2)
