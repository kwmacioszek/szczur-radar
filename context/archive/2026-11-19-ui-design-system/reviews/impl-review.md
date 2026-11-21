<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: F-07: Projekt UI/UX całej aplikacji (ui-design-system)

- **Plan**: context/changes/ui-design-system/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-20
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 5 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | FAIL |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Kryteria automatyczne wszystkich faz uruchomione 2026-11-20 na 9129520, każde z kodem 0:

- `inventory` wariantów i `screens.json`, oba także z `--gallery`, oraz z `--assets`;
- `contrast` dla `directions/{a,b,c}`, dla `tokens.css` (63 pary) i dla `style.css` aplikacji z `app-pairs.json` (19 par);
- `doc --stage 1–4`;
- `pages` na 17 stronach wariantów, na arkuszu komponentów i na 19 ekranach, w obu motywach przy 1280, 375 i 320 px;
- `selftest`: 8/8, w tym 6 celowych złamań;
- brak zmian w `szczur-radar-web/` i `szczur-radar-api/`;
- SVG roadmapy zgodne ze skryptem;
- `grep` zdania o E2E w roadmapie, wskazania w wytycznych i wiersza `**Otwarte blokujące:** 0`.

CI w PR #198: 11/11 zielonych. Wiersze Manual 1.10 i 3.7 mają dowody w `directions/directions.md` („Decyzja właściciela”), w `ui-design.md` („Decyzja właściciela”, „Jest okej”) i w komentarzach w #196. Budżety: kroje 204 kB (z 300 kB), PNG 11,3 MB (z 15 MB).

Rzeczy spoza planu są zapisane i nie są ustaleniami:

- warianty `d/`, `e/` i `round-1/`: `directions.md`, commit 03fb73f;
- skrypty galerii: `change.md`;
- `inline-sprite.mjs`: README narzędzia i wytyczne repo;
- kroje zmienne zamiast pliku na grubość: `ui-design.md:267`;
- trzecia rodzina JetBrains Mono i 6 własnych ikon: `ui-design.md:265` i `:350`;
- kadr okna dla zrzutów `post` i `target`: `ui-design.md:168`;
- nowe znaczenie `--border`: `ui-design.md:651`;
- jeden układ z panelem na życzenie zamiast paneli obok siebie: „Decyzja właściciela”;
- klocki udziału stanowisk na przeglądzie dnia, które wychodzą poza FR-039: oznaczone jako doprecyzowanie na prośbę właściciela w `ui-design.md:9` i `:740`.

## Findings

### F1 — Ekrany bez posta bez oceny, posta nieinwestycyjnego i stanu „Wszystkie”

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: context/foundation/ui-design/prototypes/screens/posts.html (i pozostałe ekrany z `screens.json`)
- **Detail**: Kontrakt treści fazy 3 (`plan.md:215`) wymaga na ekranach „wpis bez oceny z odsiewu” i „wpis nieinwestycyjny”. Żaden z 19 ekranów ich nie ma: `grep` „bez oceny|nieinwestycyjn|ocena nieudana” w `prototypes/screens/` daje 0 trafień. Każdy ekran z przełącznikiem ma wybrane „Inwestycyjne”, więc stanu „Wszystkie” nie pokazuje żaden. Te warianty są tylko w arkuszu: `components.html#c-wiersz-posta` („czeka na ocenę”, „nie inwestycyjny”, „ocena nieudana”) i `c-panel` („Post bez oceny”). Odstępstwo nie jest nigdzie zapisane, a przegląd heurystyczny go nie zgłosił. Skutek: S-51 i S-52 (domyślne ukrywanie z przełącznikiem) nie mają prototypu ekranu, choć wytyczne repo każą budować ekran według prototypu.
- **Fix A ⭐ Recommended**: Dodać do `screens.json` ekran `posts-all`: kopię `posts.html` z wybranym „Wszystkie” i liczbami obu zakresów, z wierszami „nie inwestycyjny”, „czeka na ocenę” i „ocena nieudana”, a jeden z nich otwarty w panelu jako „Post bez oceny”. Do tego cztery zrzuty, `pages`, wiersz w tabeli „Ekrany” i w kontrakcie E2E oraz ponowna publikacja galerii.
  - Strength: spełnia kontrakt fazy 3 i daje S-51/S-52 prototyp, którego wymagają wytyczne („A UI slice builds its screen after its prototype”). Komponenty są już w arkuszu, więc to składanie ekranu, a nie projektowanie.
  - Tradeoff: nowy ekran po akceptacji „Jest okej”, więc właściciel powinien zobaczyć zrzuty. Około 15 min pracy i kontroli. Galeria urośnie o około 0,3 MB (dziś 11,7 z 16 MB).
  - Confidence: MED — wszystkie elementy istnieją; nie sprawdzono, czy `doc --stage 3/4` wymaga dodatkowych wpisów.
  - Blind spot: nie wiadomo, jak wiersz bez celów wygląda w ramce dnia między wierszami z celami; może wymagać drobnej poprawki CSS.
