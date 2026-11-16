# F-05: testy w przeglądarce w CI na atrapie API — plan implementacji

## Overview

Do `szczur-radar-web` wchodzi Playwright Test. Testy chodzą na produkcyjnym buildzie (`vite preview`), a `/api/*` obsługuje atrapa `dev/mock-api.js`, której tryby (sesja, rola, dane, pusto, błąd, ładowanie i reszta) ustawia każdy test. Smoke obecnych przepływów (logowanie, wylogowanie, wygaśnięcie sesji, rola, aktywacja, feed z filtrem, post, panel admina, dzwonek) chodzi w CI przy każdym PR w nowym jobie `web-e2e`, który blokuje wdrożenie. To siatka regresji przebudowy UI w S-46–S-51 i podstawa F-06 (zrzuty porównawcze).

## Current State Analysis

- **Atrapa** `szczur-radar-web/dev/mock-api.js` (701 linii, S-43) to jedno wyrażenie `async page => {…}` dla `playwright-cli run-code`. Przechwytuje `/api/*` przez `page.route` (`:532`), czyli działa w procesie Node wywołującego, a nie w przeglądarce. Tryby to stałe w tekście (`:428-436`), a inny tryb wymaga kopii pliku z podmienioną linią (`:3-4`). Stan (sesja, przeczytane powiadomienia, uruchomione przebiegi, przywrócone posty) żyje w jednej instalacji (`:272`, `:440`, `:497`, `:658`). Zegar Node wyznacza „dziś” (`:443`, odczyt raz przy instalacji), czasy przebiegu 1 (`ago`, `:140`, przy każdym żądaniu), `readAt` (`:286`) i `createdAt` nowych przebiegów (`:508`).
- **Strażnik S-43** `src/contract/mockApi.ts` wczytuje tekst atrapy (`?raw`), podmienia stałe regexem (`withConstants`, `:44-52`; brak linii rzuca) i wykonuje go przez `(0, eval)` z udawanym `page`. `src/contract/mockApi.test.ts` (931 linii) sprawdza kształty względem plików kontraktu (`ROUTES`, `:168-326`), zachowanie (`ROWS`, `:469-629`), bramkę sesji, zawieszenia na sztucznych zegarach (`:753-829`) i niezmienniki danych. Jego `NOW` to `2026-10-20T10:00Z` (`:162-166`). `src/contract/rules.test.ts` czyta ten sam tekst i szuka w nim literałów `pageNumber > <pageMax>` i `size <= <pageMaxSize>` (`:95-101`) oraz kodów błędów (`:336-341`).
- **Brak infrastruktury.** Nie ma `@playwright/test`, `playwright.config.*`, katalogu testów ani skryptu `e2e` (`package.json:6-11`). CI ma `web-unit` (`npm ci && npm test`, `.github/workflows/ci.yml:109-125`) i `web-container-smoke` (`:127-161`, jedyna bramka typecheck i build frontu). Nie ma joba z przeglądarką. Nie istnieją `context/foundation/test-stack.md` ani `test-plan.md`.
- **Pułapki konfiguracji:**
  - Vitest nie ma własnego `include` ani `exclude` (`vitest.config.ts:6-16`), więc domyślny glob wciągnąłby `e2e/*.spec.ts`.
  - `vue-tsc -b` sprawdza tylko `src/**` (`tsconfig.app.json:14`) i dwa pliki konfiguracji (`tsconfig.node.json:22`).
  - `/kitchen-sink` istnieje tylko pod `import.meta.env.DEV` (`src/router/index.ts:42-52`), więc nie ma go w `vite preview`. To dotyczy F-06, nie F-05.
  - Port preview to domyślne 4173 (`vite.config.ts` go nie ustawia).
  - `.playwright/cli.config.json` jest w git, a nagłówek atrapy (`:116-117`) mówi, że nie jest.
- **SPA:**
  - Sesja jest czytana raz na załadowanie strony (`src/auth/session.ts:15-33`). Strażnik routera odsyła na `/login?redirect=<fullPath>` (`src/router/index.ts:60-72`). 401 w trakcie pracy czyści sesję i robi to samo (`:75-80`, `src/api/client.ts:95-103`). Logowanie wraca pod `redirect` (`src/views/LoginView.vue:43-44`, `src/router/redirect.ts:5-15`).
  - Polling: `/admin` odświeża co 10 s, gdy jest aktywny przebieg, a przebieg 1 atrapy jest `running` (`src/views/AdminView.vue:203,291-293`). Strona przebiegu odświeża co 10 s (`src/views/RunDetailView.vue:31,142`). Dzwonek odświeża po każdej nawigacji, przy widoczności karty i co 60 s na stronach admina (`src/components/NotificationBell.vue:27,77-92`).
  - Domyślne daty formularza admina to „wczoraj” z zegara przeglądarki (`src/views/AdminView.vue:33-36`), a atrapa odrzuca `to > today` z zegara Node (`mock-api.js:527`).
- **Decyzje z historii:** S-43 zostawiło atrapę jako skrypt `run-code`, bo sandbox `run-code` nie ma `import`, `require` ani `process`. Przepisanie na ESM lub middleware Vite oraz Playwright odłożono do M-5 (`context/archive/2026-11-11-contract-bound-mocks/plan-brief.md:29,48-57`, `plan.md:74,82`). Przegląd S-43 (F2) zostawił świadomie: pierwszy start przebiegu przy `running` jest przyjęty (`reviews/impl-review.md:44-61`).

## Desired End State

