# S-47: powłoka aplikacji z nawigacją u góry — plan implementacji

## Overview

Zalogowane widoki dostają jedną stałą powłokę: pasek aplikacji u góry i treść w jednej kolumnie pod nim. W pasku są znak i nazwa aplikacji, nawigacja „Główna” według roli („Posty”, a dla admina „Panel admina”), data ostatniej udanej aktualizacji danych per źródło z dniem i godziną (FR-030, doprecyzowanie z 2026-11-21), dzwonek admina, „Ciemny motyw”, e-mail i „Wyloguj”. Widoki przestają stawiać własny `AppHeader` i własny `<main>`. Tytuł strony dostają od wspólnego komponentu nagłówka strony. Wygląd i nazwy pochodzą z projektu F-07 (`context/foundation/ui-design.md`, sekcje „Nawigacja”, „Powłoka i nawigacja”, „Kontrakt dla testów E2E”), a wycinek wdraża je na tokenach i komponentach F-08. API się nie zmienia.

## Current State Analysis

- `szczur-radar-web/src/App.vue:5-8` renderuje tylko `<IconSprite />` i `<RouterView />`. Powłoki nie ma.
- Cztery zalogowane widoki same stawiają `<AppHeader />` i własny `<main>` z własną szerokością:
  - `FeedView.vue:165-166`: 48/54rem;
  - `PostDetailView.vue:138-139`: 48/54rem;
  - `AdminView.vue:412-413`: 56/63rem;
  - `RunDetailView.vue:154-155`: 48/54rem.
- Logowanie, aktywacja i `/kitchen-sink` nie mają paska i to zostaje.
- `AppHeader.vue:33-47` ma `nav` bez nazwy z odnośnikami „Posty” (`home`) i „Panel admina” (tylko admin). Bieżąca pozycja działa tylko przy dokładnym adresie (`.router-link-exact-active`, `:72`), więc na `/admin/runs/:id` „Panel admina” nie jest zaznaczony. Prototypy zaznaczają sekcję: „Posty” na `post.html`, „Panel admina” na `run.html` i `admin-quality.html`.
- Data danych stoi tylko nad listą w `FeedView.vue:169-181` („Dane zaktualizowane: Wykop … · Reddit …”). Widok czyta ją raz przy wejściu (`:62-67`), a `load()` czeka na nią przed `reportFeedLoaded('ok')` (`:70-91`), bo etykieta nad listą zmienia wysokość strony, do której wraca przywracanie przewijania (`router/scroll.ts:46-54`).
- `GET /api/freshness` (`src/api/freshness.ts:4-19`, backend `pipeline/api/FreshnessController`) daje per źródło pełną chwilę `lastUpdatedAt` i każdej zalogowanej roli. Reguła „tylko ostatni udany przebieg” żyje w `PipelineRunRepository.findLastUpdates()`. Zmian w backendzie i w kontrakcie `src/contract/freshness.json` nie trzeba.
- `formatDateTime` (`src/labels.ts:102-111`) daje już dzień z godziną w czasie warszawskim.
- `notificationsStore.ts:1-4` i `NotificationBell.vue:2-7` uzasadniają store tym, że każdy widok montuje pasek na nowo. W stałej powłoce ten powód znika, ale store nadal czyści listę przy końcu sesji (`auth/session.ts`), a jego testy pokrywają odświeżanie.
- Atrapa ma już tryb `FRESHNESS` (`date|apart|wykop|none|error`, `dev/mock-api.js:146-156, 520-527, 735-738`). Strażnik `mockApi.test.ts` nie ma dla niego żadnego wiersza w `ROWS`.
- Testy i bramki zależne od dzisiejszego paska:
  - `AppHeader.test.ts:52-60`: dokładna lista przycisków;
  - `PostDetailView.test.ts:157`: „Wyloguj” w widoku;
  - blok świeżości w `FeedView.test.ts:337-420` i testy raportu przewijania `:256`, `:274`;
  - `visual/targets.ts:92`: „Dane zaktualizowane:” w `main`;
  - 16 wzorców `view-{feed,post,admin,run}` w czterech projektach.
- E2E szukają paska przez `banner` i nazwy, które zostają: „Wyloguj”, „Ciemny motyw”, „Panel admina”, „Powiadomienia, N …”, e-mail. Dotyczy to `auth.spec.ts:21,55`, `theme.spec.ts:22,37,70`, `session-guard.spec.ts:10` i `admin.spec.ts:68`.

## Desired End State

- Każda zalogowana strona ma dokładnie jeden `banner`, jedną `navigation` „Główna”, jeden `main` i w nim jeden nagłówek poziomu 1.
- Nawigacja według roli:
  - użytkownik widzi tylko „Posty”, a admin „Posty” i „Panel admina”;
  - pozycja sekcji bieżącej strony ma `aria-current="page"`: „Posty” na `/` i `/posts/:id`, „Panel admina” na `/admin` i `/admin/runs/:id`.
