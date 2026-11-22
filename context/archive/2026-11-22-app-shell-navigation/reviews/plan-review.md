<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-47: powłoka aplikacji z nawigacją u góry

- **Plan**: context/changes/app-shell-navigation/plan.md
- **Mode**: Deep
- **Date**: 2026-11-22
- **Verdict**: SOUND (after triage: all 4 findings fixed)
- **Findings**: 0 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
18/18 paths ✓, 7/7 symbols ✓ (formatDateTime, reportFeedLoaded, focusLinkTo, FRESHNESS, bellLoaded, wzorzec QUALITY w mockApi.test, afterEach w NotificationBell), brief↔plan ✓, Progress↔Faza ✓, lessons (konto admina w PR env, Automated/Manual) ✓

## Findings

### F1 — NotificationMenu poza planem: grep 2.6 i miejsce listy

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 1 §1 (AppBar), Faza 2 §4 i kryterium 2.6
- **Detail**: Komentarz w `src/components/NotificationMenu.vue:6` wymienia AppHeader („below the nearest positioned ancestor … (AppHeader's bar)”). Faza 2 §4 poprawia tylko notificationsStore i NotificationBell, więc kryterium 2.6 (`! grep -rn "AppHeader" …src`) będzie czerwone. Skutek praktyczny: lista dzwonka ustawia się według najbliższego przodka z `position`. Prototyp ma `.app-bar > * { position: relative }`. Jeśli tę regułę skopiować, przodkiem stanie się opakowanie slotu dzwonka, a nie pasek, i przy 375 px lista może wyjść poza ekran. Kryterium 1.4 sprawdza 320 px tylko przy zamkniętej liście.
- **Fix**: W Fazie 2 §4 dopisać NotificationMenu.vue (komentarz: „AppBar”). W kontrakcie AppBar zapisać, że lista dzwonka ustawia się według samego paska: tylko `header` ma `position`, a opakowanie slotu nie. W kryterium 1.4 dodać sprawdzenie przy otwartej liście w wariancie admina.
- **Decision**: FIXED (Fix in plan)

### F2 — Odczyt daty po wylogowaniu i przy przerwanej nawigacji

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2 §2 (AppShell, „Data danych”)
- **Detail**: Przy wylogowaniu `router.replace({ name: 'login' })` wywołuje `afterEach`, gdy powłoka jest jeszcze zamontowana. Jeśli ostatni odczyt ma ponad 60 s, powłoka pyta `/api/freshness` już bez sesji i dostaje 401. NotificationBell chroni się przed tym dwoma warunkami: odczyt tylko przy `currentUser` (NotificationBell.vue:36) i pominięty przy `failure` (:81). W kontrakcie AppShell nie ma żadnego z nich.
- **Fix**: W kontrakcie: odczyt tylko przy zalogowanym `currentUser` i bez `failure` w `afterEach`, jak w dzwonku. W testach AppShell dodać przypadek „wylogowanie po 60 s nie pyta o datę danych”.
- **Decision**: FIXED (Fix in plan)

### F3 — Odznaka stanu obok tytułu przebiegu nie ma miejsca w PageHeader

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 §2, Faza 2 §3, „What We're NOT Doing”
- **Detail**: W `RunDetailView.vue:158-161` `h1` i `StatusBadge` stoją razem w `.title-row`. PageHeader ma tylko title, subtitle i slot `tools`, a plan mówi, że „żaden widok go jeszcze nie wypełnia”. Implementator musi zgadnąć, gdzie trafia odznaka, a każdy wybór inaczej zmienia wzorzec `view-run`.
- **Fix**: Zapisać w Fazie 2 §3, gdzie trafia odznaka (np. slot obok tytułu w PageHeader). Poprawić zdanie o pustym `tools`.
- **Decision**: FIXED (Fix in plan: slot `status` za `h1`, jak `.title-row` w `run.html`; `tools` zostaje pusty)

### F4 — Notatki „pause for manual confirmation” w fazach bez wierszy Manual

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 i Faza 2, „Implementation Note”
- **Detail**: Fazy 1 i 2 każą czekać na potwierdzenie człowieka, ale nie mają żadnego wiersza Manual. Kryteria 1.4 i 2.7 sprawdza agent, i słusznie są w Automated. W biegu `/10x-goal-implement` taka notatka przeczy biegowi bez nadzoru albo zostaje po cichu pominięta.
- **Fix**: Zamienić obie notatki na „commit po zielonych bramkach, bez przerwy” albo je usunąć. Jedyna bramka człowieka to 3.6.
- **Decision**: FIXED (Fix in plan)
