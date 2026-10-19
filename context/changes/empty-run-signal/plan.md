# Pusty przebieg daje sygnał (S-08) — plan implementacji

## Overview

Przebieg za zakres dat (RANGE), w którym źródło nie dało ani jednego posta do zapisu, przestaje kończyć się `succeeded`. Jego krok pobierania kończy się błędem, a cały przebieg statusem `failed`. Daje to istniejącą ścieżką powiadomienie w dzwonku, kod wyjścia crona 1 (a więc push Railway „deployment crashed”) i stojącą etykietę „Dane zaktualizowane”. Elementy pominięte przy dobrych postach (`skipped > 0`) dają ostrzeżenie: powiadomienie w dzwonku bez zmiany statusu, kodu wyjścia crona ani świeżości. Licznik `skipped` przestaje żyć tylko w panelu admina: trafia do `/internal`, do linii crona i do logów API.

Źródło: audyt C1 (jedyne ustalenie o wadze H) i B5 (`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:76,90`), roadmapa S-08 (`context/foundation/roadmap.md:147-158`), research `context/changes/empty-run-signal/research.md`.

## Current State Analysis

- **Status liczy tylko ocenę.** Status powstaje wyłącznie z wyniku oceny postów: `RunFlow.AssessedBatch.status()` (`api/pipeline/flow/RunFlow.java:150-155`). Pusta lista postów do oceny daje od razu `AssessedBatch(0,0,0,[],null)` (`api/pipeline/assess/ConcurrentAssessmentDispatcher.java:122`), czyli `SUCCEEDED`. Liczniki `fetched`/`skipped` zapisuje osobno `PipelineAgent.fetch` (`api/pipeline/flow/PipelineAgent.java:79-81`) i nic ich nie czyta przy sygnalizacji.
- **Pięć punktów sygnalizacji przepuszcza pusty przebieg:**
  - `needsAttention` daje `false` dla `SUCCEEDED` (`api/pipeline/run/PipelineRun.java:283-288`), więc `RunNotifications.runEnded` niczego nie wysyła (`api/notifications/RunNotifications.java:44`);
  - `RunState` w `/internal` nie ma `skipped` (`api/pipeline/api/InternalPipelineRunController.java:103-106`);
  - cron mapuje `succeeded | partial` na ok, czyli exit 0 (`tools/pipeline-trigger/trigger.sh:144,282`);
  - świeżość liczy każdy RANGE `succeeded`/`partial` (`api/pipeline/run/PipelineRunRepository.java:39-48`);
  - linie końcowe API (`api/pipeline/PipelineRunService.java:306-310`) i crona (`trigger.sh:259`) nie wypisują `skipped`.
- **Znaczenie liczników (Wykop):**
  - `fetched` = elementy po deduplikacji + elementy o złym kształcie JSON (`api/sources/wykop/WykopFetcher.java:55`);
  - `skipped` = zły kształt + elementy niemapowalne (`WykopFetcher.java:46-54`).

  Każdy element jest albo postem, albo pominiętym. Liczba postów źródła to więc `fetched − skipped`, a „źródło nie dało postów” = `result.posts().isEmpty()`.
- **Krok, który rzuca wyjątek, daje już pełny sygnał.** `RunRecorder.step` zamyka krok jako `failed` z komunikatem i rzuca dalej (`api/pipeline/state/RunRecorder.java:146-162`). `PipelineRunService.execute` zapisuje `error = "<Klasa>: <komunikat>"` i kończy przebieg `failed` (`PipelineRunService.java:231-244`). `needsAttention` daje wtedy `true`, cron exit 1, a świeżość tego przebiegu nie liczy. Wzorzec testu: `PipelineAgentIT.aFailingSourceFailsTheRunAndTheQueueTakesTheNextRequest` (`test/pipeline/PipelineAgentIT.java:306-329`).
- **Dzwonek pokazuje tylko ton i etykietę statusu** (`web/components/NotificationMenu.vue:162-171`). `NotifiedRun` nie ma `skipped` (`api/notifications/api/NotificationController.java:122-131`, `web/api/notifications.ts:9-19`). Powody „wymaga uwagi” buduje tylko SPA (`web/runs.ts:57-72`).
- **Wolumeny:** zaobserwowane przebiegi RANGE (okno 2 dni) pobrały od 74 do 864 elementów. Nikt nie widział ani `fetched = 0`, ani `skipped > 0` (research §7).

