<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-14: Bezpieczna bramka wdrożeń

- **Plan**: context/changes/safe-deploy-gate/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5, 6
- **Date**: 2026-10-18
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Kontrole Automated (2026-10-18, HEAD `ddf0687`):
- `tools/deploy-gate/test.sh`: „All checks passed.”
- `grep -n "needs:" .github/workflows/ci.yml`: `234: needs:`
- `cd szczur-radar-api && ./mvnw -B verify`: exit 0, 767 testów, 0 błędów. Lokalnie potrzebne są ustawienia `LLM_*`; użyto modelu z CI i atrapy klucza.
- Grep `deploy-gate|deploy-safety|deploy-verify` w `REPOSITORY_GUIDELINES.md` i `deploy-plan.md`: 26 trafień.
- CI dla `7e123c9` (run 39140400096) i `ddf0687` (run 39140426767): zielone. Wiersz 4.2 potwierdza run 39139999132.

Wiersze Manual: wszystkie nieodhaczone (1.5–1.7, 2.4–2.7, 3.3–3.4, 4.3–4.5, 5.3–5.5, 6.3), więc żaden nie jest odhaczony bez dowodu.

## Findings

### F1 — Snapshot przed apply po cichu przechodzi przy awarii CLI

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: tools/deploy-gate/verify-apply.sh:107
- **Detail**: `latest` zwraca 1 w dwóch przypadkach: gdy usługa jeszcze nie istnieje i gdy CLI zawodzi z innego powodu (auth, sieć, wyjście, które nie jest JSON-em). Snapshot nie rozróżnia tych przypadków. Po 3 nieudanych próbach zapisuje `null` z ostrzeżeniem „a service the apply creates?”. Wtedy `check` uznaje istniejący deploy sprzed apply za nowy (`before=""`), widzi `SUCCESS` i przechodzi, nie czekając na deploy wywołany przez apply. To fałszywie zielona kontrola, czyli dokładnie przypadek z incydentu 2026-10-12, który kontrola miała łapać.
- **Fix A ⭐ Recommended**: `gate.jq` wypisuje osobną listę zasobów z `resource.create` (np. `GATE_CREATES_FILE`); snapshot toleruje brak deployu tylko dla nich, a dla każdej innej usługi 3 nieudane próby kończą się exit 1 i apply jest pomijany.
  - Strength: rozróżnia oba przypadki u źródła, bo bramka i tak zna rodzaj każdej zmiany.
  - Tradeoff: nowy kontrakt między `gate.sh` a `verify-apply.sh`, przypadki w `test.sh` i aktualizacja README.
  - Confidence: HIGH — `names_resource` już rozpoznaje `Create <type> <name>`.
  - Blind spot: nie sprawdzono, co zwraca `railway deployment list` dla usługi tworzonej w tym samym apply.
- **Fix B**: snapshot kończy się exit 1 po 3 nieudanych próbach dla każdej usługi, a pusta lista (`none`) zostaje jedynym przypadkiem `null`.
  - Strength: najprostsza zmiana, zamyka fail-open.
  - Tradeoff: plan tworzący nową usługę zatrzyma się na snapshocie, jeśli CLI zwraca błąd dla nieistniejącej usługi. Wtedy potrzebny jest ręczny apply.
  - Confidence: MED — zależy od zachowania CLI dla nieistniejącej usługi.
  - Blind spot: jak wyżej.
- **Decision**: FIXED (Fix B). Snapshot po 3 nieudanych odczytach kończy się exit 1, więc apply się nie wykonuje; `null` tylko dla usługi bez deployów. Zmienione: `verify-apply.sh`, test `snapshot-unknown` (break-check: czerwony na starym kodzie), README.

