<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-14 Bezpieczna bramka wdrożeń

- **Plan**: context/changes/safe-deploy-gate/plan.md
- **Mode**: Deep
- **Date**: 2026-10-18
- **Verdict**: REVISE → SOUND (po triage)
- **Findings**: 2 critical, 4 warnings, 2 observations

## Verdicts

Werdykty sprzed triage. Wszystkie ustalenia są poprawione w planie.

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | FAIL |
| Lean Execution | WARNING |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | FAIL |

## Grounding

13/13 paths ✓, 5/5 symbols ✓ (`existsActive`, `existsByStatusIn`, `PUBLIC_ROUTES` `Map.of`, matcher `SecurityConfig.java:102`, `uq_pipeline_runs_one_running`), brief↔plan ✓, Progress↔Phase ✓.

Sub-agent zweryfikował zachowanie zewnętrzne na źródle CLI `railwayapp/cli` (v5.67.1), danych GitHub Deployments i statusów commitów z tego repo oraz dokumentacji GitHuba i Railway.

Potwierdził też założenia planu:
- `--json` i `--out` działają razem w jednym wywołaniu planu;
- `@railway/cli@5.67.1` istnieje na npm;
- prefiks we wzorcu `variables.ignore` (np. `pipeline-trigger/TRIGGER_*`) to nazwa usługi;
- `managed` domyślnie jest `true`, a Postgres nie daje błędu;
- `deployment_status` używa pliku workflow z SHA deployu;
- run, którego jedyny job pominął `if:`, kończy się `skipped` i nie blokuje Wait for CI. Dotyczy to też `in_progress`, który Railway wysyła już w stanie `WAITING`.

## Findings

### F1 — Stare API odpowie 401, a nie 404

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Critical Implementation Details → State sequencing; Phase 4 §1; 4.4; 6.3
- **Detail**: Dziś każda trasa `/api/**` wymaga sesji (`anyRequest().authenticated()` + `JsonAuthenticationEntryPoint`, `SecurityConfig.java:86-87`). Spring Security odrzuca żądanie bez sesji do nieistniejącej trasy odpowiedzią 401, zanim zadziała routing. Kontrakt `wait-safe.sh` nie obsługiwał 401, więc pierwszy push w PR env i pierwszy merge na produkcję by się zablokowały.
- **Fix**: 401 i 404 → „API bez endpointu (starsza wersja)”, exit 0 z `::warning::`. Przypadek 401 w testach, poprawione State sequencing, 4.4, 6.3, Migration Notes i brief.
- **Decision**: FIXED

