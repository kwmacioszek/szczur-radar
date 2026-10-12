# F-01 production-data-gate — plan implementacji

## Overview

F-01 zamyka bramkę produkcyjnych danych w planie Railway **Hobby**. Hobby nie ma wbudowanych kopii wolumenu ani PITR (to funkcje Pro), więc bazę chroni kopia logiczna:

- serwis cron `db-backup` w Railway codziennie robi `pg_dump` 18;
- każdy dump przed wysłaniem do bucketu `db-backups` jest sprawdzany pełnym odtworzeniem w tymczasowej bazie;
- retencja to 14 dziennych i 12 miesięcznych kopii;
- Synology pobiera kopie przez Cloud Sync, czyli kopia leży też poza Railway.

Do tego:

- wolumen Postgresa rośnie z 500 MB do 5 GB;
- klucz OpenRouter produkcji dostaje limit $10/mies., a klient LLM traktuje wyczerpany limit jako błąd konfiguracji;
- PRD, roadmapa i dokumenty wdrożeniowe opisują nowy stan.

## Current State Analysis

Źródło: `context/changes/production-data-gate/research.md` (odczyty Railway z 2026-10-11 oraz sekcja Follow-up z decyzjami użytkownika).

- **Plan i limity:** Railway Hobby (potwierdzone przez użytkownika). Limity workspace soft $5 / hard $10 są ustawione i zostają (research B, Follow-up).
- **Baza:**
  - Wolumen ma `sizeMB: 500`, zajęte 191,9 MB, a system plików ma realnie ~434 MB.
  - Nie ma harmonogramu kopii ani żadnej kopii. PITR jest wyłączone (`enabled: false`).
  - Od S-02 produkcja trzyma prawdziwe posty i oceny.
- **WAL:** obraz `postgres-ssl:18` nie zmienia `max_wal_size`, więc obowiązuje domyślny 1 GB. Próg punktu kontrolnego to ~528 MB WAL, czyli więcej niż wolne miejsce na wolumenie. To ryzyko „No space left on device”.
- **Sekrety:** `LLM_API_KEY`, `WYKOP_KEY`, `WYKOP_SECRET` i `JWT_SECRET` są zapieczętowane na produkcji. CI nigdy nie zapisuje zmiennych produkcji (`ci.yml:239-314`). Tę część F-01 trzeba tylko udokumentować.
- **IaC:** SDK `railway@3.11.0` obsługuje `bucket(name, { region })`, `ref(resource, output)` i `deploy.cronSchedule`. Bucket udostępnia zmienne `BUCKET`, `ACCESS_KEY_ID`, `SECRET_ACCESS_KEY`, `REGION`, `ENDPOINT`, a każde środowisko dostaje własny bucket z osobnymi kluczami.
- **Bramka CI** (`ci.yml:328-339`) odrzuca każdą zmianę planu wspominającą `source`, w każdym środowisku. Nowy serwis z GitHuba zostałby więc zablokowany.
- **LLM:**
  - `TransportException.kindOf` (`TransportException.java:72-82`) traktuje 401, 402 i 404 jako `CONFIGURATION`, a 403 jako `REJECTED`.
  - Wyczerpany limit klucza OpenRouter daje w praktyce HTTP 403 „Key limit exceeded”, więc posty w trwającym przebiegu traciłyby po jednej próbie.
  - Sprawdzenie LLM przed oceną (`deploy-plan.md:185`) zatrzymuje dopiero następny przebieg.
- **Narzędzia lokalne:** `compose.local.yaml` (obraz `postgres-ssl:18`), `pg_dump` 18.6, Docker. Brak jakichkolwiek skryptów backupu w repo (`tools/` ma tylko `render_roadmap.swift`).

## Desired End State

Przed merge'em (wszystko w Progress):

- Wolumen produkcji ma 5 GB, a `df -h` w kontenerze Postgresa pokazuje ~4,6–4,7 GB. Pomiary bazy są zapisane w `deploy-plan.md`.
- `tools/db-backup/` zawiera obraz, `backup.sh`, `restore-check.sh`, `nas-retention.sh` i `test.sh`. Lokalny test przechodzi: dump bez danych sesji, odtworzenie, retencja oraz porażka na uszkodzonym dumpie.
- `.railway/railway.ts` deklaruje bucket `db-backups` (region `ams`) i serwis cron `db-backup` (codziennie 02:00 UTC, `restartPolicyType: NEVER`). Bramka CI przepuszcza tworzenie nowych zasobów i nadal blokuje zmianę źródła istniejących serwisów oraz zmiany destrukcyjne.
- W PR env przebieg `db-backup` kończy się kodem 0, a w buckecie PR env leży dump z manifestem. Nieudany przebieg kończy się kodem ≠ 0 i powiadomieniem. Na dumpie z PR env przeszły test z aplikacją oraz próba procedury odtworzenia awaryjnego.
- 403 z wyczerpanego limitu klucza OpenRouter kończy się `LlmConfigurationException` (przebieg staje, posty nie tracą prób). Klucz produkcji ma limit $10 z resetem miesięcznym.
- PRD (NFR, Non-Goal, OQ4), roadmapa z obrazkiem, `infrastructure.md`, `deploy-plan.md`, `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md` i `lessons.md` opisują nowy stan.

Po merge'u (runbook w `deploy-plan.md` i komentarze w issue F-01, nie Progress):

- CI tworzy bucket i cron na produkcji, a pierwszy przebieg produkcyjny kończy się sukcesem.
- Test z aplikacją przechodzi na dumpie produkcyjnym.
- Synology Cloud Sync pobiera kopie, a zadanie DSM pilnuje retencji na NAS.
- Issue F-01 zostaje zamknięte po tej weryfikacji.

### Key Discoveries:

- `infrastructure.md:102`: usuwanie lub odtwarzanie bazy i wolumenu, włączanie i zmiana backupów oraz zmiana planu to operacje tylko dla człowieka. Agent przygotowuje polecenia i weryfikuje w trybie odczytu.
- `ci.yml:328-339`: filtr `source` w bramce IaC. Tworzenie zasobu trzeba z niego wyłączyć, inaczej CI na `main` padnie, a Wait for CI zablokuje też wdrożenie API.
- `OpenRouterClient.java:167-170` klasyfikuje błąd HTTP statusem, a `:200-211` robi to samo dla błędu w treści odpowiedzi 200. Klasyfikacja limitu klucza musi działać w obu miejscach.
- `llm/CLAUDE.md` (sekcja Adding a provider, punkt 4): ciała błędów HTTP w testach to krótki syntetyczny JSON w formacie dostawcy. Nagranie prawdziwej odpowiedzi 403 nie jest potrzebne.
- `compose.local.yaml:6`: lokalny Postgres używa tego samego obrazu `ghcr.io/railwayapp-templates/postgres-ssl:18` co produkcja.
- `pg_dump --exclude-table-data` nie zgłasza błędu, gdy wzorzec niczego nie pasuje (dokumentacja PG 18). Wykluczenie trzeba sprawdzić przez `pg_restore -l`.
- `railway.ts:62-64`: Postgres nie deklaruje rozmiaru wolumenu. Po zmianie rozmiaru trzeba sprawdzić, że `railway config plan` na produkcji nadal wychodzi z kodem 0.
- `lessons.md` („Implementacja, przegląd i archiwizacja na jednej gałęzi”): kroki możliwe dopiero po merge'u nie trafiają do Progress.

## What We're NOT Doing

- Nie przechodzimy na Railway Pro i nie używamy kopii wolumenu ani PITR Railway.
- Nie dodajemy logiki backupu do `szczur-radar-api`. Kopia to osobny serwis infrastruktury, a Non-Goal PRD nadal obowiązuje dla kodu aplikacji.
- Nie zmieniamy limitów Railway ($5 / $10 zostają).
- Nie wysyłamy kopii na Google Drive. Można to później dodać konfiguracją Synology Cloud Sync, bez kodu.
- Nie robimy odtwarzania do punktu w czasie. Możliwa utrata danych to do ~24 h, czyli odstęp między dumpami.
- Nie automatyzujemy odtwarzania produkcji. Odtworzenie awaryjne to runbook wykonywany przez człowieka.
- Nie szyfrujemy dumpów poza szyfrowaniem bucketu Railway i dysku NAS.
- Nie zmieniamy klasyfikacji 402 z `limit_source = openrouter_in_flight_budget` (zostaje `CONFIGURATION`; tego budżetu nie używamy).
- Nie budujemy panelu ani alertów poza powiadomieniem Railway o nieudanym wdrożeniu (to S-06).
- Nie ustalamy godziny przebiegu dziennego. Robi to S-05, które może przesunąć godzinę dumpa.
- Nie edytujemy sekcji `## Baseline` roadmapy: to datowany zapis stanu z 2026-09-27.

## Implementation Approach

1. Najpierw usuwamy najpilniejsze ryzyko na produkcji: za mały wolumen i WAL.
2. Potem budujemy narzędzie kopii jako samodzielny obraz Docker, który działa tak samo lokalnie i w Railway, i sprawdzamy je lokalnym testem bez Railway.
3. Następnie wpinamy je w IaC jako serwis cron z własnym bucketem i sprawdzamy w PR env. To środowisko ma osobną bazę i bucket, więc pozwala przećwiczyć także procedurę odtworzenia awaryjnego bez ryzyka dla produkcji.
4. Limit OpenRouter to niezależny, mały przyrost kodu z testami.
5. Na końcu dokumenty, w tym runbook dla kroków produkcyjnych po merge'u.

Każdy dump przechodzi przez pełny `pg_restore` do tymczasowej bazy przed wysłaniem. Restore drill nie jest więc jednorazowy, tylko powtarza się codziennie.

## Critical Implementation Details

**Kolejność na produkcji.** Powiększenie wolumenu (faza 1) musi nastąpić przed pierwszym produkcyjnym dumpem i przed jakimkolwiek odtwarzaniem na produkcji. Zmiana rozmiaru przy pełnym wolumenie przechodzi w tryb offline z restartem (docs `volumes`). W marcu i kwietniu 2026 zdarzało się też, że system plików nie rósł mimo `sizeMB: 5000`, dlatego rozstrzyga `df -h` w kontenerze, nie panel.

**Bramka CI a stare gałęzie.** Po merge'u nowe PR env są kopiami produkcji, więc dostaną `db-backup` i bucket. PR, którego gałąź nie ma jeszcze `db-backup` w `railway.ts`, dostanie w planie usunięcie tego serwisu. Bramka tego odmówi i CI tego PR będzie czerwone. Lekarstwo: rebase na `main`. Dotyczy to też otwartego PR #42 (`admin-panel-ui-hardening`), jeśli jego PR env zostanie utworzony na nowo.

**Cron w Railway.**
- Nie ma ręcznego uruchomienia: przebieg startuje tylko według harmonogramu (UTC).
- O statusie przebiegu decyduje kod wyjścia. `restartPolicyType: NEVER` zostawia porażkę widoczną, zamiast ją ponawiać.
- Do testów w PR env operator może tymczasowo ustawić harmonogram `*/5 * * * *` w panelu. Następny apply z CI przywróci wartość z IaC.

**Spójność liczników.** `pg_dump` czyta jeden snapshot, ale liczby wierszy pobierane osobnym zapytaniem mogą się z nim nie zgadzać. Dlatego `backup.sh` liczy wiersze przed dumpem i po nim, a `restore-check.sh` wymaga, żeby liczby w odtworzonej bazie mieściły się w przedziale [przed, po]. Liczone tabele (`users`, `pipeline_runs`, `posts`, `assessments`, `raw_payloads`) nie mają w aplikacji ścieżki usuwania (PRD: bezterminowa retencja).

**Serwer Postgres w kontenerze.** `initdb` i `pg_ctl` odmawiają pracy jako root, więc tymczasowy klaster działa jako użytkownik `postgres` z obrazu. Jego katalog jest w systemie plików kontenera, nie na wolumenie.

**Sekrety w logach.** Skrypty nie wypisują `PGPASSWORD` ani kluczy S3, a `set -x` jest zabronione. Klucze S3 przechodzą do rclone przez zmienne środowiskowe, nie argumenty.

