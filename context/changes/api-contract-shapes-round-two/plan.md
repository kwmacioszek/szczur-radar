# S-41: kontrakt SPA ↔ API, druga runda — kształty — Implementation Plan

## Overview

S-24 (`api-contract-guard`) wprowadził pliki kontraktu w `szczur-radar-web/src/contract/` dla czterech odpowiedzi (`run.json`, `post.json`, `failed-post.json`, `freshness.json`). IT porównuje z nimi prawdziwe, zamaskowane ciało (`ContractFiles`), a Vitest typowaną próbkę (`samples.ts`, `shapes.test.ts`). Audyt po M-3 (`context/changes/post-m3-verification-audit/research.md` §2, eksperyment X3) pokazał trzy dziury:

- pozostałe kształty, które czyta SPA, nie mają żadnego powiązania z TS;
- pliki pokazują jeden zasiany przypadek, więc warianty `null` i puste listy są poza kontraktem;
- pole opcjonalne w TS (`attentionReasons?`) pozwala usunąć je z pliku i próbki bez czerwieni w 513 testach i `vue-tsc`.

S-41 domyka kontrakt. Każda odpowiedź czytana przez SPA dostaje plik sprawdzany po obu stronach. Strażnik wyprowadzany z `@Nullable` w rekordach Javy wymaga obu wariantów każdego pola nullable, a typy odpowiedzi w TS nie mają pól opcjonalnych.

## Current State Analysis

- **Pliki kontraktu i ich porównanie.** `ContractFiles.assertMatches(json, fileName)` (`szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractFiles.java:44`) maskuje UUID i instanty ISO. Kolejność kluczy nie ma znaczenia, kolejność tablic tak. Daty `yyyy-MM-dd` nie są maskowane. Wołają go `PipelineRunControllerIT:466` (`run.json`), `PostsApiIT:257` (`post.json`), `PostsApiIT:276` (`failed-post.json`, tylko `items[0]` bez koperty) i `FreshnessIT:218`.
- **Strona SPA.** `samples.ts` ma cztery typowane próbki, `shapes.test.ts` importuje cztery pliki po nazwie. Nowy plik bez próbki nie czerwieni niczego.
- **Jackson zawsze pisze każdy klucz.** Jackson 3 (`tools.jackson`) ma domyślną inkluzję `ALWAYS`. Nie ma `@JsonInclude` ani `NON_NULL`, a jedyne ustawienie to `spring.jackson.deserialization.fail-on-unknown-properties=true` (`application.properties:46`). `null` przychodzi więc zawsze jako `"x": null` i żadne pole odpowiedzi nie jest w TS „opcjonalne” w sensie API.
- **Pola opcjonalne w typach odpowiedzi TS** są tylko dwa: `Run.attentionReasons?` (`src/api/pipeline.ts:71`) i `Run.sourceResults?` (`:107`). Trzymają zgodność z API sprzed S-22/S-20: `?? []` w `src/runs.ts:95,106,111`, test „legacy” w `src/runs.test.ts:255-261`, `v-if="run.kind === 'range' && run.sourceResults"` w `RunDetailView.vue:232`. Pozostałe `?:` w `src/` to propsy komponentów, meta routera, `PostFilter` (`posts.ts:150-152`) i opcje klienta (`client.ts:34`), czyli nie odpowiedzi API.
- **Kształty bez pliku** (producent, test Javy):

| Kształt | Trasa | Producent | Pola nullable | Dziś w IT |
|---|---|---|---|---|
| User | `POST /api/auth/login`, `GET /api/auth/me` | `auth/UserResponse.java:9` | — | ścisły literał `AuthIT:102,287` |
| ActivationLookup | `POST /api/auth/activation/lookup` | `ActivationController.java:73` | — | ścisły `ActivationIT:72,206` |
| NotificationList | `GET /api/admin/notifications` | `NotificationController.java:96,105,123` | `readAt`, `run`, `run.interruptionCause`, `run.finishedAt`, `run.error` | `containsOnlyKeys` na jednym elemencie (`NotificationsIT:154-187`) |
| SourceState[] | `GET /api/admin/pipeline-runs/sources` | `SourceIngest.java:72` | — | ścisły `PipelineRunControllerIT:156-161` |
| MissingDays | `GET /api/admin/pipeline-runs/missing-days` | `PipelineRunController.java:210,220` | — | ścisły `MissingDaysIT:245-248`; brak dnia z oboma źródłami |
| Run[] (202) | `POST /api/admin/pipeline-runs` | `PipelineRunController.java:108-109` | jak `RunView` | tylko jsonPath (`PipelineRunControllerIT:170-189,283`) |
| Strony | `GET /api/posts`, `/api/admin/posts/failed`, `/api/admin/pipeline-runs` | `PostPage`, `FailedPostPage`, `RunPage` | — (koperta) | tylko jsonPath; koperty runów i nieudanych postów nieasertowane |

