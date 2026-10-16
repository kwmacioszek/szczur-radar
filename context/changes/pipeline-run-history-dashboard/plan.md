# Historia przebiegów, postęp i powiadomienia admina (S-06) — plan implementacji

## Overview

S-06 daje adminowi trzy rzeczy, których dziś brakuje (FR-021, FR-015, NFR o sygnale awarii; `roadmap.md` S-06):

1. **Postęp trwającego przebiegu** w bazie, w `/admin`, w logu API i w heartbeacie crona. Dziś liczniki oceny zapisują się dopiero po końcu kroku `assess`, a nikt nic nie loguje.
2. **Strażnik zawieszonego przebiegu.** Po progu bez postępu (domyślnie 15 min, ale nie krócej niż najdłuższe możliwe wywołanie LLM z zapasem; na produkcji ok. 39 min) API samo kończy przebieg jako `failed` i zwalnia kolejkę. Przebieg z 2026-10-09 „trwał” ok. 22 h i blokował każdy kolejny start.
3. **Powiadomienia w aplikacji.** Admin ma dzwoneczek w nagłówku z listą powiadomień i własnym „przeczytane” dla każdego. Kliknięcie prowadzi do nowej strony przebiegu `/admin/runs/:id`. Historia w `/admin` pokazuje zakres, czas trwania, kto uruchomił i znacznik „wymaga uwagi”.

Przebieg przerwany przez deploy dostaje własną przyczynę („nowe wdrożenie”) zamiast tekstu wspólnego z awarią.

## Current State Analysis

Na podstawie `research.md` (ta zmiana) i odczytu produkcji z 2026-10-15:

- **Model i zapis.** `pipeline_runs` ma liczniki pisane warunkowym `UPDATE` (`RunRecorder.count`, `RunRecorder.java:78-92`). `PipelineAgent.assess` woła go raz, po całym kroku (`PipelineAgent.java:106-111`). W trakcie oceny przebieg ma `assessed_* = 0`.
- **Kolejka.** Jednowątkowy `ExecutorService` (`PipelineRunService.java:81-82`). Zawieszony przebieg blokuje kolejne, bo `existsActive` daje 409. Przebieg nie ma żadnego limitu czasu: jest tylko 30 s na żądanie do Wykopu (`WykopApiClient.java:40`) i `LLM_TIMEOUT` na próbę LLM.
- **Przerwanie.**
  - Odzyskiwanie na starcie (`PipelineStartupRecovery.java:57-84`, tylko serwer: `@ConditionalOnWebApplication`) i zamykanie instancji (`PipelineRunService.java:185-196`) zmieniają tylko `pipeline_runs`, a trwający krok zostaje `running` (`RunRecorder.java:126-148`).
  - Przyczynę niesie tylko tekst `error`. Przy deployu przerywa nowa instancja z tekstem „the application restarted” (produkcja 2026-10-13, `faba1678`/`b1995b4e`), takim samym jak po awarii.
- **Logi.** W `pipeline/**` jest 7 wywołań loggera, wszystkie `warn`/`error`. `llm/**` i `agents/assessor/**` nie logują nic (research § 6).
- **API admina.**
  - `GET /api/admin/pipeline-runs` zwraca strony z krokami i licznikami (`PipelineRunController.java:92-169`), bez `requestedBy`, przyczyny przerwania i postępu.
  - Trasy pojedynczego przebiegu nie ma.
  - `/internal/pipeline-runs/{id}` zwraca `RunState` bez kroku i postępu (`InternalPipelineRunController.java:85-92`).
