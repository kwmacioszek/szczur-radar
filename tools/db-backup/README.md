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
| `tables.sh` | klasyfikacja każdej tabeli schematu `public` (S-15), czytana przez `backup.sh` i `restore-check.sh`: wersja Flyway, od której tabela istnieje, i tryb. `counted`: tabela wymagana, która tylko rośnie (liczona przed dumpem i po nim, porównywana z poprzednim manifestem). `present`: tabela tylko wymagana, bo jej wiersze mogą znikać (`activation_tokens`, `flyway_schema_history`) albo jej danych nie ma w dumpie (sesje). Migracja dodająca tabelę dopisuje ją tutaj w tym samym PR. Wpisów się nie usuwa: starsze kopie nadal mają tabelę |
| `nas-retention.sh` | `nas-retention.sh <katalog> [--dry-run]`: retencja kopii na NAS (czysty bash, bez rclone i Dockera) |
| `test.sh` | lokalny test całości w Dockerze, bez Railway i S3 |

## Przebieg `backup.sh`

0. Poza produkcją nic nie robi: jeśli `RAILWAY_ENVIRONMENT_NAME` jest ustawione i różne od `production`, zapisuje „skipped” i kończy się sukcesem. PR env dostaje ten cron jako kopia produkcji, ale jego baza nie potrzebuje kopii, a PR env bez bazy wysyłałby co noc e-mail o porażce. Wyjątek: `BACKUP_IN_ANY_ENVIRONMENT=true` na serwisie, do przetestowania zmiany narzędzia w PR env. Lokalnie (`test.sh`, ręcznie) zmiennej nie ma, więc przebieg rusza.
   Cały przebieg ma twardy limit czasu `BACKUP_TIMEOUT` (domyślnie `2h`), a `pg_dump` czeka na blokadę najwyżej 5 min. Zawieszony przebieg kończy się więc porażką (`FAILED: the run exceeded …`), zamiast blokować kolejne: Railway pomija przebieg cron, dopóki poprzedni trwa.
1. Czeka, aż baza z `PG*` przyjmuje połączenia (sieć prywatna Railway bywa gotowa kilka sekund po starcie kontenera).
2. Odczytuje wersję serwera i najwyższą udaną migrację Flyway. Liczy wiersze tabel `counted` z `tables.sh`, które istnieją przy tej wersji Flyway (przy V9: `users`, `pipeline_runs`, `pipeline_run_steps`, `raw_payloads`, `posts`, `assessments`, `notifications`).
3. Kontrola wiarygodności, przed dumpem (S-15): kopia wyczyszczonej albo okrojonej bazy nie może trafić do bucketu, gdzie retencja i lustro NAS wyparłyby nią dobre kopie. Porażka kończy się komunikatem z „nothing dumped”, bez dumpa i bez wysyłki, więc retencja zostaje nietknięta.
   - Minimum: `posts` musi mieć wiersze (`posts is empty: refusing to back up an empty database`), zawsze, także przy zgodzie.
   - Brak spadku: żadna liczona tabela nie może mieć mniej wierszy niż w `counts_after` najnowszego manifestu w `daily/` (`rows lost since daily/<manifest>: posts 35 < 40`). Porównanie obejmuje tabele, które mają liczbę w obu; tabela nowsza niż ten manifest jest pomijana. Manifest z nazwą późniejszą niż bieżący przebieg (data z przyszłości) jest pomijany i logowany jako „ignoring”. Brak manifestu to nie błąd, tylko pierwsza kopia w buckecie.
   - Zgoda: komunikat spadku podaje gotową wartość `BACKUP_ACCEPT_SHRINK=szczur-radar-<ts>`, czyli nazwę najnowszego manifestu bez rozszerzenia. Ustawiona na serwisie (i po redeployu `db-backup`; białe znaki są obcinane) przepuszcza spadek tylko względem tego manifestu, z logiem `accepted: …`. Gdy zgoda pasuje, ale nic nie spadło, przebieg loguje `BACKUP_ACCEPT_SHRINK=… not needed` i zapisuje `accepted_shrink: false`; gdy w `daily/` nie ma manifestu, loguje `ignoring BACKUP_ACCEPT_SHRINK=…: there is no manifest in daily/`. Po pierwszej udanej kopii najnowszym manifestem jest już nowy, więc zgoda sama wygasa: następny przebieg loguje `ignoring BACKUP_ACCEPT_SHRINK=…` i sprawdza normalnie, także nowy spadek.
   - Najnowszy manifest, którego nie da się przeczytać albo którego `counts_after` nie jest obiektem liczb z liczbą `posts`, kończy przebieg błędem z jego nazwą, bo spadku nie da się wtedy sprawdzić. Zgoda tego nie obejmuje. Jeśli manifest jest uszkodzony, zmiana jego nazwy na taką spoza wzorca (np. `….manifest.json.broken`) sprawia, że narzędzie go pomija. Retencja usuwa tylko dokładne nazwy, więc plik `.broken` zostaje w buckecie (i w lustrze NAS): usuń go ręcznie, gdy jego dump wypadnie z retencji. Tak samo kończy się przebieg, gdy wszystkie manifesty w `daily/` mają datę późniejszą niż bieżący przebieg (zły zegar albo ręcznie zrobione nazwy): to nie jest pierwsza kopia.
