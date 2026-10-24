# S-11: utknięty przebieg sam wraca do obiegu — plan implementacji

## Overview

Przebieg `queued`/`running`, którego nikt już nie wykonuje (instancja zniknęła bez `onClose`, przebieg zakolejkowany na starej instancji po odzyskiwaniu nowej, przebieg, którego `submit` nie trafił do wykonawcy, nieudane odzyskiwanie startowe), przestaje blokować nocny cron i bramkę deployu na obudzonym API. Żywa instancja sprząta go na żądanie — w `queue()`, pod blokadą doradczą — jako `interrupted` z nową przyczyną `lost`, a `/api/deploy-safety` traktuje go jako bezpieczny. Przy każdym żądaniu startu wracają też posty `assessing` po zakończonych przebiegach (skutek A4). Bez nowego ruchu do bazy w bezczynności, więc API dalej zasypia.

Problem (z `frame.md`): każda bramka (`queue()` → 409 dla crona i admina, `/api/deploy-safety` dla bramki deployu) ufa wierszowi `queued`/`running` bez sprawdzenia, czy ktoś go jeszcze wykonuje, a jedyny mechanizm, który taki wiersz usuwa, działa przy starcie JVM — którego te same bramki, odpytując, nie dopuszczają.

## Current State Analysis

Ścieżki względem `szczur-radar-api/src/main/java/pl/szczurradar/api/`, chyba że podano inaczej.

- **Jedyny uzdrowiciel to start.** `pipeline/PipelineStartupRecovery.java:76-105` — `interruptAll` + `releaseOrphans` w jednej transakcji, przed portem; błąd tylko logowany (`:94-97`). Pobudka po uśpieniu to nowy JVM, więc dziś zombie leczy się tylko, gdy API zaśnie (`context/deployment/deploy-plan.md:1291`).
- **Watchdog widzi tylko własne przebiegi** (`pipeline/RunWatchdog.java:29-32,91-93`; `PipelineRunService.java:116,193`) i celowo nie pyta bazy w bezczynności. `RunWatchdogIT:240-257` przypina „cudzy przebieg zostaje” — to zostaje prawdą, watchdog się nie zmienia.
- **Sygnał żywotności jest w bazie.** `RunRecorder.stall` (`pipeline/state/RunRecorder.java:199-211`) mierzy brak postępu `COALESCE(last_activity_at, started_at)` na zegarze bazy, bez warunku właściciela. Dla `queued` jest tylko `created_at`.
- **Bramki.** `queue()` (`PipelineRunService.java:175-185`): blokada doradcza, `existsActive` → `PipelineBusyException` → 409 `pipeline_busy` dla crona (`tools/pipeline-trigger/trigger.sh:236-245`, ponowienia co 60 s przez 60 min) i admina. `api/DeploySafetyController.java:44-50`: `existsActive` → `pipeline_running`/`pipeline_queued`; bramka deployu czeka do 90 min (`tools/deploy-gate/wait-safe.sh:47,104-121`). Ich polling nie pozwala API zasnąć.
- **Okna sieroty są realne na Railway** (frame): stara instancja dostaje SIGTERM dopiero, gdy nowa jest aktywna (~10 s po odzyskiwaniu nowej, deploy 2026-10-23); `drainingSeconds` = 0. Nieudane `recover()` wystąpiło na produkcji 2026-10-23 07:12:22 UTC (błąd hasła Postgresa).
- **A4.** `claim` (`pipeline/state/PostTransitions.java:72-75`) bez `FOR SHARE` może przejść po interrupt; post utyka `assessing`, gdy stara instancja zginie przed `record`/`release`. `releaseOrphans` (`:166-174`) ma jednego wołającego — start.
- **Przyczyna przerwania:** enum `pipeline/run/InterruptionCause.java`, `CHECK (interruption_cause IN ('deploy','restart','shutdown'))` w `V8__pipeline_run_progress.sql:15-16`, ostatnia migracja `V10`. SPA: `szczur-radar-web/src/api/pipeline.ts:11-14`, `src/labels.ts:55`, `src/runs.ts:23-28` (tylko `deploy` ma ton `neutral`). Cron loguje przyczynę bez znajomości wartości (`trigger.sh:300-302`).
- **Próg:** `PipelineStallSettings` (`pipeline/PipelineStallSettings.java:68`) buduje dziś tylko `RunWatchdog` (`RunWatchdog.java:58-61`), tylko w serwerze (`@ConditionalOnWebApplication`); komendy operatora go nie czytają.

