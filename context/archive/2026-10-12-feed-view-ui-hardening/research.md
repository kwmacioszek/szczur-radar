---
date: 2026-10-12T17:18:28+02:00
researcher: Claude Code (claude-opus-5-5)
git_commit: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662
branch: feature/feed-view-ui-hardening
repository: szczur-radar
topic: "Audyt UI feedu (/) według /10x-ui — zarzuty przed planem"
tags: [research, ui, szczur-radar-web, FeedView, PostCard, style.css, design-tokens]
status: complete
last_updated: 2026-10-12
last_updated_by: Claude Code (claude-opus-5-5)
---

# Research: Audyt UI feedu (/)

**Date**: 2026-10-12T17:18:28+02:00
**Researcher**: Claude Code (claude-opus-5-5)
**Git Commit**: 6bebf82f8cfa76fe1661814e530fdb5d2a0a5662 (pliki `szczur-radar-web/` bez lokalnych zmian względem tego commitu; `git diff HEAD -- szczur-radar-web` pusty)
**Branch**: feature/feed-view-ui-hardening
**Repository**: szczur-radar

## Research Question

Dwukierunkowy audyt jednego widoku — feedu `/` (`szczur-radar-web/src/views/FeedView.vue` z `szczur-radar-web/src/components/PostCard.vue`) — według `/10x-ui`: 3–5 zarzutów z plik:linia i wpływem na użytkownika, w kategoriach brakujące tokeny / brakujący współdzielony komponent / przypadkowa architektura, oraz macierz 7 stanów „przed”. Wariant kontraktu: istniejący system projektowy (role i skale w `:root` `src/style.css`, komponenty w `src/components/`), bez nowych zależności. Granice: S-03 (widok szczegółowy, link do oryginału), S-04 (filtry), S-05 (data świeżości) — `change.md`.

## Summary

Feed renderuje się z prawdziwego kontraktu API i ma stany pusty, błędu i ładowania, ale jego kod jest sprzed kontraktu z `admin-panel-ui-hardening`: kolory są już rolami (skan `/10x-ui`: 0 trafień w obu plikach), a odstępy i pismo — literałami. Oba pliki są na liście `CLEANED` testu `styleContract.test.ts:85-95`, choć reguła `REPOSITORY_GUIDELINES.md:45` wymaga skali, której nie używają. Cztery zarzuty:

1. **C1 — brakujące tokeny skali** (brakujące tokeny): 27 linii `px`/`rem` w `<style>` obu plików (FeedView 6, PostCard 21), w karcie 3 z 4 rozmiarów pisma spoza skali (13, 15, 18 px), odstęp 6 px spoza skali, linia między postami z własnego `color-mix` zamiast `--border`. Kontrola przepuszcza to, bo skanuje tylko kolory.
2. **C2 — brakujące współdzielone komponenty** (brakujący komponent): błąd jako goły `<p role="alert">`, stronicowanie na surowych `<button>`, flagi jako ręczny znacznik `.flag`, stanowisko jako kolorowy `<span>` — `AppButton`, `StatusMessage` i `StatusBadge` mają tu 0 importów.
3. **C3 — stronicowanie bez stanu** (przypadkowa architektura): w trakcie wczytywania kolejnej strony etykieta mówi już „Strona 2 z 3”, a lista nadal pokazuje posty strony 1; po wczytaniu przewinięcie zostaje na dole nowej strony, a fokus klawiatury spada na `body`; strona spoza zakresu (`/?page=9`) pokazuje „Strona 10 z 3” bez postów i bez komunikatu.
4. **C4 — błąd i ładowanie bez wyjścia** (przypadkowa architektura): błąd zastępuje listę i stronicowanie, nie ma „Spróbuj ponownie”, a podpowiedź „odśwież stronę” odświeża ten sam adres; ładowanie to sam tekst bez `aria-busy`.

Wejścia przez router są poprawne (bez sesji → `/login?redirect=…`, `router/index.ts:51-53`). Bramka wizualna z poprzedniej zmiany nie obejmuje feedu: `dev/mock-api.js` odpowiada na `/api/posts` 404, a kitchen sink nie ma `PostCard`. Dowody wizualne: `ui-before/*.png`, odtwarzalne skryptem `mock-feed.js` z tego folderu.

## Przed-audyt i metoda

