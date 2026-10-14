# Zrzuty „po” — filtr feedu `/` i kitchen sink

Dowód bramki wizualnej zmiany `filter-by-assessment` (plan, faza 3 pkt 1 i 3).

- **Mock.** `npx vite --port 5173` i `playwright-cli run-code` (Chromium, `--config` z głównego checkoutu). `/` z `szczur-radar-web/dev/mock-api.js` w trybach `data`, `loading`, `error` i `empty`; każdy tryb to kopia pliku z podmienionym `MODE`, a trasy poprzedniego trybu są zdejmowane przez `page.unrouteAll`.
- **Kitchen sink.** Sekcje „Pola” i „Filtr” jako zrzuty elementów, bez mocka.
- **Rozmiary i motywy.** 1280×900 i 375×812, motyw przez `page.emulateMedia({ colorScheme })`.
- **Hover i fokus.** Wymuszone w przeglądarce (`hover()`, `Tab`), nie klasami CSS.
- **Nazwy plików:** `p3-<widok>-<stan>-<szerokość>-<motyw>.png` to mock, `pr54-*` to Railway PR env.

## Macierz 7 stanów

| Stan | 1280 jasny | 1280 ciemny | 375 jasny | 375 ciemny |
| --- | --- | --- | --- | --- |
| default | `p3-feed-data-1280-light.png` (bez filtra), `p3-feed-filtered-1280-light.png` (`q=orlen`, wzrostowo), `p3-kitchen-fields-1280-light.png`, `p3-kitchen-filter-1280-light.png` | `p3-feed-data-1280-dark.png`, `p3-feed-filtered-1280-dark.png`, `p3-kitchen-fields-1280-dark.png`, `p3-kitchen-filter-1280-dark.png` | `p3-feed-data-375-light.png`, `p3-feed-filtered-375-light.png`, `p3-kitchen-fields-375-light.png`, `p3-kitchen-filter-375-light.png` | `p3-feed-data-375-dark.png`, `p3-feed-filtered-375-dark.png`, `p3-kitchen-fields-375-dark.png`, `p3-kitchen-filter-375-dark.png` |
| hover | `p3-filter-hover-button-1280-light.png` („Filtruj”), `p3-filter-hover-select-1280-light.png` („Rodzaj celu”) | `p3-filter-hover-button-1280-dark.png`, `p3-filter-hover-select-1280-dark.png` | N/A: ekran dotykowy nie ma hover | N/A: jak obok |
| focus-visible | `p3-filter-focus-search-1280-light.png` (pole tekstowe), `p3-filter-focus-select-1280-light.png` („Rodzaj celu”), `p3-filter-focus-clear-1280-light.png` („Wyczyść filtry”) | `p3-filter-focus-{search,select,clear}-1280-dark.png` | `p3-filter-focus-{search,select,clear}-375-light.png` | `p3-filter-focus-{search,select,clear}-375-dark.png` |
| disabled | `p3-kitchen-fields-1280-light.png` (lista wyboru „Wyłączony”) | `p3-kitchen-fields-1280-dark.png` | `p3-kitchen-fields-375-light.png` | `p3-kitchen-fields-375-dark.png` |
| error | `p3-feed-error-filtered-1280-light.png` (błąd wczytania z aktywnym filtrem) | `p3-feed-error-filtered-1280-dark.png` | `p3-feed-error-filtered-375-light.png` | `p3-feed-error-filtered-375-dark.png` |
| empty | `p3-feed-no-match-1280-light.png` (`/?q=zzz`, pusty wynik filtra), `p3-feed-empty-1280-light.png` (brak postów) | `p3-feed-no-match-1280-dark.png`, `p3-feed-empty-1280-dark.png` | `p3-feed-no-match-375-light.png`, `p3-feed-empty-375-light.png` | `p3-feed-no-match-375-dark.png`, `p3-feed-empty-375-dark.png` |
| loading | `p3-feed-loading-filtered-1280-light.png` (aktywny filtr) | `p3-feed-loading-filtered-1280-dark.png` | `p3-feed-loading-filtered-375-light.png` | `p3-feed-loading-filtered-375-dark.png` |

