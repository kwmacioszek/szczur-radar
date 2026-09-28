<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Pierwszy admin przez CLI, logowanie/wylogowanie i ochrona tras

- **Plan**: context/changes/admin-bootstrap-and-login/plan.md
- **Mode**: Deep (weryfikacja kodu inline, bez podagenta)
- **Date**: 2026-09-28
- **Verdict**: REVISE → SOUND po poprawkach
- **Findings**: 1 critical, 2 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | FAIL → PASS (F1 fixed) |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING → PASS (F2, F4 fixed) |
| Plan Completeness | WARNING → PASS (F3 fixed) |

## Grounding
10/10 paths ✓, 5/5 symbols ✓ (preDeploy, randomString, get_engine/_engine, deploy-plan:113, roadmap S-01=planning), brief↔plan ✓, Progress↔Phase ✓

## Findings

### F1 — Brak `railway config apply` i kolejności rolloutu

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: End-State Alignment
- **Location**: Phase 1 §4, Phase 2 §6, Phase 4
- **Detail**: `preDeploy` dodany w `.railway/railway.ts` nie wchodzi z merge — wymaga `railway config apply` (`deploy-plan.md:115`); plan tylko sprawdzał `config plan`. Po merge produkcja wystartowałaby bez tabeli `users`. PR env od fazy 2 nie wystartowałby bez `JWT_SECRET` (sealed nie jest dziedziczony); apply na prod przed merge odpalałby alembic na starym kodzie.
- **Fix A ⭐ Recommended**: Ręcznie rozpisana kolejność rolloutu + kryterium weryfikacji preDeploy.
- **Fix B**: preDeploy w `szczur-radar-api/railway.json` jadącym z commitem.
- **Decision**: FIXED — inaczej (wariant użytkownika): job CI `railway-config` robi `config plan` (fail przy usunięciu lub zmianie `source`) → `config apply`; PR → `szczur-radar-pr-<N>` (PR env dziedziczy po prod, ale zmiany z gałęzi trzeba zastosować osobno), push na `main` → `production`; „Wait for CI” gwarantuje konfigurację przed kodem. `JWT_SECRET` ręcznie w PR env przed fazą 2 i sealed na prod przed merge. Plan: faza 1 §8, faza 2 §10, faza 4 §1/§3, Progress 1.7, 1.8, 2.7, 4.7. Doprecyzowanie po teście na tymczasowym PR #10 (2026-09-28, tylko `plan`): `RAILWAY_API_TOKEN` = token workspace (token projektu obejmuje jedno środowisko), wybór środowiska przez `RAILWAY_PROJECT_ID` + `RAILWAY_ENVIRONMENT_ID` (`railway link` z tokenem workspace → „Unauthorized”); wykryty dryf `railway.ts` vs prod (apply skasowałby bucket `Postgres-PITR` i wyłączył uśpienie) → nowy krok 0 w fazie 1 (§0, Progress 1.9).

### F2 — Test kompletności autoryzacji a zależność DB

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 §3, §8
- **Detail**: `current_user` dostaje sesję przez `Depends`, a `get_engine()` robi `os.environ["DATABASE_URL"]` (`database.py:38`) — bez bazy test dostaje 500 zamiast 401. Brak reguły wypełniania parametrów ścieżki przyszłych tras.
- **Fix**: Marker `db` na teście; parametry ścieżki wypełniane atrapą właściwego typu z sygnatury trasy.
- **Decision**: FIXED

### F3 — Konfiguracja Vitest złamie `vue-tsc -b`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §1, §5
- **Detail**: Klucz `test` w `vite.config.ts` nie przejdzie typechecku (tsconfig.node: `types: ["node"]`); `src/**/*.test.ts` wchodzą do tsconfig.app bez typów Vitest.
- **Fix**: Osobny `vitest.config.ts` (`vitest/config`) w `include` tsconfig.node.json; jawne importy z `'vitest'`.
- **Decision**: FIXED

### F4 — Nie sprawdzamy podszywania się pod `X-Real-IP`

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 4 §2, Progress 4.4
- **Detail**: Weryfikacja sprawdzała tylko, że API widzi prawdziwy adres, a nie że nagłówek wysłany przez klienta jest nadpisywany przez krawędź Railway.
- **Fix**: `curl -H 'X-Real-IP: 1.2.3.4'` w weryfikacji PR env → API widzi prawdziwy adres.
- **Decision**: FIXED