- **`run: null` w powiadomieniu jest dziś nieosiągalne.** `ck_notifications_run` (V9) wymaga `run_id` dla jedynego rodzaju `run_attention`, a FK bez `ON DELETE` blokuje usunięcie przebiegu. Gałąź `NotificationController.java:64` czeka na przyszłe rodzaje.
- **Brakujące warianty w istniejących plikach** (raport z badania):
  - `run.json`: `rangeFrom`, `rangeTo`, `startedAt`, `finishedAt`, `lastActivityAt`, `error`, `steps[].finishedAt`/`itemsOut` zawsze niepuste; `interruptionCause` i `requestedPostIds` zawsze `null`; `steps`, `sourceResults`, `attentionReasons` nigdy puste;
  - `failed-post.json`: `lastAttempt`, `lastTechnicalError`, `title`, `body`, `webUrl` zawsze niepuste; `stage` tylko `failed`;
  - `post.json`: `title`, `body`, `author`, `webUrl` zawsze niepuste; `modifiers` i `flags` zawsze `[]`.
- **Adnotacje.** Pola nullable w rekordach odpowiedzi mają `org.jspecify.annotations.Nullable` (`TYPE_USE`, czytelne przez `RecordComponent.getAnnotatedType()`). Rekordy kontraktu oceny (`agents/assessor/contract/`) w części używają `@JsonProperty` (`Item`, `Context`, `AssessmentInputV1`).
- **Trasy.** `AuthorizationCompletenessIT.routes(RequestMappingHandlerMapping)` (`:123`) wylicza każdą zmapowaną trasę. Większość handlerów zwraca `ResponseEntity<?>`, więc typu ciała nie da się wyprowadzić z sygnatury. SPA nie czyta `/api/health`, `/api/ready`, `/api/deploy-safety` ani `/api/admin/llm/check`.

### Key Discoveries:

- `ContractFiles.java:44-81`: jedno porównanie drzewa z maską. Wystarcza dla każdego nowego pliku bez zmian.
- `AuthorizationCompletenessIT.java:123-139`: wzorzec wyliczania tras do ponownego użycia.
- `PostColumnsIT` i `ClosedSetsIT`: wzorzec strażnika z dwóch stron (zbiór z kodu ↔ rejestr) z listą wyjątków z powodem.
- `rules.test.ts:222-249`: wzorzec skanu tekstu źródeł SPA z minimum trafień, żeby skan nie przechodził po cichu.
- `NotificationsIT` nie jest `@Transactional` i tworzy własnych adminów; `MissingDaysIT` ma zegar 2026-10-24, więc daty w pliku są stabilne.
- `AuthIT` i `ActivationIT` tworzą adresy z licznika (`newEmail()`). Plik kontraktu potrzebuje stałego adresu konta zasianego tylko dla testu kontraktu.

## Desired End State

- Każda trasa `/api/**` zwracająca JSON, który czyta SPA, ma w `ContractRegistry` (test Javy) swój plik albo pliki w `szczur-radar-web/src/contract/` i typ rekordu Javy. Każda inna trasa jest tam na liście wyjątków z powodem. Nowa trasa bez wpisu daje czerwony `ContractRoutesIT`.
- Dla każdego rekordu osiągalnego z rejestru `ContractVariantsTest` wymaga, żeby każde pole `@Nullable` było `null` w co najmniej jednym pliku i nie-`null` w co najmniej jednym, a każde pole listy było niepuste przynajmniej raz. Wyjątki mają powód, a wyjątek, który przestał być potrzebny, też jest czerwony.
- Każdy plik `contract/*.json` (poza `rules.json`) ma typowaną próbkę w `samples.ts`. `shapes.test.ts` wymaga próbki dla każdego pliku i porównuje je `toEqual`.
- Typy odpowiedzi w `src/api/*.ts` nie mają `?:`. Skan w Vitest odrzuca nowe `?:` poza listą dozwolonych (`PostFilter`, opcje `apiFetch`).
- Powtórzenie X3 (usunięcie `attentionReasons` z plików i próbek) daje błąd `vue-tsc`, a przywrócenie `?:` daje czerwony `npm test`.

## What We're NOT Doing

- **Atrapy** (`dev/mock-api.js`, atrapa `pipeline-trigger`, stuby `deploy-gate`): S-43. Atrapa już wysyła oba pola, które stają się wymagane, więc S-41 jej nie zmienia.
- **Rejestr reguł** (`err.detail === '<kod>'`, limit rozmiaru strony, enumy spoza `rules.json`, warunek `skipped` w `runs.ts`): S-42.
- **`vue-tsc` w `npm test` albo w jobie testów web w CI.** Typy dalej sprawdza `npm run build` i build obrazu web w CI (S-42 bierze to pod uwagę).
- **Zmiana zachowania API.** Nic nie zmienia, kiedy API wysyła `null`. `run: null` zostaje gałęzią na przyszłe rodzaje powiadomień.
- **`/internal/**`**: czyta go cron, nie SPA. Atrapa crona to S-43.
- **Odwrotny kierunek nullability** (TS `| null` tam, gdzie Java nigdy nie wysyła `null`): roadmapa tego nie wymaga.
- OpenAPI, generator klienta, walidacja odpowiedzi w runtime.
- Strażnik, że IT porównuje z plikiem ciało właściwej trasy. Rejestr mówi „trasa → plik”, a wywołanie `assertMatches` w IT jest grep-owalne.