Uwagi do macierzy:

- **disabled w formularzu: N/A.** „Filtruj” nigdy nie jest wyłączony. Wyłączona lista wyboru istnieje tylko na kitchen sink, jako wzorzec pola.
- **error pola: N/A.** Formularz nie waliduje danych po stronie klienta. Pole tekstowe ma `maxlength="100"`, a nieznane wartości z adresu są pomijane (`/?q=orlen&kind=xyz&direction=BULLISH` daje filtr samego `q`, sprawdzone w przeglądarce). Stan error to błąd wczytania listy z aktywnym filtrem.
- **hover na liście wyboru.** Pola, w tym pole daty, nie mają osobnego stylu hover; zostaje kursor przeglądarki. Zrzut pokazuje, że hover nie zmienia wyglądu.
- **Pusty wynik filtra a brak postów.** Pusty wynik filtra pokazuje w linii wyniku „Żaden post nie pasuje do filtra.” i przycisk „Wyczyść filtry”. Brak postów pokazuje „Brak ocenionych postów…” bez linii wyniku.

## Pomiary w przeglądarce (mock, tryb `data`, 1280×900)

| Akcja | Adres po akcji | Fokus | Linia wyniku |
| --- | --- | --- | --- |
| `  ORLEN ` w polu tekstowym i Enter, na `/?page=1` | `/?q=ORLEN` (bez `page`) | zostaje w polu wyszukiwania | „Pasujące do filtra: 20” |
| „Rodzaj celu” company, potem „Starsze” (Enter) | `/?kind=company&page=1` | `H1#feed-title` | „Pasujące do filtra: 80”, „Strona 2 z 2”, 30 kart (`p3-feed-filter-paging-1280-light.png`) |
| Wstecz | poprzedni filtr | — | wyniki poprzedniego filtra |
| „Wyczyść filtry” w pustym wyniku | `/` | `H1#feed-title` | „Ocenione posty: 120”, „Strona 1 z 3” |
| „Filtruj” z niezmienionym filtrem na `/?kind=company&page=1` (po impl-review F5) | `/?kind=company` | zostaje na „Filtruj” | „Strona 1 z 2”, bez przewinięcia |
| „Wyczyść filtry” w formularzu (po impl-review F2) | `/` | pole „Spółka lub temat” | „Ocenione posty: 120” |

- Enter w polu tekstowym zatwierdza formularz w prawdziwej przeglądarce. W jsdom test sprawdza tylko warunki zatwierdzenia.
- Przy 375 px w każdym trybie i stanie nie ma poziomego przewijania (`scrollWidth` = `clientWidth` = 375).
- Wysokość kontrolek: pole tekstowe, obie listy wyboru i przyciski mają wysokość przycisku przy każdej szerokości (`calc(1.45rem + 14px)`, po impl-review F3): 40,1 px przy 1280 (jedna linia) i 37,2 px przy 800 (przyciski zawijają się pod pola), tak samo pola daty i przycisk w `/admin`. Przed poprawką w fazie 2 przy 1280 było 36, 30 i 40 px.

## Liczniki skanu

| Plik | Literały kolorów, klasy palety, wartości arbitralne | `px`/`rem` w piśmie, odstępach i promieniach | Na liście `CLEANED` |
| --- | --- | --- | --- |
| `src/components/FeedFilter.vue` | 0 | 0 | tak (nowy) |
| `src/views/FeedView.vue` | 0 | 0 | tak |
| `src/components/PostCard.vue` | 0 | 0 | tak |

Skan `no literals` w `src/styleContract.test.ts` przechodzi (`npm test`). `style.css` dostał regułę pól (`search`, `select`, wysokość 40 px), ale żadnych nowych tokenów ani par kolorów.

## Lista kontrolna `/10x-ui`

Według `.claude/skills/10x-ui/references/ui-quality-checklist.md`.

### Charges

