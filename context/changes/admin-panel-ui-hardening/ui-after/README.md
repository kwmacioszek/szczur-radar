# Zrzuty „po” — `/admin` i kitchen sink

Dowód bramki wizualnej zmiany `admin-panel-ui-hardening` (plan, faza 4 pkt 5). Zrzuty z `npm run dev` i `playwright-cli` (Chromium, `--config` z głównego checkoutu): `/admin` z `szczur-radar-web/dev/mock-api.js` w trybach `data`, `loading`, `error`, `empty` (kopia z podmienionym `MODE`), `/kitchen-sink` bez mocka. Rozmiary 1280×900 i 375×812, motyw przez `set-color-scheme light|dark`. Hover i fokus wymuszone w przeglądarce (`hover`, `Shift+Tab` / `Tab`), nie klasami CSS.

Nazwy: `p<faza>-<widok>-<tryb>-<szerokość>-<motyw>.png`. Zrzuty `p2-*` (wartości tokenów, Feed i Login) i `p3-*` (panel na komponentach) zostają jako dowód wcześniejszych faz.

## Macierz 7 stanów

| Stan | 1280 jasny | 1280 ciemny | 375 jasny | 375 ciemny |
| --- | --- | --- | --- | --- |
| default | `p4-admin-data-1280-light.png` | `p4-admin-data-1280-dark.png` | `p4-admin-data-375-light.png` | `p4-admin-data-375-dark.png` |
| hover | `p3-kitchen-hover-focus-light.png` (główny „Domyślny”) | `p3-kitchen-hover-focus-dark.png` | N/A — ekran dotykowy nie ma hover | N/A — jak obok |
| focus-visible | `p3-kitchen-hover-focus-light.png` (pomocniczy „Domyślny”), `p2-admin-focus-light.png` (`/admin`) | `p3-kitchen-hover-focus-dark.png`, `p2-admin-focus-dark.png` | `p4-kitchen-375-focus-light.png` | `p4-kitchen-375-focus-dark.png` |
| disabled | `p4-admin-disabled-1280-light.png` („Uruchom” z przyczyną, „Ponów zaznaczone”, checkbox martwego posta), `p3-kitchen-light.png` (wyłączony i w toku) | `p4-admin-disabled-1280-dark.png`, `p3-kitchen-dark.png` | `p4-admin-disabled-375-light.png` | `p4-admin-disabled-375-dark.png` |
| error | `p4-admin-error-1280-light.png` | `p4-admin-error-1280-dark.png` | `p4-admin-error-375-light.png` | `p4-admin-error-375-dark.png` |
| empty | `p4-admin-empty-1280-light.png` | `p4-admin-empty-1280-dark.png` | `p4-admin-empty-375-light.png` | `p4-admin-empty-375-dark.png` |
| loading | `p4-admin-loading-1280-light.png` | `p4-admin-loading-1280-dark.png` | `p4-admin-loading-375-light.png` | `p4-admin-loading-375-dark.png` |

Stan „w toku” przycisku („Uruchamianie…”, „Ponawianie…”) trwa tyle, co odpowiedź API: na zrzutach jest w kitchen sink („W toku…”), a w `/admin` pilnują go testy `AdminView.test.ts`.

## Porównanie z `ui-before/`

| Przed | Po | Co się zmieniło |
| --- | --- | --- |
| `desktop-light.png`, `desktop-dark.png` | `p4-admin-data-1280-light.png`, `p4-admin-data-1280-dark.png` | Kobalt zamiast fioletu, chłodne szarości; „Uruchom” jako przycisk główny; status przebiegu i nieudany krok jako znacznik z tekstem („ocena (błąd)”), nie sam kolor; `failureClass` po polsku; kolory stanów przełączają się w ciemnym motywie (C1, C2, C4) |
| `loading.png` | `p4-admin-loading-1280-light.png` | Zamiast „Jeszcze nie było przebiegu.” i „Brak nieudanych postów.” każda sekcja mówi „Wczytywanie…” (`aria-busy`); fieldset źródeł ma wysokość wiersza od początku (C3) |
| `error.png` | `p4-admin-error-1280-light.png` | Komunikaty błędu jako `StatusMessage` (zabarwione tło, lewa krawędź, tekst `--text-h`); błąd źródeł w fieldsecie z „Spróbuj ponownie”, „Odśwież” też wczytuje źródła — formularz nie zostaje martwy (C2, C3) |
| `focus-3tabs.png` | `p2-admin-focus-light.png`, `p3-kitchen-hover-focus-light.png` | Pierścień fokusu z tokenu `--ring` (akcent) z odstępem, zamiast systemowego |
| `mobile-light.png` | `p4-admin-data-375-light.png` | Bez poziomego przewijania także po zmianach (`scrollWidth` = `clientWidth` = 375 w trybie `data` i `error`); znaczniki zawijają się z tekstem |

Skok układu: przy 1280×900 nagłówek „Ostatnie przebiegi” ma `y` = 258.47 px w trybie `loading` i w `data` (po wczytaniu danych), fieldset źródeł 61.19 px w obu.

## Skan literałów

Skan z `/10x-ui` (hex, `rgb(a)`, `hsl(a)`, `oklch`, `-[Npx|rem]`, klasy palety), liczba linii:

