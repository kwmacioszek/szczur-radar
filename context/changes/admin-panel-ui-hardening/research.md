---
date: 2026-10-10T14:45:21+02:00
researcher: Claude Code (claude-opus-5-5)
git_commit: 77fde0ec45a375819de28711e460372c52a56034
branch: main
repository: szczur-radar
topic: "Audyt UI panelu admina (/admin) według /10x-ui — zarzuty przed planem"
tags: [research, ui, szczur-radar-web, AdminView, AppHeader, style.css, design-tokens]
status: complete
last_updated: 2026-10-10
last_updated_by: Claude Code (claude-opus-5-5)
---

# Research: Audyt UI panelu admina (/admin)

**Date**: 2026-10-10T14:45:21+02:00
**Researcher**: Claude Code (claude-opus-5-5)
**Git Commit**: 77fde0ec45a375819de28711e460372c52a56034 (pliki `szczur-radar-web/` bez lokalnych zmian względem tego commitu)
**Branch**: main
**Repository**: szczur-radar

## Research Question

Dwukierunkowy audyt jednego widoku — panelu admina `/admin` (`szczur-radar-web/src/views/AdminView.vue` z `AppHeader.vue`) — według `/10x-ui`: 3–5 zarzutów z plik:linia i wpływem na użytkownika, w kategoriach brakujące tokeny / brakujący współdzielony komponent / przypadkowa architektura. Wariant kontraktu: zwykłe zmienne CSS, rozszerzenie istniejącego `:root`, bez nowych zależności. Granica z S-06 (`pipeline-run-history-dashboard`).

## Summary

Panel renderuje się i wykonuje swoją pracę, ale jest zbudowany z literałów i stylów elementów, nie z kontraktu. Źródło wartości to `:root` w `src/style.css` z czterema kolorami (`style.css:1-31`); widok czyta z niego 3 razy (`var(--…)`) i importuje jeden komponent (`AppHeader`). Cztery zarzuty:

1. **C1 — brakujące tokeny ról** (kolory stanów, obramowania, `accent-color`, skala): kolory stanów nie zmieniają się w trybie ciemnym i mają tam kontrast 3.56–3.85:1; `--accent` w jasnym motywie ma 4.39:1 na tekście każdego przycisku.
2. **C2 — brakujący przycisk i komunikat stanu**: przycisk istnieje tylko jako globalny selektor `button`, bez hover i bez własnego focus; główna akcja „Uruchom” wygląda jak „Odśwież”; komunikaty `role="status|alert"` są pisane ręcznie 9 razy w 4 plikach.
3. **C3 — przypadkowa architektura wejścia**: brak stanu ładowania, więc przy wejściu panel twierdzi „Jeszcze nie było przebiegu.” i „Brak nieudanych postów.”; po błędzie listy źródeł formularz jest martwy, a „Odśwież” go nie naprawia.
4. **C4 — sygnał tylko kolorem**: nieudany krok przebiegu jest oznaczony wyłącznie czerwonym kolorem nazwy kroku; statusy „trwa”/„w kolejce” nie mają wyglądu.

Wejścia przez router są poprawne (bez sesji → logowanie, rola `user` → lista postów). Ustalenia poza zarzutami: `lang="en"` przy polskim UI, surowe kody `failureClass`, brak odświeżania przebiegu w toku (→ S-06). Dowody wizualne: `ui-before/*.png`, odtwarzalne skryptem `mock-api.js` (playwright-cli, mock `/api/*`).

## Przed-audyt i metoda

