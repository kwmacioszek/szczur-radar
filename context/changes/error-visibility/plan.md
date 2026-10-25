# S-18: błędy wymagające reakcji docierają do operatora — plan implementacji

## Overview

Błędy, które dziś kończą się samą linią w logu, oraz każdy przebieg wymagający uwagi trafiają do operatora przez kanał F-04 (healthchecks.io → Telegram). Ścieżki tylko z logiem to: nieudane powiadomienie, odzyskiwanie przy starcie, zamknięcie przebiegu, watchdog, sprzątanie sierot i zapis licznika (A5). Wyjątkiem są nocne `failed`/`interrupted`, które już zgłasza cron.

Wysyłka idzie przez kolejkę z własnym wątkiem i limitem, więc nie blokuje wywołującego, działa z przerwanego wątku i nie zalewa operatora. Łańcuch przyczyn wyjątków dostawców LLM nie wynosi klucza do logów (B7), a `/api/ready` zapisuje przyczynę 503 (C10).

Wycinek roadmapy S-18 (M-2), issue #77; ustalenia audytu C10, C11, B7, A5 (`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md`), ograniczenia nadawcy z przeglądu F-04 (F8, F10: `context/archive/2026-10-20-out-of-app-alert-channel/reviews/impl-review.md`).

## Current State Analysis

Ścieżki względem `szczur-radar-api/src/main/java/pl/szczurradar/api/`, chyba że podano inaczej.

- **Nadawca F-04** (`alerts/`): `OperatorAlerts.report(AlertEvent, String)` wysyła synchronicznie `/fail` z opisem i od razu ping sukcesu (`HealthchecksAlerts.java:68-96`), nigdy nie rzuca. Ograniczenia:
  - blokuje do ok. 2 × (5 s + 10 s);
  - na przerwanym wątku zwraca `FAILED` bez wysyłki (`:86-90`);
  - bean jest tylko w serwerze (`AlertsConfiguration.java:19-21`);
  - `AlertEvent` ma tylko `TEST_SIGNAL`;
  - `HealthchecksAlerts.report` ma `@Nullable detail`, a interfejs nie (F10);
  - healthchecks.io przyjmuje ≤5 pingów na minutę na check, czyli ≤2 zdarzenia na minutę.
- **Ścieżki kończące się tylko logiem** (nikt poza logiem ich nie zgłasza):
  - nieudane powiadomienie adminów — `notifications/RunNotifications.java:53-55`;
  - nieudane odzyskiwanie przy starcie — `pipeline/PipelineStartupRecovery.java:94-98`; przed otwarciem portu;
  - błąd sprawdzenia watchdoga — `pipeline/RunWatchdog.java:90-93`; wątek `pipeline-watchdog`, co minutę;
  - nieudane sprzątanie sierot (S-11) — `pipeline/PipelineRunService.java:236-239`; wątek żądania HTTP;
  - nieudane zamknięcie przebiegu — `PipelineRunService.java:387-389`. Przebieg zostaje `running` i bez powiadomienia, a `own.remove` zabiera go watchdogowi;
  - `onClose` (`PipelineRunService.java:403-415`) nie ma żadnego `catch`. Wyjątek transakcji pomija `shutdownNow()`.
- **A5:** `ConcurrentAssessmentDispatcher.count` (`:317-336`) przy nieudanym zapisie loguje tylko WARN na post. Status przebiegu liczy się w pamięci (`RunFlow.AssessedBatch.status`), ale `PipelineRun.needsAttention` (`run/PipelineRun.java:281-294`) czyta liczniki z bazy. Zgubiony `dead` albo `processing_errors` sprawia, że `partial` nie da powiadomienia.
- **Co operator już widzi:** przebieg `failed` / `interrupted` / `partial` z martwym postem, błędem albo pominiętymi elementami daje dzwonek (`RunNotifications.runEnded`, idempotentne: `created > 0` tylko za pierwszym razem). Nocny (crona) `failed` / `interrupted` daje też kod wyjścia crona i wiadomość z checka `pipeline-trigger` (F-04). Nocny `partial` kończy crona kodem 0, więc do Telegrama nie trafia (doprecyzowanie F-04 w `context/foundation/prd.md:263`).
- **Kontekst CLI:** `PipelineRunService`, `RunNotifications` i `ConcurrentAssessmentDispatcher` istnieją też w kontekście bez serwera (`.web(NONE)`), gdzie beana `OperatorAlerts` nie ma. Wzorzec: `ObjectProvider` rozwiązywany przy użyciu, jak `PipelineStallSettings` w `PipelineRunService` (`:227`).
- **„Przebieg crona”** to `requestedBy = SystemAccount.SCHEDULER` (`user/SystemAccount`). Regułę ma dziś tylko `PipelineRunController.java:186` (`RunView.scheduled`).
- **B7:** klienci czyszczą komunikat (`llm/transport/ProviderMessages.clean`: literalna zamiana klucza, ≤500 znaków), ale surowy wyjątek SDK zostaje przyczyną:
  - `TransportException.ofTimeoutOrConnection` (`:108-122`) i `unclassified` (`:130-132`);
  - `gemini/GeminiClient.classify` (`:123-134`);
  - `openrouter/OpenRouterClient.java:189-204`;
  - `deepseek/DeepSeekClient.java:143-149`.

  Gemini `ApiException` ma w komunikacie `error.message` dostawcy, np. `API key … not valid` (fixture `GeminiClientTest.java:207-215`). `log.error(…, ex)` przy checku LLM (`ConcurrentAssessmentDispatcher.java:146`), przy błędzie posta (`:304`) i w `llm/api/LlmCheckController.java:56` drukuje cały łańcuch: brak `logback.xml`, domyślny wzorzec Springa z `%wEx`. Typ przyczyny czyta się tylko przy klasyfikacji, przed opakowaniem (`TransportException.java:109,115`, `GeminiClient.java:124`). Późniejszy kod nie robi `instanceof` na przyczynie.