- **Pliki widoku**: `src/views/FeedView.vue` (91 linii), `src/components/PostCard.vue` (113 linii). `AppHeader.vue` renderuje się w widoku, ale jest już na kontrakcie (`AppHeader.vue:45-80` — same tokeny); poza zakresem.
- **Źródło wartości**: `src/style.css:8-54` — role kolorów, skale `--font-size-*` (14/16/20/32/44 px, `:23-27`), `--space-*` (2/4/8/12/16/24 px, `:29-34`), `--radius` 8 px i `--radius-sm` 6 px (`:36-37`); ciemny motyw redefiniuje tylko role (`:56-67`). Globalne style elementów: `h1` (`:73-78`), `button` z hover i disabled (`:123-141`), `:focus-visible` z `--ring` (`:144-147`).
- **Katalog komponentów**: `src/components/` — `AppButton.vue`, `StatusMessage.vue`, `StatusBadge.vue`, `AppHeader.vue`, `PostCard.vue`.
- **Pliki reguł agenta**: `REPOSITORY_GUIDELINES.md:41-47` (sekcja UI: tokeny, komponenty, zakaz literałów, skala, kitchen sink, 7 stanów, `/10x-ui`); `CLAUDE.md` i `AGENTS.md` dołączają go i odsyłają do `/10x-ui`. Brak reguły zachęcającej do jednorazowych wartości. Lintera nie ma (`REPOSITORY_GUIDELINES.md:34`); kontrolą jest test Vitest `src/styleContract.test.ts`.
- **Skan `/10x-ui`** (kolory, klasy palety, wartości arbitralne) na obu plikach: 0 trafień.
- **Źródło → widok**: odczyty `var(--…)` — `FeedView.vue` 2 (`:77` `--text`, `:81` `--destructive`), `PostCard.vue` 7 (`:47`, `:60`, `:67`, `:93`, `:98`, `:102`, `:109`); żaden nie czyta `--space-*`, `--font-size-*`, `--radius*` ani `--border`. Importy z `src/components/`: `FeedView.vue:5-6` (`AppHeader`, `PostCard`); `AppButton`/`StatusMessage`/`StatusBadge` — 0 w obu plikach. Dla porównania `AdminView.vue` ma 20 odczytów i importuje te trzy komponenty (`:14-17`).
- **Render**: Vite dev (port 5181) + playwright-cli (Chromium, `--config` z głównego checkoutu, sesja `feed`) z mockiem `/api/*` z `mock-feed.js` (tryby `data`, `loading`, `error`, `empty`, `error-next`, `loading-next`, `slow`; 120 postów = 3 strony po 50; strona poza zakresem odpowiada jak API: 200, `items: []`, prawdziwy `total` — `PostController.java:45-52`). Rozmiary 1280×900 i 375×812, motyw `set-color-scheme light|dark`. Rola `user` (`/api/auth/me`). Pomiary (`scrollY`, `activeElement`, `aria-busy`, `scrollWidth`) przez `playwright-cli eval`.

## Charges

### C1 — Brakujące tokeny: skala pisma i odstępów, linia podziału, kontrola

