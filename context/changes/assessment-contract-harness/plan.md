# Kontrakt oceny LLM V2 z walidatorem i pomiarem na wzorcu — plan implementacji

> Wersja 3 planu (2026-10-03): fazy 2–6 przeplanowane na backend w Javie (Spring Boot 4, Embabel 1.5.2, Spring AI 2.0.1) po F-03 (`java-api-rewrite`). Faza 1 (kontrakt V2 i dokumenty) jest zrobiona i zostaje bez zmian. Wersja 2 (2026-09-30, Python/LangChain) i jej przeglądy (`reviews/plan-review.md`, `reviews/plan-review-2026-10-01.md`) są historią; decyzje produktowe z nich (role modeli, progi, wzorzec, flex) obowiązują nadal. Podstawa: `frame.md`, `research.md` (w tym „Follow-up 2026-10-03”). Implementacja Pythonowa faz 2–4 leży na gałęzi `feature/assessment-contract-harness_old` (zamknięty PR #23) — nie jest przenoszona; może służyć wyłącznie jako materiał porównawczy (np. tekst promptu, wektory testowe).

## Overview

Roadmapa F-02 (`assessment-contract-harness`). Kontrakt V2 (`context/foundation/llm-assessment/contract-v2.md`, accepted) zostaje zapisany jako kod w Javie: ścisły model wejścia i wyjścia, walidator reguł D1–D15 zbudowany na wzorcu strategii, odsiew wpisów bez treści. Ocena posta to jedno wywołanie modelu przez własnych klientów Spring AI (Gemini, DeepSeek, OpenRouter), z pominięciem `createObject` Embabel, a Embabel dostaje cienkiego agenta `AssessorAgent` dla S-02. Zestaw wzorcowy (~120 postów oznaczonych przez użytkownika) i nagrania odpowiedzi trzech modeli — GPT-6 Luna flex (pierwszy wybór), DeepSeek Flash (zapas), Gemini 3.1 Flash-Lite (punkt odniesienia) — wybierają model domyślny dla S-02; CI sprawdza progi offline na nagraniach.

## Current State Analysis

- **Backend (F-03, na `main`).** `szczur-radar-api/` — Spring Boot 4.0.8, Java 25, Maven; pakiety `health`, `user`, `auth`, `cli`, `config`, `agents` (`SkeletonAgent` bez wywołań LLM). Zależności Embabel 1.5.2 + startery google-genai, deepseek, openai-custom; Spring AI 2.0.1; Jackson 3.1.5; networknt `json-schema-validator` 3.0.1 tranzytywnie (`research.md`, sekcja F).
- **Konfiguracja LLM.** Jeden dostawca na uruchomienie: `LLM_API_PROVIDER` (`gemini` | `deepseek` | `openrouter`), `LLM_MODEL`, `LLM_API_KEY`; walidacja w `config/LlmProviderProperties.java:26-89`, błąd przy starcie przez `LlmConfigurationCheck`; profile `application-<provider>.properties` mapują je na Embabel i wyłączają pozostałych dostawców (`application.properties:36-46`). Każdy kontekst Springa wymaga tych zmiennych; testy `*Test` (bez Springa) — nie.
- **Testy i CI.** `*Test` (Surefire, bez Springa/Dockera) i `*IT` (Failsafe, Testcontainers Postgres 18) (`pom.xml:145-174`). CI `backend` wymaga ustawień LLM, ale „nothing calls the LLM in CI” (`.github/workflows/ci.yml:26-52`). Wzorzec testu klienta na lokalnym serwerze HTTP: `config/LlmProviderConfigTest.java:72-110`. Folder `src/test/resources/fixtures/<service>/` (reguła `REPOSITORY_GUIDELINES.md`) jeszcze nie istnieje.
- **Embabel/Spring AI — ustalenia z badania** (`research.md`, sekcje E–G): `createObject` dokleja schemat do promptu, parsuje pobłażliwie i domyślnie ponawia całe wywołanie do 10× także po błędzie parsowania; `Usage` bez tokenów cache. Ponowienia w warstwach: data-binding Embabel (10), `RetryTemplate` modelu (Gemini 10, DeepSeek 4), SDK (google-genai 5, openai-java 2). Gemini: `minItems`/`maxItems` dochodzą do API (HTTP 400 w pilocie), klient Embabel bez timeoutu. DeepSeek w Spring AI: tylko `JSON_OBJECT`; `prompt_cache_hit_tokens` gubione. OpenRouter przez openai-java: `extraBody` (routing `openai/flex`), `responseFormat` JSON_SCHEMA strict, cache w `getCacheReadInputTokens()`. Jackson 3 domyślnie przepuszcza obce i brakujące pola; `@Size` liczy UTF-16; `contract-v2.md:290` definiuje biały znak przez Pythonowe `str.isspace()`.
- **Dowody z pilotaży** (`frame.md`): tanie modele 95–98% zgodności z kontraktem; odrzucenia cytatów to głównie twarda spacja; Luna (pilot 8) 98% i zero przerobionych cytatów; paczkowanie zmienia co trzecią ocenę. Wcześniejszy smoke (2026-10-01, 30 postów, prompt v2.2): Gemini 100%, DeepSeek w JSON mode 93,3% (strict tool calling 83,3%), Luna flex 96,7%; DeepSeek gubił nawiasy, gdy obiekt kończył się obiektem zagnieżdżonym.
- **Railway.** `.railway/railway.ts:94-100` deklaruje `LLM_*` jako `preserve()`; niezadeklarowana zmienna ustawiona ręcznie daje w planie `Delete variable`, który bramka CI odrzuca.

## Desired End State

- `contract-v2.md` §10 definiuje białe znaki jawną listą znaków (bez odwołania do Pythona); `agent-implementation.md` nie zawiera sformułowań Pythonowych.
- `src/main/java/pl/szczurradar/api/`:
  - `agents/assessor/` — rekordy i enumy kontraktu, schemat JSON wyjścia V2 jako zasób, ścisły parser, walidator D1–D15 jako zestaw strategii, odsiew §11, prompt z wersją, `Assessor` (przygotowanie żądania, ocena odpowiedzi, `assess`) i `AssessorAgent` (Embabel) zamiast `SkeletonAgent`;
  - `llm/` — port `LlmClient` i klienci Gemini, DeepSeek, OpenRouter z wyłączonymi ponowieniami frameworka, własnymi 3 próbami transportowymi, powrotem z flex i odczytem zużycia;
  - `sources/wykop/` — mapper Wykop → `AssessmentInputV1`.
- `src/test/java/pl/szczurradar/api/measurement/` (kod tylko testowy, nigdy w jarze) — kontrola na żywo, pobranie próbki, nagrywanie, miary wzorca, raport; `README.md` opisuje uruchamianie. `CLAUDE.md` w `agents/assessor/` i `llm/`.
- `src/test/resources/fixtures/`: prawdziwe payloady dostawców, wzorzec (`assessor/gold/posts.json`, `labels.json`), nagrania trzech modeli (`llm/recordings/…`), `llm/models.json` z trzema zmierzonymi konfiguracjami i wskazaniem domyślnej.
- `./mvnw -B verify` (CI, bez wywołań LLM) sprawdza kontrakt, walidator, klientów na lokalnym serwerze HTTP, `Assessor`, agenta, miary oraz odtwarza nagrania z progami dla modelu domyślnego: zgodność z kontraktem ≥95%, trafność binarna ≥80%, recall celów ≥80%, precision celów ≥70%, kierunek na dopasowanych celach ze stanowiskiem ≥70%, dominujący kierunek posta ≥75%, sprzeczne kierunki ≤10% (kierunek i sprzeczności złagodzone z ≥75% / ≤3% decyzją użytkownika 2026-10-06, raport `gold-2026-10-06.md`).
- Raport `context/changes/assessment-contract-harness/reports/gold-<YYYY-MM-DD>.md` z jakością i kosztem trzech modeli; użytkownik zatwierdził model domyślny; runbook ustawienia go na produkcji jest w `context/deployment/deploy-plan.md`.

Weryfikacja: zielone CI na PR, raport w folderze zmiany, zatwierdzenie modelu domyślnego i kosztu przez użytkownika.

### Key Discoveries:

- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/LlmProviderProperties.java:26-89` — wzorzec walidacji ustawień LLM (enum dostawców, komunikat bez klucza) do rozszerzenia o ustawienia kanału.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/config/LlmProviderConfigTest.java:72-110` — lokalny `HttpServer` + SDK zamiast sieci; wzorzec testów klientów.
- `szczur-radar-api/src/test/java/pl/szczurradar/api/agents/AgentSkeletonIT.java:49-78` — uruchomienie agenta przez `AgentPlatform` i asercja braku wywołań LLM.
- `user/Role.java:18-29`, `config/ErrorCode.java:47-50` — enumy z `@JsonValue` małymi literami (reguła enumów, `lessons.md`).
- `embabel-agent-api-1.5.2:…/spi/support/LlmDataBindingProperties.kt:44`, `embabel-agent-google-genai-autoconfigure-1.5.2:…/GoogleGenAiModelsConfig.kt:90` — domyślne 10 prób; powód budowania własnych klientów.
- `context/foundation/lessons.md` — jedna gałąź i jeden PR; tylko człowiek merguje; implementacja, przegląd i archiwizacja przed merge; kroki możliwe po merge do runbooka, nie do Progress; zamknięte zbiory jako enumy; proaktywne porządkowanie warstw.

## What We're NOT Doing

- Zapis ocen do bazy, migracje, pobieranie z Wykopu w pipeline, mapowanie RAW, deduplikacja, UI, trasy API, harmonogram, orkiestracja wielu postów (S-02/S-03/S-05). `AssessorAgent` ocenia jeden post.
- `createObject`/`createObjectIfPossible` Embabel do oceny; natywny structured output Embabel; autokonfiguracja `spring.ai.retry`.
- Przenoszenie kodu Pythonowego z `feature/assessment-contract-harness_old`.
- Ponowienia semantyczne i „naprawianie” odpowiedzi; jedyne ponowienia to transportowe.
- Paczkowanie postów i Batch API (podział `Assessor` na przygotowanie żądania i ocenę odpowiedzi zostawia na to miejsce w S-02).
- Graf argumentów, liczbowe score/confidence, Reddit, komentarze, widok zbiorczy, rozpoznawanie spółek — poza V2/MVP.
- Eskalacja do drugiego modelu, sygnały Jev/Span-01, Gemini 3.8 Flash; odłożone po MVP: embeddingi, klasyfikator uczony na ocenach, ekstraktor typu GLiNER, klasyfikatory „finansowe”.
- Jeden przebieg pomiaru z trzema kluczami naraz i nowe zmienne kluczy per dostawca; wywołania LLM w CI.
- Bezpośrednie API OpenAI (Luna tylko przez OpenRouter).
- Ustawienie modelu domyślnego na produkcji w tej zmianie — to krok po merge (runbook w `deploy-plan.md`, komentarz w issue).

## Implementation Approach

Fazy idą od kodu bez sieci do pomiaru. Faza 2 to czysta domena (kontrakt, parser, walidator, odsiew) w testach `*Test`. Faza 3 (dawniej „3A”) to infrastruktura `llm` testowana na lokalnym serwerze HTTP z prawdziwymi payloadami. Faza 4 (dawniej „3B”) składa prompt, `Assessor` i `AssessorAgent` oraz kończy się kontrolą na żywo trzech dostawców. Faza 5 buduje zestaw wzorcowy (mapper Wykopu, próbka, arkusz, miary); oznaczanie przez użytkownika może iść równolegle z fazą 4, gdy próbka jest gotowa. Faza 6 nagrywa trzy modele, odtwarza nagrania w CI, generuje raport i zamyka wybór modelu.

Jedna prawda o kształcie wyjścia: plik JSON Schema wyjścia V2 (zasób) — z niego walidacja kształtu surowej odpowiedzi i schemat dla dostawców (uproszczony per dostawca); rekordy Javy odwzorowują ten sam kształt, a test pilnuje zgodności.

Kod pomiarowy (kontrola na żywo, próbka, nagrywanie, miary, raport) leży w `src/test/java/.../measurement/` i uruchamia się wyłącznie w profilu Maven `measurement` (tag JUnit `measurement`), którego CI nie włącza. Wyjątek: test odtwarzania nagrań i progów jest zwykłym `*Test` uruchamianym w CI.

Każdy przebieg na żywo korzysta z tej samej trójki `LLM_*` co produkcja; trzy modele = trzy przebiegi z różnymi ustawieniami. Klucze do przebiegów użytkownik generuje jednorazowo i usuwa po nagraniu; nigdy nie trafiają do repo, CI ani Railway.

Cała zmiana na gałęzi `feature/assessment-contract-harness-java`, jeden PR z `Refs #24`. Kolejność: wszystkie fazy → `/10x-impl-review` z poprawkami → `/10x-archive`; merge do `main` wykonuje użytkownik, raz, na końcu.

## Critical Implementation Details

**Ponowienia tylko w naszej warstwie.** Klienci budowani w `llm/` muszą mieć wyłączone wszystkie ponowienia poniżej: openai-java `maxRetries(0)`, google-genai `HttpRetryOptions.attempts(1)` (oraz jawny `HttpOptions` timeout — domyślnie brak), DeepSeek bez `RetryTemplate` z ponowieniami. Nasza pętla: do 3 prób na tryb przy 429, 5xx, przekroczeniu czasu i błędzie połączenia; `Retry-After`, jeśli dostępny (limit 60 s), inaczej odstęp wykładniczy `min(2^(n-1), 30)` s; każdy inny błąd → od razu `provider_error`. Flex: po 3 kolejnych odmowach 429 to samo wywołanie w trybie standardowym (`provider.only = ["openai"]`), `flex_fallback = true`. Naruszenia kontraktu i błędy parsowania nigdy nie są ponawiane. Embabel nadal rejestruje model z tej samej trójki `LLM_*` (start aplikacji), ale ocena nie przechodzi przez jego `LlmOperations`.

**Ścisłe parsowanie przez JSON Schema.** Surowy tekst → parsowanie JSON (`invalid_structured_output`: brak tekstu, niepoprawny JSON, nadmiarowe tokeny, duplikaty kluczy) → walidacja względem schematu V2 przez networknt (`schema_violation`: obce i brakujące klucze, typy, enumy, limity; `maxLength` w JSON Schema liczy code pointy — potwierdzić testem na „🚀”) → mapowanie na rekordy → walidator domenowy. Pobłażliwe parsery frameworków (Embabel `JacksonOutputConverter`, Spring AI `BeanOutputConverter`) nie mogą stać przed walidatorem.

**Kolejność pól w schemacie.** Żaden obiekt w schemacie wysyłanym do modelu nie kończy się obiektem zagnieżdżonym (`Target`: `id, kind, anchor, stance, reference`; `Claim`: `id, kind, attribution, target_ids, evidence, statement`) — DeepSeek gubił zamykające nawiasy (smoke 2026-10-01). Kontrakt nie nadaje znaczenia kolejności kluczy.

**Białe znaki.** Zbiór białych znaków §10 (po zmianie w fazie 2): U+0009–U+000D, U+001C–U+001F, U+0020, U+0085, U+00A0, U+1680, U+2000–U+200A, U+2028, U+2029, U+202F, U+205F, U+3000. Ani `Character.isWhitespace`, ani Apache `StringUtils.normalizeSpace` nie obejmuje całego zbioru (sprawdzone: pomijają U+2007, U+202F, U+0085).

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

Zamknięty model kontraktu V2 w Javie, ścisły parser, walidator D1–D15 jako zestaw strategii, odsiew §11; poprawka §10 kontraktu. Wszystko bez sieci, w testach `*Test`.

### Changes Required:

#### 1. Kontrakt §10 i dokumenty pomocnicze

**File**: `context/foundation/llm-assessment/contract-v2.md`, `context/foundation/llm-assessment/agent-implementation.md`, `REPOSITORY_GUIDELINES.md`

**Intent**: Uczynić kontrakt neutralnym językowo bez zmiany znaczenia i usunąć Pythonowe sformułowania, zanim kod je odwzoruje.

**Contract**: §10 pkt 1 wymienia jawnie zbiór białych znaków (lista z „Critical Implementation Details”) zamiast `str.isspace()`; nazwa funkcji opisowa („normalizacja białych znaków”); wpis w §17 jako zmiana redakcyjna 2026-10-03. `agent-implementation.md:13` („Pydantic, `extra="forbid"`”) → „zamknięte modele odrzucające obce i brakujące klucze”; `:18`, `:23` (OpenAI tylko przez OpenRouter), `:143-144` bez literałów Pythona. `REPOSITORY_GUIDELINES.md`: pakiety `llm`, `agents/assessor`, `sources`, kod `measurement` tylko testowy.

#### 2. Model kontraktu i schemat V2

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/contract/*.java`, `szczur-radar-api/src/main/resources/assessor/assessment-output-v2.schema.json`

**Intent**: Odwzorować wejście i wyjście 1:1 z kontraktem jako rekordy i enumy oraz trzymać jeden schemat kształtu wyjścia.

**Contract**: Rekordy `AssessmentInputV1`, `Item`, `Context`, `Parent`, `AssessmentOutputV2`, `Target`, `Anchor`, `Stance`, `Claim`, `Evidence`; enumy (wartości JSON małymi literami przez `@JsonValue`) dokładnie ze słownika §3 i typów elementów; `CONTRACT_VERSION = "V2"`. Schemat JSON (draft 2020-12): wszystkie klucze wymagane, `additionalProperties: false` na każdym obiekcie, enumy, limity §9 (`maxItems`, `minLength`/`maxLength`), `uniqueItems` dla `modifiers`/`flags`, `null` dozwolone tylko tam, gdzie kontrakt pozwala (`assertiveness`); kolejność właściwości według „Critical Implementation Details”. Walidacja wejścia: co najmniej jedno z `title`/`body` niepuste; tekst z samych białych znaków musi być `null`; `published_at` z przesunięciem strefy. Brak pól `supported_by_claim_ids`, `score`, `confidence`, `is_signal`, `schema_version` w wyjściu.

#### 3. Białe znaki, parser i walidacja kształtu

**File**: `.../agents/assessor/text/Whitespace.java`, `.../agents/assessor/validation/OutputParser.java`, `FailureClass.java`, `Violation.java`

**Intent**: Jedna normalizacja dla walidatora, odsiewu i miar oraz jedna ścieżka „surowy tekst → wyjście albo naruszenia”.

**Contract**: `Whitespace.normalize(String)` (zbiór §10 → spacja, ciągi → jedna spacja, obcięcie końców), `Whitespace.isWhitespace(int codePoint)`. `FailureClass` = `provider_error | timeout | invalid_structured_output | schema_violation | invalid_reference | fabricated_evidence | domain_invariant_violation` (enum). `Violation(FailureClass, String rule, String path, String message)`, ścieżki `$.claims[0].id`. `OutputParser.parse(String raw)` → wynik sukces (`AssessmentOutputV2`) albo lista naruszeń (`invalid_structured_output` / `schema_violation`), z kolejnością z „Critical Implementation Details”; własny ścisły `JsonMapper` (Jackson 3) niezależny od mappera Boota.

#### 4. Walidator domenowy (wzorzec strategii)

**File**: `.../agents/assessor/validation/ValidationRule.java`, `ContractV2Rules.java`, `RuleBasedValidator.java`, `StanceTable.java`, `rules/*.java`

**Intent**: Każda reguła D1–D15 to niezależna strategia, a walidator tylko uruchamia uporządkowany zestaw, żeby nowa reguła (zmiana kontraktu przez §16, inny przypadek, kolejna wersja kontraktu) była nową klasą dopisaną do zestawu, bez zmian walidatora i innych reguł.

**Contract**: `ValidationRule` — `List<Violation> check(AssessmentInputV1, AssessmentOutputV2)` + identyfikator reguły (enum `RuleId` D1–D15). `ContractV2Rules` — uporządkowana lista strategii dla V2 (D5→D7→D6 jako jedna reguła cytatu: brak dokumentu/pola → `invalid_reference`, pusty po normalizacji → `domain_invariant_violation`, nie-podciąg → `fabricated_evidence`); walidator odrzuca zestaw z powtórzonym identyfikatorem. `RuleBasedValidator.validate(input, output)` zbiera wszystkie naruszenia; klasa wyniku = pierwsza obecna w porządku `invalid_reference → fabricated_evidence → domain_invariant_violation`. `StanceTable` — enum z danymi tabeli §7.2 (assertiveness: wymagane/null; claim_ids: wymagane/dozwolone/zabronione; teza autora: wymagana/nie); D12–D14 czytają z niej. Długości liczone `codePointCount`.

#### 5. Odsiew wpisów bez treści

**File**: `.../agents/assessor/Prefilter.java`

**Intent**: Jedna definicja „wpisu bez treści” dla produkcji, próbki wzorca i testów.

**Contract**: `boolean isAssessable(AssessmentInputV1)` według §11: usunięcie `https?://\S*`, `<url>`, `#` + znaki słowa, `@` + znaki słowa (URL przed tagiem; „znak słowa” = `[\p{L}\p{N}_]`), obcięcie białych znaków (`Whitespace`) każdego pola, połączenie, próg 3 code pointy; `AssessmentOutputV2 noContentOutput()` = kształt z §11.

#### 6. Testy offline i dokumentacja pakietu

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/agents/assessor/**/*Test.java`, `src/test/resources/fixtures/assessor/real_posts.json`, `src/main/java/pl/szczurradar/api/agents/assessor/CLAUDE.md`

**Intent**: Udowodnić, że kod ma kształt kontraktu, a każda reguła ma test pozytywny i negatywny, także na prawdziwych tekstach.

**Contract**: Pozytywne: kompletne wyjścia przykładów 1–7 z `examples.md` (sekcje `# N.` z dwoma blokami ```json: wejście, wyjście) przechodzą bez naruszeń. Negatywne z asercją klasy — lista z `agent-implementation.md` §5 (m.in. zmyślony cytat, zmieniona odmiana, „[...]”, cytat z samych białych znaków w wariantach `" "`, `" \n"`, pole `null`, brakujący cel/teza, duplikat ID, powtórzenie, anchor `explicit` spoza `item`, evidence spoza `item`, teza bez celu w `claim_ids`, stanowisko kierunkowe bez tezy autora, każda zabroniona kombinacja §7.2, `not_investment_related` z celami, obce pole, wartość spoza enumu, przekroczony limit na „🚀”×(limit+1), puste wejście). Białe znaki: test na każdy znak zbioru §10 oraz U+200B jako znak niebiały. Parser: płotki markdown, nadmiarowe tokeny, duplikat klucza, liczba zamiast enumu → odrzucone. Zgodność rekordów ze schematem (te same nazwy i kolejność pól). `real_posts.json`: zanonimizowane fragmenty z `pilot/` (twarda spacja, „lenny face”, ocenzurowane słowa, emoji, sam tag, sam link, tag + `<url>`) z oczekiwanym `assessable`. `CLAUDE.md`: jak dodać regułę (nowa strategia + wpis w `ContractV2Rules` + test negatywny/pozytywny; najpierw zmiana kontraktu przez §16), białe znaki tylko przez `Whitespace`.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą bez Dockera i bez zmiennych LLM: `cd szczur-radar-api && ./mvnw -B test`
- Pełna weryfikacja przechodzi: `cd szczur-radar-api && ./mvnw -B verify`
- Wyjścia przykładów 1–7 z `examples.md` przechodzą walidator bez naruszeń (test)
- Każda reguła D1–D15 ma test negatywny z asercją klasy i co najmniej jeden pozytywny; każda zabroniona kombinacja §7.2 ma test
- Cytat różniący się od pola wyłącznie znakami ze zbioru §10 przechodzi; zmieniona odmiana i „[...]” dają `fabricated_evidence`; limity liczone w code pointach (test „🚀”)
- `contract-v2.md` nie odwołuje się do Pythona: `! grep -n "isspace\|Python" context/foundation/llm-assessment/contract-v2.md`

#### Manual Verification:

- Enumy, limity i reguły w kodzie oraz schemacie zgadzają się 1:1 z `contract-v2.md` (przegląd obok siebie)
- Użytkownik zaakceptował redakcyjną zmianę §10

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 3.

---

## Phase 3: Infrastruktura LLM (dawniej 3A)

### Overview

Pakiet `llm`: port `LlmClient`, klienci trzech dostawców budowani z `LLM_*` z pełną kontrolą żądania, ponowień i odczytu zużycia; testy na lokalnym serwerze HTTP z prawdziwymi payloadami.

### Changes Required:

#### 1. Ustawienia kanału

**File**: `.../config/LlmProviderProperties.java`, `szczur-radar-api/README.md`, `.railway/railway.ts`

**Intent**: Wyrazić tryb flex, poziom rozumowania i limit czasu bez nowych sekretów i bez drugiej ścieżki konfiguracji.

**Contract**: Opcjonalne zmienne: `LLM_SERVICE_TIER` (enum `default` | `flex`; `flex` dozwolony tylko dla `openrouter`), `LLM_REASONING_EFFORT` (enum, tylko `openrouter`; brak = domyślne dostawcy), `LLM_TIMEOUT` (czas, domyślnie 120 s; dla flex zalecane dłuższe). Błędne wartości zatrzymują start z komunikatem jak dla trójki `LLM_*`. `railway.ts`: trzy nowe nazwy jako `preserve()`. README: opis zmiennych.

#### 2. Port i klienci dostawców

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/*.java` (`LlmClient`, `LlmRequest`, `LlmResponse`, `LlmCallException`, `LlmClientFactory`, `TransportRetry`, `ProviderSchemas`, `GeminiClient`, `DeepSeekClient`, `OpenRouterClient`, `LlmConfiguration`)

**Intent**: Oddzielić wywołanie modelu od oceny i obsłużyć różnice dostawców w jednym miejscu, z gwarancją surowego tekstu i dokładnie naszych ponowień.

**Contract**: `LlmClient.complete(LlmRequest) -> LlmResponse` (rzuca `LlmCallException(FailureClass provider_error|timeout, message, attempts, serviceTier, flexFallback)` po wyczerpaniu prób). `LlmRequest(systemPrompt, userMessage, outputSchema)`. `LlmResponse(rawText, provider, model, channel, serviceTier, flexFallback, reasoningEffort, outputMode, inputTokens, outputTokens, cachedTokens, attempts)`. `LlmClientFactory.create(LlmProviderProperties)` buduje Spring AI `ChatModel` dostawcy z wyłączonymi ponowieniami i timeoutem (bez beanów modeli Embabel); `LlmConfiguration` wystawia leniwy bean `LlmClient` (start bez sieci). `temperature = 0`. Gemini: `GoogleGenAiChatModel` na własnym kliencie genai, `responseJsonSchema` bez `minItems`/`maxItems`, tekst = złączenie części z `isThought != true`, `includeThoughts` wyłączone. DeepSeek: `responseFormat = JSON_OBJECT`, myślenie wyłączone, schemat (bez limitów długości i liczności) dopisany do promptu systemowego stałym akapitem, `outputMode = json_mode`. OpenRouter: `responseFormat` JSON_SCHEMA strict (bez limitów długości i liczności), `extraBody.provider = {only: ["openai/flex"], allow_fallbacks: false}` przy `flex`, `["openai"]` po powrocie; `reasoningEffort` z ustawień. Zużycie: tokeny wejścia/wyjścia oraz cache (`getCacheReadInputTokens()`; dla DeepSeek — jeśli Spring AI nie mapuje `prompt_cache_hit_tokens`, odczyt z surowego `usage` przez interceptor `RestClient`). `ProviderSchemas.forProvider(Provider, schema)` usuwa słowa kluczowe per dostawca i nie zmienia kolejności właściwości. Odmowa modelu (`refusal`, zablokowana odpowiedź bez treści) → `provider_error` bez ponowień.

#### 3. Prawdziwe payloady i testy klientów

**File**: `src/test/resources/fixtures/{gemini,deepseek,openrouter}/*.json`, `src/test/java/pl/szczurradar/api/llm/*Test.java`, `src/main/java/pl/szczurradar/api/llm/CLAUDE.md`, `src/test/java/pl/szczurradar/api/measurement/README.md`

**Intent**: Sprawdzić offline prawdziwy kod Spring AI i SDK: co wysyłamy i jak czytamy odpowiedź, błędy i zużycie.

**Contract**: Po jednym prawdziwym, zanonimizowanym body odpowiedzi na dostawcę (sukces), zapisanym poleceniem `curl` opisanym w `measurement/README.md` (klucze użytkownika z powłoki). Testy `*Test` z lokalnym `HttpServer` (wzorzec `LlmProviderConfigTest`): wysłane żądanie zawiera właściwy schemat (Gemini bez `minItems`/`maxItems`; DeepSeek i OpenRouter bez limitów długości i liczności; `additionalProperties` i kolejność zachowane), `extraBody.provider` przy flex, wyłączone myślenie DeepSeek; odczyt surowego tekstu i zużycia (z cache) z prawdziwych payloadów; 429 → poprawna odpowiedź daje `attempts = 2`; więcej niż 3 błędy 5xx → `provider_error` po 3 próbach (serwer liczy żądania — dowód braku ukrytych ponowień SDK); przekroczenie czasu → `timeout`; 400 → `provider_error` po 1 próbie; 3× 429 w flex, potem sukces → `serviceTier = default`, `flexFallback = true`; `Retry-After` respektowany. `llm/CLAUDE.md`: jak dodać dostawcę, zakaz ponowień w SDK/frameworku, payloady testowe tylko prawdziwe.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe przechodzą offline: `cd szczur-radar-api && ./mvnw -B test`
- Pełna weryfikacja przechodzi: `cd szczur-radar-api && ./mvnw -B verify`
- Serwer testowy otrzymuje dokładnie tyle żądań, ile prób zgłasza `LlmResponse.attempts` / `LlmCallException` (brak ukrytych ponowień SDK i frameworka) dla każdego dostawcy
- Schemat wysłany do Gemini nie zawiera `minItems`/`maxItems`, a do DeepSeek i OpenRouter także `minLength`/`maxLength` (test)
- Błędne `LLM_SERVICE_TIER` / `LLM_REASONING_EFFORT` / `LLM_TIMEOUT` oraz `flex` dla dostawcy innego niż `openrouter` zatrzymują start z komunikatem bez klucza (test)
- Plan Railway nie proponuje usunięć: job `railway-config` na PR zielony

#### Manual Verification:

- Payloady w `fixtures/<dostawca>/` są prawdziwymi odpowiedziami API bez kluczy i danych osobowych

**Implementation Note**: Przed zapisaniem payloadów poproś użytkownika o jednorazowe klucze trzech dostawców (podawane w powłoce, nigdy w repo). Po zielonej weryfikacji zatrzymaj się na ręczne potwierdzenie przed fazą 4.

---

## Phase 4: Prompt, Assessor i agent (dawniej 3B)

### Overview

Prompt V2 z wersją, `Assessor` składający odsiew, jedno wywołanie i walidację w wynik z pochodzeniem, `AssessorAgent` zamiast `SkeletonAgent`, kontrola na żywo trzech dostawców.

### Changes Required:

#### 1. Prompt V2

**File**: `.../agents/assessor/prompt/AssessmentPrompt.java`, `src/main/resources/assessor/system-prompt.md`

**Intent**: Jeden wersjonowany prompt systemowy zgodny z kontraktem, oparty na sprawdzonym skróconym prompcie.

**Contract**: `PROMPT_VERSION` (nowa wersja przy każdej zmianie treści) i tekst z zasobu: wymagania `agent-implementation.md` §2, reguły §5–§7.2, zasady cytatu, limity, słownik slangu z `pilot/labeling-guidelines.md`, jawna reguła `not_expressed` → puste `claim_ids`; materiał: `pilot/scripts/prompt-short.md`, `prompt-v2-rules.md` (tekst promptu v2.2 z gałęzi `_old` dopuszczalny jako punkt wyjścia). Wiadomość użytkownika = stały prefiks „niezaufany materiał” + kompaktowy JSON wejścia (tylko pola kontraktu, czas z przesunięciem strefy, znaki spoza ASCII bez escapowania).

#### 2. `Assessor`, wynik i pochodzenie

**File**: `.../agents/assessor/Assessor.java`, `PreparedRequest.java`, `Preparation.java`, `AssessmentResult.java`, `Provenance.java`

**Intent**: Złożyć odsiew, pojedyncze wywołanie i walidację w wynik, który S-02 zapisze bez interpretacji; zostawić podział na przygotowanie i ocenę dla trybu wsadowego.

**Contract**: `Preparation prepare(AssessmentInputV1)` — sealed: `Prepared(PreparedRequest)` | `Decided(AssessmentResult)` (odsiew). `AssessmentResult evaluate(AssessmentInputV1, PreparedRequest, LlmResponse, Duration, Instant)`. `AssessmentResult assess(AssessmentInputV1)` — dokładnie jedno `LlmClient.complete` na ocenialny wpis, zero dla odsiewu; `LlmCallException` → wynik `failed` z klasą wyjątku. `AssessmentResult(Status ok|failed, @Nullable output, @Nullable failureClass, violations, provenance, @Nullable rawText)`; z jakimkolwiek naruszeniem nigdy `ok`. `Provenance(Source model|prefilter, provider, model, channel, serviceTier, flexFallback, reasoningEffort, outputMode, promptVersion, contractVersion, assessedAt, inputTokens, outputTokens, cachedTokens, durationMs, attempts)`; dla `prefilter` bez dostawcy, modelu i tokenów, `attempts = 0`.

#### 3. Agent Embabel

**File**: `.../agents/assessor/AssessorAgent.java`, usunięcie `agents/SkeletonAgent.java`, `src/test/java/.../agents/AssessorAgentIT.java` (zamiast `AgentSkeletonIT`), `szczur-radar-api/README.md`

**Intent**: Dać S-02 gotowy krok agenta, z logiką w zwykłym serwisie testowalnym bez Embabel.

**Contract**: `@Agent` z jedną `@Action` + `@AchievesGoal`: `AssessmentInputV1 → AssessmentResult`, delegacja do `Assessor`. IT: uruchomienie przez `AgentPlatform` dla wpisu bez treści daje `source = prefilter` i zero wywołań `LlmClient` oraz zero `LlmInvocation` Embabel; zachowane asercje rejestracji modelu z `AgentSkeletonIT`.

#### 4. Testy `Assessor` i kontrola na żywo

**File**: `src/test/java/.../agents/assessor/AssessorTest.java`, `src/test/java/.../measurement/LiveSmokeTest.java`, `szczur-radar-api/pom.xml`, `measurement/README.md`

**Intent**: Udowodnić klasyfikację i liczbę wywołań offline oraz wcześnie sprawdzić na żywo, że trzej dostawcy zachowują się jak w pilotach.

**Contract**: `AssessorTest` (sztuczny `LlmClient`): poprawna odpowiedź → `ok` z pochodzeniem; każda klasa błędu (`provider_error`, `timeout`, `invalid_structured_output`, `schema_violation`, `fabricated_evidence`, `invalid_reference`, `domain_invariant_violation`) → `failed` i jedno wywołanie; wpis bez treści → zero wywołań; wiadomość zawiera wyłącznie pola wejścia; `evaluate` na surowej odpowiedzi = `assess` na kliencie zwracającym tę odpowiedź. `pom.xml`: Surefire domyślnie wyklucza tag `measurement`; profil `measurement` uruchamia tylko ten tag (`./mvnw -Pmeasurement test -Dtest=<Klasa>`), bez Dockera i bez Springa. `LiveSmokeTest`: mapuje rekordy `pilot/wykop-gielda-sample-2026-09-29.json` na `AssessmentInputV1` (`entry` → `post`, `link` → `link`, czas Europe/Warsaw → RFC 3339), bierze pierwsze 30 ocenialnych, ocenia przez `Assessor` z klientem z `LLM_*`, wypisuje: wejścia, odrzucenia odsiewu, wywołania (mianownik), zgodność, klasy błędów, tokeny (z cache), medianę czasu, dla flex odsetek odmów 429 i liczbę powrotów; nic nie zapisuje do repo.

### Success Criteria:

#### Automated Verification:

- Pełna weryfikacja przechodzi bez wywołań LLM: `cd szczur-radar-api && ./mvnw -B verify`
- Każda klasa błędu ma test na sztucznym kliencie, a ocenialny wpis wywołuje klienta dokładnie raz; wpis bez treści — zero razy
- `evaluate` na surowej odpowiedzi daje wynik identyczny z `assess` na kliencie zwracającym tę odpowiedź
- `AssessorAgentIT` przechodzi; `SkeletonAgent` nie istnieje
- Domyślne `./mvnw -B test` nie uruchamia testów z tagiem `measurement`

#### Manual Verification:

- `LiveSmokeTest` wykonany trzy razy (Gemini `gemini-3.1-flash-lite`, DeepSeek Flash, OpenRouter `openai/gpt-6-luna` z `LLM_SERVICE_TIER=flex`): dla każdego zgodność z kontraktem ≥90% i najwyżej jedno `provider_error` lub `invalid_structured_output`; wyniki (z mianownikiem i dla Luny odsetkiem odmów 429 i medianą czasu) w opisie PR
- Pochodzenie ma wypełnione tokeny (w tym cache, gdy dostawca je zgłasza), wersję promptu i kontraktu dla wszystkich trzech; potwierdzona nazwa modelu DeepSeek Flash przyjmowana przez API

**Implementation Note**: Jeśli kontrola na żywo nie spełnia progu dla któregoś dostawcy, zatrzymaj się i przedstaw przyczynę oraz opcje (poprawka klienta albo promptu) przed fazą 6. Po zielonej weryfikacji zatrzymaj się na ręczne potwierdzenie.

---

## Phase 5: Zestaw wzorcowy

### Overview

Mapper Wykopu w kodzie produkcyjnym, narzędzie pobrania i anonimizacji próbki, arkusz, oznaczanie przez użytkownika i miary wzorca. Pobranie próbki wymaga fazy 2 (wejście, odsiew); oznaczanie może iść równolegle z fazą 4.

### Changes Required:

#### 1. Mapper Wykopu

**File**: `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopMapper.java`, `UnmappableItemException.java`, testy `*Test`, `src/test/resources/fixtures/wykop/*.json`

**Intent**: Jedna definicja mapowania wpisu Wykopu na wejście kontraktu dla próbki wzorca i dla S-02.

**Contract**: Wykop API v3: `entry` → `post` (`title = null`, `body = content`), `link` → `link` (`title`, `body = description`); inny zasób → `UnmappableItemException`; tekst pusty lub z samych białych znaków → `null`; brak tekstu → wyjątek; `created_at` (`yyyy-MM-dd HH:mm:ss`) jako czas Europe/Warsaw → `OffsetDateTime` (test zimą i latem, `+01:00`/`+02:00`); `channels` = kanał tagu; bez autora, URL i ID. Fixture: zanonimizowany prawdziwy element z `pilot/` w kształcie API.

#### 2. Pobranie i anonimizacja próbki

**File**: `src/test/java/.../measurement/WykopGoldSampler.java` (tag `measurement`), `src/test/resources/fixtures/assessor/gold/posts.json`

**Intent**: Próbka niezależna od postów, z których powstały reguły (2026-09-28 i 2026-09-29 wykluczone).

**Contract**: Uruchomienie: `./mvnw -Pmeasurement test -Dtest=WykopGoldSampler -Dgold.days=<d1>,<d2>` z `WYKOP_KEY`/`WYKOP_SECRET` w powłoce. Pobiera `#gielda` przez Wykop API v3 (auth, stream ze stronicowaniem), mapuje przez `WykopMapper`, pomija nieocenialne według `Prefilter`, losuje ~60 na dzień ze stałym ziarnem, anonimizuje (nicki po `@` i nicki autorów → `@uzytkownikN`, URL → `<url>`, białe znaki bez zmian), zapisuje `posts.json` jako listę `{post_id, day, input}`. Surowe odpowiedzi API poza repo.

#### 3. Arkusz oznaczania

**File**: `context/changes/assessment-contract-harness/pilot/labeling-gold.html`

**Intent**: Arkusz wskazuje nowe ścieżki Javy.

**Contract**: Ścieżka `posts.json` → `szczur-radar-api/src/test/resources/fixtures/assessor/gold/posts.json`; komentarz o formacie → `measurement/GoldLabels`; format eksportu bez zmian: `[{post_id, relevance, hard, note, targets: [{anchor, direction, hedged}]}]`.

#### 4. Miary wzorca

**File**: `src/test/java/.../measurement/GoldLabels.java`, `AnchorMatcher.java`, `GoldMetrics.java`, `GoldMetricsTest.java`, `GoldDataTest.java`, `src/test/resources/fixtures/assessor/gold/labels.json`

**Intent**: Jedna, przetestowana definicja miar, żeby progi znaczyły to samo w teście CI i w raporcie.

**Contract**: Miary: `contractCompliance`, `relevanceBinaryAccuracy` (wzorcowe `unclear` wyłączone), `targetRecall` (dopasowane / cele we wzorcu), `targetPrecision` (dopasowane / cele w odpowiedziach; brak celów w odpowiedzi przy celach we wzorcu → 0), `targetDirectionAccuracy` (poprawny `direction` wśród celów wzorca z kierunkiem innym niż `not_expressed`; niedopasowany = błąd), `dominantDirection` (więcej `bullish` niż `bearish` → `bullish`, odwrotnie → `bearish`, inaczej brak), `dominantDirectionAgreement`, `contradictionRate` (`bullish` ↔ `bearish`). Posty „trudne” wyłączone z mianowników miar poza zgodnością; brak odpowiedzi lub wynik `failed` = zero dopasowań i błąd, nie znika z mianownika; pusty mianownik we wzorcu → błąd pomiaru (`InsufficientGoldException`), nie zaliczenie progu. Progi jako stałe: 95 / 80 / 80 / 70 / 75 / 75 / ≤3 %. `AnchorMatcher`: normalizacja (`Whitespace.normalize`, `toLowerCase(Locale.ROOT)`, usunięcie początkowych `#`), oba niepuste, równość albo zawieranie krótszego w dłuższym z granicami `(?<![\p{L}\p{N}_])…(?![\p{L}\p{N}_])` (fragment dosłownie, `Pattern.quote`); skojarzenie 1:1 o największej liczbie par, remisy kolejnością we wzorcu i odpowiedzi. `GoldMetricsTest`: dane syntetyczne z ręcznie policzonym wynikiem dla każdej miary, model zwracający zawsze `targets = []`, granice anchorów w obu kolejnościach (`AI`↔`Taiwan` i `XTB`↔`XTBR` — brak; `CD Projekt`↔`akcje CD Projekt`, `#XTB`↔`xtb`, `XTB`↔`(XTB)`, `XTB`↔`$XTB` — dopasowanie; pusty anchor — brak). `GoldDataTest` (pomijany, dopóki brak plików; potem obowiązkowy): każdy `post_id` istnieje, każdy anchor jest podciągiem posta po normalizacji, brak nicków i URL, ≥100 postów nie-„trudnych”, dwa różne dni spoza 2026-09-28/29.

### Success Criteria:

#### Automated Verification:

- Testy mappera, miar i danych wzorca przechodzą: `cd szczur-radar-api && ./mvnw -B test`
- Wzorzec ma co najmniej 100 postów nie-„trudnych” z dwóch różnych dni, żadnego z 2026-09-28 ani 2026-09-29 (`GoldDataTest`)
- `posts.json` i `labels.json` nie zawierają nicków autorów ani adresów URL (`GoldDataTest`)
- Każda miara ma test na danych syntetycznych z ręcznie policzonym wynikiem

#### Manual Verification:

- Użytkownik oznaczył wszystkie posty w `labeling-gold.html` według instrukcji v1.0 i wyeksportował `labels.json`
- Użytkownik przejrzał notatki i posty „trudne”; brakujące reguły dopisane do `contract-v2.md` i instrukcji (z aktualizacją etykiet) albo świadomie odłożone

**Implementation Note**: Przed pobraniem próbki poproś użytkownika o klucze Wykopu w powłoce. Oznaczanie to ~godzina pracy użytkownika, można je rozłożyć na sesje. Zmiana kontraktu z notatek wymaga powrotu do odpowiednich kroków fazy 2 (i nowej wersji promptu w fazie 4).

---

## Phase 6: Pomiar, raport i model domyślny

### Overview

Trzy przebiegi nagrywania z `LLM_*`, nagrania odtwarzane w CI z progami dla modelu domyślnego, raport jakości i kosztu, wybór modelu i runbook produkcji.

### Changes Required:

#### 1. Konfiguracje zmierzone i nagrywanie

**File**: `src/test/resources/fixtures/llm/models.json`, `src/test/java/.../measurement/GoldRecorder.java` (tag `measurement`), `src/test/resources/fixtures/llm/recordings/`

**Intent**: Nagrać każdą odpowiedź trzech modeli na wzorcu jako prawdziwy payload do odtwarzania i raportu.

**Contract**: `models.json`: `{default, models: [{id, role: preferred|fallback|reference, provider, model, serviceTier, reasoningEffort, prices: {input, cachedInput, output, perChannel…}}]}` — `openrouter` `openai/gpt-6-luna` flex (preferred), `deepseek` Flash (fallback), `gemini` `gemini-3.1-flash-lite` (reference); `default` ustawiany w kroku 4. `GoldRecorder` (`./mvnw -Pmeasurement test -Dtest=GoldRecorder`) czyta `LLM_*`, znajduje pasującą konfigurację w `models.json` (brak → błąd), ocenia każdy post wzorca przez `Assessor` i zapisuje `recordings/<id>/<post_id>.json` = `{post_id, configId, provider, model, channel, serviceTier, flexFallback, reasoningEffort, outputMode, promptVersion, contractVersion, recordedAt, rawText, inputTokens, outputTokens, cachedTokens, durationMs, attempts, status, failureClass, violations}`. Nadpisuje wyłącznie nagrania z tą samą konfiguracją.

#### 2. Odtwarzanie nagrań i progi w CI

**File**: `src/test/java/.../measurement/RecordingsReplayTest.java` (zwykły `*Test`, uruchamiany w CI)

**Intent**: Zamienić prawdziwe odpowiedzi w test regresyjny walidatora i progów, bez sekretów.

**Contract**: Bieżące nagrania = zgodne z bieżącymi `PROMPT_VERSION`, `CONTRACT_VERSION` i konfiguracją z `models.json`; starsze nie liczą się do pomiaru. Dla każdego bieżącego nagrania z `rawText`: `Assessor.evaluate` daje zapisane `status` i `failureClass`. Dla konfiguracji `default`: komplet nagrań dla wszystkich postów wzorca i miary `GoldMetrics` spełniają progi; brak lub nieważna odpowiedź liczy się jako błąd. Kwalifikacja do porównania: starszy prompt, starszy kontrakt lub brak nagrania czyni zestaw nieporównywalnym; komplet z wynikami `failed` pozostaje porównywalny.

#### 3. Raport jakości i kosztu

**File**: `src/test/java/.../measurement/GoldReport.java` (tag `measurement`), `context/changes/assessment-contract-harness/reports/gold-<YYYY-MM-DD>.md`

**Intent**: Dać użytkownikowi liczby do wyboru modelu bez kolejnej rundy analizy.

**Contract**: `./mvnw -Pmeasurement test -Dtest=GoldReport -Dreport.out=<ścieżka>`; bez wywołań modeli. Jedna tabela trzech modeli (z rolą): liczba ocenionych postów, zgodność z kontraktem, trafność binarna, recall i precision celów, kierunek na dopasowanych celach ze stanowiskiem, dominujący kierunek, sprzeczne kierunki, naruszenia wg klasy, tokeny (z cache), koszt na post i na 12k postów, mediana czasu; dla Luny flex odsetek odmów 429, liczba ponowień oraz liczba i koszt postów po powrocie z flex; koszt miesięczny w rozbiciu na kanały (Luna: standard i flex; DeepSeek: szczyt i poza szczytem; Gemini: bezpośrednio); spełnienie każdego progu, wersje promptu i kontraktu, data. Model trafia do tabeli tylko z kompletem bieżących nagrań; niekompletny jest jawnie oznaczony z listą brakujących `post_id`.

#### 4. Wybór modelu i runbook produkcji

**File**: `src/test/resources/fixtures/llm/models.json`, `context/changes/assessment-contract-harness/reports/`, `context/foundation/roadmap.md`, `context/deployment/deploy-plan.md`

**Intent**: Zamknąć F-02 decyzją opartą na raporcie i przygotować jej wdrożenie po merge.

**Contract**: Użytkownik wybiera `default` według ról: Luna flex, jeśli spełnia progi; inaczej DeepSeek Flash, jeśli spełnia; Gemini jest punktem odniesienia, nie kandydatem. Wymaga porównywalnych zestawów trzech modeli; po zmianie promptu lub kontraktu trzeba nagrać ponownie wszystkie trzy. Jeśli ani Luna, ani DeepSeek nie spełnia progów: jedna jawna decyzja użytkownika — poprawić prompt i powtórzyć pomiar albo zaakceptować konkretny kompromis (zmienione progi w `GoldMetrics` i kryterium 6.3, z uzasadnieniem w raporcie); bez niej F-02 nie jest ukończone. `roadmap.md`: „Unknowns” F-02 uzupełnione wynikiem (status zmieniają `/10x-implement` i `/10x-archive`). `deploy-plan.md`: runbook „F-02: model domyślny” — wartości `LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT`, `LLM_TIMEOUT` dla produkcji, ustawiane przez operatora po merge, z weryfikacją w logu startu; kroki po merge prowadzone w runbooku i w issue #24, nie w Progress.

### Success Criteria:

#### Automated Verification:

- Domyślne `cd szczur-radar-api && ./mvnw -B verify` nie uruchamia testów `measurement`, uruchamia `*IT` i odtwarza wszystkie bieżące nagrania z identycznym werdyktem
- Konfiguracja `default` ma komplet nagrań dla wszystkich postów wzorca z bieżącymi `PROMPT_VERSION` i `CONTRACT_VERSION`
- Miary konfiguracji `default` spełniają progi: zgodność z kontraktem ≥95%, trafność binarna ≥80%, recall celów ≥80%, precision celów ≥70%, kierunek na dopasowanych celach ze stanowiskiem ≥70%, dominujący kierunek posta ≥75%, sprzeczne kierunki ≤10% (kierunek i sprzeczności złagodzone z ≥75% / ≤3% decyzją użytkownika 2026-10-06, raport `gold-2026-10-06.md`) (`RecordingsReplayTest`)
- Raport generuje się bez błędów do `context/changes/assessment-contract-harness/reports/`
- CI na PR (`backend`, `web-unit`, `web-container-smoke`, `railway-config`) jest zielone

#### Manual Verification:

- `GoldRecorder` wykonany trzy razy (Luna flex, DeepSeek Flash, Gemini 3.1 Flash-Lite) na całym wzorcu; raport zawiera jedną tabelę jakości i kosztu tych trzech modeli
- Użytkownik przejrzał raport i zatwierdził model domyślny oraz szacowany koszt miesięczny; jednorazowe klucze usunięte
- Jeśli progi zostały zmienione albo żaden kandydat ich nie spełnił, decyzja użytkownika i uzasadnienie są w raporcie

**Implementation Note**: Przed nagrywaniem poproś użytkownika o jednorazowe klucze trzech dostawców (deklaracja użytkownika z 2026-10-03); po nagraniu przypomnij o ich usunięciu. Po fazie 6: `/10x-impl-review`, poprawki, `/10x-archive` na tej samej gałęzi; PR gotowy, merge wykonuje użytkownik.

---

## Testing Strategy

### Unit Tests:

- Kontrakt: rekordy ↔ schemat, enumy, wymagane i obce klucze, limity w code pointach, walidacja wejścia.
- Parser: niepoprawny JSON, płotki, nadmiarowe tokeny, duplikaty kluczy, liczby zamiast enumów.
- Walidator: każda strategia D1–D15 pozytywnie i negatywnie, tabela §7.2, kolejność klas; białe znaki — każdy znak zbioru §10.
- Odsiew: prawdziwe fragmenty z `real_posts.json`.
- Klienci `llm`: lokalny `HttpServer` z prawdziwymi payloadami — żądanie, odczyt, zużycie, ponowienia, flex.
- `Assessor`: klasy błędów, liczba wywołań, pochodzenie, tylko pola wejścia w wiadomości.
- Mapper Wykopu (w tym czas letni/zimowy), miary wzorca na danych syntetycznych.

### Integration Tests:

- `AssessorAgentIT`: agent przez `AgentPlatform`, odsiew bez wywołań LLM.
- `RecordingsReplayTest` (CI): odtwarzanie nagrań i progi modelu domyślnego.
- Profil `measurement` (lokalnie, z kluczami w powłoce): `LiveSmokeTest`, `WykopGoldSampler`, `GoldRecorder`, `GoldReport`.

### Manual Testing Steps:

1. Faza 3: zapisz po jednej prawdziwej odpowiedzi dostawcy poleceniami z `measurement/README.md`.
2. Faza 4: trzy przebiegi `./mvnw -Pmeasurement test -Dtest=LiveSmokeTest` z różnymi `LLM_*`; wyniki do opisu PR.
3. Faza 5: `WykopGoldSampler` z `WYKOP_KEY`/`WYKOP_SECRET`; oznacz w `labeling-gold.html`, zapisz `labels.json`.
4. Faza 6: trzy przebiegi `GoldRecorder`, `GoldReport`, wybór `default` w `models.json`, `./mvnw -B verify`.

## Performance Considerations

Jedno wywołanie na ocenialny post; wpisy bez treści bez wywołania (~8% w pilotażach). Szacunek przy 12k postów miesięcznie (`frame.md`, „Korekta cen”): DeepSeek Flash ~$2–8 zależnie od kanału i pory, Luna ~$6 standard / ~$3 flex, Gemini 3.1 Flash-Lite ~$10. Pomiar w fazie 6: ~120 postów × 3 modele, poniżej $1. Flex odpowiada wolniej i odmawia 429 — `LLM_TIMEOUT` i odstępy ponowień mogą wydłużyć przebieg; liczby trafiają do raportu.

## Migration Notes

Brak zmian danych i migracji Flyway. `SkeletonAgent` zastępuje `AssessorAgent`; kod oceny nie jest wywoływany przez żadną trasę, więc deploy po merge nie zmienia zachowania usługi. Nowe opcjonalne zmienne `LLM_*` są w `railway.ts` jako `preserve()`. Ustawienie modelu domyślnego na produkcji to krok operatora po merge (runbook w `deploy-plan.md`).

## References

- Badanie (w tym follow-up 2026-10-03, Java/Embabel): `context/changes/assessment-contract-harness/research.md`
- Brief ramowy i dziennik pilotaży: `context/changes/assessment-contract-harness/frame.md`
- Kontrakt i dokumenty: `context/foundation/llm-assessment/contract-v2.md`, `examples.md`, `agent-implementation.md`
- Instrukcja oznaczania i prompty pilotażowe: `context/changes/assessment-contract-harness/pilot/labeling-guidelines.md`, `pilot/scripts/prompt-short.md`, `pilot/scripts/prompt-v2-rules.md`
- Zarchiwizowana zmiana F-03: `context/archive/2026-10-01-java-api-rewrite/` (research Embabel `research.md:175`, `:207-220`; review F5)
- Wzorce w kodzie: `config/LlmProviderProperties.java:26-89`, `config/LlmProviderConfigTest.java:72-110`, `agents/AgentSkeletonIT.java:49-78`, `user/Role.java:18-29`
- Materiał porównawczy (nieprzenoszony): gałąź `feature/assessment-contract-harness_old`
- Lekcje: `context/foundation/lessons.md`; roadmapa F-02/S-02: `context/foundation/roadmap.md`

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

- [x] 2.1 Testy jednostkowe przechodzą bez Dockera i bez zmiennych LLM: `cd szczur-radar-api && ./mvnw -B test` — cb0c022
- [x] 2.2 Pełna weryfikacja przechodzi: `cd szczur-radar-api && ./mvnw -B verify` — cb0c022
- [x] 2.3 Wyjścia przykładów 1–7 z `examples.md` przechodzą walidator bez naruszeń (test) — cb0c022
- [x] 2.4 Każda reguła D1–D15 ma test negatywny z asercją klasy i co najmniej jeden pozytywny; każda zabroniona kombinacja §7.2 ma test — cb0c022
- [x] 2.5 Cytat różniący się od pola wyłącznie znakami ze zbioru §10 przechodzi; zmieniona odmiana i „[...]” dają `fabricated_evidence`; limity liczone w code pointach (test „🚀”) — cb0c022
- [x] 2.6 `contract-v2.md` nie odwołuje się do Pythona: `! grep -n "isspace\|Python" context/foundation/llm-assessment/contract-v2.md` — cb0c022

#### Manual

- [x] 2.7 Enumy, limity i reguły w kodzie oraz schemacie zgadzają się 1:1 z `contract-v2.md` (przegląd obok siebie) — cb0c022
- [x] 2.8 Użytkownik zaakceptował redakcyjną zmianę §10 — cb0c022

### Phase 3: Infrastruktura LLM (dawniej 3A)

#### Automated

- [x] 3.1 Testy jednostkowe przechodzą offline: `cd szczur-radar-api && ./mvnw -B test` — fdedac9
- [x] 3.2 Pełna weryfikacja przechodzi: `cd szczur-radar-api && ./mvnw -B verify` — fdedac9
- [x] 3.3 Serwer testowy otrzymuje dokładnie tyle żądań, ile prób zgłasza `LlmResponse.attempts` / `LlmCallException` (brak ukrytych ponowień SDK i frameworka) dla każdego dostawcy — fdedac9
- [x] 3.4 Schemat wysłany do Gemini nie zawiera `minItems`/`maxItems`, a do DeepSeek i OpenRouter także `minLength`/`maxLength` (test) — fdedac9
- [x] 3.5 Błędne `LLM_SERVICE_TIER` / `LLM_REASONING_EFFORT` / `LLM_TIMEOUT` oraz `flex` dla dostawcy innego niż `openrouter` zatrzymują start z komunikatem bez klucza (test) — fdedac9
- [x] 3.6 Plan Railway nie proponuje usunięć: job `railway-config` na PR zielony — fdedac9

#### Manual

- [x] 3.7 Payloady w `fixtures/<dostawca>/` są prawdziwymi odpowiedziami API bez kluczy i danych osobowych — fdedac9

### Phase 4: Prompt, Assessor i agent (dawniej 3B)

#### Automated

- [x] 4.1 Pełna weryfikacja przechodzi bez wywołań LLM: `cd szczur-radar-api && ./mvnw -B verify` — 5732dde
- [x] 4.2 Każda klasa błędu ma test na sztucznym kliencie, a ocenialny wpis wywołuje klienta dokładnie raz; wpis bez treści — zero razy — 5732dde
- [x] 4.3 `evaluate` na surowej odpowiedzi daje wynik identyczny z `assess` na kliencie zwracającym tę odpowiedź — 5732dde
- [x] 4.4 `AssessorAgentIT` przechodzi; `SkeletonAgent` nie istnieje — 5732dde
- [x] 4.5 Domyślne `./mvnw -B test` nie uruchamia testów z tagiem `measurement` — 5732dde

#### Manual

- [x] 4.6 `LiveSmokeTest` wykonany trzy razy (Gemini `gemini-3.1-flash-lite`, DeepSeek Flash, OpenRouter `openai/gpt-6-luna` z `LLM_SERVICE_TIER=flex`): dla każdego zgodność z kontraktem ≥90% i najwyżej jedno `provider_error` lub `invalid_structured_output`; wyniki (z mianownikiem i dla Luny odsetkiem odmów 429 i medianą czasu) w opisie PR — 5732dde
- [x] 4.7 Pochodzenie ma wypełnione tokeny (w tym cache, gdy dostawca je zgłasza), wersję promptu i kontraktu dla wszystkich trzech; potwierdzona nazwa modelu DeepSeek Flash przyjmowana przez API — 5732dde

### Phase 5: Zestaw wzorcowy

#### Automated

- [x] 5.1 Testy mappera, miar i danych wzorca przechodzą: `cd szczur-radar-api && ./mvnw -B test` — b21d084
- [x] 5.2 Wzorzec ma co najmniej 100 postów nie-„trudnych” z dwóch różnych dni, żadnego z 2026-09-28 ani 2026-09-29 (`GoldDataTest`) — b21d084
- [x] 5.3 `posts.json` i `labels.json` nie zawierają nicków autorów ani adresów URL (`GoldDataTest`) — b21d084
- [x] 5.4 Każda miara ma test na danych syntetycznych z ręcznie policzonym wynikiem — b21d084

#### Manual

- [x] 5.5 Użytkownik oznaczył wszystkie posty w `labeling-gold.html` według instrukcji v1.0 i wyeksportował `labels.json` — b21d084
- [x] 5.6 Użytkownik przejrzał notatki i posty „trudne”; brakujące reguły dopisane do `contract-v2.md` i instrukcji (z aktualizacją etykiet) albo świadomie odłożone — b21d084

### Phase 6: Pomiar, raport i model domyślny

#### Automated

- [x] 6.1 Domyślne `cd szczur-radar-api && ./mvnw -B verify` nie uruchamia testów `measurement`, uruchamia `*IT` i odtwarza wszystkie bieżące nagrania z identycznym werdyktem — 6d65f19
- [x] 6.2 Konfiguracja `default` ma komplet nagrań dla wszystkich postów wzorca z bieżącymi `PROMPT_VERSION` i `CONTRACT_VERSION` — 6d65f19
- [x] 6.3 Miary konfiguracji `default` spełniają progi: zgodność z kontraktem ≥95%, trafność binarna ≥80%, recall celów ≥80%, precision celów ≥70%, kierunek na dopasowanych celach ze stanowiskiem ≥70%, dominujący kierunek posta ≥75%, sprzeczne kierunki ≤10% (kierunek i sprzeczności złagodzone z ≥75% / ≤3% decyzją użytkownika 2026-10-06, raport `gold-2026-10-06.md`) (`RecordingsReplayTest`) — 6d65f19
- [x] 6.4 Raport generuje się bez błędów do `context/changes/assessment-contract-harness/reports/` — 6d65f19
- [ ] 6.5 CI na PR (`backend`, `web-unit`, `web-container-smoke`, `railway-config`) jest zielone

#### Manual

- [x] 6.6 `GoldRecorder` wykonany trzy razy (Luna flex, DeepSeek Flash, Gemini 3.1 Flash-Lite) na całym wzorcu; raport zawiera jedną tabelę jakości i kosztu tych trzech modeli (Luna nagrana w trybie standardowym; tryb uznany za obojętny dla odpowiedzi — N13/N16, przegląd fazy 6 F2) — 6d65f19
- [x] 6.7 Użytkownik przejrzał raport i zatwierdził model domyślny oraz szacowany koszt miesięczny; jednorazowe klucze usunięte (koszt zatwierdzony 2026-10-06, N17; klucze usunięte przez użytkownika 2026-10-06) — 6d65f19
- [x] 6.8 Jeśli progi zostały zmienione albo żaden kandydat ich nie spełnił, decyzja użytkownika i uzasadnienie są w raporcie — 6d65f19
