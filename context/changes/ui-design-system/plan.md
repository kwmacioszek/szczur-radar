# F-07: Projekt UI/UX całej aplikacji (ui-design-system) Implementation Plan

## Overview

Aplikacja dostaje projekt UI/UX opracowany od nowa według standardów (WCAG 2.2 AA, heurystyki Nielsena, wzorce dojrzałych systemów projektowych), zapisany w `context/foundation/ui-design.md` razem z tokenami, arkuszem komponentów i prototypami każdego ekranu M-5. Standardy dobiera i uzasadnia projektant (agent), bo właściciel nie zna frontendu; właściciel ocenia wynik na obrazkach: wybiera jeden z trzech wariantów wyglądu, a na końcu akceptuje komplet ekranów. Zmiana nie dotyka kodu aplikacji; wdrożenie to F-08 i wycinki S-47–S-59.

## Current State Analysis

- Makiety w `docs/visuals/ui-ux-proposals/` to cztery różne języki wizualne (ciemna stacja robocza, papierowy czytnik z szeryfem, turkusowa karta, zielony przegląd), każda z innym logo i nawigacją. Właściciel potwierdził (wywiad 2026-11-19), że to tylko sugestia funkcji, a wygląd ma powstać od zera.
- SPA ma 7 tras, 11 komponentów i jeden plik tokenów `szczur-radar-web/src/style.css` (12 ról kolorów w palecie A z S-46, 5 rozmiarów tekstu, 6 odstępów, 2 promienie; bez cieni, wag, interlinii i breakpointów). Krój to `system-ui`, brak ikon poza dzwonkiem, brak animacji (`research.md`, sekcja 1).
- Bramy F-05/F-06/S-46 ograniczają to, co da się wdrożyć: role tekstowe jako hex z kontrastem ≥4,5:1 w dwóch motywach, zakaz `prefers-color-scheme` w CSS, 56 wzorców z tolerancją 0 pikseli, strażnik fontu `Liberation Sans`, E2E z jednym `banner` i `main` oraz konkretnymi etykietami (`research.md`, sekcja 2).
- Część ekranów M-5 nie ma jeszcze API (katalog celów, proporcje, przegląd dnia, filtr dat, wyszukiwanie w treści, sortowanie, ukrywanie nieinwestycyjnych). PRD v4 i decyzje z 2026-11-15 zabraniają tickerów, scalania odmian, trendu, jednej etykiety stanowiska na wpis, „Zapisanych” i statusu pipeline'u dla zwykłego użytkownika (`research.md`, sekcja 4).
- W `context/` nie ma żadnego dokumentu o standardach projektowych; `context/foundation/ui-design.md` nie istnieje.
- Bolączki właściciela (wywiad): surowy wygląd, trudno wyłapać sedno wpisu, za dużo klikania między listą a wpisem, nieczytelny panel admina. Korzysta głównie z komputera. Chce zaczynać od przeglądu dnia.

## Desired End State

`context/foundation/ui-design.md` opisuje zasady i standardy z ich zastosowaniem, zadania użytkownika, architekturę informacji z nawigacją, język wizualny, komponenty ze stanami, treść etykiet, kontrakt dla testów E2E, przekazanie do F-08 i różnice wobec roadmapy. Obok, w `context/foundation/ui-design/`, leżą tokeny w formacie `style.css`, pliki kroju z licencją, ikony, arkusz komponentów i prototypy HTML każdego ekranu ze zrzutami w obu motywach przy 1280 i 375 px. Właściciel wybrał wariant i zaakceptował komplet, a decyzje są zapisane w plikach. Kod w `szczur-radar-web/` i `szczur-radar-api/` jest bez zmian.

Weryfikacja: skrypt `design-check.mjs` przechodzi (kontrast, axe, brak przewijania w poziomie, rozmiar celów, komplet plików, pokrycie wymagań), przegląd heurystyczny nie ma otwartych ustaleń blokujących, CI w PR jest zielone, a `git diff` względem `origin/main` nie pokazuje zmian w modułach aplikacji.

### Key Discoveries:

- Wzorzec akceptacji na wariantach istnieje: `context/archive/2026-11-17-light-dark-theme-switch/palette/palette.md` (pliki wariantów, zrzuty, tabela kontrastu, sekcja „Decyzja właściciela”, zatrzymanie biegu do wyboru).
- Test kontrastu czyta pierwszy blok `:root` i dokładnie `:root[data-theme='dark']`, a role muszą być hexem 3 lub 6 cyfr (`szczur-radar-web/src/styleContract.test.ts:75-84`). Tokeny projektu w tym samym formacie przenoszą się do F-08 bez tłumaczenia.
- Brama dostępności w CI używa tagów `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa` (`szczur-radar-web/visual/axe.ts:7`). Ten sam silnik i tagi na prototypach dają wynik porównywalny z przyszłą bramą.
- Strażnik fontu sprawdza kroje przez CDP `CSS.getPlatformFontsForNode` na tekście z polską literą (`szczur-radar-web/visual/font.spec.ts:31-43`). Ta metoda nadaje się do sprawdzenia, że krój projektu rysuje polskie znaki bez kroju zastępczego.
- `font-synthesis: none` (`szczur-radar-web/src/style.css:58`) wymaga osobnego pliku dla każdej użytej wagi.
- Testy E2E szukają elementów po rolach i tekstach; link „Posty” występuje dziś w `banner` i w `main` (`szczur-radar-web/e2e/post.spec.ts:23`), więc zmiana nawigacji i etykiet decyduje o tym, które testy trzeba poprawić.
- Skill `design-taste-frontend` z repo wyklucza panele i tabele danych (jego nagłówek), więc nie jest źródłem zasad dla tej zmiany.
- W worktree nie ma `szczur-radar-web/node_modules`; kontrole stron wymagają `npm ci` i przeglądarki Chromium z Playwrighta.

