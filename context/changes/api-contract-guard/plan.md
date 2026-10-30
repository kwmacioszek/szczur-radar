# S-24: kontrakt SPA ↔ API pilnowany testem — plan implementacji

## Overview

Trzy strażniki kontraktu między `szczur-radar-api` a `szczur-radar-web`, każdy odpowiadający jednemu elementowi outcome S-24 (`context/foundation/roadmap.md:197-208`):

1. Każdy błąd, który trafia do `/error`, ma kształt `{"detail":"<kod>"}`, a nieoczekiwany błąd serwera dostaje kod `internal_error` — ten sam, którego już używają atrapa i testy SPA.
2. Pełne ciała trzech dużych odpowiedzi (przebieg z krokami i wynikami źródeł, post z oceną, nieudany post) są zapisane w plikach JSON, które porównuje zarówno IT, jak i Vitest.
3. Wspólny rejestr skopiowanych reguł (białe znaki, limity, listy wartości z etykietą, powody „wymaga uwagi”, kody błędów) jest czytany przez test Javy i test Vitest, więc kopie nie rozjadą się bez czerwonego testu.

Bez generatora klienta i bez OpenAPI — celem jest wykrycie rozjazdu, nie nowa warstwa (`roadmap.md:207`).

## Current State Analysis

Na podstawie `context/changes/api-contract-guard/research.md` (HEAD `5a013ca`):

- **Błąd 500 zmierzony na żywym jarze.** Wyjątek w kontrolerze daje 500 z domyślnym ciałem Spring Boot `{"timestamp","status":500,"error":"Internal Server Error","path"}`, z sesją i bez niej. To samo ciało dostaje 404/405 z sesją. SPA czyta `detail` miękko (`szczur-radar-web/src/api/client.ts:43-54`), więc prawdziwy błąd 500 widzi jako `ApiError(500, null)`. Atrapa (7 miejsc) i testy (12 miejsc w 6 plikach) używają `{"detail":"internal_error"}`, którego `ErrorCode.java:19-64` nie zna.
- **Awaria bazy przy logowaniu.** `AuthController.java:92-93` przepuszcza `InternalAuthenticationServiceException`. `ExceptionTranslationFilter` znajduje go w łańcuchu przyczyn i odpowiada 401 `not_authenticated`, a `LoginView.vue:26` pokazuje „Nieprawidłowy e-mail lub hasło.”.
- **Pełne ciała.** Żaden IT nie zapisuje całego `RunView` (30 pól), `StepView`, `SourceResultView`, `PostItem`/`AssessmentView` ani `FailedPost`/`LastAttempt`. Pola Javy i TS zgadzają się dziś 1:1. Ręczne dane w testach web łamią reguły serwera w co najmniej czterech miejscach:
  - `FeedView.test.ts:43` i `PostCard.test.ts:26` — cel bullish z pustym `claim_ids`;
  - `StanceText.test.ts:18` — każdy kierunek z `claim_ids: []`;
  - `RunDetailView.test.ts:258-271` — `skipped` przebiegu nie jest sumą `skipped` źródeł, a lista powodów nie ma `skipped`;
  - drobny rozjazd: `api/pipeline.test.ts:90-96` podaje statusy 400 dla kodów, które API wysyła z 409 i 503.
- **Kopie reguł.** Wszystkie są dziś równe, ale żaden test nie porównuje stron. Istniejące testy porównują stałą tylko z wartością wpisaną w ten sam test.

### Key Discoveries:

- Boot 4.0.8 tworzy `BasicErrorController` tylko przy braku innego `ErrorController` (`ErrorMvcAutoConfiguration`, `@ConditionalOnMissingBean(ErrorController.class)`). Klasy są w `org.springframework.boot.webmvc.error.*` i `org.springframework.boot.webmvc.autoconfigure.error.*`.
- `SecurityConfig.java:101` przepuszcza każdy `DispatcherType.ERROR`, więc `/error` odpowiada z sesją i bez niej. `AuthorizationCompletenessIT.routes()` (`:123-138`) zbiera mapowanie `/error` niezależnie od klasy i oczekuje 401 dla bezpośredniego REQUEST.
- `MockMvcTester` nie przekazuje błędów do kontenera. Ciało `/error` pokaże tylko IT `RANDOM_PORT` z `java.net.http.HttpClient` — wzór w `auth/OriginGuardHttpIT.java:21-60`.
- Żaden test nie asercjonuje kluczy ciała Boot (`timestamp`, `path`, `error` jako klucz błędu), więc zamiana go niczego nie psuje (grep w `src/test`).
- Testy Javy już czytają pliki spoza modułu: `Path.of("../tools/healthchecks/fixtures/ping-urls.txt")` (`alerts/AlertSettingsTest.java:29-30`) i `ContractExamples.java:20`. Obraz API buduje się z `-DskipTests` (`szczur-radar-api/Dockerfile:12`).
- Obraz web buduje z kontekstu `./szczur-radar-web` i typecheckuje testy (`tsconfig.app.json:14`). Wspólne pliki muszą więc leżeć w `szczur-radar-web/`. Wtedy zmiana pliku przedeployuje tylko web (`.railway/railway.ts:112,194`).
- `labels` w `src/labels.ts:2` i `WHITE_SPACE` w `src/feedFilter.ts:24` nie są eksportowane. `MAX_PAGE_SIZE` siedzi w `AdminView.vue:128`, długości hasła w `ActivateView.vue:14-15`. W Javie `PipelineRunController.MAX_RANGE_DAYS` (`:60`) jest pakietowe, a limity `q` (`PostController.java:87`) i strony przebiegów (`PipelineRunController.java:111`) to literały w adnotacjach.
- `ErrorCode` serializuje się przez `@JsonValue code()` (`ErrorCode.java:77-80`). Enumy odpowiedzi idą przez `@JsonValue`, więc wartości w JSON-ie najprościej pobrać przez `JsonMapper`.
- Wzór strażników repo to jawne oczekiwanie plus kompletność w obie strony (S-23 `ClosedSetsIT`, `AuthorizationCompletenessIT`). Kopie między językami S-25 sprowadził do wspólnej tabeli przypadków czytanej przez obie strony.

