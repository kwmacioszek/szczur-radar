# S-29: źródło widoczne w feedzie, świeżość bez kłamstwa — plan implementacji

## Overview

Przy dwóch źródłach użytkownik nie odróżnia postów Wykopu od postów Reddita, a etykieta „Dane zaktualizowane” pokazuje jedną datę z przebiegu, więc przebieg tylko z Redditem przesuwa też datę Wykopu. Ta zmiana dodaje do odpowiedzi posta źródło i kanał, pokazuje je w feedzie i szczegółach (`Wykop · #gielda`, `Reddit · r/stocks`) oraz liczy świeżość osobno dla każdego źródła.

Decyzje właściciela z wywiadu `/10x-plan` (2026-10-30) rozstrzygają destylację Q-02:

- **Świeżość per źródło, zawsze rozpisana:** `Wykop 30.10.2026, 01:12 · Reddit 30.10.2026, 01:12`, także gdy daty są równe.
- **Co przesuwa datę źródła:** tylko wynik `succeeded` tego źródła w przebiegu zakresu `succeeded` albo `partial`. Wyniki `empty` i `failed` jej nie ruszają.
- **Które źródła są w etykiecie:** tylko te z co najmniej jedną taką aktualizacją, liczone z historii przebiegów, bez czytania konfiguracji.
- **Oznaczenie posta:** źródło i kanał, w karcie i w szczegółach.
- **Bez filtra i sortowania po źródle:** FR-008 zostaje kandydatem do M-5.

## Current State Analysis

- **Post** — `PostItem` to jeden rekord listy i szczegółów (`posts/api/PostController.java:140-146`), bez `source` i `channel`. Encja `Post` ma oba pola (`posts/Post.java:52-53,66-67`), a oba odczyty feedu zwracają `Page<Post>` (`posts/PostRepository.java:69-73,98-105`), więc nie ma projekcji do zmiany.
- **Kanał** — `posts.channel` trzyma nazwę w pisowni źródła, bez przedrostka: `gielda`, `ValueInvesting` (`db/migration/V13__posts_channel.sql:1-5`). `#` i `r/` z roadmapy to formy wyświetlania, nie dane.
- **Świeżość** — `findLastUpdate` bierze `finished_at` przebiegu zakresu `succeeded`/`partial` o najdalszym końcu zakresu, z nich najpóźniejszego (`pipeline/run/PipelineRunRepository.java:72-81`), bez źródła. `GET /api/freshness` odpowiada `{lastUpdatedAt}` (`pipeline/api/FreshnessController.java:31-41`).
- **Dane per źródło** — `pipeline_run_sources` ma wynik każdego źródła od S-20, z backfillem historii (`V12`). `findRangesEndingSince` i `findEarliestCoveredFrom` łączą je z przebiegami (`PipelineRunRepository.java:95-120`), a `findFirstCoveredFrom` pyta w pętli po źródłach (`:106-114`).
- **SPA** — etykieta `Wykop`/`Reddit` istnieje (`szczur-radar-web/src/labels.ts:72`) i `rules.json` ją pilnuje (`src/contract/rules.test.ts:81-91`). Etykietę świeżości renderuje tylko `FeedView.vue:172-178`.
- **Kontrakt** — `post.json` opisuje post, a `PostsApiIT:183` wiąże element listy ze szczegółami. Świeżość nie ma pliku kontraktu: kształt pilnuje wyłącznie ścisły JSON w `FreshnessIT` (`:198-206`), nic nie porównuje obu stron.
- **Dokumenty** — plan S-20 i `deploy-plan.md:1937` piszą „S-29 zrobi per źródło”, a roadmapa miała to jako pytanie otwarte. Wywiad zamyka tę sprzeczność.

## Desired End State

- Karta w feedzie i strona posta pokazują `Wykop · #gielda` albo `Reddit · r/<subreddit>`.
- `GET /api/posts` i `GET /api/posts/{id}` mają w każdym poście `source` i `channel` (kanał bez przedrostka).
- `GET /api/freshness` odpowiada `{"sources":[{"source":"wykop","lastUpdatedAt":"…"},{"source":"reddit","lastUpdatedAt":"…"}]}` w kolejności `Source`, tylko dla źródeł z aktualizacją, a przed pierwszą `{"sources":[]}`.
- Przebieg tylko z Redditem, przebieg z padniętym albo pustym Redditem i przebieg z padniętym Wykopem przesuwają wyłącznie datę źródła, które dało posty.
- Przed pierwszą aktualizacją etykieta mówi „Dane nie były jeszcze aktualizowane.”, a gdy odczyt się nie uda, etykiety nie ma i lista działa.
- Sprawdzenie: `./mvnw -B verify`, `npm test`, `npm run build` zielone, zrzuty z atrapy w `ui/` oraz dowód w PR env na prawdziwym przebiegu tylko z Redditem.

