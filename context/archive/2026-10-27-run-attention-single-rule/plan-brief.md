# S-22: „wymaga uwagi” liczone jedną regułą — Plan Brief

> Full plan: `context/changes/run-attention-single-rule/plan.md`
> Research: `context/changes/post-m2-audit/research.md` (K1) i `context/domain/domain-distillation.md` (R-22, D-04, Q-05) — zamiast osobnego `research.md`

## What & Why

Reguła „wymaga uwagi” żyje w dwóch miejscach: API decyduje o fladze, powiadomieniach i alercie, a SPA osobno liczy z liczników treść powodów na stronie przebiegu. Gdy API doda powód, SPA pokaże „Wymaga uwagi: ” z pustym tekstem. Do tego przebieg przerwany przez wdrożenie ma w SPA ton neutralny obok czerwonego badge'a „wymaga uwagi”. Ta zmiana zostawia jedną regułę w API.

## Starting Point

`PipelineRun.needsAttention(status, dead, processingErrors, skipped)` daje wartość logiczną dla powiadomień, alertu i `RunView.needsAttention`. `attentionReasons` w `szczur-radar-web/src/runs.ts` powtarza tę tabelę, żeby zbudować teksty, a `runTone`/`stepTone` dają `neutral` dla `interrupted/deploy`. Testy obu stron nie mają wspólnego przypadku.

## Desired End State

API liczy listę kodów (`failed`, `interrupted`, `dead_posts`, `processing_errors`, `skipped`), a `needsAttention` jest tylko tym, że lista nie jest pusta. Odpowiedź przebiegu ma `attentionReasons`. SPA zamienia kody na polski tekst i nic nie liczy. Nieznany kod pokazuje jak jest, a flaga bez tekstów daje tekst zastępczy, więc „Wymaga uwagi:” nigdy nie jest puste. Każde przerwanie, także przez wdrożenie, ma ton ostrzeżenia. Powiadomienia i alert działają jak dziś.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Skąd SPA bierze powody (Q-05) | Kody `AttentionReason` z API; SPA tylko tłumaczy | Jedna reguła w Javie, a nowy powód nigdy nie daje pustego tekstu; S-24 nie musi pilnować tej tabeli | Plan (wywiad) |
| Ton przerwania przez wdrożenie | SPA → `warning` (`runTone` i `stepTone`) | Zgodne z regułą API, PRD (S-06: `interrupted` → powiadomienie) i zasadą „nie merguj, gdy przebieg trwa”; zero zmian w sygnałach | Plan (wywiad) |
| Gdzie widać treść powodów | Jak dziś: tylko strona przebiegu; `/admin` i dzwonek z tej samej reguły przez flagę i ton | Spełnia outcome S-22 bez pracy UI i bez zmiany `NotifiedRun` | Plan (wywiad) |
| Alert `RUN_NEEDS_ATTENTION` | Bez zmian | Nie rusza formatu zweryfikowanego na produkcji w S-18 | Plan (wywiad) |
| Tekst powodów | W SPA (`src/runs.ts`, odmiana liczebników), nie w API | Konwencja: API wysyła wartości, SPA je etykietuje (`labels.ts`) | Plan |
| Nieznany kod / brak pola | Kod jak jest; flaga bez tekstów → „sprawdź kroki i błąd przebiegu” | Wzorzec `labels.ts`; okno wdrożenia (nowy web przy starym API) | Plan |
| `needsAttention` w odpowiedzi | Zostaje, wynika z kodów | Używają go `/admin`, `RunNotifications` i stary SPA w oknie wdrożenia | Plan |

## Scope

**In scope:**
- enum `AttentionReason` i `PipelineRun.attentionReasons()`, z którego wynika `needsAttention()`;
- `RunView.attentionReasons`;
- `src/runs.ts`: tłumaczenie kodów, ton `warning` dla każdego przerwania, `stepTone` bez przyczyny;
- testy obu stron, `mock-api.js`, kitchen sink;
- `pipeline/CLAUDE.md` i `REPOSITORY_GUIDELINES.md` (wytyczna UI).

**Out of scope:**
- zmiana, które przebiegi wymagają uwagi;
- treść alertu w Telegramie;
- powody w `/admin` i w dzwonku (`NotifiedRun`);
- gotowe zdania z API;
- wspólny plik przypadków i pełny kontrakt odpowiedzi (S-24);
- `/10x-ui`;
- sekcja w `deploy-plan.md`;
- edycja `context/domain/` i audytu.

## Architecture / Approach

`PipelineRun.attentionReasons()` jest jedyną regułą. `needsAttention()` z niej wynika, a `RunNotifications` (powiadomienia i alert) dalej woła `needsAttention()`. `RunView` wysyła kody, `src/runs.ts` zamienia je na tekst i ustala ton, a `RunDetailView` wyświetla wynik. Faza API jest zgodna wstecz, a faza SPA znosi brak pola, więc kolejność wdrożenia web i API niczego nie psuje.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. API — powody jako kody z jednej reguły | `AttentionReason`, `attentionReasons()` → `needsAttention()`, `RunView.attentionReasons`, testy, `pipeline/CLAUDE.md` | Zmiana kolejności lub zakresu kodów względem dzisiejszej tabeli (pilnuje `PipelineRunTest` z dokładnymi listami) |
| 2. SPA — tekst z kodów API, ton `warning` dla wdrożenia | Tłumaczenie kodów z dwoma zabezpieczeniami, nowy ton, widoki, Vitest, atrapa, kitchen sink, wytyczne, zrzuty, PR env | Przeoczony test lub widok przypinający `neutral` dla wdrożenia (pilnuje `npm test` i `rg`) |

**Prerequisites:** brak. Gałąź `feature/run-attention-single-rule` z świeżego `origin/main`, issue S-22, Docker dla `./mvnw -B verify`.
**Estimated effort:** ~1 sesja, 2 fazy.

## Open Risks & Assumptions

- Zakładamy, że ton `neutral` dla wdrożenia był tylko wygodą wizualną z S-06, a nie potrzebą operatora. Przebiegi przerwane wdrożeniem będą od teraz wyglądać jak ostrzeżenie (decyzja wywiadu).
- `attentionReasons` w typie SPA zostaje opcjonalne także po wdrożeniu. To świadomy, mały koszt za odporność na okno wdrożenia.
- Nowy kod po stronie API bez tekstu w SPA pokaże surowy kod (np. `stale_source`). Jest czytelny, ale nie po polsku, dopóki ktoś nie doda tekstu w `src/runs.ts`.

## Success Criteria (Summary)

- Strona przebiegu zawsze podaje niepusty powód „wymaga uwagi”, wzięty z kodów, które liczy API.
- Przebieg przerwany przez wdrożenie ma w `/admin`, na stronie przebiegu i w dzwonku ton ostrzeżenia, zgodny z badge'em „wymaga uwagi”.
- Powiadomienia i alert `RUN_NEEDS_ATTENTION` działają dokładnie jak przed zmianą (`./mvnw -B verify` zielone).