- `npm run e2e` w `szczur-radar-web/` buduje aplikację, uruchamia `vite preview` i przechodzi 18 testów Chromium. Każdy test ustawia swoje tryby atrapy przez `test.use({ modes: … })`, ma świeży stan atrapy i zegar przypięty do `2026-10-20T10:00:00Z` (przeglądarka i atrapa).
- `dev/mock-api.js` to funkcja `async (page, modes = {}) => control`: walidowane tryby, opcja `now`, uchwyt `endSession()` i `dispose()`. `playwright-cli run-code --filename=…` działa jak dziś (tryby domyślne). Inny tryb w `playwright-cli` to jedna komenda, bez kopii pliku.
- Strażnik S-43 przekazuje tryby obiektem. Nieznany tryb lub wartość rzuca, a tabela wartości trybów po stronie TS i po stronie atrapy jest porównywana w obie strony.
- Job CI `web-e2e` chodzi przy każdym PR w kontenerze Playwrighta o wersji równej `@playwright/test`, z raportem jako artefaktem przy błędzie, i jest w `needs:` joba `railway-config`.
- `context/foundation/test-stack.md` ma sekcję `## E2E` według schematu `/10x-e2e`, więc kolejne wycinki dopisują testy przez `/10x-e2e`.

### Key Discoveries:

- Atrapa działa w Node (`page.route`, `mock-api.js:532`), więc zegar przypina się w dwóch miejscach: opcja `now` atrapy i `page.clock.setFixedTime` przeglądarki. Ten sam moment co `NOW` strażnika (`mockApi.test.ts:162`).
- `playwright-cli run-code` wywołuje funkcję z jednym argumentem `page` (`playwright-cli run-code --help`, 0.1.22), więc drugi parametr z wartością domyślną nie zmienia procedury zrzutów. Plik jest jednym wyrażeniem, więc `run-code "async page => ($(cat …))(page, {…})"` daje dowolny tryb bez kopii.
- Sesji nie trzeba zapisywać (`storageState`): tryb `AUTH` atrapy ustawia ją przy instalacji, a tylko testy logowania przechodzą przez formularz.
- `installMock(constants)` w strażniku już przyjmuje obiekt (`mockApi.ts:55`), więc 27 wywołań `installMock(` w `mockApi.test.ts` zostaje w tej samej postaci. Zmienia się wnętrze `installMock` i typy w teście: import `MockConstant` (`:2`) znika, alias `Constants` (`:469`) przechodzi na `MockModes`, a `it.each` z `{ QUALITY: quality }` (`:835-836`) dostaje krotkę `as const`.
- Pułapki lokatorów:
  - `link "Posty"` jest dwa razy na `/posts/:id` (nagłówek i powrót), `link "Panel admina"` dwa razy na `/admin/runs/:id`;
  - `button "Wyczyść filtry"` jest dwa razy przy pustym wyniku filtra (`FeedFilter.vue:75`, `FeedView.vue:193`), oba w `main`. Formularz (`role="search"`) jest pierwszy w DOM, więc przycisk wyniku to `.last()`;
  - „Spróbuj ponownie” i „Wczytywanie…” powtarzają się w sekcjach `/admin`;
  - `getByLabel("Hasło")` łapie też „Nowe hasło”, a `getByLabel("Do")` na `/admin` także dzwonek i przyciski „Wpisz do formularza: …”;
  - `/admin` ma też 2 checkboxy źródeł, więc checkboxy nieudanych postów szukaj w regionie „Nieudane posty”.

  Zapytania zawężaj do `getByRole('banner')` / `getByRole('main')` / regionu sekcji, a etykiety `exact: true`.
- Fallback CI to pula runnerów self-hosted z użytkownikiem `gha` bez sudo (`tools/ci-runner/README.md`), więc `playwright install --with-deps` (apt) by tam padł. Kontener `mcr.microsoft.com/playwright:v<wersja>-noble` nie potrzebuje sudo i przypina przeglądarkę oraz czcionki, czego F-06 i tak będzie potrzebować. Na puli self-hosted jest niesprawdzony: runner działa tam jako `gha` (`tools/ci-runner/setup.sh:90`) i jest trwały, a kontener działa jako root, więc job na końcu oddaje katalog roboczy jego właścicielowi (krok `chown` w fazie 4).
- `@playwright/test` 1.65.1 (stabilne, 2026-11-13) jest najnowsze, a 1.65.0 jest z 2026-11-05. Przypięcie dokładnej wersji (`-E`) trzyma zgodność z tagiem obrazu CI.

## What We're NOT Doing

- **Zrzuty porównawcze i test dostępności** (F-06), w tym serwer dev dla `/kitchen-sink`, szerokość 375 px i drugi motyw w E2E.
- **Warianty atrapy poza wygaśnięciem sesji** (decyzja z wywiadu). Zostają dla wycinków, które ich potrzebują:
  - błąd i ładowanie `/api/auth/me` (S-48);
  - 500 i zawieszenie logowania (S-49);
  - osobne zawieszenie lub błąd sekcji `/admin` i ładowanie `/api/freshness` (S-50 / S-48).
- **Pełna macierz 7 stanów** na obecnych widokach: widoki przebudują S-47–S-51.
- **Atrapa jako moduł ESM albo serwer HTTP / middleware Vite**: zostaje plikiem dla `run-code` (decyzja z wywiadu, ciągłość S-43).
- **Projekt `setup` i `storageState`**: sesję daje tryb `AUTH`.
- **Inne przeglądarki niż Chromium.**
- **Zmiana zachowania SPA ani API.** Kod widoków się nie zmienia. W `src/` zmienia się tylko strażnik atrapy (`src/contract/`).
- **Poprawka F2 z S-43** (pierwszy start przy `running` przyjęty): test startu opiera się na obecnym, opisanym w nagłówku zachowaniu.
- **Weryfikacja w Railway PR env**: zmiana nie dotyka serwowanej aplikacji ani API (build SPA bez zmian w `src/` poza strażnikiem, który nie trafia do bundla). Dowodem jest CI, w tym `web-container-smoke`.
- **`context/foundation/test-plan.md`** (`/10x-test-plan` jest odłożony w roadmapie). Ryzyka testów smoke nazywa ten plan.

## Implementation Approach

Najpierw atrapa i jej strażnik (faza 1), bo cała reszta stoi na jej nowym kontrakcie: tryby jako parametr, zegar i uchwyt. Potem infrastruktura z jednym testem seed udowodnionym na zimnym serwerze (faza 2). Na tym wzorcu powstaje smoke (faza 3), a każdy jego plik jest sprawdzony celowym zepsuciem SPA. Na końcu CI i dokumenty (faza 4), dopiero gdy testy są stabilne przy powtórzeniach, bo job blokuje wdrożenie.