- **`/api/ready`** (`health/HealthController.java:37-46`) zwraca 503 bez logu. Healthcheck Railway go nie używa (`/api/health`, `.railway/railway.ts:119`). Wołają go:
  - `tools/deploy-gate/verify-apply.sh` po apply, do ~90 razy w 3 min;
  - zewnętrzny monitor po deployu, który już alarmuje w Telegramie (`production/deploy-health`).

## Desired End State

- Operator dostaje w Telegramie (check `<env>/api-events`) jedno zdarzenie (wiadomość „down” i „up”, niżej), gdy:
  - przebieg wymaga uwagi (powstały powiadomienia w dzwonku), z wyjątkiem przebiegu crona zakończonego `failed`/`interrupted` (zgłasza go cron);
  - powiadomienie, odzyskiwanie przy starcie, zamknięcie przebiegu (także `onClose`), sprawdzenie watchdoga albo sprzątanie sierot się nie powiodło;
  - wyniki postów nie trafiły do liczników przebiegu (A5) — raz na przebieg.
- Zgłoszenie nigdy nie blokuje wywołującego i wychodzi też z wątku przerwanego przy zamykaniu. To samo zdarzenie dla tego samego przebiegu idzie najwyżej raz na 10 min, a łącznie najwyżej 2 zdarzenia na minutę.
- Treść wiadomości nie zawiera sekretów. Składa się ze zdarzenia, id przebiegu, statusu, liczników, błędu przebiegu (ten sam tekst co w `/admin`), odnośnika `/admin/runs/<id>`, a przy awarii ścieżki z klasy wyjątku (bez jego komunikatu).
- Wydruk łańcucha przyczyn każdego wyjątku z klientów LLM nie zawiera klucza; testy każdego klienta to przypinają.
- 503 z `/api/ready` zostawia linię WARN z klasą i komunikatem wyjątku.
- Weryfikacja: testy jednostkowe i IT w `./mvnw -B verify`, prawdziwy alarm z przebiegu admina w PR env na testowym checku i wiadomość w Telegramie.
- Każde zdarzenie to w Telegramie dwie wiadomości. Najpierw „down” z opisem, a zaraz po niej „up”, które tylko uzbraja check na następne zdarzenie i nie znaczy, że problem minął. Przy 2 zdarzeniach na minutę to 4 wiadomości, w limicie Telegrama 6 na minutę.

### Key Discoveries:

- `RunNotifications.runEnded` to jedno miejsce, które widzi każdy koniec przebiegu (5 wywołujących, `pipeline/CLAUDE.md`, „Notifications”), a `created > 0` daje gotową idempotencję alarmu.
- Wrzucenie do kolejki nie zależy od flagi przerwania, więc ograniczenia (1) i (2) z F8 znikają bez zmiany `HealthchecksAlerts`.
- `ContextClosedEvent` (czyli `onClose`) przychodzi przed niszczeniem beanów, więc kolejka żyje, gdy `onClose` zgłasza przerwane przebiegi.
- `send-test-alert` (`cli/SendTestAlertCommand`) woła `HealthchecksAlerts` synchronicznie bez Springa — zostaje bez zmian.

## What We're NOT Doing

