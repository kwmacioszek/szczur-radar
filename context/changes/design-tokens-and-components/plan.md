# F-08: tokeny, kroje i komponenty bazowe z projektu F-07 — plan implementacji

Przegląd planu zakończono 2026-11-21. Werdykt po poprawkach **SOUND**, ustalenia F1–F9 zamknięte. [Raport z przeglądu](reviews/plan-review.md)

## Overview

F-08 przenosi język wizualny zaakceptowanego projektu F-07 (`context/foundation/ui-design.md`, sekcja „Wdrożenie”) do `szczur-radar-web`:
- tokeny z `ui-design/tokens.css` w `src/style.css`;
- trzy kroje w plikach aplikacji, ikony z jednego sprite'a;
- komponenty bazowe: przycisk, komunikat, znacznik stanu, stanowisko i cel ze stanowiskiem, motyw, pola, odnośnik, ikona;
- 73 pary kontrastu z progiem dla każdej pary oraz test kroju na nowych krojach.

Obecne widoki dostają nowy wygląd w starym układzie, a wzorce F-06 zmieniają się raz, jako akceptacja wyglądu. Odblokowuje S-47 i S-49, a przez nie resztę M-5.

## Current State Analysis

- **Tokeny.** `src/style.css` ma tokeny S-46 (paleta A): 12 ról koloru, `--ring` i dwie krawędzie jako `var(...)`, pięć rozmiarów pisma w px, `--space-0`…`-5`, dwa promienie oraz `--sans`/`--mono` z krojami systemowymi.
  - Pismo strony to `18px/145%`, 16 px poniżej 1024 px, z `letter-spacing: 0.18px` (`style.css:53-65`).
  - Pola mają wysokość `calc(1.45rem + 14px)` (`style.css:174`).
  - Globalny `button` to wariant pomocniczy z obwódką akcentu (`style.css:138-156`).
- **Zmiana znaczenia nazw.** Dziś `--border` to linia ozdobna, a `--border-strong` to krawędź pola. W projekcie `--line` to linia, a `--border` to krawędź kontrolki.
  - `--border` jest używany w 9 plikach: AppHeader:58, NotificationMenu:255, PostAssessment:116, PostCard:56,67,154, AdminView:653,687, KitchenSink:766,797, PostDetailView:266,278, RunDetailView:363,414, oraz w fiksturze `styleContract.test.ts:206`.
  - `--border-strong` jest w 5 plikach: style.css:178, ActivateView:205, KitchenSink:825, NotificationMenu:222, PostAssessment:162.
- **Skala pisma przesuwa się pod tymi samymi nazwami.** Dziś `--font-size-lg` to 20 px, a w projekcie `-lg` to 16 px i 20 px to `-2xl`. Dziś `-xl` to 32 px, a w projekcie `-xl` to 18 px i 32 px to `-5xl`.
  - `--font-size-lg` jest w AdminView:636, KitchenSink:733, PostDetailView:216, RunDetailView:322.
  - `--font-size-xl` jest w style.css:91, 124.
  - Bez przemianowania nagłówki po cichu by zmalały.
- **Brak krojów, ikon i katalogu `public/fonts/`.** `public/` ma tylko `favicon.svg`. Jedyna ikona to ręcznie narysowany dzwonek w `NotificationMenu.vue:117-129`. `package.json` nie ma bibliotek ikon ani `@fontsource`.
- **Bramki dziś:**
  - `styleContract.test.ts`: 19 par z jednym progiem 4,5, lista `CLEANED` (17 plików) i skan literałów.
  - `visual/font.spec.ts`: oczekuje `Liberation Sans` z aliasu `visual/fonts.conf`.
  - 56 wzorców w `visual/__screenshots__/`.
  - `visual/targets.ts`: 5 stanów (`STATES`) w regionach „Przyciski” i „Pola”.
  - Żaden test nie czeka na `document.fonts.ready`.
- **Serwowanie.** Caddy (`Caddyfile:36-41`) dla brakującego pliku zwraca `index.html` z kodem 200. CSP nie ma. Smoke kontenera (`ci.yml:212-246`) nie sprawdza zasobów statycznych.
- **Tony i słowa.** Słowa i tony stanów pochodzą z `src/runs.ts` i `src/labels.ts`, a projekt ich nie zmienia (`ui-design.md:450,531`).
- **Skąd brać wartości.** Mapa tokenów, mapa komponentów i lista świadomych zmian bramek są w `ui-design.md:616-707`. Wartości i reguły komponentów są w `ui-design/prototypes/prototype.css` (wzór do odczytania, nie plik do skopiowania).

## Desired End State

**Tokeny i kroje:**
- `style.css` ma w `:root` i `:root[data-theme='dark']` dokładnie te własności `--*` co `ui-design/tokens.css`, z tymi samymi wartościami.
- Tło strony ma poświatę `--bg-glow`.
- `html` ma `font-size: 100%`. Pismo na `body` to `--font-size-md`/`--leading-normal` krojem `--font-ui`, bez zmiany rozmiaru przy 1024 px i bez światła między literami.
- Nagłówki, tytuły, treść posta i cytaty są złożone szeryfem `--font-display`, a surowy JSON krojem `--font-mono`.
- Sześć plików `woff2` z licencjami OFL leży w `szczur-radar-web/public/fonts/`. Wskazują je reguły `@font-face` z `font-display: swap` i `unicode-range` z `ui-design/fonts/README.md`.
- `index.html` ładuje wstępnie dwa pliki podzbioru łacińskiego (Newsreader i Hanken Grotesk).

**Bramki:**
- Test kontrastu sprawdza 73 pary z `src/contrastPairs.json` (kopia `ui-design/pairs.json`). Próg pochodzi z każdej pary: 4,5 dla tekstu, 3,0 dla elementów interfejsu.
- `visual/font.spec.ts` oczekuje Newsreader dla nagłówka „Logowanie” i Hanken Grotesk dla etykiety „Hasło”.
- Testy wizualne robią zrzut po `document.fonts.ready`.
- Smoke kontenera pobiera plik `woff2` i jest czerwony, gdy dostaje HTML.

**Komponenty i katalog:**
- `src/components/` ma `AppIcon` i `IconSprite`: sprite wpisany raz w `App.vue` i zamknięty zbiór nazw ikon pilnowany testem.
- Komponenty bazowe mają wygląd i stany z arkusza komponentów:
  - `AppButton`: główny, zwykły, z ikoną, sama ikona, duży, w toku ze znakiem pracy.
  - `StatusMessage`: tony powodzenie, błąd i uwaga, każdy z ikoną.
  - `StatusBadge`: nowe tło `-soft` i opcjonalna ikona.
  - `StanceText`: znak, słowo, kolor i tło dla sześciu kierunków, wariant z nazwą i rodzajem celu.
  - `ThemeToggle`: sama ikona z nazwą „Ciemny motyw”.
  - Dzwonek z ikoną `i-bell`.
  - Pola i odnośnik ze stylem globalnym.
- `/kitchen-sink` ma sekcje pod nazwami komponentów projektu dla wszystkiego, co buduje F-08, we wszystkich stanach z arkusza.

**Widoki:**
- Obecne widoki nie mają żadnego odwołania do starych nazw (`--border-strong`, `--sans`, `--mono`, `--border` w znaczeniu linii) ani do rozmiarów przesuniętych bez przemianowania.
- Układ widoków jest bez zmian.
- Wszystkie wzorce F-06 są nowe, a CI (`web-e2e` z krokiem wizualnym) jest zielone.