- **Cron.** `trigger.sh` loguje stan trwającego przebiegu tylko przy zmianie statusu (`trigger.sh:222-227`). `interrupted` traktuje jak `failed` (exit 1, `:138-145`).
  - Przebieg przerwany razem z kontenerem crona (deploy) nie daje żadnego sygnału: zastąpiony deployment nie jest `CRASHED` (`deploy-plan.md`, S-05 „Production after the merge”, po #59).
- **SPA.**
  - `/admin` pokazuje 20 najnowszych przebiegów bez zakresu, czasów i odświeżania.
  - `partial` i `interrupted` mają ten sam ton (`AdminView.vue:84-91`).
  - Sygnału poza listą nie ma (`AppHeader.vue:12,35`).
  - Atrapa `dev/mock-api.js` ma nierealny przebieg `interrupted` (wszystkie kroki udane, bez błędu) i `running` bez kroku `assess` (`:58,62`).
- **Dane z produkcji (2026-10-15, odczyt operatora):**
  - obie noce kończą się `partial`, z ok. 2% postów `failed` do ponowienia (11/469, 7/400), bez `dead` i bez błędów technicznych;
  - od startu jest 1 post `dead` na ok. 1150 ocenionych;
  - najdłuższa przerwa między kolejnymi ocenami w przebiegu to 16–42 s;
  - posty z prefiltru mają `attempts = 0`;
  - `PIPELINE_LLM_CONCURRENCY` nie jest ustawione (4 wątki), tier `flex`.

## Desired End State

- **Postęp na bieżąco.** Liczniki oceny (ok, failed, dead, błędy techniczne, ponowienia HTTP, powroty flex) i `last_activity_at` przebiegu rosną po każdym poście. Każdy start i koniec kroku przesuwa `last_activity_at`.
- **Log API** ma linie INFO o starcie i końcu przebiegu, starcie i końcu kroku oraz postępie oceny (najwyżej co minutę).
- **Strażnik zawieszenia.** Przebieg bez aktywności przez `PIPELINE_STALL_TIMEOUT` (domyślnie `max(15m, najdłuższe wywołanie LLM + 5m)`, na produkcji `39m`) API kończy jako `failed` z błędem „no progress for <próg>”. Jego otwarte kroki są zamknięte, posty oddane, a czekający przebieg (np. RANGE za RETRY) rusza.
- **Przyczyna przerwania.** Przerwany przebieg ma `interruption_cause` = `deploy` (przerwał go inny deployment), `restart` (ten sam deployment albo nieznany) lub `shutdown` (instancja się zamykała), a jego otwarte kroki mają status `interrupted`.
- **Powiadomienia.** Przebieg kończący się `failed`, `interrupted` albo `partial` z nowym `dead` lub błędem technicznym tworzy po jednym powiadomieniu dla każdego admina z logowaniem.
  - Dzwoneczek w nagłówku (tylko admin) pokazuje liczbę nieprzeczytanych i 10 najnowszych.
  - Kliknięcie oznacza powiadomienie jako przeczytane i otwiera `/admin/runs/:id`.
- **Strona przebiegu** `/admin/runs/:id` pokazuje status z przyczyną, powód „wymaga uwagi”, zakres, kto uruchomił, czasy, liczniki, kroki z czasami i błędami, a przy trwającym przebiegu postęp i „ostatnia aktywność: N temu”. Odświeża się co 10 s, dopóki przebieg trwa.
- **`/admin`** pokazuje przy każdym przebiegu zakres dat, czas trwania, „cron” / „admin”, znacznik „wymaga uwagi”, przyczynę przerwania i link do szczegółów, a pod listą „Pokaż starsze”. Odświeża się co 10 s, dopóki któryś przebieg jest `queued`/`running`.
- **Cron** co `TRIGGER_HEARTBEAT_INTERVAL` (domyślnie `5m`) loguje krok i postęp trwającego przebiegu, bez dodatkowych żądań. Przy przebiegu `interrupted` podaje przyczynę. Kody wyjścia się nie zmieniają.
- **Rola `user`** nie widzi dzwoneczka. Trasy przebiegów i powiadomień dają jej 403, a SPA przekierowuje ją z `/admin/**` na `/`.

**Weryfikacja:**
- `./mvnw -B verify`, `npm test`, `npm run build`, `tools/pipeline-trigger/test.sh` przechodzą;
- `ui-after/README.md` zawiera macierz 7 stanów;
- w Railway PR env: postęp prawdziwego przebiegu, heartbeat crona, przerwanie redeployem, które pokazuje „nowe wdrożenie” i powiadomienie.

### Key Discoveries:

- `RunRecorder` zmienia stan wyłącznie warunkowym `UPDATE` (`RunRecorder.java:18-23`). Liczenie po każdym poście i zatrzymanie przez strażnika pasują do tego wzorca, a zapisy wątku-zombie po zatrzymaniu nic nie zmienią (`PostTransitions.java:55-103`: claim tylko przy `running`, record tylko gdy przebieg ma post).
- `endStep` jest bezwarunkowy (`RunRecorder.java:150-158`). Po zatrzymaniu albo przerwaniu wątek, który się obudzi, nadpisałby zamknięcie kroku, więc musi stać się warunkowy (`status = 'running'`).
- `PipelineStartupRecovery` działa tylko w serwerze i przed otwarciem portu (`PipelineStartupRecovery.java:22-39`). Strażnik też musi być tylko w serwerze, bo `migrate` startuje ten sam kontekst.
- Railway wstrzykuje `RAILWAY_DEPLOYMENT_ID` do każdego deploymentu i domyślnie zatrzymuje stary dopiero, gdy nowy działa (docs: Variables reference, Deployment teardown). Odzyskiwanie w nowej instancji widzi więc inny id niż ten zapisany przy przebiegu.
- `Provenance.attempts` i `Provenance.flexFallback` (`Provenance.java:43-47`) są dostępne w dyspozytorze po każdej ocenie. Ponowienia HTTP to `max(attempts − 1, 0)`, bo prefiltr ma `attempts = 0`.
- Ustawienie walidowane przy starcie samego serwera: wzorzec `internal/PipelineTriggerToken` (`from(PropertyResolver)`, komunikat z nazwą zmiennej, bean `@ConditionalOnWebApplication` w `SecurityConfig.java:123-128`). Parsowanie czasu z odrzuceniem gołej liczby: `ActivationSettings.java:138-141` (sam `ActivationSettings` czyta tylko CLI, `cli/LinkCommand.java:131`). W IaC deklaruje się ją przez `preserve()` (`.railway/railway.ts:156,175`).
- Embabel 1.5.2 wykonuje akcje w swojej puli (`embabel-platform-N`): `invoke()` to `invokeAsync().get()`, a wątek `pipeline-run` tylko czeka (`AgentPlatform.kt:122-127`, `ExecutorAsyncer.kt:44-70`). Przerwanie go (`shutdownNow()`) nie dociera do akcji ani do `await` dyspozytora. Kolejkę zwalnia anulowanie przyszłości przebiegu, a akcję zatrzymują zapisy warunkowe (przegląd planu, F1).
- Nowa trasa admina trafia do `ADMIN_ROUTES` (`AuthorizationCompletenessIT.java:62-67`), co obejmuje ją też testem 403 w `AdminRoleIT.java:54-59`. `AdminRoleIT` podaje jednak trasę dosłownie jako URI (`:53-61`), więc trasa ze zmienną ścieżki wymaga wypełniania zmiennych jak `concretePath()` w `AuthorizationCompletenessIT` (przegląd planu, F3).

## What We're NOT Doing

- **Powiadomień poza aplikacją.** Bez pusha, e-maila i webhooka; kody wyjścia crona się nie zmieniają, więc Railway nadal powiadamia tylko przy `failed`/`interrupted` crona (decyzja użytkownika).
- **Przycisku „Zatrzymaj przebieg”** ani osobnego ostrzeżenia przed zatrzymaniem. Jeden próg kończy przebieg i daje powiadomienie (decyzja użytkownika).
- **Dokończenia przebiegu mimo deployu** (właściciel przebiegu po instancji). To osobna decyzja z `roadmap.md:252`. S-06 tylko nazywa przyczynę.
- **Ponowień i odmów flex wywołania w toku.** Liczymy je po zakończeniu wywołania z `Provenance`; warstwa `llm/` bez zmian poza czystą funkcją `TransportRetry.maxCallDuration` dla progu strażnika (faza 2).
- **Postępu w trakcie `fetch`** (per strona Wykopu): wymagałby zmiany kontraktu `SourceFetcher` dla każdego źródła. Krok `fetch` przesuwa `last_activity_at` tylko na starcie i końcu.
- **Przybliżonego czasu zakończenia.** Zostaje w `## Parked` roadmapy.
- **Powiadomień dla roli `user`** i rodzajów innych niż przebieg. Tabela jest na nie gotowa, ale ich nie dodajemy.
- **Retencji przebiegów i powiadomień**, filtrów historii i tabeli nieudanych postów (`admin-failed-posts-table`).
- **Interfejsu sprawdzenia LLM** (`/api/admin/llm/check`) i zmian etykiety świeżości.
- **Zmiany `PIPELINE_LLM_CONCURRENCY`** ani limitów pojedynczych wywołań.
- **Wyjęcia przebiegu z Embabel** (prosta orkiestracja). Zaparkowane w roadmapie po przeglądzie planu (F1); anulowanie przyszłości przebiegu w `PipelineRunService` zniknie razem z Embabel.

## Implementation Approach

Kolejność według zależności: schemat i zapis (faza 1) → strażnik, który korzysta z `last_activity_at` (faza 2) → powiadomienia i API czytające nowe pola (faza 3) → cron na rozszerzonym `RunState` (faza 4) → SPA na gotowym API (faza 5) → zrzuty, dokumentacja i PR env (faza 6).

Każda faza backendu jest samodzielnie testowalna testami IT na Postgresie. Migracje są dwie (`V8` w fazie 1, `V9` w fazie 3), obie tylko dodają, więc stara instancja w oknie deployu działa na nowym schemacie (zasada fix-forward, `infrastructure.md`).

Regułę „wymaga uwagi” zapisujemy w jednym miejscu (`PipelineRun.needsAttention()`). Korzystają z niej tworzenie powiadomień, lista, strona przebiegu i dzwoneczek.

## Critical Implementation Details

### State sequencing

- **Liczenie po poście bez podwójnego liczenia.** Zapis liczników po każdym poście w dyspozytorze i usunięcie zbiorczego `recorder.count(…assessed(batch))` z `PipelineAgent.assess` muszą wejść w jednym commicie. `AssessedBatch` zostaje (status przebiegu i podsumowanie błędów), ale nie zapisuje już liczników.
- **Zamknięcie kroku nie może zostać nadpisane.** Gdy strażnik albo przerwanie zamknie krok, `endStep` budzącego się wątku nie może zmienić jego statusu ani błędu: zapis kroku jest warunkowy na `status = 'running'`. Nowy krok też nie powstaje w przebiegu, który już nie trwa (wstawienie warunkowe na przebieg `running`).
- **Strażnik najpierw zapisuje, potem przerywa.** Kolejność: warunkowy `UPDATE` przebiegu (`running` → `failed`, tylko gdy `last_activity_at` jest nadal starsze niż próg) i zamknięcie kroków w jednej transakcji → oddanie postów → powiadomienie → anulowanie przyszłości przebiegu (`future.cancel`). Odwrotna kolejność pozwoliłaby `execute` oznaczyć przebieg jako `failed` z innym błędem (`close`).

### Timing & lifecycle

- **Odzyskiwanie przed portem.** Przyczynę przerwania liczy odzyskiwanie, które działa przed otwarciem portu, w transakcji (`PipelineStartupRecovery.java:63-74`): `deploy`, gdy przebieg ma `deployment_id` i różni się on od bieżącego `RAILWAY_DEPLOYMENT_ID`, w pozostałych przypadkach `restart`. Powiadomienia z odzyskiwania tworzymy po zatwierdzeniu tej transakcji. Ich błąd jest logowany i nigdy nie zatrzymuje startu.
- **Bez zdarzeń Springa.** Wywołania powiadomień są bezpośrednie, nie przez `ApplicationEvent`: odzyskiwanie działa w `afterSingletonsInstantiated`, a kolejność rejestracji słuchaczy `@EventListener` względem niego nie jest gwarantowana.

## Phase 1: Postęp, przyczyna przerwania i logi przebiegu

### Overview

Przebieg zapisuje postęp po każdym poście i czas ostatniej aktywności, przerwanie zamyka kroki i nazywa przyczynę, a API loguje przebieg na poziomie INFO.

### Changes Required:

#### 1. Migracja `V8`

**File**: `szczur-radar-api/src/main/resources/db/migration/V8__pipeline_run_progress.sql`

**Intent**: Dodać kolumny postępu i przyczyny przerwania, status kroku `interrupted`, indeks postów w ocenie i zamknąć historyczne kroki, które zostały `running` w zakończonych przebiegach (np. z 2026-10-09).

**Contract**:
- `pipeline_runs`:
  - `last_activity_at TIMESTAMPTZ NULL`;
  - `llm_retries INT NOT NULL DEFAULT 0`, `flex_fallbacks INT NOT NULL DEFAULT 0`;
  - `deployment_id TEXT NULL`;
  - `interruption_cause TEXT NULL` z `ck_pipeline_runs_interruption_cause CHECK (interruption_cause IN ('deploy', 'restart', 'shutdown'))` i `ck_pipeline_runs_interruption_status CHECK (interruption_cause IS NULL OR status = 'interrupted')`.
- `pipeline_run_steps`: `ck_pipeline_run_steps_status` (V3:56) zastąpione wersją z `'interrupted'`.
- `CREATE INDEX ix_posts_assessing_run ON posts (assessing_run_id) WHERE stage = 'assessing'`.
- Poprawka danych: kroki `running` przebiegów o statusie innym niż `queued`/`running` → `interrupted`, z `finished_at` przebiegu. Przebiegi trwające w starej instancji podczas pre-deploy `migrate` nie są dotknięte.
- `MigrationsIT` (`MigrationsIT.java:38`): lista wersji kończy się na `"8"`, a nowe ograniczenia mają asercje odrzucenia.

#### 2. Model przebiegu

**File**: `pipeline/run/PipelineRun.java`, `pipeline/run/InterruptionCause.java` (nowy), `pipeline/run/StepStatus.java`, `persistence/converters/` (nowy konwerter)

**Intent**: Encja odwzorowuje nowe kolumny (tylko do odczytu poza `deployment_id` ustawianym przy utworzeniu). Przyczyna przerwania i nowy status kroku są enumami.

**Contract**:
- `InterruptionCause { DEPLOY("deploy"), RESTART("restart"), SHUTDOWN("shutdown") }` z konwerterem jak inne enumy (`EnumValueConverter`); `StepStatus.INTERRUPTED("interrupted")`.
- Pola encji: `lastActivityAt`, `llmRetries`, `flexFallbacks`, `deploymentId`, `interruptionCause`.
- Fabryki `PipelineRun.range/retry/posts` przyjmują `@Nullable String deploymentId`.
- `JpaSchemaIT` przechodzi.

#### 3. Deployment przebiegu

**File**: `pipeline/PipelineRunService.java`

**Intent**: Każdy tworzony przebieg zapamiętuje deployment instancji, która go zakolejkowała, żeby odzyskiwanie mogło rozpoznać deploy.

**Contract**: `@Value("${RAILWAY_DEPLOYMENT_ID:}")`; pusty → `null` (lokalnie, testy). Wartość trafia do fabryk w `startRange`/`startPosts`.

#### 4. Zapis postępu i aktywności

**File**: `pipeline/state/RunRecorder.java`

**Intent**: Każda zmiana przebiegu w trakcie trwania przesuwa `last_activity_at`. Liczniki obejmują ponowienia i powroty flex. Kroki zamykają się tylko raz.

**Contract**:
- `start` ustawia też `last_activity_at = now()`.
- `step`: wstawienie wiersza kroku i jego zakończenie przesuwają `pipeline_runs.last_activity_at`, warunkowo na `running`.
- `step` wstawia wiersz kroku tylko, gdy przebieg jest `running` (`INSERT … SELECT … WHERE EXISTS`). Gdy nic nie wstawił, nie wykonuje pracy i kończy akcję cichym sygnałem: osobny wyjątek, który `execute` loguje na INFO, nie ERROR. Inaczej instancja przerwana deployem albo akcja-zombie po strażniku dopisuje krok (np. `summarize: succeeded`) do przebiegu, który już nie trwa, a śmierć instancji między wstawieniem a `endStep` zostawia krok `running` w skończonym przebiegu.
- `endStep` zmienia tylko krok o statusie `running`.
- `count(runId, Counters)` dodaje też `llm_retries`, `flex_fallbacks` i ustawia `last_activity_at = now()`.
- `Counters` dostaje `llmRetries`, `flexFallbacks` i fabryki jednego posta: `assessedOk(retries, fallback)`, `assessedFailed(dead, retries, fallback)`, `processingError()`. Fabryka `assessed(AssessedBatch)` znika.
- `interrupt(runIds, cause, reason)` i `interruptAll(currentDeploymentId)` ustawiają `interruption_cause` (dla `interruptAll` per wiersz: `deploy` gdy `deployment_id IS NOT NULL AND :current IS NOT NULL AND deployment_id <> :current`, inaczej `restart`).
  - Zamykają w tej samej transakcji otwarte kroki tych przebiegów jako `interrupted` z `finished_at = now()`.
  - Tekst `error`: „interrupted: a new deployment took over” / „interrupted: the application restarted” / „interrupted: the application stopped”.
- Zapytanie `inFlight(runId)` liczy posty `assessing` danego przebiegu (indeks z `V8`).

#### 5. Liczenie po każdym poście

**File**: `pipeline/assess/ConcurrentAssessmentDispatcher.java`, `pipeline/flow/PipelineAgent.java`

**Intent**: Po każdym poście, którego ocena się zapisała, albo po błędzie technicznym dyspozytor dopisuje jego wynik do liczników przebiegu. Krok `assess` nie liczy już zbiorczo na końcu.

**Contract**:
- W zadaniu posta:
  - po `transitions.record(...) == true` → `recorder.count(runId, Counters.assessedOk/Failed(...))`, z `retries = max(provenance.attempts − 1, 0)` i `fallback = provenance.flexFallback`;
  - po `Outcome.Error` → `Counters.processingError()`;
  - `NotTaken` nie liczy niczego.
- `PipelineAgent.assess` nie woła `recorder.count`; `recorder.error(errorSummary)` zostaje.
- `LlmConfigurationException` loguje WARN z przyczyną (dziś bez logu).

#### 6. Logi INFO

**File**: `pipeline/PipelineRunService.java`, `pipeline/state/RunRecorder.java`, `pipeline/assess/ConcurrentAssessmentDispatcher.java`

**Intent**: Log API pokazuje bez bazy, co robi przebieg.

**Contract**:
- Linie INFO:
  - `Pipeline run <id> (<kind>) started` i `… ended <status> in <czas>: fetched …, new …, ok …, failed …, dead …, errors …, retries …, flex fallbacks …` w `execute`;
  - `Run <id> step <step> started (in <itemsIn>)` i `… <status> (out <itemsOut>) in <czas>` w `RunRecorder.step`;
  - postęp oceny `Run <id> assess: <done>/<N> (ok …, failed …, dead …, errors …), in flight …, retries …, flex fallbacks …` najwyżej raz na 60 s, wyzwalany zakończeniem posta, oraz zawsze po ostatnim poście.
- Bez treści postów i sekretów.

#### 7. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/**`, `MigrationsIT.java`, `JpaSchemaIT.java`

**Intent**: Udowodnić liczenie po poście, brak podwójnego liczenia, przyczyny przerwania i zamknięte kroki.

**Contract**:
- `ConcurrentAssessmentDispatcherTest`: każdy zapisany post woła `count` raz, z ponowieniami `max(attempts − 1, 0)` (przypadek `attempts = 0` daje 0) i powrotem flex; błąd techniczny liczy `processingError`; `NotTaken` nic; linia postępu w logu (`OutputCaptureExtension`).
- `PipelineAgentIT`:
  - istniejące asercje końcowych liczników bez zmian;
  - w trakcie oceny (zablokowany skryptowy `LlmClient`) `assessed_ok` rośnie, a `last_activity_at` jest ustawione;
  - zamknięcie instancji daje `shutdown` i krok `interrupted`;
  - przebieg przerwany w trakcie oceny nie dostaje nowego kroku (`summarize`), a log nie ma ERROR.
- `PipelineStartupRecoveryIT`: przebieg z `deployment_id = 'a'` przy bieżącym `'b'` → `deploy`; ten sam id albo `null` → `restart`; otwarte kroki → `interrupted`.

### Success Criteria:

#### Automated Verification:

- Migracja `V8` stosuje się na pustej bazie i odrzuca złą przyczynę i przyczynę przy statusie innym niż `interrupted`: `MigrationsIT`
- Encje zgodne ze schematem: `JpaSchemaIT`
- Liczniki po poście z ponowieniami i powrotami flex, bez podwójnego liczenia: `ConcurrentAssessmentDispatcherTest`, `PipelineAgentIT`
- Przyczyna przerwania `deploy` / `restart` / `shutdown` i kroki `interrupted`: `PipelineStartupRecoveryIT`, `PipelineAgentIT`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`

#### Manual Verification:

- Lokalny przebieg jednego dnia (Postgres z `compose.local.yaml`, jar, prawdziwe `LLM_*` i `WYKOP_*`) loguje start, kroki, postęp oceny co najwyżej co minutę i koniec, a `pipeline_runs` w trakcie oceny ma rosnące `assessed_ok` i świeże `last_activity_at`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Strażnik zawieszonego przebiegu

### Overview

API samo kończy przebieg bez aktywności dłuższej niż `PIPELINE_STALL_TIMEOUT` i zwalnia kolejkę dla następnego przebiegu.

### Changes Required:

#### 1. Ustawienie progu

**File**: `pipeline/PipelineStallSettings.java` (nowy; wzorzec beana i startu: `internal/PipelineTriggerToken.java` — `from(PropertyResolver)`, bean `@ConditionalOnWebApplication`; parsowanie czasu bez gołej liczby jak `auth/activation/ActivationSettings.java:138-141`), `llm/transport/TransportRetry.java`

**Intent**: Próg jest zmienną z jednostką, sprawdzaną przy starcie serwera, z komunikatem bez zgadywania. Domyślny próg nigdy nie jest krótszy niż najdłuższe możliwe pojedyncze wywołanie LLM, bo `llm.check()`, ogon przebiegu i mały RETRY mają w toku tylko jedno wywołanie.

**Contract**:
- `PIPELINE_STALL_TIMEOUT`, opcjonalna, parsowana `DurationStyle.detectAndParse`.
- Domyślnie `max(15m, TransportRetry.maxCallDuration(LLM_TIMEOUT, LLM_SERVICE_TIER) + 5m)`.
  - `maxCallDuration` (nowa czysta funkcja statyczna): na tier `ATTEMPTS_PER_TIER × LLM_TIMEOUT + (ATTEMPTS_PER_TIER − 1) × MAX_RETRY_AFTER`, razy dwa przy `flex` (powrót do standardu po trzech 429).
  - Produkcja (`flex`, `300s`): 34 min → próg `39m`. Bez ustawień (`default`, `120s`): 8 min → próg `15m`.
- Jawna wartość poniżej tej granicy jest dozwolona (testy, sprawdzenie ręczne), ale serwer loguje przy starcie WARN z granicą i nazwami `PIPELINE_STALL_TIMEOUT` i `LLM_TIMEOUT`.
- Goła liczba, wartość ≤ 0 lub > `24h` przerywają start serwera komunikatem z nazwą zmiennej.
- Czyta ją tylko serwer (`@ConditionalOnWebApplication`), nie `migrate` ani komendy CLI.

#### 2. Strażnik

**File**: `pipeline/RunWatchdog.java` (nowy)

**Intent**: Co jakiś czas sprawdza przebiegi tej instancji i zatrzymuje te, które nie robią postępu.

**Contract**:
- `@Component @ConditionalOnWebApplication`, własny `ScheduledExecutorService` z jednym nazwanym wątkiem (`pipeline-watchdog`).
- Sprawdzenie co `min(próg / 5, 1 min)` obejmuje wyłącznie przebiegi z `PipelineRunService.own` o statusie `running`. Przebiegów innej instancji nie dotyka: te obsługuje odzyskiwanie.
- Zatrzymanie woła `PipelineRunService.stopStalled(runId, próg)`; błędy logowane, wątek strażnika trwa.
- Gdy `own` nie ma przebiegu (sprawdzenie w pamięci), strażnik nie pyta bazy. Railway liczy każdy pakiet wychodzący, także do Postgresa, jako aktywność, więc zapytanie co minutę nie pozwoliłoby API zasnąć (docs: Serverless), a stale włączone API zbliża twardy limit $10 planu Hobby.
- `DisposableBean` zatrzymuje wątek.

#### 3. Zatrzymanie i nowa kolejka

**File**: `pipeline/PipelineRunService.java`, `pipeline/state/RunRecorder.java`

**Intent**: Zawieszony przebieg kończy się `failed` z czytelną przyczyną, a przebiegi czekające za nim ruszają.

**Contract**:
- `RunRecorder.stall(runId, Duration)`: w jednej transakcji `UPDATE pipeline_runs SET status = 'failed', finished_at = now(), error = 'no progress for <próg>: the run was stopped' WHERE id = :id AND status = 'running' AND COALESCE(last_activity_at, started_at) < now() - :próg` oraz zamknięcie otwartych kroków jako `failed` z tym samym błędem; zwraca, czy zatrzymał.
- `PipelineRunService.stopStalled`: gdy zatrzymał →
  1. `transitions.releaseAll(runId)`;
  2. WARN `Pipeline run <id> made no progress for <próg>; stopped`;
  3. `future.cancel(false)` tego przebiegu: `get()` w `execute` rzuca `CancellationException`, `close` nic nie zmienia (zapisy warunkowe), `own` traci przebieg, a ta sama jednowątkowa kolejka bierze następny. Executor zostaje `final`, bez podmiany.
- `execute` uruchamia agenta przez `invokeAsync` i na czas czekania (`get()`) trzyma jego `CompletableFuture` w mapie `runId → future`. Akcje Embabel działają w puli `embabel-platform-N`, nie w wątku `pipeline-run` (Embabel 1.5.2: `invoke()` = `invokeAsync().get()`), więc przerwanie wątku kolejki do nich nie dociera.
- `InterruptedException` z `get()` (checked, np. przy zamknięciu kontekstu) `execute` łapie jawnie i przywraca flagę przerwania; dziś omija `catch (RuntimeException)` (`PipelineRunService.java:160`).
- Akcja-zombie działa dalej w wątku Embabel, ale nie zmienia stanu: `claim` wymaga `running`, `record` wymaga posta w ocenie tego przebiegu (po `releaseAll` już nie), a `count` i kroki są warunkowe (faza 1 §4). Do końca bieżącego wywołania LLM zajmuje slot puli LLM.
- Mechanizm jest tymczasowy: zniknie razem z Embabel w przebiegu (roadmapa, `## Parked`: pipeline bez Embabel).

#### 4. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/RunWatchdogIT.java` (nowy), `PipelineStallSettingsTest.java` (nowy)

**Intent**: Udowodnić zatrzymanie, odblokowanie kolejki i brak fałszywego zatrzymania.

**Contract**:
- `PipelineStallSettingsTest`: domyślnie `15m` przy `default`/`120s` i `39m` przy `flex`/`300s`; jawne `90s`, `1h` OK (poniżej granicy z WARN); `15`, `0s`, `25h`, `abc` → błąd z nazwą zmiennej.
- `TransportRetryTest`: `maxCallDuration` dla obu tierów.
- `RunWatchdogTest` (jednostkowy): przy pustym `own` sprawdzenie nie woła bazy ani `stopStalled`.
- `RunWatchdogIT` (`PIPELINE_STALL_TIMEOUT=2s`, skryptowy `LlmClient` blokujący na zatrzasku):
  - przebieg RETRY wisi → kończy się `failed` z błędem „no progress”, krok `assess` zamknięty jako `failed`, posty wracają bez zużytej próby;
  - RANGE zakolejkowany za nim rusza i się kończy (pula LLM w teście ma ≥ 2 wątki, bo zombie trzyma jeden do zwolnienia zatrzasku);
  - akcja-zombie po zwolnieniu zatrzasku nie zapisuje oceny, liczników ani kroku;
  - przebieg oceniający powoli, ale z postępem co < 2 s, nie jest zatrzymany;
  - przebieg spoza `own` (wstawiony SQL-em jako `running`) nie jest dotknięty.

### Success Criteria:

#### Automated Verification:

- Walidacja `PIPELINE_STALL_TIMEOUT`: `PipelineStallSettingsTest`
- Zatrzymanie zawieszonego przebiegu, zamknięte kroki, oddane posty, kolejny przebieg rusza, brak fałszywego zatrzymania: `RunWatchdogIT`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`

#### Manual Verification:

- Lokalny przebieg jednego dnia z `PIPELINE_STALL_TIMEOUT=1m` kończy się normalnie (strażnik nie zatrzymuje przebiegu z postępem), a serwer z `PIPELINE_STALL_TIMEOUT=15` nie startuje i komunikat nazywa zmienną

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Powiadomienia i API admina

### Overview

Przebieg wymagający uwagi tworzy powiadomienia dla adminów. API daje listę powiadomień, oznaczenie przeczytania, stronę pojedynczego przebiegu i rozszerzone widoki przebiegu dla admina i crona.

### Changes Required:

#### 1. Migracja `V9`

**File**: `szczur-radar-api/src/main/resources/db/migration/V9__create_notifications.sql`

**Intent**: Osobna tabela powiadomień z własnym „przeczytane” (decyzja użytkownika), gotowa na inne rodzaje.

**Contract**:
- `notifications`:
  - `id UUID` (jak w innych tabelach);
  - `recipient_id UUID NOT NULL` → `users` `ON DELETE CASCADE`;
  - `kind TEXT NOT NULL` z `ck_notifications_kind CHECK (kind IN ('run_attention'))`;
  - `run_id UUID NULL` → `pipeline_runs` z `ck_notifications_run CHECK (kind <> 'run_attention' OR run_id IS NOT NULL)`;
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `read_at TIMESTAMPTZ NULL`.
- `uq_notifications_recipient_kind_run UNIQUE (recipient_id, kind, run_id)`.
- `ix_notifications_recipient_created (recipient_id, created_at DESC, id DESC)`.
- `MigrationsIT` kończy się na `"9"`.

#### 2. Reguła „wymaga uwagi”

**File**: `pipeline/run/PipelineRun.java`

**Intent**: Jedna definicja dla powiadomień, listy, strony i crona.

**Contract**: `boolean needsAttention()` = status `failed` albo `interrupted`, albo status `partial` przy `dead > 0` lub `processingErrors > 0`. Przebiegi `queued`/`running`/`succeeded` → `false`. Unit test na wszystkie statusy i oba warunki `partial`.

#### 3. Pakiet `notifications`

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/` (nowy): `Notification`, `NotificationKind`, `NotificationRepository`, `RunNotifications`, `api/NotificationController`; konwerter w `persistence/converters/`

**Intent**: Tworzyć powiadomienia o przebiegu idempotentnie i podawać je adminowi.

**Contract**:
- `RunNotifications.runEnded(UUID runId)`: wczytuje przebieg; gdy `needsAttention()` → jeden `INSERT … SELECT` dla każdego użytkownika z `role = 'admin' AND login_enabled`, `ON CONFLICT (recipient_id, kind, run_id) DO NOTHING`.
  - Wyjątek łapany i logowany (ERROR); nigdy nie przerywa ścieżki przebiegu.
- `GET /api/admin/notifications` → `{unread, items}`, gdzie `unread` to liczba nieprzeczytanych zalogowanego admina, a `items` to jego 10 najnowszych `{id, kind, createdAt, readAt, run: {id, kind, status, interruptionCause, finishedAt, assessedFailed, dead, processingErrors, error}}`.
- `POST /api/admin/notifications/{id}/read` → 204, idempotentne. Cudze lub nieznane id → 404 `not_found`. Zły UUID → 400 jak w innych trasach.
- `POST /api/admin/notifications/read` → 204: oznacza wszystkie nieprzeczytane zalogowanego admina, idempotentne. Bez tego licznik zostałby > 0, gdy nieprzeczytanych jest więcej niż 10 widocznych.

#### 4. Wywołania przy końcu przebiegu

**File**: `pipeline/PipelineRunService.java`, `pipeline/PipelineStartupRecovery.java`

**Intent**: Każda droga, którą przebieg się kończy, zgłasza to powiadomieniom.

**Contract**: `runNotifications.runEnded(id)` w czterech miejscach:
- `execute` w `finally` po `close` (podsumowanie, wyjątek);
- `onClose` dla każdego przerwanego;
- `recover` dla każdego przerwanego, po transakcji;
- `stopStalled` po zatrzymaniu.

Idempotencja (`ON CONFLICT`) pozwala na podwójne wywołanie.

#### 5. Trasy przebiegów admina

**File**: `pipeline/api/PipelineRunController.java`, `pipeline/run/PipelineRunRepository.java`

**Intent**: Strona przebiegu i bogatsza lista.

**Contract**:
- `GET /api/admin/pipeline-runs/{id}` → `RunView` albo 404 `not_found`.
- `RunView` dostaje:
  - `scheduled` (`requestedBy` = `SystemAccount.SCHEDULER`);
  - `needsAttention`, `interruptionCause`, `lastActivityAt`, `llmRetries`, `flexFallbacks`;
  - `inFlight` (`RunRecorder.inFlight` tylko dla `running`, inaczej 0).
- `StepView.status` może być `interrupted`. Lista bez zmian w kolejności i stronicowaniu.

#### 6. Stan dla crona

**File**: `pipeline/api/InternalPipelineRunController.java`

**Intent**: Heartbeat crona potrzebuje kroku i postępu z tego samego GET-a.

**Contract**: `RunState` dostaje:
- `startedAt`, `lastActivityAt`, `interruptionCause`;
- `step` (krok o statusie `running` albo `null`);
- `assessTotal` (`items_in` kroku `assess` albo `null`).

Pozostałe pola bez zmian.

#### 7. Autoryzacja i testy

**File**: `auth/AuthorizationCompletenessIT.java`, `auth/AdminRoleIT.java`, `notifications/api/NotificationsIT.java` (nowy), `pipeline/api/PipelineRunControllerIT.java`, `pipeline/api/InternalPipelineRunIT.java`, `pipeline/RunWatchdogIT.java`, `pipeline/PipelineAgentIT.java`, `PipelineRunTest` (nowy lub istniejący test encji)

**Intent**: Trasy chronione, powiadomienia tworzone dokładnie tam, gdzie trzeba.

**Contract**:
- `ADMIN_ROUTES` + `GET /api/admin/pipeline-runs/{id}`, `GET /api/admin/notifications`, `POST /api/admin/notifications/{id}/read`, `POST /api/admin/notifications/read`.
- `AdminRoleIT` wypełnia zmienne ścieżki wspólnym helperem z `concretePath()` z `AuthorizationCompletenessIT`; bez tego `buildAndExpand` rzuci „Not enough variable values” dla tras z `{id}`.
- `NotificationsIT`:
  - dwóch adminów dostaje po jednym, `user` i konto systemowe żadnego;
  - drugie `runEnded` nie dubluje;
  - `succeeded` i `partial` tylko z `failed` nie tworzą;
  - `partial` z `dead` i `partial` z błędem technicznym tworzą;
  - lista: 10 najnowszych, liczba nieprzeczytanych;
  - oznaczenie własnego → `readAt`, cudzego → 404;
  - oznaczenie wszystkich → `unread = 0` także przy więcej niż 10 nieprzeczytanych, powiadomienia innego admina nietknięte.
- `PipelineRunControllerIT`: strona przebiegu, 404, nowe pola, `scheduled` dla przebiegu crona.
- `InternalPipelineRunIT`: dokładny zbiór kluczy z nowymi polami; `step` i `assessTotal` w trakcie oceny.
- Ścieżki tworzenia: odzyskiwanie (`PipelineStartupRecoveryIT`), zamknięcie i nieudany przebieg (`PipelineAgentIT`), strażnik (`RunWatchdogIT`).

### Success Criteria:

#### Automated Verification:

- Migracja `V9` i jej ograniczenia: `MigrationsIT`, `JpaSchemaIT`
- Reguła „wymaga uwagi” dla wszystkich statusów: test jednostkowy `PipelineRun.needsAttention`
- Powiadomienia: odbiorcy, idempotencja, lista, przeczytanie, 404 cudzego: `NotificationsIT`
- Powiadomienie z każdej drogi końca przebiegu: `PipelineStartupRecoveryIT`, `PipelineAgentIT`, `RunWatchdogIT`
- Strona przebiegu, nowe pola, `RunState` dla crona: `PipelineRunControllerIT`, `InternalPipelineRunIT`
- Nowe trasy chronione (401 bez sesji, 403 dla `user`): `AuthorizationCompletenessIT`, `AdminRoleIT`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Heartbeat crona

### Overview

Cron okresowo loguje krok i postęp trwającego przebiegu oraz przyczynę przerwania, bez dodatkowych żądań i bez zmiany kodów wyjścia.

### Changes Required:

#### 1. Heartbeat w pętli oczekiwania

**File**: `tools/pipeline-trigger/trigger.sh`

**Intent**: Trwający przebieg zostawia w logu Railway ślad co kilka minut.

**Contract**:
- `TRIGGER_HEARTBEAT_INTERVAL` (domyślnie `5m`, ten sam parser czasu co inne `TRIGGER_*`; zła wartość → exit 2).
- W gałęzi „aktywny” (`trigger.sh:222-227`), gdy od ostatniego heartbeatu tego przebiegu minął interwał, z body już pobranego GET-a powstaje linia `<kind> <id>: <status>, step <step>, assessed <ok+failed>/<assessTotal> (ok …, failed …, errors …), last activity <lastActivityAt>`.
- Brakujące pole (starsze API) → `?`, nigdy błąd.
- Linia końcowa przebiegu `interrupted` dopisuje `(cause <interruptionCause>)`.
- Kody wyjścia i liczba żądań bez zmian.

#### 2. Testy i opis

**File**: `tools/pipeline-trigger/test.sh`, `tools/pipeline-trigger/README.md`

**Intent**: Heartbeat sprawdzony na atrapie API, a operator wie, co zobaczy w logu.

**Contract**:
- Atrapa zwraca nowe pola.
- Nowy przypadek z `TRIGGER_HEARTBEAT_INTERVAL=1s` i kilkoma statusami `running`: log zawiera `step assess` i `assessed 3/10`.
- Przypadek `interrupted` zawiera `cause deploy`.
- Istniejące `requests_were` bez zmian.
- README: zmienna, przykład logu z heartbeatem.

### Success Criteria:

#### Automated Verification:

- Test crona z heartbeatem, przyczyną przerwania i niezmienioną liczbą żądań: `tools/pipeline-trigger/test.sh`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: SPA — dzwoneczek, strona przebiegu i historia

### Overview

Admin widzi powiadomienia w nagłówku, stronę przebiegu z postępem na żywo i bogatszą historię w `/admin`. Wszystko według kontraktu UI repozytorium (`REPOSITORY_GUIDELINES.md` § UI).

### Changes Required:

#### 1. Klient API i etykiety

**File**: `szczur-radar-web/src/api/pipeline.ts`, `src/api/notifications.ts` (nowy), `src/labels.ts`, `src/runs.ts` (nowy)

**Intent**: Typy i funkcje dla nowych tras oraz jedno miejsce na ton, etykietę i powód „wymaga uwagi” przebiegu, wspólne dla listy, strony i dzwoneczka.

**Contract**:
- Typy:
  - `Run` + `scheduled`, `needsAttention`, `interruptionCause`, `lastActivityAt`, `llmRetries`, `flexFallbacks`, `inFlight`;
  - `StepStatus` + `interrupted`;
  - `InterruptionCause = 'deploy' | 'restart' | 'shutdown'`.
- Funkcje: `fetchRun(id)`, `fetchRuns(page, size)`, `fetchNotifications()`, `markNotificationRead(id)`, `markAllNotificationsRead()`.
- Etykiety:
  - przyczyny „nowe wdrożenie” / „restart aplikacji” / „zatrzymanie aplikacji”;
  - krok „przerwany”;
  - `formatRelative(iso, now)` („przed chwilą”, „N min temu”, „N h temu”, potem data);
  - `formatDuration(ms)`.
- `runs.ts`:
  - `runTone(run)` (przeniesione z `AdminView.vue:84-91`; `interrupted` z przyczyną `deploy` → `neutral`, inne `interrupted` → `warning`);
  - `attentionReasons(run)` (np. „1 post martwy”, „błędy techniczne: 2”, „przerwany: nowe wdrożenie”). Dla `failed` powodem jest tekst `error` dosłownie, bez dopasowywania jego treści (także zatrzymanie przez strażnika).

#### 2. Dzwoneczek

**File**: `szczur-radar-web/src/components/NotificationBell.vue` (nowy), `src/components/AppHeader.vue`

**Intent**: Admin na każdej stronie widzi liczbę nieprzeczytanych i rozwija okienko z ostatnimi powiadomieniami.

**Contract**:
- Przycisk:
  - ikona dzwonka jako inline SVG w `currentColor`;
  - licznik nieprzeczytanych na `--accent` / `--on-accent`, ukryty przy 0;
  - `aria-label` „Powiadomienia, N nieprzeczytanych”, `aria-expanded`, `aria-controls`.
- Okienko pod przyciskiem: lista 10 najnowszych. Pozycja to tekst stanu z przyczyną, rodzaj przebiegu i czas względny, a nieprzeczytana ma znacznik tekstowy „nowe” z kolorem.
  - Kliknięcie pozycji → `markNotificationRead` → przejście do `/admin/runs/:id`, okienko się zamyka. Błąd oznaczenia nie blokuje przejścia.
  - Stany: ładowanie, „Brak powiadomień.”, błąd „Nie udało się wczytać powiadomień.” z „Spróbuj ponownie”.
  - „Oznacz wszystkie jako przeczytane” (wariant drugorzędny), widoczne przy `unread > 0`: `markAllNotificationsRead`, potem licznik 0.
- Zachowanie:
  - Escape i kliknięcie poza okienkiem zamykają je, focus wraca na przycisk;
  - pobranie przy montowaniu, potem co 60 s, gdy karta jest widoczna; stop przy odmontowaniu;
  - `AppHeader` renderuje dzwoneczek tylko dla `isAdmin`.
- Okienko mieści się przy 375 px bez przewijania w poziomie.

#### 3. Strona przebiegu

**File**: `szczur-radar-web/src/views/RunDetailView.vue` (nowy), `src/router/index.ts`

**Intent**: Jeden adres na przebieg, także spoza 20 ostatnich, z wszystkim, co admin musi wiedzieć.

**Contract**:
- Trasa `{ path: '/admin/runs/:id', name: 'admin-run', meta: { admin: true } }`.
- Treść:
  - „← Panel admina”;
  - nagłówek z rodzajem i `StatusBadge` (stan + przyczyna);
  - przy `needsAttention` blok „Wymaga uwagi” z powodami (`StatusMessage` error);
  - meta: kto uruchomił (cron / admin), zakres dat w strefie Warszawy, źródła, utworzony / start / koniec, czas trwania;
  - przy `queued`/`running` blok postępu: krok bieżący, natywny `<progress>` z `ok + failed` / `assessTotal`, w toku, ok, nieudane, martwe, błędy, ponowienia, powroty flex, „ostatnia aktywność: N temu”;
  - liczniki;
  - kroki jako lista z czasem startu, czasem trwania, wejściem / wyjściem, statusem i błędem;
  - błąd przebiegu.
- Odświeżanie co 10 s, dopóki `queued`/`running`; stop przy końcu i odmontowaniu.
- Stany: ładowanie, 404 → „Nie ma takiego przebiegu.”, błąd z „Spróbuj ponownie”.

#### 4. Historia w `/admin`

**File**: `szczur-radar-web/src/views/AdminView.vue`

**Intent**: Lista odpowiada na „kiedy, status, wynik, kto” i sama się odświeża przy trwającym przebiegu.

**Contract**:
- Wiersz dostaje:
  - zakres dat (dla `range`), czas trwania, „cron” / „admin”;
  - znacznik „wymaga uwagi” (tekst + ton `destructive`);
  - przyczynę przerwania w etykiecie statusu;
  - link „Szczegóły” do `admin-run`.
- Pod listą „Pokaż starsze” (wariant drugorzędny) dociąga kolejną stronę i znika, gdy wszystko wczytane.
- Odświeżanie co 10 s, gdy któryś wiersz jest `queued`/`running`; stop przy odmontowaniu.
  - Odświeżenie pobiera stronę 0 o rozmiarze równym liczbie wczytanych wierszy (do 100, limit API; dalsze wiersze zostają), więc starsze strony nie znikają.
  - Wiersze są deduplikowane po `id`, bo nowy przebieg przesuwa offsety stron.
- Istniejące selektory testów zostają (`archive/2026-10-10-admin-panel-ui-hardening/plan.md:33,265`).

#### 5. Atrapa API, kitchen sink i kontrakt stylu

**File**: `szczur-radar-web/dev/mock-api.js`, `src/views/KitchenSinkView.vue`, `src/styleContract.test.ts`

**Intent**: Każdy nowy stan da się pokazać na zrzucie bez backendu.

**Contract**:
- Atrapa:
  - realne przebiegi: `running` z krokiem `assess` w toku i postępem, `interrupted` z przyczyną `deploy` i krokiem `interrupted`, `failed` z błędem „no progress”, `partial` z `dead = 1`, `succeeded` z crona;
  - `rangeFrom`/`rangeTo` jako `Instant`;
  - `GET /api/admin/pipeline-runs/:id` (także 404);
  - `total` > 20 dla „Pokaż starsze”;
  - powiadomienia z przełącznikiem w nagłówku pliku (`NOTIFICATIONS=unread|none|error`);
  - `POST …/read` i `POST /api/admin/notifications/read` → 204.
- Kitchen sink: dzwoneczek zamknięty z licznikiem i bez, otwarty z listą, pusty, błąd, ładowanie; znaczniki „przerwany — nowe wdrożenie”, „wymaga uwagi”.
- `NotificationBell.vue` i `RunDetailView.vue` na liście `CLEANED`.

#### 6. Testy SPA

**File**: `src/components/NotificationBell.test.ts`, `src/views/RunDetailView.test.ts` (nowe), `src/views/AdminView.test.ts`, `src/api/*.test.ts`, `src/router/index.test.ts`, `src/labels.test.ts` / `src/runs.test.ts`

**Intent**: Zachowanie dzwoneczka, strony i listy jest sprawdzone bez przeglądarki.

**Contract**:
- `NotificationBell`:
  - licznik i `aria-label`;
  - otwarcie i zamknięcie: Escape zwraca focus;
  - kliknięcie oznacza i przechodzi;
  - pusty stan i błąd z ponowieniem;
  - „Oznacz wszystkie” zeruje licznik;
  - odpytywanie co 60 s (fake timers).
- `RunDetailView`:
  - ładowanie, 404, błąd;
  - postęp trwającego;
  - odświeżanie zatrzymuje się po końcu;
  - powody uwagi i przyczyna przerwania.
- `AdminView`:
  - zakres, czas trwania, cron/admin;
  - znacznik uwagi i link;
  - „Pokaż starsze”;
  - odświeżanie tylko przy aktywnym przebiegu, a po „Pokaż starsze” zachowuje starsze wiersze bez duplikatów.
- Router: `user` z `/admin/runs/x` → `/`.
- `formatRelative`, `formatDuration`, `runTone`, `attentionReasons`.

### Success Criteria:

#### Automated Verification:

- Testy SPA: `cd szczur-radar-web && npm test`
- Typy i build: `cd szczur-radar-web && npm run build`
- Skan `no literals` obejmuje nowe pliki: `styleContract.test.ts` w `npm test`

#### Manual Verification:

- Lokalnie z `dev/mock-api.js`: dzwoneczek z licznikiem otwiera listę, kliknięcie prowadzi do `/admin/runs/:id`, strona trwającego przebiegu pokazuje postęp, `/admin` pokazuje zakres, czas, cron/admin i „Pokaż starsze”
- Klawiatura: Tab do dzwoneczka, Enter otwiera, Tab po pozycjach, Escape zamyka i zwraca focus

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 6: Stany, dokumentacja i weryfikacja w PR env

### Overview

Macierz 7 stanów na zrzutach, dokumentacja dla następnego agenta i operatora, weryfikacja na prawdziwych danych w Railway PR env.

### Changes Required:

#### 1. Macierz 7 stanów

**File**: `context/changes/pipeline-run-history-dashboard/ui-after/` (zrzuty + `README.md`)

**Intent**: Bramka zrzutów jak w S-04 (`archive/2026-10-12-filter-by-assessment/plan.md:265-271`).

**Contract**:
- Zrzuty 1280×900 i 375×812, w jasnym i ciemnym motywie:
  - `/admin` z historią;
  - `/admin/runs/:id`: trwający z postępem, przerwany przez deploy, zatrzymany przez strażnika, 404, ładowanie, błąd;
  - dzwoneczek: zamknięty z licznikiem, otwarty, pusty, błąd, ładowanie;
  - hover i focus-visible na dzwoneczku, pozycji listy, „Szczegóły” i „Pokaż starsze”.
- `README.md`: 7 komórek (default, hover, focus-visible, disabled, error, empty, loading) → plik albo N/A z powodem (np. hover przy 375 px: ekran dotykowy; disabled dzwoneczka: nigdy nie jest wyłączony); lista `ui-quality-checklist` odhaczona albo N/A; wynik skanu `no literals`.

#### 2. Dokumentacja

**File**:
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`
- `szczur-radar-api/README.md`
- `REPOSITORY_GUIDELINES.md`
- `tools/pipeline-trigger/README.md`
- `context/deployment/deploy-plan.md`
- `.railway/railway.ts`
- `context/foundation/prd.md`, `context/foundation/roadmap.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`

**Intent**: Następny agent i operator wiedzą, jak działa postęp, strażnik i powiadomienia. PRD i roadmapa zapisują decyzje, które odchodzą od ich litery.

**Contract**:
- `pipeline/CLAUDE.md`: liczenie po poście, `last_activity_at`, przyczyna przerwania, `RunWatchdog` i anulowanie przyszłości przebiegu (akcje Embabel w osobnej puli), cztery wywołania `RunNotifications.runEnded`.
- README API:
  - `PIPELINE_STALL_TIMEOUT`;
  - nowe trasy i pakiet `notifications`;
  - `RAILWAY_DEPLOYMENT_ID` (dostarcza Railway).
- `REPOSITORY_GUIDELINES.md`: `NotificationBell` na liście wspólnych komponentów; pakiet `notifications`; strażnik w § Deployment.
- `deploy-plan.md`: sekcja S-06 (zmienne, co widać w logach i w aplikacji, runbook po merge'u: migracje `V8`/`V9`, pierwsza noc z heartbeatem, powiadomienia; rollback kodu po `V8` psuje listę przebiegów w `/admin`, więc tylko fix-forward).
- `.railway/railway.ts`: `PIPELINE_STALL_TIMEOUT: preserve()` na API.
  - Plan IaC: brak zmian albo tylko ta deklaracja.
  - `TRIGGER_HEARTBEAT_INTERVAL` jak inne czasy `TRIGGER_*` (niedeklarowany, wartość domyślna).
- `prd.md`: „Doprecyzowanie (S-06)” przy NFR o sygnale awarii.
  - `partial` z samymi `failed` do ponowienia nie daje powiadomienia, bo następny przebieg je ponawia.
  - `failed`, `interrupted` oraz `partial` z `dead` lub błędem technicznym dają powiadomienie w aplikacji (dzwoneczek admina). Bez pusha i e-maila.
- `roadmap.md` (S-06):
  - Unknown „czy dashboard wystarcza…” rozstrzygnięty: powiadomienia w aplikacji;
  - „ostrzeżenie o braku postępu” zastąpione zatrzymaniem przez strażnika;
  - wpis w dzienniku wersji; `tools/render_roadmap.swift` i `docs/visuals/roadmap-mvp.png` zgodnie z `REPOSITORY_GUIDELINES.md`.

#### 3. Weryfikacja w Railway PR env

**File**: `context/changes/pipeline-run-history-dashboard/ui-after/README.md` (sekcja „PR env”)

**Intent**: Sprawdzić na prawdziwym Wykopie, LLM i Railway to, czego testy nie pokażą.

**Contract**: W PR env (deployment web na głowie gałęzi, `deploy-plan.md` „PR-env verification: the web deployment's commit”):
1. przebieg jednego dnia: `/admin` i `/admin/runs/:id` pokazują rosnący postęp i „ostatnią aktywność”, a log API ma linie postępu;
2. cron z `TRIGGER_IN_ANY_ENVIRONMENT=true`, `TRIGGER_HEARTBEAT_INTERVAL=1m` i tymczasowym harmonogramem: log Railway ma linie heartbeatu z krokiem i postępem, potem override usunięty, harmonogram przywrócony, cron zredeployowany;
3. `railway redeploy` API w trakcie oceny: przebieg „przerwany — nowe wdrożenie”, krok `interrupted`, dzwoneczek z nieprzeczytanym powiadomieniem, kliknięcie → strona przebiegu, powiadomienie przeczytane;
4. konto `user`: brak dzwoneczka, `/admin/runs/:id` → `/`, `GET /api/admin/notifications` → 403.

Strażnik w PR env nie jest wymuszany (wymagałby zawieszenia prawdziwego przebiegu): zatrzymanie pokrywa `RunWatchdogIT`, a brak fałszywego zatrzymania także lokalna weryfikacja fazy 2.

### Success Criteria:

#### Automated Verification:

- Cały backend: `cd szczur-radar-api && ./mvnw -B verify`
- SPA: `cd szczur-radar-web && npm test && npm run build`
- Cron: `tools/pipeline-trigger/test.sh`
- Plan IaC dla PR env bez zmian destrukcyjnych: job `railway-config` zielony
- CI na PR zielone (cały workflow)

#### Manual Verification:

- `ui-after/README.md` pokazuje 7 komórek macierzy w obu motywach i przy 375 px, z listą `ui-quality-checklist`
- PR env: postęp przebiegu na żywo w `/admin` i na stronie przebiegu, linie postępu w logu API
- PR env: heartbeat crona w logu Railway
- PR env: redeploy w trakcie oceny daje „przerwany — nowe wdrożenie” i powiadomienie w dzwoneczku
- PR env: konto `user` bez dzwoneczka i bez dostępu do stron i tras przebiegów

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- `ConcurrentAssessmentDispatcherTest`: liczenie po poście, ponowienia z `attempts = 0`, błąd techniczny, linia postępu.
- `PipelineStallSettingsTest`: domyślny próg z granicy ustawień LLM, WARN poniżej niej, jednostki, odrzucenie gołej liczby i zakresu.
- `PipelineRun.needsAttention`: każdy status, `partial` z `dead` i z błędem.
- SPA: dzwoneczek, strona przebiegu, lista, etykiety czasu, ton i powody uwagi.

### Integration Tests:

- `PipelineAgentIT`: postęp w trakcie, końcowe liczniki bez zmian, zamknięcie instancji (`shutdown`).
- `PipelineStartupRecoveryIT`: `deploy` / `restart`, kroki `interrupted`, powiadomienia.
- `RunWatchdogIT`: zatrzymanie, kolejny przebieg rusza, akcja-zombie bez zapisów, brak fałszywego zatrzymania, przebieg cudzej instancji nietknięty.
- `NotificationsIT`, `PipelineRunControllerIT`, `InternalPipelineRunIT`, `AuthorizationCompletenessIT`, `AdminRoleIT`, `MigrationsIT`, `JpaSchemaIT`.
- `tools/pipeline-trigger/test.sh`: heartbeat, przyczyna przerwania, liczba żądań.

### Manual Testing Steps:

1. Lokalny przebieg jednego dnia: linie INFO w logu, rosnące liczniki w bazie i na stronie przebiegu.
2. Lokalny strażnik: przebieg z `PIPELINE_STALL_TIMEOUT=1m` kończy się normalnie; `PIPELINE_STALL_TIMEOUT=15` zatrzymuje start serwera. Samo zatrzymanie zawieszonego przebiegu pokrywa `RunWatchdogIT` (zawieszenia nie da się wywołać niezawodnie ręcznie).
3. Atrapa API: dzwoneczek, strona przebiegu, „Pokaż starsze”, klawiatura.
4. PR env: przebieg na żywo, heartbeat crona, redeploy w trakcie, konto `user`.

## Performance Considerations

- **Zapis po poście** to jeden warunkowy `UPDATE` wiersza przebiegu na post: ok. 400–650 zapisów na noc (dane z 2026-10-14/15). Pomijalne wobec wywołań LLM po ok. 15 s.
- **`inFlight`** liczy się tylko dla przebiegu `running` (najwyżej jednego) i korzysta z częściowego indeksu `ix_posts_assessing_run`.
- **Odpytywanie dotyczy tylko admina:**
  - `/admin` i strona przebiegu co 10 s, tylko gdy przebieg trwa;
  - dzwoneczek co 60 s, gdy karta jest widoczna.

  Każde żądanie przesuwa sesję, ale API i tak nie śpi w trakcie przebiegu.
- **Usypianie API.** Railway usypia usługę 5–10 min po ostatnim pakiecie wychodzącym, licząc też zapytania do bazy (docs: Serverless). Strażnik bez przebiegów w `own` nie pyta bazy. Widoczna karta admina (dzwoneczek co 60 s) nie pozwala API zasnąć; karta w tle nie odpytuje.

## Migration Notes

- `V8` i `V9` tylko dodają: kolumny z domyślnymi wartościami lub `NULL`, szerszy `CHECK` kroku, nowy indeks i tabelę. Kod sprzed S-06 zapisuje na nowym schemacie, ale nie odczyta kroku `interrupted`: `StepStatus` go nie zna, a `EnumValueConverter` rzuca wyjątek (`EnumValueConverter.java:40-43`).
  - W oknie deployu stara instancja odpowiada na `GET /api/admin/pipeline-runs` błędem 500, bo poprawka danych `V8` zamyka stare kroki jako `interrupted`. Akceptujemy to: okno trwa ok. 1–2 min, a dotyczy tylko listy w `/admin`.
  - Rollback kodu z panelu Railway po `V8` psuje listę przebiegów w `/admin` na stałe. Naprawa to fix-forward, nie rollback.
- `V8` zamyka jako `interrupted` historyczne kroki `running` przebiegów, które już się skończyły. Stare przebiegi `interrupted` nie dostają przyczyny (`NULL`). SPA pokazuje je jako „przerwany” bez dopisku.
- Powiadomienia nie powstają wstecz dla przebiegów sprzed `V9`.
- Wycofanie przez fix-forward: nowa migracja, nigdy edycja `V8`/`V9` po deployu.

## References

- Research: `context/changes/pipeline-run-history-dashboard/research.md`
- Roadmapa: `context/foundation/roadmap.md` (S-06, dopiski F3 i monitorowania; `## Parked`: przybliżony czas zakończenia)
- PRD: `context/foundation/prd.md` FR-015, FR-021, NFR o sygnale awarii (`:258-259`)
- Wzorce:
  - ustawienie tylko serwera: `szczur-radar-api/src/main/java/pl/szczurradar/api/internal/PipelineTriggerToken.java`; parsowanie czasu z jednostką: `auth/activation/ActivationSettings.java`;
  - warunkowe zapisy: `pipeline/state/RunRecorder.java`;
  - odzyskiwanie tylko w serwerze: `pipeline/PipelineStartupRecovery.java`.
- Bramka UI: `context/archive/2026-10-12-filter-by-assessment/plan.md` (faza 3), `.claude/skills/10x-ui/references/ui-quality-checklist.md`, `REPOSITORY_GUIDELINES.md` § UI
- Ograniczenia `/admin`: `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md`
- Produkcja: `context/deployment/deploy-plan.md` (S-05 „Production after the merge”, po #59)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Postęp, przyczyna przerwania i logi przebiegu

#### Automated

- [x] 1.1 Migracja `V8` stosuje się na pustej bazie i odrzuca złą przyczynę i przyczynę przy statusie innym niż `interrupted`: `MigrationsIT` — 18323b5
- [x] 1.2 Encje zgodne ze schematem: `JpaSchemaIT` — 18323b5
- [x] 1.3 Liczniki po poście z ponowieniami i powrotami flex, bez podwójnego liczenia: `ConcurrentAssessmentDispatcherTest`, `PipelineAgentIT` — 18323b5
- [x] 1.4 Przyczyna przerwania `deploy` / `restart` / `shutdown` i kroki `interrupted`: `PipelineStartupRecoveryIT`, `PipelineAgentIT` — 18323b5
- [x] 1.5 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — 18323b5

#### Manual

- [x] 1.6 Lokalny przebieg jednego dnia (Postgres z `compose.local.yaml`, jar, prawdziwe `LLM_*` i `WYKOP_*`) loguje start, kroki, postęp oceny co najwyżej co minutę i koniec, a `pipeline_runs` w trakcie oceny ma rosnące `assessed_ok` i świeże `last_activity_at` — 18323b5

### Phase 2: Strażnik zawieszonego przebiegu

#### Automated

- [x] 2.1 Walidacja `PIPELINE_STALL_TIMEOUT`: `PipelineStallSettingsTest` — 9063c36
- [x] 2.2 Zatrzymanie zawieszonego przebiegu, zamknięte kroki, oddane posty, kolejny przebieg rusza, brak fałszywego zatrzymania: `RunWatchdogIT` — 9063c36
- [x] 2.3 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — 9063c36

#### Manual

- [x] 2.4 Lokalny przebieg jednego dnia z `PIPELINE_STALL_TIMEOUT=1m` kończy się normalnie (strażnik nie zatrzymuje przebiegu z postępem), a serwer z `PIPELINE_STALL_TIMEOUT=15` nie startuje i komunikat nazywa zmienną — 9063c36

### Phase 3: Powiadomienia i API admina

#### Automated

- [x] 3.1 Migracja `V9` i jej ograniczenia: `MigrationsIT`, `JpaSchemaIT` — 3661f99
- [x] 3.2 Reguła „wymaga uwagi” dla wszystkich statusów: test jednostkowy `PipelineRun.needsAttention` — 3661f99
- [x] 3.3 Powiadomienia: odbiorcy, idempotencja, lista, przeczytanie, 404 cudzego: `NotificationsIT` — 3661f99
- [x] 3.4 Powiadomienie z każdej drogi końca przebiegu: `PipelineStartupRecoveryIT`, `PipelineAgentIT`, `RunWatchdogIT` — 3661f99
- [x] 3.5 Strona przebiegu, nowe pola, `RunState` dla crona: `PipelineRunControllerIT`, `InternalPipelineRunIT` — 3661f99
- [x] 3.6 Nowe trasy chronione (401 bez sesji, 403 dla `user`): `AuthorizationCompletenessIT`, `AdminRoleIT` — 3661f99
- [x] 3.7 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — 3661f99

### Phase 4: Heartbeat crona

#### Automated

- [x] 4.1 Test crona z heartbeatem, przyczyną przerwania i niezmienioną liczbą żądań: `tools/pipeline-trigger/test.sh` — 4dfabf1

### Phase 5: SPA — dzwoneczek, strona przebiegu i historia

#### Automated

- [x] 5.1 Testy SPA: `cd szczur-radar-web && npm test` — 5be417e
- [x] 5.2 Typy i build: `cd szczur-radar-web && npm run build` — 5be417e
- [x] 5.3 Skan `no literals` obejmuje nowe pliki: `styleContract.test.ts` w `npm test` — 5be417e

#### Manual

- [x] 5.4 Lokalnie z `dev/mock-api.js`: dzwoneczek z licznikiem otwiera listę, kliknięcie prowadzi do `/admin/runs/:id`, strona trwającego przebiegu pokazuje postęp, `/admin` pokazuje zakres, czas, cron/admin i „Pokaż starsze” — 5be417e
- [x] 5.5 Klawiatura: Tab do dzwoneczka, Enter otwiera, Tab po pozycjach, Escape zamyka i zwraca focus — 5be417e

### Phase 6: Stany, dokumentacja i weryfikacja w PR env

#### Automated

- [x] 6.1 Cały backend: `cd szczur-radar-api && ./mvnw -B verify`
- [x] 6.2 SPA: `cd szczur-radar-web && npm test && npm run build`
- [x] 6.3 Cron: `tools/pipeline-trigger/test.sh`
- [ ] 6.4 Plan IaC dla PR env bez zmian destrukcyjnych: job `railway-config` zielony
- [ ] 6.5 CI na PR zielone (cały workflow)

#### Manual

- [ ] 6.6 `ui-after/README.md` pokazuje 7 komórek macierzy w obu motywach i przy 375 px, z listą `ui-quality-checklist`
- [ ] 6.7 PR env: postęp przebiegu na żywo w `/admin` i na stronie przebiegu, linie postępu w logu API
- [ ] 6.8 PR env: heartbeat crona w logu Railway
- [ ] 6.9 PR env: redeploy w trakcie oceny daje „przerwany — nowe wdrożenie” i powiadomienie w dzwoneczku
- [ ] 6.10 PR env: konto `user` bez dzwoneczka i bez dostępu do stron i tras przebiegów
