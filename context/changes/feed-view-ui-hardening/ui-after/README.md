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
