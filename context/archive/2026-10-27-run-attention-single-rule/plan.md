# S-22: „wymaga uwagi” liczone jedną regułą — plan implementacji

## Overview

Reguła „wymaga uwagi” ma dziś dwa domy: API (`PipelineRun.needsAttention`, z którego biorą powiadomienia, alert `RUN_NEEDS_ATTENTION` i flaga `RunView.needsAttention`) i SPA (`attentionReasons` w `src/runs.ts`, które samo liczy powody z liczników). Ta zmiana zostawia jedną regułę w API: liczy ona listę **kodów powodów** (`AttentionReason`), a `needsAttention` jest tylko tym, że lista nie jest pusta. API wysyła kody w `RunView.attentionReasons`, a SPA zamienia je na tekst i nic nie liczy. Przy okazji SPA przestaje dawać ton `neutral` przebiegowi przerwanemu przez nowe wdrożenie, bo serwer uważa go za wymagający uwagi.

Slice S-22 roadmapy M-3 (cel `quality`): audyt po M-2 K1, destylacja R-22 / D-04 / Q-05, NFR `prd.md:259`.

## Current State Analysis

- **Reguła w API** — `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java:292-304`: `needsAttention()` → statyczne `needsAttention(status, dead, processingErrors, skipped)`: `FAILED`/`INTERRUPTED` → true; `PARTIAL` → `dead > 0 || processingErrors > 0 || skipped > 0`; `SUCCEEDED` → `skipped > 0`; `QUEUED`/`RUNNING` → false. Testy: `PipelineRunTest` (statyczna metoda, każdy status).
- **Użytkownicy reguły w API** — `notifications/RunNotifications.java:62` (powiadomienia + alert; alert ma własny detal z licznikami, `attentionDetail`), `pipeline/api/PipelineRunController.java:218-233` (`RunView.needsAttention`). Odpowiedź dzwonka `NotificationController.NotifiedRun` ma status, przyczynę i liczniki, bez flagi i bez powodów.
- **Kopia reguły w SPA** — `szczur-radar-web/src/runs.ts:74-93` `attentionReasons(run)` liczy teksty z `status`, `dead`, `processingErrors`, `skipped`, `error`, `interruptionCause`. Teksty pokazuje tylko strona przebiegu: `src/views/RunDetailView.vue:171-172` (`v-if="run.needsAttention"`, `Wymaga uwagi: {{ attentionReasons(run).join('; ') }}`). `/admin` pokazuje tylko badge z flagi serwera (`src/views/AdminView.vue:374`), dzwonek tylko ton i etykietę statusu (`src/components/NotificationMenu.vue:167`).
- **Rozjazd** — nowy powód po stronie API daje `needsAttention = true` przy pustej liście tekstów SPA, czyli „Wymaga uwagi: ” bez treści. Testy obu stron (`PipelineRunTest`, `RunNotificationsTest`, `runs.test.ts`) nie mają wspólnego przypadku.
- **Ton** — `runTone` (`src/runs.ts:28-32`) i `stepTone` (`src/runs.ts:45-48`) dają `neutral` dla `interrupted` z przyczyną `deploy` (decyzja S-06, `context/archive/2026-10-15-pipeline-run-history-dashboard/plan.md:509`). Serwer każdy `INTERRUPTED` uznaje za wymagający uwagi, więc w `/admin` neutralny badge stoi obok czerwonego „wymaga uwagi”. Testy przypinające `neutral`: `src/runs.test.ts:75-80, 90-98`, `src/views/AdminView.test.ts:594`, `src/views/RunDetailView.test.ts:267, 272`; kitchen sink `src/views/KitchenSinkView.vue:64`.

## Desired End State

