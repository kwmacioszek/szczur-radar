<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Pierwszy admin przez CLI, logowanie/wylogowanie i ochrona tras — ponowny przegląd

- **Plan**: context/changes/admin-bootstrap-and-login/plan.md
- **Scope**: Full plan (completed phases only; phase 4 remains pending); full branch diff `197b740..2a32003`, including fixes after the first review
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-09-29
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — CLI może utworzyć admina, którego API nie pozwoli zalogować

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/app/cli.py:85
- **Detail**: `create-admin` wymaga tylko znaku `@` i zapisuje dowolnie długi adres. `LoginRequest.email` w `app/auth/routes.py:35` ma limit 320 znaków. Adres o długości 321 znaków przechodzi normalizację CLI, ale logowanie kończy się 422 przed odczytem użytkownika; kolejne `create-admin` odmawia, bo admin już istnieje. Odrzucenie przez Pydantic (`string_too_long`) zostało odtworzone lokalnie.
- **Fix**: Ujednolicić walidację adresu CLI z kontraktem logowania (w tym limit 320 znaków) przed zapisem i dodać test nieprawidłowego adresu.
- **Decision**: FIXED — wspólny `MAX_EMAIL_LENGTH` obowiązuje w API i CLI; CLI odmawia zapisu adresu dłuższego niż 320 znaków, a test sprawdza brak konta i promptu.

### F2 — Równoległe uruchomienia CLI mogą utworzyć dwóch pierwszych adminów

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/app/cli.py:43
- **Detail**: Dwa procesy `create-admin` mogą oba odczytać brak konta z rolą `admin` przed pierwszym commitem, po czym zapisać dwa różne adresy. Migracja ogranicza wartości roli i unikalność e-maila, ale nie serializuje tego sprawdzenia. To narusza kontrakt fazy 2 §7: ponowne uruchomienie ma odmówić utworzenia kolejnego admina. Obecny test obejmuje tylko wywołania sekwencyjne.
- **Fix**: Objąć sprawdzenie i insert transakcyjną blokadą doradczą PostgreSQL oraz dodać test dwóch równoległych wywołań.
  - Strength: Zachowuje możliwość wielu adminów w przyszłym S-07, blokując tylko wyścig bootstrapu.
  - Tradeoff: Wymaga integracyjnego testu współbieżności; równoczesny operator będzie czekał na zwolnienie blokady.
  - Confidence: HIGH — osobne transakcje nie mają wspólnej blokady, a unikalność dotyczy wyłącznie e-maila.
  - Blind spot: Nie odtworzono wyścigu dwoma rzeczywistymi procesami CLI.
- **Decision**: FIXED — `pg_advisory_xact_lock` serializuje sprawdzenie i zapis pierwszego admina; test uruchamia dwie równoległe sesje CLI przy zewnętrznie trzymanej blokadzie i potwierdza dokładnie jeden zapis.

### F3 — Nowa umiejętność archiwizacji koliduje z regułami repozytorium

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: .agents/skills/10x-archive/SKILL.md:155
- **Detail**: Umiejętność poleca `git mv` do `context/archive/`, podczas gdy `AGENTS.md:7,87` zabrania umiejętnościom zapisu w tym katalogu. Ten sam plik w kroku zamykania zmiany modyfikuje `context/foundation/roadmap.md` (`:162-183`), ale nie aktualizuje `tools/render_roadmap.swift` ani `docs/visuals/roadmap-mvp.png`, czego wymaga `AGENTS.md:20`. Użycie umiejętności bez uwzględnienia nadrzędnych reguł odtworzy wcześniejsze ustalenie dotyczące wizualizacji roadmapy.
- **Fix**: Uzgodnić lokalną kopię umiejętności i jej manifest z `AGENTS.md` przed użyciem: zablokować zapis do `context/archive/` oraz wymagać aktualizacji renderera i PNG przy zmianie roadmapy.
  - Strength: Kolejny agent dostaje spójne instrukcje w miejscu wykonywania pracy.
  - Tradeoff: To plik zarządzany przez 10x CLI; lokalna poprawka wymaga utrzymania manifestu albo zmiany upstream, by `sync` jej nie nadpisał.
  - Confidence: HIGH — sprzeczne instrukcje wskazują dokładne ścieżki i operacje.
  - Blind spot: Umiejętność nie została wywołana w tej zmianie, więc nie doszło jeszcze do naruszenia danych.
- **Decision**: ACCEPTED — użytkownik utrzymuje bezwzględny zakaz zapisu do `context/archive/` z `AGENTS.md`. `10x-archive` jest pobranym plikiem kursowym i pozostaje nietknięty; reguła repozytorium ma pierwszeństwo. Przy każdej zmianie roadmapy nadal obowiązuje aktualizacja renderera i PNG.

