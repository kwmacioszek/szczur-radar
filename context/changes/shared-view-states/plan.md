# S-48: wspólne stany widoków i stronicowanie — plan implementacji

## Overview

Cztery stany widoku z projektu F-07 stają się wspólnymi komponentami: **Ładowanie** (szkielet o kształcie wyniku), **Pusta lista**, **Błąd z ponowieniem** i **Stronicowanie**. Trafiają do katalogu komponentów we wszystkich wariantach. Na te komponenty przechodzą lista postów, strona posta, strona przebiegu i pięć sekcji panelu admina, więc kopie wzorca znikają z widoków. Szerokość kolumny widoku ustawia trasa w powłoce, a nie każdy widok osobno. Wygląd i nazwy pochodzą z `context/foundation/ui-design.md` (sekcje „Stany widoku”, „Sterowanie” → „Stronicowanie”, tabela „Stany”, „Kontrakt dla testów E2E”) i z prototypów `state-loading`, `state-empty`, `state-error`, `admin-loading`, `admin-empty`, `admin-error`. API się nie zmienia.

## Current State Analysis

- Każdy widok sam wpisuje te same stany, bez szkieletu:
  - ładowanie to `<p class="loading">` z tekstem: `FeedView.vue:154`, `PostDetailView.vue:142`, `RunDetailView.vue:162` i cztery sekcje `AdminView.vue` (`:457`, `:481`, `:512`, `:563`); źródła mają `<span class="loading">` w polu wyboru (`:422`);
  - błąd to `StatusMessage tone="error"` z osobnym `AppButton` „Spróbuj ponownie” tylko w `FeedView.vue:155-158`, `PostDetailView.vue:143-146`, `RunDetailView.vue:163-166` i przy źródłach w panelu admina (`AdminView.vue:423-426`). Sekcje „Dni bez danych”, „Jakość ocen (7 dni)”, „Ostatnie przebiegi” i „Nieudane posty” nie mają własnego ponowienia; wraca się do nich tylko przez „Odśwież” całego panelu (`AdminView.vue:396-404`, `:510`);
  - pusta lista to `<p class="empty">` (`FeedView.vue:161-168`, `AdminView.vue:460, 488, 514, 565`). Brak posta albo przebiegu to `<p>` z odnośnikiem (`PostDetailView.vue:147-150`, `RunDetailView.vue:167-170`);
  - style `.loading, .empty` i `.message` są skopiowane w `FeedView.vue:205-213`, `PostDetailView.vue:224-232` i `RunDetailView.vue:323-333`.
- Ponowienie przełącza widok z powrotem na tekst ładowania (`FeedView.vue:86-88`, `PostDetailView.vue:103-105`, `RunDetailView.vue:138-140`). Prototyp zostawia błąd z przyciskiem w toku („Wczytywanie…”).
- Kontener strony: powłoka daje już kolumnę `--width-content` (70rem, `AppShell.vue:128-157`). Mimo to każdy widok stawia w niej drugą, węższą kolumnę z tym samym komentarzem:
  - `.feed` (`FeedView.vue:183-193`), `.post-detail` (`PostDetailView.vue:192-202`) i `.run-detail` (`RunDetailView.vue:291-301`): 48rem, od 1025 px 54rem;
  - `.admin` (`AdminView.vue:622-632`): 56rem, od 1025 px 63rem.
  Katalog (`KitchenSinkView.vue:1021-1025`) jest poza powłoką i ma własny `main`.
- Stronicowanie jest tylko w `FeedView.vue:169-176`:
  - `nav` „Strony” z „Nowsze”, „Strona N z M” i „Starsze”, bez ikon;
  - przy jednej stronie pokazuje „Strona 1 z 1” z dwoma wyłączonymi przyciskami (`:33`), a projekt każe go wtedy nie rysować (reguła C);
  - kolejna strona zastępuje listę tekstem „Wczytywanie strony N…” (`:39-43`), a widok przewija na górę od razu po kliknięciu (`:126-142`). Prototyp (`components.html`, „W środku, podczas wczytywania następnej strony”) zostawia listę i pokazuje „Wczytywanie…” w klikniętym przycisku.
  Przebiegi w panelu admina mają „Pokaż starsze” (doczytywanie, `AdminView.vue:554-557`). To nie jest stronicowanie i zostaje.
- Komponenty F-08 do użycia:
  - `AppButton` z `busy`/`busyLabel` i ikoną, ale ikona stoi tylko przed tekstem (`AppButton.vue:14, 41-42`), a prototyp ma strzałkę za „Starsze”;
  - `AppIcon` z ikonami `i-search`, `i-inbox`, `i-circle-alert`, `i-circle-check`, `i-refresh-cw`, `i-chevron-left` i `i-chevron-right` (`iconNames.ts:6-32`);
  - globalna klasa `.visually-hidden` (`style.css:259`).
- Bramki:
  - lista `CLEANED` w `styleContract.test.ts:128-154` obejmuje każdy widok i komponent, więc nowy komponent dochodzi do niej w chwili powstania;
  - sekcja katalogu dostaje wzorzec 375 px sama, ze sluga nagłówka 2 (`visual/targets.ts:208-247`), ale nagłówek 2 wewnątrz sekcji byłby policzony jako osobna sekcja;
  - `playwright.visual.config.ts:49` wyłącza animacje, więc pulsujący szkielet nie psuje powtarzalności zrzutów;
  - `VIEWS` nie ma żadnego stanu widoku.
- Testy, które sprawdzają dzisiejsze teksty: `e2e/feed.spec.ts:26-87`, gdzie alert ma treść „Nie udało się wczytać postów.” z kropką, której nie ma w prototypie, oraz testy jednostkowe `FeedView.test.ts`, `PostDetailView.test.ts`, `RunDetailView.test.ts` i `AdminView.test.ts`.
- Atrapa ma już każdy potrzebny tryb (`dev/mock-api.js:24-31, 147-157`):
  - `MODE` `loading`, `error`, `empty`, `loading-next` i `error-next`;
  - `QUALITY: error` i `MISSING: error`;
  - pusty wynik filtra pod `/?q=zzz`.
  `loading-next` i `error-next` nie są dziś używane w żadnym teście w przeglądarce.

## Desired End State

- W `src/components/` są:
  - `LoadingState`: szkielet w kształcie wierszy listy, linii karty albo formularza, z tekstem „Wczytywanie…” dla czytnika;
  - `EmptyState`: ikona, tytuł, zdanie i opcjonalne działanie;
  - `ErrorState`: `role="alert"`, ikona, tytuł, zdanie i „Spróbuj ponownie” z ikoną; podczas ponawiania przycisk jest w toku z tekstem „Wczytywanie…”, a komunikat traci rolę `alert`;
  - `PagerNav`: `nav` „Strony”, pary etykiet „Nowsze”/„Starsze” i „Poprzednie”/„Następne”, „Strona N z M”, przycisk w toku; przy jednej stronie nie rysuje niczego.
