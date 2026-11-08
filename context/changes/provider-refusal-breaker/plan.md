# Bezpiecznik serii odmów dostawcy LLM (S-32) — Implementation Plan

## Overview

Seria odmów dostawcy LLM na kolejnych postach (moderacja, odrzucane żądanie, odpowiedź bez tekstu) ma zatrzymać krok `assess`, zanim zużyje próby pozostałych postów przebiegu. Dziś każda odmowa jest zapisywana jako ocena `failed`, zużywa próbę i **zeruje** oba istniejące bezpieczniki (awarii dostawcy z S-09 i błędów klienta z S-17), więc seria jest cicha przez dwie noce, a w trzeciej posty stają się nieodwracalnie `dead` (audyt `post-m3-verification-audit`, R-41; destylacja domeny #3). Zmiana dodaje w `ConcurrentAssessmentDispatcher` trzeci licznik o tej samej mechanice co dwa istniejące: `REFUSAL_STREAK = 3` zapisanych wyników klasy wywołania z rzędu kończy przebieg `failed` z sygnałem jak każdy `failed`, a posty, których krok nie zdążył ocenić, wracają bez próby. Roadmapa: S-32 (`context/foundation/roadmap.md`), issue #142; PRD v3: doprecyzowanie NFR `prd.md:288` („Wyłącznik serii odmów dostawcy”).

## Current State Analysis

- **Odmowa jest wynikiem, nie wyjątkiem.** `Assessor.call` (`szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/Assessor.java:125-139`) przepuszcza `LlmConfigurationException` i `LlmUnavailableException`, a zwykły `LlmCallException` (transport `REJECTED`: inne 4xx, odmowa modelu, zablokowana odpowiedź, brak tekstu — `llm/transport/TransportException.java:42-46`, `TransportRetry.java:103-105`) zamienia w `AssessmentResult.failed` z klasą `PROVIDER_ERROR` albo `TIMEOUT` i `rawText == null` (`Assessor.java:149-160`). To jedyne miejsce, które buduje wynik tych dwóch klas; zła odpowiedź modelu ma inne klasy (`FailureClass.java`: „The first two come from the model call, the rest from parsing and validating its response”).
- **Dyspozytor zeruje serie każdym zapisanym wynikiem z wywołaniem.** `ConcurrentAssessmentDispatcher.assess` (`pipeline/assess/ConcurrentAssessmentDispatcher.java:298-303`): po `transitions.record` wynik z `provenance.attempts() >= 1` — także odmowa — zeruje `outages` i `clientErrors`; wynik `failed` zwraca `Outcome.Stored(ok=false, dead=…)` (`:305-306`), a trzecia próba daje `dead` (`Post.MAX_ASSESSMENT_ATTEMPTS`). Dwa istniejące bezpieczniki: `OUTAGE_STREAK` (`:112`, `:317-337`) i `CLIENT_ERROR_STREAK` (`:118`, `:338-359`), oba jako stałe, jeden licznik na krok, „z rzędu” w kolejności kończenia wywołań, jeden WARN i `stoppedBy.compareAndSet`.
- **Zatrzymany krok jest już sygnałem.** `AssessedBatch.stoppedBy` → krok `assess` zamykany `failed` (A6, `RunRecorder.step` z `StepOutcome`), status przebiegu `FAILED` (`state/RunOutcome`), błąd przebiegu `stopped, the remaining posts were put back: <stoppedBy>` (`pipeline/flow/RunFlow.java:184`), `AttentionReason.FAILED` → powiadomienie każdego admina, kod wyjścia crona 1 (przebieg crona) albo `RUN_NEEDS_ATTENTION` (przebieg admina). Nic nie parsuje tekstu bezpiecznika poza testami i `deploy-plan.md` (SPA, `dev/mock-api.js` i cron czytają status i wyświetlają tekst).
- **Na zwykłą noc odmów nie ma.** Raport S-28 (`context/archive/2026-10-30-reddit-quality-and-cost/report-2026-10-31.md:33`): `provider_error` 0; pomiar S-27 w PR env (`context/archive/2026-10-29-reddit-source/reviews/impl-review-phase-5.md:43`): nieudane próby to wyłącznie klasy walidacji. Naruszenia kontraktu są za to częste (Reddit: 16,6 % pierwszych prób, PRD v3 `prd.md:287`), więc bezpiecznik nie może ich liczyć.
- **Seamy testowe istnieją.** `ConcurrentAssessmentDispatcherTest`: `REFUSED` (`:145`, scriptowany klient rzuca `LlmCallException(PROVIDER_ERROR, "HTTP 400: refused", 1, …)`, `:1091-1094`), `INVALID` (model odpowiada bez JSON — naruszenie kontraktu), prefilter (`"#gielda <url>"`), `UNAVAILABLE`, `BOOM`, `CLIENT_ERROR`; `dispatchOnOneThread`. Testy `aRefusedCallEndsTheStreakOfOutages` (`:706-724`) i `aPostDecidedWithoutACallCountsNoRetryAndAFailedOneCountsItsAttemptAndDeath` (`:317-332`) utrwalają dzisiejsze zachowanie i pozostają prawdziwe (pojedyncza odmowa dalej zeruje awarie i zużywa próbę). `PipelineRunIT:637-671` ma scenariusz bezpiecznika awarii na pełnym silniku z Postgresem i `@MockitoBean LlmClient` (`answer(request -> { throw … })`), domyślnie 4 wątki LLM (`@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)`, bez `PIPELINE_LLM_CONCURRENCY`). `LlmOutageIT` sprawdza łańcuch transport → `Assessor` → dyspozytor dla 500; klasyfikację 4xx → `REJECTED` pokrywają testy klientów (`llm/CLAUDE.md`, „Adding a provider”, p. 4).
- **Dokumenty, które opisują dzisiejszą regułę:** `pipeline/CLAUDE.md` (javadoc klasy, tabela klas, „Log lines”, Rules „Attempts: three faults”, „The breaker”, „The client's breaker”, Tests), `llm/CLAUDE.md:53-57` (zdanie „so a revoked key never uses up posts' attempts”, które destylacja oznaczyła jako `declared` — R-41), `deploy-plan.md` sekcja S-09 (`:1354`: „The post's fault uses an attempt … the provider refused the request”), PRD NFR `prd.md:275` z doprecyzowaniami S-09, S-17 i PRD v3.
- **Renderer roadmapy** (`tools/render_roadmap_svg.py:208`) ma S-32 jako `ready` i nie zna statusu `planning`; poprzednie wycinki przełączały go w PR na `progress`, a przy archiwizacji na `done`, regenerując `docs/visuals/roadmap.svg`.

## Desired End State

Przebieg, w którym dostawca odmawia trzem kolejnym wywołaniom (bez odpowiedzi modelu z tekstem między nimi), kończy się `failed` z błędem `stopped, the remaining posts were put back: the LLM provider refused 3 calls in a row; the last: <komunikat ostatniej odmowy>`: trzy posty serii mają zapisany wynik `failed` (`provider_error` / `timeout`) i zużytą próbę jak dotąd, żaden dalszy post nie jest brany, a pozostałe wracają `stored`/`failed` bez próby i bez błędu technicznego. Sygnał jak każdy `failed`: dzwonek, kod wyjścia crona 1 (przebieg crona) albo `RUN_NEEDS_ATTENTION` (przebieg admina). Każda odpowiedź modelu z tekstem (`ok` albo naruszenie kontraktu) zeruje serię odmów; odmowa nadal zeruje serie awarii i klienta; prefilter, awaria dostawcy i błędy naszego kodu niczego w niej nie zmieniają. Zwykła noc (odmów 0) przebiega jak dotąd.

Weryfikacja: testy jednostkowe dyspozytora na każdą regułę, scenariusz w `PipelineRunIT` z odmową na każdy post (zapisanych najwyżej `3 + (wątki − 1)` prób, reszta nietknięta, powiadomienie), doprecyzowanie w PRD, sekcja S-32 w `deploy-plan.md`, zwykły przebieg RANGE w PR env bez regresji.

### Key Discoveries:

- Predykat „wynik z wywołania” jest w kodzie od F-02: `FailureClass.PROVIDER_ERROR` / `TIMEOUT` z `rawText == null` powstają tylko w `Assessor.failed` (`Assessor.java:149-160`) — bezpiecznik nie zgaduje po tekście komunikatu.
- Wzorzec do skopiowania: faza 3 planu S-17 (`context/archive/2026-10-25-pipeline-without-embabel/plan.md:218-272`) — stała obok `OUTAGE_STREAK`, licznik obok `outages`, `stoppedBy.compareAndSet` + jeden WARN, zerowanie zapisanym wynikiem, test na każdą regułę, doprecyzowanie PRD bez nazw klas.
- Zapis wyniku poprzedza decyzję bezpiecznika (`record` → `Outcome.Stored`), więc trzy posty serii zachowują zużytą próbę; to spójne z bezpiecznikiem klienta (każdy z trzech postów dostaje błąd techniczny) i z Outcome S-32 („posty, których nie zdążył ocenić, wracają bez straty próby”). W serii trwającej trzy noce `dead` stają się najwyżej 3 posty na noc zamiast wszystkich; przywraca je FR-034 (S-33).
- Wynik zapisany dla przebiegu, który już nie trzyma posta, to `NotTaken` (`record` zwraca `false`, `:293-297`) — seria odmów nie potrzebuje osobnego sprawdzenia `cancelled`, w przeciwieństwie do ścieżek wyjątków.
- `PipelineRunIT` ocenia na 4 wątkach: po trzeciej odmowie w locie może być jeszcze do 3 wywołań, więc scenariusz asercjonuje przedział `3…6` zapisanych odmów i co najmniej N − 6 postów nietkniętych (N = wszystkie posty modelu z dnia, N ≥ 7), nie dokładnie 3.

## What We're NOT Doing

- Zmienna środowiskowa dla progu — `REFUSAL_STREAK` jest stałą jak `OUTAGE_STREAK` i `CLIENT_ERROR_STREAK` (decyzja właściciela).
- Zerowanie serii tylko wynikiem `ok` albo liczenie sumy odmów w kroku bez zerowania (odrzucone warianty wywiadu); liczenie naruszeń kontraktu (16,6 % pierwszych prób na Reddicie zatrzymywałoby zdrowe noce).
- Cofanie prób trzem postom serii (wynik `Assessment` jest niezmienny po zapisie; droga powrotu z `dead` to FR-034 / S-33).
- Nowy `AttentionReason`, nowy licznik przebiegu, zmiana SPA, `rules.json`, `dev/mock-api.js`, crona, migracja, IaC.
- Rzucanie wyjątku z `Assessor` dla odmowy (złamałoby kontrakt „odmowa to wynik `failed`, nie wyjątek”, `agents/assessor/CLAUDE.md`, i zapis próby wymagany przez S-09).
- Drugi IT na prawdziwym transporcie (`LlmRefusalIT` obok `LlmOutageIT`): klasyfikację 4xx → `REJECTED` → `LlmCallException` pokrywają testy klientów; nowy kontekst Springa kosztowałby czas CI bez nowej wiedzy.
- Zmiana `LlmClient.CHECK_REQUEST` (łapie odmowę każdego wywołania, nie odmowy specyficzne dla promptu oceny — to robi bezpiecznik).
- Sygnał dla nieudanych prób bez `dead` (PRD v3 „Nieudane próby bez sygnału” zostawia je ciche) i dzienne podsumowanie martwych postów (S-34).

## Implementation Approach

Trzeci licznik w `ConcurrentAssessmentDispatcher` o identycznej mechanice co dwa istniejące, z predykatem na `AssessmentResult` zamiast porównywania komunikatów. Odmowa jest już zapisanym wynikiem, więc bezpiecznik działa w gałęzi `stored` (`:298-306`): wynik z wywołania (`attempts >= 1`) zeruje — jak dotąd — serie awarii i klienta, a dodatkowo: wynik klasy wywołania zwiększa serię odmów, wynik z tekstem modelu ją zeruje. Trzecia odmowa z rzędu ustawia `runStopped` i `stoppedBy` (jeden WARN), więc przebieg kończy się `failed` istniejącą drogą (A6, `RunOutcome`, `attentionReasons`). Testy: jednostkowe na każdą regułę (jak testy obu bezpieczników) i jeden scenariusz na pełnym silniku w `PipelineRunIT`. Dokumentacja w szablonie S-09/S-17: doprecyzowanie PRD pod NFR o sygnale, sekcja S-32 w `deploy-plan.md`, opisy pakietów.

## Critical Implementation Details

- **Kolejność w gałęzi `stored`.** Zerowanie `outages`/`clientErrors` przez odmowę zostaje (dostawca odpowiedział), a seria odmów jest liczona w tym samym bloku `if (result.provenance().attempts() >= 1)`: wynik prefiltera (`attempts == 0`) nie dotyka żadnego z trzech liczników. Test `aRefusedCallEndsTheStreakOfOutages` musi pozostać zielony bez zmian.
- **Komunikat ostatniej odmowy** to `message` jedynego `Violation` wyniku (`Assessor.failed`: regułą `call`, ścieżką `$`), już oczyszczony z klucza przez `ProviderMessages` w kliencie; `stoppedBy` nie dodaje nic z treści posta.

## Phase 1: Bezpiecznik serii odmów w dyspozytorze

### Overview

Predykat „wynik z wywołania” na kontrakcie oceny, stała `REFUSAL_STREAK = 3` i licznik w dyspozytorze, zatrzymanie kroku po trzeciej odmowie z rzędu, testy jednostkowe na każdą regułę, opisy pakietów; roadmapa i jej renderer na `in-progress`/`progress` w pierwszym commicie gałęzi (`/10x-implement` zakłada gałąź `feature/provider-refusal-breaker`, PR z `Refs #142` i komentarz w issue #142 przed pierwszym commitem kodu).

### Changes Required:

#### 1. Predykat wyniku z wywołania

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/validation/FailureClass.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/AssessmentResult.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/CLAUDE.md`, `szczur-radar-api/src/test/java/pl/szczurradar/api/agents/assessor/AssessorTest.java`

**Intent**: Dyspozytor ma odróżniać odmowę dostawcy (wywołanie bez tekstu) od złej odpowiedzi modelu po klasie wyniku, nie po komunikacie ani po `rawText`.

**Contract**: `FailureClass.fromCall()` → `true` tylko dla `PROVIDER_ERROR` i `TIMEOUT` (javadoc enumu już to mówi). `AssessmentResult.callFailed()` → `status == FAILED && failureClass.fromCall()`; dla `ok` i prefiltera `false`. Bez nowych stałych enumu (zbiory zapisywane w `assessments` są append-only, `ClosedSetsIT` bez zmian). `AssessorTest`: wynik odmowy (`aFailedCallIsFailedWithItsClassAfterOneCall`) ma `callFailed()`, naruszenie kontraktu i wynik prefiltera nie. `agents/assessor/CLAUDE.md`: jedno zdanie przy `Assessor`, że `callFailed()` nazywa wynik zbudowany przez `Assessor.failed`, i że to predykat bezpiecznika serii odmów w `pipeline/assess/`.

#### 2. Seria odmów w dyspozytorze

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java`

**Intent**: Trzecia odmowa dostawcy z rzędu zatrzymuje krok `assess`, zanim kolejne posty stracą próbę; pojedyncze odmowy działają jak dotąd.

**Contract**: Stała `static final int REFUSAL_STREAK = 3` obok `OUTAGE_STREAK` i `CLIENT_ERROR_STREAK`, z javadocem (odmowa dostawcy: wynik `callFailed()`). Licznik `AtomicInteger refusals` tworzony w `dispatch` obok `outages`/`clientErrors` i przekazywany do `assess`. W gałęzi `stored`, wewnątrz `if (result.provenance().attempts() >= 1)`: zerowanie `outages` i `clientErrors` bez zmian; gdy `result.callFailed()` — `refusals.incrementAndGet() >= REFUSAL_STREAK` → `runStopped.set(true)` i `stoppedBy.compareAndSet(null, "the LLM provider refused " + REFUSAL_STREAK + " calls in a row; the last: " + <message jedynego Violation>)` z jednym WARN `The LLM provider refused {} calls in a row in run {}; no further post is taken: {}`; w przeciwnym razie (`ok` albo naruszenie kontraktu) `refusals.set(0)`. Zwracany `Outcome.Stored` bez zmian (próba zużyta, `dead` liczone jak dotąd, `count` jak dotąd). Wyjątki (`LlmUnavailableException`, `LlmClientException`, inne) i prefilter nie dotykają `refusals`; `NotTaken` po `record == false` niczego nie liczy. Javadoc klasy (lista w `<ol>` i akapit „A stopped step…”) wymienia trzeci bezpiecznik; linia postępu bez zmian (odmowy są w `failed`).

#### 3. Testy fazy 1

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcherTest.java`

**Intent**: Każda reguła bezpiecznika ma test, jak reguły dwóch istniejących; dzisiejsze testy odmowy zostają bez zmian. `RunOutcomeTest` bez zmian: `aStoppedStepFailsTheRunAndSaysWhy` już pokrywa dowolny `stoppedBy` (przegląd planu, F4).

**Contract**: `ConcurrentAssessmentDispatcherTest` (na `dispatchOnOneThread`, z seamami `REFUSED`, `INVALID`, prefilter `"#gielda <url>"`, `UNAVAILABLE`, `BOOM`, `CLIENT_ERROR`), nazwy metod zawierają `Refusal`: (a) trzy `REFUSED` + dwa zwykłe → `stoppedBy` z tekstem bezpiecznika i `HTTP 400: refused`, status `FAILED`, `calls == 3`, trzy zapisane wyniki `PROVIDER_ERROR`, próby `1` dla trzech i `0` dla reszty, czwarty post nigdy nie `claim`, brak `processingErrors`, brak `releaseFailed`/`countTechnicalFailure`, trzy `count(assessedFailed(false, 0, false))`, jeden WARN, zero ERROR; (b) naruszenie kontraktu (`INVALID`) między odmowami zeruje serię (dwie, naruszenie, dwie → `stoppedBy == null`, `PARTIAL`); (c) wynik `ok` między odmowami zeruje serię; (d) post prefiltera między odmowami jej nie zeruje (trzecia odmowa zatrzymuje); (e) awaria (`UNAVAILABLE`) i błąd naszego kodu (`BOOM`) między odmowami ani jej nie zwiększają, ani nie zerują (trzecia odmowa zatrzymuje; pojedyncza awaria nieliczona, bo krok zatrzymany); (f) odmowa nadal zeruje serię błędów klienta (dwa `CLIENT_ERROR`, `REFUSED`, dwa `CLIENT_ERROR` → brak stopu) — serię awarii pokrywa istniejący `aRefusedCallEndsTheStreakOfOutages`.

#### 4. Opisy pakietów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md`

**Intent**: Reguła ma być w dokumencie pakietu obok dwóch pozostałych bezpieczników, a zdanie z `llm/CLAUDE.md`, które destylacja oznaczyła jako „declared” (R-41), ma mówić prawdę.

**Contract**: `pipeline/CLAUDE.md`: wiersz tabeli dla `assess/ConcurrentAssessmentDispatcher` (trzeci bezpiecznik), „Log lines” (nowy WARN), Rules — nowy punkt **The refusals' breaker** po „The client's breaker” (3 z rzędu, co zeruje, co nie zmienia, trzy posty serii zachowują próbę, sygnały), uzupełnienie „Attempts: three faults” (pierwsza kropka: seria odmów zatrzymuje krok), Tests — zdanie o nowych testach dyspozytora i scenariuszu `PipelineRunIT` (faza 2). `llm/CLAUDE.md:53-57`: po zdaniu o 403 `REJECTED` dopisek, że seria odmów (`REJECTED`) zatrzymuje przebieg w `pipeline/assess/` (`REFUSAL_STREAK`), więc odmowa każdego żądania kosztuje najwyżej trzy próby.

#### 5. Roadmapa i renderer

**File**: `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Status S-32 w roadmapie, rendererze i SVG ma zgadzać się ze stanem prac (reguła `REPOSITORY_GUIDELINES.md`).

**Contract**: `/10x-implement` ustawia S-32 na `in-progress` w `roadmap.md` (tabela i blok); w tym samym commicie `tools/render_roadmap_svg.py:208` dostaje `"progress"` zamiast `"ready"` i `python3 tools/render_roadmap_svg.py` regeneruje `docs/visuals/roadmap.svg`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe zielone: `cd szczur-radar-api && ./mvnw -B test`
- Testy dyspozytora pokrywają bezpiecznik: `grep -c 'Refusal' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcherTest.java` wypisuje co najmniej `6`
- Stała bez zmiennej: `grep -c 'REFUSAL_STREAK = 3' szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java` wypisuje `1`, a `grep -rl 'REFUSAL' szczur-radar-api/src/main/resources .railway/railway.ts` nic nie wypisuje
- Opisy pakietów: `grep -l 'REFUSAL_STREAK' szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md` wypisuje oba pliki, `grep -c 'callFailed' szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/CLAUDE.md` wypisuje `1`
- Renderer i SVG zgodne z roadmapą: `grep -c '"S-32": ("B", .*"progress"' tools/render_roadmap_svg.py` wypisuje `1`, a renderer jest idempotentny na indeksie: `python3 tools/render_roadmap_svg.py && git add docs/visuals/roadmap.svg && python3 tools/render_roadmap_svg.py && git diff --quiet -- docs/visuals/roadmap.svg` kończy się kodem 0

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 2: Scenariusz integracyjny i dokumenty produktu

### Overview

Seria odmów na pełnym silniku z Postgresem w `PipelineRunIT`, doprecyzowanie S-32 w PRD pod NFR o sygnale i sekcja S-32 w `deploy-plan.md` w szablonie S-09/S-17.

### Changes Required:

#### 1. Scenariusz w `PipelineRunIT`

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java`

**Intent**: Dowód „done when” destylacji #3: seria odpowiedzi `REJECTED` na wszystkie posty kończy przebieg bez zużycia prób wszystkich postów, z sygnałem.

**Contract**: Nowa metoda z `RefusingThreeCallsInARow` w nazwie, obok `aProviderUnavailableForThreeCallsInARowFailsTheRunWithoutUsingAnAttempt` (`:637`): `assessedDay()`, N postów = wszystkie z `modelAssessedPosts(16)` (asercja N ≥ 7: dzień fixture ma 16 `assessed_ok` łącznie z prefiltrem, liczby postów modelu plan nie zakłada) przestawione na `stored`, `assessment_attempts = 0`; `answer(request -> { throw new LlmCallException(CallFailure.PROVIDER_ERROR, "HTTP 403: moderation", 1, ServiceTier.DEFAULT, false, null); })` (check przechodzi domyślnie); `startPosts`. Asercje: status `failed`, `error` zawiera `the LLM provider refused 3 calls in a row` i `HTTP 403: moderation`; liczba nowych wierszy `assessments` (`result_source = 'model'`, `failure_class = 'provider_error'`) w przedziale `3…6` (4 wątki: po trzeciej odmowie najwyżej 3 wywołania w locie) i równa `assessed_failed` przebiegu oraz liczbie postów `stage = 'failed'` z `assessment_attempts = 1`; co najmniej N − 6 postów `stored` z `assessment_attempts = 0`, `technical_failures = 0`, `assessing_run_id IS NULL`; `processing_errors = 0`, `dead = 0`; `awaitClosed`, `notified(run)` równe 1. Scenariusz samodzielny, bez `@Order` (losowa kolejność klasy). `pipeline/CLAUDE.md` Tests: zdanie w punkcie `PipelineRunIT`.

#### 2. Doprecyzowanie w PRD

**File**: `context/foundation/prd.md`

**Intent**: Zmiana semantyki serii odmów ma być zapisana przy NFR o sygnale awarii, jak S-09 i S-17, i zamykać punkt „Wyłącznik serii odmów dostawcy” z PRD v3.

**Contract**: Pod blokiem doprecyzowania PRD v3 (`:285-288`) nowa linia `> Doprecyzowanie (<data>, S-32 `provider-refusal-breaker`): …` w stylu poprzednich: trzy odmowy dostawcy z rzędu (żądanie odrzucone, odpowiedź zablokowana albo bez tekstu — nie zła odpowiedź modelu) kończą przebieg `failed` z błędem „the LLM provider refused 3 calls in a row …”; trzy posty serii zachowują zużytą próbę (trzecia próba nadal daje `dead`, do przywrócenia przez FR-034), pozostałe posty wracają bez próby i bez błędu technicznego, a podejmie je RETRY; odmowa, która dotyczy treści tych samych postów, powtarza więc zatrzymanie w kolejnych dwóch nocach (RETRY kończy się `failed`, cron kodem 1, przebieg zakresu za nim idzie dalej), aż posty staną się `dead` — przyjęte: najwyżej trzy posty i trzy noce zamiast wszystkich postów okna; „z rzędu” w kolejności kończenia wywołań, każdą odpowiedź modelu z tekstem (udaną albo łamiącą kontrakt) zeruje, odrzucenie przez filtr wstępny, awaria dostawcy i błędy naszego kodu jej nie zmieniają; odmowa nadal zeruje serie awarii (S-09) i błędów klienta (S-17); sygnały jak każdy `failed`; doprecyzowania S-09 i S-17 bez zmian. Treść bez nazw klas.

#### 3. Sekcja S-32 w deploy-plan

**File**: `context/deployment/deploy-plan.md`

**Intent**: Operator ma runbook serii odmów obok runbooka awarii dostawcy, a sekcja S-09 mówi, że odmowa ma od S-32 własny bezpiecznik.

**Contract**: Nowa sekcja `## S-32: seria odmów dostawcy zatrzymuje przebieg (provider-refusal-breaker)` po ostatniej sekcji zmiany, z linią `**Status:** delivered by PR #<nr PR tej gałęzi>; production verification and closure in issue #142.` (nigdy „in progress”, lekcja o statusie sekcji). Podsekcje jak w S-09: „What changes” (reguła, co zeruje, trzy posty serii, brak zmiennej/migracji/API), „What the operator sees” (strona przebiegu: `failed`, `assessed failed 3`, błąd `stopped, the remaining posts were put back: the LLM provider refused 3 calls in a row; the last: …`; API log: WARN `The LLM provider refused 3 calls in a row in run <id>; no further post is taken: …`; cron: `FAILED`, exit 1; dzwonek), „Runbook — a refusal series (operator)” (otwórz przebieg z dzwonka; odczytaj komunikat ostatniej odmowy; `GET /api/admin/llm/check` — odmowa każdego wywołania to klucz/model, odmowa tylko promptu oceny to moderacja albo zmiana po stronie dostawcy; zapytanie read-only o trzy posty serii: ostatnie `assessments` z `failure_class = 'provider_error'` i ich `violations`; punkt „the same posts refused again the next night”: odmowa specyficzna dla treści trzech postów powtarza się — noc 1: przebieg zatrzymany po trzech odmowach (próba 1); noce 2 i 3: nocny RETRY bierze te posty wśród leftoverów i zatrzymuje się tak samo (próba 2, potem `dead`), cron kończy się `FAILED` / exit 1 każdej z tych nocy, pozostałe leftovery tego RETRY czekają, a RANGE za nim idzie dalej (osobny przebieg kolejki, dni nie zostają bez danych); od nocy 4 trzy posty są `dead` i noce są zdrowe; operator nie ma nic do naprawy, chyba że `llm/check` pokazuje odmowę każdego wywołania; `dead` przywraca FR-034 (S-33), do tego czasu zostają `dead`; nie merguj przy `RUNNING`), „PR-env verification” (odmowy nie da się wywołać w PR env — dowodem jest `PipelineRunIT`; w PR env zwykły przebieg RANGE jednego dnia kończy się jak dotąd, faza 3). W sekcji S-09 (`:1354`) dopisek `**Update (S-32, <data>):** a series of three refusals in a row stops the run (section S-32)`.

### Success Criteria:

#### Automated Verification:

- Pełna weryfikacja zielona (Docker): `cd szczur-radar-api && ./mvnw -B verify`
- Scenariusz istnieje: `grep -c 'RefusingThreeCallsInARow' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineRunIT.java` wypisuje `1`
- PRD ma doprecyzowanie S-32: `grep -c 'S-32 `provider-refusal-breaker`' context/foundation/prd.md` wypisuje `1`
- Deploy-plan ma sekcję S-32 ze statusem po merge'u: `grep -c '^## S-32: ' context/deployment/deploy-plan.md` wypisuje `1`, a `grep -A2 '^## S-32: ' context/deployment/deploy-plan.md | grep -c 'delivered by PR #.*issue #142'` wypisuje `1`

#### Manual Verification:

- Właściciel ocenia tekst doprecyzowania PRD i sekcji S-32 w `deploy-plan.md`: zgodny z doprecyzowaniami S-09 i S-17 (próby, wstrzymanie, sygnały), bez nazw klas w PRD

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 3: Weryfikacja w PR env i domknięcie

### Overview

CI zielone, zwykły przebieg w PR env bez regresji (odmowy nie da się tam wywołać), wynik w issue #142; właściciel decyduje o PR.

### Changes Required:

#### 1. Zwykły przebieg w PR env

**File**: — (operacja agenta w PR env `szczur-radar-pr-<nr PR>`)

**Intent**: Pokazać, że bezpiecznik nie zmienia zwykłej nocy: przebieg RANGE jednego dnia z prawdziwym Wykopem, Redditem i LLM kończy się jak dotąd.

**Contract**: Po zielonym `railway-config` i gotowym API (`curl -s <web>/api/ready` → 200): agent otwiera `/admin` przez `playwright-cli -s=s32 open --headed --browser=chromium <web-url>`, właściciel loguje się sam w tym oknie (lekcja: hasło nigdy przez agenta), agent uruchamia RANGE jednego dnia (wczoraj) i czeka na koniec; potem `railway ssh -s postgres -e szczur-radar-pr-<nr> -- psql -U postgres -d railway -c "<SQL>"`: ostatni przebieg `status IN ('succeeded','partial')`, `error` bez `refused 3 calls`; `SELECT count(*) FROM assessments WHERE failure_class = 'provider_error'` jako obserwacja (oczekiwane 0); log API bez `refused 3 calls in a row` i bez ERROR. Wynik (data, deployment, liczby) w komentarzu issue #142 i w tabeli „Results” sekcji S-32.

#### 2. Komentarze w issue #142

**File**: — (GitHub)

**Intent**: Issue ma odzwierciedlać stan: start (gałąź, PR) i wynik PR env; zamknięcie dopiero po weryfikacji produkcji (poza Progress).

**Contract**: Komentarz startowy z fazy 1 (`change-id`, gałąź, PR) i komentarz z wynikiem fazy 3; sekcja S-32 w `deploy-plan.md` dostaje runbook po merge'u (pierwsza noc z bezpiecznikiem: w logu nie ma WARN bezpiecznika; przebieg jak dotąd), realizowany w issue, nie w Progress.

### Success Criteria:

#### Automated Verification:

- CI PR zielone: `gh pr checks <nr PR> --watch` kończy się bez `fail`
- Zwykły przebieg w PR env kończy się jak dotąd: zapytanie SQL z p. 1 pokazuje ostatni przebieg `succeeded` albo `partial` z `error` bez `refused 3 calls`, a `railway logs -s szczur-radar-api -e szczur-radar-pr-<nr>` nie zawiera `refused 3 calls in a row` ani linii ERROR
- Issue #142 ma komentarz startowy i wynik PR env: `gh issue view 142 --comments | grep -c 'szczur-radar-pr-'` wypisuje co najmniej `1`

#### Manual Verification:

- Właściciel przegląda PR i decyduje o merge'u po `/10x-impl-review` i `/10x-archive` (merge wykonuje człowiek)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Testing Strategy

### Unit Tests:

- `ConcurrentAssessmentDispatcherTest`: stop po trzeciej odmowie (próby trzech zużyte, reszta nietknięta, czwarty post nie brany, jeden WARN, zero ERROR, brak błędów technicznych); zerowanie przez `ok` i przez naruszenie kontraktu; prefilter nie zeruje; awaria i błąd naszego kodu nie zwiększają i nie zerują; odmowa zeruje serie awarii (istniejący test) i klienta (nowy).
- `AssessorTest`: `callFailed()` dla odmowy, nie dla naruszenia kontraktu ani prefiltera.

### Integration Tests:

- `PipelineRunIT`: odmowa na każdy post przebiegu POSTS z N postów (wszystkie posty modelu z dnia, N ≥ 7) → `failed`, `3…6` zapisanych odmów (4 wątki), ≥ N − 6 postów nietkniętych, brak błędów technicznych i `dead`, powiadomienie admina, tekst błędu.

### Manual Testing Steps:

1. Właściciel czyta doprecyzowanie PRD i sekcję S-32 `deploy-plan.md` (faza 2).
2. Właściciel loguje się w oknie przeglądarki otwartym przez agenta dla przebiegu w PR env (faza 3; agent wykonuje resztę).
3. Właściciel decyduje o merge'u.

## Performance Considerations

Brak: jeden `AtomicInteger` więcej na krok i jedno porównanie enumu na zapisany wynik. Seria odmów kończy krok po trzech wywołaniach zamiast ~270, więc zła noc jest krótsza i tańsza.

## Migration Notes

Brak migracji, zmiennej i zmiany kontraktu. Historia bez zmian: posty, które wcześniejsze serie uśmierciły, zostają `dead` do FR-034 (S-33). Deploy w zwykłym oknie bez przebiegu `RUNNING`.

## References

- Roadmapa S-32: `context/foundation/roadmap.md:112`, `:183-194`; issue #142
- PRD: NFR `context/foundation/prd.md:275`, doprecyzowania S-09 (`:280`), S-17 (`:283`), PRD v3 (`:285-288`); FR-034 (`:270`)
- Audyt: `context/changes/post-m3-verification-audit/research.md:224-228` (R-41); destylacja: `context/domain/domain-distillation.md:233` (R-41), `:312` (#3), `:337` (Q-18)
- Kod: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:112-118`, `:290-359`; `agents/assessor/Assessor.java:125-160`; `agents/assessor/validation/FailureClass.java`; `llm/transport/TransportException.java:42-46`; `pipeline/flow/RunFlow.java:184`
- Testy: `ConcurrentAssessmentDispatcherTest.java:145`, `:317-332`, `:618-744`, `:1091-1094`; `PipelineRunIT.java:637-671`; `LlmOutageIT.java`
- Wzorzec: `context/archive/2026-10-25-pipeline-without-embabel/plan.md:218-272` (faza 3, bezpiecznik klienta); `context/deployment/deploy-plan.md:1348-1395` (S-09), `:1776` (S-17, bezpiecznik klienta)
- Dane: `context/archive/2026-10-30-reddit-quality-and-cost/report-2026-10-31.md:33`; `context/archive/2026-10-29-reddit-source/reviews/impl-review-phase-5.md:43`
- Lekcje: `context/foundation/lessons.md` — jedna gałąź i PR, status sekcji deploy-plan, Automated vs Manual, strona przez playwright-cli

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Bezpiecznik serii odmów w dyspozytorze

#### Automated

- [x] 1.1 Testy jednostkowe zielone: `cd szczur-radar-api && ./mvnw -B test` — 8cc4987
- [x] 1.2 Testy dyspozytora pokrywają bezpiecznik: `grep -c 'Refusal' …/ConcurrentAssessmentDispatcherTest.java` wypisuje co najmniej `6` — 8cc4987
- [x] 1.3 Stała bez zmiennej: `REFUSAL_STREAK = 3` w dyspozytorze, `REFUSAL` nieobecne w `src/main/resources` i `.railway/railway.ts` — 8cc4987
- [x] 1.4 Opisy pakietów: `REFUSAL_STREAK` w `pipeline/CLAUDE.md` i `llm/CLAUDE.md`, `callFailed` w `agents/assessor/CLAUDE.md` — 8cc4987
- [x] 1.5 Renderer i SVG zgodne z roadmapą: S-32 `progress` w `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg` zregenerowany — 8cc4987

### Phase 2: Scenariusz integracyjny i dokumenty produktu

#### Automated

- [ ] 2.1 Pełna weryfikacja zielona (Docker): `cd szczur-radar-api && ./mvnw -B verify`
- [ ] 2.2 Scenariusz istnieje: `grep -c 'RefusingThreeCallsInARow' …/PipelineRunIT.java` wypisuje `1`
- [ ] 2.3 PRD ma doprecyzowanie S-32: `grep -c 'S-32 `provider-refusal-breaker`' context/foundation/prd.md` wypisuje `1`
- [ ] 2.4 Deploy-plan ma sekcję S-32 ze statusem po merge'u (`delivered by PR #…; … issue #142`)

#### Manual

- [ ] 2.5 Właściciel ocenia tekst doprecyzowania PRD i sekcji S-32 w `deploy-plan.md`

### Phase 3: Weryfikacja w PR env i domknięcie

#### Automated

- [ ] 3.1 CI PR zielone: `gh pr checks <nr PR> --watch` bez `fail`
- [ ] 3.2 Zwykły przebieg w PR env kończy się jak dotąd (SQL: `succeeded`/`partial`, bez `refused 3 calls`; log bez WARN bezpiecznika i bez ERROR)
- [ ] 3.3 Issue #142 ma komentarz startowy i wynik PR env

#### Manual

- [ ] 3.4 Właściciel przegląda PR i decyduje o merge'u po `/10x-impl-review` i `/10x-archive`
