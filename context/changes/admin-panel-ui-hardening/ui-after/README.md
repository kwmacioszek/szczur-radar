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