- API: jedna reguła `PipelineRun.attentionReasons()` zwraca uporządkowaną listę `AttentionReason`; `needsAttention()` to `!attentionReasons().isEmpty()`. `RunView` ma pole `attentionReasons` (np. `["dead_posts","skipped"]`) obok niezmienionego `needsAttention`. Powiadomienia i alert zachowują się dokładnie jak dziś.
- SPA: `attentionReasons(run)` tylko zamienia kody z API na tekst (liczby z liczników przebiegu, tekst błędu z `run.error`, przyczyna z `run.interruptionCause`); nieznany kod pokazuje się tak, jak przyszedł; flaga `true` bez tekstów (np. stare API bez pola) daje tekst zastępczy. „Wymaga uwagi:” nigdy nie jest puste.
- SPA: przebieg przerwany przez wdrożenie ma ton `warning`, jak każde inne przerwanie; jego przerwany krok też.
- Weryfikacja: `./mvnw -B verify` i `npm test` / `npm run build` zielone; zrzuty `/admin` i strony przebiegu `r-deploy` z atrapy pokazują `warning`; w PR env przebieg zakresu bez postów pokazuje „Wymaga uwagi: NoPostsFetchedException: …”, a jego JSON ma `"attentionReasons":["failed"]`.

### Key Discoveries:

- Zamknięte zbiory API to enumy z `@JsonValue value()` — wzorzec `pipeline/run/InterruptionCause.java`. `AttentionReason` nie jest zapisywany w bazie: bez migracji, bez `CHECK`, bez konwertera w `persistence/converters/`, bez wpisu w `tools/db-backup/tables.sh`.
- SPA ma konwencję etykiet zamkniętych zbiorów API: nieznana wartość pokazuje się tak, jak przyszła (`src/labels.ts:1, 74-76`). Tekst powodów potrzebuje odmiany liczebników (`plural` z `src/labels.ts`), więc mapowanie zostaje w `src/runs.ts`, nie w `labels.ts`.
- API i web wdrażają się niezależnie (`REPOSITORY_GUIDELINES.md` — dwa moduły). Faza 1 dodaje pole (stary SPA je ignoruje), faza 2 znosi jego brak (nowy SPA przy starym API w oknie wdrożenia).
- Obraz web buduje się z samego `szczur-radar-web/` (`Dockerfile`: `COPY . ./`), a `vue-tsc` sprawdza też `*.test.ts` (`tsconfig.app.json` `include: src/**/*.ts`) — dlatego wybrane podejście nie wymaga wspólnego pliku przypadków między modułami.
- `stepTone(status, cause)` traci jedyne użycie `cause`; przy `noUnusedParameters` (`tsconfig.app.json`) parametr znika, a wywołania w `AdminView.vue:399` i `RunDetailView.vue:237` się upraszczają.

## What We're NOT Doing

- Nie zmieniamy, które przebiegi wymagają uwagi: tabela reguły przechodzi do kodów 1:1 (powiadomienia, alert `RUN_NEEDS_ATTENTION`, wyjątek przebiegów crona `failed`/`interrupted` — bez zmian; PRD bez doprecyzowania).
- Nie zmieniamy treści alertu w Telegramie (`RunNotifications.attentionDetail` zostaje: status, przyczyna, `dead`/`errors`/`skipped`, błąd, odnośnik) — decyzja wywiadu.
- Nie pokazujemy treści powodów w `/admin` ani w dzwonku; nie zmieniamy `NotifiedRun` ani `NotificationMenu` (poza tonem, który zmienia się sam przez `runTone`) — decyzja wywiadu.
- Nie zdejmujemy uwagi z przebiegu przerwanego przez wdrożenie (alternatywa „serwer: deploy bez uwagi” odrzucona w wywiadzie).
- Nie wysyłamy z API gotowych polskich zdań; nie budujemy wspólnego pliku przypadków API ↔ SPA ani generatora typów (pełny kontrakt odpowiedzi to S-24 `api-contract-guard`).
- Nie przechodzimy przez `/10x-ui`: jedyna zmiana wizualna to ton jednego wariantu badge'a, bez nowego komponentu, tokenu ani układu.
- Bez nowej sekcji w `context/deployment/deploy-plan.md`: brak zmiennych, migracji i kroków operatora; stan po merge'u w komentarzach issue S-22.
- Nie edytujemy `context/domain/` ani `context/changes/post-m2-audit/` (mapy z datą; D-04 zamknie następna destylacja).

## Implementation Approach

