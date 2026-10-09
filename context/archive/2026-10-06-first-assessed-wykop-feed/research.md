---
date: 2026-10-06T16:28:40+0200
researcher: Claude (Opus 5.5) for kwmacioszek
git_commit: eb1ab546cccaf1f4f013ac6abc4145f6aeed2ec8
branch: main
repository: szczur-radar
topic: "S-02 first-assessed-wykop-feed — co baza kodu już daje dla pobrania, oceny, zapisu i listy postów z Wykopu"
tags: [research, codebase, wykop, assessor, llm, persistence, api, web, cli, raw-payload]
status: complete
last_updated: 2026-10-06
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Dopisano sekcję External Research (Wykop API, kompresja RAW w Postgresie vs Railway Buckets, bezpieczeństwo wątkowe klientów LLM); Open Questions 1, 6 i 10 zaktualizowane."
---

# Research: S-02 first-assessed-wykop-feed

**Date**: 2026-10-06T16:28:40+0200
**Researcher**: Claude (Opus 5.5) for kwmacioszek
**Git Commit**: eb1ab546cccaf1f4f013ac6abc4145f6aeed2ec8
**Branch**: main
**Repository**: szczur-radar

Ścieżki kodu backendu poniżej skracam: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`, `web/…` = `szczur-radar-web/src/…`.

## Research Question

Slice S-02 z `context/foundation/roadmap.md:159-172`: zalogowany użytkownik widzi chronologiczną, stronicowaną listę postów z Wykopu (#gielda) z zadanego zakresu dat, bez duplikatów, każdy z oceną LLM zgodną z kontraktem V2; przebieg uruchamia ręcznie operator. PRD refs: US-01, FR-001, FR-003–FR-007, NFR (surowy payload oddzielnie, bezstratnie, kompresowalnie), NFR (nowe źródło = tylko pozyskanie + mapowanie).

Pytania: co z tego już istnieje w kodzie i dokumentach, jakie wzorce trzeba powtórzyć, jakie decyzje zostały podjęte wcześniej i co pozostaje otwarte dla `/10x-plan`.

## Summary

- **Klocki pipeline'u istnieją w `main`, ale żaden produkcyjny przepływ ich nie łączy.** Klient i repozytorium Wykopu (`api/sources/wykop/WykopApiClient.java:53`, `WykopRepository.java:94`), mapper do `AssessmentInputV1` (`WykopMapper.java:40`) i `Assessor` (`api/agents/assessor/Assessor.java:33`) są gotowe. W ścieżkach `src/main` sprawdzonych przez badanie używa ich tylko kod testowy i `measurement/`. Klasy Wykopu nie są beanami Springa i nie mają konfiguracji (`WYKOP_KEY`/`WYKOP_SECRET` występują wyłącznie w `test/measurement/WykopGoldSampler.java:84`).
- **Surowy payload jest dziś gubiony.** `WykopRepository` zamienia każdy element na `WykopItem` i odrzuca `JsonNode` (`WykopRepository.java:129`), choć javadoc `WykopItem.java:11` mówi, że „raw element is kept apart by the pipeline”. Spełnienie NFR o RAW (`prd.md:252`) wymaga zmiany repozytorium (zwracanie surowego elementu obok `WykopItem`).
- **Ocena jest gotowa do zapisu bez interpretacji.** `AssessmentResult` (status ok/failed, output, failureClass, violations, provenance, rawText) i `Provenance` (źródło, provider, model, promptVersion, contractVersion, tokeny, czas, próby) pokrywają listę pól, które kontrakt każe przechowywać (`contract-v2.md:423-436`), poza polami po stronie backendu: source/source_item_id, RAW reference, content hash, processing run.
- **Persystencji, paginacji, executora i komendy przebiegu nie ma.** Istnieją tylko migracje V1 (users) i V2 (spring_session). Nie ma jsonb, kompresji, `Pageable` ani executora w `src/main`. Wzorce do powtórzenia są jednoznaczne: encja + `JpaRepository` + migracja Flyway + `JpaSchemaIT`/`MigrationsIT`, kontroler z rekordami DTO, deny-by-default security, komenda CLI przez `CommandType` + `Command`.
- **Frontend ma tylko logowanie i placeholder `HomeView`** pod `/` (`web/router/index.ts:26`). Klient API, guard sesji i wzorzec testów są gotowe do rozbudowy o widok listy.
- **Otwarte decyzje produktowe/infrastrukturalne** (szczegóły w Open Questions): backend magazynu RAW, mechanizm ręcznego uruchomienia (CLI vs endpoint, co koliduje z planem S-05), miejsce weryfikacji z prawdziwym Wykopem/LLM przed F-01, prezentacja ocen `failed` i `prefilter` na liście, kształt rekordu przebiegu pod S-05/S-06. Do tego 7 zaległych poprawek F7–F8f oznaczonych „Before S-02”.

## Detailed Findings

### 1. Pozyskanie z Wykopu (`api/sources/wykop/`)

- `WykopApiClient(key, secret)` (`WykopApiClient.java:53`): baza `https://wykop.pl/api/v3/`, `JsonNode get(path, query)` (`:72`). Uwierzytelnia się przez `POST auth`, trzyma token i ponawia raz po 401. Według raportu jest bezpieczny wątkowo i maskuje sekrety w `toString`. Innych ponowień nie ma.
- `WykopRepository.itemsAddedBetween(tag, LocalDateTime from, LocalDateTime to)` (`WykopRepository.java:94`) i `itemsOfDay(tag, LocalDate)` (`:83`) robią to, czego wymaga FR-001 (zakres dat/godzin, czas warszawski):
  - pytają `search/entries`, potem `search/links` z parametrami `tags`, `date_from`, `date_to`, `sort=newest`, `limit`, `page` (`:113-118`);
  - robią przerwę między stronami (`:110`; 300 ms według `implementation-notes.md` N9);
  - kończą stronicowanie na `pagination.total`; strona krótsza niż `total` albo przekroczenie `MAX_PAGES` rzuca `WykopApiException` (`:145`, `:152`);
  - deduplikują w obrębie jednego pobrania po kluczu `resource:id` (`:137`, `:156-157`);
  - element o nieoczekiwanym kształcie jest liczony jako `skipped` zamiast przerywać pobranie (`:129-134`).