## What We're NOT Doing

- Żadnych zmian w `szczur-radar-web/` i `szczur-radar-api/`: tokeny w `style.css`, komponenty, widoki, strażnik fontu i wzorce F-06 zmienia dopiero F-08.
- Testów E2E nowych ekranów: powstają w wycinkach S-47–S-59 razem z ekranami. Ta zmiana daje im kontrakt (role, etykiety, przepływy) i dopisuje wymóg w roadmapie.
- Testów, które klikają po prototypach: pilnowałyby makiet, nie aplikacji.
- Przecinania wycinków w roadmapie: zmiana spisuje różnice dla `/10x-roadmap` (v6.2) i dopisuje jedno zdanie o testach E2E; statusy i opisy wycinków zostają.
- Zmian wymagań: projekt pokrywa dokładnie PRD v4. Pomysły ponad PRD trafiają na listę propozycji i nie mają prototypów.
- Elementów makiet bez pokrycia w wymaganiach: „Analizy”, „Wątki”, „Ostatnie obserwacje”, strzałka trendu „↑”, „Zapisane”, status pipeline'u dla zwykłego użytkownika, tickery i nazwy kanoniczne, scalanie odmian celu, jedna etykieta stanowiska na wpis, „Warto przeczytać” jako ocena wartości. „Przejdź do źródła” zostaje wyłącznie jako link do oryginału wpisu (FR-010).
- Rozstrzygania niewiadomych wycinków, których właścicielem jest użytkownik (pola sortowania S-54, domyślny okres i próg katalogu S-57, progi S-60): prototyp pokazuje propozycję oznaczoną w dokumencie jako niewiążąca.
- Trzeciego motywu i zmiany mechanizmu motywu z S-46.
- Zewnętrznego CDN fontów i bibliotek UI.

## Implementation Approach

Projekt powstaje jako samodzielne strony HTML ze wspólnym CSS, poza aplikacją, i jest sprawdzany tym samym silnikiem dostępności co brama w CI. Fazy idą od najtańszej decyzji do najdroższej: najpierw trzy warianty wyglądu na dwóch ekranach i wybór właściciela, potem pełny język wizualny wybranego wariantu, potem wszystkie ekrany i akceptacja, na końcu przekazanie do wdrożenia. Trwały wynik trafia do `context/foundation/` (dokument i zasoby, z których korzystają F-08 i wycinki), narzędzie kontroli do `tools/design-check/`, a materiał roboczy (warianty, galeria, przegląd heurystyczny) do folderu zmiany.

Właściciel ogląda wyniki na prywatnej stronie z galerią (narzędzie Artifact), a te same obrazki są w repo. Bieg zatrzymuje się na dwóch bramkach właściciela: po fazie 1 i po fazie 3. Fazy uruchamia `/10x-implement` w sesji interaktywnej na modelu Fable (roadmapa v6.1); przy projektowaniu wariantów i komponentów implementujący ładuje skill `frontend-design`, a przed publikacją galerii `artifact-design`.

## Critical Implementation Details

- **Format tokenów jest kontraktem z F-08**: plik tokenów ma dokładnie dwa bloki, pierwszy `:root` (jasny) i `:root[data-theme='dark']`, role kolorów użyte w parach kontrastu są hexem 3 lub 6 cyfr, a w CSS nie ma `prefers-color-scheme`. Nazwy ról istniejących w `style.css` zostają, gdy znaczenie się nie zmienia (`--bg`, `--surface`, `--text`, `--text-h`, `--accent`, `--on-accent`, `--success`, `--warning`, `--destructive`, `--highlight`, `--stance-neutral`, `--stance-mixed`).
- **Repo jest publiczne**: prototypy nie zawierają prawdziwych nazw użytkowników ze źródeł; treść wpisów jest sparafrazowana z fixture'ów i atrapy. Link do prywatnej galerii trafia do właściciela w rozmowie i nie jest zapisywany w repo ani w issue.
- **Kolejność w fazie 1**: gałąź (z commitem roadmapy v6.1) i issue #196 już istnieją; PR powstaje przed pierwszym commitem fazy 1 (zasada repo); folder zmiany jest już na gałęzi w commicie planu. Status F-07 w roadmapie przechodzi wtedy na `in-progress`, więc dane w `tools/render_roadmap_svg.py` i `docs/visuals/roadmap.svg` idą w tym samym commicie.

## Phase 1: Podstawy i trzy warianty wyglądu

### Overview

Powstaje szkielet dokumentu projektu (zasady, zadania użytkownika, układ ekranów) i trzy wyraźnie różne warianty wyglądu pokazane na dwóch ekranach. Faza kończy się wyborem właściciela.

### Changes Required:

#### 1. Start zmiany w repo

**File**: gałąź `feature/ui-design-system`, issue #196, PR; `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Spełnić zasady pracy repo przed pierwszym commitem i pokazać w roadmapie, że F-07 jest w toku.

**Contract**: Gałąź `feature/ui-design-system` już istnieje na origin: `origin/main` (00cdc83) plus commit roadmapy v6.1 (0ed2537), który wprowadza F-07 i F-08 i wchodzi do `main` razem z tą zmianą. Implementujący robi `git fetch`; jeśli `origin/main` się przesunął, przenosi gałąź przez `git rebase origin/main` i pushuje z `--force-with-lease`, nigdy nie odtwarza gałęzi od `origin/main`. Issue #196 dostaje komentarz z change-id, gałęzią i linkiem do PR; opis PR zawiera `Refs #196` i wymienia commit v6.1. F-07 ma w roadmapie status `in-progress` (zastępuje niezacommitowane `planning` z `/10x-plan`), w skrypcie SVG `"progress"`, a SVG jest wygenerowane ponownie. Folder zmiany (badanie, plan, przegląd planu) jest już na gałęzi w commicie przed fazą 1.

