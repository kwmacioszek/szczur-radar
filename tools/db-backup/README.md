# db-backup

Codzienna kopia bazy Szczur Radar (F-01, `production-data-gate`). Railway Hobby nie ma wbudowanych kopii wolumenu ani PITR (to funkcje Pro), więc bazę chroni kopia logiczna.

Serwis cron `db-backup` w Railway codziennie robi `pg_dump` 18 i wysyła go do bucketu `db-backups` w tym samym środowisku. Każdy dump trafia tam dopiero wtedy, gdy w całości odtworzy się w tymczasowej bazie. Synology pobiera kopie z bucketu przez Cloud Sync, więc kopia leży też poza Railway.

Jeden obraz służy trzem celom: przebiegowi cron w Railway, testowi lokalnemu i ręcznemu testowi odtworzenia na Macu. Runbooki (odtworzenie awaryjne, kroki po merge'u, NAS) są w `context/deployment/deploy-plan.md`, w sekcji F-01.

## Pliki

| Plik | Co robi |
| --- | --- |
| `Dockerfile` | `postgres:18` (klient i serwer 18) + `rclone`, `jq`, `ca-certificates`. `ENTRYPOINT` to `backup.sh`; `docker run <obraz> restore-check.sh …` uruchamia inne narzędzie obrazu |
| `backup.sh` | jeden przebieg kopii (niżej) |
| `restore-check.sh` | `restore-check.sh <dump> [<manifest>] [--serve]`: odtworzenie w tymczasowym klastrze i kontrole |
| `nas-retention.sh` | `nas-retention.sh <katalog> [--dry-run]`: retencja kopii na NAS (czysty bash, bez rclone i Dockera) |
| `test.sh` | lokalny test całości w Dockerze, bez Railway i S3 |

## Przebieg `backup.sh`

0. Poza produkcją nic nie robi: jeśli `RAILWAY_ENVIRONMENT_NAME` jest ustawione i różne od `production`, zapisuje „skipped” i kończy się sukcesem. PR env dostaje ten cron jako kopia produkcji, ale jego baza nie potrzebuje kopii, a PR env bez bazy wysyłałby co noc e-mail o porażce. Wyjątek: `BACKUP_IN_ANY_ENVIRONMENT=true` na serwisie, do przetestowania zmiany narzędzia w PR env. Lokalnie (`test.sh`, ręcznie) zmiennej nie ma, więc przebieg rusza.
   Cały przebieg ma twardy limit czasu `BACKUP_TIMEOUT` (domyślnie `2h`), a `pg_dump` czeka na blokadę najwyżej 5 min. Zawieszony przebieg kończy się więc porażką (`FAILED: the run exceeded …`), zamiast blokować kolejne: Railway pomija przebieg cron, dopóki poprzedni trwa.
1. Czeka, aż baza z `PG*` przyjmuje połączenia (sieć prywatna Railway bywa gotowa kilka sekund po starcie kontenera).
2. Odczytuje wersję serwera i najwyższą udaną migrację Flyway. Liczy wiersze `users`, `pipeline_runs`, `posts`, `assessments` i `raw_payloads`.
3. Uruchamia `pg_dump --format=custom --no-owner --exclude-table-data='public.spring_session*'`. Danych sesji nie ma w kopii, bo identyfikator sesji z dumpa pozwoliłby ją przejąć. Po odtworzeniu wszyscy logują się ponownie.
4. Ponownie liczy wiersze. `pg_dump` czyta jeden snapshot, więc liczby w kopii mieszczą się między pomiarem przed dumpem a pomiarem po nim.
5. Sprawdza `pg_restore --list`: muszą być dane `posts`, nie może być danych `spring_session*`. `--exclude-table-data` nie zgłasza błędu, gdy wzorzec nic nie pasuje.
6. Zapisuje manifest (JSON) i uruchamia `restore-check.sh` na dumpie z manifestem.
7. Dopiero po udanym sprawdzeniu wysyła `daily/szczur-radar-<UTC yyyymmddThhmmssZ>.dump`, a potem `.manifest.json` (manifest obok dumpa oznacza kompletną parę).
8. Jeśli w `monthly/` nie ma jeszcze kopii bieżącego miesiąca UTC, kopiuje dump po stronie serwera jako `monthly/szczur-radar-<yyyy-mm>.dump` z manifestem. Kopią miesięczną zostaje więc pierwszy udany dump miesiąca.
9. Retencja: w `daily/` zostaje `BACKUP_KEEP_DAILY` najnowszych dumpów, w `monthly/` `BACKUP_KEEP_MONTHLY`, każdy z manifestem. Liczą się tylko dokładne nazwy, które zapisuje to narzędzie (`szczur-radar-<yyyymmddThhmmssZ>.dump`, `szczur-radar-<yyyy-mm>.dump`). Inne pliki zostają i są logowane jako „ignoring”. Dumpa z bieżącego przebiegu retencja nigdy nie usuwa.

Każda porażka kończy przebieg kodem ≠ 0, więc Railway oznacza przebieg cron jako nieudany. Skrypty nigdy nie wypisują hasła do bazy ani kluczy S3 i nie używają `set -x`. Klucze trafiają do rclone przez zmienne `RCLONE_S3_*`, nie przez argumenty.

Manifest zawiera: czas, nazwę pliku, `sha256`, rozmiar, wersje serwera i `pg_dump`, wersję Flyway, wykluczone dane, liczby wierszy przed dumpem i po nim oraz wynik i czas `restore_check`.

## `restore-check.sh`

- Tymczasowy klaster Postgres 18 powstaje w katalogu tymczasowym kontenera, nigdy na wolumenie. `initdb` i serwer działają jako użytkownik `postgres` obrazu, bo odmawiają pracy jako root.
- Dump trafia do bazy `restored` przez `pg_restore --no-owner --no-acl --single-transaction`.
- Kontrole:
  - istnieją tabele aplikacji, sesji i `flyway_schema_history`;
  - tabele sesji są puste;
  - z manifestem: wersja Flyway jest taka sama, a liczby wierszy mieszczą się w przedziale [przed, po].
- Wynik: kod 0 albo ≠ 0 z nazwą kontroli, która nie przeszła.
- `--serve`: po kontrolach serwer zostaje uruchomiony na porcie 5432 kontenera (użytkownik `postgres`, hasło z `RESTORE_CHECK_PASSWORD`, baza `restored`), aż do zatrzymania kontenera. Hasło jest ustawiane ze zmiennej (`\getenv`), nigdy z wiersza poleceń. `pg_hba.conf` jest przepisywany: socket bez hasła, każde połączenie TCP, także z loopback, z hasłem.
- `backup.sh` uruchamia odtworzenie bez kluczy S3 w środowisku: odtworzenie wykonuje kod z dumpa jako superużytkownik.

## Zmienne

| Zmienna | Znaczenie |
| --- | --- |
| `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` | baza do skopiowania (w Railway referencje do `postgres`) |
| `BACKUP_REMOTE` | cel rclone, np. `:local:/out` w teście; bez niej `:s3:$S3_BUCKET` |
| `S3_BUCKET`, `S3_ENDPOINT`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION` | bucket Railway (referencje do `BUCKET`, `ENDPOINT`, `ACCESS_KEY_ID`, `SECRET_ACCESS_KEY`, `REGION`); region domyślnie `auto` |
| `S3_FORCE_PATH_STYLE` | domyślnie `false`: buckety Railway używają adresów virtual-hosted. Starszy bucket może wymagać `true`, co pokazuje jego zakładka Credentials |
| `BACKUP_KEEP_DAILY`, `BACKUP_KEEP_MONTHLY` | retencja, domyślnie `14` i `12` (także w `nas-retention.sh`) |
| `BACKUP_TIMEOUT` | limit czasu całego przebiegu (format `timeout`), domyślnie `2h` |
| `RAILWAY_ENVIRONMENT_NAME`, `BACKUP_IN_ANY_ENVIRONMENT` | środowisko Railway (ustawia je Railway); poza `production` przebieg jest pomijany, chyba że `BACKUP_IN_ANY_ENVIRONMENT=true` |
| `RESTORE_CHECK_PASSWORD` | hasło użytkownika `postgres` dla `restore-check.sh --serve` |

## Test lokalny

```bash
tools/db-backup/test.sh
```

Wymaga Dockera. Test:
- buduje obraz i uruchamia Postgresa z obrazu produkcji (`postgres-ssl:18`);
- zakłada schemat z migracji `szczur-radar-api/src/main/resources/db/migration` z `flyway_schema_history` jak po `migrate`;
- wstawia przykładowe wiersze, w tym sesję;
- uruchamia `backup.sh` z `BACKUP_REMOTE=:local:/out` (wolumen Dockera).

Sprawdza:
- dump i manifest;
- brak danych sesji w dumpie;
- kopię miesięczną;
- retencję (20 starszych dziennych → 14, 15 miesięcznych → 12; plik o innej nazwie zostaje);
- porażkę na obciętym dumpie;
- brak wysyłki, gdy sprawdzenie odtworzenia nie przejdzie;
- porażkę po przekroczeniu limitu czasu (`BACKUP_TIMEOUT=1s`);
- pominięcie przebiegu poza produkcją;
- `nas-retention.sh --dry-run` (inne nazwy i daty z przyszłości zostają);
- brak hasła w logach.

CI uruchamia ten test przy każdym PR i pushu na `main` (job `db backup test`), więc migracja psująca kopię zatrzymuje PR. Wypisuje też log pierwszego przebiegu, czyli to, co Railway pokazuje dla przebiegu cron. Kontenery, sieć i wolumen testu są zawsze usuwane.

## Ręczny test z aplikacją na pobranym dumpie

Dump i manifest pobierz z panelu Railway (bucket `db-backups` → Files) albo z kopii na NAS.

```bash
docker build -t szczur-radar-db-backup tools/db-backup
export RESTORE_CHECK_PASSWORD=<dowolne lokalne hasło>
docker run --rm -it -p 127.0.0.1:5433:5432 -e RESTORE_CHECK_PASSWORD -v "$PWD/dumps:/dumps:ro" \
  szczur-radar-db-backup restore-check.sh /dumps/<nazwa>.dump /dumps/<nazwa>.manifest.json --serve
```

Port publikuj tylko na `127.0.0.1`: to kopia prawdziwych danych. 5433 nie koliduje z bazą z `compose.local.yaml`.

Potem w `szczur-radar-api/`, z ustawieniami `LLM_*` jak przy zwykłym uruchomieniu lokalnym:
1. `PGHOST=127.0.0.1 PGPORT=5433 PGUSER=postgres PGPASSWORD=$RESTORE_CHECK_PASSWORD PGDATABASE=restored COOKIE_SECURE=false`.
2. `java -jar target/szczur-radar-api.jar migrate` musi powiedzieć, że schemat jest aktualny („Schema … is up to date”).
3. Uruchom serwer i zaloguj się jako admin. Sesje nie są w kopii, więc trzeba zalogować się od nowa.
4. `total` z `/api/posts` ma być równy temu w środowisku źródłowym.

Ctrl-C zatrzymuje kontener i usuwa odtworzoną bazę.

## Retencja na NAS

Synology Cloud Sync w kierunku „pobierz wyłącznie zmiany zdalne” nie ma opcji zachowania plików usuniętych w źródle. Folder na NAS jest więc lustrem bucketu (14 + 12), a retencja bucketu czyści też NAS. Archiwum, które przetrwa opróżnienie bucketu, to drugie zadanie Cloud Sync z NAS-a na Google Drive („prześlij wyłącznie zmiany lokalne” z opcją „nie usuwaj plików w miejscu docelowym”). Szczegóły są w runbooku w `deploy-plan.md`.

`nas-retention.sh` przydaje się tylko w folderze, który zachowuje usunięte pliki (inne ustawienie niż powyżej). Przy lustrze nie jest potrzebny. `nas-retention.sh <katalog> [--dry-run]` uruchamiany codziennie z Harmonogramu zadań DSM, po synchronizacji, zostawia w `<katalog>/daily` 14 najnowszych dumpów, a w `<katalog>/monthly` 12, każdy z manifestem. Liczy tylko dokładne nazwy z `backup.sh` i pomija pliki z datą w przyszłości: plik wgrany do bucketu pod obcą lub „późniejszą” nazwą nie wypchnie prawdziwych kopii. Najnowszego dumpa nigdy nie usuwa. `--dry-run` tylko wypisuje, co by usunął.
