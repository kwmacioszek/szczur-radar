# Egzekwowany kontrakt oceny LLM V1 — plan implementacji

## Overview

Roadmapa F-02 (`assessment-contract-harness`). Kontrakt oceny LLM V1 (`context/foundation/llm-assessment/contract-v1.md`, accepted) przestaje być wyłącznie dokumentem i staje się kodem w `szczur-radar-api/app/assessment/`. Powstają zamknięte modele wejścia i wyjścia, walidator wszystkich 16 niezmienników z jawną klasyfikacją błędów oraz funkcja `assess()` wywołująca dowolnego skonfigurowanego dostawcę LLM (OpenAI / Gemini / DeepSeek / Claude) przez LangChain. Całość ma dowód działania: 12 wymaganych przypadków testowych uruchamianych 3× na macierzy prawdziwych modeli, nagrania odpowiedzi w repo sprawdzane offline w CI oraz raport porównawczy (zaliczenia, naruszenia, tokeny, koszt). Zmiana zamyka ryzyko jakości oceny, zanim S-02 zbuduje przepływ pobierania, zapisu i widoku.

## Current State Analysis

- API to szkielet FastAPI: `szczur-radar-api/main.py` montuje tylko router sond (`app/routes.py:15`: `/api/health`, `/api/ready`), `app/database.py` robi wyłącznie `SELECT 1`. Brak modeli, integracji z LLM, konfiguracji pytest poza domyślną.
- Testy: pytest + httpx w grupie `dev` (`szczur-radar-api/pyproject.toml`), jedyny plik `tests/test_health.py` (wzorzec: `monkeypatch` na atrybucie modułu zamiast prawdziwej zależności).
- CI (`.github/workflows/ci.yml`, job `backend`) wykonuje `uv sync --locked` + `uv run pytest -q` **bez sekretów**. Workflow nie może być filtrowany ścieżkami (Railway „Wait for CI”). Każdy merge do `main` = deploy na produkcję.
- Kontrakt V1 jest kompletny i normatywny: enumy (§9), 16 niezmienników (§10), limity (§11), zakazy (§13), reguła zmiany (§14). `agent-implementation.md` podaje wymagania promptu (§2), walidacje backendu (§3), klasy błędów (§4), 12 wymaganych przypadków i 10 testów walidatora (§5), pochodzenie (§6). `examples.md` zawiera wejścia i oczekiwane zachowanie dla przypadków.
- `tech-stack-api.md` zakłada Pydantic + LangGraph (warstwa pipeline niezależna od HTTP). F-02 nie potrzebuje grafu — wystarcza funkcja za portem dostawcy, którą S-02 osadzi w węźle LangGraph.
- Dostawcy różnią się gwarancją struktury (np. DeepSeek ma tylko tryb JSON; strict schema OpenAI/Anthropic nie obsługuje części ograniczeń jak `maxLength`/limity tablic). Jedyną wiarygodną bramką jest nasza walidacja surowej odpowiedzi.

## Desired End State

- `app/assessment/` udostępnia: modele `AssessmentInputV1` / `AssessmentOutputV1` 1:1 z kontraktem, walidator zwracający listę naruszeń sklasyfikowanych według klas z `agent-implementation.md` §4, oraz `assess(input, provider) -> AssessmentResult` z pochodzeniem (dostawca, model, wersja promptu, wersja kontraktu, znacznik czasu, tokeny, czas trwania) albo jawnym błędem — bez ponowień poza transportowymi.
- Dostawca i model wybierane konfiguracją `provider:model` (env `ASSESSMENT_MODEL`); nowy dostawca = konfiguracja + pakiet integracji, bez zmiany kodu oceny.
- `uv run pytest -q` (CI, offline) sprawdza modele, walidator (10 zabronionych przypadków + poprawne przykłady), `assess()` na sztucznym dostawcy oraz wszystkie nagrania z prawdziwych modeli.
- `uv run pytest -m live` uruchamia 12 przypadków × 3 × macierz modeli i zapisuje nagrania; skrypt raportu generuje raport porównawczy w `context/changes/assessment-contract-harness/reports/`.
- Co najmniej jeden model przechodzi 12×3 (walidator + asercje semantyczne) i jest ustawiony jako domyślny `ASSESSMENT_MODEL` dla S-02; macierz została uruchomiona co najmniej także na DeepSeek i Gemini, a ich wyniki są w raporcie (ich zaliczenie nie jest warunkiem done).

Weryfikacja: zielone CI na PR, raport w folderze zmiany, nagrania modelu domyślnego 36/36 zaliczone.

### Key Discoveries:

- `szczur-radar-api/tests/test_health.py:29` — wzorzec podmiany zależności przez `monkeypatch.setattr` na module; sztuczny dostawca w fazie 2 idzie tą samą drogą (wstrzyknięcie przez parametr/port, nie sieć).
- `szczur-radar-api/app/database.py:38` — konfiguracja z `os.environ` czytana leniwie przy pierwszym użyciu, więc import modułu nie wymaga zmiennych; konfiguracja modelu oceny powiela ten wzorzec (import bez kluczy API).
- `.github/workflows/ci.yml:25` — `uv sync --locked`: nowe zależności muszą trafić do `uv.lock` w tym samym commicie.
- `contract-v1.md` §10 pkt 8 — „exact substring”: dopasowanie dosłowne, bez normalizacji białych znaków/Unicode; odstępstwo = `fabricated_evidence`, a trwale niezaliczany przypadek idzie ścieżką §14 (failing fixture → zmiana kontraktu), nie poluzowaniem walidatora.
- `context/foundation/lessons.md` — „Jedna gałąź + jeden PR”: cała zmiana na jednej gałęzi `feature/assessment-contract-harness`, jeden PR, merge do `main` raz po fazie 3.

## What We're NOT Doing

- Zapis ocen do bazy, migracje, pochodzenie przechowywane w Postgresie (S-02). `AssessmentResult` niesie pochodzenie w pamięci; utrwalenie należy do S-02.
- Pobieranie z Wykopu, mapowanie RAW → `AssessmentInputV1`, deduplikacja, UI, trasy API (S-02/S-03).
- Graf LangGraph i uruchamianie z crona (S-02/S-05).
- Polityka ponowień przy naruszeniu kontraktu, ponowienia z komunikatem walidatora, „naprawianie” outputu — decyzja: brak ponowień poza transportowymi.
- Sekrety dostawców LLM w Railway i w CI (F-01 zapieczętuje sekrety produkcyjne); klucze tylko w lokalnym środowisku powłoki.
- Testy na prawdziwych modelach w CI (koszt, niedeterminizm, ryzyko blokowania deployu).
- Canonical entity resolution, novelty, trend, engagement, `is_signal` / `is_valuable` (kontrakt §12–13).
- Rozszerzanie enumów lub struktury kontraktu w kodzie; każda luka kontraktu → reguła zmiany §14.
- Normalizacja cytatów przy sprawdzaniu evidence.

## Implementation Approach

Trzy fazy od najbardziej deterministycznej do najdroższej. Faza 1 to czysty kod bez sieci: kontrakt jako modele + walidator + testy — to jest trwała „ścieżka weryfikacji V1”, z której skorzysta S-02. Faza 2 dokłada integrację z dostawcami za portem, prompt i `assess()`, testowane wyłącznie sztucznym dostawcą, więc CI dalej nie potrzebuje kluczy. Faza 3 wprowadza koszt: przypadki testowe na prawdziwych modelach (opt-in), nagrania stające się regresyjnym testem offline oraz raport porównawczy, na którego podstawie użytkownik wybiera model domyślny.

Jedna prawda o kształcie: modele Pydantic z fazy 1. Schemat wysyłany do dostawcy jest z nich wyprowadzany (w razie potrzeby uproszczony w adapterze), ale surowa odpowiedź zawsze przechodzi przez pełny model + walidator — nigdy przez wersję uproszczoną.

## Critical Implementation Details

**Kolejność walidacji i klasyfikacja.** Surowa odpowiedź → parsowanie JSON (błąd: `invalid_structured_output`) → pełny model Pydantic z limitami §11 (błąd: `schema_violation`) → walidator domenowy. Walidator zbiera **wszystkie** naruszenia, a klasa błędu wyniku to klasa pierwszego naruszenia w porządku: `invalid_reference` → `fabricated_evidence` → `domain_invariant_violation`. Mapowanie: niezmienniki 1–5 i limity → `schema_violation`; 6–7 → `invalid_reference`; 8 oraz cytat wskazujący nieistniejący dokument/pole (parent `null`, pole `null`) → `fabricated_evidence`; 9–16 → `domain_invariant_violation`.

**Niezmiennik 13 obejmuje `mixed`.** Decyzja planu: `bullish | bearish | neutral | mixed` wymagają ≥1 `stance.claim_ids` wskazującego claim z `attribution = author`; `mixed` oparty wyłącznie na claimach `reported`/`unclear` (lub z pustym `claim_ids`) → `domain_invariant_violation`. To jest surowsze niż dosłowny niezmiennik 16 — test walidatora musi to jawnie pokrywać.

**Pusty cytat.** `quote` o długości 0 jest podciągiem każdego tekstu, więc model wymaga `quote` niepustego (min. 1 znak) — inaczej sprawdzenie evidence byłoby puste.