## Desired End State

- Każda odpowiedź z `/error` ma `Content-Type: application/json` i ciało `{"detail":"<kod>"}`. Dla 5xx kod to `internal_error` (500), dla 404 `not_found`, dla 400/401/403/415 ich istniejące kody, dla pozostałych 4xx `invalid_request` przy zachowanym statusie. Kontener dalej loguje wyjątek.
- Logowanie przy awarii magazynu kont (`InternalAuthenticationServiceException`) kończy się 500 `internal_error` z logiem ERROR i ogólnym komunikatem w SPA, a nie komunikatem o złym haśle.
- `szczur-radar-web/src/contract/` zawiera `run.json`, `post.json`, `failed-post.json` (pełne ciała z wymaskowanymi UUID i czasami) oraz `rules.json` (rejestr reguł).
- Zmiana pola któregokolwiek z trzech rekordów, wartości enumu z etykietą, stałej z rejestru albo kodu błędu bez aktualizacji drugiej strony daje czerwony `./mvnw -B verify` albo `npm test`.
- Weryfikacja: `./mvnw -B verify`, `npm test` i `npm run build` są zielone, a obraz web buduje się z kontekstu `./szczur-radar-web`. Celowe zepsucie każdej strony daje czerwony test o oczekiwanej nazwie.

## What We're NOT Doing

- **Generator klienta, OpenAPI, schematy, walidacja odpowiedzi w runtime SPA.** Poza celem S-24 (`roadmap.md:207`).
- **Awaria magazynu sesji** (zapytanie z sesją przy niedostępnej bazie daje po ~90 s HTML Tomcata, bo `/error` też nie może odczytać sesji). Przy padniętej bazie i tak nic nie działa, a `/api/ready` to pokazuje. Zostaje znaną różnicą.
- **Follow-up F1 z S-22** (`runTone`/`runStatusLabel` czytają `skipped > 0`, `NotifiedRun` bez `attentionReasons`). Zostaje follow-upem.
- **Atrapa poza minimum (K5).** Atrapa nie dostaje typów ani wspólnych danych, a jej kopie białych znaków, limitu `q` i tekstów przerwania nie są skanowane. Strażnik obejmuje w atrapie tylko literały kodów błędów. Jej `internal_error` i catch-all `not_found` stają się zgodne z API bez zmian.
- **Brak NUL w `q` po stronie SPA** i **limit 100 postów ponowienia w SPA.** To reguły obecne tylko po jednej stronie, a nie rozjazd kopii.
- **Wynik prefiltra w `KitchenSinkView.vue` i atrapie.** To dane pokazowe. Wynik prefiltra w prawdziwym poście pokrywa `post.json`, jeśli seed go używa; nie jest to wymóg.
- **Powiadomienia, „Dni bez danych”, świeżość.** Już mają pełny zbiór kluczy albo ścisłe ciało w IT (`NotificationsIT:169-187`, `MissingDaysIT:253-257`, `FreshnessIT`).
- **Migracja wszystkich ręcznych builderów testów na próbki z kontraktu.** Naprawiamy tylko dane, które łamią reguły serwera.
- **Zmiana typów TS na wymagane** (`Run.attentionReasons?`, `Run.sourceResults?` zostają opcjonalne dla starszego API). Próbki i tak zawierają te pola.

## Implementation Approach

Fazy są niezależne od siebie, ale idą w kolejności outcome. Każda kończy się zielonym `verify` i `npm test`.

- **Faza 1 (tylko API).** Daje kod błędu, z którym zgadza się już atrapa.
- **Faza 2.** Tworzy katalog `szczur-radar-web/src/contract/` i mechanikę złotych plików: maskowanie w Javie, typowane próbki w TS.
- **Faza 3.** Dokłada do tego samego katalogu rejestr reguł i skan kodów błędów.

Zasada obu strażników wspólnych plików: Java porównuje plik z rzeczywistością serwera, Vitest porównuje ten sam plik z kodem SPA. Plik jest jedynym miejscem, które obie strony muszą zaktualizować razem. Każdy rejestr ma test kompletności w obie strony, żeby nowy wpis po jednej stronie nie przeszedł po cichu.

## Critical Implementation Details

