<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-48: wspólne stany widoków i stronicowanie

- **Plan**: context/changes/shared-view-states/plan.md
- **Mode**: Deep
- **Date**: 2026-11-23
- **Verdict**: REVISE → SOUND po poprawkach
- **Findings**: 0 critical, 5 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

15/15 ścieżek ✓, 6/7 symboli (endpoint jakości ocen ✗, F4), brief↔plan ✓. Twierdzenia o atrapie (`MODE`, `QUALITY`, `MISSING`), `slug()`/`catalogSections`, `clipToArticle` w `view-feed` i flagach ładowania `AdminView` sprawdzone bezpośrednio w kodzie.

## Findings

### F1 — Ponowienie na liście: szkielet czy błąd w toku?

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2 §2, Critical Implementation Details
- **Detail**: Linie :109 i :281 mówiły, że ponowienie pokazuje szkielet. Linia :282, kontrakt `ErrorState` (`retrying`), Desired End State i testy mówiły, że błąd zostaje z przyciskiem w toku. Dziś `load()` zeruje `failed` na starcie (`FeedView.vue:60`).
- **Fix**: Usunięto „ponowienie” ze szkieletu. Błąd zostaje z `retrying` do odpowiedzi.
- **Decision**: FIXED

### F2 — Usunięcie klasy korzenia wyłącza reguły `.admin .message` itp.

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 2 §1
- **Detail**: Reguły `.admin .message` (`AdminView.vue:668`), `.run-detail .message, .attention, .refresh-error` (`RunDetailView.vue:329-331`) i `.post-detail .message` (`PostDetailView.vue:230`) wymagają klasy korzenia. Wzorzec `view-admin` nie ma komunikatów działań, więc kryterium 2.3 by tego nie złapało.
- **Fix**: Klasy korzeni zostają, znikają tylko reguły szerokości.
- **Decision**: FIXED

### F3 — `aria-busy` listy nie da się znaleźć bez selektora CSS

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 2 §4 i §5
- **Detail**: `div.posts` nie ma roli ani nazwy, a reguły E2E i F-06 zabraniają selektorów CSS i XPath. `getByRole` nie ma opcji `busy`.
- **Fix**: E2E i gotowość `view-feed-loading` sprawdzają tekst „Wczytywanie…” w `main`, a `aria-busy` kontenera listy sprawdza `FeedView.test.ts`.
- **Decision**: FIXED

### F4 — Zły endpoint i niedomknięty licznik żądań w teście sekcji

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 4 §1
- **Detail**: Plan podawał `/api/admin/quality`, a trasa to `/api/admin/assessment-quality`. Licznik „żadnego innego `/api/admin/`” liczyłby też odczyty dzwonka.
- **Fix**: Poprawiona ścieżka. Licznik pomija `/api/admin/notifications`.
- **Decision**: FIXED

### F5 — Fokus po „Odśwież” w panelu nieokreślony

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 3 §1, Critical Implementation Details
- **Detail**: Reguła fokusu po ponowieniu przerzuciłaby fokus z „Odśwież” do sekcji, która odpowie ostatnia.
- **Fix**: Fokus przenosi się tylko po ponowieniu z przycisku sekcji, a po „Odśwież” zostaje na miejscu. Dochodzi test w `AdminView.test.ts`.
- **Decision**: FIXED

### F6 — Zmiana strony, gdy na ekranie nie ma listy

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 §2
- **Detail**: Plan nie obejmował dwóch przypadków: wstecz po błędzie kolejnej strony i „Przejdź do strony M” z pustego stanu. W obu nie ma listy ani przycisku, który mógłby być w toku.
- **Fix**: Wtedy szkielet, a przewinięcie i fokus jak przy zmianie strony.
- **Decision**: FIXED

### F7 — Drobne luki w Fazie 4

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 4 §1, kryterium 4.4
- **Detail**: Filtr testu jednej strony nie był nazwany. Plik `evidence/e2e-breaks.md` nie był na żadnej liście „File”.
- **Fix**: Test jednej strony na `/?q=orlen` (20 postów), a `evidence/e2e-breaks.md` dopisany do plików §1.
- **Decision**: FIXED