### F2 — Kroki po merge'u w Progress (łamie lekcję)

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 6 §3 + Progress 6.3–6.7
- **Detail**: Lekcja „Implementacja, przegląd i archiwizacja na jednej gałęzi” (`lessons.md:75-80`) zabrania umieszczania w Progress kroków możliwych dopiero po merge'u, bo blokują `/10x-archive` przed merge'em. Kroki 6.3–6.7 były w całości weryfikacją produkcji po merge'u.
- **Fix**: Phase 6 §3 to teraz runbook w sekcji S-14 `deploy-plan.md` (wynik trafia później jako komentarz w #73). W Progress został jeden krok przed merge'em: 6.3 „runbook zapisany”. Kroki 1.7 i 2.7 (lokalny odczyt) zostały.
- **Decision**: FIXED

### F3 — Przypięty plan nie przeżyje 90 min strażnika

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Desired End State (kolejność 1–5); Phase 4 §4
- **Detail**: `apply --plan` zapisuje etag środowiska i odmawia, gdy środowisko zmieniło się od planu (`src/iac/saved_plan.rs:245-256`, CLI v5.67.1; serwer dostaje też `baseConfigEtag`). Przy kolejności plan → gate → strażnik (≤90 min) → apply każda zmiana w trakcie czekania dałaby czerwony job, a ten przez Wait for CI pomija deploy kodu.
- **Fix A ⭐ Recommended**: Kolejność strażnik → plan --out → gate → apply --plan.
  - Strength: Przypięcie obejmuje sekundy; gwarancja „stosujesz to, co sprawdziła bramka” zostaje.
  - Tradeoff: Odrzucenie przez bramkę wychodzi dopiero po strażniku (zwykle sekundy).
  - Confidence: HIGH — semantyka etagu potwierdzona w źródle CLI.
  - Blind spot: None significant.
- **Fix B**: Plan+gate przed strażnikiem, po strażniku ponowny plan --out + gate, potem apply.
  - Strength: Odrzucenie widać od razu.
  - Tradeoff: Dwa plany i dwie bramki; więcej logiki w YAML.
  - Confidence: MEDIUM.
  - Blind spot: Restarty trzeba brać z drugiego planu.
- **Decision**: FIXED (Fix A)

### F4 — Budżet 2 h liczony względnie; deploy pominięty po cichu

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Timing & lifecycle; Phase 4 §1, §4; runbook Phase 6
- **Detail**: Limity (90 + 15 min, `timeout-minutes` 115) liczyły się od startu joba. Kolejka runnerów, wolny `backend` albo czekanie w grupie `concurrency` mogą przekroczyć 120 min od pusha. Railway pomija wtedy deploy i oznacza GitHub Deployment jako `inactive`; ten stan nie uruchamia `deploy-verify`, a `ci.yml` jest zielony. Kod się nie wdraża i nikt tego nie widzi. „Re-run failed jobs” po porażce strażnika też zwykle wypada już po oknie 2 h.
- **Fix**: `DEPLOY_GUARD_DEADLINE` liczony od `created_at` runu (`gh api …/actions/runs/$GITHUB_RUN_ID`, `permissions: actions: read`). Strażnik czeka do min(limit, termin), a po terminie kończy się na czerwono z komunikatem „deploy kodu nie nastąpi”. Dwa nowe przypadki testowe. Runbook: nowy push albo `railway redeploy` zamiast samego Re-run.
  - Strength: Cichy brak wdrożenia zamienia się w czerwony sygnał.
  - Tradeoff: Jedno wywołanie `gh api` i parametr terminu w testach.
  - Confidence: HIGH — `inactive` przy pominięciu zaobserwowane na 77fde0ec; GitHub docs: `inactive` nie wyzwala workflow.
  - Blind spot: Od czego dokładnie Railway liczy okno 2 h (push czy utworzenie deployu).
- **Decision**: FIXED

### F5 — Bramka: lista dozwolonych czy lista odrzuceń

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §1 — reguły 2–5
- **Detail**: Nieznana wartość `kind` nie była obsłużona: reguła fail closed działała tylko przy braku pola. Dopisek „`resource.delete` (obejmuje `variable.delete`)” był nieprawdą, bo `variable.delete` łapało wyłącznie `severity`.
- **Fix**: Jawna lista dozwolonych rodzajów (`resource.create`, `resource.update`, `variable.set`, `domain.create`); każdy inny `kind` jest odrzucany niezależnie od `severity`. Dwie nowe fixture'y: nieznany `kind` oraz `variable.delete` z `severity: "safe"`.
- **Decision**: FIXED

### F6 — Kontrola po apply nie określa przypadku „brak nowego deployu”

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 4 §2
- **Detail**: Kryterium „deploy utworzony po starcie apply” zależało od zegarów runnera i Railway. Gdy nowy deploy jeszcze się nie pojawił, najnowszym był stary `SUCCESS`, co dawało fałszywy sukces. Brakowało też stanów `SKIPPED` i `REMOVED`.
- **Fix**: `verify-apply.sh snapshot` przed apply zapisuje ID deployów, a `check` czeka na inne ID. Brak nowego deployu do limitu daje exit 1. `SKIPPED` kończy się exit 1, przy `REMOVED` skrypt odpytuje najnowszy deploy. Nowe przypadki testowe.
- **Decision**: FIXED

### F7 — Adres weba wyznaczany na dwa sposoby

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 4 §4 vs Phase 5 §1
- **Detail**: Phase 4 czytała `RAILWAY_PUBLIC_DOMAIN` z Railway, a Phase 5 miała mapowanie wpisane na sztywno. `DEPLOY_TARGET_URL` nic nie daje, bo `target_url` i `environment_url` w statusach Railway wskazują panel Railway. Statusy deployu nie mówią, której usługi dotyczą, więc na jeden deploy przypada kilka runów.
- **Fix**: Wspólny helper `tools/deploy-gate/web-url.sh <env-name>` z testami; `DEPLOY_TARGET_URL` usunięte; dopisana uwaga o wielu statusach i deduplikacji przez otwarte issue.
- **Decision**: FIXED

### F8 — Nowe `existsByStatus` dubluje istniejące `existsByStatusIn`

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Phase 3 §1; Performance Considerations
- **Detail**: `existsByStatusIn(Collection<RunStatus>)` już istnieje (`PipelineRunRepository.java:23`). Zdanie o „istniejących indeksach na status” było nieścisłe: jest tylko częściowy `uq_pipeline_runs_one_running` (V3:39).
- **Fix**: Reużycie `existsByStatusIn` (ewentualnie opakowanego default-metodami) i poprawione zdanie o indeksach.
- **Decision**: FIXED