**Strukturyzowane wyjście per dostawca.** `with_structured_output(..., include_raw=True)` z metodą ustaloną w adapterze dostawcy: json_schema dla OpenAI / Anthropic / Gemini (JSON w `raw.text`), function calling `strict=True` dla DeepSeek (JSON w `raw.tool_calls[0].args` lub `raw.invalid_tool_calls`). Metoda i odczyt są konfiguracją adaptera, nie kontraktem; wynik sparsowany przez LangChain (`parsed`) jest ignorowany na rzecz walidacji surowej treści naszym modelem. `parsing_error` łapie tylko błędy parsera — błędy API i timeouty nadal rzucają wyjątki i są mapowane w klasie bazowej.

## Phase 1: Kontrakt jako kod

### Overview

Modele Pydantic 1:1 z kontraktem V1, walidator 16 niezmienników z klasyfikacją naruszeń i testy offline pokrywające przykłady poprawne i 10 wymaganych przypadków zabronionych.

### Changes Required:

#### 1. Modele kontraktu

**File**: `szczur-radar-api/app/assessment/__init__.py`, `szczur-radar-api/app/assessment/contract.py`

**Intent**: Odwzorować `AssessmentInputV1` i `AssessmentOutputV1` jako zamknięte modele, żeby każdy obcy klucz, brakujący klucz, wartość spoza enumu lub przekroczony limit był odrzucany u źródła.

**Contract**: Modele z `extra="forbid"` na każdym obiekcie; enumy jako `Literal[...]` dokładnie z §9; wszystkie klucze wymagane (brak tekstu = `null`, brak elementów = `[]`); `assertiveness: Literal["hedged","unhedged"] | None` wymagane. Limity §11 jako ograniczenia pól (targets ≤ 12, claims ≤ 24, evidence 1..2, `evidence.quote` 1..320, `anchor.quote` 1..100, `statement` ≤ 240, `supported_by_claim_ids` ≤ 4, `stance.claim_ids` ≤ 4). Walidacja wejścia: niezmienniki 4–5 (`item.title`/`item.body` nie oba puste; parent z ≥1 niepustym polem) jako walidatory modelu wejścia. Stała `CONTRACT_VERSION = "V1"`. Brak pól `score`, `confidence`, `sentiment`, `is_signal`, `is_valuable`, `schema_version` w output.

#### 2. Walidator domenowy i klasy błędów

**File**: `szczur-radar-api/app/assessment/validation.py`

**Intent**: Sprawdzić wszystko, czego JSON Schema nie wyrazi: unikalność i istnienie referencji, acykliczność grafu wsparcia, cytaty jako dokładne podciągi wskazanych pól wejścia, zgodność anchorów z `reference`, reguły stance — i zwrócić pełną listę naruszeń zamiast pierwszego wyjątku.

**Contract**: `validate_assessment(input: AssessmentInputV1, output: AssessmentOutputV1) -> list[Violation]`; `Violation(failure_class, invariant: int, path: str, message: str)`; `FailureClass` = `provider_error | timeout | invalid_structured_output | schema_violation | invalid_reference | fabricated_evidence | domain_invariant_violation`. Pokrywa niezmienniki 6–16 zgodnie z mapowaniem z „Critical Implementation Details”, w tym: brak duplikatów w tablicach referencji, brak self-reference i cykli w `supported_by_claim_ids`, każdy claim ma ≥1 evidence z `document = item`, `stance.claim_ids` wskazują claims, których `target_ids` zawiera dany target, niezmiennik 13 obejmuje `mixed`. Funkcja pomocnicza do parsowania surowego JSON → (`AssessmentOutputV1` | naruszenie `invalid_structured_output` / `schema_violation`), używana przez fazy 2–3.

#### 3. Testy kontraktu i walidatora

**File**: `szczur-radar-api/tests/assessment/__init__.py`, `tests/assessment/test_contract.py`, `tests/assessment/test_validation.py`

**Intent**: Udowodnić offline, że kod i kontrakt mają ten sam kształt V1 oraz że każdy zabroniony przypadek jest wykrywany z właściwą klasą.

**Contract**: Pozytywne: pełne wyjścia z `examples.md` (§1 teza z argumentem, §2 pytanie bez stance, §11 treść nieinwestycyjna, §8 contextual target) przechodzą bez naruszeń. Negatywne (po jednym teście, z asercją klasy): fabricated quote, missing target reference, missing claim reference, duplicate ID, support self-reference, support cycle, explicit anchor from parent, contextual anchor from item, not_expressed with assertiveness, not_expressed with claim_ids — oraz dodatkowo: `mixed` oparty tylko na claimie `reported`, claim bez evidence z `item`, cytat z `parent_post = null`, pusty cytat, obce pole (`is_signal`), wartość spoza enumu, przekroczony limit (13 targetów), wejście z pustym `title` i `body`.

