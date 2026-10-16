---
date: 2026-10-15T19:26:39+0200
researcher: Claude (Opus 5.5) for kwmacioszek
git_commit: c80e84654abbf9c4355a235e3122c5d4a55f94d7
branch: feature/pipeline-run-history-dashboard
repository: szczur-radar
topic: "S-06 pipeline-run-history-dashboard — co z historii przebiegów i sygnału awarii już działa po S-02/S-05, czego brakuje do monitorowania trwającego przebiegu (postęp, brak postępu, logi API, heartbeat crona) i do odróżnienia przerwania przez deploy, oraz jakie decyzje musi podjąć plan"
tags: [research, pipeline, admin-view, monitoring, logging, cron, pipeline-trigger, interrupted, s-06]
status: complete
last_updated: 2026-10-15
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Dopisek po /10x-plan: dane z produkcji (logi crona, odczyt bazy przez operatora) i zmiany na main (#58, #59)"
---

# Research: S-06 pipeline-run-history-dashboard

**Date**: 2026-10-15T19:26:39+0200
**Researcher**: Claude (Opus 5.5) for kwmacioszek
**Git Commit**: c80e84654abbf9c4355a235e3122c5d4a55f94d7 (= `origin/main` w chwili badania)
**Branch**: feature/pipeline-run-history-dashboard
**Repository**: szczur-radar

Badanie tylko do odczytu: kod API i SPA, `tools/pipeline-trigger/`, migracje, dokumenty `context/` (z archiwum), komentarze issue #18 i opis PR #56 (`gh`, odczyt). Nie uruchamiałem buildów, testów ani komend Railway; stanu produkcji nie odczytywałem.

## Research Question

Wycinek S-06 z `context/foundation/roadmap.md:241-262`: „admin widzi historię przebiegów (data/godzina, status, wynik) i jednoznaczny sygnał nieudanego lub częściowo nieudanego przebiegu; użytkownik z rolą `user` nie ma do tego dostępu” (FR-021, FR-015, NFR o sygnale awarii; `prd.md:182`, `prd.md:153-156`, `prd.md:258-259`). Do tego dwa dopiski z roadmapy:

- przebieg przerwany deployem ma być pokazany jako przerwany przez wdrożenie, a nie jak zwykła awaria (przegląd S-05, F3; `roadmap.md:252`);
- hardening monitorowania trwającego przebiegu: postęp w `/admin` (krok, czas od startu, gotowe / nieudane / w toku z N, próby, ponowienia, powroty flex → standard, ostatnia aktywność), ostrzeżenie o braku postępu, linia postępu w logu API i heartbeat crona (`roadmap.md:253-261`).

Pytania:

1. Co z historii przebiegów i sygnału awarii już istnieje (API, SPA, cron, Railway)?
2. Co da się dziś zaobserwować o trwającym przebiegu i czego brakuje do postępu na żywo?
3. Jak dziś zapisuje się i rozróżnia przerwanie przebiegu (deploy, zatrzymanie, awaria)?
4. Co logują API i cron, i gdzie wpiąć linię postępu oraz heartbeat?
5. Jakie decyzje (produktowe i techniczne) musi podjąć plan?

## Summary

1. **Historia przebiegów już jest (S-02), sygnału nie ma.**
   - `GET /api/admin/pipeline-runs` zwraca strony przebiegów od najnowszych, z krokami, licznikami i błędem (`PipelineRunController.java:92-102,136-169`).
   - `/admin` pokazuje 20 najnowszych bez stronicowania (`api/pipeline.ts:63-65`), bez dat zakresu, `startedAt`/`finishedAt` i czasu trwania (`AdminView.vue:226-246`).
   - Rozdział ról jest już sprawdzany: `ADMIN_ROUTES` w `AuthorizationCompletenessIT.java:62-67`, 403 dla `user` w `AdminRoleIT.java:54-59`, przekierowanie `user` z `/admin` na `/` w `router/index.ts:65-67` i `router/index.test.ts:77-80`.
   - Sygnał awarii poza listą nie istnieje. `AppHeader.vue` pokazuje tylko link „Panel admina” (`:12,:35`). Feed pokazuje tylko etykietę świeżości (`FeedView.vue:63-68`).
2. **`partial` to norma, nie wyjątek.** „Przy 96–100% zgodności z kontraktem prawie każda noc jest `partial`” (`archive/2026-10-12-daily-scheduled-run-freshness/plan-brief.md:27`); pierwsza noc na produkcji dała RETRY `partial` i RANGE `partial` (issue #18, komentarz zamykający). `partial` = co najmniej jeden post `failed`/`dead` albo błąd techniczny (`RunFlow.java:153-158`). Cron kończy `partial` kodem 0 (`trigger.sh:138-145`), więc aktywny sygnał dla `partial` dziś nie istnieje. PRD odkłada go do S-06 (`prd.md:259`). „Jednoznaczny sygnał częściowego błędu” wymaga więc decyzji, który `partial` jest sygnałem (pyt. D1).
3. **`interrupted` wygląda jak `partial`, a kroki zostają „w toku”.**
   - SPA daje `partial` i `interrupted` ten sam ton `warning` (`AdminView.vue:84-91`).
   - `RunRecorder.interrupt` i `interruptAll` zmieniają tylko `pipeline_runs` (`RunRecorder.java:126-148`). Krok, który trwał w chwili przerwania, zostaje `running` z `finished_at = NULL`, chyba że wątek zdążył zgłosić wyjątek (`RunRecorder.java:115-120`). UI pokaże wtedy „ocena (trwa)” w przerwanym przebiegu.
4. **Deploy jest nieodróżnialny od awarii.**
   - Przyczynę niesie tylko tekst `error`: „interrupted: the application restarted” przy odzyskiwaniu na starcie (`PipelineStartupRecovery.java:67`) albo „interrupted: the application stopped” przy zamykaniu (`PipelineRunService.java:192`).
   - Nie ma id instancji ani deploymentu: repo nie czyta `RAILWAY_DEPLOYMENT_ID` ani `RAILWAY_REPLICA_ID` (grep po `szczur-radar-api/src`, `tools`, `.railway`).
   - Przy deployu przebieg przerywa odzyskiwanie w **nowej** instancji. Tak było 2026-10-13: „`PipelineStartupRecovery` oznaczył oba `interrupted`” (issue #18). Stąd tekst „restarted” — ten sam co po awarii z restartem (wniosek).
5. **Postępu na żywo nie ma w rekordach przebiegu.**
   - Liczniki `assessed_*` zapisują się raz, po końcu całego kroku `assess` (`PipelineAgent.java:106-111`, `RunRecorder.count` `:78-92`).
   - W trakcie oceny dane są tylko pośrednio:
     - N = `items_in` kroku `assess` (`PipelineAgent.java:107`);
     - posty w toku: `posts.stage='assessing' AND assessing_run_id = X` (`PostTransitions.java:55-66`);
     - gotowe: wiersze `assessments.run_id = X` z `status`, `attempts`, `flex_fallback`, `assessed_at` (`V3__create_pipeline_and_posts.sql:130-176`).
   - Brakuje indeksów na `assessments.run_id` i `posts.assessing_run_id`: jedyny indeks `assessments` to `ix_assessments_post_latest` (`V3:178`).
   - Ponowienia w toku, odmowy flex w toku i błędy techniczne przed końcem kroku nie zostawiają śladu w bazie.
6. **Logi.**
   - API ma w `pipeline/**` 7 wywołań loggera, wszystkie `warn`/`error`. W `llm/**` i `agents/assessor/**` loggera nie ma wcale. Nie ma linii o starcie i końcu przebiegu ani kroku, ani o postępie (lista niżej).
   - Cron loguje stan trwającego przebiegu tylko przy zmianie statusu (`trigger.sh:222-227`), więc przebieg `running` przez 40 min daje jedną linię.
   - `RunState` z `/internal/pipeline-runs/{id}` nie ma kroku, `startedAt` ani postępu (`InternalPipelineRunController.java:85-92`).
7. **Wcześniejsze odłożenia do S-06** (są w zakresie):
   - układ listy przebiegów, zagregowany sygnał awarii, automatyczne odświeżanie trwającego przebiegu (`archive/2026-10-10-admin-panel-ui-hardening/ui-after/README.md:87`);
   - daty zakresu i postęp bieżącego kroku (`archive/2026-10-06-first-assessed-wykop-feed/follow-ups/review-fixes.md:67-73`);
   - oznaczenie przebiegu zaplanowanego w UI (`archive/2026-10-12-daily-scheduled-run-freshness/plan.md:57`);
   - aktywne powiadomienie przy `partial` (`plan.md:56`).

## Detailed Findings

### 1. Model danych przebiegu (migracje)

- `pipeline_runs` (`V3__create_pipeline_and_posts.sql:7-36`, `sources` z `V5:6`):
  - `kind`, `status` (`CHECK`: `queued`, `running`, `succeeded`, `partial`, `failed`, `interrupted`; `V3:30-31`);
  - `range_from`, `range_to`, `requested_post_ids`, `requested_by` (FK do `users`; konto crona ma stałe id `00000000-0000-4000-8000-000000000001`, `V6:11-12`);
  - `created_at`, `started_at`, `finished_at`;
  - liczniki `fetched`, `new_posts`, `assessed_ok`, `assessed_failed`, `processing_errors`, `dead`, `skipped`;
  - `error`.
  - Indeksy: `uq_pipeline_runs_one_running` (częściowy na `status='running'`, `V3:39`) i `ix_pipeline_runs_created (created_at DESC, id DESC)` (`V3:40`).
- `pipeline_run_steps` (`V3:43-59`): `step` (`fetch`, `store`, `select`, `assess`, `summarize`), `status` (`running`, `succeeded`, `failed`), `started_at`, `finished_at`, `items_in`, `items_out`, `error`; indeks `(run_id, started_at, id)`.
- `posts` (`V3:84-125`, `V4`): `stage` (`stored`, `assessing`, `assessed`, `failed`, `dead`), `assessment_attempts` 0–3, `assessing_run_id` (wymagany dokładnie przy `stage='assessing'`, `V3:122`), `updated_at`. Brak indeksu na `assessing_run_id` i `first_run_id`.
- `assessments` (`V3:130-176`):
  - `run_id`, `status` (`ok`/`failed`), `failure_class`;
  - `channel`, `service_tier`, `flex_fallback BOOLEAN NOT NULL`;
  - `attempts INT NOT NULL` (próby HTTP z udaną włącznie, oba tiery razem; `TransportRetry.java:69-76`, `LlmResponse.java:25`);
  - `duration_ms`, tokeny, `assessed_at` (koniec wywołania, nie czas zapisu; `Assessor.java:133-134`).
  - Kolumny `created_at` nie ma.
- Nowa wartość statusu lub kolumna (np. przyczyna przerwania) wymaga migracji Flyway i enumu (zasady: `REPOSITORY_GUIDELINES.md` § Project Structure; `JpaSchemaIT`).

### 2. Cykl życia przebiegu i co widać w trakcie

- `queued → running`: `RunRecorder.start` z `PipelineRunService.execute` (`RunRecorder.java:37-42`, `PipelineRunService.java:155`).
- Krok: `RunRecorder.step` wstawia wiersz `running` z `items_in`, po pracy ustawia `succeeded` z `items_out`, po wyjątku `failed` z `ex.getMessage()` i rzuca dalej (`RunRecorder.java:106-124`).
- Znaczenie `items_in` / `items_out` (`PipelineAgent.java`):

  | Krok | `items_in` | `items_out` | Linie |
  | --- | --- | --- | --- |
  | fetch | null | pobrane | 79-80 |
  | store | liczba postów | nowe posty | 87-88 |
  | select | nowe id (range) lub null | do oceny | 95, 101 |
  | assess | N = do oceny | ok + failed | 106-109 |
  | summarize | null | 1 | 118-125 |

- Status końcowy wylicza `AssessedBatch.status()` (`RunFlow.java:153-158`): `failed`, gdy krok zatrzymano (`stoppedBy`: nieudane sprawdzenie LLM albo odrzucona konfiguracja); `succeeded` bez postów `failed`/`dead` i bez błędów technicznych; w pozostałych przypadkach `partial`. Zatrzymany krok `assess` kończy się `succeeded` z `items_out = 0`, a przebieg `failed` (`RunFlow.java:135-146`, `ConcurrentAssessmentDispatcher.java:105-107,156-160`).
- Zapisy per post w trakcie `assess` (`ConcurrentAssessmentDispatcher.java:134-171`, pula `PIPELINE_LLM_CONCURRENCY`, domyślnie 4):
  - claim: `stage='assessing'`, `assessing_run_id`, `updated_at` (`PostTransitions.java:55-66`);
  - record: zmiana etapu i wiersz `assessments` w jednej transakcji (`PostTransitions.java:75-92`);
  - release: powrót bez śladu związanego z przebiegiem (`PostTransitions.java:99-103`).
- Czytelnik bazy widzi więc w trakcie `assess` (ta ścieżka): przebieg `running` z licznikami `fetch`/`store`, `assessed_*` = 0; krok `assess` `running` z `items_in = N`; w toku co najwyżej tyle postów, ile wątków; gotowe — wiersze `assessments` przebiegu (bez indeksu, skan sekwencyjny); ostatnią aktywność — `max(assessed_at)` lub `max(posts.updated_at)` postów w toku.
- Nie widzi: błędów technicznych (tylko log, `ConcurrentAssessmentDispatcher.java:163`, do przebiegu trafiają po końcu kroku), ponowień i odmów flex wywołania w toku, wywołania sprawdzającego LLM.
- Możliwe punkty obserwacji postępu (wniosek, bez prototypu): zadanie per post w dyspozytorze, `PostTransitions.record`, dekorator beana `LlmClient` (`LlmConfiguration.java:25-29`). `TransportRetry` nie ma słuchacza; jedyny szew to `Sleeper` (`TransportRetry.java:32-39`). W repo nie ma `@Scheduled`/`@EnableScheduling`.

### 3. Przerwanie i niepowodzenie przebiegu

- Zamykanie instancji (`onClose`, `PipelineRunService.java:185-196`): `interrupt(own, "interrupted: the application stopped")` ustawia `interrupted`, `finished_at`, `error` (`RunRecorder.java:126-138`), oddaje posty, `shutdownNow()`.
- Odzyskiwanie na starcie, zanim API otworzy port (`PipelineStartupRecovery.java:57-84`): `interruptAll("interrupted: the application restarted")` (`RunRecorder.java:141-148`) i `releaseOrphans`. Przerywa każdy przebieg `queued`/`running`, nie pytając o właściciela (`pipeline/CLAUDE.md`, „Rules”). Dla przebiegu, który umarł z instancją, `finished_at` to czas restartu, nie śmierci.
- Wyjątek w przebiegu (`PipelineRunService.java:152-168`): `log.error("Pipeline run {} failed")`, `error = SimpleName + ": " + message`, `recorder.fail` tylko dla `queued`/`running` (`RunRecorder.java:68-75`), więc przebiegu już `interrupted` nie nadpisuje.
- Kroki przerwanego przebiegu: `interrupt`/`interruptAll` nie dotykają `pipeline_run_steps` (`RunRecorder.java:126-148`). Po twardym zabiciu albo przy przerwaniu przez nową instancję krok zostaje `running` na zawsze. Przy łagodnym zatrzymaniu krok może skończyć się `failed`, jeśli przerwanie wątku wyjdzie jako `IllegalStateException("interrupted while assessing run …")` (`ConcurrentAssessmentDispatcher.java:177-180`) — kolejność nie jest gwarantowana (niesprawdzone uruchomieniem).
- Testy przerwania (`PipelineAgentIT.java:238,324`, `PipelineStartupRecoveryIT.java:36`) nie sprawdzają tekstu `error` ani stanu kroków.
- Zdarzenie z produkcji (issue #18): ręczny przebieg z 2026-10-13 17:41 UTC (`faba1678`, `b1995b4e`) przerwał deploy API po merge'u #55 (sama dokumentacja); recovery oznaczył oba `interrupted` i oddał 4 posty. Tam też: `watchPatterns` nie zawężają deployów na produkcji — każdy commit na `main` przedeployował API, web i crona („do zbadania osobno”).

### 4. API dla admina i dla crona

- `GET /api/admin/pipeline-runs?page&size` (`PipelineRunController.java:92-102`): `size` 1–100 (domyślnie 20; 101 → 400), kolejność `created_at desc, id desc` (`PipelineRunRepository.java:26-28`), kroki strony jednym zapytaniem (`PipelineRunStepRepository.java:16`). Odpowiedź `RunPage{items, page, size, total}`, `RunView` z polami jak w tabeli, `StepView{step, status, startedAt, finishedAt, itemsIn, itemsOut, error}` (`:136-169`). Nie ma `requestedBy` (cron czy admin), filtra statusu ani pól postępu.
- `GET /api/admin/posts/failed` (`FailedPostController.java:47-89`): posty `failed`/`dead` z ostatnią próbą (`failureClass`, `violations`, `assessedAt`, `provider`, `model`); bez `runId`, `attempts`, `flexFallback`.
- `GET /internal/pipeline-runs/{id}` (`InternalPipelineRunController.java:65-70,85-93`): `RunState{id, kind, status, finishedAt, fetched, newPosts, assessedOk, assessedFailed, processingErrors, dead}`; bez `skipped`, `startedAt`, `error`, kroków. `InternalPipelineRunIT` sprawdza dokładny zbiór kluczy (ok. `:213-222`), więc rozszerzenie `RunState` zmienia ten test.
- Ochrona: `/api/admin/**` → `hasRole(ADMIN)` (`SecurityConfig.java:106`); `/internal/**` osobny łańcuch, tylko token (`SecurityConfig.java:70-76`). Nowa trasa admina trafia do `ADMIN_ROUTES` (`AuthorizationCompletenessIT.java:62-67`), co automatycznie obejmuje ją testem 403 w `AdminRoleIT`.
- Testy listy: `PipelineRunControllerIT.java:257-304` (kolejność, strony, `partial`, liczniki, błąd, kroki, 400 przy `size=101`).

### 5. Widok `/admin` (SPA)

- Sekcje (`AdminView.vue`): formularz „Nowy przebieg” (`:176-214`), „Ostatnie przebiegi” z „Odśwież” (`:216-249`), „Nieudane posty” z „Ponów zaznaczone” (`:251-283`). Interfejsu sprawdzenia LLM (`/api/admin/llm/check`) w SPA nie ma (grep `src/`, `dev/`).
- Wiersz przebiegu: rodzaj, `StatusBadge` statusu, `formatDateTime(createdAt)`, źródła; liczniki z „błędy techniczne N” na czerwono przy > 0 (`.alarm`); kroki: udany jako tekst „etykieta (itemsOut)”, inny jako `StatusBadge`; `error` na czerwono (`:105-114,:226-246,:363-377`). Nie są renderowane `rangeFrom`/`rangeTo`, `startedAt`, `finishedAt`, czasy i `itemsIn` kroków.
- Odświeżanie: raz przy montowaniu, potem tylko przycisk „Odśwież” i po starcie/ponowieniu (`:69,:149,:162-168`); brak `setInterval` i `onUnmounted`. Daty: `Intl` `pl-PL`, `Europe/Warsaw` (`labels.ts:72-81`); formatera czasu względnego („od 7 min”) nie ma.
- `StatusBadge` ma 5 tonów: `success`, `warning`, `destructive`, `neutral`, `info` (`StatusBadge.vue:3,23-41`); tekst zawsze mówi stan, kolor go wzmacnia (`:2`).
- Kontrakt stylu: `AdminView.vue`, `StatusBadge.vue`, `AppHeader.vue` są na liście `CLEANED` (`styleContract.test.ts:93-97`).
- Kitchen sink: znaczniki „zakończony / częściowo / błąd / trwa / w kolejce” (`KitchenSinkView.vue:55-62,472-477`), bez „przerwany” i bez wiersza przebiegu.
- `dev/mock-api.js`:
  - przebiegi `r1`–`r5` (`:55-63`). `r5` `running` nie ma jeszcze kroku `assess`. `r1` `interrupted` ma wszystkie kroki udane i `error: null`, czyli stan, którego API nie wytworzy (por. § 3);
  - `rangeFrom`/`rangeTo` są gołymi datami zamiast `Instant` (`:54-56`);
  - POST zawsze odpowiada 409 (`:211`).
- Testy `AdminView.test.ts` opierają się na selektorach `.run`, `.status`, `.alarm`, `.run-error`, `.steps`, `.loading`, `.empty`, `.failed-posts`, `section[aria-labelledby=runs-title|failed-title]`, `#start-hint` (`:92-334`). Hardening z 2026-10-10 ustalił, że istniejące selektory zostają (`archive/2026-10-10-admin-panel-ui-hardening/plan.md:33,265`).
- Ograniczenia z hardeningu, które nadal obowiązują:
  - status zawsze tekstem i kolorem (`plan.md:23`);
  - „Uruchom” to jedyny przycisk główny (`plan.md:235`);
  - komponenty bez nazw i tonów specyficznych dla admina (`plan.md:54`);
  - ładowanie per sekcja, lista zostaje podczas odświeżania (`plan.md:306`);
  - bez skoku układu przy 1280 px i bez przewijania w poziomie przy 375 px (`ui-after/README.md:29,31`);
  - podział `AdminView` na komponenty sekcji był poza zakresem hardeningu (`plan.md:41`), nie jest zakazany.

### 6. Logi API

Wszystkie wywołania loggera w `pipeline/**`, `llm/**`, `agents/assessor/**` (grep `log.(info|warn|error|debug)`):

| Miejsce | Poziom | Treść |
| --- | --- | --- |
| `PipelineRunService.java:161` | ERROR | „Pipeline run {} failed” + wyjątek |
| `PipelineRunService.java:181` | ERROR | „Pipeline run {} could not be closed; …” |
| `PipelineStartupRecovery.java:70` | WARN | „Put back {} post(s) left assessing by unfinished runs” |
| `PipelineStartupRecovery.java:76` | ERROR | „Start-up recovery of pipeline runs failed; …” |
| `PipelineStartupRecovery.java:81` | WARN | „Marked {} unfinished pipeline run(s) interrupted: {}” |
| `ConcurrentAssessmentDispatcher.java:106` | ERROR | „The LLM check before run {} failed; …” |
| `ConcurrentAssessmentDispatcher.java:163` | ERROR | „Assessing post {} in run {} failed; …” |

- Odmowa konfiguracji LLM w trakcie przebiegu nie jest logowana, tylko trafia do `error` przebiegu.
- `application.properties` nie ma kluczy `logging.*`, a w repo nie ma pliku logback, więc obowiązują domyślne ustawienia Spring Boot. Że domyślny log konsoli idzie na stdout (a nie stderr, który Railway pokazuje jako `error`, `deploy-plan.md:452`), to wiedza o frameworku, niesprawdzona w repo.
- Logi w Railway są trzymane 7 dni na Hobby (`infrastructure.md:122`). Reguła repo: sekrety nigdy w logach (`REPOSITORY_GUIDELINES.md`).

### 7. Cron `pipeline-trigger`

- Każda linia idzie na stderr: `<UTC ISO> trigger: …` (`trigger.sh:11`).
- Pętla oczekiwania (`trigger.sh:202-247`):
  - co `TRIGGER_POLL_INTERVAL` (30 s) robi `GET /internal/pipeline-runs/{id}` dla każdego niezakończonego przebiegu;
  - w trakcie czyta tylko `.status` (`:220`) i loguje go tylko przy zmianie (`seen[$id]`, `:222-227`);
  - na końcu loguje liczniki (`:234`).
- `classify`: `succeeded|partial` → ok, `failed|interrupted` → failed (exit 1), `queued|running` → aktywny (`:138-145`). `interrupted` jest więc dla crona tym samym co `failed`, a Railway wysyła powiadomienie „deployment crashed”, jeśli konto ma tę regułę (`deploy-plan.md:449`).
- Heartbeat pasuje do gałęzi „aktywny” tej pętli: linia czasowa co N minut z tym samym GET-em, bez dodatkowych żądań. Do kroku i postępu potrzebuje pól, których `RunState` nie zwraca (§ 4).
- Testy (`tools/pipeline-trigger/test.sh`):
  - `log_says` to `grep -qF` po logu (`:169-172`);
  - `requests_were` liczy POST-y i GET-y, np. 1 POST i 3 GET-y dla `succeeded` (`:174-181,:200-205`);
  - atrapa (Python) zdejmuje kolejny status przy każdym GET-cie i zwraca stałe liczniki (`:50-118`).

  Heartbeat bez dodatkowych GET-ów nie zmienia liczników żądań. Nowe pola wymagają rozszerzenia atrapy, a heartbeat czasowy zmiennej z interwałem, żeby test mógł go skrócić (wniosek).

## Code References

- `szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:7-59,84-125,130-178` — tabele przebiegów, kroków, postów, ocen; indeksy.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/RunRecorder.java:37-42,68-92,106-148` — start, fail, liczniki, kroki, przerwanie (bez kroków).
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/PipelineAgent.java:79-125` — kroki, `items_in`/`items_out`, liczniki po kroku.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunFlow.java:135-158` — `AssessedBatch.status()`, błędy zatrzymania.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:82,103-107,134-180` — sprawdzenie LLM, pula, zadanie per post, logi.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/state/PostTransitions.java:55-103` — claim / record / release.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStartupRecovery.java:57-84` — odzyskiwanie przed portem, tekst „restarted”.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:152-196` — niepowodzenie, zamykanie, tekst „stopped”.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java:92-169` — lista admina, `RunView`, `StepView`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunController.java:65-93` — `RunState` dla crona.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/transport/TransportRetry.java:32-39,50,69-76,102` — próby, `Sleeper`, brak słuchacza.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java:62-67`, `AdminRoleIT.java:54-59` — trasy admina i 403.
- `szczur-radar-web/src/views/AdminView.vue:84-91,94-114,162-168,216-249` — tony, ładowanie, odświeżanie, wiersz przebiegu.
- `szczur-radar-web/src/api/pipeline.ts:5-39,63-65` — typy i `fetchRuns(0, 20)`.
- `szczur-radar-web/src/components/StatusBadge.vue:2-41` — tony.
- `szczur-radar-web/dev/mock-api.js:48-63,207-212` — przebiegi atrapy, tryby admina.
- `tools/pipeline-trigger/trigger.sh:11,138-145,202-247` — log, `classify`, pętla oczekiwania.
- `tools/pipeline-trigger/test.sh:50-118,169-205` — atrapa API, asercje logu i liczby żądań.

## Architecture Insights

- Każda zmiana stanu przebiegu i posta to jeden warunkowy `UPDATE` (`pipeline/CLAUDE.md`, „Rules”). Licznik postępu zapisywany per post pasowałby do tego wzorca (`UPDATE … SET assessed_ok = assessed_ok + 1 WHERE id = :run AND status = 'running'`). Alternatywą jest wyliczanie postępu przy odczycie z `assessments` i `posts`, które potrzebuje indeksów. To wybór projektowy, nie fakt (pyt. D4).
- Pipeline jest agentem Embabel z akcjami `FIRE_ONCE`; kroki to cienkie akcje, a logika siedzi w serwisach (`pipeline/CLAUDE.md`, lekcja „Akcje Embabel…” w `lessons.md`). Postęp i logi należą do dyspozytora oraz `RunRecorder`, nie do akcji.
- Trasy `/internal/**` mają osobny, bezstanowy łańcuch tylko z tokenem; rozszerzenie `RunState` nie zmienia bezpieczeństwa, ale zmienia kontrakt sprawdzany w `InternalPipelineRunIT`.
- SPA: tokeny w `style.css`, wspólne komponenty w `src/components/`, kontrakt w `styleContract.test.ts`; praca nad istniejącym widokiem idzie przez `/10x-ui` (`CLAUDE.md`, `REPOSITORY_GUIDELINES.md` § UI). Nowy wspólny komponent (np. wiersz przebiegu, pasek postępu) trafia do `src/components/` i na kitchen sink.
- Zamknięte zbiory wartości są enumami (`lessons.md`, „Zamknięte zbiory wartości…”). Dotyczy to ewentualnej przyczyny przerwania.

## Historical Context (from prior changes)

- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:70` — „No run history dashboard beyond a simple list of recent runs and steps (S-06)”. Nadal trafne: lista jest prosta.
- `context/archive/2026-10-06-first-assessed-wykop-feed/follow-ups/review-fixes.md:67-73` — z weryfikacji w PR env: brak dat zakresu w liście; trwający przebieg pokazuje tylko liczniki zakończonych kroków; przycisk startu ma mówić, czemu drugi przebieg jest odrzucany. Stan dziś:
  - daty zakresu: nadal brak (§ 5);
  - postęp: nadal brak (§ 2);
  - powód blokady przycisku: częściowo — jest `#start-hint` dla braku źródła, a 409 pokazuje się jako alert po kliknięciu (`AdminView.test.ts:129-135`). Brak podpowiedzi przed kliknięciem, gdy przebieg trwa (wniosek z `AdminView.vue:39-44`).
- `context/archive/2026-10-10-admin-panel-ui-hardening/ui-after/README.md:87,90`:
  - D1 → S-06: układ listy, zagregowany sygnał awarii, automatyczne odświeżanie. Aktualne.
  - Zmiana `admin-failed-posts-table` (wspólny wygląd tabeli także dla historii S-06) nie istnieje w `context/changes/` ani w archiwum.
- `context/archive/2026-10-12-daily-scheduled-run-freshness/plan.md:56-57` — e-mail przy `partial`, aktywne powiadomienia, dashboard i oznaczanie przebiegu zaplanowanego to S-06. Aktualne.
- `context/archive/2026-10-12-daily-scheduled-run-freshness/plan-brief.md:27` — prawie każda noc jest `partial`. Potwierdzone pierwszą nocą na produkcji (issue #18).
- `context/archive/2026-10-12-daily-scheduled-run-freshness/` (impl-review, F3) — odzyskiwanie przerywa przebieg niezależnie od właściciela; decyzja: bez zmiany kodu w S-05, notka do S-06. Pomysł „właściciel przebiegu po instancji” to jedno zdanie w `roadmap.md:252`, bez projektu.
- `roadmap.md:253-256` i PR #56: przebieg stał w `assess` ponad 7 minut bez śladu. Roadmapa nazywa go „uruchomionym ręcznie przebiegiem RETRY”, a nagłówek zmian (`roadmap.md:54`) i PR #56 mówią o „pierwszym przebiegu crona”. Przyczyny nikt nie zapisał. Że chodzi o ręczny przebieg z 17:41 UTC, przerwany później deployem (issue #18), to wniosek z czasu, niepotwierdzony. Liczby postów tamtego przebiegu nikt nie zapisał. Gdyby miał ich rząd 150 (tyle nadrobił nocny RETRY), to przy 4 wątkach i 10–20 s na ocenę (`first-assessed-wykop-feed/plan.md:586`) `assess` trwałby ok. 6–12 minut, więc 7 minut mieściłoby się w normalnym czasie. Możliwe więc, że przebieg nie stał, tylko był niewidoczny (wniosek warunkowy).
- `context/deployment/deploy-plan.md:431` — status sekcji S-05 nadal brzmi „in the PR env (#50), not merged”, choć issue #18 zamknięto 2026-10-15 po weryfikacji produkcji. Dokument jest nieaktualny w tym jednym zdaniu; fakty z pierwszej nocy są tylko w issue #18. Sekcja S-04 (`deploy-plan.md`, „filter-by-assessment”) też ma status sprzed merge'u `c80e846` — tego nie sprawdzałem w issue #17.
- Retencji ani stronicowania historii przebiegów nie rozstrzygnięto nigdzie poza `page`/`size` API; wzmianki o retencji dotyczą tylko kopii bazy.

## Related Research

- `context/archive/2026-10-12-daily-scheduled-run-freshness/research.md` — wyzwalacz dzienny, statusy, etykieta świeżości.
- `context/archive/2026-10-10-admin-panel-ui-hardening/research.md` — audyt `/admin` i odłożenie D1.
- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md` — model przebiegu i kolejka.

## Open Questions

Decyzje dla `/10x-plan` (właściciel: użytkownik, chyba że zaznaczono inaczej):

- **D1. Co jest „sygnałem” przy `partial`.** Prawie każda noc kończy się `partial` (§ Summary 2), więc sygnał przy każdym `partial` byłby szumem. Kandydaci:
  - próg (np. udział nieudanych, nowe posty `dead`, `processing_errors > 0`);
  - rozróżnienie „`partial` z błędami technicznymi” od „`partial` z postami niezgodnymi z kontraktem”;
  - stan „wymaga uwagi” liczony z ostatnich przebiegów.
- **D2. Aktywne powiadomienie czy sam dashboard** (`roadmap.md:250`, nierozstrzygnięte). Dziś aktywny kanał to tylko powiadomienie Railway przy niezerowym kodzie crona (`failed`/`interrupted`). Opcja bez nowej infrastruktury (wniosek): cron kończy się kodem ≠ 0 także przy `partial` ponad progiem z D1. Kosztem jest mylenie „crashed” z „częściowo”.
- **D3. Gdzie widać sygnał.** Tylko na liście w `/admin`, zbiorczy stan na górze `/admin` czy też znacznik w `AppHeader` dla admina na każdej stronie (dziś nie ma żadnego, § 5)?
- **D4. Postęp na żywo.** Trzeba wybrać jeden ze sposobów:
  - (a) liczniki per post i `last_activity_at` w `pipeline_runs`: warunkowy `UPDATE` przy każdym zapisie oceny, migracja kolumn;
  - (b) wyliczanie przy odczycie z `assessments`/`posts`: migracja indeksów na `assessments.run_id` i `posts.assessing_run_id`.

  Ponowienia i powroty flex są dziś znane dopiero po zakończeniu wywołania (`attempts`, `flex_fallback`). Pokazanie ich „w toku” wymaga nowego zapisu w warstwie `llm/` (właściciel: plan).
- **D5. Brak postępu.** Wymaga decyzji:
  - próg X minut (wartość od użytkownika);
  - względem czego liczony: ostatnia ocena, start kroku czy ostatni claim;
  - kto go ocenia: serwer (pole w odpowiedzi, log WARN) czy SPA.
- **D6. Przerwanie przez deploy.**
  - Kroki do naprawy: zamknąć otwarte kroki przy `interrupt`/`interruptAll` (wniosek: to błąd stanu niezależnie od D6).
  - Rozróżnienie przyczyny wymaga wyboru:
    - (a) zapis instancji/deploymentu przebiegu: `RAILWAY_DEPLOYMENT_ID` dostępny w kontenerze to wiedza o platformie, niesprawdzona w repo; migracja;
    - (b) zamknięty zbiór przyczyn przerwania (enum + kolumna) zamiast tekstu `error`;
    - (c) tylko inny opis w UI dla „restarted”/„stopped”.

    Rozwiązanie trwałe (przebieg dokończony mimo deployu) jest poza S-06 według `roadmap.md:252`.
- **D7. Heartbeat crona.** Do ustalenia:
  - interwał (zmienna `TRIGGER_*` z jednostką);
  - zakres pól `RunState`: `startedAt`, bieżący krok, postęp (zmiana kontraktu w `InternalPipelineRunIT` i atrapie `test.sh`);
  - czy cron ma osobno logować przebieg `interrupted`.
- **D8. Linia postępu w logu API.** Do ustalenia:
  - co N postów czy co minutę (repo nie ma `@Scheduled`, dyspozytor ma własną pulę);
  - poziom INFO;
  - start i koniec przebiegu oraz kroku jako INFO (dziś brak).
- **D9. Historia.** Do ustalenia:
  - ile przebiegów pokazać (dziś 20) i czy stronicować (API do 100 na stronę);
  - czy pokazać daty zakresu, czas trwania i kto uruchomił (wymaga dodania `requestedBy` lub flagi „zaplanowany” do `RunView`);
  - czy zrobić najpierw `admin-failed-posts-table` (wspólny wygląd tabeli).
- **D10. Automatyczne odświeżanie `/admin`.** Interwał, tylko gdy jest przebieg `queued`/`running`, zatrzymanie przy opuszczeniu widoku (`onUnmounted`).
- **D11. Podział pracy** (właściciel: plan). Część wizualna `/admin` podlega `/10x-ui` (`CLAUDE.md`). Plan musi ustalić, czy idzie fazą tej zmiany przez łańcuch `/10x-ui`, czy osobną zmianą po backendzie.
- **D12. Atrapa `dev/mock-api.js`.** Przebieg `interrupted` (`r1`) i `running` bez kroku `assess` (`r5`) trzeba urealnić, a dla zrzutów stanów potrzebny jest tryb z postępem i bez postępu (właściciel: plan).

Luki faktów (nie blokują planu):

- Czy Spring Boot loguje tu na stdout (poziom w Railway) — wiedza o frameworku, niesprawdzona w repo.
- Czy Embabel opakowuje wyjątki kroku (wtedy `error` przebiegu ma nazwę klasy opakowania) — niesprawdzone; `PipelineAgentIT.java:289` sprawdza tylko, że błąd nie jest pusty.
- Kolejność przy łagodnym zatrzymaniu (czy krok kończy się `failed`) — niesprawdzone uruchomieniem.
- Przyczyna „7 minut bez śladu” — niezapisana (§ Historical Context).

## Follow-up 2026-10-15 (podczas `/10x-plan`)

Źródła:
- logi crona `pipeline-trigger` z produkcji (`railway logs`, deploymenty `e379f584`, `135feaac`);
- zapytanie SQL tylko do odczytu, uruchomione przez operatora na produkcji;
- `git log c80e846..origin/main`.

**Noce:**
- 14.10: RETRY `fe22e327` `partial` (148 ok, 3 failed) i RANGE `355ca0fd` `partial` (469 pobranych, 458 ok, 11 failed).
- 15.10: RETRY `ef5f316d` `succeeded` (1 ok) i RANGE `8c0bc6a3` `partial` (657 pobranych, 400 nowych, 393 ok, 7 failed).
- Żadna z tych czterech nie miała `dead` ani błędów technicznych.

**Zagadka RETRY 15.10 (§ Historical Context) rozwiązana.** 14.10 o 14:30 UTC ręczny RETRY `8bdc2841` ocenił 14 zaległych postów: 12 ok, 2 failed, z czego 1 `dead`. Na noc został 1 post. Stan postów 2026-10-15: `dead` 1, `failed` 7, `assessed` 1143.

**„7 minut w `assess`” (§ Historical Context):**
- Przebieg `faba1678` ma `requested_by` = konto crona. Uruchomił go operator przez crona 2026-10-13 o 17:41 UTC (`deploy-plan.md`, S-05, po #59), więc słowo „ręcznie” w roadmapie znaczy „ręcznie przez crona”.
- Ocenił 104 posty, średnio 37,9 s na wywołanie, z 19 ponowieniami HTTP. Najdłuższa przerwa między kolejnymi ocenami wyniosła 42 s.
- Nie stał. Był niewidoczny, a potem przerwał go deploy #55 z tekstem „interrupted: the application restarted”. To potwierdza § Summary 4.

**Przerwy między ocenami.** Najdłuższa w przebiegu: 16–42 s; 99. percentyl ≤ 32 s (przebiegi z 13–15.10).

**Ponowienia i flex:**
- `sum(attempts − 1)` wychodzi ujemne (np. −41), bo oceny z prefiltru mają `attempts = 0`. Ponowienia trzeba liczyć jako `max(attempts − 1, 0)`.
- `flex_fallback` = 0 we wszystkich przebiegach z 13–15.10.

**Przebieg `39a3e9c6` z 2026-10-09** (RANGE, 240 nowych, 0 ocenionych) miał status `running` od 14:17 do odzyskiwania 2026-10-10 o 12:20, czyli ok. 22 h. Potwierdza to, że zawieszony przebieg blokuje kolejkę do restartu.

**Ustawienia produkcji:** `PIPELINE_LLM_CONCURRENCY` nieustawione (4 wątki), `LLM_SERVICE_TIER=flex`, `LLM_TIMEOUT=300s`.

**Railway.**
- `RAILWAY_DEPLOYMENT_ID` jest zmienną dostarczaną przez Railway (docs: Variables reference).
- Domyślnie stary deployment jest zatrzymywany dopiero wtedy, gdy nowy działa (docs: Deployment teardown).
- To zastępuje „wiedzę o platformie, niesprawdzoną w repo” z D6.

**Zmiany na `main` po `c80e846`:**
- #58 przenosi `watchPatterns` do `build`. Do 2026-10-15 każdy commit na `main` redeployował wszystkie usługi; teraz merge samej dokumentacji nie restartuje API.
- #59 aktualizuje `deploy-plan.md` S-05 do „live on production”. Uwaga w § Historical Context o nieaktualnym statusie S-05 jest więc **nieaktualna**.
- Nowy fakt z #59: kontener crona zastąpiony przez deploy nie daje sygnału, bo zastąpiony deployment nie jest `CRASHED`. Przerwany w ten sposób przebieg widać tylko w `/admin`.

**Brak limitu czasu przebiegu.** Potwierdzone grepem `timeout` w `pipeline/`, `sources/`, `llm/transport/` i `application.properties`. Są tylko limity pojedynczych wywołań: Wykop 30 s (`WykopApiClient.java:40`) i `LLM_TIMEOUT` na próbę. Zapytania JDBC nie mają limitu.

Decyzje D1–D12 rozstrzyga `plan.md` / `plan-brief.md` (tabela „Key Decisions Made”).