### Key Discoveries:

- `PipelineRunRepository.java:90-93` `findCoveringSince` — ta sama reguła co decyzja właściciela: przebieg `succeeded`/`partial` i wynik źródła `succeeded`; `MissingDays` czyta ją tak samo, więc etykieta i „Dni bez danych” liczą jedną miarą.
- `PipelineRunRepository.java:106-114` `findFirstCoveredFrom` — wzorzec: `EnumMap` i jedno zapytanie na źródło.
- `api/pipeline/api/FreshnessIT.java:184-195` — `run()` nie pisze wierszy `pipeline_run_sources`, więc każdy scenariusz trzeba przepisać; publiczny konstruktor `PipelineRunSource` jest „for tests”.
- `api/pipeline/PipelineRunIT.java:557,580` — jedyne poza `FreshnessIT` użycie `findLastUpdate`.
- `src/contract/rules.test.ts:183-186,210-213` — `source` w SPA jest typowane jako `string` i tak zostaje; bez nowej unii.
- `src/views/RunDetailView.vue:232` — precedens: nowe pole z API czytane warunkiem `v-if`.
- `src/views/FeedView.test.ts:343` — etykieta musi zostać bezpośrednio za `h1#feed-title`.
- `src/components/PostCard.test.ts:56-69` — karta ma dokładnie jeden `time` i jeden link; oznaczenie to zwykły tekst.
- `api/posts/PostSeeds.java:73-75` — każdy seed ma wpisane `Source.WYKOP`, `"gielda"`.

## What We're NOT Doing

- Nie dodajemy filtra ani sortowania po źródle (FR-008 zostaje nice-to-have, kandydat do M-5).
- Nie pokazujemy języka posta: nie jest zapisany, decyzja należy do S-28.
- Nie zwijamy równych dat do jednej ani nie pokazujemy źródła bez aktualizacji („Reddit: jeszcze nie”).
- Nie czytamy konfiguracji źródeł w `/api/freshness`: nowo włączone źródło pojawia się w etykiecie po pierwszej udanej aktualizacji.
- Nie oznaczamy źródła na admińskiej liście nieudanych postów (`FailedPost` zostaje).
- Nie robimy z kanału linku ani filtra.
- Nie zostawiamy `lastUpdatedAt` na najwyższym poziomie odpowiedzi: dwie reguły świeżości to właśnie błąd, który usuwamy.
- Nie zmieniamy definicji daty: to nadal koniec przebiegu (`prd.md:225`), a nie koniec pobrania źródła, bo posty trafiają do feedu dopiero po ocenie.
- Nie włączamy Reddita na produkcji: runbook S-28 zostaje bez zmian.
- Nie przechodzimy przez `/10x-ui` jako osobną zmianę: nowe teksty stosują kontrakt UI z `REPOSITORY_GUIDELINES.md` w fazie 3, jak w S-20.
- Nie edytujemy `context/domain/` (glossary, destylacja): to wynik `/10x-domain-distillation`, a kolejny przebieg uzgodni Q-02, R-24, D-08 i wpis „świeżość danych”.

## Implementation Approach

Dwa pionowe kawałki, każdy od API przez kontrakt do widoku, a na końcu dowód i dokumenty.

1. Faza 1: post dostaje `source` i `channel`. To czyste dodanie pól, bezpieczne w obie strony wdrożenia.
2. Faza 2: świeżość per źródło zmienia kształt odpowiedzi `/api/freshness`, dlatego idzie osobno, z przepisanym `FreshnessIT` i nowym plikiem kontraktu.
3. Faza 3: zrzuty z atrapy, PRD z rozstrzygnięciem Q-02, runbook i prawdziwy przebieg tylko z Redditem w PR env.

Reguła przedrostków żyje w SPA obok etykiety źródła: API oddaje dane, SPA je podpisuje. Dodanie źródła dalej nie rusza warstw API, a `rules.test.ts` wymusza przedrostek tak samo, jak dziś wymusza etykietę.

Każda faza kończy się zielonymi bramkami i commitem. Merge wykonuje człowiek po `/10x-impl-review` i `/10x-archive` na tej samej gałęzi. Kroki możliwe dopiero po merge'u żyją w `deploy-plan.md` i issue #123, nie w Progress.

## Critical Implementation Details

