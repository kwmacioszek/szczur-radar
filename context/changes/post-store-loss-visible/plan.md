# Post odrzucony przy zapisie nie znika po cichu (S-38) — plan implementacji

## Overview

Krok store przebiegu zakresu (`pipeline/step/PostStore`) traktuje dziś każde `DataIntegrityViolationException` przy zapisie pojedynczego posta jako „zapisany w międzyczasie przez inny przebieg”. Post odrzucony przez `CHECK`, `NOT NULL` albo błąd danych (np. U+0000 w `TEXT`) znika bez logu i licznika: `fetched` go liczy, `skipped` i `new_posts` nie, a przebieg może skończyć się `succeeded` (destylacja #2, R-06, R-01, D-15; audyt §6 D-15; FR-005, NFR `prd.md:275`; issue #148).

Po S-38 store odróżnia duplikat od odrzucenia przez ponowne odczytanie klucza posta. Odrzucony post:

- jest liczony jako `skipped` w przebiegu i w wierszu swojego źródła;
- daje powód „wymaga uwagi” `skipped`, a z nim dzwonek i `RUN_NEEDS_ATTENTION`;
- dostaje linię WARN i trafia do błędu przebiegu.

Równość „pobrane = nowe posty + duplikaty + pominięte” obowiązuje dla każdego przebiegu, którego krok store zakończył się `succeeded`, i każdego jego źródła. Pilnuje jej konstruktor typu kroku i sprawdzają ją testy.

## Current State Analysis

- **Połykanie wyjątku.** `PostStore.store` (`szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/PostStore.java:61-83`) zapisuje nowe posty paczkami po 500, każdą w jednej transakcji (najpierw RAW, potem posty). Paczka, która rzuci `DataIntegrityViolationException`, jest zapisywana jeszcze raz post po poście (:71-80). Tam każdy taki wyjątek ląduje w `catch (DataIntegrityViolationException duplicate)` z komentarzem „Stored meanwhile by another run” (:76-78), bez rozróżnienia przyczyny.
- **Test to utrwala.** `PostStoreTest.aPostStoredMeanwhileByAnotherRunIsNotNew` (`src/test/java/.../pipeline/step/PostStoreTest.java:85-93`) rzuca wyjątek z tekstem `"uq_posts_source_item"` i oczekuje pustego `newPostIds`. Przy tym `findKeys` zwraca pustą listę, więc odrzucony post nie jest odróżniany od duplikatu.
- **Ograniczenia `posts` i `raw_payloads`, które może naruszyć nowy post:**
  - `ck_posts_channel` (`channel <> ''`, `V13__posts_channel.sql:13`), a `SourcePost` pustego kanału nie odrzuca (`sources/SourcePost.java:32-39`);
  - `ck_posts_item_type`, `ck_posts_source` (V4, V12);
  - `ck_posts_content_hash` (V3);
  - `CHECK`-i `raw_payloads` (V3:74-77, V12:26-27);
  - klucze obce `first_run_id`, `raw_payload_id` (V3).

  Hibernate zamienia błędy danych z klasy SQLSTATE 22 na `DataException`, a Spring na `DataIntegrityViolationException` bez nazwy ograniczenia. U+0000 do bazy nie dociera: konstruktor `Post` usuwa je z kanału, tytułu, treści, autora, sluga, adresu i tagów (`posts/Post.java:141-148`). W `sourceItemId` (:140) zostaje, ale wtedy wywraca się już odczyt kluczy w `storedKeys` (przed zapisem), czyli krok kończy się głośno. RAW to gzip w `BYTEA` (V3:69), więc NUL go nie odrzuci.
- **Wstawienie odracza się do commita.** `Post.id` ma `@GeneratedValue(strategy = GenerationType.UUID)` (`posts/Post.java:48-49`), więc `INSERT` idzie przy flushu na końcu `transaction.execute`, jako batch JDBC (`hibernate.jdbc.batch_size`). Wyjątek wychodzi z commita, przetłumaczony przez `JpaTransactionManager`/`HibernateJpaDialect`. Przyczyną bywa `BatchUpdateException` z `SQLException` w `getNextException()`.
- **Liczniki.** `pipeline_runs.fetched` i `skipped` oraz wiersz źródła `pipeline_run_sources` (`fetched`, `skipped`, `ck_pipeline_run_sources_counts: skipped <= fetched`, V12:46) zapisuje `RunRecorder.source` w kroku fetch (`state/RunRecorder.java:140-160`). Źródła liczą `fetched = posty + skipped` (`sources/wykop/WykopFetcher.java:47-56`, `sources/reddit/RedditFetcher.java:47-56`). `new_posts` dopisuje `RunSequence.store` przez `recorder.count(Counters.stored(n))` po zamknięciu kroku (`flow/RunSequence.java:113-118`). Licznika duplikatów nie ma nigdzie, więc duplikaty to dziś reszta `fetched − new − skipped`.
- **Co czyta `skipped`** (bez zmian po S-38):
  - `PipelineRun.attentionReasons` → `AttentionReason.SKIPPED` przy `succeeded`/`partial` (`run/PipelineRun.java:321-347`, :340), czyli dzwonek, strona przebiegu i `RUN_NEEDS_ATTENTION` ze szczegółem `skipped N` (`notifications/RunNotifications`);
  - `NightSummary`: linia nocy sumuje `skipped` przebiegów, a linia źródła bierze `skipped` z wiersza źródła;
  - SPA: „— pominięte: N” (`szczur-radar-web/src/runs.ts:75-81`) i „Wynik per źródło”.

  Status przebiegu z pominiętymi zostaje `succeeded` (S-08).
- **Błąd przebiegu.** Składa go `RunSequence.assess`: podsumowanie źródeł, które nie wyszły (`FetchedBatch.summary`), potem błąd oceny, łączone ` | ` (`flow/RunSequence.java:138-150`). `RunRecorder.error` nadpisuje i przycina tekst (`state/RunRecorder.java:178-183`). SPA pokazuje `run.error` przy każdym statusie (`szczur-radar-web/src/views/RunDetailView.vue:281-283`, `AdminView.vue:441`).
- **Treść błędu Postgresa zawiera wartości wiersza** (`Failing row contains (…)`), czyli treść posta. Reguła `pipeline/CLAUDE.md`: w logach nie ma treści posta ani sekretu, a szczegół zdarzenia nie zawiera komunikatu wyjątku.
- **Strażnik `RunEndingTest`** (`src/test/java/.../pipeline/state/RunEndingTest.java:46-61`): każda publiczna metoda `RunRecorder` musi być w `ENDS` albo `NOT_ENDING` z powodem.
- **Testy IT** działają po kolei: Failsafe bez równoległości (`pom.xml`), jeden kontener Postgresa (`TestcontainersConfiguration`). `PipelineRunIT` uruchamia scenariusze w losowej kolejności i przed każdym robi `TRUNCATE` (`pipeline/CLAUDE.md`, Tests).

## Desired End State

- **Duplikat a odrzucenie.** Zapis posta, który się nie uda z `DataIntegrityViolationException`, kończy się jednym z dwóch wyników. Duplikat: post jest już w bazie po ponownym odczytaniu klucza. Odrzucenie: posta nie ma; post nie jest nowy, jego RAW się wycofał, a reszta paczki jest zapisana. Inne wyjątki kończą krok jak dziś.
- **Odrzucony post jest widoczny:**
  - jako `skipped` w `pipeline_runs` i w wierszu `pipeline_run_sources` swojego źródła;
  - w powodzie `skipped` (dzwonek, `RUN_NEEDS_ATTENTION`), bez zmiany statusu przebiegu;
  - w linii WARN: `Run <id>: the database rejected <source> <item type> <source id> at store (<cause>, <ExceptionSimpleName>); counted as skipped`, gdzie `<cause>` to nazwa ograniczenia albo `SQLState <kod>`, bez komunikatu wyjątku;
  - w błędzie przebiegu, w kolejności `<źródła> | <store> | <ocena>`: `store rejected N post(s), counted as skipped: <source> <item type> <source id> (<cause>); …`. Lista ma najwyżej 10 postów, dalej `; and M more`.
- **Linia INFO na każde źródło** po kroku store: `Run <id> store <source>: <P> post(s), new <A>, duplicates <D>, rejected <R>`.
- **Równość z roadmapy** zachodzi w każdym przebiegu zakresu, którego krok store zakończył się `succeeded`, i dla każdego jego źródła: `fetched = new + duplicates + skipped` (wiersz źródła: `fetched`, `skipped`; `new`, `duplicates` z linii INFO). `StoredBatch` nie da się zbudować z liczbami, które się nie sumują.
- **Weryfikacja:** `./mvnw -B verify` zielone. `PostStoreIT` i nowy scenariusz `PipelineRunIT` pokazują odrzucenie przez prawdziwe ograniczenie Postgresa. PR env (faza 3) pokazuje to samo na wdrożonym obrazie z prawdziwym Redditem.

### Key Discoveries:

- `PostStore.java:76-78` to jedyne miejsce połykania wyjątku. Przebieg RETRY/POSTS nie zapisuje postów, więc go to nie dotyczy.
- `fetched = posty + skipped` per źródło (`WykopFetcher:56`, `RedditFetcher:56`). Wystarczy więc dopisać odrzucone do `skipped`, żeby równość dotyczyła też wiersza źródła.
- Wzorzec warunkowego zapisu „przebieg i wiersz źródła w jednym poleceniu” już istnieje: `RunRecorder.source` (`WITH run AS (UPDATE pipeline_runs … RETURNING id) INSERT INTO pipeline_run_sources …`, :143-147).
- Wzorzec składania błędu przebiegu z części ` | `: `RunSequence.assess` (:141-148).
- Wzorzec przechwytywania linii logu i sprawdzania `RUN_NEEDS_ATTENTION` w scenariuszach: `PipelineRunIT` (scenariusze „some skipped elements”, „Reddit down”).

## What We're NOT Doing

- **Bez nowego licznika, kolumny, migracji i powodu** (np. `store_rejected`). Odrzucenie liczy się jako `skipped` (decyzja). `rules.json`, kontrakt SPA (`src/contract/`), próbki, typy TS, `runs.ts` i format `NightSummary` zostają bez zmian. Konsekwencja: w SPA odrzucenie wygląda jak pominięcie przy mapowaniu („pominięte: N”), a przyczyna jest w błędzie przebiegu i w logu.
- **Bez trwałego licznika duplikatów.** Duplikaty są w `StoredBatch` i w linii INFO, a nie w bazie ani w API (decyzja). Równości nie da się więc sprawdzić dla starego przebiegu w bazie po fakcie, tylko w logu.
- **Status źródła się nie zmienia.** Źródło, którego wszystkie posty baza odrzuciła, zostaje `succeeded` (wynik fetch). „Dni bez danych” liczą wtedy dzień jako pokryty (decyzja, przyjęty koszt).
- **Bez zmiany statusu przebiegu.** Odrzucony post nie robi z przebiegu `partial` ani `failed`, tak jak każdy pominięty element (S-08).
- **Bez ponownej próby zapisu odrzuconego posta.** Wraca dopiero przy kolejnym przebiegu zakresu obejmującym jego dzień, po naprawie przyczyny (runbook w `deploy-plan.md`).
- **Bez zmian po stronie Javy przed bazą.** Wyjątki z konstruktorów (`SourcePost`, `Post`: `NullPointerException`, walidacja) już dziś kończą krok i przebieg `failed`, więc nie są cichą utratą. To samo dotyczy U+0000 w `sourceItemId`: wywraca odczyt kluczy w `storedKeys` jeszcze przed zapisem.
- **Bez równości i tekstu `store rejected …` w przebiegu przerwanym przy store.** Gdy krok store się nie skończy albo przebieg zostanie przerwany między `count` a `storeRejected`, `new_posts` i odrzucenia nie są zapisane (tak jak dziś `new_posts`). Tekst `store rejected …` trafia do błędu przebiegu w `assess`, więc przebieg, któremu rzuci krok select albo assess, ma błąd tego kroku. `skipped` z odrzuceniami jest wtedy już zapisane.
- **Bez edycji `context/domain/domain-distillation.md`.** Status R-06/D-15 zmieni kolejne uruchomienie destylacji.
- **Bez zmian w SPA** (kod i teksty).

## Implementation Approach

Zmiana idzie od najniższej warstwy w górę.

1. Krok store podaje prawdę o każdym poście: nowy, duplikat albo odrzucony, z przyczyną. Zapisuje to typ `StoredBatch`, którego konstruktor pilnuje równości.
2. `RunSequence` zamienia odrzucenia na liczniki: nowa warunkowa metoda `RunRecorder` dopisuje je do przebiegu i do wiersza źródła. Dodaje też ich opis do błędu przebiegu.

Reszta widoczności (powód `skipped`, dzwonek, `RUN_NEEDS_ATTENTION`, podsumowanie nocy, SPA) wynika z istniejących liczników bez zmian w kodzie. Dowodem jest odrzucenie przez prawdziwe ograniczenie Postgresa: `PostStoreIT` na ograniczeniach dzisiejszego schematu, `PipelineRunIT` i PR env na ograniczeniu dodanym na czas testu, czyli dokładnie przypadku „przyszłe ograniczenie albo nowe źródło” z roadmapy.

## Critical Implementation Details

- **Kolejność i źródło wyjątku.** Wyjątek przychodzi z commita `transaction.execute`, nie z `posts.save` (UUID z Hibernate, `INSERT` przy flushu, batch JDBC). Przyczynę trzeba więc szukać w łańcuchu `getCause()` razem z `SQLException.getNextException()`:
  - najpierw nazwa ograniczenia z `org.hibernate.exception.ConstraintViolationException#getConstraintName()`, jeśli jest;
  - potem pierwszy niepusty `SQLState`;
  - na końcu `unknown`.

  Hibernate 7.2 (`PostgreSQLDialect`) wyjmuje nazwę ograniczenia z komunikatu Postgresa dla 23505, 23514 i 23503, a Spring zostawia ten wyjątek jako przyczynę. `PostStoreIT` przypina, że `CHECK` podaje nazwę także przy batchu. Bez importu klas sterownika: pgjdbc ma w `pom.xml` zakres `runtime`, więc `PSQLException` się nie skompiluje. Gdy Hibernate nie poda nazwy, przyczyną jest `SQLState`.
- **Ponowne odczytanie klucza** (`findKeys(source, List.of(sourceItemId))` zawiera `PostKey(itemType, sourceItemId)`) idzie po wycofaniu transakcji posta, poza nią. Gdy samo rzuci, krok kończy się wyjątkiem jak dziś.
- **Bez komunikatu wyjątku.** Linia WARN nie dostaje obiektu wyjątku ani jego komunikatu, bo zawierają wartości wiersza, czyli treść posta. Podaje tylko przyczynę i `getClass().getSimpleName()`.

## Phase 1: Store odróżnia duplikat od odrzucenia

### Overview

`PostStore` mówi o każdym poście, czy jest nowy, czy jest duplikatem, czy baza go odrzuciła, i z jakiego powodu. Nic jeszcze nie trafia do liczników przebiegu: robi to faza 2.

### Changes Required:

#### 1. Typy kroku

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunFlow.java`

**Intent**: `StoredBatch` niesie, co krok store zrobił z postami każdego źródła i które posty baza odrzuciła, tak żeby `RunSequence` mógł je policzyć i opisać, a równość była zapisana w typie.

**Contract**:

- **`StoredBatch(UUID runId, List<UUID> newPostIds, List<SourceStored> sources, List<RejectedPost> rejected)`.** Kopie list. `sources` są w kolejności `Source`, tylko źródła z postami.
  - `rejectedBySource()`: `Map<Source, Integer>` (kolejność `Source`, tylko z odrzuceniami).
  - `rejectedSummary()`: `Optional<String>`, `store rejected N post(s), counted as skipped: <source> <item type> <source id> (<cause>); …`. Lista ma najwyżej `REJECTED_LISTED = 10` postów, dalej `; and M more`; `Optional.empty()` bez odrzuceń. Wartości `source.value()` i `itemType` (wartość enuma w API: `post`/`link`/`comment`).
- **`SourceStored(Source source, int posts, int newPosts, int duplicates, int rejected)`.** Konstruktor rzuca `IllegalArgumentException`, gdy `posts != newPosts + duplicates + rejected` albo gdy któraś liczba jest ujemna. Duplikat to post już zapisany, zapisany w międzyczasie albo powtórzony w tym samym pobraniu.
- **`RejectedPost(Source source, ItemType itemType, String sourceItemId, String cause)`.**
- Javadoc `StoredBatch` i `FetchedBatch.skipped` mówią, że odrzucone przy zapisie są dopisywane do `skipped` dopiero po kroku store (faza 2).

#### 2. Krok store

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/PostStore.java`

**Intent**: Gdy zapis pojedynczego posta się nie uda, kod sprawdza jego klucz w bazie: jest, to duplikat, nie ma, to odrzucenie. Krok liczy duplikaty i odrzucenia na źródło, loguje każde odrzucenie i podsumowanie per źródło.

**Contract**:

- **Bez zmian:** `store(FetchedBatch) → StoredBatch` oraz paczki, checkpointy i kolejność RAW → posty.
- **Duplikaty per źródło:** posty odfiltrowane przez `storedKeys` albo powtórzone w pobraniu (`stored.add` zwraca `false`) plus posty, których zapis się nie udał, a klucz po ponownym odczycie jest w bazie.
- **Odrzucenie:** zapis się nie udał z `DataIntegrityViolationException` i klucza nie ma. Post trafia do `rejected` z przyczyną (Critical Implementation Details) i daje WARN `Run <id>: the database rejected <source> <item type> <source id> at store (<cause>, <SimpleName>); counted as skipped`, bez wyjątku jako argumentu loggera.
- **Na końcu** jedna linia INFO na każde źródło z postami: `Run <id> store <source>: <P> post(s), new <A>, duplicates <D>, rejected <R>`.
- **Javadoc klasy** opisuje trzy wyniki i to, że wyjątek inny niż `DataIntegrityViolationException` kończy krok.

#### 3. Testy jednostkowe

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/step/PostStoreTest.java`

**Intent**: Koniec utrwalenia „każde naruszenie to duplikat”. Testy przypinają oba wyniki i równość.

**Contract**:

- `aPostStoredMeanwhileByAnotherRunIsNotNew`: ponowny `findKeys` zwraca klucz, więc duplikat, a `rejected` jest puste.
- Nowy test: ponowny `findKeys` bez klucza, więc odrzucenie. W `rejected` są źródło, typ, id i przyczyna, posta nie ma w `newPostIds`, jest jedna linia WARN bez komunikatu wyjątku (linie logu przechwytywane jak w innych testach jednostkowych pakietu).
- `aChunkThatHitsAPostStoredMeanwhileIsStoredPostByPost` rozszerzony o paczkę z jednym duplikatem i jednym odrzuceniem: pozostałe posty zapisane raz.
- Post powtórzony w pobraniu liczy się jako duplikat.
- `rejectedSummary` z 12 odrzuceniami wymienia 10 i kończy się `; and 2 more`.
- `SourceStored` z liczbami, które się nie sumują, rzuca.
- Przyczyna bez nazwy ograniczenia: `DataIntegrityViolationException` z przyczyną Hibernate `DataException` (SQLState `22021`) daje `SQLState 22021`, a bez żadnej przyczyny `unknown`.
- W każdym teście: `posts` każdego źródła = `newPosts + duplicates + rejected`.
- Wyjątki w testach mają przyczynę taką jak z commita: `DataIntegrityViolationException` z Hibernate `ConstraintViolationException` (nazwa, SQLState), a nie goły wyjątek z tekstem jak dziś (`PostStoreTest:86-133`).

#### 4. Test na Postgresie

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/step/PostStoreIT.java` (nowy)

**Intent**: Dowód na prawdziwej bazie, że wyjątek z commita jest `DataIntegrityViolationException`, przyczyna ma nazwę ograniczenia, a duplikat i odrzucenie się nie mylą.

**Contract**: `@SpringBootTest` z `TestcontainersConfiguration`; przebieg do `first_run_id` wstawiony SQL-em jak w innych IT; dane sprzątane po każdym teście. Scenariusze:

- **(a) pusty kanał w paczce trzech postów.** Wynik `rejected` z przyczyną `ck_posts_channel`; pozostałe dwa posty zapisane dokładnie raz; liczba wierszy `raw_payloads` wzrosła o 2, czyli RAW odrzuconego się wycofał.
- **(b) prawdziwy duplikat.** Post już jest w tabeli, a `PostRepository` jest szpiegiem (`@MockitoSpyBean`, `doAnswer`), którego pierwsze `findKeys` zwraca pustą listę, więc `INSERT` trafia na `uq_posts_source_item`. Wynik: duplikat, nie odrzucenie.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe store przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest=PostStoreTest`
- Test store na Postgresie przechodzi: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=PostStoreIT -Dtest=PostStoreTest -Dsurefire.failIfNoSpecifiedTests=false`
- Cały backend przechodzi: `cd szczur-radar-api && ./mvnw -B verify`
- Kod nie łapie już wyjątku jako duplikatu bez sprawdzenia klucza: `! grep -n "catch (DataIntegrityViolationException duplicate)" szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/PostStore.java`

---

## Phase 2: Przebieg liczy i pokazuje odrzucone posty

### Overview

Odrzucenia z kroku store trafiają do `skipped` przebiegu i wiersza źródła oraz do błędu przebiegu. Od tego włączają się istniejące sygnały: powód `skipped`, dzwonek, `RUN_NEEDS_ATTENTION`, podsumowanie nocy i SPA. Dokumenty mówią prawdę o nowym zachowaniu.

### Changes Required:

#### 1. Zapis liczników

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/RunRecorder.java`

**Intent**: Jedna warunkowa operacja dopisuje odrzucone posty źródła do `skipped` przebiegu i do jego wiersza w `pipeline_run_sources`. Robi to w jednym poleceniu, tylko w trwającym przebiegu, zawsze oba zapisy albo żaden.

**Contract**:

- `public boolean storeRejected(UUID runId, Source source, int rejected)`: `true`, gdy zmieniła oba wiersze.
- W tym samym poleceniu przesuwa też `last_activity_at`, jak `count`.
- Wartości statusu i źródła wiązane z enumów (reguła pakietu).
- Bez wiersza źródła, którego nie powinno być przy postach tego źródła, nie zmienia nic i zwraca `false`. Wzór `RunRecorder.source` skopiowany wprost tego nie da: CTE modyfikujące dane w Postgresie wykonuje się zawsze, więc `skipped` przebiegu urosłoby i bez wiersza źródła. Dlatego `UPDATE pipeline_runs` w CTE ma warunek `AND EXISTS (SELECT 1 FROM pipeline_run_sources WHERE run_id = :id AND source = :source)` i zostaje pierwszy (wiersz przebiegu przed innymi, jak w `openStep`), a główny `UPDATE pipeline_run_sources … FROM run` zmienia wiersz źródła.
- Linia INFO `Run <id>: <source> store rejected <N>, counted as skipped`, tylko gdy zapisała.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/RunEndingTest.java`

**Intent**: Klasyfikacja nowej metody.

**Contract**: `NOT_ENDING` dostaje wpis `storeRejected` z powodem „adds the store's rejected posts to the skipped counters of a running run and its source”.

#### 2. Sekwencja przebiegu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunSequence.java`

**Intent**: Po kroku store, obok `count(stored(n))`, wywołuje `storeRejected` dla każdego źródła z `rejectedBySource()`. Błąd przebiegu dostaje `rejectedSummary()` między podsumowaniem źródeł a błędem oceny.

**Contract**:

- `store(FetchedBatch)` jak dziś, plus `storeRejected` po `count`, w kolejności `Source`, poza krokiem (jak `count`). Wyjątek zapisu kończy przebieg `failed` w `execute`, jak dziś przy `count`.
- `assess` dostaje `@Nullable StoredBatch` (`null` dla RETRY/POSTS) i składa błąd z części `fetched.summary()` (gdy `degraded`), `stored.rejectedSummary()` i `assessed.errorSummary()`, łączonych ` | `.
- Status przebiegu: reguła bez zmian (`status(...)`).
- Javadoc klasy i `assess` opisują trzy części błędu.

#### 3. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/flow/RunSequenceTest.java`

**Intent**: Przypina wywołania i tekst błędu.

**Contract**:

- `StoredBatch` z odrzuceniami dwóch źródeł: `storeRejected` dla każdego, w kolejności `Source`, po `count`.
- Błąd przebiegu `<źródła> | store rejected … | <ocena>`, gdy są wszystkie trzy części; tylko `store rejected …` przy czystym fetch i ocenie.
- Bez odrzuceń: żadnego `storeRejected`, a błąd jak dziś.
- Status `succeeded` przy samych odrzuceniach i czystej ocenie.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/RunRecorderIT.java`

**Intent**: Strażnicy nowej metody na Postgresie.

**Contract**:

- `storeRejected` w trwającym przebiegu dodaje do `pipeline_runs.skipped` i do `skipped` wiersza źródła oraz przesuwa `last_activity_at`.
- W przebiegu zakończonym nie zmienia nic i zwraca `false`.
- Bez wiersza źródła nie zmienia `pipeline_runs` i zwraca `false`.
- Inne źródło tego przebiegu zostaje bez zmian.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java`

**Intent**: Cały przebieg z postem odrzuconym przez ograniczenie, którego nie zna dzisiejszy kod, czyli przypadek „przyszłe ograniczenie”. Równość sprawdzana w kilku scenariuszach.

**Contract**:

- **Nowy scenariusz.** Na czas scenariusza `ALTER TABLE posts ADD CONSTRAINT ck_s38_rejects_one CHECK (source_item_id <> '<id jednego elementu z fixture Wykopu dnia>')`, usuwane w `finally`. Scenariusz stoi sam, bez `@Order`, i zostawia schemat jak go zastał. Przebieg zakresu Wykopu kończy się statusem z oceny (`succeeded` przy czystej ocenie), a do tego:
  - `skipped` przebiegu i wiersza `wykop` o 1 większe niż przy mapowaniu;
  - `attentionReasons` zawiera `skipped`;
  - `error` = `store rejected 1 post(s), counted as skipped: wykop <item type> <id> (ck_s38_rejects_one)`;
  - linia WARN odrzucenia, bez linii ERROR;
  - posta i jego RAW nie ma w bazie;
  - admin powiadomiony, `RUN_NEEDS_ATTENTION` podniesione ze szczegółem zawierającym `skipped 1` i `store rejected`, jak w scenariuszu „Reddit down”.
- **Równość:** w nowym scenariuszu, w pierwszym przebiegu zakresu, w „the same range again” (same duplikaty) i w scenariuszu dwóch źródeł, dla każdego źródła: `pipeline_run_sources.fetched = new + duplicates + pipeline_run_sources.skipped` (`new` i `duplicates` z linii INFO `store <source>`); a dla przebiegu: `fetched = new_posts + Σ duplicates + skipped`.

#### 4. Dokumenty

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: Pakiet opisuje nowe zachowanie tam, gdzie agent go szuka.

**Contract**:

- Wiersz `step/PostStore`: trzy wyniki i ponowne odczytanie klucza.
- Wiersz `state/RunRecorder`: `storeRejected`.
- Wiersz `flow/RunFlow`: `StoredBatch`, `SourceStored` z równością, `RejectedPost`.
- Wiersz `flow/RunSequence`: błąd w trzech częściach.
- „Log lines”: nowe WARN i INFO.
- Nowa reguła w „Rules”: post odrzucony przy zapisie to `skipped`, nigdy cichy, przebieg bez zmiany statusu (S-38).
- „Tests”: `PostStoreTest`, `PostStoreIT`, `RunRecorderIT`, nowy scenariusz i równość w `PipelineRunIT`.

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md`

**Intent**: Autor nowego źródła wie, że post, którego baza nie przyjmie, nie zginie, ale zostanie policzony jako `skipped` i oznaczony w przebiegu.

**Contract**: wiersz `SourceFetcher` (:16) i sekcja dodawania źródła: jedno zdanie o S-38.

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/AttentionReason.java`

**Intent**: Javadoc `SKIPPED` obejmuje odrzucone przy zapisie.

**Contract**: komentarz `SKIPPED`.

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Zdanie o pominiętych elementach (Deployment, akapit Watchdog: „`skipped` > 0: fetched, but they could not become a post”) obejmuje odrzucone przy zapisie.

**Contract**: dopisek w nawiasie „(also a post the database rejected at store, S-38)”.

**File**: `context/foundation/prd.md`

**Intent**: Doprecyzowanie NFR o sygnale dla operatora (`prd.md:275`), w miejscu i stylu poprzednich, z odwołaniem do FR-005.

**Contract**: nowy cytat `> Doprecyzowanie (<data>, S-38 post-store-loss-visible): …` na końcu listy doprecyzowań tego NFR. Treść:

- post, którego zapis odrzuca inne ograniczenie bazy niż klucz unikalny, jest liczony jako pominięty (w przebiegu i w wyniku źródła) i daje te same sygnały co pominięte elementy z S-08 (powiadomienie w aplikacji, `RUN_NEEDS_ATTENTION`, linia w podsumowaniu nocy), bez zmiany statusu;
- błąd przebiegu nazywa post i ograniczenie;
- dla każdego przebiegu pobrane = nowe + duplikaty + pominięte;
- odrzucony post wraca przy kolejnym przebiegu jego dnia po naprawie;
- szczegóły w `deploy-plan.md`, sekcja S-38.

**File**: `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Status S-38 jest ten sam w roadmapie i na jej obrazku (reguła `REPOSITORY_GUIDELINES.md`: każda zmiana `roadmap.md` aktualizuje renderer i SVG).

**Contract**: status S-38 w `roadmap.md` (tabela i sekcja wycinka) odpowiada etapowi zmiany. W `render_roadmap_svg.py` (:211) S-38 ma `progress`, póki roadmapa mówi `planning`/`in-progress`, bo renderer nie zna `planning`. Potem `python3 tools/render_roadmap_svg.py` odświeża `docs/visuals/roadmap.svg`.

**File**: `context/deployment/deploy-plan.md`

**Intent**: Krótki runbook S-38.

**Contract**: nowa sekcja `## S-38 post-store-loss-visible` z linią `**Status:** delivered by PR #<N>; production verification and closure in issue #148.` (lekcja o statusie). Opisuje:

- co znaczy linia WARN i `store rejected …` w błędzie przebiegu;
- co sprawdzić: ograniczenie z przyczyny, mapper źródła albo migracja;
- jak przywrócić post: po naprawie przebieg admina na dzień posta zapisuje go jako nowy;
- że operator nie ma nic do zrobienia po merge'u (bez nowych zmiennych i migracji), a weryfikacja produkcji to zwykła noc bez odrzuceń: `skipped` z mapowania i linie INFO `store` w logu API, wynik w issue.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe sekwencji i strażnika przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest='RunSequenceTest,RunEndingTest,PostStoreTest'`
- Strażnicy `storeRejected` na Postgresie przechodzą: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=RunRecorderIT -Dtest=RunEndingTest -Dsurefire.failIfNoSpecifiedTests=false`
- Scenariusz z odrzuconym postem i równość przechodzą w `PipelineRunIT`: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=PipelineRunIT -Dtest=RunSequenceTest -Dsurefire.failIfNoSpecifiedTests=false`
- Cały backend przechodzi: `cd szczur-radar-api && ./mvnw -B verify`
- Testy SPA bez zmian przechodzą (kontrakt i reguły nienaruszone): `cd szczur-radar-web && npm test`
- Dokumenty wymieniają S-38: `grep -q "S-38" szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md && grep -q "S-38" context/foundation/prd.md && grep -q "## S-38" context/deployment/deploy-plan.md`

---

## Phase 3: Weryfikacja w PR env (agent)

### Overview

Wdrożony obraz w Railway PR env z prawdziwym Redditem i prawdziwym LLM pokazuje odrzucenie przez ograniczenie dodane na czas kontroli, a potem schemat wraca do stanu z migracji. Wszystkie kontrole wykonuje agent, z dowodami w komentarzu w issue #148.

### Changes Required:

#### 1. Kontrola w PR env

**File**: brak zmian w repo; dowody w komentarzu issue #148 i w PR.

**Intent**: Sprawdzić całą drogę na wdrożonym API: fetch → store → liczniki → powody → powiadomienie → strona przebiegu.

**Contract**: kroki agenta, w PR env `szczur-radar-pr-<N>`, nigdy na produkcji:

1. **CI zielone i PR env na commicie PR:** `gh pr checks <N>`; wdrożenie `szczur-radar-api` z tym commitem `SUCCESS`.
2. **Tymczasowe ograniczenie.** SQL idzie przez `railway ssh -s postgres -e szczur-radar-pr-<N> -- psql -U postgres -d railway -c "<SQL>"` (z `-p <project id>` w checkoucie niepołączonym z projektem, `deploy-plan.md`, sekcja S-11). Subreddit z `REDDIT_SUBREDDITS` PR env (`railway variables -s szczur-radar-api -e szczur-radar-pr-<N> --json`), zapisany tak jak w `posts.channel`: kanał to nazwa z payloadu, więc wielkość liter może się różnić. Agent bierze go z `SELECT DISTINCT channel FROM posts WHERE source = 'reddit'`, a gdy PR env nie ma jeszcze postów Reddita, z przebiegu admina na inny dzień przed tym krokiem. Znacznik czasu `T` z zegara bazy (`SELECT now()`). `created_at` ustawia zegar API (`@CreationTimestamp`), a przesunięcie zegarów względem odstępu do przebiegu jest pomijalne. `railway ssh -s postgres -e szczur-radar-pr-<N> -- psql -U postgres -d railway -c "ALTER TABLE posts ADD CONSTRAINT ck_s38_verify CHECK (source <> 'reddit' OR channel <> '<sub>' OR created_at < '<T>')"`. Istniejące wiersze i ich późniejsze zmiany przechodzą, bo `created_at` się nie zmienia; odrzucane są tylko nowe posty tego subredditu.
3. **Jednorazowe konto admina** według lekcji „W PR env agent sam zakłada jednorazowe konto admina…”: `create-user --email agent-post-store-loss-visible@example.com --role admin` przez `railway ssh`, link i hasło tylko w zmiennych powłoki, logowanie `curl` z nagłówkiem `Origin`, ciasteczka w scratchpadzie.
4. **Przebieg:** `POST /api/admin/pipeline-runs` z `{"from":<dzień>,"to":<dzień>,"sources":["reddit"]}` dla dnia, którego PR env jeszcze nie pobrał (D-2 albo starszego). Czekanie na koniec przez `GET /api/admin/pipeline-runs/{id}`.
5. **Dowody:**
   - `skipped` przebiegu i wiersza `reddit` w `sourceResults` > 0 i równe sumie: pominięte przy mapowaniu (`Y` z linii INFO `Run <id>: reddit succeeded: fetched X, skipped Y`) + liczba linii WARN `the database rejected reddit` tego przebiegu w `railway logs -s szczur-radar-api -e szczur-radar-pr-<N>`;
   - `attentionReasons` zawiera `skipped`;
   - `error` zaczyna się od `store rejected`;
   - równość `fetched = new + duplicates + skipped` dla `reddit` z linii INFO `store reddit`;
   - `SELECT count(*) FROM posts WHERE channel = '<sub>' AND created_at >= '<T>'` = 0;
   - powiadomienie przebiegu w `GET /api/notifications`;
   - brak linii ERROR przebiegu w logu.
6. **Strona przebiegu** `/admin/runs/<id>` przez `playwright-cli` z ciasteczkiem agenta (lekcja o `playwright-cli`): tekst błędu `store rejected` i „pominięte” widoczne, zrzut ekranu w scratchpadzie.
7. **Sprzątanie:** `ALTER TABLE posts DROP CONSTRAINT ck_s38_verify`; `\d posts` bez `ck_s38_verify`; plik ciasteczek usunięty.
8. **Komentarz w issue #148** z id przebiegu, liczbami i wynikiem kontroli. Bez linku, hasła i ciasteczka.

### Success Criteria:

#### Automated Verification:

- CI PR zielone i API w PR env wdrożone z commitem PR: `gh pr checks <N>` bez `fail`/`pending`, a `railway` pokazuje `SUCCESS` dla `szczur-radar-api` w `szczur-radar-pr-<N>`
- Przebieg admina z tymczasowym `ck_s38_verify` ma `skipped` > 0 na przebiegu i wierszu `reddit`, równe pominiętym przy mapowaniu (linia INFO `reddit succeeded: …, skipped Y`) plus liczbie linii WARN odrzucenia, `attentionReasons` z `skipped` i `error` od `store rejected` (`curl … /api/admin/pipeline-runs/<id> | jq -e`)
- Równość `fetched = new + duplicates + skipped` dla `reddit` z linii INFO `store reddit` i zero nowych postów subredditu w bazie od `T` (`psql -tAc`)
- Powiadomienie przebiegu jest w `GET /api/notifications`, a log API nie ma linii ERROR tego przebiegu
- Strona `/admin/runs/<id>` pokazuje `store rejected` i „pominięte” (`playwright-cli` snapshot, zrzut w scratchpadzie)
- `ck_s38_verify` usunięte (`psql -tAc "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_s38_verify'"` = 0), plik ciasteczek usunięty, komentarz z dowodami w issue #148

#### Manual Verification:

- Właściciel akceptuje brzmienie linii WARN i błędu przebiegu `store rejected …` (z komentarza w issue #148 i zrzutu strony przebiegu)

---

## Testing Strategy

### Unit Tests:

- `PostStoreTest`:
  - duplikat po ponownym odczycie klucza; odrzucenie bez klucza (WARN bez komunikatu wyjątku);
  - paczka z duplikatem i odrzuceniem; powtórzenie w pobraniu;
  - lista w `rejectedSummary` ucinana po 10;
  - przyczyna: nazwa ograniczenia, `SQLState 22021` z `DataException`, `unknown`;
  - równość w każdym teście; `SourceStored` odrzuca złe liczby.
- `RunSequenceTest`: `storeRejected` per źródło po `count`; trzy części błędu w kolejności; bez odrzuceń nic się nie zmienia; status bez zmian.
- `RunEndingTest`: `storeRejected` sklasyfikowane.

### Integration Tests:

- `PostStoreIT`: `ck_posts_channel` (nazwa ograniczenia, RAW wycofany, reszta paczki zapisana), prawdziwy `uq_posts_source_item` jako duplikat.
- `RunRecorderIT`: `storeRejected` tylko w trwającym przebiegu, oba wiersze albo żaden (także bez wiersza źródła).
- `PipelineRunIT`: przebieg z postem odrzuconym przez tymczasowe `CHECK` (liczniki, powód, błąd, WARN, powiadomienie, `RUN_NEEDS_ATTENTION`) oraz równość per źródło i per przebieg w czterech scenariuszach.

### Manual Testing Steps:

1. Przeczytać w issue #148 komentarz agenta z PR env (liczby, linie WARN, błąd przebiegu) i zrzut strony przebiegu.
2. Ocenić, czy brzmienie `store rejected …` i linii WARN mówi operatorowi, co sprawdzić.

## Performance Considerations

Ponowne odczytanie klucza to jedno zapytanie na post, którego zapis się nie udał, a to dzieje się tylko w ścieżce „post po poście” po nieudanej paczce, czyli rzadko. Zwykła noc bez naruszeń ma tyle samo zapytań co dziś. `storeRejected` to jedno polecenie na źródło z odrzuceniami.

## Migration Notes

Bez migracji i bez nowych zmiennych. Stare przebiegi w bazie się nie zmieniają: posty, które mogły zginąć przed S-38, nie są odtwarzane (nie ustalono, czy jakiekolwiek zginęły; destylacja, „Statusy nierozstrzygnięte”). Wycofanie zmiany to revert kodu, bez kroku na danych.

## References

- Roadmap: `context/foundation/roadmap.md`, S-38
- Destylacja: `context/domain/domain-distillation.md` (R-01, R-06, D-15, ranking #2)
- PRD: FR-005 (`prd.md:100`), NFR o sygnale dla operatora (`prd.md:275`)
- Issue: #148
- Kod: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/PostStore.java:61-83`, `flow/RunSequence.java:113-150`, `flow/RunFlow.java:140-147`, `state/RunRecorder.java:140-160`, `run/PipelineRun.java:321-347`
- Wzorce: `RunRecorder.source` (warunkowy zapis przebiegu i wiersza źródła), `RunSequence.assess` (błąd z części), `PipelineRunIT` („some skipped elements”, „Reddit down”)
- Lekcje: „W PR env agent sam zakłada jednorazowe konto admina…”, „Stronę sprawdza agent przez playwright-cli…”, „Kryterium, które agent sprawdzi poleceniem, zapisuj jako Automated…”, „Status zmiany w deploy-plan.md ma być prawdziwy także po merge'u…”

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Store odróżnia duplikat od odrzucenia

#### Automated

- [x] 1.1 Testy jednostkowe store przechodzą — 987c3fd
- [x] 1.2 Test store na Postgresie przechodzi — 987c3fd
- [x] 1.3 Cały backend przechodzi — 987c3fd
- [x] 1.4 Kod nie łapie już wyjątku jako duplikatu bez sprawdzenia klucza — 987c3fd

### Phase 2: Przebieg liczy i pokazuje odrzucone posty

#### Automated

- [x] 2.1 Testy jednostkowe sekwencji i strażnika przechodzą — 1881b78
- [x] 2.2 Strażnicy `storeRejected` na Postgresie przechodzą — 1881b78
- [x] 2.3 Scenariusz z odrzuconym postem i równość przechodzą w `PipelineRunIT` — 1881b78
- [x] 2.4 Cały backend przechodzi — 1881b78
- [x] 2.5 Testy SPA bez zmian przechodzą — 1881b78
- [x] 2.6 Dokumenty wymieniają S-38 — 1881b78

### Phase 3: Weryfikacja w PR env (agent)

#### Automated

- [x] 3.1 CI PR zielone i API w PR env wdrożone z commitem PR — d3e8bd3
- [x] 3.2 Przebieg admina z tymczasowym `ck_s38_verify` ma `skipped` > 0, `attentionReasons` z `skipped` i `error` od `store rejected` — d3e8bd3
- [x] 3.3 Równość dla `reddit` i zero nowych postów subredditu od `T` — d3e8bd3
- [x] 3.4 Powiadomienie przebiegu jest, a log API nie ma linii ERROR tego przebiegu — d3e8bd3
- [x] 3.5 Strona `/admin/runs/<id>` pokazuje `store rejected` i „pominięte” — d3e8bd3
- [x] 3.6 `ck_s38_verify` usunięte, ciasteczka usunięte, komentarz z dowodami w issue #148 — d3e8bd3

#### Manual

- [ ] 3.7 Właściciel akceptuje brzmienie linii WARN i błędu przebiegu
