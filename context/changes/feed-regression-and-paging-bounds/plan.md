# Feed z testami regresji i granicą numeru strony Implementation Plan

## Overview

Slice S-44 (M-4, `Prerequisites: —`). Zamykamy dwie klasy luk z audytu `post-m3-verification-audit` (§4, eksperyment X6): (1) ogromny numer strony na trzech listach (`/api/posts`, `/api/admin/posts/failed`, `/api/admin/pipeline-runs`) daje dziś 500 `internal_error` zamiast 400 `invalid_request`; (2) znane ścieżki feedu nie mają testów regresji, więc nie wiadomo, czy ktokolwiek je chroni; (3) link feedu z U+0000 w `q` albo z ogromnym `?page=` wysyła zapytanie, które API słusznie odrzuca, ale SPA zostaje w błędzie przy każdym odświeżeniu.

PRD: FR-006 (lista), FR-007 (kolejność), FR-011a (filtr). Wszystkie trzy to wymagania już zbudowane; ten slice niczego nie dodaje do produktu, tylko go zabezpiecza i domyka dwa defekty.

## Current State Analysis

- Trzy listy mają `@RequestParam(defaultValue = "0") @Min(0) int page` bez górnej granicy: `PostController.java:86`, `FailedPostController.java:49`, `PipelineRunController.java:113`. `PageRequest.of(page, size)` liczy przesunięcie jako `long`; powyżej `Integer.MAX_VALUE` Spring Data rzuca `InvalidDataAccessApiUsageException: Page offset exceeds Integer.MAX_VALUE`. Wyjątek nie jest mapowany w `config/ApiExceptionHandler`, więc przez `ApiErrorController` wychodzi jako 500 `internal_error`.
- X6 uruchomiono tylko dla feedu bez filtra (`page=21474837&size=100`). Wariant z `q` (zapytanie natywne `findMatching`) oraz `FailedPostController` i `PipelineRunController` nie były uruchamiane (audyt, „Open Questions”).
- SPA czyta `?page=` z adresu przez `parseInt` bez górnej granicy (`FeedView.vue:34-37`) i wysyła je z `PAGE_SIZE = 50` (`FeedView.vue:86`), więc po poprawce API link `/?page=99999999999` dostaje 400 i feed zostaje w błędzie — ta sama klasa defektu co NUL (przegląd planu, F1).
- Maksymalny `size` to 100 na wszystkich trzech listach (`@Max(100)` w dwóch kontrolerach, `PipelineRunController.MAX_PAGE_SIZE = 100`, lustro w `rules.json` jako `runsPageMaxSize`).
- NUL w `q`: API już odrzuca 400 (`@Pattern(regexp = "[^\\x00]*")`, `PostController.java:88`; test w `PostsApiIT.badFilterValuesAreAnInvalidRequest`). Defekt jest w SPA: `normalizeQuery` w `szczur-radar-web/src/feedFilter.ts` obcina białe znaki i długość, ale nie usuwa U+0000, wbrew komentarzowi nagłówkowemu pliku („a hand-made link drops what the API would refuse”). `dev/mock-api.js` już odpowiada 400 na NUL, więc da się to odtworzyć bez prawdziwego API.
- Testy feedu: `PostsApiIT` (553 wierszy) pokrywa kształt, kolejność przy równych czasach publikacji **bez** filtra, `q` + `direction` na jednym celu, filtr patrzący tylko na najnowszą ocenę `ok` (dwie oceny `ok`), złe wartości filtra i stronicowanie z filtrem przy różnych czasach publikacji. Nie pokrywa: remisów daty z filtrem, `kind` + `direction` na jednym celu, oceny `ok` przed późniejszą `failed`, strony za końcem, filtra `direction` na stanowisku ostrożnym.

## Desired End State

- `GET` na każdej z trzech list z `page` ponad granicą daje 400 `{"detail":"invalid_request"}`; największa dozwolona strona daje 200 z pustą listą i poprawnym `total`. Granica wynika z jednej stałej powiązanej z maksymalnym `size`, a test pilnuje tej relacji.
- `PostsApiIT` ma pięć nowych testów regresji; każdy został raz uruchomiony na celowo zepsutym kodzie i zbladł na czerwono (dowód zapisany w Progress).
- `/?q=a%00b` w SPA ładuje feed z filtrem `ab`, a `/?page=99999999999` ładuje stronę 0, oba bez błędu. Testy Vitest i sprawdzenie przez `playwright-cli` na atrapie API.

