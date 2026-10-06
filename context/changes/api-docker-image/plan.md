# Obraz Dockera dla `szczur-radar-api` i wyłączenie PITR — plan implementacji

## Overview

`szczur-radar-api` przestaje być budowany przez Railpack i dostaje własny, wieloetapowy `Dockerfile`, wykrywany automatycznie przez Railway tak jak obraz `szczur-radar-web`. Cel: JDK 25 bez pułapki domyślnego JDK 21 Railpacka oraz ten sam Dockerfile lokalnie, w CI i na Railway. Tak jak dla web, CI buduje i testuje obraz, a Railway po przejściu CI buduje i wdraża API z repozytorium GitHub, używając tego samego Dockerfile’a. Bazy Temurin pozostają na ruchomych tagach `25-jdk-noble` i `25-jre-noble`. Zmiana obejmuje Dockerfile, IaC (`.railway/railway.ts`), job CI testujący obraz i dokumenty opisujące dziś Railpacka. Podstawa: `context/changes/api-docker-image/research.md`. Na prośbę użytkownika zakres obejmuje również wyłączenie Postgres PITR i usunięcie bucketa `Postgres-PITR`, aby ograniczyć koszty. Zamiast PITR produkcja dostaje wbudowany dzienny backup wolumenu Railway (harmonogram Daily, kopie przez 6 dni); test odtwarzania jest warunkiem przed S-05.

## Current State Analysis