### Success Criteria:

#### Automated Verification:

- Wszystkie testy backendu przechodzą offline: `cd szczur-radar-api && uv run pytest -q`
- Każdy z 10 wymaganych przypadków zabronionych (agent-implementation.md §5) ma test z asercją oczekiwanej klasy błędu i przechodzi
- Pełne przykłady wyjść z examples.md (§1, §2, §8, §11) przechodzą walidator bez naruszeń
- Obce pole, wartość spoza enumu i przekroczony limit są odrzucane jako `schema_violation`

#### Manual Verification:

- Enumy i limity w `app/assessment/contract.py` zgadzają się 1:1 z §9 i §11 `contract-v1.md` (przegląd obok siebie)

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 2.

---

## Phase 2: Dostawcy, prompt i `assess()`

### Overview

Port dostawcy z adapterem per dostawca na LangChain, prompt V1 z wersją, funkcja `assess()` składająca wywołanie + walidację w wynik z pochodzeniem lub jawnym błędem. Testy wyłącznie na sztucznym dostawcy.

### Changes Required:

#### 1. Zależności

**File**: `szczur-radar-api/pyproject.toml`, `szczur-radar-api/uv.lock`

**Intent**: Dodać integracje LangChain dla czterech rozważanych dostawców jako zależności aplikacji (S-02 użyje ich w pipeline).

**Contract**: `uv add langchain langchain-openai langchain-anthropic langchain-google-genai langchain-deepseek`; `uv.lock` zaktualizowany w tym samym commicie (`uv sync --locked` w CI).

#### 1a. Ochrona kluczy API

**File**: `.gitignore`

**Intent**: Klucze dostawców trzymane lokalnie nie mogą trafić do repo, jeśli ktoś użyje pliku `.env`.

**Contract**: Dodane wpisy `.env` i `.env.*`.

#### 2. Port dostawcy i adaptery per dostawca

**File**: `szczur-radar-api/app/assessment/providers/__init__.py`, `providers/base.py`, `providers/openai.py`, `providers/anthropic.py`, `providers/gemini.py`, `providers/deepseek.py`

**Intent**: Oddzielić ocenę od konkretnego dostawcy, żeby porównywać modele konfiguracją i testować `assess()` bez sieci. Każdy dostawca ma własny adapter, bo dostawcy oddają JSON w różnych miejscach odpowiedzi (json_schema → tekst; function calling → `tool_calls`), a DeepSeek nie ma json_schema w API (`langchain-deepseek` po cichu zamienia je na function calling).

**Contract**: `base.py`: protokół `AssessmentProvider` z metodą `complete(system_prompt: str, input: AssessmentInputV1) -> ProviderResponse`; `ProviderResponse(raw_text: str | None, provider: str, model: str, input_tokens: int | None, output_tokens: int | None)`; klasa bazowa `LangChainProvider` — buduje model przez `init_chat_model`, woła `with_structured_output(AssessmentOutputV1 lub schemat z niego wyprowadzony, include_raw=True, method=self.method)`, bierze tokeny z `raw.usage_metadata`, mapuje błędy transportu/dostawcy (w tym odmowę) na `provider_error`, przekroczenie czasu na `timeout`; metoda abstrakcyjna `extract_raw(raw: AIMessage) -> str | None` (`None` = brak JSON → `invalid_structured_output`). Adaptery:
- `openai.py`, `anthropic.py`, `gemini.py`: `method="json_schema"`; `extract_raw` zwraca `raw.text` (nie `raw.content` — treść może być listą bloków).
- `deepseek.py`: `method="function_calling"`, `strict=True`; `extract_raw` zwraca `json.dumps(raw.tool_calls[0]["args"])`, albo surowy string z `raw.invalid_tool_calls[0]["args"]`, albo `None`.

`__init__.py`: rejestr `from_spec("provider:model", timeout_s=...)` wybierający adapter po prefiksie (`openai`, `anthropic`, `google_genai`, `deepseek`); nieznany prefiks → błąd konfiguracji. Ponowienia wyłącznie transportowe wbudowane w klienta (429/5xx/połączenie), ustawione jawnie na `max_retries=2` w każdym adapterze (Gemini domyślnie ma 6). Timeout = `(langchain_core.exceptions.ModelTimeoutError, httpx.TimeoutException)` → `timeout` (Gemini zgłasza surowy `httpx.TimeoutException`, pozostali `ModelTimeoutError`).