#### 2. Narzędzie kontroli projektu

**File**: `tools/design-check/design-check.mjs` (nowy), `tools/design-check/README.md` (nowy), `tools/design-check/fixtures/` (nowy: `broken/` z próbkami złamań, `app-pairs.json` z 19 parami `PAIRS`)

**Intent**: Jedno polecenie na każdy rodzaj kontroli, żeby zgodność ze standardami sprawdzał agent poleceniem, a nie właściciel okiem. Narzędzie leży w `tools/`, a nie w folderze zmiany, bo żywy dokument projektu i każda jego późniejsza edycja potrzebują go pod stałą ścieżką także po `/10x-archive`. Jak `tools/ci-runner/` i `tools/render_roadmap_svg.py` nie ma osobnego zadania w CI: uruchamia je lokalnie agent zmieniający projekt.

**Contract**: Skrypt Node uruchamiany z katalogu repo, kod wyjścia 0 tylko przy braku naruszeń, wynik jako lista naruszeń z plikiem, motywem i szerokością. Podpolecenia:
- `contrast <tokens.css> <pairs.json>`: wzór luminancji WCAG 2.x jak w `styleContract.test.ts`; czyta te same dwa bloki; każda para ma próg (4,5 dla tekstu, 3,0 dla elementów interfejsu i stanów fokusu, WCAG 1.4.3 i 1.4.11); błąd dla roli spoza hex 3/6 i dla `prefers-color-scheme` w pliku.
- `pages <katalog>`: każdą stronę HTML otwiera w Chromium w motywie jasnym i ciemnym przy 1280, 375 i 320 px (320 px to próg reflow WCAG 1.4.10: tylko axe i brak przewijania w poziomie, bez zrzutów) i sprawdza: 0 naruszeń axe z tagami z `szczur-radar-web/visual/axe.ts:7`; brak przewijania w poziomie (`scrollWidth <= clientWidth`); dokładnie jeden `main` i najwyżej jeden `banner`; tekst z polskimi znakami rysowany wyłącznie zadeklarowanymi krojami (metoda z `visual/font.spec.ts`). Rozmiar celów (WCAG 2.5.8) sprawdza reguła axe `target-size` z tagu `wcag22aa`, z wyjątkami na odstęp i na linki w tekście, tak samo jak brama w CI; skrypt nie ma własnej kontroli rozmiaru.
- `shots <katalog>`: zapisuje zrzuty `<nazwa>-<motyw>-<szerokość>.png`; opcja `--scale` ustawia `deviceScaleFactor` (2 dla wariantów, 1 dla ekranów).
- `selftest`: uruchamia kontrole na celowo zepsutych próbkach w `tools/design-check/fixtures/broken/` (rola o kontraście poniżej progu, rola spoza hex 3/6, `prefers-color-scheme`, element szerszy niż 375 px, przycisk bez nazwy); każda próbka musi dać kod wyjścia różny od 0, a `selftest` kończy się kodem 0 tylko wtedy, gdy wykrył wszystkie.
- `inventory <plik-lub-katalog>`: komplet plików i zrzutów dla wariantów albo dla `screens.json`; opcja sprawdzająca, że galeria odwołuje się do każdego zrzutu.
- `doc`: wymagane nagłówki `ui-design.md`, pokrycie listy wymagań, poprawność linków względnych, brak linków do `context/changes/` (po archiwizacji przestałyby działać), obecność każdego ekranu w kontrakcie E2E.

Playwright i axe są ładowane z `szczur-radar-web/node_modules` (po `npm ci`) przez `createRequire(new URL('../../szczur-radar-web/package.json', import.meta.url))`: import bez ścieżki szuka pakietów w górę od folderu skryptu i do modułu web nie trafi, a `NODE_PATH` nie działa dla ESM. Skrypt bierze wyłącznie bezpośrednie zależności modułu: `chromium` z `@playwright/test` i `AxeBuilder` z `@axe-core/playwright` (CJS, bez `.default`). Bez nowych zależności i bez plików w module web. README podaje polecenia i wymagania (`npm ci`, `npx playwright install chromium`).

#### 3. Szkielet dokumentu projektu

**File**: `context/foundation/ui-design.md` (nowy)

**Intent**: Zapisać podstawy, z których wynikają warianty: komu i do czego służy aplikacja, jakie standardy obowiązują i jak są stosowane, jakie są ekrany i jak się między nimi przechodzi.

**Contract**: Nagłówki drugiego poziomu w tej kolejności (sprawdza je `doc`): „Cel i zakres”, „Użytkownik i zadania”, „Zasady i standardy”, „Architektura informacji i nawigacja”, „Ekrany”, „Język wizualny”, „Komponenty”, „Treść i etykiety”, „Kontrakt dla testów E2E”, „Wdrożenie”, „Różnice wobec roadmapy”, „Propozycje poza PRD”, „Decyzja właściciela”. W tej fazie wypełnione są pierwsze pięć:
- „Użytkownik i zadania”: korzystanie głównie z komputera; zadania w kolejności ważności; cztery bolączki z wywiadu, każda z odpowiedzią projektu.
- „Zasady i standardy”: dla każdego standardu źródło (nazwa i adres), reguły wybrane dla tej aplikacji i sposób sprawdzenia. Minimum: WCAG 2.2 AA (kontrast tekstu i elementów interfejsu, widoczny fokus, rozmiar celu (reguła axe `target-size`), reflow przy 320 px (`pages`), obsługa klawiaturą, nazwy dostępne), dziesięć heurystyk Nielsena jako lista kontrolna przeglądu, wzorce układu z nazwanych systemów projektowych (lista ze szczegółami obok, stała nawigacja, formularze, stany puste i błędów, szkielety ładowania), skala typografii i siatka odstępów, zasady ruchu z `prefers-reduced-motion`.
- „Architektura informacji i nawigacja”: mapa ekranów z rolami (użytkownik, admin, ekrany publiczne); ekranem startowym jest docelowo przegląd dnia, a do czasu jego wdrożenia (S-59) lista wpisów z oceną obok; stany przejściowe nawigacji po kolejnych wycinkach (które pozycje istnieją po S-47, S-57, S-59); podział panelu admina na czytelne części; układ pod komputer z panelami obok siebie i jedną kolumną przy 375 px.
- „Ekrany”: tabela ekran, cel, wymagania (FR), dane dostępne dziś albo wycinek, plik prototypu. Pokrywa listę: FR-007, FR-008, FR-009, FR-010, FR-011a, FR-011b, FR-012, FR-013, FR-015, FR-018, FR-021, FR-022, FR-030, FR-033, FR-034, FR-035, FR-036, FR-037, FR-038, FR-039, FR-040, oraz dni bez danych, stronę przebiegu, powiadomienia admina i wspólne stany (ładowanie, pusta lista, błąd z ponowieniem).

