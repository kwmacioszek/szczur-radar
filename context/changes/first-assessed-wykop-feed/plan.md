# S-02 first-assessed-wykop-feed — plan implementacji

## Overview

Pierwszy kompletny przepływ Szczur Radaru. Admin uruchamia w SPA przebieg dla zakresu dat. Pipeline (agent Embabel z deterministycznym planowaniem GOAP) najpierw ponawia „resztki”, czyli posty z nieudaną oceną (maks. 3 próby, potem `DEAD`) i posty niedokończone przez przerwany przebieg (`STORED`). Potem pobiera z Wykopu #gielda wpisy i znaleziska z zakresu, zapisuje surowy payload osobno od danych aplikacji, zapisuje posty bez duplikatów, ocenia je równolegle według kontraktu V2 i zapisuje każdy krok przebiegu. Zalogowany użytkownik widzi chronologiczną, stronicowaną listę postów z oceną. Admin widzi dodatkowo historię przebiegów i listę postów nieudanych, z możliwością ponowienia zaznaczonych.

Trwały stan żyje w Postgresie, per post i per przebieg. Model kroków jest przygotowany pod przyszłe etapy ML (embeddingi, klasyfikatory, regresja) i pod Batch API dostawcy LLM, ale żadnego z nich S-02 nie buduje.

## Current State Analysis

Na podstawie `research.md` (w tym sekcji External Research):

- **Gotowe klocki bez produkcyjnego przepływu:**
  - Wykop: `WykopApiClient`, `WykopRepository.itemsAddedBetween` (stronicowanie, deduplikacja w obrębie pobrania po `resource:id`), `WykopMapper`;
  - ocena: `Assessor` (`prepare`/`evaluate` jako dwie połowy pod tryb wsadowy), `AssessmentResult`, `Provenance`.
  - Klasy Wykopu nie są beanami. `WYKOP_KEY`/`WYKOP_SECRET` występują tylko w kodzie pomiarowym.
- **Surowy payload ginie:** `WykopRepository.java:129` (`treeToValue`) odrzuca `JsonNode`.
- **Persystencja:**
  - migracje są tylko dwie, V1 `users` i V2 `spring_session`;
  - nie ma jsonb, kompresji, stronicowania, executorów ani `@Scheduled`;
  - `MigrationsIT.java:36` sprawdza dokładną listę wersji `("1", "2")`.
- **Wzorce:**
  - encje UUID + `@CreationTimestamp`, enumy jako TEXT + `CHECK` + `AttributeConverter(autoApply)`;
  - kontrolery package-private z rekordami DTO, błędy `{"detail": code}` przez `ErrorCode`;
  - deny-by-default i `AuthorizationCompletenessIT` (`PUBLIC_ROUTES`, `ADMIN_ROUTES`);
  - `/api/admin/**` wymaga roli ADMIN (`SecurityConfig.java:57`).
- **Embabel 1.5.2:**
  - `AssessorAgent` jest jedynym `@Agent`, a `AgentPlatform` działa (`AssessorAgentIT`).
  - Wyłączone są tylko autokonfiguracje modeli (`application.properties:43-46`).
  - GOAP łączy akcje po typach deterministycznie.
  - Persistence Embabela zapisuje checkpoint tylko w stanach WAITING i końcowych (dokumentacja Embabel, Context7), więc trwałość przebiegu musi być nasza.
- **SPA:**
  - `/` → `HomeView` (placeholder), `/login` publiczny;
  - `apiFetch` w `web/api/client.ts`, a `User.role: 'user' | 'admin'` w `web/api/auth.ts:7`;
  - zwykły CSS, Vitest.
- **Wdrożenie:**
  - API ma `sleepApplication: true` (`.railway/railway.ts`);
  - zmienne LLM w PR env ustawia krok CI „Ensure PR-env LLM settings” (`.github/workflows/ci.yml:239`);
  - zmiennych Wykopu nie ma nigdzie;
  - `deploy-plan.md:270-272` mówi o osobnym serwisie cron i zakazie prawdziwych wywołań w PR env, a ta zmiana świadomie to zmienia.
- **Dług F-02:** F7, F8a–F8f oznaczone „Before S-02” (`context/archive/2026-09-27-assessment-contract-harness/follow-ups/review-fixes.md:30-47`).

## Desired End State

- Admin w SPA wpisuje zakres dat (czas warszawski) i uruchamia przebieg. Widzi w kolejce przebieg `RETRY` (jeśli są posty do ponowienia) i przebieg `RANGE`, ich status, kroki i liczniki.
- Po zakończeniu przebiegu `RANGE` każdy wpis i znalezisko #gielda z zakresu ma w bazie:
  - jeden wiersz `posts` z unikalnym kluczem (źródło, typ zasobu, id ze źródła);
  - jeden powiązany wiersz `raw_payloads` (gzip, bezstratny co do pól i wartości);
  - co najmniej jedną próbę w `assessments` z wejściem LLM, wyjściem, statusem i `Provenance`. Wyjątek: błąd techniczny przed zapisem oceny pozostawia post jako resztkę (`STORED` lub wcześniejszy `FAILED`), zwiększa `processing_errors` przebiegu i wyklucza status `succeeded`; post może zostać ponowiony przy kolejnym żądaniu.
- Ponowne uruchomienie tego samego zakresu nie tworzy duplikatów postów i nie ocenia ponownie postów z oceną ok.
- Zalogowany użytkownik widzi pod `/` posty, których ostatnia ocena jest ok (łącznie z wynikiem prefiltra), posortowane po `published_at` malejąco, stronicowane `page`/`size`.
- Admin widzi pod `/admin` listę postów nieudanych (`FAILED` z liczbą prób i `DEAD`) z klasą błędu ostatniej próby i może ponowić zaznaczone (przebieg `POSTS`).
- Przebieg zweryfikowany w Railway PR env na prawdziwym Wykopie i LLM.

Weryfikacja: `./mvnw -B verify`, `npm test`, `npm run build` zielone, CI zielone, ręczny przebieg jednego dnia w PR env (faza 6).

### Key Discoveries:

- `Assessor.java:30-31,56,73`: `prepare`/`evaluate` umożliwiają wysyłkę zapytań poza `assess()` (executor teraz, Batch API później).
- `WykopRepository.java:94,129,156-157`: zakres dat, punkt utraty RAW, klucz `resource:id`.
- `WykopMapper.java:72`: `published_at` = `created_at` Wykopu w czasie Europe/Warsaw (dla wpisów i linków).
- `Provenance.java:43`: komplet pól pochodzenia oceny; `contract-v2.md:423-436`: lista pól do przechowania.
- `api/cli/AdminBootstrap.java`: wzorzec `TransactionTemplate` + advisory lock.
- `test/auth/AuthorizationCompletenessIT.java:51,58`: rejestr tras publicznych i administracyjnych.
- `.github/workflows/ci.yml:239-270`: wzorzec ustawiania sekretów w PR env.
- PostgreSQL 18 §8.14: `jsonb` gubi kolejność kluczy, białe znaki i duplikaty kluczy, więc nie nadaje się na RAW. TOAST kompresuje dopiero krotki > ~2 KB.

## What We're NOT Doing

