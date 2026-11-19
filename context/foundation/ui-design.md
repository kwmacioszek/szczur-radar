# Projekt UI/UX aplikacji Szczur Radar

> Dokument żywy (F-07, `ui-design-system`). Źródło wyglądu i układu aplikacji webowej: wycinki UI wdrażają swoje ekrany według niego. Zmiana projektu to edycja tego dokumentu z akceptacją właściciela i zielonymi kontrolami [`tools/design-check`](../../tools/design-check/README.md).

## Cel i zakres

Aplikacja ma wyglądać i działać jak dopracowany produkt, a nie surowy panel. Ten dokument opisuje projekt całej aplikacji webowej: zasady i standardy, zadania użytkownika, układ ekranów i nawigację, język wizualny, komponenty, treść etykiet oraz to, czego potrzebują wdrożenie i testy.

Zakres to dokładnie wymagania [PRD v4](prd.md) dla aplikacji webowej (FR-006–FR-040) i ekrany kamienia milowego M-5 z [roadmapy](roadmap.md). Projekt nie dodaje wymagań. Pomysły wynikające ze standardów, których PRD nie obejmuje, są w sekcji „Propozycje poza PRD” i nie mają prototypów.

Makiety z [`ui-ux-proposals.md`](ui-ux-proposals.md) były listą funkcji, a nie wzorem wyglądu. Projekt nie zawiera ich elementów bez pokrycia w wymaganiach: tickerów i nazw kanonicznych spółek, scalania odmian celu, trendu, jednej etykiety stanowiska na cały post, „Zapisanych”, „Analiz”, „Wątków”, „Ostatnich obserwacji”, statusu pipeline'u dla zwykłego użytkownika i wyboru postów „wartych przeczytania”.

Stan sekcji:

| Sekcja | Stan |
| --- | --- |
| Cel i zakres, Użytkownik i zadania, Zasady i standardy, Architektura informacji i nawigacja, Ekrany | gotowe (faza 1) |
| Język wizualny, Komponenty, Treść i etykiety | po wyborze wariantu wyglądu (faza 2) |
| Prototypy ekranów, Decyzja właściciela | faza 3 |
| Kontrakt dla testów E2E, Wdrożenie, Różnice wobec roadmapy, Propozycje poza PRD | faza 4 |

## Użytkownik i zadania

Aplikacji używają zaproszone osoby (rola użytkownika) i administrator (rola admina, zarazem operator). Korzystają głównie z komputera; telefon jest urządzeniem dodatkowym. Projekt układa więc ekrany pod szeroki ekran (panele obok siebie), a na wąskim daje jedną kolumnę bez utraty funkcji.

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
| Trudno wyłapać sedno posta | Wiersz listy pokazuje cele ze stanowiskiem wobec każdego (znak, słowo, kolor). Tytuł albo początek treści jest pod spodem, a nie zamiast tego. |
| Za dużo klikania | Ocena wybranego posta otwiera się obok listy; lista zostaje na miejscu z zaznaczonym wierszem. Przyciski „poprzedni” i „następny” przechodzą po bieżącej liście. |
| Nieczytelny panel admina | Panel podzielony na trzy części o jednym temacie każda (przebiegi, jakość ocen, nieudane posty) zamiast jednej długiej strony z pięcioma sekcjami. |

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
4. **Spójność i standardy.** Ten sam wzorzec „lista ze szczegółami obok” dla postów i celów; te same komponenty i etykiety w każdym widoku.
5. **Zapobieganie błędom.** Akcje admina o skutkach (start przebiegu, przywrócenie postów) mówią przed kliknięciem, czego dotyczą; niedostępna akcja jest wyłączona z podanym powodem.
6. **Rozpoznawanie zamiast przypominania.** Nawigacja ma opisy, nie same ikony. Aktywne filtry są widoczne przy liście.
7. **Elastyczność i sprawność.** Filtr i sortowanie żyją w adresie strony. Lista i ocena przewijają się niezależnie.
8. **Estetyka i umiar.** Kolor niesie znaczenie (stanowisko, stan) albo wskazuje akcję; ozdobniki bez informacji odpadają.
9. **Pomoc w błędach.** Komunikat mówi, co się nie udało i co zrobić („Spróbuj ponownie”); nie przeprasza i nie zgaduje.
10. **Pomoc i dokumentacja.** Przy ocenie stała notka, że opisuje stanowisko autora, a nie prognozę. Miary, które wymagają objaśnienia (proporcje, kolejność), mają je pod nagłówkiem.

### Wzorce układu z dojrzałych systemów projektowych

