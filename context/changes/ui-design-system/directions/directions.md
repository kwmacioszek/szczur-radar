# Warianty wyglądu (F-07, faza 1)

Kierunki wizualne dla całej aplikacji, pokazane na tych samych dwóch ekranach z tą samą treścią: lista postów z oceną wybranego posta obok (`stream.html`) i przegląd dnia (`overview.html`). Strony każdego wariantu są identyczne co do znaku; różni je wyłącznie `tokens.css` i `direction.css`. Wspólna podstawa (reset, ikony, fokus) jest w `base.css`, a pliki krojów z licencjami w `fonts/`.

Zrzuty na wariant: `stream-{light,dark}-1280.png`, `overview-{light,dark}-1280.png`, `stream-{light,dark}-375.png` (kadr okna; 2× dla układu `e/`, 1× dla pozostałych). Powstają poleceniem `node tools/design-check/design-check.mjs shots <katalog wariantu> --scale 2 --only stream:1280,stream:375,overview:1280`.

## Dwie rundy

**Runda 1** (2026-11-19): „Papier”, „Pulpit” i „Miękki”, w katalogu `round-1/` (style i strony, bez zrzutów; zrzuty odtwarza polecenie powyżej). Właściciel odrzucił wszystkie trzy: „nadal wygląda jak trochę lepszy panel”, brakuje stylu i kolorystyki. Przyczyna: warianty różniły się krojem, gęstością i kształtem ramek, a kolor był w nich zarezerwowany dla znaczenia, więc żaden nie miał własnej tożsamości.

**Runda 2** (2026-11-19): „Radar”, „Parkiet” i „Salon”, w katalogach `a/`, `b/`, `c/`. Wskazówki właściciela: lubi ciemne motywy (jasny zostaje jako drugi, domyślnie według systemu), podoba mu się wygląd aplikacji Claude na telefon. Każdy wariant jest projektowany od ciemnego motywu i ma własną tożsamość: kolor marki, motyw albo charakter typografii.

Odstępstwo od planu: plan wymagał, żeby jeden wariant zachował paletę A z S-46 bez zmiany wartości. Spełniał to „Papier” z rundy 1. W rundzie 2 żaden wariant jej nie zachowuje, bo właściciel poprosił o inną kolorystykę.

## Co jest wspólne (wynika ze standardów i wymagań, nie z gustu)

- **Sedno posta na liście**: każdy wiersz pokazuje cele posta ze stanowiskiem autora wobec każdego z nich. Nie ma jednej etykiety stanowiska na cały post (kontrakt oceny jej nie ma, FR-011a).
- **Ocena obok listy**: wybrany post otwiera się w panelu obok, bez przechodzenia na inną stronę (wzorzec „lista ze szczegółami”); „poprzedni” i „następny” przechodzą po bieżącej liście.
- **Stanowisko to znak, słowo i kolor** jednocześnie, więc nie zależy od rozróżniania barw (WCAG 1.4.1). Te same słowa co dziś w aplikacji (`labels.ts`): wzrostowo, spadkowo, neutralnie, mieszanie, bez stanowiska, niejasno, „(ostrożnie)”.
- **Cytat prowadzi do oceny**: fragmenty, na których oparto ocenę, są podświetlone w treści, a każda teza ma pod sobą dosłowny cytat w tym samym kolorze co podświetlenie (FR-009).
- **Liczby są jawne**: proporcje w przeglądzie dnia mają obok paska liczby postów; kolejność „najczęściej omawianych” to liczba postów (FR-038, FR-039). Nie ma trendu ani oceny „warte przeczytania”.
- **Cel to tekst z posta**: „Orlen”, „Orlenu” i „PKN” są osobnymi pozycjami (FR-011a, Non-Goals).
- **Dwa źródła własnymi słowami**: Wykop ma „wpis” i „znalezisko”, Reddit „post” i „link” (`ITEM_TYPE_WORDS`).
- **Układ pod komputer**: panele obok siebie przy 1280 px, jedna kolumna na telefonie. Na zrzutach 375 px widać samą listę; ocena posta na telefonie jest osobnym ekranem i powstanie w fazie 3.
- **Dostępność**: kontrast tekstu co najmniej 4,5:1 i elementów interfejsu 3:1 w obu motywach, widoczny fokus, cele dotyku co najmniej 24 px, brak przewijania w poziomie od 320 px, nazwy dostępne przycisków z samą ikoną.