- Pasek pokazuje „Dane:” i po jednym wierszu na źródło, np. „Wykop 13.10.2026, 05:12”, z elementem `time`:
  - bez żadnej aktualizacji: „Dane nie były jeszcze aktualizowane.”;
  - przy błędzie odczytu albo odpowiedzi bez `sources` etykiety nie ma, a strona działa;
  - powłoka czyta datę przy starcie, potem po każdej zmianie strony i po powrocie do karty, ale pomija odczyt, jeśli poprzedni jest młodszy niż 60 s.
- Poniżej 900 px przyciski są w pierwszym wierszu paska, nawigacja w drugim, a data danych w trzecim i zostaje widoczna. E-mail znika z paska. Od 320 px strona nie przewija się w poziomie.
- Kolumna treści ma do `--width-content` (70rem) z marginesami z prototypu. Obecne widoki zachowują w niej swoją dzisiejszą szerokość treści, aż przebudują je S-50 i S-51.
- „Wyloguj” kończy sesję i prowadzi do `/login`. Błąd wylogowania pokazuje komunikat pod paskiem, a sesja zostaje.
- `AppHeader.vue` nie istnieje. Pasek to `AppBar` (prezentacja, w kitchen sink) i `AppShell` (dane, trasa-rodzic). Nagłówek strony to `PageHeader`.
- Weryfikacja:
  - zielone `npm test`, `npm run build`, `npm run e2e`, `npm run visual:docker` i CI na PR;
  - nowy `e2e/shell.spec.ts` przechodzi;
  - agent sprawdza pasek w PR env na prawdziwych danych;
  - właściciel akceptuje zrzuty obok prototypów.

### Key Discoveries:

- Trasa-rodzic z komponentem powłoki nie montuje się na nowo przy przejściu między dziećmi. `to.meta` łączy `meta` wszystkich dopasowanych rekordów, więc strażnik `meta.admin` w `router/index.ts:60-72` działa bez zmian. Nazwy tras `home`, `post`, `admin` i `admin-run` zostają, więc `scroll.ts:47` (`from.name === 'post' && to.name === 'home'`) i wszystkie `router.push({ name })` też.
- `focusLinkTo` w `scroll.ts:41-43` szuka `a[href=…]` w całym dokumencie. Odnośniki paska mają adresy `/` i `/admin`, a wiersze listy `/posts/<id>`, więc się nie zderzają.
- Kitchen sink nie ma sesji ani API (`KitchenSinkView.vue:1-5`). Pasek trzeba więc podzielić jak dzwonek (`NotificationMenu` prezentacja + `NotificationBell` dane, `REPOSITORY_GUIDELINES.md`, „UI”).
- Widoki same przenoszą fokus na swój `h1` po wczytaniu danych (`PostDetailView.vue:68`, `RunDetailView.vue:110`, `FeedView` przy zmianie strony). Termin zależy od danych, więc `PageHeader` daje tylko `focus()`, a termin zostaje w widokach.
- Sekcje kitchen sink to nagłówki 2 w `main`, a ich wzorce F-06 powstają same z nazwy (`visual/targets.ts:164-203`). Nowa sekcja to więc dwa nowe wzorce 375 px.

## What We're NOT Doing

- Adres `/posts` dla listy: lista zostaje pod `/` (`home`) do S-51 (decyzja planu). Przegląd dnia pod `/` przychodzi w S-59.
- Pozycje „Przegląd” i „Cele”: dochodzą z ekranami S-59 i S-57. Nie ma pozycji wyłączonych ani prowadzących donikąd (`ui-design.md`, „Ekran startowy”).
- Przebudowa treści widoków: zwarta lista, panel posta, części panelu admina, tabele, przełącznik zakresu i wybór dnia w nagłówku strony to S-50–S-59. `PageHeader` dostaje miejsce na narzędzia, ale żaden widok go jeszcze nie wypełnia.
- Wspólne stany ładowania, pustej listy i błędu: S-48.
- Logowanie i aktywacja: S-49. Zostają bez paska i bez zmian.
- Zmiana szerokości treści obecnych widoków do 70rem (decyzja planu: widoki zachowują swoją szerokość).
- Zmiany w API, w kontrakcie `freshness.json` i w formacie dat w `src/labels.ts`. Dzień jednocyfrowy zostaje taki, jaki daje dziś `formatDateTime`.
- Przebudowa `notificationsStore`: zostaje, zmieniają się tylko komentarze z jego uzasadnieniem.
- Nowy tryb atrapy: wystarczy istniejący `FRESHNESS`.

## Implementation Approach