- Katalog ma sekcje „Ładowanie”, „Pusta lista”, „Błąd z ponowieniem” i „Stronicowanie” ze wszystkimi wariantami. Sekcja „Przycisk” pokazuje ikonę na końcu.
- Lista postów:
  - pierwsze wczytanie i zmiana filtra pokazują szkielet wierszy;
  - przy zmianie strony lista zostaje z przyciskiem w toku, a po wczytaniu widok przewija na górę i przenosi fokus na tytuł;
  - pusty wynik filtra pokazuje nagłówek „Brak postów dla tych filtrów” i przycisk „Wyczyść filtry”; pusta lista bez filtra ma „Brak ocenionych postów” i zdanie „Pojawią się po najbliższej aktualizacji danych.”;
  - błąd pokazuje nagłówek „Nie udało się wczytać postów”, zdanie o filtrach i „Spróbuj ponownie”;
  - przy jednej stronie nie ma stronicowania.
- Strona posta i strona przebiegu mają szkielet, błąd z ponowieniem i pusty stan „Nie ma takiego …”. Surowe dane posta mają ten sam szkielet i ten sam błąd.
- Każda z pięciu sekcji panelu admina:
  - pokazuje szkielet do czasu swoich danych;
  - przy błędzie pokazuje „Błąd z ponowieniem”, a jego „Spróbuj ponownie” wczytuje tylko tę sekcję;
  - pustą listę pokazuje jako „Pusta lista”.
  „Odśwież” jest wyłączone, dopóki przebiegi wczytują się pierwszy raz.
- Trasa mówi o szerokości kolumny (`meta.column`), a powłoka ją ustawia. Widoki nie mają własnego kontenera szerokości. Wygląd widoków z danymi się nie zmienia.
- Weryfikacja:
  - zielone `npm test`, `npm run build`, `npm run e2e`, `npm run visual:docker` i CI na PR;
  - nowe wzorce sześciu stanów widoków i czterech sekcji katalogu;
  - E2E według kontraktu stanów;
  - agent sprawdza listę i panel admina w PR env;
  - właściciel akceptuje zrzuty obok prototypów.

### Key Discoveries:

- `slug()` (`visual/targets.ts:208-217`) zamienia `ł` przed usunięciem znaków diakrytycznych, więc „Ładowanie” daje `ladowanie`, a „Błąd z ponowieniem” `blad-z-ponowieniem`. Tytuły stanów w katalogu muszą być `p`, nie `h2` (`catalogSections`, `:231-247`).
- Prototypy używają dwóch elementów tytułu: `h2`, gdy stan sam nazywa obszar listy (`state-empty.html:151-157`, `state-error.html:150-156`), i `p`, gdy sekcja ma już swój nagłówek (`admin-empty.html:146-150`, `admin-error.html:146-150`). Komponent wybiera element przez prop.
- Szkielet listy w prototypie (`prototype.css:942-948, 1569-1593, 2395-2398`) to ramka `--line`/`--radius-xl`/`--surface` z wierszami w siatce `13.5rem minmax(0, 1fr) 14rem`, a poniżej 900 px w jednej kolumnie. Szkielet karty panelu admina (`admin-loading.html:145-153`) to wiersz dwóch pasków wysokości `--control-height` i pasek 40% albo paski 70% i 50%.
- Puls szkieletu i obrót znaku pracy działają tylko przy `prefers-reduced-motion: no-preference` (`prototype.css:1595-1619`, `AppButton.vue:119-129`). Bez ruchu oba są nieruchome i widoczne (`ui-design.md`, „Ruch”).
- `scroll.ts:6-8, 53-55`: przy tej samej ścieżce (`?page`) router nie przewija, bo przewija sam `FeedView`. Przeniesienie przewinięcia na moment po wczytaniu nie dotyka `scroll.ts`.
- `AdminView` czyta sekcje równolegle przy montażu (`:406-408`). Każda sekcja poza źródłami i nieudanymi postami ma licznik „ostatniego odczytu” (`latestMissing`, `latestQuality`, `latestRefresh`), a flaga ładowania jest prawdziwa tylko bez danych na ekranie (`:102`, `:237`, `:336`). Ponowienie sekcji korzysta z tej samej funkcji odczytu.

## What We're NOT Doing

- Elementy prototypów stanów z innych wycinków:
  - przełącznik „Zakres postów” (S-52);
  - pole „Szukaj w postach”, przycisk „Filtry” i znaczniki „Włączone filtry” (S-51);
  - pasek części panelu admina, odnośnik „Pokaż przebiegi” w pustym stanie nieudanych postów i tabele (S-50);
  - dni listy jako regiony z nagłówkiem 2 (S-51).
- Stany listy powiadomień w dzwonku (`NotificationMenu`). Zostają przy swoich małych stanach, bo duży blok stanu nie mieści się w wąskim menu, a projekt nie rysuje wariantu zwartego.
- Szkielet klocka liczby (`skeleton-num`): to stan komponentu „Klocek liczby” z S-59.
- Stronicowanie nieudanych postów (dziś pierwsze 50) i zamiana „Pokaż starsze” w przebiegach na stronicowanie: S-50.
- Komunikaty wyników działań i błędy, po których dane zostają na ekranie: „Nie udało się odświeżyć przebiegu. Kolejna próba za 10 s.”, błąd „Pokaż starsze”, wyniki „Uruchom”, „Ponów zaznaczone” i „Przywróć martwe”. Zostają w `StatusMessage`.
- Puste fragmenty wczytanego widoku („Brak oceny.”, „Jeszcze żadne źródło nie zakończyło pobrania.”, „Żaden krok się jeszcze nie zaczął.”). To treść widoku, nie jego stan, więc zostają tekstem.
- Logowanie i aktywacja: S-49 świadomie zostawiło je na `StatusMessage` i `AppButton`.
- Zmiana szerokości treści widoków do 70rem: widoki zachowują dzisiejsze szerokości.
- Wspólne style `.back`, `.part` i `.head` stron posta i przebiegu: znikają z przebudową tych ekranów w S-50 i S-51.
- Nowe tryby atrapy i zmiany w API: wystarczają istniejące tryby.

## Implementation Approach

1. **Komponenty.** Najpierw cztery komponenty prezentacyjne i ikona na końcu w `AppButton`. Wszystkie trafiają do katalogu, pod skan literałów i pod wzorce, zanim użyje ich jakikolwiek widok.
2. **Kolumna i widoki czytania.** Potem kontener szerokości przechodzi do powłoki. Najpierw sam, z dowodem, że wzorce widoków się nie zmieniły. Następnie lista, post i przebieg przechodzą na nowe stany, z nowym zachowaniem stronicowania i z wzorcami trzech stanów listy.
3. **Panel admina.** Pięć sekcji przechodzi na wspólne stany z własnym ponowieniem, z wzorcami trzech stanów panelu.
4. **Testy i weryfikacja.** Na końcu E2E przeklikuje przepływy stanów z kontraktu. Dokumentacja dogania kod, agent sprawdza PR env, a właściciel ocenia zrzuty.

