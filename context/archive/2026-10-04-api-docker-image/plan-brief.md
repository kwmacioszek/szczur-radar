# Obraz Dockera dla `szczur-radar-api` i wyłączenie PITR — Plan Brief

> Full plan: `context/changes/api-docker-image/plan.md`
> Research: `context/changes/api-docker-image/research.md`

## What & Why

API przestaje być budowane przez Railpack i dostaje własny wieloetapowy `Dockerfile`, wykrywany przez Railway tak jak obraz web. Zysk: JDK 25 bez pułapki domyślnego JDK 21 Railpacka oraz ten sam Dockerfile lokalnie, w CI i na Railway. Tak jak dla web, CI buduje i testuje obraz, a Railway po przejściu CI buduje i wdraża API z repozytorium GitHub, używając tego samego Dockerfile’a. Bazy Temurin pozostają na ruchomych tagach `25-jdk-noble` i `25-jre-noble`. Dodatkowo wyłączamy Postgres PITR i usuwamy bucket `Postgres-PITR`, aby ograniczyć koszty. W zamian produkcja dostaje dzienny backup wolumenu Railway (Daily, 6 dni), włączony przed wyłączeniem PITR; test odtwarzania pozostaje warunkiem przed S-05.

## Starting Point

Railpack buduje `szczur-radar-api` z `pom.xml` (`RAILPACK_JDK_VERSION=25`), a IaC ustawia start `java $JAVA_OPTS -jar target/szczur-radar-api.jar` i pre-deploy `migrate`. Przy buildzie z Dockerfile'a Railway uruchamia komendę startową w formie exec, bez rozwijania `$JAVA_OPTS`, więc obecny `start` by nie zadziałał — Dockerfile i zmiana IaC muszą wejść razem.

## Desired End State

Railway (PR env, potem produkcja) buduje API z `szczur-radar-api/Dockerfile` i uruchamia `ENTRYPOINT` obrazu jako użytkownik bez roota; pre-deploy `migrate` i healthcheck działają bez zmian. CI buduje obraz z tego samego Dockerfile'a, uruchamia na nim `migrate` przeciw Postgres 18 i sprawdza `/api/health` i `/api/ready`. Dokumenty opisują obraz, z limitem pamięci i zimnym startem zmierzonymi w PR env. PITR jest wyłączone, bucket usunięty z docelowych środowisk i IaC, baza i jej wolumen pozostają zachowane; produkcja ma dzienny backup wolumenu, a test odtwarzania blokuje S-05.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Osobna zmiana zamiast fazy F-02 | `api-docker-image`, gałąź z `origin/main` | Inny zakres i ryzyko niż kontrakt LLM; osobny PR łatwo wycofać. | Plan |
| Start JVM | Exec-form `ENTRYPOINT`, `start` usunięty z IaC, dodatkowe flagi przez `JAVA_TOOL_OPTIONS` | Jedno źródło prawdy; ten sam start lokalnie, w CI i na Railway. | Plan |
| Fallback, gdy IaC nie czyści startu | `startCommand: null`, a potem `start` bez `$`, zgodny z `ENTRYPOINT`; blokada bramki → pytanie | Forma exec bez zmiennych działa; deploy nie stoi. | Plan |
| Układ jara | Fat jar w `/app/target/szczur-radar-api.jar` | Pre-deploy i runbook `railway ssh` bez zmian; warstwy dopiero przy AOT. | Plan |
| Obraz bazowy | `eclipse-temurin:25-jdk-noble` → `25-jre-noble` | glibc i powłoka (`create-admin` przez `railway ssh`); OS przypięty, bo `25-jre` przeszedł na Ubuntu 26.04. | Plan + Research |
| Użytkownik | Bez roota (`USER`) | Higiena kontenera; aplikacja nie pisze na dysk. | Plan |
| Pamięć JVM | `-XX:MaxRAMPercentage=75` + pomiar limitu w PR env | Domyślnie heap to 25% limitu; pomiar zamiast zgadywania. | Plan + Research |
| Test w CI | Job `api-container-smoke`: non-root, `migrate` na Postgres 18, health + ready | Łapie złą ścieżkę jara i problemy obrazu przed Railway. | Plan |
| `RAILPACK_JDK_VERSION` | Zostaje jako nieużywana | Usunięcie to `variable.delete`, które bramka CI odrzuca. | Research |
| Builder w IaC | Bez pola `build`, Dockerfile wykrywany automatycznie | Wzorzec web; mniej zmian w planie IaC. | Research |
| Cron/pipeline | Poza zakresem; zajawka w S-05 (cron woła endpoint API) | Użytkownik nie chce crona na obrazie API. | Plan |
| Backup bazy | PITR wyłączone, `Postgres-PITR` usunięty; dzienny backup wolumenu Railway (Daily, 6 dni) włączony wcześniej; test odtwarzania przed S-05 | PITR to niepotrzebny koszt, a baza nie może zostać bez kopii przed zbieraniem prawdziwych danych. | Triage 2026-10-04, plan-review F1 |
| Cleanup PITR | Natywny CLI, potem IaC bez bucketa; bramka CI bez zmian | Wyłączenie archiwizacji poprzedza usunięcie kopii; CI nadal blokuje destrukcyjne operacje. | Plan |
| Śledzenie | Issue GitHub + PR z `Refs #N`, bez slice'a w roadmapie | Zgodne z workflow repo, bez rozdmuchiwania roadmapy. | Plan |