## Desired End State

- Gdy ta instancja nie ma własnego przebiegu, a żaden przebieg `queued`/`running` nie miał aktywności przez `PIPELINE_STALL_TIMEOUT` (39 min na produkcji), pierwszy `POST /internal/pipeline-runs/daily` albo `POST /api/admin/pipeline-runs` oznacza je wszystkie `interrupted` z przyczyną `lost` (kroki `interrupted`), oddaje ich posty, powiadamia adminów i tworzy nowe przebiegi (202). Przebieg innej instancji z postępem w progu — bez zmian, 409.
- Każde żądanie startu (także zakończone 409 lub 400) oddaje posty `assessing`, których przebieg nie jest `queued`/`running`.
- `GET /api/deploy-safety` odpowiada `{"safe":true,"reason":null}`, gdy żaden aktywny przebieg nie miał aktywności w progu; niczego nie zapisuje.
- SPA pokazuje przyczynę `lost` jako „utracona instancja” z tonem ostrzeżenia (`/admin`, strona przebiegu, dzwonek).
- Weryfikacja: testy IT na Postgresie (sieroty, granica progu, własny przebieg, A4, 409/400 z zatwierdzonym sprzątaniem), `DeploySafetyIT`, Vitest, scenariusz w PR env z sierotą wstawioną SQL-em.

### Key Discoveries:

- Wzorzec sprzątania istnieje: `interruptAll` + `releaseOrphans` w jednej transakcji, powiadomienia po commicie (`PipelineStartupRecovery.java:85-103`).
- Jedna instrukcja decyduje o bicie bezpieczeństwa (`DeploySafetyController.java:42-43` — „two statements would both miss a run…”); ta sama zasada dla predykatu sieroty.
- Każda zmiana stanu to jeden warunkowy `UPDATE` (`pipeline/CLAUDE.md`, Rules); wartości statusów z enumów, nigdy w SQL.
- Każdy nowy sposób zakończenia przebiegu woła `RunNotifications.runEnded` (`pipeline/CLAUDE.md`, Rules).
- `MigrationsIT:115-146` sprawdza `ck_pipeline_runs_interruption_cause` (odrzuca `crash`).
- PR env: SQL przez `railway ssh -s postgres -e <env> -- psql -U postgres -d railway -c …` (tunel `railway connect` zrywał się, `deploy-plan.md:1084`).

## What We're NOT Doing

- **Bez zamykania wyścigu `claim`** (`FOR SHARE` w `PostTransitions.claim`): regułę „no row locks” zostawiamy; rzadki dryf liczników przebiegu wobec `assessments` (A4) zostaje. Leczymy tylko widoczny skutek — post wraca do obiegu.
- **Bez zapytań do bazy w bezczynności:** `RunWatchdog` się nie zmienia; nie rozstrzygamy sprzeczności `RunWatchdog.java:30-32` z minutowym czyszczeniem sesji.
- **Bez kolumny dzierżawy/heartbeatu** ani id instancji — wystarcza `last_activity_at`/`started_at`/`created_at`.
- **Bez zmian w `trigger.sh` i `wait-safe.sh`:** obie już ponawiają dłużej niż próg.
- **Bez leczenia przez odczyty:** `/admin`, strona przebiegu, dzwonek i `/api/deploy-safety` niczego nie zapisują; zombie zostaje widoczny do najbliższego żądania startu albo deployu.
- **Bez ponawiania samego odzyskiwania startowego:** po nieudanym `recover()` przebiegi stają się sierotami po progu i sprząta je pierwsze żądanie startu.
- **Bez zatrzymywania po stronie właściciela,** gdy inna instancja sprzątnie jego żywy, zawieszony przebieg (tylko w oknie overlapu; właściciel i tak zaraz ginie).
- **Bez zmiany ustawień Railway** (`overlapSeconds`, `drainingSeconds`).

## Implementation Approach

