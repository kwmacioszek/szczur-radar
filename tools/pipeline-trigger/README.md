# pipeline-trigger

Codzienny przebieg pipeline'u Szczur Radar (S-05, `daily-scheduled-run-freshness`). Serwis cron `pipeline-trigger` w Railway codziennie o 00:30 UTC budzi API przez sieć prywatną i zleca przebieg dzienny. Potem czeka na jego wynik i kończy się kodem wyjścia, który go opisuje. Nieudany przebieg crona Railway oznacza jako `CRASHED` i wysyła e-mail.

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

0. **Straż środowiska.** Poza produkcją skrypt nic nie robi. Jeśli `RAILWAY_ENVIRONMENT_NAME` jest ustawione i różne od `production`, zapisuje „skipped” i kończy się kodem 0 bez żadnego żądania. PR env dostaje ten cron jako kopia produkcji, ale nie potrzebuje nocnego przebiegu, a bez tokenu na API wysyłałby co noc e-mail o porażce. Wyjątek: `TRIGGER_IN_ANY_ENVIRONMENT=true` na serwisie, do sprawdzenia narzędzia w PR env. Lokalnie (`test.sh`, ręcznie) zmiennej nie ma, więc przebieg rusza.
1. **Limit czasu całości.** Cały przebieg ma twardy limit `TRIGGER_TIMEOUT` (domyślnie `3h`, więcej niż suma limitów niżej). Zawieszony przebieg kończy się więc porażką (`FAILED: the run exceeded …`), zamiast blokować kolejne noce: Railway pomija przebieg cron, dopóki poprzedni trwa.
2. **Zmienne.** Wymagane są `API_PRIVATE_DOMAIN`, `API_PORT` i `PIPELINE_TRIGGER_TOKEN`. Brak którejś daje kod 2 z nazwą zmiennej, bez wartości. Zła wartość czasu też daje kod 2.
3. **Start.** `POST http://<API_PRIVATE_DOMAIN>:<API_PORT>/internal/pipeline-runs/daily` z nagłówkiem `Authorization: Bearer <token>`, bez treści. Żądanie idzie wprost do API przez sieć prywatną, z pominięciem Caddy (ten przekazuje tylko `/api/*`).
   - Brak połączenia albo 502/503/504 (poza 503 `source_not_configured`): API się budzi (uśpione odpowiada tak przez ok. 10 s) albo sieć prywatna kontenera crona nie jest jeszcze gotowa. Ponowienie co 2 s, najwyżej przez `TRIGGER_WAKE_TIMEOUT` (domyślnie `3m`).
   - 409 `pipeline_busy`: trwa albo czeka inny przebieg, np. admina. Ponowienie co `TRIGGER_BUSY_INTERVAL` (domyślnie `60s`), najwyżej przez `TRIGGER_BUSY_TIMEOUT` (domyślnie `60m`).
   - 401 `not_authenticated` (brak tokenu na API albo inny token), 503 `source_not_configured` i każde inne 4xx: od razu kod 1 z kodem błędu z odpowiedzi.
   - 202: lista utworzonych przebiegów w kolejności wykonania (`[{"id", "kind"}]`).
4. **Czekanie.** Każdy przebieg z odpowiedzi 202 jest odpytywany (`GET /internal/pipeline-runs/{id}`) co `TRIGGER_POLL_INTERVAL` (domyślnie `30s`), aż osiągnie status końcowy. Limit `TRIGGER_RUN_TIMEOUT` (domyślnie `90m`) liczy się od odpowiedzi 202 i obejmuje wszystkie przebiegi razem. Brak połączenia albo 502/503/504 w trakcie czekania jest ponawiany najwyżej przez `TRIGGER_WAKE_TIMEOUT`. Każda inna odpowiedź niż 200 albo nieznany status kończy przebieg kodem 1.
5. **Wynik.** Kod 0, gdy każdy przebieg jest `succeeded` albo `partial`. Kod 1, gdy któryś jest `failed` albo `interrupted`, a także przy przekroczeniu czasu albo błędzie odpytywania. `partial` się liczy: jego nieudane posty ponawia kolejny przebieg i widać je w `/admin`. Skrypt czeka na wszystkie przebiegi, także gdy pierwszy się nie udał, więc log ma status każdego.

Token trafia do `curl` jako konfiguracja na standardowym wejściu, nigdy jako argument (widoczny w `ps`). Skrypt nie używa `set -x` i nigdy nie wypisuje tokenu. Białe znaki na początku i końcu tokenu są obcinane, tak jak robi to API.

## Kody wyjścia

| Kod | Znaczenie |
| --- | --- |
| `0` | każdy przebieg `succeeded` albo `partial`, albo przebieg pominięty poza produkcją |
| `1` | przebieg `failed` albo `interrupted`, odmowa API (401, 503 `source_not_configured`, inne 4xx), przekroczony limit czasu, błąd odpytywania |
| `2` | błąd konfiguracji: brak zmiennej albo zła wartość |

## Logi

Jedna linia na zdarzenie, z czasem UTC:

```text
2026-10-13T00:30:04Z trigger: starting the daily run at http://szczur-radar-api.railway.internal:8000/internal/pipeline-runs/daily
2026-10-13T00:30:04Z trigger: waking the API: no answer yet (curl: (7) Failed to connect to …); retrying for up to 3m
2026-10-13T00:30:15Z trigger: the API answers (after 11s)
2026-10-13T00:30:15Z trigger: started: range 4f0c…
2026-10-13T00:30:15Z trigger: range 4f0c…: queued
2026-10-13T00:30:45Z trigger: range 4f0c…: running
2026-10-13T00:52:15Z trigger: range 4f0c…: succeeded (fetched 270, new posts 251, assessed ok 249, assessed failed 2, processing errors 0, dead 0)
2026-10-13T00:52:15Z trigger: OK: range succeeded
```