## Critical Implementation Details

- **Zegar w dwóch miejscach, przed pierwszym `goto`.** Fixture woła `page.clock.setFixedTime(FIXED_NOW)` przed instalacją atrapy i przed nawigacją testu, a atrapie przekazuje `now: FIXED_NOW`. Dzięki temu „wczoraj” formularza admina (`2026-10-19`) zgadza się z „dziś” atrapy (`2026-10-20`), a czasy względne i okna D-n są stałe. `setFixedTime` zostawia działające timery, więc polling `/admin` i strony przebiegu chodzi jak w produkcji. `Date` stoi jednak w miejscu, więc logika licząca różnicę `Date` nie rusza: dzwonek uznaje listę za świeżą (`src/notificationsStore.ts:60`, `FRESH_MS`) i po pierwszym odczycie nie czyta jej ponownie (nawigacja, widoczność karty i 60 s nic nie robią). Test, który potrzebuje upływu czasu, przesuwa go sam kolejnym `page.clock.setFixedTime(<późniejsza chwila>)`. Testy F-05 nie czekają na stan, który polling zmienia. Licznik dzwonka maleje lokalnie po odpowiedzi 204, bez ponownego odczytu.
- **Zawieszenia a zamknięcie strony.** `hang()` atrapy czeka 60 s w Node. Po zakończeniu testu strona jest zamknięta, a późniejsze `route.fulfill` dałoby błąd poza testem. Fixture po teście woła `control.dispose()`, które kończy oczekujące zawieszenia bez odpowiadania. Każdy późniejszy fulfill na zamkniętej stronie atrapa ignoruje. Sprawdza to test strażnika i bieg z `--repeat-each`.
- **Plik atrapy zostaje jednym wyrażeniem.** Nie dodawaj eksportu, średnika na końcu ani kodu poza funkcją: `run-code`, `eval` strażnika i fixture Node wczytują ten sam tekst.

## Faza 1: Atrapa z trybami jako parametr

### Overview

`dev/mock-api.js` przyjmuje tryby jako drugi parametr, z walidacją, opcją zegara i uchwytem sterowania. Strażnik S-43 przechodzi na ten kontrakt i pilnuje go, a dokumenty atrapy opisują nową procedurę.

### Changes Required:

#### 1. Atrapa

**File**: `szczur-radar-web/dev/mock-api.js`

**Intent**: Zamienić stałe trybów w tekście na parametr, żeby test i `playwright-cli` wybierały tryb bez kopii pliku, a każdy test miał własny stan i zegar. Dodać wygaśnięcie sesji w trakcie pracy, jedyny nowy wariant F-05.

**Contract**:
- Sygnatura: `async (page, modes = {}) => ({ endSession, dispose, modeValues })`.
- **`MODE_VALUES`**: tabela w atrapie, klucz trybu → lista dozwolonych wartości, pierwsza domyślna. Klucze to dzisiejsze `MODE`, `NOTIFICATIONS`, `AUTH`, `LOGIN`, `START`, `ACTIVATION`, `FRESHNESS`, `MISSING`, `QUALITY`, a wartości te z nagłówka (np. `MODE`: `data`, `loading`, `error`, `empty`, `loading-next`, `error-next`, `raw-loading`, `raw-error`).
- **Walidacja**: nieznany klucz albo wartość spoza listy rzuca `Error` z nazwą klucza i wartości przed rejestracją trasy.
- **`modes.now`** (ISO instant, opcjonalny): każdy odczyt czasu atrapy (`today`, `ago`, `readAt`, `createdAt` nowych przebiegów) idzie przez jedną funkcję zegara. Z `now` czas stoi na tej chwili, bez niego płynie jak dziś.
- **`endSession()`**: kończy sesję atrapy jak wygaśnięcie po stronie serwera. Każde kolejne wywołanie chronionej trasy daje 401 `not_authenticated`, a ponowne logowanie formularzem działa.
- **`dispose()`**: kończy oczekujące zawieszenia bez odpowiedzi. Po nim atrapa nie woła `fulfill`.
- **`modeValues`**: tabela `MODE_VALUES`, dla strażnika.
- **Nagłówek**:
  - tryby opisane jako klucze `modes` zamiast „kopii pliku z podmienioną stałą”;
  - procedura `playwright-cli` z jedną komendą dla innego trybu: `playwright-cli run-code "async page => ($(cat szczur-radar-web/dev/mock-api.js))(page, { MODE: 'error' })"`;
  - opis `now`, `endSession` i `dispose`;
  - poprawione zdanie o `.playwright/cli.config.json`: jest w git.
- Literały `pageNumber > 21474836` i `size <= 100` oraz literały `detail: '<code>'` zostają w tekście, bo czyta je `rules.test.ts`.

#### 2. Typ trybów po stronie TS

**File**: `szczur-radar-web/src/contract/mockModes.ts` (nowy)

**Intent**: Jedno typowane źródło trybów dla strażnika i fixture E2E, żeby test dostał podpowiedzi i błąd kompilacji przy literówce.

**Contract**: `MOCK_MODE_VALUES` (`as const`, klucz → krotka wartości, w kolejności atrapy), `type MockModes = { [K in keyof typeof MOCK_MODE_VALUES]?: (typeof MOCK_MODE_VALUES)[K][number] } & { now?: string }`, `interface MockControl { endSession(): void; dispose(): void; modeValues: Record<string, readonly string[]> }`, `FIXED_NOW = '2026-10-20T10:00:00Z'` (wspólne z `NOW` strażnika).

#### 3. Strażnik

**File**: `szczur-radar-web/src/contract/mockApi.ts`, `szczur-radar-web/src/contract/mockApi.test.ts`

**Intent**: Strażnik instaluje atrapę przez parametr zamiast podmiany tekstu i pilnuje nowego kontraktu.