## Wariant „Radar” (`a/`)

Tożsamość z nazwy produktu: nocny ekran radaru. Granat, świecący błękit, pierścienie radaru za znakiem.

- **Kroje**: Red Hat Display (nagłówki, tytuły, liczby) i Red Hat Text (tekst). Licencja SIL OFL 1.1, pliki w `fonts/red-hat-display/` i `fonts/red-hat-text/`, razem 89 kB.
- **Paleta**: głęboki granat z błękitnym akcentem w ciemnym motywie; w jasnym chłodna biel z granatowym tekstem i niebieskim akcentem. Boczna kolumna jest granatowa w obu motywach (role `--shell-*`), więc aplikację poznaje się też w jasnym. Bursztyn łączy podświetlony fragment treści z cytatem w ocenie.
- **Znak rozpoznawczy**: pierścienie radaru i „omiatający” znak w rogu kolumny, poświata akcentu nad treścią i wokół pierwszej liczby dnia.
- **Kształt**: panele z cienką ramką i promieniem 14 px; stanowiska jako plakietki z ciemnym tłem w kolorze znaczenia.
- **Nawigacja**: z boku, ikona z opisem; sesja w ramce na dole kolumny.
- **Gęstość**: średnia, 4 wiersze listy przy 900 px wysokości.
- **Uzasadnienie**: stała ciemna powłoka z jasną treścią to wzorzec znany z narzędzi analitycznych; akcent i semantyczne kolory stanowisk mają różne barwy (błękit wobec zieleni i czerwieni), więc nie mylą się (heurystyka 4 i 6).

### Kontrast „Radar”

Najniższy wynik dla tekstu: jasny 4.62, ciemny 5.09 (wymagane 4,5).

| pair | kind | min | light | dark |
| --- | --- | --- | --- | --- |
| `--text` on `--bg` | text | 4.5 | 7.62 | 9.44 |
| `--text` on `--surface` | text | 4.5 | 8.56 | 8.65 |
| `--text` on `--surface-2` | text | 4.5 | 7.90 | 7.90 |
| `--text` on `--selected` | text | 4.5 | 7.18 | 7.33 |
| `--text-h` on `--bg` | text | 4.5 | 16.22 | 17.34 |
| `--text-h` on `--surface` | text | 4.5 | 18.23 | 15.89 |
| `--text-muted` on `--bg` | text | 4.5 | 5.19 | 6.73 |
| `--text-muted` on `--surface` | text | 4.5 | 5.83 | 6.17 |
| `--text-muted` on `--surface-2` | text | 4.5 | 5.38 | 5.64 |
| `--text-muted` on `--selected` | text | 4.5 | 4.89 | 5.23 |
| `--accent` on `--bg` | text | 4.5 | 5.13 | 10.07 |
| `--accent` on `--surface` | text | 4.5 | 5.77 | 9.23 |
| `--accent` on `--surface-2` | text | 4.5 | 5.32 | 8.43 |
| `--on-accent` on `--accent` | text | 4.5 | 5.77 | 9.55 |
| `--success` on `--surface` | text | 4.5 | 5.78 | 9.97 |
| `--success` on `--surface-2` | text | 4.5 | 5.34 | 9.10 |
| `--success` on `--success-soft` | text | 4.5 | 4.95 | 7.94 |
| `--warning` on `--surface` | text | 4.5 | 6.21 | 10.79 |
| `--warning` on `--selected` | text | 4.5 | 5.21 | 9.15 |
| `--destructive` on `--surface` | text | 4.5 | 5.63 | 6.89 |
| `--destructive` on `--surface-2` | text | 4.5 | 5.19 | 6.29 |
| `--destructive` on `--destructive-soft` | text | 4.5 | 4.62 | 6.07 |
| `--stance-neutral` on `--surface` | text | 4.5 | 7.19 | 7.88 |
| `--stance-neutral` on `--surface-2` | text | 4.5 | 6.64 | 7.20 |
| `--stance-neutral` on `--neutral-soft` | text | 4.5 | 6.06 | 6.50 |
| `--stance-mixed` on `--surface` | text | 4.5 | 6.21 | 10.79 |
| `--stance-mixed` on `--mixed-soft` | text | 4.5 | 5.31 | 8.10 |
| `--text-h` on `--success-soft` | text | 4.5 | 15.61 | 12.65 |
| `--text-h` on `--destructive-soft` | text | 4.5 | 14.96 | 13.99 |
| `--text-h` on `--neutral-soft` | text | 4.5 | 15.35 | 13.10 |
| `--text-h` on `--mixed-soft` | text | 4.5 | 15.59 | 11.92 |
| `--text-muted` on `--neutral-soft` | text | 4.5 | 4.91 | 5.09 |
| `--text-h` on `--highlight` | text | 4.5 | 15.17 | 10.96 |
| `--shell-text` on `--shell-bg` | text | 4.5 | 10.52 | 10.52 |
| `--shell-text-h` on `--shell-bg` | text | 4.5 | 17.51 | 17.51 |
| `--shell-text-h` on `--shell-surface` | text | 4.5 | 15.51 | 15.51 |
| `--shell-muted` on `--shell-bg` | text | 4.5 | 6.81 | 6.81 |
| `--shell-muted` on `--shell-surface` | text | 4.5 | 6.03 | 6.03 |
| `--shell-accent` on `--shell-bg` | text | 4.5 | 10.07 | 10.07 |
| `--shell-accent` on `--shell-surface` | text | 4.5 | 8.92 | 8.92 |
| `--border` on `--bg` | ui | 3 | 3.61 | 3.95 |
| `--border` on `--surface` | ui | 3 | 4.06 | 3.62 |
| `--border` on `--surface-2` | ui | 3 | 3.75 | 3.31 |
| `--ring` on `--bg` | ui | 3 | 5.13 | 10.07 |
| `--ring` on `--surface` | ui | 3 | 5.77 | 9.23 |
| `--shell-accent` on `--shell-bg` | ui | 3 | 10.07 | 10.07 |
| `--success` on `--surface` | ui | 3 | 5.78 | 9.97 |
| `--destructive` on `--surface` | ui | 3 | 5.63 | 6.89 |

