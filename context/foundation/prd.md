---
project: "Szczur Radar"
version: 2
status: draft
created: 2026-09-26
updated: 2026-10-13
context_type: greenfield
product_type: data-pipeline
target_scale:
  users: small
  qps: low
  data_volume: small
timeline_budget:
  mvp_weeks: 10
  hard_deadline: 2026-12-06
  after_hours_only: true
---

## Vision & Problem Statement

Kilkuset postów i komentarzy dziennie na Wykopie (tag #gielda) i wybranych kanałach Reddita dotyczy inwestowania — wśród nich pojawiają się wartościowe sygnały i informacje, ale giną w szumie. Indywidualni inwestorzy śledzący te platformy nie mają czasu ani energii, by ręcznie przeglądać obie na bieżąco, więc regularnie przegapiają to, co warte uwagi, i tracą czas na przewijanie treści bez wartości.

Ogólne narzędzia agregujące (RSS, keyword alerts) nie rozumieją treści na tyle, by ocenić czy sklasyfikować post pod kątem inwestycyjnym, a gotowe komercyjne rozwiązania tego typu są płatne, niszowe lub trudno dostępne. Twórca narzędzia chce mieć pełną kontrolę nad tym, jak dane są zbierane, przetwarzane i oceniane — własny pipeline — i udostępnić go zaproszonym osobom, zamiast trzymać go tylko dla siebie.

## User & Persona

Podstawowa persona: indywidualny inwestor śledzący dyskusje inwestycyjne na Wykopie i Reddicie (twórca narzędzia oraz zaproszone przez niego osoby). Sięga po narzędzie, gdy chce w krótkim czasie zorientować się, czy w ostatnich postach pojawiło się coś wartego uwagi, bez ręcznego przewijania obu platform.

### Secondary persona

Administrator (operator wdrożenia) — zarządza dostępem: tworzy konta dla zaproszonych osób z przypisaną rolą (przez CLI) i przekazuje im link aktywacyjny poza systemem. Sięga po tę funkcję, gdy chce onboardować nową osobę do narzędzia.

## Success Criteria

### Primary
- Codzienny przepływ (uruchamiany automatycznie wg harmonogramu — FR-002) pobiera posty z Wykopu (#gielda) dla zadanego zakresu dat/godzin, mapuje je do wspólnego modelu, odrzuca duplikaty, przetwarza przez LLM według ustalonego schematu i zapisuje wynik do bazy danych.
- Użytkownik widzi przetworzone posty w aplikacji webowej: listę chronologiczną z filtrowaniem po podstawowych polach klasyfikacji LLM (FR-011a) oraz widok szczegółowy pojedynczego posta (wersja surowa obok przetworzonej przez LLM).
- System kont działa end-to-end: admin tworzy konto z przypisaną rolą przez CLI, przekazuje zaproszonemu link aktywacyjny, ten samodzielnie ustawia hasło, loguje się i widzi dane zgodnie ze swoją rolą.
- Admin widzi w dashboardzie historię uruchomień przepływu (kiedy, status, wynik) — widoczne wyłącznie dla roli admin (FR-021).

### Secondary
- Drugie źródło danych — Reddit (wybrane kanały) — dochodzi jako kolejne źródło w tym samym pipeline.
- Wykres giełdowy z nałożonym wykresem trendu wykrytych sygnałów dla konkretnej spółki.
- Dodatkowe sortowanie listy postów po dacie/źródle (FR-008) i po polach klasyfikacji LLM (FR-011b) — dostępne, jeśli te nice-to-have zostaną zrealizowane.
- Ręczne wywołanie przepływu na żądanie z dashboardu (FR-022) — opcjonalne rozszerzenie automatycznego harmonogramu, zależne od realizacji tego nice-to-have.

### Guardrails
- Jakość danych z LLM musi być wysoka — ekstrakcja/klasyfikacja bez halucynacji.
- Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe.
- Niezalogowany/nieuprawniony użytkownik nigdy nie widzi danych ani funkcji spoza swojej roli.

## User Stories

### US-01: Użytkownik widzi przetworzone posty z Wykopu w aplikacji webowej

- **Given** skonfigurowany zakres dat/godzin i źródło (Wykop), oraz zalogowany użytkownik z rolą `user` lub `admin`
- **When** przepływ uruchamia się automatycznie zgodnie z harmonogramem
- **Then** przetworzone posty trafiają do bazy i są widoczne w aplikacji webowej jako lista z filtrowaniem po klasyfikacji LLM oraz podglądem szczegółowym (surowy post obok przetworzonego, z linkiem do oryginału), wraz z etykietą daty ostatniej udanej aktualizacji danych

#### Acceptance Criteria
- Duplikaty (po ID posta) nie pojawiają się dwukrotnie na liście
- Każdy widoczny post ma dostępną wersję surową i przetworzoną w widoku szczegółowym
- Brak wyników dla zadanego zakresu pokazuje czytelny stan pusty, nie błąd
- Etykieta daty aktualizacji zawsze pokazuje datę ostatniego POMYŚLNEGO przebiegu, nigdy nieudanego
- Niezalogowany użytkownik nie widzi żadnej z powyższych danych

### US-02: Administrator onboarduje nowego użytkownika przez CLI

- **Given** wdrożona aplikacja z istniejącym kontem administratora (utworzonym przez FR-026)
- **When** administrator uruchamia komendę CLI tworzenia użytkownika, podając e-mail i rolę
- **Then** CLI tworzy konto i wyświetla jednorazowy, czasowo ograniczony link aktywacyjny, który administrator przekazuje zaproszonej osobie poza systemem

#### Acceptance Criteria
- Link aktywacyjny wygasa po ustalonym czasie (np. 24h)
- Zaproszony użytkownik może użyć linku dokładnie raz, żeby ustawić własne hasło
- Po wygaśnięciu linku bez użycia konto pozostaje bez hasła — logowanie niemożliwe do czasu wygenerowania nowego linku (operator może wygenerować nowy link dla tego samego konta — FR-028)

## Functional Requirements

### Zbieranie i przetwarzanie danych
- FR-001: System pobiera posty z Wykopu (tag #gielda) dla zadanego zakresu dat/godzin. Priority: must-have
  > Socrates: Kontrargument rozważony: "zależność od jednego, zewnętrznego API bez zapasowej opcji — Wykop może
  > ograniczyć/zmienić API w dowolnym momencie." Rozwiązanie: ryzyko przyjęte świadomie — MVP celowo zaczyna od
  > jednego źródła; Reddit jako drugie źródło zaplanowany jako nice-to-have (FR-023), co ogranicza tę zależność.
- FR-002: System uruchamia przepływ automatycznie codziennie o określonej godzinie. Priority: must-have
  > Socrates: Kontrargument rozważony: "automat bez nadzoru może cicho psuć dane, jeśli błędy w deduplikacji/
  > przetwarzaniu się kumulują." Rozwiązanie: ryzyko przyjęte częściowo — wymaga widoczności błędów dla operatora
  > (patrz Non-Functional Requirements); dodatkowo pokryte przez FR-021 (dashboard historii uruchomień).
- FR-003: System odrzuca duplikaty postów po identyfikatorze źródłowym (ten sam post nie jest przetwarzany dwukrotnie). Treściowo podobne posty od różnych autorów NIE są odrzucane — ich powtarzalność jest sygnałem zainteresowania tematem, nie szumem do odfiltrowania. Priority: must-have
  > Socrates: Kontrargument rozważony: "prosta dedup po ID może przepuszczać duplikaty treściowe / odrzucać sam
  > sygnał." Rozwiązanie: doprecyzowane przez użytkownika — dedup celowo ogranicza się do ID posta; powtórki tej
  > samej treści od różnych autorów to zamierzenie, nie błąd.
- FR-004: System przetwarza każdy post przez LLM według ustalonego, ustandaryzowanego schematu wejścia/wyjścia (prompt + JSON output ograniczony do schematu, żeby zminimalizować ryzyko halucynacji) i zapisuje wynik. Schemat definiuje kontrakt `context/foundation/llm-assessment/contract-v2.md` (V2; V1 zastąpiony 2026-10-01 po pilotażach na prawdziwych postach). Priority: must-have
  > Socrates: Kontrargument rozważony: "zawartość schematu jeszcze nieznana — ryzyko pustego przepisania pól /
  > halucynacji." Rozwiązanie: potwierdzone jako wymóg — kontrakt JSON i ograniczenie halucynacji; dokładna
  > zawartość schematu ustalana downstream (patrz Open Questions).
- FR-005: System zapisuje przetworzone posty do bazy danych. Priority: must-have
  > Socrates: Brak kontrargumentu; zostaje jak jest — niezbędne, żeby API miało skąd czerpać dane.

### Udostępnianie danych
- FR-006: System udostępnia przetworzone posty z bazy danych aplikacji webowej przez API, z obsługą paginacji/limitu wyników. Priority: must-have
  > Socrates: Kontrargument rozważony: "brak paginacji/limitu przy rosnącej bazie stanie się nieużywalne."
  > Rozwiązanie: przyjęte — paginacja/limit dodane do FR wprost.

### Aplikacja webowa (konsumpcja danych)
- FR-007: Użytkownik widzi listę przetworzonych postów, domyślnie posortowaną chronologicznie. Priority: must-have
  > Socrates: Kontrargument rozważony: "chronologia może nie być najlepszym porządkiem domyślnym — ważność
  > sygnału mogłaby lepiej decydować o kolejności." Rozwiązanie: chronologia zostaje domyślnym porządkiem w MVP
  > (nie ma jeszcze zdefiniowanej miary "ważności" — zależy od schematu LLM, patrz Open Questions); zmiana
  > porządku przez usera to FR-008/FR-011b.
- FR-008: Użytkownik może sortować listę postów po dacie i źródle. Priority: nice-to-have
  > Socrates: Kontrargument rozważony (przez użytkownika wprost): "lista ma sensowny domyślny porządek
  > (chronologia), więc dodatkowe sortowanie po dacie/źródle nie jest konieczne do podstawowego działania
  > produktu." Rozwiązanie: obniżone do nice-to-have — przy niewielkiej, zapraszanej grupie userów (target_scale:
  > small) chronologiczny porządek wystarcza na start; dodatkowe sortowanie to wygoda, nie konieczność.
- FR-009: Użytkownik widzi widok szczegółowy posta w czytelnej, przetworzonej formie; surowy JSON jest dostępny opcjonalnie (np. pod przełącznikiem "dane dla nerdów"), nie pokazywany domyślnie. Priority: must-have
  > Socrates: Kontrargument rozważony: "surowy JSON bez formatowania to tryb debug, niekoniecznie potrzebny
  > użytkownikowi końcowemu." Rozwiązanie: przyjęte — przetworzona wersja jest domyślna, surowa dostępna
  > opcjonalnie.
- FR-010: Widok posta zawiera link do oryginalnego posta na platformie źródłowej (Wykop/Reddit), umożliwiający przejście do pełnego kontekstu. Priority: must-have
  > Socrates: Kontrargumenty rozważone: "post źródłowy może zostać usunięty/edytowany — link donikąd" oraz
  > "zależność od stabilności URL Wykopu/Reddita." Rozwiązanie: oba ryzyka zaakceptowane — link to dodatek do
  > już zapisanej, przetworzonej treści, nie krytyczna ścieżka; FR zostaje must-have.
- FR-011a: Użytkownik może filtrować posty po podstawowych polach wynikających z oceny LLM: po spółce/temacie (dopasowanie tekstowe do fragmentu, którym post wskazuje cel, oraz rodzaju celu — bez rozpoznawania, o którą konkretnie spółkę chodzi) i po stanowisku autora wobec tego celu (pozytywne / negatywne / neutralne itd.; kontrakt oceny nie ma globalnego sentymentu posta). Priority: must-have
  > Doprecyzowanie (2026-09-27, po przyjęciu kontraktu oceny LLM V1; obowiązuje bez zmian w V2): „sentyment” = stanowisko wobec konkretnego
  > celu; filtr po spółce jest tekstowy — „CDR” i „CD Projekt” mogą nie być w MVP traktowane jako ta sama spółka.
  > Rozpoznawanie spółek jest poza zakresem MVP (patrz Non-Goals).
  > Socrates: Kontrargument rozważony: "dokładny schemat klasyfikacji LLM jeszcze nieustalony." Rozwiązanie:
  > przyjęte jako kwestia kolejności prac (schemat powstaje przy uszczegółowieniu Business Logic, patrz Open
  > Questions), nie powód do rezygnacji z tej funkcji w MVP; FR zostaje must-have.
- FR-011b: Użytkownik może sortować posty po wybranych wartościach wynikających z klasyfikacji LLM. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "zależy w pełni od jeszcze nieustalonego schematu klasyfikacji."
  > Rozwiązanie: przyjęte do wiadomości — FR zostaje nice-to-have, doprecyzowany po ustaleniu schematu.

### Kontrola dostępu i konta
- FR-012: Użytkownik może zalogować się do aplikacji przy użyciu adresu e-mail i hasła. Priority: must-have
  > Socrates: Kontrargument rozważony: "pełny system uwierzytelniania to nietrywialny nakład dołożony do już
  > dużego zakresu." Rozwiązanie: nakład zaakceptowany — logowanie to fundament całego systemu kont; FR zostaje
  > must-have.
- FR-013: Zalogowany użytkownik może ręcznie się wylogować. Priority: must-have
  > Socrates: Kontrargument rozważony: "krótkie, wygasające sesje mogłyby wystarczyć zamiast jawnego przycisku
  > wylogowania." Rozwiązanie pierwotne: logout zdegradowany na rzecz FR-029. Rewizja (przez użytkownika wprost):
  > agresywne auto-wygasanie sesji jest uciążliwe (wylogowuje w trakcie czytania postów), więc ręczny logout
  > wraca do must-have jako prosty, oczekiwany mechanizm, a FR-029 spada do nice-to-have.
- FR-029: Sesja użytkownika wygasa automatycznie po określonym czasie bezczynności (np. 5 minut, dokładna wartość
  do ustalenia) i wymaga ponownego zalogowania; aktywność użytkownika przedłuża sesję. Priority: nice-to-have
  > Socrates: Kontrargument rozważony (przez użytkownika wprost): "krótki timeout jest uciążliwy — wylogowuje w
  > trakcie czytania." Rozwiązanie: obniżone do nice-to-have; podstawą wylogowania w MVP jest FR-013.
- FR-014: Niezalogowany użytkownik nie ma dostępu do chronionych danych ani funkcji aplikacji. Priority: must-have
  > Socrates: Kontrargument rozważony: "jeśli ochrona nie jest konsekwentnie zastosowana wszędzie tam, gdzie
  > potrzebna, może zostać luka bezpieczeństwa." Rozwiązanie: FR zostaje must-have bez zmian; dodatkowo w
  > Non-Functional Requirements trafia mierzalny, testowalny wymóg konsekwentnego wymuszania uwierzytelnienia.
- FR-015: System rozróżnia role user i admin. Operacje administracyjne są dostępne wyłącznie dla roli admin i
  dzielą się na dwie grupy: (a) zawsze dostępne w MVP — dashboard historii uruchomień przepływu (FR-021,
  must-have); (b) dostępne warunkowo, wyłącznie jeśli odpowiedni nice-to-have zostanie zaimplementowany — panel
  kont (FR-016/FR-019) oraz ręczne wywołanie przepływu z dashboardu (FR-022). Priority: must-have
  > Socrates: Kontrargument rozważony: "zbyt ogólne bez listy, które operacje są administracyjne." Rozwiązanie:
  > FR doprecyzowany przez wprost wymienione odwołania do konkretnych FR; zostaje must-have.
- FR-016: Administrator może utworzyć nowe konto użytkownika z przypisaną rolą z poziomu panelu administracyjnego. Priority: nice-to-have
  > Socrates: Kontrargument rozważony (przez użytkownika wprost): "panel użytkowników + tworzenie kont to fajne
  > rozszerzenie, ale nie jest potrzebne do działania MVP, skoro CLI (FR-028) daje ten sam efekt szybciej."
  > Rozwiązanie: obniżone do nice-to-have — czysty UI-wrapper nad już wymaganym operacyjnym tworzeniem konta
  > przez CLI (FR-028) i wspólnym mechanizmem aktywacji przez link (FR-018); panel nie dodaje nowej zdolności
  > systemu, tylko alternatywny interfejs do tego, co CLI już zapewnia.
- FR-017: Po utworzeniu konta z poziomu panelu webowego system automatycznie wysyła e-mail z jednorazowym, czasowo ograniczonym linkiem aktywacyjnym. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: skoro CLI (FR-028) świadomie NIE wysyła maila (operator przekazuje link
  > sam), jedynym konsumentem automatycznej wysyłki zostaje panel webowy — sam nice-to-have. Rozwiązanie:
  > obniżone do nice-to-have; MVP nie wymaga wcale integracji z dostawcą poczty.
- FR-018: Zaproszony użytkownik ustawia własne hasło przy pierwszym logowaniu za pomocą linku aktywacyjnego. Priority: must-have
  > Socrates: Rozważone w kontekście zależności CLI/panelu (patrz FR-028): mechanizm ustawienia hasła przez link
  > jest wspólnym backendem dla obu ścieżek tworzenia konta, więc zostaje must-have niezależnie od tego, która
  > ścieżka (CLI czy panel) zostanie zaimplementowana.
- FR-019: Administrator może otworzyć panel użytkowników i zobaczyć istniejące konta wraz z przypisaną rolą. Priority: nice-to-have
  > Socrates: Ta sama argumentacja co FR-016 — CLI (FR-026/027/028) pokrywa operacyjne minimum bez panelu;
  > lista kont w UI to wygoda, nie konieczność. Rozwiązanie: obniżone do nice-to-have.
- FR-020: Użytkownik może ustawić własną etykietę/nazwę wyświetlaną, niezależną od loginu (e-mail). Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "przy realnie wielu userach sam e-mail jako identyfikator w UI bywa
  > niewygodny — to argument ZA, nie przeciw." Rozwiązanie: mimo to zostaje nice-to-have — samego e-maila w UI
  > wystarczy na start MVP; funkcja własnej etykiety poczeka do rozszerzenia.

### Dashboard operacyjny (admin)
- FR-021: Administrator widzi w dashboardzie historię uruchomień przepływu (data/godzina, status, wynik). Priority: must-have
  > Socrates: Kontrargument rozważony: "to kolejna nowa warstwa UI (dashboard) + trwałe logowanie uruchomień —
  > dodatkowy nakład obok już dużego zakresu." Rozwiązanie (przez użytkownika wprost): przy automatycznym
  > codziennym pipeline sensownie jest móc zweryfikować, czy ostatnie uruchomienia się udały — zostaje must-have.
- FR-022: Administrator może ręcznie wywołać nowy przepływ na żądanie z poziomu dashboardu; wywołanie jest asynchroniczne z osobnym sposobem sprawdzenia statusu. Priority: nice-to-have
  > Socrates: Kontrargument rozważony (przez użytkownika wprost): "automatyczny harmonogram (FR-002) już
  > istnieje; ręczne odpalenie pipeline'u było wcześniej słusznie nice-to-have." Rozwiązanie: zostaje
  > nice-to-have.
  > Doprecyzowanie (2026-10-13, S-05 `daily-scheduled-run-freshness`): zrealizowane w S-02
  > (`first-assessed-wykop-feed`). Admin uruchamia przebieg w `/admin` (`POST /api/admin/pipeline-runs`,
  > odpowiedź 202, przebieg działa w tle), a jego status widzi na liście przebiegów w `/admin`. Pipeline działa
  > w procesie API, więc API platformy Railway nie jest potrzebne. Tej samej ścieżki startu używa codzienny cron
  > (S-05). Zapisy „dostępne, jeśli to nice-to-have zostanie zrealizowane” przy FR-015 i rolach są więc spełnione.

### Administracja operacyjna
- FR-026: Operator może utworzyć pierwsze konto administratora poza interfejsem webowym przy użyciu prostego mechanizmu CLI. Priority: must-have
  > Socrates: FR powstał wprost z pytania użytkownika o lukę bootstrapu — skoro FR-016 (tworzenie kont przez
  > panel) jest nice-to-have, coś musi dać pierwsze konto niezależnie od web UI. Brak kontrargumentu do
  > rozważenia ponad to, co już rozstrzygnięto; zostaje must-have.
- FR-027: Operator może odzyskać dostęp administracyjny poza interfejsem webowym przy użyciu CLI, co najmniej przez zresetowanie poświadczeń istniejącego konta administratora lub równoważny bezpieczny mechanizm recovery. Priority: must-have
  > Socrates: Analogicznie do FR-026 — bez tego, utrata dostępu jedynego admina blokowałaby cały system bez
  > awaryjnej ścieżki odzyskania. Zostaje must-have.
- FR-028: Operator może za pomocą CLI utworzyć nowe konto użytkownika z przypisaną rolą; CLI generuje i wyświetla jednorazowy, czasowo ograniczony link aktywacyjny (bez automatycznej wysyłki maila) — operator przekazuje go zaproszonej osobie dowolnym kanałem poza systemem. Operator może również za pomocą CLI wygenerować nowy jednorazowy, czasowo ograniczony link aktywacyjny dla istniejącego, jeszcze nieaktywowanego konta (np. gdy poprzedni link wygasł przed użyciem), unieważniając poprzedni link. Priority: must-have
  > Socrates: Kontrargument rozważony i skorygowany w trakcie dyskusji: pierwotna wersja zakładała, że CLI samo
  > wysyła e-mail z zaproszeniem, co dokładałoby zależność od dostawcy poczty do must-have. Rozwiązanie: CLI
  > generuje i wypisuje link aktywacyjny bez wysyłki maila — operator przekazuje go poza systemem; usuwa to
  > zależność od poczty elektronicznej z MVP i unika ryzyka pojedynczego użytkownika bez potrzeby budowania
  > panelu webowego.
  > Doprecyzowanie (2026-10-12, S-07 `cli-user-onboarding`; dotyczy FR-027, FR-028 i US-02): operator może przez
  > CLI zresetować dowolne konto, także aktywne i konto admina, nie tylko jeszcze nieaktywowane (decyzja
  > użytkownika). Reset kasuje hasło, usuwa wszystkie sesje konta i wydaje nowy link, unieważniając poprzedni;
  > ta sama komenda daje recovery admina z FR-027. Nie jest to samodzielny reset hasła z `## Non-Goals` — robi go
  > wyłącznie operator z dostępem do CLI. Link jest ważny domyślnie 12 h (parametr wdrożenia, od 1 min do 7 dni)
  > i działa raz.

### Świeżość danych
- FR-030: Użytkownik widzi datę/godzinę ostatniej udanej aktualizacji danych (np. etykieta nad listą postów:
  "Dane zaktualizowane: [data/godzina]"); wyświetlana jest wyłącznie data ostatniego pomyślnego przebiegu,
  nigdy przebiegu zakończonego błędem. Priority: must-have
  > Socrates: Kontrargument rozważony: "sama data bez statusu myli — jeśli ostatni przebieg się nie udał, user
  > widzi starą datę bez wyjaśnienia dlaczego." Rozwiązanie (przez użytkownika wprost): pokazywać zawsze datę
  > ostatniego POMYŚLNEGO przebiegu, nigdy nieudanego — to naturalnie eliminuje mylące komunikaty bez potrzeby
  > dodatkowego statusu w widoku usera (status szczegółowy zostaje w dashboardzie admina, FR-021).
  > Doprecyzowanie (2026-10-12, S-05 `daily-scheduled-run-freshness`): udana aktualizacja to przebieg zakresu dat (RANGE), zaplanowany albo ręczny, zakończony statusem `succeeded` albo `partial`. `partial` się liczy, bo przebieg pobrał dane, a jego nieudane posty ponawia kolejny przebieg. „Przebieg zakończony błędem” to `failed` albo `interrupted`: taki przebieg nie zmienia daty. Przebiegi ponowienia (RETRY) i wybranych postów (POSTS) nie pobierają nowych danych, więc się nie liczą. Pokazywany czas to koniec przebiegu (`finished_at`) w strefie warszawskiej. Spośród udanych przebiegów liczy się ten, którego zakres sięga najdalej (najpóźniejszy koniec zakresu), a z nich ten zakończony najpóźniej: ręczne przeliczenie starszych dni przez admina nie przesuwa daty, a przebieg za te same najnowsze dni ją przesuwa (przegląd implementacji S-05, F4, 2026-10-13). Bez żadnej udanej aktualizacji etykieta mówi „Dane nie były jeszcze aktualizowane.”; gdy odczytu nie da się wykonać, etykiety nie ma, a lista działa. Trasa `GET /api/freshness` jest dostępna dla każdej zalogowanej roli i nie ujawnia szczegółów przebiegów.
  > Doprecyzowanie (2026-10-18, S-08 `empty-run-signal`): przebieg RANGE, w którym źródło nie dało ani jednego posta do zapisu (żadnego elementu w zakresie albo same elementy, z których nie powstał post), kończy się `failed`, więc daty nie przesuwa: etykieta dalej pokazuje poprzedni udany przebieg. Przebieg z pominiętymi elementami (`skipped` > 0) przy dobrych postach kończy się jak dotąd (`succeeded` albo `partial`) i datę przesuwa. Reguła obejmuje też przebiegi zakresu uruchomione przez admina: zakres bez postów (np. dzisiejszy dzień tuż po północy) kończy się `failed` i powiadamia adminów. Szczegóły: `context/deployment/deploy-plan.md`, sekcja S-08.

### Eksport danych
- FR-031: Użytkownik może wyeksportować aktualnie widoczną (przefiltrowaną) listę przetworzonych postów do pliku
  CSV lub JSON. Priority: nice-to-have
  > Socrates: Kontrargument rozważony w trakcie dyskusji o retencji: pełny, niefiltrowany eksport całej bazy
  > byłby droższy i mniej przewidywalny (rozmiar, czas). Rozwiązanie: zakres zawężony do aktualnie widocznej,
  > przefiltrowanej listy — tańsze i wystarczające do potrzeby "łatwego odczytu danych offline"; priorytet
  > nice-to-have, nie blokuje głównego przepływu MVP.

### Rozszerzenia (poza MVP)
- FR-023: System pobiera posty z wybranych kanałów Reddita jako drugie źródło danych. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "Reddit ma inny format/ograniczenia niż Wykop — dodanie źródła może nie
  > być tak tanie, jak sugeruje wspólny kontrakt mapowania." Rozwiązanie: ryzyko przyjęte i odnotowane; FR
  > zostaje nice-to-have.
- FR-024: Aplikacja webowa pokazuje wykres giełdowy z nałożonym wykresem trendu wykrytych sygnałów dla wybranej spółki. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "wymaga nowej integracji z danymi giełdowymi + rozpoznania spółki/tickera
  > — to więcej niż samo nałożenie wykresu." Rozwiązanie: ryzyko przyjęte i odnotowane; zostaje nice-to-have,
  > zależność od integracji danych giełdowych jawnie nazwana.
- FR-025: System analizuje trend sygnałów w czasie (przez LLM) i powiadamia użytkownika, gdy dla danej spółki/tematu pojawia się obiecujący trend. Priority: nice-to-have
  > Socrates: Kontrargumenty rozważone: "obiecujący trend to subiektywna ocena LLM bez jasnego kryterium (ryzyko
  > fałszywych alarmów/ciszy)" i "nowy kanał powiadomień to dodatkowa infrastruktura." Rozwiązanie: oba ryzyka
  > przyjęte świadomie — funkcja wartościowa mimo ryzyk; kryterium i kanał powiadomień do doprecyzowania przy
  > realizacji (poza MVP).
- FR-032: Użytkownik może z widoku szczegółowego posta wybrać akcję „Ask szczur” i zadać LLM pytanie o dany sygnał (np. dlaczego został oceniony w taki, a nie inny sposób). Pytanie dotyczy wyłącznie wybranego posta, a odpowiedź opiera się na treści tego posta i jego zapisanej ocenie. Obowiązuje limit pytań na użytkownika. Pytanie i odpowiedź przechodzą wyłącznie przez API aplikacji, nigdy bezpośrednio z przeglądarki do LLM. Priority: nice-to-have
  > Socrates: Kontrargumenty rozważone: "koszt LLM rośnie bez limitu, wbrew guardrailowi o minimalizacji kosztów",
  > "funkcja może zostać nadużyta jako ogólny chatbot na koszt właściciela" oraz "LLM może dopowiedzieć
  > uzasadnienie niezgodne z faktyczną oceną sygnału". Rozwiązanie: wszystkie trzy przyjęte — FR zawężony do
  > pytań o wybrany post, z odpowiedzią opartą na jego treści i zapisanej ocenie, oraz z limitem pytań na
  > użytkownika; zostaje nice-to-have.

## Non-Functional Requirements

- Operator otrzymuje jednoznaczny, widoczny sygnał, gdy zaplanowany dzienny przebieg zbierania/przetwarzania nie powiedzie się lub zakończy częściowym błędem — nie dowiaduje się o tym pośrednio, przez brak nowych danych.
  > Doprecyzowanie (2026-10-12, S-05 `daily-scheduled-run-freshness`): przebieg zaplanowany uruchamia serwis cron `pipeline-trigger`, który czeka na jego wynik. Przy `failed` albo `interrupted` cron kończy się błędem, a Railway wysyła operatorowi powiadomienie (w aplikacji i push). Wymaga to reguły powiadomień „deployment crashed” dla wszystkich środowisk w ustawieniach konta Railway: w teście z 2026-10-12 bez niej nie przyszedł ani e-mail, ani powiadomienie. Przebieg `partial` kończy crona sukcesem; jego nieudane posty widać w `/admin`, a aktywny sygnał dla częściowego błędu (dashboard i ewentualne powiadomienie) to S-06. Szczegóły: `context/deployment/deploy-plan.md`, sekcja S-05.
  > Doprecyzowanie (2026-10-16, S-06 `pipeline-run-history-dashboard`): przebieg `partial`, którego jedyne błędy to posty `failed` z próbami do wykorzystania, nie daje powiadomienia: kolejny przebieg je ponawia, a zwykła noc kończy się właśnie tak (ok. 2% postów `failed`, bez martwych). Przebieg `failed`, `interrupted` oraz `partial` z martwym postem (`dead`) albo z błędem technicznym daje każdemu adminowi powiadomienie w aplikacji: dzwoneczek z liczbą nieprzeczytanych w nagłówku i strona przebiegu `/admin/runs/:id` z powodem. Powiadomień poza aplikacją (push, e-mail) nie ma. Kody wyjścia crona się nie zmieniają, więc powiadomienie Railway działa jak dotąd (przy `failed` i `interrupted`). Przebieg bez postępu przez ustalony próg API samo kończy jako `failed`, co też daje powiadomienie. Szczegóły: `context/deployment/deploy-plan.md`, sekcja S-06.
  > Doprecyzowanie (2026-10-18, S-08 `empty-run-signal`): przebieg RANGE, w którym źródło nie dało ani jednego posta do zapisu (żadnego elementu w zakresie albo same elementy, z których nie powstał post), kończy się `failed` z błędem `NoPostsFetchedException`. Daje to te same sygnały co każdy `failed`: powiadomienie w aplikacji dla każdego admina, kod wyjścia crona 1 i push Railway „deployment crashed”. Zdanie z S-06 „Kody wyjścia crona się nie zmieniają” nie jest więc już pełne: same kody i ich reguła (1 przy `failed` i `interrupted`) zostają, ale pusty przebieg trafia teraz do `failed`, a wcześniej kończył się `succeeded`. Pominięte elementy przy dobrych postach (`skipped` > 0) dają tylko powiadomienie w aplikacji: status przebiegu i kod wyjścia crona się nie zmieniają, a data świeżości przesuwa się jak dotąd. Reguła obejmuje też przebiegi zakresu uruchomione przez admina: zakres bez postów (np. dzisiejszy dzień tuż po północy) kończy się `failed` i powiadamia adminów. Przebiegi RETRY i POSTS działają jak dotąd. Szczegóły: `context/deployment/deploy-plan.md`, sekcja S-08.
  > Doprecyzowanie (2026-10-20, F-04 `out-of-app-alert-channel`): sygnał poza aplikacją daje usługa healthchecks.io, która alarmuje w Telegramie. Oba crony (`pipeline-trigger`, `db-backup`) meldują start i kod wyjścia z ostatnią linią logu. Nocny przebieg albo backup, który się nie powiódł, daje wiadomość od razu, z ostatnią linią logu; taki, który nie wystartował albo nie skończył się na czas, daje ją po karencji (ok. 03:45 UTC dla przebiegu, ok. 04:15 UTC dla backupu). Reguła Railway „deployment crashed” zostaje jako drugi sygnał crasha. Przebieg `partial` kończy crona kodem 0, więc `partial` z martwym postem albo błędem technicznym nadal daje tylko powiadomienie w aplikacji (S-06), bez wiadomości w Telegramie. API ma nadawcę zdarzeń dla operatora, a komenda operatora `send-test-alert` sprawdza kanał od API do Telegrama; konkretne błędy API wymagające reakcji podłączy S-18, a dni bez pobranych danych pokaże S-13. Nic nie odpytuje produkcji, API nie dostaje nowego zegara, koszt usług to $0; awaria samego healthchecks.io albo Telegrama jest cicha. Szczegóły: `context/deployment/deploy-plan.md`, sekcja F-04.
  > Doprecyzowanie (2026-10-20, S-09 `attempt-accounting`): awaria dostawcy LLM po ponowieniach (5xx, przekroczenie czasu, brak połączenia albo 429 do wyczerpania prób) nie zużywa próby oceny posta. Próbę zużywa tylko odmowa dostawcy albo zła odpowiedź modelu. Trzy takie awarie z rzędu kończą przebieg `failed` z błędem „the LLM provider is unavailable: 3 calls in a row failed after their retries …”. Daje to te same sygnały co każdy `failed`: powiadomienie w aplikacji dla każdego admina, kod wyjścia crona 1 i push Railway „deployment crashed”. Posty wracają bez prób i bez błędów technicznych, a podejmie je RETRY przed kolejnym przebiegiem zakresu (następnej nocy albo admina). Pojedyncza awaria, po której przebieg idzie dalej, jest błędem technicznym posta: przebieg kończy się `partial` i daje powiadomienie, jak każdy błąd techniczny z doprecyzowania S-06. Trzy błędy techniczne posta z rzędu (pojedyncze awarie albo błędy naszego kodu) wstrzymują post: RETRY go nie bierze, więc nie jest już ponawiany automatycznie. Wstrzymany post jest na liście „Nieudane posty” w `/admin` z liczbą błędów technicznych i ostatnim błędem, a admin może go ponowić („Ponów zaznaczone”). Każdy zapisany wynik oceny (udany albo nieudany) zeruje licznik. Szczegóły: `context/deployment/deploy-plan.md`, sekcja S-09.
  > Doprecyzowanie (2026-10-25, S-18 `error-visibility`): API zgłasza operatorowi zdarzenia kanałem F-04 (check `api-events`, wiadomość w Telegramie). Przebieg, który daje adminom powiadomienie w aplikacji (S-06), daje teraz też wiadomość `RUN_NEEDS_ATTENTION` z id przebiegu, jego statusem, licznikami (martwe, błędy techniczne, pominięte), błędem przebiegu i odnośnikiem `/admin/runs/<id>`. Dotyczy to przebiegu `partial` z martwym postem albo błędem technicznym, przebiegu z pominiętymi elementami i przebiegu admina zakończonego `failed` albo `interrupted`. Zdanie z F-04 „`partial` z martwym postem albo błędem technicznym nadal daje tylko powiadomienie w aplikacji (S-06), bez wiadomości w Telegramie” nie jest więc już prawdziwe. Przebieg crona zakończony `failed` albo `interrupted` zgłasza tylko cron (kod wyjścia 1 i jego check), bez drugiej wiadomości z API. Gdy cron przestanie czekać przed końcem przebiegu (np. po 90 min), jego kod 1 mówi tylko, że przebieg się nie skończył, a późniejszy wynik `failed` albo `interrupted` widać w `/admin` i w dzwonku, nie w Telegramie. Wiadomość dają też awarie samych ścieżek sygnału: nieudane powiadomienie adminów, odzyskiwanie przebiegów przy starcie, zamknięcie przebiegu, sprawdzenie watchdoga, sprzątanie osieroconych przebiegów i zapis liczników przebiegu. Każde zdarzenie to w Telegramie dwie wiadomości: „down” z opisem i zaraz po niej „up”, które tylko uzbraja check na następne zdarzenie i nie znaczy, że problem minął. To samo zdarzenie dla tego samego przebiegu idzie najwyżej raz na 10 min, a wszystkie razem najwyżej 2 na minutę; zdarzenie, które czeka na wysyłkę w chwili zabicia procesu, ginie (powiadomienie w aplikacji i log zostają). Treść nie zawiera sekretów. Bez nowych zmiennych i migracji; przyczyna 503 z `/api/ready` trafia tylko do logu API. Szczegóły: `context/deployment/deploy-plan.md`, sekcja S-18.
- Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu; warstwa oceny, zapisu i udostępniania danych pozostaje niezmieniona.
  > Doprecyzowanie (2026-10-07, S-02 `first-assessed-wykop-feed`, przegląd fazy 2): rejestracja źródła — jego stała (`sources/Source`) i dopisanie jej do reguły `CHECK` kolumny `source` jedną migracją — jest częścią dodania źródła. Model danych, encje i zapytania warstwy zapisu się nie zmieniają: typ posta to typ wspólnego modelu (`post` / `link` / `comment`), na który źródło mapuje własne typy.
- Każda chroniona funkcja lub zasób konsekwentnie wymaga uwierzytelnienia — żadna ścieżka dostępu do danych lub funkcji nie pozostaje przypadkowo niezabezpieczona (weryfikowalne przez kompletny zestaw testów autoryzacji).
- Użytkownik nie ma wglądu w dane kont innych userów (e-maile, aktywność) — widoczne wspólnie są wyłącznie przetworzone posty; dane kontowe ograniczone do własnego konta.
- Aplikacja webowa nie ma sztywnego wymogu czasu odpowiedzi w MVP — kilkusekundowe ładowanie przy normalnym obciążeniu jest akceptowalne.
- Dane (posty, konta) są przechowywane bezterminowo w MVP — brak automatycznej retencji/czyszczenia. Przed utratą danych chroni codzienna kopia logiczna bazy, którą robi osobny serwis infrastruktury, a nie logika aplikacji. Każda kopia jest sprawdzana pełnym odtworzeniem, a jedna kopia leży też poza platformą hostingową. Utracić można najwyżej dane z ostatniej doby.
  > Zmiana (2026-10-11, F-01 `production-data-gate`): wcześniej „bezpieczeństwo przed utratą danych jest odpowiedzialnością platformy hostingowej”. Plan Railway Hobby nie ma jednak kopii wolumenu ani PITR (to funkcje planu Pro), więc kopie robi serwis cron `db-backup` (`tools/db-backup/`): `pg_dump` sprawdzany odtworzeniem, bucket Railway (14 dziennych + 12 miesięcznych kopii) i NAS operatora. Szczegóły i runbook odtworzenia: `context/deployment/deploy-plan.md`, sekcja F-01.
- Oryginalny payload źródłowy każdego zebranego posta pozostaje dostępny bez utraty informacji, ale jest utrzymywany niezależnie od znormalizowanych danych używanych przez aplikację. Sposób jego przechowywania musi pozwalać na niezależną, bezstratną kompresję lub przeniesienie surowych danych bez zmiany zachowania aplikacji ani utraty dostępu do widoku surowego posta.

## Business Logic

Aplikacja opisuje znaczenie każdego zebranego posta tak, by użytkownik mógł szybko ocenić, czy jest wartościowym sygnałem inwestycyjnym: czy treść dotyczy inwestowania, jakich spółek lub tematów dotyczy, jakie stanowisko autor zajmuje wobec każdej z nich (i czy je osłabia, np. „może”), oraz jakie tezy, opinie, fakty, pytania i działania przedstawia — każde poparte dosłownym cytatem z posta.

Wejściem oceny jest wyłącznie treść posta (tytuł, treść; docelowo też komentarze z kontekstem rodzica). Metadane źródłowe (źródło, autor, link, zaangażowanie) nie trafiają do LLM — backend przechowuje je osobno. Wyjściem jest ustandaryzowany wynik oceny per post zgodny z kontraktem `context/foundation/llm-assessment/contract-v2.md` (V2): trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności. Kontrakt V2 nie zawiera grafu powiązań argumentów (zgłoszona rozbieżność z wcześniejszym opisem — rozstrzygnięta w zmianie `assessment-contract-harness`, 2026-10-01); wpisy bez treści (poza tagami, wzmiankami i linkami brak tekstu, np. samo zdjęcie z tagiem) dostają wynik bez wywołania modelu. LLM nie wydaje decyzji „wartościowy / niewartościowy” ani nie ocenia nowości — w MVP tę ocenę robi użytkownik na podstawie opisanej struktury; automatyczna decyzja o sygnale jest odroczona (patrz Non-Goals).

Użytkownik spotyka tę ocenę na liście i w widoku szczegółowym w aplikacji webowej (z możliwością filtrowania po tych polach — FR-011a/b) i może przejść przez link do oryginalnego posta, żeby zweryfikować kontekst. W rozszerzeniu poza MVP (FR-024/FR-025) oceny są agregowane w czasie per spółka, nakładane na wykres notowań giełdowych, z alertami przy obiecującym trendzie.

## Access Control

Dostęp do aplikacji wymaga uwierzytelnienia — aplikacja jest publicznie osiągalna w internecie. Logowanie odbywa się przez adres e-mail i hasło. E-mail jest stałym identyfikatorem logowania. Opcjonalnie, jeśli nice-to-have FR-020 zostanie zrealizowany, użytkownik będzie mógł dodatkowo ustawić widoczną etykietę/nazwę wyświetlaną, niezależną od loginu — to nie jest gwarantowana funkcja MVP.

Brak publicznej rejestracji. Konta tworzy wyłącznie administrator, przypisując rolę (`user` lub `admin`) — w MVP przez CLI (patrz `## Functional Requirements` → Administracja operacyjna), opcjonalnie w przyszłości też z poziomu panelu webowego. Tworzenie konta generuje jednorazowy, czasowo ograniczony link aktywacyjny; MVP nie wysyła go automatycznie mailem — administrator przekazuje go zaproszonej osobie dowolnym kanałem poza systemem. Zaproszony klika link i samodzielnie ustawia hasło przy pierwszym logowaniu. Hasło nigdy nie jest przesyłane jawnym tekstem.

Role:
- `user` — przegląda listę przetworzonych postów i filtruje po podstawowych polach klasyfikacji LLM (FR-011a,
  must-have); otwiera widok szczegółowy. Dodatkowe sortowanie (FR-008 — po dacie/źródle, FR-011b — po polach
  klasyfikacji) oraz ustawienie własnej etykiety/nazwy wyświetlanej (FR-020) są opcjonalne — dostępne, jeśli te
  nice-to-have zostaną zrealizowane, nie gwarantowane w MVP.
- `admin` — wszystko co `user`, plus: tworzenie kont z przypisaną rolą przez CLI (FR-026/FR-027/FR-028) oraz
  dashboard z historią uruchomień przepływu (FR-021, must-have). Ręczne wywołanie nowego przepływu z dashboardu
  (FR-022) jest opcjonalne — dostępne, jeśli to nice-to-have zostanie zrealizowane, nie gwarantowane w MVP.

Niezalogowany użytkownik nie ma dostępu do żadnych chronionych danych ani funkcji — trafia na ekran logowania.

> Doprecyzowanie (2026-10-12, S-05 `daily-scheduled-run-freshness`): poza kontami osób istnieje konto systemowe `scheduler@system.invalid`, utworzone migracją, a nie przez CLI. Jest zleceniodawcą przebiegu dziennego. Ma `login_enabled = false` i nie ma hasła (wymusza to ograniczenie bazy), więc nigdy się nie zaloguje, a komendy CLI kont (S-07) muszą je odrzucać. Cron uruchamia przebieg wewnętrzną trasą `/internal/**`, osiągalną tylko z sieci prywatnej platformy i tylko z tokenem; trasa nie przyjmuje sesji, a token nie działa na trasach aplikacji (`/api/**`).

Poza zakresem MVP (przyszłość): drugi czynnik uwierzytelniania, samodzielny reset hasła, logowanie przez zewnętrznego dostawcę tożsamości, programistyczne klucze dostępu.

## Non-Goals

- **Automatyczna wysyłka e-maili jako wymagany element MVP** — ścieżka must-have (CLI, FR-028) nie wymaga integracji z dostawcą poczty; link aktywacyjny generuje CLI, a operator przekazuje go zaproszonej osobie poza systemem. Automatyczna wysyłka maila z panelu webowego (FR-017) pozostaje możliwym, nice-to-have rozszerzeniem — nie jest tu wykluczona całkowicie, tylko nie jest wymagana do działania MVP.
- **Drugi czynnik uwierzytelniania oraz logowanie przez zewnętrznego dostawcę tożsamości** — login e-mailem i hasłem wystarcza na start MVP.
- **Przetwarzanie komentarzy pod postami** — MVP ocenia wyłącznie posty; komentarze to jawnie odroczone rozszerzenie na przyszłość.
- **Logika archiwizacji/backupu danych w kodzie aplikacji** — kopię bazy robi osobny serwis infrastruktury (serwis cron `db-backup`, `tools/db-backup/`), a nie API ani pipeline (od F-01, 2026-10-11; wcześniej: „odpowiedzialność platformy hostingowej”).
- **Wysoka dostępność / multi-region** — pojedyncza instancja na jednej platformie wystarcza dla małej, zapraszanej grupy userów.
- **Dodatkowe źródła danych poza Wykop/Reddit** (np. Twitter/X, fora branżowe) — poza zakresem MVP i jego zaplanowanego rozszerzenia.
- **Rozpoznawanie spółek (entity resolution) oraz automatyczna decyzja „wartościowy sygnał”** — MVP nie ustala, o którą konkretnie spółkę chodzi (ticker/ISIN), ani nie wylicza nowości, trendu czy końcowej decyzji o sygnale; kontrakt oceny LLM (V2) umieszcza te kroki poza oceną jako osobne, dalsze etapy. Są warunkiem przyszłych FR-024/FR-025.
- **Personalizacja samej logiki oceny LLM** ("co znaczy wartościowy sygnał") per user — kryterium wartościowości pozostaje wspólne dla wszystkich; personalizowalne jest wyłącznie filtrowanie widoku na już obliczonej, wspólnej klasyfikacji (FR-011a/b), nie sama reguła oceny.
- **Rozbudowany system zarządzania tożsamością poza modelem `user`/`admin`** — dodatkowe role, granularne uprawnienia, rozbudowana edycja/deaktywacja kont, publiczna rejestracja oraz samodzielny reset hasła przez użytkownika i zarządzanie kluczami dostępu pozostają poza zakresem MVP. (Nie wyklucza to FR-016/FR-017/FR-019 — panel webowy tworzenia kont i automatyczna wysyłka maila zaproszenia pozostają nice-to-have zgodnie z aktualnymi FR.)

## Open Questions

1. **Czy `product_type` to "data-pipeline" czy "web-app"?** — Projekt to dwa moduły: pipeline+API jako rdzeń, pełnoprawna aplikacja webowa (logowanie, dashboard, lista/filtrowanie) jako warstwa konsumpcji. Wybrano `data-pipeline` jako najbliższą pojedynczą wartość; do doprecyzowania przy `/10x-tech-stack-selector`. Owner: użytkownik.
2. **[ROZSTRZYGNIĘTE 2026-09-27 — kontrakt V1; zastąpiony 2026-10-01 kontraktem `context/foundation/llm-assessment/contract-v2.md` V2 (rdzeń: trafność, cele ze stanowiskiem autora, tezy z cytatem, flagi niepewności; bez grafu argumentów)]** **Dokładny kształt pól w JSON zwracanym przez ocenę LLM** — ustalone kryteria (teza z uzasadnieniem, kierunek/siła wydźwięku, nowość, spółka/temat) trzeba jeszcze przełożyć na konkretne nazwy pól/typy i walidację schematu; wpływa też na dokładny zakres FR-011a/b. Owner: użytkownik. By: przed implementacją FR-004.
3. **Kanał i próg dla FR-025 (powiadomienia o obiecującym trendzie)** — nie ustalono jeszcze, czym jest "obiecujący trend" liczbowo ani gdzie ma trafiać powiadomienie. Owner: użytkownik. By: przy realizacji FR-025 (poza MVP).
4. **[ROZSTRZYGNIĘTE 2026-10-11 — F-01 `production-data-gate`: Railway Hobby nie ma kopii platformy (kopie wolumenu i PITR to plan Pro). Kopię robi codzienny `pg_dump` z serwisu cron, sprawdzany odtworzeniem, do bucketu Railway (14 dziennych + 12 miesięcznych) i na NAS operatora. Odtworzenie przećwiczone w PR env (`context/deployment/deploy-plan.md`, sekcja F-01)]** **Backup/retencja na docelowej platformie hostingowej** — sprawdzić realne możliwości managed backupu bazy danych na wybranej platformie przy `/10x-infra-research`, żeby "bezterminowe przechowywanie" (patrz Non-Functional Requirements) nie okazało się bez żadnej siatki bezpieczeństwa. Owner: użytkownik. By: przed `/10x-infra-research`.