- **Logowanie przy awarii bazy.** Owinięcie `InternalAuthenticationServiceException` w inny wyjątek nie wystarczy. `ExceptionTranslationFilter` przeszukuje cały łańcuch przyczyn (`ThrowableAnalyzer`) i nadal znajdzie `AuthenticationException`, więc wynik to 401. Kontroler musi zalogować błąd ERROR i sam zwrócić `ApiError` `internal_error`. Wynik limitera zostaje `ABORTED`, jak dziś.
- **Ciało `/error` pisz bez negocjacji treści.** Przy `Accept: text/html` zwrócenie obiektu z kontrolera skończyłoby się 406 albo pustym ciałem w ścieżce błędu. Pisz przez `ApiError.write` (ten sam sposób co filtry).
- **Typy JSON w TS.** Import JSON-a ma poszerzone `string`, więc nie da się go przypisać do unii TS. Typowana próbka jest osobnym obiektem TS, a test porównuje ją z plikiem przez `toEqual`. Kontrola nadmiarowych pól w literale TS i porównanie `toEqual` razem wykrywają pole dodane albo usunięte po każdej stronie.

## Phase 1: Kod nieoczekiwanego błędu (API)

### Overview

Każdy błąd, który dochodzi do `/error`, dostaje kształt `ApiError`. Nowy kod `internal_error` (500) zastępuje ciało Spring Boot, a logowanie przy awarii bazy przestaje udawać złe hasło.

### Changes Required:

#### 1. Kod błędu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ErrorCode.java`

**Intent**: Dodać zamknięty kod dla nieoczekiwanego błędu serwera i jedno mapowanie statusu HTTP na kod, którego użyje `/error`.

**Contract**: nowa wartość `INTERNAL_ERROR` (500, JSON `internal_error`) z Javadoc „nieoczekiwany błąd serwera; szczegóły tylko w logu”. Statyczne `ErrorCode forStatus(int status)`:
- 400 → `INVALID_REQUEST`, 401 → `NOT_AUTHENTICATED`, 403 → `FORBIDDEN`, 404 → `NOT_FOUND`, 415 → `UNSUPPORTED_MEDIA_TYPE`;
- każdy status ≥ 500 → `INTERNAL_ERROR`;
- każdy inny status → `INVALID_REQUEST`.

Komentarz `ErrorCode.java:10-11` o zgodności z SPA zostaje (faza 3 zamieni go na odesłanie do rejestru).

#### 2. Kontroler `/error`

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ApiErrorController.java` (nowy)

**Intent**: Zastąpić `BasicErrorController`, żeby każda odpowiedź ścieżki błędu kontenera miała kształt `{"detail":kod}`, niezależnie od `Accept`.

**Contract**: `@RestController`, który implementuje `org.springframework.boot.webmvc.error.ErrorController`, mapowanie `/error` dla każdej metody. Status bierze z atrybutu żądania `RequestDispatcher.ERROR_STATUS_CODE` (brak atrybutu → 500). Ciało pisze przez `ApiError.write(response, ErrorCode.forStatus(status))` z zachowaniem statusu. Nie loguje wyjątku drugi raz (kontener już loguje „Servlet.service() … threw exception”) i nie wypisuje do ciała komunikatu ani klasy wyjątku. Bezpośrednie wywołanie `GET /error` bez sesji dalej dostaje 401 z łańcucha Security, bo `AuthorizationCompletenessIT` tego wymaga. Jeśli `ApiError.write` przyjmuje tylko `ErrorCode` ze statusem z kodu, rozszerzyć je o wariant z jawnym statusem (np. 405 z `invalid_request`).

#### 3. Logowanie przy awarii magazynu kont

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/auth/AuthController.java`

**Intent**: Awaria bazy przy logowaniu ma być nieoczekiwanym błędem serwera (500 `internal_error`), a nie odpowiedzią 401, którą SPA pokazuje jako złe hasło.

**Contract**: gałąź `catch (InternalAuthenticationServiceException ex)` (`:92-93`) loguje ERROR („login failed: the account store did not answer”, z wyjątkiem) i zwraca `ApiError.response(ErrorCode.INTERNAL_ERROR)`. `outcome` zostaje `ABORTED`, więc limiter nie liczy próby. Javadoc metody (`:67-71`) dostaje „500 `internal_error` when the account store fails”.

#### 4. Testy