1. **Komponenty.** Najpierw powstają komponenty prezentacyjne i trafiają do katalogu (`AppBar`, `PageHeader`). Dzięki temu wygląd paska jest ustalony i ma wzorce F-06, zanim cokolwiek zostanie przepięte.
2. **Przepięcie.** Potem powłoka wchodzi do routera jako trasa-rodzic. `AppShell` łączy sesję, router i `/api/freshness` z `AppBar`, a widoki tracą własny pasek i `main`.
3. **Testy i weryfikacja.** Na końcu E2E pilnuje przepływów powłoki z kontraktu projektu, a agent i właściciel sprawdzają wynik w PR env i na zrzutach.

Każda faza kończy się zielonymi bramkami i commitem.

## Critical Implementation Details

- **Przewijanie i data danych.** W stałej powłoce data danych jest już wczytana, gdy użytkownik wraca z posta do listy, bo pasek nie montuje się na nowo. Oczekiwanie na `freshnessSettled` w `FeedView.load()` znika razem z etykietą. Ponowny odczyt przy nawigacji nie może zdejmować poprzedniej wartości na czas żądania, bo zmieniłoby to wysokość paska poniżej 900 px w chwili przywracania pozycji. Stara wartość zostaje do nadejścia nowej.
- **Gotowość zrzutów.** Każdy zalogowany widok w `visual/targets.ts` musi czekać na datę danych w `banner`, tak jak dziś `bellLoaded()` czeka na dzwonek. Inaczej zrzut ściga się z odpowiedzią `/api/freshness`, a wzorce przestają być powtarzalne.
- **Zaznaczenie sekcji.** `RouterLink` sam ustawia `aria-current="page"` tylko przy dokładnym adresie. Dla sekcji `aria-current` trzeba wyznaczyć z `meta.section` trasy i nie może go nadpisać `RouterLink`, np. przez slot `custom` albo przez `ariaCurrentValue` z własnym atrybutem. Kontrakt: na każdej stronie powłoki dokładnie jeden odnośnik nawigacji ma `aria-current="page"`.

## Faza 1: Komponenty paska i nagłówka strony

### Overview

Powstają dwa komponenty prezentacyjne według prototypu (`ui-design/prototypes/prototype.css:657-810, 2309-2375`; `screens/posts.html:108-131`). Trafiają do kitchen sink, do listy `CLEANED` i do testów jednostkowych. Aplikacja jeszcze ich nie używa.

### Changes Required:

#### 1. Pasek aplikacji (prezentacja)

**File**: `szczur-radar-web/src/components/AppBar.vue` (nowy), `szczur-radar-web/src/components/AppBar.test.ts` (nowy)

**Intent**: Pasek z projektu jako czysty komponent z propsami, żeby dało się go pokazać w kitchen sink bez sesji i API.

**Contract**:
- Propsy:
  - `items: { label: string; to: RouteLocationRaw; current: boolean }[]`;
  - `freshness: Freshness | null`: `null` znaczy brak etykiety, `sources: []` znaczy „Dane nie były jeszcze aktualizowane.”;
  - `email: string | null`;
  - `loggingOut: boolean`.
- Slot `notifications` (miejsce dzwonka) i zdarzenie `logout`.
- Znaczniki:
  - `<header>` (rola `banner`);
  - znak marki (`AppIcon brand-mark`) i „Szczur Radar” jako odnośnik do `home`;
  - `<nav aria-label="Główna">` z odnośnikami, przy czym bieżący ma `aria-current="page"`;
  - „Dane:” i po jednym wierszu na źródło: `label('source', …)` i `<time :datetime>` z `formatDateTime`, bez roli `status`;
  - slot dzwonka, `ThemeToggle`, e-mail, `AppButton` „Wyloguj” z `busy`.
- Układ z prototypu:
  - od 900 px wszystko w jednym wierszu, sesja z prawej;
  - poniżej 900 px przyciski w pierwszym wierszu, nawigacja w drugim, data w trzecim, a e-mail ukryty (`max-width: 899px`).
- Pierścienie radaru za znakiem są ozdobą bez treści.
- Lista dzwonka ustawia się według paska (`NotificationMenu`: najbliższy przodek z `position`, wyrównanie do jego prawej krawędzi). Dlatego `position` ma `header`, a żaden element między nim a dzwonkiem go nie ma. Reguła prototypu `.app-bar > * { position: relative }` nie przechodzi, a pierścienie leżą pod treścią inaczej (np. `z-index: -1` w kontekście paska).
- Wyłącznie tokeny.

#### 2. Nagłówek strony

**File**: `szczur-radar-web/src/components/PageHeader.vue` (nowy), `szczur-radar-web/src/components/PageHeader.test.ts` (nowy)

**Intent**: Jedno miejsce na tytuł strony (`h1`, jeden na stronie), opcjonalny podtytuł i narzędzia całego widoku z prawej, według komponentu „Nagłówek strony” z projektu.

