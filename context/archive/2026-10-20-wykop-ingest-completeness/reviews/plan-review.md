<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-16: Wykop nie gubi postów po cichu

- **Plan**: context/changes/wykop-ingest-completeness/plan.md
- **Mode**: Deep
- **Date**: 2026-10-20
- **Verdict**: REVISE → SOUND po triage (wszystkie 7 ustaleń FIXED w planie)
- **Findings**: 0 critical, 2 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
8/8 ścieżek ✓, 4/4 symboli ✓ (MIN_DEFAULT, PipelineStallSettingsTest, pipeline/CLAUDE.md:72-74, „the only retries”), brief↔plan ✓, Progress↔Fazy ✓

## Findings

### F1 — Gwarancja „≤ 10 min + jedno żądanie” nie jest twarda

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details; Faza 2, punkt 1
- **Detail**: W JDK 25 `HttpRequest.timeout` obejmuje tylko czas do nagłówków odpowiedzi (`MultiExchange.java:420-422,474`), a czytanie ciała (`BodyHandlers.ofString`) nie ma limitu. Wewnętrzne ponowienie JDK przy `ConnectException` dostaje nowy pełny timer, więc do nagłówków może minąć 2 × timeout. Pobranie mieści się więc w terminie + ~2 × 30 s, ale tylko gdy ciało przychodzi szybko. Zapas wynosi 5 min, a twierdzenie z planu jest nieprawdziwe.
- **Fix A ⭐ Recommended**: Poprawić twierdzenie, mechanizm zostaje (granica = termin + ~2 × timeout na wymianę, ciało zakładane jako szybkie; test relacji z zapasem 2 × `DEFAULT_TIMEOUT`)
  - Strength: Zero nowego mechanizmu; zapas 5 min pokrywa realny przypadek.
  - Tradeoff: Zawieszone ciało nadal kończy się komunikatem watchdoga.
  - Confidence: HIGH — semantyka JDK sprawdzona w źródłach.
  - Blind spot: Nie wiadomo, czy Wykop kiedykolwiek zawiesza ciało.
- **Fix B**: Twarda granica: `http.sendAsync(...).get(min(timeout, deadline − now))` + `cancel(true)` → `TRANSPORT`
  - Strength: Prawdziwa gwarancja, koniec z zawieszonym ciałem.
  - Tradeoff: Więcej kodu i testów (anulowanie, rozpakowanie wyjątków, przerwanie).
  - Confidence: MED — brak wzorca `sendAsync` w repo.
  - Blind spot: Zachowanie `cancel` na HTTP/2 nie zostało sprawdzone.
- **Decision**: FIXED (Fix A)

### F2 — Definicja `seen` i `total` niedookreślona

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3, punkt 1
- **Detail**: Plan nie mówi, z którym `total` porównuje (strona 1 czy ostatnia). Element bez `id` ma klucz `no-id:<elements.size()>` z mapy wspólnej dla obu zasobów (`WykopRepository.java:157-158`) i nigdy nie jest deduplikowany. Taki element, podobnie jak element o złym kształcie, powtórzony na przesuniętej stronie liczy się dwa razy, co może ukryć lukę.
- **Fix**: Doprecyzować w Fazie 3: porównanie z `total` strony 1 (zmianę łapie warunek (c)); `seen` = unikalne `resource:id` + elementy bez `id` lub o złym kształcie liczone przy każdym wystąpieniu (świadome zawyżenie); klucz `no-id` z mapy przejścia.
- **Decision**: FIXED

### F3 — Test relacji z watchdogiem: niezdecydowane miejsce

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2, punkt 6
- **Detail**: „PipelineStallSettingsTest (lub istniejący test)”. Test w `pipeline/` wymusza publiczne `FETCH_DEADLINE`, a w `sources/wykop` stała zostaje pakietowa (`MIN_DEFAULT` jest publiczne). Ograniczenie należy do źródła.
- **Fix**: Test w `WykopRepositoryTest`, czytający `PipelineStallSettings.MIN_DEFAULT`.
- **Decision**: FIXED

### F4 — Pominięte miejsca wywołań przy zmianie klienta

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2, punkty 4 i 6
- **Detail**: Klienta budują też `RunWatchdogIT:380`, `InternalPipelineRunIT:377` i `PipelineRunControllerIT:402` (zawsze 200). W `WykopApiClientTest` zderzą się z ponowieniami `aConnectionErrorIsATransportFailure` (`:209`) i `aLongErrorBodyIsCappedAfterMasking` (`:115`, 500).
- **Fix**: Wymienić te pliki i testy w Fazie 2 (`Sleeper` bez czekania w testach z 500 i transportem; konstruktor 4-argumentowy zostaje, więc IT bez zmian).
- **Decision**: FIXED

### F5 — Asercja logu WARN bez wzorca

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2, punkt 4 (maskowanie)
- **Detail**: Plan pisze „jeśli test przechwytuje log”, a w repo jest gotowy wzorzec `OutputCaptureExtension` (`PipelineStallSettingsTest.java:26,57`).
- **Fix**: Wymagać `@ExtendWith(OutputCaptureExtension.class)` i `contains("WARN").doesNotContain(klucz, sekret, token)`.
- **Decision**: FIXED

### F6 — Druga pętla ponowień: plan na trzecie źródło

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: What We're NOT Doing (wspólna klasa ponowień)
- **Detail**: Lekcja `lessons.md:61` każe ocenić, czy kod wytrzyma roadmapę. Odrzucenie wspólnej klasy z `llm/` jest słuszne, ale Reddit napisze trzecią pętlę.
- **Fix**: W `sources/CLAUDE.md` („Adding a source”): ponowienia według polityki `WykopApiClient`, a przy drugim źródle wydzielić wspólny helper w `sources/`.
- **Decision**: FIXED

### F7 — Weryfikacja w PR env: skąd `total`

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 4, punkt 2
- **Detail**: „`fetched` zgodne z `total` wyszukiwarki”, ale żaden log nie podaje `total`.
- **Fix**: Porównać z sondą z Fazy 1 na te same dni albo z `fetched` przebiegu produkcji za ten sam zakres w `/admin`.
- **Decision**: FIXED
