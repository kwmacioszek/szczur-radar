# S-16: Wykop nie gubi postów po cichu — plan implementacji

## Overview

Pobieranie z Wykopu przestaje kończyć się „kompletne” przy brakach i przestaje zabijać noc jednym chwilowym błędem. Stronicowanie liczy tylko unikalne elementy, rozpoznaje niespójność (pusta lub krótka strona przed `total`, mniej unikalnych niż `total`, zmiana `total` między stronami), czyta wtedy zasób jeszcze raz i — gdy drugie czytanie też jest niespójne — kończy pobieranie błędem `INCOMPLETE_PAGING`. `WykopApiClient` ponawia 429, 5xx i błędy transportu (do 3 prób, `Retry-After` ≤ 30 s, inaczej 2 s / 4 s, WARN przy każdym ponowieniu), ale nigdy poza terminem 10 min na całe pobranie, więc krok `fetch` zawsze kończy się przed watchdogiem. Silnik, port `SourceFetcher` i API się nie zmieniają.

Źródło: roadmapa S-16 (`context/foundation/roadmap.md:250-261`), audyt B5 i B6 (`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:76-77`).

## Current State Analysis

- **Stronicowanie** (`szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:105-155`):
  - `received++` liczy każdy element, także duplikat i element o złym kształcie (`:127`); dedup jest osobno, przez `putIfAbsent` po `resource:id` (`:131`).
  - Koniec pętli: `data.isEmpty() || received >= total` (`:141`) — pusta strona przy `received < total` kończy się jako komplet, a duplikaty z przesuniętych stron pompują `received`.
  - Krótka strona przy znanym `total` i `received < total` → `MALFORMED_ANSWER` od razu (`:144-149`); bez `pagination` krótka strona kończy jako komplet.
  - `MAX_PAGES = 200` (`:36`), pauza 300 ms między stronami przez wstrzykiwany `Pause` (`:38`, `:57-61`).
