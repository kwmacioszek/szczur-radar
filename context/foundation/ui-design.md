# Projekt UI/UX aplikacji Szczur Radar

> Dokument żywy (F-07, `ui-design-system`). Źródło wyglądu i układu aplikacji webowej: wycinki UI wdrażają swoje ekrany według niego. Zmiana projektu to edycja tego dokumentu z akceptacją właściciela i zielonymi kontrolami [`tools/design-check`](../../tools/design-check/README.md).

## Cel i zakres

Aplikacja ma wyglądać i działać jak dopracowany produkt, a nie surowy panel. Ten dokument opisuje projekt całej aplikacji webowej: zasady i standardy, zadania użytkownika, układ ekranów i nawigację, język wizualny, komponenty, treść etykiet oraz to, czego potrzebują wdrożenie i testy.

Zakres to dokładnie wymagania [PRD v4](prd.md) dla aplikacji webowej (FR-006–FR-040) i ekrany kamienia milowego M-5 z [roadmapy](roadmap.md). Projekt nie dodaje wymagań. Pomysły wynikające ze standardów, których PRD nie obejmuje, są w sekcji „Propozycje poza PRD” i nie mają prototypów. Jedno doprecyzowanie na prośbę właściciela (2026-11-20): przegląd dnia pokazuje udział stanowisk wzrostowych, spadkowych i pozostałych w całym dniu. To suma danych, które FR-039 i tak każe policzyć (proporcje per cel), a nie zmiana względem poprzedniego dnia; czeka na zatwierdzenie w PRD (sekcja „Różnice wobec roadmapy”).

Makiety z [`ui-ux-proposals.md`](ui-ux-proposals.md) były listą funkcji, a nie wzorem wyglądu. Projekt nie zawiera ich elementów bez pokrycia w wymaganiach: tickerów i nazw kanonicznych spółek, scalania odmian celu, trendu, jednej etykiety stanowiska na cały post, „Zapisanych”, „Analiz”, „Wątków”, „Ostatnich obserwacji”, statusu pipeline'u dla zwykłego użytkownika i wyboru postów „wartych przeczytania”.

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
| Surowy wygląd | Jeden język wizualny: krój pisma w plikach aplikacji, skala typografii i odstępów, role kolorów, komponenty ze wszystkimi stanami. Wygląd wybrał właściciel na obrazkach, po dwóch rundach wariantów (sekcja „Decyzja właściciela”). |
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
| Pola logowania i hasła mają `autocomplete` (`username`, `current-password`, `new-password`), więc działa menedżer haseł i wklejanie | 1.3.5, 3.3.8 | przegląd; testy widoków logowania i aktywacji |
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

Posty nieinwestycyjne i posty bez treści są domyślnie ukryte (FR-007). Zamiast zdania o ukrytych postach każdy ekran z listą albo liczbami ma przełącznik dwóch stanów z liczbami: „Inwestycyjne 128 | Wszystkie 165”. Na przeglądzie dnia stoi obok wyboru dnia, na ekranie postów przy nagłówku, w katalogu celów obok okresu, a na karcie celu pod paskiem proporcji. Przełącza cały widok: listę, liczniki i proporcje. Liczby w przełączniku to liczba postów w bieżącym wyniku (po filtrach, dla wybranego dnia albo okresu) w każdym z dwóch zakresów.

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
- **Nieudane posty**: lista ze stanem posta (martwy, wstrzymany, do ponowienia), liczbą prób i ostatnim błędem; „Ponów zaznaczone” dla postów do ponowienia i „Przywróć martwe” dla martwych, każdy przycisk z liczbą zaznaczonych, których dotyczy.

Strona przebiegu zostaje osobnym ekranem (liczniki, wynik per źródło, kroki, błąd), z powrotem do części „Przebiegi”.

### Szeroki i wąski ekran

- **Od 900 px**: pasek nawigacji u góry i jedna kolumna treści. Lista jest zwarta (jeden post w jednej linii) i zajmuje całą kolumnę, do około 70 rem. Szczegóły otwierają się w panelu wysuwanym z prawej (około 44 rem): lista przesuwa się do lewej krawędzi, jest lekko przyciemniona i zostaje widoczna, a wybrany wiersz jest zaznaczony. Panel przewija się niezależnie od listy.
- **Poniżej 900 px**: jedna kolumna. Post na liście zajmuje kilka linii, a panel szczegółów otwiera się na cały ekran; zamyka go przycisk „Zamknij” albo „wstecz” przeglądarki. Nawigacja zwija się do wiersza pod nazwą aplikacji.
- **Od 320 px**: strona nie przewija się w poziomie.

## Ekrany

Ekrany M-5 z wymaganiami i źródłem danych. „Dziś” znaczy, że API już daje te dane; numer wycinka wskazuje, kto dostarcza brakujące API albo widok. Prototypy są w `ui-design/prototypes/screens/`, ich spis z wymaganiami i wycinkami w [`ui-design/screens.json`](ui-design/screens.json), a zrzuty każdego ekranu (jasny i ciemny, 1280 i 375 px) w `ui-design/screens/` pod nazwą `<ekran>-<motyw>-<szerokość>.png`. Zrzut obejmuje całą stronę; ekrany z otwartym panelem bocznym (`post`, `target`) mają zrzut kadru okna, bo panel przewija się niezależnie od strony.

| Ekran | Cel ekranu | Wymagania | Dane | Prototyp |
| --- | --- | --- | --- | --- |
| Przegląd dnia | Obraz wybranego dnia na pierwszy rzut oka: klocki z liczbą postów (ze źródłami), liczbą celów ze stanowiskiem i udziałem stanowisk wzrostowych, spadkowych i pozostałych; trzy najczęściej omawiane cele z proporcjami i najnowszymi postami; tabela wszystkich celów dnia | FR-039, FR-038, FR-035, FR-007 | S-59 (na S-55 i S-58) | [`overview`](ui-design/prototypes/screens/overview.html) |
| Posty: lista i ocena w panelu | Przejrzeć posty na liście zwartej i przeczytać ocenę wybranego w panelu z prawej: cele ze stanowiskiem, tezy z cytatem, link do oryginału, dane surowe | FR-007, FR-009, FR-010, FR-011a, FR-013, FR-015, FR-030, FR-040 | dziś (lista, filtr po celu i stanowisku, ocena, link, dane surowe); układ S-51 | [`posts`](ui-design/prototypes/screens/posts.html), [`post`](ui-design/prototypes/screens/post.html) |
| Posty: filtry i kolejność | Zawęzić listę: szukanie w treści na wierzchu; cel, stanowisko, dzień albo zakres dat i kolejność pod przyciskiem „Filtry”; przełącznik zakresu „Inwestycyjne / Wszystkie” | FR-036, FR-035, FR-008, FR-011a, FR-011b, FR-007 | S-56, S-55, S-54, S-52 | [`posts-filters`](ui-design/prototypes/screens/posts-filters.html) |
| Posty: zakres „Wszystkie” | Zobaczyć także posty ukryte domyślnie: nieinwestycyjne („nie inwestycyjny”) i odsiane przed modelem („za mało treści”, bez celów); liczby obu zakresów stoją w przełączniku | FR-007 | dziś (lista ocenionych postów z nieinwestycyjnymi i odsianymi); przełącznik S-52 | [`posts-all`](ui-design/prototypes/screens/posts-all.html) |
| Post z podświetlonymi cytatami | W panelu oceny: zobaczyć w treści fragmenty, na których oparto ocenę; wyłączyć podświetlenie; przejść do poprzedniego i następnego | FR-009, FR-010, FR-011a | dziś (cytaty są w ocenie); widok S-53 | [`post`](ui-design/prototypes/screens/post.html) |
| Cele: katalog i karta celu w panelu | Znaleźć cel po nazwie, zobaczyć liczbę postów i proporcje stanowisk (wzrostowe, spadkowe, pozostałe), przejść do wypowiedzi o celu | FR-037, FR-038, FR-011a | S-57, S-58 | [`targets`](ui-design/prototypes/screens/targets.html), [`target`](ui-design/prototypes/screens/target.html) |
| Logowanie | Zalogować się e-mailem i hasłem; komunikaty błędnych danych i limitu prób | FR-012, FR-040 | dziś; widok S-49 | [`login`](ui-design/prototypes/screens/login.html) |
| Aktywacja konta | Ustawić hasło z jednorazowego linku; link nieważny, reguły hasła | FR-018 | dziś; widok S-49 | [`activate`](ui-design/prototypes/screens/activate.html) |
| Powłoka aplikacji | Nawigacja u góry według roli, data aktualizacji danych, przełącznik motywu, wylogowanie | FR-015, FR-013, FR-030, FR-040 | dziś; widok S-47 | na każdym ekranie zalogowanym |
| Panel admina: Przebiegi | Uruchomić przebieg, uzupełnić dni bez danych, zobaczyć ostatnie przebiegi i ich stan | FR-021, FR-022, FR-015 | dziś; widok S-50 | [`admin-runs`](ui-design/prototypes/screens/admin-runs.html) |
| Panel admina: Jakość ocen | Sprawdzić miary jakości per źródło wobec progów | FR-033 | dziś; „kanał jako cel” S-60 | [`admin-quality`](ui-design/prototypes/screens/admin-quality.html) |
| Panel admina: Nieudane posty | Zobaczyć nieudane i martwe posty, ponowić zaznaczone i przywrócić martwe | FR-034 | dziś; widok S-50 | [`admin-posts`](ui-design/prototypes/screens/admin-posts.html) |
| Przebieg | Liczniki, wynik per źródło, kroki, błąd i powody „wymaga uwagi” jednego przebiegu | FR-021 | dziś; widok S-50 | [`run`](ui-design/prototypes/screens/run.html) |
| Powiadomienia admina | Lista przebiegów wymagających uwagi z przejściem do przebiegu | FR-021, FR-015 | dziś | [`notifications`](ui-design/prototypes/screens/notifications.html) |
| Wspólne stany | Ładowanie, pusta lista i błąd z ponowieniem, takie same w każdym widoku | FR-007, FR-009, FR-021, FR-033, FR-034, FR-036 | dziś; komponenty S-48 | [`state-loading`](ui-design/prototypes/screens/state-loading.html), [`state-empty`](ui-design/prototypes/screens/state-empty.html), [`state-error`](ui-design/prototypes/screens/state-error.html) na liście postów; [`admin-loading`](ui-design/prototypes/screens/admin-loading.html), [`admin-empty`](ui-design/prototypes/screens/admin-empty.html), [`admin-error`](ui-design/prototypes/screens/admin-error.html) w panelu admina |

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
| `--bg-glow` | `#dee8f5` | `#14263b` | najciemniejszy punkt poświaty w prawym górnym rogu tła strony | Tylko jako początek gradientu tła; każda rola tekstu i krawędzi leżąca na `--bg` ma parę także na `--bg-glow`. |
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