**Contract**:
- Propsy: `title: string`, `headingId?: string` (dla `aria-labelledby` sekcji widoku) i `subtitle?: string`.
- Slot `status` zaraz za `h1`, w tym samym wierszu (stan przy tytule, jak `.title-row` w `prototypes/screens/run.html:138-141`), i slot `tools`.
- `h1` ma `tabindex="-1"`, a `defineExpose({ focus })` przenosi na niego fokus.

#### 3. Kitchen sink, lista CLEANED i wzorce F-06

**File**:
- `szczur-radar-web/src/views/KitchenSinkView.vue`
- `szczur-radar-web/src/styleContract.test.ts`
- `szczur-radar-web/visual/targets.ts`
- `szczur-radar-web/visual/__screenshots__/`

**Intent**: Każdy nowy komponent jest w katalogu we wszystkich wariantach i pod skanem literałów (`REPOSITORY_GUIDELINES.md`, „UI”).

**Contract**:
- Nowa sekcja „Pasek aplikacji” pokazuje cztery warianty:
  - admin z dzwonkiem (`NotificationMenu` w slocie), dwoma źródłami i zaznaczonymi „Posty”;
  - użytkownik z zaznaczonymi „Posty”;
  - „Dane nie były jeszcze aktualizowane.”;
  - brak etykiety (błąd).
- Nowa sekcja „Nagłówek strony” pokazuje warianty z samym tytułem, z podtytułem i z odznaką stanu w slocie `status`.
- Komentarz w nagłówku kitchen sink i komentarz `.bell-bar` przestają odwoływać się do `AppHeader`.
- `AppBar.vue` i `PageHeader.vue` są importowane `?raw` i dopisane do `CLEANED`.
- W `STATES` dochodzą stany `hover` i `focus` odnośnika nawigacji w regionie „Pasek aplikacji”.
- Nowe wzorce: `kitchen-sink-pasek-aplikacji.png`, `kitchen-sink-naglowek-strony.png` i dwa `state-*` nawigacji w `light-375` i `dark-375`, zapisane przez `npm run visual:update`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym:
  - `AppBar`: odnośniki z `items`, `aria-current` tylko na bieżącym, trzy stany daty, zdarzenie `logout`, slot dzwonka;
  - `PageHeader`: jeden `h1`, `focus()`, slot `status` za `h1`;
  - skan `CLEANED`.
  Polecenie: `cd szczur-radar-web && npm test`.
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy wizualne i axe przechodzą z nowymi wzorcami katalogu: `cd szczur-radar-web && npm run visual:docker`
- W kitchen sink przy 320 px sekcja „Pasek aplikacji” nie przewija strony w poziomie, także z otwartą listą dzwonka w wariancie admina. Agent sprawdza przez `playwright-cli` na `npm run dev` `document.documentElement.scrollWidth <= 320` przy zamkniętej i przy otwartej liście, a przy otwartej także to, że region „Powiadomienia” mieści się w szerokości okna (`getBoundingClientRect()`: `left >= 0`, `right <= 320`).

**Implementation Note**: Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy. Jedyna bramka człowieka to 3.6.

---

## Faza 2: Powłoka w routerze i widoki w niej

### Overview

Pasek staje się stałą powłoką zalogowanych tras. Widoki tracą `AppHeader` i własny `main`, a data danych przechodzi z listy postów do paska.

### Changes Required:

#### 1. Trasa-rodzic i sekcje

**File**: `szczur-radar-web/src/router/index.ts`, `szczur-radar-web/src/router/index.test.ts`

**Intent**: Powłoka montuje się raz dla zalogowanych tras, a każda trasa mówi, do której pozycji nawigacji należy.

**Contract**:
- Rekord `{ path: '/', component: AppShell, children: [...] }` obejmuje `home` (`''`), `post` (`posts/:id`), `admin` (`admin`, `meta.admin`) i `admin-run` (`admin/runs/:id`, `meta.admin`).
- Nazwy i adresy tras bez zmian.
- `/login`, `/activate`, `/kitchen-sink` i trasa łapiąca wszystko zostają poza rodzicem.
- `RouteMeta` dostaje `section?: AppSection`, gdzie `type AppSection = 'posts' | 'admin'`, z wartościami: `home`, `post` → `posts`; `admin`, `admin-run` → `admin`.
- Strażnik bez zmian w działaniu.
- Testy routera:
  - każda trasa powłoki ma `section`;
  - przejście między dziećmi nie montuje powłoki na nowo;
  - strażnik działa jak dotąd.

#### 2. Powłoka (dane)

**File**: `szczur-radar-web/src/components/AppShell.vue` (nowy), `szczur-radar-web/src/components/AppShell.test.ts` (nowy)

**Intent**: Łączy sesję, router i `/api/freshness` z `AppBar` i daje kolumnę treści. Przejmuje wylogowanie i dzwonek z `AppHeader`.

**Contract**:
- Pozycje nawigacji:
  - „Posty” → `home`, sekcja `posts`;
  - „Panel admina” → `admin`, sekcja `admin`, tylko dla roli `admin`;
  - `current` to `route.meta.section === section`.
