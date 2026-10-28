# pipeline-trigger

Codzienny przebieg pipeline'u Szczur Radar (S-05, `daily-scheduled-run-freshness`). Serwis cron `pipeline-trigger` w Railway codziennie o 00:30 UTC budzi API przez sieć prywatną i zleca przebieg dzienny. Potem czeka na jego wynik i kończy się kodem wyjścia, który go opisuje. Nieudany przebieg crona Railway oznacza jako `CRASHED` i wysyła powiadomienie (w aplikacji i push), ale tylko gdy konto Railway ma własną regułę powiadomień „deployment crashed” dla wszystkich środowisk (Account Settings). Bez niej porażka jest cicha: w teście w PR env (2026-10-12) nie przyszedł ani e-mail, ani powiadomienie.

Przebieg dzienny obejmuje dni D-2 i D-1 według kalendarza warszawskiego, ze wszystkich skonfigurowanych źródeł. Jeśli zostały posty z wcześniejszych przebiegów, najpierw idzie przebieg RETRY. API startuje go tym samym kodem co przycisk admina, a zleceniodawcą jest konto systemowe `scheduler@system.invalid`. Trasy wywoływane przez crona są w `szczur-radar-api` (`pipeline/api/InternalPipelineRunController.java`).

O 00:30 UTC w Warszawie jest 01:30 (zima) albo 02:30 (lato), czyli zawsze po północy, więc D-1 jest zamkniętym dniem. Zwykły przebieg kończy się przed kopią bazy o 02:00 UTC (`tools/db-backup`). Jeśli potrwa dłużej, kopia i tak jest spójna, bo `pg_dump` czyta jeden snapshot.

## Pliki

| Plik | Co robi |
| --- | --- |
| `Dockerfile` | `alpine:3.24` + `bash`, `curl`, `jq`, `coreutils` (GNU `timeout`). Użytkownik `trigger`, nie root. `ENTRYPOINT` to `trigger.sh` |
| `trigger.sh` | jeden przebieg crona (niżej) |
| `test.sh` | lokalny test w Dockerze przeciw atrapie API, bez Railway |

Komenda startu Railway nie rozwija zmiennych, więc cała logika jest w skrypcie obrazu, tak jak w `tools/db-backup`.

## Przebieg `trigger.sh`

0. **Straż środowiska.** Poza produkcją skrypt nic nie robi. Jeśli `RAILWAY_ENVIRONMENT_NAME` jest ustawione i różne od `production`, zapisuje „skipped” i kończy się kodem 0 bez żadnego żądania. PR env dostaje ten cron jako kopia produkcji, ale nie potrzebuje nocnego przebiegu, a bez tokenu na API co noc kończyłby się porażką i powiadomieniem. Wyjątek: `TRIGGER_IN_ANY_ENVIRONMENT=true` na serwisie, do sprawdzenia narzędzia w PR env. Lokalnie (`test.sh`, ręcznie) zmiennej nie ma, więc przebieg rusza.
1. **Limit czasu całości.** Cały przebieg ma twardy limit `TRIGGER_TIMEOUT` (domyślnie `3h`, więcej niż suma limitów niżej). Zawieszony przebieg kończy się więc porażką (`FAILED: the run exceeded …`), zamiast blokować kolejne noce: Railway pomija przebieg cron, dopóki poprzedni trwa.
   - **Meldunki** (F-04, `out-of-app-alert-channel`): z ustawionym `HEALTHCHECK_PING_URL` proces pilnujący limitu czasu melduje się do healthchecks.io. Przed przebiegiem wysyła `POST <adres>/start`, a po nim `POST <adres>/<kod wyjścia>` (`0`, `1` albo `2`; przekroczony limit czasu to `1`) z ostatnią niepustą linią logu przebiegu w treści (`text/plain`, najwyżej 1000 znaków), np. `… trigger: OK: range succeeded`. Healthchecks.io alarmuje w Telegramie, gdy koniec nie przyjdzie w karencji checka albo kod nie jest `0`. Meldunek idzie z tego procesu, bo tylko on widzi każdy wynik, także przekroczony limit czasu: jego meldunek idzie przed wyjściem. Przebieg pominięty poza produkcją (krok 0) nie melduje się wcale, a bez zmiennej nic nie jest wysyłane. Log przebiegu w Railway jest taki sam jak bez meldunków.
   - Każdy meldunek to jedno `curl -fsS -m 10 --retry 3`. Jego porażka (błąd HTTP, brak sieci) daje tylko linię `warning: check-in … failed (…)`. Adres jest sekretem (kto go zna, może się meldować): trafia do `curl` jako konfiguracja na standardowym wejściu, nigdy jako argument, i nie pojawia się w logu. Adres spoza `https://` albo z `?` lub `#` (zapytanie połknęłoby `/start` i `/<kod>`, a API też go odrzuca) daje ostrzeżenie `HEALTHCHECK_PING_URL is not an https:// address without ? or #; no check-in` i brak meldunku, a uzbrojony check zaalarmuje po karencji. Bez katalogu tymczasowego (pełny albo tylko do odczytu `/tmp`) meldunek końca idzie bez ostatniej linii logu, z ostrzeżeniem; kod wyjścia się nie zmienia.