## Wariant „Parkiet” (`b/`)

Energia aplikacji giełdowej: czerń, wielkie litery nagłówków, pełne kolory.

- **Kroje**: Bricolage Grotesque (nagłówki, tytuły, liczby) i Figtree (tekst). Licencja SIL OFL 1.1, pliki w `fonts/bricolage-grotesque/` i `fonts/figtree/`, razem 90 kB.
- **Paleta**: czerń i biel z elektrycznym błękitem jako akcentem. Stanowiska to pełne barwne bloki: zieleń, czerwień, fiolet dla mieszanego, szarość dla neutralnego. Cytat i podświetlenie to żółty zakreślacz w obu motywach. Czarny pasek u góry zostaje w obu motywach (role `--shell-*`).
- **Znak rozpoznawczy**: bardzo duże, ciasno złożone nagłówki i liczby; kafle liczb dnia w kolorze akcentu i w odwróconych barwach; grube paski proporcji.
- **Kształt**: bloki o ostrych narożnikach (promień 4–8 px), grube linie pod nagłówkami sekcji, przyciski w kształcie pigułki.
- **Nawigacja**: u góry, tekstowa, bieżąca pozycja jako biała pigułka.
- **Gęstość**: najmniejsza, 3 wiersze listy przy 900 px wysokości.
- **Uzasadnienie**: silna hierarchia wielkości (największe to, co najważniejsze: tytuł, liczba, nazwa celu) i kolor w pełnym nasyceniu tylko dla stanowisk prowadzą wzrok do sedna (heurystyka 8); tekst na barwnych blokach ma osobno sprawdzony kontrast (`--stance-ink`). Najgłośniejszy z trzech; przy długiej pracy może męczyć.

### Kontrast „Parkiet”

Najniższy wynik dla tekstu: jasny 4.71, ciemny 5.10 (wymagane 4,5).