Najpierw API (faza 1): reguła zwraca kody zamiast wartości logicznej, flaga wynika z kodów, `RunView` dostaje listę. Faza jest zgodna wstecz i daje zielone `verify` sama. Potem SPA (faza 2): typ z opcjonalnym polem, `attentionReasons` jako czyste mapowanie kod → tekst z dwoma zabezpieczeniami (nieznany kod, brak tekstów przy fladze), ton `warning` dla każdego przerwania, testy, atrapa i dokumenty.

Kolejność kodów ustala API i SPA jej nie zmienia: `failed` albo `interrupted` same (przebieg `failed` z pominiętymi elementami ma tylko `failed`, jak dziś), a dla `partial` / `succeeded`: `dead_posts`, `processing_errors`, `skipped` — kolejność dzisiejszych tekstów SPA.

## Phase 1: API — powody jako kody z jednej reguły

### Overview

`PipelineRun` liczy listę `AttentionReason`; `needsAttention()` z niej wynika; `RunView` wysyła listę. Nic poza tym nie zmienia zachowania.

### Changes Required:

#### 1. Zamknięty zbiór powodów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/AttentionReason.java` (nowy)

**Intent**: Nazwać powody, dla których przebieg wymaga uwagi, jako zamknięty zbiór z wartością API — jedno słownictwo dla reguły, odpowiedzi i SPA.

**Contract**: `public enum AttentionReason` z `@JsonValue public String value()`, po wzorcu `InterruptionCause`: `FAILED("failed")`, `INTERRUPTED("interrupted")`, `DEAD_POSTS("dead_posts")`, `PROCESSING_ERRORS("processing_errors")`, `SKIPPED("skipped")`. Javadoc każdej stałej mówi, kiedy występuje; javadoc typu — że kolejność stałych to kolejność w odpowiedzi, a nowy powód dostaje też tekst w `szczur-radar-web/src/runs.ts` (bez niego SPA pokaże kod).

#### 2. Reguła zwraca kody

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java`

**Intent**: Przenieść „jedną regułę” z wartości logicznej na listę kodów, żeby flaga i powody nie mogły się rozjechać.

**Contract**:
- `public List<AttentionReason> attentionReasons()` — instancyjne, deleguje do statycznej, pakietowej `static List<AttentionReason> attentionReasons(RunStatus status, int dead, int processingErrors, int skipped)`: `FAILED` → `[FAILED]`; `INTERRUPTED` → `[INTERRUPTED]`; `PARTIAL` → w tej kolejności `DEAD_POSTS` gdy `dead > 0`, `PROCESSING_ERRORS` gdy `processingErrors > 0`, `SKIPPED` gdy `skipped > 0`; `SUCCEEDED` → `[SKIPPED]` gdy `skipped > 0`, inaczej `[]`; `QUEUED`/`RUNNING` → `[]`. Lista niemodyfikowalna.
- `public boolean needsAttention()` zostaje (używają go `RunNotifications`, `RunView`, `PipelineRunIT`) i zwraca `!attentionReasons().isEmpty()`. Statyczne `needsAttention(status, …)` znika (zastąpione przez statyczne `attentionReasons`).
- Javadoc „the one rule …” przechodzi na `attentionReasons()`; `needsAttention()` odsyła do niego. Zdanie o cronie (patrzy tylko na status) zostaje.

#### 3. Odpowiedź przebiegu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java`

**Intent**: Wysłać SPA powody, zamiast kazać mu je liczyć.

**Contract**: `RunView` dostaje komponent `List<AttentionReason> attentionReasons` zaraz po `boolean needsAttention`; `RunView.of` bierze `run.attentionReasons()`. Javadoc `@param attentionReasons` — „why it needs attention, in the rule's order ({@link PipelineRun#attentionReasons()}); empty when it does not”. Lista przebiegów i strona przebiegu dostają pole tą samą drogą (`RunView`).