2. **Zmienne.** Wymagane są `API_PRIVATE_DOMAIN`, `API_PORT` i `PIPELINE_TRIGGER_TOKEN`. Brak którejś daje kod 2 z nazwą zmiennej, bez wartości. Zła wartość czasu też daje kod 2.
3. **Start.** `POST http://<API_PRIVATE_DOMAIN>:<API_PORT>/internal/pipeline-runs/daily` z nagłówkiem `Authorization: Bearer <token>`, bez treści. Żądanie idzie wprost do API przez sieć prywatną, z pominięciem Caddy (ten przekazuje tylko `/api/*`).
   - Brak połączenia albo 502/503/504 (poza 503 `source_not_configured`): API się budzi (uśpione odpowiada tak przez ok. 10 s) albo sieć prywatna kontenera crona nie jest jeszcze gotowa. Ponowienie co 2 s, najwyżej przez `TRIGGER_WAKE_TIMEOUT` (domyślnie `3m`). Ponawiane jest tylko żądanie, które nie nawiązało połączenia.
   - Brak odpowiedzi mimo nawiązanego połączenia (np. po `TRIGGER_REQUEST_TIMEOUT`, domyślnie `60s`): API mogło już zakolejkować przebieg, więc drugi `POST` czekałby na niego jak na cudzy i uruchomiłby kolejny, a wynik pierwszego nie trafiłby do kodu wyjścia. Od razu kod 1 z komunikatem „the run may have started; check /admin”.
   - 409 `pipeline_busy`: trwa albo czeka inny przebieg, np. admina. Ponowienie co `TRIGGER_BUSY_INTERVAL` (domyślnie `60s`), najwyżej przez `TRIGGER_BUSY_TIMEOUT` (domyślnie `60m`).
   - 401 `not_authenticated` (brak tokenu na API albo inny token), 503 `source_not_configured` i każde inne 4xx: od razu kod 1 z kodem błędu z odpowiedzi.
   - 202: lista utworzonych przebiegów w kolejności wykonania (`[{"id", "kind"}]`).
4. **Czekanie.** Każdy przebieg z odpowiedzi 202 jest odpytywany (`GET /internal/pipeline-runs/{id}`) co `TRIGGER_POLL_INTERVAL` (domyślnie `30s`), aż osiągnie status końcowy. Limit `TRIGGER_RUN_TIMEOUT` (domyślnie `90m`) liczy się od odpowiedzi 202 i obejmuje wszystkie przebiegi razem. Brak połączenia albo 502/503/504 w trakcie czekania jest ponawiany najwyżej przez `TRIGGER_WAKE_TIMEOUT`. Każda inna odpowiedź niż 200 albo nieznany status kończy przebieg kodem 1.
   - **Heartbeat** (S-06): przebieg `running` loguje linię ze statusem, krokiem, postępem oceny i czasem ostatniej aktywności co `TRIGGER_HEARTBEAT_INTERVAL` (domyślnie `5m`). Przebieg `queued` (np. RANGE za RETRY) loguje tylko zmianę statusu: nie ma jeszcze postępu, a heartbeaty daje przebieg przed nim. Pierwsza przychodzi jeden interwał po linii ze zmianą statusu, potem przy pierwszym odpytaniu po każdym kolejnym interwale. Linia powstaje z odpowiedzi tego samego odpytania, więc heartbeat nie dodaje żadnego żądania. Pole, którego API nie podaje (starsza wersja API) albo podaje jako `null`, jest w linii znakiem `?`.
