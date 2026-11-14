<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Testy mutacyjne rdzenia (S-45)

- **Plan**: context/changes/core-mutation-testing/plan.md
- **Mode**: Deep
- **Date**: 2026-11-13
- **Verdict**: REVISE (po poprawkach: SOUND)
- **Findings**: 2 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | FAIL |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | FAIL |

## Grounding
26/26 ścieżek ✓, 6/6 odwołań do linii SQL ✓, symbole ✓, brief↔plan ✓, Progress↔fazy ✓. Semantyka PIT sprawdzona w źródłach pitest 1.30.2 i pitest-junit5-plugin 1.2.3. `@Nested` (`ContractV2RulesTest`) nie jest problemem: plugin uruchamia klasy zagnieżdżone razem z klasą zewnętrzną (`JUnit5TestUnitFinder.java:65,89`).

## Findings

### F1 — `excludedClasses *.api.*` wyklucza cały kod

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 — profil `mutation`, kryterium 1.3
- **Detail**: W globie PIT `*` to `.*` i pasuje też do kropek (`Glob.java:66`). `*.api.*` pasuje więc do pakietu bazowego `pl.szczurradar.api` i wyklucza każdą klasę.
- **Fix**: `pl.szczurradar.api.*.api.*` i dokładny grep po `mutatedClass` w kryterium 1.3.
- **Decision**: FIXED

### F2 — `RunRecorder` i `PostTransitions` w całości `NO_COVERAGE`, a żadna kategoria powodu do nich nie pasuje

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: End-State Alignment
- **Location**: Fazy 1, 2 i 4
- **Detail**: Żaden `*Test` nie tworzy tych klas, wszędzie są mockami. Ich mutanty, w tym logika `RunRecorder.step`, byłyby `NO_COVERAGE`. Nie są ani `obronny:`, ani `równoważny:`, tylko pokrywa je IT.
- **Fix A ⭐ Recommended**: Wykluczyć `RunRecorder*` i `PostTransitions` (bez `*`, więc `Outcome` zostaje w pomiarze). Logikę Javy w `RunRecorder.step` i `PostTransitions.error` objąć wierszami `sql-breaks.md` w Fazie 5.
- **Fix B**: Kategoria `IT: Klasa#metoda` w rejestrze.
- **Decision**: FIXED (Fix A). Dodatkowo: klasa w zakresie, którą pokrywa wyłącznie IT, oznacza stop i pytanie do właściciela.

### F3 — Blokujący job a niedeterministyczne statusy mutantów

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Performance Considerations, Fazy 1, 4 i 6
- **Detail**: Strażnik jest ostry w obie strony, a testy dyspozytora są wielowątkowe i mają timeouty. Mutant raz `SURVIVED`, raz `TIMED_OUT` czerwieniłby losowo PR-y i blokował deploy.
- **Fix**: Dwa pomiary bazowe z porównaniem statusów (kryterium 1.6). Drugi zielony przebieg joba `mutation` w CI (kryterium 6.5).
- **Decision**: FIXED

### F4 — Równoważny mutant w lambdzie nie ma wyjścia

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Critical Implementation Details, Faza 2
- **Detail**: Plan zakazywał wpisów dla lambd i zmian w `src/main`, więc mutant równoważny w lambdzie zostawiłby strażnika czerwonego na zawsze.
- **Fix**: Klucz normalizuje `lambda$name$N` do `lambda$name`. Dochodzi przypadek w `MutantRegistryTest`.
- **Decision**: FIXED

### F5 — Klucz bez `methodDescription` skleja przeciążenia

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Critical Implementation Details, Faza 2
- **Detail**: `mutations.xml` ma `methodDescription` (`XMLReportListener.java:93`), a rdzeń ma przeciążenia.
- **Fix**: `methodDescription` w kluczu i przypadek „inne przeciążenie → brak dopasowania”.
- **Decision**: FIXED

### F6 — Kryterium 1.4 sprawdza logi, których nie ma

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1, kryterium 1.4
- **Detail**: PIT nie zapisuje logów w `target/`.
- **Fix**: Przebieg z `fullMutationMatrix=true` i grep po macierzy testów w `mutations.xml`.
- **Decision**: FIXED
