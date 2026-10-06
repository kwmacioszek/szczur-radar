<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Obraz Dockera dla szczur-radar-api i wyłączenie PITR

- **Plan**: context/changes/api-docker-image/plan.md
- **Mode**: Deep
- **Date**: 2026-10-04
- **Verdict**: REVISE → SOUND po triage
- **Findings**: 0 critical, 3 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
10/10 paths ✓, 5/6 symbols ✓ (pom.xml:149 → faktycznie :138), brief↔plan ✓, Progress↔Phase ✓

## Findings

### F1 — „Backup bazy: odłożone” po cichu usuwa warunek przed prawdziwymi danymi

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 §4, Migration Notes
- **Detail**: PRD:251, ryzyko #1 w infrastructure.md, deploy-plan.md:146/181 i roadmapa (Block F-01 → S-05) wymagają backupu i testu odtwarzania przed zbieraniem prawdziwych danych. Plan zamieniał to na „odłożone” bez zachowania blokady S-05.
- **Fix**: Zachować warunek przed S-05.
- **Decision**: FIXED (Fix differently) — zamiast PITR dzienny backup wolumenu Railway (Daily, 6 dni) włączony na produkcji przed `pitr disable` (krok 0 runbooka, stop gdy trial blokuje); test odtwarzania pozostaje warunkiem przed S-05; dokumenty „Backup bazy: dzienna kopia wolumenu Railway”.

### F2 — Okno między cleanupem produkcji a merge: stary IaC odtwarza bucket

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Implementation Approach; Phase 1 §5 runbook krok 3
- **Detail**: Push do main między cleanupem a merge stosuje stary railway.ts (resource.create bucketa nie jest blokowany); po merge bramka blokuje jego usunięcie.
- **Fix**: Zamrożenie main, bez ponownych uruchomień starych workflow, plan = 0 po merge.
- **Decision**: DISMISSED — tylko użytkownik developuje i kontroluje merge.

### F3 — PR env: pierwszy push z nowym IaC kończy się czerwonym CI

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 §5 Contract
- **Detail**: PR env skopiowany z produkcji ma bucket; bramka blokuje jego usunięcie, Wait for CI pomija deploy, ponowienie joba może go nie wznowić.
- **Fix**: Cleanup PR env przed pushem z IaC; awaryjnie `railway redeploy --from-source`.
- **Decision**: ACCEPTED — w razie potrzeby ręczny deploy.

### F4 — Web w PR env zostanie pominięty; nieaktualny numer linii

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Manual Testing Steps; Phase 2 §3
- **Detail**: PR nie zmienia szczur-radar-web/**, więc web w PR env będzie SKIPPED i nie da się sprawdzić 1.9; pom.xml:149 wskazuje surefire, komentarz finalName jest w :138.
- **Fix**: Ręczny `railway redeploy --from-source -s szczur-radar-web` przed 1.9; :149 → :138.
- **Decision**: FIXED
