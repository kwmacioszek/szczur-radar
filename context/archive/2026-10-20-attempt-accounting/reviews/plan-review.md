<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Awaria dostawcy LLM ani błąd naszego kodu nie zabija postów (S-09)

- **Plan**: context/changes/attempt-accounting/plan.md
- **Mode**: Deep
- **Date**: 2026-10-20
- **Verdict**: REVISE → SOUND (po triage: 6/6 poprawione w planie)
- **Findings**: 1 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | FAIL |
| Plan Completeness | WARNING |

## Grounding

18/19 paths ✓ (`TransportExceptionTest` nie istnieje — plan przewiduje fallback), 6/6 symbols ✓, brief↔plan ✓, `docs/reference/contract-surfaces.md`: brak (pominięte).

## Findings

### F1 — Odroczone liczenie awarii bez strażnika przebiegu

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details › State sequencing; Faza 2 #3; Faza 3 #2
- **Detail**: Plan zakłada, że `countTechnicalFailure` może działać bez strażnika, bo „post jest już zwolniony; jeden przebieg naraz”. Tymczasem w trzech przypadkach wątek dispatch dochodzi za pętlę `await` ze `stoppedBy == null` po tym, jak przebieg przestał być `running`, a `stage IN (stored, failed)` pasuje:
  - deploy: `onClose` przerywa przebieg i zwalnia posty (`PipelineRunService.java:330-342`), a pula Embabel kończy się przez `shutdown()`, nie `shutdownNow()`; `cancelled` ustawia tylko watchdog (`ConcurrentAssessmentDispatcher.java:185`, `PipelineRunService.java:289`);
  - nakładka deployu: `PipelineStartupRecovery.java:82-92`;
  - watchdog: futures zakończone jako `Unavailable` przed `cancel()`.

  Posty przerwane deployem dostałyby +1 do `technical_failures`.
- **Fix**: `countTechnicalFailure` dostaje `runId` i strażnik jak w `claim` (`EXISTS … status = :running`, `PostTransitions.java:59`). `release` zwraca liczbę wierszy, a `Unavailable` liczy się tylko dla posta zwolnionego przez to zadanie. Do tego testy: „przebieg nie jest już running → zero liczenia” w teście dyspozytora i wzorzec Order 7 w `PipelineAgentIT`.
- **Decision**: FIXED (Fix in plan)

### F2 — Surowy wyjątek omija maskowanie klucza i trafia do bazy

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Faza 1 #3; Key Discoveries
- **Detail**: Dziś `ofThrowable` przepuszcza komunikat przez `ProviderMessages.clean` (klucz zamieniony na `<hidden>`, 500 znaków). Pierwotny wyjątek rzucony dalej omija to czyszczenie i trafia do `pipeline_runs.error` oraz do nowego `posts.last_technical_error` (widocznego w `/admin` i zapisywanego w kopiach bazy).
- **Fix**: klient rzuca `IllegalStateException(messages.clean(String.valueOf(ex)), ex)`, czyli wyjątek spoza `LlmCallException`: zostaje błędem technicznym, ale z oczyszczonym komunikatem. Javadoc `LlmClient` zmienia się odpowiednio. Testy klientów sprawdzają typ, maskowanie i przyczynę.
  - Strength: zachowuje dzisiejsze maskowanie, a wszystkie miejsca rzucające już mają `ProviderMessages`.
  - Tradeoff: klasa wyjątku SDK widoczna dopiero w przyczynie.
  - Confidence: HIGH — `ProviderMessages` jest dostępny w każdym miejscu wywołania `ofThrowable`.
  - Blind spot: wyjątki spoza `llm/` nie są maskowane (ale nie zawierają klucza LLM).
- **Decision**: FIXED (Fix in plan)

### F3 — Ręczny test bezpiecznika w PR env niczego nie uruchomi

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 Manual Verification (Progress 3.5); Testing Strategy; brief
- **Detail**: PR env używa deepseek-v4-flash. Nagrania tego modelu mają maksimum 5,1 s, a `LLM_TIMEOUT` ma minimum 10 s (`LlmProviderProperties.java:66`). `PipelineAgentIT` mockuje `LlmClient`, więc nic nie testuje prawdziwego `TransportRetry`.
- **Fix**: IT z prawdziwym klientem (`LlmClientFactory.create` na `ScriptedProviderServer`: check ok, potem HTTP 500) → bezpiecznik → `failed` bez prób. Krok 3.5 zastąpić regresją w PR env. Usunąć `LLM_TIMEOUT=10s` z Testing Strategy i z briefu.
- **Decision**: FIXED (Fix in plan)

### F4 — Kod pomiarowy i AssessorAgent poza opisem

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 #3; Faza 1
- **Detail**: Kontrakt dla kodu pomiarowego jest niedookreślony, choć wzorzec istnieje (`test/measurement/Replay.java:99-100`). Surowy wyjątek przerywa pomiar już po fazie 1. `AssessorAgent` (akcja Embabel z domyślną polityką ponowień, `AssessorAgent.java:28-31`) zobaczy nowe wyjątki — patrz lekcja FIRE_ONCE.
- **Fix**: łapać `LlmUnavailableException` i zapisywać `assessor.failed(((Preparation.Prepared) assessor.prepare(input)).request(), ex, elapsed)`. W fazie 1 odnotować, że przerwanie pomiaru przez surowy wyjątek jest celowe. W fazie 3: `FIRE_ONCE` na akcji `AssessorAgent` albo test, że wykonała się raz.
- **Decision**: FIXED (Fix in plan)

### F5 — Surowy wyjątek z check() daje 500 w /api/admin/llm/check

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: What We're NOT Doing
- **Detail**: `LlmCheckController` łapie tylko `LlmCallException` (`:36-45`), a `ApiExceptionHandler` nie ma ogólnego handlera, więc odpowiedzią jest domyślne 500.
- **Fix**: w fazie 1 łapać też `RuntimeException` (`ok=false`, `refused=false`), z przypadkiem w `LlmCheckControllerIT`. Pozycję w „NOT Doing” zawęzić do „bez zmiany kształtu odpowiedzi”.
- **Decision**: FIXED (Fix in plan)

### F6 — Niedokładne ścieżki testów i pominięci wołający

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 #4, Faza 2 #6, Faza 4 #2 i #4
- **Detail**: Brakujące lub niedokładne ścieżki:
  - test listy nieudanych to `PostsApiIT.java:360-385`;
  - `TransportExceptionTest` nie istnieje;
  - testy predykatów są w `PostRepositoryIT.java:94` i `AssessmentSelectionTest.java:56`;
  - w SPA: `api/pipeline.test.ts:45-50`, stuby w `PostDetailView.test.ts:16` i `FeedView.test.ts:17`, `mock-api.js:159-163,334`.
- **Fix**: wpisać te ścieżki w odpowiednie punkty Changes Required.
- **Decision**: FIXED (Fix in plan)
