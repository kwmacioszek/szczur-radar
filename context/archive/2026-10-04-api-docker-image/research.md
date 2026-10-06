---
date: 2026-10-04T09:08:56+02:00
researcher: Claude (Opus 5.5) dla kwmacioszek
git_commit: 37632c49b1e0460d453dc41cd72d11097bc0e060
branch: feature/assessment-contract-harness-java
repository: szczur-radar
topic: "Przeniesienie budowania szczur-radar-api z Railpacka na własny Dockerfile — co trzeba zmienić i co może pęknąć"
tags: [research, deploy, railway, docker, railpack, iac, ci, szczur-radar-api, api-docker-image]
status: complete
last_updated: 2026-10-04
last_updated_by: Claude (Opus 5.5)
---

# Research: własny obraz Dockera dla `szczur-radar-api`

**Date**: 2026-10-04T09:08:56+02:00
**Researcher**: Claude (Opus 5.5) dla kwmacioszek
**Git Commit**: 37632c49b1e0460d453dc41cd72d11097bc0e060 (drzewo robocze zawiera niezacommitowane prace fazy 4 `assessment-contract-harness`, m.in. w `pom.xml` — numery linii `pom.xml` dotyczą drzewa roboczego)
**Branch**: feature/assessment-contract-harness-java
**Repository**: szczur-radar

## Research Question

> „Sprawdź, czy moglibyśmy dodać kolejną fazę, żeby przenieść szczur-radar-api na obraz dockerowy” — przeniesione do osobnej zmiany `api-docker-image`. Pytania: co dziś zależy od Railpacka, jak Railway potraktuje Dockerfile (wykrywanie, start, pre-deploy, cache, healthcheck), co zablokuje bramka IaC w CI, jaki obraz bazowy i układ jara, jak przetestować obraz w CI, co z przyszłym jobem pipeline'u.

Badania wewnętrzne: repo i `context/`. Zewnętrzne: dokumentacja Railway (MCP docs), Spring Boot 4.0, OpenJDK, Docker Hub / official-images — linki przy ustaleniach.

## Summary

