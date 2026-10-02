# Kontrakt oceny LLM V2 z walidatorem i pomiarem na wzorcu — plan implementacji

> Wersja 2 planu (2026-09-30). Zastępuje plan z 2026-09-28 (macierz 4 dostawców, 12 wymyślonych przypadków × 3), który powstał przed pilotażami na żywych danych. Podstawa: `frame.md` (runda 2 + dziennik pilotaży) i `research.md`. Przegląd `reviews/plan-review.md` dotyczy poprzedniej wersji.

## Overview

Roadmapa F-02 (`assessment-contract-harness`). Pilotaże na prawdziwych postach z Wykopu i Reddita pokazały, że kontrakt V1 był niedookreślonym szkicem: nie definiował, co jest celem ani od kiedy wzmianka jest stanowiskiem, a jego graf argumentów i dosłowność cytatów generowały większość odrzuceń, które nie były błędami treści. Ta zmiana (1) doprowadza kontrakt do wersji V2 opartej na danych, (2) zapisuje go jako kod z walidatorem w `szczur-radar-api/app/assessment/`, (3) buduje zestaw wzorcowy z prawdziwych postów oznaczonych przez użytkownika i (4) mierzy na nim GPT-6 Luna w trybie flex (pierwszy wybór), DeepSeek Flash (zapas) i Gemini 3.1 Flash-Lite (punkt odniesienia), wybierając model domyślny dla S-02 (decyzja użytkownika 2026-10-01). Pomiar eskalacji do drugiego modelu jest odłożony po MVP.

## Current State Analysis

- **Kod.** S-01 jest na `main`: `szczur-radar-api/app/` ma `auth/`, `models.py`, `cli.py`, `database.py`, migracje Alembic; testy w `szczur-radar-api/tests/` (pytest + httpx, marker `db` w `pyproject.toml`). Brak jakiejkolwiek integracji z LLM: w `szczur-radar-api/pyproject.toml` nie ma `langchain*` ani `langgraph`; `psycopg[binary]>=3.2` jest obecny.
- **Konfiguracja** czytana leniwie z `os.environ` przy pierwszym użyciu (`app/database.py:47`, `app/auth/tokens.py:34`) — import modułu nie wymaga zmiennych.
- **CI** (`.github/workflows/ci.yml`, job `backend`): `uv sync --locked` + `uv run pytest -q` z usługą Postgres, bez sekretów dostawców LLM; workflow nie może być filtrowany ścieżkami; merge do `main` = deploy.
- **Kontrakt V1** (`context/foundation/llm-assessment/contract-v1.md`, status accepted) — przyjęty bez kontaktu z modelem; jego jedyny pełny przykład łamie własny niezmiennik 8 (`contract-v1.md:43` vs `:129`, `:142`); ≥15 reguł niedookreślonych (`frame.md`, runda 1).
- **Dowody z pilotaży** (`frame.md`, sekcje „Pilot …”; dane i skrypty w `pilot/`):
  - tanie modele przechodzą walidację V1 w 95–98% (Wykop), koszt ~$2–10/mies. przy 12k postów (po korekcie cen — `frame.md`, „Korekta cen”); skrócony prompt nie pogarsza zgodności;
  - zgodność dominującego kierunku posta między modelami 74–78%, sprzeczne kierunki 0–2%; niezgoda dotyczy wyboru celów i progu stanowiska (definicje), a na Wykopie dodatkowo trafności (memy, żale);
  - odrzucenia `fabricated_evidence` to w większości twarda spacja z Wykopu, skróty „[...]” i zmiana odmiany, nie zmyślona treść;
  - Gemini odrzuca schemat z `minItems`/`maxItems` (HTTP 400); DeepSeek w trybie myślenia odrzuca wymuszony `tool_choice`, a `deepseek-v4-pro` w trybie strict pomijał wymagane pole; paczkowanie postów zmienia co trzecią ocenę;
  - GPT-6 Luna (pilot 8): 98% zgodności z kontraktem i zero przerobionych cytatów, ale treść nie lepsza od tanich modeli (trafność 73% względem etykiet rundy 1); domyślnie rozumuje (~700 tokenów na post, 8,7 s);
  - modele decyzyjne przez OpenRouter — Jev (TypeSafe) i Span-01 (Respan) — nie poprawiają decyzji, ale jako sygnał niepewności wskazują posty sporne: ćwiartka o najniższym `confidence` Jev lub najwyższym „unclear” Span-01 zawiera ~47–50% postów, przy których tanie modele różnią się trafnością (piloty 6 i 7).
- **Decyzje użytkownika po ramowaniu** (`frame.md`, runda 2): próg stanowiska „szeroko + pewność”, pierwszy ekran MVP = lista postów, źródło MVP = Wykop (Reddit drugi). Instrukcja oznaczania `pilot/labeling-guidelines.md` v0.3 zawiera rozstrzygnięte reguły.
- **Etykiety rundy 1** (`pilot/labels-2026-09-28.json`) były oznaczane bez instrukcji i ostrożniej niż przyjęty próg — nie są wzorcem.

## Desired End State

- `context/foundation/llm-assessment/contract-v2.md` jest źródłem prawdy (accepted); V1 w archiwum; `examples.md`, `agent-implementation.md`, README kontraktu, PRD i roadmapa (z grafiką) są z nim spójne.
- `szczur-radar-api/app/assessment/` udostępnia: modele wejścia/wyjścia V2, walidator z klasyfikacją naruszeń (cytaty z normalizacją białych znaków), odsiew wpisów bez treści oraz `assess(input) -> AssessmentResult` z pochodzeniem oceny, wykonujący najwyżej jedno wywołanie dostawcy przez LangChain.
- `szczur-radar-api/tests/assessment/gold/` zawiera ~120 zanonimizowanych postów z dwóch dni (innych niż 2026-09-28) z etykietami użytkownika według instrukcji.
- Raport w `context/changes/assessment-contract-harness/reports/` pokazuje dla Luny (flex), DeepSeek Flash i Gemini 3.1 Flash-Lite: zgodność z kontraktem, trafność, wykrywanie celów (recall i precision), kierunek dla dopasowanych celów ze stanowiskiem, dominujący kierunek posta, sprzeczne kierunki oraz koszt w rozbiciu na kanały wywołania (Luna: zwykły i flex; DeepSeek: bezpośrednio w szczycie i poza nim oraz OpenRouter z przypiętym dostawcą), a dla Luny flex także odsetek odmów i medianę czasu.
- Model domyślny spełnia progi na wzorcu: zgodność z kontraktem ≥95%, trafność binarna ≥80%, recall celów ≥80%, precision celów ≥70%, kierunek dla dopasowanych celów ze stanowiskiem ≥75%, dominujący kierunek posta ≥75%, sprzeczne kierunki ≤3% — i jest wpisany jako `DEFAULT_ASSESSMENT_MODEL`.
- `uv run pytest -q` (CI, offline, bez kluczy) sprawdza modele, walidator, `assess()` na sztucznym dostawcy, spójność wzorca oraz odtwarza nagrania prawdziwych odpowiedzi, w tym progi modelu domyślnego.

Weryfikacja: zielone CI na PR, raport w folderze zmiany, zatwierdzenie przez użytkownika tekstu V2, modelu domyślnego i jego zmierzonego kosztu.

### Key Discoveries:

- `szczur-radar-api/app/database.py:47`, `app/auth/tokens.py:34` — leniwy odczyt `os.environ`; konfiguracja oceny powiela wzorzec (import bez kluczy API).
- `szczur-radar-api/pyproject.toml` — istniejący marker `db` w `[tool.pytest.ini_options]`; nowy marker `live` dochodzi obok, a `addopts` nie może wyłączyć testów `db`.
- `context/foundation/README.md` — dokument w pełni zastąpiony przenosi się do `context/foundation/archive/YYYY-MM-DD-<doc>.md`.
- `REPOSITORY_GUIDELINES.md` — każda zmiana `roadmap.md` wymaga aktualizacji `tools/render_roadmap.swift` i regeneracji `docs/visuals/roadmap-mvp.png`; rozbieżność kodu z PRD zgłasza się, nie rozstrzyga po cichu.
- `context/foundation/lessons.md` — „jedna gałąź + jeden PR”, „tylko człowiek merguje do main”, „przed zamrożeniem kontraktu LLM zrób tani pilot skryptem na żywych danych”.
- `pilot/scripts/prompt-short.md`, `prompt-v2-rules.md`, `pilot.py` (schemat i walidator pilotażowy) — sprawdzony punkt wyjścia dla promptu, schematu i reguł walidacji.

## What We're NOT Doing

