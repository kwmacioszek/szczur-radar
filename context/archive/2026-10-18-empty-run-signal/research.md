---
date: 2026-10-18T12:21:50+0200
researcher: kwmacioszek (Claude Opus 5.5)
git_commit: c7db08c164acd537c5a5e155800cc15c9a656a52
branch: feature/empty-run-signal
repository: kwmacioszek/szczur-radar
topic: "S-08 empty-run-signal: jak dziś kończy się pusty przebieg i co trzeba zmienić, żeby dawał sygnał"
tags: [research, codebase, pipeline, run-status, needs-attention, notifications, pipeline-trigger, freshness, wykop, skipped]
status: complete
last_updated: 2026-10-18
last_updated_by: kwmacioszek (Claude Opus 5.5)
---

# Research: S-08 empty-run-signal — pusty przebieg i jego sygnał

**Date**: 2026-10-18T12:21:50+0200
**Researcher**: kwmacioszek (Claude Opus 5.5)
**Git Commit**: c7db08c164acd537c5a5e155800cc15c9a656a52
**Branch**: feature/empty-run-signal
**Repository**: kwmacioszek/szczur-radar

## Research Question

Slice S-08 z roadmapy M-2 (`context/foundation/roadmap.md:147-158`). Cel: operator dostaje sygnał (powiadomienie w aplikacji i niezerowe wyjście crona), gdy przebieg nic nie pobrał albo pominął elementy o nieoczekiwanym kształcie. Etykieta „Dane zaktualizowane” nie przesuwa się na takim przebiegu. Źródło: audyt C1 (H) i B5 (`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:76,90`).

Pytania badawcze:
1. Jak dziś powstaje status przebiegu, liczniki, `needsAttention`, powiadomienie, odpowiedź `/internal`, wyjście crona i data świeżości, i gdzie w tym łańcuchu ginie pusty przebieg?
2. Co dokładnie znaczą `fetched`, `skipped` i `newPosts` i który predykat oddaje „pusty przebieg”?
3. Które kontrakty i testy zmieni S-08 po obu stronach (API ↔ `trigger.sh`, API ↔ SPA)?
4. Czy pusty dzień na tagu jest realny? Dane o wolumenach.
5. Gdzie kończy się S-08, a zaczynają S-12 i S-16?

## Summary

**Pusty przebieg RANGE kończy się dziś `succeeded`, i to z założenia.** Status liczy wyłącznie wynik kroku oceny (`RunFlow.java:150-155`). Gdy żaden post nie trafia do oceny, dyspozytor od razu zwraca pustą partię (`ConcurrentAssessmentDispatcher.java:122`), a ta daje `SUCCEEDED`. Liczniki `fetched` i `skipped` są zapisywane osobno (`PipelineAgent.java:79-81`) i nie wpływają na status, `needsAttention`, cron ani świeżość. Wszystkie pięć punktów sygnalizacji przepuszcza taki przebieg jako udany:
- `needsAttention` daje `false` dla `SUCCEEDED` (`PipelineRun.java:283-288`), więc nie ma powiadomienia (`RunNotifications.java:44`);
- `RunState` w `/internal` nie ma `skipped` ani `needsAttention` (`InternalPipelineRunController.java:103-106`);
- cron mapuje `succeeded | partial` na ok, czyli exit 0 (`trigger.sh:144,282`);
- `/api/freshness` liczy każdy RANGE `succeeded`/`partial` (`PipelineRunRepository.java:39-48`);
- końcowa linia logu API (`PipelineRunService.java:306-310`) i linia crona (`trigger.sh:259`) nie wypisują `skipped`.

Dwa testy wprost przypinają dziś „pusty dzień = `succeeded`” i S-08 musi je zmienić: `InternalPipelineRunIT.java:226-230` (atrapa Wykopu z `{"data":[]}`, `:96`) i `RunOutcomeTest.java:35`. `PipelineRunControllerIT` też ma pustą atrapę (`:89`), ale jego asercja `needsAttention = false` (`:175`) dotyczy przebiegu jeszcze `queued`. Inne asercje tej klasy na zakończonych przebiegach (`:294, 321, 348`) plan musi sprawdzić pod nową regułą.