### F2 — Wyścig dwóch zapytań w `/api/deploy-safety`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/DeploySafetyController.java:44
- **Detail**: `existsRunning()` i `existsQueued()` to dwa osobne zapytania bez wspólnego snapshotu. Jeśli przebieg przejdzie z QUEUED w RUNNING między nimi, pierwsze nie widzi `running`, a drugie nie widzi `queued`. Endpoint zwraca wtedy `{"safe":true}`, choć przebieg trwa. Okno jest wąskie, ale strażnik pyta co 60 s przez całe czekanie.
- **Fix**: Bit `safe` rozstrzyga jednym zapytaniem `existsActive()` (`PipelineRunRepository.java:19`, QUEUED+RUNNING); dopiero gdy jest aktywny, `existsRunning()` wybiera powód (a gdy w międzyczasie przebieg się skończył, zostaje `pipeline_queued`, nigdy `safe:true`).
- **Decision**: FIXED. Bit `safe` pochodzi z `existsActive()`, powód z `existsRunning()`. Usunięte nieużywane `existsQueued()`, poprawiony `pipeline/CLAUDE.md`. Nowy `DeploySafetyControllerTest` (break-check: czerwony na starej logice); `DeploySafetyIT` 10/10 i `AuthorizationCompletenessIT` zielone.

### F3 — Bramka przepuszcza `domain.create` na dowolnej usłudze

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/deploy-gate/gate.jq:17
- **Detail**: `domain.create` jest na liście dozwolonych bez ograniczenia celu. Nieprzypilnowana zmiana w `railway.ts` (domena na `szczur-radar-api`) przeszłaby bramkę i apply, a to łamie twardą regułę „`szczur-radar-api` must not have its own public domain” (REPOSITORY_GUIDELINES, Deployment). Dziś chroni przed tym tylko przegląd `railway.ts`.
- **Fix**: Dopuścić `domain.create` tylko z celem `szczur-radar-web` (powód odrzucenia „public domain only on szczur-radar-web”) i dodać fixture domeny na API, który bramka odrzuca.
- **Decision**: FIXED. `public_service` w `gate.jq`: `domain.create` przechodzi tylko dla `szczur-radar-web`, cel ustalany zawsze, także przy `deployEffect: none`. Nowy fixture `domain-create-api.json` (break-check: czerwony bez reguły), README.

### F4 — `verify-deploy.sh` kończy się exit 2 przy złym wywołaniu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: tools/deploy-gate/verify-deploy.sh:43
- **Detail**: Plan (Phase 5 §1) mówi „Exit ≠ 0 tylko przy błędzie samego `gh`”. Skrypt kończy się też exit 2 przy złej zmiennej (`DEPLOY_STATE`, `DEPLOY_SHA`, `GITHUB_REPOSITORY`, `GH_TOKEN`, zły czas). Z `deploy-verify.yml` jest to nieosiągalne, bo `if:` filtruje stan, a zdarzenie niesie pełny SHA. Gdyby jednak do tego doszło, czerwony run zablokowałby przez Wait for CI redeploy tego commita.
- **Fix**: Dopisać to odstępstwo w `tools/deploy-gate/README.md` (exit 2 tylko przy błędzie wywołania, nieosiągalny z workflow) albo w Plan Amendments; kodu nie zmieniać.
- **Decision**: FIXED. Kod `2` był już w tabeli kodów README; dopisane, że z workflow jest nieosiągalny, co łapie i że to odstępstwo od planu. Kod bez zmian.

### F5 — Zapas terminu strażnika nie uwzględnia `/api/ready` po apply

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:399
- **Detail**: Termin to `created_at + 120m − DEPLOY_VERIFY_TIMEOUT (15m) − 5m`. Po nim mieszczą się jeszcze plan, snapshot, apply, kontrola deployów (≤15m) i `/api/ready` (≤ `DEPLOY_GUARD_WAKE_TIMEOUT`, 3m). W najgorszym razie z 5 min zapasu zostaje ~1–2 min. Tak stanowi plan, więc to uwaga do planu, nie odstępstwo.
- **Fix**: Odjąć w terminie także `DEPLOY_GUARD_WAKE_TIMEOUT`.
- **Decision**: FIXED. Termin = `created_at` + 120 min − `DEPLOY_VERIFY_TIMEOUT` − `DEPLOY_GUARD_WAKE_TIMEOUT` − 5 min (97 min), w `ci.yml`; opis w `wait-safe.sh`, README i `deploy-plan.md`.

