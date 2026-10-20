# F-04: kanał alarmowy poza aplikacją — plan implementacji

## Overview

Oba crony (`pipeline-trigger`, `db-backup`) meldują się do healthchecks.io: start i kod wyjścia
z ostatnią linią logu. Healthchecks.io alarmuje w Telegramie, gdy meldunek nie przyjdzie na czas
(noc albo backup się nie odbyły, cron utknął, kontener podmieniono w trakcie pracy) albo gdy cron
zgłosi porażkę. API dostaje nadawcę zdarzeń do osobnego checka (błąd z opisem i od razu „ok”)
oraz komendę operatora `send-test-alert`; konkretne ścieżki ERROR podłączy S-18.

Problem z `frame.md`: nikt poza aplikacją nie oczekuje nocnego przebiegu ani backupu, więc ich brak
jest niewidoczny, a API nie ma drogi, by wyprowadzić zdarzenie poza proces.

## Current State Analysis

Z `frame.md` (badanie hipotez, WYSOKA pewność):

- Brak nocy jest niewidoczny: pominięty takt crona Railway jest cichy, reguła konta „deployment
  crashed” działa tylko na `CRASHED`, QUEUED ~11 min bez sygnału (`deploy-plan.md:361`), świeżość
  mierzy koniec ostatniego przebiegu (`deploy-plan.md:467`). API usypia (`.railway/railway.ts:121`)
  i nie ma harmonogramu poza watchdogiem.
- API nie ma drogi wyjścia: ścieżki ERROR (`RunNotifications.java:53-54`,
  `PipelineStartupRecovery.java:94-97`, `RunWatchdog.java:96-98`, `PipelineRunService.java:236,313-315`,
  `ConcurrentAssessmentDispatcher.java:129,230`) kończą się logiem; dzwonek to tylko INSERT.
- Crash crona już daje push (reguła Railway, sprawdzona tylko w testach); `deploy-verification-hardening`
  (osobna zmiana w toku) doda issue z pushem w GitHub Mobile dla zdarzeń deployu i oddaje F-04
  „push innym kanałem” oraz wykrywanie braku sygnału.
- Merge, który podmienia kontener crona w trakcie pracy, nie daje dziś sygnału (`deploy-plan.md:521-525`).

Z badania kodu (ten plan):

- `tools/pipeline-trigger/trigger.sh:45-54` — zewnętrzny proces (wrapper `timeout`) jako jedyny
  widzi każdy wynik: limit czasu (124/137 → `fail`), kod 2 (`config_error`), kod 1, sukces.
  Pomijanie poza produkcją jest wcześniej (`:21-25`). Sekrety idą do `curl` przez `--config -`
  (`:76-96`). Obraz ma `curl` i `jq` (`Dockerfile:6`).
- `tools/db-backup/backup.sh:41-49` — analogiczny wrapper `BACKUP_TIMEOUT`; argumenty wykonują
  inne narzędzia przez `exec` (`:14-16`) jeszcze przed nim; pomijanie poza produkcją `:33-37`.
  **Obraz nie ma żadnego klienta HTTP** (`Dockerfile:8`: tylko `ca-certificates jq rclone`).
- Testy: `tools/pipeline-trigger/test.sh` ma stub API w Pythonie (`:57-137`) i asercje
  `requests_were` (`:194`, liczy wszystkie żądania); `tools/db-backup/test.sh` nie ma stuba HTTP.
- `.railway/railway.ts`: sekrety ustawiane przez operatora są deklarowane `preserve()` (np.
  `WYKOP_KEY` `:160-161`); wzorzec ignorowania `${pipelineTrigger.name}/TRIGGER_*` (`:312`)
  połknąłby zmienną z prefiksem `TRIGGER_`, a deklaracja + ignorowanie to błąd planu (`:291-292`).
  Nowy PR env kopiuje **niezapieczętowane** zmienne produkcji (`deploy-plan.md:50`); zapieczętowanych nie.
- API: opcjonalne ustawienia wzorem `internal/PipelineTriggerToken.java:38-49` (`from(PropertyResolver)`,
  `enabled()`, ukrywający `toString`) i `settings/InvalidSettingsException`; klient HTTP wzorem
  `sources/wykop/WykopApiClient.java` (JDK `HttpClient`, maskowanie sekretu, bez dołączania przyczyny
  wyjątku, `:139-141`); testy HTTP na lokalnym `com.sun.net.httpserver.HttpServer`
  (`src/test/.../sources/wykop/LocalWykopServer.java`). Komendy: `cli/CommandType.java`,
  `cli/CommandLine.standard()` (`:32-35`), `cli/ExitCode` (`OK` 0, `FAILED` 1, `USAGE` 2); komenda
  może działać bez kontekstu Springa.
