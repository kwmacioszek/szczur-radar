# S-42: rejestr reguł SPA, druga runda — Implementation Plan

## Overview

`szczur-radar-web/src/contract/rules.json` to rejestr reguł skopiowanych między API a SPA. `SpaRulesTest` (Java) porównuje go z serwerem, a `rules.test.ts` (Vitest) z SPA. Audyt po M-3 (`context/changes/post-m3-verification-audit/research.md` §1 R-22, R-43, eksperyment X5) wskazał krawędzie, przez które dryf przechodzi bez czerwieni:

- porównanie `err.detail === '<kod>'` z literówką przechodzi przez skan kodów błędów i przez `vue-tsc` (X5), bo `ApiError.detail` jest typu `string | null`;
- limit rozmiaru strony jest w rejestrze tylko jako „limit przebiegów”, a rozmiary feedu i listy nieudanych postów oraz atrapa nie są z nim związane;
- enumy w odpowiedziach czytanych przez SPA są poza rejestrem, a jego kompletność zależy od ręcznej mapy w `SpaRulesTest`;
- SPA sama liczy warunek powodu `skipped` w `src/runs.ts` (R-22).

S-42 zamyka każdą z tych krawędzi testem albo kompilatorem. Każda faza kończy się celowym zepsuciem, które ma dać czerwień.

## Current State Analysis

- **Kody błędów.** `ApiError.detail: string | null` (`szczur-radar-web/src/api/client.ts:7`). Porównują go dwa `switch`: `startErrorMessage` (`src/api/pipeline.ts:169`) i `activationFailure` (`src/api/activation.ts:45`). Skan w `src/contract/rules.test.ts:222-249` rozpoznaje trzy formy: `detail: '<kod>'`, `new ApiError(<status>, '<kod>')` i `case '<kod>'` w pliku z `.detail)`. Nie rozpoznaje `=== / !==` ani `expect(….detail).toBe('<kod>')`.
- **Typy w CI.** `vue-tsc -b` uruchamia `npm run build` (`package.json:8`) w budowie obrazu web (`Dockerfile:11`, job `web-container-smoke` w `.github/workflows/ci.yml`). Ten job jest w `needs:` przed `railway-config`, więc błąd kompilatora blokuje deploy. Job `web-unit` uruchamia tylko `npm test` (`vitest run`), bez typów. `tsconfig.app.json` obejmuje `src/**/*.ts`, a więc i testy.
- **Limit strony.** Wszystkie trzy listy mają te same granice z `config/PageLimits` (`MAX_SIZE = 100`, `MAX_PAGE`):
  - `PostController.java:88-89` i `FailedPostController.java:54-55` mają `@Max(PageLimits.MAX_SIZE)`;
  - `PipelineRunController.java:65` ma alias `MAX_PAGE_SIZE = PageLimits.MAX_SIZE`.

  Rejestr ma `pageMax` i `runsPageMaxSize` (= alias), a SPA ma `MAX_PAGE` (`src/api/posts.ts:15`) i `MAX_RUNS_PAGE_SIZE` (`src/api/pipeline.ts:130`). Rozmiary, o które prosi SPA, są luźnymi literałami: `PAGE_SIZE = 50` (`src/views/FeedView.vue:15`), domyślne `size = 50` w `fetchPosts` i `fetchFailedPosts` (`src/api/posts.ts:150,176`), `PAGE_SIZE = 20` (`src/views/AdminView.vue:182`). Atrapa jest samodzielnym skryptem bez importów: `pageNumber > 21474836 || !(size >= 1 && size <= 100)` (`dev/mock-api.js:485`). `rules.test.ts:83` sprawdza tekstowo tylko `pageNumber > ${pageMax}`. `src/contract/mockApi.test.ts:560,566` ma literały `page=21474837`, `size=101` itd., a `src/components/FeedFilter.test.ts:33` literał `'100'` (to `feedQueryMaxLength`).
- **Enumy spoza rejestru.** Z typów `ContractRegistry.SHAPES` (`szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractRegistry.java:63-99`) da się osiągnąć 8 enumów Javy, których nie ma w `rules.json`:

| Enum Javy | Gdzie w odpowiedzi | TS dziś |
|---|---|---|
| `user/Role` (`USER, ADMIN`) | `UserResponse.role` | inline `'user' \| 'admin'` (`src/api/auth.ts:7`) |
| `notifications/NotificationKind` (`run_attention`) | `NotificationItem.kind` | `NotificationKind` (`src/api/notifications.ts:6`) |
| `posts/PostStage` (`STORED, ASSESSING, ASSESSED, FAILED, DEAD`) | `FailedPost.stage` | `'stored' \| 'failed' \| 'dead'` (`src/api/posts.ts:105`) — podzbiór |
| `agents/assessor/Provenance.Source` (`MODEL, PREFILTER`) | `AssessmentView.resultSource` | inline (`src/api/posts.ts:92`) |
| `agents/assessor/contract/Document` | `Anchor/Evidence.document` | inline w `Quote` (`src/api/posts.ts:37`) |
| `agents/assessor/contract/Field` | `Anchor/Evidence.field` | inline w `Quote` (`src/api/posts.ts:38`) |
| `agents/assessor/contract/Assertiveness` | `Stance.assertiveness` | `'hedged' \| 'unhedged' \| null` (`src/api/posts.ts:33`) |
| `llm/Provider` (`GEMINI, DEEPSEEK, OPENROUTER`; `@JsonValue id()`) | `FailedPost.lastAttempt.provider` | `string \| null` (`src/api/posts.ts`, `lastAttempt`) |

  `SpaRulesTest.java:75-96` trzyma ręczną mapę 19 enumów. Nowy enum w odpowiedzi nie czerwieni niczego. `ContractVariantsTest` już chodzi refleksją po rekordach z `SHAPES` i traktuje enumy jako liście (`ContractVariantsTest.java:187-196,263-310`). `rules.test.ts` wymaga etykiet dla każdego zbioru poza `attentionReason` i `itemType` (`:87-98`) oraz unii TS dla każdego zbioru poza `direction`, `source`, `targetKind` (`:255-259`). `mockApi.test.ts` ma ręczne mapy ścieżek do zbiorów (`enums` w `Check`, np. `:65-81`).
- **Warunek `skipped` (R-22).** `PipelineRun.attentionReasons` (`szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java:321-347`) daje `SKIPPED` dla `succeeded`/`partial` ze `skipped > 0`. SPA kopiuje ten warunek w dwóch miejscach:
  - `runTone` (`src/runs.ts:39`: `succeeded` ze `skipped > 0` → `warning`);
  - `hasSkipped` (`src/runs.ts:80-82`, sufiks „— pominięte: N” w `runStatusLabel`).

  Obie funkcje czytają `RunState` (`status`, `interruptionCause`, `skipped`) i służą liście przebiegów, stronie przebiegu oraz dzwonkowi (`src/components/NotificationMenu.vue:167`). Przebieg w powiadomieniu (`NotificationController.NotifiedRun`, `notifications/api/NotificationController.java:123-131`) nie ma `attentionReasons`. `notifications.json` przypina `NotificationsIT:282`.

### Key Discoveries:

- `ContractRegistry.SHAPES` jest w teście jednostkowym dostępny bez Springa (typy ładowane po nazwie binarnej). `SpaRulesTest` może więc wyprowadzić osiągalne enumy bez IT.
- `ContractVariantsTest.java:181-196`: wzorzec rozwijania `List<X>` (także `ContractRegistry.ListType`) i rozpoznawania liści (enum, prymityw, `String`, `UUID`, instant, data).
- `rules.test.ts:174-208`: wzorzec `Exhaustive<U, T>` z `satisfies`. Krotka bez członka unii albo z członkiem spoza niej nie kompiluje się.
- `rules.test.ts:83-85`: wzorzec tekstowego sprawdzenia literału atrapy wobec `rules.json`.
- `mockApi.test.ts:405-408`: ścieżki z `enums` są sprawdzane z `rules.values[set]`. Nowy zbiór w rejestrze daje atrapie ten sam strażnik po dopisaniu ścieżki.
- Kod `SKIPPED` dla przebiegu `succeeded` nie ma dziś żadnego sąsiada (nieudane i puste źródła dają `partial`, S-20). Ton z kodów zmienia więc obecne wyniki tylko tam, gdzie API dałoby dla `succeeded` inny powód.

## Desired End State

- `ApiError.detail` ma typ `ErrorCode | null`, a `ErrorCode` to unia kodów z `rules.json` `errorCodes` (pilnowana krotką `Exhaustive`). Powtórzenie X5 daje błąd `vue-tsc` (TS2367) i czerwony skan w `npm test`.
- Rejestr ma jeden limit rozmiaru strony, `pageMaxSize` (= `PageLimits.MAX_SIZE`), i SPA ma jedną stałą `MAX_PAGE_SIZE`. Każdy rozmiar strony, o który prosi SPA, jest nazwaną stałą sprawdzaną testem w granicach `1…pageMaxSize`. Literały atrapy i jej testu są związane z `rules.json`. Obniżenie limitu w API czerwieni SPA i atrapę.
- `SpaRulesTest` wyprowadza każdy enum osiągalny z typów `ContractRegistry.SHAPES`. Zbiór tych enumów jest równy rejestrowi `VALUES` w obie strony: enum bez wpisu jest czerwony i wpis bez osiągalnego enuma też. `rules.json` ma 27 list wartości, każda z nazwaną unią TS albo z nazwanym wyjątkiem.
- Przebieg w powiadomieniu niesie `attentionReasons` z API. `runTone` i `runStatusLabel` czytają kody, a SPA nie liczy żadnego warunku powodu „wymaga uwagi”.

## What We're NOT Doing