| pair | kind | min | light | dark |
| --- | --- | --- | --- | --- |
| `--text` on `--bg` | text | 4.5 | 9.57 | 10.01 |
| `--text` on `--surface` | text | 4.5 | 10.54 | 9.14 |
| `--text` on `--surface-2` | text | 4.5 | 8.91 | 8.10 |
| `--text` on `--selected` | text | 4.5 | 8.58 | 8.03 |
| `--text-h` on `--bg` | text | 4.5 | 17.65 | 19.44 |
| `--text-h` on `--surface` | text | 4.5 | 19.44 | 17.75 |
| `--text-muted` on `--bg` | text | 4.5 | 5.89 | 6.36 |
| `--text-muted` on `--surface` | text | 4.5 | 6.49 | 5.81 |
| `--text-muted` on `--surface-2` | text | 4.5 | 5.48 | 5.15 |
| `--text-muted` on `--selected` | text | 4.5 | 5.28 | 5.10 |
| `--accent` on `--bg` | text | 4.5 | 5.06 | 7.53 |
| `--accent` on `--surface` | text | 4.5 | 5.58 | 6.88 |
| `--accent` on `--surface-2` | text | 4.5 | 4.71 | 6.10 |
| `--on-accent` on `--accent` | text | 4.5 | 5.58 | 7.20 |
| `--invert-text` on `--invert-bg` | text | 4.5 | 19.44 | 19.44 |
| `--stance-ink` on `--success` | text | 4.5 | 5.78 | 10.90 |
| `--stance-ink` on `--destructive` | text | 4.5 | 5.62 | 7.01 |
| `--stance-ink` on `--stance-neutral` | text | 4.5 | 7.56 | 8.56 |
| `--stance-ink` on `--stance-mixed` | text | 4.5 | 6.65 | 8.53 |
| `--success` on `--surface` | text | 4.5 | 5.78 | 9.95 |
| `--success` on `--surface-2` | text | 4.5 | 4.89 | 8.82 |
| `--destructive` on `--surface` | text | 4.5 | 5.62 | 6.40 |
| `--destructive` on `--surface-2` | text | 4.5 | 4.75 | 5.67 |
| `--stance-neutral` on `--surface` | text | 4.5 | 7.56 | 7.81 |
| `--stance-neutral` on `--surface-2` | text | 4.5 | 6.39 | 6.93 |
| `--warning` on `--surface` | text | 4.5 | 6.33 | 10.33 |
| `--warning` on `--selected` | text | 4.5 | 5.15 | 9.07 |
| `--highlight-ink` on `--highlight` | text | 4.5 | 15.28 | 15.28 |
| `--shell-text` on `--shell-bg` | text | 4.5 | 12.09 | 12.09 |
| `--shell-text-h` on `--shell-surface` | text | 4.5 | 16.69 | 16.69 |
| `--shell-muted` on `--shell-bg` | text | 4.5 | 6.73 | 6.73 |
| `--shell-bg` on `--shell-text-h` | text | 4.5 | 19.44 | 19.44 |
| `--shell-bg` on `--shell-accent` | text | 4.5 | 7.53 | 7.53 |
| `--border` on `--bg` | ui | 3 | 4.07 | 3.87 |
| `--border` on `--surface` | ui | 3 | 4.48 | 3.53 |
| `--border` on `--surface-2` | ui | 3 | 3.79 | 3.13 |
| `--ring` on `--bg` | ui | 3 | 5.06 | 7.53 |
| `--ring` on `--surface` | ui | 3 | 5.58 | 6.88 |
| `--shell-accent` on `--shell-bg` | ui | 3 | 7.53 | 7.53 |
| `--success` on `--surface` | ui | 3 | 5.78 | 9.95 |
| `--destructive` on `--surface` | ui | 3 | 5.62 | 6.40 |

## Wariant „Salon” (`c/`)

Spokój i ciepło, w duchu aplikacji do czytania i rozmowy, na którą wskazał właściciel.

- **Kroje**: Newsreader (szeryf: nagłówki, tytuły postów, treść, cytaty, liczby) i Hanken Grotesk (interfejs). Licencja SIL OFL 1.1, pliki w `fonts/newsreader/` i `fonts/hanken-grotesk/`, razem 149 kB.
- **Paleta**: ciepły grafit w ciemnym motywie, kość słoniowa w jasnym; akcent w kolorze wypalonej gliny tylko tam, gdzie coś jest wybrane albo klikalne. Stanowiska w przygaszonych odcieniach (szałwia, róż, piasek) na miękkim tle.
- **Znak rozpoznawczy**: szeryfowa typografia o lekkiej grubości i dużo światła; cała lista z oceną na jednej dużej, miękkiej powierzchni.
- **Kształt**: duże promienie (20 px bloki, 14–16 px wewnątrz), pigułki stanowisk, cienkie linie.
- **Nawigacja**: z boku, lekka, bez ramki.
- **Gęstość**: średnia, 4 wiersze listy przy 900 px wysokości; treść posta 20 px.
- **Uzasadnienie**: szeryf i szeroka interlinia służą czytaniu dłuższych wypowiedzi; jeden przygaszony akcent i brak ozdobników trzymają uwagę na treści (heurystyka 8). Najspokojniejszy z trzech; najmniej „giełdowy”.