## Phase 1: Wolumen 5 GB i pomiar bazy

### Overview

Operator powiększa wolumen produkcyjnego Postgresa do maksimum Hobby i potwierdza, że system plików naprawdę urósł. Mierzymy bazę, żeby oprzeć przyszłe decyzje (rozmiar dumpa, przyrost) na liczbach zamiast na szacunku z research (5–10 KB na post).

### Changes Required:

#### 1. Zmiana rozmiaru wolumenu (operator, produkcja)

**File**: brak (panel Railway)

**Intent**: Usunąć ryzyko zapełnienia wolumenu przez WAL i zrobić miejsce na lata danych. Rekomendacja 5 GB z research (Follow-up, szacunek wolumenu).

**Contract**: Panel Railway → `production` → wolumen `postgres-volume` (blok wolumenu pod serwisem `postgres`) → Settings → Live Resize → `5000` MB (Railway liczy w jednostkach SI). Nie w trakcie przebiegu `RUNNING` w `/admin`.

#### 2. Pomiar bazy i zapis stanu (operator, agent pomaga w trybie odczytu)

**File**: `context/deployment/deploy-plan.md`

**Intent**: Założyć sekcję `## F-01: bramka produkcyjnych danych (production-data-gate)` i zapisać w niej stan na start:
- plan Hobby i limity $5/$10 (`railway usage limit status`);
- nazwy zapieczętowanych zmiennych (bez wartości);
- rozmiar wolumenu przed zmianą i po niej oraz wynik `df -h`;
- pomiary bazy.

**Contract**: Pomiary to zapytania tylko do odczytu, które wykonuje operator przez `railway connect postgres -e production` albo `railway ssh -s postgres -e production`:
- `pg_database_size(current_database())`;
- `pg_total_relation_size` dla tabel aplikacji;
- `count(*)` z `posts`, `assessments`, `raw_payloads`, `pipeline_runs`, `users`;
- rozmiar `pg_ls_waldir()`;
- `pg_settings` dla `max_wal_size`, `min_wal_size`, `shared_buffers`.

Wartości trafiają do tabeli w sekcji F-01. Hasła i klucze nigdy.

### Success Criteria:

#### Automated Verification:

- `railway volume -e <production-env-id> list --json` pokazuje `postgres-volume` z `sizeMB: 5000`
- `railway config plan` dla `production` (dry run, bez apply) nie pokazuje żadnej zmiany (`--detailed-exit-code` = 0)
- `curl -s https://szczur-radar-web-production.up.railway.app/api/ready` zwraca 200

#### Manual Verification:

- `df -h /var/lib/postgresql/data` w kontenerze `postgres` (produkcja) pokazuje ~4,6–4,7 GB rozmiaru, nie ~434 MB
- Logowanie admina na produkcji działa po zmianie rozmiaru
- Sekcja F-01 w `deploy-plan.md` zawiera pomiary bazy, wynik `df -h` i stan limitów oraz sekretów (same nazwy)

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się, aż człowiek potwierdzi weryfikację ręczną.

---

## Phase 2: Narzędzie kopii `tools/db-backup/`

### Overview

Samodzielny obraz Docker z `pg_dump`/`pg_restore`/serwerem Postgres 18 i `rclone`. Skrypty robią dump, sprawdzają go pełnym odtworzeniem, wysyłają z manifestem i pilnują retencji. Jest też skrypt retencji dla NAS i lokalny test całości bez Railway.

### Changes Required:

#### 1. Obraz

**File**: `tools/db-backup/Dockerfile`

**Intent**: Jeden obraz do trzech zastosowań: przebiegu cron w Railway, testu lokalnego i ręcznego testu odtworzenia na Macu. Ta sama wersja główna Postgresa co produkcja.

**Contract**:
- Bazą jest oficjalny `postgres:18` (Debian), który daje klienta i serwer 18.
- Dochodzą `rclone` i `ca-certificates` z pakietów Debiana.
- Skrypty leżą w `/usr/local/bin`. `ENTRYPOINT` uruchamia `backup.sh`, a inne polecenia (`restore-check.sh`, `nas-retention.sh`) podaje się jako argument (`docker run … restore-check.sh …`).
- Brak `HEALTHCHECK` i `EXPOSE`: to zadanie, nie serwer.

#### 2. Dump, sprawdzenie, wysyłka, retencja

**File**: `tools/db-backup/backup.sh`

**Intent**: Jeden przebieg kopii. Wynik wysyła tylko wtedy, gdy dump da się w całości odtworzyć. Każda porażka kończy się kodem ≠ 0, żeby Railway oznaczył przebieg jako nieudany.

