---
date: 2026-11-05
researcher: Claude (Sonnet 5.5)
git_commit: c8a6488
branch: feature/it-test-isolation
repository: szczur-radar
topic: "S-30: dlaczego wynik testów integracyjnych zależy od kolejności klas"
tags: [research, codebase, tests, failsafe, testcontainers, MigrationsIT, PostRepositoryIT]
status: partial
last_updated: 2026-11-05
last_updated_by: Claude (Sonnet 5.5)
---

# Research: S-30 — izolacja testów integracyjnych

## Research Question

Co powoduje, że CI bywa czerwone zależnie od kolejności klas `*IT` (run 41784416932), i jaki jest zakres tego problemu w `szczur-radar-api`?

## Summary

Czerwony run 41784416932 (push #137 na `main`): `PostRepositoryIT` — 2 testy padły (`leftoversAreStoredAndRetryableFailedPostsOldestFirst:103`, `aPostOnHoldIsNoLeftoverAndOneTechnicalFailureShortOfItIs:114`), 326 testów, 2 porażki.

Mechanizm (sprawdzony w kodzie):
1. `MigrationsIT` i `PostRepositoryIT` mają identyczną konfigurację kontekstu (`webEnvironment = NONE`, `properties = MigrateCommand.ENABLE_FLYWAY`, `@Import(TestcontainersConfiguration.class)`), więc Spring cache'uje jeden kontekst, a z nim jeden kontener Postgresa.
2. `PostRepositoryIT` jest `@Transactional` (rollback), ale pyta o dane globalnie: `posts.findLeftoverIds()` i `existsLeftover()` zwracają wszystkie posty w stage `stored`/`failed` w całej bazie.
3. `MigrationsIT` nie jest `@Transactional` i nie ma `@AfterEach`; zatwierdza posty (m.in. `70401` reddit, przez `postInsert(...)` i insert w `redditIsARegisteredSource`) i nie usuwa ich. Usuwa tylko wybranych użytkowników (`MigrationsIT:338`, `:376`).
4. Kolejność klas w failsafe to domyślne `runOrder=filesystem` (zależne od maszyny); raz `PostRepositoryIT` biegnie przed `MigrationsIT` (zielono), raz po (czerwono). `it.seed` (`pom.xml:35`, `:196`) losuje tylko kolejność metod w `PipelineRunIT` i `RunWatchdogIT`; `ClassOrderer` nie jest ustawiony nigdzie (korekta po `/10x-plan-review`).

Wniosek: błąd jest po stronie testów, nie kodu produkcyjnego. Potwierdza to zapis w pamięci projektu (czerwony run: `MigrationsIT` 9., `PostRepositoryIT` zaraz po; zielony PR: 10. i 15.).

## Detailed Findings

### Współdzielenie kontekstu i bazy

- `TestcontainersConfiguration` tworzy `postgres:18` jako bean `@ServiceConnection`; javadoc: „each distinct Spring test context gets its own container”. To prawda, ale ten sam klucz kontekstu = ten sam kontener, a klucz tworzą m.in. `properties`, `@Import`, `webEnvironment`.
- Klasy z tym samym kluczem co `PostRepositoryIT` (sprawdzone po nagłówkach `@SpringBootTest`/`@Import`): `MigrationsIT`, `ClosedSetsIT`, `RunRecorderIT`, `PostColumnsIT` (ta ostatnia porządkuje w `@AfterEach`, `PostColumnsIT:226-239`). Pełnej tabeli kluczy dla wszystkich 33 klas nie zbudowałem (luka).

### Wzorce porządkowania w 33 klasach `@SpringBootTest`

Z grepa po nagłówkach i słowach kluczowych (przybliżenie, nie dowód):
- `@Transactional` (rollback): `FreshnessIT`, `PostsApiIT`, `PostRepositoryIT`, `RunRecorderIT`, `MissingDaysIT`, `DeploySafetyIT`, `PipelineRunWithoutSourceIT`.
- Własne `DELETE`/`deleteAll`: `ActivationIT`, `AuthIT`, `CreateAdminCommandIT`, `PipelineRunControllerIT`, `PipelineRunIT`, `PostColumnsIT`, `RunWatchdogIT`, `MigrationsIT` (tylko users).
- Piszą bez transakcji i bez widocznego sprzątania: `ActivationLinksIT`, `ApiErrorHttpIT`, `InternalPipelineRunIT`, `JpaSchemaIT`, `LinkCommandIT`, `LlmOutageIT`, `NotificationsIT`, `OrphanedRunIT`, `PipelineStartupRecoveryIT`, `V12BackfillIT`, `V13BackfillIT`, `MigrationsIT` (posty). Każdą z nich trzeba osobno sprawdzić, czy zostawia dane i czy jakaś klasa tego samego kontekstu je czyta.

### Globalne odczyty — kandydaci na kruche asercje

- `PostRepositoryIT:103`, `:114` (potwierdzone, padły).
- `PipelineRunIT:303,304,331,889,915`: `count(*) FROM posts` bez filtra (`isEqualTo(16)`, `isZero()`); klasa sama sprząta (`DELETE`), ale kontekst dzieli z innymi — do sprawdzenia.
- `PipelineStartupRecoveryIT:141,149` `count(*) FROM assessments`; `RunWatchdogIT:175` i `PipelineRunIT:238` `pipeline_runs WHERE status IN ('queued','running')`; `LlmOutageIT:118-122` liczy posty/oceny globalnie.
- Asercje `before/after` (`InternalPipelineRunWithoutTokenIT:41,50`) są odporne na zostawione dane.

## Code References

- `szczur-radar-api/pom.xml:33-35,196` — ziarno `it.seed` z timestampu; losuje tylko kolejność metod (`MethodOrderer.Random` w `PipelineRunIT`, `RunWatchdogIT`), nie klas.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/TestcontainersConfiguration.java` — kontener na kontekst.
- `.../MigrationsIT.java:206, 338, 376, 419` — commity postów, brak sprzątania postów.
- `.../posts/PostRepositoryIT.java:46-48,98-114` — `@Transactional`, globalne `findLeftoverIds`.
- `.../pipeline/state/PostColumnsIT.java:226-239` — wzorzec `@AfterEach` z `DELETE`.

## Architecture Insights

- Reguła „oddzielny kontener = izolacja” z javadoc `TestcontainersConfiguration` jest myląca: izolacja wynika z klucza kontekstu, a ten bywa przypadkowo wspólny.
- Dwie drogi naprawy (do wyboru w `/10x-plan`, nie rozstrzygam): (a) lokalnie — `MigrationsIT` sprząta, `PostRepositoryIT` asertuje tylko własne posty; (b) systemowo — wspólny mechanizm czyszczenia (np. `@AfterEach`/rozszerzenie JUnit czyszczące tabele) albo wymuszenie osobnych kontekstów (`@DirtiesContext`, kosztowne czasowo). Warunek „done” z roadmapy: „test nie widzi danych zostawionych przez inny test”, co sugeruje (b) lub jej odpowiednik wraz z testem strażniczym (powtórka na różnych `-Dit.seed`).

## Historical Context (from prior changes)

- `context/foundation/roadmap.md:110` — S-30, `ready`, bez zależności; S-31 i S-45 zależą od S-30.
- `context/foundation/verification-scope.md` — „Regresja w CI”.
- Pamięć projektu `m4-first-slice-flaky-leftovers` — opis awarii i dwa warianty naprawy; właściciel wybrał S-30 jako pierwszy wycinek M-4.
- Nie przeszukiwałem `context/archive/` pod kątem wcześniejszych decyzji o izolacji testów (luka).

## Related Research

Nie dotyczy.

## Open Questions

- Czy któraś z klas „bez sprzątania” jest dziś realnie czytana przez inną w tym samym kontekście (pełna tabela klucz kontekstu → klasy)? Najtaniej: skrypt/test strażniczy lub jednorazowe uruchomienie z kilkoma `-Dit.seed`.
- Zakres naprawy: tylko znana para czy wszystkie klasy z globalnymi odczytami?
- Czy dodać test strażniczy (np. po każdej klasie sprawdza puste tabele) — koszt vs. zysk dla S-31 (scenariusze na pełnym API będą pisać dużo danych).
- Nie uruchamiałem testów; tezy o kolejności oparte na logach CI i kodzie, nie na odtworzeniu lokalnym.
