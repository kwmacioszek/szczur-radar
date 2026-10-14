---
date: 2026-10-12T16:57:33+0200
researcher: Claude (Opus 5.5) for kwmacioszek
git_commit: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662
branch: feature/filter-by-assessment
repository: szczur-radar
topic: "S-04 filter-by-assessment — na czym oprzeć filtr listy po celu (fragment + rodzaj) i stanowisku autora wobec tego samego celu"
tags: [research, codebase, feed, posts-api, assessment-contract-v2, jsonb, web, feed-view]
status: complete
last_updated: 2026-10-12
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Pierwsza wersja."
---

# Research: S-04 filter-by-assessment

**Date**: 2026-10-12T16:57:33+0200
**Researcher**: Claude (Opus 5.5) for kwmacioszek
**Git Commit**: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662
**Branch**: feature/filter-by-assessment
**Repository**: szczur-radar

Ścieżki skracam: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`, `web/…` = `szczur-radar-web/src/…`, `migration/…` = `szczur-radar-api/src/main/resources/db/migration/…`.

## Research Question

Slice S-04 z `context/foundation/roadmap.md:196-206`: użytkownik zawęża listę postów po spółce/temacie (dopasowanie tekstowe do fragmentu wskazującego cel i rodzaju celu) oraz po stanowisku autora wobec tego celu; brak wyników pokazuje czytelny stan pusty. PRD: FR-011a (`context/foundation/prd.md:124-127`, must-have), US-01. Ryzyko z roadmapy (`roadmap.md:205`): stanowisko jest per cel, więc filtr musi łączyć cel i stanowisko **tego samego** celu.

Pytania: gdzie i w jakim kształcie leżą cele i stanowiska, jak dziś działa feed (backend i SPA), jakie konwencje musi powtórzyć filtr, co zdecydowano wcześniej i co zostaje do decyzji w `/10x-plan`.

## Summary

- **Cele i stanowiska jako dane strukturalne są tylko w JSONB `assessments.output`** (`migration/V3__create_pipeline_and_posts.sql:139`; kolumna `raw_text` trzyma jeszcze surowy tekst odpowiedzi modelu), zapisywanym z rekordu `AssessmentOutputV2` (`api/posts/Assessment.java:155-156`). Element `targets[]` ma kształt `{id, kind, anchor{document, field, quote}, stance{direction, assertiveness, claim_ids}, reference}` (`api/agents/assessor/contract/Target.java:10-11`, `Stance.java:12-14`). Każdy cel ma dokładnie jedno `stance`, więc „to samo” znaczy „ten sam element tablicy”. Filtr musi więc sprawdzać oba warunki na jednym elemencie (`EXISTS … jsonb_array_elements(output->'targets')`), a nie dwoma niezależnymi warunkami na poście.
- **Fragment celu (`anchor.quote`) jest dosłowny.** Walidator (D6, §10) przepuszcza tylko normalizację białych znaków: zmieniona odmiana, wielkość liter lub usunięty markdown to naruszenie (`context/foundation/llm-assessment/contract-v2.md:299-305`). Rekord celu ma 5 pól i żadne nie jest nazwą znormalizowaną, tickerem ani identyfikatorem encji (`Target.java:11`, `contract-v2.md:152,158`). W 120 nagraniach konfiguracji produkcyjnej `luna` (zestaw wzorcowy, nie produkcja) ta sama spółka występuje jako `Orlen`/`Orlenu`/`Orlenie`/`orlenu`/`#orlen`/`#PKN`; 8 ze 161 fragmentów zaczyna się od `#`, 1 od `$`.
- **Słownik stanowisk to `bullish | bearish | neutral | mixed | not_expressed | unclear`** (`contract/Direction.java:10`), a nie „pozytywne/negatywne” z PRD. W tych samych 120 nagraniach `luna` 81 ze 161 celów ma `not_expressed`, a `neutral` nie wystąpił ani razu. `hedged`/`unhedged` to osobne pole `assertiveness` (`Stance.java:13`).
- **Feed stronicuje `posts` przed załadowaniem ocen.** `PostController.list` bierze stronę postów w etapie `assessed` (`api/posts/api/PostController.java:47`), a dopiero potem najnowszą ocenę `ok` drugim zapytaniem `DISTINCT ON` (`PostController.java:48-50`, `api/posts/AssessmentRepository.java:29-31`). Filtr musi wejść do zapytania stronicującego, inaczej `total` i strony będą błędne.
- **W ścieżkach zapisu obecnego kodu post `assessed` ma dokładnie jedną ocenę `ok`, ale baza tego nie wymusza.** `PostTransitions.claim` bierze tylko posty `stored`/`failed` (`api/pipeline/state/PostTransitions.java:55-66`), więc post po ocenie `ok` nie jest oceniany ponownie. Nie ma jednak unikalnego indeksu na `(post_id) WHERE status='ok'` (poza kluczem głównym tabela ma jeden indeks, `ix_assessments_post_latest`, `V3__…sql:178`; V4 i V5 nie dodają indeksów), a `PostsApiIT` celowo zakłada dwie oceny `ok` jednego posta (`test/posts/api/PostsApiIT.java:121-132`). Filtr spójny z tym, co pokazuje karta, sprawdza najnowszą ocenę `ok`.
- **Konwencje API są gotowe, poza wiązaniem enumów.** Błędny parametr już daje 400 `{"detail":"invalid_request"}` (`api/config/ApiExceptionHandler.java:37-41`). Spring wiąże enum po nazwie stałej (`BULLISH`), a wartości kontraktu są małymi literami. Precedensu konwertera nie ma: wszystkie 6 `@RequestParam` w `szczur-radar-api/src/main/java` to `int page`/`int size`. Nowy parametr istniejącej trasy nie zmienia `AuthorizationCompletenessIT`.
- **Baza: Postgres 18 (testy, CI, lokalnie) bez rozszerzeń, dane małe.** Lokalnie (`ghcr.io/railwayapp-templates/postgres-ssl:18`, `en_US.utf8`, dostawca `libc`) `ILIKE` składa wielkość polskich liter. `pg_trgm` i `unaccent` są dostępne, ale żadna migracja ich nie tworzy. Produkcja miała 282 posty 2026-10-11, a dump z 2026-10-12 dawał `/api/posts` `total` = 30 (`context/deployment/deploy-plan.md`, sekcja F-01), więc przeszukanie JSONB bez indeksu wystarcza w MVP.
- **Frontend:** `FeedView` trzyma stronę w `?page=`, a `goTo` zachowuje pozostałe klucze query (`web/views/FeedView.vue:17-21,36-38`). Brak wspólnego pola tekstowego, `<select>` i globalnego stylu dla nich (jest tylko `input[type=date]`, `web/style.css:149-161`). Etykiety kierunku istnieją, etykiet rodzaju celu nie ma, a karta nie pokazuje rodzaju celu. `dev/mock-api.js` nie obsługuje `/api/posts`.
- **Kolejność prac do decyzji:** `admin-panel-ui-hardening` zaplanował osobną zmianę `feed-view-ui-hardening` (FeedView + PostCard na `AppButton`/`StatusBadge`, `px` na skalę) „przed S-03 i S-04” (`context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:39`). Folder tej zmiany nie istnieje.

