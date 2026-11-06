# Strażnicy zapisu i końca przebiegu liczą zbiory z kodu (S-39) Implementation Plan

## Overview

CI ma czerwienieć w trzech sytuacjach, które dziś łapią tylko przypadkowe testy zachowania: konwerter enuma zapisuje inną wartość, niż dopuszcza `CHECK` kolumny (X1), złożony `CHECK` ma literał spoza enuma (X2), nowa metoda kończąca przebieg nie ma powiązanego powiadomienia (R-23). Zmiana dotyczy wyłącznie testów (`szczur-radar-api/src/test/`) i kilku zdań dokumentacji; kod produkcyjny i migracje zostają.

## Current State Analysis

- `ClosedSetsIT` liczy wartości enuma własną lambdą z `REGISTRY` (np. `RunKind::value`), a nie tym, co zapisuje konwerter z `persistence/converters/`. X1 (`RunKindConverter` → `RunKind::name`) zostawił `ClosedSetsIT` zielony; czerwone były dopiero `PipelineRunControllerIT` (`ck_pipeline_runs_kind`).
- Złożone `CHECK`i z literałem enuma (`ck_pipeline_runs_interruption_status`: `status = 'interrupted'`, `ck_pipeline_runs_requested_posts`, `ck_posts_assessing_owner`, `ck_pipeline_run_sources_error`) są w `NOT_A_VALUE_LIST` i pomijane po nazwie. X2 (`'interupted'` w nowej migracji) zostawił `ClosedSetsIT` zielony; czerwone były `PipelineStartupRecoveryIT`, `OrphanedRunIT`, `PipelineRunControllerIT`.
- Stan końcowy przebiegu zapisuje tylko `RunRecorder` (`pipeline/state/`): `finish`, `fail`, `interrupt`, `stall`, `interruptAll`, `interruptOrphans`. Każda ścieżka dziś woła `RunNotifications.runEnded` (`PipelineRunService` ok. `:270`, `:386`, `:480`, `:551`; `PipelineStartupRecovery:113`), ale nic nie czerwieni na nowej ścieżce (audyt §5).
- `RunSequence.finish` (`flow/RunSequence.java:163`) zapisuje koniec, a `runEnded` woła dopiero `PipelineRunService.execute` (`:386`): reguła „wywołanie obok wywołania” nie pasuje.
- Konwerterów jest 21: 20 w `persistence/converters/` (dziedziczą po `persistence/EnumValueConverter`) i `user/RoleConverter` (implementuje `AttributeConverter` bezpośrednio, konstruktory pakietowe). Kolumna `pipeline_runs.sources` jest `List<String>` bez konwertera: wartość zapisuje konstruktor `PipelineRun` przez `Source::value`.
- Wzorzec strażnika refleksyjnego: `PostColumnsIT:178-192` (`everyTransitionHasAScenario`).

## Desired End State

- `ClosedSetsIT` porównuje z `CHECK` wartość zwróconą przez prawdziwy konwerter, nie lambdę rejestru; zły konwerter zaczerwienia test.
- Każdy złożony `CHECK` z literałem czyta pary `kolumna = 'literał'`; literał musi być wartością enuma tej kolumny albo `CHECK` jest nazwanym wyjątkiem z powodem; wyjątek, który przestał obowiązywać, zaczerwienia test.
- Każda publiczna metoda `RunRecorder` jest sklasyfikowana (kończy przebieg / nie); każda kończąca ma nazwane testy, które sprawdzają `runEnded`, i te testy istnieją.
- Każdy strażnik ma stałe testy czerwonej ścieżki oraz zapisany przebieg ze zmianą psującą kod (X1, X2, brak `runEnded`), po którym zmiana jest cofnięta.
- `./mvnw -B verify` zielone.

### Key Discoveries:

- Rejestr ma już dwustronną kontrolę (metamodel JPA ↔ `REGISTRY` ↔ schemat), więc zmienia się tylko źródło wartości i kontrola literałów, nie struktura.
- `theCheckParserReadsEveryFormOfAListOfValues` to wzorzec testu parsera na dzisiejszych formach z `pg_get_constraintdef`, żeby zmiana formatu Postgresa była czerwona, a nie pusta i zielona.
- `NOT_A_VALUE_LIST` ma już kontrolę `gone` (wpis, którego `CHECK` zniknął): rozszerzamy ją na powód i na „wpis już nie dotyczy literału”.
- Strażnik R-23 nie potrzebuje bazy: to refleksja po `RunRecorder.class` i po klasach testów, więc może być `*Test` (surefire, bez Dockera).