**Predykat.** W Wykopie `fetched` = elementy po deduplikacji + elementy o złym kształcie JSON (`WykopFetcher.java:55`). `skipped` = zły kształt JSON + elementy, których nie da się zmapować (`WykopFetcher.java:46-54`). Zatem `skipped ⊆ fetched`, a liczba postów przekazanych do zapisu to `fetched − skipped`:
- „0 pobranych” to `fetched = 0`;
- „same pominięte” to `fetched = skipped`;
- oba przypadki obejmuje warunek `fetched − skipped = 0`.

`newPosts = 0` nie jest dobrym kryterium: ten sam zakres uruchomiony ponownie daje legalne `new = 0` (`PipelineAgentIT.java:201`), a okno D-2…D-1 co noc pobiera D-2 drugi raz.

**Wolumeny.** Każdy zaobserwowany przebieg RANGE (okno 2 dni) pobrał od 74 do 864 elementów. Najmniejszy (74) to przebieg w PR env z 2026-10-12, prawdopodobnie za sobotę i niedzielę (`deploy-plan.md:489`). W `context/` nie ma ani jednej obserwacji `fetched = 0` ani `skipped > 0`. 0 pobranych w oknie 2 dni wskazuje na awarię. Niski, ale niezerowy próg byłby kruchy, bo weekendy mają ~35 elementów dziennie.

**Granice.** S-08 sygnalizuje na podstawie liczników, które już istnieją na poziomie przebiegu. Wykrycie niekompletnego stronicowania (B5) należy do S-16 (`roadmap.md:248-259`): utracone elementy nigdy nie trafiają do `fetched`, więc S-08 ich nie zobaczy. Dostarczalność powiadomień i spójność kroków ze statusem to S-12 (`roadmap.md:198-208`).

**Sprzeczność z PRD do oznaczenia.** Doprecyzowanie S-06 w NFR mówi „Kody wyjścia crona się nie zmieniają” (`prd.md:260`). Wynik S-08 wymaga niezerowego wyjścia przy pustym przebiegu (`roadmap.md:149`). Plan musi dopisać nowe doprecyzowanie do PRD, a nie rozstrzygać tego po cichu w kodzie.

## Detailed Findings