Każda faza kończy się zielonymi bramkami i commitem.

## Critical Implementation Details

- **Fokus po ponowieniu.** Przycisk w toku jest wyłączony (`AppButton busy`). Chromium zostawia wtedy fokus na wyłączonym elemencie, a po jego odmontowaniu fokus spada na `body`. Dlatego:
  - po udanym ponowieniu fokus idzie tam, gdzie dziś: na `h1` widoku, a w sekcji panelu admina na nagłówek 2 sekcji (`tabindex="-1"`);
  - po kolejnej porażce idzie z powrotem na „Spróbuj ponownie”, które `ErrorState` daje przez `focus()`;
  - oba przeniesienia dotyczą tylko ponowienia z własnego przycisku „Spróbuj ponownie”. Po „Odśwież” w panelu admina fokus zostaje na miejscu, także gdy odświeżenie ponawia sekcje w błędzie.
  Kolejna porażka musi też zostać ogłoszona drugi raz, więc `ErrorState` po zakończeniu ponawiania renderuje komunikat na nowo z rolą `alert` (np. przez nowy `key`). Samo przywrócenie atrybutu `role` na istniejącym elemencie nie wystarcza.
- **Zmiana strony a przewijanie.** Kliknięty przycisk w toku stoi pod listą, więc natychmiastowe `window.scrollTo({ top: 0 })` (`FeedView.vue:138`) schowałoby go. Przewinięcie na górę i fokus na tytule idą po zakończeniu wczytania strony, przy powodzeniu i przy błędzie (błąd ogłasza swój `alert`). Stara lista zostaje tylko przy zmianie samej strony przy tym samym filtrze. Zmiana filtra i pierwsze wejście pokazują szkielet, bo stara lista należy wtedy do innego zapytania. Ponowienie zostawia błąd z przyciskiem w toku (`ErrorState` z `retrying`) do odpowiedzi. Wstecz i dalej w przeglądarce zmieniają stronę tak samo: w toku jest przycisk w kierunku strony docelowej.

## Faza 1: Komponenty stanów i stronicowania

### Overview

Powstają cztery komponenty prezentacyjne według arkusza komponentów (`ui-design/prototypes/components.html:600-617, 840-887`, style `prototype.css:1031-1038, 1520-1619, 2395-2398`). `AppButton` dostaje ikonę na końcu. Wszystko trafia do katalogu, do `CLEANED` i do testów jednostkowych. Widoki jeszcze ich nie używają.

### Changes Required:

#### 1. Ikona na końcu przycisku

**File**: `szczur-radar-web/src/components/AppButton.vue`, `AppButton.test.ts`

**Intent**: „Starsze” i „Następne” mają strzałkę za tekstem, jak w prototypie.

**Contract**:
- Prop `iconPosition?: 'start' | 'end'`, domyślnie `'start'`. Przy `'end'` ikona stoi za tekstem.
- Znak pracy w toku zawsze stoi przed tekstem, a ikona znika jak dziś.
- Przy `iconOnly` prop nie ma znaczenia.

#### 2. Ładowanie

**File**: `szczur-radar-web/src/components/LoadingState.vue` (nowy), `LoadingState.test.ts` (nowy)

**Intent**: Szkielet o kształcie wyniku zamiast tekstu ładowania. Czytnik słyszy „Wczytywanie…”.

**Contract**:
- Propsy:
  - `shape: 'rows' | 'lines' | 'form'`;
  - `count?: number`: wiersze dla `rows` (domyślnie 6) albo paski dla `lines` (domyślnie 2);
  - `label?: string`, domyślnie „Wczytywanie…”.
- `label` jest w `span.visually-hidden`. Paski to puste elementy bez roli.
- Kształty:
  - `rows`: ramka listy z prototypu (`.posts` plus `.skeleton-row`), szerokości pasków w stałym wzorze z prototypu;
  - `lines`: paski 70% i 50% w kolejności;
  - `form`: wiersz dwóch pasków wysokości `--control-height` i pasek 40%.
- `aria-busy="true"` ustawia kontener widoku albo sekcji, który obejmuje też inne elementy (np. stronicowanie), a nie komponent.
- Puls tylko przy `prefers-reduced-motion: no-preference`.
- Wyłącznie tokeny. Szerokości w procentach i siatka wiersza mogą być literałami, bo skan ich nie obejmuje.

#### 3. Pusta lista

**File**: `szczur-radar-web/src/components/EmptyState.vue` (nowy), `EmptyState.test.ts` (nowy)

**Intent**: Pusty widok mówi, czego nie ma, dlaczego, i daje jeden następny krok.

**Contract**:
- Propsy:
  - `icon: IconName`;
  - `title: string`;
  - `titleTag?: 'h2' | 'p'`, domyślnie `'p'`;
  - `titleId?: string` (dla `aria-labelledby` obszaru).
- Domyślny slot to zdanie (`p.state-text`), a slot `action` to opcjonalny przycisk albo odnośnik.
- Bez roli. Wygląd `.state` z prototypu.

#### 4. Błąd z ponowieniem

**File**: `szczur-radar-web/src/components/ErrorState.vue` (nowy), `ErrorState.test.ts` (nowy)

**Intent**: Widok, który się nie wczytał, mówi, co się nie udało i co zostało. Daje jeden przycisk, który próbuje jeszcze raz i pokazuje, że próbuje.

**Contract**:
- Propsy:
  - `title: string`;
  - `titleTag?: 'h2' | 'p'`, domyślnie `'p'`;
  - `titleId?: string`;
  - `retrying?: boolean`.
- Domyślny slot to zdanie. Zdarzenie `retry`.
- `defineExpose({ focus })` przenosi fokus na przycisk.
- Znaczniki:
  - kontener `.state.state-error` z `role="alert"`, gdy nie `retrying`;
  - ikona `i-circle-alert`;
  - `AppButton icon="i-refresh-cw"` „Spróbuj ponownie” z `:busy="retrying"` i `busy-label="Wczytywanie…"`.
- Po przejściu `retrying` z `true` na `false` komunikat renderuje się na nowo, żeby czytnik ogłosił go drugi raz (Critical Implementation Details).

#### 5. Stronicowanie

**File**: `szczur-radar-web/src/components/PagerNav.vue` (nowy), `PagerNav.test.ts` (nowy)

**Intent**: Jedno stronicowanie list z projektu, z obiema parami etykiet.

**Contract**:
- Propsy:
  - `page: number` (od 0);
  - `pages: number`;
  - `labels?: 'time' | 'order'`, domyślnie `'time'` („Nowsze”/„Starsze”), `'order'` daje „Poprzednie”/„Następne”;
  - `busy?: 'previous' | 'next' | null`.
