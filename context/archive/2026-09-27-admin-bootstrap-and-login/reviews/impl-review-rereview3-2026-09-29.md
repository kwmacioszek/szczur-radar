<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Pierwszy admin przez CLI, logowanie/wylogowanie i ochrona tras — trzeci przegląd

- **Plan**: context/changes/admin-bootstrap-and-login/plan.md
- **Scope**: Full plan (completed phases only); diff `197b740..8ecedd8`
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-09-29
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 0 observations
- **Reviewed HEAD**: `8ecedd809ec72e8cfb0061cd74befe9bd71c94a3`
- **Merge readiness**: NOT READY — brak produkcyjnego `JWT_SECRET`; przed merge pozostają kontrole fazy 4 opisane poniżej.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

Brak nowych ustaleń w ukończonych fazach 1–3. Nie ma ustaleń wymagających triage. `APPROVED` dotyczy implementacji tych faz; nie oznacza zakończenia oczekującej fazy wdrożenia ani zgody na merge przy brakującym sekrecie.

## Wcześniejsze ustalenia

- Pierwszy przegląd F1/F2: limiter rezerwuje próbę przed pierwszym `await`, rozlicza ją w `finally` i usuwa wygasłe klucze. Testy współbieżności, anulowania i czyszczenia przechodzą.
- Pierwszy przegląd F3: renderer roadmapy pokazuje S-01 `w toku`, a PNG został zaktualizowany w poprzedniej poprawce.
- Drugi przegląd F1: API i CLI współdzielą `MAX_EMAIL_LENGTH = 320`; test odrzuca za długi adres przed promptem i zapisem.
- Drugi przegląd F2: transakcyjna blokada `pg_advisory_xact_lock` obejmuje sprawdzenie i zapis pierwszego admina. Test dwóch równoległych sesji potwierdza pojedynczy zapis.
- Drugi przegląd F3/F4: wcześniejsze decyzje `ACCEPTED` i `SKIPPED` dotyczące pobranych narzędzi kursowych pozostają obowiązujące. Nie otwarto ich ponownie.

## Zakres i zgodność

Dwa niezależne przeglądy objęły zgodność z planem oraz bezpieczeństwo, niezawodność i istotne wzorce. Model i migracja, leniwe sesje DB, CLI, JWT/cookie, autoryzacja tras, limiter, Origin, klient API, stan sesji, router i widoki SPA odpowiadają kontraktowi faz 1–3. Dodatkowe helpery i testy wspierają zaplanowaną funkcję. `JWT_SECRET: preserve()` jest uzasadnioną i udokumentowaną korektą pierwotnego zakazu zmian `env`: zachowuje ręcznie dostarczony sekret, nie generuje go.

Progress: fazy 1–3 mają 21/21 punktów `[x]`, faza 4 ma 0/7. Łącznie 21/28; bieżąca faza 4. Progress pozostawiono bez zmian zgodnie z kontraktem skilla. Odhaczone kontrole ręczne faz 1–3 mają zapis w commitach Progress i wcześniejszych raportach; nie powtarzano sesji przeglądarkowej. Nie traktowano ich jako świeżej weryfikacji wdrożeniowej fazy 4.

Nieśledzony `AGENTS.user.md` nie należy do przeglądanego diffu i pozostał nietknięty.

## Weryfikacja automatyczna

Polecenia backendu uruchomiono w `szczur-radar-api/`, z `UV_CACHE_DIR=/private/tmp/szczur-radar-uv-review`; frontend z Node 24.21.0.

| Kontrola | Wynik |
|----------|-------|
| `uv sync --locked` | PASS — `Resolved 43 packages; Checked 40 packages` |
| `TEST_DATABASE_URL=postgresql://szczur_local:local_only@127.0.0.1:5432/szczur_radar_test REQUIRE_DB_TESTS=1 uv run pytest -q --tb=short` | PASS — `69 passed, 1 warning in 6.19s`; obejmuje migrację upgrade/downgrade/upgrade, authz, login, Origin, JWT, limiter i CLI |
| `env -u DATABASE_URL uv run pytest -q tests/test_health.py` | PASS — `3 passed, 1 warning in 0.05s` |
| `npm test` | PASS — 3 pliki, 29 testów |
| `npm run build` | PASS — `vue-tsc -b` oraz Vite; 33 moduły |
| `git diff --check 197b740..HEAD -- . ':!.agents' ':!.claude'` | PASS — kod, konfiguracja i dokumentacja poza pobranymi plikami kursowymi |
| CI PR #13 dla `8ecedd8` | PASS — backend, web-unit, web-container-smoke, railway-config; [run 36577441992](https://github.com/kwmacioszek/szczur-radar/actions/runs/36577441992) |