- [x] Punkt wyjścia: zarzuty D2 (filtry i ich stan pusty) i D3 (układ nad listą), przekazane tej zmianie przez `feed-view-ui-hardening`. To funkcja, nie naprawa istniejącego widoku: `research.md` i plan opisują je jako decyzje, nie jako listę `## Charges`.
- [x] Brakujące tokeny: 0 literałów przed i po w zmienionych plikach (tabela wyżej).
- [x] Brakujący współdzielony komponent: formularz to `FeedFilter` w `src/components/`. Przyciski to `AppButton`, pola mają styl globalny. Nie powstał drugi przycisk ani drugie pole.
- [x] Wejścia:
  - bez sesji `/api/posts` daje 401 (PR env);
  - bez danych jest stan pusty (`p3-feed-empty-*`);
  - prosto z linku: `/?q=…&kind=…&direction=…` odtwarza filtr, a złe wartości są pomijane.
- [x] Zarzuty poza planem jako deferred:
  - tekst „Wczytywanie strony 1…” po zmianie filtra (uwaga z fazy 2);
  - wygląd `input[type=search]` w Safari (niesprawdzony);
  - D4 (`AppHeader` przy 375 px) pozostaje poza zakresem.

### Contract

- [x] Tokeny: pismo, odstępy, promienie i kolory z `:root` w `szczur-radar-web/src/style.css`.
- [x] Warstwa komponentów repo (`src/components/`): nowy `FeedFilter`, `PostCard` z rodzajem celu; oba na kitchen sink.
- [x] Macierz 7 stanów: tabela wyżej, każda komórka ze zrzutem albo N/A z powodem.
- [x] Desktop i jedna szerokość mobilna: 1280×900 i 375×812.
- [x] Fokus i nazwy:
  - pierścień `--ring` na polu tekstowym, listach wyboru, „Filtruj” i „Wyczyść filtry”;
  - etykiety pól to `<label>` z tekstem;
  - formularz ma `role="search"` i `aria-label="Filtr postów"`;
  - liczba wyników jest w regionie `role="status"`, obecnym w DOM od pierwszego renderu.
- [x] Ciemny motyw tylko na warstwie tokenów, oba motywy na zrzutach. Rozwinięta lista wyboru dostała tło `--bg` i tekst `--text-h`.

### Guard

- [x] `REPOSITORY_GUIDELINES.md`, sekcja „UI”:
  - `FeedFilter` w katalogu komponentów;
  - pola formularzy mają jeden globalny styl w `style.css` i nie dostają własnego w widoku;
  - mock `/api/posts` obsługuje `q`, `kind` i `direction`.
- [x] Kontrola: `src/styleContract.test.ts` z `FeedFilter.vue` na liście `CLEANED` (Vitest, job „web unit tests” w CI).

### Gate

- [x] Kitchen sink (sekcje „Pola” i „Filtr”) i zrzuty widoku z mockiem w obu motywach i przy 375 px. Baseline `toHaveScreenshot`: N/A, bo w repo nie ma tego narzędzia.
- [ ] `/10x-impl-review`: po fazie 3.
- [x] Zakres: jeden widok (`FeedView`) z formularzem i kartą. Poza widokiem tylko reguła pól w `style.css`, mock, kitchen sink i reguła w repo.

## PR env

- **Środowisko i adres:** środowisko `szczur-radar-pr-54`, data 2026-10-13, adres https://szczur-radar-web-szczur-radar-pr-54.up.railway.app.
- **Wdrożenia:**
  - api z `089023b`, czyli głowy gałęzi dla plików API; commit fazy 2 nie zmienia API, więc deploy api był `SKIPPED`;
  - web z `d1930d7`, czyli głowy gałęzi z kodem SPA.
  - Pierwszy deploy web po pushu fazy 2 wziął starszy `089023b` (znany problem z `deploy-plan.md`). Naprawił to `railway redeploy --from-source`, a `meta.commitHash` ostatniego `SUCCESS` to `d1930d7`.