Kontrola `tools/design-check` sprawdza 73 pary z `pairs.json` w obu motywach: tekst co najmniej 4,5:1, elementy interfejsu i fokus co najmniej 3:1 (WCAG 1.4.3 i 1.4.11). Wszystkie przechodzą. Poświata tła ma własną rolę `--bg-glow`, bo axe nie ocenia kontrastu tekstu na gradiencie (takie węzły trafiają do `incomplete`, nie do naruszeń); dlatego jej najciemniejszy punkt jest parą w `pairs.json`. Osiem par najbliżej progu:

| Para | Rodzaj | Próg | Jasny | Ciemny |
| --- | --- | --- | --- | --- |
| `--destructive` na `--bg-glow` | tekst | 4,5:1 | 4,55:1 | 6,08:1 |
| `--destructive` na `--destructive-soft` | tekst | 4,5:1 | 4,62:1 | 6,07:1 |
| `--accent` na `--bg-glow` | tekst | 4,5:1 | 4,66:1 | 8,15:1 |
| `--success` na `--bg-glow` | tekst | 4,5:1 | 4,67:1 | 8,80:1 |
| `--text-muted` na `--bg-glow` | tekst | 4,5:1 | 4,71:1 | 5,45:1 |
| `--destructive` na `--selected` | tekst | 4,5:1 | 4,72:1 | 5,84:1 |
| `--border` na `--bg-glow` | element interfejsu | 3:1 | 3,28:1 | 3,20:1 |
| `--accent` na `--selected` | tekst | 4,5:1 | 4,84:1 | 7,82:1 |

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

Karty i listy leżą płasko: od tła odróżnia je kolor `--surface` i linia `--line`, bez cienia. Cień `--shadow-overlay` (`0 1.5rem 4rem -1rem color-mix(in srgb, var(--scrim) 40%, transparent)`) mają tylko warstwy nad stroną: panel boczny, lista powiadomień i panel filtrów. Kolejność warstw: `--z-bar` (5) dla paska aplikacji z jego listą powiadomień, `--z-scrim` (10) dla przyciemnienia, `--z-overlay` (20) dla tego, co nad nim.

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