- **Kategoria**: brakujące tokeny.
- **Dowody (widok → źródło)**:
  - Literały w `<style>`: `FeedView.vue` 6 linii (`:65`, `:67`, `:72`, `:73`, `:88`, `:89`), `PostCard.vue` 21 linii (`:46`, `:47`, `:52`, `:53`, `:54`, `:59`, `:64`, `:65`, `:73`, `:74`, `:78`, `:79`, `:83`, `:88`, `:89`, `:94`, `:107`, `:108`, `:109`, `:110`, `:111`). Na odstępy i pismo (`font-size|margin|padding|gap|letter-spacing` z `px`) przypada 5 linii w FeedView i 18 w PostCard.
  - Pismo karty: `.meta` 14px (`PostCard.vue:54`), nagłówek 18px (`:65`), trafność i cele 15px (`:74`, `:89`), notka i flaga 13px (`:79`, `:111`). Skala ma 14/16/20/32/44 (`style.css:23-27`), więc 13, 15 i 18 są poza nią. Bazowy tekst aplikacji to 18px powyżej 1024 px szerokości i 16px poniżej (`style.css:41`, `:51-53`).
  - Odstęp 6px (`PostCard.vue:78`, `:88`, `:107`, `:108`) — poza skalą 2/4/8/12/16/24 (`style.css:29-34`). Pozostałe odstępy (4/8/12/16 px) mają odpowiedniki `--space-1..4`, promień 6px (`:110`) — `--radius-sm`.
  - Linia między postami: `color-mix(in srgb, var(--text) 20%, transparent)` (`PostCard.vue:47`) obok tokenu `--border` = to samo z 25% (`style.css:19`), którego używają `AppHeader.vue:52` i `AdminView.vue:348`.
  - `h1` w FeedView ma `font-size: 32px` (`FeedView.vue:73`), a globalny `h1` daje już `var(--font-size-xl)` = 32px (`style.css:73-78`) — powtórzenie z czasu, gdy globalny `h1` miał 44px (research admina, `context/archive/2026-10-10-admin-panel-ui-hardening/research.md:56`).
  - **Kontrola przepuszcza**: oba pliki są na liście `CLEANED` (`styleContract.test.ts:92`, `:94`), a skan `LITERAL` (`:98-99`) łapie tylko kolory, klasy palety i `-[Npx]`. Reguła mówi „spacing and font sizes come from the scale … The `no literals` scan … enforces this for the files on its list — add a file once it is cleaned” (`REPOSITORY_GUIDELINES.md:45`). Pliki dodano w admin-panel-ui-hardening po samej podmianie kolorów (`context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:257`).
  - Wśród pozostałych plików z listy `CLEANED` `px` w piśmie i odstępach ma tylko `LoginView.vue` (`:77`, `:84`, `:86`, `:91`, `:100` — następna zmiana `login-view-ui-hardening`); `AdminView`, `AppHeader`, `AppButton`, `StatusMessage`, `StatusBadge`, `KitchenSinkView` nie mają `px` w piśmie ani odstępach — tylko obramowania 1px/4px i szerokości w `rem` (wyszukiwanie `(font-size|margin|padding|gap|letter-spacing)…px` na tych 7 plikach: 5 trafień, wszystkie w LoginView).
- **Wpływ na użytkownika**: na desktopie treść oceny — trafność, cele ze stanowiskiem (15px) i flagi (13px) — jest drukowana mniejszym pismem niż reszta aplikacji (bazowe 18px, najmniejszy krok skali 14px), więc najważniejsza część feedu czyta się najtrudniej, a flagi i notka prefiltra (13px) są najmniejszym tekstem SPA — poza `PostCard.vue` najmniejszy `font-size` w `szczur-radar-web/src/` to 14px (wyszukiwanie `font-size` w `src/` bez testów) (`feed-data-1280-light-viewport.png`). Karta ma cztery rozmiary pisma, z czego trzy spoza skali, więc nowe widoki (S-03, S-04) nie mają z czego dziedziczyć.
- **Naprawa (kierunek)**: odstępy, pismo i promień karty i widoku z `--space-*`, `--font-size-*`, `--radius-sm`; linia z `--border`; usunąć powtórzony `font-size` `h1`. Ewentualny brakujący krok skali (np. między 16 a 20 dla nagłówka posta) dodać do `:root`, nie do widoku. Kontrola: zaostrzyć skan dla oczyszczonych plików o `px` w piśmie i odstępach (Open Questions 4).

### C2 — Brakujące współdzielone komponenty: komunikat, przycisk, znacznik

- **Kategoria**: brakujący współdzielony komponent.
- **Dowody (duplikat → komponent)**:
  - Błąd: `<p v-if="error" class="error" role="alert">` (`FeedView.vue:47`) z własnym kolorem (`:80-82`) — `StatusMessage` z tonem `error` daje `role="alert"`, zabarwione tło i lewą krawędź (`StatusMessage.vue:7`, `:14-29`), jak w panelu admina (`AdminView.vue:222`, `:254`).
  - Stronicowanie: dwa surowe `<button type="button">` (`FeedView.vue:55`, `:57`) — wyglądają jak wariant pomocniczy `AppButton` przez globalny `button` (`style.css:123-141`), ale nie mają `busy`/`aria-busy` (`AppButton.vue:9-11`, `:23`), więc w trakcie wczytywania są tylko wyłączone.
  - Flagi: `<span class="flag">` z ręcznym obramowaniem 1px `--text`, promieniem 6px, paddingiem 0 6px i pismem 13px (`PostCard.vue:37-39`, `:105-112`) — odpowiednik `StatusBadge` z tonem `neutral` (`StatusBadge.vue:14-21`, `:35-37`: `--space-2`, `--radius-sm`, `--font-size-sm`, obramowanie i tekst w kolorze tonu). Ten sam pomysł (stan jako tekst w obwódce) ma już dwie implementacje o różnych wymiarach.
  - Stanowisko: `<span class="stance" :class="direction">` z kolorem tylko dla `bullish`/`bearish` (`PostCard.vue:32-34`, `:97-103`); tekst („wzrostowo”, „spadkowo (ostrożnie)”) niesie znaczenie, kolor je wzmacnia. Plan admina projektował tony `StatusBadge` tak, „żeby PostCard mógł użyć `StatusBadge` dla »wzrostowo« / »spadkowo«” (`context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:54`).