Ostrzeżenie backendu jest istniejącą deprecacją `httpx` w Starlette TestClient; testy nie były pominięte. W przeciwieństwie do poprzedniego przeglądu zielone CI obejmuje już obie serie poprawek. GitHub zwraca `mergeable: MERGEABLE`, `mergeStateStatus: CLEAN`, a HEAD PR jest zgodny z lokalnym HEAD.

Odczytowy `railway config plan --json`, ze wskazanym projektem i środowiskiem przez `RAILWAY_PROJECT_ID` / `RAILWAY_ENVIRONMENT_ID`:

- PR `szczur-radar-pr-13`, z `IAC_SOURCE_BRANCH=feature/admin-bootstrap-and-login`: `ok: true`, `changeSet.changes: []`, brak diagnostyki.
- `production`: dokładnie jedna zmiana `safe`: API `deploy.preDeployCommand` z `null` na `["uv run alembic upgrade head"]`. Brak usunięć zasobów, zmiennych lub zmiany source. Nie wykonywano apply.
- Historyczny warunek fazy 1 „zero zmian na produkcji przed dodaniem preDeploy” jest udokumentowany w poprzednich raportach i runbooku. Obecny kod celowo zawiera preDeploy, więc ponowne oczekiwanie kodu 0 dla tego historycznego warunku byłoby błędne.

## Gotowość do merge — oczekująca faza 4

**Nie mergować jeszcze do main.** To konkretne warunki wdrożenia, nie nowe defekty ukończonej implementacji.

1. **Potwierdzony brak `JWT_SECRET` na produkcji (plan 4.2).** Odczyt metadanych `Environment.variables` zwrócił dla API na `production` wyłącznie `PORT` i `DATABASE_URL`; `hasNextPage: false`. PR env ma `JWT_SECRET`. Zapytanie pobierało tylko `name`, `isSealed`, `serviceId`, bez wartości. Produkcyjny `currentGraph` IaC także nie zawiera JWT_SECRET. `preserve()` nie dostarcza brakującej wartości. `main.py:27` wywołuje `jwt_secret()` przy starcie, a `app/auth/tokens.py:35` rzuca `RuntimeError`, gdy sekret nie istnieje lub jest za krótki. Przed merge należy dostarczyć losowy sekret do produkcyjnego API, zapieczętować go i zapisać wykonanie w runbooku bez wartości sekretu.
2. **Dokończyć ręczną weryfikację PR env (plan 4.3–4.4).** Statusy wdrożeń API i web dla `8ecedd8` to SUCCESS, ale nie zastępują testu logowania/wylogowania, ochrony tras, 403 dla obcego Origin, 429 po limicie oraz potwierdzenia rzeczywistego adresu klienta i nadpisania podszytego `X-Real-IP`. Te punkty pozostają nieodhaczone i nie zostały wykonane w tym przeglądzie.

Dowody wdrożenia PR: API `682495d9-0fb6-4826-83f3-c3ae1921b329`, web `d1bea9ec-8ae9-4aca-8788-66e0d567480d`, środowisko `72c33357-bb81-4077-ba5e-29c381726b13`. Produkcja pozostaje na kodzie `197b740`.

Punkt 4.1 ma już aktualny dowód zielonego CI, choć checkbox pozostaje `[ ]`. Punkty 4.5 i 4.7 dotyczą migracji, bootstrapu admina i konfiguracji **po merge** — nie są wymagane przed merge. Wpis 4.6 należy uzupełniać w toku wdrożenia. Po spełnieniu powyższych warunków przed merge nie ma obecnie wskazanej potrzeby czwartego pełnego przeglądu kodu, o ile implementacja się nie zmieni.

## Podsumowanie raportu

```text
═══════════════════════════════════════════════════════════
  IMPLEMENTATION REVIEW: admin-bootstrap-and-login
  Reviewed phases: 1, 2, 3 | Date: 2026-09-29
  Findings: 0 critical, 0 warnings, 0 observations
  Plan Adherence        PASS
  Scope Discipline      PASS
  Safety & Quality      PASS
  Architecture          PASS
  Pattern Consistency   PASS
  Success Criteria      PASS
  Overall: APPROVED (phases 1–3)
  Merge: NOT READY (phase 4 prerequisites pending)
═══════════════════════════════════════════════════════════
```