- **Fix B**: Zapisać odstępstwo w `ui-design.md`. W „Ekrany” zdanie, że wiersze bez oceny, nieinwestycyjne i z nieudaną oceną oraz panel „Post bez oceny” pokazuje arkusz komponentów. Do tego, że stan „Wszystkie” to ten sam ekran `posts` z innymi liczbami. To samo zdanie w kontrakcie E2E przy S-52.
  - Strength: bez nowych zrzutów i bez ponownego pokazywania właścicielowi; arkusz ma wszystkie warianty wiersza.
  - Tradeoff: S-52 zbuduje stan, którego nikt nie widział w kontekście ekranu, a kontrakt fazy 3 zostaje niespełniony, tylko jawnie.
  - Confidence: HIGH — edycja dwóch akapitów.
  - Blind spot: nie wiadomo, czy właściciel chce zobaczyć „Wszystkie” przed S-52.
- **Decision**: FIXED (Fix A, poprawiony zakres).
  - Nowy ekran `posts-all` jest w `screens.json`, w tabeli „Ekrany”, w kontrakcie E2E i w galerii (opublikowana ponownie pod tym samym adresem). Przy „Decyzji właściciela” jest notka, że ekran powstał po akceptacji. Ma 4 zrzuty całej strony; kontrole przeszły z kodem 0: `pages`, `inventory` z `--gallery`, `doc --stage 3/4`.
  - Korekta wobec pierwotnego opisu: lista użytkownika ma tylko posty ocenione (`PostController`: „assessed posts”, FR-007: „przetworzone posty”). Ekran pokazuje więc post nieinwestycyjny i post odsiany przed modelem (`Prefilter.noContentOutput`: flaga „za mało treści”, bez celów), a nie wiersze „czeka na ocenę” i „ocena nieudana”, które należą do panelu admina. Panelu bocznego nie ma, żeby stan był widoczny także przy 375 px.
  - Właściciel ogląda ekran w galerii.

### F2 — `contrast` zaokrągla wynik przed porównaniem z progiem

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/design-check/design-check.mjs:204-205
- **Detail**: Kod robi `row[theme] = ratio.toFixed(2)`, a potem porównuje `Number(row[theme]) < pair.min`. Para 4,4994:1 daje „4.50” i przechodzi (agent potwierdził: `#118a11` na `#ffffff`, kod 0). `styleContract.test.ts` porównuje surowy wynik (`toBeGreaterThanOrEqual(4.5)`) i na tej parze byłby czerwony. Jest to sprzeczne z obietnicą README, że skrypt czyta tokeny tak jak test aplikacji. Dziś żadna para nie leży bliżej niż 0,01 od progu.
- **Fix**: Porównywać surowe `ratio < pair.min` i zaokrąglać tylko do wyświetlenia, a do `fixtures/broken/` dodać próbkę 4,499:1.
- **Decision**: FIXED.
  - `design-check.mjs` porównuje teraz dokładny wynik.
  - Nowa próbka `fixtures/broken/near-threshold/` (`#118a11` na `#ffffff`, 4,4994:1) jest w pętli `selftest` i w README. `selftest` przechodzi 9/9.
  - `contrast` dla tokenów projektu, `style.css` aplikacji i wariantów a–e kończy się kodem 0.

