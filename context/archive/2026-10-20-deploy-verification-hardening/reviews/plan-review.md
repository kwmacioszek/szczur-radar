<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Wiarygodna weryfikacja wdrożeń

- **Plan**: `context/changes/deploy-verification-hardening/plan.md`
- **Mode**: Deep
- **Date**: 2026-10-20
- **Verdict**: REVISE → SOUND po triage (plan przepisany)
- **Findings**: 1 critical, 4 warnings, 2 observations

Kontekst przeglądu od użytkownika: „Ja chcę głównie mieć monitor środowiska po deployu paczki.
Tylko tyle. […] sprawdzić, czy na pewno produkcja po deployu wstała, nie sypie błędami i tyle.”

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | FAIL |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding

10/10 ścieżek ✓, 4/6 symboli ✓ (`ci.yml:267,420` bez tekstu „deploy-verify”), brief↔plan ✗
(brief opisywał reconcile, którego plan już nie miał).

## Findings

### F1 — Cztery nowe elementy infrastruktury dla jednego sygnału

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — stawka architektoniczna
- **Dimension**: Lean Execution / Architectural Fitness
- **Location**: Implementation Approach, Fazy 2–3
- **Detail**: Publiczne repo, Cloudflare Worker, webhook Railway, dwa PAT-y i token workspace
  Railway dla czerwieni, która nie blokuje Wait for CI. Frame ustalił, że status commita w osobnym
  kontekście daje to także w szczur-radar; `deploy-verify.yml` już reaguje na finalne zdarzenia,
  a token Railway już jest w sekretach repo (`railway-config`).
- **Fix A ⭐ Recommended**: Rozbudować istniejący `deploy-verify` w szczur-radar (stan usług
  przez `railway deployment list --json`, status commita, jedno issue; run nadal zielony).
  - Strength: zero nowej infrastruktury, testy w istniejącym `test.sh`.
  - Tradeoff: runy na `szczur-ci`, runy `skipped` zostają, czerwień tylko w statusie i issue.
  - Confidence: HIGH.
  - Blind spot: czy `deployment_status` `success` przychodzi przed końcem wszystkich usług.
- **Fix B**: Zostawić zewnętrzny monitor jak w planie.
  - Strength: darmowe runnery, zero runów w szczur-radar, czerwony run.
  - Tradeoff: nowe repo, język, platforma, 4 sekrety.
  - Confidence: MEDIUM.
  - Blind spot: wyłączanie workflowów w nieaktywnym repo publicznym; limit 100 w `queue: max`.
- **Decision**: FIXED (fix differently). Użytkownik odrzucił Fix A: nie chce runów w szczur-radar,
  „nigdy czerwony” jest dla niego bezużyteczny, przeszkadza szum `skipped` i zależność od
  `szczur-ci`. Wybrał odchudzony zewnętrzny monitor: Worker jako przekaźnik, jeden skrypt
  (stan usług z Railway + `/api/ready` + `/healthz`), czerwony run i issue w repo monitora
  przypisane do operatora. Bez fazy 1, bez statusu commita, bez PAT do szczur-radar, bez bramki
  produkcji. Plan i brief przepisane.

### F2 — Faza 1 (commit w API i webie) nie jest potrzebna do celu

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM
- **Dimension**: Lean Execution
- **Location**: Faza 1; Critical Implementation Details (tożsamość)
- **Detail**: `commit` w `/api/ready`, `ARG`, `/version.json`, reguła Caddy i asercje CI służą
  wykryciu „stara wersja nadal serwuje”, a to samo mówi stan `FAILED`/`CRASHED` z Railway API,
  który plan i tak czyta. Plan sam wskazywał, że brak SHA przy buildzie blokuje całość.
- **Fix A ⭐ Recommended**: Usunąć fazę 1 w całości.
  - Strength: zero zmian w aplikacji; znika jedyne twarde ryzyko blokujące.
  - Tradeoff: przypadek „Railway mówi SUCCESS, serwuje stary obraz” niewykrywany; nie
    zaobserwowany w 30 deployach.
  - Confidence: HIGH.
  - Blind spot: żadnego istotnego.
- **Fix B**: Zostawić tylko `commit` w `/api/ready` z env kontenera.
  - Strength: tanie, bez `ARG`, Caddy i `/version.json`.
  - Tradeoff: web bez tożsamości.
  - Confidence: MEDIUM.
  - Blind spot: `RAILWAY_GIT_COMMIT_SHA` w kontenerze nie sprawdzone na żywo.
- **Decision**: FIXED via Fix A (w ramach przepisania po F1).

### F3 — Maszyna stanów issue ponad potrzebę

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM
- **Dimension**: Lean Execution
- **Location**: Critical Implementation Details (zdarzenia, issue); Testing Strategy
- **Detail**: Znacznik zbioru awarii, wpisy per usługa, `superseded`, osobna ścieżka REMOVED,
  sześć testów na samo issue. Dla „jedno issue na środowisko, zamknij gdy zielono” wystarczy
  werdykt z bieżącego stanu wszystkich usług i kolejka per środowisko.
- **Fix**: Werdykt środowiska ze stanu bieżącego; RED → otwórz albo skomentuj, GREEN → zamknij.
  - Strength: jedna reguła, cztery testy zamiast dwunastu.
  - Tradeoff: każde kolejne RED dodaje komentarz.
  - Confidence: HIGH — tak działa dziś `verify-deploy.sh:163-186`.
  - Blind spot: żadnego istotnego.
- **Decision**: FIXED (w ramach przepisania po F1).

### F4 — „Nie sypie błędami” nie jest zdefiniowane

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW
- **Dimension**: Blind Spots
- **Location**: Desired End State
- **Detail**: Plan sprawdza stan deployu i HTTP 200; API, które wstaje, a po minucie rzuca
  wyjątkami, przejdzie na zielono.
- **Fix**: Zapisać wprost: zielony = `SUCCESS`/`SLEEPING` każdej usługi + 200 z sond w oknie 3 min;
  błędy po starcie poza zakresem (osobna zmiana).
- **Decision**: FIXED (Desired End State i What We're NOT Doing w nowym planie).

### F5 — Brief rozjechał się z planem

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW
- **Dimension**: Plan Completeness
- **Location**: `plan-brief.md`; plan :164, :166
- **Detail**: Brief obiecywał reconcile, którego plan miał w „NOT Doing”; plan miał wiszące
  odwołania „decyzja F2” i „F7 — B” do nieistniejących ustaleń.
- **Fix**: Przepisać brief z planu, usunąć wiszące odwołania.
- **Decision**: FIXED (brief przepisany).

### F6 — Luka w numeracji Progress (3.12)

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Plan Completeness
- **Location**: Progress, Faza 3
- **Detail**: Numeracja skakała 3.11 → 3.13.
- **Fix**: Przenumerować.
- **Decision**: FIXED (nowa numeracja 3.1–3.9).

### F7 — Kotwice `ci.yml:267,420` bez tekstu do zmiany

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Plan Completeness
- **Location**: Faza 4, pkt 2
- **Detail**: Tylko `ci.yml:489` wspomina deploy-verify.
- **Fix**: Zostawić w fazie 4 tylko `ci.yml:489`.
- **Decision**: FIXED.

## Triage summary

- Fixed: F1 (fix differently), F2 (Fix A), F3, F4, F5, F6, F7 (7)
- Skipped / Accepted / Dismissed: brak

Werdykt po poprawkach: REVISE → SOUND.
