# Projekt UI/UX aplikacji Szczur Radar

> Dokument żywy (F-07, `ui-design-system`). Źródło wyglądu i układu aplikacji webowej: wycinki UI wdrażają swoje ekrany według niego. Zmiana projektu to edycja tego dokumentu z akceptacją właściciela i zielonymi kontrolami [`tools/design-check`](../../tools/design-check/README.md).

## Cel i zakres

Aplikacja ma wyglądać i działać jak dopracowany produkt, a nie surowy panel. Ten dokument opisuje projekt całej aplikacji webowej: zasady i standardy, zadania użytkownika, układ ekranów i nawigację, język wizualny, komponenty, treść etykiet oraz to, czego potrzebują wdrożenie i testy.

Zakres to dokładnie wymagania [PRD v4](prd.md) dla aplikacji webowej (FR-006–FR-040) i ekrany kamienia milowego M-5 z [roadmapy](roadmap.md). Projekt nie dodaje wymagań. Pomysły wynikające ze standardów, których PRD nie obejmuje, są w sekcji „Propozycje poza PRD” i nie mają prototypów. Jedno doprecyzowanie na prośbę właściciela (2026-11-20): przegląd dnia pokazuje udział stanowisk wzrostowych, spadkowych i pozostałych w całym dniu. To suma danych, które FR-039 i tak każe policzyć (proporcje per cel), a nie zmiana względem poprzedniego dnia; czeka na zatwierdzenie w PRD (sekcja „Różnice wobec roadmapy”).

Makiety z [`ui-ux-proposals.md`](ui-ux-proposals.md) były listą funkcji, a nie wzorem wyglądu. Projekt nie zawiera ich elementów bez pokrycia w wymaganiach: tickerów i nazw kanonicznych spółek, scalania odmian celu, trendu, jednej etykiety stanowiska na cały post, „Zapisanych”, „Analiz”, „Wątków”, „Ostatnich obserwacji”, statusu pipeline'u dla zwykłego użytkownika i wyboru postów „wartych przeczytania”.

Stan sekcji:

| Sekcja | Stan |
| --- | --- |
| Cel i zakres, Użytkownik i zadania, Zasady i standardy, Architektura informacji i nawigacja, Ekrany | gotowe (faza 1) |
| Język wizualny, Komponenty, Treść i etykiety | po wyborze wariantu wyglądu (faza 2) |
| Prototypy ekranów, Decyzja właściciela | faza 3 |
| Kontrakt dla testów E2E, Wdrożenie, Różnice wobec roadmapy, Propozycje poza PRD | faza 4 |

## Użytkownik i zadania

Aplikacji używają zaproszone osoby (rola użytkownika) i administrator (rola admina, zarazem operator). Korzystają głównie z komputera; telefon jest urządzeniem dodatkowym. Projekt układa więc ekrany pod szeroki ekran, a na wąskim daje jedną kolumnę bez utraty funkcji.

Zadania w kolejności ważności:

1. **Zobaczyć, o czym dziś mowa.** Ile jest postów, które cele są najczęściej omawiane i jakie stanowiska wobec nich przeważają (przegląd dnia).
2. **Przejrzeć nowe posty i wyłapać sedno.** Dla każdego posta: o jakich celach mówi autor i jakie ma wobec nich stanowisko, bez otwierania posta.
3. **Sprawdzić, skąd ocena.** Przeczytać post z fragmentami, na których oparto ocenę, i z tezami pod dosłownymi cytatami; przejść do oryginału.
4. **Znaleźć wypowiedzi o konkretnym celu.** Katalog celów, proporcje stanowisk wobec celu, filtry i wyszukiwanie.
5. **Dopilnować danych (admin).** Uruchomić przebieg, zobaczyć, co wymaga uwagi, uzupełnić dni bez danych, przywrócić nieudane posty, sprawdzić jakość ocen.

Bolączki zgłoszone przez właściciela (2026-11-19) i odpowiedź projektu:

| Bolączka | Odpowiedź projektu |
| --- | --- |
| Surowy wygląd | Jeden język wizualny: krój pisma w plikach aplikacji, skala typografii i odstępów, role kolorów, komponenty ze wszystkimi stanami. Wygląd wybiera właściciel spośród trzech wariantów. |
| Trudno wyłapać sedno posta | Wiersz listy pokazuje w jednej linii tytuł albo początek treści i cele ze stanowiskiem wobec każdego (znak, słowo, kolor). |
| Za dużo klikania | Ocena wybranego posta wysuwa się w panelu z prawej; lista zostaje widoczna w tle z zaznaczonym postem. Przyciski „poprzedni” i „następny” przechodzą po bieżącej liście bez zamykania panelu. |
| Nieczytelny panel admina | Panel podzielony na trzy części o jednym temacie każda (przebiegi, jakość ocen, nieudane posty) zamiast jednej długiej strony z pięcioma sekcjami. |
| „Wygląda jak panel administratora, gubię się” (2026-11-20, po pierwszych prototypach z panelami obok siebie) | Jedna kolumna i jedna rzecz naraz: lista zwarta, filtry pod jednym przyciskiem, szczegóły dopiero po kliknięciu, menu u góry zamiast bocznej kolumny. Zdania objaśniające zastąpione klockami z liczbami i przełącznikami. |

## Zasady i standardy

Standardy dobiera projekt, a ich spełnienie sprawdza agent poleceniem albo niezależnym przeglądem, nie właściciel okiem. Każda zasada ma źródło, reguły wybrane dla tej aplikacji i sposób sprawdzenia.

### Dostępność: WCAG 2.2, poziom AA