- Appender Logback ani alarm z każdej linii ERROR: tylko jawne zdarzenia `AlertEvent` (decyzja planu). Nieobsłużone 500 i ERROR-y bibliotek zostają w logu.
- Alarmu przy upadku przebiegu (`PipelineRunService.java:309`), nieudanym checku LLM (`ConcurrentAssessmentDispatcher.java:146`) i błędzie posta (`:304`) jako osobnych zdarzeń: trafiają do operatora przez `RUN_NEEDS_ATTENTION` (dzwonek) albo przez crona.
- Naprawy liczników przy A5 (ponowny zapis z pamięci): tylko widoczność, model liczenia z S-06 bez zmian.
- Alarmu przy 503 z `/api/ready`: tylko WARN. Awarię po deployu zgłasza monitor. Awaria bazy przy bezczynnym API zostaje bez alarmu, dopóki nie ruszy przebieg albo cron.
- Logu przyczyny 503 w `/api/deploy-safety` (`DeploySafetyController.java:55`): poza C10.
- Maskowania w konfiguracji Logback i nowego `logback.xml`.
- Error trackera, metryk, actuatora; nowych zmiennych środowiska i migracji.
- Ponowień wysyłki i trwałej kolejki: zdarzenie w kolejce ginie przy SIGKILL (dzwonek i log zostają).
- A6 i reszty S-12 (`## Parked`), dni bez danych (S-13).

## Implementation Approach

1. Nad istniejącym synchronicznym nadawcą powstaje web-only kolejka `OperatorAlertQueue` z jednym wątkiem, deduplikacją i limitem. Ścieżki błędów wołają tylko ją: nieblokująco, nigdy nie rzuca.
2. Beany wspólne z kontekstem CLI biorą ją przez `ObjectProvider` i bez beana nic nie robią.
3. Alarm „wymaga uwagi” wisi na idempotentnym `runEnded`. Reguła „pomiń dubel crona” korzysta z jednej metody `PipelineRun.scheduled()`, którą przejmuje też `RunView`.
4. B7 naprawia się u źródła. Każdy surowy wyjątek SDK, który klient dołącza jako przyczynę, jest zastępowany oczyszczoną kopią z oryginalnym stack trace'em, tuż po klasyfikacji.

## Critical Implementation Details

- **Zamykanie:** `onClose` musi zgłosić `RUN_CLOSE_FAILED` przed `shutdownNow()` i wołać `shutdownNow()` w `finally`. Kolejka przy `destroy()` dopróżnia oczekujące zdarzenia najwyżej `DRAIN_TIMEOUT` (5 s; okno SIGTERM→SIGKILL Railway jest nieznane, audyt Open Question 5), potem zatrzymuje wątek. Beany z `ObjectProvider` nie rejestrują zależności, więc kolejka może zostać zniszczona przed `RunWatchdog`: zgłoszenie po `destroy()` jest odrzucane z linią INFO, bez wyjątku.
- **Limit healthchecks.io:** odmowa pingu sukcesu zostawia check w stanie „down”, a wtedy kolejne zdarzenia nie dają wiadomości (alarm tylko przy zmianie stanu). Dlatego limit ≤2 zdarzenia na minutę liczy kolejka, nie healthchecks.io.

## Phase 1: Nadawca w tle

### Overview

Kanał przyjmuje zdarzenia bez blokowania, z każdego wątku, i pilnuje limitów. Nikt go jeszcze nie woła poza testami.

### Changes Required:

#### 1. Zdarzenia

**File**: `alerts/AlertEvent.java`

**Intent**: Zamknięty zbiór zdarzeń S-18 obok `TEST_SIGNAL`, każde z krótkim opisem dla człowieka.

**Contract**: nowe wartości:
- `RUN_NEEDS_ATTENTION`;
- `NOTIFICATION_FAILED`;
- `RECOVERY_FAILED`;
- `RUN_CLOSE_FAILED`;
- `WATCHDOG_FAILED`;
- `ORPHAN_CLEANUP_FAILED`;
- `RUN_COUNTERS_LOST`.

Opis każdej mówi, co się stało i co operator powinien sprawdzić (np. „admins were not notified about a run; check /admin”).

#### 2. Wyrównanie nadawcy

**File**: `alerts/OperatorAlerts.java`, `alerts/NoOperatorAlerts.java`

**Intent**: `detail` może być `null` w całym pakiecie, jak już w `HealthchecksAlerts` (F10).

**Contract**: `Delivery report(AlertEvent event, @Nullable String detail)`.

#### 3. Kolejka zgłoszeń

**File**: `alerts/OperatorAlertQueue.java` (nowy), `alerts/AlertsConfiguration.java`

**Intent**: Jedyny punkt wejścia ścieżek błędów. Przyjmuje zgłoszenie od razu i wysyła je na własnym wątku przez `OperatorAlerts`, więc wywołujący nie czeka i przerwany wątek nie gubi zdarzenia.

Deduplikuje: to samo zdarzenie z tym samym przebiegiem w oknie `REPEAT_WINDOW` (10 min) idzie raz. Powtórzenia są liczone, a następne wysłane zgłoszenie z tym kluczem po oknie niesie dopisek z ich liczbą.

Pilnuje limitu `MAX_PER_MINUTE` (2 w ciągu 60 s): nadmiar czeka w kolejce o pojemności `CAPACITY` (20). Przepełnienie odrzuca zgłoszenie z WARN nazywającym zdarzenie.

