# S-15: kopia bazy sprawdzana w całości — plan implementacji

## Overview

Nocne sprawdzenie kopii bazy (`tools/db-backup/`) zyskuje dwie zdolności:
- **Zna każdą tabelę z migracji.** Wymaganie tabeli zależy od wersji Flyway dumpa, a tabela, której narzędzie nie zna, kończy kontrolę błędem.
- **Odrzuca kopię podejrzanie pustej bazy, zanim trafi do bucketu.** Odrzucenie następuje w dwóch przypadkach:
  - rosnąca tabela ma mniej wierszy niż w najnowszym manifeście;
  - `posts` jest pusta.
  
  Wyjątkiem jest jednorazowa zgoda operatora po celowym odtworzeniu.

Źródła: roadmapa S-15 (`context/foundation/roadmap.md:236-246`) i audyt C9 (`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:98`).

## Current State Analysis

Na podstawie `context/changes/backup-check-completeness/research.md`:

- **Wymagane tabele.** `restore-check.sh:12-13` wymaga 9 tabel, czyli schematu z V5. Nie ma wśród nich `activation_tokens` (V7) ani `notifications` (V9). Nowa tabela przechodzi po cichu, bo `test.sh:93-105` stosuje wszystkie migracje, ale nie porównuje tabel z listami narzędzia.
- **Liczone tabele.** `backup.sh:21` liczy 5 tabel. `restore-check.sh:108-116` sprawdza dla każdej tabeli z manifestu, czy liczba wierszy mieści się w przedziale [przed, po]. To dowodzi spójności migawki, ale nie wiarygodności danych: pusta baza daje [0, 0] i przechodzi.
- **Kontrola TOC.** `backup.sh:171` wymaga tylko wpisu `TABLE DATA public posts` w TOC, a taki wpis istnieje także dla pustej tabeli.
- **Usuwanie wierszy.** Wiersze tracą dokładnie 3 tabele:
  - `activation_tokens`: `ActivationLinks.java:168`;
  - `spring_session`: `ActivationLinks.java:196`, wylogowanie, czyszczenie Spring Session;
  - `spring_session_attributes`: kaskada.
  
  Pozostałe 8 tabel tylko rośnie, w tym `pipeline_run_steps` i `notifications`.
- **Wcześniejsza decyzja.** `activation_tokens` pominięto celowo: tabela ma ścieżkę usuwania, a twarde wymaganie złamałoby starsze dumpy (`context/archive/2026-10-12-cli-user-onboarding/plan.md:60`).
- **Starsze dumpy.**
  - Kopia miesięczna `monthly/szczur-radar-2026-10` jest z Flyway V5 (`deploy-plan.md:388`, `backup.sh:208-217`).
  - Retencja trzyma kopie miesięczne 12 miesięcy (`.railway/railway.ts:242`).
  - Runbook odtworzenia uruchamia `restore-check.sh` na pobranym dumpie (`deploy-plan.md:302-308`).
- **Bucket i porażka przebiegu.**
  - Tylko `backup.sh` ma dostęp do bucketu. `list_files` (`backup.sh:104-118`) zwraca dokładne nazwy posortowane po czasie.
  - Porażka przed wysyłką nie dotyka retencji, bo `prune` działa dopiero po wysyłce (`backup.sh:204-220`).
- **Zmienne `db-backup` w IaC.**
  - Serwis nie ma żadnego `preserve()` (`.railway/railway.ts:229-243`).
  - Zmienna ustawiona ręcznie bez deklaracji czerwieni job `railway-config` (`ci.yml:353-370`, audyt C7).
  - Wzorzec deklaracji: `TRIGGER_IN_ANY_ENVIRONMENT: preserve()` (`.railway/railway.ts:273`).
  - Zmiana zmiennej nie przedeployowuje crona, a przebieg używa istniejącego wdrożenia. Potrzebny jest redeploy (`deploy-plan.md:502`).
- **PR env.** PR env nie ma danych produkcji. Przy PR, który zmienia tylko `tools/db-backup`, może nie mieć nawet bazy (`deploy-plan.md:343-346`).
- **Brak Wait for CI dla crona.** Cron `db-backup` wdraża się bez Wait for CI (audyt C5), więc `db-backup-test` musi przejść w PR.

## Desired End State

- **Klasyfikacja tabel.** `tools/db-backup/tables.sh` klasyfikuje każdą tabelę schematu `public`, a czytają go `backup.sh` i `restore-check.sh`.
- **`restore-check.sh`:**
  - wymaga tabeli tylko wtedy, gdy wersja Flyway odtworzonej bazy jest ≥ wersji, od której tabela istnieje;
  - kończy się błędem `tables: unknown <t>` dla tabeli, której klasyfikacja nie zna.
  
  Dump z V5 przechodzi, a dump z nową, niesklasyfikowaną tabelą nie przechodzi.