**Contract**:
- `installMock(modes: MockModes = {})` wykonuje tekst atrapy raz i woła go z `(page, modes)`. Zwraca `call()` jak dziś oraz `control`. `withConstants` i `MockConstant` znikają.
- `NOW` w teście zostaje liczbą: `Date.parse(FIXED_NOW)` (idzie do `Intl…format(NOW)` i `vi.setSystemTime`).
- Nowe przypadki w `mockApi.test.ts`:
  - nieznany klucz trybu i nieznana wartość rzucają;
  - `modeValues` atrapy równa się `MOCK_MODE_VALUES` w obie strony (klucze i wartości w kolejności);
  - instalacja z każdą wartością każdego trybu nie rzuca;
  - po `endSession()` `/api/auth/me` i `/api/posts` dają 401 `not_authenticated`, a logowanie potem przywraca sesję;
  - z `now` okno `missing-days` i `assessment-quality` liczy się od dnia `now`, a `lastActivityAt` przebiegu 1 to `now − 20 s`;
  - po `dispose()` zawieszone żądanie (`MODE: 'loading'`) kończy się bez odpowiedzi.

  Istniejące wiersze zostają.

#### 4. Wytyczne

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Zdanie o strażniku i procedura zrzutów mają opisywać nowy kontrakt.

**Contract**: w akapicie kontraktu SPA (`:44`) „a mode constant of the mock is replaced through `installMock`, which throws when the constant is gone” → tryby jako parametr, walidowane, a tabela `MOCK_MODE_VALUES` porównywana z atrapą w obie strony. W „Visual check” (`:55`) inny tryb przez `run-code` z obiektem trybów.

#### 5. Roadmapa i jej SVG

**File**: `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: `roadmap.md` zmienił status F-05 (`ready` → `planning`), a wytyczne każą przy każdej zmianie roadmapy poprawić dane skryptu i wygenerować SVG. Idzie to do pierwszego commita gałęzi razem z folderem zmiany.

**Contract**: status F-05 w danych skryptu (`:230`) równy statusowi w `context/foundation/roadmap.md`; `python3 tools/render_roadmap_svg.py` generuje `docs/visuals/roadmap.svg`. `context/map/repo-map.md` (opis „8 stałych trybu”) zostaje do następnego `/10x-repo-map`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe frontu przechodzą, w tym nowe przypadki strażnika: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- W tekście atrapy nie ma już stałych trybów: `! grep -nE "^\s*const (MODE|NOTIFICATIONS|AUTH|LOGIN|START|ACTIVATION|FRESHNESS|MISSING|QUALITY) = '" szczur-radar-web/dev/mock-api.js`
- Procedura zrzutów działa bez zmian i z trybem. Agent uruchamia `npx vite --port 5173 --strictPort` w tle, `playwright-cli -s=e2e-harness open`, `run-code --filename=szczur-radar-web/dev/mock-api.js`, `goto http://localhost:5173/admin`. `playwright-cli -s=e2e-harness eval "document.querySelector('h1')?.textContent"` daje `Panel admina`. Potem `run-code "async page => ($(cat szczur-radar-web/dev/mock-api.js))(page, { MODE: 'error' })"` i `goto http://localhost:5173/` dają alert „Nie udało się wczytać postów.” w snapshocie. Na końcu przeglądarka i serwer zamknięte.

**Implementation Note**: Po zielonej weryfikacji automatycznej przejdź do fazy 2. Ta faza nie ma kroków ręcznych.

---

## Faza 2: Infrastruktura Playwright i seed

### Overview

Playwright Test w `szczur-radar-web`, fixture z trybami, zegarem i sprzątaniem, typecheck i izolacja od Vitest oraz jeden test seed, który jest wzorcem dla reszty. Powstaje też zapis w `test-stack.md`.

### Changes Required:

#### 1. Zależność i skrypt

**File**: `szczur-radar-web/package.json`, `szczur-radar-web/package-lock.json`

**Intent**: Dodać runner E2E w przypiętej wersji, zgodnej z obrazem CI.

**Contract**: `npm i -D -E @playwright/test@1.65.1`; skrypt `"e2e": "playwright test"`. Lokalnie przeglądarka: `npx playwright install chromium` (bez zmian w plikach).

#### 2. Konfiguracja

**File**: `szczur-radar-web/playwright.config.ts` (nowy)

**Intent**: Testy na produkcyjnym buildzie, z portem nadpisywalnym, deterministyczne w CI.

**Contract**:
- `testDir: './e2e'`, `fullyParallel: true`, `forbidOnly: !!process.env.CI`, `retries: process.env.CI ? 1 : 0`.
- `failOnFlakyTests: !!process.env.CI`: ponowienie w CI służy tylko do zebrania trace'a. Test, który przeszedł dopiero w ponowieniu, daje czerwony job i raport.
- `reporter`: w CI `list` i `html` (`open: 'never'`), lokalnie `list`.
- `use`: `baseURL` z `PORT = Number(process.env.E2E_PORT ?? 4173)` (4173 to domyślny port `vite preview`), `trace: 'on-first-retry'`, `screenshot: 'only-on-failure'`, `locale: 'pl-PL'`, `timezoneId: 'Europe/Warsaw'`.
- Jeden projekt `chromium` (`devices['Desktop Chrome']`).
- `webServer`: `npm run build && npm run preview -- --port ${PORT} --strictPort`, `url: baseURL`, `reuseExistingServer: !process.env.CI`, `timeout: 180_000`.
- Bez projektu `setup` i `storageState`.

#### 3. Fixture

**File**: `szczur-radar-web/e2e/fixtures.ts` (nowy)

**Intent**: Każdy test dostaje świeżą atrapę w swoich trybach i stały zegar bez kodu w samym teście.

**Contract**:
- Eksportuje `test` i `expect`.
- `test = base.extend<{ modes: MockModes; mock: MockControl }>`:
  - `modes` to opcja (`[{}, { option: true }]`), ustawiana przez `test.use({ modes: { AUTH: 'anonymous' } })`;
  - `mock` to automatyczny fixture, który po kolei: woła `page.clock.setFixedTime(FIXED_NOW)`, wczytuje tekst `dev/mock-api.js` z dysku (`readFileSync` + `(0, eval)`, ścieżka przez `import.meta.url`), wywołuje go z `(page, { ...modes, now: FIXED_NOW })`, oddaje `control` testowi, a po teście woła `control.dispose()`.