Każde przyjęte zgłoszenie loguje INFO z nazwą zdarzenia i przebiegiem. Po wysyłce kolejka loguje drugą linię INFO: zdarzenie, przebieg i wynik `Delivery` (`DELIVERED` / `FAILED` / `NOT_CONFIGURED`). To dowód w PR env, bo linia `HealthchecksAlerts` nie zna przebiegu. Bean tylko w serwerze, obok `OperatorAlerts`.

**Contract**:
- `public void raise(AlertEvent event, @Nullable UUID runId, String detail)` — nie blokuje, nie rzuca;
- własny jednowątkowy wykonawca (platformowy, nazwa `operator-alerts`);
- zegar wstrzykiwany (`InstantSource`) dla testów;
- `destroy()` dopróżnia kolejkę najwyżej `DRAIN_TIMEOUT` (5 s), potem `shutdownNow()`; zgłoszenie po `destroy()` jest odrzucane z INFO.

Detal przycina dalej `HealthchecksAlerts.body`.

#### 4. Testy

**File**: `src/test/java/pl/szczurradar/api/alerts/OperatorAlertQueueTest.java` (nowy), `AlertsConfigurationTest.java`

**Intent**: Zachowanie kolejki na fałszywym `OperatorAlerts` i sterowanym zegarze, bez sieci.

**Contract**: przypadki:
- `raise` wraca od razu, gdy delegat wisi;
- `raise` z przerwanego wątku dostarcza zdarzenie, a flaga przerwania wywołującego zostaje;
- powtórzenie w oknie nie wychodzi, a po oknie wychodzi z liczbą powtórzeń;
- inne zdarzenie albo inny przebieg w oknie wychodzi;
- trzecie zdarzenie w minucie czeka na koniec minuty;
- przepełnienie daje WARN i nie rzuca;
- `destroy()` dostarcza oczekujące w limicie czasu, a po nim `raise` nie rzuca.

`AlertsConfigurationTest`: serwer ma kolejkę, a kontekst bez serwera jej nie ma.

### Success Criteria:

#### Automated Verification:

- Testy pakietu `alerts` zielone: `./mvnw -B test -Dtest='pl.szczurradar.api.alerts.*Test'`
- Kontekst bez serwera startuje bez `OperatorAlertQueue` (`AlertsConfigurationTest`)
- Pełny zestaw zielony: `./mvnw -B verify`

---

## Phase 2: Podłączenie ścieżek

### Overview

Ścieżki błędów i przebiegi wymagające uwagi zgłaszają zdarzenia do kolejki.

### Changes Required:

#### 1. Jedna reguła „przebieg crona”

**File**: `pipeline/run/PipelineRun.java`, `pipeline/api/PipelineRunController.java`

**Intent**: Reguła „to przebieg crona” w jednym miejscu, używana przez `RunView` i alarm.

**Contract**: `public boolean scheduled()` — `SystemAccount.SCHEDULER.id().equals(requestedBy)`; `RunView.scheduled` z niej korzysta.

#### 2. Powiadomienia

**File**: `notifications/RunNotifications.java`

**Intent**: Gdy `runEnded` utworzył powiadomienia (`created > 0`), zgłasza `RUN_NEEDS_ATTENTION`. Wyjątek: przebieg crona (`scheduled()`) zakończony `failed` albo `interrupted`, bo ten zgłasza już cron kodem wyjścia. W `catch` zgłasza `NOTIFICATION_FAILED`.

**Contract**:
- kolejka przez `ObjectProvider<OperatorAlertQueue>`, nic bez beana;
- detal `RUN_NEEDS_ATTENTION`: rodzaj, cron albo admin, status z przyczyną przerwania, `dead`, `errors`, `skipped`, błąd przebiegu przycięty do 300 znaków (ten sam tekst co w `/admin`; komunikaty klientów LLM są w nim już oczyszczone z klucza), `/admin/runs/<id>`;
- detal `NOTIFICATION_FAILED`: id przebiegu i klasa wyjątku, „details in the API log”.

Komunikatu wyjątku z bazy nie wysyła.

#### 3. Silnik przebiegów

**File**: `pipeline/PipelineStartupRecovery.java`, `pipeline/RunWatchdog.java`, `pipeline/PipelineRunService.java`

**Intent**: Każda ścieżka, która dziś kończy się samym ERROR, zgłasza zdarzenie: `RECOVERY_FAILED`, `WATCHDOG_FAILED` (z id przebiegu, więc raz na 10 min na przebieg), `ORPHAN_CLEANUP_FAILED`, `RUN_CLOSE_FAILED` (z `close`, z id przebiegu). `onClose` dostaje `catch`: ERROR i `RUN_CLOSE_FAILED` z listą przebiegów tej instancji, a `shutdownNow()` w `finally`.

