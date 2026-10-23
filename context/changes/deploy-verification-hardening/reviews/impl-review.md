<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Wiarygodna weryfikacja wdrożeń

- **Plan**: context/changes/deploy-verification-hardening/plan.md
- **Scope**: Full plan (wszystkie wiersze Automated `[x]`; wiersze Manual częściowo otwarte — przegląd na prośbę operatora przed ich zamknięciem)
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-23
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 5 observations

Zakres kodu: repo `kwmacioszek/szczur-radar-monitor` (`origin/main` + PR #16, commit `d44bd10`) oraz gałąź `feature/deploy-verification-hardening` w szczur-radar (diff od merge-base z `origin/main`).

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | PASS |
| Success Criteria | WARNING |

Kryteria Automated (2026-10-23): Worker `npm test` 44/44, `tsc --noEmit` OK; `verifier/test.sh` OK; shellcheck v0.11.0 i actionlint 1.7.12 bez uwag; CI monitora zielone na `main` (39458625188) i na gałęzi PR #16 (39932616368); środowisko `monitor` z polityką gałęzi `main`; `tools/deploy-gate/test.sh` OK; brak odwołań do `verify-deploy.sh` / `deploy-verify.yml` poza `context/`; CI szczur-radar na PR #99 zielone (39941340250). Manual: 1.3, 2.4, 3.3–3.11, 4.4, 4.5 otwarte (dowody 1.3, 2.4, 3.3, 3.9, 4.4 w komentarzach #91); 1.3 w zapisanej formie nie przechodzi (F2).

## Findings

### F1 — Fałszywy RED, gdy nowszy deploy czeka na CI

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-monitor/verifier/verify.sh:186-216
- **Detail**: Werdykt bierze najnowszy deploy usługi wg `createdAt`, a `WAITING` / `QUEUED` / `BUILDING` ponawia tylko przez `SETTLE_TIMEOUT` (5 min). Push w szczur-radar od razu tworzy deploy `WAITING` (Wait for CI), a CI trwa zwykle dłużej niż 5 min. Run weryfikacji poprzedniego deployu, który trafi w ten moment, kończy się RED „nieweryfikowalne: WAITING” z issue i pushem do operatora, choć usługa serwuje poprzedni `SUCCESS`. Prawdopodobne przy szybkich kolejnych pushach do PR i kolejnych merge'ach na `main`. Samo się leczy przy następnym zielonym runie, ale każdy taki przypadek to fałszywy alarm.
- **Fix A ⭐ Recommended**: Wiersze `WAITING` / `QUEUED` / `NEEDS_APPROVAL` nowsze niż deploy z webhooka pomijać (ich własny webhook końcowy przyjdzie później) i oceniać najnowszy deploy, który już ruszył.
  - Strength: run ocenia to, co zgłosił webhook; nie ma alarmu za deploy, który jeszcze nie wystartował; reguła „stan z Railway, nie z webhooka” zostaje.
  - Tradeoff: jeden przypadek więcej w regule werdyktu i w `test.sh`.
  - Confidence: MED — wymaga sprawdzenia, czy deploy w Wait for CI ma stan `WAITING` czy `QUEUED`.
  - Blind spot: zachowanie `BUILDING` dłuższego niż 5 min (budowa obrazu Javy) zostaje.
- **Fix B**: Zostawić i opisać w README jako znane ograniczenie.
  - Strength: zero zmian w kodzie.
  - Tradeoff: fałszywe issue i pushe przy szybkiej iteracji.
  - Confidence: HIGH — zachowanie przewidywalne.
  - Blind spot: częstotliwość w praktyce nie zmierzona.
- **Decision**: FIXED via Fix A (szczur-radar-monitor@eb1ee62, PR #16: filtr w `verify.sh`, przypadki `newer-*` w `test.sh`, README)

### F2 — Nazwane eksporty w module wejściowym Workera łamią lokalny workerd

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: szczur-radar-monitor/worker/src/index.ts:24-39,61,105
- **Detail**: `index.ts` eksportuje stałe i funkcje obok `default`. `wrangler dev` (4.147.0) nie startuje: „Incorrect type for map entry 'GITHUB_API_VERSION'”. Wdrożony Worker to toleruje, ale testy Vitest działają w Node i tego nie wykryją; kryterium 1.3 w zapisanej formie nie przechodzi, instrukcja lokalnego uruchomienia w README nie działa, przyszły deploy może zostać odrzucony.
- **Fix**: Przenieść `handle` i stałe do `worker/src/relay.ts`, w `index.ts` zostawić tylko `export default { fetch }`, testy importują z `relay.ts`; potem powtórzyć 1.3.
- **Decision**: FIXED (szczur-radar-monitor@b591b31, PR #16). `wrangler dev` 2026-10-23 11:27Z: poprawny sekret bez tokena GitHub → 502, zły → 401, `GET /` → 404; testy 44/44, `tsc` OK. Wdrożony Worker zmieni się dopiero po merge'u PR #16 i `wrangler deploy` (operator).

### F3 — Wywołania `railway` i `gh` bez limitu czasu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-monitor/verifier/verify.sh:147, 315-358
- **Detail**: Zawieszone wywołanie CLI kończy job dopiero na `timeout-minutes: 15`: bez raportu i bez issue, jedynie e-mail o nieudanym runie. Okna `curl` mają limity, wywołania CLI nie.
- **Fix**: Owinąć `railway` i `gh` w `timeout 60` (coreutils na `ubuntu-latest`); kod 124 z `railway` traktować jak „Railway nie odpowiada”, z `gh` jak `gh_failed`; przypadek w `test.sh`.
- **Decision**: FIXED differently (szczur-radar-monitor@ab8ffea, PR #16): zamiast coreutils `timeout` (brak na macOS, gdzie też działa `test.sh`) funkcja `limited()` w `lib.sh`, limit `CLI_TIMEOUT` (60s); testy `railway-hangs`, `gh-hangs`.

### F4 — Dokumentacja szczur-radar nie nadąża za PR #16 i S-14

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: REPOSITORY_GUIDELINES.md:62; context/deployment/deploy-plan.md:924, 975, sekcja `deploy-verification-hardening`; tools/deploy-gate/README.md
- **Detail**: Po PR #16 `postgres` i crony są oceniane samym stanem, a odrzucony token daje od razu RED; szczur-radar mówi tylko o „cronie”. W S-14 linie 924 („triggers the workflow `deploy-verify`”) i 975 („handed over to deploy-verify”) opisują usunięty workflow, choć 986 ma już notkę „Replaced”.
- **Fix**: Dopisać „usługi bez sond (crony, `postgres`)” i „odrzucony token: od razu RED” w REPOSITORY_GUIDELINES, deploy-plan i README deploy-gate; przy 924 i 975 notka „Update (deploy-verification-hardening)” z odesłaniem do nowej sekcji.
- **Decision**: FIXED (szczur-radar: REPOSITORY_GUIDELINES, deploy-plan sekcja monitora i S-14, README deploy-gate; także F1 i F3 opisane)

### F5 — Cicha awaria przekaźnika (PAT, brak logów Workera)

- **Severity**: 💬 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-monitor/worker/wrangler.toml; worker/src/index.ts:81, 171
- **Detail**: Po wygaśnięciu PAT (wymagana data ważności) Worker zwraca 502, Railway ponawia i rezygnuje; Worker nie ma `[observability]`, więc logi nie są trwałe. Monitor przestaje działać bez sygnału. Wykrywanie brakujących runów (heartbeat, reconcile) plan świadomie wyłączył („What We're NOT Doing”), więc proponowana poprawka zostaje w tych granicach.
- **Fix**: `[observability] enabled = true` w `wrangler.toml` i w runbooku `deploy-plan.md` data wygaśnięcia PAT z przypomnieniem o rotacji; bez heartbeatu (poza zakresem planu).
  - Strength: trwałe logi 502/204 do diagnozy, zero nowych mechanizmów.
  - Tradeoff: awaria nadal wykrywana dopiero przez operatora.
  - Confidence: HIGH — standardowa opcja Workers.
  - Blind spot: limity darmowego planu logów nie sprawdzone.
- **Decision**: FIXED (szczur-radar-monitor@9954b87 `[observability] enabled = true`, PR #16, działa po `wrangler deploy`; deploy-plan: przypomnienie o wygaśnięciu PAT i diagnoza z logów Workera). Limity Free (ok. 200 tys. zdarzeń dziennie, 3 dni) daleko powyżej ruchu; bez heartbeatu, zgodnie z planem.

### F6 — Rozpoznanie `Unauthorized` zależy od jednej treści komunikatu

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-monitor/verifier/verify.sh:150 (PR #16)
- **Detail**: `grep -q Unauthorized` rozróżnia wielkość liter i jedno sformułowanie CLI. Inne brzmienie wraca do 5 min ponowień i mylącego „Railway nie odpowiada” (bezpiecznie, ale z błędną przyczyną).
- **Fix**: `grep -qiE 'unauthorized|invalid.*token'` w PR #16.
- **Decision**: FIXED (szczur-radar-monitor@3e505a4, PR #16; test `railway-invalid-token`)

### F7 — Ręczny dispatch nie sprawdza zgodności ID i nazwy środowiska

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-monitor/.github/workflows/verify.yml:15-22; verifier/verify.sh:74-89
- **Detail**: Stan czytany jest po `environment_id`, sondy i issue po `environment_name`. Literówka w ręcznym uruchomieniu (udokumentowana ścieżka przy zgubionym webhooku) miesza dwa środowiska.
- **Fix**: W README i runbooku: ID i nazwę kopiować razem z jednego źródła (`railway environment list --json`); opcjonalnie odrzucać niezgodność w skrypcie.
- **Decision**: FIXED (README monitora w PR #16 i runbook w deploy-plan; bez walidacji w skrypcie)

### F8 — Słabsze przypięcie zależności niż akcji

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-monitor/.github/workflows/verify.yml (`npm install --global @railway/cli@5.67.1`), .github/workflows/ci.yml:29 (`koalaman/shellcheck:v0.11.0`)
- **Detail**: Akcje są przypięte po SHA, ale CLI Railway instalowany jest z npm bez lockfile'a w jobie z tokenem workspace (zależności przechodnie nieprzypięte), a obraz shellcheck po tagu. W publicznym repo z tokenem Railway to jedyna ścieżka łańcucha dostaw do sekretu.
- **Fix**: CLI z wydania GitHub z weryfikacją sumy SHA-256 (albo `npm ci` z lockfile'em w repo); obraz shellcheck po digeście.
- **Decision**: FIXED (szczur-radar-monitor@ace0e8c, PR #16): binarka `railway-v5.67.1-x86_64-unknown-linux-musl` z `sha256sum --check` (suma zgodna z digestem GitHuba), bez setup-node; shellcheck `@sha256:61862eba…`. actionlint OK, pobranie i suma sprawdzone lokalnie; pierwszy prawdziwy run dopiero po merge'u (środowisko `monitor` tylko z `main`).