- `WykopItem` (`WykopItem.java:25`) trzyma resource, id, createdAt, content, title, description, tags, author.username, slug i flagi mediów. Celowo pomija głosy i komentarze (`:11`). `webUrl()` buduje link do oryginału, potrzebny w S-03 (FR-010).
- `WykopMapper.map(WykopItem, channel)` (`WykopMapper.java:40`):
  - `entry` → `post` (title=null, body=content);
  - `link` → `link` (title, body=description);
  - `created_at` interpretuje jako Europe/Warsaw → `OffsetDateTime` (`:72`);
  - rzuca `UnmappableItemException` dla innych typów, braku tekstu i złej daty.
  - Wejście nie zawiera id źródła ani autora, zgodnie z `contract-v2.md:77`, więc klucz zapisu musi pochodzić z `WykopItem` (resource + id).
- Fixtures (`szczur-radar-api/src/test/resources/fixtures/wykop/`): `entry.json`, `link.json`, `search-entries-page.json` (12 elementów, `total:262`), `search-links-page.json` (4 linki). Są anonimizowane (nicki, URL-e, przenumerowane id). Testy klienta i repozytorium używają lokalnego serwera HTTP `test/sources/wykop/LocalWykopServer`.
- Wiedza o API (`context/archive/2026-09-27-assessment-contract-harness/implementation-notes.md`, N9):
  - `search/all` nie nadaje się do użytku (brak stron);
  - typowy dzień 2026-10-01 dał 262 wpisy i 4 linki;
  - użytkownik wybrał wpisy i linki bez komentarzy (PRD §Non-Goals).
  - Limitów ani warunków API Wykopu nie opisuje żaden sprawdzony dokument (`roadmap.md:169` — otwarte unknown).

### 2. Ocena LLM (`api/agents/assessor/`, `api/llm/`)

- `Assessor` to zwykły `@Service` (`Assessor.java:33`) z leniwie wstrzykiwanym `LlmClient` (`:45`). API:
  - `assess(AssessmentInputV1)` ocenia jeden wpis; błąd dostawcy zwraca jako `FAILED`, nie rzuca.
  - `prepare(input)` (`:56`) zwraca `Decided` (wynik prefiltra) albo `Prepared(PreparedRequest)`.
  - `evaluate(input, prepared, response, duration, assessedAt)` (`:73`) ocenia odpowiedź.
  - Javadoc `:30-31` wprost przewiduje tryb wsadowy S-02 na bazie `prepare`/`evaluate`.
