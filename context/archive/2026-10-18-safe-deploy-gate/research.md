---
date: 2026-10-18T12:17:50+0200
researcher: Claude (Opus 5.5) z kwmacioszek
git_commit: c7db08c164acd537c5a5e155800cc15c9a656a52
branch: feature/safe-deploy-gate
repository: szczur-radar
topic: "S-14 safe-deploy-gate: stan bramki wdrożeń (audyt C4–C7) i fakty potrzebne do planu"
tags: [research, ci, railway-iac, deploy-gate, wait-for-ci, cron, preserve]
status: complete
last_updated: 2026-10-18
last_updated_by: Claude (Opus 5.5)
---

# Research: S-14 safe-deploy-gate — bramka wdrożeń (audyt C4–C7)

**Date**: 2026-10-18T12:17:50+0200
**Researcher**: Claude (Opus 5.5) z kwmacioszek
**Git Commit**: c7db08c164acd537c5a5e155800cc15c9a656a52 (= `origin/main` w chwili badania)
**Branch**: feature/safe-deploy-gate
**Repository**: szczur-radar

## Research Question

Slice S-14 z `context/foundation/roadmap.md:223-234`: „merge do `main` nie wdroży commita z czerwonymi testami (IaC stosowane dopiero po testach), crony czekają na CI, po apply i deployu jest automatyczna kontrola zdrowia produkcji, a zmienna ustawiona ręcznie nie blokuje kolejnych wdrożeń; logika bramki ma testy” (audyt C4, C5, C6, C7 w `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:93-96`).

Pytania badawcze:
1. Jak dziś wygląda graf jobów CI i bramka IaC — co odrzuca, co przepuszcza, na czym operuje (C4, C6)?
2. Dlaczego crony nie czekają na CI i co stoi na przeszkodzie (C5)?
3. Które zmienne mogą zostać ustawione ręcznie bez `preserve()` i jakie mechanizmy Railway mogą to rozwiązać (C7)?
4. Co jest dostępne do automatycznej kontroli zdrowia po apply/deployu (C6) i czy automat może wykryć przebieg `RUNNING`?
5. Jakie fakty o Railway (Wait for CI, deploy po `config apply`, format planu) są udokumentowane, a jakie nieznane?

## Summary

- **C4 potwierdzone.** `.github/workflows/ci.yml` (jedyny workflow, 377 linii) ma 7 jobów i ani jednego `needs:`; `railway-config` (`ci.yml:205`) stosuje IaC na produkcji równolegle z `backend`, `web-*`, `api-container-smoke`, `db-backup-test`, `pipeline-trigger-test`. `railway config apply` wywołuje deploy zmienionej usługi (`deployEffect: "deploy"` dla zmian zmiennych i większości pól — źródło CLI), a to, czy taki deploy czeka na Wait for CI, nie jest udokumentowane. Dodanie `needs:` na wszystkie joby testowe usuwa ryzyko niezależnie od tej odpowiedzi i nie łamie zasady „konfiguracja przed kodem”, bo Wait for CI czeka na konkluzję całego workflow, w tym `railway-config`.
- **C5 potwierdzone i zaobserwowane.** `db-backup` (`.railway/railway.ts:223-224`) i `pipeline-trigger` (`:263-264`) nie deklarują `checkSuites`. Na merge'u `5b6eabb` `pipeline-trigger` dostał status „deploying” w chwili pusha (15:02:56), a api/web dopiero po CI (15:06:11) — obserwacja statusów commita na GitHubie. Jedyną przeszkodą jest reguła bramki, która każdą zmianę z tekstem `source` w `summary`/`details` traktuje jako zmianę źródła (`ci.yml:362-366`); `source.checkSuites (null → true)` wpada w nią (`deploy-plan.md:347`). Uzasadnienie „CI does not test tools/db-backup” w `.railway/railway.ts:221-222` i `deploy-plan.md:347` jest nieaktualne (`ci.yml:180-203` ma `db-backup-test` i `pipeline-trigger-test`).
- **C6 potwierdzone.** Po kroku „Apply” (`ci.yml:373-377`) workflow się kończy: nie ma czekania na deploy, `/api/ready` ani sprawdzenia przebiegu. Kontrola po merge'u jest dziś ręczna (`deploy-plan.md:142-145`). Bramka przepuszcza każdą zmianę `severity: "safe"`, w tym te, które restartują Postgresa (incydent 2026-10-12, `deploy-plan.md:384-386`). Reguła źródła to `test()` regexem na polach tekstowych `summary`/`details`, choć plan JSON ma pola strukturalne `kind`, `field`, `severity`, `deployEffect` (źródło CLI `change_set.rs`). Brak fixture'ów planu i testów bramki — bramka jest inline w YAML.
- **Automat nie może dziś wykryć przebiegu `RUNNING`.** Publiczne są tylko `/api/health` (liveness, bez zależności) i `/api/ready` (`SELECT 1`) — `HealthController.java:28-46`, `SecurityConfig.java:102`; reszta `/api/**` wymaga sesji, a `/internal/**` jest tylko w sieci prywatnej i jedyny endpoint bez ID przebiegu (`POST /internal/pipeline-runs/daily`) ma skutki uboczne.
- **C7 potwierdzone i doprecyzowane.** W planie `variable.delete` ma zawsze `severity: "destructive"` (źródło CLI), więc bramka odrzuca je przez warunek `severity == "destructive"` (`ci.yml:354`), nie przez osobną regułę. Na cronach `preserve()` ma tylko `TRIGGER_IN_ANY_ENVIRONMENT` (`.railway/railway.ts:273`); README i deploy-plan zachęcają do ręcznego ustawienia m.in. `BACKUP_IN_ANY_ENVIRONMENT`, `BACKUP_TIMEOUT`, `S3_FORCE_PATH_STYLE`, `TRIGGER_*`, a na API `JAVA_TOOL_OPTIONS` — żadna z nich nie jest zadeklarowana. Railway CLI od **v5.64.0 (2026-10-08)** ma politykę `project(..., { variables: { managed, ignore: [...] } })`, w której ignorowane klucze „are removed from both sides before diffing: never set, never deleted”; CI przypina `@railway/cli@5.62.1` (`ci.yml:240`), a typy SDK `railway` 3.11.0 i 3.16.0 tego pola nie mają.

