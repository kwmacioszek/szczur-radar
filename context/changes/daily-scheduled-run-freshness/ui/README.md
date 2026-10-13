# Zrzuty — etykieta świeżości na feedzie `/`

Dowód bramki wizualnej fazy 4 (plan, pkt 5). Zrzuty z `npx vite --port 5191` i `playwright-cli` (Chromium, `--config` z głównego checkoutu): `/` z `szczur-radar-web/dev/mock-api.js` w trybie `data`, z wariantem etykiety `FRESHNESS` `date`, `none` albo `error` (kopia pliku z podmienioną stałą). Rozmiary 1280×900 i 375×812 (sam viewport, bez `--full-page`), motyw przez `set-color-scheme light|dark`. Mock loguje jako admin, więc nagłówek ma „Panel admina”. Etykieta pokazuje się tak samo roli `user` (trasa `GET /api/freshness` dla każdej zalogowanej roli, `FreshnessIT`).

Nazwy: `feed-freshness-<wariant>-<szerokość>-<motyw>.png`. Wariant `date` mocka to `2026-10-13T03:12:00Z`, czyli „13.10.2026, 05:12” w Warszawie.

## Macierz 7 stanów

| Stan | 1280 jasny | 1280 ciemny | 375 jasny | 375 ciemny |
| --- | --- | --- | --- | --- |
| default (data) | `feed-freshness-date-1280-light.png` | `feed-freshness-date-1280-dark.png` | `feed-freshness-date-375-light.png` | `feed-freshness-date-375-dark.png` |
| empty (brak udanego przebiegu) | `feed-freshness-none-1280-light.png` | `feed-freshness-none-1280-dark.png` | `feed-freshness-none-375-light.png` | `feed-freshness-none-375-dark.png` |
| error (odczyt 500) | `feed-freshness-error-1280-light.png` | N/A — etykiety nie ma, motyw niczego nie zmienia | N/A — jak obok | `feed-freshness-error-375-dark.png` |
| loading | N/A — w trakcie odczytu nic się nie renderuje, żeby nie mignął tekst zastępczy (test „shows nothing while loading”) | N/A | N/A | N/A |
| hover | N/A — etykieta nie ma kontrolek | N/A | N/A | N/A |
| focus-visible | N/A — etykieta nie ma kontrolek; fokus po zmianie strony trafia na `h1` jak dotąd (`FeedView.test.ts`) | N/A | N/A | N/A |
| disabled | N/A — etykieta nie ma kontrolek | N/A | N/A | N/A |

Przy błędzie odczytu lista działa normalnie: na zrzucie `error` jest 50 kart i nie ma komunikatu błędu, bo awaria etykiety nie dotyczy postów.

## Pomiary

| Wariant | Szerokość | `scrollWidth` / `clientWidth` | Tekst `.freshness` | Wysokość etykiety | Karty |
| --- | --- | --- | --- | --- | --- |
| `date` | 375 | 375 / 375 | „Dane zaktualizowane: 13.10.2026, 05:12” | 23 px (jedna linia) | 50 |
| `none` | 375 | 375 / 375 | „Dane nie były jeszcze aktualizowane.” | 23 px (jedna linia) | 50 |
| `error` | 375 | 375 / 375 | brak elementu | — | 50 |
| `date` | 1280 | 1280 / 1280 | „Dane zaktualizowane: 13.10.2026, 05:12” | 26 px | 50 |

## Styl

- `.freshness`: `font-size: var(--font-size-sm)`, `color: var(--text)` (drugorzędny tekst, para kontrastu `--text`/`--bg` już jest w `styleContract.test.ts`), `margin: 0 0 var(--space-4)`. Bez nowej roli koloru.
- `h1:has(+ .freshness)` zmniejsza odstęp pod nagłówkiem do `--space-2`, gdy etykieta jest; bez niej (`error`) odstępy są takie jak przed zmianą.
- `FeedView.vue` zostaje na liście `CLEANED` skanu `no literals`.

## Struktura

`<h1>` i etykieta stoją bezpośrednio w `<main>`, przed `<section aria-labelledby="feed-title">` z `aria-busy`. W widoku po `feed-view-ui-hardening` nagłówek był wewnątrz sekcji. Przeniesienie pozwala trzymać etykietę tuż pod nagłówkiem, a jednocześnie poza regionem, który przy zmianie strony jest `aria-busy`. Sekcja nadal ma nazwę „Posty” przez `aria-labelledby`, a fokus po zmianie strony wraca na `h1`.

## PR env (faza 5)

`pr-env-user-1280-light.png` i `pr-env-user-375-dark.png`: `/` w `szczur-radar-pr-50` po przebiegu uruchomionym przez crona (2026-10-13, start 06:42 UTC), zalogowany `freshness-check@example.com` z rolą `user` (nagłówek bez „Panel admina”). Etykieta „Dane zaktualizowane: 13.10.2026, 08:43” to koniec przebiegu RANGE (`2026-10-13T06:43:51Z`). Przebieg opisuje `context/deployment/deploy-plan.md`, sekcja S-05.
