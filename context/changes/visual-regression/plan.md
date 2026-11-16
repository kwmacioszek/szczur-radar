# F-06: zrzuty porównawcze i test dostępności w CI — plan implementacji

## Overview

Do `szczur-radar-web` wchodzi druga warstwa testów w przeglądarce: zrzuty porównawcze (`toHaveScreenshot`) i automatyczny test dostępności (axe-core) na atrapie API z F-05. Pilnowane są:
- każdy widok z danymi w obu motywach, przy 375 px i na desktopie (24 wzorce);
- każda sekcja katalogu komponentów `/kitchen-sink` w obu motywach przy 375 px (20 wzorców);
- mały zestaw stanów hover i focus wspólnych kontrolek (10 wzorców).

Zrzuty renderuje tylko przypięty obraz Playwright na linux/amd64, z aliasem fontu ustawionym wyłącznie dla testów. Wzorce aktualizuje lokalny skrypt dockerowy, a czerwony krok CI wgrywa nowe wzorce jako artefakt (zapas). Zmiana wyglądu aktualizuje wzorce w tym samym PR, więc weryfikacja wyglądu w S-46–S-59 to przegląd różnic obrazków, a nie ręczne zrzuty (NFR `prd.md:328-330`, roadmapa F-06).

## Current State Analysis

**Infrastruktura E2E z F-05:**
- Playwright Test 1.65.1 (`szczur-radar-web/package.json:20`), jeden projekt Chromium z `devices['Desktop Chrome']` (1280×720).
- Testy chodzą na buildzie produkcyjnym (`npm run build && npm run preview`, `playwright.config.ts:25-30`), z `locale: 'pl-PL'`, `timezoneId: 'Europe/Warsaw'` (`:21-22`) i w CI z `retries: 1` + `failOnFlakyTests` (`:13-14`).
- Fixture `e2e/fixtures.ts:13-27` instaluje świeżą atrapę `dev/mock-api.js` w trybach testu i zatrzymuje zegar na `FIXED_NOW = 2026-10-20T10:00:00Z` (`src/contract/mockModes.ts:32`).
- Job `web-e2e` (`.github/workflows/ci.yml:127-177`):
  - kontener `mcr.microsoft.com/playwright:v1.65.1-noble`;
  - kontrola, że wersja `@playwright/test` równa się tagowi obrazu (`:146-156`; czyta jedyną linię `container:` w pliku);
  - artefakt `playwright-report` przy błędzie, `chown` na końcu;
  - jest w `needs:` joba `railway-config` (`:361-371`).
- Zmierzone czasy: `web-e2e` trwa 41–51 s (inicjalizacja kontenera ~30 s, testy z buildem 9 s). Najdłuższy job to `mutation` (~6 min 22 s), więc F-06 ma ~5,5 min zapasu, zanim wydłuży bramkę wdrożenia.

**Katalog komponentów:**
- `/kitchen-sink` istnieje tylko pod `import.meta.env.DEV` (`src/router/index.ts:42-53`). `vite build` ustawia `NODE_ENV=production`, więc katalogu nie ma w `dist/` ani w `vite preview`.
- Ma 10 sekcji `<section aria-labelledby>` z nagłówkiem `h2`, czyli role `region` z nazwą: Tokeny, Przyciski, Komunikaty, Znaczniki, Dzwoneczek, Pola, Filtr, Posty, Stanowisko, Ocena posta (`src/views/KitchenSinkView.vue:590-703`).
- Regionami są też 4 otwarte panele dzwonka w sekcji Dzwoneczek (`role="region"` z nazwą „Powiadomienia”, `src/components/NotificationMenu.vue:133-139`). `getByRole('main').getByRole('region')` zwraca więc 14 regionów, nie 10. Sekcje wylicza się po nagłówkach `h2`.
- Nie woła API, ale strażnik routera woła `/api/auth/me` przy każdej nawigacji (`src/router/index.ts:60-61`), a pod `vite` bez atrapy to żądanie idzie przez proxy na `:8000` (`vite.config.ts:14`).
- Celowo pokazuje stany `aria-busy` (przycisk „W toku”, dzwonek „ładowanie”), więc reguła „czekaj, aż nic nie jest zajęte” tu nie działa.
- Klik w dowolne miejsce albo Escape zamyka wszystkie 4 otwarte panele dzwonka (`src/components/NotificationMenu.vue:82-101`).
- Hover i focus-visible to wyłącznie pseudo-klasy:
  - hover przycisków: `style.css:136-138`, `AppButton.vue:37-39`;
  - pierścień `:focus-visible` z `--ring`: `style.css:146-149`;
  - nic ich nie wymusza statycznie.

**Motyw i font:**
- Motyw ciemny to tylko `@media (prefers-color-scheme: dark)` (`style.css:58-69`), więc `colorScheme` Playwrighta przełącza go w całości.
- Font to `--sans: system-ui, 'Segoe UI', Roboto, sans-serif` (`style.css:39`), bez `@font-face`.
- W obrazie Playwright `fc-match system-ui` daje WenQuanYi Zen Hei. Na zrzutach nagłówki są wtedy bez pogrubienia, a ą, ę, ź pochodzą z innego fontu.
- Obraz ma Liberation Sans i Liberation Mono. Plik fontconfig z `<include>` systemowej konfiguracji i aliasem `system-ui`/`sans-serif` → Liberation Sans przestawia `fc-match` na Liberation Sans Regular i Bold (sprawdzone 2026-11-16).
- Obraz ustawia `PLAYWRIGHT_BROWSERS_PATH=/ms-playwright` i działa jako root.

**Próbny pomiar** (2026-11-16, scratchpad, bez zmian w repo; `@axe-core/playwright` 4.14.0, axe-core 4.14.1):
- axe z tagami WCAG 2.0/2.1/2.2 A/AA: 0 naruszeń na 9 stronach (login, aktywacja, feed jako user i admin, post, `/admin`, przebieg zakończony i trwający, katalog) × 2 motywy × 2 szerokości. Test kontrolny (obrazek bez `alt`, tekst `#ccc` na `#fff`) dał `image-alt` i `color-contrast`.
- Reguła best-practice `landmark-unique` zgłasza kilka paneli dzwonka na katalogu; reguły best-practice nie wchodzą do zestawu.
- Dwa kolejne zrzuty całej strony są identyczne bajt w bajt na wszystkich 36 kombinacjach. Tak samo między osobnymi przebiegami: 3 na macOS (1 i 4 workery) i 2 w kontenerze.
- Polling `/admin`, strony przebiegu i dzwonka niczego nie zmienia przy zatrzymanym zegarze i stałych danych atrapy. Animacji, `Math.random` i obrazków nie ma.
- Wysokości stron:
  - feed: 10–15 tys. px, 1,2–1,45 MB na PNG;
  - `/admin`: 4,4–6,9 tys. px;
  - katalog: 8,8–11 tys. px.
  
  Komplet całostronicowy na jedną platformę to ~19 MB.