- Harmonogramu dziennego i etykiety świeżości (S-05). Dashboardu historii przebiegów poza prostą listą ostatnich przebiegów i kroków (S-06).
- Widoku szczegółowego posta, surowego JSON w UI i linku do oryginału (S-03). S-02 tylko zapisuje to, czego S-03 potrzebuje: RAW, slug/URL, autora.
- Filtrowania (S-04).
- Etapów ML (embeddingi, klasyfikatory, regresja) i Batch API. Tylko szkielet, który je przyjmie (etapy posta, `AssessmentDispatcher`).
- Zmiany promptu N19 i ponownego pomiaru na zestawie wzorcowym.
- Komentarzy z Wykopu (PRD §Non-Goals) i źródeł innych niż Wykop.
- Railway Bucket dla RAW. RAW zostaje w Postgresie za interfejsem `RawPayloadStore`.
- LangGraph4j i checkpointów Embabela. Trwałość daje nasz stan w bazie.
- Komendy CLI do przebiegu. Uruchamia go wyłącznie endpoint admina.
- Wznawiania przerwanego przebiegu „od kroku”. Przerwany przebieg dostaje status `INTERRUPTED`, a jego niedokończone posty bierze następny przebieg.

## Implementation Approach

1. Najpierw domykamy dług F-02, żeby od pierwszego zapisu dane były spójne (provider/model w nieudanych ocenach, jednolita serializacja enumów, poprawna klasyfikacja pustych odpowiedzi).
2. Potem model danych z trwałym stanem:
   - `pipeline_runs` i `pipeline_run_steps` opisują przebieg;
   - `posts.stage` i `posts.assessment_attempts` opisują postęp posta;
   - `assessments` przechowuje każdą próbę;
   - `raw_payloads` trzyma RAW osobno.
3. Pipeline to Embabel `@Agent` (GOAP) z **cienkimi akcjami**: każda deleguje do zwykłego serwisu Springa i zapisuje swój krok. Logikę testujemy jednostkowo bez Embabela, a graf jednym IT.
4. Przebiegi wykonuje w tle jednowątkowy executor przebiegów (kolejka, jeden aktywny naraz). Oceny wysyła `AssessmentDispatcher` z własnym executorem o limicie `PIPELINE_LLM_CONCURRENCY` (domyślnie 4).
5. Na końcu REST, SPA, dokumenty i weryfikacja w PR env.

## Critical Implementation Details

- **Kolejność i współbieżność przebiegów.**
  - `POST /api/admin/pipeline-runs` z zakresem tworzy w jednej transakcji do dwóch przebiegów `QUEUED`: `RETRY` (tylko jeśli istnieje post `STORED` albo `FAILED` z próbami < 3) i `RANGE`. Każdy post dostaje najwyżej jedną próbę na żądanie: `RETRY` bierze resztki, a `RANGE` tylko świeżo zapisane posty swojego zakresu, przekazane przez `StoredBatch` i mające `first_run_id = :rangeRunId`. Post niedokończony w `RETRY` pozostaje do ponowienia przez `RETRY` przy kolejnym żądaniu albo przez jawny przebieg `POSTS`; nie trafia do `RANGE` tego samego żądania. Przekazuje je jednowątkowemu executorowi przebiegów w tej kolejności.
  - Żądanie odrzucane jest kodem 409 `pipeline_busy`, gdy istnieje przebieg `QUEUED` lub `RUNNING`. Sprawdzenie i wstawienie są atomowe: transakcja zaczyna się od `pg_advisory_xact_lock` (wzorzec `AdminBootstrap`), więc dwa równoczesne żądania nie utworzą dwóch kompletów przebiegów.
  - Unikalny indeks częściowy na `pipeline_runs` (co najwyżej jeden `RUNNING`) jest drugą linią obrony.
- **Przerwanie przez deploy lub restart.** Po starcie aplikacji (`ApplicationReadyEvent`) przebiegi `QUEUED` i `RUNNING` przechodzą w `INTERRUPTED`. Posty w etapie `ASSESSING` należące do tych przebiegów (`assessing_run_id`) wracają do stanu sprzed próby, bez doliczania próby: `assessment_attempts = 0` → `STORED`, `> 0` → `FAILED` (próby rosną tylko przy `FAILED`, więc osobna kolumna poprzedniego etapu nie jest potrzebna). Cofnięcie zeruje `assessing_run_id`. Ta sama reguła obowiązuje przy każdym cofnięciu `ASSESSING`. Bez tego indeks „jeden `RUNNING`” zablokowałby kolejne przebiegi na zawsze.
- **Nakładanie się wersji przy deployu.** Railway uruchamia nową wersję obok starej i wyłącza starą (SIGTERM) dopiero po healthchecku nowej, więc recovery nowej wersji może się wykonać, gdy stara jeszcze liczy przebieg. Dlatego:
  - przejście `QUEUED` → `RUNNING` jest warunkowe; brak zmienionego wiersza oznacza, że executor nie uruchamia agenta. Zmiana statusu końcowego także wymaga dotychczasowego `RUNNING`;
  - zamiast blokad wierszy (`SELECT … FOR UPDATE`) każde przejście stanu to pojedynczy warunkowy `UPDATE`; liczba zmienionych wierszy mówi, czy zapis się udał. Przejęcie posta: `UPDATE posts SET stage = 'assessing', assessing_run_id = :runId WHERE id = :id AND stage IN (…) AND EXISTS (SELECT 1 FROM pipeline_runs WHERE id = :runId AND status = 'running')`; zero wierszy oznacza, że przebieg nie jest już aktywny, więc stary worker nie podejmuje kolejnych postów. Liczniki i podsumowanie przebiegu to `UPDATE pipeline_runs … WHERE id = :runId AND status = 'running'`, więc przerwany przebieg ich nie zmienia;
  - `ASSESSING` ma właściciela w `posts.assessing_run_id`. Zapis wyniku to jedna transakcja: najpierw `UPDATE posts SET stage = …, assessing_run_id = NULL, … WHERE id = :id AND stage = 'assessing' AND assessing_run_id = :runId`, a wiersz `assessments` jest wstawiany tylko przy jednym zmienionym wierszu (inaczej rollback, bez oceny i bez doliczania próby). Wynik, który stary worker zapisze, zanim recovery cofnie jego post, jest przyjmowany: to prawdziwa ocena, a post wciąż należał do tego workera;
  - `finally`, końcowe sprzątanie i recovery cofają wyłącznie posty z `stage = 'assessing' AND assessing_run_id = :runId` i zerują właściciela, więc nie dotykają postów przejętych już przez nowy przebieg. Pozostałe spóźnione zapisy starego przebiegu (RAW, nowe posty, kroki) są nieszkodliwe: deduplikacja odrzuca powtórzone posty, a posty `STORED` podejmie następny `RETRY`;
  - przy zamykaniu kontekstu (`ContextClosedEvent`) instancja zatrzymuje executory i sama oznacza swój przebieg jako `INTERRUPTED`;
  - reguła operatorska: nie merguj do `main` i nie pushuj gałęzi PR, gdy w `/admin` jest przebieg `RUNNING` (runbook, faza 6). Osobny serwis workera, niezależny od deployu API, to kierunek dla S-05.
- **Licznik prób.** Próba liczy się tylko wtedy, gdy zapisano wiersz `assessments` ze statusem `FAILED`. Trzecia taka próba ustawia `stage = DEAD`. `DEAD` nie trafia do `RETRY` ani `POSTS`. Ponowienie `DEAD` z listy admina jest jawnie odrzucane (400 `post_dead`); świadome wskrzeszanie jest poza S-02.
- **Każda ocena zapisywana od razu**, osobną transakcją per post, a nie na końcu batcha. Dzięki temu przerwanie traci najwyżej oceny w locie.
- **Uśpienie API na Railway.** `sleepApplication: true` usypia serwis po ok. 10 min bez ruchu wychodzącego. Trwający przebieg generuje ruch do Wykopu i LLM, więc API nie powinno zasnąć. Faza 6 sprawdza to w PR env. Jeśli zaśnie, przebieg kończy się jako `INTERRUPTED` przy następnym starcie, bez utraty danych.
- **Czas Wykopu.** `date_from`/`date_to` i `created_at` są w czasie Europe/Warsaw bez offsetu. Formularz SPA przyjmuje daty kalendarzowe, a backend zamienia je na `[from 00:00:00, to 23:59:59]` czasu warszawskiego, tak jak `itemsOfDay` (`WykopRepository.java:83`).