- **Wpływ na użytkownika**: błąd feedu to jedna czerwona linia bez tła, inna niż komunikaty w panelu admina (`feed-error-1280-light.png` vs `AdminView`), więc łatwo ją wziąć za zwykły tekst; przy wolnej odpowiedzi przyciski stronicowania tylko szarzeją, bez informacji, że coś się dzieje. Flagi wyglądają prawie jak znaczniki panelu admina, ale mają mniejsze pismo i inne odstępy — ten sam pojęciowo element rozjeżdża się między widokami i będzie się rozjeżdżał dalej przy S-03.
- **Naprawa (kierunek)**: `StatusMessage` dla błędu, `AppButton` (z `busy` albo bez — decyzja planu) dla stronicowania, `StatusBadge` dla flag; stanowisko jako `StatusBadge` z tonem albo tekst w kolorze roli — decyzja planu (Open Questions 3). Nowych komponentów nie potrzeba; `PostCard` trafia do kitchen sink (reguła `REPOSITORY_GUIDELINES.md:44`: nowy współdzielony komponent idzie do kitchen sink — `PostCard` jest w `src/components/`, a kitchen sink go nie ma, `KitchenSinkView.vue:7-9`).

### C3 — Przypadkowa architektura: stronicowanie bez stanu

- **Kategoria**: przypadkowa architektura (stan strony wyprowadzany z dwóch źródeł, wejście z linku).
- **Dowody**:
  - **Stara lista pod nowym numerem.** Numer strony pochodzi z adresu (`FeedView.vue:18-21`), liczba stron z poprzedniej odpowiedzi (`:22`), a lista z `result`, które zostaje do nadejścia nowej odpowiedzi (`:13`, `:28`, `:52-53`). W trakcie wczytywania strony 2 widok pokazuje „Strona 2 z 3” nad postami strony 1 i oba przyciski wyłączone (`?page=1`, `disabled: [true, true]`, `aria-busy`: 0 elementów; `feed-paging-loading-1280-light.png`, tryb `loading-next`). Wniosek: po uśpieniu API pierwsze żądanie trwa ~9.6–10.5 s (`context/deployment/deploy-plan.md:118`), więc ten stan może trwać kilka sekund, jeśli użytkownik czytał stronę dłużej niż do uśpienia usługi.
  - **Przewinięcie zostaje na dole.** Router nie ma `scrollBehavior` (`router/index.ts:47`), a `goTo` zmienia tylko query (`FeedView.vue:36-38`). Zmierzone przy 1280×900: przed kliknięciem „Starsze” `scrollY` = 8213, po wczytaniu strony 2 `scrollY` = 8223 (wysokość dokumentu 9113 px) — użytkownik ląduje przy ostatnich postach nowej strony (`feed-after-older-click-1280-light.png`).
  - **Fokus spada na `body`.** Oba przyciski są `:disabled="… || loading"` (`FeedView.vue:55`, `:57`); wyłączony przycisk traci fokus. Zmierzone: fokus na „Starsze” (`BUTTON`) → Enter → `document.activeElement` = `BODY` w trakcie i po wczytaniu (tryb `slow`).
  - **Strona spoza zakresu.** API dla `page` poza końcem odpowiada 200 z pustą listą i prawdziwym `total` (`PostController.java:45-52`). Widok sprawdza stan pusty tylko dla `total === 0` (`FeedView.vue:49`), więc `/?page=9` przy 120 postach pokazuje sam nagłówek i pasek „Strona 10 z 3” (0 artykułów, „Nowsze” aktywny, „Starsze” wyłączony; `feed-out-of-range-1280-light.png`). „Nowsze” cofa o jedną stronę (`:55`), czyli przez 6 kolejnych pustych stron do strony 3. Taki adres powstaje z zakładki lub linku, gdy `total` zmaleje (po zmianie widoczności postów albo po odtworzeniu bazy — `deploy-plan.md`, sekcja F-01).