## What We're NOT Doing

- Żadnych zmian w kodzie produkcyjnym ani migracjach (żadnych adnotacji `@EndsRun` w `RunRecorder`).
- Nie wykrywamy nowego *wywołującego* istniejącej metody kończącej (np. nowa ścieżka wołająca `finish`): strażnik widzi nową metodę, a lukę wywołujących pokrywa tylko przebieg ze zmianą psującą kod. ArchUnit odrzucony (nowa zależność, granulacja klasy).
- Zakładamy, że `RunRecorder` jest jedynym zapisującym stan końcowy przebiegu (dziś każdy `UPDATE pipeline_runs SET status` jest w nim, a encja `PipelineRun` nie ma settera statusu). Nowy zapis spoza `RunRecorder` (inna klasa z własnym `UPDATE`) jest ścieżką, której strażnik nie widzi; wychwyci ją tylko przegląd.
- Strażnik R-23 dowodzi, że nazwane testy istnieją, nie że sprawdzają `runEnded`. Treść potwierdza jednorazowy przebieg ze zmianą psującą kod (3.3), nie stały test.
- Nie zapisujemy wierszy per wartość do bazy (X1) ani nie dodajemy testów mutacyjnych (PIT).
- Nie ruszamy `RunNotifications` (brak ponowienia po commicie to S-12 w Parked) ani `PostColumnsIT`.
- Nie poprawiamy statusów R-17/R-23 w `context/domain/domain-distillation.md` (to robi kolejny przebieg destylacji).
- Nie sprawdzamy `CHECK`ów z wzorcem lub zbiorem otwartym (`ck_posts_channel`, hashe `ck_*_hash`, `ck_*_sha256`, `ck_activation_tokens_token_hash`): zostają nazwanymi wyjątkami.

## Implementation Approach

Każdy strażnik dzieli się na „czytaj świat” i „oceń”. Część oceniająca to czysta funkcja w klasie pomocniczej w `src/test/`, wołana przez strażnika (`*IT` z bazą dla X1/X2, `*Test` dla R-23) i przez osobny `*Test` bez Springa z danymi złymi celowo: tak czerwona ścieżka zostaje w testach na stałe. Przebieg ze zmianą psującą kod jest dodatkowym dowodem, wykonanym raz na fazę i cofniętym przed commitem.

Wyjątki są mapami `nazwa → powód` w testach; strażnik czerwieni się, gdy wyjątek nie ma powodu albo przestał obowiązywać.

## Critical Implementation Details

- **Konwertery**: lista z `ClassPathScanningCandidateComponentProvider` po `@Converter` w `pl.szczurradar.api` (obejmuje `user/RoleConverter`); typ enuma z `ResolvableType.forClass(c).as(AttributeConverter.class).getGeneric(0)`; konstruktory pakietowe: `getDeclaredConstructor()` + `setAccessible(true)`. Konwerter każdego enuma z `REGISTRY` musi istnieć, z wyjątkiem wpisu `pipeline_runs.sources`: tę wartość zapisuje konstruktor `PipelineRun` (`PipelineRun.java:142`), więc strażnik czyta ją z `PipelineRun.range(...).getSources()`, nie z konwertera.
- **Literały złożonych `CHECK`ów**: Postgres normalizuje tekst (`(status)::text = 'interrupted'::text`, rzutowania na kolumnach); parser musi czytać te formy i mieć test na każdą dziś występującą, żeby zmiana wyjścia była czerwona, nie pusta.

## Phase 1: X1 — wartość z prawdziwego konwertera

### Overview

`ClosedSetsIT` bierze z konwertera wartość zapisywaną dla każdej stałej i porównuje ją z `CHECK` kolumny.

### Changes Required:

#### 1. Źródło wartości w rejestrze

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsIT.java`

**Intent**: `StoredEnum` przestaje nieść lambdę wartości; wartości liczy z konwertera wybranego po typie enuma. Kolumna `pipeline_runs.sources` (bez konwertera) bierze wartości z prawdziwego zapisującego: `PipelineRun.range(...)` i jego `getSources()`, więc nie jest wyjątkiem.

**Contract**: `REGISTRY` zachowuje enum, tabelę, kolumnę, encję i atrybut; usuwa argument `Function<E,String>`. Źródło wartości wpisu to konwerter albo, dla `pipeline_runs.sources`, zapis przez `PipelineRun.range(from, to, List<Source>, …)`. Brak jednego i drugiego dla wpisu = czerwono z nazwą enuma.

#### 2. Wyszukiwanie i ocena konwerterów

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsGuard.java` (nowy)