**Contract**:
- `PipelineStartupRecovery` i `RunWatchdog` (web-only) wstrzykują kolejkę wprost;
- `PipelineRunService` przez `ObjectProvider`;
- detal: co się nie udało, id przebiegu (gdy jest), klasa wyjątku i co się stanie dalej (np. „runs left running are cleaned up by the next start request after PIPELINE_STALL_TIMEOUT”).

Linie ERROR zostają.

#### 4. Liczniki przebiegu (A5)

**File**: `pipeline/assess/ConcurrentAssessmentDispatcher.java`

**Intent**: Nieudane zapisy licznika w jednym `dispatch` są zbierane: ile wyników nie weszło, ile z nich martwych i ile błędów technicznych. Dotyczy to też zapisu z `countOutage`. Po zebraniu wszystkich zadań, gdy coś przepadło, idzie jedna linia ERROR o zaniżonych licznikach i `needsAttention` oraz jedno zgłoszenie `RUN_COUNTERS_LOST`. WARN na post zostaje.

**Contract**:
- licznik zgubionych żyje w stanie jednego `dispatch` (obok `Tasks`/`Progress`), bezpieczny wątkowo;
- kolejka przez `ObjectProvider`;
- detal: id przebiegu, liczba zgubionych wyników, martwych i błędów, `/admin/runs/<id>`.

#### 5. Testy

**File**: testy obok zmienionych klas (`src/test/java/pl/szczurradar/api/…`)

**Intent**: Każda ścieżka zgłasza dokładnie raz, z właściwym zdarzeniem i przebiegiem; dubel crona jest pomijany.

**Contract**: przypadki:
- `RunNotificationsTest` (nowy, mocki):
  - przebieg admina `failed` daje `RUN_NEEDS_ATTENTION`;
  - przebieg crona `failed` i `interrupted` nie daje;
  - przebieg crona `partial` z martwym postem daje;
  - `created == 0` nie daje;
  - wyjątek repozytorium daje `NOTIFICATION_FAILED` i nie rzuca;
  - bez beana kolejki nic nie rzuca;
- `PipelineRunTest`: `scheduled()`;
- `PipelineStartupRecoveryIT` albo test jednostkowy: nieudana transakcja daje `RECOVERY_FAILED`;
- `RunWatchdogTest`: błąd sprawdzenia daje `WATCHDOG_FAILED` z id przebiegu;
- `PipelineRunServiceTest`: nieudane sprzątanie daje `ORPHAN_CLEANUP_FAILED`, a żądanie jest dalej rozstrzygane; nieudany `close` daje `RUN_CLOSE_FAILED`; nieudany `onClose` daje `RUN_CLOSE_FAILED` i `shutdownNow()` jednak się wykonuje;
- `ConcurrentAssessmentDispatcherTest`:
  - dwa nieudane `count` (jeden martwy) dają jedno `RUN_COUNTERS_LOST` z liczbami;
  - bez nieudanych zapisów nie ma zgłoszenia;
- `NotificationsIT`: przez prawdziwy kontekst, przebieg admina wymagający uwagi zgłasza `RUN_NEEDS_ATTENTION` do kolejki (podmienionej na przechwytującą przez `@MockitoBean` albo `@TestConfiguration`).

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe zielone: `./mvnw -B test`
- IT zielone, w tym `NotificationsIT`, `PipelineStartupRecoveryIT`, `RunWatchdogIT`, `PipelineAgentIT` (bez nowych ERROR w logu): `./mvnw -B verify`
- `AuthorizationCompletenessIT` bez zmian (żadnej nowej trasy)

---

## Phase 3: Klucze poza logami i przyczyna 503

### Overview

Łańcuch przyczyn wyjątków z klientów LLM nie zawiera klucza, a 503 z `/api/ready` ma przyczynę w logu.

### Changes Required:

#### 1. Oczyszczona przyczyna

**File**: `llm/transport/ProviderMessages.java`, `llm/transport/CleanedProviderException.java` (nowy)

**Intent**: Kopia łańcucha przyczyn bez klucza, ale ze śladem do diagnozy. Każdy element staje się wyjątkiem z komunikatem `<oryginalna klasa>: <oczyszczony komunikat>`, oryginalnym stack trace'em i oczyszczoną przyczyną. Bez wyjątków stłumionych, z limitem głębokości i ochroną przed cyklem.

**Contract**: `public @Nullable Throwable cleanCause(@Nullable Throwable error)` w `ProviderMessages`; `CleanedProviderException extends RuntimeException` to publiczna klasa w `llm/transport` (klienci są w innych pakietach), tworzona tylko przez `ProviderMessages.cleanCause`.

#### 2. Klienci i fabryki wyjątków

**File**: `llm/transport/TransportException.java`, `llm/gemini/GeminiClient.java`, `llm/openrouter/OpenRouterClient.java`, `llm/deepseek/DeepSeekClient.java`