**Strażnik i dokumentacja:**
- `node tools/design-check/design-check.mjs app` porównuje projekt z aplikacją: tokeny, pary, sprite i pliki krojów. Kończy się kodem 0, a jego selftest wykrywa każdy rozjazd.
- `REPOSITORY_GUIDELINES.md` (sekcja UI i opis F-06), `tools/design-check/README.md` i `ui-design.md` opisują stan po F-08.
- `## Parked` w roadmapie ma zadanie: test zgodności w CI, do decyzji przed zamknięciem M-5.

### Key Discoveries:

- Mapa tokenów z pułapką `--border`: `ui-design.md:628-660`. Obie nazwy zmieniają się w jednym commicie (`:658`).
- Świadome zmiany bramek: `ui-design.md:695-707`, w tym dwa `-1px` z `prototype.css` (`:701`).
- Test kontrastu wymaga, żeby rola w parze była hexem w obu blokach (`styleContract.test.ts:80-85`). Tokeny projektu spełniają ten warunek, także `--ring` i `--border`.
- `design-check contrast` czyta pliki tak samo jak test aplikacji (`tools/design-check/README.md`, tabela poleceń). `fixtures/app-pairs.json` dowodzi tego dziś na 19 parach.
- Sprite ma identyfikatory `i-*`: 19 ikon Lucide z kreską w siatce 24, plus `i-brand`. Ma też `s-up`, `s-down`, `s-flat`, `s-mixed` i `s-none`, czyli wypełnione znaki stanowiska w siatce 12 (`ui-design/icons/sprite.svg`, 5,6 kB). „niejasno” i „bez stanowiska” mają ten sam znak `s-none`.
- Dopisek „(ostrożnie)” arkusz rysuje nawiasami z CSS (`prototype.css:570-576`). Testy oczekują dosłownego „wzrostowo (ostrożnie)” (`StanceText.test.ts:12`, `PostCard.test.ts:120`).
- Testy, które łamie zmiana komponentów:
  - `AppButton.test.ts:6-20`: dokładna lista klas.
  - `StatusMessage.test.ts:6-15`: `tagName` to `P`.
  - `ThemeToggle.test.ts:14,19` oraz `AppHeader.test.ts:49,55`: `text()` to „Ciemny motyw”.
- E2E szukają po nazwach dostępnych, a te się nie zmieniają, bo ikony mają `aria-hidden`.
- Lokatory stanów w `visual/targets.ts:215-216` używają `nth(0)`/`nth(1)` dla dwóch przycisków „Domyślny” w regionie „Przyciski”.
- `railway-config` ma `needs:` na `web-e2e`. Póki krok wizualny jest czerwony, środowisko PR się nie wdraża (`ci.yml:394-400`).

## What We're NOT Doing

- Układu ekranów: pasek u góry, jedna kolumna, wiersz posta, panel boczny, tabele, części panelu admina. Robią to S-47–S-59.
- Nowych wariantów w widokach (decyzja właściciela). Przyciski widoków nie dostają ikon. Odnośnik do oryginału nie dostaje ikony `external-link`. Znacznik „wymaga uwagi” nie dostaje ikony. Warianty są na `/kitchen-sink`, a wpinają je wycinki ekranów.
- Komponentów należących do wycinków: nawigacji i nagłówka strony (S-47), ładowania, pustej listy, błędu z ponowieniem i stronicowania (S-48), tabeli i części panelu admina (S-50), wiersza posta, panelu bocznego i filtrów (S-51+), przełącznika zakresu (S-52), cytatu w treści (S-53), paska proporcji, klocka liczby i karty celu dnia (S-57–S-59).
- Zmiany nazw sekcji katalogu komponentów, których F-08 nie przebudowuje: „Posty”, „Ocena posta”, „Filtr” i „Dzwoneczek” zmienią nazwy w swoich wycinkach.
- Sprzątania `LoginView.vue` z px i dopisania go do `CLEANED`. Projekt przypisuje to S-49 (`ui-design.md:701`). W samym widoku F-08 ogranicza edycje do przepięcia nazw tokenów i klasy formularza, która zachowuje lokalny styl pól do S-49.
- Zmian słów i tonów stanów w `src/runs.ts` i `src/labels.ts`, w tym tonu „wymaga uwagi”.
- Testu zgodności projektu z aplikacją w CI. Jest tylko polecenie ręczne, a test w CI czeka w `## Parked` do decyzji przed zamknięciem M-5 (decyzja właściciela).
- Nagłówków cache i CSP w Caddy.
- Zmian w API i kontrakcie SPA ↔ API.

## Implementation Approach

Kolejność idzie za sekcją „Kolejność” projektu (`ui-design.md:689-693`): najpierw tokeny i kroje, potem ikony i komponenty, na końcu przegląd widoków i nowe wzorce.

Faza 1 jest jednym atomowym przepięciem nazw. Każde użycie starej nazwy zmienia się w tym samym commicie co definicja w `style.css`, więc żaden widok nie dostaje grubszej linii ani mniejszego nagłówka przez pomyłkę.

Wzorce F-06 powstają raz, w fazie 3, po wszystkich zmianach wyglądu. Między fazami 1 a 3 krok wizualny w CI jest czerwony na starych wzorcach, co jest zamierzone. Bramką faz 1–2 są Vitest, E2E i build.

Strażnik zgodności (faza 4) to nowe polecenie `design-check`, bez zadania CI, jak reszta tego narzędzia.

## Critical Implementation Details

- **Przemianowanie przed podmianą wartości.** W fazie 1 trzeba przemianować `--font-size-lg`→`--font-size-2xl` i `--font-size-xl`→`--font-size-5xl` we wszystkich plikach w tym samym commicie, w którym `style.css` dostaje skalę projektu. Tak samo `--border`→`--line` i `--border-strong`→`--border`. Najpierw przemianowanie starych użyć, potem nowe definicje: odwrotna kolejność zmienia znaczenie `--border` pod istniejącymi użyciami.
- **Nazwa kroju w CDP.** `CSS.getPlatformFontsForNode` podaje nazwę rodziny z pliku kroju, która nie musi być równa nazwie z `@font-face`. Na początku fazy 1 trzeba odczytać, co Chromium w obrazie Playwright podaje dla dwóch plików łacińskich, i wpisać tę nazwę do `font.spec.ts`. Kontrola `design-check pages` z F-07 przechodzi na tych plikach, więc nazwy są do ustalenia, nie do zgadywania.
- **Kroje a zrzuty.** Przy `font-display: swap` krój zastępczy może zmienić wysokość treści i położenie kontrolek. Czekaj na `document.fonts.ready` po pojawieniu się tekstu, przed pomiarem kadru feedu, ustawieniem hover lub fokusu w katalogu oraz odczytem kroju przez CDP.
- **Pierwszy `visual:update` z nowymi celami jest czerwony raz.** `baselines.spec.ts` biegnie równolegle i nie widzi plików, które jeszcze nie powstały. Drugi przebieg jest zielony (S-46, `plan.md:34,145`).

## Faza 1: Tokeny i kroje

### Overview

`style.css` dostaje tokeny projektu, a wszystkie pliki przechodzą na nowe nazwy w jednym commicie. Kroje są w aplikacji. Bramki kontrastu i kroju znają nowy stan, a smoke kontenera łapie brakujący krój.

### Changes Required:

#### 1. Tokeny w `style.css`

**File**: `szczur-radar-web/src/style.css`, `szczur-radar-web/src/views/{FeedView,PostDetailView,RunDetailView,AdminView}.vue`, `szczur-radar-web/src/components/{PostCard,NotificationMenu}.vue`

