# Panel admina w trzech częściach (S-50) — plan implementacji

## Overview

Panel admina dziś jest jedną stroną `/admin` z pięcioma sekcjami. Ten wycinek dzieli go według zaakceptowanego projektu (`context/foundation/ui-design.md`, prototypy `admin-runs`, `admin-quality`, `admin-posts`, `run`) na trzy części pod własnymi adresami: „Przebiegi” (`/admin`), „Jakość ocen” (`/admin/quality`) i „Nieudane posty” (`/admin/posts`). Przełącza je pasek części pod nagłówkiem „Panel admina”. Listy przebiegów i nieudanych postów stają się tabelami, a strona przebiegu `/admin/runs/:id` dostaje układ z prototypu `run`. Jedyna zmiana API to pochodzenie nieudanego posta (`source`, `channel`), potrzebne w kolumnie „Źródło”. Wycinek S-50, issue #182, PRD FR-014, FR-015, FR-021, FR-022, FR-033, FR-034 (wszystkie `must-have` poza FR-022, który jest zrealizowany).

## Current State Analysis

- `szczur-radar-web/src/views/AdminView.vue` ma 964 linie, w tym około 158 linii stylu lokalnego. Pięć sekcji: „Nowy przebieg” (:543-587), „Dni bez danych” (:589-624), „Jakość ocen (7 dni)” (:626-668), „Ostatnie przebiegi” (:670-733) i „Nieudane posty” (:735-802). Każda ma własne `xLoading`/`xError`/`xRetrying`, licznik kolejności odpowiedzi i fokus po ponowieniu przez `sectionFocus()` (:66-90), zgodnie z S-48.
- Sekcje są powiązane:
  - start przebiegu woła `loadRuns` (:151);
  - „Wpisz do formularza” wypełnia formularz startu, także źródła, i jest wyłączone bez formularza (`fillForm` :208, `formShown` :202);
  - ponowienie nieudanych postów woła `loadRuns` (:489);
  - koniec przebiegu odświeża dni bez danych i jakość ocen (:349-352);
  - „Odśwież” (:673) czyta wszystkie sekcje.
- Nieudane posty: czytana jest tylko pierwsza strona (50, `fetchFailedPosts` w `src/api/posts.ts:199`), bez stronicowania. Lista przebiegów ma „Pokaż starsze” i odpytywanie co 10 s, gdy przebieg trwa (`useEvery`, :401).
- `szczur-radar-web/src/views/RunDetailView.vue` (459 linii):
  - nagłówek „Przebieg: <rodzaj>” ze znacznikiem;
  - sekcje „Postęp” (gdy trwa), „Liczniki”, „Wynik per źródło”, „Kroki” i „Błąd przebiegu”;
  - powrót „← Panel admina”;
  - odczyt co 10 s, gdy przebieg trwa.
- Router (`src/router/index.ts:54-64`): `admin` (AdminView, `meta: {admin, section:'admin', column:'wide'}`) i `admin-run` (`column:'narrow'`) to dzieci powłoki `AppShell`. `meta.section` zaznacza „Panel admina” w nawigacji (`AppShell.vue:30-40`). Testy routera (`src/router/index.test.ts:165-218`) wymieniają te adresy.
- API ma prawie wszystko:
  - źródła w starcie (`StartRequest.sources`);
  - ponowienie zaznaczonych (`POST /api/admin/pipeline-runs {postIds}`, `retryPosts` w `src/api/pipeline.ts:122`);
  - przywrócenie zaznaczonych martwych (`POST /api/admin/posts/restore`, `restorePosts` w `src/api/posts.ts:212`);
  - liczniki i progi obu miar jakości (`src/api/quality.ts`);
  - pola tabel i strony przebiegu (`Run`, `src/api/pipeline.ts:59`).
  
  Brakuje dwóch rzeczy:
  - **G1:** rekord `FailedPost` (`szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/FailedPostController.java:89`) nie ma `source` ani `channel`.
  - **G2:** `GET /api/posts/{id}` zwraca tylko posty ocenione (`PostController.java:102-128`), więc tytuł nieudanego posta nie może otworzyć posta w aplikacji.
- Testy:
  - `e2e/admin.spec.ts`: 5 testów;
  - blok „panel admina” w `e2e/states.spec.ts:91-180`, który używa stałej `SECTIONS` z pięcioma nazwami;
  - `e2e/shell.spec.ts:9-60`;
  - `src/views/AdminView.test.ts` (1309 linii) i `src/views/RunDetailView.test.ts` (555 linii);
  - cele `view-admin`, `view-admin-loading`, `view-admin-empty`, `view-admin-error` i `view-run` w `visual/targets.ts:189-271`, każdy z wzorcami w czterech projektach;
  - `AdminView.vue` i `RunDetailView.vue` są na liście `CLEANED` w `src/styleContract.test.ts:132-162`.
- Atrapa `dev/mock-api.js`:
  - ma 4 nieudane posty (:350) i honoruje `page`/`size` (:717-723);
  - nie ma trybu z ponad 50 nieudanymi postami;
  - tryby panelu to `MODE` (`loading` wstrzymuje wszystkie trasy `/api/admin`, `error`, `empty`), `START`, `MISSING` i `QUALITY`.
- Wspólne komponenty z S-48 i S-47: `PageHeader`, `LoadingState`, `EmptyState`, `ErrorState`, `PagerNav` (z `labels: 'order'`: „Poprzednie”/„Następne”), `StatusBadge`, `StatusMessage`, `AppButton`, `AppIcon`. Strona w adresie ma helper `pageFromQuery` (`src/feedFilter.ts:64`).

## Desired End State

Admin wchodzi na „Panel admina” i widzi część „Przebiegi”: formularz „Nowy przebieg” ze źródłami, „Dni bez danych” z „Wpisz do formularza: …” i tabelę „Ostatnie przebiegi” z kolumnami Przebieg, Rodzaj, Stan, Zakres, Ocenione, Nieudane, Martwe i Czas. Tabela ma „Odśwież” i „Pokaż starsze”.

Pasek części `navigation` „Panel admina” ma odnośniki „Przebiegi”, „Jakość ocen” i „Nieudane posty”. Bieżący odnośnik ma `aria-current="page"`, a „Nieudane posty” pokazuje liczbę nieudanych postów (bez liczby przy 0).

„Jakość ocen” pokazuje kartę każdego źródła z obiema miarami. Każda miara ma wartość, opis „N z M prób · próg P%” albo „N z M tez · próg P%”, stan słowami i pasek z kreską progu.

„Nieudane posty” to tabela z kolumnami Post, Źródło, Stan, Próby i Ostatni błąd:
- tytuł prowadzi do oryginału w nowej karcie;
- pod tabelą jest stronicowanie, a numer strony stoi w adresie;
- „Ponów zaznaczone (N)” daje komunikat z odnośnikiem do nowego przebiegu;
- „Przywróć martwe (N)” usuwa posty z listy i zmniejsza licznik w pasku.

Strona przebiegu ma nagłówek „Przebieg z <data>” ze znacznikiem stanu, metadane, liczniki, wynik per źródło i kroki, a powrót prowadzi do części „Przebiegi”.

Każda część ma ładowanie, pusty stan i błąd z ponowieniem z S-48, niezależne od pozostałych części. Weryfikacja:
- zielone `./mvnw -B verify`, `npm test`, `npm run build`, `npm run e2e` i `npm run visual:docker`;
- nowe testy E2E według kontraktu `ui-design.md:584-594`;
- kontrola w PR env przez agenta;
- ocena właściciela na zrzutach obok prototypów.

