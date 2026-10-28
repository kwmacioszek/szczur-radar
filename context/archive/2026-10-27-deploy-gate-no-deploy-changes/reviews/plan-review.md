<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-21: zmiana IaC bez deployu nie blokuje wdrożeń

- **Plan**: context/changes/deploy-gate-no-deploy-changes/plan.md
- **Mode**: Deep
- **Date**: 2026-10-28
- **Verdict**: REVISE (po triage: SOUND)
- **Findings**: 2 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | FAIL |
| Plan Completeness | FAIL |

## Grounding
Grounding: 9/9 paths ✓, 6/7 symbols ✓ (MCP update-service has no checkSuites ✗), brief↔plan ✓

Weryfikacja źródeł CLI v5.67.1 (sub-agent): `meta` przechodzi bez zmian (`Deployments.graphql:8`, `gql/queries/mod.rs:7`, `deployment.rs:62,136`); `--limit` 1–1000 (`output/limit.rs:4,8-26`); CLI nie filtruje statusów (`deployment.rs:109-110`); kolejność listy to domyślne zachowanie API (brak sortowania i `orderBy`).

## Findings

### F1 — PR env tego PR-a nie będzie miał żadnego deploymentu

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Blind Spots
- **Location**: Phase 2 (całość), kryteria 2.1, 2.3–2.5
- **Detail**: PR zmienia tylko `tools/deploy-gate/`, `context/`, `.railway/railway.ts`, `ci.yml` i `REPOSITORY_GUIDELINES.md`, czyli żadnego obserwowanego katalogu usługi. Według `deploy-plan.md:1453-1455` taki PR env nie ma żadnego deploymentu. Każdy run z apply'em kończy się wtedy exit 1 na `/api/ready`, `pipeline-trigger` bez deploymentu nie dostanie deploymentu IaC (2.5), a pushe nie tworzą wierszy `SKIPPED` (2.4).
- **Fix A ⭐ Recommended**: usługi PR env działają przed scenariuszami
  - Strength: zostają trzy scenariusze na żywym Railway.
  - Tradeoff: buildy w PR env, krok przygotowawczy.
  - Confidence: MED — reguła „deployuje tylko dotknięte usługi” jest potwierdzona (`deploy-plan.md:64,245,1455`).
  - Blind spot: czy `redeploy --from-source` działa dla usługi bez żadnego deploymentu (stąd plan awaryjny).
- **Fix B**: bez apply w PR env; przypadek pozytywny tylko do odczytu na produkcji w 1.4; pierwszy żywy apply po merge'u
  - Strength: tanio, bez niewiadomych.
  - Tradeoff: pierwszy żywy apply nowej kontroli dopiero na produkcji.
  - Confidence: HIGH.
  - Blind spot: stare deploymenty IaC są `REMOVED` (reguła z F4).
- **Decision**: FIXED via Fix A, wariant z `railway redeploy --from-source` (wzór `deploy-plan.md:1034`); README + close/reopen PR-a jako plan awaryjny. Nowa zmiana 0 w fazie 2, kryterium 2.2 rozszerzone o katalogi usług, wiersz fazy 2 w briefie.

### F2 — Railway MCP nie ustawi checkSuites

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2, zmiana 1; Prerequisites w plan-brief.md
- **Detail**: MCP `update-service`: „Source changes are not handled by this tool”; `connect-service-source` nie ma `checkSuites` i na żywo działa na wszystkie środowiska. CLI ma `railway environment edit -e <env> --service-config <svc> source.checkSuites <bool>` (`environment/mod.rs:152-161`, `EnvironmentPatchCommit` dla jednego środowiska).
- **Fix**: CLI `railway environment edit` tylko w PR env, potwierdzenie MCP `get-service-config`, pusty commit; awaryjnie operator w panelu.
- **Decision**: FIXED

### F3 — Choreografia trzech pushy niedookreślona

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Completeness
- **Location**: Phase 2, zmiany 2–3, kryteria 2.1, 2.4, 2.5
- **Detail**: Push 2 musi trafić w ~2-minutowe okno runu 1 (brak metody i planu awaryjnego). Push 3 przed końcem runu 2 daje deploymentowi IaC `commitHash` commita 3, a w czasie oczekiwania `railway-config` w grupie `concurrency` anuluje run 2 (`ci.yml:285-287`).
- **Fix**: moment pusha 2 według kroku „Deployments before the apply” (`gh run view --json jobs`, ≤ 90 s), chybienie → 2.4 „nieodtworzone w PR env”; push 3 po konkluzji runu 2 i `SUCCESS` jego deploymentu IaC.
- **Decision**: FIXED

### F4 — REMOVED może ocenić SKIPPED pusha

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1, kontrakt `check`, przypadek (f)
- **Detail**: „Najnowszy nad nim, dowolnego pochodzenia” przy [push `SKIPPED`, push `SUCCESS`, IaC `REMOVED`, granica] daje exit 1 — ta sama klasa fałszywego alarmu, którą S-21 usuwa; brak reguły, gdy nad `REMOVED` nic nie ma.
- **Fix**: najnowszy nad nim o statusie innym niż `SKIPPED`, brak → czekaj; (f) z pushem `SKIPPED` na wierzchu.
- **Decision**: FIXED

### F5 — Okno > limit = exit 2 psuje przypadki z limitem 2s

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1, `DEPLOY_VERIFY_DEPLOY_WINDOW`; `test.sh`
- **Detail**: Przy domyślnym oknie 2m przypadki z `DEPLOY_VERIFY_TIMEOUT=2s` (`cli-fails`, `limit`, (h)) dają exit 2; `run_verify` (`test.sh:549`) nie zeruje nowej zmiennej; walidacja tylko w `check` wychodzi po apply.
- **Fix**: walidacja razem z innymi czasami w obu trybach; `run_verify` zeruje zmienną; przypadki 2s z oknem ≤ limit.
- **Decision**: FIXED

### F6 — Kolejność listy to domyślne zachowanie API, nie kontrakt

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Implementation Approach („lista jest od najnowszego”)
- **Detail**: CLI 5.67.1 nie sortuje (`deployment.rs:122-127`), zapytanie bez `orderBy`; CLI sortuje gdzie indziej po stronie klienta (`down.rs:71`, `logs.rs:810`). Migawka i granica opierają się na kolejności API.
- **Fix**: założenie w nagłówku `verify-apply.sh`; 1.4 sprawdza malejące `createdAt` (sortowanie w samym `check` nie usuwa zależności migawki).
- **Decision**: FIXED
