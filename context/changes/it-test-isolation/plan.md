# Izolacja testów integracyjnych (S-30) Implementation Plan

## Overview

Wynik `*IT` ma nie zależeć od kolejności klas ani od danych zostawionych przez inny test. Jedno globalne rozszerzenie JUnit czyści dane testowe po każdej klasie i pilnuje, żeby kolejna klasa startowała na czystej bazie. Dowodem jest odtworzenie czerwonej kolejności z runu 41784416932 i zielone wyniki na kilku ziarnach `-Dit.seed`.

## Current State Analysis

- Klasy z tym samym kluczem kontekstu Springa dzielą jeden kontener Postgresa. `MigrationsIT`, `PostRepositoryIT`, `ClosedSetsIT`, `RunRecorderIT` i `PostColumnsIT` mają ten sam klucz (`research.md`).
- `MigrationsIT` zatwierdza posty i ich nie usuwa. `PostRepositoryIT` jest `@Transactional`, ale `findLeftoverIds()` czyta całą bazę, więc widzi cudze posty.
- Kolejność klas w failsafe to domyślne `runOrder=filesystem` (zależne od maszyny, bez ziarna). `it.seed` (`pom.xml:35,196`) losuje tylko kolejność metod w `PipelineRunIT` i `RunWatchdogIT`; `ClassOrderer` nie jest ustawiony nigdzie.
- Około dwunastu innych klas zapisuje bez transakcji i bez widocznego sprzątania; kilka testów liczy globalnie (`research.md`, „Globalne odczyty”).
- Jedyne dane z migracji, które muszą zostać: konto `SCHEDULER` (`V6__users_login_enabled_scheduler.sql:11`) oraz `flyway_schema_history`.

## Desired End State

Po każdej klasie `*IT` w jej bazie zostają tylko dane z migracji. Przed każdą klasą rozszerzenie sprawdza to i pada z nazwą tabeli, jeśli jest inaczej. `./mvnw -B verify` jest zielone w obu kolejnościach klas (`-Dfailsafe.runOrder=alphabetical` i `reversealphabetical`), w tym dla kolejności `MigrationsIT` → `PostRepositoryIT`.

### Key Discoveries:

- Reguła „każdy kontekst ma własny kontener” z javadoc `TestcontainersConfiguration` jest prawdziwa tylko dla różnych kluczy kontekstu; ten sam klucz to ta sama baza.
- `PostColumnsIT:226-239` już sprząta w `@AfterEach` — wzorzec do zastąpienia wspólnym mechanizmem, nie do powielania.
- `HealthIT.WithoutDatabase` ma `DataSource` wskazujący na niedostępny port 1 (`HealthIT.java:47-52`), `DeploySafetyIT` i `HealthIT` mają klasy `@Nested`, a testy `*Test` (surefire) działają bez Springa — rozszerzenie musi wtedy nic nie robić.

## What We're NOT Doing

- Nie dzielimy kontekstów (`@DirtiesContext`, osobny kontener na klasę) — kosztowne dla czasu CI.
- Nie poprawiamy kolejności testów wewnątrz klasy ani izolacji między metodami jednej klasy.
- Nie ruszamy kodu produkcyjnego ani migracji.
- Nie robimy audytu ręcznego 33 klas; globalne `count(*)` poprawiamy tylko tam, gdzie guard lub seedy je wykażą.
- Nie usuwamy istniejących `@AfterEach` z klas (mogą zostać; usunięcie to osobne sprzątanie).

## Implementation Approach

Rozszerzenie `DbIsolationExtension` implementuje `BeforeAllCallback` i `AfterAllCallback`, rejestrowane globalnie przez autodetekcję JUnit. Bierze `JdbcTemplate` z kontekstu Springa danej klasy. Tabele wylicza dynamicznie z `information_schema` (schemat `public`), więc nowa migracja z tabelą nie wymaga zmian w rozszerzeniu. Zachowuje `flyway_schema_history` i wiersz `SCHEDULER` w `users`.

## Critical Implementation Details