#### 3. Prompt V1

**File**: `szczur-radar-api/app/assessment/prompt.py`

**Intent**: Jeden wersjonowany prompt systemowy realizujący wymagania `agent-implementation.md` §2 i zakazy `contract-v1.md` §13, z semantyką enumów z §4–8.

**Contract**: Stała `PROMPT_VERSION` (np. `"assessment-prompt-v1.0"`) i `SYSTEM_PROMPT`; funkcja budująca wiadomość użytkownika z `input.model_dump_json()` ujętego jako niezaufany materiał do analizy. Do modelu trafiają wyłącznie pola `AssessmentInputV1` (żadnych source, autora, URL, engagement, ID). Zmiana treści promptu = podbicie `PROMPT_VERSION`.

#### 4. Konfiguracja i `assess()`

**File**: `szczur-radar-api/app/assessment/config.py`, `szczur-radar-api/app/assessment/service.py`

**Intent**: Złożyć wywołanie dostawcy i pełną walidację w jeden wynik, który S-02 zapisze bez interpretacji.

**Contract**: `config.py`: `get_assessment_model_spec()` czyta `ASSESSMENT_MODEL` leniwie (domyślna wartość ustawiana w fazie 3), `get_default_provider()`. `service.py`: `assess(input: AssessmentInputV1, provider: AssessmentProvider | None = None) -> AssessmentResult`; `AssessmentResult(status: "ok" | "failed", output: AssessmentOutputV1 | None, failure_class: FailureClass | None, violations: list[Violation], provenance: Provenance, raw_text: str | None)`; `Provenance(provider, model, prompt_version, contract_version, assessed_at, input_tokens, output_tokens, duration_ms)`. Dokładnie jedno wywołanie dostawcy na ocenę; wynik z naruszeniami nigdy nie ma `status = "ok"`.

#### 5. Testy `assess()`

**File**: `szczur-radar-api/tests/assessment/test_service.py`

**Intent**: Udowodnić klasyfikację błędów i brak ponowień bez sieci.

**Contract**: `extract_raw` każdego adaptera testowany na spreparowanych `AIMessage` (tekst, lista bloków, `tool_calls`, `invalid_tool_calls`, brak JSON). Sztuczny dostawca zwracający zadany surowy tekst lub rzucający błąd; przypadki: poprawny wynik → `ok` z wypełnionym pochodzeniem; błąd dostawcy → `provider_error`; timeout (zarówno `ModelTimeoutError`, jak i `httpx.TimeoutException`) → `timeout`; nie-JSON → `invalid_structured_output`; obce pole → `schema_violation`; zmyślony cytat → `fabricated_evidence`; sztuczny dostawca wywołany dokładnie raz przy każdym błędzie; wiadomość do dostawcy zawiera tylko pola `AssessmentInputV1`.

### Success Criteria:

#### Automated Verification:

- Zależności zablokowane i zsynchronizowane: `cd szczur-radar-api && uv sync --locked`
- Wszystkie testy backendu przechodzą offline, bez kluczy API: `uv run pytest -q`
- Każda klasa błędu (`provider_error`, `timeout`, `invalid_structured_output`, `schema_violation`, `fabricated_evidence`) ma test na sztucznym dostawcy, a dostawca jest wywoływany dokładnie raz
- Import `app.assessment` nie wymaga żadnej zmiennej środowiskowej

#### Manual Verification:

- Pojedyncze lokalne wywołanie `assess()` na jednym prawdziwym modelu (z kluczem w powłoce) zwraca `ok` albo sklasyfikowany błąd z wypełnionymi tokenami i wersją promptu

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 3.

---

## Phase 3: Przypadki testowe na prawdziwych modelach i raport

### Overview

12 wymaganych przypadków z asercjami semantycznymi, opt-in przebieg `live` na macierzy modeli (3 uruchomienia każdy), nagrania w repo odtwarzane offline w CI, raport porównawczy i wybór modelu domyślnego.

### Changes Required:

#### 1. Przypadki testowe i asercje semantyczne

**File**: `szczur-radar-api/tests/assessment/cases/<case_id>.json`, `szczur-radar-api/tests/assessment/cases.py`

**Intent**: Zapisać 12 wymaganych przypadków (`agent-implementation.md` §5) jako wejścia z `examples.md` i sprawdzalne oczekiwania, żeby „przechodzi” znaczyło to samo dla każdego modelu.