Konwencja: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`, `web/…` = `szczur-radar-web/src/…`.

### 1. Status przebiegu

- `RunStatus` = `QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, INTERRUPTED` (`api/pipeline/run/RunStatus.java:13`), z regułą `CHECK` w `V3__create_pipeline_and_posts.sql:30-31`. Nowy status wymaga migracji tej reguły.
- `SUCCEEDED/PARTIAL/FAILED` z normalnego zakończenia wylicza `RunFlow.AssessedBatch.status()` (`api/pipeline/flow/RunFlow.java:150-155`):
  - `stoppedBy != null` daje `FAILED`;
  - `failed == 0 && dead == 0 && processingErrors.isEmpty()` daje `SUCCEEDED`;
  - w pozostałych przypadkach `PARTIAL`.

  Rekord `AssessedBatch` nie niesie `fetched` ani `skipped`.
- Pusta lista postów do oceny daje `AssessedBatch(0,0,0,[],null)` przed sprawdzeniem LLM (`api/pipeline/assess/ConcurrentAssessmentDispatcher.java:122`), a więc `SUCCEEDED`.
- Zapis statusu: krok SUMMARIZE w `PipelineAgent.summarize` (`api/pipeline/flow/PipelineAgent.java:121-128`), potem `RunRecorder.finish`, czyli warunkowy `UPDATE … WHERE status='running'` (`api/pipeline/state/RunRecorder.java:76-80`).
- Inne drogi do `FAILED`:
  - wyjątek dowolnej akcji: `PipelineRunService.execute` → `RunRecorder.fail`, z `error = "<Klasa>: <komunikat>"` (`api/pipeline/PipelineRunService.java:231-244`);
  - watchdog: `RunRecorder.stall`;
  - źródło przestało być skonfigurowane między kolejką a pobraniem (`api/pipeline/step/SourceIngest.java:71-73`).
- Krok, który się nie powiódł, rzuca wyjątek, a ten kończy przebieg jako `FAILED` z tekstem błędu (`RunRecorder.step`, `RunRecorder.java:146-162`).

  Wniosek [I]: wyjątek rzucony w kroku FETCH przy `fetched − skipped = 0` dałby `FAILED`, powiadomienie, exit 1 crona i stojącą świeżość bez żadnej zmiany w cronie, SPA ani zapytaniu świeżości. Powód pokazałby `attentionReasons` dla `failed` → `run.error` (`web/runs.ts:59-60`). Tak nie da się jednak wyrazić „ostrzeżenia”, czyli części pominiętych przy dobrych postach.
- `summarize` dostaje tylko `AssessedBatch`. Reguła statusu oparta na `fetched` potrzebowałaby `FetchedBatch` z blackboardu (jest tylko w RANGE) albo odczytu przebiegu z bazy.

### 2. Liczniki przebiegu

- Kolumny `pipeline_runs`: `fetched, new_posts, assessed_ok, assessed_failed, processing_errors, dead, skipped` (`V3:18-25`) oraz `llm_retries, flex_fallbacks` (`V8:11-12`). Pola encji: `api/pipeline/run/PipelineRun.java:73-104`. Każdy zapis idzie przez `RunRecorder.count` (`RunRecorder.java:101-115`).
- Znaczenie (dla jedynego źródła, Wykopu; `api/sources/Source.java:9-11`):

| Licznik | Znaczenie | Gdzie ustawiany |
|---|---|---|
| `fetched` | elementy po deduplikacji `resource:id` + elementy o złym kształcie JSON (`WykopFetcher.java:55`) | `PipelineAgent.fetch` (`PipelineAgent.java:79-81`) |
| `skipped` | zły kształt JSON (`WykopRepository.java:132-135`) + `UnmappableItemException` (`WykopFetcher.java:46-54`) | j.w. |
| (posty do zapisu) | `fetched − skipped`; `items_in` kroku STORE, bez kolumny | `PipelineAgent.java:87` |
| `new_posts` | posty wstawione pierwszy raz w tym przebiegu (`api/pipeline/step/PostStore.java:56-77`) | `PipelineAgent.java:89` |
| duplikaty | brak licznika; da się je wyliczyć jako `fetched − skipped − new_posts` | — |

- `UnmappableItemException` obejmuje: brak id, `resource` inny niż `entry`/`link`, brak tekstu, brak lub zły `created_at` (`WykopPosts.java:45-47`, `WykopMapper.java:42-44,61-63,68-78`). Oba źródła `skipped` sumują się w jedną liczbę, bez rozbicia na przyczyny. Pakiet `sources/` niczego tu nie loguje.
- Kod nie sprawdza po swojej stronie elementów spoza okna dat. Ufa `date_from`/`date_to` API, więc taki element zostaje zapisany, nie pominięty.
- Liczniki są sumowane po źródłach (`SourceIngest.java:66-80`). Liczniki per źródło ani per dzień nie istnieją: Wykop przeszukuje D-2…D-1 jednym zapytaniem (`WykopFetcher.java:43-44`).
- Tylko RANGE pobiera dane. RETRY i POSTS idą wprost do SELECT z `fetched = 0` z założenia (`PipelineAgent.route`, `PipelineAgent.java:68-75`). Reguła „pusty przebieg” musi więc dotyczyć tylko `kind = RANGE`.
- Źródło nieskonfigurowane przy starcie: `SourceIngest.resolve` rzuca `SourceNotConfiguredException` (503 `source_not_configured`) i przebieg w ogóle nie powstaje (`SourceIngest.java:38-50`). Cron kończy wtedy exit 1 (`trigger.sh:176-177`). Ten przypadek już daje sygnał.

### 3. `needsAttention` i powiadomienia

- `PipelineRun.needsAttention` (`api/pipeline/run/PipelineRun.java:278-289`):
  - `FAILED/INTERRUPTED` → `true`;
  - `PARTIAL` → `dead > 0 || processingErrors > 0`;
  - `QUEUED/RUNNING/SUCCEEDED` → `false`.

  Wartość jest booleanem; backend nie ma enumu powodów.
- Javadoc (`PipelineRun.java:272-276`) nazywa to regułą „dla … crona”, ale cron patrzy tylko na `status` (`trigger.sh:142-151`). `partial` z martwym postem daje dziś powiadomienie i jednocześnie exit 0. To rozjazd dokumentacji z kodem. S-08 albo S-12 (A5: „`needsAttention` zgodne z wynikiem”) musi go rozstrzygnąć.
- `RunNotifications.runEnded` (`api/notifications/RunNotifications.java:41-56`) przy `needsAttention()` wstawia po jednym wierszu dla każdego admina z `login_enabled`, z `ON CONFLICT (recipient_id, kind, run_id) DO NOTHING` (unikalność z V9). Wywoływane z 4 miejsc: `PipelineRunService.java:245, 284, 338`, `PipelineStartupRecovery.java:103`.
- Jedyny rodzaj powiadomienia to `NotificationKind.RUN_ATTENTION` (`api/notifications/NotificationKind.java:12`), z regułą `CHECK` w V9. Nowy powód w ramach `RUN_ATTENTION` nie wymaga migracji; nowy rodzaj powiadomienia wymaga.
- Wniosek: zmiana samego `needsAttention` (albo statusu) wystarczy, żeby powiadomienie powstało. Dzwonek pokaże je jednak z zieloną plakietką „zakończony”, jeśli status zostanie `succeeded` (zob. §6).

### 4. Cron `tools/pipeline-trigger/trigger.sh` i `/internal`

- `POST /internal/pipeline-runs/daily` tworzy jeden przebieg RANGE D-2 00:00…D-1 23:59:59 (Warszawa) (`InternalPipelineRunController.java:68`, `PipelineRunService.java:147-153`). Gdy istnieją leftovery, wcześniej powstaje RETRY. Cron czeka na wszystkie przebiegi.
- `GET /internal/pipeline-runs/{id}` zwraca `RunState` (`InternalPipelineRunController.java:103-124`): `id, kind, status, startedAt, finishedAt, lastActivityAt, interruptionCause, step, assessTotal, fetched, newPosts, assessedOk, assessedFailed, processingErrors, dead`. **Bez `skipped` i bez `needsAttention`.** Zbiór kluczy przypina `containsOnlyKeys` w `test/pipeline/api/InternalPipelineRunIT.java:221`.
- `classify()` (`trigger.sh:142-151`):
  - `succeeded | partial` → ok;
  - `failed`, `interrupted` → porażka;
  - `queued` → czeka, `running` → trwa;
  - **inny status → `return 1`, czyli „unexpected status”, exit 1.** Nowy status trzeba dopisać tutaj.
- Linia per przebieg (`trigger.sh:259`) czyta pola jq bez `// "?"`, więc brakujące pole wypisze `null`. Linia `progress()` ma fallbacki dla starszego API.
- Wyjście: `fail "a run did not succeed: …"` (exit 1, `:280`) albo `OK: …` (exit 0, `:282`). Kody wyjścia opisuje `deploy-plan.md:449`. Exit ≠ 0 daje `CRASHED`, a o nim informuje tylko reguła konta Railway „deployment crashed” (`deploy-plan.md:450`).
- Atrapa API w `tools/pipeline-trigger/test.sh:44-135` (Python, `STUB_CASE`):
  - status może być słownikiem nadpisującym pola treści (`:111-113,124`);
  - domyślna treść jest na `:116-123`;
  - `old_api` usuwa nowsze pola (`:125-127`).

  Testy przypinają dosłowne linie logu (`:219, 235, 261` dla liczników; `:220, 225, 228, 232, 236` dla OK/FAILED), więc zmiana formatu linii zmienia te asercje. CI uruchamia sam `test.sh` (`.github/workflows/ci.yml:193-203`).