### F6 — Po nieudanym apply nie ma kontroli `/api/ready`

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:465
- **Detail**: `if: steps.apply.outcome == 'success'` pomija kontrolę po apply, który padł w połowie i mógł już zrestartować Postgresa. Job i tak jest czerwony, ale log nie mówi, czy produkcja żyje.
- **Fix**: `if: always() && steps.apply.outcome == 'failure'` dla osobnego kroku tylko z `/api/ready` (albo zostawić i opisać w runbooku S-14).
- **Decision**: FIXED. Nowy krok „API after a failed apply” (`if: always() && steps.apply.outcome == 'failure'`): `verify-apply.sh check` z pustą listą, więc tylko `/api/ready`. Opis w README i `deploy-plan.md`.

### F7 — Wykrywanie zmiany źródła w `details` tylko po `source.`

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: tools/deploy-gate/gate.jq:31
- **Detail**: `startswith("source.")` nie złapie detalu o całym obiekcie (`source (null → {...})`). Wyłapałby go regex na `summary`, ale nie wtedy, gdy ścieżka chowa się za „and N more”. Takiego formatu nie ma w znanych formatach CLI ani w fixture'ach, więc luka jest teoretyczna.
- **Fix**: Zmienić warunek na `test("^source([. (]|$)")` i dodać fixture.
- **Decision**: FIXED. `test("^source([. (]|$)")` w `gate.jq`; nowy fixture `source-whole-object.json` (break-check: czerwony na `startswith`), README.

### F8 — Wiersz Manual 1.5 jest nieaktualny po Plan Amendments

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/safe-deploy-gate/plan.md:695
- **Detail**: 1.5 wymaga, żeby plan z CLI 5.67.1 zawierał `field` i `deployEffect`. Prawdziwy plan ich nie ma (run 39124555511), a wariant A zastąpił te reguły. Druga część wiersza (fixture z prawdziwego planu: `pr-env-checksuites.json`, `pr-env-empty.json`) jest spełniona.
- **Fix**: Przy odhaczaniu wierszy Manual zamknąć 1.5 z dopiskiem „pola nieobecne — zastąpione Plan Amendments 2026-10-18; fixture dodany” (tytułu wiersza nie zmieniać).
- **Decision**: SKIPPED. Wiersz Manual zamyka człowiek przy odhaczaniu, z dopiskiem „zastąpione Plan Amendments; fixture dodany”.

### F9 — `railway variable delete` w PR env przed strażnikiem i bez `--skip-deploys`

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:351
- **Detail**: Istniejący krok „Ensure PR-env LLM settings” (sprzed S-14) usuwa skopiowane zmienne `LLM_*` bez `--skip-deploys` i przed strażnikiem. W PR env może to zrestartować API w trakcie przebiegu, a strażnik tego nie zatrzyma. Dotyczy tylko PR env i tylko sytuacji, gdy skopiowano wartości z produkcji. Zmiana jest poza zakresem S-14.
- **Fix**: Dopisać `--skip-deploys` (deploy i tak nastąpi po workflow) albo zapisać jako follow-up.
- **Decision**: SKIPPED (niewykonalne w tej formie). `railway variable delete` w CLI 5.67.x nie ma `--skip-deploys` (flaga jest tylko w `variable` i `variable set`), więc dopisanie jej wywróciłoby krok. Follow-up poza S-14: przenieść usuwanie za strażnika albo usuwać przez `railway variable --skip-deploys` / plan IaC.