- **Liczone tabele.** `backup.sh` liczy 7 rosnących tabel, czyli dzisiejsze 5 oraz `pipeline_run_steps` i `notifications`.
- **Kontrola przed dumpem.** Przed dumpem `backup.sh` kończy przebieg kodem ≠ 0, bez dumpa i bez wysyłki, w 2 przypadkach:
  - `posts` ma 0 wierszy;
  - którakolwiek rosnąca tabela ma mniej wierszy niż w `counts_after` najnowszego manifestu w `daily/`, a `BACKUP_ACCEPT_SHRINK` nie wskazuje tego manifestu.
  
  Komunikat błędu podaje gotową wartość zgody.
- **Manifest** dostaje 2 pola: `compared_with` (nazwa porównanego manifestu albo `null`) i `accepted_shrink` (`true`/`false`).
- **Testy.** `tools/db-backup/test.sh` i job CI `db-backup-test` pokrywają:
  - dump z V5;
  - nieznaną tabelę;
  - spadek;
  - zgodę, także wygasłą;
  - pustą bazę;
  - manifest z nowymi tabelami.
- **IaC.** `.railway/railway.ts` deklaruje `BACKUP_IN_ANY_ENVIRONMENT` i `BACKUP_ACCEPT_SHRINK` przez `preserve()`.
- **Dokumenty:**
  - runbook odtworzenia ma krok o zgodzie;
  - `REPOSITORY_GUIDELINES.md` mówi, że nowa tabela trafia do `tables.sh` w tym samym PR.

Weryfikacja: `tools/db-backup/test.sh` lokalnie i w CI, przebieg w PR env (OK → spadek `CRASHED` → zgoda OK) oraz lokalny `restore-check.sh` na prawdziwych dumpach z V5 i V9.

### Key Discoveries:

- `restore-check.sh:108` iteruje po kluczach `counts_before` z manifestu. Dodanie tabel do liczonych nie psuje więc starszych manifestów.
- `restore-check.sh:92-95` sprawdza tabele **przed** odczytem wersji Flyway (`:100-102`). Wymaganie zależne od wersji wymaga odwrócenia tej kolejności.
- `test.sh:223-232` usuwa `pipeline_run_steps`, żeby sprawdzić komunikat `tables: missing`. Gdy ta tabela trafi do liczonych, `backup.sh` przerwie się już na liczeniu wierszy przed dumpem (`backup.sh:162`). Test musi więc usuwać tabelę tylko wymaganą, czyli `activation_tokens`.
- `test.sh:166-178` tworzy w `daily/` puste, fałszywe manifesty z 2020 roku. Są starsze od prawdziwych, więc nie zostaną „najnowszym”. Nazwa z datą w przyszłości musi być jednak pomijana, tak jak robi to `nas-retention.sh`.
- `backup.sh:157-162` odczytuje wersję Flyway źródła przed liczeniem wierszy. Liczenie może więc pominąć tabele nowsze niż schemat bazy źródłowej.

## What We're NOT Doing

- Nie deklarujemy `preserve()` dla pozostałych zmiennych `BACKUP_*`: `BACKUP_TIMEOUT`, `BACKUP_REMOTE`, `S3_FORCE_PATH_STYLE`. Reszta C7 należy do S-14 (`safe-deploy-gate`).
- Nie zmieniamy Wait for CI dla crona (C5, S-14) ani reguły powiadomień Railway.
- Nie liczymy wierszy `activation_tokens` ani tabel sesji. Ich wiersze znikają, więc są tylko „obecne”.
- Nie dodajemy progu procentowego ani tolerancji spadku. Przy bezterminowej retencji z PRD każdy spadek rosnącej tabeli to błąd albo celowe odtworzenie.
- Nie zapisujemy listy tabel w manifeście. Klasyfikacja jest stała, w skryptach.
- Nie zmieniamy retencji, kopii miesięcznej, NAS ani Google Drive. Nie dodajemy też dead-man's switch dla crona (C2, F-04).
- Nie automatyzujemy odtwarzania. Krok zgody to runbook człowieka.
- Pozostałe nieaktualne zdania w dokumentach operatora należą do S-19. Tutaj poprawiamy tylko zdania „CI does not test tools/db-backup” w miejscach, które i tak edytujemy (`railway.ts:221-222`, `deploy-plan.md:347`).

## Implementation Approach

