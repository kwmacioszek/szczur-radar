---
project: "Szczur Radar"
version: 1
status: draft
created: 2026-09-25
context_type: greenfield
product_type: data-pipeline
target_scale:
  users: small
  qps: low
  data_volume: small
timeline_budget:
  mvp_weeks: 3
  hard_deadline: null
  after_hours_only: true
---

## Vision & Problem Statement

Kilkuset postów i komentarzy dziennie na Wykopie (tag #gielda) i wybranych kanałach Reddita dotyczy inwestowania — wśród nich pojawiają się wartościowe sygnały i informacje, ale giną w szumie. Uczestnik (jedyny, nazwany użytkownik tego narzędzia) nie ma czasu ani energii, by ręcznie przeglądać obie platformy na bieżąco, więc regularnie przegapia to, co warte uwagi, i traci czas na przewijanie treści bez wartości.

Ogólne narzędzia agregujące (RSS, keyword alerts) nie rozumieją treści na tyle, by ocenić czy sklasyfikować post pod kątem inwestycyjnym, a gotowe komercyjne rozwiązania tego typu są płatne, niszowe lub trudno dostępne. Dodatkowo użytkownik chce mieć pełną kontrolę nad tym, jak dane są zbierane, przetwarzane i oceniane — własny pipeline, nie zależność od cudzej platformy czy czarnej skrzynki.

## User & Persona

Podstawowa (i jedyna w MVP) persona: sam uczestnik, indywidualny inwestor, który śledzi dyskusje inwestycyjne na Wykopie i Reddicie. Sięga po to narzędzie, gdy chce w krótkim czasie zorientować się, czy w ostatnich postach pojawiło się coś wartego uwagi, bez ręcznego przewijania obu platform.

## Success Criteria

### Primary
- Codzienny (lub uruchamiany na żądanie) przepływ pobiera posty z Wykopu (#gielda) dla zadanego zakresu dat/godzin, mapuje je do wspólnego modelu, odrzuca duplikaty, przetwarza przez LLM według ustalonego schematu i zapisuje wynik do bazy danych.
- Użytkownik widzi przetworzone posty w aplikacji webowej: listę chronologiczną z filtrowaniem/sortowaniem oraz widok szczegółowy pojedynczego posta (wersja surowa obok przetworzonej przez LLM).

### Secondary
- Drugie źródło danych — Reddit (wybrane kanały) — dochodzi jako kolejne źródło w tym samym pipeline.
- Wykres giełdowy z nałożonym wykresem trendu wykrytych sygnałów dla konkretnej spółki, żeby zobaczyć jak sygnały przecinają się z realnym ruchem ceny.

### Guardrails
- Jakość danych z LLM musi być wysoka — ekstrakcja/klasyfikacja bez halucynacji.
- Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe.

## User Stories

### US-01: Użytkownik widzi przetworzone posty z Wykopu w aplikacji webowej

- **Given** skonfigurowany zakres dat/godzin i źródło (Wykop)
- **When** przepływ uruchamia się (na żądanie przez API lub automatycznie o zaplanowanej porze)
- **Then** przetworzone posty trafiają do bazy i są widoczne w aplikacji webowej jako lista z filtrowaniem/sortowaniem oraz podglądem szczegółowym (surowy post obok przetworzonego przez LLM)

#### Acceptance Criteria
- Duplikaty nie pojawiają się dwukrotnie na liście
- Każdy widoczny post ma dostępną wersję surową i przetworzoną przez LLM w widoku szczegółowym
- Brak wyników dla zadanego zakresu pokazuje czytelny stan pusty, nie błąd

## Functional Requirements

### Zbieranie i przetwarzanie danych
- FR-001: System pobiera posty z Wykopu (tag #gielda) dla zadanego zakresu dat/godzin. Priority: must-have
  > Socrates: Kontrargument rozważony: "zależność od jednego, zewnętrznego API bez zapasowej opcji — Wykop może
  > ograniczyć/zmienić API w dowolnym momencie." Rozwiązanie: ryzyko przyjęte świadomie — MVP celowo zaczyna od
  > jednego źródła (decyzja z fazy shapingu); Reddit jako drugie źródło jest już zaplanowany jako FR-012
  > (nice-to-have), co ogranicza tę zależność w kolejnym kroku.
- FR-002: Użytkownik może uruchomić przepływ na żądanie z poziomu aplikacji webowej, poprzez API backendu —
  wywołanie jest asynchroniczne (nie blokuje) i udostępnia osobny sposób sprawdzenia statusu/postępu.
  Priority: nice-to-have
  > Socrates: Kontrargumenty rozważone: "przy dobrym harmonogramie rzadko potrzebne" + "długi synchroniczny
  > przepływ wywołany przez API to słabe UX bez informacji o postępie." Rozwiązanie: zdegradowane do nice-to-have;
  > gdy zaimplementowane, wywołanie ma być asynchroniczne z osobnym endpointem statusu.
- FR-003: System uruchamia przepływ automatycznie codziennie o określonej godzinie. Priority: must-have
  > Socrates: Kontrargument rozważony: "automat bez nadzoru może cicho psuć dane, jeśli błędy w deduplikacji/
  > przetwarzaniu się kumulują." Rozwiązanie: ryzyko przyjęte częściowo — wymaga widoczności błędów przetwarzania
  > dla operatora (patrz Non-Functional Requirements).
- FR-004: *(usunięty jako osobny wymóg funkcjonalny)*
  > Socrates: Kontrargumenty rozważone: "przedwczesna abstrakcja — sztywny kontrakt zanim poznasz kształt danych
  > z Reddita" + "to opis architektury, nie zachowania widocznego dla użytkownika." Rozwiązanie: oba przyjęte —
  > FR-004 usunięty jako wymóg funkcjonalny; opisywał wewnętrzny kontrakt mapper/LLM, który jest architekturą,
  > nie zachowaniem produktu. Numeracja zachowana dla ciągłości historii decyzji.
- FR-005: System odrzuca duplikaty postów po identyfikatorze źródłowym (ten sam post nie jest przetwarzany
  dwukrotnie). Treściowo podobne posty od różnych autorów NIE są odrzucane — ich powtarzalność jest sygnałem
  zainteresowania tematem, nie szumem do odfiltrowania. Priority: must-have
  > Socrates: Kontrargument rozważony: "prosta dedup po ID może przepuszczać duplikaty treściowe / odrzucać
  > sam sygnał." Rozwiązanie: doprecyzowane przez użytkownika — dedup celowo ogranicza się do ID posta;
  > powtórki tej samej treści od różnych autorów to zamierzenie, nie błąd.
- FR-006: System przetwarza każdy post przez LLM według ustalonego, ustandaryzowanego schematu wejścia/wyjścia
  (prompt + JSON output ograniczony do schematu, żeby zminimalizować ryzyko halucynacji) i zapisuje wynik.
  Priority: must-have
  > Socrates: Kontrargument rozważony: "zawartość schematu jeszcze nieznana — ryzyko pustego przepisania pól
  > / halucynacji." Rozwiązanie: potwierdzone jako wymóg — kontrakt JSON i ograniczenie halucynacji, dokładna
  > zawartość schematu do ustalenia (patrz Open Questions).
- FR-007: System zapisuje przetworzone posty do bazy danych. Priority: must-have
  > Socrates: Brak kontrargumentu; zostaje jak jest — niezbędne, żeby API miało skąd czerpać dane.

### Udostępnianie danych
- FR-008: System udostępnia przetworzone posty z bazy danych aplikacji webowej przez API, z obsługą
  paginacji/limitu wyników. Priority: must-have
  > Socrates: Kontrargument rozważony: "brak paginacji/limitu przy rosnącej bazie stanie się nieużywalne."
  > Rozwiązanie: przyjęte — paginacja/limit dodane do FR wprost.

### Aplikacja webowa
- FR-009: Użytkownik widzi listę przetworzonych postów, domyślnie posortowaną chronologicznie. Priority: must-have
  > Socrates: Kontrargument rozważony: "chronologia może nie być najlepszym porządkiem domyślnym — ważność
  > sygnału mogłaby lepiej decydować o kolejności." Rozwiązanie: chronologia zostaje jako domyślny porządek
  > w MVP (nie ma jeszcze zdefiniowanej miary "ważności" — zależy od dokładnej treści schematu oceny); zmiana
  > porządku przez użytkownika to FR-010.
- FR-010: Użytkownik może sortować listę postów po dacie i źródle. Priority: must-have
  > Socrates: Kontrargumenty rozważone: "kryteria filtrowania niezdefiniowane bez schematu LLM" i "to sporo
  > pracy frontendowej." Rozwiązanie: FR zawężony do sortowania po dacie/źródle (tanie, dane już dostępne);
  > filtrowanie/sortowanie po klasyfikacji LLM przeniesione do FR-015 (nice-to-have), bo zależy od schematu
  > jeszcze nieustalonego.
- FR-011: Użytkownik widzi widok szczegółowy posta w czytelnej, przetworzonej formie; surowy JSON jest dostępny
  opcjonalnie (np. pod przełącznikiem "dane dla nerdów"), nie pokazywany domyślnie. Priority: must-have
  > Socrates: Kontrargument rozważony: "surowy JSON bez formatowania to tryb debug, niekoniecznie potrzebny
  > użytkownikowi końcowemu." Rozwiązanie: przyjęte — przetworzona wersja jest domyślna, surowa dostępna
  > opcjonalnie.
- FR-016: Widok posta zawiera link do oryginalnego posta na platformie źródłowej (Wykop/Reddit), umożliwiający
  przejście do pełnego kontekstu. Priority: must-have

### Kontrola dostępu
- FR-017: System udostępnia endpoint logowania, który weryfikuje dane logowania i ustanawia sesję serwerową
  w bezpiecznym ciasteczku. Priority: must-have
- FR-018: System udostępnia endpoint wylogowania, który unieważnia bieżącą sesję po stronie serwera. Priority: must-have
- FR-019: System udostępnia endpoint zwracający dane bieżącego zalogowanego użytkownika. Priority: must-have
- FR-020: Wszystkie chronione endpointy API wymagają ważnej sesji, weryfikowanej przez jedną centralną zależność.
  Priority: must-have
  > Decyzja: dodane po zmianie infrastruktury na publicznie dostępną (patrz Access Control) — zamiast tymczasowego
  > współdzielonego sekretu (Basic Auth/klucz API) wybrano minimalny, prawdziwy model użytkownika i sesji, żeby
  > nie trzeba było go później wyrzucać przy przejściu na wielu użytkowników. Zakres celowo wąski — bez rejestracji,
  > resetu hasła, ról, OAuth, zarządzania kluczami API (patrz Non-Goals).

### Rozszerzenia (poza MVP)
- FR-012: System pobiera posty z wybranych kanałów Reddita jako drugie źródło danych. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "Reddit ma inny format/ograniczenia niż Wykop — dodanie źródła może nie
  > być tak tanie, jak sugeruje kontrakt mappera." Rozwiązanie: ryzyko przyjęte i odnotowane; FR zostaje
  > nice-to-have.
- FR-013: Aplikacja webowa pokazuje wykres giełdowy z nałożonym wykresem trendu wykrytych sygnałów dla wybranej
  spółki. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "wymaga nowej integracji z danymi giełdowymi + rozpoznania spółki/tickera
  > — to więcej niż samo nałożenie wykresu." Rozwiązanie: ryzyko przyjęte i odnotowane; zostaje nice-to-have,
  > zależność od czwartej integracji (dane giełdowe) jawnie nazwana.
- FR-014: System analizuje trend sygnałów w czasie (przez LLM) i powiadamia użytkownika, gdy dla danej
  spółki/tematu pojawia się obiecujący trend. Priority: nice-to-have
  > Socrates: Kontrargumenty rozważone: "obiecujący trend to subiektywna ocena LLM bez jasnego kryterium (ryzyko
  > fałszywych alarmów/ciszy)" i "nowy kanał powiadomień to dodatkowa infrastruktura." Rozwiązanie: oba ryzyka
  > przyjęte świadomie — użytkownik ocenia tę funkcję jako wartościową mimo ryzyk i chce się nią zająć po MVP;
  > kryterium "obiecującego trendu" i kanał powiadomień do doprecyzowania przy realizacji.
- FR-015: Użytkownik może filtrować/sortować listę postów po klasyfikacji nadanej przez LLM (np. sentyment, typ
  tezy — dokładne kryteria do ustalenia). Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "zależy w pełni od jeszcze nieustalonego schematu." Rozwiązanie: przyjęte
  > do wiadomości — FR zostaje jako placeholder, doprecyzowany po ustaleniu schematu.

## Non-Functional Requirements

- Operator otrzymuje jednoznaczny, widoczny sygnał, gdy zaplanowany dzienny przebieg zbierania/przetwarzania nie powiedzie się lub zakończy częściowym błędem — nie dowiaduje się o tym pośrednio, przez brak nowych danych.
- Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu; warstwa oceny, zapisu i udostępniania danych pozostaje niezmieniona.

## Business Logic

Aplikacja ocenia każdy zebrany post pod kątem tego, czy stanowi wartościowy sygnał inwestycyjny — na podstawie obecności konkretnej tezy z uzasadnieniem, wyraźnego kierunku/siły wydźwięku oraz nowości informacji — i przypisuje go do konkretnej spółki lub tematu.

Wejściem oceny jest treść posta (tytuł, treść, docelowo też komentarze) wraz z metadanymi źródłowymi (data publikacji, źródło, link do oryginału). Wyjściem jest ustandaryzowany wynik oceny per post: czy jest wartościowy, dla jakiej spółki/tematu, jaki kierunek i siłę wydźwięku niesie, oraz krótkie uzasadnienie/streszczenie tezy.

Użytkownik spotyka tę ocenę na liście i w widoku szczegółowym w aplikacji webowej i może przejść przez link do oryginalnego posta, żeby zweryfikować kontekst. W rozszerzeniu poza MVP (FR-013/FR-014) oceny są agregowane w czasie per spółka, nakładane na wykres notowań giełdowych, z alertami przy obiecującym trendzie.

## Access Control

Jeden użytkownik w MVP (konto zakładane ręcznie, bez publicznej rejestracji), ale zbudowany jako pełny mechanizm logowania i sesji — nie tymczasowy placeholder — żeby przejście na wielu użytkowników w przyszłości było rozszerzeniem architektury, nie jej przepisaniem. Powód zmiany względem wcześniejszej decyzji: MVP wdraża się na publicznie dostępnej infrastrukturze (nie wyłącznie w sieci domowej/VPN), więc brak jakiejkolwiek kontroli dostępu przestał być bezpieczny.

Sesja jest utrzymywana po stronie serwera (nie token bezstanowy typu JWT), z tokenem sesyjnym w ciasteczku o właściwościach Secure, HttpOnly i SameSite=Strict; wylogowanie unieważnia sesję po stronie serwera, nie tylko usuwa ciasteczko po stronie klienta. Hasła użytkowników są przechowywane wyłącznie jako hash Argon2, nigdy w postaci jawnej; przechowywany token sesji jest analogicznie hashowany, nie surowy. `SameSite=Strict` nie jest traktowane jako kompletna ochrona przed CSRF — żądania zmieniające stan (np. FR-002, FR-017–FR-020) wymagają dodatkowej walidacji pochodzenia żądania. Wszystkie chronione endpointy API korzystają z jednej, centralnej zależności weryfikującej tożsamość użytkownika, żeby logika biznesowa nie była powiązana z konkretnym mechanizmem uwierzytelniania. Frontend i API wdrażane są produkcyjnie pod tym samym originem, żeby uniknąć złożoności cross-origin cookies i CORS z poświadczeniami.

Poza zakresem MVP (patrz Non-Goals): publiczna rejestracja, reset hasła, role/uprawnienia, OAuth, zarządzanie kluczami API.

## Non-Goals

- **Personalizacja kryteriów oceny per użytkownik** — jedno wspólne kryterium "wartościowości" dla wszystkich. Przy istotnie większej skali (wielu użytkownikach) wymagałoby to customizacji per osoba/portfel, ale to świadomie poza zakresem MVP.
- **Przetwarzanie komentarzy pod postami** — MVP ocenia tylko posty; komentarze to jawnie odroczone rozszerzenie na przyszłość.
- **Publiczna rejestracja, reset hasła, role/uprawnienia, OAuth, zarządzanie kluczami API** — model użytkownika i sesji jest przygotowany pod wielu użytkowników w przyszłości, ale te funkcje są świadomie poza zakresem MVP; dziś jest dokładnie jedno konto, zakładane ręcznie.

## Open Questions

1. **Czy `product_type` to "data-pipeline" czy "web-app"?** — Projekt opisany jako pipeline z API + aplikacja webowa jako pełnoprawny sposób konsumpcji, nie tylko wewnętrzne narzędzie. Wybrano `data-pipeline` jako najbliższą wartość. Owner: użytkownik. By: przed wyborem stacku technologicznego.
2. **Dokładny kształt pól w JSON zwracanym przez ocenę LLM** — ustalone kryteria (teza z uzasadnieniem, kierunek/siła wydźwięku, nowość, spółka/temat) trzeba jeszcze przełożyć na konkretne nazwy pól/typy i walidację schematu. Owner: użytkownik. By: przed implementacją FR-006.
3. **Kanał i próg dla FR-014 (powiadomienia o obiecującym trendzie)** — nie ustalono jeszcze, czym jest "obiecujący trend" liczbowo ani gdzie ma trafiać powiadomienie. Owner: użytkownik. By: przy realizacji FR-014 (poza MVP).
4. **Ostateczny termin** — świadomie zostawiony otwarty na etapie planowania (bez sztywnego `hard_deadline`). Owner: użytkownik.