**Okno wdrożenia.** API i web wdrażają się niezależnie i w nieustalonej kolejności, więc nowy SPA może przez chwilę dostać stare `{lastUpdatedAt}`. Odczyt `freshness.sources.length` wywróciłby wtedy render całego feedu, a PRD wymaga, by przy nieudanym odczycie lista działała (`prd.md:225`). Dlatego etykieta ma warunek `freshness?.sources` i w tym oknie jej nie ma. W drugą stronę stary SPA w otwartej karcie pokaże „Dane nie były jeszcze aktualizowane.” do odświeżenia strony; przyjmujemy to świadomie.

## Phase 1: Post ze źródłem i kanałem

### Overview

Odpowiedź posta ma `source` i `channel`, kontrakt i typ TS mówią to samo, a karta i strona posta pokazują `Wykop · #gielda` albo `Reddit · r/stocks`. Na początku fazy, przed pierwszym commitem kodu: gałąź `feature/source-aware-feed` (istnieje jako worktree z `origin/main`, 78c57ca; sprawdzić `git fetch`), PR z `Refs #123`, komentarz w issue #123 z change-id, gałęzią i linkiem do PR. Folder zmiany oraz `roadmap.md` ze statusem `in-progress` wchodzą w pierwszy commit razem z `tools/render_roadmap_svg.py` i `docs/visuals/roadmap.svg`.

### Changes Required:

#### 1. Odpowiedź posta

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java`

**Intent**: Pokazać źródło i kanał posta na liście i w szczegółach jednym rekordem, bez nowego zapytania.

**Contract**: `PostItem(UUID id, Source source, String channel, ItemType itemType, Instant publishedAt, …)`, oba pola niepuste, wypełniane w `PostItem.of` z `post.getSource()` i `post.getChannel()`. Javadoc rekordu: `source` to wartość `Source` (`wykop`, `reddit`), `channel` kanał w pisowni źródła, bez przedrostka. `AssessmentView.resultSource` to inne pojęcie i zostaje bez zmian.

#### 2. Testy API i seed Reddita

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/PostSeeds.java`, `.../posts/api/PostsApiIT.java`

**Intent**: Dowieść, że post Reddita wychodzi z API ze swoim źródłem i kanałem, także na liście.

**Contract**: `PostSeeds` dostaje wariant zapisujący post `Source.REDDIT` z podanym kanałem (surowy payload zapisany jako `Source.REDDIT`); dotychczasowe metody bez zmian. `PostsApiIT`: post Wykopu ma `wykop`/`gielda`, post Reddita `reddit`/`ValueInvesting` w `/api/posts` i `/api/posts/{id}`, a kanał przychodzi bez przedrostka. Istniejące `ContractFiles.assertMatches(…, "post.json")` wymusza plik z pkt 3.

#### 3. Kontrakt i typ

**File**: `szczur-radar-web/src/contract/post.json`, `src/contract/samples.ts`, `src/api/posts.ts`

**Intent**: Nowe pola zmieniają plik, próbkę i typ w jednym PR (reguła z `REPOSITORY_GUIDELINES.md`).

**Contract**: `"source": "wykop", "channel": "gielda"` po `id` w `post.json` i `samplePost`; `PostItem.source: string` i `PostItem.channel: string` z komentarzem o braku przedrostka. `source` zostaje `string` (`rules.test.ts:183-186`).

#### 4. Podpis pochodzenia posta

**File**: `szczur-radar-web/src/labels.ts`, `src/labels.test.ts`, `src/contract/rules.test.ts`

**Intent**: Jedno miejsce, które zna pisownię kanału danego źródła, z testem wymuszającym ją dla każdego źródła.

**Contract**: eksportowana stała `CHANNEL_PREFIXES: Record<string, string>` (`wykop: '#'`, `reddit: 'r/'`) i funkcja `postOrigin(source: string, channel: string): string`. Wyniki: `Wykop · #gielda`, `Reddit · r/ValueInvesting`; nieznane źródło bez etykiety i przedrostka daje `x · y`. W `rules.test.ts`: klucze `CHANNEL_PREFIXES` równe `rules.values.source`, więc nowe źródło bez przedrostka jest czerwone.

#### 5. Karta i strona posta

**File**: `szczur-radar-web/src/components/PostCard.vue`, `src/components/PostCard.test.ts`, `src/views/PostDetailView.vue`, `src/views/PostDetailView.test.ts`

**Intent**: Użytkownik widzi pochodzenie posta w obu miejscach, a układ zostaje ten sam.

**Contract**: w `.meta` karty element `<span class="origin">` zaraz po dacie (kolejność: data, pochodzenie, typ, istotność, notka prefiltra); w `.meta` strony posta po typie, przed autorem. Zwykły tekst, bez linku i bez znacznika `time`, więc testy „jeden link” i „jeden `time`” zostają. `.origin` ma `overflow-wrap: anywhere`, żeby długa nazwa kanału nie rozpychała kolumny 9rem. Testy: Wykop i Reddit w karcie oraz na stronie posta.

