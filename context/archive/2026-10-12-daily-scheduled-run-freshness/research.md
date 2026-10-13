---
date: 2026-10-12T17:08:02+0200
researcher: Claude (Opus 5.5) for kwmacioszek
git_commit: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662
branch: feature/daily-scheduled-run-freshness
repository: szczur-radar
topic: "S-05 daily-scheduled-run-freshness — co już działa po S-02, czego brakuje do codziennego automatycznego przebiegu na produkcji i etykiety „Dane zaktualizowane”, oraz jakie decyzje musi podjąć plan"
tags: [research, pipeline, scheduler, railway, cron, security, freshness, feed-view, wykop]
status: complete
last_updated: 2026-10-12
last_updated_by: Claude (Opus 5.5)
---

# Research: S-05 daily-scheduled-run-freshness

**Date**: 2026-10-12T17:08:02+0200
**Researcher**: Claude (Opus 5.5) for kwmacioszek
**Git Commit**: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662 (= `origin/main` w chwili badania)
**Branch**: feature/daily-scheduled-run-freshness
**Repository**: szczur-radar

Badanie tylko do odczytu: kod, dokumenty `context/`, `.railway/railway.ts`, CI i dokumentacja Railway. Nie uruchamiałem buildów, testów ani komend Railway zmieniających stan; stanu produkcji nie odczytywałem.

## Research Question

