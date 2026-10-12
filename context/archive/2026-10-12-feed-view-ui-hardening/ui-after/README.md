# Zrzuty „po” — feed `/` i kitchen sink

Dowód bramki wizualnej zmiany `feed-view-ui-hardening` (plan, faza 3 pkt 6). Zrzuty z `npx vite --port 5181` i `playwright-cli` (Chromium, `--config` z głównego checkoutu): `/` z `szczur-radar-web/dev/mock-api.js` w trybach `data`, `loading`, `error`, `empty`, `loading-next`, `error-next` (kopia z podmienionym `MODE`), `/kitchen-sink` bez mocka. Rozmiary 1280×900 i 375×812, motyw przez `set-color-scheme light|dark`. Hover i fokus wymuszone w przeglądarce (`hover`, `Tab`), nie klasami CSS. Mock loguje jako admin, więc nagłówek ma „Panel admina” (zrzuty `ui-before/` robione jako `user`).

Nazwy: `p<faza>-<widok>-<stan>-<szerokość>-<motyw>.png`. `p2-*` to dowód fazy 2 (karty C2 na skali i komponentach), `p3-*` — stany i stronicowanie.

## Macierz 7 stanów

| Stan | 1280 jasny | 1280 ciemny | 375 jasny | 375 ciemny |
| --- | --- | --- | --- | --- |
| default | `p3-feed-data-1280-light.png`, `p2-kitchen-posts-1280-light.png` | `p3-feed-data-1280-dark.png`, `p2-kitchen-posts-1280-dark.png` | `p3-feed-data-375-light.png`, `p2-kitchen-posts-375-light.png` | `p3-feed-data-375-dark.png`, `p2-kitchen-posts-375-dark.png` |
| hover | `p3-feed-hover-1280-light.png` („Starsze”) | `p3-feed-hover-1280-dark.png` | N/A — ekran dotykowy nie ma hover | N/A — jak obok |
| focus-visible | `p3-feed-focus-pager-1280-light.png` („Starsze” po 4× Tab), `p3-feed-focus-after-paging-1280-light.png` („Posty” po zmianie strony) | `p3-feed-focus-pager-1280-dark.png`, `p3-feed-focus-after-paging-1280-dark.png` | `p3-feed-focus-pager-375-light.png` | `p3-feed-focus-pager-375-dark.png` |
| disabled | `p3-feed-pager-disabled-1280-light.png` („Nowsze” na stronie 1) | `p3-feed-pager-disabled-1280-dark.png` | `p3-feed-pager-disabled-375-light.png` | `p3-feed-pager-disabled-375-dark.png` |
| error | `p3-feed-error-1280-light.png` (pierwsze wejście), `p3-feed-paging-error-1280-light.png` (po zmianie strony) | `p3-feed-error-1280-dark.png`, `p3-feed-paging-error-1280-dark.png` | `p3-feed-error-375-light.png`, `p3-feed-paging-error-375-light.png` | `p3-feed-error-375-dark.png`, `p3-feed-paging-error-375-dark.png` |
| empty | `p3-feed-empty-1280-light.png` (`total` 0), `p3-feed-out-of-range-1280-light.png` (`/?page=9`) | `p3-feed-empty-1280-dark.png`, `p3-feed-out-of-range-1280-dark.png` | `p3-feed-empty-375-light.png`, `p3-feed-out-of-range-375-light.png` | `p3-feed-empty-375-dark.png`, `p3-feed-out-of-range-375-dark.png` |
| loading | `p3-feed-loading-1280-light.png` (pierwsze wejście), `p3-feed-paging-loading-1280-light.png` (zmiana strony) | `p3-feed-loading-1280-dark.png`, `p3-feed-paging-loading-1280-dark.png` | `p3-feed-loading-375-light.png`, `p3-feed-paging-loading-375-light.png` | `p3-feed-loading-375-dark.png`, `p3-feed-paging-loading-375-dark.png` |