- **CI na `d1930d7`:** zielone we wszystkich jobach.
- **Dane:** konto admina założone przez operatora (`create-admin` przez `railway ssh`), jeden przebieg zakresu dat w `/admin`. Feed ma 71 ocenionych postów z 74 celami.
- **Lokalizacja bazy:** `SELECT datlocprovider, datcollate, datctype FROM pg_database WHERE datname = current_database()` przez `railway ssh -s postgres` dało `c | en_US.utf8 | en_US.utf8` (PostgreSQL 18.6, libc). To ta sama lokalizacja co w Testcontainers, w której `ILIKE` składa wielkość polskich liter.

### API, zalogowana sesja (faza 1, 2026-10-13)

Wszystkie wyniki zgadzają się z wynikiem policzonym niezależnie w przeglądarce z pełnego feedu: ten sam zbiór postów, ta sama kolejność i `total`.

| Sprawdzenie | Filtr | Wynik |
| --- | --- | --- |
| Tekst | `asts` / `etf` / `xtb` | 7 / 5 / 2 |
| Ten sam cel | `q=centra danych&direction=bullish` (post `06dcf579…`: „centra danych” bez stanowiska, wzrostowe „Kęty”) | 0 |
| Ten sam cel, właściwy kierunek | `q=centra danych&direction=not_expressed` | 1, ten post |
| Polska litera, odwrotna wielkość | `q=KĘTY` (w danych „Kęty”) | 1 |
| Każdy rodzaj | `macro_topic` / `company` / `asset` / `sector` / `other` | 13 / 25 / 7 / 3 / 1 |
| Każdy kierunek | `not_expressed` / `bullish` / `bearish` / `mixed` | 19 / 20 / 9 / 2 |
| Wszystkie trzy | `asts` + `company` + `bullish` | 3 |
| `%`, `_` | `q=%`, `q=_` | 0 i 0 |
| Pusty tekst | `q=␣␣` | 71 (bez filtra) |
| Złe wartości | `kind=xyz`, `direction=BULLISH`, `kind=Company`, `q` o długości 101 | 400 `invalid_request` |

### SPA na prawdziwych danych (faza 3, web `d1930d7`)

| Akcja w formularzu | Adres | Linia wyniku | Pierwsza karta (`dt`) |
| --- | --- | --- | --- |
| `KĘTY` i Enter | `/?q=K%C4%98TY` | „Pasujące do filtra: 1” | „Kęty · spółka \| centra danych · sektor” |
| `centra danych` + „wzrostowo”, „Filtruj” | `/?q=centra+danych&direction=bullish` | „Żaden post nie pasuje do filtra.” (`pr54-same-target-1280-light.png`) | — |
| Ten sam tekst + „bez stanowiska” | `/?q=centra+danych&direction=not_expressed` | „Pasujące do filtra: 1” | ten sam post |
| `/?q=asts` (małe litery, dane „ASTS”) | — | „Pasujące do filtra: 7” | „ASTS · spółka \| RKLB · spółka” |
| `/?kind=macro_topic` | — | „Pasujące do filtra: 13” | „S&P500 · temat makro” |
| `/?direction=bearish` | — | „Pasujące do filtra: 9” | — |
| `/?q=zzz`, potem „Wyczyść filtry” | `/` | „Żaden post…”, potem „Ocenione posty: 71”, fokus na `H1#feed-title` | — |
| „Starsze” bez filtra | `/?page=1` | „Ocenione posty: 71”, 21 kart, fokus na `H1#feed-title` | — |

- **Zmiana strony przy filtrze.** Na prawdziwych danych żaden filtr nie przekracza 50 postów (największy to `company` z 25), więc „Starsze” jest przy filtrze wyłączone. Przejście na drugą stronę z filtrem sprawdzono na mocku (`/?kind=company`, wiersz w „Pomiarach”) i w teście (c) `FeedView.test.ts`.
- **Zrzuty** `/?q=asts&kind=company&direction=bullish` („Pasujące do filtra: 3”) i `/?q=zzz`: `pr54-filtered-{1280,375}-{light,dark}.png`, `pr54-no-match-{1280,375}-{light,dark}.png`. Przy 375 px nie ma poziomego przewijania.
- **Produkcja** ma osobną bazę. Wyszukiwanie z polską wielką literą i „ten sam cel” sprawdza runbook w issue #17 po merge.
