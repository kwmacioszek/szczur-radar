# Hardening UI feedu — Implementation Plan

## Overview

Feed `/` (`FeedView` + `PostCard`) przechodzi z literałów i ręcznych elementów na kontrakt systemu projektowego tego repozytorium: skale `--font-size-*`, `--space-*`, `--radius*`, role kolorów i trzy współdzielone komponenty (`AppButton`, `StatusMessage`, `StatusBadge`). Stronicowanie dostaje jedno źródło stanu, a zmiana strony, błąd, ładowanie i strona poza zakresem — zachowanie, które nie zostawia użytkownika ze starą listą, na dole strony albo bez wyjścia. Kontrola `no literals` zaczyna pilnować skali, reguła repo to opisuje. Zakres: jeden widok plus jego komponent karty; bez nowych tokenów, zależności i zmian backendu.

Źródło: `research.md` (zarzuty C1–C4, deferred D1–D4, macierz 7 stanów „przed”, zrzuty `ui-before/`, mock `mock-feed.js`). Decyzje wizualne poparte przebiegiem `design-taste-frontend` w trybie „redesign — preserve” (odczyt: feed produktowy dla zaproszonych inwestorów, spokojny, czytelny; pokrętła VARIANCE 2 / MOTION 1 / DENSITY 5).

## Current State Analysis

- Kolory feedu są już rolami (skan `/10x-ui`: 0 trafień), ale pismo i odstępy to 23 linie `px` (FeedView 5, PostCard 18); w karcie 3 z 4 rozmiarów pisma są spoza skali (13/15/18 px), odstęp 6 px spoza skali, linia między postami z własnego `color-mix` (`research.md`, C1).
- Oba pliki są na liście `CLEANED` w `src/styleContract.test.ts:85-95`, a skan `LITERAL` (`:98-99`) łapie tylko kolory — reguła `REPOSITORY_GUIDELINES.md:45` obiecuje więcej, niż test sprawdza.
- `AppButton`, `StatusMessage`, `StatusBadge` mają w feedzie 0 importów: błąd to goły `<p role="alert">` (`FeedView.vue:47`), stronicowanie to surowe `<button>` (`:55`, `:57`), flagi to ręczny `.flag` (`PostCard.vue:105-112`) (C2).
- Stan strony ma dwa źródła: numer z adresu (`FeedView.vue:18-21`), lista i `total` z ostatniej odpowiedzi (`:13`, `:22`). Skutki zmierzone w researchu: „Strona 2 z 3” nad postami strony 1, `scrollY` 8213 → 8223 po zmianie strony, fokus na `body`, `/?page=9` → „Strona 10 z 3” bez treści (C3). `load()` nie odrzuca spóźnionych odpowiedzi.
- Błąd wypiera listę i stronicowanie bez ponowienia; ładowanie bez `aria-busy` (C4).
- Bramka wizualna nie obejmuje feedu: `dev/mock-api.js:51` odpowiada na `/api/posts` 404, kitchen sink nie ma `PostCard`.

## Desired End State

- `FeedView.vue` i `PostCard.vue` biorą pismo, odstępy, promienie i kolory wyłącznie z `:root` w `src/style.css`; błąd, przyciski i flagi to `StatusMessage`, `AppButton`, `StatusBadge`.
- Karta ma dwa kroki skali: nagłówek `--font-size-md`, waga 600, `--text-h`; treść oceny `--font-size-md`; metadane, notka i flagi `--font-size-sm`. Typ posta to zwykła metadana obok daty (`--text`, bez wersalików i akcentu). Stanowisko to tekst: nazwa celu `--text-h` 600, „wzrostowo” `--success`, „spadkowo” `--destructive`, pozostałe `--text`.
- Etykieta stronicowania, lista i liczba stron pochodzą z jednej, ostatniej żądanej odpowiedzi. Zmiana strony: od razu przewinięcie na górę, zamiast listy „Wczytywanie strony N…” z `aria-busy`, po wczytaniu fokus na nagłówku „Posty”. Strona poza zakresem: komunikat i przycisk do ostatniej strony. Błąd: `StatusMessage` z „Spróbuj ponownie”.
- Skan `no literals` odrzuca `px`/`rem` w piśmie, odstępach i promieniach plików z listy `CLEANED`; `LoginView` jest poza listą do `login-view-ui-hardening`; `REPOSITORY_GUIDELINES.md` mówi to samo.
- Weryfikacja: `npm test` i `npm run build` zielone; `ui-after/README.md` z macierzą 7 stanów w obu motywach przy 1280 i 375 px; feed w Railway PR env na prawdziwych danych.