- `AssessorAgent` (Embabel `@Agent`) tylko deleguje do `assessor.assess`. Według raportu (z odwołaniem do CLAUDE.md assessora) oceny nie przechodzą przez `createObject` Embabela, więc ścieżka wywołania w S-02 to `Assessor` + `LlmClient`.
- Prefiltr (`Prefilter.java:35`): po usunięciu URL-i, tagów i wzmianek wymaga co najmniej 3 punktów kodowych. Inaczej zwraca wynik `ok` z `relevance=unclear`, flagą `insufficient_content` i `Provenance.prefilter` (bez modelu i tokenów). Według `contract-v2.md:469` dotyczy to ok. 8% wpisów #gielda.
- Walidacja: parser, potem reguły D1–D15 (`RuleBasedValidator.contractV2()`, `Assessor.java:42`). Naruszenia wracają w wyniku. Nic nie jest naprawiane ani ponawiane poza transportem (`contract-v2.md:333-372`).
- `PROMPT_VERSION = "v2.5"` (`prompt/AssessmentPrompt.java:39`).
- `LlmClient` to bean `@Bean @Lazy` (`api/llm/LlmConfiguration.java:18-19`), z dostawcami Gemini, DeepSeek i OpenRouter. Ponowienia transportu istnieją wyłącznie w `api/llm/TransportRetry.java`: 429, 5xx, timeout, `Retry-After` do 60 s, przejście z flex na default po 3×429. Klient nie ma własnego executora. Bezpieczeństwa wątkowego klientów LLM badanie nie zweryfikowało, co jest istotne przy równoległej ocenie.
- Model produkcyjny: `openrouter` / `openai/gpt-6-luna` / `flex`. PR env dostaje ustawienia CI bez tieru, reasoningu i timeoutu (`REPOSITORY_GUIDELINES.md` § Deployment; `deploy-plan.md:143-157` według raportu).

### 3. Persystencja (wzorce)

- Encja `api/user/User.java:19-72`:
  - `@Id @GeneratedValue(strategy=UUID)`;
  - `Instant createdAt` z `@CreationTimestamp`;
  - chroniony konstruktor bezargumentowy, same gettery.
- Repozytorium `UserRepository extends JpaRepository<User, UUID>` z zapytaniami pochodnymi.
- Enumy zapisuje się jako TEXT z `CHECK` w SQL i `AttributeConverter(autoApply=true)` (`api/user/RoleConverter.java`), bez `@Enumerated`.
- Migracje: `V1__create_users.sql` stosuje `UUID DEFAULT gen_random_uuid()`, `TIMESTAMPTZ DEFAULT now()`, TEXT i nazwane ograniczenia (`users_pkey`, `uq_*`, `ck_*`). `V2__create_spring_session.sql` jest jedynym miejscem z BYTEA. jsonb, kompresja i bulk upsert nie występują w sprawdzonym `src/main`.
- Konfiguracja (`application.properties`):
  - `ddl-auto=none`;
  - Flyway wyłączony dla serwera (`:14`), migracje uruchamia wyłącznie komenda `migrate`;
  - `open-in-view=false`.
- Testy schematu:
  - `test/JpaSchemaIT.java` waliduje encje przez `ddl-auto=validate` na zmigrowanym Postgres 18. Nowe encje trzeba tam dopisać.
  - `test/MigrationsIT.java:36` ma `containsExactly("1", "2")`, więc **V3+ wymaga aktualizacji tego asercji**.
- Wzorzec blokady przed równoległym przebiegiem: `api/cli/AdminBootstrap.java` używa `TransactionTemplate` z `pg_advisory_xact_lock`.
- Granica RAW (`context/foundation/tech-stack-api.md:45-49`):
  - RAW to niezmienny artefakt za dedykowanym interfejsem magazynu, a model aplikacji trzyma tylko stabilną referencję;
  - przechowywanie musi być bezstratne i przezroczyście kompresowalne, z wymienialnym backendem;
  - preferowany kandydat to Railway Buckets; tymczasowa implementacja w bazie jest dopuszczalna, jeśli RAW jest fizycznie/logicznie oddzielony od głównej ścieżki zapytań.
  - Lekcja „Oddziel RAW od danych operacyjnych” (`context/foundation/lessons.md`) mówi to samo.

### 4. REST i bezpieczeństwo

