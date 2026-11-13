<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-31 — ręczne weryfikacje PR env jako scenariusze w CI

- **Plan**: context/changes/pr-env-scenarios-in-ci/plan.md
- **Mode**: Deep
- **Date**: 2026-11-12
- **Verdict**: SOUND
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS (1 observation) |
| Blind Spots | WARNING |
| Plan Completeness | PASS (2 observations) |

## Grounding

17/17 paths ✓, 9/9 symbols ✓, brief↔plan ✓, Progress↔Phase ✓. Deep check of 7 risky claims, 0 contradicted:
- the model's input is deterministic, and all 33 posts can be rebuilt from the fixtures with public classes;
- 13/14/6 posts go to the model;
- a range is fetched with one series of requests, not one per day;
- `OwnRuns` is public;
- all four deliberate breakages turn their scenarios red reliably.

## Findings

### F1 — Dostarczenie do ntfy jest asynchroniczne, a „cisza” go nie obejmuje

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Faza 2 #2 (LocalNtfy), #4 („Po scenariuszu”); Faza 3 C, E, F, H
- **Detail**: Linia `taken` powstaje na wątku wołającego w `raise` (`OperatorAlertQueue.java:192`), czyli przed `own.remove`. Żądanie do ntfy i linia `reported:` (`:253`) idą później, na wątku `operator-alerts`, a kolejka nie pokazuje, czy jest bezczynna. Skutki:
  - spóźniona wiadomość z poprzedniego scenariusza może zepsuć „dokładnie jedną” w C albo E;
  - `reported: DELIVERED` w E to wyścig;
  - H sprawdzał brak alertu tylko w ntfy.
- **Fix**: Cisza czeka, aż liczba zakończonych zgłoszeń (dekorator `OperatorAlerts` w `LocalNtfy`) zrówna się z liczbą linii `taken`. Asercje na ntfy filtrują po tytule i id przebiegu. `reported: DELIVERED` sprawdza się czekaniem na log. „Brak alertu” sprawdza się na linii `taken` i w ntfy po ciszy.
  - Strength: opiera się na jedynym pewnym sygnale, bez zmian w src/main.
  - Tradeoff: cisza zależy od przechwytywania wyjścia per metoda.
  - Confidence: HIGH — wątki i kolejność potwierdzone w kodzie.
  - Blind spot: granice przechwytywania `OutputCaptureExtension` dla logów z cudzego wątku; dlatego filtr po id jest głównym zabezpieczeniem.
- **Decision**: FIXED (Fix in plan) — Critical Implementation Details, Faza 2 #2 i #4, scenariusze C, E, F, H.

### F2 — Narzędzie nagrywające zapisze odpowiedź 200, z której klient nie wyjmie tekstu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 1 #2; Critical Implementation Details
- **Detail**: Rejestrator zapisałby 200 z odmową, `content_filter`, bez tekstu albo bez `choices`. Klient zamienia taką odpowiedź w błąd wywołania (`FailureClass.fromCall`), a ten:
  - liczy się do wyłącznika S-32 (`REFUSAL_STREAK` = 3, w kolejności końca czterech równoległych wywołań), więc przebieg stanąłby albo nie, zależnie od kolejności;
  - ma własny limit resztek, inny niż ścieżka „failed → RETRY → dead”.
- **Fix**: Zapis tylko ciała, z którego produkcyjny klient zwraca tekst modelu (odrzucenie przez walidator jest OK). Inne ciało jest ponawiane kilka razy; przy powtarzającej się odmowie narzędzie kończy pracę z listą postów dla właściciela. Reguła trafia do README nagrań.
- **Decision**: FIXED (Fix in plan) — Faza 1 #2 i #3, Critical Implementation Details.

### F3 — Druga atrapa „odpowiedź z funkcji żądania”

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 1 #4
- **Detail**: `test/sources/LocalApiServer.java` jest publiczny, już odpowiada przez `Function<Request, Reply>` i zapisuje żądania. Plan dokładał ten sam tryb do `ScriptedProviderServer` (FIFO, 4 użytkowników).
- **Fix**: Serwerem nagrań jest `LocalApiServer` (Javadoc: „an external API”), a `ScriptedProviderServer` zostaje bez zmian.
- **Decision**: FIXED (Fix in plan) — Faza 1 #4 i #8.

### F4 — Fakty do poprawienia dla implementującego

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Key Discoveries; Faza 2 #2, #4; Scenariusz A
- **Detail**: Do poprawienia:
  - kolejność na końcu przebiegu to zamknięcie kroku, potem status (`RunSequence.java:191-193`);
  - pełna linia r/stocks kończy się `, 0 of unexpected shape`;
  - nadpisywanie definicji beanów jest wyłączone, więc beany `@Primary` muszą mieć inne nazwy, a listę punktów wstrzyknięcia trzeba uzupełnić o `ConcurrentAssessmentDispatcher`;
  - `RUN_NEEDS_ATTENTION` wymaga powiadomienia admina, więc admin musi mieć `login_enabled`, a tabela `users` zostaje poza `TRUNCATE`.
- **Fix**: Poprawki w Key Discoveries, scenariuszu A i kontrakcie Fazy 2 #2 i #4; otwarte ryzyko o beanach w briefie zamknięte.
- **Decision**: FIXED (Fix in plan) — plan.md i plan-brief.md.

### F5 — Kryterium „job ≤ 285 s” jest w szumie runnera

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 4, kryterium 4.6
- **Detail**: Rozrzut 6 zielonych runów (190–224 s) zjada ponad połowę budżetu 60 s. Czas klasy mierzy to, co znaczy budżet.
- **Fix**: Bramką jest czas klasy (≤ 60 s); czas joba przed i po trafia do `deploy-plan.md` jako pomiar.
- **Decision**: FIXED (Fix in plan) — kryterium 4.6.
