<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Feed z testami regresji i granicą numeru strony

- **Plan**: context/changes/feed-regression-and-paging-bounds/plan.md
- **Mode**: Deep (sprawdzone lokalnie, bez sub-agenta)
- **Date**: 2026-11-06
- **Verdict**: SOUND (po triage; przed nim 1 ostrzeżenie)
- **Findings**: 0 critical, 1 warning, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING (F1, naprawione) |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS |
| Plan Completeness | PASS |

## Grounding

10/10 paths ✓, 6/6 symbols ✓, brief↔plan ✓, Progress↔Phase ✓.

## Findings

### F1 — SPA: ogromne `?page=` nadal zawiesza feed

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Faza 1 i Faza 3; roadmapa S-44, „Outcome”
- **Detail**: `FeedView.vue:34-37` bierze `?page=` z adresu bez górnej granicy i wysyła je z `PAGE_SIZE = 50`. Po poprawce API link `/?page=99999999999` dostaje 400, ale SPA zostaje w błędzie przy każdym odświeżeniu, jak z NUL.
- **Fix A ⭐ Recommended**: SPA traktuje `page` ponad granicą jak wartość nieznaną i wraca na stronę 0; granica w `rules.json`.
  - Strength: Domyka „bez zawieszonego feedu” dla obu defektów.
  - Tradeoff: Nowa kopia reguły w SPA (wpis w rejestrze, testy po obu stronach).
  - Confidence: HIGH — kod i wzorzec rejestru potwierdzone.
  - Blind spot: Granica liczona od `size` 100 (API), nie 50 (SPA).
- **Fix B**: Nie zmieniać SPA, zapisać w „What We're NOT Doing”.
  - Strength: Mniejszy zakres.
  - Tradeoff: Wynik z roadmapy tylko w połowie.
  - Confidence: MED
  - Blind spot: Zakres „zawieszonego feedu” w oczach właściciela.
- **Decision**: FIXED (Fix A) — faza 3 rozszerzona o granicę `page` w `FeedView`, `rules.json`, `SpaRulesTest`, `rules.test.ts`; Progress 3.5.

### F2 — `PageLimitsTest` powiela `SpaRulesTest`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Faza 1, punkt 3(c)
- **Detail**: `SpaRulesTest.java:62` już porównuje `runsPageMaxSize` z `PipelineRunController.MAX_PAGE_SIZE`.
- **Fix**: Zostawić w `PageLimitsTest` tylko `MAX_PAGE * MAX_SIZE ≤ Integer.MAX_VALUE`.
- **Decision**: FIXED

### F3 — Wiersze „czerwone przed / po zepsuciu” nie dają się sprawdzić po fakcie

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Progress 1.1, 1.3, 2.2, 3.3
- **Detail**: Czerwony stan nie trafia do historii (commit na zielonej fazie); dowód istnieje tylko w opisie commita.
- **Fix**: Wynik (test i asercja) w opisie commita fazy; wiersz odhaczany po tym zapisie.
- **Decision**: FIXED