## Detailed Findings

### 1. Kształt celów i stanowisk (kontrakt V2)

- `AssessmentOutputV2`: `relevance`, `modifiers[]`, `targets[]`, `claims[]`, `flags[]`. Wszystkie klucze są wymagane, a obce klucze są zabronione (`szczur-radar-api/src/main/resources/assessor/assessment-output-v2.schema.json:6-7`, wg agenta).
- `targets[i]` (`contract-v2.md:148-158`, `Target.java:10-11`):
  - `id` — lokalny (`t1`, `t2`…), „nie jest identyfikatorem encji” (`contract-v2.md:152`);
  - `kind` ∈ `company | asset | sector | macro_topic | other | unknown` (`TargetKind.java:10`);
  - `anchor` = `{document: item|parent_post|parent_comment, field: title|body, quote}`, przy czym `quote` ma 1–100 znaków (`contract-v2.md:276`, `assessment-output-v2.schema.json:67`);
  - `stance` = `{direction, assertiveness, claim_ids}` (`Stance.java:12-14`);
  - `reference` ∈ `explicit | contextual | ambiguous | unresolved`.
- Kierunek (`contract-v2.md:164`):
  - `bullish` obejmuje też kupno lub trzymanie (§7.1.8) oraz opis wzrostu ceny (§7.1.7); `bearish` symetrycznie;
  - `neutral` tylko przy jawnej deklaracji neutralności;
  - `not_expressed` — cel jest, stanowiska brak;
  - `unclear` — stanowisko jest, kierunek nieustalony.
