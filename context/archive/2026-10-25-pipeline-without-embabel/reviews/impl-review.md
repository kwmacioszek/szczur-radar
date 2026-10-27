<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-17: przebieg pipeline'u bez Embabel — Implementation Plan

- **Plan**: `context/changes/pipeline-without-embabel/plan.md`
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-10-25
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 6 observations

Drugi przegląd tego samego dnia, na prośbę użytkownika, jako niezależna weryfikacja pierwszego raportu (22:33). Trzech subagentów: odchylenia od planu (24 pozycje planu: 21 MATCH, 3 DRIFT w dokumentacji i weryfikacji PR env, 0 MISSING; żadna pozycja „What We're NOT Doing” nie naruszona), bezpieczeństwo i wzorce, oraz adwersaryjna weryfikacja siedmiu ustaleń z pierwszego raportu na kodzie. Wynik weryfikacji: wszystkie siedem potwierdzone (F3 częściowo: wyścigi są, skutek z Hikari warunkowy), ale dwie proponowane poprawki wymagały korekty, bo wprowadzałyby regresję — F3 (pominięcie `runEnded` przy `isShutdown()` gubi powiadomienie) i F5 (`ex.toString()` łamie `PipelineRunServiceTest:265`). Dwa nowe ustalenia niskiej wagi: F8 (krok zostaje `running` po `Error`, stan sprzed zmiany) i F9 (higiena testów). Wszystkie wiersze Automated odhaczone i ponownie przebiegłe zielono na 4303564; wiersze Manual 1.5, 3.4, 4.5 i 5.4–5.7 otwarte (żaden nie odhaczony bez dowodu; dla 1.5 i 3.4 dowody w repo są — poniżej). Commity: 7f095c1, 81ee77d, e118bea, 8fb760c, eb26113, epilog 4303564. PR #110 otwarty, 11 checków zielonych.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Success criteria (ponowny przebieg 2026-10-25, 23:17–23:24, na 4303564)

| Wiersz | Wynik |
|---|---|
| 1.1, 3.1 `./mvnw -B test` | PASS: 834 testów jednostkowych (w obu przebiegach `verify`) |
| 1.2, 2.3, 3.2, 4.1 `./mvnw -B verify` ×2 | PASS ×2: 834 + 270 testów; ziarna 261025221750 i 261025222029 (każda klasa loguje `-Dit.seed=<n>` na INFO) |
| 1.3 `grep com.embabel` w `pipeline` | PASS: nic |
| 1.4 `RunSequenceTest,PipelineRunServiceTest,RunWatchdogTest` | PASS: 31 testów (9 w `RunSequenceTest`) |
| 2.1 `dependency:tree | grep -ci embabel` | PASS: `0` (drzewo zmierzone offline przez subagenta; zob. F4) |
| 2.2 `grep -rli embabel` src + pom | PASS: nic |
| 2.4 smoke obrazu Dockera | PASS: uid 999 (nie root); `migrate` bez `LLM_*` → kod 2 + `APPLICATION FAILED TO START` z nazwami trzech zmiennych; z `PG*`+`LLM_*` `migrate` → 0; serwer `/api/health` gotowy, `/api/ready` 200; 0 linii `Embabel`/`Deployed agent`/`embabel-platform`/`Default LLM`; `Started SzczurRadarApiApplication in 7.47 s`; log w domyślnym formacie Boota, 0 WARN, 0 ERROR |
| 3.3 linia S-17 w PRD | PASS: `1` |
| 4.2 pojedyncze scenariusze | PASS: `PipelineRunIT#aRangeRun…` 1/1 (11,2 s), `RunWatchdogIT#aRunWaitingForACall…` 1/1 (17,6 s) |
| 4.3 `OrderAnnotation` | PASS: nic |
| 4.4 `RunRecorderIT` | PASS: 11/11 |
| 5.1 grep dokumentów | PASS: nic |
| 5.2 sekcja S-17 / liczba `Status: delivered` | PASS: `1`; 5 na gałęzi, 4 na `main` |
| 5.3 `gh pr checks 110` | PASS: 11/11 |

Dowody dla otwartych wierszy Manual: 1.5 — `pipeline/CLAUDE.md` nie ma zdania o Embabel, agencie, GOAP, FIRE_ONCE ani zombie jako stanie bieżącym (jedyne „agent” to ścieżka `agents/assessor/AssessorTest`, linia 394); 3.4 — doprecyzowanie S-17 (`prd.md:267`) nie nazywa klas, odwołuje się do prób i wstrzymania z S-09 i mówi, że S-09 obowiązuje bez zmian; 4.5 — mapa 15 scenariuszy sprawdzona nazwa po nazwie: 13 w `PipelineRunIT`, Order 4 w `PipelineStartupRecoveryIT`, Order 6 w `PostRepositoryIT`, żaden nie zniknął (odhaczenie należy do człowieka).

## Findings

### F1 — Wyniki PR env i czasy startu odesłane do issue #76 zamiast do `deploy-plan.md`

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:1788, :1812-1821
- **Detail**: Kontrakt fazy 5 §2 (`plan.md:353`) wymaga, by „PR-env verification” zawierała wyniki z nazwą środowiska i datami, a „Startup time” linie `Started … in N s` przed i po, na PR env i lokalnie w Dockerze; wiersz 5.6 (`plan.md:492`) mówi wprost „zapisany w `deploy-plan.md`”. Na gałęzi: „PR-env verification” to checklista 0–4 dla człowieka ze zdaniem „Results, the environment's name, deployment IDs and times go to issue #76 as comments, not here”, a „Startup time” ma tylko lokalny Docker (przed 9,27/9,93/9,65 s na 0a17f7d; po 7,40/6,92/8,53 s na 8fb760c) i odsyła sześć wartości z PR env do issue. Wartości „przed” z PR env są obecne przez odwołanie (F-03 2026-10-02, api-docker-image 2026-10-06); brakuje wartości „po”, bo 5.4–5.7 jeszcze nie wykonano — a treść sekcji z góry przesądza, że nigdy tu nie trafią. Precedens: 7 z 9 nagłówków „PR-env verification (” w deploy-plan nazywa środowisko (często z datą) i trzyma wyniki w pliku; lekcja o linii Status przenosi do issue tylko stan produkcji. Odchylenie nie jest zapisane w planie.
- **Fix A ⭐ Recommended**: Po wykonaniu 5.4–5.7 wpisać wyniki do sekcji jako „PR-env verification (szczur-radar-pr-110, <data>)”: środowisko, id deploymentów, sześć linii startu; zmienić zdanie checklisty tak, by do #76 szły tylko wyniki produkcji.
  - Strength: Zgodne z kontraktem planu, wierszem 5.6 i resztą deploy-plan; operator znajduje dowód tam, gdzie runbook.
  - Tradeoff: Jeszcze jeden commit dokumentacji na gałęzi po kontrolach ręcznych (CI tylko dla docs).
  - Confidence: HIGH — sekcje :368, :513, :623, :1029 mają ten kształt.
  - Blind spot: None significant.
- **Fix B**: Zapisać odchylenie w planie (addendum pod fazą 5, nie przepisywanie kontraktu — `context/` to ślad planowania): wyniki PR env idą do komentarzy issue #76.
  - Strength: Bez zmiany deploy-plan; dowody obok weryfikacji produkcji w jednym miejscu.
  - Tradeoff: Łamie precedens repo; runbook nie pokazuje, jak zmianę sprawdzono.
  - Confidence: MED — obronne, ale żadna inna sekcja tak nie robi.
  - Blind spot: Czy operator w awarii czyta najpierw issue, czy deploy-plan.
- **Decision**: FIXED via Fix A (sekcja S-17 w deploy-plan.md: nagłówek z nazwą środowiska, tabela „Results” i tabela czasów startu na Railway do wypełnienia po 5.4–5.7; do #76 tylko produkcja)

### F2 — Przerwanie w trakcie `llm.check()` daje linię ERROR zatrzymanego przebiegu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:176-185
- **Detail**: `dispatch` wykonuje `llm.check()` na wątku `pipeline-run` (przed zmianą: na wątku Embabel, gdzie przerwanie nie docierało). `TransportRetry.java:135-144` zamienia przerwanie między próbami na `LlmUnavailableException("interrupted between attempts")` z zachowaną flagą; wywołanie DeepSeek w locie na `HttpClient` JDK też jest przerywalne (`IOException` → `CONNECTION` → odczekanie → to samo). `catch` loguje `ERROR The LLM check before run {} failed; no post is assessed` ze stosem i zwraca paczkę ze `stoppedBy`; wszystko dalej jest już INFO i warunkowe (`endStep` zastaje krok zamknięty, `summarize` → `RunNoLongerRunningException` → INFO), więc ta linia ERROR jest jedynym problemem; stan bazy jest poprawny. Realny wyzwalacz to `onClose` → `shutdownNow()` podczas check'u w locie albo w jego odczekaniu (deploy przy chwiejnym dostawcy); watchdog domyślnie nie sięga (check trwa krócej niż `PIPELINE_STALL_TIMEOUT`, chyba że operator ustawił go niżej). Sprzeczne z Desired End State (`plan.md:23`), z tekstem S-17 w deploy-plan („A stopped run gives no ERROR line”, „stara instancja bez ERROR”) i z kontrolą 5.5. Żaden test nie przerywa check'u (ITy przerywają tylko wywołania postów).
- **Fix**: W tym `catch`, gdy `Thread.currentThread().isInterrupted()`: INFO `Run {} assess: the LLM check was interrupted by the run's stop` i `throw new RunInterruptedException(runId, RunStep.ASSESS)` — `execute` klasyfikuje to jako INFO, `RunRecorder.step` tłumi nieudany `endStep` (zbudowany na to, plan-review F4), a `dispatching.put` jest dopiero po check'u, więc nie ma czego wyrejestrować. Jeden test w `ConcurrentAssessmentDispatcherTest`: check trzymany na latchu, wątek `dispatch` przerwany → brak ERROR, `RunInterruptedException`. Razem z ogólną asercją „brak ` ERROR `” w scenariuszu watchdoga (F7b).
- **Decision**: FIXED (gałąź `isInterrupted()` w `catch` check'u: INFO + `RunInterruptedException`; test `aCheckInterruptedByTheRunsStopEndsTheStepAsAStoppedRun`; `pipeline/CLAUDE.md` — tabela, „What observes the interrupt”, Tests)

### F3 — Przerwanie może dotrzeć do `finally` w `execute`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:329-363
- **Detail**: Flaga przerwania jest czyszczona i `isShutdown()` czytany tylko w `catch` (:331-332); `finally` (:352-363) niczego nie sprawdza. Dwa okna, oba realne i oba rzędu milisekund: (a) watchdog zatwierdza `stall`, następny `openStep` kroku rzuca, `catch` czyści flagę, a `cancel(true)` ze `stopStalled` (:420) ląduje dopiero w `finally` — albo w `stillRunning` (:371-379): nieudany odczyt `isRunning` jest tłumiony i traktowany jako „nadal running”, co daje fałszywy `ERROR Pipeline run … failed` (baza nietknięta: `fail` jest warunkowy); (b) przerwanie `onClose` zatwierdza się, gdy `summarize` jest w `finish` → `finish` zwraca `false`, `run` wraca normalnie bez `catch`, a `close` + `runEnded` biegną z flagą ustawioną, gdy `shutdownNow()` wyląduje w trakcie. Skutek warunkowy: Hikari odmawia tylko wątkowi, który musi czekać na połączenie (pula wyczerpana) — wtedy fałszywy `RUN_CLOSE_FAILED` ERROR + alert dla przebiegu, który watchdog/`onClose` zakończył poprawnie. Następny przebieg nie jest zatruty: `FutureTask.cancel` przerywa tylko w stanie NEW, a `ThreadPoolExecutor.runWorker` czyści flagę przed kolejnym zadaniem — ale `pipeline/CLAUDE.md:178` („the flag stays cleared”) opiera się na executorze, nie na kodzie.
- **Fix**: Na początku `finally`: `boolean flagged = Thread.interrupted();`, a na końcu przywrócenie flagi, gdy `interrupted || (flagged && executor.isShutdown())`. `close` i `runEnded` zostają jak są (warunkowe / idempotentne). Nie pomijać `runEnded` na podstawie samego `isShutdown()` (tak proponował pierwszy raport): przebieg, którego `finish` zatwierdził się tuż przed przerwaniem `onClose`, nie został przez `onClose` zgłoszony i to `finally` jest jego jedynym powiadomieniem — pominięcie zamieniłoby głośny fałszywy alarm na cichy brak sygnału. Jedno zdanie w `pipeline/CLAUDE.md`, że czyszczenie flagi przez executor jest zabezpieczeniem.
- **Decision**: FIXED (`finally` w `execute`: `Thread.interrupted()` na początku, flaga przywracana gdy `interrupted || (flagged && executor.isShutdown())`, `close`/`runEnded` bez zmian; dwa testy w `PipelineRunServiceTest`; Javadoc `execute` i `pipeline/CLAUDE.md`)

### F4 — Usunięcie BOM Embabel zmieniło wersje przechodnie pod ORM i SDK

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/pom.xml:38-48 (`dependencyManagement`)
- **Detail**: Zmierzone (`dependency:tree -Dverbose` offline, pom z gałęzi i z 0a17f7d): `org.antlr:antlr4-runtime` 4.13.2 → 4.13.1 (`spring-ai-model` jest bliżej w drzewie niż `hibernate-core` 7.2.24, które deklaruje 4.13.2 dla swojego parsera HQL; ANTLR sprawdza tylko major.minor, więc cicho; oba `verify` zielone); `com.google.guava:guava` 33.3.1-jre → 33.4.0-android (własny pom `google-genai` 1.65.0 chce 33.4.0-jre, `-android` prosi tylko `google-auth-library`; kod projektu Guavy nie używa); `openai-java-client-okhttp` przypięty ręcznie do 4.49.0 = `openai-java-core` ze `spring-ai-openai` (skomentowane w `pom.xml:23-25` i :107-108). Nikt tych wersji nie zarządza: `spring-boot-dependencies` 4.0.8 nie ma `org.antlr`, `spring-ai-bom` 2.0.1 nie ma antlr, guavy ani `com.openai` — czysta zasada „najbliższy wygrywa”. Nikt tego obniżenia nie wybrał.
- **Fix**: W `dependencyManagement` przypiąć `antlr4-runtime` 4.13.2 (komentarz: wersja parsera Hibernate) i `guava` 33.4.0-jre (wersja, której chce `google-genai`); w notce o aktualizacji w `llm/CLAUDE.md`: przy każdym podbiciu Spring AI sprawdzić wersję `openai-java-core` w drzewie i podbić `openai-java.version`. Potem `./mvnw -B verify`.
- **Decision**: FIXED (`pom.xml` `dependencyManagement`: `antlr4-runtime` 4.13.2 i `guava` 33.4.0-jre z komentarzem; drzewo offline potwierdza; `llm/CLAUDE.md`: sekcja „Bumping Spring AI or Spring Boot”; pełne `verify` na końcu triage)

### F5 — Gałąź „ended by the watchdog” gubi komunikat i stos zbiegającego się błędu

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:342-346
- **Detail**: Linia INFO nazywa tylko `ex.getClass().getSimpleName()` (tak w planie, `plan.md:98`); `endStep` zastaje krok zamknięty (`closed == 0`) i nie zapisuje błędu; nieudany odczyt stłumiony w `stillRunning` też znika. Scenariusz: odzyskiwanie nowszej instancji przerywa przebieg dokładnie wtedy, gdy Wykop albo mapper rzuca prawdziwy błąd — komunikat i stos nie lądują nigdzie. Analogiczne linie dyspozytora (:312, :332) zachowują `ex.toString()`. Rzadkie: po przerwaniu wyjątek jest niemal zawsze wyjątkiem samego przerwania.
- **Fix**: Logować `getSimpleName() + ": " + getMessage()` (format błędu przebiegu; `PipelineRunServiceTest:264-265` asercjonuje `contains("… its step ended with IllegalStateException")`, więc zostaje zielony — surowe `ex.toString()` wypisałoby pełną nazwę pakietu i zepsułoby test oraz udokumentowaną linię) i sam wyjątek na DEBUG.
- **Decision**: FIXED (linia INFO z `<SimpleName>: <message>` i wyjątek na DEBUG; asercja w `PipelineRunServiceTest` rozszerzona o komunikat; `pipeline/CLAUDE.md` i sekcja S-17 w deploy-plan)

### F6 — ITy czytają prywatne pole `executor` dyspozytora przez refleksję

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java:703, RunWatchdogIT.java:384
- **Detail**: `settle()` bierze pulę LLM przez `ReflectionTestUtils.getField(dispatcher, "executor")` — pierwsze użycie refleksji do wnętrza kodu produkcyjnego w repo (`git grep ReflectionTestUtils 0a17f7d` pusty). `settle()` strzeże izolacji każdego scenariusza, a zmiana nazwy pola albo proxy na beanie psuje go dopiero w czasie wykonania. Dostęp pakietowy nie wchodzi w grę: ITy są w `pipeline`, dyspozytor w `pipeline.assess`; `quiet()` potrzebuje tylko `getActiveCount() == 0 && getQueue().isEmpty()`.
- **Fix A ⭐ Recommended**: Zbudować pulę LLM jako nazwany bean z `PIPELINE_LLM_CONCURRENCY` i podać ją do istniejącego pakietowego konstruktora dyspozytora (:155-158); ITy wstrzykują ją `@Autowired`.
  - Strength: Realizuje regułę repo „kod równoległy dostaje własny executor”, zero refleksji, bez nowego API na porcie.
  - Tradeoff: Jedna klasa konfiguracji więcej w `pipeline/assess`.
  - Confidence: HIGH — konstruktor już przyjmuje executor.
  - Blind spot: None significant.
- **Fix B**: `boolean idle()` na porcie `AssessmentDispatcher` (Javadoc „for tests and diagnostics”), `settle()` woła je zamiast refleksji.
  - Strength: Najmniejsza zmiana, czytelna intencja.
  - Tradeoff: Metoda diagnostyczna na porcie produkcyjnym; wersja Batch API musiałaby ją też mieć.
  - Confidence: HIGH.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A (nowy bean `pipeline/assess/AssessmentPool` z `idle()`/`activeTasks()`/`queuedTasks()` i własnym `destroy()`; dyspozytor dostaje pulę z zewnątrz, bez `DisposableBean`; `PipelineRunIT`/`RunWatchdogIT` wstrzykują bean, `ReflectionTestUtils` usunięte; `pipeline/CLAUDE.md`)

### F7 — Tekst planu rozjechany z przyjętymi adaptacjami

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/pipeline-without-embabel/plan.md:65, :361, :402
- **Detail**: (a) Faza 5 §3 (:361) i Manual Testing krok 2 (:402) mówią „push commita w trakcie `assess`”; checklista S-17 w deploy-plan (:1792) używa `railway redeploy`, bo strażnik S-14 (`tools/deploy-gate/wait-safe.sh`, 90 min, potem `exit 1` → Wait for CI nigdy nie wdraża tego commita) sprawia, że push w normalnych ustawieniach nigdy nie przerwie działającego przebiegu (wyjątki: `DEPLOY_GUARD=off`, API nieosiągalne → przejście z ostrzeżeniem, przebieg wystartowany po przejściu strażnika). (b) :65 mówi, że scenariusz 1 `RunWatchdogIT` asercjonuje brak ERROR; stary i nowy test sprawdzają tylko dwa konkretne komunikaty (`RunWatchdogIT:264-265`); ogólny filtr ` ERROR ` istnieje tylko w `PipelineRunIT:411, :499` — a to właśnie w scenariuszu watchdoga pokazałaby się linia z F2. `/10x-archive` i kolejne przeglądy czytają plan jako prawdę.
- **Fix**: Dopisać krótkie „Addendum (implementation)” pod fazą 5 / w epilogu (nigdy nie przepisywać akapitów kontraktu — `context/` to ślad planowania) o `railway redeploy` zamiast pusha; dodać ogólną asercję „brak ` ERROR `” do scenariusza watchdoga (dziś przechodzi, a strzeże poprawki F2).
- **Decision**: FIXED (sekcja „Addendum (implementation, 2026-10-26)” w `plan.md` przed `## Progress`, bez zmian w istniejącym tekście; ogólna asercja braku ` ERROR ` w scenariuszu watchdoga `RunWatchdogIT`)

### F8 — `fail()` nie zamyka otwartego kroku, gdy z kroku wyjdzie `Error`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/RunRecorder.java:92-99 (oraz :184)
- **Detail**: `RunRecorder.step` łapie tylko `RuntimeException` (:184), `execute` też; `Error` (OOM, `NoClassDefFoundError`) omija oba, `guarded` loguje ERROR, `finally` → `close` → `fail` kończy przebieg `failed` — ale `fail` (inaczej niż `stall`, `interrupt`, `interruptAll`) nie woła `closeSteps`, więc wiersz kroku zostaje `running` na zawsze przy przebiegu `failed` (strona przebiegu i `inFlight`). Stan sprzed zmiany (`fail` identyczny na 0a17f7d), nie regresja; nowy silnik tylko czyni ścieżkę `Error` czytelniejszą (`guarded`).
- **Fix**: `closeSteps(List.of(runId), StepStatus.FAILED, error)` w `fail`, w tej samej transakcji (jak `stall`), z przypadkiem w `RunRecorderIT`; alternatywnie `catch (Throwable)` w `step` z rethrow.
- **Decision**: FIXED (`RunRecorder.fail` zamyka otwarte kroki `failed` z tym samym błędem w jednej transakcji, jak `stall`; przypadek w `RunRecorderIT`; `pipeline/CLAUDE.md`)

### F9 — `settle()` nie zamyka instancji ze `stoppingInstance()`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java:228, :680-685
- **Detail**: Dwa scenariusze budują drugą instancję serwisu i same wołają `stopping.onClose()` (:383, :648). `settle()` zwalnia latche i czeka, aż `ownRuns()` każdej instancji będzie puste, ale nigdy ich nie zamyka — scenariusz, który padnie przed własnym `onClose()`, zostawia żywy wątek `pipeline-run` do końca JVM i przebieg, który dokończy się normalnie, pisząc do bazy równolegle z `reset()` następnego scenariusza (ten asercjonuje ciszę, więc padłby z mylącym komunikatem).
- **Fix**: W `settle()` po zwolnieniu latchy `stoppingInstances.forEach(PipelineRunService::onClose)` (idempotentne: `shutdownNow` na bezczynnym executorze, `interrupt` na pustej liście), potem czyszczenie listy.
- **Decision**: FIXED (`settle()` w `PipelineRunIT` woła `onClose()` każdej instancji ze `stoppingInstance()` po zwolnieniu latchy i czyści listę po uciszeniu)

## Not reported (checked)

- Semantyka silnika zgodna z planem w każdym punkcie kontraktu: kolejność w `submit` (rejestracja przed `execute`), `guarded`, jeden `catch` z `Thread.interrupted()` i `isShutdown()` przed zapytaniem do bazy, gałęzie INFO/ERROR, kolejność `stopStalled` (stall+releaseAll → runEnded → cancel(true) → dispatcher.cancel; test InOrder), `await` (flaga `cancelled` → `cancel(true)` → `IllegalStateException`), checkpointy przed każdą paczką i każdym źródłem bez konsumowania flagi, F5 w watchdogu, bezpiecznik F7 (`CLIENT_ERROR_STREAK`, reset tylko przy `attempts >= 1`, anulowane zadania nieliczone, outage osobno), `StepOutcome` i stłumiony `endStep`.
- Podwójne kończenie przebiegu: każde zakończenie to jeden warunkowy `UPDATE`; `runEnded` idempotentny (`uq_notifications_recipient_kind_run`); `running.remove` przed `close`.
- `TRUNCATE` w `reset()` jest domknięty po kluczach obcych (V3, V9), ITy tylko na Testcontainers, `reset()` asercjonuje ciszę przed czyszczeniem; `RunRecorderIT` transakcyjny.
- Sekrety: komunikat `LlmClientException`, `stoppedBy`, WARN i `last_technical_error` przechodzą przez `ProviderMessages.clean`; alerty niosą tylko nazwy klas; brak nowych tras; każdy parametr SQL bindowany.
- Granice pakietów: `llm` nie importuje `pipeline`; `settings` nietknięte; `pipeline/CLAUDE.md` zgodne z kodem (każda nazwana metoda/stała istnieje).
- Wzorce: tabulatory i układ importów z `.editorconfig`; `RunInterruptedException` lustrzane do `RunNoLongerRunningException`; `RunRecorderIT` lustrzane do `PostRepositoryIT`; `static volatile` przełączniki jak w `OrphanedRunIT`; fixture'y Wykopu to prawdziwe nagrania.
- Dodatki poza planem, wszystkie nieszkodliwe: `stillRunning()` (nieudany odczyt `isRunning` → ERROR + `failed` z odczytem w `addSuppressed`, zamiast propagacji), `RunRecorderIT.isolate()`, sufiks `(run <id>)` w `RunInterruptedException`, `RandomOrderSeed`, zmiana nazwy w `sources/CLAUDE.md`, Javadoc `LlmClient.complete`.
- `submit` po `shutdownNow()` zostawia przebieg `queued` (`RejectedExecutionException`): stan sprzed zmiany, wyłączony przez plan (A8), sprzątany przez `cleanUpOrphans`.
- Posty paczki w locie zachowują `first_run_id` zatrzymanego przebiegu — udokumentowane „at most the chunk it was in”.
- Nic z „What We're NOT Doing” nie zostało zrobione.

## Drzewo robocze (poza przeglądanymi commitami)

Niezacommitowane: `change.md` (`status: impl_reviewed`), `context/foundation/roadmap.md` (S-17 `proposed` → `in-progress`, dwa miejsca). Regeneracja `docs/visuals/roadmap.svg` przy `in-progress` jest świadomie wyłączona przez plan (`plan.md:40, :52`: generator zna tylko `proposed`/`parked`/`done`; przy `done` w archiwizacji). Pierwszy raport z 22:33 zachowany w scratchpadzie sesji.

## Triage (2026-10-26)

Wszystkie 9 ustaleń naprawione w drzewie roboczym gałęzi (F1 i F6 przez Fix A). Po poprawkach pełne `./mvnw -B verify` zielone: 837 testów jednostkowych (3 nowe: przerwany `llm.check()`, dwa spóźnione przerwania w `finally`) i 270 integracyjnych (ziarno 261026151640). Zmienione pliki: `pom.xml` (piny), `pipeline/assess/AssessmentPool.java` (nowy), `ConcurrentAssessmentDispatcher.java`, `PipelineRunService.java`, `state/RunRecorder.java`, `pipeline/CLAUDE.md`, `llm/CLAUDE.md`, testy `ConcurrentAssessmentDispatcherTest`, `PipelineRunServiceTest`, `RunRecorderIT`, `PipelineRunIT`, `RunWatchdogIT`, dokumenty `deploy-plan.md` (sekcja S-17) i `plan.md` (addendum). Niezacommitowane: commit i push należą do dalszego kroku (wiersze Manual 5.4–5.7 w PR env nadal przed człowiekiem).
