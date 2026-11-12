# S-31: ręczne weryfikacje PR env jako scenariusze w CI — plan implementacji

## Overview

Ręczne kroki weryfikacji PR env z wycinków S-13, S-17, S-18, S-20 i S-27 (oraz nocna ścieżka S-34) mają chodzić w jobie `backend` jako scenariusze: przebieg startuje przez HTTP, naprawdę kończy się ocenionymi postami, a wynik jest czytany przez trasy API. Źródła i LLM są atrapami HTTP z prawdziwych odpowiedzi. To, czego CI nie zobaczy, trafia na nazwaną listę ręczną. S-31 to gwiazda przewodnia M-4 (`context/foundation/roadmap.md:170-182`, issue #141).

Ścieżki w planie: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `test/` = `szczur-radar-api/src/test/java/pl/szczurradar/api/`, `fix/` = `szczur-radar-api/src/test/resources/fixtures/`. Polecenia `./mvnw` biegną w `szczur-radar-api/`.

## Current State Analysis

Pełny obraz jest w `context/changes/pr-env-scenarios-in-ci/research.md`. Najważniejsze:

- **Brakuje łańcucha „HTTP → przebieg się kończy → odczyt przez API”.**
  - `PipelineRunIT` doprowadza przebiegi do końca, ale startuje je przez `PipelineRunService.startRange` i sprawdza SQL-em (`test/pipeline/PipelineRunIT.java:120-124`).
  - `PipelineRunControllerIT` i `InternalPipelineRunIT` startują przez HTTP, ale ich Wykop zwraca pusty dzień, więc zakres kończy się `failed` na `fetch` (`test/pipeline/api/PipelineRunControllerIT.java:88-103`, `test/pipeline/api/InternalPipelineRunIT.java:125-139`).
  - `missing-days`, strona przebiegu, feed i podsumowanie nocy są sprawdzane na wierszach zasianych SQL-em.
- **Atrapy źródeł są gotowe.** `test/sources/LocalApiServer.java` z prawdziwymi klientami i `HttpRetry`; fixtury z prawdziwych odpowiedzi:
  - Wykop: 12 wpisów + 4 znaleziska z 2026-10-01;
  - r/inwestowanie: 14 postów z 2026-10-27;
  - r/stocks: 25 postów, z czego 19 usuniętych.
- **Atrapa LLM z prawdziwych odpowiedzi nie istnieje dla dni fixtur.**
  - `test/llm/ScriptedProviderServer.java` odpowiada z kolejki, więc przy 4 równoległych wywołaniach nie dopasuje odpowiedzi do posta.
  - Nagrania (`fix/llm/recordings/`) trzymają tylko tekst modelu i obejmują 7 z 33 postów fixtur idących do modelu.
- **Alerty.** `NTFY_URL` przyjmuje tylko `https` (`api/alerts/AlertSettings.java:58`), więc lokalny serwer nie wejdzie przez ustawienie. Kolejka wysyła 2 zdarzenia na minutę (`api/alerts/OperatorAlertQueue.java:56-67`). Żaden test nie łączy przebiegu z prawdziwą kolejką i prawdziwym `NtfyAlerts`.
- **Czas CI.** `backend` trwa 190–225 s i nie ma `timeout-minutes` (`.github/workflows/ci.yml:20-61`).
- **Lista ręczna** w `context/foundation/verification-scope.md:30-35` ma 4 pozycje. Wpis o Telegramie jest od S-34 prawdziwy tylko dla cronów.

## Desired End State

- `./mvnw -B verify` uruchamia klasę `PrEnvScenariosIT` z 8 scenariuszami w jednym kontekście Springa. Każdy startuje przebieg przez HTTP (trasa admina albo crona), czeka na jego koniec i sprawdza wynik przez trasy API oraz odebrane wiadomości ntfy.
- LLM w scenariuszach to prawdziwy klient OpenRoutera na lokalnym serwerze. Serwer odpowiada pełnym, nagranym ciałem odpowiedzi dostawcy dla każdego z 33 postów fixtur idących do modelu (model produkcji, `openai/gpt-6-luna`).
- Job `backend` ma `timeout-minutes: 15`, a scenariusze dokładają do niego najwyżej 60 s.
- `verification-scope.md` ma listę ręczną zgodną z tym, co naprawdę zostaje ręczne. `REPOSITORY_GUIDELINES.md` ma regułę: krok PR env, który da się odtworzyć na atrapach, powstaje jako scenariusz.
- Cztery celowe zepsucia kodu produkcyjnego czerwienią scenariusze (dowód, że łapią błędy).

Weryfikacja: zielone CI na PR z `PrEnvScenariosIT` w logu Failsafe, pomiar czasu i tabela zepsuć w sekcji S-31 w `context/deployment/deploy-plan.md`.

### Key Discoveries:

- **Wiadomość do modelu identyfikuje post treścią.** To `"Oceń poniższy AssessmentInputV1 …\n"` + zwarty JSON wejścia, bez id i adresu (`api/agents/assessor/prompt/AssessmentPrompt.java:45,58-60`). Wejście jest deterministyczne, bo powstaje z surowego payloadu (`api/pipeline/assess/AssessmentInputs.java:32-38`). Wszystkie 36 wejść fixtur jest różnych.
- **Prefiltr odcina 3 wpisy Wykopu** (id 1, 4, 8; `api/agents/assessor/Prefilter.java:27-38`). Do modelu idą 33 posty: 13 z Wykopu, 14 z r/inwestowanie, 6 z r/stocks.
- **Check LLM idzie tą samą drogą HTTP.** Jest wysyłany raz na krok oceny, z wiadomością `Return the JSON object {"ok": true}.`; odpowiedź nie jest parsowana (`api/llm/LlmClient.java:27-30,55-57`, `api/pipeline/assess/ConcurrentAssessmentDispatcher.java:189-208`).
- **Szew klienta dostawcy już jest:** `LlmClientFactory.create(props, baseUrl, sleeper)` (`api/llm/LlmClientFactory.java:32-41`), użyty z `@TestBean LlmClient` w `test/pipeline/LlmOutageIT.java:76-94`.
- **Kolejka alertów ma pakietowy konstruktor** `(OperatorAlerts, InstantSource, Sleeper, Duration)` (`api/alerts/OperatorAlertQueue.java:154`), a `NtfyAlerts` pakietowy konstruktor z `URI` (`api/alerts/NtfyAlerts.java:66`). Wzorzec czasu wirtualnego: `test/alerts/OperatorAlertQueueTest.java:44-51`.
- **Nic nie filtruje postów po dacie**, a pokrycie dni i świeżość liczą się z zakresu przebiegu (`api/sources/wykop/WykopRepository.java:166-192`, `api/sources/reddit/RedditRepository.java:142-191`, `api/pipeline/run/PipelineRunRepository.java:83-118`). Fixtury z października działają dla dowolnego zakresu.
- **Zakres z resztkami kolejkuje najpierw RETRY:** `POST /api/admin/pipeline-runs` zwraca `[retry, range]` (`api/pipeline/PipelineRunService.java:180-187`).
- **Status końcowy nie znaczy, że przebieg jest zamknięty.** Kolejność na wątku przebiegu: zamknięcie ostatniego kroku → status (`api/pipeline/flow/RunSequence.java:191-193`) → `notifications.runEnded` → `own.remove` (`api/pipeline/PipelineRunService.java:379-390`). Publiczny `OwnRuns.of(service)` (`test/pipeline/OwnRuns.java:11-20`) mówi, kiedy instancja oddała przebieg.
- **Feed pokazuje tylko posty `assessed`** (`api/posts/PostRepository.java:123-130`).
- **Zegar.** Bean `Clock` steruje walidacją zakresu, zakresem nocy, oknem `missing-days` i datą podsumowania nocy. `created_at` przebiegu i `assessed_at` oceny idą z zegara JVM (`api/pipeline/run/PipelineRun.java:66-68`, `api/agents/assessor/Assessor.java:54-57`).

## What We're NOT Doing

- **Test czarnej skrzynki na obrazie Dockera.** Wymagałby ustawień adresów bazowych źródeł i LLM oraz obejścia reguły `https` dla `NTFY_URL`, czyli kodu produkcyjnego tylko dla testów. Zero zmian w `src/main` poza dokumentami `CLAUDE.md`.
- **Scenariusz przerwania przez deploy (S-17 krok 2).** Zostaje na liście ręcznej; logikę kryją `PipelineStartupRecoveryIT` i `PipelineRunIT` (decyzja właściciela).
- **Grep formatu logu i linii Embabel oraz pomiar czasu startu (S-17 kroki 3–4).** Embabel jest usunięty z zależności, format to domyślny format Spring Boota, a czas startu na runnerze CI nie jest porównywalny z Railway.
- **Prawdziwe logowanie w scenariuszach.** Sesję admina daje post-procesor `user(...)`, jak w `PipelineRunControllerIT`; logowanie sprawdza `AuthIT`.
- **Przepisywanie istniejących klas IT na wspólny `LocalWykop`.** Sześć klas zostaje ze swoimi kopiami; migracja to osobna zmiana.
- **Asercje na linie `quality 7d` w podsumowaniu nocy.** Zależą od zegara JVM; kryją je `NightSummaryIT` i `NightSummaryTest`.
- **Playwright i oględziny UI.** To M-5 (`roadmap.md:85`).
- **Scenariusze dla S-32, S-35 i S-38.** Krok PR env z S-32 („zwykły przebieg bez zmian”) pokrywa scenariusz zwykłego przebiegu; S-35 i S-38 nie miały kroków PR env.
- **Strażnik wersji promptu w nagraniach.** Dopasowanie idzie po wiadomości użytkownika, która nie zawiera promptu systemowego. Ponowne nagranie jest potrzebne po zmianie kontraktu oceny albo fixtur źródeł, nie po zmianie promptu.

## Implementation Approach

Jedna klasa najwyższego poziomu = jeden kontekst Springa i jeden kontener Postgresa. Wszystkie atrapy są współdzielonymi, publicznymi konfiguracjami testowymi ze statycznymi przełącznikami, na wzór `LocalReddit`. Scenariusze są izolowane tak jak w `PipelineRunIT`: przed każdym czyszczenie tabel i przełączników, po każdym czekanie na ciszę; kolejność metod jest losowa z `it.seed`.

Atrapa LLM powstaje w dwóch krokach. Najpierw jednorazowe narzędzie (tag `measurement`, poza CI) nagrywa pełne ciała odpowiedzi OpenRoutera dla 33 postów. Potem lokalny serwer odpowiada tymi ciałami według wiadomości użytkownika z żądania. Oczekiwane liczniki scenariuszy (ile ocen udanych, ile odrzuconych) wynikają z nagrań i są wpisane do testów po nagraniu.

Praca idzie na istniejącej gałęzi `feature/pr-env-scenarios-in-ci`, z jednym PR i komentarzem w issue #141 (`REPOSITORY_GUIDELINES.md`, „Workflow”).

## Critical Implementation Details

- **Kolejność czekania po końcu przebiegu.** Scenariusz czeka na status końcowy z `GET /api/admin/pipeline-runs/{id}`, a potem na `OwnRuns.of(service).isEmpty()`. Dopiero wtedy powiadomienia są zapisane, a `RUN_NEEDS_ATTENTION` zgłoszone albo świadomie pominięte. Asercja „brak alertu” jest wiarygodna tylko po tym drugim czekaniu.
- **Nagrania dają zawsze tekst modelu.** Żadne nie jest błędem wywołania (reguła zapisu w Fazie 1 #2), więc wyłącznik S-32 nie zadziała w scenariuszach, a każdy tekst modelu zeruje jego serię.
- **Nagrana odpowiedź odrzucona przez walidator powtarza się przy każdej próbie.** Taki post jest `failed`, kolejny start zakresu kolejkuje najpierw RETRY, a trzecia próba daje `dead` i powód `dead_posts`. Scenariusze z kilkoma przebiegami (powtórka zakresu, dni bez danych) czekają na każdy przebieg z odpowiedzi startu i liczą oczekiwania z tabeli wyników nagrań.
- **Zgłoszony alert to jeszcze nie dostarczony.** Linię `Operator alert <EVENT> (run <id>) taken` loguje wątek wołającego w `raise` (`api/alerts/OperatorAlertQueue.java:192`), więc dla `RUN_NEEDS_ATTENTION` przed `own.remove`. Żądanie do ntfy i linia `reported: …` idą później, na wątku `operator-alerts` (`:253`), a kolejka nie pokazuje, czy jest bezczynna. Dlatego:
  - asercje na ntfy filtrują po tytule i id przebiegu;
  - `reported: DELIVERED` sprawdza się czekaniem na log;
  - „brak alertu” sprawdza się na braku linii `taken` po oddaniu przebiegu i na liście ntfy po ciszy.
- **Limity kolejki alertów działają w czasie wirtualnym.** Testowa kolejka dostaje `InstantSource`, który przesuwa jej `Sleeper`, jak w `OperatorAlertQueueTest`. Z prawdziwym zegarem trzecie zdarzenie w klasie czekałoby ok. 60 s i przekroczyło budżet czasu CI.
- **Klucz dostawcy tylko z powłoki.** Narzędzie nagrywające czyta go ze środowiska (`source ~/.config/szczur-radar/openrouter.env`). Klucz nie trafia do pliku, logu ani nagrania. Faza 1 wymaga tego pliku, więc idzie interaktywnie; bez niego implementujący zatrzymuje się i prosi właściciela o uruchomienie polecenia.

## Faza 1: Nagrania i atrapa dostawcy LLM

### Overview

Powstają nagrane odpowiedzi dostawcy dla dni fixtur, serwer, który odpowiada nimi według treści żądania, i test pilnujący kompletności nagrań. `LocalReddit` uczy się odpowiadać według subreddita.

### Changes Required:

#### 1. Indeks postów dni fixtur

**File**: `test/scenario/RecordedDays.java` (nowy, publiczny)

**Intent**: Jedno miejsce, które wie, które posty fixtur idą do modelu i jaka wiadomość użytkownika im odpowiada. Używają go serwer atrapy, narzędzie nagrywające i test kompletności.

**Contract**: Czyta `fix/wykop/search-entries-page.json`, `fix/wykop/search-links-page.json`, `fix/reddit/search-posts-inwestowanie.json` i `fix/reddit/search-posts-stocks.json`. Każdy element mapuje produkcyjnym mapperem (jak `test/pipeline/PipelineRunIT.java:931-938`), z produkcyjną regułą pominięcia usuniętych postów Reddita i z `Prefilter.isAssessable`. Dla każdego posta idącego do modelu daje: nazwę pliku nagrania (`wykop-entry-<id>.json`, `wykop-link-<id>.json`, `reddit-<id>.json`) i `AssessmentPrompt.userMessage(input)`. Oczekiwana liczba: 33.

#### 2. Narzędzie nagrywające

**File**: `test/measurement/ScenarioDaysRecorder.java` (nowy, `@Tag("measurement")`)

**Intent**: Jednorazowo nagrać prawdziwe odpowiedzi modelu produkcji dla 33 postów, żeby scenariusze nie używały odpowiedzi pisanych ręcznie.

**Contract**: Dla każdego posta z `RecordedDays` bez nagrania wysyła do OpenRoutera żądanie, które buduje produkcyjny klient (ustawienia konfiguracji `luna` z `fix/llm/models.json`: `openrouter`, `openai/gpt-6-luna`), i zapisuje ciało odpowiedzi HTTP 200 bez zmian do `fix/llm/scenario-days/<nazwa>.json`. Zapisuje tylko ciało, z którego produkcyjny klient OpenRoutera zwraca tekst modelu; tekst odrzucony przez walidator też jest dobrym nagraniem. Nie zapisuje odpowiedzi innej niż 200, ciała z kluczem `error` ani ciała, które klient zamienia w błąd wywołania albo awarię: odmowy, `content_filter`, braku tekstu, braku `choices`. Takie wywołanie ponawia kilka razy. Gdy odmowa się powtarza, kończy pracę z listą tych postów, a decyzję podejmuje właściciel. Powód: błąd wywołania liczy się do wyłącznika S-32 (`REFUSAL_STREAK` = 3 w kolejności końca czterech równoległych wywołań), więc trzy takie nagrania zatrzymywałyby przebieg albo nie, zależnie od kolejności. Do tego ma własny limit resztek, inny niż ścieżka „failed → RETRY → dead”. Wznawia pracę, pomijając istniejące pliki. Na końcu wypisuje tabelę: plik → wynik walidacji kontraktu (`ok` albo klasa błędu). Uruchomienie: `( source ~/.config/szczur-radar/openrouter.env && ./mvnw -Pmeasurement test -Dtest=ScenarioDaysRecorder )`. Sposób przechwycenia surowego ciała (lokalny serwer przekazujący dalej albo przechwycenie żądania klienta i jego ponowne wysłanie `java.net.http`) wybiera implementujący.

#### 3. Nagrania

**File**: `fix/llm/scenario-days/` (nowy katalog: 33 pliki `*.json` + `README.md`)

**Intent**: Prawdziwe payloady dostawcy jako fixtura, zgodnie z regułą fixtur usług zewnętrznych.

**Contract**: `README.md` podaje datę nagrania, model, ustawienia, polecenie, tabelę wyników walidacji per plik, regułę zapisu (tylko ciało, z którego klient zwraca tekst modelu) i regułę ponownego nagrania (zmiana kontraktu oceny albo fixtur źródeł). Katalog nie ma nazwy konfiguracji z `models.json`, więc `RecordingsReplayTest` i `GoldRecorder` go nie czytają.

#### 4. Serwer dostawcy odpowiadający według treści

**File**: `test/scenario/` (nowa funkcja odpowiedzi), `test/sources/LocalApiServer.java` (tylko Javadoc)

**Intent**: Serwer ma odpowiadać na żądanie nagraniem jego posta, niezależnie od kolejności równoległych wywołań.

**Contract**: Serwerem nagrań jest `LocalApiServer`: jest publiczny, odpowiada przez `Function<Request, Reply>` i zapisuje żądania z nagłówkami i ciałem. Jego Javadoc mówi „an external API” zamiast „a source's API”. `ScriptedProviderServer` (kolejka FIFO, czterech użytkowników) zostaje bez zmian. W `test/scenario/` powstaje funkcja odpowiedzi z `RecordedDays`:
- wiadomość checku → `fix/openrouter/chat-completion.json`;
- wiadomość znanego posta → jego nagranie;
- każda inna → HTTP 400 i wpis na publicznej liście niedopasowanych żądań.

#### 5. Test kompletności nagrań

**File**: `test/scenario/RecordedDaysTest.java` (nowy, bez Springa)

**Intent**: Zmiana fixtury źródła albo mappera bez ponownego nagrania ma czerwienić test z jasnym komunikatem, a nie scenariusz z niejasnym.

**Contract**: Sprawdza, że:
- każdy z 33 postów ma plik, a katalog nie ma pliku bez posta;
- prawdziwy klient OpenRoutera na serwerze z punktu 4 dostaje dla każdej wiadomości niepusty tekst;
- nieznana wiadomość daje błąd i wpis na liście niedopasowanych.

#### 6. Reddit według subreddita

**File**: `test/sources/reddit/LocalReddit.java`

**Intent**: Scenariusz konfiguruje dwa subreddity z różnymi stronami, żeby pokryć pominięcie usuniętych postów r/stocks.

**Contract**: Dodatkowa metoda przypisująca fixturę do subreddita (odczyt parametru `subreddit` zapytania); bez przypisania zostaje dzisiejsze zachowanie (`DEFAULT_PAGE` dla każdego wyszukiwania). `reset()` czyści przypisania.

#### 7. Roadmapa w rendererze

**File**: `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Status S-31 w roadmapie zmienił się przy planowaniu, a wytyczne wymagają zgodnego SVG.

**Contract**: Status `S-31` w danych renderera równy statusowi w `context/foundation/roadmap.md`; SVG wygenerowany przez `python3 tools/render_roadmap_svg.py`.

#### 8. Dokumenty narzędzia i fixtur

**File**: `test/measurement/README.md`, `test/measurement/CLAUDE.md`, `api/llm/CLAUDE.md`

**Intent**: Następna osoba ma wiedzieć, skąd są nagrania i jak je odnowić.

**Contract**: Sekcja o `ScenarioDaysRecorder` w README pomiarów (polecenie, katalog, reguła odnowienia), wzmianka w `CLAUDE.md` pomiarów, zdanie w `llm/CLAUDE.md` obok opisu `ScriptedProviderServer` (`:20-22`, `:135`), że atrapą dostawcy w scenariuszach jest `LocalApiServer` z nagraniami z `fix/llm/scenario-days/`.

### Success Criteria:

#### Automated Verification:

- Test kompletności nagrań jest zielony: `./mvnw -B test -Dtest=RecordedDaysTest`
- Katalog nagrań ma 33 pliki JSON: `ls src/test/resources/fixtures/llm/scenario-days/*.json | wc -l` daje 33
- Nagrania nie zawierają klucza: `grep -rIlE "sk-or-|Bearer " src/test/resources/fixtures/llm/scenario-days` nic nie zwraca
- Wszystkie testy jednostkowe są zielone, w tym `RecordingsReplayTest` i testy klientów dostawców: `./mvnw -B test`
- Klasy używające `LocalReddit` są zielone: `./mvnw -B verify -Dit.test='PipelineRunIT,RunWatchdogIT,MissingDaysIT'`
- SVG roadmapy jest aktualny: `python3 tools/render_roadmap_svg.py && git diff --exit-code docs/visuals/roadmap.svg` po commicie fazy
- PR istnieje i ma `Refs #141`: `gh pr view --json body --jq .body | grep -q "Refs #141"`

---

## Faza 2: Kontekst scenariuszy i zwykły przebieg

### Overview

Powstają wspólne atrapy kontekstu (Wykop, ntfy z prawdziwą kolejką, zegar) i klasa `PrEnvScenariosIT` z dwoma pierwszymi scenariuszami: zakres dwóch źródeł od startu HTTP do feedu oraz powtórka zakresu.

### Changes Required:

#### 1. Wspólny Wykop

**File**: `test/sources/wykop/LocalWykop.java` (nowy, publiczny `@TestConfiguration`)

**Intent**: Scenariusze potrzebują Wykopu z pełnym dniem fixtury i przełącznikami awarii, bez siódmej zagnieżdżonej kopii.

**Contract**: Na wzór `LocalReddit`: statyczny `LocalWykopServer`, bean `WykopRepository` z produkcyjnym klientem bez oczekiwań (jak `test/pipeline/PipelineRunIT.java:1272-1284`), przełączniki `down()`, `empty()`, `reset()`. Domyślnie `/search/entries` zwraca fixturę wpisów z `pagination.total` = 12, a `/search/links` fixturę znalezisk.

#### 2. Lokalny ntfy z prawdziwą kolejką

**File**: `test/alerts/LocalNtfy.java` (nowy, publiczny `@TestConfiguration`), `test/alerts/LocalNtfyServer.java`

**Intent**: Zdarzenie ma przejść prawdziwą drogę: `OperatorAlertQueue` → `NtfyAlerts` → żądanie HTTP, bez czekania na limit minutowy.

**Contract**: Konfiguracja w pakiecie `alerts` daje kontekstowi `OperatorAlerts` = `NtfyAlerts` na adresie lokalnego serwera (pakietowy konstruktor, z tokenem testowym) oraz `OperatorAlertQueue` z pakietowego konstruktora z zegarem wirtualnym przesuwanym przez `Sleeper`. Każdy punkt wstrzyknięcia kolejki dostaje kolejkę testową (`PipelineRunService`, `RunNotifications`, `InternalPipelineRunController`, `RunWatchdog`, `PipelineStartupRecovery`, `ConcurrentAssessmentDispatcher`). Nadpisywanie definicji beanów jest wyłączone, więc beany testowe mają inne nazwy niż `operatorAlerts` i `operatorAlertQueue`. Są `@Primary`, więc trafiają do każdego punktu wstrzyknięcia, także przez `ObjectProvider` (wzorzec: `@Primary Clock` w `test/pipeline/NightSummaryIT.java:250`). Produkcyjna kolejka zostaje w kontekście nieużywana. Publiczne API: lista odebranych wiadomości (tytuł, priorytet, nagłówek autoryzacji, treść), czyszczenie listy, czekanie z limitem czasu na wiadomość o danym tytule i id przebiegu oraz liczba zakończonych zgłoszeń. Tę liczbę daje dekorator `OperatorAlerts` wokół `NtfyAlerts`, który liczy powroty z `report`; to jedyny sygnał, że kolejka skończyła wysyłać. Kontekst ustawia `NTFY_URL=` i `NTFY_TOKEN=` puste, jak `test/alerts/AlertsConfigurationTest.java:24-25`.
#### 3. Wspólny zegar testowy

**File**: `test/config/MutableClock.java` (nowy, publiczny), `test/pipeline/api/InternalPipelineRunIT.java`

**Intent**: Scenariusze potrzebują stałego „dziś” dla zakresu nocy i okna `missing-days`; klasa już istnieje jako zagnieżdżona.

**Contract**: `MutableClock` z `InternalPipelineRunIT.java:654-681` przeniesiony bez zmiany zachowania; `InternalPipelineRunIT` używa wspólnej klasy.

#### 4. Klasa scenariuszy

**File**: `test/scenario/PrEnvScenariosIT.java` (nowy), `test/pipeline/RandomOrderSeed.java`

**Intent**: Jeden kontekst dla wszystkich scenariuszy, z izolacją i czekaniem według wzorca `PipelineRunIT`.

**Contract**:
- **Kontekst:** `@SpringBootTest` z Flywayem, tokenem crona (`PipelineTriggerToken.NAME`) i pustymi `NTFY_*`; `@AutoConfigureMockMvc`; importy `TestcontainersConfiguration`, `LocalWykop`, `LocalReddit`, `LocalNtfy`, zegar `@Primary` ustawiony na stałą chwilę; `@TestBean LlmClient` = klient OpenRoutera z `LlmClientFactory.create` na serwerze nagrań.
- **Klasa:** `@Timeout` 5 min, losowa kolejność metod z `it.seed` (`RandomOrderSeed` staje się publiczny), `OutputCaptureExtension`.
- **Przed scenariuszem:** brak aktywnych przebiegów, `TRUNCATE` tabel przebiegu i postów jak `PipelineRunIT.java:254-270`, reset przełączników źródeł, Reddit z subredditami `inwestowanie` i `stocks`, wyczyszczone wiadomości ntfy i lista żądań LLM. Konto admina ma `login_enabled`, bo bez powiadomienia admina `RunNotifications` nie zgłasza `RUN_NEEDS_ATTENTION` (`api/notifications/RunNotifications.java:114-119`). Tabela `users` zostaje poza `TRUNCATE`, bo trzyma FK `pipeline_runs.requested_by` i konto `SCHEDULER` dla `/daily`.
- **Po scenariuszu:** czekanie na ciszę, potem asercja, że lista niedopasowanych żądań LLM jest pusta. Cisza to:
  - brak własnych przebiegów;
  - bezczynna pula LLM;
  - kolejka alertów, która skończyła: liczba zakończonych zgłoszeń `LocalNtfy` równa liczbie linii `Operator alert … taken` w przechwyconym wyjściu scenariusza.
- **Pomocniki:** start admina przez `POST /api/admin/pipeline-runs` z `user(<uuid>).roles(ADMIN)`; start crona przez `POST /internal/pipeline-runs/daily` z `Authorization: Bearer`; czekanie na każdy przebieg z odpowiedzi startu według „Critical Implementation Details”.

#### 5. Scenariusz A: zakres dwóch źródeł od startu do feedu

**File**: `test/scenario/PrEnvScenariosIT.java`

**Intent**: Odtwarza S-17 krok 1, S-20 krok 5.2, S-27 kroki 5.4–5.5 i krok PR env z S-32.

**Contract**: Admin startuje zakres jednego dnia bez wskazania źródeł. Asercje przez API:
- **Przebieg:** status `succeeded` albo `partial` zgodnie z tabelą wyników nagrań; `fetched` 36, `newPosts` 36, `skipped` 0; `attentionReasons` puste; `error` null.
- **Kroki:** `fetch`, `store`, `select`, `assess`, `summarize` w statusie `succeeded`.
- **Źródła:** `sourceResults` = `wykop` `succeeded` 16/0 i `reddit` `succeeded` 20/0, w tej kolejności.
- **Strażnik deployu:** `GET /api/deploy-safety` daje `{"safe":true,"reason":null}`.
- **Feed:** zawiera post Reddita z `source` `reddit`, `channel` `inwestowanie` i `webUrl` zaczynającym się od `https://www.reddit.com/r/inwestowanie/comments/`; jego `/raw` ma klucz `subreddit`.
- **Prawdziwa ocena:** ocena jednego posta Wykopu w feedzie ma cele z jego nagranej odpowiedzi.
- **Log:** ma linię `Reddit r/stocks: 6 post(s) in range, 19 removed or deleted left out, 0 of unexpected shape` (`api/sources/reddit/RedditRepository.java:126`) i nie ma linii ERROR.

#### 6. Scenariusz B: ten sam zakres drugi raz

**File**: `test/scenario/PrEnvScenariosIT.java`

**Intent**: Odtwarza S-27 krok 5.6 (bez duplikatów).

**Contract**: Po scenariuszu A w tym samym teście drugi start tego samego zakresu. Przebieg zakresu ma `newPosts` 0 i oba źródła `succeeded`; liczba postów w bazie się nie zmienia. Gdy nagrania mają odrzucone odpowiedzi, odpowiedź startu to `[retry, range]` i scenariusz czeka na oba.

### Success Criteria:

#### Automated Verification:

- Klasa scenariuszy jest zielona z dwoma scenariuszami: `./mvnw -B verify -Dtest=RecordedDaysTest -Dit.test=PrEnvScenariosIT`
- `InternalPipelineRunIT` jest zielony po przeniesieniu zegara: `./mvnw -B verify -Dtest=RecordedDaysTest -Dit.test=InternalPipelineRunIT`
- Pełny zestaw jest zielony: `./mvnw -B verify`
- Brak zmian w kodzie produkcyjnym poza dokumentami: `git diff --name-only origin/main -- szczur-radar-api/src/main | grep -v "CLAUDE.md$"` nic nie zwraca

---

## Faza 3: Scenariusze sygnałów, dni bez danych i nocy

### Overview

Sześć scenariuszy w tej samej klasie: awaria i cisza źródła, alert w ntfy, reguła crona, dni bez danych i noc z podsumowaniem.

### Changes Required:

#### 1. Scenariusz C: źródło padło w przebiegu admina

**File**: `test/scenario/PrEnvScenariosIT.java`

**Intent**: Główne zachowanie S-20, którego PR env nie mógł sprawdzić, z sygnałami S-18.

**Contract**: `LocalReddit.down()`, start admina. Asercje:
- status `partial`; `attentionReasons` zaczyna się od `source_failed`; `error` zaczyna się od `reddit failed: RedditApiException`;
- `sourceResults`: `wykop` `succeeded` 16/0, `reddit` `failed` z błędem;
- posty Wykopu są w feedzie;
- `GET /api/admin/notifications` ma powiadomienie tego przebiegu;
- lokalny ntfy dostał dla id tego przebiegu dokładnie jedną wiadomość z tytułem `Szczur Radar RUN_NEEDS_ATTENTION`, której treść zawiera `(range, admin) ended partial`, `error: reddit failed` i `/admin/runs/<id>`.

#### 2. Scenariusz D: puste źródło w przebiegu crona

**File**: `test/scenario/PrEnvScenariosIT.java`

**Intent**: S-20: cisza źródła obok postów drugiego daje `partial`, cron kończy się kodem 0, a API zgłasza zdarzenie także dla przebiegu crona.

**Contract**: `LocalReddit.empty()`, start przez `/internal/pipeline-runs/daily`. Asercje:
- `GET /internal/pipeline-runs/{id}` ma `status` `partial`;
- strona przebiegu admina ma `scheduled` true, `source_empty` w `attentionReasons` i `error` zawierający `reddit gave no post to store (fetched 0, skipped 0)`;
- zakres przebiegu to D-2…D-1 zegara testowego;
- ntfy dostał `RUN_NEEDS_ATTENTION` z `(range, cron) ended partial`.

#### 3. Scenariusz E: żadne źródło nie dało posta, przebieg admina

**File**: `test/scenario/PrEnvScenariosIT.java`

**Intent**: Odtwarza kroki 3–5 weryfikacji S-18 (przebieg 2004-01-01) i regułę S-08/S-20 o pustym przebiegu.

**Contract**: `LocalWykop.empty()` i `LocalReddit.empty()`, start admina. Asercje:
- status `failed`; `error` zawiera `NoPostsFetchedException` i nazywa oba źródła; krok `fetch` `failed`; brak kroków `store`, `select`, `assess`;
- powiadomienie admina istnieje;
- ntfy dostał dla id tego przebiegu jedną wiadomość `RUN_NEEDS_ATTENTION` z `(range, admin) ended failed: dead 0, errors 0, skipped 0; error:` i `/admin/runs/<id>`;
- treść wiadomości nie zawiera tokenu testowego ani adresu tematu;
- log ma `Operator alert RUN_NEEDS_ATTENTION (run <id>) taken`, a po czekaniu na log także `Operator alert RUN_NEEDS_ATTENTION (run <id>) reported: DELIVERED`.

#### 4. Scenariusz F: nieudany przebieg crona zgłasza tylko cron

**File**: `test/scenario/PrEnvScenariosIT.java`

**Intent**: Reguła S-18: przebieg crona zakończony `failed` nie daje zdarzenia z API.

**Contract**: Oba źródła puste, start przez `/internal/pipeline-runs/daily`. Asercje po oddaniu przebiegu przez instancję:
- `GET /internal/pipeline-runs/{id}` ma `status` `failed`;
- powiadomienie admina istnieje;
- log nie ma `Operator alert RUN_NEEDS_ATTENTION (run <id>) taken`, a po ciszy kolejki ntfy nie ma żadnej wiadomości dla tego przebiegu.

#### 5. Scenariusz G: dzień bez danych i jego wypełnienie

**File**: `test/scenario/PrEnvScenariosIT.java`

**Intent**: Odtwarza krok 3.2 weryfikacji S-13 i zmianę S-20 w pokryciu dni (liczy się wynik źródła, nie prośba).

**Contract**: Zegar na stałej chwili, D = jej dzień warszawski. Kroki i asercje na `GET /api/admin/pipeline-runs/missing-days`:
1. Bez przebiegów: `days` puste.
2. Po przebiegu D-3 z oboma źródłami: `days` = D-2 z `sources` `[wykop, reddit]`.
3. Po przebiegu D-2 z `LocalReddit.down()`: `days` = D-2 z `sources` `[reddit]`.
4. Po przebiegu D-2 z działającym Redditem: `days` puste.

Okno w odpowiedzi to D-15…D-2. Każdy start czeka na wszystkie przebiegi z odpowiedzi (RETRY przed zakresem).

#### 6. Scenariusz H: noc crona z podsumowaniem

**File**: `test/scenario/PrEnvScenariosIT.java`

**Intent**: Odtwarza weryfikację PR env z S-34: cron → przebieg → podsumowanie nocy → wiadomość ntfy.

**Contract**: Start przez `/internal/pipeline-runs/daily`, po końcu przebiegów `POST /internal/pipeline-runs/nightly-summary` z ich id. Asercje:
- odpowiedź 200 z `text`, który ma linię `wykop:` i linię `reddit:` z liczbami pobranych zgodnymi z przebiegiem (16 i 20);
- ntfy dostał wiadomość z tytułem `Szczur Radar NIGHT_SUMMARY`, której treść zawiera ten sam tekst;
- dla przebiegu, który nie wymaga uwagi albo którego jedynym powodem jest `dead_posts`, log nie ma linii `Operator alert RUN_NEEDS_ATTENTION (run <id>) taken`, a po ciszy kolejki ntfy nie ma dla niego `RUN_NEEDS_ATTENTION`.

Linie `quality 7d` nie są sprawdzane.

#### 7. Opis klasy w dokumentach pakietów

**File**: `api/pipeline/CLAUDE.md`, `api/sources/CLAUDE.md`, `szczur-radar-api/README.md`

**Intent**: Dokumenty testów mają opisywać nową klasę i wspólne atrapy; przy okazji znika znany rozjazd.

**Contract**:
- `pipeline/CLAUDE.md`, „## Tests”: wpis `PrEnvScenariosIT` z listą scenariuszy A–H i ich wycinkami, w formacie pozostałych wpisów; klasa dopisana do listy `@Timeout` (`:543-545`); poprawka `:657` („a mocked `OperatorAlerts`” → `OperatorAlertQueue`).
- `sources/CLAUDE.md`, „## Tests” (`:118-141`): `LocalWykop` i przypisanie strony do subreddita w `LocalReddit`.
- `README.md:283`: `it.seed` losuje kolejność metod także w `PrEnvScenariosIT`.

### Success Criteria:

#### Automated Verification:

- Klasa scenariuszy jest zielona z ośmioma testami: `./mvnw -B verify -Dtest=RecordedDaysTest -Dit.test=PrEnvScenariosIT` pokazuje `Tests run: 8, Failures: 0, Errors: 0` dla `PrEnvScenariosIT`
- Pełny zestaw jest zielony: `./mvnw -B verify`
- Dokument testów opisuje klasę: `grep -c "PrEnvScenariosIT" src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` daje co najmniej 1

---

## Faza 4: CI, lista ręczna i dowód

### Overview

Kontrola czasu CI, dokumenty (lista ręczna, reguła, runbook) i dowód, że scenariusze są stabilne i łapią błędy.

### Changes Required:

#### 1. Limit czasu joba `backend`

**File**: `.github/workflows/ci.yml`

**Intent**: Zawieszony test nie może trzymać runu 6 godzin; okno Wait for CI na Railway to 2 h.

**Contract**: `timeout-minutes: 15` w jobie `backend` (`ci.yml:20`). Żaden inny job się nie zmienia; brak filtrów ścieżek.

#### 2. Lista ręczna

**File**: `context/foundation/verification-scope.md`

**Intent**: Lista „Co zostanie ręczne” ma odpowiadać temu, co naprawdę zostaje ręczne po S-31, bo opiera się na niej „done when” M-4 (decyzja właściciela).

**Contract**: Sekcja „Co zostanie ręczne (CI tego nie zobaczy)” ma 8 pozycji:
1. Dostarczenie alertu na telefon: Telegram przez healthchecks.io dla cronów i `deploy-health`, aplikacja ntfy dla zdarzeń API (od S-34).
2. Przerwanie przebiegu przez deploy na Railway (SIGTERM, nakładanie się instancji) i zachowanie Wait for CI.
3. Prawdziwe API Wykopu, Arctic Shift i dostawcy LLM: dostępność, format, limity, jakość ocen modelu.
4. Harmonogram cronów.
5. Wygląd i teksty widoków w przeglądarce (do M-5, Playwright).
6. Pomiary na Railway: czas startu po wybudzeniu, czytelność logów.
7. Migracja na danych produkcji: pre-deploy `migrate`, backfill.
8. Zmienne i sekrety środowisk Railway: krok CI ustawiający wartości w PR env, zapieczętowane wartości produkcji.

Pod listą jedno zdanie: scenariusze S-31 pokrywają resztę kroków PR env z S-13, S-17, S-18, S-20, S-27 i S-34 (`PrEnvScenariosIT`). Pozostałe sekcje pliku bez zmian.

#### 3. Reguła i opis w wytycznych

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Plan następnego wycinka ma dopisać scenariusz zamiast kroku klikanego w PR env (decyzja właściciela).

**Contract**: W „Coding Style & Testing” akapit o `PrEnvScenariosIT`: co to jest, jego atrapy (`LocalWykop`, `LocalReddit`, serwer nagrań z `fix/llm/scenario-days/`, `LocalNtfy`, `MutableClock`), reguła ponownego nagrania oraz reguła: krok weryfikacji PR env, który da się odtworzyć na atrapach, wycinek dopisuje jako scenariusz w tej klasie, a w PR env zostają tylko pozycje listy z `verification-scope.md`. W „Deployment”, w punkcie o CI: `backend` ma limit 15 min.

#### 4. Sekcja S-31 w planie wdrożeń

**File**: `context/deployment/deploy-plan.md`

**Intent**: Operator ma jedno miejsce z mapą „krok PR env → scenariusz”, listą ręczną i sposobem odnowienia nagrań.

**Contract**: Nowa sekcja `## S-31: ręczne weryfikacje PR env jako scenariusze w CI (pr-env-scenarios-in-ci)` przed `## Phases`. Zawiera:
- linię statusu prawdziwą także po merge'u: `**Status:** delivered by PR #<N>; production verification and closure in issue #141.`;
- tabelę: scenariusz A–H → wycinek i krok PR env, który zastępuje;
- odnośnik do listy ręcznej;
- polecenie i regułę ponownego nagrania;
- tabelę celowych zepsuć z wynikiem;
- zmierzony czas CI przed i po;
- runbook po merge'u: CI na `main` zielone z `PrEnvScenariosIT`, komentarz i zamknięcie #141.

#### 5. Celowe zepsucia

**File**: brak trwałej zmiany (zepsucia w `api/` są cofane przed commitem)

**Intent**: Cel M-4 to testy, które łapią błędy. Każde zepsucie ma zaczerwienić co najmniej jeden scenariusz.

**Contract**: Cztery zepsucia, każde osobno, uruchomione z `-Dit.test=PrEnvScenariosIT`:

| # | Zepsucie | Oczekiwany czerwony scenariusz |
| --- | --- | --- |
| 1 | `RunKindConverter` zapisuje `name()` zamiast wartości (eksperyment X1 audytu) | każdy, który startuje przebieg |
| 2 | pokrycie dnia liczy źródło, o które przebieg prosił, zamiast wyniku `succeeded` (`PipelineRunRepository.findCoveringSince`) | G |
| 3 | `RunNotifications` zgłasza `RUN_NEEDS_ATTENTION` także dla przebiegu crona zakończonego `failed` | F |
| 4 | `InternalPipelineRunController.nightlySummary` nie zgłasza `NIGHT_SUMMARY` | H |

Wynik każdego (czerwone testy z nazwami) trafia do tabeli w sekcji S-31 `deploy-plan.md`. Po próbach `git status` nie pokazuje zmian w `szczur-radar-api/src/main`.

### Success Criteria:

#### Automated Verification:

- Scenariusze są stabilne przy pięciu ziarnach kolejności: `for s in 1 2 3 4 5; do ./mvnw -B -q verify -Dtest=RecordedDaysTest -Dit.test=PrEnvScenariosIT -Dit.seed=$s || break; done` kończy się kodem 0
- Pełny zestaw jest zielony w obu kolejnościach klas: `./mvnw -B verify -Dfailsafe.runOrder=alphabetical` i `./mvnw -B verify -Dfailsafe.runOrder=reversealphabetical`
- Każde z czterech celowych zepsuć czerwieni `PrEnvScenariosIT`, a po próbach `git status --porcelain szczur-radar-api/src/main` jest puste
- Job `backend` ma limit: `grep -A12 "^  backend:" .github/workflows/ci.yml | grep -q "timeout-minutes: 15"`
- CI na PR jest zielone: `gh pr checks` pokazuje wszystkie joby `pass`
- Czas scenariuszy mieści się w budżecie: w logu joba `backend` zielonego runu PR linia `Tests run: 8 … in pl.szczurradar.api.scenario.PrEnvScenariosIT` ma `Time elapsed` ≤ 60 s. Czas joba przed i po (`gh run view <id> --json jobs`) trafia do sekcji S-31 w `deploy-plan.md` jako pomiar, nie jako próg, bo rozrzut runnera (190–224 s w 6 runach) zjada ponad połowę budżetu.
- Dokumenty mają nowe treści: `grep -c "PrEnvScenariosIT" REPOSITORY_GUIDELINES.md context/deployment/deploy-plan.md context/foundation/verification-scope.md` daje co najmniej 1 dla każdego pliku

#### Manual Verification:

- Właściciel akceptuje treść listy ręcznej w `verification-scope.md` i reguły w `REPOSITORY_GUIDELINES.md` (zmieniają kryterium „done when” M-4 i sposób weryfikacji kolejnych wycinków)

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na akceptację właściciela. Potem `/10x-impl-review` (z jawnym `phase 4`), `/10x-archive` na gałęzi i przekazanie merge'a człowiekowi.

---

## Testing Strategy

### Unit Tests:

- `RecordedDaysTest`: kompletność nagrań (33 posty, brak osieroconych plików), odpowiedź serwera przez prawdziwego klienta OpenRoutera, nieznane żądanie.
- Istniejące testy klientów dostawców (`OpenRouterClientTest`, `DeepSeekClientTest`, `GeminiClientTest`) i `RecordingsReplayTest` pozostają zielone bez zmian.

### Integration Tests:

- `PrEnvScenariosIT`, 8 scenariuszy:
  - A — zakres dwóch źródeł od startu HTTP do feedu i `/raw` (S-17, S-20, S-27, S-32);
  - B — powtórka zakresu bez duplikatów (S-27);
  - C — źródło padło, `partial`, dzwonek i `RUN_NEEDS_ATTENTION` w ntfy (S-20, S-18);
  - D — puste źródło w przebiegu crona, `partial` (S-20);
  - E — żadne źródło nie dało posta, `failed` i alert (S-18, S-08);
  - F — nieudany przebieg crona bez alertu z API (S-18);
  - G — dzień bez danych i jego wypełnienie, także przy padniętym źródle (S-13, S-20);
  - H — noc crona z podsumowaniem w ntfy (S-34).
- `InternalPipelineRunIT` po przeniesieniu zegara; `PipelineRunIT`, `RunWatchdogIT`, `MissingDaysIT` po zmianie `LocalReddit`.

### Manual Testing Steps:

1. Właściciel czyta listę ręczną w `verification-scope.md` i regułę w `REPOSITORY_GUIDELINES.md` i je akceptuje albo poprawia.

## Performance Considerations

Budżet: scenariusze dokładają do joba `backend` najwyżej 60 s (decyzja właściciela). Jedna klasa = jeden nowy kontekst (ok. 4 s w zmierzonym jobie) plus migracje. Każdy przebieg to do 33 wywołań lokalnego HTTP bez opóźnień, a limity kolejki alertów biegną w czasie wirtualnym. Szacunek: 15–30 s. Przy przekroczeniu budżetu najpierw łączy się scenariusze, które startują ten sam przebieg (C z G krok 3), zanim cokolwiek zostanie wycięte.

## Migration Notes

Brak migracji, zmiennych i zmian tras. PR dotyka katalogu `szczur-radar-api/` tylko w testach i dokumentach, więc PR env nie ma niczego do sprawdzenia poza zielonym CI. Po merge'u weryfikacją produkcji jest zielone CI na `main` z `PrEnvScenariosIT`; potem komentarz i zamknięcie issue #141.

## References

- Badanie: `context/changes/pr-env-scenarios-in-ci/research.md`
- Roadmapa: `context/foundation/roadmap.md:170-182`; zakres M-4: `context/foundation/verification-scope.md`
- Wzorzec scenariuszy i izolacji: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java:120-124, 254-293`
- Wzorzec prawdziwego klienta LLM na lokalnym serwerze: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/LlmOutageIT.java:54-126`
- Wzorzec kolejki w czasie wirtualnym: `szczur-radar-api/src/test/java/pl/szczurradar/api/alerts/OperatorAlertQueueTest.java:44-51`
- Wzorzec narzędzia nagrywającego: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/GoldRecorder.java:38-97`
- Kroki PR env: `context/deployment/deploy-plan.md` sekcje S-13 (`:1638`), S-18 (`:1731`), S-17 (`:1786`), S-20 (`:1966`), S-27 (`:2049`), S-34 (`:2364`)
- Eksperymenty X1/X2: `context/changes/post-m3-verification-audit/research.md:65-68, 253`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Nagrania i atrapa dostawcy LLM

#### Automated

- [x] 1.1 Test kompletności nagrań jest zielony (`RecordedDaysTest`) — a0e60a1
- [x] 1.2 Katalog nagrań ma 33 pliki JSON — a0e60a1
- [x] 1.3 Nagrania nie zawierają klucza — a0e60a1
- [x] 1.4 Wszystkie testy jednostkowe są zielone (`./mvnw -B test`) — a0e60a1
- [x] 1.5 Klasy używające `LocalReddit` są zielone — a0e60a1
- [x] 1.6 SVG roadmapy jest aktualny — a0e60a1
- [x] 1.7 PR istnieje i ma `Refs #141` — a0e60a1

### Phase 2: Kontekst scenariuszy i zwykły przebieg

#### Automated

- [x] 2.1 Klasa scenariuszy jest zielona z dwoma scenariuszami — ce321fd
- [x] 2.2 `InternalPipelineRunIT` jest zielony po przeniesieniu zegara — ce321fd
- [x] 2.3 Pełny zestaw jest zielony (`./mvnw -B verify`) — ce321fd
- [x] 2.4 Brak zmian w kodzie produkcyjnym poza dokumentami — ce321fd

### Phase 3: Scenariusze sygnałów, dni bez danych i nocy

#### Automated

- [x] 3.1 Klasa scenariuszy jest zielona z ośmioma testami — 2f2fe49
- [x] 3.2 Pełny zestaw jest zielony (`./mvnw -B verify`) — 2f2fe49
- [x] 3.3 Dokument testów opisuje klasę — 2f2fe49

### Phase 4: CI, lista ręczna i dowód

#### Automated

- [x] 4.1 Scenariusze są stabilne przy pięciu ziarnach kolejności — f761555
- [x] 4.2 Pełny zestaw jest zielony w obu kolejnościach klas — f761555
- [x] 4.3 Każde z czterech celowych zepsuć czerwieni `PrEnvScenariosIT` — f761555
- [x] 4.4 Job `backend` ma limit 15 min — f761555
- [x] 4.5 CI na PR jest zielone — f761555
- [x] 4.6 Czas scenariuszy mieści się w budżecie — f761555
- [x] 4.7 Dokumenty mają nowe treści — f761555

#### Manual

- [ ] 4.8 Właściciel akceptuje treść listy ręcznej i reguły