**Contract**:
- **Wejście (zmienne środowiskowe):**
  - `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`;
  - `BACKUP_REMOTE`: miejsce docelowe rclone, np. `:s3:<bucket>` albo `:local:/out` w teście;
  - `S3_ENDPOINT`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION`, przekazywane do rclone jako `RCLONE_S3_*` (provider `Other`);
  - `BACKUP_KEEP_DAILY` (domyślnie `14`), `BACKUP_KEEP_MONTHLY` (domyślnie `12`).
- **Kroki:**
  1. Liczby wierszy przed dumpem.
  2. `pg_dump -Fc --no-owner --exclude-table-data='public.spring_session*'`.
  3. Liczby wierszy po dumpie.
  4. `pg_restore -l` musi przejść i nie może zawierać `TABLE DATA` dla `spring_session*`.
  5. `restore-check.sh` na pliku z manifestem.
  6. Wysyłka `daily/szczur-radar-<UTC yyyymmddThhmmssZ>.dump` i `.manifest.json`.
  7. Jeśli w `monthly/` nie ma jeszcze kopii z bieżącego miesiąca UTC, kopia jako `monthly/szczur-radar-<yyyy-mm>.dump` z manifestem.
  8. Retencja: w `daily/` zostaje `BACKUP_KEEP_DAILY` najnowszych par dump+manifest, w `monthly/` `BACKUP_KEEP_MONTHLY` najnowszych.
- **Manifest (JSON):**
  - czas, nazwa pliku, sha256, rozmiar;
  - wersje serwera i `pg_dump`;
  - najwyższa udana wersja z `flyway_schema_history`;
  - liczby wierszy przed i po dla każdej tabeli;
  - wynik i czas `restore-check`.
- **Bezpieczeństwo:** `set -euo pipefail`. Nic nie trafia do celu przed udanym `restore-check`. Sekrety nie trafiają do logów.
- **Przykład brzegowy retencji miesięcznej:** dump 2026-11-01T02:00Z tworzy `monthly/szczur-radar-2026-11.dump`. Dump 2026-11-02 go nie zastępuje. Jeśli dump 1 listopada się nie udał, kopią miesięczną zostaje pierwszy udany dump listopada.

#### 3. Sprawdzenie odtworzenia

**File**: `tools/db-backup/restore-check.sh`

**Intent**: Dowód, że dump daje zdrową, odpytywalną bazę. Ten sam skrypt służy codziennemu sprawdzeniu i ręcznemu testowi z aplikacją.

**Contract**:
- **Wywołanie:** `restore-check.sh <dump> [<manifest>] [--serve]`.
- **Przebieg:** tymczasowy klaster (`initdb` jako `postgres` w katalogu tymczasowym), potem `pg_restore --no-owner --no-acl --single-transaction` do pustej bazy utworzonej z `template0`.
- **Kontrole:**
  - tabele `users`, `spring_session`, `spring_session_attributes`, `pipeline_runs`, `pipeline_run_steps`, `raw_payloads`, `posts`, `assessments`, `flyway_schema_history` istnieją;
  - `spring_session` jest pusta;
  - z manifestem: najwyższa wersja Flyway równa tej z manifestu, a liczby wierszy w przedziale [przed, po].
- **Wynik:** kod 0 albo ≠ 0 z nazwaną kontrolą, która nie przeszła.
- **`--serve`:** po kontrolach serwer zostaje uruchomiony na porcie 5432 kontenera (`listen_addresses='*'`), aż do zatrzymania kontenera. Służy do lokalnego testu z aplikacją.

#### 4. Retencja na NAS

**File**: `tools/db-backup/nas-retention.sh`

**Intent**: Na NAS-ie Cloud Sync nie usuwa plików skasowanych w buckecie, żeby kopia przetrwała ewentualne wyczyszczenie bucketu. Retencję 14 + 12 stosuje więc osobny skrypt uruchamiany przez Harmonogram zadań DSM.

**Contract**:
- **Wywołanie:** `nas-retention.sh <dir> [--dry-run]`.
- **Działanie:** w `<dir>/daily` zostaje 14 najnowszych par dump+manifest, w `<dir>/monthly` 12 najnowszych. Skrypt nigdy nie usuwa najnowszego pliku, a przy `--dry-run` tylko wypisuje, co by usunął.
- **Środowisko:** czysty bash i narzędzia GNU dostępne w DSM. Bez `rclone` i Dockera.

#### 5. Test lokalny i opis

**File**: `tools/db-backup/test.sh`, `tools/db-backup/README.md`

**Intent**: Sprawdzić całość bez Railway i bez S3 oraz opisać użycie narzędzia.

**Contract**:
- **`test.sh` (Docker na Macu):**
  1. Uruchamia tymczasowy kontener `postgres-ssl:18` na wolnym porcie `127.0.0.1`.
  2. Zakłada schemat z `szczur-radar-api/src/main/resources/db/migration/V*.sql` (psql, kolejno) oraz `flyway_schema_history` z wierszem dla wersji 5.
  3. Wstawia przykładowe wiersze, w tym sesje.
  4. Buduje obraz i uruchamia `backup.sh` z `BACKUP_REMOTE=:local:/out`.
- **Asercje `test.sh`:**
  - dump i manifest istnieją;
  - w `pg_restore -l` nie ma danych `spring_session`;
  - `restore-check` przeszedł;
  - retencja na 20 sztucznych plikach w `daily/` zostawia 14;
  - obcięty dump powoduje porażkę `restore-check` i brak wysyłki;
  - `nas-retention.sh --dry-run` wskazuje właściwe pliki.
- **Sprzątanie:** kontenery zawsze usuwane (`trap`).
- **`README.md`:** cel, zmienne, uruchomienie testu, ręczny `restore-check --serve` na pobranym dumpie, odsyłacz do runbooków w `deploy-plan.md` (sekcja F-01).

### Success Criteria:

#### Automated Verification:

- `bash -n tools/db-backup/*.sh` przechodzi bez błędów
- `docker build -t szczur-radar-db-backup tools/db-backup` kończy się sukcesem
- `docker run --rm --entrypoint pg_dump szczur-radar-db-backup --version` pokazuje wersję 18.x
- `tools/db-backup/test.sh` przechodzi (dump bez danych sesji, `restore-check` OK, retencja 14 z 20, obcięty dump → porażka bez wysyłki, `nas-retention --dry-run`)

#### Manual Verification:

- W logu z `test.sh` nie ma haseł ani kluczy (przegląd wyjścia)

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się, aż człowiek potwierdzi weryfikację ręczną.

---

## Phase 3: Serwis cron i bucket w IaC, bramka CI, weryfikacja w PR env

### Overview

`db-backup` i `db-backups` trafiają do `.railway/railway.ts`. Bramka CI zaczyna przepuszczać tworzenie nowych zasobów. Całość, razem z procedurą odtworzenia awaryjnego, sprawdzamy w PR env.

### Changes Required:

#### 1. IaC

**File**: `.railway/railway.ts`

**Intent**: Zadeklarować bucket i serwis cron, żeby plan IaC dla produkcji wychodził z kodem 0 i każde środowisko miało własną kopię bazy we własnym buckecie.

**Contract**:
- **Bucket:** `bucket("db-backups", { region: "ams" })`.
- **Serwis `db-backup`:**
  - `source: github(REPO, { branch, checkSuites: true })`, `rootDirectory: "tools/db-backup"`, `watchPatterns: ["tools/db-backup/**"]`;
  - `deploy: { cronSchedule: "0 2 * * *", restartPolicyType: "NEVER" }`, `replicas: { [REGION]: 1 }`;
  - `env`:
    - `PGHOST`…`PGDATABASE` jako referencje `db.env.*` (jak w API);
    - `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION` przez `ref(backups, "<ENDPOINT|BUCKET|ACCESS_KEY_ID|SECRET_ACCESS_KEY|REGION>")`;
    - `BACKUP_KEEP_DAILY: "14"`, `BACKUP_KEEP_MONTHLY: "12"`.
- Zasoby dopisane do `project(...).resources`.
- Komentarz wyjaśnia: dlaczego cron, dlaczego bucket per środowisko, że harmonogram jest w UTC i że S-05 może zmienić godzinę.
- `BACKUP_REMOTE` składa skrypt z `S3_BUCKET`.
- Nazwy wyjść `ref` sprawdzamy `railway config plan` przed commitem.

#### 2. Bramka CI

**File**: `.github/workflows/ci.yml` (krok „Plan and gate”, ok. `:315-341`)

**Intent**: Pozwolić IaC tworzyć nowe zasoby (ten serwis, a w przyszłości np. cron z S-05), zachowując ochronę przed przepisaniem źródła istniejącego serwisu i przed zmianami destrukcyjnymi.

**Contract**:
- Filtr `source` pomija zmiany, które tworzą nowy zasób (rodzaj zmiany `resource.create`; dokładną nazwę pola potwierdza JSON z `railway config plan --json`).
- Filtry `severity == "destructive"` i `kind == "resource.delete"` bez zmian.
- `--confirm-destructive` nigdy.
- Komentarz przy kroku opisuje, dlaczego tworzenie jest dozwolone.

#### 3. Zapis w `deploy-plan.md`

**File**: `context/deployment/deploy-plan.md` (sekcja F-01)

**Intent**: Opisać serwis kopii i procedurę odtworzenia awaryjnego, zanim przećwiczymy ją w PR env.

**Contract**: Podsekcje:
- **„Jak działa kopia”:** serwis, harmonogram, bucket, nazwy obiektów, manifest, retencja, koszt.
- **„Runbook — odtworzenie bazy z dumpa”** (tylko człowiek):
  1. Żaden przebieg nie jest `RUNNING`.
  2. Wybór dumpa z bucketu.
  3. `restore-check.sh` lokalnie.
  4. `railway connect postgres -e <env> --tunnel-only`.
  5. `pg_restore --clean --if-exists --no-owner --no-acl --single-transaction -d <db>` przez tunel.
  6. Redeploy API.
  7. `/api/ready`, logowanie i `/api/posts` (`total`).

  Sesje nie są w dumpie, więc wszyscy logują się ponownie.
- **„Weryfikacja w PR env”:** wyniki tej fazy.

#### 4. Powiadomienie o nieudanym przebiegu (operator)

**File**: brak (ustawienia Railway)

**Intent**: Porażka kopii nie może przejść niezauważona.

**Contract**: Operator potwierdza, że Railway wysyła e-mail o nieudanym lub przerwanym wdrożeniu (ustawienia powiadomień konta). Sprawdzamy to celowo wywołaną porażką w PR env.

### Success Criteria:

#### Automated Verification:

- `railway config plan` dla PR env (dry run, `IAC_SOURCE_BRANCH=feature/production-data-gate`) pokazuje tylko utworzenie bucketu `db-backups` i serwisu `db-backup` z jego zmiennymi, bez zmian destrukcyjnych
- Filtr bramki uruchomiony lokalnie (`jq`) na tym planie przepuszcza go, a na planie ze zmianą źródła istniejącego serwisu (np. dry run z innym `IAC_SOURCE_BRANCH`) odmawia
- CI na PR jest zielone, łącznie z `railway-config` (apply do PR env)

#### Manual Verification:

- W PR env `db-backup` zbudował się z `tools/db-backup/Dockerfile`, a przebieg (harmonogram tymczasowo `*/5 * * * *`) skończył się kodem 0; log pokazuje rozmiar dumpa, `restore-check` OK i wysyłkę
- Bucket PR env zawiera `daily/…dump` z manifestem, a w manifeście liczby wierszy mieszczą się w przedziale [przed, po]
- Celowo zepsuty przebieg w PR env (np. tymczasowo zły `S3_BUCKET`) kończy się kodem ≠ 0 i przychodzi e-mail Railway; potem przywrócenie wartości i harmonogramu z IaC
- Test z aplikacją na dumpie z PR env: `restore-check.sh --serve` lokalnie, potem `java -jar target/szczur-radar-api.jar migrate` mówi „Schema … is up to date”, API startuje, logowanie admina działa, a `/api/posts` ma `total` równy temu w PR env
- Próba runbooka odtworzenia awaryjnego na bazie PR env: po `pg_restore --clean …` `/api/ready` daje 200, logowanie działa, a `/api/posts` ma ten sam `total`

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się, aż człowiek potwierdzi weryfikację ręczną.

---

## Phase 4: Limit kosztów OpenRouter

### Overview

Wyczerpany limit kredytu klucza OpenRouter ma zatrzymać przebieg jak każdy błąd konfiguracji, a nie zużywać próby postów. Operator ustawia limit $10/mies. na kluczu produkcji.

### Changes Required:

#### 1. Klasyfikacja 403 z limitu klucza

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterClient.java`