- Kontrolery są package-private `@RestController` z `@RequestMapping("/api/…")` i wstrzykiwaniem przez konstruktor (`api/health/HealthController.java:17-19`, `api/auth/AuthController.java:42-44`).
- DTO to package-private rekordy z fabryką `of(entity)` (`api/auth/UserResponse.java:9-14`). Paginacji nie ma nigdzie.
- Format błędów `{"detail":"<code>"}` daje `ApiError` + enum `ErrorCode` (`api/config/ApiError.java:15-28`, `ErrorCode.java:13-51`). `ApiExceptionHandler` nie obsługuje błędnych parametrów zapytania (type mismatch, brak parametru), więc parametry stronicowania potrzebują handlera, inaczej odpowiedź będzie miała domyślny format Springa.
- Jackson 3 (Boot 4.0.8) z `fail-on-unknown-properties=true`. Nazwy pól są camelCase, bez strategii nazewnictwa.
- `SecurityConfig.java:57-58`: `/api/admin/**` wymaga roli ADMIN, `anyRequest().authenticated()`.
  - Nowe GET (np. `/api/posts`) nie wymaga zmian w konfiguracji.
  - `test/auth/AuthorizationCompletenessIT.java` automatycznie wykrywa każdą trasę i wymaga 401 bez sesji. Trasy publiczne trafiają do `PUBLIC_ROUTES` (`:51`), a administracyjne do `ADMIN_ROUTES` (`:58`, dziś puste).
  - `OriginGuardFilter` dotyczy tylko metod modyfikujących.
- W testach z zalogowanym użytkownikiem używa się helperów `login`/`loginCookie`/`sessionCookie` z `test/auth/AuthIT.java:411-425` albo `user(uuid).roles("USER")` jak w `AdminRoleIT`.

### 5. CLI i uruchamianie

- Kontrakt CLI:
  - `Command { CommandType type(); int run(String[] args); }`;
  - nazwy w enumie `CommandType` (`migrate`, `create-admin`);
  - rejestracja na sztywno w `CommandLine.standard()` (`api/cli/CommandLine.java:33`);
  - kody wyjścia 0/1/2;
  - `CommandLineTest.standardRegistersEveryCommandType` pilnuje, że każdy `CommandType` jest zarejestrowany.
- Kontekst bez warstwy web zapewnia `SzczurRadarApiApplication.commandApplication()` (`WebApplicationType.NONE`). Ładuje tę samą konfigurację, łącznie z kontrolą LLM.
- Wzorzec `create-admin` (`api/cli/CreateAdminCommand.java`):
  - walidacja argumentów i `PG*` przed startem kontekstu;
  - logika w beanie `@Service`;
  - parser argumentów pisany ręcznie, bo biblioteki do argumentów nie ma.
- Obraz Dockera: `ENTRYPOINT` uruchamia jar (`szczur-radar-api/Dockerfile:25`), argumenty są doklejane, np. `docker run <image> run-pipeline …`. W `src/main` nie ma executorów, `@Async` ani `@Scheduled`. Reguła repo każe przekazywać własny executor (`availableProcessors()` może zwrócić 1).

### 6. Frontend (`szczur-radar-web/`)

- Routing:
  - trasy `/login` (publiczna), `/` → `HomeView`, catch-all → `/` (`web/router/index.ts:25-27`);
  - guard `beforeEach` z `ensureSession()`;
  - 401 przekierowuje na logowanie przez `onUnauthorized`.
- `web/api/client.ts`: `apiFetch<T>()` z `credentials: 'same-origin'`, klasą `ApiError(status, detail)` i opcją `handleUnauthorized`.
- `HomeView.vue` to placeholder (topbar z e-mailem i wylogowaniem). Style to zwykły CSS ze zmiennymi i trybem ciemnym (`web/style.css`), bez bibliotek UI i bez store'a.
- Testy (Vitest + `@vue/test-utils`) mockują `fetch` przez `vi.stubGlobal` (`web/api/client.test.ts`), a w widokach `vi.mock` modułów API (`web/views/LoginView.test.ts`).

## Code References