- 36 kombinacji (zrzut i axe) trwa ~25 s na 4 workerach.

**Platforma:** Mac właściciela to arm64 (OrbStack), runnery CI to amd64. Wzorce muszą powstawać w tym samym obrazie na amd64.

**Pułapki konfiguracji:**
- Vitest wyklucza tylko `e2e/**` (`vitest.config.ts:12`), więc domyślny glob wciągnąłby `visual/*.spec.ts`.
- `tsconfig.e2e.json` obejmuje `e2e/**/*.ts`, `playwright.config.ts` i `src/contract/mockModes.ts`. `vue-tsc -b` w `npm run build` (także w obrazie Dockera) typecheckuje tylko to.
- `.dockerignore` nie wyklucza katalogów z PNG ani nowych katalogów raportów.
- Reguła repo: w testach przeglądarki nie ma `page.locator(`, XPath ani `waitForTimeout` (`REPOSITORY_GUIDELINES.md`, sekcja Coding Style; kryterium F-05).

## Desired End State

- `npm run visual:docker` (z `szczur-radar-web/`, na hoście z Dockerem) uruchamia w obrazie `mcr.microsoft.com/playwright:v<wersja @playwright/test>-noble` na linux/amd64 wszystkie testy wyglądu i dostępności i przechodzi. Sprawdza:
  - 24 zrzuty widoków;
  - 20 zrzutów sekcji katalogu;
  - 10 zrzutów stanów;
  - axe na każdym widoku i na katalogu;
  - strażnika fontu;
  - strażnika wzorców.
- `npm run visual:update` zapisuje brakujące i zmienione wzorce. `node_modules` hosta zostaje nietknięte.
- `npx playwright test -c playwright.visual.config.ts` poza tym obrazem odmawia z komunikatem, który wskazuje `npm run visual:docker`. `npm run e2e` na hoście działa jak dziś.
- W CI job `web-e2e` po kroku E2E uruchamia krok zrzutów (także gdy E2E są czerwone). Zmiana wyglądu bez aktualizacji wzorców albo naruszenie axe daje czerwony job i pominięty `railway-config`. Czerwony krok zrzutów zostawia artefakt `visual-baselines` z nowymi wzorcami, gotowymi do rozpakowania.
- Wzorce leżą w `szczur-radar-web/visual/__screenshots__/<projekt>/`, gdzie projekt to `light-375`, `dark-375`, `light-1280` albo `dark-1280`. Każdy plik należy do bieżącego celu i odwrotnie.
- `REPOSITORY_GUIDELINES.md` mówi, jak dodać cel, zaktualizować wzorce i czytać różnice. `verification-scope.md` nie liczy już wyglądu jako ręcznej kontroli, a jedynie akceptację nowego wyglądu.

### Key Discoveries:

- Regiony katalogu mają nazwy z nagłówków `h2` (`KitchenSinkView.vue:590-591` i kolejne sekcje), więc nagłówki `getByRole('main').getByRole('heading', { level: 2 })` i region po nazwie (`getByRole('region', { name, exact: true })`) wyliczają sekcje bez CSS i bez paneli dzwonka. Nowa sekcja (np. wspólne stany w S-48) sama dostaje cel zrzutu.
- `PostCard` renderuje `<article>` (`src/components/PostCard.vue:21`). Post n atrapy to wariant n % 6 (`e2e/feed.spec.ts:5-8`), więc 6 pierwszych artykułów pokrywa wszystkie warianty, a początek 7. wyznacza miejsce przycięcia zrzutu feedu.
- Przebieg 3 atrapy jest zakończony (`partial`, nieudane źródło Reddit z błędem, wynik per źródło), więc nie odpytuje API i pokazuje najwięcej elementów strony przebiegu (`dev/mock-api.js:64-66`).
- Wzorzec czekania na wczytanie `/admin` jest w `e2e/admin.spec.ts:9-26`: nagłówki sekcji, liczby wierszy i brak „Wczytywanie…”.
- Playwright 1.65: tryb `--update-snapshots=changed` zapisuje brakujące i niezgodne wzorce, a `none` niczego nie zapisuje. Flaga z linii poleceń wygrywa z `updateSnapshots` z konfiguracji.
- `launchOptions.env` przekazuje zmienne środowiskowe procesowi przeglądarki, więc `FONTCONFIG_FILE` żyje w konfiguracji testów, a nie w YAML-u CI i skrypcie osobno.
- CDP `CSS.getPlatformFontsForNode` (sesja CDP Chromium) zwraca fonty, którymi faktycznie narysowano glify węzła. Na tym opiera się strażnik fontu.

## What We're NOT Doing

- Fontu webowego ani zmiany typografii w aplikacji. Typografia należy do S-46, a alias fontu działa tylko w testach.
- Zrzutów innych stanów widoków niż jeden stan z danymi (wersja odchudzona, roadmapa `:186`). Ładowanie, błąd i pusto sprawdzają funkcjonalnie testy F-05.
- Zrzutów katalogu na desktopie. PRD `:330` mówi o katalogu przy 375 px.
- Hover i focus poza 5 stanami: hover i focus przycisku primary i secondary oraz focus pola wyszukiwania. Pole nie ma stylu hover, więc zrzut hover dawałby kopię domyślnego.
- Klas wymuszających stan w komponentach ani zmian w `src/`.
- Innych przeglądarek niż Chromium.
- Reguł axe best-practice i rejestru wyjątków axe. Dziś nie ma naruszeń, a wyjątek byłby zmianą kodu testu z komentarzem w PR.
- Bota commitującego wzorce, Git LFS, porównań w zewnętrznej usłudze.
- Zmian w `context/foundation/test-stack.md`. Sekcję pisze `/10x-e2e-setup`, a inne skille ją tylko czytają.
- Weryfikacji w PR env, bo nic wdrażanego się nie zmienia (obraz web dostaje tylko nowe wpisy `.dockerignore`), ani na puli self-hosted, gdzie kontenera nie sprawdzono już przy F-05.
- Poprawek nieaktualnych wierszy roadmapy (Backlog Handoff, Parked). To zadanie `/10x-roadmap`.

