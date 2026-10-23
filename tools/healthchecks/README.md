# healthchecks

Checki healthchecks.io kanału alarmowego poza aplikacją (F-04, `out-of-app-alert-channel`). Oba crony (`tools/pipeline-trigger`, `tools/db-backup`) meldują się do swoich checków: `start` przed przebiegiem, potem kod wyjścia z ostatnią linią logu. API wysyła swoje zdarzenia do trzeciego checka (`alerts/HealthchecksAlerts`, komenda `send-test-alert`). Healthchecks.io alarmuje w Telegramie, gdy meldunek nie przyjdzie na czas albo zgłosi porażkę, i drugą wiadomością, gdy check wraca do „up”. Każda usługa dostaje adres swojego checka w zmiennej `HEALTHCHECK_PING_URL`.

`checks.sh` zakłada i aktualizuje checki jednego środowiska przez API zarządzania (v3). Uruchamia go operator na swoim komputerze, nie CI ani Railway: klucz API projektu nie trafia do CI, Railway ani repo.

## Pliki

| Plik | Co robi |
| --- | --- |
| `checks.sh` | `apply` i `delete` checków środowiska (niżej) |
| `test.sh` | lokalny test `checks.sh` z atrapą `curl` w `PATH`, bez healthchecks.io i Dockera |
| `fixtures/*.json` | odpowiedzi API zarządzania dla testu (`fixtures/README.md`: zbudowane według dokumentacji, do zastąpienia nagranymi) |

## Jednorazowo w panelu healthchecks.io

1. Konto w planie Hobbyist (darmowy, 20 checków; Telegram i API zarządzania są w nim bez ograniczeń) i projekt, np. `szczur-radar`. Checki wszystkich środowisk żyją w nim obok siebie, rozróżnione nazwą (`<środowisko>/…`) i tagiem środowiska.
2. Integracja Telegram: Integrations → Telegram. W Telegramie napisz `/start` do @HealthchecksBot (czat prywatny albo grupa, do której go dodasz). Bot odpowiada linkiem; jego otwarcie potwierdza podpięcie czatu do projektu.
3. Klucz API: Settings → API Access → „Create API key” (klucz z prawem zapisu, nie „read-only”). Klucz trzyma operator lokalnie, np. w menedżerze haseł. Nigdy w CI, Railway ani repo, nigdy jako argument polecenia (zostaje w historii powłoki i w `ps`).

`checks.sh` ustawia każdemu checkowi `channels: "*"`, czyli wszystkie integracje projektu, więc integracja Telegram musi istnieć przed `apply`.

## Użycie

```bash
read -rs HEALTHCHECKS_API_KEY && export HEALTHCHECKS_API_KEY   # wklej klucz, bez echa
tools/healthchecks/checks.sh apply production
tools/healthchecks/checks.sh delete szczur-radar-pr-<N>
```

- **`apply <środowisko>`** robi upsert (`POST /api/v3/checks/` z `unique: ["name"]`: istniejący check o tej nazwie jest aktualizowany, nie dublowany) trzech checków z tabeli niżej, z tagami `szczur-radar <środowisko>`. Każdy check crona w stanie „new” dostaje jeden meldunek sukcesu (uzbrojenie): nowy check healthchecks.io nie liczy terminów do pierwszego meldunku, więc bez tego milczałby zawsze. Potem `apply` czyta checki z powrotem (`GET /api/v3/checks/`) i kończy się kodem 1, jeśli któryś check crona nie jest „up” (np. „down” po nieudanej nocy albo „started” w trakcie przebiegu). Dopiero gdy oba są „up”, drukuje na stdout po linii `<nazwa> <adres pingu>` na check. Te adresy to wartości `HEALTHCHECK_PING_URL` (runbook F-04 w `context/deployment/deploy-plan.md`). Ponowny `apply` jest bezpieczny: zmienia tylko ustawienia, adresy zostają te same.
- **`apply <środowisko> --schedule '<cron>' --grace <sekundy>`**: nadpisuje harmonogram i karencję obu checków cronów, np. `--schedule '*/5 * * * *' --grace 60` do sprawdzenia alarmu w PR env. Tylko do weryfikacji: potem `apply` bez opcji albo `delete`. Każdą opcję można podać osobno; check zdarzeń API się nie zmienia.
- **`delete <środowisko>`** usuwa trzy checki środowiska (znalezione po nazwie; brakujący pomija). Do sprzątania po weryfikacji w PR env. `delete production` wyłącza alarmy produkcji.

Środowisko to nazwa środowiska Railway (`[a-z0-9-]+`, np. `production`, `szczur-radar-pr-81`). Log idzie na stderr, z czasem UTC, i nie zawiera klucza ani adresów pingów. Nieoczekiwany argument nie jest powtarzany w komunikacie (mógłby nim być wklejony klucz).