Konwencja ścieżek: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`, `web/…` = `szczur-radar-web/src/…`.

## Desired End State

- **Przebieg RANGE ze źródłem bez postów** (0 elementów albo same pominięte) kończy się `failed`:
  - krok `fetch` ma status `failed` z komunikatem nazywającym źródło i liczby;
  - `pipeline_runs` ma zapisane prawdziwe `fetched` i `skipped`;
  - `error` = `NoPostsFetchedException: …`;
  - kroków `store`/`select`/`assess` nie ma.

  Każdy admin dostaje powiadomienie. Cron loguje przebieg z licznikami i kończy się kodem 1. `GET /api/freshness` zwraca datę poprzedniego udanego przebiegu.
- **Przebieg z `skipped > 0` i postami do zapisu** kończy się jak dotąd (`succeeded` albo `partial`), ale `needsAttention = true`:
  - każdy admin dostaje powiadomienie;
  - dzwonek i lista pokazują ton ostrzeżenia z etykietą `… — pominięte: N`;
  - strona przebiegu podaje powód „pominięte elementy: N”;
  - API loguje WARN z liczbą pominiętych dla źródła;
  - cron kończy się 0, a świeżość się przesuwa.
- `GET /internal/pipeline-runs/{id}` zwraca `skipped`. Linia crona per przebieg i końcowa linia API wypisują `skipped`.
- Przebiegi RETRY i POSTS oraz przebiegi bez pominiętych zachowują się dokładnie jak dziś.
- PRD, `deploy-plan.md`, README crona, `pipeline/CLAUDE.md` i `REPOSITORY_GUIDELINES.md` opisują nowe zachowanie.

### Key Discoveries:

- Liczniki są zapisywane po udanym kroku (`PipelineAgent.java:79-81`), więc wyjątek kroku pobierania musi nieść liczby, a agent musi je zapisać przed ponownym rzuceniem (Critical Implementation Details).
- `SourceIngest.fetch` już zapisuje zasadę „a failing source fails the step” (`api/pipeline/step/SourceIngest.java:65`). Sprawdzenie per źródło w tym samym miejscu jest ogólne dla każdego źródła (NFR „nowe źródło nie zmienia silnika”).
- `RunOutcomeTest.java:35` („0 postów do oceny = `SUCCEEDED`”) zostaje bez zmian. Dotyczy oceny, więc RETRY bez postów nadal kończy się `succeeded`.
- `containsOnlyKeys` przypina `RunState` (`test/pipeline/api/InternalPipelineRunIT.java:221-223`) i `NotifiedRun` (`test/notifications/api/NotificationsIT.java:145-146`).
- Javadoc `PipelineRun.needsAttention` (`PipelineRun.java:272-276`) twierdzi, że reguła obejmuje crona. Cron patrzy jednak tylko na status (`trigger.sh:142-151`). Decyzja: kod crona bez zmian, javadoc do poprawki.
- Atrapa API crona przypina dosłowną linię liczników w `tools/pipeline-trigger/test.sh:219, 235, 261`. Domyślna treść atrapy jest na `:115-123`.

## What We're NOT Doing

- **Nowy status przebiegu** (np. `empty`). Pusty przebieg to `failed`, a migracja `CHECK` i nowe gałęzie w cronie i SPA nie są potrzebne.
- **Zmiana kodów wyjścia crona.** Cron nadal kończy się 1 tylko przy `failed`/`interrupted`. Ostrzeżenia (martwy post, pominięte) idą tylko przez dzwonek. Wyrównanie crona z `needsAttention` zostaje w S-12 (A5).
- **Pole `needsAttention` w `/internal`.**
- **Próg „za mało postów”** (np. < 20 na 2 dni) i błąd przy udziale pominiętych powyżej progu. Brak danych do progu.
- **Rozbicie `skipped` na przyczyny, log każdego pominiętego elementu i zachowanie jego RAW** (zmiana portu `SourceFetcher.Fetched`).
- **Wykrycie niekompletnego stronicowania Wykopu i ponowienia 429/5xx.** To S-16 (B5, B6): zgubione elementy nigdy nie trafiają do `fetched`.
- **Izolacja źródeł** (puste źródło A nie blokuje zapisu postów źródła B) i świeżość per źródło. To Parked (`roadmap.md:342`). Dziś jedno źródło, a zasada „błąd źródła = błąd kroku” już obowiązuje.
- **Zmiana zapytania świeżości.** Pusty przebieg jest `failed`, więc wypada sam. Przebieg z pominiętymi przesuwa etykietę zgodnie z decyzją.
- **Kanał alarmowy poza aplikacją** (F-04), wykrycie brakującej nocy (S-13), dostarczalność powiadomień i spójność kroków (S-12).

## Implementation Approach

Dwie reguły w backendzie, a reszta to propagacja kontraktu:
1. **Błąd:** `SourceIngest.fetch` po pobraniu wszystkich źródeł rzuca `NoPostsFetchedException`, gdy któreś źródło nie dało posta. Wyjątek niesie źródła i sumy liczników. Istniejąca ścieżka błędu kroku daje `failed` i wszystkie sygnały, bez zmian w cronie, świeżości i SPA dla błędów.
2. **Ostrzeżenie:** `needsAttention` dostaje `skipped`. `succeeded` lub `partial` z `skipped > 0` wymaga uwagi, więc istniejący `RunNotifications` wyśle powiadomienie.

Potem `skipped` trafia do `RunState`, `NotifiedRun` i logów (faza 1), do linii crona (faza 2), do powodu, tonu i etykiety w SPA (faza 3). Na koniec dokumentacja i weryfikacja w PR env (faza 4).

## Critical Implementation Details

### State sequencing

`RunRecorder.step` przy wyjątku zamyka krok `fetch` jako `failed` i rzuca dalej. Liczniki muszą trafić do `pipeline_runs` po tym, ale przed wyjściem z akcji, czyli zanim `PipelineRunService.execute` zamknie przebieg jako `failed`. `RunRecorder.count` działa tylko na przebiegu `running`. `PipelineAgent.fetch` łapie więc `NoPostsFetchedException` wokół wywołania `recorder.step(...)`, zapisuje `Counters.fetched(ex.fetched(), ex.skipped())` i rzuca ten sam wyjątek dalej. Bez tego strona przebiegu z samymi pominiętymi pokaże „pobrane 0, pominięte 0”.

## Phase 1: Backend — pusty przebieg i ostrzeżenie o pominiętych

### Overview

Pusty przebieg RANGE kończy się `failed` przy pobieraniu, a pominięte ustawiają `needsAttention`. `skipped` trafia do `/internal`, `NotifiedRun` i logów. Testy przypinają nowe zachowanie end-to-end.

### Changes Required:

#### 1. Wyjątek „źródło bez postów”

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/NoPostsFetchedException.java` (nowy)