- Dokumentacja kontraktu: `tools/pipeline-trigger/README.md` (przepływ `:19-32`, tabela wyjść `:36-42`, przykładowe logi `:48-67`), `deploy-plan.md:443` (lista pól `RunState`), `REPOSITORY_GUIDELINES.md` (Deployment, „Daily run”).

### 5. Świeżość `/api/freshness`

- `FreshnessController` (`api/pipeline/api/FreshnessController.java:31-42`) zwraca `{lastUpdatedAt}`.
- `PipelineRunRepository.findLastUpdate` (`api/pipeline/run/PipelineRunRepository.java:39-48`): `kind = RANGE`, `status IN (SUCCEEDED, PARTIAL)`, `finishedAt is not null`, `order by r.rangeTo desc, r.finishedAt desc limit 1`. Kolumny `fetched` i `skipped` są dostępne w tym samym wierszu.
- Wykluczenie pustego przebiegu da się zrobić na dwa sposoby: predykatem (np. `and r.fetched > r.skipped`) albo nowym statusem spoza listy. Przy porządku `rangeTo desc` wykluczony przebieg niczego nie przesłania: etykieta wraca do ostatniego niepustego przebiegu.
- `test/pipeline/api/FreshnessIT.java:184-195`: fixture zapisuje `PipelineRun.range(...)` i aktualizuje tylko `status` i `finished_at`, więc `fetched = 0` w każdym przypadku. Predykat po licznikach złamie każdy pozytywny przypadek, jeśli fixture nie dostanie `fetched`.
- PRD FR-030: „nigdy przebiegu zakończonego błędem” (`prd.md:218-220`). Doprecyzowanie S-05: „`partial` się liczy, bo przebieg pobrał dane” (`prd.md:225`). To wspiera wykluczenie pustego przebiegu i wymaga dopisania doprecyzowania.
- Widok: `web/views/FeedView.vue:172-178` (trzy stany etykiety). Zmiana po samej stronie backendu nie wymaga zmian w SPA.