#### 6. Fabryki, kitchen sink i atrapa

**File**: `src/components/PostCard.test.ts:8`, `src/views/FeedView.test.ts:25`, `src/views/PostDetailView.test.ts:22`, `src/views/KitchenSinkView.vue:194`, `szczur-radar-web/dev/mock-api.js:326`

**Intent**: Wymagane pola trafiają do każdego literału, a kitchen sink i atrapa pokazują post Reddita.

**Contract**: literały `PostItem` mają `source` i `channel`, w `KitchenSinkView.vue` wszystkie pięć postów (`:197,218,243,257,267`); kitchen sink dostaje wariant `reddit`/`ValueInvesting` (najdłuższa nazwa pierwszej konfiguracji). `feedItem` atrapy daje co trzeciemu postowi `reddit`, kanał z `stocks`, `ValueInvesting`, `investing` i adres `https://www.reddit.com/r/<kanał>/…`, reszcie `wykop`/`gielda`. Nagłówek atrapy opisuje nowe pola.

### Success Criteria:

#### Automated Verification:

- Testy API z Testcontainers zielone: `cd szczur-radar-api && ./mvnw -B verify`
- Testy SPA zielone: `cd szczur-radar-web && npm test`
- Typecheck i build zielone: `cd szczur-radar-web && npm run build`
- Plik kontraktu niesie oba pola: `jq -e '.source == "wykop" and .channel == "gielda"' szczur-radar-web/src/contract/post.json`
- Nowe źródło bez przedrostka jest czerwone: `rg -n "CHANNEL_PREFIXES" szczur-radar-web/src/contract/rules.test.ts`

---

## Phase 2: Świeżość per źródło

### Overview

`/api/freshness` odpowiada listą źródeł z ich datami, etykieta w feedzie je rozpisuje, a kształt pilnuje nowy plik kontraktu.

### Changes Required:

#### 1. Odczyt per źródło

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java`

**Intent**: Policzyć dzisiejszą regułę osobno dla każdego źródła, tą samą miarą co „Dni bez danych”, i usunąć regułę globalną.

**Contract**: znikają `findLastUpdate` i `findFinishedAtOfNewestRange`. Nowa `default Map<Source, Instant> findLastUpdates()` pyta raz na `Source.values()` (wzorzec `findFirstCoveredFrom`), zwraca `EnumMap` tylko ze źródłami, które mają aktualizację. Zapytanie: `RunKind.RANGE`, status przebiegu `SUCCEEDED`/`PARTIAL`, `finishedAt` niepuste, `SourceFetchStatus.SUCCEEDED`, dane `source`; porządek `rangeTo desc, finishedAt desc`, `limit 1`; wynik to `r.finishedAt` przebiegu, nie `s.finishedAt`. Zbiór `SUCCEEDED`/`PARTIAL`, powtórzony teraz trzy razy (destylacja, pozycja 5), staje się jedną stałą interfejsu. Javadoc `findCoveringSince` wskazuje `findLastUpdates`.

#### 2. Trasa

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/FreshnessController.java`

**Intent**: Odpowiedź niesie datę każdego źródła i nadal nic więcej o przebiegach.

**Contract**: `record Freshness(List<SourceUpdate> sources)`, `record SourceUpdate(Source source, Instant lastUpdatedAt)`, oba pola niepuste; kolejność `Source`; lista pusta przed pierwszą aktualizacją. Javadoc kontrolera opisuje regułę per źródło (FR-030, S-29).

