# Zrzuty — sekcja „Wynik per źródło” na stronie przebiegu

Dowód bramki wizualnej fazy 4 (plan, pkt 7). Zrzuty z `npx vite --port 5197 --strictPort` i `playwright-cli` (Chromium, `.playwright/cli.config.json` worktree), z `szczur-radar-web/dev/mock-api.js` w trybie `data` (`AUTH = 'admin'`, `NOTIFICATIONS = 'unread'`). Rozmiary 1280×900 i 375×812, `screenshot --full-page`, motyw przez `set-color-scheme light|dark`.

Nazwy: `run-<fixtura>-<szerokość>-<motyw>.png`.

- `r-source-failed` — noc crona, Wykop i Reddit, `partial`: Wykop `pobrane`, Reddit `błąd` z błędem wiersza; „Wymaga uwagi: błąd pobrania: Reddit”.
- `r-source-empty` — zakres admina, Wykop i Reddit, `partial`: Reddit `bez postów`; „Wymaga uwagi: bez postów: Reddit (pobrano 0, pominięto 0)”.
- `fetching-empty` — stan pusty: kopia `mock-api.js` (poza repo), w której `r-running` jest jeszcze w kroku `fetch` (`sourceResults: []`, tylko krok `fetch` `running`, liczniki 0); sekcja mówi „Jeszcze żadne źródło nie zakończyło pobrania.”

## Macierz 7 stanów

| Stan | 1280 jasny | 1280 ciemny | 375 jasny | 375 ciemny |
| --- | --- | --- | --- | --- |
| default (źródło padło) | `run-source-failed-1280-light.png` | `run-source-failed-1280-dark.png` | `run-source-failed-375-light.png` | `run-source-failed-375-dark.png` |
| default (źródło bez postów) | `run-source-empty-1280-light.png` | `run-source-empty-1280-dark.png` | `run-source-empty-375-light.png` | `run-source-empty-375-dark.png` |
| empty (przebieg zakresu bez wierszy) | N/A — jak obok, motyw zmienia tylko kolory ról | `run-fetching-empty-1280-dark.png` | `run-fetching-empty-375-light.png` | N/A — jak obok |
| loading | N/A — sekcja istnieje tylko dla wczytanego przebiegu; stan „Wczytywanie przebiegu…” strony bez zmian (`RunDetailView.test.ts`, „says it is loading the run”) | N/A | N/A | N/A |
| error | N/A — sekcja istnieje tylko dla wczytanego przebiegu; błąd wczytania i odświeżenia to stany strony bez zmian (`RunDetailView.test.ts`, „offers a retry after an error”, „keeps the run on screen when a refresh fails”); błąd źródła pokazuje default `r-source-failed` | N/A | N/A | N/A |
| hover | N/A — sekcja nie ma elementów interaktywnych (lista, etykiety, `StatusBadge`) | N/A | N/A | N/A |
| focus-visible | N/A — sekcja nie ma elementów interaktywnych; fokus po wczytaniu trafia na `h1` jak dotąd | N/A | N/A | N/A |
| disabled | N/A — sekcja nie ma kontrolek | N/A | N/A | N/A |

Sekcja nie pokazuje się dla przebiegu innego niż zakres ani gdy API nie wysyła `sourceResults` (API sprzed S-20) — bez zrzutu, pokrywa to `RunDetailView.test.ts` („is not there …”).

## Pomiary

| Fixtura | Szerokość | `scrollWidth` / `clientWidth` |
| --- | --- | --- |
| `r-source-failed` (jasny, ciemny) | 375 | 375 / 375 |
| `r-source-empty` (jasny, ciemny) | 375 | 375 / 375 |
| `fetching-empty` (jasny) | 375 | 375 / 375 |

Długi błąd źródła łamie się w kolumnie (`overflow-wrap: anywhere`, jak błąd kroku).

## Styl

- Sekcja `part` między „Liczniki” a „Kroki”, lista `source-list` z wierszami `source-result`: te same reguły co lista kroków (wspólne selektory w `RunDetailView.vue`), tylko tokeny (`--space-*`, `--font-size-sm`, `--border`, `--destructive`); plik zostaje na liście `CLEANED` skanu `no literals`.
- Status źródła: `StatusBadge` z `sourceTone` (`src/runs.ts`: `succeeded` → `success`, `empty` → `warning`, `failed` → `destructive`) i etykietą z `labels.ts` (`sourceStatus`). Bez nowej roli koloru i bez nowego komponentu współdzielonego.
