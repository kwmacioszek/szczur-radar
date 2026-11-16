<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: F-05: testy w przeglądarce w CI na atrapie API

- **Plan**: context/changes/e2e-harness/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-16
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 4 observations

Faza 3 jest przejrzana w całości (wszystkie wiersze Automated `[x]`). Jej wiersz Manual 3.6 (akceptacja listy 18 testów przez właściciela) nadal czeka i jest decyzją człowieka, nie ustaleniem.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Kryteria automatyczne uruchomione ponownie 2026-11-16 na `7f436ed`:

- `npm test`: 832 testy zielone.
- `npm run build`: zielony.
- Brak stałych trybów w atrapie.
- `vitest list` z `e2e/`: 0.
- Brak `page.locator(`, `waitForTimeout` i `xpath=`.
- `test-stack.md` ma 11 pól schematu.
- `npm run e2e` na zimnym porcie 4173: 18 testów zielonych.
- `--repeat-each=5 --workers=4`: 90 zielonych, bez `flaky`.
- Zepsucie seeda (`router/index.ts:63` bez `query.redirect`) daje czerwony seed. Błąd typu w `e2e/fixtures.ts` daje czerwony `npm run build`. Oba cofnięte, drzewo czyste.
- `web-e2e` jest w `needs` joba `railway-config`.
- PR #193 ma `Refs #176` i wszystkie checki zielone, w tym `web E2E tests (Playwright)` (bieg 43885226874, „18 passed”).
- Bieg czerwony 43882827640 ma artefakt `playwright-report`, a `railway-config` był w nim pominięty.

Zgodność z planem: wszystkie zaplanowane pliki MATCH, brak MISSING i EXTRA. Status F-05 w roadmapie to `in-progress` zamiast `planning` z tekstu planu. To kosmetyka: skrypt SVG i `roadmap.md` są zgodne, a wygenerowany SVG jest identyczny co do bajtu.

## Findings

### F1 — Seed i testy logowania łamią regułę lokatorów E2E

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/e2e/session-guard.spec.ts:15-22 (oraz auth.spec.ts:11-15,22,26,42-44,63; activation.spec.ts:9-17; feed.spec.ts:15-16; post.spec.ts:25)
- **Detail**: `REPOSITORY_GUIDELINES.md` (Browser tests) każe zawężać lokatory do `banner` / `main` / regionu, a etykiety podawać z `exact: true`. Sześć wywołań `getByLabel` nie ma `exact: true` (`'E-mail'` ×4, `'Spółka lub temat'`, `'Stanowisko'`). Testy logowania i aktywacji szukają na całej stronie (`page.getByRole('alert')`, `page.getByRole('status')`, `page.getByLabel(…)`), choć `LoginView` i `ActivateView` renderują `<main>`. Dziś każda strona ma jeden alert i jeden status. Po dodaniu toasta albo komunikatu w powłoce (S-47) te lokatory złamią tryb strict. Seed jest wzorcem z `test-stack.md`, więc kolejne testy go skopiują.
- **Fix**: dodać `{ exact: true }` do sześciu `getByLabel` i zawęzić lokatory w `session-guard`, `auth`, `activation` i `post:25` do `page.getByRole('main')`, jak w `feed.spec.ts` i `admin.spec.ts`.
- **Decision**: FIXED (Fix now). Lokatory są zawężone do `main`, etykiety mają `exact: true`. Komentarz wzorca w seedzie opisuje regułę. `Stanowisko` to `getByRole('combobox', { name, exact: true })`: `<select>` jest wewnątrz `<label>`, więc tekst etykiety obejmuje też opcje i `getByLabel(…, { exact: true })` niczego nie znajduje. Po poprawce `npm run e2e` daje 18/18, `--repeat-each=5 --workers=4` daje 90/90, a `npm run build` przechodzi.