Karta posta nie ma kontrolek, więc jej hover i fokus są N/A do S-03 (link do widoku szczegółowego, D1). Stan „w toku” przycisków stronicowania nie występuje: w trakcie wczytywania stronicowanie znika, a region listy ma `aria-busy="true"` (testy `FeedView.test.ts`).

## Pomiary (1280×900, tryb `data`)

- Zmiana strony klawiaturą (4× Tab do „Starsze”, Enter): `?page=1`, `scrollY` 0, `document.activeElement` = `H1#feed-title`, etykieta „Strona 2 z 3”. Przed zmianą (`ui-before/`): `scrollY` 8213 → 8223, fokus na `BODY`.
- Bez poziomego przewijania przy 375 px w trybach `data`, `loading`, `error`, `empty` (`scrollWidth` = `clientWidth` = 375; faza 2: `p2-feed-data-375-*`).
- Karta przy 1280: kolumny 162 px | 596 px, nagłówek 16 px / 600, stanowisko 16 px, znaczniki 14 px; przy 375: jedna kolumna 293 px.

## Liczniki skanu

| | Przed (`research.md`) | Po |
| --- | --- | --- |
| Skan `/10x-ui` (kolory, klasy palety, wartości arbitralne) w `FeedView.vue` + `PostCard.vue` | 0 | 0 |
| `px`/`rem` w piśmie, odstępach i promieniach (`font-size`, `margin`, `padding`, `gap`, `letter-spacing`, `border-radius`) | 23 (5 + 18) | 0 (pilnuje `styleContract.test.ts`) |
| Importy `AppButton` / `StatusMessage` / `StatusBadge` | 0 | `FeedView`: `AppButton`, `StatusMessage`; `PostCard`: `StatusBadge` |

## Porównanie z `ui-before/`

| Przed | Po | Co się zmieniło |
| --- | --- | --- |
| `feed-data-1280-light-viewport.png` | `p3-feed-data-1280-light.png` | Karty C2 w obwódce: metadane (data i godzina w jednej linii, typ bez akcentu, trafność jako znacznik) obok treści; nagłówek 16 px / 600; cele w siatce „spółka \| stanowisko”; flagi jako `StatusBadge` (C1, C2) |
| `feed-data-375-light.png` | `p3-feed-data-375-light.png` | Metadane w jednym wierszu nad treścią, oddzielone kreską; bez poziomego przewijania |
| `feed-paging-loading-1280-light.png` | `p3-feed-paging-loading-1280-light.png` | Zamiast „Strona 2 z 3” nad postami strony 1: góra strony i „Wczytywanie strony 2…” z `aria-busy` (C3) |
| `feed-after-older-click-1280-light.png` | `p3-feed-focus-after-paging-1280-light.png` | Po wczytaniu góra nowej strony i fokus na „Posty”, zamiast dołu strony i fokusu na `body` (C3) |
| `feed-out-of-range-1280-light.png` | `p3-feed-out-of-range-1280-light.png` | Zamiast pustego „Strona 10 z 3”: „Nie ma strony 10. Posty kończą się na stronie 3.” i „Przejdź do strony 3” (C3) |
| `feed-error-next-1280-light.png`, `feed-error-1280-light.png` | `p3-feed-paging-error-1280-light.png`, `p3-feed-error-1280-light.png` | `StatusMessage` z tłem i krawędzią oraz „Spróbuj ponownie” zamiast gołej czerwonej linii z radą odświeżenia (C2, C4) |
| `feed-loading-1280-light.png` | `p3-feed-loading-1280-light.png` | Ten sam tekst „Wczytywanie…”, teraz z `aria-busy` na regionie listy (C4) |
| `feed-empty-1280-light.png` | `p3-feed-empty-1280-light.png` | „…po najbliższej aktualizacji danych.” zamiast „…po pierwszym przebiegu.” |

## Lista kontrolna `/10x-ui`

Według `.claude/skills/10x-ui/references/ui-quality-checklist.md`.

### Charges

