# S-05 daily-scheduled-run-freshness — plan implementacji

## Overview

Przebieg pipeline'u na produkcji uruchamia się sam co noc, a zalogowany użytkownik widzi nad listą postów datę i godzinę ostatniej udanej aktualizacji danych (US-01, FR-002, FR-030; issue #18).

- **Wyzwalacz:** codziennie o 00:30 UTC nowy serwis cron Railway `pipeline-trigger` budzi API przez sieć prywatną. Wywołuje `POST /internal/pipeline-runs/daily` z tokenem konta systemowego, a API uruchamia przebieg dni D-2 i D-1 (czas warszawski) tym samym kodem, którego używa przycisk admina.
- **Sygnał awarii:** cron czeka na wynik i kończy się kodem ≠ 0 przy `failed` lub `interrupted`. Railway wysyła wtedy e-mail.
- **Etykieta:** nad listą jest „Dane zaktualizowane: …”, czyli czas zakończenia ostatniego przebiegu RANGE ze statusem `succeeded` albo `partial`.

## Current State Analysis

Źródło: `context/changes/daily-scheduled-run-freshness/research.md`.

- **Maszyneria przebiegów z S-02 jest gotowa.** Obejmuje start (`PipelineRunService.startRange`, `PipelineRunService.java:107-112`), kolejkę w procesie z jednym wątkiem i 409 `pipeline_busy` przy aktywnym przebiegu (`:134-149`), statusy (`RunFlow.java:153-158`), `finished_at` (`RunRecorder.java:55,69,132,143`) oraz odzyskiwanie po starcie (`PipelineStartupRecovery.java:50-72`).
- **Brak wejścia bez sesji.** `POST /api/admin/pipeline-runs` wymaga sesji admina (`SecurityConfig.java:57`). `pipeline_runs.requested_by` jest `NOT NULL` z kluczem obcym do `users` (`V3__create_pipeline_and_posts.sql:14,28`), a kontroler bierze jego wartość z sesji (`PipelineRunController.java:81`). `RunView` nie ujawnia `requested_by` (`PipelineRunController.java:156-168`).
- **`@Scheduled` w API odpada.** API ma `sleepApplication: true` (`.railway/railway.ts:103`), a uśpiony serwis nie ma kontenera (`deploy-plan.md:49`). Ruch z sieci prywatnej budzi serwis, ale pierwsze żądanie „may return a 502” (docs.railway.com/deployments/serverless). Zimny start trwa ok. 9,6–10,5 s (`deploy-plan.md:118`).
- **Granica publiczne/prywatne.** Caddy przekazuje z internetu wyłącznie `/api/*` (`Caddyfile:29-34`), a resztę obsługuje fallback SPA (`:37`). API nie ma publicznej domeny.
- **Wzorzec crona** jest w `db-backup` (`railway.ts:183-207`, `tools/db-backup/backup.sh:26-46,141-147`): brak `checkSuites`, straż środowiska, limit czasu, kod wyjścia jako sygnał.
- **Wyścig przy budzeniu** (wniosek z cyklu życia Spring Boot). Odzyskiwanie działa na `ApplicationReadyEvent`, już po otwarciu portu, i zmienia wszystkie `queued`/`running` na `interrupted` bez sprawdzania właściciela (`RunRecorder.java:141-148`). Żądanie crona, które samo obudziło API, może więc zostać od razu przerwane.
- **Etykieta nie ma źródła danych.** Nie istnieje zapytanie o ostatni udany przebieg (`PipelineRunRepository.java:16-25`). `GET /api/posts` zwraca tylko `items/page/size/total` (`PostController.java:56`).
- **Testy z założeniem o tabeli `users`.** `CreateAdminCommandIT` czyści całą tabelę `users` i liczy jej wiersze (`CreateAdminCommandIT.java:73,186-190`). `MigrationsIT` sprawdza dokładną listę wersji `1`–`5` (`MigrationsIT.java:37`).
- **Logowanie.** Konto bez hasła już dziś nie zaloguje się i wygląda jak złe hasło (`AccountDetailsService.java:43-44`).

## Desired End State

- **Noc 1 po merge'u:**
  - O 00:30 UTC (± kilka minut) Railway uruchamia `pipeline-trigger`. Cron budzi API, dostaje 202 z przebiegami (RETRY, jeśli są resztki, i RANGE dla D-2…D-1) i co 30 s odpytuje ich status.
  - Po zakończeniu kończy się kodem 0, gdy wszystkie przebiegi są `succeeded`/`partial`, a kodem ≠ 0 w pozostałych przypadkach (Railway: `CRASHED` + e-mail).
  - W `/admin` widać przebieg RANGE zlecony przez konto systemowe.
- **Etykieta:** na `/` użytkownik (rola `user` lub `admin`) widzi „Dane zaktualizowane: 13.10.2026, 02:51”, czyli `finished_at` tego przebiegu w strefie warszawskiej.
  - Bez żadnego udanego przebiegu RANGE: „Dane nie były jeszcze aktualizowane.”
  - Gdy odczyt etykiety się nie uda, etykiety nie ma, a lista działa.
- **Konto systemowe:** `scheduler@system.invalid` nie może się zalogować, a token crona działa wyłącznie na `/internal/**`, niedostępnym z internetu.
- **Weryfikacja:**
  - `./mvnw -B verify`, `npm test`, `npm run build` i `tools/pipeline-trigger/test.sh` przechodzą, CI na PR jest zielone;
  - pełny łańcuch (cron → przebieg → etykieta) sprawdzony w Railway PR env, także z uśpionego API.

### Key Discoveries:

- `OriginGuardFilter` działa tylko pod `/api/**` i przepuszcza żądania bez `Origin` (`OriginGuardFilter.java:24-47`). `/internal/**` jest poza jego zakresem.
- `AuthorizationCompletenessIT` przechodzi przez wszystkie zmapowane trasy, także spoza `/api/`, i wymaga 401 bez sesji i nagłówków (`AuthorizationCompletenessIT.java:84-125`). Trasy `/internal/**` muszą więc bez tokenu dawać 401 i nie trafiają do `PUBLIC_ROUTES`.
- Kontekst bezpieczeństwa jest dziś zapisywany do sesji (`SecurityConfig.java:76-79`). Łańcuch tokenu musi być bezstanowy, inaczej każde wywołanie crona tworzyłoby wiersz `spring_session`.
- Doba przebiegu to doba Europe/Warsaw (`PipelineRunService.java:56-57,107-110`), a cron Railway liczy w UTC (docs.railway.com/cron-jobs). O 00:30 UTC w Warszawie jest 01:30 (zima) albo 02:30 (lato), czyli zawsze po północy.
- Powtórka dnia nie wywołuje LLM dla postów już zapisanych (`AssessmentSelection.java:37-44`; `PipelineAgentIT.java:182-193`), więc zakładka D-2 kosztuje tylko ok. 7 zapytań do Wykopu.
- Komenda startu Railway nie rozwija zmiennych (`railway.ts:89-90`). Logika crona musi więc siedzieć w skrypcie obrazu, jak `backup.sh`.
- Zmienna ustawiona ręcznie w Railway, a niezadeklarowana w `railway.ts`, daje w planie `Delete variable`, który bramka CI odrzuca (`deploy-plan.md`, Recovery runbook → IaC). Każdą ręcznie ustawianą zmienną deklarujemy przez `preserve()`.

## What We're NOT Doing

- Wyniesienia pipeline'u do osobnego serwisu workera („kierunek dla S-05” z planu S-02, `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:106`). Decyzja 2026-10-12: S-05 dodaje tylko wyzwalacz.
- `@Scheduled` w API i wyzwalacza z GitHub Actions.
- Logowania tokenem na trasach `/api/**`, w tym na `POST /api/admin/pipeline-runs`. Cron ma własną cienką trasę.
- Sprawdzania wersji schematu Flyway w czasie działania. Ryzyko wycofane z uzasadnieniem w fazie 5.
- Limitu ponowień RETRY w przebiegu dziennym. Zostaje bez limitu (F6 z S-02, decyzja 2026-10-12).
- E-maila przy `partial`. Taki przebieg jest widoczny w `/admin`; aktywne powiadomienia i dashboard to S-06.
- Dashboardu historii przebiegów i oznaczania przebiegu zaplanowanego w UI (S-06).
- Zmiany statusu FR-022 w roadmapie. Powód jego odłożenia jest nieaktualny, bo admin już uruchamia przebiegi na żądanie (research). To tylko sygnalizujemy, decyzja należy do użytkownika.
- Zmian w `GET /api/posts`, `PostPage` i `api/posts.ts`. Etykieta ma osobną trasę i moduł.
- Zmiany godziny backupu (`db-backup` zostaje o 02:00 UTC).

## Implementation Approach

Kolejność: model konta i wejście crona w API → odczyt świeżości → serwis cron i IaC → etykieta w SPA → dokumenty i weryfikacja końcowa. Fazy 1–3 nie zależą od zmian UI. Faza 4 zaczyna się po merge'u `feed-view-ui-hardening` do `main`; jeśli ta zmiana nie wejdzie do czasu fazy 4, etykieta powstaje na obecnym `FeedView`, a `feed-view-ui-hardening` robi rebase (decyzja 2026-10-12).

Proces (`REPOSITORY_GUIDELINES.md`, Workflow; reguły repo, nie wiersze Progress):

- gałąź `feature/daily-scheduled-run-freshness` istnieje w tym worktree na `6bebf82`. Na starcie fazy 1 `/10x-implement` robi `git fetch` i rebase na `origin/main`, jeśli ten się przesunął;
- issue roadmapy #18 (`S-05: …`) dostaje komentarz o starcie;
- jeden PR z `Refs #18`; folder zmiany idzie w pierwszym commicie.

Implementacja, weryfikacja w PR env, `/10x-impl-review` i `/10x-archive` dzieją się na tej gałęzi. Merge robi człowiek.

**Kontrakt uwierzytelniania wywołania maszynowego** (wymagany przez `REPOSITORY_GUIDELINES.md`, § Deployment, przed implementacją):

- **Transport:**
  - cron woła `http://<API_PRIVATE_DOMAIN>:<API_PORT>/internal/…` bezpośrednio przez sieć prywatną Railway, z pominięciem Caddy;
  - nagłówek `Authorization: Bearer <PIPELINE_TRIGGER_TOKEN>`;
  - `/internal/**` jest nieosiągalne z internetu, bo Caddy przekazuje tylko `/api/*`.
- **Przechowywanie tokenu:**
  - jedna wartość w zmiennej `PIPELINE_TRIGGER_TOKEN` serwisu API (na produkcji zapieczętowana, ustawia ją operator przez `--stdin`);
  - cron dostaje ją referencją IaC;
  - API porównuje token w stałym czasie;
  - brak tokenu w API oznacza, że `/internal/**` zawsze odpowiada 401 (fail closed);
  - token nigdy nie trafia do logów.
- **Sesja:** łańcuch `/internal/**` jest bezstanowy. Nie czyta ciasteczka `sr_session`, nie tworzy sesji i nie ustawia ciasteczek. Token nie działa na `/api/**`, a sesja nie działa na `/internal/**`.
- **CSRF:** nie dotyczy. Trasa nie jest wołana z przeglądarki, nie używa ciasteczek, a bez poprawnego tokenu żądanie dostaje 401.

## Critical Implementation Details

**Numer migracji a S-07.** `cli-user-onboarding` (S-07, realizowane równolegle) planuje `V6__create_activation_tokens.sql` i `MigrationsIT` z wersjami `1`–`6`. Flyway odrzuca migrację o numerze niższym od już zastosowanej. Zmiana, która wchodzi do `main` druga, przed merge'em przenumerowuje swoją migrację na następny wolny numer i poprawia listę w `MigrationsIT`; nigdy po deployu. Ten plan pisze `V<n>`, gdzie `n` to następny wolny numer na `origin/main` w chwili implementacji.

**Konto systemowe a S-07.** `reset-password` z S-07 „przywraca dowolne istniejące konto” i wydaje link aktywacyjny. Konto z `login_enabled = false` musi być przez `create-user`, `reset-password`, `lookup` i `activate` odrzucane. Kod tej odmowy dopisuje zmiana, która wchodzi druga, bo dopiero wtedy istnieją i kolumna, i `ActivationLinks`. Ograniczenie `CHECK` z fazy 1 i tak nie pozwoli zapisać hasła takiemu kontu.

**Odzyskiwanie przed otwarciem portu.** Recovery musi zakończyć się, zanim serwer WWW przyjmie pierwsze połączenie. W przeciwnym razie `POST` crona, który sam budzi API, utworzy przebieg, a chwilę później odzyskiwanie oznaczy go `interrupted`. Spring Boot otwiera konektor serwera dopiero w fazie lifecycle `WebServerStartStopLifecycle`, czyli po `afterSingletonsInstantiated`. Odzyskiwanie wykonane wcześniej (np. `SmartInitializingSingleton`) jest przed pierwszym żądaniem; `@ConditionalOnWebApplication` zostaje, więc `migrate` go nie uruchamia.