| Kod | Znaczenie |
| --- | --- |
| `0` | checki gotowe (oba checki cronów „up”), albo usunięte |
| `1` | błąd API (zły klucz, błąd HTTP, brak odpowiedzi, nieoczekiwana odpowiedź) albo check crona nie jest „up” |
| `2` | błąd użycia: argumenty, brak `HEALTHCHECKS_API_KEY`, zła nazwa środowiska albo zła wartość opcji |

## Checki

| Nazwa | Rodzaj | Harmonogram | Strefa | Karencja | Alarm braku meldunku |
| --- | --- | --- | --- | --- | --- |
| `<środowisko>/pipeline-trigger` | cron | `30 0 * * *` | UTC | 11700 s (3 h 15 min) | ~03:45 UTC |
| `<środowisko>/db-backup` | cron | `0 2 * * *` | UTC | 8100 s (2 h 15 min) | ~04:15 UTC |
| `<środowisko>/api-events` | simple | okres 31536000 s (365 dni) | — | 31536000 s | nigdy w praktyce |

Harmonogramy są takie same jak `cronSchedule` usług w `.railway/railway.ts`; zmiana jednego wymaga zmiany drugiego i `apply`. Karencja liczy się od meldunku `/start`, więc pokrywa cały limit czasu crona z zapasem 15 min: `TRIGGER_TIMEOUT` (domyślnie `3h`, `tools/pipeline-trigger/README.md`) i `BACKUP_TIMEOUT` (domyślnie `2h`, `tools/db-backup/README.md`). **Zmiana limitu wymaga `apply` z nową karencją** (stałe `graces` w `checks.sh`), inaczej długi, ale poprawny przebieg da fałszywy alarm. Check zdarzeń API nie ma harmonogramu: każde zdarzenie to `/fail` z opisem i zaraz sukces, który odnawia okres, a healthchecks.io alarmuje tylko przy zmianie stanu.

## Adres pingu to sekret

Kto zna adres (`https://hc-ping.com/<uuid>`), może meldować się za usługę: zgłosić sukces po nieudanej nocy albo zasypać kanał fałszywymi alarmami. Dlatego:
- `checks.sh` drukuje adresy tylko na stdout i nigdy do logu; klucz i adresy przekazuje do `curl` jako konfigurację na standardowym wejściu, nigdy jako argument;
- na produkcji operator ustawia `HEALTHCHECK_PING_URL` przez `--stdin` i pieczętuje (sealed) na każdej z trzech usług, więc nowy PR env jej nie kopiuje i się nie melduje; `.railway/railway.ts` deklaruje tylko nazwę (`preserve()`), nigdy wartość;
- adres nie trafia do issue, PR, logu ani komentarza. Wyciekły adres: `delete` i `apply` środowiska dają nowe UUID-y, potem nowe wartości zmiennych.

## Test lokalny

```bash
tools/healthchecks/test.sh
```

Wymaga `bash` i `jq` (bez Dockera). Atrapa `curl` w `PATH` odpowiada według skryptu przypadku (metoda, adres, status, plik z `fixtures/`, w kolejności) i zapisuje, co `checks.sh` wysłał: argumenty, konfigurację ze stdin, nagłówki i treść. Sprawdza:
- `apply` na pustym projekcie: treść trzech upsertów (nazwa, tagi, harmonogram, strefa, karencja, okres, `channels`, `unique`), uzbrojenie obu checków cronów meldunkiem bez klucza API, odczyt z obydwoma „up” i adresy tylko na stdout;
- `apply`, gdy checki istnieją i są „up”: bez meldunku uzbrajającego;
- check crona nie „up” po uzbrojeniu (także nieudany meldunek uzbrajający), check „down” i check brakujący w odczycie → kod 1, bez adresów na stdout;
- `--schedule` i `--grace` w treści obu checków cronów (check zdarzeń bez zmian), `--grace` osobno;
- `delete`: trzy checki po UUID z odczytu, check innego środowiska zostaje, brakujący jest pomijany, nieudane usunięcie → kod 1;
- błędy API: 401 z komunikatem API, 500, brak odpowiedzi, odpowiedź dla innego checka, odczyt bez listy → kod 1, bez kolejnych żądań;
- błędy użycia: brak albo pusty klucz, złe argumenty, nazwy środowiska, wartości opcji i `HEALTHCHECKS_API_URL` → kod 2 bez żądania;
- w każdym przypadku: klucza nie ma w argumentach `curl`, na stdout ani w logu, a UUID-ów checków w logu.

CI uruchamia ten test przy każdym PR i pushu na `main` (job `healthchecks test`).