- Typy z `src/contract/mockModes.ts`.

#### 4. Typecheck, izolacja od Vitest, pliki ignorowane

**File**: `szczur-radar-web/tsconfig.e2e.json` (nowy), `szczur-radar-web/tsconfig.json`, `szczur-radar-web/vitest.config.ts`, `szczur-radar-web/.gitignore`, `szczur-radar-web/.dockerignore`

**Intent**: Kod E2E sprawdzany przez `vue-tsc -b` (także w obrazie Docker), niewciągany przez Vitest, a wyniki biegów nie w git i nie w kontekście obrazu.

**Contract**:
- `tsconfig.e2e.json` na wzór `tsconfig.node.json`: `include` to `e2e/**/*.ts`, `playwright.config.ts` i `src/contract/mockModes.ts`; typy `node`.
- `tsconfig.json` ma referencję na `tsconfig.e2e.json`.
- `vitest.config.ts`: `test.exclude: [...configDefaults.exclude, 'e2e/**']`.
- `.gitignore`: `test-results/`, `playwright-report/`, `blob-report/`, `playwright/.cache/`.
- `.dockerignore`: `test-results`, `playwright-report`, `blob-report`.

#### 5. Seed

**File**: `szczur-radar-web/e2e/session-guard.spec.ts` (nowy)

**Intent**: Pierwszy test i wzorzec dla reszty. Ryzyko: przebudowa routera lub shellu (S-47) zgubi powrót pod żądaną stronę po zalogowaniu.

**Contract**:
- Komentarz o pochodzeniu z ryzykiem `session-guard-roundtrip` (F-05).
- `test.use({ modes: { AUTH: 'anonymous' } })`.
- Test „anonimowy link do posta prowadzi przez logowanie z powrotem do tego posta”:
  1. `goto('/posts/00000000-0000-4000-8000-000400000005')`;
  2. oczekiwany URL `/login?redirect=/posts/…05` (vue-router nie koduje `/` w wartości query), h1 „Logowanie”;
  3. wypełnienie „E-mail” (`admin@szczur-radar.pl`) i „Hasło” (`exact: true`), klik „Zaloguj się”;
  4. URL wraca do `/posts/…05`, h1 posta to tytuł posta 5 z atrapy, a w `banner` widać link „Panel admina”.
- Lokatory tylko po rolach i etykietach.

#### 6. Zapis stosu testów

**File**: `context/foundation/test-stack.md` (nowy)

**Intent**: `/10x-e2e` i `/10x-tdd` w kolejnych wycinkach czytają stąd runner i komendy zamiast skanować repo.

**Contract**: `# Test stack` i sekcja `## E2E` dokładnie według `.claude/skills/10x-e2e-setup/references/test-stack-e2e-schema.md`:
- runner `@playwright/test 1.65.1`;
- config `szczur-radar-web/playwright.config.ts`;
- komendy `cd szczur-radar-web && npx playwright test e2e/<name>.spec.ts` / `npm run e2e`;
- base URL `http://localhost:4173`;
- port 4173 (domyślny `vite preview`, `E2E_PORT` nadpisuje);
- web server command `npm run build && npm run preview -- --port $E2E_PORT --strictPort`, `reuseExistingServer` poza CI;
- `auth setup project: none — sesję ustawia tryb AUTH atrapy per test (e2e/fixtures.ts, test.use({ modes }))`;
- `storageState: none`;
- seed `szczur-radar-web/e2e/session-guard.spec.ts` — `session-guard-roundtrip`;
- browser CLI `playwright-cli`, command skill w `.claude/skills/playwright-cli`;
- `updated` = data zapisu.

### Success Criteria:

#### Automated Verification:

- Seed przechodzi na zimnym serwerze. Port 4173 jest wolny przed biegiem (`! lsof -nP -iTCP:4173 -sTCP:LISTEN`), potem `cd szczur-radar-web && npx playwright test e2e/session-guard.spec.ts`.
- Seed czerwienieje przy celowym zepsuciu: w `src/router/index.ts` odesłanie na login bez `query.redirect` daje czerwony seed. Zepsucie jest cofnięte, a `git diff --quiet szczur-radar-web/src` po cofnięciu.
- Vitest nie zbiera `e2e/`: `cd szczur-radar-web && npm test` przechodzi, a `npx vitest list 2>/dev/null | grep -c "e2e/"` daje `0`.
- `vue-tsc -b` sprawdza kod E2E: celowy błąd typu w `e2e/fixtures.ts` (np. `modes: { MODE: 'nope' }`) daje czerwony `npm run build`. Po cofnięciu `npm run build` przechodzi.
- `test-stack.md` ma każde wymagane pole schematu: `grep -cE "^- (runner|config|single-spec command|full-suite command|base URL|port|web server command|auth setup project|storageState|seed|updated):" context/foundation/test-stack.md` daje `11`.

**Implementation Note**: Po zielonej weryfikacji automatycznej przejdź do fazy 3. Ta faza nie ma kroków ręcznych.

---

## Faza 3: Smoke obecnych przepływów

### Overview

17 testów obok seeda (razem 18) na obecnych widokach, po jednym pliku na przepływ. Każdy test jest nazwany ryzykiem, ustawia tryby przez `test.use` i sprawdza wynik biznesowy. Każdy plik jest udowodniony celowym zepsuciem SPA.

### Changes Required:

#### 1. Sesja i rola

**File**: `szczur-radar-web/e2e/auth.spec.ts` (nowy)

**Intent**: Obecne zachowanie logowania, wylogowania, wygaśnięcia sesji i roli, które S-47 i S-49 przebudują.

**Contract** — testy:
- **„złe hasło zostaje na logowaniu z komunikatem”** (`AUTH: 'anonymous'`, `LOGIN: 'invalid'`): alert „Nieprawidłowy e-mail lub hasło.”, URL zostaje `/login`.
- **„wylogowanie kończy sesję, a chroniona strona wraca do logowania”**: klik „Wyloguj” w `banner` daje `/login`. Potem `goto('/')` daje `/login?redirect=/` (bez kodowania `/`).
- **„wygaśnięcie sesji w trakcie pracy prowadzi do logowania i z powrotem”**:
  1. feed, potem `mock.endSession()`;
  2. klik „Starsze” daje `/login?redirect=/?page=1`;
  3. logowanie formularzem wraca na `/?page=1` z „Strona 2 z 3”.
