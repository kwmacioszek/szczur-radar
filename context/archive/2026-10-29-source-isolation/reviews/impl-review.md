<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-20: awaria albo cisza jednego źródła nie psuje przebiegu pozostałych

- **Plan**: context/changes/source-isolation/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-10-29
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Evidence

- Zakres gita: `ef0d33b2..aed4698` (7 commitów, 72 pliki, PR #130). Każdy blok **File** faz 1–5 i każdy wymieniony w planie scenariusz testów odnaleziony (MISSING: brak). Lista „What We're NOT Doing” nienaruszona (`InternalPipelineRunController`, `trigger.sh`, `tools/pipeline-trigger/test.sh`, `RunNotifications.attentionDetail`, `NotifiedRun`, `findLastUpdate`, `context/domain/`, `context/changes/post-m2-audit/` bez zmian; pobranie sekwencyjne; brak `CHECK` wiążącego status z licznikami).
- Zmiany poza listą planu — drobne i spójne: javadoc `SourceFetcher`, `CoveredRange` jako osobny rekord, usunięcie `Counters.fetched` (wymuszone kryterium 2.3), `ClosedSetsIT.NOT_A_VALUE_LIST` += `ck_pipeline_run_sources_error`, wiersz źródła w `PipelineRunWithoutSourceIT`, dopisek S-27 w roadmapie (z plan-review F1), dodatkowe scenariusze testów, `nightly()` w atrapie i wysokość `.bell-sample` na kitchen sinku.
- Kryteria Automated (wszystkie fazy) sprawdzone lokalnie 2026-10-29: `./mvnw -B verify` BUILD SUCCESS (308 IT, 0 błędów; w tym `V12BackfillIT` 4, `RunRecorderIT` 15, `PipelineRunIT` 18, `RunWatchdogIT` 6, `MissingDaysIT` 14, `PipelineRunControllerIT` 13, `MigrationsIT` 26, `ClosedSetsIT` 5, `JpaSchemaIT` 5); `npm test` 429/429; `npm run build` OK; `tools/db-backup/test.sh` kod 0; `rg 'Counters\.fetched\('` i `rg array_position` w `src/main` puste; 11 PNG + `ui/README.md` z macierzą 7 stanów; `gh pr view` ma `Refs #114`; CI run 40886957430 na HEAD `aed4698` zielony (wszystkie joby); dowody PR env (`sourceResults` z wierszem `wykop succeeded`, 503 dla `reddit`, `/sources` tylko Wykop, `missing-days`) w komentarzu issue #114 i w sekcji S-20 runbooka.
- Kryteria Manual 4.5 (polskie teksty sekcji i powodów) i 5.5 (ocena PRD i runbooka, decyzja o merge'u) nieodhaczone — oczekują na użytkownika, zgodnie z planem.
- Zweryfikowane bez uwag: parametryzacja SQL (`RunRecorder.source`, JPQL pokrycia, V12 bez tekstu dynamicznego); semantyka CTE (0 wierszy → brak wstawienia → `false`; naruszenie klucza unikalnego cofa całą instrukcję); strażnik `status = running` i „kto zatrzymuje, pisze pierwszy” dla trzech ścieżek zatrzymania; flaga przerwania zachowana; reguła statusu i błędu `partial`; ucięcie błędu; brak nowej trasy; brak `v-html`; lista przebiegów ładuje wiersze jednym `findByRunIdIn`; izolacja `V12BackfillIT` (własny kontener); `reset()` i `ScriptedSourceFetcher` sprzątają; wzorce `StepStatus`/`PipelineRunStep` odwzorowane; `RunDetailView.vue` tylko tokeny, `StatusBadge`, etykiety z `labels.ts`.

## Findings

### F1 — Historyczne puste przebiegi `succeeded` dostają po V12 „wymaga uwagi”, runbook o tym milczy

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/resources/db/migration/V12__pipeline_run_sources.sql:54-56,80-88; szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java:326-332; context/deployment/deploy-plan.md:1956,1990
- **Detail**: Backfill klasyfikuje przebieg sprzed S-08 z krokiem `fetch` `succeeded` i `fetched = skipped` jako `empty` i ustawia mu `sources_empty = 1`. `attentionReasons` dla `SUCCEEDED` dodaje `SOURCE_EMPTY`, gdy `sourcesEmpty > 0`, więc każdy taki historyczny przebieg zmienia po deployu `needsAttention` z `false` na `true` w liście `/admin` i na swojej stronie („bez postów: Wykop (pobrano 0, pominięto 0)”). Powiadomienia ani alertu wstecz nie ma (`runEnded` tylko na końcu przebiegu). Sekcja „An intended correction of history” i krok 2 runbooka mówią tylko o różnicy w „Dniach bez danych”, nie o fladze uwagi — operator porównujący `/admin` przed i po może wziąć to za regresję. Plan (Migration Notes) ma tę samą lukę.
- **Fix**: Dopisać w runbooku S-20 (akapit „An intended correction of history” oraz krok 2) jedno zdanie: historyczne przebiegi `succeeded` sklasyfikowane jako `empty` pokazują od deployu „wymaga uwagi: bez postów: Wykop (…)” w `/admin` i na stronie przebiegu; bez nowego powiadomienia i alertu; to ta sama korekta historii.
- **Decision**: FIXED — runbook S-20 (deploy-plan.md, akapit „An intended correction of history” i krok 2) wymienia flagę „wymaga uwagi: bez postów” na historycznych przebiegach `empty`

### F2 — Awaria źródła „z przerwania” logowana jako WARN ze stosem, gdy dispatcher loguje INFO

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/SourceIngest.java:117-121
- **Detail**: `catch (RuntimeException ex)` loguje `log.warn("Run {}: {} failed: {}", …, ex)` także wtedy, gdy źródło rzuciło, bo przebieg został zatrzymany (`cancel(true)` strażnika, `shutdownNow()` w `onClose`: `WykopApiException(INTERRUPTED)` z zachowaną flagą). Ten sam przypadek w `ConcurrentAssessmentDispatcher.java:180-183,339-342` jest INFO bez stosu („ended with its stopped run”). Zwykłe zatrzymanie przebiegu (deploy) zostawia więc w logu WARN ze stosem wyglądający jak awaria Wykopu. `PipelineRunIT.aRunStoppedBetweenItsSourcesGetsNoRowForTheSourceItWasIn` sprawdza tylko brak ERROR.
- **Fix**: W `catch` sprawdzić `Thread.currentThread().isInterrupted()`: wtedy `log.info("Run {}: {} ended with the run's stop: {}", runId, source, ex.toString())` bez stosu, w przeciwnym razie dotychczasowy WARN; wynik zostaje `FAILED` (jego zapis i tak jest odrzucony). Przypiąć w `SourceIngestTest` (scenariusz przerwania: brak WARN) i w `PipelineRunIT` (brak WARN „failed” dla zatrzymanego źródła).
- **Decision**: FIXED — `SourceIngest` loguje INFO `ended with the run's stop` bez stosu na przerwanym wątku; nowy `SourceIngestTest.aSourceThatThrowsBecauseTheRunWasStoppedIsItsFailureAtInfo`, dwie asercje w `PipelineRunIT`, `pipeline/CLAUDE.md`; `SourceIngestTest` 15/15 i `PipelineRunIT` 18/18 zielone

### F3 — Odrzucony zapis wyniku źródła nie kończy kroku: zatrzymany z innej instancji przebieg pobiera kolejne źródło

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunSequence.java:108-110; szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/step/SourceIngest.java:122
- **Detail**: `RunSequence.fetch` ignoruje wartość `recorder.source(...)` (konsument), a pętla `SourceIngest` kończy się tylko przez `RunInterruptedException.checkpoint` (flaga wątku). Przebieg przerwany bez przerwania tego wątku — odzyskiwanie nowej instancji (`deploy`) albo `interruptOrphans` innej instancji (`lost`) — dostaje `false` z zapisu, a mimo to pobiera następne źródło (Wykop do 10 min) i dopiero `openStep` kroku `store` odmawia. Przed S-20 między źródłami nie było żadnego zapisu, więc to nie regresja; plan zaprojektował konsumenta bez wyniku. Zgodnie z lekcją „uzasadniaj zakres celem użytkownika” brak wymagania, którego to dotyczy.
- **Fix**: Zakończyć krok po odrzuconym zapisie: w `RunSequence.fetch` `if (!recorder.source(…)) throw new RunNoLongerRunningException(runId, RunStep.FETCH)` (klasyfikator `execute` loguje to już na INFO); przypiąć w `RunSequenceTest` przypadek `source()` → `false`.
  - Strength: Zamyka pętlę natychmiast po tym, jak ktoś inny zakończył przebieg; jedna linia i jeden test.
  - Tradeoff: Rzadki przypadek (stara instancja i tak jest zatrzymywana); zmiana poza kontraktem planu.
  - Confidence: MED — `RunNoLongerRunningException(UUID, RunStep)` istnieje, ale nie sprawdzono, jak `recorder.step` klasyfikuje ją w kroku `fetch`.
  - Blind spot: Czy `execute` nie zapisze wtedy kroku `fetch` jako `failed` z tym wyjątkiem.
- **Decision**: SKIPPED — nie regresja, brak wymagania użytkownika; plan zaprojektował konsumenta bez wyniku

### F4 — `ck_pipeline_run_sources_counts` wobec historii produkcji: wywiedzione jako bezpieczne, bez odczytu produkcji

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/resources/db/migration/V12__pipeline_run_sources.sql:46,60-61; context/deployment/deploy-plan.md:1970
- **Detail**: Backfill wstawia `r.fetched`, `r.skipped` przebiegu do tabeli z `CHECK (skipped <= fetched)`. Historia liczników od S-02 (`fetched = elements + skippedElements`, `skipped = skippedElements + unmappable`, jedyny zapis w `RunRecorder`) daje `skipped <= fetched` z konstrukcji, a `resolve().distinct()` wyklucza duplikat w `sources` (klucz unikalny); `ck_pipeline_run_sources_error` spełnia `CASE … COALESCE`. Plan świadomie nie dodał `CHECK` wiążących status z licznikami z obawy o dane historyczne — ten sam argument dotyczy tego ograniczenia. Nieudany pre-deploy `migrate` zatrzymuje deploy (fix-forward V13).
- **Fix**: Dopisać w kroku 0 runbooka S-20 jedną kontrolę przed merge'em na produkcji: `SELECT count(*) FROM pipeline_runs WHERE kind = 'range' AND (skipped > fetched OR fetched < 0 OR skipped < 0)` (oczekiwane 0).
- **Decision**: FIXED — krok 0 runbooka S-20 ma kontrolę liczników (`bad_counters`, oczekiwane 0) przed merge'em

### F5 — Runbook: przebieg `RUNNING` podczas migracji może dostać wiersz, gdy jego krok `fetch` już się skończył

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/deployment/deploy-plan.md:1958; szczur-radar-api/src/main/resources/db/migration/V12__pipeline_run_sources.sql:72-73
- **Detail**: Backfill filtruje po statusie kroku (`f.status IN ('succeeded', 'failed')`), nie przebiegu. Przebieg `running` w kroku `assess` podczas `migrate` ma krok `fetch` `succeeded`, więc dostaje wiersz `succeeded` i liczniki, a runbook mówi „gets no row”. Bez skutku: odzyskiwanie nowej instancji oznacza go `interrupted`, a pokrycie liczą tylko `succeeded`/`partial`. Plan (Migration Notes) ma to samo zdanie.
- **Fix**: Przeredagować zdanie runbooka: „gets no row unless its `fetch` step had already ended; as `interrupted` it covers nothing anyway”.
- **Decision**: FIXED — zdanie runbooka S-20 o przebiegu `RUNNING` podczas deployu mówi o statusie kroku `fetch`

### F6 — `RunWatchdogIT` dwóch źródeł: 2 s marginesu na źródło wobec progu 5 s

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/RunWatchdogIT.java:327-333
- **Detail**: `twoSourcesTakingLongerThanTheTimeoutTogetherAreNotStopped` opóźnia Wykop o 3 s i `ScriptedSourceFetcher` o 3 s przy `TIMEOUT = 5 s`; źródło Wykopu to auth + wpisy (3 s) + linki + zapis, czyli ok. 2 s zapasu na obciążonym self-hosted runnerze (istniejący scenariusz „slow run with progress” ma 2 s pauzy i 3 s zapasu). Asercje są odporne (`> TIMEOUT` rośnie z opóźnieniem); scenariusz strażnika może mierzyć czas (S-26). Lokalnie i w CI zielony.
- **Fix**: Zostawić; jeśli test zacznie migać, skrócić oba opóźnienia do 2,5 s albo podnieść próg scenariusza przez zagnieżdżone `@SpringBootTest` jak w `DeploySafetyIT`.
- **Decision**: SKIPPED — zostaje 3 s + 3 s: skrócenie zmniejszyłoby margines asercji `fetch > TIMEOUT`; test zielony lokalnie i w CI