- healthchecks.io (dokumentacja + źródło `hc/api/models.py`): darmowy plan Hobbyist, 20 checków,
  Telegram bez ograniczeń planu; alarm w Telegramie zawiera powód i treść ostatniego meldunku
  (≤1000 znaków); `/fail` i `/<kod≠0>` od razu dają „down”, następny sukces „up”; po `/start` brak
  końca w karencji daje alarm; **alarm tylko przy zmianie stanu** (`models.py:535-539`); ≤5 pingów
  na minutę na check; API zarządzania v3 (klucz projektu z prawem zapisu, upsert `unique`) jest w
  darmowym planie. Alarmy idą przez @HealthchecksBot, podpinanego raz w panelu.

## Desired End State

- Noc bez meldunku `pipeline-trigger` (do 03:45 UTC) albo `db-backup` (do 04:15 UTC) daje wiadomość
  w Telegramie z powodem „nie przyszedł na czas”; porażka crona — od razu, z jego ostatnią linią logu;
  następny dobry przebieg — wiadomość „up”.
- Awaria healthchecks.io albo sieci nigdy nie zmienia wyniku ani kodu wyjścia crona.
- Komenda `send-test-alert` na API daje w Telegramie wiadomość z opisem zdarzenia i zaraz „up”;
  nadawca jest gotowy do podłączenia przez S-18 i nigdy nie psuje wywołującego.
- Nic nie odpytuje produkcji z zewnątrz, w API nie ma nowego zegara, koszt usług $0.
- Checki powstają skryptem `tools/healthchecks/checks.sh` z kluczem trzymanym lokalnie przez operatora.
- Sprawdzone w PR env na testowych checkach; produkcja uruchamiana runbookiem F-04 w `deploy-plan.md`.

### Key Discoveries:

- Ping musi iść z wrappera, nie z ciała skryptu — tylko tam widać limit czasu (`trigger.sh:45-54`,
  `backup.sh:41-49`); gałąź limitu czasu woła dziś `fail` (exit), więc meldunek musi pójść przed nim.
- Karencja liczy się od `/start`, więc musi pokryć najdłuższy przebieg: limit `TRIGGER_TIMEOUT` 3 h,
  `BACKUP_TIMEOUT` 2 h (`trigger.sh:28`, `backup.sh:41`).
- `requests_were` w `pipeline-trigger/test.sh:194` liczy wszystkie żądania — meldunki zepsułyby
  istniejące asercje, jeśli filtr nie rozróżni ścieżek.
- Zapieczętowanie produkcyjnej wartości jest warunkiem izolacji PR envów (`deploy-plan.md:50,1155`);
  cron czyta zmienne przy tworzeniu deploymentu, więc po ustawieniu potrzebuje redeployu.
- Adres pingu jest sekretem (każdy, kto go zna, może meldować) — nie może trafić do logu, `ps`,
  komunikatu wyjątku ani echa walidacji (`SettingText.echoed` powtarza 16 znaków — tu nie wolno).

## What We're NOT Doing

- Podłączania ścieżek ERROR API i logu `/api/ready` do nadawcy (S-18); F-04 ma tylko zdarzenie testowe.
- Wykrywania dni bez danych i uzupełniania dni starszych niż D-2 (S-13).
- Własnego bota Telegrama i wysyłki z API bezpośrednio do Telegrama (decyzja: wszystko przez healthchecks.io).
- Zmian w `deploy-verification-hardening` ani kierowania jego alarmów do Telegrama.
- Usuwania reguły Railway „deployment crashed” — zostaje jako drugi sygnał crasha.
- Nadzoru synchronizacji NAS → Google Drive (poza Railway) i alarmu o awarii samego healthchecks.io.
- Checków healthchecks.io w CI ani w `.railway/railway.ts`; klucz API zarządzania nie trafia do CI ani Railway.
- Odpytywania produkcji z zewnątrz, zegarów w API, kanału przychodzącego (ograniczenia z `frame.md`).

## Implementation Approach