1. **Zmiana jest mała i wykonalna, ale nie jest „tylko dodaniem Dockerfile'a”.** Railway buduje Dockerfile'em zawsze, gdy znajdzie plik `Dockerfile` w katalogu głównym serwisu ([config-as-code reference](https://docs.railway.com/config-as-code/reference), [dockerfiles](https://docs.railway.com/builds/dockerfiles)); serwis ma `rootDirectory: "szczur-radar-api"` (`.railway/railway.ts:65`). Samo zacommitowanie `szczur-radar-api/Dockerfile` przełącza więc builder przy następnym deployu — bez zmiany IaC, tak jak dziś działa web (`.railway/railway.ts:111`).
2. **Obecna komenda startowa przestanie działać z Dockerfile'em.** Przy buildzie Dockerfile własna komenda startowa zastępuje `ENTRYPOINT` obrazu i jest uruchamiana w formie exec, bez rozwijania zmiennych ([start-command](https://docs.railway.com/deployments/start-command)). `start: "java $JAVA_OPTS -jar target/szczur-radar-api.jar"` (`.railway/railway.ts:68`) przekaże do JVM dosłowny napis `$JAVA_OPTS`, a ścieżka `target/...` istnieje tylko, jeśli obraz ją odtworzy. To samo dotyczy `preDeploy` (`.railway/railway.ts:73`), które działa w osobnym kontenerze z tego samego obrazu ([pre-deploy-command](https://docs.railway.com/deployments/pre-deploy-command)). Plan musi zmienić `start`/`preDeploy` razem z Dockerfile'em w jednym commicie.
3. **Bramka IaC w CI**: odrzuca zmiany `severity == "destructive"`, `kind == "resource.delete"` oraz każdą, której opis zawiera słowo `source` (`.github/workflows/ci.yml:211-217`). Usunięcie `RAILPACK_JDK_VERSION` z `railway.ts` to usunięcie zmiennej, czyli zmiana destrukcyjna (`node_modules/railway/README.md`, sekcja IaC; precedens `DATABASE_URL` w `.railway/railway.ts:87-90`) — zmienna musi zostać zadeklarowana jako nieużywana. Jak plan opisze zmianę `start`/`build.builder` i czy wyczyszczenie `start` wymaga jawnego `startCommand: null` — **nieznane**, do sprawdzenia `railway config plan --json` na PR.
4. **Aplikacja jest gotowa do konteneryzacji**: w `src/main` nie ma odczytów z systemu plików (zero trafień dla `Path.of`, `Paths.get`, `Files.`, `new File(`, `user.dir`, `FileSystemResource`, `file:`), zasoby czyta z classpath (`AssessmentPrompt.java:52`, `OutputParser.java:45`), port z `PORT` (`application.properties:5`), komendy przez argumenty (`SzczurRadarApiApplication.java:23-29`, `CommandLine.java:41-52`). `./mvnw -DskipTests package` potrzebuje tylko JDK 25 i sieci (failsafe/Testcontainers są w fazach `integration-test`/`verify`, `pom.xml:171-187`).
5. **Przyszły job pipeline'u** ma być osobnym serwisem cron z Root Directory `szczur-radar-api` i komendą, która uruchamia pipeline i kończy działanie (`context/foundation/infrastructure.md:133`, `:106`; `context/deployment/deploy-plan.md:195`). Z Dockerfile'em w tym katalogu taki serwis zbuduje ten sam obraz — to najmocniejszy argument za zmianą obok usunięcia pułapki domyślnego JDK 21 Railpacka (`infrastructure.md:86`, `:113`).
6. **Poza zakresem tej zmiany (decyzja odłożona wcześniej)**: AOT cache/CDS. F-03 odłożyło ją do „najpierw pomiar zimnego startu” (`context/deployment/deploy-plan.md:56`, `context/foundation/infrastructure.md:114`). Trening AOT w buildzie koliduje z wymogiem `PG*`/`LLM_*` przy starcie aplikacji ([aot-cache how-to](https://docs.spring.io/spring-boot/4.0/how-to/aot-cache.html)). Układ warstwowy jara zostawia na to miejsce.

## Detailed Findings

### A. Co dziś zależy od Railpacka

- IaC serwisu API: `rootDirectory` (`.railway/railway.ts:65`), `start` (`:68`), `preDeploy` (`:73`), `healthcheck: "/api/health"` (`:74`), `sleepApplication: true` (`:76`), `RAILPACK_JDK_VERSION: "25"` (`:79`). Brak pola `build` — builder wybiera Railway.
- Ścieżka jara jest stała: `<finalName>szczur-radar-api</finalName>` z komentarzem o Railway (`szczur-radar-api/pom.xml:149-150`).
- Runbook operatora zakłada kontener Railpacka: `railway ssh` → `/app` → `java -jar target/szczur-radar-api.jar …` (`context/deployment/deploy-plan.md:40`). `create-admin` wymaga interaktywnego TTY (`CreateAdminCommand.java:60-89`), więc obraz uruchomieniowy **musi mieć powłokę** (wyklucza distroless).
- Railpack nie sprawił dotąd zarejestrowanego problemu: brak wpisów o czasie buildu, cache czy rozmiarze obrazu. Pułapka JDK 21 jest opisana jako ryzyko, bez incydentu (`infrastructure.md:86`, `:113`; `deploy-plan.md:81`, `:175`). Zmierzony zimny start w Javie: Spring + Embabel 5,9–7,7 s, kontener `migrate` ~10,5 s, klik→zalogowany po >1 h uśpienia ~10–11 s (`deploy-plan.md:51-56`). Czasów buildu Javy nie zmierzono (liczby w `deploy-plan.md:29` pochodzą z ery Pythona).

### B. Railway z Dockerfile'em

- **Wykrywanie i pierwszeństwo**: plik `Dockerfile` w katalogu głównym źródła; inna nazwa/ścieżka → zmienna `RAILWAY_DOCKERFILE_PATH` ([dockerfiles](https://docs.railway.com/builds/dockerfiles)). „Committing a Dockerfile also switches your service to the Dockerfile builder on the next deploy, even if it previously built with Railpack” ([tanstack guide](https://docs.railway.com/guides/tanstack-start#use-a-dockerfile)). Po przełączeniu `RAILPACK_JDK_VERSION` nie ma wpływu.
- **Start**: własna komenda startowa zastępuje `ENTRYPOINT` i działa w formie exec; zmienne wymagają `/bin/sh -c "exec …"` ([start-command](https://docs.railway.com/deployments/start-command)). Bez własnej komendy działa `ENTRYPOINT`/`CMD` obrazu. JVM sama czyta `JAVA_TOOL_OPTIONS` bez powłoki ([tracing/java](https://docs.railway.com/observability/tracing/java)).
- **Pre-deploy**: osobny kontener ze zmiennymi serwisu i siecią prywatną, niezerowy kod przerywa deploy, bez ponowień ([pre-deploy-command](https://docs.railway.com/deployments/pre-deploy-command)). Że używa tego samego obrazu, dokumentacja sugeruje („installed in the application image”), ale nie mówi wprost. Forma exec także tu (odpowiedzi personelu Railway: [1](https://station.railway.com/questions/interpolating-environmental-variable-in-d0a77f93), [2](https://station.railway.com/questions/pre-deploy-command-parsing-of-develop-71b60872)). `java -jar <ścieżka> migrate` bez zmiennych zadziała, jeśli ścieżka zgadza się z układem obrazu.
- **Cache**: cache mounty tylko w formacie `--mount=type=cache,id=s/<service-id>-<ścieżka>,target=<ścieżka>` z dosłownym ID serwisu ([dockerfiles#cache-mounts](https://docs.railway.com/builds/dockerfiles#cache-mounts)). ID różni się między projektami, a czy instancje w PR env mają to samo ID — nieudokumentowane. Cache warstw jest domyślny, ale „cache hit on builds is not guaranteed” ([build-configuration](https://docs.railway.com/builds/build-configuration)). Wniosek: polegać na kolejności warstw (najpierw `pom.xml` + wrapper i pobranie zależności, potem `src`), bez cache mountów.
- **Zmienne w buildzie**: tylko przez `ARG` w danym etapie; sekrety w `ARG` mogą trafić do obrazu ([dockerfiles#using-variables-at-build-time](https://docs.railway.com/builds/dockerfiles#using-variables-at-build-time)). Build API nie potrzebuje żadnych zmiennych.
- **Healthcheck**: ścieżka z konfiguracji, dowolne 2xx, domyślny limit 300 s, tylko przy deployu ([healthchecks](https://docs.railway.com/deployments/healthchecks)). Dyrektywa `HEALTHCHECK` w Dockerfile — nieudokumentowana, przyjąć, że ignorowana.

### C. IaC (`railway/iac` 3.11.0) i bramka CI

- SDK przyjmuje `build?: string | BuildConfig` z `builder?: "NIXPACKS" | "DOCKERFILE" | "RAILPACK" | "HEROKU" | "PAKETO" | null`, `dockerfilePath`, `watchPatterns` (`node_modules/railway/dist/index-C3uk0ruc.d.ts:69-79`) oraz `DeployConfig.startCommand?: string | null`, `preDeployCommand?: string[] | null` (`:80+`). `normalizeBuild` przekazuje obiekt `build` bez zmian (`node_modules/railway/dist/index.js:260-263`); `normalizeDeploy` mapuje `start`/`preDeploy` na `startCommand`/`preDeployCommand` (`:264-276`). Publiczna dokumentacja IaC nie opisuje `builder` ani `dockerfilePath`.
- **Ustalenie poboczne**: `service()` (`node_modules/railway/dist/index.js:150-171`) nie czyta górnopoziomowego `watchPatterns`, a w `dist/index.js` nie ma innego odczytu tego pola. `watchPatterns` podane w `railway.ts:66` i `:117` na górnym poziomie najpewniej nie są zarządzane przez IaC (obowiązuje wartość z dashboardu). Niezweryfikowane na żywym planie (CLI mogłoby przetwarzać pole osobno). Jeśli plan wprowadzi `build: { … }`, przeniesienie `watchPatterns` do środka zacznie nim zarządzać i pokaże się w planie.
- **Bramka** (`.github/workflows/ci.yml:197-228`): blokuje `destructive`, `resource.delete` i dopasowanie `(^|[ ,])source([.]|$| )` w `summary`/`details` (`:211-217`); nie ma osobnych reguł dla `build.*`, `startCommand` ani `preDeployCommand`. Usunięcie zmiennej jest destrukcyjne — dlatego `DATABASE_URL` i `JWT_SECRET` zostały w IaC jako nieużywane (`.railway/railway.ts:87-93`).
- Brak przykładu `plan.json` dla zmiany buildera: nie wiadomo, czy jego opis zawiera słowo `source`.

### D. Aplikacja i build Maven

- `./mvnw` w trybie `distributionType=only-script` pobiera Maven 3.9.16 z Maven Central (`szczur-radar-api/.mvn/wrapper/maven-wrapper.properties:1-3`); `mvnw` jest wykonywalny w git (`100755`), z `eol=lf` (`szczur-radar-api/.gitattributes:1`). Wrapper pobiera dystrybucję przez `curl`/`wget` — obraz budujący musi je mieć (wniosek, nie sprawdzony w skrypcie; obrazy `eclipse-temurin` na Ubuntu zawierają `curl` — do potwierdzenia przy implementacji).
- `spring-boot-maven-plugin` bez konfiguracji (`pom.xml:152-155`): fat jar z repackage; indeks warstw włączony domyślnie w Boot, więc `java -Djarmode=tools -jar … extract --layers` powinno działać (niezweryfikowane lokalnie) ([dockerfiles](https://docs.spring.io/spring-boot/4.0/reference/packaging/container-images/dockerfiles.html)).
- Start bez argumentów = serwer HTTP; z argumentem = komenda operatora i `System.exit` (`SzczurRadarApiApplication.java:23-29`); nieznana komenda → kod 2 (`CommandLine.java:41-52`); `migrate` uruchamia kontekst bez weba z `--spring.flyway.enabled=true`, kod 0/1 (`MigrateCommand.java:44-58`). `ENTRYPOINT ["java", …, "-jar", "<jar>"]` bez `CMD` daje serwer, a `docker run <obraz> migrate` — migrację.
- Kontekst budowania: nie ma `szczur-radar-api/.dockerignore` ani głównego `.dockerignore` (`git ls-files`); lokalny `szczur-radar-api/target/` ma m.in. jar 161 MB. Build na Railway (źródło z GitHub) go nie widzi (`target/` jest w `.gitignore`), ale lokalny `docker build` wyśle go w kontekście — potrzebny `.dockerignore` (wzór: `szczur-radar-web/.dockerignore:1-8`).

### E. Obraz bazowy i JVM

- Eclipse Temurin 25 (25.0.4.1_1): ogólne tagi `25-jdk`/`25-jre` wskazują teraz Ubuntu 26.04 (resolute), więc OS trzeba przypiąć ([official-images](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin)). Rozmiary skompresowane amd64 ([repo-info](https://github.com/docker-library/repo-info/tree/master/repos/eclipse-temurin/remote)): `25-jre-alpine` 75,5 MB, `25-jre-noble` 106,7 MB, `25-jdk-noble` 142,4 MB. Alpine to musl zamiast glibc.
- Alternatywy: `bellsoft/liberica-openjre-debian:25-cds` (~128 MB, używany w przykładach Spring Boot), distroless `java25-debian13` (bez powłoki — wyklucza `railway ssh` + `create-admin` i opakowania `sh -c`), Corretto `25-al2023-headless` (~158 MB).
- Heap: `MaxRAMPercentage` w JDK 25 nadal domyślnie 25% (propozycja 75% — JDK-8350596 „Won't Fix”); G1 tylko na maszynie „server-class” (≥2 CPU i ≥1792 MB), inaczej SerialGC ([GC ergonomics 25](https://docs.oracle.com/en/java/javase/25/gctuning/ergonomics.html)). Limity pamięci/CPU kontenera na Railway nie są udokumentowane; w repo nie ma żadnej wartości `JAVA_OPTS` ani pomiaru pamięci (`JAVA_OPTS` występuje tylko w komendzie startowej).
- `availableProcessors()` liczone z limitu CPU cgroup od JDK 19 (JDK-8281181) — zgodne z istniejącą regułą repo o własnym executorze (`REPOSITORY_GUIDELINES.md`, sekcja Coding Style).

### F. Test obrazu w CI

- Wzór: `web-container-smoke` (`.github/workflows/ci.yml:72-102`) — `docker build` prawdziwego Dockerfile'a, `docker run`, pętla `curl` 30×1 s na health, logi przy błędzie.
- API startuje bez dostępnej bazy: pula jest leniwa, `/api/health` → 200, `/api/ready` → 503 (`HealthController.java:28-46`), ale wymaga ustawionych `PG*` (`szczur-radar-api/README.md:78`) i `LLM_API_PROVIDER`/`LLM_MODEL`/`LLM_API_KEY` (`config/LlmProviderProperties.java:113-118`). Job `backend` ma je już z `vars`/`secrets` (`ci.yml:30-43`). Czy start przejdzie z fikcyjnym kluczem LLM — niesprawdzone. Start JVM może przekroczyć 30 s pętli przy zimnym runnerze.

### G. Dokumenty do aktualizacji przy zmianie

`.railway/railway.ts:61-62`, `:68`, `:73`, `:79` (komentarz); `szczur-radar-api/pom.xml:149` (komentarz); `context/deployment/deploy-plan.md:35`, `:40`, `:41`, `:81`, `:175`; `context/foundation/infrastructure.md:10`, `:13`, `:19`, `:60`, `:86`, `:110`, `:113`, `:129`; `context/foundation/tech-stack-api.md:31`; `REPOSITORY_GUIDELINES.md:45` (CI: dodać smoke test API) i opis `szczur-radar-api/` w Project Structure; `szczur-radar-api/README.md:94` (pre-deploy na Railway). Linie `infrastructure.md:39`, `:49`, `:68`, `:78`, `:82`, `:88`, `:90` i `deploy-plan.md:106`, `:113` to historia — bez zmian.

## Code References

- `.railway/railway.ts:59-110` — serwis `szczur-radar-api` (start, preDeploy, healthcheck, zmienne)
- `.github/workflows/ci.yml:72-102` — `web-container-smoke` (wzór dla API)
- `.github/workflows/ci.yml:197-228` — bramka planu IaC
- `node_modules/railway/dist/index-C3uk0ruc.d.ts:69-79` — `BuildConfig` (builder, dockerfilePath)
- `node_modules/railway/dist/index.js:150-171`, `:260-276` — `service()`, `normalizeBuild`, `normalizeDeploy`
- `szczur-radar-api/pom.xml:149-155` — `finalName`, plugin Boot bez konfiguracji
- `szczur-radar-api/src/main/java/pl/szczurradar/api/SzczurRadarApiApplication.java:23-29` — serwer vs komenda
- `szczur-radar-api/src/main/java/pl/szczurradar/api/health/HealthController.java:28-46` — health/ready
- `szczur-radar-web/Dockerfile:1-18`, `szczur-radar-web/.dockerignore:1-8` — istniejący wzorzec

## Architecture Insights

- Repo ma już precedens „Dockerfile wykrywany automatycznie, bez pola builder w IaC” (web). Spójne z nim podejście dla API: Dockerfile w `szczur-radar-api/`, bez `build.builder` w `railway.ts` — mniej pól w planie IaC, mniejsze ryzyko trafienia w regułę `source`.
- Jeden obraz, różne komendy: serwer (domyślnie), `migrate` (pre-deploy), `create-admin` (operator przez `railway ssh`), w przyszłości pipeline (cron). Wymaga `ENTRYPOINT` na `java … -jar` i komend Railway zgodnych z formą exec.
- Dwa spójne warianty startu: (a) usunąć `start` z IaC, flagi JVM w `ENTRYPOINT` lub w `JAVA_TOOL_OPTIONS`; (b) zostawić `start` jako `/bin/sh -c "exec java $JAVA_OPTS -jar …"`. Wariant (a) wymaga potwierdzenia, że usunięcie `start` z IaC czyści komendę zapisaną na serwisie.
- Dwa warianty układu jara: (a) fat jar w `/app/target/szczur-radar-api.jar` — runbook i `preDeploy` bez zmian; (b) warstwowy (`extract --layers`) — lepszy cache warstw i gotowość na AOT, ale nowa ścieżka w `preDeploy`, runbooku i dokumentach.

## Historical Context (from prior changes)

- `context/foundation/infrastructure.md:19`, `:60` — Railway wybrany m.in. za DX Railpacka (autodetekcja); własny Dockerfile liczono jako koszt innych platform (`:49`, `:68`). Supported dziś jako historia decyzji, nie jako zakaz.
- `context/archive/2026-10-01-java-api-rewrite/research.md:181`, `:276-280`, `:300` — w F-03 pytanie „Railpack czy własny Dockerfile” rozstrzygnięto na Railpack, bo wykrywa `pom.xml` i obsługuje `RAILPACK_JDK_VERSION`. Żadna wcześniejsza zmiana nie odrzuciła Dockerfile'a dla API z konkretnego powodu.
- `context/archive/2026-10-01-java-api-rewrite/plan.md:51`, `:445` — AOT/CDS poza zakresem F-03 do czasu pomiaru zimnego startu; pomiar jest (`deploy-plan.md:51-56`), decyzja wciąż otwarta.
- `context/foundation/roadmap.md:268` — self-hosted runner na lokalnym Dockerze (issue #30) zaparkowany; niezwiązany z obrazem API.

## Related Research

- `context/archive/2026-10-01-java-api-rewrite/research.md` — wybór Railpacka dla Javy.
- `context/changes/assessment-contract-harness/research.md` — nie dotyczy (LLM).

## Open Questions

1. Jak `railway config plan --json` opisuje zmianę `startCommand`/`preDeployCommand` (i ewentualnie `build.builder`) i czy opis zawiera słowo `source` blokowane przez bramkę? Rozstrzyga: dry-run planu na PR env.
2. Czy usunięcie `start` z `railway.ts` czyści komendę zapisaną na serwisie, czy potrzebne jest `startCommand: null`?
3. Fat jar w `target/` czy układ warstwowy? (wybór projektowy dla planu)
4. Flagi JVM: `JAVA_TOOL_OPTIONS` jako zmienna serwisu, flagi w `ENTRYPOINT` czy `sh -c` z `$JAVA_OPTS`? Jakie wartości (`MaxRAMPercentage`) przy nieznanym limicie pamięci kontenera Railway — do zmierzenia `java -XX:+PrintFlagsFinal -version` w kontenerze.
5. Obraz uruchomieniowy: `eclipse-temurin:25-jre-noble` (glibc, powłoka) czy `25-jre-alpine` (mniejszy, musl)?
6. Czy smoke test obrazu w CI przejdzie z fikcyjnym `LLM_API_KEY`, czy musi użyć sekretu CI?
7. Czy top-level `watchPatterns` w `railway.ts` jest faktycznie ignorowane (ustalenie poboczne z sekcji C) — warto sprawdzić w tym samym dry-runie.