**Intent**: Rozpoznać 403 oznaczające wyczerpany budżet klucza i potraktować je jako `TransportException.Kind.CONFIGURATION`. Każde inne 403 (moderacja, blokada guardrail) zostaje `REJECTED`.

**Contract**: Dotyczy błędu HTTP (gałąź `OpenAIServiceException`, `:167-170`) i błędu w treści odpowiedzi 200 (`failedAnswer`, `:200-211`). Status lub `error.code` 403 daje `CONFIGURATION`, gdy spełniony jest którykolwiek warunek:
- `error.message` zaczyna się od „Key limit exceeded”;
- `error.metadata.limit_source` należy do zbioru {`openrouter_key_limit`, `openrouter_workspace_budget`, `openrouter_guardrail_budget`}.

Pozostałe zasady:
- Komunikat przechodzi przez `ProviderMessages.clean`.
- Pozostałe statusy bez zmian.
- `TransportException.kindOf` zostaje ogólne, bo reguła dotyczy tylko OpenRoutera.

#### 2. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/llm/openrouter/OpenRouterClientTest.java`

**Intent**: Udowodnić klasyfikację i brak ponowień na `ScriptedProviderServer`.

**Contract**: Ciała są krótkim syntetycznym JSON-em w formacie OpenRouter (`llm/CLAUDE.md`, Adding a provider, punkt 4). Przypadki:
- (a) HTTP 403 `{"error":{"code":403,"message":"Key limit exceeded (monthly limit). …"}}` daje `LlmConfigurationException` po 1 żądaniu;
- (b) HTTP 403 z `metadata.limit_source = "openrouter_key_limit"` bez tego prefiksu daje `LlmConfigurationException`;
- (c) HTTP 403 moderacji (inny komunikat, bez `limit_source`) daje porażkę `REJECTED` (`provider_error`) po 1 żądaniu;
- (d) odpowiedź 200 z `{"error":{"code":403,"message":"Key limit exceeded …"}}` daje `LlmConfigurationException`.