### F3 — Tekst na gradiencie tła jest poza kontrolą kontrastu

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: context/foundation/ui-design/prototypes/prototype.css:87-92, :670-682; tools/design-check/design-check.mjs:382-387
- **Detail**:
  - `pages` liczy tylko `violations` axe. Węzły, których tła axe nie ustali, trafiają do `incomplete` i przechodzą (tak samo w `visual/axe.ts`).
  - Tło `body` to radialny gradient akcentu, a za `.app-bar` stoi dekoracyjny pseudo-element.
  - Przez to axe nie ocenił kontrastu około 47% węzłów na `components.html` (295 z 621) i około 20% na `posts.html` (18 z 88). Wśród nich są marka, odnośniki nawigacji i data danych w nagłówku.
  - Zmierzone tło pod nagłówkiem (jasny motyw, 1280 px) to `rgb(222,232,245)` zamiast `--bg` `rgb(238,242,248)`.
  - Kontrast na tym tle: `--destructive` 4,55, `--accent` 4,66, `--text-muted` 4,71. Wszystkie przechodzą, ale o włos i bez strażnika.
  - `pairs.json` zakłada płaskie `--bg`, a `ui-design.md:54` obiecuje 4,5:1 „na każdym tle, na którym występuje”.
  - F-08 przeniesie tę lukę do bramy F-06.
- **Fix A ⭐ Recommended**: Najciemniejszy punkt gradientu zapisać jako rolę hex w `tokens.css` w obu motywach (np. `--bg-glow`), budować z niej gradient i dodać pary tekstu nagłówka na tej roli do `pairs.json`.
  - Strength: `contrast`, a po F-08 także `styleContract.test.ts`, pilnują wprost najgorszego tła; mieści się w formacie tokenów z „Critical Implementation Details”.
  - Tradeoff: nowa rola tła do przeniesienia w F-08; trzeba wyznaczyć wartość dla motywu ciemnego.
  - Confidence: MED — wartość pochodzi z pomiaru pikseli, nie z rachunku na `color-mix`.
  - Blind spot: pseudo-element za `.app-bar` może dawać inne tło niż gradient `body`.
- **Fix B**: `pages` zgłasza liczbę wyników `incomplete` reguły `color-contrast`, jako naruszenie albo jako ostrzeżenie z progiem, a README opisuje lukę.
  - Strength: łapie każde przyszłe tło, którego axe nie oceni, nie tylko ten gradient.
  - Tradeoff: jako naruszenie da dziś setki czerwonych węzłów i wymusi usunięcie gradientów albo listę wyjątków; jako ostrzeżenie łatwo je przeoczyć.
  - Confidence: LOW — axe zwraca `incomplete` także z innych powodów; nie wiadomo, ile zostanie po zmianie tła.
  - Blind spot: polityka `visual/axe.ts` w aplikacji zostaje bez zmian, chyba że F-08 ją zmieni.
- **Decision**: FIXED (Fix A).
  - Środek gradientu przy 12% akcentu (`#d3e1f2`) nie spełniał 4,5:1 dla 4 ról w jasnym motywie, m.in. `--destructive` 4,24. Przechodził tylko widoczny fragment.
  - Nowa rola `--bg-glow` w `tokens.css`: `#dee8f5` w jasnym motywie (7% akcentu) i `#14263b` w ciemnym (12%, bez zmian). Gradient w `prototype.css` startuje od tej roli.
  - W `pairs.json` jest 10 nowych par na `--bg-glow`, razem 73; wszystkie przechodzą, minimum 4,55. Celowe złamanie (powrót do `#d3e1f2`) daje 4 naruszenia i kod 1.
  - `ui-design.md`: rola w tabeli kolorów, 73 pary, nowa tabela par najbliżej progu, `--bg-glow` w mapie nowych tokenów i wyjaśnienie luki axe `incomplete`.
  - Zrzuty: 42 jasne zrobione ponownie; 34 się zmieniły, 6 wyszło identycznych, 2 to nowe `posts-all`. Ciemne zostały, bo różnica mieści się w szumie palety (średnio 0,1 poziomu).
  - `pages` na 21 stronach przechodzi. Galeria opublikowana ponownie.

### F4 — `selftest` nie dowodzi, że kontrola krojów działa

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/design-check/design-check.mjs:845-846, :350-356; tools/design-check/fixtures/broken/system-font.html; tools/design-check/README.md:45
- **Detail**:
  - Strony `ok/` idą z `skipFonts`, więc kontrola krojów nie ma zielonej ścieżki.
  - `broken/system-font.html` nie ma `@font-face`, więc wyzwala naraz obie gałęzie `font`: „brak własnego kroju” i „krój systemowy narysował tekst”. Warunek `some(kind==='font')` przejdzie na którejkolwiek z nich.
  - W efekcie zepsuta gałąź dla pojedynczego elementu (np. `glyphCount` zawsze 0) nie zrobi `selftest` czerwonym.
  - `README.md:45` mówi, że repo nie ma pliku kroju, a ta zmiana dodała sześć plików `woff2`.
  - Agent sprawdził ręcznie, że gałąź działa dziś: na stronie próbnej wykryła krój Courier.