5. **Wynik.** Kod 0, gdy każdy przebieg jest `succeeded` albo `partial`. Kod 1, gdy któryś jest `failed` albo `interrupted`, a także przy przekroczeniu czasu albo błędzie odpytywania. Pusty zakres to `failed` w API (S-08, `empty-run-signal`): przebieg RANGE, w którym źródło nie dało ani jednego posta do zapisu, kończy się błędem `NoPostsFetchedException`, więc cron kończy się kodem 1 tą samą ścieżką. Pominięte elementy przy dobrych postach (`skipped` > 0) nie zmieniają statusu ani kodu wyjścia: dają tylko powiadomienie w aplikacji. `partial` się liczy: jego nieudane posty ponawia kolejny przebieg i widać je w `/admin`. Skrypt czeka na wszystkie przebiegi, także gdy pierwszy się nie udał, więc log ma status każdego. Linia przebiegu `interrupted` kończy się przyczyną przerwania: `(cause deploy)` (przebieg przerwało inne wdrożenie API, np. merge na `main`), `(cause restart)` (API wystartowało ponownie w tym samym wdrożeniu albo wdrożenie jest nieznane) albo `(cause shutdown)` (instancja API się zamykała).

Token trafia do `curl` jako konfiguracja na standardowym wejściu, nigdy jako argument (widoczny w `ps`). Skrypt nie używa `set -x` i nigdy nie wypisuje tokenu. Białe znaki na początku i końcu tokenu są obcinane, tak jak robi to API.

## Kody wyjścia

| Kod | Znaczenie |
| --- | --- |
| `0` | każdy przebieg `succeeded` albo `partial`, albo przebieg pominięty poza produkcją |
| `1` | przebieg `failed` (także pusty zakres: źródło nie dało posta do zapisu) albo `interrupted`, odmowa API (401, 503 `source_not_configured`, inne 4xx), przekroczony limit czasu, błąd odpytywania |
| `2` | błąd konfiguracji: brak zmiennej albo zła wartość |

Meldunek do healthchecks.io nigdy nie zmienia kodu wyjścia: jego porażka daje tylko ostrzeżenie w logu, a kod jest taki sam jak bez meldunku.

## Logi

Jedna linia na zdarzenie, z czasem UTC:

```text
2026-10-13T00:30:04Z trigger: starting the daily run at http://szczur-radar-api.railway.internal:8000/internal/pipeline-runs/daily
2026-10-13T00:30:04Z trigger: waking the API: no answer yet (curl: (7) Failed to connect to …); retrying for up to 3m
2026-10-13T00:30:15Z trigger: the API answers (after 11s)
2026-10-13T00:30:15Z trigger: started: range 4f0c…
2026-10-13T00:30:15Z trigger: range 4f0c…: queued
2026-10-13T00:30:45Z trigger: range 4f0c…: running
2026-10-13T00:35:45Z trigger: range 4f0c…: running, step assess, assessed 38/251 (ok 38, failed 0, errors 0), last activity 2026-10-13T00:35:44.912Z
2026-10-13T00:40:45Z trigger: range 4f0c…: running, step assess, assessed 97/251 (ok 96, failed 1, errors 0), last activity 2026-10-13T00:40:43.207Z
…
2026-10-13T00:52:15Z trigger: range 4f0c…: succeeded (fetched 270, skipped 0, new posts 251, assessed ok 249, assessed failed 2, processing errors 0, dead 0)
2026-10-13T00:52:15Z trigger: OK: range succeeded
```

Liczniki znaczą to samo co na liście przebiegów w `/admin`. `skipped` to pobrane elementy, z których nie powstał post; API bez tego pola (starsza wersja) daje `skipped ?`. W linii heartbeatu `assessed 97/251` to posty już ocenione (udane i nieudane) z tych, które wziął krok `assess`, `errors` to błędy techniczne, a `last activity` to ostatnia zmiana w przebiegu według API (UTC). Przed krokiem `assess` jest `assessed 0/?`, a między krokami `step ?`. Porażka kończy się linią `FAILED: …` z przyczyną, np. przebieg przerwany wdrożeniem API:

```text
2026-10-13T00:45:45Z trigger: range 4f0c…: running, step assess, assessed 160/251 (ok 159, failed 1, errors 0), last activity 2026-10-13T00:45:41.380Z
2026-10-13T00:47:15Z trigger: range 4f0c…: interrupted (fetched 270, skipped 0, new posts 251, assessed ok 171, assessed failed 1, processing errors 0, dead 0) (cause deploy)
2026-10-13T00:47:15Z trigger: FAILED: a run did not succeed: range interrupted
```