## Implementation Approach

Osobna konfiguracja `playwright.visual.config.ts` z testami w `szczur-radar-web/visual/` korzysta z tej samej fixture `e2e/fixtures.ts` (atrapa i zegar). Ma 4 projekty: motyw × szerokość. Uruchamia dwa serwery:
- build produkcyjny (`vite preview`) dla widoków;
- serwer dev (`vite`) dla katalogu.

Konfiguracja działa tylko w obrazie Playwright na linux/amd64, przekazuje przeglądarce `FONTCONFIG_FILE` z aliasem fontu, a w CI nie zapisuje wzorców. Cele to dane: tabela widoków i stanów w `visual/targets.ts` oraz regiony katalogu wyliczane z przeglądarki. Testy zrzutów i strażnik wzorców czytają te same dane.

Lokalnie wszystko chodzi przez `scripts/visual-docker.sh`, który uruchamia przypięty obraz z `--platform linux/amd64` i trzyma `node_modules` kontenera w wolumenie. W CI to nowy krok tego samego joba `web-e2e`: ten sam kontener, ta sama kontrola wersji, raport w osobnym katalogu.

Fazy idą tak, żeby ryzyko platformy (wzorce z emulowanego amd64 na Macu a amd64 w CI) wyszło przy pierwszym pushu fazy 1, zanim powstaną wzorce katalogu.

## Critical Implementation Details

- **`node_modules` w kontenerze.** Montowany katalog `szczur-radar-web/` ma `node_modules` hosta z binariami darwin (esbuild, rollup). `npm ci` w kontenerze bez osobnego wolumenu na `/work/node_modules` zastąpi je linuksowymi i zepsuje `npm test` / `npm run dev` na hoście. Wolumen musi przykrywać ten katalog. Nazwa wolumenu pochodzi ze ścieżki worktree, żeby dwa worktree nie dzieliły jednej instalacji.
- **Dwa raporty w jednym jobie.** Oba przebiegi piszą domyślnie do `playwright-report/` i `test-results/`, więc drugi nadpisałby pierwszy. Konfiguracja zrzutów pisze do `playwright-report-visual/` i `test-results-visual/`, a krok produkujący wzorce w CI używa tylko reportera `list`.
- **Katalog i atrapa.** Test katalogu instaluje atrapę (fixture robi to automatycznie; tryb `AUTH: 'anonymous'`), bo bez niej `/api/auth/me` idzie przez proxy dev na `:8000`. Nie klika i nie wciska Escape przed zrzutem sekcji Dzwoneczek, bo to zamyka otwarte panele.

## Faza 1: Harness zrzutów i widoki

### Overview

Konfiguracja, font, strażnik środowiska i skrypt dockerowy. 24 zrzuty widoków z axe i krok w CI. Pierwszy push dowodzi, że wzorce zrobione w emulowanym amd64 na Macu przechodzą na runnerze CI.

### Changes Required:

#### 1. Gałąź, PR i issue

**File**: — (git, GitHub)

**Intent**: Praca na jednej gałęzi i w jednym PR według workflow repo. Issue F-06 wie, gdzie trwa praca.

**Contract**:
- Gałąź `feature/visual-regression` od świeżo pobranego `origin/main` (`git fetch` przed sprawdzeniem bazy).
- Folder zmiany (`change.md`, `plan.md`, `plan-brief.md`) wchodzi w pierwszym commicie.
- PR z `Refs #177`.
- Komentarz w issue #177 z id zmiany, gałęzią i linkiem do PR.

#### 2. Roadmapa i jej SVG

**File**: `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Status F-06 przechodzi na `in-progress`, a obrazek roadmapy pokazuje to samo (reguła repo: zmiana roadmapy aktualizuje dane SVG).

**Contract**: wiersz F-06 w `## At a glance` i `- **Status:**` sekcji F-06 to `in-progress` (dziś niezacommitowane `planning`); dane F-06 w `tools/render_roadmap_svg.py` mają status `"progress"`, bo słownik `STATUS` skryptu (`:29-36`) nie zna `in-progress` (dziś `"proposed"`); `python3 tools/render_roadmap_svg.py` odświeża `docs/visuals/roadmap.svg`.

#### 3. Zależność, skrypty npm, pliki ignorowane

**File**: `szczur-radar-web/package.json`, `szczur-radar-web/package-lock.json`, `szczur-radar-web/.gitignore`, `szczur-radar-web/.dockerignore`, `szczur-radar-web/vitest.config.ts`, `szczur-radar-web/tsconfig.e2e.json`

**Intent**: axe dla Playwrighta jako zależność deweloperska o przypiętej wersji i polecenia testów wyglądu. Typecheck kodu testów wyglądu, a Vitest i obraz web bez nich.

**Contract**:
- `devDependencies`: `@axe-core/playwright` w dokładnej wersji `4.14.0`, bez `^`, tak jak `@playwright/test`.
- Skrypty:
  - `visual`: `playwright test -c playwright.visual.config.ts`, uruchamiany tylko w obrazie;
  - `visual:docker`: `bash scripts/visual-docker.sh`, z argumentami przekazywanymi dalej;
  - `visual:update`: `bash scripts/visual-docker.sh --update-snapshots=changed`.
- `.gitignore`: `playwright-report-visual/` i `test-results-visual/`.
- `.dockerignore`: `visual/__screenshots__`, `playwright-report-visual`, `test-results-visual`. Pliki `.ts` z `visual/` zostają w kontekście, bo `vue-tsc -b` w obrazie je typecheckuje.
- Vitest: `exclude` dostaje `visual/**` obok `e2e/**`.
- `tsconfig.e2e.json`: `include` dostaje `visual/**/*.ts` i `playwright.visual.config.ts`.

#### 4. Konfiguracja zrzutów

**File**: `szczur-radar-web/playwright.visual.config.ts`

**Intent**: Jedno miejsce, które gwarantuje identyczne renderowanie, czyli ten sam obraz, architekturę, font, locale, strefę i zegar. Lokalnie i w CI.

**Contract**:
- **Strażnik środowiska.** Konfiguracja rzuca błąd przed uruchomieniem czegokolwiek, gdy nie działa na `linux` + `x64` z `PLAYWRIGHT_BROWSERS_PATH=/ms-playwright`. Komunikat po polsku wskazuje `npm run visual:docker` i `npm run visual:update`.
- **Ustawienia przeglądarki:**
  - `testDir: './visual'`;
  - `use`: to samo `locale` (`pl-PL`) i `timezoneId` (`Europe/Warsaw`) co `playwright.config.ts`;
  - `launchOptions.env`: zmienne procesu plus `FONTCONFIG_FILE` z bezwzględną ścieżką do `visual/fonts.conf`.