1. **Faza 1.** Jedno źródło wiedzy o tabelach (`tables.sh`) i kontrole w `restore-check.sh`. Kopia zgodna wstecz, ale kompletna.
2. **Faza 2.** Nowa kontrola wiarygodności w `backup.sh`, przed dumpem. Tylko ten skrypt widzi bucket, a porażka przed dumpem nie rusza retencji ani nie zużywa 2 h limitu.
3. **Faza 3.** IaC, dokumenty i weryfikacja na żywym Railway.

Każda faza kończy się zielonym `tools/db-backup/test.sh`.

## Critical Implementation Details

- **Kolejność w `test.sh`.** Przypadek pustej bazy opróżnia `posts`. Każdy kolejny przebieg odpadłby wtedy na minimum, także test brakującej tabeli z kroku 3 i test limitu czasu. Pustą bazę trzeba więc sprawdzać jako ostatnią albo na osobnej bazie.
- **Nieznana tabela przed brakującą.** `restore-check.sh` sprawdza `missing` przed `unknown`, a krok 3 usuwa `activation_tokens` i nie przywraca tej tabeli. Przypadek `stray` biegnie więc w kroku 3 przed `drop table activation_tokens`, a w fazie 2 po kroku „spadek i zgoda”. Inaczej padnie na `tables: missing activation_tokens`.
- **Spadek przepuszczony przez zgodę.** Ten przebieg zostawia w `daily/` nowy, mniejszy manifest. Każdy następny test porównuje się już z nim.
- **Zgoda na produkcji wymaga redeployu.** Ustawienie zmiennej nie zmienia trwającego wdrożenia crona (`deploy-plan.md:502`). Runbook musi kazać przedeployować `db-backup` po ustawieniu `BACKUP_ACCEPT_SHRINK`.

## Faza 1: Klasyfikacja tabel i restore-check

### Overview

Każda tabela schematu `public` ma klasyfikację. `restore-check.sh` wymaga tabel zgodnie z wersją Flyway dumpa i odrzuca nieznane. `backup.sh` liczy wszystkie rosnące tabele.

### Changes Required:

#### 1. Klasyfikacja tabel

**File**: `tools/db-backup/tables.sh` (nowy)

**Intent**: Jedno miejsce, które mówi, jakie tabele ma schemat i jak je sprawdzać. Nowa migracja z tabelą zmienia tylko ten plik.

**Contract**: Plik źródłowy bash, bez efektów ubocznych. Definiuje tablicę wpisów `<tabela> <wersja Flyway, od której istnieje> <tryb>`:
- `counted`: tabela jest wymagana i rośnie. Liczona przed dumpem i po nim, porównywana z poprzednim manifestem.
- `present`: tabela jest tylko wymagana. Jej wiersze mogą znikać albo są wykluczone z dumpa.

Wpisy (11):

| Tabela | Wersja | Tryb |
|---|---|---|
| `users` | 1 | `counted` |
| `flyway_schema_history` | 1 | `present` |
| `spring_session`, `spring_session_attributes` | 2 | `present` |
| `pipeline_runs`, `pipeline_run_steps`, `raw_payloads`, `posts`, `assessments` | 3 | `counted` |
| `activation_tokens` | 7 | `present` |
| `notifications` | 9 | `counted` |

Komentarz przy każdym wpisie `present` podaje powód (ścieżka usuwania albo dane wykluczone z dumpa). Komentarz nagłówka mówi, że migracja dodająca tabelę dopisuje ją tutaj, a `restore-check.sh` odrzuca tabelę, której tu nie ma.

Plik daje 2 funkcje pomocnicze:
- tabele wymagane przy danej wersji Flyway;
- tabele `counted` przy danej wersji Flyway.

Wersje porównuje się liczbowo (`(( ))`), nigdy jako tekst, bo tekstowo `'10' < '2'`, a następna migracja to V10. Wersja, która nie pasuje do `^[0-9]+$`, daje błąd. Komentarz nagłówka mówi też, że wpisów się nie usuwa. Tabela usunięta w przyszłej migracji dostaje wtedy wersję „do”, a nie znika z listy, bo inaczej starsze kopie (miesięczne trzymane są 12 miesięcy) padłyby na `tables: unknown`.

```bash
# <table> <first Flyway version that has it> <counted|present>
readonly TABLES=(
    "users 1 counted"
    "flyway_schema_history 1 present" # Flyway's own; checked separately
    "spring_session 2 present"        # rows deleted (logout, expiry); data excluded from the dump
    ...
)
```

#### 2. Obraz

**File**: `tools/db-backup/Dockerfile`

**Intent**: Obraz zawiera `tables.sh` obok skryptów, które go czytają.

**Contract**: `COPY` obejmuje `tables.sh` do `/usr/local/bin/` z prawami `0644`, bo plik jest tylko czytany. Skrypty czytają go ze swojego katalogu: `source "$(dirname "${BASH_SOURCE[0]}")/tables.sh"`.