## Phase 1: Domknięcie długu F-02 (F7, F8a–F8f)

### Overview

Zamykamy poprawki oznaczone „Before S-02” w `follow-ups/review-fixes.md:30-47`, zanim jakiekolwiek oceny trafią do bazy.

### Changes Required:

#### 1. F7 — provider i model w nieudanej ocenie

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/Assessor.java`

**Intent**: `callFailed` zapisuje dziś `null` jako provider i model. Assessor ma dostać skonfigurowanego providera i model, żeby nieudana próba w bazie mówiła, kogo dotyczyła.

**Contract**: `Provenance` dla `FAILED` z `PROVIDER_ERROR`/`TIMEOUT` ma niepuste `provider` i `model`, wzięte z `LlmProviderProperties` (wstrzykiwane do `Assessor`). Prywatne `callFailed` staje się publiczną metodą `Assessor.failed(PreparedRequest, LlmCallException, Duration)`, której używają `assess()` i dispatcher z fazy 3 (jedno miejsce budowania wyniku `FAILED`, bez kopii). Test w `AssessorTest`.

**Consumers**: Aktualizacja konstruktorów w `szczur-radar-api/src/test/java/pl/szczurradar/api/agents/assessor/AssessorTest.java` oraz w pakiecie `src/test/java/pl/szczurradar/api/measurement/`: `GoldRecorder.java`, `LiveSmokeTest.java`, `Replay.java`. `GoldRecorder` i `LiveSmokeTest` przekazują tę samą konfigurację providera/modelu co do `LlmClientFactory`. Dla testów i offline `Replay` zachować konstruktor pomocniczy przyjmujący klienta, `Provider`, model i `Clock`; produkcyjny konstruktor deleguje do niego z wartościami z `LlmProviderProperties`. `Replay` używa stałych wartości testowych i klienta odrzucającego każdą próbę wywołania, bez wymagania klucza, zmiennych środowiska ani sieci. Pochodzenie odtwarzanych odpowiedzi w `evaluate()` nadal pochodzi z nagranego `LlmResponse`.

#### 2. F8a — spójna serializacja enumów dostawcy

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/LlmProviderProperties.java` (enumy `Provider`, `ServiceTier`, `ReasoningEffort`)

**Intent**: Wartości zapisywane w bazie i JSON mają być małymi literami, spójnie z `Channel`/`OutputMode`.

**Contract**: `@JsonValue` na `id()`. Przeniesienie enumów do `llm` jest dopuszczalne jako osobny commit refaktoringu bez zmiany zachowania. Test serializacji `Provenance`.

#### 3. F8b — górna granica `LLM_TIMEOUT`

**File**: `LlmProviderProperties.java`

**Intent**: Uniknąć przepełnienia `toIntExact` w Gemini.

**Contract**: `from()` odrzuca wartość powyżej ustalonego maksimum (np. 30 min) komunikatem dopisanym do zbiorczego `IllegalStateException`.

#### 4. F8c — pusta odpowiedź Gemini

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/GeminiClient.java`

**Intent**: Pusta odpowiedź z finish reason `stop` ma być błędem dostawcy jak u innych klientów, a nie sukcesem.

**Contract**: Pusty tekst (sprawdzany przez `Whitespace`, nie `isBlank`) kończy się `LlmCallException(PROVIDER_ERROR)`, oznaczonym jako `REJECTED`. Test na fixture.

#### 5. F8d — maskowanie sekretów w błędach Wykopu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java`

**Intent**: Treść błędów (także z `auth`) nie może ujawniać klucza, sekretu ani tokenu.

**Contract**: Komunikaty `WykopApiException` przechodzą przez maskowanie analogiczne do `ProviderMessages` (sekret → `<hidden>`, maks. 500 znaków). Test z `LocalWykopServer`.

#### 6. F8e — cache schematu wyjścia

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/ProviderSchemas.java` (i miejsca czytające schemat)

**Intent**: Nie czytać i nie przekształcać schematu przy każdym wywołaniu. Ma to znaczenie przy równoległych ocenach.

**Contract**: Tekst schematu wyjścia w miejscu jego wczytania jest buforowany bezpiecznie wątkowo. Cache `ProviderSchemas.forProvider(provider, schema)` ma klucz `(provider, schema)` obejmujący pełny tekst schematu; przekształcenie liczone raz dla danej pary. Różne schematy tego samego providera nie współdzielą wyniku. `ProviderSchemasTest` zachowuje istniejące przypadki różnych schematów i sprawdza ich poprawne wyniki także przy równoległych wywołaniach.

#### 7. F8f — niewidoczne znaki a §10 kontraktu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/text/` (normalizacja), `context/foundation/llm-assessment/contract-v2.md` §10

**Intent**: Sprawdzić na `fixtures/wykop` i `fixtures/assessor/gold`, czy U+200B, U+00AD i U+FEFF występują w danych Wykopu. Jeśli tak, rozszerzyć normalizację §10 przez procedurę zmiany kontraktu §16: failing fixture → zmiana kontraktu → kod.

**Contract**: Jeśli zmiana jest potrzebna: nowa reguła normalizacji, test D6 na cytacie z takim znakiem i wpis w kontrakcie. Jeśli znaków nie ma, wynik sprawdzenia trafia jako notatka do `follow-ups/` tej zmiany, bez zmian w kodzie.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe assessora, LLM i Wykopu pokrywają F7, F8a–F8e (i F8f, jeśli dotyczy): `cd szczur-radar-api && ./mvnw -B test`
- Pełna weryfikacja backendu przechodzi: `cd szczur-radar-api && ./mvnw -B verify`

#### Manual Verification:

- Wynik sprawdzenia F8f (znaki obecne lub nie) jest zapisany i zaakceptowany przez użytkownika

