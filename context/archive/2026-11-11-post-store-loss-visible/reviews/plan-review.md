<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Post odrzucony przy zapisie nie znika po cichu (S-38)

- **Plan**: context/changes/post-store-loss-visible/plan.md
- **Mode**: Deep
- **Date**: 2026-11-11
- **Verdict**: REVISE → SOUND po poprawkach
- **Findings**: 0 critical, 3 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
9/9 paths ✓, 6/7 symbols ✓ (NUL ✗ → F1), brief↔plan ✓, Progress↔Phase ✓ (4/6/6+1)

## Findings

### F1 — PostStoreIT (c) „U+0000 w title” nie dociera do bazy

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Current State (NUL), Phase 1 §4 (c)
- **Detail**: Konstruktor `Post` usuwa U+0000 z pól tekstowych (`posts/Post.java:141-148`), więc scenariusz (c) zapisze post. NUL w `sourceItemId` wywraca się wcześniej, na `findKeys` w `storedKeys`. RAW to gzip `BYTEA`. Fallback `SQLState` bez realnego testu.
- **Fix**: Usunąć (c) z `PostStoreIT`, poprawić zdanie o NUL, fallback `SQLState` w `PostStoreTest` z przyczyną Hibernate `DataException` (22021), dopisek w „Czego NIE robimy”.
- **Decision**: FIXED

### F2 — Wzór `RunRecorder.source` łamie „oba albo żaden” w `storeRejected`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 2 §1
- **Detail**: CTE modyfikujące dane w Postgresie wykonuje się zawsze, więc bez wiersza źródła `skipped` przebiegu i tak rośnie; kontrakt i `RunRecorderIT` byłyby sprzeczne z kodem skopiowanym z `source` (`RunRecorder.java:143-147`).
- **Fix**: `AND EXISTS (wiersz źródła)` w `UPDATE pipeline_runs` w CTE, wiersz przebiegu pierwszy (jak `openStep`), `UPDATE pipeline_run_sources … FROM run`.
- **Decision**: FIXED

### F3 — Kryteria PR env niestabilne

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3, kroki 2 i 5, kryterium 3.2
- **Detail**: `skipped` = liczba WARN pomija pominięte przy mapowaniu; wielkość liter subredditu z `REDDIT_SUBREDDITS` może różnić się od `channel`; `T` z zegara agenta, `created_at` z `@CreationTimestamp`; brak uwagi o `-p <project>`.
- **Fix**: `skipped` = mapowanie (linia INFO) + WARN; subreddit z `SELECT DISTINCT channel`; `T` z `SELECT now()`; uwaga o `-p`.
- **Decision**: FIXED

### F4 — Równość „w każdym przebiegu zakresu” to nadmierna obietnica

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Overview, Desired End State
- **Detail**: Przebieg przerwany w store albo między `count` a `storeRejected` nie zapisuje `new_posts`/odrzuceń; tekst `store rejected …` powstaje dopiero w `assess`.
- **Fix**: Zawęzić do przebiegu ze store `succeeded`; wyjątek dopisany do „Czego NIE robimy”.
- **Decision**: FIXED

### F5 — Fallback pgjdbc się nie skompiluje; testy jednostkowe bez przyczyny

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Critical Implementation Details
- **Detail**: Sterownik ma `<scope>runtime</scope>` (`pom.xml:91`); Hibernate 7.2 sam podaje nazwę dla 23505/23514/23503; `PostStoreTest` rzuca goły wyjątek bez przyczyny.
- **Fix**: Usunąć fallback pgjdbc; testy budują wyjątek z przyczyną Hibernate `ConstraintViolationException`.
- **Decision**: FIXED

### F6 — Status roadmapy zmieniony bez renderera i SVG

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 §4
- **Detail**: `roadmap.md` ma S-38 `planning`, `tools/render_roadmap_svg.py:211` wciąż `ready`; reguła repo wymaga renderera i `docs/visuals/roadmap.svg`.
- **Fix**: Wpis w Phase 2 §4: status w roadmapie i rendererze (`progress`), `python3 tools/render_roadmap_svg.py`.
- **Decision**: FIXED
