# Lista postów z oceną w panelu bocznym (S-51) — plan implementacji

## Overview

S-51 zastępuje dzisiejszy feed z kartami (`/`) i osobną stronę posta (`/posts/:id`) jednym ekranem „Posty” pod `/posts`. Ekran ma trzy części:

- zwartą listę: jeden post w wierszu, wiersze pogrupowane po dniach;
- filtr po celu i stanowisku w panelu „Filtry”, ze znacznikami włączonych filtrów;
- ocenę wybranego posta w modalnym panelu wysuwanym z prawej, nad przyciemnioną listą. Poniżej 900 px panel zajmuje cały ekran.

Wybrany post ma własny adres. Zmienia się tylko SPA, API zostaje bez zmian. To gwiazda przewodnia M-5 (`context/foundation/roadmap.md`, S-51; issue #183). Wygląd i zachowanie biorę z `context/foundation/ui-design.md` (prototypy `posts`, `post`, `posts-filters`).

## Current State Analysis

- **Trasy** (`szczur-radar-web/src/router/index.ts:50-63`):
  - `''` → `home` (`FeedView`) i `posts/:id` → `post` (`PostDetailView`), obie z `column: 'narrow'`;
  - strażnik i `safeRedirect` odsyłają na `{ path: '/' }` (`router/index.ts:90, 93`);
  - S-47 zostawił adres `/posts` temu wycinkowi (`context/archive/2026-11-22-app-shell-navigation/plan-brief.md:31`).
- **Lista** (`src/views/FeedView.vue`):
  - ładuje strony (`fetchPosts`, `FEED_PAGE_SIZE = 50`) w stanach `fresh`/`page`/`retry` i ustawia fokus;
  - filtr i strona żyją w adresie (`src/feedFilter.ts`);
  - rysuje `PostCard` (`article` z `h2` i linkiem), `PagerNav`, wspólne stany oraz wiersz statusu „Ocenione posty: N” / „Pasujące do filtra: N”;
  - zgłasza koniec wczytania do `router/scroll.ts` (`reportFeedLoaded`).
- **Strona posta** (`src/views/PostDetailView.vue`):
  - wczytuje `GET /api/posts/:id`;
  - tytuł bez tytułu posta to „{Typ} użytkownika {autor}” (`:40-47`);
  - „Otwórz oryginał na {host}” tylko dla http(s) (`:50-59`), treść, `PostAssessment`, `details` „Dane dla nerdów: surowy JSON źródła” z leniwym `/raw` i jego stanami;
  - powrót do listy przez `history.state.back` (`:128-149`).
- **Przewijanie** (`src/router/scroll.ts`): przywraca pozycję listy po powrocie ze strony posta, dopiero gdy `FeedView` zgłosi wczytanie, i oddaje fokus linkowi posta. Lista jest dziś odmontowywana przy wejściu w post, stąd ta logika.
- **Dane** (badanie API):
  - element `GET /api/posts` to pełny `PostItem` z oceną V2: `id`, `source`, `channel`, `itemType`, `publishedAt`, `title?`, `body?`, `author?`, `webUrl?` i `assessment { output, resultSource, assessedAt }`;
  - `GET /api/posts/{id}` zwraca ten sam kształt (`szczur-radar-api/.../posts/api/PostController.java:87-118`);
  - lista zawiera tylko posty `ASSESSED`, od najnowszych, stronicowane offsetem;
  - wiersz i panel nie potrzebują nowych danych.
- **Komponenty** (`src/components/`):
  - `PostCard` (karta), `PostAssessment` (cele z twierdzeniami, tezy ogólne, zastrzeżenia), `FeedFilter` (trzy pola w jednym rzędzie), `StanceText`, `StatusBadge`, `PageHeader` (slot `tools`, `focus()`), `EmptyState`, `ErrorState`, `LoadingState`, `PagerNav`, `AppButton`, `AppIcon`;
  - wzorzec wyskakującego panelu ze zamykaniem przez Esc i kliknięcie poza nim to `NotificationMenu.vue:4, 78-95`.
- **Tokeny** są już w `src/style.css`: `--selected`, `--scrim`, `--width-panel` (44rem), `--width-content` (70rem), `--duration`, `--ease-out`, `--z-scrim`, `--z-overlay`, `--shadow-overlay`, `--radius-2xl`. Nikt nie używa jeszcze `--width-panel`, `--z-scrim`, `--duration` ani `--scrim`.
- **Testy:**
  - E2E: `e2e/feed.spec.ts` (6 testów, karty `article`), `e2e/post.spec.ts` (strona posta), `e2e/session-guard.spec.ts` (h1 posta po logowaniu), `e2e/states.spec.ts` (stany listy);
  - wzorce F-06 w `visual/targets.ts:125-187`: `view-feed` (przycięty do `article`, `clipToArticle`), `view-feed-{loading,empty,error}` i `view-post`;
  - katalog `/kitchen-sink`: sekcje „Filtr”, „Posty”, „Ocena posta”;
  - `src/styleContract.test.ts` `CLEANED` wymienia `FeedView`, `PostCard`, `PostAssessment`, `PostDetailView` i `FeedFilter`.

## Desired End State

- **Adresy.** `/posts` to lista z filtrem i stroną w adresie. `/posts/:id?<ten sam filtr i strona>` to lista z otwartym panelem tego posta. `/` przekierowuje na `/posts` z tymi samymi parametrami. Odświeżenie, link i „wstecz” zachowują wybór.
- **Lista.**
  - Regiony dni z nagłówkiem 2 („Dziś, 20 października”, „Wczoraj, 19 października”, „Sobota, 17 października”), a w każdym lista wierszy.
  - Wiersz jest jednym odnośnikiem: godzina, źródło i kanał, tytuł (albo początek treści zwykłą grubością), dwa pierwsze cele ze stanowiskiem i „i N inne”, flagi tonem `--warning`, „nie inwestycyjny”, „bez celów”.
  - Wiersz otwartego posta ma `aria-current="true"`, tło `--selected` i pasek akcentu.
  - Poniżej 900 px wiersz ma kilka linii.
- **Panel posta.**
  - `dialog` z `aria-modal="true"` i nazwą równą tytułowi posta. Pasek: źródło, kanał, typ, data i „Zamknij post”.
  - Treść: tytuł, autor, „Otwórz oryginał na {host}”, tekst posta, „Ocena” z celami i twierdzeniami z cytatem, „Surowe dane” (`aria-expanded`) z regionem „Surowe dane posta”.
  - Reszta strony jest `inert` pod przyciemnieniem. Fokus wchodzi do panelu, Tab w nim krąży, Esc i „Zamknij post” zamykają panel, a fokus wraca na wiersz.
  - Poniżej 900 px panel zajmuje cały ekran, a „wstecz” go zamyka.
- **Filtry.**
  - Przycisk „Filtry” albo „Filtry: N” z `aria-expanded`.
  - Pod nim panel z polami „Spółka lub temat”, „Rodzaj celu”, „Stanowisko” i przyciskami „Filtruj”, „Wyczyść filtry”, „Zamknij filtry”. Esc zamyka panel i oddaje fokus przyciskowi.
  - Nad listą stoi lista „Włączone filtry” ze znacznikami „Usuń filtr: …” i „Wyczyść filtry”.
- **Zniknęły** `PostDetailView`, `PostCard` i logika przywracania przewijania z `router/scroll.ts`.
- **Weryfikacja.** Przechodzą `npm test`, `npm run e2e` i `npm run visual:docker`, a CI na PR jest zielone. W PR env agent sprawdza listę i panel na prawdziwych danych. Właściciel akceptuje wygląd w galerii zrzutów.

### Key Discoveries:

- Element listy ma pełną ocenę, więc panel posta z bieżącej strony nie potrzebuje żądania. Tylko post spoza strony czyta `GET /api/posts/:id` (`PostController.java:102-106`).
- Lista zawiera tylko posty `ASSESSED` (`PostRepository.java:123-131`), więc stany wiersza „czeka na ocenę” i „ocena nieudana” z projektu tu nie występują. Post z prefiltra ma `resultSource: 'prefilter'` i flagę `insufficient_content` („za mało treści”, `labels.ts:19`).
- Vue Router ponownie używa komponentu rodzica przy zmianie trasy dziecka. Lista pod `/posts` zostaje więc zamontowana przy otwarciu `/posts/:id`, pod warunkiem że zapytanie (filtr, strona) jest to samo. Watcher `[page, filter]` w `FeedView.vue:219-246` nie przeładuje wtedy listy.
- `scrollBehavior` (`router/scroll.ts`) przewija na górę przy każdej zmianie ścieżki. Przejście `/posts` ↔ `/posts/:id` to inna ścieżka, więc bez wyjątku lista skakałaby na górę.
- Wzorzec zamykania wyskakującego panelu jest w `NotificationMenu.vue:78-95`.
- Pole „Szukaj w postach” wymaga wyszukiwania w treści (FR-036), czyli nowego API. Należy do S-56 (roadmapa S-56, `ui-design.md`, „Różnice wobec roadmapy”), choć plan S-48 (`context/archive/2026-11-23-shared-view-states/plan.md:81`) przypisał je S-51. Dzisiejsze `q` dopasowuje tekst celu i zostaje polem „Spółka lub temat”.

## What We're NOT Doing

- Przełącznik „Inwestycyjne / Wszystkie” z liczbami (S-52). Wiersz statusu „Ocenione posty: N” / „Pasujące do filtra: N” zostaje do S-52.
- Podświetlenie cytatów w treści, pole „Podświetl fragmenty…”, przyciski „Poprzedni post” / „Następny post” i pozycja „4 z 128” w pasku panelu (S-53).
- Kolejność (S-54), pola „Od”/„Do” i znacznik dat (S-55), pole „Szukaj w postach” (S-56).
- Stany wiersza „czeka na ocenę” i „ocena nieudana”: lista ma tylko posty ocenione.
- Zmiany API, kontraktu (`src/contract/*.json`) i atrapy poza opisem jej nagłówka.
- Karta celu w panelu (S-58). `SidePanel` jest tylko gotowy do jej użycia.
- Skróty klawiatury w panelu („Propozycje poza PRD” w `ui-design.md`).
- Przegląd dnia pod `/` (S-59). Do niego `/` przekierowuje na `/posts`.

## Implementation Approach

Ekran zostaje jednym widokiem listy (`FeedView`) pod trasą `posts`. Post to trasa dziecka `post` (`/posts/:id`), którą lista rysuje jako panel nad sobą, więc lista nie jest odmontowywana ani ładowana ponownie.

Odnośnik wiersza niesie bieżące zapytanie listy. Kliknięcie dodaje wpis historii. Zamknięcie wraca `router.back()`, gdy poprzedni wpis to ta sama lista. Inaczej (wejście z linku, po logowaniu) robi `router.replace` na `/posts` z tym samym zapytaniem.

Panel jest ogólnym komponentem `SidePanel` (dialog, warstwy, fokus, szerokości), który S-58 użyje dla karty celu. Zawartość posta jest osobnym komponentem z tytułem, oryginałem, treścią, oceną i surowymi danymi przeniesionymi z `PostDetailView`. Komponent trasy `PostPanel` łączy oba i wybiera źródło danych. `FeedView` rysuje go przez `RouterView` i podaje mu wiersze bieżącej strony.

Fazy idą od listy (z działającą starą stroną posta), przez panel (usuwa starą stronę i logikę przewijania), do panelu filtrów. Każda faza kończy się zielona: z własnymi testami jednostkowymi, E2E, wzorcami F-06 i sekcjami katalogu.

## Critical Implementation Details

- **Zapytanie listy w adresie posta.** Odnośnik wiersza to `{ name: 'post', params: { id }, query: route.query }`. Bez zapytania watcher listy uzna zmianę strony albo filtra i przeładuje listę na stronę 0. `scrollBehavior` nie przewija, gdy i `from`, i `to` należą do ekranu postów (`posts` lub `post`).
- **Warstwy.** Panel i przyciemnienie przenosi `Teleport` do `body`, poza `#app`. Na czas otwarcia `#app` dostaje `inert`, a `body` blokadę przewijania. Dlatego `dialog` nie stoi w `main` i E2E szuka go w `page`, nie w `main`.
- **Fokus po zamknięciu.** Fokus wraca na odnośnik wiersza tego posta (`aria-current`), gdy wiersz jest na stronie. Inaczej (post spoza strony) wraca na nagłówek „Posty”.
- **Ruch.** Wysunięcie trwa `--duration` z `--ease-out`, tylko przy `prefers-reduced-motion: no-preference`. Wzorce F-06 i E2E nie mogą czekać na animację: zrzut powstaje po jej końcu albo przy wyłączonym ruchu, jak dziś puls szkieletu.

## Faza 1: Adres i zwarta lista

### Overview

Lista przechodzi pod `/posts` i dostaje wygląd listy zwartej z grupami dni. Stara strona posta działa dalej pod `/posts/:id`, więc faza jest samodzielnie zielona.

### Changes Required:

#### 1. Trasy

**File**: `szczur-radar-web/src/router/index.ts`, `src/router/scroll.ts`, `src/components/AppShell.vue`, `src/components/AppBar.vue`, `src/views/KitchenSinkView.vue`

**Intent**: Lista ma docelowy adres `/posts`, a `/` na nią przekierowuje, aż przyjdzie przegląd dnia (S-59). Nawigacja i marka prowadzą na listę.

**Contract**:
- Trasa `posts` (`/posts`, `FeedView`, `meta: { section: 'posts' }`) bez `column`. Lista zwarta zajmuje całą kolumnę powłoki, czyli do `--width-content` (`ui-design.md`, „Szeroki i wąski ekran”). Trasa bez `column` już dziś tak działa (`router/index.ts:31-32`, `AppShell.vue`).
- `''` przekierowuje na `{ name: 'posts', query: to.query }`.
- `home` znika; odwołania `{ name: 'home' }` w `AppShell.vue:32`, `AppBar.vue:40`, `KitchenSinkView.vue:193, 760` i `PostDetailView` przechodzą na `posts`.
- Strażnik i `safeRedirect` zostają przy `/`, które przekierowuje dalej.
- W `scroll.ts` warunek przywracania pozycji czyta `to.name === 'posts'` zamiast `home`. Logikę usuwa faza 2.
- Pierwszy commit gałęzi (folder zmiany i zmieniona `context/foundation/roadmap.md`, S-51 `planning`): status S-51 w `tools/render_roadmap_svg.py:240` też na `planning` i `docs/visuals/roadmap.svg` wygenerowany ponownie (`python3 tools/render_roadmap_svg.py`). Tak każe `REPOSITORY_GUIDELINES.md`.

#### 2. Wiersz posta

**File**: `src/components/PostRow.vue` (nowy; zastępuje `src/components/PostCard.vue` i `PostCard.test.ts`), `src/labels.ts`

**Intent**: Jeden post w jednej linii według projektu („Wiersz posta”, `ui-design.md`; prototyp `posts.html:153-158`, `prototype.css:950-1031, 2371-2392`). Stanowisko pokazuje per cel, bo kontrakt nie ma sentymentu posta (`prd.md:133`).

**Contract**:
- Props: `post: PostItem`, `to: RouteLocationRaw`, `selected: boolean`.
- Element listy `li` z jednym `RouterLink`, który ma `aria-current="true"`, gdy `selected`. Kolejność w dokumencie:
  - `time` z godziną w Warszawie („09:41”, nowy formatter w `labels.ts`);
  - `postOrigin(source, channel)`;
  - typ słowami źródła (`postType`): w DOM dla czytnika, wizualnie ukryty jak w prototypie;
  - tytuł krojem `--font-display` albo, bez tytułu, początek `body` zwykłą grubością w kolorze `--text`. Jedna linia z wielokropkiem; poniżej 900 px do 3 linii;
  - cele: dwa pierwsze w kolejności modelu, każdy jako `StanceText` z `target` i `kind`, i „i N inne” przy więcej niż dwóch;
  - flagi `label('flag', …)` tonem `--warning`;
  - „nie inwestycyjny” dla `relevance: not_investment_related`;
  - „bez celów” dla posta ocenionego przez model bez celów.

  Post z prefiltra pokazuje swoją flagę „za mało treści” i nie pokazuje „bez celów”. Brak oceny (`assessment: null`, w praktyce nie występuje) pokazuje sam tytuł.
- Wygląd z tokenów: siatka `13.5rem minmax(0,1fr) auto` od 900 px, wskazanie `--surface-2`, wybrany `--selected` z `inset 3px 0 0 var(--accent)`, fokus jak globalny `:focus-visible` z ujemnym odsunięciem. Od 320 px bez przewijania w poziomie. To naprawa z F-08 (`context/archive/2026-11-21-design-tokens-and-components/plan.md:481-484`).
- `PostRow.vue` trafia do `CLEANED` (`src/styleContract.test.ts`) w miejsce `PostCard.vue`.

#### 3. Lista z grupami dni

**File**: `src/views/FeedView.vue`, `src/postDays.ts` (nowy, z testem)

**Intent**: Wiersze stoją w ramkach dni z nagłówkiem dnia, jak w prototypie (`posts.html:150-152`).

**Contract**:
- `groupByDay(items, now)` grupuje po dniu w Europe/Warsaw w kolejności listy. Nazwa dnia:
  - „Dziś, 20 października”;
  - „Wczoraj, 19 października”;
  - starszy dzień tego roku: „Sobota, 17 października”;
  - inny rok: „Środa, 31 grudnia 2025”.

  Dzień podzielony między dwie strony ma nagłówek na obu.
- Każdy dzień to `section` z `aria-labelledby` na swoim `h2` i lista `ol` wierszy w jednej ramce (`--surface`, `--radius-xl`, krawędź `--line`).
- `PagerNav`, wspólne stany, wiersz statusu, fokus i stany ładowania zostają bez zmian.
- Lista przekazuje wierszowi `to`: na razie `{ name: 'post', params: { id } }`.

#### 4. Testy listy

**File**: `src/views/FeedView.test.ts`, `src/components/PostRow.test.ts` (nowy), `src/postDays.test.ts`, `src/router/*.test.ts`, `src/components/AppShell.test.ts`, `src/components/AppBar.test.ts`, `e2e/feed.spec.ts`, `e2e/states.spec.ts`, `e2e/shell.spec.ts`, `e2e/post.spec.ts`, `e2e/auth.spec.ts`, `visual/targets.ts`, `visual/views.spec.ts`, `visual/__screenshots__/`

**Intent**: Testy znajdują wiersze zamiast kart, a adres listy to `/posts`. Nowe testy pilnują przekierowania i grup dni.

**Contract**:
- **Vitest:**
  - `PostRow`: każdy wariant (tytuł, bez tytułu, 1/2/3+ cele z „i N inne”, flagi, nie inwestycyjny, bez celów, prefiltr, wybrany);
  - `groupByDay`: dziś, wczoraj, inny dzień, inny rok, granica północy w Warszawie przy `FIXED_NOW`;
  - router: `/` z zapytaniem przekierowuje na `/posts` z tym zapytaniem;
  - `AppShell.test.ts` (`:112`, `:206`) i `AppBar.test.ts` (`:12`, `:18`): rekord i nazwa `home` → `posts`, ścieżki `/` → `/posts`. Mock `PostDetailView` (`AppShell.test.ts:21`) zostaje do fazy 2.
- **E2E:**
  - lokatory `article` zmieniają się na regiony dni i odnośniki w elementach listy, a adresy `/` na `/posts`;
  - `auth.spec.ts`: oczekiwane adresy po przekierowaniu: `/login?redirect=/posts` (`:60`), `/login?redirect=/posts?page=1` (`:71`), `/posts?page=1` (`:76`), `/posts` po wejściu zwykłego użytkownika na `/admin` (`:92`);
  - nowy test „stary adres listy z filtrem prowadzi na /posts z tym samym filtrem”;
  - nowy test „wiersze stoją pod nagłówkiem swojego dnia” na `/posts?page=1`. Atrapa liczy daty od 2026-10-12 20:00Z co 17 min (`dev/mock-api.js:448`), więc strona 0 to jeden dzień (12 października), a strona 1 to dwa (12 i 11 października). Atrapa zostaje bez zmian. „Dziś” i „Wczoraj” pilnuje tylko Vitest (`groupByDay`), bo przy `FIXED_NOW` 2026-10-20 nie występują w E2E ani na zrzutach.

  Lokatory po roli i etykiecie, w regionie dnia albo w `main`.
- **F-06:**
  - `view-feed*` przemianowane na `view-posts*` (ścieżka `/posts`); gotowość to liczba wierszy strony i status „Ocenione posty: 120”;
  - `clipToArticle` przechodzi na przycięcie nad N-tym wierszem listy albo nad stronicowaniem. Nad regionem dnia się nie da, bo strona 0 ma jeden region. To nowe pole celu w `visual/targets.ts`, obsłużone w `views.spec.ts`. Zrzut `view-posts` pokaże więc jeden dzień, inaczej niż prototyp, i galeria (faza 4) to zaznacza;
  - wzorce przez `npm run visual:update`; osierocone pliki usunięte.
- **Katalog:** sekcja „Posty” staje się „Wiersz posta” ze wszystkimi wariantami `PostRow`. Jej wzorzec `kitchen-sink-*` powstaje sam.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test`
- Typy i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą, bez osieroconych i brakujących wzorców: `cd szczur-radar-web && npm run visual:docker`
- `design-check app` przechodzi: `node tools/design-check/design-check.mjs app`
- Przy 320 px strona `/posts` nie przewija się w poziomie (`scrollWidth <= 320`). Sprawdza to test E2E przy tej szerokości.
- Każdy nowy test E2E jest czerwony na swoim złamaniu. Agent łamie kod raz na test (przekierowanie gubi zapytanie, grupy łączą dni), uruchamia `npx playwright test e2e/feed.spec.ts -g "<nazwa>"`, sprawdza czerwień i cofa zmianę. Wyniki trafiają do `context/changes/stream-and-inspector/evidence/e2e-breaks.md`.

---

## Faza 2: Panel posta

### Overview

Post otwiera się w modalnym panelu nad listą pod `/posts/:id`. `PostDetailView` i logika przywracania przewijania znikają.

### Changes Required:

#### 1. Wspólny panel boczny

**File**: `src/components/SidePanel.vue` (nowy, z testem)

**Intent**: Jeden komponent panelu wysuwanego z prawej (`ui-design.md`, „Panel boczny”; `prototype.css:1042-1262, 2400-2405`) dla posta teraz i karty celu w S-58. Zawiera tylko mechanikę dialogu, bez treści posta.

**Contract**:
- Props: `labelledby: string` (id nagłówka treści). Sloty: `bar` (metadane i przyciski) i domyślny (przewijana treść). Zdarzenie: `close`. Metoda: `focus()`.
- Rysuje przez `Teleport to="body"` przyciemnienie (`--scrim` 36%, `--z-scrim`) i `section` z `role="dialog"`, `aria-modal="true"` i `aria-labelledby` (`--z-overlay`, `--shadow-overlay`, `--radius-2xl`).
- Szerokość `min(var(--width-panel), calc(100vw - 1.5rem))` od 900 px. Poniżej `inset: 0`, bez promienia i krawędzi.
- Na czas montażu `#app` jest `inert`, a `body` nie przewija się. Oba wracają przy odmontowaniu.
- Tab i Shift+Tab krążą w panelu. Esc emituje `close`. Klik w przyciemnienie też emituje `close`.
- Wysunięcie: `transform` przez `--duration` z `--ease-out` tylko przy `prefers-reduced-motion: no-preference`.
- Trafia do `CLEANED`.

#### 2. Zawartość panelu posta

**File**: `src/components/PostInspector.vue` (nowy, z testem), `src/components/PostAssessment.vue`, `src/labels.ts`

**Intent**: Treść panelu według prototypu `post.html:225-328` bez części S-53. Logika z `PostDetailView` (tytuł zastępczy, oryginał, surowe dane) przechodzi tu.

**Contract**:
- Props: `post: PostItem`. Zdarzenie: `close`.
- **Pasek** (slot `bar` panelu): `postOrigin`, typ, pełna data (`formatDateTime`) i przycisk z samą ikoną `i-x` „Zamknij post”.
- **Artykuł:**
  - `h2` z tytułem albo zastępczym „{Typ} użytkownika {autor}” (funkcja `postTitle(post)` w `labels.ts`, wspólna z nazwą dialogu);
  - autor;
  - odnośnik „Otwórz oryginał” z ikoną `external-link`, `target="_blank"`, `rel="noopener noreferrer"` i `aria-label` „Otwórz oryginał na {host} (otwiera się w nowej karcie)”, tylko dla adresu http(s);
  - treść krojem `--font-display` z zachowanymi łamaniami.
- **Sekcja „Ocena”** (`h3`) z meta „Post inwestycyjny / nie inwestycyjny / niejasny. Oceniono {data}.”, a pod nią `PostAssessment` w układzie „Cel w ocenie”:
  - każdy cel jako `article` z `h4` (cytat celu), rodzajem i stanowiskiem;
  - twierdzenia: rodzaj, treść i cytat w `blockquote` z kreską;
  - cel bez twierdzeń: „Wspomniany, bez twierdzeń.”;
  - „Tezy ogólne” i „Zastrzeżenia” jak dziś, o poziom niżej.

  Post z prefiltra: „za mało treści, bez oceny modelu”.
- **Stopka:**
  - zdanie „Ocena opisuje stanowisko autora posta. Nie jest prognozą ani rekomendacją.”;
  - przycisk „Surowe dane” z ikoną `i-braces` i `aria-expanded`;
  - po rozwinięciu `pre` z `role="region"`, `aria-label="Surowe dane posta"`, `tabindex="0"` i krojem `--font-mono`;
  - leniwe `/raw` z dzisiejszymi stanami ładowania, błędu i ponowienia (`PostDetailView.vue:96-125`).

  `PostAssessment.test.ts` i `PostAssessment` w `CLEANED` zostają.

#### 3. Trasa posta, dane i historia

**File**: `src/router/index.ts`, `src/router/scroll.ts`, `src/components/NotificationBell.vue`, `src/views/FeedView.vue`, `src/views/PostPanel.vue` (nowy, z testem), `src/views/PostDetailView.vue` i `PostDetailView.test.ts` (usunięte)

**Intent**: Wybrany post ma własny adres nad zamontowaną listą. Panel pokazuje post z bieżącej strony od razu, a post spoza niej wczytuje (decyzje planu: nowy wpis historii; dane z listy, inaczej z API).

**Contract**:
- Trasa `post` (`:id`) jest dzieckiem `posts` z `component: PostPanel` i `meta: { section: 'posts' }`. Rekord bez komponentu daje w vue-router 5.3.1 ostrzeżenie `VUE_ROUTER_R0026` przy każdej nawigacji.
- `FeedView` rysuje `<RouterView :items="<wiersze bieżącej strony albo []>" @closed="<fokus na wiersz albo nagłówek>" />`. Atrybuty przechodzą do komponentu trasy. `PostPanel` (`SidePanel` + `PostInspector` + dane i stany poniżej) ma własny test, a `FeedView` nie przejmuje logiki danych posta.
- Wiersz dostaje `to = { name: 'post', params: { id }, query: route.query }` i `selected`, gdy `route.params.id === post.id`.
- **Dane panelu** (w `PostPanel`, z propa `items` albo z API). Źródło wybiera się raz, przy otwarciu i przy zmianie `id`. Jeśli lista się wtedy jeszcze wczytuje (`items` puste, np. po odświeżeniu `/posts/:id`), panel pobiera post sam, a listę, która przyjdzie później, ignoruje:
  - post z `items` (wiersze bieżącej strony) bez żądania;
  - inaczej `fetchPost(id)` ze stanami w panelu:
    - ładowanie: `LoadingState` `lines`, panel `aria-busy`;
    - błąd: `ErrorState` „Nie udało się wczytać posta” z ponowieniem;
    - 404/400: `EmptyState` „Nie ma takiego posta” z „Post nie istnieje albo nie został jeszcze oceniony.”.

    Nazwa dialogu w tych stanach to nagłówek stanu. Odpowiedź starszego żądania jest odrzucana (licznik jak w `PostDetailView`).
- **Zamknięcie** (przycisk, Esc, przyciemnienie): `router.back()`, gdy `history.state.back` rozwiązuje się do `posts` z tym samym zapytaniem. Inaczej `router.replace({ name: 'posts', query: route.query })`.
- **Fokus:**
  - po otwarciu na nagłówek posta w panelu (albo stanu);
  - po zamknięciu na odnośnik wiersza tego posta, a bez niego na nagłówek „Posty”;
  - „wstecz” przeglądarki zamyka panel tak samo.
- **`scroll.ts`:** znika oczekiwanie na wczytanie listy i oddawanie fokusu (`reportFeedLoaded`, `nextFeedLoad`, `RESTORE_TIMEOUT_MS`, `focusLinkTo`), razem z wywołaniami w `FeedView` i testami. Zostaje: „ta sama ścieżka albo oba końce w ekranie postów → brak przewijania; inna ścieżka → góra”. Warunek „ekran postów” (`name` to `posts` albo `post`) to jedna funkcja w `src/router/`, której używają `scroll.ts` i dzwonek.
- **Dzwonek** (`src/components/NotificationBell.vue:79-81`, test `NotificationBell.test.ts`): po nawigacji w obrębie ekranu postów (otwarcie i zamknięcie panelu, w obu kierunkach) nie pobiera powiadomień ponownie. Każda inna nawigacja dalej je pobiera.
- `PostDetailView.vue` i jego test znikają, a z nimi mock `PostDetailView` w `AppShell.test.ts:21`. Z `CLEANED` znika `PostDetailView.vue`, a dochodzą `SidePanel.vue`, `PostInspector.vue` i `PostPanel.vue`.

#### 4. Testy panelu

**File**: `e2e/post.spec.ts`, `e2e/session-guard.spec.ts`, `src/views/FeedView.test.ts`, `src/views/PostPanel.test.ts` (nowy), `src/components/AppShell.test.ts`, `src/router/scroll.test.ts`, `visual/targets.ts`, `visual/views.spec.ts`, `src/views/KitchenSinkView.vue`

**Intent**: Przepływy panelu z kontraktu E2E (`ui-design.md`, wiersz `post`) bez części S-53, na prawdziwej przeglądarce. Modalność i „wstecz” sprawdza tylko przeglądarka (ryzyko w roadmapie).

**Contract**:
- **E2E `post.spec.ts`** (`dialog` w `page`, reszta w `main`):
  - klik wiersza: dialog o nazwie „Wpis użytkownika uzytkownik0”, adres `/posts/<id>` z zapytaniem listy, fokus w dialogu, wiersz z `aria-current="true"`, nagłówki „Ocena” i „CDR”;
  - „Zamknij post”: dialog znika, fokus na wierszu, adres listy z tym samym filtrem i stroną (wejście z `?page=1&direction=bullish`);
  - Esc: to samo;
  - Tab z ostatniego elementu panelu wraca na pierwszy;
  - „Surowe dane”: `aria-expanded="true"` i JSON źródła w regionie „Surowe dane posta”;
  - `MODE: raw-loading` i `raw-error`: stany surowych danych;
  - link do posta spoza strony: panel wczytuje post;
  - nieznany id: „Nie ma takiego posta”;
  - przy 375 px panel ma szerokość okna, a „wstecz” przeglądarki zamyka go i wraca do listy.
- **E2E `session-guard.spec.ts`:** po logowaniu adres posta i dialog o nazwie tytułu posta nad listą.
- **Vitest:** `SidePanel` (Esc, krążenie Tab, `inert` i odblokowanie przy odmontowaniu, przyciemnienie), `PostInspector` (tytuł zastępczy, oryginał tylko http(s), stany surowych danych), `PostPanel` (dane z `items` bez żądania, `fetchPost` spoza strony, odrzucona stara odpowiedź, zamknięcie `back` vs `replace`), `FeedView` (przekazanie `items` do trasy dziecka, fokus po zamknięciu na wiersz albo nagłówek), `scroll.ts` (brak przewijania w ekranie postów).
- **F-06:** `view-post` na `/posts/<POST_0>` z gotowością na dialog i jego nagłówki. Zrzut kadru okna (projekt, „Ekrany”), więc nowe pole celu „tylko okno” w `views.spec.ts`. Wzorce we wszystkich czterech projektach, axe na otwartym dialogu.
- **Katalog:** sekcja „Ocena posta” staje się „Panel boczny” z `PostInspector` w statycznej ramce panelu (bez dialogu i warstw), w wariantach: tytuł, bez tytułu, prefiltr, cel bez twierdzeń, tezy ogólne.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test`
- Typy i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy E2E przechodzą, w tym `post.spec.ts` i `session-guard.spec.ts`: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą z nowym `view-post`: `cd szczur-radar-web && npm run visual:docker`
- W `src/` nie ma odwołań do `PostDetailView` ani `reportFeedLoaded`: `! grep -rn "PostDetailView\|reportFeedLoaded" szczur-radar-web/src szczur-radar-web/e2e szczur-radar-web/visual`
- Każdy nowy test E2E panelu jest czerwony na swoim złamaniu. Przykłady złamań:
  - odnośnik wiersza bez zapytania;
  - zamknięcie bez powrotu fokusu;
  - brak `inert`;
  - Esc nie zamyka;
  - `scrollBehavior` przewija przy otwarciu.

  Wyniki dochodzą do `evidence/e2e-breaks.md`.

---

## Faza 3: Panel „Filtry”

### Overview

Pola filtra przechodzą pod przycisk „Filtry”, a włączone filtry stoją nad listą jako znaczniki.

### Changes Required:

#### 1. Formularz z przyciskiem i panelem

**File**: `src/components/FeedFilter.vue`, `FeedFilter.test.ts`

**Intent**: Filtr według projektu („Filtry”, `ui-design.md`; `posts-filters.html:145-168`, `components.html:577-595`) z polami tego wycinka. Pole „Szukaj w postach”, „Od”/„Do” i „Kolejność” dodadzą S-56, S-55 i S-54.

**Contract**:
- Formularz `role="search"`, `aria-label="Filtr postów"` z przyciskiem `type="button"` z ikoną `i-list-filter`:
  - nazwa „Filtry”, a przy włączonych filtrach „Filtry: N” (N to liczba włączonych kluczy);
  - `aria-expanded`.
- Zaraz po przycisku w dokumencie stoi panel `role="group"`, `aria-label="Filtry"` z nagłówkiem „Filtry” i przyciskiem z samą ikoną „Zamknij filtry”. Pola: „Spółka lub temat” (`type="search"`, `maxlength` jak dziś), „Rodzaj celu” i „Stanowisko”. Przyciski: „Wyczyść filtry” i „Filtruj” (główny, `type="submit"`).
- Pola są szkicem do „Filtruj” albo Enter, jak dziś. Otwarcie panelu ustawia je na zastosowany filtr.
- „Filtruj” i „Wyczyść filtry” zamykają panel. Esc, „Zamknij filtry” i klik poza formularzem zamykają go bez zastosowania (wzorzec `NotificationMenu.vue:78-95`). Fokus wraca na przycisk „Filtry”.
- Zdarzenia `apply` i `clear` bez zmian. Panel nie jest modalny.
- Poniżej 900 px przycisk zajmuje swój wiersz, a panel stoi pod nim na całą szerokość.

#### 2. Znaczniki włączonych filtrów

**File**: `src/components/FilterChips.vue` (nowy, z testem), `src/views/FeedView.vue`

**Intent**: Włączone filtry widać nad listą, a każdy da się zdjąć jednym przyciskiem.

**Contract**:
- Props: `filter: PostFilter`. Zdarzenia: `remove: [key: keyof PostFilter]` i `clear`.
- Lista `ul` z `aria-label="Włączone filtry"`, a w niej przyciski z ikoną `i-x`:
  - tekst „Cel: {q}”, „Rodzaj: {label}”, „Stanowisko: {label}”;
  - nazwa „Usuń filtr: Cel: {q}” itd.;
  - na końcu przycisk tekstowy „Wyczyść filtry”.

  Bez filtrów komponent nic nie rysuje.
- `FeedView` stawia go między formularzem a wierszem statusu. `remove` robi `router.push` z filtrem bez tego klucza (strona 0). Fokus zostaje w wierszu znaczników: przechodzi na następny znacznik, a po ostatnim na przycisk „Filtry”.
- Pusty wynik filtra (`state-empty`, „Brak postów dla tych filtrów”) pokazuje znaczniki nad sobą, jak w kontrakcie E2E.
- `FilterChips.vue` trafia do `CLEANED`.

#### 3. Testy filtrów

**File**: `e2e/feed.spec.ts`, `e2e/states.spec.ts`, `visual/targets.ts`, `src/views/KitchenSinkView.vue`, `src/views/FeedView.test.ts`

**Intent**: Przepływy `posts-filters` tego wycinka z kontraktu E2E.

**Contract**:
- **E2E:**
  - „filtr po celu i stanowisku trafia do adresu i zawęża listę”: otwiera „Filtry”, wpisuje cel, wybiera stanowisko, „Filtruj”; parametry w adresie, znaczniki nad listą, „Filtry: 2”, lista zawężona;
  - „Usuń filtr: …”: znika jeden klucz z adresu i jeden znacznik;
  - Esc: panel zamknięty, fokus na „Filtry”;
  - pusty wynik: znaczniki i „Wyczyść filtry” wracają do pełnej listy i adresu bez filtrów. Przycisk „Wyczyść filtry” jest w liście „Włączone filtry”, w pustym stanie i w otwartym panelu, więc test szuka go w liście „Włączone filtry” albo w regionie pustego stanu, nigdy w samym `main`.
- **Vitest:** `FeedFilter` (liczba w nazwie, szkic resetowany przy otwarciu, zamykanie i fokus), `FilterChips` (teksty, nazwy, `remove`, brak przy braku filtra), `FeedView` („Wyczyść filtry” ze znaczników i z pustego stanu czyści ten sam filtr).
- **F-06:**
  - nowy cel `view-posts-filters`: otwarty panel z polami i znacznikami, z filtrem w adresie;
  - `view-posts-empty` z gotowością na znaczniki;
  - `STATES`: fokus znacznika i wskazanie przycisku „Filtry” przy 375 px.
- **Katalog:** sekcja „Filtr” staje się „Filtry”: zamknięty, otwarty, z liczbą, znaczniki.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test`
- Typy i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą z `view-posts-filters`: `cd szczur-radar-web && npm run visual:docker`
- Każdy nowy test E2E filtrów jest czerwony na swoim złamaniu. Przykłady złamań:
  - znacznik zdejmuje wszystkie filtry;
  - Esc nie oddaje fokusu;
  - liczba w przycisku liczy puste klucze.

  Wyniki dochodzą do `evidence/e2e-breaks.md`.

---

## Faza 4: Domknięcie i weryfikacja

### Overview

Dokumentacja mówi o nowym ekranie. Agent sprawdza go w PR env na prawdziwych danych, a właściciel ocenia wygląd na zrzutach.

### Changes Required:

#### 1. Dokumentacja

**File**: `REPOSITORY_GUIDELINES.md`, `szczur-radar-web/dev/mock-api.js` (tylko nagłówek), `context/foundation/ui-design.md` (sekcja „Istniejące testy, które zmiana łamie”)

**Intent**: Następny agent znajduje nowe komponenty i trasy zamiast starych.

**Contract**:
- `REPOSITORY_GUIDELINES.md`, sekcja UI:
  - `PostCard` → `PostRow`;
  - nowe `SidePanel`, `PostInspector`, `FilterChips` i komponent trasy `PostPanel`;
  - `FeedFilter` jako przycisk z panelem;
  - w strukturze modułu `posts` z dzieckiem `post` zamiast `home`/`post`;
  - wpisy o `router/scroll.ts` i stronie posta poprawione.
- W nagłówku atrapy „the feed (/)” i „a post (/posts/:id)” przechodzą na `/posts` i `/posts/:id`.
- `ui-design.md`, tabela „Istniejące testy, które zmiana łamie”:
  - wiersz testów stronicowania i stanów `feed.spec.ts` przypisuje zmianę adresu listy z `/` na `/posts` do S-59. Plan robi ją w S-51, więc wiersz dostaje S-51 i zdanie „`/` przekierowuje na `/posts` z zapytaniem, aż S-59 da tam przegląd dnia”;
  - opis PR wymienia tę edycję do akceptacji właściciela (zasada „Design source”).
- Inne zmiany `ui-design.md` tylko wtedy, gdy implementacja świadomie odchodzi od nazwy z kontraktu E2E. Takie odejście też wymaga akceptacji właściciela.

#### 2. Weryfikacja w PR env i galeria

**File**: opis PR, galeria jako Artifact (poza repozytorium), `context/changes/stream-and-inspector/evidence/pr-env/README.md` (nowy, tylko wyniki pomiarów)

**Intent**: Lista i panel działają na prawdziwym API i prawdziwych postach. Właściciel ocenia wygląd obok prototypów (pamięć `ui-decisions-by-pictures`).

**Contract**:
- Galeria zestawia `view-posts`, `view-post`, `view-posts-filters`, `view-posts-empty` i wiersz przy 320 px z `ui-design/screens/posts-*`, `post-*` i `posts-filters-*`, w jasnym i ciemnym motywie, przy 375 i 1280 px.
- Opis PR wymienia zmienione grupy wzorców: przemianowane i nowe `view-posts*`, nowe `view-post`, sekcje katalogu „Wiersz posta”, „Panel boczny” i „Filtry”, przesunięte sekcje i stany katalogu oraz `view-*` bez zmian. Zawiera `Refs #183`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test`
- Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą: `cd szczur-radar-web && npm run visual:docker`
- CI na PR jest zielone, w tym `web-e2e` (krok wizualny) i `web-container-smoke`: `gh pr checks`
- W PR env lista i panel działają na prawdziwym API. Po wdrożeniu sprawdzanego commita agent wykonuje kontrolę w Chromium przez `playwright-cli`:
  1. Zakłada w PR env jednorazowe konto `--role admin`, aktywuje je i uzyskuje ciasteczko sesji według procedury z `lessons.md` („W PR env agent sam zakłada jednorazowe konto admina…”). Link, token, hasło i ciasteczko są tylko w zmiennych powłoki i w pliku w scratchpadzie, usuniętym po kontroli.
  2. Otwiera `/` i trafia na `/posts`. Lista wczytuje się bez „Wczytywanie…”, a każdy wiersz stoi w regionie dnia.
  3. Klika pierwszy wiersz. Dialog ma nazwę równą tytułowi albo „… użytkownika …”, adres to `/posts/<id>`, a odnośnik „Otwórz oryginał” prowadzi na wykop.pl albo reddit.com. „Surowe dane” pokazują JSON.
  4. Esc zamyka dialog, a fokus jest na wierszu.
  5. Otwiera adres posta z drugiej strony listy i potwierdza, że panel go wczytał.
  6. Przy 375 px dialog ma szerokość okna, „wstecz” go zamyka, a `scrollWidth <= 375` na `/posts`.

  Wyniki pomiarów (bez sekretów) trafiają do `evidence/pr-env/README.md`.

#### Manual Verification:

- Właściciel akceptuje wygląd listy, panelu posta i panelu filtrów w galerii zrzutów obok prototypów (jasny i ciemny, 375 i 1280 px, wiersz przy 320 px)

---

## Testing Strategy

### Unit Tests:

- `PostRow`: każdy wariant wiersza, „i N inne”, flagi, nie inwestycyjny, bez celów, prefiltr, `aria-current`.
- `groupByDay`: nazwy dni, inny rok, północ w Warszawie, kolejność listy.
- `SidePanel`: Esc, krążenie Tab, `inert` i blokada przewijania z odblokowaniem, przyciemnienie.
- `PostInspector`: tytuł zastępczy, oryginał tylko http(s), stany surowych danych, meta oceny, cel bez twierdzeń.
- `FeedFilter` i `FilterChips`: liczba w przycisku, szkic, zamykanie, fokus, znaczniki i ich nazwy.
- `PostPanel`: dane z listy i z API, stara odpowiedź odrzucona, zamknięcie `back`/`replace`.
- `FeedView`: grupy, `items` dla trasy dziecka, fokus po zamknięciu, zdjęcie znacznika.
- Router i `scroll.ts`: przekierowanie `/` z zapytaniem, brak przewijania w ekranie postów.

### Integration Tests:

- **E2E:**
  - `feed.spec.ts`: lista, dni, przekierowanie, filtry, znaczniki, 320 px;
  - `post.spec.ts`: panel, fokus, Esc, „wstecz”, surowe dane, post spoza strony, nieznany id, 375 px;
  - `session-guard.spec.ts`: dialog po logowaniu;
  - `states.spec.ts`: stany pod `/posts`.

  Każdy nowy test złamany raz.
- **F-06:** `view-posts*`, `view-post` (kadr okna), `view-posts-filters`, sekcje katalogu i stany przy 375 px, axe na każdym.

### Manual Testing Steps:

1. Właściciel ogląda galerię zrzutów listy, panelu i filtrów obok prototypów i akceptuje wygląd (faza 4).

## Performance Considerations

- Panel posta z bieżącej strony nie wysyła żądania. Post spoza strony to jedno `GET /api/posts/:id`, a `/raw` dalej czyta się dopiero po „Surowe dane”.
- Otwarcie i zamknięcie panelu nie pobiera ponownie powiadomień admina. Dzwonek pomija nawigację w obrębie ekranu postów.
- Lista nie przeładowuje się przy otwarciu ani zamknięciu panelu, bo zapytanie się nie zmienia.
- Animacja zmienia tylko `transform`.

## Migration Notes

Brak zmian danych i API. Stare adresy działają: `/?…` przekierowuje na `/posts?…`, a `/posts/:id` otwiera ten sam post, teraz w panelu nad listą. Wdrożenie web jest niezależne od API.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-51), issue #183
- Projekt: `context/foundation/ui-design.md`:
  - „Mapa ekranów”;
  - „Szeroki i wąski ekran”;
  - „Sterowanie” → „Filtry”;
  - „Treść” → „Wiersz posta”, „Panel boczny”, „Cel w ocenie”;
  - „Kontrakt dla testów E2E” (`posts`, `post`, `posts-filters`, `state-empty`, „Istniejące testy, które zmiana łamie”).
- Prototypy: `ui-design/prototypes/screens/posts.html`, `post.html`, `posts-filters.html`, `components.html` (`#c-wiersz-posta`, `#c-panel`, `#c-karta-celu`, `#c-filtry`), style `prototypes/prototype.css:925-1262, 2309-2464`.
- Wcześniejsze decyzje:
  - `context/archive/2026-11-22-app-shell-navigation/plan-brief.md:31` (`/posts` w S-51);
  - `context/archive/2026-11-23-shared-view-states/plan.md:80-91`;
  - `context/archive/2026-11-21-design-tokens-and-components/plan.md:97-98, 481-484`.
- Kod: `szczur-radar-web/src/views/FeedView.vue`, `src/views/PostDetailView.vue`, `src/router/index.ts`, `src/router/scroll.ts`, `src/components/NotificationMenu.vue:78-95`, `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:87-118`.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Adres i zwarta lista

#### Automated

- [x] 1.1 Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test` — 2d95550
- [x] 1.2 Typy i build przechodzą: `cd szczur-radar-web && npm run build` — 2d95550
- [x] 1.3 Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e` — 2d95550
- [x] 1.4 Testy wizualne i axe przechodzą, bez osieroconych i brakujących wzorców: `cd szczur-radar-web && npm run visual:docker` — 2d95550
- [x] 1.5 `design-check app` przechodzi: `node tools/design-check/design-check.mjs app` — 2d95550
- [x] 1.6 Przy 320 px strona `/posts` nie przewija się w poziomie (test E2E) — 2d95550
- [x] 1.7 Każdy nowy test E2E jest czerwony na swoim złamaniu (`evidence/e2e-breaks.md`) — 2d95550

### Phase 2: Panel posta

#### Automated

- [x] 2.1 Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test` — 02028e8
- [x] 2.2 Typy i build przechodzą: `cd szczur-radar-web && npm run build` — 02028e8
- [x] 2.3 Testy E2E przechodzą, w tym `post.spec.ts` i `session-guard.spec.ts`: `cd szczur-radar-web && npm run e2e` — 02028e8
- [x] 2.4 Testy wizualne i axe przechodzą z nowym `view-post`: `cd szczur-radar-web && npm run visual:docker` — 02028e8
- [x] 2.5 Brak odwołań do `PostDetailView` i `reportFeedLoaded` — 02028e8
- [x] 2.6 Każdy nowy test E2E panelu jest czerwony na swoim złamaniu (`evidence/e2e-breaks.md`) — 02028e8

### Phase 3: Panel „Filtry”

#### Automated

- [x] 3.1 Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test` — cde219e
- [x] 3.2 Typy i build przechodzą: `cd szczur-radar-web && npm run build` — cde219e
- [x] 3.3 Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e` — cde219e
- [x] 3.4 Testy wizualne i axe przechodzą z `view-posts-filters`: `cd szczur-radar-web && npm run visual:docker` — cde219e
- [x] 3.5 Każdy nowy test E2E filtrów jest czerwony na swoim złamaniu (`evidence/e2e-breaks.md`) — cde219e

### Phase 4: Domknięcie i weryfikacja

#### Automated

- [ ] 4.1 Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test`
- [ ] 4.2 Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- [ ] 4.3 Testy wizualne i axe przechodzą: `cd szczur-radar-web && npm run visual:docker`
- [ ] 4.4 CI na PR jest zielone, w tym `web-e2e` i `web-container-smoke`: `gh pr checks`
- [ ] 4.5 W PR env lista i panel działają na prawdziwym API (kontrola agenta przez `playwright-cli`, `evidence/pr-env/README.md`)

#### Manual

- [ ] 4.6 Właściciel akceptuje wygląd listy, panelu posta i panelu filtrów w galerii zrzutów obok prototypów
