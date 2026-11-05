<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Izolacja testów integracyjnych (S-30)

- **Plan**: context/changes/it-test-isolation/plan.md
- **Scope**: Phase 2 of 2 (z oceną diffu fazy 1)
- **Reviewed phases**: 1, 2
- **Date**: 2026-11-05
- **Verdict**: NEEDS ATTENTION → po triage: F1, F2 naprawione, F3–F5 zaakceptowane
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS (odstępstwa zatwierdzone przez użytkownika) |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS (backend CI na PR #140 w trakcie) |

## Findings

### F1 — Nieudane czyszczenie jest połykane po cichu

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/DbIsolationExtension.java:78-83
- **Detail**: `afterAll` łapie każdy `DataAccessException` (brak bazy, ale też timeout/lock/deadlock TRUNCATE). Prawdziwa przyczyna ginie, a następna klasa pada na „earlier test left rows".
- **Fix**: łap wyjątek tylko, gdy lista tabel jest pusta/baza niedostępna; po nieudanym TRUNCATE zaloguj WARN z przyczyną albo rzuć dalej, i ustaw `SET LOCAL lock_timeout` w transakcji czyszczącej.
  - Strength: awaria czyszczenia wskazuje własną przyczynę, a TRUNCATE nie wisi bez limitu na wątku tła.
  - Tradeoff: czyszczenie wymaga transakcji i może zacząć padać tam, gdzie dziś tylko cicho zawodzi.
  - Confidence: MED — nie widać dziś takiego przypadku w testach.
  - Blind spot: nie sprawdzone, jak zachowa się timeout na wolnym runnerze CI.
- **Decision**: FIXED (połknięty wyjątek tylko przy braku bazy; czyszczenie w transakcji z lock_timeout 30s, błąd propaguje)

### F2 — Brak pewności, że czyszczona baza jest testowa

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: DbIsolationExtension.java:136-151
- **Detail**: Rozszerzenie robi TRUNCATE na dowolnym `DataSource` kontekstu. Dziś każda klasa importuje `TestcontainersConfiguration`, ale przyszły `*IT` bez tego importu użyje zmiennych `PG*` i wyczyści lokalną bazę dewelopera (README opisuje takie użycie).
- **Fix**: czyść tylko, gdy kontekst ma bean `PostgreSQLContainer`; w przeciwnym razie odmów z komunikatem.
  - Strength: nowy IT bez Testcontainers nie skasuje lokalnych danych.
  - Tradeoff: IT z własną bazą (np. `spring.datasource` na zewnętrzny Postgres) nie zostanie wyczyszczony.
  - Confidence: HIGH — wszystkie klasy używają dziś `TestcontainersConfiguration`.
  - Blind spot: `V12/V13BackfillIT` nie mają `ENABLE_FLYWAY`, ale też importują `TestcontainersConfiguration`.
- **Decision**: FIXED (czyści tylko kontekst z beanem PostgreSQLContainer)

### F3 — `@DirtiesContext` wykluczone tylko na poziomie klasy

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: DbIsolationExtension.java:138-140
- **Detail**: Metoda z `@DirtiesContext` zamyka kontekst, a `afterAll` budzi nowy (nowy kontener) tylko po to, by go czyścić. Żaden test tego dziś nie używa.
- **Fix**: pomiń też klasy, które mają taką metodę.
- **Decision**: ACCEPTED (latentne, żaden test nie używa)

### F4 — „Pierwsza klasa kontekstu" to pierwsza przetworzona przez rozszerzenie

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: DbIsolationExtension.java:57-63
- **Detail**: Klasa pominięta przez rozszerzenie (`@Nested`, `@DirtiesContext`, kontekst z kilkoma `DataSource`), która pisze do tej samej bazy, nie jest widziana przez guard. Teoretyczne dziś.
- **Fix**: zaakceptować i odnotować w javadoc.
- **Decision**: ACCEPTED (teoretyczne dziś)

### F5 — Kolejność względem `SpringExtension` opiera się na regule JUnit

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: DbIsolationExtension.java:50
- **Detail**: Działa, bo rozszerzenia autodetekowane są w rejestrze głównym (`beforeAll` przed `SpringExtension`, `afterAll` po nim), ale nie jest to nigdzie zapisane.
- **Fix**: jeden komentarz w kodzie z tą zależnością.
- **Decision**: ACCEPTED (bez zmian; kolejność wynika z reguły JUnit)