#### 4. Testy

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/run/PipelineRunTest.java`

**Intent**: Przypiąć dokładną listę kodów dla każdego statusu i kolejność, w miejsce dzisiejszych asercji logicznych.

**Contract**: przypadki — `FAILED`/`INTERRUPTED` z licznikami 0 i 1 → tylko własny kod (przebieg `failed` z `dead`/`processingErrors`/`skipped` > 0 → `[FAILED]`); `PARTIAL`: każdy licznik osobno, wszystkie trzy → `[DEAD_POSTS, PROCESSING_ERRORS, SKIPPED]`, same zera → `[]`; `SUCCEEDED`: `skipped` → `[SKIPPED]`, `dead`/`processingErrors` bez `skipped` → `[]`; `QUEUED`/`RUNNING` z licznikami → `[]`; nowy przebieg (`PipelineRun.retry`) → `attentionReasons()` puste i `needsAttention()` false. Test `scheduled()` bez zmian.

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java`

**Intent**: Przypiąć pole w JSON obok istniejących asercji `needsAttention`.

**Contract**: przy każdej asercji `needsAttention` dopisać `attentionReasons`: `:175` (`$[0].attentionReasons` puste), `:294` (`$.items[*].attentionReasons` → `[["processing_errors"], []]`), `:321` (`$.attentionReasons` → `["interrupted"]`), `:348` (puste dla przebiegu `running`). W teście `:294` albo osobnym: przebieg `partial` z `dead`, `processing_errors` i `skipped` > 0 → `["dead_posts","processing_errors","skipped"]` (wartości z `value()`, kolejność reguły).

#### 5. Dokumentacja pakietu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`

**Intent**: Opisać nowe pole i miejsce reguły tam, gdzie agent go szuka.

**Contract**: drzewo `run/` — „PipelineRun (+ attentionReasons → needsAttention), AttentionReason”; wiersz `PipelineRunController` — `RunView` ma `needsAttention` i `attentionReasons` (kody w kolejności reguły; SPA zamienia je na tekst w `src/runs.ts`); punkt „Notifications” — `runEnded` powiadamia przebieg z niepustym `attentionReasons()` (`needsAttention()`); sekcja Tests — `run/PipelineRunTest` (`attentionReasons` dla każdego statusu, kolejność, puste dla nowego przebiegu).

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe reguły przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest=PipelineRunTest`
- Pełna bramka backendu przechodzi (w tym `PipelineRunControllerIT`, `NotificationsIT`, `RunNotificationsTest`, `PipelineRunIT`): `cd szczur-radar-api && ./mvnw -B verify`
- W kodzie produkcyjnym nie ma drugiej tabeli reguły: `rg -n "needsAttention\(RunStatus" szczur-radar-api/src` nie znajduje nic

**Implementation Note**: Faza nie ma kroków ręcznych; po zielonej bramce przejdź do fazy 2 (pole w JSON sprawdza faza 2 w PR env).

---

## Phase 2: SPA — tekst z kodów API, ton `warning` dla wdrożenia

### Overview

SPA bierze powody z `Run.attentionReasons`, zamienia je na tekst i nic nie liczy; „Wymaga uwagi:” nigdy nie jest puste; każde przerwanie ma ton `warning`. Atrapa, kitchen sink, testy i wytyczne idą za tym.

### Changes Required:

#### 1. Kontrakt odpowiedzi w SPA

**File**: `szczur-radar-web/src/api/pipeline.ts`

**Intent**: Opisać nowe pole odpowiedzi i jego zbiór wartości.

**Contract**: `export type AttentionReason = 'failed' | 'interrupted' | 'dead_posts' | 'processing_errors' | 'skipped'` (komentarz: `pipeline/run/AttentionReason`; API może dodać nowy kod, SPA pokazuje go wtedy jak jest). W `Run`: `attentionReasons?: AttentionReason[]` po `needsAttention` — opcjonalne, bo API sprzed S-22 go nie wysyła (okno wdrożenia: nowy web przy starym API). Komentarz przy `needsAttention`: „true exactly when `attentionReasons` is not empty (the API's one rule)”.

#### 2. Mapowanie kodów na tekst i ton

**File**: `szczur-radar-web/src/runs.ts`

**Intent**: Zamienić drugą kopię reguły na samo tłumaczenie kodów i zdjąć wyjątek tonu dla wdrożenia.

**Contract**:
- `attentionReasons(run)` — wejście: `Pick<Run, 'needsAttention' | 'attentionReasons' | 'interruptionCause' | 'dead' | 'processingErrors' | 'skipped' | 'error'>`; wynik: teksty w kolejności kodów z API (bez sortowania i bez patrzenia na `status`). Teksty jak dziś: `failed` → `run.error ?? 'przebieg zakończył się błędem'`; `interrupted` → `przerwany: <label('interruptionCause', …)>` albo `przerwany` bez przyczyny; `dead_posts` → `N post martwy / posty martwe / postów martwych`; `processing_errors` → `błędy techniczne: N`; `skipped` → `pominięte elementy: N`; nieznany kod → kod tak, jak przyszedł. Gdy `run.needsAttention` i żadnego tekstu (brak pola albo pusta lista) → jeden tekst zastępczy `sprawdź kroki i błąd przebiegu`; gdy `needsAttention` false → `[]`.
- `runTone`: `interrupted` zawsze `warning` (usunąć gałąź `deploy`); `stepTone(status: StepStatus)` bez parametru `cause` — `interrupted` zawsze `warning`.
- Komentarze: nagłówek pliku i javadoc `runTone`/`stepTone`/`attentionReasons` — powody liczy API (`pipeline/run/PipelineRun.attentionReasons`), tu tylko ich tekst; przerwanie przez wdrożenie wymaga uwagi (reguła API), więc ma ton ostrzeżenia. Typy pomocnicze `AttentionFields` / `hasSkipped` dostosować albo usunąć, jeśli przestaną być potrzebne (`hasSkipped` zostaje dla `runStatusLabel`).

#### 3. Widoki

**File**: `szczur-radar-web/src/views/AdminView.vue`, `szczur-radar-web/src/views/RunDetailView.vue`

**Intent**: Dostosować wywołania do nowej sygnatury `stepTone`; reszta widoków działa bez zmian.

**Contract**: `stepTone(step.status)` w `AdminView.vue:399` i `RunDetailView.vue:237`. Szablon „Wymaga uwagi” (`RunDetailView.vue:171-172`) zostaje: `v-if="run.needsAttention"` i `attentionReasons(run).join('; ')`.

#### 4. Kitchen sink i atrapa

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`