**Files**:
- `szczur-radar-api/src/test/java/pl/szczurradar/api/config/ApiErrorHttpIT.java` (nowy)
- `szczur-radar-api/src/test/java/pl/szczurradar/api/config/ErrorCodeTest.java` (nowy albo istniejący test enumu)
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthIT.java`

**Intent**: Przypiąć prawdziwe ciało kontenera na błąd kontrolera i na błąd ścieżki `/error` oraz nowe zachowanie logowania.

**Contract**:
- `ApiErrorHttpIT` (`RANDOM_PORT`, `MigrateCommand.ENABLE_FLYWAY`, `HttpClient` jak `OriginGuardHttpIT`). Przypadki:
  - (a) publiczna trasa, której bean rzuca `RuntimeException` (np. `@MockitoBean` dla serwisu `POST /api/auth/activation/lookup`) → 500, `Content-Type` JSON, ciało dokładnie `{"detail":"internal_error"}`;
  - (b) to samo z `Accept: text/html` → to samo ciało JSON;
  - (c) zapytanie z sesją na nieznaną trasę `/api/…` → 404 `{"detail":"not_found"}`. Sesja przez prawdziwe logowanie kontem utworzonym w teście; jeśli to za drogie, wystarczy inna ścieżka `/error` z sesją, np. 405 na znanej trasie z niedozwoloną metodą → `{"detail":"invalid_request"}`.
- `ErrorCodeTest` — tabela `forStatus` dla 400, 401, 403, 404, 405, 415, 500, 503.
- `AuthIT` — logowanie, w którym `UserDetailsService`/repozytorium rzuca błąd dostępu do danych → 500 `{"detail":"internal_error"}`, bez sesji w odpowiedzi.

#### 5. Dokumentacja

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Opisać jeden kształt błędu.

**Contract**: w opisie pakietu `config` (`Project Structure`): „`ApiErrorController`: każda odpowiedź `/error` to `{"detail":kod}` (`ErrorCode.forStatus`), nieoczekiwany błąd → 500 `internal_error`; szczegóły tylko w logu”.

### Success Criteria:

#### Automated Verification:

- Nowy IT przechodzi: `cd szczur-radar-api && ./mvnw -B -Dit.test=ApiErrorHttpIT -Dtest=ErrorCodeTest -Dsurefire.failIfNoSpecifiedTests=false verify`
- Testy logowania i kompletności autoryzacji przechodzą: `./mvnw -B -Dit.test='AuthIT,AuthorizationCompletenessIT,AdminRoleIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- Celowe zepsucie (agent wykonuje i cofa): usunięcie `ApiErrorController` daje czerwony `ApiErrorHttpIT` z ciałem Boot w komunikacie
- Testy web bez zmian zielone: `cd szczur-radar-web && npm test`

**Implementation Note**: Po fazie i zielonej automatycznej weryfikacji przejść do fazy 2; ta faza nie ma wierszy Manual.

---

## Phase 2: Wspólny pełny kształt dużych odpowiedzi

### Overview

Trzy złote pliki JSON w `szczur-radar-web/src/contract/` zapisują pełne ciała `GET /api/admin/pipeline-runs/{id}`, `GET /api/posts/{id}` i elementu `GET /api/admin/posts/failed`. IT porównuje z nimi zamaskowane prawdziwe ciało, a Vitest porównuje z nimi typowane próbki TS. Ręczne dane testów, które łamią reguły serwera, dostają poprawne wartości.

### Changes Required:

#### 1. Złote pliki

**Files**: `szczur-radar-web/src/contract/run.json`, `post.json`, `failed-post.json` (nowe)

**Intent**: Jedno źródło pełnego kształtu dla obu stron.

**Contract**: każdy plik to sformatowany JSON jednego obiektu w kształcie odpowiedzi serwera. Każda wartość string w formacie UUID jest zastąpiona `"<uuid>"`, a każda wartość string w formacie ISO-8601 instant (`…T…Z`) przez `"<instant>"`. Inne wartości zostają dosłowne. Dane muszą pokazywać jak najwięcej pól z wartością, a nie `null`:
- `run.json`: przebieg zakresu zakończony `partial` z kilkoma krokami i co najmniej jednym wynikiem źródła, niepustymi `attentionReasons`, ustawionymi `startedAt`/`finishedAt`/`lastActivityAt` oraz liczbami zgodnymi z regułą serwera (`fetched`/`skipped` to sumy źródeł, `needsAttention == !attentionReasons.isEmpty()`);
- `post.json`: post z oceną, w której jest cel z kierunkiem i `claim_ids` (np. `ContractFixtures.validOutput()`);
- `failed-post.json`: post `failed` z `lastAttempt` z co najmniej jednym naruszeniem.

Seed wybiera implementujący; pole, którego żaden stan nie ustawia razem z innymi, może zostać `null`.

#### 2. Porównanie w Javie

**Files**:
- `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractFiles.java` (nowy, pomocnik testowy)
- `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java`

**Intent**: IT przypina pełne ciało każdej z trzech odpowiedzi do złotego pliku. Zmiana rekordu bez aktualizacji pliku jest czerwona.

**Contract**:
- `ContractFiles.assertMatches(String json, String fileName)`: parsuje ciało, zamienia wartości jak w kontrakcie pliku (rekurencyjnie, także w tablicach) i porównuje drzewo Jackson z drzewem pliku `Path.of("../szczur-radar-web/src/contract/" + fileName)` (jak `AlertSettingsTest.java:29-30`). Porównanie nie zależy od kolejności kluczy, ale zależy od kolejności tablic. Komunikat błędu zawiera ładnie sformatowane zamaskowane ciało, gotowe do wklejenia do pliku.
- Nowe testy, z seedem z istniejących helperów tych klas:
  - `PipelineRunControllerIT.runDetailMatchesTheSharedContract` — szczegół przebiegu;
  - `PostsApiIT.postDetailMatchesTheSharedContract` — szczegół posta;
  - `PostsApiIT.failedPostMatchesTheSharedContract` — pierwszy element listy nieudanych.

Wyrażenia regularne maskowania (UUID i instant) są stałymi w `ContractFiles`, a ich odpowiednik nie jest potrzebny po stronie TS, bo próbki TS używają samych znaczników.

#### 3. Typowane próbki i test Vitest

