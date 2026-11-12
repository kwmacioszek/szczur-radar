# S-43: atrapy odpowiadają jak prawdziwe API — plan implementacji

## Overview

Atrapa SPA (`szczur-radar-web/dev/mock-api.js`), atrapa API crona (`tools/pipeline-trigger/test.sh`) i stuby API bramki deployu (`tools/deploy-gate/test.sh`) mają odpowiadać tak jak prawdziwe API, a rozjazd ma czerwienić test. Robimy to przed M-5, bo M-5 postawi na atrapie SPA testy E2E (decyzja z 2026-11-04, `context/foundation/roadmap.md:391`). Bez tego E2E sprawdzałyby atrapę, nie API.

Ścieżki w planie: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `test/` = `szczur-radar-api/src/test/java/pl/szczurradar/api/`, `web/` = `szczur-radar-web/`, `mock` = `web/dev/mock-api.js`.

## Current State Analysis

Pełny obraz jest w `context/changes/contract-bound-mocks/research.md`. Najważniejsze:

- **Atrapa SPA nie ma strażnika kształtu.** Pilnują jej tylko dwa skany tekstu w `web/src/contract/rules.test.ts`: literał limitu strony (`:82-84`) i kody błędów (`:255-276`). Zmiana nazwy pola przechodzi (eksperyment X4).
- **Klucze i typy są dziś zgodne.** Drzewa kluczy wszystkich ciał są zgodne z plikami kontraktu. Typy wartości (także null) każdej ścieżki atrapy występują w plikach kontraktu tego samego rekordu: skrypt w scratchpadzie z 2026-11-11 nie znalazł luki dla RunView, PostItem, FailedPostPage, NotificationList, Sources, MissingDays, Freshness i User.
- **Zachowanie i dane mają 12 rozbieżności z API** (`research.md` §1.3):
  - źródła tylko Wykop (`mock:420`);
  - start przebiegu zawsze 409 (`mock:430`);
  - brak `/api/auth/login` (spada do 404 z `mock:504`);
  - id bez UUID i 404 zamiast 400;
  - lista nieudanych ignoruje `page`/`size` (`mock:455`);
  - `violations: []` (`mock:250-252`);
  - brak walidacji `size`/`page` (`mock:476` sprawdza tylko górną granicę strony);
  - restore przyjmuje id bez UUID;
  - tryb `anonymous` daje 401 tylko na `/me`;
  - brak roli `user`;
  - wynik źródła kończy się po kroku `fetch` (`mock:102` vs `:171`);
  - dni bez danych tylko z Wykopem (`mock:386`).

  Do tego `/raw` zwraca format Wykopu także dla postów z Reddita.
- **Atrapa jest skryptem `playwright-cli run-code`, bez `export`** (`mock:93`). Wykonany ma piaskownicę bez `require`, `import()` i `process`; jest tylko `fetch` (sprawdzone 2026-11-11 skryptem próbnym). Atrapa nie może więc sama czytać plików kontraktu. Vitest może jednak wykonać jej tekst z `?raw` (`rules.test.ts:2`) z podstawionymi obiektami `page`/`route`. Badacz potwierdził to w scratchpadzie; wykonanie nie ma skutków ubocznych.
- **Atrapa crona jest zgodna co do kluczy, ale niezwiązana z API.** Kopiuje ręcznie 16 pól `RunState`, kody błędów i tekst podsumowania nocy (`tools/pipeline-trigger/test.sh:76-186`). `classify` zna 6 statusów, a jego komentarz mówi, że są „named only here” (`tools/pipeline-trigger/trigger.sh:206-219`). Atrapa wysyła `assessTotal: 251` dla każdego stanu poza `queued` (`test.sh:181`), a API wysyła null przed krokiem oceny (`test/pipeline/api/InternalPipelineRunIT.java:262-263`). `nightly-summary` ignoruje ciało żądania (`test.sh:128-141`).
- **Stuby bramki to literały** (`tools/deploy-gate/test.sh:436-439`, `:610`). Stub `ready` wysyła `{"status":"ok"}`, a API `{"status":"ready"}` (`api/health/HealthController.java:43-53`).
- **Kontrakt `/internal/**`, `/api/deploy-safety` i `/api/ready` nie ma plików.** `ContractRoutesIT.java:41` filtruje do `/api/`, a `NOT_READ_BY_SPA` (`ContractRegistry.java:63-72`) wymienia trasy bez plików. IT tych tras porównują literały: `InternalPipelineRunIT:235`, `:251`, `:352`, `:363-385`, `DeploySafetyIT:76`, `:83`, `:90`, `:221-223`, `HealthIT:36-37`, `:68-69`.

## Desired End State

- `npm test` w `web/` zawiera strażnika atrapy SPA. Strażnik wykonuje `dev/mock-api.js` bez przeglądarki i:
  - na każdej trasie z tabeli porównuje ciało z plikami kontraktu (klucze ściśle, typy wartości z kontraktu, wartości enumów z `rules.json`);
  - sprawdza tabelę zachowań API (401/403/400/404/202/409/503 z kodami);
  - sprawdza trzy niezmienniki danych.

  Tabela tras obejmuje każdy plik kontraktu. Nowy plik bez trasy w atrapie czerwieni test.
- Atrapa SPA odpowiada jak API we wszystkich 12 punktach i dla `/raw` Reddita. Procedura zrzutów ekranu z jej nagłówka nadal działa. Nowe adresy z UUID są w nagłówku.
- `InternalPipelineRunIT`, `DeploySafetyIT` i `HealthIT` porównują prawdziwe odpowiedzi z plikami w `tools/pipeline-trigger/fixtures/api/` i `tools/deploy-gate/fixtures/api/`. Atrapy w `test.sh` budują z nich odpowiedzi. Zmiana kształtu w API czerwieni IT, a po poprawce pliku atrapa narzędzia dostaje nowy kształt bez ręcznej kopii.
- `tools/pipeline-trigger/test.sh` czerwieni się, gdy zbiór statusów w `classify` różni się od `rules.json` `values.runStatus`.