#### 4. Trzy warianty wyglądu

**File**: `context/changes/ui-design-system/directions/{a,b,c}/` (nowe: `tokens.css`, `pairs.json`, `direction.css`, `stream.html`, `overview.html`, zrzuty), `context/changes/ui-design-system/directions/directions.md` (nowy)

**Intent**: Dać właścicielowi wybór na obrazkach między trzema spójnymi kierunkami, zanim powstanie cały system.

**Contract**:
- Każdy wariant to kompletna propozycja: kroje pisma (najwyżej dwie rodziny na otwartej licencji, z polskimi znakami, pliki lokalne), skala typografii, gęstość, kształt powierzchni (linie, karty, panele), użycie koloru w obu motywach, styl ikon. Warianty różnią się wyraźnie na co najmniej trzech z tych osi. Jeden wariant zachowuje paletę A z S-46 bez zmiany wartości, żeby właściciel mógł porównać z własnym wyborem; pozostałe mogą ją stroić albo zmienić.
- Każdy wariant pokazuje te same dwa ekrany z tą samą treścią: lista wpisów z oceną wybranego wpisu obok (`stream.html`) i przegląd dnia (`overview.html`). Treść spełnia PRD: stanowisko per cel, cytaty, dwa źródła ze słowami typu posta z `ITEM_TYPE_WORDS`, odmiany celu jako osobne pozycje, liczby jawne, bez elementów z listy „What We're NOT Doing”.
- Motyw ustawia atrybut `data-theme` na `<html>` (parametr adresu `?theme=dark`), jak w aplikacji.
- Zrzuty na wariant: oba ekrany w obu motywach przy 1280 px i `stream` w obu motywach przy 375 px, czyli 6 PNG, razem 18. Zrzuty wariantów obejmują kadr okna, nie całą stronę, i mają `deviceScaleFactor: 2`, żeby kroje oglądane na ekranie o wysokiej gęstości nie były rozmyte, bo to z nich właściciel wybiera krój.
- `directions.md`: dla każdego wariantu jednozdaniowy opis charakteru, wybrane kroje z licencją, uzasadnienie standardami, tabela kontrastu, wynik kontroli stron; na końcu sekcja „Decyzja właściciela” z miejscem na wybór.

#### 5. Galeria wariantów

**File**: `context/changes/ui-design-system/gallery/index.html` (nowy)

**Intent**: Właściciel ogląda warianty obok siebie w przeglądarce jednym kliknięciem.

**Contract**: Strona zestawia trzy warianty obok siebie dla tego samego ekranu, z przełączeniem motywu i szerokości, i odwołuje się do wszystkich 18 zrzutów. Publikowana jako prywatny Artifact z obrazkami jako plikami towarzyszącymi; link dostaje właściciel w rozmowie. Gdy narzędzie Artifact jest niedostępne, właściciel dostaje ścieżkę do tej samej strony otwieranej lokalnie.

### Success Criteria:

#### Automated Verification:

- Trzy warianty mają komplet plików i 18 zrzutów: `node tools/design-check/design-check.mjs inventory context/changes/ui-design-system/directions`
- Każdy wariant spełnia progi kontrastu w obu motywach: `node tools/design-check/design-check.mjs contrast` dla `directions/a`, `directions/b` i `directions/c`
- Strony wariantów przechodzą kontrolę stron (axe, brak przewijania w poziomie, rozmiar celów, kroje z polskimi znakami): `node tools/design-check/design-check.mjs pages context/changes/ui-design-system/directions`
- `ui-design.md` ma wymagane nagłówki i tabelę ekranów pokrywającą listę wymagań: `node tools/design-check/design-check.mjs doc --stage 1`
- Galeria odwołuje się do każdego zrzutu wariantów: `node tools/design-check/design-check.mjs inventory context/changes/ui-design-system/directions --gallery context/changes/ui-design-system/gallery/index.html`
- Kod aplikacji bez zmian: `git diff --quiet $(git merge-base origin/main HEAD) -- szczur-radar-web szczur-radar-api`
- SVG roadmapy zgodne z danymi skryptu: `grep -q '"F-07": (.*"progress"' tools/render_roadmap_svg.py && python3 tools/render_roadmap_svg.py && git diff --quiet docs/visuals/roadmap.svg`
- Skrypt kontroli wykrywa celowe złamania: `node tools/design-check/design-check.mjs selftest`
- Parser kontrastu czyta plik tokenów tak jak test aplikacji: `node tools/design-check/design-check.mjs contrast szczur-radar-web/src/style.css tools/design-check/fixtures/app-pairs.json` (19 par z `PAIRS`, `styleContract.test.ts:29-49`, próg 4,5) kończy się kodem 0

#### Manual Verification:

- Właściciel wybiera wariant A, B albo C (albo zleca połączenie lub poprawki) na galerii, a wybór jest zapisany w `directions/directions.md`, sekcja „Decyzja właściciela”