**Files**:
- `szczur-radar-web/src/contract/samples.ts` (nowy)
- `szczur-radar-web/src/contract/shapes.test.ts` (nowy)

**Intent**: Dowieść, że typy TS opisują dokładnie to, co serwer wysyła.

**Contract**:
- `samples.ts` eksportuje `sampleRun: Run`, `samplePost: PostItem`, `sampleFailedPost: FailedPost` jako literały obiektów typowane adnotacją, czyli z kontrolą nadmiarowych pól. Wartości są przepisane z plików JSON, ze znacznikami `<uuid>`/`<instant>` dosłownie.
- `shapes.test.ts` importuje trzy pliki JSON i dla każdej pary sprawdza `expect(sample).toEqual(json)`.
- Nagłówek komentarza w `samples.ts` mówi, że pole dodane w Javie zmienia plik JSON (IT pokazuje nowe ciało), próbkę i typ TS razem.

Próbki mogą służyć istniejącym testom jako baza (`{ ...sampleRun, … }`), ale migracja testów nie jest wymogiem.

#### 4. Ręczne dane łamiące reguły serwera

**Files**:
- `szczur-radar-web/src/views/FeedView.test.ts`
- `szczur-radar-web/src/components/PostCard.test.ts`
- `szczur-radar-web/src/components/StanceText.test.ts`
- `szczur-radar-web/src/views/RunDetailView.test.ts`
- `szczur-radar-web/src/api/pipeline.test.ts`

**Intent**: Dane testowe mają być takie, jakie serwer może wysłać.

**Contract**:
- Cele bullish/bearish/neutral/mixed dostają 1–4 `claim_ids`, a odpowiadające tezy autora istnieją w `claims`, gdy test renderuje oceny (`FeedView.test.ts:43`, `PostCard.test.ts:26`, `StanceText.test.ts:18` — według `StanceTable.java:13-18,70-73`).
- `sourceFailed` w `RunDetailView.test.ts:258-271` ma `skipped` przebiegu równe sumie źródeł i powody w kolejności serwera (`['source_failed','skipped']` albo źródło z `skipped: 0`).
- `api/pipeline.test.ts:90-96` używa statusów API: 409 dla `pipeline_busy`, 503 dla `source_not_configured`.

Asercje testów się nie zmieniają, chyba że zależą od zmienionej wartości.

#### 5. Dokumentacja

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Zapisać regułę złotych plików.

**Contract**: w `Coding Style & Testing` jeden punkt: `szczur-radar-web/src/contract/` trzyma pełne ciała `run.json`, `post.json`, `failed-post.json`, które czytają IT (`ContractFiles`) i Vitest (`samples.ts`). Nowe pole tych rekordów zmienia plik, próbkę i typ TS w tym samym PR. Plik leży w web, bo obraz web typecheckuje testy z własnego kontekstu.

### Success Criteria:

#### Automated Verification:

- Trzy nowe testy IT przechodzą: `cd szczur-radar-api && ./mvnw -B -Dit.test='PipelineRunControllerIT,PostsApiIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
- Test kształtów Vitest przechodzi: `cd szczur-radar-web && npx vitest run src/contract/shapes.test.ts`
- Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Obraz web buduje się z własnego kontekstu (typecheck testów z importem JSON): `docker build -t sr-web-contract ./szczur-radar-web && docker rmi sr-web-contract`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- Celowe zepsucie Javy (agent wykonuje i cofa): nowe pole w `RunView` daje czerwony `runDetailMatchesTheSharedContract` z nowym ciałem w komunikacie
- Celowe zepsucie TS (agent wykonuje i cofa): usunięcie pola z interfejsu `Run` w `src/api/pipeline.ts` daje błąd `vue-tsc` w `samples.ts`, a usunięcie pola z próbki daje czerwony `shapes.test.ts`

#### Manual Verification:

- Właściciel przegląda `run.json`, `post.json`, `failed-post.json` i potwierdza, że pokazują reprezentatywne dane: przebieg wymagający uwagi z wynikami źródeł, post z oceną z tezami, nieudany post z naruszeniem

**Implementation Note**: Po fazie i zielonej automatycznej weryfikacji poprosić o przegląd złotych plików (wiersz Manual), potem faza 3.

---

## Phase 3: Rejestr skopiowanych reguł

### Overview

`szczur-radar-web/src/contract/rules.json` zapisuje każdą regułę skopiowaną między Javą a SPA. Test Javy porównuje go z serwerem, test Vitest z SPA. Skan literałów pilnuje, że SPA, testy i atrapa używają tylko kodów błędów, które serwer zna. Komunikaty SPA z liczbami reguł są budowane ze stałych.

### Changes Required:

#### 1. Rejestr

**File**: `szczur-radar-web/src/contract/rules.json` (nowy)

**Intent**: Jedno miejsce, które obie strony muszą zaktualizować razem.

**Contract**: obiekt z czterema kluczami:
- `whitespace` — rosnąca lista punktów kodowych zbioru §10 (dziś 29);
- `limits` — `feedQueryMaxLength: 100`, `runRangeMaxDays: 7`, `runsPageMaxSize: 100`, `passwordMinLength: 12`, `passwordMaxLength: 128`, `maxAssessmentAttempts: 3`;
- `values` — dla każdego zbioru z `labels` w `src/labels.ts` (`itemType`, `relevance`, `direction`, `flag`, `modifier`, `claimKind`, `attribution`, `targetKind`, `reference`, `runKind`, `runStatus`, `interruptionCause`, `stepStatus`, `step`, `failureClass`, `source`, `sourceStatus`) plus `attentionReason`: lista wartości JSON w kolejności deklaracji enumu w Javie;
- `errorCodes` — kody `ErrorCode` w kolejności deklaracji.

Nazwy zbiorów są nazwami kluczy `labels`.

#### 2. Strona Javy

**Files**:
- `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/SpaRulesTest.java` (nowy, test jednostkowy bez Springa)
- `posts/PostFilter.java` albo `posts/api/PostController.java`
- `pipeline/api/PipelineRunController.java`
- `config/ErrorCode.java`

**Intent**: Serwer i rejestr nie mogą się różnić.

**Contract**:
- `SpaRulesTest` czyta `../szczur-radar-web/src/contract/rules.json` i sprawdza:
  - `whitespace` równa się zbiorowi `cp ∈ [0, 0x10FFFF]`, dla których `Whitespace.isWhitespace(cp)`;
  - każdy wpis `limits` równa się stałej Javy z jawnej mapy w teście (nazwa → stała); zbiór kluczy pliku równa się zbiorowi kluczy mapy;
  - każdy wpis `values` równa się, w kolejności, wartościom enumu z jawnej mapy w teście (nazwa → klasa enumu), serializowanym przez `JsonMapper` (respektuje `@JsonValue`); zbiór kluczy pliku równa się zbiorowi kluczy mapy;
  - `errorCodes` równa się `ErrorCode.values()` po serializacji.
- Stałe, do których test nie ma dostępu, stają się publiczne i są jedynym źródłem wartości w adnotacjach:
  - `MAX_QUERY_LENGTH = 100` dla `@Size` parametru `q` (`PostController.java:87`);
  - `PipelineRunController.MAX_RANGE_DAYS` (dziś pakietowe);
  - nowa `PipelineRunController.MAX_PAGE_SIZE = 100` dla `@Max` strony przebiegów (`:111`).

  `Passwords.MIN_LENGTH`/`MAX_LENGTH` i `Post.MAX_ASSESSMENT_ATTEMPTS` są już publiczne.
- Komentarz `ErrorCode.java:10-11` odsyła do `rules.json` i `SpaRulesTest`.

#### 3. Strona SPA — stałe i komunikaty

**Files**:
- `szczur-radar-web/src/feedFilter.ts`
- `szczur-radar-web/src/labels.ts`
- `szczur-radar-web/src/api/pipeline.ts`
- `szczur-radar-web/src/api/activation.ts` (albo inny moduł bez Vue)
- `szczur-radar-web/src/views/AdminView.vue`
- `szczur-radar-web/src/views/ActivateView.vue`
- `szczur-radar-web/src/api/posts.ts`

**Intent**: Każda kopia reguły w SPA jest eksportowaną stałą, którą test może porównać, a komunikaty nie powtarzają liczb literałem.

**Contract**:
- `WHITE_SPACE` (`feedFilter.ts:24`) i `labels` (`labels.ts:2`) są eksportowane bez zmiany wartości.
- `MAX_RUNS_PAGE_SIZE` przenosi się z `AdminView.vue:128` do `api/pipeline.ts`.
- `PASSWORD_MIN_LENGTH`/`PASSWORD_MAX_LENGTH` przenoszą się z `ActivateView.vue:14-15` do modułu TS (np. `api/activation.ts`); widoki je importują.
- Nowa `MAX_ASSESSMENT_ATTEMPTS = 3` w `api/posts.ts`.
- `startErrorMessage` (`api/pipeline.ts:172,176`) buduje „(… nieudane próby)” i „maks. … dni” z `MAX_ASSESSMENT_ATTEMPTS` i `MAX_RANGE_DAYS` (`missingDays.ts:5`). Tekst wynikowy jest bajt w bajt taki jak dziś; istniejące testy komunikatów to potwierdzą.

#### 4. Strona SPA — test rejestru i skan kodów błędów

**File**: `szczur-radar-web/src/contract/rules.test.ts` (nowy)

**Intent**: SPA i rejestr nie mogą się różnić. Atrapa i testy nie mogą używać kodu błędu, którego serwer nie zna.

**Contract**:
- **Białe znaki.** Zbiór punktów kodowych z przedziału `0..0xFFFF` pasujących do `new RegExp('[' + WHITE_SPACE + ']')` równa się `whitespace`. Test sprawdza też, że każda wartość `whitespace` ≤ 0xFFFF.
- **Limity.** Każdy wpis `limits` równa się eksportowanej stałej SPA z jawnej mapy w teście, a zbiory kluczy są równe: `MAX_QUERY_LENGTH`, `MAX_RANGE_DAYS`, `MAX_RUNS_PAGE_SIZE`, `PASSWORD_MIN_LENGTH`, `PASSWORD_MAX_LENGTH`, `MAX_ASSESSMENT_ATTEMPTS`.
- **Etykiety.** Dla każdego zbioru `labels` zbiór kluczy równa się `values[zbiór]` jako zbiorowi. Zbiór nazw `labels` równa się nazwom `values` bez `attentionReason`.
- **Kolejność filtrów.** `KINDS` i `DIRECTIONS` (`api/posts.ts:10,14`) równają się `values.targetKind` i `values.direction` w kolejności.
- **Tony.** `stepTone`, `sourceTone` i ton przebiegu dla każdej wartości `stepStatus`, `sourceStatus` i `runStatus` dają zdefiniowany `BadgeTone`.
- **Powody „wymaga uwagi”.** `attentionReasons(...)` dla każdego kodu `attentionReason` daje tekst różny od samego kodu, czyli nie fallback.
- **Unie TS.** Każdy zbiór z unią w `src/api/*.ts` (`RunStatus`, `RunKind`, `StepStatus`, `RunStepName`, `InterruptionCause`, `AttentionReason`, `SourceFetchStatus`, unie kontraktu oceny w `posts.ts`, `FailureClass`) ma w teście krotkę `as const`, którą kompilator uznaje za pełną listę unii (kontrola typów jak niżej). W runtime ta krotka równa się `values[zbiór]` jako zbiorowi. Zbiory z typem `string` (`source`) są pominięte.
- **Skan kodów błędów.** `import.meta.glob('../**/*.{ts,vue}', { query: '?raw', import: 'default', eager: true })` (bez samego pliku testu) i `../../dev/mock-api.js?raw` zbierają literały kodów w trzech postaciach: `detail: '<kod>'`, `new ApiError(<status>, '<kod>')`, `case '<kod>'` w plikach zawierających `.detail)`. Każdy zebrany kod należy do `errorCodes`. Test wymaga co najmniej 20 trafień, żeby zepsute wyrażenie nie przechodziło po cichu; dziś trafień jest więcej (19 wystąpień `internal_error` plus kody w `api/*.ts`).

