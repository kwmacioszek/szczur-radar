# S-23: zamknięte zbiory i kolumny posta pilnowane w CI — Implementation Plan

## Overview

Dwa testy integracyjne, które przenoszą wykrycie dwóch klas błędów z nocnego przebiegu na produkcji do CI: (1) wartość enumu zapisywanego w bazie, której `CHECK` kolumny nie przyjmuje (albo `CHECK` z wartością, której kod nie zna), oraz (2) kolumna `posts`, której warunkowe zapisy stanu w `PostTransitions` nie obsługują. Bez zmian w kodzie produkcyjnym i schemacie; do tego krótkie zdania w opisach pakietów, żeby autor nowego enumu, migracji albo kolumny posta wiedział, który test go zatrzyma. Roadmapa: `context/foundation/roadmap.md:185-195` (S-23), issue #117. Źródła: audyt po M-2, E4 (`context/changes/post-m2-audit/research.md:75`), destylacja domeny R-17 i wiersz rankingu 4 (`context/domain/domain-distillation.md:162,234`).

Konwencja ścieżek: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`, `mig/…` = `szczur-radar-api/src/main/resources/db/migration/…`.

## Current State Analysis

- **20 enumów trafia do bazy, 22 pary enum → tabela.kolumna.** 19 przez `api/persistence/EnumValueConverter.java:16` (konwertery `api/persistence/converters/*Converter.java`, `autoApply`, pakietowo-prywatne, konstruktor `super(X.class, X::value)` albo `X::id`), `Role` przez własny `api/user/RoleConverter.java:8`. `@Enumerated` nie występuje. `Source` trafia do trzech kolumn: `posts.source`, `raw_payloads.source` (konwerter) i tablicy `pipeline_runs.sources` (`List<String>` z `Source::value`, `api/pipeline/run/PipelineRun.java:52-54,132`, bez konwertera).
- **Pary (enum → kolumna, akcesor):** `PostStage` → `posts.stage`; `ItemType` → `posts.item_type`; `Source` → `posts.source`, `raw_payloads.source`, `pipeline_runs.sources`; `RunStatus` → `pipeline_runs.status`; `RunKind` → `pipeline_runs.kind`; `InterruptionCause` → `pipeline_runs.interruption_cause`; `StepStatus` → `pipeline_run_steps.status`; `RunStep` → `pipeline_run_steps.step`; `AssessmentResult.Status` → `assessments.status`; `FailureClass` → `assessments.failure_class`; `Provenance.Source` → `assessments.result_source`; `Provider` (`id()`) → `assessments.provider`; `Channel` → `assessments.channel`; `ServiceTier` (`id()`) → `assessments.service_tier`; `ReasoningEffort` (`id()`) → `assessments.reasoning_effort`; `OutputMode` → `assessments.output_mode`; `NotificationKind` → `notifications.kind`; `ContentEncoding` → `raw_payloads.content_encoding`; `RawMediaType` → `raw_payloads.media_type`; `Role` → `users.role`.
- **Dziś wszystkie 22 pary są równe swoim `CHECK`-om** (aktualne definicje: `mig/V1…:12`, `mig/V3…:29-31,55,75-77,111,114,161-175`, `mig/V4…:15`, `mig/V5…:8-10`, `mig/V8…:21-23`, `mig/V9…:17`, `mig/V11…:9-11`). Nie ma `CHECK` z listą wartości bez enumu ani enumu-kolumny bez `CHECK`. Strażnik ma więc od pierwszego uruchomienia być zielony.
- **Żaden test nie przechodzi przez `.values()` przeciw bazie.** `test/MigrationsIT.java` sprawdza tylko odrzucenie jednej nieznanej wartości na kolumnę (np. `:139-140,153-158,174-175,181-184,202,238,301`) i wstawia znane wartości literałami; `test/JpaSchemaIT.java` robi round-trip po jednej stałej. Nowa stała bez migracji wyszłaby dopiero naruszeniem `CHECK` w locie (np. `interrupt` w `onClose` → `RUN_CLOSE_FAILED`).
- **`posts` ma 21 kolumn:** 15 ustawianych raz przy `INSERT` (`id`, `source`, `item_type`, `source_item_id`, `published_at`, `title`, `body`, `author`, `slug`, `web_url`, `tags`, `raw_payload_id`, `content_hash`, `first_run_id`, `created_at`) i 6 stanu próby (`stage`, `assessment_attempts`, `assessing_run_id`, `technical_failures`, `last_technical_error`, `updated_at`). Jedyny `INSERT` to JPA w `api/pipeline/step/PostStore.java:91` (`Post` bez setterów, `api/posts/Post.java:127-144`); jedyne `UPDATE` to 7 metod `api/pipeline/state/PostTransitions.java`: `claim` `:70`, `record` `:91`, `release` `:118`, `releaseFailed` `:131`, `countTechnicalFailure` `:149`, `releaseAll` `:161`, `releaseOrphans` `:172` (wspólne `RELEASE_SET` `:42-43`, `COUNT_FAILURE` `:50`). `RunRecorder` tylko czyta `posts`.
- **Luki testów przejść** (`test/posts/PostRepositoryIT.java`): żaden test nie sprawdza `updated_at` ani tego, że kolumny ustawiane raz przetrwają przejście; `record` sprawdzony tylko z wynikiem `failed` (`:158`); `release` tylko na ścieżce `false` (`:141`). Kolumna V10 `technical_failures` jest pilnowana tylko dzięki dopisanemu `aStoredResultResetsTheTechnicalFailures`.
- **Wzorzec testu na bazie:** `@SpringBootTest(webEnvironment = NONE, properties = MigrateCommand.ENABLE_FLYWAY)` + `@Import(TestcontainersConfiguration.class)` (`test/MigrationsIT.java:27-29`); ten sam zestaw właściwości współdzieli kontekst i kontener. `PostRepositoryIT` dodaje `@Transactional` (wycofanie po teście) i pomocniki stanu przez JDBC (`:363-416`).

## Desired End State

- `test/persistence/ClosedSetsIT` jest zielony i czerwienieje, gdy: (a) stała enumu z rejestru nie ma swojej wartości w `CHECK` kolumny, (b) `CHECK` ma wartość, której enum nie ma, (c) encja dostaje atrybut-enum nieobecny w rejestrze, (d) migracja dodaje jednokolumnowy `CHECK` z listą wartości nieobecny w rejestrze. Komunikat nazywa tabelę.kolumnę, enum i różnicę w obie strony.
- `test/pipeline/state/PostColumnsIT` jest zielony i czerwienieje, gdy: (a) `posts` dostaje kolumnę bez klasyfikacji, (b) któreś z 7 przejść zostawia kolumnę stanu z inną wartością niż oczekiwana w macierzy, (c) przejście zmienia kolumnę ustawianą raz, (d) przejście, które zmienia wiersz, nie przesuwa `updated_at`.
- Opisy pakietów (`api/sources/CLAUDE.md`, `api/llm/CLAUDE.md`, `api/agents/assessor/CLAUDE.md`, `api/pipeline/CLAUDE.md`) i `REPOSITORY_GUIDELINES.md` nazywają test, który pilnuje każdej reguły.
- Weryfikacja: `cd szczur-radar-api && ./mvnw -B verify` zielone; celowe złamanie każdej z reguł daje czerwony test z czytelnym komunikatem.

### Key Discoveries:

- `pg_get_constraintdef` normalizuje `IN` do trzech postaci: wielowartościowe `CHECK ((stage = ANY (ARRAY['stored'::text, 'assessing'::text, …])))`, **jednowartościowe** `CHECK ((source = 'wykop'::text))` (dotyczy `ck_posts_source`, `ck_raw_payloads_source`, `ck_notifications_kind`, `ck_raw_payloads_content_encoding`, `ck_raw_payloads_media_type`) i tablicowe `CHECK ((sources <@ ARRAY['wykop'::text]))` (`ck_pipeline_runs_sources`, `mig/V5…:8-10`).
- `CHECK`-i złożone też zawierają literały enumów (`ck_posts_stage_attempts` `mig/V3…:116-121`, `ck_posts_assessing_owner` `:122`, `ck_assessments_outcome` `:165-167`, `ck_pipeline_runs_range` `mig/V4…:18-22`, `ck_pipeline_runs_requested_posts` `mig/V3…:35`, `ck_pipeline_runs_interruption_status` `mig/V8…:17-18`, `ck_notifications_run` `mig/V9…:18`) — nie są listą wartości jednej kolumny i strażnik ich nie liczy.
- Kolumny nullable (`failure_class`, `provider`, `channel`, `service_tier`, `reasoning_effort`, `output_mode`, `interruption_cause`) mają `x IN (...)` — `NULL` przechodzi; porównujemy tylko zbiory wartości, nie `NULL`.
- `PostTransitions` wiąże wartości z enumów (`stages()` `:190-194`), a `record` dostaje gotowy `Assessment` — `PostRepositoryIT:158-165` pokazuje, jak go zbudować (`ContractFixtures.validInput()`, pomocnik `failed(...)`).
- `updated_at` w `UPDATE` to `now()` = czas początku transakcji; w teście `@Transactional` byłby równy wartości ustawionej w tej samej transakcji — patrz Critical Implementation Details.

## What We're NOT Doing

- **R-19 / wiersz 6 destylacji:** żadnego `CHECK` ani triggera broniącego przejść stanu przebiegu w bazie (`UPDATE … SET status='running'` na `succeeded` dalej przejdzie na poziomie bazy), żadnej migracji. Wiersz 6 destylacji (`context/domain/domain-distillation.md:236`) zostaje otwarty.
- **Strażnik kolumn `pipeline_runs` / `pipeline_run_steps`** (ten sam podział JPA-insert/SQL-update w `RunRecorder`) — poza outcome slice'a.
- **`CHECK`-i złożone** (lista w Key Discoveries): nie sprawdzamy, czy nowa stała ma w nich regułę (np. nowy `AssessmentResult.Status` odrzuciłby `ck_assessments_outcome`). To decyzja semantyczna autora nowej stałej, nie członkostwo w zbiorze.
- **Enumy w JSONB** (`assessments.input/output/violations`) i stałe wersji bez `CHECK` (`prompt_version`, `contract_version`, `model`) — pilnują ich `ContractModelTest` / `ContractSchemaTest` albo nie są zbiorem zamkniętym w bazie.
- **Zmiany w kodzie produkcyjnym, schemacie, `MigrationsIT`** (nie ma nowej migracji, lista wersji 1–11 bez zmian) i w istniejących testach `PostRepositoryIT` (nowy test je uzupełnia, nie zastępuje).
- **Weryfikacja w PR env i sekcja w `deploy-plan.md`** — zmiana jest wyłącznie testowa, nie zmienia zachowania wdrożenia; dowodem jest CI.
- **Edycja `context/domain/*`** — destylacja jest diffowana przy następnym uruchomieniu, nie poprawiana ręcznie.

## Implementation Approach

Każdy strażnik to jeden test integracyjny na prawdziwym Postgresie po migracjach, z jawnie zapisanym oczekiwaniem (rejestr / klasyfikacja + macierz) i kontrolą kompletności, która czerwienieje, gdy rzeczywistość (metamodel JPA, `pg_constraint`, `information_schema`) ma coś, czego oczekiwanie nie zna. Dzięki temu nowy enum, nowy `CHECK` albo nowa kolumna nie przechodzą po cichu — test wymusza dopisanie wiersza, a dopisanie wiersza wymusza decyzję. Komunikaty błędów mówią, co dopisać i gdzie (migracja vs rejestr vs `PostTransitions`). Fazy są niezależne; każda kończy się zielonym `verify` i własnym commitem.

## Critical Implementation Details

- **Parser `CHECK`:** dopasowanie musi obejmować całą definicję (zakotwiczone), inaczej złapie `CHECK`-i złożone z literałami. Trzy dozwolone postacie z Key Discoveries; wartości wyciągane z literałów `'…'::text`. Kontrola kompletności po stronie bazy bierze `pg_constraint` z `contype = 'c'` dla tabel schematu `public` (bez tabel Spring Session i `flyway_schema_history`, które nie mają takich `CHECK`).
- **`updated_at` w `PostColumnsIT`:** `now()` w Postgresie to czas początku transakcji, więc test nie może być `@Transactional` z przejściem w tej samej transakcji co przygotowanie wiersza. Przygotowanie stanu ustawia `updated_at` jawnie na przeszłość (np. `now() - interval '1 day'`), a asercja sprawdza `updated_at > ` tej wartości. Sprzątanie bez `@Transactional`: każdy scenariusz tworzy własny post i przebieg (unikalne id), więc scenariusze się nie widzą; ewentualnie wspólne sprzątanie po teście.

## Phase 1: Strażnik zamkniętych zbiorów

### Overview

`ClosedSetsIT` porównuje każdą parę enum → kolumna z aktualnym `CHECK`-iem i pilnuje, żeby rejestr par był kompletny względem encji i bazy. Dopiski w opisach pakietów, które dziś mówią „dodaj wartość do `CHECK` migracją” i „stałe są append-only”.

### Changes Required:

#### 1. Test zbiorów zamkniętych

**File**: `test/persistence/ClosedSetsIT.java`

**Intent**: Jeden test z jawnym rejestrem 22 par i trzema kontrolami: równość zbiorów wartości z `CHECK`, kompletność względem atrybutów-enumów encji i kompletność względem jednokolumnowych `CHECK`-ów z listą wartości. To jest strażnik R-17 i reguły „nowe źródło = stała `Source` + migracja `CHECK`” przed S-27.

**Contract**:
- Adnotacje jak `MigrationsIT` (`webEnvironment = NONE`, `MigrateCommand.ENABLE_FLYWAY`, `TestcontainersConfiguration`), `JdbcTemplate` i `EntityManagerFactory` wstrzykiwane.
- Rejestr: lista wpisów (enum, funkcja wartości zapisywanej — ta sama referencja metody, której używa konwerter lub zapis: `X::value` / `X::id`; tabela, kolumna; opcjonalnie encja + nazwa atrybutu JPA, puste dla `pipeline_runs.sources`). 22 wpisy z Current State Analysis.
- `everyStoredEnumEqualsTheCheckOfItsColumn` (nazwa przykładowa): dla każdego wpisu zbiór `values()` przez funkcję wartości = zbiór wartości sparsowanego `CHECK` kolumny; błąd zbiera wszystkie rozjazdy naraz i dla każdego podaje `tabela.kolumna`, enum, „brak w CHECK: […]” i „brak w enumie: […]”, z podpowiedzią „nowa wartość → migracja Flyway rozszerzająca `CHECK`; stałych nie usuwa się (append-only)”.
- `everyEnumAttributeOfAnEntityIsInTheRegistry`: atrybuty z `EntityManagerFactory.getMetamodel().getEntities()` o typie enum (standardowe API JPA, bez wewnętrznego API Hibernate) = atrybuty z rejestru; błąd nazywa encję i atrybut do dopisania.
- `everyValueListCheckIsInTheRegistry`: zbiór `(tabela, kolumna)` z jednokolumnowych `CHECK`-ów z listą wartości w `pg_constraint` = zbiór z rejestru; błąd nazywa constraint i mówi „kolumna zbioru zamkniętego musi mieć enum (lekcja: zamknięte zbiory w enumach)”.
- Parser `CHECK` jako prywatna metoda testu (trzy postacie, Critical Implementation Details); ma własny mały test w tym samym pliku na trzech dzisiejszych przykładach (`ck_posts_stage`, `ck_posts_source`, `ck_pipeline_runs_sources`), żeby zmiana formatu Postgresa nie dała „pustego zbioru = zielono”. Pusty sparsowany zbiór dla wpisu z rejestru to błąd.

#### 2. Opisy pakietów

**File**: `api/sources/CLAUDE.md` (sekcja „Adding a source”, krok 1, `:30-33`)

**Intent**: Dopisać, że `persistence/ClosedSetsIT` czerwienieje, gdy stała `Source` nie ma migracji (albo migracja nie ma stałej) w którejś z trzech kolumn.

**Contract**: jedno zdanie w kroku 1.

**File**: `api/llm/CLAUDE.md` (`:118-122`) i `api/agents/assessor/CLAUDE.md` (`:23-25`)

**Intent**: Przy regule append-only dopisać, że równość stałych z `CHECK`-iem kolumny pilnuje `persistence/ClosedSetsIT`.

**Contract**: po jednym zdaniu przy istniejącej regule.

**File**: `REPOSITORY_GUIDELINES.md` (sekcja Project Structure, zdanie „Closed value sets (roles, error codes, command names, CLI exit codes and options) are enums, not free strings.”) i `szczur-radar-api/README.md` (wiersz pakietu `persistence`, `:32`)

**Intent**: Dopisać, że enum zapisywany w bazie ma `CHECK` swojej kolumny równy swoim wartościom, pilnowany przez `ClosedSetsIT` (nowy enum → wpis w rejestrze testu).

**Contract**: jedno zdanie w każdym pliku; `AGENTS.md` nie jest zmieniany (wskazuje na `REPOSITORY_GUIDELINES.md`).

### Success Criteria:

#### Automated Verification:

- Pełna weryfikacja z Testcontainers zielona: `cd szczur-radar-api && ./mvnw -B verify`
- `ClosedSetsIT` istnieje i przechodzi sam: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=ClosedSetsIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- Celowe złamanie — nowa stała bez migracji: tymczasowa stała `REDDIT` w `api/sources/Source.java` daje czerwony `ClosedSetsIT` z komunikatem nazywającym `posts.source`, `raw_payloads.source` i `pipeline_runs.sources`; po cofnięciu zielony (zmiana nie trafia do commita)
- Celowe złamanie — `CHECK` bez enumu: tymczasowa migracja testowa dodająca kolumnę z `CHECK (x IN ('a','b'))` (albo wartość `'reddit'` w `ck_posts_source` bez stałej) daje czerwony test kompletności / równości; po cofnięciu zielony (zmiana nie trafia do commita)
- Dopiski w opisach są na miejscu: `grep -l 'ClosedSetsIT' REPOSITORY_GUIDELINES.md szczur-radar-api/README.md szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/CLAUDE.md` wypisuje 5 plików

#### Manual Verification:

- Przegląd rejestru w `ClosedSetsIT`: 22 wpisy odpowiadają liście z Current State Analysis, a komunikat błędu z celowego złamania jest zrozumiały dla autora nowego źródła (mówi, którą migrację dopisać)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 2: Strażnik kolumn posta

### Overview

`PostColumnsIT` wymaga, żeby każda kolumna `posts` była sklasyfikowana, i dla każdego z 7 przejść `PostTransitions` sprawdza na poście z „brudnymi” wartościami pełny wynik: kolumny stanu z macierzy, kolumny ustawiane raz bez zmian, `updated_at` przesunięte. Dopisek w `api/pipeline/CLAUDE.md`.

### Changes Required:

#### 1. Test kolumn posta

**File**: `test/pipeline/state/PostColumnsIT.java`

**Intent**: Nowa kolumna `posts` nie przejdzie bez decyzji: albo jest ustawiana raz (i test pilnuje, że żadne przejście jej nie rusza), albo jest stanem próby (i macierz wymaga oczekiwanej wartości po każdym z 7 przejść). Domyka też luki `updated_at`, niezmiennych kolumn i `record` z wynikiem `ok`.

**Contract**:
- Adnotacje jak `MigrationsIT`, bez `@Transactional` (Critical Implementation Details); `PostTransitions`, `JdbcTemplate`, repozytoria potrzebne do przygotowania posta i przebiegu.
- Klasyfikacja: dwa zbiory nazw kolumn — `SET_ONCE` (15) i `STATE` (6, w tym `updated_at`). `everyPostColumnIsClassified`: kolumny `posts` z `information_schema.columns` = `SET_ONCE ∪ STATE`, zbiory rozłączne; błąd nazywa nową kolumnę i mówi „sklasyfikuj ją tutaj; jeśli to stan próby — dopisz jej wartość do macierzy i obsłuż ją w `PostTransitions`”.
- Macierz: dla każdego przejścia (scenariusza) stan wejściowy posta i przebiegu oraz oczekiwana wartość każdej kolumny `STATE` poza `updated_at` po przejściu. Stan wejściowy „brudny”: `technical_failures` > 0 i `last_technical_error` ustawione tam, gdzie przejście ma je zresetować albo zachować, `assessment_attempts` > 0 dla `CASE` w `RELEASE_SET`. Scenariusze (minimum): `claim` (stored → assessing, właściciel ustawiony, liczniki bez zmian); `record` z wynikiem `ok` (→ assessed, attempts bez zmian, właściciel `NULL`, `technical_failures` 0, error `NULL`); `record` z wynikiem `failed` (→ failed, attempts +1, reset); `release` (→ stored/failed wg attempts, właściciel `NULL`, liczniki bez zmian); `releaseFailed` (→ jak `release` + `technical_failures` +1 + error); `countTechnicalFailure` (stage i właściciel bez zmian, +1 + error); `releaseAll` i `releaseOrphans` (jak `release`). Kompletność macierzy: każdy scenariusz ma oczekiwanie dla każdej kolumny `STATE` poza `updated_at` — brak wpisu dla nowej kolumny stanu to błąd testu, nie pominięcie.
- Dla każdego scenariusza dodatkowo: wszystkie kolumny `SET_ONCE` po przejściu równe tym sprzed (porównanie całego wiersza, nie wybranych pól), `updated_at` większe od ustawionej przeszłej wartości, metoda zwraca „zmieniono” (`true` / niepusty `Optional` / liczba > 0).
- Każda z 7 publicznych metod `PostTransitions` ma co najmniej jeden scenariusz; test sprawdza to przez listę scenariuszy (nazwy metod), żeby nowa metoda przejścia też wymagała wpisu. Sposób pokrycia (refleksja po metodach publicznych vs jawna lista porównana z `PostTransitions.class.getDeclaredMethods()`) — do wyboru implementującego; warunek: nowa publiczna metoda bez scenariusza = czerwony test.

#### 2. Opis pakietu

**File**: `api/pipeline/CLAUDE.md` (wiersz `state/PostTransitions`, `:65`)

**Intent**: Dopisać, że kolumna dodana do `posts` musi być sklasyfikowana w `state/PostColumnsIT` (ustawiana raz albo stan próby z wartością po każdym przejściu), a nowe przejście dostaje tam scenariusz.

**Contract**: jedno zdanie w istniejącym wierszu tabeli.

### Success Criteria:

#### Automated Verification:

- Pełna weryfikacja z Testcontainers zielona: `cd szczur-radar-api && ./mvnw -B verify`
- `PostColumnsIT` istnieje i przechodzi sam: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=PostColumnsIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- Celowe złamanie — nowa kolumna: tymczasowa migracja `ALTER TABLE posts ADD COLUMN probe INTEGER NOT NULL DEFAULT 0` daje czerwony `everyPostColumnIsClassified` nazywający `probe`; po cofnięciu zielony (zmiana nie trafia do commita)
- Celowe złamanie — przejście gubi reset: usunięcie `technical_failures = 0` z `record` w `PostTransitions` daje czerwony scenariusz `record`; usunięcie `updated_at = now()` z `claim` daje czerwony scenariusz `claim`; po cofnięciu zielony (zmiany nie trafiają do commita)
- Dopisek w opisie pakietu: `grep -c 'PostColumnsIT' szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` wypisuje co najmniej `1`
- CI zielone na PR (wszystkie joby): `gh pr checks <PR> --watch`

#### Manual Verification:

- Przegląd macierzy w `PostColumnsIT`: oczekiwane wartości kolumn stanu po każdym przejściu zgadzają się z opisem przejść w `api/pipeline/CLAUDE.md:65,276-283,319-320` (żadne oczekiwanie nie „utrwala” błędu obecnego kodu)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Testing Strategy

### Unit Tests:

- Parser `CHECK` w `ClosedSetsIT` na trzech postaciach definicji (wielowartościowa, jednowartościowa, tablicowa) — w tym samym pliku, na prawdziwych definicjach z bazy.

### Integration Tests:

- `ClosedSetsIT`: 22 pary × równość zbiorów; kompletność względem metamodelu JPA i `pg_constraint`.
- `PostColumnsIT`: klasyfikacja 21 kolumn; 8+ scenariuszy przejść (7 metod, `record` ×2) z pełnym wierszem przed i po.

### Manual Testing Steps:

1. Przeczytać rejestr `ClosedSetsIT` i porównać z listą par w tym planie.
2. Przeczytać macierz `PostColumnsIT` i porównać z opisem przejść w `api/pipeline/CLAUDE.md`.
3. Przeczytać komunikat błędu z celowego złamania (`Source.REDDIT` bez migracji) — czy autor S-27 wie z niego, co zrobić.

## Performance Considerations

Oba testy dzielą kontekst Spring i kontener z `MigrationsIT` (ten sam zestaw właściwości), więc dokładają sekundy, nie nowy start Postgresa — o ile `PostColumnsIT` nie wymaga dodatkowych beanów zmieniających klucz kontekstu.

## Migration Notes

Brak migracji. Pierwsza migracja, przy której strażnik zadziała, to rejestracja Reddita w S-27 (`Source` + `CHECK` trzech kolumn).

## References

- Roadmapa: `context/foundation/roadmap.md:185-195` (S-23), issue #117
- Audyt po M-2: `context/changes/post-m2-audit/research.md:75` (E4)
- Destylacja domeny: `context/domain/domain-distillation.md:162` (R-17), `:169` (R-19), `:234` (wiersz 4), `:236` (wiersz 6, poza zakresem)
- Wzorzec testu: `test/MigrationsIT.java:27-32`, `test/posts/PostRepositoryIT.java:46-49,158-165,363-416`
- Przejścia: `api/pipeline/state/PostTransitions.java:42-50,70-178`; opis `api/pipeline/CLAUDE.md:65,273-283`
- Konwertery: `api/persistence/EnumValueConverter.java:16-42`, `api/persistence/converters/`, `api/user/RoleConverter.java:8`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Strażnik zamkniętych zbiorów

#### Automated

- [x] 1.1 Pełna weryfikacja z Testcontainers zielona: `cd szczur-radar-api && ./mvnw -B verify` — e7c294f
- [x] 1.2 `ClosedSetsIT` istnieje i przechodzi sam: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=ClosedSetsIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` — e7c294f
- [x] 1.3 Celowe złamanie — nowa stała bez migracji: tymczasowa stała `REDDIT` w `api/sources/Source.java` daje czerwony `ClosedSetsIT` z komunikatem nazywającym `posts.source`, `raw_payloads.source` i `pipeline_runs.sources`; po cofnięciu zielony (zmiana nie trafia do commita) — e7c294f
- [x] 1.4 Celowe złamanie — `CHECK` bez enumu: tymczasowa migracja testowa dodająca kolumnę z `CHECK (x IN ('a','b'))` (albo wartość `'reddit'` w `ck_posts_source` bez stałej) daje czerwony test kompletności / równości; po cofnięciu zielony (zmiana nie trafia do commita) — e7c294f
- [x] 1.5 Dopiski w opisach są na miejscu: `grep -l 'ClosedSetsIT' REPOSITORY_GUIDELINES.md szczur-radar-api/README.md szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/CLAUDE.md` wypisuje 5 plików — e7c294f

#### Manual

- [x] 1.6 Przegląd rejestru w `ClosedSetsIT`: 22 wpisy odpowiadają liście z Current State Analysis, a komunikat błędu z celowego złamania jest zrozumiały dla autora nowego źródła (mówi, którą migrację dopisać) — 29aad2e (przegląd implementacji 2026-10-28, potwierdzone przez użytkownika 2026-10-28)

### Phase 2: Strażnik kolumn posta

#### Automated

- [x] 2.1 Pełna weryfikacja z Testcontainers zielona: `cd szczur-radar-api && ./mvnw -B verify` — b522368
- [x] 2.2 `PostColumnsIT` istnieje i przechodzi sam: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=PostColumnsIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` — b522368
- [x] 2.3 Celowe złamanie — nowa kolumna: tymczasowa migracja `ALTER TABLE posts ADD COLUMN probe INTEGER NOT NULL DEFAULT 0` daje czerwony `everyPostColumnIsClassified` nazywający `probe`; po cofnięciu zielony (zmiana nie trafia do commita) — b522368
- [x] 2.4 Celowe złamanie — przejście gubi reset: usunięcie `technical_failures = 0` z `record` w `PostTransitions` daje czerwony scenariusz `record`; usunięcie `updated_at = now()` z `claim` daje czerwony scenariusz `claim`; po cofnięciu zielony (zmiany nie trafiają do commita) — b522368
- [x] 2.5 Dopisek w opisie pakietu: `grep -c 'PostColumnsIT' szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` wypisuje co najmniej `1` — b522368
- [x] 2.6 CI zielone na PR (wszystkie joby): `gh pr checks <PR> --watch` — b522368

#### Manual

- [x] 2.7 Przegląd macierzy w `PostColumnsIT`: oczekiwane wartości kolumn stanu po każdym przejściu zgadzają się z opisem przejść w `api/pipeline/CLAUDE.md:65,276-283,319-320` (żadne oczekiwanie nie „utrwala” błędu obecnego kodu) — 29aad2e (przegląd implementacji 2026-10-28, potwierdzone przez użytkownika 2026-10-28)