**Contract**: Każdy JSON = poprawny `AssessmentInputV1` (stały `published_at`, `channels: ["gielda"]`; `contextual_target` jako `item_type: comment` z `parent_post`). `cases.py`: mapa `case_id -> predykat(output) -> list[str]` (puste = zaliczone). Oczekiwania:
- `thesis_with_support`: target z anchorem „CD Projekt” → `bullish`; ≥1 claim `investment_thesis` autora z niepustym `supported_by_claim_ids`.
- `thesis_without_support`: ≥1 claim `investment_thesis` z `supported_by_claim_ids = []`; target → `bullish`.
- `question_without_stance`: target „KGHM” → `not_expressed`; ≥1 claim `question`.
- `factual_information_without_stance`: `investment_related`; ≥1 target, wszystkie `unresolved` i `not_expressed`; wszystkie claims `factual_claim`.
- `two_targets_opposite_stance`: Allegro → `bearish`, XTB → `bullish`; ≥1 claim `trade_action`.
- `negative_tone_bullish_stance`: target → `bullish`.
- `reported_claim`: ≥1 claim `reported`; żaden target nie ma `bullish | bearish | neutral | mixed`.
- `contextual_target`: target `contextual` z anchorem z `parent_post` zawierającym „XTB”; ≥1 claim `trade_action` autora.
- `ambiguous_target`: target „Apple” → `reference = ambiguous`.
- `unresolved_target`: target `unresolved` → `bullish`.
- `sarcasm`: nie jest prawdą, że target → `bullish` bez flagi `possible_sarcasm`.
- `non_investment_content`: `not_investment_related`, `targets = []`, `claims = []`, `modifiers = []`, `flags = []`.

„Target z anchorem X” oznacza target, którego `anchor.quote` zawiera X jako podciąg (z rozróżnieniem wielkości liter), np. „KGHM” pasuje do „akcje KGHM”. Przypadek zalicza się, gdy wynik `assess()` ma `status = ok` i predykat zwraca pustą listę.

#### 2. Macierz modeli i przebieg `live`

**File**: `szczur-radar-api/tests/assessment/live_models.toml`, `szczur-radar-api/tests/assessment/test_live.py`, `szczur-radar-api/pyproject.toml`

**Intent**: Uruchamiać przypadki na prawdziwych modelach tylko na żądanie i zapisywać każdą odpowiedź jako nagranie.

**Contract**: `live_models.toml` — lista wpisów `{spec = "provider:model", price_in_per_mtok, price_out_per_mtok}` (macierz + cennik do raportu; nadpisywalna env `ASSESSMENT_LIVE_MODELS`). `pyproject.toml`: `[tool.pytest.ini_options]` z markerem `live` i `addopts = "-m 'not live'"` (CI i domyślne `pytest` pomijają live; `uv run pytest -m live` je włącza). `test_live.py`: parametryzacja model × przypadek × uruchomienie 1..3; model bez klucza API w env → `skip` z nazwą brakującej zmiennej. Env `ASSESSMENT_LIVE_MODELS` (lista `provider:model` po przecinku) zawęża macierz, np. do jednego modelu; bez niej leci cała lista z `live_models.toml`. Testy live tylko nagrywają (nie asertują zaliczenia) — werdykt ocenia test nagrań offline i raport. Każde uruchomienie zapisuje nagranie `tests/assessment/recordings/<provider>__<model>/<case_id>__run<n>.json` = `{case_id, spec, run, prompt_version, contract_version, recorded_at, raw_text, input_tokens, output_tokens, duration_ms, status, failure_class, violations, semantic_failures}`. Nagrania zapisywane są także dla niezaliczonych uruchomień.

#### 3. Odtwarzanie nagrań offline (CI)

**File**: `szczur-radar-api/tests/assessment/test_recordings.py`, `szczur-radar-api/app/assessment/config.py`

**Intent**: Zamienić prawdziwe odpowiedzi modeli w regresyjny test walidatora i asercji, który CI uruchamia bez sekretów i kosztów.

**Contract**: Dla każdego nagrania: ponowne przepuszczenie `raw_text` przez parsowanie + walidator + predykat daje dokładnie zapisane `status`, `failure_class` i `semantic_failures` (nagrania `provider_error`/`timeout` bez `raw_text` pomijane w ponownej walidacji). Dla modelu domyślnego (`DEFAULT_ASSESSMENT_MODEL` w `config.py`, ustawiony na model wybrany w tej fazie): istnieje komplet 12×3 nagrań z bieżącym `PROMPT_VERSION` i wszystkie mają `status = ok` oraz puste `semantic_failures`.

#### 4. Raport porównawczy

**File**: `szczur-radar-api/scripts/assessment_report.py`, `context/changes/assessment-contract-harness/reports/live-<YYYY-MM-DD>.md`

**Intent**: Dać użytkownikowi podstawę do wyboru modelu: jakość, stabilność i koszt obok siebie.