#### 3. Dokumentacja pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md`

**Intent**: Zaktualizować punkt „A refused configuration is not a failed request”: 403 z wyczerpanego budżetu klucza OpenRouter to konfiguracja, reszta 403 zostaje `REJECTED`. Dodać źródło (docs OpenRouter, limity kluczy).

**Contract**: Ten punkt i punkt o błędach w treści odpowiedzi 200 (lista kodów).

#### 4. Limit na kluczu (operator)

**File**: brak (panel OpenRouter)

**Intent**: Twardy pułap kosztów LLM, którego limity Railway nie obejmują.

**Contract**: Klucz produkcji → Key limit: $10, reset miesięczny (o północy UTC). Klucza CI nie zmieniamy.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B test -Dtest=OpenRouterClientTest` przechodzi (przypadki a–d)
- `./mvnw -B verify` przechodzi
- CI na PR jest zielone

#### Manual Verification:

- Panel OpenRouter pokazuje na kluczu produkcji limit $10 z resetem miesięcznym
- `GET /api/admin/llm/check` na produkcji (zalogowany admin) zwraca `"ok":true` po ustawieniu limitu

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się, aż człowiek potwierdzi weryfikację ręczną.

---

## Phase 5: Dokumenty i zamknięcie bramki

### Overview

Dokumenty źródłowe i operacyjne opisują nowy stan. Kroki produkcyjne po merge'u są gotowe jako runbook.

### Changes Required:

#### 1. PRD

**File**: `context/foundation/prd.md`

**Intent**: Usunąć sprzeczność z decyzją użytkownika (research, Follow-up, flaga PRD).

**Contract**:
- NFR (`:252`): ochrona przed utratą danych to codzienna kopia logiczna (`pg_dump`) robiona przez osobny serwis infrastruktury i sprawdzana odtworzeniem, z kopią poza Railway na NAS operatora. Railway Hobby nie ma kopii platformy.
- Non-Goal (`:287`): bez logiki backupu w kodzie aplikacji.
- OQ4 (`:299`): oznaczyć jako ROZSTRZYGNIĘTE (F-01, data).
- Frontmatter `updated`.

Treść akceptuje użytkownik w przeglądzie PR.

#### 2. Roadmapa i obrazek

**File**: `context/foundation/roadmap.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`

**Intent**: Wpis F-01 i otwarte pytania mają odpowiadać nowej decyzji.

**Contract**:
- Wpis F-01:
  - Outcome: Hobby, wolumen 5 GB, codzienny `pg_dump` sprawdzany odtworzeniem, bucket + NAS, limity $5/$10, limit OpenRouter, sekrety;
  - Unknowns rozstrzygnięte;
  - Unlocks: „przed S-05” zamiast „przed fazą 6”.
- OQ4 (`:260`) rozstrzygnięte.
- OQ5 (`:261`): dopisać pułap $10/mies. na kluczu OpenRouter (budżet na przebieg nadal otwarty).
- Nowa linia w changelogu (v2.12).
- Karta F-01 w `render_roadmap.swift` (szczegóły i status) zgodna z roadmapą, potem ponowne wygenerowanie PNG (`swift tools/render_roadmap.swift`).
- Status wpisu prowadzą skille (`/10x-implement`, `/10x-archive`).

#### 3. Infrastruktura

**File**: `context/foundation/infrastructure.md`

**Intent**: Rejestr ryzyk i opis operacyjny zgodne ze stanem.

**Contract**:
- Notka „Update 2026-10-xx (F-01)” na górze.
- Risk #1: łagodzenie to cron `pg_dump` + sprawdzenie odtworzeniem + NAS. Powrót do Render tylko, gdy kopia przestanie działać.
- Wiersz kosztów: limity $5/$10 ustawione.
- Operational Story: punkty o backupie i rollbacku.

#### 4. Dokument wdrożenia

**File**: `context/deployment/deploy-plan.md`

**Intent**: Domknąć sekcję F-01 i oznaczyć nieaktualne miejsca.

**Contract**:
- **Runbook po merge'u** (operator; śledzony w issue F-01):
  1. CI na `main` stosuje bucket i serwis na produkcji.
  2. Pierwszy przebieg produkcyjny (02:00 UTC albo tymczasowy harmonogram) kończy się kodem 0, a w buckecie są dump i manifest.
  3. Test z aplikacją na dumpie produkcyjnym (jak w fazie 3).
  4. Synology Cloud Sync: dostawca S3, adres i klucze bucketu produkcji z panelu (Credentials), tylko pobieranie, bez usuwania plików skasowanych w źródle; do folderu na NAS.
  5. Zadanie DSM z `nas-retention.sh` raz dziennie, po synchronizacji.
  6. Zapis wyników i komentarz w issue, potem zamknięcie issue.
- **Notki „Update 2026-10-xx (F-01)”** przy nieaktualnych zdaniach. Datowanej historii nie przepisujemy:
  - bramka „before Phase 6” (`:17`, `:347`);
  - „no real data” (`:116`, `:198`);
  - $15/$25 (`:14`, `:272`, `:282`);
  - nieistniejący krok CI z `JWT_SECRET` (`:29`);
  - przepis Daily (`:117`);
  - „Backups can't be enabled” w Support steps (`:323`).

#### 5. Wytyczne repo i README API

**File**: `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`

**Intent**: Zastąpić zdania „brak backupu do subskrypcji”.

**Contract**:
- `REPOSITORY_GUIDELINES.md:26` i `szczur-radar-api/README.md:125`: ostatecznością jest odtworzenie z codziennego dumpa (runbook w `deploy-plan.md`, F-01).
- `REPOSITORY_GUIDELINES.md` → Project Structure: krótki punkt o `tools/db-backup/`.
- `REPOSITORY_GUIDELINES.md` → Deployment: punkt o serwisie `db-backup`, buckecie per środowisko i kopii na NAS.

#### 6. Lekcja

**File**: `context/foundation/lessons.md` (tylko dopisanie)

**Intent**: Utrwalić wzorzec, który podważył plan z 2026-10-06.

**Contract**: Nowy wpis w formacie pliku:
- Railway Hobby nie ma kopii wolumenu ani PITR (Pro).
- Wolumen z trialu (500 MB) nie rośnie sam przy zmianie planu, a domyślny `max_wal_size` 1 GB może go zapełnić.
- Reguła: przed planowaniem backupu sprawdź tabelę funkcji planu (`railway.com/pricing`). Kopię logiczną zawsze sprawdzaj pełnym odtworzeniem przed uznaniem jej za ważną.
- Applies to: infra, plan, plan-review.

### Success Criteria:

#### Automated Verification:

- `grep -n "there is none until the Railway subscription" REPOSITORY_GUIDELINES.md` nic nie zwraca
- `grep -n "do czasu subskrypcji Railway backupu nie ma" szczur-radar-api/README.md` nic nie zwraca
- `swift tools/render_roadmap.swift` generuje `docs/visuals/roadmap-mvp.png` bez błędu
- `git diff --stat context/foundation/lessons.md` pokazuje tylko dodane linie

#### Manual Verification:

- Użytkownik akceptuje w przeglądzie PR nowe brzmienie NFR, Non-Goal i OQ4 w PRD
- Karta F-01 na `docs/visuals/roadmap-mvp.png` pokazuje nowy opis
- Runbook po merge'u w `deploy-plan.md` jest kompletny: produkcyjny przebieg, test z aplikacją, Cloud Sync, retencja na NAS, issue

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się, aż człowiek potwierdzi weryfikację ręczną. Potem `/10x-impl-review` i `/10x-archive` na tej samej gałęzi. Merge robi człowiek.

---

## Testing Strategy

### Unit Tests:

- `OpenRouterClientTest`: przypadki a–d z fazy 4. Każdy liczy żądania, co dowodzi braku ponowień.

### Integration Tests:

- `tools/db-backup/test.sh`: prawdziwy Postgres 18 z migracjami repo, dump bez danych sesji, odtworzenie, retencja, obcięty dump, `nas-retention --dry-run`.
- PR env: pełny przebieg cron w Railway z bucketem, celowa porażka z powiadomieniem, test z aplikacją, próba runbooka odtworzenia awaryjnego.

### Manual Testing Steps:

1. Faza 1: `df -h` w kontenerze Postgresa produkcji pokazuje ~4,6–4,7 GB.
2. Faza 3: przebieg `db-backup` w PR env kończy się kodem 0, a celowa porażka daje e-mail.
3. Faza 3: dump z PR env → `restore-check --serve` → API `migrate` i logowanie → `total` w `/api/posts` się zgadza.
4. Faza 3: runbook odtworzenia awaryjnego na bazie PR env.
5. Faza 4: limit $10 widoczny w panelu OpenRouter, `/api/admin/llm/check` ok.

## Performance Considerations

- **Przebieg kopii:** `pg_dump` bazy rzędu kilkuset MB to sekundy, a `restore-check` około minuty. Cron działa kilka minut dziennie, więc koszt CPU i RAM jest pomijalny.
- **Egress:** wysyłka do bucketu liczy się jako egress ($0,05/GB). Przy dumpach rzędu 0,1–0,5 GB po roku to ~$0,15–0,75/mies. Bucket kosztuje $0,015/GB-mies. Zmieści się w limicie $10.
- **Wpływ na bazę:** `pg_dump` czyta jeden snapshot i nie blokuje zapisów przebiegu pipeline. O 02:00 UTC zwykle nic nie działa (przebiegi są ręczne do S-05).

## Migration Notes

- Bez migracji Flyway. Schemat bazy się nie zmienia.
- IaC dodaje dwa zasoby w każdym środowisku. PR env dostają własny cron i bucket (grosze), które znikają z zamknięciem PR.
- Starsze gałęzie bez `db-backup` w `railway.ts` trzeba zrebase'ować na `main`, zanim ich PR env zostanie utworzony po merge'u (Critical Implementation Details).
- Gałąź `feature/production-data-gate` istnieje (z `origin/main` 771b451, 2026-10-11). Issue `F-01: Bramka produkcyjnych danych — …` i PR powstają na starcie fazy 1, a folder zmiany trafia do pierwszego commita.

## References

- Research: `context/changes/production-data-gate/research.md` (z sekcją Follow-up 2026-10-11)
- Roadmapa F-01: `context/foundation/roadmap.md:99-112`; OQ4/OQ5: `:260-261`
- PRD: `context/foundation/prd.md:49,252,287,299`
- Bramka CI: `.github/workflows/ci.yml:315-341`
- IaC: `.railway/railway.ts`; SDK `node_modules/railway/dist/index-C3uk0ruc.d.ts` (`bucket`, `ref`, `DeployConfig.cronSchedule`)
- Klient LLM: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/openrouter/OpenRouterClient.java:161-215`, `llm/transport/TransportException.java:72-82`, `llm/CLAUDE.md`
- Wzorzec poprzedniego planu klienta OpenRouter: `context/archive/2026-10-10-openrouter-in-body-errors/plan.md`
- Docs Railway: `docs.railway.com/volumes` (Live Resize), `volumes/reference` (rozliczenie za zajęte miejsce), `storage-buckets` (zmienne, bucket per środowisko), `cron-jobs`, `guides/postgres-backups-restores`
- Docs OpenRouter: `openrouter.ai/docs/guides/best-practices/capping-shared-agent-keys`, `openrouter.ai/docs/api_reference/limits`
- PostgreSQL 18: `app-pgdump.html` (`--exclude-table-data`), `app-pgrestore.html` (`--single-transaction`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Wolumen 5 GB i pomiar bazy

#### Automated

- [x] 1.1 `railway volume -e <production-env-id> list --json` pokazuje `postgres-volume` z `sizeMB: 5000` — e4f3166
- [x] 1.2 `railway config plan` dla `production` (dry run, bez apply) nie pokazuje żadnej zmiany (`--detailed-exit-code` = 0) — e4f3166
- [x] 1.3 `curl -s https://szczur-radar-web-production.up.railway.app/api/ready` zwraca 200 — e4f3166

#### Manual

- [x] 1.4 `df -h /var/lib/postgresql/data` w kontenerze `postgres` (produkcja) pokazuje ~4,6–4,7 GB rozmiaru, nie ~434 MB — e4f3166
- [x] 1.5 Logowanie admina na produkcji działa po zmianie rozmiaru — e4f3166
- [x] 1.6 Sekcja F-01 w `deploy-plan.md` zawiera pomiary bazy, wynik `df -h` i stan limitów oraz sekretów (same nazwy) — e4f3166

### Phase 2: Narzędzie kopii `tools/db-backup/`

#### Automated

- [x] 2.1 `bash -n tools/db-backup/*.sh` przechodzi bez błędów — 0cd1f10
- [x] 2.2 `docker build -t szczur-radar-db-backup tools/db-backup` kończy się sukcesem — 0cd1f10
- [x] 2.3 `docker run --rm --entrypoint pg_dump szczur-radar-db-backup --version` pokazuje wersję 18.x — 0cd1f10
- [x] 2.4 `tools/db-backup/test.sh` przechodzi (dump bez danych sesji, `restore-check` OK, retencja 14 z 20, obcięty dump → porażka bez wysyłki, `nas-retention --dry-run`) — 0cd1f10

#### Manual

- [x] 2.5 W logu z `test.sh` nie ma haseł ani kluczy (przegląd wyjścia) — 0cd1f10

### Phase 3: Serwis cron i bucket w IaC, bramka CI, weryfikacja w PR env

#### Automated

- [x] 3.1 `railway config plan` dla PR env (dry run, `IAC_SOURCE_BRANCH=feature/production-data-gate`) pokazuje tylko utworzenie bucketu `db-backups` i serwisu `db-backup` z jego zmiennymi, bez zmian destrukcyjnych — fb49846
- [x] 3.2 Filtr bramki uruchomiony lokalnie (`jq`) na tym planie przepuszcza go, a na planie ze zmianą źródła istniejącego serwisu (np. dry run z innym `IAC_SOURCE_BRANCH`) odmawia — fb49846
- [x] 3.3 CI na PR jest zielone, łącznie z `railway-config` (apply do PR env) — fb49846

#### Manual

- [x] 3.4 W PR env `db-backup` zbudował się z `tools/db-backup/Dockerfile`, a przebieg (harmonogram tymczasowo `*/5 * * * *`) skończył się kodem 0; log pokazuje rozmiar dumpa, `restore-check` OK i wysyłkę — fb49846
- [x] 3.5 Bucket PR env zawiera `daily/…dump` z manifestem, a w manifeście liczby wierszy mieszczą się w przedziale [przed, po] — fb49846
- [x] 3.6 Celowo zepsuty przebieg w PR env (np. tymczasowo zły `S3_BUCKET`) kończy się kodem ≠ 0 i przychodzi e-mail Railway; potem przywrócenie wartości i harmonogramu z IaC — fb49846
- [x] 3.7 Test z aplikacją na dumpie z PR env: `restore-check.sh --serve` lokalnie, potem `java -jar target/szczur-radar-api.jar migrate` mówi „Schema … is up to date”, API startuje, logowanie admina działa, a `/api/posts` ma `total` równy temu w PR env — fb49846
- [x] 3.8 Próba runbooka odtworzenia awaryjnego na bazie PR env: po `pg_restore --clean …` `/api/ready` daje 200, logowanie działa, a `/api/posts` ma ten sam `total` — fb49846

### Phase 4: Limit kosztów OpenRouter

#### Automated

- [x] 4.1 `./mvnw -B test -Dtest=OpenRouterClientTest` przechodzi (przypadki a–d) — f672dc8
- [x] 4.2 `./mvnw -B verify` przechodzi — f672dc8
- [x] 4.3 CI na PR jest zielone — f672dc8

#### Manual

- [x] 4.4 Panel OpenRouter pokazuje na kluczu produkcji limit $10 z resetem miesięcznym — f672dc8
- [x] 4.5 `GET /api/admin/llm/check` na produkcji (zalogowany admin) zwraca `"ok":true` po ustawieniu limitu — f672dc8

### Phase 5: Dokumenty i zamknięcie bramki

#### Automated

- [x] 5.1 `grep -n "there is none until the Railway subscription" REPOSITORY_GUIDELINES.md` nic nie zwraca — cedc525
- [x] 5.2 `grep -n "do czasu subskrypcji Railway backupu nie ma" szczur-radar-api/README.md` nic nie zwraca — cedc525
- [x] 5.3 `swift tools/render_roadmap.swift` generuje `docs/visuals/roadmap-mvp.png` bez błędu — cedc525
- [x] 5.4 `git diff --stat context/foundation/lessons.md` pokazuje tylko dodane linie — cedc525

#### Manual

- [x] 5.5 Użytkownik akceptuje w przeglądzie PR nowe brzmienie NFR, Non-Goal i OQ4 w PRD — cedc525
- [x] 5.6 Karta F-01 na `docs/visuals/roadmap-mvp.png` pokazuje nowy opis — cedc525
- [x] 5.7 Runbook po merge'u w `deploy-plan.md` jest kompletny: produkcyjny przebieg, test z aplikacją, Cloud Sync, retencja na NAS, issue — cedc525