### Key Discoveries:

- Wzorzec ładowania, błędu i ponowienia per sekcja: `szczur-radar-web/src/views/AdminView.vue:216-222` (`aria-busy`, „Wczytywanie…”, `StatusMessage`), `:185-186` („Spróbuj ponownie”).
- `StatusBadge` ma tony ogólne (`StatusBadge.vue:3`, `:23-41`); `neutral` = obwódka i tekst `--text`, `--radius-sm`, `--font-size-sm`.
- Globalny `h1` daje już `--font-size-xl` (`style.css:73-78`) — powtórzony `font-size: 32px` w `FeedView.vue:73` jest zbędny.
- API dla strony poza zakresem: 200, `items: []`, prawdziwy `total` (`PostController.java:45-52`).
- Istniejący test stronicowania mockuje stałą odpowiedź `page: 1` niezależnie od żądanej strony (`FeedView.test.ts:80-94`) — stan widoku musi być kluczowany żądaną stroną, nie polem `page` odpowiedzi.
- Rozszerzony skan na obecnej liście `CLEANED` trafia `px` w piśmie i odstępach tylko w `LoginView.vue` (`:77`, `:84`, `:86`, `:91`, `:100`) poza plikami feedu (`research.md`, C1).

## What We're NOT Doing

- Link do oryginału, autor, widok szczegółowy posta → S-03 (`post-detail-view`); karta nadal nie ma kontrolek (D1).
- Filtry i ich stan pusty → S-04; data ostatniej udanej aktualizacji → S-05 (D2).
- Drugi pasek stronicowania nad listą i rozmiar strony → razem z filtrami S-04 (D3).
- `AppHeader` przy 375 px (D4), `LoginView` (zmiana `login-view-ui-hardening`; tu tylko zdjęcie z listy `CLEANED`).
- Nowe kroki skali, nowe role kolorów, szkielety kart, animacje, przełącznik motywu, nowe zależności.
- Stanowisko jako `StatusBadge` (odrzucone: do 6 obwódek na kartę, stanowisko wyglądałoby jak flaga).
- Ciche przekierowanie strony poza zakresem (odrzucone na rzecz komunikatu z przyciskiem).
- Zmiany backendu, `/api/posts` i `api/posts.ts`.

## Implementation Approach

Kolejność z `/10x-ui`: środowisko → wartości tokenów → jeden widok → stany → utrwalenie. **Faza „wartości tokenów” jest N/A**: po decyzjach (dwa kroki skali + waga, stanowisko tekstem w kolorze roli, typ w `--text`) widok potrzebuje wyłącznie istniejących ról i kroków skali; `style.css` się nie zmienia. Faza 1 nie zmienia wyglądu aplikacji, tylko daje bramce dane feedu. Faza 2 przenosi kartę i widok na kontrakt i włącza ostrzejszy skan. Faza 3 przebudowuje stan stronicowania i domyka macierz 7 stanów. Faza 4 zostawia regułę, listę kontrolną i weryfikację w PR env.

Proces (reguły repo, nie wiersze Progress): gałąź `feature/feed-view-ui-hardening` istnieje w tym worktree na `6bebf82`; na starcie fazy 1 `/10x-implement` robi `git fetch` i potwierdza, że gałąź stoi na aktualnym `origin/main` (inaczej rebase przed pierwszym commitem), zakłada issue `feed-view-ui-hardening: …` (zmiana poza roadmapą) i PR z `Refs #<issue>`; folder zmiany idzie w pierwszym commicie. Implementacja, weryfikacja w PR env, `/10x-impl-review` z poprawkami i `/10x-archive` na tej samej gałęzi; merge robi człowiek.