- Pliki widoku: `src/views/AdminView.vue` (330 linii), `src/components/AppHeader.vue` (78 linii). Źródło wartości: `src/style.css`. Katalog komponentów: `src/components/` (`AppHeader.vue`, `PostCard.vue`).
- Pliki reguł agenta: `CLAUDE.md` i `AGENTS.md` dołączają `REPOSITORY_GUIDELINES.md` i zawierają blok `@przeprogramowani/10x-cli` (`CLAUDE.md:3-14`, `AGENTS.md:3-17`). Wyszukiwanie `css|styl|token|tailwind|component|ui|design` w `REPOSITORY_GUIDELINES.md` i `context/foundation/tech-stack-web.md` nie zwraca reguły UI — ani zalecającej tokeny, ani zachęcającej do jednorazowych wartości. Lintera nie ma (`REPOSITORY_GUIDELINES.md:33`).
- Skan literałów z `/10x-ui` (kolory, klasy palety, wartości arbitralne): `AdminView.vue` 5 linii (`:282`, `:287`, `:294`, `:308`, `:328`), `AppHeader.vue` 1 linia (`:76`). Poza widokiem: `FeedView.vue:81`, `LoginView.vue:104`, `:115`, `PostCard.vue:98`, `:102` — razem 11 linii w 5 plikach. Wartości `px` (forma literału w zwykłym CSS): 18 linii w bloku `<style>` `AdminView.vue` (221–320).
- Render: Vite dev + playwright-cli (Chromium z `.playwright/cli.config.json`) z mockiem `/api/*` (`mock-api.js`, tryby `data`/`loading`/`error`/`empty`), 1280×900 i 375×812, motyw jasny i ciemny. Zrzuty w `ui-before/`.
- Kontrast: wzór luminancji względnej WCAG 2.x, liczony jednorazowym skryptem dla par kolor/tło z `style.css` i widoku.

## Charges

### C1 — Brakujące tokeny ról: kolory stanów, obramowania, `accent-color`, skala

- **Kategoria**: brakujące tokeny.
- **Dowody (widok → źródło)**:
  - `AdminView.vue:282` `#188038` (status `succeeded`) i `:328` `#188038` (`.ok`) → powinien pokryć `--success`.
  - `AdminView.vue:287` `#b06000` (statusy `partial`, `interrupted`) → `--warning`.
  - `AdminView.vue:294` `#d93025` (`.status.failed`, `.alarm`, `.run-error`, `.error`), `:308` (`.steps .failed`), `AppHeader.vue:76` (`.error`) → `--destructive`.
  - Obramowania z `color-mix(in srgb, var(--text) N%, transparent)` z trzema różnymi N dla tej samej roli: 30% (`AdminView.vue:248`), 20% (`AdminView.vue:268`), 25% (`AppHeader.vue:48`) → `--border`.
  - Checkboxy (`AdminView.vue:140-145`, `:192-197`) mają `accent-color: auto` (odczyt `getComputedStyle` w renderze); wyszukiwanie `accent-color` w `szczur-radar-web/src/` nie zwraca trafień → systemowy niebieski przy fioletowym akcencie (`ui-before/desktop-light.png`).
  - Skala: rozmiary pisma w widoku 32px (`AdminView.vue:227`), 20px (`:232`), 14px (`:300`, `:312`); h1 32px jest wpisany drugi raz w `FeedView.vue:73`, bo globalny `h1` ma 44px (`style.css:37-46`, styl nagłówka karty logowania ze startera). Odstępy w widoku: 2/4/8/12/16/24 px; promień 6px (`AdminView.vue:249`, `style.css:103`) obok 8px (`style.css:87`, `LoginView.vue:93`, `:102`). W `:root` nie ma skali pisma, odstępów ani promieni (`style.css:1-31`).