### Key Discoveries:

- `Post.source` i `Post.channel` są `NOT NULL` (`posts/Post.java:66-68`), a `PostItem` ma je jako `Source source, String channel` (`PostController.java:146`). Te same typy w `FailedPost` nie są `@Nullable`, więc `ContractVariantsTest` nie wymaga nowego wariantu ani wpisu w `KNOWN_GAPS`.
- `PostsApiIT.java:326` przypina `failed-posts-page.json` przez `ContractFiles.assertMatches`. `src/contract/mockApi.test.ts` porównuje klucze odpowiedzi atrapy z plikiem dokładnie, więc atrapa dostaje nowe pola w tym samym commicie co plik.
- `src/labels.ts:84` `postOrigin(source, channel)` daje „Reddit · r/stocks” i „Wykop · #gielda”. Kolumna „Źródło” używa jej bez nowej logiki.
- Pasek jakości w prototypie (`admin-quality.html:157-183`) ma skalę 1,5 × próg: kreska progu stoi na 66,6%, a wypełnienie to `min(100, wartość / (1,5 × próg) × 100)`%.
- Odnośnik w tabeli przebiegów ma etykietę „Szczegóły przebiegu z <formatDateTime(createdAt)>” (`AdminView.vue:701`). Nagłówek strony przebiegu „Przebieg z …” (kontrakt `ui-design.md:587`) bierze tę samą datę, więc odnośnik i nagłówek się zgadzają.
- `visual/targets.ts` `catalogSections()` (:317) robi wzorzec z każdego `h2` w `main` katalogu. S-48 (impl-review F3) nauczył, że sekcja dodana w środku katalogu przesuwa i zmienia wzorce wszystkich sekcji pod nią.
- Pierwszy `npm run visual:update` z nowym celem jest raz czerwony, bo strażnik wzorców biegnie równolegle. Drugi przebieg jest zielony (REPOSITORY_GUIDELINES, F-06).
- Zajęty przycisk `AppButton` jest wyłączony, więc Chromium oddaje fokus na `body`. Po akcji fokus wraca przez `src/focus.ts` `restoreFocus` (S-49) albo przez wzorzec `sectionFocus` (S-48).

## What We're NOT Doing

- Panel boczny posta i otwieranie nieudanego posta w aplikacji (G2): tytuł prowadzi do oryginału (`webUrl`). Bez `webUrl` tytuł jest tekstem. Panel posta należy do S-51.
- Nowa trasa API do czytania dowolnego posta przez admina.
- Zaznaczanie postów na wielu stronach: zaznaczenie obejmuje bieżącą stronę i czyści się przy zmianie strony.
- Zastąpienie „Pokaż starsze” w tabeli przebiegów stronicowaniem: prototyp `admin-runs` ma „Pokaż starsze”, więc zostaje.
- Miara „kanał jako cel” w „Jakość ocen” (S-60).
- Nowa trasa API dla licznika w pasku części: licznik bierze `total` z `GET /api/admin/posts/failed?page=0&size=1`.
- Zmiana `NotificationBell`, `AppShell` i nawigacji głównej, poza tym, że „Panel admina” jest bieżący na każdej części.
- Tryby atrapy wstrzymujące albo psujące pojedyncze sekcje „Przebiegów”: wystarczą istniejące `MODE`, `MISSING` i `QUALITY`.

## Implementation Approach

- Najpierw backend i atrapa, bo kontrakt odpowiedzi zmienia się w API, w pliku kontraktu, w próbce, w typie TS i w atrapie naraz.
- Potem dwa wspólne komponenty z katalogiem i wzorcami, jeszcze bez widoku, który ich używa (wzorzec S-47, S-48 i S-49).
- Potem samo rozbicie na trasy: sekcje przechodzą do części bez zmiany wyglądu, a testy jednostkowe, E2E i wzorce idą za nimi. Dzięki temu przegląd widzi przeniesienie osobno od przebudowy.
- Potem przebudowa każdej części według prototypu, potem strona przebiegu.
- Na końcu testy E2E nowych przepływów, dokumentacja, PR env i ocena właściciela.

Wzorce zmieniają się w fazie, która zmienia wygląd. Opis PR wymienia grupy wzorców i powód.

Trasa `/admin` staje się rodzicem z komponentem układu panelu, który ma `h1`, pasek części z licznikiem i `<RouterView>`. Dzieci:
- `''` → `admin` (Przebiegi): nazwa zostaje, więc istniejące odnośniki do `admin` działają;
- `quality` → `admin-quality`;
- `posts` → `admin-posts`.

`admin-run` zostaje rodzeństwem bez układu panelu, bo ma własny `h1`. Wszystkie mają `meta.section: 'admin'`.

## Critical Implementation Details

- **Kolejność w katalogu:** nowe sekcje „Części panelu admina” i „Tabela” stają na końcu `/kitchen-sink`, za „Nagłówek strony”. Wtedy żadna istniejąca sekcja się nie przesuwa i żaden jej wzorzec się nie zmienia. Jeśli zmieni się jakiś wzorzec spoza nowych, opis PR podaje to jako osobną grupę z powodem.
- **Układ panelu nie jest remontowany:** przejście między częściami zmienia tylko dziecko `<RouterView>` układu. Licznik w pasku nie znika na czas odczytu: stara liczba zostaje do nowej odpowiedzi. Test routera sprawdza, że powłoka i układ panelu nie montują się na nowo przy `/admin` → `/admin/quality`.
- **Fokus przy wejściu i przy zmianie części:** dziś `AdminView` nie stawia fokusu na nagłówku (robią to `FeedView` i `RunDetailView`), więc to nowe zachowanie.
  - Przy wejściu do panelu z innej strony (montaż układu) fokus przechodzi na `h1` „Panel admina” przez `PageHeader.focus()`, raz, po wczytaniu pierwszej sekcji części.
  - Przy zmianie części paskiem fokus zostaje na klikniętym odnośniku. Układ się nie remontuje, więc odnośnik z nowym `aria-current` zostaje w DOM, a klawiatura zostaje w pasku jak w zakładkach.
  - Ponowienie sekcji dalej stawia fokus na `h2` sekcji albo na błąd (`sectionFocus`).

## Phase 1: Pochodzenie nieudanego posta w API

### Overview

`FailedPost` dostaje `source` i `channel`. Zmiana idzie razem przez API, kontrakt, typ TS i atrapę. Atrapa dostaje też tryb z ponad 50 nieudanymi postami, potrzebny do stronicowania w fazach 4 i 6.

### Changes Required:

#### 1. Rekord odpowiedzi listy nieudanych postów

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/FailedPostController.java`

**Intent**: Lista nieudanych postów mówi, z jakiego źródła i kanału jest post, żeby kolumna „Źródło” mogła pokazać „Reddit · r/stocks”.

**Contract**: `record FailedPost(UUID id, Source source, String channel, ItemType itemType, …)`. Pola są w tej samej kolejności i z tym samym javadokiem co w `PostItem` (`PostController.java:146`). `FailedPost.of` czyta `post.getSource()` i `post.getChannel()`. Nie są `@Nullable`.

#### 2. Plik kontraktu, próbka i typ TS

**File**: `szczur-radar-web/src/contract/failed-posts-page.json`, `szczur-radar-web/src/contract/samples.ts`, `szczur-radar-web/src/api/posts.ts`

**Intent**: Pełna odpowiedź z nowymi polami jest wspólnym plikiem, który czytają test API i Vitest.

**Contract**:
- plik JSON ma `source` i `channel` w każdym elemencie, tak jak je zapisze `PostsApiIT` (ten test wygeneruje rzeczywisty plik);
- `interface FailedPost` ma `source: string` i `channel: string` z komentarzami jak w `PostItem` (`posts.ts:96-99`), bez pól `?:`;
- próbka w `samples.ts` ma te same pola.

#### 3. Test API

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/posts/api/PostsApiIT.java`