- Zdarzenia `previous` i `next`.
- Przy `pages <= 1` komponent nie renderuje niczego.
- Znaczniki:
  - `<nav aria-label="Strony">`;
  - pierwszy przycisk z `i-chevron-left` przed tekstem, wyłączony na stronie 0;
  - `span` „Strona {page+1} z {pages}” z cyframi tabelarycznymi;
  - drugi przycisk z `i-chevron-right` na końcu, wyłączony na ostatniej stronie.
- Przycisk z `busy` jest w toku („Wczytywanie…”), a drugi zostaje aktywny, jak w prototypie.

#### 6. Katalog, CLEANED i wzorce F-06

**File**:
- `szczur-radar-web/src/views/KitchenSinkView.vue`
- `szczur-radar-web/src/styleContract.test.ts`
- `szczur-radar-web/visual/__screenshots__/`

**Intent**: Każdy nowy komponent jest w katalogu we wszystkich wariantach i pod skanem literałów (`REPOSITORY_GUIDELINES.md`, „UI”).

**Contract**:
- Nowe sekcje, w kolejności arkusza:
  - „Stronicowanie”: pierwsza strona, strona środkowa z „Starsze” w toku, ostatnia strona, wariant „Poprzednie”/„Następne” na pierwszej stronie, i zdanie, że przy jednej stronie stronicowania nie ma;
  - „Ładowanie”: `rows`, `lines` i `form`, każdy w próbce z `aria-busy="true"`;
  - „Pusta lista”: filtry bez wyników (`i-search`, przycisk „Wyczyść filtry”), brak danych (`i-inbox`, bez działania), nic do zrobienia (`i-circle-check`);
  - „Błąd z ponowieniem”: domyślny i podczas ponawiania.
- Tytuły stanów w katalogu są `p` (Key Discoveries).
- W sekcji „Przycisk” dochodzi próbka z ikoną na końcu.
- Cztery nowe pliki są importowane `?raw` i dopisane do `CLEANED`.
- Nowe wzorce `kitchen-sink-{stronicowanie,ladowanie,pusta-lista,blad-z-ponowieniem}.png` i zmieniony `kitchen-sink-przycisk.png` w `light-375` i `dark-375` zapisuje `npm run visual:update`. Nowe kontrolki to `AppButton`, który ma już stany hover i focus, więc `STATES` bez zmian.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym:
  - `AppButton`: ikona na końcu;
  - `LoadingState`: trzy kształty, liczba wierszy, tekst dla czytnika;
  - `EmptyState`: element tytułu, slot działania;
  - `ErrorState`: `alert` bez ponawiania, brak `alert` i przycisk w toku podczas ponawiania, `retry`, `focus()`, ponowne wyrenderowanie po ponawianiu;
  - `PagerNav`: obie pary etykiet, wyłączenia na pierwszej i ostatniej stronie, brak przy jednej stronie, `busy`, zdarzenia;
  - skan `CLEANED`.
  Polecenie: `cd szczur-radar-web && npm test`.
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy wizualne i axe przechodzą z nowymi wzorcami katalogu: `cd szczur-radar-web && npm run visual:docker`
- Bez ruchu szkielet i znak pracy stoją. Agent sprawdza przez `playwright-cli` na `npm run dev` (`/kitchen-sink`) z `emulateMedia({ reducedMotion: 'reduce' })`, że `getComputedStyle` pierwszego paska szkieletu i znaku pracy ma `animation-name: none`, a z `no-preference`, że animacja jest ustawiona.
- W katalogu przy 320 px nowe sekcje nie przewijają strony w poziomie. Agent sprawdza `document.documentElement.scrollWidth <= 320` przez `playwright-cli`.

**Implementation Note**: Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy. Jedyna bramka człowieka to 4.7.

---

## Faza 2: Kolumna w trasie oraz stany listy, posta i przebiegu

### Overview

Szerokość kolumny widoku przechodzi do powłoki. Lista postów, strona posta i strona przebiegu przechodzą na wspólne stany. Lista dostaje stronicowanie z prototypu. Stany listy dostają wzorce F-06.

### Changes Required:

#### 1. Szerokość kolumny w trasie

**File**:
- `szczur-radar-web/src/router/index.ts`, `src/router/index.test.ts`
- `szczur-radar-web/src/components/AppShell.vue`, `AppShell.test.ts`
- `FeedView.vue`, `PostDetailView.vue`, `RunDetailView.vue`, `AdminView.vue`

**Intent**: Jedno miejsce na szerokość treści widoku zamiast czterech kopii kontenera z tym samym komentarzem.

**Contract**:
- `RouteMeta` dostaje `column?: PageColumn`, gdzie `type PageColumn = 'narrow' | 'wide'`:
  - `home`, `post` i `admin-run` mają `narrow` (48rem, od 1025 px 54rem);
  - `admin` ma `wide` (56rem, od 1025 px 63rem).
- `AppShell` owija `<RouterView />` w kontener z klasą według `route.meta.column`, wyśrodkowany, z dzisiejszymi szerokościami i jednym komentarzem o rozmiarze pisma. Bez `column` kontener jest na całą kolumnę powłoki.
- Widoki tracą reguły szerokości klas `.feed`, `.post-detail`, `.run-detail` i `.admin`. Same klasy korzeni zostają, bo podnoszą specyficzność reguł komunikatów ponad zerowy margines `StatusMessage`: `.admin .message` (`AdminView.vue:668`), `.run-detail .message, .attention, .refresh-error` (`RunDetailView.vue:329-331`) i `.post-detail .message` (`PostDetailView.vue:230`). Wzorzec `view-admin` nie ma komunikatów działań, więc kryterium 2.3 nie złapałoby ich zniknięcia.
- Testy routera: każda trasa powłoki ma `column`. Test `AppShell`: klasa kontenera według `meta.column`.
- Ta zmiana idzie pierwsza i osobno, zanim zmienią się stany widoków (kryterium 2.3).

#### 2. Lista postów

**File**: `szczur-radar-web/src/views/FeedView.vue`, `FeedView.test.ts`

**Intent**: Lista pokazuje stany z prototypów `state-loading`, `state-empty` i `state-error` oraz stronicowanie z arkusza.

**Contract**:
- Stany w kontenerze listy (`aria-busy="true"` w trakcie każdego wczytania):
  - pierwsze wczytanie i zmiana filtra: `LoadingState shape="rows"`;
  - błąd: `ErrorState title-tag="h2"` „Nie udało się wczytać postów”, a zdanie to „Serwer nie odpowiedział. Twoje filtry zostały.” przy włączonym filtrze, bez filtra „Serwer nie odpowiedział.”; „Spróbuj ponownie” wczytuje tę samą stronę i filtr, a podczas ponawiania komunikat zostaje z przyciskiem w toku: błąd trwa do odpowiedzi (dziś `load()` zeruje `failed` na starcie, `FeedView.vue:60`);
  - pusty wynik filtra: `EmptyState title-tag="h2" icon="i-search"` „Brak postów dla tych filtrów”, zdanie „Zmień filtr albo wyczyść go, żeby zobaczyć wszystkie posty.” i `AppButton` „Wyczyść filtry” (działanie i fokus jak dziś); linia wyniku zostaje „Żaden post nie pasuje do filtra.”;
  - pusta lista bez filtra: `EmptyState title-tag="h2" icon="i-inbox"` „Brak ocenionych postów” ze zdaniem „Pojawią się po najbliższej aktualizacji danych.”;
  - strona za ostatnią: `EmptyState title-tag="h2" icon="i-inbox"` „Nie ma strony {N}” ze zdaniem „Posty kończą się na stronie {M}.” i przyciskiem „Przejdź do strony {M}”;
  - lista: karty i `PagerNav` z `page`, `pages` i `busy`.