- **Projekty** (nazwy też w `visual/targets.ts`):
  - `light-375` i `dark-375`: `viewport` 375×812;
  - `light-1280` i `dark-1280`: `viewport` 1280×720;
  - `colorScheme` według nazwy.
- **Wzorce:**
  - `expect.toHaveScreenshot.pathTemplate`: `{testDir}/__screenshots__/{projectName}/{arg}{ext}`, bez sufiksu platformy;
  - `animations: 'disabled'`, `caret: 'hide'`;
  - domyślny `threshold` 0.2 i zero dopuszczalnych pikseli różnicy, bo pomiar dał zrzuty identyczne bajt w bajt.
- **Uruchamianie:**
  - `updateSnapshots: 'none'` przy `CI`, inaczej `'missing'`;
  - `retries` i `failOnFlakyTests` jak w `playwright.config.ts`;
  - `outputDir: 'test-results-visual'`;
  - reporter przy `CI`: `[['list'], ['html', { outputFolder: 'playwright-report-visual', open: 'never' }]]`, jak `playwright.config.ts:15`, bo kryteria 1.10 i 2.6 czytają podsumowanie z logu, a sam reporter HTML go nie drukuje; inaczej `list`.
- **`webServer`** (tablica):
  - build produkcyjny `npm run build && npm run preview -- --port <E2E_PORT, domyślnie 4173> --strictPort`;
  - serwer dev `npx vite --port <E2E_DEV_PORT, domyślnie 5173> --strictPort`;
  - oba z `reuseExistingServer: !CI`.

  Adres serwera dev eksportuje `visual/targets.ts` dla testu katalogu (faza 2).

#### 5. Font tylko dla testów

**File**: `szczur-radar-web/visual/fonts.conf`

**Intent**: Zrzuty z pogrubieniem i polskimi literami z jednego fontu, bez zmiany wyglądu aplikacji.

**Contract**:
- Plik fontconfig z `<include>` `/etc/fonts/fonts.conf`.
- Silne przypisania (`binding="strong"`):
  - `system-ui` → Liberation Sans;
  - `sans-serif` → Liberation Sans;
  - `ui-monospace` i `monospace` → Liberation Mono.
- Komentarz w pliku mówi, że to alias testów zrzutów, a krój użytkownika zależy od jego systemu.

#### 6. Cele i pomocnicy

**File**: `szczur-radar-web/visual/targets.ts`, `szczur-radar-web/visual/axe.ts`

**Intent**: Cele zrzutów jako dane czytane przez testy i strażnika wzorców (faza 2). Jedna funkcja asercji axe z czytelnym komunikatem.

**Contract**:
- `PROJECTS`: 4 nazwy z motywem i szerokością.
- `NARROW_PROJECTS`: projekty 375.
- `VIEWS`: tabela 6 widoków. Każdy ma nazwę pliku, adres, tryby atrapy i warunek gotowości po rolach i tekstach:

  | Nazwa pliku | Adres | Tryby atrapy | Gotowy, gdy |
  |---|---|---|---|
  | `view-login` | `/login` | `AUTH: 'anonymous'` | formularz logowania widoczny |
  | `view-activate` | `/activate#<token>` | `AUTH: 'anonymous'`, `ACTIVATION: 'valid'` | formularz z e-mailem konta widoczny |
  | `view-feed` | `/` | `AUTH: 'user'` | 50 artykułów i linia świeżości widoczne |
  | `view-post` | `/posts/<post 0>` | `AUTH: 'user'` | ocena widoczna |
  | `view-admin` | `/admin` | `AUTH: 'admin'` | wszystkie sekcje wczytane, jak `e2e/admin.spec.ts:9-26` |
  | `view-run` | `/admin/runs/<przebieg 3>` | `AUTH: 'admin'` | sekcja wyniku per źródło widoczna |

  Ustawienie opcjonalne: przycięcie zrzutu feedu do początku 7. artykułu.
- `STATES` (dla fazy 2) i adres serwera dev.
- `expectNoAxeViolations(page)`:
  - `AxeBuilder` z tagami `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa`;
  - asercja pustej listy `violations`;
  - przy błędzie komunikat z id reguły, wpływem i selektorami węzłów;
  - wynik „needs review” (`incomplete`) nie jest błędem.

#### 7. Zrzuty i axe widoków

**File**: `szczur-radar-web/visual/views.spec.ts`

**Intent**: Każdy widok z danymi wygląda jak jego wzorzec i nie ma naruszeń dostępności, w każdym z 4 projektów.

**Contract**:
- Jeden test na widok w każdym projekcie, nazwany ryzykiem, np. „widok feed nie zmienia wyglądu i nie łamie dostępności”.
- Kroki testu:
  1. tryby z tabeli (`test.use` per `describe` albo per test);
  2. `goto` i warunek gotowości;
  3. `expect.soft(page).toHaveScreenshot('<nazwa>.png', { fullPage: true })`;
  4. `expectNoAxeViolations`. Przy czerwonym zrzucie raport ma też wynik axe.
- Feed: `clip` od góry strony do początku 7. artykułu (`getByRole('main').getByRole('article').nth(6)`, `boundingBox` przy przewinięciu 0), na pełnej szerokości.
- Spec importuje `test` i `expect` z `../e2e/fixtures.ts`.
- Bez `page.locator(`, XPath i `waitForTimeout`.

#### 8. Strażnik fontu

**File**: `szczur-radar-web/visual/font.spec.ts`

**Intent**: Gdy przyszły obraz albo zmiana konfiguracji zgubi alias, test jest czerwony, zamiast cicho produkować wzorce w obcym foncie.

**Contract**:
- Test w jednym projekcie (`light-375`), na `/login` z `AUTH: 'anonymous'`.
- Przez sesję CDP (`CSS.getPlatformFontsForNode`) dla nagłówka strony i dla węzła z polskimi literami (np. etykieta „Hasło”) jedynym użytym fontem jest `Liberation Sans`.

#### 9. Skrypt dockerowy

**File**: `szczur-radar-web/scripts/visual-docker.sh`

**Intent**: Jedno polecenie hosta uruchamia testy wyglądu w tym samym obrazie i na tej samej architekturze co CI.