- **„zwykły użytkownik nie widzi panelu admina”** (`AUTH: 'user'`): brak linku „Panel admina” i przycisku dzwonka w `banner`, a `goto('/admin')` kończy się na `/` z h1 „Posty”.

#### 2. Aktywacja

**File**: `szczur-radar-web/e2e/activation.spec.ts` (nowy)

**Intent**: Jedyna droga do pierwszego hasła konta (FR-026–028), na ekranie, który przebuduje S-49.

**Contract**: test **„link aktywacyjny ustawia hasło i prowadzi do logowania”** (`AUTH: 'anonymous'`):
1. `goto('/activate#token')`, tekst „Ustaw hasło dla jan.kowalski@example.com.”;
2. wypełnienie „Nowe hasło” i „Powtórz hasło” 12+ znakami, klik „Ustaw hasło”;
3. URL `/login`, status „Hasło ustawione. Zaloguj się.”, pole „E-mail” wypełnione `jan.kowalski@example.com`.

#### 3. Feed

**File**: `szczur-radar-web/e2e/feed.spec.ts` (nowy)

**Intent**: Lista z filtrem, stronicowanie i stany listy, które S-48 i S-51 przebudują.

**Contract** — testy:
- **„filtr po celu i stanowisku trafia do adresu i zawęża listę”**:
  1. „Spółka lub temat” = `orlen`, „Stanowisko” = „wzrostowo”, klik „Filtruj”;
  2. URL ma `q=orlen&direction=bullish`;
  3. status „Pasujące do filtra: N”, gdzie N to liczba wyznaczona z danych atrapy (posty wariantu 5 z celem „Orlen” bullish). Implementujący liczy ją z `mock-api.js` i wpisuje jako stałą z komentarzem;
  4. każda karta na stronie to post „Orlen ogłasza buyback za 4 mld zł”.
- **„filtr bez wyników pozwala wrócić do pełnej listy”**: `goto('/?q=zzz')` daje „Żaden post nie pasuje do filtra.”. Klik „Wyczyść filtry” wyniku (`getByRole('button', { name: 'Wyczyść filtry' }).last()`, bo oba przyciski są w `main`) daje „Ocenione posty: 120” i URL `/`.
- **„przejście na starszą stronę zmienia adres i stronę”**: klik „Starsze” daje `?page=1` i „Strona 2 z 3”.
- **„ładowanie listy jest widoczne”** (`MODE: 'loading'`): „Wczytywanie…” w `main`.
- **„błąd listy pozwala ponowić wczytanie”** (`MODE: 'error'`): alert „Nie udało się wczytać postów.”. Klik „Spróbuj ponownie” wysyła nowe `GET /api/posts` (`page.waitForRequest`), a alert jest dalej widoczny.
- **„pusta lista mówi, kiedy pojawią się posty”** (`MODE: 'empty'`): „Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.”

#### 4. Post

**File**: `szczur-radar-web/e2e/post.spec.ts` (nowy)

**Intent**: Widok jednego wpisu z oceną i surowymi danymi, który zastąpi inspektor w S-51.

**Contract**: test **„post otwarty z listy pokazuje ocenę i surowe dane, a powrót wraca do listy”**:
1. klik linku pierwszej karty, URL `/posts/00000000-0000-4000-8000-000400000000`;
2. h1 „Wpis użytkownika uzytkownik0” (post 0 nie ma tytułu, więc widok składa h1 jako „{Typ} użytkownika {autor}”);
3. h2 „Ocena” i h3 celu „CDR”;
4. rozwinięcie „Dane dla nerdów: surowy JSON źródła” pokazuje JSON z `"slug"`;
5. klik „Posty” w `main` (link powrotu) daje `/` z h1 „Posty”.

#### 5. Panel admina

**File**: `szczur-radar-web/e2e/admin.spec.ts` (nowy)

**Intent**: Panel admina, strona przebiegu i dzwonek, które przebuduje S-50.

**Contract** — testy (domyślnie `AUTH: 'admin'`):
- **„panel admina wczytuje wszystkie sekcje”**:
  - h2 „Nowy przebieg”, „Dni bez danych”, „Jakość ocen (7 dni)”, „Ostatnie przebiegi”, „Nieudane posty”;
  - w każdej sekcji treść z danych zamiast „Wczytywanie…”: przyciski „Wpisz do formularza: …”, linki „Szczegóły przebiegu z …” (20) i „Pokaż starsze”, checkboxy nieudanych postów.
- **„uruchomienie przebiegu dodaje go na górę listy”**: daty „Od” i „Do” to domyślne `2026-10-19` (stały zegar). Klik „Uruchom” daje status zaczynający się od „Uruchomiono:”, a pierwszy wiersz listy ma odznakę „w kolejce”.
- **„zajęty pipeline odrzuca start z komunikatem”** (`START: 'busy'`): alert „Przebieg już trwa. Poczekaj, aż się zakończy, i spróbuj ponownie.”
- **„szczegóły przebiegu otwierają jego stronę”**: klik pierwszego „Szczegóły przebiegu z …” daje `/admin/runs/00000000-0000-4000-8000-000100000001`, h1 „Przebieg: zakres dat” i h2 „Postęp”.
- **„powiadomienie z dzwonka otwiera przebieg i zmniejsza licznik”**:
  1. przycisk „Powiadomienia, 5 nieprzeczytanych”, klik;
  2. klik pierwszej pozycji w regionie „Powiadomienia” daje `/admin/runs/00000000-0000-4000-8000-000100000003`;
  3. przycisk dzwonka „Powiadomienia, 4 nieprzeczytane”.

#### 6. Dowody celowego psucia

**File**: `context/changes/e2e-harness/e2e-breaks.md` (nowy)