**Implementation Note**: tu bieg się zatrzymuje. Faza 2 startuje dopiero po zapisanym wyborze właściciela. Zlecone poprawki wariantu przechodzą te same kontrole i ponowną publikację galerii przed zapisem wyboru.

---

## Phase 2: Język wizualny i komponenty

### Overview

Wybrany wariant staje się kompletnym systemem: tokeny, krój pisma, ikony i arkusz komponentów ze wszystkimi stanami, opisane w dokumencie projektu.

### Changes Required:

#### 1. Tokeny

**File**: `context/foundation/ui-design/tokens.css` (nowy), `context/foundation/ui-design/pairs.json` (nowy)

**Intent**: Jeden plik z całym językiem wizualnym, gotowy do przeniesienia do `style.css` w F-08.

**Contract**: Format z „Critical Implementation Details”. Zawiera role kolorów w obu motywach (tekst, powierzchnie i ich poziomy, obramowania, akcent, stany, stanowiska, podświetlenie cytatu, zaznaczenie wiersza), skalę typografii z rozmiarem, interlinią i wagą, skalę odstępów, promienie, cienie albo poziomy wyniesienia, szerokości układu i progi szerokości, czasy i krzywe ruchu. Obramowania i pierścień fokusu są hexem, żeby dało się policzyć ich kontrast. `pairs.json` wymienia każdą parę tekst na tle i każdą parę elementu interfejsu z progiem; to lista, którą F-08 dopisze do `PAIRS`, dając testowi próg osobno dla każdej pary (dziś jest jeden `MIN_CONTRAST = 4.5`, `styleContract.test.ts:26`, więc pary 3:1 bez tej zmiany byłyby czerwone).

#### 2. Krój pisma i ikony

**File**: `context/foundation/ui-design/fonts/` (nowy: pliki `woff2`, plik licencji), `context/foundation/ui-design/icons/` (nowy: pliki SVG, plik licencji)

**Intent**: Zasoby, które F-08 skopiuje do aplikacji bez szukania źródeł.

**Contract**: Kroje na licencji OFL, podzbiory łaciński i łaciński rozszerzony, osobny plik dla każdej użytej wagi, łącznie do około 300 kB. Ikony z jednego zestawu na otwartej licencji, tylko użyte w projekcie, z `currentColor` i bez literałów kolorów; lista ikon z nazwą i miejscem użycia jest w dokumencie.

#### 3. Arkusz komponentów

**File**: `context/foundation/ui-design/prototypes/prototype.css` (nowy), `context/foundation/ui-design/prototypes/components.html` (nowy), zrzuty w `context/foundation/ui-design/screens/`

**Intent**: Każdy element interfejsu pokazany we wszystkich stanach, jako wzór dla katalogu komponentów w F-08.

**Contract**: Każdy komponent ma sekcję z kotwicą `c-<nazwa>` i pokazuje stany z macierzy siedmiu stanów repo (domyślny, hover, fokus, wyłączony, błąd, pusty, ładowanie) albo ma w dokumencie „nie dotyczy” z powodem. Minimum: przycisk (główny, drugorzędny, z ikoną, sam z ikoną), link, pola (tekst, hasło, wyszukiwanie, data, lista wyboru, pole zaznaczenia), przełącznik segmentowy, znacznik stanu, etykieta stanowiska (sześć kierunków i „ostrożnie”), znacznik celu z rodzajem, pasek proporcji stanowisk z liczbami, wiersz wpisu na liście (zaznaczony, bez oceny, z wieloma celami), karta celu w ocenie z tezami i cytatem, podświetlenie cytatu w treści, nawigacja, nagłówek strony, dzwonek z listą powiadomień, wiersz tabeli i listy admina, paginacja, komunikat stanu, szkielet ładowania, pusta lista, błąd z ponowieniem, przełącznik motywu. `prototype.css` używa wyłącznie tokenów z `tokens.css`.

#### 4. Dokument: język wizualny, komponenty, treść

**File**: `context/foundation/ui-design.md`

**Intent**: Opisać system tak, żeby F-08 i wycinki wdrażały go bez zgadywania.

**Contract**: „Język wizualny”: tabele tokenów z przeznaczeniem każdej roli i zasadami użycia (kiedy która powierzchnia, kiedy akcent). „Komponenty”: dla każdego komponentu przeznaczenie, anatomia, stany, zasady dostępności (rola, nazwa, obsługa klawiaturą) i odpowiednik wśród 11 istniejących komponentów (zostaje, zmienia się, nowy). „Treść i etykiety”: nazwy pozycji nawigacji, nagłówków i przycisków, formaty dat i liczb, ton komunikatów; etykiety istniejące w testach E2E zostają, chyba że dokument uzasadnia zmianę.

### Success Criteria:

#### Automated Verification:

- Tokeny spełniają progi kontrastu i format zgodny z testem aplikacji: `node tools/design-check/design-check.mjs contrast context/foundation/ui-design/tokens.css context/foundation/ui-design/pairs.json`
- Arkusz komponentów przechodzi kontrolę stron w obu motywach przy 1280 i 375 px: `node tools/design-check/design-check.mjs pages context/foundation/ui-design/prototypes/components.html`
- Każdy komponent z tabeli w `ui-design.md` ma kotwicę w arkuszu, a arkusz ma zrzuty: `node tools/design-check/design-check.mjs doc --stage 2`
- Pliki krojów i ikon mają pliki licencji, a `prototype.css` nie zawiera literałów kolorów: `node tools/design-check/design-check.mjs inventory context/foundation/ui-design --assets`
- Kod aplikacji bez zmian: `git diff --quiet $(git merge-base origin/main HEAD) -- szczur-radar-web szczur-radar-api`

---

## Phase 3: Prototypy wszystkich ekranów

### Overview