## Scope

**In scope:** `szczur-radar-api/Dockerfile`, `.dockerignore`, `start` w `.railway/railway.ts` i komentarze, job CI `api-container-smoke`, weryfikacja i pomiary w PR env, aktualizacja `deploy-plan.md`, `infrastructure.md`, `tech-stack-api.md`, `REPOSITORY_GUIDELINES.md`, README API, komentarz w `pom.xml`; cleanup PITR, usunięcie deklaracji bucketa, oznaczenie backupów jako odłożonych w dokumentacji i roadmapie oraz aktualizacja renderera i obrazu roadmapy.

**Out of scope:** AOT cache/CDS i jar warstwowy, serwis cron, `watchPatterns` w IaC, cache mounty BuildKit, alpine/distroless, usuwanie zmiennych API, wybór alternatywnego backupu i restore drill, zmiany w kodzie aplikacji, kroki produkcyjne w Progress.

## Architecture / Approach

Etap build (JDK 25 noble): najpierw `mvnw`, `.mvn/`, `pom.xml` i pobranie zależności, potem `src/` i `./mvnw -DskipTests package`. Etap runtime (JRE 25 noble): `WORKDIR /app`, jar w `target/`, użytkownik bez roota, `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/target/szczur-radar-api.jar"]`. Bez argumentów obraz uruchamia serwer, z argumentem `migrate` migrację. Na Railway job `railway-config` czyści komendę startową w tym samym workflow, na który czeka „Wait for CI”, więc konfiguracja jest gotowa przed deployem obrazu. PITR wyłączamy natywnym CLI na wskazanym Postgres, weryfikujemy deploy bazy, usuwamy pozostały bucket i dopiero wtedy stosujemy IaC bez `pitrBucket`. Produkcyjny cleanup jest runbookiem przed merge, poza Progress; po nim nie stosujemy starego IaC z deklaracją bucketa. Cofnięcie Dockerfile’a nie przywraca PITR ani usuniętych kopii.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Obraz, IaC i CI | Dockerfile + `.dockerignore` + `start` usunięty z IaC + `api-container-smoke`, cleanup PITR i IaC bez bucketa, zweryfikowane w PR env | IaC nie czyści starej komendy startowej albo pre-deploy działa inaczej niż start (fallbacki opisane w planie) |
| 2. Dokumentacja | Dokumenty opisują obraz, pomiary, runbook przełączenia i cleanupu produkcji, odłożone backupy; roadmapa i jej obraz są aktualne | Przeoczony opis Railpacka w bieżących dokumentach |

**Prerequisites:** `git fetch` i gałąź z `origin/main` (lokalny `main` jest nieaktualny); Docker lokalnie; dostęp do Railway PR env; issue GitHub założone na starcie.
**Estimated effort:** ~1–2 sesje w 2 fazach. Większość czasu zajmie weryfikacja w PR env.

## Open Risks & Assumptions

- Nie wiadomo, czy brak `start` w `railway.ts` czyści komendę zapisaną na serwisie (`pruneEmpty` w SDK). Rozstrzyga diff planu w jobie `railway-config`; fallback w planie.
- Zakładamy, że pre-deploy zastępuje `ENTRYPOINT` tak jak start. Jeśli dokleja argumenty, poprawka to `preDeploy: "migrate"`.
- Konflikt w `railway.ts` z gałęzią F-02 (blok serwisu API). Rozwiązuje go rebase gałęzi scalanej później.
- Limit pamięci kontenera Railway jest nieznany do czasu pomiaru; 75% może wymagać korekty.

- Usunięcie bucketa jest destrukcyjne i usuwa zapisane w nim kopie/WAL. Cleanup wykonujemy poza automatycznym apply CI; baza i wolumen nie są usuwane. Harmonogram Daily może być zablokowany na trialu — wtedy stop i pytanie do użytkownika przed wyłączeniem PITR. Kopie wolumenu giną przy wyczyszczeniu wolumenu; test odtwarzania przed S-05.

## Success Criteria (Summary)

- PR env buduje API z Dockerfile'a, pre-deploy `migrate` i healthcheck przechodzą, logowanie przez web działa.
- CI na PR jest zielone, z nowym `api-container-smoke` i planem IaC bez zmian destrukcyjnych.
- Dokumenty i runbook opisują obraz; po merge produkcję weryfikuje się według runbooka, a wynik trafia do komentarza w issue.

- W PR env PITR jest wyłączone, bucket usunięty, gotowość bazy i logowanie działają; dokumentacja oznacza backupy jako odłożone.