### Key Discoveries:

- Dla `size ≤ 100` wystarczy statyczne `@Max(Integer.MAX_VALUE / 100)` (= 21 474 836) na `page`: `21474836 × 100 = 2 147 483 600 ≤ 2 147 483 647`. Obsługuje to istniejący `HandlerMethodValidationException` → 400 (`ApiExceptionHandler.java:37-41`), bez nowej ścieżki kodu.
- Lustro reguł SPA ↔ API: `rules.json` rejestruje limity kopiowane między API a SPA. Od przeglądu planu (F1) SPA też zna granicę `page`, więc faza 3 wpisuje ją do `rules.json` (`limits`) razem z testami po obu stronach (`SpaRulesTest`, `rules.test.ts`). Reguła „NUL usuwany z `q`” jest drugą nową kopią reguły API po stronie SPA; faza 3 sprawdza, czy zasada z `REPOSITORY_GUIDELINES.md` wymaga dla niej wpisu.
- `lessons.md`: „Kryterium, które agent sprawdzi poleceniem, zapisuj jako Automated” i „Stronę sprawdza agent przez playwright-cli” — dlatego ten plan nie ma wierszy Manual poza pytaniem o recenzję końcową.

## What We're NOT Doing

- Nie zmieniamy kształtu odpowiedzi ani kontraktu (`src/contract/*.json`), nie dodajemy kodu błędu — używamy istniejącego `invalid_request`.
- Nie dodajemy stronicowania kursorem ani nie zmieniamy limitu `size`.
- Nie naprawiamy w API odrzucania NUL (już działa); nie usuwamy NUL po stronie API. Nie zmieniamy `PAGE_SIZE` SPA (50).
- Nie robimy testów mutacyjnych (PIT) — to S-45. Celowe zepsucie w fazie 2 jest ręcznym dowodem dla pięciu nowych testów, nie systemem.
- Nie przebudowujemy `PostsApiIT` ani nie dzielimy go na klasy.
- Nie dotykamy `context/archive/` ani audytu.

## Implementation Approach

Trzy niezależne fazy, każda kończy się zielona i z własnym commitem. Faza 1 najpierw odtwarza defekt czerwonym testem na każdej z trzech list (to rozstrzyga niewiadomą z audytu o `FailedPostController` i `PipelineRunController`), potem naprawia. Faza 2 jest czystym dodawaniem testów; zepsucie kodu produkcyjnego robi się tylko lokalnie i jest cofane przed commitem. Faza 3 zmienia jedną funkcję w SPA.

## Phase 1: Granica numeru strony (API)

### Overview

Trzy listy odrzucają `page` ponad granicą 400-tką; granica jest jedną stałą powiązaną z maksymalnym `size`.

### Changes Required:

#### 1. Stała granicy

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/PageLimits.java` (nowy)

**Intent**: Jedno miejsce, w którym maksymalny `size` listy i wynikająca z niego największa dozwolona strona są zapisane razem, żeby nie dało się zmienić jednego bez drugiego.

**Contract**: Klasa końcowa ze stałymi czasu kompilacji: `MAX_SIZE = 100` i `MAX_PAGE = Integer.MAX_VALUE / MAX_SIZE` (adnotacja `@Max` wymaga stałej). Inwariant: `MAX_PAGE * MAX_SIZE ≤ Integer.MAX_VALUE`.

#### 2. Kontrolery list

**Files**: `PostController.java`, `FailedPostController.java` (`posts/api/`), `PipelineRunController.java` (`pipeline/api/`)

**Intent**: Dodać do `page` `@Max(PageLimits.MAX_PAGE)` i użyć `PageLimits.MAX_SIZE` zamiast literału `100` w `@Max` dla `size`. `PipelineRunController.MAX_PAGE_SIZE` zostaje publiczną nazwą (używa jej test/rejestr), ale jej wartość pochodzi z `PageLimits.MAX_SIZE`. Zaktualizować Javadoc „`page` from 0” o górną granicę.

**Contract**: `page` ∈ [0, `MAX_PAGE`]; poza zakresem → `HandlerMethodValidationException` → 400 `invalid_request` (bez zmian w `ApiExceptionHandler`).

#### 3. Testy

**Files**: `PostsApiIT.java` (rozszerzyć `badPagingIsAnInvalidRequest`), `PipelineRunControllerIT.java`, test dla listy nieudanych w `PostsApiIT` (lista nieudanych jest tam już testowana) oraz mały test jednostkowy `PageLimitsTest`.

**Intent**: Dla każdej z trzech list: (a) `page=21474837&size=100` → 400 `invalid_request`, także z `q=xtb` dla feedu (wariant natywnego zapytania z X6); (b) `page=21474836&size=100` → 200, `items` pusty, `total` poprawny; (c) `PageLimitsTest` pilnuje `MAX_PAGE * MAX_SIZE ≤ Integer.MAX_VALUE` (zgodność `MAX_SIZE` z `rules.json` pilnuje już `SpaRulesTest`). **Kolejność**: testy (a) najpierw na niezmienionym kodzie — muszą być czerwone na wszystkich trzech listach (potwierdza defekt); dopiero potem poprawka.

### Success Criteria:

#### Automated Verification:

- Defekt odtworzony: testy (a) dla trzech list są czerwone na kodzie sprzed poprawki (500 / `InvalidDataAccessApiUsageException`); nazwy testów i asercje, które padły, trafiają do opisu commita fazy, a wiersz Progress jest odhaczany dopiero po tym zapisie
- Po poprawce testy API przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- Celowe zepsucie: (i) `MAX_PAGE` zawężone do `Integer.MAX_VALUE / 1000` — test brzegu (b) czerwony; (ii) `MAX_PAGE` ustawione na `Integer.MAX_VALUE` — testy (a) i `PageLimitsTest` czerwone; (iii) usunięcie `@Max` z jednego kontrolera — jego test (a) czerwony; każda zmiana cofnięta; wyniki w opisie commita fazy
- Test `AuthorizationCompletenessIT` nadal zielony (żadna trasa nie dodana)

---

## Phase 2: Testy regresji feedu (API)

### Overview

Pięć brakujących ścieżek feedu dostaje test w `PostsApiIT`, każdy z dowodem, że czerwienieje na zepsutym kodzie.

### Changes Required:

#### 1. Pięć testów

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java` (z użyciem `PostSeeds`, `target(...)`, `output(...)` i `feed(...)` już w pliku)

**Intent**: Najpierw sprawdzić w pliku, co pokrywają istniejące testy (tabela w „Current State Analysis”), i dopisać tylko brakujące warianty — bez kopiowania istniejących. Testy:

1. **Remisy daty z filtrem** — kilka postów z tą samą `publishedAt`, pasujących do `q`; strony `size=2` są stabilne (id malejąco), `total` zgodny, żaden post nie powtarza się ani nie ginie między stronami. (Istniejący test remisów jest bez filtra; filtr idzie ścieżką natywnego SQL.)
2. **`kind` + `direction` na jednym celu** — post z dwoma celami: `company`/`not_expressed` i `macro_topic`/`bullish`. `kind=company&direction=bullish` nie pasuje (warunki na różnych celach), `kind=macro_topic&direction=bullish` pasuje.
3. **Ocena `ok` przed późniejszą `failed`** — post z oceną `ok`, potem nowsza `failed`: feed pokazuje ocenę `ok` (kształt `assessment`), a filtr patrzy na jej wynik.
4. **Strona za końcem** — `page` większe niż liczba stron: 200, `items` pusty, `total` bez zmian, `page`/`size` odbite; z filtrem i bez.
5. **Stanowisko ostrożne** — cel z `direction` i `assertiveness: hedged`: `direction=…` go znajduje, w widoku zachowuje `hedged`.

**Contract**: Tylko `src/test`. Żadnych zmian w kodzie produkcyjnym poza lokalnym, cofanym zepsuciem do dowodu.

#### 2. Dowód przez celowe zepsucie

