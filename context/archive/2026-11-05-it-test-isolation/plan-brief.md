# Izolacja testów integracyjnych (S-30) — Plan Brief

> Full plan: `context/changes/it-test-isolation/plan.md`
> Research: `context/changes/it-test-isolation/research.md`

## What & Why

Wynik testów `*IT` zależy dziś od losowej kolejności klas: `MigrationsIT` zostawia w bazie posty, które widzi `PostRepositoryIT`, i CI na `main` bywa czerwone bez powodu (run 41784416932). Chcemy, żeby test nie widział danych zostawionych przez inny test.

## Starting Point

Klasy z tym samym kluczem kontekstu Springa dzielą jeden kontener Postgresa; część klas zapisuje bez transakcji i bez sprzątania, a kolejność klas to `runOrder=filesystem` (zależna od maszyny); `it.seed` losuje tylko metody dwóch klas.

## Desired End State

Po każdej klasie `*IT` w bazie zostają tylko dane z migracji, a następna klasa dostaje to sprawdzone przed startem. `./mvnw -B verify` jest zielone dla dowolnego `-Dit.seed`.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Mechanizm | Globalne rozszerzenie czyszczące tabele po klasie | Chroni też przyszłe testy z S-31 bez zmian w czasie CI | Plan |
| Guard | Sprawdzenie „baza czysta” przed klasą, fail z nazwą tabeli | Po klasie zawsze są jej dane, więc sens ma tylko kontrola przed; łapie zapisy po sprzątaniu | Plan |
| Kolejność klas | `ClassOrderer.Random` na istniejącym `it.seed` | `it.seed` dotąd losował tylko metody dwóch klas; bez tego ziarna nic nie dowodzą | Plan (review F1) |
| Weryfikacja | Znalezione czerwone ziarno → zielone z rozszerzeniem + 3 ziarna | Jedno zielone nie dowodzi niezależności od kolejności | Plan |
| Zakres | Wszystkie klasy przez rozszerzenie, bez ręcznego audytu | Spełnia warunek roadmapy mniejszym diffem | Plan |
| Przyczyna | Wspólny kontekst + niesprzątane commity MigrationsIT | Potwierdzone w kodzie i logach CI | Research |

## Scope

**In scope:** `DbIsolationExtension` (tylko klasy najwyższego poziomu), rejestracja globalna, `ClassOrderer.Random`, test rozszerzenia, dowód red→green, poprawka javadoc i README.

**Out of scope:** osobne konteksty na klasę, zmiany produkcyjne i migracje, kolejność testów w klasie, ręczny audyt 33 klas.

## Architecture / Approach

JUnit `BeforeAll`/`AfterAll` z `JdbcTemplate` z kontekstu klasy. Tabele wyliczane z `information_schema`; zostają `flyway_schema_history` i konto `SCHEDULER`. Bez kontekstu lub bazy rozszerzenie nic nie robi.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Rozszerzenie izolacji | Rozszerzenie, rejestracja, test | Klasy zmieniające schemat (V12/V13) i zapisy z wątków tła |
| 2. Dowód i dokumentacja | Red→green, 3 ziarna, docs | Czas lokalnych `verify` z Dockerem |

**Prerequisites:** Docker lokalnie, JDK 25.
**Estimated effort:** ~1–2 sesje, 2 fazy.

## Open Risks & Assumptions

- Guard może ujawnić kolejne przecieki (zapisy po sprzątaniu) — to prawdziwe błędy, ale poszerzą zakres.
- Zakładam, że żadna klasa nie polega na danych pozostawionych przez poprzednią klasę; wyjdzie to na pełnym `verify`.

## Success Criteria (Summary)

- Czerwona kolejność `MigrationsIT` → `PostRepositoryIT` jest zielona.
- Pełny `verify` zielony na co najmniej trzech ziarnach i w CI.