### Kontrast „Salon”

Najniższy wynik dla tekstu: jasny 4.64, ciemny 4.87 (wymagane 4,5).

| pair | kind | min | light | dark |
| --- | --- | --- | --- | --- |
| `--text` on `--bg` | text | 4.5 | 7.30 | 9.29 |
| `--text` on `--surface` | text | 4.5 | 8.02 | 8.27 |
| `--text` on `--surface-2` | text | 4.5 | 6.74 | 7.31 |
| `--text` on `--selected` | text | 4.5 | 6.65 | 7.25 |
| `--text-h` on `--bg` | text | 4.5 | 15.07 | 15.00 |
| `--text-h` on `--surface` | text | 4.5 | 16.55 | 13.36 |
| `--text-muted` on `--bg` | text | 4.5 | 5.17 | 6.54 |
| `--text-muted` on `--surface` | text | 4.5 | 5.68 | 5.82 |
| `--text-muted` on `--surface-2` | text | 4.5 | 4.77 | 5.15 |
| `--text-muted` on `--selected` | text | 4.5 | 4.71 | 5.10 |
| `--accent` on `--bg` | text | 4.5 | 5.21 | 6.87 |
| `--accent` on `--surface` | text | 4.5 | 5.72 | 6.12 |
| `--accent` on `--surface-2` | text | 4.5 | 4.81 | 5.41 |
| `--on-accent` on `--accent` | text | 4.5 | 5.81 | 6.98 |
| `--success` on `--surface` | text | 4.5 | 5.86 | 8.37 |
| `--success` on `--surface-2` | text | 4.5 | 4.93 | 7.40 |
| `--success` on `--success-soft` | text | 4.5 | 5.02 | 7.13 |
| `--warning` on `--surface` | text | 4.5 | 6.23 | 8.15 |
| `--warning` on `--selected` | text | 4.5 | 5.16 | 7.14 |
| `--destructive` on `--surface` | text | 4.5 | 5.95 | 5.91 |
| `--destructive` on `--surface-2` | text | 4.5 | 5.00 | 5.22 |
| `--destructive` on `--destructive-soft` | text | 4.5 | 4.89 | 5.38 |
| `--stance-neutral` on `--surface` | text | 4.5 | 6.97 | 6.86 |
| `--stance-neutral` on `--surface-2` | text | 4.5 | 5.86 | 6.07 |
| `--stance-neutral` on `--neutral-soft` | text | 4.5 | 5.69 | 5.73 |
| `--stance-mixed` on `--surface` | text | 4.5 | 6.36 | 8.07 |
| `--stance-mixed` on `--mixed-soft` | text | 4.5 | 5.42 | 6.63 |
| `--text-h` on `--success-soft` | text | 4.5 | 14.17 | 11.38 |
| `--text-h` on `--destructive-soft` | text | 4.5 | 13.60 | 12.17 |
| `--text-h` on `--neutral-soft` | text | 4.5 | 13.51 | 11.16 |
| `--text-h` on `--mixed-soft` | text | 4.5 | 14.10 | 10.97 |
| `--text-muted` on `--neutral-soft` | text | 4.5 | 4.64 | 4.87 |
| `--text-h` on `--highlight` | text | 4.5 | 13.19 | 9.28 |
| `--border` on `--bg` | ui | 3 | 3.56 | 4.33 |
| `--border` on `--surface` | ui | 3 | 3.91 | 3.85 |
| `--border` on `--surface-2` | ui | 3 | 3.29 | 3.41 |
| `--ring` on `--bg` | ui | 3 | 5.21 | 6.87 |
| `--ring` on `--surface` | ui | 3 | 5.72 | 6.12 |
| `--success` on `--surface` | ui | 3 | 5.86 | 8.37 |
| `--destructive` on `--surface` | ui | 3 | 5.95 | 5.91 |
| `--accent` on `--selected` | text | 4.5 | 4.74 | 5.37 |
| `--text-h` on `--selected` | text | 4.5 | 13.73 | 11.71 |
| `--stance-mixed` on `--surface-2` | text | 4.5 | 5.35 | 7.14 |
| `--text` on `--highlight` | text | 4.5 | 6.39 | 5.75 |

