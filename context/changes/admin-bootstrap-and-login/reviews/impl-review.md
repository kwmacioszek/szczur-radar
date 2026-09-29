<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Pierwszy admin przez CLI, logowanie/wylogowanie i ochrona tras

- **Plan**: context/changes/admin-bootstrap-and-login/plan.md
- **Scope**: Full plan (completed phases only; phase 4 remains pending)
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-09-29
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 0 observations
- **Triage**: COMPLETE — F1, F2, F3 fixed after the review

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Współbieżne próby mogą ominąć limit logowania

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/app/auth/routes.py:78
- **Detail**: `login_limiter.check()` działa przed `await session.scalar(...)` i `await run_in_threadpool(...)`, a porażka jest zapisywana dopiero w linii 93. Wiele równoległych żądań dla jednego e-maila/IP może przejść kontrolę, zanim którekolwiek zapisze porażkę, więc planowany limit 5/20 prób na 15 minut nie obowiązuje dla takiej serii.
- **Fix**: Rezerwować próbę atomowo w limiterze przed pierwszym `await`, a po wyniku rozliczać rezerwację i zwalniać ją także przy błędzie lub anulowaniu.
  - Strength: Zamyka lukę check–record przy równoległych żądaniach bez zmiany kontraktu API.
  - Tradeoff: Wymaga stanu prób w toku i testu współbieżności oraz anulowania.
  - Confidence: HIGH — między kontrolą a zapisem są dwa punkty `await`.
  - Blind spot: Nie zmierzono jeszcze obciążenia podczas serii równoległych logowań.
- **Decision**: FIXED — atomic `reserve()` before DB/Argon2 work; `finish()` in `finally` records the result or releases an aborted attempt. Concurrent and cancelled request tests pass.

### F2 — Wygasłe liczniki prób pozostają w pamięci

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/app/auth/rate_limit.py:37
- **Detail**: `_recent()` usuwa wygasłe wpisy tylko po ponownym użyciu tego samego klucza. Jednorazowe nieudane logowania dla różnych e-maili i IP pozostają w `_by_email` i `_by_ip` do końca życia procesu; niezalogowany klient może stale zwiększać zużycie pamięci.
- **Fix**: Dodać okresowe czyszczenie wygasłych kluczy w obu słownikach i sprawdzić zachowanie przy wielu unikatowych próbach.
  - Strength: Zachowuje obecny licznik i okno 15 minut, a usuwa stan, który nie może już wpływać na limit.
  - Tradeoff: Czyszczenie skanuje klucze; należy ograniczyć jego częstotliwość.
  - Confidence: HIGH — kod usuwa klucz wyłącznie w `_recent()` dla tego samego klucza lub po udanym logowaniu dla e-maila.
  - Blind spot: Nie zmierzono tempa przyrostu pamięci przy rzeczywistym ruchu.
- **Decision**: FIXED — expired e-mail and IP keys are swept on the next check after a window; the one-off-key cleanup test passes.

### F3 — Wizualizacja roadmapy nie odpowiada zmianie statusu S-01

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: context/foundation/roadmap.md:48
- **Detail**: Commit funkcji zmienił S-01 na `in-progress`, ale `tools/render_roadmap.swift:162` wciąż pokazuje `planowanie`, a `docs/visuals/roadmap-mvp.png` nie został wygenerowany ponownie. `AGENTS.md` wymaga obu kroków przy każdej zmianie roadmapy.
- **Fix**: Zaktualizować status S-01 w rendererze i wygenerować obraz ponownie.
- **Decision**: FIXED — S-01 now reads `w toku` in the renderer, the displayed date matches the roadmap update, and `roadmap-mvp.png` was regenerated and visually checked.

## Triage result

- F1, F2, F3: fixed in the current working tree on 2026-09-29.
- Backend verification after fixes: `TEST_DATABASE_URL=… REQUIRE_DB_TESTS=1 uv run pytest -q --tb=short` — 67 passed, 1 existing deprecation warning.
- `git diff --check` passed; the generated PNG is 3200 × 3740 and its S-01 card shows `W TOKU`.
- No `/10x-lesson` entry was added: these findings have been fixed, and F3's renderer rule already exists in `AGENTS.md`.
- The `NEEDS ATTENTION` verdict above records the original review; every finding from that review is now resolved.

## Verification

- `cd szczur-radar-api && uv sync --locked`: PASS (`Resolved 43 packages; Checked 40 packages`), z cache w `/private/tmp` z powodu ograniczenia sandboxa.
- `TEST_DATABASE_URL=… REQUIRE_DB_TESTS=1 uv run pytest -q`: PASS (64 passed, 1 deprecation warning) na lokalnym Postgresie 18; obejmuje round trip migracji, kompletność autoryzacji, logowanie, limity, Origin, tokeny i CLI. Pierwszy bieg bez dostępu do lokalnego portu był zablokowany przez sandbox; powtórzony z dostępem przeszedł.
- `uv run pytest -q tests/test_health.py` bez `DATABASE_URL`: PASS (3 passed).
- `cd szczur-radar-web && npm test`: PASS (3 files, 29 tests) z Node 24.
- `cd szczur-radar-web && npm run build`: PASS (`vue-tsc -b` i Vite build) z Node 24.
- PR #13: backend, web-unit, web-container-smoke i railway-config są zielone w [GitHub Actions](https://github.com/kwmacioszek/szczur-radar/actions/runs/36533567727).
- Railway PR env `szczur-radar-pr-13`: API deployment dla commita `8c54471` ma `preDeployCommand = uv run alembic upgrade head`; `railway config plan --json` zwraca 0 zmian. Produkcja pozostaje na `197b740`; bieżący plan dla niej proponuje jedynie bezpieczne dodanie API `preDeployCommand`. Historyczne kryterium z fazy 1 — zero zmian *przed* dodaniem pre-deploy — nie jest ponownie uruchamialne z bieżącego pliku IaC.
- Manualne punkty faz 1–3 mają `[x]` w Progress. Kontrole lokalnego UI i `curl` są zapisane w commitach Progress oraz pokryte odpowiednimi testami; nie powtarzano ręcznej sesji w przeglądarce. Punkty fazy 4 pozostają `[ ]` i nie wchodzą do tego przeglądu.

## Scope notes

- Planowane elementy faz 1–3 są obecne bez istotnych braków lub odchyleń. Dodatkowe pliki testów, helper przekierowania i stylów wspierają zaplanowaną funkcję.
- Wcześniejsze, niezwiązane z funkcją niezatwierdzone zmiany w `.agents/`, `.claude/`, `AGENTS.md` i `CLAUDE.md` nie były częścią zakresu diffu `7139be8^..HEAD` i pozostały nietknięte.

## Najnowszy przegląd — 2026-09-29

Trzeci przegląd dla `8ecedd8`: **APPROVED** dla faz 1, 2, 3, bez nowych ustaleń. **Merge nadal oczekuje warunków fazy 4**, w tym potwierdzonego brakującego `JWT_SECRET` na produkcji. Pełne aktualne wyniki: [impl-review-rereview3-2026-09-29.md](impl-review-rereview3-2026-09-29.md). Wcześniejsze ustalenia i decyzje powyżej zachowano jako ślad planowania.
