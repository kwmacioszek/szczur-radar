---
project: "Szczur Radar"
context_type: greenfield
created: 2026-09-26
updated: 2026-09-26
product_type: data-pipeline
target_scale:
  users: small
  qps: low
  data_volume: small
timeline_budget:
  mvp_weeks: 10
  hard_deadline: 2026-12-06
  after_hours_only: true
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  gray_areas_resolved:
    - topic: "primary persona scope"
      decision: "multi-user od startu — admin (operator wdrożenia) zaprasza realne, konkretne osoby jako pełnoprawnych userów; to nie jest formalność jednej persony jak w poprzedniej wersji"
    - topic: "personalizacja kryteriów oceny sygnału"
      decision: "rozwiązane: włączone jako proste filtry predykatowe na już istniejących polach klasyfikacji LLM (spółka/temat, sentyment) — FR-011a must-have (filtrowanie), FR-011b nice-to-have (sortowanie po tych polach); nie pełny silnik skryptów/zapytań"
    - topic: "auth mechanism"
      decision: "login przez e-mail + hasło; brak rejestracji publicznej; konta tworzy admin — docelowo przez CLI (must-have, FR-026/027/028), panel webowy opcjonalny (nice-to-have); aktywacja przez jednorazowy link do samodzielnego ustawienia hasła (nie tymczasowe hasło w treści wiadomości), link generowany przez CLI i przekazywany poza systemem — bez automatycznej wysyłki maila w MVP"
    - topic: "login identifier"
      decision: "e-mail jest identyfikatorem logowania (niezmienny); user może dodatkowo ustawić widoczną etykietę/nazwę wyświetlaną (display name), oddzielną od loginu"
    - topic: "timeline"
      decision: "deadline 06.12.2026 (ok. 10 tygodni od startu sesji), dostępność 3×3-5h w tygodniu (pon/śr/czw) + 2×8h+ w weekend; użytkownik świadomie wybrał mvp_weeks=10 (cały dostępny czas), nie skracanie zakresu"
    - topic: "admin dashboard operacyjny"
      decision: "admin widzi historię uruchomień przepływu (kiedy/status/wynik) — must-have (FR-021); ręczne wywołanie z dashboardu (FR-022) zdegradowane z powrotem do nice-to-have — harmonogram automatyczny już to pokrywa"
    - topic: "bootstrap i recovery administratora"
      decision: "nowa sekcja Administracja operacyjna (CLI) — FR-026 (bootstrap pierwszego admina) i FR-027 (recovery dostępu) must-have, niezależne od web UI; CLI to narzędzie awaryjne/operatorskie, celowo NIE rozszerzane o pełny CRUD/listing/permissions userów"
    - topic: "onboarding dodatkowych userów bez web panelu"
      decision: "FR-028 (CLI: utworzenie usera + wygenerowanie linku aktywacyjnego) must-have — unika ryzyka single-user MVP bez potrzeby budowania panelu webowego. Skorygowane: CLI NIE wysyła maila — generuje i wypisuje link aktywacyjny (operator przekazuje go dowolnym kanałem poza systemem). Konsekwencja: FR-018 (user ustawia hasło przez link) zostaje must-have (wspólny mechanizm dla CLI i, docelowo, panelu), ale FR-017 (automatyczna wysyłka maila) wraca do nice-to-have — MVP nie wymaga wcale integracji z dostawcą e-mail. FR-016 (formularz w panelu) i FR-019 (lista kont w panelu) zostają nice-to-have"
    - topic: "widoczność świeżości danych"
      decision: "dodane FR-030 (must-have): etykieta z datą/godziną ostatniej UDANEJ aktualizacji danych, widoczna dla wszystkich ról; nigdy nie pokazuje daty nieudanego przebiegu — eliminuje mylące komunikaty bez potrzeby dodatkowego statusu w widoku usera"
    - topic: "retencja danych i eksport"
      decision: "retencja: bezterminowa, brak custom logiki archiwizacji w MVP — backup to odpowiedzialność platformy hostingowej (Railway), nie aplikacji (Forward: infra). Rozdzielone od realnej potrzeby: dodane FR-031 (nice-to-have) — eksport aktualnie widocznej/przefiltrowanej listy postów do CSV/JSON, do czytania offline"
    - topic: "oddzielenie RAW payloadów od danych operacyjnych"
      decision: "surowy payload źródłowy pozostaje bezterminowo dostępny, ale jest oddzielony od znormalizowanych danych używanych do filtrowania, sortowania i oceny. Granica storage musi umożliwiać bezstratną kompresję oraz późniejsze przeniesienie RAW niezależnie od modelu domenowego i bez utraty widoku surowych danych. Nie przesądza to jeszcze konkretnego backendu storage ani algorytmu kompresji"
    - topic: "prywatność kont i wydajność"
      decision: "user nie widzi danych kont innych userów (tylko wspólne przetworzone posty są dzielone); brak sztywnego wymogu czasu odpowiedzi w MVP (kilka sekund akceptowalne)"
    - topic: "product_type"
      decision: "data-pipeline (najbliższa pojedyncza wartość) — projekt to dwa moduły: pipeline+API jako rdzeń, aplikacja webowa jako warstwa konsumpcji; niuans do Open Questions, doprecyzowanie przy /10x-tech-stack-selector"
    - topic: "target_scale"
      decision: "users: small — twórca + garstka zaproszonych znajomych, nie szersza publika"
    - topic: "Ask szczur (dodane po zamknięciu sesji)"
      decision: "FR-032 (nice-to-have): pytanie do LLM o wybrany sygnał z widoku posta, wyłącznie przez API aplikacji; zawężone do wybranego posta, odpowiedź oparta na jego treści i zapisanej ocenie, limit pytań na użytkownika"
    - topic: "logout vs auto-wygasanie sesji (rewizja)"
      decision: "FR-013 (ręczny logout) z powrotem must-have; FR-029 (auto-wygasanie sesji) obniżone do nice-to-have — krótki timeout uciążliwy w trakcie czytania postów"
  frs_drafted: 33
  quality_check_status: accepted