#### 3. Testy API

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/FreshnessIT.java`, `.../pipeline/PipelineRunIT.java`

**Intent**: Przypiąć regułę per źródło i przypadek, który dziś kłamie, oraz sprawdzić ją na prawdziwych przebiegach dwóch źródeł.

**Contract**: `FreshnessIT.run()` przyjmuje źródło i zapisuje jego wynik (`PipelineRunSource`) dla każdego przebiegu, także RETRY, POSTS, `failed` i `interrupted`, bo inaczej scenariusze filtrów rodzaju i statusu przeszłyby bez względu na zapytanie; wynik `failed` podaje `error` (`ck_pipeline_run_sources_error`). `body()` buduje nowy JSON; dotychczasowe scenariusze zostają w tym samym sensie na wyniku `wykop succeeded`. Nowe scenariusze:
- bez przebiegów `{"sources":[]}`;
- przebieg tylko z Redditem przesuwa Reddita, Wykop zostaje;
- `partial` z `reddit failed` i `partial` z `reddit empty` przesuwają tylko Wykopa;
- źródło z samymi `failed`/`empty` nie ma wpisu;
- dwa źródła wracają w kolejności `wykop`, `reddit`;
- wynik `succeeded` w przebiegu `failed` albo `interrupted` nie przesuwa daty;
- przebieg admina po starszym zakresie zostawia datę źródła.

Jeden scenariusz przechodzi przez `ContractFiles.assertMatches(…, "freshness.json")`. `PipelineRunIT:557,580` czytają `findLastUpdates()`, `:558` sprawdza `containsKey(Source.WYKOP)`, a import `Optional` (`:16`) znika; scenariusze dwóch źródeł (Reddit padł, Reddit pusty, Wykop padł) sprawdzają, że mapa ma wyłącznie źródło, które dało posty.

#### 4. Kontrakt i typ

**File**: `szczur-radar-web/src/contract/freshness.json` (nowy), `src/contract/samples.ts`, `src/contract/shapes.test.ts`, `src/api/freshness.ts`

**Intent**: Kształt jest teraz zagnieżdżony i niesie wartości `source`, więc dostaje ten sam strażnik co post i przebieg.

**Contract**: plik z dwoma wpisami `wykop`, `reddit` i `"lastUpdatedAt": "<instant>"`; `sampleFreshness: Freshness`; test w `shapes.test.ts`; nagłówek `samples.ts` wymienia nowy plik. TS: `SourceFreshness { source: string; lastUpdatedAt: string }`, `Freshness { sources: SourceFreshness[] }`.

#### 5. Etykieta w feedzie

**File**: `szczur-radar-web/src/views/FeedView.vue`, `src/views/FeedView.test.ts`

**Intent**: Etykieta rozpisuje datę każdego źródła i nie wywraca listy, gdy odpowiedź nie ma `sources`.

**Contract**: warunek `v-if="freshness?.sources"` (zob. Critical Implementation Details). Lista niepusta: `Dane zaktualizowane: Wykop 13.10.2026, 05:12 · Reddit 13.10.2026, 05:12`, każdy wpis to `label('source', …)` i `<time :datetime>` w spanie niełamiącym się w środku, separator ` · `, kolejność z API. Lista pusta: `Dane nie były jeszcze aktualizowane.` Etykieta zostaje bezpośrednio za `h1` (`:343`). Testy `FeedView.test.ts:97,253,264,333-381`: jedno źródło, dwa źródła, różne daty, pusta lista, nieudany odczyt, odpowiedź bez `sources` (etykiety nie ma, lista działa), odczyt raz na wejście; `:253,264` to typ i wywołanie `settle` w teście „reports a loaded page only once the freshness label is settled”.

#### 6. Atrapa

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Każdy stan etykiety da się pokazać na zrzucie.

**Contract**: `FRESHNESS`: `'date'` (dwa źródła, ta sama data, domyślnie), `'apart'` (Reddit starszy), `'wykop'` (jedno źródło, jak produkcja do S-28), `'none'` (`{sources: []}`), `'error'` (500). Nagłówek i `/api/freshness` jak `FreshnessController`.

### Success Criteria:

#### Automated Verification:

- Testy API z Testcontainers zielone: `cd szczur-radar-api && ./mvnw -B verify`
- Testy SPA zielone: `cd szczur-radar-web && npm test`
- Typecheck i build zielone: `cd szczur-radar-web && npm run build`
- Plik kontraktu ma kształt listy: `jq -e '.sources[0].source == "wykop" and .sources[1].source == "reddit" and .sources[0].lastUpdatedAt == "<instant>" and (has("lastUpdatedAt") | not)' szczur-radar-web/src/contract/freshness.json`
- Reguły globalnej nie ma w kodzie: `! rg -n "findLastUpdate\b|findFinishedAtOfNewestRange" --glob '*.java' szczur-radar-api/src`
- Przypadek „tylko Reddit” jest przypięty: `rg -n "Reddit" szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/FreshnessIT.java`

---

## Phase 3: Zrzuty, dokumenty i dowód w PR env

### Overview

Nowe teksty są sprawdzone wizualnie, dokumenty mówią prawdę, Q-02 jest zapisane w PRD, a prawdziwy przebieg w PR env dowodzi, że data Wykopu stoi, gdy przesuwa się data Reddita. Merge wykonuje człowiek po `/10x-impl-review` i `/10x-archive`.

### Changes Required:

#### 1. Zrzuty

**File**: `context/changes/source-aware-feed/ui/README.md` (nowy) i zrzuty obok

**Intent**: Sprawdzić nowe teksty w obu motywach i przy 375 px według kontraktu UI.

**Contract**: atrapa + `playwright-cli`. Feed przy `FRESHNESS` `'date'` (jasny, ciemny, 375 px), `'apart'` (jasny, 375 px), `'none'` (jasny) oraz strona posta Reddita (jasny, ciemny, 375 px). Macierz 7 stanów w README: default (zrzuty), empty (`'none'`), error (`'error'`: etykiety nie ma, lista działa; bez zrzutu), loading (etykiety nie ma; bez zrzutu), hover, focus-visible, disabled — N/A, bo to tekst, nie kontrolka.

#### 2. PRD

**File**: `context/foundation/prd.md`

**Intent**: Zapisać rozstrzygnięcie Q-02 i usunąć zdania „jeszcze nie”.

**Contract**: przy FR-030 `Doprecyzowanie (2026-10-30, S-29 source-aware-feed)`: data per źródło, co ją przesuwa, które źródła są w etykiecie, pusta etykieta jak dotąd, definicja przebiegu i `finished_at` bez zmian. Przy FR-023 doprecyzowanie o oznaczeniu źródła i kanału w feedzie i szczegółach oraz o braku filtra (FR-008 nice-to-have). Poprawka w miejscu: `:273` „API jej jeszcze nie pokazuje (S-29)”. Przy `:269` dopisek, że od S-29 przesuwa się tylko data zdrowego źródła.

#### 3. Dokumenty kodu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`, `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md`, `szczur-radar-api/README.md`, `REPOSITORY_GUIDELINES.md`