- [x] Zmiana wyszła z `## Charges` w `research.md`: 4 zarzuty (C1–C4), każdy z plikiem, linią i wpływem na użytkownika.
- [x] Brakujące tokeny: literałów kolorów, klas palety i wartości arbitralnych było 0 przed i jest 0 po; `px`/`rem` w piśmie, odstępach i promieniach 23 → 0 (tabela „Liczniki skanu”).
- [x] Brakujący współdzielony komponent: feed używa `AppButton`, `StatusMessage`, `StatusBadge`; ręczny znacznik `.flag` i goły `<p role="alert">` zniknęły; nowy prymityw nie powstał.
- [x] Przypadkowa architektura, wejścia: bez sesji → `/login?redirect=…` (guard bez zmian; w PR env `/api/posts` bez sesji = 401); bez danych → stan pusty (`p3-feed-empty-*`); prosto z linku → `/?page=9` z komunikatem i przyciskiem (`p3-feed-out-of-range-*`).
- [x] Zarzuty poza planem zapisane jako deferred w `research.md`: D1 link i widok posta → S-03, D2 filtry → S-04 (data świeżości → S-05), D3 pasek stronicowania nad listą → S-04, D4 `AppHeader` przy 375 px → poza zakresem.

### Contract

- [x] Tokeny: pismo, odstępy, promienie i kolory z `:root` w `szczur-radar-web/src/style.css`; `style.css` bez zmian (faza „wartości tokenów” N/A).
- [x] Warstwa komponentów repo (`src/components/`): `PostCard` przebudowany w miejscu (karta C2), bez drugiego przycisku, karty ani pola; sekcja „Posty” w kitchen sink.
- [x] Macierz 7 stanów: tabela wyżej, każda komórka ze zrzutem albo N/A z powodem.
- [x] Desktop i jedna szerokość mobilna: 1280×900 i 375×812.
- [x] Fokus i nazwy: pierścień `--ring` na każdej kontrolce widoku („Posty”, „Wyloguj”, „Nowsze”, „Starsze”, „Spróbuj ponownie”, „Przejdź do strony N”) i na nagłówku „Posty” po zmianie strony; nazwy z tekstu kontrolek. Znacznik trafności pokazuje samą wartość („inwestycyjny”) bez prefiksu „Trafność:” — decyzja użytkownika przy fazie 2.
- [x] Ciemny motyw wyłącznie na warstwie tokenów; oba motywy na zrzutach; brak nowych par kolorów (test kontrastu bez zmian).

### Guard

- [x] Plik reguł agenta (`REPOSITORY_GUIDELINES.md`, sekcja „UI”, dołączany przez `CLAUDE.md` / `AGENTS.md`): źródło tokenów, katalog komponentów (z `PostCard`), zakaz literałów i wartości spoza skali, kitchen sink, mock `/admin` i `/`; brak reguł zachęcających do jednorazowych wartości.
- [x] Kontrola: lintera nie ma; jest nią `src/styleContract.test.ts` (Vitest, job „web unit tests” w CI) — skan kolorów i skali na liście `CLEANED`; `LoginView.vue` poza listą do `login-view-ui-hardening`.

### Gate

- [x] Kitchen sink (sekcja „Posty”) i zrzuty widoku z mockiem w obu motywach i przy 375 px; narzędzia `toHaveScreenshot` w repo nie ma, więc baseline N/A.
- [x] `/10x-impl-review` — fazy 1–4 sprawdzone; APPROVED; F1 (skan wielowierszowego CSS) i F2 (dowód rzeczywistej drugiej strony) naprawione w triage. Raport: `../reviews/impl-review.md`.
- [x] Zakres: jeden widok (`FeedView`) z kartą `PostCard`, bez zmian tokenów globalnych; poza widokiem tylko reguła, kontrola, mock i kitchen sink.

## Weryfikacja w Railway PR env (2026-10-12)