| Plik | Przed | Po |
| --- | --- | --- |
| `views/AdminView.vue` | 5 | 0 |
| `components/AppHeader.vue` | 1 | 0 |
| `views/FeedView.vue` | 1 | 0 |
| `views/LoginView.vue` | 2 | 0 |
| `components/PostCard.vue` | 2 | 0 |
| `components/AppButton.vue`, `StatusMessage.vue`, `StatusBadge.vue`, `views/KitchenSinkView.vue` | — (nowe) | 0 |

Pilnuje tego `szczur-radar-web/src/styleContract.test.ts` (opis „no literals”), razem z kontrastem ról w obu motywach.

## Uwagi

- Wyłączony checkbox w jasnym motywie jest bardzo blady (`p3-kitchen-light.png`, martwy post w `p4-admin-data-1280-light.png`): to domyślny wygląd przeglądarki, `accent-color` go nie zmienia.
- W trybie `error` przy 1280 px fieldset z komunikatem i „Spróbuj ponownie” jest szerszy, więc „Uruchom” przechodzi do drugiego wiersza formularza.

## Lista kontrolna `ui-quality-checklist`

Z `.claude/skills/10x-ui/references/ui-quality-checklist.md`, po fazach 1–5.

### Charges

- [x] Zmiana zaczęła się od `## Charges` w `research.md`: C1–C4, każdy z plikiem, linią i skutkiem dla admina.
- [x] Brakujące tokeny: literały kolorów w widoku zastąpione rolami z `:root` w `src/style.css`; skan spadł z 11 linii do 0 (tabela wyżej).
- [x] Brakujący współdzielony komponent: `/admin` i `AppHeader` używają `AppButton`, `StatusMessage`, `StatusBadge`; drugiego przycisku w panelu nie ma. Własny przycisk `LoginView` i globalne przyciski `FeedView` mają już ten sam wygląd (gwarancje 1–2 planu); ich przejście na komponent to odłożone zmiany niżej.
- [x] Przypadkowa architektura: wejścia sprawdzone w researchu (bez sesji → `/login`, bez danych, z linku; `router/index.ts`); `/kitchen-sink` publiczny tylko w dev i nieobecny w `dist/`.
- [x] Zarzuty poza planem zapisane jako odłożone, nie porzucone (sekcja niżej).

### Contract

- [x] Tokeny: kolory, odstępy i pismo z `:root` w `src/style.css`, nie literały w widoku.
- [x] Warstwa komponentów: repo nie miało żadnej; trzy komponenty w `src/components/` bez nowych zależności (bez shadcn, plan: „żadnych nowych zależności”).
- [x] Macierz 7 stanów: każdy pokazany albo N/A z powodem (tabela wyżej).
- [x] Desktop (1280×900) i jedna szerokość mobilna (375×812).
- [x] Widoczny fokus i nazwy kontrolek: pierścień `--ring` na każdej kontrolce (`p2-admin-focus-*.png`, `p3-kitchen-hover-focus-*.png`); każda kontrolka ma etykietę albo tekst przycisku, „Uruchom” ma przyczynę wyłączenia przez `aria-describedby`.
- [x] Ciemny motyw zmieniony na warstwie tokenów (blok `prefers-color-scheme` w `style.css`) i sprawdzony w obu motywach; kontrast ról pilnowany testem.

### Guard

- [x] Plik reguł agenta (`REPOSITORY_GUIDELINES.md`, sekcja „UI (szczur-radar-web)”) wskazuje źródło tokenów i katalog komponentów i zakazuje literałów w widokach; reguł zapraszających do jednorazowych wartości nie było.
- [x] N/A dla lintera i hooka pre-commit — repo nie ma ani jednego, ani drugiego. W ich miejsce skan literałów w `src/styleContract.test.ts` biegnie w CI (job „web unit tests”) na oczyszczonych plikach.

### Gate

- [x] Kitchen sink (`/kitchen-sink`) i zrzuty `/admin` z mockiem; `toHaveScreenshot` N/A — bez Playwright Test (bez nowych zależności), więc bez bazowych zrzutów do aktualizacji.
- [ ] `/10x-impl-review` — uruchamiany po fazie 5 na tej gałęzi, przed `/10x-archive`; wynik i triaż uwag UI dopisuję tutaj.
- [x] Zakres utrzymany: jeden widok (`AdminView` + `AppHeader`) plus globalne tokeny; Feed, Login i PostCard tylko z mechaniczną podmianą 5 literałów.

## Odłożone

- **D1 → S-06** (`pipeline-run-history-dashboard`): układ listy przebiegów, zagregowany sygnał awarii, automatyczne odświeżanie przebiegu w toku.
- **`feed-view-ui-hardening`** (przed S-03 i S-04): FeedView + PostCard na `AppButton` / `StatusBadge` („wzrostowo” / „spadkowo”), `px` na skalę.
- **`login-view-ui-hardening`**: przycisk logowania jako `AppButton` wariant główny, błąd jako `StatusMessage`.
- **`admin-failed-posts-table`** (prośba z weryfikacji fazy 3): nieudane posty jako tabela z wierszami i kolumnami zamiast listy; wspólny wygląd tabeli do użycia też w historii przebiegów S-06.
- Wyłączony checkbox w jasnym motywie (domyślny wygląd przeglądarki) — do decyzji w jednej z powyższych zmian.