**Intent**: Następny agent i operator czytają kształt i reguły, które obowiązują.

**Contract**:
- `pipeline/CLAUDE.md`: wiersz `FreshnessController` (`:46`), `FreshnessIT` (`:532`) i wzmianki o dacie feedu (`:374,:583`).
- `sources/CLAUDE.md`: „Adding a source” dopisuje w SPA etykietę i przedrostek kanału.
- `README.md:290`: nowy JSON.
- `REPOSITORY_GUIDELINES.md`: `freshness.json` na liście plików kontraktu (`:43`), `PostCard` z pochodzeniem posta (`:50`).

#### 4. Runbook

**File**: `context/deployment/deploy-plan.md`

**Intent**: Operator wie, co się zmieniło i co sprawdzić po merge'u.

**Contract**: sekcja `## S-29: źródło widoczne w feedzie, świeżość bez kłamstwa (source-aware-feed)`, `**Status:** delivered by PR #<N>; production verification and closure in issue #123.`; co się zmienia (etykieta z nazwą źródła, bez migracji, bez zmiennych, okno wdrożenia); po merge'u tylko do odczytu: `/api/freshness` ma `wykop` z datą ostatniej nocy, feed pokazuje `Wykop · #gielda`; rollback przez revert, bo nic nie jest zapisywane. Zdania przyszłe poprawione w miejscu: `:484` (JSON), `:1937`, `:2023`, `:2027`.

#### 5. Roadmapa

**File**: `context/foundation/roadmap.md`

**Intent**: Pytania S-29 są rozstrzygnięte, a roadmapa ma to powiedzieć.

**Contract**: w sekcji S-29 pozycje `Unknowns` oznaczone jako rozstrzygnięte w planie 2026-10-30 (język posta zostaje przy S-28). Status prowadzą `/10x-implement` i `/10x-archive`; każda zmiana pliku idzie z `tools/render_roadmap_svg.py` i `docs/visuals/roadmap.svg`.

#### 6. Weryfikacja w PR env

**File**: brak (operacja i zapis dowodu)

**Intent**: Dowieść na prawdziwym przebiegu tego, czego test nie pokaże: Railway, prawdziwy Arctic Shift i wdrożony SPA.

**Contract**: w PR env (`szczur-radar-pr-<N>`, admin z runbooka S-07, sesja `<cookie>`, `REDDIT_SUBREDDITS` z repository variable):
1. `POST /api/admin/pipeline-runs {"from":"<D-2>","to":"<D-2>"}` i czekanie na koniec ostatniego przebiegu z odpowiedzi `POST` (przy postach do ponowienia są dwa: `[retry, range]`); wcześniej w tym PR env nie było zakresu za późniejszy dzień. Odpowiedź `/api/freshness` zapisana jako `BEFORE`.
2. `POST` z `"sources":["reddit"]` za ten sam dzień i czekanie na koniec ostatniego przebiegu z odpowiedzi `POST`; odpowiedź zapisana jako `AFTER`.
3. `playwright-cli` na `<web-origin>/`: tekst `.freshness` i jeden `.origin` Reddita cytowane w `ui/README.md`, bez tytułów postów i autorów.
4. Komentarz w issue #123 z wynikami.