**Intent**:
- Zastąpić tokeny S-46 tokenami z `context/foundation/ui-design/tokens.css` (role, kroje, skale, wymiary, ruch, warstwy).
- Ustawić `font-size: 100%` na `html`, a typografię strony i tło z poświatą `--bg-glow` na `body` (wzór `prototype.css:79-98`).
- Przenieść globalne reguły na nową skalę: `h1` szeryfem z `--weight-medium`, `--leading-tight` i `--tracking-tight`; `.page`, `.card`, `.eyebrow` i `.lead` bez px.
- Zachować poniższe szerokości w tym samym commicie, w którym zmienia się baza `rem`.
- Dodać globalną klasę `.visually-hidden` (`ui-design.md:701`).

**Contract**:
- Własności `--*` w obu blokach są identyczne nazwą i wartością z `tokens.css`. Strażnik `app` w fazie 4 porównuje je w obie strony.
- Plik nie ma zapytania o `prefers-color-scheme` (`styleContract.test.ts`, „theme source”).
- Rozmiar pisma nie zmienia się przy 1024 px. Media query `.card h1` zostaje jako reguła układu, przepięta na tokeny.
- Opis w nagłówku pliku wskazuje na `ui-design.md` zamiast palety A.

Poniżej i przy 1024 px zostają obecne wartości w `rem`. Powyżej 1024 px użyć wartości z trzeciej kolumny, żeby przy domyślnej bazie 16 px zachować wymiar liczony dotąd z bazy 18 px.

| Element | Do 1024 px | Powyżej 1024 px |
| --- | --- | --- |
| `.card` logowania i aktywacji, `max-width` | `34rem` (544 px) | `38.25rem` (612 px) |
| Feed, szczegóły posta i szczegóły przebiegu, `max-width` | `48rem` (768 px) | `54rem` (864 px) |
| Panel admina, `max-width` | `56rem` (896 px) | `63rem` (1008 px) |
| Pierwsza kolumna karty posta, gdy działa układ dwukolumnowy | `9rem` (144 px) | `10.125rem` (162 px) |
| Limit szerokości panelu powiadomień wewnątrz `min()` | `24rem` (384 px) | `27rem` (432 px) |

#### 2. Przepięcie nazw we wszystkich plikach

**File**: `szczur-radar-web/src/**/*.vue`, `szczur-radar-web/src/styleContract.test.ts`

**Intent**: W tym samym commicie co zmiana 1 przepiąć każde użycie starej nazwy (pliki i linie w Current State Analysis), żeby wygląd każdego elementu wynikał z tokenu o tym samym znaczeniu.

**Contract**:
- `--border` (linia) → `--line`.
- `--border-strong` → `--border`, z wyjątkiem kreski cytatu `PostAssessment.vue:162`. Kreska cytatu jest linią, więc dostaje `--warning`, jak cytat w projekcie (`ui-design.md:233`).
- `--sans` → `--font-ui`, `--mono` → `--font-mono`.
- `--font-size-lg` → `--font-size-2xl`, `--font-size-xl` → `--font-size-5xl`.
- `--font-size-md` i `--font-size-display` zostają pod tą samą nazwą (15 px i 48 px według mapy).
- `KitchenSinkView.vue` `colourRoles` pokazuje wszystkie role projektu: dochodzą `--bg-glow`, `--surface-2`, `--selected`, `--scrim`, `--text-muted`, `--line` i pięć teł `-soft`.
- Po zmianie żaden plik w `src/` nie używa `--border-strong`, `--sans` ani `--mono`, a skan `CLEANED` jest zielony.

#### 3. Kroje w aplikacji

**File**: `szczur-radar-web/public/fonts/` (nowy), `szczur-radar-web/src/style.css`, `szczur-radar-web/index.html`

**Intent**:
- Skopiować bez zmian sześć plików `woff2` i trzy licencje `OFL-*.txt` z `context/foundation/ui-design/fonts/`.
- Opisać je regułami `@font-face`.
- Ładować wstępnie dwa pliki łacińskie używane na każdym ekranie, żeby kroje docierały bez mignięcia kroju zastępczego.

**Contract**:
- Sześć reguł `@font-face` w `style.css`:
  - rodziny `Newsreader`, `Hanken Grotesk` i `JetBrains Mono`;
  - `font-weight` jako zakres z `ui-design/fonts/README.md`;
  - `font-display: swap`;
  - `unicode-range` `latin` i `latin-ext` z tego README;
  - `src: url('/fonts/<plik>.woff2') format('woff2')`.
- Ścieżki bezwzględne do `public/`: Vite zostawia je bez hasha, więc preload i `@font-face` wskazują ten sam adres.
- `index.html` ma dwa `<link rel="preload" as="font" type="font/woff2" crossorigin>` dla `newsreader-latin-wght-normal.woff2` i `hanken-grotesk-latin-wght-normal.woff2`. Klasyczny skrypt motywu zostaje pierwszy w `<head>`.

#### 4. Pary kontrastu z progiem dla każdej pary

**File**: `szczur-radar-web/src/contrastPairs.json` (nowy), `szczur-radar-web/src/styleContract.test.ts`

**Intent**:
- Zastąpić 19 par z jednym progiem 73 parami projektu, każdą z własnym progiem i rodzajem.
- Trzymać pary jako plik JSON w module web, bo obraz Dockera buduje testy tylko z `szczur-radar-web/`.

**Contract**:
- `contrastPairs.json` jest kopią `context/foundation/ui-design/pairs.json` co do bajtu (`{fg, bg, min, kind}`). Strażnik `app` w fazie 4 to porównuje.
- Test importuje plik jak `rules.json` (`src/contract/rules.test.ts:45`).
- Każdy przypadek ma nazwę `$fg on $bg ($theme, $kind) ≥ $min:1`.
- `MIN_CONTRAST` znika.
- Komentarz przy `CLEANED` dostaje poprawny zakres linii `LoginView.vue` (dziś 84-131, nieaktualne 77-102) i informację, że plik dochodzi w S-49.

#### 5. Bramka kroju i zrzuty po załadowaniu krojów

**File**: `szczur-radar-web/visual/font.spec.ts`, `szczur-radar-web/visual/fonts.conf`, `szczur-radar-web/visual/views.spec.ts`, `szczur-radar-web/visual/kitchen-sink.spec.ts`, `szczur-radar-web/visual/targets.ts`

**Intent**:
- Strażnik kroju sprawdza kroje aplikacji zamiast aliasu.
- Każdy zrzut powstaje dopiero po załadowaniu krojów.

**Contract**:
- `font.spec.ts` oczekuje dla nagłówka 1 „Logowanie” dokładnie rodziny Newsreader, a dla etykiety „Hasło” dokładnie Hanken Grotesk, pod nazwą, którą zgłasza CDP (Critical Implementation Details). Test dalej jest czerwony przy każdej innej rodzinie, także przy kroju zastępczym z aliasu. Nazwa testu i komentarz mówią o krojach aplikacji.
- `fonts.conf` zostaje jako krój zastępczy dla znaków spoza podzbiorów, a jego komentarz to mówi.
- Wspólna funkcja `waitForFonts(page)` w `visual/targets.ts` czeka na `document.fonts.ready`. Wywołania:
  - `views.spec.ts` po `view.ready(page)`, przed oboma wariantami zrzutu. W feedzie także przed `boundingBox()` siódmej karty, który wyznacza granicę kadru.
  - `kitchen-sink.spec.ts` po `openCatalog(page)` w teście sekcji i w testach stanów. W testach stanów czekanie poprzedza przewinięcie do regionu oraz ustawienie hover lub fokusu.
  - `font.spec.ts` po sprawdzeniu widoczności „Logowanie” i „Hasło”, przed pierwszym wywołaniem `platformFonts`.

#### 6. Smoke kontenera sprawdza krój

**File**: `.github/workflows/ci.yml` (zadanie `web-container-smoke`, krok „Smoke test /healthz and /”)

