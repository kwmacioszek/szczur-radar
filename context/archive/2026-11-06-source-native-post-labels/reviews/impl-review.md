<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Typ posta słowami jego źródła (S-37)

- **Plan**: context/changes/source-native-post-labels/plan.md
- **Scope**: Full plan (obie fazy, w tym Faza 2 z oczekującym wierszem Manual 2.7)
- **Reviewed phases**: 1, 2
- **Date**: 2026-11-06
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Kryteria Automated uruchomione ponownie: `npm test` 533/533, `npm run build` OK, grep `label('itemType'` bez trafień, `SpaRulesTest` exit 0. Wiersz Manual 2.7 (akceptacja słów tytułu zastępczego w PR) jest oczekujący, nieodhaczony, zgodnie z polityką.

## Findings

### F1 — Warunkowa asercja w it.each

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/components/PostCard.test.ts:95-103
- **Detail**: `if (source === 'reddit') expect([...]).not.toContain(text)` jest zbędne (poprzedza je dokładne `toBe(word)`), a warunek w teście ukrywa asercję przed czytelnikiem.
- **Fix**: Usunąć wiersz z `if`; `toBe(word)` już dowodzi, że to nie słowo Wykopu.
- **Decision**: FIXED (Fix now)

### F2 — Ryzyko S-37 w roadmapie nieaktualne

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/roadmap.md:257
- **Detail**: Linia Risk mówi, że zmiana „zmienia rejestr (`rules.json`) i obie strony”; faktycznie `rules.json` i `SpaRulesTest` się nie zmieniły, zmienił się tylko SPA i jego test rejestru.
- **Fix**: Przy zamknięciu slice'a poprawić linię Risk w roadmapie na stan faktyczny (foundation docs wolno edytować w miejscu).
- **Decision**: FIXED (Fix now)
