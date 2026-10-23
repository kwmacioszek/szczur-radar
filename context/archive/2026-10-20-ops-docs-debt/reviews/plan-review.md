<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-19: dokumenty operatora mówią prawdę

- **Plan**: context/changes/ops-docs-debt/plan.md
- **Mode**: Deep
- **Date**: 2026-10-20
- **Verdict**: REVISE → SOUND po triage (7/7 FIXED)
- **Findings**: 0 critical, 4 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

6/6 paths ✓, 5/5 symbols ✓ (`gate.jq` odrzuca `variable.delete` ✓, brak innych referencji do 3 zmiennych ✓, `ci.yml` `RAILWAY_API_TOKEN` ✓), brief↔plan ✓; status SVG ✗ (F3).

## Findings

### F1 — Otwarte PR env zrobią się czerwone po merge'u S-19

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Faza 3 — runbook „after the merge”; Critical Implementation Details
- **Detail**: Plan uwzględnia tylko PR env tego PR-a i produkcję. Otwarty PR #92 (deploy-verification-hardening) ma PR env skopiowany z produkcji, więc ma `RAILPACK_JDK_VERSION` i `DATABASE_URL`. Gdy wciągnie `main` z S-19, jego plan IaC pokaże `variable.delete`, a `gate.jq` odmówi (README bramki :58-59), co zablokuje też jego deploy.
- **Fix**: W runbookach „before/after the merge”: wylistuj otwarte PR-y (`gh pr list`), w każdym istniejącym PR env usuń 3 zmienne (sprawdzenie przez `jq 'keys'`), zanim jego gałąź wciągnie `main`; wynik w komentarzu #78. W sekcji S-19 jedno zdanie dla przyszłych PR-ów: PR env sprzed S-19 → usuń zmienne przed rebase'em.
  - Strength: Ten sam mechanizm, który plan już stosuje; dziś to jeden PR (#92).
  - Tradeoff: Agent dotyka PR env cudzej zmiany (non-prod).
  - Confidence: HIGH — `gate.jq` i README bramki potwierdzają odmowę; #92 jest otwarty.
  - Blind spot: Czy PR env #92 nadal istnieje i ma te zmienne — nie sprawdzone.
- **Decision**: FIXED (Fix in plan)

### F2 — Sekcja S-19 odtworzy dług, który S-19 spłaca

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Faza 3 — sekcja S-19 (punkt „status”)
- **Detail**: Wszystkie 8 nieaktualnych statusów ma tę samą przyczynę: status pisany na gałęzi przed merge'em („not merged”), którego po merge'u nikt nie poprawia. Plan nie określa brzmienia statusu S-19, więc najpewniej powstanie dziewiąty taki wpis.
- **Fix A ⭐ Recommended**: Status S-19 w formie niezależnej od merge'a („delivered by PR #N; production verification and closure in issue #78”) + lekcja przez `/10x-lesson`: statusy w `deploy-plan.md` odsyłają do issue zamiast pisać „not merged”.
  - Strength: Usuwa przyczynę, nie tylko objaw.
  - Tradeoff: Sekcja nie mówi wprost „live on production”; trzeba zajrzeć do issue.
  - Confidence: HIGH — wzorzec przyczyny widoczny w 8 sekcjach.
  - Blind spot: Czy inne skille nie wymuszają starego brzmienia — nie sprawdzone.
- **Fix B**: Tylko brzmienie statusu S-19, bez lekcji.
  - Strength: Minimalna zmiana planu.
  - Tradeoff: Następna zmiana (np. #92) wpisze kolejne „not merged”.
  - Confidence: HIGH
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A)

### F3 — Nazwa statusu w SVG to "progress", nie "in-progress"

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 — #6 Roadmapa i SVG
- **Detail**: `tools/render_roadmap_svg.py:26-32` zna tylko `done`, `progress`, `ready`, `proposed` i `blocked`; `"in-progress"` daje `KeyError` w `STATUS[status]` (:110), a kryterium 1.4 jest czerwone.
- **Fix**: S-19 → `"progress"` (:174, :286); usunąć zastrzeżenie „sprawdź nazwę”.
- **Decision**: FIXED (Fix in plan)

### F4 — Kolizja z otwartym PR #92 (deploy-verify, deploy-plan.md)

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2 — kryterium 2.3; Faza 1 — #2 (sekcja S-14)
- **Detail**: #92 usuwa `deploy-verify.yml` i zmienia runbook S-14 w `deploy-plan.md`. Kryterium 2.3 odwołuje się do `deploy-verify`, a faza 1 przepisuje status S-14, więc jeśli #92 zmerguje pierwszy, będą konflikty.
- **Fix**: Kryterium 2.3: „wszystkie workflow na commicie PR zielone, w tym `railway-config`, i brak nowego alertu po deployu (deploy-verify albo monitor z #92, zależnie od `main`)”. W fazie 1: przed edycją `git fetch` + rebase na `origin/main`; konflikty w `deploy-plan.md` na korzyść treści z `main`.
- **Decision**: FIXED (Fix in plan)

### F5 — Niespójne okna nocne i nazwa zmiany w planie IaC

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Critical Implementation Details vs Faza 3 #2; Key Discoveries
- **Detail**: „poza 02:00 UTC” vs „01:50–02:30 UTC”; „variable.create” zamiast `variable.set`, który bramka zna (README bramki :59, :208).
- **Fix**: Ujednolicić do „00:30–01:30 i 01:50–02:30 UTC” oraz „`variable.set` (dozwolone)”.
- **Decision**: FIXED (Fix in plan)

### F6 — Stan końcowy obiecuje status w każdej sekcji

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Desired End State, pkt 1
- **Detail**: „Każda sekcja zmiany ma status z datą, commitem merge'a i stanem issue”, a starsze sekcje (F-03, api-docker-image, F-02, S-05, S-07, S-03) nie mają linii Status, kryterium 1.1 sprawdza tylko „not merged”.
- **Fix**: „Każda istniejąca linia Status jest prawdziwa”; nowych linii w sekcjach bez statusu nie dopisujemy.
- **Decision**: FIXED (Fix in plan)

### F7 — Skutek `railway variable delete` niezweryfikowany przed produkcją

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 #2 → Faza 3 #2
- **Detail**: Plan zakłada „może przedeployować API”, ale nie ustala tego; Railway może też zostawić zmianę jako staged (`deploy-plan.md:208`).
- **Fix**: W fazie 2, po usunięciu w PR env, zapisać skutek (MCP `get-staged-changes`, nowy deployment w `list-deployments`); runbook produkcyjny opiera się na tym wyniku.
- **Decision**: FIXED (Fix in plan)