- **Dowody (źródło → widoki)**: `:root` ma `--text`, `--text-h`, `--bg`, `--accent`, `--sans` (`style.css:2-7`) i ciemne wartości tych czterech kolorów (`style.css:24-31`). Odczyty `var(--…)`: `AdminView` 3, `AppHeader` 4, `FeedView` 1, `LoginView` 5, `PostCard` 5. Ról stanu (success/warning/destructive), obramowania ani pierścienia fokusu nie ma.
- **Wpływ na użytkownika**: w trybie ciemnym kolory stanów zostają jasnomotywowe i mają do `--bg` `#16171d` kontrast 3.56:1 (`#188038`), 3.85:1 (`#b06000`) i 3.75:1 (`#d93025`) — poniżej 4.5:1 dla zwykłego tekstu — więc „błąd”, „częściowo”, „błędy techniczne N” i treść błędu przebiegu są najsłabiej czytelne dokładnie tam, gdzie admin szuka awarii; w jasnym motywie `--accent` `#aa3bff` na `#fff` ma 4.39:1, czyli tekst każdego z 4 przycisków widoku (`style.css:86-89`) i link „Posty” (`AppHeader.vue:58`) jest pod progiem.
- **Naprawa**: role w `:root` z wartościami dla obu motywów (`--success`, `--warning`, `--destructive`, `--border`, `--ring`, korekta jasnego `--accent`), `accent-color: var(--accent)` globalnie, mała skala (pismo, odstępy, promień); widok odwołuje się do ról.
- **Uwaga zakresu**: zmiana ciemnego `--accent` dotknie przycisk logowania — dziś `#fff` na `#c084fc` ma 2.64:1 (`LoginView.vue:103-104`). Literały w `FeedView`/`LoginView`/`PostCard` są poza tym widokiem (patrz Open Questions).

### C2 — Brakujący współdzielony komponent: przycisk i komunikat stanu

- **Kategoria**: brakujący współdzielony komponent.
- **Dowody**:
  - Przycisk istnieje tylko jako globalny selektor elementu `button` / `button:disabled` (`style.css:81-96`) w jednym wariancie (obrys w kolorze akcentu). `LoginView.vue:98-111` nadpisuje go lokalnie drugim wariantem (wypełniony, `#fff` w `:104`) — dwa style przycisku w dwóch plikach, żaden nie jest komponentem. Wyszukiwanie `hover`, `outline` i `focus` w `szczur-radar-web/src/` nie zwraca trafień: przycisk nie ma stanu hover ani własnego stanu fokusu.
  - W widoku trzy przyciski — „Uruchom” (submit, główna akcja, `AdminView.vue:149`), „Odśwież” (`:158`), „Ponów zaznaczone” (`:207`) — oraz „Wyloguj” (`AppHeader.vue:36`) mają ten sam wygląd (`ui-before/desktop-light.png`).
  - Komunikat stanu `<p class="ok|error" role="status|alert">` jest pisany ręcznie 9 razy w 4 plikach: `AdminView.vue:151`, `:152`, `:160`, `:186`, `:211`, `:212`, `AppHeader.vue:38`, `FeedView.vue:47`, `LoginView.vue:64`; każdy plik ma własną regułę koloru (C1).
  - Pole daty: globalny `input[type='date']` (`style.css:98-106`), a `LoginView.vue:89-96` stylizuje swoje pola osobno.
- **Wpływ na użytkownika**: główna akcja „Uruchom” nie odróżnia się od „Odśwież” i „Wyloguj”, przyciski nie reagują na najechanie, a wynik startu („Uruchomiono…”, „Przebieg już trwa…”) to sam kolorowy tekst pod formularzem — łatwo go przeoczyć i kliknąć drugi raz.
- **Naprawa**: komponenty w `src/components/` (zwykłe SFC, bez zależności): przycisk z wariantami główny/pomocniczy oraz stanami hover, focus-visible, disabled i „w toku”; komunikat stanu z tonem ok/błąd mapowanym na `role="status"` / `role="alert"`; style elementów w `style.css` zostają jako baza albo przechodzą do komponentu (decyzja planu).
- **Ograniczenie z testów**: `AdminView.test.ts` szuka po `[role="status"]` (`:93`, `:163`), `[role="alert"]` (`:115`), `form button` (`:102`, `:107`, `:124`) i tekście przycisku „Ponów zaznaczone” (`:154`) — komponent musi zachować te role i element `<button>` albo testy trzeba zaktualizować w tej samej fazie.

### C3 — Przypadkowa architektura: fałszywy stan pusty przy wejściu i martwy formularz po błędzie źródeł