Każda usługa ma swój check i jedną zmienną `HEALTHCHECK_PING_URL` (adres UUID checka). Bez zmiennej
meldowanie nic nie robi — tak działają PR envy i lokalne uruchomienia. Crony meldują się z wrappera
(`/start`, potem `/<kod wyjścia>` z ostatnią linią logu w treści); jedno zapytanie `curl` z krótkim
limitem i kilkoma ponowieniami, którego wynik jest ignorowany poza ostrzeżeniem w logu. API wysyła do
checka zdarzeń `/fail` z opisem, a zaraz potem meldunek sukcesu — bo healthchecks.io alarmuje tylko
przy zmianie stanu, każde zdarzenie musi wrócić do „up”. Konfiguracja checków (harmonogram UTC,
karencja, podpięcie Telegrama) żyje w skrypcie z API zarządzania; produkcję uruchamia runbook po
merge'u, a weryfikacja przed merge'em idzie na testowych checkach w PR env.

## Critical Implementation Details

- **Kolejność w wrapperze.** Meldunek `/start` idzie po gałęzi „skipped”, a meldunek końca przed
  każdym `exit` wrappera — także w gałęzi limitu czasu (dziś `fail` kończy proces od razu). Wynik
  crona = wynik ciała skryptu; meldunek nigdy go nie zmienia.
- **Ostatnia linia logu.** Ciało skryptu pisze na stderr; wrapper przechwytuje je tak, żeby log w
  Railway pozostał bez zmian (np. kopia przez `tee` do pliku tymczasowego), i wysyła tylko ostatnią
  niepustą linię. Logi skryptów nie zawierają sekretów (istniejące asercje w `test.sh`).
- **Sekret w `curl`.** Adres idzie przez `--config -` na stdin, jak token w `trigger.sh:76-96`;
  nigdy `set -x`. Adres spoza `https://` → ostrzeżenie w logu i brak meldunku, nie błąd konfiguracji
  crona; uzbrojony check zaalarmuje wtedy po karencji.
- **Uzbrojenie checków.** Nowy check ma stan „new” i healthchecks.io nie liczy dla niego terminu
  (`hc/api/models.py:309-336,365`): bez pierwszego meldunku milczy zawsze. Dlatego `checks.sh apply`
  wysyła do każdego checka crona w stanie „new” jeden meldunek sukcesu i sprawdza przez API stan „up”;
  runbook i weryfikacja w PR env sprawdzają ten stan przed czekaniem na alarm.
- **Karencje.** `pipeline-trigger`: `30 0 * * *` UTC, karencja 3 h 15 min (alarm braku ~03:45 UTC).
  `db-backup`: `0 2 * * *` UTC, karencja 2 h 15 min (~04:15 UTC). Check zdarzeń API: typ „simple”,
  okres i karencja po 365 dni (bez harmonogramu; każde zdarzenie kończy się sukcesem, więc okres się odnawia).
  Karencje cronów wynikają z `TRIGGER_TIMEOUT` i `BACKUP_TIMEOUT` (oba ustawiane ręcznie, na liście
  `ignore`): zmiana limitu wymaga `checks.sh apply` z nową karencją, inaczej długi przebieg da fałszywy alarm.

## Phase 1: Meldunki cronów

### Overview

`pipeline-trigger` i `db-backup` meldują start i wynik do healthchecks.io, jeśli ustawiono
`HEALTHCHECK_PING_URL`. Bez zmiennej zachowują się dokładnie jak dziś.

### Changes Required:

#### 1. Meldunki w `pipeline-trigger`

**File**: `tools/pipeline-trigger/trigger.sh`

**Intent**: Wrapper melduje `/start` przed uruchomieniem ciała, a po nim `/<kod>` (0, 1, 2; limit
czasu → 1) z ostatnią linią logu w treści, i kończy się kodem ciała. Funkcja meldunku nic nie robi
bez zmiennej, nie loguje adresu, a jej porażka daje tylko ostrzeżenie.

**Contract**: nowa opcjonalna zmienna `HEALTHCHECK_PING_URL` (`https://…`); żądania
`POST <url>/start` i `POST <url>/<exit-code>` z treścią `text/plain` ≤ 1000 znaków; `curl -fsS -m 10
--retry 3 -o /dev/null`, adres przez `--config -`; kody wyjścia crona bez zmian; gałąź „skipped” nie
melduje niczego.

#### 2. Meldunki w `db-backup`

**File**: `tools/db-backup/backup.sh`, `tools/db-backup/Dockerfile`

**Intent**: To samo w wrapperze `BACKUP_TIMEOUT`; ścieżki `exec` innych narzędzi (`:14-16`) i gałąź
„skipped” nie meldują. Obraz dostaje `curl`.

**Contract**: ta sama zmienna i te same żądania co w punkcie 1; `curl` w linii `apt-get install`
obrazu; funkcja meldunku powielona świadomie (osobne obrazy i konteksty budowania), z komentarzem
wskazującym drugą kopię.

#### 3. Testy na podstawionym serwerze