Źródło: [Web Content Accessibility Guidelines 2.2](https://www.w3.org/TR/WCAG22/).

| Reguła | Kryterium | Jak sprawdzamy |
| --- | --- | --- |
| Tekst ma kontrast co najmniej 4,5:1 na każdym tle, na którym występuje, w obu motywach | 1.4.3 | `design-check contrast` na parach z `pairs.json`; reguła axe `color-contrast` |
| Obramowania pól, pierścień fokusu i znaki niosące znaczenie mają kontrast co najmniej 3:1 | 1.4.11 | `design-check contrast` (pary rodzaju `ui`) |
| Znaczenia nie niesie sam kolor: stanowisko i stan to zawsze znak i słowo, kolor je wzmacnia | 1.4.1 | przegląd heurystyczny |
| Fokus klawiatury jest widoczny na każdym elemencie interaktywnym i nie jest zasłonięty | 2.4.7, 2.4.11 | arkusz komponentów (stan fokusu), przegląd |
| Cel dotyku ma co najmniej 24×24 px albo wystarczający odstęp | 2.5.8 | reguła axe `target-size` w `design-check pages` |
| Strona nie przewija się w poziomie od szerokości 320 px (tabela danych może przewijać się we własnej ramce) | 1.4.10 | `design-check pages` przy 320 px |
| Wszystko da się obsłużyć klawiaturą, w kolejności zgodnej z układem | 2.1.1, 2.4.3 | przegląd; testy E2E wycinków |
| Każdy element interaktywny ma rolę i nazwę dostępną; przycisk z samą ikoną ma etykietę | 4.1.2 | reguły axe (`button-name`, `link-name`, `label`) |
| Struktura jest w znacznikach: jeden `main`, jeden `banner`, nawigacja i sekcje jako nazwane regiony, nagłówki w kolejności, tabele z nagłówkami | 1.3.1, 2.4.6 | `design-check pages` (role), reguły axe |
| Błąd w formularzu jest opisany słowami przy polu, a pole ma etykietę nad sobą | 3.3.1, 3.3.2 | arkusz komponentów (stan błędu), przegląd |
| Treść nie zależy od ruchu; ruch można wyłączyć ustawieniem systemu | 2.2.2, 2.3.3 (AAA, jako wskazówka) | przegląd; `prefers-reduced-motion` w stylach |

### Użyteczność: dziesięć heurystyk Nielsena

Źródło: [10 Usability Heuristics for User Interface Design](https://www.nngroup.com/articles/ten-usability-heuristics/) (Nielsen Norman Group). Lista kontrolna przeglądu heurystycznego; przy każdej to, co oznacza w tej aplikacji.

1. **Widoczność stanu systemu.** Data ostatniej aktualizacji danych per źródło jest zawsze widoczna. Ładowanie, pusta lista i błąd wyglądają tak samo w każdym widoku. Zaznaczony wiersz pokazuje, którego posta dotyczy panel obok.
2. **Zgodność ze światem użytkownika.** Słowa źródeł (Wykop: wpis, znalezisko; Reddit: post, link), stanowiska słowami z oceny, daty w czasie warszawskim.
3. **Kontrola i swoboda.** Wybrany post ma własny adres, więc działają „wstecz”, odświeżenie i link z powiadomienia. Filtry da się wyczyścić jednym przyciskiem.
4. **Spójność i standardy.** Ten sam wzorzec dla postów i celów: lista, a szczegóły w panelu wysuwanym z prawej; te same komponenty i etykiety w każdym widoku.
5. **Zapobieganie błędom.** Akcje admina o skutkach (start przebiegu, przywrócenie postów) mówią przed kliknięciem, czego dotyczą; niedostępna akcja jest wyłączona z podanym powodem.
6. **Rozpoznawanie zamiast przypominania.** Nawigacja ma opisy, nie same ikony. Aktywne filtry są widoczne przy liście.
7. **Elastyczność i sprawność.** Filtr i sortowanie żyją w adresie strony. Lista i ocena przewijają się niezależnie.
8. **Estetyka i umiar.** Kolor niesie znaczenie (stanowisko, stan) albo wskazuje akcję; ozdobniki bez informacji odpadają.
9. **Pomoc w błędach.** Komunikat mówi, co się nie udało i co zrobić („Spróbuj ponownie”); nie przeprasza i nie zgaduje.
10. **Pomoc i dokumentacja.** Przy ocenie stała notka, że opisuje stanowisko autora, a nie prognozę. Miary, które wymagają objaśnienia (proporcje, kolejność), mają je pod nagłówkiem.

### Wzorce układu z dojrzałych systemów projektowych

| Wzorzec | Źródło | Zastosowanie |
| --- | --- | --- |
| Lista ze szczegółami (list-detail) w panelu bocznym | [Material Design 3, układy kanoniczne](https://m3.material.io/foundations/layout/canonical-layouts) i [panele boczne](https://m3.material.io/components/side-sheets/overview) | Posty i katalog celów: lista w jednej kolumnie, szczegóły (ocena posta, karta celu) w panelu wysuwanym z prawej na życzenie, z listą widoczną w tle. Na wąskim ekranie panel zajmuje cały ekran. |
| Stała nawigacja główna | [Material Design 3, nawigacja](https://m3.material.io/components/navigation-rail/overview); [Carbon, UI shell](https://carbondesignsystem.com/components/UI-shell-header/usage/) | Jedna nawigacja w powłoce aplikacji zamiast nagłówka w każdym widoku. |
| Tabela danych | [Carbon, data table](https://carbondesignsystem.com/components/data-table/usage/) | Cele dnia, przebiegi, nieudane posty: nagłówki kolumn, liczby do prawej, wiersz jako cel kliknięcia. |
| Formularze | [GOV.UK Design System](https://design-system.service.gov.uk/components/text-input/) | Etykieta nad polem, podpowiedź pod etykietą, błąd słowami przy polu, jedna kolumna. |
| Pusty stan, ładowanie, błąd | [Carbon, empty states](https://carbondesignsystem.com/patterns/empty-states-pattern/) i [loading](https://carbondesignsystem.com/patterns/loading-pattern/) | Pusty stan mówi, co zrobić dalej; ładowanie jako szkielet treści o kształcie wyniku; błąd z przyciskiem ponowienia. |
| Zachowanie widżetów | [WAI-ARIA Authoring Practices](https://www.w3.org/WAI/ARIA/apg/patterns/), w tym [dialog](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/) | Panel oceny posta i karty celu jako dialog (fokus w panelu, Esc zamyka, fokus wraca na wybrany wiersz), lista powiadomień i panel filtrów jako rozwijany panel (disclosure), części panelu admina jako nawigacja drugiego poziomu (odnośniki, bieżący z `aria-current="page"`; wzorzec kart dotyczy paneli na jednej stronie, a tu każda część ma własny adres), przełącznik zakresu jako grupa przycisków ze stanem. |

### Typografia, siatka i ruch

- **Skala typografii**: zamknięty zestaw rozmiarów o stałym stosunku, każdy z interlinią i wagą; żadnych rozmiarów spoza skali. Tekst ciągły ma łam 45–75 znaków i interlinię 1,45–1,65 (R. Bringhurst, „Elementarz stylu w typografii”).
- **Siatka odstępów**: wielokrotności 4 px; odstęp wewnątrz grupy jest mniejszy niż między grupami (bliskość grupuje).
- **Liczby**: w tabelach cyfry tabelaryczne i wyrównanie do prawej.
- **Krój**: w plikach aplikacji, na otwartej licencji, z polskimi znakami; każda użyta grubość ma własne dane w pliku (aplikacja ma `font-synthesis: none`).
- **Ruch**: tylko jako odpowiedź na działanie użytkownika (otwarcie, rozwinięcie), do 200 ms; przy `prefers-reduced-motion` bez animacji. Zrzuty porównawcze w CI mają animacje wyłączone.
- **Dwa motywy**: jasny i ciemny na mechanizmie z S-46 (`data-theme`); każda rola koloru ma wartość w obu.

## Architektura informacji i nawigacja

### Mapa ekranów

| Ekran | Dla kogo | Adres (propozycja) | Wejście |
| --- | --- | --- | --- |
| Logowanie | każdy | `/login` (bez zmian) | adres aplikacji bez sesji |
| Aktywacja konta | każdy | `/activate` (bez zmian) | jednorazowy link |
| Przegląd dnia | użytkownik | `/` (ekran startowy docelowo) | nawigacja „Przegląd” |
| Posty: lista, ocena w panelu z prawej | użytkownik | `/posts`, wybrany post `/posts/:id` (ten adres już istnieje) | nawigacja „Posty”; link z przeglądu i z karty celu |
| Cele: katalog, karta celu w panelu z prawej | użytkownik | `/targets`, wybrany cel `/targets/:name` | nawigacja „Cele”; link z przeglądu |
| Panel admina: Przebiegi | admin | `/admin` (bez zmian) | nawigacja „Panel admina” |
| Panel admina: Jakość ocen | admin | `/admin/quality` | pasek części panelu admina |
| Panel admina: Nieudane posty | admin | `/admin/posts` | pasek części panelu admina |
| Przebieg | admin | `/admin/runs/:id` (bez zmian) | wiersz przebiegu; powiadomienie |

Wybrany post ma własny adres: działa link z powiadomienia i z innego ekranu, „wstecz” wraca do listy z filtrem, a odświeżenie strony nie gubi wyboru. To rekomendacja projektu dla niewiadomej S-51; ostatecznie rozstrzyga ją plan tego wycinka.

Czytanie posta z podświetlonymi cytatami nie jest osobnym ekranem. Podświetlenie i przejścia „poprzedni”/„następny” należą do panelu oceny (S-53 dodaje je do ekranu z S-51). Na wąskim ekranie panel oceny jest osobnym ekranem pod adresem posta.

Katalog celów i karta celu to jeden ekran w tym samym wzorcu co posty: lista celów, a karta wybranego celu w panelu z prawej.

### Nawigacja

Nawigacja główna jest częścią powłoki aplikacji (jeden `banner` na stronie), w tej kolejności: **Przegląd**, **Posty**, **Cele**, a dla admina także **Panel admina**. Kolejność odpowiada kolejności zadań: obraz dnia, lista, szukanie po celu, obsługa.

Obok nawigacji, w tej samej powłoce: data ostatniej aktualizacji danych per źródło (FR-030), powiadomienia (tylko admin), przełącznik „Ciemny motyw” (FR-040), e-mail zalogowanego i „Wyloguj” (FR-013). Ekrany publiczne (logowanie, aktywacja) nie mają nawigacji.

Nawigacja jest w pasku u góry strony, bez bocznej kolumny (decyzja właściciela, 2026-11-20).

### Zakres postów

Posty nieinwestycyjne i posty bez treści są domyślnie ukryte (FR-007). Zamiast zdania o ukrytych postach każdy ekran z listą albo liczbami ma przełącznik dwóch stanów z liczbami: „Inwestycyjne 128 | Wszystkie 165”. Na przeglądzie dnia stoi obok wyboru dnia, na ekranie postów przy nagłówku. Przełącza cały widok: listę, liczniki i proporcje.

### Ekran startowy

Docelowo po zalogowaniu otwiera się przegląd dnia (decyzja właściciela, 2026-11-19). Przegląd powstaje na końcu M-5 (S-59), więc do tego czasu ekranem startowym jest lista postów.

Stany przejściowe nawigacji:

| Po wdrożeniu | Pozycje nawigacji | Ekran startowy `/` |
| --- | --- | --- |
| S-47 (powłoka) | Posty, Panel admina (admin) | Posty |
| S-57 (katalog celów) | Posty, Cele, Panel admina (admin) | Posty |
| S-59 (przegląd dnia) | Przegląd, Posty, Cele, Panel admina (admin) | Przegląd |

Pozycja nawigacji pojawia się razem ze swoim ekranem; nie ma pozycji wyłączonych ani prowadzących donikąd.

### Panel admina

Trzy części pod wspólnym nagłówkiem „Panel admina”, każda z własnym adresem; przełącza je pasek nawigacji drugiego poziomu pod nagłówkiem:

- **Przebiegi**: nowy przebieg (zakres dat, źródła), dni bez danych z uzupełnieniem, ostatnie przebiegi. To praca codzienna, więc ta część otwiera panel.
- **Jakość ocen**: miary z 7 dni per źródło (nieudane próby, tezy w innym języku, po S-60 „kanał jako cel”) z progami.
- **Nieudane posty**: lista z etapem i ostatnią próbą, przywracanie zaznaczonych.

Strona przebiegu zostaje osobnym ekranem (liczniki, wynik per źródło, kroki, błąd), z powrotem do części „Przebiegi”.

### Szeroki i wąski ekran

- **Od 900 px**: pasek nawigacji u góry i jedna kolumna treści. Lista jest zwarta (jeden post w jednej linii) i zajmuje całą kolumnę, do około 70 rem. Szczegóły otwierają się w panelu wysuwanym z prawej (około 44 rem): lista przesuwa się do lewej krawędzi, jest lekko przyciemniona i zostaje widoczna, a wybrany wiersz jest zaznaczony. Panel przewija się niezależnie od listy.
- **Poniżej 900 px**: jedna kolumna. Post na liście zajmuje kilka linii, a panel szczegółów otwiera się na cały ekran; zamyka go przycisk „Zamknij” albo „wstecz” przeglądarki. Nawigacja zwija się do wiersza pod nazwą aplikacji.
- **Od 320 px**: strona nie przewija się w poziomie.

## Ekrany

Ekrany M-5 z wymaganiami i źródłem danych. „Dziś” znaczy, że API już daje te dane; numer wycinka wskazuje, kto dostarcza brakujące API albo widok. Prototypy każdego ekranu (oba motywy, 1280 i 375 px) powstają w fazie 3 w `ui-design/prototypes/screens/`.

| Ekran | Cel ekranu | Wymagania | Dane | Prototyp (faza 3) |
| --- | --- | --- | --- | --- |
| Przegląd dnia | Obraz wybranego dnia na pierwszy rzut oka: klocki z liczbą postów (ze źródłami), liczbą celów ze stanowiskiem i udziałem stanowisk wzrostowych, spadkowych i pozostałych; trzy najczęściej omawiane cele z proporcjami i najnowszymi postami; tabela wszystkich celów dnia | FR-039, FR-038, FR-035, FR-007 | S-59 (na S-55 i S-58) | `overview` |
| Posty: lista i ocena w panelu | Przejrzeć posty na liście zwartej i przeczytać ocenę wybranego w panelu z prawej: cele ze stanowiskiem, tezy z cytatem, link do oryginału, dane surowe | FR-007, FR-009, FR-010, FR-011a, FR-030 | dziś (lista, filtr po celu i stanowisku, ocena, link, dane surowe); układ S-51 | `posts`, `post` |
| Posty: filtry i kolejność | Zawęzić listę: szukanie w treści na wierzchu; cel, stanowisko, dzień albo zakres dat i kolejność pod przyciskiem „Filtry”; przełącznik zakresu „Inwestycyjne / Wszystkie” | FR-036, FR-035, FR-008, FR-011b, FR-007 | S-56, S-55, S-54, S-52 | `posts-filters` |
| Post z podświetlonymi cytatami | W panelu oceny: zobaczyć w treści fragmenty, na których oparto ocenę; wyłączyć podświetlenie; przejść do poprzedniego i następnego | FR-009 | dziś (cytaty są w ocenie); widok S-53 | `post` |
| Cele: katalog i karta celu w panelu | Znaleźć cel po nazwie, zobaczyć liczbę postów i proporcje stanowisk (wzrostowe, spadkowe, pozostałe), przejść do wypowiedzi o celu | FR-037, FR-038, FR-011a | S-57, S-58 | `targets`, `target` |
| Logowanie | Zalogować się e-mailem i hasłem; komunikaty błędnych danych i limitu prób | FR-012, FR-040 | dziś; widok S-49 | `login` |
| Aktywacja konta | Ustawić hasło z jednorazowego linku; link nieważny, reguły hasła | FR-018 | dziś; widok S-49 | `activate` |
| Powłoka aplikacji | Nawigacja u góry według roli, data aktualizacji danych, przełącznik motywu, wylogowanie | FR-015, FR-013, FR-030, FR-040 | dziś; widok S-47 | na każdym ekranie zalogowanym |
| Panel admina: Przebiegi | Uruchomić przebieg, uzupełnić dni bez danych, zobaczyć ostatnie przebiegi i ich stan | FR-021, FR-022, FR-015 | dziś; widok S-50 | `admin-runs` |
| Panel admina: Jakość ocen | Sprawdzić miary jakości per źródło wobec progów | FR-033 | dziś; „kanał jako cel” S-60 | `admin-quality` |
| Panel admina: Nieudane posty | Zobaczyć nieudane i martwe posty, przywrócić zaznaczone | FR-034 | dziś; widok S-50 | `admin-posts` |
| Przebieg | Liczniki, wynik per źródło, kroki, błąd i powody „wymaga uwagi” jednego przebiegu | FR-021 | dziś; widok S-50 | `run` |
| Powiadomienia admina | Lista przebiegów wymagających uwagi z przejściem do przebiegu | FR-021, FR-015 | dziś | `notifications` |
| Wspólne stany | Ładowanie, pusta lista i błąd z ponowieniem, takie same w każdym widoku | FR-007, FR-009 | dziś; komponenty S-48 | `state-loading`, `state-empty`, `state-error` |

Dni bez danych są częścią ekranu „Panel admina: Przebiegi”. Elementy zależne od wycinków, których niewiadome należą do właściciela (pola sortowania S-54, domyślny okres i próg katalogu S-57, progi S-60), prototypy pokazują jako propozycję niewiążącą do czasu planu danego wycinka.

## Język wizualny

Źródłem wartości jest [`ui-design/tokens.css`](ui-design/tokens.css): pierwszy blok `:root` to motyw jasny i wszystkie skale, blok `:root[data-theme='dark']` zmienia tylko role koloru. To ten sam format co `szczur-radar-web/src/style.css`, więc F-08 przenosi plik bez tłumaczenia. Pary kontrastu są w [`ui-design/pairs.json`](ui-design/pairs.json). Każdy token w użyciu pokazuje [arkusz komponentów](ui-design/prototypes/components.html) ([zrzut ciemny](ui-design/screens/components-dark-1280.png), [zrzut jasny](ui-design/screens/components-light-1280.png)).

### Charakter

Wygląd nazywa się „Salon w kolorach Radaru”, a układ stron „Kanał” (decyzje właściciela z 2026-11-20). Cztery zasady, z których wynika reszta:

- **Granat i błękit radaru.** Ciemny motyw jest pierwszy: granatowe tło, jaśniejsze granatowe powierzchnie, błękitny akcent. Motyw jasny ma te same role na chłodnej bieli. Bez wyboru użytkownika strona idzie za ustawieniem systemu.
- **Szeryf do czytania, grotesk do obsługi.** To, co użytkownik czyta (tytuły, treść posta, cytaty, liczby dnia), jest złożone szeryfem Newsreader. To, czym steruje (przyciski, pola, znaczniki, tabele), groteskiem Hanken Grotesk. Dzięki temu ekran wygląda jak czytelnia, a nie jak formularz.
- **Kolor znaczy.** Zieleń to wzrost i powodzenie, czerwień to spadek i błąd, bursztyn to uwaga i cytat, błękit to działanie. Poza tym ekran jest granatowy albo biały. Znaczenia nigdy nie niesie sam kolor: obok jest słowo albo znak.
- **Jedna rzecz naraz.** Jedna kolumna, jedna powierzchnia na grupę treści, szczegóły w panelu na życzenie, filtry pod przyciskiem. Na ekranie nie ma bocznych kolumn ani formularzy na wierzchu.

Znak marki to tarcza radaru z wiązką. Za nim, w lewym rogu paska aplikacji, są cienkie pierścienie radaru: jedyna ozdoba interfejsu.

### Kolor

| Rola | Jasny | Ciemny | Do czego | Zasada |
| --- | --- | --- | --- | --- |
| `--bg` | `#eef2f8` | `#0a1020` | tło strony | Nic poza stroną nie ma tego tła; na nim leżą karty i listy. |
| `--surface` | `#ffffff` | `#101a2e` | karty, ramki list, panele, pola | Jedna powierzchnia na grupę treści, bez karty w karcie. |
| `--surface-2` | `#f3f6fb` | `#16223a` | wskazany wiersz, blok wewnątrz panelu, wyłączone pole | Drugi poziom tylko wewnątrz `--surface`. |
| `--selected` | `#e2ecfb` | `#152847` | wybrany wiersz, wybrana opcja przełącznika, włączony przycisk-ikona | Zawsze razem z drugim sygnałem: paskiem akcentu, obwódką albo `aria-current`/`aria-pressed`. |
| `--scrim` | `#0a1020` | `#03060d` | przyciemnienie listy pod panelem bocznym, kolor cienia | Na liście 36% krycia: lista zostaje czytelna. |
| `--text-h` | `#0b1528` | `#f0f5ff` | nagłówki, tytuły postów, treść posta, wartości w tabelach | Najmocniejszy tekst; to, co użytkownik czyta najpierw. |
| `--text` | `#3f4d63` | `#a9b8d0` | tekst zwykły, opisy, pozycje nawigacji | Domyślny kolor tekstu. |
| `--text-muted` | `#57667d` | `#8a9bb8` | metadane: godzina, kanał, rodzaj celu, podpisy | Nigdy dla treści, bez której ekranu nie da się zrozumieć. |
| `--accent` | `#0b63c9` | `#5cc8ff` | przycisk główny, odnośniki, licznik dzwonka, znak marki | Jeden przycisk główny na widok. |
| `--on-accent` | `#ffffff` | `#04182a` | tekst i ikona na `--accent` | Tekst na `--accent` ma zawsze ten kolor. |
| `--ring` | `#0b63c9` | `#5cc8ff` | obwódka fokusu | 2 px, odsunięta o 2 px; w ramkach z przycięciem do środka. |
| `--line` | `#d3dbe8` | `#22314d` | linie podziału, krawędź karty i ramki listy | Ozdoba, nie granica kontrolki: nie musi mieć 3:1. |
| `--border` | `#6f7f99` | `#5f7396` | krawędź pola, przycisku i przełącznika; część „pozostałe” paska proporcji | Granica kontrolki, co najmniej 3:1 do tła. |
| `--success` | `#12753b` | `#4ade80` | stanowisko wzrostowe, powodzenie, stan „zakończony” | Zawsze ze słowem albo znakiem. |
| `--destructive` | `#c0322b` | `#ff7b72` | stanowisko spadkowe, błąd, stan „błąd” | Zawsze ze słowem albo znakiem. |
| `--warning` | `#8a5500` | `#f5c563` | uwaga, stany „częściowo” i „przerwany”, kreska cytatu | Zawsze ze słowem albo znakiem. |
| `--stance-neutral` | `#4a5870` | `#9fb0c9` | stanowisko neutralne, liczba „pozostałe” | Tylko dla stanowisk. |
| `--stance-mixed` | `#8a5500` | `#f5c563` | stanowisko mieszane | Tylko dla stanowisk. |
| `--success-soft` | `#dcf3e4` | `#0f3323` | tło znacznika i komunikatu powodzenia | Tylko pod tekstem `--success`, `--text-h` albo `--text`. |
| `--warning-soft` | `#fbeccb` | `#3a3012` | tło znacznika i komunikatu uwagi | Tylko pod tekstem `--warning`, `--text-h` albo `--text`. |
| `--destructive-soft` | `#fde3e0` | `#40191b` | tło znacznika i komunikatu błędu | Tylko pod tekstem `--destructive`, `--text-h` albo `--text`. |
| `--neutral-soft` | `#e7ecf3` | `#1d2a44` | tło znacznika neutralnego i „bez stanowiska” | Tylko pod tekstem `--stance-neutral`, `--text-muted`, `--text-h` albo `--text`. |
| `--mixed-soft` | `#fbeccb` | `#3a3012` | tło znacznika stanowiska mieszanego | Tylko pod tekstem `--stance-mixed`, `--text-h` albo `--text`. |
| `--highlight` | `#ffe9a8` | `#40360f` | tło cytatu w treści posta | Tylko dla fragmentów, na których oparto ocenę. |

Nazwy ról z S-46 zostają (`--bg`, `--surface`, `--text`, `--text-h`, `--accent`, `--on-accent`, `--success`, `--warning`, `--destructive`, `--highlight`, `--stance-neutral`, `--stance-mixed`); zmieniają się ich wartości. Nowe role to `--surface-2`, `--selected`, `--scrim`, `--text-muted`, `--ring`, `--line`, `--border` i pięć teł `-soft`.

Zasady użycia:

- Widok i komponent używają wyłącznie ról. Literał koloru jest tylko w pliku tokenów.
- Stan wskazania (hover) to zmiana tła na `--surface-2`, a dla przycisku głównego przyciemnienie akcentu. Wskazanie nigdy nie zmienia układu.
- `--warning` i `--stance-mixed` mają dziś tę samą wartość. To osobne role, bo znaczą co innego i mogą się rozejść.
- Cytat w treści posta to tło `--highlight` z kreską `--warning` pod spodem. Ta sama kreska stoi z lewej strony cytatu w ocenie, więc oko łączy cytat z jego miejscem w treści.

### Kontrast

Kontrola `tools/design-check` sprawdza 63 pary z `pairs.json` w obu motywach: tekst co najmniej 4,5:1, elementy interfejsu i fokus co najmniej 3:1 (WCAG 1.4.3 i 1.4.11). Wszystkie przechodzą. Osiem par najbliżej progu:

| Para | Rodzaj | Próg | Jasny | Ciemny |
| --- | --- | --- | --- | --- |
| `--destructive` na `--destructive-soft` | tekst | 4,5:1 | 4,62:1 | 6,07:1 |
| `--destructive` na `--selected` | tekst | 4,5:1 | 4,72:1 | 5,84:1 |
| `--accent` na `--selected` | tekst | 4,5:1 | 4,84:1 | 7,82:1 |
| `--success` na `--selected` | tekst | 4,5:1 | 4,85:1 | 8,45:1 |
| `--text-muted` na `--selected` | tekst | 4,5:1 | 4,89:1 | 5,23:1 |
| `--text-muted` na `--neutral-soft` | tekst | 4,5:1 | 4,91:1 | 5,09:1 |
| `--success` na `--success-soft` | tekst | 4,5:1 | 4,95:1 | 7,94:1 |
| `--border` na `--surface-2` | element interfejsu | 3:1 | 3,75:1 | 3,31:1 |

Nowa para koloru tekstu i tła w komponencie trafia do `pairs.json`, zanim trafi do widoku.

### Pismo

| Krój | Token | Rola | Pliki | Grubości w użyciu |
| --- | --- | --- | --- | --- |
| Newsreader | `--font-display` | to, co się czyta: tytuły, treść posta, cytaty, liczby dnia | `newsreader-latin-wght-normal.woff2`, `newsreader-latin-ext-wght-normal.woff2` | 400, 500 |
| Hanken Grotesk | `--font-ui` | to, czym się steruje: przyciski, pola, znaczniki, tabele, metadane | `hanken-grotesk-latin-wght-normal.woff2`, `hanken-grotesk-latin-ext-wght-normal.woff2` | 400, 500, 600 |
| JetBrains Mono | `--font-mono` | surowe dane JSON | `jetbrains-mono-latin-wght-normal.woff2`, `jetbrains-mono-latin-ext-wght-normal.woff2` | 400 |

Pliki są w [`ui-design/fonts/`](ui-design/fonts/README.md) na licencji SIL OFL 1.1, razem około 200 kB (budżet 300 kB). To kroje zmienne: jeden plik na podzbiór znaków (łaciński i łaciński rozszerzony z polskimi literami) zawiera wszystkie grubości, więc przy `font-synthesis: none` każda użyta grubość ma własne dane. Znaki spoza tych podzbiorów (strzałki, trójkąty) są ikonami SVG, nie tekstem. JetBrains Mono przeglądarka pobiera dopiero po otwarciu surowych danych.

| Token | Rozmiar | Krój | Do czego |
| --- | --- | --- | --- |
| `--font-size-xs` | `0.8125rem` (13 px) | interfejs | metadane, podpisy, nagłówki kolumn, licznik dzwonka |
| `--font-size-sm` | `0.875rem` (14 px) | interfejs | znaczniki, etykiety pól, nagłówek dnia w liście |
| `--font-size-md` | `0.9375rem` (15 px) | interfejs | tekst interfejsu: przyciski, pola, nawigacja, twierdzenia (rozmiar domyślny) |
| `--font-size-lg` | `1rem` (16 px) | interfejs | duże pole wyszukiwania |
| `--font-size-xl` | `1.125rem` (18 px) | szeryf | tytuł posta w liście, cytat w ocenie |
| `--font-size-2xl` | `1.25rem` (20 px) | szeryf | treść posta, podtytuł strony, nazwa celu w tabeli |
| `--font-size-3xl` | `1.375rem` (22 px) | szeryf | nazwa aplikacji, nazwa celu w ocenie, tytuł stanu pustego i błędu |
| `--font-size-4xl` | `1.625rem` (26 px) | szeryf | nagłówek sekcji (h2) |
| `--font-size-5xl` | `2rem` (32 px) | szeryf | nazwa celu na karcie dnia; tytuł posta na wąskim ekranie |
| `--font-size-6xl` | `2.375rem` (38 px) | szeryf | tytuł otwartego posta; tytuł strony na wąskim ekranie |
| `--font-size-display` | `3rem` (48 px) | szeryf | tytuł strony (h1), liczba w klocku |

Interlinia: `--leading-tight` (1.12) dla tytułów od 32 px, `--leading-snug` (1.3) dla mniejszych tytułów i tytułu posta w liście, `--leading-normal` (1.5) dla tekstu interfejsu, `--leading-relaxed` (1.6) dla treści posta. Grubość: `--weight-regular` (400) dla tekstu, `--weight-medium` (500) dla tytułów szeryfem, etykiet pól i przycisków, `--weight-semibold` (600) dla nazwy celu w znaczniku, wybranej pozycji i przycisku głównego. Światło między literami: `--tracking-tight` (-0.025em) dla szeryfu od 32 px, `--tracking-snug` (-0.012em) dla mniejszych tytułów szeryfem; tekst interfejsu bez zmian.

Zasady: żadnego rozmiaru spoza skali; żadnych wersalików w etykietach; liczby w tabelach, licznikach i godzinach mają cyfry tabelaryczne; łam treści posta ma najwyżej `--width-reading`.

### Odstępy

| Token | Wartość | Do czego |
| --- | --- | --- |
| `--space-0` | `0.125rem` (2 px) | korekty wewnątrz elementu: znak przy tekście, wnętrze przełącznika |
| `--space-1` | `0.25rem` (4 px) | ikona przy tekście, etykieta nad polem |
| `--space-2` | `0.5rem` (8 px) | elementy w jednym wierszu: znaczniki, przyciski obok siebie |
| `--space-3` | `0.75rem` (12 px) | wnętrze wiersza i pola, odstęp między kartami w rzędzie liczb |
| `--space-4` | `1rem` (16 px) | wnętrze karty i panelu filtrów, odstęp między kartami |
| `--space-5` | `1.5rem` (24 px) | wnętrze karty celu, margines strony, odstęp między blokami jednej sekcji |
| `--space-6` | `2rem` (32 px) | odstęp między grupami: dni w liście, nagłówek a treść |
| `--space-7` | `2.5rem` (40 px) | wnętrze panelu bocznego, góra strony |
| `--space-8` | `3rem` (48 px) | odstęp między sekcjami strony |
| `--space-9` | `4rem` (64 px) | dół strony |

Siatka ma krok 4 px. Odstęp wewnątrz grupy jest zawsze mniejszy niż między grupami. Rodzeństwo rozdziela `gap` rodzica, nie marginesy elementów.

### Promienie

| Token | Wartość | Do czego |
| --- | --- | --- |
| `--radius-sm` | `6px` | cytat w treści, pasek szkieletu |
| `--radius` | `10px` | przycisk, pole, komunikat |
| `--radius-lg` | `16px` | duże pole i duży przycisk, blok celu w ocenie, lista powiadomień |
| `--radius-xl` | `20px` | karta, ramka listy, klocek liczby, tabela |
| `--radius-2xl` | `24px` | panel boczny, karta logowania |
| `--radius-full` | `999px` | znacznik, przełącznik zakresu, pozycja nawigacji, pasek proporcji |

Im większy element, tym większy promień. Element wewnątrz ma promień mniejszy niż jego ramka.

### Wyniesienie i warstwy

Karty i listy leżą płasko: od tła odróżnia je kolor `--surface` i linia `--line`, bez cienia. Cień `--shadow-overlay` (`0 1.5rem 4rem -1rem color-mix(in srgb, var(--scrim) 40%, transparent)`) mają tylko warstwy nad stroną: panel boczny, lista powiadomień i panel filtrów. Kolejność warstw: `--z-scrim` (10) dla przyciemnienia, `--z-overlay` (20) dla tego, co nad nim.

### Wymiary i układ

| Token | Wartość | Do czego |
| --- | --- | --- |
| `--control-height-sm` | `2.125rem` (34 px) | opcja przełącznika zakresu |
| `--control-height` | `2.5rem` (40 px) | przycisk, pole, przycisk-ikona, pozycja nawigacji: pole i przycisk w jednym wierszu mają tę samą wysokość |
| `--control-height-lg` | `3.25rem` (52 px) | duże pole wyszukiwania i przycisk „Filtry” obok |
| `--icon-size` | `1.25rem` (20 px) | ikona w przycisku-ikonie; w przycisku z tekstem 18 px |
| `--icon-stroke` | `1.5` | grubość kreski ikon (w jednostkach siatki 24 px) |
| `--bar-height` | `0.5rem` (8 px) | pasek proporcji w wierszu tabeli |
| `--bar-height-lg` | `0.75rem` (12 px) | pasek proporcji na karcie celu |
| `--width-content` | `70rem` (1120 px) | największa szerokość kolumny treści |
| `--width-panel` | `44rem` (704 px) | szerokość panelu bocznego |
| `--width-reading` | `38rem` (608 px) | największa szerokość łamu treści posta (około 70 znaków) |

Punkt przełamania układu to 900 px: od tej szerokości lista zwarta i panel boczny, poniżej jedna kolumna i panel na cały ekran. Zapytania o szerokość nie czytają zmiennych CSS, więc 900 px jest stałą układu (`max-width: 899px`), nie tokenem. Od 320 px strona nie przewija się w poziomie; w poziomie może przewijać się tylko tabela wewnątrz własnej ramki.

### Ruch

| Token | Wartość | Do czego |
| --- | --- | --- |
| `--duration-fast` | `120ms` | zmiana tła i koloru po wskazaniu |
| `--duration` | `200ms` | wysunięcie panelu bocznego, otwarcie listy powiadomień i filtrów |
| `--ease-out` | `cubic-bezier(0.2, 0, 0, 1)` | krzywa każdego przejścia: szybki start, łagodny koniec |

Ruch jest tylko odpowiedzią na działanie użytkownika. Nic nie wjeżdża przy wczytaniu strony. Szkielet ładowania pulsuje, a znak pracy w przycisku się obraca, tylko gdy system pozwala na ruch (`prefers-reduced-motion: no-preference`); bez ruchu oba zostają widoczne i nieruchome.

### Ikony

Ikony pochodzą z jednego zestawu, [Lucide](https://lucide.dev/) 1.77.0 (licencja ISC), plus sześć własnych: znak marki i pięć znaków stanowiska. Pliki i licencja są w [`ui-design/icons/`](ui-design/icons/README.md). Każda ikona rysuje się kolorem tekstu (`currentColor`), kreską `--icon-stroke`. Ikona nigdy nie stoi sama bez nazwy: ma tekst obok albo nazwę dla czytnika ekranu na przycisku, a sama jest ukryta przed czytnikiem.

| Ikona | Gdzie |
| --- | --- |
| `brand-mark` | znak marki w pasku aplikacji i na ekranach logowania |
| `bell` | dzwonek powiadomień |
| `moon` | przycisk „Ciemny motyw” |
| `search` | pole wyszukiwania; pusta lista po filtrach |
| `list-filter` | przycisk „Filtry” |
| `chevron-left`, `chevron-right` | poprzedni i następny post w panelu; poprzedni i następny dzień; „Nowsze” i „Starsze” |
| `chevron-down` | rozwijane części (kroki przebiegu) |
| `x` | zamknięcie panelu bocznego i panelu filtrów |
| `external-link` | odnośnik do oryginału poza aplikacją |
| `braces` | przycisk „Surowe dane” |
| `circle-alert` | błąd: komunikat, błąd pola, stan błędu widoku, znacznik „błąd” |
| `circle-check` | komunikat powodzenia |
| `triangle-alert` | komunikat uwagi, znacznik stanu wymagającego uwagi |
| `info` | informacja bez oceny: post czeka na ocenę |
| `refresh-cw` | „Spróbuj ponownie” |
| `play` | „Uruchom” przebieg |
| `rotate-ccw` | „Przywróć zaznaczone” nieudane posty |
| `inbox` | pusta lista: dzień bez danych |
| `arrow-down-up` | kolejność listy |
| `calendar` | dzień w nagłówku listy tam, gdzie nie ma pola daty |
| `stance-up`, `stance-down`, `stance-flat`, `stance-mixed`, `stance-none` | znak stanowiska: wzrostowo, spadkowo, neutralnie, mieszanie, bez stanowiska i niejasno |

## Komponenty

Każdy komponent ma sekcję na [arkuszu komponentów](ui-design/prototypes/components.html) ze wszystkimi stanami w obu motywach. Zrzuty arkusza: [ciemny 1280](ui-design/screens/components-dark-1280.png), [jasny 1280](ui-design/screens/components-light-1280.png), [ciemny 375](ui-design/screens/components-dark-375.png), [jasny 375](ui-design/screens/components-light-375.png). Style komponentów są w `ui-design/prototypes/prototype.css` i używają wyłącznie tokenów.

### Komponenty istniejące i nowe

Jedenaście komponentów aplikacji zostaje, zmienia się ich wygląd. Układ „Kanał” dodaje dziesięć nowych. Komponenty buduje S-48 na tokenach z F-08; pełna mapa wdrożenia powstaje w sekcji „Wdrożenie”.

| Komponent w projekcie | W aplikacji dziś | Zmiana |
| --- | --- | --- |
| [Przycisk](ui-design/prototypes/components.html#c-przycisk) | `AppButton` | nowy wygląd; dochodzą warianty z ikoną, sama ikona i duży |
| [Komunikat](ui-design/prototypes/components.html#c-komunikat) | `StatusMessage` | nowy wygląd z ikoną; dochodzi ton „uwaga” |
| [Znacznik stanu](ui-design/prototypes/components.html#c-znacznik-stanu) | `StatusBadge` | nowy wygląd; te same tony |
| [Stanowisko](ui-design/prototypes/components.html#c-stanowisko) | `StanceText` | dochodzi znak i tło znacznika; słowa bez zmian |
| [Wiersz posta](ui-design/prototypes/components.html#c-wiersz-posta) | `PostCard` | karta staje się wierszem listy zwartej; ocena przechodzi do panelu |
| [Cel w ocenie](ui-design/prototypes/components.html#c-karta-celu) | `PostAssessment` | twierdzenia pod celem zostają; dochodzi cytat z kreską |
| [Filtry](ui-design/prototypes/components.html#c-filtry) | `FeedFilter` | wyszukiwanie zostaje na wierzchu, reszta pól przechodzi pod przycisk „Filtry” |
| [Powiadomienia](ui-design/prototypes/components.html#c-powiadomienia) | `NotificationMenu`, `NotificationBell` | nowy wygląd; zachowanie bez zmian |
| [Motyw](ui-design/prototypes/components.html#c-motyw) | `ThemeToggle` | przycisk z samą ikoną; nazwa „Ciemny motyw” zostaje |
| [Nawigacja](ui-design/prototypes/components.html#c-nawigacja) | `AppHeader` | pasek u góry z pozycjami według roli i datą danych |
| [Odnośnik](ui-design/prototypes/components.html#c-odnosnik), [Pola](ui-design/prototypes/components.html#c-pola) | style globalne w `style.css` | nowy wygląd; pola dalej mają jeden styl globalny |
| [Przełącznik zakresu](ui-design/prototypes/components.html#c-przelacznik), [Cel ze stanowiskiem](ui-design/prototypes/components.html#c-cel), [Pasek proporcji](ui-design/prototypes/components.html#c-pasek-proporcji), [Nagłówek strony](ui-design/prototypes/components.html#c-naglowek-strony), [Klocek liczby](ui-design/prototypes/components.html#c-klocek-liczby), [Karta celu dnia](ui-design/prototypes/components.html#c-karta-tematu), [Panel boczny](ui-design/prototypes/components.html#c-panel), [Cytat w treści](ui-design/prototypes/components.html#c-cytat), [Części panelu admina](ui-design/prototypes/components.html#c-podnawigacja), [Tabela](ui-design/prototypes/components.html#c-tabela) | brak | nowe |
| [Stronicowanie](ui-design/prototypes/components.html#c-stronicowanie), [Ładowanie](ui-design/prototypes/components.html#c-szkielet), [Pusta lista](ui-design/prototypes/components.html#c-pusta-lista), [Błąd z ponowieniem](ui-design/prototypes/components.html#c-blad) | w widokach, bez wspólnego komponentu | stają się wspólnymi komponentami |

### Stany

Siedem stanów każdego komponentu. „tak” znaczy, że arkusz pokazuje ten stan; litera wskazuje powód, dla którego stan nie dotyczy komponentu.

| Komponent | Domyślny | Wskazany | Fokus | Wyłączony | Błąd | Pusty | Ładowanie |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Przycisk | tak | tak | tak | tak | B | B | tak („w toku”) |
| Odnośnik | tak | tak | tak | C | B | B | B |
| Pola | tak | tak | tak | tak | tak | tak | B |
| Przełącznik zakresu | tak | tak | tak | tak | D | tak (liczby 0) | tak |
| Znacznik stanu | tak | A | A | A | B | B | B |
| Stanowisko | tak | A | A | A | B | tak („bez stanowiska”) | B |
| Cel ze stanowiskiem | tak | A | A | A | B | B | B |
| Pasek proporcji | tak | A | A | A | B | tak | B |
| Nagłówek strony | tak | E | E | E | D | B | B |
| Klocek liczby | tak | A | A | A | D | tak | tak |
| Karta celu dnia | tak | tak | tak | C | D | tak | tak |
| Wiersz posta | tak | tak | tak | C | tak („ocena nieudana”) | tak („czeka na ocenę”) | tak |
| Filtry | tak | E | E | E | D | tak (bez filtrów) | B |
| Stronicowanie | tak | E | E | tak | D | C | tak |
| Panel boczny | tak | E | E | C | F | tak (post bez oceny) | F |
| Cytat w treści | tak | A | A | tak (podświetlenie wyłączone) | B | B | B |
| Cel w ocenie | tak | A | A | A | B | tak (bez twierdzeń) | B |
| Powiadomienia | tak | tak | tak | C | tak | tak | tak |
| Części panelu admina | tak | tak | tak | C | B | B | B |
| Tabela | tak | tak | E | C | D | F | tak |
| Komunikat | tak | A | A | A | tak (ton błędu) | B | B |
| Ładowanie | tak | A | A | A | D | B | tak |
| Pusta lista | tak | E | E | C | D | tak | B |
| Błąd z ponowieniem | tak | E | E | C | tak | B | tak (ponawianie) |
| Motyw | tak | tak | tak | C | B | B | B |
| Nawigacja | tak | tak | tak | C | B | B | B |

- **A**: element nie jest interaktywny, więc nie ma wskazania, fokusu ani stanu wyłączonego.
- **B**: element sam nie wczytuje danych; błąd, pustkę i ładowanie pokazuje widok, w którym stoi.
- **C**: zamiast stanu wyłączonego element nie jest rysowany (pozycja nawigacji bez uprawnień, stronicowanie jednej strony, odnośnik donikąd).
- **D**: błąd wczytania pokazuje komponent „Błąd z ponowieniem” w miejscu treści widoku.
- **E**: stany wskazania, fokusu i wyłączenia mają kontrolki wewnątrz (przyciski, pola, odnośniki), nie sam kontener.
- **F**: kontener pokazuje w swoim wnętrzu wspólne komponenty „Ładowanie”, „Pusta lista” albo „Błąd z ponowieniem”.

### Sterowanie

**[Przycisk](ui-design/prototypes/components.html#c-przycisk)**. Uruchamia działanie. Budowa: opcjonalna ikona, tekst; wysokość `--control-height`. Warianty: główny (tło `--accent`, jeden na widok), zwykły (krawędź `--border`), z ikoną, duży (obok dużego wyszukiwania), sama ikona. W toku: znak pracy, tekst zmienia się na nazwę trwającego działania („Uruchamianie…”), przycisk nie przyjmuje kliknięć i ma `aria-busy="true"`. Dostępność: element `button`; przycisk z samą ikoną ma `aria-label`; przycisk dwustanowy ma `aria-pressed`; cel dotyku co najmniej 24 px (WCAG 2.5.8), tu 40 px.

**[Odnośnik](ui-design/prototypes/components.html#c-odnosnik)**. Prowadzi do innego miejsca. Kolor `--accent`, zawsze podkreślony; wskazany ma grubsze podkreślenie. Odnośnik poza aplikację ma ikonę `external-link`, otwiera nową kartę i mówi o tym w `aria-label` („otwiera się w nowej karcie”).

**[Pola](ui-design/prototypes/components.html#c-pola)**. Tekst, hasło, wyszukiwanie, data, lista wyboru, pole wyboru. Budowa: etykieta nad polem, pole, pod polem podpowiedź albo błąd. Wysokość jak przycisk, więc wiersz pól i przycisków się wyrównuje. Błąd: czerwona krawędź, ikona i zdanie pod polem. Dostępność: `label` powiązany z polem; błąd i podpowiedź przez `aria-describedby`, błędne pole ma `aria-invalid="true"`; tekst zastępczy nie zastępuje etykiety (duże wyszukiwanie nad listą ma `aria-label`). Pole daty jest natywne: format i kalendarz daje przeglądarka.

**[Przełącznik zakresu](ui-design/prototypes/components.html#c-przelacznik)**. Dwie opcje z liczbą postów: „Inwestycyjne” (domyślna) i „Wszystkie”. Przełącza cały widok. Wybrana opcja ma tło `--selected`, obwódkę akcentu i grubszy tekst. Dostępność: `role="group"` z nazwą „Zakres postów”, opcje to przyciski z `aria-pressed`. Podczas ładowania opcje są wyłączone i bez liczb.

**[Filtry](ui-design/prototypes/components.html#c-filtry)**. Nad listą: duże wyszukiwanie i przycisk „Filtry”. Pod przyciskiem otwiera się panel z polami (rodzaj celu, stanowisko, zakres dat, kolejność) i przyciskami „Filtruj” oraz „Wyczyść filtry”. Włączone filtry widać na przycisku jako liczbę („Filtry: 2”). Dostępność: formularz `role="search"` z nazwą „Filtr postów”; przycisk ma `aria-expanded`; Esc zamyka panel i oddaje fokus przyciskowi. Filtr dalej żyje w adresie strony.

**[Stronicowanie](ui-design/prototypes/components.html#c-stronicowanie)**. „Nowsze”, numer strony, „Starsze”. Na pierwszej stronie „Nowsze” jest wyłączone, na ostatniej „Starsze”. Dostępność: `nav` z nazwą „Strony”.

**[Motyw](ui-design/prototypes/components.html#c-motyw)**. Przycisk z ikoną księżyca i nazwą „Ciemny motyw”; `aria-pressed` mówi, czy ciemny motyw jest włączony. Zachowanie z S-46 bez zmian.

### Znaczenie

**[Znacznik stanu](ui-design/prototypes/components.html#c-znacznik-stanu)**. Stan przebiegu, kroku albo źródła. Tekst mówi stan, ton go wzmacnia: neutralny, informacja, powodzenie, uwaga, błąd. Stan wymagający uwagi może mieć ikonę. Słowa stanów i ich tony bez zmian (`src/runs.ts`).

**[Stanowisko](ui-design/prototypes/components.html#c-stanowisko)**. Stanowisko autora wobec celu: znak, słowo i kolor. Sześć kierunków: wzrostowo, spadkowo, neutralnie, mieszanie, bez stanowiska, niejasno; każde może mieć dopisek „(ostrożnie)”. Trzy sygnały naraz, więc stanowisko czyta też osoba, która nie rozróżnia zieleni i czerwieni (WCAG 1.4.1).

**[Cel ze stanowiskiem](ui-design/prototypes/components.html#c-cel)**. Nazwa celu i stanowisko w jednym znaczniku; tam, gdzie jest miejsce, także rodzaj celu. Znacznik zostaje w jednej linii; na bardzo wąskim ekranie skraca się nazwa celu, nie stanowisko.

**[Pasek proporcji](ui-design/prototypes/components.html#c-pasek-proporcji)**. Udział stanowisk wzrostowych, spadkowych i pozostałych wobec celu. Dostępność: pasek to `role="img"` z opisem liczbami; te same liczby stoją obok jako tekst. Cel bez stanowisk ma pusty pasek i napis „Brak stanowisk”.

**[Cytat w treści](ui-design/prototypes/components.html#c-cytat)**. Fragment treści posta, na którym oparto ocenę: element `mark` z tłem `--highlight` i kreską `--warning`. Pole wyboru „Podświetl cytaty z oceny” wyłącza podświetlenie; tekst się wtedy nie przesuwa.

**[Komunikat](ui-design/prototypes/components.html#c-komunikat)**. Wynik działania albo stan, który trzeba znać: powodzenie (`role="status"`), błąd (`role="alert"`), uwaga (`role="status"`). Budowa: ikona tonu i zdanie. Stoi przy tym, czego dotyczy, nie na górze strony.

### Treść

**[Wiersz posta](ui-design/prototypes/components.html#c-wiersz-posta)**. Jeden post w jednej linii: godzina, źródło i kanał; tytuł szeryfem; z prawej cele ze stanowiskami. Wiersze jednego dnia stoją w jednej ramce pod nagłówkiem dnia. Cały wiersz jest odnośnikiem do posta i otwiera panel boczny. Wybrany wiersz ma tło `--selected`, pasek akcentu z lewej i `aria-current="true"`. Gdy celów jest więcej niż się mieści, wiersz pokazuje dwa i napis „i 3 inne”. Post bez oceny ma „czeka na ocenę”, nieinwestycyjny „nie inwestycyjny”, z nieudaną oceną „ocena nieudana”. Poniżej 900 px wiersz ma kilka linii: metadane, tytuł, cele.

**[Panel boczny](ui-design/prototypes/components.html#c-panel)**. Ocena wybranego posta albo karta wybranego celu. Wysuwa się z prawej (szerokość `--width-panel`), lista przesuwa się do lewej krawędzi i zostaje widoczna pod lekkim przyciemnieniem. Budowa: pasek (źródło, kanał, typ i data posta; „4 z 128”; poprzedni, następny, zamknij) i przewijana treść. Dostępność: `role="dialog"` z nazwą z tytułu posta i `aria-modal="true"`; po otwarciu fokus przechodzi do panelu, Tab krąży w panelu, Esc zamyka, a fokus wraca na wybrany wiersz (wzorzec dialogu z WAI-ARIA Authoring Practices). Poniżej 900 px panel zajmuje cały ekran. Wybrany post ma własny adres.

**[Cel w ocenie](ui-design/prototypes/components.html#c-karta-celu)**. Jeden cel oceny: nazwa, rodzaj, stanowisko; pod spodem twierdzenia, każde z rodzajem („informacja”, „opinia”, „teza inwestycyjna”, „pytanie”, „działanie”) i cytatem z treści. Cel bez twierdzeń ma napis „Wspomniany, bez twierdzeń.”. Pod oceną: data oceny i przycisk „Surowe dane”, który rozwija dane źródła krojem `--font-mono`.

**[Klocek liczby](ui-design/prototypes/components.html#c-klocek-liczby)**. Jedna liczba dnia: podpis, liczba szeryfem, drobne wyjaśnienie. Przegląd dnia ma pięć: Posty, Cele ze stanowiskiem, Wzrostowe, Spadkowe, Pozostałe. Udziały mają znak i kolor stanowiska. Dostępność: lista opisowa (`dl`), więc czytnik czyta podpis razem z liczbą. Dzień bez stanowisk pokazuje kreskę i „brak stanowisk”.

**[Karta celu dnia](ui-design/prototypes/components.html#c-karta-tematu)**. Najczęściej omawiany cel: nazwa (odnośnik do celu), liczba postów, pasek proporcji z liczbami, najnowsze posty ze stanowiskiem. Przegląd dnia ma trzy karty obok siebie, poniżej 900 px jedną pod drugą.

**[Tabela](ui-design/prototypes/components.html#c-tabela)**. Dane w wierszach i kolumnach: cele dnia, przebiegi, nieudane posty. Liczby są wyrównane do prawej i mają cyfry tabelaryczne; zero jest wyszarzone. Pierwsza komórka wiersza jest jego nagłówkiem (`th scope="row"`) i odnośnikiem dalej. Wiersz zaznacza pole wyboru z nazwą („Zaznacz post: …”). Na wąskim ekranie tabela przewija się w poziomie wewnątrz własnej ramki.

### Powłoka i nawigacja

**[Nawigacja](ui-design/prototypes/components.html#c-nawigacja)**. Pasek u góry każdej strony po zalogowaniu: znak i nazwa aplikacji, pozycje (Przegląd, Posty, Cele, dla admina Panel admina), data danych per źródło, dzwonek (admin), motyw, e-mail, „Wyloguj”. Bieżąca pozycja ma `aria-current="page"`. Dostępność: jeden `banner` na stronie, `nav` z nazwą „Główna”. Poniżej 900 px pozycje schodzą do drugiego wiersza, a data danych i e-mail znikają z paska.

**[Nagłówek strony](ui-design/prototypes/components.html#c-naglowek-strony)**. Tytuł strony (`h1`, jeden na stronie) i opcjonalny podtytuł; z prawej tylko to, co zmienia cały widok: przełącznik zakresu i wybór dnia (poprzedni, pole daty, następny). Po zmianie widoku fokus przechodzi na tytuł.

**[Części panelu admina](ui-design/prototypes/components.html#c-podnawigacja)**. Pasek pod nagłówkiem „Panel admina”: Przebiegi, Jakość ocen, Nieudane posty. Każda część ma własny adres, więc to odnośniki w `nav` z nazwą „Panel admina”, a bieżąca ma `aria-current="page"`. Część może mieć licznik.

**[Powiadomienia](ui-design/prototypes/components.html#c-powiadomienia)**. Dzwonek z licznikiem nieprzeczytanych i lista pod nim. Powiadomienie: znacznik stanu przebiegu, zdanie, rodzaj przebiegu i czas; nieprzeczytane ma pasek akcentu i grubszy tekst. Dostępność: przycisk z nazwą „Powiadomienia, 2 nieprzeczytane” i `aria-expanded`; lista to `region` z nazwą „Powiadomienia”; Esc zamyka. Widzi je tylko administrator.

### Stany widoku

**[Ładowanie](ui-design/prototypes/components.html#c-szkielet)**. Szkielet o kształcie wyniku: wiersze listy, klocki, karta. Kontener ma `aria-busy="true"`, a czytnik słyszy „Wczytywanie…”. Bez osobnego ekranu z kręcącym się kółkiem.

**[Pusta lista](ui-design/prototypes/components.html#c-pusta-lista)**. Ikona, tytuł mówiący, czego nie ma, zdanie dlaczego i jeden przycisk z następnym krokiem („Wyczyść filtry”, „Pokaż ostatni dzień z danymi”).

**[Błąd z ponowieniem](ui-design/prototypes/components.html#c-blad)**. Ikona, tytuł mówiący, co się nie udało, zdanie o tym, co zostało (filtry, wybrany dzień), i przycisk „Spróbuj ponownie”. `role="alert"`. Podczas ponawiania przycisk jest w toku.

## Treść i etykiety

### Zasady

- **Zwykłe słowa.** Nazwy mówią, co użytkownik zobaczy albo zrobi, nie jak działa system: „Posty”, „Cele”, „Filtry”, „Surowe dane”.
- **Jedno słowo na jedną rzecz.** Zawsze „post” (nie „wpis”; „wpis” to tylko typ posta na Wykopie), „cel”, „stanowisko”, „twierdzenie”, „ocena”, „przebieg”. Słownik: `context/domain/glossary.md`.
- **Przycisk to czasownik** i mówi, co się stanie: „Filtruj”, „Wyczyść filtry”, „Uruchom”, „Spróbuj ponownie”. Działanie ma tę samą nazwę od przycisku po komunikat.
- **Wielkość liter jak w zdaniu.** Nazwy ekranów, przycisków i pól wielką literą; wartości (stany, stanowiska, rodzaje) małą: „zakończony”, „wzrostowo”, „spółka”. Bez wersalików.
- **Błąd mówi, co się stało i co zrobić**, bez przepraszania i bez kodów: „Nie udało się wczytać postów.” i przycisk „Spróbuj ponownie”.
- **Pusty widok zaprasza do działania**: mówi, czego nie ma, dlaczego, i daje jeden następny krok.
- **Liczba zamiast zdania.** To, co jest liczbą, stoi jako liczba z podpisem (klocek, licznik w przełączniku), nie w zdaniu objaśniającym.

### Nazwy

Etykiety, po których testy E2E znajdują elementy, zostają. Zmiany są w ostatniej kolumnie z uzasadnieniem; kontrakt nazw per ekran powstaje w sekcji „Kontrakt dla testów E2E”.

| Miejsce | Etykieta | Dziś | Uwaga |
| --- | --- | --- | --- |
| Nawigacja | „Przegląd” | brak | nowa pozycja (S-59) |
| Nawigacja | „Posty” | „Posty” | bez zmian |
| Nawigacja | „Cele” | brak | nowa pozycja (S-57) |
| Nawigacja | „Panel admina” | „Panel admina” | bez zmian |
| Pasek aplikacji | „Wyloguj”, „Ciemny motyw”, „Powiadomienia, N nieprzeczytanych” | te same | bez zmian |
| Tytuły stron | „Przegląd dnia”, „Posty”, „Cele”, „Panel admina”, „Logowanie”, „Ustawienie hasła” | „Posty”, „Logowanie”, „Ustawienie hasła” | istniejące bez zmian |
| Zakres postów | „Inwestycyjne”, „Wszystkie” | brak | nowy przełącznik zamiast zdania o ukrytych postach |
| Wyszukiwanie | „Spółka lub temat”; tekst zastępczy „Szukaj spółki lub tematu” | „Spółka lub temat” | nazwa pola bez zmian |
| Filtry | „Filtry”, „Rodzaj celu”, „Stanowisko”, „Od”, „Do”, „Kolejność”, „Filtruj”, „Wyczyść filtry” | „Rodzaj celu”, „Stanowisko”, „Filtruj”, „Wyczyść filtry” | istniejące bez zmian; nowe: przycisk „Filtry”, zakres dat, kolejność |
| Stronicowanie | „Nowsze”, „Starsze”, „Strona 1 z 7” | te same | bez zmian |
| Panel posta | „Poprzedni post”, „Następny post”, „Zamknij post”, „Oryginał”, „Podświetl cytaty z oceny” | „Otwórz oryginał na … (otwiera się w nowej karcie)” | nazwa odnośnika do oryginału dla czytnika bez zmian; reszta nowa |
| Panel posta | „Surowe dane” | „Dane dla nerdów: surowy JSON źródła” | zmiana: nazwa mówi wprost, co się otworzy; test E2E zmienia etykietę w wycinku, który przebuduje ten widok |
| Logowanie | „E-mail”, „Hasło”, „Zaloguj się” | te same | bez zmian |
| Aktywacja | „Nowe hasło”, „Powtórz hasło”, „Ustaw hasło” | te same | bez zmian |
| Panel admina | „Przebiegi”, „Jakość ocen”, „Nieudane posty” | sekcje jednej strony | nowe nazwy części; sekcje „Nowy przebieg” i „Ostatnie przebiegi” zostają w części „Przebiegi” |
| Przebiegi | „Uruchom”, „Od”, „Do”, „Szczegóły przebiegu z …” | te same | bez zmian |
| Stany | „Wczytywanie…”, „Spróbuj ponownie”, „Brak powiadomień.” | te same | bez zmian |

Słowa wartości (stany przebiegu, stanowiska, rodzaje celu i twierdzenia, typy posta per źródło) pochodzą z `src/labels.ts` i `src/runs.ts` i nie zmieniają się.

### Daty, godziny i liczby

| Co | Format | Przykład |
| --- | --- | --- |
| Dzień | `dd.mm.rrrr` | 20.10.2026 |
| Dzień z godziną | `dd.mm.rrrr, gg:mm` | 20.10.2026, 08:12 |
| Godzina posta w liście dnia | `gg:mm` | 09:41 |
| Nagłówek dnia w liście | słownie, z „Dzisiaj” i „Wczoraj” | Dzisiaj, 20 października |
| Podtytuł przeglądu dnia | dzień tygodnia i pełna data | wtorek, 20 października 2026 |
| Zakres dni | półpauza bez spacji | 18.10–19.10.2026 |
| Czas w powiadomieniach | względny do doby („przed chwilą”, „N min temu”, „N h temu”), potem dzień z godziną | 2 h temu; 19.10.2026, 02:41 |
| Udział | procent bez miejsc po przecinku | 45% |
| Liczba z rzeczownikiem | odmiana przez `plural` | 1 post, 23 posty, 128 postów |
| Duża liczba | odstęp co trzy cyfry od pięciu cyfr, jak `Intl.NumberFormat('pl-PL')` | 3134; 12 345 |
| Brak wartości | półpauza i słowa obok | – brak stanowisk |

Wszystkie daty i godziny są w czasie warszawskim (`Europe/Warsaw`), jak dziś w `src/labels.ts`. Element `time` ma atrybut `datetime` z pełną datą.

## Kontrakt dla testów E2E

> Do uzupełnienia w fazie 4.

## Wdrożenie

> Do uzupełnienia w fazie 4.

## Różnice wobec roadmapy

> Do uzupełnienia w fazie 4.

## Propozycje poza PRD

> Do uzupełnienia w fazie 4.

## Decyzja właściciela

> Do uzupełnienia w fazie 3.