**Intent**: Test przypina odpowiedź z pochodzeniem posta dla każdego stanu (`failed`, `dead`, wstrzymany).

**Contract**:
- istniejące `ContractFiles.assertMatches(body(result), "failed-posts-page.json")` (:326) obejmuje nowe pola;
- `aFailedPostsPageMatchesTheSharedContract` (:298) dziś sieje tylko posty z Wykopu (`seeds.post`), więc dostaje jeden nieudany post z Reddita: `seeds.redditPost("stocks", PostStage.FAILED, 1, …)` (`PostSeeds.java:81`) z próbą `seeds.failed(…)`;
- plik kontraktu i próbka w `samples.ts` mają wtedy oba pochodzenia, a jedna asercja w teście listy sprawdza `source: "reddit"` i `channel: "stocks"` tego posta.

#### 4. Atrapa: pochodzenie i tryb `FAILED`

**File**: `szczur-radar-web/dev/mock-api.js`, `szczur-radar-web/src/contract/mockModes.ts`, `szczur-radar-web/src/contract/mockApi.test.ts`

**Intent**:
- Nieudane posty atrapy mają `source` i `channel`: posty 1 i 3 z Wykopu (`gielda`), posty 2 i 4 z Reddita (`stocks`).
- Nowy tryb `FAILED` (`data` | `many`) daje w `many` 57 nieudanych postów: 4 istniejące i 53 kolejne `failed`, z rosnącymi UUID z `uuid(3, n)`. To dwie strony po 50.

**Contract**:
- `MODE_VALUES.FAILED = ['data', 'many']` w atrapie i `MOCK_MODE_VALUES.FAILED` w `mockModes.ts`, w tej samej kolejności;
- wiersz trybu w nagłówku atrapy;
- przypadek w `mockApi.test.ts`: `FAILED: 'many'` daje `total: 57`, pierwsza strona ma 50 elementów, a strona `page=1` ma 7;
- `MODE: 'empty'` dalej daje pustą listę niezależnie od `FAILED`.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą, w tym `PostsApiIT`, `ContractVariantsTest` i `ContractRoutesIT`: `cd szczur-radar-api && ./mvnw -B verify`
- Testy webu przechodzą, w tym `shapes.test.ts` (plik ↔ próbka), `responseTypes.test.ts` i `mockApi.test.ts` (klucze atrapy ↔ plik, tryb `FAILED` w obu tablicach): `cd szczur-radar-web && npm test`
- Typy i build przechodzą: `cd szczur-radar-web && npm run build`
- Plik kontraktu ma pochodzenie w każdym elemencie: `jq -e '[.items[] | has("source") and has("channel")] | all' szczur-radar-web/src/contract/failed-posts-page.json`

Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy.

---

## Phase 2: Komponenty „Części panelu admina” i „Tabela”

### Overview

Dwa nowe wspólne komponenty z arkusza komponentów (`components.html#c-podnawigacja`, `#c-tabela`) trafiają do `src/components/`, na `/kitchen-sink`, na listę `CLEANED` i do `STATES`. Żaden widok jeszcze ich nie używa.

### Changes Required:

#### 1. Pasek części panelu

**File**: `szczur-radar-web/src/components/AdminPartsNav.vue` (nowy), `szczur-radar-web/src/components/AdminPartsNav.test.ts` (nowy)

**Intent**: Pasek drugiego poziomu pod nagłówkiem „Panel admina” z odnośnikami do części. Bieżąca część ma `aria-current="page"`. Część może mieć licznik w znaczniku. Komponent tylko rysuje propsy, więc może stać na kitchen sink.

**Contract**:
- `props: { label: string; items: { label: string; to: RouteLocationRaw; current: boolean; count?: number | null }[] }`;
- `nav` z `aria-label` równym `label`;
- odnośniki `RouterLink`;
- licznik `StatusBadge` z tonem `warning`, rysowany tylko przy `count > 0`;
- styl z prototypu (`prototype.css:888-917` `.subnav`) bez ujemnego marginesu `-1px` (skan `CLEANED` go odrzuci, a F-08 zapisał tylko „bez literału”, bez tokenu grubości linii):
  - linia pod paskiem to `border-bottom: 1px solid var(--line)` na `nav`;
  - podkreślenie bieżącej części to `box-shadow: inset 0 -2px 0 var(--accent)` na odnośniku z `aria-current="page"`, którego skan nie sprawdza. Kreska leży wewnątrz odnośnika, na linii paska, tak jak w prototypie;
  - wskazanie `--surface-2`, fokus `--ring`;
- test jednostkowy: `aria-current` tylko na bieżącym, brak znacznika przy 0 i `null`.

#### 2. Tabela danych

**File**: `szczur-radar-web/src/components/DataTable.vue` (nowy), `szczur-radar-web/src/components/DataTable.test.ts` (nowy)

**Intent**: Ramka tabeli z arkusza:
- ukryty podpis;
- nagłówki kolumn;
- liczby do prawej z cyframi tabelarycznymi, a zero wyszarzone;
- pierwsza komórka wiersza jako jego nagłówek (`th scope="row"`);
- zaznaczony wiersz z tłem `--selected`;
- poziome przewijanie wewnątrz własnej ramki poniżej 900 px, bez przewijania strony.

Kolumny i wiersze daje widok przez slot, bo każda tabela ma inne komórki.

**Contract**:
- `props: { caption: string }`; domyślny slot to `<thead>`/`<tbody>`;
- komponent dostarcza ramkę przewijaną (`role="region"`, `tabindex="0"` i nazwa z podpisu, żeby klawiatura mogła ją przewinąć, WCAG 2.1.1) oraz style komórek przez `:slotted()`;
- klasy komórek, których używa widok: `num` (liczba), `zero` (wartość 0) i `is-selected` na wierszu;
- test jednostkowy: podpis jest `caption` ukrytym wizualnie, a ramka ma nazwę dostępną.