### F2 — Kontrola wersji Playwrighta porównuje z kopią literału, nie z obrazem

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:146-156
- **Detail**: `IMAGE_VERSION: "1.65.1"` powtarza tag z `container:` (`:138`). Podniesienie samego `container:` zostawia `installed == IMAGE_VERSION` i krok przechodzi. Komentarz joba i wytyczne obiecują, że „a check step is red otherwise”. Test i tak padnie, bo w obrazie nie będzie rewizji przeglądarki dla starego Playwrighta, ale z mylącym błędem w `npm run e2e`, a nie z komunikatem kroku kontroli.
- **Fix**: sprawdzać tag w samym pliku workflow zamiast literału, np. `grep -q "mcr.microsoft.com/playwright:v$installed-noble" "$GITHUB_WORKSPACE/.github/workflows/ci.yml"`, i usunąć `IMAGE_VERSION`.
- **Decision**: FIXED (Fix now). `IMAGE_VERSION` usunięte. Krok czyta obraz z linii `container:` pliku `ci.yml` (`sed -n 's/^ *container: *\(mcr…playwright:[^ ]*\) *$/\1/p'`). Zwykły grep złapałby też wzorzec w samym kroku. Lokalnie wykonałem dokładny skrypt kroku: przy 1.65.1 wychodzi 0, przy 1.66.0 wychodzi 1 z `::error::`. Podmiana tagu w kopii pliku też daje czerwony wynik. Potwierdzenie w CI przyjdzie po pushu.

### F3 — Krok `chown` nie oddaje root-owego cache npm na puli self-hosted

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:171-175
- **Detail**: Kontener działa z `HOME=/github/home`, czyli z `_temp/_github_home` runnera. `npm ci` jako root zostawia tam `~/.npm` należące do roota. Krok oddaje tylko `$GITHUB_WORKSPACE`. Na runnerach GitHuba (stan dzisiejszy) nie ma to znaczenia. Na trwałej puli `szczur-ci` zostają pliki roota w `_temp`.
- **Fix**: w tym samym kroku oddać też `"$HOME"` (`chown -R … "$GITHUB_WORKSPACE" "$HOME"`).
- **Decision**: FIXED (Fix now). Krok oddaje teraz `"$GITHUB_WORKSPACE" "$HOME"`, a komentarz to opisuje. Potwierdzenie w CI przyjdzie po pushu.

### F4 — Raport nie jest zapisywany, gdy job przekroczy limit czasu

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:158
- **Detail**: Po `timeout-minutes: 10` bieg jest `cancelled`, nie `failure`. Upload z `if: failure()` się wtedy nie wykona, a przy zawieszeniu raport i trace przydałyby się najbardziej. Job `mutation` ma ten sam wzorzec.
- **Fix**: `if: failure() || cancelled()`.
- **Decision**: FIXED (Fix now). Krok uploadu raportu w `web-e2e` ma `if: failure() || cancelled()` z komentarzem. Job `mutation` zostaje bez zmian (poza zakresem F-05).

### F5 — Test ponowienia listy dowodzi wysłania żądania, nie jego wyniku

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/e2e/feed.spec.ts:62-73
- **Detail**: Po kliknięciu „Spróbuj ponownie” test sprawdza ten sam alert, który był już widoczny. `waitForRequest` dowodzi, że żądanie wyszło. Asercja może jednak przejść, zanim SPA przetworzy odpowiedź, a nawet gdy SPA jej nie wyrenderuje. Plan tak opisał ten test, więc to luka planu.
- **Fix**: czekać też na odpowiedź (`page.waitForResponse` z `status() === 500`) przed końcową asercją alertu.
- **Decision**: FIXED (Fix now). `waitForRequest` zastąpione przez `waitForResponse` (GET `/api/posts`, 500) przed końcową asercją alertu. `feed.spec.ts --repeat-each=5 --workers=4` daje 30/30.

### F6 — Komentarze o kolejności sprzątania mówią „po zamknięciu strony”

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/dev/mock-api.js:18, szczur-radar-web/e2e/fixtures.ts:21
- **Detail**: Fixture `mock` zależy od `page`, więc jego teardown (`control.dispose()`) wykonuje się przed zamknięciem strony, nie po nim. Zachowanie jest poprawne: `dispose` czyści timery, wznowione handlery nie odpowiadają, a `fulfill` łapie błąd zamkniętej strony. Komentarze opisują jednak inną kolejność.
- **Fix**: poprawić oba komentarze: `dispose()` wykonuje się po teście, zanim strona zostanie zamknięta, a odpowiedź na zamkniętą stronę i tak jest ignorowana.
- **Decision**: FIXED (Fix now). Oba komentarze mówią teraz, że `dispose()` wykonuje się po teście, zanim strona zostanie zamknięta. Kod bez zmian.

## Triage

Wszystkie 6 ustaleń poprawione 2026-11-16. Po poprawkach lokalnie:

- `npm test`: 832 testy zielone.
- `npm run e2e`: 18/18.
- `--repeat-each=5 --workers=4`: 90/90 bez `flaky`.
- `npm run build`: zielony.
- Brak lokatorów CSS, XPath i `waitForTimeout`.

Zmiany w `ci.yml` (F2–F4) potwierdzi bieg CI po pushu.