- **Groźny przypadek, którego liczniki nie widzą.** Zakres nocy (D-2…D-1) leży w przeszłości, więc nowe elementy nie wpadają w trakcie; groźne jest usunięcie. 100 elementów, po stronie 1 usunięty jeden → strona 2 zwraca 49 przy `total` 99; 50 + 49 = 99 = `total`, a element nr 51 przepadł. Wykrywa to dopiero zmiana `total` między stronami.
- **Istniejąca mitygacja:** każdy dzień jest pobierany w dwie kolejne noce (D-1, potem D-2), a dedup między przebiegami działa — trwale ginie tylko to, co przepadnie przy drugim pobraniu.
- **Klient** (`WykopApiClient.java`): „No retries otherwise — the caller decides” (`:26`); 401 → jedno odświeżenie tokenu (`:82-85`); non-2xx → `WykopApiException.httpStatus` (`:150-154`); `IOException` → `TRANSPORT` (`:140`); przerwanie → `INTERRUPTED`; time-out żądania 30 s (`:40`). Każdy komunikat przechodzi przez `clean` (maskowanie klucza, sekretu, tokenu; maks. 500 znaków).
- **Kształt odpowiedzi** (fixture'y `src/test/resources/fixtures/wykop/search-*-page.json`): tylko `data` i `pagination {per_page, total}`, bez kursora. Wpisy mają `status: "visible"`, linki nie mają `status`; żadne nagranie nie pokazuje usuniętego elementu (niewiadoma z roadmapy).
- **Propagacja błędu:** `SourceIngest.fetch` (`pipeline/step/SourceIngest.java:78-103`) nie łapie i nie ponawia; akcja `fetch` jest `FIRE_ONCE` (`pipeline/flow/PipelineAgent.java:86-107`); wyjątek → krok `fetch:failed` → przebieg `failed` → dzwonek, cron exit 1.
- **Watchdog** (`pipeline/RunWatchdog`, `pipeline/PipelineStallSettings.java`): zatrzymuje przebieg bez postępu (`last_activity_at`) przez `PIPELINE_STALL_TIMEOUT` (domyślnie `max(15 min, maxCallDuration LLM + 5 min)`, 39 min na produkcji). Krok `fetch` rusza `last_activity_at` tylko na starcie i końcu (`pipeline/CLAUDE.md:72-74`) — całe pobieranie musi zmieścić się poniżej 15 min.
- **Limity Wykopu są nieznane** (API v3 ich nie publikuje; `context/archive/2026-10-06-first-assessed-wykop-feed/research.md:184-189`); PR env i produkcja dzielą klucze. Dzień to ~6 + 1 żądań wyszukiwania i jedno `auth`.
- **Reguły o ponowieniach:** `lessons.md:82-86` (akcje Embabel `FIRE_ONCE`, ponowienia tylko w warstwie transportu, z nazwy `TransportRetry`); `llm/CLAUDE.md:34` i `REPOSITORY_GUIDELINES.md` („`transport/` with the only retries”); `sources/CLAUDE.md` — klient nie może ponawiać „po cichu”.
- **Testy:** `WykopRepositoryTest:79` (`pagesUntilTheTotalIsReached`) opiera się na liczeniu duplikatów (12 elementów powtórzonych do 150); `WykopApiClientTest:185` przypina „500 → jedno żądanie”; `LocalWykopServer` nie wysyła nagłówków; `PipelineAgentIT:317` (stały 503 → `failed`) buduje repozytorium sam (`:771-779`).

## Desired End State

- Spokojna noc: te same żądania co dziś (bez dodatkowych), te same posty i liczniki.
- Usunięcie elementu w trakcie stronicowania lub pusta/krótka strona przed `total`: zasób czytany jeszcze raz; spójne drugie czytanie daje poprawny wynik (WARN w logu z przyczyną), niespójne — przebieg `failed` z `INCOMPLETE_PAGING` i komunikatem „N z total, przyczyna”.
- Chwilowe 429/5xx/transport: ponowione w kliencie (WARN), noc kończy się `succeeded`; trwała awaria: `failed` po 3 próbach z ostatnią przyczyną, albo po terminie 10 min z `DEADLINE_EXCEEDED` — zawsze przed watchdogiem.
- Niewiadoma o usuniętych elementach zamknięta wynikiem sondy na żywym API, zapisanym w `change.md`.
- Dokumentacja pakietów i lekcja mówią, gdzie żyją ponowienia źródła i jak są ograniczone w czasie.

Weryfikacja: `./mvnw -B verify` zielone; przebieg za zakres w PR env na prawdziwym Wykopie `succeeded` z licznikami zgodnymi z `total` wyszukiwarki.

### Key Discoveries:

- `WykopRepository.java:127,141` — `received` z duplikatami i warunek końca (B5).
- `WykopApiClient.java:26,135-157` — brak ponowień, mapowanie błędów (B6).
- `llm/transport/TransportRetry.java:25-39,121-139` — wzorce do przeniesienia (nie do ponownego użycia — klasa jest związana z tierami i wyjątkami LLM): `Sleeper` z `REAL = Thread::sleep`, `Retry-After` z limitem, backoff `2^(n-1)`, przywrócenie flagi przerwania.
- `pipeline/PipelineStallSettings.java:40,43` — `MIN_DEFAULT` 15 min, granica dla terminu pobierania.
- `PipelineAgentIT.java:771-779` — jedyne miejsce poza testami pakietu, które buduje `WykopApiClient`/`WykopRepository` ręcznie (oprócz `WykopGoldSampler`, który używa konstruktora domyślnego).

## What We're NOT Doing

- **Okna czasu ≤ 1 strona** (dzielenie zakresu, żeby uniknąć stronicowania) ani wielokrotne przejścia ze składaniem wyników — odrzucone jako nadmiarowe: jedno ponowne czytanie wystarcza przy rzadkim usunięciu i dwóch pobraniach każdego dnia.
- **Zapis i ostrzeżenie przy niekompletnym wyniku** (doliczanie braków do `skipped` albo nowy licznik w porcie) — niekompletny wynik po ponownym czytaniu kończy pobieranie błędem.
- **Puls postępu przez port `SourceFetcher`** (ruszanie `last_activity_at` w trakcie pobierania) — zmiana silnika poza zakresem S-16; termin pobierania wystarcza.
- **Nowe zmienne środowiskowe** (`WYKOP_*` do strojenia prób, terminu, time-outu) — stałe w kodzie; brak zmian w `.railway/railway.ts`.
- **Ponowienia w `SourceIngest` / `PipelineAgent`** — akcja `fetch` zostaje `FIRE_ONCE`.
- **Wspólna klasa ponowień dla `llm/` i `sources/`** — `TransportRetry` zostaje LLM-owy.
- **Izolacja źródeł, uzupełnianie dni starszych niż D-2, przesunięcie DST (B11)** — inne pozycje roadmapy.

## Implementation Approach

Trzy niezależne kawałki w jednym pakiecie, od faktów do kodu: najpierw sonda na żywym API (może pokazać kształt usuniętych elementów i częstość niespójności, zanim zmienimy logikę), potem klient (ponowienia + termin — podstawa, na której repozytorium liczy termin), potem repozytorium (spójność stronicowania). Termin pobierania liczy repozytorium raz na `itemsAddedBetween` przez zegar klienta i przekazuje go do każdego wywołania; klient nie zaczyna próby ani czekania, które by go przekroczyło. Dzięki temu granica „pobranie ≈ 10 min + 2 × time-out żądania” jest własnością kodu, a nie rachunkiem prawdopodobieństwa (przy szybko przychodzącym ciele odpowiedzi).

## Critical Implementation Details

- **Termin sprawdzany przed każdą próbą, także pierwszą.** Próba startuje tylko wtedy, gdy `now + timeout ≤ deadline`, a czekanie tylko, gdy `now + wait + timeout ≤ deadline`; inaczej `DEADLINE_EXCEEDED` z ostatnią przyczyną. Termin ogranicza więc pobranie do około `FETCH_DEADLINE + 2 × DEFAULT_TIMEOUT` (≈ 11 min), a nie do 10 min + nieograniczonej liczby żądań. Dlaczego nie równo „+ jedno żądanie”: w JDK `HttpRequest.timeout` obejmuje tylko czas do nagłówków odpowiedzi, a wewnętrzne ponowienie połączenia przez JDK (`ConnectException`) dostaje nowy pełny timer (źródła JDK 25, `MultiExchange.java:415-422,474`). Czytanie ciała (`BodyHandlers.ofString`) nie ma limitu — zawieszone ciało zostaje przypadkiem watchdoga, jak dziś, i nie jest celem tej zmiany. Test przypina `FETCH_DEADLINE + 2 × WykopApiClient.DEFAULT_TIMEOUT < PipelineStallSettings.MIN_DEFAULT`, żeby zmiana którejkolwiek stałej nie złamała tej relacji po cichu.
- **Odświeżenie tokenu jest `synchronized`.** Ponowienia `POST auth` czekają pod tym zamkiem; jedno pobranie jest jednowątkowe, więc to akceptowalne — ale nie przenoś czekania na odpowiedź GET pod zamek.
- **Drugie czytanie zastępuje pierwsze** dla danego zasobu (świeża mapa elementów tego zasobu), zamiast łączyć oba — inaczej element usunięty w trakcie pierwszego czytania trafiłby do bazy. Elementy drugiego zasobu (linki) nie są czytane ponownie z powodu niespójności wpisów.

## Faza 1: Sonda na żywym API

### Overview

Zamknąć niewiadomą z roadmapy („czy wyszukiwarka zwraca usunięte elementy i w jakim kształcie”) i zmierzyć, jak często dziś zdarza się niespójność stronicowania — na prawdziwych odpowiedziach, przed zmianą logiki.

### Changes Required:

#### 1. Sonda stronicowania

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/WykopPagingProbe.java`

**Intent**: Test z tagiem `measurement` (poza CI i jarem, standard kodu z `measurement/CLAUDE.md`), który dla każdego z ostatnich N dni (domyślnie 30, `-Dprobe.days`) czyta #gielda `search/entries` i `search/links` stronami **przez `WykopApiClient` bezpośrednio** (surowe strony, nie `WykopRepository`), z kluczami z powłoki i pauzą 300 ms. Drukuje tylko agregaty: dla dnia i zasobu liczbę stron, `total` każdej strony (czy się zmienił), elementy razem / unikalne / duplikaty, pustą lub krótką stronę przed `total`; dla całości rozkład wartości `status` elementu (z `null` osobno) i liczbę elementów bez tekstu.

**Contract**: Uruchomienie `./mvnw -Pmeasurement test -Dtest=WykopPagingProbe [-Dprobe.days=30]` z `WYKOP_KEY`/`WYKOP_SECRET` w powłoce; nic nie zapisuje na dysk, nie drukuje treści, nicków, id ani URL-i.

#### 2. Instrukcja uruchomienia

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/README.md`

**Intent**: Sekcja „Paging probe (`WykopPagingProbe`, S-16)” obok sekcji gold sample: co mierzy, polecenie, koszt (~7 żądań na dzień, ~1–2 min dla 30 dni), że wynik trafia do `change.md`.

**Contract**: Nowa sekcja w README; nic poza nią.

#### 3. Wynik w change.md

**File**: `context/changes/wykop-ingest-completeness/change.md`

**Intent**: Po ręcznym uruchomieniu dopisać do `## Notes` agregaty sondy (data uruchomienia, zakres dni, rozkład `status`, liczba niespójności każdego rodzaju) i wniosek dla niewiadomej z roadmapy.

**Contract**: Tylko sekcja `## Notes`; żadnych surowych danych.

**Bramka decyzyjna:** jeśli sonda pokaże elementy o `status` innym niż `visible`/`null`, które **mają tekst** (więc dziś zostałyby zapisane i ocenione), zatrzymaj się przed fazą 3 i wróć z tym do użytkownika — czy je pomijać, to osobna decyzja. Elementy usunięte bez tekstu już dziś kończą jako `skipped`. Tak samo, jeśli `total` zmienia się między stronami w zwykłe, spokojne dni (warunek (c) z fazy 3 wymuszałby wtedy drugie czytanie co noc) — warunek trzeba skorygować przed fazą 3.

### Success Criteria:

#### Automated Verification:

- Kompilacja testów z profilem pomiarowym przechodzi: `cd szczur-radar-api && ./mvnw -B -Pmeasurement test-compile`
- Sonda nie wchodzi do zwykłego `test`: `./mvnw -B test` nie uruchamia `WykopPagingProbe` (tag `measurement` wykluczony)

#### Manual Verification:

- Sonda uruchomiona z kluczami na ≥ 30 dniach, agregaty i wniosek zapisane w `change.md` (`## Notes`)
  Wykonuje: agent — `( set -a; source ~/.config/szczur-radar/wykop.env; set +a; cd szczur-radar-api && ./mvnw -B -Pmeasurement test -Dtest=WykopPagingProbe -Dprobe.days=30 )`, potem dopisuje agregaty i wniosek do `change.md`; dowód: wyjście sondy (same agregaty) i diff `change.md`; człowiek: nic.
- Bramka decyzyjna sprawdzona: brak elementów nie-`visible` z tekstem albo decyzja użytkownika zapisana w `change.md`
  Wykonuje: agent — z wyjścia sondy liczy (a) elementy o `status` innym niż `visible`/`null` z tekstem, (b) dni, w których `total` zmienił się między stronami; dowód: obie liczby w `change.md`; człowiek: tylko gdy (a) lub (b) > 0 — decyzja (faza 3 jest już wdrożona, więc korekta idzie jako poprawka do przodu).

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne uruchomienie sondy przez człowieka (klucze tylko w jego powłoce) przed fazą 2. Zmiana sposobu weryfikacji (2026-10-21, za zgodą użytkownika): sondę uruchamia agent w podpowłoce z `~/.config/szczur-radar/wykop.env`; klucze nie trafiają do wyjścia, logów, czatu ani repo (sonda drukuje tylko agregaty).

---

## Faza 2: Ponowienia i termin w `WykopApiClient`

### Overview

Chwilowe 429, 5xx i błędy transportu są ponawiane w kliencie — jawnie (WARN) i zawsze w terminie, który repozytorium wyznacza dla całego pobrania.

### Changes Required:

#### 1. Klient: próby, czekanie, termin

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java`

**Intent**: Każda wymiana HTTP (GET wyszukiwarki, GET po odświeżeniu tokenu, `POST auth`) dostaje do `MAX_ATTEMPTS = 3` prób przy 429, 5xx (500–599) i `TRANSPORT` (w tym time-out żądania). Czekanie przed kolejną próbą: `Retry-After` (sekundy albo data HTTP) przycięty do `MAX_RETRY_AFTER = 30 s`, przy braku lub nieczytelnym nagłówku 2 s, potem 4 s. Inne 4xx, `MALFORMED_ANSWER` i `INTERRUPTED` nigdy nie są ponawiane; 401 nadal daje jedno odświeżenie tokenu. Przed każdą próbą i każdym czekaniem sprawdzany jest termin (patrz Critical Implementation Details). Każde ponowienie to jeden WARN przez slf4j (wywołanie, status lub rodzaj błędu, numer próby, czekanie), zbudowany przez `clean`; ostatni błąd jest rzucany bez osobnego logu (loguje go krok). Javadoc klasy przestaje mówić „No retries” i opisuje politykę.

**Contract**:
- `public JsonNode get(String path, Map<String, ?> query, Instant deadline)`; dotychczasowe `get(path, query)` zostaje jako skrót z terminem `deadline(DEFAULT_TIMEOUT × MAX_ATTEMPTS + 2 × MAX_RETRY_AFTER)` dla wywołań spoza repozytorium (sonda).
- `public Instant deadline(Duration budget)` — `now + budget` z zegara klienta.
- Konstruktor testowy: `WykopApiClient(URI baseUrl, String key, String secret, Duration timeout, Sleeper sleeper, InstantSource clock)`; istniejące konstruktory delegują z `Sleeper.REAL` i `InstantSource.system()`.
- `@FunctionalInterface public interface Sleeper { void sleep(Duration d) throws InterruptedException; Sleeper REAL = Thread::sleep; }` (kształt jak `TransportRetry.Sleeper`).
- Wyczerpane próby: `WykopApiException` z rodzajem ostatniego błędu (`HTTP_STATUS` ze statusem albo `TRANSPORT`), komunikat kończy się „after 3 attempts”.
- Przekroczony termin: `WykopApiException(DEADLINE_EXCEEDED, …)` z ostatnią przyczyną w komunikacie (lub „before the first attempt”).
- Przerwanie w czasie czekania: przywrócona flaga, `INTERRUPTED`, bez dalszych prób.

#### 2. Nowy rodzaj błędu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopFailure.java`

**Intent**: `DEADLINE_EXCEEDED` — pobieranie nie zmieściło się w terminie; javadoc mówi, że termin chroni przed watchdogiem.

**Contract**: Nowa stała enuma; nic poza pakietem `sources/wykop` nie rozgałęzia się po `WykopFailure`.

#### 3. Lokalny serwer z nagłówkami

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/LocalWykopServer.java`

**Intent**: `Reply` dostaje nagłówki odpowiedzi (np. `Retry-After`), żeby dało się nagrać 429 z czekaniem; dotychczasowe `Reply(status, body)` i `Reply.ok` działają bez zmian.

**Contract**: `record Reply(int status, String body, Map<String, String> headers)` z konstruktorem kompaktowym `Reply(int, String)` → puste nagłówki.

#### 4. Testy klienta

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopApiClientTest.java`

**Intent**: Liczba żądań dowodzi, że nie ma ukrytych ponowień (wzorzec `llm/CLAUDE.md`): test `anErrorAnswerIsAnExceptionAfterOneRequestWithoutRetries` zamienia się w przypadki poniżej; `Sleeper` zbiera czekania, a fałszywy zegar przesuwa się o nie.

**Contract**: Przypadki:
- 503 → 200: sukces, 2 GET-y, czekania `[2s]`
- 500 × 3: `HTTP_STATUS` 500 „after 3 attempts”, 3 GET-y, czekania `[2s, 4s]`
- 429 z `Retry-After: 7` → 200: czekanie `[7s]`; `Retry-After: 120` → `[30s]`; nieczytelny → `[2s]`
- 400 i 404: jedno żądanie, bez czekania
- `POST auth` 503 → 200: token pobrany, 2 wywołania `auth`
- transport (zamknięty serwer): `TRANSPORT` po 3 próbach
- termin: zegar ustawiony tak, że po pierwszym 503 czekanie + time-out przekracza termin → `DEADLINE_EXCEEDED`, 1 żądanie; termin już minął → `DEADLINE_EXCEEDED` bez żądania
- przerwanie w czasie czekania → `INTERRUPTED`, flaga przerwania ustawiona, bez kolejnej próby
- maskowanie: ciało 503 cytujące klucz, sekret i token — ani wyjątek po wyczerpaniu prób, ani komunikat WARN nie zawiera sekretów; log sprawdzany wzorcem repo `@ExtendWith(OutputCaptureExtension.class)` + `assertThat(output).contains("WARN").doesNotContain(klucz, sekret, token)` (jak `pipeline/PipelineStallSettingsTest.java:26,57`)
- istniejące testy, które zderzą się z ponowieniami: `aConnectionErrorIsATransportFailure` (`:209`, zamknięty serwer → 3 próby) i `aLongErrorBodyIsCappedAfterMasking` (`:115`, 500 → ponawiane) dostają `Sleeper` bez czekania i asercję liczby żądań; testy 4xx (`:84-85`, `:177`, 403) zostają z jednym żądaniem

#### 5. Repozytorium przekazuje termin

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java`

**Intent**: `itemsAddedBetween` wyznacza raz `deadline = client.deadline(FETCH_DEADLINE)` i przekazuje go do każdego `client.get`; logika stronicowania bez zmian w tej fazie.

**Contract**: `static final Duration FETCH_DEADLINE = Duration.ofMinutes(10)`; javadoc `@throws` wymienia `DEADLINE_EXCEEDED`.

#### 6. Relacja z watchdogiem i IT

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopRepositoryTest.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineAgentIT.java`

**Intent**: Jeden test przypina `WykopRepository.FETCH_DEADLINE + 2 × WykopApiClient.DEFAULT_TIMEOUT < PipelineStallSettings.MIN_DEFAULT` — test w `WykopRepositoryTest` (ograniczenie należy do źródła; `FETCH_DEADLINE` zostaje pakietowe, a `MIN_DEFAULT` i `DEFAULT_TIMEOUT` są publiczne). `PipelineAgentIT.LocalWykop` buduje klienta z `Sleeper` bez czekania, żeby stały 503 (`:317`) dalej dawał `failed` bez realnych pauz.

**Contract**: Asercje `aFailingSourceFailsTheRunAndTheQueueTakesTheNextRequest` bez zmian (przebieg `failed`, `fetch:failed`, jedno powiadomienie, następny przebieg `succeeded`). Pozostałe miejsca, które budują klienta — `WykopConfiguration.java:38`, `measurement/WykopGoldSampler.java:84`, `RunWatchdogIT:380`, `api/InternalPipelineRunIT:377`, `api/PipelineRunControllerIT:402` — używają konstruktorów 2- i 4-argumentowych, które zostają (z `Sleeper.REAL`), więc bez zmian; te IT odpowiadają zawsze 200, więc nie trafiają na ponowienia.

#### 7. Dokumentacja pakietów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md`, `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: `sources/CLAUDE.md` — wiersz `<Source>ApiClient`: ponawia tylko chwilowe błędy, jawnie (WARN), w terminie od repozytorium; nigdy nie ponawia po cichu ani poza terminem; „Adding a source” — nowe źródło ponawia według polityki `WykopApiClient` (te same statusy, limity i termin), a przy drugim źródle z ponowieniami wspólną pętlę wydziela się do `sources/` zamiast kopiować ją trzeci raz (lekcja `lessons.md:61`); sekcja Tests — przypadki ponowień z liczeniem żądań. `REPOSITORY_GUIDELINES.md` — „`transport/` with the only retries” → „the only retries of model calls” (a ponowienia źródła żyją w jego kliencie). `pipeline/CLAUDE.md` przy uwadze o kroku `fetch` bez postępu: źródło ogranicza swoje pobranie terminem poniżej `MIN_DEFAULT` (Wykop: 10 min). Przejrzeć `grep -rn "No retries\|no retries\|without retries" szczur-radar-api/src szczur-radar-api/README.md` i poprawić tylko to, co dotyczy Wykopu.

**Contract**: Tylko wskazane wiersze/akapity.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-api && ./mvnw -B test`
- Testy klienta pokrywają przypadki z punktu 4, każdy z asercją liczby żądań: `./mvnw -B test -Dtest=WykopApiClientTest`
- Relacja terminu z watchdogiem przypięta testem w `WykopRepositoryTest`
- Pełna weryfikacja przechodzi (w tym `PipelineAgentIT`): `./mvnw -B verify`

#### Manual Verification:

- Przegląd: żaden nowy komunikat ani log nie może zawierać klucza, sekretu ani tokenu (wszystko przez `clean`)
  Wykonuje: agent — (1) każde nowe `log.*` i każdy komunikat wyjątku z `git diff origin/main -- szczur-radar-api/src/main` przypisane do testu, który sprawdza brak sekretów (`WykopApiClientTest`: `retriesQuotingTheSecretsLeaveThemOutOfTheErrorAndTheWarnings` i testy maskowania); miejsce bez takiego testu dostaje test; (2) po przebiegu z 4.3: `grep -cF` wartości `WYKOP_KEY` i `WYKOP_SECRET` (z `wykop.env`, bez wypisywania) w logach API PR env daje 0; dowód: tabela miejsce → test i obie liczby; człowiek: nic (dowodem są testy z break-checkiem, tabela je uzupełnia).

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 3.

---

## Faza 3: Spójność stronicowania

### Overview

Stronicowanie liczy unikalne elementy, rozpoznaje niespójność, czyta zasób raz jeszcze i kończy błędem, gdy drugie czytanie też jest niespójne.

### Changes Required:

#### 1. Repozytorium: przejście zasobu i ponowne czytanie

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java`

**Intent**: Jedno przejście zasobu zbiera elementy do własnej mapy i liczy `seen` = unikalne elementy (po `resource:id`) + elementy bez `id` + elementy o złym kształcie tego przejścia. Elementów bez `id` i o złym kształcie nie da się deduplikować, więc liczą się przy każdym wystąpieniu (świadome zawyżenie: w prawdziwych nagraniach jest ich 0, a przesunięcie stron i tak łapie warunek (c)); klucz `no-id:<pozycja>` liczony z mapy przejścia, nie ze wspólnej. `total` to `pagination.total` **strony 1** przejścia — z nim porównuje się `seen`, a jego zmianę na dalszych stronach łapie (c). Przejście jest **spójne**, gdy kończy się przy `seen ≥ total` i `total` był ten sam na każdej stronie. **Niespójność**: (a) pusta strona przy `seen < total`, (b) krótka strona (`< PAGE_SIZE`) przy `seen < total` — dziś `MALFORMED_ANSWER`, (c) `total` strony różny od `total` strony 1. Pełna strona przy `seen < total` → następna strona (również gdy duplikaty opóźniają `seen`; wtedy kolejna pusta strona to (a)). Przy niespójności WARN z przyczyną i drugie przejście od strony 1, które **zastępuje** pierwsze; drugie niespójne → `WykopApiException(INCOMPLETE_PAGING, …)`. Odpowiedź bez `pagination.total` zachowuje się jak dziś (krótka lub pusta strona kończy, bez sprawdzeń). `MAX_PAGES` (na przejście) i `data` nie-tablica (`MALFORMED_ANSWER`) bez zmian. Termin z fazy 2 obejmuje oba przejścia.

**Contract**: Wynik `WykopSearchResult` bez zmian kształtu; komunikat `INCOMPLETE_PAGING`: zasób, `seen` z `total`, przyczyna (np. „search/entries stayed inconsistent after 2 reads: 261 of 262, total changed 262→261 on page 3”). Javadoc klasy opisuje spójność i jedno ponowne czytanie.

#### 2. Nowy rodzaj błędu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopFailure.java`

**Intent**: `INCOMPLETE_PAGING` — stronicowanie niespójne w dwóch czytaniach; `MALFORMED_ANSWER` przestaje opisywać „a page short of the total”.

**Contract**: Nowa stała enuma, poprawiony javadoc `MALFORMED_ANSWER`.

#### 3. Testy repozytorium

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopRepositoryTest.java`

**Intent**: Strony budowane z prawdziwego nagrania z minimalną zmianą (przenumerowane `id`, zmieniony `total`), z komentarzem, co zmieniono (`sources/CLAUDE.md`, Tests). Odpowiedzi zależne od numeru strony i numeru przejścia.

**Contract**: Przypadki:
- `pagesUntilTheTotalIsReached` przepisany: 3 strony różnych elementów (50 + 50 + 12, `total` 112) → 112 elementów, 3 żądania, bez ponownego czytania
- spokojna noc: liczba żądań = liczba stron (żadnego dodatkowego)
- przesunięcie po usunięciu: przejście 1 — strona 1 `total` 100, strona 2 49 elementów przy `total` 99 → drugie przejście spójne (99) → wynik = elementy drugiego przejścia, element nr 51 obecny
- pusta strona przed `total` → drugie przejście spójne → wynik poprawny
- krótka strona przed `total` (dawny test `:119`) → drugie przejście tak samo → `INCOMPLETE_PAGING` „12 of 100”
- duplikaty ukrywające lukę (te same elementy na stronach 1 i 2, `total` 100) → wykryte, drugie przejście
- oba przejścia niespójne → `INCOMPLETE_PAGING`, liczba żądań = 2 przejścia
- niespójność wpisów nie powoduje ponownego czytania linków
- bez `pagination` i `data` nie-tablica, `MAX_PAGES` — jak dziś

#### 4. Dokumentacja pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md`

**Intent**: Wiersz `<Source>Repository`: „paging to the end” → stronicowanie do `total` ze sprawdzeniem spójności i jednym ponownym czytaniem; niekompletny wynik to błąd źródła, nigdy wynik „kompletny”.

**Contract**: Tylko ten wiersz (i ewentualnie sekcja Tests).

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-api && ./mvnw -B test`
- Testy repozytorium pokrywają przypadki z punktu 3: `./mvnw -B test -Dtest=WykopRepositoryTest`
- Pełna weryfikacja przechodzi (w tym `PipelineAgentIT`: pusty zakres, posty pominięte, dedup między przebiegami): `./mvnw -B verify`

#### Manual Verification:

- Przegląd komunikatów WARN i `INCOMPLETE_PAGING`: operator z samego komunikatu wie, który zasób, ile z ilu i dlaczego
  Wykonuje: test + agent — `WykopRepositoryTest` przypina pełną treść WARN dla każdego rodzaju niespójności (zmieniony `total`, pusta strona, duplikaty, krótka strona) i pełną treść `INCOMPLETE_PAGING` (zmieniony `total`, krótka strona); agent wkleja wszystkie warianty (z testów i z logów PR env, jeśli wystąpią); dowód: `./mvnw -B test -Dtest=WykopRepositoryTest` i lista wariantów; człowiek: ocena, czy tekst wystarcza operatorowi (ocena języka).

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 4.

---

## Faza 4: Domknięcie

### Overview

Lekcja dla przyszłych źródeł, weryfikacja na prawdziwym Wykopie w PR env, aktualizacja issue.

### Changes Required:

#### 1. Lekcja

**File**: `context/foundation/lessons.md`

**Intent**: Dopisać (append-only) regułę: ponowienia źródła danych żyją w jego kliencie (warstwie transportu), są jawne (WARN) i ograniczone terminem całego pobrania poniżej progu watchdoga, bo krok `fetch` nie raportuje postępu; stronicowanie po `total` sprawdza spójność, a nie tylko liczbę. Uzupełnia regułę z `:82-86` (która wymienia tylko `TransportRetry`), nie zmienia jej.

**Contract**: Nowy wpis na końcu pliku w formacie istniejących wpisów; nic wcześniej nie jest edytowane.

#### 2. Weryfikacja w PR env i issue

**File**: — (operacje, bez zmian w kodzie)

**Intent**: W PR env (prawdziwe klucze Wykopu) admin uruchamia przebieg za zakres dwóch niedawnych dni; przebieg `succeeded`, `fetched` zgodne z sumą `total` wpisów i linków tych dni. Źródło `total` (żaden log go nie podaje): sonda z fazy 1 uruchomiona na te same dni (`-Dprobe.days` obejmujące zakres) albo `fetched` przebiegu produkcji za ten sam zakres w `/admin` (drobna różnica = elementy dodane lub usunięte między przebiegami, opisać w komentarzu). W logach API brak WARN o ponowieniach i niespójności (albo uzasadnione). Komentarz w issue S-16 z wynikiem; zasady „nie merguj przy `RUNNING`” obowiązują.

**Contract**: Brak.

### Success Criteria:

#### Automated Verification:

- CI na PR zielone (wszystkie joby, w tym `./mvnw -B verify`)

#### Manual Verification:

- Lekcja dopisana na końcu `context/foundation/lessons.md`, bez edycji wcześniejszych wpisów
  Wykonuje: agent — `git diff origin/main -- context/foundation/lessons.md`: 0 usuniętych linii, jeden fragment na końcu pliku; dowód: wynik `git diff --numstat` i fragment; człowiek: nic.
- Przebieg za zakres w PR env `succeeded`, `fetched` zgodne z `total` wyszukiwarki, logi bez nieuzasadnionych WARN
  Wykonuje: agent — `/api/deploy-safety` → `safe:true`; logowanie `curl`-em jako admin PR env (dane z pliku poza repo, nigdy na czacie), `POST /api/admin/pipeline-runs` za dwa niedawne dni, czekanie na koniec, odczyt `status` i `fetched`; zaraz potem sonda z fazy 1 z `-Dprobe.days` obejmującym ten zakres i porównanie `fetched` z sumą `total` wpisów i linków; logi API przez `railway logs -s szczur-radar-api -e szczur-radar-pr-94 -p <project>` (bez `railway link`) przeszukane pod „Retrying Wykop” i „Reading Wykop … again”; dowód: id przebiegu, `status`, `fetched`, suma `total`, liczba linii WARN z treścią; człowiek: jednorazowo dostęp admina do PR env. Wymaga działającego PR env: przy awarii Railway wiersz czeka, a nie jest zastępowany inną kontrolą.
- Komentarz w issue S-16 z wynikiem weryfikacji w PR env
  Wykonuje: agent — `gh issue comment 75` z dowodami z 1.3, 1.4, 2.5 i 4.3; dowód: link do komentarza; człowiek: nic (merge pozostaje u człowieka).

---

## Testing Strategy

### Unit Tests:

- `WykopApiClientTest`: polityka ponowień z liczeniem żądań i czekań (fałszywy zegar przesuwany przez `Sleeper`), termin, przerwanie, maskowanie sekretów.
- `WykopRepositoryTest`: przejścia spójne i niespójne na stronach zależnych od numeru strony i przejścia; przesunięcie po usunięciu; brak dodatkowych żądań w spokojną noc.
- `WykopRepositoryTest`: `FETCH_DEADLINE + 2 × DEFAULT_TIMEOUT < MIN_DEFAULT`.
- `WykopFetcherTest` bez zmian (mock repozytorium).

### Integration Tests:

- `PipelineAgentIT`: stały 503 → `failed` (z ponowieniami bez czekania), pusty zakres → `failed`, szczęśliwa ścieżka i dedup między przebiegami bez zmian.

### Manual Testing Steps:

1. Sonda na ≥ 30 dniach (agent, klucze z `~/.config/szczur-radar/wykop.env` w podpowłoce); wynik i bramka decyzyjna w `change.md`.
2. Przebieg za zakres w PR env na prawdziwym Wykopie (agent przez API admina); zaraz po nim sonda na te same dni, żeby `total` nie zdążył się zmienić, i porównanie `fetched` z sumą `total` (zapasowo: `fetched` przebiegu produkcji za ten zakres).
3. Logi API w PR env (agent): linie WARN z treścią i licznik wystąpień `WYKOP_KEY`/`WYKOP_SECRET` (oczekiwane 0).
4. Człowiek: dostęp admina do PR env, ocena treści komunikatów (3.4) i decyzja tylko przy zapalonej bramce 1.4.

## Performance Considerations

Spokojna noc: zero dodatkowych żądań. Niespójna noc: jedno dodatkowe przejście zasobu (~6 żądań). Najgorszy przypadek przy awarii Wykopu: około 10 min (termin) + 2 × 30 s, a nie kilkadziesiąt minut (przy szybko przychodzącym ciele odpowiedzi — patrz Critical Implementation Details). Liczba żądań przy ponowieniach: ≤ 3 na wymianę, z czekaniem ≥ 2 s — nie zwiększa tempa względem dzisiejszej pauzy 300 ms.

## Migration Notes

Brak migracji, zmiennych środowiskowych i zmian IaC. Wycofanie = revert PR.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-16)
- Audyt: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:76-77,136,273`
- Brak limitów Wykopu: `context/archive/2026-10-06-first-assessed-wykop-feed/research.md:184-189`
- Wzorzec ponowień: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/transport/TransportRetry.java`
- Próg watchdoga: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStallSettings.java`
- Lekcja o `FIRE_ONCE`: `context/foundation/lessons.md:82-86`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Sonda na żywym API

#### Automated

- [x] 1.1 Kompilacja testów z profilem pomiarowym przechodzi — 7cdd713
- [x] 1.2 Sonda nie wchodzi do zwykłego `test` — 7cdd713

#### Manual

- [x] 1.3 Sonda uruchomiona z kluczami na ≥ 30 dniach, agregaty i wniosek zapisane w `change.md` — 9a6e7ab
- [x] 1.4 Bramka decyzyjna sprawdzona (brak elementów nie-`visible` z tekstem albo decyzja zapisana) — 9a6e7ab

### Phase 2: Ponowienia i termin w WykopApiClient

#### Automated

- [x] 2.1 Testy jednostkowe przechodzą — f08c06a
- [x] 2.2 Testy klienta pokrywają przypadki ponowień z asercją liczby żądań — f08c06a
- [x] 2.3 Relacja terminu z watchdogiem przypięta testem w `WykopRepositoryTest` — f08c06a
- [x] 2.4 Pełna weryfikacja przechodzi (w tym `PipelineAgentIT`) — f08c06a

#### Manual

- [ ] 2.5 Przegląd: żaden nowy komunikat ani log nie zawiera klucza, sekretu ani tokenu

### Phase 3: Spójność stronicowania

#### Automated

- [x] 3.1 Testy jednostkowe przechodzą — 8ab8bc7
- [x] 3.2 Testy repozytorium pokrywają przypadki spójności i ponownego czytania — 8ab8bc7
- [x] 3.3 Pełna weryfikacja przechodzi (w tym `PipelineAgentIT`) — 8ab8bc7

#### Manual

- [ ] 3.4 Przegląd komunikatów WARN i `INCOMPLETE_PAGING`

### Phase 4: Domknięcie

#### Automated

- [x] 4.1 CI na PR zielone — 220f3af

#### Manual

- [x] 4.2 Lekcja dopisana na końcu `lessons.md` — 1cf5f7b
- [ ] 4.3 Przebieg za zakres w PR env `succeeded`, `fetched` zgodne z `total`
- [ ] 4.4 Komentarz w issue S-16 z wynikiem weryfikacji