Predykat „wszystko, co aktywne, jest martwe” liczony jedną instrukcją na zegarze bazy, z progiem `PIPELINE_STALL_TIMEOUT` (ta sama miara co watchdog, więc przebieg, którego własny watchdog by nie zatrzymał, nie zostanie sprzątnięty przez inną instancję). Sprząta się zbiorczo — wszystkie aktywne albo żaden — bo kolejka jest jedna i sekwencyjna: przebieg `queued` za martwym `running` jest tak samo osierocony, a żywy przebieg z postępem oznacza żywą kolejkę. Sprzątanie wykonuje tylko instancja bez własnych przebiegów (własne pilnuje jej watchdog). Wyzwalają je ci sami, którzy dziś dostają 409, więc nie dochodzi żaden ruch w bezczynności. `/api/deploy-safety` używa tego samego predykatu tylko do odczytu: deploy uruchamia nową instancję, której odzyskiwanie startowe i tak sprząta.

## Critical Implementation Details

- **Sprzątanie commituje niezależnie od odmowy.** Dziś `PipelineBusyException` (i `PostDeadException` w `startPosts`) wycofuje transakcję `queue()`. Przerwanie sierot i `releaseOrphans` muszą zostać zatwierdzone także wtedy, gdy żądanie kończy się 409 (świeży cudzy przebieg) albo 400 — np. osobna transakcja pod tą samą blokadą doradczą przed transakcją tworzącą albo transakcja zwracająca wynik zamiast rzucać. Test to przypina.
- **Kolejność:** blokada doradcza → przerwanie sierot (tylko przy pustym `own`) → `releaseOrphans` → `existsActive` → zapis nowych przebiegów; WARN i `runEnded` dopiero po commicie (powiadomienia czytają przerwane przebiegi).
- **Próg tylko w serwerze.** `PipelineStallSettings` ma być jednym obiektem serwera (np. bean warunkowy na aplikację webową, wstrzykiwany do `RunWatchdog`, `PipelineRunService` i `DeploySafetyController`), walidowanym przy starcie jak dziś (`InvalidSettingsException` → `APPLICATION FAILED TO START`). Kontekst komend operatora (`migrate`, `create-admin`…) nadal go nie czyta i nadal startuje.

## Phase 1: Sprzątanie sierot w kolejce

### Overview

Nowa przyczyna `lost` w schemacie i modelu; instrukcja przerywająca sieroty; `queue()` sprząta sieroty i osierocone posty przed decyzją o 409; testy na Postgresie.

### Changes Required:

#### 1. Migracja

**File**: `szczur-radar-api/src/main/resources/db/migration/V11__pipeline_run_interruption_lost.sql`

**Intent**: Dopuścić `lost` jako przyczynę przerwania (fix-forward, bez edycji `V8`).

**Contract**: `ck_pipeline_runs_interruption_cause` = `interruption_cause IN ('deploy','restart','shutdown','lost')`; `ck_pipeline_runs_interruption_status` bez zmian. Bez nowej tabeli, więc `tools/db-backup/tables.sh` bez zmian.

#### 2. Model przyczyny

**File**: `pipeline/run/InterruptionCause.java`

**Intent**: Nowa wartość dla przebiegu, który żywa instancja znalazła bez wykonawcy.

**Contract**: `LOST("lost", "interrupted: the instance running it was lost without closing it")` z Javadociem (sprzątnięty przez żądanie startu innej instancji po `PIPELINE_STALL_TIMEOUT` bez aktywności). Konwerter (`persistence/converters/InterruptionCauseConverter`) obsługuje go bez zmian.

#### 3. Instrukcja przerywająca sieroty

**File**: `pipeline/state/RunRecorder.java`

**Intent**: Jedna instrukcja, która — tylko gdy żaden przebieg `queued`/`running` nie miał aktywności przez `threshold` — oznacza je wszystkie `interrupted` (`lost`) i zamyka ich otwarte kroki jako `interrupted`, w jednej transakcji jak `interruptAll`.

**Contract**: `List<UUID> interruptOrphans(Duration threshold)`. `UPDATE pipeline_runs SET status = :interrupted, finished_at = now(), interruption_cause = :lost, error = :lostError WHERE status IN (:queued, :running) AND NOT EXISTS (SELECT 1 FROM pipeline_runs a WHERE a.status IN (:queued, :running) AND COALESCE(a.last_activity_at, a.started_at, a.created_at) >= now() - <threshold w ms jak w stall>) RETURNING id`, potem `closeSteps(…, StepStatus.INTERRUPTED, null)`. Wartości z enumów.