- **Wpływ na użytkownika**: po kliknięciu „Starsze” użytkownik przez czas ładowania czyta stare posty jako nowe, a potem trafia na koniec nowej strony i musi przewinąć ~8000 px w górę, żeby zobaczyć jej najnowsze posty; użytkownik klawiatury traci miejsce i nie słyszy, że strona się zmieniła. Stary link do dalszej strony wygląda jak zepsuta aplikacja: pusty ekran z „Strona 10 z 3”.
- **Naprawa (kierunek)**: jedno źródło stanu strony w widoku (lista, numer i liczba stron z tej samej odpowiedzi albo jawny stan „wczytywanie strony N”); po zmianie strony przewinięcie na początek listy i fokus na jej nagłówek lub region; strona poza zakresem → komunikat z przejściem na ostatnią/pierwszą stronę albo `router.replace` na ostatnią (decyzja planu, Open Questions 1–2).

### C4 — Przypadkowa architektura: błąd i ładowanie bez wyjścia

- **Kategoria**: przypadkowa architektura (stany wejścia).
- **Dowody**:
  - Błąd wypiera wszystko: `v-if="error"` stoi przed listą i stronicowaniem (`FeedView.vue:47-59`). Po błędzie strony 2 (tryb `error-next`) widok ma tylko nagłówek i komunikat, 0 przycisków w `main`, `scrollY` 0 (`feed-error-next-1280-light.png`). Komunikat każe „odświeżyć stronę” (`:30`) — odświeżenie ładuje ten sam `?page=1`.
  - Brak ponowienia: w widoku nie ma przycisku „Spróbuj ponownie”; panel admina ma ten wzorzec (`AdminView.vue:185-186`).
  - Ładowanie: `<p class="status">Wczytywanie…</p>` tylko gdy nie ma jeszcze żadnego wyniku (`FeedView.vue:48`), bez `aria-busy` — zmierzone 0 elementów `[aria-busy]` w trybach `loading`, `error`, `empty`, `data` i `loading-next`; panel admina oznacza sekcje `aria-busy` (`AdminView.vue:181`, `:216`, `:251`). Przy zmianie strony nie ma żadnego wskaźnika poza wyłączonymi przyciskami (C3).
  - Klasa `.status` ustawia kolor równy dziedziczonemu (`FeedView.vue:76-78`, `--text` jest kolorem `:root`, `style.css:44`) — reguła bez efektu.
- **Wpływ na użytkownika**: chwilowy błąd API (np. restart przy deployu — `REPOSITORY_GUIDELINES.md`, § Deployment) zostawia użytkownika na pustej stronie bez przycisku, a jedyna podpowiedź może dać ten sam błąd; czytnik ekranu nie dostaje informacji, że lista się wczytuje.
- **Naprawa (kierunek)**: błąd jako `StatusMessage` z „Spróbuj ponownie” (`AppButton`), bez gubienia stronicowania tam, gdzie jest co pokazać; `aria-busy` na regionie listy w trakcie każdego wczytywania; wzorzec z `AdminView`.

### Deferred (nie usuwać)

- **D1 — link do oryginału, autor i widok szczegółowy** → S-03 (`post-detail-view`). `PostItem` ma `webUrl` i `author` (`api/posts.ts:35-36`), karta ich nie pokazuje (`PostCard.vue:17-41`); nagłówek ucina tekst do 200 znaków (`:9-12`). Karta nie ma dziś żadnej kontrolki, więc jej hover i fokus są N/A do S-03.
- **D2 — filtry i stan pusty filtrów** → S-04 (`filter-by-assessment`); **data ostatniej udanej aktualizacji** nad listą → S-05 (`daily-scheduled-run-freshness`).
- **D3 — stronicowanie tylko pod listą.** 50 postów to ~9100 px przy 1280×900 (`scrollHeight` 9113), pasek jest tylko na dole (`FeedView.vue:54-58`). Drugi pasek nad listą albo mniejsza strona to decyzja układu; S-04 doda nad listą filtry, więc lepiej rozstrzygnąć to razem z nimi.
- **D4 — `AppHeader` przy 375 px**: e-mail i „Wyloguj” łamią się na dwa wiersze (`feed-data-375-light.png`) — komponent jest na kontrakcie, poza tym widokiem.

## Macierz 7 stanów — stan obecny