**Intent**: Nazwany błąd kroku pobierania, gdy źródło w zakresie nie dało żadnego posta do zapisu. Jego komunikat staje się tekstem błędu przebiegu na stronie `/admin/runs/:id`.

**Contract**: `RuntimeException` w pakiecie `pipeline` (obok `SourceNotConfiguredException`) z polami `List<Source> sources` (źródła bez postów), `int fetched`, `int skipped` (sumy wszystkich źródeł przebiegu, do zapisania w licznikach). Komunikat po angielsku jak inne błędy przebiegu, z nazwą źródła i jego liczbami, np. `wykop gave no post to store (fetched 0, skipped 0)`; kilka źródeł łączy `; `.

#### 2. Sprawdzenie per źródło i log pominiętych

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/SourceIngest.java`

**Intent**: `fetch` pobiera wszystkie źródła jak dziś. Dla źródła z `result.skipped() > 0` loguje WARN z identyfikatorem przebiegu, źródłem i liczbami. Po pętli rzuca `NoPostsFetchedException`, gdy co najmniej jedno źródło dało pustą listę postów. Javadoc metody opisuje nową zasadę.

**Contract**: `FetchedBatch fetch(RangeTarget)` rzuca `NoPostsFetchedException(empty, fetched, skipped)` zamiast zwracać partię, gdy `result.posts().isEmpty()` dla któregokolwiek źródła. Linia WARN: `Run <id>: <source> skipped <skipped> of <fetched> elements (unexpected shape or not mappable)`, tylko przy `skipped > 0`. `SourceNotConfiguredException` bez zmian.

To pierwszy logger w `pipeline/step/` (dziś loggery mają `PipelineRunService`, `RunRecorder`, dispatcher i watchdog). Zostaje tutaj, bo tylko ta pętla zna liczby per źródło; suma po pętli je gubi. Pusty fetch nie dostaje osobnej linii, bo jego błąd logują już Embabel (WARN) i `PipelineRunService.execute` (ERROR ze stosem).

#### 3. Zapis liczników przed błędem

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/PipelineAgent.java`

**Intent**: Strona przebiegu ma pokazać prawdziwe pobrane i pominięte także w przebiegu, który padł na pustym źródle (zob. State sequencing).

**Contract**: `fetch(RangeTarget)` łapie `NoPostsFetchedException` wokół `recorder.step(...)`, wywołuje `recorder.count(runId, Counters.fetched(ex.fetched(), ex.skipped()))` i rzuca ten sam wyjątek. Akcja pozostaje `FIRE_ONCE`.

#### 4. Reguła „wymaga uwagi”

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java`

**Intent**: Przebieg z pominiętymi elementami wymaga uwagi niezależnie od wyniku oceny. Javadoc mówi prawdę o cronie.

**Contract**: `static boolean needsAttention(RunStatus status, int dead, int processingErrors, int skipped)`:
- `FAILED`, `INTERRUPTED` → `true`;
- `PARTIAL` → `dead > 0 || processingErrors > 0 || skipped > 0`;
- `SUCCEEDED` → `skipped > 0`;
- `QUEUED`, `RUNNING` → `false`.

Javadoc: jedna reguła dla powiadomień, listy, strony przebiegu i dzwonka; cron patrzy tylko na status (`failed`/`interrupted` → exit 1), więc martwy post i pominięte go nie wywracają.

#### 5. `skipped` w `/internal` i w powiadomieniach

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunController.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/api/NotificationController.java`

**Intent**: Cron i dzwonek dostają licznik pominiętych.