- **Kategoria**: przypadkowa architektura (wejście stanu pustego).
- **Dowody**:
  - `runs` i `failed` startują jako puste tablice (`AdminView.vue:48`, `:72`); w pliku nie ma flagi ładowania (wyszukiwanie `loading|aria-busy` w `AdminView.vue` bez trafień). Szablon pokazuje stan pusty dla `runs.length === 0` (`:161`) i `failed.length === 0` (`:187`) także przed pierwszą odpowiedzią — przy zawieszonym API oba teksty są widoczne (`ui-before/loading.png`). `FeedView` ma to rozwiązane (`FeedView.vue:14`, `:48`: `loading` i „Wczytywanie…”).
  - Źródła są pustą listą do odpowiedzi `fetchSources()` (`AdminView.vue:25-26`, `:118-119`); do tego czasu fieldset ma samą legendę (`:137-148`), a „Uruchom” jest wyłączony, bo `chosenSources` jest puste (`:149`), bez podania przyczyny.
  - Po błędzie listy źródeł (`:120-121`) jedyny przycisk odświeżania (`:158`) woła `refresh()`, które przeładowuje przebiegi i nieudane posty, ale nie źródła (`:112-114`) — formularz zostaje martwy do przeładowania strony (`ui-before/error.png`).
  - Gdy źródła dochodzą, wiersz formularza zmienia wysokość i sekcje poniżej przesuwają się (nagłówek „Ostatnie przebiegi” na y≈250 w `loading.png`, y≈271 w `desktop-light.png`, 1280×900).
- **Wpływ na użytkownika**: przy każdym wejściu (dłużej przy wolnym API) admin czyta „Jeszcze nie było przebiegu.” i „Brak nieudanych postów.” — nieprawdziwe zdania, które mogą ukryć awarię; po błędzie listy źródeł formularz nie działa, nie mówi dlaczego, a „Odśwież” tego nie naprawia.
- **Naprawa**: stan ładowania na sekcję (przed pierwszą odpowiedzią nie pokazywać stanu pustego), rezerwacja miejsca na listę źródeł, przyczyna wyłączenia „Uruchom”, ponowienie wczytania źródeł z poziomu panelu.
- **Sprawdzone wejścia bez zarzutu**: bez sesji → `/login?redirect=/admin` (`router/index.ts:39-41`); rola `user` → `/` (`router/index.ts:45-47`; API i tak zwraca 403, `router/index.ts:22`); wygaśnięcie sesji w trakcie → logowanie (`router/index.ts:51-56`); bezpośredni link `/admin` jako admin → widok.

### C4 — Status kroku przekazany tylko kolorem; statusy w toku bez wyglądu

- **Kategoria**: brakujący współdzielony komponent (znacznik statusu).
- **Dowody**:
  - Kroki przebiegu renderują się jako `etykieta (itemsOut)` z klasą równą `step.status` (`AdminView.vue:174-178`); jedyną różnicą nieudanego kroku jest czerwony kolor (`.steps .failed`, `:307-309`). W przebiegu „częściowo” widać „ocena” na czerwono bez słowa o błędzie (`ui-before/desktop-light.png`).
  - Statusy przebiegu mają tekst (`label('runStatus', …)`, `:166`) i kolor tylko dla `succeeded`, `partial`, `interrupted`, `failed` (`:281-295`); `queued` („w kolejce”) i `running` („trwa”) wyglądają jak sąsiednia data.
- **Wpływ na użytkownika**: admin, który nie rozróżnia czerwieni (albo czyta ciemny motyw przy 3.75:1, C1), nie wie, który krok zawiódł w przebiegu częściowym lub nieudanym, a przebieg w toku nie wyróżnia się na liście.
- **Naprawa**: komponent znacznika statusu w `src/components/` (ton z tokenów C1 + zawsze tekst) dla statusu przebiegu i kroku; nieudany krok dostaje tekst, nie tylko kolor.
- **Granica z S-06**: S-06 ma dać „jednoznaczny sygnał nieudanego lub częściowo nieudanego przebiegu” (`context/foundation/roadmap.md`, S-06 Outcome). Tu tylko sygnał niezależny od koloru i komponent, który S-06 użyje; układ historii, agregowany sygnał awarii i odświeżanie → **deferred do S-06**.

### Deferred (nie usuwać)