- Zmiana samej strony (przycisk, wstecz, dalej):
  - stara lista zostaje, a w toku jest przycisk w kierunku strony docelowej;
  - gdy na ekranie nie ma listy z `PagerNav` (np. wstecz po błędzie kolejnej strony albo „Przejdź do strony {M}” z pustego stanu „Nie ma strony {N}”), w trakcie wczytania jest `LoadingState shape="rows"`, a przewinięcie i fokus są jak przy każdej zmianie strony;
  - po wczytaniu widok przewija na górę i przenosi fokus na tytuł, przy powodzeniu i przy błędzie (Critical Implementation Details);
  - `loadingText` i „Wczytywanie strony N…” znikają.
- Raport `reportFeedLoaded` bez zmian w znaczeniu.
- Style `.loading`, `.empty`, `.posts .message` i `.pager` znikają.
- Testy widoku: każdy stan, stara lista z przyciskiem w toku przy zmianie strony, przewinięcie i fokus dopiero po wczytaniu, brak stronicowania przy jednej stronie, ponowienie z komunikatem w toku, fokus po kolejnej porażce na „Spróbuj ponownie”.

#### 3. Strona posta i strona przebiegu

**File**: `szczur-radar-web/src/views/PostDetailView.vue`, `PostDetailView.test.ts`, `src/views/RunDetailView.vue`, `RunDetailView.test.ts`

**Intent**: Oba widoki używają tych samych stanów co lista, a ponowienie wygląda jak w prototypie.

**Contract**:
- Strona posta:
  - ładowanie: `LoadingState shape="lines" :count="4"`;
  - błąd: `ErrorState title-tag="h2"` „Nie udało się wczytać posta”, „Serwer nie odpowiedział.”;
  - brak: `EmptyState title-tag="h2" icon="i-inbox"` „Nie ma takiego posta”, „Post nie istnieje albo nie został jeszcze oceniony.” i odnośnik „Przejdź do listy postów” w slocie `action`.
- Strona przebiegu:
  - ładowanie: `LoadingState shape="lines" :count="4"`;
  - błąd: `ErrorState title-tag="h2"` „Nie udało się wczytać przebiegu”, „Serwer nie odpowiedział.”;
  - brak: `EmptyState title-tag="h2" icon="i-inbox"` „Nie ma takiego przebiegu”, zdanie „Przebieg nie istnieje albo został usunięty z historii.” i odnośnik „Wróć do panelu admina”.
- Surowe dane posta:
  - ładowanie: `LoadingState shape="lines"`;
  - błąd: `ErrorState` „Nie udało się wczytać danych źródła”, „Ocena posta zostaje bez zmian.”;
  - fokus przy ponowieniu jak dziś (`PostDetailView.vue:93-96`).
- Ponowienie w obu widokach: komunikat zostaje z przyciskiem w toku do odpowiedzi. Fokus po powodzeniu na `h1`, po porażce na „Spróbuj ponownie”.
- „Nie udało się odświeżyć przebiegu…” zostaje w `StatusMessage`.
- Style `.loading` i `.empty` znikają, a `.message` zostaje tylko tam, gdzie jest jeszcze `StatusMessage`.
- Testy obu widoków: każdy stan i ponowienie.

#### 4. Istniejące testy E2E

**File**: `szczur-radar-web/e2e/feed.spec.ts`

**Intent**: Dzisiejsze testy stanów listy sprawdzają nowe teksty i role. Ich ryzyka się nie zmieniają.

**Contract**:
- „filtr bez wyników pozwala wrócić do pełnej listy”: nagłówek 2 „Brak postów dla tych filtrów” w `main`, potem „Wyczyść filtry” jak dziś (dwa przyciski).
- „ładowanie listy jest widoczne”: tekst „Wczytywanie…” w `main`. Kontener listy nie ma roli, więc bez selektora CSS nie da się go znaleźć; jego `aria-busy="true"` sprawdza `FeedView.test.ts`.
- „błąd listy pozwala ponowić wczytanie”: `alert` z nagłówkiem „Nie udało się wczytać postów” zamiast pełnej treści z kropką.
- „pusta lista mówi, kiedy pojawią się posty”: nagłówek „Brak ocenionych postów” i zdanie „Pojawią się po najbliższej aktualizacji danych.”.
- Nagłówek pliku przestaje mówić, że stany przebuduje S-48.

#### 5. Wzorce stanów listy

**File**: `szczur-radar-web/visual/targets.ts`, `szczur-radar-web/visual/__screenshots__/`

**Intent**: Stany listy z prototypów są pod tą samą bramką wyglądu i dostępności co widoki.

**Contract**:
- Nowe wpisy `VIEWS`, każdy z `freshnessLoaded()`:
  - `view-feed-loading`: `/`, `{ AUTH: 'user', MODE: 'loading' }`; gotowy, gdy w `main` widać tekst „Wczytywanie…” (bez selektora `aria-busy`, jak w E2E);
  - `view-feed-empty`: `/?q=zzz`, `{ AUTH: 'user' }`; gotowy przy nagłówku 2 „Brak postów dla tych filtrów” i linii wyniku „Żaden post nie pasuje do filtra.”;
  - `view-feed-error`: `/`, `{ AUTH: 'user', MODE: 'error' }`; gotowy przy `alert` z nagłówkiem „Nie udało się wczytać postów”.
- `npm run visual:update` zapisuje 12 nowych plików. Wzorce `view-feed`, `view-post`, `view-run` i `view-admin` się nie zmieniają (`view-feed` jest przycięty nad stronicowaniem).

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym router, `AppShell`, `FeedView`, `PostDetailView` i `RunDetailView`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Po samym przeniesieniu kolumny (zmiana 1, przed zmianami 2–5) testy wizualne przechodzą bez aktualizacji żadnego wzorca: `cd szczur-radar-web && npm run visual:docker`
- Testy E2E przechodzą, w tym poprawione testy stanów listy: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą z nowymi wzorcami stanów listy, a drugi przebieg bez zmian w kodzie też jest zielony: `cd szczur-radar-web && npm run visual:docker` (dwa razy)
- W widokach nie ma klas starego wzorca stanów ani kontenera szerokości: `! grep -nE 'class="(loading|empty)"|\.(feed|post-detail|run-detail) \{' szczur-radar-web/src/views/FeedView.vue szczur-radar-web/src/views/PostDetailView.vue szczur-radar-web/src/views/RunDetailView.vue`
- Powrót z posta do listy dalej przywraca pozycję i fokus na odnośniku posta. Agent sprawdza przez `playwright-cli` na buildzie produkcyjnym (`vite preview`) z atrapą: przewija na 3. ekran listy, otwiera post, cofa i porównuje `scrollY` oraz `document.activeElement`.