**Contract**: `RunState` dostaje `int skipped` (po `dead`); `NotifiedRun` dostaje `int skipped` (po `processingErrors`), oba wypełniane z `PipelineRun.getSkipped()`. Javadoc `RunState` wymienia nowe pole.

#### 6. Końcowa linia logu API

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java`

**Intent**: Log Railway pokazuje pominięte bez otwierania panelu.

**Contract**: linia `Pipeline run {id} ({kind}) ended {status} in {t}: fetched N, skipped N, new N, ok N, …`. `skipped` stoi zaraz po `fetched`, reszta bez zmian.

#### 7. Testy backendu

**Files**: `test/pipeline/step/SourceIngestTest.java`, `test/pipeline/run/PipelineRunTest.java`, `test/pipeline/PipelineAgentIT.java`, `test/pipeline/api/InternalPipelineRunIT.java`, `test/notifications/api/NotificationsIT.java`

**Intent**: Przypiąć obie reguły i kontrakt.

**Contract**:
- `SourceIngestTest`: helper `fetcher(...)` (`:68-89`) przyjmuje `Fetched` per test zamiast stałego `Fetched(1, 3, 2)`. Test sprawdza `NoPostsFetchedException` z nazwanym źródłem i liczbami dla źródła z 0 elementów i dla źródła z samymi pominiętymi; źródło z pominiętymi i co najmniej jednym postem zwraca partię. Przypadku dwóch źródeł nie da się dziś napisać (`Source` ma tylko `WYKOP`, a `SourceIngest` trzyma fetchery w mapie po `Source`, `SourceIngest.java:29`); wymóg tego testu trafia do `sources/CLAUDE.md` (zmiana 9).
- `PipelineRunTest`: `needsAttention` z `skipped` dla każdego statusu (`SUCCEEDED`/`PARTIAL` z `skipped > 0` → `true`, `QUEUED`/`RUNNING` → `false`).
- `PipelineAgentIT`, trzy nowe przypadki na `LocalWykopServer`, sterowane statycznymi flagami jak `wykopDown` (`:97-109`, np. `wykopEmpty`, `wykopUnmappable`, reset w `finally`); przy leftoverach przebieg zakresu to `runs.getLast()`:
  - (a) pusty zakres: przebieg `failed`, krok `fetch` `failed` z komunikatem, brak kroku `store`, `fetched = 0`, `skipped = 0`, `error` zaczyna się od `NoPostsFetchedException`, adminowie powiadomieni raz, następny przebieg w kolejce rusza. Do tego: Wykop dostał dokładnie jedną rundę wyszukiwań (wpisy + linki), czyli akcja `FIRE_ONCE` nie ponowiła pobrania (`context/foundation/lessons.md`, „Akcje Embabel…”), a `PipelineRunRepository.findLastUpdate()` jest taki sam jak przed przebiegiem;
  - (b) same niemapowalne elementy (np. wpisy bez tekstu): `failed` z `fetched = skipped = N > 0` zapisanymi w `pipeline_runs`;
  - (c) część pominiętych: status jak dotąd, `needsAttention = true`, adminowie powiadomieni.
- `InternalPipelineRunIT`: pusty dzień kończy się `"status":"failed"`, `assessTotal` = `null`; zbiór kluczy z `skipped`; javadoc klasy bez „answering an empty day” jako sukcesu.
- `NotificationsIT`: zbiór kluczy `NotifiedRun` z `skipped`.
- `RunOutcomeTest` bez zmian.

#### 8. Dokumentacja pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: Opis pakietu zgadza się z kodem.

**Contract**:
- wiersz `InternalPipelineRunController` (`:37`): `skipped` w liczbach;
- opis logów (`:74-76`): `skipped` w linii końcowej i nowa linia WARN;
- opis `runEnded`/`needsAttention` (`:138`): pominięte;
- nowa zasada: krok `fetch` przebiegu RANGE pada, gdy źródło nie dało posta.

#### 9. Wymóg dla kolejnego źródła

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md`

**Intent**: Reguła działa per źródło, ale przypadku „jedno źródło puste, drugie z postami” nie da się przetestować przed dodaniem drugiego źródła. Wymóg ma trafić tam, gdzie zagląda każdy, kto dodaje źródło.

**Contract**: w opisie dodawania źródła punkt: drugie źródło dopisuje w `SourceIngestTest` przypadek dwóch źródeł z jednym pustym (wyjątek nazywa tylko puste źródło, liczby są sumą obu). Dopisek: puste źródło wywraca dziś cały krok `fetch`, także posty innych źródeł; izolacja źródeł jest Parked (`context/foundation/roadmap.md:342`).

### Success Criteria:

#### Automated Verification:

- Pusty zakres, same niemapowalne i część pominiętych dają oczekiwany status, liczniki, krok i powiadomienie: `PipelineAgentIT`, `SourceIngestTest`
- `needsAttention` z `skipped` dla każdego statusu: `PipelineRunTest`
- Kontrakt `/internal` (pusty dzień `failed`, `skipped` w kluczach) i `NotifiedRun` (`skipped` w kluczach): `InternalPipelineRunIT`, `NotificationsIT`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`

#### Manual Verification:

- Lokalnie (Postgres z `compose.local.yaml`, jar, prawdziwe `WYKOP_*` i `LLM_*`) przebieg admina dla zwykłego dnia kończy się jak dotąd. Linia końcowa API zawiera `skipped 0`, a przy `skipped = 0` nie ma linii WARN

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przez człowieka przed fazą 2.

---

## Phase 2: Cron — `skipped` w kontrakcie `trigger.sh`

### Overview

Cron wypisuje `skipped` w linii każdego przebiegu. Testy atrapy przypinają oba nowe scenariusze: pusty przebieg kończy crona kodem 1, a pominięte kodem 0.

### Changes Required:

#### 1. Linia per przebieg

**File**: `tools/pipeline-trigger/trigger.sh`

**Intent**: Log crona (i powiadomienie Railway o crashu) mówi, ile pobrano i pominięto. Kody wyjścia się nie zmieniają: pusty przebieg jest `failed`, więc wyjdzie istniejącą ścieżką.

**Contract**: linia `:259` przyjmuje kształt `<kind> <id>: <status> (fetched N, skipped N, new posts N, assessed ok N, assessed failed N, processing errors N, dead N)<cause>`. `skipped` czyta z fallbackiem `// "?"` jak `progress()`, żeby starsze API dawało `skipped ?` zamiast `null`. Komentarz przy `classify()` mówi, że pusty przebieg API kończy jako `failed`.

#### 2. Atrapa API i przypadki testowe

**File**: `tools/pipeline-trigger/test.sh`

**Intent**: Atrapa odwzorowuje nowe pole, a dwa nowe przypadki przypinają zachowanie przy pustym przebiegu i przy pominiętych.

**Contract**:
- domyślna treść GET (`:115-123`) dostaje `skipped: 0`, a lista `old_api` (`:125-127`) usuwa `skipped`;
- przypięte linie `:219`, `:235` (z `skipped 0`) i `:261` (z `skipped ?`) w nowym kształcie;
- nowy `check_case empty`: przebieg kończy `failed` z `fetched 0, skipped 0` → exit 1, linia z licznikami i `FAILED: a run did not succeed: range failed`;
- nowy `check_case skipped-warning`: `succeeded` z `skipped 3` → exit 0, linia z `skipped 3` i `OK: range succeeded`.

#### 3. README narzędzia

**File**: `tools/pipeline-trigger/README.md`

**Intent**: Opis kontraktu zgadza się z kodem.

**Contract**:
- przepływ (`:19-32`): pusty zakres to `failed` w API;
- tabela wyjść (`:36-42`): kod 1 obejmuje pusty przebieg;
- przykładowe logi (`:48-67`) z `skipped`;
- lista testów (`:98-112`) z `empty` i `skipped-warning`.

### Success Criteria:

#### Automated Verification:

- Wszystkie przypadki atrapy, w tym `empty` (exit 1) i `skipped-warning` (exit 0): `tools/pipeline-trigger/test.sh`

#### Manual Verification:

- README narzędzia i przykładowe logi zgadzają się z wyjściem `test.sh` (porównanie wzrokowe linii liczników)

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przez człowieka przed fazą 3.

---

## Phase 3: SPA — powód, ton i etykieta ostrzeżenia

### Overview

Przebieg `succeeded` lub `partial` z pominiętymi wygląda jak ostrzeżenie w liście `/admin`, na stronie przebiegu i w dzwonku. Strona przebiegu podaje powód. Pusty przebieg (`failed`) pokazuje się istniejącą ścieżką: czerwona plakietka „błąd” i tekst błędu jako powód.

### Changes Required:

#### 1. Typ powiadomienia

**File**: `szczur-radar-web/src/api/notifications.ts`

**Intent**: Dzwonek zna pominięte.

**Contract**: `NotifiedRun` dostaje `skipped: number`, jak backend z fazy 1.

#### 2. Wspólna logika przebiegu

**File**: `szczur-radar-web/src/runs.ts`

**Intent**: Jedno miejsce decyduje o tonie, etykiecie i powodach dla listy, strony przebiegu i dzwonka (zasada pliku, `runs.ts:1-2`).

**Contract**:
- typ `RunState` (`:8`) dostaje `skipped`;
- `runTone`: `succeeded` z `skipped > 0` → `warning`, reszta bez zmian;
- `runStatusLabel`: `succeeded`/`partial` z `skipped > 0` dopisuje ` — pominięte: N`, np. `zakończony — pominięte: 3`;
- `AttentionFields` (`:11`) dodaje `skipped`;
- `attentionReasons`: dla `succeeded` i `partial` z `skipped > 0` dodaje `pominięte elementy: N`, po martwych postach i błędach technicznych.

#### 3. Kitchen sink i atrapa API