**Contract**:
- `set -euo pipefail`.
- Wersja to dokładne `devDependencies['@playwright/test']` z `package.json` (wersja z `^` albo `~` to błąd z komunikatem).
- Obraz `mcr.microsoft.com/playwright:v<wersja>-noble`, opcje `--platform linux/amd64 --rm --init --ipc=host`.
- Montowanie:
  - katalog modułu web jako `/work`;
  - nazwany wolumen pochodzący ze ścieżki modułu na `/work/node_modules`.
- W kontenerze `npm ci --no-audit --no-fund`, potem `npm run visual -- "$@"`.
- Na końcu, także po czerwonym teście, `chown` na właściciela katalogu modułu dla tego, co kontener zapisał w montowaniu. Kod wyjścia testów przechodzi dalej.
- Bez działającego Dockera skrypt kończy się czytelnym komunikatem.

#### 10. Krok w CI

**File**: `.github/workflows/ci.yml`

**Intent**: Wygląd i dostępność blokują wdrożenie przy każdym PR i pushu na `main`, bez nowego joba i bez drugiej kontroli wersji obrazu.

**Contract**:
- W jobie `web-e2e`, który zachowuje nazwę, kontener, `timeout-minutes: 10` i kontrolę wersji:
  - krok instalacji dostaje `id`, a krok E2E zostaje bez zmian;
  - nowy krok „Run visual tests” (`id: visual`) z `npm run visual`, uruchamiany, gdy instalacja się udała, a bieg nie jest anulowany (także po czerwonych E2E);
  - krok „Produce baselines for review” z `if: ${{ !cancelled() && steps.visual.outcome == 'failure' }}`: `npm run visual -- --update-snapshots=changed --reporter=list`, z `continue-on-error: true`, więc jego kod wyjścia nie zmienia wyniku joba. Warunek musi mieć jawną funkcję statusu: samo `steps.visual.outcome == 'failure'` dostaje domyślne `success()`, a po czerwonym kroku zrzutów job jest już `failure`, więc krok byłby zawsze pominięty;
  - `actions/upload-artifact@v4` z nazwą `visual-baselines` i ścieżką `szczur-radar-web/visual/__screenshots__/`, w tym samym warunku (`!cancelled() && steps.visual.outcome == 'failure'`), `retention-days: 7`;
  - istniejący krok raportu dostaje ścieżki `szczur-radar-web/playwright-report-visual/` i `szczur-radar-web/test-results-visual/`.
- Komentarz nad krokiem zrzutów mówi, że wzorce renderuje ten obraz na amd64 i że artefakt `visual-baselines` rozpakowany w `szczur-radar-web/visual/__screenshots__/` to nowe wzorce do przeglądu.
- `needs:` joba `railway-config` bez zmian, bo `web-e2e` już tam jest.

#### 11. Pierwsze wzorce

**File**: `szczur-radar-web/visual/__screenshots__/{light,dark}-{375,1280}/view-*.png`

**Intent**: 24 wzorce widoków jako stan odniesienia przed S-46.

**Contract**: wygenerowane przez `npm run visual:update` na hoście (emulowany amd64); commitowane razem z kodem fazy.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe frontu przechodzą, a Vitest nie zbiera `visual/`: `npm test` zielone i `npx vitest list | grep -c "visual/"` daje `0`.
- `vue-tsc -b` sprawdza kod testów wyglądu: `npm run build` zielone, a chwilowy błąd typu w `visual/views.spec.ts` (np. przypisanie liczby do zmiennej typu `string`) daje czerwony build. Po cofnięciu build znowu zielony.
- Zestaw wyglądu przechodzi dwa razy z rzędu w kontenerze: `npm run visual:update`, potem dwa razy `npm run visual:docker`. Oba zielone, 25 testów (24 widoki + strażnik fontu), bez `flaky`.
- Wzorców widoków jest 24: `ls szczur-radar-web/visual/__screenshots__/*/view-*.png | wc -l` daje `24`.
- Poza obrazem konfiguracja odmawia: na hoście macOS `npx playwright test -c playwright.visual.config.ts` kończy się kodem różnym od 0, a wyjście zawiera `visual:docker`.
- Kontener nie podmienia `node_modules` hosta: po `npm run visual:docker` na hoście `npm test` i `npm run e2e` są zielone (18 testów).
- W testach wyglądu nie ma lokatorów CSS, XPath ani czekania na czas: `! grep -nE "page\.locator\(|waitForTimeout|xpath=" szczur-radar-web/visual/*.ts`
- Obraz web nie dostaje wzorców ani raportów wyglądu: `grep -qx "visual/__screenshots__" szczur-radar-web/.dockerignore`, to samo dla `playwright-report-visual` i `test-results-visual`.
- PR ma w opisie `Refs #177`: `gh pr view --json body -q .body | grep -q "Refs #177"`.
- Wzorce z Maca przechodzą w CI: na PR job `web E2E tests (Playwright)` jest `pass`, `gh run view <run-id> --log | grep -E "25 passed"` znajduje wynik kroku zrzutów, a `gh pr checks` nie ma `fail`.

**Implementation Note**: Jeśli wzorce z emulowanego amd64 nie przechodzą w CI, kryterium wyżej jest czerwone. Wtedy obowiązuje zapas z decyzji planu: wzorce pochodzą z artefaktu `visual-baselines` czerwonego biegu (`gh run download <run-id> -n visual-baselines -D szczur-radar-web/visual/__screenshots__`). Różnicę platformy trzeba wtedy zapisać w `REPOSITORY_GUIDELINES.md` (faza 3) jako jedyną drogę aktualizacji, a `visual:update` zostaje do szybkiej pętli lokalnej. Ta faza nie ma kroków ręcznych.

---

## Faza 2: Katalog komponentów

### Overview

Każda sekcja katalogu w obu motywach przy 375 px, 5 stanów hover/focus, axe katalogu i strażnik, że wzorce i cele zgadzają się w obie strony.

### Changes Required:

#### 1. Sekcje katalogu

**File**: `szczur-radar-web/visual/kitchen-sink.spec.ts`

**Intent**: Zmiana wspólnego komponentu zmienia wzorzec tylko jego sekcji, więc różnica w PR wskazuje komponent. Nowa sekcja dostaje wzorzec sama.