- **Fix**: Dać `ok/page.html` deklarację `@font-face` na `context/foundation/ui-design/fonts/hanken-grotesk-*.woff2` z włączoną kontrolą krojów. Dodać `broken/mixed-font.html` (krój projektu i jeden element w `monospace`) sprawdzany na komunikat o elemencie. Poprawić `README.md:45`.
- **Decision**: FIXED.
  - Nowy `fixtures/font.css` zawiera `@font-face` Hanken Grotesk z plików repo; korzystają z niego `ok/page.html` (kontrola krojów jest teraz włączona) i nowa próbka `broken/mixed-font.html`.
  - `selftest` sprawdza próbki z krojami po treści komunikatu: `system-font.html` musi dać „no custom (@font-face) font was used”, a `mixed-font.html` „drawn with the system font” dla tego jednego elementu. Przechodzi 10/10.
  - Celowe złamanie (gałąź dla elementu wyłączona przez `glyphCount > 1e9` w tymczasowej kopii skryptu) daje czerwony `selftest` z kodem 1.
  - README poprawione.

### F5 — Żywy dokument projektu ma nieaktualne i niespójne miejsca

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/ui-design.md:13-20, :38, :370, :784; tabela „Ekrany”
- **Detail**: `ui-design.md` czytają po archiwizacji F-08 i każdy wycinek. Nieaktualne lub niespójne są w nim:
  - tabela „Stan sekcji” (`:13-20`) opisuje stan z fazy 1 („po wyborze wariantu”, „faza 3”);
  - `:38` mówi, że właściciel wybiera „spośród trzech wariantów”, a były dwie rundy i układ `e/`;
  - `:784` datuje decyzję o wyglądzie na 2026-11-20, a `directions.md:321` i komentarz w #196 na 2026-11-19;
  - `:370` nazywa `rotate-ccw` „Przywróć martwe”, a `icons/README.md` „Przywróć zaznaczone”, bo poprawka H-22 trafiła tylko do dokumentu;
  - kolumny FR tabeli „Ekrany” różnią się od `screens.json` w kilku wierszach: FR-036 przy `state-empty`, FR-033 przy `admin-error` i FR-013/015/040 przy `posts` są tylko w `screens.json`. Plan (faza 3) wymaga zgodności, a `doc --stage 3` porównuje tylko odnośniki.
- **Fix**: W jednej poprawce dokumentów: usunąć albo zaktualizować „Stan sekcji”, poprawić `:38` i datę w `:784`, ujednolicić etykietę `rotate-ccw` w `icons/README.md` i zrównać kolumny FR w „Ekrany” ze `screens.json`.
- **Decision**: FIXED.
  - Usunięta tabela „Stan sekcji”.
  - Bolączka „Surowy wygląd”: zamiast „spośród trzech wariantów” jest „po dwóch rundach wariantów”.
  - Decyzja o wyglądzie ma datę 2026-11-19, zgodną z `directions.md` i #196.
  - `icons/README.md` ma etykietę „Przywróć martwe”, jak prototyp i poprawka H-22. Cel wiersza „Panel admina: Nieudane posty” brzmi teraz „ponowić zaznaczone i przywrócić martwe”.
  - W kolumnach FR „Ekrany” brakowało FR obecnych w `screens.json`; dopisano je w 4 wierszach. Skrypt porównujący każdy wiersz z sumą FR jego ekranów nie znajduje już różnic.
  - `doc --stage 1–4` i `inventory --assets` kończą się kodem 0.