albo pusty zakres (źródło nie dało posta do zapisu; błąd przebiegu widać na jego stronie w `/admin`):

```text
2026-10-13T00:31:15Z trigger: range 4f0c…: failed (fetched 0, skipped 0, new posts 0, assessed ok 0, assessed failed 0, processing errors 0, dead 0)
2026-10-13T00:31:15Z trigger: FAILED: a run did not succeed: range failed
```

Skrypt pisze logi na stderr, a Railway pokazuje stderr crona na poziomie `error`, więc w widoku logów każda linia jest czerwona, także w udanym przebiegu. O wyniku mówi kod wyjścia (status deploymentu), nie kolor.

## Zmienne

| Zmienna | Znaczenie |
| --- | --- |
| `API_PRIVATE_DOMAIN` | prywatna nazwa API (w Railway referencja do `RAILWAY_PRIVATE_DOMAIN` serwisu `szczur-radar-api`) |
| `API_PORT` | port API w sieci prywatnej (w Railway `8000`, `API_INTERNAL_PORT` w `.railway/railway.ts`) |
| `PIPELINE_TRIGGER_TOKEN` | wspólny sekret z API (w Railway referencja do zmiennej API o tej nazwie) |
| `TRIGGER_WAKE_TIMEOUT` | jak długo ponawiać, gdy API nie odpowiada, domyślnie `3m` |
| `TRIGGER_BUSY_TIMEOUT`, `TRIGGER_BUSY_INTERVAL` | jak długo i jak często ponawiać przy 409, domyślnie `60m` i `60s` |
| `TRIGGER_POLL_INTERVAL` | co ile odpytywać przebiegi, domyślnie `30s` |
| `TRIGGER_HEARTBEAT_INTERVAL` | co ile logować heartbeat trwającego przebiegu (krok i postęp), domyślnie `5m`; linia przychodzi przy pierwszym odpytaniu po upływie interwału, więc nie częściej niż `TRIGGER_POLL_INTERVAL` |
| `TRIGGER_RUN_TIMEOUT` | jak długo czekać na koniec wszystkich przebiegów, domyślnie `90m` |
| `TRIGGER_REQUEST_TIMEOUT` | limit jednego żądania do API, domyślnie `60s` |
| `TRIGGER_TIMEOUT` | limit czasu całego przebiegu crona, domyślnie `3h`. Karencja checka healthchecks.io wynika z tego limitu: jego zmiana wymaga `tools/healthchecks/checks.sh apply` z nową karencją, inaczej długi przebieg da fałszywy alarm |
| `HEALTHCHECK_PING_URL` | opcjonalny adres meldunków checka healthchecks.io (`https://…`, F-04); bez niego cron się nie melduje. Sekret: nigdy w logu ani w argumentach `curl` |
| `RAILWAY_ENVIRONMENT_NAME`, `TRIGGER_IN_ANY_ENVIRONMENT` | środowisko Railway (ustawia je Railway); poza `production` przebieg jest pomijany, chyba że `TRIGGER_IN_ANY_ENVIRONMENT=true` |

Czasy to dodatnia liczba całkowita z jednostką `s`, `m` albo `h`; liczba bez jednostki to sekundy.

W Railway serwis i jego zmienne deklaruje `.railway/railway.ts`. Token jest wartością tylko na API: na produkcji zapieczętowaną, ustawioną przez operatora przez `--stdin`. Cron dostaje go referencją. Zmienne strojenia `TRIGGER_*` (także `TRIGGER_IN_ANY_ENVIRONMENT`) można ustawiać ręcznie na serwisie: `railway.ts` je ignoruje (wzorzec `pipeline-trigger/TRIGGER_*` w `variables.ignore` projektu, S-14), więc IaC ich nie ustawia i nie usuwa. Każda inna zmienna ustawiona ręcznie na serwisie wymaga dopisania wzorca do `variables.ignore` albo deklaracji w `railway.ts`. Inaczej plan IaC proponuje jej usunięcie (`variable.delete`), a bramka CI (`railway-config`) go odrzuca.

## Test lokalny

```bash
tools/pipeline-trigger/test.sh
```