- **Brak kontekstu / bazy / klasa zagnieżdżona** — rozszerzenie działa tylko dla klas najwyższego poziomu (`getEnclosingClass() == null`; `beforeAll`/`afterAll` odpalają też dla `@Nested`, co dałoby fałszywy fail i czyszczenie w środku klasy zewnętrznej). Nic nie robi, gdy klasa nie ma `@SpringBootTest`, kontekst nie ma `DataSource` albo połączenie się nie udaje (`HealthIT.WithoutDatabase`, surefire `*Test`).
- **Schemat zmieniany przez test** — `V12BackfillIT` i `V13BackfillIT` nie używają `ENABLE_FLYWAY` i robią `Flyway.clean()` + migrację do wersji 12 (`V13BackfillIT:37-38`). Przed klasą baza może nie mieć tabel (traktowana jako czysta), a lista tabel jest brana w momencie czyszczenia, nie zapisana na stałe.
- **Pisanie po sprzątaniu** — przebiegi pipeline'u działają w wątkach tła; guard przed klasą ma wykryć zapis, który przyszedł po `afterAll`. Jeśli taki przypadek się pojawi, to prawdziwy przeciek do naprawy w klasie, nie w rozszerzeniu.

## Phase 1: Rozszerzenie izolacji

### Overview

Dodanie `DbIsolationExtension`, jego rejestracji i testu samego rozszerzenia.

### Changes Required:

#### 1. Rozszerzenie

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/DbIsolationExtension.java`

**Intent**: Po każdej klasie usuwa dane testowe; przed każdą sprawdza, że w bazie są tylko dane z migracji, a w razie różnicy kończy klasę błędem z nazwami tabel i liczbą wierszy.

**Contract**: `BeforeAllCallback` + `AfterAllCallback`. Czyści `TRUNCATE ... CASCADE` wszystkie tabele schematu `public` poza `flyway_schema_history`; `users` czyści przez `DELETE WHERE id <> SystemAccount.SCHEDULER`. „Czysto” = brak wierszy poza `flyway_schema_history` i kontem `SCHEDULER`. Rozszerzenie nic nie robi bez kontekstu Springa z `DataSource`.

#### 2. Rejestracja globalna

**File**: `szczur-radar-api/src/test/resources/junit-platform.properties` oraz `szczur-radar-api/src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension`

**Intent**: Włącza autodetekcję rozszerzeń i wskazuje `DbIsolationExtension`, żeby działało dla wszystkich klas bez adnotacji.

**Contract**: `junit.jupiter.extensions.autodetection.enabled=true` + wpis z pełną nazwą klasy.

#### 3. Kolejność klas (zmiana w trakcie implementacji)

**Intent**: Pierwotnie `ClassOrderer.Random` na `it.seed`. Sprawdzone w Phase 1: z failsafe 3.5.6 nie zmienia kolejności (9 ziaren, także `runOrder=random` + seed, 6 ziaren — zawsze ta sama kolejność), więc nie jest dodany. Niezależność od kolejności dowodzimy `-Dfailsafe.runOrder=alphabetical` i `reversealphabetical` (Fix B z `reviews/plan-review.md`).

**Contract**: bez zmian w konfiguracji kolejności klas.

#### 4. Test rozszerzenia

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/DbIsolationExtensionIT.java`

**Intent**: Dowodzi, że czyszczenie usuwa zatwierdzone dane i zostawia `SCHEDULER`, a guard pada na brudnej bazie.

**Contract**: Test na własnym kontekście; wywołuje czyszczenie i sprawdzenie bezpośrednio na metodach rozszerzenia (pomocnicze metody pakietowe), bez zależności od kolejności klas.

### Success Criteria:

#### Automated Verification:

- Test rozszerzenia przechodzi: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=DbIsolationExtensionIT`
- Para klas przechodzi w obu kolejnościach: `./mvnw -B verify -Dit.test='MigrationsIT,PostRepositoryIT' -Dfailsafe.runOrder=alphabetical` oraz `reversealphabetical`
- Testy jednostkowe nadal przechodzą bez Dockera: `./mvnw -B test`

#### Manual Verification:

- Przegląd diffa: rozszerzenie nie dotyka tabel z danymi z migracji poza `SCHEDULER`.

**Implementation Note**: Po automatycznej weryfikacji tej fazy zatrzymaj się na ręczne potwierdzenie przed Phase 2.

---

## Phase 2: Dowód i dokumentacja

### Overview

Odtworzenie czerwonej kolejności, zielone wyniki na kilku ziarnach, poprawa dokumentacji.

### Changes Required:

#### 1. Dowód red → green

**Intent**: Pokazać, że przed zmianą kolejność `MigrationsIT` → `PostRepositoryIT` była czerwona, a po zmianie jest zielona, oraz że pełny `verify` jest niezależny od ziarna.

**Contract**: Przed zmianą: z `-Dfailsafe.runOrder=alphabetical` (MigrationsIT przed PostRepositoryIT) i wyłączoną autodetekcją rozszerzeń (`-Djunit.jupiter.extensions.autodetection.enabled=false`) pokazać czerwone `PostRepositoryIT`; po zmianie ten sam przebieg z rozszerzeniem. Pełny `./mvnw -B verify` dla co najmniej 3 różnych ziaren. Wyniki zapisane w komentarzu PR/Progress.

#### 2. Dokumentacja

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/TestcontainersConfiguration.java`, `szczur-radar-api/README.md`