Weryfikacja: `npm test` i `npm run build` w `web/`, `./mvnw -B verify`, `tools/pipeline-trigger/test.sh`, `tools/deploy-gate/test.sh` oraz celowe zepsucia z kryteriów faz.

### Key Discoveries:

- `ContractFiles.assertMatches(json, fileName)` jest publiczne, ale katalog jest stałą (`test/contract/ContractFiles.java:23`, `:44-54`). Maskuje UUID i instanty rekursywnie (`:57-79`). Wzorzec ścieżki względem modułu do `../tools/…` już istnieje: `test/alerts/AlertSettingsTest.java:34` czyta `tools/healthchecks/fixtures/ping-urls.txt`.
- Ścisłe kody API potrzebne w tabeli zachowań:
  - anonim → 401 `not_authenticated` na każdej nie-publicznej trasie `/api/` (`api/auth/JsonAuthenticationEntryPoint.java:19`);
  - zwykły użytkownik na `/api/admin/**` → 403 `forbidden` (`api/config/SecurityConfig.java:101-107`, `JsonAccessDeniedHandler:41`);
  - trasy publiczne: `GET /api/health|ready|deploy-safety`, `POST /api/auth/login|logout|activation|activation/lookup` (`AuthorizationCompletenessIT.java:45-54`);
  - id bez UUID → 400 `invalid_request`; nieznany post → 404 `post_not_found` (`PostController.java:120-122`); nieznany przebieg i powiadomienie → 404 `not_found`;
  - stronicowanie: `page` 0…21474836, `size` 1…100, inaczej 400 `invalid_request`; domyślne `size` to 50 (posty), 20 (przebiegi), 50 (nieudane) (`PostController.java:88-89`, `PipelineRunController.java:65`, `:115-116`, `FailedPostController.java:54-55`);
  - logowanie: 200 z ciałem `user.json`, 401 `invalid_credentials`, 429 `too_many_attempts` z `Retry-After` (`AuthController.java:72-115`);
  - start: 202 `RunView[]` (zakres, a przed nim ponowienie, gdy są posty do ponowienia; albo `postIds` → przebieg `posts`), 400 `invalid_request` (`from > to`, `to` > dziś, zakres > 7 dni, mieszane pola, pusta lub > 100 lista `postIds`, nieznane źródło), 409 `pipeline_busy`, 503 `source_not_configured` (`PipelineRunController.java:100-111`, `:170-189`).
- `violations` nie bywa puste w API: `AssessmentResult.java:48-49` rzuca wyjątek. `provider_error` i `timeout` mają jedno naruszenie `{failureClass, rule:"call", path:"$", message}` (`Assessor.java:149-159`). Wzorce naruszeń są w `web/src/contract/failed-posts-page.json:13-71`.
- `/raw` Reddita to jeden element `data[]` z Arctic Shift (`RedditRepository.java:191`). Skrócony kształt można wziąć z `szczur-radar-api/src/test/resources/fixtures/reddit/search-posts-inwestowanie.json:3-112`.
- `InternalPipelineRunIT` ma już trzy stany `RunState`:
  - zakończony `failed` przed oceną (`:247-271`, `step` i `assessTotal` null);
  - `running` w kroku `assess` (`:275-297`);
  - `interrupted` `deploy` (`:299-307`).

  Stanu `queued` nie ma. Przebiegi wstawia się wprost przez `jdbc`.
- Wszystkie testy narzędzi działają na hoście po pełnym checkoucie (`.github/workflows/ci.yml:217-219`, `:232-234`). Atrapa crona to Python w kontenerze bez montowania, z kodem i przypadkiem przekazanymi przez env (`tools/pipeline-trigger/test.sh:209-212`), więc pliki trzeba jej przekazać tak samo.
- `tools/deploy-gate/fixtures/` trzyma 28 planów IaC czytanych przez `test.sh:46`, `:81-83`. Odpowiedzi API idą do podkatalogu `fixtures/api/`.
- Renderer roadmapy zna tylko statusy `done/ready/blocked/proposed/progress/parked` (`tools/render_roadmap_svg.py:33`, `:112`). S-43 jest w nim `proposed` (`:218`).

## What We're NOT Doing

- **Bez przepisywania atrapy SPA na typowany moduł ESM ani middleware Vite** (decyzja planu). Zostaje skryptem `run-code`, bo procedura zrzutów i `/10x-ui` się nie zmieniają. Typy atrapy pilnuje strażnik, nie kompilator.
- **Atrapa nie odpowiada wyłącznie z plików kontraktu.** Pliki niosą po jednym, dwóch wariantach na kształt, a atrapa potrzebuje ich dziesiątek do zrzutów (`research.md` §1.5).
- **Bez wspólnego pliku tras dla Javy i TS.** Tabela tras strażnika atrapy jest ręczna, ale musi pokryć każdy plik kontraktu. Nowa trasa, która używa już istniejącego pliku (jak logowanie i `/me` z `user.json`), nie czerwieni strażnika sama; dopisuje się ją do tabeli razem z wpisem w `ContractRegistry.SHAPES`.
- **Bez zasiewania nowych wariantów w IT dla atrapy SPA.** Dziś nie ma luki typów (Current State). Reguła „wariant atrapy musi być w kontrakcie” pilnuje przyszłych wariantów: brakujący wariant dopisuje się do pliku kontraktu przez IT, nie przez wyjątek w strażniku.
- **Plików kontraktu tras narzędzi nie ma w `web/src/contract/` ani w `ContractRegistry`.** Rejestr web zostaje tylko dla tras czytanych przez SPA, bez zmian w `ContractRoutesIT`, `ContractVariantsTest`, `shapes.test.ts` i bez wyjątku etykiet w `rules.test.ts:86-96`.
- **Powody `deploy-safety` nie trafiają do `rules.json`.** `wait-safe.sh` ich nie rozróżnia (`wait-safe.sh:91-95`), a pliki `fixtures/api/` niosą oba warianty.
- **Bez zmian w stubie CLI `railway`** (`tools/deploy-gate/test.sh:510-580`) i w planach IaC w `tools/deploy-gate/fixtures/*.json`. To kontrakt Railway, nie API.
- **Bez zmian w `trigger.sh` w działaniu.** Obraz crona i jego zachowanie zostają; zmienia się tylko komentarz przy `classify` i test.
- **Bez Playwrighta, `/10x-e2e-setup` i testów obecnych widoków.** To M-5 (decyzja z 2026-11-04).
- **Bez weryfikacji w Railway PR env.** Zmiana dotyczy testów, atrapy deweloperskiej i dokumentacji, a nie kodu wdrażanego na Railway. Wystarczy zielone CI.