- Środowisko `szczur-radar-pr-48`, adres `https://szczur-radar-web-szczur-radar-pr-48.up.railway.app`; web wdrożony z `a371271` (głowa gałęzi z kodem widoku; commit fazy 4 zmienia tylko dokumenty), CI na `a371271` zielone we wszystkich jobach.
- Dane: konto admina utworzone przez operatora (`create-admin` przez `railway ssh`), jeden przebieg zakresu dat w `/admin` — `partial`, 34 ocenione, 1 nieudany; feed `total` 34.
- `/` przy 1280×900 i 375×812 w obu motywach: 34 karty C2, bez poziomego przewijania (`scrollWidth` = `clientWidth`), bez alertów, „Strona 1 z 1” z oboma przyciskami wyłączonymi. Zrzuty: `pr48-feed-{1280,375}-{light,dark}.png`.
- Zmiana strony na prawdziwych danych: 34 posty mieszczą się na jednej stronie, więc „Starsze” jest wyłączone; tę samą ścieżkę (zmiana `?page`) sprawdzono przez `/?page=1` → „Nie ma strony 2. Posty kończą się na stronie 1.” (`pr48-feed-out-of-range-1280-light.png`) → „Przejdź do strony 1” → `scrollY` 0, fokus na `H1#feed-title`, 34 karty. „Starsze” z dołu pełnej strony sprawdzone na mocku (`p3-feed-paging-loading-*`, pomiar w sekcji „Pomiary”).

## Uzupełnienie po implementation review — F2 (2026-10-12)

**F2 FIXED — 2026-10-12, 20:09 UTC.** Po odblokowaniu uprawnień i zalogowaniu konta admina w headed Playwright wykonano rzeczywiste przejście „Starsze” na stronę 2. Środowisko i adres jak wyżej; Railway MCP potwierdził web deployment `fb3b49bc-575c-48f9-99aa-1a5b98957e1b` z **df6213a602a4d49fe1fdc53b2362f0135bd4848b**, stan **SUCCESS**.

Dane: początkowo 34 ocenione posty, brak aktywnego przebiegu. Przez `/admin` uruchomiono dodatkowy dzień Wykopu **2026-10-10**: run `e92a403a-73d2-4476-b67f-2fa66ffb04af` zakończony `partial` (39 pobranych, 37 ocenionych, 2 nieudane). Automatyczny retry `85edf5a0-5614-493d-84ee-3d9033a9b3d9` zakończył się `succeeded` (1 oceniony). Po zakończeniu obu: **total 72**, pierwsza strona 50, druga 22. Dane rzeczywiste, bez zmiany PAGE_SIZE i mockowania API.

Wykonany skrypt: `../follow-ups/verify-pr-env-paging.js`. Każdy wariant uruchamia „Starsze” klawiszem Enter z dołu pierwszej strony, sprawdza HTTP 200 oraz `page=1, size=50`, odrębność rekordów obu stron i zgodność wszystkich nagłówków kart z odpowiedzią. Wszystkie cztery warianty pokazały **Strona 2 z 2**, **22 karty**, **scrollY 0** i **document.activeElement = H1#feed-title**. Obejrzano wszystkie nowe PNG; układ, zawijanie, znaczniki i widoczny fokus poprawne.

| Wariant | scrollY przed → po | scrollWidth = clientWidth | Zrzut |
| --- | --- | --- | --- |
| 1280×900, jasny | 7936 → 0 | 1265 | `f2-pr48-starsze-1280-light-2026-10-12T20-09-14-963Z.png` |
| 1280×900, ciemny | 7936 → 0 | 1265 | `f2-pr48-starsze-1280-dark-2026-10-12T20-09-14-963Z.png` |
| 375×812, jasny | 12708.5 → 0 | 360 | `f2-pr48-starsze-375-light-2026-10-12T20-09-14-963Z.png` |
| 375×812, ciemny | 12708.5 → 0 | 360 | `f2-pr48-starsze-375-dark-2026-10-12T20-09-14-963Z.png` |

Szerokość viewportu zawiera pasek przewijania; identyczne scrollWidth i clientWidth potwierdzają brak poziomego przepełnienia. Pełne pomiary, ID postów, dane przebiegów i deployment: `f2-pr48-starsze-2026-10-12T20-09-14-963Z.json`. Początkowy dowód 34 postów zachowano wyżej jako historię weryfikacji.
