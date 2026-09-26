---
project: "Szczur Radar"
context_type: greenfield
created: 2026-09-24
updated: 2026-09-26
product_type: data-pipeline
target_scale:
  users: small
  qps: low
  data_volume: small
timeline_budget:
  mvp_weeks: 3
  hard_deadline: null
  after_hours_only: true
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  gray_areas_resolved:
    - topic: "pain category"
      decision: "dane rozproszone w wielu miejscach + paraliż decyzyjny/przeciążenie informacją + tarcie w codziennym przepływie pracy"
    - topic: "insight"
      decision: "ogólne agregatory/alerty nie łączą wielu źródeł społecznościowych z ekstrakcją LLM dopasowaną do inwestowania; istniejące narzędzia są płatne/niszowe/trudno dostępne; dodatkowo użytkownik chce mieć pełną kontrolę nad pipeline'em"
    - topic: "primary persona scope"
      decision: "pojedyncza główna persona w MVP — sam uczestnik; początkowe konto ma rolę admin. Rola user jest częścią minimalnego modelu kontroli dostępu, ale nie oznacza dodania drugiej persony do MVP"
    - topic: "access control mechanism"
      decision: "aplikacja wymaga uwierzytelnienia, ponieważ docelowe wdrożenie MVP może być publicznie osiągalne; minimalny model autoryzacji obejmuje role user i admin. Początkowe konto administratora jest tworzone poza publiczną rejestracją. Publiczna rejestracja, zarządzanie użytkownikami/rolami, reset hasła, OAuth i API keys są poza zakresem MVP"
    - topic: "MVP scope (first flow)"
      decision: "zawężone z pełnego obrazu (2 źródła + wykres giełdowy/trend) do: orkiestrowany flow, jedno źródło na start (Wykop, z miejscem na rozszerzenie), LLM, API, aplikacja webowa z listą + filtrowaniem/sortowaniem + widokiem szczegółowym. Reddit i wykres giełdowy z trendem przesunięte do Secondary/nice-to-have."
    - topic: "product framing"
      decision: "product_type: data-pipeline (nuans web-app w Open Questions); target_scale.users: small; brak sztywnego deadline'u; praca wyłącznie po godzinach"
    - topic: "non-goals"
      decision: "personalizacja kryteriów oceny per użytkownik oraz przetwarzanie komentarzy pod postami — jawnie poza MVP"
  frs_drafted: 22
  quality_check_status: accepted
---

# Shape Notes

## Seed idea (verbatim)

Chce zbudowac aplikacje, ktora bedzie pobierac np. z wykopu tag #gielda lub z kanalow reddit posty ludzi dotyczace inwestowania. Kazdy post bylby sciagany jako surowy json, standaryzowany do wspolnego modelu, obrabiany przez LLM wedlug schematu i nastepnie zapisywany do bazy danych gdzie bylby wystawiony prosty fastapi dla aplikacji webowej.

Zaakceptowana decyzja architektoniczna: podział na dwa moduły — Moduł 1 (backend): flow oparty o LangGraph + integracja z LLM + baza danych + REST API. Moduł 2 (frontend): aplikacja webowa korzystająca z REST API modułu 1.

Istniejący postęp: działający POC collectora dla obu źródeł (Wykop — otwarte API; Reddit — przez Arctic Shift).

## Vision & Problem Statement

