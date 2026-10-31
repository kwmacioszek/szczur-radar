<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-28 reddit-quality-and-cost — Obraz konfiguracji produkcyjnej na Reddicie przed włączeniem

- **Plan**: context/changes/reddit-quality-and-cost/plan.md
- **Scope**: Phase 4 of 4
- **Reviewed phases**: 4
- **Date**: 2026-10-31
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Wiersze 4.1–4.3 potwierdzone poleceniami, 4.4 przez `gh issue view`, 4.5 przez CI dla `856c4d3` (9 jobów zielonych, `gh pr checks`: 11 pass). Wszystkie liczby w `deploy-plan.md`, roadmapie i komentarzach zgadzają się z raportem. Merge `489d02d` zachował S-29 `done` i S-28 `in-progress`, bez utraty linii. Sekretów w diffie nie ma. Dodatki poza planem (wpis o porównaniu modeli w Parked, nowy `Risk` w S-28, zdanie o 12 stycznia) są nieszkodliwe i wynikają z decyzji właściciela.

## Findings

### F1 — Zostały odsyłacze do decyzji o języku przy S-28

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/foundation/prd.md:243, context/deployment/deploy-plan.md:2154
- **Detail**: PRD mówi, że decyzja o języku posta należy do S-28, a `deploy-plan.md` odsyła do S-28, choć nowa sekcja o języku nie wspomina. Faza 4 przeniosła `posts.language` do Parked i poprawiła tylko roadmapę.
- **Fix**: Skierować oba zdania na `## Parked` w roadmapie.
- **Decision**: FIXED

### F2 — Limit $10 bez nazwy w dokumencie operatora

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: context/deployment/deploy-plan.md:2138
- **Detail**: „The $10 monthly limit stays” nie mówi, o który limit chodzi. Pozostałe $10 w tym pliku to twardy limit przestrzeni Railway, a tu chodzi o klucz OpenRouter, którego wyczerpanie zatrzymuje przebieg.
- **Fix**: Napisać „The OpenRouter key's $10 monthly limit stays”.
- **Decision**: FIXED

### F3 — Zamknięcie #122 nie jest opisane

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:2132, komentarz issue #122
- **Detail**: Plan mówi, że #122 zamyka się po merge'u i weryfikacji produkcji, a sekcja S-28 nie ma takiego zdania. Komentarz w #122 mówi „otwarte do merge'a”, co jest sprzeczne z regułą repo.
- **Fix**: Dopisać zdanie do sekcji S-28 i poprawić komentarz w #122.
- **Decision**: FIXED

### F4 — Krok 4 runbooka nie mówi, co zrobić z porównaniem

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:2094
- **Detail**: Krok każe porównać `dead` z raportem, ale nie podaje kryterium. Obok 2,5 % Luny stoi bez etykiety 9 z 79 z modelu CI. Częste alarmy `RUN_NEEDS_ATTENTION` mogą też przykryć `source_failed` i `source_empty`, więc trzeba czytać ich powód.
- **Fix**: Dopisać etykietę modelu CI i zdanie: reagujemy dopiero powyżej 4,6 % (górna granica raportu) przez kilka nocy, a w alercie czytamy powód.
- **Decision**: FIXED

### F5 — Data 12 stycznia bez źródła

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: context/deployment/deploy-plan.md:2099, context/foundation/roadmap.md:266, context/changes/reddit-quality-and-cost/change.md:20
- **Detail**: Data jest podstawą decyzji i zgadza się z treścią issue #76 archiwum Arctic Shift, ale zdanie w `deploy-plan.md` tego nie cytuje, a roadmapa i `change.md` podają ją jako fakt bez źródła.
- **Fix**: Wskazać issue #76 jako źródło daty w `deploy-plan.md`.
- **Decision**: FIXED

### F6 — Nagłówek sekcji po angielsku

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/deployment/deploy-plan.md:2130
- **Detail**: Pozostałe sekcje wycinków mają polski tytuł z roadmapy. Ten nagłówek podyktował plan.
- **Fix**: Zostawić zgodnie z planem albo zmienić na tytuł S-28 z roadmapy.
- **Decision**: SKIPPED (nagłówek zgodny z planem)

### F7 — Wpis o `posts.language` zgubił szczegóły

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/foundation/roadmap.md:341
- **Detail**: Usunięta niewiadoma zawierała stanowisko właściciela z 2026-10-29 („ma być zapisany”) i dwa warianty: pole w wyjściu modelu albo detekcja w backendzie, z backfillem. Wpis w Parked tego nie niesie, a warunek powrotu zawęża „osobną potrzebę właściciela” z planu.
- **Fix**: Dopisać datę, oba warianty i warunek „gdy właściciel wróci do tej potrzeby”.
- **Decision**: FIXED