**Intent**: Każde miejsce, które dołącza surowy wyjątek SDK albo transportu jako przyczynę, dołącza `messages.cleanCause(error)`, i to po klasyfikacji (która czyta typy w oryginalnym łańcuchu). Dotyczy:
- `ofTimeoutOrConnection`;
- `unclassified`;
- `GeminiClient.classify`;
- `OpenRouterClient.java:189-204`;
- `DeepSeekClient.java:143-149`.

Komunikaty bez zmian.

**Contract**: sygnatury fabryk bez zmian (już biorą `ProviderMessages`); przyczyna `TransportException` i `IllegalStateException` z `unclassified` to zawsze `CleanedProviderException` albo `null`.

#### 3. Przyczyna 503

**File**: `health/HealthController.java`

**Intent**: Nieudane `SELECT 1` zostawia jedną linię WARN z `ex.toString()` (klasa i komunikat), bez stack trace'u i bez alarmu.

**Contract**: odpowiedź bez zmian (503 `{"status":"unavailable"}`).

#### 4. Testy

**File**: `GeminiClientTest`, `OpenRouterClientTest`, `DeepSeekClientTest`, `ProviderMessagesTest`, `src/test/java/pl/szczurradar/api/health/` (test kontrolera)

**Intent**: Dowód, że klucz nie wychodzi przez żadną przyczynę, i że ślad zostaje.

**Contract**:
- dla każdego klienta przypadek, w którym odpowiedź dostawcy (401, 5xx, błąd bez statusu, timeout) cytuje klucz: pełny wydruk `printStackTrace` wyjątku, który opuszcza klienta, nie zawiera klucza, a zawiera nazwę klasy oryginalnej przyczyny;
- `ProviderMessagesTest`: kopia łańcucha z cyklem i z `null` komunikatem;
- trzy istniejące asercje `getCause()` `isSameAs(sdkError)` w przypadkach „nieznany błąd SDK → unclassified” (`GeminiClientTest.java:247`, `DeepSeekClientTest.java:239`, `OpenRouterClientTest.java:496`) zmieniają się na: przyczyna to `CleanedProviderException` z nazwą oryginalnej klasy w komunikacie i oryginalnym stack trace'em;
- test kontrolera: 503 i jedna linia WARN z klasą wyjątku.

### Success Criteria:

#### Automated Verification:

- Testy klientów LLM i transportu zielone: `./mvnw -B test -Dtest='pl.szczurradar.api.llm.**.*Test'`
- Test „klucza nie ma w łańcuchu” jest czerwony bez `cleanCause` (sprawdzenie celowym cofnięciem jednego miejsca)
- Pełny zestaw zielony, w tym `LlmOutageIT` i `LlmCheckControllerIT`: `./mvnw -B verify`

---

## Phase 4: Dokumenty i weryfikacja w PR env

### Overview

Dokumenty mówią, co trafia do Telegrama i jak to sprawdzić. Alarm jest sprawdzony na prawdziwym kanale w PR env.

### Changes Required:

#### 1. Dokumenty pakietów

**File**: `pipeline/CLAUDE.md`, `llm/CLAUDE.md`, javadoc `alerts/OperatorAlertQueue`

**Intent**:
- `pipeline/CLAUDE.md`, sekcja „Notifications” i Rules: który koniec przebiegu daje `RUN_NEEDS_ATTENTION` (i dlaczego nie nocny `failed`). Gdy cron odpuści wcześniej (limit 90 min, API nieosiągalne, `POST` bez odpowiedzi), jego exit 1 mówi tylko „not ended”, a późniejszy wynik przebiegu jest w `/admin` i w dzwonku, nie w Telegramie. Do tego lista ścieżek z ich zdarzeniami, reguła „nowa ścieżka, która kończy się ERROR i której nic innego nie zgłasza, dostaje zdarzenie `AlertEvent` i woła `OperatorAlertQueue`”;
- `llm/CLAUDE.md`: przyczyny wyjątków klientów są oczyszczoną kopią (`cleanCause`), nowy klient robi to samo.

**Contract**: istniejące sekcje uzupełnione, bez nowych plików.

#### 2. Wytyczne, PRD, plan wdrożeń

**File**: `REPOSITORY_GUIDELINES.md`, `context/foundation/prd.md`, `context/deployment/deploy-plan.md`, `tools/healthchecks/README.md`

**Intent**:
- `REPOSITORY_GUIDELINES.md`: opis `alerts` (zdarzenia S-18, kolejka, limity) zamiast „the error paths are wired in S-18”, akapit Watchdog i kanału poza aplikacją;
- PRD: `> Doprecyzowanie (S-18 error-visibility)` po doprecyzowaniu F-04 (`prd.md:263`):
  - `partial` z martwym postem albo błędem, pominięte elementy i przebieg admina `failed`/`interrupted` trafiają teraz do Telegrama;
  - nocne `failed`/`interrupted` tylko z crona;
  - awarie ścieżek sygnału też;