**Files**: `szczur-radar-web/src/views/KitchenSinkView.vue`, `szczur-radar-web/dev/mock-api.js`

**Intent**: Każdy wariant przebiegu i powiadomienia jest widoczny do sprawdzenia wzrokowego (zasada UI w `REPOSITORY_GUIDELINES.md`).

**Contract**:
- kitchen sink: sekcja plakietek (`:59-68`) dostaje „zakończony — pominięte: N” w tonie ostrzeżenia; powiadomienia (`:73-117`) dostają wariant `succeeded` z pominiętymi i pusty przebieg `failed` z tekstem `NoPostsFetchedException: wykop gave no post to store (fetched 0, skipped 0)`; obiekty `run` dostają `skipped`;
- mock: `allRuns()` (`:89-119`) dostaje przebieg `r-skipped` (`succeeded`, `skipped` > 0, `needsAttention: true`) i `r-empty` (`failed`, `fetched 0`, krok `fetch` `failed`, ten sam tekst błędu, `needsAttention: true`), oba z powiadomieniami; `notifiedRun()` (`:124-126`) dodaje `skipped`; nagłówek pliku wymienia nowe przebiegi.

#### 4. Testy SPA

**Files**: `szczur-radar-web/src/runs.test.ts`, `szczur-radar-web/src/views/AdminView.test.ts`, `szczur-radar-web/src/views/RunDetailView.test.ts`, `szczur-radar-web/src/components/NotificationBell.test.ts`

**Intent**: Przypiąć ton, etykietę i powód oraz usunąć niemożliwe dziś fixture'y.

**Contract**:
- `runs.test.ts`: ton, etykieta i powody dla `succeeded`/`partial` z `skipped > 0` i z `skipped = 0`; dosłowne obiekty `{status, interruptionCause}` (`:72, 76-78, 95-96, 102-103`) dostają `skipped`, bo bez niego nie przejdą typechecku;
- `AdminView.test.ts`: domyślny `run()` (`:29-61`) ma `skipped: 0` (dziś `2` przy `needsAttention: false`; bez zmiany psują się testy plakietek `:291`, `:292`, `:492`), a osobny przypadek pominiętych pokazuje ton ostrzeżenia, etykietę z liczbą i „wymaga uwagi”;
- `RunDetailView.test.ts`: tekst powodu z pominiętymi;
- `NotificationBell.test.ts`: `run` w `notification()` (`:33-43`) z `skipped`; to samo w domyślnych `notification()` kitchen sinka (`KitchenSinkView.vue:81-91`, zmiana 3);
- `dev/mock-api.js` leży poza `src` i nie przechodzi typechecku: `skipped` w `notifiedRun()` sprawdzić ręcznie przy zrzutach.

### Success Criteria:

#### Automated Verification:

- Ton, etykieta i powody pominiętych oraz spójne fixture'y: `cd szczur-radar-web && npm test`
- Typecheck i build (w tym kitchen sink i mock z `skipped`): `cd szczur-radar-web && npm run build`

#### Manual Verification:

- Zrzuty z `dev/mock-api.js` + `playwright-cli` w obu motywach i przy 375 px pokazują `/admin`, obie strony przebiegów, otwarty dzwonek i sekcje kitchen sinka
  - `/admin` z `r-skipped` (ton ostrzeżenia, „wymaga uwagi”) i `r-empty` (błąd);
  - `/admin/runs/r-skipped` (powód „pominięte elementy: N”) i `/admin/runs/r-empty` (tekst błędu, krok `fetch` `failed`);
  - otwarty dzwonek z oboma powiadomieniami;
  - sekcje plakietek i powiadomień na `/kitchen-sink`.
- Macierz 7 stanów: default i loading jak w zrzutach widoków; hover, focus-visible i disabled — N/A, zmiana nie dodaje elementów interaktywnych; error to `r-empty`; empty bez zmian (lista bez przebiegów nie dotyka nowej logiki)

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przez człowieka przed fazą 4.

---

## Phase 4: Dokumentacja i weryfikacja w PR env

### Overview

PRD i dokumenty operatora opisują nowe zachowanie i zmienioną decyzję S-06 o kodach wyjścia. PR env potwierdza brak regresji i nowy kontrakt na żywym Wykopie.

### Changes Required:

#### 1. PRD

**File**: `context/foundation/prd.md`

**Intent**: Doprecyzowanie S-08 zamiast cichego rozstrzygnięcia w kodzie (hard rule repo). Doprecyzowanie S-06 „Kody wyjścia crona się nie zmieniają” (`prd.md:260`) nie jest już pełne.

**Contract**:
- nowe doprecyzowanie (2026-10-18, S-08 `empty-run-signal`) pod NFR (`prd.md:258-260`): przebieg RANGE, w którym źródło nie dało posta do zapisu, kończy się `failed` (powiadomienie, cron exit 1, push Railway); pominięte przy dobrych postach dają tylko powiadomienie w aplikacji;
- doprecyzowanie pod FR-030 (`prd.md:225`): pusty przebieg jest `failed`, więc daty nie przesuwa; przebieg z pominiętymi ją przesuwa;
- w obu: reguła obejmuje też przebiegi zakresu uruchomione przez admina, więc zakres bez postów (np. dzisiejszy dzień tuż po północy) kończy się `failed` i powiadamia adminów.