**Implementation Note**: Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy. Jedyna bramka człowieka to 4.7.

---

## Faza 3: Panel admina na wspólnych stanach

### Overview

Pięć sekcji panelu admina pokazuje szkielet, pustą listę i błąd z własnym ponowieniem, zgodnie z prototypami `admin-loading`, `admin-empty` i `admin-error` i z przepływem `admin-error` z kontraktu: błąd jednej części nie psuje pozostałych, a jej „Spróbuj ponownie” wczytuje tylko ją. S-50 przeniesie te sekcje do trzech części bez zmiany stanów.

### Changes Required:

#### 1. Sekcje panelu

**File**: `szczur-radar-web/src/views/AdminView.vue`, `AdminView.test.ts`

**Intent**: Każda sekcja ma te same stany co widoki i własne ponowienie. „Odśwież” nie udaje, że działa przed pierwszą odpowiedzią.

**Contract**:
- Każda sekcja ma `aria-busy="true"` na `section`, dopóki wczytuje się bez danych na ekranie. Tytuły stanów są `p`, bo sekcja ma swój nagłówek 2. Zdania błędów to „Pozostałe sekcje działają osobno.”, chyba że niżej stoi inaczej.
- „Nowy przebieg”:
  - do odpowiedzi o źródłach cały formularz zastępuje `LoadingState shape="form"`, jak karta w prototypie;
  - przy błędzie formularz zastępuje `ErrorState` „Nie udało się wczytać listy źródeł”, „Bez listy źródeł nie da się uruchomić przebiegu.”, z ponowieniem `loadSources`;
  - „Wpisz do formularza” z „Dni bez danych” przy braku formularza wpisuje zakres bez przenoszenia fokusu.
- „Dni bez danych”:
  - `LoadingState shape="lines"`;
  - błąd „Nie udało się wczytać dni bez danych”;
  - pusto: `EmptyState icon="i-circle-check"` „Brak dni bez danych”, „Każdy dzień od {od} do {do} ma dane.”
- „Jakość ocen (7 dni)”:
  - `LoadingState shape="lines"`;
  - błąd „Nie udało się wczytać jakości ocen”;
  - pusto: `EmptyState icon="i-inbox"` „Brak ocen w ostatnich {N} dniach”, „Miary pojawią się po ocenach z kolejnego przebiegu.”, pod linią okna jak dziś.
- „Ostatnie przebiegi”:
  - `LoadingState shape="rows"`;
  - błąd „Nie udało się wczytać przebiegów”;
  - pusto: `EmptyState icon="i-inbox"` „Jeszcze nie było przebiegu”, „Pierwszy uruchomisz w sekcji „Nowy przebieg”.”;
  - „Odśwież” jest wyłączone, dopóki przebiegi wczytują się pierwszy raz (kontrakt `admin-loading`).
- „Nieudane posty”:
  - `LoadingState shape="rows"`;
  - błąd „Nie udało się wczytać nieudanych postów”;
  - pusto: `EmptyState icon="i-circle-check"` „Brak nieudanych postów”, „Żaden post nie czeka na ponowienie ani nie jest martwy.”
- Ponowienie sekcji:
  - „Spróbuj ponownie” wywołuje tylko funkcję odczytu tej sekcji (`loadSources`, `loadMissing`, `loadQuality`, `loadRuns`, `loadFailed`);
  - błąd zostaje z przyciskiem w toku do odpowiedzi, także gdy ponowienie przyszło z „Odśwież”;
  - po powodzeniu fokus idzie na nagłówek 2 sekcji (`tabindex="-1"`), po porażce na „Spróbuj ponownie” (Critical Implementation Details); tylko po ponowieniu z przycisku sekcji, a po „Odśwież” fokus zostaje na miejscu.
- „Odśwież” dalej czyta wszystkie sekcje.
- Stara lista przy odświeżaniu zostaje na ekranie jak dziś.
- Komunikaty działań, błąd „Pokaż starsze” i podpowiedź startu zostają w `StatusMessage` i tekście.
- Testy widoku:
  - stany każdej sekcji;
  - ponowienie jednej sekcji bez żądań pozostałych;
  - fokus po ponowieniu;
  - fokus zostaje na „Odśwież”, gdy odświeżenie ponawia sekcję w błędzie;
  - „Odśwież” wyłączone przy pierwszym wczytaniu.

#### 2. Wzorce stanów panelu

**File**: `szczur-radar-web/visual/targets.ts`, `szczur-radar-web/visual/__screenshots__/`

**Intent**: Stany panelu z prototypów są pod bramką wyglądu i dostępności.

**Contract**:
- Nowe wpisy `VIEWS`, każdy z `freshnessLoaded()`:
  - `view-admin-loading`: `/admin`, `{ AUTH: 'admin', MODE: 'loading' }`; gotowy, gdy pięć regionów sekcji ma `aria-busy="true"`, a „Odśwież” jest wyłączone; dzwonek wisi w tym trybie, więc gotowość czeka tylko na jego przycisk, a nie na `bellLoaded()`;
  - `view-admin-empty`: `/admin`, `{ AUTH: 'admin', MODE: 'empty' }`; gotowy przy „Brak nieudanych postów” i „Jeszcze nie było przebiegu”, bez „Wczytywanie…”, z `bellLoaded()`;
  - `view-admin-error`: `/admin`, `{ AUTH: 'admin', QUALITY: 'error' }`; gotowy przy `alert` w regionie „Jakość ocen (7 dni)” z „Nie udało się wczytać jakości ocen”, przy wczytanych pozostałych sekcjach jak w `view-admin` i z `bellLoaded()`.
- `npm run visual:update` zapisuje 12 nowych plików. `view-admin` się nie zmienia.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą, w tym `AdminView`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Dotychczasowe testy E2E przechodzą: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą z nowymi wzorcami stanów panelu, a `view-admin` bez zmian; drugi przebieg bez zmian w kodzie też jest zielony: `cd szczur-radar-web && npm run visual:docker` (dwa razy)
- W panelu admina nie ma klas starego wzorca stanów: `! grep -nE 'class="(loading|empty)"' szczur-radar-web/src/views/AdminView.vue`

**Implementation Note**: Faza nie ma kryteriów Manual. Po zielonych bramkach Automated commit i przejście do następnej fazy bez przerwy. Jedyna bramka człowieka to 4.7.

---

## Faza 4: Testy E2E stanów i weryfikacja

### Overview