**Intent**: Brakujący albo źle podpięty plik kroju czerwieni CI, a nie przechodzi jako HTML z kodem 200 z fallbacku SPA.

**Contract**:
- `curl -fsS -o /dev/null -w '%{content_type}' http://localhost:8080/fonts/hanken-grotesk-latin-wght-normal.woff2` zwraca typ zaczynający się od `font/woff2`.
- Komentarz w kroku wyjaśnia, że fallback `try_files` oddaje `index.html` z kodem 200.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym 73 pary × 2 motywy i skan `CLEANED`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy E2E przechodzą bez zmian w specach: `cd szczur-radar-web && npm run e2e`
- W `szczur-radar-web/src/` nie ma starych nazw: `grep -rnE -- '--border-strong|--sans\b|--mono\b' szczur-radar-web/src` nic nie zwraca
- Parser `design-check` czyta nowy `style.css` z parami aplikacji: `node tools/design-check/design-check.mjs contrast szczur-radar-web/src/style.css szczur-radar-web/src/contrastPairs.json` kończy się kodem 0
- Strażnik kroju przechodzi w obrazie Playwright: `cd szczur-radar-web && npm run visual:docker -- visual/font.spec.ts`
- Smoke kontenera z nowym krokiem przechodzi lokalnie: `docker build -t szczur-radar-web ./szczur-radar-web`, uruchomienie i ten sam `curl` daje `font/woff2`
- Celowe złamanie: usunięty plik `public/fonts/hanken-grotesk-latin-wght-normal.woff2` czerwieni lokalny smoke i `font.spec.ts`; po przywróceniu oba są zielone

#### Manual Verification:

- (brak — wygląd ocenia właściciel w fazie 3 na wzorcach)

---

## Faza 2: Ikony i komponenty bazowe

### Overview

Aplikacja ma komponent ikony i sprite. Komponenty bazowe mają wygląd i stany z arkusza komponentów. Katalog pokazuje je pod nazwami projektu, a cele wizualne znają nowe sekcje i stany.

### Changes Required:

#### 1. Ikony

**File**: `szczur-radar-web/src/assets/icons/sprite.svg` i `LICENSE-lucide.txt` (nowe, kopie z `context/foundation/ui-design/icons/`), `szczur-radar-web/src/components/{AppIcon.vue,AppIcon.test.ts,IconSprite.vue,iconNames.ts}` (nowe), `szczur-radar-web/src/App.vue`

**Intent**: Jedno źródło ikon w aplikacji. Sprite jest wpisany raz w stronę, bez żądania sieciowego, więc ikona jest gotowa razem z dokumentem i nie destabilizuje zrzutów. Komponent rysuje ikonę z tego sprite'a według reguł projektu (`ui-design.md:341-366`).

**Contract**:
- `IconSprite` wstawia zawartość `sprite.svg` (import `?raw`) w ukryty element (`aria-hidden="true"`, bez wymiarów). `App.vue` renderuje go raz, przed `RouterView`.
- `iconNames.ts` eksportuje krotkę `ICON_NAMES` zadeklarowaną z `as const` i typ `IconName = (typeof ICON_NAMES)[number]`. Krotka zawiera identyfikatory symboli sprite'a (`i-*` i `s-*`). `AppIcon` używa typu w propie `name: IconName`.
- Rysuje `<svg aria-hidden="true" focusable="false"><use href="#<name>"/></svg>`:
  - `i-*` z kreską `currentColor`, `--icon-stroke` i `--icon-size`;
  - `s-*` wypełnione `currentColor`, w rozmiarze znaku stanowiska z `prototype.css:184-186`.
- `AppIcon.test.ts` porównuje posortowaną kopię `ICON_NAMES` z posortowaną listą identyfikatorów `<symbol id>` odczytanych z `sprite.svg?raw`. Wymaga zgodności obu list, więc wykrywa brakujące, nadmiarowe i powtórzone pozycje w krotce. Sprawdza też `aria-hidden` i `focusable`.
- Oba komponenty trafiają do `CLEANED`.

#### 2. Przycisk

**File**: `szczur-radar-web/src/components/AppButton.vue`, `AppButton.test.ts`, `szczur-radar-web/src/style.css` (globalny `button`)

**Intent**: Przycisk ma warianty i stany z arkusza: główny, zwykły, z ikoną, sama ikona, duży, w toku ze znakiem pracy (`ui-design.md:434`, `prototype.css:257-321, 1559-1617`). Natywne przyciski w widokach dalej wyglądają jak wariant zwykły.

**Contract**:
- Jedynym korzeniem `AppButton` jest natywny `<button>`. Korzeń zachowuje przekazywanie atrybutów, klas i zdarzeń oraz klasy wariantów `primary` i `secondary`. `NotificationMenu` przywraca fokus przez `$el`, a `NotificationBell.test.ts` sprawdza klasę `secondary` akcji oznaczania powiadomień.
- Istniejące propy zostają (`variant`, `type`, `disabled`, `busy`, `busyLabel`). Dochodzą:
  - `icon?: IconName`: ikona przed tekstem;
  - `size?: 'md' | 'lg'`;
  - jawny tryb samej ikony. Nazwa dostępna jest wtedy obowiązkowa (`aria-label` z atrybutów albo propu); test sprawdza, że przycisk z samą ikoną ma niepustą nazwę.
- W toku przycisk jest natywnie wyłączony, ma `aria-busy="true"` i znak pracy z `aria-hidden`. Znak poprzedza tekst `busyLabel`, jeśli go podano, albo dotychczasową treść slotu, jak w przycisku „Wyloguj”. Obrót tylko przy `prefers-reduced-motion: no-preference`.
- Przycisk z samą ikoną z `aria-pressed="true"` albo `aria-expanded="true"` ma tło `--selected` i kolor `--accent`.
- Globalna reguła `button` w `style.css` ma wygląd wariantu zwykłego z projektu (krawędź `--border`, tekst `--text-h`, wysokość `--control-height`, wskazanie `--surface-2`, wyłączony z kryciem z `prototype.css`), więc jest jeden wygląd przycisku zwykłego.
- `AppButton.test.ts` sprawdza obecność wymaganej klasy wariantu, atrybuty i zachowanie slotu przy `busy` bez `busyLabel`. Asercja klasy dopuszcza dodatkowe klasy stylu.

#### 3. Komunikat

**File**: `szczur-radar-web/src/components/StatusMessage.vue`, `StatusMessage.test.ts`

**Intent**: Komunikat ma ikonę tonu i ton „uwaga” (`ui-design.md:460`, `prototype.css:1490-1518`).

**Contract**:
- `tone: 'ok' | 'error' | 'warning'`. Role: `ok` → `status`, `error` → `alert`, `warning` → `status`.
- Ikona dla każdego tonu: `i-circle-check`, `i-circle-alert`, `i-triangle-alert`.
- Korzeń zachowuje rolę, klasy komponentu i domyślne przekazywanie klas odbiorcy. W środku są ikona i akapit z treścią. Korzeń i akapit mają `margin: 0`. Tło to `--*-soft`, tekst ma kolor `--text-h`, a lewa kreska znika.
- Odstępy określa odbiorca, zgodnie z przeglądem widoków w fazie 3. W katalogu sekcja „Komunikat” układa próbki w stosie z `gap: var(--space-3)`.
- Test sprawdza role, ikonę i treść, bez `tagName`.

#### 4. Znacznik stanu

**File**: `szczur-radar-web/src/components/StatusBadge.vue`, `StatusBadge.test.ts`

**Intent**: Znacznik ma wygląd z arkusza (`prototype.css:490-538`). Tony i ich nazwy się nie zmieniają.