- **D1 — układ listy przebiegów i automatyczne odświeżanie przebiegu w toku** → S-06. Lista nie odpytuje API; status „trwa” zmienia się dopiero po „Odśwież” (`AdminView.vue:51-58`, `:158`).
- **D2 — literały kolorów w innych widokach** (`FeedView.vue:81`, `LoginView.vue:104`, `:115`, `PostCard.vue:98`, `:102`) i kontrast przycisku logowania w ciemnym motywie (2.64:1) — poza jednym widokiem tej zmiany, chyba że plan zdecyduje inaczej (Open Questions 2).

## Macierz 7 stanów — stan obecny

| Stan | Teraz | Dowód |
| --- | --- | --- |
| default | 5 linii z literałami kolorów, 18 linii z `px`, 1 importowany komponent | C1, skan |
| hover | brak — `hover` nie występuje w `szczur-radar-web/src/` | C2 |
| focus-visible | domyślny pierścień przeglądarki (widoczny, systemowy niebieski); brak tokenu `--ring`; `outline`/`focus` nie występują w `src/` | `ui-before/focus-3tabs.png` |
| disabled | `opacity: 0.6` globalnie (`style.css:93-96`); „Uruchom” nie zmienia etykiety w trakcie startu (`AdminView.vue:149`); przyczyna wyłączenia niepodana (C3) | C2, C3 |
| error | czerwony tekst pod formularzem/sekcją, przy akcji; kolor z literału, 3.75:1 w ciemnym motywie | C1, C2, `ui-before/error.png` |
| empty | teksty istnieją (`:161`, `:187`), ale pokazują się też w trakcie ładowania | C3 |
| loading | brak; skok układu po dojściu źródeł | C3, `ui-before/loading.png` |

Mobile 375×812: bez poziomego przewijania (`scrollWidth` = `clientWidth` = 375), nagłówek łamie się na dwa wiersze, formularz zawija się (`ui-before/mobile-light.png`). Nazwy dostępne: wszystkie 10 kontrolek w renderze `data` ma nazwę (etykieta lub tekst przycisku).

## Inne ustalenia (poza zarzutami)

- `index.html:2` ma `lang="en"` przy polskim interfejsie — czytnik ekranu czyta polski tekst z angielską wymową. Zmiana globalna, jednolinijkowa; kandydat do pierwszej fazy (środowisko).
- `failureClass` jest pokazywany surowym kodem (`schema_violation`, `transport_error`; `AdminView.vue:203`) — `labels.ts:2-34` nie ma zbioru dla klas porażek. Treść, nie wygląd; do decyzji w planie.
- Brak narzędzia testów zrzutów ekranu (`package.json` bez Playwright Test/Storybook) — bramka wizualna według `/10x-ui` to strona kitchen sink + zrzuty playwright-cli z `mock-api.js`; nie instalujemy runnera.
- Brak lintera — „kontrola” z `/10x-ui` może być testem Vitest skanującym pliki oczyszczonego widoku regexem ze skanu (bez nowej zależności).

## Code References

- `szczur-radar-web/src/style.css:1-31` — `:root` (4 kolory, `--sans`) i ciemne wartości przez `prefers-color-scheme`.
- `szczur-radar-web/src/style.css:37-46` — globalny `h1` 44px/32px (nagłówek karty ze startera).
- `szczur-radar-web/src/style.css:81-106` — globalne `button`, `button:disabled`, `input[type='date']` (ukryty „komponent”).
- `szczur-radar-web/src/views/AdminView.vue:48`, `:72` — `runs`/`failed` startują puste, bez flagi ładowania.
- `szczur-radar-web/src/views/AdminView.vue:112-124` — `refresh()` bez źródeł; `onMounted` wczytuje źródła raz.
- `szczur-radar-web/src/views/AdminView.vue:137-149` — fieldset źródeł i „Uruchom”.
- `szczur-radar-web/src/views/AdminView.vue:161`, `:187` — stany puste.
- `szczur-radar-web/src/views/AdminView.vue:174-178`, `:307-309` — kroki, nieudany tylko kolorem.
- `szczur-radar-web/src/views/AdminView.vue:217-330` — style widoku (literały kolorów i `px`).
- `szczur-radar-web/src/components/AppHeader.vue:36-38`, `:48`, `:76` — „Wyloguj”, komunikat, obramowanie, literał błędu.
- `szczur-radar-web/src/views/LoginView.vue:89-116` — drugi wariant pola i przycisku.
- `szczur-radar-web/src/views/FeedView.vue:14`, `:48` — wzorzec stanu ładowania.
- `szczur-radar-web/src/router/index.ts:34-60` — guard sesji i roli, obsługa 401.
- `szczur-radar-web/src/views/AdminView.test.ts:93-163` — selektory, które refaktor musi zachować lub zaktualizować.
- `szczur-radar-web/index.html:2` — `lang="en"`.

