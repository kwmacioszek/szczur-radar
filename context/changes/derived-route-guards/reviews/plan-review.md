<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Strażnik tras admina i `/internal` bez ręcznej listy (S-40)

- **Plan**: context/changes/derived-route-guards/plan.md
- **Mode**: Deep
- **Date**: 2026-11-11
- **Verdict**: SOUND
- **Findings**: 0 critical, 1 warning, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS |
| Plan Completeness | WARNING |

## Grounding
Grounding: 9/9 paths ✓, 8/8 symbols ✓, brief↔plan ✓, Progress↔Phase ✓. Zweryfikowane w kodzie: `OriginGuardFilter` przepuszcza POST bez `Origin` (403 na trasach admina pochodzi z reguły roli); `PipelineTriggerToken.NAME + "="` jest unikalne dla `InternalPipelineRunWithoutTokenIT`, a `InternalPipelineRunIT` ma `TestBeans` (sondy nie tworzą nowego kontekstu); w `src/main` brak `RouterFunction`, `WebMvcConfigurer` i `static/`; jedyny kontroler `/internal` to `InternalPipelineRunController`; `ADMIN_ROUTES` występuje dokładnie w 4 miejscach wskazanych przez plan.

## Findings

### F1 — Cofnięcie przebiegu psującego kasuje javadoc SecurityConfig

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1, Success Criteria 1.2 (i 2.2)
- **Detail**: Zmiana 1.4 edytuje javadoc `SecurityConfig`, a kryterium 1.2 cofa przebieg psujący tego samego pliku przez `git checkout -- szczur-radar-api/src/main`, co przywraca HEAD i kasuje niezatwierdzony javadoc. Ścieżka jest względna do korzenia repo, a polecenie robi `cd szczur-radar-api`.
- **Fix**: W 1.2 cofnięcie ręczne + `git diff --stat` z korzenia repo (tylko javadoc); w 2.2 cofnięcie i `git status` z korzenia repo.
- **Decision**: FIXED

### F2 — „Bez sesji i ciasteczka” obiecane dla `InternalPipelineRunWithoutTokenIT`, nie zbudowane

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Desired End State (l. 23) vs Phase 2, zmiana 3
- **Detail**: Stan końcowy obiecywał „bez sesji i ciasteczka” dla obu klas; zmiana 2.3 sprawdza tylko 401, treść i brak przebiegu, a helper `internal(...)` jest prywatny w `InternalPipelineRunIT`.
- **Fix**: Zawężenie stanu końcowego — kontrola ciasteczka i sesji tylko w `InternalPipelineRunIT`.
- **Decision**: FIXED (zawężony stan końcowy)
