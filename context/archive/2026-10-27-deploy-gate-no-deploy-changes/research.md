---
date: 2026-10-28T06:18:46+0000
researcher: kwmacioszek (Claude Opus 5.5)
git_commit: e5ab295
branch: feature/deploy-gate-no-deploy-changes
repository: kwmacioszek/szczur-radar
topic: "S-21: jak kontrola po apply ma rozpoznać deployment swojego apply'a, zmianę bez deployu i deploy, który naprawdę padł — uzupełnienie audytu post-m2-audit (P1–P3)"
tags: [research, deploy-gate, verify-apply, railway, iac, wait-for-ci, s-14, s-21]
status: complete
last_updated: 2026-10-28
last_updated_by: kwmacioszek (Claude Opus 5.5)
---

# Research: S-21 — kontrola po apply a zmiany IaC bez deployu

**Date**: 2026-10-28T06:18:46+0000
**Researcher**: kwmacioszek (Claude Opus 5.5)
**Git Commit**: e5ab295 (`main` po otwarciu M-3; kod bramki bez zmian od `9ef690b`)
**Branch**: feature/deploy-gate-no-deploy-changes
**Repository**: kwmacioszek/szczur-radar

## Research Question

Wycinek S-21 (`context/foundation/roadmap.md`, „S-21: Zmiana IaC bez deployu nie blokuje wdrożeń”, issue #115): kontrola po apply w `railway-config` ma przechodzić, gdy zastosowana zmiana nie wywołuje deployu (np. `source.checkSuites`) albo gdy w trakcie pojawi się deployment z innego pusha, i nie przepuszczać deployu, który naprawdę się nie udał; runbook S-14 i issue #73 mają mówić prawdę o `2abc64c`.

Ten dokument **uzupełnia** audyt `context/changes/post-m2-audit/research.md` (sekcja 3: oś czasu `2abc64c`, P1, P2, P3, reguły-kandydaci R1–R6) i go nie powtarza. Pytania tej sesji:

1. Czym deployment wywołany przez apply różni się w `meta` od pushowego (Unknown S-21) i czy taki redeploy czeka na Wait for CI.
2. Czy da się z góry wiedzieć, że zmiana nie deployuje (źródło `deployEffect`).
3. Co kontrola po apply naprawdę weryfikuje dziś, per usługa.
4. Gdzie leży kod, testy i okablowanie CI, które zmiana dotknie.
5. Które zdania w dokumentach i issue są fałszywe.

Metoda: kod bramki przeczytany w całości przez głównego agenta (`verify-apply.sh`, `gate.jq`, `gate.sh`, `lib.sh`); trzech pracowników równolegle (żywy Railway tylko do odczytu — CLI 5.71.0 `railway deployment list --json` i Railway MCP; testy i `ci.yml`; historia — dokończona przez głównego agenta po limicie). Zrzuty deploymentów produkcji: 298 wpisów z pięciu usług, `createdAt` 2026-09-26…2026-10-27 (scratchpad sesji, `dep-<usługa>.json`, poza repo). Liczby i rozstrzygające `meta` policzone przez głównego agenta `jq` na tych zrzutach. Znaczniki: `[O]` obserwacja, `[I]` wnioskowanie, `[U]` nieznane.

## Summary

- **Deployment apply'a da się rozpoznać, ale nie po `reason` ani `commitHash`.** W 298 pobranych deploymentach produkcji 31 ma `meta.patchId` z przedrostkiem `iac-change-set/<envId>/` [O]; to deploymenty wywołane przez `railway config apply` (czasy pokrywają się z krokami apply runów CI, np. 2026-10-12 11:38:14 restart Postgresa `be5af616`, 2026-10-13 13:23:17 API `0fd012bb`, 2026-10-15 17:33:56 cztery usługi). Mają `reason: "deploy"` i `commitHash` jak pushowe; `commitHash` to czubek gałęzi w chwili apply'a, nie commit runu: apply runu 38778345828 (`427ebc3`, run utworzony 17:33:34Z) dał 2026-10-15 17:33:56 deploymenty z `commitHash` `eee1eda` — commita z 17:33:40, 9 s po `427ebc3` [O]. Reguła R2 audytu w wersji „`reason`/`commitHash`” nie działa; działa w wersji „`patchId` z `iac-change-set/`” [I]. Railway MCP `list-deployments` obcina `meta` i `patchId` nie pokazuje; CLI `railway deployment list --json` pokazuje [O].
- **Deployment apply'a nie bywa `SKIPPED` przez watch paths.** Wszystkie 23 deploymenty IaC usług z GitHuba mają `meta.ignoreWatchPatterns: true`; żaden z 31 deploymentów IaC nie jest `SKIPPED` (28 `REMOVED`, 3 `FAILED`) [O]. Wszystkie 101 `SKIPPED` z `skippedReason: "No changes to watched files"` nie mają klucza `reason` — to pushe [O]. `SKIPPED` z `skippedReason: "CI check suite failed"` (3, w tym API `981111be` z `2abc64c`) powstaje dopiero po konkluzji workflow [O, audyt], czyli po kontroli.
- **Redeploy z apply'a na usłudze z Wait for CI czeka na CI.** Bezpośrednio: run 39140400096 (PR env `szczur-radar-pr-81`, 2026-10-18, zmiana `deploy.cronSchedule` na `pipeline-trigger` z `checkSuites: true`) — 1 s po „Applied pinned Railway configuration.” kontrola zobaczyła nowy deployment `8562a9d2` w `WAITING` i przekazała go dalej [O, log runu]. Pośrednio na produkcji: build `0fd012bb` (API, 2026-10-13) ruszył 18 s po konkluzji swojego workflow, a deployment IaC `b132b91c` crona bez `checkSuites` zbudował się ~2 min przed konkluzją [I, czasy z logów buildów, pracownik]. To rozstrzyga pierwszą połowę Unknown S-21 i Open Roadmap Question 2 (dla apply'a; dla `railway redeploy` czerwonego commita `deploy-plan.md:967` zostaje „Not verified yet”).
- **Zmiany bez deployu nie da się dziś przewidzieć z planu.** `railway config plan --json` nie daje `deployEffect` w żadnej wersji CLI: `struct Change { summary, severity, kind, details }` jest ta sama w 5.67.1 (przypięta w `ci.yml:317`) i 5.71.0 (najnowsza na npm) [O]. CLI liczy `deployEffect` wewnętrznie (`deploy_effect_for_update`, „Mirrors backboard's `changesThatSkipRedeploy`”) i daje `"none"` tylko dla `config`, `groupId`, `cdn` i samego `tracing.autoInstrumentation*` — `source.checkSuites` byłby więc `"deploy"`, a apply z 2026-10-19 nie wywołał żadnego deploymentu (ostatni deployment IaC w zrzutach: 2026-10-15T17:33:56Z) [O]. Dokumentacja Railway nie wymienia, które pola IaC redeployują [O]. Lista „bez deployu” (R1) byłaby więc wiedzą empiryczną repo, nie kontraktem platformy.
- **Kontrola weryfikuje do końca tylko Postgresa i `/api/ready`.** Na żywo `source.checkSuites: true` mają API, web, db-backup i pipeline-trigger; Postgres ma źródło obrazowe bez Wait for CI (MCP `get-service-config`, 2026-10-28) [O]. Deployment apply'a na czterech pierwszych stoi w `WAITING` → kontrola tylko go przekazuje monitorowi (`verify-apply.sh:164-168`); czeka do `SUCCESS` wyłącznie na Postgresie, czyli na klasie incydentu z 2026-10-12, dla której ją zbudowano [I]. Deploymenty apply'a naprawdę padają: 3 deploymenty IaC API mają `FAILED` (2026-09-26 ×2, 2026-10-11) [O].
- **Zakres zmian w repo jest mały i zlokalizowany:** `verify-apply.sh` (`latest()` czyta tylko `id`/`status` z `--limit 1`, `:80-95`), `gate.jq:42-43` (reguła restartu), atrapa `railway` w `test.sh:488-530` (wymusza `--limit 1`, `meta: {}`), asercje utrwalające błąd (`test.sh:187-196`, `:597-608`), dokumenty: `tools/deploy-gate/README.md:61-65,131-136,168`, `deploy-plan.md:939,976-981,1020,1037-1059`, issue #73. `test.sh` jest dziś zielony (exit 0, 47 s) [O].

## Detailed Findings

### 1. `meta` deploymentów na produkcji (Unknown S-21)

Kształty `meta` w 298 deploymentach (zrzuty CLI 5.71.0; pracownik, rozstrzygające wpisy sprawdzone przez głównego agenta) [O]:

| Pochodzenie | `status` | Klucze rozróżniające | Przykład |
|---|---|---|---|
| push, zbudowany | `SUCCESS`/`SLEEPING`/`REMOVED`/`FAILED` | `reason: "deploy"`, `commitHash`, `branch`; bez `patchId`, bez `ignoreWatchPatterns` | — |
| push poza watch paths | `SKIPPED` | brak klucza `reason`; `skippedReason: "No changes to watched files"` (101 z 101 bez `reason`) | db-backup `2f602f86` (`fa37101`, 2026-10-19 16:31:42) |
| push przy czerwonym CI | `SKIPPED` | `reason: "deploy"`, `skippedReason: "CI check suite failed"` (3) | API `981111be` (`2abc64c`) |
| anulowany | `SKIPPED` | `skippedReason: "Deployment cancelled"` (1) | — |
| apply IaC | 28 `REMOVED`, 3 `FAILED`, 0 `SKIPPED` | `patchId: "iac-change-set/598f2026-…/<32 hex>"`, `reason: "deploy"`, `commitHash` = czubek gałęzi, `ignoreWatchPatterns: true` (23 z 23 usług GitHub; Postgres bez tego klucza) | API `402e9429`, pipeline-trigger `30f18f66` (2026-10-15, `eee1eda`) |
| redeploy | — | `reason: "redeploy"`, `ignoreWatchPatterns: true`; zachowuje `patchId` oryginału | Postgres `416b4a1e` (ten sam `patchId` co `be5af616`) |
| inna łatka środowiska | — | `patchId` w formie UUID (15 deploymentów usług GitHub + deploymenty Postgresa); pochodzenie [U] (panel, MCP, zmienna?) | 2026-10-12 21:06:26 patch `99415894…` na pięciu usługach |

- Hash w `iac-change-set/<env>/<hash>` nie jest jednoznaczny dla jednego runu: `d794b8f1…` niosą deploymenty z 2026-10-11 19:18:39, 20:00:55 i 21:14:49 [O]; jeden apply z 2026-10-15 dał ten sam hash `56c3a093…` na czterech usługach [O].
- Postgres deployuje się wyłącznie łatkami (każdy z 20 jego deploymentów w zrzutach ma `patchId`, IaC albo UUID, i żaden nie ma `commitHash`) [O] — równoległy push nie może więc „podmienić” deploymentu Postgresa [I].
- `railway config apply --json` zwraca w `RunnerResponse` m.in. `applyResult {…, deploymentId, stagedPatchId}` (źródło CLI 5.71.0, `runner.rs`) [O]; CI wywołuje apply bez `--json` (`ci.yml:499`), więc nie zapisuje tych ID. Czy `stagedPatchId` równa się hashowi z `patchId` deploymentu — [U].
- Brak historii statusów: ani CLI, ani MCP nie pokazują, czy deployment przeszedł przez `WAITING`; statusy GitHub Deployment są per środowisko (audyt P9) [O].

### 2. Wait for CI a deploy z apply'a

- **PR env, obserwacja wprost** (run 39140400096, 2026-10-18, commit `7e123c9` zmieniający tylko `.railway/railway.ts` i `plan.md`, czyli spoza `tools/pipeline-trigger/**`): migawka `9da17d2e` 15:28:19, „Applied” 15:28:22, nowy deployment `8562a9d2` `WAITING` 15:28:23, `::notice::` „handed over”, `/api/ready` 200 [O, `gh run view --log`]. Zmiana `deploy.cronSchedule` (`git show 7e123c9 -- .railway/railway.ts`). Ten sam przebieg opisuje komentarz w #73 z 2026-10-18 (dla runu 39139999132, commit `ec1fe66`).
- **Produkcja, pośrednio** [I, pracownik, czasy z logów buildów]: API `0fd012bb` (IaC, utworzony 13:23:17, 2026-10-13) zbudował się od 13:25:58, 18 s po konkluzji runu 38404494243 (13:25:40, potwierdzone `gh run view`). Kontrola: pipeline-trigger `b132b91c` (IaC, bez `checkSuites` w tamtym czasie) zbudował się 12:31:27, ok. 2 min przed konkluzją swojego runu.
- **Wniosek** [I]: deployment z apply'a na usłudze z `checkSuites: true` czeka na konkluzję workflow commita w swoim `commitHash` — a to czubek gałęzi, więc przy równoległym pushu czeka na CI nowszego commita. Czerwony `railway-config` zrobi go `SKIPPED` dopiero po konkluzji, poza oknem kontroli.

### 3. Czy zmianę „bez deployu” da się przewidzieć

- CLI: `deployEffect` liczony w `src/iac/change_set.rs` (`deploy_effect_for_update`, identyczny w v5.67.1 i v5.71.0): `"deploy"` dla tworzenia usługi/bazy i każdego `variable.set`/`variable.delete`; dla `resource.update` `"none"` tylko dla `config`, `groupId`, `cdn` i `tracing` z samymi detalami `tracing.autoInstrumentation*` [O, źródło CLI]. Pole ginie w `runner.rs` (`struct Change` bez niego) [O].
- Reguła CLI daje `"deploy"` dla `source.*`; obserwacja mówi, że `source.checkSuites (null → true)` nie deployuje: brak deploymentów IaC db-backup i pipeline-trigger w oknie 2026-10-19 16:20–17:00 (są tylko pushowe `2abc64c`, `fa37101`, `1a6d300`) [O]. Nawet przyszłe CLI z `deployEffect` dałoby więc dla tego przypadku zły wynik [I].
- Dokumentacja Railway (pracownik, cytaty): zmienne i zmiany staged redeployują „affected services” (https://docs.railway.com/deployments/staged-changes, https://docs.railway.com/variables); zmiana replik/regionu „scales the service without a full redeploy” (https://docs.railway.com/guides/multi-region-api-failover); strona IaC (https://docs.railway.com/infrastructure-as-code) nie wymienia pól, które redeployują, ani `deployEffect` [O].
- Obserwacje „deployuje” w historii repo: `postgres deploy.sleepApplication (null → false)` (2026-10-12, `be5af616`, `deploy-plan.md:414`), `deploy.cronSchedule` (PR env, wyżej), apply `watchPatterns` 2026-10-15 (cztery usługi) [O]. Obserwacja „nie deployuje”: `source.checkSuites` (2026-10-19) [O]. Pozostałe rodzaje dozwolone przez bramkę (`domain.create`, `variable.set` przez IaC, `limitOverride`) — bez obserwacji w tej sesji [U].

### 4. Co kontrola weryfikuje dziś (per usługa)

- `verify-apply.sh check` (`:146-196`): dla każdej usługi z `restarts.txt` pyta `railway deployment list --json --limit 1` (`:82`), bierze pierwszy deployment o ID innym niż migawka (`:154`) i ocenia tylko jego `status` (`:159-182`); `meta` nie czyta (`:87-90`).
- Konsekwencje na HEAD [I, z kodu i stanu na żywo]:
  - usługi z `checkSuites: true` (API, web, db-backup, pipeline-trigger): deploy z apply'a → `WAITING` → przekazanie monitorowi (`:164-168`); zmiana bez deployu → do 15 min czekania i `exit 1` „did not trigger a deploy” (`:184-187`) albo pierwszy deployment równoległego pusha wzięty za deployment apply'a (`--limit 1` widzi tylko najnowszy) — `SKIPPED` → `exit 1` (`:170-173`), `WAITING` → fałszywe „przekazanie”, `SUCCESS` (kod innego pusha, który przeszedł już CI) → fałszywe „deployed”;
  - Postgres: deploy z apply'a buduje się od razu, kontrola czeka do `SUCCESS` albo `FAILED`/`CRASHED`; to jedyna usługa, na której kontrola wykrywa zawieszenie w `DEPLOYING` (limit, `:189-190`) — monitor tego nie wykryje, bo reaguje tylko na zdarzenia końcowe `Deployment.deployed|redeployed|failed|crashed|oom_killed` (`deploy-plan.md`, sekcja `deploy-verification-hardening`, „Worker”).
- Monitor `szczur-radar-monitor` dostaje webhook także dla deploymentów z apply'a po ich zbudowaniu (webhook projektu obejmuje wszystkie deploymenty) [I]; dla usług z Wait for CI to on jest dziś faktycznym sprawdzeniem deployu z apply'a.

### 5. Kod, testy i CI, które zmiana dotknie

- **`gate.jq`**: `def restarts: .deployEffect != "none";` (`:42-43`) i jego użycie w `target`/`deploys` (`:130-134`) oraz `.restarts` (`:142`); komentarz nagłówka `:9-13`. `gate.sh` pisze `restarts.txt` tylko dla dozwolonego planu (`:80-82`) i kolumnę „Restarts” w podsumowaniu (`:60-67`). Jedynym konsumentem `restarts.txt` poza testami jest `verify-apply.sh` i `ci.yml` (pracownik, grep całego repo poza `context/archive`) [O].
- **`verify-apply.sh`**: `latest()` (`:80-95`) — miejsce na odczyt `meta.patchId`/`createdAt` i `--limit` > 1; tabela statusów w nagłówku `:16-24`.
- **`test.sh`** (69 `PASS`, 47 s) [O, pracownik]:
  - atrapa `railway` (`:488-530`) przyjmuje wyłącznie `deployment list --json --limit 1 -s … -p … -e …` (inne → exit 3), odpowiada `[{"id","status","createdAt":"2026-10-18T12:00:00Z","meta":{}}]` (`:526`) — nie wyrazi `patchId`, `skippedReason`, kilku deploymentów ani innego commita;
  - asercje utrwalające regułę „każda zmiana deployuje”: `pr-env-checksuites` (`:185-189`, podsumowanie „**Restarts:** `db-backup`, `pipeline-trigger`”), `checksuites-enable` (`:194-195`); `ended-SKIPPED` → 1 (`:597-608`) bez względu na pochodzenie; `same-id` (`:633-637`) poprawny dla zmiany, która deployuje, ale nieodróżnialny od zmiany bez deployu;
  - brak przypadków: zmiana bez deployu; plan mieszany (`checkSuites` + zmiana deployująca); nowy deployment z innego pusha (`SKIPPED`/`WAITING`/`SUCCESS`); push nad deploymentem apply'a między odpytaniami; `REMOVED` → `SKIPPED`.
- **`ci.yml`, job `railway-config`** (`:249-518`): migawka (`:486-493`, tylko gdy `PENDING_CHANGES != '0'`), apply `railway config apply --plan plan.pin --yes` bez `--json` (`:494-499`), kontrola (`:500-508`, `if: steps.apply.outcome == 'success'`), „API after a failed apply” (`:509-518`). Brak `continue-on-error` w całym pliku: każdy niezerowy krok czerwieni workflow, a Wait for CI pomija wtedy deploy kodu commita [O, pracownik; zachowanie Wait for CI — audyt]. Limity: `DEPLOY_VERIFY_TIMEOUT=15m` w budżecie 2 h (`deploy-plan.md:985`).
- **PR env, dodatkowe źródło deploymentów**: „Ensure PR-env LLM settings” woła `railway variable delete … -s szczur-radar-api` bez `--skip-deploys` (`ci.yml:381`), przed migawką; czy to deployuje i czy deployment powstaje przed migawką — [U]. Deployment z takiej zmiany miałby `patchId` inny niż `iac-change-set/` [I, z tabeli w §1].

### 6. Dokumenty i issue do poprawienia

| Miejsce | Stan na HEAD | Werdykt |
|---|---|---|
| issue #73, komentarz zamykający 2026-10-24, pkt 2 („Produkcja wdrożyła `2abc64c` mimo czerwonego CI … przyczyna niezbadana”) | bez korekty; issue `CLOSED` | sprzeczne z danymi dla API (audyt P2: `981111be` `SKIPPED`, `skippedReason: "CI check suite failed"` [O]); prawdziwe dla cronów (zbudowane przy pushu, zanim apply włączył im Wait for CI) |
| issue #73, pkt 1 („`verify-apply.sh` nadal traktuje `SKIPPED` (zwykły wynik watch paths) jako błąd”) | — | częściowe: `SKIPPED` był z równoległego pusha `fa37101`; sedno to zmiana bez deployu, której kontrola nie przewiduje (audyt P1) |
| `deploy-plan.md:939` (reguła 6 „Every change counts as a redeploy”) | — | sprzeczne z obserwacją dla `source.checkSuites` |
| `deploy-plan.md:976-981` (semantyka kontroli: `SKIPPED` → błąd, to samo ID → „did not trigger a deploy”) | — | do przepisania razem z regułą |
| `deploy-plan.md:1020` („announces both crons as redeployed”) | — | sprzeczne: apply nic nie zredeployował |
| `deploy-plan.md:1037-1059` (runbook „after the merge”, 13 pól `[ ]`, brak notatki „Production after the merge”) | — | audyt P2: 1d padło, 2a błędnie uznane, 5b niewykonane; reszta spełniona wg komentarza #73 |
| `deploy-plan.md:967` („Not verified yet: whether Wait for CI also holds back a `railway redeploy` …”) | — | zostaje nierozstrzygnięte (dotyczy `railway redeploy`, nie apply'a); dla apply'a rozstrzyga §2 |
| `tools/deploy-gate/README.md:61-65` (reguła 6), `:131-136` (statusy, „did not trigger a deploy”), `:168` (fixture PR-81 → „restart `db-backup` i `pipeline-trigger`”) | — | do przepisania z regułą |
| `gate.jq:9-13,42-43`, `verify-apply.sh:2-25` (komentarze) | — | do przepisania z regułą |
| `REPOSITORY_GUIDELINES.md`, sekcja Deployment („applies exactly that plan and then checks the redeployed services and `/api/ready`”) | — | ogólne; prawdziwe po zmianie, jeśli „redeployed” zostanie |

Linia `**Status (2026-10-19): live on production**` sekcji S-14 (`deploy-plan.md:908`) mówi o „reopened 2026-10-21”, a #73 jest zamknięte od 2026-10-24 — lekcja „Status zmiany w deploy-plan.md ma być prawdziwy także po merge'u” (`lessons.md`) każe pisać status prawdziwy po merge'u i stan produkcji trzymać w issue.

## Code References

- `tools/deploy-gate/verify-apply.sh:16-24` — tabela statusów; `:80-95` — `latest()` (`--limit 1`, tylko `id`/`status`); `:146-196` — pętla kontroli; `:154` — „nowe ID = deployment apply'a”; `:164-168` — przekazanie `WAITING`/`QUEUED`; `:170-173` — `FAILED|CRASHED|SKIPPED` → exit 1; `:184-190` — limit.
- `tools/deploy-gate/gate.jq:9-13,42-43,130-134,142` — reguła restartu i lista `restarts`; `:25,38-40` — jedyna dozwolona zmiana źródła.
- `tools/deploy-gate/gate.sh:12-14,60-67,80-82` — `GATE_RESTARTS_FILE` i podsumowanie.
- `tools/deploy-gate/test.sh:185-196,488-530,597-608,633-637` — asercje i atrapa CLI.
- `tools/deploy-gate/README.md:61-65,113-138,168` — reguła 6 i kontrola po apply.
- `.github/workflows/ci.yml:316-317,381,486-518` — CLI 5.67.1, `variable delete` w PR env, migawka/apply/kontrola.
- `.railway/railway.ts:106,192,227,274` — `checkSuites: true` na czterech usługach GitHub.
- `context/deployment/deploy-plan.md:908,939,967,972-985,1018-1020,1037-1059` — sekcja S-14.

## Architecture Insights

1. **„Nowe ID po migawce” to zły zamiennik „deployment tego apply'a”.** Railway oznacza deployment apply'a (`patchId` `iac-change-set/…`, `ignoreWatchPatterns: true`), a kontrola tej informacji nie czyta; stąd oba fałszywe wyniki z S-21 (zmiana bez deployu i równoległy push).
2. **Oczekiwanie „ta zmiana deployuje” nie ma wiarygodnego źródła.** Plan CLI go nie daje, wewnętrzna reguła CLI myli się dla `source.*`, a dokumentacja milczy. Kontrola, która wymaga deployu, będzie fałszywie czerwona przy każdej nowej rodzinie zmian bez deployu; kontrola, która deployu nie wymaga, nie wykryje zmiany, która powinna była zdeployować, a nie zdeployowała (czy taka awaria kiedykolwiek wystąpiła — [U]; zrzuty jej nie rozróżnią) — to wybór dla planu.
3. **Wartość kontroli skupia się na Postgresie i `/api/ready`.** Przy Wait for CI na czterech usługach GitHub kontrola widzi ich deploy z apply'a najwyżej jako `WAITING`; resztę robi monitor po CI. Postgres (bez Wait for CI, bez pushy) to jedyna usługa, której zawieszenie kontrola łapie, a monitor nie.
4. **Czerwony `railway-config` jest droższy niż przepuszczony fałszywy alarm.** Wait for CI pomija przy nim deploy kodu commita (audyt P1), więc fałszywy czerwony wynik bramki blokuje wdrożenie, którego żadna inna warstwa by nie zablokowała.

## Historical Context (from prior changes)

- `context/archive/2026-10-18-safe-deploy-gate/plan.md:43-47,140` — kontrola po apply domyka C6 i incydent 2026-10-12; `WAITING`/`QUEUED` przekazywane, bo „Deploy wywołany przez apply na usłudze z `checkSuites: true` … może stać w `WAITING` … (nieustalone)” — §2 to potwierdza (supported).
- `context/archive/2026-10-18-safe-deploy-gate/plan.md:90` — „`deployEffect` jest `"none"` tylko dla pól `config`, `groupId`, `cdn` … Każda inna zmiana usługi wywołuje deploy” — opis reguły CLI poprawny (supported dla źródła CLI), wniosek o zachowaniu platformy obalony dla `source.checkSuites` (contradicted).
- `context/archive/2026-10-18-safe-deploy-gate/plan.md:671-677` (Plan Amendment, wariant A) — plan CLI 5.67.1 bez `deployEffect`, „brak `deployEffect` dalej oznacza restart (reguła 6)” — decyzja, której skutkiem jest P1.
- `context/archive/2026-10-18-safe-deploy-gate/plan.md:737` (Progress 4.5) — runy 39139999132, 39140400096 z deployem `WAITING` przekazanym dalej; źródło obserwacji §2.
- `context/archive/2026-10-20-deploy-verification-hardening/` i `deploy-plan.md`, sekcja `deploy-verification-hardening` — monitor reaguje na zdarzenia końcowe deployu; nie wykrywa zawieszenia w `DEPLOYING`.
- `context/changes/post-m2-audit/research.md:102-104` — P1, P2, P3, R1–R6; ten dokument rozstrzyga `[U]` w R2 i w P2 (część o apply'u).

## Related Research

- `context/changes/post-m2-audit/research.md` — audyt po M-2, sekcja 3 (oś czasu `2abc64c`, P1–P3, P9).
- `context/archive/2026-10-18-safe-deploy-gate/research.md` — research S-14 (Wait for CI, format `railway deployment list`).

## Open Questions

Decyzje dla `/10x-plan` (fakty wyżej, wybór nie):
1. Reguła kontroli: (a) filtr deploymentów po `meta.patchId` z `iac-change-set/` i `createdAt` po migawce (kilka deploymentów zamiast `--limit 1`); (b) lista zmian „bez deployu” w `gate.jq` (R1, wiedza empiryczna: dziś tylko `source.checkSuites`); (c) brak wymogu deployu — brak deploymentu IaC w krótkim oknie = przejście z `::notice::`; (d) kombinacja. Każda z nich zostawia `/api/ready` i czekanie na Postgresa.
2. Czy kontrola ma dalej czekać na usługi z Wait for CI, skoro widzi je najwyżej jako `WAITING`, czy ograniczyć ją do usług bez Wait for CI (dziś: Postgres) i zostawić resztę monitorowi.
3. Czy poprawić #73 nowym komentarzem (issue zamknięte) i czy wypełnić 13 pól runbooka S-14 wstecz, czy zastąpić je notatką „Production after the merge” z odwołaniem do audytu.

Nierozstrzygnięte fakty:
4. Czy `stagedPatchId`/`applyResult.id` z `railway config apply --json` równa się hashowi w `patchId` deploymentu (wiązanie deploymentu z konkretnym apply'em) — [U]; sprawdzalne w PR env.
5. Które z pozostałych dozwolonych zmian (`domain.create`, `variable.set` przez IaC, `deploy.limitOverride`) deployują — [U]; dokumentacja mówi „affected services are redeployed” dla zmiennych.
6. Czy `railway variable delete` bez `--skip-deploys` (`ci.yml:381`) w PR env deployuje API i kiedy — [U].
7. Pochodzenie łatek z `patchId` w formie UUID (15 deploymentów usług GitHub) — [U]; nie wpływa na filtr `iac-change-set/`.