Przepływy stanów z kontraktu projektu (`ui-design.md`, wiersze `state-loading`, `state-empty`, `state-error`, `admin-loading`, `admin-empty`, `admin-error` oraz stronicowanie z `posts`) dostają testy w przeglądarce. Dokumentacja nazywa nowe komponenty. Agent sprawdza PR env, a właściciel ocenia zrzuty.

### Changes Required:

#### 1. Testy E2E stanów

**File**: `szczur-radar-web/e2e/states.spec.ts` (nowy), `context/changes/shared-view-states/evidence/e2e-breaks.md` (nowy, wyniki złamań z kryterium 4.4)

**Intent**: Każde ryzyko nowego zachowania, którego nie łapią poprawione testy z fazy 2, ma swój test (NFR `prd.md:328`).

**Contract**:
- Według reguł repozytorium: `test` i `expect` z `e2e/fixtures.ts`, lokatory po roli, etykiecie i tekście w `main` albo regionie sekcji, nazwy według ryzyka.
- Lista postów:
  - „kolejna strona wczytuje się z listą na ekranie”: `MODE: 'loading-next'`; klik „Starsze” daje przycisk „Wczytywanie…” w `navigation` „Strony” z `aria-busy="true"`, 50 artykułów nadal w `main` i adres `/?page=1`;
  - „błąd kolejnej strony pozwala ją ponowić”: `MODE: 'error-next'`; klik „Starsze” daje `alert` „Nie udało się wczytać postów” i fokus na nagłówku 1; „Spróbuj ponownie” wysyła żądanie strony 1 i `alert` wraca;
  - „błąd listy ponawia ten sam filtr”: `MODE: 'error'`, `/?q=orlen`; „Spróbuj ponownie” wysyła żądanie z `q=orlen`, a adres zostaje z filtrem;
  - „lista mieszcząca się na jednej stronie nie ma stronicowania”: `/?q=orlen` (20 postów w atrapie, `e2e/feed.spec.ts:6-8`); w `main` nie ma `navigation` „Strony”.
- Panel admina:
  - „każda sekcja panelu pokazuje szkielet do czasu swoich danych”: `MODE: 'loading'`; regiony „Nowy przebieg”, „Dni bez danych”, „Jakość ocen (7 dni)”, „Ostatnie przebiegi” i „Nieudane posty” mają `aria-busy="true"` i tekst „Wczytywanie…”, a „Odśwież” jest wyłączone;
  - „błąd jednej sekcji nie psuje pozostałych, a jej ponowienie wczytuje tylko ją”: `QUALITY: 'error'`; `alert` tylko w regionie „Jakość ocen (7 dni)”, a pozostałe sekcje mają dane; „Spróbuj ponownie” wysyła jedno żądanie `GET /api/admin/assessment-quality` i żadnego innego `/api/admin/` poza `/api/admin/notifications` dzwonka, który czyta niezależnie (licznik żądań w teście);
  - „błąd całego panelu daje ponowienie w każdej sekcji”: `MODE: 'error'`; pięć regionów z `alert` i przyciskiem „Spróbuj ponownie”;
  - „brak nieudanych postów to pusty stan bez listy”: `MODE: 'empty'`; region „Nieudane posty” z tekstem „Brak nieudanych postów” bez pól wyboru, region „Ostatnie przebiegi” z „Jeszcze nie było przebiegu”.

#### 2. Dokumentacja repozytorium i projektu

**File**: `REPOSITORY_GUIDELINES.md`, `context/foundation/ui-design.md`

**Intent**: Następny widok zaczyna od wspólnych stanów i szerokości z trasy, zamiast kopiować wzorzec.

**Contract**:
- `REPOSITORY_GUIDELINES.md`:
  - w „Project Structure”, w opisie `szczur-radar-web/`, przy powłoce dochodzi `meta.column` (szerokość kolumny widoku);
  - w „UI”, w „Shared components” dochodzą `LoadingState`, `EmptyState`, `ErrorState` i `PagerNav` z jednym zdaniem każdy, `AppButton` z `iconPosition`, oraz zdanie, że ładowanie, pusty stan, błąd wczytania i stronicowanie widoku to te komponenty, a widok nie stawia własnego kontenera szerokości.
- `ui-design.md`, „Mapa komponentów”: w wierszu S-48 nazwy komponentów z planu zamiast „nowe wspólne komponenty”. Treść projektu bez zmian.
- Bez nowych zasad poza tym.

#### 3. Weryfikacja w PR env i akceptacja wyglądu

**File**: opis PR, galeria jako Artifact (poza repozytorium), `context/changes/shared-view-states/evidence/pr-env/README.md` (nowy, tylko wyniki pomiarów)

**Intent**: Stany i stronicowanie działają na prawdziwym API. Właściciel ocenia wygląd na obrazkach obok prototypów (pamięć `ui-decisions-by-pictures`).

**Contract**:
- Galeria pokazuje:
  - `view-feed-{loading,empty,error}` obok `ui-design/screens/state-*`;
  - `view-admin-{loading,empty,error}` obok `admin-*`;
  - sekcje katalogu obok `components-*-375.png`;
  - jasny i ciemny motyw, 375 i 1280 px.
- Opis PR wymienia grupy wzorców: nowe `view-feed-*` i `view-admin-*`, nowe sekcje katalogu, zmieniony `kitchen-sink-przycisk`, oraz `view-*` bez zmian. Zawiera `Refs #180`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą: `cd szczur-radar-web && npm test`
- Testy E2E przechodzą, w tym `states.spec.ts`: `cd szczur-radar-web && npm run e2e`
- Testy wizualne i axe przechodzą: `cd szczur-radar-web && npm run visual:docker`
- Każdy nowy test E2E jest czerwony na swoim złamaniu. Agent łamie kod raz na test, uruchamia `npx playwright test e2e/states.spec.ts -g "<nazwa>"`, sprawdza czerwień i cofa zmianę. Wyniki trafiają do `evidence/e2e-breaks.md`. Przykłady złamań:
  - ponowienie sekcji wywołuje `refresh()`;
  - `PagerNav` rysuje się przy jednej stronie;
  - zmiana strony zdejmuje listę.
- CI na PR jest zielone, w tym `web-e2e` (krok wizualny) i `web-container-smoke`: `gh pr checks`
- W PR env lista i panel działają na prawdziwym API. Po wdrożeniu sprawdzanego commita agent wykonuje kontrolę w Chromium przez `playwright-cli`:
  1. Zakłada w PR env jednorazowe konto `--role admin`, aktywuje je i uzyskuje ciasteczko sesji według procedury z `lessons.md` („W PR env agent sam zakłada jednorazowe konto admina…”). Link, token, hasło i ciasteczko są tylko w zmiennych powłoki i w pliku w scratchpadzie, usuniętym po kontroli.
  2. Otwiera `/`. Lista wczytuje się bez „Wczytywanie…”. `navigation` „Strony” jest wtedy i tylko wtedy, gdy `GET /api/posts?page=0&size=50` (przez `curl` z ciasteczkiem) ma `total > 50`. Jeśli jest, klik „Starsze” daje adres `/?page=1` i tekst „Strona 2 z …”.
  3. Otwiera `/?q=zzzzzz` i potwierdza nagłówek 2 „Brak postów dla tych filtrów”.
  4. Otwiera `/admin` i potwierdza, że w pięciu sekcjach nie ma `aria-busy="true"` ani `alert`, a „Odśwież” jest aktywne.
  5. Przy 375 px `scrollWidth <= 375` na `/` i `/admin`.
  Wyniki pomiarów (bez sekretów) trafiają do `evidence/pr-env/README.md`.