**Contract**:
- `BadgeTone` bez zmian. Tony mapują się na pary kolor i tło:
  - `success` → `--success`/`--success-soft`;
  - `warning` → `--warning`/`--warning-soft`;
  - `destructive` → `--destructive`/`--destructive-soft`;
  - `neutral` → `--stance-neutral`/`--neutral-soft`;
  - `info` → `--accent`/`--selected`.
- Opcjonalny `icon?: boolean`: ikona tonu (`warning` → `i-triangle-alert`, `destructive` → `i-circle-alert`). Używa go tylko katalog.
- `data-tone` i klasa `status-badge` zostają, bo testy widoków po nich szukają (`AdminView.test.ts:730-788`, `RunDetailView.test.ts:198-428` i inne).

#### 5. Stanowisko i cel ze stanowiskiem

**File**: `szczur-radar-web/src/components/StanceText.vue`, `StanceText.test.ts`

**Intent**: Stanowisko to znak, słowo i kolor w znaczniku z tłem dla sześciu kierunków. Wariant z nazwą celu (i rodzajem) daje „Cel ze stanowiskiem” (`ui-design.md:452-454`, `prototype.css:512-585`).

**Contract**:

| Kierunek | Znak | Kolor | Tło |
| --- | --- | --- | --- |
| `bullish` | `s-up` | `--success` | `--success-soft` |
| `bearish` | `s-down` | `--destructive` | `--destructive-soft` |
| `neutral` | `s-flat` | `--stance-neutral` | `--neutral-soft` |
| `mixed` | `s-mixed` | `--stance-mixed` | `--mixed-soft` |
| `not_expressed`, `unclear` | `s-none` | `--text-muted` | `--neutral-soft` |

- Słowa z `labels.ts` bez zmian.
- Dopisek to dosłowny tekst „(ostrożnie)” w DOM. Tekst stanowiska z dopiskiem zostaje „wzrostowo (ostrożnie)”.
- Klasy kierunków i prop `tag` zostają.
- Dochodzą opcjonalne `target?: string` i `kind?: string`. Nazwa celu stoi pierwsza (`--text-h`, `--weight-semibold`, skraca się wielokropkiem, stanowisko się nie skraca), a rodzaj po niej (`--font-size-xs`).
- Test sprawdza znak dla każdego kierunku, tekst z dopiskiem i wariant z celem.

#### 6. Motyw i dzwonek

**File**: `szczur-radar-web/src/components/ThemeToggle.vue`, `ThemeToggle.test.ts`, `AppHeader.test.ts`, `NotificationMenu.vue`

**Intent**:
- Przełącznik motywu to przycisk z samą ikoną `i-moon` z nazwą „Ciemny motyw” (`ui-design.md:446`).
- Dzwonek rysuje ikonę `i-bell` ze sprite'a zamiast ręcznie narysowanej ścieżki.
- Zachowanie obu się nie zmienia.

**Contract**:
- `ThemeToggle` to `AppButton` w trybie samej ikony z `aria-label="Ciemny motyw"` i `aria-pressed`.
- Testy sprawdzają nazwę dostępną (`aria-label`), a nie `text()`. W `AppHeader.test.ts:49,55` dotyczy to listy przycisków.
- Dzwonek w `NotificationMenu.vue:109-132` używa `AppIcon name="i-bell"`. Nazwa „Powiadomienia, N nieprzeczytanych”, licznik i `aria-expanded` bez zmian.

#### 7. Pola i odnośnik

**File**: `szczur-radar-web/src/style.css`, formularz w `szczur-radar-web/src/views/LoginView.vue`

**Intent**: Jeden globalny wygląd pól i odnośnika z arkusza (`prototype.css:232-255, 329-442`).

**Contract**:
- **Pola tekstowe:**
  - Reguła obejmuje `input[type='text' | 'email' | 'password' | 'search' | 'date']` i `select`.
  - Formularz w `LoginView.vue` dostaje klasę `login-form` obok obecnej klasy `form`. Nowe globalne selektory pól i ich stanów wyłączają potomków formularza przez `:where(:not(.login-form *))`. Do S-49 pola zachowują własny styl i wysokość wynikającą z treści oraz paddingu. Nadal dziedziczą krój i korzystają z globalnego fokusu.
  - Wysokość `--control-height` zastępuje `calc(1.45rem + 14px)`, wewnętrzne odstępy `0 var(--space-3)`.
  - Krawędź `--border`, tło `--surface`, tekst `--text-h`.
  - Wskazanie zmienia krawędź na `--text`. Warunek `:where(:hover:not(:disabled))` zachowuje specyficzność selektora bazowego. `[aria-invalid='true']` ma pierwszeństwo i zachowuje krawędź `--destructive` oraz cień wewnętrzny również pod kursorem. Wyłączone pole ma tło `--surface-2`. Tekst zastępczy ma kolor `--text-muted`.
  - Kontrolki z innym `type` nie są objęte, a lista typów w regule jest jawna.
- **Pole wyboru:** checkbox ma rozmiar z arkusza i `accent-color: var(--accent)`.
- **Odnośnik:** globalna reguła `a` daje kolor `--accent`, stałe podkreślenie z odsunięciem i grubsze podkreślenie przy wskazaniu. `text-underline-offset` w `em` nie jest skanowane. Reguły widoków, które zdejmują podkreślenie, rozstrzyga faza 3.
- Dla nowych i migrowanych pól obowiązuje globalny styl z `style.css`. Wyjątek `LoginView.vue` wygasa przy migracji w S-49.