- `api/sources/wykop/WykopRepository.java:94` — pobranie zakresu dat (entries, potem links), deduplikacja `resource:id`
- `api/sources/wykop/WykopRepository.java:129` — `treeToValue` gubi surowy `JsonNode`
- `api/sources/wykop/WykopItem.java:11` — javadoc zakładający, że pipeline trzyma surowy element osobno
- `api/sources/wykop/WykopMapper.java:40` — `WykopItem` → `AssessmentInputV1`
- `api/agents/assessor/Assessor.java:30-31,56,73` — `prepare`/`evaluate` jako dwie połowy pod tryb wsadowy S-02
- `api/agents/assessor/Provenance.java:43` — pola pochodzenia oceny
- `api/agents/assessor/prompt/AssessmentPrompt.java:39` — `PROMPT_VERSION = "v2.5"`
- `api/llm/LlmConfiguration.java:18-19` — leniwy bean `LlmClient`
- `api/config/SecurityConfig.java:57-58` — deny-by-default
- `api/cli/CommandLine.java:33` — rejestracja komend
- `test/MigrationsIT.java:36` — asercja historii Flyway do aktualizacji
- `test/auth/AuthorizationCompletenessIT.java:51,58` — `PUBLIC_ROUTES`, `ADMIN_ROUTES`
- `test/measurement/WykopGoldSampler.java:84` — jedyne użycie `WYKOP_KEY`/`WYKOP_SECRET`
- `szczur-radar-web/src/router/index.ts:26` — `/` → `HomeView` (placeholder)

## Architecture Insights

- **Warstwy rozdzielone tak, jak wymaga NFR o nowym źródle.** `sources/wykop` (pozyskanie + mapowanie) nie zna oceny ani zapisu. `agents/assessor` nie zna źródła. `llm` to infrastruktura dostawców. Według raportu `api/sources/CLAUDE.md` wprost wyklucza ze źródła harmonogram, deduplikację i persystencję. S-02 dokłada więc nową warstwę orkiestracji (przebieg: pobranie → dedup względem bazy → RAW → ocena → zapis) i persystencji, bez wpychania jej do `sources` ani `assessor`.
- **Deduplikacja ma dwa poziomy.** W obrębie pobrania robi ją `WykopRepository`. Między przebiegami musi to robić baza: unikalny klucz (source, resource, source_item_id), a post już oceniony nie jest oceniany ponownie (FR-003, `prd.md:89`). Klucz musi zawierać `resource`, bo repozytorium traktuje `entry` i `link` jako osobne przestrzenie id (`WykopRepository.java:156-157`).
- **Ocena jako zapis bez interpretacji.** Kontrakt każe zapisać `output` modelu bez zmian obok `provenance` (`contract-v2.md:79-108`). Serializacja `AssessmentOutputV2` idzie przez `ContractJson.MAPPER`, nie przez mapper Boota (według raportu z CLAUDE.md assessora). F8a (brak `@JsonValue` na enumach dostawcy) wpływa na spójność zapisanych wartości.
- **Jeden obraz, wiele komend.** `migrate` już tak działa. Komenda przebiegu wpasowuje się w ten sam mechanizm bez zmian w Dockerfile.

## Historical Context (from prior changes)