### Success Criteria:

#### Automated Verification:

- Zrzuty istnieją: `test "$(ls context/changes/source-aware-feed/ui/*.png | wc -l)" -ge 9`
- `ui/README.md` wymienia każdy stan macierzy: `for s in default hover focus-visible disabled error empty loading; do rg -q "$s" context/changes/source-aware-feed/ui/README.md || echo "brak: $s"; done | wc -l | grep -qx ' *0'`
- Dokumenty nie opisują starego kształtu: `! rg -n "findLastUpdate\b|\{lastUpdatedAt\}" --glob '*.md' --glob '!context/archive/**' --glob '!context/changes/**' --glob '!context/domain/**' .`
- PRD zapisuje decyzję: `rg -n "Doprecyzowanie \(2026-10-30, S-29" context/foundation/prd.md`
- CI PR zielone: `gh pr checks <N>`
- PR env: oba źródła w etykiecie, bez starego pola: `curl -sb <cookie> <web-origin>/api/freshness | jq -e '[.sources[].source] == ["wykop","reddit"] and (has("lastUpdatedAt") | not)'`
- PR env: przebieg tylko z Redditem przesuwa tylko Reddita: `jq -en --argjson a "$BEFORE" --argjson b "$AFTER" '($a.sources|length) == 2 and $a.sources[0] == $b.sources[0] and $b.sources[1].lastUpdatedAt > $a.sources[1].lastUpdatedAt'`
- PR env: post Reddita ma źródło i kanał bez przedrostka: `curl -sb <cookie> '<web-origin>/api/posts?page=<page>&size=100' | jq -e 'any(.items[]; .source == "reddit" and (.channel | IN("stocks","ValueInvesting","investing")))'` (zacząć od `page=0`)
- PR env: wdrożony SPA pokazuje oba źródła: `rg -n "Wykop .*Reddit|Reddit · r/" context/changes/source-aware-feed/ui/README.md`
- Komentarz o wyniku w issue: `gh issue view 123 --comments | rg -c "PR env"`

#### Manual Verification:

- Ocena napisów i układu na zrzutach z `ui/`: etykieta przy dwóch datach oraz `Reddit · r/ValueInvesting` w wąskiej kolumnie karty (ocena użytkownika)

---

## Testing Strategy

### Unit Tests:

- `labels.test.ts`: `postOrigin` dla obu źródeł, nazwa z wielkimi literami, nieznane źródło.
- `rules.test.ts`: klucze `CHANNEL_PREFIXES` równe wartościom `source`.
- `PostCard.test.ts`, `PostDetailView.test.ts`: pochodzenie Wykopu i Reddita, jeden link, jeden `time`.
- `FeedView.test.ts`: jedno i dwa źródła, różne daty, pusta lista, błąd odczytu, odpowiedź bez `sources`.
- `shapes.test.ts`: `freshness.json` równy `sampleFreshness`, `post.json` równy `samplePost`.

### Integration Tests:

- `PostsApiIT`: oba źródła na liście i w szczegółach, `post.json`.
- `FreshnessIT`: każda reguła z fazy 2, kontrakt `freshness.json`, role bez zmian.
- `PipelineRunIT`: prawdziwe przebiegi dwóch źródeł przesuwają tylko źródło, które dało posty.

### Manual Testing Steps:

1. Obejrzeć zrzuty feedu przy `'date'` i `'apart'`: czy etykieta czyta się dobrze w obu motywach i przy 375 px.
2. Obejrzeć kartę z `Reddit · r/ValueInvesting` w kolumnie 9rem i stronę posta Reddita.

## Performance Considerations

Dwa krótkie zapytania raz na wejście do feedu zamiast jednego. `pipeline_run_sources` ma po jednym wierszu na źródło i przebieg, więc bez nowego indeksu. Odpowiedź posta dokłada pola już wczytane z encji.

## Migration Notes

Bez migracji. `V12` dał historii wiersze `pipeline_run_sources`, ale stare przebiegi bez posta mają wynik `empty`. Etykieta produkcji i tak pokaże tę samą godzinę z nazwą `Wykop`: od S-08 przebieg bez posta kończy się `failed`, więc noce po 2026-10-19 mają wynik `succeeded` i wygrywają w sortowaniu po `rangeTo`. Rollback przez revert: nic nie jest zapisywane.

## References

