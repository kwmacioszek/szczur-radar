# Filtrowanie po ocenie LLM (S-04) — Implementation Plan

## Overview

Feed `/` dostaje filtr po ocenie LLM (FR-011a, US-01): tekst dopasowany do fragmentu wskazującego cel, rodzaj celu i stanowisko autora wobec **tego samego** celu. Wszystkie podane warunki muszą być spełnione przez jeden element `targets[]` najnowszej oceny `ok` posta. Nad listą stoi formularz i liczba wyników, pusty wynik filtra ma własny stan, a karta pokazuje rodzaj każdego celu. Bez filtra feed działa jak dziś.

Źródła: `research.md` (kształt kontraktu, dane, konwencje), plan zarchiwizowanej zmiany `feed-view-ui-hardening` (PR #48, `404679f`), który przekazał tej zmianie D2 (filtry i ich stan pusty) i D3 (układ nad listą).

## Current State Analysis

- **Dane.** Cele i stanowiska leżą w JSONB `assessments.output` (`V3__create_pipeline_and_posts.sql:139`). Element `targets[]` to `{id, kind, anchor{document, field, quote}, stance{direction, assertiveness, claim_ids}, reference}` (`agents/assessor/contract/Target.java:10-11`). Stanowisko jest zagnieżdżone w celu, więc „ten sam cel” znaczy „ten sam element tablicy”. `anchor.quote` jest dosłowny (1–100 znaków, D6), bez nazwy znormalizowanej (`research.md` § 1–2).
- **API.** `GET /api/posts` najpierw stronicuje `posts` w etapie `assessed` pochodnym zapytaniem `findByStageIn` z sortowaniem `NEWEST_FIRST` (`PostController.java:47`, `PostRepository.java:59-63`), a dopiero potem dociąga najnowszą ocenę `ok` (`AssessmentRepository.java:20-31`). Filtr po JSONB musi więc wejść do zapytania stronicującego, inaczej `total` i strony będą błędne.
- **Wiązanie parametrów.** Wartości kontraktu są małymi literami (`TargetKind.value()`, `Direction.value()` z `@JsonValue`), a Spring wiąże enum po nazwie stałej. W `src/main` nie ma `WebMvcConfigurer` ani konwertera `String` → enum. Błędny parametr już daje 400 `{"detail":"invalid_request"}` (`config/ApiExceptionHandler.java:37-41`).
- **SPA po #48.** `FeedView.vue` trzyma pokazaną stronę jako parę (żądany numer, odpowiedź) w `shown` (`:20`), numeruje wczytania i odrzuca spóźnione (`:25`, `:41-56`), przy zmianie strony przewija na górę i po wczytaniu ustawia fokus na „Posty” (`:69-77`). Ma stany: wczytywanie, błąd z „Spróbuj ponownie”, pusto, strona poza zakresem, lista z pagerem (`:85-105`). Stronę czyta z `?page=`, a `goTo` zachowuje inne klucze query (`:63-65`).
- **Karta.** `PostCard.vue:29-36` pokazuje cel jako `dt` (fragment) i `dd` (kierunek, „(ostrożnie)” dla `hedged`). Rodzaju celu nie pokazuje. `labels.ts` nie ma zbioru `kind`, a w `api/posts.ts:14` `kind` to `string`.
- **Pola formularza.** Brak pola tekstowego i `<select>` w aplikacji. Wzorzec to globalny styl pola w `style.css` (`input[type='date']`, `:149-157`, checkbox `:159-161`), pokazany w sekcji „Pola” kitchen sink (`KitchenSinkView.vue:179-187`); formularz w widoku: `<form>` z `<label>Tekst <input/></label>` i `AppButton type="submit" variant="primary"` (`AdminView.vue:178-211`).
- **Bramka wizualna.** `dev/mock-api.js` obsługuje `/api/posts` (120 postów, tryby `data`/`loading`/`error`/`empty`/`loading-next`/`error-next`, `:95-105`), ale nie czyta parametrów filtra, a wszystkie cele mają rodzaj `company` (`:58-62`). To samo dotyczy danych `PostCard` na kitchen sink (`KitchenSinkView.vue:50-57`).
- **Równoległe zmiany na tym samym widoku:** PR #50 (S-05) przenosi `h1` przed `<section>` i dodaje pod nim etykietę świeżości (`FeedView.vue`, `FeedView.test.ts`, `dev/mock-api.js`); S-03 `post-detail-view` (jeszcze bez planu) zmieni `PostCard` i doda trasę w `PostController`. Konflikty rozwiązuje zmiana mergowana później.

## Desired End State

- `GET /api/posts?page=&size=&q=&kind=&direction=`: bez `q`/`kind`/`direction` (lub z pustym `q`) odpowiedź jak dziś; z dowolnym z nich tylko posty `assessed`, których najnowsza ocena `ok` ma co najmniej jeden element `targets[]` spełniający jednocześnie wszystkie podane warunki: `anchor.quote` zawiera `q` bez rozróżniania wielkości liter, `kind` równe, `stance.direction` równe. Kolejność `published_at DESC, id DESC`, `total` = liczba pasujących postów. Nieznana wartość `kind`/`direction` albo `q` dłuższe niż 100 znaków → 400 `invalid_request`.
- Feed `/`: nad listą formularz „Spółka lub temat” (pole wyszukiwania), „Rodzaj celu” (dowolny + 6), „Stanowisko” (dowolne + 6), „Filtruj” i — gdy filtr jest aktywny — „Wyczyść filtry”. Filtr żyje w adresie (`/?q=cdr&kind=company&direction=bullish`), więc odświeżenie, link i wstecz/dalej go zachowują; zatwierdzenie zeruje stronę.
- Nad listą linia wyniku: „Ocenione posty: N” bez filtra, „Pasujące do filtra: N” z filtrem. Pusty wynik filtra: „Żaden post nie pasuje do filtra.” z „Wyczyść filtry”, inny niż „Brak ocenionych postów…”. Stronicowanie tylko pod listą, strona 50 (D3 rozstrzygnięte).
- Karta pokazuje przy każdym celu jego rodzaj po polsku (np. „CDR · spółka — wzrostowo”).
- Weryfikacja: `./mvnw -B verify`, `npm test`, `npm run build` zielone; `ui-after/README.md` z macierzą 7 stanów w obu motywach przy 1280 i 375 px; filtr w Railway PR env na prawdziwych danych.

### Key Discoveries:

- Gotowy przypadek „ten sam cel”: `ContractFixtures.validOutput()` ma `XTB` `bullish` i `CDR` `not_expressed` (`ContractFixtures.java:52-58`) — filtr `q=cdr&direction=bullish` nie może zwrócić tego posta.
- `ContractFixtures.target(...)` zawsze tworzy rodzaj `COMPANY` (`ContractFixtures.java:71-75`), a `PostSeeds.ok(postId, assessedAt)` zawsze zapisuje `validOutput()` (`PostSeeds.java:78-82`) — testy filtra potrzebują przeciążeń z rodzajem i z własnym wyjściem.
- `PostsApiIT` celowo zakłada dwie oceny `ok` jednego posta (`PostsApiIT.java:120-132`): „najnowsza `ok`” trzeba testować, unikalnego indeksu nie dodajemy.
- Indeks `ix_assessments_post_latest (post_id, assessed_at DESC, id DESC)` (`V3__…sql:178`) obsługuje wybór najnowszej oceny `ok` na post.
- Testcontainers `postgres:18` i lokalny obraz Railway mają `en_US.utf8` (libc), w którym `ILIKE` składa wielkość polskich liter (`research.md` § 5); lokalizacji produkcyjnej bazy nie sprawdzano.
- Testy, które zmiana sygnatury dotyka: `FeedView.test.ts` (`toHaveBeenLastCalledWith(n, 50)` w 5 miejscach), `api/pipeline.test.ts:42-52` (dokładny URL `/api/posts?page=2&size=50` — ma zostać bez zmian), `PostCard.test.ts`.

## What We're NOT Doing

- Wielokrotny wybór rodzaju lub kierunku, filtr ostrożności (`hedged`/`unhedged`), dopasowanie bez polskich znaków (`unaccent`), dopasowanie początku słowa.
- Rozpoznawanie spółek i synonimów (`PKN` ↔ `Orlen`, `CDR` ↔ `CD Projekt`) — poza MVP (PRD FR-011a, Non-Goals).
- Wyróżnianie na karcie celu, który pasuje do filtra (wymagałoby powtórzenia dopasowania serwera w SPA).
- Drugi pasek stronicowania nad listą i zmiana rozmiaru strony (D3: liczba wyników nad listą, pager na dole, 50).
- Filtr po typie posta, dacie, trafności; sortowanie (FR-008, FR-011b); zapisane filtry; filtrowanie na żywo w trakcie pisania.
- Migracje, indeksy, denormalizacja celów (`post_targets`), unikalny indeks ocen `ok`; zmiana kształtu odpowiedzi `/api/posts` (bez echa filtra).
- `LoginView` (zmiana `login-view-ui-hardening`), `AppHeader` przy 375 px (D4 z #48).

## Implementation Approach

Trzy fazy: API, SPA, stany i weryfikacja. Faza 1 jest kompletna sama w sobie (testy IT, bez UI). Faza 2 przestrzega kontraktu UI repozytorium po #48: role i skala z `style.css`, `AppButton`/`StatusMessage`/`StatusBadge`, nowy komponent w `src/components/` i na kitchen sink, plik na liście `CLEANED`. Faza 3 domyka macierz 7 stanów zrzutami z `dev/mock-api.js`, regułę w `REPOSITORY_GUIDELINES.md` i sprawdzenie w Railway PR env.

Bez filtra API zostaje na dzisiejszej ścieżce (`findPage`), więc istniejące zachowanie i testy feedu się nie zmieniają; z filtrem idzie jedno natywne zapytanie z osobnym zliczeniem. Oceny do wyświetlenia w obu przypadkach przychodzą z istniejącego `findLatestOk`.

Proces (reguły repo, nie wiersze Progress): gałąź `feature/filter-by-assessment` stoi na `origin/main` `002da67` bez commitów; na starcie fazy 1 `/10x-implement` robi `git fetch` i — jeśli `main` poszedł dalej — przesuwa gałąź przed pierwszym commitem. Issue slice'a to #17 (`S-04: …`): komentarz z change id, gałęzią i PR, PR z `Refs #17`; folder zmiany (z `roadmap.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`) w pierwszym commicie. Implementacja, PR env, `/10x-impl-review`, `/10x-archive` na tej gałęzi; merge robi człowiek. Po merge (runbook w issue #17, nie w Progress): wyszukiwanie z polską wielką literą i filtr „ten sam cel” na produkcji, potem zamknięcie issue.

## Critical Implementation Details

- **Natywne zapytanie: kolejność i typy.** `NEWEST_FIRST` używa nazw pól encji (`publishedAt`), których natywny SQL nie zna — zapytanie ma własne `ORDER BY p.published_at DESC, p.id DESC` i dostaje `PageRequest` bez sortowania, z jawnym `countQuery`. Parametry, które mogą być `null`, trzeba rzutować (`CAST(:kind AS text)`), inaczej Postgres odpowie „could not determine data type of parameter”.
- **Jeden element, najnowsza ocena.** Warunki tekstu, rodzaju i kierunku stoją w jednym `EXISTS` po `jsonb_array_elements(<output najnowszej oceny ok> -> 'targets')`; osobne `EXISTS` na każde pole odtwarzają błąd z ryzyka roadmapy. Najnowsza ocena `ok` = ta sama reguła co `findLatestOk` (`assessed_at DESC, id DESC`), np. przez `JOIN LATERAL (… LIMIT 1)`.
- **Ucieczka w `ILIKE`.** `q` jest przycinane, a `%`, `_` i znak ucieczki są escapowane w Javie przed złożeniem wzorca `%…%`; zapytanie podaje ten sam znak w `ESCAPE`. Bez tego `q=%` pasuje do wszystkiego.
- **Wynik filtra dla czytnika ekranu.** Zatwierdzenie filtra nie przewija i nie przenosi fokusu (zostaje w formularzu). Linia wyniku jest w regionie `role="status"`, który istnieje w DOM przed zmianą tekstu — region dodawany razem z tekstem nie jest ogłaszany niezawodnie.

## Phase 1: API — filtr w `GET /api/posts`

### Overview

Trzy opcjonalne parametry listy, jedno zapytanie dla trybu z filtrem, testy IT na prawdziwym Postgresie.

### Changes Required:

#### 1. Wiązanie wartości kontraktu w query

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/config/WebConfig.java` (nowy)

**Intent**: Parametry `kind` i `direction` przyjmują te same wartości, które SPA dostaje w `assessment.output` (małe litery), a nie nazwy stałych.

**Contract**: `@Configuration` z `WebMvcConfigurer.addFormatters`, rejestruje konwertery `String` → `TargetKind` i `String` → `Direction` po `value()` (jeden generyczny konwerter na wzór `persistence/EnumValueConverter`). Nieznana wartość (także `BULLISH`) rzuca wyjątek, który Spring opakowuje w `MethodArgumentTypeMismatchException` → istniejące 400 `invalid_request`.

#### 2. Filtr jako wartość

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/PostFilter.java` (nowy)

**Intent**: Jedno miejsce na znaczenie „brak warunku” i normalizację tekstu, zanim trafi do zapytania.

**Contract**: `record PostFilter(@Nullable String quote, @Nullable TargetKind kind, @Nullable Direction direction)`; fabryka przycina `quote` i zamienia pusty tekst na `null`; `isEmpty()`; metoda zwracająca wzorzec `ILIKE` z ucieczką (`%`, `_`, znak ucieczki) albo `null`.

#### 3. Zapytanie stronicujące z filtrem

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/PostRepository.java`

**Intent**: Strona i `total` liczone po filtrze w bazie.

**Contract**: `default Page<Post> findAssessedPage(PostFilter filter, int page, int size)` — dla `filter.isEmpty()` deleguje do dzisiejszego `findPage(List.of(PostStage.ASSESSED), page, size)`, w przeciwnym razie woła natywne zapytanie z `countQuery`: posty `stage = 'assessed'` złączone z ich najnowszą oceną `status = 'ok'`, warunek `EXISTS` po elementach `output -> 'targets'` z `(:pattern IS NULL OR t -> 'anchor' ->> 'quote' ILIKE :pattern ESCAPE …) AND (:kind IS NULL OR t ->> 'kind' = :kind) AND (:direction IS NULL OR t -> 'stance' ->> 'direction' = :direction)` (parametry rzutowane na `text`, wartości enumów przez `value()`), `ORDER BY published_at DESC, id DESC`. Javadoc interfejsu opisuje „ten sam element” i „najnowsza `ok`”.

#### 4. Parametry trasy

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java`

**Intent**: Lista przyjmuje filtr; kształt odpowiedzi bez zmian.

**Contract**: `list(page, size, @RequestParam(required = false) @Size(max = 100) @Nullable String q, @RequestParam(required = false) @Nullable TargetKind kind, @RequestParam(required = false) @Nullable Direction direction)` → `posts.findAssessedPage(PostFilter.of(q, kind, direction), page, size)`, dalej jak dziś (`findLatestOk`, `PostPage`). Javadoc klasy i metody: FR-011a, znaczenie parametrów, 400 dla nieznanej wartości i `q` > 100 znaków.

#### 5. Dane testowe

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/agents/assessor/ContractFixtures.java`, `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/PostSeeds.java`

**Intent**: Testy filtra potrzebują celów innych rodzajów i ocen z własnym wyjściem.

**Contract**: `ContractFixtures.target(...)` z parametrem `TargetKind` (dotychczasowe przeciążenia bez zmian, nadal `COMPANY`); `PostSeeds.ok(UUID postId, String assessedAt, AssessmentOutputV2 output)` (dotychczasowe `ok(postId, assessedAt)` deleguje z `validOutput()`).

#### 6. Testy IT filtra

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java`

**Intent**: Każda reguła filtra ma test, który bez niej by nie przeszedł.

**Contract**: Nowe przypadki (sesja `USER`, jak istniejące):
- (a) ten sam cel: post z `validOutput()` — `q=cdr&direction=bullish` → `total` 0; `q=xtb&direction=bullish` → 1; `q=CDR&direction=not_expressed` → 1;
- (b) wielkość liter i polskie litery: cel `ŻABKA` — `q=żabka` → 1, `q=zabka` → 0 (brak `unaccent` udokumentowany testem);
- (c) rodzaj: post z celem `rynek` (`macro_topic`) i post z celem `Orlen` (`company`) — `kind=macro_topic` → tylko pierwszy; `q=orlen&kind=macro_topic` → 0;
- (d) ucieczka: `q=%` i `q=_` nie pasują do celów bez tych znaków;
- (e) najnowsza `ok`: starsza ocena `ok` z celem `CDR`, nowsza z celem `XTB` — `q=cdr` → 0, `q=xtb` → 1;
- (f) stronicowanie z filtrem: 3 pasujące z 4, `size=2` — strona 0 ma 2 najnowsze, strona 1 ma 1, `total` 3 na obu;
- (g) wykluczenia: post z wynikiem prefiltra (`targets: []`) i post w etapie `stored`/`failed` nie wchodzą przy żadnym filtrze; `q=%20` (samo białe znaki) działa jak brak filtra;
- (h) 400 `{"detail":"invalid_request"}`: `kind=spolka`, `direction=BULLISH`, `q` o długości 101.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą, w tym przypadki (a)–(h): `cd szczur-radar-api && ./mvnw -B verify`
- Istniejące testy feedu bez zmian przechodzą (bez filtra odpowiedź jak dziś): `cd szczur-radar-api && ./mvnw -B verify -Dit.test=PostsApiIT`
- Trasa nadal wymaga sesji: `AuthorizationCompletenessIT` przechodzi bez zmian listy tras (część `./mvnw -B verify`)

#### Manual Verification:

- Lokalnie (`compose.local.yaml` + jar, kilka ocenionych postów) `curl` z ciasteczkiem sesji: `/api/posts?q=<fragment z prawdziwego posta>` i `…&direction=…` dają oczekiwane posty i `total`; `/api/posts?kind=xyz` → 400

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 2: SPA — filtr w feedzie

### Overview

Klient API, stan filtra w adresie, formularz jako współdzielony komponent, liczba wyników, pusty wynik filtra, rodzaj celu na karcie, mock z filtrowaniem.

### Changes Required:

#### 1. Klient i typy

**File**: `szczur-radar-web/src/api/posts.ts`

**Intent**: Zamknięte zbiory rodzajów i kierunków w jednym miejscu (źródło typów i opcji formularza); filtr w żądaniu.

**Contract**: `export const TARGET_KINDS = ['company', 'asset', 'sector', 'macro_topic', 'other', 'unknown'] as const` i `TargetKind` z niego; analogicznie `DIRECTIONS` dla istniejącego `Direction`; `Target.kind: TargetKind`. `export interface PostFilter { q?: string; kind?: TargetKind; direction?: Direction }`. `fetchPosts(page: number, size = 50, filter: PostFilter = {})` składa URL przez `URLSearchParams`: `page`, `size`, potem tylko podane klucze filtra — bez filtra URL bez zmian (`/api/posts?page=2&size=50`).

#### 2. Filtr ⇄ adres

**File**: `szczur-radar-web/src/feedFilter.ts` (nowy), `szczur-radar-web/src/feedFilter.test.ts` (nowy)

**Intent**: Jedna reguła czytania filtra z adresu i zapisu do adresu; spreparowany link nie kończy się 400.

**Contract**: `filterFromQuery(query: LocationQuery): PostFilter` — `q` przycięte, puste pomijane, ucięte do 100 znaków; `kind`/`direction` tylko ze znanych wartości (inne pomijane); `filterToQuery(filter): Record<string, string>` — tylko niepuste klucze; `isFilterActive(filter)`; `sameFilter(a, b)`. Testy: znane i nieznane wartości, białe znaki, 101 znaków, tablica w query (`?q=a&q=b`) → pierwsza wartość.

#### 3. Etykiety rodzajów

**File**: `szczur-radar-web/src/labels.ts`

**Intent**: Polskie nazwy rodzajów dla formularza i karty (z definicji `contract-v2.md:153`).

**Contract**: Zbiór `kind`: `company` spółka, `asset` aktywo, `sector` sektor, `macro_topic` temat makro, `other` inny, `unknown` nieustalony.

#### 4. Pola wyszukiwania i wyboru

**File**: `szczur-radar-web/src/style.css`

**Intent**: Nowe pola wyglądają jak istniejące pole daty; bez nowych tokenów i komponentów pól.

**Contract**: Reguła `input[type='date']` (`:149-157`) obejmuje też `input[type='search']` i `select` (te same pismo, odstępy ze skali, obramowanie `--border-strong`, `--radius`, tło przezroczyste, tekst `--text-h`); `select:disabled` czytelny jak przycisk wyłączony. Fokus z istniejącego `:focus-visible` (`--ring`).

#### 5. Formularz filtra

**File**: `szczur-radar-web/src/components/FeedFilter.vue` (nowy), `szczur-radar-web/src/components/FeedFilter.test.ts` (nowy)

**Intent**: Formularz jako komponent: testowalny osobno, pokazany na kitchen sink we wszystkich stanach.

**Contract**:
- Props `filter: PostFilter` (filtr zastosowany, z adresu); emits `apply(filter: PostFilter)` i `clear()`.
- `<form role="search" aria-label="Filtr postów">`: `<label>Spółka lub temat <input type="search" maxlength="100"></label>`, `<label>Rodzaj celu <select>` („dowolny” + 6 z `label('kind', …)`), `<label>Stanowisko <select>` („dowolne” + 6 z `label('direction', …)`), `AppButton type="submit" variant="primary"` „Filtruj”, `AppButton type="button"` „Wyczyść filtry” tylko gdy `filter` jest aktywny.
- Zatwierdzenie (przycisk lub Enter w polu) emituje `apply` z przyciętym tekstem i bez pustych kluczy. Zmiana propsa `filter` (wstecz/dalej, wyczyszczenie) nadpisuje pola.
- Układ: pola w wierszu z zawijaniem, odstępy z `--space-*`; przy 375 px jedno pod drugim na pełną szerokość, bez poziomego przewijania.
- Testy: etykiety i opcje (7 + 7), `apply` z przyciętym tekstem, Enter zatwierdza, „Wyczyść filtry” widoczny tylko przy aktywnym filtrze i emituje `clear`, zmiana propsa aktualizuje pola.

#### 6. Widok listy

**File**: `szczur-radar-web/src/views/FeedView.vue`

**Intent**: Filtr z adresu steruje wczytaniem; wynik jest widoczny i ogłaszany; zachowanie stronicowania z #48 bez zmian.

**Contract**:
- `filter` = `filterFromQuery(route.query)`; wczytanie `fetchPosts(page, PAGE_SIZE, filter)`; `shown` zapamiętuje także filtr odpowiedzi; numeracja wczytań bez zmian (spóźniona odpowiedź starszego filtra jest odrzucana).
- Obserwowany klucz = strona + filtr. Zmiana samej strony: jak dziś (przewinięcie od razu, fokus na `h1` po wczytaniu). Zmiana filtra: bez przewinięcia i bez przenoszenia fokusu. Pierwsze wejście bez obu.
- `FeedFilter` nad listą, poza łańcuchem stanów i poza regionem `aria-busy`; `apply` → `router.push({ query: filterToQuery(f) })` (strona znika z adresu = 0), `clear` → `router.push({ query: {} })`. `goTo` zachowuje klucze filtra (już robi to `...route.query`).
- Linia wyniku nad listą w regionie `role="status"` obecnym w DOM od początku: po udanym wczytaniu „Ocenione posty: {total}” (bez filtra, `total > 0`), „Pasujące do filtra: {total}” (z filtrem, `total > 0`), „Żaden post nie pasuje do filtra.” (z filtrem, `total === 0`); w trakcie wczytywania i po błędzie pusta.
- Łańcuch stanów: pusty wynik filtra (`total === 0` i filtr aktywny) pokazuje `AppButton` „Wyczyść filtry” (to samo co `clear`); `total === 0` bez filtra — dotychczasowe „Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.”; strona poza zakresem, błąd z ponowieniem, wczytywanie, lista z pagerem — bez zmian.

#### 7. Rodzaj celu na karcie

**File**: `szczur-radar-web/src/components/PostCard.vue`, `szczur-radar-web/src/components/PostCard.test.ts`

**Intent**: Na liście widać, czemu post pasuje do filtra po rodzaju (np. Orlen jako spółka i jako temat makro wyglądają inaczej).

**Contract**: W `dt` po fragmencie rodzaj z `label('kind', target.kind)` jako drugorzędny tekst (`--text`, bez wagi 600, rozdzielony od fragmentu, np. „CDR · spółka”); kierunek w `dd` bez zmian. Bez wyróżniania celów. Test: fragment i rodzaj w `dt`, nieznany rodzaj pokazany tak, jak przyszedł.

#### 8. Kitchen sink i kontrola

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`, `szczur-radar-web/src/styleContract.test.ts`

**Intent**: Nowy komponent i nowe pola na stronie kontrolnej; plik pod skanem literałów.

**Contract**: Sekcja „Pola”: pole wyszukiwania (puste, z tekstem) i `<select>` (zwykły, wyłączony). Nowa sekcja „Filtr” (`aria-labelledby="filter-title"`): `FeedFilter` bez filtra i z aktywnym filtrem (`q`, `kind`, `direction`, widoczny „Wyczyść filtry”). Dane sekcji „Posty”: cele co najmniej czterech rodzajów (`company`, `macro_topic`, `asset`, `sector`). `src/components/FeedFilter.vue` na liście `CLEANED` (z importem `?raw`).

#### 9. Mock `/api/posts` z filtrem

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Zrzuty filtra bez działającego API, z tą samą semantyką co API.

**Contract**: `/api/posts` czyta `q`, `kind`, `direction` i zostawia posty, których któryś cel spełnia wszystkie podane warunki naraz (`quote.toLocaleLowerCase('pl')` zawiera `q.trim().toLocaleLowerCase('pl')`); `total` po filtrze; nieznany `kind`/`direction` → 400 `{"detail":"invalid_request"}`. Warianty postów dostają cele innych rodzajów (np. `rynek` `macro_topic`, `ropa` `asset`, `banki` `sector`). Nagłówek: parametry filtra i przykład pustego wyniku (`/?q=zzz`). Tryby i `/api/admin/*` bez zmian.

#### 10. Testy widoku i klienta

**File**: `szczur-radar-web/src/views/FeedView.test.ts`, `szczur-radar-web/src/api/pipeline.test.ts`

**Intent**: Każde zachowanie filtra w widoku ma test; dotychczasowe przypadki zostają.

**Contract**: Istniejące asercje `toHaveBeenLastCalledWith(n, 50)` → `(n, 50, {})`. Nowe przypadki: (a) `/?q=cdr&direction=bullish` → `fetchPosts(0, 50, { q: 'cdr', direction: 'bullish' })`, pola formularza wypełnione; (b) zatwierdzenie filtra na `/?page=2` → adres bez `page`, `fetchPosts(0, 50, {…})`, bez `window.scrollTo` i bez fokusu na `h1`; (c) „Starsze” przy aktywnym filtrze → `fetchPosts(1, 50, {…})`; (d) linia wyniku: „Ocenione posty: 120” i „Pasujące do filtra: 3” w elemencie `[role="status"]`; (e) pusty wynik filtra: „Żaden post nie pasuje do filtra.”, kliknięcie „Wyczyść filtry” → `fetchPosts(0, 50, {})`, adres `/`; (f) `/?kind=xyz` → `fetchPosts(0, 50, {})`; (g) odpowiedź starszego filtra rozwiązana po nowszym nie zmienia widoku. `pipeline.test.ts`: URL bez filtra bez zmian, z filtrem `/api/posts?page=0&size=50&q=%C5%BCabka&kind=company&direction=bullish`.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą, w tym `feedFilter.test.ts`, `FeedFilter.test.ts`, przypadki (a)–(g) i skan `no literals` z `FeedFilter.vue`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą, a `dist/` nie zawiera kitchen sink: `cd szczur-radar-web && npm run build && ! grep -ri kitchen dist/`
- `dev/mock-api.js` parsuje się: `node --check szczur-radar-web/dev/mock-api.js`

#### Manual Verification:

- `/` z `dev/mock-api.js` (tryb `data`) przy 1280×900 i 375×812, oba motywy: formularz nad listą, linia wyniku, filtr `q=orlen` + `Stanowisko: wzrostowo` zawęża listę, `/?q=zzz` pokazuje pusty wynik filtra z „Wyczyść filtry”, karta pokazuje rodzaj przy celu, bez poziomego przewijania przy 375 px
- `/kitchen-sink`, sekcje „Pola”, „Filtr” i „Posty” w obu motywach: pola i formularz zgodne z polem daty i przyciskami

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 3: Stany, zrzuty i weryfikacja w PR env

### Overview

Macierz 7 stanów dla formularza i nowych stanów listy, reguła dla następnego agenta, filtr na prawdziwych danych w Railway PR env.

### Changes Required:

#### 1. Macierz 7 stanów na zrzutach

**File**: `context/changes/filter-by-assessment/ui-after/` (zrzuty + `README.md`)

**Intent**: Dowód bramki wizualnej do przeglądu.

**Contract**: Zrzuty 1280×900 i 375×812, jasny i ciemny: `/` w trybie `data` bez filtra i z filtrem, `/?q=zzz` (pusty wynik filtra), `empty` (brak postów), `loading` z aktywnym filtrem, `error` z aktywnym filtrem, hover i focus-visible na polach i przyciskach formularza, kitchen sink „Pola” i „Filtr”. `README.md`: tabela 7 stanów → plik albo N/A z powodem (disabled formularza: N/A — „Filtruj” nigdy nie jest wyłączony, `select` wyłączony pokazany na kitchen sink; hover przy 375 px: N/A, ekran dotykowy; error pola: N/A — brak walidacji po stronie klienta, `maxlength` i odrzucanie nieznanych wartości z adresu), lista `ui-quality-checklist` odhaczona albo N/A z powodem, wynik skanu `no literals` dla `FeedFilter.vue` i `FeedView.vue`.

#### 2. Reguła UI w repo

**File**: `REPOSITORY_GUIDELINES.md` (sekcja „UI (szczur-radar-web)”, poza blokiem `@przeprogramowani/10x-cli`)

**Intent**: Następny agent znajduje pola i formularz filtra zamiast pisać własne.

**Contract**: Punkt o komponentach wymienia `FeedFilter` (formularz filtra feedu, na kitchen sink); zdanie, że pola formularzy (`date`, `search`, `select`, checkbox) mają globalny styl w `style.css` i nie dostają własnego w widoku; punkt o zrzutach: mock `/api/posts` obsługuje parametry filtra.

#### 3. Weryfikacja w Railway PR env

**File**: `context/changes/filter-by-assessment/ui-after/README.md`

**Intent**: Filtr na prawdziwych fragmentach (odmiana, hashtagi, polskie litery) i na bazie Railway.

**Contract**: Sekcja „PR env”: data, adres, commit web (`meta.commitHash` ostatniego `SUCCESS` = głowa gałęzi, inaczej `railway redeploy --from-source`), liczba postów (w razie pustej bazy przebieg zakresu dat w `/admin` PR env), lokalizacja bazy (`SELECT datlocprovider, datcollate, datctype FROM pg_database WHERE datname = current_database()`), wyniki: fragment z prawdziwego posta wpisany małymi literami, fragment z polską literą wpisany wielkimi, rodzaj, kierunek, kombinacja „ten sam cel” (post z dwoma celami o różnych kierunkach), pusty wynik, wyczyszczenie, zmiana strony przy filtrze; zrzuty w obu motywach i przy 375 px.

### Success Criteria:

#### Automated Verification:

- `REPOSITORY_GUIDELINES.md` wymienia `FeedFilter`: `grep -c 'FeedFilter' REPOSITORY_GUIDELINES.md` zwraca co najmniej 1
- Testy i build: `cd szczur-radar-api && ./mvnw -B verify` oraz `cd szczur-radar-web && npm test && npm run build`
- CI na PR jest zielone: `gh pr checks <PR>`

#### Manual Verification:

- `ui-after/README.md` pokazuje każdą z 7 komórek macierzy (default, hover, focus-visible, disabled, error, empty, loading) na zrzucie albo jako N/A z powodem, w obu motywach i przy 375 px, z odhaczoną listą `ui-quality-checklist`
- W Railway PR env (zalogowany użytkownik, prawdziwe dane): wyszukiwanie z polską wielką literą znajduje fragment pisany małymi, filtr „ten sam cel” nie zwraca posta, w którym tekst i kierunek należą do różnych celów, pusty wynik i „Wyczyść filtry” działają, lokalizacja bazy zapisana w README

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka. Potem `/10x-impl-review` i `/10x-archive` na tej samej gałęzi; merge robi człowiek.

---

## Testing Strategy

### Unit Tests:

- `feedFilter.test.ts`: czytanie filtra z adresu (znane/nieznane wartości, białe znaki, limit 100, powtórzony klucz), zapis do adresu.
- `FeedFilter.test.ts`: opcje i etykiety, zatwierdzenie, Enter, „Wyczyść filtry”, synchronizacja z propsem.
- `FeedView.test.ts`: dotychczasowe przypadki #48 z trzecim argumentem `{}`; (a)–(g) z fazy 2.
- `PostCard.test.ts`: rodzaj celu w `dt`.
- `pipeline.test.ts`: URL bez filtra niezmieniony, z filtrem zakodowany.
- `styleContract.test.ts`: `FeedFilter.vue` na liście `CLEANED`.

### Integration Tests:

- `PostsApiIT` (a)–(h) na Testcontainers `postgres:18`: ten sam cel, wielkość liter i polskie litery, rodzaj, ucieczka, najnowsza `ok`, stronicowanie i `total`, wykluczenia, 400.
- CI: `./mvnw -B verify`, Vitest, build z prawdziwym Dockerfile web (`web-container-smoke`).

### Manual Testing Steps:

1. `cd szczur-radar-web && npm ci && npm run dev`; `playwright-cli open --config=<główny checkout>/.playwright/cli.config.json`, `run-code --filename=szczur-radar-web/dev/mock-api.js`, `goto http://localhost:5173/`.
2. Filtr `orlen` + „wzrostowo”, potem „Starsze”, potem wstecz: filtr i strona wracają z adresu.
3. `/?q=zzz`, `/?kind=xyz`, Tab przez formularz (widoczny fokus na każdym polu i przycisku).
4. PR env: kroki z fazy 3.

## Performance Considerations

Bez filtra zapytania jak dziś. Z filtrem Postgres przegląda JSONB najnowszej oceny `ok` każdego ocenionego posta (bez indeksu): przy dzisiejszych ~kilkudziesięciu–kilkuset postach i wzroście ok. 270 dziennie to tanie; wybór najnowszej oceny korzysta z `ix_assessments_post_latest`. Gdy kiedyś zwolni (dziesiątki tysięcy postów), kolejny krok to tabela celów albo indeks trigramowy — osobna zmiana z pomiarem.

## Migration Notes

Brak migracji i zmian danych. Kształt odpowiedzi `/api/posts` bez zmian, nowe parametry opcjonalne, więc stara wersja SPA działa z nowym API. Wycofanie = revert commitów tej gałęzi.

## References

- Research: `context/changes/filter-by-assessment/research.md`
- Roadmapa S-04: `context/foundation/roadmap.md` (Change ID `filter-by-assessment`), PRD FR-011a: `context/foundation/prd.md`
- Kontrakt celu i stanowiska: `context/foundation/llm-assessment/contract-v2.md:148-168`, `:299-305`
- Poprzednia zmiana feedu: `context/archive/2026-10-12-feed-view-ui-hardening/plan.md` (stan strony, zrzuty, D2/D3)
- Kontrakt UI: `.claude/skills/10x-ui/SKILL.md`, `.claude/skills/10x-ui/references/ui-quality-checklist.md`, `REPOSITORY_GUIDELINES.md` § UI
- Wzorzec formularza: `szczur-radar-web/src/views/AdminView.vue:178-211`
- PR env: `context/deployment/deploy-plan.md` („PR-env verification: the web deployment's commit”)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: API — filtr w `GET /api/posts`

#### Automated

- [x] 1.1 Testy backendu przechodzą, w tym przypadki (a)–(h)
- [x] 1.2 Istniejące testy feedu bez zmian przechodzą
- [x] 1.3 Trasa nadal wymaga sesji: `AuthorizationCompletenessIT` przechodzi bez zmian listy tras

#### Manual

- [ ] 1.4 Lokalnie `curl` z sesją: `q` i `direction` dają oczekiwane posty i `total`, `kind=xyz` → 400

### Phase 2: SPA — filtr w feedzie

#### Automated

- [ ] 2.1 Testy web przechodzą, w tym `feedFilter.test.ts`, `FeedFilter.test.ts`, przypadki (a)–(g) i skan `no literals` z `FeedFilter.vue`
- [ ] 2.2 Typecheck i build przechodzą, a `dist/` nie zawiera kitchen sink
- [ ] 2.3 `dev/mock-api.js` parsuje się

#### Manual

- [ ] 2.4 `/` z mockiem przy 1280 i 375 px w obu motywach: formularz, linia wyniku, zawężanie, pusty wynik filtra, rodzaj na karcie, bez poziomego przewijania
- [ ] 2.5 Kitchen sink „Pola”, „Filtr” i „Posty” w obu motywach

### Phase 3: Stany, zrzuty i weryfikacja w PR env

#### Automated

- [ ] 3.1 `REPOSITORY_GUIDELINES.md` wymienia `FeedFilter`
- [ ] 3.2 Testy i build backendu i web przechodzą
- [ ] 3.3 CI na PR jest zielone

#### Manual

- [ ] 3.4 `ui-after/README.md` pokazuje 7 komórek macierzy w obu motywach i przy 375 px, z listą `ui-quality-checklist`
- [ ] 3.5 Railway PR env na prawdziwych danych: polska wielka litera, „ten sam cel”, pusty wynik i wyczyszczenie, lokalizacja bazy zapisana
