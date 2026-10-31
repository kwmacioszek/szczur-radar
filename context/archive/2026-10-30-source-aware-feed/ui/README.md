# Zrzuty — pochodzenie posta i świeżość per źródło

Dowód bramki wizualnej fazy 3 (plan, pkt 1). Zrzuty z `npx vite --port 5197 --strictPort` i `playwright-cli` (Chromium, `.playwright/cli.config.json` worktree), z `szczur-radar-web/dev/mock-api.js` w trybie `data` (`AUTH = 'admin'`, `NOTIFICATIONS = 'unread'`). Warianty `FRESHNESS` inne niż `'date'` to kopia atrapy poza repo z podmienioną stałą. Motyw przez `set-color-scheme light|dark`. Treść postów pochodzi z atrapy, nie z prawdziwych danych.

Nazwy: `feed-<FRESHNESS>-<szerokość>-<motyw>.png` i `post-reddit-<szerokość>-<motyw>.png`.

- Feed: viewport 1280×1500 i 375×2130, bez `--full-page`. Lista ma 50 postów, a pełna strona ma ponad 10 000 px i na zrzucie niczego nie widać. Te rozmiary obejmują etykietę i sześć pierwszych kart, wśród nich karty `r/stocks` i `r/ValueInvesting` (post `feed-5`, najdłuższa nazwa kanału z pierwszej konfiguracji).
- Strona posta: `/posts/feed-5`, 1280×900 i 375×812, `--full-page`.

## Macierz 7 stanów

| Stan | 1280 jasny | 1280 ciemny | 375 jasny |
| --- | --- | --- | --- |
| default, feed, równe daty (`'date'`) | `feed-date-1280-light.png` | `feed-date-1280-dark.png` | `feed-date-375-light.png` |
| default, feed, różne daty (`'apart'`) | `feed-apart-1280-light.png` | N/A — motyw zmienia tylko kolory ról | `feed-apart-375-light.png` |
| default, strona posta | `post-reddit-1280-light.png` | `post-reddit-1280-dark.png` | `post-reddit-375-light.png` |
| empty (`'none'`, „Dane nie były jeszcze aktualizowane.”) | `feed-none-1280-light.png` | N/A — ta sama reguła koloru co `'date'` | N/A — jedna krótka linia |
| error (`'error'`, 500) | bez zrzutu: etykiety nie ma, lista działa; sprawdzone na kopii atrapy (`.freshness` nie istnieje, 50 kart, „Ocenione posty: 120”) oraz w `FeedView.test.ts` („shows nothing when it cannot be read, and the posts still render”) | N/A | N/A |
| loading | bez zrzutu: etykiety nie ma, dopóki odpowiedź nie przyjdzie (`FeedView.test.ts`, „shows nothing while loading”) | N/A | N/A |
| hover | N/A — etykieta i pochodzenie posta to tekst, nie kontrolka | N/A | N/A |
| focus-visible | N/A — tekst, nie kontrolka; obrys na tytule strony posta to fokus po wczytaniu, jak dotąd | N/A | N/A |
| disabled | N/A — tekst, nie kontrolka | N/A | N/A |

## Pomiary

| Widok | Szerokość | `scrollWidth` / `clientWidth` |
| --- | --- | --- |
| feed `'date'`, `'apart'` | 375 | 375 / 375 |
| feed `'date'`, `'apart'`, `'none'` | 1280 | 1280 / 1280 |
| strona posta `feed-5` | 375 | 375 / 375 |
| strona posta `feed-5` | 1280 | 1280 / 1280 |

Bez przewijania w poziomie w żadnym widoku.

## Co widać na zrzutach

- Etykieta przy 375 px łamie się między źródłami: pierwsza linia kończy się separatorem ` · `, a drugie źródło z datą zaczyna następną. Wpis źródła nie rozpada się w środku (`.source-update`, `white-space: nowrap`).
- Przy 1280 px kolumna metadanych karty ma 9rem i pochodzenie z `ValueInvesting` zajmuje dwie linie: najpierw nazwa źródła z separatorem na końcu, pod nią `r/ValueInvesting`. Nazwa kanału nie rozpycha kolumny. Przy 375 px metadane idą wierszem, więc pochodzenie mieści się w jednej linii.
- Etykieta i pochodzenie posta używają istniejących ról i skali: bez nowej roli koloru, bez nowego komponentu współdzielonego.

## Dowód w PR env

`szczur-radar-pr-133`, API i web wdrożone z `0181835`, 2026-10-30. Zakres to D-2 (28.10.2026).

1. Przebieg `ce0629e3-3492-4169-a59b-ee43329ec4d9` (oba źródła): `partial`, `wykop` `succeeded` (fetched 360), `reddit` `succeeded` (fetched 79). `BEFORE`: `{"sources":[{"source":"wykop","lastUpdatedAt":"2026-10-30T20:02:32.342643Z"},{"source":"reddit","lastUpdatedAt":"2026-10-30T20:02:32.342643Z"}]}`
2. Żądanie z `"sources":["reddit"]` zwróciło `[retry, range]`. Przebieg `f8bd2aa7-dbd1-46e8-bce0-8e914b2efb76`: `succeeded`, `reddit` `succeeded` (fetched 79, new 0). `AFTER`: `{"sources":[{"source":"wykop","lastUpdatedAt":"2026-10-30T20:02:32.342643Z"},{"source":"reddit","lastUpdatedAt":"2026-10-30T20:08:54.759915Z"}]}`

Data Reddita przesunęła się, data Wykopu została bez zmian.

3. Wdrożony SPA (`playwright-cli`, sesja konta testowego), tekst `.freshness`: `Dane zaktualizowane: Wykop 30.10.2026, 21:02 · Reddit 30.10.2026, 21:08`; `.origin`: `Reddit · r/stocks`, `Wykop · #gielda`.