**Budzenie a cron.** Bezpośredni wywołujący z sieci prywatnej nie ma ponawiania Caddy. Przez kilkanaście sekund po starcie kontenera crona może nie działać sieć prywatna (`backup.sh:141-147`), a uśpione API odpowiada odmową połączenia albo 502/503 przez ok. 10 s. Cron ponawia start przy błędzie połączenia i przy 502/503/504 do `TRIGGER_WAKE_TIMEOUT`. 401 i inne 4xx kończą działanie od razu.

**Ręczne zmienne w PR env.** Weryfikacja w PR env ustawia ręcznie `PIPELINE_TRIGGER_TOKEN` (API) i `TRIGGER_IN_ANY_ENVIRONMENT` (cron). Obie są zadeklarowane w `railway.ts` przez `preserve()`, więc kolejny push nie zostanie odrzucony przez bramkę CI.

## Phase 1: Konto systemowe i wejście crona w API

### Overview

Konto systemowe, którym zlecany jest przebieg dzienny, bezstanowe uwierzytelnianie tokenem na `/internal/**`, trasa startu i statusu dla crona oraz odzyskiwanie przed otwarciem portu. Bez IaC i bez zmian w SPA.

### Changes Required:

#### 1. Migracja: flaga logowania i konto systemowe

**File**: `szczur-radar-api/src/main/resources/db/migration/V<n>__users_login_enabled_scheduler.sql`

**Intent**: Konto, które nie jest osobą, nie może się zalogować ani dostać hasła, a przebieg dzienny ma prawdziwy `requested_by` z kluczem obcym.

**Contract**:

- `users.login_enabled BOOLEAN NOT NULL DEFAULT true`;
- nazwane ograniczenie `CHECK (login_enabled OR password_hash IS NULL)`: konto bez logowania nie ma hasła;
- `INSERT` konta systemowego: stały UUID, `email = 'scheduler@system.invalid'`, `role = 'user'`, `password_hash NULL`, `login_enabled = false`;
- komentarz fix-forward jak w V1/V2 i zdanie, po co jest konto.

#### 2. Encja i stałe konta systemowego

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/user/User.java`, nowy `user/SystemAccount.java`

**Intent**: Encja odzwierciedla kolumnę (sprawdza to `JpaSchemaIT`), a stały UUID i e-mail mają jedno źródło w kodzie.

**Contract**:

- `User.isLoginEnabled()` z kolumny `login_enabled` (`nullable = false`);
- enum `SystemAccount { SCHEDULER(UUID id, String email) }` z wartościami z migracji (zamknięty zbiór, `lessons.md`).

#### 3. Logowanie odrzuca konto bez logowania

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/AccountDetailsService.java`

**Intent**: Konto z `login_enabled = false` zachowuje się jak złe hasło, z pełną weryfikacją argon2 i bez ujawniania, że istnieje.

**Contract**: gdy `!user.isLoginEnabled()`, zwracany jest `unusableHash`, tak samo jak dla konta bez hasła (`:43-44`). Javadoc klasy wymienia oba przypadki.

