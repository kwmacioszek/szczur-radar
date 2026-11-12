<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Jakość ocen per źródło — nieudane próby według klas (S-35)

- **Plan**: context/changes/per-source-failure-quality/plan.md
- **Mode**: Deep
- **Date**: 2026-11-11
- **Verdict**: REVISE → SOUND po poprawkach
- **Findings**: 0 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
14/14 paths ✓ (V14 wolne, najwyższa V13), 6/6 symbols ✓ (HIGH_PRIORITY, alertRunId, NightSummary.of, ADMIN_ROUTES, failureClass w labels/rules.json, Source bez konstruktora), brief↔plan ✓, Progress↔Phase ✓ (19/19 wierszy)

## Findings

### F1 — Reguła przejścia liczy wczorajsze okno z danymi, których wczorajsza noc nie widziała

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 1 §4 `crossed(night)`; Desired End State
- **Detail**: `crossed` porównywał window(D) z window(D−1) liczonym teraz, a więc z całym dniem D−1, choć wczorajsza noc liczyła je o ~01:30. Przebiegi admina zapisują próby z modelem o każdej porze (`ConcurrentAssessmentDispatcher.java:312`). Mogą przez to (a) ukryć przejście na zawsze albo (b) dać drugi alarm w tym samym epizodzie.
- **Fix A ⭐ Recommended**: okno porównania `[start D−7, min(start D, now − 24 h))`
  - Strength: porównanie widzi to, co widziało wczorajsze podsumowanie; nadal bez zapisanego stanu.
  - Tradeoff: parametr `until` i jeden przypadek testowy więcej.
  - Confidence: MED — odchyłka to różnica czasu trwania nocnych przebiegów.
  - Blind spot: noc, której cron wystartował z dużym opóźnieniem.
- **Fix B**: przyjąć ryzyko i opisać je w Open Risks i w runbooku
  - Strength: zero kodu.
  - Tradeoff: przypadek (a) zostaje cichy.
  - Confidence: HIGH.
  - Blind spot: częstość przebiegów admina w dzień.
- **Decision**: FIXED (Fix A)

### F2 — Kształt odpowiedzi `/api/admin/assessment-quality` nie ma swojego rekordu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 §4 vs Phase 3 §1
- **Detail**: `SourceQuality.classes` było `Map`, a `QualityWindow` nie miało `windowDays`/`minAttempts`. Zwrócony wprost nie przeszedłby `ContractVariantsTest.leaf()` (:188-195) ani `record()` (:307-309).
- **Fix**: rekordy `QualityView` / `SourceQualityView` / `ClassCountView` zagnieżdżone w kontrolerze, `QualityView.of(QualityWindow)` wzorem `MissingDaysView.of`, wpis w `SHAPES`.
- **Decision**: FIXED

### F3 — `posts/AssessmentQuality` potrzebuje strefy Warszawy, która żyje w `pipeline/`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 1 §4
- **Detail**: jedyna strefa to `PipelineRunService.ZONE` (:97). Jej import w korzeniu `posts/` zamknąłby cykl pakietów.
- **Fix**: strefa jako parametr (`window(to, zone)`, `crossed(night, now, zone)`) wzorem `MissingDays.Coverage.of(range, ZONE)`.
- **Decision**: FIXED

### F4 — Miara nie widzi prób, które nie stały się wierszem

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: decyzja „Mianownik”; Phase 2 §4
- **Detail**: awaria dostawcy po ponowieniach (S-09), błędy klienta i naszego kodu, przerwany przebieg i posty pominięte po wyłączniku S-32 nie dają wiersza `assessments`, więc są poza miarą.
- **Fix**: zdanie w doprecyzowaniu PRD i w runbooku S-35.
- **Decision**: FIXED

### F5 — Drobne nieścisłości: tony StatusBadge i „przypięty” format w test.sh

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 §3; Key Discoveries; Phase 2 §3
- **Detail**: `StatusBadge` nie ma tonu „ok”. Stub `test.sh` formatu nie przypina.
- **Fix**: mapowanie `ok → success`, `above_threshold → warning`, `not_enough_attempts → neutral`; poprawione zdania o test.sh i README.
- **Decision**: FIXED