| Stan | Teraz | Dowód |
| --- | --- | --- |
| default | kolory z ról, ale pismo i odstępy z 23 literałów `px` (5 + 18), 3 z 4 rozmiarów pisma karty poza skalą, flagi i błąd ręcznie | C1, C2, `feed-data-1280-*-viewport.png`, `feed-data-375-*.png` |
| hover | tylko przyciski stronicowania, przez globalny `button:where(:hover…)` (`style.css:134-136`); karta nie ma kontrolek (N/A do S-03, D1) | `feed-hover-pager-1280-light.png` |
| focus-visible | pierścień z `--ring` na „Posty”, „Wyloguj”, „Starsze” (3× Tab, `style.css:144-147`); po zmianie strony fokus spada na `body` | `feed-focus-pager-1280-light.png`, `-dark.png`; C3 |
| disabled | `opacity: 0.6` globalnie (`style.css:138-141`); „Nowsze” na stronie 1 czytelny; w trakcie wczytywania oba wyłączone bez powodu na ekranie | `feed-pager-1280-light.png`, `feed-paging-loading-1280-light.png` |
| error | czerwony tekst `role="alert"` bez tła, bez ponowienia; wypiera listę i stronicowanie | C2, C4, `feed-error-*.png`, `feed-error-next-1280-light.png` |
| empty | dla `total === 0` jest („Brak ocenionych postów. Pojawią się po pierwszym przebiegu.”); dla strony poza zakresem brak | `feed-empty-*.png`, C3, `feed-out-of-range-1280-light.png` |
| loading | pierwsze wejście: sam tekst „Wczytywanie…”, bez `aria-busy`; zmiana strony: stara lista pod nowym numerem | C3, C4, `feed-loading-*.png`, `feed-paging-loading-1280-light.png` |

Mobile 375×812: bez poziomego przewijania w trybach `loading`, `error`, `empty`, `data` (`scrollWidth` = `clientWidth` = 375); długi URL bez spacji łamie się dzięki `overflow-wrap: anywhere` (`PostCard.vue:68`). Ciemny motyw: kolory przełączają się z rolami (`feed-data-1280-dark-viewport.png`); nowych par kolorów ta zmiana nie wprowadza, istniejące pary są w teście kontrastu (`styleContract.test.ts:21-29`). Nazwy dostępne: kontrolki widoku to link „Posty”, „Wyloguj”, „Nowsze”, „Starsze” — każda ma nazwę z tekstu.

## Środowisko bramki wizualnej

- `szczur-radar-web/dev/mock-api.js` zna tylko `/api/auth/me` i trasy `/api/admin/*`; inne ścieżki, w tym `/api/posts`, dostają 404 (`dev/mock-api.js:51`), więc feed na tym mocku pokazuje błąd. Procedura w jego nagłówku i reguła (`REPOSITORY_GUIDELINES.md:46`) wskazują go jako narzędzie zrzutów prawdziwych widoków.
- `mock-feed.js` w tym folderze to dowód researchu: tryby `data`, `loading`, `error`, `empty`, `error-next`, `loading-next`, `slow`. Plan zdecyduje, czy feed trafia do `dev/mock-api.js` (jeden mock z trasą `/api/posts`), czy zostaje osobny plik.
- Kitchen sink (`KitchenSinkView.vue`) pokazuje tokeny, przyciski, komunikaty, znaczniki i pola (`:54-90`), bez `PostCard`.
- W worktree trzeba `npm ci` w `szczur-radar-web/` przed `npx vite` (brak `node_modules` w świeżym worktree); `.playwright-cli/` jest w `.gitignore` (`:29`).

## Inne ustalenia (poza zarzutami)

- Typ posta („WPIS”, „ZNALEZISKO”) jest w kolorze `--accent` (`PostCard.vue:57-61`) — tym samym, co linki i przyciski, choć nie jest klikalny. Ten sam wzorzec ma `.eyebrow` logowania (`style.css:96-102`). Do decyzji w planie; kontrast spełniony (`--accent` na `--bg` jest w `PAIRS`).
- Tekst stanu pustego mówi o „przebiegu” (`FeedView.vue:50`) — pojęcie z panelu admina; dla roli `user` to żargon. Treść, nie wygląd.
- Cele bez stanowiska kierunkowego (`neutral`, `mixed`, `not_expressed`, `unclear`) mają kolor dziedziczony `--text` (`PostCard.vue:97-103` stylizuje tylko `bullish`/`bearish`) — spójne z „kolor tylko wzmacnia”, ważne dla decyzji o `StatusBadge` (C2).
- Każdy post ma `<h2>` z tytułem albo początkiem treści (`PostCard.vue:23`), 50 na stronę — nawigacja nagłówkami czytnika ekranu przechodzi po postach; zostaje.