**File**: `tools/pipeline-trigger/test.sh`, `tools/db-backup/test.sh`

**Intent**: Stub `pipeline-trigger` dostaje ścieżki `/ping/<id>[/start|/<kod>]` i zapisuje treść;
`requests_were` liczy tylko żądania do API. `db-backup/test.sh` dostaje stub HTTP wzorem
`start_stub` z `pipeline-trigger` (kontener `$id-…` sprzątany przez `cleanup`, bez nazwanych
kontenerów po teście — self-hosted runner).

**Contract**: przypadki `pipeline-trigger`: sukces → `start` + `0` z linią `OK:`; porażka przebiegu →
`1` z linią `FAILED:`; błąd konfiguracji → `2`; limit czasu → `1`; stub meldunków zwraca 500 albo jest
nieosiągalny → kod wyjścia bez zmian; brak zmiennej → zero meldunków; „skipped” → zero meldunków;
adres nie pojawia się w logu. `db-backup`: sukces → `start` + `0`; odmowa przy kurczeniu → `1` z linią;
stub meldunków nieosiągalny → wynik bez zmian.

#### 4. README narzędzi

**File**: `tools/pipeline-trigger/README.md`, `tools/db-backup/README.md`

**Intent**: Wiersz `HEALTHCHECK_PING_URL` w tabeli zmiennych, krok meldunku w „Przebieg”, zdanie w
„Kody wyjścia”, że meldunek nigdy nie zmienia kodu, i przy `TRIGGER_TIMEOUT` / `BACKUP_TIMEOUT` uwaga,
że zmiana limitu wymaga `checks.sh apply` z nową karencją.

**Contract**: sekcje „Zmienne”, „Przebieg”, „Kody wyjścia” każdego README.

### Success Criteria:

#### Automated Verification:

- Testy triggera przechodzą: `tools/pipeline-trigger/test.sh`
- Testy backupu przechodzą: `tools/db-backup/test.sh`
- Po testach nie zostają kontenery ani sieci testów: `docker ps -a` i `docker network ls` bez wpisów z prefiksem testu

#### Manual Verification:

- Przegląd: adres meldunku nie trafia do logu, `ps` ani komunikatu w żadnej ścieżce obu skryptów

**Implementation Note**: Po zielonych testach zatrzymaj się na ręcznym przeglądzie przed fazą 2.

---

## Phase 2: Zgłaszanie zdarzeń z API

### Overview

API dostaje nadawcę zdarzeń operatora: opcjonalne ustawienie, wysyłkę do checka zdarzeń, która
nigdy nie psuje wywołującego, i komendę `send-test-alert`. Żadna ścieżka biegu go jeszcze nie woła.

### Changes Required:

#### 1. Ustawienie

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertSettings.java` (nowy)

**Intent**: Czyta opcjonalne `HEALTHCHECK_PING_URL`: brak albo pusty → kanał nieskonfigurowany; wartość
nie będąca absolutnym adresem `https://` → `InvalidSettingsException` z nazwą zmiennej, **bez echa wartości**.

**Contract**: `static AlertSettings from(Function<String, String> env)` (używalne ze Springiem i bez),
`boolean configured()`, `toString()` → `<hidden>`; walidacja przy starcie serwera i w komendzie. Bean
konfiguracji w `alerts/` ma `@ConditionalOnWebApplication` (jak `RunWatchdog`): komendy budują ten sam
kontekst bez weba (`SzczurRadarApiApplication.java:36-40`), więc zła wartość zatrzymuje start serwera,
jak `PIPELINE_STALL_TIMEOUT` (`deploy-plan.md:95`), a nie pre-deploy `migrate`.

#### 2. Nadawca

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/` — `AlertEvent.java`,
`OperatorAlerts.java`, `HealthchecksAlerts.java`, `NoOperatorAlerts.java` (nowe)

**Intent**: Zamknięty zbiór zdarzeń w enumie (w F-04 tylko `TEST_SIGNAL`; S-18 dopisze resztę).
`report` wysyła `POST <url>/fail` z treścią „<zdarzenie>: <opis>” (przycięte do 1000 znaków), a po
odpowiedzi `POST <url>` z krótkim „ok”, żeby check wrócił do „up” i kolejne zdarzenie znów dało
wiadomość. Jedna próba na żądanie, bez ponowień (ponowienia żyją tylko w `llm/transport`), krótkie
limity czasu. Nigdy nie rzuca; porażka → WARN bez adresu i bez dołączonej przyczyny wyjątku (wzorem
`WykopApiClient.java:139-141`). Bez konfiguracji bean to `NoOperatorAlerts`.

**Contract**: `interface OperatorAlerts { Delivery report(AlertEvent event, String detail); }`, gdzie
`Delivery` to enum `DELIVERED` / `FAILED` / `NOT_CONFIGURED`; wysyłka synchroniczna (connect 5 s,
żądanie 10 s), bez executora i bez zegara; JDK `HttpClient` wzorem `WykopApiClient`.

#### 3. Komenda operatora

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/cli/SendTestAlertCommand.java` (nowy),
`cli/CommandType.java`, `cli/CommandLine.java`