### F6 — Galeria ładuje kroje z Google Fonts

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: context/changes/ui-design-system/gallery/index.html:5
- **Detail**: „What We're NOT Doing” wyklucza zewnętrzny CDN krojów. Galeria ładuje Inter i Space Grotesk z fonts.googleapis.com. To strona przeglądu dla właściciela, a reguły Artifact pozwalają na Google Fonts, więc jedynym skutkiem jest zapytanie do zewnętrznego hosta przy otwarciu kopii z repo. Nie jest to zapisane. Prototypy i warianty nie ładują niczego z zewnątrz; jest tylko zwykły `href` do wykop.pl.
- **Fix**: Dopisać w `change.md` zdanie, że galeria jako strona przeglądu korzysta z Google Fonts, albo zamienić jej kroje na `system-ui`.
- **Decision**: FIXED. `change.md` w punkcie „Galeria” mówi, że galeria jest stroną przeglądu i bierze kroje z Google Fonts (dozwolone w Artifact). Zakaz zewnętrznego CDN dotyczy prototypów i zasobów projektu. Przy okazji rozmiar galerii zaktualizowany na 12,0 MB.

### F7 — `prototype.css` ma wartości spoza skali

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/foundation/ui-design/prototypes/prototype.css:160, :597, :900
- **Detail**: Nagłówek pliku powołuje się na zasady listy `CLEANED`, a mimo to plik ma `margin: -1px` (`:160`), `gap: 2px` (`:597`) i `margin-bottom: -1px` (`:900`). Te wartości wywołają skan `OFF_SCALE` w `styleContract.test.ts`, gdy F-08 przeniesie te reguły do pliku z listy. `inventory --assets` sprawdza tylko literały kolorów, więc tego nie łapie.
- **Fix**: Zamienić na tokeny skali (np. `--space-0`) albo wypisać je jako wyjątki do rozstrzygnięcia w sekcji „Wdrożenie”.
- **Decision**: FIXED.
  - `.bar` ma `gap: var(--space-0)`. To 0,125 rem przy `html { font-size: 100% }`, czyli te same 2 px, więc zrzuty się nie zmieniają.
  - Dwie wartości `-1px` są zapisane w „Wdrożenie” przy wierszu „lista `CLEANED`”:
    - `.visually-hidden` trafia do globalnego `style.css`, którego lista nie obejmuje;
    - `.subnav a` F-08 zapisuje bez literału.
  - `doc --stage 4` i `inventory --assets` kończą się kodem 0.

### F8 — Zapisy w folderze zmiany: daty, brakujące tabele, SHA w Progress

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/ui-design-system/directions/directions.md:308; context/changes/ui-design-system/plan.md:365
- **Detail**: Folder zmiany po `/10x-archive` jest niezmienny, więc to ostatnia chwila na te poprawki:
  - `directions.md:308` datuje wynik kontroli stron na 2026-11-19, a wynik liczy strony `e/` z 2026-11-20;
  - `e/` nie ma tabeli kontrastu, choć jego `pairs.json` przechodzi;
  - runda 1 nie ma opisów, a plik nie mówi, że „Papier” to `round-1/a`;
  - wiersz Progress 1.10 wskazuje ef6f7eb, choć ostateczna decyzja o układzie weszła w 03fb73f;
  - warianty używają nicku `prosty_inwestor` (8 plików). Nie ma go w fixture'ach, ale wygląda na prawdziwy nick, a prototypy końcowe używają `konto_przykladowe`. Ryzyko jest niskie.
  - H-11 i H-25–H-28 zostają „otwarte” w `heuristic-review.md`. Powód wyjaśnia `change.md:17`, a poprawki zostały sprawdzone.
- **Fix**: Przed `/10x-archive` dopisać `— 03fb73f` przy 1.10, poprawić datę w `directions.md:308` i dodać tabelę kontrastu `e/` z jednym zdaniem o rundzie 1. Zamiana nicku jest opcjonalna, bo wymaga ponownych zrzutów `stream` wariantów.
- **Decision**: FIXED (bez zamiany nicku).
  - Progress 1.10 kończy się na `— ef6f7eb, 03fb73f`.
  - `directions.md`:
    - nowa sekcja „Kontrast „Kanał”” z tabelą 44 par z narzędzia (najniżej 4,62 w jasnym i 5,09 w ciemnym);
    - data wyniku kontroli stron 2026-11-20;
    - runda 1 wskazuje katalogi `round-1/a/`, `round-1/b/` i `round-1/c/` w kolejności nazw („Papier” to `a/`).
  - Nick `prosty_inwestor` został, zgodnie z decyzją: ryzyko niskie, a zamiana wymagałaby nowych zrzutów.

