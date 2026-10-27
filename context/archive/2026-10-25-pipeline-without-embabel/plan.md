# S-17: przebieg pipeline'u bez Embabel — Implementation Plan

## Overview

Przebieg pipeline'u (fetch → store → select → assess → summarize) przestaje być agentem Embabel planowanym przez GOAP i staje się zwykłą sekwencją kroków w Javie, wykonywaną na wątku kolejki `pipeline-run`. Dzięki temu przerwanie (watchdog, zamknięcie instancji) dociera do kroku i do oceny, a akcje „zombie” znikają razem z mapą `waiting`, `cancel(false)` i `unwrap`. Embabel wychodzi z całego API (pom, properties, `AssessorAgent`), bo po przebiegu nie ma w kodzie produkcyjnym żadnego konsumenta. Przy okazji domykamy trzy pozycje Parked adresowane do „najbliższej pracy w tym kodzie” (A6, F5 z S-18, F7 z S-09) i przebudowujemy testy silnika na skupione i niezależne od kolejności (MS-03). Roadmapa: `context/foundation/roadmap.md:273-284`, issue #76. Research: `context/changes/pipeline-without-embabel/research.md`.

Konwencja ścieżek: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`, `res/…` = `szczur-radar-api/src/main/resources/…`.

## Current State Analysis

- **Embabel w przebiegu to 3 pliki i 7 symboli**: `api/pipeline/PipelineRunService.java:21-22` (`AgentPlatform`, `AgentInvocation`), `api/pipeline/flow/PipelineAgent.java:5-8` (`@Agent`, `@Action`, `@AchievesGoal`, `ActionRetryPolicy`), `api/pipeline/flow/RunFlow.java:11` (`SomeOf`). Agent ma 7 akcji `FIRE_ONCE` i jedno rozgałęzienie w `route` (`PipelineAgent.java:72-152`); logika kroków leży w `step/`, `assess/`, `state/`, a akcje tylko wołają ją przez `RunRecorder.step`.
- **Model wątków Embabel** (`research.md`, sekcja B): planowanie i wszystkie akcje biegną na jednym wątku własnej puli `newCachedThreadPool` (`embabel-platform-N`, bez limitu), a wątek `pipeline-run` tylko czeka w `agent.get()` (`PipelineRunService.java:303-306`). Stąd mapa `waiting` (`:145,305,329,372`), `agent.cancel(false)` (`:372-375`, nie przerywa nic), gałąź `CancellationException` (`:314-317`), `unwrap` (`:411-414`) i osobne wejście `AssessmentDispatcher.cancel(runId)` dla zadań puli LLM. `onClose` (`:424-450`) robi `shutdownNow()`, które przerywa tylko czekanie.
- **Zombie** po `stall`/`interrupt`: w `fetch` kończy pobranie (termin Wykopu 10 min) i następny `openStep` odmawia; w otwartym kroku `store` wstawia posty z `first_run_id` martwego przebiegu do końca pętli paczek (`api/pipeline/step/PostStore.java:56-90`, audyt A7); `llm.check()` biegnie na wątku akcji przed `dispatching.put`, więc `cancel` go nie sięga (`api/pipeline/assess/ConcurrentAssessmentDispatcher.java:159-178`).
- **Strażniki warunkowe** w `RunRecorder` (11 metod) i `PostTransitions` (7 metod) chronią nakładkę deployu, odzyskiwanie i sieroty; żaden nie istnieje tylko dla zombie (`research.md`, sekcja E) — wszystkie zostają.
- **Poza przebiegiem**: `api/agents/assessor/AssessorAgent.java` (38 linii, bez wywołań w `src/main`), 4 pliki z Embabel w komentarzach, `res/application.properties:53-58` i 3 pliki `application-<provider>.properties` mapujące `LLM_*` na rejestr Embabel. Start platformy: skan 8 pakietów, eager beany dostawcy, `logback-embabel.xml` (aplikacja nie ma własnej konfiguracji logowania), autokonfiguracje klienta MCP i chat-client Spring AI, obejście `spring-ai-autoconfigure-model-tool` (`pom.xml:106-112`).
- **Wymóg `LLM_*` w `migrate`** niesie niezależnie `api/config/LlmConfigurationCheck` (S-10, `res/META-INF/spring.factories:1-2`) — decyzja: zostaje bez zmian.
- **Testy**: `test/pipeline/PipelineAgentIT.java` 871 linii, 15 metod `@Order` na jednej bazie bez czyszczenia (Javadoc `:88`), 16 postów tworzonych tylko w Order 1, statyczne flagi `volatile` (`:104-112`); `RunWatchdogIT` 5 metod `@Order` (2 i 5 używają postów z 1); `RunRecorder` bez skupionego testu na bazie; `PostTransitions` częściowo w `test/posts/PostRepositoryIT.java:124-199` (brak `claim`, `releaseAll`, `releaseOrphans`); `PipelineRunServiceTest` mockuje `AgentPlatform` (`:85`) i pokrywa tylko ścieżki nieudanego `close`/`onClose`.
- **Pozycje Parked czekające na S-17**: A6 — dyspozytor zwraca `stoppedBy` zamiast rzucać, więc `RunRecorder.step` zamyka krok `assess` jako `succeeded` w przebiegu `failed` (`roadmap.md:366`); F5 — `alerts.raise` w `RunWatchdog.check` bez osobnej ochrony (`api/pipeline/RunWatchdog.java:100-110`, `roadmap.md:372`); F7 — seria niesklasyfikowanych błędów klienta LLM (`TransportException.unclassified` → `IllegalStateException`, `api/llm/transport/TransportException.java:131-133`) spala technical failure każdego posta przebiegu (`context/archive/2026-10-20-attempt-accounting/follow-ups/review-fixes.md:6-15`).

## Desired End State

- Przebieg działa jako sekwencja kroków w `api/pipeline/flow/RunSequence` na wątku `pipeline-run`; `PipelineRunService` nie zna `AgentPlatform`, `waiting` ani `unwrap`; `stopStalled` po zapisach woła `cancel(true)` na zadaniu przebiegu i `dispatcher.cancel(runId)`; `onClose` przez `shutdownNow()` przerywa krok, nie tylko czekanie.
- Zatrzymany przebieg kończy się w logu INFO (nie ERROR), a jego krok kończy się w ciągu sekund po przerwaniu, gdy stoi w `await`, w kliencie Wykopu albo między paczkami `store`; w locie na OkHttp — po `LLM_TIMEOUT`, jak dotąd.
- Krok `assess` zatrzymany przez `stoppedBy` jest zapisany `failed` z tym powodem (A6). `RunWatchdog.check` nie może zostać zatrzymany przez wyjątek z `alerts.raise` (F5). Trzy niesklasyfikowane błędy klienta LLM z rzędu zatrzymują krok jak `OUTAGE_STREAK` (F7), z doprecyzowaniem w PRD.
- `./mvnw -q -o dependency:tree | grep -ci embabel` = 0; `grep -ri embabel szczur-radar-api/src pom.xml` = 0; serwer, `migrate` i testy startują bez Embabel; `LLM_*` nadal wymagane przez `LlmConfigurationCheck`; logi konsoli w domyślnym formacie Boota.
- Testy silnika: `RunSequenceTest` (bez Springa), `RunRecorderIT` (na bazie), `PipelineRunIT` i `RunWatchdogIT` z `@TestMethodOrder(MethodOrderer.Random.class)` i resetem bazy przed każdą metodą; `./mvnw -B verify` zielony dwa razy z rzędu.
- Dokumentacja (`pipeline/CLAUDE.md`, `llm/CLAUDE.md`, `agents/assessor/CLAUDE.md`, README API, `REPOSITORY_GUIDELINES.md`, `tech-stack-api.md`, `infrastructure.md`, `deploy-plan.md`, komentarze `ci.yml`/`railway.ts`) opisuje stan bez Embabel; PR env zweryfikowany: przebieg RANGE, przerwanie przez deploy, czas startu przed i po, wygląd logów.

### Key Discoveries:

- Wyjątek akcji wychodzi dziś z `agent.get()` jako `ExecutionException` z **oryginalną** przyczyną (`PipelineAgentIT.java:543-544` asercjonuje `NoPostsFetchedException: wykop gave no post to store …`); tekst błędu przebiegu `<SimpleName>: <message>` (`PipelineRunService.java:325`) czytają SPA, `dev/mock-api.js` i cron — zostaje bez zmian.
- `await` dyspozytora na `InterruptedException` już anuluje wszystkie zadania `cancel(true)` i rzuca `IllegalStateException` (`ConcurrentAssessmentDispatcher.java:391-399`); `WykopApiClient.exchange` zamienia `InterruptedException` na `WykopApiException(INTERRUPTED)` (`api/sources/wykop/WykopApiClient.java:242-246`); `TransportRetry.sleep` na przerwaniu rzuca `LlmUnavailableException` (`api/llm/transport/TransportRetry.java:135-143`). Przerwanie wątku orkiestrującego sięga więc głęboko bez nowych mechanizmów.
- `RunRecorder.openStep` odmawia kroku w przebiegu, który nie jest `running` (`api/pipeline/state/RunRecorder.java:276-287`), a `endStep` nie zmienia kroku zamkniętego przez przerwanie (`:294-311`) — to pozostaje jedyną obroną przed zapisami kroku po zatrzymaniu.
- `submit` używa `executor.execute` bez `Future` (`PipelineRunService.java:274-277`); zadanie trzeba zarejestrować **przed** oddaniem go executorowi, inaczej `execute` może skończyć i usunąć wpis, zanim `submit` go doda.
- `ThreadPoolExecutor` czyści flagę przerwania przed następnym zadaniem, ale Hikari odmawia połączenia wątkowi z ustawioną flagą — dzisiejszy `execute` przywraca flagę dopiero po sprzątaniu (`:335-338`).
- `com.openai:openai-java-client-okhttp` 4.49.0 przychodzi dziś **wyłącznie** przez `embabel-agent-openai`; pom `spring-ai-openai` 2.0.1 deklaruje tylko `openai-java-core` + `okhttp` (`research.md`, sekcja C). `spring-ai-bom` nie ma w `~/.m2`.
- `AssessorTest.anEntryWithoutContentIsDecidedByThePrefilterWithoutACall` (`test/agents/assessor/AssessorTest.java:170-179`) i testy `TransportRetryTest` pokrywają istotę 2 z 4 testów `AssessorAgentIT`; pozostałe 2 pinują rejestr modeli Embabel.
- `OpenRouterClientTest.java:97` asercjonuje ścieżkę `/chat/completions` względem bazy; test „bez drugiego `/v1`” z `LlmProviderConfigTest:86-118` trzeba przenieść na stałą bazy `OpenRouterClient.create`.
- `PostRepositoryIT` to wzorzec testu na bazie: `@SpringBootTest(webEnvironment = NONE, properties = MigrateCommand.ENABLE_FLYWAY)`, `@Import(TestcontainersConfiguration.class)`, `@Transactional` (`test/posts/PostRepositoryIT.java:45-48`).
- `tools/render_roadmap_svg.py` zna tylko stany `proposed`/`parked`/`done` (`:271-272`), więc SVG roadmapy zmienia się dopiero przy `done` (archiwizacja).

## What We're NOT Doing

- Drugie źródło (Reddit) ani zmiany w `sources/`; `SourceIngest.fetch` dostaje tylko checkpoint przerwania między źródłami.
- Porządki granic pakietów poza tym, co wypada samo: cykl `pipeline ↔ notifications`, dwa mechanizmy zapisu `posts` (JPA w `PostStore`, SQL w `PostTransitions`) i konwertery zależne od `pipeline.run` zostają (`roadmap.md:358`).
- `FOR SHARE` w `claim` (A4, przyjęte ryzyko resztkowe), A8 (`queued` przy `submit` po zamknięciu executora; `23:59:59`), B10 (dodatkowa próba po przerwaniu przy przejściu flex→default).
- Zmiana `LlmConfigurationCheck` ani wymogu `LLM_*` dla komend operatora (decyzja S-10 zostaje).
- Własny `logback-spring.xml`; `spring.output.ansi.enabled`; jakakolwiek konfiguracja logowania — domyślny format Boota.
- Zmiana `PIPELINE_STALL_TIMEOUT`, jego domyślnej wartości ani semantyki watchdoga; dyspozytor Batch API; zmiana kontraktów `/internal`, `/api/admin/pipeline-runs` i SPA.
- Przerywanie wywołań OkHttp w locie (Gemini, OpenRouter) inaczej niż przez `LLM_TIMEOUT`.
- Testy jednostkowe dla `D-2…D-1` i `RunState.of` (`api/pipeline/api/InternalPipelineRunController`): to logika API, nie silnika; zostają w `InternalPipelineRunIT`.
- Odświeżenie `context/map/repo-map.md` i `context/map/evidence/*` (wygenerowane mapy); regeneracja `docs/visuals/roadmap.svg` przy statusie `planning`/`in-progress` (generator ich nie zna; przy `done` w archiwizacji).
- Nowe zmienne środowiskowe, migracje Flyway, zmiany `.railway/railway.ts` poza komentarzem.

## Implementation Approach

Pięć faz na jednej gałęzi `feature/pipeline-without-embabel` i w jednym PR (lekcja „jedna gałąź + jeden PR”). Najpierw silnik przestaje potrzebować Embabel przy Embabel nadal na classpath (faza 1 zielona sama), potem Embabel wychodzi z pom i properties (faza 2), potem bezpiecznik F7 na nowym silniku (faza 3), potem testy przebudowane raz, na docelowym classpath (faza 4), na końcu dokumentacja i weryfikacja w PR env (faza 5). Każda faza kończy się zielonym `./mvnw -B verify` i commitem.

Nowy silnik: `flow/RunSequence` to zwykła klasa Springa z tymi samymi współpracownikami co `PipelineAgent`, metoda `run(RunRequest)` wykonuje kroki po kolei przez `RunRecorder.step` i zwraca `RunSummary`. `PipelineRunService.execute` woła ją na wątku `pipeline-run`; zadanie przebiegu jest `FutureTask` zarejestrowanym przed oddaniem executorowi, więc watchdog może je przerwać `cancel(true)`. Klasyfikację końca przebiegu po wyjątku rozstrzyga stan w bazie (`recorder.isRunning`), nie typ wyjątku: przebieg zakończony przez kogoś innego (watchdog, `onClose`, odzyskiwanie) to INFO, własny błąd kroku to ERROR i `failed` z `<SimpleName>: <message>`. `AssessmentDispatcher.cancel(runId)` zostaje jako druga dźwignia: sięga zadań puli LLM także wtedy, gdy wątek przebiegu stoi w `llm.check()` na OkHttp, gdzie przerwanie nie działa.

## Critical Implementation Details

- **Timing & lifecycle.** `submit` tworzy `FutureTask` i wkłada go do mapy `running` **przed** `executor.execute(task)`; `execute` usuwa wpis w `finally`. `stopStalled` zachowuje kolejność S-06: `stall` + `releaseAll` w jednej transakcji → `runEnded` → `task.cancel(true)` → `dispatcher.cancel(runId)`; odwrotna kolejność pozwoliłaby `execute` zamknąć przebieg `failed` z innym błędem.
- **State sequencing.** W `catch` `execute` — jednym dla każdego `RuntimeException`, także `RunNoLongerRunningException` — najpierw `Thread.interrupted()` (czyści flagę i zapamiętuje ją) i `executor.isShutdown()`, potem dopiero `recorder.isRunning` / `close` (Hikari odmawia połączenia przerwanemu wątkowi); flaga jest przywracana tylko gdy `executor.isShutdown()` (instancja się zamyka), nigdy po `cancel(true)` watchdoga, bo ten sam wątek bierze następny przebieg.
- **Debug & observability.** Po przerwaniu kroku wyjątki wyglądają różnie (`WykopApiException(INTERRUPTED)`, `IllegalStateException("interrupted while assessing …")`, `RunInterruptedException`, `RunNoLongerRunningException`); o INFO zamiast ERROR decyduje `!recorder.isRunning(runId)` po wyjątku, a `RunNoLongerRunningException` jest INFO zawsze. `PipelineAgentIT` Order 10 i `RunWatchdogIT` Order 1 asercjonują brak ERROR — to zostaje kryterium.
- **Zależności.** Wersja `openai-java-client-okhttp` musi równać się `openai-java-core`, którą ciągnie `spring-ai-openai` 2.0.1 (4.49.0 według drzewa); `spring-ai-bom` wymaga jednorazowo sieci — jeśli nie rozwiąże się, trzy moduły dostają `${spring-ai.version}` wprost.

## Phase 1: Silnik bez Embabel

### Overview

Przebieg wykonuje `flow/RunSequence` na wątku `pipeline-run`; `PipelineRunService` traci `AgentPlatform`, `waiting`, `unwrap` i gałąź `CancellationException`; `stopStalled` przerywa zadanie przebiegu; checkpointy przerwania w `PostStore` i `SourceIngest` (A7); krok `assess` ze `stoppedBy` zamykany `failed` (A6); `RunWatchdog` chroni `alerts.raise` (F5). Embabel zostaje na classpath (`AssessorAgent` kompiluje się), więc faza jest zielona sama; `pipeline/CLAUDE.md` opisuje nowy stan.

### Changes Required:

#### 1. Sekwencja kroków

**File**: `api/pipeline/flow/RunSequence.java` (nowy), `api/pipeline/flow/PipelineAgent.java` (usunięty), `api/pipeline/flow/RunFlow.java`

**Intent**: Jedna klasa bez Embabel wykonuje kroki przebiegu po kolei; typy z `RunFlow` zostają jako dane kroków, znika `RouteRun`/`SomeOf`.

**Contract**: `@Component class RunSequence` z konstruktorem `(SourceIngest, PostStore, AssessmentSelection, AssessmentDispatcher, RunRecorder)` i metodą `RunSummary run(RunRequest request)`. RANGE: `fetch(RangeTarget)` → `store` → `selectFromStored` → `assess` → `summarize`; RETRY i POSTS: `selectFromTargets(SelectionTarget)` → `assess` → `summarize`. Każdy krok dokładnie tak jak dziś w `PipelineAgent.java:86-152`: `fetch` pisze liczniki przed rethrow `NoPostsFetchedException` (nieudany `count` jako WARN + `addSuppressed`), `store` liczy nowe posty, `assess` ustawia `recorder.error(errorSummary)`, `summarize` zamyka krok przed `finish` i zwraca `RunSummary(INTERRUPTED)`, gdy `finish` nic nie zmienił. Wyjątek kroku wychodzi z `run` bez opakowania. `RunFlow`: usunięte `SomeOf` i `RouteRun`, Javadoc opisuje sekwencję zamiast GOAP; pozostałe rekordy bez zmian.

#### 2. Krok z własnym wynikiem (A6)

**File**: `api/pipeline/state/RunRecorder.java`

**Intent**: Krok `assess`, który dyspozytor zatrzymał (`stoppedBy`), ma być zapisany `failed` z tym powodem, choć `dispatch` nie rzuca.

**Contract**: Nowe przeciążenie `<T> T step(UUID runId, RunStep step, @Nullable Integer itemsIn, Supplier<T> work, Function<T, StepOutcome> outcome)` z rekordem `RunRecorder.StepOutcome(StepStatus status, @Nullable Integer itemsOut, @Nullable String error)`; dotychczasowe `step(…, ToIntFunction<T> itemsOut)` deleguje z `SUCCEEDED`. `openStep`/`endStep` bez zmian (`endStep` ucina błąd jak dotąd). W `catch` metody `step` wywołanie `endStep(FAILED, …)` jest opakowane w `try/catch (RuntimeException)`: jego błąd idzie jako `addSuppressed` do wyjątku kroku, który jest rzucany dalej — na przerwanym wątku (`cancel(true)`, `shutdownNow`) `endStep` biegnie jeszcze przed `Thread.interrupted()` w `execute` i nie może zastąpić prawdziwej przyczyny (plan-review F4). `RunSequence.assess` mapuje `stoppedBy != null` → `StepOutcome(FAILED, ok + failed, stoppedBy)`, inaczej `SUCCEEDED`.

#### 3. Serwis przebiegów na własnym wątku

**File**: `api/pipeline/PipelineRunService.java`

**Intent**: Wątek `pipeline-run` wykonuje przebieg sam, więc przerwanie go dociera do kroku; znikają artefakty modelu wątków Embabel.

**Contract**: Konstruktor przyjmuje `RunSequence sequence` zamiast `AgentPlatform agentPlatform`; importy `com.embabel.*` usunięte. `submit(runId)`: `own.add`, `FutureTask<?> task = new FutureTask<>(() -> guarded(runId), null)`, `running.put(runId, task)`, `executor.execute(task)` — w tej kolejności (mapa `running: Map<UUID, Future<?>>` zastępuje `waiting`). `guarded(runId)` to `execute(runId)` w `try/catch (Throwable)`, który loguje ERROR „Pipeline run {} crashed its queue thread” i rzuca dalej: `FutureTask.run` łapie każdy `Throwable` do przyszłości, której nikt nie czyta, więc bez tego `Error` (OOM, `NoClassDefFoundError`) albo wyjątek z `finally` `execute` znikałby bez śladu — dziś trafia do uncaught handlera wątku (plan-review F3). `execute(runId)`: `recorder.start` → log startu → `sequence.run(RunRequest.of(run))`; jeden `catch (RuntimeException ex)` dla każdego wyjątku (także `RunNoLongerRunningException`), który na wejściu — przed jakimkolwiek zapytaniem do bazy — robi `Thread.interrupted()` (czyści flagę i ją zapamiętuje) i `boolean stopping = executor.isShutdown()`, a dopiero potem rozróżnia: `stopping` → INFO „stopped, the application is stopping”, `interrupted = true` (również gdy wyjątkiem jest `RunNoLongerRunningException`: `onClose` pisze `interrupt` przed `shutdownNow`, więc krok może odmówić w `openStep`, zanim przyjdzie przerwanie — plan-review F4); `ex instanceof RunNoLongerRunningException` → INFO „is no longer running here; its remaining steps were not started”; `!recorder.isRunning(runId)` → INFO „ended by the watchdog or an interruption; its step ended with <SimpleName>”; inaczej ERROR „Pipeline run {} failed” + `error = ex.getClass().getSimpleName() + ": " + ex.getMessage()`. `finally` jak dziś (`running.remove`, `close` + `runEnded` gdy nie `interrupted`, `own.remove`, przywrócenie flagi tylko gdy `interrupted`). `stopStalled`: po transakcji i `runEnded`: `Future<?> task = running.get(runId); if (task != null) task.cancel(true);` potem `dispatcher.cancel(runId)` (Javadoc: dlaczego obie dźwignie). `onClose`: bez zmian funkcjonalnych; Javadoc mówi, że `shutdownNow()` przerywa krok. Usunięte: `waiting`, `unwrap`, gałąź `CancellationException`, `RunNoLongerRunningException.isCauseOf` (metoda w `state/RunNoLongerRunningException.java` do usunięcia, gdy nikt jej nie woła).

#### 4. Checkpointy przerwania w krokach (A7)

**File**: `api/pipeline/state/RunInterruptedException.java` (nowy), `api/pipeline/step/PostStore.java`, `api/pipeline/step/SourceIngest.java`

**Intent**: Krok `store` i `fetch` nie wykonują kolejnej paczki ani kolejnego źródła po przerwaniu wątku; zatrzymany przebieg nie wstawia postów martwego przebiegu dłużej niż jedną paczkę.

**Contract**: `RunInterruptedException extends RuntimeException` z komunikatem `the run's thread was interrupted during <step>` i statyczną `checkpoint(UUID runId, RunStep step)`, która rzuca, gdy `Thread.currentThread().isInterrupted()` (bez czyszczenia flagi). `PostStore.store` woła ją przed każdą paczką (także pierwszą), `SourceIngest.fetch` przed każdym źródłem. Komunikaty i liczniki poza tym bez zmian.

#### 5. Watchdog odporny na błąd alertu (F5)

**File**: `api/pipeline/RunWatchdog.java`

**Intent**: Wyjątek z `alerts.raise` w `catch (Throwable)` nie może zatrzymać kolejnych sprawdzeń (`scheduleWithFixedDelay`).

**Contract**: W `check()` wywołanie `alerts.raise(...)` w osobnym `try/catch (Throwable)` z linią ERROR „The pipeline watchdog could not raise WATCHDOG_FAILED for run {}”; pętla idzie dalej. Reszta klasy bez zmian.

#### 6. Dyspozytor — przerwany `await` oznacza zadania jako anulowane

**File**: `api/pipeline/assess/ConcurrentAssessmentDispatcher.java`, `api/pipeline/assess/AssessmentDispatcher.java`

**Intent**: Przerwanie wątku przebiegu (watchdog `cancel(true)`, `onClose` `shutdownNow()`) ma kończyć zadania puli tak samo jak `cancel(runId)`: bez outage'u, bez `Outcome.Error`, bez linii ERROR. Dziś `await` anuluje futures bez ustawienia flagi `cancelled` (tę ustawia tylko `cancel(runId)`, `:237`), a `finally` w `dispatch` (`:219`) usuwa wpis `dispatching`, więc późniejszy `dispatcher.cancel(runId)` nic nie znajduje; przy `onClose` nie jest wołany wcale (plan-review F1).

**Contract**: W `await` (`:391-399`), w gałęzi `InterruptedException`: najpierw flaga `cancelled` paczki (`tasks.cancelled()`, ta sama, którą ustawia `cancel(runId)`), potem `cancel(true)` na futures, potem `IllegalStateException` jak dotąd. `cancel(runId)` zostaje jako druga dźwignia: sięga zadań puli także wtedy, gdy wątek przebiegu stoi w `llm.check()` na OkHttp, gdzie przerwanie nie działa. Javadoc portu i implementacji opisuje obie drogi.

#### 7. Testy fazy 1

**File**: `test/pipeline/flow/RunSequenceTest.java` (nowy), `test/pipeline/PipelineRunServiceTest.java`, `test/pipeline/RunWatchdogTest.java`, `test/pipeline/assess/ConcurrentAssessmentDispatcherTest.java`, `test/pipeline/PipelineAgentIT.java`, `test/pipeline/RunWatchdogIT.java`

**Intent**: Logika sekwencji i gałęzie `execute`/`stopStalled` dostają testy bez Springa; istniejące ITy tylko dostosowane (pełna przebudowa w fazie 4).

**Contract**: `RunSequenceTest` (Mockito, bez Springa): RANGE wykonuje 5 kroków w kolejności i każdy raz; RETRY i POSTS wykonują 3; `fetch` z `NoPostsFetchedException` pisze liczniki przed rethrow, a nieudany `count` ląduje w `addSuppressed` i WARN; `store` liczy nowe posty; `assess` ze `stoppedBy` zamyka krok `FAILED` z tym tekstem i ustawia `recorder.error`; `summarize` zamyka krok przed `finish` i zwraca `INTERRUPTED`, gdy `finish` zwraca `false`; krok rzucający wyjątek kończy `run` tym wyjątkiem bez kolejnego kroku (odpowiednik testu FIRE_ONCE z lekcji); `RunNoLongerRunningException` z `step` przerywa sekwencję. `PipelineRunServiceTest`: bez `AgentPlatform`; `execute` z mockiem `RunSequence`: sukces (brak ERROR, `close` nie zmienia statusu), wyjątek kroku → ERROR + `fail(runId, "<SimpleName>: <message>")` + `runEnded`, wyjątek przy `isRunning == false` → INFO bez `fail`, wyjątek przy zamkniętym executorze → bez `close`/`runEnded` i z przywróconą flagą (także gdy tym wyjątkiem jest `RunNoLongerRunningException`), `RunNoLongerRunningException` przy działającym executorze → INFO, `Error` rzucony przez `execute` (np. z `close`) → linia ERROR z zadania `submit` mimo `FutureTask`; `stopStalled`: kolejność `stall` → `releaseAll` → `runEnded` → `cancel(true)` → `dispatcher.cancel` (InOrder) i `false`, gdy `stall` nic nie zmienił; dotychczasowe 5 testów zachowane. `RunWatchdogTest`: `alerts.raise` rzucający `RuntimeException` i `Error` nie przerywa sprawdzania pozostałych przebiegów. `ConcurrentAssessmentDispatcherTest`: wątek `dispatch` przerwany w `await` (zadanie w locie na latchu) → flaga `cancelled` ustawiona przed anulowaniem, zadanie kończy się jako `NotTaken` (bez outage'u, bez `releaseFailed`, bez ERROR), `dispatch` rzuca `IllegalStateException`; dotychczasowe testy bez zmian. `PipelineAgentIT`/`RunWatchdogIT`: usunięte `@Autowired AgentPlatform`, druga instancja serwisu budowana z beanem `RunSequence`; asercje odwołujące się do „agent”/„action” w tekstach logów dostosowane; scenariusze bez zmian.

#### 8. Opis pakietu

**File**: `api/pipeline/CLAUDE.md`

**Intent**: Dokument pakietu opisuje sekwencję zamiast agenta, przerwanie zamiast zombie i regułę „krok wykonuje się raz” zamiast FIRE_ONCE.

**Contract**: Nagłówek i „Where things are” (`flow/ RunSequence (the steps in order), RunFlow (the step types)`), schemat przebiegu, tabela klas; sekcja „Why a future, not the queue thread” zastąpiona „Interruption reaches the step” (co obserwuje przerwanie, co kończy się na `LLM_TIMEOUT`/`socketTimeout`, obie dźwignie `stopStalled`, klasyfikacja INFO/ERROR); reguła FIRE_ONCE → „a step with side effects runs once; retries live only below the step”; A6 w opisie `RunRecorder.step`; sekcja Tests zaktualizowana o `RunSequenceTest`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe zielone: `cd szczur-radar-api && ./mvnw -B test`
- Pełna weryfikacja z Testcontainers zielona: `cd szczur-radar-api && ./mvnw -B verify`
- Pakiet przebiegu bez Embabel: `grep -rl 'com.embabel' szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline` zwraca nic
- `RunSequenceTest` i rozszerzony `PipelineRunServiceTest` istnieją i przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest='RunSequenceTest,PipelineRunServiceTest,RunWatchdogTest'`

#### Manual Verification:

- Przegląd `api/pipeline/CLAUDE.md`: żadne zdanie nie opisuje Embabel, agenta ani zombie jako stanu bieżącego

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 2: Embabel poza projektem

### Overview

Embabel wychodzi z pom, properties i kodu poza przebiegiem; Spring AI i openai-java deklarowane wprost; `AssessorAgent` i jego IT usunięte; `LlmProviderConfigTest` zachowuje walidację `LLM_*`; `llm/CLAUDE.md` i `agents/assessor/CLAUDE.md` zaktualizowane. Fail-fast `LlmConfigurationCheck` bez zmian.

### Changes Required:

#### 1. Zależności

**File**: `szczur-radar-api/pom.xml`

**Intent**: Usunąć Embabel i obejście, które istniało tylko dla jego autokonfiguracji; zadeklarować wprost to, co klienci `llm/` naprawdę importują.

**Contract**: Usunięte: `embabel-agent.version` (`:21-22`), import BOM `embabel-agent-dependencies` (`:33-39`), `embabel-agent-starter` i 3 startery dostawców (`:86-105`), `spring-ai-autoconfigure-model-tool` (`:106-112`). Dodane: import `org.springframework.ai:spring-ai-bom:${spring-ai.version}` (`2.0.1`, komentarz `:22-23` przepisany: „the Spring AI line the llm clients are built on”) — a gdy nie rozwiąże się, wersja `${spring-ai.version}` wprost na modułach — oraz zależności `spring-ai-openai`, `spring-ai-deepseek`, `spring-ai-google-genai`, `com.openai:openai-java-client-okhttp` w wersji równej `openai-java-core` z drzewa (4.49.0 według `research.md`, sekcja C). Komentarz przy `json-schema-validator` (`:24`) bez odwołania do Embabel.

#### 2. Właściwości

**File**: `res/application.properties`, `res/application-gemini.properties`, `res/application-deepseek.properties`, `res/application-openrouter.properties`

**Intent**: Bez rejestru Embabel nie ma czego mapować; profile dostawców znikają.

**Contract**: Z `application.properties` usunięte linie `spring.profiles.include=${LLM_API_PROVIDER:}`, `embabel.models.default-llm=…` i trzy `spring.autoconfigure.exclude` (`:53-58`); komentarz `:48-52` mówi, że `LLM_*` czyta `llm/LlmProviderProperties`, a klient powstaje leniwie. Trzy pliki profili usunięte.

#### 3. Kod poza przebiegiem

**File**: `api/agents/assessor/AssessorAgent.java` (usunięty), `api/llm/LlmClientFactory.java`, `api/llm/LlmConfiguration.java`, `api/llm/LlmProviderProperties.java`, `api/llm/Provider.java`, `api/config/LlmConfigurationCheck.java`

**Intent**: Usunąć agenta bez wywołań i komentarze opisujące Embabel jako stan bieżący.

**Contract**: `AssessorAgent.java` usunięty (nikt w `src/main` go nie woła). Javadoc: `LlmClientFactory:12`, `LlmConfiguration:9`, `LlmProviderProperties:41-46` (bez „activates the Spring profile … Embabel's properties”), `Provider:7-9` (`id()` to wartość `LLM_API_PROVIDER` i JSON, nie nazwa profilu), `LlmConfigurationCheck:9-11` (istnieje, bo zły `LLM_*` ma zatrzymać każdy kontekst, serwer i komendy, zanim cokolwiek ruszy — bez odwołania do błędu Embabel).

#### 4. Testy fazy 2

**File**: `test/agents/AssessorAgentIT.java` (usunięty), `test/llm/LlmProviderConfigTest.java`, `test/llm/openrouter/OpenRouterClientTest.java`

**Intent**: Zachować walidację `LLM_*` i ścieżkę OpenRouter; usunąć testy rejestru Embabel.

**Contract**: `AssessorAgentIT` usunięty (prefilter bez wywołania: `AssessorTest:170-179`; jedno wywołanie przy awarii: `AssessorTest.aProviderUnavailableAfterTheRetriesIsThrownNotAFailedResult`, `TransportRetryTest`). `LlmProviderConfigTest`: usunięte 3 testy mapowania (`:47-78`) i stałe autokonfiguracji (`:41-45`); test base-url (`:80-118`) przeniesiony do `OpenRouterClientTest` jako asercja, że `OpenRouterClient.create` z bazą `…/api/v1` wysyła na `/api/v1/chat/completions` (jeśli `:97` tego nie dowodzi); helper `environment(...)` bez asercji `getActiveProfiles()` (albo asercja, że żaden profil nie jest aktywny); przypadki walidacji (`:121-262`) bez zmian.

#### 5. Opisy pakietów

**File**: `api/llm/CLAUDE.md`, `api/agents/assessor/CLAUDE.md`

**Intent**: Dokumenty mówią, że klienci budują `ChatModel` Spring AI wprost i że nie ma agenta.

**Contract**: `llm/CLAUDE.md:39-41` („outside Embabel's model beans”, `createObject`) → klienci budują `ChatModel` z `LLM_*`, bez frameworka agentowego; `:104-107` „Adding a provider” bez `application-<provider>.properties` „for Embabel's registry”. `agents/assessor/CLAUDE.md:11` bez `AssessorAgent`.

### Success Criteria:

#### Automated Verification:

- Drzewo zależności bez Embabel: `cd szczur-radar-api && ./mvnw -q dependency:tree | grep -ci embabel` wypisuje `0`
- Źródła i pom bez Embabel: `grep -rli embabel szczur-radar-api/src szczur-radar-api/pom.xml` zwraca nic
- Pełna weryfikacja zielona: `cd szczur-radar-api && ./mvnw -B verify`
- Obraz startuje bez Embabel i z fail-fast `LLM_*`: `docker build -t szczur-radar-api ./szczur-radar-api`, `docker run --rm szczur-radar-api migrate` bez `LLM_*` kończy się kodem `2` z blokiem `APPLICATION FAILED TO START`, a z `PG*` i `LLM_*` `migrate` kończy się `0` i serwer odpowiada `200` na `/api/ready` (sekwencja jak w jobie `api-container-smoke`); log startu nie zawiera `Embabel`, `Deployed agent` ani `embabel-platform`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 3: Bezpiecznik na niesklasyfikowane błędy klienta LLM (F7)

### Overview

Błąd klienta LLM poza `LlmCallException` (dziś goły `IllegalStateException` z `TransportException.unclassified`) dostaje własny typ; trzy takie błędy z rzędu zatrzymują krok `assess` jak `OUTAGE_STREAK`; PRD dostaje doprecyzowanie NFR.

### Changes Required:

#### 1. Typ błędu klienta

**File**: `api/llm/LlmClientException.java` (nowy), `api/llm/transport/TransportException.java`, `api/llm/CLAUDE.md`

**Intent**: Dyspozytor ma odróżniać błąd klienta LLM od błędu mappera albo zapisu bez zgadywania po komunikacie.

**Contract**: `public class LlmClientException extends IllegalStateException` w korzeniu `llm/` (API pakietu), konstruktor `(String message, Throwable cleanedCause)`. `TransportException.unclassified(...)` (`:131-133`) zwraca `LlmClientException`; klienci (`OpenRouterClient:206`, `GeminiClient:134`, `DeepSeekClient:149`) bez zmian w kodzie. `llm/CLAUDE.md:31-35` nazywa ten typ. Testy klientów `anUnknownSdkErrorLeavesTheClientUnclassifiedAfterOneCall` (×3) asercjonują `LlmClientException` (nadal `IllegalStateException`).

#### 2. Seria w dyspozytorze

**File**: `api/pipeline/assess/ConcurrentAssessmentDispatcher.java`, `api/pipeline/CLAUDE.md`

**Intent**: Systematyczny błąd klienta (np. po zmianie SDK) zatrzymuje przebieg po trzech postach zamiast przejść przez każdy.

**Contract**: Stała `CLIENT_ERROR_STREAK = 3` obok `OUTAGE_STREAK`; licznik `clientErrors` (`AtomicInteger`) obok `outages`. W `assess`, w `catch (RuntimeException ex)` (`:302-310`), gdy `ex instanceof LlmClientException` i nie `cancelled`: `clientErrors.incrementAndGet() >= CLIENT_ERROR_STREAK` → `runStopped.set(true)` i `stoppedBy.compareAndSet(null, "the LLM client failed " + CLIENT_ERROR_STREAK + " calls in a row with an error that is not the provider's; the last: <SimpleName>: <message>")` z jednym WARN; sam post traktowany jak dotąd (`Outcome.Error`, `releaseFailed`, technical failure, processing error). Zapisany wynik z `provenance.attempts() >= 1` zeruje `clientErrors` tak jak `outages` (`:266-268`); `LlmUnavailableException`, prefilter i błędy innych klas nie zmieniają tego licznika. Przebieg kończy się `failed` przez `AssessedBatch.status()`, krok `assess` `failed` (A6), sygnały jak każdy `failed`. Reguła w `pipeline/CLAUDE.md` „Attempts: three faults” rozszerzona o ten bezpiecznik.

#### 3. Doprecyzowanie w PRD

**File**: `context/foundation/prd.md`

**Intent**: Zmiana semantyki prób musi być zapisana przy NFR o sygnale awarii, jak poprzednie wycinki.

**Contract**: Pod wpisem S-18 (`:266`) nowa linia `> Doprecyzowanie (<data>, S-17 `pipeline-without-embabel`): …` w stylu poprzednich: trzy kolejne błędy klienta LLM, które nie są winą dostawcy ani posta (błąd SDK poza klasyfikacją), kończą przebieg `failed` z błędem „the LLM client failed 3 calls in a row …”; każdy z tych trzech postów ma jeden błąd techniczny więcej (jak w S-09, wstrzymanie po trzech), pozostałe posty wracają bez prób; sygnały jak każdy `failed`. Doprecyzowanie S-09 bez zmian. Treść bez odwołań do nazw klas.

#### 4. Testy fazy 3

**File**: `test/pipeline/assess/ConcurrentAssessmentDispatcherTest.java`, `test/pipeline/state/RunOutcomeTest.java`

**Intent**: Bezpiecznik ma test na każdą regułę: liczenie, zatrzymanie, reset, co nie liczy się do serii.

**Contract**: `ConcurrentAssessmentDispatcherTest`: trzy `LlmClientException` z rzędu → krok zatrzymany po trzecim (żaden dalszy post nie jest brany ani wołany), trzy posty z `releaseFailed` i trzema `processingError`, `stoppedBy` z tekstem bezpiecznika; zapisany wynik między błędami zeruje serię (czwarty błąd nie zatrzymuje); `RuntimeException` z `inputs.of` (mapper) nie zwiększa serii; `LlmUnavailableException` nie zeruje serii klienta; błąd klienta anulowanego zadania nie liczy się. `RunOutcomeTest`: `AssessedBatch` ze `stoppedBy` bezpiecznika i błędami technicznymi daje `FAILED` i `errorSummary` z obiema częściami.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe zielone: `cd szczur-radar-api && ./mvnw -B test`
- Pełna weryfikacja zielona: `cd szczur-radar-api && ./mvnw -B verify`
- PRD ma doprecyzowanie S-17: `grep -c 'S-17 `pipeline-without-embabel`' context/foundation/prd.md` wypisuje `1`

#### Manual Verification:

- Przegląd tekstu doprecyzowania w PRD: zgodny z doprecyzowaniem S-09 (próby, wstrzymanie) i bez nazw klas

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 4: Testy skupione i niezależne od kolejności

### Overview

`RunRecorder` dostaje test na bazie, `PostRepositoryIT` brakujące metody `PostTransitions`, `PipelineAgentIT` staje się `PipelineRunIT` z resetem bazy i losową kolejnością metod, `RunWatchdogIT` tak samo; dwa scenariusze czystej persystencji przechodzą do testów, gdzie należą.

### Changes Required:

#### 1. Zapisy stanu przebiegu na bazie

**File**: `test/pipeline/state/RunRecorderIT.java` (nowy)

**Intent**: Każdy strażnik `RunRecorder` ma własny test na Postgresie, zamiast być dowodzony pośrednio przez ITy całego przebiegu.

**Contract**: Wzorzec `PostRepositoryIT` (`@SpringBootTest(NONE, ENABLE_FLYWAY)`, `@Import(TestcontainersConfiguration)`, `@Transactional`; przebiegi wstawiane przez `PipelineRunRepository`/SQL). Przypadki: `start` tylko z `queued`; `finish`, `count`, `touch`, `error` tylko w `running` (`finish` zachowuje `error` przez `COALESCE`); `fail` z `queued` i `running`, nie z zakończonego; `step` rzuca `RunNoLongerRunningException` przed `work` w przebiegu nie-`running` i nie dodaje wiersza; krok zamknięty przez `interrupt` nie jest zmieniany przez `endStep`; nowe przeciążenie `step` z `StepOutcome(FAILED, …)` zapisuje `failed` z błędem (A6); `stall` tylko gdy aktywność starsza niż próg, zamyka otwarte kroki `failed` z tym samym błędem; `interrupt` z przyczyną zamyka kroki `interrupted`; `interruptAll` nadaje `deploy`/`restart` po `deployment_id`; `interruptOrphans` wszystko albo nic (dwa przypadki na poziomie bazy; reszta w `OrphanedRunIT`).

#### 2. Brakujące przejścia postów

**File**: `test/posts/PostRepositoryIT.java`

**Intent**: `claim`, `releaseAll` i `releaseOrphans` mają skupione testy na bazie; scenariusz Order 6 trafia tu.

**Contract**: Nowe metody: `claim` tylko ze `stored`/`failed`, tylko gdy przebieg `running`, zwraca `assessment_attempts`; `releaseAll` zwalnia tylko posty tego przebiegu; `releaseOrphans` zwalnia post, którego przebieg nie jest `queued`/`running`, i zostawia post żywego przebiegu (dawny `PipelineAgentIT` Order 6 `recoveryPutsBackAPostClaimedJustAsItsRunWasInterrupted`).

#### 3. Przebieg end-to-end bez kolejności

**File**: `test/pipeline/PipelineAgentIT.java` → `test/pipeline/PipelineRunIT.java`, `test/pipeline/PipelineStartupRecoveryIT.java`

**Intent**: Każdy scenariusz całego przebiegu działa sam i w dowolnej kolejności; 15 dotychczasowych scenariuszy zachowanych według mapy.

**Contract**: Klasa `PipelineRunIT` z `@TestMethodOrder(MethodOrderer.Random.class)` zamiast `OrderAnnotation`. `@AfterEach settle()` zwalnia każdy latch mocka LLM, który scenariusz trzyma (dawne Order 7 i 15 zwalniają go tu, nie w `finally` metody po asercjach), i czeka, aż serwis ucichnie: `service.ownRuns()` puste (dawny `awaitClosed`, dla każdego przebiegu scenariusza) i żadne zadanie puli dyspozytora w toku — `finished()` wraca, gdy `summarize` zapisze status, czyli przed `finally` w `execute` (close, linia końcowa, powiadomienie, `own.remove`), więc samo `finished()` nie wystarcza (plan-review F2). `@BeforeEach reset()` najpierw asercjonuje tę ciszę (`ownRuns()` puste, brak wierszy `queued`/`running`), potem czyści tabele jednym poleceniem `TRUNCATE notifications, pipeline_run_steps, assessments, posts, raw_payloads, pipeline_runs` (Postgres odmawia `TRUNCATE` tabeli, do której odwołuje się inna, jeśli ta nie jest w tym samym poleceniu — sześć osobnych pada na `posts`; lista jest domknięta po kluczach obcych; nietknięte zostają `users`, `activation_tokens`, `spring_session*`, `flyway_schema_history` — plan-review F6) i zeruje flagi `wykopDown`/`wykopEmpty`/`wykopTextless`; admin tworzony raz; helper `assessedDay()` wykonuje przebieg RANGE dnia i czeka na jego koniec dla scenariuszy, które potrzebują ocenionych postów. Ziarno losowej kolejności jest odtwarzalne: failsafe (`pom.xml`, `systemPropertyVariables`) ustawia `junit.jupiter.execution.order.random.seed` na `${it.seed}` (domyślnie liczba z `maven.build.timestamp`, inna w każdym buildzie), `@BeforeAll` wypisuje je linią INFO, a `./mvnw -B verify -Dit.seed=<n>` powtarza kolejność czerwonego runu z CI (`MethodOrderer.Random` sam loguje ziarno tylko na poziomie CONFIG, którego Surefire/Failsafe nie pokazują — plan-review F7). Mapa: Order 1 → `aRangeRunFetchesStoresAndAssessesEveryPostOfTheDay`; 2 → ten sam zakres po `assessedDay()`; 3 → resztka dostaje jedną próbę na żądanie; 4 → `PipelineStartupRecoveryIT.recoveryInterruptsUnfinishedRunsAndALateWorkerChangesNothing`; 5 → awaria źródła, kolejka bierze następny; 6 → `PostRepositoryIT` (pkt 2); 7 → zamykana instancja: przebieg `interrupted`/`shutdown`, krok `interrupted`, posty wracają, a wątek kolejki loguje INFO o zatrzymaniu i **nie** loguje linii końcowej ani ERROR; 8 → odrzucony klucz; 9 → liczniki rosną w trakcie; 10 → przebieg przerwany w trakcie oceny nie dostaje kroku i nie loguje ERROR; 11 → pusty zakres (`fetch` wykonany raz: jedna runda wyszukiwań); 12 → same elementy niemapowalne; 13 → część pominięta; 14 → bezpiecznik dostawcy; 15 → awaria po przerwaniu przez zamykaną instancję nic nie liczy. Każdy scenariusz asercjonuje to, co dziś, plus — tam gdzie przerwanie — że krok kończy się po przerwaniu, nie po `LLM_TIMEOUT`.

#### 4. Watchdog bez kolejności

**File**: `test/pipeline/RunWatchdogIT.java`

**Intent**: Pięć scenariuszy watchdoga działa niezależnie; scenariusz 1 dowodzi, że przerwanie dociera do kroku.

**Contract**: `@TestMethodOrder(MethodOrderer.Random.class)`, `@AfterEach settle()` i `@BeforeEach reset()` jak w `PipelineRunIT` (Order 2 i 5 kończą się dziś po `finished()`, przed `finally` w `execute`), helper zamiast polegania na postach z Order 1 (Order 2 i 5). Order 1 dodatkowo asercjonuje, że po `stall` krok `assess` zatrzymanego przebiegu kończy się linią „ended … after its run had stopped” w czasie rzędu sekund (nie `LLM_TIMEOUT`) i że zadanie LLM zostało przerwane; reszta asercji bez zmian.

### Success Criteria:

#### Automated Verification:

- Pełna weryfikacja zielona dwa razy z rzędu (dwa losowe ziarna): `cd szczur-radar-api && ./mvnw -B verify && ./mvnw -B verify`
- Pojedyncze scenariusze działają same: `cd szczur-radar-api && ./mvnw -B verify -Dit.test='PipelineRunIT#aRangeRunFetchesStoresAndAssessesEveryPostOfTheDay' -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` i to samo dla jednego scenariusza z przerwaniem (`RunWatchdogIT#aRunWaitingForACallThatDoesNotEndIsStoppedAndTheRunBehindItGoesOn`)
- Brak `OrderAnnotation` w testach silnika: `grep -rl 'OrderAnnotation' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline` zwraca nic
- `RunRecorderIT` istnieje i przechodzi: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=RunRecorderIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`

#### Manual Verification:

- Przegląd mapy scenariuszy: każdy z 15 dawnych `@Order` ma następcę nazwany w tym planie, żaden nie zniknął

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 5: Dokumentacja i weryfikacja w PR env

### Overview

Dokumentacja projektu opisuje stan bez Embabel; `deploy-plan.md` dostaje sekcję S-17 ze zmianą formatu logów i pomiarem startu; PR env potwierdza przebieg, przerwanie przez deploy, czas startu i wygląd logów.

### Changes Required:

#### 1. Dokumenty repozytorium

**File**: `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`, `context/foundation/tech-stack-api.md`, `context/foundation/infrastructure.md`, `.github/workflows/ci.yml`, `.railway/railway.ts`

**Intent**: Żaden dokument bieżący nie nazywa Embabel częścią stacku ani przebiegu; historia zostaje oznaczona jako historia.

**Contract**: `REPOSITORY_GUIDELINES.md:16` (`agents` bez „Embabel”; `pipeline` z `flow/` jako sekwencja), `:29` („Spring Boot / Spring AI BOMs”), `:65` („The Java pipeline runs inside the API process”). `README.md:3` (bez „Embabel (agenci LLM)”), `:25` (wiersz `agents` bez `AssessorAgent`), `:28` (`flow/` sekwencja kroków), `:38-57` (sekcja „Dostawca LLM (Embabel)” → „Dostawca LLM”: każdy kontekst czyta `LLM_*` przez `LlmProviderProperties`, bez platformy; bez punktu o mapowaniu na właściwości Embabel; lista modeli DeepSeek bez „w Embabel”), `:70` (ocena buduje `ChatModel` wprost), `:74` (bez „rdzeń Embabel ma własny executor”). `tech-stack-api.md`: nowy blok `## Update <data> — pipeline without Embabel (S-17)` nad blokiem 2026-10-01: orkiestracja przebiegu to zwykła sekwencja kroków w Javie, klienci LLM na Spring AI 2.0.1 deklarowanym wprost, Embabel usunięty; punkt „LLM orchestration” w bloku 2026-10-01 dostaje dopisek „(superseded by the S-17 update above)”. `infrastructure.md:9` (`framework: Spring Boot 4 (szczur-radar-api) / …`), `:151` (cold start „waits for Spring”). `ci.yml:34-37` komentarz bez Embabel (każdy kontekst czyta `LLM_*`). `railway.ts:131` komentarz „LLM provider of the assessment client”.

#### 2. Deploy-plan

**File**: `context/deployment/deploy-plan.md`

**Intent**: Operator czyta, co zmieniło się w działaniu API i jak to zweryfikowano; status prawdziwy także po merge'u (lekcja).

**Contract**: W sekcji S-06 punkt `:739` przepisany: zatrzymany przebieg kończy swój krok po przerwaniu wątku (w `await`, w kliencie Wykopu, między paczkami `store`), a wywołanie na OkHttp w locie po `LLM_TIMEOUT`. Nowa sekcja `## S-17: przebieg bez Embabel (pipeline-without-embabel)` z: linią `**Status:** delivered by PR #<N>; production verification and closure in issue #76.`; „What changed for the operator” (brak linii Embabel na starcie, domyślny format logów Boota — przykład linii przed i po, przerwanie docierające do kroku, bezpiecznik F7, krok `assess` `failed` przy zatrzymaniu); „PR-env verification” (wyniki z punktu 3 poniżej z nazwą środowiska i datami); „Startup time” (linie `Started SzczurRadarApiApplication in N s` przed i po, na PR env i lokalnie w Dockerze); „Runbook — after the merge (operator; tracked in issue #76)”: pierwsza noc crona, czas startu produkcji po pierwszym budzeniu, sprawdzenie `/api/ready`; „Rollback: fix-forward only”. Sekcje `:62` i `:68` (pomiary F-03) bez zmian jako historia.

#### 3. Weryfikacja w PR env

**File**: (bez pliku; wyniki trafiają do `deploy-plan.md` i komentarza w issue #76)

**Intent**: Największa zmiana kamienia musi być sprawdzona tam, gdzie działa: na Railway z prawdziwym deployem i snem.

**Contract**: Na PR env: (1) przebieg RANGE jednego dnia z `/admin` kończy się `succeeded`/`partial`, wszystkie kroki zamknięte, `/api/deploy-safety` `safe:true` po końcu; (2) przerwanie przez deploy: start przebiegu RANGE kilku dni, push commita w trakcie `assess` → nowa instancja: przebieg `interrupted` z przyczyną `deploy`, `assessing` = 0, powiadomienie w dzwonku, brak ERROR w jej logu; stara instancja: linia „step assess … after its run had stopped” w ciągu sekund po SIGTERM; (3) czas startu: trzy linie `Started … in N s` po uśpieniu i obudzeniu przed zmianą (z logów produkcji lub PR env na `main`) i po zmianie (PR env); (4) logi: domyślny wzorzec Boota, brak `Deployed agent`, `embabel-platform`, `Default LLM`; (5) `migrate` pre-deploy przechodzi, a `LLM_*` nadal wymagane (log `APPLICATION FAILED TO START` przy teście z brakującą zmienną na PR env nie jest potrzebny — dowodzi tego faza 2).

### Success Criteria:

#### Automated Verification:

- Dokumenty bieżące bez Embabel: `grep -il embabel REPOSITORY_GUIDELINES.md szczur-radar-api/README.md .github/workflows/ci.yml .railway/railway.ts context/foundation/infrastructure.md` zwraca nic
- Deploy-plan ma sekcję S-17 ze statusem w formacie lekcji: `grep -c 'S-17: przebieg bez Embabel' context/deployment/deploy-plan.md` wypisuje `1` i `grep -c 'Status:\*\* delivered by PR' context/deployment/deploy-plan.md` nie maleje
- CI zielone na PR (wszystkie joby, w tym `api-container-smoke` i `railway-config`): `gh pr checks <PR> --watch`

#### Manual Verification:

- PR env: przebieg RANGE kończy się `succeeded`/`partial` ze wszystkimi krokami zamkniętymi, `/api/deploy-safety` wraca do `safe:true`
- PR env: przerwanie przez deploy kończy przebieg `interrupted` (`deploy`), posty wracają, dzwonek ma powiadomienie, nowa instancja bez ERROR, stara kończy krok w ciągu sekund
- PR env: czas startu po obudzeniu zmierzony przed i po (trzy linie `Started … in N s` każda) i zapisany w `deploy-plan.md`
- PR env: logi w domyślnym formacie Boota, bez linii Embabel; operator potwierdza, że format jest czytelny w Railway

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Testing Strategy

### Unit Tests:

- `RunSequenceTest`: kolejność i jednokrotność kroków dla RANGE/RETRY/POSTS, liczniki `fetch` przed rethrow, A6, `summarize` z `INTERRUPTED`, wyjątek kroku bez kolejnego kroku.
- `PipelineRunServiceTest`: klasyfikacja końca przebiegu (ERROR + `failed` / INFO po zatrzymaniu przez innych / INFO przy zamykaniu bez `close`), kolejność w `stopStalled`, dotychczasowe ścieżki awarii `close`/`onClose`/`cleanUpOrphans`.
- `RunWatchdogTest`: F5 — błąd `alerts.raise` nie zatrzymuje sprawdzeń.
- `ConcurrentAssessmentDispatcherTest`: przerwany `await` oznacza zadania `cancelled` (faza 1, F1 przeglądu planu); F7 — seria, zatrzymanie, reset, co nie liczy się; dotychczasowe 20 testów bez zmian semantycznych.
- Klienci `llm/`: `unclassified` → `LlmClientException`.

### Integration Tests:

- `RunRecorderIT` (nowy) i `PostRepositoryIT` (rozszerzony): każdy strażnik warunkowy na Postgresie.
- `PipelineRunIT`: 13 scenariuszy całego przebiegu z mockiem LLM i lokalnym Wykopem, losowa kolejność z odtwarzalnym ziarnem (`-Dit.seed`), `settle()` po każdym scenariuszu i reset bazy jednym `TRUNCATE`; `PipelineStartupRecoveryIT` + scenariusz odzyskiwania z późnym workerem.
- `RunWatchdogIT`: zatrzymanie docierające do kroku, slow-but-progressing, cudzy przebieg, `socketTimeout`, bezpiecznik zamiast watchdoga.
- Bez zmian: `OrphanedRunIT`, `InternalPipelineRunIT`, `LlmOutageIT`, `NotificationsIT`, `JpaSchemaIT`.

### Manual Testing Steps:

1. PR env: uruchom przebieg RANGE jednego dnia z `/admin`; sprawdź `/admin/runs/:id` (kroki zamknięte) i `/api/deploy-safety`.
2. PR env: uruchom przebieg RANGE 3–4 dni; w trakcie `assess` wypchnij commit; po deployu sprawdź status `interrupted`/`deploy`, posty `assessing` = 0, dzwonek, logi obu instancji.
3. Zbierz czasy `Started … in N s` (3 budzenia przed, 3 po) i wpisz do `deploy-plan.md`.
4. Obejrzyj logi API na PR env: format, brak linii Embabel.

## Performance Considerations

- Start aplikacji: znika skan 8 pakietów Embabel, `AutoRegistration`, eager beany dostawcy i autokonfiguracje klienta MCP/chat-client; nasz `LlmClient` jest `@Lazy`, więc po zmianie na starcie nie powstaje bean związany z LLM poza `llmProviderProperties`. Oczekiwany krótszy czas budzenia ze snu; wartość mierzona, nie obiecywana (faza 5).
- Przebieg: kroki na wątku `pipeline-run` zamiast puli Embabel; jeden wątek mniej na przebieg; brak zmiany w puli LLM (`PIPELINE_LLM_CONCURRENCY`).
- `RunInterruptedException.checkpoint` to odczyt flagi, bez I/O; `recorder.isRunning` w `catch` to jedno zapytanie na zakończony błędem przebieg.

## Migration Notes

- Bez migracji Flyway i bez nowych zmiennych; `.railway/railway.ts` zmienia tylko komentarz. Rollback: fix-forward (revert PR to kolejny deploy).
- Merge poza oknem nocnego przebiegu (00:30–01:30 UTC) i nie podczas `RUNNING` (strażnik deployu, `/api/deploy-safety`).
- Po merge'u: pierwsza noc crona i czas startu produkcji weryfikowane w issue #76; `roadmap.md` → `done`, notka wersji, `tools/render_roadmap_svg.py:169,272` → `done` i regeneracja `docs/visuals/roadmap.svg` (archiwizacja).

## References

- Research: `context/changes/pipeline-without-embabel/research.md`
- Roadmapa S-17: `context/foundation/roadmap.md:273-284`; issue #76
- Audyt po MVP: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:65,123-158,235-241,271`
- Pochodzenie (S-06 F1): `context/archive/2026-10-15-pipeline-run-history-dashboard/reviews/plan-review.md:25-34`
- Kontrakt prób S-09 i F7: `context/archive/2026-10-20-attempt-accounting/plan.md`, `follow-ups/review-fixes.md:6-15`
- F5 S-18: `context/archive/2026-10-24-error-visibility/reviews/impl-review.md:106-117`
- Wzorzec testu na bazie: `test/posts/PostRepositoryIT.java:45-48`
- Lekcje: `context/foundation/lessons.md` (FIRE_ONCE `:82-87`, ponowienia źródła `:141-147`, jedna gałąź/PR, status w deploy-plan)

## Addendum (implementation, 2026-10-26)

Adaptacje przyjęte w implementacji i w przeglądzie implementacji (`reviews/impl-review.md`), bez zmiany kontraktów faz powyżej:

- Faza 5 §3 (2) i Manual Testing krok 2: przerwanie przez deploy na PR env wywołuje `railway redeploy -s szczur-radar-api -e szczur-radar-pr-110 -y` (albo `redeploy` z MCP Railway), nie push commita — strażnik deployu S-14 wstrzymuje deploy pusha, dopóki przebieg jest `RUNNING` (do 90 min; potem `railway-config` kończy się czerwono i Wait for CI nie wdraża tego commita). Procedura: `context/deployment/deploy-plan.md`, sekcja S-17, „PR-env verification”.
- Faza 5 §2 i wiersz 5.6: wyniki PR env (daty, id deploymentów, sześć linii `Started … in N s`) trafiają do tabel sekcji S-17 w `deploy-plan.md` (przegląd F1), nie do komentarzy issue #76; do #76 idą tylko wyniki produkcji.
- Key Discoveries („`RunWatchdogIT` Order 1 asercjonuje brak ERROR”): ten scenariusz sprawdzał tylko dwa konkretne komunikaty; przegląd F7 dodał mu ogólną asercję braku linii ` ERROR ` (jak w `PipelineRunIT`), która strzeże poprawki F2 (przerwany `llm.check()` kończy krok `RunInterruptedException` na INFO, nie linią ERROR).
- Poprawki kodu z przeglądu poza kontraktami faz: F2 (gałąź przerwania w `catch` check'u dyspozytora), F3 (`finally` w `execute` czyści spóźnione przerwanie przed `close`), F4 (`antlr4-runtime` i `guava` przypięte w `dependencyManagement`), F5 (linia INFO po zatrzymaniu z komunikatem wyjątku), F6 (pula wątków LLM jako bean `pipeline/assess/AssessmentPool`; ITy bez refleksji).

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Silnik bez Embabel

#### Automated

- [x] 1.1 Testy jednostkowe zielone: `cd szczur-radar-api && ./mvnw -B test` — 7f095c1
- [x] 1.2 Pełna weryfikacja z Testcontainers zielona: `cd szczur-radar-api && ./mvnw -B verify` — 7f095c1
- [x] 1.3 Pakiet przebiegu bez Embabel: `grep -rl 'com.embabel' szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline` zwraca nic — 7f095c1
- [x] 1.4 `RunSequenceTest` i rozszerzony `PipelineRunServiceTest` istnieją i przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest='RunSequenceTest,PipelineRunServiceTest,RunWatchdogTest'` — 7f095c1

#### Manual

- [x] 1.5 Przegląd `api/pipeline/CLAUDE.md`: żadne zdanie nie opisuje Embabel, agenta ani zombie jako stanu bieżącego — f2a2a09 (przegląd implementacji 2026-10-26, potwierdzone przez użytkownika 2026-10-27)

### Phase 2: Embabel poza projektem

#### Automated

- [x] 2.1 Drzewo zależności bez Embabel: `cd szczur-radar-api && ./mvnw -q dependency:tree | grep -ci embabel` wypisuje `0` — 81ee77d
- [x] 2.2 Źródła i pom bez Embabel: `grep -rli embabel szczur-radar-api/src szczur-radar-api/pom.xml` zwraca nic — 81ee77d
- [x] 2.3 Pełna weryfikacja zielona: `cd szczur-radar-api && ./mvnw -B verify` — 81ee77d
- [x] 2.4 Obraz startuje bez Embabel i z fail-fast `LLM_*`: `docker build -t szczur-radar-api ./szczur-radar-api`, `docker run --rm szczur-radar-api migrate` bez `LLM_*` kończy się kodem `2` z blokiem `APPLICATION FAILED TO START`, a z `PG*` i `LLM_*` `migrate` kończy się `0` i serwer odpowiada `200` na `/api/ready` (sekwencja jak w jobie `api-container-smoke`); log startu nie zawiera `Embabel`, `Deployed agent` ani `embabel-platform` — 81ee77d

### Phase 3: Bezpiecznik na niesklasyfikowane błędy klienta LLM (F7)

#### Automated

- [x] 3.1 Testy jednostkowe zielone: `cd szczur-radar-api && ./mvnw -B test` — e118bea
- [x] 3.2 Pełna weryfikacja zielona: `cd szczur-radar-api && ./mvnw -B verify` — e118bea
- [x] 3.3 PRD ma doprecyzowanie S-17: `grep -c 'S-17 `pipeline-without-embabel`' context/foundation/prd.md` wypisuje `1` — e118bea

#### Manual

- [x] 3.4 Przegląd tekstu doprecyzowania w PRD: zgodny z doprecyzowaniem S-09 (próby, wstrzymanie) i bez nazw klas — e118bea (`prd.md:267`; potwierdzone przez użytkownika 2026-10-27)

### Phase 4: Testy skupione i niezależne od kolejności

#### Automated

- [x] 4.1 Pełna weryfikacja zielona dwa razy z rzędu (dwa losowe ziarna): `cd szczur-radar-api && ./mvnw -B verify && ./mvnw -B verify` — 8fb760c
- [x] 4.2 Pojedyncze scenariusze działają same: `cd szczur-radar-api && ./mvnw -B verify -Dit.test='PipelineRunIT#aRangeRunFetchesStoresAndAssessesEveryPostOfTheDay' -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` i to samo dla jednego scenariusza z przerwaniem (`RunWatchdogIT#aRunWaitingForACallThatDoesNotEndIsStoppedAndTheRunBehindItGoesOn`) — 8fb760c
- [x] 4.3 Brak `OrderAnnotation` w testach silnika: `grep -rl 'OrderAnnotation' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline` zwraca nic — 8fb760c
- [x] 4.4 `RunRecorderIT` istnieje i przechodzi: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=RunRecorderIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` — 8fb760c

#### Manual

- [x] 4.5 Przegląd mapy scenariuszy: każdy z 15 dawnych `@Order` ma następcę nazwany w tym planie, żaden nie zniknął — 8fb760c (mapa w `reviews/impl-review.md`; potwierdzone przez użytkownika 2026-10-27)

### Phase 5: Dokumentacja i weryfikacja w PR env

#### Automated

- [x] 5.1 Dokumenty bieżące bez Embabel: `grep -il embabel REPOSITORY_GUIDELINES.md szczur-radar-api/README.md .github/workflows/ci.yml .railway/railway.ts context/foundation/infrastructure.md` zwraca nic — eb26113
- [x] 5.2 Deploy-plan ma sekcję S-17 ze statusem w formacie lekcji: `grep -c 'S-17: przebieg bez Embabel' context/deployment/deploy-plan.md` wypisuje `1` i `grep -c 'Status:\*\* delivered by PR' context/deployment/deploy-plan.md` nie maleje — eb26113
- [x] 5.3 CI zielone na PR (wszystkie joby, w tym `api-container-smoke` i `railway-config`): `gh pr checks <PR> --watch` — eb26113

#### Manual

- [x] 5.4 PR env: przebieg RANGE kończy się `succeeded`/`partial` ze wszystkimi krokami zamkniętymi, `/api/deploy-safety` wraca do `safe:true` — f2a2a09 (szczur-radar-pr-110, 2026-10-26; `deploy-plan.md` S-17, Results 1)
- [x] 5.5 PR env: przerwanie przez deploy kończy przebieg `interrupted` (`deploy`), posty wracają, dzwonek ma powiadomienie, nowa instancja bez ERROR, stara kończy krok w ciągu sekund — f2a2a09 (szczur-radar-pr-110, 2026-10-26; `deploy-plan.md` S-17, Results 2)
- [x] 5.6 PR env: czas startu po obudzeniu zmierzony przed i po (trzy linie `Started … in N s` każda) i zapisany w `deploy-plan.md` — f2a2a09 (przed: produkcja 7.94/7.38/6.87 s; po: szczur-radar-pr-110 6.18/7.61/6.26 s; `deploy-plan.md` S-17, Startup time)
- [x] 5.7 PR env: logi w domyślnym formacie Boota, bez linii Embabel; operator potwierdza, że format jest czytelny w Railway — f2a2a09 (szczur-radar-pr-110; `deploy-plan.md` S-17, Results 4; czytelność potwierdzona przez operatora 2026-10-27)