#### 4. Kolejka

**File**: `pipeline/PipelineRunService.java`

**Intent**: Przed decyzją o 409 sprzątnąć sieroty (gdy ta instancja nie ma własnych przebiegów) i oddać osierocone posty; zatwierdzić to także przy odmowie; po commicie zalogować i powiadomić.

**Contract**: w `queue()` pod `pg_advisory_xact_lock(LOCK_ID)`: `own.isEmpty()` → `recorder.interruptOrphans(stall.timeout())`; zawsze `transitions.releaseOrphans()`; potem jak dziś `existsActive` → `PipelineBusyException`. Po commicie: WARN `Marked {} orphaned pipeline run(s) interrupted: no queued or running run showed activity for {} and none runs on this instance: {}` (czas przez `Durations.format`), WARN `Put back {} post(s) left assessing by ended runs` przy `released > 0`, `notifications.runEnded` dla każdego przerwanego. Javadoc klasy i `queue()` opisuje regułę. Próg z serwerowego `PipelineStallSettings` (Critical Implementation Details).

#### 5. Próg jako obiekt serwera

**File**: `pipeline/RunWatchdog.java`, `pipeline/PipelineStallSettings.java` (lub klasa konfiguracji w `pipeline/`)

**Intent**: Jeden walidowany `PipelineStallSettings` w serwerze, wspólny dla watchdoga, kolejki i bramki.

**Contract**: `RunWatchdog` dostaje gotowe ustawienia zamiast budować je z `Environment`; zachowanie walidacji i komunikatów startu bez zmian (`PipelineStallSettingsTest`, testy startu z błędną wartością zielone); `PipelineRunService` przyjmuje ustawienia tak, by kontekst bez serwera WWW nie wymagał ani nie czytał `PIPELINE_STALL_TIMEOUT`.

#### 6. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/OrphanedRunIT.java` (nowy; Postgres, `PIPELINE_STALL_TIMEOUT` krótki, np. `5s`), `MigrationsIT.java`

**Intent**: Przypiąć regułę na bazie, z wierszami „innej instancji” wstawianymi SQL-em jak w `RunWatchdogIT:242-257`.

**Contract**, scenariusze:
- przebieg `running` z `deployment_id='deployment-other'`, aktywność sprzed godziny, otwarty krok, post `assessing` przez niego trzymany, plus przebieg `queued` za nim → `POST /internal/pipeline-runs/daily` daje 202; oba stare `interrupted` z `lost` i błędem z enumu, krok `interrupted`, post z powrotem `stored`, admin powiadomiony (2 powiadomienia), linia WARN w logu, nowe przebiegi utworzone;
- sam osierocony `queued` (przypadek A8) → sprzątnięty, 202;
- przebieg `running` z aktywnością w progu (`now()`) → 409, wiersz bez zmian;
- martwy `running` + świeży `queued` → 409, nic nie sprzątnięte (wszystko albo nic);
- granica: aktywność starsza niż próg o mniej niż próg (np. `now() - 2s` przy `5s`) → nie sprzątnięty;
- własny przebieg tej instancji w toku (LLM trzymany na zatrzasku, jak w `RunWatchdogIT`) + martwy cudzy wiersz → 409, cudzy bez zmian;
- A4: post `assessing` przy przebiegu `interrupted`, a obok świeży cudzy `running` → żądanie dostaje 409, a post i tak wraca (`stored`) — sprzątanie zatwierdzone mimo odmowy; to samo dla 400 `post_dead` z `startPosts`;
- `POST /api/admin/pipeline-runs` (sesja admina) sprząta tak samo (jeden przypadek);
- `MigrationsIT`: `lost` przyjęte przy `status='interrupted'`, `crash` nadal odrzucone.

### Success Criteria:

#### Automated Verification:

- Migracje przechodzą, a `MigrationsIT` przyjmuje `lost` i nadal odrzuca nieznaną przyczynę: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=MigrationsIT`
- `OrphanedRunIT` zielony we wszystkich scenariuszach: `./mvnw -B verify -Dit.test=OrphanedRunIT`
- Istniejące testy odzyskiwania i watchdoga bez zmian zielone (`PipelineStartupRecoveryIT`, `RunWatchdogIT` z `anotherInstancesRunIsLeftAlone`, `RunWatchdogTest`, `PipelineAgentIT`, `InternalPipelineRunIT`, `PipelineStallSettingsTest`): `./mvnw -B verify`
- `JpaSchemaIT` zielony (encje zgodne ze schematem po `V11`): w ramach `./mvnw -B verify`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Bramka deployu ignoruje martwe przebiegi

### Overview

`/api/deploy-safety` odpowiada `safe:true`, gdy wszystko, co aktywne, jest martwe według tego samego progu; bez zapisów.

### Changes Required:

#### 1. Zapytanie o żywe aktywne przebiegi

**File**: `pipeline/run/PipelineRunRepository.java`

**Intent**: Jedno zapytanie o to, czy istnieje przebieg `queued`/`running` z aktywnością w progu — to samo kryterium, które w `interruptOrphans` blokuje sprzątanie.

**Contract**: `boolean existsLiveActive(Duration threshold)` (natywne zapytanie z `COALESCE(last_activity_at, started_at, created_at) >= now() - …`, statusy z enumów); `existsActive` zostaje dla `queue()`.

#### 2. Kontroler

**File**: `pipeline/api/DeploySafetyController.java`

**Intent**: Bit bezpieczeństwa decyduje `existsLiveActive` jedną instrukcją; powód jak dziś (`pipeline_running`, gdy istnieje przebieg `running`, inaczej `pipeline_queued`).

**Contract**: odpowiedź `{safe, reason}` bez zmian kształtu; 503 bez bazy jak dziś; Javadoc i komentarz opisują martwe przebiegi; próg z serwerowego `PipelineStallSettings`.

#### 3. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/DeploySafetyIT.java`

**Intent**: Przypiąć nowe odpowiedzi obok istniejących.

**Contract**: martwy `running` → `{"safe":true,"reason":null}` i wiersz bez zmian (brak zapisu); martwy `queued` → safe; świeży `running` → `pipeline_running`; świeży `queued` + martwy `running` → `safe:false`; istniejące przypadki (running wyprzedza queued, zakończone bezpieczne, bez sesji i ciasteczka, 503) zielone.

### Success Criteria:

#### Automated Verification:

- `DeploySafetyIT` zielony z nowymi przypadkami: `./mvnw -B verify -Dit.test=DeploySafetyIT`
- Test bramki deployu bez zmian zielony: `tools/deploy-gate/test.sh`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: SPA zna przyczynę `lost`

### Overview

Typ, etykieta i ton nowej przyczyny w SPA; wariant na kitchen sinku i w mocku.

### Changes Required:

#### 1. Typ i etykieta

**File**: `szczur-radar-web/src/api/pipeline.ts`, `szczur-radar-web/src/labels.ts`

**Intent**: SPA przyjmuje `lost` i pokazuje go po polsku.

**Contract**: `InterruptionCause = 'deploy' | 'restart' | 'shutdown' | 'lost'` (komentarz typu uzupełniony); `labels.interruptionCause.lost = 'utracona instancja'`.

#### 2. Ton

**File**: `szczur-radar-web/src/runs.ts`

**Intent**: `lost` to anomalia, nie oczekiwany deploy — zostaje ton `warning` (logika bez zmian), komentarz `runTone`/`stepTone` wymienia przypadek.

**Contract**: `runTone({status:'interrupted', interruptionCause:'lost'})` = `warning`; `stepTone('interrupted','lost')` = `warning`.