---

# Shape Notes

## Seed idea (verbatim, z poprzedniej sesji — reużyte jako punkt wyjścia)

Chce zbudowac aplikacje, ktora bedzie pobierac np. z wykopu tag #gielda lub z kanalow reddit posty ludzi dotyczace inwestowania. Kazdy post bylby sciagany jako surowy json, standaryzowany do wspolnego modelu, obrabiany przez LLM wedlug schematu i nastepnie zapisywany do bazy danych gdzie bylby wystawiony prosty fastapi dla aplikacji webowej.

Zaakceptowana decyzja architektoniczna (przeniesiona z poprzedniej sesji, potwierdzona ponownie w tej): podział na
dwa moduły — Moduł 1 (backend): pipeline zbierania/przetwarzania danych + integracja z LLM + baza danych + REST
API. Moduł 2 (frontend): aplikacja webowa korzystająca z REST API modułu 1. Cała komunikacja frontend↔backend
przechodzi przez API — bez bezpośrednich wywołań poza tą granicą. Szczegóły stackowe (informacyjne, nie PRD) —
patrz `## Forward: tech-stack`.

### Potwierdzona wykonalność techniczna (feasibility check)

Kontekst techniczny — nie kod do reużycia, nie rozszerzenie zakresu MVP.

W pierwszym, zarchiwizowanym przebiegu sprawdzono wyłącznie technicznie, że pobranie danych jest wykonalne — z
Wykopu przez otwarte REST API oraz z Reddita przez Arctic Shift. Kod pipeline'u zostanie napisany od nowa w tym
przebiegu. Nie zmienia to priorytetu Reddita w FR — pozostaje nice-to-have (FR-023) zgodnie z aktualną rundą
Sokratejską.

## Zmiany założeń względem poprzedniej sesji (ta sesja)

- Deploy: Railway (nie self-hosted) → aplikacja wystawiona na publiczny adres. (informacyjne dla `/10x-infra-research`, patrz `## Forward: infra`)
- Model użytkowników: multi-user od startu, nie jedna osoba. Admin (operator) zaprasza realne osoby jako userów.
- Brak publicznej rejestracji — w MVP konta tworzy wyłącznie admin przez CLI (nie przez panel webowy).
- CLI generuje jednorazowy, czasowo ograniczony link aktywacyjny. CLI NIE wysyła maila — operator przekazuje link zaproszonej osobie poza systemem.
- Panel webowy zarządzania kontami pozostaje nice-to-have (FR-016/FR-019); automatyczna wysyłka maila z panelu również pozostaje nice-to-have (FR-017).
- Sama logika oceny LLM ("co znaczy wartościowy sygnał") jest wspólna dla wszystkich userów — nie jest personalizowana per user.
- Personalizacja w MVP polega na filtrowaniu widoku po już istniejących polach klasyfikacji LLM — filtrowanie jest must-have (FR-011a); dodatkowe sortowanie po tych samych polach jest nice-to-have (FR-011b).