**Intent**: Zapis, że każdy plik smoke łapie swoje ryzyko, na wzór `sql-breaks.md` z S-45.

**Contract**: tabela z kolumnami plik testu → zepsucie w `src/` → test, który zaczerwienił, i fragment błędu → cofnięte. Zepsucia, po jednym na plik:

| Plik | Zepsucie |
|---|---|
| `auth` | po 401 klient nie powiadamia routera (`client.ts`) |
| `activation` | po aktywacji brak przejścia na `/login` |
| `feed` | `filterToQuery` gubi `direction` |
| `post` | widok bez sekcji surowego JSON |
| `admin` | po starcie lista się nie odświeża |

### Success Criteria:

#### Automated Verification:

- Cały zestaw przechodzi na zimnym serwerze: wolny port 4173, potem `cd szczur-radar-web && npm run e2e` daje 18 testów zielonych.
- Zestaw jest stabilny przy powtórzeniach i równoległości: `npx playwright test --repeat-each=5 --workers=4` bez czerwonego i bez `flaky` w raporcie.
- Każdy plik smoke czerwienieje przy swoim zepsuciu: 5 zepsuć z `e2e-breaks.md` wykonanych i cofniętych, tabela wypełniona, `git diff --quiet szczur-radar-web/src` po cofnięciu.
- W testach nie ma lokatorów CSS, XPath ani czekania na czas: `! grep -nE "page\.locator\(|waitForTimeout|xpath=" szczur-radar-web/e2e/*.ts`
- Testy jednostkowe frontu dalej przechodzą: `npm test`.

#### Manual Verification:

- Właściciel akceptuje listę 18 testów (nazwy w `e2e/*.spec.ts`) jako siatkę regresji obecnych przepływów przed S-46–S-51.

**Implementation Note**: Pod `/10x-implement` po zielonej weryfikacji automatycznej zatrzymaj się na akceptację właściciela (wiersz Manual) przed fazą 4. Pod `/10x-goal-implement` wiersz 3.6 trafia na listę końcową, a akceptacja następuje przed merge'em. Zmiana listy testów po akceptacji zmienia liczbę „18” w kryterium 4.3 i w dokumentach fazy 4.

---

## Faza 4: CI i dokumentacja

### Overview

Job `web-e2e` w CI blokuje wdrożenie przy czerwonym E2E. Wytyczne repo mówią, jak pisać i uruchamiać testy E2E. PR ma zielone CI.

### Changes Required:

#### 1. Job CI

**File**: `.github/workflows/ci.yml`

**Intent**: E2E przy każdym PR i pushu na `main`, na obu pulach runnerów, jako bramka wdrożenia.

**Contract**:
- Job `web-e2e`:
  - nazwa „web E2E tests (Playwright)”;
  - `runs-on` to to samo wyrażenie co inne joby;
  - `timeout-minutes: 10`;
  - `container: mcr.microsoft.com/playwright:v1.65.1-noble`;
  - `defaults.run.working-directory: szczur-radar-web`.
- Kroki:
  - `actions/checkout@v4`;
  - kontrola, że `npx playwright --version` po `npm ci` równa się wersji w tagu obrazu (inna wersja to czerwony krok z komunikatem, którą stronę poprawić);
  - `npm run e2e`;
  - `actions/upload-artifact@v4` z `if: failure()`, `name: playwright-report`, ścieżki `szczur-radar-web/playwright-report/` i `szczur-radar-web/test-results/`, `retention-days: 7`, `if-no-files-found: ignore`;
  - ostatni krok z `if: always()`: `chown -R "$(stat -c '%u:%g' "$GITHUB_WORKSPACE")" "$GITHUB_WORKSPACE"`. Kontener działa jako root, a trwały runner self-hosted jako `gha`: bez tego kroku następny job na tym runnerze nie posprząta plików roota. Na runnerach GitHuba krok nic nie zmienia.
- `web-e2e` dopisany do `needs:` joba `railway-config` (`ci.yml:309-318`).
- Bez filtra ścieżek.

#### 2. Wytyczne i zakres weryfikacji

**File**: `REPOSITORY_GUIDELINES.md`, `context/foundation/verification-scope.md`

**Intent**: Następny agent wie, gdzie leżą testy E2E, jak je uruchomić i jakie reguły obowiązują. Lista ręcznych kontroli ma przestać mówić, że teksty i przepływy w przeglądarce są ręczne.

**Contract**:
- **„Build, Test, and Development Commands”** → Frontend: `npm run e2e` (build + preview + Playwright, port `E2E_PORT`, przeglądarka `npx playwright install chromium`).
- **„Coding Style & Testing”**, nowy punkt E2E:
  - testy w `szczur-radar-web/e2e/`, zawsze z `e2e/fixtures.ts`;
  - tryby atrapy przez `test.use({ modes })`, zegar stały `FIXED_NOW`: `Date` stoi, timery chodzą;
  - test logiki liczonej z różnicy `Date` (np. świeżość listy dzwonka) przesuwa czas kolejnym `page.clock.setFixedTime`;
  - lokatory po rolach, etykietach i tekstach, zawężone do `banner` / `main`; bez CSS, XPath i `waitForTimeout`; test nazwany ryzykiem;
  - nowy przepływ SPA dostaje test E2E (NFR `prd.md:328`);
  - nowy tryb atrapy to wpis w `MODE_VALUES` atrapy i w `MOCK_MODE_VALUES`, przypadek w strażniku i opis w nagłówku;
  - zapis stosu w `context/foundation/test-stack.md`.
- **„Deployment”** → lista jobów CI: `web-e2e` w kontenerze Playwrighta, wersja obrazu równa `@playwright/test` (podniesienie zmienia oba w tym samym PR).
- **`verification-scope.md`** → pozycja „Wygląd i teksty widoków w przeglądarce (do M-5, Playwright)”: przepływy i teksty pokrywają testy E2E (F-05), a wygląd zostaje do F-06.

### Success Criteria:

#### Automated Verification:

- Workflow jest poprawnym YAML-em, a `web-e2e` jest w `needs` joba `railway-config`: `python3 -c "import yaml,sys; d=yaml.safe_load(open('.github/workflows/ci.yml')); sys.exit(0 if 'web-e2e' in d['jobs']['railway-config']['needs'] else 1)"`
- PR gałęzi `feature/e2e-harness` ma w opisie `Refs #176`: `gh pr view --json body -q .body | grep -q "Refs #176"`
- Job `web-e2e` na PR jest zielony i przeszedł 18 testów: `gh pr checks` pokazuje `web E2E tests (Playwright)` jako `pass`, a `gh run view <run-id> --log | grep -E "18 passed"` coś znajduje.
- Każdy job CI na PR jest zielony, w tym `railway-config`: `gh pr checks` bez `fail`.
- Job czerwienieje na czerwonym teście i oddaje raport. Tymczasowy commit z odwróconą asercją seeda daje czerwony `web-e2e`, artefakt `playwright-report` (`gh run view <run-id> --json jobs` / `gh api repos/{owner}/{repo}/actions/runs/<run-id>/artifacts` ma `playwright-report`) i pominięty `railway-config`. Potem commit cofający daje zielony bieg.

**Implementation Note**: Po zielonym CI przekaż PR do merge'a (merge robi człowiek). Ta faza nie ma kroków ręcznych.

---

## Testing Strategy

### Unit Tests:

- Strażnik atrapy (`src/contract/mockApi.test.ts`):
  - walidacja trybów;
  - tabela trybów TS ↔ atrapa w obie strony;
  - `endSession`, `now`, `dispose`;
  - istniejące kształty, zachowania i zawieszenia bez zmian.
- `rules.test.ts` bez zmian: czyta te same literały w tekście atrapy.

### Integration Tests:

- 18 testów Playwright w `szczur-radar-web/e2e/`:
  - seed (`session-guard`);
  - `auth` (4);
  - `activation` (1);
  - `feed` (6);
  - `post` (1);
  - `admin` (5).
- Na produkcyjnym buildzie, Chromium, stały zegar, świeża atrapa na test.

### Manual Testing Steps:

1. Właściciel czyta nazwy testów w `szczur-radar-web/e2e/*.spec.ts` i decyduje, czy to wystarczająca siatka regresji przed przebudową (wiersz 3.6).

## Performance Considerations

- Job `web-e2e`: obraz Playwrighta (pobranie około 1–2 min przy zimnym runnerze), `npm ci`, build Vite i 18 testów równolegle. Szacunek: 3–5 min, limit 10 min.
- Joby CI chodzą równolegle, a `railway-config` czeka na wszystkie, więc wydłużenie bramki to najwyżej różnica między `web-e2e` a najdłuższym jobem (backend około 10 min).

## Migration Notes

- Brak migracji danych i zmian w Railway.
- Podniesienie wersji Playwrighta zmienia w tym samym PR `package.json` (dokładna wersja) i tag obrazu w `ci.yml`. Pilnuje tego krok kontroli wersji w jobie.

## References

- Audyt: `context/changes/post-m4-ui-readiness-audit/research.md` (sekcja „3. Podłoga E2E”, decyzje właściciela z 2026-11-15)
- Roadmapa: `context/foundation/roadmap.md` (F-05, `:159-172`)
- PRD: NFR `context/foundation/prd.md:328`
- S-43: `context/archive/2026-11-11-contract-bound-mocks/plan-brief.md`, `plan.md`, `reviews/impl-review.md`
- Atrapa i strażnik: `szczur-radar-web/dev/mock-api.js`, `szczur-radar-web/src/contract/mockApi.ts`, `szczur-radar-web/src/contract/mockApi.test.ts`, `szczur-radar-web/src/contract/rules.test.ts:95-101,336-341`
- Schemat `test-stack.md`: `.claude/skills/10x-e2e-setup/references/test-stack-e2e-schema.md`
- Reguły E2E: `.claude/skills/10x-e2e-setup/references/e2e-quality-rules.md`
- Wzorzec dowodu psucia: `context/archive/2026-11-13-core-mutation-testing/sql-breaks.md`
- Issue: #176

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Atrapa z trybami jako parametr

#### Automated

- [x] 1.1 Testy jednostkowe frontu przechodzą, w tym nowe przypadki strażnika — 51b8706
- [x] 1.2 Typecheck i build przechodzą — 51b8706
- [x] 1.3 W tekście atrapy nie ma już stałych trybów — 51b8706
- [x] 1.4 Procedura zrzutów działa bez zmian i z trybem — 51b8706

### Phase 2: Infrastruktura Playwright i seed

#### Automated

- [x] 2.1 Seed przechodzi na zimnym serwerze — 56deab6
- [x] 2.2 Seed czerwienieje przy celowym zepsuciu — 56deab6
- [x] 2.3 Vitest nie zbiera e2e/ — 56deab6
- [x] 2.4 vue-tsc -b sprawdza kod E2E — 56deab6
- [x] 2.5 test-stack.md ma każde wymagane pole schematu — 56deab6

### Phase 3: Smoke obecnych przepływów

#### Automated

- [x] 3.1 Cały zestaw przechodzi na zimnym serwerze — 93df399
- [x] 3.2 Zestaw jest stabilny przy powtórzeniach i równoległości — 93df399
- [x] 3.3 Każdy plik smoke czerwienieje przy swoim zepsuciu — 93df399
- [x] 3.4 W testach nie ma lokatorów CSS, XPath ani czekania na czas — 93df399
- [x] 3.5 Testy jednostkowe frontu dalej przechodzą — 93df399

#### Manual

- [ ] 3.6 Właściciel akceptuje listę 18 testów jako siatkę regresji

### Phase 4: CI i dokumentacja

#### Automated

- [x] 4.1 Workflow jest poprawnym YAML-em, a web-e2e jest w needs joba railway-config
- [x] 4.2 PR gałęzi feature/e2e-harness ma w opisie Refs #176
- [ ] 4.3 Job web-e2e na PR jest zielony i przeszedł 18 testów
- [ ] 4.4 Każdy job CI na PR jest zielony, w tym railway-config
- [ ] 4.5 Job czerwienieje na czerwonym teście i oddaje raport
