<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-18: błędy wymagające reakcji docierają do operatora

- **Plan**: context/changes/error-visibility/plan.md
- **Mode**: Deep
- **Date**: 2026-10-24
- **Verdict**: REVISE → SOUND po poprawkach (wszystkie 6 ustaleń FIXED)
- **Findings**: 1 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

14/14 paths ✓, 5/5 symbols ✓, brief↔plan ✓.

Zweryfikowane w kodzie bez ustaleń:
- `onClose` (`ContextClosedEvent`) idzie przed `destroy()` beanów (Spring 7.0.9 `AbstractApplicationContext.doClose`);
- Boot 4 ma domyślnie `server.shutdown=graceful`;
- żaden kod główny nie czyta przyczyny wyjątku LLM po klasyfikacji; `CommandChecks` szuka tylko `InvalidSettingsException`;
- cron czeka na wszystkie swoje przebiegi (RETRY i RANGE należą do schedulera) i kończy się kodem 1 przy `failed`/`interrupted` (`tools/pipeline-trigger/trigger.sh:198-207,312-337`).

## Findings

### F1 — Weryfikacja w PR env: „przebieg za dzisiejszy dzień” nie da failed

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Faza 4 (zmiana 3, kryterium 4.2), brief
- **Detail**: Kontroler przyjmuje tylko `from ≤ to ≤ dziś`, najwyżej 7 dni (`PipelineRunController.java:140-147`). Dzisiejszy `#gielda` prawie zawsze ma posty: przebieg poszedłby do oceny LLM i raczej skończył się `succeeded`, bez alarmu.
- **Fix**: Zakres `2004-01-01`–`2004-01-01` (sprzed Wykopu: 0 elementów → `NoPostsFetchedException` → `failed`, bez LLM), przedtem sprawdzenie, że nie ma resztek (inaczej RETRY wyda wywołania LLM).
- **Decision**: FIXED — Faza 4 (zmiana 3, 4.2, runbook), Manual Testing Steps, brief.

### F2 — Faza 3 psuje trzy istniejące testy, których plan nie wymienia

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3, zmiana 4
- **Detail**: `GeminiClientTest.java:247`, `DeepSeekClientTest.java:239` i `OpenRouterClientTest.java:496` sprawdzają `getCause()` `isSameAs(sdkError)`; z oczyszczoną kopią będą czerwone.
- **Fix**: Dopisać je do zmiany 4 z nową asercją (`CleanedProviderException`, nazwa oryginalnej klasy, oryginalny stack trace).
- **Decision**: FIXED — Faza 3, zmiana 4.

### F3 — Dwie wiadomości w Telegramie na zdarzenie, nie jedna

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Desired End State, Performance Considerations, brief
- **Detail**: Check `api-events` ma `channels: "*"` bez filtra (`tools/healthchecks/checks.sh:38-47,202-206`). Telegram dostaje więc „down” i „up” (`tools/healthchecks/README.md:3`): 2 wiadomości na zdarzenie, 4 na minutę przy limicie Telegrama 6.
- **Fix**: Opisać „down” z opisem + „up”, które tylko uzbraja check, w planie, briefie i sekcji `deploy-plan.md`; bez zmian konfiguracji.
- **Decision**: FIXED — Desired End State, Performance Considerations, Faza 4 (deploy-plan), brief.

### F4 — 4.2 szuka id przebiegu w linii logu, która go nie ma

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 4 (4.2), Faza 1 (zmiana 3)
- **Detail**: `Operator alert {} delivered to healthchecks.io` (`HealthchecksAlerts.java:83`) nie zna przebiegu, a plan nie mówił, że kolejka loguje wynik wysyłki.
- **Fix**: Kolejka po wysyłce loguje INFO ze zdarzeniem, przebiegiem i `Delivery`; 4.2 szuka tej linii.
- **Decision**: FIXED — Faza 1, zmiana 3; Faza 4, 4.2 (tytuł w Progress poprawiony przed implementacją).

### F5 — Pomijanie dubla crona: limit czasu crona i zgubiona odpowiedź

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 (zmiana 2), Faza 4 (`pipeline/CLAUDE.md`, `deploy-plan.md`)
- **Detail**: Gdy cron odpuści przed końcem przebiegu (`TRIGGER_RUN_TIMEOUT` 90 min, API nieosiągalne 3 min, `POST` bez odpowiedzi), jego exit 1 mówi tylko „not ended”. Późniejszy `failed` tego przebiegu nie trafia do Telegrama, tylko do dzwonka.
- **Fix**: Zapisać to w `pipeline/CLAUDE.md` i sekcji `deploy-plan.md`; reguła zostaje.
- **Decision**: FIXED — Faza 4 (zmiany 1 i 2), brief (Open Risks).

### F6 — CleanedProviderException: „publiczny tylko w pakiecie” jest niewykonalne w Javie

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3, zmiana 1
- **Detail**: Klienci są w innych pakietach (`llm/gemini`, `llm/openrouter`, `llm/deepseek`), więc klasa musi być publiczna.
- **Fix**: Publiczna klasa w `llm/transport`, tworzona tylko przez `ProviderMessages.cleanCause`.
- **Decision**: FIXED — Faza 3, zmiana 1.