## Vision & Problem Statement

Kilkuset postów i komentarzy dziennie na Wykopie (tag #gielda) i wybranych kanałach Reddita dotyczy inwestowania — wśród nich pojawiają się wartościowe sygnały i informacje, ale giną w szumie. Indywidualni inwestorzy śledzący te platformy nie mają czasu ani energii, by ręcznie przeglądać obie na bieżąco, więc regularnie przegapiają to, co warte uwagi, i tracą czas na przewijanie treści bez wartości.

Ogólne narzędzia agregujące (RSS, keyword alerts) nie rozumieją treści na tyle, by ocenić czy sklasyfikować post pod kątem inwestycyjnym, a gotowe komercyjne rozwiązania tego typu są płatne, niszowe lub trudno dostępne. Twórca narzędzia chce mieć pełną kontrolę nad tym, jak dane są zbierane, przetwarzane i oceniane — własny pipeline — i udostępnić go zaproszonym osobom, zamiast trzymać go tylko dla siebie.

## User & Persona

Podstawowa persona: indywidualny inwestor śledzący dyskusje inwestycyjne na Wykopie i Reddicie (twórca narzędzia oraz zaproszone przez niego osoby). Sięga po narzędzie, gdy chce w krótkim czasie zorientować się, czy w ostatnich postach pojawiło się coś wartego uwagi, bez ręcznego przewijania obu platform.

### Secondary persona

Administrator (operator wdrożenia) — zarządza dostępem: tworzy konta dla zaproszonych osób z przypisaną rolą (przez CLI) i przekazuje im link aktywacyjny poza systemem. Sięga po tę funkcję, gdy chce onboardować nową osobę do narzędzia.

## Access Control

Dostęp do aplikacji wymaga uwierzytelnienia — aplikacja jest publicznie osiągalna (Railway). Logowanie odbywa się przez adres e-mail i hasło. E-mail jest stałym identyfikatorem logowania. Opcjonalnie, jeśli nice-to-have FR-020 zostanie zrealizowany, użytkownik będzie mógł dodatkowo ustawić widoczną etykietę/nazwę wyświetlaną, niezależną od loginu — to nie jest gwarantowana funkcja MVP.

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

Poza zakresem MVP (przyszłość): drugi czynnik uwierzytelniania (TOTP), samodzielny reset hasła, OAuth, API keys.

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

## Timeline acknowledgment

Acknowledged on 2026-09-26: 10-week MVP (deadline 2026-12-06) requires sustained dedication across ~5 pracujących dni tygodniowo (3×3-5h w tygodniu, 2×8h+ w weekend); user accepted, pełen dostępny czas do deadline'u, bez celowego skracania zakresu.

## Functional Requirements

### Zbieranie i przetwarzanie danych
- FR-001: System pobiera posty z Wykopu (tag #gielda) dla zadanego zakresu dat/godzin. Priority: must-have
  > Socrates: Kontrargument rozważony: "zależność od jednego, zewnętrznego API bez zapasowej opcji — Wykop może
  > ograniczyć/zmienić API w dowolnym momencie." Rozwiązanie: ryzyko przyjęte świadomie — MVP celowo zaczyna od
  > jednego źródła; Reddit jako drugie źródło zaplanowany jako nice-to-have (FR-023), co ogranicza tę zależność.
- FR-002: System uruchamia przepływ automatycznie codziennie o określonej godzinie. Priority: must-have
  > Socrates: Kontrargument rozważony: "automat bez nadzoru może cicho psuć dane, jeśli błędy w deduplikacji/
  > przetwarzaniu się kumulują." Rozwiązanie: ryzyko przyjęte częściowo — wymaga widoczności błędów dla operatora
  > (NFR w Fazie 5); dodatkowo pokryte przez FR-021 (dashboard historii uruchomień).
- FR-003: System odrzuca duplikaty postów po identyfikatorze źródłowym (ten sam post nie jest przetwarzany dwukrotnie). Treściowo podobne posty od różnych autorów NIE są odrzucane — ich powtarzalność jest sygnałem zainteresowania tematem, nie szumem do odfiltrowania. Priority: must-have
  > Socrates: Kontrargument rozważony: "prosta dedup po ID może przepuszczać duplikaty treściowe / odrzucać sam
  > sygnał." Rozwiązanie: doprecyzowane przez użytkownika — dedup celowo ogranicza się do ID posta; powtórki tej
  > samej treści od różnych autorów to zamierzenie, nie błąd.
- FR-004: System przetwarza każdy post przez LLM według ustalonego, ustandaryzowanego schematu wejścia/wyjścia (prompt + JSON output ograniczony do schematu, żeby zminimalizować ryzyko halucynacji) i zapisuje wynik. Priority: must-have
  > Socrates: Kontrargument rozważony: "zawartość schematu jeszcze nieznana — ryzyko pustego przepisania pól /
  > halucynacji." Rozwiązanie: potwierdzone jako wymóg — kontrakt JSON i ograniczenie halucynacji; dokładna
  > zawartość schematu ustalana w Fazie 5.
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
  > (nie ma jeszcze zdefiniowanej miary "ważności" — zależy od schematu LLM z Fazy 5); zmiana porządku przez
  > usera to FR-008/FR-011b.
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
- FR-011a: Użytkownik może filtrować posty po podstawowych polach wynikających z klasyfikacji LLM, np. spółce/temacie i sentymencie. Priority: must-have
  > Socrates: Kontrargument rozważony: "dokładny schemat klasyfikacji LLM jeszcze nieustalony." Rozwiązanie:
  > przyjęte jako kwestia kolejności prac (schemat powstaje w Fazie 5 — Business Logic), nie powód do rezygnacji
  > z tej funkcji w MVP; FR zostaje must-have.
- FR-011b: Użytkownik może sortować posty po wybranych wartościach wynikających z klasyfikacji LLM. Priority: nice-to-have
  > Socrates: Kontrargument rozważony: "zależy w pełni od jeszcze nieustalonego schematu klasyfikacji."
  > Rozwiązanie: przyjęte do wiadomości — FR zostaje nice-to-have, doprecyzowany po Fazie 5.

### Kontrola dostępu i konta
- FR-012: Użytkownik może zalogować się do aplikacji przy użyciu adresu e-mail i hasła. Priority: must-have
  > Socrates: Kontrargument rozważony: "pełny system haseł (hashing, sesje, walidacja) to nietrywialny nakład
  > dołożony do już dużego zakresu." Rozwiązanie: nakład zaakceptowany — logowanie to fundament całego systemu
  > kont, spójne z decyzją o e-mail+hasło z Fazy 2; FR zostaje must-have.
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
  > Socrates: Kontrargument rozważony: "jeśli guard nie jest konsekwentnie zastosowany na każdym endpoincie,
  > może zostać dziura bezpieczeństwa." Rozwiązanie: FR zostaje must-have bez zmian; dodatkowo w Fazie 5
  > (Non-Functional Requirements) trafia mierzalny, testowalny wymóg konsekwentnego wymuszania uwierzytelnienia
  > na każdym chronionym endpoincie.
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
  > obniżone do nice-to-have; MVP nie wymaga wcale integracji z dostawcą e-mail.
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
  > Socrates: Kontrargument rozważony: "to kolejna nowa warstwa UI (dashboard) + trwałe logowanie uruchomień w
  > bazie — dodatkowy nakład obok już dużego zakresu." Rozwiązanie (przez użytkownika wprost): przy automatycznym
  > codziennym pipeline sensownie jest móc zweryfikować, czy ostatnie uruchomienia się udały — zostaje must-have.
- FR-022: Administrator może ręcznie wywołać nowy przepływ na żądanie z poziomu dashboardu; wywołanie jest asynchroniczne z osobnym sposobem sprawdzenia statusu. Priority: nice-to-have
  > Socrates: Kontrargument rozważony (przez użytkownika wprost): "automatyczny harmonogram (FR-002) już
  > istnieje; ręczne odpalenie pipeline'u było wcześniej słusznie nice-to-have." Rozwiązanie: zostaje
  > nice-to-have.

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
  > zależność e-mail z MVP i unika ryzyka single-user bez potrzeby budowania panelu webowego.

### Świeżość danych
- FR-030: Użytkownik widzi datę/godzinę ostatniej udanej aktualizacji danych (np. etykieta nad listą postów:
  "Dane zaktualizowane: [data/godzina]"); wyświetlana jest wyłącznie data ostatniego pomyślnego przebiegu,
  nigdy przebiegu zakończonego błędem. Priority: must-have
  > Socrates: Kontrargument rozważony: "sama data bez statusu myli — jeśli ostatni przebieg się nie udał, user
  > widzi starą datę bez wyjaśnienia dlaczego." Rozwiązanie (przez użytkownika wprost): pokazywać zawsze datę
  > ostatniego POMYŚLNEGO przebiegu, nigdy nieudanego — to naturalnie eliminuje mylące komunikaty bez potrzeby
  > dodatkowego statusu w widoku usera (status szczegółowy zostaje w dashboardzie admina, FR-021).

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
  > być tak tanie, jak sugeruje kontrakt mappera." Rozwiązanie: ryzyko przyjęte i odnotowane; FR zostaje
  > nice-to-have.
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

## Quality cross-check

```
Access Control:           present
Business Logic:           present (jedno zdanie: "Aplikacja ocenia każdy zebrany post pod kątem tego, czy
                           stanowi wartościowy sygnał inwestycyjny...")
Project artifacts:        present (shape-notes.md z pełnym checkpoint)
Timeline-cost ack:        present (## Timeline acknowledgment, mvp_weeks=10 świadomie zaakceptowane)
Non-Goals:                present (8 pozycji)
```

Wszystkie elementy obecne — `quality_check_status: accepted`.

## Business Logic

Aplikacja ocenia każdy zebrany post pod kątem tego, czy stanowi wartościowy sygnał inwestycyjny — na podstawie obecności konkretnej tezy z uzasadnieniem, wyraźnego kierunku/siły wydźwięku oraz nowości informacji — i przypisuje go do konkretnej spółki lub tematu.

Wejściem oceny jest treść posta (tytuł, treść, docelowo też komentarze) wraz z metadanymi źródłowymi (data publikacji, źródło, link do oryginału). Wyjściem jest ustandaryzowany wynik oceny per post: czy jest wartościowy, dla jakiej spółki/tematu, jaki kierunek i siłę wydźwięku niesie, oraz krótkie uzasadnienie/streszczenie tezy.

Użytkownik spotyka tę ocenę na liście i w widoku szczegółowym w aplikacji webowej (z możliwością filtrowania po tych polach — FR-011a/b) i może przejść przez link do oryginalnego posta, żeby zweryfikować kontekst. W rozszerzeniu poza MVP (FR-024/FR-025) oceny są agregowane w czasie per spółka, nakładane na wykres notowań giełdowych, z alertami przy obiecującym trendzie.

## Non-Functional Requirements

- Operator otrzymuje jednoznaczny, widoczny sygnał, gdy zaplanowany dzienny przebieg zbierania/przetwarzania nie powiedzie się lub zakończy częściowym błędem — nie dowiaduje się o tym pośrednio, przez brak nowych danych.
- Dodanie nowego źródła danych ogranicza się do nowego pozyskania i mapowania do wspólnego modelu; warstwa oceny, zapisu i udostępniania danych pozostaje niezmieniona.
- Każdy chroniony endpoint konsekwentnie wymaga uwierzytelnienia — brak przypadkowo niezabezpieczonej ścieżki dostępu do danych lub funkcji (testowalne: zestaw testów autoryzacji pokrywa każdą chronioną trasę).
- Użytkownik nie ma wglądu w dane kont innych userów (e-maile, aktywność) — widoczne wspólnie są wyłącznie przetworzone posty; dane kontowe ograniczone do własnego konta.
- Aplikacja webowa nie ma sztywnego wymogu czasu odpowiedzi w MVP — kilkusekundowe ładowanie przy normalnym obciążeniu jest akceptowalne.
- Dane (posty, konta) są przechowywane bezterminowo w MVP — brak automatycznej retencji/czyszczenia. Bezpieczeństwo przed utratą danych (backup) jest odpowiedzialnością platformy hostingowej, nie custom logiki aplikacji (patrz `## Forward: infra`).
- Oryginalny payload źródłowy każdego zebranego posta pozostaje dostępny bez utraty informacji, ale jest utrzymywany niezależnie od znormalizowanych danych używanych przez aplikację; jego storage musi umożliwiać bezstratną kompresję i późniejsze przeniesienie RAW bez zmiany kontraktu aplikacji ani utraty widoku surowego posta.

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
- Po wygaśnięciu linku bez użycia konto pozostaje bez hasła — logowanie niemożliwe do czasu wygenerowania nowego linku

## Non-Goals

- **Automatyczna wysyłka e-maili jako wymagany element MVP** — ścieżka must-have (CLI, FR-028) nie wymaga integracji z dostawcą poczty; link aktywacyjny generuje CLI, a operator przekazuje go zaproszonej osobie poza systemem. Automatyczna wysyłka maila z panelu webowego (FR-017) pozostaje możliwym, nice-to-have rozszerzeniem — nie jest tu wykluczona całkowicie, tylko nie jest wymagana do działania MVP.
- **Drugi czynnik uwierzytelniania (TOTP) oraz OAuth** — login e-mailem i hasłem wystarcza na start MVP.
- **Przetwarzanie komentarzy pod postami** — MVP ocenia wyłącznie posty; komentarze to jawnie odroczone rozszerzenie na przyszłość.
- **Custom logika archiwizacji/backupu danych** — bezpieczeństwo przed utratą danych to odpowiedzialność platformy hostingowej (Railway), nie kodu aplikacji (patrz `## Forward: infra`).
- **Wysoka dostępność / multi-region** — pojedyncza instancja na jednej platformie wystarcza dla małej, zapraszanej grupy userów.
- **Dodatkowe źródła danych poza Wykop/Reddit** (np. Twitter/X, fora branżowe) — poza zakresem MVP i jego zaplanowanego rozszerzenia.
- **Personalizacja samej logiki oceny LLM** ("co znaczy wartościowy sygnał") per user — kryterium wartościowości pozostaje wspólne dla wszystkich; personalizowalne jest wyłącznie filtrowanie widoku na już obliczonej, wspólnej klasyfikacji (FR-011a/b), nie sama reguła oceny.
- **Rozbudowany system IAM/RBAC poza modelem `user`/`admin`** — dodatkowe role, granular permissions, rozbudowana edycja/deaktywacja kont, publiczna rejestracja oraz samodzielny reset hasła przez użytkownika i zarządzanie API keys pozostają poza zakresem MVP. (Nie wyklucza to FR-016/FR-017/FR-019 — panel webowy tworzenia kont i automatyczna wysyłka maila zaproszenia pozostają nice-to-have zgodnie z aktualnymi FR.)

## Open Questions

1. **Czy `product_type` to "data-pipeline" czy "web-app"?** — Projekt to dwa moduły: pipeline+API jako rdzeń, pełnoprawna aplikacja webowa (logowanie, dashboard, lista/filtrowanie) jako warstwa konsumpcji. Wybrano `data-pipeline` jako najbliższą pojedynczą wartość; do doprecyzowania przy `/10x-tech-stack-selector`. Owner: użytkownik.
2. **Dokładny kształt pól w JSON zwracanym przez ocenę LLM** — ustalone kryteria (teza z uzasadnieniem, kierunek/siła wydźwięku, nowość, spółka/temat) trzeba jeszcze przełożyć na konkretne nazwy pól/typy i walidację schematu; wpływa też na dokładny zakres FR-011a/b. Owner: użytkownik. By: przed implementacją FR-004.
3. **Kanał i próg dla FR-025 (powiadomienia o obiecującym trendzie)** — nie ustalono jeszcze, czym jest "obiecujący trend" liczbowo ani gdzie ma trafiać powiadomienie. Owner: użytkownik. By: przy realizacji FR-025 (poza MVP).
4. **Backup/retencja na Railway** — sprawdzić realne możliwości managed backupu bazy danych na wybranej platformie przy `/10x-infra-research`, żeby "bezterminowe przechowywanie" (NFR) nie okazało się bez żadnej siatki bezpieczeństwa. Owner: użytkownik. By: przed `/10x-infra-research`.

## Forward: tech-stack

Informacyjne, nie wchodzi do PRD — do podjęcia w `/10x-tech-stack-selector`:

- Podział na dwa moduły (potwierdzone w tej sesji): Moduł 1 (backend) — pipeline zbierania/przetwarzania + REST API. Moduł 2 (frontend) — aplikacja webowa konsumująca REST API modułu 1.
- Poprzednia, zarchiwizowana sesja (`context/foundation/archive/shape-notes-2026-09-26-1137.md`) zawierała konkretne preferencje technologiczne (LangGraph, FastAPI) — do potwierdzenia lub zmiany przy `/10x-tech-stack-selector`, nie zakładać automatycznie ich dalszej aktualności.
- Nowy komponent: CLI operatorskie (bootstrap/recovery/tworzenie usera, FR-026/027/028) — musi współdzielić logikę uwierzytelniania z backendem API (Moduł 1), nie być osobnym, niezależnym systemem kont.
- Zasada graniczna z poprzedniej sesji (nadal aktualna): cała komunikacja aplikacji webowej z backendem przez API — bez bezpośrednich wywołań poza tą granicą.
- Kontrakt mappera per źródło (przeniesione z poprzedniej sesji): każde źródło (Wykop, Reddit, przyszłe) mapuje własne, surowe dane do wspólnego, ustandaryzowanego modelu przed dalszym przetwarzaniem; warstwa downstream (ocena LLM, zapis, API) nie powinna zależeć od formatu konkretnego źródła. Wejście do LLM ma jasny, wspólny kontrakt; wyjście z LLM ma jasny, ustandaryzowany kontrakt (schemat z FR-004). To wskazówka architektoniczna dla `/10x-tech-stack-selector`, nie osobny FR — zgodna z NFR mówiącym, że dodanie nowego źródła ogranicza się do pozyskania i mapowania, bez zmiany warstwy oceny/zapisu/udostępniania.
- Nowa granica storage dla RAW: surowy payload źródłowy nie powinien być częścią głównego, zapytaniowego rekordu posta. Warstwa backendu ma traktować RAW jako osobny, niezmienny artefakt dostępny przez stabilne odwołanie, tak aby można go było bezstratnie kompresować i w przyszłości przenieść między backendami storage bez zmiany modelu domenowego ani API. Konkretny backend i algorytm kompresji są decyzją downstream.

## Forward: technical-roadmap

Informacyjne, nie wchodzi do PRD/MVP:

- W przyszłości pipeline może zostać rozszerzony o lekkie modele ML/AI, embeddingi i klasyfikatory, poza samą ekstrakcją przez LLM.
- Powinno to zostać uwzględnione przy wyborze architektury/orchestratora w `/10x-tech-stack-selector`, żeby uniknąć architektury, którą trzeba by całkowicie przepisać przy dodawaniu takich etapów.
- To NIE jest zatwierdzony wybór technologiczny — LangGraph i FastAPI to preferencje z poprzedniej, zarchiwizowanej sesji, do ponownej oceny przy `/10x-tech-stack-selector`, a nie automatycznie przyjęty wybór.

## Forward: infra

Informacyjne, nie wchodzi do PRD — do podjęcia w `/10x-infra-research`:

- Platforma docelowa: Railway (nie self-hosted).
- Aplikacja będzie publicznie osiągalna pod adresem internetowym — stąd wymóg uwierzytelniania (patrz `## Access Control`).
- Retencja/backup: sprawdzić możliwości managed backupu bazy danych na Railway — PRD zakłada bezterminowe przechowywanie danych bez custom logiki archiwizacji w aplikacji; bezpieczeństwo przed utratą danych ma zapewniać platforma, nie kod produktu.
- Wzrost RAW/storage: stary Szczur Radar miał 11 767 wpisów z 29 dni i bazę SQLite ok. 46 MB (ok. 45 MB w `items`), co daje historyczny punkt odniesienia ok. 4 KB na wpis i ok. 0,56 GB/rok przy podobnym ruchu. Nowa wersja ma większy narzut (PostgreSQL, indeksy, wynik LLM), więc przed implementacją ingestu trzeba zaprojektować RAW jako osobny, kompresowalny artefakt i monitorować jego wzrost niezależnie od danych operacyjnych.
- Railway-specific follow-up: porównać trzymanie RAW w osobnym storage z trzymaniem go na wolumenie Postgresa. Według dokumentacji Railway sprawdzonej 2026-09-27 Hobby ma 5 GB Volume Storage, podczas gdy Buckets mają na Hobby łączny limit 1 TB i są rozliczane osobno; to czyni object storage naturalnym kandydatem dla długowiecznych RAW payloadów, ale decyzja fizycznego backendu należy do planowania implementacji.