**Intent**: `send-test-alert` wysyła `TEST_SIGNAL` przez nadawcę bez uruchamiania Springa (bez `PG*`
i `LLM_*`), drukuje wynik bez adresu i kończy się kodem.

**Contract**: `CommandType.SEND_TEST_ALERT("send-test-alert", …)`; rejestracja w
`CommandLine.standard()`; `ExitCode.OK` przy `DELIVERED`, `FAILED` przy `FAILED`, `USAGE` przy braku
albo złej wartości `HEALTHCHECK_PING_URL`.

#### 4. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/alerts/` i `.../cli/`

**Intent**: `*Test` bez Springa na lokalnym `HttpServer` (wzorem `LocalWykopServer`): kolejność
`/fail` → sukces i treść; 429/500/brak odpowiedzi/przekroczony czas → `FAILED` bez wyjątku; adres nie
występuje w logu, komunikacie ani `toString`; ustawienie: brak, pusty, `http://`, śmieci (bez echa).
Komenda: kody wyjścia dla trzech wyników. `WebApplicationContextRunner` dla beana: bez zmiennej →
`NoOperatorAlerts`, z poprawną → `HealthchecksAlerts`, ze złą → błąd startu; zwykły
`ApplicationContextRunner` (kontekst komendy) ze złą wartością startuje bez błędu.

**Contract**: nadawca czyta tylko kod statusu odpowiedzi, więc nie potrzebuje fixture'a z prawdziwą treścią.

#### 5. Dokumentacja API

**File**: `szczur-radar-api/README.md`

**Intent**: Zmienna `HEALTHCHECK_PING_URL` i komenda `send-test-alert` w sekcji komend operatora.