Wycinek S-05 z `context/foundation/roadmap.md:208-222`: „przepływ uruchamia się sam codziennie o ustalonej godzinie na produkcji, a użytkownik widzi nad listą datę i godzinę ostatniej UDANEJ aktualizacji danych” (US-01, FR-002, FR-030; issue #18). `change.md` nie ma notatek, więc pytania wynikają z roadmapy (cztery „Unknowns” i „Risk”, `roadmap.md:216-221`):

1. Co z maszynerii przebiegów (S-02) da się użyć bez zmian, a czego brakuje do wyzwolenia bez sesji admina?
2. Jak może działać dzienny wyzwalacz na Railway (cron, uśpienie API, sieć prywatna, środowiska PR) i jak wpasować wywołanie maszynowe w model bezpieczeństwa?
3. Co dokładnie znaczy „ostatni udany przebieg” w obecnym modelu statusów i skąd SPA ma wziąć tę datę?
4. Jak domknąć dzień (F9: stronicowanie Wykopu) i jaki zakres dat ma przebieg dzienny?
5. Jak wyzwalacz koliduje z deployami, ręcznymi przebiegami admina i backupem o 02:00 UTC?
6. Czy ryzyko „sprawdzenie wersji schematu przed zapisem” jest nadal aktualne?

## Summary

1. **Maszyneria przebiegów już istnieje (S-02).** Start `POST /api/admin/pipeline-runs` (202), kolejka w pamięci z jednym wątkiem, blokada doradcza + `existsActive` (409 `pipeline_busy`), statusy, znaczniki czasu i odzyskiwanie po restarcie są w kodzie (`PipelineRunService.java:80-81,107-112,134-149`; `PipelineStartupRecovery.java:50-72`; `V3__create_pipeline_and_posts.sql:7-40`). Zdanie z ryzyka S-05 „wprowadza zapis każdego przebiegu” (`roadmap.md:221`) jest nieaktualne: tabela `pipeline_runs` istnieje od S-02. Brakuje trzech rzeczy: wyzwalacza bez sesji admina, odczytu „ostatniego udanego przebiegu” dostępnego dla roli `user` oraz etykiety w `FeedView`.
2. **Bloker modelu danych dla wyzwalacza maszynowego.** `pipeline_runs.requested_by` jest `UUID NOT NULL` z kluczem obcym do `users` (`V3__create_pipeline_and_posts.sql:14,28`), encja wymaga wartości (`PipelineRun.java:107`), a kontroler bierze ją z sesji (`PipelineRunController.java:81`). Przebieg bez użytkownika wymaga migracji (np. kolumna kto/co wyzwoliło) albo użytkownika systemowego.
3. **`@Scheduled` w API odpada przy uśpieniu** (`roadmap.md:220`; API i web mają `sleepApplication: true`, `.railway/railway.ts:103,159`). Railway budzi uśpiony serwis ruchem z internetu **albo z sieci prywatnej** z tego samego projektu, ale pierwsze żądanie „may return a 502 Bad Gateway” (docs.railway.com/deployments/serverless, sprawdzone 2026-10-12). Zimny start API to ok. 9,6–10,5 s (`deploy-plan.md:118`). Cron, który woła API bezpośrednio przez sieć prywatną, omija ponawianie Caddy (`Caddyfile:29-34`, `lb_try_duration 30s`), więc musi ponawiać sam.
4. **Wywołanie maszynowe a bezpieczeństwo.** `OriginGuardFilter` przepuszcza żądania bez nagłówka `Origin` (`OriginGuardFilter.java:27,46-47`), więc `curl` nie dostanie 403. Filtr tokenu w Spring Security, który bez tokenu odpowiada 401, przechodzi `AuthorizationCompletenessIT` bez wpisu w `PUBLIC_ROUTES` (`AuthorizationCompletenessIT.java:84-107`). Caddy przekazuje publicznie **każdą** ścieżkę `/api/*` (`Caddyfile:29`), więc trasa pod `/api/` jest osiągalna z internetu (chroniona tylko tokenem). Trasa poza `/api/` trafia w Caddy do fallbacku SPA (`Caddyfile:37`), więc jest osiągalna tylko z sieci prywatnej (API nie ma publicznej domeny).
5. **Środowiska PR dostaną kopię crona.** Nowy serwis powstaje w każdym PR env przez IaC (bramka CI przepuszcza `resource.create`, `ci.yml:343-354`) i przez kopiowanie produkcji. PR env ma prawdziwe klucze Wykopu i klucz LLM z CI (`ci.yml:252-327`). Bez straży środowiska, jak w `tools/db-backup/backup.sh:26-34`, każdy otwarty PR env robiłby co noc prawdziwy przebieg.
6. **„Udany przebieg” w kodzie:** `SUCCEEDED` tylko bez nieudanych ocen, bez postów `DEAD` i bez błędów technicznych; inaczej `PARTIAL`; `FAILED` gdy krok został przerwany (`RunFlow.java:153-158`). `SUCCEEDED` obejmuje też przebieg bez nowych postów: dyspozytor wraca przed sprawdzeniem LLM (`ConcurrentAssessmentDispatcher.java:99-101`). Przebieg `FAILED` przerwany w kroku oceny mógł zapisać część ocen (`ConcurrentAssessmentDispatcher.java:156-160`). Zapytania o „ostatni udany przebieg” nie ma ani w API, ani w SPA (`PipelineRunRepository.java:16-25`; `PostController.java:56`).
7. **Doba to doba kalendarzowa Europe/Warsaw**, a nie UTC (`PipelineRunService.java:56-57,108-110`), natomiast cron Railway działa w UTC (docs.railway.com/cron-jobs). Stała godzina UTC przesuwa się o godzinę względem Warszawy przy zmianie czasu. Walidacja zakresu: `from <= to <= dziś (Warszawa)`, najwyżej 7 dni (`PipelineRunController.java:136-143`).
8. **F9 potwierdzone.** Stronicowanie liczy pobrane elementy (z duplikatami) do `total`, więc element przesunięty na już przeczytaną stronę po usunięciu innego ginie (`WykopRepository.java:126-142`; `reviews/impl-review.md:129-137` w archiwum S-02). Powtórka dnia kosztuje zapytania do Wykopu, ale nie wywołania LLM dla postów już zapisanych (`AssessmentSelection.java:37-44`; `PipelineAgentIT.java:182-193`). Zakładka (np. dwa dni) lub drugie przejście są tanie po stronie LLM.
9. **Kolizje:** drugi start podczas aktywnego przebiegu dostaje 409 i nic nie tworzy (`PipelineRunService.java:136-139`). Przebieg `QUEUED` lub `RUNNING` przy zamknięciu albo starcie instancji staje się `INTERRUPTED` i nigdy nie jest wznawiany (`PipelineStartupRecovery.java:50-72`; `RunRecorder.java:141-148`). Wniosek z cyklu życia Spring Boot, niezweryfikowany testem: odzyskiwanie startuje na `ApplicationReadyEvent`, już po otwarciu portu, więc żądanie crona, które samo obudziło API, może zostać oznaczone `interrupted` przez to odzyskiwanie (szczegóły niżej).
10. **Wersja schematu.** W `src/main` nie ma żadnego sprawdzenia wersji schematu w czasie działania: Flyway jest wyłączony przy starcie serwera (`application.properties:14-15`) i działa tylko w komendzie `migrate`. Przesłanka ryzyka (cron z innego commita, `infrastructure.md:121`) zniknęła, bo pipeline działa w procesie API. Zostaje okno nakładania się deployu (stara instancja kończy przebieg po migracji nowej). Plan musi to ryzyko wycofać z uzasadnieniem albo przeformułować.

## Detailed Findings

### 1. Maszyneria przebiegów (S-02) — co jest

- **Start.** `StartRequest(from, to, sources, postIds)`: albo zakres, albo lista postów (`PipelineRunController.java:129-143`). Zakres jest zamieniany na `[from 00:00:00, to 23:59:59]` czasu warszawskiego (`PipelineRunService.java:107-110`), a Wykop dostaje `date_from`/`date_to` w tym samym czasie (`WykopFetcher.java:43-44`, wg raportu pracownika; `WykopRepository.java:95-120`).
- **Kolejność RETRY → RANGE.** Gdy istnieją „resztki” (posty `stored` albo `failed` z < 3 próbami, z dowolnej daty; `PostRepository.java:34-48`), start zakresu tworzy w jednej transakcji `[RETRY, RANGE]` (`PipelineRunService.java:111`). RETRY bierze wszystkie resztki bez limitu (`AssessmentSelection.java:52-54`). Przegląd S-02 odłożył limit RETRY „revisit with S-05” (`context/archive/2026-10-06-first-assessed-wykop-feed/follow-ups/review-fixes.md:19`).
- **Jeden przebieg naraz.** W transakcji z `pg_advisory_xact_lock` serwis sprawdza `existsActive()` (status `queued` lub `running`); jeśli taki jest, rzuca `PipelineBusyException` → 409 `pipeline_busy` (`PipelineRunService.java:134-144`; `PipelineRunRepository.java:16-18`). Druga linia obrony: unikalny indeks częściowy na `status = 'running'` (`V3__create_pipeline_and_posts.sql:38-39`). Wykonanie: `Executors.newSingleThreadExecutor` w pamięci procesu (`PipelineRunService.java:80-81`); wiersz `queued` w bazie nie jest podejmowany przez inną instancję ani po restarcie.
- **Idempotencja dnia (FR-003).** Post jest zapisywany raz na `(source, item_type, source_item_id)` (`PostStore.java:56-77`; `V4__posts_shared_item_type.sql:16`). Przebieg RANGE ocenia tylko posty, które sam zapisał jako pierwszy (`AssessmentSelection.java:37-44`). Test `theSameRangeAgainStoresNoDuplicatesAndAssessesNothingAssessed` (`PipelineAgentIT.java:182-193`).
- **Akcje Embabel.** Wszystkie 7 akcji `PipelineAgent` ma `ActionRetryPolicy.FIRE_ONCE` (`PipelineAgent.java:67,77,85,93,99,105,115`), zgodnie z lekcją z `lessons.md:82-87`.

### 2. Statusy, znaczniki czasu i „ostatni udany przebieg”

- **Statusy:** `QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, INTERRUPTED` (`RunStatus.java:13`); rodzaje `RANGE, RETRY, POSTS` (`RunKind.java:13`; CHECK w `V3__create_pipeline_and_posts.sql:29-31`).
- **Wyliczenie statusu końcowego** (`RunFlow.java:153-158`):
  - `FAILED`, gdy krok został zatrzymany (`stoppedBy`): nieudane sprawdzenie LLM przed oceną (`ConcurrentAssessmentDispatcher.java:102-108`) albo `LlmConfigurationException` w trakcie, np. wyczerpany limit klucza OpenRouter (`ConcurrentAssessmentDispatcher.java:156-160`; `PipelineAgentIT.java:362-382`). W tym drugim przypadku oceny zapisane przed zatrzymaniem zostają.
  - `FAILED` także przez wyjątek w kroku pobrania lub zapisu (błąd Wykopu, źródło nieskonfigurowane, błąd bazy): `PipelineRunService.java:160-182`, `RunRecorder.java:68-75`, test `aFailingSourceFailsTheRunAndTheQueueTakesTheNextRequest` (`PipelineAgentIT.java:281-300`).
  - `SUCCEEDED`, gdy `failed == 0 && dead == 0` i brak błędów technicznych; w przeciwnym razie `PARTIAL`. `failed` liczy każdą zapisaną nieudaną ocenę: naruszenie kontraktu oraz `provider_error`/`timeout` po ponowieniach transportu (wg raportu pracownika: `Assessor.java:121-132`).
  - Przebieg z zerową liczbą postów do oceny kończy się `SUCCEEDED` bez wywołania LLM (`ConcurrentAssessmentDispatcher.java:99-101`).
- **Znaczniki czasu** (`TIMESTAMPTZ`, `V3__create_pipeline_and_posts.sql:15-17`): `created_at` (utworzenie), `started_at` (`queued → running`, `RunRecorder.java:38`), `finished_at` (koniec, porażka lub przerwanie: `RunRecorder.java:55,69,132,143`). Zakres przebiegu RANGE jest w `range_from`/`range_to`.
- **Brak odczytu dla użytkownika.** `PipelineRunRepository` ma tylko `existsActive`/`existsByStatusIn` i `findPage` po `created_at` (`PipelineRunRepository.java:16-25`); indeksy: unikalny „jeden running” i `(created_at DESC, id DESC)` (`V3__create_pipeline_and_posts.sql:38-40`). `GET /api/posts` zwraca `PostPage(items, page, size, total)` bez metadanych (`PostController.java:44-57`). Jedyne trasy z informacją o przebiegach są pod `/api/admin/` (`PipelineRunController.java:46`), czyli tylko dla admina (`SecurityConfig.java:57`).
- **Pytania dla etykiety, których kod nie rozstrzyga** (decyzje, nie fakty):
  - czy liczy się tylko `SUCCEEDED`, czy też `PARTIAL` (PRD mówi „nigdy przebiegu zakończonego błędem”, `prd.md:207-209`; NFR traktuje „częściowy błąd” jako sygnał dla operatora, `prd.md:246`);
  - które rodzaje się liczą: RETRY i POSTS nie pobierają nowych danych; ręczny RANGE admina dla starego dnia przesunąłby etykietę, choć bieżący dzień mógł nie zostać pobrany; obecnie nic nie odróżnia przebiegu zaplanowanego od ręcznego;
  - który znacznik: `finished_at` (kiedy zakończono) czy `range_to` (do kiedy sięgają dane).

### 3. Wyzwalacz na Railway — ograniczenia platformy

- **Decyzje historyczne:** użytkownik nie chce crona startującego kontener z obrazu API (`context/archive/2026-10-04-api-docker-image/plan-brief.md:32`). Kandydat z roadmapy: mały serwis cron z `curl` wysyła `POST` przez sieć prywatną do wewnętrznego endpointu z tokenem, API odpowiada 202 (`roadmap.md:220`).
- **Cron Railway** (docs.railway.com/cron-jobs, wg raportu pracownika z cytatami): harmonogram w UTC; odstęp ≥ 5 min; jeśli poprzednie wykonanie jeszcze trwa, nowe jest pomijane; serwis ma wykonać zadanie i się zakończyć; czas startu może się różnić o kilka minut. Brak ręcznego wyzwolenia: obejściem jest tymczasowy harmonogram w panelu (`deploy-plan.md:286`). Status wykonania to kod wyjścia; porażka = `CRASHED` + e-mail Railway, także w PR env (`deploy-plan.md:281`).
- **Uśpienie i budzenie** (docs.railway.com/deployments/serverless, odczyt 2026-10-12): bezczynność mierzona po ruchu **wychodzącym**, uśpienie 5–10 min po ostatnim pakiecie; „A service is woken when it receives traffic from the internet or from another service in the same project through the private network”; „The first request sent to a slept service may return a 502 Bad Gateway response”. Uśpiony serwis nie ma kontenera (`deploy-plan.md:49`), więc obudzenie to pełny start JVM: 7,9–8,3 s startu, pierwsze żądanie ok. 9,6–10,5 s (`deploy-plan.md:117-118`).
- **Trwający przebieg utrzymuje API przy życiu:** ruch do Wykopu, LLM i bazy. Zweryfikowano w PR env S-02 (`context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:109,670`, punkt 6.4). Niezweryfikowane: pojedyncze oczekiwanie na LLM do 300 s (`LLM_TIMEOUT` przy flex) bez innego ruchu; przy 4 wątkach i zapisach do bazy jest mało prawdopodobne.
- **Wzorzec do skopiowania: `db-backup`** (`.railway/railway.ts:183-204`): `source: github(REPO, { branch })` bez `checkSuites` (`railway.ts:178-182`), `rootDirectory`, `watchPatterns`, `deploy: { cronSchedule, restartPolicyType: "NEVER", limitOverride }`, dopisanie do `resources` (`railway.ts:207`). W `tools/db-backup/` przydatne są: straż środowiska z nadpisaniem (`backup.sh:26-34`), limit czasu przez `timeout` (`backup.sh:36-46`), pętla oczekiwania na sieć prywatną po starcie kontenera (`backup.sh:141-147`), wzorzec `test.sh` i job CI `db-backup-test` (`ci.yml:180-191`). Obraz `postgres:18` jest za ciężki dla wyzwalacza.
- **Bez powłoki w komendzie startu:** komenda startu Railway nie rozwija zmiennych (`railway.ts:89-90`), więc `curl` z `$TOKEN` wymaga skryptu lub `sh -c`.
- **Port i domena:** `API_INTERNAL_PORT = "8000"` (`railway.ts:40`), domena prywatna przez `api.env.RAILWAY_PRIVATE_DOMAIN` (wzorzec web, `railway.ts:161-162`).

### 4. Wywołanie maszynowe w modelu bezpieczeństwa

- **Dziś:** domyślna odmowa `anyRequest().authenticated()` dla każdej ścieżki, nie tylko `/api/` (`SecurityConfig.java:52-58`); CSRF, form login i basic auth wyłączone, obrona to `SameSite=Strict` + `OriginGuardFilter` (`SecurityConfig.java:59-62`). Kontekst bezpieczeństwa zapisywany do żądania i sesji (`SecurityConfig.java:76-79`): filtr tokenu nie może zakładać sesji, bo każde wywołanie crona dodawałoby wiersz `spring_session`. W `src/main` nie ma uwierzytelniania tokenem ani `@Scheduled`.
- **`OriginGuardFilter`** działa tylko dla POST/PUT/PATCH/DELETE pod `/api/**` (`OriginGuardFilter.java:33-41`) i odrzuca tylko obecny, obcy `Origin` (`:46-47`): „Requests without Origin (curl, server-to-server) pass” (`:27`).
- **`AuthorizationCompletenessIT`** przechodzi przez wszystkie zmapowane trasy aplikacji, także spoza `/api/` (`AuthorizationCompletenessIT.java:109-125`), i wymaga 401 bez sesji dla każdej trasy spoza `PUBLIC_ROUTES` (`:51-55`, `:84-107`). Trasa pod `/api/admin/**` podlega `hasRole(ADMIN)` (`SecurityConfig.java:57`) i musi być w `ADMIN_ROUTES` (`:58-63`), a wtedy `AdminRoleIT` wymaga 403 dla roli USER (`AdminRoleIT.java:53-61`).
- **Ekspozycja trasy:**
  - pod `/api/` — Caddy przekazuje ją publicznie (`Caddyfile:29-34`); chroni ją tylko token; można dodać w Caddy bardziej szczegółowy `handle` odrzucający np. `/api/internal/*` (bloki `handle` są wzajemnie wykluczające się, caddyserver.com/docs/caddyfile/directives/handle, wg raportu pracownika);
  - poza `/api/` — w Caddy trafia do fallbacku SPA (`Caddyfile:37`), więc z internetu dostaje `index.html`; z sieci prywatnej trafia do API. Filtry `OriginGuardFilter` i `SessionCookieRenewalFilter` jej nie dotyczą (oba tylko `/api/**`).
- **Przechowywanie tokenu:** `preserve()` na API (wzorzec `railway.ts:123-146`) + referencja z serwisu crona to jedna wartość w jednym miejscu. Zapieczętowane wartości nie są kopiowane do PR env (docs.railway.com/variables). Niewyjaśnione w dokumentacji: czy referencja do **zapieczętowanej** zmiennej innego serwisu się rozwiązuje. Pola SDK `isSealed`/`generator` nie mają opisu semantyki IaC (wg raportu pracownika, `railway@3.11.0` d.ts:59-68). Usunięcie zadeklarowanej zmiennej to `variable.delete`, które bramka CI odrzuca (`railway.ts:106-108`), więc nazwy zmiennej nie da się później wycofać z IaC.

### 5. Środowiska PR

- Cron powstanie w każdym PR env: IaC stosowane do PR env (`ci.yml:199-215`) przepuszcza `resource.create` (`ci.yml:343-354`, komentarz wprost wymienia S-05), a nowy PR env jest kopią produkcji (`railway.ts:170-174`).
- PR env ma te same klucze aplikacji Wykopu co produkcja i klucz LLM z CI (`ci.yml:252-327`; `deploy-plan.md:186`), więc niestrzeżony cron robiłby co noc prawdziwy przebieg w każdym otwartym PR env i dzieliłby limity klucza Wykopu z produkcją.
- Zapieczętowany token tylko na produkcji powoduje 401 w PR env, a więc `CRASHED` i e-mail co noc dla każdego PR env. To samo, gdy API lub baza PR env nie zostały wdrożone (PR bez zmian API nie wdraża API, `deploy-plan.md:58`). Straż `RAILWAY_ENVIRONMENT_NAME == production` z nadpisaniem (wzorzec `backup.sh:26-34`) rozwiązuje oba przypadki.

### 6. Kompletność dnia, zakres dat i koszt powtórki

- **F9:** `received++` liczy każdy element, także duplikaty i nieparsowalne (`WykopRepository.java:127`), deduplikacja `putIfAbsent` (`:137-138`), koniec pętli przy `received >= total` (`:140-142`). Przy zmniejszeniu wyniku między stronami element przesunięty na przeczytaną stronę ginie bez błędu. Inne limity: 50 na stronę, 300 ms przerwy, `MAX_PAGES=200` (`:33-38`). Klient Wykopu nie ponawia, poza jednym odświeżeniem tokenu przy 401 (wg raportu pracownika, `WykopApiClient.java:25-26,81-85`).
- **Powtórka dnia:** pobiera ponownie wszystkie strony (bez kursora), nie zapisuje ponownie postów ani RAW (`PostStore.java:57-58`), nie wywołuje LLM dla postów już zapisanych. Nowo znaleziony post kosztuje sprawdzenie LLM i jedną ocenę. Jeśli istnieją resztki, poprzedzający RETRY wydaje sprawdzenie LLM i jedną ocenę na resztkę (`PipelineRunService.java:111`).
- **Wielkość dnia:** ok. 270 postów (`context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:586`; `deploy-plan.md:227`), ok. 6+1 stron zapytań na dzień (`context/archive/2026-10-06-first-assessed-wykop-feed/research.md:188-190`, wg raportu pracownika). Przebieg dnia: ok. 12–25 min przy 4 wątkach i 10–20 s na ocenę (`…/first-assessed-wykop-feed/plan.md:586`).
- **Zakres dat:** walidator przyjmuje `to == dziś` (`PipelineRunController.java:141`), więc przebieg „dziś” pobrałby niepełny dzień. Zakres dwudniowy `[D-2, D-1]` mieści się w limicie 7 dni i daje drugie przejście dnia D-2 (zakładka z `roadmap.md:218`).
- **Granica doby:** dzień Warszawy kończy się o 22:00 UTC (czas letni) lub 23:00 UTC (zimowy), więc godzina crona w UTC musi leżeć po tej granicy w obu porach roku, jeśli przebieg ma pobierać „wczoraj”.

### 7. Kolizje: ręczne przebiegi, deploy, odzyskiwanie, backup

- **409 przy aktywnym przebiegu** (`PipelineRunService.java:136-139`): zaplanowany start w trakcie ręcznego przebiegu admina nic nie tworzy. Archiwum nie określa, co wtedy robi wyzwalacz (kod wyjścia, ponowienie).
- **Deploy przerywa przebieg.** Reguła operatora: nie merguj do `main`, gdy przebieg jest `RUNNING` (`REPOSITORY_GUIDELINES.md` § Deployment; `deploy-plan.md:174`). Zamknięcie instancji oznacza jej przebiegi `interrupted` (`PipelineRunService.java:190-196`), a nowa instancja na starcie oznacza wszystkie `queued`/`running` (`RunRecorder.java:141-148`). Wznowienia nie ma (`…/first-assessed-wykop-feed/plan.md:79`, wg raportu pracownika). Przerwany RANGE, który nie skończył pobierania, zostawia dzień niepobrany do czasu kolejnego przebiegu tego dnia, co zakładka też by naprawiła.
- **Wyścig przy budzeniu (wniosek, niezweryfikowany testem).** `PipelineStartupRecovery.recover()` słucha `ApplicationReadyEvent` (`PipelineStartupRecovery.java:50`) i bez sprawdzania właściciela zmienia wszystkie `queued`/`running` na `interrupted` (`RunRecorder.java:141-148`). Spring Boot publikuje `ApplicationReadyEvent` po starcie serwera WWW (koniec `refresh()`), więc istnieje krótkie okno, w którym port już przyjmuje żądania, a odzyskiwanie jeszcze nie przebiegło. Gdy uśpione API budzi właśnie `POST` crona ponawiany co sekundę-dwie, pierwsze udane żądanie może trafić w to okno. Utworzony przebieg zostałby wtedy oznaczony `interrupted` z błędem „the application restarted” i nie wykonałby się. Admin z SPA praktycznie nie trafia w to okno (najpierw ładuje stronę i `/api/auth/me`). Plan powinien to sprawdzić testem albo przenieść odzyskiwanie przed otwarcie portu, albo odmawiać startu do jego zakończenia.
- **Backup o 02:00 UTC** (`railway.ts:188`): F-01 zostawiło godzinę przebiegu S-05 i ewentualne przesunięcie dumpa (`context/archive/2026-10-11-production-data-gate/plan.md:77`; `railway.ts:175`). `pg_dump` czyta jeden snapshot i nie blokuje zapisów pipeline (`…/production-data-gate/plan.md:570`); kontrola liczby wierszy akceptuje zapisy w trakcie (wartości w przedziale „przed”–„po”, `…/production-data-gate/plan.md:101`, wg raportu pracownika). Przebieg zakończony przed dumpem trafia do kopii tej samej nocy; odtworzenie traci wtedy mniej opłaconych ocen LLM (`deploy-plan.md:289`). Kolejności nie ustalono.

### 8. Etykieta świeżości w SPA

- **Miejsce:** `FeedView.vue` — między `<h1>Posty</h1>` (`:46`) a łańcuchem stanów błąd/ładowanie/pusto/dane (`:47-59`), więc etykieta nie zależy od gałęzi stanów. Widok pobiera `fetchPosts(page, 50)` (`FeedView.vue:8,24-34`) i nie czyta roli.
- **Formatowanie daty:** wspólny `formatDateTime` (`pl-PL`, `dateStyle/timeStyle: short`, `timeZone: 'Europe/Warsaw'`, `labels.ts:49-58`), używany przez `PostCard.vue:20` i `AdminView.vue:229`.
- **Komponenty:** `StatusMessage` ma tylko tony `ok`/`error` z rolami `status`/`alert` (`StatusMessage.vue:3,7`), więc nie pasuje do statycznej etykiety (rola `status` ogłaszałaby ją czytnikowi ekranu jak wynik akcji). `StatusBadge` ma tony `success|warning|destructive|neutral|info` (`StatusBadge.vue:3`). Nowy wspólny komponent trafia do `src/components/` i na kitchen sink (`REPOSITORY_GUIDELINES.md` § UI).
- **Kształt danych:** dodanie pola do `PostPage` rozjeżdża wspólny typ `Page<T>` (`posts.ts:45-50`, importowany też przez `pipeline.ts:3`); osobna trasa uwierzytelniona (poza `/api/admin/`) nie wymaga zmian list w `AuthorizationCompletenessIT`. NFR „brak wglądu w dane cudzych kont” (`prd.md:250`) oznacza, że odczyt dla roli `user` nie powinien ujawniać np. `requested_by`.
- **Testy i zrzuty:** `FeedView.test.ts` mockuje `../api/posts` fabryką (`:11`), więc nowa funkcja w `posts.ts` musi do niej trafić. `dev/mock-api.js` nie obsługuje `/api/posts` (spada na 404, `:51`) i zawsze zwraca rolę `admin` (`:44`), więc zrzuty widoku listy wymagają rozszerzenia mocka. `FeedView.vue` jest na liście skanu „no literals” (`styleContract.test.ts:85-95`), ale ma literały px, których regex nie łapie (wg raportu pracownika).
- **Proces UI:** `FeedView` już się renderuje, a `REPOSITORY_GUIDELINES.md` § UI każe prace UI na istniejącym widoku prowadzić przez `/10x-ui` (macierz 7 stanów, zrzuty w obu motywach i przy 375 px). Osobna zmiana `feed-view-ui-hardening` jest zapowiedziana przed S-03/S-04 (`context/archive/2026-10-10-admin-panel-ui-hardening/ui-after/README.md:87-88`, wg raportu pracownika).

### 9. Sprawdzenie wersji schematu przed zapisem

- **Stan kodu:** `spring.flyway.enabled=false` przy starcie serwera (`application.properties:14-15`); Flyway włącza tylko `MigrateCommand` (`cli/MigrateCommand.java:11-23,54-57`, wg raportu pracownika); Hibernate `ddl-auto=none` bez `validate` (`application.properties:22-25`); `/api/ready` robi tylko `SELECT 1`. Nie ma odczytu `flyway_schema_history` w `src/main` (przeszukane: `schema_history`, `schemaVersion`, `MigrationInfo`, `flyway`; jedyne `schema_version` to pole wejścia kontraktu oceny, `AssessmentInputV1.java:13-20`, niezwiązane). `JpaSchemaIT` sprawdza encje względem schematu tylko w testach.
- **Źródło ryzyka:** pre-mortem (`infrastructure.md:93`) i rejestr ryzyk: „The cron service deploys from the same commit … The pipeline checks the Flyway schema version at start and aborts on mismatch” (`infrastructure.md:121`), roadmapa S-05 Risk (`roadmap.md:221`).
- **Werdykt:** przesłanka „cron zbudowany ze starszego commita” nie dotyczy obecnej architektury, bo pipeline działa w procesie API, a `migrate` jest pre-deployem tego samego obrazu. Zostaje okno nakładania się deployu: nowa instancja migruje i startuje obok starej, która kończy przebieg na nowym schemacie (`…/first-assessed-wykop-feed/plan.md:100-104`). Wyzwalacz typu „cron + curl” nie pisze do bazy, więc sam nie wnosi ryzyka wersji schematu.

### 10. Koszt i limit LLM

- Produkcyjny model GPT-6 Luna przez OpenRouter: ok. $5,9/mies. (`default`) albo ok. $2,9/mies. (`flex`) przy 12 000 postów (`deploy-plan.md:159`), czyli ok. $0,0005 lub $0,00025 na post. Przy ok. 270 postach dziennie daje to ok. $0,13 lub $0,07 dziennie (szacunek z tych dwóch wartości).
- Twardy pułap: limit $10/mies. na kluczu OpenRouter produkcji. Wyczerpany limit kończy przebieg `FAILED` bez zużycia prób postów (`OpenRouterClient.java:57-76,244-255`; `ConcurrentAssessmentDispatcher.java:156-160`; test `PipelineAgentIT.java:362-382`). Budżet na przebieg nadal nieustalony (`roadmap.md:271`).
- Luka: obecny tier produkcji nie jest nigdzie zapisany. 2026-10-09 przełączono na `default` jako obejście (`deploy-plan.md:213`), a runbook powrotu na `flex` jest w `deploy-plan.md:215-221` (issue #39).

### 11. Sygnał awarii dla operatora (granica z S-06)

- NFR: operator dostaje jednoznaczny sygnał, gdy **zaplanowany** dzienny przebieg się nie powiedzie lub zakończy częściowym błędem (`prd.md:246`). S-06 dostarcza dashboard historii (`roadmap.md:224-235`), z otwartym pytaniem, czy dashboard wystarczy jako sygnał (`roadmap.md:233`).
- Cron, który tylko kolejkuje przebieg (202), kończy się sukcesem także wtedy, gdy przebieg później padnie: `CRASHED` + e-mail Railway (`deploy-plan.md:281`) sygnalizuje tylko porażkę samego wywołania. Gdyby cron czekał na koniec przebiegu (ok. 12–25 min) i kończył się kodem ≠ 0 przy `failed`/`partial`, e-mail Railway dawałby aktywny sygnał bez S-06. Wymaga to odczytu statusu przebiegu z tokenem. To opcja, nie decyzja.

## Code References

- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:56-57` — strefa `Europe/Warsaw` dla zakresów
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:107-112` — `startRange`, RETRY przed RANGE
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:134-149` — blokada doradcza, 409, kolejka w pamięci
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java:76-86` — start, `requested_by` z sesji (`:81`)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java:136-143` — walidacja zakresu
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunFlow.java:153-158` — status końcowy przebiegu
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:99-108,156-160` — zero postów, sprawdzenie LLM, zatrzymanie przez konfigurację
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStartupRecovery.java:50-72` — odzyskiwanie na `ApplicationReadyEvent`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/RunRecorder.java:141-148` — `interruptAll` bez sprawdzania właściciela
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java:16-25` — brak zapytania „ostatni udany”
- `szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:7-40` — `pipeline_runs`, `requested_by NOT NULL` + FK, indeksy
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:126-142` — stronicowanie (F9)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java:52-62,76-79` — reguły dostępu, wyłączone mechanizmy, repozytorium kontekstu
- `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/OriginGuardFilter.java:24-47` — przepuszcza żądania bez `Origin`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java:51-63,84-125` — listy tras i asercja 401
- `szczur-radar-api/src/main/resources/application.properties:14-15,22-25` — Flyway wyłączony przy starcie, `ddl-auto=none`
- `szczur-radar-web/Caddyfile:18-20,29-34,37` — `/healthz`, proxy `/api/*` z ponawianiem, fallback SPA
- `.railway/railway.ts:103,159,183-207` — uśpienie api/web, serwis `db-backup`, `resources`
- `.github/workflows/ci.yml:252-327,342-354` — sekrety PR env, bramka planu z wyjątkiem `resource.create`
- `tools/db-backup/backup.sh:26-46,141-147` — straż środowiska, limit czasu, czekanie na sieć prywatną
- `szczur-radar-web/src/views/FeedView.vue:44-59` — nagłówek i łańcuch stanów listy
- `szczur-radar-web/src/labels.ts:49-58` — `formatDateTime` w strefie warszawskiej
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:44-57` — `GET /api/posts`, `PostPage`

## Architecture Insights

- **Stan przebiegów jest trwały, kolejka nie.** Wiersze `pipeline_runs` przeżywają restart, ale wykonanie jest w jednowątkowym executorze procesu; po restarcie nic nie jest podejmowane, tylko przerywane. Wyzwalacz musi zakładać, że przebieg może nie dojść do skutku, i mieć sposób na naprawę (zakładka, ponowne wywołanie).
- **Wszystkie zmiany stanu to warunkowe UPDATE** (`pipeline/CLAUDE.md:45-47`), więc spóźnione zapisy przerwanego przebiegu są nieszkodliwe. Nie chroni to jednak przed przerwaniem świeżo utworzonego przebiegu przez odzyskiwanie (sekcja 7).
- **Granica publiczne/prywatne wyznacza Caddy, nie API.** Wszystko pod `/api/*` jest publiczne przez web; reszta ścieżek API jest osiągalna tylko z sieci prywatnej. `SecurityConfig` chroni wszystkie ścieżki jednakowo.
- **Wzorzec serwisu cron w IaC już istnieje** (F-01): bez `checkSuites`, straż środowiska w skrypcie, `restartPolicyType: NEVER`, kod wyjścia jako sygnał. Lekcja: nowy zasób IaC sprawdzić na produkcji po pierwszym apply (`lessons.md:99-104`).
- **Doba biznesowa = Warszawa, harmonogram = UTC.** Każda decyzja o godzinie i zakresie musi przeliczać obie strefy i zmianę czasu.

## Historical Context (from prior changes)

- `context/archive/2026-10-04-api-docker-image/plan-brief.md:32` — DECYZJA użytkownika: cron/pipeline poza zakresem, „zajawka w S-05 (cron woła endpoint API)”, bo „użytkownik nie chce crona na obrazie API”. **Wspierane, obowiązuje.**
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:106` — „Osobny serwis workera, niezależny od deployu API, to kierunek dla S-05”. **Sprzeczne** z późniejszym `roadmap.md:220` („S-05 rozstrzyga tylko wyzwalacz dzienny”) oraz `infrastructure.md:17` i `REPOSITORY_GUIDELINES.md` § Deployment. Do rozstrzygnięcia przez użytkownika w planie.
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:109` + `:670` (6.4) — trwający przebieg nie pozwala API zasnąć. **Wspierane** (zweryfikowane w PR env).
- `context/archive/2026-10-06-first-assessed-wykop-feed/reviews/impl-review.md:129-137` — F9, „the daily run (S-05) will not repeat a day”, poprawka: zakładka lub drugie przejście w S-05. **Wspierane** przez kod (`WykopRepository.java:126-142`).
- `context/archive/2026-10-06-first-assessed-wykop-feed/follow-ups/review-fixes.md:19` — limit RETRY „revisit with S-05”. **Otwarte.**
- `context/archive/2026-10-11-production-data-gate/plan.md:77` — godzina przebiegu dziennego należy do S-05, które może przesunąć godzinę dumpa. **Otwarte.**
- `context/archive/2026-10-11-production-data-gate/plan.md:570` — „O 02:00 UTC zwykle nic nie działa (przebiegi są ręczne do S-05)”. **Częściowo:** prawdziwe dziś, przestanie być prawdziwe po S-05, jeśli przebieg wypadnie przed 02:00 UTC i potrwa dłużej.
- `context/foundation/roadmap.md:221` (Risk S-05) — „Wprowadza zapis każdego przebiegu”: **sprzeczne** (zapis istnieje od S-02); „sprawdzenie zgodności wersji schematu przed zapisem”: **częściowo** (przesłanka z `infrastructure.md:121` nieaktualna, okno deployu zostaje).
- `context/foundation/roadmap.md:280` (Parked FR-022) — „wymaga API platformy”: **sprzeczne** z kodem. Admin już uruchamia przebieg na żądanie asynchronicznie i widzi status na liście (`PipelineRunController.java:76-99`), co odpowiada treści FR-022 (`prd.md:186`).
- `context/foundation/infrastructure.md:86` — cron Railway bez ręcznego wyzwolenia, UTC, pomijanie przy trwającym poprzednim. **Wspierane** (docs.railway.com/cron-jobs, wg raportu pracownika).
- `context/archive/2026-10-04-api-docker-image/follow-ups/review-fixes.md` F4 (Hikari gubi bezczynne połączenia) — **otwarte**. Mało istotne dla przebiegu po obudzeniu, bo obudzenie to nowy kontener.

## Related Research

- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md` — pipeline, Wykop, kolejka, odzyskiwanie (S-02)
- `context/archive/2026-10-11-production-data-gate/research.md` — cron `db-backup`, IaC, limity kosztów (F-01)
- `context/archive/2026-10-04-api-docker-image/research.md` — obraz API, pierwotna zajawka crona
- `context/archive/2026-10-10-admin-panel-ui-hardening/research.md` — kontrakt UI, komponenty, zrzuty

## Open Questions

Decyzje dla `/10x-plan` (właściciel: użytkownik, chyba że zaznaczono inaczej):

1. **Wyzwalacz:** cron + `curl` przez sieć prywatną (kandydat z roadmapy) czy inna forma. Do tego: czy trasa ma być poza `/api/` (tylko sieć prywatna) czy pod `/api/` z blokadą w Caddy, i czy mimo prywatności wymagany jest token. Do rozstrzygnięcia też sprzeczność „osobny worker” (S-02 `plan.md:106`) z „tylko wyzwalacz” (`roadmap.md:220`).
2. **Tożsamość przebiegu zaplanowanego:** migracja `requested_by` (np. nullable + kolumna wyzwalacza) czy użytkownik systemowy. Ta sama kolumna mogłaby odróżnić przebieg zaplanowany od ręcznego dla etykiety i S-06.
3. **Etykieta świeżości:** które statusy (`SUCCEEDED` czy także `PARTIAL`), które rodzaje (tylko RANGE czy tylko zaplanowane), który znacznik (`finished_at` czy `range_to`), pole w `PostPage` czy osobna trasa, tekst przy braku udanego przebiegu.
4. **Godzina i zakres:** godzina w UTC po końcu doby warszawskiej w obu porach roku; zakres `[D-1]` czy z zakładką `[D-2, D-1]` (F9); kolejność względem backupu o 02:00 UTC (przed dumpem czy po).
5. **Zachowanie crona:** ponawianie przy budzeniu (502/odmowa połączenia przez ok. 10 s), reakcja na 409 (ręczny przebieg admina w toku), kod wyjścia; czy cron czeka na wynik przebiegu, żeby e-mail Railway był sygnałem awarii (NFR `prd.md:246`), czy zostawia to S-06.
6. **Wyścig odzyskiwania przy budzeniu** (sekcja 7): sprawdzić testem; jeśli się potwierdzi — odzyskiwanie przed otwarciem portu albo 503 do jego zakończenia.
7. **Ryzyko wersji schematu:** wycofać z uzasadnieniem (ten sam obraz, pre-deploy `migrate`) czy dodać sprawdzenie (np. wersja z `flyway_schema_history` na starcie przebiegu) na okno nakładania się deployu.
8. **Limit RETRY** (F6 z S-02): czy przebieg dzienny ma ograniczać liczbę resztek do ponownej oceny.
9. **Proces UI:** czy etykieta w `FeedView` idzie przez reguły `/10x-ui` w ramach tej zmiany (macierz 7 stanów, zrzuty, rozszerzenie `mock-api.js`), czy po `feed-view-ui-hardening`.
10. **Dokumenty do aktualizacji przy planie:** `roadmap.md` (Risk S-05, Parked FR-022), `infrastructure.md:121`, `deploy-plan.md` (nowy serwis, runbook), `REPOSITORY_GUIDELINES.md` § Deployment („The daily trigger is S-05's decision”).
11. **Luki faktów:** obecny tier LLM produkcji (niezapisany); co dokładnie widzi bezpośredni wywołujący z sieci prywatnej podczas budzenia (502, odmowa połączenia czy zawieszenie — dokumentacja mówi tylko o możliwym 502); czy referencja do zapieczętowanej zmiennej innego serwisu się rozwiązuje.