#### 4. Bezstanowy łańcuch tokenu dla `/internal/**`

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java`, nowy pakiet `szczur-radar-api/src/main/java/pl/szczurradar/api/internal/` (filtr tokenu, ustawienia tokenu)

**Intent**: Trasy dla crona przyjmują wyłącznie token, nigdy sesję; trasy `/api/**` nigdy nie przyjmują tokenu.

**Contract**:

- drugi `SecurityFilterChain` z `securityMatcher("/internal/**")`, uporządkowany przed obecnym łańcuchem i istniejący tylko w aplikacji SERVLET;
- łańcuch jest bezstanowy: sesja nie jest czytana ani tworzona, kontekst nie trafia do sesji, `NullRequestCache`, CSRF, form login i basic wyłączone;
- filtr czyta `Authorization: Bearer <token>` i porównuje go w stałym czasie z `PIPELINE_TRIGGER_TOKEN` (`MessageDigest.isEqual` na SHA-256 obu wartości). Przy zgodności principal to UUID `SystemAccount.SCHEDULER` z jedynym uprawnieniem `PIPELINE_TRIGGER`. Brak, zły token albo brak zmiennej w API oznacza brak uwierzytelnienia, a `JsonAuthenticationEntryPoint` odpowiada 401 `{"detail":"not_authenticated"}`;
- reguła `anyRequest().hasAuthority("PIPELINE_TRIGGER")` dla tego łańcucha;
- `PIPELINE_TRIGGER_TOKEN` jest opcjonalny. Gdy ustawiony, musi mieć co najmniej 32 znaki, inaczej serwer nie startuje z komunikatem `Invalid PIPELINE_TRIGGER_TOKEN: …` (wartość nie trafia do komunikatu). `migrate` i `create-admin` go nie czytają;
- Javadoc `SecurityConfig` opisuje oba łańcuchy.

#### 5. Trasy crona

**File**: nowy `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunController.java`; obsługa wyjątków przebiegów wspólna dla obu kontrolerów

**Intent**: Cron uruchamia przebieg dzienny bez treści żądania i odpytuje jego wynik. Kod startu jest ten sam co u admina.

**Contract**:

- `POST /internal/pipeline-runs/daily`, bez treści:
  - zakres `from = dziś − 2`, `to = dziś − 1`, gdzie `dziś` to data w `PipelineRunService.ZONE` z wstrzykiwanego `Clock` (testy podają stały);
  - źródła: wszystkie skonfigurowane;
  - `requestedBy = SystemAccount.SCHEDULER`;
  - 202 z listą `[{id, kind}]` w kolejności wykonania;
  - 409 `pipeline_busy`, 503 `source_not_configured`. Te same kody co trasa admina; obsługa `@ExceptionHandler` wyniesiona z `PipelineRunController.java:110-123` tak, żeby obejmowała oba kontrolery, bez zmiany odpowiedzi admina.
- `GET /internal/pipeline-runs/{id}` → 200 `{id, kind, status, finishedAt}` | 404 `not_found`.
- Wstrzykiwany `Clock` jako bean (systemowy UTC). `PipelineRunController.java:78` może z niego korzystać bez zmiany zachowania.

#### 6. Odzyskiwanie przed otwarciem portu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStartupRecovery.java`

**Intent**: Żądanie, które budzi API, nie może trafić przed odzyskiwanie (Critical Implementation Details).

**Contract**: odzyskiwanie wykonuje się, zanim serwer WWW przyjmie połączenia, np. w `SmartInitializingSingleton.afterSingletonsInstantiated()` zamiast `@EventListener(ApplicationReadyEvent)`. Zostaje `@ConditionalOnWebApplication`, jedna transakcja i obsługa błędu (log, serwer startuje dalej). Javadoc mówi, dlaczego odzyskiwanie musi być przed portem.

#### 7. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/…`

**Intent**: Każda nowa gwarancja ma test, który bez zmiany by nie przeszedł.

**Contract**:

- `MigrationsIT`: lista wersji z `V<n>`; konto systemowe istnieje z `login_enabled = false` i bez hasła; `UPDATE` ustawiający mu hasło łamie `CHECK`;
- `JpaSchemaIT` przechodzi z polem `loginEnabled`;
- `AuthIT`: logowanie `scheduler@system.invalid` dowolnym hasłem → 401 `invalid_credentials`, tak samo jak złe hasło;
- `CreateAdminCommandIT`: czyści i liczy tylko konta z `login_enabled = true`; dotychczasowe asercje bez zmian znaczenia;
- `InternalPipelineRunIT` (`MockMvcTester`, Postgres):
  - bez nagłówka i ze złym tokenem → 401;
  - API bez `PIPELINE_TRIGGER_TOKEN` → 401 także dla „dowolnego” tokenu;
  - sesja admina na `/internal/…` → 401;
  - token na `POST /api/admin/pipeline-runs` i `GET /api/posts` → 401;
  - poprawny token → 202 z przebiegiem RANGE o zakresie `[D-2 00:00, D-1 23:59:59]` Warszawy dla stałego `Clock`, w tym dzień zmiany czasu;
  - `requested_by` = konto systemowe;
  - drugi start w trakcie → 409;
  - status nieznanego id → 404;
  - żadna odpowiedź nie ma `Set-Cookie` i nie przybywa wierszy `spring_session`;
- test kolejności: odzyskiwanie kończy się przed `WebServerInitializedEvent`;
- test ustawień tokenu: brak = wyłączone; krótszy niż 32 znaki = błąd startu;
- `AuthorizationCompletenessIT` przechodzi bez zmian list (nowe trasy dają 401 bez nagłówków).

#### 8. Konto systemowe a S-07

**File**: zależnie od stanu `origin/main` w chwili implementacji

**Intent**: Komendy S-07 nie mogą wydać linku aktywacyjnego kontu systemowemu (Critical Implementation Details).

**Contract**:

- jeśli `auth/activation/ActivationLinks` jest już na `origin/main`: `createUser`, `resetPassword`, `lookup` i `activate` odrzucają konto z `login_enabled = false` (CLI kod 1 z komunikatem „konto systemowe”; HTTP 400 `activation_link_invalid`), z testem;
- jeśli nie: komentarz w issue #20 (S-07) z tym wymaganiem i odsyłaczem do tego planu.

### Success Criteria:

#### Automated Verification:

- `MigrationsIT` przechodzi z nową wersją, kontem systemowym i ograniczeniem `CHECK`
- `JpaSchemaIT`, `AuthIT` i `CreateAdminCommandIT` przechodzą z kontem systemowym w bazie
- `InternalPipelineRunIT` przechodzi: token, brak sesji, zakres D-2…D-1, 409, 404, brak `Set-Cookie`
- Test kolejności odzyskiwania i test ustawień tokenu przechodzą
- `AuthorizationCompletenessIT` przechodzi bez zmian list
- `./mvnw -B verify` przechodzi w całości
- Wymaganie dla S-07 obsłużone: test odmowy konta systemowego albo komentarz w issue #20

#### Manual Verification:

- Lokalnie (`compose.local.yaml`, jar z `PIPELINE_TRIGGER_TOKEN`): `curl -X POST -H "Authorization: Bearer …" http://localhost:8000/internal/pipeline-runs/daily` → 202, przebieg widoczny w `/admin`; ten sam `curl` przez `npm run dev` (`http://localhost:5173/internal/…`) nie trafia do API

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Odczyt świeżości dla użytkownika

### Overview

Trasa dla każdej zalogowanej roli z datą ostatniej udanej aktualizacji danych, liczoną według reguły z wywiadu.

### Changes Required:

#### 1. Zapytanie

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java`

**Intent**: Jedno zapytanie realizujące decyzję: liczy się przebieg RANGE (zaplanowany lub ręczny) ze statusem `succeeded` albo `partial`, a datą jest jego `finished_at`.

**Contract**: `Optional<Instant> findLastUpdate()` = `max(finishedAt)` po `kind = RANGE` i `status IN (SUCCEEDED, PARTIAL)`, z wartościami bindowanymi z enumów (`pipeline/CLAUDE.md`, Rules).

#### 2. Kontroler

**File**: nowy `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/FreshnessController.java`

**Intent**: Etykieta w SPA nie wymaga roli admina i nie ujawnia szczegółów przebiegów (NFR: brak wglądu w dane cudzych kont, `prd.md:250`).

**Contract**: `GET /api/freshness` → 200 `{"lastUpdatedAt": "<ISO-8601 UTC>" | null}`. Dostęp: każda zalogowana sesja (reguła `anyRequest().authenticated()`), bez wpisów w listach `AuthorizationCompletenessIT`. Javadoc mówi, co się liczy jako aktualizacja i dlaczego.

#### 3. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/FreshnessIT.java`

**Intent**: Przypadki z wywiadu jako testy.

**Contract**:

- brak przebiegów → `null`;
- RANGE `succeeded` → jego `finished_at`;
- późniejszy RANGE `partial` → `finished_at` przebiegu `partial`;
- późniejszy RANGE `failed` albo `interrupted` → bez zmiany;
- późniejszy RETRY lub POSTS `succeeded` → bez zmiany;
- późniejszy ręczny RANGE `succeeded` (`requested_by` = admin) → jego `finished_at`;
- sesja z rolą `user` → 200; bez sesji → 401.

### Success Criteria:

#### Automated Verification:

- `FreshnessIT` przechodzi ze wszystkimi przypadkami z wywiadu
- `AuthorizationCompletenessIT` przechodzi bez zmian list
- `./mvnw -B verify` przechodzi w całości

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Serwis cron `pipeline-trigger` i IaC

### Overview

Mały obraz z `curl`, który budzi API, startuje przebieg dzienny, czeka na wynik i zwraca kod wyjścia. Do tego test narzędzia w CI, deklaracja serwisu w `railway.ts` i weryfikacja w PR env.

### Changes Required:

#### 1. Narzędzie

**File**: `tools/pipeline-trigger/Dockerfile`, `tools/pipeline-trigger/trigger.sh`, `tools/pipeline-trigger/README.md`

**Intent**: Cała logika crona w testowalnym skrypcie (komenda startu Railway nie rozwija zmiennych), w tym samym stylu co `tools/db-backup/`.

**Contract**:

- **Obraz:** lekki (np. `alpine` z `curl`, `jq`, `tzdata` niepotrzebne), `ENTRYPOINT` na `trigger.sh`, użytkownik nie-root.
- **Kolejność działania `trigger.sh`:**
  1. Straż środowiska: gdy `RAILWAY_ENVIRONMENT_NAME` jest ustawione i różne od `production`, a `TRIGGER_IN_ANY_ENVIRONMENT` ≠ `true`, skrypt loguje „skipped” i kończy się kodem 0 (wzorzec `backup.sh:26-34`).
  2. Wymagane zmienne: `API_PRIVATE_DOMAIN`, `API_PORT`, `PIPELINE_TRIGGER_TOKEN`. Brak którejś daje kod 2 z nazwą zmiennej, bez wartości.
  3. Start: `POST /internal/pipeline-runs/daily`, ponawiany przy błędzie połączenia i 502/503/504 do `TRIGGER_WAKE_TIMEOUT` (domyślnie `3m`). 409 jest ponawiany co 60 s do `TRIGGER_BUSY_TIMEOUT` (domyślnie `60m`). 401, inne 4xx i 503 `source_not_configured` dają od razu kod 1 z kodem błędu z odpowiedzi.
  4. Czekanie: każdy przebieg z odpowiedzi 202 jest odpytywany (`GET /internal/pipeline-runs/{id}`) co `TRIGGER_POLL_INTERVAL` (domyślnie `30s`), aż osiągnie status końcowy albo minie `TRIGGER_RUN_TIMEOUT` (domyślnie `90m`).
  5. Wynik: kod 0, gdy każdy przebieg jest `succeeded` lub `partial`; kod 1 przy `failed`, `interrupted`, przekroczeniu czasu albo błędzie odpytywania.
- **Logi:** jedna linia na zdarzenie (start, id i rodzaj przebiegów, status końcowy z licznikami), bez tokenu i bez `set -x`.
- **Limit czasu całości:** `timeout` na całym skrypcie (wzorzec `backup.sh:36-46`), żeby zawieszony przebieg nie blokował kolejnych nocy (Railway pomija cron, gdy poprzedni trwa).
- **README:** zmienne, kody wyjścia, test lokalny, PR env.

#### 2. Test narzędzia i CI

**File**: `tools/pipeline-trigger/test.sh`, `.github/workflows/ci.yml`

**Intent**: Zachowanie crona sprawdzone bez Railway, na każdym PR (wzorzec `db-backup-test`, `ci.yml:180-191`).

**Contract**:

- `test.sh` buduje obraz i uruchamia go w sieci Dockera przeciw atrapie API (mały serwer HTTP w kontenerze, skryptowane odpowiedzi);
- scenariusze:
  - „skipped” poza produkcją (kod 0, brak żądań);
  - 202 → `succeeded` → kod 0;
  - `partial` → kod 0;
  - `failed` → kod 1;
  - RETRY `failed` i RANGE `succeeded` → kod 1;
  - odmowa połączenia przez pierwsze sekundy, potem 202 → kod 0;
  - 409, potem 202 → kod 0;
  - 401 → kod 1 bez ponowień;
  - brak zmiennej → kod 2;
  - token nieobecny w logach.
- Job CI `pipeline-trigger-test` uruchamia `tools/pipeline-trigger/test.sh`, bez filtrów ścieżek (`REPOSITORY_GUIDELINES.md`, Deployment).

#### 3. IaC

**File**: `.railway/railway.ts`

**Intent**: Serwis cron i token jako kod. Token nigdy nie jest wartością w repo.

**Contract**:

- **Serwis `pipeline-trigger`** według wzorca `db-backup` (`:183-204`):
  - `source: github(REPO, { branch })` bez `checkSuites`, `rootDirectory: "tools/pipeline-trigger"`, `watchPatterns: ["tools/pipeline-trigger/**"]`, `replicas: { [REGION]: 1 }`;
  - `deploy: { cronSchedule: "30 0 * * *", restartPolicyType: "NEVER", limitOverride: RESOURCE_LIMITS }`;
  - `env`: `API_PRIVATE_DOMAIN: api.env.RAILWAY_PRIVATE_DOMAIN`, `API_PORT: API_INTERNAL_PORT`, `PIPELINE_TRIGGER_TOKEN: api.env.PIPELINE_TRIGGER_TOKEN`, `TRIGGER_IN_ANY_ENVIRONMENT: preserve()`;
  - dopisany do `resources`.
- **API:** `PIPELINE_TRIGGER_TOKEN: preserve()` w `env`.
- **Komentarz:** po co jest serwis, dlaczego 00:30 UTC (po północy Warszawy w obu porach roku, koniec przed backupem o 02:00), straż środowiska i referencja tokenu. Zdanie „S-05 (daily run) may move it” przy `db-backup` (`:175`) dostaje rozstrzygnięcie: backup zostaje o 02:00.
- **Plan PR env:** pokazuje utworzenie serwisu i nic destrukcyjnego. Kolejny plan nie ma stałego diffu (`lessons.md`).

#### 4. Weryfikacja w PR env

**File**: brak (operacje Railway; wynik trafia do `deploy-plan.md` w fazie 5)

**Intent**: Prawdziwy łańcuch na Railway, w tym budzenie uśpionego API przez sieć prywatną.

**Contract**:

1. Na API PR env: token przez `openssl rand -hex 32 | railway variable set PIPELINE_TRIGGER_TOKEN --stdin -s szczur-radar-api -e szczur-radar-pr-<N> --skip-deploys`, potem `railway redeploy` API.
2. Na `pipeline-trigger`: `TRIGGER_IN_ANY_ENVIRONMENT=true` i tymczasowy harmonogram w panelu (`*/5 * * * *`), który kolejny push przywraca z IaC.
3. Przed wykonaniem API ma spać (≥ 10 min bez ruchu).
4. Jeśli PR env nie ma wdrożonego Postgresa, API lub web, `railway redeploy --from-source` (`deploy-plan.md`, F-01 „The PR env had no database”).
5. Po weryfikacji `TRIGGER_IN_ANY_ENVIRONMENT` wraca do braku wartości.

### Success Criteria:

#### Automated Verification:

- `tools/pipeline-trigger/test.sh` przechodzi lokalnie ze wszystkimi scenariuszami
- CI na PR zielone, w tym nowy job `pipeline-trigger-test` i `railway-config`
- `railway-config` na PR tworzy serwis `pipeline-trigger` bez zmian destrukcyjnych i bez zmian źródła istniejących serwisów, a ponowny plan jest pusty

#### Manual Verification:

- PR env: uśpione API, wykonanie crona → log „waking”/start, 202, status końcowy, kod 0 (deployment crona `SUCCESS`); w `/admin` PR env przebieg RANGE dla D-2…D-1
- PR env: na cronie tymczasowo inny `PIPELINE_TRIGGER_TOKEN` niż na API (wartość zamiast referencji; kolejny push przywraca referencję z IaC) → wykonanie crona kończy się `CRASHED` i Railway wysyła e-mail
- PR env bez `TRIGGER_IN_ANY_ENVIRONMENT`: wykonanie loguje „skipped” i kończy się `SUCCESS` bez żądań do API

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Etykieta świeżości w `FeedView`

### Overview

Etykieta nad listą postów: data ostatniej udanej aktualizacji, tekst zastępczy albo nic przy błędzie. Start po merge'u `feed-view-ui-hardening` do `main` (rebase gałęzi przed pierwszym commitem fazy). Jeśli ta zmiana nie wejdzie do tego czasu, etykieta powstaje na obecnym widoku.

### Changes Required:

#### 1. Klient API

**File**: nowy `szczur-radar-web/src/api/freshness.ts`

**Intent**: Osobny moduł, żeby nie ruszać `api/posts.ts` (zmieniają go S-03 i S-04) ani fabryk `vi.mock('../api/posts')`.

**Contract**: `fetchFreshness(): Promise<{ lastUpdatedAt: string | null }>` nad `apiFetch`, z komentarzem nagłówka wskazującym `pipeline/api/FreshnessController.java`.

#### 2. Etykieta

**File**: `szczur-radar-web/src/views/FeedView.vue`

**Intent**: Użytkownik zawsze wie, z kiedy są dane. Awaria etykiety nie psuje listy.

**Contract**:

- Odczyt raz przy wejściu w widok, równolegle z postami, nie przy każdej zmianie strony.
- `<p class="freshness">` tuż pod `<h1>`, poza regionem listy z `aria-busy`, bez roli live. Treść zależy od stanu:
  - data → `Dane zaktualizowane: <time :datetime="lastUpdatedAt">{{ formatDateTime(lastUpdatedAt) }}</time>` (`labels.ts:49-58`, Warszawa);
  - `null` → „Dane nie były jeszcze aktualizowane.”;
  - w trakcie wczytywania i przy błędzie odczytu nic nie jest renderowane.
- Styl wyłącznie z tokenów: `--font-size-sm`, odstępy `--space-*`, kolor `--text` (bez nowej roli i pary kontrastu). Plik zostaje na liście `CLEANED` (`styleContract.test.ts`), także po zaostrzeniu skanu przez `feed-view-ui-hardening`.

#### 3. Mock do zrzutów

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Bramka wizualna obejmuje etykietę.

**Contract**: `GET /api/freshness` z wartością domyślną (data), z przełącznikiem wariantów `date | none | error` opisanym w nagłówku pliku. Przy wariancie `error` odpowiada 500, a pozostałe trasy działają.

#### 4. Testy

**File**: `szczur-radar-web/src/views/FeedView.test.ts`

**Intent**: Trzy stany etykiety i jej niezależność od listy.

**Contract**:

- `vi.mock('../api/freshness')`;
- data → tekst „Dane zaktualizowane:” i `time[datetime]` z wartością z API;
- `null` → tekst zastępczy;
- błąd odczytu → brak `.freshness`, a posty się renderują;
- zmiana strony nie wywołuje `fetchFreshness` ponownie.

#### 5. Zrzuty

**File**: `context/changes/daily-scheduled-run-freshness/ui/` (zrzuty + `README.md`)

**Intent**: Dowód bramki wizualnej (`REPOSITORY_GUIDELINES.md`, § UI).

**Contract**:

- zrzuty `/` z `dev/mock-api.js` przy 1280×900 i 375×812, w obu motywach, dla `date` i `none`, plus `error` przy 1280 w jasnym;
- `README.md` z tabelą 7 stanów etykiety:
  - default: data;
  - empty: tekst zastępczy;
  - error: brak etykiety, lista działa;
  - loading: N/A, bo nic nie jest renderowane, żeby nie migał tekst zastępczy;
  - hover, focus-visible, disabled: N/A, bo etykieta nie ma kontrolek.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą, w tym trzy stany etykiety i skan `no literals`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`