- `email` pochodzi z `currentUser`, a `NotificationBell` w slocie jest tylko dla admina.
- Wylogowanie:
  - powodzenie: `logout()`, potem `clearSession()` i `router.replace({ name: 'login' })`;
  - porażka: komunikat „Nie udało się wylogować. Spróbuj ponownie.” w `StatusMessage` pod paskiem, a sesja zostaje.
- Data danych:
  - stała `FRESHNESS_FRESH_MS = 60_000`;
  - odczyt przy montażu, w `router.afterEach` i przy `visibilitychange` do `visible`, pominięty, gdy ostatni udany odczyt jest młodszy niż `FRESHNESS_FRESH_MS`;
  - odczyt tylko przy zalogowanym `currentUser` (wylogowanie wywołuje `afterEach` w drodze do `/login`, gdy powłoka jest jeszcze zamontowana) i tylko po udanej nawigacji (`afterEach` bez `failure`), tak jak w `NotificationBell.vue:36-39, 80-82`;
  - błąd zostawia poprzednią wartość, a bez niej daje `null`;
  - odpowiedź bez `sources` daje `null`;
  - nasłuch jest zdejmowany przy odmontowaniu.
- Treść to `<main>` z kontenerem kolumny: `max-width: var(--width-content)`, marginesy z prototypu `.page`, czyli `--space-7 --space-5 --space-9`, a poniżej 900 px `--space-5 --space-3 --space-8`. W środku jest `<RouterView />`.
- Testy:
  - pozycje według roli i sekcji;
  - wylogowanie: powodzenie i porażka;
  - reguła odczytu na sztucznych zegarach: start, nawigacja po 60 s, nawigacja przed 60 s bez żądania, powrót do karty;
  - wylogowanie po 60 s nie pyta o datę danych, a przerwana nawigacja też nie;
  - błąd i stary kształt odpowiedzi;
  - użytkownik nie pyta o powiadomienia.

#### 3. Widoki w powłoce

**File**:
- `szczur-radar-web/src/views/FeedView.vue`
- `szczur-radar-web/src/views/PostDetailView.vue`
- `szczur-radar-web/src/views/AdminView.vue`
- `szczur-radar-web/src/views/RunDetailView.vue`
- testy tych widoków

**Intent**: Widoki renderują tylko swoją treść wewnątrz `main` powłoki, z tytułem z `PageHeader`, i zachowują dzisiejszą szerokość treści (decyzja planu).

**Contract**:
- Każdy widok:
  - traci `<AppHeader />` i własny `<main>`;
  - korzeń to zwykły kontener z dzisiejszym `max-width` (48/54rem, w panelu admina 56/63rem), wyśrodkowany, bez własnego zewnętrznego `padding`, bo margines daje powłoka;
  - tytuł ze `PageHeader`: „Posty”, tytuł posta, „Panel admina”, tytuł przebiegu, z dzisiejszymi `id` dla `aria-labelledby`;
  - `RunDetailView`: `StatusBadge` przebiegu trafia do slotu `status`, a `.title-row` i jego style znikają;
  - przenosi fokus przez `focus()` nagłówka w tym samym momencie co dziś.
- Odnośniki powrotu („Posty” na stronie posta, „Panel admina” na stronie przebiegu) zostają w treści.
- `FeedView` traci etykietę świeżości, jej style, `fetchFreshness` i oczekiwanie na `freshnessSettled`. `reportFeedLoaded('ok')` idzie zaraz po wyrenderowaniu listy.
- Testy widoków:
  - `FeedView.test.ts` traci blok świeżości, a testy raportu przewijania nie czekają na datę;
  - `PostDetailView.test.ts:157` nie szuka „Wyloguj”;
  - zbędne mocki powiadomień w `AdminView.test.ts` i `RunDetailView.test.ts` znikają.

#### 4. Usunięcie AppHeader i komentarze

**File**:
- `szczur-radar-web/src/components/AppHeader.vue` i `AppHeader.test.ts` (usunięte)
- `szczur-radar-web/src/styleContract.test.ts`
- `szczur-radar-web/src/notificationsStore.ts`, `szczur-radar-web/src/components/NotificationBell.vue`, `szczur-radar-web/src/components/NotificationMenu.vue`

**Intent**: Jedna droga do paska. Komentarze mówią prawdę o stałej powłoce.

**Contract**:
- `AppHeader.vue` wypada z `CLEANED`, a `AppShell.vue` do niego wchodzi.
- Przypadki z `AppHeader.test.ts` (dzwonek dla admina, motyw obok „Wyloguj”, brak dzwonka i żądania dla użytkownika) przechodzą do `AppShell.test.ts`. Asercja na dokładną listę przycisków przechodzi w asercję na obecność i brak nazw.
- Komentarze w nagłówku `notificationsStore.ts` i `NotificationBell.vue` uzasadniają store tym, że lista żyje z sesją: czyści ją koniec sesji, a po ponownym zalogowaniu powłoka montuje się na nowo. Bez zdania o pasku w każdym widoku.
- Komentarz w nagłówku `NotificationMenu.vue` (`:6`) mówi o pasku `AppBar` zamiast „AppHeader's bar”.