- `assertiveness`:
  - `hedged` znaczy, że autor osłabia stanowisko albo kierunek jest odczytany pośrednio. To nie pewność modelu (`contract-v2.md:165,168`).
  - Tabela §7.2 (`api/agents/assessor/validation/StanceTable.java:13-18`, wg agenta): `bullish`/`bearish`/`neutral` mają `hedged`/`unhedged`, pozostałe kierunki `null`.
- Reguły walidatora istotne dla filtra (tabela `contract-v2.md:342-357`, wg agenta):
  - D6: fragment jest ciągłym podciągiem wskazanego pola po normalizacji białych znaków (bez zmiany wielkości liter i odmiany, `contract-v2.md:299-305`);
  - D15: `not_investment_related` ⇒ `targets = []`;
  - walidator nie sprawdza unikalności fragmentów między celami ani zasady „jedna encja = jeden cel” (§6.7 mierzona tylko na zestawie wzorcowym).
- Ocena `failed` ma `output = NULL` (`V3__…sql:165-167`), więc `output` mają tylko oceny `ok`: wynik modelu po walidacji albo gotowy wynik prefiltra.
- Wynik prefiltra (wpis bez treści) ma `targets: []` (`contract-v2.md:317-326`). Filtr, który wymaga istnienia pasującego celu, wyklucza go więc bez osobnej obsługi.

### 2. Prawdziwe fragmenty i rozkłady (nagrania zestawu wzorcowego)

Źródło: 120 plików (120 różnych postów `gielda-2026-10-01/02-NNN`) w `szczur-radar-api/src/test/resources/fixtures/llm/recordings/luna/`, wszystkie `status = ok`, prompt v2.5. Policzone skryptem z `rawText`; to próbka zestawu wzorcowego, nie dane produkcyjne.

- Celów na post: 0 → 23 posty, 1 → 64, 2 → 23, 3 → 4, 4 → 3, 7/8/12 → po 1. Razem 161 celów.
- Rodzaje: `company` 89, `macro_topic` 34, `asset` 19, `other` 11, `sector` 4, `unknown` 4.
- Kierunki: `not_expressed` 81, `bearish` 39, `bullish` 34, `unclear` 4, `mixed` 3, `neutral` 0.
- Wg agenta, w tych samych nagraniach `assertiveness` to `hedged` 49 i `unhedged` 24, czyli 49 z 73 stanowisk `bullish`/`bearish` jest osłabionych. W pozostałych trzech konfiguracjach (`luna-low`, `deepseek-flash`, `gemini-flash-lite`) `neutral` wystąpił łącznie raz.
- Fragmenty (sprawdzone skryptem w tej samej próbce `luna`):
  - forma odmieniona lub slangowa i oryginalna wielkość liter, wszystkie z rodzajem `company`: `Orlen`, `Orlenu`, `Orlenie`, `orlenie`, `orlenu`, `#orlen`, `#PKN`; `Micron`, `MICRON`, `microna`, `mikrus`, `mikrusami`; `XTB`, `xtb`, `#xtb`, `$XTB`;
  - czasem fragment nie jest nazwą, tylko słowem lub zdaniem: `odkupię` (rodzaj `unknown`), `Nic nie rośnie`, `KRACHU`, `krach` (`macro_topic`).