Wymaga Dockera i `openssl` (jednorazowy certyfikat atrapy). Test buduje obraz i dla każdego przypadku uruchamia go w sieci Dockera obok atrapy API. Atrapa to mały serwer HTTP w Pythonie (`python:3.14-alpine`) o skryptowanych odpowiedziach; meldunki przyjmuje po https jak healthchecks.io, a przypadki z meldunkami używają obrazu, który ufa jej certyfikatowi. Sprawdza:
- użytkownika innego niż root;
- pominięcie poza produkcją (kod 0, żadnego żądania) i przebieg w PR env z `TRIGGER_IN_ANY_ENVIRONMENT=true`;
- `succeeded` i `partial` → kod 0, `failed` → kod 1;
- nieudany RETRY i udany RANGE → kod 1, oba przebiegi doczekane;
- `interrupted` → kod 1 i przyczyna w logu (`cause deploy`);
- pusty zakres (`empty`: `failed` z `fetched 0, skipped 0`) → kod 1 i liczniki w logu;
- pominięte przy dobrych postach (`skipped-warning`: `succeeded` ze `skipped 3`) → kod 0 i `skipped 3` w logu;
- heartbeat z `TRIGGER_HEARTBEAT_INTERVAL=1s`: krok i postęp (`step assess`, `assessed 3/10`) przy każdym odpytaniu po zmianie statusu, bez dodatkowego żądania; przebieg `queued` bez heartbeatu;
- API bez pól kroku, postępu i `skipped` (starsza wersja) → `?` w heartbeacie, w przyczynie i w `skipped`, bez błędu;
- odmowę połączenia przez pierwsze sekundy, potem 503 bez treści, potem 202 → kod 0;
- 409, potem 202 → kod 0;
- 401 i 503 `source_not_configured` → kod 1 po jednym żądaniu;
- brak odpowiedzi na `POST`, który dotarł do API (`TRIGGER_REQUEST_TIMEOUT`) → kod 1 bez drugiego `POST`;
- każdy limit czasu (`TRIGGER_WAKE_TIMEOUT`, `TRIGGER_BUSY_TIMEOUT`, `TRIGGER_RUN_TIMEOUT`, `TRIGGER_TIMEOUT`) → kod 1;
- brak `PIPELINE_TRIGGER_TOKEN` i zła wartość `TRIGGER_HEARTBEAT_INTERVAL` → kod 2;
- meldunki z `HEALTHCHECK_PING_URL`: `start`, potem `0` z linią `OK:`, `1` z linią `FAILED:` (także po `TRIGGER_TIMEOUT`), `2` przy błędzie konfiguracji; meldunek z odpowiedzią 500 (ponawiany) albo na nieosiągalny adres nie zmienia kodu wyjścia; bez zmiennej, przy pominięciu poza produkcją i z adresem spoza `https://` albo z zapytaniem nie ma żadnego meldunku; bez katalogu tymczasowego kod wyjścia się nie zmienia;
- brak tokenów i klucza meldunków w logach.

CI uruchamia ten test przy każdym PR i pushu na `main` (job `pipeline trigger test`). Wypisuje też log udanego przebiegu, czyli to, co Railway pokazuje dla przebiegu cron. Kontenery i sieć testu są zawsze usuwane.

## PR env

Cron jest w każdym PR env, ale tam się pomija. Żeby sprawdzić go w PR env `szczur-radar-pr-<N>`:

1. Ustaw token na API i wdroż je ponownie:
   ```bash
   openssl rand -hex 32 | railway variable set PIPELINE_TRIGGER_TOKEN --stdin -s szczur-radar-api -e szczur-radar-pr-<N> --skip-deploys
   railway redeploy -s szczur-radar-api -e szczur-radar-pr-<N> -y
   ```
2. Na serwisie `pipeline-trigger` ustaw `TRIGGER_IN_ANY_ENVIRONMENT=true`. W panelu ustaw tymczasowy harmonogram (Settings → Cron Schedule, np. `*/5 * * * *`, minimum 5 minut). Railway nie ma ręcznego wyzwolenia crona. Kolejny push przywraca harmonogram z IaC.
3. Log wykonania kończy się `OK: …` albo `FAILED: …`, a deployment crona ma status `SUCCESS` albo `CRASHED`. Wykonanie z harmonogramu nie tworzy nowego deploymentu, tylko używa istniejącego, więc status deploymentu to wynik ostatniego wykonania. Przebieg widać w `/admin` PR env.
4. Po sprawdzeniu usuń `TRIGGER_IN_ANY_ENVIRONMENT` (IaC ignoruje `TRIGGER_*`, więc plan jej ani nie cofnie, ani nie usunie).

Na produkcji tymczasowy harmonogram trzeba przywrócić na `30 0 * * *` ręcznie: do następnego merge'a nic go nie przywróci.