### F9 — Konwencje narzędzia odbiegają od sąsiednich `tools/`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/design-check/README.md:12, :28; tools/design-check/design-check.mjs:892; context/foundation/ui-design/icons/README.md:26-31
- **Detail**:
  - README jest po angielsku, a README sąsiednich narzędzi (`deploy-gate`, `healthchecks`, `pipeline-trigger`) są po polsku.
  - Nieoczekiwany wyjątek kończy się kodem 1, tym samym co naruszenia (`README.md:12`). Przykłady: `%zz` w odnośniku dokumentu albo katalog zamiast `tokens.css`. `deploy-gate` daje w takim przypadku 2.
  - README (`:28`) podaje wysokość okna 900, a przy 375 px skrypt używa 812 (`design-check.mjs:33`).
  - `icons/README.md` daje 6 własnym ikonom „licencję repozytorium”, a repo nie ma pliku LICENSE.
- **Fix**: Dać kod 2 dla błędu wykonania (`try/catch` w punkcie wejścia), poprawić wysokość okna w README i nazwać wprost licencję własnych ikon (np. ISC jak Lucide). Język README jest do decyzji.
- **Decision**: FIXED (README zostaje po angielsku).
  - Nieoczekiwany błąd kończy się kodem 4 z wypisanym stosem, a nie 2, bo 2 i 3 już znaczą błąd użycia i brak środowiska. Kod 1 znaczy tylko naruszenia. Sprawdzone: `%zz` w odnośniku daje 4, nieznane polecenie 2, `selftest` 0.
  - README: dopisany kod 4; okna 1280x900, 375x812 i 320x900.
  - Własne ikony mają licencję ISC (jak Lucide) w `icons/README.md`, także wiersz `sprite.svg`, oraz w `ui-design.md` (sekcja ikon).

### F10 — Skrypty pomocnicze są kruche na brzegach, a `pages` nie blokuje sieci

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/design-check/inline-sprite.mjs:26, :32; tools/design-check/design-check.mjs:276; context/changes/ui-design-system/gallery/inline.py:22, :34, :43; context/changes/ui-design-system/gallery/quantize.mjs:5
- **Detail**:
  - `inline-sprite.mjs`: gdy zgubi się `<!--/sprite-->`, wstawia drugi sprite ze zdublowanymi `id` i kończy się kodem 0, a `pages` dalej przechodzi. Zamiana zwykłym tekstem zinterpretowałaby `$&` i `$'`, gdyby trafiły do sprite'a.
  - `pages` nie blokuje żądań sieciowych, więc prototyp ładujący CDN przejdzie, choć plan to wyklucza.
  - `inline.py`: `int | None` (`:22`) nie działa na `/usr/bin/python3` 3.9 z macOS; `assert` (`:43`) znika przy `-O`; wyrażenie regularne `[a-e]` (`:34`) pominie przyszły wariant `f`.
  - `quantize.mjs:5` opisuje opcję `--min-bytes`, której nie ma.
  - Dziś wszystko działa: sprite jest idempotentny na 20 stronach.
- **Fix**: W `inline-sprite.mjs` zgłaszać błąd przy znaczniku otwierającym bez zamykającego i użyć funkcji jako zamiennika. W `pages` dodać `context.route('**', …)`, które zgłasza każde żądanie spoza `file:`. Skrypty galerii (materiał jednorazowy) można zostawić.
- **Decision**: FIXED (skrypty galerii bez zmian).
  - `inline-sprite.mjs`:
    - przy zepsutych znacznikach (otwierający bez zamykającego obok istniejącego sprite'a albo znacznik dwa razy) zostawia stronę bez zmian i kończy się kodem 1;
    - zamiennikiem jest funkcja.
  - Sprawdzone na kopiach: zwykła strona zostaje bez zmian (kod 0); zgubiony znacznik daje kod 1, a plik jest nietknięty; pierwsze uruchomienie daje stronę identyczną z prawdziwą. Na 20 ekranach sprite bez zmian.
  - `pages` odrzuca i zgłasza (`network`) każde żądanie spoza `file:`. Nowa próbka `broken/external-request.html` była czerwona przed wprowadzeniem blokady i jest zielona po niej. `selftest` przechodzi 11/11.
  - `pages` przechodzi na 21 stronach prototypów i 17 stronach wariantów.
  - README narzędzia i `REPOSITORY_GUIDELINES.md` opisują nową kontrolę.