- `deploy-plan.md`: nowa sekcja S-18:
  - linia Status prawdziwa także po merge'u („delivered by PR #N; production verification and closure in issue #77”);
  - lista zdarzeń, limity i co znaczy brak wiadomości; zdarzenie to wiadomość „down” z opisem i zaraz po niej „up”, które tylko uzbraja check; po exit 1 „not ended” z crona wynik przebiegu jest w `/admin`, nie w Telegramie;
  - weryfikacja w PR env;
  - runbook produkcji po merge'u, poza Progress: brak nowych zmiennych, sprawdzenie linii „Operator alerts: sent to …” po deployu, przebieg admina `2004-01-01`–`2004-01-01` (bez resztek w kolejce) → wiadomość `RUN_NEEDS_ATTENTION`, wyniki w issue #77;
- `tools/healthchecks/README.md`: check `api-events` niesie zdarzenia S-18.

**Contract**: sekcje dopisane, istniejące nienaruszone poza zdaniami, które S-18 unieważnia.

#### 3. Weryfikacja w PR env

**Intent**: Jeden prawdziwy alarm przez kanał od API do Telegrama, na testowym checku PR env, bez dotykania produkcji.

Kroki:
1. Operator: `tools/healthchecks/checks.sh apply szczur-radar-pr-<N>` z `HEALTHCHECKS_API_KEY`, potem ustawia `HEALTHCHECK_PING_URL` na API PR env.
2. Agent:
   - sprawdza w `/admin` (lista nieudanych postów), że nie ma resztek; inaczej `startRange` najpierw kolejkuje RETRY, który wydaje wywołania LLM (`PostRepository.existsLeftover`);
   - uruchamia przebieg zakresu admina sprzed istnienia Wykopu, `{"from":"2004-01-01","to":"2004-01-01"}`, przez `POST /api/admin/pipeline-runs`. Kontroler przyjmuje tylko `from ≤ to ≤ dziś`, a dzisiejszy tag prawie zawsze ma posty, które poszłyby do oceny. Tu źródło nie da elementów, więc `NoPostsFetchedException` → `failed`, bez kroku oceny i bez LLM (gdyby Wykop odrzucił datę, przebieg też kończy się `failed`);
   - w logu API sprawdza linię wyniku wysyłki z kolejki: zdarzenie `RUN_NEEDS_ATTENTION`, id tego przebiegu, `DELIVERED`.
3. Operator: potwierdza wiadomość w Telegramie; na koniec `checks.sh delete szczur-radar-pr-<N>`.

**Contract**: dowody (linie logu, czas, id przebiegu) w komentarzu issue #77.

### Success Criteria:

#### Automated Verification:

- CI na PR zielone (wszystkie joby `ci.yml`, w tym `railway-config`)
- W PR env (agent): przebieg admina `2004-01-01`–`2004-01-01` (bez resztek w kolejce) kończy się `failed`, a log API ma linię wyniku wysyłki z kolejki z `RUN_NEEDS_ATTENTION`, id tego przebiegu i `DELIVERED`
- Dokumenty: `grep` po `OperatorAlertQueue` w `pipeline/CLAUDE.md` i `REPOSITORY_GUIDELINES.md` oraz po „S-18” w `prd.md` i `deploy-plan.md` znajduje nowe akapity

#### Manual Verification:

- Wiadomość w Telegramie z checka `szczur-radar-pr-<N>/api-events` ma zdarzenie `RUN_NEEDS_ATTENTION`, id przebiegu i odnośnik `/admin/runs/<id>`, bez sekretów
- Testowy check PR env usunięty (`checks.sh delete szczur-radar-pr-<N>`)

---

## Testing Strategy

### Unit Tests:

- Kolejka: brak blokowania, przerwany wątek, okno powtórzeń z licznikiem, limit 2/min, przepełnienie, dopróżnienie przy zamykaniu.
- Reguła alarmu „wymaga uwagi”: admin / cron × `failed` / `interrupted` / `partial`, `created == 0`, brak beana.
- Każda ścieżka awarii zgłasza raz właściwe zdarzenie (mocki `RunRecorder`, `TransactionTemplate`).
- A5: jedno zgłoszenie na `dispatch` z liczbami.
- Klienci LLM: wydruk łańcucha przyczyn bez klucza (401, 5xx, błąd bez statusu, timeout); `cleanCause` z cyklem i `null`.

### Integration Tests:

- `NotificationsIT`: prawdziwy koniec przebiegu admina wymagający uwagi zgłasza `RUN_NEEDS_ATTENTION`.
- Istniejące `PipelineAgentIT`, `RunWatchdogIT`, `OrphanedRunIT`, `PipelineStartupRecoveryIT`, `LlmOutageIT`, `LlmCheckControllerIT` zielone bez zmian zachowania.
- `AlertsConfigurationTest`: komendy operatora (`migrate`) startują bez kolejki.