- Roadmapa: `context/foundation/roadmap.md`, sekcja S-29; issue #123
- Audyt: `context/changes/post-m2-audit/research.md`, S5, S6
- Destylacja: `context/domain/domain-distillation.md`, R-24, D-08, Q-02
- Podobna implementacja: `context/archive/2026-10-29-source-isolation/plan.md` (wyniki per źródło), `PipelineRunRepository.java:106-114`
- Reguła pokrycia: `pipeline/run/MissingDays.java`
- Kontrakt: `REPOSITORY_GUIDELINES.md`, sekcja Coding Style & Testing (`src/contract/`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Post ze źródłem i kanałem

#### Automated

- [x] 1.1 Testy API z Testcontainers zielone: `cd szczur-radar-api && ./mvnw -B verify` — 3ebecfc
- [x] 1.2 Testy SPA zielone: `cd szczur-radar-web && npm test` — 3ebecfc
- [x] 1.3 Typecheck i build zielone: `cd szczur-radar-web && npm run build` — 3ebecfc
- [x] 1.4 Plik kontraktu niesie oba pola: `jq -e '.source == "wykop" and .channel == "gielda"' szczur-radar-web/src/contract/post.json` — 3ebecfc
- [x] 1.5 Nowe źródło bez przedrostka jest czerwone: `rg -n "CHANNEL_PREFIXES" szczur-radar-web/src/contract/rules.test.ts` — 3ebecfc

### Phase 2: Świeżość per źródło

#### Automated

- [x] 2.1 Testy API z Testcontainers zielone: `cd szczur-radar-api && ./mvnw -B verify` — 0181835
- [x] 2.2 Testy SPA zielone: `cd szczur-radar-web && npm test` — 0181835
- [x] 2.3 Typecheck i build zielone: `cd szczur-radar-web && npm run build` — 0181835
- [x] 2.4 Plik kontraktu ma kształt listy: `jq -e '.sources[0].source == "wykop" and .sources[1].source == "reddit" and .sources[0].lastUpdatedAt == "<instant>" and (has("lastUpdatedAt") | not)' szczur-radar-web/src/contract/freshness.json` — 0181835
- [x] 2.5 Reguły globalnej nie ma w kodzie: `! rg -n "findLastUpdate\b|findFinishedAtOfNewestRange" --glob '*.java' szczur-radar-api/src` — 0181835
- [x] 2.6 Przypadek „tylko Reddit” jest przypięty: `rg -n "Reddit" szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/FreshnessIT.java` — 0181835

### Phase 3: Zrzuty, dokumenty i dowód w PR env

#### Automated

- [x] 3.1 Zrzuty istnieją: `test "$(ls context/changes/source-aware-feed/ui/*.png | wc -l)" -ge 9`
- [x] 3.2 `ui/README.md` wymienia każdy stan macierzy: `for s in default hover focus-visible disabled error empty loading; do rg -q "$s" context/changes/source-aware-feed/ui/README.md || echo "brak: $s"; done | wc -l | grep -qx ' *0'`
- [x] 3.3 Dokumenty nie opisują starego kształtu: `! rg -n "findLastUpdate\b|\{lastUpdatedAt\}" --glob '*.md' --glob '!context/archive/**' --glob '!context/changes/**' --glob '!context/domain/**' .`
- [x] 3.4 PRD zapisuje decyzję: `rg -n "Doprecyzowanie \(2026-10-30, S-29" context/foundation/prd.md`
- [x] 3.5 CI PR zielone: `gh pr checks <N>`
- [x] 3.6 PR env: oba źródła w etykiecie, bez starego pola: `curl -sb <cookie> <web-origin>/api/freshness | jq -e '[.sources[].source] == ["wykop","reddit"] and (has("lastUpdatedAt") | not)'`
- [x] 3.7 PR env: przebieg tylko z Redditem przesuwa tylko Reddita: `jq -en --argjson a "$BEFORE" --argjson b "$AFTER" '($a.sources|length) == 2 and $a.sources[0] == $b.sources[0] and $b.sources[1].lastUpdatedAt > $a.sources[1].lastUpdatedAt'`
- [x] 3.8 PR env: post Reddita ma źródło i kanał bez przedrostka: `curl -sb <cookie> '<web-origin>/api/posts?page=<page>&size=100' | jq -e 'any(.items[]; .source == "reddit" and (.channel | IN("stocks","ValueInvesting","investing")))'` (zacząć od `page=0`)
- [x] 3.9 PR env: wdrożony SPA pokazuje oba źródła: `rg -n "Wykop .*Reddit|Reddit · r/" context/changes/source-aware-feed/ui/README.md`
- [x] 3.10 Komentarz o wyniku w issue: `gh issue view 123 --comments | rg -c "PR env"`

#### Manual

- [ ] 3.11 Ocena napisów i układu na zrzutach z `ui/`: etykieta przy dwóch datach oraz `Reddit · r/ValueInvesting` w wąskiej kolumnie karty (ocena użytkownika)