#### 5. Gotowość widoków i wzorce F-06

**File**: `szczur-radar-web/visual/targets.ts`, `szczur-radar-web/visual/__screenshots__/`

**Intent**: Zrzuty widoków czekają na pełny pasek i pokazują nowy układ.

**Contract**:
- Nowy pomocnik `freshnessLoaded()` czeka na tekst „Dane:” w `banner`. Wywołują go `view-feed`, `view-post`, `view-admin` i `view-run`.
- `view-feed` przestaje szukać „Dane zaktualizowane:” w `main`.
- Wzorce `view-{feed,post,admin,run}.png` w czterech projektach zapisuje od nowa `npm run visual:update`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym router, `AppShell`, widoki i skan `CLEANED`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Dotychczasowe testy E2E przechodzą bez zmian w ich lokatorach: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą na nowych wzorcach widoków: `cd szczur-radar-web && npm run visual:docker`
- Drugi przebieg bez zmian w kodzie jest zielony, więc zrzuty są powtarzalne: `cd szczur-radar-web && npm run visual:docker`
- W kodzie nie ma odwołań do `AppHeader`: `! grep -rn "AppHeader" szczur-radar-web/src szczur-radar-web/e2e szczur-radar-web/visual szczur-radar-web/dev`
- Powrót z posta do listy przywraca pozycję i fokus na odnośniku posta. Agent sprawdza to przez `playwright-cli` na buildzie produkcyjnym (`vite preview`) z atrapą `dev/mock-api.js`: przewija na 3. ekran listy, otwiera post, cofa i porównuje `scrollY` oraz `document.activeElement`.

**Implementation Note**: Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy. Jedyna bramka człowieka to 3.6.

---

## Faza 3: Testy E2E powłoki i weryfikacja

### Overview

Przepływy powłoki z kontraktu projektu dostają testy w przeglądarce. Strażnik atrapy pilnuje trybów daty danych. Dokumentacja dogania kod, a pasek jest sprawdzony w PR env i oceniony na zrzutach.

### Changes Required:

#### 1. Testy E2E powłoki

**File**: `szczur-radar-web/e2e/shell.spec.ts` (nowy)

**Intent**: Każde ryzyko przepięcia, którego nie łapią istniejące testy, ma swój test (NFR `prd.md:328`, `ui-design.md`, „Powłoka (każdy ekran po zalogowaniu)”).

**Contract**:
- Testy według reguł repozytorium: `test`/`expect` z `e2e/fixtures.ts`, lokatory po roli i tekście w `banner`/`main`, nazwy według ryzyka:
  - „pozycja nawigacji otwiera swój ekran i przenosi zaznaczenie”: admin; `/` ma „Posty” z `aria-current`; klik „Panel admina” daje nagłówek 1 „Panel admina” i przeniesione zaznaczenie; „Szczegóły przebiegu z …” zostawia zaznaczone „Panel admina”; „Posty” i otwarcie posta zostawiają zaznaczone „Posty”;
  - „każdy ekran po zalogowaniu ma jeden pasek, jedną treść i jeden nagłówek 1”: `/`, post, `/admin`, przebieg;
  - „data danych pokazuje dzień i godzinę każdego źródła”: `FRESHNESS: 'apart'`, w pasku „Wykop 13.10.2026, 05:12” i „Reddit 11.10.2026, 05:12” w elementach `time`;
  - „bez aktualizacji pasek mówi, że danych jeszcze nie ma”: `FRESHNESS: 'none'`;
  - „błąd daty danych nie psuje strony”: `FRESHNESS: 'error'`, brak „Dane:” w pasku i wczytana lista;
  - „na wąskim ekranie data danych zostaje w pasku”: 375 px, „Dane:” widoczne, e-mail niewidoczny, „Wyloguj” widoczne;
  - „strony publiczne nie mają paska”: `AUTH: 'anonymous'`, `/login` bez `navigation` „Główna”.
- Wylogowanie, motyw, brak „Panelu admina” u użytkownika i dzwonek mają już testy (`auth.spec.ts`, `theme.spec.ts`, `admin.spec.ts`), więc nie są powielane.

#### 2. Strażnik atrapy i nagłówek atrapy

**File**: `szczur-radar-web/src/contract/mockApi.test.ts`, `szczur-radar-web/dev/mock-api.js`

**Intent**: Tryby `FRESHNESS`, na których stoją nowe testy, są pod strażnikiem atrapy.

