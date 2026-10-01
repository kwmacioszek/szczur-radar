<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Kontrakt oceny LLM V2 z walidatorem i pomiarem na wzorcu

- **Plan**: context/changes/assessment-contract-harness/plan.md
- **Mode**: Deep (zakres: zmiany z 2026-10-01 — role modeli, flex, ponowienia, podział `assess()`, OpenRouter, kryteria 3.10–3.11, notatki do 3.8/3.9/5.6/5.7/5.9)
- **Date**: 2026-10-01
- **Verdict**: REVISE → SOUND po triage
- **Findings**: 0 critical, 3 warnings, 2 observations
- **Uwaga**: zapis pod osobną nazwą, żeby nie nadpisać `plan-review.md` (przegląd wersji 1 planu); przegląd wersji 2 z triage F1–F5 z 2026-09-30 nie ma osobnego pliku — jego decyzje są w `plan.md` i `plan-brief.md`.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
9/9 paths ✓, 3/3 symbols ✓ (`app/database.py:47`, `app/auth/tokens.py:34`, marker `db`), brief↔plan ✓, Progress↔Phase ✓ (39 kryteriów, 1:1). Twierdzenia zewnętrzne zweryfikowane: `ChatOpenAI(service_tier="flex")` (LangChain reference, PR #31005), flex = ceny Batch API, odmowa „429 Resource Unavailable” bez opłaty, zalecany powrót do trybu standardowego (OpenAI flex-processing guide), domyślne ponowienia klienta openai-python (`max_retries=2`: 408/409/429/5xx/połączenie/timeout, `retry-after`), preferencje dostawcy OpenRouter (`order`/`only`/`allow_fallbacks`/`quantizations` jako lista dozwolona/`require_parameters`; punkty końcowe z sufiksem trybu wymagają jawnego wskazania). Niezweryfikowane: obsługa flex dla samego `gpt-6-luna` i honorowanie `service_tier` przez OpenRouter.

## Findings

### F1 — „Dokładnie jedno wywołanie” kontra ponowienia

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Faza 3 — kryteria 3.3 i 3.10, „Granica F-02”, Contract `service.py` i testów
- **Detail**: Kryterium 3.3 wymagało „dokładnie raz”, a nowy test „429, potem ok”; plan nie określał, gdzie żyją ponowienia (klient OpenAI ma własne `max_retries=2`), więc próby mogły się mnożyć, a test na poziomie portu nie trafia w ponowienia klienta.
- **Fix**: „Jedno wywołanie logiczne na post”; ponowienia wyłącznie w adapterze (klienci z `max_retries=0`, do 3 prób transportowych z backoffem i `retry-after`); `attempts` w pochodzeniu; test 429→ok na poziomie adaptera.
- **Decision**: FIXED (ponowienia tylko w adapterze — decyzja użytkownika)

### F2 — Brak zachowania, gdy flex długo odmawia

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: „Critical Implementation Details” → Flex i ponowienia; Faza 3 `openai.py`
- **Detail**: Flex przy braku mocy zwraca 429; kilka szybkich ponowień zwykle tego nie przeczeka, a OpenAI zaleca powrót do trybu standardowego. Plan nie określał zachowania.
- **Fix A ⭐ Recommended**: Powrót do trybu standardowego po N odmowach flex (ten sam model), oznaczenie w pochodzeniu i raporcie.
- **Fix B**: Bez powrotu; `provider_error` i ponowienie w kolejnym przebiegu (S-02).
- **Decision**: FIXED via Fix A — po 3 odmowach flex próba w trybie standardowym; `service_tier` i `flex_fallback` w pochodzeniu (S-02 zapisuje je w bazie przy rekordzie) i w raporcie z fazy 5 (decyzja użytkownika).

### F3 — Nieokreślona konfiguracja Luny w pomiarze

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 5 — `live_models.toml`, Wybór modelu
- **Detail**: Plan nie określa poziomu rozumowania ani kanału, z którym mierzy się Lunę (rozumowanie to ~700 z ~985 tokenów wyjścia w pilocie 8); wybór może zapaść na innej konfiguracji niż produkcyjna.
- **Fix**: Utrwalić pełną konfigurację (kanał, tryb obsługi, poziom rozumowania; domyślnie jak w pilocie 8) w `live_models.toml`, w każdym nagraniu i w pochodzeniu każdej oceny; `DEFAULT_ASSESSMENT_MODEL` wskazuje tę samą konfigurację; zmiana konfiguracji = nowy pomiar. Jakości nie zapisuje się per post — mierzy się ją na wzorcu.
- **Decision**: FIXED — konfiguracja (`channel`, `reasoning_effort`, tryb) w pochodzeniu każdej oceny, w nagraniach i w `live_models.toml`; zmiana konfiguracji = nowy pomiar (decyzja użytkownika)

### F4 — Doprecyzować preferencje dostawcy w OpenRouter

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 3 — `openai.py`
- **Detail**: Plan mówił „wykluczenie kwantyzacji fp4”, a OpenRouter nie ma flagi wykluczenia; `openai/flex` wymaga jawnego wskazania.
- **Fix**: Kontrakt `openai.py` z `extra_body.provider` (`only: ["openai/flex"]`, `allow_fallbacks: false`; dla DeepSeek `quantizations` jako lista dozwolona bez fp4 i `require_parameters: true`); bezpośrednio u OpenAI `service_tier="flex"`.
- **Decision**: FIXED (rekomendacja przyjęta przez użytkownika)

### F5 — Kryterium schematu nie obejmuje adaptera OpenAI

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 — kryterium 3.6
- **Detail**: Test schematu obejmował Gemini i DeepSeek, nie model pierwszego wyboru.
- **Fix**: Notatka w bloku fazy 3 (3.6 obejmuje OpenAI) i test schematu OpenAI w `test_providers.py`.
- **Decision**: FIXED (rekomendacja przyjęta przez użytkownika)