- `context/archive/2026-09-27-assessment-contract-harness/plan.md:43,47` — persystencja, migracje, dedup, RAW, UI, trasy API, harmonogram, orkiestracja wielu postów i batching celowo odłożone do S-02 (wsparte).
- `context/archive/2026-09-27-assessment-contract-harness/implementation-notes.md` N9 — `search/entries` + `search/links` zamiast `/tags/gielda/stream` z pilotażu. Wsparte kodem (`WykopRepository.java:113-118`).
- `context/archive/2026-09-27-assessment-contract-harness/implementation-notes.md:256` (N19) — prompt v2.5 nie ma wyjątku §6.6/§7.1.7; poprawić „przy następnej zmianie promptu (S-02 lub hardening)” razem z ponownym nagraniem. Status: otwarte.
- `context/archive/2026-09-27-assessment-contract-harness/follow-ups/review-fixes.md:30-47` — punkty F7, F8a–F8f „Before S-02 (results persisted, assessment unattended)”, śledzone w issue S-02 (#14), wszystkie niezaznaczone:
  - F7: provider/model `null` przy nieudanym wywołaniu;
  - F8a: enumy bez `@JsonValue`;
  - F8b: brak górnej granicy `LLM_TIMEOUT`;
  - F8c: pusta odpowiedź Gemini traktowana jako sukces;
  - F8d: niezamaskowane sekrety w błędach Wykopu;
  - F8e: schemat czytany przy każdym wywołaniu;
  - F8f: niewidoczne znaki poza §10.
- `context/archive/2026-10-04-api-docker-image/` — według raportu jeden obraz z wieloma komendami, a pipeline jako osobna komenda/serwis cron. Follow-up F4: ostrzeżenie Hikari „connection closed” po bezczynności, istotne przy długim przebiegu.
- `context/deployment/deploy-plan.md:270-272` — pipeline jako osobny serwis cron Railway, który kończy się po przebiegu; prawdziwe wywołania Wykop/LLM „only in production after credentials, spend limits, failure visibility, and a dry run… Keep them out of PR previews”.
- `context/foundation/roadmap.md:198-212` (S-05 unknowns) — nowszy kierunek: użytkownik nie chce, by cron startował kontener z obrazem API. Kandydat to cron z `curl` robiący `POST` do wewnętrznego endpointu API (202, przebieg w tle), co zmienia regułę „pipeline nie zależy od cyklu życia API”. **Sprzeczne** z `deploy-plan.md:270` i `REPOSITORY_GUIDELINES.md` § Deployment. Rozstrzygnięcie zapisano dla planu S-05, ale ma ono wpływ na kształt ręcznego uruchomienia w S-02.

## External Research (2026-10-06)

Źródła zewnętrzne: exa.ai (web), dokumentacja PostgreSQL 18, Railway docs (`search-docs`). Uzupełnia pytania 1, 6 i 10 z Open Questions.

### Wykop API v3 — limity i warunki

- Publiczne strony `https://dev.wykop.pl/` i `https://wykop.pl/dla-programistow` opisują API ogólnie (REST, JSON, zasoby „publicznie dostępne w serwisie”). **Nie podają limitów zapytań, kwot ani warunków przechowywania danych.** Szczegółowa dokumentacja (Swagger `doc.wykop.pl`) jest dostępna dopiero po uzyskaniu dostępu do API.
- Regulamin serwisu (`https://wykop.pl/regulamin`) dotyczy kont użytkowników i nie zawiera osobnych zasad API. Przeszukanie nie znalazło publicznie dostępnego „regulaminu API”. Jeśli istnieją warunki zaakceptowane przy rejestracji aplikacji, zna je tylko właściciel klucza.
- Klienci open source (pywykop3, wykop-sdk-reloaded, xxdeepfriedxx/wykop) obsługują ten sam przepływ `auth` (key + secret → JWT) co `WykopApiClient`. Do odczytu danych publicznych nie potrzebują logowania użytkownika (`connect`). Żaden z przejrzanych klientów nie implementuje limitera ani nie dokumentuje konkretnego limitu.
- **Wniosek:** limit pozostaje nieznany. Skala S-02 jest mała: typowy dzień to ok. 6 + 1 zapytań stronicowania (262 wpisy / 50 na stronę + linki, `implementation-notes.md` N9) i jedno `auth`, z przerwą 300 ms między stronami. Ryzyko przekroczenia limitu przy ręcznym przebiegu jest niskie. **Do potwierdzenia przez użytkownika:** warunki zaakceptowane przy rejestracji klucza na dev.wykop.pl (przechowywanie treści, cel niekomercyjny, ewentualny limit). Obsługa 429 w `WykopApiClient` nie istnieje (ponawia tylko 401), więc 429 przerwie przebieg wyjątkiem.

### Przechowywanie RAW: Postgres vs Railway Buckets

- **`jsonb` nie jest bezstratny.** Dokumentacja PostgreSQL 18 (§8.14 JSON Types) mówi, że `jsonb` „does not preserve white space, does not preserve the order of object keys, and does not keep duplicate object keys” i odrzuca `\u0000`. Typ `json` przechowuje dokładną kopię tekstu. Dla wymogu „bezstratnie” odpada więc `jsonb`; zostają `bytea` (dokładne bajty), `text`/`json` (dokładny tekst) albo obiekt w buckecie.
- **Bezstratność a Jackson.** `WykopRepository` dostaje stronę jako `JsonNode`. Ponowna serializacja elementu przez Jacksona daje JSON semantycznie równy, ale nie zawsze identyczny bajtowo: zmieniają się escape'y, białe znaki i zapis liczb. Bajtowo dokładny jest tylko pełny body odpowiedzi HTTP (strona). Opcje do decyzji w planie:
  - (a) RAW = dokładne bajty całej strony + wskaźnik elementu (indeks w `data`);
  - (b) RAW = element zserializowany przez Jacksona, z jawnie przyjętą definicją „bezstratnie = bez utraty pól i wartości”.
- **Kompresja TOAST.**
  - PostgreSQL 18: `default_toast_compression` domyślnie `pglz`; `lz4` jest dostępne, jeśli serwer skompilowano z `--with-lz4` (dokumentacja §19.11.1). Algorytm ustawia się per kolumna: `COMPRESSION lz4` w `CREATE TABLE` albo `ALTER TABLE … ALTER COLUMN … SET COMPRESSION lz4`.
  - Pakiety społeczności PostgreSQL mają `lz4` włączone (dbi-services). Czy obraz Postgresa na Railway je ma, nie sprawdzono; weryfikacja to `SELECT pg_column_compression(col)` po zapisie.
  - **Pułapka:** TOAST próbuje kompresji dopiero, gdy krotka przekracza ok. 2000 B (`TOAST_TUPLE_THRESHOLD`, credativ i dbi-services). Fixture `entry.json` ma 1,4 KB, więc pojedyncze wpisy mogą nie być kompresowane wcale. Czy obniżenie `toast_tuple_target` (min. 128) to zmienia, źródła podają niejednoznacznie; trzeba to sprawdzić testem na Postgres 18.
  - Pewną kompresję małych elementów daje kompresja w aplikacji (np. gzip do `bytea`) albo zapis całych stron (ok. 35 KB na stronę według fixture `search-entries-page.json`), które TOAST kompresuje.
  - Typowe współczynniki dla JSON: pglz ok. 2,2×, lz4 ok. 2,1× (tigerdata); lz4 jest szybsze.
- **Railway Buckets** (Railway docs: Storage Buckets Billing):
  - $0,015/GB-miesiąc, operacje S3 i egress bucketu bezpłatne;
  - bucket **nie jest w sieci prywatnej**, a upload z serwisu Railway liczy się jako egress serwisu;
  - wolumen (Postgres) kosztuje $0,15/GB-miesiąc (Railway docs: Volumes).
- **Skala kosztu:** szacunek z `shape-notes.md` (według raportu) to ok. 4 KB na wpis i ok. 0,56 GB/rok bez kompresji. W Postgresie to ok. $0,08/mies. po roku, w buckecie ok. $0,01/mies. Różnica jest pomijalna na etapie MVP. Bucket dokłada za to drugi system: klucze S3, osobny bucket na PR env, egress przy uploadzie i brak transakcji wspólnej z zapisem posta.
- **Wniosek:** dla S-02 najprostsza zgodna z `tech-stack-api.md:49` opcja to osobna tabela RAW w Postgresie (`bytea`, kompresja w aplikacji albo strony + `lz4`), schowana za interfejsem magazynu. Przeniesienie do bucketu zostaje możliwe bez zmiany modelu domeny. Decyzja należy do użytkownika (Open Question 1).

### Bezpieczeństwo wątkowe klientów LLM (weryfikacja w kodzie)

- `GeminiClient`, `DeepSeekClient` i `OpenRouterClient` mają wyłącznie pola `final` (`api/llm/GeminiClient.java:27-33`, `DeepSeekClient.java:27-35`, `OpenRouterClient.java:30-42`).
- Jedyny stan per wywołanie, `DeepSeekRawResponse`, trzyma dane w `ThreadLocal` i jest wprost zaprojektowany pod „a call and its attempts run on the caller's thread” (`api/llm/DeepSeekRawResponse.java:19-20,41`).
- Pod spodem są `HttpClient`/`RestClient`/OkHttp (`LlmClientFactory.java:89-116`), które z założenia obsługują wiele wątków.
- **Wniosek:** w sprawdzonych klasach nie ma współdzielonego, mutowalnego stanu, więc równoległe wywołania na jednym `LlmClient` są prawdopodobnie bezpieczne. Nie są jednak przetestowane: `measurement/` nie używa executora (grep `Executor|parallel|newFixedThreadPool` bez wyników). Jeśli plan wybierze równoległość, potrzebny jest test współbieżny. Sekwencyjny przebieg ok. 270 postów przy medianie kilku sekund na ocenę trwa kilkanaście–kilkadziesiąt minut, co dla ręcznego przebiegu może być akceptowalne.

## Related Research

- `context/archive/2026-09-27-assessment-contract-harness/research.md` — kontrakt oceny, pilotaż, wolumen (~406 postów/dzień według `shape-notes.md`).
- `context/archive/2026-10-01-java-api-rewrite/` i `context/archive/2026-10-04-api-docker-image/research.md` — wzorce backendu w Javie i obrazu.

## Open Questions

Decyzje dla `/10x-plan` (nie blokują badania; większość to wybory użytkownika):

1. **Backend magazynu RAW.** Oddzielna tabela w Postgresie (np. `bytea` gzip albo `jsonb` z kompresją TOAST) za interfejsem magazynu, czy od razu Railway Bucket? `tech-stack-api.md:49` dopuszcza tymczasowo bazę; `infrastructure.md` (według raportu) każe zdecydować przed ingestem produkcyjnym. Wymóg „bezstratnie” oznacza zapis dokładnych bajtów elementu. Fixtures są anonimizowane (`fixtures/wykop/`), więc do testu bezstratności potrzebny jest syntetyczny albo świeżo pobrany element. *Aktualizacja (External Research):* `jsonb` odpada (nie jest bezstratny); do wyboru element czy cała strona jako jednostka RAW oraz kompresja w aplikacji vs TOAST `lz4` (małe wpisy < 2 KB mogą nie być kompresowane przez TOAST).
2. **Mechanizm ręcznego uruchomienia.** Komenda CLI (`run-pipeline --from --to`, w duchu `migrate`, uruchamiana przez `railway ssh` albo lokalnie), czy wewnętrzny endpoint pod przyszły cron z S-05 i FR-022? Roadmapa S-05 skłania się ku endpointowi; `deploy-plan.md` i guidelines mówią o osobnym serwisie.
3. **Gdzie weryfikować z prawdziwym Wykopem/LLM** (`roadmap.md:168`). Lokalnie z sekretami w powłoce, czy PR env z dodanymi `WYKOP_KEY`/`WYKOP_SECRET`? To drugie jest sprzeczne z `deploy-plan.md:272`. Zmiennych Wykopu nie ma w `.railway/railway.ts` ani w CI (według raportu).
4. **Prezentacja na liście ocen `failed` i `prefilter`.** PRD nie mówi, czy post z oceną nieudaną (brak `output`) pojawia się na liście ani czy ponawiać jego ocenę w kolejnym przebiegu. US-01 wymaga, by każdy widoczny post miał wersję surową i przetworzoną.
5. **Rekord przebiegu.** S-05/S-06 będą potrzebować: id, start/koniec, status ok/partial/failed, liczniki, zakres dat. Czy S-02 wprowadza tabelę przebiegów od razu (kontrakt wymaga „pipeline version / processing run” przy ocenie, `contract-v2.md:430`), czy minimalnie?
6. **Równoległość i koszt oceny.** Sekwencyjnie czy przez własny executor, i czy klienci LLM są bezpieczni wątkowo? *Aktualizacja:* w sprawdzonych klasach brak współdzielonego mutowalnego stanu (sekcja External Research), ale brak testu współbieżnego. Batch API i tryb off-peak zostały odłożone do S-02/S-05 (`frame.md:146` w archiwum F-02 według raportu). Pułapu kosztów brak (`roadmap.md:260`).
7. **Paginacja API.** Offset/limit czy kursor po (`published_at`, id)? Konieczny handler błędnych parametrów w `ApiExceptionHandler`.
8. **Zakres poprawek F7–F8f i N19.** Czy wchodzą w S-02 jako pierwsza faza, czy osobno? F7 i F8a dotyczą wprost zapisywanych danych.
9. **Szczegóły danych.**
   - Dla linków mapper używa `created_at`, nie `published_at` (`WykopMapper.java:72`). Do potwierdzenia, która data jest „chronologiczna” na liście.
   - Czas Wykopu jest lokalny bez offsetu, więc godzina przy zmianie czasu (DST) jest niejednoznaczna.
   - Które metadane źródłowe (autor, URL/slug, tagi) trafiają do znormalizowanego posta na potrzeby S-03/S-04.
10. **Limity i warunki API Wykopu.** W repo nieudokumentowane (`roadmap.md:169`). *Aktualizacja:* publiczne źródła nie podają limitów ani warunków API (sekcja External Research); do potwierdzenia przez użytkownika na podstawie warunków rejestracji klucza. `WykopApiClient` nie obsługuje 429.