Każdy ekran M-5 powstaje jako prototyp z komponentów fazy 2, w obu motywach i dwóch szerokościach, przechodzi przegląd heurystyczny i trafia do właściciela do akceptacji.

### Changes Required:

#### 1. Prototypy ekranów

**File**: `context/foundation/ui-design/screens.json` (nowy), `context/foundation/ui-design/prototypes/screens/*.html` (nowe), `context/foundation/ui-design/screens/*.png` (nowe)

**Intent**: Pokazać każdy ekran tak, jak ma wyglądać po wdrożeniu, z treścią zgodną z kontraktem danych.

**Contract**:
- `screens.json` to spis ekranów: nazwa pliku, tytuł, wymagania (FR), wycinek roadmapy, źródło danych (dziś albo wycinek). Tabela „Ekrany” w dokumencie jest z nim zgodna.
- Ekrany pokrywają listę wymagań z fazy 1 i co najmniej: przegląd dnia, listę wpisów z oceną obok (z filtrem po celu i stanowisku, przełącznikiem „pokaż wszystkie”, sortowaniem, filtrem dat, wyszukiwaniem), czytanie wpisu z podświetlonymi cytatami i przejściem do poprzedniego i następnego, katalog celów, kartę celu z proporcjami, logowanie, aktywację, panel admina w nowym podziale, stronę przebiegu, otwarte powiadomienia admina oraz stany ładowania, pustej listy i błędu z ponowieniem na liście i w panelu admina.
- Każdy ekran ma zrzuty całej strony w czterech odmianach: jasny i ciemny przy 1280 i 375 px, z `deviceScaleFactor: 1`.
- Strony mają role i nazwy takie, jakie dostanie aplikacja: jeden `main`, na ekranach zalogowanych jeden `banner` z wylogowaniem, e-mailem i przełącznikiem „Ciemny motyw”, nawigacja i sekcje jako nazwane regiony, pola z etykietami.
- Treść: wpisy z dwóch źródeł, ocena z wieloma celami i różnymi stanowiskami, stanowisko „ostrożnie”, wpis bez oceny z odsiewu, wpis nieinwestycyjny, odmiany celu jako osobne pozycje katalogu, cel z jednym wpisem, długi tytuł, proporcje o różnym rozkładzie. Bez prawdziwych nazw użytkowników.
- Elementy zależne od nowego API są oznaczone w tabeli „Ekrany” wycinkiem, który je dostarcza; propozycje dla niewiadomych właściciela są opisane jako niewiążące.

#### 2. Przegląd heurystyczny

**File**: `context/changes/ui-design-system/reviews/heuristic-review.md` (nowy)

**Intent**: Niezależne sprawdzenie projektu względem standardów, zanim zobaczy go właściciel, który sam tych standardów nie oceni.

**Contract**: Przegląd wykonuje osobny agent, który nie projektował ekranów, na zrzutach i prototypach, według listy z sekcji „Zasady i standardy”: dziesięć heurystyk Nielsena, reguły WCAG wybrane w dokumencie, zgodność z PRD (brak elementów zabronionych) i odpowiedź na cztery bolączki właściciela. Każde ustalenie ma ekran, regułę, wagę (blokujące albo uwaga) i stan (otwarte albo zamknięte ze wskazaniem poprawki). Plik kończy się wierszem `**Otwarte blokujące:** N`. Ustalenia blokujące poprawia implementujący w prototypach przed bramką właściciela, a zamyka je wyłącznie agent przeglądu w drugim przejściu po poprawionych prototypach i zrzutach: tylko on zmienia stan ustalenia i wiersz `**Otwarte blokujące:** N`, a każde przejście jest w pliku osobną sekcją z datą.

#### 3. Galeria kompletu i decyzja

**File**: `context/changes/ui-design-system/gallery/index.html`, `context/foundation/ui-design.md` (sekcja „Decyzja właściciela”)

**Intent**: Właściciel ogląda komplet w jednym miejscu, a jego decyzja jest zapisana przy projekcie.

**Contract**: Galeria pokazuje każdy ekran w obu motywach i obu szerokościach oraz arkusz komponentów, z podpisem celu ekranu; publikacja jak w fazie 1. Sekcja „Decyzja właściciela” zapisuje datę, wybrany wariant, akceptację albo listę poprawek z ich wykonaniem.

### Success Criteria:

#### Automated Verification:

- Każdy ekran ze `screens.json` ma prototyp i cztery zrzuty: `node tools/design-check/design-check.mjs inventory context/foundation/ui-design/screens.json`
- Wszystkie prototypy ekranów przechodzą kontrolę stron w obu motywach przy 1280 i 375 px: `node tools/design-check/design-check.mjs pages context/foundation/ui-design/prototypes/screens`
- Tabela „Ekrany” jest zgodna ze `screens.json` i pokrywa listę wymagań: `node tools/design-check/design-check.mjs doc --stage 3`
- Przegląd heurystyczny nie ma otwartych ustaleń blokujących: `grep -qx '\*\*Otwarte blokujące:\*\* 0' context/changes/ui-design-system/reviews/heuristic-review.md`
- Galeria odwołuje się do każdego zrzutu ekranów: `node tools/design-check/design-check.mjs inventory context/foundation/ui-design/screens.json --gallery context/changes/ui-design-system/gallery/index.html`
- Kod aplikacji bez zmian: `git diff --quiet $(git merge-base origin/main HEAD) -- szczur-radar-web szczur-radar-api`

#### Manual Verification:

- Właściciel akceptuje komplet ekranów na galerii albo zleca poprawki, a decyzja jest zapisana w `ui-design.md`, sekcja „Decyzja właściciela”

**Implementation Note**: tu bieg się zatrzymuje. Poprawki zlecone przez właściciela przechodzą kontrole tej fazy i ponowną publikację galerii. Faza 4 startuje po zapisanej akceptacji.

---