- **`vue-tsc` w `npm test` albo w jobie `web-unit`.** Kompilator łapie X5 w budowie obrazu (blokuje deploy), a rozszerzony skan łapie go w `npm test`. Workflow CI się nie zmienia.
- **Etykiety dla ośmiu nowych zbiorów** i jakakolwiek zmiana widoku. Zbiory bez etykiet dostają w `rules.test.ts` nazwaną listę wyjątków z powodem.
- **`needsAttention` w `NotifiedRun`.** Wystarcza `attentionReasons`: niepusta lista ≡ wymaga uwagi (reguła API).
- **Strażnik skanujący `src/runs.ts` pod kątem liczenia powodów.** Kopia znika z kodu, a test zachowania (licznik `skipped` > 0 bez kodu `skipped` nie daje tonu ani sufiksu) czerwieni jej powrót.
- **Walidacja nieznanego `detail` w runtime.** Kod spoza unii, który przyszedłby od nowszego API, przechodzi jak dziś do gałęzi domyślnej `switch`.
- **Wyprowadzanie unii TS z AST.** Łańcuch „enum Javy osiągalny z `SHAPES` → wpis w `rules.json` → unia TS albo nazwany wyjątek” wystarcza.
- **Domyślne `size` w `@RequestParam(defaultValue = …)` po stronie API.** SPA zawsze wysyła `size`.
- Atrapy narzędzi (`tools/pipeline-trigger`, `tools/deploy-gate`): S-43, zrobione.

## Implementation Approach

Każda krawędź dostaje strażnika tam, gdzie repo ma już wzorzec:

- unia z krotką `Exhaustive` dla kodów błędów, z dodatkowym skanem Vitest;
- nazwane stałe z testem granic i tekstowe sprawdzenie literałów atrapy dla limitu strony;
- typowe chodzenie refleksją po `SHAPES` dla enumów, tak jak w `ContractVariantsTest`;
- pole kontraktu z pliku przypinanego przez IT dla powodów w powiadomieniu.

Fazy są niezależne, a kolejność idzie od najmniejszej powierzchni do zmiany kontraktu. Faza 4 kończy się dokumentacją i zielonym PR.

## Critical Implementation Details

- **TS2367 działa tylko na rozłącznych typach literałowych.** `detail` musi być dokładnie `ErrorCode | null` (nie `ErrorCode | string`), inaczej `=== 'pipeline_bussy'` dalej się kompiluje. `readDetail` rzutuje tekst z serwera na `ErrorCode` na granicy, tak jak typy odpowiedzi rzutuje `apiFetch<T>`.
- **`Document` to globalny typ DOM.** Unie zbiorów `document` i `field` dostają nazwy `QuoteDocument` i `QuoteField`, nie `Document`/`Field`.
- **`PostStage` ma w Javie pięć wartości.** Unia TS rośnie do pięciu (typ pola odpowiada enumowi na kablu), a komentarz mówi, że lista nieudanych daje tylko `stored`, `failed`, `dead`. Kod widoku (`stage === 'dead'`, `!== 'stored'`) kompiluje się bez zmian.
- **`Assertiveness` w TS zawiera dziś `null`.** Unia zbioru jest bez `null`, a pole `Stance.assertiveness` ma typ `Assertiveness | null`.

## Phase 1: Kody błędów jako unia

### Overview

Kompilator i skan Vitest łapią kod błędu spoza API w każdej formie porównania, także X5.

### Changes Required:

#### 1. Typ `detail`

**File**: `szczur-radar-web/src/api/client.ts`

**Intent**: zawęzić `ApiError.detail` do kodów API, żeby porównanie z literówką było błędem kompilacji.

**Contract**: `export type ErrorCode` = 15 kodów w kolejności `config/ErrorCode`. `ApiError.detail: ErrorCode | null`, a parametr konstruktora jest tego samego typu. `readDetail(): Promise<ErrorCode | null>` rzutuje tekst z ciała na granicy, a komentarz mówi, że kod nowszego API trafia do gałęzi domyślnej.

#### 2. Krotka kodów i skan

**File**: `szczur-radar-web/src/contract/rules.test.ts`

**Intent**: związać unię z `rules.json` i rozszerzyć skan o formy, których dziś nie widzi, żeby lokalne `npm test` też łapało X5.

**Contract**:
- `ERROR_CODES` `satisfies Exhaustive<ErrorCode, …>` i jest porównana z `rules.errorCodes`.
- `literals()` rozpoznaje też:
  - `.detail === '<kod>'` i `.detail !== '<kod>'`;
  - formy odwrócone `'<kod>' === ….detail`;
  - `.detail).toBe('<kod>')` / `.toEqual('<kod>')`.
- Nowy test „rozpoznaje każdą formę” przepuszcza przez `literals()` po jednym przykładzie każdej formy, żeby zepsuty regex nie przechodził po cichu. Minimum trafień zostaje.

#### 3. Testy i wywołania

