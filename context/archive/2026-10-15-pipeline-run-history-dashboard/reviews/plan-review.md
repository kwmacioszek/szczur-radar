<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Historia przebiegów, postęp i powiadomienia admina (S-06)

- **Plan**: context/changes/pipeline-run-history-dashboard/plan.md
- **Mode**: Deep
- **Date**: 2026-10-15 (triage 2026-10-16)
- **Verdict**: REVISE → SOUND po poprawkach
- **Findings**: 1 critical, 6 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | PASS |
| Architectural Fitness | FAIL |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
10/10 paths ✓, 9/9 symbols ✓, Progress↔Fazy ✓ (6 faz, 33 kroki), brief↔plan ✓ (zastrzeżenie: ocena ryzyka w briefie, F2)

## Findings

### F1 — `shutdownNow()` nie przerywa przebiegu: akcje działają w wątkach Embabel

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Architectural Fitness
- **Location**: Faza 2 §3; Key Discoveries; State sequencing
- **Detail**: Embabel 1.5.2: `invoke()` = `invokeAsync().get()`; `AgentPlatform.start` uruchamia proces przez `asyncer.async { run() }` (`AgentPlatform.kt:122-127`, `ExecutorAsyncer.kt:44-70`) na puli `embabel-platform-N`, a proces SIMPLE wykonuje tam akcje. Wątek `pipeline-run` tylko czeka w `CompletableFuture.get()`; `shutdownNow()` przerywa samo czekanie, `assess` i `await` dyspozytora działają dalej, a `InterruptedException` (checked z Kotlina) omija `catch (RuntimeException)` (`PipelineRunService.java:160`). Działa tylko dźwignia bazodanowa (status ≠ `running` → `claim` zawodzi).
- **Fix A ⭐ Recommended**: przyszłość per przebieg (`invokeAsync`, mapa runId → future), strażnik po zapisach woła `future.cancel(false)`; ta sama kolejka bierze następny przebieg, bez podmiany executora.
- **Fix B**: zostawić podmianę kolejki, łapać `InterruptedException`, zapisać, że akcja-zombie trwa.
- **Decision**: FIXED (Fix A). Na pytanie użytkownika o sens Embabel: wyjęcie przebiegu z Embabel zaparkowane w `roadmap.md` (`## Parked`, v2.21) jako osobna zmiana z researchem; plan zapisuje mechanizm jako tymczasowy.

### F2 — Próg 15 min krótszy niż najgorsze pojedyncze wywołanie LLM

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Faza 2 §1; plan-brief „Open Risks”
- **Detail**: Przy `LLM_TIMEOUT=300s` jedno wywołanie trwa do 6×300 s + 4×60 s ≈ 34 min (flex → default), bez powrotu ≈ 17 min (`TransportRetry.java:25,95-115`). `llm.check()`, ogon przebiegu i mały RETRY mają w toku jedno wywołanie, więc zdanie briefu „wszystkie wątki naraz” nie zachodzi.
- **Fix A ⭐ Recommended**: domyślny próg = max(15m, `TransportRetry.maxCallDuration(LLM_TIMEOUT, LLM_SERVICE_TIER)` + 5m).
- **Fix B**: zostawić 15m, poprawić opis ryzyka.
- **Decision**: FIXED (Fix A, z korektą: jawna wartość poniżej granicy jest dozwolona z WARN przy starcie zamiast odrzucenia, bo `RunWatchdogIT` używa `2s`, a sprawdzenie ręczne `1m`).

### F3 — `AdminRoleIT` wyłoży się na trasach z `{id}`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 §7; Key Discoveries
- **Detail**: `AdminRoleIT` (`:53-61`) podaje trasę z `ADMIN_ROUTES` dosłownie jako URI; `buildAndExpand` rzuci „Not enough variable values”.
- **Fix**: `AdminRoleIT` wypełnia zmienne ścieżki wspólnym helperem z `concretePath()`.
- **Decision**: FIXED

### F4 — Wstawienie wiersza kroku nie jest warunkowe

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 1 §4 (`step`)
- **Detail**: `RunRecorder.step` robi `INSERT` bez warunku (`RunRecorder.java:106-113`); przerwana instancja dopisuje `summarize: succeeded` do przerwanego przebiegu (`PipelineAgent.java:117-126`), a śmierć instancji między wstawieniem a `endStep` odtwarza krok `running` w skończonym przebiegu.
- **Fix**: `INSERT … SELECT … WHERE EXISTS (przebieg running)`; bez wstawienia brak pracy i cichy koniec akcji; test w `PipelineAgentIT`.
- **Decision**: FIXED

### F5 — Strażnik i dzwoneczek mogą wyłączyć usypianie API (koszt)

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 §2; Faza 5 §2; Performance Considerations
- **Detail**: Railway liczy każdy pakiet wychodzący, także zapytania do bazy po sieci prywatnej, jako aktywność (docs: Serverless). Strażnik pytający bazę przy pustym `own` trzymałby API stale włączone (Hobby, twardy limit $10).
- **Fix**: brak zapytań przy pustym `own` + `RunWatchdogTest`; notka w Performance o widocznej karcie admina.
- **Decision**: FIXED

### F6 — Dwie luki SPA: nieprzeczytane poza 10 i „Pokaż starsze” przy odświeżaniu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 §3; Faza 5 §2 i §4
- **Detail**: Przy więcej niż 10 nieprzeczytanych licznik zostawał > 0 bez pozycji do kliknięcia; odświeżanie co 10 s nie określało losu doklejonych stron offsetowych.
- **Fix**: `POST /api/admin/notifications/read` („Oznacz wszystkie”); odświeżenie strony 0 o rozmiarze liczby wczytanych wierszy z deduplikacją po `id`.
- **Decision**: FIXED

### F7 — Reguła „wymaga uwagi” bez dopisku w PRD i roadmapie

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Faza 6 §2
- **Detail**: NFR (`prd.md:258`) chce sygnału przy częściowym błędzie; plan pomija `partial` z samymi `failed` do ponowienia (decyzja użytkownika). Faza 6 nie aktualizowała PRD ani roadmapy.
- **Fix**: `prd.md` „Doprecyzowanie (S-06)”; `roadmap.md` — rozstrzygnięty Unknown, strażnik zamiast ostrzeżenia, renderer i PNG.
- **Decision**: FIXED

### F8 — Wzorzec `PipelineStallSettings`: `PipelineTriggerToken`, nie `ActivationSettings`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 2 §1; Key Discoveries
- **Detail**: `ActivationSettings` czyta tylko CLI (`cli/LinkCommand.java:131`); ustawienie samego serwera to `internal/PipelineTriggerToken` (`SecurityConfig.java:123-128`).
- **Fix**: `PipelineTriggerToken` jako wzorzec beana, z `ActivationSettings` tylko parsowanie czasu.
- **Decision**: FIXED

### F9 — „Brak postępu: zatrzymany” tylko z tekstu `error`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 5 §1 (`attentionReasons`)
- **Detail**: SPA musiałoby dopasowywać prefiks „no progress” w wolnym tekście.
- **Fix**: dla `failed` powodem jest `error` dosłownie.
- **Decision**: FIXED

### F10 — Wycofanie kodu po `V8` psuje listę przebiegów

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Migration Notes
- **Detail**: Stary `StepStatus` nie zna `interrupted`, a `EnumValueConverter` rzuca wyjątek (`EnumValueConverter.java:40-43`): 500 na liście w oknie deployu i trwale po rollbacku z panelu.
- **Fix**: notka w Migration Notes i w runbooku S-06 (`deploy-plan.md`).
- **Decision**: FIXED