**Intent**: Czysta logika: znajdź konwertery, policz wartości enuma przez `convertToDatabaseColumn`, porównaj ze zbiorem `CHECK`.

**Contract**: statyczne metody `convertersByEnum()` (mapa typ enuma → konwerter) i `writtenValues(enumType, converter)`; wyjątki jako `Map<String,String>` nazwa → powód.

#### 3. Test czerwonej ścieżki

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsGuardTest.java` (nowy, `*Test`, bez Springa)

**Intent**: Dowód, że zły konwerter i brakujący konwerter są zgłaszane.

**Contract**: przypadki: konwerter zapisujący `name()` zamiast wartości → wartość różna od zbioru `CHECK`; enum bez konwertera → zgłoszony; dobry konwerter → brak uwag.

### Success Criteria:

#### Automated Verification:

- Strażnik i jego testy zielone: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=ClosedSetsIT -Dtest=ClosedSetsGuardTest -Dsurefire.failIfNoSpecifiedTests=false`
- Przebieg X1: w `RunKindConverter` zamień `RunKind::value` na `RunKind::name`, uruchom `./mvnw -B verify -Dit.test=ClosedSetsIT`, oczekuj czerwonego wyniku z nazwą `pipeline_runs.kind` (dodaj `-Dtest=ClosedSetsGuardTest -Dsurefire.failIfNoSpecifiedTests=false`, żeby jednostkowe testy nie przesłoniły wyniku); cofnij zmianę (`git checkout -- szczur-radar-api/src/main/java/pl/szczurradar/api/persistence/converters/RunKindConverter.java`) i potwierdź, że `git status` nie pokazuje zmian w `src/main`.
- Pełny zestaw: `cd szczur-radar-api && ./mvnw -B verify`

## Phase 2: X2 — literały złożonych `CHECK`ów

### Overview

Złożone `CHECK`i z literałem enuma są sprawdzane: literał musi być wartością enuma swojej kolumny.

### Changes Required:

#### 1. Parser par `kolumna = 'literał'`

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsGuard.java`

**Intent**: Z definicji złożonego `CHECK` (z `pg_get_constraintdef`) wyciągnąć pary kolumna–literał i ocenić je względem wartości enumów z rejestru dla tej tabeli.

**Contract**: `literalPairs(definition)` → lista `(kolumna, literał)` czytająca formy `col = 'x'::text`, `(col)::text = 'x'::text`, `col <> 'x'` i `col IS NOT NULL`-owe bez literałów (te nic nie wnoszą); `unknownLiterals(table, pairs, registry)` → pary, których literał nie jest wartością enuma kolumny `table.col`, albo kolumny bez wpisu w rejestrze.

#### 2. `NOT_A_VALUE_LIST` jako mapa z powodem

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsIT.java`

**Intent**: Zamienić zbiór nazw na mapę nazwa → powód. Zostaje pięć wpisów z wzorcem lub zbiorem otwartym: `ck_posts_channel`, `ck_assessments_input_hash`, `ck_posts_content_hash`, `ck_raw_payloads_sha256`, `ck_activation_tokens_token_hash`. Osiem złożonych `CHECK`ów z literałem enuma wychodzi z wyjątków i idzie przez nową ocenę: `ck_pipeline_runs_interruption_status`, `ck_pipeline_runs_requested_posts`, `ck_pipeline_runs_range`, `ck_posts_assessing_owner`, `ck_posts_stage_attempts`, `ck_assessments_outcome`, `ck_notifications_run`, `ck_pipeline_run_sources_error`.

**Contract**: test `everyCheckWithLiteralsIsAValueListOrKnown` rozszerzony o: literały złożonego `CHECK`a spoza wyjątków są wartościami enuma swojej kolumny; wpis w mapie bez powodu albo taki, którego `CHECK` zniknął lub który nie ma już literału do wyjątku, jest czerwony.