## Phase 4: Przekazanie do wdrożenia

### Overview

Zaakceptowany projekt dostaje wszystko, czego potrzebują F-08 i wycinki: mapę wdrożenia, kontrakt dla testów E2E i różnice wobec roadmapy; wytyczne repo i roadmapa wskazują projekt.

### Changes Required:

#### 1. Dokument: wdrożenie, testy E2E, roadmapa

**File**: `context/foundation/ui-design.md`

**Intent**: Zamienić projekt w kryterium akceptacji dla kolejnych zmian.

**Contract**:
- „Wdrożenie”: mapa tokenów (dzisiejsza nazwa i wartość, nowa nazwa i wartość, nowe role), mapa komponentów (11 istniejących i nowe), kolejność wdrażania i lista zmian w bramach, które F-08 robi świadomie: oczekiwana rodzina w `visual/font.spec.ts` i alias `visual/fonts.conf`, pary w `PAIRS` z progiem osobnym dla każdej pary (4,5 dla tekstu, 3,0 dla elementów interfejsu i fokusu) zamiast jednego `MIN_CONTRAST`, pliki na liście `CLEANED`, 56 wzorców F-06, formuła wysokości pól `style.css:170-175`, nazwy sekcji katalogu jako nazwy wzorców.
- „Kontrakt dla testów E2E”: dla każdego ekranu ze `screens.json` role i nazwy, po których test znajduje elementy; przepływy do przeklikania zapisane jako kroki użytkownika z oczekiwanym wynikiem; istniejące testy z `szczur-radar-web/e2e/`, które zmiana nawigacji lub etykiet łamie, z nazwą pliku i testu; tryby atrapy potrzebne do przepływu.
- „Różnice wobec roadmapy”: lista dla `/10x-roadmap` (v6.2): ekran startowy (przegląd dnia docelowo, lista do S-59), podział panelu admina wobec S-50, każda inna zmiana ekranów wobec S-47–S-59, rozstrzygnięcie otwartego pytania 7, rozstrzygnięte niewiadome F-07 (krój, paleta), tabela wycinek i jego prototypy.
- „Propozycje poza PRD”: pomysły wynikające ze standardów, których PRD v4 nie obejmuje, bez prototypów.

#### 2. Roadmapa: test E2E w każdym wycinku

**File**: `context/foundation/roadmap.md`

**Intent**: Wymóg testów klikania ma być widoczny przy wycinkach, a nie tylko w warunku ukończenia kamienia milowego (decyzja właściciela z wywiadu).

**Contract**: Notka nad wycinkami (`roadmap.md:224`) dostaje zdanie, że każdy wycinek S-47–S-59 kończy się testem E2E swoich przepływów według sekcji „Kontrakt dla testów E2E” w `ui-design.md`. Inne treści roadmapy zostają; `updated` we frontmatterze dostaje bieżącą datę, a SVG jest wygenerowane ponownie.

#### 3. Wytyczne repo i propozycje makiet

**File**: `REPOSITORY_GUIDELINES.md` (sekcja „UI (szczur-radar-web)”), `context/foundation/ui-ux-proposals.md`

**Intent**: Kolejny agent pracujący nad UI trafia na projekt, a nie na makiety.

**Contract**: W wytycznych jeden punkt: źródłem wyglądu i układu jest `context/foundation/ui-design.md` z zasobami w `context/foundation/ui-design/`; wycinek UI wdraża swój ekran według prototypu i kontraktu E2E; zmiana projektu to edycja tego dokumentu z akceptacją właściciela i kontrolami `tools/design-check/`. W sekcji „Project Structure” punkt o `tools/design-check/`: co sprawdza, jak uruchomić, wymagania (`npm ci` w module web, Chromium Playwrighta), brak zadania w CI. W `ui-ux-proposals.md` notka pod istniejącą z 2026-11-19: projekt zaakceptowany, z datą i wskazaniem dokumentu.

#### 4. Domknięcie PR

**File**: PR zmiany, issue #196

**Intent**: Oddać zmianę do przeglądu i merge'u przez człowieka.

**Contract**: Opis PR wymienia, co powstało, wybrany wariant, wynik kontroli i `Refs #196`; issue dostaje komentarz o akceptacji projektu. Po tej fazie idą `/10x-impl-review` (z jawnym `phase 4`) i `/10x-archive` na tej samej gałęzi. Merge robi człowiek.

### Success Criteria:

#### Automated Verification:

- `ui-design.md` ma wszystkie sekcje wypełnione, linki względne działają, a kontrakt E2E wymienia każdy ekran ze `screens.json`: `node tools/design-check/design-check.mjs doc --stage 4`
- Roadmapa zawiera zdanie o teście E2E w każdym wycinku: `grep -q 'kończy się testem E2E' context/foundation/roadmap.md`
- Wytyczne repo wskazują dokument projektu: `grep -q 'context/foundation/ui-design.md' REPOSITORY_GUIDELINES.md`
- SVG roadmapy zgodne z danymi skryptu: `python3 tools/render_roadmap_svg.py && git diff --quiet docs/visuals/roadmap.svg`
- Kod aplikacji bez zmian: `git diff --quiet $(git merge-base origin/main HEAD) -- szczur-radar-web szczur-radar-api`
- CI w PR jest zielone: `gh pr checks --watch` kończy się kodem 0

---

## Testing Strategy

### Unit Tests:

- Brak: zmiana nie dodaje kodu aplikacji. Istniejące testy modułów nie są uruchamiane lokalnie; pilnuje ich CI w PR.

### Integration Tests:

- `design-check.mjs pages` jest odpowiednikiem bramy dostępności F-06 dla prototypów: ten sam silnik axe i te same tagi (w tym `target-size`), dwa motywy, szerokości 1280 i 375 px oraz 320 px dla reflow.
- `design-check.mjs contrast` powtarza wzór i parser `styleContract.test.ts` na tokenach projektu, więc pary tekstowe (4,5), które przechodzą tutaj, przejdą test aplikacji po przeniesieniu w F-08; zgodność parsera z testem pokazuje kryterium 1.9 na dzisiejszym `style.css`.
- Skrypt jest sprawdzany na celowym złamaniu przed pierwszym użyciem (`selftest`, kryterium 1.8).

### Manual Testing Steps:

1. Właściciel otwiera galerię wariantów i wybiera jeden (bramka fazy 1).
2. Właściciel otwiera galerię kompletu i akceptuje albo wypisuje poprawki (bramka fazy 3).

## Performance Considerations

Zrzuty zwiększają repo: 18 PNG wariantów w kadrze okna (2×) i około 50 PNG ekranów całych stron (1×). Budżet łączny to 15 MB; przy przekroczeniu zrzuty przy 375 px obejmują kadr okna zamiast całej strony. Pliki krojów mają budżet około 300 kB, bo F-08 doda je do tego, co pobiera przeglądarka.

## Migration Notes

Brak danych i kodu do migracji. Projekt wchodzi do aplikacji w F-08 (tokeny, komponenty, bramy) i w wycinkach S-47–S-59 (ekrany). Wycofanie zmiany to revert commitów w `context/`, `tools/design-check/`, `REPOSITORY_GUIDELINES.md` i roadmapie.

## References

- Badanie: `context/changes/ui-design-system/research.md`
- Roadmapa: `context/foundation/roadmap.md:193-206` (F-07), `:208-219` (F-08), `:224` (notka nad wycinkami), `:445` (pytanie 7)
- Wzorzec akceptacji na wariantach: `context/archive/2026-11-17-light-dark-theme-switch/palette/palette.md`, `context/archive/2026-11-17-light-dark-theme-switch/plan.md:165-210`
- Bramy: `szczur-radar-web/src/styleContract.test.ts:26-49,75-84,108-138`, `szczur-radar-web/visual/axe.ts:7`, `szczur-radar-web/visual/font.spec.ts:31-43`, `szczur-radar-web/e2e/`
- Decyzje właściciela z 2026-11-15: `context/changes/post-m4-ui-readiness-audit/research.md:347-361`
- Makiety (materiał wejściowy): `context/foundation/ui-ux-proposals.md`, `docs/visuals/ui-ux-proposals/`
- Issue: #196 (F-07), #197 (F-08)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Podstawy i trzy warianty wyglądu

#### Automated

- [x] 1.1 Trzy warianty mają komplet plików i 18 zrzutów — ef6f7eb
- [x] 1.2 Każdy wariant spełnia progi kontrastu w obu motywach — ef6f7eb
- [x] 1.3 Strony wariantów przechodzą kontrolę stron (axe, brak przewijania w poziomie, rozmiar celów, kroje z polskimi znakami) — ef6f7eb
- [x] 1.4 `ui-design.md` ma wymagane nagłówki i tabelę ekranów pokrywającą listę wymagań — ef6f7eb
- [x] 1.5 Galeria odwołuje się do każdego zrzutu wariantów — ef6f7eb
- [x] 1.6 Kod aplikacji bez zmian — ef6f7eb
- [x] 1.7 SVG roadmapy zgodne z danymi skryptu — ef6f7eb
- [x] 1.8 Skrypt kontroli wykrywa celowe złamania — ef6f7eb
- [x] 1.9 Parser kontrastu czyta plik tokenów tak jak test aplikacji — ef6f7eb

#### Manual

- [x] 1.10 Właściciel wybiera wariant A, B albo C (albo zleca połączenie lub poprawki) na galerii, a wybór jest zapisany w `directions/directions.md`, sekcja „Decyzja właściciela” — ef6f7eb

### Phase 2: Język wizualny i komponenty

#### Automated

- [ ] 2.1 Tokeny spełniają progi kontrastu i format zgodny z testem aplikacji
- [ ] 2.2 Arkusz komponentów przechodzi kontrolę stron w obu motywach przy 1280 i 375 px
- [ ] 2.3 Każdy komponent z tabeli w `ui-design.md` ma kotwicę w arkuszu, a arkusz ma zrzuty
- [ ] 2.4 Pliki krojów i ikon mają pliki licencji, a `prototype.css` nie zawiera literałów kolorów
- [ ] 2.5 Kod aplikacji bez zmian

### Phase 3: Prototypy wszystkich ekranów

#### Automated

- [ ] 3.1 Każdy ekran ze `screens.json` ma prototyp i cztery zrzuty
- [ ] 3.2 Wszystkie prototypy ekranów przechodzą kontrolę stron w obu motywach przy 1280 i 375 px
- [ ] 3.3 Tabela „Ekrany” jest zgodna ze `screens.json` i pokrywa listę wymagań
- [ ] 3.4 Przegląd heurystyczny nie ma otwartych ustaleń blokujących
- [ ] 3.5 Galeria odwołuje się do każdego zrzutu ekranów
- [ ] 3.6 Kod aplikacji bez zmian

#### Manual

- [ ] 3.7 Właściciel akceptuje komplet ekranów na galerii albo zleca poprawki, a decyzja jest zapisana w `ui-design.md`, sekcja „Decyzja właściciela”

### Phase 4: Przekazanie do wdrożenia

#### Automated

- [ ] 4.1 `ui-design.md` ma wszystkie sekcje wypełnione, linki względne działają, a kontrakt E2E wymienia każdy ekran ze `screens.json`
- [ ] 4.2 Roadmapa zawiera zdanie o teście E2E w każdym wycinku
- [ ] 4.3 Wytyczne repo wskazują dokument projektu
- [ ] 4.4 SVG roadmapy zgodne z danymi skryptu
- [ ] 4.5 Kod aplikacji bez zmian
- [ ] 4.6 CI w PR jest zielone