## Wariant „Salon w kolorach Radaru” (`d/`)

Połączenie zlecone przez właściciela po rundzie 2 (2026-11-19): „Radar jest fajny, ale wygląd podoba mi się w Salonie; najlepiej wziąć kolory z Radaru i wygląd z Salonu”.

- **Z „Salonu”**: układ (lekka boczna kolumna bez ramki, lista i ocena na jednej dużej, miękkiej powierzchni), kroje Newsreader i Hanken Grotesk (razem 149 kB), duże promienie, pigułki stanowisk, cienkie linie, gęstość.
- **Z „Radaru”**: wszystkie role koloru w obu motywach (głęboki granat i świecący błękit w ciemnym, chłodna biel i niebieski akcent w jasnym), bursztyn łączący podświetlony fragment z cytatem w ocenie, poświata akcentu nad treścią, pierścienie radaru i „omiatający” znak przy nazwie.
- **Czego nie przenosi**: granatowej kolumny w jasnym motywie (w połączeniu kolumna ma kolor tła, jak w „Salonie”) i poświaty wokół pierwszej liczby dnia.
- Strony są te same co w pozostałych wariantach; `pairs.json` to pary „Salonu” policzone na kolorach „Radaru”.

### Kontrast „Salon w kolorach Radaru”

Najniższy wynik dla tekstu: jasny 4.62, ciemny 5.09 (wymagane 4,5).

| pair | kind | min | light | dark |
| --- | --- | --- | --- | --- |
| `--text` on `--bg` | text | 4.5 | 7.62 | 9.44 |
| `--text` on `--surface` | text | 4.5 | 8.56 | 8.65 |
| `--text` on `--surface-2` | text | 4.5 | 7.90 | 7.90 |
| `--text` on `--selected` | text | 4.5 | 7.18 | 7.33 |
| `--text-h` on `--bg` | text | 4.5 | 16.22 | 17.34 |
| `--text-h` on `--surface` | text | 4.5 | 18.23 | 15.89 |
| `--text-muted` on `--bg` | text | 4.5 | 5.19 | 6.73 |
| `--text-muted` on `--surface` | text | 4.5 | 5.83 | 6.17 |
| `--text-muted` on `--surface-2` | text | 4.5 | 5.38 | 5.64 |
| `--text-muted` on `--selected` | text | 4.5 | 4.89 | 5.23 |
| `--accent` on `--bg` | text | 4.5 | 5.13 | 10.07 |
| `--accent` on `--surface` | text | 4.5 | 5.77 | 9.23 |
| `--accent` on `--surface-2` | text | 4.5 | 5.32 | 8.43 |
| `--on-accent` on `--accent` | text | 4.5 | 5.77 | 9.55 |
| `--success` on `--surface` | text | 4.5 | 5.78 | 9.97 |
| `--success` on `--surface-2` | text | 4.5 | 5.34 | 9.10 |
| `--success` on `--success-soft` | text | 4.5 | 4.95 | 7.94 |
| `--warning` on `--surface` | text | 4.5 | 6.21 | 10.79 |
| `--warning` on `--selected` | text | 4.5 | 5.21 | 9.15 |
| `--destructive` on `--surface` | text | 4.5 | 5.63 | 6.89 |
| `--destructive` on `--surface-2` | text | 4.5 | 5.19 | 6.29 |
| `--destructive` on `--destructive-soft` | text | 4.5 | 4.62 | 6.07 |
| `--stance-neutral` on `--surface` | text | 4.5 | 7.19 | 7.88 |
| `--stance-neutral` on `--surface-2` | text | 4.5 | 6.64 | 7.20 |
| `--stance-neutral` on `--neutral-soft` | text | 4.5 | 6.06 | 6.50 |
| `--stance-mixed` on `--surface` | text | 4.5 | 6.21 | 10.79 |
| `--stance-mixed` on `--mixed-soft` | text | 4.5 | 5.31 | 8.10 |
| `--text-h` on `--success-soft` | text | 4.5 | 15.61 | 12.65 |
| `--text-h` on `--destructive-soft` | text | 4.5 | 14.96 | 13.99 |
| `--text-h` on `--neutral-soft` | text | 4.5 | 15.35 | 13.10 |
| `--text-h` on `--mixed-soft` | text | 4.5 | 15.59 | 11.92 |
| `--text-muted` on `--neutral-soft` | text | 4.5 | 4.91 | 5.09 |
| `--text-h` on `--highlight` | text | 4.5 | 15.17 | 10.96 |
| `--border` on `--bg` | ui | 3 | 3.61 | 3.95 |
| `--border` on `--surface` | ui | 3 | 4.06 | 3.62 |
| `--border` on `--surface-2` | ui | 3 | 3.75 | 3.31 |
| `--ring` on `--bg` | ui | 3 | 5.13 | 10.07 |
| `--ring` on `--surface` | ui | 3 | 5.77 | 9.23 |
| `--success` on `--surface` | ui | 3 | 5.78 | 9.97 |
| `--destructive` on `--surface` | ui | 3 | 5.63 | 6.89 |
| `--accent` on `--selected` | text | 4.5 | 4.84 | 7.82 |
| `--text-h` on `--selected` | text | 4.5 | 15.30 | 13.47 |
| `--stance-mixed` on `--surface-2` | text | 4.5 | 5.74 | 9.86 |
| `--text` on `--highlight` | text | 4.5 | 7.12 | 5.96 |