**File**: `szczur-radar-web/src/api/pipeline.test.ts:107`, `src/views/PostDetailView.test.ts:151`, `src/views/RunDetailView.test.ts:163`, `src/views/ActivateView.test.ts:100` (tabele `it.each`, które podają `detail` jako zmienną `string`; pozostałe 36 wywołań ma literał kodu albo `null`)

**Intent**: dopasować testy do zawężonego typu bez zmiany ich znaczenia.

**Contract**: każde `new ApiError(status, x)` w testach ma `x` z unii albo `null`. Tabele `it.each` z tych czterech plików są typowane jako `ErrorCode` (albo `as const`), więc kod spoza unii w wierszu tabeli też nie kompiluje się.

### Success Criteria:

#### Automated Verification:

- Testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Obraz web buduje się z własnego kontekstu (typecheck testów): `docker build -t sr-web-rules ./szczur-radar-web && docker rmi sr-web-rules`
- Powtórzenie X5 (agent wykonuje i cofa): `if (err.detail === 'pipeline_bussy') return 'x'` przed `switch` w `startErrorMessage` daje błąd TS2367 w `npm run build` i czerwony skan w `npm test`; `new ApiError(409, 'pipeline_bussy')` w teście daje błąd `vue-tsc`
- Celowe zepsucie unii (agent wykonuje i cofa): usunięcie `post_dead` z `ErrorCode` daje błąd `vue-tsc` (krotka `Exhaustive` i `case 'post_dead'`)

---

## Phase 2: Jeden limit rozmiaru strony

### Overview

Rejestr i SPA znają jeden limit rozmiaru strony. Rozmiary, o które prosi SPA, oraz literały atrapy są z nim związane testem.

### Changes Required:

#### 1. Rejestr i serwer

**File**: `szczur-radar-web/src/contract/rules.json`, `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/SpaRulesTest.java`, `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/PipelineRunController.java`

**Intent**: zastąpić „limit przebiegów” limitem każdej listy i usunąć alias, który udawał osobny limit.

**Contract**: `limits.runsPageMaxSize` → `limits.pageMaxSize`, w `SpaRulesTest.LIMITS` → `PageLimits.MAX_SIZE`. `PipelineRunController.MAX_PAGE_SIZE` znika, a `@Max(PageLimits.MAX_SIZE)` jest jak w dwóch pozostałych kontrolerach. Odwołania w testach Javy idą do `PageLimits.MAX_SIZE`.

#### 2. Stałe SPA

**File**: `szczur-radar-web/src/api/posts.ts`, `src/api/pipeline.ts`, `src/views/FeedView.vue`, `src/views/AdminView.vue`

**Intent**: jedna stała limitu i nazwane rozmiary próśb zamiast literałów w widokach.

**Contract**:
- `MAX_PAGE_SIZE = 100` w `posts.ts` obok `MAX_PAGE` (obie z `PageLimits`) zastępuje `MAX_RUNS_PAGE_SIZE`.
- `FEED_PAGE_SIZE = 50` jest domyślnym `size` w `fetchPosts` i rozmiarem strony w `FeedView`.
- `FAILED_PAGE_SIZE = 50` jest domyślnym `size` w `fetchFailedPosts`.
- `RUNS_PAGE_SIZE = 20` w `pipeline.ts` zastępuje `PAGE_SIZE` w `AdminView`.

#### 3. Strażnicy w Vitest

**File**: `szczur-radar-web/src/contract/rules.test.ts`, `src/contract/mockApi.test.ts`, `src/components/FeedFilter.test.ts`, `src/views/AdminView.test.ts`, `dev/mock-api.js` (tylko komentarz nagłówka i literał, jeśli zmieni się kształt warunku)

**Intent**: obniżenie limitu w `rules.json` czerwieni każdy rozmiar SPA i atrapę, a testy nie trzymają własnych kopii limitów.

**Contract**:
- W `limits` jest `pageMaxSize: MAX_PAGE_SIZE`.
- Nowy test: każdy z `FEED_PAGE_SIZE`, `FAILED_PAGE_SIZE`, `RUNS_PAGE_SIZE` jest w `1…rules.limits.pageMaxSize`.
- Tekstowe sprawdzenie atrapy obejmuje też `size <= ${pageMaxSize}`.
- `mockApi.test.ts` liczy `page=<pageMax + 1>`, `page=<pageMax>`, `size=<pageMaxSize + 1>` i `size=<pageMaxSize>` z `rules.limits`.
- `FeedFilter.test.ts:33` porównuje z `String(MAX_QUERY_LENGTH)`.
- `AdminView.test.ts:882,889` sprawdza rozmiar `fetchRuns` z `RUNS_PAGE_SIZE` zamiast literału `20`.

### Success Criteria:

#### Automated Verification:

- Rejestr po stronie serwera zgodny: `cd szczur-radar-api && ./mvnw -B -Dtest=SpaRulesTest test`
- Granice stron przebiegów w API bez zmian: `cd szczur-radar-api && ./mvnw -B -Dit.test=PipelineRunControllerIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
- Testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Celowe zepsucie limitu (agent wykonuje i cofa): `pageMaxSize: 40` w `rules.json` daje czerwony `SpaRulesTest` i czerwony `rules.test.ts` (rozmiary feedu i nieudanych 50 > 40, warunek atrapy); `FEED_PAGE_SIZE = 101` daje czerwony `rules.test.ts`

---

## Phase 3: Enumy z rejestru, kompletność wyprowadzana

### Overview

Każdy enum osiągalny z odpowiedzi czytanych przez SPA jest w `rules.json`, a `SpaRulesTest` wyprowadza ten zbiór sam. SPA ma dla każdego zbioru nazwaną unię albo nazwany wyjątek, a atrapa jest sprawdzana także na nowych zbiorach.

### Changes Required:

#### 1. Wyprowadzanie osiągalnych enumów

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/SpaRulesTest.java`, nowy pakietowy pomocnik `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractTypes.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractVariantsTest.java`

**Intent**: zastąpić ręczną krawędź mapy wyprowadzeniem z `SHAPES`, żeby nowy enum w odpowiedzi czerwienił test, zanim SPA zacznie go używać. Chodzenie po typach ma jedno miejsce: dwa testy, które rozjadą się przy pierwszym nowym kształcie typu, to kolejna krawędź.

**Contract**:
- `ContractTypes` przejmuje z `ContractVariantsTest` `listElement()` (`List<X>`, także `ContractRegistry.ListType`), `leaf()` i rekurencyjne chodzenie po komponentach rekordów (`Walk.reach`, ~l.245-268). Oba testy go używają, a `ContractVariantsTest` zachowuje swoje zachowanie (asercja „liść albo rekord” zostaje).
- `ContractTypes` zbiera też enumy osiągalne z danego `Type`, każdy z pierwszą ścieżką `Rekord.komponent`, z odwiedzonymi typami.
- Nowy test: zbiór enumów osiągalnych z typów wszystkich `SHAPES` jest równy zbiorowi klas w `VALUES`, porównywany jako `Set<Class<?>>`. Osiągalne są dwa enumy o prostej nazwie `Source` (`sources.Source` i `Provenance.Source`), więc nazwa prosta nie wystarcza. Oba kierunki są czerwone, a komunikat nazywa enum nazwą binarną (np. `Provenance$Source`) i ścieżkę `Rekord.komponent`.
- Drugi test podaje funkcji syntetyczny rekord z enumem spoza rejestru i sprawdza, że funkcja go zgłasza. Strażnik nie może przejść, bo nic nie znalazł.
- `VALUES` dostaje osiem wpisów:

  | Klucz | Enum Javy |
  |---|---|
  | `role` | `Role` |
  | `notificationKind` | `NotificationKind` |
  | `postStage` | `PostStage` |
  | `resultSource` | `Provenance.Source` |
  | `document` | `Document` |
  | `field` | `Field` |
  | `assertiveness` | `Assertiveness` |
  | `provider` | `Provider` |

  Javadoc mapy mówi, że nazwa nie zawsze jest nazwą zbioru `labels`.

#### 2. Rejestr

**File**: `szczur-radar-web/src/contract/rules.json`

**Intent**: osiem nowych list wartości.

**Contract**: `values.<klucz>` = wartości na kablu (`@JsonValue`) w kolejności deklaracji enuma, dla ośmiu kluczy z tabeli wyżej.

#### 3. Unie TS

**File**: `szczur-radar-web/src/api/auth.ts`, `src/api/notifications.ts`, `src/api/posts.ts`

**Intent**: każdy zbiór rejestru ma nazwaną unię, którą test może sprawdzić krotką.

**Contract**:
- `Role` (pole `role: Role`).
- `NotificationKind` (już jest).
- `PostStage` z pięcioma wartościami, z komentarzem, które daje lista nieudanych.
- `ResultSource` (pole `resultSource`).
- `QuoteDocument` i `QuoteField` (pola `Quote`).
- `Assertiveness` bez `null`, a pole stanowiska ma typ `Assertiveness | null`.
- `Provider` (pole `lastAttempt.provider: Provider | null`).

#### 4. Testy SPA i atrapy

**File**: `szczur-radar-web/src/contract/rules.test.ts`, `src/contract/mockApi.test.ts`

**Intent**: nowe zbiory są pilnowane po stronie SPA i atrapy tak jak dotychczasowe.

**Contract**:
- `UNION_TUPLES` dostaje 8 krotek `Exhaustive`.
- Test etykiet bierze zbiory bez etykiet z nazwanej mapy `NO_LABEL` (zbiór → powód: `attentionReason`, `itemType` i 8 nowych) zamiast z filtra w linii.
- `enums` w `mockApi.test.ts` dostają ścieżki nowych zbiorów w swoich sprawdzeniach: rola użytkownika, rodzaj powiadomienia, etap i dostawca w liście nieudanych, `resultSource`, `document`/`field` kotwic i dowodów, `assertiveness`. Wartości nullable są dozwolone jako `null`, tak jak teraz.