**Intent**: Poprawić wprowadzający w błąd opis „każdy kontekst ma własny kontener” i opisać izolację danych oraz sposób powtórzenia kolejności.

**Contract**: Javadoc wspomina, że ten sam klucz kontekstu dzieli bazę i że `DbIsolationExtension` czyści po klasie; README (sekcja o testach) zawiera `-Dit.seed`.

### Success Criteria:

#### Automated Verification:

- Pełny verify zielony w dwóch kolejnościach klas: `./mvnw -B verify -Dfailsafe.runOrder=alphabetical` i `-Dfailsafe.runOrder=reversealphabetical`
- Przebieg z dowodu red (alphabetical, bez rozszerzenia czerwony) jest zielony z rozszerzeniem

#### Manual Verification:

- Czerwona kolejność (alphabetical, bez rozszerzenia) faktycznie padała w `PostRepositoryIT` (dowód red).
- CI na PR zielone; czas `backend (Maven verify)` nie wzrósł zauważalnie względem zielonych runów.

---

## Testing Strategy

### Unit Tests:

- Brak nowych testów jednostkowych; rozszerzenie bez Springa ma nic nie robić (sprawdzane przez `./mvnw -B test`).

### Integration Tests:

- `DbIsolationExtensionIT`: czyszczenie, zachowanie `SCHEDULER`, guard na brudnej bazie.
- Pełny `verify` na wielu ziarnach jako test niezależności od kolejności.

### Manual Testing Steps:

1. Bez rozszerzenia, z `runOrder=alphabetical`, potwierdź czerwone `PostRepositoryIT`.
2. Na gałęzi powtórz z tym samym ziarnem i potwierdź zielone.

## Performance Considerations

Jedno `TRUNCATE` na klasę (33 klasy) — pomijalny koszt względem startu kontenerów.

## Migration Notes

Nie dotyczy (bez zmian schematu). Nowa tabela w przyszłej migracji jest czyszczona automatycznie.

## References

- Related research: `context/changes/it-test-isolation/research.md`
- Wzorzec sprzątania: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/PostColumnsIT.java:226`
- Awaria: run CI 41784416932; roadmapa `context/foundation/roadmap.md:110` (S-30)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Rozszerzenie izolacji

#### Automated

- [x] 1.1 Test rozszerzenia przechodzi: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=DbIsolationExtensionIT` — 2ef165d
- [x] 1.2 Para klas przechodzi w obu kolejnościach: `./mvnw -B verify -Dit.test='MigrationsIT,PostRepositoryIT' -Dfailsafe.runOrder=alphabetical` oraz `reversealphabetical` — 2ef165d
- [x] 1.3 Testy jednostkowe nadal przechodzą bez Dockera: `./mvnw -B test` — 2ef165d

#### Manual

- [x] 1.4 Przegląd diffa: rozszerzenie nie dotyka tabel z danymi z migracji poza `SCHEDULER`. — 4594aa2

### Phase 2: Dowód i dokumentacja

#### Automated

- [x] 2.1 Pełny verify zielony w dwóch kolejnościach klas: `./mvnw -B verify -Dfailsafe.runOrder=alphabetical` i `-Dfailsafe.runOrder=reversealphabetical` — 5731567
- [x] 2.2 Przebieg z dowodu red (alphabetical, bez rozszerzenia czerwony) jest zielony z rozszerzeniem — 5731567

#### Manual

- [x] 2.3 Czerwona kolejność (alphabetical, bez rozszerzenia) faktycznie padała w `PostRepositoryIT` (dowód red). — 4594aa2
- [x] 2.4 CI na PR zielone; czas `backend (Maven verify)` nie wzrósł zauważalnie względem zielonych runów. — 4594aa2