**Contract**:
- Wiersz `ROWS` „freshness: error” (`GET /api/freshness`, 500, `internal_error`).
- Wpis trasy `/api/freshness` w `ROUTES` sprawdza kształt także dla `none` i `apart`, przez dodatkowe `installMock` jak przy `QUALITY`.
- Zdanie w nagłówku atrapy (`:127`) mówi o pasku powłoki zamiast o nagłówku każdego widoku.

#### 3. Dokumentacja repozytorium

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Przewodnik nazywa nowe komponenty i powłokę, więc następny wycinek zaczyna od nich.

**Contract**:
- W „Project Structure” w opisie `szczur-radar-web/` dochodzi powłoka (trasa-rodzic, `meta.section`).
- W „UI”, w „Shared components”:
  - dochodzą `AppBar`, `AppShell` i `PageHeader`;
  - `ThemeToggle` i `NotificationBell` są „w `AppBar`”/„renderowane przez `AppShell`” zamiast „w `AppHeader`”;
  - zdanie o `notificationsStore` zgadza się z nowym komentarzem.
- Bez nowych zasad poza tym.

#### 4. Weryfikacja w PR env i akceptacja wyglądu

**File**: opis PR, galeria jako Artifact (poza repozytorium), `context/changes/app-shell-navigation/evidence/pr-env/README.md` (nowy, tylko wyniki pomiarów)

**Intent**: Pasek działa na prawdziwym API i prawdziwych datach. Właściciel ocenia wygląd na obrazkach obok prototypów (pamięć `ui-decisions-by-pictures`). Opis PR pokazuje zrzut F-06 obok prototypu (roadmapa, M-5).

**Contract**:
- Galeria pokazuje:
  - pasek w `view-{feed,post,admin,run}` w czterech projektach obok `ui-design/screens/posts-*` i `run-*`;
  - sekcje „Pasek aplikacji” i „Nagłówek strony” obok `components-*-375.png`.
- Opis PR wymienia zmienione grupy wzorców: `view-*` (powłoka, kolumna), nowe `kitchen-sink-pasek-aplikacji`, `kitchen-sink-naglowek-strony` i `state-*` nawigacji. Zawiera `Refs #179`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym strażnik atrapy: `cd szczur-radar-web && npm test`
- Testy E2E przechodzą, w tym `shell.spec.ts`: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą: `cd szczur-radar-web && npm run visual:docker`
- CI na PR jest zielone, w tym `web-e2e` (krok wizualny) i `web-container-smoke`: `gh pr checks`
- W PR env pasek działa na prawdziwym API. Po wdrożeniu sprawdzanego commita agent wykonuje kontrolę w Chromium przez `playwright-cli`:
  1. Zakłada w PR env jednorazowe konto `--role admin`, aktywuje je i uzyskuje ciasteczko sesji według procedury z `lessons.md` („W PR env agent sam zakłada jednorazowe konto admina…”). Link, token, hasło i ciasteczko są tylko w zmiennych powłoki i w pliku w scratchpadzie, usuniętym po kontroli.
  2. Odczytuje `GET /api/freshness` przez `curl` z ciasteczkiem i dla każdego źródła wylicza oczekiwany tekst „<Źródło> dd.mm.rrrr, gg:mm” w czasie warszawskim.
  3. Wczytuje sesję przez `playwright-cli state-load` i otwiera `/`. Potwierdza w `banner`:
     - `navigation` „Główna” z „Posty” (`aria-current="page"`) i „Panel admina”;
     - „Dane:” i wiersze równe wyliczonym w kroku 2;
     - przycisk „Powiadomienia, …”, „Ciemny motyw”, e-mail konta i „Wyloguj”.
  4. Klika „Panel admina”, potem „Szczegóły przebiegu z …”, jeśli przebieg istnieje. Na obu stronach jest dokładnie jeden `banner`, jeden `main` i jeden nagłówek 1, a zaznaczone jest „Panel admina”.
  5. Przy 375 px potwierdza widoczne „Dane:” i `scrollWidth <= 375`.
  6. Klika „Wyloguj” i potwierdza adres `/login`.
  Wyniki pomiarów (bez sekretów) trafiają do `evidence/pr-env/README.md`.

#### Manual Verification:

- Właściciel akceptuje wygląd paska i kolumny w galerii zrzutów obok prototypów (jasny i ciemny, 375 i 1280 px)

---

## Testing Strategy

### Unit Tests:

- `AppBar`:
  - odnośniki i `aria-current`;
  - stany daty (źródła, brak aktualizacji, `null`) i `time` z `datetime`;
  - slot dzwonka, `logout`, `busy`.
- `PageHeader`: jeden `h1` z `tabindex="-1"`, `focus()`, podtytuł, slot `status` i slot narzędzi.
- `AppShell`:
  - pozycje według roli i sekcji;
  - wylogowanie (powodzenie, porażka);
  - reguła odczytu daty na sztucznych zegarach (montaż, `afterEach` przed i po 60 s, `visibilitychange`), bez odczytu po wylogowaniu i przy przerwanej nawigacji;
  - błąd zostawia poprzednią wartość;
  - stary kształt daje `null`;
  - użytkownik bez dzwonka i bez żądania o powiadomienia.