**Contract**: `uv run python scripts/assessment_report.py --recordings tests/assessment/recordings --models tests/assessment/live_models.toml --out <ścieżka>`. Raport Markdown: tabela model × (zaliczone uruchomienia /36, przypadki zaliczone 3/3, naruszenia wg klasy, średnie tokeny wej./wyj. na ocenę, szacowany koszt na ocenę i na 100 postów, mediana czasu), sekcja niezaliczonych przypadków per model z pierwszym naruszeniem lub asercją, wersja promptu i data. Raport liczy wyłącznie nagrania z bieżącym `PROMPT_VERSION`; nagrania ze starszą wersją wypisuje osobno jako nieaktualne (do ponownego uruchomienia). Tryb `--update-verdicts` przelicza zapisane `status` / `failure_class` / `violations` / `semantic_failures` wszystkich nagrań z ich `raw_text` (po świadomej zmianie walidatora lub asercji; zmiana widoczna w diffie).

#### 5. Przebieg macierzy i wybór modelu domyślnego

**File**: `context/changes/assessment-contract-harness/reports/`, `szczur-radar-api/app/assessment/config.py`

**Intent**: Wykonać właściwy dowód F-02 i ustalić model, którego użyje S-02.

**Contract**: Przebieg `uv run pytest -m live` co najmniej na: modelu kandydującym na domyślny, DeepSeek i Gemini (pozostali dostawcy opcjonalnie). Model domyślny = model, który przeszedł 12×3, wybrany przez użytkownika po raporcie; wpisany do `DEFAULT_ASSESSMENT_MODEL`. Przypadek trwale niezaliczany przez wszystkie modele z powodu luki kontraktu **blokuje done**: zachowane nagranie jako failing fixture + opis luki w raporcie, propozycja minimalnej zmiany `contract-v1.md` do akceptacji użytkownika, a po akceptacji — w tej samej zmianie — aktualizacja kontraktu, `examples.md`, modeli/walidatora/asercji i ponowny przebieg macierzy (reguła §14). Żaden przypadek nie jest wyłączany z 12×3.

### Success Criteria:

#### Automated Verification:

- Domyślne `uv run pytest -q` pomija testy `live` i odtwarza wszystkie nagrania z identycznym werdyktem
- Model domyślny ma komplet 12×3 nagrań z bieżącym `PROMPT_VERSION`, wszystkie zaliczone (walidator + asercje)
- `ASSESSMENT_LIVE_MODELS=<model-domyślny> uv run pytest -m live` nagrywa komplet 36 uruchomień wyłącznie modelu domyślnego
- Raport generuje się bez błędów: `uv run python scripts/assessment_report.py ... --out ../context/changes/assessment-contract-harness/reports/live-<data>.md`
- CI na PR (`backend` + `web-container-smoke`) jest zielone

#### Manual Verification:

- Macierz została uruchomiona co najmniej na modelu domyślnym, DeepSeek i Gemini, a wyniki wszystkich trzech są w raporcie
- Użytkownik przejrzał raport i zatwierdził model domyślny oraz szacowany koszt na ocenę
- Każdy przypadek niezaliczany przez wszystkie modele został rozwiązany zmianą promptu z podbiciem `PROMPT_VERSION` albo zaakceptowaną zmianą `contract-v1.md` (reguła §14) z ponownym przebiegiem — bez wyłączania przypadków

**Implementation Note**: Po fazie 3 PR z całą zmianą jest gotowy do jednego merge do `main`.

---

## Testing Strategy

### Unit Tests:

- Modele: obce pola, enumy, wymagane klucze, `null` vs `[]`, limity §11, walidacja wejścia (niezmienniki 4–5).
- Walidator: 10 wymaganych przypadków zabronionych + `mixed` bez claimu autora, claim bez evidence z `item`, cytat z nieistniejącego rodzica, pusty cytat.
- `assess()`: każda klasa błędu na sztucznym dostawcy, jedno wywołanie, pochodzenie wypełnione, do modelu trafia tylko `AssessmentInputV1`.

### Integration Tests:

- `-m live`: 12 przypadków × 3 × macierz modeli na prawdziwych API (opt-in, lokalnie).
- Odtwarzanie nagrań w CI jako regresja walidatora i asercji na prawdziwych odpowiedziach.

### Manual Testing Steps:

1. Wyeksportuj klucze API wybranych dostawców w powłoce (nigdy do repo ani do Railway).
2. `cd szczur-radar-api && uv run pytest -m live` z macierzą w `live_models.toml`.
3. Wygeneruj raport do `context/changes/assessment-contract-harness/reports/` i przejrzyj niezaliczone przypadki.
4. Wybierz model domyślny, ustaw `DEFAULT_ASSESSMENT_MODEL`, uruchom `uv run pytest -q` (nagrania modelu domyślnego muszą być 36/36).