- Fragmenty `#`/`$` (8 + 1 ze 161 w `luna`) nie przeszkadzają w dopasowaniu podciągu: „orlen” znajduje się w „#orlen”. Podciąg bez rozróżniania wielkości liter nie łączy jednak `PKN` z `Orlen`, `CDR` z `CD Projekt` ani formy bez polskich znaków z formą z nimi (to drugie rozwiązałby `unaccent`). PRD świadomie to akceptuje (`prd.md:125-127`), a luka slangu i hashtagów była już opisana w `context/archive/2026-09-27-assessment-contract-harness/frame.md:177`.

### 3. Backend — feed dziś

- `GET /api/posts?page=&size=`: `page` od 0, `size` 1–100, domyślnie 50 (`PostController.java:43-46`).
  - Strona: `posts.findPage(List.of(PostStage.ASSESSED), page, size)` (`:47`), czyli pochodne zapytanie Spring Data `findByStageIn` z sortowaniem `NEWEST_FIRST` = `publishedAt DESC, id DESC` (`api/posts/PostRepository.java:23,58-63`). Indeks `ix_posts_stage_published (stage, published_at DESC, id DESC)` (`V3__…sql:125`).
  - Oceny: `findLatestOk(ids)` → natywne `SELECT DISTINCT ON (a.post_id) … AND a.status = :status ORDER BY a.post_id, a.assessed_at DESC, a.id DESC` (`AssessmentRepository.java:20-31`).
  - Odpowiedź: `{items, page, size, total}`; `PostItem.assessment` = `{output, resultSource, assessedAt}` (`PostController.java:56-88`).
- Konsekwencja: dziś stronicowanie jest po stronie `posts`, a filtr po JSONB oceny wymaga jednego zapytania (natywnego lub `JdbcClient`), które łączy post z jego najnowszą oceną `ok` i z warunkiem `EXISTS` po `targets[]`, z osobnym zliczeniem `total`. Oceny do wyświetlenia mogą dalej przychodzić z istniejącego `findLatestOk`.
- Jedna ocena `ok` na post w obecnym kodzie:
  - `claim` aktualizuje tylko `stage IN (stored, failed)` (`PostTransitions.java:55-66`);
  - przebieg `posts` wybranych przez admina też odsiewa do `STORED`/`FAILED` (`api/pipeline/step/AssessmentSelection.java:62-63`, wg agenta);
  - `content_hash` jest zapisywany, ale nikt go nie czyta (`api/posts/Post.java:181`, wg agenta), więc edycja posta nie powoduje ponownej oceny.
  - Ponowne ocenianie postów `assessed` nie jest nigdzie zaplanowane (wg agenta, sprawdzono PRD i roadmapę).
- Testy feedu: `test/posts/api/PostsApiIT.java`:
  - Testcontainers, `@Transactional`, `MockMvcTester` z `user(...).roles("USER")`;
  - `badPagingIsAnInvalidRequest` (`:134-142`) sprawdza dokładne ciało 400.
- Dane testowe:
  - `PostSeeds.ok(postId, assessedAt)` zawsze zapisuje `ContractFixtures.validOutput()` (`test/posts/PostSeeds.java:77-82`);
  - `validOutput()` ma dwa cele: `XTB` `bullish`/`unhedged` i `CDR` `not_expressed` (`test/agents/assessor/ContractFixtures.java:52-58`). To gotowy przypadek „ten sam cel, nie dowolny cel”: filtr `CDR` + `bullish` ma nie zwrócić tego posta;
  - `ContractFixtures.target(...)` i `investment(targets, claims)` (`:60-67`) pozwalają zbudować własne wyjście, ale `PostSeeds` potrzebuje przeciążenia `ok` z własnym `output`.