### Success Criteria:

#### Automated Verification:

- Rejestr i wyprowadzenie po stronie serwera zielone: `cd szczur-radar-api && ./mvnw -B -Dtest=SpaRulesTest test`
- Testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- Celowe zepsucie wyprowadzenia (agent wykonuje i cofa): usunięcie `provider` z `VALUES` i z `rules.json` daje czerwony `SpaRulesTest` z nazwą `Provider` i ścieżką przez `LastAttempt.provider`; wpis `serviceTier` → `llm/ServiceTier` (enum nieosiągalny z odpowiedzi) dodany do `VALUES` i `rules.json` daje czerwony `SpaRulesTest` w drugą stronę, z nazwą `ServiceTier`
- Celowe zepsucie wartości (agent wykonuje i cofa): wartość `unhedged` usunięta z unii `Assertiveness` daje błąd `vue-tsc`; rola `superadmin` w danych `dev/mock-api.js` daje czerwony `mockApi.test.ts`

---

## Phase 4: Powody „wymaga uwagi” w powiadomieniu

### Overview

Przebieg w powiadomieniu niesie kody powodów z API. Ton i status przebiegu (lista, strona przebiegu, dzwonek) czytają kody i nie liczą warunku `skipped`. Faza kończy się dokumentacją i zielonym PR.

### Changes Required:

#### 1. Kontrakt powiadomień w API

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/notifications/api/NotificationController.java`

**Intent**: dzwonek dostaje tę samą regułę co strona przebiegu, z jednego źródła (`PipelineRun.attentionReasons()`).

**Contract**: `NotifiedRun` ma komponent `List<AttentionReason> attentionReasons` zaraz po `interruptionCause`, z javadokiem: kolejność reguły, pusta, gdy przebieg uwagi nie wymaga. `NotifiedRun.of` przekazuje `run.attentionReasons()`.

#### 2. Plik kontraktu, próbka, typ, atrapa

**File**: `szczur-radar-web/src/contract/notifications.json`, `src/contract/samples.ts`, `src/api/notifications.ts`, `dev/mock-api.js`, `src/contract/mockApi.test.ts`, `src/views/KitchenSinkView.vue` (`notification()`, ~l.80-100: kompiluje go `vue-tsc`, więc bez pola pada budowa obrazu)

**Intent**: nowe pole po obu stronach kontraktu i w atrapie w tym samym PR.

**Contract**:
- `notifications.json` z ciała `NotificationsIT` (dziś `["interrupted"]` i `["dead_posts"]`).
- Próbka `NotificationList` z tymi samymi kodami.
- `NotifiedRun.attentionReasons: AttentionReason[]` z komentarzem.
- Atrapa ma już kody przy każdym przebiegu (wpisane na sztywno, l.159, 189, …), więc `notifiedRun()` (~l.270) tylko kopiuje `r.attentionReasons`. Atrapa nie liczy reguły.
- W sprawdzeniu powiadomień atrapy (`NOTIFICATION_ENUMS`, ~l.96) jest ścieżka `$.items[].run.attentionReasons[]` → `attentionReason`.
- Powiadomienia kitchen sinka mają `attentionReasons` zgodne ze swoim stanem.

#### 3. `src/runs.ts` bez warunku powodu

**File**: `szczur-radar-web/src/runs.ts`

**Intent**: SPA pokazuje powody API zamiast je liczyć (R-22).

**Contract**:
- `RunState` = `status`, `interruptionCause`, `attentionReasons`, `skipped` (sam licznik do tekstu).
- `runTone`: `succeeded` z niepustymi `attentionReasons` → `warning`. To każdy powód, jaki API da przebiegowi `succeeded`, nie tylko `skipped`.
- `runStatusLabel`: sufiks „— pominięte: N” wtedy i tylko wtedy, gdy kody zawierają `skipped`.
- `hasSkipped` znika, a komentarz nagłówka mówi, że nic tu nie jest liczone.

#### 4. Testy SPA

**File**: `szczur-radar-web/src/runs.test.ts`, `src/contract/rules.test.ts` (sekcja `tones` i literał `RunState` w `:147`), `src/components/NotificationBell.test.ts` (~l.27-45), testy `NotificationMenu` i widoków z ręcznymi przebiegami

**Intent**: zachowanie przypięte na kodach, a powrót liczenia po stronie SPA jest czerwony.

**Contract**:
- Przypadki tonu i sufiksu z kodami.
- Licznik bez kodu: `succeeded` i `partial`, każdy ze `skipped: 3` i `attentionReasons` bez `skipped` (dla `succeeded` puste). Żaden nie dostaje sufiksu, a `succeeded` nie dostaje `warning`. Tylko te statusy wykrywają powrót kopii: `hasSkipped` dotyczy `succeeded`/`partial`, a stary `runTone` `succeeded` ze `skipped > 0`. Przypadek `interrupted` ze `skipped` > 0 zostaje jako dodatkowy.
- Fikstury przebiegów powiadomień i literał w `rules.test.ts:147` mają `attentionReasons`.

#### 5. Dokumentacja i roadmapa

**File**: `REPOSITORY_GUIDELINES.md`, `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: opis rejestru i reguły „wymaga uwagi” zgodny z kodem.