**Contract**: sekcje zmiennych i „Komendy operatora”.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-api && ./mvnw -B test`
- Pełna weryfikacja przechodzi: `cd szczur-radar-api && ./mvnw -B verify`

#### Manual Verification:

- Lokalnie `java -jar target/szczur-radar-api.jar send-test-alert` bez zmiennej kończy się kodem 2 z czytelnym komunikatem

**Implementation Note**: Po zielonym `verify` zatrzymaj się na ręcznym sprawdzeniu przed fazą 3.

---

## Phase 3: Checki jako kod i IaC

### Overview

Skrypt operatora tworzy i aktualizuje checki przez API zarządzania; `.railway/railway.ts` deklaruje
`HEALTHCHECK_PING_URL` na trzech usługach; CI testuje skrypt.

### Changes Required:

#### 1. Skrypt checków

**File**: `tools/healthchecks/checks.sh`, `tools/healthchecks/README.md` (nowe)

**Intent**: `apply <środowisko>` robi upsert (`unique: ["name"]`) trzech checków
`<środowisko>/pipeline-trigger`, `<środowisko>/db-backup`, `<środowisko>/api-events` z harmonogramami,
strefą UTC i karencjami z sekcji Critical Implementation Details oraz `channels: "*"` (Telegram
projektu), uzbraja checki cronów w stanie „new” jednym meldunkiem sukcesu, sprawdza przez
`GET /api/v3/checks/` stan „up” obu checków cronów i drukuje adresy pingów na terminal. Opcje `--schedule '<cron>'` i `--grace <sekundy>`
nadpisują oba checki cronów (tylko weryfikacja). `delete <środowisko>` usuwa checki środowiska.

**Contract**: bash + `curl` + `jq`; klucz w `HEALTHCHECKS_API_KEY` (nigdy argument), do `curl` przez
`--config -`; kody: 0 sukces, 1 błąd API, 2 błąd użycia; adresy drukowane tylko na stdout, nigdy do
logu; README: założenie projektu i Telegrama w panelu, uzyskanie klucza, użycie, uwaga „adres to sekret”.

#### 2. Testy skryptu

**File**: `tools/healthchecks/test.sh`, `tools/healthchecks/fixtures/` (nowe)

**Intent**: Testy bez Dockera, ze stubem `curl` w `PATH` (wzorem stubów w `tools/deploy-gate/test.sh`),
na odpowiedziach nagranych z prawdziwego API zarządzania: treść upsertu (nazwa, harmonogram, strefa,
karencja, kanały), uzbrojenie checka w stanie „new” i brak meldunku dla checka już „up”, check
cronu nie w stanie „up” po uzbrojeniu → 1, nadpisania weryfikacyjne, `delete`, błąd API → 1, brak
klucza → 2, klucz nie trafia do argumentów ani wyjścia.

**Contract**: `fixtures/` z prawdziwymi odpowiedziami `POST /api/v3/checks/` i `GET /api/v3/checks/`.

#### 3. Job CI

**File**: `.github/workflows/ci.yml`

**Intent**: Job `healthchecks-test` uruchamia `tools/healthchecks/test.sh` i wchodzi do `needs:`
joba `railway-config`, jak `deploy-gate-test`.

**Contract**: `runs-on` z tej samej zmiennej runnera co pozostałe joby; bez filtra ścieżek.

#### 4. Deklaracje zmiennych

**File**: `.railway/railway.ts`

**Intent**: `HEALTHCHECK_PING_URL: preserve()` na `api`, `dbBackup` i `pipelineTrigger`, z
komentarzem: ustawiane przez operatora, zapieczętowane na produkcji, brak w PR envach (meldowanie
wyłączone), nigdy wartość w repo.

**Contract**: trzy deklaracje `preserve()`; nazwa poza wzorcem `TRIGGER_*` i listą `ignore`;
`railway config plan` bez zmian destrukcyjnych.

#### 5. Wytyczne repozytorium

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Punkt `tools/healthchecks/` w strukturze, pakiet `alerts` w opisie API, job
`healthchecks-test` w liście CI i punkt w „Deployment” o meldunkach i kanale alarmowym.

**Contract**: sekcje „Project Structure & Module Organization” i „Deployment”.

### Success Criteria:

#### Automated Verification:

- Testy skryptu przechodzą: `tools/healthchecks/test.sh`
- `shellcheck tools/healthchecks/*.sh` bez błędów
- Plan IaC dla PR env bez zmian destrukcyjnych i zielona bramka: job `railway-config` w CI
- CI na PR zielone, w tym `healthchecks-test`

#### Manual Verification:

- Fixture'y nagrane z prawdziwego API zarządzania (na koncie operatora), bez klucza i adresów pingów w treści
- `railway config plan --json` pokazuje `HEALTHCHECK_PING_URL` jako `preserve` na trzech usługach

**Implementation Note**: Po zielonym CI zatrzymaj się przed weryfikacją w PR env.

---

## Phase 4: Weryfikacja w PR env i dokumentacja

### Overview

Prawdziwe meldunki i wiadomości w Telegramie sprawdzone na testowych checkach PR env tej zmiany;
runbook uruchomienia na produkcji i aktualizacja dokumentów fundamentu.

### Changes Required:

#### 1. Runbook F-04

**File**: `context/deployment/deploy-plan.md`

**Intent**: Nowa sekcja `F-04` w stylu S-05/F-01: jak działa, checki (nazwa, harmonogram, strefa,
karencja), zmienne, weryfikacja w PR env, runbook po merge'u, fałszywy alarm i awaria healthchecks.io,
zmiana `TRIGGER_TIMEOUT` / `BACKUP_TIMEOUT` (wymaga `checks.sh apply` z nową karencją). Przy okazji
poprawia nieaktualne zdanie „No manual trigger” w sekcji F-01 (`deploy-plan.md:296`): crona da się
uruchomić ręcznie z panelu (`deploy-plan.md:1155`).

**Contract**: runbook po merge'u (poza Progress): 1) `checks.sh apply production` i potwierdzenie,
że oba checki cronów mają stan „up” (uzbrojone); 2) `railway variable
set HEALTHCHECK_PING_URL … --skip-deploys` na trzech usługach i zapieczętowanie; 3) jeden redeploy
cronów i API (cron czyta zmienne przy tworzeniu deploymentu); 4) `send-test-alert` na produkcji przez
`railway ssh` (po obudzeniu API, jak przy `create-user`); 5) pierwsza noc: w panelu healthchecks.io
`start` + `0` obu cronów, brak wiadomości w Telegramie; 6) komentarz w issue F-04 i zamknięcie.

#### 2. Dokumenty fundamentu

**File**: `context/foundation/prd.md`, `context/foundation/infrastructure.md`,
`context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: PRD: `> Doprecyzowanie (F-04)` pod NFR o sygnale awarii przebiegu (co alarmuje, kanał,
co zostaje dla S-13/S-18). Infrastruktura: healthchecks.io i Telegram jako usługi zewnętrzne ($0, sekret
adresu, alarmy przez @HealthchecksBot). Roadmapa: opis F-04 (kanał = healthchecks.io → Telegram, meldunki
cronów, nadawca w API), notatki w S-13 i S-18, odblokowanie zależności; potem dane w
`tools/render_roadmap_svg.py` i `python3 tools/render_roadmap_svg.py`.

**Contract**: wpisy edytowane w miejscu; status F-04 zmieniają skille (`/10x-implement`, `/10x-archive`).

#### 3. Weryfikacja w PR env

**File**: brak zmian w kodzie — kroki w sekcji F-04 runbooka (część „weryfikacja w PR env”)

**Intent**: Operator zakłada testowe checki `checks.sh apply pr-<N>` (uzbrojone, stan „up”), ustawia
ich adresy ręcznie na trzech usługach PR env oraz `TRIGGER_IN_ANY_ENVIRONMENT=true` i
`BACKUP_IN_ANY_ENVIRONMENT=true`, redeployuje crony i uruchamia ich przebiegi ręcznie z panelu
(harmonogramy cronów bez zmian). Scenariusz „brak meldunku” zmienia tylko harmonogram testowego checka:
`checks.sh apply pr-<N> --schedule '<najbliższe minuty>' --grace 60` bez uruchamiania crona. Potem
sprząta: `checks.sh delete pr-<N>`, usunięcie zmiennych, redeploy cronów.

**Contract**: zmienne `*_IN_ANY_ENVIRONMENT` są na liście `ignore`, `HEALTHCHECK_PING_URL` jest
`preserve()` — ręczne ustawienie nie blokuje IaC.

### Success Criteria:

#### Automated Verification:

- CI na PR zielone po zmianach dokumentacji

#### Manual Verification:


- PR env przed ręcznym ustawieniem nie ma `HEALTHCHECK_PING_URL` na żadnej z trzech usług (produkcyjna wartość się nie skopiowała)
- Udany przebieg `pipeline-trigger` w PR env daje w panelu `start` i `0` z linią `OK:`, bez wiadomości w Telegramie
- Nieudany przebieg `pipeline-trigger` (np. zły token) daje w Telegramie „down” z linią `FAILED:`, a kolejny udany — „up”
- Uzbrojony check (stan „up”) bez meldunku po swoim harmonogramie i karencji daje w Telegramie „down” z powodem „nie przyszedł na czas”
- Przebieg `db-backup` w PR env daje w panelu `start` i `0`
- `send-test-alert` na API PR env daje w Telegramie „down” z opisem zdarzenia testowego i zaraz „up”, a API zasypia potem jak zwykle
- Po weryfikacji testowe checki usunięte, a zmienne PR env przywrócone (harmonogramy cronów bez zmian)
- Runbook F-04 przeczytany przez operatora i zgodny z tym, co zrobiono w PR env
- Dane F-04 i data „stan” w `tools/render_roadmap_svg.py` zgodne z `roadmap.md`, a `docs/visuals/roadmap.svg` wygenerowane na nowo (skrypt nie czyta roadmapy, dane są wpisane ręcznie)

**Implementation Note**: Kroki produkcyjne wykonuje operator po merge'u według runbooka; nie należą do Progress.

---

## Testing Strategy

### Unit Tests:

- API: nadawca na lokalnym `HttpServer` (kolejność `/fail` → sukces, przycięcie treści, błędy bez
  wyjątku, brak adresu w logach i komunikatach), ustawienie (brak/pusty/zły bez echa), komenda (kody).
- Skrypt checków: stub `curl`, nagrane odpowiedzi API zarządzania.

### Integration Tests:

- `ApplicationContextRunner` dla beana nadawcy; `./mvnw -B verify` (bez nowych `*IT` w bazie).
- `tools/pipeline-trigger/test.sh` i `tools/db-backup/test.sh` w Dockerze ze stubem meldunków.

### Manual Testing Steps:

1. Testowe checki w PR env (uzbrojone), przebiegi cronów uruchamiane ręcznie z panelu: sukces, porażka, brak meldunku, backup, `send-test-alert` (Phase 4).
2. Po merge'u: runbook F-04 i pierwsza noc na produkcji (poza Progress).

## Performance Considerations

Meldunek to jedno krótkie żądanie wychodzące; cron płaci sekundy, API wysyła tylko w trakcie własnej
pracy. Limity healthchecks.io: ≤5 pingów na minutę na check (zdarzenie API = 2 pingi, więc ≤2 zdarzenia
na minutę; nadmiar przepada bez wpływu na wywołującego), Telegram ≤6 wiadomości na minutę na czat.

## Migration Notes

Bez migracji bazy i bez nowych tabel (`tools/db-backup/tables.sh` bez zmian). Wycofanie: usunięcie
`HEALTHCHECK_PING_URL` wyłącza meldowanie bez redeployu kodu (po redeployu usługi); checki można
wstrzymać w panelu.

## References

- Frame: `context/changes/out-of-app-alert-channel/frame.md`
- `tools/pipeline-trigger/trigger.sh:21-25,45-54,76-96`, `tools/pipeline-trigger/test.sh:57-154,194`
- `tools/db-backup/backup.sh:14-16,33-49`, `tools/db-backup/Dockerfile:8`, `tools/db-backup/test.sh:45-51,103-109`
- `.railway/railway.ts:156-161,235,277,291-292,307-314`; `.github/workflows/ci.yml:209-257`
- `szczur-radar-api/.../internal/PipelineTriggerToken.java:38-49`, `.../sources/wykop/WykopApiClient.java:28-33,139-141`,
  `.../cli/CommandType.java`, `.../cli/CommandLine.java:32-35`, `src/test/.../sources/wykop/LocalWykopServer.java`
- `context/deployment/deploy-plan.md:50,95,361,467,521-525,1155` (sekcje S-05, F-01, S-14)
- healthchecks.io: `/docs/http_api/`, `/docs/configuring_checks/`, `/docs/api/`, `/docs/bash/`;
  źródło `hc/api/models.py:493-575`, `hc/integrations/telegram/templates/telegram_message.html`
- `deploy-verification-hardening` (osobna zmiana): `context/changes/deploy-verification-hardening/plan.md:91-98`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Meldunki cronów

#### Automated

- [x] 1.1 Testy triggera przechodzą: `tools/pipeline-trigger/test.sh`
- [x] 1.2 Testy backupu przechodzą: `tools/db-backup/test.sh`
- [x] 1.3 Po testach nie zostają kontenery ani sieci testów

#### Manual

- [ ] 1.4 Przegląd: adres meldunku nie trafia do logu, `ps` ani komunikatu w żadnej ścieżce obu skryptów

### Phase 2: Zgłaszanie zdarzeń z API

#### Automated

- [ ] 2.1 Testy jednostkowe przechodzą: `./mvnw -B test`
- [ ] 2.2 Pełna weryfikacja przechodzi: `./mvnw -B verify`

#### Manual

- [ ] 2.3 Lokalnie `send-test-alert` bez zmiennej kończy się kodem 2 z czytelnym komunikatem

### Phase 3: Checki jako kod i IaC

#### Automated

- [ ] 3.1 Testy skryptu przechodzą: `tools/healthchecks/test.sh`
- [ ] 3.2 `shellcheck tools/healthchecks/*.sh` bez błędów
- [ ] 3.3 Plan IaC dla PR env bez zmian destrukcyjnych i zielona bramka `railway-config`
- [ ] 3.4 CI na PR zielone, w tym `healthchecks-test`

#### Manual

- [ ] 3.5 Fixture'y nagrane z prawdziwego API zarządzania, bez klucza i adresów pingów
- [ ] 3.6 `railway config plan --json` pokazuje `HEALTHCHECK_PING_URL` jako `preserve` na trzech usługach

### Phase 4: Weryfikacja w PR env i dokumentacja

#### Automated

- [ ] 4.1 CI na PR zielone po zmianach dokumentacji

#### Manual

- [ ] 4.3 PR env bez `HEALTHCHECK_PING_URL` przed ręcznym ustawieniem (produkcyjna wartość się nie skopiowała)
- [ ] 4.4 Udany `pipeline-trigger` daje `start` i `0` z linią `OK:`, bez wiadomości w Telegramie
- [ ] 4.5 Nieudany `pipeline-trigger` daje „down” z linią `FAILED:`, kolejny udany — „up”
- [ ] 4.6 Uzbrojony check bez meldunku po harmonogramie i karencji daje „down” z powodem „nie przyszedł na czas”
- [ ] 4.7 `db-backup` w PR env daje `start` i `0`
- [ ] 4.8 `send-test-alert` w PR env daje „down” z opisem i zaraz „up”, API potem zasypia
- [ ] 4.9 Testowe checki usunięte, zmienne PR env przywrócone, harmonogramy cronów bez zmian
- [ ] 4.10 Runbook F-04 zgodny z tym, co zrobiono w PR env
- [ ] 4.11 Dane F-04 i data w `render_roadmap_svg.py` zgodne z `roadmap.md`, SVG wygenerowane na nowo