| Wzorzec | Źródło | Zastosowanie |
| --- | --- | --- |
| Lista ze szczegółami obok (list-detail) | [Material Design 3, układy kanoniczne](https://m3.material.io/foundations/layout/canonical-layouts) | Posty z oceną obok; katalog celów z kartą celu obok. Na wąskim ekranie lista i szczegóły to osobne ekrany. |
| Stała nawigacja główna | [Material Design 3, nawigacja](https://m3.material.io/components/navigation-rail/overview); [Carbon, UI shell](https://carbondesignsystem.com/components/UI-shell-header/usage/) | Jedna nawigacja w powłoce aplikacji zamiast nagłówka w każdym widoku. |
| Tabela danych | [Carbon, data table](https://carbondesignsystem.com/components/data-table/usage/) | Cele dnia, przebiegi, nieudane posty: nagłówki kolumn, liczby do prawej, wiersz jako cel kliknięcia. |
| Formularze | [GOV.UK Design System](https://design-system.service.gov.uk/components/text-input/) | Etykieta nad polem, podpowiedź pod etykietą, błąd słowami przy polu, jedna kolumna. |
| Pusty stan, ładowanie, błąd | [Carbon, empty states](https://carbondesignsystem.com/patterns/empty-states-pattern/) i [loading](https://carbondesignsystem.com/patterns/loading-pattern/) | Pusty stan mówi, co zrobić dalej; ładowanie jako szkielet treści o kształcie wyniku; błąd z przyciskiem ponowienia. |
| Zachowanie widżetów | [WAI-ARIA Authoring Practices](https://www.w3.org/WAI/ARIA/apg/patterns/) | Lista powiadomień jako rozwijany panel (disclosure), części panelu admina jako karty (tabs). |

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
| Posty: lista z oceną obok | użytkownik | `/posts`, wybrany post `/posts/:id` (ten adres już istnieje) | nawigacja „Posty”; link z przeglądu i z karty celu |
| Cele: katalog z kartą celu obok | użytkownik | `/targets`, wybrany cel `/targets/:name` | nawigacja „Cele”; link z przeglądu |
| Panel admina: Przebiegi | admin | `/admin` (bez zmian) | nawigacja „Panel admina” |
| Panel admina: Jakość ocen | admin | `/admin/quality` | karta w panelu admina |
| Panel admina: Nieudane posty | admin | `/admin/posts` | karta w panelu admina |
| Przebieg | admin | `/admin/runs/:id` (bez zmian) | wiersz przebiegu; powiadomienie |

Wybrany post ma własny adres: działa link z powiadomienia i z innego ekranu, „wstecz” wraca do listy z filtrem, a odświeżenie strony nie gubi wyboru. To rekomendacja projektu dla niewiadomej S-51; ostatecznie rozstrzyga ją plan tego wycinka.

Czytanie posta z podświetlonymi cytatami nie jest osobnym ekranem. Podświetlenie i przejścia „poprzedni”/„następny” należą do panelu oceny (S-53 dodaje je do ekranu z S-51). Na wąskim ekranie panel oceny jest osobnym ekranem pod adresem posta.

Katalog celów i karta celu to jeden ekran w tym samym wzorcu co posty: lista celów po lewej, karta wybranego celu po prawej.

### Nawigacja

Nawigacja główna jest częścią powłoki aplikacji (jeden `banner` na stronie), w tej kolejności: **Przegląd**, **Posty**, **Cele**, a dla admina także **Panel admina**. Kolejność odpowiada kolejności zadań: obraz dnia, lista, szukanie po celu, obsługa.

Obok nawigacji, w tej samej powłoce: data ostatniej aktualizacji danych per źródło (FR-030), powiadomienia (tylko admin), przełącznik „Ciemny motyw” (FR-040), e-mail zalogowanego i „Wyloguj” (FR-013). Ekrany publiczne (logowanie, aktywacja) nie mają nawigacji.

Położenie nawigacji (góra albo bok) należy do wariantu wyglądu i jest opisane w sekcji „Język wizualny”.

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

Trzy części jako karty pod wspólnym nagłówkiem „Panel admina”, każda z własnym adresem:

- **Przebiegi**: nowy przebieg (zakres dat, źródła), dni bez danych z uzupełnieniem, ostatnie przebiegi. To praca codzienna, więc ta część otwiera panel.
- **Jakość ocen**: miary z 7 dni per źródło (nieudane próby, tezy w innym języku, po S-60 „kanał jako cel”) z progami.
- **Nieudane posty**: lista z etapem i ostatnią próbą, przywracanie zaznaczonych.

Strona przebiegu zostaje osobnym ekranem (liczniki, wynik per źródło, kroki, błąd), z powrotem do części „Przebiegi”.

### Szeroki i wąski ekran

- **Od 900 px**: powłoka z nawigacją i dwa panele obok siebie na ekranach list (lista około 30 rem, szczegóły reszta). Panele przewijają się niezależnie, strona nie.
- **Poniżej 900 px**: jedna kolumna. Lista i szczegóły to osobne ekrany; ze szczegółów wraca się przyciskiem „wstecz” przeglądarki albo linkiem do listy. Nawigacja zwija się do wiersza pod nazwą aplikacji.
- **Od 320 px**: strona nie przewija się w poziomie.

## Ekrany

Ekrany M-5 z wymaganiami i źródłem danych. „Dziś” znaczy, że API już daje te dane; numer wycinka wskazuje, kto dostarcza brakujące API albo widok. Prototypy każdego ekranu (oba motywy, 1280 i 375 px) powstają w fazie 3 w `ui-design/prototypes/screens/`.

| Ekran | Cel ekranu | Wymagania | Dane | Prototyp (faza 3) |
| --- | --- | --- | --- | --- |
| Przegląd dnia | Obraz wybranego dnia: liczba postów, liczba celów ze stanowiskiem, źródła, cele dnia z proporcjami, najczęściej omawiane cele z najnowszymi postami | FR-039, FR-038, FR-035, FR-007 | S-59 (na S-55 i S-58) | `overview` |
| Posty: lista z oceną obok | Przejrzeć posty i przeczytać ocenę wybranego: cele ze stanowiskiem, tezy z cytatem, link do oryginału, dane surowe | FR-007, FR-009, FR-010, FR-011a, FR-030 | dziś (lista, filtr po celu i stanowisku, ocena, link, dane surowe); układ S-51 | `posts` |
| Posty: filtry i kolejność | Zawęzić listę: szukanie w treści, dzień albo zakres dat, kolejność, przełącznik „Pokaż wszystkie” | FR-036, FR-035, FR-008, FR-011b, FR-007 | S-56, S-55, S-54, S-52 | `posts-filters` |
| Post z podświetlonymi cytatami | Zobaczyć w treści fragmenty, na których oparto ocenę; wyłączyć podświetlenie; przejść do poprzedniego i następnego | FR-009 | dziś (cytaty są w ocenie); widok S-53 | `post` |
| Cele: katalog z kartą celu | Znaleźć cel po nazwie, zobaczyć liczbę postów i proporcje stanowisk (wzrostowe, spadkowe, pozostałe), przejść do wypowiedzi o celu | FR-037, FR-038, FR-011a | S-57, S-58 | `targets` |
| Logowanie | Zalogować się e-mailem i hasłem; komunikaty błędnych danych i limitu prób | FR-012, FR-040 | dziś; widok S-49 | `login` |
| Aktywacja konta | Ustawić hasło z jednorazowego linku; link nieważny, reguły hasła | FR-018 | dziś; widok S-49 | `activate` |
| Powłoka aplikacji | Nawigacja według roli, data aktualizacji danych, przełącznik motywu, wylogowanie | FR-015, FR-013, FR-030, FR-040 | dziś; widok S-47 | na każdym ekranie zalogowanym |
| Panel admina: Przebiegi | Uruchomić przebieg, uzupełnić dni bez danych, zobaczyć ostatnie przebiegi i ich stan | FR-021, FR-022, FR-015 | dziś; widok S-50 | `admin-runs` |
| Panel admina: Jakość ocen | Sprawdzić miary jakości per źródło wobec progów | FR-033 | dziś; „kanał jako cel” S-60 | `admin-quality` |
| Panel admina: Nieudane posty | Zobaczyć nieudane i martwe posty, przywrócić zaznaczone | FR-034 | dziś; widok S-50 | `admin-posts` |
| Przebieg | Liczniki, wynik per źródło, kroki, błąd i powody „wymaga uwagi” jednego przebiegu | FR-021 | dziś; widok S-50 | `run` |
| Powiadomienia admina | Lista przebiegów wymagających uwagi z przejściem do przebiegu | FR-021, FR-015 | dziś | `notifications` |
| Wspólne stany | Ładowanie, pusta lista i błąd z ponowieniem, takie same w każdym widoku | FR-007, FR-009 | dziś; komponenty S-48 | `state-loading`, `state-empty`, `state-error` |

Dni bez danych są częścią ekranu „Panel admina: Przebiegi”. Elementy zależne od wycinków, których niewiadome należą do właściciela (pola sortowania S-54, domyślny okres i próg katalogu S-57, progi S-60), prototypy pokazują jako propozycję niewiążącą do czasu planu danego wycinka.

## Język wizualny

> Do uzupełnienia w fazie 2, po wyborze wariantu wyglądu.

## Komponenty

> Do uzupełnienia w fazie 2.

## Treść i etykiety

> Do uzupełnienia w fazie 2.

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