### 4. Backend — konwencje dla nowych parametrów

- Błędy parametrów:
  - `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` i `HandlerMethodValidationException` dają 400 `invalid_request` (`ApiExceptionHandler.java:33-41`);
  - `@Size`/`@NotBlank` na parametrze `String` przejdą tą samą ścieżką, bez zmian w obsłudze błędów.
- Enumy w query: brak precedensu.
  - W `src/main` nie ma `Converter<String, …>` ani `WebMvcConfigurer`, wg agenta.
  - Domyślne wiązanie przyjmuje `BULLISH`, a nie `bullish`. Potrzebny jest konwerter po `value()` albo parametr `String` z wyszukaniem wartości.
  - Zamknięte zbiory trzymamy w enumach (`context/foundation/lessons.md`, „Zamknięte zbiory wartości trzymaj w enumach”), więc `Direction` i `TargetKind` z kontraktu są naturalnymi typami parametrów.
- `AuthorizationCompletenessIT` wylicza trasy po metodzie i wzorcu ścieżki, wg agenta. Nowy parametr na `/api/posts` go nie zmienia.
- `JpaSchemaIT` dotyczy tylko encji i kolumn. Migracja z samym indeksem albo samo nowe zapytanie natywne go nie dotyka.

### 5. Baza danych

- Postgres 18 w testach, CI i lokalnie (wersji produkcyjnej nie sprawdzano):
  - Testcontainers `postgres:18` (`test/TestcontainersConfiguration.java:24`);
  - CI `postgres:18` (`.github/workflows/ci.yml:114`);
  - lokalnie `ghcr.io/railwayapp-templates/postgres-ssl:18` (`compose.local.yaml:5`).
- Lokalizacja (sprawdzone 2026-10-12 na lokalnym kontenerze `szczur-radar-local-postgres-1`, tylko odczyt):
  - baza ma `datlocprovider = c` (libc), `datcollate = datctype = en_US.utf8`;
  - `'ŻABKA ORLENU' ILIKE '%żabka orlenu%'` → `true`, `lower('ŻÓŁĆ ĘŚ')` → `żółć ęś`;
  - obrazy `postgres:18` i `postgres-ssl:18` mają w środowisku `LANG=en_US.utf8`.

  Lokalizacji produkcyjnej bazy nie sprawdzano (zob. Open Questions).
- Rozszerzenia:
  - `pg_available_extensions` lokalnie zawiera `pg_trgm` i `unaccent`;
  - żadna migracja V1–V5 nie ma `CREATE EXTENSION` (wg agenta, przeszukano `src/main/resources/db`);
  - czy rola aplikacji na Railway może je utworzyć — nie sprawdzano.
- Wolumen: pomiar z 2026-10-11 to `posts` 282, `assessments` 55 (`context/deployment/deploy-plan.md`, „Database measurements”); 2026-10-12 `/api/posts` `total` = 30 (tamże, „State 2026-10-12”). Wzrost to ok. 270 postów dziennie. Bez indeksu filtr przegląda JSONB ocen `ok` przy każdym zapytaniu, co przy tych liczbach jest tanie; indeks GIN nie pomaga przy `ILIKE '%…%'` na fragmencie bez `pg_trgm` na wyciągniętym tekście.

### 6. Frontend — widok listy

- `web/views/FeedView.vue`:
  - `fetchPosts(page.value, PAGE_SIZE)` (`:28`), `PAGE_SIZE = 50` (`:8`);
  - `page` z `route.query.page` (`:17-21`);
  - `goTo` robi `router.push({ query: { ...route.query, page } })` (`:36-38`), więc zachowuje inne klucze query;
  - `watch(page, load, { immediate: true })` (`:40`) obserwuje tylko stronę.