#### Manual Verification:

- Właściciel akceptuje wygląd stanów i stronicowania w galerii zrzutów obok prototypów (jasny i ciemny, 375 i 1280 px)

---

## Testing Strategy

### Unit Tests:

- `LoadingState`: kształty, liczba, tekst dla czytnika, brak roli i `aria-busy` na komponencie.
- `EmptyState`: `h2`/`p`, `titleId`, zdanie, slot działania.
- `ErrorState`: `alert` tylko bez ponawiania, przycisk w toku z „Wczytywanie…”, `retry`, `focus()`, ponowne wyrenderowanie po ponawianiu.
- `PagerNav`: etykiety `time` i `order`, wyłączenia na krańcach, brak przy `pages <= 1`, `busy`, zdarzenia, ikony po właściwej stronie.
- `AppButton`: `iconPosition`.
- Router i `AppShell`: `meta.column` każdej trasy powłoki i klasa kontenera.
- `FeedView`, `PostDetailView` i `RunDetailView`: każdy stan, ponowienie z komunikatem w toku i fokus. W `FeedView` także zmiana strony ze starą listą, przewinięcie i fokus po wczytaniu, brak stronicowania przy jednej stronie i strona za ostatnią.
- `AdminView`: stany pięciu sekcji, ponowienie jednej bez żądań pozostałych, „Odśwież” wyłączone przy pierwszym wczytaniu, fokus.

### Integration Tests:

- E2E: poprawione testy stanów w `feed.spec.ts` (faza 2) i nowy `states.spec.ts` (faza 4), każdy złamany raz.
- F-06:
  - 4 nowe sekcje katalogu i `kitchen-sink-przycisk` (375 px);
  - 6 nowych widoków stanów w czterech projektach;
  - axe na każdym;
  - `view-*` bez zmian.

### Manual Testing Steps:

1. Właściciel ogląda galerię zrzutów stanów obok prototypów i akceptuje wygląd (faza 4).

## Performance Considerations

Bez nowych żądań. Ponowienie sekcji panelu wysyła jedno żądanie zamiast pięciu („Odśwież”). Szkielet to kilka pustych elementów. Puls działa tylko z ruchem i nie dotyka układu, bo zmienia `opacity`.

## Migration Notes

Brak zmian danych i API. Wdrożenie web jest niezależne od API.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-48), issue #180
- Projekt: `context/foundation/ui-design.md` („Stany”, „Sterowanie” → „Stronicowanie”, „Stany widoku”, „Ruch”, „Kontrakt dla testów E2E”, „Mapa komponentów”)
- Prototypy: `ui-design/prototypes/components.html:600-617, 840-887`, style `prototypes/prototype.css:942-948, 1031-1038, 1520-1619, 2395-2398`, ekrany `prototypes/screens/state-{loading,empty,error}.html` i `admin-{loading,empty,error}.html`
- PRD: FR-007, FR-009, NFR `prd.md:328`
- Audyt: `context/changes/post-m4-ui-readiness-audit/research.md` (sekcja 1, „Widoki”)
- Wcześniejsze zmiany:
  - powłoka i `PageHeader`: `context/archive/2026-11-22-app-shell-navigation/plan.md`;
  - komponenty F-08: `context/archive/2026-11-21-design-tokens-and-components/plan.md`;
  - stany aktywacji poza S-48: `context/archive/2026-11-22-login-on-shared-foundation/plan-brief.md`
- Wzorce do naśladowania:
  - zachowanie listy i przywracanie pozycji: `szczur-radar-web/src/views/FeedView.vue:55-142`, `src/router/scroll.ts`;
  - odczyty sekcji: `src/views/AdminView.vue:60-404`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Komponenty stanów i stronicowania

#### Automated

- [x] 1.1 Testy jednostkowe przechodzą, w tym cztery nowe komponenty, AppButton i skan CLEANED
- [x] 1.2 Typecheck i build przechodzą
- [x] 1.3 Testy wizualne i axe przechodzą z nowymi wzorcami katalogu
- [x] 1.4 Bez ruchu szkielet i znak pracy stoją, z ruchem są animowane (kontrola agenta przez playwright-cli)
- [x] 1.5 W katalogu przy 320 px nowe sekcje nie przewijają strony w poziomie

### Phase 2: Kolumna w trasie oraz stany listy, posta i przebiegu

#### Automated

- [ ] 2.1 Testy jednostkowe przechodzą, w tym router, AppShell, FeedView, PostDetailView i RunDetailView
- [ ] 2.2 Typecheck i build przechodzą
- [ ] 2.3 Po samym przeniesieniu kolumny testy wizualne przechodzą bez aktualizacji wzorców
- [ ] 2.4 Testy E2E przechodzą, w tym poprawione testy stanów listy
- [ ] 2.5 Testy wizualne i axe przechodzą z nowymi wzorcami stanów listy, dwa przebiegi zielone
- [ ] 2.6 W widokach nie ma klas starego wzorca stanów ani kontenera szerokości
- [ ] 2.7 Powrót z posta do listy przywraca pozycję i fokus na odnośniku posta

### Phase 3: Panel admina na wspólnych stanach

#### Automated

- [ ] 3.1 Testy jednostkowe przechodzą, w tym AdminView
- [ ] 3.2 Typecheck i build przechodzą
- [ ] 3.3 Dotychczasowe testy E2E przechodzą
- [ ] 3.4 Testy wizualne i axe przechodzą z nowymi wzorcami stanów panelu, view-admin bez zmian, dwa przebiegi zielone
- [ ] 3.5 W panelu admina nie ma klas starego wzorca stanów

### Phase 4: Testy E2E stanów i weryfikacja

#### Automated

- [ ] 4.1 Testy jednostkowe przechodzą
- [ ] 4.2 Testy E2E przechodzą, w tym states.spec.ts
- [ ] 4.3 Testy wizualne i axe przechodzą
- [ ] 4.4 Każdy nowy test E2E jest czerwony na swoim złamaniu (evidence/e2e-breaks.md)
- [ ] 4.5 CI na PR jest zielone, w tym web-e2e i web-container-smoke
- [ ] 4.6 W PR env lista i panel działają na prawdziwym API (kontrola agenta przez playwright-cli)

#### Manual

- [ ] 4.7 Właściciel akceptuje wygląd stanów i stronicowania w galerii zrzutów obok prototypów