Kilkuset postów i komentarzy dziennie na Wykopie (tag #gielda) i wybranych kanałach Reddita dotyczy inwestowania — wśród nich pojawiają się wartościowe sygnały i informacje, ale giną w szumie. Uczestnik (jedyny, nazwany użytkownik tego narzędzia) nie ma czasu ani energii, by ręcznie przeglądać obie platformy na bieżąco, więc regularnie przegapia to, co warte uwagi, i traci czas na przewijanie treści bez wartości.

Ogólne narzędzia agregujące (RSS, keyword alerts) nie rozumieją treści na tyle, by ocenić czy sklasyfikować post pod kątem inwestycyjnym, a gotowe komercyjne rozwiązania tego typu są płatne, niszowe lub trudno dostępne. Dodatkowo użytkownik chce mieć pełną kontrolę nad tym, jak dane są zbierane, przetwarzane i oceniane — własny pipeline, nie zależność od cudzej platformy czy czarnej skrzynki.

## User & Persona

Podstawowa (i jedyna w MVP) persona: sam uczestnik, indywidualny inwestor, który śledzi dyskusje inwestycyjne na Wykopie i Reddicie. Sięga po to narzędzie, gdy chce w krótkim czasie zorientować się, czy w ostatnich postach pojawiło się coś wartego uwagi, bez ręcznego przewijania obu platform.

MVP nadal ma jedną główną personę produktową. Role user/admin opisują model kontroli dostępu, a nie dwie osobne persony. Początkowy użytkownik aplikacji ma rolę admin.

## Access Control

Dostęp do aplikacji wymaga uwierzytelnienia. Brak logowania i ochrona wyłącznie przez LAN/VPN nie są już założeniem MVP, ponieważ aplikacja może być wdrożona w publicznie osiągalnym środowisku.

Minimalny model kontroli dostępu rozróżnia dwie role:

- `user` — może korzystać z funkcji konsumpcji danych: przeglądać listę przetworzonych postów, sortować/filtrować dane oraz otwierać widok szczegółowy;
- `admin` — posiada możliwości roli user oraz może wykonywać operacje administracyjne/operacyjne, takie jak ręczne uruchomienie pipeline'u, jeśli dana funkcja znajduje się w zaimplementowanym zakresie.

Początkowe konto użytkownika jest tworzone jako admin.

Publiczna rejestracja, reset hasła przez użytkownika, zarządzanie użytkownikami i rolami z poziomu UI, dodatkowe role, granular permissions, OAuth oraz zarządzanie API keys są poza zakresem MVP.

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

## Functional Requirements

### Zbieranie i przetwarzanie danych
- FR-001: System pobiera posty z Wykopu (tag #gielda) dla zadanego zakresu dat/godzin. Priority: must-have
  > Socrates: Kontrargument rozważony: "zależność od jednego, zewnętrznego API bez zapasowej opcji — Wykop może
  > ograniczyć/zmienić API w dowolnym momencie." Rozwiązanie: ryzyko przyjęte świadomie — MVP celowo zaczyna od
  > jednego źródła (decyzja z Fazy 3); Reddit jako drugie źródło jest już zaplanowany jako FR-012 (nice-to-have),
  > co ogranicza tę zależność w kolejnym kroku.
- FR-002: Administrator może uruchomić przepływ na żądanie z poziomu aplikacji webowej, poprzez API backendu —
  wywołanie jest asynchroniczne (nie blokuje) i udostępnia osobny sposób sprawdzenia statusu/postępu.
  Priority: nice-to-have
  > Socrates: Kontrargumenty rozważone: "przy dobrym harmonogramie rzadko potrzebne" + "długi synchroniczny
  > przepływ wywołany przez API to słabe UX bez informacji o postępie." Rozwiązanie: zdegradowane do nice-to-have;
  > gdy zaimplementowane, wywołanie ma być asynchroniczne z osobnym endpointem statusu.
- FR-003: System uruchamia przepływ automatycznie codziennie o określonej godzinie. Priority: must-have
  > Socrates: Kontrargument rozważony: "automat bez nadzoru może cicho psuć dane, jeśli błędy w deduplikacji/
  > przetwarzaniu się kumulują." Rozwiązanie: ryzyko przyjęte częściowo — wymaga widoczności błędów przetwarzania
  > dla operatora; doprecyzowane jako NFR w Fazie 5.
- FR-004: [usunięty jako osobny FR]
  > Socrates: Kontrargumenty rozważone: "przedwczesna abstrakcja — sztywny kontrakt zanim poznasz kształt danych
  > z Reddita" + "to opis architektury, nie zachowania widocznego dla użytkownika." Rozwiązanie: oba przyjęte —
  > FR-004 usunięty jako wymóg funkcjonalny. Treść przeniesiona do `## Forward: tech-stack` (kontrakt mapper/LLM
  > jako wskazówka implementacyjna) i do NFR w Fazie 5 (łatwość dodania nowego źródła danych).
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
  > zawartość schematu ustalana w Fazie 5.
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
  > w MVP (nie ma jeszcze zdefiniowanej miary "ważności" — to zależy od schematu LLM z Fazy 5); zmiana porządku
  > przez użytkownika to FR-010.
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
- FR-017: Użytkownik może zalogować się do aplikacji przy użyciu przypisanego mu konta. Priority: must-have
- FR-018: Zalogowany użytkownik może się wylogować. Priority: must-have
- FR-019: Niezalogowany użytkownik nie ma dostępu do chronionych danych ani funkcji aplikacji. Priority: must-have
- FR-020: System rozróżnia role user i admin; operacje oznaczone jako administracyjne są dostępne wyłącznie dla
  roli admin. Priority: must-have
- FR-021: Administrator może otworzyć prosty panel użytkowników i zobaczyć istniejące konta wraz z przypisaną
  rolą (`user` lub `admin`). Priority: nice-to-have
- FR-022: Administrator może utworzyć nowe konto użytkownika i podczas tworzenia przypisać mu rolę `user` albo
  `admin`. Priority: nice-to-have

### Rozszerzenia (poza MVP)
- FR-012: System pobiera posty z wybranych kanałów Reddita jako drugie źródło danych. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "Reddit ma inny format/ograniczenia niż Wykop — dodanie źródła może nie
  > być tak tanie, jak sugeruje kontrakt mappera." Rozwiązanie: ryzyko przyjęte i odnotowane; FR zostaje
  > nice-to-have, kontrakt mappera (Forward: tech-stack) ma to ryzyko ograniczyć, nie wyeliminować.
- FR-013: Aplikacja webowa pokazuje wykres giełdowy z nałożonym wykresem trendu wykrytych sygnałów dla wybranej spółki. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "wymaga nowej integracji z danymi giełdowymi + rozpoznania spółki/tickera
  > — to więcej niż samo nałożenie wykresu." Rozwiązanie: ryzyko przyjęte i odnotowane; zostaje nice-to-have,
  > zależność od czwartej integracji (dane giełdowe) jawnie nazwana.
- FR-014: System analizuje trend sygnałów w czasie (przez LLM) i powiadamia użytkownika, gdy dla danej spółki/tematu pojawia się obiecujący trend. Priority: nice-to-have
  > Socrates: Kontrargumenty rozważone: "obiecujący trend to subiektywna ocena LLM bez jasnego kryterium (ryzyko
  > fałszywych alarmów/ciszy)" i "nowy kanał powiadomień to dodatkowa infrastruktura." Rozwiązanie: oba ryzyka
  > przyjęte świadomie — użytkownik ocenia tę funkcję jako wartościową mimo ryzyk i chce się nią zająć po MVP;
  > kryterium "obiecującego trendu" i kanał powiadomień do doprecyzowania przy realizacji.
- FR-015: Użytkownik może filtrować/sortować listę postów po klasyfikacji nadanej przez LLM (np. sentyment, typ tezy — dokładne kryteria z Fazy 5). Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "zależy w pełni od jeszcze nieustalonego schematu." Rozwiązanie: przyjęte
  > do wiadomości — FR zostaje jako placeholder, doprecyzowany po Fazie 5.

## User Stories

### US-01: Użytkownik widzi przetworzone posty z Wykopu w aplikacji webowej

- **Given** skonfigurowany zakres dat/godzin i źródło (Wykop)
- **When** przepływ uruchamia się — automatycznie zgodnie z harmonogramem, albo ręcznie na żądanie jako operacja administratora
- **Then** przetworzone posty trafiają do bazy i są widoczne w aplikacji webowej jako lista z filtrowaniem/sortowaniem oraz podglądem szczegółowym (surowy post obok przetworzonego przez LLM)

#### Acceptance Criteria
- Duplikaty nie pojawiają się dwukrotnie na liście
- Każdy widoczny post ma dostępną wersję surową i przetworzoną przez LLM w widoku szczegółowym
- Brak wyników dla zadanego zakresu pokazuje czytelny stan pusty, nie błąd

## Non-Functional Requirements

- Operator otrzymuje jednoznaczny, widoczny sygnał, gdy zaplanowany dzienny przebieg zbierania/przetwarzania nie powiedzie się lub zakończy częściowym błędem — nie dowiaduje się o tym pośrednio, przez brak nowych danych.
- Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu; warstwa oceny, zapisu i udostępniania danych pozostaje niezmieniona.

## Business Logic

Aplikacja ocenia każdy zebrany post pod kątem tego, czy stanowi wartościowy sygnał inwestycyjny — na podstawie obecności konkretnej tezy z uzasadnieniem, wyraźnego kierunku/siły wydźwięku oraz nowości informacji — i przypisuje go do konkretnej spółki lub tematu.

Wejściem oceny jest treść posta (tytuł, treść, docelowo też komentarze) wraz z metadanymi źródłowymi (data publikacji, źródło, link do oryginału). Wyjściem jest ustandaryzowany wynik oceny per post: czy jest wartościowy, dla jakiej spółki/tematu, jaki kierunek i siłę wydźwięku niesie, oraz krótkie uzasadnienie/streszczenie tezy.

Użytkownik spotyka tę ocenę na liście i w widoku szczegółowym w aplikacji webowej i może przejść przez link do oryginalnego posta, żeby zweryfikować kontekst. W rozszerzeniu poza MVP (FR-013/FR-014) oceny są agregowane w czasie per spółka, nakładane na wykres notowań giełdowych, z alertami przy obiecującym trendzie.

## Non-Goals

- **Personalizacja kryteriów oceny per użytkownik** — jedno wspólne kryterium "wartościowości" dla wszystkich. Przy skali 100x (wielu użytkowników) wymagałoby to customizacji per osoba/portfel, ale to świadomie poza zakresem MVP (patrz sprawdzian skali w Fazie 6).
- **Przetwarzanie komentarzy pod postami** — MVP ocenia tylko posty; komentarze to jawnie odroczone rozszerzenie na przyszłość.
- **Rozbudowane zarządzanie kontami i uprawnieniami** — publiczna rejestracja, samodzielny reset hasła, edycja/deaktywacja kont, zaproszenia mailowe, dodatkowe role, granular permissions, OAuth oraz zarządzanie API keys są poza zakresem MVP. Prosty panel administratora — lista kont z przypisaną rolą i tworzenie nowego konta z rolą `user`/`admin` (FR-021, FR-022) — jest dopuszczalnym nice-to-have, nie pełnym systemem IAM/RBAC.

## Open Questions

1. **Czy `product_type` to "data-pipeline" czy "web-app"?** — Projekt opisany jako pipeline z API + aplikacja webowa jako pełnoprawny sposób konsumpcji, nie tylko wewnętrzne narzędzie. Wybrano `data-pipeline` jako najbliższą wartość; do doprecyzowania przy wyborze stacku. Owner: użytkownik. By: przed `10x-tech-stack-selector`.
2. **Dokładny kształt pól w JSON zwracanym przez ocenę LLM** — ustalone kryteria (teza z uzasadnieniem, kierunek/siła wydźwięku, nowość, spółka/temat) trzeba jeszcze przełożyć na konkretne nazwy pól/typy i walidację schematu. Owner: użytkownik. By: przed implementacją FR-006.
3. **Kanał i próg dla FR-014 (powiadomienia o obiecującym trendzie)** — nie ustalono jeszcze, czym jest "obiecujący trend" liczbowo ani gdzie ma trafiać powiadomienie. Owner: użytkownik. By: przy realizacji FR-014 (poza MVP).
4. **Ostateczny termin** — świadomie zostawiony otwarty na etapie shape'owania (bez sztywnego `hard_deadline`). Owner: użytkownik.

## Forward: tech-stack

Informacyjne, nie wchodzi do PRD — do podjęcia w `10x-tech-stack-selector`:

- Podział na dwa moduły (decyzja architektoniczna użytkownika): Moduł 1 (backend) — orkiestracja przepływu, integracja z LLM, baza danych, REST API. Moduł 2 (frontend) — aplikacja webowa konsumująca REST API modułu 1.
- Preferencje technologiczne zgłoszone przez użytkownika: LangGraph jako orkiestrator przepływu backendu; FastAPI jako framework REST API.
- Zasada graniczna: cała komunikacja aplikacji webowej z backendem przez API — bez bezpośrednich wywołań poza tą granicą (dotyczy też wyzwalania przepływu na żądanie).
- Wskazówka implementacyjna (z usuniętego FR-004): mapper dedykowany per źródło, z jasnym kontraktem — co wchodzi do LLM (ustandaryzowany model wejściowy) i co z niego wychodzi (ustandaryzowany model wyjściowy).

## Forward: technical-roadmap

Informacyjne, nie wchodzi do PRD:

- W przyszłości planowane rozszerzenie pipeline'u o lekkie modele ML/AI, embeddingi i klasyfikatory (poza samą ekstrakcją przez LLM) — stąd zamiar oparcia całego flow o orkiestrator zdolny to ogarnąć bez przepisywania architektury.