Istniejące doprecyzowania zostają bez zmian (dopisywanie, nie przepisywanie).

#### 2. Plan wdrożenia

**File**: `context/deployment/deploy-plan.md`

**Intent**: Operator wie, co zobaczy przy pustym przebiegu i przy pominiętych, i jak to zdiagnozować.

**Contract**:
- nowa sekcja S-08 ze statusem i zachowaniem obu przypadków (dzwonek, cron, świeżość, linia WARN, tekst błędu na stronie przebiegu);
- runbook „pusty przebieg”: sprawdź Wykop i klucze, uruchom zakres ponownie z `/admin` po naprawie; następna noc i tak pobiera D-2 drugi raz. Przebieg admina za zakres bez postów (np. dziś tuż po północy) też kończy się `failed`, i to nie jest awaria;
- zapis weryfikacji w PR env i kroki weryfikacji produkcji po merge'u (pierwsza noc: linia crona z `skipped`, linia końcowa API);
- kontrakt `RunState` w sekcji S-05 (`:443`) i kody wyjścia (`:449`) dostają `skipped` i „pusty przebieg = `failed`” z odsyłaczem do S-08.

#### 3. Wytyczne repo

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Sekcja Deployment opisuje, kiedy admin dostaje powiadomienie i kiedy cron kończy się błędem.

**Contract**: akapit Watchdog (reguła powiadomień) dostaje pominięte elementy i pusty przebieg; akapit Daily run dostaje „przebieg zakresu bez postów kończy się `failed`, więc cron exit ≠ 0”.

### Success Criteria:

#### Automated Verification:

- Pełna bramka po zmianach dokumentacji: `cd szczur-radar-api && ./mvnw -B verify`, `tools/pipeline-trigger/test.sh`, `cd szczur-radar-web && npm test && npm run build`
- CI na PR zielone (wszystkie joby `.github/workflows/ci.yml`)

#### Manual Verification:

- PR env, cron z `TRIGGER_IN_ANY_ENVIRONMENT=true` i jednorazowym harmonogramem: przebieg kończy się jak dotąd, linia crona zawiera `skipped N`, a linia końcowa API `skipped N`
- PR env, przebieg admina dla bardzo starego zakresu (np. 2008-01-01): jeśli Wykop nie zwróci elementów, przebieg jest `failed`, dzwonek ma powiadomienie, strona przebiegu pokazuje tekst `NoPostsFetchedException` i krok `fetch` `failed`, a `/api/freshness` się nie zmienia. Jeśli Wykop zwróci elementy, wynik trafia do `deploy-plan.md`, a pokrycie zostaje przy IT z fazy 1
- PRD, `deploy-plan.md` i `REPOSITORY_GUIDELINES.md` opisują to samo zachowanie co kod (przegląd)

**Implementation Note**: Weryfikacja produkcji po merge'u (pierwsza noc) to runbook w `deploy-plan.md` i komentarz w issue S-08, nie krok Progress (`context/foundation/lessons.md`, „Implementacja, przegląd i archiwizacja na jednej gałęzi”).

---

## Testing Strategy

### Unit Tests:

- `SourceIngestTest`: wyjątek dla źródła z 0 elementów i dla źródła z samymi pominiętymi (liczby w wyjątku); brak wyjątku przy pominiętych i co najmniej jednym poście. Przypadek dwóch źródeł dopisze zmiana dodająca drugie źródło (wymóg w `sources/CLAUDE.md`).
- `PipelineRunTest`: `needsAttention` dla każdego statusu z `skipped` 0 i > 0.
- `runs.test.ts`: ton, etykieta i powody z pominiętymi i bez.

### Integration Tests:

- `PipelineAgentIT`: pusty zakres, same niemapowalne, część pominiętych — status, krok, liczniki, `error`, powiadomienie, kolejny przebieg w kolejce.
- `InternalPipelineRunIT`: pusty dzień przez `/internal` → `failed`, `skipped` w kluczach.
- `NotificationsIT`: `skipped` w `NotifiedRun`.
- `tools/pipeline-trigger/test.sh`: `empty` → exit 1, `skipped-warning` → exit 0, `old-api` → `skipped ?`.

### Manual Testing Steps:

1. Lokalny przebieg zwykłego dnia: bez regresji, `skipped 0` w linii końcowej API.
2. Zrzuty SPA na atrapie: `r-skipped`, `r-empty`, dzwonek, kitchen sink, oba motywy, 375 px.
3. PR env: cron z `skipped` w linii, próba pustego zakresu przez admina.

## Performance Considerations