- Zapis ocen do bazy, migracje, pobieranie z Wykopu w pipeline, mapowanie RAW, deduplikacja, UI, trasy API, harmonogram (S-02/S-03/S-05).
- Graf LangGraph ani checkpointing — `assess()` to funkcja za portem dostawcy; S-02 zdecyduje o orkiestracji. Wskazówka dla S-02: `RetryPolicy` węzła oceny musi mieć jawne `retry_on` ograniczone do błędów transportu (429, 5xx, przekroczenie czasu) — nigdy naruszeń kontraktu — a liczba prób dobrana z uwzględnieniem ponowień klienta dostawcy (próby obu poziomów się mnożą). Wartości domyślne `RetryPolicy` (LangGraph 1.x): `max_attempts=3`, `initial_interval=0.5`, `backoff_factor=2.0`, `max_interval=128`, `jitter=True`; czy domyślne `retry_on` obejmuje 429 — do sprawdzenia w kodzie przy S-02.
- Graf argumentów (`supported_by_claim_ids`), drugi cytat na claim, liczbowe score/confidence w wyniku oceny — poza V2.
- Reddit jako źródło, komentarze i kontekst rodzica w użyciu, widok zbiorczy „o czym się mówi”, rozpoznawanie spółek (entity resolution) — poza MVP zgodnie z PRD.
- Paczkowanie postów w jednym wywołaniu (odradzone pomiarem, pilot 3) i Batch API (decyzja pipeline'u, S-02/S-05; dla Luny tę samą obniżkę ceny daje flex, DeepSeek nie ma API batch). F-02 przygotowuje tylko podział `assess()` na przygotowanie zapytania i ocenę odpowiedzi, żeby tryb batch nie wymagał przebudowy walidacji.
- Ponowienia semantyczne i „naprawianie” odpowiedzi; jedyne ponowienia to transportowe.
- Pomiar i wdrożenie eskalacji do drugiego modelu, w tym przełącznik w `assess()`, sygnały Jev / Span-01, niezgoda dwóch modeli i przebieg Gemini 3.8 Flash — odłożone po MVP. F-02 wykonuje jedną ocenę na post.
- **Odłożone na później (po MVP), świadomie nie teraz:**
  - *Embeddingi* (np. Gemini Embedding, BGE-M3, Qwen3-Embedding) — pod widok zbiorczy „o czym dziś się mówi” (grupowanie podobnych postów), dopasowanie slangu do spółek dla filtra FR-011a („en wideo” → Nvidia) i wykrywanie podobnych wpisów; wymagają zapisanych ocen i decyzji produktowej o widoku zbiorczym.
  - *Klasyfikator trafności/odsiewu szumu uczony na zapisanych ocenach LLM* (encoder lub embeddingi + prosty klasyfikator) — sensowny dopiero, gdy pipeline zbierze kilka tysięcy ocenionych postów; dałby powtarzalność w najsłabszym miejscu Wykopu (trafność).
  - *Ekstraktor fragmentów typu GLiNER do wskazywania celów* — adresuje wybór celów dosłownymi fragmentami tekstu, ale brak dowodów dla polskiego i slangu, a model trzeba hostować samodzielnie.
  - *Gotowe klasyfikatory „finansowe” (rodzina FinBERT)* — sentyment zdań z wiadomości, bez polskiego i nieprzeznaczone do mediów społecznościowych; w porównaniach przegrywają z LLM bez trenowania. Wracamy tylko, jeśli pojawi się model dla polskich treści giełdowych.
  - *Podpięcie sygnału modelu decyzyjnego (Jev / Span-01) lub niezgody dwóch modeli do `assess()`* — część odłożonego badania eskalacji po MVP.
- Adapter Anthropic; macierz 4 dostawców z poprzedniego planu.
- Sekrety dostawców LLM w Railway i w CI (F-01); testy na żywych modelach w CI.
- Używanie etykiet rundy 1 jako wzorca.

## Implementation Approach

Pięć faz od dokumentu do pomiaru. Faza 1 ustala kontrakt V2 i spójność dokumentów — bez niej kod i wzorzec nie mają definicji. Faza 2 to czysty kod bez sieci (modele, walidator, odsiew). Faza 3 dokłada dostawców przez LangChain, prompt i `assess()` testowane sztucznym dostawcą, z jedną kontrolą na żywo, że ścieżka LangChain zachowuje się jak bezpośrednie wywołania z pilotaży. W fazie 4 (zestaw wzorcowy) przygotowanie arkusza zależy od akceptacji kontraktu w fazie 1 i może iść równolegle z fazą 2; pobranie i przygotowanie próbki wymaga modeli wejścia i odsiewu z fazy 2. Oznaczanie próbki przez użytkownika może iść równolegle z fazą 3. Faza 5 łączy wszystko: pojedynczy przebieg porównawczy dwóch tanich modeli i Luny, nagrania odtwarzane w CI, podstawowy raport i wybór modelu. Wynik jednego przebiegu zamyka F-02 po decyzji użytkownika; badanie kaskady nie blokuje S-02.

Jedna prawda o kształcie: modele Pydantic z fazy 2. Schemat wysyłany do dostawcy jest z nich wyprowadzany i upraszczany w adapterze, ale surowa odpowiedź zawsze przechodzi pełny model i walidator.

Cała zmiana na jednej gałęzi `feature/assessment-contract-harness`, jeden PR, merge wykonuje użytkownik po fazie 5.

## Critical Implementation Details

**Ścieżka LangChain jest niesprawdzona — pilotaże szły bezpośrednio przez HTTP.** Znane pułapki do obsłużenia w adapterach: (a) Gemini zwraca HTTP 400 dla schematu z `minItems`/`maxItems`, więc schemat wysyłany do dostawcy nie zawiera ograniczeń liczności tablic; (b) `langchain-deepseek` po cichu zamienia `json_schema` na function calling, DeepSeek w trybie myślenia odrzuca wymuszony `tool_choice`, więc adapter musi wyłączyć myślenie; (c) tryb strict nie gwarantuje pól wymaganych (`deepseek-v4-pro` pomijał je w 12/23 odpowiedzi). Dlatego faza 3 kończy się kontrolą na żywo, a kompletność i limity egzekwuje wyłącznie nasz model Pydantic.

**Normalizacja cytatów (niezmiennik cytatu w V2).** Porównanie `quote in field` wykonuje się po normalizacji obu stron: każdy znak biały Unicode (w tym twarda spacja ` `) → spacja, ciągi białych znaków (także łamania linii) → jedna spacja, obcięcie końców. Minimalna długość cytatu po normalizacji wynosi 1 znak; pusty wynik normalizacji dla anchor lub evidence jest odrzucany przed sprawdzeniem podciągu jako `domain_invariant_violation`. Nic więcej: bez zmiany wielkości liter, bez usuwania markdownu, bez normalizacji cudzysłowów. Zmieniona odmiana i skrót „[...]” nadal są naruszeniem.

**Kolejność walidacji i klasyfikacja.** Surowa odpowiedź → parsowanie JSON (`invalid_structured_output`) → pełny model Pydantic z limitami (`schema_violation`) → walidator domenowy zbierający wszystkie naruszenia; klasa wyniku to klasa pierwszego naruszenia w porządku `invalid_reference` → `fabricated_evidence` → `domain_invariant_violation`.

**Wynik bez wywołania modelu.** Wpis bez treści (po usunięciu tagów, wzmianek, adresów URL i używanego w zanonimizowanych próbkach znacznika `<url>` zostaje mniej niż 3 znaki w tytule i treści łącznie) dostaje gotowy wynik: `relevance = unclear`, puste listy, flaga `insufficient_content`, a pochodzenie jawnie oznacza źródło `prefilter` (bez dostawcy, modelu i tokenów). Reguła jest częścią kontraktu V2.

**Granica F-02.** `assess()` wykonuje najwyżej jedno wywołanie modelu; nie ma przełącznika eskalacji ani drugiego dostawcy. Ewentualną kaskadę można zaplanować po MVP na podstawie danych z działającego produktu. Decyzja użytkownika z triage F2 świadomie odracza Gemini 3.8 Flash i modele decyzyjne. Role modeli: GPT-6 Luna w trybie flex — pierwszy wybór; DeepSeek Flash — zapas; Gemini 3.1 Flash-Lite — punkt odniesienia (decyzja użytkownika 2026-10-01; zastępuje ustalenie z triage F2, w którym Luna była wyłącznie punktem odniesienia).

**Flex i ponowienia.** Tryb flex (OpenAI `service_tier="flex"` bezpośrednio albo dostawca `openai/flex` w OpenRouter) kosztuje połowę ceny, ale odpowiada wolniej i przy braku zasobów odmawia błędem 429. Adapter ma długi, konfigurowalny limit czasu wywołania. **Ponowienia żyją wyłącznie w adapterze** (przegląd 2026-10-01, F1): klienci LangChain/SDK wszystkich dostawców mają `max_retries=0`, a adapter wykonuje do 3 prób transportowych (429, 5xx, przekroczenie czasu, błąd połączenia) z wykładniczym odstępem i respektowaniem nagłówka `retry-after`. Naruszenia kontraktu i błędy parsowania nigdy nie są ponawiane (brak ponowień semantycznych). „Jedno wywołanie” w tym planie oznacza jedno wywołanie logiczne `complete()` na ocenialny post; liczba prób jest zapisywana osobno w pochodzeniu (`attempts`). **Powrót z flex** (F2): jeśli 3 kolejne próby w trybie flex kończą się odmową 429 z powodu braku zasobów, adapter wykonuje dla tego samego wywołania próbę w trybie standardowym (ten sam model, pełna cena — to nie jest eskalacja do innego modelu); pochodzenie zapisuje faktyczny tryb (`service_tier`: `flex` | `default`) i `flex_fallback: true`, a raport z fazy 5 liczy takie posty i ich koszt. S-02 zapisuje te pola w bazie razem z oceną.

## Phase 1: Kontrakt V2 i dokumenty

### Overview

Nowy kontrakt V2 jako źródło prawdy, V1 w archiwum, spójne dokumenty pomocnicze, PRD i roadmapa; folder `pilot/` trafia do repo.

### Changes Required:

#### 1. Kontrakt V2

**File**: `context/foundation/llm-assessment/contract-v2.md`

**Intent**: Zapisać kontrakt w kształcie „rdzeń + tezy z cytatem”, z definicjami, których V1 nie miał, tak żeby walidator i prompt nie musiały niczego dopowiadać.

**Contract**: Frontmatter `version: 2`, `status: accepted` (po akceptacji użytkownika), `source_of_truth: true`. Treść:
- wejście `AssessmentInputV1` bez zmian kształtu (w MVP `context.parent_* = null`);
- wyjście `AssessmentOutputV2`: `relevance`, `modifiers[]`, `targets[]` (`id`, `kind`, `reference`, `anchor{document, field, quote}`, `stance{direction, assertiveness, claim_ids}`), `claims[]` (`id`, `kind`, `attribution`, `target_ids`, `statement`, `evidence` — dokładnie jeden cytat z `item`), `flags[]`; bez `supported_by_claim_ids`;
- definicja celu (podmiot, nie teza; każda nazwana spółka/aktywo; hashtag spółki jako anchor; tagi kanału nigdy; cel ogólny `other` dla slangu bez nazwy) — z instrukcji v0.3, reguły 3.1–3.6;
- reguły trafności (memy, narzekania na brokera, sam link, pytania o wydarzenia rynkowe) — reguły 2.1–2.4;
- próg stanowiska „szeroko + pewność” i znaczenie `assertiveness` (w tym `hedged` dla stanowiska odczytanego z sugestii), scenariusz dwustronny = `unclear`, pytanie retoryczne, przyjęta nadzieja, „na peronie” — reguły 4.0–4.6;
- dla każdej wartości `direction` jawnie: czy `claim_ids` są wymagane, dozwolone czy zabronione;
- niezmiennik cytatu z normalizacją białych znaków (definicja z „Critical Implementation Details”), minimalna długość cytatu po normalizacji białych znaków: 1 znak; pusty wynik normalizacji dla anchor lub evidence jest odrzucany; jednostka limitów = znaki Unicode (code points);
- reguła „wpis bez treści” i wynik bez wywołania modelu;
- `statement` w języku posta;
- limity, lista zakazów (dawne §13), granice downstream (dawne §12), reguła zmiany (dawne §14);
- sekcja „Zmiany względem V1” z odwołaniem do `frame.md`.

#### 2. Archiwizacja V1

**File**: `context/foundation/llm-assessment/contract-v1.md` → `context/foundation/archive/<data-przeniesienia>-llm-assessment-contract-v1.md`

**Intent**: Zachować V1 jako zapis historyczny zgodnie z konwencją foundation, bez zmiany treści poza nagłówkiem.

**Contract**: `git mv`; we frontmatterze `status: superseded` i wskazanie na `contract-v2.md`. Odwołania `contract-v1.md:<linia>` w `frame.md` i `research.md` zostają (dotyczą treści archiwalnej). Przed commitem popraw ścieżkę odczytu V1 w `pilot/scripts/pilot.py` na względną wobec `__file__` i wskazującą nowy plik archiwalny. Zastąp pozostałe absolutne ścieżki do folderu pilota w `pilot4.py`, `compare_gold.py` i `analyze6.py` ścieżkami względnymi. Dla wszystkich skryptów w `pilot/scripts/` rozdziel katalog skryptów i promptów (`scripts/`), wyników (`runs/`) oraz próbek i etykiet (folder `pilot/`); ścieżki wyprowadź z `__file__`. Popraw odczyty i zapisy wyników w `pilot.py`, `pilot2.py`–`pilot8.py` oraz `analyze2.py`, `analyze3.py`, `analyze5.py`, `analyze6.py`, `analyze7.py`, `analyze8.py` na `runs/`, zachowując prawidłowe odczyty w `compare_gold.py` i `frame2_checks.py`. Zweryfikuj również zależności przez `exec`, w tym `pilot4.py` i `pilot5.py` przez `pilot3.py` oraz `analyze7.py` i `analyze8.py` przez `analyze6.py`.

**Odtwarzanie i ponowne wykonanie pilotów**: Analizy `analyze2.py`, `analyze3.py`, `analyze5.py`, `analyze6.py`, `analyze7.py`, `analyze8.py`, `compare_gold.py` i `frame2_checks.py` mają działać offline na zachowanych próbkach, etykietach i wynikach z `runs/`; zweryfikuj je po poprawkach ścieżek, bez nowych wywołań modeli. Ponowne wykonanie `pilot.py`, `pilot2.py` i `pilot5.py` wymaga dodatkowych lokalnych danych wejściowych (`gielda-p1.json`, `gielda-p2.json`, `gielda-2026-09-28.json`, `reddit-sample-24_29.json`), których nie ma w repo. Te skrypty przyjmują ścieżki wejściowe jako argumenty i przed wywołaniem API sprawdzają ich dostępność; brak pliku daje czytelny komunikat wskazujący wymagany argument i plik. `context/changes/assessment-contract-harness/pilot/README.md` dokumentuje oddzielnie komendy analiz offline oraz ponownego wykonania pilotów z wymaganymi plikami wejściowymi i zmiennymi kluczy API. Zachowane zanonimizowane `runs/pilot2-results.json` i nagrania pilota Reddita pozostają podstawą analiz; brak lokalnych danych do ponownego wykonania nie blokuje odtworzenia analiz historycznych.

#### 3. Dokumenty pomocnicze kontraktu

**File**: `context/foundation/llm-assessment/README.md`, `examples.md`, `agent-implementation.md`

**Intent**: Usunąć rozjazd z V2 i zastąpić wymyślone przykłady prawdziwymi.

**Contract**: README wskazuje `contract-v2.md` jako źródło prawdy i aktualizuje „najważniejsze decyzje”. `examples.md`: przykłady z zanonimizowanych postów z `pilot/` (slang jako anchor, twarda spacja w cytacie, mem bez aktywa, sam link, wpis bez treści, scenariusz dwustronny, stanowisko z sugestii), każdy z kompletnym wyjściem zgodnym z V2. `agent-implementation.md`: walidacje backendu i klasy błędów dla V2; „Required fixtures” zastąpione zestawem wzorcowym z progami i listą testów walidatora; definicja done warstwy kontraktu odwołuje się do progów.

#### 4. PRD i roadmapa

**File**: `context/foundation/prd.md`, `context/foundation/roadmap.md`, `tools/render_roadmap.swift`, `docs/visuals/roadmap-mvp.png`, `context/changes/assessment-contract-harness/change.md`

**Intent**: Zgłosić i usunąć rozbieżność między PRD/roadmapą a kontraktem bez grafu argumentów.

**Contract**: Przeszukaj aktywne `prd.md` i `roadmap.md` pod kątem `contract-v1.md`, `V1`, grafu argumentów i opisów argumentów. W `prd.md` popraw FR-004, opis oceny przy `:256` i `:258` oraz Open Question 2; w `roadmap.md` popraw wizję, źródła, tabelę i wynik F-02, opis S-02, wynik i ryzyko S-03, kolejność prac oraz odwołania do kontraktu. Opisz wynik V2 jako „trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności”, bez obietnicy grafu argumentów. `change.md` aktualizuje tytuł i notatkę z V1/macierz 4 dostawców do rzeczywistego zakresu V2. Status F-02 w roadmapie pozostaje `planning`. `tools/render_roadmap.swift` usuwa twardy napis „Walidacja V1”, a grafika jest regenerowana zgodnie z regułą repo.

#### 5. Instrukcja oznaczania i folder pilotaży

**File**: `context/changes/assessment-contract-harness/pilot/labeling-guidelines.md`, cały `context/changes/assessment-contract-harness/pilot/`

**Intent**: Instrukcja ma mówić to samo co kontrakt V2; dowody pilotaży mają być w repo.

**Contract**: Instrukcja v1.0: odwołania do sekcji `contract-v2.md` zamiast własnych definicji tam, gdzie kontrakt je przejął; słownik slangu zostaje w instrukcji jako materiał do promptu. Commit całego `pilot/` (próbki, etykiety, arkusze, skrypty, prompty, `runs/`) po kontroli, że nie ma w nim kluczy API ani nicków autorów.

### Success Criteria:

#### Automated Verification:

- `contract-v2.md` istnieje, a `contract-v1.md` jest tylko w archiwum: `test -f context/foundation/llm-assessment/contract-v2.md && ! test -f context/foundation/llm-assessment/contract-v1.md && ls context/foundation/archive/*llm-assessment-contract-v1.md`
- Dokumenty foundation poza archiwum nie odwołują się do ścieżki V1: `rg -n 'contract-v1\.md' context/foundation --glob '*.md' --glob '!**/archive/**'` zwraca kod 1 (brak trafień)
- W folderze zmiany nie ma kluczy API: `grep -rEn "sk-or-v1-[0-9a-f]{20}|sk-[0-9a-f]{32}|AQ\.Ab8|w5[0-9a-f]{30}" context/changes/assessment-contract-harness` nie zwraca nic
- Grafika roadmapy jest zregenerowana w tym samym commicie co `roadmap.md`: `git diff --name-only main -- docs/visuals/roadmap-mvp.png tools/render_roadmap.swift` zwraca oba pliki
- Testy backendu nadal przechodzą: `cd szczur-radar-api && uv run pytest -q`

#### Manual Verification:

- Użytkownik przeczytał i zaakceptował tekst `contract-v2.md` (kształt wyjścia, definicja celu, próg stanowiska, reguła cytatu, reguła „brak treści”)
- Użytkownik potwierdził zmienione sformułowania w `prd.md` i `roadmap.md` (w tym opis S-03)
- Każdy przykład w `examples.md` ma kompletne wyjście i ręcznie sprawdzone cytaty względem swojego wejścia

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 2. Po akceptacji kontraktu można rozpocząć przygotowanie arkusza z fazy 4; pobranie i przygotowanie próbki czeka na modele wejścia i odsiew z fazy 2.

---

## Phase 2: Kontrakt jako kod

### Overview

Modele Pydantic 1:1 z kontraktem V2, walidator z klasyfikacją naruszeń i normalizacją cytatów, odsiew wpisów bez treści, testy offline także na prawdziwych fragmentach z pilotaży.

### Changes Required:

#### 1. Modele kontraktu

**File**: `szczur-radar-api/app/assessment/__init__.py`, `szczur-radar-api/app/assessment/contract.py`

**Intent**: Odwzorować wejście i wyjście V2 jako zamknięte modele, żeby obcy klucz, brak klucza, wartość spoza enumu i przekroczony limit były odrzucane u źródła.

**Contract**: `AssessmentInputV1`, `AssessmentOutputV2` z `extra="forbid"` na każdym obiekcie; enumy jako `Literal[...]` dokładnie z kontraktu; wszystkie klucze wymagane; limity kontraktu jako ograniczenia pól (długości liczone w znakach Unicode); walidacja wejścia (tytuł i treść nie oba puste). Stała `CONTRACT_VERSION = "V2"`. Brak pól `supported_by_claim_ids`, `score`, `confidence`, `is_signal`, `schema_version` w wyjściu.

#### 2. Walidator domenowy, normalizacja i klasy błędów

**File**: `szczur-radar-api/app/assessment/validation.py`

**Intent**: Sprawdzić wszystko, czego schemat nie wyrazi, i zwrócić pełną listę naruszeń z klasami.

**Contract**: `normalize_whitespace(text) -> str` (definicja z „Critical Implementation Details”); `validate_assessment(input, output) -> list[Violation]`; `Violation(failure_class, rule, path, message)`; `FailureClass` = `provider_error | timeout | invalid_structured_output | schema_violation | invalid_reference | fabricated_evidence | domain_invariant_violation`. Reguły: unikalność i istnienie ID, brak duplikatów w tablicach referencji, odrzucenie pustego wyniku normalizacji cytatu w anchor i evidence jako `domain_invariant_violation` przed sprawdzeniem podciągu, cytat i anchor jako podciąg wskazanego pola po normalizacji, cytat z nieistniejącego pola lub dokumentu, `explicit` → anchor z `item`, claim ma cytat z `item`, `stance.claim_ids` wskazują claimy z tym celem w `target_ids`, reguły `direction` × `assertiveness` × `claim_ids` z kontraktu V2, stanowisko kierunkowe oparte na claimie autora. Funkcja pomocnicza: surowy JSON → (`AssessmentOutputV2` | naruszenie `invalid_structured_output` / `schema_violation`).

#### 3. Odsiew wpisów bez treści

**File**: `szczur-radar-api/app/assessment/prefilter.py`

**Intent**: Jedna definicja „brak treści” dla testów i produkcji, żeby nie płacić za ocenę wpisów typu zdjęcie + tag.

**Contract**: `is_assessable(input) -> bool` według reguły kontraktu, w tym `<url>` jako zamiennik adresu w próbkach; `no_content_output() -> AssessmentOutputV2` (kształt z „Critical Implementation Details”).

#### 4. Testy

**File**: `szczur-radar-api/tests/assessment/__init__.py`, `test_contract.py`, `test_validation.py`, `test_prefilter.py`, `fixtures/real_posts.json`

**Intent**: Udowodnić offline, że kod i kontrakt mają ten sam kształt oraz że każda reguła ma test pozytywny i negatywny, także na prawdziwych tekstach.

**Contract**: Pozytywne: kompletne wyjścia z `examples.md` przechodzą bez naruszeń. Negatywne, po jednym teście z asercją klasy: zmyślony cytat, cytat ze zmienioną odmianą, cytat ze skrótem „[...]”, brakujący cel, brakujący claim, duplikat ID, anchor `explicit` spoza `item`, claim bez cytatu z `item`, stanowisko kierunkowe bez claimu autora, każda zabroniona kombinacja `direction` × `assertiveness` × `claim_ids`, obce pole, wartość spoza enumu, przekroczony limit, pusty cytat, wejście z pustym tytułem i treścią. Normalizacja: cytat ze zwykłą spacją przechodzi dla pola z twardą spacją i z łamaniem linii. Testy parametryzowane dla anchor i evidence zawierających wyłącznie zwykłe spacje, twarde spacje, łamania linii lub ich mieszankę (w tym `"\u00a0\n"`) oczekują `domain_invariant_violation`, także gdy pole wejścia zawiera identyczne białe znaki. `fixtures/real_posts.json`: zanonimizowane fragmenty z `pilot/` (twarda spacja, „lenny face”, słowa ocenzurowane przez Wykop, emoji, sam tag, sam link oraz tag z samym `<url>`); ostatni przypadek musi być odrzucony przez odsiew.

### Success Criteria:

#### Automated Verification:

- Wszystkie testy backendu przechodzą offline: `cd szczur-radar-api && uv run pytest -q`
- Każda reguła walidatora z listy w `test_validation.py` ma test negatywny z asercją klasy błędu i co najmniej jeden test pozytywny
- Kompletne wyjścia z `examples.md` przechodzą walidator bez naruszeń
- Cytat różniący się od pola wyłącznie białymi znakami przechodzi, a cytat ze zmienioną odmianą lub skrótem „[...]” jest odrzucany jako `fabricated_evidence`
- Wpisy bez treści z `fixtures/real_posts.json` dają `is_assessable == False`, a pozostałe `True`
- Import `app.assessment` nie wymaga żadnej zmiennej środowiskowej

#### Manual Verification:

- Enumy, limity i reguły w `app/assessment/contract.py` i `validation.py` zgadzają się 1:1 z `contract-v2.md` (przegląd obok siebie)

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 3. Kryterium 2.4 dotyczy cytatów niepustych po normalizacji; cytaty złożone wyłącznie z białych znaków są odrzucane zgodnie z regułą minimum 1 znaku po normalizacji.

---

## Phase 3: Dostawcy, prompt i `assess()`

### Overview

Port dostawcy z adapterami LangChain dla Gemini, DeepSeek i OpenAI, prompt V2 z wersją, `assess()` składające odsiew, jedno wywołanie i walidację w wynik z pochodzeniem, testy na sztucznym dostawcy i kontrola na żywo zgodności ze ścieżką z pilotaży.

### Changes Required:

#### 1. Zależności i ochrona kluczy

**File**: `szczur-radar-api/pyproject.toml`, `szczur-radar-api/uv.lock`, `.gitignore`

**Intent**: Dodać integracje LangChain dla trzech dostawców i upewnić się, że lokalne klucze nie trafią do repo.

**Contract**: `uv add langchain langchain-google-genai langchain-deepseek langchain-openai`; `uv.lock` w tym samym commicie. `.gitignore`: `.env`, `.env.*`.

#### 2. Port dostawcy i adaptery

**File**: `szczur-radar-api/app/assessment/providers/__init__.py`, `base.py`, `gemini.py`, `deepseek.py`, `openai.py`

**Intent**: Oddzielić ocenę od dostawcy, żeby wybierać model konfiguracją i testować `assess()` bez sieci; obsłużyć różnice dostawców w jednym miejscu.

**Contract**: `base.py`: protokół `AssessmentProvider.complete(system_prompt, input) -> ProviderResponse`; `ProviderResponse(raw_text | None, provider, model, input_tokens, output_tokens, cached_tokens)`; klasa bazowa na `init_chat_model` + `with_structured_output(..., include_raw=True)`; schemat dla dostawcy wyprowadzany z `AssessmentOutputV2` przez funkcję upraszczającą (bez ograniczeń liczności tablic; dla DeepSeek także bez ograniczeń długości); mapowanie błędów transportu i odmowy na `provider_error`, przekroczenia czasu (`ModelTimeoutError`, `httpx.TimeoutException`) na `timeout` — po wyczerpaniu prób; klient z `max_retries=0`, a wspólna pętla ponowień transportowych i powrotu z flex w klasie bazowej (zasady w „Critical Implementation Details” → Flex i ponowienia); `ProviderResponse` dodatkowo `attempts`, `service_tier`, `flex_fallback`; `temperature=0`. `gemini.py`: metoda `json_schema`, odczyt z `raw.text`. `deepseek.py`: function calling strict z wyłączonym myśleniem, odczyt z `raw.tool_calls[0].args` albo `raw.invalid_tool_calls`. `openai.py`: metoda `json_schema` strict, odczyt z `raw.text`, schemat bez ograniczeń długości i liczności; parametry adaptera: poziom rozumowania, tryb obsługi (`default` | `flex`), opcjonalny `base_url` (OpenRouter, zgodny z OpenAI) z preferencjami dostawcy, limit czasu i liczba prób transportowych. Tryb flex: bezpośrednio u OpenAI przez `ChatOpenAI(service_tier="flex")`; przez OpenRouter przez `extra_body={"provider": {"only": ["openai/flex"], "allow_fallbacks": false}}` (punkty końcowe z sufiksem trybu wymagają jawnego wskazania; `service_tier` przez OpenRouter niepotwierdzony), a powrót z flex przełącza na `["openai"]`. DeepSeek przez OpenRouter: `provider.quantizations` jako lista dozwolona bez fp4 (np. `["fp8", "bf16", "fp16", "fp32"]` — OpenRouter nie ma flagi wykluczenia) i `require_parameters: true`, żeby pominąć dostawców bez wymuszanego schematu (F4). Rejestr `from_spec("provider:model", timeout_s=...)` dla prefiksów `google_genai`, `deepseek` i `openai`; nieznany prefiks → błąd konfiguracji.

#### 3. Prompt V2

**File**: `szczur-radar-api/app/assessment/prompt.py`

**Intent**: Jeden wersjonowany prompt systemowy zgodny z kontraktem V2, oparty na sprawdzonym w pilotażach skróconym prompcie.

**Contract**: `PROMPT_VERSION` i `SYSTEM_PROMPT` (skrócony prompt + reguły V2 + słownik slangu; bez przykładów z zestawu wzorcowego); funkcja budująca wiadomość użytkownika z `input.model_dump_json()` jako niezaufanego materiału. Do modelu trafiają wyłącznie pola wejścia kontraktu. Zmiana treści promptu = nowy `PROMPT_VERSION`.

#### 4. Konfiguracja i `assess()`

**File**: `szczur-radar-api/app/assessment/config.py`, `szczur-radar-api/app/assessment/service.py`

**Intent**: Złożyć odsiew, pojedyncze wywołanie dostawcy i walidację w jeden wynik, który S-02 zapisze bez interpretacji.

**Contract**: `config.py`: leniwy odczyt `ASSESSMENT_MODEL` (domyślnie `DEFAULT_ASSESSMENT_MODEL`, ustawiany w fazie 5) oraz ustawień kanału (tryb obsługi, `base_url`, preferencje dostawcy, limit czasu, liczba ponowień). `service.py`: `prepare_request(input) -> PreparedRequest | AssessmentResult` (odsiew; dla ocenialnego wpisu prompt, wiadomość użytkownika, schemat i wersje), `evaluate_response(input, prepared, provider_response) -> AssessmentResult` (parsowanie, walidacja, klasyfikacja, pochodzenie) oraz `assess(input, provider=None) -> AssessmentResult` składające oba kroki z jednym wywołaniem dostawcy; tryb wsadowy w S-02 może użyć tych samych dwóch kroków. `AssessmentResult(status: "ok" | "failed", output, failure_class, violations, provenance, raw_text)`; `Provenance(source: "model" | "prefilter", provider, model, channel, reasoning_effort, prompt_version, contract_version, assessed_at, input_tokens, output_tokens, cached_tokens, duration_ms, attempts, service_tier, flex_fallback)` — `channel` (np. `openai`, `openrouter`) i `reasoning_effort` zapisują konfigurację, z którą powstała ocena (F3); jakości nie zapisuje się per post. Wpis bez treści → wynik z `source = "prefilter"` bez wywołania dostawcy; pozostałe wpisy → dokładnie jedno wywołanie. Wynik z naruszeniami nigdy nie ma `status = "ok"`.

#### 5. Testy offline

**File**: `szczur-radar-api/tests/assessment/test_providers.py`, `test_service.py`

**Intent**: Udowodnić klasyfikację błędów i liczbę wywołań bez sieci.

**Contract**: Odczyt surowej odpowiedzi każdego adaptera na spreparowanych `AIMessage` (tekst, lista bloków, `tool_calls`, `invalid_tool_calls`, brak JSON); schemat dla dostawcy nie zawiera nieobsługiwanych słów kluczowych. Sztuczny dostawca: poprawny wynik → `ok` z pochodzeniem; każda klasa błędu (`provider_error`, oba typy `timeout`, `invalid_structured_output`, `schema_violation`, `fabricated_evidence`) → `failed` i jedno wywołanie; wpis bez treści → zero wywołań; wiadomość do dostawcy zawiera wyłącznie pola wejścia. Ponowienia (na poziomie adaptera, sztuczny model czatu pod adapterem): 429, potem poprawna odpowiedź → `ok` i `attempts = 2`; więcej niż 3 błędy transportu → `provider_error` po 3 próbach; 3 odmowy flex, potem poprawna odpowiedź w trybie standardowym → `ok`, `service_tier = "default"`, `flex_fallback = true`; odpowiedź naruszająca kontrakt nie jest ponawiana (`attempts = 1`). Na poziomie `assess()` sztuczny dostawca jest wywoływany raz na ocenialny post (wywołanie logiczne). Schemat dla OpenAI nie zawiera ograniczeń długości ani liczności (F5). `evaluate_response` na nagranej surowej odpowiedzi daje ten sam wynik co `assess()` na sztucznym dostawcy zwracającym tę odpowiedź.

#### 6. Kontrola zgodności ze ścieżką z pilotaży (na żywo)

**File**: `szczur-radar-api/scripts/assessment_smoke.py`

**Intent**: Wcześnie wykryć, czy LangChain zachowuje się inaczej niż bezpośrednie wywołania, na których opierają się liczby z pilotaży.

**Contract**: Skrypt jawnie mapuje rekordy `pilot/wykop-gielda-sample-2026-09-29.json` (`sample_id`, `resource`, `created_at`, `title`, `body`) na `AssessmentInputV1` (`entry` → `post`, `link` → `link`), zachowując zanonimizowany tekst i konwertując lokalny czas Wykopu przez `Europe/Warsaw` do RFC 3339 z offsetem. Po `is_assessable` wybiera pierwsze 30 ocenialnych postów i przepuszcza je przez `assess()` dla `google_genai:gemini-3.1-flash-lite` i `deepseek:deepseek-flash`; wypisuje liczbę wejść, odrzuceń przez odsiew i rzeczywistych wywołań jako mianownik zgodności z kontraktem, klasy błędów, tokeny i czas. Klucze z powłoki (`GOOGLE_API_KEY`, `DEEPSEEK_API_KEY`); adapter OpenAI sprawdzany tym samym skryptem z `openai:gpt-6-luna` w trybie flex (bezpośrednio z `OPENAI_API_KEY` albo przez OpenRouter z `OPENROUTER_API_KEY`) — jako pierwszy wybór jest obowiązkowy w tej kontroli; wynik wklejany do opisu PR, nie do repo.

### Success Criteria:

#### Automated Verification:

- Zależności zablokowane i zsynchronizowane: `cd szczur-radar-api && uv sync --locked`
- Wszystkie testy backendu przechodzą offline, bez kluczy API: `uv run pytest -q`
- Każda klasa błędu ma test na sztucznym dostawcy, a ocenialny wpis wywołuje dostawcę dokładnie raz
- Wpis bez treści daje wynik z `source = "prefilter"` i zero wywołań dostawcy
- Schemat wysyłany do Gemini nie zawiera `minItems`/`maxItems`, a do DeepSeek także `minLength`/`maxLength`
- Import `app.assessment` nie wymaga żadnej zmiennej środowiskowej
- Ponawiane są wyłącznie błędy transportu (429, 5xx, przekroczenie czasu); odpowiedź naruszająca kontrakt kończy się jednym wywołaniem
- `evaluate_response` na surowej odpowiedzi daje wynik identyczny z `assess()` na sztucznym dostawcy zwracającym tę samą odpowiedź

#### Manual Verification:

- `assessment_smoke.py` na 30 ocenialnych postach (30 wywołań na model): dla obu modeli zgodność z kontraktem ≥90% i najwyżej jedno `provider_error` lub `invalid_structured_output` na model; wynik z licznikiem odrzuceń i mianownikiem w opisie PR
- Wypełnione tokeny, wersja promptu i wersja kontraktu w pochodzeniu dla obu dostawców

**Implementation Note**: Kryterium 3.3 dotyczy wywołań logicznych (`complete()` raz na ocenialny post); próby transportowe są liczone osobno (F1). Kryterium 3.6 obejmuje także schemat wysyłany do OpenAI — bez ograniczeń długości i liczności (F5). Kryterium 3.10 obejmuje powrót z flex po 3 odmowach (F2). Kryteria 3.8 i 3.9 obejmują także `openai:gpt-6-luna` w trybie flex (pierwszy wybór, decyzja 2026-10-01); w opisie PR podaj dla niej również odsetek odmów 429 i medianę czasu. Jeśli kontrola na żywo nie spełnia progu dla któregoś dostawcy, zatrzymaj się i przedstaw użytkownikowi przyczynę oraz opcje (poprawka adaptera albo zmiana warstwy integracji dla tego dostawcy), zanim przejdziesz do fazy 5.

---

## Phase 4: Zestaw wzorcowy

### Overview

Świeża, zanonimizowana próbka ~120 postów z dwóch dni, arkusz z instrukcją, etykiety użytkownika zapisane w repo oraz funkcje liczące miary względem wzorca. Przygotowanie arkusza zależy od akceptacji kontraktu w fazie 1; pobranie i przygotowanie próbki wymaga `AssessmentInputV1` i `is_assessable` z fazy 2. Oznaczanie próbki może iść równolegle z fazą 3.

### Changes Required:

#### 1. Pobranie i anonimizacja próbki

**File**: `context/changes/assessment-contract-harness/pilot/scripts/fetch_wykop_sample.py`, `szczur-radar-api/tests/assessment/gold/posts.json`

**Intent**: Mieć próbkę niezależną od postów, z których powstały reguły (2026-09-28 i próbka z 2026-09-29 są wykluczone).

**Contract**: Skrypt przyjmuje daty, pobiera `#gielda` przez Wykop API v3 (klucze z powłoki), pomija wpisy nieoceniane według `is_assessable`, losuje po ~60 na dzień ze stałym ziarnem, anonimizuje (nicki po `@` i nicki autorów → pseudonimy, URL → `<url>`, białe znaki bez zmian) i zapisuje `posts.json` jako listę `{post_id, day, input: AssessmentInputV1}`. Modele są później uruchamiane na tym zanonimizowanym tekście.

#### 2. Arkusz oznaczania

**File**: `context/changes/assessment-contract-harness/pilot/labeling-gold.html`

**Intent**: Pozwolić użytkownikowi oznaczyć rdzeń szybko i zgodnie z instrukcją, bez podglądu odpowiedzi modeli.

**Contract**: Na bazie `pilot/labeling-r2.html`: skrót instrukcji v1.0, trafność, cele z zaznaczenia tekstu, kierunek, pewność stanowiska (pewne/niepewne), flaga „trudny”, notatka; zapis w przeglądarce; eksport `labels.json`.

#### 3. Wzorzec i miary

**File**: `szczur-radar-api/tests/assessment/gold/labels.json`, `szczur-radar-api/tests/assessment/gold_metrics.py`, `szczur-radar-api/tests/assessment/test_gold.py`

**Intent**: Jedna, przetestowana definicja miar, żeby progi znaczyły to samo w testach i w raporcie.

**Contract**: `labels.json`: etykiety użytkownika (`post_id`, `relevance`, `targets[{anchor, direction, hedged}]`, `hard`, `note`). `gold_metrics.py`: `contract_compliance`, `relevance_binary_accuracy` (posty ze wzorcowym `unclear` wyłączone), dopasowanie celów 1:1 na podstawie cytatów `anchor` po normalizacji białych znaków i wielkości liter oraz usunięciu początkowego `#` (równość albo zawieranie całego fragmentu z granicami słowa według reguły poniżej; wybierz dopasowanie o największej liczbie par, remisy rozstrzygnij kolejnością celów we wzorcu i odpowiedzi), `target_recall` (liczba dopasowanych celów / liczba celów we wzorcu), `target_precision` (liczba dopasowanych celów / liczba celów w odpowiedziach), `target_direction_accuracy` (poprawny `direction` wśród dopasowanych celów, których wzorcowy kierunek nie jest `not_expressed`), `dominant_direction(output | label)` (więcej `bullish` niż `bearish` → `bullish`, odwrotnie → `bearish`, inaczej → brak), `dominant_direction_agreement`, `contradiction_rate` (`bullish` ↔ `bearish`). Miary celów liczone globalnie z postów nie-„trudnych”; brak odpowiedzi lub wynik nieważny daje zero dopasowań, a brak niepustego mianownika we wzorcu dla miary kierunku jest błędem pomiaru, nie zaliczeniem progu. Posty „trudne” wyłączone z mianowników; stałe progów `95 / 80 / 80 / 70 / 75 / 75 / 3` odpowiednio dla wymienionych miar. `test_gold.py`: spójność wzorca (każdy `post_id` istnieje, każdy anchor jest podciągiem posta po normalizacji, brak nicków i URL, ≥100 postów nie-„trudnych”, dwa różne dni) i testy miar na danych syntetycznych, w tym modelu zwracającego zawsze `targets=[]`.

**Granice dopasowania anchorów**: Oba znormalizowane anchory muszą być niepuste. Zawieranie jest symetryczne: krótszy fragment może wystąpić w dłuższym tylko wtedy, gdy bezpośrednio przed nim i po nim jest początek/koniec tekstu albo znak spoza Unicode `\w` (litery, cyfry i `_`). Odpowiednik w Pythonie: `re.search(r"(?<!\w)" + re.escape(shorter) + r"(?!\w)", longer)` bez `re.ASCII`. Fragment dopasowuje się dosłownie, wraz z wewnętrzną interpunkcją; reguła nie usuwa znaków wewnątrz nazwy. `test_gold.py` sprawdza w obu kolejnościach: `AI` ↔ `Taiwan` i `XTB` ↔ `XTBR` dają zero dopasowań; `CD Projekt` ↔ `akcje CD Projekt`, `#XTB` ↔ `xtb`, `XTB` ↔ `(XTB)` i `XTB` ↔ `$XTB` dają dopasowanie; pusty anchor po normalizacji nie daje dopasowania. Przed pomiarem na wzorcu sprawdź regułę na anchorach z pilotaży.

**Puste mianowniki**: Gdy wzorzec ma cele, lecz model nie zwraca żadnego, `target_precision = 0` i `target_recall = 0`. Gdy wzorzec ma cele ze stanowiskiem, lecz żaden nie jest dopasowany, `target_direction_accuracy = 0`. Jeśli sam wzorzec nie ma celów albo celów ze stanowiskiem, `test_gold.py` zgłasza niewystarczający wzorzec zamiast zaliczać próg.

### Success Criteria:

#### Automated Verification:

- Testy spójności wzorca i miar przechodzą: `cd szczur-radar-api && uv run pytest -q tests/assessment/test_gold.py`
- Wzorzec ma co najmniej 100 postów nie-„trudnych” z dwóch różnych dni, żadnego z 2026-09-28 ani z próbki 2026-09-29
- `posts.json` i `labels.json` nie zawierają nicków autorów ani adresów URL (test)
- Każda miara ma test na danych syntetycznych z ręcznie policzonym wynikiem

#### Manual Verification:

- Użytkownik oznaczył wszystkie posty w arkuszu według instrukcji v1.0 i wyeksportował `labels.json`
- Użytkownik przejrzał notatki i posty „trudne”; reguły, których zabrakło, zostały dopisane do `contract-v2.md` i instrukcji (z aktualizacją dotkniętych etykiet) albo świadomie odłożone

**Implementation Note**: Oznaczanie to praca użytkownika (szacunkowo godzina lub więcej); można je rozłożyć na sesje. Zmiana kontraktu wynikła z notatek wymaga powrotu do odpowiednich kroków faz 1–2.

---

## Phase 5: Pomiar, raport i model domyślny

### Overview

Jeden przebieg na żywo (na żądanie) dwóch tanich kandydatów i Luny jako punktu odniesienia, nagrania odtwarzane offline w CI, podstawowy raport jakości i kosztu oraz wybór modelu domyślnego.

### Changes Required:

#### 1. Przebieg na żywo i nagrania

**File**: `szczur-radar-api/tests/assessment/live_models.toml`, `szczur-radar-api/tests/assessment/test_live.py`, `szczur-radar-api/pyproject.toml`, `szczur-radar-api/tests/assessment/recordings/`

**Intent**: Uruchamiać modele na wzorcu tylko na żądanie i zapisywać każdą odpowiedź jako nagranie.

**Contract**: `live_models.toml`: wpisy `{spec, role: "preferred" | "fallback" | "reference", channel, price_in, price_cached_in, price_out}` — pierwszy wybór `openai:gpt-6-luna` w trybie flex, zapas `deepseek:deepseek-flash`, punkt odniesienia `google_genai:gemini-3.1-flash-lite` (decyzja użytkownika 2026-10-01); `channel` opisuje kanał i cennik użyty w pomiarze. Każdy wpis utrwala pełną konfigurację wywołania (kanał, tryb obsługi, poziom rozumowania — dla Luny domyślnie jak w pilocie 8), która trafia do każdego nagrania; `DEFAULT_ASSESSMENT_MODEL` wskazuje dokładnie zmierzoną konfigurację, a każda jej zmiana wymaga nowego pomiaru na wzorcu (F3). `pyproject.toml`: marker `live` obok `db`, `addopts = "-m 'not live'"` (testy `db` nadal działają w CI). `test_live.py`: każdy z trzech modeli × cały wzorzec, jedno uruchomienie; brak klucza → `skip` z nazwą zmiennej; zmienna `ASSESSMENT_LIVE_MODELS` zawęża listę; testy tylko nagrywają. Nagranie: `recordings/<provider>__<model>/<post_id>.json` = `{post_id, spec, channel, reasoning_effort, prompt_version, contract_version, recorded_at, raw_text, input_tokens, output_tokens, cached_tokens, duration_ms, attempts, service_tier, flex_fallback, status, failure_class, violations}`.

#### 2. Odtwarzanie nagrań i progi w CI

**File**: `szczur-radar-api/tests/assessment/test_recordings.py`, `szczur-radar-api/app/assessment/config.py`

**Intent**: Zamienić prawdziwe odpowiedzi w test regresyjny walidatora i progów, działający bez sekretów.

**Contract**: Nagrania do odtwarzania i pomiaru są wybierane według bieżących `PROMPT_VERSION` i `CONTRACT_VERSION` oraz konfiguracji wywołania z `live_models.toml` (kanał, tryb, poziom rozumowania; F3); starsze wersje nie trafiają do bieżącego pomiaru. Dla każdego bieżącego nagrania z `raw_text`: ponowne parsowanie i walidacja daje zapisane `status` i `failure_class`. Dla `DEFAULT_ASSESSMENT_MODEL`: komplet nagrań dla wszystkich postów wzorca z bieżącymi `PROMPT_VERSION` i `CONTRACT_VERSION` oraz miary z `gold_metrics.py` spełniają progi: zgodność 95%, trafność 80%, recall celów 80%, precision celów 70%, kierunek na dopasowanych celach ze stanowiskiem 75%, dominujący kierunek posta 75%, sprzeczności najwyżej 3%. Brak lub nieważna odpowiedź liczy się jako błąd w miarach jakości, nie znika z mianownika. `test_recordings.py` sprawdza również kwalifikowanie zestawów do tabeli porównawczej: starszy prompt, starszy kontrakt i brak nagrania dla posta wzorca czynią zestaw nieporównywalnym; kompletny zestaw bieżących nagrań zawierający wyniki `failed` pozostaje porównywalny, a błędy obniżają miary jakości.

#### 3. Raport jakości i kosztu

**File**: `szczur-radar-api/scripts/assessment_report.py`, `context/changes/assessment-contract-harness/reports/gold-<YYYY-MM-DD>.md`

**Intent**: Dać użytkownikowi liczby potrzebne do wyboru modelu dla S-02 bez kolejnej rundy analizy kaskady.

**Contract**: `uv run python scripts/assessment_report.py --recordings tests/assessment/recordings --gold tests/assessment/gold --models tests/assessment/live_models.toml --out <ścieżka>`. Raport Markdown: jedna tabela dla trzech modeli (z rolą każdego) z liczbą ocenionych postów, zgodnością z kontraktem, trafnością binarną, recall i precision celów, kierunkiem na dopasowanych celach ze stanowiskiem, dominującym kierunkiem posta, sprzecznymi kierunkami, naruszeniami wg klasy, tokenami, kosztem na post i na 12k postów oraz medianą czasu; dla Luny flex także odsetek odmów 429, liczbę ponowień oraz liczbę i koszt postów ocenionych po powrocie do trybu standardowego (`flex_fallback`); koszt miesięczny w rozbiciu na kanały (Luna: zwykły i flex; DeepSeek: bezpośrednio w szczycie i poza nim oraz OpenRouter z przypiętym dostawcą; Gemini: bezpośrednio); zaznacza spełnienie każdego progu, wersje promptu i kontraktu oraz datę. Model trafia do tabeli porównawczej tylko wtedy, gdy ma dokładnie jedno nagranie dla każdego posta z bieżącego `gold/posts.json`, z bieżącymi `PROMPT_VERSION` i `CONTRACT_VERSION`; wszystkie modele są więc mierzone na tym samym zestawie postów. Nagrania starszych wersji są wykluczone z tabeli, a model z niekompletnym zestawem jest jawnie oznaczony jako nieporównywalny wraz z listą brakujących `post_id`; raport nie zastępuje braków starszymi nagraniami ani nie zawęża wzorca do części wspólnej. Nagrane błędy dostawcy i nieważne odpowiedzi są uwzględniane jako błędy jakości zgodnie z `gold_metrics.py`. Raport nie uruchamia dodatkowych modeli.

#### 4. Wybór modelu

**File**: `szczur-radar-api/app/assessment/config.py`, `context/changes/assessment-contract-harness/reports/`, `context/foundation/roadmap.md`

**Intent**: Zamknąć F-02 decyzją opartą na raporcie.

**Contract**: Po jednym pełnym przebiegu użytkownik wybiera `DEFAULT_ASSESSMENT_MODEL` według ról (decyzja użytkownika 2026-10-01): Luna w trybie flex, jeśli spełnia progi; w przeciwnym razie DeepSeek Flash, jeśli spełnia progi; Gemini 3.1 Flash-Lite jest punktem odniesienia, nie kandydatem na model domyślny. Wybór wymaga porównywalnych zestawów wszystkich trzech modeli według reguły raportu; po zmianie promptu lub kontraktu trzeba uzupełnić bieżące nagrania wszystkich trzech modeli przed decyzją. Jeśli ani Luna, ani DeepSeek nie spełnia progów, raport pokazuje odstępstwa i użytkownik podejmuje jedną jawną decyzję: poprawić prompt i powtórzyć pomiar albo zaakceptować konkretny kompromis jakościowy przez zmianę wskazanych progów w `gold_metrics.py` oraz kryterium 5.3; bez tej decyzji F-02 nie jest ukończone. Zmienione progi i uzasadnienie są w raporcie, a CI sprawdza zaakceptowane wartości. W `roadmap.md` pozycja „Unknowns” F-02 (wybór dostawcy i koszt) jest uzupełniona wynikiem; status F-02 zmienia dopiero `/10x-implement` i `/10x-archive`.

### Success Criteria:

#### Automated Verification:

- Domyślne `uv run pytest -q` pomija testy `live`, uruchamia testy `db` i odtwarza wszystkie nagrania z identycznym werdyktem
- Model domyślny ma komplet nagrań dla wszystkich postów wzorca z bieżącym `PROMPT_VERSION`
- Miary modelu domyślnego na wzorcu spełniają progi: zgodność z kontraktem ≥95%, trafność binarna ≥80%, recall celów ≥80%, precision celów ≥70%, kierunek na dopasowanych celach ze stanowiskiem ≥75%, dominujący kierunek posta ≥75%, sprzeczne kierunki ≤3% (test w `test_recordings.py`)
- Raport generuje się bez błędów poleceniem z punktu 3 do `context/changes/assessment-contract-harness/reports/`
- CI na PR (`backend` + `web-container-smoke`) jest zielone

#### Manual Verification:

- Przebieg `uv run pytest -m live` wykonany dla obu kandydatów i `gpt-6-luna` na całym wzorcu; raport zawiera jedną tabelę jakości i kosztu tych trzech modeli
- Użytkownik przejrzał raport i zatwierdził model domyślny oraz szacowany koszt miesięczny
- Jeśli progi zostały zmienione albo żaden kandydat ich nie spełnił, decyzja użytkownika i jej uzasadnienie są w raporcie

**Implementation Note**: Role modeli zmieniła decyzja użytkownika 2026-10-01 (Luna flex — pierwszy wybór, DeepSeek Flash — zapas, Gemini 3.1 Flash-Lite — punkt odniesienia); kryteria 5.6, 5.7 i 5.9 czyta się według tych ról: „oba kandydaci” = Luna i DeepSeek, „żaden kandydat” = ani Luna, ani DeepSeek. Kryterium 5.1 oznacza odtwarzanie wszystkich nagrań bieżących wersji promptu i kontraktu; starsze nagrania są wykluczone zgodnie z regułą wyboru nagrań. Kryterium 5.2 wymaga także bieżącego `CONTRACT_VERSION`, a tabela z kryterium 5.6 — kompletnych, porównywalnych zestawów wszystkich trzech modeli. Po fazie 5 PR z całą zmianą jest gotowy; merge do `main` wykonuje użytkownik.

---

## Testing Strategy

### Unit Tests:

- Modele: obce pola, enumy, wymagane klucze, limity w znakach Unicode, walidacja wejścia.
- Walidator: każda reguła V2 pozytywnie i negatywnie; normalizacja białych znaków na prawdziwych fragmentach (twarda spacja, łamania linii, „lenny face”, ocenzurowane słowa).
- Odsiew: wpisy z samym tagiem, samym linkiem, samą wzmianką.
- `assess()`: klasy błędów, liczba wywołań, pochodzenie, tylko pola wejścia w wiadomości.
- Miary względem wzorca na danych syntetycznych.

### Integration Tests:

- `-m live`: Luna (flex), DeepSeek Flash i Gemini 3.1 Flash-Lite × cały wzorzec (na żądanie, lokalnie, z kluczami w powłoce).
- Odtwarzanie nagrań w CI: regresja walidatora i progi modelu domyślnego.

### Manual Testing Steps:

1. Wyeksportuj klucze w powłoce (`GOOGLE_API_KEY`, `DEEPSEEK_API_KEY`, `OPENAI_API_KEY` albo `OPENROUTER_API_KEY`, do pobrania próbki `WYKOP_KEY` / `WYKOP_SECRET`); nigdy do repo ani do Railway.
2. Faza 3: `cd szczur-radar-api && uv run python scripts/assessment_smoke.py`.
3. Faza 4: pobierz próbkę skryptem, oznacz w `labeling-gold.html`, zapisz `labels.json`.
4. Faza 5: `uv run pytest -m live`, potem podstawowy raport; przejrzyj tabelę, wybierz model według ról, ustaw `DEFAULT_ASSESSMENT_MODEL`, uruchom `uv run pytest -q`.

## Performance Considerations

Jedno wywołanie na ocenialny post; wpisy bez treści bez wywołania (~8% postów w pilotażach). Szacunek z pilotaży przy 12k postów miesięcznie i skróconym prompcie (ceny z katalogu OpenRouter i cennika DeepSeek, odczyt 2026-09-30): deepseek-flash ~$2–8 zależnie od kanału i pory (poza szczytem połowa stawki), gpt-6-luna ~$6 z domyślnym rozumowaniem, w trybie flex ~$3, gemini-3.1-flash-lite ~$10; ~1,3–2,2k tokenów wejścia na post, z czego większość to stały prompt objęty cache. Przebieg pomiarowy fazy 5 to ~120 postów × 3 modele na całym wzorcu — poniżej $1 według szacunku z pilotaży. Koszt rzeczywisty jest mierzony z `usage` i raportowany; to wejście do Open Roadmap Question 5 (budżet LLM).

## Migration Notes

Brak zmian danych i infrastruktury. Nowe zależności Pythona zwiększają obraz API; kod oceny nie jest wywoływany przez żadną trasę, więc deploy po merge nie zmienia zachowania usługi. Przeniesienie `contract-v1.md` do archiwum zmienia ścieżkę pliku: dokumenty foundation są poprawiane w fazie 1, a odwołania w `frame.md`, `research.md` i archiwalnych zmianach pozostają jako zapis historyczny.

## References

- Brief ramowy i dziennik pilotaży: `context/changes/assessment-contract-harness/frame.md`
- Badanie: `context/changes/assessment-contract-harness/research.md`
- Instrukcja oznaczania: `context/changes/assessment-contract-harness/pilot/labeling-guidelines.md`
- Prompty i skrypty pilotażowe: `context/changes/assessment-contract-harness/pilot/scripts/` (`prompt-short.md`, `prompt-v2-rules.md`, `pilot.py`, `pilot6.py`, `pilot7.py`, `pilot8.py`, `frame2_checks.py`)
- Kontrakt V1 (do archiwizacji): `context/foundation/llm-assessment/contract-v1.md`
- Roadmapa: `context/foundation/roadmap.md` (F-02, S-02, S-03); PRD: `context/foundation/prd.md` (FR-004, `:258`)
- Lekcje: `context/foundation/lessons.md`
- Wzorce w kodzie: `szczur-radar-api/app/database.py:47`, `szczur-radar-api/app/auth/tokens.py:34`, `szczur-radar-api/pyproject.toml` (marker `db`)
- Poprzedni przegląd planu (dotyczy wersji 1): `context/changes/assessment-contract-harness/reviews/plan-review.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Kontrakt V2 i dokumenty

#### Automated

- [x] 1.1 `contract-v2.md` istnieje, a `contract-v1.md` jest tylko w archiwum: `test -f context/foundation/llm-assessment/contract-v2.md && ! test -f context/foundation/llm-assessment/contract-v1.md && ls context/foundation/archive/*llm-assessment-contract-v1.md`
- [x] 1.2 Dokumenty foundation poza archiwum nie odwołują się do ścieżki V1: `rg -n 'contract-v1\.md' context/foundation --glob '*.md' --glob '!**/archive/**'` zwraca kod 1 (brak trafień)
- [x] 1.3 W folderze zmiany nie ma kluczy API: `grep -rEn "sk-or-v1-[0-9a-f]{20}|sk-[0-9a-f]{32}|AQ\.Ab8|w5[0-9a-f]{30}" context/changes/assessment-contract-harness` nie zwraca nic
- [x] 1.4 Grafika roadmapy jest zregenerowana w tym samym commicie co `roadmap.md`: `git diff --name-only main -- docs/visuals/roadmap-mvp.png tools/render_roadmap.swift` zwraca oba pliki
- [x] 1.5 Testy backendu nadal przechodzą: `cd szczur-radar-api && uv run pytest -q`

#### Manual

- [x] 1.6 Użytkownik przeczytał i zaakceptował tekst `contract-v2.md` (kształt wyjścia, definicja celu, próg stanowiska, reguła cytatu, reguła „brak treści”)
- [x] 1.7 Użytkownik potwierdził zmienione sformułowania w `prd.md` i `roadmap.md` (w tym opis S-03)
- [x] 1.8 Każdy przykład w `examples.md` ma kompletne wyjście i ręcznie sprawdzone cytaty względem swojego wejścia

### Phase 2: Kontrakt jako kod

#### Automated

- [ ] 2.1 Wszystkie testy backendu przechodzą offline: `cd szczur-radar-api && uv run pytest -q`
- [ ] 2.2 Każda reguła walidatora z listy w `test_validation.py` ma test negatywny z asercją klasy błędu i co najmniej jeden test pozytywny
- [ ] 2.3 Kompletne wyjścia z `examples.md` przechodzą walidator bez naruszeń
- [ ] 2.4 Cytat różniący się od pola wyłącznie białymi znakami przechodzi, a cytat ze zmienioną odmianą lub skrótem „[...]” jest odrzucany jako `fabricated_evidence`
- [ ] 2.5 Wpisy bez treści z `fixtures/real_posts.json` dają `is_assessable == False`, a pozostałe `True`
- [ ] 2.6 Import `app.assessment` nie wymaga żadnej zmiennej środowiskowej

#### Manual

- [ ] 2.7 Enumy, limity i reguły w `app/assessment/contract.py` i `validation.py` zgadzają się 1:1 z `contract-v2.md` (przegląd obok siebie)

### Phase 3: Dostawcy, prompt i `assess()`

#### Automated

- [ ] 3.1 Zależności zablokowane i zsynchronizowane: `cd szczur-radar-api && uv sync --locked`
- [ ] 3.2 Wszystkie testy backendu przechodzą offline, bez kluczy API: `uv run pytest -q`
- [ ] 3.3 Każda klasa błędu ma test na sztucznym dostawcy, a ocenialny wpis wywołuje dostawcę dokładnie raz
- [ ] 3.4 Wpis bez treści daje wynik z `source = "prefilter"` i zero wywołań dostawcy
- [ ] 3.6 Schemat wysyłany do Gemini nie zawiera `minItems`/`maxItems`, a do DeepSeek także `minLength`/`maxLength`
- [ ] 3.7 Import `app.assessment` nie wymaga żadnej zmiennej środowiskowej
- [ ] 3.10 Ponawiane są wyłącznie błędy transportu (429, 5xx, przekroczenie czasu); odpowiedź naruszająca kontrakt kończy się jednym wywołaniem
- [ ] 3.11 `evaluate_response` na surowej odpowiedzi daje wynik identyczny z `assess()` na sztucznym dostawcy zwracającym tę samą odpowiedź

#### Manual

- [ ] 3.8 `assessment_smoke.py` na 30 ocenialnych postach (30 wywołań na model): dla obu modeli zgodność z kontraktem ≥90% i najwyżej jedno `provider_error` lub `invalid_structured_output` na model; wynik z licznikiem odrzuceń i mianownikiem w opisie PR
- [ ] 3.9 Wypełnione tokeny, wersja promptu i wersja kontraktu w pochodzeniu dla obu dostawców

### Phase 4: Zestaw wzorcowy

#### Automated

- [ ] 4.1 Testy spójności wzorca i miar przechodzą: `cd szczur-radar-api && uv run pytest -q tests/assessment/test_gold.py`
- [ ] 4.2 Wzorzec ma co najmniej 100 postów nie-„trudnych” z dwóch różnych dni, żadnego z 2026-09-28 ani z próbki 2026-09-29
- [ ] 4.3 `posts.json` i `labels.json` nie zawierają nicków autorów ani adresów URL (test)
- [ ] 4.4 Każda miara ma test na danych syntetycznych z ręcznie policzonym wynikiem

#### Manual

- [ ] 4.5 Użytkownik oznaczył wszystkie posty w arkuszu według instrukcji v1.0 i wyeksportował `labels.json`
- [ ] 4.6 Użytkownik przejrzał notatki i posty „trudne”; reguły, których zabrakło, zostały dopisane do `contract-v2.md` i instrukcji (z aktualizacją dotkniętych etykiet) albo świadomie odłożone

### Phase 5: Pomiar, raport i model domyślny

#### Automated

- [ ] 5.1 Domyślne `uv run pytest -q` pomija testy `live`, uruchamia testy `db` i odtwarza wszystkie nagrania z identycznym werdyktem
- [ ] 5.2 Model domyślny ma komplet nagrań dla wszystkich postów wzorca z bieżącym `PROMPT_VERSION`
- [ ] 5.3 Miary modelu domyślnego na wzorcu spełniają progi: zgodność z kontraktem ≥95%, trafność binarna ≥80%, recall celów ≥80%, precision celów ≥70%, kierunek na dopasowanych celach ze stanowiskiem ≥75%, dominujący kierunek posta ≥75%, sprzeczne kierunki ≤3% (test w `test_recordings.py`)
- [ ] 5.4 Raport generuje się bez błędów poleceniem z punktu 3 do `context/changes/assessment-contract-harness/reports/`
- [ ] 5.5 CI na PR (`backend` + `web-container-smoke`) jest zielone

#### Manual

- [ ] 5.6 Przebieg `uv run pytest -m live` wykonany dla obu kandydatów i `gpt-6-luna` na całym wzorcu; raport zawiera jedną tabelę jakości i kosztu tych trzech modeli
- [ ] 5.7 Użytkownik przejrzał raport i zatwierdził model domyślny oraz szacowany koszt miesięczny
- [ ] 5.9 Jeśli progi zostały zmienione albo żaden kandydat ich nie spełnił, decyzja użytkownika i jej uzasadnienie są w raporcie