## Detailed Findings

### 1. Graf jobów CI (C4)

| Job | Linia | `if:` | `needs:` | Co robi |
|---|---|---|---|---|
| `backend` | `ci.yml:12` | — | — | sprawdza zmienne LLM (`:30-43`), `./mvnw -B verify` (`:47-52`) |
| `web-unit` | `:54` | — | — | `npm ci`, `npm test` (`:67-70`) |
| `web-container-smoke` | `:72` | — | — | build obrazu web, `/healthz` i deep route (`:81-99`) |
| `api-container-smoke` | `:104` | — | — | Postgres 18, obraz API, non-root, `migrate`, `/api/health` i `/api/ready` lokalnie (`:140-175`) |
| `db-backup-test` | `:180` | — | — | `tools/db-backup/test.sh` (`:191`) |
| `pipeline-trigger-test` | `:193` | — | — | `tools/pipeline-trigger/test.sh` (`:203`) |
| `railway-config` | `:205` | push albo PR z tego samego repo (`:214-216`) | — | plan → bramka → apply |

- `grep -n "needs:" .github/workflows/ci.yml` nie zwraca nic (sprawdzone na `c7db08c`).
- Jedyny `concurrency:` jest na `railway-config` (`:218-220`): grupa per środowisko, `cancel-in-progress: false`. Dokumentacja Railway wprost ostrzega, by krok wymagany przed deployem nie był w grupie anulującej kolejkę (https://docs.railway.com/deployments/github-autodeploys#wait-for-ci) — obecna konfiguracja tego warunku dotrzymuje.
- Komentarz `ci.yml:208-210` i `deploy-plan.md:942` uzasadniają projekt: „Wait for CI waits for this whole workflow, so the service config … is in place BEFORE Railway deploys the code”. Źródło decyzji: plan-review F1 zmiany `admin-bootstrap-and-login` (`context/archive/2026-09-27-admin-bootstrap-and-login/reviews/plan-review.md:34`, `plan.md:146-148`). Żaden zapis nie rozważa kolejności względem testów — brak `needs:` nie był świadomą decyzją (sprawdzone: archiwum, deploy-plan, historia `ci.yml`).
- Skutki `needs:` (wniosek z grafu, nie test): na PR config trafi do PR env później (po najwolniejszym jobie, prawdopodobnie `backend`), a przy czerwonym teście `railway-config` zostanie `skipped`; konkluzja workflow i tak jest `failure`, więc Wait for CI pomija deploy kodu. Nic w repo nie wymaga wcześniejszego apply w PR env — kroki „Ensure PR-env LLM/Wykop” piszą zmienne z `--skip-deploys` (`ci.yml:264-339`).
- Wyjątek w krokach PR: `railway variable delete` dla `LLM_SERVICE_TIER`/`LLM_REASONING_EFFORT`/`LLM_TIMEOUT` (`ci.yml:302-307`) nie ma `--skip-deploys` (flaga nie istnieje dla delete, `deploy-plan.md:492`), więc prawdopodobnie wyzwala deploy PR env poza bramką. Dotyczy tylko PR env; niezweryfikowane na żywo.

### 2. Bramka IaC (C6)

Kod: `ci.yml:340-377`, inline shell + `jq`, bez osobnego skryptu.

- `:342` `railway config plan --json > plan.json`; `:343` wypisuje `.diff`.
- `:345-352` (JSON): `.ok == true`, `.currentEnvironment.environmentName == $TARGET_ENV`, `.currentEnvironment.projectId == $RAILWAY_PROJECT_ID`, inaczej exit 1.
- `:354` (JSON): odrzuca `severity == "destructive"` lub `kind == "resource.delete"`.
- `:362-366` (tekst w JSON): odrzuca każdą zmianę, która nie jest `resource.create`, a której `summary` lub którykolwiek `details[]` pasuje do `(^|[ ,])source([.]|$| )`.
- `:372` `PENDING_CHANGES` = liczba zmian; `:373-377` apply tylko gdy > 0, `railway config apply --yes`, nigdy `--confirm-destructive`.

Co przepuszcza (na tej ścieżce): każdą zmianę `severity: "safe"` bez słowa `source` w tekście — m.in. `variable.set`, `deploy.*` (np. `limitOverride`, `sleepApplication`, `cronSchedule`), `resource.create`. Bramka nie patrzy na `deployEffect`, więc nie odróżnia zmiany, która restartuje Postgresa, od zmiany bez deployu.

Format planu (źródło CLI `src/iac/change_set.rs` z gałęzi `master` repozytorium `railwayapp/cli`, kopia w scratchpadzie sesji; https://github.com/railwayapp/cli/blob/master/src/iac/change_set.rs):
- `variable.set`: `kind`, `address`, `variable`, `summary`, `details`, `severity: "safe"`, `deployEffect: "deploy"` (`change_set.rs:806-821`).
- `variable.delete`: `severity: "destructive"`, `deployEffect: "deploy"`, `summary: "Delete variable <service>.<KEY>"` (`change_set.rs:832-841`).
- `resource.update`: `kind`, `address`, `field`, `before`, `after`, `path`, `summary`, `severity`, `deployEffect`, opcjonalnie `details` (`change_set.rs:1860-1884`).
- `deployEffect` dla `resource.update` jest `"none"` tylko dla pól `config`, `groupId`, `cdn` i dla samych zmian `tracing.autoInstrumentation`; w pozostałych przypadkach `"deploy"` („Mirrors backboard's `changesThatSkipRedeploy`”, `change_set.rs:1886-1905`).
- `resource.delete`: `severity: "destructive"` (`change_set.rs:330-335`).
- Zastrzeżenie: źródło to bieżący `master`, a CI używa CLI 5.62.1 (2026-09-24). Czy 5.62.1 emituje `field` i `deployEffect`, nie sprawdzono — potrzebna próbka `railway config plan --json` z tej wersji (patrz Open Questions).
- Zmiana `checkSuites` pojawia się jako aktualizacja źródła z detalem `source.checkSuites (null → true)` (`deploy-plan.md:347`); w strukturze CLI to `resource.update` z `field` = `source` (wniosek z `change_set.rs`, niezweryfikowany na próbce).
- CLI 5.67.0 (lokalnie) ma `config plan --out <pinned>` i `config apply --plan <pinned>` („Apply this pinned plan without re-evaluating the authoring file”) oraz `--detailed-exit-code` (`railway config plan --help`). Obecny CI planuje i stosuje w dwóch osobnych wywołaniach (`:342`, `:377`), więc zastosowany plan nie jest przypięty do sprawdzonego. Dostępność `--out`/`--plan` w 5.62.1: niesprawdzona.

Fixture'y i testy: brak. `git ls-files` nie zawiera `plan*.json`; grep `changeSet|resource.delete|config plan --json` trafia tylko w `ci.yml`, komentarze `.railway/railway.ts:6,12,26` i prozę w `context/`. Przykłady zmian istnieją tylko jako proza (`deploy-plan.md:259,347,486,623`).

### 3. Crony bez Wait for CI (C5)

- Deklaracje: api `github(REPO, { branch, checkSuites: true })` (`.railway/railway.ts:102`), web (`:192`); `db-backup` `github(REPO, { branch })` (`:224`), `pipeline-trigger` (`:264`).
- Komentarz `:16-20`: `checkSuites` zadeklarowane na api i web, „so IaC does not reset the operator's toggle (a plan otherwise shows true → null)”.
- Komentarz `:218-222`: Railway zostawia `checkSuites` nieustawione na usłudze utworzonej przez IaC, więc każdy kolejny plan pokazałby `source.checkSuites (null → true)`, a bramka odrzuca to jako zmianę źródła. Historia: CI na `2b21c4a` czerwone (`deploy-plan.md:347`); obejście w `b7ee254` (#44), powtórzone dla `pipeline-trigger` w `13e371a` (#50, `deploy-plan.md:467`).
- Dokumentacja Railway (https://docs.railway.com/deployments/github-autodeploys#wait-for-ci): „new deployments sit in a `WAITING` state until every GitHub Actions check suite on the commit has finished. Railway looks at the conclusion of each workflow run, not at individual jobs”; „A workflow that fails skips the deployment immediately”; po 2 h deploy jest pomijany. Flaga jest per usługa; nic nie wyklucza cronów (brak zapisu specyficznego dla cronów).
- Czy po jednorazowym zastosowaniu `checkSuites: true` na istniejącym cronie diff zniknie (jak na api/web), czy będzie stały (jak `sleepApplication` na Postgresie, `deploy-plan.md:384-386`): nieustalone. Repo pokazuje tylko, że zmiana została odrzucona przed apply.
- `tools/db-backup/README.md:86` sam stwierdza, że CI uruchamia test backupu na każdym PR i pushu do `main` — sprzeczne z komentarzem w `railway.ts:221-222`.

### 4. Zmienne ustawiane ręcznie (C7)

Deklaracje na cronach (`.railway/railway.ts`):
- `db-backup` (`:229-243`): `PG*` i `S3_*` jako referencje, `BACKUP_KEEP_DAILY="14"`, `BACKUP_KEEP_MONTHLY="12"` jako literały, **brak** `preserve()`.
- `pipeline-trigger` (`:269-274`): `API_PRIVATE_DOMAIN`, `PIPELINE_TRIGGER_TOKEN` (referencje), `API_PORT` (literał), `TRIGGER_IN_ANY_ENVIRONMENT: preserve()`.

Zmienne czytane przez skrypty, niezadeklarowane w `railway.ts`, a dokumentacja zachęca lub dopuszcza ich ręczne ustawienie:

| Zmienna | Usługa | Czytana | Domyślnie | Dokumentacja |
|---|---|---|---|---|
| `BACKUP_IN_ANY_ENVIRONMENT` | db-backup | `tools/db-backup/backup.sh:31` | brak | „set on the service”: `tools/db-backup/README.md:21,59`, `deploy-plan.md:281`, komentarz `.railway/railway.ts:211-212` |
| `BACKUP_TIMEOUT` | db-backup | `backup.sh:41,43` | `2h` | strojenie: `README.md:58`, `deploy-plan.md:281` |
| `S3_FORCE_PATH_STYLE` | db-backup | `backup.sh:73` | `false` | warunkowo: `README.md:56` |
| `TRIGGER_TIMEOUT`, `TRIGGER_WAKE_TIMEOUT`, `TRIGGER_BUSY_TIMEOUT`, `TRIGGER_BUSY_INTERVAL`, `TRIGGER_POLL_INTERVAL`, `TRIGGER_RUN_TIMEOUT`, `TRIGGER_REQUEST_TIMEOUT` | pipeline-trigger | `tools/pipeline-trigger/trigger.sh:28-30,46,61-66` | `3h`, `3m`, `60m`, `60s`, `30s`, `90m`, `60s` | strojenie: `tools/pipeline-trigger/README.md:79-85` |
| `TRIGGER_HEARTBEAT_INTERVAL` | pipeline-trigger | `trigger.sh:30,67` | `5m` | ręcznie tylko do weryfikacji, usunąć przed pushem: `deploy-plan.md:737`; ustawiona w PR env i usunięta (`:788`, `:797`) |
| `JAVA_TOOL_OPTIONS` | api | JVM | — | „extra JVM flags”: `deploy-plan.md:50`, `szczur-radar-api/README.md:220`, `REPOSITORY_GUIDELINES.md` (Build) |

- `BACKUP_KEEP_*` są literałami, więc ręczna zmiana w panelu zostanie **nadpisana** przy następnym apply (`variable.set`, `safe`), nie zablokuje bramki.
- `tools/pipeline-trigger/README.md:90` zawiera ostrzeżenie o `preserve()`; `tools/db-backup/README.md` go nie ma (grep `railway.ts|preserve`: 0 trafień), mimo że zachęca do `BACKUP_IN_ANY_ENVIRONMENT`.
- API: wszystkie zmienne czytane przez aplikację wymienione w `REPOSITORY_GUIDELINES.md` (LLM_*, PIPELINE_*, WYKOP_*, WEB_PUBLIC_URL, ACTIVATION_LINK_TTL, PIPELINE_TRIGGER_TOKEN) są zadeklarowane (`.railway/railway.ts:119-184`). Poza `JAVA_TOOL_OPTIONS` niezadeklarowane jest `COOKIE_SECURE` (`SessionConfig.java:35`), opisane tylko dla uruchomień lokalnych. Web: brak luk (`Caddyfile:11,30`).
- Brak testu ani kroku CI porównującego `railway.ts` ze zmiennymi czytanymi przez skrypty (sprawdzone: `ci.yml`, `tools/*/test.sh`, `git grep railway.ts` w `*.sh|*.yml|*.java|*.ts`).

Mechanizmy Railway:
- `preserve()` działa per klucz (`node_modules/railway/dist/index-*.d.ts:403` w SDK 3.11.0, czytane z głównego checkoutu; worktree nie ma `node_modules`). Personel Railway we wrześniu 2026: „There is no unmanaged escape hatch today… `preserve()` per variable is the only mechanism” (https://station.railway.com/questions/iac-no-way-to-leave-environment-variabl-2485d6db).
- CLI **v5.64.0** (2026-10-08, PR https://github.com/railwayapp/cli/pull/1256) dodał politykę projektu `variables: { managed: boolean, ignore: string[] }`; wzorce `KEY_GLOB` (każda usługa) lub `service/KEY_GLOB` (jedna usługa), jedynym znakiem specjalnym jest `*`; `managed: false` = żadnych `variable.set`/`variable.delete`; plan `--json` dostaje pole `variablePolicy`. Lokalne CLI 5.67.0 zawiera `variablePolicy` i `VariablePolicyReport` w binarce (`strings`), a `change_set.rs:12,420-443` je obsługuje.
- SDK: `ProjectDefinition` w 3.11.0 (`d.ts:248-261`) i 3.16.0 nie ma pola `variables`; `project()` w runtime przekazuje resztę definicji dalej, więc użycie wymaga rzutowania typu (raport agenta na podstawie `dist/index.js`; niezweryfikowane uruchomieniem).
- Komenda `railway config partials list|release|transfer` (CLI 5.67) działa per adres zasobu, nie per zmienna — nie rozwiązuje C7 (help CLI).

### 5. Kontrola zdrowia po apply/deployu (C6)

Co jest dziś:
- W CI tylko lokalny `api-container-smoke` (`ci.yml:163-175`); nic po `Apply` (`:373-377`).
- Ręczna lista po merge'u: CI zielone i diff `railway-config` tylko z oczekiwanymi zmianami, log buildu, pre-deploy `migrate` exit 0, healthcheck, `/api/ready` 200 na publicznym adresie, logowanie admina (`deploy-plan.md:142-145`; ten sam wzór `:86,207,332,629,829-830`).
- Healthcheck Railway: api `/api/health` (`.railway/railway.ts:115`), web `/healthz` (`:195`). Dokumentacja: nowy deploy staje się aktywny dopiero po zdanym healthchecku, przy porażce deploy jest `failed`, a stary obsługuje ruch; healthcheck działa tylko w chwili deployu (https://docs.railway.com/deployments/healthchecks). `/api/health` nie sprawdza bazy (`HealthController.java:28-31`), więc healthcheck API nie wykryje zawieszonego Postgresa — wykryje to dopiero `/api/ready`.

Endpointy dostępne z GitHub Actions:
- `GET /api/health` → `{"status":"ok"}`; `GET /api/ready` → 200 `ready` albo 503 `unavailable` po `SELECT 1`, wyjątek połknięty bez logu (`HealthController.java:37-46`, audyt C10). Oba publiczne (`SecurityConfig.java:102`; `AuthorizationCompletenessIT.java:51-53`, „reveals only up/down”). Ruch przez Caddy weba (`Caddyfile:29-34`), z 30 s ponowień, bo API usypia; sonda budzi API i mierzy zimny start.
- Pozostałe `/api/**` wymagają sesji (`SecurityConfig.java:106-107`), w tym `/api/freshness` i `/api/admin/pipeline-runs`.
- `/internal/**`: własny łańcuch z tokenem (`SecurityConfig.java:75`), osiągalny tylko z sieci prywatnej Railway. `POST /internal/pipeline-runs/daily` uruchamia przebiegi (409 `pipeline_busy`, gdy jakiś jest w kolejce lub trwa, `InternalPipelineRunController.java:65`) — ma skutki uboczne; `GET /internal/pipeline-runs/{id}` wymaga ID (`:77`).
- Wniosek: dziś żaden endpoint nie pozwala automatowi spoza sieci prywatnej sprawdzić, czy przebieg jest `RUNNING`.

Narzędzia Railway do sprawdzania deployu z CI:
- `railway deployment list --json [--limit N] -s <service> -e <env>`: statusy `SUCCESS, FAILED, CRASHED, BUILDING, DEPLOYING, INITIALIZING, WAITING, QUEUED, REMOVED, REMOVING`; `meta.commitHash` niesie commit (https://docs.railway.com/cli/deployment; `deploy-plan.md:226`). Brak komendy „wait” — trzeba odpytywać.
- `railway status --json` (https://docs.railway.com/cli/status).
- GitHub (obserwacja na tym repo, `railway-app[bot]`): jeden GitHub Deployment na środowisko na commit, nazwa środowiska `szczur-radar / production` (nie `production`); statusy commita per usługa, kontekst `szczur-radar - <service>`, opisy m.in. „Railway is deploying the service”, „Success”, „Deployment failed”, „No deployment needed - watched paths not modified”. Podczas `WAITING` nie widziano statusu; przypadek „pominięty, bo CI czerwone” nie był obserwowany. Późniejsze redeploye dopisują statusy do tego samego SHA, więc oczekujący musi filtrować po czasie.
- Kolejność zdarzeń na `main` (wniosek z dokumentacji Wait for CI): autodeploy kodu startuje dopiero po zakończeniu **całego** workflow, więc krok w tym samym `ci.yml` nie może zaczekać na autodeploy tego samego commita. Może zaczekać na deploy wywołany własnym apply (który startuje w trakcie joba), ale nie na deploy kodu. Kontrola deployu kodu wymaga osobnego wyzwalacza (np. `on: deployment_status`, `workflow_run` albo harmonogram). Czy workflow wyzwolony przez `deployment_status` dla tego samego SHA wpływa na Wait for CI kolejnych redeployów tego commita: nieustalone.

### 6. Incydenty, które bramka miała lub ma powstrzymać

| Data | Zdarzenie | Źródło |
|---|---|---|
| 2026-09-28 | PR #10: apply skasowałby bucket `Postgres-PITR` i wyłączył uśpienie (dryf) → reguła „plan = 0 przed pierwszym apply” | `lessons.md:33-38` |
| 2026-10-02 | Dwa deploye API z `7a406f2`, oba `migrate` w tej samej sekundzie (lock Flyway) | `deploy-plan.md:70` |
| 2026-10-11 | PR-44: `source.checkSuites (null → true)` odrzucone, CI czerwone → crony bez `checkSuites` | `deploy-plan.md:347` |
| 2026-10-12 | Ponowny `railway-config` po `b7ee254` zastosował stały diff `sleepApplication (null → false)` (`safe`); Postgres zawisł na „Mounting volume” 9 min, `/api/ready` 503, ręczny `railway redeploy` | `deploy-plan.md:384-386`; `lessons.md:99-104` |
| 2026-10-13 | Merge samej dokumentacji #55 przedeployował wszystkie usługi (zgubione `watchPatterns`), 2 przebiegi `interrupted`; naprawa #58 | `deploy-plan.md:94,509-512`; `lessons.md:106-111` |

## Code References

- `.github/workflows/ci.yml:6-9` — wyzwalacze bez path-filter (wymóg Wait for CI)
- `.github/workflows/ci.yml:12,54,72,104,180,193,205` — siedem jobów, żaden z `needs:`
- `.github/workflows/ci.yml:218-220` — `concurrency` tylko na `railway-config`
- `.github/workflows/ci.yml:240` — `@railway/cli@5.62.1`
- `.github/workflows/ci.yml:264-339` — kroki PR env (LLM, Wykop), `variable delete` bez `--skip-deploys` `:302-307`
- `.github/workflows/ci.yml:340-372` — „Plan and gate”: env check `:345-352`, destructive/delete `:354`, regex `source` `:362-366`
- `.github/workflows/ci.yml:373-377` — apply bez `--confirm-destructive`, koniec workflow
- `.railway/railway.ts:16-20` — `checkSuites` na api/web i dlaczego
- `.railway/railway.ts:102,192` — api i web z `checkSuites: true`
- `.railway/railway.ts:218-222,224,262,264` — crony bez `checkSuites`, nieaktualne uzasadnienie
- `.railway/railway.ts:229-243,269-274` — zmienne cronów
- `szczur-radar-api/src/main/java/pl/szczurradar/api/health/HealthController.java:28-46` — `/api/health`, `/api/ready`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/SecurityConfig.java:75,102,106-107` — łańcuch `/internal`, publiczne health/ready, reszta z sesją
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunController.java:65,77` — jedyne endpointy `/internal`
- `szczur-radar-web/Caddyfile:17-20,29-34` — `/healthz`, proxy `/api/*`
- `tools/db-backup/backup.sh:31,41,73` — `BACKUP_IN_ANY_ENVIRONMENT`, `BACKUP_TIMEOUT`, `S3_FORCE_PATH_STYLE`
- `tools/pipeline-trigger/trigger.sh:22,28-30,61-67` — `TRIGGER_*`
- `tools/pipeline-trigger/README.md:90` — reguła `preserve()` dla cronów (tylko tu)
- `context/deployment/deploy-plan.md:93,142-145,347,384-386,942` — dwa deploye po merge'u, ręczna kontrola, `checkSuites` na cronach, incydent Postgresa, specyfikacja bramki

## Architecture Insights

- **Bramka sprawdza tylko plan przed apply, nigdy skutek po nim.** Ten jeden powód stoi za C4, C6 i C7: kolejność względem testów nie jest wymuszona, nikt nie sprawdza skutku, a każda luka w deklaracjach zmiennych objawia się jako czerwone CI na `main`.
- **Plan JSON jest bogatszy, niż bramka z niego korzysta.** Pola `kind`, `field`, `severity`, `deployEffect` pozwalają zastąpić regex na tekście regułami strukturalnymi i odróżnić zmianę, która restartuje usługę, od zmiany bez deployu (z zastrzeżeniem wersji CLI). Dopasowanie po polach daje się też testować na fixture'ach JSON bez Railway.
- **„Konfiguracja przed kodem” i „testy przed konfiguracją” są zgodne.** Wait for CI czeka na konkluzję całego workflow, więc `needs:` przesuwa apply po testach, a deploy kodu nadal następuje po apply.
- **Jedna reguła bramki wywołuje kaskadę.** Reguła „żadnej zmiany `source`” wymusiła wyjątek dla cronów (brak `checkSuites`), który tworzy lukę C5. Przy `checkSuites` reguła jest za szeroka: włączenie Wait for CI (`null → true`) zaostrza ochronę, a nie przepina źródła.
- **Brak publicznego sygnału o trwającym przebiegu.** Każda automatyczna ochrona przed deployem w trakcie `RUNNING` wymaga albo nowego, publicznie osiągalnego sygnału (nowa trasa: `PUBLIC_ROUTES` z uzasadnieniem albo token), albo rezygnacji z tego warunku na rzecz kontroli po fakcie.

## Historical Context (from prior changes)

- `context/archive/2026-09-27-admin-bootstrap-and-login/reviews/plan-review.md:34`, `plan.md:146-148`: narodziny `railway-config` i bramki. Teza „Wait for CI gwarantuje konfigurację przed kodem” jest prawdziwa dla deployu kodu; nie odnosi się do testów, więc C4 nie jest jej sprzecznością, tylko luką.
- `9b9fbf8` (#13): pierwsza wersja bramki (destructive / `resource.delete` / regex `source`).
- `context/archive/2026-10-11-production-data-gate/plan.md:30,43,58,304-312` i `b7ee254` (#44): wyjątek `resource.create` z reguły źródła i crony bez `checkSuites`. Ten sam przegląd (`reviews/impl-review.md:70-90`) dodał `db-backup-test` i zanotował „`db-backup` also deploys without Wait for CI” (`:80`). Werdykt dla komentarza w `railway.ts:221-222`: od tego przeglądu nieaktualny w części „CI does not test”; w części o odrzuceniu `checkSuites` aktualny.
- `context/archive/2026-10-06-first-assessed-wykop-feed/reviews/impl-review.md:88-96`: brak `preserve()` = czerwona bramka i zablokowany deploy; naprawa dopisywała zmienne pojedynczo (wzorzec, który daje C7).
- `context/archive/2026-10-06-first-assessed-wykop-feed/follow-ups/review-fixes.md:76-81`: PR env wdrożył web ze starego commita, podejrzenie „Wait for CI picking the previous commit's check suite?”. Werdykt: nierozstrzygnięte (`deploy-plan.md:224-226` opisuje tylko obejście).
- `context/foundation/lessons.md:33-38` (token workspace, plan = 0, blokuj destructive), `:99-104` (sprawdzaj w planie, które usługi zrestartuje), `:106-111` (sprawdzaj żywą konfigurację, nie pusty plan). Wszystkie trzy obowiązują i zawężają projekt: S-14 nie powinna dodawać `--confirm-destructive`. Każde nowe pole w `railway.ts` (np. `checkSuites` na cronach, polityka `variables`) wymaga po apply sprawdzenia żywej konfiguracji.

## Related Research

- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` — audyt C1–C15, źródło slice'u; `:240` („Bramka IaC chroni przed zniszczeniem, nie przed restartem ani przed zablokowaniem pracy”).
- `context/map/repo-map.md` — strefy ryzyka (platform / IaC / deploy w `context/map/evidence/1-history.md:91`).

## Open Questions

Wymagają żywego Railway albo próbki z CLI (nie da się ich rozstrzygnąć w repo):
1. **Czy deploy wywołany `railway config apply` czeka na Wait for CI** (audyt C4)? Brak dokumentacji; pośrednie obserwacje (`deploy-plan.md:32-33,70,624-626`) tego nie dowodzą. Po dodaniu `needs:` odpowiedź przestaje blokować C4, ale nadal ma znaczenie dla deployu, który apply wyzwala przed końcem workflow.
2. **Czy CLI 5.62.1 emituje `field` i `deployEffect`** oraz czy ma `--out`/`--plan`? Rozstrzyga to, czy reguły strukturalne i przypięty plan wymagają podbicia CLI. Polityka `variables` (C7) wymaga CLI ≥ 5.64.0 niezależnie od tej odpowiedzi.
3. **Dokładny JSON zmiany `checkSuites`** i to, czy po jednym apply `checkSuites: true` na cronie diff znika. Potrzebna próbka `railway config plan --json` (np. w PR env S-14) — także jako fixture testów bramki.
4. **Czy crony z `checkSuites: true` faktycznie czekają na CI.** Dokumentacja nie wyklucza cronów, ale tego nie potwierdza.
5. **Czy produkcja ma dziś ręcznie ustawione zmienne cronów** (np. pozostałe `TRIGGER_*`/`BACKUP_*`)? `deploy-plan.md:797` zapisuje usunięcie w PR env; stanu produkcji nie sprawdzano. Przed zmianą polityki zmiennych trzeba to sprawdzić planem (`--detailed-exit-code`).
6. **Jak Railway oznacza w GitHubie deploy pominięty przez czerwone CI** i czy deploye wywołane apply publikują statusy o tym samym znaczeniu — ważne, jeśli kontrola po deployu ma czekać na statusy GitHuba zamiast odpytywać `railway deployment list`.

Wybory dla `/10x-plan` (decyzje produktowe lub projektowe, nie luki w dowodach):
- **C6 / `RUNNING`:** czy kontrola po deployu ma tylko wykrywać i alarmować (`/api/ready`, status deployu), czy także blokować apply w trakcie przebiegu. Blokowanie wymaga nowego sygnału z API: publicznego (w `PUBLIC_ROUTES` z uzasadnieniem) albo chronionego tokenem.
- **C6 / restart:** czy bramka ma odrzucać, czy tylko ogłaszać zmiany z `deployEffect: "deploy"` na Postgresie i API. Odrzucenie zatrzymuje także zamierzone zmiany.
- **C7:** `variables.ignore` (CLI ≥ 5.64.0, typ SDK przez rzutowanie) czy rozszerzenie listy `preserve()` (działa na obecnym CLI, ale zostaje zależne od dyscypliny).
- **Gdzie żyje kontrola po deployu kodu:** osobny workflow (`deployment_status` / `workflow_run`) czy tylko kontrola po własnym apply w `railway-config`.