#### 3. restore-check.sh

**File**: `tools/db-backup/restore-check.sh`

**Intent**: Kontrola tabel zna cały schemat, nie odrzuca prawdziwych starszych dumpów i łapie dryf.

**Contract**:
- `REQUIRED_TABLES` znika, a skrypt czyta `tables.sh`.
- Wersję Flyway odczytuje przed kontrolą tabel, bo dziś czyta ją po niej (`:100-102`).
- Kontrole, w tej kolejności:
  1. `flyway` (bez zmian);
  2. `tables: missing …`: każda tabela z wersją ≤ wersji odtworzonej bazy musi istnieć;
  3. `tables: unknown …`: każda tabela bazowa w `public` (`information_schema.tables`, `BASE TABLE`) musi mieć klasyfikację. Komunikat mówi: „classify it in tables.sh”;
  4. sesje puste (bez zmian);
  5. porównanie z manifestem (bez zmian, nadal po kluczach manifestu).
- Linia `OK` podaje liczbę wymaganych tabel przy tej wersji.

#### 4. backup.sh: liczone tabele

**File**: `tools/db-backup/backup.sh`

**Intent**: Liczyć wszystkie rosnące tabele, a nie 5 wybranych.

**Contract**:
- `COUNTED_TABLES` znika. `count_rows` liczy tabele `counted` z `tables.sh` przy wersji Flyway źródła (`flyway_version`, odczytana w `:157-160`, przed liczeniem).
- Komentarz `:19-20` przechodzi do `tables.sh`.
- Komunikaty błędów liczenia (`:162`, `:167`) wymieniają liczone tabele tak jak dziś.

#### 5. Test

**File**: `tools/db-backup/test.sh`

**Intent**: Test chroni klasyfikację, zgodność wstecz i nowe tabele.

**Contract**:
- **Dane przykładowe** (`:107-135`) zyskują 1 wiersz `activation_tokens` (dla admina) i 1 wiersz `notifications` (admin + przebieg).
- **Sprawdzenie manifestu** (`:153-157`) obejmuje:
  - `counts_before.notifications == 1`;
  - `counts_after.pipeline_run_steps == 1`;
  - brak klucza `activation_tokens` w `counts_before`.
- **Nowy przypadek: dump z V5.**
  1. Druga baza w kontenerze testowym dostaje migracje V1–V5 z ich `flyway_schema_history`.
  2. Jej `pg_dump -Fc` z kontenera bazy trafia do pliku.
  3. `restore-check.sh` z obrazu przechodzi bez manifestu, a log podaje Flyway V5.
- **Nowy przypadek: nieznana tabela.**
  1. `create table stray (id int)` w bazie testowej.
  2. `backup.sh` kończy się ≠ 0, a log ma `restore-check failed; nothing uploaded` i `tables: unknown stray`.
  3. Backupy w `/out` się nie zmieniają.
  4. Tabela zostaje usunięta.
- **Zmiana w kroku 3 (`:223-232`).** Test usuwa `activation_tokens` zamiast `pipeline_run_steps` i oczekuje `tables: missing activation_tokens`.
- **Nagłówek** pliku (`:2-14`) wymienia nowe przypadki.

### Success Criteria:

#### Automated Verification:

- `tools/db-backup/test.sh` kończy się „All checks passed.”, w tym przypadkami: dump z V5 przechodzi, `tables: unknown stray`, `tables: missing activation_tokens`, manifest z `notifications` i `pipeline_run_steps`.
- `bash -n tools/db-backup/*.sh` nie zgłasza błędów składni.
- Job CI `db-backup-test` jest zielony w PR.

#### Manual Verification:

- `restore-check.sh` z obrazu tej gałęzi przechodzi lokalnie na prawdziwych dumpach z bucketu produkcji, każdy z manifestem:
  - `monthly/szczur-radar-2026-10` (Flyway V5);
  - najnowszy `daily/` (Flyway V9).
  
  Dumpy trzeba pobrać z panelu albo z NAS. Sprawdzenie idzie komendą z kroku 2 runbooka odtworzenia (`deploy-plan.md:302-306`), z obrazem zbudowanym z tej gałęzi.

**Implementation Note**: Po fazie i zielonej automatycznej weryfikacji zatrzymaj się na ręczne potwierdzenie przed fazą 2.

---

## Faza 2: Odrzucanie podejrzanie pustej kopii

### Overview

Przed dumpem `backup.sh` porównuje liczby wierszy z najnowszym manifestem w `daily/` i wymaga niepustej tabeli `posts`. Jednorazowa zgoda `BACKUP_ACCEPT_SHRINK` przepuszcza spadek tylko względem wskazanego manifestu.