## Implementation Approach

1. Najpierw strażnik kształtu (faza 1). Jest zielony od razu, bo klucze i typy się zgadzają, a celowe zepsucie X4 go czerwieni.
2. Potem poprawki zachowania i danych atrapy SPA z tabelą zachowań dopisaną do tego samego strażnika (faza 2). Każda rozbieżność dostaje wiersz, który byłby czerwony na dzisiejszej atrapie.
3. Na koniec atrapy narzędzi (fazy 3 i 4) według jednego wzoru:
   - IT Javy porównuje prawdziwą odpowiedź z plikiem w `tools/<narzędzie>/fixtures/api/` przez przeciążenie `ContractFiles.assertMatches(json, Path)` (precedens `ping-urls.txt`);
   - `test.sh` czyta te pliki na hoście i buduje z nich odpowiedzi atrapy, podstawiając w miejsce `<uuid>`/`<instant>` konkretne wartości;
   - zbiory wartości (statusy, kody błędów) sprawdza jq wobec `web/src/contract/rules.json`.

## Critical Implementation Details

- **Tryby atrapy w strażniku.** Tryb ustawia się podmianą linii `const MODE = '…'` (i pozostałych stałych z `mock:374-379` oraz nowych z fazy 2) w tekście przed wykonaniem. Pomocnik musi rzucić błąd, gdy linii stałej nie znajdzie; inaczej przemianowana stała cicho zostawi tryb domyślny.
- **Zawieszenia.** 60-sekundowe `setTimeout` (`mock:396`, `:478`, `:498`) testuje się na sztucznych zegarach Vitest. Sprawdzenie zawieszenia to „odpowiedź nie przyszła po 59 s, przyszła po 60 s”, nigdy prawdziwe czekanie.
- **Stan atrapy.** `readIds`, mutowana tablica `failed` oraz nowa sesja i nowe przebiegi z fazy 2 żyją na jedną instalację. Każdy test instaluje atrapę od nowa.
- **Wyjątek w `ContractFiles.mask`.** Atrapa crona musi odtworzyć wartości zamaskowane w plikach. Każdy `<uuid>` w pliku `RunState` to `id` przebiegu, a każdy `<instant>` to stały czas z atrapy. Pole, którego plik nie zna, jest dla atrapy błędem, a nie nowym kluczem; nadpisania przypadku nie mogą dodawać kluczy.

## Faza 1: Strażnik kształtu atrapy SPA

### Overview

Test Vitest wykonuje atrapę bez przeglądarki, odpytuje każdą trasę z tabeli i porównuje ciała z plikami kontraktu. Atrapa się nie zmienia, a test od razu jest zielony.

### Changes Required:

#### 1. Pomocnik uruchamiający atrapę

**File**: `szczur-radar-web/src/contract/mockApi.ts` (nowy; importują go tylko testy)

**Intent**: Wykonuje tekst `../../dev/mock-api.js?raw` (wyrażenie funkcji) z podstawionym `page` (`unrouteAll`, `route`) i przechwytuje handler trasy. Udostępnia wywołanie trasy, które zwraca status, typ treści i sparsowane ciało, oraz instalację z podmienionymi stałymi trybów.

**Contract**:
- `installMock(constants?: Partial<Record<MockConstant, string>>): Promise<MockApi>`;
- `MockApi.call(method, path, body?) → Promise<{ status: number; contentType: string | undefined; body: unknown }>`;
- `MockConstant` to unia nazw stałych z `mock:374-379` (faza 2 dopisuje nowe);
- brak linii stałej w tekście atrapy → wyjątek;
- `route.request()` udaje `url()`, `method()`, `postDataJSON()`.

#### 2. Strażnik kształtu

**File**: `szczur-radar-web/src/contract/mockApi.test.ts` (nowy)

**Intent**: Dla każdej trasy z tabeli tras porównuje odpowiedzi atrapy w trybie domyślnym z plikami kontraktu. Listy są pobierane strona po stronie, a pojedyncze przebiegi i posty dla każdego id z listy. Dzięki temu zmiana nazwy, usunięcie pola, nowy typ wartości albo nieistniejąca wartość enumu w atrapie czerwieni test.

**Contract**:
- **Tabela tras:** wpis = metoda, ścieżka (z ciałem dla POST), plik(i) kontraktu i korzeń rekordu (`$`, `$[]`, `$.items[]`). Rekord, który występuje w kilku plikach (RunView w `run.json`, `runs-page.json`, `started-runs.json`; PostItem w `post.json`, `posts-page.json`), jest porównywany z sumą tych plików.
- **Pokrycie:** zbiór plików wymienionych w tabeli = pliki zebrane przez `import.meta.glob('./*.json')` bez `rules.json`. W fazie 1 `started-runs.json` dostaje trasę dopiero w fazie 2, bo atrapa zwraca dziś 409; do tego czasu jest jedynym nazwanym wyjątkiem pokrycia z powodem, a faza 2 go usuwa.
- **Reguła kluczy:** każdy obiekt atrapy na ścieżce (z tablicami zwiniętymi do `[]`) ma dokładnie zbiór kluczy obiektów kontraktu na tej ścieżce.
- **Reguła typów:** typ JSON każdej wartości atrapy (`null`, `string`, `number`, `boolean`, `object`, `array`) występuje na tej ścieżce w którymś pliku rekordu. Brakujący wariant dopisuje się do pliku kontraktu przez IT, nie przez wyjątek w teście.
- **Reguła enumów:** jawna mapa ścieżka → zbiór `rules.json` `values` (m.in. `status` → `runStatus`, `kind` → `runKind`, `steps[].step` → `step`, `steps[].status` → `stepStatus`, `interruptionCause`, `sourceResults[].source` → `source`, `sourceResults[].status` → `sourceStatus`, `attentionReasons[]` → `attentionReason`, `lastAttempt.failureClass` → `failureClass`, `source` postu i świeżości → `source`, `itemType`, cele `kind` → `targetKind` i `stance.direction` → `direction`; tylko zbiory, które są w `rules.json`). Każda wartość atrapy na tej ścieżce należy do zbioru, a każda ścieżka z mapy istnieje w którymś pliku kontraktu, więc przestarzały wpis czerwieni test.

