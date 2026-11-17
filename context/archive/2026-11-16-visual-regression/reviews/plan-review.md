<!-- PLAN-REVIEW-REPORT -->
# Plan Review: F-06: zrzuty porównawcze i test dostępności w CI

- **Plan**: context/changes/visual-regression/plan.md
- **Mode**: Deep
- **Date**: 2026-11-16
- **Verdict**: REVISE → SOUND (po poprawkach)
- **Findings**: 2 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | FAIL → PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | FAIL → PASS |
| Plan Completeness | WARNING → PASS |

## Grounding
10/10 paths ✓, 7/7 symbols ✓ (FEED_PAGE_SIZE=50, ACTIVATION 'valid', przebieg 3 `partial`, `meta.public` katalogu, trasa atrapy na każdym originie, „do F-06” w verification-scope, regiony `section aria-labelledby`), brief↔plan ✓, Progress↔Fazy ✓

## Findings

### F1 — Katalog ma 14 regionów, nie 10

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 — #1 Sekcje katalogu, #3 Strażnik wzorców
- **Detail**: 4 otwarte panele `NotificationMenu` mają `role="region"` z nazwą „Powiadomienia” (`NotificationMenu.vue:133-139`). `getByRole('main').getByRole('region')` zwraca więc 14 regionów, a 4 z nich dają ten sam slug, czyli czerwony test według reguły planu. Liczby 20/54 i strażnik wzorców opierały się na tym samym wyliczeniu.
- **Fix**: sekcje wyliczane po nagłówkach `h2` w `main`, region po nazwie (`getByRole('region', { name, exact: true })`), wspólnie dla testu sekcji i strażnika; wpis w Current State Analysis.
- **Decision**: FIXED (Fix in plan; także brief)

### F2 — Artefakt `visual-baselines` nigdy nie powstanie

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: End-State Alignment
- **Location**: Faza 1 — #10 Krok w CI
- **Detail**: `if: steps.visual.outcome == 'failure'` bez funkcji statusu dostaje domyślne `success()`. Po czerwonym kroku zrzutów job jest już `failure`, więc krok produkujący wzorce i upload byłyby zawsze pominięte, a zapas z artefaktu (i kryterium 3.2) by nie działał.
- **Fix**: `if: ${{ !cancelled() && steps.visual.outcome == 'failure' }}` w obu krokach, a krok produkujący wzorce z `continue-on-error: true`.
- **Decision**: FIXED (Fix in plan)

### F3 — Stan hover nie ma celowego złamania

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 — #2 Stany; Faza 3 — #1 tabela złamań
- **Detail**: Złamania sprawdzały tylko pierścień focus. Nic nie dowodziło, że wzorce `state-*-hover` różnią się od stanu domyślnego. Przewinięcie przy zrzucie regionu mogłoby zgasić hover, a wzorzec zapisałby się jako kopia stanu domyślnego.
- **Fix**: 7. wiersz tabeli złamań (usunięte reguły hover w `style.css:136-138` i `AppButton.vue:37-39`), asercja `:hover` przed zrzutem i po nim, 7 złamań w 3.1 i w briefie.
- **Decision**: FIXED (Fix in plan)

### F4 — Dwa przyciski „Domyślny” nie mają nazwy wiersza

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2 — #2 tabela STATES
- **Detail**: Wiersze przycisków to `<div class="row">` bez roli i nazwy (`KitchenSinkView.vue:605-609`), więc lokator po roli i nazwie „Domyślny” łamie strict mode; zmian w `src/` plan zabrania.
- **Fix**: lokatory w tabeli STATES: `getByRole('button', { name: 'Domyślny', exact: true }).nth(0|1)` w kolejności `variants`, `getByRole('searchbox', { name: 'Puste', exact: true })`.
- **Decision**: FIXED (Fix in plan)

### F5 — Klucz statusu w SVG to `progress`, nie `in-progress`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 — #2 Roadmapa i jej SVG
- **Detail**: `STATUS` w `tools/render_roadmap_svg.py:29-36` nie zna `in-progress`, więc „ten sam status” dałby `KeyError`.
- **Fix**: dane SVG `"progress"`, `roadmap.md` `in-progress`.
- **Decision**: FIXED (Fix in plan)

### F6 — Sam reporter HTML w CI nie wypisze „25 passed”

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 — #4 Uruchamianie; kryteria 1.10, 2.6
- **Detail**: Kryteria czytają podsumowanie z logu, a sam reporter HTML go nie drukuje.
- **Fix**: przy CI `[['list'], ['html', { outputFolder: 'playwright-report-visual', open: 'never' }]]`.
- **Decision**: FIXED (Fix in plan)

### F7 — Złamania: strażnik fontu i sprzątanie po wzorcach

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 3 — #1 tabela, kryterium 3.1
- **Detail**: Złamanie etykiety „Hasło” zaczerwieniłoby też strażnika fontu (czyta ten węzeł). `git diff --quiet` nie widzi nieśledzonego `orphan.png` ani przywrócenia usuniętego wzorca.
- **Fix**: złamanie etykiety „E-mail” (`LoginView.vue:61`), sprawdzenie `test -z "$(git status --porcelain szczur-radar-web)"` w metodzie i w 3.1.
- **Decision**: FIXED (Fix in plan)