Ikony pochodzą z jednego zestawu, [Lucide](https://lucide.dev/) 1.77.0 (licencja ISC, 19 ikon), plus sześć własnych na tej samej licencji ISC: znak marki i pięć znaków stanowiska. Pliki i licencja są w [`ui-design/icons/`](ui-design/icons/README.md). Każda ikona rysuje się kolorem tekstu (`currentColor`), kreską `--icon-stroke`. Ikona nigdy nie stoi sama bez nazwy: ma tekst obok albo nazwę dla czytnika ekranu na przycisku, a sama jest ukryta przed czytnikiem.

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
| `triangle-alert` | komunikat uwagi, znacznik „wymaga uwagi” i stan źródła bez postów |
| `info` | informacja bez oceny: post czeka na ocenę |
| `refresh-cw` | „Spróbuj ponownie” |
| `play` | „Uruchom” przebieg |
| `rotate-ccw` | „Przywróć martwe” w nieudanych postach |
| `inbox` | pusta lista: dzień bez danych |
| `arrow-down-up` | kolejność listy |
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
| Filtry | tak | E | E | E | D | tak (bez filtrów: bez wiersza włączonych filtrów) | B |
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

**[Pola](ui-design/prototypes/components.html#c-pola)**. Tekst, hasło, wyszukiwanie, data, lista wyboru, pole wyboru. Budowa: etykieta nad polem, pole, pod polem podpowiedź albo błąd. Wysokość jak przycisk, więc wiersz pól i przycisków się wyrównuje. Błąd: czerwona krawędź, ikona i zdanie pod polem. Dostępność: `label` powiązany z polem; błąd i podpowiedź przez `aria-describedby`, błędne pole ma `aria-invalid="true"`; tekst zastępczy nie zastępuje etykiety (duże wyszukiwanie nad listą ma nazwę „Szukaj w postach” w `aria-label`). Pole daty jest natywne: format i kalendarz daje przeglądarka.

**[Przełącznik zakresu](ui-design/prototypes/components.html#c-przelacznik)**. Dwie opcje z liczbą postów: „Inwestycyjne” (domyślna) i „Wszystkie”. Przełącza cały widok. Wybrana opcja ma tło `--selected`, obwódkę akcentu i grubszy tekst. Dostępność: `role="group"` z nazwą „Zakres postów”, opcje to przyciski z `aria-pressed`. Podczas ładowania opcje są wyłączone i bez liczb.

**[Filtry](ui-design/prototypes/components.html#c-filtry)**. Nad listą: duże pole „Szukaj w postach”, które szuka słów w tytule i treści (FR-036), i przycisk „Filtry”. Pod przyciskiem otwiera się panel z polami: „Spółka lub temat” (filtr po celu, FR-011a), rodzaj celu, stanowisko, zakres dat i kolejność, oraz przyciskami „Filtruj” i „Wyczyść filtry”. Szukanie w treści i filtr po celu to dwa osobne pola, bo znaczą co innego: „Orlen” w treści znajdzie też posty, w których Orlen nie jest celem. Włączone filtry stoją nad listą jako wiersz znaczników („Cel: Orlen”, „14.10–20.10.2026”); każdy znacznik jest przyciskiem, który zdejmuje swój filtr, a na końcu wiersza jest „Wyczyść filtry”. Przycisk pokazuje ich liczbę („Filtry: 2”). Lista nie ma ukrytego domyślnego okresu: bez filtra dat pokazuje wszystkie posty od najnowszych. Dostępność: formularz `role="search"` z nazwą „Filtr postów”; przycisk ma `aria-expanded`, a panel stoi w dokumencie zaraz po nim; Esc zamyka panel i oddaje fokus przyciskowi; znacznik filtra ma nazwę „Usuń filtr: …”. Poniżej 900 px pole szukania zajmuje cały wiersz, a przycisk „Filtry” stoi pod nim. Filtr dalej żyje w adresie strony.

**[Stronicowanie](ui-design/prototypes/components.html#c-stronicowanie)**. „Nowsze”, numer strony, „Starsze” dla list ułożonych w czasie (posty). Lista ułożona inaczej (katalog celów według liczby postów) ma „Poprzednie” i „Następne”. Na pierwszej stronie pierwszy przycisk jest wyłączony, na ostatniej drugi. Dostępność: `nav` z nazwą „Strony”. Prototypy pokazują skróconą listę; strona ma do 50 pozycji.

**[Motyw](ui-design/prototypes/components.html#c-motyw)**. Przycisk z ikoną księżyca i nazwą „Ciemny motyw”; `aria-pressed` mówi, czy ciemny motyw jest włączony. Zachowanie z S-46 bez zmian.

### Znaczenie

**[Znacznik stanu](ui-design/prototypes/components.html#c-znacznik-stanu)**. Stan przebiegu, kroku albo źródła. Tekst mówi stan, ton go wzmacnia: neutralny, informacja, powodzenie, uwaga, błąd. Stan wymagający uwagi może mieć ikonę. Słowa stanów i ich tony bez zmian (`src/runs.ts`).

**[Stanowisko](ui-design/prototypes/components.html#c-stanowisko)**. Stanowisko autora wobec celu: znak, słowo i kolor. Sześć kierunków: wzrostowo, spadkowo, neutralnie, mieszanie, bez stanowiska, niejasno; każde może mieć dopisek „(ostrożnie)”. Trzy sygnały naraz, więc stanowisko czyta też osoba, która nie rozróżnia zieleni i czerwieni (WCAG 1.4.1).

**[Cel ze stanowiskiem](ui-design/prototypes/components.html#c-cel)**. Nazwa celu i stanowisko w jednym znaczniku; tam, gdzie jest miejsce, także rodzaj celu. Znacznik zostaje w jednej linii; na bardzo wąskim ekranie skraca się nazwa celu, nie stanowisko.

**[Pasek proporcji](ui-design/prototypes/components.html#c-pasek-proporcji)**. Udział stanowisk wzrostowych, spadkowych i pozostałych wobec celu. „Pozostałe” to wszystko poza wzrostowym i spadkowym: neutralne, mieszane, niejasne i brak stanowiska; ekran z proporcjami mówi to pod tabelą albo nagłówkiem. Dostępność: pasek to `role="img"` z opisem liczbami w tej samej odmianie co tekst obok („33 wzrostowe, 17 spadkowych, 11 pozostałych”); te same liczby stoją obok jako tekst. Cel bez stanowisk ma pusty pasek i napis „Brak stanowisk”.

**[Cytat w treści](ui-design/prototypes/components.html#c-cytat)**. Fragment treści posta, na którym oparto ocenę: element `mark` z tłem `--highlight` i kreską `--warning`. Podświetlony jest każdy fragment, na który wskazuje ocena: cytat twierdzenia i miejsce, w którym cel pada w treści, także gdy cel nie ma twierdzeń. Gdy cytat pochodzi z tytułu, podświetlony jest tytuł w panelu. Pole wyboru „Podświetl fragmenty, na których oparto ocenę” wyłącza podświetlenie; tekst się wtedy nie przesuwa.

**[Komunikat](ui-design/prototypes/components.html#c-komunikat)**. Wynik działania albo stan, który trzeba znać: powodzenie (`role="status"`), błąd (`role="alert"`), uwaga (`role="status"`). Budowa: ikona tonu i zdanie. Stoi przy tym, czego dotyczy, nie na górze strony.

### Treść

**[Wiersz posta](ui-design/prototypes/components.html#c-wiersz-posta)**. Jeden post w jednej linii: godzina, źródło i kanał; tytuł szeryfem; z prawej cele ze stanowiskami. Wiersze jednego dnia stoją w jednej ramce pod nagłówkiem dnia. Post bez tytułu (wpis i komentarz) pokazuje w miejscu tytułu początek treści, zwykłą grubością i kolorem `--text`, żeby nie udawał tytułu. Cały wiersz jest odnośnikiem do posta i otwiera panel boczny. Wybrany wiersz ma tło `--selected`, pasek akcentu z lewej i `aria-current="true"`. Gdy celów jest więcej niż się mieści, wiersz pokazuje dwa i napis „i 3 inne”. Flaga niepewności oceny („możliwy sarkazm”, „brak kontekstu”) stoi za celami kolorem `--warning`. Post bez oceny ma „czeka na ocenę”, nieinwestycyjny „nie inwestycyjny”, z nieudaną oceną „ocena nieudana”. Poniżej 900 px wiersz ma kilka linii: metadane, tytuł, cele.

**[Panel boczny](ui-design/prototypes/components.html#c-panel)**. Ocena wybranego posta albo karta wybranego celu. Wysuwa się z prawej (szerokość `--width-panel`), lista przesuwa się do lewej krawędzi i zostaje widoczna pod lekkim przyciemnieniem. Budowa: pasek (źródło, kanał, typ i data posta; „4 z 128”; poprzedni, następny, zamknij) i przewijana treść. Dostępność: `role="dialog"` z nazwą z tytułu posta i `aria-modal="true"`; po otwarciu fokus przechodzi do panelu, Tab krąży w panelu, Esc zamyka, a fokus wraca na wybrany wiersz (wzorzec dialogu z WAI-ARIA Authoring Practices). Poniżej 900 px panel zajmuje cały ekran. Wybrany post ma własny adres.

**[Cel w ocenie](ui-design/prototypes/components.html#c-karta-celu)**. Jeden cel oceny: nazwa, rodzaj, stanowisko; pod spodem twierdzenia, każde z rodzajem („informacja”, „opinia”, „teza inwestycyjna”, „pytanie”, „działanie”) i cytatem z treści. Cel bez twierdzeń ma napis „Wspomniany, bez twierdzeń.”. Pod oceną: data oceny i przycisk „Surowe dane”, który rozwija dane źródła krojem `--font-mono`.

**[Klocek liczby](ui-design/prototypes/components.html#c-klocek-liczby)**. Jedna liczba dnia: podpis, liczba szeryfem, drobne wyjaśnienie. Przegląd dnia ma pięć: Posty, Cele ze stanowiskiem, Wzrostowe, Spadkowe, Pozostałe. Udziały mają znak i kolor stanowiska. Dostępność: lista opisowa (`dl`), więc czytnik czyta podpis razem z liczbą. Dzień bez stanowisk pokazuje kreskę i „brak stanowisk”.

**[Karta celu dnia](ui-design/prototypes/components.html#c-karta-tematu)**. Najczęściej omawiany cel: nazwa (odnośnik do celu), liczba postów, pasek proporcji z liczbami, najnowsze posty ze stanowiskiem. Przegląd dnia ma trzy karty obok siebie, poniżej 900 px jedną pod drugą.

**[Tabela](ui-design/prototypes/components.html#c-tabela)**. Dane w wierszach i kolumnach: cele dnia, przebiegi, nieudane posty. Liczby są wyrównane do prawej i mają cyfry tabelaryczne; zero jest wyszarzone. Pierwsza komórka wiersza jest jego nagłówkiem (`th scope="row"`) i odnośnikiem dalej. Wiersz zaznacza pole wyboru z nazwą („Zaznacz post: …”). Na wąskim ekranie tabela przewija się w poziomie wewnątrz własnej ramki.

### Powłoka i nawigacja

**[Nawigacja](ui-design/prototypes/components.html#c-nawigacja)**. Pasek u góry każdej strony po zalogowaniu: znak i nazwa aplikacji, pozycje (Przegląd, Posty, Cele, dla admina Panel admina), data danych per źródło, dzwonek (admin), motyw, e-mail, „Wyloguj”. Bieżąca pozycja ma `aria-current="page"`. Dostępność: jeden `banner` na stronie, `nav` z nazwą „Główna”. Data danych ma dla każdego źródła dzień i godzinę („Dane: Wykop 20.10.2026, 10:15” i pod spodem „Reddit 20.10.2026, 10:15”, jedno źródło w jednej linii), bo ostatnia udana aktualizacja mogła być wczoraj. Poniżej 900 px pozycje schodzą do drugiego wiersza, data danych do trzeciego (zostaje widoczna, FR-030), a e-mail znika z paska.

**[Nagłówek strony](ui-design/prototypes/components.html#c-naglowek-strony)**. Tytuł strony (`h1`, jeden na stronie) i opcjonalny podtytuł; z prawej tylko to, co zmienia cały widok: przełącznik zakresu i wybór dnia (poprzedni, pole daty, następny). Po zmianie widoku fokus przechodzi na tytuł.

**[Części panelu admina](ui-design/prototypes/components.html#c-podnawigacja)**. Pasek pod nagłówkiem „Panel admina”: Przebiegi, Jakość ocen, Nieudane posty. Każda część ma własny adres, więc to odnośniki w `nav` z nazwą „Panel admina”, a bieżąca ma `aria-current="page"`. Część może mieć licznik.

**[Powiadomienia](ui-design/prototypes/components.html#c-powiadomienia)**. Dzwonek z licznikiem nieprzeczytanych i lista pod nim. Powiadomienie: znacznik stanu przebiegu, zdanie, rodzaj przebiegu i czas; nieprzeczytane ma znacznik „nowe”, pasek akcentu i grubszy tekst. Dostępność: przycisk z nazwą „Powiadomienia, 2 nieprzeczytane” i `aria-expanded`; lista to `region` z nazwą „Powiadomienia” i stoi w dokumencie zaraz po dzwonku, więc Tab z dzwonka wchodzi do listy; Esc zamyka. Poniżej 900 px lista otwiera się w pasku i przesuwa treść w dół. Widzi je tylko administrator.

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
| Okres w katalogu celów | „Dziś”, „7 dni”, „30 dni” | brak | nowy przełącznik (S-57; domyślny okres ustali plan wycinka) |
| Wyszukiwanie w treści | „Szukaj w postach”; tekst zastępczy „Szukaj w tytule i treści” | brak | nowe pole (FR-036, S-56) |
| Filtry | „Filtry”, „Spółka lub temat”, „Rodzaj celu”, „Stanowisko”, „Od”, „Do”, „Kolejność”, „Filtruj”, „Wyczyść filtry”; znacznik włączonego filtra „Usuń filtr: …” | „Spółka lub temat”, „Rodzaj celu”, „Stanowisko”, „Filtruj”, „Wyczyść filtry” | istniejące bez zmian („Spółka lub temat” zostaje nazwą filtra po celu); nowe: przycisk „Filtry”, zakres dat, kolejność, znaczniki włączonych filtrów |
| Stronicowanie | „Nowsze”, „Starsze”, „Strona 1 z 7”; w katalogu celów „Poprzednie”, „Następne” | te same | istniejące bez zmian; para dla katalogu celów nowa |
| Panel posta | „Poprzedni post”, „Następny post”, „Zamknij post”, „Otwórz oryginał”, „Podświetl fragmenty, na których oparto ocenę” | „Otwórz oryginał na … (otwiera się w nowej karcie)” | nazwa odnośnika do oryginału dla czytnika bez zmian; reszta nowa |
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
| Nagłówek dnia w liście | słownie, z „Dziś” i „Wczoraj” | Dziś, 20 października |
| Podtytuł przeglądu dnia | dzień tygodnia i pełna data | wtorek, 20 października 2026 |
| Zakres dni | półpauza bez spacji | 18.10–19.10.2026 |
| Czas w powiadomieniach | względny do doby („przed chwilą”, „N min temu”, „N h temu”), potem dzień z godziną | 2 h temu; 19.10.2026, 02:39 |
| Data danych w pasku aplikacji | źródło oraz dzień z godziną ostatniej udanej aktualizacji, jedno źródło w jednej linii | Wykop 20.10.2026, 10:15 |
| Udział stanowisk | procent bez miejsc po przecinku | 45% |
| Miara jakości ocen | procent z jednym miejscem po przecinku, bo progi to pojedyncze procenty | 6,1% |
| Liczba z rzeczownikiem | odmiana przez `plural` | 1 post, 23 posty, 128 postów |
| Duża liczba | odstęp co trzy cyfry od pięciu cyfr, jak `Intl.NumberFormat('pl-PL')` | 3134; 12 345 |
| Brak wartości | półpauza i słowa obok | – brak stanowisk |

Wszystkie daty i godziny są w czasie warszawskim (`Europe/Warsaw`), jak dziś w `src/labels.ts`. Element `time` ma atrybut `datetime` z pełną datą.

## Kontrakt dla testów E2E

Każdy wycinek S-47–S-59 kończy się testem E2E swoich przepływów (NFR `prd.md:328`, zasady w `REPOSITORY_GUIDELINES.md`: lokatory po roli, etykiecie i tekście w obrębie `banner`, `main` albo nazwanego regionu; bez selektorów CSS). Ta sekcja mówi, po czym test znajduje elementy i co ma przeklikać. Nazwy są takie jak w prototypach; zmiana nazwy w kodzie to zmiana tego dokumentu.

### Powłoka (każdy ekran po zalogowaniu)

| Element | Rola i nazwa |
| --- | --- |
| Pasek aplikacji | `banner` (jeden na stronie) |
| Nawigacja | `navigation` „Główna” z odnośnikami „Przegląd”, „Posty”, „Cele” i, dla admina, „Panel admina”; bieżący ma `aria-current="page"` |
| Data danych | tekst „Dane:” i po jednym wierszu na źródło, np. „Wykop 20.10.2026, 10:15” (element `time`) |
| Powiadomienia (admin) | przycisk „Powiadomienia, N nieprzeczytanych” z `aria-expanded` |
| Motyw | przycisk „Ciemny motyw” z `aria-pressed` |
| Sesja | tekst z e-mailem (od 900 px) i przycisk „Wyloguj” |
| Treść | `main` (jeden na stronie) z jednym nagłówkiem poziomu 1 |

Przepływy powłoki: klik pozycji nawigacji otwiera jej ekran i przenosi `aria-current`; zwykły użytkownik nie ma odnośnika „Panel admina” ani dzwonka; „Wyloguj” kończy sesję i prowadzi do logowania; „Ciemny motyw” zmienia `data-theme` i przetrwa przeładowanie. Tryby atrapy: `AUTH`, `FRESHNESS`, `NOTIFICATIONS`.

### Ekrany

| Ekran | Role i nazwy | Przepływy: krok i oczekiwany wynik | Tryby atrapy |
| --- | --- | --- | --- |
| `overview` | nagłówek 1 „Przegląd dnia”; grupa „Zakres postów” z przyciskami „Inwestycyjne” i „Wszystkie” (`aria-pressed`); przyciski „Poprzedni dzień”, „Następny dzień”, pole „Dzień”; terminy „Posty”, „Cele ze stanowiskiem”, „Wzrostowe”, „Spadkowe”, „Pozostałe”; regiony „Najczęściej omawiane” (nagłówki 3 z nazwą celu) i „Wszystkie cele dnia” (tabela) | zmiana dnia: liczby i cele są z wybranego dnia, a dzień jest w adresie; „Wszystkie”: liczby rosną o posty nieinwestycyjne; klik nazwy celu: otwiera kartę celu; klik posta na karcie celu dnia: otwiera post w panelu | nowy tryb dla przeglądu (dane, ładowanie, błąd, dzień bez danych), `FRESHNESS` |
| `posts` | nagłówek 1 „Posty”; grupa „Zakres postów”; formularz `search` „Filtr postów” z polem „Szukaj w postach” i przyciskiem „Filtry” (`aria-expanded`); regiony dni z nagłówkiem 2 („Dziś, 20 października”); wiersz posta to odnośnik w elemencie listy; `navigation` „Strony” z „Nowsze”, „Starsze” i tekstem „Strona 1 z 3” | klik wiersza: otwiera post w panelu; „Starsze”: druga strona i numer strony w adresie; „Wszystkie”: lista i liczby obejmują posty nieinwestycyjne, a stan jest w adresie | `MODE` (`data`, `loading-next`, `error-next`) |
| `post` | `dialog` o nazwie równej tytułowi posta (post bez tytułu: „Wpis użytkownika …”, jak dziś); przyciski „Poprzedni post”, „Następny post”, „Zamknij post”; odnośnik „Otwórz oryginał na … (otwiera się w nowej karcie)”; pole wyboru „Podświetl fragmenty, na których oparto ocenę”; nagłówek „Ocena”; nagłówki z nazwami celów; przycisk „Surowe dane” (`aria-expanded`) i region „Surowe dane posta” | klik wiersza: dialog jest otwarty, adres to adres posta, fokus jest w dialogu, wiersz ma `aria-current`; „Zamknij post” albo Esc: dialog znika, fokus wraca na wiersz, adres wraca do listy z tym samym filtrem i stroną; „Następny post”: treść i adres następnego posta listy; odznaczenie pola: w treści nie ma elementów `mark`; „Surowe dane”: widać JSON źródła; wejście na adres posta bez sesji: logowanie, a po nim ten post; poniżej 900 px „wstecz” przeglądarki zamyka panel | `MODE` (`raw-loading`, `raw-error`), `AUTH` |
| `posts-filters` | przycisk „Filtry: N”; w panelu pola „Spółka lub temat”, „Rodzaj celu”, „Stanowisko”, „Od”, „Do”, „Kolejność” oraz przyciski „Filtruj”, „Wyczyść filtry”, „Zamknij filtry”; lista „Włączone filtry” z przyciskami „Usuń filtr: …” | wpisanie celu i wybór stanowiska, „Filtruj”: parametry są w adresie, nad listą stoją znaczniki, lista i liczby w przełączniku są zawężone; „Usuń filtr: …”: znika jeden filtr z adresu i z listy znaczników; wpisanie słów w „Szukaj w postach”: lista zawiera tylko posty z tymi słowami (S-56); „Od” i „Do”: lista z zakresu dni (S-55); „Kolejność”: porządek listy (S-54); Esc: panel zamknięty, fokus na przycisku „Filtry” | `MODE` (`data`, `empty`) |
| `posts-all` | jak `posts`; w grupie „Zakres postów” przycisk „Wszystkie” ma `aria-pressed="true"`; wiersze z tekstem „nie inwestycyjny” i „za mało treści”; tekst „Strona 1 z 4” | wejście z zakresem „Wszystkie” w adresie: lista obejmuje posty nieinwestycyjne i odsiane, a przełącznik pokazuje liczby obu zakresów; „Inwestycyjne”: wiersze „nie inwestycyjny” i „za mało treści” znikają, zakres w adresie się zmienia, a liczby w przełączniku zostają; przeładowanie: zakres zostaje | nowy tryb albo parametr atrapy dla zakresu postów (S-52) |
| `targets` | nagłówek 1 „Cele”; grupy „Zakres postów” i „Okres” (przyciski „Dziś”, „7 dni”, „30 dni”); pole „Szukaj celu”; przycisk „Kolejność: …”; tabela z nagłówkami kolumn „Cel”, „Posty”, „Wzrostowe”, „Spadkowe”, „Pozostałe”, „Udział”; nagłówek wiersza to odnośnik z nazwą celu; `navigation` „Strony” z „Poprzednie”, „Następne” | wpisanie nazwy: w tabeli zostają pasujące cele; zmiana okresu: liczby z nowego okresu i okres w adresie; klik nazwy celu: karta celu w panelu | nowy tryb dla katalogu (dane, ładowanie, błąd, brak celów) |
| `target` | `dialog` o nazwie celu; przyciski „Poprzedni cel”, „Następny cel”, „Zamknij cel”; obraz „Stanowiska wobec …” z liczbami; grupa „Zakres postów o celu”; nagłówek „Wypowiedzi”; odnośniki z tytułami postów; odnośnik „Wszystkie posty o …” | klik celu: dialog otwarty, adres celu, fokus w dialogu; klik tytułu wypowiedzi: post w panelu na ekranie postów; „Wszystkie posty o …”: ekran postów ze znacznikiem „Cel: …”; „Zamknij cel”: fokus wraca na wiersz celu | jak `targets` |
| `login` | nagłówek 1 „Logowanie”; pola „E-mail” i „Hasło”; przycisk „Zaloguj się”; komunikat `alert`; przycisk „Ciemny motyw” | poprawne dane: ekran startowy; złe hasło: zostaje na logowaniu z komunikatem „Nieprawidłowy e-mail lub hasło.”; limit prób: komunikat „Zbyt wiele prób. …”; zapamiętany motyw jest zastosowany | `AUTH: anonymous`, `LOGIN` |
| `activate` | nagłówek 1 „Ustawienie hasła”; pola „Nowe hasło” i „Powtórz hasło”; przycisk „Ustaw hasło”; błąd przy polu | poprawne hasło: logowanie z komunikatem „Hasło ustawione. Zaloguj się.”; za krótkie hasło: błąd przy polu, fokus na polu; nieważny link: komunikat i brak formularza | `AUTH: anonymous`, `ACTIVATION` |
| `admin-runs` | nagłówek 1 „Panel admina”; `navigation` „Panel admina” z odnośnikami „Przebiegi”, „Jakość ocen”, „Nieudane posty”; regiony „Nowy przebieg” (pola „Od”, „Do”, grupa „Źródła”, przycisk „Uruchom”), „Dni bez danych” (przyciski „Wpisz do formularza: …”), „Ostatnie przebiegi” (tabela, odnośniki „Szczegóły przebiegu z …”, przyciski „Odśwież”, „Pokaż starsze”) | „Uruchom”: nowy przebieg na górze tabeli; zajęty pipeline: komunikat i brak nowego wiersza; „Wpisz do formularza: …”: pola „Od” i „Do” mają ten zakres, fokus na „Od”; „Pokaż starsze”: tabela się wydłuża; klik części: jej adres i `aria-current` | `START`, `MISSING` |
| `admin-quality` | region „Jakość ocen (7 dni)”; nagłówki 3 z nazwą źródła; nazwa miary, wartość, próg i stan słowami („w normie”, „ponad progiem”, „za mało danych”); obraz paska z tym samym opisem | wejście z paska części: adres części; każda miara ma wartość, próg i stan słowami | `QUALITY` |
| `admin-posts` | region „Nieudane posty”; tabela; pola wyboru „Zaznacz post: …”; przyciski „Ponów zaznaczone (N)” i „Przywróć martwe (N)” | zaznaczenie postów: liczby w przyciskach rosną osobno dla postów do ponowienia i martwych; „Ponów zaznaczone”: komunikat powodzenia; „Przywróć martwe”: posty znikają z listy; klik tytułu: post w panelu | `MODE` (`data`; `empty` to brak przebiegów i nieudanych postów) |
| `run` | odnośnik „Panel admina” (powrót); nagłówek 1 „Przebieg z …” i znacznik stanu; komunikat „Wymaga uwagi: …”; nagłówki 2 „Liczniki”, „Wynik per źródło”, „Kroki” | „Szczegóły przebiegu z …” z tabeli: strona przebiegu; „Panel admina”: powrót do części „Przebiegi”; trwający przebieg odświeża się sam | `MODE: data` (przebiegi atrapy z powodami „wymaga uwagi”) |
| `notifications` | przycisk „Powiadomienia, N nieprzeczytanych” (`aria-expanded="true"`); region „Powiadomienia”; przycisk „Oznacz wszystkie jako przeczytane”; odnośniki powiadomień ze znacznikiem „nowe” | klik dzwonka: lista otwarta, Tab wchodzi do listy; klik powiadomienia: strona przebiegu i licznik mniejszy o jeden; Esc: lista zamknięta, fokus na dzwonku | `NOTIFICATIONS` |
| `state-loading` | region listy z `aria-busy="true"` i tekstem „Wczytywanie…”; przełącznik zakresu wyłączony | lista w trakcie wczytywania pokazuje szkielet i tekst dla czytnika | `MODE: loading` |
| `state-empty` | nagłówek „Brak postów dla tych filtrów”; lista „Włączone filtry”; przycisk „Wyczyść filtry” | filtr bez wyników: pusty stan i znaczniki; „Wyczyść filtry”: pełna lista i adres bez filtrów. Pusta lista bez filtrów zostaje z dzisiejszym tekstem „Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.” | `MODE: empty` |
| `state-error` | komunikat `alert` z nagłówkiem „Nie udało się wczytać postów”; przycisk „Spróbuj ponownie” | błąd listy: komunikat; „Spróbuj ponownie”: lista się wczytuje, filtry zostają | `MODE: error` |
| `admin-loading` | regiony części z `aria-busy="true"` i tekstem „Wczytywanie…”; „Odśwież” wyłączone | każda sekcja pokazuje szkielet do czasu swoich danych | `MODE: loading` (wstrzymuje odpowiedzi panelu admina) |
| `admin-empty` | region „Nieudane posty” z tekstem „Brak nieudanych postów”; odnośnik „Pokaż przebiegi” | brak nieudanych postów: pusty stan bez tabeli i bez licznika w pasku części | `MODE: empty` |
| `admin-error` | komunikat `alert` „Nie udało się wczytać jakości ocen”; przycisk „Spróbuj ponownie” | błąd jednej części: komunikat w tej części, pozostałe części działają; „Spróbuj ponownie” wczytuje ją | `QUALITY: error` (jedna część); `MODE: error` (cały panel) |

Nowy tryb atrapy to wpis w `MODE_VALUES` atrapy i w `MOCK_MODE_VALUES`, przypadek w `src/contract/mockApi.test.ts` i wiersz w nagłówku atrapy.

### Istniejące testy, które zmiana łamie

| Plik i test | Co się zmienia | Wycinek |
| --- | --- | --- |
| `e2e/feed.spec.ts`: „filtr po celu i stanowisku trafia do adresu i zawęża listę” | pola „Spółka lub temat” i „Stanowisko” są w panelu pod przyciskiem „Filtry”, więc test najpierw go otwiera; teksty stanu „Ocenione posty: 120” i „Pasujące do filtra: N” zastępują liczby w przełączniku zakresu; karty (`article` z nagłówkiem 2) stają się wierszami listy | S-51, S-52 |
| `e2e/feed.spec.ts`: „filtr bez wyników pozwala wrócić do pełnej listy” | pusty stan ma nagłówek „Brak postów dla tych filtrów” i znaczniki włączonych filtrów; „Wyczyść filtry” zostaje | S-48, S-51 |
| `e2e/feed.spec.ts`: „przejście na starszą stronę zmienia adres i stronę”, „ładowanie listy jest widoczne”, „błąd listy pozwala ponowić wczytanie”, „pusta lista mówi, kiedy pojawią się posty” | etykiety zostają („Starsze”, „Strona 2 z 3”, „Wczytywanie…”, „Spróbuj ponownie”); zmienia się adres listy z `/` na `/posts`, gdy ekranem startowym zostanie przegląd | S-59 |
| `e2e/post.spec.ts`: „post otwarty z listy pokazuje ocenę i surowe dane, a powrót wraca do listy” | post jest dialogiem nad listą, a nie stroną z nagłówkiem 1 w `main`; „Dane dla nerdów: surowy JSON źródła” to przycisk „Surowe dane”; powrót to „Zamknij post”, nie odnośnik „Posty” w `main`; poziomy nagłówków oceny są inne | S-51, S-53 |
| `e2e/session-guard.spec.ts`: „anonimowy link do posta prowadzi przez logowanie z powrotem do tego posta” | po logowaniu widać listę z otwartym dialogiem posta; asercja na nagłówek posta przechodzi na nazwę dialogu | S-51 |
| `e2e/admin.spec.ts`: „panel admina wczytuje wszystkie sekcje” | pięć sekcji jednej strony to trzy części z własnymi adresami: „Nowy przebieg”, „Dni bez danych” i „Ostatnie przebiegi” w „Przebiegi”, „Jakość ocen (7 dni)” i „Nieudane posty” osobno; tekst miary jakości ma inny układ (nazwa miary, wartość, „N z M prób · próg P%”) | S-50 |
| `e2e/admin.spec.ts`: „uruchomienie przebiegu dodaje go na górę listy”, „zajęty pipeline odrzuca start z komunikatem”, „szczegóły przebiegu otwierają jego stronę”, „powiadomienie z dzwonka otwiera przebieg i zmniejsza licznik” | nazwy zostają („Uruchom”, „Szczegóły przebiegu z …”, „Powiadomienia, N nieprzeczytanych”); lista przebiegów jest tabelą, więc lokator wiersza się zmienia | S-50 |
| `e2e/auth.spec.ts`, `e2e/activation.spec.ts`, `e2e/theme.spec.ts` | nazwy pól i przycisków zostają; po S-59 ekranem po zalogowaniu jest przegląd dnia | S-49, S-59 |
| `visual/targets.ts`: `view-feed`, `view-post`, `view-admin`, `view-run` | warunki gotowości widoków (nagłówki i regiony) zmieniają się razem z ekranami; dochodzą cele dla nowych ekranów | każdy wycinek ekranu |

## Wdrożenie

Projekt wdrażają F-08 (tokeny, kroje, komponenty bazowe, bramki) i wycinki S-47–S-59 (ekrany). Kryterium akceptacji wycinka to prototyp jego ekranu z sekcji „Ekrany” i kontrakt z sekcji „Kontrakt dla testów E2E”; opis PR pokazuje zrzut F-06 obok zrzutu prototypu.

### Co skąd brać

| Zasób | Skąd | Dokąd |
| --- | --- | --- |
| Tokeny | `ui-design/tokens.css` | `szczur-radar-web/src/style.css`, bloki `:root` i `:root[data-theme='dark']` |
| Pary kontrastu | `ui-design/pairs.json` | `PAIRS` w `src/styleContract.test.ts` |
| Kroje | `ui-design/fonts/*.woff2` z licencjami | zasoby modułu web z regułami `@font-face` (zakresy `unicode-range` w `ui-design/fonts/README.md`) |
| Ikony | `ui-design/icons/*.svg` z licencją | komponent ikony albo wspólny sprite; grubość kreski z `--icon-stroke` |
| Style komponentów | `ui-design/prototypes/prototype.css` | wzór do odczytania wartości i reguł, nie plik do skopiowania: klasy prototypu nie są nazwami w aplikacji |

W aplikacji kroje mają `font-display: swap` i wstępne ładowanie dwóch plików podzbioru łacińskiego używanych na każdym ekranie (prototypy mają `block`, żeby zrzut nie złapał kroju zastępczego).

### Mapa tokenów

| Dziś w `style.css` | Wartość dziś (jasny / ciemny) | W projekcie | Wartość w projekcie (jasny / ciemny) |
| --- | --- | --- | --- |
| `--bg` | `#f7f4ee` / `#101a20` | `--bg` | `#eef2f8` / `#0a1020` |
| `--surface` | `#fffdf8` / `#17252d` | `--surface` | `#ffffff` / `#101a2e` |
| `--text` | `#4d5560` / `#a7b4bc` | `--text` | `#3f4d63` / `#a9b8d0` |
| `--text-h` | `#1a1814` / `#eef3f5` | `--text-h` | `#0b1528` / `#f0f5ff` |
| `--accent` | `#1d4fa8` / `#7fb0ff` | `--accent` | `#0b63c9` / `#5cc8ff` |
| `--on-accent` | `#ffffff` / `#101a20` | `--on-accent` | `#ffffff` / `#04182a` |
| `--success` | `#1a7a3e` / `#4cc77c` | `--success` | `#12753b` / `#4ade80` |
| `--warning` | `#8f5400` / `#e6b04a` | `--warning` | `#8a5500` / `#f5c563` |
| `--destructive` | `#bf2f28` / `#f0786f` | `--destructive` | `#c0322b` / `#ff7b72` |
| `--highlight` | `#f4e3a0` / `#3a3519` | `--highlight` | `#ffe9a8` / `#40360f` |
| `--stance-neutral` | `#55606b` / `#93a2ac` | `--stance-neutral` | `#4a5870` / `#9fb0c9` |
| `--stance-mixed` | `#8a5200` / `#e0b25a` | `--stance-mixed` | `#8a5500` / `#f5c563` |
| `--ring` | `var(--accent)` | `--ring` | `#0b63c9` / `#5cc8ff` (hex, bo jest w parach kontrastu) |
| `--border` (linie ozdobne) | `color-mix(…, var(--text) 25%, transparent)` | `--line` | `#d3dbe8` / `#22314d` |
| `--border-strong` (krawędź pola) | `var(--text)` | `--border` | `#6f7f99` / `#5f7396` |
| `--sans` | `system-ui, …` | `--font-ui` | `'Hanken Grotesk', system-ui, sans-serif` |
| `--mono` | `ui-monospace, …` | `--font-mono` | `'JetBrains Mono', ui-monospace, monospace` |
| `--font-size-sm` | 14 px | `--font-size-sm` | 14 px |
| `--font-size-md` | 16 px | `--font-size-md` i `--font-size-lg` | 15 px (tekst interfejsu) i 16 px |
| `--font-size-lg` | 20 px | `--font-size-2xl` | 20 px |
| `--font-size-xl` | 32 px | `--font-size-5xl` | 32 px |
| `--font-size-display` | 44 px | `--font-size-display` | 48 px |
| `--space-0` … `--space-5` | 2, 4, 8, 12, 16, 24 px | `--space-0` … `--space-5` | te same wartości w rem |
| `--radius`, `--radius-sm` | 8 px, 6 px | `--radius`, `--radius-sm` | 10 px, 6 px |
| pismo strony `18px/145%`, 16 px do 1024 px, `letter-spacing: 0.18px` | | pismo strony | `--font-size-md` / `--leading-normal`, bez zmiany rozmiaru przy 1024 px i bez światła między literami |

Uwaga na nazwę `--border`: dziś znaczy linię ozdobną, w projekcie krawędź kontrolki. F-08 zmienia obie nazwy w jednym commicie (`--border` na `--line`, `--border-strong` na `--border`), żeby żaden widok nie dostał grubszej linii przez pomyłkę.

Nowe tokeny, których dziś nie ma: role `--bg-glow` (z gradientem tła strony), `--surface-2`, `--selected`, `--scrim`, `--text-muted`, pięć teł `-soft`; `--font-display`; rozmiary `--font-size-xs`, `-xl`, `-3xl`, `-4xl`, `-6xl`; interlinie `--leading-*`, grubości `--weight-*`, światła `--tracking-*`; odstępy `--space-6` … `--space-9`; promienie `--radius-lg`, `-xl`, `-2xl`, `-full`; `--shadow-overlay`; wymiary `--control-height*`, `--icon-size`, `--icon-stroke`, `--bar-height*`, `--width-*`; ruch `--duration*`, `--ease-out`; warstwy `--z-*`. Pełna lista z opisem jest w sekcji „Język wizualny”.

### Mapa komponentów

Nazwy nowych komponentów są propozycją; ustala je plan wycinka.

| Komponent projektu | W aplikacji | Kto buduje |
| --- | --- | --- |
| Przycisk | `AppButton` (dochodzą: z ikoną, sama ikona, duży) | F-08 |
| Komunikat | `StatusMessage` (dochodzi ton „uwaga” i ikona) | F-08 |
| Znacznik stanu | `StatusBadge` | F-08 |
| Stanowisko, Cel ze stanowiskiem | `StanceText` (dochodzi znak i tło; wariant z nazwą celu) | F-08 |
| Motyw | `ThemeToggle` (sama ikona) | F-08 |
| Pola, Odnośnik | style globalne w `style.css` | F-08 |
| Ikony | nowy komponent ikony | F-08 |
| Nawigacja, Nagłówek strony | `AppHeader`; nowy nagłówek strony | S-47 |
| Powiadomienia | `NotificationMenu`, `NotificationBell` | S-47 |
| Ładowanie, Pusta lista, Błąd z ponowieniem, Stronicowanie | nowe wspólne komponenty | S-48 |
| Części panelu admina, Tabela | nowe | S-50 |
| Wiersz posta | `PostCard` staje się wierszem | S-51 |
| Panel boczny, Cel w ocenie | nowy panel; `PostAssessment` | S-51 |
| Filtry | `FeedFilter` (panel pod przyciskiem, znaczniki) | S-51, potem S-54, S-55, S-56 |
| Przełącznik zakresu | nowy | S-52 |
| Cytat w treści | nowy | S-53 |
| Pasek proporcji | nowy | S-57, S-58 |
| Klocek liczby, Karta celu dnia | nowe | S-59 |

Każdy komponent wspólny trafia do `src/components/` i na `/kitchen-sink` we wszystkich stanach z arkusza komponentów.

### Kolejność

1. **F-08**: tokeny i kroje w `style.css`, pary kontrastu, komponenty bazowe, przepięcie obecnych widoków na nowe tokeny, wzorce F-06 od nowa. Po F-08 aplikacja ma nowy wygląd w starym układzie.
2. **S-47**: powłoka z nawigacją u góry i datą danych w pasku. **S-48**: wspólne stany. **S-49**: logowanie i aktywacja.
3. **S-50**: panel admina w trzech częściach. **S-51**: lista zwarta i panel posta. Dalej S-52–S-59 w kolejności roadmapy.

### Zmiany bramek, które F-08 robi świadomie

| Bramka | Dziś | Po F-08 |
| --- | --- | --- |
| `visual/font.spec.ts` i `visual/fonts.conf` | test oczekuje `Liberation Sans` dla nagłówka „Logowanie” i etykiety „Hasło”; alias zamienia `system-ui` i `sans-serif` na Liberation Sans | aplikacja ma własne kroje, więc test oczekuje `Newsreader` dla nagłówka i `Hanken Grotesk` dla etykiety; alias zostaje tylko jako kroje zastępcze i nie decyduje o zrzutach; test dalej jest czerwony, gdy tekst rysuje inny krój |
| `PAIRS` i `MIN_CONTRAST` w `src/styleContract.test.ts` | 19 par, jeden próg 4,5 | 73 pary z `ui-design/pairs.json`, próg osobny dla pary: 4,5 dla tekstu, 3,0 dla elementów interfejsu i fokusu; role w parach są hexem, w tym `--ring` i `--border` |
| lista `CLEANED` | 17 plików; `LoginView.vue` poza listą | pliki zostają na liście po przepięciu; `LoginView.vue` dochodzi w S-49; każdy nowy komponent dochodzi w chwili powstania. `prototype.css` ma dwie wartości `-1px`, które skan `OFF_SCALE` by odrzucił: `.visually-hidden` (`margin: -1px`) trafia do globalnego `style.css`, którego lista nie obejmuje, a `.subnav a` (`margin-bottom: -1px`, podkreślenie zakładki nachodzi na linię pod paskiem o jej grubość) F-08 zapisuje bez literału, np. przez token grubości linii |
| wzorce F-06 | 56 plików PNG | wszystkie zmieniają się raz w F-08 (nowe kolory, kroje, rozmiary), potem grupami w wycinkach ekranów; opis PR mówi, które grupy i dlaczego |
| wysokość pól, `style.css:170-175` | `calc(1.45rem + 14px)`, liczona z pisma strony | `--control-height` (2,5 rem) dla pól i przycisków; duże wyszukiwanie `--control-height-lg` |
| nazwy sekcji `/kitchen-sink` | nazwa sekcji daje nazwę wzorca `kitchen-sink-<slug>.png` | sekcje nazywają się jak komponenty w tym dokumencie, więc wzorce zmieniają nazwy; strażnik wzorców wymaga zgodności w obie strony |
| stany hover i fokus w `visual/targets.ts` | pięć stanów (`state-*`) | te same stany na nowych komponentach; dochodzą stany nowych kontrolek (opcja przełącznika, wiersz posta) |

`design-check` z `tools/design-check/` sprawdza projekt, nie aplikację, i nie jest zadaniem CI. Po F-08 źródłem prawdy o tokenach w aplikacji jest `style.css` z jego testem; `ui-design/tokens.css` zostaje zapisem projektu i musi być z nim zgodny przy każdej zmianie projektu.

## Różnice wobec roadmapy

Lista dla `/10x-roadmap` (v6.2). Numery i issue wycinków zostają; zmieniają się opisy.

| Pozycja | Roadmapa v6.1 | Projekt |
| --- | --- | --- |
| Ekran startowy | nieokreślony | docelowo przegląd dnia (po S-59); do tego czasu lista postów. Pozycje nawigacji dochodzą razem ze swoimi ekranami (tabela w „Ekran startowy”). |
| Adresy | `/`, `/posts/:id`, `/admin`, `/admin/runs/:id` | `/` (ekran startowy), `/posts`, `/posts/:id`, `/targets`, `/targets/:name`, `/admin`, `/admin/quality`, `/admin/posts`, `/admin/runs/:id` |
| S-47 | „stała nawigacja i układ paneli” | pasek u góry i jedna kolumna, bez paneli stałych i bez kolumny bocznej; data danych per źródło w pasku, z dniem i godziną (dziś stoi nad listą postów) |
| S-48 | wspólne stany | cztery wspólne komponenty: ładowanie (szkielet), pusta lista, błąd z ponowieniem, stronicowanie |
| S-50 | panel admina w sekcjach | trzy części z własnymi adresami (Przebiegi, Jakość ocen, Nieudane posty) i pasek części; strona przebiegu osobno; lista przebiegów i nieudanych postów jako tabele |
| S-51 | „strumień wpisów z inspektorem oceny obok listy” | lista zwarta (jeden post w jednej linii) i ocena w panelu wysuwanym z prawej na życzenie; wybrany post ma własny adres (rekomendacja dla niewiadomej wycinka); słowo „post”, nie „wpis” |
| S-52 | przełącznik „pokaż wszystkie” | przełącznik dwóch stanów z liczbami „Inwestycyjne N / Wszystkie M” na ekranie postów, przeglądzie dnia, w katalogu i na karcie celu |
| S-53 | czytnik z podświetlonymi cytatami | nie osobny ekran: podświetlenie, jego wyłącznik i przejścia „poprzedni”/„następny” są częścią panelu posta; podświetlony jest też tytuł, gdy cytat z niego pochodzi, i miejsce celu bez twierdzeń |
| S-54 | sortowanie listy | pole „Kolejność” w panelu filtrów; prototyp pokazuje tylko „od najnowszych” i „od najstarszych”; pola oceny zostają niewiadomą właściciela |
| S-55 | filtr po dacie | pola „Od” i „Do” w panelu filtrów; zakres widać jako znacznik nad listą; lista nie ma ukrytego domyślnego okresu |
| S-56 | wyszukiwanie w treści obok filtra po celu | duże pole „Szukaj w postach” nad listą; filtr po celu „Spółka lub temat” w panelu filtrów |
| S-57 | katalog celów | tabela celów z liczbą postów i udziałem stanowisk; okres „Dziś / 7 dni / 30 dni” (propozycja, domyślnie 7 dni); cel z jednym postem jest w katalogu (propozycja progu: brak). Obie rzeczy zostają niewiadomą właściciela. |
| S-58 | karta celu | karta w panelu z prawej na ekranie katalogu, nie osobna strona; S-57 i S-58 dzielą jeden ekran i jeden wzorzec z postami |
| S-59 | przegląd dnia | pięć klocków liczb (posty, cele ze stanowiskiem, udział stanowisk wzrostowych, spadkowych i pozostałych), trzy najczęściej omawiane cele, tabela celów dnia |
| S-60 | miara „kanał jako cel” | wchodzi do części „Jakość ocen” w tym samym wzorze miary; prototyp jej nie rysuje, bo próg i mianownik ustali wycinek |
| F-08 | tokeny i komponenty | zakres według sekcji „Wdrożenie”: mapa tokenów, kroje w aplikacji, zmiany bramek |

Do uzgodnienia z PRD przy v6.2:

- **FR-039**: klocki „Wzrostowe”, „Spadkowe”, „Pozostałe” na przeglądzie dnia to udział stanowisk w wybranym dniu (prośba właściciela z 2026-11-20). To migawka jednego dnia, nie trend dzień do dnia, którego PRD v4 nie przewiduje.
- **FR-030**: data danych przechodzi z listy postów do paska aplikacji i jest na każdym ekranie po zalogowaniu.
- **FR-007**: przełącznik zakresu jest także w katalogu celów i na karcie celu, zgodnie z doprecyzowaniem v4.

Rozstrzygnięcia:

- **Otwarte pytanie 7** (elementy makiet bez znaczenia w PRD): „Analizy”, „Wątki”, „Ostatnie obserwacje”, „Przejdź do źródła ↓” i strzałka „↑” przy odsetku nie wchodzą do projektu. „Wątki” byłyby sprzeczne z Non-Goals, strzałka przy odsetku sugeruje trend, a pozostałe nie mają danych w kontrakcie. Odnośnik do źródła to „Otwórz oryginał” w panelu posta.
- **Niewiadome F-07**: krój to Newsreader (czytanie), Hanken Grotesk (obsługa) i JetBrains Mono (surowe dane), na licencji OFL, w plikach repozytorium. Paleta A z S-46 jest zastąpiona paletą „Salon w kolorach Radaru”; nazwy ról i mechanizm motywu z S-46 zostają. Projekt zmienia architekturę informacji (jedna kolumna z panelem na życzenie, trzy części panelu admina, katalog i karta celu na jednym ekranie), więc opisy S-47, S-50, S-51, S-53, S-57 i S-58 wymagają przecięcia na nowo.

Wycinek i jego prototypy:

| Wycinek | Prototypy |
| --- | --- |
| F-08 | arkusz komponentów (`ui-design/prototypes/components.html`) |
| S-47 | pasek aplikacji na każdym ekranie; `notifications` |
| S-48 | `state-loading`, `state-empty`, `state-error`, `admin-loading`, `admin-empty`, `admin-error` |
| S-49 | `login`, `activate` |
| S-50 | `admin-runs`, `admin-quality`, `admin-posts`, `run` |
| S-51 | `posts`, `post` |
| S-52 | przełącznik zakresu na `posts`, `overview`, `targets`, `target` |
| S-53 | `post` (podświetlenia, poprzedni i następny) |
| S-54, S-55, S-56 | `posts-filters` |
| S-57 | `targets` |
| S-58 | `target` |
| S-59 | `overview` |
| S-60 | `admin-quality` (wzór miary) |

## Propozycje poza PRD

Pomysły, które wynikają ze standardów albo z pracy nad projektem, a których PRD v4 nie obejmuje. Nie mają prototypów i nie są częścią M-5; każdy wymaga decyzji właściciela i wpisu w PRD.

- **Skróty klawiatury w panelu posta** (następny, poprzedni, zamknij). Nielsen 7: przyspieszenie dla kogoś, kto czyta kilkadziesiąt postów dziennie. PRD nie wspomina o skrótach.
- **Zapamiętanie ostatniego zakresu i okresu** („Inwestycyjne / Wszystkie”, „7 dni”) dla użytkownika. Dziś każdy wybór żyje w adresie strony; zapamiętanie wymaga stanu po stronie przeglądarki albo konta.
- **Podpowiedź odmian celu** na karcie celu („zobacz też: Orlenu, PKN”). Katalog grupuje cele po tekście, bez rozpoznawania spółek, więc odmiany są osobnymi pozycjami; łączenie ich to rozpoznawanie encji, którego PRD nie przewiduje.
- **Znak „nowe od ostatniej wizyty”** na liście postów. Wymaga stanu przeczytania per użytkownik, którego model danych nie ma.
- **Powiadomienia dla zwykłego użytkownika** (na przykład o nowych postach o wybranym celu). Dziś powiadomienia dotyczą tylko przebiegów i tylko admina; MVP nie ma automatycznych wiadomości.

## Decyzja właściciela

Właściciel ocenia projekt na obrazkach: w galerii zrzutów każdego ekranu w obu motywach, na komputerze i telefonie. Decyzje projektowe (kroje, kolory, wzorce) podejmuje projektant według standardów z sekcji „Zasady i standardy”.

| Data | Decyzja |
| --- | --- |
| 2026-11-19 | Aplikacja jest używana głównie na komputerze. Ekranem startowym ma być docelowo przegląd dnia. |
| 2026-11-19 | Wygląd: „Salon w kolorach Radaru” (granat i błękit, szeryf do czytania, grotesk do obsługi), motyw ciemny jako pierwszy, jasny jako drugi, domyślnie według systemu. Pierwsza runda wariantów odrzucona jako „trochę lepszy panel”. |
| 2026-11-20 | Układ: „Kanał”. Nawigacja u góry, jedna kolumna, lista postów zwarta, ocena posta w panelu wysuwanym z prawej z listą widoczną w tle, przegląd dnia z klockami liczb zamiast zdania, przełącznik „Inwestycyjne / Wszystkie” zamiast zdania o ukrytych postach. Układ z kolumną boczną i panelem stałym odrzucony jako „panel administratora”. |
| 2026-11-20 | **Akceptacja kompletu**: 19 ekranów z `ui-design/screens.json` i arkusz komponentów, w obu motywach, przy 1280 i 375 px. Bez poprawek („Jest okej”). |

Przed akceptacją komplet przeszedł niezależny przegląd heurystyczny w dwóch przejściach (heurystyki Nielsena, wybrane reguły WCAG 2.2 AA, zgodność z PRD, bolączki właściciela): dziewięć ustaleń blokujących poprawiono i zamknięto, zero zostało otwartych. Zaakceptowany stan to prototypy w `ui-design/prototypes/` i zrzuty w `ui-design/screens/` z tego samego commitu co ten zapis.

Po akceptacji, w przeglądzie implementacji (2026-11-20, ustalenie F1), doszedł ekran [`posts-all`](ui-design/prototypes/screens/posts-all.html): zakres „Wszystkie” z postem nieinwestycyjnym i postem odsianym przed oceną („za mało treści”), których plan wymagał na ekranach. Jest złożony wyłącznie z komponentów arkusza i przechodzi kontrole `tools/design-check`. Nie przeszedł przeglądu heurystycznego, a właściciel ogląda go w galerii.