- Railway buduje API Railpackiem: brak pola `build` w IaC, JDK z `RAILPACK_JDK_VERSION: "25"` (`.railway/railway.ts:79`), start `java $JAVA_OPTS -jar target/szczur-radar-api.jar` (`:68`), pre-deploy `java -jar target/szczur-radar-api.jar migrate` (`:73`), healthcheck `/api/health` (`:74`), `sleepApplication: true` (`:76`).
- Przy buildzie z Dockerfile'a własna komenda startowa Railway zastępuje `ENTRYPOINT` obrazu i działa w formie exec, bez rozwijania zmiennych ([start-command](https://docs.railway.com/deployments/start-command)) — obecny `start` przekazałby JVM dosłowne `$JAVA_OPTS`. Pre-deploy działa w osobnym kontenerze z obrazu aplikacji ([pre-deploy-command](https://docs.railway.com/deployments/pre-deploy-command)).
- Sam plik `szczur-radar-api/Dockerfile` przełącza serwis na builder Dockerfile przy następnym deployu (`rootDirectory: "szczur-radar-api"`, `.railway/railway.ts:65`; [tanstack guide](https://docs.railway.com/guides/tanstack-start#use-a-dockerfile)). Dockerfile i zmiana IaC muszą więc wejść w jednym commicie.
- Bramka IaC w CI odrzuca zmiany `destructive`, `resource.delete` i każdą z opisem zawierającym słowo `source` (`.github/workflows/ci.yml:211-217`). Usunięcie zmiennej jest destrukcyjne — precedens: `DATABASE_URL` i `JWT_SECRET` zostały w IaC jako nieużywane (`.railway/railway.ts:87-93`).
- Aplikacja nie czyta plików z dysku (research, sekcja D), port bierze z `PORT` (`application.properties:5`), komendy z argumentów (`SzczurRadarApiApplication.java:23-29`). `./mvnw -DskipTests package` potrzebuje tylko JDK 25 i sieci; Testcontainers działa dopiero w `verify` (`pom.xml:171-187`).
- Wzorzec w repo: `szczur-radar-web/Dockerfile` (wieloetapowy, bez pola `builder` w IaC) i job `web-container-smoke` (`.github/workflows/ci.yml:72-102`).
- Lokalny `main` jest nieaktualny (`6d4861d`); aktualny jest `origin/main` (`0ee766f`, F-03 zarchiwizowane). Gałąź `feature/assessment-contract-harness-java` (F-02) zmienia ten sam serwis w `railway.ts` (+7 linii `LLM_*` `preserve()`) i ma niezacommitowane prace fazy 4 w drzewie roboczym.

- PITR i bucket `Postgres-PITR` są obecnie utrzymywane w `.railway/railway.ts:43-54,121`; komentarz `:19-24` wymaga ich zachowania. Wyłączenie PITR jest operacją CLI, a usunięcie bucketa z IaC spowoduje destrukcyjny diff, blokowany przez CI. Użytkownik zmienił tę decyzję: zasoby PITR usuwamy, a backup zapewnia dzienny harmonogram kopii wolumenu Railway ([volume backups](https://docs.railway.com/volumes/backups): Daily co 24 h, przechowywane 6 dni, przyrostowe, cena wolumenu; kopie giną przy wyczyszczeniu wolumenu i odtwarzają się tylko w tym samym środowisku). Na trialu harmonogram może być niedostępny (`lessons.md`, „Railway trial…”: `pitr schedule set` → „You do not have access”).
- Obecne dokumenty traktują backup z testem odtwarzania jako twardy warunek przed zbieraniem prawdziwych danych (`deploy-plan.md:146,181`, roadmapa `Block: F-01 → S-05`, ryzyko #1 w `infrastructure.md`, PRD:251). Ta zmiana zachowuje ten warunek.

## Desired End State

Railway (PR env, a po merge produkcja) buduje API z `szczur-radar-api/Dockerfile`: etap `eclipse-temurin:25-jdk-noble` buduje jar przez `./mvnw`, etap `eclipse-temurin:25-jre-noble` uruchamia go jako użytkownik bez roota z `/app/target/szczur-radar-api.jar` przez `ENTRYPOINT` z `-XX:MaxRAMPercentage=75`. IaC nie ustawia komendy startowej, pre-deploy `migrate` działa bez zmian, healthcheck przechodzi. CI buduje obraz z tego samego Dockerfile'a, uruchamia na nim `migrate` przeciw Postgres 18 i sprawdza `/api/health` oraz `/api/ready`. Dokumenty opisują Dockerfile zamiast Railpacka, z limitem pamięci i zimnym startem zmierzonymi w PR env. Postgres PITR jest wyłączone, bucket `Postgres-PITR` usunięty z konfiguracji docelowych środowisk i z IaC; działająca baza i jej wolumen pozostają zachowane. Backup bazy: dzienna kopia wolumenu Railway (Daily, 6 dni) włączona na produkcji przed wyłączeniem PITR, z co najmniej jedną kopią widoczną w zakładce Backups; jeśli trial blokuje harmonogram, dokumenty zapisują to jako blokadę do czasu subskrypcji. Test odtwarzania pozostaje warunkiem przed S-05 (zbieranie prawdziwych danych).

### Key Discoveries:

- Railway zawsze buduje Dockerfile'em, gdy znajdzie `Dockerfile` w katalogu głównym serwisu ([config-as-code reference](https://docs.railway.com/config-as-code/reference)).
- SDK IaC mapuje `start` na `deploy.startCommand` przez `??` i przepuszcza wynik przez `pruneEmpty` (`node_modules/railway/dist/index.js:264-276`) — czy brak `start` w `railway.ts` czyści komendę zapisaną na serwisie, pokaże dopiero plan.
- `MaxRAMPercentage` w JDK 25 domyślnie 25% ([JDK-8350596](https://bugs.openjdk.org/browse/JDK-8350596) „Won't Fix”); limit pamięci kontenera Railway jest nieudokumentowany.
- `create-admin` wymaga TTY (`CreateAdminCommand.java:60-89`) i jest uruchamiany przez `railway ssh` (`context/deployment/deploy-plan.md:40`) — obraz uruchomieniowy musi mieć powłokę.
- Tagi `eclipse-temurin:25-jre`/`25-jdk` bez sufiksu wskazują już Ubuntu 26.04 — OS trzeba przypiąć (`-noble`) ([official-images](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin)).

## What We're NOT Doing

- AOT cache / CDS i układ warstwowy jara (`extract --layers`) — decyzja odłożona od F-03 (`deploy-plan.md:56`); fat jar w `/app/target/` zostawia runbook bez zmian.
- Serwis cron i sposób uruchamiania pipeline'u — zajawka w S-05 (`context/foundation/roadmap.md`, Unknowns S-05).
- Zarządzanie `watchPatterns` przez IaC (ustalenie poboczne researchu, sekcja C) — bez zmian.
- Cache mounty BuildKit na Railway (wymagają dosłownego ID serwisu) — wystarcza kolejność warstw.
- Usuwanie `RAILPACK_JDK_VERSION` ani innych zmiennych API. Konfiguracja i powiązania PITR na Postgres są usuwane przez jego natywną komendę wyłączenia.
- Własny `pg_dump`/serwis cron z bucketem, harmonogramy Weekly/Monthly i test odtwarzania (restore drill) — test odtwarzania jest warunkiem przed S-05, nie częścią tej zmiany.
- Backup w PR env — brak prawdziwych danych.
- Obraz alpine lub distroless, `HEALTHCHECK` w Dockerfile, zmiany w kodzie aplikacji.
- Nowy slice w roadmapie — zmiana śledzona issue + PR.
- Kroki po merge w Progress: przełączenie i weryfikacja produkcji to runbook w `deploy-plan.md` i komentarz w issue (`context/foundation/lessons.md`, „Implementacja, przegląd i archiwizacja na jednej gałęzi”).

## Implementation Approach

Jedna gałąź `feature/api-docker-image` z aktualnego `origin/main` (najpierw `git fetch`; nie z lokalnego `main` ani z gałęzi F-02). Na starcie issue GitHub „api-docker-image: obraz Dockera dla szczur-radar-api” z komentarzem (change id, gałąź, link do PR); PR z `Refs #<issue>`. Faza 1 dostarcza w jednym commicie Dockerfile, `.dockerignore`, zmianę IaC i job CI, potem weryfikację w Railway PR env. Faza 2 aktualizuje dokumenty o zmierzone wartości. Potem `/10x-impl-review` i `/10x-archive` na tej samej gałęzi; merge wykonuje człowiek. Gałąź scalona później (ta albo F-02) robi rebase i rozwiązuje konflikt w bloku serwisu API w `railway.ts`; nie przywraca usuniętej deklaracji `Postgres-PITR`. Cleanup PITR wykonujemy osobnymi, jawnie wybranymi operacjami CLI w docelowym środowisku, przed zastosowaniem IaC bez bucketa. Produkcyjny cleanup jest runbookiem przed merge, poza Progress; nie zmieniamy bramki CI ani nie dodajemy `--confirm-destructive` do workflow. Po cleanupie produkcji nie uruchamiamy starego IaC z deklaracją bucketa: kolejny apply ma używać wersji z tego PR.

Wycofanie obrazu API: cofamy tylko zmiany Dockerfile’a i startu API, przywracając Railpacka oraz wcześniejszą komendę `start: "java $JAVA_OPTS -jar target/szczur-radar-api.jar"`; sprawdzamy diff IaC przed apply. Nie wykonujemy pełnego revertu przywracającego deklarację bucketa PITR. Wyłączenie PITR pozostaje w mocy; ponowne włączenie wymaga osobnej decyzji i nie odtwarza usuniętych kopii. Pre-deploy API pozostaje zgodny z rzeczywiście wybranym sposobem uruchomienia.

## Critical Implementation Details

- **Kolejność na Railway**: job `railway-config` stosuje plan IaC w tym samym workflow, na który czeka „Wait for CI”, więc usunięcie komendy startowej trafia na serwis przed deployem obrazu — w PR env i po merge. Jeśli plan nie pokaże wyczyszczenia `startCommand`, nowy obraz wystartuje ze starą komendą i nie przejdzie healthchecku.
- **Semantyka pre-deploy**: dokumentacja Railway mówi wprost tylko o komendzie startowej, że zastępuje `ENTRYPOINT`. Jeśli pre-deploy zamiast tego zostałby doklejony jako argumenty do `ENTRYPOINT`, `java -jar target/szczur-radar-api.jar migrate` dałby `args[0] = "java"` i kod 2 (`CommandLine.java:41-52`). Log kontenera pre-deploy w PR env rozstrzyga to; poprawka w takim przypadku to `preDeploy: "migrate"` z komentarzem.
- **Ścieżki względne**: `preDeploy` i runbook używają `target/szczur-radar-api.jar` względem katalogu roboczego — obraz musi mieć `WORKDIR /app`, a `ENTRYPOINT` może używać ścieżki bezwzględnej.

- **Backup przed cleanupem (produkcja)**: najpierw włączony harmonogram Daily i ręczna kopia wolumenu Postgres widoczna w zakładce Backups; dopiero potem wyłączenie PITR — baza ani przez chwilę nie zostaje bez kopii. Kopie wolumenu nie zależą od bucketa `Postgres-PITR`.
- **Cleanup PITR**: najpierw `railway postgres pitr disable` na wskazanym `postgres` i środowisku, potem sukces deploya Postgres i potwierdzenie `pitr status` jako wyłączone. Jeśli bucket nadal istnieje, usuwamy tylko `Postgres-PITR` z tego środowiska. Dopiero potem stosujemy IaC bez jego deklaracji. Usunięcie bucketa usuwa przechowywane w nim kopie i WAL; nie usuwamy serwisu Postgres ani jego wolumenu.

## Phase 1: Obraz, IaC i CI

### Overview

Railway buduje i uruchamia API z własnego obrazu; CI buduje i testuje obraz z tego samego Dockerfile'a przed deployem.

### Changes Required:

#### 1. Dockerfile API

**File**: `szczur-radar-api/Dockerfile`

**Intent**: Wieloetapowy build: etap budujący pobiera zależności w osobnej warstwie (zmiana samego `src` nie pobiera ich ponownie) i buduje fat jar; etap uruchomieniowy zawiera tylko JRE i jar, działa bez roota i domyślnie startuje serwer HTTP.

**Contract**: Build `FROM eclipse-temurin:25-jdk-noble`: najpierw `mvnw`, `.mvn/`, `pom.xml` i pobranie zależności (`./mvnw -B dependency:go-offline` lub równoważne), potem `src/` i `./mvnw -B -DskipTests package`. Runtime `FROM eclipse-temurin:25-jre-noble`: użytkownik systemowy bez uprawnień (`USER`), `WORKDIR /app`, jar w `/app/target/szczur-radar-api.jar`, `ENTRYPOINT` w formie exec `["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/target/szczur-radar-api.jar"]`, bez `CMD`. Dodatkowe flagi JVM operator ustawia zmienną `JAVA_TOOL_OPTIONS` (JVM czyta ją sama). Komentarz na górze jak w `szczur-radar-web/Dockerfile:1`. Kontrakt: `docker run <obraz>` = serwer, `docker run <obraz> migrate` = migracja.

#### 2. Kontekst budowania

**File**: `szczur-radar-api/.dockerignore`

**Intent**: Lokalny i CI-owy `docker build` nie wysyła `target/` (jar 161 MB), plików IDE ani logów.

**Contract**: Co najmniej `target`, `.idea`, `*.iml`, `.DS_Store`, `*.log` (wzór: `szczur-radar-web/.dockerignore:1-8`). Nie wyklucza `mvnw`, `.mvn/`, `pom.xml`, `src/`.

#### 3. IaC serwisu API

**File**: `.railway/railway.ts`

**Intent**: Serwis uruchamia `ENTRYPOINT` obrazu zamiast komendy Railpacka; pre-deploy i reszta bez zmian; zmienna Railpacka zostaje jako nieużywana, bo jej usunięcie zablokuje bramka.

**Contract**: Usunięte `start`. `preDeploy`, `healthcheck`, `replicas`, `deploy`, pozostałe `env` bez zmian. Bez pola `build` (Dockerfile wykrywany automatycznie, jak web). Komentarze nad serwisem (`:55-62`) opisują Dockerfile zamiast Railpacka; przy `RAILPACK_JDK_VERSION` komentarz „nieużywane od `api-docker-image`; usunięcie to `variable.delete`, które bramka CI odrzuca” (styl `:87-90`). Kolejność, jeśli plan na PR nie pokaże wyczyszczenia komendy startowej: (1) spróbować `deploy: { sleepApplication: true, startCommand: null }`; (2) jeśli `pruneEmpty` je usuwa i plan nadal nic nie pokazuje — `start: "java -XX:MaxRAMPercentage=75 -jar /app/target/szczur-radar-api.jar"` (forma exec bez `$`, ta sama co `ENTRYPOINT`) z komentarzem o utrzymaniu zgodności z Dockerfile'em. Jeśli bramka zablokuje plan (`source`/`destructive`) — stop i pytanie do użytkownika.

#### 4. Job CI testujący obraz

**File**: `.github/workflows/ci.yml`

**Intent**: Prawdziwy obraz API jest budowany i uruchamiany na każdym PR i pushu do `main`: komenda z pre-deploy (`migrate`) przeciw Postgres 18, potem serwer z gotowością bazy. Łapie złą ścieżkę jara, brak zasobów i problemy z obrazem przed Railway.

**Contract**: Nowy job `api-container-smoke` (nazwa „api container smoke test”), bez filtra ścieżek (`REPOSITORY_GUIDELINES.md`, Deployment). `services.postgres` `postgres:18` z healthcheckiem `pg_isready` (wzór: historyczny job na `main` sprzed F-03). Kroki: checkout; `docker build -t szczur-radar-api ./szczur-radar-api`; sprawdzenie, że użytkownik obrazu nie jest rootem (`docker run --rm --entrypoint id szczur-radar-api -u` ≠ `0`); `docker run --rm --network host` z `PG*` wskazującymi na usługę i `LLM_API_PROVIDER`/`LLM_MODEL` z `vars` oraz `LLM_API_KEY` z `secrets` (jak job `backend`, `ci.yml:30-43`) i argumentem `migrate` — kod 0; `docker run -d --name api --network host -e PORT=8000 …` (te same zmienne); pętla `curl -fsS http://localhost:8000/api/health` do 90 × 1 s, z `docker logs api` i `exit 1` przy braku gotowości; `curl -fsS http://localhost:8000/api/ready` → 200; krok `if: always()` z `docker logs api || true`. Nowy job jest częścią workflow, na który czeka „Wait for CI”.

#### 5. Wyłączenie PITR i usunięcie jego zasobów

**File**: `.railway/railway.ts`, `context/deployment/deploy-plan.md`

**Intent**: Postgres przestaje archiwizować WAL, a niewykorzystywany bucket z kopiami przestaje generować koszty. Backup bazy na produkcji zapewnia dzienna kopia wolumenu Railway, włączona przed wyłączeniem PITR.

**Contract**: Usunąć import `bucket`, deklarację `pitrBucket` i jego wpis w `resources`; poprawić komentarze nakazujące zachowanie PITR. Zachować `postgres`, jego wolumen, połączenia API i ustawienia uśpienia. W PR env wykonać cleanup przed pomyślnym apply IaC; jeśli pierwszy job zatrzyma się na destrukcyjnym diffie usunięcia bucketa, wykonać cleanup i ponowić job. Bramka nadal odrzuca wszystkie zmiany destrukcyjne i `source`.

**Runbook cleanupu (PR env podczas implementacji; produkcja przed merge, poza Progress):**

0. Tylko produkcja: w serwisie `postgres` → zakładka Backups włączyć harmonogram **Daily** i wykonać ręczną kopię; potwierdzić, że kopia jest na liście. Jeśli trial odmawia („You do not have access” lub brak opcji) — zatrzymać się i zapytać użytkownika, czy mimo to wyłączyć PITR (wtedy dokumenty zapisują brak backupu do czasu subskrypcji).
1. Zweryfikować projekt `szczur-radar`, środowisko, serwis `postgres` i bucket; odczytać status PITR. Tam, gdzie PITR jest aktywne, wykonać `railway postgres pitr disable --project <project-id> --environment <env-id> --service <postgres-id>`. Komenda domyślnie wdraża konfigurację bazy; poczekać na SUCCESS, potwierdzić wyłączone PITR i `/api/ready` przez web.
2. Jeśli bucket pozostał, usunąć tylko `Postgres-PITR`: `railway bucket -e <env-id> -b <bucket-id> delete` w zweryfikowanym projekcie. Potwierdzić faktyczne usunięcie, a nie tylko staged deletion. Nie usuwać bazy, wolumenu ani innych bucketów.
3. Sprawdzić brak bucketa i diff `railway config plan` z nowego IaC; CI ma pozostać zielone bez wyjątku dla zmian destrukcyjnych. Produkcję oczyścić przed merge, gdy PR jest zweryfikowany; kolejny apply musi używać nowego IaC, także na równoległej gałęzi F-02. Po merge sprawdzić brak odtworzenia PITR/bucketa i logowanie; wynik zapisać w issue.

### Success Criteria:

#### Automated Verification:

- Obraz buduje się lokalnie z czystego kontekstu: `docker build -t szczur-radar-api ./szczur-radar-api`
- Kontekst budowania nie zawiera `target/`: `grep -qx 'target' szczur-radar-api/.dockerignore`
- Testy backendu przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- Job CI `api-container-smoke` jest zielony na PR (non-root, `migrate` = 0, `/api/health` i `/api/ready` = 200)
- Job CI `railway-config` jest zielony na PR, a wypisany diff planu pokazuje wyczyszczenie komendy startowej API (lub fallback z `start` bez `$`) i żadnej zmiany destrukcyjnej ani `source`
- Pozostałe joby CI (`backend`, `web-unit`, `web-container-smoke`) są zielone na PR
- IaC nie deklaruje bucketa PITR: `rg -n 'pitrBucket|bucket\("Postgres-PITR"' .railway/railway.ts` nie znajduje trafień

#### Manual Verification:

- Log builda API w Railway PR env pokazuje build z Dockerfile'a („Using detected Dockerfile”), nie Railpacka
- Kontener pre-deploy w PR env wykonał `migrate` z kodem 0, a deploy przeszedł healthcheck `/api/health`
- Logowanie admina przez web w PR env działa end-to-end (proxy `/api/*` → API z obrazu)
- `railway ssh` do API w PR env: powłoka działa jako użytkownik bez roota, `java -jar target/szczur-radar-api.jar` z `/app` uruchamia komendę (np. `create-admin` bez `--email` kończy się komunikatem użycia)
- Zmierzone w PR env i zapisane w opisie PR: limit pamięci kontenera (`cat /sys/fs/cgroup/memory.max`), efektywny heap i GC (`java -XX:MaxRAMPercentage=75 -XX:+PrintFlagsFinal -version | grep -E 'MaxHeapSize|UseSerialGC|UseG1GC'`), rozmiar obrazu i czas builda z logu Railway, czas od pierwszego żądania po uśpieniu do zalogowania (porównanie z 10–11 s z `deploy-plan.md:51-56`)

- W PR env PITR jest wyłączone, bucket `Postgres-PITR` nie istnieje, `/api/ready` i logowanie działają po cleanupie

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie weryfikacji w PR env przed fazą 2. Komentarz w issue po zweryfikowaniu fazy w PR env.

---

## Phase 2: Dokumentacja

### Overview

Dokumenty repo opisują obraz Dockera zamiast Railpacka, z wartościami zmierzonymi w fazie 1 i runbookiem przełączenia produkcji.

### Changes Required:

#### 1. Plan wdrożenia i runbook

**File**: `context/deployment/deploy-plan.md`

**Intent**: Bieżący stan API i rozwiązywanie problemów odpowiadają Dockerfile'owi; historia faz zostaje nietknięta.

**Contract**: `:35` (stan API: Dockerfile, JDK z obrazu), `:40` (runbook `railway ssh`: `/app`, użytkownik bez roota, ta sama komenda), `:41` (lista zmiennych: `RAILPACK_JDK_VERSION` nieużywane, opcjonalne `JAVA_TOOL_OPTIONS`), `:81` i `:175` (awaria builda API: Dockerfile, `./mvnw` w etapie build, przypięte tagi `-noble`, `.dockerignore`), nowy wpis o zimnym starcie/pamięci z pomiarów fazy 1. Runbook po merge: sprawdzenie w logu produkcji buildu z Dockerfile'a, pre-deploy `migrate`, healthchecku i logowania admina; wynik jako komentarz w issue, które człowiek zamyka po weryfikacji produkcji. Linie historyczne `:106`, `:113` bez zmian. Dopisać runbook cleanupu PITR z fazy 1 (krok 0 z backupem Daily, produkcyjna operacja przed merge oraz weryfikacja po merge). Bieżący status i troubleshooting wskazują dzienny backup wolumenu jako metodę ratowania danych (`:180` „PITR as the last resort” → odtworzenie kopii wolumenu), a `:181` zachowuje warunek „backup + test odtwarzania przed zbieraniem prawdziwych danych”; historyczne wyniki PITR i restore drill pozostają opisem historii.

#### 2. Fundamenty

**File**: `context/foundation/infrastructure.md`, `context/foundation/tech-stack-api.md`

**Intent**: Decyzja o buildzie API jest aktualna, z notką „Update 2026-10-04 (`api-docker-image`)” w stylu istniejącego wpisu F-03 (`infrastructure.md:13`).

**Contract**: `infrastructure.md:10` (frontmatter runtime), `:13`/notka aktualizacji, `:19`, `:60`, `:86` (pułapka JDK 21 nieaktualna — JDK przypięty w obrazie), wiersze ryzyka `:110` i `:113` (zamknięte/zmienione), `:129` (konfiguracja serwisu). Linie historyczne `:39`, `:49`, `:68`, `:78`, `:82`, `:88`, `:90` bez zmian. `tech-stack-api.md:31` — build z Dockerfile'a.

#### 3. Wytyczne repo, README i komentarze

**File**: `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`, `szczur-radar-api/pom.xml`

**Intent**: Agenci i ludzie widzą obraz Dockera jako sposób wdrożenia i nowy job CI.

**Contract**: `REPOSITORY_GUIDELINES.md` — opis `szczur-radar-api/` w Project Structure (obraz z `Dockerfile`), Build Commands (lokalny `docker build`/`docker run … migrate`), sekcja CI `:45` (dodać `api-container-smoke`). `szczur-radar-api/README.md:94` (pre-deploy na Railway) i krótka sekcja o obrazie. `pom.xml:138` — komentarz `finalName` wskazuje Dockerfile i pre-deploy.

#### 4. Dzienny backup zamiast PITR w dokumentacji i roadmapie

**File**: `context/foundation/infrastructure.md`, `context/foundation/roadmap.md`, `REPOSITORY_GUIDELINES.md`, `context/deployment/deploy-plan.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`

**Intent**: Dokumentacja opisuje rzeczywisty backup (dzienna kopia wolumenu Railway) zamiast PITR i zachowuje warunek testu odtwarzania przed zbieraniem prawdziwych danych. Wdrożenie Dockerfile’a nie zależy od testu odtwarzania.

**Contract**: W bieżących sekcjach dopisać `Backup bazy: dzienna kopia wolumenu Railway (Daily, 6 dni)` (albo, jeśli trial zablokował harmonogram, `Backup bazy: brak do czasu subskrypcji`) i decyzję użytkownika o wyłączeniu PITR ze względu na koszty, z ograniczeniami kopii wolumenu (giną przy wyczyszczeniu wolumenu, odtwarzanie tylko w tym samym środowisku). Aktualny runbook wskazuje odtworzenie kopii wolumenu (zakładka Backups → Restore) zamiast PITR; migracje nadal są fix-forward. F-01 i Open Risks roadmapy: PITR zastąpione dzienną kopią, test odtwarzania nadal blokuje S-05 (`Block: F-01 → S-05` zostaje); ryzyko #1 w `infrastructure.md` dostaje nową mitygację. Nie zmieniać pozostałych wymagań kosztów i sekretów ani wymagania ochrony danych z PRD. Zaktualizować renderer i wygenerować obraz roadmapy zgodnie z wytycznymi repo. Historyczne wykonane operacje i append-only `lessons.md` pozostają zachowane; bieżąca decyzja jest opisana jako ich późniejsza aktualizacja.

### Success Criteria:

#### Automated Verification:

- Bieżące dokumenty nie opisują Railpacka jako buildu API poza historią i komentarzem zmiennej: `rg -n 'Railpack' context/deployment/deploy-plan.md context/foundation/tech-stack-api.md REPOSITORY_GUIDELINES.md szczur-radar-api/README.md` zwraca wyłącznie linie świadomie historyczne, wypisane w opisie PR
- `REPOSITORY_GUIDELINES.md` wymienia nowy job: `grep -n 'api-container-smoke\|api container smoke' REPOSITORY_GUIDELINES.md`
- CI na PR jest zielone po zmianach dokumentów
- Stan backupu jest zapisany w dokumentach: `rg -n 'Backup bazy:' context/foundation/infrastructure.md context/foundation/roadmap.md context/deployment/deploy-plan.md` (trafienie w każdym pliku), a warunek przed S-05 zostaje: `rg -n 'F-01 → S-05' context/foundation/roadmap.md`; renderer i obraz roadmapy są aktualne

#### Manual Verification:

- Użytkownik przeczytał zmienione fragmenty `deploy-plan.md`, `infrastructure.md` i `REPOSITORY_GUIDELINES.md` i potwierdził zgodność z wynikami z PR env
- Runbook przełączenia produkcji w `deploy-plan.md` jest kompletny (co sprawdzić po merge, gdzie skomentować w issue)
- Runbook wyłączenia PITR i usunięcia bucketa oraz opis dziennego backupu są spójne z IaC, wynikami z PR env i stanem zakładki Backups na produkcji

---

## Testing Strategy

### Unit Tests:

- Brak nowych — kod aplikacji się nie zmienia; `./mvnw -B verify` pilnuje regresji.

### Integration Tests:

- `api-container-smoke`: obraz z prawdziwego Dockerfile'a, `migrate` przeciw Postgres 18, serwer z `/api/health` i `/api/ready`, użytkownik bez roota.
- `railway-config`: plan IaC na PR env bez zmian destrukcyjnych i `source`.

### Manual Testing Steps:

1. Lokalnie: `docker build -t szczur-radar-api ./szczur-radar-api`, potem `docker run` z `PG*` na lokalnego Postgresa z `compose.local.yaml` i `migrate`, następnie serwer i `curl localhost:8000/api/ready`.
2. PR env: log builda (Dockerfile), log pre-deploy (`migrate` = 0), healthcheck. PR nie zmienia `szczur-radar-web/**`, więc pierwszy deploy web w PR env będzie `SKIPPED` (watch paths, `deploy-plan.md:49`) — przed logowaniem wdrożyć go ręcznie: `railway redeploy --from-source -s szczur-radar-web -e szczur-radar-pr-<N>`. Potem logowanie przez web.
3. PR env: `railway ssh` — użytkownik, `java -jar target/szczur-radar-api.jar`, limit pamięci i flagi JVM.
4. PR env: odczekać uśpienie (>10 min bez ruchu), zmierzyć pierwsze wejście do zalogowania.
5. PR env: potwierdzić wyłączone PITR, brak bucketa, brak niezamierzonych zmian bazy i działanie aplikacji po cleanupie.

## Performance Considerations

Obraz `25-jre-noble` (~107 MB skompresowany) plus jar ~160 MB; każda zmiana kodu to nowa warstwa jara. Zimny start po uśpieniu wynikał dotąd głównie ze startu Springa i Embabel (5,9–7,7 s, `deploy-plan.md:51-56`) — obraz go nie skraca; pomiar w fazie 1 potwierdza brak regresji. `-XX:MaxRAMPercentage=75` podnosi heap z domyślnych 25% limitu kontenera; pomiar `PrintFlagsFinal` pokazuje, czy JVM wybiera G1 czy SerialGC (≥2 CPU i ≥1792 MB dla G1).

## Migration Notes

Brak danych do migracji. Produkcja przełącza się przy merge: `railway-config` czyści komendę startową, potem deploy obrazu z Dockerfile'a. Wycofanie obrazu przez selektywne cofnięcie zmian API, bez przywracania PITR (sekcja Implementation Approach). Zmienna `RAILPACK_JDK_VERSION` zostaje na serwisie. Wyłączenie PITR i usunięcie jego bucketa to osobne operacje runbooka, wykonane przed apply IaC bez bucketa; istniejące archiwum kopii/WAL w usuniętym buckecie zostanie utracone. Nie wykonujemy migracji ani resetu danych w Postgres. Przed wyłączeniem PITR na produkcji włączamy dzienny backup wolumenu i robimy ręczną kopię; test odtwarzania pozostaje warunkiem przed S-05.

## References

- Research: `context/changes/api-docker-image/research.md`
- Wzorzec: `szczur-radar-web/Dockerfile:1-18`, `.github/workflows/ci.yml:72-102`
- IaC: `.railway/railway.ts:59-110`, bramka `.github/workflows/ci.yml:197-228`
- Railway: [dockerfiles](https://docs.railway.com/builds/dockerfiles), [start-command](https://docs.railway.com/deployments/start-command), [pre-deploy-command](https://docs.railway.com/deployments/pre-deploy-command), [healthchecks](https://docs.railway.com/deployments/healthchecks)
- PITR i cleanup: [postgres CLI](https://docs.railway.com/cli/postgres), [bucket CLI](https://docs.railway.com/cli/bucket)
- Spring Boot 4: [container images / dockerfiles](https://docs.spring.io/spring-boot/4.0/reference/packaging/container-images/dockerfiles.html)
- Lekcje: `context/foundation/lessons.md` (jedna gałąź + PR env; implementacja, przegląd i archiwizacja przed merge; tylko człowiek merguje)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Obraz, IaC i CI

#### Automated

- [x] 1.1 Obraz buduje się lokalnie z czystego kontekstu: `docker build -t szczur-radar-api ./szczur-radar-api` — f96804a
- [x] 1.2 Kontekst budowania nie zawiera `target/`: `grep -qx 'target' szczur-radar-api/.dockerignore` — f96804a
- [x] 1.3 Testy backendu przechodzą: `cd szczur-radar-api && ./mvnw -B verify` — f96804a
- [x] 1.4 Job CI `api-container-smoke` jest zielony na PR (non-root, `migrate` = 0, `/api/health` i `/api/ready` = 200) — f96804a
- [x] 1.5 Job CI `railway-config` jest zielony na PR, a wypisany diff planu pokazuje wyczyszczenie komendy startowej API (lub fallback z `start` bez `$`) i żadnej zmiany destrukcyjnej ani `source` — f96804a
- [x] 1.6 Pozostałe joby CI (`backend`, `web-unit`, `web-container-smoke`) są zielone na PR — f96804a
- [x] 1.12 IaC nie deklaruje bucketa PITR: `rg -n 'pitrBucket|bucket\("Postgres-PITR"' .railway/railway.ts` nie znajduje trafień — f96804a

#### Manual

- [x] 1.7 Log builda API w Railway PR env pokazuje build z Dockerfile'a („Using detected Dockerfile”), nie Railpacka — f96804a
- [x] 1.8 Kontener pre-deploy w PR env wykonał `migrate` z kodem 0, a deploy przeszedł healthcheck `/api/health` — f96804a
- [x] 1.9 Logowanie admina przez web w PR env działa end-to-end (proxy `/api/*` → API z obrazu) — f96804a
- [x] 1.10 `railway ssh` do API w PR env: powłoka działa jako użytkownik bez roota, `java -jar target/szczur-radar-api.jar` z `/app` uruchamia komendę — f96804a
- [x] 1.11 Zmierzone w PR env i zapisane w opisie PR: limit pamięci, heap i GC, rozmiar obrazu, czas builda, pierwsze wejście po uśpieniu — f96804a
- [x] 1.13 W PR env PITR jest wyłączone, bucket `Postgres-PITR` nie istnieje, `/api/ready` i logowanie działają po cleanupie — f96804a

### Phase 2: Dokumentacja

#### Automated

- [x] 2.1 Bieżące dokumenty nie opisują Railpacka jako buildu API poza historią i komentarzem zmiennej — 90c49c9
- [x] 2.2 `REPOSITORY_GUIDELINES.md` wymienia nowy job `api-container-smoke` — 90c49c9
- [x] 2.3 CI na PR jest zielone po zmianach dokumentów — 90c49c9
- [x] 2.6 Stan backupu jest zapisany w dokumentach, a warunek przed S-05 zostaje; renderer i obraz roadmapy są aktualne — 90c49c9

#### Manual

- [x] 2.4 Użytkownik przeczytał zmienione fragmenty `deploy-plan.md`, `infrastructure.md` i `REPOSITORY_GUIDELINES.md` i potwierdził zgodność z wynikami z PR env — 90c49c9
- [x] 2.5 Runbook przełączenia produkcji w `deploy-plan.md` jest kompletny — 90c49c9
- [x] 2.7 Runbook wyłączenia PITR i usunięcia bucketa oraz opis dziennego backupu są spójne z IaC, wynikami z PR env i zakładką Backups — 90c49c9