Kontrola pełności unii (nieoczywista, dlatego fragment):

```ts
// Compiles only when T lists every member of U; extra members fail `satisfies`.
type Exhaustive<U, T extends readonly U[]> = [Exclude<U, T[number]>] extends [never] ? T : never
const RUN_STATUSES = ['queued', 'running', 'succeeded', 'partial', 'failed', 'interrupted'] as const satisfies readonly RunStatus[]
const runStatuses: Exhaustive<RunStatus, typeof RUN_STATUSES> = RUN_STATUSES
```

#### 5. Dokumentacja

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Zapisać regułę rejestru.

**Contract**: punkt z fazy 2 (`Coding Style & Testing`) rozszerzony o `rules.json`. Nowa wartość enumu z etykietą, nowy kod błędu, zmiana zbioru białych znaków albo limitu z rejestru zmienia plik i obie strony w tym samym PR (`SpaRulesTest`, `rules.test.ts`). Nowa kopia reguły po stronie SPA trafia do rejestru. W sekcji `UI` przy `runs.ts` dopisać, że kod `AttentionReason` bez tekstu jest czerwony w `rules.test.ts`.

### Success Criteria:

#### Automated Verification:

- Test rejestru Javy przechodzi: `cd szczur-radar-api && ./mvnw -B -Dtest=SpaRulesTest test`
- Test rejestru Vitest przechodzi: `cd szczur-radar-web && npx vitest run src/contract/rules.test.ts`
- Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Obraz web buduje się z własnego kontekstu (`?raw` atrapy i JSON w typecheck): `docker build -t sr-web-contract ./szczur-radar-web && docker rmi sr-web-contract`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- Celowe zepsucie Javy (agent wykonuje i cofa): nowa wartość w `RunStatus` daje czerwony `SpaRulesTest`; zmiana `Passwords.MIN_LENGTH` daje czerwony `SpaRulesTest`
- Celowe zepsucie SPA (agent wykonuje i cofa): usunięcie etykiety `runStatus`, tekstu powodu w `runs.ts` albo znaku z `WHITE_SPACE` daje czerwony `rules.test.ts`; `detail: 'no_such_code'` w atrapie daje czerwony skan
- CI na PR zielone: `gh pr checks --watch` kończy się bez czerwonego joba

**Implementation Note**: Po fazie i zielonej automatycznej weryfikacji zmiana jest gotowa do `/10x-impl-review`.

---

## Testing Strategy

### Unit Tests:

- `ErrorCodeTest` — tabela `forStatus`.
- `SpaRulesTest` — rejestr kontra Java, z kompletnością kluczy w obie strony.
- `shapes.test.ts` — typowane próbki kontra złote pliki.
- `rules.test.ts` — rejestr kontra SPA, pełność unii TS i skan kodów błędów.

### Integration Tests:

- `ApiErrorHttpIT` — prawdziwe ciało kontenera na wyjątek kontrolera, przy `Accept: text/html` i na ścieżce `/error` z sesją.
- `AuthIT` — logowanie przy awarii magazynu kont kończy się 500 `internal_error`.
- `PipelineRunControllerIT` i `PostsApiIT` — pełne ciała kontra złote pliki.
- `AuthorizationCompletenessIT` i `AdminRoleIT` — bez zmian, potwierdzają `/error` po zamianie kontrolera.

### Manual Testing Steps:

1. Przegląd trzech złotych plików przez właściciela (wiersz 2.8).

## Performance Considerations