Brak: jedno porównanie na źródło w kroku pobierania i jedno pole więcej w trzech odpowiedziach JSON.

## Migration Notes

Brak migracji: kolumna `skipped` istnieje od V3. Wdrożenie API i crona następuje tym samym merge'em. Cron czyta `skipped` z fallbackiem `?`, więc kolejność wdrożenia usług nie psuje linii logu. Po merge'u obowiązuje zakaz merge'a w oknie nocnym (00:30–01:30 UTC) jak dla każdej zmiany.

## References

- Research: `context/changes/empty-run-signal/research.md`
- Audyt: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:76,90,113`
- Roadmapa: `context/foundation/roadmap.md:147-158` (S-08), `:198-208` (S-12), `:248-259` (S-16)
- Decyzje S-06 o kodach crona i regule uwagi: `context/archive/2026-10-15-pipeline-run-history-dashboard/plan.md:73,346`, `plan-brief.md:25,48`, `research.md:249`
- Wzorzec testu błędu źródła: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineAgentIT.java:306-329`
- PRD: `context/foundation/prd.md:218-225` (FR-030), `:258-260` (NFR sygnał awarii)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — pusty przebieg i ostrzeżenie o pominiętych

#### Automated

- [x] 1.1 Pusty zakres, same niemapowalne i część pominiętych dają oczekiwany status, liczniki, krok i powiadomienie: `PipelineAgentIT`, `SourceIngestTest` — d1a906c
- [x] 1.2 `needsAttention` z `skipped` dla każdego statusu: `PipelineRunTest` — d1a906c
- [x] 1.3 Kontrakt `/internal` (pusty dzień `failed`, `skipped` w kluczach) i `NotifiedRun` (`skipped` w kluczach): `InternalPipelineRunIT`, `NotificationsIT` — d1a906c
- [x] 1.4 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — d1a906c

#### Manual

- [x] 1.5 Lokalnie (Postgres z `compose.local.yaml`, jar, prawdziwe `WYKOP_*` i `LLM_*`) przebieg admina dla zwykłego dnia kończy się jak dotąd. Linia końcowa API zawiera `skipped 0`, a przy `skipped = 0` nie ma linii WARN — 006babf

### Phase 2: Cron — `skipped` w kontrakcie `trigger.sh`

#### Automated

- [x] 2.1 Wszystkie przypadki atrapy, w tym `empty` (exit 1) i `skipped-warning` (exit 0): `tools/pipeline-trigger/test.sh` — a61aaf6

#### Manual

- [x] 2.2 README narzędzia i przykładowe logi zgadzają się z wyjściem `test.sh` (porównanie wzrokowe linii liczników) — 006babf

### Phase 3: SPA — powód, ton i etykieta ostrzeżenia

#### Automated

- [x] 3.1 Ton, etykieta i powody pominiętych oraz spójne fixture'y: `cd szczur-radar-web && npm test` — f6999e8
- [x] 3.2 Typecheck i build (w tym kitchen sink i mock z `skipped`): `cd szczur-radar-web && npm run build` — f6999e8

#### Manual

- [x] 3.3 Zrzuty z `dev/mock-api.js` + `playwright-cli` w obu motywach i przy 375 px pokazują `/admin`, obie strony przebiegów, otwarty dzwonek i sekcje kitchen sinka — 1673a28
- [x] 3.4 Macierz 7 stanów: default i loading jak w zrzutach widoków; hover, focus-visible i disabled — N/A, zmiana nie dodaje elementów interaktywnych; error to `r-empty`; empty bez zmian (lista bez przebiegów nie dotyka nowej logiki) — 006babf

### Phase 4: Dokumentacja i weryfikacja w PR env

#### Automated

- [x] 4.1 Pełna bramka po zmianach dokumentacji: `cd szczur-radar-api && ./mvnw -B verify`, `tools/pipeline-trigger/test.sh`, `cd szczur-radar-web && npm test && npm run build` — c9b4065
- [x] 4.2 CI na PR zielone (wszystkie joby `.github/workflows/ci.yml`) — b42b579

#### Manual

- [ ] 4.3 PR env, cron z `TRIGGER_IN_ANY_ENVIRONMENT=true` i jednorazowym harmonogramem: przebieg kończy się jak dotąd, linia crona zawiera `skipped N`, a linia końcowa API `skipped N`
- [ ] 4.4 PR env, przebieg admina dla bardzo starego zakresu (np. 2008-01-01): jeśli Wykop nie zwróci elementów, przebieg jest `failed`, dzwonek ma powiadomienie, strona przebiegu pokazuje tekst `NoPostsFetchedException` i krok `fetch` `failed`, a `/api/freshness` się nie zmienia. Jeśli Wykop zwróci elementy, wynik trafia do `deploy-plan.md`, a pokrycie zostaje przy IT z fazy 1
- [x] 4.5 PRD, `deploy-plan.md` i `REPOSITORY_GUIDELINES.md` opisują to samo zachowanie co kod (przegląd) — 1673a28