4. Uruchamia `pg_dump --format=custom --no-owner --exclude-table-data='public.spring_session*'`. Danych sesji nie ma w kopii, bo identyfikator sesji z dumpa pozwoliłby ją przejąć. Po odtworzeniu wszyscy logują się ponownie.
5. Ponownie liczy wiersze. `pg_dump` czyta jeden snapshot, więc liczby w kopii mieszczą się między pomiarem przed dumpem a pomiarem po nim.
6. Sprawdza `pg_restore --list`: muszą być dane `posts`, nie może być danych `spring_session*`. `--exclude-table-data` nie zgłasza błędu, gdy wzorzec nic nie pasuje.
7. Zapisuje manifest (JSON) i uruchamia `restore-check.sh` na dumpie z manifestem.
8. Dopiero po udanym sprawdzeniu wysyła `daily/szczur-radar-<UTC yyyymmddThhmmssZ>.dump`, a potem `.manifest.json` (manifest obok dumpa oznacza kompletną parę).
9. Jeśli w `monthly/` nie ma jeszcze kopii bieżącego miesiąca UTC, kopiuje dump po stronie serwera jako `monthly/szczur-radar-<yyyy-mm>.dump` z manifestem. Kopią miesięczną zostaje więc pierwszy udany dump miesiąca.
10. Retencja: w `daily/` zostaje `BACKUP_KEEP_DAILY` najnowszych dumpów, w `monthly/` `BACKUP_KEEP_MONTHLY`, każdy z manifestem. Liczą się tylko dokładne nazwy, które zapisuje to narzędzie (`szczur-radar-<yyyymmddThhmmssZ>.dump`, `szczur-radar-<yyyy-mm>.dump`). Inne pliki zostają i są logowane jako „ignoring”. Dumpa z bieżącego przebiegu retencja nigdy nie usuwa.

Każda porażka kończy przebieg kodem ≠ 0, więc Railway oznacza przebieg cron jako nieudany. Skrypty nigdy nie wypisują hasła do bazy ani kluczy S3 i nie używają `set -x`. Klucze trafiają do rclone przez zmienne `RCLONE_S3_*`, nie przez argumenty.

Manifest zawiera: czas, nazwę pliku, `sha256`, rozmiar, wersje serwera i `pg_dump`, wersję Flyway, wykluczone dane, liczby wierszy przed dumpem i po nim, `compared_with` (nazwę porównanego manifestu z `daily/` albo `null` przy pierwszej kopii), `accepted_shrink` (`true`, gdy spadek przepuściła zgoda `BACKUP_ACCEPT_SHRINK`) oraz wynik i czas `restore_check`. `format` zostaje `1`: nowe pola tylko doszły, a starsze manifesty bez nich nadal przechodzą przez `restore-check.sh`.

## `restore-check.sh`

- Tymczasowy klaster Postgres 18 powstaje w katalogu tymczasowym kontenera, nigdy na wolumenie. `initdb` i serwer działają jako użytkownik `postgres` obrazu, bo odmawiają pracy jako root.
- Dump trafia do bazy `restored` przez `pg_restore --no-owner --no-acl --single-transaction`.
- Kontrole, w tej kolejności:
  - `flyway_schema_history` ma udaną migrację; jej wersja wyznacza wymagane tabele;
  - istnieje każda tabela z `tables.sh`, której wersja jest ≤ wersji Flyway odtworzonej bazy (`tables: missing …`). Dump sprzed V7 (np. kopia miesięczna 2026-10 z V5) przechodzi więc bez `activation_tokens` i `notifications`;
  - każda tabela bazowa schematu `public` ma klasyfikację w `tables.sh` (`tables: unknown <t>; classify it in tables.sh`), więc tabela z nowej migracji nie przejdzie po cichu;
  - tabele sesji są puste;
  - z manifestem: wersja Flyway jest taka sama, a liczby wierszy każdej tabeli z manifestu mieszczą się w przedziale [przed, po]. Starszy manifest ma mniej liczonych tabel i nadal przechodzi.
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
| `RAILWAY_ENVIRONMENT_NAME`, `BACKUP_IN_ANY_ENVIRONMENT` | środowisko Railway (ustawia je Railway); poza `production` przebieg jest pomijany, chyba że `BACKUP_IN_ANY_ENVIRONMENT=true` (ustawiana ręcznie; `.railway/railway.ts` ją ignoruje: `variables.ignore`) |
| `BACKUP_ACCEPT_SHRINK` | jednorazowa zgoda na spadek liczby wierszy po celowym odtworzeniu bazy: nazwa najnowszego manifestu w `daily/` bez rozszerzenia (`szczur-radar-<ts>`, podaje ją komunikat odrzuconego przebiegu). Działa tylko względem tego manifestu, więc po pierwszej udanej kopii wygasa; nie obejmuje pustej `posts`. Zmiana zmiennej wymaga redeployu `db-backup`. Ustawiana ręcznie (`.railway/railway.ts` ją ignoruje: `variables.ignore`); runbook odtworzenia w `deploy-plan.md`, sekcja F-01 |
| `RESTORE_CHECK_PASSWORD` | hasło użytkownika `postgres` dla `restore-check.sh --serve` |