## Zrzuty `ui-before/`

| Plik | Co pokazuje |
| --- | --- |
| `feed-data-1280-light-viewport.png`, `feed-data-1280-dark-viewport.png` | stan domyślny, górna część, oba motywy |
| `feed-data-1280-light.png`, `feed-data-1280-dark.png` | cała strona 1 (9113 px) |
| `feed-data-375-light.png`, `feed-data-375-dark.png` | stan domyślny przy 375 px |
| `feed-prefilter-url-1280-light.png` | post z prefiltra („bez treści, bez oceny modelu”), długi URL, cztery cele z różnymi stanowiskami |
| `feed-pager-1280-light.png` | dół strony 1, „Nowsze” wyłączony |
| `feed-hover-pager-1280-light.png` | hover na „Starsze” (strona 2) |
| `feed-focus-pager-1280-light.png`, `feed-focus-pager-1280-dark.png` | pierścień fokusu na „Starsze” |
| `feed-paging-loading-1280-light.png` | C3: „Strona 2 z 3” nad postami strony 1 w trakcie wczytywania |
| `feed-after-older-click-1280-light.png` | C3: po wczytaniu strony 2 widok zostaje na dole |
| `feed-out-of-range-1280-light.png` | C3: `/?page=9` → „Strona 10 z 3”, brak postów i komunikatu |
| `feed-error-next-1280-light.png` | C4: błąd strony 2 bez stronicowania i ponowienia |
| `feed-loading-{1280,375}-{light,dark}.png` | ładowanie pierwszego wejścia |
| `feed-error-{1280,375}-{light,dark}.png` | błąd pierwszego wejścia |
| `feed-empty-{1280,375}-{light,dark}.png` | stan pusty (`total` 0) |

## Code References