### 6. SPA (admin, strona przebiegu, dzwonek)

- `RunView` admina (`api/pipeline/api/PipelineRunController.java:177-193`) ma już `fetched`, `skipped`, `needsAttention` i kroki z `itemsIn/itemsOut`; lustro TS: `web/api/pipeline.ts:26-61`. `PipelineRunControllerIT` sprawdza wartości `needsAttention` (`:175, 294, 321, 348`), ale nie przypina zbioru kluczy.
- Lista `/admin` bierze plakietkę „wymaga uwagi” wprost z `run.needsAttention` (`web/views/AdminView.vue:300`), więc działa bez zmian. Liczniki z „pominięte” są w `AdminView.vue:182` i `RunDetailView.vue:74`.
- `attentionReasons` (`web/runs.ts:57-72`) buduje powody po stronie klienta, jako polskie teksty bez kluczy:
  - `failed` → `run.error`;
  - `interrupted` → przyczyna;
  - `partial` → martwe posty i błędy techniczne;
  - `default` → `[]`.

  Typ wejścia `AttentionFields` (`:11`) wybiera tylko `dead, processingErrors, error`. Nowy powód przy statusie `succeeded`/`partial` wymaga zmiany tej funkcji; inaczej strona przebiegu pokaże „Wymaga uwagi:” z pustym tekstem (`RunDetailView.vue:171-172`). Testy: `web/runs.test.ts:107-145`.