### Changes Required:

#### 1. backup.sh: kontrola wiarygodności

**File**: `tools/db-backup/backup.sh`

**Intent**: Kopia wyczyszczonej albo okrojonej bazy nie trafia do bucketu, więc nie wyprze dobrych kopii z retencji ani z lustra NAS. Porażka jest głośna: `CRASHED` i powiadomienie przez regułę konta.

**Contract**:

**Miejsce.** Kontrola działa po `counts_before` (`:162`) i przed `pg_dump` (`:165`). Porażka kończy się komunikatem z „nothing dumped” i kodem ≠ 0.

**Najnowszy manifest.**
- Spośród plików `daily/` z `list_files` bierze `szczur-radar-<ts>.manifest.json`.
- Pomija nazwy późniejsze niż nazwa bieżącego przebiegu (`$name`), czyli z datą w przyszłości.
- Wybiera ostatni po sortowaniu.
- Czyta go przez `rclone cat`.
- Brak manifestu nie jest błędem: to pierwsza kopia w buckecie. Błąd odczytu albo niepoprawny JSON (`counts_after` nie jest obiektem liczb) kończy przebieg błędem, bo nie da się wtedy sprawdzić spadku. Komunikat podaje nazwę pliku i wskazówkę: jeśli manifest jest uszkodzony, zmień jego nazwę na taką spoza wzorca (np. `….manifest.json.broken`), a `list_files` go pominie. Zgoda `BACKUP_ACCEPT_SHRINK` tego przypadku nie obejmuje.

**Minimum.** `counts_before.posts > 0`, zawsze, także przy zgodzie. Komunikat: `posts is empty: refusing to back up an empty database; nothing dumped`.

**Brak spadku.**
- Porównanie obejmuje każdą tabelę z bieżącego `counts_before`, która ma liczbę także w `counts_after` poprzedniego manifestu. Tabela nowsza niż poprzedni manifest jest pomijana.
- Spadek zachodzi, gdy bieżąca liczba jest mniejsza od poprzedniej.
- Wtedy, bez zgody, przebieg kończy się jednym komunikatem, który:
  - wymienia każdą tabelę ze spadkiem w formie `posts 300 < 350`;
  - podaje nazwę porównanego manifestu;
  - podaje gotową instrukcję: `if the database was restored on purpose, set BACKUP_ACCEPT_SHRINK=szczur-radar-<ts> on db-backup and redeploy it`.

**Zgoda.**
- `BACKUP_ACCEPT_SHRINK` przyjmuje nazwę bazową porównanego manifestu, bez rozszerzenia.
- Gdy jest równa nazwie najnowszego manifestu, spadek jest akceptowany z logiem `accepted: … (BACKUP_ACCEPT_SHRINK)`.
- Gdy jest ustawiona na inną wartość, log mówi `ignoring BACKUP_ACCEPT_SHRINK=…: the newest manifest is …`, a kontrola działa normalnie.
- Po pierwszej udanej kopii najnowszym manifestem jest już nowy, więc zgoda sama przestaje działać.

**Manifest.** Dwa nowe pola, `format` zostaje `1`, bo zmiana jest addytywna:
- `compared_with`: nazwa pliku porównanego manifestu albo `null`;
- `accepted_shrink`: `true`, gdy zgoda została użyta.

**Komentarz nagłówka** (`:1-6`) opisuje nową kontrolę.

#### 2. Test

**File**: `tools/db-backup/test.sh`

**Intent**: Każda gałąź nowej kontroli ma przypadek, a kolejność przypadków zostawia bazę gotową dla kroków, które jej potrzebują.

**Contract**:
- **Pierwszy przebieg (`run1`):** `compared_with == null`, `accepted_shrink == false`.
- **Retencja (`run2`):** `compared_with` to manifest `run1`.
- **Nowy krok „spadek i zgoda”**, po retencji i przed krokiem 3:
  1. Manifest z datą w przyszłości (`szczur-radar-29991231T020000Z.manifest.json` z dużymi liczbami) jest pomijany: przebieg przechodzi, a `compared_with` nie wskazuje go. Plik zostaje potem usunięty, żeby nie psuć liczenia manifestów w innych krokach.
  2. Usunięcie 5 postów z ich ocenami daje porażkę ≠ 0 z `posts 35 < 40` i gotową wartością `BACKUP_ACCEPT_SHRINK=…` w logu. Backupy w `/out` się nie zmieniają.
  3. Przebieg z `-e BACKUP_ACCEPT_SHRINK=<ta wartość>` przechodzi, z `accepted_shrink == true` i `compared_with` wskazującym tę kopię.
  4. Kolejny przebieg z tą samą zmienną przechodzi: log ma `ignoring BACKUP_ACCEPT_SHRINK`, a `accepted_shrink == false`.