## Implementation Approach

Mechanizm z S-24 zostaje: złoty plik w kontekście obrazu web, maskowane porównanie w IT, typowana próbka i `toEqual` w Vitest. S-41 rozszerza go w trzech krokach:

1. **Nowe kształty** dostają pliki, IT i próbki. `shapes.test.ts` wylicza pliki sam (`import.meta.glob`), więc pilnuje, że żaden nie zostaje bez próbki.
2. **Pełne strony list** niosą warianty `null` w kilku elementach jednego ciała. Dwa opcjonalne pola stają się wymagane, a skan pilnuje, żeby `?:` nie wróciło.
3. **Strażnicy wyprowadzani z kodu:**
   - rejestr tras i typów;
   - test wariantów czytający `@Nullable` z rekordów i drzewa JSON plików;
   - IT kompletności tras.

   Faza kończy się wpisem w `REPOSITORY_GUIDELINES.md`.

Strażnik idzie na koniec, bo wymaga plików z fazy 1 i 2. W fazie 3 dosiewa się już tylko warianty, które on jeszcze wskaże, albo dopisuje wyjątki z powodem.

## Critical Implementation Details

- **Izolacja danych (S-30).** Pełne ciało strony ma dokładne `total` i elementy. Test kontraktu strony musi widzieć tylko dane, które sam zasiał. `PostsApiIT` ma to przez `@Transactional`. W `PipelineRunControllerIT` test strony przebiegów najpierw kasuje powiadomienia i przebiegi, bo ta klasa nie sprząta między testami, a `PostSeeds` wstawia własny przebieg (szczegóły w fazie 2, §2). Inaczej plik zacznie zależeć od kolejności testów, czyli od tej samej wady, którą usunął S-30.
- **Stałe wartości w plikach.** Maska obejmuje tylko UUID i instanty. E-mail, daty `yyyy-MM-dd`, liczniki i teksty błędów muszą być w teście stałe: stały adres konta testu kontraktu, zegar `MissingDaysIT`, jawne `page`/`size` w zapytaniu.
- **`run: null` z serializacji.** Wariant powstaje w IT przez wstrzyknięty `JsonMapper` aplikacji (ten sam, którego używa MVC), nie przez `JsonMapper.builder().build()`. Dowodzi wtedy serializacji tak, jak zrobi ją kontroler.

## Phase 1: Nowe kształty po obu stronach

### Overview

Każda odpowiedź czytana przez SPA, której dziś nie ma w kontrakcie (poza stronami list), dostaje plik, porównanie w IT i typowaną próbkę. `shapes.test.ts` przestaje znać pliki po nazwie: wylicza je i wymaga próbki dla każdego.

### Changes Required:

#### 1. Pliki kontraktu

**File**: `szczur-radar-web/src/contract/` — nowe `user.json`, `activation-lookup.json`, `notifications.json`, `notifications-without-run.json`, `sources.json`, `missing-days.json`, `started-runs.json`

**Intent**: Pełne, zamaskowane ciała odpowiedzi, wygenerowane z komunikatu błędu `ContractFiles` i przeformatowane w stylu istniejących plików (wcięcie 2 spacje).

**Contract**:

| Plik | Ciało | Co musi zawierać |
|---|---|---|
| `user.json` | `login` i `me` (ten sam kształt) | stałe konto testu kontraktu |
| `activation-lookup.json` | lookup | stały adres konta |
| `notifications.json` | `GET /api/admin/notifications` | `unread` > 0; element nieprzeczytany (`readAt: null`) i przeczytany; jeden przebieg z `interruptionCause` i `error`, jeden z oboma `null` |
| `notifications-without-run.json` | ten sam kształt | jeden element z `run: null` |
| `sources.json` | lista źródeł | jak dziś: Wykop skonfigurowany, Reddit nie |
| `missing-days.json` | dni bez danych | dzień z brakiem obu źródeł i dzień z brakiem jednego |
| `started-runs.json` | 202 startu zakresu z resztkami | `[retry, range]` (kolejność z `PipelineRunService.java:185`) w stanie kolejkowanym: `startedAt`/`finishedAt`/`lastActivityAt` `null`, `steps: []`, `sourceResults: []`, `attentionReasons: []`; retry z `rangeFrom`/`rangeTo` `null` i `sources: []` |

#### 2. Porównania w IT

**File**: `AuthIT`, `ActivationIT`, `NotificationsIT`, `PipelineRunControllerIT`, `MissingDaysIT` (pakiety jak dziś)

**Intent**: Jeden test kontraktu na plik, wołający `ContractFiles.assertMatches`. Istniejące ścisłe literały zostają tam, gdzie sprawdzają zachowanie, a nie kształt.