**Intent**: Dla każdego z pięciu testów raz zepsuć kod produkcyjny w sposób, który ten test ma wykryć, zobaczyć czerwony wynik (nazwa testu i asercja do opisu commita), cofnąć zepsucie (`git checkout` pliku produkcyjnego). Propozycje: (1) `p.id DESC` → `p.id ASC` w `PostRepository.findMatching`; (2) w `MATCHING` ocenić `kind` na `latest.output -> 'targets' -> 0` zamiast na elemencie `t`; (3) usunąć `a.status = :status` z `MATCHING` (filtr zacznie patrzeć na najnowszą ocenę każdego statusu); jeśli test 3 sprawdza też kształt `assessment` w feedzie bez filtra, drugie zepsucie: usunąć filtr statusu w `AssessmentRepository.findLatestOk`; (4) `found.getTotalElements()` → `found.getContent().size()` w `PostController.list`; (5) w filtrze `direction` dodać `AND t -> 'stance' ->> 'assertiveness' IS DISTINCT FROM 'hedged'`. Jeśli zepsucie nie zaczerwieni testu, test jest poprawiany (nie zepsucie osłabiane).

### Success Criteria:

#### Automated Verification:

- Nowe testy przechodzą: `cd szczur-radar-api && ./mvnw -B verify -Dit.test=PostsApiIT`
- Każdy z pięciu testów jest czerwony na swoim zepsuciu (pięć wpisów: zepsucie → czerwony test i asercja, w opisie commita fazy; wiersz odhaczany po tym zapisie), a po cofnięciu `git diff --stat -- szczur-radar-api/src/main` jest pusty
- Pełny zestaw API: `cd szczur-radar-api && ./mvnw -B verify`

---

## Phase 3: NUL i ogromny `page` w linku feedu (SPA)

### Overview

Link z U+0000 w `q` filtruje po tekście bez tego znaku, a link z `?page=` ponad granicą API otwiera stronę 0; w obu przypadkach feed się ładuje.

### Changes Required:

#### 1. Normalizacja `q`

**File**: `szczur-radar-web/src/feedFilter.ts`

**Intent**: `normalizeQuery` usuwa każdy U+0000 z tekstu (najpierw usunięcie, potem obcięcie do 100 znaków i białe znaki na brzegach, żeby wynik był tym, co API zaakceptuje). Pusty wynik = brak warunku, jak dziś. Zaktualizować komentarz nagłówkowy pliku o NUL.

**Contract**: `normalizeQuery('a\u0000b') === 'ab'`; `filterFromQuery({ q: 'a\u0000b' }).q === 'ab'`; `normalizeQuery('\u0000') === undefined`; wynik nigdy nie zawiera U+0000.

#### 2. Granica `page` w adresie

**Files**: `szczur-radar-web/src/views/FeedView.vue` (computed `page`), `szczur-radar-web/src/contract/rules.json`, `rules.test.ts`, `szczur-radar-api/src/test/java/pl/szczurradar/api/contract/SpaRulesTest.java`

**Intent**: `?page=` większe niż granica API jest traktowane jak wartość nieznana i daje stronę 0 (jak nieznane `kind`/`direction` w `filterFromQuery`), więc SPA nigdy nie wysyła strony, którą API odrzuci. Granica to nowy wpis `limits` w `rules.json` (nazwa do ustalenia przy wdrożeniu, np. `feedPageMax`), równy `PageLimits.MAX_PAGE`; `SpaRulesTest` porównuje go z serwerem, `rules.test.ts` ze stałą SPA.

**Contract**: `?page=21474836` → strona 21474836, `?page=21474837` i `?page=99999999999` → strona 0, `?page=-1` i `?page=x` → strona 0 jak dziś.

#### 3. Test i rejestr reguły NUL

**Files**: `szczur-radar-web/src/feedFilter.test.ts`, ewentualnie `szczur-radar-web/src/contract/rules.json` + `rules.test.ts`

**Intent**: Dodać przypadki do `feedFilter.test.ts` (NUL w środku, na brzegu, sam NUL, NUL przy obcięciu do 100 znaków). Sprawdzić zasadę z `REPOSITORY_GUIDELINES.md` („a new copy of a rule on the SPA side goes into the registry”): jeśli rejestr ma miejsce na regułę znaków niedozwolonych w `q`, wpisać ją po obu stronach (`SpaRulesTest` ↔ `rules.test.ts`); jeśli nie — opisać w komentarzu pliku, że to lustro `@Pattern` w `PostController`, i nie rozbudowywać rejestru (zakres: `lessons.md`, „Uzasadniaj zakres celem użytkownika”).

#### 4. Sprawdzenie na stronie