#### 3. Roadmapa w rendererze

**File**: `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Zgodnie z regułą repo zmiana roadmapy (S-43 → `in-progress` przez `/10x-implement`) idzie razem z rendererem i SVG w pierwszym commicie.

**Contract**: wpis `S-43` w danych renderera ma status `progress` (`:218`); SVG regenerowany przez `python3 tools/render_roadmap_svg.py`.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą, w tym nowy strażnik: `cd szczur-radar-web && npm test`
- Typecheck i build web przechodzą (nowe pliki TS w `src/`): `cd szczur-radar-web && npm run build`
- Celowe zepsucie X4 (agent wykonuje i cofa): `configured` → `enabled` w odpowiedzi `/api/admin/pipeline-runs/sources` atrapy daje czerwony strażnik z nazwą ścieżki
- Celowe zepsucie typu i enumu (agent wykonuje i cofa): `fetched: '3'` zamiast liczby w jednym przebiegu oraz status `'done'` w jednym przebiegu dają czerwony strażnik
- Celowe zepsucie pokrycia (agent wykonuje i cofa): tymczasowy plik `src/contract/extra.json` daje czerwony strażnik pokrycia
- Celowe zepsucie trybu (agent wykonuje i cofa): przemianowanie stałej `MISSING` w atrapie daje błąd pomocnika
- SVG roadmapy odpowiada rendererowi: `python3 tools/render_roadmap_svg.py && git diff --exit-code docs/visuals/roadmap.svg` po commicie

---

## Faza 2: Atrapa SPA odpowiada jak API

### Overview

Atrapa SPA zostaje poprawiona we wszystkich 12 punktach i dla `/raw` Reddita, a strażnik dostaje tabelę zachowań i niezmienniki danych. Każdy wiersz, który pilnuje jednej z 12 rozbieżności albo `/raw` Reddita, byłby czerwony na atrapie sprzed tej fazy. Wiersze, które przypinają zachowanie zgodne z API już przed fazą, tabela oznacza jako „zgodne przed fazą”: 401 na `/me` w `AUTH='anonymous'`, 404 dla nieznanego UUID przebiegu, powiadomienia i postu, 400 dla `page > 21474836` w feedzie, 204 wylogowania, 400 restore dla `[]`/null.

### Changes Required:

#### 1. Identyfikatory

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Wszystkie id (konto, przebiegi, powiadomienia, nieudane posty, posty feedu) stają się stałymi UUID z czytelnym prefiksem rodzaju. Trasy z id w ścieżce odpowiadają jak API: id bez UUID → 400 `invalid_request`, nieznany UUID → 404 (`post_not_found` dla postów, `not_found` dla przebiegów i powiadomień).

**Contract**: funkcja budująca UUID z rodzaju i numeru (np. `00000000-0000-4000-8000-0001000000NN` dla przebiegów), stała dla danego elementu między wywołaniami; trasy `GET /api/admin/pipeline-runs/{id}`, `GET /api/posts/{id}[/raw]`, `POST /api/admin/notifications/{id}/read`, elementy `postIds` w restore i starcie.

#### 2. Sesja, logowanie i role

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Atrapa trzyma sesję jak API. `AUTH` przyjmuje `'admin' | 'user' | 'anonymous'` i ustawia stan początkowy. Logowanie i wylogowanie go zmieniają, co da M-5 przepływ logowania i strażnika sesji.

**Contract**:
- bez sesji każda nie-publiczna trasa `/api/` → 401 `not_authenticated`;
- sesja `user` na `/api/admin/**` → 403 `forbidden`, a na pozostałych trasach dane; `/me` zwraca `role: 'user'`;
- `POST /api/auth/login`: nowa stała `LOGIN = 'ok' | 'invalid' | 'limited'`:
  - `ok` → 200 z ciałem jak `user.json`, rola z adresu (konto admina z atrapy → `admin`, inne → `user`);
  - `invalid` → 401 `invalid_credentials`;
  - `limited` → 429 `too_many_attempts` z nagłówkiem `Retry-After`;
  - ciało bez `email`/`password` → 400 `invalid_request`;
- `POST /api/auth/logout` → 204 zawsze, czyści sesję;
- trasy aktywacji bez zmian (publiczne).

#### 3. Start przebiegu i źródła

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Start odpowiada 202 jak API, a dzisiejsze 409 zostaje jako tryb. Źródła i dni bez danych obejmują Reddita, tak jak reszta danych atrapy.

**Contract**:
- nowa stała `START = 'accepted' | 'busy' | 'not_configured'`:
  - `accepted` → 202 `RunView[]` z nowymi przebiegami `queued` dopisanymi do listy instalacji: dla zakresu przebieg `range`, poprzedzony przebiegiem `retry`, gdy atrapa ma nieudane posty do ponowienia; dla `postIds` przebieg `posts`, a gdy któryś z nich jest martwy na liście `failed` atrapy → 400 `post_dead` jak API (`PipelineRunService.java:198-200`, `PipelineRunExceptionHandler`), bez nowego przebiegu;
  - `busy` → 409 `pipeline_busy`;
  - `not_configured` → 503 `source_not_configured`;
- walidacja ciała jak `StartRequest` (`PipelineRunController.java:170-189`) → 400 `invalid_request`: `from > to`, `to` po dziś (Warszawa), zakres > 7 dni, brak `from`/`to`, mieszanie zakresu i `postIds`, pusta lub > 100 lista, null w liście, nieznane źródło;
- `/api/admin/pipeline-runs/sources` → `[{wykop,true},{reddit,true}]`;
- `missingDays()` zwraca dni z `wykop`, `reddit` i obu źródeł.

#### 4. Stronicowanie i restore

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Trzy listy walidują i odsyłają `page`/`size` jak API, a restore waliduje elementy.

**Contract**:
- `/api/posts`, `/api/admin/pipeline-runs`, `/api/admin/posts/failed`: `page` liczba całkowita 0…21474836, `size` 1…100 (domyślnie 50/20/50), inaczej 400 `invalid_request`;
- lista nieudanych tnie `items` według `page`/`size` i odsyła żądane wartości;
- restore: element, który nie jest UUID → 400 `invalid_request`.

Literał `pageNumber > ${rules.limits.pageMax}` sprawdzany przez `rules.test.ts:82-84` musi zostać w tekście atrapy, albo test trzeba dostosować do nowej postaci tej samej granicy w tym samym commicie.

#### 5. Poprawki danych

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Dane atrapy nie mogą być stanem, którego API nie wytworzy.

**Contract**:
- każda nieudana próba ma ≥1 naruszenie w kształcie API: `provider_error`/`timeout` → jedno `{failureClass, rule:'call', path:'$', message}`, `schema_violation` → `schema:<keyword>`, `fabricated_evidence` → reguła walidatora (wzory z `failed-posts-page.json:13-71`);
- w każdym przebiegu `finishedAt` każdego wyniku źródła mieści się w czasie kroku `fetch` (`startedAt` ≤ … ≤ `finishedAt` kroku), w tym w `r-source-failed` (`mock:102`, `:171`);
- `/api/posts/{id}/raw` dla postu z Reddita zwraca skrócony element Arctic Shift (kształt z `fixtures/reddit/search-posts-inwestowanie.json:3-112`: m.in. `id`, `subreddit` = kanał postu, `title`, `selftext`, `permalink`, `created_utc`); dla Wykopu bez zmian.

#### 6. Tabela zachowań i niezmienniki w strażniku

**File**: `szczur-radar-web/src/contract/mockApi.test.ts`, `szczur-radar-web/src/contract/mockApi.ts`

**Intent**: Wiersz na każde zachowanie z punktów 1–4 oraz trzy niezmienniki danych z punktu 5. `started-runs.json` dostaje trasę, a wyjątek pokrycia z fazy 1 znika. Tabela kształtu obejmuje teraz także `POST /api/auth/login` (`user.json`).

**Contract**:
- wiersz = stałe trybu, metoda, ścieżka, ciało → oczekiwany status i `detail` (albo plik kontraktu dla 2xx), z opcjonalnym znacznikiem „zgodne przed fazą” (patrz Overview);
- wiersze obejmują co najmniej:
  - 401 na każdej nie-publicznej trasie z tabeli tras w `AUTH='anonymous'`;
  - 403 na każdej trasie `/api/admin/**` w `AUTH='user'`;
  - trzy wyniki logowania i 400;
  - wylogowanie, po którym `/me` daje 401;
  - 400 dla id bez UUID na czterech trasach z id i 404 dla nieznanego UUID;
  - granice `page`/`size` na trzech listach;
  - start 202 dla zakresu i `postIds` (ciało zgodne z `started-runs.json`), 400 dla przypadków walidacji, 400 `post_dead` dla `postIds` z martwym postem, 409 i 503 w trybach;
  - restore 400 dla id bez UUID;
- niezmienniki: każda `lastAttempt` ma ≥1 naruszenie; czasy wyników źródeł mieszczą się w kroku `fetch`; `/raw` postu z Reddita ma `subreddit` równy jego kanałowi;
- `MockConstant` obejmuje `LOGIN` i `START`.

#### 7. Dokumentacja atrapy

**File**: `szczur-radar-web/dev/mock-api.js` (nagłówek `:1-91`), `REPOSITORY_GUIDELINES.md` (sekcja UI, akapit „Visual check”; sekcja Coding Style, akapit o `src/contract/`)

**Intent**: Nagłówek opisuje nowe tryby (`AUTH` z `user`, `LOGIN`, `START`) i podaje adresy z UUID zamiast `r-running`/`feed-5`. Wytyczne mówią, że strażnik `mockApi.test.ts` porównuje atrapę z plikami kontraktu i tabelą zachowań, a nowa trasa SPA dostaje wiersz w jego tabeli tras.

**Contract**: tylko tekst; procedura `playwright-cli run-code` bez zmian.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą: `cd szczur-radar-web && npm test`
- Typecheck i build web przechodzą: `cd szczur-radar-web && npm run build`
- Każdy wiersz tabeli zachowań poza oznaczonymi „zgodne przed fazą” jest czerwony na atrapie z commitu fazy 1 (agent uruchamia strażnik fazy 2 na `git show <sha commitu fazy 1>:szczur-radar-web/dev/mock-api.js` podstawionym w pomocniku albo w tymczasowej kopii i potwierdza czerwień każdego takiego wiersza oraz zieleń wierszy „zgodne przed fazą”; potem przywraca)
- Celowe zepsucie zachowania (agent wykonuje i cofa): przywrócenie 409 dla startu w trybie `accepted` oraz pustej listy `violations` w jednej próbie daje czerwony strażnik
- Atrapa nadal działa w przeglądarce (agent): `npx vite --port 5173 --strictPort` i `playwright-cli run-code --filename=szczur-radar-web/dev/mock-api.js`, potem `playwright-cli` otwiera `/admin`, stronę przebiegu, feed, stronę postu z Reddita (z „Dane dla nerdów”) i `/activate` z adresów z nagłówka atrapy; w każdej trwa render bez błędów w konsoli, a `/admin` pokazuje Reddita w „Dni bez danych”
- Przepływ logowania na atrapie (agent): z `AUTH='anonymous'` strona logowania przyjmuje dane w trybie `LOGIN='ok'` i prowadzi do feedu, a w `LOGIN='invalid'` pokazuje komunikat błędu (`playwright-cli` snapshot)

---

## Faza 3: Atrapa crona z kontraktu

### Overview

Kształty tras `/internal/pipeline-runs/**` dostają pliki w `tools/pipeline-trigger/fixtures/api/`, przypięte przez `InternalPipelineRunIT`. Atrapa w `test.sh` buduje z nich odpowiedzi, a `classify` jest sprawdzane wobec `rules.json`.

### Changes Required:

#### 1. Porównanie z plikiem spoza katalogu SPA

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractFiles.java`

**Intent**: Przeciążenie przyjmujące ścieżkę pliku, na które deleguje dzisiejsza metoda. Komunikat o różnicy (wydruk zamaskowanego ciała do skopiowania) zostaje ten sam.

**Contract**: `public static void assertMatches(String json, Path file)`; `assertMatches(String json, String fileName)` woła je z `Path.of(DIRECTORY + fileName)`.

#### 2. Pliki kontraktu crona i IT

**File**: `tools/pipeline-trigger/fixtures/api/*.json` (nowe), `tools/pipeline-trigger/fixtures/README.md` (nowy, kto czyta pliki — jak nagłówek `ping-urls.txt:1-9`), `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java`

**Intent**: Każda odpowiedź, którą czyta `trigger.sh`, jest plikiem, który IT porównuje z prawdziwą odpowiedzią zamiast literału albo `containsOnlyKeys`. Dzisiejsze asercje wartości (np. `containsEntry("fetched", finished.getFetched())`) zostają tam, gdzie pilnują zachowania, a nie kształtu.

**Contract**:
- `daily-started.json` (202 `/daily`, `:235`; `List<StartedRun(id, kind)>`, inny rekord niż `RunView[]` w `web/src/contract/started-runs.json`, stąd inna nazwa);
- `run-state-failed.json` (zakończony przed oceną, `:247-271`);
- `run-state-running.json` (`:275-297`);
- `run-state-interrupted.json` (`deploy`, `:299-307`);
- `run-state-queued.json` (nowy przypadek: przebieg `queued` wstawiony przez `jdbc`, sprzątany w `finally` jak `:308-311`);
- `nightly-summary.json` (`:336-352`; tekst jest częścią pliku, więc dane przypadku muszą dawać stały tekst — dziś dają, bo zegar i przebiegi są stałe);
- ciała błędów 400/404/409/401 porównywane dalej literałami (to `{"detail":kod}`); ich kody sprawdza punkt 3.

#### 3. Atrapa crona z plików i `classify` wobec rejestru

**File**: `tools/pipeline-trigger/test.sh`, `tools/pipeline-trigger/trigger.sh` (tylko komentarz przy `classify`, `:206-209`)

**Intent**: Atrapa nie zna już pól `RunState` ani tekstu podsumowania. Dostaje pliki przez env i podstawia wartości zamaskowane. Zbiór statusów `classify` i kody błędów atrapy są sprawdzane wobec `rules.json`, zanim ruszy pierwszy przypadek.

**Contract**:
- `test.sh` czyta pliki z `fixtures/api/` na hoście i przekazuje je atrapie jako JSON w zmiennej env (jak `STUB_CASE`);
- odpowiedź `GET /{id}` = plik stanu wybrany według statusu (`queued` → queued, `running` → running, `interrupted` → interrupted, `succeeded`/`partial`/`failed` → failed z podmienionym `status`), z `<uuid>` = id przebiegu i `<instant>` = stałe czasy atrapy, a potem nadpisania przypadku; nadpisanie klucza spoza pliku → błąd atrapy;
- `assessTotal` i `step` wynikają z pliku stanu, więc atrapa nie wysyła już `assessTotal: 251` przed oceną;
- przypadek `old_api` dalej usuwa pola sprzed S-06/S-08;
- odpowiedź 202 = `daily-started.json` z id przebiegów przypadku;
- `nightly-summary` = `nightly-summary.json`; atrapa odpowiada 400 `invalid_request` na ciało bez listy `runs` albo z 0 lub > 10 id oraz 404 `not_found` na id nieznanego przebiegu (`IPRIT:363-385`);
- przed przypadkami `test.sh` (jq na hoście):
  - wyciąga z `trigger.sh` wzorce `case` w `classify` i sprawdza, że ich zbiór = `rules.json` `.values.runStatus`;
  - sprawdza, że każdy kod błędu atrapy (`pipeline_busy`, `source_not_configured`, `not_authenticated`, `internal_error`, `not_found`, `invalid_request`) należy do `.errorCodes`;
- komentarz przy `classify` mówi, że zbiór pilnuje `test.sh` wobec `rules.json`.

#### 4. Wytyczne repo

**File**: `REPOSITORY_GUIDELINES.md` (Project Structure, wpis `tools/pipeline-trigger/`)

**Intent**: Agent zmieniający `RunState`, `StartedRun` albo podsumowanie nocy wie z wytycznych, że kształt żyje w pliku narzędzia, tak jak wpis `tools/healthchecks/` opisuje `ping-urls.txt`.

**Contract**: jedno zdanie: `fixtures/api/` trzyma odpowiedzi `/internal/pipeline-runs/**`, które przypina `InternalPipelineRunIT` (`ContractFiles.assertMatches(json, Path)`), a `test.sh` buduje z nich atrapę API; `classify` jest sprawdzane wobec `rules.json` `values.runStatus`.

### Success Criteria:

#### Automated Verification:

- Testy integracyjne API przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- Test crona przechodzi (Docker): `tools/pipeline-trigger/test.sh`
- Celowe zepsucie kształtu (agent wykonuje i cofa): zmiana nazwy klucza w `run-state-running.json` daje czerwony `InternalPipelineRunIT` z wydrukiem ciała, a ta sama zmiana w nadpisaniu przypadku `test.sh` daje błąd atrapy
- Celowe zepsucie rejestru (agent wykonuje i cofa): usunięcie `partial` z `classify` w kopii `trigger.sh` albo dopisanie wartości do `.values.runStatus` w kopii `rules.json` daje czerwony `test.sh` przed pierwszym przypadkiem
- Atrapa nie wysyła `assessTotal` przed oceną (agent): `test.sh` loguje tętno przebiegu zakończonego `failed` z `assessed …/?` (grep w logu przypadku)

---

## Faza 4: Stuby `deploy-gate` z kontraktu

### Overview

Odpowiedzi `/api/deploy-safety` i `/api/ready` dostają pliki w `tools/deploy-gate/fixtures/api/`, przypięte przez `DeploySafetyIT` i `HealthIT`. Stuby w `tools/deploy-gate/test.sh` czytają je zamiast literałów.

### Changes Required:

#### 1. Pliki kontraktu bramki i IT

**File**: `tools/deploy-gate/fixtures/api/*.json` (nowe), `tools/deploy-gate/README.md` (akapit o `fixtures/api/`), `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/DeploySafetyIT.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/health/HealthIT.java`

**Intent**: IT porównują ciała `/api/deploy-safety` i `/api/ready` z plikami przez `ContractFiles.assertMatches(json, Path)` zamiast literałów.

**Contract**: `deploy-safety-safe.json` (`DeploySafetyIT:76`), `deploy-safety-running.json` (`:83`), `deploy-safety-queued.json` (`:90`), `deploy-safety-unavailable.json` (503, `:221-223`), `ready.json` (`HealthIT:36-37`); asercje statusów HTTP zostają.

#### 2. Stuby z plików

**File**: `tools/deploy-gate/test.sh`

**Intent**: Stałe `safe`, `running`, `queued`, `unavailable` (`:436-439`) i `ready` (`:610`) budowane jq z plików `fixtures/api/`, ze statusem HTTP przy pliku. Stub `ready` wysyła więc `{"status":"ready"}` jak API.

**Contract**: stałe zachowują format `{"status": <kod>, "body": <ciało>}`, którego używa stub; pętle po `fixtures/*.json` planów IaC nie widzą podkatalogu `api/` (`:46`, `:81-83` biorą pliki po nazwie). `ready.json` służy wierności stuba, nie bramce: `verify-apply.sh:311-321` (przez `lib.sh:40-48`) sprawdza tylko kod HTTP `/api/ready`, więc zmiana ciała w pliku nie czerwieni `test.sh`, tylko `HealthIT`.

#### 3. Wytyczne repo

**File**: `REPOSITORY_GUIDELINES.md` (Project Structure, wpis `tools/deploy-gate/`)

**Intent**: Jak w fazie 3: kształt odpowiedzi `/api/deploy-safety` i `/api/ready` żyje w pliku narzędzia przypiętym przez IT.

**Contract**: jedno zdanie: `fixtures/api/` trzyma odpowiedzi `/api/deploy-safety` i `/api/ready`, które przypinają `DeploySafetyIT` i `HealthIT`, a stuby `test.sh` je czytają; plany IaC w `fixtures/*.json` zostają po nazwie.

### Success Criteria:

#### Automated Verification:

- Testy integracyjne API przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- Test bramki przechodzi: `tools/deploy-gate/test.sh`
- Celowe zepsucie (agent wykonuje i cofa): zmiana klucza `reason` w `deploy-safety-running.json` daje czerwony `DeploySafetyIT` i czerwony `tools/deploy-gate/test.sh` (wait-safe nie widzi powodu)
- Brak literałów ciał API w stubach (agent): `grep -nE "pipeline_running|pipeline_queued|\"status\": \"(ok|ready|unavailable)\"" tools/deploy-gate/test.sh` nie znajduje nic poza odczytem plików

---

## Testing Strategy

### Unit Tests:

- `mockApi.test.ts`:
  - kształt (klucze, typy, enumy) na każdej trasie z tabeli i pokrycie plików kontraktu;
  - tabela zachowań na trybach `AUTH`, `LOGIN`, `START`, `MODE`;
  - niezmienniki danych;
  - zawieszenia na sztucznych zegarach.
- `rules.test.ts`: skan kodów błędów atrapy i literał limitu strony zostają; nowe kody w atrapie (`forbidden`, `invalid_credentials`, `too_many_attempts`) są już w `errorCodes`.

### Integration Tests:

- `InternalPipelineRunIT`, `DeploySafetyIT`, `HealthIT` porównują odpowiedzi z plikami narzędzi.
- `tools/pipeline-trigger/test.sh` i `tools/deploy-gate/test.sh` budują atrapy z tych plików, a `classify` i kody błędów sprawdzają wobec `rules.json`.

### Manual Testing Steps:

Brak. Każde sprawdzenie, także atrapa w przeglądarce i przepływ logowania, wykonuje agent przez `playwright-cli` (kryteria faz 2).

## Performance Considerations

Strażnik atrapy pobiera 3 strony feedu, 2 strony przebiegów i każdy przebieg oraz post po id, bez sieci i z wirtualnym czasem. Powinien zająć sekundy w `npm test`. Jeśli zajmie więcej niż kilka sekund, pojedyncze pobrania postów ogranicza się do próbki każdego rodzaju (Wykop, Reddit, komentarz, link).

## Migration Notes

Nie dotyczy: bez migracji, bez zmian w wdrażanym kodzie. Adresy do zrzutów z nagłówka atrapy zmieniają się na UUID; stare (`/admin/runs/r-running`, `/posts/feed-5`) dają w atrapie 400, tak jak dałoby API.

## References

- Research: `context/changes/contract-bound-mocks/research.md`
- Audyt: `context/changes/post-m3-verification-audit/research.md` §3, eksperyment X4, „Materiał dla M-4” p. 4
- S-24: `context/archive/2026-10-29-api-contract-guard/plan.md:51` (K5: atrapa bez typów i wspólnych danych)
- S-41: `context/archive/2026-11-08-api-contract-shapes-round-two/plan.md:58`, `:62` (atrapy przekazane do S-43)
- Wzorzec wspólnego pliku: `tools/healthchecks/fixtures/ping-urls.txt`, `szczur-radar-api/src/test/java/pl/szczurradar/api/alerts/AlertSettingsTest.java:34`, `tools/healthchecks/test.sh:442-475`
- Porównanie z plikiem: `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractFiles.java:23-79`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Strażnik kształtu atrapy SPA

#### Automated

- [x] 1.1 Testy web przechodzą, w tym nowy strażnik: `cd szczur-radar-web && npm test` — 3f0e05a
- [x] 1.2 Typecheck i build web przechodzą (nowe pliki TS w `src/`): `cd szczur-radar-web && npm run build` — 3f0e05a
- [x] 1.3 Celowe zepsucie X4 (agent wykonuje i cofa): `configured` → `enabled` w odpowiedzi `/api/admin/pipeline-runs/sources` atrapy daje czerwony strażnik z nazwą ścieżki — 3f0e05a
- [x] 1.4 Celowe zepsucie typu i enumu (agent wykonuje i cofa): `fetched: '3'` zamiast liczby w jednym przebiegu oraz status `'done'` w jednym przebiegu dają czerwony strażnik — 3f0e05a
- [x] 1.5 Celowe zepsucie pokrycia (agent wykonuje i cofa): tymczasowy plik `src/contract/extra.json` daje czerwony strażnik pokrycia — 3f0e05a
- [x] 1.6 Celowe zepsucie trybu (agent wykonuje i cofa): przemianowanie stałej `MISSING` w atrapie daje błąd pomocnika — 3f0e05a
- [x] 1.7 SVG roadmapy odpowiada rendererowi: `python3 tools/render_roadmap_svg.py && git diff --exit-code docs/visuals/roadmap.svg` po commicie — 3f0e05a

### Phase 2: Atrapa SPA odpowiada jak API

#### Automated

- [x] 2.1 Testy web przechodzą: `cd szczur-radar-web && npm test` — 28e924c
- [x] 2.2 Typecheck i build web przechodzą: `cd szczur-radar-web && npm run build` — 28e924c
- [x] 2.3 Każdy wiersz tabeli zachowań poza oznaczonymi „zgodne przed fazą” jest czerwony na atrapie z commitu fazy 1 (agent uruchamia strażnik fazy 2 na `git show <sha commitu fazy 1>:szczur-radar-web/dev/mock-api.js` podstawionym w pomocniku albo w tymczasowej kopii i potwierdza czerwień każdego takiego wiersza oraz zieleń wierszy „zgodne przed fazą”; potem przywraca) — 28e924c
- [x] 2.4 Celowe zepsucie zachowania (agent wykonuje i cofa): przywrócenie 409 dla startu w trybie `accepted` oraz pustej listy `violations` w jednej próbie daje czerwony strażnik — 28e924c
- [x] 2.5 Atrapa nadal działa w przeglądarce (agent): `npx vite --port 5173 --strictPort` i `playwright-cli run-code --filename=szczur-radar-web/dev/mock-api.js`, potem `playwright-cli` otwiera `/admin`, stronę przebiegu, feed, stronę postu z Reddita (z „Dane dla nerdów”) i `/activate` z adresów z nagłówka atrapy; w każdej trwa render bez błędów w konsoli, a `/admin` pokazuje Reddita w „Dni bez danych” — 28e924c
- [x] 2.6 Przepływ logowania na atrapie (agent): z `AUTH='anonymous'` strona logowania przyjmuje dane w trybie `LOGIN='ok'` i prowadzi do feedu, a w `LOGIN='invalid'` pokazuje komunikat błędu (`playwright-cli` snapshot) — 28e924c

### Phase 3: Atrapa crona z kontraktu

#### Automated

- [x] 3.1 Testy integracyjne API przechodzą: `cd szczur-radar-api && ./mvnw -B verify` — 472a7f7
- [x] 3.2 Test crona przechodzi (Docker): `tools/pipeline-trigger/test.sh` — 472a7f7
- [x] 3.3 Celowe zepsucie kształtu (agent wykonuje i cofa): zmiana nazwy klucza w `run-state-running.json` daje czerwony `InternalPipelineRunIT` z wydrukiem ciała, a ta sama zmiana w nadpisaniu przypadku `test.sh` daje błąd atrapy — 472a7f7
- [x] 3.4 Celowe zepsucie rejestru (agent wykonuje i cofa): usunięcie `partial` z `classify` w kopii `trigger.sh` albo dopisanie wartości do `.values.runStatus` w kopii `rules.json` daje czerwony `test.sh` przed pierwszym przypadkiem — 472a7f7
- [x] 3.5 Atrapa nie wysyła `assessTotal` przed oceną (agent): `test.sh` loguje tętno przebiegu zakończonego `failed` z `assessed …/?` (grep w logu przypadku) — e4999b9

### Phase 4: Stuby `deploy-gate` z kontraktu

#### Automated

- [x] 4.1 Testy integracyjne API przechodzą: `cd szczur-radar-api && ./mvnw -B verify` — 45086be
- [x] 4.2 Test bramki przechodzi: `tools/deploy-gate/test.sh` — 45086be
- [x] 4.3 Celowe zepsucie (agent wykonuje i cofa): zmiana klucza `reason` w `deploy-safety-running.json` daje czerwony `DeploySafetyIT` i czerwony `tools/deploy-gate/test.sh` (wait-safe nie widzi powodu) — 45086be
- [x] 4.4 Brak literałów ciał API w stubach (agent): `grep -nE "pipeline_running|pipeline_queued|\"status\": \"(ok|ready|unavailable)\"" tools/deploy-gate/test.sh` nie znajduje nic poza odczytem plików — 45086be