## Critical Implementation Details

- **Spóźnione odpowiedzi.** Każde wczytanie dostaje numer; odpowiedź (albo błąd) starszego wczytania niż ostatnie jest ignorowana. Bez tego szybkie „Starsze, Starsze” albo wstecz/dalej pokażą starszą stronę pod nowszym adresem — C3 w innej postaci, a S-04 odziedziczy to z filtrami.
- **Przewinięcie i fokus.** Przewinięcie na górę (`window.scrollTo({ top: 0 })`, bez `behavior: 'smooth'` — MOTION 1) w chwili zmiany strony, nie po odpowiedzi; fokus na `h1` (`tabindex="-1"`) dopiero po wczytaniu i tylko po zmianie strony, nie przy pierwszym wejściu (nie kradnij fokusu przy nawigacji z nagłówka). jsdom nie implementuje `window.scrollTo` — testy go stubują (`vi.spyOn(window, 'scrollTo').mockImplementation(() => {})`); test fokusu montuje z `attachTo: document.body`.
- **Worktree i zrzuty.** W świeżym worktree `npm ci` w `szczur-radar-web/` przed `vite`; `playwright-cli open --config=<główny checkout>/.playwright/cli.config.json`; mock dopasowuje `pathname.startsWith('/api/')`, nigdy `**/api/**` (łapie moduły Vite). Folder `context/` to ślad planowania: `research.md`, `mock-feed.js` i `ui-before/` zostają bez zmian.
- **PR env tylko z web.** PR zmienia wyłącznie `szczur-radar-web/` i dokumenty, więc Railway może pominąć deploy API i Postgresa w nowym PR env (`deploy-plan.md`, F-01 „The PR env had no database”). Wtedy `railway redeploy --from-source` dla `postgres`, `szczur-radar-api`, `szczur-radar-web`; przed sprawdzeniem UI `meta.commitHash` ostatniego `SUCCESS` web = głowa gałęzi (`deploy-plan.md`, „PR-env verification: the web deployment's commit”). Dane: przebieg zakresu dat w `/admin` PR env (prawdziwe wywołania Wykopu i LLM w PR env są dozwolone).

## Phase 1: Środowisko i bramka

### Overview

Bramka dostaje dane feedu bez zmiany wyglądu aplikacji: utrzymywany mock zna `/api/posts`, a kitchen sink pokazuje `PostCard` w obecnym wyglądzie.

### Changes Required:

#### 1. Mock `/api/posts`

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Jedno utrzymywane narzędzie do zrzutów `/admin` i `/` (reguła `REPOSITORY_GUIDELINES.md:46` wskazuje ten plik); dane i tryby feedu przeniesione z `context/changes/feed-view-ui-hardening/mock-feed.js`, który zostaje jako dowód researchu.

**Contract**: `/api/posts` odpowiada jak `PostController` (`page`, `size`, `total`; strona poza zakresem = 200, `items: []`, prawdziwy `total`), 120 postów z wariantami z `mock-feed.js` (typy `post`/`link`/`comment`, długi tekst, długi URL, prefiltr, 0–4 cele z każdym kierunkiem, flagi). Tryby: `loading` i `error` obejmują także `/api/posts`, `empty` daje `total` 0, nowe `loading-next` i `error-next` dotyczą tylko `page > 0`. Komentarz nagłówka: lista trybów i `goto http://localhost:5173/` dla feedu. `/api/admin/*` bez zmian.

#### 2. Kitchen sink: sekcja „Post”

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`

**Intent**: `PostCard` jest współdzielonym komponentem w `src/components/`, a kitchen sink go nie pokazuje (reguła `REPOSITORY_GUIDELINES.md:44`).

**Contract**: Nowa sekcja `aria-labelledby="posts-title"`, nagłówek „Posty”, stałe dane `PostItem` (typ z `api/posts.ts`), co najmniej: wpis z oceną modelu (cel wzrostowo ostrożnie, cel spadkowo, dwie flagi), znalezisko z tytułem i czterema celami (`neutral`, `mixed`, `not_expressed`, `bullish`), post z prefiltra, post bez oceny (`assessment: null`), post z długim URL bez spacji. Bez wywołań API.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą, a `dist/` nie zawiera kitchen sink: `cd szczur-radar-web && npm run build && ! grep -ri kitchen dist/`
- `dev/mock-api.js` parsuje się i obsługuje `/api/posts`: `node --check szczur-radar-web/dev/mock-api.js && grep -c "'/api/posts'" szczur-radar-web/dev/mock-api.js` zwraca co najmniej 1

#### Manual Verification:

- `/` z `dev/mock-api.js` (tryb `data`) przy 1280×900 wygląda jak `ui-before/feed-data-1280-light-viewport.png`; `/kitchen-sink` pokazuje sekcję „Posty” ze wszystkimi wariantami karty w obu motywach

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 2: Karta i widok na kontrakcie

### Overview

Zarzuty C1 i C2 oraz poprawki treści: karta i widok na skali i komponentach, nowa hierarchia karty, ostrzejsza kontrola.

### Changes Required:

#### 1. `PostCard` na skali, rolach i `StatusBadge`

**File**: `szczur-radar-web/src/components/PostCard.vue`

**Intent**: Hierarchia wagą i kolorem zamiast czterech rozmiarów pisma; treść oceny czytelna (16 zamiast 15 px), flagi i notka nie mniejsze niż najmniejszy krok skali; jedna implementacja znacznika w aplikacji.

**Contract**:
- Metadane (`time` + typ): `--font-size-sm`, `--text`; typ to tekst z `label('itemType', …)` bez `text-transform`, `letter-spacing` i `--accent`.
- Nagłówek `h2`: `--font-size-md`, `font-weight: 600`, `--text-h`; skracanie do 200 znaków bez zmian (`:9-12`).
- Trafność i lista celów: `--font-size-md`; notka prefiltra: `--font-size-sm`.
- Cel: nazwa `--text-h`, `font-weight: 600`; stanowisko tekstem, kolor `--success` dla `bullish`, `--destructive` dla `bearish`, dziedziczony `--text` dla pozostałych; „(ostrożnie)” bez zmian.
- Flagi: każda jako `StatusBadge tone="neutral"`, tekst z `label('flag', …)`; klasa `.flag` znika.
- Linia między postami: `border-bottom: 1px solid var(--border)`.
- Odstępy, odstępy siatki i promienie wyłącznie z `--space-*` i `--radius-sm`; 6 px zastąpione najbliższym krokiem skali.

#### 2. `FeedView` na skali i komponentach

**File**: `szczur-radar-web/src/views/FeedView.vue`

**Intent**: Usunąć literały i ręczne elementy; nowy tekst stanu pustego bez żargonu panelu admina.

**Contract**:
- `.feed` i `.pager`: odstępy z `--space-*`; `max-width: 48rem` zostaje (szerokość układu, nie skala).
- `h1`: bez własnego `font-size`; marginesy z `--space-*`.
- Błąd: `StatusMessage tone="error"` (tekst w fazie 3); klasy `.error` i `.status` znikają.
- Stronicowanie: `AppButton` (wariant pomocniczy) dla „Nowsze” i „Starsze”, `nav aria-label="Strony"` i `.position` bez zmian.
- Stan pusty (`total === 0`): klasa `.empty`, tekst „Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.”

#### 3. Skan skali w kontroli

**File**: `szczur-radar-web/src/styleContract.test.ts`

**Intent**: Lista `CLEANED` ma znaczyć „bez literałów kolorów i bez wartości spoza skali”, jak mówi reguła.

**Contract**: Test `no literals` sprawdza dla każdego pliku z `CLEANED` dwa wzorce: dotychczasowy `LITERAL` oraz nowy wzorzec `px`/`rem` w wartościach właściwości `font-size`, `margin*`, `padding*`, `gap`, `row-gap`, `column-gap`, `letter-spacing`, `border-radius`; błąd nadal wypisuje `plik:linia`. Obramowania (`border: 1px …`, `border-left: 4px …`) i szerokości układu w `rem` (`max-width`, `min-width`, `width`, `grid-template-columns`) są poza wzorcem. `LoginView.vue` znika z `CLEANED` i z importów `?raw`, z komentarzem, że wraca w `login-view-ui-hardening` (literały `px` w odstępach i piśmie, `LoginView.vue:77-100`). Pozostałe pliki listy przechodzą bez zmian.

#### 4. Testy karty i widoku

**File**: `szczur-radar-web/src/components/PostCard.test.ts` (nowy), `szczur-radar-web/src/views/FeedView.test.ts`

**Intent**: Utrwalić zachowanie karty po zmianie hierarchii i znaczników.

**Contract**: `PostCard.test.ts`: typ posta jako tekst etykiety („wpis”), stanowisko z klasą kierunku i tekstem „wzrostowo (ostrożnie)”, flagi jako `StatusBadge` (`[data-tone="neutral"]`) z polskimi etykietami, notka prefiltra, karta bez oceny renderuje datę i nagłówek. `FeedView.test.ts`: dotychczasowe przypadki z tymi samymi selektorami (`article`, `.empty`, `.position`, `.pager button`, `[role="alert"]`); stan pusty z nowym tekstem.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą, w tym rozszerzony skan `no literals` i `PostCard.test.ts`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Brak `px`/`rem` w piśmie i odstępach feedu: `grep -cE '(font-size|margin|padding|gap|letter-spacing|border-radius)[^;]*[0-9](px|rem)' szczur-radar-web/src/views/FeedView.vue szczur-radar-web/src/components/PostCard.vue` zwraca 0 dla obu plików
- `FeedView` i `PostCard` importują komponenty: `grep -c "StatusMessage\|AppButton" szczur-radar-web/src/views/FeedView.vue` i `grep -c StatusBadge szczur-radar-web/src/components/PostCard.vue` zwracają co najmniej 1

#### Manual Verification:

- `/` z mockiem (tryb `data`) w obu motywach przy 1280 i 375 px: nagłówek posta wyróżniony wagą i kolorem, ocena 16 px, metadane i flagi 14 px, typ posta bez akcentu, stanowiska w kolorze roli, flagi jako znaczniki; bez poziomego przewijania przy 375 px; zrzuty w `ui-after/`
- Kitchen sink, sekcja „Posty”, w obu motywach: każdy wariant karty zgodny z powyższym

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 3: Stany i stronicowanie

### Overview

Zarzuty C3 i C4: jedno źródło stanu strony, zmiana strony bez starej listy, przewinięcia i utraty fokusu, strona poza zakresem, błąd z ponowieniem; macierz 7 stanów na zrzutach.

### Changes Required:

#### 1. Jedno źródło stanu strony

**File**: `szczur-radar-web/src/views/FeedView.vue`

**Intent**: Lista, numer strony i liczba stron pochodzą z tej samej odpowiedzi; nic nie miesza strony z adresu ze starą listą.

**Contract**: Widok trzyma ostatnią pokazaną stronę jako parę (żądany numer strony, odpowiedź `Page<PostItem>`) oraz stan wczytywania z żądanym numerem; etykieta `Strona X z Y` i przyciski liczą się z pokazanej pary. Każde wczytanie ma numer porządkowy; wynik lub błąd wczytania innego niż ostatnie jest ignorowany. `page` z adresu (`?page=`, od 0) i `PAGE_SIZE` 50 bez zmian; parsowanie `?page` bez zmian.

#### 2. Zmiana strony i stan ładowania

**File**: `szczur-radar-web/src/views/FeedView.vue`

**Intent**: Po „Starsze”/„Nowsze” (i wstecz/dalej) użytkownik widzi górę strony, wie, że nowa strona się wczytuje, a po wczytaniu fokus jest na początku listy.

**Contract**:
- Region listy: `section aria-labelledby` = `id` nagłówka `h1` („Posty”, `tabindex="-1"`), `aria-busy="true"` w trakcie każdego wczytywania.
- W trakcie wczytywania zamiast listy i stronicowania: `<p class="loading">` z tekstem „Wczytywanie…”, gdy nic nie jest jeszcze pokazane i żądana jest strona 1, w pozostałych przypadkach „Wczytywanie strony N…” (N od 1).
- Przy zmianie numeru strony (po pierwszym wejściu): `window.scrollTo({ top: 0 })` od razu; po udanym wczytaniu fokus na `h1`. Pierwsze wejście nie przewija i nie przenosi fokusu.

#### 3. Strona poza zakresem

**File**: `szczur-radar-web/src/views/FeedView.vue`

**Intent**: Stary link do dalszej strony mówi, co się stało, i daje jedno wyjście.

**Contract**: Gdy pokazana odpowiedź ma `items` puste i `total > 0`: `<p class="empty">Nie ma strony {N}. Posty kończą się na stronie {ostatnia}.</p>` oraz `AppButton` „Przejdź do strony {ostatnia}”, który ustawia `?page` na ostatnią stronę. Bez paska stronicowania. `total === 0` zostaje zwykłym stanem pustym niezależnie od numeru strony.

#### 4. Błąd z ponowieniem

**File**: `szczur-radar-web/src/views/FeedView.vue`

**Intent**: Chwilowy błąd API nie zostawia pustej strony bez przycisku.

**Contract**: `StatusMessage tone="error"`: „Nie udało się wczytać postów.” i pod nim `AppButton` „Spróbuj ponownie”, który wczytuje tę samą stronę. Wzorzec `AdminView.vue:185-186`, `:222`. Bez paska stronicowania w stanie błędu.

#### 5. Testy stanów

**File**: `szczur-radar-web/src/views/FeedView.test.ts`

**Intent**: Każda poprawka C3/C4 ma test, który dziś by nie przeszedł.

**Contract**: Nowe przypadki: (a) w trakcie wczytywania strony 2 nie ma `article` strony 1, jest „Wczytywanie strony 2…”, sekcja ma `aria-busy="true"`, nie ma `.pager`; (b) zmiana strony woła `window.scrollTo` z `top: 0`, pierwsze wejście nie woła; (c) po wczytaniu strony 2 `document.activeElement` to `h1`; (d) `/?page=9` przy `total` 120 pokazuje komunikat o stronie 10 i przycisk, kliknięcie ustawia `?page=2`; (e) odpowiedź strony 2 rozwiązana po odpowiedzi strony 3 nie zmienia widoku strony 3; (f) błąd pokazuje `[role="alert"]` i „Spróbuj ponownie”, kliknięcie przy udanej odpowiedzi pokazuje posty. `window.scrollTo` stubowany w `beforeEach`.

#### 6. Macierz 7 stanów na zrzutach

**File**: `context/changes/feed-view-ui-hardening/ui-after/` (zrzuty + `README.md`)

**Intent**: Dowód bramki wizualnej do przeglądu.

**Contract**: Zrzuty 1280×900 i 375×812, jasny i ciemny: `/` w trybach `data`, `loading`, `error`, `empty` (`dev/mock-api.js`), zmiana strony w `loading-next` i `error-next`, `/?page=9`, hover i fokus na stronicowaniu, kitchen sink sekcja „Posty”. `README.md`: tabela 7 stanów → plik albo N/A z powodem (hover i fokus karty: N/A do S-03, karta nie ma kontrolek; hover przy 375 px: N/A, ekran dotykowy), porównanie z `ui-before/`, liczniki `px` w piśmie i odstępach przed/po (23 → 0), pomiar `scrollY` i `document.activeElement` po zmianie strony.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą, w tym przypadki (a)–(f): `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`

#### Manual Verification:

- `ui-after/README.md` pokazuje każdą z 7 komórek macierzy (default, hover, focus-visible, disabled, error, empty, loading) na zrzucie albo jako N/A z powodem, w obu motywach i przy 375 px
- Z mockiem przy 1280×900: „Starsze” na dole strony 1 → od razu góra strony z „Wczytywanie strony 2…”, po wczytaniu `scrollY` 0 i fokus na „Posty”; `/?page=9` pokazuje komunikat i przycisk do strony 3; `error-next` pokazuje „Spróbuj ponownie”

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 4: Utrwalenie i weryfikacja

### Overview

Reguła dla następnego agenta, lista kontrolna `/10x-ui` i sprawdzenie na prawdziwych danych w Railway PR env.

### Changes Required:

#### 1. Reguła UI w repo

**File**: `REPOSITORY_GUIDELINES.md` (sekcja „UI (szczur-radar-web)”, poza blokiem `@przeprogramowani/10x-cli`)

**Intent**: Reguła mówi dokładnie to, co sprawdza test, i nazywa wyjątek `LoginView`.

**Contract**: Punkt o literałach (`:45`): skan `no literals` w `src/styleContract.test.ts` odrzuca w plikach z listy kolory, klasy palety, wartości arbitralne oraz `px`/`rem` w piśmie, odstępach i promieniach (obramowania i szerokości układu dozwolone); plik trafia na listę po oczyszczeniu z obu; `LoginView.vue` jest poza listą do `login-view-ui-hardening`. Punkt o zrzutach (`:46`): `dev/mock-api.js` obsługuje `/admin` i `/`. Punkt o komponentach (`:44`) wymienia `PostCard` (karta posta, w kitchen sink).

#### 2. Lista kontrolna i porównanie

**File**: `context/changes/feed-view-ui-hardening/ui-after/README.md`

**Intent**: Wynik `ui-quality-checklist` przy zmianie, do przeglądu.

**Contract**: Każdy punkt `.claude/skills/10x-ui/references/ui-quality-checklist.md` odhaczony albo N/A z powodem; deferred D1–D4 z celami (S-03, S-04, S-04, poza zakresem); wynik PR env (data, adres, commit web, liczba postów, zrzuty).

### Success Criteria:

#### Automated Verification:

- `REPOSITORY_GUIDELINES.md` opisuje skan skali i wyjątek `LoginView`: `grep -c 'login-view-ui-hardening' REPOSITORY_GUIDELINES.md` zwraca co najmniej 1
- Testy i build web przechodzą: `cd szczur-radar-web && npm test && npm run build`
- CI na PR jest zielone: `gh pr checks <PR>`

#### Manual Verification:

- `ui-after/README.md` zawiera odhaczoną listę `ui-quality-checklist` (lub N/A z powodem)
- `/` w Railway PR env (prawdziwe dane, zalogowany użytkownik) w obu motywach i przy 375 px wygląda jak zrzuty z mocka; „Starsze” prowadzi na górę następnej strony

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka. Potem `/10x-impl-review` (z opcjonalnym przebiegiem layoutu `design-taste-frontend` na zbudowanym widoku) i `/10x-archive` na tej samej gałęzi; merge robi człowiek.

---

## Testing Strategy

### Unit Tests:

- `styleContract.test.ts`: kontrast par ról w obu motywach (bez zmian); `no literals` z wzorcem kolorów i skali dla plików `CLEANED` bez `LoginView`.
- `PostCard.test.ts`: typ jako tekst, stanowisko (klasa i tekst), flagi jako `StatusBadge neutral`, notka prefiltra, karta bez oceny.
- `FeedView.test.ts`: dotychczasowe przypadki z tymi samymi selektorami; stan pusty z nowym tekstem; (a)–(f) z fazy 3.

### Integration Tests:

- Build produkcyjny bez kitchen sink (`grep` na `dist/`); CI z prawdziwym Dockerfile web (`web-container-smoke`).

### Manual Testing Steps:

1. `cd szczur-radar-web && npm ci && npm run dev`; z katalogu głównego `playwright-cli open --config=<główny checkout>/.playwright/cli.config.json`, `run-code --filename=szczur-radar-web/dev/mock-api.js`, `goto http://localhost:5173/`.
2. Tryby `data`, `loading`, `error`, `empty`, `loading-next`, `error-next` (podmiana `MODE`): zrzut 1280×900 i 375×812, `set-color-scheme light` i `dark`.
3. Zmiana strony z dołu strony 1: `eval` `scrollY` i `document.activeElement.tagName` po wczytaniu.
4. `/?page=9`; Tab przez widok: „Posty”, „Wyloguj”, „Starsze” z widocznym fokusem.
5. PR env: `/` na prawdziwych danych, oba motywy, 375 px, zmiana strony.

## Performance Considerations

Brak istotnych: te same żądania co dziś (jedno na stronę); znika rendering 50 starych kart w trakcie wczytywania; kitchen sink nie trafia do bundla produkcyjnego.

## Migration Notes

Brak danych do migracji. Wycofanie = revert commitów tej gałęzi. `LoginView` wraca na listę `CLEANED` w swojej zmianie.

## References

- Research: `context/changes/feed-view-ui-hardening/research.md` (C1–C4, D1–D4, macierz „przed”, zrzuty `ui-before/`, `mock-feed.js`)
- Kontrakt UI: `.claude/skills/10x-ui/SKILL.md`, `.claude/skills/10x-ui/references/ui-quality-checklist.md`
- Poprzednia zmiana `/10x-ui`: `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md` (tokeny, komponenty, kontrola, kitchen sink)
- Wzorzec ładowania i ponowienia: `szczur-radar-web/src/views/AdminView.vue:185-186`, `:216-222`
- Kontrakt API feedu: `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:43-52`
- PR env: `context/deployment/deploy-plan.md` (F-01 „The PR env had no database”, „PR-env verification: the web deployment's commit”)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Środowisko i bramka

#### Automated

- [ ] 1.1 Testy web przechodzą
- [ ] 1.2 Typecheck i build przechodzą, a `dist/` nie zawiera kitchen sink
- [ ] 1.3 `dev/mock-api.js` parsuje się i obsługuje `/api/posts`

#### Manual

- [ ] 1.4 `/` z `dev/mock-api.js` wygląda jak `ui-before/feed-data-1280-light-viewport.png`; kitchen sink pokazuje sekcję „Posty” w obu motywach

### Phase 2: Karta i widok na kontrakcie

#### Automated

- [ ] 2.1 Testy web przechodzą, w tym rozszerzony skan `no literals` i `PostCard.test.ts`
- [ ] 2.2 Typecheck i build przechodzą
- [ ] 2.3 Brak `px`/`rem` w piśmie i odstępach feedu
- [ ] 2.4 `FeedView` i `PostCard` importują komponenty

#### Manual

- [ ] 2.5 `/` z mockiem w obu motywach przy 1280 i 375 px: nowa hierarchia karty, typ bez akcentu, stanowiska w kolorze roli, flagi jako znaczniki; zrzuty w `ui-after/`
- [ ] 2.6 Kitchen sink, sekcja „Posty”, w obu motywach: każdy wariant karty zgodny

### Phase 3: Stany i stronicowanie

#### Automated

- [ ] 3.1 Testy web przechodzą, w tym przypadki (a)–(f)
- [ ] 3.2 Typecheck i build przechodzą

#### Manual

- [ ] 3.3 `ui-after/README.md` pokazuje 7 komórek macierzy w obu motywach i przy 375 px
- [ ] 3.4 Zmiana strony z mockiem: góra strony, „Wczytywanie strony 2…”, fokus na „Posty”; `/?page=9` z przyciskiem; `error-next` z „Spróbuj ponownie”

### Phase 4: Utrwalenie i weryfikacja

#### Automated

- [ ] 4.1 `REPOSITORY_GUIDELINES.md` opisuje skan skali i wyjątek `LoginView`
- [ ] 4.2 Testy i build web przechodzą
- [ ] 4.3 CI na PR jest zielone

#### Manual

- [ ] 4.4 `ui-after/README.md` zawiera odhaczoną listę `ui-quality-checklist`
- [ ] 4.5 `/` w Railway PR env w obu motywach i przy 375 px wygląda jak zrzuty z mocka; „Starsze” prowadzi na górę następnej strony