**Intent**: Kitchen sink pokazuje tony takie, jakie dają widoki.

**Contract**: w liście `badges` (`:58-67`) wpis `przerwany — nowe wdrożenie` ma ton `warning` (nie `neutral`). Powiadomienie `n2` (przerwanie przez wdrożenie) zmienia ton samo przez `runTone`.

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Atrapa odpowiada jak API po S-22, żeby zrzuty i ręczne sprawdzenia pokazywały powody z kodów.

**Contract**: `nightly()` ma domyślnie `attentionReasons: []`; przebiegi z `needsAttention: true` dostają kody zgodne z regułą: `r-skipped` → `['skipped']`, `r-deploy` i `r-lost` → `['interrupted']`, `r-empty` i `r-stalled` → `['failed']`, `r-dead` → `['dead_posts']`. Komentarz w nagłówku atrapy przy opisie przebiegów, jeśli wymienia pola.

#### 5. Testy Vitest

**File**: `szczur-radar-web/src/runs.test.ts`

**Intent**: Przypiąć tłumaczenie kodów, oba zabezpieczenia i nowy ton.

**Contract**: helper `run()` z `attentionReasons: []`; `attentionReasons`: każdy kod → swój tekst (z odmianą 1 / 3 / 5 dla `dead_posts`), kolejność z API zachowana (np. `['skipped', 'dead_posts']` → w tej kolejności), `failed` z błędem i bez, `interrupted` z przyczyną i bez, nieznany kod (`'stale_source'`) → `'stale_source'`, `needsAttention: true` bez pola i z pustą listą → `['sprawdź kroki i błąd przebiegu']`, przebieg `partial` z `dead: 1` ale bez kodów i `needsAttention: false` → `[]` (SPA nie liczy z liczników); `runTone`: `interrupted` z każdą przyczyną, `deploy` też → `warning`; `stepTone('interrupted')` → `warning`.

**File**: `szczur-radar-web/src/views/RunDetailView.test.ts`, `szczur-radar-web/src/views/AdminView.test.ts`