**Implementation Note**: Na początku tej fazy `/10x-implement` tworzy gałąź `feature/first-assessed-wykop-feed` z `origin/main`, PR i komentarz w issue S-02 (#14) zgodnie z `REPOSITORY_GUIDELINES.md`. Folder zmiany idzie w pierwszym commicie.

---

## Phase 2: Model danych, magazyn RAW i źródło Wykopu jako komponent

### Overview

Schemat trwałego stanu przebiegów i postów, RAW osobno od danych aplikacji oraz `WykopRepository` oddające surowy element.

### Changes Required:

#### 1. Migracja V3

**File**: `szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql`

**Intent**: Tabele przebiegu, postów, RAW i ocen zgodnie z konwencjami V1: UUID z `gen_random_uuid()`, `TIMESTAMPTZ`, TEXT + `CHECK` dla zamkniętych zbiorów, nazwane ograniczenia, komentarz fix-forward.

**Contract**:
- `pipeline_runs`:
  - `id`; `kind` ∈ {`range`, `retry`, `posts`};
  - `status` ∈ {`queued`, `running`, `succeeded`, `partial`, `failed`, `interrupted`};
  - `range_from`, `range_to` (TIMESTAMPTZ, null poza `range`);
  - `requested_post_ids` (UUID[], tylko `posts`); `requested_by` (FK `users`);
  - `created_at`, `started_at`, `finished_at`;
  - liczniki (`fetched`, `new_posts`, `assessed_ok`, `assessed_failed`, `processing_errors`, `dead`, `skipped`); `error`. `processing_errors` liczy posty z błędem technicznym, dla których nie zapisano wyniku oceny; nie jest liczbą nieudanych prób LLM;
  - unikalny indeks częściowy: co najwyżej jeden wiersz `status = 'running'`.
  - indeks `(created_at DESC, id DESC)` dla stabilnej listy przebiegów.
- `pipeline_run_steps`:
  - `id`, `run_id` (FK), `step` (TEXT + CHECK: `fetch`, `store`, `select`, `assess`, `summarize`);
  - `status` ∈ {`running`, `succeeded`, `failed`};
  - `started_at`, `finished_at`, `items_in`, `items_out`, `error`.
- `raw_payloads`:
  - `id`, `source`, `content_encoding` (`gzip`), `media_type` (`application/json`), `payload BYTEA`, `sha256`, `created_at`.
  - Bez indeksów treści; tabela jest poza główną ścieżką zapytań.
- `posts`:
  - `id`, `source` (`wykop`), `resource` (`entry` | `link`), `source_item_id` (TEXT NOT NULL);
  - `published_at` (TIMESTAMPTZ), `title`, `body`, `author`, `slug`, `web_url`, `tags` (TEXT[]);
  - `raw_payload_id` (FK, unikalny), `content_hash`;
  - `stage` ∈ {`stored`, `assessing`, `assessed`, `failed`, `dead`}; `assessment_attempts` INT;
  - `assessing_run_id` (nullable FK `pipeline_runs`); CHECK: właściciel jest niepusty wtedy i tylko wtedy, gdy `stage = 'assessing'`;
  - `first_run_id` (FK), `created_at`, `updated_at`;
  - `UNIQUE (source, resource, source_item_id)`; indeks `(stage, published_at DESC, id DESC)`.
- `assessments`:
  - `id`, `post_id` (FK), `run_id` (FK); `status` ∈ {`ok`, `failed`}; `failure_class` (TEXT + CHECK z 7 klas kontraktu, null dla ok);
  - `input` (JSONB — `AssessmentInputV1`), `input_hash`, `output` (JSONB, null dla failed), `violations` (JSONB), `raw_text`;
  - kolumny `Provenance`: `result_source` (`model` | `prefilter`), `provider`, `model`, `channel`, `service_tier`, `flex_fallback`, `reasoning_effort`, `output_mode`, `prompt_version`, `contract_version`, `assessed_at`, `input_tokens`, `output_tokens`, `cached_tokens`, `duration_ms`, `attempts`;
  - indeks `(post_id, assessed_at DESC, id DESC)`. Przy równym `assessed_at` ostatnią próbę wyznacza `id DESC` (jawna reguła remisu, nie numer kolejności wykonania).

`jsonb` jest dopuszczalne dla `input`/`output`/`violations`: to dane aplikacji, a nie RAW.

#### 2. Encje i repozytoria

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/` (`PipelineRun`, `PipelineRunStep`, repozytoria, enumy z konwerterami), `.../posts/` (`Post`, `Assessment`, `PostRepository`, `AssessmentRepository`), `.../raw/` (`RawPayload`)

**Intent**: Encje w stylu `User`. Każdy zamknięty zbiór jako enum + `AttributeConverter(autoApply)` (lekcja o enumach). Kolumny JSONB mapowane przez `@JdbcTypeCode(SqlTypes.JSON)`, a `output` serializowany przez `ContractJson.MAPPER`, nie mapper Boota.

**Contract**: `PostRepository` ma zapytania potrzebne pipeline'owi i API:
- istniejące klucze dla zbioru `(resource, source_item_id)`;
- resztki: posty `STORED` oraz `FAILED` z `assessment_attempts < 3`;
- strona postów `ASSESSED` po `published_at DESC, id DESC`;
- strona postów `FAILED`/`DEAD` z ostatnią próbą;
- wybór ostatniej oceny (ok dla listy użytkownika) i ostatniej próby dla listy nieudanych po `assessed_at DESC, id DESC`, również przy identycznych timestampach.

#### 3. Magazyn RAW

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/raw/RawPayloadStore.java` (+ `PostgresRawPayloadStore`)

**Intent**: Dedykowany interfejs magazynu (`tech-stack-api.md:45-49`), żeby backend dało się później wymienić (np. na Railway Bucket) bez zmiany modelu domeny i API.

**Contract**:
- `RawPayloadRef store(String source, byte[] json)` oraz `byte[] load(RawPayloadRef)`.
- Implementacja gzipuje do `bytea` i liczy `sha256` z bajtów nieskompresowanych.
- „Bezstratnie” oznacza w S-02: element Wykopu zserializowany przez Jacksona bez utraty pól i wartości (`research.md`, External Research). Test: `load(store(x))` jest bajtowo równe `x`, a JSON elementu z fixture po zapisie i odczycie ma te same pola i wartości co wejście.

#### 4. `WykopRepository` oddaje surowy element

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java`, `WykopSearchResult.java`

**Intent**: Obok `WykopItem` zachować `JsonNode` elementu (zgodnie z javadoc `WykopItem.java:11`). Deduplikacja w obrębie pobrania i obsługa elementów o złym kształcie bez zmian.

**Contract**: `WykopSearchResult` zawiera listę par (`WykopItem`, surowy `JsonNode` elementu). Istniejące testy repozytorium zostają, a nowy test sprawdza, że surowy element ma wszystkie pola fixture (np. `votes`, `comments`).

**Consumers**: Dostosować asercje `szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopRepositoryTest.java` oraz `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/WykopGoldSampler.java`: sampler wyciąga `WykopItem` z par, zachowując swój `Map<LocalDate, List<WykopItem>>` i dotychczasowe wyniki próbkowania. Kod pomiarowy pozostaje poza produkcyjnym jar, ale musi kompilować się podczas zwykłego buildu.

#### 5. Wykop jako komponent Springa

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopConfiguration.java`

**Intent**: Bean `WykopRepository` z `WYKOP_KEY`/`WYKOP_SECRET`. Klucze są **opcjonalne**: API, CI i testy startują bez nich, a brak kluczy wychodzi dopiero przy próbie przebiegu `RANGE`.

**Contract**: Bean leniwy albo `Optional`. Bez kluczy `RANGE` kończy się błędem `wykop_not_configured` (patrz faza 4). `README.md` API dokumentuje obie zmienne.

#### 6. Testy schematu

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/MigrationsIT.java`, `JpaSchemaIT.java`

**Intent**: `MigrationsIT` oczekuje `("1", "2", "3")` i sprawdza nowe ograniczenia (unikalny klucz posta, CHECK statusów, unikalny `running`), w stylu istniejących asercji. `JpaSchemaIT` waliduje nowe encje i robi round-trip postu z RAW i oceną.

### Success Criteria:

#### Automated Verification:

- Migracja V3 przechodzi, a `MigrationsIT` sprawdza nowe ograniczenia: `./mvnw -B verify -Dit.test=MigrationsIT`
- `JpaSchemaIT` waliduje nowe encje i round-trip: `./mvnw -B verify -Dit.test=JpaSchemaIT`
- Testy `RawPayloadStore` (round-trip bajtowy i polowy) i `WykopRepository` (surowy element) przechodzą: `./mvnw -B test`
- Pełna weryfikacja backendu przechodzi: `./mvnw -B verify`

---

## Phase 3: Pipeline przebiegu (Embabel GOAP + trwały stan)

### Overview

Agent przebiegu z cienkimi akcjami, serwisy z logiką, dispatcher ocen z executorem, limit prób, kolejka przebiegów i obsługa przerwania.

### Changes Required:

#### 1. Serwisy kroków (logika bez Embabela)

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/` — `WykopIngest`, `PostStore`, `AssessmentSelection`, `RunRecorder`

**Intent**: Każdy krok to zwykły serwis testowalny jednostkowo.
- `WykopIngest` pobiera zakres.
- `PostStore` zapisuje RAW i nowe posty, a istniejące klucze pomija (FR-003, deduplikacja po `(source, resource, source_item_id)`). `StoredBatch` zawiera identyfikatory wyłącznie nowo zapisanych postów z `first_run_id` równym bieżącemu `RANGE`; nie zawiera identyfikatorów pominiętych duplikatów.
- `AssessmentSelection` wybiera posty do oceny:
  - dla `RETRY`: wszystkie resztki, niezależnie od daty: `STORED` oraz `FAILED` z próbami < 3 (także posty cofnięte przez recovery);
  - dla `RANGE`: tylko posty w etapie `STORED` o identyfikatorach z `StoredBatch` i z `first_run_id = :rangeRunId`. Sam zakres dat i etap `STORED` nie wystarczają do wyboru: post cofnięty do `STORED` po błędzie technicznym w poprzedzającym `RETRY` nie jest nowym postem tego `RANGE` i czeka na kolejne ponowienie;
  - dla `POSTS`: wskazane id, z pominięciem `ASSESSED` i `DEAD`.
- `RunRecorder` zapisuje kroki i liczniki.

**Contract**: Mapowanie `WykopItem` → `posts` korzysta z `WykopMapper` (wejście oceny) i `WykopItem.webUrl()`. Element, którego nie da się zmapować (`UnmappableItemException`) albo który nie ma `id` (`WykopItem.id` jest `@Nullable`, a repozytorium trzyma go pod kluczem `no-id:<n>`), jest liczony jako `skipped` w kroku `store`, a jego RAW nie jest zapisywany.

#### 2. `AssessmentDispatcher` (furtka pod Batch API)

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/AssessmentDispatcher.java`, `ConcurrentAssessmentDispatcher.java`

**Intent**: Krok `assess` nie woła `Assessor.assess()` bezpośrednio, tylko dispatcher. Implementacja S-02 używa `Assessor.prepare` + `LlmClient` + `Assessor.evaluate` na własnym executorze z limitem `PIPELINE_LLM_CONCURRENCY` (domyślnie 4, przez `@Value`). Przyszła implementacja Batch API zastąpi ją bez zmian w krokach.

**Contract**:
- `ASSESSING` oraz `assessing_run_id = :runId` ustawiane są per post jednym warunkowym `UPDATE` (Critical Implementation Details), dopiero gdy wątek executora bierze post (nie hurtem dla całej partii). Zero zmienionych wierszy kończy pracę workera.
- Wyjątek inny niż błąd LLM (bug w naszym kodzie, błąd zapisu) przy jednym poście: w `finally` etap wraca do stanu sprzed próby (bez doliczania próby), błąd idzie do logu, a reszta postów jest oceniana dalej. Dispatcher przekazuje błąd techniczny wraz z id posta w `AssessedBatch`; `RunRecorder` zapisuje `processing_errors` i komunikat podsumowania w `pipeline_runs.error`. Nie powstaje fikcyjna ocena `FAILED`, a `assessment_attempts` nie rośnie. Odrzucony zapis po utracie właściciela lub przerwaniu przebiegu nie jest błędem technicznym nowego przebiegu i nie zmienia jego liczników.
- Dla każdego posta: `stage` przechodzi `ASSESSING` → zapis wiersza `assessments` → `ASSESSED`, albo `FAILED` i `attempts + 1`, a przy 3 próbach `DEAD`. Wszystko w osobnej transakcji per post, od razu po odpowiedzi. Warunkowa zmiana etapu (właściciel = `:runId`) i wstawienie oceny są w jednej transakcji; gdy post nie należy już do przebiegu, nie powstaje ocena ani nie rośnie liczba prób. `finally` także cofa tylko własne posty.
- `LlmCallException` zamienia na wynik wyłącznie `Assessor.failed(...)` (faza 1, F7). Dispatcher nie buduje wyników `FAILED` sam.
- Wynik prefiltra zapisywany jest jako ocena ok z `result_source = prefilter`.
- Executor tworzony przez nas, z nazwanymi wątkami, i zamykany przy zamknięciu kontekstu (reguła o `availableProcessors()`).

#### 3. Agent przebiegu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineAgent.java` (+ rekordy typów kroków)

**Intent**: Embabel `@Agent` z planowaniem GOAP. Graf wynika z typów:
- `RunRequest` → `RangeTarget` | `SelectionTarget`;
- `RangeTarget` → `FetchedBatch` → `StoredBatch`;
- (`StoredBatch` | `SelectionTarget`) → `PendingAssessments` → `AssessedBatch` → `RunSummary` (`@AchievesGoal`).

Każda akcja to kilka linii: wywołanie serwisu z punktu 1 lub 2 i zapis kroku. Przyszłe etapy ML wchodzą jako nowe typy i akcje między `StoredBatch` a `PendingAssessments`.

**Contract**:
- Uruchamiany przez `AgentInvocation.create(agentPlatform, RunSummary.class).invoke(request)`.
- Rozgałęzienie `RunRequest` → `RangeTarget` | `SelectionTarget` przez typ `SomeOf` Embabela: `record RouteRun(@Nullable RangeTarget range, @Nullable SelectionTarget selection) implements SomeOf`; na tablicę trafia tylko niepuste pole.
- `PendingAssessments` produkują dwie akcje: `selectFromStored(StoredBatch)` i `selectFromTargets(SelectionTarget)`; w danym przebiegu na tablicy jest wejście tylko jednej z nich.
- Reguła jednoznaczności: dla danego stanu tablicy uruchomić się może dokładnie jedna akcja (bez niejednoznaczności planowania).
- `RunSummary` ustala status przebiegu:
  - `succeeded`: brak nieudanych ocen, `DEAD` i błędów technicznych (`processing_errors = 0`);
  - `partial`: zakończone kroki, ale co najmniej jedna nieudana ocena, `DEAD` albo błąd techniczny per post (`processing_errors > 0`);
  - `failed`: wyjątek przerywający cały krok, np. błąd Wykopu albo niemożność trwałego zapisania podsumowania. `INTERRUPTED` zachowuje pierwszeństwo zgodnie z warunkami zapisu i recovery.

#### 4. Kolejka i cykl życia przebiegów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java`, `PipelineStartupRecovery.java`

**Intent**: Implementacja zasad z sekcji Critical Implementation Details.
- `PipelineRunService` tworzy przebiegi (`RETRY` + `RANGE` albo `POSTS`), odrzuca start przy aktywnym przebiegu i wykonuje je po kolei na jednowątkowym executorze przebiegów.
- Przejścia stanów warunkowe, a przy `ContextClosedEvent` własny przebieg → `INTERRUPTED` (sekcja Critical Implementation Details, „Nakładanie się wersji przy deployu”).
- `PipelineStartupRecovery` przy starcie warunkowo oznacza osierocone przebiegi (`QUEUED`/`RUNNING`) jako `INTERRUPTED` i w tej samej transakcji cofa wyłącznie ich posty `ASSESSING` (po `assessing_run_id`) według reguły `assessment_attempts = 0` → `STORED`, `> 0` → `FAILED` (bez doliczania próby), zerując właściciela.

**Contract**: Serwis zwraca utworzone przebiegi albo rzuca wyjątek mapowany na 409 `pipeline_busy`. Nieobsłużony wyjątek w przebiegu kończy go jako `failed` z `error` i zwalnia kolejkę. Na końcu każdego przebiegu (także `failed`) serwis cofa wyłącznie pozostałe posty `ASSESSING` należące do niego (`assessing_run_id = :runId`) tą samą logiką co `PipelineStartupRecovery`, żeby nic nie czekało na restart. Liczniki i status w `RunRecorder` zapisywane są warunkowo (`status = 'running'`), bez blokad wierszy; przerwany przebieg nie zmienia swoich liczników ani statusu, a nowego przebiegu nie dotyka wcale.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe serwisów: deduplikacja, wybór postów dla `RANGE`/`RETRY`/`POSTS`, limit 3 prób → `DEAD`, prefiltr jako ok, status `RunSummary`: `./mvnw -B test`
- Test współbieżny `ConcurrentAssessmentDispatcher` (wiele postów, limit wątków, jeden `LlmClient`, każdy post oceniony dokładnie raz; `LlmClient` rzucający `IllegalStateException` dla jednego posta nie zostawia go w `ASSESSING` i nie dolicza próby, pozostałe posty są oceniane, a błąd trafia do podsumowania z `processing_errors = 1` i statusem `partial`): `./mvnw -B test`
- `PipelineAgentIT` przechodzi cały graf `RANGE` i `RETRY` na stubach (`LocalWykopServer` + skryptowany `LlmClient`) i sprawdza zapisane posty, RAW, oceny, kroki i liczniki. Scenariusz istniejącego `STORED` z zakresu, dla którego `RETRY` kończy ocenę błędem technicznym: następujący `RANGE` nie wybiera go ponownie (jedno podejście na żądanie), a `RETRY` kolejnego żądania nadal wybiera ten post: `./mvnw -B verify`
- IT drugiego przebiegu tego samego zakresu: zero nowych postów i zero ocen dla `ASSESSED`: `./mvnw -B verify`
- IT recovery: przebieg `RUNNING` i post `ASSESSING` po starcie kontekstu są oznaczone/cofnięte, a spóźniony zapis `succeeded` dla przebiegu `INTERRUPTED` niczego nie zmienia. Po przejęciu tego samego posta przez nowy przebieg spóźniony wynik, `finally` i końcowe sprzątanie starego workera nie dopisują oceny ani nie zmieniają etapu, właściciela ani prób; stary worker nie przejmuje nowych postów po `INTERRUPTED`: `./mvnw -B verify`

---

## Phase 4: REST API

### Overview

Trasy listy dla użytkownika oraz trasy admina: start przebiegu, lista przebiegów i lista nieudanych.

### Changes Required:

#### 1. Lista postów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/PostController.java` (+ DTO)

**Intent**: `GET /api/posts?page=&size=` (FR-006, FR-007) zwraca posty `ASSESSED` z ich ostatnią oceną ok.

**Contract**:
- `page` od 0, `size` domyślnie 50, maks. 100; sortowanie `published_at DESC, id DESC`.
- Ostatnia ocena ok wybierana po `assessed_at DESC, id DESC`; identyczny timestamp nie pozostawia wyboru przypadkowi.
- Odpowiedź: `{ items: [...], page, size, total }`.
- Element: `id`, `resource`, `publishedAt`, `title`, `body`, `author`, `webUrl`, `assessment` (`output` kontraktu V2 + `resultSource`, `assessedAt`).
- Wymaga sesji; deny-by-default, bez zmian w `SecurityConfig`.

#### 2. Przebiegi (admin)

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunController.java` (+ DTO)

**Intent**: Start i podgląd przebiegów.

**Contract**:
- `POST /api/admin/pipeline-runs`, body: `{ "from": "YYYY-MM-DD", "to": "YYYY-MM-DD" }` albo `{ "postIds": [uuid, ...] }`.
  - Walidacja: dokładnie jeden wariant, `from ≤ to`, `to` nie później niż dziś (czas warszawski), zakres maks. 7 dni, `postIds` 1–100.
  - Odpowiedź 202 z listą utworzonych przebiegów.
  - Błędy: 409 `pipeline_busy`, 400 `invalid_request`, 400 `post_dead` (gdy wskazano post `DEAD`), 503 `wykop_not_configured` (zakres bez kluczy Wykopu).
- `GET /api/admin/pipeline-runs?page=&size=`: ostatnie przebiegi po `created_at DESC, id DESC`, z krokami, licznikami (w tym `processingErrors`, odpowiadającym `processing_errors`) i `error`. Równe `created_at` rozstrzyga `id DESC`. Błąd techniczny per post pozostaje widoczny w podsumowaniu przebiegu mimo braku oceny `FAILED`.

#### 3. Lista nieudanych (admin)

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/FailedPostController.java`

**Intent**: Narzędzie operatorskie do diagnozy i ponowień.

**Contract**: `GET /api/admin/posts/failed?page=&size=` zwraca posty `FAILED` i `DEAD`. Element: dane posta, `stage`, `assessmentAttempts`, ostatnia próba (`failureClass`, `violations`, `assessedAt`, `provider`, `model`) wybrana po `assessed_at DESC, id DESC`.

#### 4. Błędy i rejestry tras

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ErrorCode.java`, `ApiExceptionHandler.java`, `src/test/.../auth/AuthorizationCompletenessIT.java`

**Intent**: Nowe kody i obsługa błędnych parametrów zapytania w formacie `{"detail": ...}`.

**Contract**:
- `ErrorCode` + `PIPELINE_BUSY` (409), `POST_DEAD` (400), `WYKOP_NOT_CONFIGURED` (503).
- `ApiExceptionHandler` mapuje `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` i `HandlerMethodValidationException` (np. `@Max(100)` na `size`) na `invalid_request`.
- `ADMIN_ROUTES` zawiera `POST /api/admin/pipeline-runs`, `GET /api/admin/pipeline-runs`, `GET /api/admin/posts/failed`.

### Success Criteria:

#### Automated Verification:

- `AuthorizationCompletenessIT` (401 bez sesji dla nowych tras) i `AdminRoleIT` (403 dla roli user na trasach admina) przechodzą: `./mvnw -B verify`
- IT `GET /api/posts`: kolejność, stronicowanie, `total`, brak postów `FAILED`/`DEAD`, deterministyczny wybór ostatniej oceny ok przy równym `assessed_at`, `size > 100` i `page=x` dają `invalid_request`: `./mvnw -B verify`
- IT `POST /api/admin/pipeline-runs`: 202 z `RETRY` + `RANGE` przy istniejących failed, 409 przy aktywnym przebiegu (także dla dwóch równoczesnych żądań: dokładnie jedno dostaje 202), 400 dla złego zakresu i posta `DEAD`, 503 bez kluczy Wykopu; po błędzie technicznym per post `GET /api/admin/pipeline-runs` pokazuje `partial`, `processingErrors` i komunikat `error`; lista przebiegów ma stabilne strony przy równym `created_at` dzięki `id DESC`: `./mvnw -B verify`
- IT `GET /api/admin/posts/failed`: zwraca `FAILED` i `DEAD` z ostatnią próbą, rozstrzygając równe `assessed_at` przez `id DESC`: `./mvnw -B verify`

---

## Phase 5: SPA — lista postów i panel admina

### Overview

Widok listy zamiast placeholdera i widok admina z formularzem przebiegu, historią przebiegów i listą nieudanych.

### Changes Required:

#### 1. Klient API i typy

**File**: `szczur-radar-web/src/api/posts.ts`, `szczur-radar-web/src/api/pipeline.ts`

**Intent**: Funkcje i typy dla tras z fazy 4 przez istniejące `apiFetch`, w stylu `api/auth.ts`.

**Contract**: Typy odpowiedzi odpowiadają DTO z fazy 4. Błędy `pipeline_busy`, `post_dead` i `wykop_not_configured` trafiają do UI jako czytelne komunikaty po polsku.

#### 2. Lista postów

**File**: `szczur-radar-web/src/views/FeedView.vue` (zastępuje treść `HomeView.vue` pod `/`), komponent elementu listy

**Intent**: Chronologiczna lista (US-01, FR-007) z kontrolkami stron. Każdy element pokazuje datę, typ (wpis/znalezisko), tytuł lub początek treści oraz zwięzłą ocenę: trafność, cele ze stanowiskiem i flagi niepewności. Pełna prezentacja oceny to S-03. Pusty wynik pokazuje stan pusty, a nie błąd (US-01). Topbar z e-mailem i wylogowaniem zostaje, a admin widzi dodatkowo link do panelu.

**Contract**: Strona odczytywana z query (`?page=`), więc odświeżenie zachowuje stronę. Zwykły CSS w stylu `style.css`.

#### 3. Panel admina

**File**: `szczur-radar-web/src/views/AdminView.vue`, `szczur-radar-web/src/router/index.ts`

**Intent**: Pod `/admin` trzy sekcje:
- formularz od/do (daty kalendarzowe) i „Uruchom”;
- ostatnie przebiegi z rodzajem, statusem, krokami i licznikami (także `processingErrors` jako „Błędy techniczne”) oraz komunikatem `error`, z ręcznym odświeżaniem;
- lista nieudanych z klasą błędu, liczbą prób i oznaczeniem `DEAD`, z zaznaczaniem postów (poza `DEAD`) i przyciskiem „Ponów zaznaczone”.

**Contract**: Trasa z `meta: { admin: true }`. Guard przekierowuje użytkownika bez roli admin na `/`. Backend i tak zwraca 403. Przyciski „Uruchom” i „Ponów zaznaczone” są zablokowane od kliknięcia do odpowiedzi serwera (ochrona przed podwójnym kliknięciem; gwarancją jest blokada w backendzie).

### Success Criteria:

#### Automated Verification:

- Testy Vitest: klient `posts`/`pipeline`, `FeedView` (stronicowanie, stan pusty), `AdminView` (start, komunikat `pipeline_busy`, ponowienie zaznaczonych, prezentacja `partial`, licznika błędów technicznych i komunikatu `error`), guard admina: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`

#### Manual Verification:

- Lokalnie (`npm run dev` + API na stubach albo z kluczami): lista, stronicowanie, panel admina i ponowienie wyglądają i działają poprawnie, także na wąskim ekranie

---

## Phase 6: Dokumenty, konfiguracja Railway i weryfikacja w PR env

### Overview

Aktualizacja reguł wdrożenia (pipeline w API, prawdziwe wywołania w PR env), sekrety Wykopu w PR env i na produkcji oraz weryfikacja całości na prawdziwych danych przed merge.

### Changes Required:

#### 1. Reguły i dokumenty

**File**: `REPOSITORY_GUIDELINES.md` (§ Deployment, § Project Structure), `context/foundation/infrastructure.md`, `context/deployment/deploy-plan.md`, `szczur-radar-api/README.md`, `context/foundation/roadmap.md` (F-01 outcome, S-05 unknown)

**Intent**: Zapisać decyzje S-02.
- Pipeline działa w procesie API, w tle, uruchamiany przez admina endpointem. To zastępuje regułę „osobny serwis cron, niezależny od cyklu życia API”, a sposób harmonogramu zostaje dla S-05.
- Prawdziwe wywołania Wykopu i LLM są dozwolone w PR env (zmiana `deploy-plan.md:272`).
- Te same klucze Wykopu na produkcji i w PR env. Odnotować to jako odstępstwo od F-01 („sekrety Wykopu… niedostępne w środowiskach PR”) i zaktualizować tekst F-01.
- Nowe zmienne `WYKOP_KEY`, `WYKOP_SECRET` (opcjonalne) i `PIPELINE_LLM_CONCURRENCY`.
- Nowe pakiety `pipeline`, `posts`, `raw` w opisie struktury.

**Contract**: Sekcje dokumentów zaktualizowane w miejscu (`context/foundation/README.md`). `lessons.md` tylko append, jeśli powstanie lekcja.

#### 2. Railway IaC i CI

**File**: `.railway/railway.ts`, `.github/workflows/ci.yml`

**Intent**: `WYKOP_KEY: preserve()` i `WYKOP_SECRET: preserve()` w serwisie API. Krok „Ensure PR-env LLM settings” rozszerzony o Wykop z sekretów GitHub `WYKOP_KEY`/`WYKOP_SECRET`: ustawiane tylko gdy się różnią, nigdy nie drukowane, nigdy na produkcji.

**Contract**:
- `railway config plan` dla produkcji bez zmian destrukcyjnych.
- Brak sekretów Wykopu w GitHub nie przerywa CI. Krok ostrzega, a PR env działa bez przebiegu `RANGE` (503 `wykop_not_configured`).

#### 3. Runbook produkcyjny (po merge, poza Progress)

**File**: `context/deployment/deploy-plan.md` (nowa sekcja `first-assessed-wykop-feed`)

**Intent**: Kroki po merge, wykonywane przez operatora:
0. Reguła stała: nie mergować do `main` (deploy produkcji), gdy w `/admin` jest przebieg `RUNNING`; to samo dla pushy na gałąź PR w trakcie testów.
1. Ustawić `WYKOP_KEY`/`WYKOP_SECRET` na produkcji (sealed).
2. Zalogować się jako admin i uruchomić przebieg jednego dnia.
3. Sprawdzić listę, kroki i nieudane.
4. Zamknąć issue S-02 komentarzem z wynikiem.

Odnotować ryzyko: przed F-01 brak backupu i limitów kosztów (lekcja: kroki po merge to runbook i komentarze w issue, nie Progress).

### Success Criteria:

#### Automated Verification:

- CI zielone na PR, w tym `railway-config` z rozszerzonym krokiem i `api-container-smoke` z migracją V3: `gh pr checks`
- `railway config plan` dla produkcji nie pokazuje zmian destrukcyjnych (wynik joba `railway-config`)

#### Manual Verification:

- Sekrety GitHub `WYKOP_KEY`/`WYKOP_SECRET` dodane przez użytkownika, a PR env ma ustawione zmienne Wykopu
- W PR env admin uruchamia z SPA przebieg jednego dnia: przebiegi `RETRY`/`RANGE` kończą się `succeeded` lub `partial`, kroki i liczniki są widoczne, API nie zasypia w trakcie
- Lista postów w PR env pokazuje ocenione posty z tego dnia chronologicznie i bez duplikatów; ponowny przebieg tego dnia nie dodaje postów
- Nieudane posty (jeśli są) widać w panelu admina, a „Ponów zaznaczone” tworzy przebieg `POSTS`
- Koszt przebiegu w PR env (tokeny z `assessments`) jest zgodny z szacunkiem i zaakceptowany przez użytkownika

**Implementation Note**: Po fazie 6 na tej samej gałęzi: `/10x-impl-review` z poprawkami, `/10x-archive`. Merge wykonuje człowiek, a produkcja według runbooka (lekcja „Implementacja, przegląd i archiwizacja na jednej gałęzi”).

---

## Testing Strategy

### Unit Tests:

- Poprawki F7–F8f (assessor, klienci LLM, Wykop).
- `RawPayloadStore`: round-trip bajtowy, sha256, gzip.
- `WykopRepository`: surowy element zachowuje wszystkie pola.
- Serwisy pipeline'u:
  - deduplikacja (ten sam `source_item_id` dla `entry` i `link` to dwa różne posty);
  - wybór postów per rodzaj przebiegu;
  - limit 3 prób i przejście w `DEAD`;
  - prefiltr jako ok;
  - wyliczenie statusu przebiegu.
- `ConcurrentAssessmentDispatcher`: limit równoległości, każdy post dokładnie raz, zapis per post.

### Integration Tests:

- `MigrationsIT`, `JpaSchemaIT` dla V3.
- `PipelineAgentIT`: pełny graf `RANGE` i `RETRY` na `LocalWykopServer` i skryptowanym LLM; powtórny przebieg; recovery po restarcie.
- Kontrolery: lista, przebiegi, nieudane, kody błędów; `AuthorizationCompletenessIT`, `AdminRoleIT`.

### Manual Testing Steps:

1. W PR env zaloguj się jako admin, otwórz `/admin`, uruchom przebieg dla wczorajszej daty.
2. Odśwież listę przebiegów aż do zakończenia; sprawdź kroki `fetch`, `store`, `select`, `assess`, `summarize` i liczniki.
3. Otwórz `/`: posty z dnia, najnowsze na górze, stronicowanie działa.
4. Uruchom ten sam dzień ponownie: `new_posts = 0`, brak nowych ocen dla postów ok.
5. Jeśli są nieudane: zaznacz je i ponów; po 3 nieudanych próbach post ma oznaczenie `DEAD`.

## Performance Considerations

- Typowy dzień to ok. 270 postów. Przy 4 wątkach i 10–20 s na ocenę przebieg trwa ok. 12–25 min. Limit `PIPELINE_LLM_CONCURRENCY` chroni przed 429 na tierze flex, gdzie fallback na tier default podnosi koszt (`llm/TransportRetry.java`).
- Zapytanie listy korzysta z indeksu `(stage, published_at DESC, id DESC)`; ostatnią ocenę wybiera indeks `(post_id, assessed_at DESC, id DESC)`, a listę przebiegów obsługuje indeks `(created_at DESC, id DESC)`.
- RAW (ok. 0,56 GB/rok przed kompresją) siedzi w osobnej tabeli poza ścieżką zapytań listy.

## Migration Notes

- V3 jest addytywna i nie dotyka V1/V2. Wdrożenie przez pre-deploy `migrate` (fix-forward).
- Na produkcji brak danych do migracji. Pierwszy przebieg według runbooka.
- Wycofanie: poprawka fix-forward. Backup bazy niedostępny przed F-01 (`deploy-plan.md`), co jest świadomie przyjętym ryzykiem.

## References

- Research: `context/changes/first-assessed-wykop-feed/research.md`
- Roadmapa S-02: `context/foundation/roadmap.md:159-172`
- Kontrakt V2: `context/foundation/llm-assessment/contract-v2.md` (wejście §36-77, przechowywanie §423-436)
- Dług F-02: `context/archive/2026-09-27-assessment-contract-harness/follow-ups/review-fixes.md:30-47`
- Granica RAW: `context/foundation/tech-stack-api.md:45-49`, `context/foundation/lessons.md` („Oddziel RAW…”)
- Wzorce: `api/user/User.java`, `api/cli/AdminBootstrap.java`, `api/config/SecurityConfig.java:57-58`, `test/auth/AuthorizationCompletenessIT.java:51,58`, `.github/workflows/ci.yml:239`
- Embabel: GOAP i `AgentInvocation` (dokumentacja embabel-agent, Context7); persistence Embabela tylko w stanach WAITING i końcowych

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Domknięcie długu F-02 (F7, F8a–F8f)

#### Automated

- [x] 1.1 Testy jednostkowe assessora, LLM i Wykopu pokrywają F7, F8a–F8e (i F8f, jeśli dotyczy)
- [x] 1.2 Pełna weryfikacja backendu przechodzi

#### Manual

- [x] 1.3 Wynik sprawdzenia F8f (znaki obecne lub nie) jest zapisany i zaakceptowany przez użytkownika

### Phase 2: Model danych, magazyn RAW i źródło Wykopu jako komponent

#### Automated

- [ ] 2.1 Migracja V3 przechodzi, a MigrationsIT sprawdza nowe ograniczenia
- [ ] 2.2 JpaSchemaIT waliduje nowe encje i round-trip
- [ ] 2.3 Testy RawPayloadStore (round-trip bajtowy i polowy) i WykopRepository (surowy element) przechodzą
- [ ] 2.4 Pełna weryfikacja backendu przechodzi

### Phase 3: Pipeline przebiegu (Embabel GOAP + trwały stan)

#### Automated

- [ ] 3.1 Testy jednostkowe serwisów: deduplikacja, wybór postów, limit 3 prób → DEAD, prefiltr jako ok, status RunSummary
- [ ] 3.2 Test współbieżny ConcurrentAssessmentDispatcher
- [ ] 3.3 PipelineAgentIT przechodzi cały graf RANGE i RETRY na stubach
- [ ] 3.4 IT drugiego przebiegu tego samego zakresu: zero nowych postów i zero ocen dla ASSESSED
- [ ] 3.5 IT recovery: przebieg RUNNING i post ASSESSING po starcie kontekstu są oznaczone/cofnięte

### Phase 4: REST API

#### Automated

- [ ] 4.1 AuthorizationCompletenessIT i AdminRoleIT przechodzą dla nowych tras
- [ ] 4.2 IT GET /api/posts: kolejność, stronicowanie, total, brak FAILED/DEAD, błędne parametry
- [ ] 4.3 IT POST /api/admin/pipeline-runs: 202, 409, 400, 503
- [ ] 4.4 IT GET /api/admin/posts/failed: FAILED i DEAD z ostatnią próbą

### Phase 5: SPA — lista postów i panel admina

#### Automated

- [ ] 5.1 Testy Vitest: klient, FeedView, AdminView, guard admina
- [ ] 5.2 Typecheck i build przechodzą

#### Manual

- [ ] 5.3 Lokalnie lista, stronicowanie, panel admina i ponowienie działają, także na wąskim ekranie

### Phase 6: Dokumenty, konfiguracja Railway i weryfikacja w PR env

#### Automated

- [ ] 6.1 CI zielone na PR, w tym railway-config z rozszerzonym krokiem i api-container-smoke z migracją V3
- [ ] 6.2 railway config plan dla produkcji nie pokazuje zmian destrukcyjnych

#### Manual

- [ ] 6.3 Sekrety GitHub WYKOP_KEY/WYKOP_SECRET dodane, PR env ma zmienne Wykopu
- [ ] 6.4 W PR env przebieg jednego dnia z SPA kończy się succeeded lub partial, kroki i liczniki widoczne, API nie zasypia
- [ ] 6.5 Lista postów w PR env chronologiczna i bez duplikatów; ponowny przebieg nie dodaje postów
- [ ] 6.6 Nieudane posty widoczne w panelu admina, ponowienie tworzy przebieg POSTS
- [ ] 6.7 Koszt przebiegu w PR env zgodny z szacunkiem i zaakceptowany przez użytkownika