### F4 — Aktualizacja narzędzi 10x CLI wyszła poza plan S-01

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: AGENTS.md:48
- **Detail**: Commit `2a32003` wraz z poprawkami auth dodał lub zmienił umiejętności i manifesty w `.agents/` oraz `.claude/`, a także bloki kursowe w `AGENTS.md` i `CLAUDE.md`. Plan fazy 3 §6 wyraźnie zakładał pozostawienie bloku `@przeprogramowani/10x-cli` bez zmian. Jest to niezależna aktualizacja narzędzi, bez wpływu na runtime auth; 61 plików wymienionych w manifestach i ich hashe sprawdzono jako spójne.
- **Fix**: Opisać aktualizację 10x CLI jako osobny zakres w PR; następne synchronizacje commitować oddzielnie od implementacji funkcji.
- **Decision**: SKIPPED — niezależna aktualizacja narzędzi to oczekiwany skutek pobrania nowej lekcji; użytkownik uznał odchylenie zakresu za nieistotne.

## Coverage and verification

- Progress: fazy 1–3 mają 21/21 punktów `[x]`; faza 4 ma 0/7 i nie była przedmiotem przeglądu. `change.md` ma już `status: impl_reviewed` i `updated: 2026-09-29`.
- Poprzednie F1 (równoległe logowania), F2 (wygasłe liczniki) i F3 (status S-01 na obrazie) są naprawione: `reserve()` wykonuje atomową kontrolę przed pierwszym `await`, `finish()` zwalnia rezerwację w `finally`, sweep usuwa wygasłe klucze, a renderer pokazuje `w toku`. Testy współbieżności, anulowania i sweep przechodzą. Nie są ponownymi ustaleniami.
- `cd szczur-radar-api && uv sync --locked`: PASS (43 pakiety rozwiązane, 40 sprawdzonych).
- `TEST_DATABASE_URL=… REQUIRE_DB_TESTS=1 uv run pytest -q --tb=short`: PASS, 67 testów na lokalnym PostgreSQL, 1 istniejące ostrzeżenie deprecacyjne. Test bez dostępu do portu 5432 najpierw trafił na blokadę sandboxa; powtórzony z dostępem przeszedł.
- `uv run pytest -q tests/test_health.py` bez `DATABASE_URL`: PASS, 3 testy.
- `cd szczur-radar-web && npm test`: PASS, 3 pliki / 29 testów na Node 24. `npm run build`: PASS (`vue-tsc -b` + Vite). Domyślny `npm` w podkatalogu wskazywał lokalny Node 18 i kończył się błędem importu `node:util.styleText`; po wskazaniu Node 24 oba oryginalne polecenia przeszły.
- `node --test .agents/skills/10x-plan/scripts/metadata-guard.test.mjs`: PASS, 18 testów. `git diff --check` dla kodu, konfiguracji i dokumentów poza `.agents/`/`.claude/`: PASS. Pełny diff zgłasza tylko sześć celowych spacji kończących linie Markdown w pobranej umiejętności `10x-impl-review`.
- PR #13: `backend`, `web-unit`, `web-container-smoke` i `railway-config` są zielone dla `8c54471`. `2a32003` pozostaje lokalny, więc nie ma jeszcze wyniku CI dla poprawek po pierwszym przeglądzie. Odczytowy `railway config plan --json` w środowisku `szczur-radar-pr-13` zwrócił `ok: true` i `changeSet.changes: []`; pre-deploy jest ustawiony. Historycznego kryterium „0 zmian na produkcji przed dodaniem preDeploy” nie da się ponownie uruchomić z obecnego pliku IaC.
- Punkty Manual faz 1–3 są odhaczone w Progress i mają dowody w wcześniejszych commitach oraz pierwszym raporcie. Nie powtarzano ręcznej sesji w przeglądarce ani `curl`; weryfikacja wdrożeniowa fazy 4 pozostaje oczekująca.
- Istniejący `reviews/impl-review.md` zachowano jako ślad pierwszego przeglądu. Nieśledzony `AGENTS.user.md` jest kopią starego `AGENTS.md` i nie należy do diffu `197b740..2a32003`.

## Triage result — 2026-09-29

- F1, F2: naprawione w bieżącym drzewie roboczym. Testy CLI: 8 passed; pełny backend na lokalnym PostgreSQL: 69 passed, 1 istniejące ostrzeżenie deprecacyjne; `git diff --check` przeszedł.
- F3: zaakceptowano nadrzędność zakazu z `AGENTS.md`; plik kursowy bez zmian. To rozstrzyga działanie w tym repozytorium, choć pobrana umiejętność nadal zawiera sprzeczny krok.
- F4: pominięto — aktualizacja narzędzi jest oczekiwanym skutkiem pobrania nowej lekcji.
- Brak ustaleń z `Decision: PENDING`. Werdykt `NEEDS ATTENTION` powyżej opisuje stan w chwili przeglądu, przed triage.
