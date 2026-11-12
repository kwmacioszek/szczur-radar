<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-43 — atrapy odpowiadają jak prawdziwe API

- **Plan**: context/changes/contract-bound-mocks/plan.md
- **Mode**: Deep
- **Date**: 2026-11-11
- **Verdict**: SOUND
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | WARNING |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
13/13 paths ✓, 12/12 symbols ✓, brief↔plan ✓, Progress↔Faza ✓ (7/6/5/4 rows)

Verified in code (sub-agent): contract files have no key-set unions (the "exactly these keys" rule works); the night-summary text is deterministic and holds no UUID/instant (`ContractFiles.mask` masks whole values only, `:60,63`); `wait-safe.sh:91-97` reads `reason`; IaC plan fixtures are read by name, so `fixtures/api/` breaks nothing; `src/contract/mockApi.ts` typechecks under `tsconfig.app.json` (precedent `samples.ts`); login without fields → 400 `invalid_request` (`LoginRequest` `@NotNull`); restore with a non-UUID element → 400 `invalid_request` (Jackson binding, `ApiExceptionHandler:22-24`).

## Findings

### F1 — Kryterium 2.3 („każdy wiersz czerwony na starej atrapie”) jest niespełnialne

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2 — Overview, §6, Success Criteria 2.3
- **Detail**: Część wierszy już przechodzi na starej atrapie: 401 na `/me` w `anonymous` (`mock:400-403`), 404 dla nieznanego UUID przebiegu/powiadomienia/postu (`mock:432-435`, `:446-448`, `:500`), 400 dla `page > 21474836` (`mock:476`), 204 wylogowania, 400 restore dla `[]`/null (`mock:458-460`). `HEAD~` jest niejednoznaczne przed commitem fazy 2.
- **Fix**: Czerwone na atrapie z commitu fazy 1 mają być wiersze 12 rozbieżności i `/raw` Reddita; pozostałe oznaczone „zgodne przed fazą”; plik bazowy `git show <sha fazy 1>:…`.
- **Decision**: FIXED

### F2 — Start `postIds` nie odpowiada 400 `post_dead`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 §3 i §6
- **Detail**: API odrzuca przebieg `posts` z martwym postem 400 `post_dead` (`PipelineRunService.java:198-200`, `PipelineRunController.java:97`); SPA go obsługuje (`src/api/pipeline.ts:172`). Atrapa ma martwy post `p3`, a kontrakt `START='accepted'` dałby 202.
- **Fix**: `postIds` z martwym postem → 400 `post_dead` bez nowego przebiegu; wiersz w tabeli zachowań.
- **Decision**: FIXED

### F3 — `ready.json` niczego nie chroni w bramce

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Faza 4 §1–2
- **Detail**: `verify-apply.sh:311-321` i `lib.sh:40-48` sprawdzają tylko kod HTTP `/api/ready`; rozjazd ciała jest nieszkodliwy dla `test.sh`.
- **Fix**: Zostawić `ready.json` i zapisać w planie, że służy wierności stuba, nie bramce.
- **Decision**: FIXED

### F4 — Dwa `started-runs.json` o różnych kształtach

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 3 §2–3
- **Detail**: `/daily` zwraca `List<StartedRun(id, kind)>` (`InternalPipelineRunController.java:83-87`), a `web/src/contract/started-runs.json` to `RunView[]`.
- **Fix**: Plik crona nazwany `daily-started.json`.
- **Decision**: FIXED

### F5 — Wytyczne nie mówią o `tools/*/fixtures/api/` przypiętych przez IT

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 2 §7, fazy 3–4
- **Detail**: Wpisy `tools/pipeline-trigger/` i `tools/deploy-gate/` w `REPOSITORY_GUIDELINES.md` nie wspominają `fixtures/api/`, choć wpis `tools/healthchecks/` opisuje swój wspólny plik.
- **Fix**: Po jednym zdaniu w obu wpisach (faza 3 §4, faza 4 §3).
- **Decision**: FIXED