**Intent**: Dane testów jak z API po S-22 i nowy ton przerwania przez wdrożenie.

**Contract**: przebiegi z `needsAttention: true` w `RunDetailView.test.ts:240-305` dostają `attentionReasons` zgodne z regułą; `:267` i `:272` → `warning`; nowe przypadki: kod nieznany pokazany jak jest w „Wymaga uwagi: …” i przebieg z flagą bez pola → `Wymaga uwagi: sprawdź kroki i błąd przebiegu`. `AdminView.test.ts:594` → `warning` (badge „wymaga uwagi” bez zmian). `NotificationBell.test.ts` i `NotificationMenu`-owe testy sprawdzić pod kątem tonu przerwania przez wdrożenie i poprawić, jeśli go przypinają.

#### 6. Wytyczne repozytorium

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Wytyczna UI mówi prawdę o tym, skąd biorą się powody.

**Contract**: w punkcie o `src/components/` zdanie „A run's tone, status text with its interruption cause and "wymaga uwagi" reasons come from `src/runs.ts`…” → ton, tekst statusu i **tekst** powodów „wymaga uwagi” są w `src/runs.ts`; same powody (kody `attentionReasons`) liczy API (`pipeline/run/PipelineRun.attentionReasons`), SPA ich nie liczy; nowy kod dostaje tekst w `src/runs.ts`.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- SPA nie liczy już powodów z liczników ani nie ma wyjątku tonu dla wdrożenia: `rg -n "=== 'deploy'|run\.dead > 0|run\.processingErrors > 0" szczur-radar-web/src/runs.ts` nie znajduje nic

#### Manual Verification:

- Zrzuty z atrapy `/admin` i `/admin/runs/r-deploy` w obu motywach i przy 375 px pokazują ton ostrzeżenia dla przerwania przez wdrożenie
- PR env: przebieg zakresu bez postów pokazuje „Wymaga uwagi: NoPostsFetchedException: …”, a jego JSON ma `"attentionReasons":["failed"]`