- Stany w jednym łańcuchu `v-if` (`:47-59`): błąd, wczytywanie, pusto („Brak ocenionych postów. Pojawią się po pierwszym przebiegu.”) i lista z pagerem.
  - Formularz filtra musi stać poza tym łańcuchem, bo gałąź pusta zastępuje wszystko.
  - Pusty wynik filtra potrzebuje innego tekstu niż „brak postów w ogóle”.
- `web/api/posts.ts`:
  - `fetchPosts(page, size)` składa URL szablonem bez `URLSearchParams` (`:83-85`);
  - typy `Direction` (`:8`) i `Target` (`:12-18`), w których `kind` to zwykły `string`.
- `web/components/PostCard.vue:29-36` pokazuje `anchor.quote` i etykietę kierunku z dopiskiem „(ostrożnie)” dla `hedged`.
  - Rodzaj celu nie jest nigdzie pokazywany.
  - Kolor mają tylko `bullish` (`--success`) i `bearish` (`--destructive`) (`:97-103`).
- Etykiety: `web/labels.ts:9-16` ma zbiór `direction` (wzrostowo, spadkowo, neutralnie, mieszanie, bez stanowiska, niejasno); zbioru dla `kind` brak.
- Komponenty wspólne:
  - są `AppButton`, `StatusMessage` (tony `ok`/`error`, bez neutralnego) i `StatusBadge`;
  - nie ma komponentu pola tekstowego, `<select>` ani opakowania etykiety; w `szczur-radar-web/src` nie ma ani `<select`, ani `type="text"`/`type="search"` (grep 2026-10-12);
  - wzorce formularzy do powtórzenia: `LoginView.vue:49-68` (label + input) i `AdminView.vue:178-210` (`fieldset`/`legend`, `.start` z `gap var(--space-3) var(--space-4)`);
  - globalny styl pól istnieje tylko dla `input[type='date']` i checkboxa (`web/style.css:149-161`);
  - nowy wspólny komponent trafia do `web/components/`, na kitchen sink (sekcja „Pola”, `web/views/KitchenSinkView.vue:89-97`) i na listę `CLEANED` w `web/styleContract.test.ts:85-95` (`REPOSITORY_GUIDELINES.md` § UI).
- Testy, które zmiana sygnatury `fetchPosts` lub tekstu pustego stanu musi zaktualizować:
  - `web/views/FeedView.test.ts`: mock modułu API, asercja `toHaveBeenLastCalledWith(1, 50)`, tekst „Brak ocenionych postów”;
  - `web/api/pipeline.test.ts:41-52`: dokładny URL `/api/posts?page=2&size=50`.
- `szczur-radar-web/dev/mock-api.js` obsługuje tylko `/api/auth/me` (rola admin) i `/api/admin/*`; wszystko inne, w tym `/api/posts`, dostaje 404 (`:44-51`). Zrzuty filtra wymagają gałęzi `/api/posts` z danymi i obsługą parametrów.

## Code References