## Architecture Insights

- Wariant kontraktu: **istniejący, szczątkowy system na zwykłych zmiennych CSS** — wartości w `:root` `src/style.css`, komponenty w `src/components/`, style lokalne w `<style scoped>`. Rozszerzamy `:root` (role + ciemne wartości w tym samym bloku `prefers-color-scheme`) i dodajemy 2–3 komponenty używane przez ten widok; bez Tailwinda, shadcn i nowych zależności.
- Tryb ciemny działa dziś na warstwie tokenów tylko dla czterech kolorów; wszystko, co jest literałem (stany), wypada z przełączania — to ten sam mechanizm co klasyczna awaria dark mode opisana w `/10x-ui`.
- Paleta (`--accent` `#aa3bff`/`#c084fc`, `--text` `#6b6375`, `--bg` `#16171d`) pochodzi ze startera Vite; komentarz `style.css:48` wciąż mówi o „placeholder home”, którego już nie ma (`HomeView.vue` → `AppHeader.vue` w #37).
- Reguła UI w pliku reguł agenta nie istnieje — po tej zmianie trafi do `REPOSITORY_GUIDELINES.md` (dołączany przez `CLAUDE.md`/`AGENTS.md`), poza blokiem `@przeprogramowani/10x-cli`.

## Historical Context (from prior changes)

- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md:136` — „Style to zwykły CSS ze zmiennymi i trybem ciemnym (`web/style.css`), bez bibliotek UI i bez store'a.” — **supported** (ścieżka dziś `szczur-radar-web/src/style.css`).
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:35`, `:466` — decyzja „zwykły CSS” / „Zwykły CSS w stylu `style.css`” dla FeedView i AdminView — **supported**; ta zmiana ją utrzymuje.
- `context/archive/2026-10-06-first-assessed-wykop-feed/plan.md:483` — zakres testów AdminView (start, `pipeline_busy`, ponowienie, `partial`, licznik błędów technicznych, komunikat `error`) — **supported** przez `AdminView.test.ts`.
- `context/foundation/tech-stack-web.md` — brak decyzji o stylach, tokenach i komponentach UI (wyszukiwanie `css|styl|design|tailwind|component|a11y` bez trafień).

## Related Research

- `context/archive/2026-10-06-first-assessed-wykop-feed/research.md` — powstanie FeedView i AdminView (S-02).

## Open Questions

1. **Kitchen sink**: gdzie ma żyć — trasa tylko w trybie dev (`import.meta.env.DEV`) czy osobny wpis HTML Vite — tak, by nie trafił do obrazu produkcyjnego.
2. **Zasięg tokenów**: tokeny są globalne; czy w tej zmianie podmienić też identyczne role w `FeedView`/`LoginView`/`PostCard` (D2, mechanicznie), czy zostawić to następnym widokom.
3. **Akcent**: zachować fiolet startera z ciemniejszą wartością jasną (≥4.5:1), czy wybrać nowy akcent — decyzja produktowa.
4. **Kontrola**: test Vitest skanujący oczyszczone pliki jako „kontrola” z `/10x-ui` — akceptowalny bez nowej zależności?
5. **`failureClass`**: polskie etykiety w `labels.ts` w tej zmianie czy osobno.