- Router: trasy powłoki mają `section`, powłoka nie montuje się na nowo między dziećmi, strażnik bez zmian.
- Widoki: bez paska i własnego `main`, tytuł z `PageHeader`, fokus jak dziś. `FeedView` raportuje wczytanie bez czekania na datę.

### Integration Tests:

- E2E `shell.spec.ts` (faza 3) i dotychczasowe specyfikacje bez zmian lokatorów (faza 2).
- F-06: wzorce widoków i katalogu z axe na każdym widoku.

### Manual Testing Steps:

1. Właściciel ogląda galerię zrzutów paska obok prototypów i akceptuje wygląd (faza 3).

## Performance Considerations

Data danych to jedno małe żądanie przy nawigacji, najwyżej raz na 60 s, i przy powrocie do karty. Nie ma odpytywania w tle, więc API na Hobby nie jest budzone bez działania użytkownika.

## Migration Notes

Brak zmian danych i API. Wdrożenie web jest niezależne od API: powłoka toleruje odpowiedź `/api/freshness` bez `sources` tak jak dziś `FeedView`.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-47), issue #179
- Projekt: `context/foundation/ui-design.md` („Nawigacja”, „Ekran startowy”, „Szeroki i wąski ekran”, „Powłoka i nawigacja”, „Daty, godziny i liczby”, „Kontrakt dla testów E2E”); prototyp `context/foundation/ui-design/prototypes/screens/posts.html:108-131`, style `prototypes/prototype.css:657-810, 2309-2375`
- PRD: FR-007, FR-013, FR-015, FR-021, FR-030 (doprecyzowanie z 2026-11-21, `prd.md:241`)
- Audyt: `context/changes/post-m4-ui-readiness-audit/research.md` („Shell i router”)
- Wcześniejsze decyzje:
  - store powiadomień: `context/archive/2026-10-15-pipeline-run-history-dashboard/reviews/impl-review.md` (F2);
  - motyw w pasku: `context/archive/2026-11-17-light-dark-theme-switch/plan-brief.md`;
  - komponenty F-08: `context/archive/2026-11-21-design-tokens-and-components/plan.md`;
  - etykieta świeżości: `context/archive/2026-10-12-daily-scheduled-run-freshness/plan.md`, `context/archive/2026-10-30-source-aware-feed/plan-brief.md`.
- Wzorzec prezentacja + dane: `szczur-radar-web/src/components/NotificationMenu.vue`, `NotificationBell.vue`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Komponenty paska i nagłówka strony

#### Automated

- [x] 1.1 Testy jednostkowe przechodzą, w tym AppBar, PageHeader i skan CLEANED — 2ed6633
- [x] 1.2 Typecheck i build przechodzą — 2ed6633
- [x] 1.3 Testy wizualne i axe przechodzą z nowymi wzorcami katalogu — 2ed6633
- [x] 1.4 W kitchen sink przy 320 px sekcja „Pasek aplikacji” nie przewija strony w poziomie, także z otwartą listą dzwonka — 2ed6633

### Phase 2: Powłoka w routerze i widoki w niej

#### Automated

- [x] 2.1 Testy jednostkowe przechodzą, w tym router, AppShell, widoki i skan CLEANED — 92234f0
- [x] 2.2 Typecheck i build przechodzą — 92234f0
- [x] 2.3 Dotychczasowe testy E2E przechodzą bez zmian w ich lokatorach — 92234f0
- [x] 2.4 Testy wizualne i axe przechodzą na nowych wzorcach widoków — 92234f0
- [x] 2.5 Drugi przebieg bez zmian w kodzie jest zielony (zrzuty powtarzalne) — 92234f0
- [x] 2.6 W kodzie nie ma odwołań do AppHeader — 92234f0
- [x] 2.7 Powrót z posta do listy przywraca pozycję i fokus na odnośniku posta — 92234f0

### Phase 3: Testy E2E powłoki i weryfikacja

#### Automated

- [x] 3.1 Testy jednostkowe przechodzą, w tym strażnik atrapy — 3469327
- [x] 3.2 Testy E2E przechodzą, w tym shell.spec.ts — 3469327
- [x] 3.3 Testy wizualne i axe przechodzą — 3469327
- [x] 3.4 CI na PR jest zielone, w tym web-e2e i web-container-smoke — 64e3ece
- [x] 3.5 W PR env pasek działa na prawdziwym API (kontrola agenta przez playwright-cli) — 0980f3f

#### Manual

- [ ] 3.6 Właściciel akceptuje wygląd paska i kolumny w galerii zrzutów obok prototypów