**Contract**:
- Akapit o `src/contract/` (`rules.json`): unia `ErrorCode` z `ApiError.detail`, jeden limit `pageMaxSize` i nazwane rozmiary SPA, `SpaRulesTest` wyprowadza każdy enum osiągalny z `ContractRegistry.SHAPES`.
- Sekcja UI o `src/runs.ts`: ton, status i dzwonek czytają kody, a przebieg w powiadomieniu je niesie.
- Status S-42 w roadmapie i dane renderera z odświeżonym SVG (`python3 tools/render_roadmap_svg.py`) tak, jak robią to fazy `/10x-implement`.

### Success Criteria:

#### Automated Verification:

- Kontrakt powiadomień przypięty: `cd szczur-radar-api && ./mvnw -B -Dit.test=NotificationsIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify`
- Strażnicy kontraktu zieloni: `cd szczur-radar-api && ./mvnw -B -Dtest='ContractVariantsTest,SpaRulesTest' test`
- Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify`
- Testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build`
- Obraz web buduje się z własnego kontekstu: `docker build -t sr-web-rules ./szczur-radar-web && docker rmi sr-web-rules`
- Celowe zepsucie R-22 (agent wykonuje i cofa): przywrócenie `run.skipped > 0` w `runTone` albo `hasSkipped` w `runStatusLabel` daje czerwony `runs.test.ts`; usunięcie `attentionReasons` z `notifications.json` i próbki daje błąd `vue-tsc` i czerwony `mockApi.test.ts`
- Atrapa w przeglądarce (agent): przed zmianą `src/runs.ts` (§3) agent zapisuje w scratchpadzie teksty odznak rozwiniętego dzwonka z `npm run dev` z `dev/mock-api.js` (`playwright-cli`, `/admin` jako admin). Po zmianie te same kroki dają identyczną listę tekstów (np. „przerwany — nowe wdrożenie”), a w konsoli nie ma błędów
- CI na PR zielone, z budową obrazu web, testami i `railway-config` dla PR env: `gh pr checks <PR> --watch` kończy się bez czerwonego joba

---

## Testing Strategy

### Unit Tests:

- `SpaRulesTest`: równość zbioru osiągalnych enumów i `VALUES` w obie strony, syntetyczny rekord z enumem spoza rejestru, nowy klucz `pageMaxSize`.
- `rules.test.ts`:
  - krotka `ErrorCode`;
  - skan z testem każdej formy;
  - granice rozmiarów SPA i warunek atrapy;
  - 8 nowych krotek;
  - mapa `NO_LABEL`.
- `runs.test.ts`: ton i sufiks z kodów, licznik bez kodu nie daje ani tonu, ani sufiksu.
- `mockApi.test.ts`: ścieżki nowych zbiorów i limity z `rules.json`.

### Integration Tests:

- `NotificationsIT` przypina `notifications.json` z `attentionReasons`.
- `PipelineRunControllerIT` zostaje zielony po usunięciu aliasu limitu.

### Manual Testing Steps:

1. Nic poza przeglądem commitów przed merge'em. Kontrola atrapy w przeglądarce i CI są wierszami Automated fazy 4.

## Performance Considerations

Brak: zmiany dotyczą testów, typów i jednego pola listy powiadomień (lista z co najwyżej 10 elementów, powody liczone w pamięci z liczników przebiegu).

## Migration Notes

Brak migracji bazy. Nowe pole w `/api/admin/notifications` jest addytywne. SPA i API wdrażają się z tego samego merge'a, a SPA czyta pole dopiero w tej samej wersji.

Dopisek z przeglądu (F1, `reviews/impl-review.md`): web i API wdrażają się osobno, a API ma pre-deploy `migrate`, więc przez chwilę nowe SPA może dostać od starego API przebieg w powiadomieniu bez `attentionReasons`. Dzwonek admina nie narysuje wtedy listy (`TypeError` w `runTone`). Naprawia się przy następnym odczycie listy. Przyjęte świadomie, tak jak przy `RunView.attentionReasons`.

## References