## Układ „Kanał” (`e/`)

Po potwierdzeniu wyglądu (wariant `d/`) właściciel wrócił do fazy 1 (2026-11-20): „to nadal wygląda jak panel administratora”, „trochę się gubię”, „może trochę inny układ stron”. Przyczyna leżała w układzie, nie w kolorach: boczne menu, formularz z czterema filtrami, lista i stały panel ze szczegółami pokazywały wszystko naraz. Właściciel nie chce odchodzić od roadmapy i zaproponował, żeby ocena posta była wyskakującym dialogiem wkomponowanym w ekran, a nie sekcją wciśniętą obok listy.

Układ `e/` zachowuje wygląd `d/` (te same tokeny i kroje; doszła rola `--scrim` dla przyciemnienia tła) i zmienia strukturę stron. Stan po decyzjach właściciela z 2026-11-20:

- **Posty** (`stream.html`): jedna kolumna. Lista **zwarta**: jeden post w jednej linii (godzina i źródło, tytuł, stanowiska po prawej), jedna ramka na dzień, posty pogrupowane po dniach. Na wierzchu szukanie i przełącznik zakresu; pozostałe filtry (cel, stanowisko, dzień, kolejność) pod przyciskiem „Filtry”.
- **Ocena posta** (`post-sheet.html`): **panel wysuwany z prawej** nad listę (`role="dialog"`, własny adres posta, „poprzedni”/„następny”, „Zamknij”). Lista przesuwa się do lewej krawędzi, jest lekko przyciemniona i zostaje widoczna w tle; wybrany post jest zaznaczony. Na wąskim ekranie panel zajmuje cały ekran.
- **Przegląd dnia** (`overview.html`): pięć klocków z liczbami dnia (posty, cele ze stanowiskiem, udział stanowisk wzrostowych, spadkowych i pozostałych), trzy karty najczęściej omawianych celów z paskiem proporcji i najnowszymi postami, pełna tabela celów niżej.
- **Zakres postów**: przełącznik „Inwestycyjne 128 | Wszystkie 165” na obu ekranach (na przeglądzie obok wyboru dnia) zamiast zdania o ukrytych postach i pola „Pokaż wszystkie” (FR-007).
- **Nawigacja**: u góry zamiast bocznej kolumny.

Formy pokazane właścicielowi i odrzucone (plików nie ma w repo, opis dla porządku):