## Performance Considerations

Ocena to jedno wywołanie na post; przebieg macierzy to 36 wywołań na model. Koszt na ocenę jest mierzony (tokeny z `usage_metadata` × cennik z `live_models.toml`) i jest wejściem do Open Roadmap Question 5 (budżet LLM na przebieg) oraz S-05. Timeout wywołania konfigurowalny; brak ponowień semantycznych trzyma koszt błędu na 1 wywołaniu.

## Migration Notes

Brak zmian danych i infrastruktury. Nowe zależności Pythona zwiększają obraz API, ale kod oceny nie jest wywoływany przez żadną trasę, więc deploy na produkcję po merge nie zmienia zachowania usługi.

## References

- Roadmapa: `context/foundation/roadmap.md` (F-02)
- Kontrakt: `context/foundation/llm-assessment/contract-v1.md`
- Wymagania implementacyjne: `context/foundation/llm-assessment/agent-implementation.md`
- Przykłady: `context/foundation/llm-assessment/examples.md`
- Stack: `context/foundation/tech-stack-api.md`
- Lekcje: `context/foundation/lessons.md` (jedna gałąź + jeden PR)
- Wzorzec testów: `szczur-radar-api/tests/test_health.py:29`
- CI: `.github/workflows/ci.yml:25`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Kontrakt jako kod

#### Automated

- [ ] 1.1 Wszystkie testy backendu przechodzą offline: `cd szczur-radar-api && uv run pytest -q`
- [ ] 1.2 Każdy z 10 wymaganych przypadków zabronionych (agent-implementation.md §5) ma test z asercją oczekiwanej klasy błędu i przechodzi
- [ ] 1.3 Pełne przykłady wyjść z examples.md (§1, §2, §8, §11) przechodzą walidator bez naruszeń
- [ ] 1.4 Obce pole, wartość spoza enumu i przekroczony limit są odrzucane jako `schema_violation`

#### Manual

- [ ] 1.5 Enumy i limity w `app/assessment/contract.py` zgadzają się 1:1 z §9 i §11 `contract-v1.md` (przegląd obok siebie)

### Phase 2: Dostawcy, prompt i `assess()`

#### Automated

- [ ] 2.1 Zależności zablokowane i zsynchronizowane: `cd szczur-radar-api && uv sync --locked`
- [ ] 2.2 Wszystkie testy backendu przechodzą offline, bez kluczy API: `uv run pytest -q`
- [ ] 2.3 Każda klasa błędu (`provider_error`, `timeout`, `invalid_structured_output`, `schema_violation`, `fabricated_evidence`) ma test na sztucznym dostawcy, a dostawca jest wywoływany dokładnie raz
- [ ] 2.4 Import `app.assessment` nie wymaga żadnej zmiennej środowiskowej

#### Manual

- [ ] 2.5 Pojedyncze lokalne wywołanie `assess()` na jednym prawdziwym modelu (z kluczem w powłoce) zwraca `ok` albo sklasyfikowany błąd z wypełnionymi tokenami i wersją promptu

### Phase 3: Przypadki testowe na prawdziwych modelach i raport

#### Automated

- [ ] 3.1 Domyślne `uv run pytest -q` pomija testy `live` i odtwarza wszystkie nagrania z identycznym werdyktem
- [ ] 3.2 Model domyślny ma komplet 12×3 nagrań z bieżącym `PROMPT_VERSION`, wszystkie zaliczone (walidator + asercje)
- [ ] 3.3 `ASSESSMENT_LIVE_MODELS=<model-domyślny> uv run pytest -m live` nagrywa komplet 36 uruchomień wyłącznie modelu domyślnego
- [ ] 3.4 Raport generuje się bez błędów: `uv run python scripts/assessment_report.py ... --out ../context/changes/assessment-contract-harness/reports/live-<data>.md`
- [ ] 3.5 CI na PR (`backend` + `web-container-smoke`) jest zielone

#### Manual

- [ ] 3.6 Macierz została uruchomiona co najmniej na modelu domyślnym, DeepSeek i Gemini, a wyniki wszystkich trzech są w raporcie
- [ ] 3.7 Użytkownik przejrzał raport i zatwierdził model domyślny oraz szacowany koszt na ocenę
- [ ] 3.8 Każdy przypadek niezaliczany przez wszystkie modele został rozwiązany zmianą promptu z podbiciem `PROMPT_VERSION` albo zaakceptowaną zmianą `contract-v1.md` (reguła §14) z ponownym przebiegiem — bez wyłączania przypadków
