# S-48: wspólne stany widoków i stronicowanie — Plan Brief

> Full plan: `context/changes/shared-view-states/plan.md`

## What & Why

Użytkownik ma widzieć w każdym widoku te same cztery elementy z projektu F-07: ładowanie jako szkielet, pustą listę, błąd z ponowieniem i stronicowanie. Wszystkie są w katalogu komponentów we wszystkich wariantach. Dziś każdy widok kopiuje ten wzorzec i własny kontener szerokości, więc każdy nowy ekran z S-50–S-59 skopiowałby go znowu.

## Starting Point

Ładowanie to dziś `<p class="loading">` z tekstem, bez szkieletu. Błąd to `StatusMessage` z osobnym przyciskiem, a cztery z pięciu sekcji panelu admina nie mają własnego ponowienia. Pusta lista to `<p class="empty">`. Stronicowanie jest tylko na liście postów: rysuje się też przy jednej stronie, a kolejna strona zastępuje listę tekstem. Powłoka z S-47 daje kolumnę 70rem, ale każdy widok stawia w niej własną, węższą kolumnę.

## Desired End State

- Cztery wspólne komponenty (`LoadingState`, `EmptyState`, `ErrorState`, `PagerNav`) są w katalogu i pod wzorcami F-06.
- Lista postów, strona posta, strona przebiegu i pięć sekcji panelu admina używają tych komponentów.
- Zmiana strony zostawia listę z przyciskiem w toku, a przy jednej stronie nie ma stronicowania.
- Błąd jednej sekcji panelu nie psuje pozostałych, a jego „Spróbuj ponownie” wczytuje tylko ją.
- Szerokość kolumny widoku ustawia trasa (`meta.column`), więc widoki z danymi wyglądają jak dziś.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Zakres przepięcia | lista, post, przebieg i pięć sekcji panelu admina; lista dzwonka zostaje | spełnia wynik roadmapy, a duży blok stanu nie mieści się w wąskim menu dzwonka |
| Kontener strony | `meta.column` (`narrow` / `wide`) w trasie, kolumnę stawia `AppShell` | jedno miejsce na szerokość; wygląd i wzorce widoków bez zmian |
| Kolejna strona listy | lista zostaje, przycisk w toku; przewinięcie i fokus po wczytaniu; błąd strony zastępuje listę | tak rysuje prototyp, a tryby `loading-next` i `error-next` już są w atrapie |
| Jedna strona | stronicowania nie ma | reguła C projektu („stronicowanie jednej strony nie jest rysowane”) |
| Wariant „Poprzednie/Następne” | teraz, jako prop `labels: 'order'` | wynik roadmapy mówi „we wszystkich wariantach”, a koszt to para etykiet |
| Ponowienie w panelu admina | każda sekcja osobno; „Odśwież” zostaje i czyta wszystkie | dokładnie przepływ `admin-error` z kontraktu E2E; S-50 przenosi to bez zmian |
| Wzorce F-06 | 6 nowych widoków stanów (24 PNG) plus 4 sekcje katalogu | stany są pod tą samą bramką wyglądu i axe co widoki |
| Ponowienie (wygląd) | błąd zostaje z przyciskiem „Wczytywanie…” w toku, bez `alert` | wariant „Podczas ponawiania” z arkusza komponentów |
| Tytuł stanu | `h2`, gdy stan nazywa obszar listy; `p` w sekcjach z własnym nagłówkiem i w katalogu | tak robią prototypy; `h2` w katalogu udawałby sekcję |

## Scope

**In scope:**
- komponenty `LoadingState` (`rows`, `lines`, `form`), `EmptyState`, `ErrorState` i `PagerNav`, oraz `AppButton` z ikoną na końcu;
- sekcje katalogu, lista `CLEANED`, testy jednostkowe;
- `meta.column` w routerze i `AppShell`, widoki bez własnego kontenera szerokości;
- stany i stronicowanie listy postów, stany strony posta (także surowych danych) i strony przebiegu;
- stany i ponowienie pięciu sekcji panelu admina, „Odśwież” wyłączone przy pierwszym wczytaniu;
- poprawione testy stanów w `e2e/feed.spec.ts`, nowy `e2e/states.spec.ts`, wzorce stanów;
- `REPOSITORY_GUIDELINES.md`, nazwy w „Mapie komponentów” w `ui-design.md`, kontrola w PR env, galeria dla właściciela.

