<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Egzekwowany kontrakt oceny LLM V1

- **Plan**: context/changes/assessment-contract-harness/plan.md
- **Mode**: Deep
- **Date**: 2026-09-28
- **Verdict**: REVISE → SOUND (po triage)
- **Findings**: 0 critical, 5 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | PASS |

## Grounding
5/5 paths ✓; 3 anchory file:line przesunięte (F7); brief↔plan ✓; Progress↔Phase 18/18 ✓. Twierdzenia o LangChain zweryfikowane w źródłach: langchain 1.4.2, langchain-deepseek 1.1.1, langchain-google-genai 4.4.0, pytest 9.1.1.

## Findings

### F1 — Surowa odpowiedź nie jest jednolitym tekstem

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Phase 2 — Port dostawcy
- **Detail**: Przy function calling (Anthropic domyślnie, DeepSeek zawsze — langchain-deepseek zamienia json_schema na function_calling) JSON jest w `raw.tool_calls[0].args`, zepsuty w `invalid_tool_calls`, a `content` bywa listą bloków; jeden `raw_text` z treści nie działa.
- **Fix**: Adapter per dostawca za wspólnym interfejsem (propozycja użytkownika): json_schema + `raw.text` dla OpenAI/Anthropic/Gemini; function_calling strict + odczyt `tool_calls`/`invalid_tool_calls` dla DeepSeek; testy `extract_raw` offline.
- **Decision**: FIXED (adapter per dostawca)

### F2 — Timeout i ponowienia różnią się per dostawca

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 2 — Port dostawcy
- **Detail**: Gemini zgłasza surowy `httpx.TimeoutException` (nie `ModelTimeoutError`) i domyślnie ponawia 6×.
- **Fix**: `(ModelTimeoutError, httpx.TimeoutException)` → `timeout`; `max_retries=2` we wszystkich adapterach; test obu typów timeoutu.
- **Decision**: FIXED

### F3 — Luka kontraktu vs. warunek done 12×3

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Phase 3 — 3.2/3.3 vs 3.8
- **Detail**: 3.8 pozwalało opisać przypadek niezaliczany przez wszystkie modele jako lukę kontraktu, a done wymaga 36/36.
- **Fix A ⭐ Recommended**: Luka blokuje done; zmiana `contract-v1.md` (akceptacja użytkownika) w tej zmianie + ponowny przebieg; bez wyłączeń.
- **Fix B**: Done z jawnie zatwierdzonym wyłączeniem przypadku.
- **Decision**: FIXED (Fix A)

### F4 — Semantyka przebiegu live i filtr na model domyślny

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 — test_live.py, 3.3
- **Detail**: Brak komendy zawężającej macierz do jednego modelu; nieokreślone, czy test live asertuje zaliczenie.
- **Fix**: Env `ASSESSMENT_LIVE_MODELS` zawęża macierz; testy live tylko nagrywają (bez asercji), werdykt ocenia test nagrań offline i raport.
- **Decision**: FIXED (wariant użytkownika: tylko filtr modelu, bez czerwonych testów live)

### F5 — Nagrania: werdykt zapisany na sztywno + mieszanie wersji promptu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 — test_recordings.py, raport
- **Detail**: Poprawka walidatora wymuszałaby ręczną edycję werdyktów w nagraniach; raport porównywałby modele na różnych wersjach promptu.
- **Fix**: `--update-verdicts` w skrypcie raportu; raport liczy tylko nagrania z bieżącym `PROMPT_VERSION`, starsze osobno.
- **Decision**: FIXED

### F6 — Niezdefiniowane dopasowanie „target z anchorem X”

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 — cases.py
- **Detail**: Nie określono, czy dopasowanie to równość czy zawieranie.
- **Fix**: X jako podciąg `anchor.quote`, z rozróżnieniem wielkości liter.
- **Decision**: FIXED

### F7 — Kotwice file:line przesunięte, brak .env w .gitignore

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Key Discoveries, References, Phase 2
- **Detail**: `test_health.py:24→29`, `ci.yml:23→25`, `database.py:30→38`; `.gitignore` bez `.env`.
- **Fix**: Poprawione numery; faza 2 pkt 1a dodaje `.env`/`.env.*` do `.gitignore`.
- **Decision**: FIXED