**Contract**:
- Test w projektach `light-375` i `dark-375` (inne projekty go pomijają), na serwerze dev (adres z `visual/targets.ts`), `AUTH: 'anonymous'`.
- Po nagłówku „Kitchen sink” test wylicza nagłówki `getByRole('main').getByRole('heading', { level: 2 })` i dla każdego bierze region o jego nazwie (`getByRole('main').getByRole('region', { name, exact: true })`). Nie wylicza wszystkich regionów, bo 4 otwarte panele dzwonka to też regiony („Powiadomienia”). Dla każdego regionu wykonuje `expect.soft(region).toHaveScreenshot('kitchen-sink-<slug>.png')`, gdzie slug pochodzi z nazwy regionu: małe litery ASCII, polskie litery bez ogonków, spacje → `-`. Dwa regiony o tym samym slugu to czerwony test.
- Bez czekania na brak `aria-busy` (katalog pokazuje go celowo), bez kliknięć i Escape.
- Drugi test w tych samych projektach: `expectNoAxeViolations` na katalogu.
- Funkcja sluga i wyliczanie regionów są wspólne ze strażnikiem wzorców (w `visual/targets.ts` albo osobnym module w `visual/`).

#### 2. Stany hover i focus

**File**: `szczur-radar-web/visual/kitchen-sink.spec.ts`, `szczur-radar-web/visual/targets.ts`

**Intent**: Regresja stylu hover przycisków albo pierścienia `:focus-visible` (`--ring`) jest czerwona w CI. axe tego nie sprawdza.

**Contract**:
- `STATES`: 5 wpisów (nazwa pliku, region, kontrolka po roli i nazwie, stan):

  | Nazwa pliku | Region | Kontrolka | Stan |
  |---|---|---|---|
  | `state-przyciski-primary-hover` | Przyciski | `getByRole('button', { name: 'Domyślny', exact: true }).nth(0)` (wiersz primary) | hover |
  | `state-przyciski-primary-focus` | Przyciski | `getByRole('button', { name: 'Domyślny', exact: true }).nth(0)` (wiersz primary) | focus |
  | `state-przyciski-secondary-hover` | Przyciski | `getByRole('button', { name: 'Domyślny', exact: true }).nth(1)` (wiersz secondary) | hover |
  | `state-przyciski-secondary-focus` | Przyciski | `getByRole('button', { name: 'Domyślny', exact: true }).nth(1)` (wiersz secondary) | focus |
  | `state-pola-search-focus` | Pola | `getByRole('searchbox', { name: 'Puste', exact: true })` | focus |

  Wiersze przycisków to zwykłe `<div class="row">` bez roli i nazwy (`KitchenSinkView.vue:605-609`), więc oba przyciski „Domyślny” mają tę samą nazwę. Indeks idzie za kolejnością `variants`: primary, potem secondary (`KitchenSinkView.vue:54-55`). Lokatory działają w regionie danego wiersza tabeli.

- Jeden test na stan w projektach 375, każdy na świeżej stronie.
- Hover: `hover()` kontrolki i asercja, że pasuje do `:hover`, przed zrzutem i po nim. Gdyby zrzut regionu przewinął stronę, przycisk wyszedłby spod kursora, a wzorzec zapisałby się jako kopia stanu domyślnego.
- Focus: fokus z klawiatury (Tab od poprzedniego elementu albo `focus()`) i asercja, że kontrolka pasuje do `:focus-visible`, zanim powstanie zrzut. Bez tej asercji zrzut mógłby pokazać fokus bez pierścienia i uchodzić za wzorzec.
- Zrzut całego regionu (`toHaveScreenshot('<nazwa>.png')`), bo pierścień wychodzi poza obrys kontrolki.

#### 3. Strażnik wzorców

**File**: `szczur-radar-web/visual/baselines.spec.ts`

**Intent**: Po przebudowie widoków w S-47–S-51 nie zostają osierocone wzorce, które niczego nie pilnują.

**Contract**:
- Test w jednym projekcie (`light-375`) wylicza oczekiwane pliki: `VIEWS` × `PROJECTS`, regiony katalogu (otwiera katalog i liczy je jak test sekcji) × `NARROW_PROJECTS`, `STATES` × `NARROW_PROJECTS`.
- Porównuje je w obie strony z plikami `*.png` w `visual/__screenshots__/<projekt>/` i z listą podkatalogów, bez nieznanego projektu.
- Komunikat błędu nazywa plik osierocony (do usunięcia) albo brakujący (do `npm run visual:update`).

#### 4. Wzorce katalogu

**File**: `szczur-radar-web/visual/__screenshots__/{light,dark}-375/kitchen-sink-*.png`, `…/state-*.png`

**Intent**: 30 nowych wzorców jako stan odniesienia katalogu przed S-46.

**Contract**: wygenerowane przez `npm run visual:update`; commitowane razem z kodem fazy.

### Success Criteria:

#### Automated Verification:

- Cały zestaw przechodzi w kontenerze: `npm run visual:docker` zielone. Testy: 24 widoki, 20 sekcji (2 testy po 10 regionów), 2 testy axe katalogu, 10 stanów, strażnik fontu i strażnik wzorców.
- Wzorców jest 54: `find szczur-radar-web/visual/__screenshots__ -name "*.png" | wc -l` daje `54`, w tym 20 `kitchen-sink-*` i 10 `state-*`.
- Zestaw jest stabilny przy powtórzeniach i równoległości: `npm run visual:docker -- --repeat-each=3 --workers=4` bez czerwonego i bez `flaky`.
- W testach wyglądu nie ma lokatorów CSS, XPath ani czekania na czas: `! grep -nE "page\.locator\(|waitForTimeout|xpath=" szczur-radar-web/visual/*.ts`
- Testy jednostkowe i build przechodzą: `npm test`, `npm run build`.
- CI na PR zielone z nowymi wzorcami: `gh pr checks` bez `fail`, a log kroku zrzutów nie ma `failed`.

**Implementation Note**: Faza nie ma kroków ręcznych. Przegląd wszystkich 54 wzorców przez właściciela jest w fazie 3, po celowych złamaniach.

---

## Faza 3: Celowe złamania i dokumentacja

### Overview

Dowody, że każda bramka czerwienieje na swoim złamaniu i że zapas z artefaktu CI daje działające wzorce. Wytyczne repo dla następnych wycinków. Akceptacja wzorców przez właściciela.

### Changes Required:

#### 1. Dowody celowych złamań

**File**: `context/changes/visual-regression/visual-breaks.md`

**Intent**: Wiadomo, że zrzuty, axe, strażnik fontu i strażnik wzorców łapią to, czego mają pilnować, a nie tylko przechodzą.

**Contract**: metoda jak w `context/archive/2026-11-16-e2e-harness/e2e-breaks.md`: jedno minimalne złamanie naraz, uruchomienie `npm run visual:docker`, zapis testu, który poczerwieniał (nazwa, plik wzorca albo reguła, fragment błędu), cofnięcie i `test -z "$(git status --porcelain szczur-radar-web)"`. Tabela ma 7 wierszy:

| Złamanie | Oczekiwany czerwony |
|---|---|
| zmiana wartości koloru tokenu w jasnym `:root` `style.css` (np. `--accent`) | zrzuty sekcji Tokeny i Przyciski oraz widoki z tym kolorem w projektach jasnych |
| usunięcie tekstu etykiety „E-mail” na `/login` (`LoginView.vue:61`; nie etykiety „Hasło”, bo ten węzeł czyta strażnik fontu) | axe `label` na `view-login` (i zrzut); strażnik fontu zielony |
| `outline: none` dla `:focus-visible` w `style.css` | 3 zrzuty `state-*-focus` i widoki z fokusem po wczytaniu (post, przebieg, aktywacja); zrzuty hover zielone |
| usunięte reguły hover przycisków: `button:where(:hover…)` (`style.css:136-138`) i `.primary:hover` (`AppButton.vue:37-39`) | 2 zrzuty `state-*-hover` w obu projektach 375; zrzuty focus zielone |
| `FONTCONFIG_FILE` pominięte w `launchOptions.env` | strażnik fontu |
| dodatkowy plik `visual/__screenshots__/light-375/orphan.png` | strażnik wzorców: plik osierocony |
| usunięty wzorzec `view-post.png` w `dark-1280`, bieg z `--update-snapshots=none` (jak w CI) | `view-post` w `dark-1280` (brak wzorca, nic nie zapisane) i strażnik wzorców: plik brakujący |

#### 2. Zapas z artefaktu CI

**File**: `context/changes/visual-regression/visual-breaks.md` (sekcja zapasu)

**Intent**: Droga „wzorce z CI” działa naprawdę, gdyby lokalna emulacja kiedyś się rozjechała.

**Contract**:
1. Tymczasowy commit ze zmianą tokenu daje czerwony `web-e2e`, pominięty `railway-config` i artefakt `visual-baselines`.
2. `gh run download <run-id> -n visual-baselines -D szczur-radar-web/visual/__screenshots__` rozpakowuje wzorce lokalnie, a `npm run visual:docker` na zepsutym kodzie jest wtedy zielone.
3. Commit cofający złamanie (bez pobranych wzorców) daje zielony bieg.

Run id i wynik trafiają do sekcji.

#### 3. Wytyczne repo

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Następny agent wie, jak dodać cel, zaktualizować wzorce i czytać różnice, a zmiana wyglądu w S-46–S-59 aktualizuje wzorce świadomie.

**Contract**:
- **„Build, Test, and Development Commands”** → Frontend: `npm run visual:docker` (testy wyglądu i dostępności w przypiętym obrazie Playwright na linux/amd64, Docker; na arm64 przez emulację) i `npm run visual:update` (zapisuje brakujące i zmienione wzorce).
- **„Coding Style & Testing”**, nowy punkt „Zrzuty porównawcze i dostępność (F-06)”:
  - konfiguracja `playwright.visual.config.ts`, testy w `szczur-radar-web/visual/`, fixture z `e2e/fixtures.ts`;
  - 4 projekty, wzorce w `visual/__screenshots__/<projekt>/`;
  - renderuje tylko obraz (strażnik środowiska), font to alias testów `visual/fonts.conf` (Liberation Sans) pilnowany strażnikiem;
  - nowy widok to wpis w `VIEWS`, nowa sekcja katalogu dostaje wzorzec sama, nowy stan to wpis w `STATES`;
  - strażnik wzorców wymaga zgodności w obie strony;
  - axe WCAG 2.0/2.1/2.2 A/AA blokuje, a wyjątek to zmiana kodu testu z powodem w komentarzu;
  - te same reguły lokatorów co E2E.
- **„UI (szczur-radar-web)”**, punkt „Visual check”:
  - regresję wyglądu pilnują wzorce F-06;
  - zmiana wyglądu aktualizuje wzorce w tym samym PR, a opis PR mówi, które grupy wzorców się zmieniły i dlaczego (nigdy aktualizacja tylko po to, żeby CI było zielone);
  - przegląd to różnice obrazków w „Files changed” albo raport `playwright-report-visual` z artefaktu;
  - ręczne zrzuty `playwright-cli` zostają dla stanów, których wzorce nie pokrywają.
- **„Deployment”** → lista jobów CI: `web-e2e` uruchamia też testy wyglądu, a czerwony krok zrzutów zostawia artefakt `visual-baselines`. Jeśli faza 1 przeszła na zapas, droga z artefaktu jest jedyną drogą aktualizacji.

#### 4. Zakres weryfikacji

**File**: `context/foundation/verification-scope.md`

**Intent**: Lista ręcznych kontroli przestaje mówić, że wygląd widoków jest ręczny.

**Contract**: pozycja „Wygląd widoków w przeglądarce (do F-06: …)” w sekcji „Co zostanie ręczne” zastąpiona pozycją: akceptacja nowego wyglądu, czyli ocena różnic obrazków w PR. Zrzuty porównawcze i axe (F-06, `szczur-radar-web/visual/`) łapią każdą zmianę wyglądu i naruszenie dostępności w CI. Zdanie o E2E (F-05) zostaje.

### Success Criteria:

#### Automated Verification:

- Każda bramka czerwienieje na swoim złamaniu: 7 złamań z `visual-breaks.md` wykonanych i cofniętych, tabela wypełniona, `test -z "$(git status --porcelain szczur-radar-web)"` po cofnięciu (widzi też nieśledzony `orphan.png` i przywrócony `view-post.png`, czego `git diff` nie widzi), a `npm run visual:docker` potem zielone.
- Zapas z artefaktu CI daje działające wzorce: sekcja zapasu w `visual-breaks.md` ma run id czerwonego biegu z artefaktem `visual-baselines` (`gh api repos/{owner}/{repo}/actions/runs/<run-id>/artifacts` zawiera `visual-baselines`), zielony lokalny bieg na pobranych wzorcach i zielony bieg po cofnięciu.
- Wytyczne opisują polecenia i reguły: `grep -q "visual:update" REPOSITORY_GUIDELINES.md && grep -q "visual-baselines" REPOSITORY_GUIDELINES.md && grep -q "playwright.visual.config.ts" REPOSITORY_GUIDELINES.md`
- `verification-scope.md` nie liczy wyglądu jako ręcznej kontroli: `! grep -q "do F-06" context/foundation/verification-scope.md && grep -q "szczur-radar-web/visual/" context/foundation/verification-scope.md`
- Każdy job CI na PR jest zielony, w tym `railway-config`: `gh pr checks` bez `fail`.