- `szczur-radar-web/src/views/FeedView.vue:13-22` — `result`, `loading`, `error`; numer strony z adresu, liczba stron z poprzedniej odpowiedzi.
- `szczur-radar-web/src/views/FeedView.vue:24-38` — `load()`, komunikat błędu, `goTo()` bez przewinięcia.
- `szczur-radar-web/src/views/FeedView.vue:47-58` — kolejność stanów: błąd, ładowanie, pusto, lista + stronicowanie.
- `szczur-radar-web/src/views/FeedView.vue:63-91` — style widoku (literały, powtórzony `h1`, `.status`).
- `szczur-radar-web/src/components/PostCard.vue:17-41` — szablon karty (typ, nagłówek, trafność, cele, flagi).
- `szczur-radar-web/src/components/PostCard.vue:44-113` — style karty (literały, `color-mix`, `.flag`, `.stance`).
- `szczur-radar-web/src/style.css:8-54` — `:root`: role i skale.
- `szczur-radar-web/src/style.css:123-147` — globalny `button`, hover, disabled, `:focus-visible`.
- `szczur-radar-web/src/styleContract.test.ts:85-99` — lista `CLEANED` i skan `LITERAL`.
- `szczur-radar-web/src/components/StatusMessage.vue`, `StatusBadge.vue`, `AppButton.vue` — komponenty do użycia.
- `szczur-radar-web/src/views/AdminView.vue:181-186`, `:216-222`, `:251-254` — wzorzec `aria-busy`, ponowienia i `StatusMessage`.
- `szczur-radar-web/src/router/index.ts:46-61` — `createRouter` bez `scrollBehavior`, guard sesji.
- `szczur-radar-web/src/views/FeedView.test.ts:60-108` — testy, które polegają na `.empty`, `.position`, `.pager button`, `[role="alert"]` i `article` (refaktor zachowuje albo aktualizuje te selektory).
- `szczur-radar-web/dev/mock-api.js:51` — 404 dla `/api/posts`.
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:43-52` — `page` od 0, `size` 1–100, strona poza zakresem = pusta lista z `total`.

## Architecture Insights

- Kontrakt UI ma dwie warstwy egzekwowania: reguła (`REPOSITORY_GUIDELINES.md:41-47`) i test (`styleContract.test.ts`). Test pokrywa kolory i kontrast, nie skalę — lista `CLEANED` znaczy dziś „bez literałów kolorów”, a reguła czyta ją jako „oczyszczony”. Ta rozbieżność pozwoliła dwóm plikom feedu przejść jako oczyszczone.
- Komponenty współdzielone powstały w panelu admina pod jego potrzeby, ale z tonami ogólnymi (`StatusBadge`: `success`/`warning`/`destructive`/`neutral`/`info`), więc feed może ich użyć bez nowych komponentów.
- Stan widoku listy jest wyprowadzany z adresu (strona) i z ostatniej odpowiedzi (lista, `total`) bez stanu „która strona jest na ekranie” — źródło C3. S-04 dołoży do adresu filtry, więc ten sam wzorzec obejmie też je.

## Historical Context (from prior changes)

- `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:39` — „Migracja `FeedView`, `LoginView`, `PostCard` na nowe komponenty — następne zmiany `/10x-ui`: **`feed-view-ui-hardening`** (FeedView + PostCard, przed S-03 i S-04 …)”. **Supported**: ta zmiana.
- `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:54` — tony `StatusBadge` ogólne, „żeby PostCard mógł użyć `StatusBadge` dla »wzrostowo« / »spadkowo«”. **Supported** (`StatusBadge.vue:3`); dziś niewykorzystane.
- `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:168-188`, `:458` — w fazie 2 FeedView, LoginView i PostCard dostały tylko podmianę 5 literałów kolorów na role; skan kolorów na nich zwraca 0. **Supported** (skan w tym researchu: 0 trafień w FeedView i PostCard).
- `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:257` — lista plików testu `no literals` z FeedView i PostCard. **Supported** (`styleContract.test.ts:92`, `:94`); twierdzenie reguły, że pliki z listy są oczyszczone ze skali — **contradicted** dla tych dwóch plików (C1).
- `context/archive/2026-10-10-admin-panel-ui-hardening/research.md:78` — „`FeedView` ma to rozwiązane (`FeedView.vue:14`, `:48`: `loading` i »Wczytywanie…«)”. **Partial**: pierwsze wejście ma tekst ładowania; zmiana strony nie ma stanu ładowania, a żadne wczytywanie nie ma `aria-busy` (C3, C4).
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:51`, `:462` — feed pod `/`, chronologicznie, stronicowany `page`/`size`; komponent elementu listy. **Supported**.
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:658` — „wąski ekran i dopracowanie UI odłożone”. **Supported**; wąski ekran feedu nie ma dziś poziomego przewijania (pomiar 375 px wyżej).

## Related Research

- `context/archive/2026-10-10-admin-panel-ui-hardening/research.md` — audyt `/admin` według `/10x-ui`, z którego pochodzą tokeny, komponenty i kontrola.
- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md` — powstanie feedu i API `/api/posts` (S-02).

## Open Questions

1. **Zmiana strony**: zachować starą listę z wyraźnym stanem „wczytywanie strony N” (`aria-busy`, przyciski w toku), czy od razu zastąpić ją stanem ładowania? Po wczytaniu: przewinięcie na początek listy i fokus na nagłówek listy — do potwierdzenia w planie.
2. **Strona poza zakresem**: `router.replace` na ostatnią istniejącą stronę, czy komunikat z przejściem do strony 1?
3. **Stanowisko**: `StatusBadge` z tonem (`success` dla wzrostowo, `destructive` dla spadkowo, `neutral` dla pozostałych), czy tekst w kolorze roli jak dziś? Druga opcja zostawia stanowisko jako zwykły tekst obok nazwy celu; pierwsza zrównuje je wizualnie z flagami.
4. **Kontrola skali**: zaostrzyć skan w `styleContract.test.ts` o `px` w `font-size|margin|padding|gap|letter-spacing` — dla wszystkich plików `CLEANED` (wtedy `LoginView` wypada z listy do swojej zmiany) czy dla osobnej listy plików oczyszczonych ze skali?
5. **Nagłówek posta**: 18px nie ma kroku w skali — `--font-size-md` (16), `--font-size-lg` (20) czy nowy krok w `:root`?
6. **Mock**: rozszerzyć `dev/mock-api.js` o `/api/posts` z trybami feedu, czy dołożyć osobny `dev/mock-feed.js`?
7. **Treść**: tekst stanu pustego bez „przebiegu” i kolor typu posta (`--accent`) — w tej zmianie czy osobno?