#### 3. Test parsera i czerwonej ścieżki

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsGuardTest.java`

**Intent**: Parser czyta dzisiejsze formy i zgłasza literówkę.

**Contract**: przypadki na tekstach definicji zapisanych jako stałe: po jednej realnej definicji każdego z ośmiu złożonych `CHECK`ów (wymienionych wyżej; teksty skopiowane z `pg_get_constraintdef` w tej fazie), w tym formy `<>` (`ck_notifications_run`, `ck_posts_stage_attempts`), porównania kolumna–kolumna obok literału (`ck_pipeline_runs_range`) i `AND`/`OR` z `IS NULL` (`ck_assessments_outcome`); `status = 'interupted'` → zgłoszone; literał z innej kolumny → zgłoszone; wzorzec z hashem → nie jest parą literałów enuma.

### Success Criteria:

#### Automated Verification:

- Strażnik i testy: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=ClosedSetsIT -Dtest=ClosedSetsGuardTest -Dsurefire.failIfNoSpecifiedTests=false`
- Przebieg X2: dodaj tymczasową migrację `szczur-radar-api/src/main/resources/db/migration/V<następna>__break.sql` zastępującą `ck_pipeline_runs_interruption_status` wersją z `status = 'interupted'`, uruchom `./mvnw -B verify -Dit.test=ClosedSetsIT`, oczekuj czerwonego wyniku z nazwą `ck_pipeline_runs_interruption_status` i literałem `interupted` (z tymi samymi `-Dtest=ClosedSetsGuardTest -Dsurefire.failIfNoSpecifiedTests=false`); usuń plik migracji i potwierdź `git status` bez zmian w `src/main`.
- Pełny zestaw: `cd szczur-radar-api && ./mvnw -B verify`

## Phase 3: R-23 — koniec przebiegu zawsze powiadamia

### Overview

Nowa publiczna metoda `RunRecorder` nie przechodzi niesklasyfikowana, a każda kończąca przebieg ma testy, które sprawdzają `runEnded`.

### Changes Required:

#### 1. Strażnik klasyfikacji

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/RunEndingGuard.java` (nowy)

**Intent**: Czysta logika w stylu `PostColumnsIT`: publiczne metody klasy ↔ klasyfikacja; każda kończąca ma nazwane testy, które istnieją (po refleksji: klasa, metoda, `@Test`/`@ParameterizedTest`).

**Contract**: `unclassified(Class<?>, Set<String> ends, Map<String,String> notEnding)` (powód przy każdej niekończącej), `missingCoverage(Map<String,List<String>> testsByMethod)` z odwołaniami `pakiet.Klasa#metoda`; metoda kończąca bez testów, test nieistniejący, wpis o metodzie, której już nie ma — zgłoszone.

#### 2. Strażnik na `RunRecorder`

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/RunEndingTest.java` (nowy, `*Test`, bez Springa)

**Intent**: Klasyfikacja metod `RunRecorder`: kończące `finish`, `fail`, `interrupt`, `stall`, `interruptAll`, `interruptOrphans`; niekończące (`start`, `isRunning`, `count`, `source`, `touch`, `error`, `inFlight`, `step` i każda inna publiczna znaleziona przy implementacji) z jednozdaniowym powodem. Każda kończąca wskazuje istniejące testy z audytu (`PipelineRunServiceTest`, `PipelineRunIT`, `OrphanedRunIT`, `PipelineStartupRecoveryIT`), wybrane po przeczytaniu, że sprawdzają `runEnded` albo skutek (powiadomienie). Dla ścieżki bez takiego testu — dopisać go w tej fazie.

**Contract**: `ENDS` jako `Map<metoda, List<odwołanie do testu>>`, `NOT_ENDING` jako `Map<metoda, powód>`; test `everyPublicMethodIsClassified`, test `everyEndingMethodHasExistingTests`.

#### 3. Test czerwonej ścieżki

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/RunEndingGuardTest.java` (nowy)

**Intent**: Dowód, że strażnik zgłasza nową metodę, test nieistniejący i wpis przeterminowany.

**Contract**: klasa próbna z publiczną metodą spoza klasyfikacji → zgłoszona; odwołanie do nieistniejącej metody testowej → zgłoszone; wpis `NOT_ENDING` o metodzie, której klasa nie ma → zgłoszony.