#### 3. Podgląd i mock

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`, `szczur-radar-web/dev/mock-api.js`

**Intent**: Przebieg przerwany z `lost` widoczny na kitchen sinku i w historii `/admin` mocka (obok `r-deploy`).

**Contract**: jeden dodatkowy wariant/wiersz; bez nowych komponentów i literałów stylu.

#### 4. Testy

**File**: `szczur-radar-web/src/runs.test.ts`, `szczur-radar-web/src/labels.test.ts`

**Intent**: Przypiąć ton i etykietę `lost`.

**Contract**: przypadki dla `runTone`, `stepTone` i etykiety.

### Success Criteria:

#### Automated Verification:

- Testy SPA zielone: `cd szczur-radar-web && npm test`
- Typecheck i build: `npm run build`

#### Manual Verification:

- Agent: zrzuty `/admin` (mock) i `/kitchen-sink` z przebiegiem `lost` w obu motywach i przy 375 px pokazują „utracona instancja” w tonie ostrzeżenia (`dev/mock-api.js` + `playwright-cli`); pozostałe stany macierzy N/A — zmienia się tylko tekst istniejącego `StatusBadge`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Dokumenty i weryfikacja w PR env

### Overview

Dokumenty operatora i agenta mówią o nowej regule; scenariusz z sierotą wstawioną SQL-em w PR env.

### Changes Required:

#### 1. Dokument pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: Opisać sprzątanie sierot na żądanie.

**Contract**: w „Interruption cause” — `lost`; w Rules — reguła „No run stays queued/running without a thread” uzupełniona o sprzątanie w `queue()` (predykat, pusty `own`, wszystko albo nic, zatwierdzone mimo 409/400) i `releaseOrphans` przy każdym żądaniu startu; w „Notifications” — piąte miejsce `runEnded`; w tabeli Entries — `DeploySafetyController` ignoruje martwe przebiegi; w Tests — `OrphanedRunIT` i nowe przypadki `DeploySafetyIT`.

#### 2. Plan wdrożeń i wytyczne

**File**: `context/deployment/deploy-plan.md`, `REPOSITORY_GUIDELINES.md`

**Intent**: Runbook zombie mówi prawdę po S-11.

**Contract**: nowa sekcja S-11 ze statusem „**Status:** delivered by PR #N; production verification and closure in issue #70.” (lekcja o statusie w `deploy-plan.md`), regułą, logiem WARN i scenariuszem PR env; S-14 runbook krok 2 „A zombie run”: bramka przechodzi, gdy przebieg jest martwy dłużej niż `PIPELINE_STALL_TIMEOUT`, start przebiegu (cron/admin) go sprząta, restart API tylko wcześniej; wzmianka w S-06. `REPOSITORY_GUIDELINES.md`, punkt „Watchdog (since S-06)”: jedno zdanie o sprzątaniu sierot (`lost`) przy żądaniu startu i o `deploy-safety`.

#### 3. Weryfikacja w PR env

**Intent**: Pokazać regułę na prawdziwym Railway i Postgresie.

**Contract**: w PR env (`PIPELINE_STALL_TIMEOUT` domyślny 15 min) przez `railway ssh -s postgres -e <env> -- psql -U postgres -d railway -c …`: wstawić przebieg `running` (`deployment_id='deployment-other'`, `started_at`/`last_activity_at` = `now() - interval '1 hour'`, otwarty krok `assess`) i post `assessing` przez niego trzymany; `curl -s <web>/api/deploy-safety`; uruchomić przebieg (admin w `/admin` albo `POST /internal/pipeline-runs/daily` z tokenem z wnętrza sieci prywatnej, jak `trigger.sh`); sprawdzić SQL-em wiersz, krok i post; potem wiersz z `last_activity_at = now()` i ponownie `deploy-safety`; posprzątać wstawione wiersze.

### Success Criteria:

#### Automated Verification:

- Backend i SPA zielone po zmianach dokumentów: `cd szczur-radar-api && ./mvnw -B verify` oraz `cd szczur-radar-web && npm test`
- CI na PR zielone (wszystkie joby `ci.yml`, w tym `railway-config` i `api-container-smoke`)

#### Manual Verification:

- Agent: w PR env martwy wstawiony przebieg daje `{"safe":true,"reason":null}`, a świeży `{"safe":false,"reason":"pipeline_running"}` (wynik `curl` w komentarzu issue #70)
- Agent lub człowiek: start przebiegu w PR env przy martwym wierszu daje 202; SQL pokazuje stary przebieg `interrupted`/`lost`, krok `interrupted`, post `stored`; log API ma linię WARN `Marked 1 orphaned pipeline run(s) interrupted…`
- Człowiek: `/admin` i strona przebiegu w PR env pokazują „utracona instancja”, a dzwonek ma powiadomienie o tym przebiegu

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- Vitest: ton i etykieta `lost` (`runs.test.ts`, `labels.test.ts`).
- `PipelineStallSettingsTest` i `RunWatchdogTest` bez zmian zachowania (watchdog nadal nie pyta bazy bez własnego przebiegu).

### Integration Tests:

- `OrphanedRunIT` (Postgres): sieroty `running`+`queued` i sam `queued` sprzątnięte z `lost`, 202, posty z powrotem, powiadomienia, WARN; świeży cudzy → 409; wszystko albo nic; granica progu; własny przebieg wyłącza sprzątanie; A4 post wraca mimo 409 i 400; ścieżka admina.
- `DeploySafetyIT`: martwe → safe bez zapisu; świeże → powód jak dziś.
- `MigrationsIT`: `lost` przyjęte.

### Manual Testing Steps:

1. PR env: wstawić martwy przebieg SQL-em, `curl /api/deploy-safety` → `safe:true`.
2. Uruchomić przebieg → 202; SQL: stary `interrupted`/`lost`, post `stored`; log WARN.
3. Wstawić świeży przebieg → `deploy-safety` `pipeline_running`; posprzątać.
4. `/admin` i dzwonek pokazują „utracona instancja”.

## Performance Considerations

Dwie dodatkowe instrukcje w `queue()` (wywoływanym przez człowieka albo raz na noc przez cron, ponawiany co 60 s przy 409) i jedna zmieniona w `deploy-safety` (co 60 s tylko podczas bramki). `NOT EXISTS` i `releaseOrphans` czytają niewiele wierszy aktywnych przebiegów i postów `assessing`. Brak ruchu w bezczynności.

## Migration Notes

`V11` tylko rozszerza `CHECK`; istniejące wiersze go spełniają. Fix-forward: nie edytować `V8`. Starsza wersja API po wycofaniu nie zna `lost` — jej konwerter może nie odczytać takiego wiersza (niesprawdzone); wycofanie po pierwszym `lost` naprawia się nową poprawką do przodu, nie edycją `V11`.

## References

- Frame: `context/changes/stuck-run-self-heals/frame.md`
- Audyt: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (A2, A4, A8)
- Wzorzec: `pipeline/PipelineStartupRecovery.java:76-105`, `pipeline/state/RunRecorder.java:199-232`, `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/RunWatchdogIT.java:240-257`
- Roadmapa: `context/foundation/roadmap.md` (S-11), issue #70
- Lekcje: „Uzasadniaj zakres celem użytkownika”, „Planuj weryfikację agent-first”, „Status zmiany w deploy-plan.md ma być prawdziwy także po merge'u” (`context/foundation/lessons.md`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Sprzątanie sierot w kolejce

#### Automated

- [x] 1.1 Migracje przechodzą, a `MigrationsIT` przyjmuje `lost` i nadal odrzuca nieznaną przyczynę — ee0885e
- [x] 1.2 `OrphanedRunIT` zielony we wszystkich scenariuszach — ee0885e
- [x] 1.3 Istniejące testy odzyskiwania i watchdoga bez zmian zielone — ee0885e
- [x] 1.4 `JpaSchemaIT` zielony (encje zgodne ze schematem po `V11`) — ee0885e

### Phase 2: Bramka deployu ignoruje martwe przebiegi

#### Automated

- [x] 2.1 `DeploySafetyIT` zielony z nowymi przypadkami — f47fb4c
- [x] 2.2 Test bramki deployu bez zmian zielony — f47fb4c
- [x] 2.3 Cały backend zielony — f47fb4c

### Phase 3: SPA zna przyczynę `lost`

#### Automated

- [x] 3.1 Testy SPA zielone — 9b23757
- [x] 3.2 Typecheck i build — 9b23757

#### Manual

- [ ] 3.3 Agent: zrzuty `/admin` (mock) i `/kitchen-sink` z przebiegiem `lost` w obu motywach i przy 375 px

### Phase 4: Dokumenty i weryfikacja w PR env

#### Automated

- [x] 4.1 Backend i SPA zielone po zmianach dokumentów
- [ ] 4.2 CI na PR zielone

#### Manual

- [ ] 4.3 Agent: w PR env martwy przebieg daje `safe:true`, a świeży `pipeline_running`
- [ ] 4.4 Agent lub człowiek: start przebiegu w PR env przy martwym wierszu daje 202 i sprząta go jako `lost`
- [ ] 4.5 Człowiek: `/admin`, strona przebiegu i dzwonek w PR env pokazują „utracona instancja”