W Railway serwis i jego zmienne deklaruje `.railway/railway.ts`. Zmienne strojenia `BACKUP_IN_ANY_ENVIRONMENT`, `BACKUP_ACCEPT_SHRINK`, `BACKUP_TIMEOUT` i `S3_FORCE_PATH_STYLE` można ustawiać ręcznie na serwisie: `railway.ts` je ignoruje (wzorce `db-backup/…` w `variables.ignore` projektu, S-14), więc IaC ich nie ustawia i nie usuwa. `BACKUP_KEEP_DAILY` i `BACKUP_KEEP_MONTHLY` są zadeklarowane wartością, więc ręczną zmianę w panelu nadpisze następny apply; zmienia się je w `railway.ts`. Każda inna zmienna ustawiona ręcznie na serwisie wymaga dopisania wzorca do `variables.ignore` albo deklaracji w `railway.ts`. Inaczej plan IaC proponuje jej usunięcie (`variable.delete`), a bramka CI (`railway-config`) go odrzuca.

## Test lokalny

```bash
tools/db-backup/test.sh
```

Wymaga Dockera. Test:
- buduje obraz i uruchamia Postgresa z obrazu produkcji (`postgres-ssl:18`);
- zakłada schemat z migracji `szczur-radar-api/src/main/resources/db/migration` z `flyway_schema_history` jak po `migrate`;
- wstawia przykładowe wiersze, w tym link aktywacyjny, powiadomienie i sesję;
- uruchamia `backup.sh` z `BACKUP_REMOTE=:local:/out` (wolumen Dockera).

Sprawdza:
- `tables.sh`: przy V9 11 wymaganych i 7 liczonych tabel, a błędny wpis (wersja `9x`, czwarte pole) kończy się błędem zamiast po cichu pominąć tabelę;
- dump i manifest (liczby wszystkich tabel `counted`, z `notifications` i `pipeline_run_steps`, bez `activation_tokens`; `compared_with: null` i `accepted_shrink: false` przy pierwszej kopii);
- brak danych sesji w dumpie;
- kopię miesięczną;
- retencję (20 starszych dziennych → 14, 15 miesięcznych → 12; plik o innej nazwie zostaje) i porównanie drugiego przebiegu z manifestem pierwszego;
- pominięcie manifestu z datą w przyszłości;
- odrzucenie spadku przed dumpem (5 usuniętych postów: `posts 35 < 40`) z gotową wartością `BACKUP_ACCEPT_SHRINK`, bez zmian w kopiach; przebieg z tą zgodą (`accepted_shrink: true`) i jej wygaśnięcie w następnym przebiegu: stara wartość nie przepuszcza nowego spadku;
- przejście dumpa z Flyway V5 (bez `activation_tokens` i `notifications`) przez `restore-check.sh`;
- porażkę na obciętym dumpie;
- porażkę po przekroczeniu limitu czasu (`BACKUP_TIMEOUT=1s`);
- pominięcie przebiegu poza produkcją;
- brak wysyłki, gdy sprawdzenie odtworzenia nie przejdzie: tabela bez klasyfikacji (`tables: unknown`) i brak wymaganej tabeli (`tables: missing activation_tokens`);
- odrzucenie pustej `posts` przed dumpem, także przy zgodzie wskazującej najnowszy manifest;
- `nas-retention.sh --dry-run` (inne nazwy i daty z przyszłości zostają);
- brak hasła w logach.

CI uruchamia ten test przy każdym PR i pushu na `main` (job `db backup test`), więc migracja psująca kopię zatrzymuje PR, także migracja z tabelą, której nie ma w `tables.sh`. Wypisuje też log pierwszego przebiegu, czyli to, co Railway pokazuje dla przebiegu cron. Kontenery, sieć i wolumen testu są zawsze usuwane.

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