- `api/posts/api/PostController.java:43-53` — `GET /api/posts`: strona postów, potem najnowsza ocena `ok`
- `api/posts/PostRepository.java:23,58-63` — `NEWEST_FIRST`, `findPage`/`findByStageIn`
- `api/posts/AssessmentRepository.java:20-35` — `DISTINCT ON` najnowszej oceny (`ok` lub dowolnej)
- `api/posts/Assessment.java:155-156` — zapis `output` przez `ContractJson.MAPPER`
- `migration/V3__create_pipeline_and_posts.sql:125,139,165-167,178` — indeks postów, `output JSONB`, `ok` ⇔ `output` niepuste, indeks ocen
- `api/agents/assessor/contract/Target.java:10-11`, `Stance.java:12-14`, `Direction.java:10`, `TargetKind.java:10` — kształt celu i enumy
- `context/foundation/llm-assessment/contract-v2.md:148-168,299-305,317-326` — tabele `targets`/`stance`, niezmiennik cytatu, wynik prefiltra
- `api/pipeline/state/PostTransitions.java:55-66` — `claim` tylko ze `stored`/`failed`
- `api/config/ApiExceptionHandler.java:33-41` — 400 `invalid_request` dla parametrów
- `test/posts/api/PostsApiIT.java:121-142` — remis dwóch ocen `ok`, błędne stronicowanie
- `test/agents/assessor/ContractFixtures.java:52-67` — wyjście z `XTB` bullish i `CDR` not_expressed, budowniczowie celów
- `web/views/FeedView.vue:17-59` — stan strony w URL, `watch`, łańcuch stanów
- `web/api/posts.ts:8-18,83-85` — typy `Direction`/`Target`, `fetchPosts`
- `web/components/PostCard.vue:29-36,97-103` — prezentacja celów i kolory stanowisk
- `web/labels.ts:9-16` — etykiety kierunku
- `web/style.css:149-161` — globalny styl pól (tylko data, checkbox)
- `szczur-radar-web/dev/mock-api.js:38-51` — brak `/api/posts` w mocku

## Architecture Insights

- „Ten sam cel” jest strukturalny, nie semantyczny: stanowisko jest zagnieżdżone w celu (`Target.java:11`). Poprawny warunek to jeden element `targets[]` spełniający jednocześnie warunki fragmentu, rodzaju i kierunku. Rozdzielenie ich na `@>`/`EXISTS` per pole daje błąd z ryzyka roadmapy. Przypadek testowy jest gotowy w `ContractFixtures.validOutput()`.
- Dziś JSONB `output` jest jedynym strukturalnym zapisem celów; denormalizacja (np. tabela `post_targets` wypełniana przy zapisie oceny) to osobna decyzja z backfillem i dodatkowym miejscem zapisu w `PostTransitions.record`. Wolumen jej nie wymaga.
- Wartości enumów w API i JSON to małe litery z `@JsonValue value()` (`Direction.java:12-16`). Parametry query powinny używać tych samych wartości co pola, które SPA już dostaje w `assessment.output`.
- Stan widoku jest w URL (`?page=`, decyzja S-02 `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:466`). Parametry filtra pasują do tego wzorca (odświeżenie i przekierowanie z logowania zachowują `fullPath`, `web/router/index.ts:49-61`, wg agenta). Zmiana filtra powinna zerować stronę.

## Historical Context (from prior changes)

- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:72` — filtrowanie (S-04) jawnie poza zakresem S-02.
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:392-399` — kontrakt `GET /api/posts` (offset `page`/`size` + `total`, kolejność, „najnowsza ocena `ok`”). Pytanie offset vs kursor (`research.md:232`) rozstrzygnięte na offset; S-04 nie musi go ponownie otwierać.
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:238` — `jsonb` dopuszczalne dla `output`; brak decyzji o indeksie czy denormalizacji celów.
- `context/archive/2026-10-06-first-assessed-wykop-feed/reviews/impl-review-phase-2.md:66` — pytanie, czy S-03/S-04 będą filtrować po typie zasobu w bazie. Zamknięte przez V4 (`item_type` zamiast `resource`); FR-011a nie wymaga filtra po typie.
- `context/archive/2026-09-27-assessment-contract-harness/frame.md:177` — slang i hashtagi w fragmentach umkną filtrowi tekstowemu po nazwie spółki (znana, przyjęta luka).
- `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:39`, `ui-after/README.md:88` — zaplanowana zmiana `feed-view-ui-hardening` przed S-03/S-04. W `context/changes/` jej nie ma, a `FeedView`/`PostCard` nadal mają własne style przycisków i wartości `px`.

## Related Research

- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md` — feed, persystencja ocen, wzorce API i SPA (S-02).
- `context/archive/2026-09-27-assessment-contract-harness/` — kontrakt V2, zestaw wzorcowy i nagrania użyte w sekcji 2.