#### 8. Katalog komponentów i cele wizualne

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`, `szczur-radar-web/visual/targets.ts`

**Intent**:
- Każdy komponent F-08 ma w katalogu sekcję pod nazwą z projektu, ze wszystkimi stanami z arkusza (tabela stanów `ui-design.md:396-423`).
- Hover i fokus kontrolek z tabeli `STATES` mają wzorce w obu motywach przy 375 px.

**Contract**:
- **Sekcje (nagłówki 2 w tej kolejności):**
  - „Tokeny”;
  - „Przycisk”: główny, zwykły, z ikoną, duży, sama ikona, w toku, wyłączony;
  - „Odnośnik”: wewnętrzny i zewnętrzny z ikoną;
  - „Pola”: e-mail wypełniony („E-mail”), pusty i wyłączony, hasło z błędem, powtórzenie hasła z podpowiedzią, wyszukiwanie puste („Puste”) i wypełnione, duże wyszukiwanie z ikoną i tekstem zastępczym, data, lista wyboru aktywna i wyłączona oraz checkbox zaznaczony, niezaznaczony i wyłączony;
  - „Komunikat”: trzy tony;
  - „Znacznik stanu”: pięć tonów, z ikoną i bez;
  - „Stanowisko”: sześć kierunków bez dopisku oraz dodatkowy przykład „(ostrożnie)” dla każdego z trzech kierunków `bullish`, `bearish` i `neutral`. Dla tych trzech kierunków próbka bez dopisku ma `assertiveness: 'unhedged'`, a z dopiskiem `'hedged'`. Próbki `mixed`, `unclear` i `not_expressed` mają `assertiveness: null`, zgodnie z [kontraktem oceny §7.2](../../foundation/llm-assessment/contract-v2.md);
  - „Cel ze stanowiskiem”;
  - „Motyw”: jeden `ThemeToggle` o nazwie „Ciemny motyw”. W świeżym kontekście projektu jasnego ma `aria-pressed="false"`, a ciemnego `aria-pressed="true"`;
  - „Ikony”: katalog iteruje po `ICON_NAMES` i pokazuje dla każdej nazwy symbol z `AppIcon` oraz jego nazwę;
  - dalej bez zmiany nazw: „Dzwoneczek”, „Filtr”, „Posty”, „Ocena posta”.
- **Nazwy kontrolek stanów są jednoznaczne w swoim regionie.** Lokatory w `STATES` nie używają `nth`.
- **Błąd i podpowiedź w sekcji „Pola”.** Pole hasła ma `aria-invalid="true"` i wskazuje przez `aria-describedby` widoczny komunikat pod polem z ikoną `i-circle-alert`. Pole powtórzenia hasła wiąże w ten sam sposób własną podpowiedź. Każde pole ma osobną etykietę lub nazwę dostępną.
- Duże wyszukiwanie używa `--control-height-lg` i ma nazwę dostępną „Szukaj w postach”.
- **`STATES`:**
  - stany przycisku głównego i zwykłego przechodzą pod region „Przycisk” jako `state-przycisk-*`;
  - dochodzą hover i fokus przycisku z samą ikoną (`state-przycisk-icon-*`) oraz hover i fokus odnośnika (`state-odnosnik-*`);
  - w regionie „Pola” dochodzą `state-pola-email-hover` i `state-pola-email-focus` dla pola `textbox` o nazwie „E-mail”. `state-pola-search-focus` zostaje dla pola `searchbox` o nazwie „Puste”;
  - w regionie „Motyw” dochodzą `state-motyw-hover` i `state-motyw-focus` dla przycisku „Ciemny motyw”. Oba projekty wąskie potwierdzają przed zrzutem `aria-pressed` właściwe dla motywu.
- Odnośnik zewnętrzny w katalogu ma nazwę dostępną „… (otwiera się w nowej karcie)”, zgodnie z `ui-design.md:436`.
- axe katalogu przechodzi (`visual/kitchen-sink.spec.ts`).

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym strażnik zbioru ikon, warianty komponentów i `CLEANED` z nowymi komponentami: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy E2E przechodzą bez zmian w specach (nazwy dostępne bez zmian): `cd szczur-radar-web && npm run e2e`
- Celowe złamanie: symbol usunięty z kopii sprite'a czerwieni `AppIcon.test.ts`; po przywróceniu test jest zielony

#### Manual Verification:

- (brak — wygląd ocenia właściciel w fazie 3 na wzorcach)

---

## Faza 3: Widoki w nowym wyglądzie i wzorce F-06 od nowa

### Overview

Widoki stosują reguły projektu bez zmiany układu. Wszystkie wzorce F-06 powstają od nowa. Właściciel akceptuje wygląd na obrazkach, a CI jest zielone.

### Changes Required:

#### 1. Przegląd widoków pod regułami projektu

**File**: `szczur-radar-web/src/views/*.vue`, `szczur-radar-web/src/components/{AppHeader,PostCard,PostAssessment,FeedFilter,NotificationMenu}.vue`, `szczur-radar-web/src/style.css`

**Intent**: Obecne widoki i komponenty, których F-08 nie przebudowuje, wyglądają jak projekt w zakresie, który nie zmienia układu (`ui-design.md:185-339`).

**Contract**:
- **Kroje:**
  - nagłówki, tytuły postów, treść posta i cytat w ocenie mają `--font-display` z wagą i światłem z `ui-design.md:276`;
  - surowy JSON ma `--font-mono`;
  - tekst interfejsu zostaje `--font-ui`.
- **Liczbowe wartości pisma i stanów na tokenach:**
  - `font-weight: 600` → `--weight-semibold` (albo `--weight-medium`, jak mówi tabela pisma);
  - `line-height: 1.45` → `--leading-*`;
  - `letter-spacing: normal` usunięte;
  - wskazanie przez `color-mix(…)` → `--surface-2` albo `--selected`, według zasad z `ui-design.md:228-233`.
- **`.eyebrow`:** bez wersalików i bez światła między literami (`ui-design.md:278, 501`).
- **Odnośniki:**
  - odnośniki w tekście (powrót „Posty”, „Panel admina”, linki przebiegów) idą za regułą globalną;
  - odnośniki będące elementem nawigacji albo tytułem karty (`AppHeader`, tytuł w `PostCard`) zachowują własny styl.
- **Odstępy przy `StatusMessage`:**
  - W blokowych gałęziach błędu `FeedView`, `PostDetailView` (post i surowe dane), `RunDetailView`, `ActivateView` (błąd sprawdzania linku) i `NotificationMenu` (błąd listy) odbiorca nadaje komunikatowi lokalną klasę z `margin-block: var(--space-3)`. To zachowuje odstęp od „Spróbuj ponownie” oraz od treści nad komunikatem.
  - Ten sam odstęp ustaw lokalnie na samodzielnych komunikatach w sekcjach `AdminView` i przy błędzie oznaczania w `NotificationMenu`. W `RunDetailView` użyj istniejących klas `.attention` i `.refresh-error`, a w `AppHeader` zapewnij odstęp w kontenerze `.notice`.
  - Formularze logowania i aktywacji oraz `fieldset.sources` w panelu admina korzystają z istniejącego `gap`.
- **Pola w `ActivateView`:** własny styl pól znika na rzecz reguły globalnej.
- **Układ** (siatki, kolejność, szerokości, punkty przełamania) bez zmian. Obowiązują limity szerokości i wymiary kolumny z tabeli w fazie 1.
- **`LoginView.vue`:** kolory i krój dziedziczone z tokenów. Pola korzystają z wyjątku `login-form` z fazy 2.

#### 2. Wzorce F-06 od nowa

**File**: `szczur-radar-web/visual/__screenshots__/**` (wszystkie)

**Intent**: Wzorce opisują nowy wygląd. Pliki sekcji katalogu i stanów mają nowe nazwy, a osierocone stare pliki znikają.

**Contract**:
- `npm run visual:update` dwa razy. Pierwszy przebieg z nowymi celami jest czerwony raz (Critical Implementation Details).
- Pliki nieistniejących już sekcji i stanów są usunięte: `kitchen-sink-przyciski`, `-komunikaty`, `-znaczniki`, `state-przyciski-*`.
- `baselines.spec.ts` jest zielony w obie strony.
- Dwa kolejne `npm run visual:docker` bez zmian w kodzie są zielone, co dowodzi powtarzalności zrzutów z krojami.

#### 3. Akceptacja wyglądu i PR

**File**: opis PR, galeria jako Artifact (poza repozytorium)

**Intent**: Właściciel ocenia nowy wygląd na obrazkach, obok prototypów (pamięć `ui-decisions-by-pictures`). Opis PR mówi, które grupy wzorców się zmieniły i dlaczego (`REPOSITORY_GUIDELINES.md`, „Visual check”).

**Contract**:
- Galeria pokazuje każdy widok w czterech projektach obok najbliższego zrzutu z `context/foundation/ui-design/screens/`, a sekcje katalogu obok `components-*-375.png`. Podpis przy widokach mówi, że układ zmieniają dopiero S-47–S-59.
- Opis PR wymienia grupy: wszystkie `view-*` (kolory, kroje, rozmiary), `kitchen-sink-*` (nowe nazwy sekcji, nowe komponenty), `state-*` (nowe kontrolki).

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym skan `CLEANED`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą na nowych wzorcach: `cd szczur-radar-web && npm run visual:docker`
  W ramach tego kryterium, przed zatwierdzeniem wzorców, agent mierzy w Chromium przy 1024 i 1280 px wymiary z tabeli fazy 1. Korzysta z tych samych widoków i trybów mocka co `visual/targets.ts` oraz z otwartego panelu powiadomień. Porównuje obliczone `max-width` widoków, szerokość pierwszej kolumny karty i szerokość panelu przy dostępnej przestrzeni większej od limitu.
  Na ekranie aktywacji agent wywołuje błąd pola „Nowe hasło” przez wysłanie zbyt krótkiego hasła. Odczytuje w Chromium obliczony kolor krawędzi i cień przed oraz po najechaniu na pole z `aria-invalid="true"`. W obu stanach krawędź i cień mają kolor `--destructive`.
  W feedzie (`AUTH: 'user', MODE: 'error'`) oraz w otwartym panelu powiadomień (`AUTH: 'admin', NOTIFICATIONS: 'error'`) agent po załadowaniu krojów mierzy przy 375 px odstęp między dolną krawędzią alertu a górną krawędzią przycisku „Spróbuj ponownie”. Odstęp wynosi co najmniej `--space-3`.
- Drugi przebieg bez zmian w kodzie jest zielony (zrzuty powtarzalne): `cd szczur-radar-web && npm run visual:docker`
- CI na PR jest zielone, w tym `web-e2e` (krok wizualny) i `web-container-smoke`: `gh pr checks`
- W środowisku PR strona logowania i feed rysują tekst krojami aplikacji, a użyte pliki `woff2` przychodzą jako `font/woff2`. Po wdrożeniu sprawdzanego commita agent wykonuje kontrolę w Chromium przez `playwright-cli`:
  1. W sesji anonimowej otwiera `/login` i czeka na widoczność „Logowanie” oraz „Hasło”. Po `document.fonts.ready` sprawdza przez `CSS.getPlatformFontsForNode` jak w `visual/font.spec.ts`, że nagłówek rysuje Newsreader, a etykietę Hanken Grotesk, bez glifów z kroju zastępczego.
  2. W środowisku tego PR tworzy jednorazowe konto z `create-user --role user`, aktywuje je i uzyskuje ciasteczko sesji według [procedury w lessons.md](../../foundation/lessons.md). Link aktywacyjny, token i hasło pozostają w zmiennych powłoki. Stan przeglądarki z ciasteczkiem trafia do pliku tymczasowego poza repozytorium, usuwanego po kontroli, jak w [weryfikacji S-36](../../archive/2026-11-12-statement-language-quality/evidence/pr-env/README.md). Do dowodów trafiają wyłącznie wyniki pomiarów.
  3. Wczytuje sesję przez `playwright-cli state-load`, otwiera `/` i potwierdza adres oraz widoczność nagłówka „Posty” i etykiety „Spółka lub temat” w `main`. Po `document.fonts.ready` potwierdza przez CDP odpowiednio Newsreader i Hanken Grotesk, także przy pustym feedzie.
  4. W obu przejściach sprawdza statusy odpowiedzi i typ `Content-Type` żądań o załadowane pliki `woff2`, w tym podzbiór Hanken Grotesk z polskimi znakami użyty w „Hasło”.

#### Manual Verification:

- Właściciel akceptuje nowy wygląd widoków i katalogu w galerii zrzutów obok prototypów (jasny i ciemny, 375 i 1280 px)

---

## Faza 4: Strażnik zgodności i dokumentacja

### Overview

Jedno polecenie wykrywa rozjazd projektu i aplikacji. Dokumentacja opisuje stan po F-08, a roadmapa ma zadanie na test w CI przed zamknięciem M-5.

### Changes Required:

#### 1. Polecenie `design-check app`

**File**: `tools/design-check/design-check.mjs`, `tools/design-check/fixtures/` (nowe próbki `app`), usunięty `tools/design-check/fixtures/app-pairs.json`

**Intent**: Po F-08 źródłem prawdy w aplikacji jest `style.css`, a `ui-design/tokens.css` jest zapisem projektu (`ui-design.md:707`). Polecenie wykrywa każdy rozjazd tych kopii. Zastępuje `fixtures/app-pairs.json`, bo pary aplikacji są teraz parami projektu.

**Contract**:
- `node tools/design-check/design-check.mjs app [--design <ui-design dir>] [--web <web module dir>]`, domyślnie `context/foundation/ui-design` i `szczur-radar-web`.
- Naruszenia jednego rodzaju `app`, każde w formacie `VIOLATION [app] <file> <theme> -: <message>`:
  - własności `--*` obu bloków `tokens.css` i `src/style.css` różnią się nazwą albo wartością, w obie strony, z nazwą roli i obiema wartościami;
  - `pairs.json` i `src/contrastPairs.json` różnią się;
  - `icons/sprite.svg` i `src/assets/icons/sprite.svg` różnią się;
  - pliki `woff2` i licencje w `fonts/` i `public/fonts/` różnią się (brak albo inne bajty).
- Po porównaniu uruchamia `contrast` na `src/style.css` z `src/contrastPairs.json`.
- Kody wyjścia jak reszta narzędzia.
- `selftest` dostaje zielony przypadek i po jednej próbce dla każdego rodzaju rozjazdu (token, para, sprite, krój), każdą z komunikatem.

#### 2. Dokumentacja

**File**: `tools/design-check/README.md`, `REPOSITORY_GUIDELINES.md`, `context/foundation/ui-design.md`

**Intent**: Opisy repozytorium mówią, jak jest po F-08, żeby kolejny agent używał tokenów, krojów, ikon i komponentów, a nie przepisywał ich w widoku.

**Contract**:
- **`tools/design-check/README.md`:** wiersz polecenia `app`, wiersz selftestu z nowymi próbkami. Zamiast wiersza `fixtures/app-pairs.json` jest informacja, że `app` sprawdza parser na pliku aplikacji.
- **`REPOSITORY_GUIDELINES.md`, sekcja UI:**
  - zdanie „F-08 moves the tokens… until then” opisuje stan po F-08;
  - tokeny pochodzą z `ui-design/tokens.css`, `--line` to linia, a `--border` to krawędź kontrolki;
  - kroje leżą w `public/fonts/` z `@font-face` w `style.css` i preloadem;
  - pary kontrastu to `src/contrastPairs.json` z progiem dla każdej pary, zamiast „≥4.5:1”;
  - komponenty: `AppIcon` i `IconSprite` oraz nowe warianty `AppButton`, `StatusMessage`, `StatusBadge`, `StanceText`;
  - zdanie o palecie z `light-dark-theme-switch` zastępuje odwołanie do `ui-design.md`.
- **`REPOSITORY_GUIDELINES.md`, opis F-06:**
  - alias `visual/fonts.conf` to tylko krój zastępczy;
  - `visual/font.spec.ts` pilnuje krojów aplikacji;
  - zrzuty powstają po `document.fonts.ready`.
- **`REPOSITORY_GUIDELINES.md`, opis `tools/design-check/`:** dochodzi polecenie `app`.
- **`ui-design.md`:**
  - akapit pod „Zmiany bramek…” (`:707`) mówi, że zgodność sprawdza `design-check app`, uruchamiane przy każdej zmianie projektu;
  - reszta dokumentu bez zmian;
  - `node tools/design-check/design-check.mjs doc --stage 4` zostaje zielony.

#### 3. Zadanie w roadmapie

**File**: `context/foundation/roadmap.md` (sekcja `## Parked`, nowa pozycja na końcu listy)

**Intent**: Decyzja właściciela: test zgodności projektu i aplikacji w CI rozważyć przed zamknięciem M-5, gdy będzie widać, jak często projekt się zmienia.

**Contract**: Jedna pozycja w formacie sąsiednich:

> **Test zgodności projektu i aplikacji w CI (TODO przed zamknięciem M-5)** — Why parked: …

Treść pozycji:
- dziś `design-check app` jest ręczny (F-08);
- warunek powrotu: przed zamknięciem M-5 ocenić, ile zmian projektu w S-47–S-60 rozjechało kopie;
- pytanie otwarte: jak test w CI ma czytać `context/` mimo budowania obrazu web z samego `szczur-radar-web/`.

Bez innych edycji roadmapy: status F-08 zmieniają skille.

### Success Criteria:

#### Automated Verification:

- Strażnik zgodności przechodzi na repozytorium: `node tools/design-check/design-check.mjs app` kończy się kodem 0
- Selftest wykrywa każdy rodzaj rozjazdu i przechodzi na zielonej próbce: `node tools/design-check/design-check.mjs selftest` kończy się kodem 0
- Celowe złamanie: zmieniona wartość jednej roli w `szczur-radar-web/src/style.css` daje `VIOLATION [app]` z nazwą roli i kod 1; po przywróceniu kod 0
- Dokument projektu przechodzi kontrolę: `node tools/design-check/design-check.mjs doc --stage 4`
- `fixtures/app-pairs.json` jest usunięty, a nic się do niego nie odwołuje: `grep -rn "app-pairs" tools REPOSITORY_GUIDELINES.md` nic nie zwraca
- Testy jednostkowe i build dalej przechodzą: `cd szczur-radar-web && npm test && npm run build`

#### Manual Verification:

- (brak)

---

## Testing Strategy

### Unit Tests:

- **`styleContract.test.ts`:** 73 pary × 2 motywy z progiem dla każdej pary; brak `prefers-color-scheme`; skan `CLEANED` z `AppIcon` i `IconSprite`.
- **`AppIcon.test.ts`:** lista `ICON_NAMES` zgodna z identyfikatorami w spricie; `aria-hidden` i `focusable`.
- **`AppButton.test.ts`:** warianty (główny, zwykły, z ikoną, sama ikona z obowiązkową nazwą, duży); w toku ze znakiem pracy, `aria-busy` i blokadą kliknięć; `aria-pressed` na samej ikonie.
- **`StatusMessage.test.ts`:** trzy tony z rolą i ikoną.
- **`StatusBadge.test.ts`:** tony, `data-tone`, opcjonalna ikona.
- **`StanceText.test.ts`:** znak dla sześciu kierunków, „(ostrożnie)”, wariant z celem i rodzajem.
- **`ThemeToggle.test.ts` i `AppHeader.test.ts`:** nazwa dostępna „Ciemny motyw” i `aria-pressed`.
- **Testy widoków:** szukają po `data-tone`, `.status-badge` i klasach kierunków, więc zostają zielone bez zmian.

### Integration Tests:

- **E2E (`npm run e2e`)** po każdej fazie bez zmian w specach. Nazwy dostępne są kontraktem (`ui-design.md:508`).
- **Testy wizualne i axe (`npm run visual:docker`)** w fazie 3: widoki w czterech projektach, sekcje katalogu i stany przy 375 px, strażnik kroju i strażnik wzorców.
- **Smoke kontenera:** krój podawany jako `font/woff2`.

### Manual Testing Steps:

1. Właściciel ogląda galerię nowych zrzutów obok prototypów i akceptuje wygląd (faza 3).

## Performance Considerations

- Kroje to razem około 200 kB w sześciu plikach (budżet 300 kB, `ui-design.md:260`). Przeglądarka pobiera tylko podzbiory, których tekst używa: JetBrains Mono dopiero przy surowych danych, latin-ext przy polskich znakach.
- Preload obejmuje tylko dwa pliki łacińskie, potrzebne na każdym ekranie.
- Sprite (5,6 kB) jest w paczce JS jako tekst, bez osobnego żądania.

## Migration Notes

- Brak danych i API. Wycofanie to cofnięcie commitów PR-a. Wzorce wracają razem z kodem.
- Wycinki S-47+ zaczynają od tokenów i komponentów F-08. Nowy komponent trafia do `src/components/`, na `/kitchen-sink` i do `CLEANED`.

## References

- Projekt: `context/foundation/ui-design.md` („Język wizualny”, „Komponenty”, „Wdrożenie”), zasoby w `context/foundation/ui-design/` (`tokens.css`, `pairs.json`, `fonts/`, `icons/`, `prototypes/components.html`, `prototypes/prototype.css`, `screens/`)
- Roadmapa: `context/foundation/roadmap.md`, blok F-08
- Mechanizm motywu: `context/archive/2026-11-17-light-dark-theme-switch/`
- Zrzuty i alias kroju: `context/archive/2026-11-16-visual-regression/` (`plan-brief.md`, `visual-breaks.md`)
- Projekt UI/UX: `context/archive/2026-11-19-ui-design-system/` (`research.md:53-63,142` o bramce kroju i serwowaniu)
- Bramki: `szczur-radar-web/src/styleContract.test.ts`, `szczur-radar-web/visual/{font.spec.ts,fonts.conf,targets.ts,baselines.spec.ts}`, `.github/workflows/ci.yml` (`web-e2e`, `web-container-smoke`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Tokeny i kroje

#### Automated

- [x] 1.1 Testy jednostkowe przechodzą, w tym 73 pary × 2 motywy i skan `CLEANED` — 2813926
- [x] 1.2 Typecheck i build przechodzą — 2813926
- [x] 1.3 Testy E2E przechodzą bez zmian w specach — 2813926
- [x] 1.4 W `szczur-radar-web/src/` nie ma starych nazw (`--border-strong`, `--sans`, `--mono`) — 2813926
- [x] 1.5 Parser `design-check` czyta nowy `style.css` z parami aplikacji — 2813926
- [x] 1.6 Strażnik kroju przechodzi w obrazie Playwright — 2813926
- [x] 1.7 Smoke kontenera z nowym krokiem przechodzi lokalnie — 2813926
- [x] 1.8 Celowe złamanie: usunięty plik kroju czerwieni smoke i `font.spec.ts` — 2813926

### Phase 2: Ikony i komponenty bazowe

#### Automated

- [x] 2.1 Testy jednostkowe przechodzą, w tym strażnik zbioru ikon, warianty komponentów i `CLEANED` — e16cfd4
- [x] 2.2 Typecheck i build przechodzą — e16cfd4
- [x] 2.3 Testy E2E przechodzą bez zmian w specach — e16cfd4
- [x] 2.4 Celowe złamanie: symbol usunięty z kopii sprite'a czerwieni `AppIcon.test.ts` — e16cfd4

### Phase 3: Widoki w nowym wyglądzie i wzorce F-06 od nowa

#### Automated

- [x] 3.1 Testy jednostkowe przechodzą, w tym skan `CLEANED`
- [x] 3.2 Typecheck i build przechodzą
- [x] 3.3 Testy E2E przechodzą
- [x] 3.4 Testy wizualne i axe przechodzą na nowych wzorcach
- [x] 3.5 Drugi przebieg testów wizualnych bez zmian w kodzie jest zielony
- [ ] 3.6 CI na PR jest zielone, w tym `web-e2e` i `web-container-smoke`
- [ ] 3.7 W środowisku PR tekst rysują kroje aplikacji, a pliki `woff2` przychodzą jako `font/woff2`

#### Manual

- [ ] 3.8 Właściciel akceptuje nowy wygląd widoków i katalogu w galerii zrzutów obok prototypów

### Phase 4: Strażnik zgodności i dokumentacja

#### Automated

- [x] 4.1 Strażnik zgodności `design-check app` przechodzi na repozytorium
- [x] 4.2 Selftest wykrywa każdy rodzaj rozjazdu i przechodzi na zielonej próbce
- [x] 4.3 Celowe złamanie: zmieniona rola w `style.css` daje `VIOLATION [app]`
- [x] 4.4 Dokument projektu przechodzi `design-check doc --stage 4`
- [x] 4.5 `fixtures/app-pairs.json` usunięty i bez odwołań
- [x] 4.6 Testy jednostkowe i build dalej przechodzą