**Out of scope:**
- przełącznik zakresu (S-52), filtry pod przyciskiem i znaczniki (S-51), części panelu admina, tabele i „Pokaż przebiegi” (S-50);
- stany listy powiadomień, szkielet klocka liczby (S-59), stronicowanie nieudanych postów (S-50);
- komunikaty działań i błędy, po których dane zostają na ekranie;
- logowanie i aktywacja (S-49);
- szerokość 70rem dla widoków, nowe tryby atrapy, API.

## Architecture / Approach

Komponenty są czysto prezentacyjne: propsy i sloty, bez danych, jak `AppBar`. Widok zostaje właścicielem stanu i fokusu, a komponent daje wygląd, role i `focus()` przycisku ponowienia. `aria-busy` stawia kontener widoku albo sekcji, bo obejmuje też stronicowanie i nagłówek. Powłoka czyta `route.meta.column` i owija `RouterView` w kolumnę o dzisiejszej szerokości. Kolejność: komponenty, potem kolumna osobno (wzorce bez zmian), potem widoki, na końcu E2E i weryfikacja.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Komponenty stanów i stronicowania | 4 komponenty, ikona na końcu, 4 sekcje katalogu, 8 wzorców | `h2` w katalogu liczone jako sekcja; ruch bez `prefers-reduced-motion` |
| 2. Kolumna w trasie oraz stany listy, posta i przebiegu | `meta.column`, nowe stany trzech widoków, stronicowanie z prototypu, 12 wzorców | przewinięcie i fokus przy zmianie strony; przywracanie pozycji po powrocie z posta |
| 3. Panel admina na wspólnych stanach | 5 sekcji ze szkieletem, pustym stanem i własnym ponowieniem, 12 wzorców | ponowienie jednej sekcji nie może pytać o pozostałe |
| 4. Testy E2E stanów i weryfikacja | `states.spec.ts` (każdy test złamany raz), dokumentacja, PR env, galeria | dzwonek w trybie `loading` wisi i może rozchwiać gotowość zrzutu |

**Prerequisites:** S-47 w `main` (jest, #201); issue #180; Docker do `npm run visual:docker`.
**Estimated effort:** ~3–4 sesje w 4 fazach.

## Open Risks & Assumptions

- Chromium zostawia fokus na wyłączonym przycisku w toku. Plan kieruje fokus jawnie: po powodzeniu na tytuł, po porażce na „Spróbuj ponownie”.
- Ponowne ogłoszenie błędu wymaga nowego wyrenderowania komunikatu z rolą `alert`. Sama zmiana atrybutu nie wystarczy.
- Prawdziwe API w PR env rzadko daje błąd albo wolną odpowiedź, więc tam sprawdzane są tylko stany z danymi, pusty filtr i stronicowanie. Błędy i ładowanie pilnują E2E i wzorce na atrapie.
- Teksty pustych stanów i błędów są nowe (tytuł plus zdanie). Tekst pustej listy bez filtra zostaje słowami, ale jest rozbity na tytuł i zdanie.

## Success Criteria (Summary)

- Każdy z czterech widoków i każda sekcja panelu pokazuje szkielet, pustą listę i błąd z ponowieniem w jednym wyglądzie. Właściciel akceptuje to na zrzutach obok prototypów.
- Lista zmienia stronę bez znikania, a przy jednej stronie nie ma stronicowania. Błąd jednej sekcji panelu ponawia się sam.
- Zielone `npm test`, `npm run e2e`, `npm run visual:docker` i CI. Widoki z danymi mają niezmienione wzorce.