## Open Questions

Decyzje produktowe i projektowe dla `/10x-plan` (fakty powyżej ich nie rozstrzygają):

1. **Kolejność wobec `feed-view-ui-hardening`.** Najpierw osobna zmiana `/10x-ui` (FeedView + PostCard na komponenty i skalę), potem S-04? Albo wchłonąć ją do S-04, albo świadomie pominąć? S-04 dokłada do tego samego widoku pierwsze pola formularza poza panelem admina.
2. **Pola filtra i ich znaczenie.**
   - Czy „rodzaj celu” z FR-011a to osobny wybór (enum `kind`), czy tylko część kontekstu dopasowania tekstowego? Roadmapa (`roadmap.md:198`) i PRD (`prd.md:124`) czytają się jako osobne kryterium.
   - Wybór pojedynczy czy wielokrotny dla `kind` i `direction`?
3. **Które kierunki pokazać.**
   - Słownik kontraktu ma 6 wartości; „pozytywne/negatywne/neutralne” z PRD odpowiadają `bullish`/`bearish`/`neutral`.
   - `neutral` w próbce `luna` nie wystąpił, a `not_expressed` to połowa celów.
   - Czy ujawniać `hedged`/`unhedged` jako osobny filtr, czy pominąć?
4. **Semantyka dopasowania tekstu.**
   - Domyślny kandydat: podciąg bez rozróżniania wielkości liter (`ILIKE '%…%'` z ucieczką `%`, `_`, `\`) po `anchor.quote`, z przycięciem zapytania; minimalna i maksymalna długość (fragment ma ≤100 znaków).
   - Czy dokładać `unaccent` („zabka” → „Żabka”)? Wymaga `CREATE EXTENSION`; uprawnień na Railway nie sprawdzano.
   - Czy usuwać `#`/`$` z zapytania (podciąg i tak je obejmuje w kierunku fragment ⊇ zapytanie)?
5. **Łączenie warunków.**
   - Rekomendacja z faktów: wszystkie podane warunki (tekst, `kind`, `direction`) na tym samym elemencie `targets[]`, przy czym wartości w obrębie jednego pola łączy LUB.
   - Sam kierunek bez tekstu to „dowolny cel o tym kierunku”.
   - Bez żadnego warunku feed działa jak dziś (także posty bez celów).
6. **Spójność z wyświetlaną oceną.** Filtrować po najnowszej ocenie `ok` (spójne z kartą, odporne na przyszłe ponowne oceny), czy po dowolnej ocenie `ok` (dziś równoważne)? Czy dodać częściowy unikalny indeks `(post_id) WHERE status = 'ok'`? Uwaga: `PostsApiIT.java:121-132` seeduje dwie oceny `ok`, więc taki indeks wymagałby zmiany tego testu.
7. **Prezentacja wyniku.**
   - Czy karta ma pokazywać rodzaj celu (dziś nie pokazuje), żeby filtr po rodzaju był zrozumiały?
   - Czy wyróżniać cel, który pasuje do filtra?
   - Tekst pustego stanu dla filtra i akcja „wyczyść filtry”.
8. **Nazwy parametrów i API.**
   - Nazwy w query: np. `q`, `kind`, `direction`, powtarzane dla wielu wartości.
   - Wiązanie enumów małymi literami: konwerter Springa po `value()` albo `String` + wyszukanie wartości.
   - Te same nazwy w URL SPA.
9. **Weryfikacja lokalizacji produkcyjnej bazy.**
   - Wnioski o składaniu wielkości polskich liter pochodzą z lokalnego kontenera tego samego obrazu Railway.
   - Przed lub w trakcie weryfikacji w PR env warto potwierdzić `SELECT datlocprovider, datctype FROM pg_database WHERE datname = current_database()` albo test `ILIKE` z polską literą w IT. Testcontainers `postgres:18` ma też `LANG=en_US.utf8`.