Liczniki znaczą to samo co na liście przebiegów w `/admin`. Porażka kończy się linią `FAILED: …` z przyczyną.

## Zmienne

| Zmienna | Znaczenie |
| --- | --- |
| `API_PRIVATE_DOMAIN` | prywatna nazwa API (w Railway referencja do `RAILWAY_PRIVATE_DOMAIN` serwisu `szczur-radar-api`) |
| `API_PORT` | port API w sieci prywatnej (w Railway `8000`, `API_INTERNAL_PORT` w `.railway/railway.ts`) |
| `PIPELINE_TRIGGER_TOKEN` | wspólny sekret z API (w Railway referencja do zmiennej API o tej nazwie) |
| `TRIGGER_WAKE_TIMEOUT` | jak długo ponawiać, gdy API nie odpowiada, domyślnie `3m` |
| `TRIGGER_BUSY_TIMEOUT`, `TRIGGER_BUSY_INTERVAL` | jak długo i jak często ponawiać przy 409, domyślnie `60m` i `60s` |
| `TRIGGER_POLL_INTERVAL` | co ile odpytywać przebiegi, domyślnie `30s` |
| `TRIGGER_RUN_TIMEOUT` | jak długo czekać na koniec wszystkich przebiegów, domyślnie `90m` |
| `TRIGGER_TIMEOUT` | limit czasu całego przebiegu crona, domyślnie `3h` |
| `RAILWAY_ENVIRONMENT_NAME`, `TRIGGER_IN_ANY_ENVIRONMENT` | środowisko Railway (ustawia je Railway); poza `production` przebieg jest pomijany, chyba że `TRIGGER_IN_ANY_ENVIRONMENT=true` |

Czasy to dodatnia liczba całkowita z jednostką `s`, `m` albo `h`; liczba bez jednostki to sekundy.

W Railway serwis i jego zmienne deklaruje `.railway/railway.ts`. Token jest wartością tylko na API: na produkcji zapieczętowaną, ustawioną przez operatora przez `--stdin`. Cron dostaje go referencją. Zmienna ustawiona ręcznie na serwisie musi być zadeklarowana w `railway.ts` przez `preserve()`. Inaczej plan IaC proponuje jej usunięcie, a bramka CI (`railway-config`) go odrzuca. Dziś dotyczy to tylko `TRIGGER_IN_ANY_ENVIRONMENT`. Pozostałe `TRIGGER_*` zostają na wartościach domyślnych.

## Test lokalny

```bash
tools/pipeline-trigger/test.sh
```

Wymaga Dockera. Test buduje obraz i dla każdego przypadku uruchamia go w sieci Dockera obok atrapy API. Atrapa to mały serwer HTTP w Pythonie (`python:3.14-alpine`) o skryptowanych odpowiedziach. Sprawdza:
- użytkownika innego niż root;
- pominięcie poza produkcją (kod 0, żadnego żądania) i przebieg w PR env z `TRIGGER_IN_ANY_ENVIRONMENT=true`;
- `succeeded` i `partial` → kod 0, `failed` → kod 1;
- nieudany RETRY i udany RANGE → kod 1, oba przebiegi doczekane;
- odmowę połączenia przez pierwsze sekundy, potem 503 bez treści, potem 202 → kod 0;
- 409, potem 202 → kod 0;
- 401 i 503 `source_not_configured` → kod 1 po jednym żądaniu;
- każdy limit czasu (`TRIGGER_WAKE_TIMEOUT`, `TRIGGER_BUSY_TIMEOUT`, `TRIGGER_RUN_TIMEOUT`, `TRIGGER_TIMEOUT`) → kod 1;
- brak `PIPELINE_TRIGGER_TOKEN` → kod 2;
- brak tokenów w logach.

CI uruchamia ten test przy każdym PR i pushu na `main` (job `pipeline trigger test`). Wypisuje też log udanego przebiegu, czyli to, co Railway pokazuje dla przebiegu cron. Kontenery i sieć testu są zawsze usuwane.

## PR env

Cron jest w każdym PR env, ale tam się pomija. Żeby sprawdzić go w PR env `szczur-radar-pr-<N>`:

1. Ustaw token na API i wdroż je ponownie:
   ```bash
   openssl rand -hex 32 | railway variable set PIPELINE_TRIGGER_TOKEN --stdin -s szczur-radar-api -e szczur-radar-pr-<N> --skip-deploys
   railway redeploy -s szczur-radar-api -e szczur-radar-pr-<N> -y
   ```
2. Na serwisie `pipeline-trigger` ustaw `TRIGGER_IN_ANY_ENVIRONMENT=true`. W panelu ustaw tymczasowy harmonogram (Settings → Cron Schedule, np. `*/5 * * * *`, minimum 5 minut). Railway nie ma ręcznego wyzwolenia crona. Kolejny push przywraca harmonogram z IaC.
3. Log wykonania kończy się `OK: …` albo `FAILED: …`, a deployment crona ma status `SUCCESS` albo `CRASHED`. Przebieg widać w `/admin` PR env.
4. Po sprawdzeniu usuń `TRIGGER_IN_ANY_ENVIRONMENT` (`preserve()` niczego nie tworzy, więc plan tego nie cofnie).

Na produkcji tymczasowy harmonogram trzeba przywrócić na `30 0 * * *` ręcznie: do następnego merge'a nic go nie przywróci.