- Dzwonek (`web/components/NotificationMenu.vue:162-171`) pokazuje tylko plakietkę `runTone`/`runStatusLabel`, rodzaj i czas, bez powodów. `NotifiedRun` (`web/api/notifications.ts:9-19`, lustro backendu `NotificationController.java:96-131`) nie ma `fetched`/`skipped`. `NotificationsIT.java:139,145-146` przypina klucze elementu i `run`.
- Nowy `RunStatus` dotyka:
  - `web/api/pipeline.ts:6`;
  - `statusTone` (`Record<RunStatus,…>`, `web/runs.ts:13-20`; typecheck wymusi);
  - `web/labels.ts:47-54`;
  - kitchen sinka: `badges` `KitchenSinkView.vue:59-68` i powiadomień `:73-117`, które według komentarza zawierają „every reason the API notifies about”;
  - `dev/mock-api.js`: `nightly()` `:77-88`, `allRuns()` `:89-119`, `notifiedRun()` `:124-126`.

### 7. Wolumeny i realność pustego dnia

| Przebieg RANGE (okno 2 dni) | Pobrane / nowe | Źródło |
|---|---|---|
| PR env, 2026-10-12 (prawdopodobnie sob–nd, wnioskowane z daty) | 74 / — | `context/deployment/deploy-plan.md:489` |
| PR env, 11–12 X (nd–pn) | 248 / — | `deploy-plan.md:499` |
| produkcja, noc S-05 | 469 / 469 | `deploy-plan.md:517` |
| produkcja | 470 / 251 | `deploy-plan.md:753` |
| produkcja, noc 2026-10-18 | 425 / 71 | `deploy-plan.md:812` |
| PR env, zakres 14–15.10 (śr–czw) | 864 / 864 | `deploy-plan.md:788-794` |

- Pojedyncze dni: 262 wpisy i 4 linki 2026-10-01 (`context/archive/2026-10-06-first-assessed-wykop-feed/research.md:61`); 156 elementów w poniedziałek 2026-09-28 (`context/archive/2026-09-27-assessment-contract-harness/frame.md:187`); „~270 postów dziennie” (`deploy-plan.md:228,455`).
- W `context/` nie ma obserwacji `fetched = 0` ani `skipped > 0`. Sprawdzony zakres: `context/archive/**`, `context/deployment/deploy-plan.md`, `context/foundation/*`. Fixture'y Wykopu (`szczur-radar-api/src/test/resources/fixtures/wykop/`) nie mają prawdziwego nagrania pustej strony; puste odpowiedzi w IT są syntetyczne.

### 8. Granice z innymi slice'ami

- **S-16 `wykop-ingest-completeness`** (`roadmap.md:248-259`): pusta strona przy `received < total` i duplikaty zawyżające `received` (`WykopRepository.java:127,141-142`) oraz ponowienia 429/5xx (B6).
  - S-08 cytuje B5 w PRD refs (`roadmap.md:151`). Raport jest jednak jednoznaczny: zgubione przy stronicowaniu elementy nie wchodzą do `fetched`, więc S-08 ich nie wykryje.
  - Część B5 wspólna z S-08 to tylko to, że `skipped` istnieje i milczy.
- **S-12 `reliable-run-notifications`** (`roadmap.md:198-208`): utrata powiadomienia przy chwilowym błędzie bazy (A3), `needsAttention` zgodne z wynikiem (A5), krok oceny `succeeded` w przebiegu `failed` (A6). Roadmapa: S-12 idzie „po S-08, bo ten dodaje nowy powód powiadomienia i zmienia `needsAttention`” (`roadmap.md:207`).
- **Poza S-08:** kanał poza aplikacją (F-04), brakująca noc i dziury w dniach (S-13: C2, C3), fałszywy alarm 90 min crona (C8, Parked, `roadmap.md:344`), świeżość per źródło (Parked, `roadmap.md:342`).

## Code References

- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/RunFlow.java:150-155` — reguła statusu, bez warunku na `fetched`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/assess/ConcurrentAssessmentDispatcher.java:122` — pusta lista postów → pusta partia → `SUCCEEDED`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/flow/PipelineAgent.java:68-75,79-81,87-89,121-128` — routing rodzajów, liczniki fetch/store, summarize
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopFetcher.java:40-56` — `fetched` i `skipped`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:127-150` — `received`, zły kształt, koniec pętli (B5)
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java:272-289` — `needsAttention` i jego javadoc
- `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/RunNotifications.java:41-56` — bramka powiadomień
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunController.java:68,103-124` — okno dzienne, `RunState`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java:39-48` — zapytanie świeżości
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineRunService.java:306-310` — końcowa linia logu bez `skipped`
- `tools/pipeline-trigger/trigger.sh:142-151,259,280-282` — mapowanie statusów, linia per przebieg, wyjście
- `tools/pipeline-trigger/test.sh:44-135` — atrapa API
- `szczur-radar-web/src/runs.ts:11,57-72` — powody „wymaga uwagi”
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java:96,221-230` — pusty dzień = `succeeded`, zbiór kluczy
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java:89,294,321,348` — pusta atrapa Wykopu; asercje `needsAttention` zakończonych przebiegów do sprawdzenia
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/state/RunOutcomeTest.java:33-38` — `(0,0,0)` → `SUCCEEDED`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/run/PipelineRunTest.java` — `needsAttention` dla każdego statusu
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/FreshnessIT.java:184-195` — fixture bez `fetched`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/notifications/api/NotificationsIT.java:228` — fixture SQL przebiegu
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/PipelineAgentIT.java:201,306` — naturalne miejsce na test pustego/pominiętego zakresu end-to-end

## Architecture Insights

- **Status mierzy ocenę, a nie dane.** Cały łańcuch sygnałów (powiadomienie, cron, świeżość, UI) czyta status, więc najtańszy punkt zmiany to miejsce, w którym status powstaje albo w którym krok rzuca wyjątek. Dostępne kształty zmiany (wybór należy do planu):
  - **(a) Wyjątek w kroku FETCH przy `fetched − skipped = 0`.** Daje `FAILED` z tekstem błędu. Cron, SPA, świeżość i powiadomienia działają bez zmian; zmieniają się testy przypinające pusty dzień (`InternalPipelineRunIT`, `RunOutcomeTest` albo nowy test kroku FETCH) i doprecyzowanie PRD. Nie wyrazi ostrzeżenia przy części pominiętych.
  - **(b) Nowa gałąź statusu.** Na przykład `PARTIAL` przy `skipped > 0` albo `FAILED` przy pustym fetchu w `summarize`, z `FetchedBatch`. `needsAttention` i SPA (`attentionReasons`) dostają nowy powód.
  - **(c) Nowy status albo flaga przebiegu.** Migracja `CHECK`, `classify()` w cronie, typy i etykiety SPA, kitchen sink, mock; świeżość przez listę statusów albo predykat.
- **Reguła `needsAttention` jest jedna dla backendu, ale powody żyją tylko w SPA** (`web/runs.ts:57-72`). Każdy nowy powód wymaga zmian po obu stronach. Backendowy enum powodów nie istnieje.
- **Kontrakt cron ↔ `/internal` nie ma wspólnego schematu.** Przypina go `containsOnlyKeys` w IT i ręcznie utrzymywana atrapa w `test.sh`. Zmiana pola albo statusu musi trafić w trzy miejsca naraz: `RunState`, IT i atrapę z asercjami logu.
- **Zasada `sources/CLAUDE.md`: „nie gubić elementów po cichu”.** `skipped` liczy pominięte elementy, ale ani ich nie loguje, ani nie zachowuje RAW (`SourceFetcher.java:32-33`), więc diagnoza „który element i dlaczego” jest dziś niemożliwa. Rozbicie przyczyn wymagałoby zmiany portu `Fetched`, a to wykracza poza minimum S-08.

## Historical Context (from prior changes)

- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md:50`: `skipped` powstał, żeby jeden dziwny element nie przerywał pobrania. Werdykt: wspiera dzisiejszy kod. Plan S-02 (`plan.md:320`) umieszczał licznik w kroku STORE, a `follow-ups/review-fixes.md:122-123` przenosi go do FETCH. Kod potwierdza FETCH (`PipelineAgent.java:79-81`), więc zdanie z planu jest nieaktualne.
- `context/archive/2026-10-12-daily-scheduled-run-freshness/research.md:64-65`: „przebieg z zerową liczbą postów do oceny kończy się `SUCCEEDED`”. Werdykt: nadal prawda (`RunFlow.java:150-155`). Warunku na `fetched` nikt wtedy nie rozważał.
- `context/archive/2026-10-12-daily-scheduled-run-freshness/plan.md:312` i `deploy-plan.md:449`: exit 0 dla `succeeded`/`partial`. Werdykt: nadal prawda (`trigger.sh:144`).
- `context/archive/2026-10-15-pipeline-run-history-dashboard/plan.md:73` i `plan-brief.md:48`: decyzja użytkownika „kody wyjścia crona się nie zmieniają”, zapisana w PRD (`prd.md:260`). S-08 świadomie ją zmienia. Wymaga to nowego doprecyzowania PRD.
- `context/archive/2026-10-15-pipeline-run-history-dashboard/research.md:249` (D2): exit ≠ 0 przy `partial` ponad progiem rozważono i odrzucono, bo myliłoby „crashed” z „częściowo”. Ten argument dotyczy też ostrzeżenia przy części `skipped`.
- `context/archive/2026-10-15-pipeline-run-history-dashboard/plan-brief.md:25`: sygnał przy każdym `partial` byłby co noc (~2% `failed`). Nowy powód nie może więc zależeć od `assessedFailed`.
- `context/archive/2026-10-15-pipeline-run-history-dashboard/research.md:130`: `RunState` „bez `skipped`”. Powodu pominięcia nie zapisano; agent przeszukał archiwa S-02/S-05/S-06 i go nie znalazł.

## Related Research

- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` — audyt źródłowy (C1, B5, macierz obserwowalności `:113`).
- `context/map/repo-map.md:79,101,107` — capability daily-run, zależność silnik → powiadomienia, kontrakt crona testowany tylko na atrapie.

## Open Questions

Decyzje produktowe dla `/10x-plan` (nie fakty):
1. Co jest „pustym” przebiegiem: `fetched = 0`, `fetched = skipped` (oba: `fetched − skipped = 0`), czy także udział `skipped` powyżej progu? Dane wspierają traktowanie `fetched − skipped = 0` w RANGE jako błędu (§7). Dla `skipped > 0` przy dobrych postach brak jakichkolwiek obserwacji, więc próg byłby zgadywany.
2. Czy `skipped > 0` przy dobrych postach to ostrzeżenie (powiadomienie, exit 0, świeżość się przesuwa), czy błąd? Argument z S-06 D2 („crashed” ≠ „częściowo”) przemawia za ostrzeżeniem bez zmiany wyjścia.
3. Kształt zmiany: (a) wyjątek w FETCH, (b) gałąź statusu, (c) nowy status lub flaga (Architecture Insights).
4. Czy dodać `skipped` (i ewentualnie `needsAttention`) do `RunState` i linii logu crona oraz API? To nie jest konieczne dla sygnału przy wariancie (a), ale zamyka lukę „`skipped` tylko w UI” z C1.
5. Czy S-08 wyrównuje cron z `needsAttention` (`partial` z martwym postem → exit ≠ 0), czy zostawia to S-12 (A5)? Javadoc `PipelineRun.java:272-276` już dziś twierdzi, że reguła obejmuje crona.
6. Czy S-08 rozbija `skipped` na przyczyny albo loguje pominięte elementy (zmiana portu `Fetched`)? Zakres może urosnąć.

Fakty nierozstrzygnięte:
7. Jakie dokładnie kształty JSON dają `JacksonException` dla `WykopItem` w Jackson 3 (wszystkie pola są `@Nullable`, `ignoreUnknown=true`; `WykopItem.java:24-27`)? Literalny `null` w `data` albo w `tags` prawdopodobnie daje NPE i `FAILED` zamiast `skipped` (wnioskowanie, bez testu).
8. Czy wyszukiwarka Wykopu zwraca usunięte elementy i w jakim kształcie (audyt Q10; należy do S-16).