#### 3. Katalog i listy pilnujące

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`, `szczur-radar-web/src/styleContract.test.ts`, `szczur-radar-web/visual/targets.ts`

**Intent**:
- Kitchen sink pokazuje oba komponenty we wszystkich stanach z tabeli „Stany” (`ui-design.md:416-417`). Sekcje stoją na końcu katalogu (patrz Critical Implementation Details).
- „Części panelu admina” mają bieżącą część, licznik i część bez licznika.
- „Tabela” ma tabelę przebiegów jak w prototypie (z odnośnikiem w nagłówku wiersza, znacznikami stanu i zerem) oraz tabelę z polami wyboru i zaznaczonym wierszem.
- Oba pliki trafiają na `CLEANED`.

**Contract**:
- nagłówki sekcji `h2` „Części panelu admina” i „Tabela”, tytuły próbek jako `p`;
- wpisy `STATES`: wskazanie i fokus odnośnika części, wskazanie wiersza tabeli; nazwy `state-*` według konwencji pliku;
- nowe wzorce `kitchen-sink-czesci-panelu-admina.png` i `kitchen-sink-tabela.png` oraz `state-*` w projektach `light-375` i `dark-375`.

### Success Criteria:

#### Automated Verification:

- Testy webu przechodzą, w tym testy obu komponentów i skan `CLEANED`: `cd szczur-radar-web && npm test`
- Build przechodzi: `cd szczur-radar-web && npm run build`
- Wzorce nowych sekcji i stanów są zapisane, a innych plików nie ruszono: `cd szczur-radar-web && npm run visual:update && git status --porcelain visual/__screenshots__` pokazuje wyłącznie nowe pliki `kitchen-sink-czesci-panelu-admina.png`, `kitchen-sink-tabela.png` i nowe `state-*` w `light-375` i `dark-375` (gdy zmienił się inny plik, opis PR podaje go z powodem)
- Testy wizualne i dostępności przechodzą dwa razy z rzędu bez zmian w kodzie: `cd szczur-radar-web && npm run visual:docker`
- Kopie projektu w aplikacji są zgodne z projektem: `node tools/design-check/design-check.mjs app`
- Strona katalogu nie przewija się w poziomie przy 320 px (tabela przewija się w swojej ramce): `playwright-cli` na `npm run dev`, `/kitchen-sink`, `document.documentElement.scrollWidth <= 320`

Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy.

---

## Phase 3: Rozbicie panelu na trzy adresy

### Overview

`AdminView.vue` rozpada się na układ panelu i trzy widoki części. Sekcje przechodzą do części ze swoim dzisiejszym wyglądem, stanami i ponowieniami. Testy idą za nimi.

### Changes Required:

#### 1. Trasy

**File**: `szczur-radar-web/src/router/index.ts`, `szczur-radar-web/src/router/index.test.ts`, `szczur-radar-web/src/components/AppShell.test.ts`

**Intent**: `/admin` staje się rodzicem z układem panelu i trzema dziećmi. `admin-run` zostaje rodzeństwem bez układu.

**Contract**:
- dziecko powłoki `path: 'admin'`, `component: AdminPanelView`, `meta: { admin: true, section: 'admin', column: 'wide' }`;
- dzieci:
  - `{ path: '', name: 'admin', component: AdminRunsView }`;
  - `{ path: 'quality', name: 'admin-quality', component: AdminQualityView }`;
  - `{ path: 'posts', name: 'admin-posts', component: AdminFailedPostsView }`;
- każda trasa z `meta.column` (test :186 tego wymaga), dziedziczonym albo powtórzonym;
- strażnik admina obejmuje dzieci (`meta.admin` w `to.matched`);
- testy routera:
  - tabele sekcji i kolumn z trzema nowymi adresami;
  - asercja `matched` (`index.test.ts:175`) daje 3 dla części panelu i 2 dla pozostałych stron powłoki;
  - test „gives every page of the shell a column” (:189) schodzi też do dzieci układu panelu;
  - użytkownik bez roli admina odbity z `/admin/quality` i `/admin/posts`;
  - powłoka i układ panelu nie remontowane przy `/admin` → `/admin/quality`;
- `src/components/AppShell.test.ts:22` mockuje nowy widok zamiast `../views/AdminView.vue`.

#### 2. Układ panelu

**File**: `szczur-radar-web/src/views/admin/AdminPanelView.vue` (nowy), `szczur-radar-web/src/views/admin/AdminPanelView.test.ts` (nowy)

**Intent**: Wspólny nagłówek „Panel admina”, pasek części z licznikiem nieudanych postów i miejsce na część. Licznik czyta `total` listy nieudanych postów przy wejściu i po każdej zmianie części. Część „Nieudane posty” może poprosić o ponowny odczyt po przywróceniu. Błąd albo zawieszenie odczytu licznika nie pokazuje komunikatu: znacznik po prostu nie stoi, a część pokazuje swój stan.

**Contract**:
- `PageHeader title="Panel admina"`, `AdminPartsNav label="Panel admina"` z trzema pozycjami (`current` z `route.name`), `<RouterView>`;
- `provide(ADMIN_PANEL_KEY, { refreshFailedCount(): void, focusTitle(): void })` z kluczem typu `InjectionKey` w `src/views/admin/adminPanel.ts`;
- odczyt licznika `fetchFailedPosts(0, 1)` z licznikiem kolejności odpowiedzi;
- `focusTitle()` działa tylko raz na montaż układu: pierwsze wywołanie stawia fokus na `h1`, a kolejne (z części otwartej paskiem) nic nie robią;
- testy: `aria-current` według trasy, licznik z `total`, brak licznika przy 0 i przy błędzie, ponowny odczyt po `refreshFailedCount`, fokus na `h1` po pierwszym `focusTitle()` i bez zmiany fokusu po drugim (zmiana części).

#### 3. Trzy części z dzisiejszymi sekcjami

**File**: `szczur-radar-web/src/views/admin/AdminRunsView.vue`, `AdminQualityView.vue`, `AdminFailedPostsView.vue` (nowe); `szczur-radar-web/src/views/AdminView.vue` (usunięty)

**Intent**: Sekcje przechodzą bez zmiany swojego wyglądu i zachowania:
- „Nowy przebieg”, „Dni bez danych” i „Ostatnie przebiegi” do „Przebiegów”;
- „Jakość ocen (7 dni)” do „Jakości ocen”;
- „Nieudane posty” do „Nieudanych postów”.

Powiązania między sekcjami:
- start i „Wpisz do formularza” zostają w jednej części;
- koniec przebiegu odświeża tylko dni bez danych, bo jakość ocen czyta się świeżo przy wejściu w swoją część;
- „Odśwież” czyta trzy sekcje „Przebiegów”;
- ponowienie nieudanych postów nie odświeża już przebiegów, a do fazy 4 pokazuje dzisiejszy komunikat;
- przywrócenie woła `refreshFailedCount()`.

Wspólne funkcje sekcji (`sectionFocus`, typ `Read`) trafiają do jednego modułu zamiast trzech kopii.

**Contract**:
- `src/views/admin/sectionState.ts` z `sectionFocus` i `Read`, przeniesionymi z `AdminView.vue:41-90` bez zmiany zachowania;
- każda część ma swój styl lokalny, tylko ten, którego używa;
- każdy nowy plik trafia na `CLEANED` w miejsce `AdminView.vue`;
- po wczytaniu pierwszej sekcji część woła `focusTitle()` z układu, raz na swój montaż; układ wykona tylko pierwsze wywołanie (patrz Critical Implementation Details).

#### 4. Testy jednostkowe części

**File**: `szczur-radar-web/src/views/AdminView.test.ts` (usunięty) → `src/views/admin/AdminRunsView.test.ts`, `AdminQualityView.test.ts`, `AdminFailedPostsView.test.ts` (nowe)

**Intent**: Bloki `describe` przechodzą do testu swojej części bez osłabienia asercji:
- formularz startu, ładowanie i „Odśwież”, przebiegi, dni bez danych → Przebiegi;
- jakość ocen → Jakość ocen;
- nieudane posty → Nieudane posty;
- ponowienie sekcji (:378-521) do każdej części dla jej sekcji.

Znikają tylko asercje powiązań, których już nie ma: ponowienie odświeża przebiegi, a koniec przebiegu odświeża jakość. Ich brak stwierdza nowy test w danej części.

**Contract**: pomocnicza funkcja montowania z routerem w pamięci i układem panelu tam, gdzie test sprawdza `refreshFailedCount`.

#### 5. Testy E2E i wzorce za nowymi adresami

**File**: `szczur-radar-web/e2e/admin.spec.ts`, `szczur-radar-web/e2e/states.spec.ts`, `szczur-radar-web/e2e/shell.spec.ts`, `szczur-radar-web/visual/targets.ts`

**Intent**: Istniejące przepływy przechodzą na nowych adresach. „Panel admina wczytuje wszystkie sekcje” staje się sprawdzeniem trzech części: wejście przez pasek części, sekcje każdej części i `aria-current`. Tablica `SCREENS` w `e2e/shell.spec.ts:55-60` dostaje wiersze `/admin/quality` i `/admin/posts`: jeden pasek, jedna treść, jeden `h1` „Panel admina” i bieżący „Panel admina” w nawigacji głównej.

Stała `SECTIONS` w `states.spec.ts` dzieli się na części:
- `MODE: loading` sprawdza sekcje „Przebiegów” (`admin-loading`);
- `QUALITY: error` sprawdza alert w „Jakości ocen” i ponowienie, które wysyła tylko `GET /api/admin/assessment-quality`, a potem klik „Przebiegi” w pasku: ta część ma dane (`admin-error`, „pozostałe części działają”);
- nowy przypadek `MISSING: 'error'` na `/admin` przejmuje dzisiejszą asercję niezależności sekcji jednej strony: alert tylko w „Dni bez danych”, „Nowy przebieg” i „Ostatnie przebiegi” mają dane, a ponowienie wysyła tylko `GET /api/admin/pipeline-runs/missing-days`;
- `MODE: empty` sprawdza „Brak nieudanych postów” na `/admin/posts` i „Jeszcze nie było przebiegu” na `/admin` (`admin-empty`);
- `MODE: error` sprawdza alert w każdej części.

Cele wizualne:
- `view-admin` → `/admin`;
- `view-admin-loading` → `/admin`;
- `view-admin-empty` → `/admin/posts`;
- `view-admin-error` → `/admin/quality`;
- nowe `view-admin-quality` i `view-admin-posts`.

**Contract**:
- lokatory po roli i nazwie w `main`: `getByRole('navigation', { name: 'Panel admina' })`, odnośniki „Przebiegi”, „Jakość ocen” i `/^Nieudane posty/` (z licznikiem nazwa to „Nieudane posty 4”);
- warunki `ready` celów po nagłówkach i tekstach z „Wczytywanie…” równym 0, bez kliknięć.

### Success Criteria:

#### Automated Verification:

- Testy webu przechodzą, w tym router, układ panelu, trzy części i skan `CLEANED`: `cd szczur-radar-web && npm test`
- Build przechodzi: `cd szczur-radar-web && npm run build`
- Nie zostało nic ze starego widoku: `! grep -rn "AdminView" szczur-radar-web/src szczur-radar-web/e2e szczur-radar-web/visual`
- Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Wzorce widoków panelu są zapisane od nowa, inne widoki bez zmian: `cd szczur-radar-web && npm run visual:update && git status --porcelain visual/__screenshots__` pokazuje tylko `view-admin*.png` (w tym nowe `view-admin-quality.png` i `view-admin-posts.png`) w czterech projektach
- Testy wizualne i dostępności przechodzą dwa razy z rzędu: `cd szczur-radar-web && npm run visual:docker`
- Strona nie przewija się w poziomie przy 320 px na każdej części: `playwright-cli` na `npm run preview` z atrapą, `/admin`, `/admin/quality`, `/admin/posts`, `document.documentElement.scrollWidth <= 320`

Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy.

---

## Phase 4: Części według prototypów

### Overview

Każda część dostaje układ prototypu: tabelę przebiegów, karty jakości z paskiem i tabelę nieudanych postów ze źródłem, stanem, stronicowaniem, odnośnikiem do oryginału i komunikatem po ponowieniu z odnośnikiem do przebiegu.

### Changes Required:

#### 1. Przebiegi

**File**: `szczur-radar-web/src/views/admin/AdminRunsView.vue` (+ test)

**Intent**: Lista „Ostatnie przebiegi” staje się tabelą `DataTable` jak w `admin-runs.html:170-193`. „Nowy przebieg” i „Dni bez danych” dostają układ prototypu:
- karta formularza z polami „Od”, „Do”, grupą „Źródła” i przyciskiem „Uruchom” z ikoną `play`;
- wiersze dni z zakresem, źródłami i przyciskiem „Wpisz do formularza”.

Zachowanie zostaje bez zmian: odpytywanie, „Pokaż starsze”, scalanie wierszy po odświeżeniu i fokus na pierwszym dołożonym odnośniku.

**Contract**:
- podpis tabeli „Ostatnie przebiegi, od najnowszego”;
- kolumny:
  - Przebieg: `th scope="row"` z odnośnikiem o tekście `formatDateTime(createdAt)` i etykiecie „Szczegóły przebiegu z …”;
  - Rodzaj: słowo `runKind`;
  - Stan: znacznik stanu z `runTone`/`runStatusLabel` i, przy `needsAttention`, znacznik „wymaga uwagi” z ikoną `triangle-alert`;
  - Zakres: `runRange` albo puste;
  - Ocenione, Nieudane, Martwe: `num`, `zero` przy 0, ton `destructive` dla Nieudanych i Martwych > 0;
  - Czas: `runDuration`;
- nowy przebieg z odpowiedzi startu stoi w pierwszym wierszu.

#### 2. Jakość ocen

**File**: `szczur-radar-web/src/views/admin/AdminQualityView.vue` (+ test)

**Intent**: Karta każdego źródła jak w `admin-quality.html:144-188`:
- `h3` z nazwą źródła i znacznikiem źródła;
- dwie miary: „Nieudane próby oceny” i „Tezy w innym języku niż post”;
- każda miara ma wartość z jednym miejscem po przecinku, opis „N z M prób · próg P%” (albo „N z M tez · próg P%”), stan słowami i pasek;
- pod nieudanymi próbami udział klas błędu;
- pod kartami okno („14.10–20.10.2026”) i notka o progach z `minAttempts` i `minTheses`.

Znacznik źródła:
- „ponad progiem”, gdy którakolwiek miara jest ponad progiem;
- w przeciwnym razie stan miary nieudanych prób (`row.status`).

Miara tez bez danych (`language: null`) pokazuje „Tezy w innym języku niż post: nie udało się policzyć” bez paska, jak dziś.

**Contract**:
- pasek `role="img"` z `aria-label` równym tekstowi miary („Nieudane próby oceny: 6,1%, 191 z 3134 prób · próg 5%”);
- wypełnienie `min(100, wartość / (1,5 × próg) × 100)`%, kreska progu na 66,6%, szerokość przez `:style` (dozwolona w skanie);
- liczby z odmianą `plural` i formatem `pl-PL`;
- tryby atrapy `QUALITY` (`data`, `few`, `nolang`, `none`, `error`) pokryte testem jednostkowym.

#### 3. Nieudane posty

**File**: `szczur-radar-web/src/views/admin/AdminFailedPostsView.vue` (+ test)

**Intent**: Tabela `DataTable` jak w `admin-posts.html:144-168`:
- kolumna zaznaczenia;
- Post: tytuł albo początek treści, odnośnik do `webUrl` w nowej karcie z ikoną `external-link`, tekst bez `webUrl`;
- Źródło: `postOrigin`;
- Stan: „martwy”, „wstrzymany” albo „do ponowienia” jako znacznik o tonie `destructive`, `warning` albo `neutral`;
- Próby;
- Ostatni błąd: słowo klasy błędu albo „błędy techniczne: N”.

Nad przyciskami stoi „Zaznaczone: N”. Pod tabelą jest `PagerNav labels="order"`, a numer strony stoi w adresie. Po udanym „Ponów zaznaczone” część pokazuje komunikat powodzenia z odnośnikiem do nowego przebiegu. Pusty stan ma ikonę `circle-check`, tytuł „Brak nieudanych postów”, zdanie „Żaden post nie czeka na ponowienie ani nie jest martwy.” i odnośnik „Pokaż przebiegi” do `admin`.

**Contract**:
- strona z `?page=` przez `pageFromQuery`; zmiana strony to `router.push` z nowym `page`, a strona 0 bez parametru;
- `fetchFailedPosts(page, FAILED_PAGE_SIZE)`;
- zaznaczenie czyści się przy zmianie strony, po ponowieniu i po przywróceniu;
- strona większa niż ostatnia po przywróceniu przechodzi na ostatnią;
- „Ponów zaznaczone (N)” wysyła tylko zaznaczone niemartwe, „Przywróć martwe (N)” tylko zaznaczone martwe;
- odnośnik tytułu ma `aria-label` „<tytuł> (otwiera się w nowej karcie)”, `target="_blank"` i `rel="noopener noreferrer"`;
- pole wyboru ma etykietę „Zaznacz post: <tytuł>”;
- komunikat po ponowieniu to `StatusMessage tone="ok"` z tekstem „Uruchomiono ponowienie: N postów.” (odmiana `plural`) i odnośnikiem „Szczegóły przebiegu z <formatDateTime(createdAt)>” do `admin-run` pierwszego przebiegu z odpowiedzi;
- błędy startu z `startErrorMessage` (`pipeline_busy`, `post_dead`);
- po przywróceniu odczyt bieżącej strony i `refreshFailedCount()`;
- fokus po akcji wraca na przycisk albo komunikat przez `restoreFocus`.

#### 4. Wzorce

**File**: `szczur-radar-web/visual/targets.ts`, `szczur-radar-web/visual/__screenshots__/**`

**Intent**: Wzorce części zmieniają się razem z ich wyglądem. Warunki `ready` celów idą za nowymi rolami: tabela, nagłówki `h3` źródeł i pasek części z licznikiem.

**Contract**: grupy `view-admin`, `view-admin-loading`, `view-admin-empty`, `view-admin-error`, `view-admin-quality` i `view-admin-posts` w czterech projektach.

### Success Criteria:

#### Automated Verification:

- Testy webu przechodzą, w tym testy trzech części (tabela przebiegów, pasek jakości dla każdego trybu `QUALITY`, stronicowanie, zaznaczenie, komunikat po ponowieniu z odnośnikiem, przywrócenie z odczytem licznika, odnośnik do oryginału i tekst bez `webUrl`) oraz skan `CLEANED`: `cd szczur-radar-web && npm test`
- Build przechodzi: `cd szczur-radar-web && npm run build`
- Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Wzorce części są zapisane od nowa, inne bez zmian: `cd szczur-radar-web && npm run visual:update && git status --porcelain visual/__screenshots__` pokazuje tylko `view-admin*.png`
- Testy wizualne i dostępności przechodzą dwa razy z rzędu (axe na tabelach, ramkach przewijanych i paskach `role="img"`): `cd szczur-radar-web && npm run visual:docker`
- Kopie projektu są zgodne: `node tools/design-check/design-check.mjs app`
- Bez przewijania strony w poziomie przy 320 px na trzech częściach (tabele przewijają się w swoich ramkach): `playwright-cli` na `npm run preview` z atrapą, także z `FAILED: 'many'`, `document.documentElement.scrollWidth <= 320`
- Stany spoza wzorców mają zrzut albo N/A z powodem, w obu motywach przy 375 px: ładowanie i błąd każdej części, wyłączone „Ponów zaznaczone”/„Przywróć martwe” bez zaznaczenia, zajęty „Uruchom”, druga strona nieudanych postów. Zrzuty `playwright-cli` są w `context/changes/admin-panel-components/evidence/states/` z listą w `README.md`.

Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy.

---

## Phase 5: Strona przebiegu według prototypu

### Overview

`RunDetailView.vue` dostaje układ z `run.html`. Dane, odpytywanie i stany zostają.

### Changes Required:

#### 1. Widok przebiegu

**File**: `szczur-radar-web/src/views/RunDetailView.vue`, `szczur-radar-web/src/views/RunDetailView.test.ts`

**Intent**:
- powrót „Panel admina” z ikoną `chevron-left` do `admin`;
- `h1` „Przebieg z <formatDateTime(createdAt)>” ze znacznikiem stanu;
- komunikat „Wymaga uwagi: …” z `src/runs.ts`;
- metadane `dl`: Rodzaj, Zakres, Źródła, Uruchomił, Start, Koniec, Czas trwania;
- „Liczniki” jako siatka liczb, z tonem alarmu dla Nieudanych i Martwych > 0;
- „Wynik per źródło” jako karty ze znacznikiem `sourceTone` i opisem „pobrane N · pominięte N · posty N”;
- „Kroki” jako lista numerowana z nazwą, opisem („N elementów · czas”), znacznikiem i błędem kroku.

„Postęp” zostaje dla trwającego przebiegu, a „Błąd przebiegu” dla przebiegu z błędem. Zachowanie bez zmian: odczyt co 10 s, gdy trwa, zmiana `id`, pusty stan „Nie ma takiego przebiegu” i fokus na nagłówku.

**Contract**:
- `h2` „Liczniki”, „Wynik per źródło” i „Kroki” jak w kontrakcie E2E (`ui-design.md:587`);
- czasy w `time` z `datetime`;
- styl lokalny tylko z tokenów (plik zostaje na `CLEANED`);
- testy jednostkowe idą za nowym nagłówkiem i nowymi rolami bez osłabienia asercji zachowania.

#### 2. Testy E2E i wzorzec

**File**: `szczur-radar-web/e2e/admin.spec.ts`, `szczur-radar-web/e2e/shell.spec.ts`, `szczur-radar-web/visual/targets.ts`

**Intent**: Asercje przechodzą z „Przebieg: zakres dat” na „Przebieg z …”. Cel `view-run` dostaje nowy warunek gotowości i nowy wzorzec.

**Contract**: wzorzec `view-run.png` w czterech projektach.

### Success Criteria:

#### Automated Verification:

- Testy webu przechodzą, w tym `RunDetailView.test.ts` i skan `CLEANED`: `cd szczur-radar-web && npm test`
- Build przechodzi: `cd szczur-radar-web && npm run build`
- Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Zmienił się tylko wzorzec strony przebiegu: `cd szczur-radar-web && npm run visual:update && git status --porcelain visual/__screenshots__` pokazuje tylko `view-run.png` w czterech projektach
- Testy wizualne i dostępności przechodzą dwa razy z rzędu: `cd szczur-radar-web && npm run visual:docker`
- Bez przewijania w poziomie przy 320 px na stronie przebiegu z błędem kroku (`RUN_3`) i na trwającej (`RUN_1`): `playwright-cli`, `document.documentElement.scrollWidth <= 320`
- Zrzuty stanów spoza wzorców (ładowanie, błąd z ponowieniem, „Nie ma takiego przebiegu”, trwający z „Postępem”) w obu motywach przy 375 px są w `context/changes/admin-panel-components/evidence/states/`

Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy.

---

## Phase 6: Testy przepływów, dokumentacja i weryfikacja

### Overview

Nowe przepływy dostają testy E2E według kontraktu projektu, każdy złamany raz na dowód. Dokumentacja zapisuje zmianę G2 i nowe adresy. Agent sprawdza PR env, a właściciel ocenia wygląd na zrzutach obok prototypów.

### Changes Required:

#### 1. Testy E2E nowych przepływów

**File**: `szczur-radar-web/e2e/admin.spec.ts`, `context/changes/admin-panel-components/evidence/e2e-breaks.md` (nowy)

**Intent**: Testy według kolumny przepływów `ui-design.md:584-587`, nazwane po ryzyku:
- klik części w pasku zmienia adres i `aria-current`, a fokus zostaje na klikniętym odnośniku;
- „Wpisz do formularza: …” wpisuje zakres do „Od” i „Do” i stawia fokus na „Od”;
- „Pokaż starsze” wydłuża tabelę;
- każda miara jakości ma wartość, próg i stan słowami;
- zaznaczenie postów zwiększa osobno liczby w „Ponów zaznaczone (N)” i „Przywróć martwe (N)”;
- „Ponów zaznaczone” daje komunikat z odnośnikiem, który otwiera stronę nowego przebiegu;
- „Przywróć martwe” usuwa posty z tabeli i zmniejsza licznik w pasku;
- tytuł nieudanego posta prowadzi do oryginału w nowej karcie (`href` i `target`, bez otwierania);
- z `MODE: 'empty'` na `/admin/posts` pusty stan nie ma tabeli, odnośnik w pasku nazywa się dokładnie „Nieudane posty” (bez licznika), a „Pokaż przebiegi” prowadzi na `/admin` (kontrakt `admin-empty`, `ui-design.md:592`);
- z `FAILED: 'many'` „Następne” daje drugą stronę i `page=1` w adresie, a odświeżenie zostaje na niej;
- „Panel admina” na stronie przebiegu wraca do części „Przebiegi”;
- wejście na `/admin/posts` bez roli admina odbija na `/`.

**Contract**:
- `test`/`expect` z `e2e/fixtures.ts`;
- lokatory po roli, etykiecie i tekście w `main` albo regionie, etykiety `exact: true`;
- bez selektorów CSS, XPath i `waitForTimeout`;
- każdy test złamany raz zmianą w kodzie aplikacji, z zapisem w `e2e-breaks.md`: test, zmiana, czerwony wynik, cofnięcie.

#### 2. Dokumentacja

**File**: `context/foundation/ui-design.md`, `REPOSITORY_GUIDELINES.md`, `context/foundation/roadmap.md` (tylko status), `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Projekt zapisuje decyzję G2: w „Nieudanych postach” tytuł prowadzi do oryginału, nie do panelu posta. Właściciel zaakceptował ją 2026-11-24 w `/10x-plan` S-50. Zmiana trafia do kontraktu `admin-posts` (:586), do tabeli decyzji i do akapitu „Tabela”.

Wytyczne repozytorium opisują:
- trasę `/admin` z dziećmi `admin`, `admin-quality` i `admin-posts`;
- układ panelu `src/views/admin/`;
- komponenty `AdminPartsNav` i `DataTable` na liście wspólnych komponentów;
- nowy tryb atrapy `FAILED` w opisie atrapy.

Roadmapa i jej rysunek dostają tylko status wycinka, przez `/10x-implement` i `/10x-archive`.

**Contract**: sekcje „Ekrany” i „Kontrakt dla testów E2E” w `ui-design.md`; punkt `szczur-radar-web/` w „Project Structure” i akapity „Shared components” i „Visual check” w `REPOSITORY_GUIDELINES.md`.

#### 3. Opis PR i PR env

**File**: opis PR, `context/changes/admin-panel-components/evidence/pr-env/README.md` (nowy)

**Intent**:
- Opis PR wymienia grupy wzorców: nowe `kitchen-sink-*` i `state-*`, zmienione `view-admin*` i `view-run`, nowe `view-admin-quality` i `view-admin-posts`, z powodem każdej. Zawiera `Refs #182`.
- Agent sprawdza w PR env trzy części i stronę przebiegu na prawdziwym API, ze świeżo założonym kontem admina (lessons.md, „W PR env agent sam zakłada jednorazowe konto admina”).

**Contract**: wyniki w `evidence/pr-env/README.md` bez sekretów.

### Success Criteria:

#### Automated Verification:

- Wszystkie testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Każdy nowy test E2E był raz czerwony na celowym złamaniu: `context/changes/admin-panel-components/evidence/e2e-breaks.md` ma wiersz dla każdego nowego testu, z czerwonym wynikiem i cofnięciem
- Testy webu, build, testy wizualne dwa razy i kopie projektu przechodzą: `cd szczur-radar-web && npm test && npm run build && npm run visual:docker`, potem `node tools/design-check/design-check.mjs app`
- Testy backendu przechodzą: `cd szczur-radar-api && ./mvnw -B verify`
- Opis PR zawiera grupy wzorców i issue: `gh pr view --json body -q .body | grep -E "view-admin-posts|view-run|kitchen-sink-tabela|Refs #182"`
- CI PR jest zielone, w tym `web-e2e` (krok wizualny), `web-container-smoke`, `mutation` i `railway-config`: `gh pr checks`
- PR env: agent zakłada jednorazowe konto admina według lessons.md i sprawdza na publicznym adresie PR env (`playwright-cli` z sesją z `curl`):
  - `/admin`: pasek części, tabela przebiegów i jej liczba wierszy równa `GET /api/admin/pipeline-runs` z tą samą sesją;
  - `/admin/quality`: karty źródeł zgodne z `GET /api/admin/assessment-quality`;
  - `/admin/posts`: tabela albo pusty stan zgodny z `GET /api/admin/posts/failed`, kolumna „Źródło” wypełniona, licznik w pasku równy `total`;
  - strona przebiegu z tabeli: nagłówek „Przebieg z …”;
  - `scrollWidth <= 375` przy 375 px na każdej części;
  - wynik bez sekretów w `evidence/pr-env/README.md`.
  
  PR env bez przebiegów dostaje jeden przebieg jednego dnia z formularza przed kontrolą.

#### Manual Verification:

- Właściciel akceptuje wygląd trzech części i strony przebiegu w galerii zrzutów obok prototypów `admin-runs`, `admin-quality`, `admin-posts`, `run`, `admin-loading`, `admin-empty`, `admin-error` (jasny i ciemny, 375 i 1280 px). Galerię publikuje agent jako Artifact z osadzonymi obrazami.

---

## Testing Strategy

### Unit Tests:

- `AdminPartsNav`, `DataTable`: `aria-current`, licznik, podpis, nazwa ramki.
- Układ panelu: licznik z `total`, brak przy 0 i błędzie, ponowny odczyt po przywróceniu.
- Części: dzisiejsze testy sekcji przeniesione bez osłabienia. Nowe:
  - tabela przebiegów;
  - paski jakości dla każdego trybu `QUALITY` i reguła znacznika źródła;
  - stronicowanie z adresem i przejście na ostatnią stronę po przywróceniu;
  - zaznaczenie per strona;
  - komunikat po ponowieniu z odnośnikiem;
  - odnośnik do oryginału i tekst bez `webUrl`.
- Router: nowe adresy, odbicie bez roli admina, brak remontu układu.
- Atrapa: tryb `FAILED` w `mockApi.test.ts`.

### Integration Tests:

- `PostsApiIT`: pochodzenie posta w odpowiedzi listy, przypięte plikiem kontraktu.
- E2E: przepływy kontraktu `ui-design.md:584-594` i każdy nowy przepływ z fazy 6, każdy złamany raz.
- Wizualne: wzorce `view-admin*`, `view-run`, `kitchen-sink-*`, `state-*` i axe na każdym widoku.

### Manual Testing Steps:

1. Właściciel otwiera galerię i porównuje zrzuty części i strony przebiegu z prototypami w obu motywach i obu szerokościach.

## Performance Considerations

Licznik w pasku czyta jedną stronę wielkości 1 przy każdej zmianie części, czyli jedno małe zapytanie na kliknięcie. Odpytywanie co 10 s zostaje tylko w „Przebiegach” i na stronie przebiegu, tylko gdy przebieg trwa.

## Migration Notes

Bez migracji bazy. Odpowiedź `GET /api/admin/posts/failed` dostaje dwa nowe pola. Jedynym klientem jest SPA w tym samym PR, więc wdrożenie API przed webem i webu przed API jest zgodne. Adres `/admin` zostaje.

## References

- Projekt: `context/foundation/ui-design.md` (Panel admina :141-149, Ekrany :172-175, Komponenty :474 i :482, Kontrakt E2E :584-594 i :607-608)
- Prototypy: `context/foundation/ui-design/prototypes/screens/admin-runs.html`, `admin-quality.html`, `admin-posts.html`, `run.html`, `admin-loading.html`, `admin-empty.html`, `admin-error.html`; arkusz `components.html#c-podnawigacja`, `#c-tabela`
- Roadmapa: `context/foundation/roadmap.md:281-291` (S-50), issue #182
- Poprzednie wycinki: `context/archive/2026-11-23-shared-view-states/plan.md` (stany sekcji, :363-437), `context/archive/2026-11-22-app-shell-navigation/plan.md` (powłoka, galeria), `context/archive/2026-11-22-login-on-shared-foundation/plan.md` (`restoreFocus`, zrzuty stanów)
- Kod: `szczur-radar-web/src/views/AdminView.vue`, `src/views/RunDetailView.vue`, `src/router/index.ts:54-64`, `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/FailedPostController.java:89`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Pochodzenie nieudanego posta w API

#### Automated

- [x] 1.1 Testy backendu przechodzą (PostsApiIT, ContractVariantsTest, ContractRoutesIT): `./mvnw -B verify` — 7334a7a
- [x] 1.2 Testy webu przechodzą (shapes, responseTypes, mockApi z trybem FAILED): `npm test` — 7334a7a
- [x] 1.3 Typy i build przechodzą: `npm run build` — 7334a7a
- [x] 1.4 Plik kontraktu ma pochodzenie w każdym elemencie (`jq -e`) — 7334a7a

### Phase 2: Komponenty „Części panelu admina” i „Tabela”

#### Automated

- [x] 2.1 Testy webu przechodzą (komponenty, CLEANED): `npm test` — 6a96857
- [x] 2.2 Build przechodzi: `npm run build` — 6a96857
- [x] 2.3 Wzorce nowych sekcji i stanów zapisane, innych plików nie ruszono (`visual:update` + `git status`) — 6a96857
- [x] 2.4 Testy wizualne i dostępności przechodzą dwa razy z rzędu: `npm run visual:docker` — 6a96857
- [x] 2.5 Kopie projektu zgodne: `design-check.mjs app` — 6a96857
- [x] 2.6 Katalog bez przewijania w poziomie przy 320 px (`playwright-cli`) — 0d5530e

### Phase 3: Rozbicie panelu na trzy adresy

#### Automated

- [x] 3.1 Testy webu przechodzą (router, układ, trzy części, CLEANED): `npm test` — 31f7c4e
- [x] 3.2 Build przechodzi: `npm run build` — 31f7c4e
- [x] 3.3 Nie zostało nic ze starego widoku (`! grep AdminView`) — 31f7c4e
- [x] 3.4 Testy E2E przechodzą: `npm run e2e` — 31f7c4e
- [x] 3.5 Wzorce widoków panelu zapisane od nowa, inne bez zmian (`visual:update` + `git status`) — 31f7c4e
- [x] 3.6 Testy wizualne i dostępności przechodzą dwa razy z rzędu: `npm run visual:docker` — 31f7c4e
- [x] 3.7 Bez przewijania w poziomie przy 320 px na każdej części (`playwright-cli`) — 31f7c4e

### Phase 4: Części według prototypów

#### Automated

- [x] 4.1 Testy webu przechodzą (tabele, paski jakości, stronicowanie, zaznaczenie, komunikat z odnośnikiem, przywrócenie, odnośnik do oryginału, CLEANED): `npm test` — 079ee63
- [x] 4.2 Build przechodzi: `npm run build` — 079ee63
- [x] 4.3 Testy E2E przechodzą: `npm run e2e` — 079ee63
- [x] 4.4 Wzorce części zapisane od nowa, inne bez zmian (`visual:update` + `git status`) — 079ee63
- [x] 4.5 Testy wizualne i dostępności przechodzą dwa razy z rzędu: `npm run visual:docker` — 079ee63
- [x] 4.6 Kopie projektu zgodne: `design-check.mjs app` — 079ee63
- [x] 4.7 Bez przewijania strony w poziomie przy 320 px na trzech częściach, także z FAILED many (`playwright-cli`) — 079ee63
- [x] 4.8 Zrzuty albo N/A stanów spoza wzorców w `evidence/states/` — 079ee63

### Phase 5: Strona przebiegu według prototypu

#### Automated

- [x] 5.1 Testy webu przechodzą (RunDetailView, CLEANED): `npm test` — c428ec8
- [x] 5.2 Build przechodzi: `npm run build` — c428ec8
- [x] 5.3 Testy E2E przechodzą: `npm run e2e` — c428ec8
- [x] 5.4 Zmienił się tylko wzorzec view-run (`visual:update` + `git status`) — c428ec8
- [x] 5.5 Testy wizualne i dostępności przechodzą dwa razy z rzędu: `npm run visual:docker` — c428ec8
- [x] 5.6 Bez przewijania w poziomie przy 320 px na stronie przebiegu (`playwright-cli`) — c428ec8
- [x] 5.7 Zrzuty stanów strony przebiegu spoza wzorców w `evidence/states/` — c428ec8

### Phase 6: Testy przepływów, dokumentacja i weryfikacja

#### Automated

- [x] 6.1 Wszystkie testy E2E przechodzą: `npm run e2e` — 5b499b8
- [x] 6.2 Każdy nowy test E2E był raz czerwony na celowym złamaniu (`evidence/e2e-breaks.md`) — 5b499b8
- [x] 6.3 Testy webu, build, wizualne dwa razy i kopie projektu przechodzą — 5b499b8
- [x] 6.4 Testy backendu przechodzą: `./mvnw -B verify` — 5b499b8
- [x] 6.5 Opis PR zawiera grupy wzorców i `Refs #182` (`gh pr view`) — 5b499b8
- [x] 6.6 CI PR zielone: `gh pr checks` — 5b499b8
- [x] 6.7 PR env: trzy części i strona przebiegu zgodne z API, bez przewijania przy 375 px (`evidence/pr-env/README.md`) — 5b499b8

#### Manual

- [ ] 6.8 Właściciel akceptuje wygląd trzech części i strony przebiegu w galerii zrzutów obok prototypów