- Audyt: `context/changes/post-m3-verification-audit/research.md` (§1 R-22, R-43, X5; „Materiał dla M-4” p. 3)
- Poprzednia runda kontraktu: `context/archive/2026-11-08-api-contract-shapes-round-two/plan.md` (S-41 zostawił te punkty S-42, `:59-60`)
- Atrapy: `context/archive/2026-11-11-contract-bound-mocks/` (S-43, `mockApi.test.ts`)
- Wzorce: `szczur-radar-web/src/contract/rules.test.ts:174-208` (`Exhaustive`), `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/ContractVariantsTest.java:181-196` (chodzenie po typach)
- Reguła powodów: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRun.java:321-352`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Kody błędów jako unia

#### Automated

- [x] 1.1 Testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build` — 86e1677
- [x] 1.2 Obraz web buduje się z własnego kontekstu (typecheck testów): `docker build -t sr-web-rules ./szczur-radar-web && docker rmi sr-web-rules` — 86e1677
- [x] 1.3 Powtórzenie X5 (agent wykonuje i cofa): `if (err.detail === 'pipeline_bussy') return 'x'` przed `switch` w `startErrorMessage` daje błąd TS2367 w `npm run build` i czerwony skan w `npm test`; `new ApiError(409, 'pipeline_bussy')` w teście daje błąd `vue-tsc` — 86e1677
- [x] 1.4 Celowe zepsucie unii (agent wykonuje i cofa): usunięcie `post_dead` z `ErrorCode` daje błąd `vue-tsc` (krotka `Exhaustive` i `case 'post_dead'`) — 86e1677

### Phase 2: Jeden limit rozmiaru strony

#### Automated

- [x] 2.1 Rejestr po stronie serwera zgodny: `cd szczur-radar-api && ./mvnw -B -Dtest=SpaRulesTest test` — 0252b48
- [x] 2.2 Granice stron przebiegów w API bez zmian: `cd szczur-radar-api && ./mvnw -B -Dit.test=PipelineRunControllerIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify` — 0252b48
- [x] 2.3 Testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build` — 0252b48
- [x] 2.4 Celowe zepsucie limitu (agent wykonuje i cofa): `pageMaxSize: 40` w `rules.json` daje czerwony `SpaRulesTest` i czerwony `rules.test.ts` (rozmiary feedu i nieudanych 50 > 40, warunek atrapy); `FEED_PAGE_SIZE = 101` daje czerwony `rules.test.ts` — 0252b48

### Phase 3: Enumy z rejestru, kompletność wyprowadzana

#### Automated

- [x] 3.1 Rejestr i wyprowadzenie po stronie serwera zielone: `cd szczur-radar-api && ./mvnw -B -Dtest=SpaRulesTest test` — be9933c
- [x] 3.2 Testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build` — be9933c
- [x] 3.3 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — be9933c
- [x] 3.4 Celowe zepsucie wyprowadzenia (agent wykonuje i cofa): usunięcie `provider` z `VALUES` i z `rules.json` daje czerwony `SpaRulesTest` z nazwą `Provider` i ścieżką przez `LastAttempt.provider`; wpis `serviceTier` → `llm/ServiceTier` (enum nieosiągalny z odpowiedzi) dodany do `VALUES` i `rules.json` daje czerwony `SpaRulesTest` w drugą stronę, z nazwą `ServiceTier` — be9933c
- [x] 3.5 Celowe zepsucie wartości (agent wykonuje i cofa): wartość `unhedged` usunięta z unii `Assertiveness` daje błąd `vue-tsc`; rola `superadmin` w danych `dev/mock-api.js` daje czerwony `mockApi.test.ts` — be9933c

### Phase 4: Powody „wymaga uwagi” w powiadomieniu

#### Automated

- [x] 4.1 Kontrakt powiadomień przypięty: `cd szczur-radar-api && ./mvnw -B -Dit.test=NotificationsIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify` — d4eae6f
- [x] 4.2 Strażnicy kontraktu zieloni: `cd szczur-radar-api && ./mvnw -B -Dtest='ContractVariantsTest,SpaRulesTest' test` — d4eae6f
- [x] 4.3 Cały backend zielony: `cd szczur-radar-api && ./mvnw -B verify` — d4eae6f
- [x] 4.4 Testy web i typecheck zielone: `cd szczur-radar-web && npm test && npm run build` — d4eae6f
- [x] 4.5 Obraz web buduje się z własnego kontekstu: `docker build -t sr-web-rules ./szczur-radar-web && docker rmi sr-web-rules` — d4eae6f
- [x] 4.6 Celowe zepsucie R-22 (agent wykonuje i cofa): przywrócenie `run.skipped > 0` w `runTone` albo `hasSkipped` w `runStatusLabel` daje czerwony `runs.test.ts`; usunięcie `attentionReasons` z `notifications.json` i próbki daje błąd `vue-tsc` i czerwony `mockApi.test.ts` — d4eae6f
- [x] 4.7 Atrapa w przeglądarce (agent): przed zmianą `src/runs.ts` (§3) agent zapisuje w scratchpadzie teksty odznak rozwiniętego dzwonka z `npm run dev` z `dev/mock-api.js` (`playwright-cli`, `/admin` jako admin). Po zmianie te same kroki dają identyczną listę tekstów (np. „przerwany — nowe wdrożenie”), a w konsoli nie ma błędów — d4eae6f
- [x] 4.8 CI na PR zielone, z budową obrazu web, testami i `railway-config` dla PR env: `gh pr checks <PR> --watch` kończy się bez czerwonego joba — 937c46c (PR #170)