- **Nowy ostatni krok „pusta baza”**, po kroku 3:
  1. `truncate` tabel `assessments`, `posts` i `raw_payloads`.
  2. Przebieg z `BACKUP_ACCEPT_SHRINK` wskazującym najnowszy manifest kończy się ≠ 0 z `posts is empty`.
  3. Backupy w `/out` się nie zmieniają.
- **Nagłówek** pliku wymienia nowe przypadki.
- Krok „no secrets in the logs” obejmuje nowe logi bez zmian, bo bierze `"$tmp"/*.log`.

### Success Criteria:

#### Automated Verification:

- `tools/db-backup/test.sh` kończy się „All checks passed.”, w tym przypadkami: data z przyszłości pomijana, spadek odrzucony, zgoda przyjęta, zgoda wygasła, pusta baza odrzucona mimo zgody.
- `bash -n tools/db-backup/*.sh` nie zgłasza błędów składni.
- Job CI `db-backup-test` jest zielony w PR.

**Implementation Note**: Po fazie i zielonej automatycznej weryfikacji przejdź do fazy 3. Weryfikacja na żywym Railway jest w fazie 3.

---

## Faza 3: IaC, dokumenty i weryfikacja w PR env

### Overview

Zmienne nadpisania przetrwają plan IaC. Operator i przyszłe zmiany wiedzą o nowej kontroli i o klasyfikacji tabel. Przebieg w PR env potwierdza zachowanie na żywym Railway.

### Changes Required:

#### 1. Deklaracje zmiennych

**File**: `.railway/railway.ts`

**Intent**: Ręcznie ustawione `BACKUP_IN_ANY_ENVIRONMENT` (weryfikacja w PR env) i `BACKUP_ACCEPT_SHRINK` (po odtworzeniu) nie dają w planie usunięcia zmiennej, które czerwieni `railway-config` i zatrzymuje wdrożenia.

**Contract**:
- `env` serwisu `db-backup` (`:229-243`) zyskuje `BACKUP_IN_ANY_ENVIRONMENT: preserve()` i `BACKUP_ACCEPT_SHRINK: preserve()`, z komentarzem jak przy `TRIGGER_IN_ANY_ENVIRONMENT` (`:261-262`).
- Komentarz `:218-222` zastępuje „CI does not test tools/db-backup anyway (…locally)” prawdą: job CI `db-backup-test` testuje narzędzie, ale wdrożenie crona na nie nie czeka.

#### 2. README narzędzia

**File**: `tools/db-backup/README.md`

**Intent**: Opis przebiegu i kontroli odpowiada kodowi.

**Contract**:
- Tabela „Pliki” dostaje `tables.sh`.
- „Przebieg `backup.sh`” dostaje krok kontroli przed dumpem: minimum, brak spadku, zgoda.
- Krok 2 mówi o liczeniu rosnących tabel z `tables.sh`.
- „`restore-check.sh`” opisuje wymagania zależne od wersji i nieznaną tabelę.
- Manifest dostaje `compared_with` i `accepted_shrink`.
- „Zmienne” dostają `BACKUP_ACCEPT_SHRINK`.
- „Test lokalny” dostaje nowe przypadki.

#### 3. Runbooki operatora

**File**: `context/deployment/deploy-plan.md`

**Intent**: Operator wie, co zrobić z następną nocną kopią po celowym odtworzeniu, a rekord S-15 zapisuje weryfikację.

**Contract**:
- **Sekcja F-01, lista zachowań crona:**
  - zdanie o odrzucaniu podejrzanie pustej kopii i o zgodzie;
  - zdanie o uszkodzonym najnowszym manifeście: zmiana nazwy na taką spoza wzorca odblokowuje przebieg.
- **Runbook odtworzenia (`:297-336`), nowy krok 8 po weryfikacji:**
  1. Przed odtworzeniem zapisz nazwę najnowszego manifestu w `daily/`.
  2. Po odtworzeniu ustaw na `db-backup` `BACKUP_ACCEPT_SHRINK=<ta nazwa bez rozszerzenia>`.
  3. Przedeployuj `db-backup`, bo zmienna nie działa w trwającym wdrożeniu.
  4. Po pierwszej kopii z `accepted_shrink: true` usuń zmienną. Zostawiona zmienna jest nieaktywna.
  
  Alternatywa: nocny przebieg padnie z gotową wartością w logu.