Szczegóły obu kroków w „Manual Testing Steps”. Zrzuty robi agent (`dev/mock-api.js` + `playwright-cli`), ogląda człowiek; ze stanów 7-stanowej macierzy tylko `default`, reszta N/A z powodem (zmienia się sam ton jednego wariantu badge'a). W PR env admin uruchamia przebieg zakresu na dzisiejszy dzień (źródło bez postów → `failed`), a odpowiedź `GET /api/admin/pipeline-runs/<id>` ma też `"needsAttention":true`.

> Notatka z wykonania (2026-10-28): 2.5 sprawdzono lokalnie, nie w PR env. W PR env `szczur-radar-pr-128` nie było konta admina, a `railway ssh` (potrzebne do `create-admin`) było niedostępne po stronie Railway, a dla agenta zablokowane uprawnieniami. Sprawdzenie, za zgodą użytkownika: obraz API z `szczur-radar-api/Dockerfile` z commita `f077113`, Postgres 18, prawdziwe klucze Wykop i LLM, SPA przez proxy Vite. Przebieg zakresu `2004-01-01`, a nie dzisiejszego dnia, który o tej porze ma już posty: `failed`, `"needsAttention":true`, `"attentionReasons":["failed"]`, strona przebiegu „Wymaga uwagi: NoPostsFetchedException: wykop gave no post to store (fetched 0, skipped 0)”. Samo wdrożenie na Railway potwierdziło CI PR #128 (zielone wdrożenia API i web, `/api/ready` 200). Dowody w komentarzu PR #128.

**Implementation Note**: Po zielonej automatycznej weryfikacji zatrzymaj się na ręcznym potwierdzeniu zrzutów i sprawdzenia w PR env.

---

## Testing Strategy

### Unit Tests:

- `PipelineRunTest`: dokładna lista kodów i kolejność dla każdego statusu; `failed` z licznikami → tylko `failed`; `succeeded` z `dead`/`processingErrors` bez `skipped` → pusta; nowy przebieg → pusta lista i `needsAttention` false.
- `runs.test.ts`: tłumaczenie każdego kodu, kolejność z API, nieznany kod, brak pola / pusta lista przy fladze, brak liczenia z liczników, ton `warning` dla każdego przerwania.

### Integration Tests:

- `PipelineRunControllerIT`: `attentionReasons` w JSON listy i strony przebiegu obok `needsAttention` (puste, `["processing_errors"]`, `["interrupted"]`, `["dead_posts","processing_errors","skipped"]`).
- Bez zmian, ale w bramce: `NotificationsIT`, `RunNotificationsTest`, `PipelineRunIT:579` — potwierdzają, że powiadomienia i alert zachowują się jak przed zmianą.

### Manual Testing Steps:

1. `npm run dev` z atrapą, `/admin`: wiersz `r-deploy` ma badge ostrzeżenia obok „wymaga uwagi”; zrzut w obu motywach i przy 375 px.
2. `/admin/runs/r-deploy`: badge i krok `ocena` w tonie ostrzeżenia, „Wymaga uwagi: przerwany: nowe wdrożenie”.
3. PR env: przebieg zakresu na dziś → `failed`; strona przebiegu i JSON jak w kryterium fazy 2.

## Performance Considerations

Brak: lista co najwyżej pięciu kodów liczona z pól już wczytanego przebiegu.

## Migration Notes

Bez migracji bazy. Wdrożenie: API i web wychodzą z tego samego merge'a, ale w różnych chwilach. Stary SPA przy nowym API ignoruje `attentionReasons`; nowy SPA przy starym API widzi `needsAttention: true` bez pola i pokazuje tekst zastępczy zamiast pustego. Wycofanie: revert PR — żadna dana nie zależy od nowego pola.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-22)
- Audyt po M-2: `context/changes/post-m2-audit/research.md:118` (K1)
- Destylacja: `context/domain/domain-distillation.md` (R-22 `:172`, D-04 `:207`, Q-05 `:252`)
- Reguła API: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java:281-304`
- Powiadomienia i alert: `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/RunNotifications.java:59-114`
- `RunView`: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java:204-233`
- Wzorzec enumu z wartością API: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/InterruptionCause.java`
- Kopia reguły i ton w SPA: `szczur-radar-web/src/runs.ts:22-93`
- Konwencja etykiet: `szczur-radar-web/src/labels.ts:1-76`
- Pochodzenie tonu `neutral` dla wdrożenia: `context/archive/2026-10-15-pipeline-run-history-dashboard/plan.md:509`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: API — powody jako kody z jednej reguły

#### Automated

- [x] 1.1 Testy jednostkowe reguły przechodzą: `cd szczur-radar-api && ./mvnw -B test -Dtest=PipelineRunTest` — 45db4d3
- [x] 1.2 Pełna bramka backendu przechodzi (w tym `PipelineRunControllerIT`, `NotificationsIT`, `RunNotificationsTest`, `PipelineRunIT`): `cd szczur-radar-api && ./mvnw -B verify` — 45db4d3
- [x] 1.3 W kodzie produkcyjnym nie ma drugiej tabeli reguły: `rg -n "needsAttention\(RunStatus" szczur-radar-api/src` nie znajduje nic — 45db4d3

### Phase 2: SPA — tekst z kodów API, ton `warning` dla wdrożenia

#### Automated

- [x] 2.1 Testy web przechodzą: `cd szczur-radar-web && npm test` — 5d3297a
- [x] 2.2 Typecheck i build przechodzą: `cd szczur-radar-web && npm run build` — 5d3297a
- [x] 2.3 SPA nie liczy już powodów z liczników ani nie ma wyjątku tonu dla wdrożenia: `rg -n "=== 'deploy'|run\.dead > 0|run\.processingErrors > 0" szczur-radar-web/src/runs.ts` nie znajduje nic — 5d3297a

#### Manual

- [x] 2.4 Zrzuty z atrapy `/admin` i `/admin/runs/r-deploy` w obu motywach i przy 375 px pokazują ton ostrzeżenia dla przerwania przez wdrożenie — 6164ba3
- [x] 2.5 PR env: przebieg zakresu bez postów pokazuje „Wymaga uwagi: NoPostsFetchedException: …”, a jego JSON ma `"attentionReasons":["failed"]` — c042580