**Contract**:
- `AuthIT`: konto ze stałym adresem (np. `contract@szczur.test`, rola `admin`), logowanie i `me` porównane z `user.json`.
- `ActivationIT`: link dla stałego adresu, lookup porównany z `activation-lookup.json`.
- `NotificationsIT`:
  - zasiane dwa przebiegi (z przyczyną i błędem, i bez nich), jedno powiadomienie oznaczone jako przeczytane, lista porównana z `notifications.json`;
  - osobny test serializuje `NotificationList(1, [NotificationItem(…, readAt null, run null)])` wstrzykniętym `JsonMapper` i porównuje z `notifications-without-run.json`. Komentarz w teście wyjaśnia, że schemat (V9) nie pozwala dziś zasiać tego stanu.
- `PipelineRunControllerIT`: `sources.json` (zastępuje literał `:156-161`) i `started-runs.json` (start zakresu, który zwraca najpierw retry, potem range; seed z resztkami jak dziś w `:164-189`).
- `MissingDaysIT`: scenariusz z dniem bez obu źródeł (Reddit skonfigurowany w tym teście tak jak w `aPartialRunCoversOnlyTheSourcesThatSucceeded`), ciało porównane z `missing-days.json`.

#### 3. Próbki i wyliczanie plików w Vitest

**File**: `szczur-radar-web/src/contract/samples.ts`, `szczur-radar-web/src/contract/shapes.test.ts`

**Intent**: Typowana próbka na każdy nowy plik oraz test, który sam znajduje pliki, więc plik bez próbki (albo próbka bez pliku) jest czerwony.

**Contract**:
- `samples.ts` eksportuje nazwane, typowane stałe (`User`, `ActivationLookup`, `NotificationList`, `SourceState[]`, `MissingDays`, `Run[]`) oraz mapę `SAMPLES: Record<string, unknown>` nazwa pliku → próbka. Adnotacja typu zostaje na stałej, więc nadmiarowe pole łapie `vue-tsc`.
- `shapes.test.ts`:
  - `import.meta.glob('./*.json', { eager: true, import: 'default' })` bez `rules.json`;
  - test równości zbiorów nazw (pliki ↔ klucze `SAMPLES`);
  - `toEqual` dla każdej pary (`it.each`).
- `fetchRuns`, `startRange` i `retryPosts` dalej typowane jako `Run`/`Run[]`. Próbka `started-runs` używa typu `Run[]`.

#### 4. Dokumentacja

**File**: `REPOSITORY_GUIDELINES.md` (punkt o `szczur-radar-web/src/contract/`)

**Intent**: Lista plików kontraktu przestaje być wyliczana z nazwy. Punkt mówi, że każdy plik ma próbkę i że `shapes.test.ts` to wymusza. Pełna reguła rejestru przychodzi w fazie 3.

### Success Criteria:

#### Automated Verification:

- Nowe testy kontraktu IT przechodzą: `cd szczur-radar-api && ./mvnw -B -Dit.test='AuthIT,ActivationIT,NotificationsIT,PipelineRunControllerIT,MissingDaysIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
- Test kształtów Vitest przechodzi: `cd szczur-radar-web && npx vitest run src/contract/shapes.test.ts`
- Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- Celowe zepsucie (agent wykonuje i cofa): nowy plik `contract/extra.json` bez próbki daje czerwony `shapes.test.ts`; nowe pole w `UserResponse` daje czerwony test kontraktu w `AuthIT` z nowym ciałem w komunikacie

---

## Phase 2: Pełne strony z wariantami i wymagane pola TS

### Overview

Trzy strony list są porównywane całym ciałem razem z kopertą, a ich elementy niosą warianty `null` rekordów `RunView`, `PostItem` i `FailedPost`. `attentionReasons` i `sourceResults` stają się wymagane, ścieżki zgodności ze starym API znikają, a skan pilnuje, żeby `?:` nie wróciło do typów odpowiedzi.

### Changes Required:

#### 1. Pliki stron

**File**: `szczur-radar-web/src/contract/` — nowe `posts-page.json`, `failed-posts-page.json`, `runs-page.json`; usunięty `failed-post.json`

**Intent**: Jedno pełne ciało strony na listę, z jawnym `page`/`size` w zapytaniu i kilkoma elementami dobranymi pod warianty.

**Contract**:
- `posts-page.json` (`GET /api/posts?page=0&size=…`): post z pełną oceną modelu z niepustymi `modifiers` i `flags`, wszystkimi tekstami i `webUrl`; post z `title`, `author` i `webUrl` `null` (np. komentarz albo wpis bez tytułu); post z oceną `prefilter` i `body: null`. `assessment: null` jest nieosiągalne (`PostController.java:143`) i w fazie 3 trafia do wyjątków.
- `failed-posts-page.json` (`GET /api/admin/posts/failed?page=0&size=…`): `failed` z ostatnią próbą z naruszeniem i bez błędu technicznego (`lastTechnicalError: null`); drugi `failed`, którego ostatnia próba ma `provider` i `model` `null` (`UPDATE assessments` w seedzie, bo `PostSeeds.failed` zawsze ustawia gemini); `dead`; `stored` wstrzymany (`onHold: true`) z `lastAttempt: null` i niepustym `lastTechnicalError`. Zastępuje porównanie samego `items[0]` (`PostsApiIT:276`).
- `runs-page.json` (`GET /api/admin/pipeline-runs?page=0&size=…`): przebieg zakresu `partial` jak w `run.json`; retry przerwany z `interruptionCause` i krokiem z `error` oraz `finishedAt`/`itemsOut` `null`; przebieg `posts` z `requestedPostIds`, `rangeFrom`/`rangeTo` `null` i `sourceResults: []`.

#### 2. Porównania w IT

**File**: `PostsApiIT`, `PipelineRunControllerIT`

**Intent**: Seed na test kontraktu strony widzi tylko własne dane (patrz Critical Implementation Details). Ciało porównane z plikiem. Test `failed-post.json` zastąpiony testem strony.

**Contract**:
- seedy przez istniejące `PostSeeds` i helpery przebiegów (`PipelineRunControllerIT:438` dla `run.json`); stałe teksty błędów i liczniki;
- `PostsApiIT` jest `@Transactional` (`:61`), więc strony postów i nieudanych postów są izolowane same z siebie;
- `PipelineRunControllerIT` nie jest `@Transactional` i nie sprząta przebiegów między testami: `@AfterEach` (`:143-153`) tylko czeka na bezczynność, każdy `start()` zostawia swoje przebiegi, a konstruktor `PostSeeds` w `setUp` wstawia przebieg retry `succeeded` (`PostSeeds.java:65-67`). Test `runs-page.json` zaczyna więc od usunięcia wszystkich powiadomień i przebiegów, w kolejności FK `notifications` → `pipeline_runs` (wzorzec `:334-335`). Potem sieje swoje trzy przebiegi, a przebieg retry z `PostSeeds` zostaje świadomie jako czwarty element strony (`succeeded` retry z pełnym czasem i pustymi listami). Inne testy tej klasy nie mogą zależeć od przebiegów z wcześniejszych testów: dziś nie zależą, bo test listy używa `created_at` 2099 i asercji po id (`:288-337`).

#### 3. Wymagane pola w TS i usunięcie zgodności ze starym API

**File**: `szczur-radar-web/src/api/pipeline.ts`, `src/runs.ts`, `src/views/RunDetailView.vue`, testy (`runs.test.ts`, `RunDetailView.test.ts`, `AdminView.test.ts`)

**Intent**: `Run.attentionReasons` i `Run.sourceResults` są wymagane, bo API wysyła je zawsze. Kod nie zakłada już API sprzed S-20/S-22.

**Contract**:
- w `pipeline.ts` usunięte `?` i zdania „Optional: an API from before…”;
- w `runs.ts` usunięte `?? []`; tekst zastępczy dla `needsAttention` bez powodów zostaje dla pustej listy;
- usunięte oba przypadki zgodności ze starym API (`delete legacy.attentionReasons`, który przy wymaganym polu się nie skompiluje): `runs.test.ts:257` i `RunDetailView.test.ts:445`; w `runs.test.ts` zostaje przypadek pustej listy;
- `RunDetailView.vue:232` warunkuje sekcję tylko rodzajem przebiegu;
- bazowe fabryki `run()` w `runs.test.ts:32`, `AdminView.test.ts:30` i `RunDetailView.test.ts:51` dostają `sourceResults: []`. W `RunDetailView.test.ts` każdy przebieg range wyświetli wtedy „Jeszcze żadne źródło nie zakończyło pobrania.” (`RunDetailView.vue:237`), więc asercje tekstu tej strony trzeba przejrzeć;
- `KitchenSinkView.vue` i `dev/mock-api.js` zostają bez zmian. KitchenSink nie buduje `Run`, a atrapa ma tylko przebiegi range z `attentionReasons` i `sourceResults` (`nightly()`, `:112-128`).

#### 4. Skan pól opcjonalnych w typach odpowiedzi

**File**: `szczur-radar-web/src/contract/shapes.test.ts` (albo osobny `responseTypes.test.ts` w `src/contract/`)

**Intent**: Pole opcjonalne w typie odpowiedzi to dziura w kontrakcie (X3), bo API zawsze wysyła każdy klucz. Skan łapie ją w `npm test`, bez czekania na `vue-tsc`.

**Contract**:
- czyta źródła `src/api/*.ts` bez `*.test.ts` (`import.meta.glob(…, { query: '?raw', import: 'default', eager: true })`);
- każda linia deklaracji `nazwa?:` musi być na liście dozwolonych `{ 'posts.ts': ['q', 'kind', 'direction'], 'client.ts': ['handleUnauthorized'] }` z komentarzem, dlaczego to nie są odpowiedzi API;
- test wymaga też, żeby każdy wpis listy dozwolonych został znaleziony (minimum trafień, jak `rules.test.ts`), więc skan nie przechodzi po cichu przy zmianie formatu.

#### 5. Próbki

**File**: `szczur-radar-web/src/contract/samples.ts`

**Intent**: Próbki `Page<PostItem>`, `Page<FailedPost>`, `Page<Run>` w mapie `SAMPLES`; `sampleFailedPost` usunięta razem z plikiem.

### Success Criteria:

#### Automated Verification:

- Testy stron IT przechodzą: `cd szczur-radar-api && ./mvnw -B -Dit.test='PostsApiIT,PipelineRunControllerIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
- Testy kształtów i skan przechodzą: `cd szczur-radar-web && npx vitest run src/contract/`
- Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Obraz web buduje się z własnego kontekstu (typecheck testów z `import.meta.glob`): `docker build -t sr-web-contract ./szczur-radar-web && docker rmi sr-web-contract`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- Powtórzenie X3 (agent wykonuje i cofa): usunięcie `attentionReasons` ze wszystkich plików z `Run` i ich próbek daje błąd `vue-tsc` w `npm run build`; przywrócenie `attentionReasons?:` w `pipeline.ts` daje czerwony skan w `npm test`
- Atrapa nadal działa (agent): `npm run dev` z `dev/mock-api.js`, `playwright-cli` otwiera `/admin` i stronę przebiegu range z atrapy (`/admin/runs/r-source-failed`), w konsoli nie ma błędów

#### Manual Verification:

- Właściciel przegląda `posts-page.json`, `failed-posts-page.json` i `runs-page.json` i potwierdza, że elementy pokazują realistyczne warianty (komentarz bez tytułu, post wstrzymany, przerwany retry)

---

## Phase 3: Strażnicy wyprowadzani z kodu

### Overview

Kompletność kontraktu przestaje zależeć od pamięci. Rejestr mówi, która trasa ma który plik i typ. Test wariantów czyta `@Nullable` i listy z rekordów, a IT tras sprawdza, że każda trasa `/api/**` jest w rejestrze albo na liście wyjątków.

### Changes Required:

#### 1. Rejestr kontraktu

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractRegistry.java`

**Intent**: Jedno miejsce, które wiąże trasę z plikami i typem Javy. Typ jest jawny, bo handlery zwracają `ResponseEntity<?>`.

**Contract**:
- `SHAPES`: trasa (metoda + wzorzec, jak w `Route` z §3) → lista `(plik, typ)`. Typ to `java.lang.reflect.Type`, żeby wyrazić `List<RunView>` i `List<SourceState>` (np. przez parametryzowany typ z pomocnika).
- **Widoczność (przegląd planu, F1).** Z pakietu `contract` jest dostępny tylko `SourceIngest.SourceState`. Pozostałe rekordy odpowiedzi i ich kontrolery są package-private (`PostController`, `FailedPostController`, `NotificationController`, `FreshnessController`, `UserResponse`, `ActivationController.LookupResponse`, rekordy w `PipelineRunController`). Rejestr wskazuje je więc nazwą binarną (`"pl.szczurradar.api.posts.api.PostController$PostItem"`) rozwiązywaną przez `Class.forName` w statycznym inicjalizatorze. Nieznana nazwa przerywa test od razu z nazwą, której brakuje. Kod produkcyjny nie zmienia widoczności. Odczyt `getRecordComponents()` i adnotacji nie wymaga `setAccessible`.
- `NOT_READ_BY_SPA`: trasa → powód. Wpisy: `/api/health`, `/api/ready`, `/api/deploy-safety` (czyta CI), `/api/admin/llm/check` (narzędzie operatora), `/api/posts/{id}/raw` (tekst, nie kształt) oraz trasy 204 bez ciała (`logout`, `activation`, oba `read` powiadomień).

#### 2. Strażnik wariantów

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractVariantsTest.java` (unit, bez Springa)

**Intent**: Dla każdego rekordu osiągalnego z typów w rejestrze pole `@Nullable` musi mieć w plikach obie wartości, a pole listy przynajmniej jeden niepusty przypadek. To pilnuje, że warianty i kształt elementów są w kontrakcie.

**Contract**:
- Przechodzi typ rekurencyjnie:
  - rekord → komponenty; nazwa w JSON to `@JsonProperty` albo nazwa komponentu;
  - `List<X>` → `X`;
  - liście: enum, `String`, `UUID`, `Instant`, `LocalDate`, liczby, `boolean`.
- Dla każdego wystąpienia pary (rekord, komponent) zbiera wartości ze wszystkich plików, w których ten rekord występuje: po wszystkich elementach tablic i na każdej głębokości. Wariant w dowolnym pliku liczy się dla rekordu.
- Reguły:
  - `@Nullable` (`getAnnotatedType().isAnnotationPresent(Nullable.class)`) → ≥1 `null` i ≥1 nie-`null`;
  - `List` → ≥1 niepusta.
- `KNOWN_GAPS`: `Rekord.komponent` → powód. Wpis, którego reguła jest już spełniona, jest błędem (lista nie gnije). Przewidywane wpisy, każdy sprawdzony w implementacji:
  - `PostItem.assessment`: post oceniony bez oceny `ok` „nie powinien się zdarzyć”, `PostController.java:143`;
  - `FailedPost.LastAttempt.failureClass`: `ck_assessments_outcome` wymaga klasy dla nieudanej próby;
  - `NotifiedRun.finishedAt`: powiadomienie powstaje dopiero na końcu przebiegu, `RunNotifications.runEnded`.

  `LastAttempt.provider` i `model` nie są wyjątkami. Kolumny są NULL-owalne (`V3__create_pipeline_and_posts.sql:143-144`), a `Provenance` MODEL ich nie wymaga, więc dostają wariant `null` w `failed-posts-page.json` (faza 2, przegląd planu F5).
- Strażnik nie może przechodzić po cichu. Test wymaga, żeby przejście znalazło znane pola nullable, np. `RunView.rangeFrom`, `NotificationItem.run` i `PostItem.title`.
- Komunikat błędu podaje rekord, komponent, brakujący wariant i pliki, w których rekord występuje.

#### 3. Kompletność plików i tras

**File**: `ContractVariantsTest` (pliki), nowy `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractRoutesIT.java`, nowy publiczny helper `szczur-radar-api/src/test/java/pl/szczurradar/api/web/MappedRoutes.java`, a także `AuthorizationCompletenessIT` i `AdminRoleIT`

**Intent**: Każdy plik `contract/*.json` poza `rules.json` jest w rejestrze, a każda zmapowana trasa `/api/**` jest w `SHAPES` albo w `NOT_READ_BY_SPA`.

**Contract**:
- unit: zbiór plików w katalogu (bez `rules.json`) = zbiór plików w `SHAPES`;
- `AuthorizationCompletenessIT.routes(…)` i rekord `Route` (`:123-142`) są package-private w pakiecie `auth`, więc z `contract` ich nie widać (przegląd planu, F1). Osobny commit refactor bez zmiany zachowania przenosi je do publicznej klasy testowej `web/MappedRoutes` (`routes(RequestMappingHandlerMapping)`, `Route` z `key()` i `concretePath()`). `AuthorizationCompletenessIT` i `AdminRoleIT` przechodzą na nią, a oba IT dalej są zielone. Z tej samej klasy skorzysta S-40. `routes()` zwraca wszystko, także `/error` (5 metod) i `/internal/**`, więc filtr `/api/**` jest po stronie wołającego;
- IT: `MappedRoutes.routes(handlerMapping)` ograniczone do `/api/**` = klucze `SHAPES` ∪ `NOT_READ_BY_SPA`, w obie strony (wpis bez trasy też jest błędem). `/internal/**` i `/error` są poza zakresem, bo SPA ich nie czyta.

#### 4. Dosianie brakujących wariantów

**File**: IT i pliki z faz 1–2

**Intent**: Strażnik uruchomiony pierwszy raz wskaże warianty, których seedy jeszcze nie mają. Dosiewamy je w istniejących plikach, a wariant nieosiągalny trafia do `KNOWN_GAPS` z powodem.

#### 5. Dokumentacja

**File**: `REPOSITORY_GUIDELINES.md` (punkt o `src/contract/`)

**Intent**: Reguła dla następnego wycinka:
- nowa trasa czytana przez SPA dostaje plik, próbkę i wpis w `ContractRegistry`; inna trasa dostaje wpis w `NOT_READ_BY_SPA` z powodem;
- nowe pole `@Nullable` albo lista dostaje wariant w pliku albo wpis w `KNOWN_GAPS`;
- typy odpowiedzi w `src/api/` nie mają `?:`.

### Success Criteria:

#### Automated Verification:

- Strażnik wariantów przechodzi: `cd szczur-radar-api && ./mvnw -B -Dtest=ContractVariantsTest test`
- IT tras przechodzi: `cd szczur-radar-api && ./mvnw -B -Dit.test=ContractRoutesIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Celowe zepsucie wariantu (agent wykonuje i cofa): zamiana `rangeFrom: null` na wartość we wszystkich plikach z `RunView` daje czerwony `ContractVariantsTest` z nazwą `RunView.rangeFrom`; wpis w `KNOWN_GAPS` dla pola, które ma oba warianty, też jest czerwony
- Celowe zepsucie trasy (agent wykonuje i cofa): nowy `@GetMapping` pod `/api/admin/` bez wpisu w rejestrze daje czerwony `ContractRoutesIT`
- Celowe zepsucie pliku (agent wykonuje i cofa): plik `contract/extra.json` bez wpisu w `SHAPES` daje czerwony `ContractVariantsTest`

---

## Testing Strategy

### Unit Tests:

- `ContractVariantsTest`:
  - przejście po typach z rejestru, reguły `@Nullable` i list;
  - `KNOWN_GAPS` bez wpisów zbędnych;
  - równość zbioru plików z rejestrem;
  - minimum znalezionych pól.
- Vitest:
  - `shapes.test.ts`: równość zbioru plików i próbek, `toEqual` każdej pary;
  - skan `?:` z minimum trafień;
  - `runs.test.ts` bez przypadku zgodności ze starym API.

### Integration Tests:

- Jeden test kontraktu na plik w IT, które już obsługują daną trasę. Każdy widzi tylko własne dane.
- `ContractRoutesIT`: trasy `/api/**` ↔ rejestr w obie strony.

### Manual Testing Steps:

1. Właściciel przegląda pliki stron z fazy 2 (wiersz Manual 2.8).

## Performance Considerations

Kilka nowych testów w istniejących klasach IT i jeden nowy IT z kontekstem Springa (współdzielonym przez cache kontekstów, jeśli konfiguracja jest ta sama co w `AuthorizationCompletenessIT`). Strażnik wariantów jest testem jednostkowym na kilkunastu plikach.

## Migration Notes

Brak migracji bazy. Web i API wdrażają się niezależnie. Usunięcie zgodności z API sprzed S-20/S-22 jest bezpieczne, bo produkcja ma oba od dawna.

## References

- Audyt: `context/changes/post-m3-verification-audit/research.md` (§2, X3, „Materiał dla M-4” p. 3)
- Poprzednia runda: `context/archive/2026-10-29-api-contract-guard/plan.md`, `plan-brief.md`
- Roadmapa: `context/foundation/roadmap.md`, S-41; issue #151
- `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractFiles.java:44-81`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/auth/AuthorizationCompletenessIT.java:123-139`
- `szczur-radar-web/src/contract/rules.test.ts:222-249` (wzorzec skanu z minimum trafień)
- `szczur-radar-web/src/api/pipeline.ts:71,107`, `src/runs.ts:95-111`, `src/views/RunDetailView.vue:232`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/api/NotificationController.java:64,96-125`; `db/migration/V9__create_notifications.sql`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Nowe kształty po obu stronach

#### Automated

- [x] 1.1 Nowe testy kontraktu IT przechodzą: `cd szczur-radar-api && ./mvnw -B -Dit.test='AuthIT,ActivationIT,NotificationsIT,PipelineRunControllerIT,MissingDaysIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify` — 0176413
- [x] 1.2 Test kształtów Vitest przechodzi: `cd szczur-radar-web && npx vitest run src/contract/shapes.test.ts` — 0176413
- [x] 1.3 Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build` — 0176413
- [x] 1.4 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — 0176413
- [x] 1.5 Celowe zepsucie (agent wykonuje i cofa): nowy plik `contract/extra.json` bez próbki daje czerwony `shapes.test.ts`; nowe pole w `UserResponse` daje czerwony test kontraktu w `AuthIT` z nowym ciałem w komunikacie — 0176413

### Phase 2: Pełne strony z wariantami i wymagane pola TS

#### Automated

- [x] 2.1 Testy stron IT przechodzą: `cd szczur-radar-api && ./mvnw -B -Dit.test='PostsApiIT,PipelineRunControllerIT' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify` — 17ba4ba
- [x] 2.2 Testy kształtów i skan przechodzą: `cd szczur-radar-web && npx vitest run src/contract/` — 17ba4ba
- [x] 2.3 Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build` — 17ba4ba
- [x] 2.4 Obraz web buduje się z własnego kontekstu (typecheck testów z `import.meta.glob`): `docker build -t sr-web-contract ./szczur-radar-web && docker rmi sr-web-contract` — 17ba4ba
- [x] 2.5 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — 17ba4ba
- [x] 2.6 Powtórzenie X3 (agent wykonuje i cofa): usunięcie `attentionReasons` ze wszystkich plików z `Run` i ich próbek daje błąd `vue-tsc` w `npm run build`; przywrócenie `attentionReasons?:` w `pipeline.ts` daje czerwony skan w `npm test` — 17ba4ba
- [x] 2.7 Atrapa nadal działa (agent): `npm run dev` z `dev/mock-api.js`, `playwright-cli` otwiera `/admin` i stronę przebiegu range z atrapy (`/admin/runs/r-source-failed`), w konsoli nie ma błędów — 17ba4ba

#### Manual

- [ ] 2.8 Właściciel przegląda `posts-page.json`, `failed-posts-page.json` i `runs-page.json` i potwierdza, że elementy pokazują realistyczne warianty (komentarz bez tytułu, post wstrzymany, przerwany retry)

### Phase 3: Strażnicy wyprowadzani z kodu

#### Automated

- [x] 3.1 Strażnik wariantów przechodzi: `cd szczur-radar-api && ./mvnw -B -Dtest=ContractVariantsTest test`
- [x] 3.2 IT tras przechodzi: `cd szczur-radar-api && ./mvnw -B -Dit.test=ContractRoutesIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
- [x] 3.3 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- [x] 3.4 Całe testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- [x] 3.5 Celowe zepsucie wariantu (agent wykonuje i cofa): zamiana `rangeFrom: null` na wartość we wszystkich plikach z `RunView` daje czerwony `ContractVariantsTest` z nazwą `RunView.rangeFrom`; wpis w `KNOWN_GAPS` dla pola, które ma oba warianty, też jest czerwony
- [x] 3.6 Celowe zepsucie trasy (agent wykonuje i cofa): nowy `@GetMapping` pod `/api/admin/` bez wpisu w rejestrze daje czerwony `ContractRoutesIT`
- [x] 3.7 Celowe zepsucie pliku (agent wykonuje i cofa): plik `contract/extra.json` bez wpisu w `SHAPES` daje czerwony `ContractVariantsTest`