Brak. `ApiErrorController` pisze kilkadziesiąt bajtów zamiast mapy Boot, a testy dokładają jeden IT na prawdziwym porcie (kontekst Springa jak w `OriginGuardHttpIT`).

## Migration Notes

Brak migracji bazy. Zmiana kształtu ciała 404/405/5xx jest zgodna z SPA, które czyta tylko `detail`. Kolejność deployu web/API jest dowolna. Wycofanie przez revert PR.

## References

- Research: `context/changes/api-contract-guard/research.md`
- Audyt: `context/changes/post-m2-audit/research.md:112-134` (K2, K3, K4)
- Wzór IT na prawdziwym porcie: `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/OriginGuardHttpIT.java:21-60`
- Wzór pliku spoza modułu: `szczur-radar-api/src/test/java/pl/szczurradar/api/alerts/AlertSettingsTest.java:29-30`
- Wzór rejestru z kompletnością: `szczur-radar-api/src/test/java/pl/szczurradar/api/persistence/ClosedSetsIT.java:77,151-245`
- Wzór skanu `?raw`: `szczur-radar-web/src/styleContract.test.ts:1-18`
- S-22 (Q-05, F1): `context/archive/2026-10-27-run-attention-single-rule/plan.md:38`
- S-25 (wspólna tabela przypadków): `context/archive/2026-10-27-alert-ping-single-rule/plan.md:16,34-50`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Kod nieoczekiwanego błędu (API)

#### Automated

- [x] 1.1 Nowy IT przechodzi: `cd szczur-radar-api && ./mvnw -B -Dit.test=ApiErrorHttpIT -Dtest=ErrorCodeTest -Dsurefire.failIfNoSpecifiedTests=false verify` — a90397f
- [x] 1.2 Testy logowania i kompletności autoryzacji przechodzą: `./mvnw -B -Dit.test='AuthIT,AuthorizationCompletenessIT,AdminRoleIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify` — a90397f
- [x] 1.3 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — a90397f
- [x] 1.4 Celowe zepsucie (agent wykonuje i cofa): usunięcie `ApiErrorController` daje czerwony `ApiErrorHttpIT` z ciałem Boot w komunikacie — a90397f
- [x] 1.5 Testy web bez zmian zielone: `cd szczur-radar-web && npm test` — a90397f

### Phase 2: Wspólny pełny kształt dużych odpowiedzi

#### Automated

- [x] 2.1 Trzy nowe testy IT przechodzą: `cd szczur-radar-api && ./mvnw -B -Dit.test='PipelineRunControllerIT,PostsApiIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify` — 8318374
- [x] 2.2 Test kształtów Vitest przechodzi: `cd szczur-radar-web && npx vitest run src/contract/shapes.test.ts` — 8318374
- [x] 2.3 Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build` — 8318374
- [x] 2.4 Obraz web buduje się z własnego kontekstu (typecheck testów z importem JSON): `docker build -t sr-web-contract ./szczur-radar-web && docker rmi sr-web-contract` — 8318374
- [x] 2.5 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — 8318374
- [x] 2.6 Celowe zepsucie Javy (agent wykonuje i cofa): nowe pole w `RunView` daje czerwony `runDetailMatchesTheSharedContract` z nowym ciałem w komunikacie — 8318374
- [x] 2.7 Celowe zepsucie TS (agent wykonuje i cofa): usunięcie pola z interfejsu `Run` w `src/api/pipeline.ts` daje błąd `vue-tsc` w `samples.ts`, a usunięcie pola z próbki daje czerwony `shapes.test.ts` — 8318374

#### Manual

- [x] 2.8 Właściciel przegląda `run.json`, `post.json`, `failed-post.json` i potwierdza, że pokazują reprezentatywne dane: przebieg wymagający uwagi z wynikami źródeł, post z oceną z tezami, nieudany post z naruszeniem

### Phase 3: Rejestr skopiowanych reguł

#### Automated

- [x] 3.1 Test rejestru Javy przechodzi: `cd szczur-radar-api && ./mvnw -B -Dtest=SpaRulesTest test` — ce7865b
- [x] 3.2 Test rejestru Vitest przechodzi: `cd szczur-radar-web && npx vitest run src/contract/rules.test.ts` — ce7865b
- [x] 3.3 Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build` — ce7865b
- [x] 3.4 Obraz web buduje się z własnego kontekstu (`?raw` atrapy i JSON w typecheck): `docker build -t sr-web-contract ./szczur-radar-web && docker rmi sr-web-contract` — ce7865b
- [x] 3.5 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — ce7865b
- [x] 3.6 Celowe zepsucie Javy (agent wykonuje i cofa): nowa wartość w `RunStatus` daje czerwony `SpaRulesTest`; zmiana `Passwords.MIN_LENGTH` daje czerwony `SpaRulesTest` — ce7865b
- [x] 3.7 Celowe zepsucie SPA (agent wykonuje i cofa): usunięcie etykiety `runStatus`, tekstu powodu w `runs.ts` albo znaku z `WHITE_SPACE` daje czerwony `rules.test.ts`; `detail: 'no_such_code'` w atrapie daje czerwony skan — ce7865b
- [x] 3.8 CI na PR zielone: `gh pr checks --watch` kończy się bez czerwonego joba — ce7865b