### Manual Testing Steps:

1. W PR env z testowym checkiem, bez resztek w kolejce: przebieg admina `2004-01-01`–`2004-01-01` → `failed` → wiadomość `RUN_NEEDS_ATTENTION` w Telegramie.
2. Usunięcie testowego checka po weryfikacji.

## Performance Considerations

Jeden dodatkowy wątek platformowy i kolejka do 20 zgłoszeń. Zgłoszenie to wpis do kolejki, a wysyłka to dwa krótkie żądania HTTP na zdarzenie. Bezczynne API nie wysyła nic, więc nic nie zmienia usypiania na Railway. Limity: 2 zdarzenia na minutę (healthchecks.io: 5 pingów na minutę na check). Każde zdarzenie daje w Telegramie wiadomość „down” i „up” (check `api-events` ma wszystkie kanały, bez filtra: `tools/healthchecks/checks.sh`), czyli najwyżej 4 na minutę przy limicie Telegrama 6 na minutę na czat.

## Migration Notes

Bez migracji bazy, nowych tabel i zmiennych środowiska (`tools/db-backup/tables.sh` bez zmian). Produkcja ma `HEALTHCHECK_PING_URL` od F-04, więc po merge'u alarmy działają od razu. Wycofanie to revert commitu; nic nie zostaje w danych.

## References

- Roadmapa: `context/foundation/roadmap.md`, sekcja S-18; issue #77
- Audyt: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (A5, B7, C10, C11)
- Przegląd F-04 (F8, F10): `context/archive/2026-10-20-out-of-app-alert-channel/reviews/impl-review.md`
- Plan F-04 (nadawca, limity): `context/archive/2026-10-20-out-of-app-alert-channel/plan.md`
- Zaparkowany S-12 (A5 → S-18): `context/archive/2026-10-24-reliable-run-notifications/frame.md`
- Wzorzec przyczyny bez sekretów: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java:350-360`, `alerts/HealthchecksAlerts.java:91-94`
- Wzorzec `ObjectProvider` w beanie wspólnym z CLI: `pipeline/PipelineRunService.java:227`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Nadawca w tle

#### Automated

- [x] 1.1 Testy pakietu `alerts` zielone: `./mvnw -B test -Dtest='pl.szczurradar.api.alerts.*Test'` — 75107c3
- [x] 1.2 Kontekst bez serwera startuje bez `OperatorAlertQueue` (`AlertsConfigurationTest`) — 75107c3
- [x] 1.3 Pełny zestaw zielony: `./mvnw -B verify` — 75107c3

### Phase 2: Podłączenie ścieżek

#### Automated

- [x] 2.1 Testy jednostkowe zielone: `./mvnw -B test` — d585dbe
- [x] 2.2 IT zielone, w tym `NotificationsIT`, `PipelineStartupRecoveryIT`, `RunWatchdogIT`, `PipelineAgentIT` (bez nowych ERROR w logu): `./mvnw -B verify` — d585dbe
- [x] 2.3 `AuthorizationCompletenessIT` bez zmian (żadnej nowej trasy) — d585dbe

### Phase 3: Klucze poza logami i przyczyna 503

#### Automated

- [x] 3.1 Testy klientów LLM i transportu zielone: `./mvnw -B test -Dtest='pl.szczurradar.api.llm.**.*Test'` — 71ef627
- [x] 3.2 Test „klucza nie ma w łańcuchu” jest czerwony bez `cleanCause` (sprawdzenie celowym cofnięciem jednego miejsca) — 71ef627
- [x] 3.3 Pełny zestaw zielony, w tym `LlmOutageIT` i `LlmCheckControllerIT`: `./mvnw -B verify` — 71ef627

### Phase 4: Dokumenty i weryfikacja w PR env

#### Automated

- [x] 4.1 CI na PR zielone (wszystkie joby `ci.yml`, w tym `railway-config`) — f3842fd
- [x] 4.2 W PR env (agent): przebieg admina `2004-01-01`–`2004-01-01` (bez resztek w kolejce) kończy się `failed`, a log API ma linię wyniku wysyłki z kolejki z `RUN_NEEDS_ATTENTION`, id tego przebiegu i `DELIVERED` — f3842fd
- [x] 4.3 Dokumenty: `grep` po `OperatorAlertQueue` w `pipeline/CLAUDE.md` i `REPOSITORY_GUIDELINES.md` oraz po „S-18” w `prd.md` i `deploy-plan.md` znajduje nowe akapity — f3842fd

#### Manual

- [ ] 4.4 Wiadomość w Telegramie z checka `szczur-radar-pr-<N>/api-events` ma zdarzenie `RUN_NEEDS_ATTENTION`, id przebiegu i odnośnik `/admin/runs/<id>`, bez sekretów
- [ ] 4.5 Testowy check PR env usunięty (`checks.sh delete szczur-radar-pr-<N>`)