- **`:347`:** usunięte nieaktualne zdanie „CI does not test tools/db-backup anyway”.
- **Nowa sekcja „S-15 backup-check-completeness”:**
  - kroki weryfikacji w PR env i ich wynik (wpisywany w czasie weryfikacji);
  - runbook produkcji po merge'u, wykonywany poza Progress:
    - pierwszy przebieg 02:00 ma `compared_with` ≠ `null` i `accepted_shrink: false`;
    - manifest zawiera `notifications` i `pipeline_run_steps`;
    - wynik idzie do issue S-15.

#### 4. Reguła dla przyszłych migracji

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Następna tabela nie przejdzie po cichu: agent i człowiek wiedzą, że migracja z tabelą zmienia też `tools/db-backup/tables.sh`.

**Contract**:
- Punkt o `tools/db-backup/` dostaje zdanie: migracja dodająca tabelę klasyfikuje ją w `tools/db-backup/tables.sh` w tym samym PR, inaczej `db-backup-test` jest czerwony.
- Punkt „Database backup” w „Deployment” dostaje półzdanie o odrzucaniu podejrzanie pustej kopii i o `BACKUP_ACCEPT_SHRINK`, z odsyłaczem do runbooka.

### Success Criteria:

#### Automated Verification:

- `tools/db-backup/test.sh` kończy się „All checks passed.”.
- Job CI `railway-config` jest zielony w PR: plan dla PR env nie zawiera zmian `destructive`, `resource.delete` ani zmian źródła.
- Cały workflow CI jest zielony w PR, w tym `db-backup-test`.

#### Manual Verification:

- **PR env ma bazę z postami.** Jeśli PR env nie ma bazy, wolumen dołącza się ręcznie, a następnie wykonuje się redeploy `postgres`, API i weba (jak w pr-44). Dane przykładowe z `tools/db-backup/test.sh:107-135` (użytkownik, przebieg, krok, 40 postów z RAW i ocenami, aktywacja, powiadomienie) trafiają SQL-em przez tunel `railway connect postgres`, z `psql` z obrazu `db-backup`. Przebieg pipeline'u (`pipeline-trigger` z `TRIGGER_IN_ANY_ENVIRONMENT=true` i jednorazowym harmonogramem, jak w S-05) to tylko opcja, gdy PR env i tak go potrzebuje, bo kosztuje prawdziwe wywołania Wykopu i LLM.
- **Dwa przebiegi przechodzą.** Z `BACKUP_IN_ANY_ENVIRONMENT=true`, tymczasowym harmonogramem i redeployem `db-backup`:
  - pierwszy ma `compared_with: null`;
  - drugi ma `compared_with` wskazujące pierwszy.
  
  Manifesty zawierają `notifications` i `pipeline_run_steps`.
- **Spadek daje `CRASHED`.** Po usunięciu kilku postów SQL-em w PR env następny przebieg kończy się `CRASHED`. Log podaje tabelę ze spadkiem i gotową wartość `BACKUP_ACCEPT_SHRINK`. Operator dostaje powiadomienie z reguły „deployment crashed”, a bucket PR env się nie zmienia.
- **Zgoda przechodzi.** Po ustawieniu `BACKUP_ACCEPT_SHRINK` na podaną wartość i redeployu `db-backup` następny przebieg przechodzi z `accepted_shrink: true`.
- **Bramka IaC przepuszcza ręczne zmienne.** Push na gałąź przy ustawionych obu zmiennych zostawia `railway-config` zielone.
- **Sprzątanie.** Po weryfikacji obie zmienne są usunięte, harmonogram wraca na `0 2 * * *`, a `db-backup` jest przedeployowany. Wynik zapisany jest w `deploy-plan.md` (sekcja S-15) i w komentarzu w issue S-15.

**Implementation Note**: Po zielonym CI i weryfikacji w PR env przejdź do `/10x-impl-review` i `/10x-archive` na tej samej gałęzi. Merge robi człowiek. Weryfikacja produkcji po merge'u idzie runbookiem z `deploy-plan.md` i komentarzem w issue, poza Progress.

---

## Testing Strategy

### Unit Tests:

- Brak testów jednostkowych. Narzędzie to bash, a jego testem jest `tools/db-backup/test.sh`: Docker, Postgres 18 i prawdziwe migracje.

### Integration Tests:

`tools/db-backup/test.sh`, w CI jako `db-backup-test`, sprawdza:
- **Klasyfikację:**
  - manifest z 7 liczonymi tabelami;
  - dump z V5 przechodzi;
  - nieznana tabela daje błąd;
  - brak `activation_tokens` przy V9 daje błąd.
- **Wiarygodność:**
  - pierwszy przebieg bez porównania;
  - porównanie z poprzednim manifestem;
  - data z przyszłości pomijana;
  - spadek odrzucony;
  - zgoda przyjęta, potem nieaktywna;
  - pusta baza odrzucona mimo zgody.