#### 4. Dokumentacja

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` (akapit „Notifications”, ok. `:249`), javadoc `ClosedSetsIT`

**Intent**: Reguła „każdy koniec woła `runEnded`” przestaje być samą konwencją: dopisać nazwę strażnika i jak go rozszerzyć (nowa publiczna metoda `RunRecorder` → klasyfikacja i testy); w javadoc `ClosedSetsIT` opisać wartości z konwertera i literały złożonych `CHECK`ów.

**Contract**: po jednym–dwóch zdaniach w istniejących akapitach; bez nowych sekcji.

### Success Criteria:

#### Automated Verification:

- Strażnik R-23 i jego testy: `cd szczur-radar-api && ./mvnw -B test -Dtest='RunEnding*Test'`
- Przebieg R-23 (a): dodaj do `RunRecorder` tymczasową publiczną metodę, uruchom `./mvnw -B test -Dtest=RunEndingTest`, oczekuj czerwonego wyniku z jej nazwą; cofnij zmianę.
- Przebieg R-23 (b): zakomentuj jedno `notifications.runEnded(runId)` w `PipelineRunService` (watchdog, ok. `:480`), uruchom `./mvnw -B verify -Dtest=PipelineRunServiceTest -Dit.test='PipelineRunIT,OrphanedRunIT,PipelineStartupRecoveryIT' -Dsurefire.failIfNoSpecifiedTests=false`, oczekuj, że co najmniej jeden test nazwany w klasyfikacji jest czerwony; cofnij zmianę i potwierdź `git status` bez zmian w `src/main` poza `CLAUDE.md`.
- Dokumentacja wskazuje strażnika: `grep -n RunEndingTest szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` zwraca wiersz, a javadoc `ClosedSetsIT` wspomina konwertery i literały złożonych `CHECK`ów
- Pełny zestaw: `cd szczur-radar-api && ./mvnw -B verify`

---

## Testing Strategy

### Unit Tests:

- `ClosedSetsGuardTest`: zły konwerter, brak konwertera, dobry; parser literałów na realnych definicjach i na literówce.
- `RunEndingGuardTest`: metoda poza klasyfikacją, test nieistniejący, wpis przeterminowany.

### Integration Tests:

- `ClosedSetsIT` na migrowanym schemacie: wartości z konwerterów równe `CHECK`om, literały złożonych `CHECK`ów należą do enumów, wyjątki aktualne.
- Istniejące testy nazwane w `RunEndingTest` pozostają jedynym dowodem, że ścieżka woła `runEnded`.

### Manual Testing Steps:

Brak: wszystko zmienia kod testów i jest sprawdzane poleceniem.

## Performance Considerations

Skan classpath po `@Converter` i refleksja po `RunRecorder` to ułamek sekundy; `RunEndingTest` nie startuje Springa ani Dockera.

## Migration Notes

Bez migracji. Przebiegi ze zmianą psującą kod nie zostawiają zmian w `src/main` (sprawdza je `git status` w kryteriach).

## References

- Audyt: `context/changes/post-m3-verification-audit/research.md` (§1 R-17 `:78-89`, X1 `:67`, X2 `:68`, §5 R-23 `:199-215`)
- Destylacja: `context/domain/domain-distillation.md` (R-17 `:216`, R-23 `:229`, #6 `:315`)
- Roadmapa: `context/foundation/roadmap.md` (S-39)
- Wzorzec strażnika: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/PostColumnsIT.java:178-192`
- Konwertery: `szczur-radar-api/src/main/java/pl/szczurradar/api/persistence/EnumValueConverter.java`, `persistence/converters/`, `user/RoleConverter.java`
- Kontrakt zakończenia przebiegu: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` (Notifications, ok. `:249`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: X1 — wartość z prawdziwego konwertera

#### Automated

- [x] 1.1 Strażnik X1 i jego testy zielone (`ClosedSetsIT`, `ClosedSetsGuardTest`) — 05feb01
- [x] 1.2 Przebieg X1: `RunKind::name` w `RunKindConverter` czerwieni `ClosedSetsIT`, zmiana cofnięta — 05feb01
- [x] 1.3 Pełny `./mvnw -B verify` zielony — 05feb01

### Phase 2: X2 — literały złożonych `CHECK`ów

#### Automated

- [x] 2.1 Strażnik X2 i testy parsera zielone (`ClosedSetsIT`, `ClosedSetsGuardTest`)
- [x] 2.2 Przebieg X2: migracja z `'interupted'` czerwieni `ClosedSetsIT`, migracja usunięta
- [x] 2.3 Pełny `./mvnw -B verify` zielony

### Phase 3: R-23 — koniec przebiegu zawsze powiadamia

#### Automated

- [ ] 3.1 Strażnik R-23 i jego testy zielone (`RunEndingTest`, `RunEndingGuardTest`)
- [ ] 3.2 Przebieg R-23 (a): nowa publiczna metoda `RunRecorder` czerwieni `RunEndingTest`, zmiana cofnięta
- [ ] 3.3 Przebieg R-23 (b): usunięte `runEnded` czerwieni nazwany test, zmiana cofnięta
- [ ] 3.4 Pełny `./mvnw -B verify` zielony
- [ ] 3.5 Dokumentacja wskazuje strażnika (`pipeline/CLAUDE.md`, javadoc `ClosedSetsIT`)