**Intent**: Agent odtwarza defekty i poprawki na atrapie API (`dev/mock-api.js`, tryb domyślny): `npm run dev` z atrapą, `playwright-cli` otwiera `/?q=a%00b` i `/?page=99999999999`. Przed poprawką widoczny stan błędu feedu (atrapa musi odpowiadać 400 także na `page` ponad granicą — jeśli nie, dopisać to w `dev/mock-api.js` zgodnie z API); po poprawce lista postów, pole filtra z tekstem `ab` i pierwsza strona. Zrzut ekranu w jasnym i ciemnym motywie nie jest potrzebny — zmiana nie dotyka wyglądu.

### Success Criteria:

#### Automated Verification:

- Testy SPA: `cd szczur-radar-web && npm test`
- Typecheck i build: `cd szczur-radar-web && npm run build`
- Celowe zepsucie: usunięcie kroku usuwania U+0000 z `normalizeQuery` oraz usunięcie granicy `page` w `FeedView` — nowe przypadki w testach czerwone, zmiany cofnięte; wyniki w opisie commita fazy
- Rejestr: `SpaRulesTest` i `rules.test.ts` zielone z nowym wpisem granicy `page`
- `playwright-cli`: `/?q=a%00b` i `/?page=99999999999` na atrapie API pokazują feed (lista postów, brak komunikatu błędu), a bez poprawki — błąd (snapshoty opisane w commicie fazy)

---

## Testing Strategy

### Unit Tests:

- `PageLimitsTest` (relacja stałych), `feedFilter.test.ts` (NUL), test granicy `page` w SPA.

### Integration Tests:

- `PostsApiIT`: pięć nowych testów feedu i granica `page` dla feedu oraz listy nieudanych; `PipelineRunControllerIT`: granica `page` dla przebiegów.

### Manual Testing Steps:

1. Brak kroków ręcznych. Wygląd strony sprawdza agent przez `playwright-cli` (faza 3); merge i weryfikacja w Railway PR env po stronie człowieka według `REPOSITORY_GUIDELINES.md` (poza Progress).

## Performance Considerations

Brak. `@Max` jest sprawdzane przed zapytaniem; strony powyżej granicy nie dochodzą do bazy.

## Migration Notes

Brak migracji, brak zmiany schematu ani kontraktu. Klient wysyłający `page` ponad 21 474 836 dostaje 400 zamiast 500 — żaden znany klient tego nie robi.

## References

- Audyt: `context/changes/post-m3-verification-audit/research.md` (§4 „Feed”, eksperyment X6, „Open Questions”)
- Roadmapa: `context/foundation/roadmap.md`, sekcja S-44
- Obsługa błędów: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/ApiExceptionHandler.java:37-41`
- Kontrolery: `PostController.java:86`, `FailedPostController.java:49`, `PipelineRunController.java:113`
- Zapytanie filtra: `PostRepository.java:112-124`
- Normalizacja `q` w SPA: `szczur-radar-web/src/feedFilter.ts`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Granica numeru strony (API)

#### Automated

- [x] 1.1 Defekt odtworzony: testy dla trzech list czerwone na kodzie sprzed poprawki — 023a51f
- [x] 1.2 Testy API przechodzą po poprawce: `./mvnw -B verify` — 023a51f
- [x] 1.3 Celowe zepsucie granicy lub usunięcie `@Max` zaczerwienia testy, zmiana cofnięta — 023a51f
- [x] 1.4 `AuthorizationCompletenessIT` nadal zielony — 023a51f

### Phase 2: Testy regresji feedu (API)

#### Automated

- [x] 2.1 Pięć nowych testów przechodzi: `./mvnw -B verify -Dit.test=PostsApiIT` — d8c234b
- [x] 2.2 Każdy z pięciu testów czerwony na swoim celowym zepsuciu, zepsucie cofnięte — d8c234b
- [x] 2.3 Pełny zestaw API zielony: `./mvnw -B verify` — d8c234b

### Phase 3: NUL i ogromny `page` w linku feedu (SPA)

#### Automated

- [x] 3.1 Testy SPA przechodzą: `npm test`
- [x] 3.2 Typecheck i build przechodzą: `npm run build`
- [x] 3.3 Celowe zepsucie normalizacji i granicy `page` zaczerwienia nowe przypadki, zmiany cofnięte
- [x] 3.4 `playwright-cli`: `/?q=a%00b` i `/?page=99999999999` na atrapie API pokazują feed, a bez poprawki błąd
- [x] 3.5 `SpaRulesTest` i `rules.test.ts` zielone z nowym wpisem granicy `page`