#### Manual Verification:

- Właściciel przegląda 54 wzorce w PR („Files changed”) i akceptuje je jako stan odniesienia wyglądu przed S-46, w tym renderowanie fontem Liberation Sans zamiast kroju systemu.

**Implementation Note**: Pod `/10x-implement` po zielonej weryfikacji automatycznej zatrzymaj się na akceptację właściciela. Pod `/10x-goal-implement` wiersz Manual trafia na listę końcową. Merge robi człowiek.

---

## Testing Strategy

### Unit Tests:

- Bez nowych testów Vitest. Kod aplikacji się nie zmienia. Kryteria pilnują, że `npm test` dalej przechodzi i nie zbiera `visual/`.

### Integration Tests:

- Testy wyglądu (`visual/*.spec.ts`) to testy w przeglądarce na atrapie API zgodnej z kontraktem:
  - zrzut i axe każdego widoku w 4 projektach;
  - sekcje i axe katalogu w 2 projektach;
  - 5 stanów;
  - strażnik fontu i strażnik wzorców.
- Celowe złamania z fazy 3 dowodzą, że każda bramka czerwienieje: kolor, etykieta, pierścień, hover, font, plik osierocony, brak wzorca.
- E2E z F-05 (`npm run e2e`, 18 testów) zostają bez zmian i chodzą w tym samym jobie.

### Manual Testing Steps:

1. Przejrzyj w PR („Files changed”) 54 nowe PNG: widoki w obu motywach i szerokościach, sekcje katalogu, stany hover/focus.
2. Zaakceptuj je jako stan odniesienia (wiersz Manual fazy 3).

## Performance Considerations

- **Czas CI.** Pomiar: 36 kombinacji (zrzut i axe) trwa ~25 s na 4 workerach. Krok zrzutów dokłada drugi build i serwer dev, więc `web-e2e` wydłuża się szacunkowo o 1–1,5 min (z ~50 s), przy ~5,5 min zapasu do `mutation` i `timeout-minutes: 10`.
- **Emulacja amd64 na Macu.** Pobranie obrazu amd64 (~2 GB) przy pierwszym uruchomieniu. Testy pod Rosettą są wolniejsze niż natywnie, ale to pętla lokalna.
- **Rozmiar repo.** 54 PNG zamiast ~19 MB całostronicowych dzięki przycięciu feedu (~1,3 MB → kilkaset KB na zrzut) i podziałowi katalogu na sekcje. Zmiana jednego komponentu przepisuje tylko pliki jego sekcji. Pełna aktualizacja palety w S-46 przepisuje wszystkie pliki raz.

## Migration Notes

- Nic do migrowania. S-46 (nowa paleta) celowo zmienia wszystkie 54 wzorce w jednym PR, jako świadomą akceptację właściciela (roadmapa `:201`).
- Podniesienie `@playwright/test` zmienia też tag obrazu w `ci.yml` (istniejąca kontrola wersji) i może zmienić renderowanie. Wtedy w tym samym PR idzie `npm run visual:update` i przegląd różnic.

## References

- Roadmapa F-06: `context/foundation/roadmap.md:174-188`; PRD NFR: `context/foundation/prd.md:328-330`
- F-05 (plan, przeglądy, złamania): `context/archive/2026-11-16-e2e-harness/plan.md`, `reviews/plan-review.md`, `reviews/impl-review.md`, `e2e-breaks.md`
- Audyt gotowości UI: `context/changes/post-m4-ui-readiness-audit/research.md:89-97` (co pokazuje katalog)
- Konfiguracja i fixture E2E: `szczur-radar-web/playwright.config.ts`, `szczur-radar-web/e2e/fixtures.ts`, `szczur-radar-web/dev/mock-api.js` (nagłówek: tryby, id przebiegów i postów)
- Katalog: `szczur-radar-web/src/views/KitchenSinkView.vue:585-705`; router: `szczur-radar-web/src/router/index.ts:42-53`
- Job CI: `.github/workflows/ci.yml:127-177`
- Pomiar próbny (scratchpad sesji planowania, nietrwały): skrypty probe axe i zrzutów, wyniki podsumowane w „Current State Analysis”
- Issue: #177

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Harness zrzutów i widoki

#### Automated

- [x] 1.1 Testy jednostkowe frontu przechodzą, a Vitest nie zbiera visual/ — cccf28a
- [x] 1.2 vue-tsc -b sprawdza kod testów wyglądu — cccf28a
- [x] 1.3 Zestaw wyglądu przechodzi dwa razy z rzędu w kontenerze — cccf28a
- [x] 1.4 Wzorców widoków jest 24 — cccf28a
- [x] 1.5 Poza obrazem konfiguracja odmawia — cccf28a
- [x] 1.6 Kontener nie podmienia node_modules hosta — cccf28a
- [x] 1.7 W testach wyglądu nie ma lokatorów CSS, XPath ani czekania na czas — cccf28a
- [x] 1.8 Obraz web nie dostaje wzorców ani raportów wyglądu — cccf28a
- [x] 1.9 PR ma w opisie Refs #177 — cccf28a
- [x] 1.10 Wzorce z Maca przechodzą w CI — cccf28a

### Phase 2: Katalog komponentów

#### Automated

- [x] 2.1 Cały zestaw przechodzi w kontenerze — b99eae4
- [x] 2.2 Wzorców jest 54 — b99eae4
- [x] 2.3 Zestaw jest stabilny przy powtórzeniach i równoległości — b99eae4
- [x] 2.4 W testach wyglądu nie ma lokatorów CSS, XPath ani czekania na czas — b99eae4
- [x] 2.5 Testy jednostkowe i build przechodzą — b99eae4
- [x] 2.6 CI na PR zielone z nowymi wzorcami — b99eae4

### Phase 3: Celowe złamania i dokumentacja

#### Automated

- [x] 3.1 Każda bramka czerwienieje na swoim złamaniu — dbc7731
- [x] 3.2 Zapas z artefaktu CI daje działające wzorce — dbc7731
- [x] 3.3 Wytyczne opisują polecenia i reguły — dbc7731
- [x] 3.4 verification-scope.md nie liczy wyglądu jako ręcznej kontroli — dbc7731
- [x] 3.5 Każdy job CI na PR jest zielony — dbc7731

#### Manual

- [ ] 3.6 Właściciel przegląda 54 wzorce i akceptuje je jako stan odniesienia przed S-46