- **Niezmienione:**
  - retencja;
  - obcięty dump;
  - limit czasu;
  - pominięcie poza produkcją;
  - `nas-retention.sh`;
  - brak hasła w logach.

### Manual Testing Steps:

1. Lokalnie: `restore-check.sh` z obrazu gałęzi na `monthly/szczur-radar-2026-10` (V5) i najnowszym `daily/` (V9) z produkcji.
2. PR env: przebiegi OK, potem spadek `CRASHED` z powiadomieniem, potem zgoda OK, a `railway-config` zielone przy ręcznych zmiennych.
3. Po merge'u (runbook, poza Progress): pierwszy przebieg 02:00 na produkcji z `compared_with` ≠ `null`.

## Performance Considerations

Kontrola dokłada do przebiegu 3 operacje:
- `rclone lsf` katalogu `daily/`, który już dziś jest listowany przy retencji;
- `rclone cat` jednego manifestu (kilka KB);
- `count(*)` 2 dodatkowych tabel.

Porażka przed dumpem skraca przebieg zamiast go wydłużać.

## Migration Notes

- **Brak migracji bazy.**
  - Manifest ma nowe pola addytywne: `format` zostaje `1`, a starsze manifesty bez `compared_with` nadal przechodzą przez `restore-check.sh`.
  - Pierwsza kopia po wdrożeniu porówna się z manifestem sprzed wdrożenia. Ten manifest ma tylko 5 liczonych tabel, więc porównanie obejmie je, a `pipeline_run_steps` i `notifications` zostaną pominięte.
- **Wycofanie.** Revert commita wraca do dzisiejszych list. Deklaracje `preserve()` mogą zostać, bo są nieszkodliwe.

## References

- Research: `context/changes/backup-check-completeness/research.md`
- Audyt C9 i C7: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:96,98`
- Projekt narzędzia F-01: `context/archive/2026-10-11-production-data-gate/plan.md:101,199-222`
- Decyzja o `activation_tokens`: `context/archive/2026-10-12-cli-user-onboarding/plan.md:60`
- Wzorzec kontroli i komunikatu: `tools/db-backup/restore-check.sh:92-117`
- Wzorzec przypadku porażki w teście: `tools/db-backup/test.sh:223-232`
- Wzorzec `preserve()` dla nadpisania: `.railway/railway.ts:261-273`; runbook nadpisania: `deploy-plan.md:464,502`
- Lekcja „kopię logiczną uznawaj za ważną dopiero po pełnym odtworzeniu”: `context/foundation/lessons.md:88-97`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Klasyfikacja tabel i restore-check

#### Automated

- [x] 1.1 tools/db-backup/test.sh przechodzi z przypadkami: dump V5, nieznana tabela, brak activation_tokens, manifest z notifications i pipeline_run_steps — 067ce5b
- [x] 1.2 bash -n tools/db-backup/*.sh bez błędów — 067ce5b
- [x] 1.3 Job CI db-backup-test zielony w PR — 067ce5b

#### Manual

- [x] 1.4 restore-check.sh z gałęzi przechodzi na produkcyjnych dumpach monthly 2026-10 (V5) i najnowszym daily (V9)

### Phase 2: Odrzucanie podejrzanie pustej kopii

#### Automated

- [x] 2.1 tools/db-backup/test.sh przechodzi z przypadkami: data z przyszłości, spadek, zgoda, zgoda wygasła, pusta baza — 6894228
- [x] 2.2 bash -n tools/db-backup/*.sh bez błędów — 6894228
- [x] 2.3 Job CI db-backup-test zielony w PR — 6894228

### Phase 3: IaC, dokumenty i weryfikacja w PR env

#### Automated

- [x] 3.1 tools/db-backup/test.sh przechodzi — e0f9a38
- [x] 3.2 Job CI railway-config zielony w PR (bez zmian destructive, resource.delete i źródła) — e0f9a38
- [x] 3.3 Cały workflow CI zielony w PR — e0f9a38

#### Manual

- [x] 3.4 PR env ma bazę z postami (wolumen, redeploy, dane przykładowe SQL-em)
- [x] 3.5 Dwa przebiegi OK w PR env, drugi z compared_with pierwszego, manifest z notifications i pipeline_run_steps
- [x] 3.6 Spadek w PR env daje CRASHED z gotową wartością BACKUP_ACCEPT_SHRINK, powiadomienie i bucket bez zmian
- [x] 3.7 Zgoda BACKUP_ACCEPT_SHRINK z redeployem daje przebieg OK z accepted_shrink true
- [x] 3.8 Push przy ustawionych zmiennych zostawia railway-config zielone
- [x] 3.9 Sprzątanie PR env i zapis wyniku w deploy-plan (S-15) oraz w issue