| Element | Odrzucona forma | Powód (słowa właściciela albo wybór) |
| --- | --- | --- |
| Lista postów | karty, każdy post w osobnej ramce | „nie podoba mi się układ z klockami” |
| Lista postów | wiersze bez ramek z godziną po lewej | „lista zwarta jest czytelniejsza” |
| Ocena posta | dialog pośrodku ekranu (treść po lewej, ocena po prawej) | wybrany panel z prawej, „żeby było widać w tle listę” |
| Nagłówek przeglądu | zdanie z liczbami dnia | „lepiej dać kilka prostych klocków z liczbami, jakimiś % na + albo −” |
| Zakres postów | zdanie „Bez 37 postów nieinwestycyjnych i bez treści” z polem wyboru | „może jakoś inaczej to pokazać”; wybrany przełącznik |

Dwie rzeczy do zapisania w fazie 4 (różnice wobec roadmapy i PRD):

- S-51 mówi o „panelu inspektora obok listy”. Panel na życzenie spełnia te same wymagania (FR-009, FR-010); zmienia się sformułowanie wycinka.
- Klocki „Wzrostowe / Spadkowe / Pozostałe” pokazują udział stanowisk w całym dniu. FR-039 wymienia liczbę postów, liczbę celów, źródła i proporcje per cel, ale nie udział w całym dniu; to suma danych, które przegląd i tak liczy. Właściciel poprosił o te klocki (2026-11-20), więc jest to doprecyzowanie FR-039 do zatwierdzenia w PRD. Udział nie jest zmianą względem poprzedniego dnia (trend zostaje poza zakresem).

Zrzuty: sześć standardowych (2×) oraz `post-sheet-{light,dark}-1280.png`. Zrzuty wariantów `a/`–`d/` są od 2026-11-20 w skali 1×, bo decyzja o wyglądzie już zapadła, a zrzuty 2× zajmowały ponad 8 MB.

## Wynik kontroli stron

`node tools/design-check/design-check.mjs pages context/changes/ui-design-system/directions` (2026-11-19): 17 stron (6 z rundy 2, 2 połączenia, 3 układu „Kanał” i 6 z rundy 1), oba motywy, szerokości 1280, 375 i 320 px, 0 naruszeń. Kontrola obejmuje reguły axe z tagami WCAG 2.0/2.1/2.2 A i AA (w tym rozmiar celu), brak przewijania strony w poziomie, jeden `main` i jeden `banner`, oraz to, że cały tekst rysują kroje wariantu (polskie znaki bez kroju zastępczego).

## Ograniczenia tablic wariantów

- Pokazują dwa ekrany, a nie całą aplikację. Pozostałe ekrany i komponenty we wszystkich stanach powstaną w wybranym wariancie (fazy 2 i 3).
- Filtry na liście zawierają pola, których API jeszcze nie obsługuje (szukanie w treści S-56, kolejność S-54, przełącznik „Pokaż wszystkie” S-52); przegląd dnia to S-59. Tablice pokazują stan docelowy.
- Wariant można łączyć: np. krój jednego z paletą drugiego. Połączenie przechodzi te same kontrole przed zapisem decyzji.
- Poświata i pierścienie w „Radarze” to gradienty; tekst leżący na gradiencie axe oznacza jako „do ręcznej oceny”, a nie sprawdza. Kontrast tych miejsc pokrywają pary z `pairs.json` dla obu krańców gradientu.

## Decyzja właściciela

Runda 1: żaden wariant (2026-11-19).

Runda 2: połączenie, kolory z „Radaru” i wygląd z „Salonu” (2026-11-19). Wariant `d/` „Salon w kolorach Radaru” przygotowany i **potwierdzony przez właściciela 2026-11-19** (odpowiedź „Tak” na pytanie, czy to wybrany wygląd), bez poprawek. To jest wariant dla faz 2 i 3.

Układ stron (2026-11-20): właściciel wrócił do fazy 1 i poprosił o inny układ z oceną posta w dialogu. Wybory właściciela w układzie `e/` „Kanał”: lista zwarta („lista zwarta jest czytelniejsza”), ocena posta w panelu z prawej („żeby było widać w tle listę”), nagłówek przeglądu jako klocki z liczbami, przełącznik „Inwestycyjne / Wszystkie”. Całość potwierdzona przez właściciela 2026-11-20 (zatwierdzenie commita układu). Wariant `e/` „Kanał” jest podstawą faz 2 i 3: wygląd z `d/`, układ z `e/`.