#### Manual Verification:

- Zrzuty w `ui/` w obu motywach i przy 375 px; etykieta bez poziomego przewijania przy 375 px; `README.md` z tabelą 7 stanów (zrzut albo N/A z powodem)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: Dokumenty i weryfikacja końcowa w PR env

### Overview

Dokumenty fundamentów i wdrożenia odzwierciedlają decyzje S-05, a cały łańcuch (cron → przebieg → etykieta) jest sprawdzony w PR env.

### Changes Required:

#### 1. Dokument wdrożenia

**File**: `context/deployment/deploy-plan.md`

**Intent**: Nowa sekcja „S-05: codzienny przebieg (daily-scheduled-run-freshness)” przed `## Phases`.

**Contract**:

- **Opis działania:** godzina, zakres D-2…D-1, kody wyjścia i e-mail, straż środowiska.
- **Zmienne:** nazwy, token zapieczętowany na produkcji, referencja w cronie.
- **Okno bez merge'a:** ok. 00:30–01:30 UTC; reguła „nie merge'uj przy `RUNNING`” obejmuje noc.
- **Wynik weryfikacji w PR env** (faza 3 i ta faza).
- **Runbook po merge'u** (operator, śledzony w issue #18, nie w Progress):
  0. przed merge'em ustaw i zapieczętuj token na produkcji: `openssl rand -hex 32 | railway variable set PIPELINE_TRIGGER_TOKEN --stdin -s szczur-radar-api -e production --skip-deploys`;
  1. CI na `main`: `railway-config` tworzy `pipeline-trigger` i nic poza tym;
  2. `pipeline-trigger` istnieje w produkcji (`railway status --json`), a jego `PIPELINE_TRIGGER_TOKEN` rozwiązuje się (`railway variable list -s pipeline-trigger -e production`, tylko długość). Jeśli referencja do zapieczętowanej zmiennej się nie rozwiązuje, ustaw token także na cronie (zapieczętowany) i zapisz to (`lessons.md`: nowy zasób IaC sprawdzaj na produkcji);
  3. pierwsza noc albo tymczasowy harmonogram (potem z powrotem `30 0 * * *`; na produkcji nic go nie przywróci do następnego merge'a): deployment crona `SUCCESS`, przebieg w `/admin`, etykieta na `/`;
  4. komentarz w issue #18, potem zamknięcie po weryfikacji produkcji.
- **Odsyłacze:** zdanie „The daily trigger is S-05 (`daily-scheduled-run-freshness`)” w sekcji `first-assessed-wykop-feed` i Phase 6 odsyłają do nowej sekcji.

#### 2. Dokumenty fundamentów

**File**: `context/foundation/prd.md`, `context/foundation/roadmap.md`, `context/foundation/infrastructure.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`

**Intent**: Decyzje S-05 zapisane tam, gdzie czytają je kolejne zmiany.

**Contract**:

- **PRD:**
  - „Doprecyzowanie (2026-10-12, S-05)” pod FR-030: udany przebieg = RANGE (zaplanowany lub ręczny) zakończony `succeeded` albo `partial`; „zakończony błędem” = `failed`/`interrupted`; pokazywany czas = koniec przebiegu;
  - pod NFR o sygnale awarii: `failed`/`interrupted` przebiegu zaplanowanego → e-mail Railway z crona; `partial` → `/admin`, dalej S-06;
  - pod Access Control: konto systemowe z migracji, bez logowania.
- **Roadmapa S-05:**
  - Unknowns rozstrzygnięte: godzina 00:30 UTC, zakres D-2…D-1, `partial` się liczy, wyzwalacz cron + `/internal`;
  - Risk poprawiony: zapis przebiegów istnieje od S-02, a sprawdzenie schematu wycofane z uzasadnieniem.
- **`render_roadmap.swift` i PNG** zgodne z roadmapą (`REPOSITORY_GUIDELINES.md`).
- **`infrastructure.md`:**
  - `:17`: wyzwalacz rozstrzygnięty;
  - wiersz ryzyka `:121` („Cron pipeline writes to a schema from an older or newer migration”) z dopiskiem „Update (S-05)”: pipeline działa w procesie API z obrazu, którego pre-deploy `migrate` stosuje migracje; okno deployu pokrywa zasada fix-forward (migracja zgodna z poprzednią wersją kodu); sprawdzenia w kodzie nie ma.

#### 3. Reguły repo i dokumentacja modułów

**File**: `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`, `szczur-radar-api/README.md`

**Intent**: Kolejny agent wie, że `/internal/**` istnieje, jak jest chronione i skąd biorą się przebiegi nocne.

**Contract**:

- **`REPOSITORY_GUIDELINES.md`:**
  - Project Structure: `tools/pipeline-trigger/`, pakiet `internal`;
  - Coding Style: trasy `/internal/**` wyłącznie na token, poza Caddy, bez sesji; nowa trasa tam nie trafia do `PUBLIC_ROUTES`;
  - § Deployment: zdanie „The daily trigger is S-05's decision” zastąpione opisem wyzwalacza i okna bez merge'a.
- **`pipeline/CLAUDE.md`:** wejścia (`InternalPipelineRunController`, `FreshnessController`), odzyskiwanie przed otwarciem portu.
- **`szczur-radar-api/README.md`:** zmienna `PIPELINE_TRIGGER_TOKEN` i lokalne wywołanie trasy dziennej.

#### 4. Weryfikacja końcowa w PR env

**File**: brak (operacje Railway; wynik w `deploy-plan.md`)

**Intent**: Cały łańcuch na prawdziwych danych po rebase na świeży `origin/main`.

**Contract**:

- przed weryfikacją rebase na `origin/main` (S-07, `feed-view-ui-hardening`) i ponowne `verify`;
- przy konflikcie w `docs/visuals/roadmap-mvp.png` obraz jest generowany ponownie, nie scalany;
- web PR env wdrożony z głowy gałęzi (`meta.commitHash`, `deploy-plan.md`).

### Success Criteria:

#### Automated Verification:

- CI na PR zielone w całości (backend `verify`, web, smoke kontenerów, `db-backup-test`, `pipeline-trigger-test`, `railway-config`)
- `./mvnw -B verify`, `npm test`, `npm run build` przechodzą po rebase na `origin/main`

#### Manual Verification:

- PR env: wykonanie crona z uśpionego API → przebieg `succeeded`/`partial` → konto z rolą `user` widzi na `/` „Dane zaktualizowane: …” z czasem końca tego przebiegu (Warszawa)
- `deploy-plan.md` ma sekcję S-05 z wynikiem weryfikacji i runbookiem; PRD, roadmapa z PNG, `infrastructure.md`, `REPOSITORY_GUIDELINES.md`, `pipeline/CLAUDE.md` i README API zaktualizowane

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Potem `/10x-impl-review` i `/10x-archive` na tej samej gałęzi; merge robi człowiek.

---

## Testing Strategy

### Unit Tests:

- Ustawienia tokenu: brak = wyłączone, krótszy niż 32 znaki = błąd startu, wartość nigdy w komunikacie.
- Filtr tokenu: brak nagłówka, zły schemat, zły token, poprawny token (principal = konto systemowe, uprawnienie `PIPELINE_TRIGGER`).
- Vitest: trzy stany etykiety, brak ponownego odczytu przy zmianie strony.

### Integration Tests:

- `MigrationsIT`, `JpaSchemaIT`, `AuthIT`, `CreateAdminCommandIT`: konto systemowe i flaga.
- `InternalPipelineRunIT`: kontrakt `/internal/**`, rozdział token/sesja, zakres dnia przy stałym `Clock` (także dzień zmiany czasu), 409, brak sesji i ciasteczek.
- Kolejność odzyskiwania względem `WebServerInitializedEvent`.
- `FreshnessIT`: reguła etykiety na przypadkach z wywiadu.
- `AuthorizationCompletenessIT`: nowe trasy bez zmian list.
- `tools/pipeline-trigger/test.sh`: zachowanie crona przeciw atrapie API (CI).

### Manual Testing Steps:

1. Lokalnie: jar z `PIPELINE_TRIGGER_TOKEN`, `curl` na `/internal/pipeline-runs/daily`, przebieg w `/admin`, etykieta na `/`.
2. PR env: wykonanie crona z uśpionego API, kod wyjścia, przebieg, etykieta dla roli `user`.
3. PR env: zły token → `CRASHED` + e-mail; brak nadpisania środowiska → „skipped”.
4. Zrzuty etykiety w obu motywach i przy 375 px.

## Performance Considerations

- Przebieg dzienny to ok. 270 postów, 12–25 min przy 4 wątkach (`context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:586`). Noc po awarii dostawcy (RETRY bez limitu + RANGE) trwa do ok. 50 min, więc nadal kończy się przed 02:00 UTC.
- Zakładka D-2 to ok. 7 dodatkowych zapytań do Wykopu dziennie, bez wywołań LLM dla postów już zapisanych.
- Koszt LLM ok. $0,13 na noc przy tierze `default` i ok. $0,07 przy `flex` (szacunek z `deploy-plan.md:159`), daleko od limitu $10/mies. klucza OpenRouter.
- `GET /api/freshness` to `max()` po tabeli rzędu 1–2 wierszy dziennie; indeks niepotrzebny.
- Kontener crona żyje tyle, ile przebieg (do ok. 1 h). Koszt Railway za ten czas jest pomijalny przy ograniczeniu 2 vCPU / 4 GB i faktycznym zużyciu `curl`.

## Migration Notes

- Migracja dodaje kolumnę z wartością domyślną (istniejące konta: `login_enabled = true`, bez zmian zachowania) i jedno konto systemowe. Fix-forward: błąd poprawia nowa migracja.
- Dzienny `pg_dump` obejmuje konto systemowe. `restore-check.sh` i `COUNTED_TABLES` (`backup.sh:19-21`) bez zmian.
- Merge zmienia `.railway/railway.ts`, więc API wdroży się dwa razy (znane zachowanie, `deploy-plan.md`, Troubleshooting (Java)). Nie merge'ować przy przebiegu `RUNNING` ani w oknie ok. 00:30–01:30 UTC.
- Wycofanie: revert commitów gałęzi. Serwis `pipeline-trigger` zostaje w Railway, bo bramka CI odrzuca usunięcie, ale bez tokenu API odpowiada mu 401 (e-mail co noc). Do wyłączenia ręcznie w panelu (usunięcie harmonogramu).

## References

- Research: `context/changes/daily-scheduled-run-freshness/research.md`
- PRD: US-01 (`prd.md:54-65`), FR-002 (`:85`), FR-021/FR-022 (`:182-186`), FR-030 (`:207-213`), NFR (`:246-252`)
- Roadmapa: S-05 (`roadmap.md:208-222`)
- Wzorzec crona: `.railway/railway.ts:166-204`, `tools/db-backup/backup.sh`, `tools/db-backup/test.sh`, `ci.yml:180-191`
- Start przebiegu: `PipelineRunService.java:107-149`, `PipelineRunController.java:76-143`
- Bezpieczeństwo: `SecurityConfig.java:47-80`, `OriginGuardFilter.java:24-47`, `AuthorizationCompletenessIT.java:51-125`
- Zmiany równoległe: `feed-view-ui-hardening` (plan w jej worktree, faza 4 tej zmiany po niej), `cli-user-onboarding` (S-07, plan w jej worktree; numer migracji i konto systemowe)
- Lekcje: `context/foundation/lessons.md` (FIRE_ONCE, nowy zasób IaC sprawdzaj na produkcji, stałe diffy, enumy, jedna gałąź do archiwizacji)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Konto systemowe i wejście crona w API

#### Automated

- [x] 1.1 MigrationsIT z nową wersją, kontem systemowym i CHECK — 6e9d4e2
- [x] 1.2 JpaSchemaIT, AuthIT i CreateAdminCommandIT z kontem systemowym — 6e9d4e2
- [x] 1.3 InternalPipelineRunIT — 6e9d4e2
- [x] 1.4 Test kolejności odzyskiwania i test ustawień tokenu — 6e9d4e2
- [x] 1.5 AuthorizationCompletenessIT bez zmian list — 6e9d4e2
- [x] 1.6 ./mvnw -B verify — 6e9d4e2
- [x] 1.7 Wymaganie dla S-07 obsłużone (test albo komentarz w issue #20) — 6e9d4e2

#### Manual

- [x] 1.8 Lokalny curl na /internal/pipeline-runs/daily; ścieżka przez Vite nie trafia do API — 6e9d4e2

### Phase 2: Odczyt świeżości dla użytkownika

#### Automated

- [x] 2.1 FreshnessIT z przypadkami z wywiadu
- [x] 2.2 AuthorizationCompletenessIT bez zmian list
- [x] 2.3 ./mvnw -B verify

### Phase 3: Serwis cron pipeline-trigger i IaC

#### Automated

- [ ] 3.1 tools/pipeline-trigger/test.sh ze wszystkimi scenariuszami
- [ ] 3.2 CI na PR zielone z pipeline-trigger-test i railway-config
- [ ] 3.3 railway-config tworzy pipeline-trigger bez zmian destrukcyjnych, ponowny plan pusty

#### Manual

- [ ] 3.4 PR env: cron budzi uśpione API, przebieg D-2…D-1, kod 0
- [ ] 3.5 PR env: zły token kończy się CRASHED i e-mailem
- [ ] 3.6 PR env bez nadpisania środowiska: skipped bez żądań

### Phase 4: Etykieta świeżości w FeedView

#### Automated

- [ ] 4.1 npm test z trzema stanami etykiety i skanem no literals
- [ ] 4.2 npm run build

#### Manual

- [ ] 4.3 Zrzuty etykiety w obu motywach i przy 375 px, tabela 7 stanów

### Phase 5: Dokumenty i weryfikacja końcowa w PR env

#### Automated

- [ ] 5.1 CI na PR zielone w całości
- [ ] 5.2 verify, npm test i npm run build po rebase na origin/main

#### Manual

- [ ] 5.3 PR env: cron → przebieg → etykieta dla roli user
- [ ] 5.4 Dokumenty i PNG roadmapy zaktualizowane
